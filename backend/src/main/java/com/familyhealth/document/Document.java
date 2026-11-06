package com.familyhealth.document;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

/** Metadata for an uploaded original. The file itself is in private storage and is never altered. */
@Entity
@Table(name = "document")
class Document extends BaseEntity {

	@Column(name = "profile_id", nullable = false, updatable = false)
	private UUID profileId;

	@Column(name = "uploaded_by", nullable = false, updatable = false)
	private UUID uploadedBy;

	@Column(name = "original_filename", nullable = false, updatable = false)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, updatable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(nullable = false, updatable = false)
	private String sha256;

	@Column(name = "storage_key", nullable = false, updatable = false)
	private String storageKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DocumentCategory category;

	@Column(name = "document_on")
	private LocalDate documentOn;

	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "extraction_status", nullable = false)
	private ExtractionStatus extractionStatus = ExtractionStatus.NOT_REQUESTED;

	protected Document() {
	}

	Document(UUID profileId, UUID uploadedBy, String originalFilename, String contentType, long sizeBytes,
			String sha256, String storageKey) {
		this.profileId = profileId;
		this.uploadedBy = uploadedBy;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.sizeBytes = sizeBytes;
		this.sha256 = sha256;
		this.storageKey = storageKey;
	}

	void describe(DocumentCategory category, LocalDate documentOn, String description) {
		this.category = category;
		this.documentOn = documentOn;
		this.description = description;
	}

	void extractionStatus(ExtractionStatus status) {
		this.extractionStatus = status;
	}

	UUID profileId() {
		return profileId;
	}

	UUID uploadedBy() {
		return uploadedBy;
	}

	String originalFilename() {
		return originalFilename;
	}

	String contentType() {
		return contentType;
	}

	long sizeBytes() {
		return sizeBytes;
	}

	String sha256() {
		return sha256;
	}

	String storageKey() {
		return storageKey;
	}

	DocumentCategory category() {
		return category;
	}

	LocalDate documentOn() {
		return documentOn;
	}

	String description() {
		return description;
	}

	ExtractionStatus extractionStatus() {
		return extractionStatus;
	}

}
