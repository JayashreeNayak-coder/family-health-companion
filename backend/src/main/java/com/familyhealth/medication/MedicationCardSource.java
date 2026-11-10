package com.familyhealth.medication;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.familyhealth.common.BaseEntity;
import com.familyhealth.emergencycard.CardItemType;
import com.familyhealth.emergencycard.CardSources;
import org.springframework.stereotype.Component;

/** Medications, as sources of emergency card lines. */
@Component
class MedicationCardSource implements CardSources {

	private final MedicationRepository medications;

	MedicationCardSource(MedicationRepository medications) {
		this.medications = medications;
	}

	@Override
	public Set<CardItemType> types() {
		return Set.of(CardItemType.MEDICATION);
	}

	@Override
	public Optional<Instant> lastChanged(CardItemType type, UUID profileId, UUID recordId) {
		return medications.findByIdAndProfileId(recordId, profileId).map(BaseEntity::updatedAt);
	}

}
