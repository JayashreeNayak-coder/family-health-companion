package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Date and time are as the user recorded them, and either may be unknown (docs/04 section 1.1). */
@Entity
@Table(name = "doctor_visit")
class DoctorVisit extends HealthRecord {

	@Column(name = "occurred_on")
	private LocalDate occurredOn;

	@Column(name = "occurred_time")
	private LocalTime occurredTime;

	@Column(name = "doctor_name")
	private String doctorName;

	private String facility;

	private String reason;

	private String notes;

	protected DoctorVisit() {
	}

	DoctorVisit(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	void update(LocalDate occurredOn, LocalTime occurredTime, String doctorName, String facility, String reason,
			String notes) {
		this.occurredOn = occurredOn;
		this.occurredTime = occurredTime;
		this.doctorName = doctorName;
		this.facility = facility;
		this.reason = reason;
		this.notes = notes;
	}

	LocalDate occurredOn() {
		return occurredOn;
	}

	LocalTime occurredTime() {
		return occurredTime;
	}

	String doctorName() {
		return doctorName;
	}

	String facility() {
		return facility;
	}

	String reason() {
		return reason;
	}

	String notes() {
		return notes;
	}

}
