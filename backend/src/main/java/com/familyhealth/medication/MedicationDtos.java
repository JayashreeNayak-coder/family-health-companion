package com.familyhealth.medication;

import com.familyhealth.records.HealthRecordRequest;
import com.familyhealth.records.SourceType;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

final class MedicationDtos {

	private MedicationDtos() {
	}

	record Request(
			@NotBlank @Size(max = 200) String name,
			@Size(max = 200) String dosageAsRecorded,
			@Size(max = 1000) String instructionsAsRecorded,
			@Size(max = 200) String prescribedBy,
			LocalDate startOn,
			LocalDate endOn,
			MedicationStatus status,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {

		@AssertTrue(message = "the end date can't be before the start date")
		boolean isEndOnValid() {
			return startOn == null || endOn == null || !endOn.isBefore(startOn);
		}

	}

	record Response(UUID id, UUID profileId, String name, String dosageAsRecorded, String instructionsAsRecorded,
			String prescribedBy, LocalDate startOn, LocalDate endOn, MedicationStatus status, SourceType sourceType,
			UUID sourceDocumentId, Instant sourceDocumentDeletedAt, Long version) {
	}

	/** {@code daysOfWeek} is null for daily; otherwise 1 (Monday) to 7 (Sunday). */
	record ScheduleItem(
			@NotNull LocalTime timeOfDay,
			@Size(min = 1, max = 7) List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
			@NotBlank @Size(max = 64) String timeZone,
			boolean reminderEnabled) {
	}

	record SchedulesRequest(@NotNull @Size(max = 12) List<@Valid @NotNull ScheduleItem> schedules) {
	}

	record ScheduleResponse(UUID id, @JsonFormat(pattern = "HH:mm") LocalTime timeOfDay, List<Integer> daysOfWeek,
			String timeZone, boolean reminderEnabled) {
	}

	record AdherenceRequest(@NotNull UUID scheduleId, @NotNull Instant scheduledFor, @NotNull AdherenceStatus status) {
	}

	record AdherenceResponse(UUID id, UUID medicationId, UUID scheduleId, Instant scheduledFor, AdherenceStatus status,
			Instant recordedAt) {
	}

	/** A dose on the in-app reminder list. {@code status} is null until it is recorded. */
	record DueDose(UUID medicationId, String medicationName, String dosageAsRecorded, UUID scheduleId,
			Instant scheduledFor, @JsonFormat(pattern = "HH:mm") LocalTime timeOfDay, String timeZone,
			boolean reminderEnabled, AdherenceStatus status) {
	}

}
