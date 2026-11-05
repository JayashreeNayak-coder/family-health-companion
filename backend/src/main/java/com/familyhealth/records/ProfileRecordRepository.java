package com.familyhealth.records;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** Every lookup filters on the profile, so an ID from another profile can't return data. */
@NoRepositoryBean
public interface ProfileRecordRepository<E extends ProfileRecord> extends JpaRepository<E, UUID> {

	Page<E> findByProfileId(UUID profileId, Pageable pageable);

	List<E> findByProfileId(UUID profileId);

	Optional<E> findByIdAndProfileId(UUID id, UUID profileId);

}
