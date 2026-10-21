package com.splitmate.expense;

import java.util.List;

/** One page of expenses plus the total of everything matching the current filters. */
public record ExpensePage(List<ExpenseResponse> items, int page, int size, long totalItems, int totalPages,
		long totalAmountMinor) {
}
