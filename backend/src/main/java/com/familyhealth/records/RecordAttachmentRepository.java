package com.familyhealth.records;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RecordAttachmentRepository extends JpaRepository<RecordAttachment, UUID> {

	List<RecordAttachment> findByProfileIdAndRecordTypeOrderByCreatedAtAscIdAsc(UUID profileId, RecordType recordType);

	List<RecordAttachment> findByProfileIdAndRecordTypeAndRecordIdOrderByCreatedAtAscIdAsc(UUID profileId,
			RecordType recordType, UUID recordId);

	Optional<RecordAttachment> findByIdAndProfileId(UUID id, UUID profileId);

	boolean existsByRecordTypeAndRecordIdAndDocumentId(RecordType recordType, UUID recordId, UUID documentId);

	@Modifying
	@Query("delete from RecordAttachment a where a.recordType = :recordType and a.recordId = :recordId")
	void deleteForRecord(RecordType recordType, UUID recordId);

}
