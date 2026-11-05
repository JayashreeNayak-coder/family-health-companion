package com.familyhealth.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

final class ProfileDtos {

	private ProfileDtos() {
	}

	/** SELF links the profile to the caller. DEPENDENT and ADULT are managed for someone without a linked account. */
	enum ProfileKind {

		SELF,
		DEPENDENT,
		ADULT

	}

	record CreateProfileRequest(
			@NotBlank @Size(max = 200) String fullName,
			@PastOrPresent LocalDate dateOfBirth,
			@NotNull ProfileKind kind) {
	}

	record UpdateProfileRequest(
			@NotBlank @Size(max = 200) String fullName,
			@PastOrPresent LocalDate dateOfBirth,
			@NotNull Long version) {
	}

	record ProfileResponse(UUID id, UUID familyId, String fullName, LocalDate dateOfBirth, boolean dependent,
			boolean linkedToAccount, AccessLevel accessLevel, Long version) {

		static ProfileResponse from(HealthProfile profile, AccessLevel level) {
			return new ProfileResponse(profile.id(), profile.familyId(), profile.fullName(), profile.dateOfBirth(),
					profile.dependent(), profile.linkedAccountId() != null, level, profile.version());
		}

	}

	record GrantRequest(@NotNull AccessLevel level) {
	}

	/** {@code linkedAccount} marks the person this profile belongs to, whose access can't be changed. */
	record GrantResponse(UUID accountId, String displayName, String email, AccessLevel level, boolean linkedAccount) {
	}

}
