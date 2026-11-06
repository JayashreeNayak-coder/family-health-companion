package com.familyhealth.extraction;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ExtractionDraftItemRepository extends JpaRepository<ExtractionDraftItem, UUID> {

	List<ExtractionDraftItem> findByDraftIdOrderByPosition(UUID draftId);

	Optional<ExtractionDraftItem> findByIdAndDraftId(UUID id, UUID draftId);

}
