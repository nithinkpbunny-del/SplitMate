package com.splitmate.report;

import java.time.Instant;
import java.time.LocalDate;

/** A row in the split's report list. */
public record ReportSummary(String id, Instant generatedAt, String generatedByName, LocalDate periodStart,
		LocalDate periodEnd, long totalSpendMinor, long expenseCount, String topInsight) {
}
