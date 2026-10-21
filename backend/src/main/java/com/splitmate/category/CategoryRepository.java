package com.splitmate.category;

import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface CategoryRepository extends MongoRepository<Category, ObjectId> {

	/** Global defaults plus the given split's own categories. */
	@Query(value = "{ $or: [ { 'splitId': null }, { 'splitId': ?0 } ] }", sort = "{ 'sortOrder': 1, 'name': 1 }")
	List<Category> findAvailableFor(ObjectId splitId);

	@Query(value = "{ 'splitId': null }", sort = "{ 'sortOrder': 1 }")
	List<Category> findDefaults();

	Optional<Category> findBySplitIdIsNullAndKey(String key);

}
