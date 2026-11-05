package com.familyhealth.medication;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Owned through its medication. A null {@code daysOfWeek} means daily; 1 is Monday and 7 is Sunday. */
@Entity
@Table(name = "medication_schedule")
class MedicationSchedule extends BaseEntity {

	@Column(name = "medication_id", nullable = false, updatable = false)
	private UUID medicationId;

	@Column(name = "time_of_day", nullable = false)
	private LocalTime timeOfDay;

	@JdbcTypeCode(SqlTypes.ARRAY)
	@Column(name = "days_of_week", columnDefinition = "smallint[]")
	private Short[] daysOfWeek;

	@Column(name = "time_zone", nullable = false)
	private String timeZone;

	@Column(name = "reminder_enabled", nullable = false)
	private boolean reminderEnabled;

	protected MedicationSchedule() {
	}

	MedicationSchedule(UUID medicationId, LocalTime timeOfDay, List<Integer> days, String timeZone,
			boolean reminderEnabled) {
		this.medicationId = medicationId;
		this.timeOfDay = timeOfDay;
		this.daysOfWeek = days == null ? null : days.stream().map(Integer::shortValue).toArray(Short[]::new);
		this.timeZone = timeZone;
		this.reminderEnabled = reminderEnabled;
	}

	boolean sameDoses(LocalTime otherTime, List<Integer> otherDays, String otherZone) {
		return timeOfDay.equals(otherTime) && timeZone.equals(otherZone) && sameDays(daysOfWeek(), otherDays);
	}

	private static boolean sameDays(List<Integer> first, List<Integer> second) {
		if (first == null || second == null) {
			return first == second;
		}
		return first.stream().sorted().toList().equals(second.stream().sorted().toList());
	}

	boolean appliesOn(LocalDate date) {
		return daysOfWeek == null || Arrays.asList(daysOfWeek).contains((short) date.getDayOfWeek().getValue());
	}

	/** The instant of this schedule's dose on a date, in the schedule's own time zone. */
	Instant doseOn(LocalDate date) {
		return ZonedDateTime.of(date, timeOfDay, ZoneId.of(timeZone)).toInstant();
	}

	LocalDate localDateOf(Instant instant) {
		return instant.atZone(ZoneId.of(timeZone)).toLocalDate();
	}

	void setReminderEnabled(boolean reminderEnabled) {
		this.reminderEnabled = reminderEnabled;
	}

	UUID medicationId() {
		return medicationId;
	}

	LocalTime timeOfDay() {
		return timeOfDay;
	}

	List<Integer> daysOfWeek() {
		return daysOfWeek == null ? null : Arrays.stream(daysOfWeek).map(Short::intValue).toList();
	}

	String timeZone() {
		return timeZone;
	}

	boolean reminderEnabled() {
		return reminderEnabled;
	}

}
