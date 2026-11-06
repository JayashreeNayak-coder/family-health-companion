package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.FoodLogEntryDtos.Request;
import com.familyhealth.records.FoodLogEntryDtos.Response;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class FoodLogEntryService extends RecordService<FoodLogEntry, Request, Response> {

	FoodLogEntryService(FoodLogEntryRepository repository, ProfileAccess access) {
		super(repository, access);
	}

	@Override
	public RecordType recordType() {
		return RecordType.FOOD_LOG;
	}

	@Override
	protected FoodLogEntry newRecord(UUID profileId, UUID accountId, Request request) {
		FoodLogEntry record = new FoodLogEntry(profileId);
		apply(record, request);
		return record;
	}

	@Override
	protected void apply(FoodLogEntry record, Request request) {
		record.update(request.description().trim(), request.mealType(), request.occurredOn(), request.occurredTime());
	}

	@Override
	protected Response toResponse(FoodLogEntry record) {
		return new Response(record.id(), record.profileId(), record.description(), record.mealType(),
				record.occurredOn(), record.occurredTime(), record.version());
	}

}
