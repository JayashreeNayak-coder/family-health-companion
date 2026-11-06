package com.familyhealth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Every integration test uses the fake provider, so no test can reach a real AI service. */
@TestConfiguration(proxyBeanMethods = false)
class FakeAiConfig {

	@Bean
	@Primary
	FakeDocumentExtractor fakeDocumentExtractor() {
		return new FakeDocumentExtractor();
	}

}
