package com.splitmate.user;

import java.time.Instant;

/**
 * Public view of a user. Never contains the password hash.
 */
public record UserResponse(String id, String name, String email, String avatarColor, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId().toHexString(), user.getName(), user.getEmail(), user.getAvatarColor(),
				user.getCreatedAt());
	}

}
