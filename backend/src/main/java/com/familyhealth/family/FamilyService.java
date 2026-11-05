package com.familyhealth.family;

import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditOutcome;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.PageResponse;
import com.familyhealth.common.SecureTokens;
import com.familyhealth.family.FamilyDtos.AcceptInvitationRequest;
import com.familyhealth.family.FamilyDtos.CreateFamilyRequest;
import com.familyhealth.family.FamilyDtos.CreateInvitationRequest;
import com.familyhealth.family.FamilyDtos.FamilyMember;
import com.familyhealth.family.FamilyDtos.FamilyResponse;
import com.familyhealth.family.FamilyDtos.FamilySummary;
import com.familyhealth.family.FamilyDtos.InvitationCreated;
import com.familyhealth.family.FamilyDtos.InvitationPreview;
import com.familyhealth.identity.AccountDirectory;
import com.familyhealth.identity.AccountDirectory.AccountSummary;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class FamilyService {

	static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

	private final FamilyRepository families;

	private final FamilyMembershipRepository memberships;

	private final FamilyInvitationRepository invitations;

	private final FamilyMembers members;

	private final AccountDirectory accounts;

	private final InvitedProfileLinks profileLinks;

	private final AuditService audit;

	private final Clock clock;

	FamilyService(FamilyRepository families, FamilyMembershipRepository memberships,
			FamilyInvitationRepository invitations, FamilyMembers members, AccountDirectory accounts,
			InvitedProfileLinks profileLinks, AuditService audit, Clock clock) {
		this.families = families;
		this.memberships = memberships;
		this.invitations = invitations;
		this.members = members;
		this.accounts = accounts;
		this.profileLinks = profileLinks;
		this.audit = audit;
		this.clock = clock;
	}

	@Transactional
	public FamilySummary create(UUID accountId, CreateFamilyRequest request) {
		Family family = families.save(new Family(request.name().trim(), accountId));
		memberships.save(new FamilyMembership(family.id(), accountId, FamilyRole.MANAGER));
		return new FamilySummary(family.id(), family.name(), FamilyRole.MANAGER);
	}

	@Transactional(readOnly = true)
	public PageResponse<FamilySummary> list(UUID accountId, Pageable pageable) {
		Page<Family> page = families.findForAccount(accountId, pageable);
		List<UUID> ids = page.getContent().stream().map(Family::id).toList();
		Map<UUID, FamilyRole> roles = memberships.findByUserAccountIdAndFamilyIdIn(accountId, ids)
			.stream()
			.collect(Collectors.toMap(FamilyMembership::familyId, FamilyMembership::role));
		return PageResponse.from(page, family -> new FamilySummary(family.id(), family.name(), roles.get(family.id())));
	}

	@Transactional(readOnly = true)
	public FamilyResponse get(UUID accountId, UUID familyId) {
		members.requireMember(familyId, accountId);
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		List<FamilyMembership> rows = memberships.findByFamilyId(familyId);
		Map<UUID, AccountSummary> summaries = accounts
			.summaries(rows.stream().map(FamilyMembership::userAccountId).toList());
		List<FamilyMember> list = rows.stream().map(row -> {
			AccountSummary account = summaries.get(row.userAccountId());
			return new FamilyMember(account.id(), account.displayName(), account.email(), row.role());
		}).sorted(Comparator.comparing(FamilyMember::displayName)).toList();
		FamilyRole callerRole = rows.stream()
			.filter(row -> row.userAccountId().equals(accountId))
			.map(FamilyMembership::role)
			.findFirst()
			.orElseThrow(FamilyService::notFound);
		return new FamilyResponse(family.id(), family.name(), callerRole, list);
	}

	@Transactional
	public InvitationCreated invite(UUID accountId, UUID familyId, CreateInvitationRequest request) {
		members.requireManager(familyId, accountId);
		String email = AccountDirectory.normalizeEmail(request.email());
		FamilyRole role = request.role() != null ? request.role() : FamilyRole.MEMBER;
		if (request.profileId() != null) {
			profileLinks.checkCanOffer(accountId, familyId, request.profileId());
		}
		else if (accounts.findIdByEmail(email).filter(id -> members.isMember(familyId, id)).isPresent()) {
			throw alreadyMember();
		}
		String token = SecureTokens.generate();
		FamilyInvitation invitation = invitations.save(new FamilyInvitation(familyId, request.profileId(), email, role,
				SecureTokens.hash(token), accountId, clock.instant().plus(INVITATION_LIFETIME)));
		audit.record(AuditAction.FAMILY_INVITATION_CREATED, AuditOutcome.SUCCESS, accountId, AuditService.TARGET_FAMILY,
				familyId, Map.of("role", role.name()));
		return new InvitationCreated(invitation.id(), email, role, request.profileId(), invitation.expiresAt(), token);
	}

	@Transactional
	public InvitationPreview preview(UUID accountId, String token) {
		FamilyInvitation invitation = openInvitation(accountId, token);
		Family family = families.findById(invitation.familyId()).orElseThrow(FamilyService::notFound);
		String profileName = invitation.profileId() != null ? profileLinks.profileName(invitation.profileId()) : null;
		return new InvitationPreview(family.name(), invitation.role(), profileName);
	}

	@Transactional
	public FamilySummary accept(UUID accountId, String token, AcceptInvitationRequest request) {
		FamilyInvitation invitation = openInvitation(accountId, token);
		UUID familyId = invitation.familyId();
		boolean alreadyMember = members.isMember(familyId, accountId);
		if (invitation.profileId() == null && alreadyMember) {
			throw alreadyMember();
		}
		if (!alreadyMember) {
			memberships.save(new FamilyMembership(familyId, accountId, invitation.role()));
		}
		if (invitation.profileId() != null) {
			if (request == null || request.keepAccess() == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "KEEP_ACCESS_REQUIRED",
						"Choose how much access others keep to this profile");
			}
			profileLinks.link(invitation.profileId(), accountId, request.keepAccess());
		}
		invitation.accept(accountId, clock.instant());
		audit.record(AuditAction.FAMILY_INVITATION_ACCEPTED, AuditOutcome.SUCCESS, accountId,
				AuditService.TARGET_FAMILY, familyId, Map.of());
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		FamilyRole role = memberships.findByFamilyIdAndUserAccountId(familyId, accountId)
			.map(FamilyMembership::role)
			.orElseThrow(FamilyService::notFound);
		return new FamilySummary(family.id(), family.name(), role);
	}

	/** Unknown, expired, used, and someone else's invitations all look the same to the caller. */
	private FamilyInvitation openInvitation(UUID accountId, String token) {
		String email = accounts.findById(accountId).map(AccountSummary::email).orElseThrow(FamilyService::notFound);
		return invitations.findByTokenHash(SecureTokens.hash(token))
			.filter(invitation -> invitation.isOpenFor(email, clock.instant()))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND",
					"This invitation is not valid for your account, or it has expired"));
	}

	private static ApiException alreadyMember() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "This person is already in the family");
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
	}

}
