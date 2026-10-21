package com.splitmate.user;

import org.bson.types.ObjectId;

import com.splitmate.common.ApiException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final ApplicationEventPublisher events;

	public UserService(UserRepository users, PasswordEncoder passwordEncoder, ApplicationEventPublisher events) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.events = events;
	}

	/** Loads the user behind a valid token; a token for a deleted user is treated as logged out. */
	public User require(ObjectId id) {
		return users.findById(id).orElseThrow(() -> ApiException.unauthorized("Please log in again"));
	}

	public User updateProfile(ObjectId id, UpdateProfileRequest request) {
		User user = require(id);
		if (request.name() != null) {
			user.setName(request.name().trim());
		}
		if (request.avatarColor() != null) {
			if (!AvatarPalette.isValid(request.avatarColor())) {
				throw ApiException.badRequest("Pick one of the available avatar colours");
			}
			user.setAvatarColor(request.avatarColor().toUpperCase());
		}
		User saved = users.save(user);
		// Splits embed a copy of each member's name and colour; let them refresh it.
		events.publishEvent(new UserProfileChangedEvent(saved.getId(), saved.getName(), saved.getAvatarColor()));
		return saved;
	}

	public void changePassword(ObjectId id, ChangePasswordRequest request) {
		User user = require(id);
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw ApiException.badRequest("Your current password is not correct");
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw ApiException.badRequest("The new password must be different from the current one");
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		users.save(user);
	}

}
