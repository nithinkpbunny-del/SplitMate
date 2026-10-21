package com.splitmate.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.bson.types.ObjectId;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UserRepository extends MongoRepository<User, ObjectId> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	List<User> findByIdIn(Collection<ObjectId> ids);

}
