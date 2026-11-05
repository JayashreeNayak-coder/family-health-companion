package com.familyhealth.medication;

import static com.familyhealth.ProfileFixture.created;
import static com.familyhealth.ProfileFixture.read;
import static org.assertj.core.api.Assertions.assertThat;

import com.familyhealth.ApiClient;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

class MedicationIntegrationTest extends IntegrationTestBase {

	private static final String UTC_DOSE = "2026-01-15T08:00:00Z";

	@Autowired
	private MockMvc mockMvc;

	private ProfileFixture f;

	private String medications;

	private String medicationId;

	private String medication;

	@BeforeEach
	void setUp() throws Exception {
		f = new ProfileFixture(mockMvc);
		medications = "/api/v1/profiles/" + f.profileId + "/medications";
		medicationId = addMedication(Map.of("name", "Synthetic medicine", "status", "ACTIVE", "dosageAsRecorded",
				"1 tablet"));
		medication = medications + "/" + medicationId;
	}

	private String addMedication(Map<String, String> body) throws Exception {
		return read(created(f.editor.post(medications, body)), "$.id");
	}

	private static Map<String, Object> daily(String time, String zone) {
		return Map.of("timeOfDay", time, "timeZone", zone, "reminderEnabled", true);
	}

	private String setSchedule(Map<String, Object> schedule) throws Exception {
		MockHttpServletResponse response = f.editor.put(medication + "/schedules", Map.of("schedules", List.of(schedule)));
		assertThat(response.getStatus()).as(response.getContentAsString()).isEqualTo(200);
		return read(response, "$[0].id");
	}

	private static Map<String, Object> dose(String scheduleId, String scheduledFor, String status) {
		return Map.of("scheduleId", scheduleId, "scheduledFor", scheduledFor, "status", status);
	}

	private List<Map<String, Object>> dueOn(ApiClient client, String date) throws Exception {
		return JsonPath.read(client.get(medications + "/due?date=" + date).getContentAsString(), "$");
	}

	@Test
	void schedulesAndDueListAuthorizationMatrix() throws Exception {
		Map<String, Object> body = Map.of("schedules", List.of(daily("08:00", "UTC")));
		String schedules = medication + "/schedules";
		String due = medications + "/due?date=2026-01-15";

		for (String path : List.of(schedules, due)) {
			assertThat(f.signedOut.get(path).getStatus()).isEqualTo(401);
			assertThat(f.outsider.get(path).getStatus()).isEqualTo(404);
			assertThat(f.memberWithoutGrant.get(path).getStatus()).isEqualTo(404);
			assertThat(f.viewer.get(path).getStatus()).isEqualTo(200);
		}

		assertThat(f.signedOut.put(schedules, body).getStatus()).isEqualTo(401);
		assertThat(f.outsider.put(schedules, body).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.put(schedules, body).getStatus()).isEqualTo(404);
		assertThat(f.viewer.put(schedules, body).getStatus()).isEqualTo(403);
		assertThat(dueOn(f.manager, "2026-01-15")).isEmpty();
		assertThat(f.editor.put(schedules, body).getStatus()).isEqualTo(200);
	}

	@Test
	void adherenceAuthorizationMatrix() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		Map<String, Object> body = dose(scheduleId, UTC_DOSE, "TAKEN");
		String path = medication + "/adherence";

		assertThat(f.signedOut.post(path, body).getStatus()).isEqualTo(401);
		assertThat(f.outsider.post(path, body).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.post(path, body).getStatus()).isEqualTo(404);
		assertThat(f.viewer.post(path, body).getStatus()).isEqualTo(403);
		assertThat(dueOn(f.manager, "2026-01-15").get(0).get("status")).isNull();

