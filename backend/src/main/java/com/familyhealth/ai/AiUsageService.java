package com.familyhealth.ai;

import com.familyhealth.common.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-account and global limits over a rolling day, so one account can't use up a free or small
 * budget (docs/06 section 8.6). Each accepted request is counted when made; each provider call is
 * recorded afterwards with its token counts.
 */
@Service
public class AiUsageService {

	public static final String REQUESTED = "REQUESTED";

	private static final Duration WINDOW = Duration.ofDays(1);

	private final AiUsageEventRepository events;

	private final Clock clock;

	private final int perAccountPerDay;

	private final int globalPerDay;

	AiUsageService(AiUsageEventRepository events, Clock clock,
			@Value("${app.ai.limits.per-account-per-day}") int perAccountPerDay,
			@Value("${app.ai.limits.global-per-day}") int globalPerDay) {
		this.events = events;
		this.clock = clock;
		this.perAccountPerDay = perAccountPerDay;
		this.globalPerDay = globalPerDay;
	}

	/** Counts the request if it's within both limits. Joins the caller's transaction. */
	@Transactional
	public void claim(UUID accountId, String feature) {
		Instant now = clock.instant();
		Instant since = now.minus(WINDOW);
		if (events.countByUserAccountIdAndFeatureAndOutcomeAndOccurredAtAfter(accountId, feature, REQUESTED,
				since) >= perAccountPerDay
				|| events.countByFeatureAndOutcomeAndOccurredAtAfter(feature, REQUESTED, since) >= globalPerDay) {
			throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_LIMIT_REACHED",
					"The daily AI limit has been reached. Manual entry still works.");
		}
		events.save(new AiUsageEvent(accountId, feature, now, null, null, REQUESTED));
	}

	/** Records one provider call. Its own transaction, so a failure that follows still leaves it counted. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordCall(UUID accountId, String feature, Integer inputTokens, Integer outputTokens, String outcome) {
		events.save(new AiUsageEvent(accountId, feature, clock.instant(), inputTokens, outputTokens, outcome));
	}

}
