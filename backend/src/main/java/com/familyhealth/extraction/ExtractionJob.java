package com.familyhealth.extraction;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One request to read a document. The table also serves as the worker's queue. */
@Entity
@Table(name = "extraction_job")
class ExtractionJob extends BaseEntity {

	enum Status {

		QUEUED, PROCESSING, SUCCEEDED, FAILED

	}

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Column(name = "requested_by", nullable = false, updatable = false)
	private UUID requestedBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.QUEUED;

	@Column(nullable = false)
	private int attempts;

	@Column(nullable = false, updatable = false)
	private String provider;

	@Column(nullable = false, updatable = false)
	private String model;

	@Column(name = "prompt_version", nullable = false, updatable = false)
	private String promptVersion;

	@Column(name = "error_code")
	private String errorCode;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected ExtractionJob() {
	}

	ExtractionJob(UUID documentId, UUID requestedBy, String provider, String model, String promptVersion) {
		this.documentId = documentId;
		this.requestedBy = requestedBy;
		this.provider = provider;
		this.model = model;
		this.promptVersion = promptVersion;
	}

	void start(Instant now) {
		status = Status.PROCESSING;
		attempts++;
		startedAt = now;
	}

	void requeue(String errorCode) {
		status = Status.QUEUED;
		this.errorCode = errorCode;
	}

	void succeed(Instant now) {
		status = Status.SUCCEEDED;
		errorCode = null;
		finishedAt = now;
	}

	void fail(String errorCode, Instant now) {
		status = Status.FAILED;
		this.errorCode = errorCode;
		finishedAt = now;
	}

	UUID documentId() {
		return documentId;
	}

	UUID requestedBy() {
		return requestedBy;
	}

	Status status() {
		return status;
	}

	int attempts() {
		return attempts;
	}

	String errorCode() {
		return errorCode;
	}

	Instant startedAt() {
		return startedAt;
	}

	Instant finishedAt() {
		return finishedAt;
	}

	String model() {
		return model;
	}

}
