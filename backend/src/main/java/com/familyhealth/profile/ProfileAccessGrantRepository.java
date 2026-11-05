package com.familyhealth.profile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ProfileAccessGrantRepository extends JpaRepository<ProfileAccessGrant, UUID> {

	Optional<ProfileAccessGrant> findByProfileIdAndUserAccountId(UUID profileId, UUID userAccountId);

	List<ProfileAccessGrant> findByProfileId(UUID profileId);

	long countByProfileIdAndLevel(UUID profileId, AccessLevel level);

}
