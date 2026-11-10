package com.familyhealth.summary;

import com.familyhealth.records.SourceType;
import jakarta.validation.constraints.AssertTrue;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class SummaryDtos {

	static final String AI_GENERATED = "AI_GENERATED";

	static final String DISCLAIMER = "AI-generated summary of recorded information. It is not medical advice, a "
			+ "diagnosis, or a medical certificate. Talk to a qualified healthcare professional about health questions.";

	private SummaryDtos() {
	}

	/** Both dates are optional. There is no free-text field, so nothing can ask for a diagnosis. */
	record Request(LocalDate periodFrom, LocalDate periodTo) {

		@AssertTrue(message = "the start date must not be after the end date")
		boolean isPeriodValid() {
			return periodFrom == null || periodTo == null || !periodFrom.isAfter(periodTo);
		}

	}

	/**
	 * A cited record, as it was when the summary was made. {@code sourceType} keeps user-reported
	 * and documented records apart (docs/06 section 8.3).
	 */
	record Source(String ref, String type, UUID id, LocalDate date, String title, String detail,
			SourceType sourceType) {
	}

	record Point(String topic, String text, List<Source> sources) {
	}

	record Conflict(String text, List<Source> sources) {
	}

	record Response(UUID id, UUID profileId, String contentType, String promptVersion, String model,
			LocalDate periodFrom, LocalDate periodTo, Instant createdAt, List<Point> points, List<String> gaps,
			List<Conflict> conflicts, List<String> limitations, String disclaimer) {
	}

	/** What is stored in {@code content}: references, not copies, inside points and conflicts. */
	record StoredPoint(String topic, String text, List<String> refs) {
	}

	record StoredConflict(String text, List<String> refs) {
	}

	record StoredContent(List<StoredPoint> points, List<String> gaps, List<StoredConflict> conflicts,
			List<String> limitations, List<Source> sources) {
	}

	record SourceRef(String type, UUID id) {
	}

}
