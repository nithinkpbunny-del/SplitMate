package com.splitmate.expense;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.splitmate.support.MongoIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseFlowIntegrationTest extends MongoIntegrationTest {

	private TestUser a;

	private TestUser b;

	private TestUser c;

	private String splitId;

	private String groceries;

	@BeforeEach
	void setUp() throws Exception {
		a = register("Aarav Mehta", "a@example.com");
		b = register("Isha Rao", "b@example.com");
		c = register("Rohan Nair", "c@example.com");
		var split = call(a, "POST", "/api/splits", "{\"name\":\"October month split\"}");
		splitId = read(split, "$.id");
		String code = read(split, "$.inviteCode");
		call(b, "POST", "/api/invites/" + code + "/join").andExpect(status().isOk());
		call(c, "POST", "/api/invites/" + code + "/join").andExpect(status().isOk());
		List<String> ids = read(call(a, "GET", "/api/categories"), "$[?(@.key == 'groceries')].id");
		groceries = ids.getFirst();
	}

	private String expense(TestUser who, String amount, String paidBy, List<String> participants) throws Exception {
		String people = participants == null ? "" : ",\"participants\":[\"" + String.join("\",\"", participants) + "\"]";
		String payer = paidBy == null ? "" : ",\"paidBy\":\"" + paidBy + "\"";
		return """
				{"description":"Groceries","amount":"%s","categoryId":"%s","date":"2026-10-12"%s%s}"""
			.formatted(amount, groceries, payer, people);
	}

	@Test
	void sharesAreComputedOnTheServerAndStoredExactly() throws Exception {
		call(a, "POST", "/api/splits/" + splitId + "/expenses", expense(a, "10,00", null, null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.amountMinor").value(1000))
			.andExpect(jsonPath("$.paidBy").value(a.id()))
			.andExpect(jsonPath("$.shares.length()").value(3));

		Document stored = mongo.getCollection("expenses").find().first();
		List<Document> shares = stored.getList("shares", Document.class);
		assertThat(shares.stream().mapToLong(s -> s.getLong("amountMinor")).sum()).isEqualTo(1000);
		assertThat(shares.stream().map(s -> s.getLong("amountMinor")).toList()).containsExactly(334L, 333L, 333L);
		assertThat(stored.get("paidBy")).isInstanceOf(ObjectId.class);
		assertThat(stored.getDate("date").toInstant().toString()).isEqualTo("2026-10-12T00:00:00Z");
	}

	@Test
	void payerNeedNotParticipateAndOutsidersAreRejected() throws Exception {
		call(a, "POST", "/api/splits/" + splitId + "/expenses", expense(a, "30", a.id(), List.of(b.id(), c.id())))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.shares[0].amountMinor").value(1500));
		call(a, "POST", "/api/splits/" + splitId + "/expenses",
				expense(a, "30", a.id(), List.of(b.id(), new ObjectId().toHexString())))
			.andExpect(status().isBadRequest());
	}

	@Test
	void onlyCreatorPayerOrOwnerMayEditAndDeleteIsSoft() throws Exception {
		String id = read(call(b, "POST", "/api/splits/" + splitId + "/expenses", expense(b, "20", null, null)), "$.id");
		String path = "/api/splits/" + splitId + "/expenses/" + id;
		call(c, "PUT", path, expense(c, "1", null, null)).andExpect(status().isForbidden());
		call(c, "DELETE", path).andExpect(status().isForbidden());
		call(a, "PUT", path, expense(a, "21", null, null)).andExpect(status().isOk()); // owner
		call(b, "DELETE", path).andExpect(status().isNoContent());

		call(a, "GET", "/api/splits/" + splitId + "/expenses").andExpect(jsonPath("$.totalItems").value(0));
		assertThat(mongo.getCollection("expenses").find().first().getBoolean("deleted")).isTrue();
		call(a, "GET", "/api/splits/" + splitId).andExpect(jsonPath("$.totalSpendMinor").value(0));
	}

	@Test
	void filtersAndLiveBalances() throws Exception {
		call(a, "POST", "/api/splits/" + splitId + "/expenses", expense(a, "90", null, null));
		call(b, "POST", "/api/splits/" + splitId + "/expenses", expense(b, "40", null, List.of(b.id(), c.id())));

		call(a, "GET", "/api/splits/" + splitId + "/expenses?participant=" + a.id())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.totalAmountMinor").value(9000));
		call(a, "GET", "/api/splits/" + splitId + "/expenses?paidBy=" + b.id() + "&q=GROC")
			.andExpect(jsonPath("$.totalItems").value(1));

		// Worked example from the spec: A +60, B −10, C −50
		call(a, "GET", "/api/splits/" + splitId).andExpect(jsonPath("$.myNetMinor").value(6000));
		call(b, "GET", "/api/splits/" + splitId).andExpect(jsonPath("$.myNetMinor").value(-1000));
		call(c, "GET", "/api/splits/" + splitId).andExpect(jsonPath("$.myNetMinor").value(-5000))
			.andExpect(jsonPath("$.calculationStale").value(true));

		// C is on an expense, so cannot be removed
		call(a, "DELETE", "/api/splits/" + splitId + "/members/" + c.id()).andExpect(status().isConflict());
	}

	@Test
	void closedSplitsRejectNewExpenses() throws Exception {
		call(a, "PATCH", "/api/splits/" + splitId, "{\"status\":\"ARCHIVED\"}").andExpect(status().isOk());
		call(a, "POST", "/api/splits/" + splitId + "/expenses", expense(a, "5", null, null)).andExpect(status().isConflict());
	}

}
