package com.familyhealth.emergencycard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class EmergencyCardIntegrationTest extends IntegrationTestBase {

	private static final String PUBLIC = "/api/v1/public/emergency-cards/";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	private ProfileFixture f;

	private String base;

	private String card;

	private String allergyId;

	private String medicationId;

	private String contactId;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		base = "/api/v1/profiles/" + f.profileId;
		card = base + "/emergency-card";
		allergyId = created(f.editor.post(base + "/allergies",
				Map.of("allergen", "Synthetic peanut", "reactionAsRecorded", "Hives", "notes", "Private allergy note")));
		medicationId = created(f.editor.post(base + "/medications",
				Map.of("name", "Synthetic medicine", "dosageAsRecorded", "1 tablet", "startOn", "2026-01-01")));
		contactId = created(f.editor.post(base + "/emergency-contacts",
				Map.of("name", "Synthetic Parent", "relationship", "Parent", "phone", "+10000000000")));
	}

	private static String created(MockHttpServletResponse response) throws Exception {
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(201);
		return JsonPath.read(response.getContentAsString(), "$.id");
	}

	private static Map<String, Object> item(String type, String text, String sourceRecordId) {
		Map<String, Object> item = new HashMap<>();
		item.put("type", type);
		item.put("text", text);
		if (sourceRecordId != null) {
			item.put("sourceRecordId", sourceRecordId);
		}
		return item;
	}

	private List<Map<String, Object>> standardItems() {
		return List.of(item("PERSON", "Child S.", null), item("ALLERGY", "Synthetic peanut: hives", allergyId),
				item("MEDICATION", "Synthetic medicine, 1 tablet", medicationId),
				item("EMERGENCY_CONTACT", "Synthetic Parent (Parent) +10000000000", contactId),
				item("NOTE", "Blood type O+ (synthetic)", null));
	}

	private MockHttpServletResponse save(ApiClient client, Long version, List<Map<String, Object>> items)
			throws Exception {
		Map<String, Object> body = new HashMap<>();
		body.put("items", items);
		if (version != null) {
			body.put("version", version);
		}
		return client.put(card, body);
	}

	private long version() throws Exception {
		return ((Number) JsonPath.read(f.manager.get(card).getContentAsString(), "$.version")).longValue();
	}

	/** Saves the standard items, turns the card on, and returns the token from the link. */
	private String enableStandardCard() throws Exception {
		assertThat(save(f.manager, null, standardItems()).getStatus()).isEqualTo(200);
		MockHttpServletResponse enabled = f.manager.post(card + "/enable", Map.of());
		assertThat(enabled.getStatus()).as(enabled.getContentAsString()).isEqualTo(200);
		return token(enabled);
	}

	private static String token(MockHttpServletResponse response) throws Exception {
		String link = JsonPath.read(response.getContentAsString(), "$.link");
		assertThat(link).startsWith("http://localhost:5173/emergency-card#");
		return link.substring(link.indexOf('#') + 1);
	}

	private int publicStatus(String token) throws Exception {
		return new ApiClient(mockMvc).get(PUBLIC + token).getStatus();
	}

	@Test
	void aNewProfileHasACardThatIsOffAndEmpty() throws Exception {
		MockHttpServletResponse response = f.manager.get(card);
		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(JsonPath.<Boolean>read(response.getContentAsString(), "$.enabled")).isFalse();
		assertThat(JsonPath.<List<?>>read(response.getContentAsString(), "$.items")).isEmpty();
	}

	@Test
	void onlyManagersCanSeeOrChangeTheCard() throws Exception {
		for (ApiClient client : List.of(f.viewer, f.editor)) {
			assertThat(client.get(card).getStatus()).isEqualTo(403);
			assertThat(save(client, null, standardItems()).getStatus()).isEqualTo(403);
			assertThat(client.post(card + "/enable", Map.of()).getStatus()).isEqualTo(403);
		}
		for (ApiClient client : List.of(f.memberWithoutGrant, f.outsider)) {
			assertThat(client.get(card).getStatus()).isEqualTo(404);
			assertThat(client.post(card + "/disable", Map.of()).getStatus()).isEqualTo(404);
		}
		assertThat(f.signedOut.get(card).getStatus()).isEqualTo(401);
	}

	@Test
	void thePublicLinkShowsOnlyTheApprovedLinesWithNoIdsAndIsNeverCached() throws Exception {
		String token = enableStandardCard();

		MockHttpServletResponse response = f.signedOut.get(PUBLIC + token);

		assertThat(response.getStatus()).isEqualTo(200);
		String body = response.getContentAsString();
		assertThat(JsonPath.<List<String>>read(body, "$.items[*].text")).containsExactly("Child S.",
				"Synthetic peanut: hives", "Synthetic medicine, 1 tablet", "Synthetic Parent (Parent) +10000000000",
				"Blood type O+ (synthetic)");
		assertThat(JsonPath.<List<String>>read(body, "$.items[*].type")).containsExactly("PERSON", "ALLERGY",
				"MEDICATION", "EMERGENCY_CONTACT", "NOTE");
		assertThat(body).doesNotContain(allergyId, medicationId, contactId, f.profileId, "Private allergy note",
				"Child Synthetic", "source");
		assertThat(response.getHeader("Cache-Control")).contains("no-store");
		assertThat(response.getHeader("X-Robots-Tag")).contains("noindex");
	}

	@Test
	void theOwnerIsToldWhenASourceRecordChangesButThePublicCardKeepsTheApprovedText() throws Exception {
		String token = enableStandardCard();
		long allergyVersion = ((Number) JsonPath.read(f.editor.get(base + "/allergies/" + allergyId).getContentAsString(),
				"$.version")).longValue();
		assertThat(f.editor.put(base + "/allergies/" + allergyId,
				Map.of("allergen", "Synthetic peanut", "reactionAsRecorded", "Swelling", "version", allergyVersion))
			.getStatus()).isEqualTo(200);
		assertThat(f.editor.delete(base + "/emergency-contacts/" + contactId).getStatus()).isEqualTo(204);

		String owner = f.manager.get(card).getContentAsString();
		assertThat(JsonPath.<List<String>>read(owner, "$.items[*].sourceStatus")).containsExactly("NONE", "CHANGED",
				"CURRENT", "DELETED", "NONE");
		assertThat(JsonPath.<List<String>>read(new ApiClient(mockMvc).get(PUBLIC + token).getContentAsString(),
				"$.items[*].text")).contains("Synthetic peanut: hives", "Synthetic Parent (Parent) +10000000000");
	}

	@Test
	void aNewLinkStopsTheOldOneAndTurningOffStopsEveryLink() throws Exception {
		String first = enableStandardCard();
		assertThat(f.manager.post(card + "/enable", Map.of()).getStatus()).isEqualTo(409);

		String second = token(f.manager.post(card + "/rotate", Map.of()));
		assertThat(publicStatus(first)).isEqualTo(404);
		assertThat(publicStatus(second)).isEqualTo(200);

		MockHttpServletResponse off = f.manager.post(card + "/disable", Map.of());
		assertThat(JsonPath.<Boolean>read(off.getContentAsString(), "$.enabled")).isFalse();
		assertThat(publicStatus(second)).isEqualTo(404);
		assertThat(f.manager.post(card + "/rotate", Map.of()).getStatus()).isEqualTo(409);

		String third = token(f.manager.post(card + "/enable", Map.of()));
		assertThat(third).isNotEqualTo(second);
		assertThat(publicStatus(second)).isEqualTo(404);
		assertThat(publicStatus(third)).isEqualTo(200);
		assertThat(jdbc.queryForObject("select token_hash from emergency_card where profile_id = ?::uuid",
				String.class, f.profileId)).isNotEqualTo(third).hasSize(64);
	}

	@Test
	void unknownAndMalformedLinksAreNotFound() throws Exception {
		assertThat(publicStatus("A".repeat(43))).isEqualTo(404);
		assertThat(publicStatus("too-short")).isEqualTo(404);
	}

	@Test
	void anEmptyCardCannotBeTurnedOn() throws Exception {
		assertThat(f.manager.post(card + "/enable", Map.of()).getStatus()).isEqualTo(422);
		assertThat(save(f.manager, null, List.of()).getStatus()).isEqualTo(200);
		assertThat(f.manager.post(card + "/enable", Map.of()).getStatus()).isEqualTo(422);
	}

	@Test
	void itemsAreValidated() throws Exception {
		String otherAllergy = created(f.manager.post("/api/v1/profiles/" + f.otherProfileId + "/allergies",
				Map.of("allergen", "Other synthetic allergen")));
		List<List<Map<String, Object>>> invalid = List.of(
				List.of(item("ALLERGY", "From another profile", otherAllergy)),
				List.of(item("MEDICATION", "Wrong kind of record", allergyId)),
				List.of(item("NOTE", "Notes come from no record", allergyId)),
				List.of(item("PERSON", "One", null), item("PERSON", "Two", null)),
				List.of(item("NOTE", "   ", null)), List.of(item("NOTE", "x".repeat(301), null)));
		for (List<Map<String, Object>> items : invalid) {
			assertThat(save(f.manager, null, items).getStatus()).as(items.toString()).isEqualTo(400);
		}
		List<Map<String, Object>> tooMany = new ArrayList<>();
		for (int i = 0; i < 31; i++) {
			tooMany.add(item("NOTE", "Line " + i, null));
		}
		assertThat(save(f.manager, null, tooMany).getStatus()).isEqualTo(400);
		assertThat(jdbc.queryForObject("select count(*) from emergency_card where profile_id = ?::uuid", Long.class,
				f.profileId)).isZero();
	}

	@Test
	void aStaleVersionIsRejected() throws Exception {
		assertThat(save(f.manager, null, standardItems()).getStatus()).isEqualTo(200);
		long version = version();
		assertThat(save(f.manager, version, List.of(item("NOTE", "Newer", null))).getStatus()).isEqualTo(200);
		assertThat(save(f.manager, version, List.of(item("NOTE", "Older", null))).getStatus()).isEqualTo(409);
		assertThat(JsonPath.<List<String>>read(f.manager.get(card).getContentAsString(), "$.items[*].text"))
			.containsExactly("Newer");
	}

	@Test
	void publicReadsAreRateLimitedPerAddress() throws Exception {
		String token = enableStandardCard();
		ApiClient visitor = new ApiClient(mockMvc);
		for (int i = 0; i < 30; i++) {
			assertThat(visitor.get(PUBLIC + token).getStatus()).isEqualTo(200);
		}
		MockHttpServletResponse limited = visitor.get(PUBLIC + token);
		assertThat(limited.getStatus()).isEqualTo(429);
		assertThat(limited.getHeader("Retry-After")).isNotBlank();
		assertThat(new ApiClient(mockMvc).get(PUBLIC + token).getStatus()).isEqualTo(200);
	}

	@Test
	void changesAreAuditedWithoutTheirContent() throws Exception {
		enableStandardCard();
		f.manager.post(card + "/rotate", Map.of());
		f.manager.post(card + "/disable", Map.of());

		List<Map<String, Object>> events = jdbc.queryForList(
				"select action, metadata::text as metadata from audit_event where profile_id = ?::uuid and action like 'EMERGENCY_CARD%' order by occurred_at",
				f.profileId);
		assertThat(events).extracting(event -> event.get("action")).containsExactly("EMERGENCY_CARD_UPDATED",
				"EMERGENCY_CARD_ENABLED", "EMERGENCY_CARD_LINK_ROTATED", "EMERGENCY_CARD_DISABLED");
		assertThat(events.toString()).doesNotContain("peanut", "Synthetic Parent");
	}

	@Test
	void deletingTheProfileDeletesTheCardAndItsLink() throws Exception {
		String token = enableStandardCard();
		assertThat(f.manager.delete(base).getStatus()).isEqualTo(204);
		assertThat(publicStatus(token)).isEqualTo(404);
		assertThat(jdbc.queryForObject("select count(*) from emergency_card where profile_id = ?::uuid", Long.class,
				f.profileId)).isZero();
	}

}
