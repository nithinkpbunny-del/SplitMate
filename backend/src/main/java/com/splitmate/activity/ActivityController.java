package com.splitmate.activity;

import java.util.List;

import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;
import com.splitmate.split.Split;
import com.splitmate.split.SplitAccess;
import com.splitmate.split.SplitService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Activity", description = "The activity timeline")
@RestController
@RequestMapping("/api")
public class ActivityController {

	private final ActivityService activity;

	private final SplitAccess access;

	private final SplitService splits;

	public ActivityController(ActivityService activity, SplitAccess access, SplitService splits) {
		this.activity = activity;
		this.access = access;
		this.splits = splits;
	}

	/** Timeline of one split. */
	@Operation(summary = "A split's activity timeline")
	@GetMapping("/splits/{splitId}/activity")
	public List<ActivityResponse> forSplit(@PathVariable String splitId, @RequestParam(defaultValue = "50") int limit,
			@CurrentUserId ObjectId userId) {
		Split split = access.requireMember(splitId, userId);
		return activity.forSplit(split.getId(), limit).stream().map(ActivityResponse::from).toList();
	}

	/** Recent activity across all of the caller's splits (dashboard). */
	@Operation(summary = "Recent activity across all my splits")
	@GetMapping("/activity")
	public List<ActivityResponse> recent(@RequestParam(defaultValue = "12") int limit, @CurrentUserId ObjectId userId) {
		return activity.forSplits(splits.splitIdsOf(userId), limit).stream().map(ActivityResponse::from).toList();
	}

}
