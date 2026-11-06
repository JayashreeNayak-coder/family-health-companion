package com.familyhealth.extraction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExtractionDraftRepository extends JpaRepository<ExtractionDraft, UUID> {

	Optional<ExtractionDraft> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

	boolean existsByDocumentIdAndStatus(UUID documentId, ExtractionDraft.Status status);

}
