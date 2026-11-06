package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.ConditionDtos.Request;
import com.familyhealth.records.ConditionDtos.Response;
import com.familyhealth.records.timeline.TimelineEntry;
import com.familyhealth.records.timeline.TimelineSource;
import com.familyhealth.records.timeline.TimelineType;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ConditionService extends RecordService<Condition, Request, Response> implements TimelineSource {

	private final ConditionRepository repository;

	ConditionService(ConditionRepository repository, ProfileAccess access) {
		super(repository, access);
		this.repository = repository;
	}

	@Override
	public List<TimelineEntry> timelineEntries(UUID profileId) {
		return repository.findByProfileId(profileId)
			.stream()
			.map(condition -> new TimelineEntry(TimelineType.CONDITION, condition.id(), condition.onsetOn(), null,
					condition.name(), null, condition.sourceType(), condition.sourceDocumentId(),
					condition.sourceDocumentDeletedAt()))
			.toList();
	}

	@Override
	public RecordType recordType() {
		return RecordType.CONDITION;
	}

	@Override
	protected Condition newRecord(UUID profileId, UUID accountId, Request request) {
		Condition record = new Condition(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(Condition record, Request request) {
		record.update(request.name().trim(), request.status(), request.onsetOn(), request.notes());
	}

	@Override
	protected Response toResponse(Condition record) {
		return new Response(record.id(), record.profileId(), record.name(), record.status(), record.onsetOn(),
				record.notes(), record.sourceType(), record.sourceDocumentId(), record.sourceDocumentDeletedAt(),
				record.version());
	}

}
