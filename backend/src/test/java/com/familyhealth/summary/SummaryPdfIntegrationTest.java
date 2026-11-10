package com.familyhealth.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.familyhealth.FakeSummaryGenerator;
import com.familyhealth.IntegrationTestBase;
import com.familyhealth.ProfileFixture;
import com.familyhealth.ai.SummaryGenerator;
import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Phase 18: a summary exported as a labelled PDF (docs/07, "PDF exports are labelled"). */
class SummaryPdfIntegrationTest extends IntegrationTestBase {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private FakeSummaryGenerator fake;

	private ProfileFixture f;

	private String summaries;

	@BeforeEach
	void setUp() throws Exception {
		fake.reset();
		f = new ProfileFixture(mockMvc);
		summaries = "/api/v1/profiles/" + f.profileId + "/summaries";
		f.editor.post("/api/v1/profiles/" + f.profileId + "/allergies",
				Map.of("allergen", "Sesame", "reactionAsRecorded", "Hives"));
		f.editor.post("/api/v1/profiles/" + f.profileId + "/lab-results",
				Map.of("testName", "Synthetic HbA1c", "valueNumeric", 5.8, "unit", "%", "resultOn", "2026-01-10"));
	}

	private String summaryWith(List<SummaryGenerator.Point> points) throws Exception {
		fake.willAnswer(new SummaryGenerator.Result(points, List.of("The allergy has no date."), List.of(), 100, 50));
		MockHttpServletResponse created = f.viewer.post(summaries, Map.of());
		assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
		return JsonPath.read(created.getContentAsString(), "$.id");
	}

	private static String text(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
		}
	}

	private static int pages(byte[] pdf) throws Exception {
		try (PDDocument document = Loader.loadPDF(pdf)) {
			return document.getNumberOfPages();
		}
	}

	@Test
	void theExportIsLabelledAndCitesItsRecords() throws Exception {
		String id = summaryWith(List.of(new SummaryGenerator.Point("ALLERGIES", "Allergy to Sesame, reaction Hives.",
				List.of("R1")),
				new SummaryGenerator.Point("LAB_RESULTS", "Synthetic HbA1c 5.8 % on 2026-01-10.", List.of("R2"))));

		MockHttpServletResponse response = f.viewer.get(summaries + "/" + id + "/pdf");

		assertThat(response.getStatus()).isEqualTo(200);
		assertThat(response.getContentType()).isEqualTo("application/pdf");
		assertThat(response.getHeader("Content-Disposition")).contains("attachment").contains(".pdf");
		assertThat(response.getHeader("Cache-Control")).contains("no-store");
		String text = text(response.getContentAsByteArray());
		assertThat(text).contains("Health record summary", "Child Synthetic",
				"User-generated health record summary. Not an official medical certificate.", "AI-generated",
				"It is not medical advice", "Allergy to Sesame, reaction Hives.", "Allergies", "Lab results",
				"Source: Lab result · 10 Jan 2026 · Synthetic HbA1c", "Missing information", "The allergy has no date.",
				"Limitations", "Page 1 of 1");
		assertThat(jdbc.queryForObject(
				"select count(*) from audit_event where action = 'PDF_EXPORTED' and profile_id = ?::uuid and metadata->>'summaryId' = ?",
				Long.class, f.profileId, id)).isEqualTo(1);
	}

	@Test
	void onlyPeopleWhoCanViewTheProfileCanExport() throws Exception {
		String id = summaryWith(List.of(new SummaryGenerator.Point("ALLERGIES", "Allergy to Sesame.", List.of("R1"))));
		String pdf = summaries + "/" + id + "/pdf";
		assertThat(f.outsider.get(pdf).getStatus()).isEqualTo(404);
		assertThat(f.memberWithoutGrant.get(pdf).getStatus()).isEqualTo(404);
		assertThat(f.signedOut.get(pdf).getStatus()).isEqualTo(401);
		assertThat(f.viewer.get(summaries + "/" + java.util.UUID.randomUUID() + "/pdf").getStatus()).isEqualTo(404);
	}

	@Test
	void longSummariesRunOntoMorePagesAndUnknownCharactersDontBreakTheExport() throws Exception {
		List<SummaryGenerator.Point> points = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			points.add(new SummaryGenerator.Point("LAB_RESULTS", "Synthetic HbA1c 5.8 % on 2026-01-10, statement " + i
					+ ". " + "A long sentence that wraps across the width of the page. ".repeat(3), List.of("R2")));
		}
		points.add(new SummaryGenerator.Point("ALLERGIES", "Allergy to Sesame (तिल) with a\ttab and a\nnew line.",
				List.of("R1")));
		String id = summaryWith(points);

		MockHttpServletResponse response = f.viewer.get(summaries + "/" + id + "/pdf");

		assertThat(response.getStatus()).isEqualTo(200);
		byte[] pdf = response.getContentAsByteArray();
		int pages = pages(pdf);
		assertThat(pages).isGreaterThan(1);
		String text = text(pdf);
		assertThat(text).contains("statement 39", "Allergy to Sesame (", "with a tab and a new line.",
				"Page " + pages + " of " + pages);
		assertThat(text).doesNotContain("तिल");
	}

}
