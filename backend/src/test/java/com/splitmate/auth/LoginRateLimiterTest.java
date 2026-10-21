package com.splitmate.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.splitmate.common.RateLimitedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

	private final MutableClock clock = new MutableClock(Instant.parse("2026-10-01T10:00:00Z"));

	private final LoginRateLimiter limiter = new LoginRateLimiter(10, Duration.ofMinutes(5), clock);

	@Test
	void blocksAfterTenFailuresAndUnblocksAfterWindow() {
		for (int i = 0; i < 10; i++) {
			limiter.check("1.1.1.1", "a@x.com");
			limiter.recordFailure("1.1.1.1", "a@x.com");
		}
		assertThatThrownBy(() -> limiter.check("1.1.1.1", "a@x.com"))
			.isInstanceOf(RateLimitedException.class)
			.hasMessageContaining("Too many login attempts");

		// other emails and other IPs are not affected
		assertThatCode(() -> limiter.check("1.1.1.1", "b@x.com")).doesNotThrowAnyException();
		assertThatCode(() -> limiter.check("2.2.2.2", "a@x.com")).doesNotThrowAnyException();

		clock.advance(Duration.ofMinutes(5).plusSeconds(1));
		assertThatCode(() -> limiter.check("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
	}

	@Test
	void successfulLoginResetsCounter() {
		for (int i = 0; i < 9; i++) {
			limiter.recordFailure("1.1.1.1", "a@x.com");
		}
		limiter.reset("1.1.1.1", "a@x.com");
		limiter.recordFailure("1.1.1.1", "a@x.com");
		assertThatCode(() -> limiter.check("1.1.1.1", "a@x.com")).doesNotThrowAnyException();
	}

	static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}

	}

}
