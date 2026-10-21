package com.splitmate.report;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.splitmate.activity.ActivityService;
import com.splitmate.activity.ActivityType;
import com.splitmate.category.Category;
import com.splitmate.category.CategoryService;
import com.splitmate.common.ApiException;
import com.splitmate.common.Ids;
import com.splitmate.expense.Expense;
import com.splitmate.expense.ExpenseService;
import com.splitmate.settlement.BalanceQueries;
import com.splitmate.settlement.Settlement;
import com.splitmate.settlement.SettlementService;
import com.splitmate.split.Member;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;
import com.splitmate.split.SplitDeletedEvent;
import com.splitmate.split.SplitService;

import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Spending analysis reports: statistics from MongoDB aggregations, rule-based insights, a PDF stored in GridFS.
 */
@Service
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);

	private static final long MAX_PERIOD_DAYS = 366;

	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.UK);

	private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

	public record PdfDownload(GridFsResource resource, String fileName, long length) {
	}

	private final ReportRepository reports;

	private final ReportAggregations aggregations;

	private final InsightGenerator insights;

	private final PdfReportRenderer pdf;

	private final GridFsTemplate gridFs;

	private final SplitAccess access;

	private final SplitService splits;

	private final SettlementService settlements;

	private final BalanceQueries balances;

	private final ExpenseService expenses;

	private final CategoryService categories;

	private final ActivityService activity;

	public ReportService(ReportRepository reports, ReportAggregations aggregations, InsightGenerator insights,
			PdfReportRenderer pdf, GridFsTemplate gridFs, SplitAccess access, SplitService splits,
			SettlementService settlements, BalanceQueries balances, ExpenseService expenses,
			CategoryService categories, ActivityService activity) {
		this.reports = reports;
		this.aggregations = aggregations;
		this.insights = insights;
		this.pdf = pdf;
		this.gridFs = gridFs;
		this.access = access;
		this.splits = splits;
		this.settlements = settlements;
		this.balances = balances;
		this.expenses = expenses;
		this.categories = categories;
		this.activity = activity;
	}

	public ReportResponse generate(String splitId, ObjectId userId, ReportRequest request) {
		Split split = access.requireMember(splitId, userId);
		Settlement settlement = settlements.latestSnapshot(split.getId())
			.orElseThrow(() -> ApiException.conflict("Calculate the split first. The report builds on the latest calculation."));

		LocalDate[] range = aggregations.expenseDateRange(split.getId());
		if (range == null) {
			throw ApiException.conflict("Add some expenses first, there is nothing to report yet.");
		}
		LocalDate start = firstNonNull(request == null ? null : request.periodStart(),
				split.getPeriod() == null ? null : split.getPeriod().start(), range[0]);
		LocalDate end = firstNonNull(request == null ? null : request.periodEnd(),
				split.getPeriod() == null ? null : split.getPeriod().end(), range[1]);
		if (end.isBefore(start)) {
			throw ApiException.badRequest("The period must end on or after its start date");
		}
		if (ChronoUnit.DAYS.between(start, end) > MAX_PERIOD_DAYS) {
			throw ApiException.badRequest("A report can cover at most one year");
		}

		ReportStats stats = aggregations.compute(split, start, end);
		if (stats.expenseCount() == 0) {
			throw ApiException.conflict("There are no expenses between " + start + " and " + end + ".");
		}
		List<String> insightList = insights.generate(stats, split.getCurrency(), split.activeMembers().size(),
				previousPeriod(split, userId, start));

		String actor = access.actorName(split, userId);
		Report report = new Report();
		report.setSplitId(split.getId());
		report.setSettlementId(settlement.getId());
		report.setGeneratedBy(userId);
		report.setGeneratedAt(Instant.now());
		report.setPeriodStart(start);
		report.setPeriodEnd(end);
		report.setCurrency(split.getCurrency());
		report.setStats(stats);
		report.setInsights(insightList);
		report = reports.save(report);

		try {
			Map<ObjectId, Category> byId = categories.byIdFor(split.getId());
			List<Expense> ledger = expenses.liveExpenses(split.getId())
				.stream()
				.filter(e -> !e.getDate().isBefore(start) && !e.getDate().isAfter(end))
				.toList();
			byte[] bytes = pdf.render(new PdfReportRenderer.Input(split, report, settlement, split.isCalculationStale(),
					ledger, byId, actor));
			String fileName = fileName(split, start);
			ObjectId fileId = gridFs.store(new ByteArrayInputStream(bytes), fileName, "application/pdf",
					new Document("reportId", report.getId()).append("splitId", split.getId()));
			report.setPdfFileId(fileId);
			report.setPdfFileName(fileName);
			report = reports.save(report);
		}
		catch (RuntimeException ex) {
			reports.delete(report); // never leave a report without its PDF
			throw ex;
		}

		activity.log(split.getId(), userId, actor, ActivityType.REPORT_GENERATED,
				actor + " generated a spending report for " + SHORT.format(start) + " - " + LONG.format(end),
				Map.of("reportId", report.getId().toHexString()));
		log.info("Report {} generated for split {} ({} expenses)", report.getId(), split.getId(), stats.expenseCount());
		return ReportResponse.from(report, actor);
	}

	public List<ReportSummary> list(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		return reports.findBySplitIdOrderByGeneratedAtDesc(split.getId()).stream().map(r -> {
			String topInsight = r.getInsights().isEmpty() ? null : r.getInsights().getFirst();
			return new ReportSummary(r.getId().toHexString(), r.getGeneratedAt(), nameOf(split, r.getGeneratedBy()),
					r.getPeriodStart(), r.getPeriodEnd(), r.getStats().totalSpendMinor(), r.getStats().expenseCount(),
					topInsight);
		}).toList();
	}

	public ReportResponse get(String reportId, ObjectId userId) {
		Report report = require(reportId, userId);
		Split split = access.requireMember(report.getSplitId(), userId);
		return ReportResponse.from(report, nameOf(split, report.getGeneratedBy()));
	}

	/** The stored PDF, streamed from GridFS. */
	public PdfDownload pdf(String reportId, ObjectId userId) {
		Report report = require(reportId, userId);
		if (report.getPdfFileId() == null) {
			throw ApiException.notFound("This report has no PDF");
		}
		GridFSFile file = gridFs.findOne(Query.query(where("_id").is(report.getPdfFileId())));
		if (file == null) {
			throw ApiException.notFound("The PDF file is missing");
		}
		return new PdfDownload(gridFs.getResource(file), report.getPdfFileName(), file.getLength());
	}

	@EventListener
	void onSplitDeleted(SplitDeletedEvent event) {
		for (Report report : reports.findBySplitIdOrderByGeneratedAtDesc(event.splitId())) {
			if (report.getPdfFileId() != null) {
				gridFs.delete(Query.query(where("_id").is(report.getPdfFileId())));
			}
		}
		reports.deleteBySplitId(event.splitId());
	}

	// ----------------------------------------------------------------- helpers

	private Report require(String reportId, ObjectId userId) {
		Report report = reports.findById(Ids.parse(reportId, "Report")).orElseThrow(() -> ApiException.notFound("Report not found"));
		access.requireMember(report.getSplitId(), userId); // 404 for non-members
		return report;
	}

	/**
	 * The most recent earlier split of the caller that has exactly the same active members and ended before this
	 * period started, for the "up/down versus last month" insight.
	 */
	private InsightGenerator.PreviousPeriod previousPeriod(Split split, ObjectId userId, LocalDate periodStart) {
		Set<ObjectId> members = split.activeMembers().stream().map(Member::getUserId).collect(Collectors.toSet());
		return splits.splitsOf(userId)
			.stream()
			.filter(other -> !other.getId().equals(split.getId()) && other.getPeriod() != null
					&& other.getPeriod().end().isBefore(periodStart))
			.filter(other -> other.activeMembers().stream().map(Member::getUserId).collect(Collectors.toSet()).equals(members))
			.max(Comparator.comparing(other -> other.getPeriod().end()))
			.map(other -> new InsightGenerator.PreviousPeriod(other.getName(),
					balances.figuresFor(other.getId(), userId).totalSpendMinor()))
			.orElse(null);
	}

	/** {@code SplitMate-October-month-split-2026-10.pdf} */
	static String fileName(Split split, LocalDate periodStart) {
		return "SplitMate-" + slug(split.getName()) + "-" + periodStart.format(DateTimeFormatter.ofPattern("yyyy-MM")) + ".pdf";
	}

	/** ASCII-only, dash separated: "Oktober Käse!" becomes "Oktober-Kase". */
	static String slug(String name) {
		String ascii = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = ascii.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
		return slug.isBlank() ? "split" : slug;
	}

	private static String nameOf(Split split, ObjectId userId) {
		return split.member(userId).map(Member::getName).orElse("Someone");
	}

	@SafeVarargs
	private static <T> T firstNonNull(T... values) {
		for (T value : values) {
			if (value != null) {
				return value;
			}
		}
		throw new IllegalStateException("no value");
	}

}
