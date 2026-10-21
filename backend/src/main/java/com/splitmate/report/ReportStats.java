package com.splitmate.report;

import java.time.LocalDate;
import java.util.List;

/**
 * Everything the report shows, computed with MongoDB aggregation pipelines. Money in minor units; the
 * {@code percent} fields are display-only ratios.
 */
public record ReportStats(
		long totalSpendMinor,
		long expenseCount,
		long avgPerExpenseMinor,
		long avgPerDayMinor,
		long avgPerPersonMinor,
		int days,
		List<CategoryStat> byCategory,
		List<MemberStat> byMember,
		List<DailyStat> dailyTrend,
		List<WeekdayStat> weekdayPattern,
		List<TopExpense> topExpenses,
		List<MatrixCell> memberCategoryMatrix) {

	public record CategoryStat(String categoryId, String key, String name, String color, long totalMinor, long count,
			double percent) {
	}

	public record MemberStat(String userId, String name, String avatarColor, long paidMinor, long shareMinor,
			long netMinor, double percentOfPaid) {
	}

	public record DailyStat(LocalDate date, long totalMinor) {
	}

	public record WeekdayStat(String weekday, long totalMinor) {
	}

	public record TopExpense(String expenseId, String description, long amountMinor, String paidByName,
			LocalDate date, String categoryName) {
	}

	public record MatrixCell(String userId, String categoryKey, long shareMinor) {
	}

}
