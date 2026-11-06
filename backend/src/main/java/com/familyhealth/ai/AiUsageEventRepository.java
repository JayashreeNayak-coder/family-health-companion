package com.familyhealth.ai;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AiUsageEventRepository extends JpaRepository<AiUsageEvent, UUID> {

	long countByUserAccountIdAndFeatureAndOutcomeAndOccurredAtAfter(UUID userAccountId, String feature, String outcome,
			Instant after);

	long countByFeatureAndOutcomeAndOccurredAtAfter(String feature, String outcome, Instant after);

}
