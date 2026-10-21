package com.splitmate.split;

import java.time.Instant;
import java.util.List;

/** A split card on the dashboard. */
public record SplitSummaryResponse(
		String id,
		String name,
		String description,
		String emoji,
		String currency,
		SplitStatus status,
		SplitPeriod period,
		MemberRole myRole,
		List<MemberResponse> members,
		int memberCount,
		long totalSpendMinor,
		long expenseCount,
		long myNetMinor,
		boolean calculationStale,
		Instant lastActivityAt,
		Instant createdAt) {
}
