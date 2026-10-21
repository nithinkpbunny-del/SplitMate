package com.splitmate.common;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape returned by every endpoint:
 * {@code { timestamp, status, error, message, path, fieldErrors: [{field, message}] }}.
 */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path,
		List<FieldError> fieldErrors) {

	public record FieldError(String field, String message) {
	}

	public static ErrorResponse of(int status, String error, String message, String path) {
		return new ErrorResponse(Instant.now(), status, error, message, path, List.of());
	}

	public static ErrorResponse of(int status, String error, String message, String path, List<FieldError> fieldErrors) {
		return new ErrorResponse(Instant.now(), status, error, message, path, fieldErrors);
	}

}
