package com.familyhealth.family;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FamilyMembershipRepository extends JpaRepository<FamilyMembership, UUID> {

	Optional<FamilyMembership> findByFamilyIdAndUserAccountId(UUID familyId, UUID userAccountId);

	List<FamilyMembership> findByFamilyId(UUID familyId);

	List<FamilyMembership> findByUserAccountIdAndFamilyIdIn(UUID userAccountId, List<UUID> familyIds);

}
