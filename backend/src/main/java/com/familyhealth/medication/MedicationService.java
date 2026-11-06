package com.familyhealth.medication;

import com.familyhealth.common.ApiException;
import com.familyhealth.medication.MedicationDtos.AdherenceRequest;
import com.familyhealth.medication.MedicationDtos.AdherenceResponse;
import com.familyhealth.medication.MedicationDtos.DueDose;
import com.familyhealth.medication.MedicationDtos.Request;
import com.familyhealth.medication.MedicationDtos.Response;
import com.familyhealth.medication.MedicationDtos.ScheduleItem;
import com.familyhealth.medication.MedicationDtos.ScheduleResponse;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.RecordService;
import com.familyhealth.records.RecordType;
import com.familyhealth.records.timeline.TimelineEntry;
import com.familyhealth.records.timeline.TimelineSource;
import com.familyhealth.records.timeline.TimelineType;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Tracks what the user records. It never suggests a dose or a change to a medication. */
@Service
class MedicationService extends RecordService<Medication, Request, Response> implements TimelineSource {

	private final MedicationRepository medications;

	private final MedicationScheduleRepository schedules;

	private final AdherenceEventRepository adherence;

	private final ProfileAccess access;

	private final Clock clock;

	MedicationService(MedicationRepository medications, MedicationScheduleRepository schedules,
			AdherenceEventRepository adherence, ProfileAccess access, Clock clock) {
		super(medications, access);
		this.medications = medications;
		this.schedules = schedules;
		this.adherence = adherence;
		this.access = access;
		this.clock = clock;
	}

	@Override
	public RecordType recordType() {
		return RecordType.MEDICATION;
	}

