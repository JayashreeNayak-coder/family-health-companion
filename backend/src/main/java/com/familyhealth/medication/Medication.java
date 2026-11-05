package com.familyhealth.medication;

import com.familyhealth.records.HealthRecord;
import com.familyhealth.records.Provenance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Dosage is free text as recorded, so the system can't be read as recommending a dose. */
@Entity
@Table(name = "medication")
class Medication extends HealthRecord {

	@Column(nullable = false)
	private String name;

	@Column(name = "dosage_as_recorded")
	private String dosageAsRecorded;

	@Column(name = "instructions_as_recorded")
	private String instructionsAsRecorded;

	@Column(name = "prescribed_by")
	private String prescribedBy;

	@Column(name = "start_on")
	private LocalDate startOn;

	@Column(name = "end_on")
	private LocalDate endOn;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private MedicationStatus status;

	protected Medication() {
	}

	Medication(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	void update(String name, String dosageAsRecorded, String instructionsAsRecorded, String prescribedBy,
			LocalDate startOn, LocalDate endOn, MedicationStatus status) {
		this.name = name;
		this.dosageAsRecorded = dosageAsRecorded;
		this.instructionsAsRecorded = instructionsAsRecorded;
		this.prescribedBy = prescribedBy;
		this.startOn = startOn;
		this.endOn = endOn;
		this.status = status != null ? status : MedicationStatus.UNKNOWN;
	}

	/** Missing start and end dates don't limit the range, because they aren't guessed. */
	boolean covers(LocalDate date) {
		return (startOn == null || !date.isBefore(startOn)) && (endOn == null || !date.isAfter(endOn));
	}

	boolean isActive() {
		return status == MedicationStatus.ACTIVE;
	}

	String name() {
		return name;
	}

	String dosageAsRecorded() {
		return dosageAsRecorded;
	}

	String instructionsAsRecorded() {
		return instructionsAsRecorded;
	}

	String prescribedBy() {
		return prescribedBy;
	}

	LocalDate startOn() {
		return startOn;
	}

	LocalDate endOn() {
		return endOn;
	}

	MedicationStatus status() {
		return status;
	}

}
