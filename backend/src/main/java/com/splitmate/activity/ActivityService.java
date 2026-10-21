package com.splitmate.activity;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.splitmate.split.SplitDeletedEvent;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

/**
 * Writes and reads the per-split activity timeline.
 */
@Service
public class ActivityService {

	private static final int MAX_LIMIT = 200;

	private final ActivityRepository repository;

	private final MongoTemplate mongo;

	public ActivityService(ActivityRepository repository, MongoTemplate mongo) {
		this.repository = repository;
		this.mongo = mongo;
	}

	public ActivityLog log(ObjectId splitId, ObjectId actorId, String actorName, ActivityType type, String message) {
		return log(splitId, actorId, actorName, type, message, Map.of());
	}

	public ActivityLog log(ObjectId splitId, ObjectId actorId, String actorName, ActivityType type, String message,
			Map<String, Object> metadata) {
		ActivityLog entry = new ActivityLog();
		entry.setSplitId(splitId);
		entry.setActorId(actorId);
		entry.setActorName(actorName);
		entry.setType(type);
		entry.setMessage(message);
		entry.setMetadata(metadata);
		return repository.save(entry);
	}

	public List<ActivityLog> forSplit(ObjectId splitId, int limit) {
		return repository.findBySplitIdOrderByCreatedAtDesc(splitId, PageRequest.of(0, clamp(limit)));
	}

	public List<ActivityLog> forSplits(Collection<ObjectId> splitIds, int limit) {
		if (splitIds.isEmpty()) {
			return List.of();
		}
		return repository.findBySplitIdInOrderByCreatedAtDesc(splitIds, PageRequest.of(0, clamp(limit)));
	}

	/** Most recent activity time per split ({@code $group} with {@code $max}). */
	public Map<ObjectId, Instant> latestActivityBySplit(Collection<ObjectId> splitIds) {
		if (splitIds.isEmpty()) {
			return Map.of();
		}
		Aggregation aggregation = Aggregation.newAggregation(
				Aggregation.match(Criteria.where("splitId").in(splitIds)),
				Aggregation.group("splitId").max("createdAt").as("latest"));
		Map<ObjectId, Instant> latest = new HashMap<>();
		for (Document row : mongo.aggregate(aggregation, ActivityLog.class, Document.class)) {
			latest.put(row.getObjectId("_id"), row.getDate("latest").toInstant());
		}
		return latest;
	}

	@EventListener
	void onSplitDeleted(SplitDeletedEvent event) {
		repository.deleteBySplitId(event.splitId());
	}

	private static int clamp(int limit) {
		return Math.max(1, Math.min(limit, MAX_LIMIT));
	}

}
