package com.splitmate.auth;

import org.junit.jupiter.api.Test;

import com.splitmate.support.MongoIntegrationTest;

import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Login rate limiting over HTTP: 10 failed attempts per IP and email within 5 minutes, then 429. Each test uses its own
 * email address, because the limiter is keyed by IP (always 127.0.0.1 under MockMvc) and email.
 */
class LoginRateLimitIntegrationTest extends MongoIntegrationTest {

	private void login(String email, String password, int expectedStatus) throws Exception {
		mvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
			.andExpect(status().is(expectedStatus));
	}

	@Test
	void tenFailuresThenTooManyRequests() throws Exception {
		register("Limited", "limited@example.com");
		for (int i = 1; i <= 10; i++) {
			login("limited@example.com", "Wrong1234", 401);
		}
		// the 11th attempt is refused, even with the right password, and tells the client when to retry
		mvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"limited@example.com\",\"password\":\"Secret123\"}"))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.status").value(429))
			.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Too many login attempts")));
	}

	@Test
	void otherEmailsAreNotAffected() throws Exception {
		register("Victim", "victim@example.com");
		for (int i = 1; i <= 10; i++) {
			login("attacker-target@example.com", "Wrong1234", 401);
		}
		login("attacker-target@example.com", "Wrong1234", 429);
		login("victim@example.com", "Secret123", 200);
	}

	@Test
	void aSuccessfulLoginResetsTheCounter() throws Exception {
		register("Reset", "reset@example.com");
		for (int i = 1; i <= 9; i++) {
			login("reset@example.com", "Wrong1234", 401);
		}
		login("reset@example.com", "Secret123", 200); // success on the 10th attempt clears the failures
		for (int i = 1; i <= 9; i++) {
			login("reset@example.com", "Wrong1234", 401);
		}
		login("reset@example.com", "Secret123", 200);
	}

	@Test
	void unknownEmailsAreLimitedToo() throws Exception {
		for (int i = 1; i <= 10; i++) {
			login("ghost@example.com", "Wrong1234", 401);
		}
		login("ghost@example.com", "Wrong1234", 429);
	}

}
