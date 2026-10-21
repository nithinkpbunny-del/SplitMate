package com.splitmate.settlement;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SettlementRepository extends MongoRepository<Settlement, ObjectId> {

	Optional<Settlement> findFirstBySplitIdOrderByVersionDesc(ObjectId splitId);

	List<Settlement> findBySplitIdOrderByVersionDesc(ObjectId splitId);

	Optional<Settlement> findByIdAndSplitId(ObjectId id, ObjectId splitId);

	void deleteBySplitId(ObjectId splitId);

}
