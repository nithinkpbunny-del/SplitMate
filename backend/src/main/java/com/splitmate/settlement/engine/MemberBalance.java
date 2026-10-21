package com.splitmate.settlement.engine;

/**
 * One member's position. {@code net = paid − share + paymentsSent − paymentsReceived};
 * positive means the group owes them (they get money back), negative means they owe.
 */
public record MemberBalance(String userId, long paidMinor, long shareMinor, long paymentsSentMinor,
		long paymentsReceivedMinor, long netMinor) {
}
