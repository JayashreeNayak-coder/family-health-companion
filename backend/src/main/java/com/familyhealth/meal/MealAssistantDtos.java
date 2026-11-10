package com.familyhealth.meal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

final class MealAssistantDtos {

	static final String AI_GENERATED = "AI_GENERATED";

	static final String DISCLAIMER = "These are AI-generated recipe ideas, not medical or nutrition advice.";

	static final String HEALTH_AWARE_DISCLAIMER = "These are AI-generated recipe ideas, not medical or nutrition "
			+ "advice. They are not confirmed safe for this person.";

	private MealAssistantDtos() {
	}

	/**
	 * General mode takes only what the user typed. {@code avoid} is for ingredients to leave out
	 * that the user lists in this request, such as allergies (docs/02 section 3.8).
	 */
	record GeneralRequest(
			@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 80) String> ingredients,
			@Min(5) @Max(600) Integer maxMinutes,
			@Size(max = 300) String preferences,
			@Size(max = 20) List<@NotBlank @Size(max = 80) String> avoid) {

		List<String> avoidOrEmpty() {
			return avoid == null ? List.of() : avoid;
		}

	}

	/**
	 * Health-aware mode adds the profile's recorded allergies, restrictions, and preferences.
	 * Conditions are sent only when listed in {@code conditionIds}, which the user confirms as
	 * relevant (docs/06 section 8.2).
	 */
	record HealthAwareRequest(
			@NotEmpty @Size(max = 20) List<@NotBlank @Size(max = 80) String> ingredients,
			@Min(5) @Max(600) Integer maxMinutes,
			@Size(max = 300) String preferences,
			@Size(max = 20) List<@NotBlank @Size(max = 80) String> avoid,
			@Size(max = 20) List<@NotNull UUID> conditionIds) {

		List<String> avoidOrEmpty() {
			return avoid == null ? List.of() : avoid;
		}

		List<UUID> conditionIdsOrEmpty() {
			return conditionIds == null ? List.of() : conditionIds;
		}

	}

	/** Exactly the recorded facts that were sent, so the user can see what was used. */
	record Considered(List<String> allergies, List<String> restrictions, List<String> preferences,
			List<String> conditions) {
	}

	/** {@code avoidMatches} lists the ingredients to avoid that the recipe's text mentions. */
	record RecipeView(String title, Integer minutes, Integer servings, List<String> ingredients, List<String> steps,
			List<String> avoidMatches) {
	}

	/**
	 * Every AI response carries these labels (docs/05 section 7). {@code considered} is null in
	 * general mode.
	 */
	record Response(String contentType, String mode, String promptVersion, List<RecipeView> recipes,
			List<String> limitations, String disclaimer, Considered considered) {
	}

}
