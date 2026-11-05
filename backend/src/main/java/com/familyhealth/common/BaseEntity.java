package com.familyhealth.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The ID, timestamps, and optimistic lock version from docs/04 section 1. */
@MappedSuperclass
public abstract class BaseEntity {

	@Id
	private UUID id = UUID.randomUUID();

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	public UUID id() {
		return id;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant updatedAt() {
		return updatedAt;
	}

	public Long version() {
		return version;
	}

	/** JPA only compares against the version it loaded, so a client's stale version has to be checked here. */
	public void requireVersion(Long expected) {
		if (expected == null || !expected.equals(version)) {
			throw new StaleVersionException();
		}
	}

}
