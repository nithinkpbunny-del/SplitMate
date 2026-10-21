package com.splitmate.split;

import org.bson.types.ObjectId;

import com.splitmate.common.ApiException;
import com.splitmate.common.Ids;

import org.springframework.stereotype.Component;

/**
 * Authorization for split-scoped endpoints. A caller who is not an active member gets 404, exactly as if
 * the split did not exist, so ids cannot be probed. Owner-only actions return 403 to other members.
 */
@Component
public class SplitAccess {

	static final String NOT_FOUND = "Split not found";

	private final SplitRepository splits;

	public SplitAccess(SplitRepository splits) {
		this.splits = splits;
	}

	public Split requireMember(String splitId, ObjectId userId) {
		return requireMember(Ids.parse(splitId, "Split"), userId);
	}

	public Split requireMember(ObjectId splitId, ObjectId userId) {
		Split split = splits.findById(splitId).orElseThrow(() -> ApiException.notFound(NOT_FOUND));
		if (!split.isActiveMember(userId)) {
			throw ApiException.notFound(NOT_FOUND);
		}
		return split;
	}

	public Split requireOwner(String splitId, ObjectId userId) {
		Split split = requireMember(splitId, userId);
		requireOwner(split, userId);
		return split;
	}

	public void requireOwner(Split split, ObjectId userId) {
		if (!split.isOwner(userId)) {
			throw ApiException.forbidden("Only the owner of this split can do that");
		}
	}

	/** Expenses and payments can only change while the split is open. */
	public void requireOpen(Split split) {
		switch (split.getStatus()) {
			case OPEN -> {
			}
			case SETTLED -> throw ApiException.conflict("This split is settled. The owner can reopen it to make changes.");
			case ARCHIVED -> throw ApiException.conflict("This split is archived. The owner can reopen it to make changes.");
		}
	}

	public String actorName(Split split, ObjectId userId) {
		return split.member(userId).map(Member::getName).orElse("Someone");
	}

}
