package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Where a new record came from, already checked. */
public record Provenance(SourceType sourceType, UUID sourceDocumentId, UUID createdBy) {

	/**
	 * Defaults to user reported. Documents don't exist until Phase 11, so no document ID can be
	 * valid yet, and accepting one would store a reference to nothing. Phase 11 replaces the
	 * second check with a lookup in the record's own profile.
	 */
	public static Provenance of(SourceType requested, UUID sourceDocumentId, UUID createdBy) {
		SourceType type = requested != null ? requested : SourceType.USER_REPORTED;
		if (type == SourceType.DOCUMENTED && sourceDocumentId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_DOCUMENT_REQUIRED",
					"A documented record must name its source document");
		}
		if (sourceDocumentId != null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "SOURCE_DOCUMENT_NOT_FOUND", "Source document not found");
		}
		return new Provenance(type, null, createdBy);
	}

}
