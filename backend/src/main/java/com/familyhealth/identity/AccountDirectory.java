package com.familyhealth.identity;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** What other modules may read about accounts. Never exposes password hashes. */
@Service
public class AccountDirectory {

	public record AccountSummary(UUID id, String email, String displayName) {
	}

	private final UserAccountRepository accounts;

	AccountDirectory(UserAccountRepository accounts) {
		this.accounts = accounts;
	}

	public static String normalizeEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	@Transactional(readOnly = true)
	public Map<UUID, AccountSummary> summaries(Collection<UUID> ids) {
		List<UserAccount> found = accounts.findAllById(ids);
		return found.stream()
			.map(account -> new AccountSummary(account.id(), account.email(), account.displayName()))
			.collect(Collectors.toMap(AccountSummary::id, Function.identity()));
	}

	@Transactional(readOnly = true)
	public Optional<AccountSummary> findById(UUID id) {
		return accounts.findById(id)
			.map(account -> new AccountSummary(account.id(), account.email(), account.displayName()));
	}

	@Transactional(readOnly = true)
	public Optional<UUID> findIdByEmail(String normalizedEmail) {
		return accounts.findByEmail(normalizedEmail).map(UserAccount::id);
	}

}
