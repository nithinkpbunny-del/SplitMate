package com.splitmate.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed view of the {@code app.*} settings in application.yml.
 */
@ConfigurationProperties("app")
public record AppProperties(
		@DefaultValue("http://localhost:5173") String baseUrl,
		@DefaultValue Jwt jwt,
		@DefaultValue Cookie cookie,
		@DefaultValue Cors cors,
		@DefaultValue LoginRateLimit loginRateLimit,
		@DefaultValue Seed seed) {

	public record Jwt(String secret, @DefaultValue("7") int expiryDays, boolean allowGeneratedSecret) {
	}

	public record Cookie(@DefaultValue("SM_TOKEN") String name, boolean secure) {
	}

	public record Cors(@DefaultValue List<String> allowedOrigins) {
	}

	public record LoginRateLimit(@DefaultValue("10") int maxAttempts, @DefaultValue("5") int windowMinutes) {
	}

	public record Seed(boolean enabled) {
	}

	/** Base URL without a trailing slash. */
	public String normalizedBaseUrl() {
		return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
	}

}
