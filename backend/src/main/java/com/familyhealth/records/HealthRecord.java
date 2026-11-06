package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

/** A confirmed health record, carrying where it came from (docs/04 section 1.2). */
@MappedSuperclass
public abstract class HealthRecord extends ProfileRecord {

	@Enumerated(EnumType.STRING)
	@Column(name = "source_type", nullable = false)
	private SourceType sourceType;

	@Column(name = "source_document_id")
	private UUID sourceDocumentId;

	@Column(name = "source_document_deleted_at")
	private Instant sourceDocumentDeletedAt;

	@Column(name = "extraction_draft_item_id")
	private UUID extractionDraftItemId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected HealthRecord() {
	}

	protected HealthRecord(UUID profileId, Provenance provenance) {
		super(profileId);
		this.sourceType = provenance.sourceType();
		this.sourceDocumentId = provenance.sourceDocumentId();
		this.createdBy = provenance.createdBy();
	}

	/** Marks a record confirmed from an AI draft item. Set once, when the record is created. */
	void linkDraftItem(UUID draftItemId) {
		this.extractionDraftItemId = draftItemId;
	}

	public UUID extractionDraftItemId() {
		return extractionDraftItemId;
	}

	public SourceType sourceType() {
		return sourceType;
	}

	public UUID sourceDocumentId() {
		return sourceDocumentId;
	}

	public Instant sourceDocumentDeletedAt() {
		return sourceDocumentDeletedAt;
	}

	public UUID createdBy() {
		return createdBy;
	}

}
