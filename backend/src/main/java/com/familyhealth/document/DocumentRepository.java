package com.familyhealth.document;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Every lookup filters on the profile, so an ID from another profile can't return a document. */
interface DocumentRepository extends JpaRepository<Document, UUID> {

	Page<Document> findByProfileId(UUID profileId, Pageable pageable);

	Optional<Document> findByIdAndProfileId(UUID id, UUID profileId);

	boolean existsByIdAndProfileId(UUID id, UUID profileId);

	List<Document> findByProfileIdAndIdIn(UUID profileId, Collection<UUID> ids);

	@Query("select d.storageKey from Document d where d.profileId = :profileId")
	List<String> findStorageKeysByProfileId(UUID profileId);

}
