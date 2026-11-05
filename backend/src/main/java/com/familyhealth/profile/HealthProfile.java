package com.familyhealth.profile;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** A person's health record holder. Separate from login accounts: a dependent has no account at all. */
@Entity
@Table(name = "health_profile")
class HealthProfile extends BaseEntity {

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "linked_account_id")
	private UUID linkedAccountId;

	@Column(name = "full_name", nullable = false)
	private String fullName;

	@Column(name = "date_of_birth")
	private LocalDate dateOfBirth;

	@Column(name = "is_dependent", nullable = false)
	private boolean dependent;

	protected HealthProfile() {
	}

	HealthProfile(UUID familyId, UUID linkedAccountId, String fullName, LocalDate dateOfBirth, boolean dependent) {
		this.familyId = familyId;
		this.linkedAccountId = linkedAccountId;
		this.fullName = fullName;
		this.dateOfBirth = dateOfBirth;
		this.dependent = dependent;
	}

	void updateDetails(String fullName, LocalDate dateOfBirth) {
		this.fullName = fullName;
		this.dateOfBirth = dateOfBirth;
	}

	void linkTo(UUID accountId) {
		linkedAccountId = accountId;
		dependent = false;
	}

	UUID familyId() {
		return familyId;
	}

	UUID linkedAccountId() {
		return linkedAccountId;
	}

	String fullName() {
		return fullName;
	}

	LocalDate dateOfBirth() {
		return dateOfBirth;
	}

	boolean dependent() {
		return dependent;
	}

}
