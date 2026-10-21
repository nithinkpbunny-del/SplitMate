package com.splitmate.settlement.engine;

/** A real-world payment from one member to another. */
public record LedgerPayment(String fromUserId, String toUserId, long amountMinor) {
}
