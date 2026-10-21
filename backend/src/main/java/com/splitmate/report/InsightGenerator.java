package com.splitmate.report;

import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.splitmate.common.Money;

import org.springframework.stereotype.Component;

/**
 * Turns report statistics into plain-English observations. Every sentence is computed from the data; a rule
 * only produces an insight when its data supports one, and at most seven are returned.
 */
@Component
public class InsightGenerator {

	private static final int MAX_INSIGHTS = 7;

	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.UK);

	/** Total of an earlier split with the same members, for the month-over-month comparison. */
	public record PreviousPeriod(String splitName, long totalSpendMinor) {
	}

	public List<String> generate(ReportStats stats, String currency, int memberCount, PreviousPeriod previous) {
		List<String> insights = new ArrayList<>();
		if (stats.expenseCount() == 0) {
			insights.add("No expenses were recorded in this period.");
			return insights;
		}

		topCategory(stats, insights);
		highestDay(stats, currency, insights);
		biggestExpense(stats, currency, insights);
		paidVsConsumed(stats, currency, insights);
		perPersonPerDay(stats, currency, memberCount, insights);
		weekendVsWeekday(stats, currency, insights);
		comparedToPrevious(stats, currency, previous, insights);

		return insights.size() > MAX_INSIGHTS ? insights.subList(0, MAX_INSIGHTS) : insights;
	}

	private void topCategory(ReportStats stats, List<String> out) {
		if (stats.byCategory().isEmpty()) {
			return;
		}
		ReportStats.CategoryStat top = stats.byCategory().getFirst();
		if (stats.byCategory().size() == 1) {
			out.add("All spending this period was on " + top.name() + ".");
			return;
		}
		out.add(top.name() + " made up " + percent(top.percent()) + " of all spending.");
	}

	private void highestDay(ReportStats stats, String currency, List<String> out) {
		stats.dailyTrend().stream().max(Comparator.comparingLong(ReportStats.DailyStat::totalMinor)).ifPresent(day -> {
			if (day.totalMinor() > 0 && stats.dailyTrend().size() > 1) {
				out.add("The most expensive day was " + day.date().format(DAY) + " ("
						+ Money.format(day.totalMinor(), currency) + ").");
			}
		});
	}

	private void biggestExpense(ReportStats stats, String currency, List<String> out) {
		if (stats.topExpenses().isEmpty()) {
			return;
		}
		ReportStats.TopExpense top = stats.topExpenses().getFirst();
		String share = stats.totalSpendMinor() == 0 ? ""
				: ", " + percent(top.amountMinor() * 100.0 / stats.totalSpendMinor()) + " of the total";
		out.add("The biggest single expense was " + top.description() + " at "
				+ Money.format(top.amountMinor(), currency) + (top.paidByName() == null ? "" : ", paid by " + firstName(top.paidByName()))
				+ share + ".");
	}

	private void paidVsConsumed(ReportStats stats, String currency, List<String> out) {
		if (stats.byMember().size() < 2) {
			return;
		}
		ReportStats.MemberStat payer = stats.byMember().stream().max(Comparator.comparingLong(ReportStats.MemberStat::paidMinor)).orElseThrow();
		ReportStats.MemberStat consumer = stats.byMember().stream().max(Comparator.comparingLong(ReportStats.MemberStat::shareMinor)).orElseThrow();
		if (payer.userId().equals(consumer.userId())) {
			out.add(firstName(payer.name()) + " both paid the most (" + Money.format(payer.paidMinor(), currency)
					+ ") and had the largest share (" + Money.format(consumer.shareMinor(), currency) + ").");
		}
		else {
			out.add(firstName(payer.name()) + " paid the most (" + Money.format(payer.paidMinor(), currency) + "), while "
					+ firstName(consumer.name()) + " had the largest share (" + Money.format(consumer.shareMinor(), currency)
					+ ").");
		}
	}

	private void perPersonPerDay(ReportStats stats, String currency, int memberCount, List<String> out) {
		if (memberCount < 1 || stats.days() < 1) {
			return;
		}
		long perPersonPerDay = ReportAggregations.divideRounded(stats.totalSpendMinor(), (long) memberCount * stats.days());
		out.add("On average each person spent " + Money.format(perPersonPerDay, currency) + " per day over "
				+ stats.days() + " days.");
	}

	private void weekendVsWeekday(ReportStats stats, String currency, List<String> out) {
		long weekendTotal = 0;
		long weekdayTotal = 0;
		int weekendDays = 0;
		int weekdayDays = 0;
		for (ReportStats.DailyStat day : stats.dailyTrend()) {
			DayOfWeek dow = day.date().getDayOfWeek();
			if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
				weekendTotal += day.totalMinor();
				weekendDays++;
			}
			else {
				weekdayTotal += day.totalMinor();
				weekdayDays++;
			}
		}
		if (weekendDays == 0 || weekdayDays == 0) {
			return;
		}
		long weekendAvg = ReportAggregations.divideRounded(weekendTotal, weekendDays);
		long weekdayAvg = ReportAggregations.divideRounded(weekdayTotal, weekdayDays);
		if (weekendAvg == 0 && weekdayAvg == 0) {
			return;
		}
		String comparison;
		if (weekdayAvg == 0 || weekendAvg == 0) {
			comparison = weekendAvg > weekdayAvg ? "Almost all spending happened at weekends"
					: "Almost all spending happened on weekdays";
			out.add(comparison + ".");
			return;
		}
		double ratio = (double) weekendAvg / weekdayAvg;
		String direction = ratio >= 1 ? percent((ratio - 1) * 100) + " more" : percent((1 - ratio) * 100) + " less";
		out.add("Weekends averaged " + Money.format(weekendAvg, currency) + " a day versus "
				+ Money.format(weekdayAvg, currency) + " on weekdays (" + direction + ").");
	}

	private void comparedToPrevious(ReportStats stats, String currency, PreviousPeriod previous, List<String> out) {
		if (previous == null || previous.totalSpendMinor() <= 0) {
			return;
		}
		long diff = stats.totalSpendMinor() - previous.totalSpendMinor();
		double change = Math.abs(diff) * 100.0 / previous.totalSpendMinor();
		if (Math.round(change) == 0) {
			out.add("Spending was about the same as " + previous.splitName() + " ("
					+ Money.format(previous.totalSpendMinor(), currency) + ").");
			return;
		}
		out.add("Spending is " + (diff > 0 ? "up " : "down ") + percent(change) + " compared with "
				+ previous.splitName() + " (" + Money.format(previous.totalSpendMinor(), currency) + ").");
	}

	private static String percent(double value) {
		return Math.round(value) + "%";
	}

	private static String firstName(String name) {
		String trimmed = name.trim();
		int space = trimmed.indexOf(' ');
		return space < 0 ? trimmed : trimmed.substring(0, space);
	}

}
