package com.familyhealth.summary;

import com.familyhealth.records.ProfileRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An AI-generated summary. Never a health record, and never changed after it is made. */
@Entity
@Table(name = "health_summary")
class HealthSummary extends ProfileRecord {

	@Column(name = "generated_by", nullable = false, updatable = false)
	private UUID generatedBy;

	@Column(name = "period_from", updatable = false)
	private LocalDate periodFrom;

	@Column(name = "period_to", updatable = false)
	private LocalDate periodTo;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, updatable = false)
	private String content;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "source_refs", nullable = false, updatable = false)
	private String sourceRefs;

	@Column(nullable = false, updatable = false)
	private String provider;

	@Column(nullable = false, updatable = false)
	private String model;

	@Column(name = "prompt_version", nullable = false, updatable = false)
	private String promptVersion;

	protected HealthSummary() {
	}

	HealthSummary(UUID profileId, UUID generatedBy, LocalDate periodFrom, LocalDate periodTo, String content,
			String sourceRefs, String provider, String model, String promptVersion) {
		super(profileId);
		this.generatedBy = generatedBy;
		this.periodFrom = periodFrom;
		this.periodTo = periodTo;
		this.content = content;
		this.sourceRefs = sourceRefs;
		this.provider = provider;
		this.model = model;
		this.promptVersion = promptVersion;
	}

	LocalDate periodFrom() {
		return periodFrom;
	}

	LocalDate periodTo() {
		return periodTo;
	}

	String content() {
		return content;
	}

	String model() {
		return model;
	}

	String promptVersion() {
		return promptVersion;
	}

}
