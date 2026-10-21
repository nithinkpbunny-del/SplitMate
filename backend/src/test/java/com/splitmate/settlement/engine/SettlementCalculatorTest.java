package com.splitmate.settlement.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The required engine tests from the spec (§6.5) plus a few extra edge cases.
 */
class SettlementCalculatorTest {

	private static final String A = "a";

	private static final String B = "b";

	private static final String C = "c";

	private final SettlementCalculator calculator = new SettlementCalculator();

	private static LedgerExpense equal(String id, String paidBy, long amount, String... participants) {
		return new LedgerExpense(id, paidBy, amount, EqualSplit.shares(amount, List.of(participants)), false);
	}

	private static Map<String, Long> nets(SettlementResult result) {
		return result.balances().stream().collect(Collectors.toMap(MemberBalance::userId, MemberBalance::netMinor));
	}

	/** A pays €90 groceries for A, B, C; B pays €40 dinner for B, C. */
	private static List<LedgerExpense> workedExample() {
		return List.of(equal("groceries", A, 9000, A, B, C), equal("dinner", B, 4000, B, C));
	}

	@Test
	@DisplayName("1. Worked example: A +60, B −10, C −50; C→A €50, B→A €10")
	void workedExampleFromTheSpec() {
		SettlementResult result = calculator.calculate(List.of(A, B, C), workedExample(), List.of());

		assertThat(nets(result)).containsExactlyInAnyOrderEntriesOf(Map.of(A, 6000L, B, -1000L, C, -5000L));
		assertThat(result.transfers()).containsExactly(new Transfer(C, A, 5000), new Transfer(B, A, 1000));
		assertThat(result.totalSpendMinor()).isEqualTo(13000);

		MemberBalance a = result.balances().getFirst();
		assertThat(a.paidMinor()).isEqualTo(9000);
		assertThat(a.shareMinor()).isEqualTo(3000);
	}

	@Test
	@DisplayName("2. €10.00 among 3 → 334 / 333 / 333, sum exact")
	void tenEurosAmongThree() {
		Map<String, Long> shares = EqualSplit.shares(1000, List.of(C, A, B));

		assertThat(shares).containsExactly(Map.entry(A, 334L), Map.entry(B, 333L), Map.entry(C, 333L));
		assertThat(shares.values().stream().mapToLong(Long::longValue).sum()).isEqualTo(1000);
	}

	@Test
	@DisplayName("3. Payer not among participants: A pays €30 for B and C → A +30, B −15, C −15")
	void payerNotAParticipant() {
		SettlementResult result = calculator.calculate(List.of(A, B, C), List.of(equal("taxi", A, 3000, B, C)), List.of());

		assertThat(nets(result)).containsExactlyInAnyOrderEntriesOf(Map.of(A, 3000L, B, -1500L, C, -1500L));
		assertThat(result.transfers()).hasSize(2);
	}

	@Test
	@DisplayName("4. Payment C→A €50 after the worked example → C 0, A +10")
	void paymentReducesBalances() {
		SettlementResult result = calculator.calculate(List.of(A, B, C), workedExample(),
				List.of(new LedgerPayment(C, A, 5000)));

		assertThat(nets(result)).containsExactlyInAnyOrderEntriesOf(Map.of(A, 1000L, B, -1000L, C, 0L));
		assertThat(result.transfers()).containsExactly(new Transfer(B, A, 1000));
		MemberBalance c = result.balances().get(2);
		assertThat(c.paymentsSentMinor()).isEqualTo(5000);
	}

	@Test
	@DisplayName("5. Everyone balanced → no transfers, split can be marked settled")
	void allBalanced() {
		SettlementResult result = calculator.calculate(List.of(A, B, C), workedExample(),
				List.of(new LedgerPayment(C, A, 5000), new LedgerPayment(B, A, 1000)));

		assertThat(result.transfers()).isEmpty();
		assertThat(result.isAllSettled()).isTrue();
	}

