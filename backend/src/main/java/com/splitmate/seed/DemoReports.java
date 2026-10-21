package com.splitmate.seed;

import org.bson.types.ObjectId;

import com.splitmate.report.ReportRequest;
import com.splitmate.report.ReportService;

import org.springframework.stereotype.Component;

/** Generates the demo reports through the same service the API uses. */
@Component
class DemoReports {

	private final ReportService reports;

	DemoReports(ReportService reports) {
		this.reports = reports;
	}

	void generateFor(String splitId, ObjectId actor) {
		reports.generate(splitId, actor, new ReportRequest(null, null));
	}

}
