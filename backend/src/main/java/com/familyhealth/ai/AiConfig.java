package com.familyhealth.ai;

import com.familyhealth.ai.gemini.GeminiClient;
import com.familyhealth.ai.gemini.GeminiDocumentExtractor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chooses the provider. Only this class knows Gemini exists, so another provider replaces it here
 * without changes elsewhere. With no key, AI features report unavailable and nothing else changes.
 */
@Configuration
class AiConfig {

	@Bean
	GeminiClient geminiClient(JsonMapper json, @Value("${app.ai.gemini.base-url}") String baseUrl,
			@Value("${app.ai.gemini.api-key:}") String apiKey, @Value("${app.ai.timeout}") Duration timeout) {
		return new GeminiClient(json, baseUrl, apiKey, timeout);
	}

	@Bean
	ExtractionResponseParser extractionResponseParser(JsonMapper json) {
		return new ExtractionResponseParser(json);
	}

	@Bean
	DocumentExtractor documentExtractor(GeminiClient client, @Value("${app.ai.gemini.model}") String model,
			@Value("${app.ai.gemini.fallback-model:}") String fallbackModel, ExtractionResponseParser parser,
			PromptResources prompts) {
		return new GeminiDocumentExtractor(client, model, fallbackModel, parser, prompts);
	}

}
