package com.familyhealth.emergencycard;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

final class EmergencyCardDtos {

	static final int MAX_ITEMS = 30;

	static final int MAX_TEXT = 300;

	private EmergencyCardDtos() {
	}

	/** One line the user approved. {@code sourceRecordId} names the record it was copied from, if any. */
	record ItemRequest(@NotNull CardItemType type, @NotBlank @Size(max = MAX_TEXT) String text, UUID sourceRecordId) {
	}

	/** Replaces every item. {@code version} is null only when the profile has no card yet. */
	record UpdateRequest(Long version, @NotNull @Size(max = MAX_ITEMS) List<@Valid @NotNull ItemRequest> items) {
	}

	/** CURRENT, CHANGED (edited since it was copied), DELETED, or NONE (typed by the user). */
	enum SourceStatus {

		NONE, CURRENT, CHANGED, DELETED

	}

	record ItemResponse(CardItemType type, String text, UUID sourceRecordId, SourceStatus sourceStatus) {
	}

	/** The owner's view. Never contains the link: it is shown only once, when made. */
	record CardResponse(boolean enabled, Instant linkCreatedAt, List<ItemResponse> items, Long version) {
	}

	/** Returned only by enable and rotate. */
	record LinkResponse(String link, CardResponse card) {
	}

	/** What anyone with the link sees: the approved lines and nothing else, no IDs. */
	record PublicItem(CardItemType type, String text) {
	}

	record PublicCard(List<PublicItem> items) {
	}

}
