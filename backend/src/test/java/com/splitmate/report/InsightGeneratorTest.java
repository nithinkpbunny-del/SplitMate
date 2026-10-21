package com.splitmate.report;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.splitmate.report.InsightGenerator.PreviousPeriod;

import static org.assertj.core.api.Assertions.assertThat;

class InsightGeneratorTest {

	private final InsightGenerator generator = new InsightGenerator();

	private static ReportStats.CategoryStat category(String name, long total, long all) {
		return new ReportStats.CategoryStat("c-" + name, name.toLowerCase(), name, "#FFFFFF", total, 1,
				Math.round(total * 1000.0 / all) / 10.0);
	}

	private static ReportStats.MemberStat member(String id, String name, long paid, long share) {
		return new ReportStats.MemberStat(id, name, "#FFFFFF", paid, share, paid - share, 0);
	}

	/** Mon 6 Oct to Sun 12 Oct 2025, one full week, with the given spend per day (Mon..Sun). */
	private static List<ReportStats.DailyStat> week(long... perDay) {
		List<ReportStats.DailyStat> days = new ArrayList<>();
		for (int i = 0; i < perDay.length; i++) {
			days.add(new ReportStats.DailyStat(LocalDate.of(2025, 10, 6).plusDays(i), perDay[i]));
		}
		return days;
	}

	private static ReportStats stats(long total, long count, List<ReportStats.CategoryStat> categories,
			List<ReportStats.MemberStat> members, List<ReportStats.DailyStat> daily,
			List<ReportStats.TopExpense> top) {
		return new ReportStats(total, count, count == 0 ? 0 : total / count, daily.isEmpty() ? 0 : total / daily.size(),
				total / 3, daily.size(), categories, members, daily, List.of(), top, List.of());
	}

	private static ReportStats sample() {
		long total = 100_000;
		return stats(total, 5,
				List.of(category("Rent", 58_000, total), category("Groceries", 30_000, total), category("Dining Out", 12_000, total)),
				List.of(member("a", "Aarav Mehta", 70_000, 33_000), member("b", "Isha Rao", 20_000, 34_000), member("c", "Rohan Nair", 10_000, 33_000)),
				// Mon..Sun: 58,000 on Monday, weekdays otherwise quiet, 20,000 split over the weekend
				week(58_000, 2_000, 2_000, 2_000, 6_000, 10_000, 20_000),
				List.of(new ReportStats.TopExpense("e1", "Rent October", 58_000, "Aarav Mehta", LocalDate.of(2025, 10, 6), "Rent")));
	}

	@Test
	void noExpensesGivesASingleExplanation() {
		ReportStats empty = stats(0, 0, List.of(), List.of(), List.of(), List.of());
		assertThat(generator.generate(empty, "EUR", 3, null)).containsExactly("No expenses were recorded in this period.");
	}

	@Test
	void topCategoryShareIsReportedAsAPercentage() {
		assertThat(generator.generate(sample(), "EUR", 3, null)).contains("Rent made up 58% of all spending.");
	}

	@Test
	void singleCategoryGetsItsOwnSentence() {
		ReportStats one = stats(5_000, 2, List.of(category("Groceries", 5_000, 5_000)), List.of(), week(5_000, 0, 0, 0, 0, 0, 0), List.of());
		assertThat(generator.generate(one, "EUR", 3, null)).contains("All spending this period was on Groceries.");
	}

	@Test
	void highestDayAndBiggestExpenseUseRealDataAndFormatting() {
		List<String> insights = generator.generate(sample(), "EUR", 3, null);
		assertThat(insights).contains(
				"The most expensive day was Monday 6 October (€580.00).",
				"The biggest single expense was Rent October at €580.00, paid by Aarav, 58% of the total.");
	}

	@Test
	void paidMostVersusConsumedMostWhenDifferentPeople() {
		assertThat(generator.generate(sample(), "EUR", 3, null))
			.contains("Aarav paid the most (€700.00), while Isha had the largest share (€340.00).");
	}

	@Test
	void paidMostAndConsumedMostWhenSamePerson() {
		ReportStats s = stats(100_000, 5, List.of(category("Rent", 60_000, 100_000), category("Other", 40_000, 100_000)),
				List.of(member("a", "Aarav Mehta", 80_000, 50_000), member("b", "Isha Rao", 20_000, 50_000)),
				week(100_000, 0, 0, 0, 0, 0, 0), List.of());
		assertThat(generator.generate(s, "EUR", 2, null))
			.contains("Aarav both paid the most (€800.00) and had the largest share (€500.00).");
	}

	@Test
	void averagePerPersonPerDayUsesIntegerRounding() {
		// 100000 / (3 people * 7 days) = 4761.9 cents -> €47.62
		assertThat(generator.generate(sample(), "EUR", 3, null))
			.contains("On average each person spent €47.62 per day over 7 days.");
	}

	@Test
	void weekendVersusWeekdayComparesDailyAverages() {
		// weekdays: (58000+2000+2000+2000+6000)/5 = 14000, weekend: (10000+20000)/2 = 15000 -> 7% more
		assertThat(generator.generate(sample(), "EUR", 3, null))
			.contains("Weekends averaged €150.00 a day versus €140.00 on weekdays (7% more).");
	}

	@Test
	void weekendInsightSkippedWhenPeriodHasNoWeekend() {
		ReportStats midweek = stats(3_000, 2, List.of(category("Groceries", 3_000, 3_000)), List.of(), week(1_000, 1_000, 1_000), List.of());
		assertThat(generator.generate(midweek, "EUR", 3, null)).noneMatch(s -> s.startsWith("Weekends averaged"));
	}

	@Test
	void comparisonWithThePreviousSplit() {
		assertThat(generator.generate(sample(), "EUR", 3, new PreviousPeriod("September month split", 80_000)))
			.contains("Spending is up 25% compared with September month split (€800.00).");
		assertThat(generator.generate(sample(), "EUR", 3, new PreviousPeriod("September month split", 125_000)))
			.contains("Spending is down 20% compared with September month split (€1,250.00).");
		assertThat(generator.generate(sample(), "EUR", 3, new PreviousPeriod("September month split", 100_200)))
			.contains("Spending was about the same as September month split (€1,002.00).");
		assertThat(generator.generate(sample(), "EUR", 3, null)).noneMatch(s -> s.contains("compared with"));
	}

	@Test
	void neverMoreThanSevenInsightsAndAlwaysAtLeastFourForRealData() {
		List<String> insights = generator.generate(sample(), "EUR", 3, new PreviousPeriod("September month split", 80_000));
		assertThat(insights).hasSizeBetween(4, 7);
		assertThat(insights).doesNotHaveDuplicates();
	}

	@Test
	void everyInsightIsComputedFromTheInput() {
		// Changing the data changes the text: nothing is hard coded.
		ReportStats other = stats(10_000, 2, List.of(category("Travel", 7_500, 10_000), category("Other", 2_500, 10_000)),
				List.of(member("a", "Sneha Iyer", 10_000, 5_000), member("b", "Rohan Nair", 0, 5_000)),
				week(0, 0, 0, 0, 10_000, 0, 0), List.of());
		List<String> insights = generator.generate(other, "EUR", 2, null);
		assertThat(insights).contains("Travel made up 75% of all spending.");
		assertThat(insights).noneMatch(s -> s.contains("Rent"));
	}

}
