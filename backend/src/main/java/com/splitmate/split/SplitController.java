package com.splitmate.split;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Splits", description = "Splits, their members and invite links")
@RestController
@RequestMapping("/api/splits")
public class SplitController {

	private final SplitService service;

	public SplitController(SplitService service) {
		this.service = service;
	}

	@Operation(summary = "My splits, with totals and my balance")
	@GetMapping
	public List<SplitSummaryResponse> mySplits(@CurrentUserId ObjectId userId) {
		return service.listMine(userId);
	}

	@Operation(summary = "Create a split (you become the owner)")
	@ApiResponse(responseCode = "201", description = "Created")
	@PostMapping
	public ResponseEntity<SplitResponse> create(@Valid @RequestBody CreateSplitRequest request,
			@CurrentUserId ObjectId userId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(userId, request));
	}

	@Operation(summary = "One split with members and invite link")
	@GetMapping("/{splitId}")
	public SplitResponse get(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.get(splitId, userId);
	}

	@Operation(summary = "Edit, archive or reopen a split (owner)")
	@PatchMapping("/{splitId}")
	public SplitResponse update(@PathVariable String splitId, @Valid @RequestBody UpdateSplitRequest request,
			@CurrentUserId ObjectId userId) {
		return service.update(splitId, userId, request);
	}

	@Operation(summary = "Delete an empty split, or archive one that has expenses (owner)")
	@DeleteMapping("/{splitId}")
	public DeleteSplitResponse delete(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.delete(splitId, userId);
	}

	/** Owner removes a member, or a member removes themself (leave). */
	@Operation(summary = "Remove a member, or leave (blocked while involved in expenses or owing money)")
	@DeleteMapping("/{splitId}/members/{memberId}")
	public ResponseEntity<Void> removeMember(@PathVariable String splitId, @PathVariable String memberId,
			@CurrentUserId ObjectId userId) {
		service.removeMember(splitId, memberId, userId);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "Create a new invite code; the old link stops working (owner)")
	@PostMapping("/{splitId}/invite/regenerate")
	public SplitResponse regenerateInvite(@PathVariable String splitId, @CurrentUserId ObjectId userId) {
		return service.regenerateInvite(splitId, userId);
	}

	@Operation(summary = "Turn the invite link on or off (owner)")
	@PatchMapping("/{splitId}/invite")
	public SplitResponse inviteSettings(@PathVariable String splitId, @Valid @RequestBody InviteSettingsRequest request,
			@CurrentUserId ObjectId userId) {
		return service.setInviteEnabled(splitId, userId, request.enabled());
	}

}
