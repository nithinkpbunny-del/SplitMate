package com.splitmate.report;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.splitmate.expense.Expense;
import com.splitmate.expense.Share;
import com.splitmate.seed.DemoDataSeeder;
import com.splitmate.split.Split;
import com.splitmate.support.MongoIntegrationTest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the report's aggregation pipeline against the demo data and checks every breakdown against a plain
 * Java computation over the same expenses. Also exercises the demo seeder end to end.
 */
@TestPropertySource(properties = "app.seed.enabled=true")
class ReportAggregationsIntegrationTest extends MongoIntegrationTest {

	@Autowired
	private DemoDataSeeder seeder;

	@Autowired
	private ReportAggregations aggregations;

	private Split october;

	@BeforeEach
	void seed() throws Exception {
		seeder.run(null); // the base class emptied the database after the context's own startup run
		october = mongo.findOne(Query.query(Criteria.where("name").is("October month split")), Split.class);
	}

	private List<Expense> expenses() {
		return mongo.find(Query.query(Criteria.where("splitId").is(october.getId()).and("deleted").is(false)), Expense.class);
	}

	@Test
	void seederIsIdempotent() throws Exception {
		long users = mongo.getCollection("users").countDocuments();
		long expenses = mongo.getCollection("expenses").countDocuments();
		seeder.run(null);
		assertThat(mongo.getCollection("users").countDocuments()).isEqualTo(users).isEqualTo(4);
		assertThat(mongo.getCollection("expenses").countDocuments()).isEqualTo(expenses).isEqualTo(43);
	}

	@Test
	void everyBreakdownMatchesAPlainJavaComputation() {
		ReportStats stats = aggregations.compute(october, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31));
		List<Expense> all = expenses();
		long total = all.stream().mapToLong(Expense::getAmountMinor).sum();

		assertThat(stats.totalSpendMinor()).isEqualTo(total).isEqualTo(305_706);
		assertThat(stats.expenseCount()).isEqualTo(30);
		assertThat(stats.days()).isEqualTo(31);
		assertThat(stats.avgPerExpenseMinor()).isEqualTo(10_190); // 305706 / 30 = 10190.2
		assertThat(stats.avgPerDayMinor()).isEqualTo(9_861); // 305706 / 31 = 9861.48
		assertThat(stats.avgPerPersonMinor()).isEqualTo(76_427); // 305706 / 4 = 76426.5

		// by category
		Map<ObjectId, Long> expectedByCategory = all.stream()
			.collect(Collectors.groupingBy(Expense::getCategoryId, Collectors.summingLong(Expense::getAmountMinor)));
		assertThat(stats.byCategory()).hasSize(expectedByCategory.size());
		assertThat(stats.byCategory().stream().mapToLong(ReportStats.CategoryStat::totalMinor).sum()).isEqualTo(total);
		stats.byCategory().forEach(c -> assertThat(c.totalMinor()).isEqualTo(expectedByCategory.get(new ObjectId(c.categoryId()))));
		assertThat(stats.byCategory().getFirst().key()).isEqualTo("rent");
		assertThat(stats.byCategory().getFirst().totalMinor()).isEqualTo(160_000);
		assertThat(stats.byCategory().getFirst().percent()).isEqualTo(52.3);
		assertThat(stats.byCategory()).isSortedAccordingTo((a, b) -> Long.compare(b.totalMinor(), a.totalMinor()));

		// by member: paid and share
		Map<ObjectId, Long> paid = all.stream()
			.collect(Collectors.groupingBy(Expense::getPaidBy, Collectors.summingLong(Expense::getAmountMinor)));
		Map<ObjectId, Long> share = new HashMap<>();
		for (Expense e : all) {
			for (Share s : e.getShares()) {
				share.merge(s.userId(), s.amountMinor(), Long::sum);
			}
		}
		assertThat(stats.byMember()).hasSize(4);
		stats.byMember().forEach(m -> {
			assertThat(m.paidMinor()).isEqualTo(paid.getOrDefault(new ObjectId(m.userId()), 0L));
			assertThat(m.shareMinor()).isEqualTo(share.getOrDefault(new ObjectId(m.userId()), 0L));
			assertThat(m.netMinor()).isEqualTo(m.paidMinor() - m.shareMinor());
		});
		assertThat(stats.byMember().stream().mapToLong(ReportStats.MemberStat::paidMinor).sum()).isEqualTo(total);
		assertThat(stats.byMember().stream().mapToLong(ReportStats.MemberStat::shareMinor).sum()).isEqualTo(total);
		assertThat(stats.byMember().stream().mapToLong(ReportStats.MemberStat::netMinor).sum()).isZero();

		// daily trend: one point per day, zero filled, dates on the right day
		assertThat(stats.dailyTrend()).hasSize(31);
		assertThat(stats.dailyTrend().getFirst().date()).isEqualTo(LocalDate.of(2026, 10, 1));
		assertThat(stats.dailyTrend().getFirst().totalMinor()).isEqualTo(160_000);
		Map<LocalDate, Long> expectedDaily = all.stream()
			.collect(Collectors.groupingBy(Expense::getDate, Collectors.summingLong(Expense::getAmountMinor)));
		stats.dailyTrend().forEach(d -> assertThat(d.totalMinor()).isEqualTo(expectedDaily.getOrDefault(d.date(), 0L)));

		// weekday pattern: Monday..Sunday, same totals as grouping the dates in Java
		assertThat(stats.weekdayPattern()).extracting(ReportStats.WeekdayStat::weekday)
			.containsExactly("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
		Map<DayOfWeek, Long> expectedWeekday = all.stream()
			.collect(Collectors.groupingBy(e -> e.getDate().getDayOfWeek(), Collectors.summingLong(Expense::getAmountMinor)));
		stats.weekdayPattern().forEach(w -> assertThat(w.totalMinor())
			.isEqualTo(expectedWeekday.getOrDefault(DayOfWeek.valueOf(w.weekday()), 0L)));

		// top expenses
		assertThat(stats.topExpenses()).hasSize(5);
		assertThat(stats.topExpenses().getFirst().description()).isEqualTo("Rent October");
		assertThat(stats.topExpenses().getFirst().paidByName()).isEqualTo("Aarav Mehta");
		assertThat(stats.topExpenses().getFirst().categoryName()).isEqualTo("Rent");
		assertThat(stats.topExpenses().get(1).description()).isEqualTo("Airbnb Prague (2 nights)");
		assertThat(stats.topExpenses()).isSortedAccordingTo((a, b) -> Long.compare(b.amountMinor(), a.amountMinor()));

		// member × category matrix adds up to the total
		assertThat(stats.memberCategoryMatrix().stream().mapToLong(ReportStats.MatrixCell::shareMinor).sum()).isEqualTo(total);
	}

	@Test
	void periodFilterAndDateRange() {
		ReportStats firstWeek = aggregations.compute(october, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7));
		long expected = expenses().stream()
			.filter(e -> !e.getDate().isAfter(LocalDate.of(2026, 10, 7)))
			.mapToLong(Expense::getAmountMinor)
			.sum();
		assertThat(firstWeek.totalSpendMinor()).isEqualTo(expected);
		assertThat(firstWeek.dailyTrend()).hasSize(7);

		LocalDate[] range = aggregations.expenseDateRange(october.getId());
		assertThat(range).containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 20));

		ReportStats empty = aggregations.compute(october, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31));
		assertThat(empty.totalSpendMinor()).isZero();
		assertThat(empty.byCategory()).isEmpty();
	}

}
