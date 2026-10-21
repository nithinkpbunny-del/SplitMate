package com.splitmate.auth;

import java.time.Instant;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.splitmate.common.ApiException;
import com.splitmate.user.AvatarPalette;
import com.splitmate.user.User;
import com.splitmate.user.UserRepository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Registration and login. Returns the user together with a freshly signed token.
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);

	/** Generic message so the response does not reveal which emails are registered. */
	static final String BAD_CREDENTIALS = "Incorrect email or password";

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final TokenService tokenService;

	private final LoginRateLimiter rateLimiter;

	/** Hash compared against when the email is unknown, so both paths take the same time. */
	private final String dummyHash;

	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService,
			LoginRateLimiter rateLimiter) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.tokenService = tokenService;
		this.rateLimiter = rateLimiter;
		this.dummyHash = passwordEncoder.encode("splitmate-timing-guard");
	}

	public record AuthResult(User user, String token) {
	}

	public AuthResult register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (users.existsByEmail(email)) {
			throw ApiException.conflict("An account with this email already exists. Try logging in.");
		}
		User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()),
				AvatarPalette.random());
		user.setLastLoginAt(Instant.now());
		try {
			user = users.save(user);
		}
		catch (DuplicateKeyException ex) {
			// Two registrations raced past the exists check; the unique index caught it.
			throw ApiException.conflict("An account with this email already exists. Try logging in.");
		}
		log.info("Registered user {}", user.getId());
		return new AuthResult(user, tokenService.issue(user));
	}

	public AuthResult login(LoginRequest request, String clientIp) {
		String email = normalizeEmail(request.email());
		rateLimiter.check(clientIp, email);
		User user = users.findByEmail(email).orElse(null);
		boolean matches = passwordEncoder.matches(request.password(), user != null ? user.getPasswordHash() : dummyHash);
		if (user == null || !matches) {
			rateLimiter.recordFailure(clientIp, email);
			throw ApiException.unauthorized(BAD_CREDENTIALS);
		}
		rateLimiter.reset(clientIp, email);
		user.setLastLoginAt(Instant.now());
		user = users.save(user);
		return new AuthResult(user, tokenService.issue(user));
	}

	static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
