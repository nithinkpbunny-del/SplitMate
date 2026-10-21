package com.splitmate.auth;

import java.time.Duration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.splitmate.config.AppProperties;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the httpOnly auth cookie that carries the JWT. SameSite=Lax, Secure in production.
 */
@Component
public class AuthCookies {

	private final AppProperties.Cookie settings;

	private final Duration lifetime;

	public AuthCookies(AppProperties properties, TokenService tokenService) {
		this.settings = properties.cookie();
		this.lifetime = tokenService.lifetime();
	}

	public String name() {
		return settings.name();
	}

	public ResponseCookie create(String token) {
		return base(token).maxAge(lifetime).build();
	}

	public ResponseCookie clear() {
		return base("").maxAge(0).build();
	}

	public boolean isPresent(HttpServletRequest request) {
		return read(request) != null;
	}

	public String read(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (settings.name().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private ResponseCookie.ResponseCookieBuilder base(String value) {
		return ResponseCookie.from(settings.name(), value)
			.httpOnly(true)
			.secure(settings.secure())
			.sameSite("Lax")
			.path("/");
	}

}
