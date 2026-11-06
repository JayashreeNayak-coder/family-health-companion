package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.AllergyDtos.Request;
import com.familyhealth.records.AllergyDtos.Response;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AllergyService extends RecordService<Allergy, Request, Response> {

	AllergyService(AllergyRepository repository, ProfileAccess access) {
		super(repository, access);
	}

	@Override
	public RecordType recordType() {
		return RecordType.ALLERGY;
	}

	@Override
	protected Allergy newRecord(UUID profileId, UUID accountId, Request request) {
		Allergy record = new Allergy(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(Allergy record, Request request) {
		record.update(request.allergen().trim(), request.reactionAsRecorded(), request.severityAsRecorded(),
				request.notes());
	}

	@Override
	protected Response toResponse(Allergy record) {
		return new Response(record.id(), record.profileId(), record.allergen(), record.reactionAsRecorded(),
				record.severityAsRecorded(), record.notes(), record.sourceType(), record.sourceDocumentId(),
				record.sourceDocumentDeletedAt(), record.version());
	}

}
