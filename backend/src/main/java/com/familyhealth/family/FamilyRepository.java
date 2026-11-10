package com.familyhealth.family;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FamilyRepository extends JpaRepository<Family, UUID> {

	@Query("""
			select f from Family f
			where f.id in (select m.familyId from FamilyMembership m where m.userAccountId = :accountId)
			""")
	Page<Family> findForAccount(@Param("accountId") UUID accountId, Pageable pageable);

	/** Every other family that any of these accounts belongs to, for the name check. */
	@Query("""
			select f from Family f
			where f.id <> :excluded
			and f.id in (select m.familyId from FamilyMembership m where m.userAccountId in :accountIds)
			""")
	List<Family> findOthersForAccounts(@Param("accountIds") Collection<UUID> accountIds,
			@Param("excluded") UUID excluded);

}
