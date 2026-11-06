package com.familyhealth.records;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class LabResultDtos {

	private LabResultDtos() {
	}

	record Request(
			@NotBlank @Size(max = 200) String testName,
			@Digits(integer = 12, fraction = 6) BigDecimal valueNumeric,
			@Size(max = 200) String valueText,
			@Size(max = 50) String unit,
			LocalDate resultOn,
			@Size(max = 200) String referenceRangeAsPrinted,
			SourceType sourceType,
			UUID sourceDocumentId,
			Long version) implements HealthRecordRequest {

		@AssertTrue(message = "a numeric or text value is required")
		boolean isValuePresent() {
			return valueNumeric != null || (valueText != null && !valueText.isBlank());
		}

	}

	record Response(UUID id, UUID profileId, String testName, String testKey, BigDecimal valueNumeric,
			String valueText, String unit, LocalDate resultOn, String referenceRangeAsPrinted, SourceType sourceType,
			UUID sourceDocumentId, Instant sourceDocumentDeletedAt, Long version) {
	}

	/** One test and unit that has values, so the UI can offer it as a trend. */
	record TrendSeries(String testKey, String testName, String unit, long count) {
	}

	record TrendPoint(UUID id, LocalDate resultOn, BigDecimal value, SourceType sourceType) {
	}

	/**
	 * Comparable values only: same test key and exact unit, with a number and a date. Excluded
	 * values are counted, so the user knows the trend isn't the whole history. No interpretation.
	 */
	record Trend(String testKey, String unit, List<TrendPoint> points, long excludedCount) {
	}

}
