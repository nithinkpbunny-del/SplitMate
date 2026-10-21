package com.splitmate.report;

import java.util.List;

import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Reports", description = "Spending reports (aggregations, insights, PDF in GridFS) and the CSV export")
@RestController
@RequestMapping("/api")
public class ReportController {

	private static final MediaType CSV = new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8);

	private final ReportService service;

	private final CsvExporter csv;

	public ReportController(ReportService service, CsvExporter csv) {
		this.service = service;
		this.csv = csv;
	}

	/** Builds the stats and insights from MongoDB aggregations, renders the PDF and stores it in GridFS. */
	@Operation(summary = "Generate a spending report with PDF")
	@ApiResponse(responseCode = "201", description = "Created")
	@PostMapping("/splits/{splitId}/reports")
	public ResponseEntity<ReportResponse> generate(@PathVariable String splitId,
			@RequestBody(required = false) ReportRequest request, @CurrentUserId ObjectId userId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.generate(splitId, userId, request));
	}

	@Operation(summary = "List a split's reports")
	@GetMapping("/splits/{splitId}/reports")
	public List<ReportSummary> list(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.list(splitId, userId);
	}

	@Operation(summary = "One report with statistics and insights")
	@GetMapping("/reports/{reportId}")
	public ReportResponse get(@PathVariable String reportId, @CurrentUserId ObjectId userId) {
		return service.get(reportId, userId);
	}

	@Operation(summary = "Download the report PDF (streamed from GridFS)")
	@GetMapping("/reports/{reportId}/pdf")
	public ResponseEntity<org.springframework.core.io.Resource> pdf(@PathVariable String reportId,
			@CurrentUserId ObjectId userId) {
		ReportService.PdfDownload download = service.pdf(reportId, userId);
		return ResponseEntity.ok()
			.contentType(MediaType.APPLICATION_PDF)
			.contentLength(download.length())
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.attachment().filename(download.fileName()).build().toString())
			.body(download.resource());
	}

	@Operation(summary = "Export all expenses as CSV")
	@GetMapping("/splits/{splitId}/export.csv")
	public ResponseEntity<byte[]> exportCsv(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		CsvExporter.CsvFile file = csv.export(splitId, userId);
		return ResponseEntity.ok()
			.contentType(CSV)
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString())
			.body(file.content());
	}

}
