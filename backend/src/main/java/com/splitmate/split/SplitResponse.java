package com.splitmate.split;

import java.time.Instant;
import java.util.List;

/**
 * Full split as seen by a member. {@code members} lists everyone who ever joined (inactive ones are
 * still referenced by old expenses); {@code calculationStale} drives the "Recalculate" banner.
 */
public record SplitResponse(
		String id,
		String name,
		String description,
		String emoji,
		String currency,
		SplitStatus status,
		String ownerId,
		MemberRole myRole,
		List<MemberResponse> members,
		String inviteCode,
		String inviteUrl,
		boolean inviteEnabled,
		SplitPeriod period,
		long totalSpendMinor,
		long expenseCount,
		long myNetMinor,
		boolean currencyLocked,
		Instant lastCalculatedAt,
		boolean calculationStale,
		int settlementVersion,
		Instant createdAt,
		Instant updatedAt) {
}
