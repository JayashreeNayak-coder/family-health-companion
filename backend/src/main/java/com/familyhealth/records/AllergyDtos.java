package com.familyhealth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class AllergyDtos {

	private AllergyDtos() {
	}

	record Request(
			@NotBlank @Size(max = 200) String allergen,
			@Size(max = 500) String reactionAsRecorded,
			@Size(max = 100) String severityAsRecorded,
			@Size(max = 4000) String notes,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {
	}

	record Response(UUID id, UUID profileId, String allergen, String reactionAsRecorded, String severityAsRecorded,
			String notes, SourceType sourceType, UUID sourceDocumentId, Instant sourceDocumentDeletedAt,
			Long version) {
	}

}
