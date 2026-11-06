package com.familyhealth.records;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

final class FoodLogEntryDtos {

	private FoodLogEntryDtos() {
	}

	record Request(
			@NotBlank @Size(max = 1000) String description,
			MealType mealType,
			LocalDate occurredOn,
			LocalTime occurredTime,
			Long version) implements VersionedRequest {

		@AssertTrue(message = "a time needs a date")
		boolean isOccurredTimeValid() {
			return occurredTime == null || occurredOn != null;
		}

	}

	record Response(UUID id, UUID profileId, String description, MealType mealType, LocalDate occurredOn,
			@JsonFormat(pattern = "HH:mm") LocalTime occurredTime, Long version) {
	}

}
