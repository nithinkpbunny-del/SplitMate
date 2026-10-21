package com.splitmate.user;

import java.time.Instant;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A registered person. Collection {@code users}; {@code email} is stored lowercase and is unique.
 */
@Document("users")
@Getter
@Setter
@NoArgsConstructor
public class User {

	@Id
	private ObjectId id;

	private String name;

	@Indexed(unique = true)
	private String email;

	private String passwordHash;

	private String avatarColor;

	@CreatedDate
	private Instant createdAt;

	@LastModifiedDate
	private Instant updatedAt;

	private Instant lastLoginAt;

	public User(String name, String email, String passwordHash, String avatarColor) {
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.avatarColor = avatarColor;
	}

}