	@Override
	protected Medication newRecord(UUID profileId, UUID accountId, Request request) {
		Medication record = new Medication(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(Medication record, Request request) {
		record.update(request.name().trim(), request.dosageAsRecorded(), request.instructionsAsRecorded(),
				request.prescribedBy(), request.startOn(), request.endOn(), request.status());
	}

	@Override
	protected Response toResponse(Medication record) {
		return new Response(record.id(), record.profileId(), record.name(), record.dosageAsRecorded(),
				record.instructionsAsRecorded(), record.prescribedBy(), record.startOn(), record.endOn(),
				record.status(), record.sourceType(), record.sourceDocumentId(), record.sourceDocumentDeletedAt(),
				record.version());
	}

	@Override
	public List<TimelineEntry> timelineEntries(UUID profileId) {
		return medications.findByProfileId(profileId)
			.stream()
			.map(medication -> new TimelineEntry(TimelineType.MEDICATION, medication.id(), medication.startOn(), null,
					medication.name(), medication.dosageAsRecorded(), medication.sourceType(),
					medication.sourceDocumentId(), medication.sourceDocumentDeletedAt()))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<ScheduleResponse> listSchedules(UUID accountId, UUID profileId, UUID medicationId) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		load(profileId, medicationId);
		return schedules.findByMedicationIdOrderByTimeOfDayAsc(medicationId)
			.stream()
			.map(MedicationService::toScheduleResponse)
			.toList();
	}

	/**
	 * Replaces the medication's schedules. A schedule that keeps the same doses keeps its row, so
	 * its recorded doses stay attached and can't be logged a second time.
	 */
	@Transactional
	public List<ScheduleResponse> replaceSchedules(UUID accountId, UUID profileId, UUID medicationId,
			List<ScheduleItem> items) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		load(profileId, medicationId);
		items.forEach(item -> requireValidZone(item.timeZone()));
		List<MedicationSchedule> remaining = new ArrayList<>(
				schedules.findByMedicationIdOrderByTimeOfDayAsc(medicationId));
		List<MedicationSchedule> kept = new ArrayList<>();
		for (ScheduleItem item : items) {
			MedicationSchedule match = remaining.stream()
				.filter(existing -> existing.sameDoses(item.timeOfDay(), item.daysOfWeek(), item.timeZone()))
				.findFirst()
				.orElse(null);
			if (match != null) {
				remaining.remove(match);
				match.setReminderEnabled(item.reminderEnabled());
				kept.add(match);
			}
			else {
				kept.add(new MedicationSchedule(medicationId, item.timeOfDay(), item.daysOfWeek(), item.timeZone(),
						item.reminderEnabled()));
			}
		}
		schedules.deleteAll(remaining);
		return schedules.saveAll(kept)
			.stream()
			.sorted(Comparator.comparing(MedicationSchedule::timeOfDay))
			.map(MedicationService::toScheduleResponse)
			.toList();
	}

	@Transactional
	public AdherenceResponse recordAdherence(UUID accountId, UUID profileId, UUID medicationId,
			AdherenceRequest request) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		Medication medication = load(profileId, medicationId);
		MedicationSchedule schedule = schedules.findByIdAndMedicationId(request.scheduleId(), medicationId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
		Instant dose = request.scheduledFor();
		LocalDate date = schedule.localDateOf(dose);
		if (!schedule.appliesOn(date) || !schedule.doseOn(date).equals(dose) || !medication.covers(date)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_SCHEDULED_DOSE",
					"This time doesn't match a scheduled dose");
		}
		if (dose.isAfter(clock.instant()) && date.isAfter(LocalDate.ofInstant(clock.instant(), zoneOf(schedule)))) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "DOSE_IN_FUTURE", "A future day's dose can't be recorded yet");
		}
		if (adherence.existsByScheduleIdAndScheduledFor(schedule.id(), dose)) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_RECORDED", "This dose has already been recorded");
		}
		AdherenceEvent event = adherence.saveAndFlush(new AdherenceEvent(medicationId, schedule.id(), dose,
				request.status(), clock.instant(), accountId));
		return new AdherenceResponse(event.id(), medicationId, event.scheduleId(), event.scheduledFor(),
				event.status(), event.recordedAt());
	}

	/** Doses of active medications on a date, each in its schedule's own time zone. */
	@Transactional(readOnly = true)
	public List<DueDose> due(UUID accountId, UUID profileId, LocalDate date) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		Map<UUID, Medication> active = medications.findByProfileId(profileId)
			.stream()
			.filter(Medication::isActive)
			.filter(medication -> medication.covers(date))
			.collect(Collectors.toMap(Medication::id, Function.identity()));
		List<MedicationSchedule> applicable = active.isEmpty() ? List.of()
				: schedules.findByMedicationIdIn(active.keySet())
					.stream()
					.filter(schedule -> schedule.appliesOn(date))
					.toList();
		if (applicable.isEmpty()) {
			return List.of();
		}
		Map<UUID, Instant> doses = applicable.stream()
			.collect(Collectors.toMap(MedicationSchedule::id, schedule -> schedule.doseOn(date)));
		Map<UUID, AdherenceStatus> recorded = adherence
			.findByScheduleIdInAndScheduledForIn(doses.keySet(), doses.values())
			.stream()
			.filter(event -> event.scheduledFor().equals(doses.get(event.scheduleId())))
			.collect(Collectors.toMap(AdherenceEvent::scheduleId, AdherenceEvent::status));
		return applicable.stream().map(schedule -> {
			Medication medication = active.get(schedule.medicationId());
			return new DueDose(medication.id(), medication.name(), medication.dosageAsRecorded(), schedule.id(),
					doses.get(schedule.id()), schedule.timeOfDay(), schedule.timeZone(), schedule.reminderEnabled(),
					recorded.get(schedule.id()));
		}).sorted(Comparator.comparing(DueDose::scheduledFor).thenComparing(DueDose::medicationName)).toList();
	}

	private static ZoneId zoneOf(MedicationSchedule schedule) {
		return ZoneId.of(schedule.timeZone());
	}

	private static void requireValidZone(String zone) {
		try {
			ZoneId.of(zone);
		}
		catch (DateTimeException ex) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TIME_ZONE", "Unknown time zone");
		}
	}

	private static ScheduleResponse toScheduleResponse(MedicationSchedule schedule) {
		return new ScheduleResponse(schedule.id(), schedule.timeOfDay(), schedule.daysOfWeek(), schedule.timeZone(),
				schedule.reminderEnabled());
	}

}
