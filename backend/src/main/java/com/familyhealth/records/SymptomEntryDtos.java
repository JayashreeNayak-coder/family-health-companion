package com.familyhealth.records;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

final class SymptomEntryDtos {

	private SymptomEntryDtos() {
	}

	record Request(
			@NotBlank @Size(max = 500) String description,
			@Size(max = 100) String severityAsReported,
			LocalDate occurredOn,
			LocalTime occurredTime,
			@Size(max = 4000) String notes,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {

		@AssertTrue(message = "a time needs a date")
		boolean isOccurredTimeValid() {
			return occurredTime == null || occurredOn != null;
		}

	}

	record Response(UUID id, UUID profileId, String description, String severityAsReported, LocalDate occurredOn,
			@JsonFormat(pattern = "HH:mm") LocalTime occurredTime, String notes, SourceType sourceType,
			UUID sourceDocumentId, Instant sourceDocumentDeletedAt, Long version) {
	}

}
