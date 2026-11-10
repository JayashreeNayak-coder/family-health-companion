package com.familyhealth.emergencycard;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lets the emergency card check the records its items were copied from, without depending on the
 * packages that own them. Implemented next to each record type's repository.
 */
public interface CardSources {

	/** The item types this implementation answers for. */
	Set<CardItemType> types();

	/** When the record last changed, or empty if this profile has no such record of that type. */
	Optional<Instant> lastChanged(CardItemType type, UUID profileId, UUID recordId);

}
