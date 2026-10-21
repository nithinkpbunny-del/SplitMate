package com.splitmate.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "Enter your email") @Size(max = 254) String email,
		@NotBlank(message = "Enter your password") @Size(max = 100) String password) {

	public LoginRequest {
		email = email == null ? null : email.trim();
	}

}
