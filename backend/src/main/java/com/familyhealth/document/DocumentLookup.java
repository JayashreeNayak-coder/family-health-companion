package com.familyhealth.document;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What other modules may ask about documents. Callers have already checked access to the profile,
 * and every lookup is limited to that profile.
 */
public interface DocumentLookup {

	boolean existsInProfile(UUID profileId, UUID documentId);

	Map<UUID, DocumentSummary> summaries(UUID profileId, Collection<UUID> documentIds);

	Optional<DocumentSummary> find(UUID profileId, UUID documentId);

	/** The original's bytes, for a background job that has already been authorised. */
	Optional<StoredFile> readContent(UUID documentId);

	void setExtractionStatus(UUID documentId, ExtractionStatus status);

	record StoredFile(byte[] content, String contentType) {
	}

}
