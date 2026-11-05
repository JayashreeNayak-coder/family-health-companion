package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.ConditionDtos.Request;
import com.familyhealth.records.ConditionDtos.Response;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class ConditionService extends RecordService<Condition, Request, Response> {

	ConditionService(ConditionRepository repository, ProfileAccess access) {
		super(repository, access);
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
