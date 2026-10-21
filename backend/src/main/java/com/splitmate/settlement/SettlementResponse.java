package com.splitmate.settlement;

import java.time.Instant;
import java.util.List;

public record SettlementResponse(
		String id,
		String splitId,
		int version,
		String calculatedBy,
		String calculatedByName,
		Instant calculatedAt,
		String currency,
		long totalSpendMinor,
		int expenseCount,
		List<String> expenseIds,
		List<MemberSummaryResponse> memberSummaries,
		List<TransferResponse> transfers,
		boolean allSettled) {

	public record MemberSummaryResponse(String userId, String name, String avatarColor, long paidMinor,
			long shareMinor, long paymentsSentMinor, long paymentsReceivedMinor, long netMinor) {
	}

	public record TransferResponse(String fromUserId, String fromName, String toUserId, String toName,
			long amountMinor) {
	}

	static SettlementResponse from(Settlement s, String calculatedByName) {
		return new SettlementResponse(s.getId().toHexString(), s.getSplitId().toHexString(), s.getVersion(),
				s.getCalculatedBy().toHexString(), calculatedByName, s.getCalculatedAt(), s.getCurrency(),
				s.getTotalSpendMinor(), s.getExpenseCount(), s.getExpenseIds().stream().map(id -> id.toHexString()).toList(),
				s.getMemberSummaries()
					.stream()
					.map(m -> new MemberSummaryResponse(m.userId().toHexString(), m.name(), m.avatarColor(), m.paidMinor(),
							m.shareMinor(), m.paymentsSentMinor(), m.paymentsReceivedMinor(), m.netMinor()))
					.toList(),
				s.getTransfers()
					.stream()
					.map(t -> new TransferResponse(t.fromUserId().toHexString(), t.fromName(), t.toUserId().toHexString(),
							t.toName(), t.amountMinor()))
					.toList(),
				s.getMemberSummaries().stream().allMatch(m -> m.netMinor() == 0));
	}

}
