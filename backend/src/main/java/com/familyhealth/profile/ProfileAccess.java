package com.familyhealth.profile;

import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * The single access check. Every operation on a profile or its records calls this first
 * (docs/06 section 3). No grant returns 404, so a profile's existence stays hidden; too low a
 * level on a visible profile returns 403.
 */
@Service
public class ProfileAccess {

	private final ProfileAccessGrantRepository grants;

	private final AuditService audit;

	ProfileAccess(ProfileAccessGrantRepository grants, AuditService audit) {
		this.grants = grants;
		this.audit = audit;
	}

	/** Returns the caller's actual level, which may be higher than the one required. */
	public AccessLevel require(UUID accountId, UUID profileId, AccessLevel required) {
		AccessLevel held = grants.findByProfileIdAndUserAccountId(profileId, accountId)
			.map(ProfileAccessGrant::level)
			.orElse(null);
		if (held == null) {
			audit.recordProfileDenied(accountId, profileId, "NO_GRANT");
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
		}
		if (!held.includes(required)) {
			audit.recordProfileDenied(accountId, profileId, "LEVEL_TOO_LOW");
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Your access level doesn't allow this");
		}
		return held;
	}

}
