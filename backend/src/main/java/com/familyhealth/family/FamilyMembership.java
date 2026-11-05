package com.familyhealth.family;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** Belonging to a family grants no access to anyone's records. Only grants do that (docs/06 section 3). */
@Entity
@Table(name = "family_membership")
class FamilyMembership extends BaseEntity {

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "user_account_id", nullable = false)
	private UUID userAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FamilyRole role;

	protected FamilyMembership() {
	}

	FamilyMembership(UUID familyId, UUID userAccountId, FamilyRole role) {
		this.familyId = familyId;
		this.userAccountId = userAccountId;
		this.role = role;
	}

	UUID familyId() {
		return familyId;
	}

	UUID userAccountId() {
		return userAccountId;
	}

	FamilyRole role() {
		return role;
	}

}
