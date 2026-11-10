package com.familyhealth.ai;

import com.familyhealth.ai.AiException.Code;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Checks model output against recipe schema v1. Anything unexpected rejects the whole response:
 * nothing is repaired by guessing (docs/06 section 8.6). Shared by every provider.
 */
public final class RecipeResponseParser {

	public static final String VERSION = "v1";

	static final int MAX_RECIPES = 3;

	static final int MAX_INGREDIENTS = 40;

	static final int MAX_STEPS = 30;

	private static final int MAX_TITLE = 200;

	private static final int MAX_LINE = 1000;

	private static final Set<String> TOP_LEVEL = Set.of("recipes");

	private static final Set<String> RECIPE_FIELDS = Set.of("title", "minutes", "servings", "ingredients", "steps");

	private final JsonMapper json;

	public RecipeResponseParser(JsonMapper json) {
		this.json = json;
	}

	public RecipeGenerator.Result parse(String text, Integer inputTokens, Integer outputTokens) {
		JsonNode root;
		try {
			root = json.readTree(text);
		}
		catch (JacksonException ex) {
			throw malformed("not JSON");
		}
		if (root == null || !root.isObject() || !TOP_LEVEL.containsAll(root.propertyNames())) {
			throw malformed("unexpected top-level shape");
		}
		JsonNode recipes = root.get("recipes");
		if (recipes == null || !recipes.isArray() || recipes.isEmpty() || recipes.size() > MAX_RECIPES) {
			throw malformed("expected 1 to " + MAX_RECIPES + " recipes");
		}
		List<RecipeGenerator.Recipe> parsed = new ArrayList<>();
		for (JsonNode recipe : recipes) {
			parsed.add(recipe(recipe));
		}
		return new RecipeGenerator.Result(List.copyOf(parsed), inputTokens, outputTokens);
	}

	private RecipeGenerator.Recipe recipe(JsonNode node) {
		if (!node.isObject() || !RECIPE_FIELDS.containsAll(node.propertyNames())) {
			throw malformed("unexpected recipe fields");
		}
		return new RecipeGenerator.Recipe(text(node.get("title"), MAX_TITLE), whole(node.get("minutes"), 1, 1440),
				whole(node.get("servings"), 1, 50), lines(node.get("ingredients"), MAX_INGREDIENTS),
				lines(node.get("steps"), MAX_STEPS));
	}

	private static String text(JsonNode node, int max) {
		if (node == null || !node.isString()) {
			throw malformed("expected text");
		}
		String value = node.asString().strip();
		if (value.isEmpty() || value.length() > max) {
			throw malformed("text empty or too long");
		}
		return value;
	}

	private static Integer whole(JsonNode node, int min, int max) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (!node.isIntegralNumber() || node.asInt() < min || node.asInt() > max) {
			throw malformed("number out of range");
		}
		return node.asInt();
	}

	private static List<String> lines(JsonNode node, int max) {
		if (node == null || !node.isArray() || node.isEmpty() || node.size() > max) {
			throw malformed("expected 1 to " + max + " lines");
		}
		List<String> lines = new ArrayList<>();
		for (JsonNode line : node) {
			lines.add(text(line, MAX_LINE));
		}
		return List.copyOf(lines);
	}

	private static AiException malformed(String reason) {
		return new AiException(Code.MALFORMED_RESPONSE, "Recipe output rejected: " + reason);
	}

}
