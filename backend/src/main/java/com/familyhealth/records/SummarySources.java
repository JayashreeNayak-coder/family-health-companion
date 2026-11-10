package com.familyhealth.records;

import com.familyhealth.records.timeline.TimelineEntry;
import com.familyhealth.records.timeline.TimelineService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The records a summary may restate: the timeline's conditions, visits, symptoms, medications,
 * and lab results in the period, records with no date, and the profile's allergies. Notes,
 * documents, contacts, food log entries, and names are never included.
 */
@Service
public class SummarySources {

	/** A copy of one record as it was when the summary was made. */
	public record Snapshot(String type, UUID id, LocalDate date, String title, String detail, SourceType sourceType) {
	}

	/** {@code truncated} is true when older dated records were left out to stay under the limit. */
	public record Sources(List<Snapshot> records, boolean truncated) {
	}

	private final TimelineService timeline;

	private final AllergyRepository allergies;

	SummarySources(TimelineService timeline, AllergyRepository allergies) {
		this.timeline = timeline;
		this.allergies = allergies;
	}

	/** Checks VIEW through the timeline. Allergies come first, then dated records newest first. */
	@Transactional(readOnly = true)
	public Sources load(UUID accountId, UUID profileId, LocalDate from, LocalDate to, int max) {
		TimelineService.Timeline entries = timeline.get(accountId, profileId, from, to, null);
		List<Snapshot> records = new ArrayList<>();
		allergies.findByProfileId(profileId)
			.forEach(allergy -> records.add(new Snapshot("ALLERGY", allergy.id(), null, allergy.allergen(),
					allergy.reactionAsRecorded(), allergy.sourceType())));
		entries.dateUnknown().forEach(entry -> records.add(snapshot(entry)));
		boolean truncated = false;
		for (TimelineEntry entry : entries.entries()) {
			if (records.size() >= max) {
				truncated = true;
				break;
			}
			records.add(snapshot(entry));
		}
		return new Sources(List.copyOf(records.subList(0, Math.min(records.size(), max))),
				truncated || records.size() > max);
	}

	private static Snapshot snapshot(TimelineEntry entry) {
		return new Snapshot(entry.type().name(), entry.id(), entry.date(), entry.title(), entry.detail(),
				entry.sourceType());
	}

}
