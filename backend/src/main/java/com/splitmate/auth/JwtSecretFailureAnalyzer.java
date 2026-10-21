package com.splitmate.auth;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;

/**
 * Turns the long nested startup exception for a missing or short JWT_SECRET into Spring Boot's short
 * "APPLICATION FAILED TO START" message with an action.
 */
class JwtSecretFailureAnalyzer extends AbstractFailureAnalyzer<JwtSecretException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, JwtSecretException cause) {
		return new FailureAnalysis(cause.getMessage(),
				"Set JWT_SECRET to a random value of at least 32 bytes, for example:\n\n"
						+ "    export JWT_SECRET=$(openssl rand -base64 48)\n\n"
						+ "or put it in a .env file next to where you start the app (see .env.example). "
						+ "The dev and demo profiles generate a temporary secret instead; production never does.",
				cause);
	}

}
