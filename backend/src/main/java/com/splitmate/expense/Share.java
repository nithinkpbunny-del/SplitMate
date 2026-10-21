package com.splitmate.expense;

import org.bson.types.ObjectId;

/** One participant's part of an expense, in minor units. Computed by the server. */
public record Share(ObjectId userId, long amountMinor) {
}
