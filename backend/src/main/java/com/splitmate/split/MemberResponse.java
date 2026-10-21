package com.splitmate.split;

import java.time.Instant;

public record MemberResponse(String userId, String name, String email, String avatarColor, MemberRole role,
		Instant joinedAt, boolean active) {

	public static MemberResponse from(Member m) {
		return new MemberResponse(m.getUserId().toHexString(), m.getName(), m.getEmail(), m.getAvatarColor(),
				m.getRole(), m.getJoinedAt(), m.isActive());
	}

}
