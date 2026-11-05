package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/** Not a health record, so it carries no provenance. */
@Entity
@Table(name = "emergency_contact")
class EmergencyContact extends ProfileRecord {

	@Column(nullable = false)
	private String name;

	private String relationship;

	@Column(nullable = false)
	private String phone;

	protected EmergencyContact() {
	}

	EmergencyContact(UUID profileId) {
		super(profileId);
	}

	void update(String name, String relationship, String phone) {
		this.name = name;
		this.relationship = relationship;
		this.phone = phone;
	}

	String name() {
		return name;
	}

	String relationship() {
		return relationship;
	}

	String phone() {
		return phone;
	}

}
