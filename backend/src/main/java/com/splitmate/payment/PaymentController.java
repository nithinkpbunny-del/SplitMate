package com.splitmate.payment;

import java.util.List;

import jakarta.validation.Valid;
import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payments", description = "Real-world \"I paid you back\" records")
@RestController
@RequestMapping("/api/splits/{splitId}/payments")
public class PaymentController {

	private final PaymentService service;

	public PaymentController(PaymentService service) {
		this.service = service;
	}

	@Operation(summary = "Record a payment between two members")
	@ApiResponse(responseCode = "201", description = "Created")
	@PostMapping
	public ResponseEntity<PaymentResponse> record(@PathVariable String splitId, @Valid @RequestBody PaymentRequest request,
			@CurrentUserId ObjectId userId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.record(splitId, userId, request));
	}

	@Operation(summary = "List payments")
	@GetMapping
	public List<PaymentResponse> list(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.list(splitId, userId);
	}

	@Operation(summary = "Delete a payment (recorder or owner)")
	@DeleteMapping("/{paymentId}")
	public ResponseEntity<Void> delete(@PathVariable String splitId, @PathVariable String paymentId,
			@CurrentUserId ObjectId userId) {
		service.delete(splitId, paymentId, userId);
		return ResponseEntity.noContent().build();
	}

}
