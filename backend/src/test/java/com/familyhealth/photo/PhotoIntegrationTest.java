package com.familyhealth.photo;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.imageio.ImageIO;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Profile and family photos: who may change and see them, and that files follow the database. */
class PhotoIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Value("${app.documents.storage-dir}")
	private String storageDir;

	private ProfileFixture f;

	private String profilePhoto;

	private String familyPhoto;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		profilePhoto = "/api/v1/profiles/" + f.profileId + "/photo";
		familyPhoto = "/api/v1/families/" + f.familyId + "/photo";
	}

	private static byte[] png() throws Exception {
		BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(Color.ORANGE);
		g.fillRect(0, 0, 300, 200);
		g.dispose();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	private String profileKey() {
		return jdbc.queryForObject("select photo_key from health_profile where id = ?::uuid", String.class, f.profileId);
	}

	private boolean stored(String key) {
		return Files.exists(Path.of(storageDir).toAbsolutePath().normalize().resolve(key));
	}

	@Test
	void editorsSetAProfilePhotoThatViewersSeeAndOutsidersDoNot() throws Exception {
		assertThat(f.viewer.get(profilePhoto).getStatus()).isEqualTo(404);
		assertThat(f.viewer.uploadPut(profilePhoto, "me.png", "image/png", png()).getStatus()).isEqualTo(403);

		MockHttpServletResponse saved = f.editor.uploadPut(profilePhoto, "me.png", "image/png", png());
		assertThat(saved.getStatus()).as(saved.getContentAsString()).isEqualTo(200);
		assertThat(JsonPath.<String>read(saved.getContentAsString(), "$.photoUpdatedAt")).isNotNull();

		MockHttpServletResponse photo = f.viewer.get(profilePhoto);
		assertThat(photo.getStatus()).isEqualTo(200);
		assertThat(photo.getContentType()).isEqualTo("image/jpeg");
		assertThat(photo.getHeader("Cache-Control")).contains("private");
		BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(photo.getContentAsByteArray()));
		assertThat(image.getWidth()).isEqualTo(200);
		assertThat(image.getHeight()).isEqualTo(200);

		assertThat(f.outsider.get(profilePhoto).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.get(profilePhoto).getStatus()).isEqualTo(404);
		assertThat(f.signedOut.get(profilePhoto).getStatus()).isEqualTo(401);
	}

	@Test
	void replacingOrRemovingAPhotoDeletesTheOldFile() throws Exception {
		f.editor.uploadPut(profilePhoto, "one.png", "image/png", png());
		String first = profileKey();
		assertThat(stored(first)).isTrue();

		f.editor.uploadPut(profilePhoto, "two.png", "image/png", png());
		String second = profileKey();
		assertThat(second).isNotEqualTo(first);
		assertThat(stored(first)).isFalse();
		assertThat(stored(second)).isTrue();

		MockHttpServletResponse removed = f.editor.delete(profilePhoto);
		assertThat(removed.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<Object>read(removed.getContentAsString(), "$.photoUpdatedAt")).isNull();
		assertThat(stored(second)).isFalse();
		assertThat(f.viewer.get(profilePhoto).getStatus()).isEqualTo(404);
	}

	@Test
	void aRejectedUploadLeavesTheOldPhotoInPlace() throws Exception {
		f.editor.uploadPut(profilePhoto, "one.png", "image/png", png());
		String key = profileKey();

		MockHttpServletResponse html = f.editor.uploadPut(profilePhoto, "photo.png", "image/png",
				"<html>not a photo</html>".getBytes());

		assertThat(html.getStatus()).isEqualTo(415);
		assertThat(profileKey()).isEqualTo(key);
		assertThat(stored(key)).isTrue();
	}

	@Test
	void deletingAProfileDeletesItsPhotoFile() throws Exception {
		f.editor.uploadPut(profilePhoto, "one.png", "image/png", png());
		String key = profileKey();
		assertThat(f.manager.delete("/api/v1/profiles/" + f.profileId).getStatus()).isEqualTo(204);
		assertThat(stored(key)).isFalse();
	}

	@Test
	void managersSetAFamilyPhotoThatMembersSee() throws Exception {
		assertThat(f.viewer.uploadPut(familyPhoto, "f.png", "image/png", png()).getStatus()).isEqualTo(403);
		MockHttpServletResponse saved = f.manager.uploadPut(familyPhoto, "f.png", "image/png", png());
		assertThat(saved.getStatus()).as(saved.getContentAsString()).isEqualTo(200);

		assertThat(f.viewer.get(familyPhoto).getStatus()).isEqualTo(200);
		assertThat(f.outsider.get(familyPhoto).getStatus()).isEqualTo(404);
		assertThat(JsonPath.<String>read(f.viewer.get("/api/v1/families/" + f.familyId).getContentAsString(),
				"$.photoUpdatedAt")).isNotNull();

		assertThat(f.manager.delete(familyPhoto).getStatus()).isEqualTo(200);
		assertThat(f.viewer.get(familyPhoto).getStatus()).isEqualTo(404);
	}

	@Test
	void deletingAFamilyDeletesEveryPhotoFile() throws Exception {
		var manager = new com.familyhealth.ApiClient(mockMvc);
		manager.signUp(com.familyhealth.ApiClient.uniqueEmail(), "Solo Synthetic");
		String familyId = JsonPath.read(manager.post("/api/v1/families", Map.of("name", "Photo Family"))
			.getContentAsString(), "$.id");
		String profileId = JsonPath.read(manager.post("/api/v1/families/" + familyId + "/profiles",
				Map.of("fullName", "Child Synthetic", "kind", "DEPENDENT")).getContentAsString(), "$.id");
		manager.uploadPut("/api/v1/families/" + familyId + "/photo", "f.png", "image/png", png());
		manager.uploadPut("/api/v1/profiles/" + profileId + "/photo", "p.png", "image/png", png());
		String familyKey = jdbc.queryForObject("select photo_key from family where id = ?::uuid", String.class, familyId);
		String profileKey = jdbc.queryForObject("select photo_key from health_profile where id = ?::uuid", String.class,
				profileId);

		assertThat(manager.delete("/api/v1/families/" + familyId).getStatus()).isEqualTo(204);

		assertThat(stored(familyKey)).isFalse();
		assertThat(stored(profileKey)).isFalse();
	}

}
