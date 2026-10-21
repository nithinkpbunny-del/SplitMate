package com.splitmate.settlement.engine;

import java.util.List;

/**
 * Output of {@link SettlementCalculator#calculate}.
 * @param balances one entry per member, ordered by user id
 * @param transfers the settlement plan (at most n − 1 transfers)
 * @param totalSpendMinor sum of all included expenses
 * @param includedExpenseIds the non-deleted expenses that were counted
 */
public record SettlementResult(List<MemberBalance> balances, List<Transfer> transfers, long totalSpendMinor,
		List<String> includedExpenseIds) {

	public boolean isAllSettled() {
		return balances.stream().allMatch(b -> b.netMinor() == 0);
	}

}
