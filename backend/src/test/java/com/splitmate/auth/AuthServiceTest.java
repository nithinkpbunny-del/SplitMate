package com.splitmate.auth;

import java.util.Optional;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.splitmate.common.ApiException;
import com.splitmate.user.AvatarPalette;
import com.splitmate.user.User;
import com.splitmate.user.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

	private final UserRepository users = mock(UserRepository.class);

	private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

	private final TokenService tokens = mock(TokenService.class);

	private final LoginRateLimiter limiter = mock(LoginRateLimiter.class);

	private AuthService service;

	@BeforeEach
	void setUp() {
		service = new AuthService(users, encoder, tokens, limiter);
		when(users.save(any(User.class))).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			if (u.getId() == null) {
				u.setId(new ObjectId());
			}
			return u;
		});
		when(tokens.issue(any())).thenReturn("signed-token");
	}

	@Test
	void registerNormalisesEmailHashesPasswordAndAssignsColour() {
		AuthService.AuthResult result = service.register(new RegisterRequest("  Aarav Mehta ", " Aarav@Demo.COM ", "Demo@1234"));

		ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		User user = saved.getValue();
		assertThat(user.getEmail()).isEqualTo("aarav@demo.com");
		assertThat(user.getName()).isEqualTo("Aarav Mehta");
		assertThat(user.getPasswordHash()).isNotEqualTo("Demo@1234").startsWith("$2a$");
		assertThat(encoder.matches("Demo@1234", user.getPasswordHash())).isTrue();
		assertThat(AvatarPalette.COLORS).contains(user.getAvatarColor());
		assertThat(result.token()).isEqualTo("signed-token");
	}

	@Test
	void registerRejectsDuplicateEmail() {
		when(users.existsByEmail("aarav@demo.com")).thenReturn(true);

		assertThatThrownBy(() -> service.register(new RegisterRequest("Aarav", "AARAV@demo.com", "Demo@1234")))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
		verify(users, never()).save(any());
	}

	@Test
	void loginSucceedsWithCorrectPasswordAndResetsLimiter() {
		User user = new User("Aarav", "aarav@demo.com", encoder.encode("Demo@1234"), "#F59E0B");
		user.setId(new ObjectId());
		when(users.findByEmail("aarav@demo.com")).thenReturn(Optional.of(user));

		AuthService.AuthResult result = service.login(new LoginRequest("Aarav@Demo.com", "Demo@1234"), "127.0.0.1");

		assertThat(result.user().getLastLoginAt()).isNotNull();
		verify(limiter).check("127.0.0.1", "aarav@demo.com");
		verify(limiter).reset("127.0.0.1", "aarav@demo.com");
	}

	@Test
	void loginWithWrongPasswordIs401AndRecordsFailure() {
		User user = new User("Aarav", "aarav@demo.com", encoder.encode("Demo@1234"), "#F59E0B");
		when(users.findByEmail("aarav@demo.com")).thenReturn(Optional.of(user));

		assertThatThrownBy(() -> service.login(new LoginRequest("aarav@demo.com", "wrong-pass1"), "127.0.0.1"))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
				assertThat(ex.getMessage()).isEqualTo(AuthService.BAD_CREDENTIALS);
			});
		verify(limiter).recordFailure("127.0.0.1", "aarav@demo.com");
	}

	@Test
	void loginWithUnknownEmailGivesSameMessage() {
		when(users.findByEmail("ghost@demo.com")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.login(new LoginRequest("ghost@demo.com", "whatever1"), "127.0.0.1"))
			.hasMessage(AuthService.BAD_CREDENTIALS);
	}

}
