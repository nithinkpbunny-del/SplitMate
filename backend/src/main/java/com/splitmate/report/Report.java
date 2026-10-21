package com.splitmate.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A generated spending analysis. Collection {@code reports}; the PDF itself lives in GridFS
 * ({@code fs.files}/{@code fs.chunks}) and is referenced by {@code pdfFileId}.
 */
@Document("reports")
@CompoundIndex(name = "split_generated", def = "{'splitId': 1, 'generatedAt': -1}")
@Getter
@Setter
@NoArgsConstructor
public class Report {

	@Id
	private ObjectId id;

	private ObjectId splitId;

	private ObjectId settlementId;

	private ObjectId generatedBy;

	private Instant generatedAt;

	private LocalDate periodStart;

	private LocalDate periodEnd;

	private String currency;

	private ReportStats stats;

	private List<String> insights = new ArrayList<>();

	private ObjectId pdfFileId;

	private String pdfFileName;

}
