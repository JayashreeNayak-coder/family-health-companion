package com.familyhealth.family;

import com.familyhealth.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A single-use invitation for one email address. Only the token's hash is stored. */
@Entity
@Table(name = "family_invitation")
class FamilyInvitation extends BaseEntity {

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	/** Set when accepting should also link this profile to the invited adult's account. */
	@Column(name = "profile_id")
	private UUID profileId;

	@Column(name = "invited_email", nullable = false)
	private String invitedEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FamilyRole role;

	@Column(name = "token_hash", nullable = false)
	private String tokenHash;

	@Column(name = "invited_by", nullable = false)
	private UUID invitedBy;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "accepted_at")
	private Instant acceptedAt;

	@Column(name = "accepted_by")
	private UUID acceptedBy;

	protected FamilyInvitation() {
	}

	FamilyInvitation(UUID familyId, UUID profileId, String invitedEmail, FamilyRole role, String tokenHash,
			UUID invitedBy, Instant expiresAt) {
		this.familyId = familyId;
		this.profileId = profileId;
		this.invitedEmail = invitedEmail;
		this.role = role;
		this.tokenHash = tokenHash;
		this.invitedBy = invitedBy;
		this.expiresAt = expiresAt;
	}

	boolean isOpenFor(String email, Instant now) {
		return acceptedAt == null && now.isBefore(expiresAt) && invitedEmail.equals(email);
	}

	void accept(UUID accountId, Instant now) {
		acceptedBy = accountId;
		acceptedAt = now;
	}

	UUID familyId() {
		return familyId;
	}

	UUID profileId() {
		return profileId;
	}

	String invitedEmail() {
		return invitedEmail;
	}

	FamilyRole role() {
		return role;
	}

	Instant expiresAt() {
		return expiresAt;
	}

}
