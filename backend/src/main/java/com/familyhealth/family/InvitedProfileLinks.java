package com.familyhealth.family;

import java.util.UUID;

/**
 * What invitations need from the profile module. Declared here and implemented there, because the
 * profile module already depends on this one for membership checks.
 */
public interface InvitedProfileLinks {

	/** Checks that the inviter may offer this profile, that it belongs to the family, and that it isn't linked yet. */
	void checkCanOffer(UUID inviterAccountId, UUID familyId, UUID profileId);

	String profileName(UUID profileId);

	/**
	 * Links the profile to the adult, who receives MANAGE. Everyone else's access is reduced to
	 * {@code keepAccess}, the adult's choice (DECISIONS.md, D-013).
	 */
	void link(UUID profileId, UUID adultAccountId, KeepAccess keepAccess);

	enum KeepAccess {

		NONE,
		VIEW,
		EDIT

	}

}
