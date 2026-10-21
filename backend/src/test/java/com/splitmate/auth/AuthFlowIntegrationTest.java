package com.splitmate.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import com.splitmate.support.MongoIntegrationTest;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthFlowIntegrationTest extends MongoIntegrationTest {

	private static final String REGISTER = """
			{"name":"Isha Rao","email":"isha@example.com","password":"Secret123"}""";

	@Test
	void registerSetsHttpOnlyCookieAndMeReturnsUser() throws Exception {
		MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(REGISTER))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value("isha@example.com"))
			.andExpect(jsonPath("$.passwordHash").doesNotExist())
			.andReturn();

		String setCookie = result.getResponse().getHeader("Set-Cookie");
		assertThat(setCookie).contains("SM_TOKEN=").contains("HttpOnly").contains("SameSite=Lax");
		Cookie cookie = result.getResponse().getCookie("SM_TOKEN");

		mvc.perform(get("/api/auth/me").cookie(cookie))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Isha Rao"));
	}

	@Test
	void bearerHeaderAlsoWorks() throws Exception {
		Cookie cookie = mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(REGISTER))
			.andReturn()
			.getResponse()
			.getCookie("SM_TOKEN");

		mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + cookie.getValue()))
			.andExpect(status().isOk());
	}

	@Test
	void writesWithoutCsrfTokenAreRejected() throws Exception {
		mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTER))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void meWithoutLoginIs401WithErrorShape() throws Exception {
		mvc.perform(get("/api/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.status").value(401))
			.andExpect(jsonPath("$.path").value("/api/auth/me"))
			.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void invalidCookieIsClearedOn401() throws Exception {
		mvc.perform(get("/api/auth/me").cookie(new Cookie("SM_TOKEN", "not-a-jwt")))
			.andExpect(status().isUnauthorized())
			.andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Max-Age=0"))));
	}

	@Test
	void validationErrorsListFields() throws Exception {
		mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"I","email":"nope","password":"short"}"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors.length()").value(3));
	}

	@Test
	void duplicateEmailIs409AndWrongPasswordIs401() throws Exception {
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGISTER))
			.andExpect(status().isCreated());
		mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REGISTER))
			.andExpect(status().isConflict());
		mvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"ISHA@example.com","password":"Wrong1234"}"""))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.message").value("Incorrect email or password"));
	}

}
