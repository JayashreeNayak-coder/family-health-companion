package com.familyhealth.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Counts failed logins per account and per IP address in a fixed time window. Counters are kept
 * in memory: they are lost on restart and not shared between instances, which suits the
 * single-instance prototype. Unknown emails are counted exactly like known ones.
 */
@Component
class LoginRateLimiter {

	/** Upper bound on tracked keys per counter, so rotating fake emails cannot exhaust memory. */
	static final int MAX_TRACKED_KEYS = 100_000;

	private final Clock clock;

	private final Duration window;

	private final int maxPerAccount;

	private final int maxPerAddress;

	private final Map<String, Window> accounts = new ConcurrentHashMap<>();

	private final Map<String, Window> addresses = new ConcurrentHashMap<>();

	LoginRateLimiter(Clock clock, @Value("${app.login-rate-limit.window}") Duration window,
			@Value("${app.login-rate-limit.max-failures-per-account}") int maxPerAccount,
			@Value("${app.login-rate-limit.max-failures-per-address}") int maxPerAddress) {
		this.clock = clock;
		this.window = window;
		this.maxPerAccount = maxPerAccount;
		this.maxPerAddress = maxPerAddress;
	}

	/** Seconds until another attempt is allowed, or empty if an attempt is allowed now. */
	OptionalLong retryAfterSeconds(String email, String address) {
		Instant now = clock.instant();
		long account = blockedSeconds(accounts.get(email), maxPerAccount, now);
		long ip = blockedSeconds(addresses.get(address), maxPerAddress, now);
		long longest = Math.max(account, ip);
		return longest > 0 ? OptionalLong.of(longest) : OptionalLong.empty();
	}

	void recordFailure(String email, String address) {
		increment(accounts, email);
		increment(addresses, address);
	}

	/** Clears the account's counter only: one success must not reset an attacker's IP counter. */
	void recordSuccess(String email) {
		accounts.remove(email);
	}

	private long blockedSeconds(Window current, int max, Instant now) {
		if (current == null || current.failures() < max || isExpired(current, now)) {
			return 0;
		}
		long millis = Duration.between(now, current.startedAt().plus(window)).toMillis();
		return Math.max(1, (millis + 999) / 1000);
	}

	private void increment(Map<String, Window> counters, String key) {
		Instant now = clock.instant();
		if (counters.size() >= MAX_TRACKED_KEYS && !counters.containsKey(key)) {
			counters.values().removeIf(entry -> isExpired(entry, now));
			if (counters.size() >= MAX_TRACKED_KEYS) {
				return;
			}
		}
		counters.compute(key, (ignored, current) -> current == null || isExpired(current, now)
				? new Window(now, 1)
				: new Window(current.startedAt(), current.failures() + 1));
	}

	private boolean isExpired(Window entry, Instant now) {
		return !now.isBefore(entry.startedAt().plus(window));
	}

	private record Window(Instant startedAt, int failures) {
	}

}
