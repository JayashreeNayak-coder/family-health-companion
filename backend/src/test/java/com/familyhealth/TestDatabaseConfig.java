package com.familyhealth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Starts PostgreSQL in a container, unless TEST_DB_URL points at an existing test database.
 * Either way tests run against real PostgreSQL 18.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestDatabaseConfig {

	@Bean
	@ServiceConnection
	@ConditionalOnExpression("'${TEST_DB_URL:}'.isEmpty()")
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
	}

}
