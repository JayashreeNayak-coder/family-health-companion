package com.familyhealth.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A login account. Health profiles are separate and arrive in Phase 5. */
@Entity
@Table(name = "user_account")
class UserAccount {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String email;

	/** Null for an account made with Google until the user sets a password through a reset. */
	@Column(name = "password_hash")
	private String passwordHash;

	/** Google's stable ID for the person ("sub"), once they have signed in with Google. */
	@Column(name = "google_subject")
	private String googleSubject;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private Long version;

	protected UserAccount() {
	}

	UserAccount(String email, String passwordHash, String displayName) {
		this.id = UUID.randomUUID();
		this.email = email;
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.status = AccountStatus.ACTIVE;
	}

	/** An account created by signing in with Google: no password yet. */
	static UserAccount fromGoogle(String email, String googleSubject, String displayName) {
		UserAccount account = new UserAccount(email, null, displayName);
		account.googleSubject = googleSubject;
		return account;
	}

	void changePassword(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	void linkGoogle(String googleSubject) {
		this.googleSubject = googleSubject;
	}

	String googleSubject() {
		return googleSubject;
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
		updatedAt = createdAt;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	UUID id() {
		return id;
	}

	String email() {
		return email;
	}

	String passwordHash() {
		return passwordHash;
	}

	String displayName() {
		return displayName;
	}

	boolean isActive() {
		return status == AccountStatus.ACTIVE;
	}

}
