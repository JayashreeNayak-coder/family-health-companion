package com.familyhealth.profile;

import java.util.UUID;

/**
 * Implemented by the document module. Foreign keys delete a profile's rows, but stored files need
 * deleting too. Called before the profile is deleted, in the same transaction.
 */
public interface ProfileFiles {

	/** Removes the files once the transaction commits, and keeps them if it rolls back. */
	void deleteAllAfterCommit(UUID profileId);

}
