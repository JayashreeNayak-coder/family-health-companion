package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "condition")
class Condition extends HealthRecord {

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ConditionStatus status;

	@Column(name = "onset_on")
	private LocalDate onsetOn;

	private String notes;

	protected Condition() {
	}

	Condition(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	void update(String name, ConditionStatus status, LocalDate onsetOn, String notes) {
		this.name = name;
		this.status = status != null ? status : ConditionStatus.UNKNOWN;
		this.onsetOn = onsetOn;
		this.notes = notes;
	}

	String name() {
		return name;
	}

	ConditionStatus status() {
		return status;
	}

	LocalDate onsetOn() {
		return onsetOn;
	}

	String notes() {
		return notes;
	}

}
