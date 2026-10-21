package com.splitmate.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.splitmate.category.Category;
import com.splitmate.expense.Expense;
import com.splitmate.split.Member;
import com.splitmate.split.Split;
import com.splitmate.user.User;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.ArrayOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.facet;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.lookup;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

/**
 * The report's statistics, computed inside MongoDB. One {@code $facet} pipeline over the split's expenses
 * produces every breakdown in a single round trip:
 *
 * <pre>
 * $match  { splitId, deleted: false, date: { $gte: start, $lte: end } }
 * $facet  {
 *   totals:       [ $group ]
 *   byCategory:   [ $group categoryId → $lookup categories → $unwind → $sort ]
 *   daily:        [ $project $dateToString → $group day → $sort ]
 *   weekday:      [ $project $dayOfWeek → $group ]
 *   top:          [ $sort amount → $limit 5 → $lookup users → $lookup categories ]
 *   paid:         [ $group paidBy ]
 *   shares:       [ $unwind shares → $group shares.userId ]
 *   matrix:       [ $unwind shares → $group {user, category} → $lookup categories ]
 * }
 * </pre>
 */
@Component
public class ReportAggregations {

	private static final List<DayOfWeek> WEEK = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
			DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

	private final MongoTemplate mongo;

	public ReportAggregations(MongoTemplate mongo) {
		this.mongo = mongo;
	}

	/** First and last expense date of the split, or null when it has no expenses. */
	public LocalDate[] expenseDateRange(ObjectId splitId) {
		Aggregation aggregation = Aggregation.newAggregation(
				match(Criteria.where("splitId").is(splitId).and("deleted").is(false)),
				group().min("date").as("first").max("date").as("last"));
		Document row = mongo.aggregate(aggregation, collection(Expense.class), Document.class).getUniqueMappedResult();
		if (row == null) {
			return null;
		}
		return new LocalDate[] { toLocalDate(row.getDate("first")), toLocalDate(row.getDate("last")) };
	}

	public ReportStats compute(Split split, LocalDate start, LocalDate end) {
		String categories = collection(Category.class);
		String users = collection(User.class);

		AggregationOperation facets = facet(group().sum("amountMinor").as("total").count().as("count"))
			.as("totals")
			.and(group("categoryId").sum("amountMinor").as("total").count().as("count"),
					lookup(categories, "_id", "_id", "category"), unwind("category"),
					sort(Sort.by(Sort.Direction.DESC, "total")))
			.as("byCategory")
			.and(project("amountMinor").and(DateOperators.dateOf("date").withTimezone(DateOperators.Timezone.valueOf("UTC"))
				.toString("%Y-%m-%d")).as("day"), group("day").sum("amountMinor").as("total"),
					sort(Sort.by(Sort.Direction.ASC, Fields.UNDERSCORE_ID)))
			.as("daily")
			.and(project("amountMinor").and(DateOperators.dateOf("date").withTimezone(DateOperators.Timezone.valueOf("UTC"))
				.dayOfWeek()).as("weekday"), group("weekday").sum("amountMinor").as("total"))
			.as("weekday")
			.and(sort(Sort.by(Sort.Direction.DESC, "amountMinor").and(Sort.by(Sort.Direction.ASC, "date"))), limit(5),
					lookup(users, "paidBy", "_id", "payer"), lookup(categories, "categoryId", "_id", "category"),
					project("description", "amountMinor", "date")
						.and(ArrayOperators.ArrayElemAt.arrayOf("payer.name").elementAt(0)).as("payerName")
						.and(ArrayOperators.ArrayElemAt.arrayOf("category.name").elementAt(0)).as("categoryName"))
			.as("top")
			.and(group("paidBy").sum("amountMinor").as("paid"))
			.as("paid")
			.and(unwind("shares"), group("shares.userId").sum("shares.amountMinor").as("share"))
			.as("shares")
			.and(unwind("shares"),
					group(Fields.from(Fields.field("userId", "shares.userId"), Fields.field("categoryId", "categoryId")))
						.sum("shares.amountMinor").as("share"),
					lookup(categories, "_id.categoryId", "_id", "category"), unwind("category"))
			.as("matrix");

		Aggregation aggregation = Aggregation.newAggregation(
				match(Criteria.where("splitId").is(split.getId()).and("deleted").is(false)
					.and("date").gte(start).lte(end)),
				facets);
		Document result = mongo.aggregate(aggregation, collection(Expense.class), Document.class).getUniqueMappedResult();
		return toStats(split, start, end, result == null ? new Document() : result);
	}

