package com.familyhealth.records;

import com.familyhealth.document.DocumentReferences;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Clears a deleted document from the records confirmed from it, and marks when that happened, so
 * the UI shows "source document deleted" (docs/04 section 8). A table added later but missing here
 * makes the delete fail on its foreign key, rather than leave a dangling reference.
 */
@Component
class DocumentReferenceCleaner implements DocumentReferences {

	private static final List<String> HEALTH_RECORD_TABLES = List.of("condition", "allergy", "dietary_restriction",
			"doctor_visit", "symptom_entry", "medication", "lab_result");

	private final EntityManager entityManager;

	private final Clock clock;

	DocumentReferenceCleaner(EntityManager entityManager, Clock clock) {
		this.entityManager = entityManager;
		this.clock = clock;
	}

	@Override
	public void documentDeleted(UUID profileId, UUID documentId) {
		OffsetDateTime now = OffsetDateTime.now(clock);
		for (String table : HEALTH_RECORD_TABLES) {
			entityManager
				.createNativeQuery("UPDATE " + table + " SET source_document_id = NULL, source_document_deleted_at = ?1, "
						+ "updated_at = ?1, version = version + 1 WHERE profile_id = ?2 AND source_document_id = ?3")
				.setParameter(1, now)
				.setParameter(2, profileId)
				.setParameter(3, documentId)
				.executeUpdate();
		}
		// Records already loaded in this transaction must not write their old reference back.
		entityManager.clear();
	}

}
