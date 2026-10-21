package com.splitmate.auth;

/** Password policy shared by registration and password change: 8+ chars with a letter and a number. */
public final class PasswordRules {

	public static final String REGEX = "^(?=.*\\p{L})(?=.*\\d).{8,100}$";

	public static final String MESSAGE = "Password needs at least 8 characters, including a letter and a number";

	private PasswordRules() {
	}

}
