package com.splitmate.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
		@NotBlank(message = "Enter your name") @Size(min = 2, max = 50, message = "Name must be 2 to 50 characters") String name,
		@NotBlank(message = "Enter your email") @Email(message = "Enter a valid email address") @Size(max = 254) String email,
		@NotBlank(message = "Choose a password") @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE) String password) {

	public RegisterRequest {
		name = name == null ? null : name.trim();
		email = email == null ? null : email.trim();
	}

}
