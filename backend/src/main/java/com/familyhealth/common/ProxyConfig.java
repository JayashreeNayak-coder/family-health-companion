package com.familyhealth.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers {@link ProxySecretFilter} ahead of sessions and security. It does nothing unless PROXY_SECRET is set. */
@Configuration(proxyBeanMethods = false)
class ProxyConfig {

	private static final Logger log = LoggerFactory.getLogger(ProxyConfig.class);

	/** Long enough that it can't be guessed. `openssl rand -hex 32` gives 64 characters. */
	static final int MIN_SECRET_LENGTH = 32;

	@Bean
	FilterRegistrationBean<ProxySecretFilter> proxySecretFilter(@Value("${app.proxy.secret:}") String secret) {
		String value = secret == null ? "" : secret.strip();
		if (!value.isEmpty() && value.length() < MIN_SECRET_LENGTH) {
			throw new IllegalStateException("PROXY_SECRET must be at least " + MIN_SECRET_LENGTH + " characters");
		}
		if (!value.isEmpty()) {
			log.info("Requests must come through the proxy: PROXY_SECRET is set");
		}
		FilterRegistrationBean<ProxySecretFilter> registration = new FilterRegistrationBean<>(new ProxySecretFilter(value));
		// Before Spring Session and Spring Security, so a rejected request never touches a session.
		registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
		return registration;
	}

}
