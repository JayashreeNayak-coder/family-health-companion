package com.familyhealth.extraction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ExtractionJobRepository extends JpaRepository<ExtractionJob, UUID> {

	Optional<ExtractionJob> findFirstByDocumentIdOrderByCreatedAtDesc(UUID documentId);

	boolean existsByDocumentIdAndStatusIn(UUID documentId, List<ExtractionJob.Status> statuses);

	/** Claims the oldest queued job. Another worker skips it instead of waiting. */
	@Query(value = "SELECT * FROM extraction_job WHERE status = 'QUEUED' ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
			nativeQuery = true)
	Optional<ExtractionJob> claimNext();

	/** Jobs left in progress by a server that stopped mid-call. */
	List<ExtractionJob> findByStatusAndStartedAtBefore(ExtractionJob.Status status, Instant before);

}
