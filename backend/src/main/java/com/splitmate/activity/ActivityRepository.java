package com.splitmate.activity;

import java.util.Collection;
import java.util.List;

import org.bson.types.ObjectId;

import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityRepository extends MongoRepository<ActivityLog, ObjectId> {

	List<ActivityLog> findBySplitIdOrderByCreatedAtDesc(ObjectId splitId, Pageable pageable);

	List<ActivityLog> findBySplitIdInOrderByCreatedAtDesc(Collection<ObjectId> splitIds, Pageable pageable);

	void deleteBySplitId(ObjectId splitId);

}
