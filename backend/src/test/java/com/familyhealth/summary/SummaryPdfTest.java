package com.familyhealth.summary;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import com.familyhealth.records.SourceType;
import com.familyhealth.summary.SummaryDtos.Conflict;
import com.familyhealth.summary.SummaryDtos.Point;
import com.familyhealth.summary.SummaryDtos.Response;
import com.familyhealth.summary.SummaryDtos.Source;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class SummaryPdfTest {

	private static Response summary() {
		Source allergy = new Source("R1", "ALLERGY", UUID.randomUUID(), null, "Penicillin (synthetic)", "Rash",
				SourceType.USER_REPORTED);
		Source lab = new Source("R2", "LAB_RESULT", UUID.randomUUID(), LocalDate.of(2026, 1, 10), "HbA1c", "5.8 %",
				SourceType.DOCUMENTED);
		Source lab2 = new Source("R3", "LAB_RESULT", UUID.randomUUID(), LocalDate.of(2026, 3, 10), "HbA1c", "5.7 %",
				SourceType.DOCUMENTED);
		return new Response(UUID.randomUUID(), UUID.randomUUID(), "AI_GENERATED", "summaries-v1", "fake-model",
				LocalDate.of(2025, 9, 1), LocalDate.of(2026, 3, 31), Instant.parse("2026-11-10T10:00:00Z"),
				List.of(new Point("ALLERGIES", "Allergy to penicillin, reaction recorded as rash.", List.of(allergy)),
						new Point("LAB_RESULTS", "HbA1c was 5.8 % on 10 Jan 2026 and 5.7 % on 10 Mar 2026.",
								List.of(lab, lab2))),
				List.of("The allergy has no date."),
				List.of(new Conflict("Two HbA1c results differ on dates close together.", List.of(lab, lab2))),
				List.of("This summary only restates recorded information."), SummaryDtos.DISCLAIMER);
	}

	@Test
	void rendersALabelledA4SummaryWithSourcesAndAPageFooter() throws Exception {
		byte[] pdf = SummaryPdf.render(summary(), "Ravi Demo");
		// A sample to look at by eye. target/ is build output and never committed.
		Files.write(Path.of("target", "summary-sample.pdf"), pdf);

		try (PDDocument document = Loader.loadPDF(pdf)) {
			assertThat(document.getNumberOfPages()).isEqualTo(1);
			assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(595.27563f);
			assertThat(document.getDocumentInformation().getSubject()).isEqualTo(SummaryPdf.LABEL);
			String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
			assertThat(text).contains("Ravi Demo", "Period: 1 Sep 2025 to 31 Mar 2026", SummaryPdf.LABEL,
					"Source: Allergy · date unknown · Penicillin (synthetic) · Rash · user reported",
					"Source: Lab result · 10 Jan 2026 · HbA1c · 5.8 % · from document", "Records that disagree",
					"Missing information", "Limitations", "Page 1 of 1");
		}
	}

}
