package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.FoodLogEntryDtos.FromRecipeRequest;
import com.familyhealth.records.FoodLogEntryDtos.Request;
import com.familyhealth.records.FoodLogEntryDtos.Response;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FoodLogEntryService extends RecordService<FoodLogEntry, Request, Response> {

	private final FoodLogEntryRepository entries;

	private final SavedRecipeRepository recipes;

	private final ProfileAccess access;

	FoodLogEntryService(FoodLogEntryRepository entries, SavedRecipeRepository recipes, ProfileAccess access) {
		super(entries, access);
		this.entries = entries;
		this.recipes = recipes;
		this.access = access;
	}

	/** The recipe must belong to the same profile, so one profile can't read another's by ID. */
	@Transactional
	public Response createFromRecipe(UUID accountId, UUID profileId, UUID recipeId, FromRecipeRequest request) {
		access.require(accountId, profileId, AccessLevel.EDIT);
		SavedRecipe recipe = recipes.findByIdAndProfileId(recipeId, profileId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found"));
		FoodLogEntry entry = new FoodLogEntry(profileId);
		entry.update(recipe.title(), request.mealType(), request.occurredOn(), request.occurredTime());
		entry.linkRecipe(recipe.id());
		return toResponse(entries.saveAndFlush(entry));
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
				record.occurredOn(), record.occurredTime(), record.savedRecipeId(), record.version());
	}

}
