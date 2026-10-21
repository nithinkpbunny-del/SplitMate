package com.splitmate.user;

import java.util.List;

import jakarta.validation.Valid;
import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users", description = "Your profile, avatar colour and password")
@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "Change your name or avatar colour")
	@PatchMapping("/me")
	public UserResponse updateProfile(@CurrentUserId ObjectId userId, @Valid @RequestBody UpdateProfileRequest request) {
		return UserResponse.from(userService.updateProfile(userId, request));
	}

	@Operation(summary = "Change your password")
	@PutMapping("/me/password")
	public ResponseEntity<Void> changePassword(@CurrentUserId ObjectId userId,
			@Valid @RequestBody ChangePasswordRequest request) {
		userService.changePassword(userId, request);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "The avatar colour palette")
	@GetMapping("/avatar-colors")
	public List<String> avatarColors() {
		return AvatarPalette.COLORS;
	}

}
