package com.splitmate.report;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.splitmate.support.MongoIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report generation through the HTTP API: the spec's worked example (A pays 90 for all, B pays 40 for B and C).
 */
class ReportFlowIntegrationTest extends MongoIntegrationTest {

	private TestUser a;

	private TestUser b;

	private TestUser c;

	private String base;

	private String groceries;

	@BeforeEach
	void setUp() throws Exception {
		a = register("Aarav Mehta", "a@example.com");
		b = register("Isha Rao", "b@example.com");
		c = register("Rohan Nair", "c@example.com");
		var split = call(a, "POST", "/api/splits", """
				{"name":"Oktober Käse split 🏠","period":{"start":"2026-10-01","end":"2026-10-31"}}""");
		base = "/api/splits/" + read(split, "$.id");
		String code = read(split, "$.inviteCode");
		call(b, "POST", "/api/invites/" + code + "/join");
		call(c, "POST", "/api/invites/" + code + "/join");
		List<String> ids = read(call(a, "GET", "/api/categories"), "$[?(@.key == 'groceries')].id");
		groceries = ids.getFirst();
	}

	private void addWorkedExample() throws Exception {
		call(a, "POST", base + "/expenses", """
				{"description":"Groceries, \\"weekly\\"","amount":"90","categoryId":"%s","date":"2026-10-10"}""".formatted(groceries))
			.andExpect(status().isCreated());
		call(b, "POST", base + "/expenses", """
				{"description":"=Dinner","amount":"40","categoryId":"%s","date":"2026-10-11","participants":["%s","%s"]}"""
			.formatted(groceries, b.id(), c.id())).andExpect(status().isCreated());
	}

	@Test
	void reportNeedsExpensesAndACalculation() throws Exception {
		call(a, "POST", base + "/reports").andExpect(status().isConflict()); // no calculation
		call(a, "POST", base + "/calculate").andExpect(status().isOk());
		call(a, "POST", base + "/reports").andExpect(status().isConflict()); // calculated, but no expenses
		addWorkedExample();
		call(a, "POST", base + "/reports", "{\"periodStart\":\"2026-10-31\",\"periodEnd\":\"2026-10-01\"}")
			.andExpect(status().isBadRequest());
		call(a, "POST", base + "/reports", "{\"periodStart\":\"2027-01-01\",\"periodEnd\":\"2027-01-31\"}")
			.andExpect(status().isConflict());
	}

	@Test
	void generatesStatsInsightsAndAPdfStoredInGridFs() throws Exception {
		addWorkedExample();
		call(a, "POST", base + "/calculate").andExpect(status().isOk());

		var created = call(b, "POST", base + "/reports")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.periodStart").value("2026-10-01"))
			.andExpect(jsonPath("$.periodEnd").value("2026-10-31"))
			.andExpect(jsonPath("$.stats.totalSpendMinor").value(13000))
			.andExpect(jsonPath("$.stats.expenseCount").value(2))
			.andExpect(jsonPath("$.stats.byMember.length()").value(3))
			.andExpect(jsonPath("$.stats.dailyTrend.length()").value(31))
			.andExpect(jsonPath("$.insights.length()").isNotEmpty())
			.andExpect(jsonPath("$.generatedByName").value("Isha Rao"))
			.andExpect(jsonPath("$.pdfFileName").value("SplitMate-Oktober-Kase-split-2026-10.pdf"));
		String reportId = read(created, "$.id");

		// the PDF is a GridFS file linked to the report
		assertThat(mongo.getCollection("fs.files").countDocuments()).isEqualTo(1);
		Document file = mongo.getCollection("fs.files").find().first();
		assertThat(file.get("metadata", Document.class).getObjectId("reportId").toHexString()).isEqualTo(reportId);

		call(c, "GET", base + "/reports").andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].totalSpendMinor").value(13000));
		call(c, "GET", "/api/reports/" + reportId).andExpect(status().isOk()).andExpect(jsonPath("$.stats.byCategory[0].key").value("groceries"));

		byte[] pdf = call(c, "GET", "/api/reports/" + reportId + "/pdf")
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Type", "application/pdf"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("SplitMate-Oktober-Kase-split-2026-10.pdf")))
			.andReturn().getResponse().getContentAsByteArray();
		assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
		try (PDDocument document = Loader.loadPDF(pdf)) {
			String text = new PDFTextStripper().getText(document);
			assertThat(text).contains("Oktober Käse split").contains("€130.00").contains("Aarav Mehta").contains("+€60.00")
				.contains("Rohan Nair pays Aarav Mehta").contains("€50.00").contains("=Dinner");
			assertThat(text).doesNotContain("🏠");
		}
	}

	@Test
	void outsidersGet404AndCsvIsExcelSafe() throws Exception {
		addWorkedExample();
		call(a, "POST", base + "/calculate");
		String reportId = read(call(a, "POST", base + "/reports"), "$.id");
		TestUser stranger = register("Stranger", "x@example.com");
		call(stranger, "GET", "/api/reports/" + reportId).andExpect(status().isNotFound());
		call(stranger, "GET", "/api/reports/" + reportId + "/pdf").andExpect(status().isNotFound());
		call(stranger, "GET", base + "/reports").andExpect(status().isNotFound());
		call(stranger, "POST", base + "/reports").andExpect(status().isNotFound());
		call(stranger, "GET", base + "/export.csv").andExpect(status().isNotFound());

		String csv = call(b, "GET", base + "/export.csv")
			.andExpect(status().isOk())
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("SplitMate-Oktober-Kase-split-expenses.csv")))
			.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		assertThat(csv).startsWith("﻿Date,Description,Category,Amount,Currency,Paid by,Shared by,Notes");
		assertThat(csv).contains("2026-10-10,\"Groceries, \"\"weekly\"\"\",Groceries,90.00,EUR,Aarav Mehta,Aarav Mehta; Isha Rao; Rohan Nair,");
		assertThat(csv).contains("'=Dinner"); // formula injection is neutralised
	}

}
