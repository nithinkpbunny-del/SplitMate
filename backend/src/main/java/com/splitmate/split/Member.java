package com.splitmate.split;

import java.time.Instant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

/**
 * A person in a split, embedded in the split document (splits are small and bounded). Name, email and
 * colour are copied from the user so a split renders without extra lookups; they are refreshed when the
 * user edits their profile. Members who leave stay in the array with {@code active=false}, because old
 * expenses and settlements still refer to them.
 */
@Getter
@Setter
@NoArgsConstructor
public class Member {

	private ObjectId userId;

	private String name;

	private String email;

	private String avatarColor;

	private MemberRole role;

	private Instant joinedAt;

	private boolean active;

	public Member(ObjectId userId, String name, String email, String avatarColor, MemberRole role, Instant joinedAt) {
		this.userId = userId;
		this.name = name;
		this.email = email;
		this.avatarColor = avatarColor;
		this.role = role;
		this.joinedAt = joinedAt;
		this.active = true;
	}

}
