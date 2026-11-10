package com.familyhealth.ai.gemini;

import com.familyhealth.ai.AiException;
import com.familyhealth.ai.PromptResources;
import com.familyhealth.ai.RecipeGenerator;
import com.familyhealth.ai.RecipeResponseParser;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Sends the request's own fields only, as JSON data in a separate part (docs/06 section 8.2). */
public class GeminiRecipeGenerator implements RecipeGenerator {

	/** Some variety, so asking again gives different ideas. */
	private static final double TEMPERATURE = 0.7;

	private final GeminiClient client;

	private final JsonMapper json;

	private final String model;

	private final String fallbackModel;

	private final RecipeResponseParser parser;

	private final String prompt;

	private final JsonNode schema;

	public GeminiRecipeGenerator(GeminiClient client, JsonMapper json, String model, String fallbackModel,
			RecipeResponseParser parser, PromptResources prompts) {
		this.client = client;
		this.json = json;
		this.model = model;
		this.fallbackModel = fallbackModel == null || fallbackModel.isBlank() ? null : fallbackModel;
		this.parser = parser;
		this.prompt = prompts.text("ai/recipes/" + RecipeResponseParser.VERSION + "/prompt.txt");
		this.schema = prompts.json("ai/recipes/" + RecipeResponseParser.VERSION + "/schema.json");
	}

	@Override
	public boolean isAvailable() {
		return client.hasKey();
	}

	@Override
	public String provider() {
		return "gemini";
	}

	@Override
	public String model() {
		return model;
	}

	@Override
	public String promptVersion() {
		return "recipes-" + RecipeResponseParser.VERSION;
	}

	/** Tries the fallback model once when the main one reports high demand. */
	@Override
	public Result generate(Request request) {
		List<GeminiClient.Part> parts = List.of(new GeminiClient.Text(json.writeValueAsString(body(request))),
				new GeminiClient.Text("Suggest recipes for the request above in the JSON structure."));
		GeminiClient.Output output;
		try {
			output = client.generate(model, prompt, parts, schema, TEMPERATURE);
		}
		catch (AiException ex) {
			if (ex.code() != AiException.Code.UNAVAILABLE || fallbackModel == null) {
				throw ex;
			}
			output = client.generate(fallbackModel, prompt, parts, schema, TEMPERATURE);
		}
		return parser.parse(output.text(), output.inputTokens(), output.outputTokens());
	}

	/** Leaves out empty fields, so the model sees only what the user gave. */
	static Map<String, Object> body(Request request) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("ingredients", request.ingredients());
		if (request.maxMinutes() != null) {
			body.put("maxMinutes", request.maxMinutes());
		}
		if (request.preferences() != null && !request.preferences().isBlank()) {
			body.put("preferences", request.preferences());
		}
		if (!request.avoid().isEmpty()) {
			body.put("avoid", request.avoid());
		}
		RecipeGenerator.ProfileContext profile = request.profile();
		if (profile != null) {
			Map<String, Object> facts = new LinkedHashMap<>();
			facts.put("allergies", profile.allergies());
			facts.put("restrictions", profile.restrictions());
			facts.put("preferences", profile.preferences());
			facts.put("conditions", profile.conditions());
			body.put("profile", facts);
		}
		return body;
	}

}
