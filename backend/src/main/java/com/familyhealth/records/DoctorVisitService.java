package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.DoctorVisitDtos.Request;
import com.familyhealth.records.DoctorVisitDtos.Response;
import com.familyhealth.records.timeline.TimelineEntry;
import com.familyhealth.records.timeline.TimelineSource;
import com.familyhealth.records.timeline.TimelineType;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
class DoctorVisitService extends RecordService<DoctorVisit, Request, Response> implements TimelineSource {

	private final DoctorVisitRepository repository;

	DoctorVisitService(DoctorVisitRepository repository, ProfileAccess access) {
		super(repository, access);
		this.repository = repository;
	}

	@Override
	public RecordType recordType() {
		return RecordType.VISIT;
	}

	@Override
	protected DoctorVisit newRecord(UUID profileId, UUID accountId, Request request) {
		DoctorVisit record = new DoctorVisit(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(DoctorVisit record, Request request) {
		record.update(request.occurredOn(), request.occurredTime(), request.doctorName(), request.facility(),
				request.reason(), request.notes());
	}

	@Override
	protected Response toResponse(DoctorVisit record) {
		return new Response(record.id(), record.profileId(), record.occurredOn(), record.occurredTime(),
				record.doctorName(), record.facility(), record.reason(), record.notes(), record.sourceType(),
				record.sourceDocumentId(), record.sourceDocumentDeletedAt(), record.version());
	}

	@Override
	public List<TimelineEntry> timelineEntries(UUID profileId) {
		return repository.findByProfileId(profileId).stream().map(visit -> {
			String title = visit.reason() != null ? visit.reason() : "Doctor visit";
			String detail = Stream.of(visit.doctorName(), visit.facility())
				.filter(part -> part != null && !part.isBlank())
				.collect(Collectors.joining(" · "));
			return new TimelineEntry(TimelineType.VISIT, visit.id(), visit.occurredOn(), visit.occurredTime(), title,
					detail.isEmpty() ? null : detail, visit.sourceType(), visit.sourceDocumentId(),
					visit.sourceDocumentDeletedAt());
		}).toList();
	}

}
