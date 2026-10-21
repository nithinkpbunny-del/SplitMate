package com.splitmate.split;

import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Invites", description = "The public invite preview and joining a split")
@RestController
@RequestMapping("/api/invites")
public class InviteController {

	private final SplitService service;

	public InviteController(SplitService service) {
		this.service = service;
	}

	/** Public: a limited preview of the split behind an invite code. */
	@Operation(summary = "Public preview of the split behind an invite code")
	@SecurityRequirements
	@GetMapping("/{code}")
	public InvitePreviewResponse preview(@PathVariable String code) {
		return service.preview(code);
	}

	/** Authenticated: join the split. Idempotent. */
	@Operation(summary = "Join a split by invite code (idempotent)")
	@PostMapping("/{code}/join")
	public SplitResponse join(@PathVariable String code, @CurrentUserId ObjectId userId) {
		return service.join(code, userId);
	}

}
