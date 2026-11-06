package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

/** A document attached to a record as supporting evidence. The link never changes, so it has no update. */
@Entity
@Table(name = "record_attachment")
class RecordAttachment extends ProfileRecord {

	@Column(name = "document_id", nullable = false, updatable = false)
	private UUID documentId;

	@Enumerated(EnumType.STRING)
	@Column(name = "record_type", nullable = false, updatable = false)
	private RecordType recordType;

	@Column(name = "record_id", nullable = false, updatable = false)
	private UUID recordId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	protected RecordAttachment() {
	}

	RecordAttachment(UUID profileId, UUID documentId, RecordType recordType, UUID recordId, UUID createdBy) {
		super(profileId);
		this.documentId = documentId;
		this.recordType = recordType;
		this.recordId = recordId;
		this.createdBy = createdBy;
	}

	UUID documentId() {
		return documentId;
	}

	RecordType recordType() {
		return recordType;
	}

	UUID recordId() {
		return recordId;
	}

}
