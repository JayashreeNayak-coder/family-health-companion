package com.familyhealth.family;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface FamilyInvitationRepository extends JpaRepository<FamilyInvitation, UUID> {

	/** Locked, so two simultaneous accepts of one invitation can't both succeed. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<FamilyInvitation> findByTokenHash(String tokenHash);

}
