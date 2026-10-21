package com.splitmate.support;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Base class for end-to-end tests against a real local MongoDB (database {@code splitmate_test}, emptied
 * before each test, except the seeded categories). Docker is not assumed, so these run with {@code ./mvnw -Pintegration test}.
 */
@Tag("mongo-integration")
@SpringBootTest(properties = {
		"spring.mongodb.uri=mongodb://localhost:27017/splitmate_test",
		"app.jwt.secret=integration-test-secret-that-is-long-enough-1234567890",
		"spring.config.import=" })
@AutoConfigureMockMvc
public abstract class MongoIntegrationTest {

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected MongoTemplate mongo;

	@BeforeEach
	void cleanDatabase() {
		for (String collection : mongo.getCollectionNames()) {
			// categories are reference data seeded once at startup
			if (!collection.startsWith("system.") && !collection.equals("categories")) {
				mongo.getCollection(collection).deleteMany(new org.bson.Document());
			}
		}
	}

	/** A registered test user: their auth cookie and id. */
	protected record TestUser(Cookie cookie, String id) {
	}

	protected TestUser register(String name, String email) throws Exception {
		var response = mvc.perform(post("/api/auth/register").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"%s\",\"email\":\"%s\",\"password\":\"Secret123\"}".formatted(name, email)))
			.andReturn()
			.getResponse();
		return new TestUser(response.getCookie("SM_TOKEN"), JsonPath.read(response.getContentAsString(), "$.id"));
	}

	protected ResultActions call(TestUser user, String method, String path, String json) throws Exception {
		MockHttpServletRequestBuilder builder = switch (method) {
			case "GET" -> get(path);
			case "POST" -> post(path);
			case "PUT" -> put(path);
			case "PATCH" -> patch(path);
			case "DELETE" -> delete(path);
			default -> throw new IllegalArgumentException(method);
		};
		if (user != null) {
			builder.cookie(user.cookie());
		}
		if (json != null) {
			builder.contentType(MediaType.APPLICATION_JSON).content(json);
		}
		return mvc.perform(builder.with(csrf()));
	}

	protected ResultActions call(TestUser user, String method, String path) throws Exception {
		return call(user, method, path, null);
	}

	protected static <T> T read(ResultActions result, String jsonPath) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
	}

}
