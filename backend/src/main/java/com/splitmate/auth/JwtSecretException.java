package com.splitmate.auth;

/** The signing secret is missing or too short. Reported at startup by {@link JwtSecretFailureAnalyzer}. */
public class JwtSecretException extends IllegalStateException {

	public JwtSecretException(String message) {
		super(message);
	}

}
