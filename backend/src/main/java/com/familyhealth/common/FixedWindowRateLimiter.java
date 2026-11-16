package com.familyhealth.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Counts requests per key, usually an IP address, in a fixed window, and throws
 * {@link RateLimitedException} once a key has used up its window. In memory: lost on restart and
 * not shared between instances, which suits the single-instance prototype.
 */
public class FixedWindowRateLimiter {

	static final int MAX_TRACKED_KEYS = 100_000;

	private final Clock clock;

	private final Duration window;

	private final int maxPerKey;

	private final Map<String, Window> keys = new ConcurrentHashMap<>();

	public FixedWindowRateLimiter(Clock clock, Duration window, int maxPerKey) {
		this.clock = clock;
		this.window = window;
		this.maxPerKey = maxPerKey;
	}

	/** Counts one request, or throws when the key has used up its window. */
	public void acquire(String key) {
		Instant now = clock.instant();
		if (keys.size() >= MAX_TRACKED_KEYS && !keys.containsKey(key)) {
			keys.values().removeIf(entry -> isExpired(entry, now));
		}
		Window current = keys.compute(key, (ignored, existing) -> existing == null || isExpired(existing, now)
				? new Window(now, 1)
				: new Window(existing.startedAt(), existing.count() + 1));
		if (current.count() > maxPerKey) {
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
