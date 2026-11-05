package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Reaction and severity are kept as recorded. The system never grades them. */
@Entity
@Table(name = "allergy")
class Allergy extends HealthRecord {

	@Column(nullable = false)
	private String allergen;

	@Column(name = "reaction_as_recorded")
	private String reactionAsRecorded;

	@Column(name = "severity_as_recorded")
	private String severityAsRecorded;

	private String notes;

	protected Allergy() {
	}

	Allergy(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	void update(String allergen, String reactionAsRecorded, String severityAsRecorded, String notes) {
		this.allergen = allergen;
		this.reactionAsRecorded = reactionAsRecorded;
		this.severityAsRecorded = severityAsRecorded;
		this.notes = notes;
	}

	String allergen() {
		return allergen;
	}

	String reactionAsRecorded() {
		return reactionAsRecorded;
	}

	String severityAsRecorded() {
		return severityAsRecorded;
	}

	String notes() {
		return notes;
	}

}
