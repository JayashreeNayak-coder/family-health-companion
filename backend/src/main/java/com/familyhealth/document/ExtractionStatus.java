package com.familyhealth.document;

/** Only NOT_REQUESTED is used until AI extraction arrives in Phase 12. */
public enum ExtractionStatus {

	NOT_REQUESTED,
	QUEUED,
	PROCESSING,
	READY_FOR_REVIEW,
	FAILED,
	REVIEWED

}
