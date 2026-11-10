package com.familyhealth.emergencycard;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One approved line of a card. Replaced as a whole list, so it has no version of its own. */
@Entity
@Table(name = "emergency_card_item")
class EmergencyCardItem {

	@Id
	private UUID id = UUID.randomUUID();

	@Enumerated(EnumType.STRING)
	@Column(name = "item_type", nullable = false, updatable = false)
	private CardItemType itemType;

	@Column(name = "display_text", nullable = false, updatable = false)
	private String displayText;

	@Column(name = "display_order", nullable = false, updatable = false)
	private int displayOrder;

	@Column(name = "source_record_id", updatable = false)
	private UUID sourceRecordId;

	@Column(name = "source_updated_at", updatable = false)
	private Instant sourceUpdatedAt;

	protected EmergencyCardItem() {
	}

	EmergencyCardItem(CardItemType itemType, String displayText, int displayOrder, UUID sourceRecordId,
			Instant sourceUpdatedAt) {
		this.itemType = itemType;
		this.displayText = displayText;
		this.displayOrder = displayOrder;
		this.sourceRecordId = sourceRecordId;
		this.sourceUpdatedAt = sourceUpdatedAt;
	}

	UUID id() {
		return id;
	}

	CardItemType itemType() {
		return itemType;
	}

	String displayText() {
		return displayText;
	}

	int displayOrder() {
		return displayOrder;
	}

	UUID sourceRecordId() {
		return sourceRecordId;
	}

	Instant sourceUpdatedAt() {
		return sourceUpdatedAt;
	}

}
