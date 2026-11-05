package com.familyhealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class ApplicationIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Value("${spring.jpa.hibernate.ddl-auto}")
	private String ddlAuto;

	@Test
	void runsAgainstPostgresql18() {
		String version = jdbcTemplate.queryForObject("SHOW server_version_num", String.class);

		assertThat(version).startsWith("18");
	}

	@ParameterizedTest
	@ValueSource(strings = { "1", "2" })
	void flywayAppliesMigrations(String version) {
		Integer applied = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE version = ? AND success", Integer.class, version);

		assertThat(applied).isEqualTo(1);
	}

	@Test
	void applicationStartsWithSchemaValidation() {
		assertThat(ddlAuto).isEqualTo("validate");
		assertThat(entityManagerFactory.isOpen()).isTrue();
	}

	@Test
	void healthEndpointReturnsUpWithoutDetails() throws Exception {
		mockMvc.perform(get("/actuator/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"))
			.andExpect(jsonPath("$.components").doesNotExist())
			.andExpect(jsonPath("$.details").doesNotExist());
	}

	@ParameterizedTest
	@ValueSource(strings = { "/actuator/env", "/actuator/beans", "/actuator/configprops", "/actuator/heapdump" })
	void otherActuatorEndpointsAreNotExposed(String path) throws Exception {
		mockMvc.perform(get(path)).andExpect(status().isUnauthorized());

		ApiClient signedIn = new ApiClient(mockMvc);
		String email = ApiClient.uniqueEmail();
		signedIn.register(email, ApiClient.PASSWORD);
		signedIn.login(email, ApiClient.PASSWORD);

		assertThat(signedIn.get(path).getStatus()).isEqualTo(404);
	}

}
