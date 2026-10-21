package com.splitmate.settlement;

import java.time.Instant;

/** A row in the calculation history list. */
public record SettlementHistoryItem(String id, int version, Instant calculatedAt, String calculatedByName,
		long totalSpendMinor, int expenseCount, int transferCount) {
}
