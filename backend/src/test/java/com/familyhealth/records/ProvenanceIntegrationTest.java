package com.familyhealth.records;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class ProvenanceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private ProfileFixture f;

	private String conditions;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		conditions = "/api/v1/profiles/" + f.profileId + "/conditions";
	}

	@Test
	void recordsDefaultToUserReportedAndKeepTheirCreator() throws Exception {
		MockHttpServletResponse response = created(f.editor.post(conditions, Map.of("name", "Synthetic")));

		assertThat(read(response, "$.sourceType")).isEqualTo("USER_REPORTED");
		assertThat(JsonPath.<Object>read(response.getContentAsString(), "$.sourceDocumentId")).isNull();
		assertThat(read(response, "$.status")).isEqualTo("UNKNOWN");
		assertThat(jdbc.queryForObject("select created_by::text from condition where id = ?::uuid", String.class,
				read(response, "$.id"))).isEqualTo(f.editorId);
	}

	@Test
	void documentedWithoutASourceIsRejectedByTheService() throws Exception {
		MockHttpServletResponse response = f.editor.post(conditions,
				Map.of("name", "Synthetic", "sourceType", "DOCUMENTED"));

		assertThat(response.getStatus()).isEqualTo(400);
		assertThat(read(response, "$.code")).isEqualTo("SOURCE_DOCUMENT_REQUIRED");
	}

	@Test
	void aSourceDocumentThatDoesNotExistIsRejected() throws Exception {
		MockHttpServletResponse response = f.editor.post(conditions,
				Map.of("name", "Synthetic", "sourceType", "DOCUMENTED", "sourceDocumentId", UUID.randomUUID()));

		assertThat(response.getStatus()).isEqualTo(404);
		assertThat(read(response, "$.code")).isEqualTo("SOURCE_DOCUMENT_NOT_FOUND");
	}

	@Test
	void documentedWithoutASourceIsRejectedByTheDatabase() {
		assertThatThrownBy(() -> jdbc.update("""
				insert into allergy (id, profile_id, source_type, created_by, allergen, created_at, updated_at, version)
				values (gen_random_uuid(), ?::uuid, 'DOCUMENTED', ?::uuid, 'Synthetic', now(), now(), 0)
				""", f.profileId, f.editorId)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void anUpdateCannotChangeProvenance() throws Exception {
		String id = read(created(f.editor.post(conditions, Map.of("name", "Synthetic"))), "$.id");

		MockHttpServletResponse updated = f.editor.put(conditions + "/" + id,
				Map.of("name", "Changed", "sourceType", "DOCUMENTED", "version", 0));

		assertThat(updated.getStatus()).isEqualTo(200);
		assertThat(read(updated, "$.sourceType")).isEqualTo("USER_REPORTED");
	}

}
