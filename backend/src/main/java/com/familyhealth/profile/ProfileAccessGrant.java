package com.familyhealth.profile;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** The only source of access to a profile (docs/06 section 3, rule 1). */
@Entity
@Table(name = "profile_access_grant")
class ProfileAccessGrant extends BaseEntity {

	@Column(name = "profile_id", nullable = false)
	private UUID profileId;

	@Column(name = "user_account_id", nullable = false)
	private UUID userAccountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccessLevel level;

	@Column(name = "granted_by", nullable = false)
	private UUID grantedBy;

	protected ProfileAccessGrant() {
	}

	ProfileAccessGrant(UUID profileId, UUID userAccountId, AccessLevel level, UUID grantedBy) {
		this.profileId = profileId;
		this.userAccountId = userAccountId;
		this.level = level;
		this.grantedBy = grantedBy;
	}

	void change(AccessLevel level, UUID grantedBy) {
		this.level = level;
		this.grantedBy = grantedBy;
	}

	UUID userAccountId() {
		return userAccountId;
	}

	AccessLevel level() {
		return level;
	}

}
