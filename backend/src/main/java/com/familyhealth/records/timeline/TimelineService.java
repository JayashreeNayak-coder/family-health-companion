package com.familyhealth.records.timeline;

import com.familyhealth.common.ApiException;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class TimelineService {

	/** Newest first. An entry with no time sorts after the timed entries of its day. */
	private static final Comparator<TimelineEntry> NEWEST_FIRST = Comparator
		.comparing(TimelineEntry::date, Comparator.<LocalDate>reverseOrder())
		.thenComparing(TimelineEntry::time, Comparator.nullsLast(Comparator.<LocalTime>reverseOrder()))
		.thenComparing(TimelineEntry::title)
		.thenComparing(TimelineEntry::id);

	record Timeline(List<TimelineEntry> entries, List<TimelineEntry> dateUnknown) {
	}

	private final List<TimelineSource> sources;

	private final ProfileAccess access;

	TimelineService(List<TimelineSource> sources, ProfileAccess access) {
		this.sources = sources;
		this.access = access;
	}

	/**
	 * Undated records can't be placed inside or outside a date range, so a range doesn't hide them.
	 * The type filter still applies. Null filters mean no limit.
	 */
	@Transactional(readOnly = true)
	public Timeline get(UUID accountId, UUID profileId, LocalDate from, LocalDate to, Set<TimelineType> types) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		if (from != null && to != null && from.isAfter(to)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "The start date is after the end date");
		}
		List<TimelineEntry> all = sources.stream()
			.flatMap(source -> source.timelineEntries(profileId).stream())
			.filter(entry -> types == null || types.isEmpty() || types.contains(entry.type()))
			.toList();
		List<TimelineEntry> dated = all.stream()
			.filter(entry -> entry.date() != null)
			.filter(entry -> from == null || !entry.date().isBefore(from))
			.filter(entry -> to == null || !entry.date().isAfter(to))
			.sorted(NEWEST_FIRST)
			.toList();
		List<TimelineEntry> undated = all.stream()
			.filter(entry -> entry.date() == null)
			.sorted(Comparator.comparing(TimelineEntry::title).thenComparing(TimelineEntry::id))
			.toList();
		return new Timeline(dated, undated);
	}

}
