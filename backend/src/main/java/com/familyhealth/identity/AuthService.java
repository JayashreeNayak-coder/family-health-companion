package com.familyhealth.identity;

import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditOutcome;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.RateLimitedException;
import com.familyhealth.identity.AuthDtos.AccountResponse;
import com.familyhealth.identity.AuthDtos.LoginRequest;
import com.familyhealth.identity.AuthDtos.RegisterRequest;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuthService {

	private final UserAccountRepository accounts;

	private final PasswordEncoder passwordEncoder;

	private final LoginRateLimiter rateLimiter;

	private final AuditService audit;

	/** Checked when an email is unknown, so unknown and known emails take about the same time. */
	private final String dummyHash;

	AuthService(UserAccountRepository accounts, PasswordEncoder passwordEncoder, LoginRateLimiter rateLimiter,
			AuditService audit) {
		this.accounts = accounts;
		this.passwordEncoder = passwordEncoder;
		this.rateLimiter = rateLimiter;
		this.audit = audit;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional
	public AccountResponse register(RegisterRequest request) {
		String email = normalizeEmail(request.email());
		if (accounts.existsByEmail(email)) {
			throw emailTaken();
		}
		UserAccount account = new UserAccount(email, passwordEncoder.encode(request.password()),
				request.displayName().trim());
		try {
			accounts.saveAndFlush(account);
		}
		catch (DataIntegrityViolationException ex) {
			// Two registrations raced past the check above.
			throw emailTaken();
		}
		audit.record(AuditAction.ACCOUNT_REGISTERED, AuditOutcome.SUCCESS, account.id(), AuditService.TARGET_ACCOUNT,
				account.id(), null);
		return AccountResponse.from(account);
	}

	/**
	 * Every failure looks the same to the caller: unknown email, wrong password, and disabled
	 * account. Not transactional, so a failure's audit event commits even though login throws.
	 */
	AccountResponse login(LoginRequest request, String address) {
		String email = normalizeEmail(request.email());
		OptionalLong retryAfter = rateLimiter.retryAfterSeconds(email, address);
		if (retryAfter.isPresent()) {
			throw new RateLimitedException(retryAfter.getAsLong());
		}

		Optional<UserAccount> found = accounts.findByEmail(email);
		String hash = found.map(UserAccount::passwordHash).orElse(dummyHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);

		if (found.isEmpty() || !passwordMatches || !found.get().isActive()) {
			rateLimiter.recordFailure(email, address);
			audit.record(AuditAction.LOGIN, AuditOutcome.FAILURE, null, AuditService.TARGET_ACCOUNT,
					found.map(UserAccount::id).orElse(null), null);
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Incorrect email or password");
		}

		UserAccount account = found.get();
		rateLimiter.recordSuccess(email);
		audit.record(AuditAction.LOGIN, AuditOutcome.SUCCESS, account.id(), AuditService.TARGET_ACCOUNT, account.id(),
				null);
		return AccountResponse.from(account);
	}

	void recordLogout(AccountPrincipal principal) {
		audit.record(AuditAction.LOGOUT, AuditOutcome.SUCCESS, principal.accountId(), AuditService.TARGET_ACCOUNT,
				principal.accountId(), null);
	}

	void recordDenied(AccountPrincipal principal, String reason) {
		audit.record(AuditAction.ACCESS_DENIED, AuditOutcome.DENIED, principal.accountId(), null, null,
				Map.of("reason", reason));
	}

	@Transactional(readOnly = true)
	public AccountResponse currentAccount(AccountPrincipal principal) {
		return accounts.findById(principal.accountId())
			.map(AccountResponse::from)
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Sign in to continue"));
	}

	private static String normalizeEmail(String email) {
		return AccountDirectory.normalizeEmail(email);
	}

	private static ApiException emailTaken() {
		return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "This email is already registered");
	}

}
