package com.familyhealth.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** One emailed reset link. Only the token's hash is stored, and a link works once. */
@Entity
@Table(name = "password_reset_token")
class PasswordResetToken {

	@Id
	private UUID id;

	@Column(name = "user_account_id", nullable = false, updatable = false)
	private UUID accountId;

	@Column(name = "token_hash", nullable = false, updatable = false)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	protected PasswordResetToken() {
	}

	PasswordResetToken(UUID accountId, String tokenHash, Instant createdAt, Instant expiresAt) {
		this.id = UUID.randomUUID();
		this.accountId = accountId;
		this.tokenHash = tokenHash;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
	}

	UUID accountId() {
		return accountId;
	}

	boolean isUsable(Instant now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	void markUsed(Instant now) {
		this.usedAt = now;
	}

}
