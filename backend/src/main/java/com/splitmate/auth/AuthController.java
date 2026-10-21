package com.splitmate.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.bson.types.ObjectId;

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

	@PostMapping("/register")
	public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		AuthService.AuthResult result = authService.register(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.header(HttpHeaders.SET_COOKIE, cookies.create(result.token()).toString())
			.body(UserResponse.from(result.user()));
	}

	@PostMapping("/login")
	public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
		AuthService.AuthResult result = authService.login(request, http.getRemoteAddr());
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, cookies.create(result.token()).toString())
			.body(UserResponse.from(result.user()));
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).build();
	}

	@GetMapping("/me")
	public UserResponse me(@CurrentUserId ObjectId userId) {
		return UserResponse.from(userService.require(userId));
	}

	/**
	 * Makes sure the XSRF-TOKEN cookie exists. The SPA calls this once on startup; afterwards it echoes
	 * the cookie value in the X-XSRF-TOKEN header on every state-changing request.
	 */
	@GetMapping("/csrf")
	public Map<String, String> csrf(CsrfToken token) {
		return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
	}

}
