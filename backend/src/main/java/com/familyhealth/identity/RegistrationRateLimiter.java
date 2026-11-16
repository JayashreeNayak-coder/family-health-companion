package com.familyhealth.identity;

import com.familyhealth.common.FixedWindowRateLimiter;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits account registrations per IP address (docs/06 section 2), so a public demo can't be filled
 * with accounts. Every valid attempt counts, including one for an email that is already taken. The
 * limit allows a classroom sharing one address to register together.
 */
@Component
class RegistrationRateLimiter {

	private final FixedWindowRateLimiter limiter;

	RegistrationRateLimiter(Clock clock, @Value("${app.registration-rate-limit.window}") Duration window,
			@Value("${app.registration-rate-limit.max-per-address}") int maxPerAddress) {
		this.limiter = new FixedWindowRateLimiter(clock, window, maxPerAddress);
	}

	void acquire(String address) {
		limiter.acquire(address);
	}

}
