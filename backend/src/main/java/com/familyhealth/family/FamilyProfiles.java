package com.familyhealth.family;

import java.util.UUID;

/**
 * What deleting a family needs from the profile module. Declared here and implemented there,
 * because the profile module already depends on this one, as with {@link InvitedProfileLinks}.
 */
public interface FamilyProfiles {

	/**
	 * Deletes every profile in the family, with their records and, once the transaction commits,
	 * their stored files. Audits each deletion. Joins the caller's transaction.
	 */
	void deleteAllInFamily(UUID accountId, UUID familyId);

}
