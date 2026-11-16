package com.familyhealth.emergencycard;

import com.familyhealth.common.FixedWindowRateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits public card reads per IP address in a fixed window (docs/06 section 6). Every read
 * counts, found or not, so guessing links is throttled too.
 */
@Component
class PublicCardRateLimiter {

	private final FixedWindowRateLimiter limiter;

	PublicCardRateLimiter(Clock clock, @Value("${app.emergency-card.rate-limit.window}") Duration window,
			@Value("${app.emergency-card.rate-limit.max-per-address}") int maxPerAddress) {
		this.limiter = new FixedWindowRateLimiter(clock, window, maxPerAddress);
	}

	/** Counts one read, or throws when the address has used up its window. */
	void acquire(String address) {
		limiter.acquire(address);
	}

}
