package com.splitmate.split;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A group of people sharing expenses, always called a "Split". Collection {@code splits}.
 */
@Document("splits")
@CompoundIndex(name = "members_userId", def = "{'members.userId': 1}")
@Getter
@Setter
@NoArgsConstructor
public class Split {

	@Id
	private ObjectId id;

	private String name;

	private String description;

	private String emoji;

	/** ISO 4217 code; fixed once the first expense exists. */
	private String currency;

	@Indexed
	private ObjectId ownerId;

	private List<Member> members = new ArrayList<>();

	@Indexed(unique = true)
	private String inviteCode;

	private boolean inviteEnabled;

	private Instant inviteRegeneratedAt;

	private SplitPeriod period;

	private SplitStatus status;

	private Instant lastCalculatedAt;

	private Instant lastExpenseChangeAt;

	private Instant lastPaymentChangeAt;

	/** Counter for settlement snapshot versions, incremented atomically with findAndModify. */
	private int settlementVersion;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	public Optional<Member> member(ObjectId userId) {
		return members.stream().filter(m -> m.getUserId().equals(userId)).findFirst();
	}

	public boolean isActiveMember(ObjectId userId) {
		return member(userId).map(Member::isActive).orElse(false);
	}

	public boolean isOwner(ObjectId userId) {
		return ownerId.equals(userId);
	}

	public List<Member> activeMembers() {
		return members.stream().filter(Member::isActive).toList();
	}

	/**
	 * True when expenses or payments changed after the last calculation, so the shown balances are out of date.
	 */
	public boolean isCalculationStale() {
		if (lastCalculatedAt == null) {
			return lastExpenseChangeAt != null || lastPaymentChangeAt != null;
		}
		return (lastExpenseChangeAt != null && lastExpenseChangeAt.isAfter(lastCalculatedAt))
				|| (lastPaymentChangeAt != null && lastPaymentChangeAt.isAfter(lastCalculatedAt));
	}

}
