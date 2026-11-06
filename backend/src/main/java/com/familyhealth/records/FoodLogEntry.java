package com.familyhealth.records;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** What was eaten, as the user describes it. Not a health record, so it carries no provenance. */
@Entity
@Table(name = "food_log_entry")
class FoodLogEntry extends ProfileRecord {

	@Column(nullable = false)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "meal_type")
	private MealType mealType;

	@Column(name = "occurred_on")
	private LocalDate occurredOn;

	@Column(name = "occurred_time")
	private LocalTime occurredTime;

	protected FoodLogEntry() {
	}

	FoodLogEntry(UUID profileId) {
		super(profileId);
	}

	void update(String description, MealType mealType, LocalDate occurredOn, LocalTime occurredTime) {
		this.description = description;
		this.mealType = mealType;
		this.occurredOn = occurredOn;
		this.occurredTime = occurredTime;
	}

	String description() {
		return description;
	}

	MealType mealType() {
		return mealType;
	}

	LocalDate occurredOn() {
		return occurredOn;
	}

	LocalTime occurredTime() {
		return occurredTime;
	}

}
