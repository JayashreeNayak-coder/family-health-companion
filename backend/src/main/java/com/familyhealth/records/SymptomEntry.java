package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "symptom_entry")
class SymptomEntry extends HealthRecord {

	@Column(nullable = false)
	private String description;

	@Column(name = "severity_as_reported")
	private String severityAsReported;

	@Column(name = "occurred_on")
	private LocalDate occurredOn;

	@Column(name = "occurred_time")
	private LocalTime occurredTime;

	private String notes;

	protected SymptomEntry() {
	}

	SymptomEntry(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	void update(String description, String severityAsReported, LocalDate occurredOn, LocalTime occurredTime,
			String notes) {
		this.description = description;
		this.severityAsReported = severityAsReported;
		this.occurredOn = occurredOn;
		this.occurredTime = occurredTime;
		this.notes = notes;
	}

	String description() {
		return description;
	}

	String severityAsReported() {
		return severityAsReported;
	}

	LocalDate occurredOn() {
		return occurredOn;
	}

	LocalTime occurredTime() {
		return occurredTime;
	}

	String notes() {
		return notes;
	}

}
