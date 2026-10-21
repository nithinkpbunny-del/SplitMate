package com.splitmate.payment;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentRepository extends MongoRepository<Payment, ObjectId> {

	List<Payment> findBySplitIdOrderByCreatedAtDesc(ObjectId splitId);

	List<Payment> findBySplitId(ObjectId splitId);

	Optional<Payment> findByIdAndSplitId(ObjectId id, ObjectId splitId);

	void deleteBySplitId(ObjectId splitId);

}
