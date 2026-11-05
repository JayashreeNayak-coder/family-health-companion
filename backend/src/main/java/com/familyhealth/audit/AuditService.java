package com.familyhealth.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

	public static final String TARGET_ACCOUNT = "ACCOUNT";

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
		events.save(new AuditEvent(clock.instant(), actorAccountId, action, targetType, targetId, outcome, metadata));
	}

}
