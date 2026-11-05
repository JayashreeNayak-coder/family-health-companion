package com.familyhealth.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {

	private static final String EMAIL = "someone@example.test";

	private static final String ADDRESS = "203.0.113.1";

	private final MutableClock clock = new MutableClock();

	private final LoginRateLimiter limiter = new LoginRateLimiter(clock, Duration.ofMinutes(15), 3, 5);

	@Test
	void allowsAttemptsBelowTheLimit() {
		failures(EMAIL, ADDRESS, 2);

		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).isEmpty();
	}

	@Test
	void blocksAnAccountAtItsLimitFromAnyAddress() {
		failures(EMAIL, ADDRESS, 3);

		assertThat(limiter.retryAfterSeconds(EMAIL, "203.0.113.2")).hasValue(900);
	}

	@Test
	void blocksAnAddressAtItsLimitForAnyAccount() {
		for (int i = 0; i < 5; i++) {
			limiter.recordFailure("user" + i + "@example.test", ADDRESS);
		}

		assertThat(limiter.retryAfterSeconds("another@example.test", ADDRESS)).isPresent();
		assertThat(limiter.retryAfterSeconds("another@example.test", "203.0.113.2")).isEmpty();
	}

	@Test
	void allowsAttemptsAgainAfterTheWindow() {
		failures(EMAIL, ADDRESS, 3);

		clock.advance(Duration.ofMinutes(14));
		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).hasValue(60);

		clock.advance(Duration.ofMinutes(1));
		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).isEmpty();
	}

	@Test
	void failureAfterTheWindowStartsANewCount() {
		failures(EMAIL, ADDRESS, 3);
		clock.advance(Duration.ofMinutes(15));

		failures(EMAIL, ADDRESS, 1);

		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).isEmpty();
	}

	@Test
	void successClearsTheAccountButNotTheAddress() {
		failures(EMAIL, ADDRESS, 3);
		failures("other@example.test", ADDRESS, 2);

		limiter.recordSuccess(EMAIL);

		assertThat(limiter.retryAfterSeconds(EMAIL, "203.0.113.2")).isEmpty();
		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).isPresent();
	}

	@Test
	void trackedKeysAreBounded() {
		for (int i = 0; i < LoginRateLimiter.MAX_TRACKED_KEYS; i++) {
			limiter.recordFailure("user" + i + "@example.test", "address" + i);
		}

		limiter.recordFailure(EMAIL, ADDRESS);
		limiter.recordFailure(EMAIL, ADDRESS);
		limiter.recordFailure(EMAIL, ADDRESS);
		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).isEmpty();

		// Expired entries are purged, so new keys are tracked again.
		clock.advance(Duration.ofMinutes(15));
		failures(EMAIL, ADDRESS, 3);
		assertThat(limiter.retryAfterSeconds(EMAIL, ADDRESS)).isPresent();
	}

	private void failures(String email, String address, int count) {
		for (int i = 0; i < count; i++) {
			limiter.recordFailure(email, address);
		}
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-05T08:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

}
