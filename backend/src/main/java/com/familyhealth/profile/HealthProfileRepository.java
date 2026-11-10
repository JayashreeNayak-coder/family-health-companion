package com.familyhealth.profile;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface HealthProfileRepository extends JpaRepository<HealthProfile, UUID> {

	boolean existsByFamilyIdAndLinkedAccountId(UUID familyId, UUID linkedAccountId);

	List<HealthProfile> findByFamilyId(UUID familyId);

	/** Serializes changes to a profile's grants, so "at least one MANAGE" can't be broken by a race. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from HealthProfile p where p.id = :id")
	Optional<HealthProfile> findByIdForUpdate(@Param("id") UUID id);

	@Query(value = """
			select p, g.level
			from HealthProfile p, ProfileAccessGrant g
			where g.profileId = p.id and g.userAccountId = :accountId
			order by p.fullName, p.id
			""", countQuery = "select count(g) from ProfileAccessGrant g where g.userAccountId = :accountId")
	Page<Object[]> findForAccount(@Param("accountId") UUID accountId, Pageable pageable);

}
