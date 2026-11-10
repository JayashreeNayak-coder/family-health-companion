package com.familyhealth.emergencycard;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.familyhealth.common.RateLimitedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits public card reads per IP address in a fixed window (docs/06 section 6). Every read
 * counts, found or not, so guessing links is throttled too. In memory, like the login limiter:
 * lost on restart and not shared between instances, which suits the single-instance prototype.
 */
@Component
class PublicCardRateLimiter {

	static final int MAX_TRACKED_ADDRESSES = 100_000;

	private final Clock clock;

	private final Duration window;

	private final int maxPerAddress;

	private final Map<String, Window> addresses = new ConcurrentHashMap<>();

	PublicCardRateLimiter(Clock clock, @Value("${app.emergency-card.rate-limit.window}") Duration window,
			@Value("${app.emergency-card.rate-limit.max-per-address}") int maxPerAddress) {
		this.clock = clock;
		this.window = window;
		this.maxPerAddress = maxPerAddress;
	}

	/** Counts one read, or throws when the address has used up its window. */
	void acquire(String address) {
		Instant now = clock.instant();
		if (addresses.size() >= MAX_TRACKED_ADDRESSES && !addresses.containsKey(address)) {
			addresses.values().removeIf(entry -> isExpired(entry, now));
		}
		Window current = addresses.compute(address, (ignored, existing) -> existing == null || isExpired(existing, now)
				? new Window(now, 1)
				: new Window(existing.startedAt(), existing.count() + 1));
		if (current.count() > maxPerAddress) {
			long millis = Duration.between(now, current.startedAt().plus(window)).toMillis();
			throw new RateLimitedException(Math.max(1, (millis + 999) / 1000));
		}
	}

	private boolean isExpired(Window entry, Instant now) {
		return !now.isBefore(entry.startedAt().plus(window));
	}

	private record Window(Instant startedAt, int count) {
	}

}
