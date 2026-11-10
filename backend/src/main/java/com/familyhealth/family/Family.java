package com.familyhealth.family;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "family")
class Family extends BaseEntity {

	@Column(nullable = false)
	private String name;

	@Column(name = "created_by", nullable = false)
	private UUID createdBy;

	@Column(name = "photo_key")
	private String photoKey;

	@Column(name = "photo_updated_at")
	private Instant photoUpdatedAt;

	protected Family() {
	}

	Family(String name, UUID createdBy) {
		this.name = name;
		this.createdBy = createdBy;
	}

	String name() {
		return name;
	}

	void rename(String name) {
		this.name = name;
	}

	String photoKey() {
		return photoKey;
	}

	Instant photoUpdatedAt() {
		return photoUpdatedAt;
	}

	/** Returns the replaced key, if any, so its file can be deleted. */
	String usePhoto(String key, Instant now) {
		String old = photoKey;
		photoKey = key;
		photoUpdatedAt = key == null ? null : now;
		return old;
	}

}
