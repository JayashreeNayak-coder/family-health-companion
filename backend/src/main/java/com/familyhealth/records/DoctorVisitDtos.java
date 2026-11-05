package com.familyhealth.records;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

final class DoctorVisitDtos {

	private DoctorVisitDtos() {
	}

	record Request(
			LocalDate occurredOn,
			LocalTime occurredTime,
			@Size(max = 200) String doctorName,
			@Size(max = 200) String facility,
			@Size(max = 500) String reason,
			@Size(max = 4000) String notes,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {

		@AssertTrue(message = "a time needs a date")
		boolean isOccurredTimeValid() {
			return occurredTime == null || occurredOn != null;
		}

	}

	record Response(UUID id, UUID profileId, LocalDate occurredOn, @JsonFormat(pattern = "HH:mm") LocalTime occurredTime,
			String doctorName, String facility, String reason, String notes, SourceType sourceType,
			UUID sourceDocumentId, Instant sourceDocumentDeletedAt, Long version) {
	}

}
