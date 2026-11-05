package com.familyhealth.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	public static final String TARGET_ACCOUNT = "ACCOUNT";

	public static final String TARGET_PROFILE = "PROFILE";

	public static final String TARGET_FAMILY = "FAMILY";

	private final AuditEventRepository events;

	private final Clock clock;

	AuditService(AuditEventRepository events, Clock clock) {
		this.events = events;
		this.clock = clock;
	}

	/**
	 * Joins the caller's transaction when there is one, so the event commits with the change it describes.
	 * Metadata must hold only short codes: never submitted values, health content, or tokens.
	 */
	@Transactional
	public void record(AuditAction action, AuditOutcome outcome, UUID actorAccountId, String targetType,
			UUID targetId, Map<String, String> metadata) {
		events.save(new AuditEvent(clock.instant(), actorAccountId, action, targetType, targetId, null, outcome,
				metadata));
	}

	/** An action on a profile. Joins the caller's transaction, like {@link #record}. */
	@Transactional
	public void recordForProfile(AuditAction action, UUID actorAccountId, UUID profileId,
			Map<String, String> metadata) {
		events.save(new AuditEvent(clock.instant(), actorAccountId, action, TARGET_PROFILE, profileId, profileId,
				AuditOutcome.SUCCESS, metadata));
	}

	/** Commits on its own, because the failure that follows a denial rolls back the caller's transaction. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordProfileDenied(UUID actorAccountId, UUID profileId, String reason) {
		events.save(new AuditEvent(clock.instant(), actorAccountId, AuditAction.ACCESS_DENIED, TARGET_PROFILE,
				profileId, profileId, AuditOutcome.DENIED, Map.of("reason", reason)));
	}

}
