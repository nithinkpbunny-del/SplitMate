package com.splitmate.expense;

import java.time.LocalDate;

import jakarta.validation.Valid;
import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Expenses", description = "Expenses with server-side shares, filters and soft delete")
@RestController
@RequestMapping("/api/splits/{splitId}/expenses")
public class ExpenseController {

	private final ExpenseService service;

	public ExpenseController(ExpenseService service) {
		this.service = service;
	}

	@Operation(summary = "List expenses with filters, sorting and paging")
	@GetMapping
	public ExpensePage list(@PathVariable String splitId, @CurrentUserId ObjectId userId,
			@RequestParam(required = false) String category, @RequestParam(required = false) String paidBy,
			@RequestParam(required = false) String participant,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q, @RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
		return service.list(splitId, userId, new ExpenseFilter(category, paidBy, participant, from, to, q, sort, page, size));
	}

	@Operation(summary = "One expense")
	@GetMapping("/{expenseId}")
	public ExpenseResponse get(@PathVariable String splitId, @PathVariable String expenseId,
			@CurrentUserId ObjectId userId) {
		return service.get(splitId, expenseId, userId);
	}

	@Operation(summary = "Add an expense (shares are computed on the server)")
	@ApiResponse(responseCode = "201", description = "Created")
	@PostMapping
	public ResponseEntity<ExpenseResponse> create(@PathVariable String splitId, @Valid @RequestBody ExpenseRequest request,
			@CurrentUserId ObjectId userId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(splitId, userId, request));
	}

	@Operation(summary = "Replace an expense (creator, payer or owner)")
	@PutMapping("/{expenseId}")
	public ExpenseResponse update(@PathVariable String splitId, @PathVariable String expenseId,
			@Valid @RequestBody ExpenseRequest request, @CurrentUserId ObjectId userId) {
		return service.update(splitId, expenseId, userId, request);
	}

	@Operation(summary = "Soft-delete an expense (creator, payer or owner)")
	@DeleteMapping("/{expenseId}")
	public ResponseEntity<Void> delete(@PathVariable String splitId, @PathVariable String expenseId,
			@CurrentUserId ObjectId userId) {
		service.delete(splitId, expenseId, userId);
		return ResponseEntity.noContent().build();
	}

}
