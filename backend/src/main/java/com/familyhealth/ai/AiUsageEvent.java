package com.familyhealth.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Counts and outcomes only, never prompt or response text. Append-only. */
@Entity
@Table(name = "ai_usage_event")
class AiUsageEvent {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "user_account_id", nullable = false)
	private UUID userAccountId;

	@Column(nullable = false)
	private String feature;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "input_tokens")
	private Integer inputTokens;

	@Column(name = "output_tokens")
	private Integer outputTokens;

	@Column(nullable = false)
	private String outcome;

	protected AiUsageEvent() {
	}

	AiUsageEvent(UUID userAccountId, String feature, Instant occurredAt, Integer inputTokens, Integer outputTokens,
			String outcome) {
		this.userAccountId = userAccountId;
		this.feature = feature;
		this.occurredAt = occurredAt;
		this.inputTokens = inputTokens;
		this.outputTokens = outputTokens;
		this.outcome = outcome;
	}

}
