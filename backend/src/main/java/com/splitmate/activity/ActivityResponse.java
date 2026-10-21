package com.splitmate.activity;

import java.time.Instant;
import java.util.Map;

public record ActivityResponse(String id, String splitId, String actorId, String actorName, ActivityType type,
		String message, Map<String, Object> metadata, Instant createdAt) {

	public static ActivityResponse from(ActivityLog log) {
		return new ActivityResponse(log.getId().toHexString(), log.getSplitId().toHexString(),
				log.getActorId() == null ? null : log.getActorId().toHexString(), log.getActorName(), log.getType(),
				log.getMessage(), log.getMetadata() == null ? Map.of() : log.getMetadata(), log.getCreatedAt());
	}

}
