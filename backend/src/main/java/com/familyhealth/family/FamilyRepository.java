package com.familyhealth.family;

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

}
