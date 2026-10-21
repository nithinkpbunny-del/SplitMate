package com.splitmate.common;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every exception into the standard {@link ErrorResponse} JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
		ResponseEntity.BodyBuilder builder = ResponseEntity.status(ex.getStatus());
		if (ex instanceof RateLimitedException limited) {
			builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.getRetryAfterSeconds()));
		}
		return builder.body(body(ex.getStatus(), ex.getMessage(), request));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(fe -> new ErrorResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
			.toList();
		String message = fieldErrors.isEmpty() ? "Validation failed" : fieldErrors.getFirst().message();
		return ResponseEntity.badRequest()
			.body(ErrorResponse.of(400, "Bad Request", message, request.getRequestURI(), fieldErrors));
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
		List<ErrorResponse.FieldError> fieldErrors = ex.getParameterValidationResults()
			.stream()
			.flatMap(result -> result.getResolvableErrors()
				.stream()
				.map(err -> new ErrorResponse.FieldError(result.getMethodParameter().getParameterName(),
						err.getDefaultMessage())))
			.toList();
		String message = fieldErrors.isEmpty() ? "Validation failed" : fieldErrors.getFirst().message();
		return ResponseEntity.badRequest()
			.body(ErrorResponse.of(400, "Bad Request", message, request.getRequestURI(), fieldErrors));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
		return ResponseEntity.badRequest()
			.body(body(HttpStatus.BAD_REQUEST, "The request body is missing or is not valid JSON", request));
	}

	@ExceptionHandler({ MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class })
	ResponseEntity<ErrorResponse> handleBadParam(Exception ex, HttpServletRequest request) {
		String message = ex instanceof MethodArgumentTypeMismatchException mismatch
				? "Invalid value for '" + mismatch.getName() + "'" : ex.getMessage();
		return ResponseEntity.badRequest().body(body(HttpStatus.BAD_REQUEST, message, request));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(HttpStatus.NOT_FOUND, "Not found", request));
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(body(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
			.body(body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), request));
	}

	@ExceptionHandler(DuplicateKeyException.class)
	ResponseEntity<ErrorResponse> handleDuplicate(DuplicateKeyException ex, HttpServletRequest request) {
		log.warn("Duplicate key on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(body(HttpStatus.CONFLICT, "This already exists", request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
			.body(body(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side. Please try again.", request));
	}

	private static ErrorResponse body(HttpStatus status, String message, HttpServletRequest request) {
		return ErrorResponse.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
	}

}
