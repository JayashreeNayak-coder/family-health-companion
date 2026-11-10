package com.familyhealth.emergencycard;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmergencyCardRepository extends JpaRepository<EmergencyCard, UUID> {

	Optional<EmergencyCard> findByProfileId(UUID profileId);

	Optional<EmergencyCard> findByTokenHashAndEnabledTrue(String tokenHash);

}