	private ReportStats toStats(Split split, LocalDate start, LocalDate end, Document r) {
		Document totals = list(r, "totals").stream().findFirst().orElse(new Document());
		long total = num(totals, "total");
		long count = num(totals, "count");
		int days = (int) ChronoUnit.DAYS.between(start, end) + 1;
		int people = Math.max(1, split.activeMembers().size());

		List<ReportStats.CategoryStat> byCategory = list(r, "byCategory").stream().map(d -> {
			Document c = d.get("category", Document.class);
			long t = num(d, "total");
			return new ReportStats.CategoryStat(d.getObjectId("_id").toHexString(), c.getString("key"), c.getString("name"),
					c.getString("color"), t, num(d, "count"), percent(t, total));
		}).toList();

		Map<ObjectId, Long> paid = new HashMap<>();
		list(r, "paid").forEach(d -> paid.put(d.getObjectId("_id"), num(d, "paid")));
		Map<ObjectId, Long> shares = new HashMap<>();
		list(r, "shares").forEach(d -> shares.put(d.getObjectId("_id"), num(d, "share")));
		List<ReportStats.MemberStat> byMember = new ArrayList<>();
		for (Member m : split.getMembers()) {
			long p = paid.getOrDefault(m.getUserId(), 0L);
			long s = shares.getOrDefault(m.getUserId(), 0L);
			if (!m.isActive() && p == 0 && s == 0) {
				continue;
			}
			byMember.add(new ReportStats.MemberStat(m.getUserId().toHexString(), m.getName(), m.getAvatarColor(), p, s,
					p - s, percent(p, total)));
		}

		Map<String, Long> dailyMap = new HashMap<>();
		list(r, "daily").forEach(d -> dailyMap.put(d.getString("_id"), num(d, "total")));
		// one point per day of the period, zero-filled, so the trend line has no gaps
		List<ReportStats.DailyStat> daily = new ArrayList<>();
		for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
			daily.add(new ReportStats.DailyStat(day, dailyMap.getOrDefault(day.toString(), 0L)));
		}

		// $dayOfWeek: 1 = Sunday … 7 = Saturday
		Map<DayOfWeek, Long> weekdayMap = new HashMap<>();
		list(r, "weekday").forEach(d -> weekdayMap.put(DayOfWeek.of((int) ((num(d, "_id") + 5) % 7) + 1), num(d, "total")));
		List<ReportStats.WeekdayStat> weekdays = WEEK.stream()
			.map(day -> new ReportStats.WeekdayStat(day.name(), weekdayMap.getOrDefault(day, 0L)))
			.toList();

		List<ReportStats.TopExpense> top = list(r, "top").stream()
			.map(d -> new ReportStats.TopExpense(d.getObjectId("_id").toHexString(), d.getString("description"),
					num(d, "amountMinor"), d.getString("payerName"), toLocalDate(d.getDate("date")),
					d.getString("categoryName")))
			.toList();

		List<ReportStats.MatrixCell> matrix = list(r, "matrix").stream().map(d -> {
			Document id = d.get("_id", Document.class);
			return new ReportStats.MatrixCell(id.getObjectId("userId").toHexString(),
					d.get("category", Document.class).getString("key"), num(d, "share"));
		}).toList();

		return new ReportStats(total, count, divideRounded(total, count), divideRounded(total, days),
				divideRounded(total, people), days, byCategory, byMember, daily, weekdays, top, matrix);
	}

	/** Integer division rounded half up, for non-negative minor units (no floating point near money). */
	static long divideRounded(long amount, long divisor) {
		return divisor <= 0 ? 0 : (2 * amount + divisor) / (2 * divisor);
	}

	private String collection(Class<?> type) {
		return mongo.getCollectionName(type);
	}

	private static List<Document> list(Document d, String key) {
		List<Document> value = d.getList(key, Document.class);
		return value == null ? List.of() : value;
	}

	private static long num(Document d, String key) {
		Object v = d.get(key);
		return v instanceof Number n ? n.longValue() : 0L;
	}

	private static double percent(long part, long whole) {
		return whole == 0 ? 0 : Math.round(part * 1000.0 / whole) / 10.0;
	}

	private static LocalDate toLocalDate(Date date) {
		return date == null ? null : date.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
	}

}
