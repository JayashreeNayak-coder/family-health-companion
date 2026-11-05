package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.DietaryRestrictionDtos.Request;
import com.familyhealth.records.DietaryRestrictionDtos.Response;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class DietaryRestrictionService extends RecordService<DietaryRestriction, Request, Response> {

	DietaryRestrictionService(DietaryRestrictionRepository repository, ProfileAccess access) {
		super(repository, access);
	}

	@Override
	protected DietaryRestriction newRecord(UUID profileId, UUID accountId, Request request) {
		DietaryRestriction record = new DietaryRestriction(profileId, request.provenance(accountId));
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(DietaryRestriction record, Request request) {
		record.update(request.kind(), request.description().trim());
	}

	@Override
	protected Response toResponse(DietaryRestriction record) {
		return new Response(record.id(), record.profileId(), record.kind(), record.description(), record.sourceType(),
				record.sourceDocumentId(), record.sourceDocumentDeletedAt(), record.version());
	}

}
