package com.familyhealth.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

final class ConditionDtos {

	private ConditionDtos() {
	}

	record Request(
			@NotBlank @Size(max = 200) String name,
			ConditionStatus status,
			LocalDate onsetOn,
			@Size(max = 4000) String notes,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {
	}

	record Response(UUID id, UUID profileId, String name, ConditionStatus status, LocalDate onsetOn, String notes,
			SourceType sourceType, UUID sourceDocumentId, Instant sourceDocumentDeletedAt, Long version) {
	}

}
