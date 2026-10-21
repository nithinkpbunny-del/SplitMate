package com.splitmate.settlement.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * The split calculation engine. Pure Java (no Spring, no MongoDB types), deterministic, exact to the cent.
 *
 * <h2>Balances</h2>
 * For every non-deleted expense and every payment:
 * <pre>
 * paid(u)             = Σ amount of expenses paid by u
 * share(u)            = Σ u's share across expenses
 * paymentsSent(u)     = Σ payments from u
 * paymentsReceived(u) = Σ payments to u
 * net(u)              = paid − share + paymentsSent − paymentsReceived
 * </pre>
 * Invariant: Σ net = 0. If it ever fails, that is a bug, and an exception is thrown instead of hiding it.
 *
 * <h2>Who pays whom</h2>
 * Greedy settlement: repeatedly match the largest debtor with the largest creditor and transfer the
 * smaller of the two amounts. Every step zeroes at least one person (the last step zeroes two), so there
 * are at most n − 1 transfers. Ties are broken by user id, so the result is identical on every run.
 */
public final class SettlementCalculator {

	/**
	 * @param memberIds everyone who should appear in the result, even with nothing to do (ids found only in
	 * expenses or payments, e.g. people who have since left, are added automatically)
	 */
	public SettlementResult calculate(Collection<String> memberIds, List<LedgerExpense> expenses,
			List<LedgerPayment> payments) {
		Map<String, long[]> acc = new TreeMap<>(); // userId → [paid, share, sent, received]
		memberIds.forEach(id -> acc.computeIfAbsent(id, k -> new long[4]));

		long total = 0;
		List<String> included = new ArrayList<>();
		for (LedgerExpense expense : expenses) {
			if (expense.deleted()) {
				continue;
			}
			long shareSum = expense.shares().values().stream().mapToLong(Long::longValue).sum();
			if (shareSum != expense.amountMinor()) {
				throw new IllegalStateException("Expense " + expense.id() + " shares sum to " + shareSum
						+ " but the amount is " + expense.amountMinor());
			}
			acc.computeIfAbsent(expense.paidBy(), k -> new long[4])[0] += expense.amountMinor();
			expense.shares().forEach((userId, share) -> acc.computeIfAbsent(userId, k -> new long[4])[1] += share);
			total += expense.amountMinor();
			included.add(expense.id());
		}
		for (LedgerPayment payment : payments) {
			if (payment.amountMinor() <= 0 || payment.fromUserId().equals(payment.toUserId())) {
				throw new IllegalArgumentException("Invalid payment " + payment);
			}
			acc.computeIfAbsent(payment.fromUserId(), k -> new long[4])[2] += payment.amountMinor();
			acc.computeIfAbsent(payment.toUserId(), k -> new long[4])[3] += payment.amountMinor();
		}

		List<MemberBalance> balances = new ArrayList<>();
		long netSum = 0;
		for (Map.Entry<String, long[]> entry : acc.entrySet()) {
			long[] v = entry.getValue();
			long net = v[0] - v[1] + v[2] - v[3];
			netSum += net;
			balances.add(new MemberBalance(entry.getKey(), v[0], v[1], v[2], v[3], net));
		}
		if (netSum != 0) {
			throw new IllegalStateException("Balances do not sum to zero (" + netSum + "): this is a bug");
		}

		return new SettlementResult(List.copyOf(balances), settle(balances), total, List.copyOf(included));
	}

	/** Greedy minimum-transfer plan for the given balances. */
	public List<Transfer> settle(List<MemberBalance> balances) {
		record Position(String userId, long amount) {
		}
		// Largest amount first; equal amounts by user id so the plan is deterministic.
		Comparator<Position> largestFirst = Comparator.comparingLong(Position::amount)
			.reversed()
			.thenComparing(Position::userId);
		PriorityQueue<Position> creditors = new PriorityQueue<>(largestFirst);
		PriorityQueue<Position> debtors = new PriorityQueue<>(largestFirst);
		for (MemberBalance b : balances) {
			if (b.netMinor() > 0) {
				creditors.add(new Position(b.userId(), b.netMinor()));
			}
			else if (b.netMinor() < 0) {
				debtors.add(new Position(b.userId(), -b.netMinor()));
			}
		}

		List<Transfer> transfers = new ArrayList<>();
		while (!creditors.isEmpty() && !debtors.isEmpty()) {
			Position debtor = debtors.poll();
			Position creditor = creditors.poll();
			long amount = Math.min(debtor.amount(), creditor.amount());
			transfers.add(new Transfer(debtor.userId(), creditor.userId(), amount));
			if (debtor.amount() > amount) {
				debtors.add(new Position(debtor.userId(), debtor.amount() - amount));
			}
			if (creditor.amount() > amount) {
				creditors.add(new Position(creditor.userId(), creditor.amount() - amount));
			}
		}
		if (!creditors.isEmpty() || !debtors.isEmpty()) {
			throw new IllegalStateException("Settlement left unmatched balances: this is a bug");
		}
		return List.copyOf(transfers);
	}

}
