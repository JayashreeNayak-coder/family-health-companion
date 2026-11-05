package com.familyhealth.records;

import java.util.UUID;

/** The provenance a client may state when creating a record. It can't be changed afterwards. */
public interface HealthRecordRequest extends VersionedRequest {

	SourceType sourceType();

	UUID sourceDocumentId();

	default Provenance provenance(UUID createdBy) {
		return Provenance.of(sourceType(), sourceDocumentId(), createdBy);
	}

}
