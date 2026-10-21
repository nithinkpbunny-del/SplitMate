package com.splitmate.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReportResponse(String id, String splitId, String settlementId, String generatedBy,
		String generatedByName, Instant generatedAt, LocalDate periodStart, LocalDate periodEnd, String currency,
		ReportStats stats, List<String> insights, String pdfFileId, String pdfFileName) {

	static ReportResponse from(Report r, String generatedByName) {
		return new ReportResponse(r.getId().toHexString(), r.getSplitId().toHexString(),
				r.getSettlementId() == null ? null : r.getSettlementId().toHexString(), r.getGeneratedBy().toHexString(),
				generatedByName, r.getGeneratedAt(), r.getPeriodStart(), r.getPeriodEnd(), r.getCurrency(), r.getStats(),
				r.getInsights(), r.getPdfFileId() == null ? null : r.getPdfFileId().toHexString(), r.getPdfFileName());
	}

}
