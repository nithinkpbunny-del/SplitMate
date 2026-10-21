package com.splitmate.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.splitmate.common.RateLimitedException;
import com.splitmate.config.AppProperties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory sliding-window limit on failed logins per (IP, email): by default 10 failures in 5 minutes.
 * A successful login clears the counter. Good enough for a single instance; a cluster would use Redis.
 */
@Component
public class LoginRateLimiter {

	private final int maxAttempts;

	private final Duration window;

	private final Clock clock;

	private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

	@Autowired
	public LoginRateLimiter(AppProperties properties) {
		this(properties.loginRateLimit().maxAttempts(), Duration.ofMinutes(properties.loginRateLimit().windowMinutes()),
				Clock.systemUTC());
	}

	LoginRateLimiter(int maxAttempts, Duration window, Clock clock) {
		this.maxAttempts = maxAttempts;
		this.window = window;
		this.clock = clock;
	}

	/** Throws 429 when this IP has too many recent failures for this email. */
	public void check(String ip, String email) {
		Deque<Instant> attempts = failures.get(key(ip, email));
		if (attempts == null) {
			return;
		}
		synchronized (attempts) {
			prune(attempts);
			if (attempts.size() >= maxAttempts) {
				Instant oldest = attempts.peekFirst();
				long retryAfter = Math.max(1, Duration.between(clock.instant(), oldest.plus(window)).toSeconds());
				long minutes = Math.max(1, (retryAfter + 59) / 60);
				throw new RateLimitedException("Too many login attempts. Try again in " + minutes
						+ (minutes == 1 ? " minute." : " minutes."), retryAfter);
			}
		}
	}

	public void recordFailure(String ip, String email) {
		Deque<Instant> attempts = failures.computeIfAbsent(key(ip, email), k -> new ArrayDeque<>());
		synchronized (attempts) {
			prune(attempts);
			attempts.addLast(clock.instant());
		}
	}

	public void reset(String ip, String email) {
		failures.remove(key(ip, email));
	}

	/** Drops stale entries so the map does not grow forever. */
	@Scheduled(fixedDelay = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
	void evictExpired() {
		failures.entrySet().removeIf(entry -> {
			synchronized (entry.getValue()) {
				prune(entry.getValue());
				return entry.getValue().isEmpty();
			}
		});
	}

	private void prune(Deque<Instant> attempts) {
		Instant cutoff = clock.instant().minus(window);
		while (!attempts.isEmpty() && attempts.peekFirst().isBefore(cutoff)) {
			attempts.removeFirst();
		}
	}

	private static String key(String ip, String email) {
		return ip + "|" + email;
	}

}
