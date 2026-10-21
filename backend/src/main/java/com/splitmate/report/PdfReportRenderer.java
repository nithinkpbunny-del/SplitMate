package com.splitmate.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.bson.types.ObjectId;
import org.thymeleaf.context.Context;

import com.splitmate.category.Category;
import com.splitmate.common.Money;
import com.splitmate.expense.Expense;
import com.splitmate.settlement.Settlement;
import com.splitmate.split.Member;
import com.splitmate.split.Split;

import org.springframework.stereotype.Component;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders a report as an A4 PDF: the Thymeleaf template {@code templates/report.html} becomes PDF with
 * OpenHTMLtoPDF. Tables and KPIs only (no charts). The built-in PDF fonts are used, so text is reduced to
 * what they can draw: no emoji, and the typographic minus becomes a plain hyphen.
 */
@Component
public class PdfReportRenderer {

	private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

	private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);

	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.UK);

	private final SpringTemplateEngine templates;

	public PdfReportRenderer(SpringTemplateEngine templates) {
		this.templates = templates;
	}

	public record Input(Split split, Report report, Settlement settlement, boolean calculationStale,
			List<Expense> expenses, Map<ObjectId, Category> categories, String generatedByName) {
	}

	record Kpi(String label, String value) {
	}

	record CategoryRow(String name, String color, long count, String total, String barWidth, String percentLabel) {
	}

	record MemberRow(String name, String paid, String share, String net) {
	}

	record TransferRow(String text, String amount) {
	}

	record TopRow(int rank, String description, String paidBy, String date, String amount) {
	}

	record LedgerRow(String date, String description, String category, String paidBy, String sharedBy, String amount) {
	}

	public byte[] render(Input in) {
		String html = templates.process("report", context(in));
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			builder.withHtmlContent(html, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not render the report PDF", ex);
		}
		catch (RuntimeException ex) {
			throw new IllegalStateException("Could not render the report PDF", ex);
		}
	}

	Context context(Input in) {
		Split split = in.split();
		ReportStats stats = in.report().getStats();
		String currency = in.report().getCurrency();
		Map<String, Member> members = split.getMembers()
			.stream()
			.collect(Collectors.toMap(m -> m.getUserId().toHexString(), m -> m, (a, b) -> a));

		List<Kpi> kpis = List.of(new Kpi("Total spend", money(stats.totalSpendMinor(), currency)),
				new Kpi("Expenses", String.valueOf(stats.expenseCount())),
				new Kpi("Average per day", money(stats.avgPerDayMinor(), currency)),
				new Kpi("Average per person", money(stats.avgPerPersonMinor(), currency)));

		List<CategoryRow> categories = stats.byCategory()
			.stream()
			.map(c -> new CategoryRow(safe(c.name()), c.color(), c.count(), money(c.totalMinor(), currency),
					Math.max(1, Math.round(c.percent())) + "%", String.format(Locale.UK, "%.1f%%", c.percent())))
			.toList();

		List<MemberRow> memberRows = stats.byMember()
			.stream()
			.map(m -> new MemberRow(safe(m.name()), money(m.paidMinor(), currency), money(m.shareMinor(), currency),
					signed(m.netMinor(), currency)))
			.toList();

		List<TransferRow> transfers = in.settlement() == null ? List.of()
				: in.settlement()
					.getTransfers()
					.stream()
					.map(t -> new TransferRow(safe(t.fromName()) + " pays " + safe(t.toName()), money(t.amountMinor(), currency)))
					.toList();
		String settlementNote = in.settlement() == null ? ""
				: "From calculation v" + in.settlement().getVersion() + " of "
						+ LONG_DATE.format(in.settlement().getCalculatedAt().atZone(ZONE))
						+ (in.calculationStale() ? ". Expenses or payments changed since, so recalculate for current balances." : ".");

		List<TopRow> top = new ArrayList<>();
		int rank = 1;
		for (ReportStats.TopExpense e : stats.topExpenses()) {
			top.add(new TopRow(rank++, safe(e.description()), safe(e.paidByName()), SHORT_DATE.format(e.date()),
					money(e.amountMinor(), currency)));
		}

		int activeCount = split.activeMembers().size();
		List<LedgerRow> ledger = in.expenses()
			.stream()
			.map(e -> new LedgerRow(SHORT_DATE.format(e.getDate()), safe(e.getDescription()),
					safe(in.categories().get(e.getCategoryId()) == null ? "" : in.categories().get(e.getCategoryId()).getName()),
					safe(nameOf(members, e.getPaidBy())), sharedBy(e, members, activeCount),
					money(e.getAmountMinor(), currency)))
			.toList();

		Context ctx = new Context(Locale.UK);
		ctx.setVariable("title", "SplitMate report - " + safe(split.getName()));
		ctx.setVariable("splitName", safe(split.getName()));
		ctx.setVariable("periodLabel", LONG_DATE.format(in.report().getPeriodStart()) + "  -  " + LONG_DATE.format(in.report().getPeriodEnd()));
		ctx.setVariable("membersLine", "Members: " + split.activeMembers().stream().map(m -> safe(m.getName())).collect(Collectors.joining(", ")));
		ctx.setVariable("generatedLine", "Generated " + LONG_DATE.format(Instant.now().atZone(ZONE)) + " by " + safe(in.generatedByName()));
		ctx.setVariable("kpis", kpis);
		ctx.setVariable("categories", categories);
		ctx.setVariable("members", memberRows);
		ctx.setVariable("transfers", transfers);
		ctx.setVariable("settlementNote", settlementNote);
		ctx.setVariable("top", top);
		ctx.setVariable("insights", in.report().getInsights().stream().map(PdfReportRenderer::safe).toList());
		ctx.setVariable("ledger", ledger);
		ctx.setVariable("ledgerTotalLabel", "Total (" + stats.expenseCount() + " expenses)");
		ctx.setVariable("ledgerTotal", money(stats.totalSpendMinor(), currency));
		return ctx;
	}

	private static String sharedBy(Expense e, Map<String, Member> members, int activeCount) {
		int n = e.getParticipants().size();
		if (n == activeCount && n > 1) {
			return "Everyone (" + n + ")";
		}
		return e.getParticipants().stream().map(id -> safe(firstName(nameOf(members, id)))).collect(Collectors.joining(", "));
	}

	private static String nameOf(Map<String, Member> members, ObjectId id) {
		Member m = members.get(id.toHexString());
		return m == null ? "Former member" : m.getName();
	}

	private static String firstName(String name) {
		int space = name.indexOf(' ');
		return space < 0 ? name : name.substring(0, space);
	}

	private static String money(long minor, String currency) {
		return safe(Money.format(minor, currency));
	}

	private static String signed(long minor, String currency) {
		return safe(Money.formatSigned(minor, currency));
	}

	/**
	 * Keeps only characters the built-in PDF fonts (WinAnsi) can draw: Latin-1, the euro sign and common
	 * punctuation. Emoji and other symbols are dropped, the typographic minus becomes a hyphen.
	 */
	static String safe(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		text.codePoints().forEach(cp -> {
			if (cp == 0x2212) {
				sb.append('-');
			}
			else if (cp == 0x20AC || cp == 0x2013 || cp == 0x2014 || cp == 0x2018 || cp == 0x2019 || cp == 0x201C
					|| cp == 0x201D || cp == 0x2022 || cp == 0x2026 || (cp >= 0x20 && cp < 0x7F) || (cp >= 0xA0 && cp <= 0xFF)) {
				sb.appendCodePoint(cp);
			}
			else if (cp == 0xA0 || Character.isWhitespace(cp)) {
				sb.append(' ');
			}
		});
		return sb.toString().strip();
	}

}
