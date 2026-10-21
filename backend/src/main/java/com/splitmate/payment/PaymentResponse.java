package com.splitmate.payment;

import java.time.Instant;

/** {@code warning} is set when the payment is more than the latest calculation said was owed. */
public record PaymentResponse(String id, String splitId, String fromUserId, String toUserId, long amountMinor,
		String note, String settlementId, String recordedBy, Instant createdAt, String warning) {

	static PaymentResponse from(Payment p, String warning) {
		return new PaymentResponse(p.getId().toHexString(), p.getSplitId().toHexString(), p.getFromUserId().toHexString(),
				p.getToUserId().toHexString(), p.getAmountMinor(), p.getNote(),
				p.getSettlementId() == null ? null : p.getSettlementId().toHexString(), p.getRecordedBy().toHexString(),
				p.getCreatedAt(), warning);
	}

}
