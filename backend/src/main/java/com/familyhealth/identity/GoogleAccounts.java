package com.familyhealth.identity;

import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditOutcome;
import com.familyhealth.audit.AuditService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds or creates the account for a Google sign-in (DECISIONS.md, D-028). An account already
 * linked to the Google ID is used. Otherwise, a Google-verified email that matches an account
 * links to it, and a new email creates an account with no password. An unverified email never
 * matches or creates anything.
 */
@Service
class GoogleAccounts {

	/** Why a Google sign-in was refused. Safe to show; never includes the email. */
	enum Refusal {

		EMAIL_NOT_VERIFIED, ACCOUNT_DISABLED, LINKED_TO_ANOTHER_GOOGLE_ACCOUNT, MISSING_DETAILS

	}

	sealed interface Outcome permits SignedIn, Refused {
	}

	record SignedIn(UUID accountId) implements Outcome {
	}

	record Refused(Refusal reason) implements Outcome {
	}

	private static final int MAX_NAME = 100;

	private final UserAccountRepository accounts;

	private final AuditService audit;

	GoogleAccounts(UserAccountRepository accounts, AuditService audit) {
		this.accounts = accounts;
		this.audit = audit;
	}

	@Transactional
	public Outcome signIn(String subject, String rawEmail, boolean emailVerified, String name) {
		if (subject == null || subject.isBlank() || rawEmail == null || rawEmail.isBlank()) {
			return refused(null, Refusal.MISSING_DETAILS);
		}
		Optional<UserAccount> linked = accounts.findByGoogleSubject(subject);
		if (linked.isPresent()) {
			return signedIn(linked.get());
		}
		if (!emailVerified) {
			return refused(null, Refusal.EMAIL_NOT_VERIFIED);
		}
		String email = AccountDirectory.normalizeEmail(rawEmail);
		Optional<UserAccount> byEmail = accounts.findByEmail(email);
		if (byEmail.isPresent()) {
			UserAccount account = byEmail.get();
			if (account.googleSubject() != null) {
				return refused(account, Refusal.LINKED_TO_ANOTHER_GOOGLE_ACCOUNT);
			}
			if (!account.isActive()) {
				return refused(account, Refusal.ACCOUNT_DISABLED);
			}
			account.linkGoogle(subject);
			audit.record(AuditAction.GOOGLE_ACCOUNT_LINKED, AuditOutcome.SUCCESS, account.id(),
					AuditService.TARGET_ACCOUNT, account.id(), null);
			return signedIn(account);
		}
		UserAccount created = accounts.saveAndFlush(UserAccount.fromGoogle(email, subject, displayName(name, email)));
		audit.record(AuditAction.ACCOUNT_REGISTERED, AuditOutcome.SUCCESS, created.id(), AuditService.TARGET_ACCOUNT,
				created.id(), Map.of("method", "GOOGLE"));
		return signedIn(created);
	}

	private Outcome signedIn(UserAccount account) {
		if (!account.isActive()) {
			return refused(account, Refusal.ACCOUNT_DISABLED);
		}
		audit.record(AuditAction.LOGIN, AuditOutcome.SUCCESS, account.id(), AuditService.TARGET_ACCOUNT, account.id(),
				Map.of("method", "GOOGLE"));
		return new SignedIn(account.id());
	}

	private Outcome refused(UserAccount account, Refusal reason) {
		UUID id = account == null ? null : account.id();
		audit.record(AuditAction.LOGIN, AuditOutcome.FAILURE, null, AuditService.TARGET_ACCOUNT, id,
				Map.of("method", "GOOGLE", "reason", reason.name()));
		return new Refused(reason);
	}

	private static String displayName(String name, String email) {
		String chosen = name == null || name.isBlank() ? email.substring(0, email.indexOf('@')) : name.strip();
		return chosen.length() > MAX_NAME ? chosen.substring(0, MAX_NAME) : chosen;
	}

}
