package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.EmergencyContactDtos.Request;
import com.familyhealth.records.EmergencyContactDtos.Response;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class EmergencyContactService extends RecordService<EmergencyContact, Request, Response> {

	EmergencyContactService(EmergencyContactRepository repository, ProfileAccess access) {
		super(repository, access);
	}

	@Override
	protected EmergencyContact newRecord(UUID profileId, UUID accountId, Request request) {
		EmergencyContact record = new EmergencyContact(profileId);
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(EmergencyContact record, Request request) {
		record.update(request.name().trim(), request.relationship(), request.phone().trim());
	}

	@Override
	protected Response toResponse(EmergencyContact record) {
		return new Response(record.id(), record.profileId(), record.name(), record.relationship(), record.phone(),
				record.version());
	}

}
