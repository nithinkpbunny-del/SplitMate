package com.splitmate.split;

import org.bson.types.ObjectId;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invites")
public class InviteController {

	private final SplitService service;

	public InviteController(SplitService service) {
		this.service = service;
	}

	/** Public: a limited preview of the split behind an invite code. */
	@GetMapping("/{code}")
	public InvitePreviewResponse preview(@PathVariable String code) {
		return service.preview(code);
	}

	/** Authenticated: join the split. Idempotent. */
	@PostMapping("/{code}/join")
	public SplitResponse join(@PathVariable String code, @CurrentUserId ObjectId userId) {
		return service.join(code, userId);
	}

}
