package com.familyhealth.meal;

import com.familyhealth.ai.AiErrors;
import com.familyhealth.ai.AiException;
import com.familyhealth.ai.AiUsageService;
import com.familyhealth.ai.RecipeGenerator;
import com.familyhealth.meal.MealAssistantDtos.Considered;
import com.familyhealth.meal.MealAssistantDtos.GeneralRequest;
import com.familyhealth.meal.MealAssistantDtos.HealthAwareRequest;
import com.familyhealth.meal.MealAssistantDtos.RecipeView;
import com.familyhealth.meal.MealAssistantDtos.Response;
import com.familyhealth.records.DietaryFacts;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The meal assistant (docs/02 section 3.8). Not transactional: the usage claim commits on its own
 * before the slow provider call, so no database connection is held while waiting.
 */
@Service
public class MealAssistantService {

	static final String GENERAL_FEATURE = "MEAL_GENERAL";

	static final String HEALTH_AWARE_FEATURE = "MEAL_HEALTH_AWARE";

	private static final String MATCHING_NOTE = "were checked by simple word matching, which can miss other names "
			+ "and foods made from them. Check every ingredient yourself.";

	private static final Logger log = LoggerFactory.getLogger(MealAssistantService.class);

	private final RecipeGenerator generator;

	private final AiUsageService usage;

	private final DietaryFacts dietaryFacts;

	MealAssistantService(RecipeGenerator generator, AiUsageService usage, DietaryFacts dietaryFacts) {
		this.generator = generator;
		this.usage = usage;
		this.dietaryFacts = dietaryFacts;
	}

	/** Uses no profile data: the request carries only what the user typed (docs/06 section 8.2). */
	public Response general(UUID accountId, GeneralRequest request) {
		List<String> avoid = strip(request.avoidOrEmpty());
		RecipeGenerator.Request prompt = new RecipeGenerator.Request(strip(request.ingredients()),
				request.maxMinutes(), blankToNull(request.preferences()), avoid);
		RecipeGenerator.Result result = call(accountId, GENERAL_FEATURE, prompt);

		List<String> limitations = new ArrayList<>();
		limitations.add("General mode doesn't use any family member's health records.");
		limitations.add(avoid.isEmpty() ? "No ingredients to avoid were listed, so nothing was checked for allergies."
				: "Ingredients to avoid " + MATCHING_NOTE);
		return new Response(MealAssistantDtos.AI_GENERATED, "GENERAL", generator.promptVersion(),
				views(result, avoid), List.copyOf(limitations), MealAssistantDtos.DISCLAIMER, null);
	}

	/**
	 * Sends the profile's recorded allergies, restrictions, and preferences, and only the
	 * conditions the user chose. Says what is missing, and never claims a recipe is safe.
	 */
	public Response healthAware(UUID accountId, UUID profileId, HealthAwareRequest request) {
		// Access and the condition IDs are checked before anything else, even when AI is off.
		DietaryFacts.Facts facts = dietaryFacts.load(accountId, profileId, request.conditionIdsOrEmpty());
		List<String> restrictions = facts.restrictions()
			.stream()
			.filter(item -> item.kind().equals("RESTRICTION"))
			.map(DietaryFacts.Restriction::description)
			.toList();
		List<String> preferences = facts.restrictions()
			.stream()
			.filter(item -> item.kind().equals("PREFERENCE"))
			.map(DietaryFacts.Restriction::description)
			.toList();
		List<String> typedAvoid = strip(request.avoidOrEmpty());
		RecipeGenerator.ProfileContext profile = new RecipeGenerator.ProfileContext(facts.allergens(), restrictions,
				preferences, facts.conditions());
		RecipeGenerator.Request prompt = new RecipeGenerator.Request(strip(request.ingredients()),
				request.maxMinutes(), blankToNull(request.preferences()), typedAvoid, profile);
		RecipeGenerator.Result result = call(accountId, HEALTH_AWARE_FEATURE, prompt);

		List<String> limitations = new ArrayList<>();
		limitations.add("Based only on what is recorded for this person. Records may be incomplete or out of date.");
		if (facts.allergens().isEmpty()) {
			limitations.add("No allergies are recorded for this person.");
		}
		if (restrictions.isEmpty() && preferences.isEmpty()) {
			limitations.add("No dietary restrictions or preferences are recorded for this person.");
		}
		if (facts.conditions().isEmpty()) {
			limitations.add("No health conditions were included.");
		}
		else {
			limitations.add("Conditions were used only to avoid obvious conflicts. This is not a diet for any "
					+ "condition. Ask a qualified healthcare professional about diet and health conditions.");
		}
		limitations.add("Recorded allergies and ingredients to avoid " + MATCHING_NOTE);

		List<String> avoid = List.copyOf(new LinkedHashSet<>(concat(facts.allergens(), typedAvoid)));
		return new Response(MealAssistantDtos.AI_GENERATED, "HEALTH_AWARE", generator.promptVersion(),
				views(result, avoid), List.copyOf(limitations), MealAssistantDtos.HEALTH_AWARE_DISCLAIMER,
				new Considered(facts.allergens(), restrictions, preferences, facts.conditions()));
	}

	private RecipeGenerator.Result call(UUID accountId, String feature, RecipeGenerator.Request prompt) {
		if (!generator.isAvailable()) {
			throw AiErrors.unavailable();
		}
		usage.claim(accountId, feature);
		RecipeGenerator.Result result;
		try {
			result = generator.generate(prompt);
		}
		catch (AiException ex) {
			// The code only: the message and cause never reach the logs with request text.
			log.warn("Recipe generation failed: {}", ex.code());
			usage.recordCall(accountId, feature, null, null, ex.code().name());
			throw AiErrors.toApi(ex);
		}
		usage.recordCall(accountId, feature, result.inputTokens(), result.outputTokens(), "SUCCEEDED");
		return result;
	}

	private static List<RecipeView> views(RecipeGenerator.Result result, List<String> avoid) {
		return result.recipes()
			.stream()
			.map(recipe -> new RecipeView(recipe.title(), recipe.minutes(), recipe.servings(), recipe.ingredients(),
					recipe.steps(), IngredientCheck.matches(avoid, recipe.title(), recipe.ingredients(), recipe.steps())))
			.toList();
	}

	private static List<String> strip(List<String> items) {
		return items.stream().map(String::strip).toList();
	}

	private static List<String> concat(List<String> first, List<String> second) {
		List<String> all = new ArrayList<>(first);
		all.addAll(second);
		return all;
	}

	private static String blankToNull(String text) {
		return text == null || text.isBlank() ? null : text.strip();
	}

}
