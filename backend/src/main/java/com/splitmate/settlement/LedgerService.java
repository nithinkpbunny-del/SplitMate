package com.splitmate.settlement;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.splitmate.expense.Expense;
import com.splitmate.expense.ExpenseRepository;
import com.splitmate.expense.Share;
import com.splitmate.payment.Payment;
import com.splitmate.payment.PaymentRepository;
import com.splitmate.settlement.engine.LedgerExpense;
import com.splitmate.settlement.engine.LedgerPayment;
import com.splitmate.settlement.engine.SettlementCalculator;
import com.splitmate.settlement.engine.SettlementResult;
import com.splitmate.split.Member;
import com.splitmate.split.Split;

import org.springframework.stereotype.Service;

/**
 * Feeds the pure {@link SettlementCalculator} with a split's current expenses and payments.
 */
@Service
public class LedgerService {

	private final SettlementCalculator calculator = new SettlementCalculator();

	private final ExpenseRepository expenses;

	private final PaymentRepository payments;

	public LedgerService(ExpenseRepository expenses, PaymentRepository payments) {
		this.expenses = expenses;
		this.payments = payments;
	}

	/** Runs the engine on the split's live data. Members who left appear only if they are still involved. */
	public SettlementResult calculateLive(Split split) {
		List<Expense> live = expenses.findBySplitIdAndDeletedFalse(split.getId());
		List<Payment> paid = payments.findBySplitId(split.getId());
		Set<String> memberIds = new LinkedHashSet<>();
		split.activeMembers().forEach(m -> memberIds.add(m.getUserId().toHexString()));
		List<LedgerExpense> ledger = live.stream()
			.map(e -> new LedgerExpense(e.getId().toHexString(), e.getPaidBy().toHexString(), e.getAmountMinor(),
					e.getShares().stream().collect(Collectors.toMap(s -> s.userId().toHexString(), Share::amountMinor)),
					e.isDeleted()))
			.toList();
		List<LedgerPayment> ledgerPayments = paid.stream()
			.map(p -> new LedgerPayment(p.getFromUserId().toHexString(), p.getToUserId().toHexString(), p.getAmountMinor()))
			.toList();
		return calculator.calculate(memberIds, ledger, ledgerPayments);
	}

	public static Map<String, Member> membersById(Split split) {
		return split.getMembers().stream().collect(Collectors.toMap(m -> m.getUserId().toHexString(), m -> m));
	}

}
