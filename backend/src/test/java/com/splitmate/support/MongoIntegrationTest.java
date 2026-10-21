package com.splitmate.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for end-to-end tests against a real local MongoDB (database {@code splitmate_test}, dropped
 * before each test). Docker is not assumed, so these run with {@code ./mvnw -Pintegration test}.
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
			if (!collection.startsWith("system.")) {
				mongo.getCollection(collection).deleteMany(new org.bson.Document());
			}
		}
	}

}
