package com.familyhealth.extraction;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ExtractionDtos {

	/** Every AI response carries these labels (docs/05 section 7). */
	static final String AI_GENERATED = "AI_GENERATED";

	static final String DISCLAIMER = "AI-generated draft. Check every value against the document. "
			+ "Nothing is saved until you approve it, and this is not medical advice.";

	private ExtractionDtos() {
	}

	record JobResponse(UUID id, UUID documentId, String status, String errorCode, int attempts, Instant requestedAt,
			Instant finishedAt) {

		static JobResponse from(ExtractionJob job) {
			return new JobResponse(job.id(), job.documentId(), job.status().name(), job.errorCode(), job.attempts(),
					job.createdAt(), job.finishedAt());
		}

	}

	record ItemResponse(UUID id, String type, Map<String, Object> payload, String status, UUID confirmedRecordId,
			Long version) {
	}

	record DraftResponse(UUID id, UUID documentId, String status, String schemaVersion, String contentType,
			String disclaimer, List<String> limitations, List<ItemResponse> items) {
	}

	record CorrectRequest(@NotNull Map<String, Object> payload, @NotNull Long version) {
	}

	/** {@code payload} is optional: when given, it replaces the draft's values before approval. */
	record ApproveRequest(Map<String, Object> payload, @NotNull Long version) {
	}

	record RejectRequest(@NotNull Long version) {
	}

}
