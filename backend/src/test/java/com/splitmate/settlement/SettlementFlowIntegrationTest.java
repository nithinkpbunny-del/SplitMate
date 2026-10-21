package com.splitmate.settlement;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.splitmate.support.MongoIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The spec's worked example end to end through the HTTP API, with payments, recalculation and settling.
 */
class SettlementFlowIntegrationTest extends MongoIntegrationTest {

	private TestUser a;

	private TestUser b;

	private TestUser c;

	private String base;

	@BeforeEach
	void setUp() throws Exception {
		a = register("Aarav Mehta", "a@example.com");
		b = register("Isha Rao", "b@example.com");
		c = register("Rohan Nair", "c@example.com");
		var split = call(a, "POST", "/api/splits", "{\"name\":\"October month split\"}");
		base = "/api/splits/" + read(split, "$.id");
		String code = read(split, "$.inviteCode");
		call(b, "POST", "/api/invites/" + code + "/join");
		call(c, "POST", "/api/invites/" + code + "/join");
		List<String> ids = read(call(a, "GET", "/api/categories"), "$[?(@.key == 'groceries')].id");
		String category = ids.getFirst();
		call(a, "POST", base + "/expenses", """
				{"description":"Groceries","amount":"90","categoryId":"%s","date":"2026-10-10"}""".formatted(category))
			.andExpect(status().isCreated());
		call(b, "POST", base + "/expenses", """
				{"description":"Dinner","amount":"40","categoryId":"%s","date":"2026-10-11","participants":["%s","%s"]}"""
			.formatted(category, b.id(), c.id())).andExpect(status().isCreated());
	}

	private Map<String, Integer> nets(String json) {
		List<Map<String, Object>> summaries = com.jayway.jsonpath.JsonPath.read(json, "$.memberSummaries");
		return summaries.stream()
			.collect(java.util.stream.Collectors.toMap(m -> (String) m.get("userId"), m -> (Integer) m.get("netMinor")));
	}

	@Test
	void calculatePayRecalculateAndSettle() throws Exception {
		call(a, "GET", base + "/settlements/latest").andExpect(status().isNoContent());

		var first = call(c, "POST", base + "/calculate")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.totalSpendMinor").value(13000))
			.andExpect(jsonPath("$.expenseCount").value(2))
			.andExpect(jsonPath("$.transfers.length()").value(2))
			.andExpect(jsonPath("$.transfers[0].fromUserId").value(c.id()))
			.andExpect(jsonPath("$.transfers[0].toUserId").value(a.id()))
			.andExpect(jsonPath("$.transfers[0].amountMinor").value(5000))
			.andExpect(jsonPath("$.transfers[1].fromUserId").value(b.id()))
			.andExpect(jsonPath("$.transfers[1].amountMinor").value(1000));
		assertThat(nets(first.andReturn().getResponse().getContentAsString()))
			.containsExactlyInAnyOrderEntriesOf(Map.of(a.id(), 6000, b.id(), -1000, c.id(), -5000));
		call(a, "GET", base).andExpect(jsonPath("$.calculationStale").value(false));

		// the live aggregation and the engine agree
		call(a, "GET", base).andExpect(jsonPath("$.myNetMinor").value(6000));
		call(b, "GET", base).andExpect(jsonPath("$.myNetMinor").value(-1000));

		// C pays A back; the snapshot is now stale
		call(c, "POST", base + "/payments", """
				{"fromUserId":"%s","toUserId":"%s","amount":"50,00","note":"PayPal"}""".formatted(c.id(), a.id()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.warning").doesNotExist());
		call(a, "GET", base).andExpect(jsonPath("$.calculationStale").value(true));
		call(a, "POST", base + "/settle").andExpect(status().isConflict());

		var second = call(a, "POST", base + "/calculate").andExpect(jsonPath("$.version").value(2));
		assertThat(nets(second.andReturn().getResponse().getContentAsString()))
			.containsExactlyInAnyOrderEntriesOf(Map.of(a.id(), 1000, b.id(), -1000, c.id(), 0));

		// B overpays: allowed, with a clear warning
		call(b, "POST", base + "/payments", """
				{"fromUserId":"%s","toUserId":"%s","amount":"15"}""".formatted(b.id(), a.id()))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.warning").value(org.hamcrest.Matchers.containsString("only owed")));
		var paymentId = (String) read(call(b, "GET", base + "/payments"), "$[0].id");
		call(c, "DELETE", base + "/payments/" + paymentId).andExpect(status().isForbidden());
		call(b, "DELETE", base + "/payments/" + paymentId).andExpect(status().isNoContent());
		call(b, "POST", base + "/payments", """
				{"fromUserId":"%s","toUserId":"%s","amount":"10"}""".formatted(b.id(), a.id()))
			.andExpect(jsonPath("$.warning").doesNotExist());

		call(a, "POST", base + "/calculate")
			.andExpect(jsonPath("$.version").value(3))
			.andExpect(jsonPath("$.allSettled").value(true))
			.andExpect(jsonPath("$.transfers.length()").value(0));
		call(b, "POST", base + "/settle").andExpect(status().isNoContent());
		call(a, "GET", base).andExpect(jsonPath("$.status").value("SETTLED"));

		call(a, "GET", base + "/settlements").andExpect(jsonPath("$.length()").value(3));
		assertThat(mongo.getCollection("activity_logs").countDocuments(new org.bson.Document("type", "SPLIT_SETTLED")))
			.isEqualTo(1);
	}

	@Test
	void outsidersCannotCalculateOrPay() throws Exception {
		TestUser stranger = register("Stranger", "x@example.com");
		call(stranger, "POST", base + "/calculate").andExpect(status().isNotFound());
		call(stranger, "GET", base + "/settlements/latest").andExpect(status().isNotFound());
		call(stranger, "POST", base + "/payments", "{\"fromUserId\":\"%s\",\"toUserId\":\"%s\",\"amount\":\"1\"}"
			.formatted(c.id(), a.id())).andExpect(status().isNotFound());
		// a member recording a payment between two other people
		call(b, "POST", base + "/payments", "{\"fromUserId\":\"%s\",\"toUserId\":\"%s\",\"amount\":\"1\"}"
			.formatted(c.id(), a.id())).andExpect(status().isForbidden());
	}

}
