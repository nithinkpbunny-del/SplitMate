package com.splitmate.split;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface SplitRepository extends MongoRepository<Split, ObjectId> {

	/** Splits where the user is an active member, newest first. */
	@Query(value = "{ 'members': { $elemMatch: { 'userId': ?0, 'active': true } } }", sort = "{ 'createdAt': -1 }")
	List<Split> findActiveByMember(ObjectId userId);

	Optional<Split> findByInviteCode(String inviteCode);

	boolean existsByInviteCode(String inviteCode);

}
