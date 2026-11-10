package com.familyhealth.ai;

import java.util.List;

/**
 * Suggests recipes from the ingredients a user typed. Implementations send only the request's
 * fields, as data separate from the instructions, and give the model no tools (docs/06 section
 * 8.2). The caller decides what goes into a request; general mode never adds profile data.
 */
public interface RecipeGenerator {

	/** False when no provider or key is configured. Manual workflows don't depend on this. */
	boolean isAvailable();

	String provider();

	String model();

	String promptVersion();

	/** @throws AiException when the provider fails or its output doesn't match the schema */
	Result generate(Request request);

	/**
	 * What the user asked for. {@code avoid} holds ingredients the user listed in this request,
	 * for example allergies. {@code maxMinutes} and {@code preferences} may be null.
	 * {@code profile} is null in general mode, and never holds names, dates, or contact details.
	 */
	record Request(List<String> ingredients, Integer maxMinutes, String preferences, List<String> avoid,
			ProfileContext profile) {

		/** General mode: no profile data at all. */
		public Request(List<String> ingredients, Integer maxMinutes, String preferences, List<String> avoid) {
			this(ingredients, maxMinutes, preferences, avoid, null);
		}

	}

	/**
	 * The recorded facts health-aware mode may send (docs/06 section 8.2). {@code conditions}
	 * holds only the conditions the user confirmed as relevant.
	 */
	record ProfileContext(List<String> allergies, List<String> restrictions, List<String> preferences,
			List<String> conditions) {
	}

	/** {@code minutes} and {@code servings} are null when the model gave none. */
	record Recipe(String title, Integer minutes, Integer servings, List<String> ingredients, List<String> steps) {
	}

	record Result(List<Recipe> recipes, Integer inputTokens, Integer outputTokens) {
	}

}
