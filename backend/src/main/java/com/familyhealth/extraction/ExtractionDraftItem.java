package com.familyhealth.extraction;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One proposed record. The payload is JSON in the record type's own request field names. */
@Entity
@Table(name = "extraction_draft_item")
class ExtractionDraftItem extends BaseEntity {

	enum Status {

		PENDING, APPROVED, REJECTED

	}

	@Column(name = "draft_id", nullable = false, updatable = false)
	private UUID draftId;

	@Column(nullable = false, updatable = false)
	private int position;

	@Column(name = "item_type", nullable = false, updatable = false)
	private String itemType;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Status status = Status.PENDING;

	@Column(name = "confirmed_record_id")
	private UUID confirmedRecordId;

	@Column(name = "decided_by")
	private UUID decidedBy;

	@Column(name = "decided_at")
	private Instant decidedAt;

	protected ExtractionDraftItem() {
	}

	ExtractionDraftItem(UUID draftId, int position, String itemType, String payload) {
		this.draftId = draftId;
		this.position = position;
		this.itemType = itemType;
		this.payload = payload;
	}

	void correct(String payload) {
		this.payload = payload;
	}

	void approve(UUID recordId, UUID accountId, Instant now) {
		status = Status.APPROVED;
		confirmedRecordId = recordId;
		decidedBy = accountId;
		decidedAt = now;
	}

	void reject(UUID accountId, Instant now) {
		status = Status.REJECTED;
		decidedBy = accountId;
		decidedAt = now;
	}

	UUID draftId() {
		return draftId;
	}

	int position() {
		return position;
	}

	String itemType() {
		return itemType;
	}

	String payload() {
		return payload;
	}

	Status status() {
		return status;
	}

	UUID confirmedRecordId() {
		return confirmedRecordId;
	}

}
