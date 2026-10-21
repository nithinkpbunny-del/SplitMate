package com.splitmate.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.splitmate.common.security.CurrentUserId;
import com.splitmate.user.UserResponse;
import com.splitmate.user.UserService;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Authentication", description = "Register, log in and out, the current user, and the CSRF token")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	private final UserService userService;

	private final AuthCookies cookies;

	public AuthController(AuthService authService, UserService userService, AuthCookies cookies) {
		this.authService = authService;
		this.userService = userService;
		this.cookies = cookies;
	}

	@Operation(summary = "Create an account and log in")
	@SecurityRequirements
	@ApiResponse(responseCode = "201", description = "Created")
	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		AuthService.AuthResult result = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, cookies.create(result.token()).toString())
			.body(UserResponse.from(result.user()));
	}

	@Operation(summary = "Log in (sets the SM_TOKEN cookie; rate limited)")
	@SecurityRequirements
	@PostMapping("/login")
	public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		AuthService.AuthResult result = authService.login(request, http.getRemoteAddr());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookies.create(result.token()).toString())
			.body(UserResponse.from(result.user()));
	}

	@Operation(summary = "Log out (clears the cookie)")
	@SecurityRequirements
	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
	}

	@Operation(summary = "The logged-in user")
	@GetMapping("/me")
	public UserResponse me(@CurrentUserId ObjectId userId) {
		return UserResponse.from(userService.require(userId));
	}

	/**
	 * Makes sure the XSRF-TOKEN cookie exists. The SPA calls this once on startup; afterwards it echoes
	 * the cookie value in the X-XSRF-TOKEN header on every state-changing request.
	 */
	@Operation(summary = "Get the CSRF token (also sets the XSRF-TOKEN cookie)")
	@SecurityRequirements
	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}

}
