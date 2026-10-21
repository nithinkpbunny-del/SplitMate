package com.splitmate.settlement.engine;

/** "from pays to amount" — one step of the settlement plan. */
public record Transfer(String fromUserId, String toUserId, long amountMinor) {
}
