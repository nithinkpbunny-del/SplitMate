package com.splitmate.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import com.splitmate.common.ErrorResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * Writes 401/403 responses from the security filter chain in the same JSON shape as the controllers.
 */
@Component
public class JsonSecurityErrorHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	private final AuthCookies cookies;

	public JsonSecurityErrorHandlers(JsonMapper jsonMapper, AuthCookies cookies) {
		this.jsonMapper = jsonMapper;
		this.cookies = cookies;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		boolean hadCookie = cookies.isPresent(request);
		if (hadCookie) {
			// The cookie's token is expired or invalid: remove it so the browser stops sending it.
			response.addHeader(HttpHeaders.SET_COOKIE, cookies.clear().toString());
		}
		String message = hadCookie || request.getHeader(HttpHeaders.AUTHORIZATION) != null
				? "Your session has expired. Please log in again." : "Please log in to continue.";
		write(response, request, HttpStatus.UNAUTHORIZED, message);
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
			throws IOException {
		String message = ex instanceof CsrfException
				? "Security token missing or expired. Refresh the page and try again."
				: "You do not have permission to do that.";
		write(response, request, HttpStatus.FORBIDDEN, message);
	}

	private void write(HttpServletResponse response, HttpServletRequest request, HttpStatus status, String message)
			throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(),
				ErrorResponse.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
	}

}
