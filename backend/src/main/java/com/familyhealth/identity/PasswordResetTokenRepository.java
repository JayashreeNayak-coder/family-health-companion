package com.familyhealth.identity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	long countByAccountIdAndCreatedAtAfter(UUID accountId, Instant since);

	List<PasswordResetToken> findByAccountIdAndUsedAtIsNull(UUID accountId);

}
