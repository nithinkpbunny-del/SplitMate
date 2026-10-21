package com.splitmate.expense;

import java.time.LocalDate;

/**
 * Query-string filters for the expense list. Sort is one of {@code date_desc} (default), {@code date_asc},
 * {@code amount_desc}, {@code amount_asc}.
 */
public record ExpenseFilter(String category, String paidBy, String participant, LocalDate from, LocalDate to, String q,
		String sort, Integer page, Integer size) {
}
