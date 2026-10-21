package com.splitmate.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import com.splitmate.auth.PasswordRules;

public record ChangePasswordRequest(
		@NotBlank(message = "Enter your current password") String currentPassword,
		@NotBlank(message = "Choose a new password") @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE) String newPassword) {
}
