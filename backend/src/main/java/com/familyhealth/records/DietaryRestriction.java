package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "dietary_restriction")
class DietaryRestriction extends HealthRecord {

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DietaryKind kind;

	@Column(nullable = false)
	private String description;

	protected DietaryRestriction() {
	}

	DietaryRestriction(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	void update(DietaryKind kind, String description) {
		this.kind = kind;
		this.description = description;
	}

	DietaryKind kind() {
		return kind;
	}

	String description() {
		return description;
	}

}
