package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.SymptomEntryDtos.Request;
import com.familyhealth.records.SymptomEntryDtos.Response;
import com.familyhealth.records.timeline.TimelineEntry;
import com.familyhealth.records.timeline.TimelineSource;
import com.familyhealth.records.timeline.TimelineType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SymptomEntryService extends RecordService<SymptomEntry, Request, Response> implements TimelineSource {

	private final SymptomEntryRepository repository;

	SymptomEntryService(SymptomEntryRepository repository, ProfileAccess access) {
		super(repository, access);
		this.repository = repository;
	}

	@Override
	public RecordType recordType() {
		return RecordType.SYMPTOM;
	}

	@Override
	protected SymptomEntry newRecord(UUID profileId, UUID accountId, Request request) {
		SymptomEntry record = new SymptomEntry(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(SymptomEntry record, Request request) {
		record.update(request.description().trim(), request.severityAsReported(), request.occurredOn(),
				request.occurredTime(), request.notes());
	}

	@Override
	protected Response toResponse(SymptomEntry record) {
		return new Response(record.id(), record.profileId(), record.description(), record.severityAsReported(),
				record.occurredOn(), record.occurredTime(), record.notes(), record.sourceType(),
				record.sourceDocumentId(), record.sourceDocumentDeletedAt(), record.version());
	}

	@Override
	public List<TimelineEntry> timelineEntries(UUID profileId) {
		return repository.findByProfileId(profileId)
			.stream()
			.map(symptom -> new TimelineEntry(TimelineType.SYMPTOM, symptom.id(), symptom.occurredOn(),
					symptom.occurredTime(), symptom.description(), symptom.severityAsReported(), symptom.sourceType(),
					symptom.sourceDocumentId(), symptom.sourceDocumentDeletedAt()))
			.toList();
	}

}