		assertThat(f.editor.post(path, body).getStatus()).isEqualTo(201);
		assertThat(dueOn(f.viewer, "2026-01-15").get(0).get("status")).isEqualTo("TAKEN");
	}

	@Test
	void aMedicationFromAnotherProfileReturns404OnEverySubEndpoint() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		f.grant(f.otherProfileId, f.editorId, "EDIT");
		String wrong = "/api/v1/profiles/" + f.otherProfileId + "/medications/" + medicationId;

		assertThat(f.editor.get(wrong + "/schedules").getStatus()).isEqualTo(404);
		assertThat(f.editor.put(wrong + "/schedules", Map.of("schedules", List.of())).getStatus()).isEqualTo(404);
		assertThat(f.editor.post(wrong + "/adherence", dose(scheduleId, UTC_DOSE, "TAKEN")).getStatus()).isEqualTo(404);
		assertThat(JsonPath.<List<?>>read(f.editor.get(medication + "/schedules").getContentAsString(), "$")).hasSize(1);
	}

	@Test
	void aScheduleFromAnotherMedicationReturns404() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		String other = addMedication(Map.of("name", "Other medicine", "status", "ACTIVE"));

		MockHttpServletResponse response = f.editor.post(medications + "/" + other + "/adherence",
				dose(scheduleId, UTC_DOSE, "TAKEN"));

		assertThat(response.getStatus()).isEqualTo(404);
	}

	@Test
	void oneDoseCannotBeLoggedTwice() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		String path = medication + "/adherence";

		assertThat(f.editor.post(path, dose(scheduleId, UTC_DOSE, "TAKEN")).getStatus()).isEqualTo(201);

		MockHttpServletResponse second = f.editor.post(path, dose(scheduleId, UTC_DOSE, "SKIPPED"));
		assertThat(second.getStatus()).isEqualTo(409);
		assertThat(read(second, "$.code")).isEqualTo("ALREADY_RECORDED");
		assertThat(dueOn(f.editor, "2026-01-15").get(0).get("status")).isEqualTo("TAKEN");
	}

	@Test
	void savingTheSameScheduleAgainKeepsItsRecordedDoses() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		f.editor.post(medication + "/adherence", dose(scheduleId, UTC_DOSE, "TAKEN"));

		String again = setSchedule(Map.of("timeOfDay", "08:00", "timeZone", "UTC", "reminderEnabled", false));

		assertThat(again).isEqualTo(scheduleId);
		assertThat(dueOn(f.editor, "2026-01-15").get(0).get("status")).isEqualTo("TAKEN");
		assertThat(f.editor.post(medication + "/adherence", dose(scheduleId, UTC_DOSE, "SKIPPED")).getStatus())
			.isEqualTo(409);
	}

	@Test
	void onlyARealDoseTimeCanBeRecorded() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		String path = medication + "/adherence";

		MockHttpServletResponse wrongTime = f.editor.post(path, dose(scheduleId, "2026-01-15T09:00:00Z", "TAKEN"));
		assertThat(wrongTime.getStatus()).isEqualTo(400);
		assertThat(read(wrongTime, "$.code")).isEqualTo("NOT_A_SCHEDULED_DOSE");

		MockHttpServletResponse future = f.editor.post(path, dose(scheduleId, "2999-01-15T08:00:00Z", "TAKEN"));
		assertThat(future.getStatus()).isEqualTo(400);
		assertThat(read(future, "$.code")).isEqualTo("DOSE_IN_FUTURE");
	}

	@Test
	void dueRespectsDaysOfWeekDatesAndStatus() throws Exception {
		// 2026-01-15 is a Thursday.
		setSchedule(Map.of("timeOfDay", "08:00", "daysOfWeek", List.of(4), "timeZone", "UTC", "reminderEnabled", true));
		assertThat(dueOn(f.editor, "2026-01-15")).hasSize(1);
		assertThat(dueOn(f.editor, "2026-01-16")).isEmpty();

		f.editor.put(medication, Map.of("name", "Synthetic medicine", "status", "ACTIVE", "startOn", "2026-01-16",
				"version", 0));
		assertThat(dueOn(f.editor, "2026-01-15")).isEmpty();
		assertThat(dueOn(f.editor, "2026-01-22")).hasSize(1);

		f.editor.put(medication, Map.of("name", "Synthetic medicine", "status", "STOPPED", "version", 1));
		assertThat(dueOn(f.editor, "2026-01-22")).isEmpty();
	}

	@Test
	void anUnknownStatusIsNotTreatedAsActive() throws Exception {
		f.editor.put(medication, Map.of("name", "Synthetic medicine", "version", 0));
		setSchedule(daily("08:00", "UTC"));

		assertThat(dueOn(f.editor, "2026-01-15")).isEmpty();
	}

	@Test
	void dosesUseTheSchedulesOwnTimeZone() throws Exception {
		setSchedule(daily("08:00", "Asia/Kolkata"));

		Map<String, Object> dose = dueOn(f.editor, "2026-01-15").get(0);

		assertThat(dose.get("scheduledFor")).isEqualTo("2026-01-15T02:30:00Z");
		assertThat(dose.get("timeOfDay")).isEqualTo("08:00");
	}

	@Test
	void rejectsInvalidSchedules() throws Exception {
		String path = medication + "/schedules";

		MockHttpServletResponse zone = f.editor.put(path, Map.of("schedules", List.of(daily("08:00", "Mars/Olympus"))));
		assertThat(zone.getStatus()).isEqualTo(400);
		assertThat(read(zone, "$.code")).isEqualTo("INVALID_TIME_ZONE");

		assertThat(f.editor.put(path, Map.of("schedules", List.of(Map.of("timeOfDay", "08:00", "daysOfWeek", List.of(8),
				"timeZone", "UTC")))).getStatus()).isEqualTo(400);
		assertThat(f.editor.put(path, Map.of("schedules", List.of(Map.of("timeZone", "UTC")))).getStatus())
			.isEqualTo(400);
		assertThat(f.editor.get(medications + "/due").getStatus()).isEqualTo(400);
	}

	@Test
	void medicationsAppearOnTheTimeline() throws Exception {
		addMedication(Map.of("name", "Dated medicine", "startOn", "2026-01-01"));

		String body = f.viewer.get("/api/v1/profiles/" + f.profileId + "/timeline?types=MEDICATION").getContentAsString();

		assertThat(JsonPath.<String>read(body, "$.entries[0].title")).isEqualTo("Dated medicine");
		assertThat(JsonPath.<String>read(body, "$.dateUnknown[0].title")).isEqualTo("Synthetic medicine");
	}

	@Test
	void deletingAMedicationRemovesItsSchedulesAndDoses() throws Exception {
		String scheduleId = setSchedule(daily("08:00", "UTC"));
		f.editor.post(medication + "/adherence", dose(scheduleId, UTC_DOSE, "TAKEN"));

		assertThat(f.editor.delete(medication).getStatus()).isEqualTo(204);

		assertThat(dueOn(f.editor, "2026-01-15")).isEmpty();
		assertThat(f.editor.get(medication + "/schedules").getStatus()).isEqualTo(404);
	}

}
