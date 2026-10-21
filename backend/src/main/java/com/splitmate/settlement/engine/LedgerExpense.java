package com.splitmate.settlement.engine;

import java.util.Map;

/**
 * An expense as the calculator sees it.
 * @param shares participant id → share in minor units (must sum to {@code amountMinor})
 */
public record LedgerExpense(String id, String paidBy, long amountMinor, Map<String, Long> shares, boolean deleted) {

	public LedgerExpense {
		shares = Map.copyOf(shares);
	}

}
