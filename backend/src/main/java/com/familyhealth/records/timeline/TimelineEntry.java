package com.familyhealth.records.timeline;

import com.familyhealth.records.SourceType;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** One record on the timeline. A null date is never replaced with an entry or upload date. */
public record TimelineEntry(TimelineType type, UUID id, LocalDate date, @JsonFormat(pattern = "HH:mm") LocalTime time,
		String title, String detail, SourceType sourceType, UUID sourceDocumentId, Instant sourceDocumentDeletedAt) {
}
