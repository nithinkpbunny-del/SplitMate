package com.splitmate.split;

import java.util.List;

import jakarta.validation.Valid;
import org.bson.types.ObjectId;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/splits")
public class SplitController {

	private final SplitService service;

	public SplitController(SplitService service) {
		this.service = service;
	}

	@GetMapping
	public List<SplitSummaryResponse> mySplits(@CurrentUserId ObjectId userId) {
		return service.listMine(userId);
	}

	@PostMapping
	public ResponseEntity<SplitResponse> create(@Valid @RequestBody CreateSplitRequest request,
			@CurrentUserId ObjectId userId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId, request));
	}

	@GetMapping("/{splitId}")
	public SplitResponse get(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.get(splitId, userId);
	}

	@PatchMapping("/{splitId}")
	public SplitResponse update(@PathVariable String splitId, @Valid @RequestBody UpdateSplitRequest request,
			@CurrentUserId ObjectId userId) {
		return service.update(splitId, userId, request);
	}

	@DeleteMapping("/{splitId}")
	public DeleteSplitResponse delete(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.delete(splitId, userId);
	}

	/** Owner removes a member, or a member removes themself (leave). */
	@DeleteMapping("/{splitId}/members/{memberId}")
	public ResponseEntity<Void> removeMember(@PathVariable String splitId, @PathVariable String memberId,
			@CurrentUserId ObjectId userId) {
		service.removeMember(splitId, memberId, userId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{splitId}/invite/regenerate")
	public SplitResponse regenerateInvite(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.regenerateInvite(splitId, userId);
	}

	@PatchMapping("/{splitId}/invite")
	public SplitResponse inviteSettings(@PathVariable String splitId, @Valid @RequestBody InviteSettingsRequest request,
			@CurrentUserId ObjectId userId) {
		return service.setInviteEnabled(splitId, userId, request.enabled());
	}

}
