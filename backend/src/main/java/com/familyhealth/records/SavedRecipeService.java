package com.familyhealth.records;

import com.familyhealth.profile.ProfileAccess;
import com.familyhealth.records.SavedRecipeDtos.Request;
import com.familyhealth.records.SavedRecipeDtos.Response;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
class SavedRecipeService extends RecordService<SavedRecipe, Request, Response> {

	private static final TypeReference<List<String>> LINES = new TypeReference<>() {
	};

	private final JsonMapper json;

	SavedRecipeService(SavedRecipeRepository repository, ProfileAccess access, JsonMapper json) {
		super(repository, access);
		this.json = json;
	}

	@Override
	protected SavedRecipe newRecord(UUID profileId, UUID accountId, Request request) {
		SavedRecipe recipe = new SavedRecipe(profileId, accountId, request.mode(), request.promptVersion());
		apply(recipe, request);
		return recipe;
	}

	@Override
	protected void apply(SavedRecipe recipe, Request request) {
		recipe.update(request.title().strip(), json.writeValueAsString(strip(request.ingredients())),
				json.writeValueAsString(strip(request.steps())));
	}

	@Override
	protected Response toResponse(SavedRecipe recipe) {
		return new Response(recipe.id(), recipe.profileId(), recipe.title(),
				json.readValue(recipe.ingredientsJson(), LINES), json.readValue(recipe.stepsJson(), LINES),
				recipe.mode(), recipe.promptVersion(), SavedRecipeDtos.AI_GENERATED, recipe.createdAt(),
				recipe.version());
	}

	private static List<String> strip(List<String> lines) {
		return lines.stream().map(String::strip).toList();
	}

}
