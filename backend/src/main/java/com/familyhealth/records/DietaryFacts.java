package com.familyhealth.records;

import com.familyhealth.common.FieldValidationException;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only profile data health-aware meal suggestions may use (docs/06 section 8.2): allergen
 * names, dietary restriction and preference text, and the names of conditions the user chose.
 * Reactions, notes, dates, documents, names, and everything else stay out.
 */
@Service
public class DietaryFacts {

	/** A restriction or preference, as the user recorded it. */
	public record Restriction(String kind, String description) {
	}

	public record Facts(List<String> allergens, List<Restriction> restrictions, List<String> conditions) {
	}

	private final ProfileAccess access;

	private final AllergyRepository allergies;

	private final DietaryRestrictionRepository restrictions;

	private final ConditionRepository conditions;

	DietaryFacts(ProfileAccess access, AllergyRepository allergies, DietaryRestrictionRepository restrictions,
			ConditionRepository conditions) {
		this.access = access;
		this.allergies = allergies;
		this.restrictions = restrictions;
		this.conditions = conditions;
	}

	/**
	 * Needs VIEW on the profile. Every condition ID must belong to the profile, so another
	 * profile's records can't be pulled in by ID.
	 */
	@Transactional(readOnly = true)
	public Facts load(UUID accountId, UUID profileId, Collection<UUID> conditionIds) {
		access.require(accountId, profileId, AccessLevel.VIEW);
		Set<UUID> wanted = new HashSet<>(conditionIds);
		List<Condition> chosen = conditions.findByProfileId(profileId)
			.stream()
			.filter(condition -> wanted.contains(condition.id()))
			.toList();
		if (chosen.size() != wanted.size()) {
			throw new FieldValidationException(List.of(new FieldValidationException.FieldError("conditionIds",
					"must be conditions recorded for this profile")));
		}
		return new Facts(
				allergies.findByProfileId(profileId).stream().map(Allergy::allergen).distinct().toList(),
				restrictions.findByProfileId(profileId)
					.stream()
					.map(item -> new Restriction(item.kind().name(), item.description()))
					.toList(),
				chosen.stream().map(Condition::name).distinct().toList());
	}

}
