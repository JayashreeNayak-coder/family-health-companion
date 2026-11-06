package com.familyhealth;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Import;

/** Shared setup, so all integration tests reuse one application context and one real database. */
@SpringBootTest(properties = {
		"spring.datasource.url=${TEST_DB_URL:}",
		"spring.datasource.username=${TEST_DB_USERNAME:}",
		"spring.datasource.password=${TEST_DB_PASSWORD:}",
		"app.documents.storage-dir=${java.io.tmpdir}/family-health-test-documents"
})
// No request dumps: they would print test passwords, and hide real log output from the logging test.
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(TestDatabaseConfig.class)
public abstract class IntegrationTestBase {

}
