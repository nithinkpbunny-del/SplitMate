package com.splitmate.payment;

import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;

import com.splitmate.activity.ActivityService;
import com.splitmate.activity.ActivityType;
import com.splitmate.common.ApiException;
import com.splitmate.common.Ids;
import com.splitmate.common.Money;
import com.splitmate.settlement.Settlement;
import com.splitmate.settlement.SettlementService;
import com.splitmate.split.Member;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;
import com.splitmate.split.SplitService;

import org.springframework.stereotype.Service;

/**
 * "I paid you back" records. Payments shift balances the next time the split is calculated.
 */
@Service
public class PaymentService {

	private final PaymentRepository payments;

	private final SplitAccess access;

	private final SplitService splits;

	private final SettlementService settlements;

	private final ActivityService activity;

	public PaymentService(PaymentRepository payments, SplitAccess access, SplitService splits,
			SettlementService settlements, ActivityService activity) {
		this.payments = payments;
		this.access = access;
		this.splits = splits;
		this.settlements = settlements;
		this.activity = activity;
	}

	public PaymentResponse record(String splitId, ObjectId userId, PaymentRequest request) {
		Split split = access.requireMember(splitId, userId);
		access.requireOpen(split);
		ObjectId from = Ids.parseField(request.fromUserId(), "fromUserId");
		ObjectId to = Ids.parseField(request.toUserId(), "toUserId");
		if (from.equals(to)) {
			throw ApiException.badRequest("Choose two different people");
		}
		Member payer = activeMember(split, from);
		Member payee = activeMember(split, to);
		if (!userId.equals(from) && !userId.equals(to) && !split.isOwner(userId)) {
			throw ApiException.forbidden("Only the payer, the person paid, or the split owner can record this payment");
		}
		long amount = Money.parse(request.amount());

		Settlement latest = settlements.latestSnapshot(split.getId()).orElse(null);
		String warning = overpaymentWarning(split, latest, payer, payee, amount);

		Payment payment = new Payment();
		payment.setSplitId(split.getId());
		payment.setFromUserId(from);
		payment.setToUserId(to);
		payment.setAmountMinor(amount);
		payment.setNote(request.note());
		payment.setSettlementId(latest == null ? null : latest.getId());
		payment.setRecordedBy(userId);
		payment = payments.save(payment);
		splits.markPaymentsChanged(split.getId());

		String actor = access.actorName(split, userId);
		activity.log(split.getId(), userId, actor, ActivityType.PAYMENT_RECORDED,
				payer.getName() + " paid " + payee.getName() + " " + Money.format(amount, split.getCurrency())
						+ (payment.getNote() == null ? "" : " (" + payment.getNote() + ")"),
				Map.of("paymentId", payment.getId().toHexString(), "amountMinor", amount));
		return PaymentResponse.from(payment, warning);
	}

	public List<PaymentResponse> list(String splitId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		return payments.findBySplitIdOrderByCreatedAtDesc(split.getId())
			.stream()
			.map(p -> PaymentResponse.from(p, null))
			.toList();
	}

	public void delete(String splitId, String paymentId, ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		access.requireOpen(split);
		Payment payment = payments.findByIdAndSplitId(Ids.parse(paymentId, "Payment"), split.getId())
			.orElseThrow(() -> ApiException.notFound("Payment not found"));
		if (!payment.getRecordedBy().equals(userId) && !split.isOwner(userId)) {
			throw ApiException.forbidden("Only the person who recorded it or the split owner can delete this payment");
		}
		payments.delete(payment);
		splits.markPaymentsChanged(split.getId());

		String actor = access.actorName(split, userId);
		String from = split.member(payment.getFromUserId()).map(Member::getName).orElse("Someone");
		String to = split.member(payment.getToUserId()).map(Member::getName).orElse("someone");
		activity.log(split.getId(), userId, actor, ActivityType.PAYMENT_DELETED,
				actor + " deleted the payment " + from + " → " + to + " ("
						+ Money.format(payment.getAmountMinor(), split.getCurrency()) + ")");
	}

	/**
	 * Compares the amount with what the payer still owes the payee per the latest calculation (minus
	 * payments between them recorded since). Overpaying is allowed, it just gets a clear warning.
	 */
	private String overpaymentWarning(Split split, Settlement latest, Member payer, Member payee, long amount) {
		if (latest == null) {
			return null;
		}
		long owed = latest.getTransfers()
			.stream()
			.filter(t -> t.fromUserId().equals(payer.getUserId()) && t.toUserId().equals(payee.getUserId()))
			.mapToLong(Settlement.TransferRecord::amountMinor)
			.sum();
		long alreadyPaid = payments.findBySplitId(split.getId())
			.stream()
			.filter(p -> p.getFromUserId().equals(payer.getUserId()) && p.getToUserId().equals(payee.getUserId()))
			.filter(p -> p.getCreatedAt() != null && !p.getCreatedAt().isBefore(latest.getCalculatedAt()))
			.mapToLong(Payment::getAmountMinor)
			.sum();
		long remaining = Math.max(0, owed - alreadyPaid);
		if (amount <= remaining) {
			return null;
		}
		String currency = split.getCurrency();
		if (remaining == 0) {
			return "In the latest calculation " + payer.getName() + " didn't owe " + payee.getName()
					+ " anything. The payment is recorded; after recalculating, " + payee.getName() + " will owe it back.";
		}
		return payer.getName() + " only owed " + payee.getName() + " " + Money.format(remaining, currency)
				+ " in the latest calculation. The extra " + Money.format(amount - remaining, currency)
				+ " is recorded too and will show up after recalculating.";
	}

	private static Member activeMember(Split split, ObjectId userId) {
		return split.member(userId)
			.filter(Member::isActive)
			.orElseThrow(() -> ApiException.badRequest("Both people must be members of this split"));
	}

}
