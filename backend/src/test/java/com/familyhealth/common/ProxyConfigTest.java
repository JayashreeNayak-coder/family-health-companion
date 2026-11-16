package com.familyhealth.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ProxyConfigTest {

	@Test
	void shortSecretStopsStartup() {
		assertThatThrownBy(() -> new ProxyConfig().proxySecretFilter("too-short"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("PROXY_SECRET");
	}

}
