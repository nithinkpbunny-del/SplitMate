package com.splitmate.auth;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Finds the JWT for a request: an {@code Authorization: Bearer} header wins (handy for curl/Postman),
 * otherwise the httpOnly auth cookie is used.
 *
 * <p>The cookie is ignored on public endpoints (login, register, invite preview...), so a stale cookie
 * can never turn a public request into a 401.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

	private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();

	private final AuthCookies cookies;

	private final RequestMatcher publicEndpoints;

	public CookieBearerTokenResolver(AuthCookies cookies, RequestMatcher publicEndpoints) {
		this.cookies = cookies;
		this.publicEndpoints = publicEndpoints;
	}

	@Override
	public String resolve(HttpServletRequest request) {
		if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
			return headerResolver.resolve(request);
		}
		if (publicEndpoints.matches(request)) {
			return null;
		}
		return cookies.read(request);
	}

}
