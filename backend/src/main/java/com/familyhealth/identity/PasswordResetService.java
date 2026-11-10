package com.familyhealth.identity;

import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditOutcome;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.SecureTokens;
import com.familyhealth.mail.EmailSender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Forgot password" (docs/06 section 2). A request always gets the same answer, so it never shows
 * whether an email has an account. A link works once, expires, and using it signs the account out
 * everywhere.
 */
@Service
class PasswordResetService {

	private final UserAccountRepository accounts;

	private final PasswordResetTokenRepository tokens;

	private final PasswordEncoder passwordEncoder;

	private final EmailSender email;

	private final AuditService audit;

	private final LoginRateLimiter rateLimiter;

	private final FindByIndexNameSessionRepository<? extends Session> sessions;

	private final Clock clock;

	private final String publicUrl;

	private final Duration validFor;

	private final int maxPerHour;

	PasswordResetService(UserAccountRepository accounts, PasswordResetTokenRepository tokens,
			PasswordEncoder passwordEncoder, EmailSender email, AuditService audit, LoginRateLimiter rateLimiter,
			FindByIndexNameSessionRepository<? extends Session> sessions, Clock clock,
			@Value("${app.public-url}") String publicUrl,
			@Value("${app.password-reset.valid-for}") Duration validFor,
			@Value("${app.password-reset.max-per-account-per-hour}") int maxPerHour) {
		this.accounts = accounts;
		this.tokens = tokens;
		this.passwordEncoder = passwordEncoder;
		this.email = email;
		this.audit = audit;
		this.rateLimiter = rateLimiter;
		this.sessions = sessions;
		this.clock = clock;
		this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
		this.validFor = validFor;
		this.maxPerHour = maxPerHour;
	}

	/**
	 * Sends a link when the email belongs to an active account, within a small hourly limit.
	 * Otherwise does nothing. The caller can't tell which happened.
	 */
	@Transactional
	public void request(String rawEmail) {
		Instant now = clock.instant();
		Optional<UserAccount> found = accounts.findByEmail(AccountDirectory.normalizeEmail(rawEmail));
		if (found.isEmpty() || !found.get().isActive()) {
			return;
		}
		UserAccount account = found.get();
		if (tokens.countByAccountIdAndCreatedAtAfter(account.id(), now.minus(Duration.ofHours(1))) >= maxPerHour) {
			return;
		}
		// Only the newest link works.
		tokens.findByAccountIdAndUsedAtIsNull(account.id()).forEach(old -> old.markUsed(now));
		String token = SecureTokens.generate();
		tokens.save(new PasswordResetToken(account.id(), SecureTokens.hash(token), now, now.plus(validFor)));
		audit.record(AuditAction.PASSWORD_RESET_REQUESTED, AuditOutcome.SUCCESS, null, AuditService.TARGET_ACCOUNT,
				account.id(), null);
		// The token travels after "#", so it never reaches a server log or a Referer header.
		email.send(account.email(), "Reset your Family Health Companion password", """
				Hello %s,

				Someone asked to reset the password for this Family Health Companion account. To choose a new password, open this link within %d minutes:

				%s/reset-password#token=%s

				The link works once. If you didn't ask for this, you can ignore this email and your password stays the same.
				""".formatted(account.displayName(), validFor.toMinutes(), publicUrl, token));
	}

	/** Sets the new password, uses up the link, and ends every session of the account. */
	@Transactional
	public void confirm(String token, String newPassword) {
		Instant now = clock.instant();
		PasswordResetToken reset = tokens.findByTokenHash(SecureTokens.hash(token))
			.filter(found -> found.isUsable(now))
			.orElseThrow(PasswordResetService::invalidLink);
		UserAccount account = accounts.findById(reset.accountId())
			.filter(UserAccount::isActive)
			.orElseThrow(PasswordResetService::invalidLink);
		account.changePassword(passwordEncoder.encode(newPassword));
		reset.markUsed(now);
		tokens.findByAccountIdAndUsedAtIsNull(account.id()).forEach(other -> other.markUsed(now));
		sessions.findByPrincipalName(account.id().toString()).keySet().forEach(sessions::deleteById);
		rateLimiter.recordSuccess(account.email());
		audit.record(AuditAction.PASSWORD_RESET, AuditOutcome.SUCCESS, account.id(), AuditService.TARGET_ACCOUNT,
				account.id(), null);
	}

	private static ApiException invalidLink() {
		return new ApiException(HttpStatus.BAD_REQUEST, "RESET_LINK_INVALID",
				"This reset link is invalid or has expired. Ask for a new one.");
	}

}
