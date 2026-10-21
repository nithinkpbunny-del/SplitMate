package com.splitmate.activity;

import java.time.Instant;
import java.util.Map;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * One entry in a split's timeline. Collection {@code activity_logs}. The actor's name is copied in so
 * the timeline reads naturally even if the person later leaves.
 */
@Document("activity_logs")
@CompoundIndex(name = "split_timeline", def = "{'splitId': 1, 'createdAt': -1}")
@Getter
@Setter
@NoArgsConstructor
public class ActivityLog {

	@Id
	private ObjectId id;

	private ObjectId splitId;

	private ObjectId actorId;

	private String actorName;

	private ActivityType type;

	private String message;

	private Map<String, Object> metadata;

	@CreatedDate
	private Instant createdAt;

}
