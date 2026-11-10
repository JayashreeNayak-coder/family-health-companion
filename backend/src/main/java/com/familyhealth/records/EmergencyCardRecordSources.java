package com.familyhealth.records;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.familyhealth.common.BaseEntity;
import com.familyhealth.emergencycard.CardItemType;
import com.familyhealth.emergencycard.CardSources;
import org.springframework.stereotype.Component;

/** Allergies, conditions, and emergency contacts, as sources of emergency card lines. */
@Component
class EmergencyCardRecordSources implements CardSources {

	private final AllergyRepository allergies;

	private final ConditionRepository conditions;

	private final EmergencyContactRepository contacts;

	EmergencyCardRecordSources(AllergyRepository allergies, ConditionRepository conditions,
			EmergencyContactRepository contacts) {
		this.allergies = allergies;
		this.conditions = conditions;
		this.contacts = contacts;
	}

	@Override
	public Set<CardItemType> types() {
		return Set.of(CardItemType.ALLERGY, CardItemType.CONDITION, CardItemType.EMERGENCY_CONTACT);
	}

	@Override
	public Optional<Instant> lastChanged(CardItemType type, UUID profileId, UUID recordId) {
		ProfileRecordRepository<? extends ProfileRecord> repository = switch (type) {
			case ALLERGY -> allergies;
			case CONDITION -> conditions;
			case EMERGENCY_CONTACT -> contacts;
			default -> throw new IllegalArgumentException("Not a record type here: " + type);
		};
		return repository.findByIdAndProfileId(recordId, profileId).map(BaseEntity::updatedAt);
	}

}
