package com.familyhealth.records;

import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.LabResultDtos.Request;
import com.familyhealth.records.LabResultDtos.Response;
import com.familyhealth.records.LabResultDtos.Trend;
import com.familyhealth.records.LabResultDtos.TrendPoint;
import com.familyhealth.records.LabResultDtos.TrendSeries;
import com.familyhealth.records.timeline.TimelineEntry;
import com.familyhealth.records.timeline.TimelineSource;
import com.familyhealth.records.timeline.TimelineType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores and lists values as recorded. Nothing here labels a value normal or abnormal. */
@Service
class LabResultService extends RecordService<LabResult, Request, Response> implements TimelineSource {

	private static final Comparator<LabResult> OLDEST_FIRST = Comparator.comparing(LabResult::resultOn)
		.thenComparing(LabResult::id);

	private final LabResultRepository repository;

	private final ProfileAccess access;

	LabResultService(LabResultRepository repository, ProfileAccess access) {
		super(repository, access);
		this.repository = repository;
		this.access = access;
	}

	@Override
	public RecordType recordType() {
		return RecordType.LAB_RESULT;
	}

	@Override
	protected LabResult newRecord(UUID profileId, UUID accountId, Request request) {
		LabResult record = new LabResult(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(LabResult record, Request request) {
		record.update(request.testName().trim(), request.valueNumeric(), blankToNull(request.valueText()),
				blankToNull(request.unit()), request.resultOn(), blankToNull(request.referenceRangeAsPrinted()));
	}

	@Override
	protected Response toResponse(LabResult record) {
		return new Response(record.id(), record.profileId(), record.testName(), record.testKey(),
				record.valueNumeric(), record.valueText(), record.unit(), record.resultOn(),
				record.referenceRangeAsPrinted(), record.sourceType(), record.sourceDocumentId(),
				record.sourceDocumentDeletedAt(), record.version());
	}

	/** Each test and unit with at least one comparable value. A different unit is a different series. */
	@Transactional(readOnly = true)
	public List<TrendSeries> series(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		Map<List<String>, List<LabResult>> groups = new LinkedHashMap<>();
		repository.findByProfileId(profileId)
			.stream()
			.filter(LabResultService::comparable)
			.sorted(Comparator.comparing(LabResult::testKey)
				.thenComparing(LabResult::unit, Comparator.nullsFirst(Comparator.naturalOrder())))
			.forEach(result -> groups
				.computeIfAbsent(Arrays.asList(result.testKey(), result.unit()), key -> new ArrayList<>())
				.add(result));
		return groups.values().stream().map(results -> {
			LabResult latest = results.stream().max(OLDEST_FIRST).orElseThrow();
			return new TrendSeries(latest.testKey(), latest.testName(), latest.unit(), results.size());
		}).toList();
	}

	/** A missing unit is its own series: values without a unit are only compared with each other. */
	@Transactional(readOnly = true)
	public Trend trend(UUID accountId, UUID profileId, String testKey, String unit) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		String key = LabResult.keyOf(testKey);
		String wantedUnit = blankToNull(unit);
		List<LabResult> all = repository.findByProfileIdAndTestKey(profileId, key);
		List<TrendPoint> points = all.stream()
			.filter(LabResultService::comparable)
			.filter(result -> Objects.equals(result.unit(), wantedUnit))
			.sorted(OLDEST_FIRST)
			.map(result -> new TrendPoint(result.id(), result.resultOn(), result.valueNumeric(), result.sourceType()))
			.toList();
		return new Trend(key, wantedUnit, points, all.size() - points.size());
	}

	@Override
	public List<TimelineEntry> timelineEntries(UUID profileId) {
		return repository.findByProfileId(profileId)
			.stream()
			.map(result -> new TimelineEntry(TimelineType.LAB_RESULT, result.id(), result.resultOn(), null,
					result.testName(), valueLabel(result), result.sourceType(), result.sourceDocumentId(),
					result.sourceDocumentDeletedAt()))
			.toList();
	}

	private static boolean comparable(LabResult result) {
		return result.valueNumeric() != null && result.resultOn() != null;
	}

	private static String valueLabel(LabResult result) {
		String value = result.valueNumeric() != null ? result.valueNumeric().toPlainString() : result.valueText();
		return result.unit() != null ? value + " " + result.unit() : value;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

}
