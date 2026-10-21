package com.splitmate.settlement;

import java.util.List;

import org.bson.types.ObjectId;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/splits/{splitId}")
public class SettlementController {

	private final SettlementService service;

	public SettlementController(SettlementService service) {
		this.service = service;
	}

	/** Runs the calculation on current data and stores a new snapshot. */
	@PostMapping("/calculate")
	public SettlementResponse calculate(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.calculate(splitId, userId);
	}

	/** The newest snapshot, or 204 when the split was never calculated. */
	@GetMapping("/settlements/latest")
	public ResponseEntity<SettlementResponse> latest(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.latest(splitId, userId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
	}

	@GetMapping("/settlements")
	public List<SettlementHistoryItem> history(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.history(splitId, userId);
	}

	@GetMapping("/settlements/{settlementId}")
	public SettlementResponse get(@PathVariable String splitId, @PathVariable String settlementId,
			@CurrentUserId ObjectId userId) {
		return service.get(splitId, settlementId, userId);
	}

	/** "Mark split as settled": only when every balance is zero after an up-to-date calculation. */
	@PostMapping("/settle")
	public ResponseEntity<Void> settle(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		service.markSettled(splitId, userId);
		return ResponseEntity.noContent().build();
	}

}
