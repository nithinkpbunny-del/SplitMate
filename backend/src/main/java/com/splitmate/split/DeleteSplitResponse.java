package com.splitmate.split;

/** DELETE either removes an empty split or, when it already has expenses, archives it. */
public record DeleteSplitResponse(boolean deleted, boolean archived, String message) {
}
