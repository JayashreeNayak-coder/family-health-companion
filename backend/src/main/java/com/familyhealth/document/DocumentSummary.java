package com.familyhealth.document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A document's metadata as the API shows it. The storage key and hash are never exposed. */
public record DocumentSummary(UUID id, UUID profileId, String originalFilename, String contentType, long sizeBytes,
		DocumentCategory category, LocalDate documentOn, String description, ExtractionStatus extractionStatus,
		Instant uploadedAt) {

	static DocumentSummary from(Document document) {
		return new DocumentSummary(document.id(), document.profileId(), document.originalFilename(),
				document.contentType(), document.sizeBytes(), document.category(), document.documentOn(),
				document.description(), document.extractionStatus(), document.createdAt());
	}

}
