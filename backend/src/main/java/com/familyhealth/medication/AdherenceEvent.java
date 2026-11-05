package com.familyhealth.medication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** That a dose was taken or skipped, as the user reported it. */
@Entity
@Immutable
@Table(name = "adherence_event")
class AdherenceEvent {

	@Id
	private UUID id;

	@Column(name = "medication_id", nullable = false)
	private UUID medicationId;

	@Column(name = "schedule_id")
	private UUID scheduleId;

	@Column(name = "scheduled_for", nullable = false)
	private Instant scheduledFor;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AdherenceStatus status;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "recorded_by", nullable = false)
	private UUID recordedBy;

	protected AdherenceEvent() {
	}

	AdherenceEvent(UUID medicationId, UUID scheduleId, Instant scheduledFor, AdherenceStatus status, Instant recordedAt,
			UUID recordedBy) {
		this.id = UUID.randomUUID();
		this.medicationId = medicationId;
		this.scheduleId = scheduleId;
		this.scheduledFor = scheduledFor;
		this.status = status;
		this.recordedAt = recordedAt;
		this.recordedBy = recordedBy;
	}

	UUID id() {
		return id;
	}

	UUID scheduleId() {
		return scheduleId;
	}

	Instant scheduledFor() {
		return scheduledFor;
	}

	AdherenceStatus status() {
		return status;
	}

	Instant recordedAt() {
		return recordedAt;
	}

}
