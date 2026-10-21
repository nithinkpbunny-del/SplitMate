package com.splitmate.split;

import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.splitmate.support.MongoIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SplitFlowIntegrationTest extends MongoIntegrationTest {

	private TestUser aarav;

	private TestUser isha;

	private String splitId;

	private String code;

	@BeforeEach
	void createSplit() throws Exception {
		aarav = register("Aarav Mehta", "aarav@example.com");
		isha = register("Isha Rao", "isha@example.com");
		var created = call(aarav, "POST", "/api/splits", """
				{"name":"October month split","emoji":"🏠","period":{"start":"2026-10-01","end":"2026-10-31"}}""")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.myRole").value("OWNER"))
			.andExpect(jsonPath("$.currency").value("EUR"));
		splitId = read(created, "$.id");
		code = read(created, "$.inviteCode");
		assertThat(code).hasSize(12).matches("[0-9A-Za-z]+");
		assertThat((String) read(created, "$.inviteUrl")).endsWith("/join/" + code);
	}

	@Test
	void nonMembersGet404AndPreviewIsPublic() throws Exception {
		call(isha, "GET", "/api/splits/" + splitId).andExpect(status().isNotFound());
		call(null, "GET", "/api/invites/" + code)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.splitName").value("October month split"))
			.andExpect(jsonPath("$.ownerName").value("Aarav Mehta"))
			.andExpect(jsonPath("$.members[0].firstName").value("Aarav"))
			.andExpect(jsonPath("$.members[0].email").doesNotExist());
	}

	@Test
	void joiningIsIdempotent() throws Exception {
		call(isha, "POST", "/api/invites/" + code + "/join").andExpect(status().isOk());
		call(isha, "POST", "/api/invites/" + code + "/join")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members.length()").value(2))
			.andExpect(jsonPath("$.myRole").value("MEMBER"));

		Document split = mongo.getCollection("splits").find().first();
		assertThat(split.getList("members", Document.class)).hasSize(2);
		List<Document> joins = mongo.getCollection("activity_logs").find(new Document("type", "MEMBER_JOINED")).into(new java.util.ArrayList<>());
		assertThat(joins).hasSize(1);
	}

	@Test
	void regeneratingKillsOldLinkAndDisablingBlocksJoins() throws Exception {
		call(isha, "POST", "/api/splits/" + splitId + "/invite/regenerate").andExpect(status().isNotFound());
		String newCode = read(call(aarav, "POST", "/api/splits/" + splitId + "/invite/regenerate"), "$.inviteCode");
		call(null, "GET", "/api/invites/" + code).andExpect(status().isNotFound());

		call(aarav, "PATCH", "/api/splits/" + splitId + "/invite", "{\"enabled\":false}").andExpect(status().isOk());
		call(isha, "POST", "/api/invites/" + newCode + "/join").andExpect(status().isNotFound());
		call(aarav, "PATCH", "/api/splits/" + splitId + "/invite", "{\"enabled\":true}").andExpect(status().isOk());
		call(isha, "POST", "/api/invites/" + newCode + "/join").andExpect(status().isOk());
	}

	@Test
	void ownerOnlyActionsAre403ForMembers() throws Exception {
		call(isha, "POST", "/api/invites/" + code + "/join");
		call(isha, "PATCH", "/api/splits/" + splitId, "{\"name\":\"Mine now\"}").andExpect(status().isForbidden());
		call(isha, "DELETE", "/api/splits/" + splitId + "/members/" + aarav.id()).andExpect(status().isForbidden());
		call(aarav, "DELETE", "/api/splits/" + splitId + "/members/" + aarav.id()).andExpect(status().isConflict());
	}

	@Test
	void memberCanLeaveAndRejoin() throws Exception {
		call(isha, "POST", "/api/invites/" + code + "/join");
		call(isha, "DELETE", "/api/splits/" + splitId + "/members/" + isha.id()).andExpect(status().isNoContent());
		call(isha, "GET", "/api/splits/" + splitId).andExpect(status().isNotFound());
		call(isha, "POST", "/api/invites/" + code + "/join")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.members.length()").value(2));
	}

	@Test
	void emptySplitIsDeletedWithItsActivity() throws Exception {
		call(aarav, "DELETE", "/api/splits/" + splitId)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.deleted").value(true));
		assertThat(mongo.getCollection("splits").countDocuments()).isZero();
		assertThat(mongo.getCollection("activity_logs").countDocuments()).isZero();
	}

	@Test
	void dashboardListsMySplitsOnly() throws Exception {
		call(isha, "GET", "/api/splits").andExpect(jsonPath("$.length()").value(0));
		call(isha, "POST", "/api/invites/" + code + "/join");
		call(isha, "GET", "/api/splits")
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].memberCount").value(2))
			.andExpect(jsonPath("$[0].myNetMinor").value(0));
	}

}