	@Test
	@DisplayName("6. Property: 1,000 random groups → Σ net = 0, transfers settle exactly, ≤ n−1 transfers")
	void randomisedProperty() {
		Random random = new Random(20261001);
		for (int run = 0; run < 1000; run++) {
			int n = 1 + random.nextInt(10);
			List<String> members = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				members.add(String.format("u%02d", i));
			}
			List<LedgerExpense> expenses = new ArrayList<>();
			int count = random.nextInt(40);
			for (int e = 0; e < count; e++) {
				List<String> participants = new ArrayList<>(members.stream().filter(m -> random.nextBoolean()).toList());
				if (participants.isEmpty()) {
					participants.add(members.get(random.nextInt(n)));
				}
				long amount = 1 + random.nextInt(250_000);
				String payer = members.get(random.nextInt(n));
				expenses.add(new LedgerExpense("e" + e, payer, amount, EqualSplit.shares(amount, participants),
						random.nextInt(10) == 0));
			}
			List<LedgerPayment> payments = new ArrayList<>();
			if (n > 1) {
				for (int p = 0; p < random.nextInt(5); p++) {
					String from = members.get(random.nextInt(n));
					String to = members.get(random.nextInt(n));
					if (!from.equals(to)) {
						payments.add(new LedgerPayment(from, to, 1 + random.nextInt(50_000)));
					}
				}
			}

			SettlementResult result = calculator.calculate(members, expenses, payments);

			assertThat(result.balances().stream().mapToLong(MemberBalance::netMinor).sum()).isZero();
			Map<String, Long> remaining = new HashMap<>(nets(result));
			for (Transfer t : result.transfers()) {
				assertThat(t.amountMinor()).isPositive();
				remaining.merge(t.fromUserId(), t.amountMinor(), Long::sum);
				remaining.merge(t.toUserId(), -t.amountMinor(), Long::sum);
			}
			assertThat(remaining.values()).allMatch(v -> v == 0L);
			assertThat(result.transfers().size()).isLessThanOrEqualTo(Math.max(0, n - 1));
			// deterministic: the same input gives the same plan
			assertThat(calculator.calculate(members, expenses, payments)).isEqualTo(result);
		}
	}

	@Test
	@DisplayName("7. Single-member split → no transfers")
	void singleMember() {
		SettlementResult result = calculator.calculate(List.of(A), List.of(equal("solo", A, 4999, A)), List.of());

		assertThat(result.transfers()).isEmpty();
		assertThat(nets(result)).containsExactly(Map.entry(A, 0L));
	}

	@Test
	@DisplayName("8. Deleted expenses are ignored")
	void deletedExpensesIgnored() {
		List<LedgerExpense> expenses = new ArrayList<>(workedExample());
		expenses.add(new LedgerExpense("deleted", C, 100000, EqualSplit.shares(100000, List.of(A, B, C)), true));

		SettlementResult result = calculator.calculate(List.of(A, B, C), expenses, List.of());

		assertThat(nets(result)).containsExactlyInAnyOrderEntriesOf(Map.of(A, 6000L, B, -1000L, C, -5000L));
		assertThat(result.includedExpenseIds()).containsExactly("groceries", "dinner");
		assertThat(result.totalSpendMinor()).isEqualTo(13000);
	}

	@Nested
	class EdgeCases {

		@Test
		void tiesAreBrokenByUserIdSoResultsAreStable() {
			// A and B are both owed 10, C and D both owe 10
			List<LedgerExpense> expenses = List.of(equal("x", A, 2000, A, C), equal("y", B, 2000, B, "d"));
			SettlementResult result = calculator.calculate(List.of("d", C, B, A), expenses, List.of());
			assertThat(result.transfers()).containsExactly(new Transfer(C, A, 1000), new Transfer("d", B, 1000));
		}

		@Test
		void membersWithNothingToDoStillAppear() {
			SettlementResult result = calculator.calculate(List.of(A, B, C), List.of(equal("x", A, 1000, A, B)), List.of());
			assertThat(result.balances()).extracting(MemberBalance::userId).containsExactly(A, B, C);
		}

		@Test
		void sharesThatDoNotAddUpAreRejected() {
			LedgerExpense broken = new LedgerExpense("bad", A, 1000, Map.of(A, 500L, B, 400L), false);
			assertThatThrownBy(() -> calculator.calculate(List.of(A, B), List.of(broken), List.of()))
				.isInstanceOf(IllegalStateException.class);
		}

		@Test
		void equalSplitRejectsBadInput() {
			assertThatThrownBy(() -> EqualSplit.shares(0, List.of(A))).isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> EqualSplit.shares(100, List.of())).isInstanceOf(IllegalArgumentException.class);
			assertThat(EqualSplit.shares(1, List.of(A, B, C))).containsExactly(Map.entry(A, 1L), Map.entry(B, 0L),
					Map.entry(C, 0L));
		}

	}

}
