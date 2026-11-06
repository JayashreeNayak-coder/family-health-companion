package com.familyhealth.ai.gemini;

import com.familyhealth.ai.AiException;
import com.familyhealth.ai.AiException.Code;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The Gemini API's generateContent call over plain HTTP, so no provider SDK is needed. The key
 * travels in a header, never in the URL, and neither requests nor responses are logged.
 */
public class GeminiClient {

	/** One request part: text, or inline file data. */
	public sealed interface Part permits Text, InlineData {
	}

	public record Text(String text) implements Part {
	}

	public record InlineData(String mimeType, String base64) implements Part {
	}

	public record Output(String text, Integer inputTokens, Integer outputTokens) {
	}

	private final HttpClient http;

	private final JsonMapper json;

	private final String baseUrl;

	private final String apiKey;

	private final Duration timeout;

	public GeminiClient(JsonMapper json, String baseUrl, String apiKey, Duration timeout) {
		this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
		this.json = json;
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.apiKey = apiKey;
		this.timeout = timeout;
	}

	public boolean hasKey() {
		return apiKey != null && !apiKey.isBlank();
	}

	/**
	 * Instructions go in the system instruction and user content in separate parts, so document
	 * text can't pose as instructions. Temperature 0 keeps copying as literal as possible.
	 */
	public Output generate(String model, String systemInstruction, List<Part> parts, JsonNode responseSchema) {
		if (!hasKey()) {
			throw new AiException(Code.NOT_CONFIGURED, "No Gemini API key is configured");
		}
		ObjectNode body = json.createObjectNode();
		body.putObject("systemInstruction").putArray("parts").addObject().put("text", systemInstruction);
		ArrayNode contentParts = body.putArray("contents").addObject().put("role", "user").putArray("parts");
		for (Part part : parts) {
			switch (part) {
				case Text text -> contentParts.addObject().put("text", text.text());
				case InlineData data -> contentParts.addObject()
					.putObject("inlineData")
					.put("mimeType", data.mimeType())
					.put("data", data.base64());
			}
		}
		ObjectNode config = body.putObject("generationConfig");
		config.put("temperature", 0);
		if (responseSchema != null) {
			config.put("responseMimeType", "application/json");
			config.set("responseSchema", responseSchema);
		}

		HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/" + model + ":generateContent"))
			.timeout(timeout)
			.header("Content-Type", "application/json")
			.header("x-goog-api-key", apiKey)
			.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
			.build();
		HttpResponse<String> response;
		try {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (HttpTimeoutException ex) {
			throw new AiException(Code.TIMEOUT, "Gemini did not answer in time", ex);
		}
		catch (IOException ex) {
			throw new AiException(Code.UNAVAILABLE, "Gemini could not be reached", ex);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AiException(Code.UNAVAILABLE, "Interrupted while calling Gemini", ex);
		}
		return read(response.statusCode(), response.body());
	}

	private Output read(int status, String body) {
		if (status == 429) {
			throw new AiException(Code.RATE_LIMITED, "Gemini usage limit reached");
		}
		if (status == 400 || status == 401 || status == 403 || status == 404) {
			// A refused key, unknown model, or rejected request. Retrying won't help.
			throw new AiException(Code.NOT_CONFIGURED, "Gemini refused the request with status " + status);
		}
		if (status >= 500) {
			throw new AiException(Code.UNAVAILABLE, "Gemini returned status " + status);
		}
		if (status != 200) {
			throw new AiException(Code.MALFORMED_RESPONSE, "Gemini returned status " + status);
		}
		JsonNode root;
		try {
			root = json.readTree(body);
		}
		catch (JacksonException ex) {
			throw new AiException(Code.MALFORMED_RESPONSE, "Gemini response is not JSON");
		}
		JsonNode usage = root.path("usageMetadata");
		Integer input = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
		Integer output = usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null;
		JsonNode candidate = root.path("candidates").path(0);
		if (candidate.isMissingNode()) {
			if (root.path("promptFeedback").has("blockReason")) {
				throw new AiException(Code.BLOCKED, "Gemini declined the request");
			}
			throw new AiException(Code.MALFORMED_RESPONSE, "Gemini returned no candidate");
		}
		String finish = candidate.path("finishReason").asString("");
		if (!finish.isEmpty() && !finish.equals("STOP")) {
			throw new AiException(finish.equals("SAFETY") || finish.equals("PROHIBITED_CONTENT") ? Code.BLOCKED
					: Code.MALFORMED_RESPONSE, "Gemini stopped early: " + finish);
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode part : candidate.path("content").path("parts")) {
			if (part.has("text")) {
				text.append(part.get("text").asString());
			}
		}
		if (text.isEmpty()) {
			throw new AiException(Code.MALFORMED_RESPONSE, "Gemini returned no text");
		}
		return new Output(text.toString(), input, output);
	}

}
