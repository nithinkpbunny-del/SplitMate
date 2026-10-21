package com.splitmate.seed;

import org.bson.types.ObjectId;

import org.springframework.stereotype.Component;

/**
 * Generates the demo reports. The report module is wired in a later step; until then this does nothing.
 */
@Component
class DemoReports {

	void generateFor(String splitId, ObjectId actor) {
		// replaced once ReportService exists
	}

}
