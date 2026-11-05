package com.familyhealth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class DietaryRestrictionDtos {

	private DietaryRestrictionDtos() {
	}

	record Request(
			@NotNull DietaryKind kind,
			@NotBlank @Size(max = 500) String description,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {
	}

	record Response(UUID id, UUID profileId, DietaryKind kind, String description, SourceType sourceType,
			UUID sourceDocumentId, Instant sourceDocumentDeletedAt, Long version) {
	}

}
