package com.familyhealth.records;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.util.UUID;

/** Anything owned by one health profile. */
@MappedSuperclass
public abstract class ProfileRecord extends BaseEntity {

	@Column(name = "profile_id", nullable = false, updatable = false)
	private UUID profileId;

	protected ProfileRecord() {
	}

	protected ProfileRecord(UUID profileId) {
		this.profileId = profileId;
	}

	public UUID profileId() {
		return profileId;
	}

}
