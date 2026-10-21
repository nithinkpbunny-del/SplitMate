package com.splitmate.common;

import org.springframework.http.HttpStatus;

/** 429 with a Retry-After hint. */
public class RateLimitedException extends ApiException {

	private final long retryAfterSeconds;

	public RateLimitedException(String message, long retryAfterSeconds) {
		super(HttpStatus.TOO_MANY_REQUESTS, message);
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long getRetryAfterSeconds() {
		return retryAfterSeconds;
	}

}
