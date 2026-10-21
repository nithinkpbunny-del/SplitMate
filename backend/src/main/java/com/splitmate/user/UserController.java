package com.splitmate.user;

import java.util.List;

import jakarta.validation.Valid;
import org.bson.types.ObjectId;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PatchMapping("/me")
	public UserResponse updateProfile(@CurrentUserId ObjectId userId, @Valid @RequestBody UpdateProfileRequest request) {
		return UserResponse.from(userService.updateProfile(userId, request));
	}

	@PutMapping("/me/password")
	public ResponseEntity<Void> changePassword(@CurrentUserId ObjectId userId,
			@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(userId, request);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/avatar-colors")
	public List<String> avatarColors() {
		return AvatarPalette.COLORS;
	}

}
