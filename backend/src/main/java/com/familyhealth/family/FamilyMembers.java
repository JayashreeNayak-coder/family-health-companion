package com.familyhealth.family;

import com.familyhealth.common.ApiException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What other modules may ask about family membership. No access means 404, so a family's existence stays hidden. */
@Service
public class FamilyMembers {

	private final FamilyMembershipRepository memberships;

	FamilyMembers(FamilyMembershipRepository memberships) {
		this.memberships = memberships;
	}

	@Transactional(readOnly = true)
	public boolean isMember(UUID familyId, UUID accountId) {
		return role(familyId, accountId).isPresent();
	}

	@Transactional(readOnly = true)
	public void requireMember(UUID familyId, UUID accountId) {
		role(familyId, accountId).orElseThrow(FamilyMembers::notFound);
	}

	@Transactional(readOnly = true)
	public void requireManager(UUID familyId, UUID accountId) {
		FamilyRole role = role(familyId, accountId).orElseThrow(FamilyMembers::notFound);
		if (role != FamilyRole.MANAGER) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a family manager can do this");
		}
	}

	private Optional<FamilyRole> role(UUID familyId, UUID accountId) {
		return memberships.findByFamilyIdAndUserAccountId(familyId, accountId).map(FamilyMembership::role);
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
	}

}
