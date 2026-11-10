package com.familyhealth.family;

import com.familyhealth.family.InvitedProfileLinks.KeepAccess;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class FamilyDtos {

	private FamilyDtos() {
	}

	record CreateFamilyRequest(@NotBlank @Size(max = 100) String name) {
	}

	/** Renames a family. Managers only. */
	record RenameFamilyRequest(@NotBlank @Size(max = 100) String name, @NotNull Long version) {
	}

	/** {@code photoUpdatedAt} is null without a photo; it changes whenever the photo does. */
	record FamilySummary(UUID id, String name, FamilyRole role, Instant photoUpdatedAt, Long version) {
	}

	record FamilyMember(UUID accountId, String displayName, String email, FamilyRole role) {
	}

	record FamilyResponse(UUID id, String name, FamilyRole role, List<FamilyMember> members, Instant photoUpdatedAt,
			Long version) {
	}

	/** {@code role} defaults to MEMBER. {@code profileId} offers that profile to the invited adult. */
	record CreateInvitationRequest(@NotBlank @Email @Size(max = 254) String email, FamilyRole role, UUID profileId) {
	}

	/** The only time the token is returned. */
	record InvitationCreated(UUID id, String email, FamilyRole role, UUID profileId, Instant expiresAt, String token) {
	}

	record InvitationPreview(String familyName, FamilyRole role, String profileName) {
	}

	/** {@code keepAccess} is required when the invitation links a profile. */
	record AcceptInvitationRequest(KeepAccess keepAccess) {
	}

}
