package com.splitmate.split;

import org.bson.types.ObjectId;

/** Published after an empty split is deleted, so each module can remove its own documents. */
public record SplitDeletedEvent(ObjectId splitId) {
}
