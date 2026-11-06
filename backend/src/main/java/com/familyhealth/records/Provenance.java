package com.familyhealth.records;

import com.familyhealth.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Where a new record came from. {@link RecordService} has already checked the document is in the profile. */
public record Provenance(SourceType sourceType, UUID sourceDocumentId, UUID createdBy) {

	/**
	 * A named document implies DOCUMENTED, and DOCUMENTED needs a document. No type and no document
	 * means user reported.
	 */
	public static Provenance of(SourceType requested, UUID sourceDocumentId, UUID createdBy) {
		SourceType type = requested != null ? requested
				: sourceDocumentId != null ? SourceType.DOCUMENTED : SourceType.USER_REPORTED;
		if (type == SourceType.DOCUMENTED && sourceDocumentId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_DOCUMENT_REQUIRED",
					"A documented record must name its source document");
		}
		if (type == SourceType.USER_REPORTED && sourceDocumentId != null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SOURCE_TYPE_MISMATCH",
					"A record with a source document is documented, not user reported");
		}
		return new Provenance(type, sourceDocumentId, createdBy);
	}

}
