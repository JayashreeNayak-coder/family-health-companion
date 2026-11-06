package com.familyhealth.document;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class DocumentIntegrationTest extends IntegrationTestBase {

	static final byte[] PNG = withHeader(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A }, 64);

	static final byte[] JPEG = withHeader(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0 }, 64);

	static final byte[] PDF = "%PDF-1.7\nsynthetic\n%%EOF".getBytes(StandardCharsets.US_ASCII);

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${app.documents.storage-dir}")
	private String storageDir;

	private ProfileFixture f;

	private String documents;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		documents = "/api/v1/profiles/" + f.profileId + "/documents";
	}

	static byte[] withHeader(byte[] header, int length) {
		byte[] content = Arrays.copyOf(header, length);
		Arrays.fill(content, header.length, length, (byte) 7);
		return content;
	}

	static String upload(ApiClient client, String profileId, String filename, byte[] content) throws Exception {
		return read(created(client.upload("/api/v1/profiles/" + profileId + "/documents", filename,
				"application/octet-stream", content, Map.of())), "$.id");
	}

	private Path storedFile(String documentId) {
		String key = jdbc.queryForObject("select storage_key from document where id = ?::uuid", String.class,
				documentId);
		return Path.of(storageDir, key);
	}

	@Test
	void theTypeComesFromTheContentAndTheOriginalComesBackUnchanged() throws Exception {
		MockHttpServletResponse uploaded = created(f.editor.upload(documents, "C:\\photos\\prescription.png",
				"text/html", PNG, Map.of("category", "PRESCRIPTION", "documentOn", "2026-03-04",
						"description", "Synthetic prescription")));

		assertThat(read(uploaded, "$.contentType")).isEqualTo("image/png");
		assertThat(read(uploaded, "$.originalFilename")).isEqualTo("prescription.png");
		assertThat(read(uploaded, "$.category")).isEqualTo("PRESCRIPTION");
		assertThat(read(uploaded, "$.documentOn")).isEqualTo("2026-03-04");
		assertThat(read(uploaded, "$.extractionStatus")).isEqualTo("NOT_REQUESTED");
		assertThat(uploaded.getContentAsString()).doesNotContain("storageKey").doesNotContain("sha256");

		String id = read(uploaded, "$.id");
		MockHttpServletResponse content = f.viewer.get(documents + "/" + id + "/content");

		assertThat(content.getStatus()).isEqualTo(200);
		assertThat(content.getContentAsByteArray()).isEqualTo(PNG);
		assertThat(content.getContentType()).isEqualTo("image/png");
		assertThat(content.getHeader("Content-Disposition")).startsWith("attachment").contains("prescription.png");
		assertThat(content.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(content.getHeader("Cache-Control")).contains("no-store");
		assertThat(jdbc.queryForList("select action from audit_event where profile_id = ?::uuid", String.class,
				f.profileId)).contains("DOCUMENT_UPLOADED", "DOCUMENT_DOWNLOADED");
	}

	@Test
	void jpegAndPdfAreAccepted() throws Exception {
		assertThat(read(created(f.editor.upload(documents, "scan.jpg", "image/jpeg", JPEG, Map.of())),
				"$.contentType")).isEqualTo("image/jpeg");
		MockHttpServletResponse pdf = created(f.editor.upload(documents, "report.pdf", "application/pdf", PDF,
				Map.of()));
		assertThat(read(pdf, "$.contentType")).isEqualTo("application/pdf");
		assertThat(read(pdf, "$.category")).isEqualTo("OTHER");
	}

	@Test
	void htmlSvgAndOfficeFilesAreRejectedWhateverTheyClaimToBe() throws Exception {
		for (byte[] content : List.of("<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8),
				"<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8),
				new byte[] { 'P', 'K', 3, 4, 0, 0, 0, 0, 0 })) {
			MockHttpServletResponse response = f.editor.upload(documents, "photo.png", "image/png", content, Map.of());
			assertThat(response.getStatus()).isEqualTo(415);
			assertThat(read(response, "$.code")).isEqualTo("UNSUPPORTED_FILE_TYPE");
		}
		assertThat(jdbc.queryForObject("select count(*) from document where profile_id = ?::uuid", Long.class,
				f.profileId)).isZero();
	}

	@Test
	void emptyOversizedAndInvalidUploadsAreRejected() throws Exception {
		MockHttpServletResponse empty = f.editor.upload(documents, "empty.png", "image/png", new byte[0], Map.of());
		assertThat(empty.getStatus()).isEqualTo(400);
		assertThat(read(empty, "$.code")).isEqualTo("FILE_REQUIRED");

		byte[] large = withHeader(PNG, 10 * 1024 * 1024 + 1);
		MockHttpServletResponse tooLarge = f.editor.upload(documents, "large.png", "image/png", large, Map.of());
		assertThat(tooLarge.getStatus()).isEqualTo(413);
		assertThat(read(tooLarge, "$.code")).isEqualTo("FILE_TOO_LARGE");

		MockHttpServletResponse badCategory = f.editor.upload(documents, "a.png", "image/png", PNG,
				Map.of("category", "XRAY_DIAGNOSIS"));
		assertThat(badCategory.getStatus()).isEqualTo(400);

		MockHttpServletResponse longDescription = f.editor.upload(documents, "a.png", "image/png", PNG,
				Map.of("description", "x".repeat(201)));
		assertThat(longDescription.getStatus()).isEqualTo(400);
		assertThat(read(longDescription, "$.code")).isEqualTo("VALIDATION_FAILED");
	}

	@Test
	void accessFollowsTheProfileGrants() throws Exception {
		String id = upload(f.manager, f.profileId, "visit.png", PNG);
		String one = documents + "/" + id;

		assertThat(f.viewer.get(documents).getStatus()).isEqualTo(200);
		assertThat(f.viewer.upload(documents, "a.png", "image/png", PNG, Map.of()).getStatus()).isEqualTo(403);
		assertThat(f.editor.upload(documents, "a.png", "image/png", PNG, Map.of()).getStatus()).isEqualTo(201);
		for (ApiClient noGrant : List.of(f.memberWithoutGrant, f.outsider)) {
			assertThat(noGrant.get(documents).getStatus()).isEqualTo(404);
			assertThat(noGrant.get(one + "/content").getStatus()).isEqualTo(404);
			assertThat(noGrant.upload(documents, "a.png", "image/png", PNG, Map.of()).getStatus()).isEqualTo(404);
			assertThat(noGrant.delete(one).getStatus()).isEqualTo(404);
		}
		assertThat(f.signedOut.get(one + "/content").getStatus()).isEqualTo(401);
		// The manager can open both profiles, but a document is only found under its own.
		assertThat(f.manager.get("/api/v1/profiles/" + f.otherProfileId + "/documents/" + id + "/content").getStatus())
			.isEqualTo(404);
		assertThat(f.manager.delete("/api/v1/profiles/" + f.otherProfileId + "/documents/" + id).getStatus())
			.isEqualTo(404);
	}

	@Test
	void onlyManageCanDeleteAndRecordsKeepTheirDataAndSourceLabel() throws Exception {
		String id = upload(f.editor, f.profileId, "lab.pdf", PDF);
		Path file = storedFile(id);
		assertThat(file).exists();
		String conditions = "/api/v1/profiles/" + f.profileId + "/conditions";
		String condition = read(created(f.editor.post(conditions,
				Map.of("name", "Synthetic", "sourceType", "DOCUMENTED", "sourceDocumentId", id))), "$.id");

		assertThat(f.editor.delete(documents + "/" + id).getStatus()).isEqualTo(403);
		assertThat(f.manager.delete(documents + "/" + id).getStatus()).isEqualTo(204);

		assertThat(file).doesNotExist();
		assertThat(f.manager.get(documents + "/" + id).getStatus()).isEqualTo(404);
		MockHttpServletResponse after = f.viewer.get(conditions + "/" + condition);
		assertThat(read(after, "$.name")).isEqualTo("Synthetic");
		assertThat(read(after, "$.sourceType")).isEqualTo("DOCUMENTED");
		assertThat(JsonPath.<Object>read(after.getContentAsString(), "$.sourceDocumentId")).isNull();
		assertThat(JsonPath.<Object>read(after.getContentAsString(), "$.sourceDocumentDeletedAt")).isNotNull();
		assertThat(jdbc.queryForList("select action from audit_event where profile_id = ?::uuid", String.class,
				f.profileId)).contains("DOCUMENT_DELETED");
	}

	@Test
	void aSourceDocumentMustBelongToTheRecordsProfile() throws Exception {
		String own = upload(f.manager, f.profileId, "own.png", PNG);
		String other = upload(f.manager, f.otherProfileId, "other.png", PNG);
		String visits = "/api/v1/profiles/" + f.profileId + "/visits";

		MockHttpServletResponse documented = created(f.editor.post(visits, Map.of("sourceDocumentId", own)));
		assertThat(read(documented, "$.sourceType")).isEqualTo("DOCUMENTED");
		assertThat(read(documented, "$.sourceDocumentId")).isEqualTo(own);

		MockHttpServletResponse foreign = f.manager.post(visits, Map.of("sourceDocumentId", other));
		assertThat(foreign.getStatus()).isEqualTo(404);
		assertThat(read(foreign, "$.code")).isEqualTo("SOURCE_DOCUMENT_NOT_FOUND");

		MockHttpServletResponse mismatch = f.editor.post(visits,
				Map.of("sourceType", "USER_REPORTED", "sourceDocumentId", own));
		assertThat(mismatch.getStatus()).isEqualTo(400);
		assertThat(read(mismatch, "$.code")).isEqualTo("SOURCE_TYPE_MISMATCH");
	}

	@Test
	void deletingAProfileDeletesItsFiles() throws Exception {
		String profile = f.createDependent("Deleted Synthetic");
		String id = upload(f.manager, profile, "x.png", PNG);
		Path file = storedFile(id);
		created(f.manager.post("/api/v1/profiles/" + profile + "/symptoms",
				Map.of("description", "Synthetic", "sourceDocumentId", id)));

		assertThat(f.manager.delete("/api/v1/profiles/" + profile).getStatus()).isEqualTo(204);

		assertThat(file).doesNotExist();
		assertThat(jdbc.queryForObject("select count(*) from document where id = ?::uuid", Long.class, id)).isZero();
	}

	@Test
	void aFailedUploadLeavesNoFileBehind() throws Exception {
		long before = countFiles();
		// The document row fails its constraint, after the file was written.
		jdbc.execute("alter table document add constraint test_reject_ck check (original_filename <> 'reject.png')");
		try {
			assertThat(f.editor.upload(documents, "reject.png", "image/png", PNG, Map.of()).getStatus())
				.isEqualTo(409);
		}
		finally {
			jdbc.execute("alter table document drop constraint test_reject_ck");
		}
		assertThat(countFiles()).isEqualTo(before);
	}

	private long countFiles() throws Exception {
		try (var files = Files.list(Path.of(storageDir))) {
			return files.count();
		}
	}

	@Test
	void unknownDocumentsAreNotFound() throws Exception {
		assertThat(f.manager.get(documents + "/" + UUID.randomUUID() + "/content").getStatus()).isEqualTo(404);
	}

}
