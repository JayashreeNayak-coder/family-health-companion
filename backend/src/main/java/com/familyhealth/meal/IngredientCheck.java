package com.familyhealth.meal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Flags recipes that mention an ingredient to avoid, by plain text matching. It catches obvious
 * conflicts only: it misses other names, translations, and products made from an ingredient. So
 * it can only ever flag a conflict, and a recipe with no flag must never be called safe (docs/06
 * section 8.5). Matching is deliberately loose: "nut" also flags "coconut", which is the safer error.
 */
final class IngredientCheck {

	private static final int MIN_STEM = 3;

	private IngredientCheck() {
	}

	/** The terms, as the user typed them, that appear anywhere in the recipe's text. */
	static List<String> matches(List<String> avoid, String title, List<String> ingredients, List<String> steps) {
		StringBuilder all = new StringBuilder(title);
		ingredients.forEach(line -> all.append('\n').append(line));
		steps.forEach(line -> all.append('\n').append(line));
		String text = all.toString().toLowerCase(Locale.ROOT);
		List<String> found = new ArrayList<>();
		for (String term : avoid) {
			if (stems(term).stream().anyMatch(text::contains) && !found.contains(term)) {
				found.add(term);
			}
		}
		return List.copyOf(found);
	}

	/** The term, and its singular when it looks plural ("peanuts" also matches "peanut"). */
	static List<String> stems(String term) {
		String lower = term.strip().toLowerCase(Locale.ROOT);
		List<String> stems = new ArrayList<>();
		if (lower.isEmpty()) {
			return stems;
		}
		stems.add(lower);
		if (lower.endsWith("es") && lower.length() - 2 >= MIN_STEM) {
			stems.add(lower.substring(0, lower.length() - 2));
		}
		if (lower.endsWith("s") && lower.length() - 1 >= MIN_STEM) {
			stems.add(lower.substring(0, lower.length() - 1));
		}
		return stems;
	}

}
