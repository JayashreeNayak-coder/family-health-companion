package com.familyhealth.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Who did what, when, and the outcome. Never holds health content, passwords, or tokens. */
@Entity
@Immutable
@Table(name = "audit_event")
class AuditEvent {

	@Id
	private UUID id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "actor_account_id")
	private UUID actorAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditAction action;

	@Column(name = "target_type")
	private String targetType;

	@Column(name = "target_id")
	private UUID targetId;

	@Column(name = "profile_id")
	private UUID profileId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditOutcome outcome;

	@JdbcTypeCode(SqlTypes.JSON)
	private Map<String, String> metadata;

	protected AuditEvent() {
	}

	AuditEvent(Instant occurredAt, UUID actorAccountId, AuditAction action, String targetType, UUID targetId,
			AuditOutcome outcome, Map<String, String> metadata) {
		this.id = UUID.randomUUID();
		this.occurredAt = occurredAt;
		this.actorAccountId = actorAccountId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.outcome = outcome;
		this.metadata = metadata;
	}

}
