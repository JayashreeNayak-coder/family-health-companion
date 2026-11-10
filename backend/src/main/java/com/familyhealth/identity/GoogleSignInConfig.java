package com.familyhealth.identity;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Google sign-in exists only when both the client ID and secret are set, from the environment.
 * The redirect goes through the app's public address, so it matches what is registered with
 * Google and passes through the same proxy as the API.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${app.oauth.google.client-id:}' != '' and '${app.oauth.google.client-secret:}' != ''")
class GoogleSignInConfig {

	static final String REDIRECT_PATH = "/login/oauth2/code/google";

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(@Value("${app.oauth.google.client-id}") String clientId,
			@Value("${app.oauth.google.client-secret}") String clientSecret,
			@Value("${app.public-url}") String publicUrl) {
		String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
		return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
			.clientId(clientId)
			.clientSecret(clientSecret)
			.redirectUri(base + REDIRECT_PATH)
			.build());
	}

}
