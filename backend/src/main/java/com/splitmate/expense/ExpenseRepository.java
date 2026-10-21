package com.splitmate.expense;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface ExpenseRepository extends MongoRepository<Expense, ObjectId> {

	List<Expense> findBySplitIdAndDeletedFalse(ObjectId splitId);

	Optional<Expense> findByIdAndSplitIdAndDeletedFalse(ObjectId id, ObjectId splitId);

	long countBySplitIdAndDeletedFalse(ObjectId splitId);

	/** Is the user the payer or a participant of any live expense in the split? */
	@Query(value = "{ 'splitId': ?0, 'deleted': false, $or: [ { 'paidBy': ?1 }, { 'participants': ?1 } ] }", exists = true)
	boolean existsLiveInvolving(ObjectId splitId, ObjectId userId);

	void deleteBySplitId(ObjectId splitId);

}
