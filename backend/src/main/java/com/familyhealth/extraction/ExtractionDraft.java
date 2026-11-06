package com.familyhealth.extraction;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** AI output awaiting review. Nothing in it is a record until a person approves an item. */
@Entity
@Table(name = "extraction_draft")
class ExtractionDraft extends BaseEntity {

	enum Status {

		PENDING_REVIEW, COMPLETED, REJECTED

	}

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "job_id", nullable = false, updatable = false)
	private UUID jobId;

	@Column(name = "schema_version", nullable = false, updatable = false)
	private String schemaVersion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.PENDING_REVIEW;

	@Column(name = "reviewed_by")
	private UUID reviewedBy;

	@Column(name = "reviewed_at")
	private Instant reviewedAt;

	protected ExtractionDraft() {
	}

	ExtractionDraft(UUID documentId, UUID jobId, String schemaVersion) {
		this.documentId = documentId;
		this.jobId = jobId;
		this.schemaVersion = schemaVersion;
	}

	/** Completed if anything was approved, otherwise rejected. */
	void finish(boolean anyApproved, UUID accountId, Instant now) {
		status = anyApproved ? Status.COMPLETED : Status.REJECTED;
		reviewedBy = accountId;
		reviewedAt = now;
	}

	UUID documentId() {
		return documentId;
	}

	String schemaVersion() {
		return schemaVersion;
	}

	Status status() {
		return status;
	}

	UUID jobId() {
		return jobId;
	}

}
