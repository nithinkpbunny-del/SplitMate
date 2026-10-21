package com.splitmate.expense;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ExpenseResponse(
		String id,
		String splitId,
		String description,
		long amountMinor,
		String currency,
		String categoryId,
		String paidBy,
		SplitType splitType,
		List<String> participants,
		List<ShareResponse> shares,
		LocalDate date,
		String notes,
		String createdBy,
		boolean canEdit,
		Instant createdAt,
		Instant updatedAt) {

	public record ShareResponse(String userId, long amountMinor) {
	}

	static ExpenseResponse from(Expense e, boolean canEdit) {
		return new ExpenseResponse(e.getId().toHexString(), e.getSplitId().toHexString(), e.getDescription(),
				e.getAmountMinor(), e.getCurrency(), e.getCategoryId().toHexString(), e.getPaidBy().toHexString(),
				e.getSplitType(), e.getParticipants().stream().map(id -> id.toHexString()).toList(),
				e.getShares().stream().map(s -> new ShareResponse(s.userId().toHexString(), s.amountMinor())).toList(),
				e.getDate(), e.getNotes(), e.getCreatedBy().toHexString(), canEdit, e.getCreatedAt(), e.getUpdatedAt());
	}

}
