package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/** A lab value as recorded. Unit and date stay null when the source lacks them, and are never guessed. */
@Entity
@Table(name = "lab_result")
class LabResult extends HealthRecord {

	@Column(name = "test_name", nullable = false)
	private String testName;

	@Column(name = "test_key", nullable = false)
	private String testKey;

	@Column(name = "value_numeric")
	private BigDecimal valueNumeric;

	@Column(name = "value_text")
	private String valueText;

	private String unit;

	@Column(name = "result_on")
	private LocalDate resultOn;

	@Column(name = "reference_range_as_printed")
	private String referenceRangeAsPrinted;

	protected LabResult() {
	}

	LabResult(UUID profileId, Provenance provenance) {
		super(profileId, provenance);
	}

	/** Groups values for trends: the name trimmed, lowercased, and with inner spaces collapsed. */
	static String keyOf(String testName) {
		return testName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
	}

	void update(String testName, BigDecimal valueNumeric, String valueText, String unit, LocalDate resultOn,
			String referenceRangeAsPrinted) {
		this.testName = testName;
		this.testKey = keyOf(testName);
		this.valueNumeric = valueNumeric;
		this.valueText = valueText;
		this.unit = unit;
		this.resultOn = resultOn;
		this.referenceRangeAsPrinted = referenceRangeAsPrinted;
	}

	String testName() {
		return testName;
	}

	String testKey() {
		return testKey;
	}

	BigDecimal valueNumeric() {
		return valueNumeric;
	}

	String valueText() {
		return valueText;
	}

	String unit() {
		return unit;
	}

	LocalDate resultOn() {
		return resultOn;
	}

	String referenceRangeAsPrinted() {
		return referenceRangeAsPrinted;
	}

}
