package com.splitmate.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial profile update; null fields are left unchanged. */
public record UpdateProfileRequest(
		@Size(min = 2, max = 50, message = "Name must be 2 to 50 characters")
		@Pattern(regexp = ".*\\S.*", message = "Enter your name") String name,
		String avatarColor) {
}
