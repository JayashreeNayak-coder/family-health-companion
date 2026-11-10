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
import com.familyhealth.family.FamilyDtos.RenameFamilyRequest;
import com.familyhealth.identity.AccountDirectory.AccountSummary;
import com.familyhealth.identity.AccountDirectory;
import com.familyhealth.photo.PhotoStore;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class FamilyService {

	static final Duration INVITATION_LIFETIME = Duration.ofDays(7);

	private final FamilyRepository families;

	private final FamilyMembershipRepository memberships;

	private final FamilyInvitationRepository invitations;

	private final FamilyMembers members;

	private final AccountDirectory accounts;

	private final InvitedProfileLinks profileLinks;

	private final FamilyProfiles familyProfiles;

	private final PhotoStore photos;

	private final AuditService audit;

	private final Clock clock;

	FamilyService(FamilyRepository families, FamilyMembershipRepository memberships,
			FamilyInvitationRepository invitations, FamilyMembers members, AccountDirectory accounts,
			InvitedProfileLinks profileLinks, FamilyProfiles familyProfiles, PhotoStore photos, AuditService audit,
			Clock clock) {
		this.familyProfiles = familyProfiles;
		this.photos = photos;
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
		String name = cleanName(request.name());
		requireNameFree(List.of(accountId), NO_FAMILY, name, "You already belong to a family with this name");
		Family family = families.saveAndFlush(new Family(name, accountId));
		memberships.save(new FamilyMembership(family.id(), accountId, FamilyRole.MANAGER));
		return summary(family, FamilyRole.MANAGER);
	}

	/** Managers only. The new name must be free for every member (DECISIONS.md, D-031). */
	@Transactional
	public FamilySummary rename(UUID accountId, UUID familyId, RenameFamilyRequest request) {
		members.requireManager(familyId, accountId);
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		family.requireVersion(request.version());
		String name = cleanName(request.name());
		List<UUID> memberIds = memberships.findByFamilyId(familyId).stream().map(FamilyMembership::userAccountId).toList();
		requireNameFree(memberIds, familyId, name, "Someone in this family already belongs to another family with this name");
		family.rename(name);
		family = families.saveAndFlush(family);
		audit.record(AuditAction.FAMILY_RENAMED, AuditOutcome.SUCCESS, accountId, AuditService.TARGET_FAMILY, familyId,
				Map.of());
		return summary(family, FamilyRole.MANAGER);
	}

	/**
	 * Managers only, and only when no other account is in the family, so no other adult's profile
	 * is destroyed (DECISIONS.md, D-031). Every profile in it goes, with its records and files.
	 */
	@Transactional
	public void delete(UUID accountId, UUID familyId) {
		members.requireManager(familyId, accountId);
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		if (memberships.findByFamilyId(familyId).size() > 1) {
			throw new ApiException(HttpStatus.CONFLICT, "FAMILY_HAS_OTHER_MEMBERS",
					"A family can be deleted only when you are its only member");
		}
		familyProfiles.deleteAllInFamily(accountId, familyId);
		photos.deleteAfterCommit(family.photoKey());
		families.delete(family);
		families.flush();
		audit.record(AuditAction.FAMILY_DELETED, AuditOutcome.SUCCESS, accountId, AuditService.TARGET_FAMILY, familyId,
				Map.of());
	}

	/** Managers only. Replaces any earlier photo. */
	@Transactional
	public FamilySummary setPhoto(UUID accountId, UUID familyId, MultipartFile file) {
		members.requireManager(familyId, accountId);
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		photos.deleteAfterCommit(family.usePhoto(photos.save(file), clock.instant()));
		return summary(families.saveAndFlush(family), FamilyRole.MANAGER);
	}

	@Transactional
	public FamilySummary deletePhoto(UUID accountId, UUID familyId) {
		members.requireManager(familyId, accountId);
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		photos.deleteAfterCommit(family.usePhoto(null, clock.instant()));
		return summary(families.saveAndFlush(family), FamilyRole.MANAGER);
	}

	/** Any member. */
	@Transactional(readOnly = true)
	public byte[] photo(UUID accountId, UUID familyId) {
		members.requireMember(familyId, accountId);
		Family family = families.findById(familyId).orElseThrow(FamilyService::notFound);
		if (family.photoKey() == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_NOT_FOUND", "This family has no photo");
		}
		return photos.read(family.photoKey());
	}

	@Transactional(readOnly = true)
	public PageResponse<FamilySummary> list(UUID accountId, Pageable pageable) {
		Page<Family> page = families.findForAccount(accountId, pageable);
		List<UUID> ids = page.getContent().stream().map(Family::id).toList();
		Map<UUID, FamilyRole> roles = memberships.findByUserAccountIdAndFamilyIdIn(accountId, ids)
			.stream()
			.collect(Collectors.toMap(FamilyMembership::familyId, FamilyMembership::role));
		return PageResponse.from(page, family -> summary(family, roles.get(family.id())));
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
		return new FamilyResponse(family.id(), family.name(), callerRole, list, family.photoUpdatedAt(),
				family.version());
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
			String name = families.findById(familyId).map(Family::name).orElseThrow(FamilyService::notFound);
			requireNameFree(List.of(accountId), familyId, name,
					"You already belong to a family with this name. Ask a manager of one of them to rename it first");
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
		return summary(family, role);
	}

	/** Unknown, expired, used, and someone else's invitations all look the same to the caller. */
	private FamilyInvitation openInvitation(UUID accountId, String token) {
		String email = accounts.findById(accountId).map(AccountSummary::email).orElseThrow(FamilyService::notFound);
		return invitations.findByTokenHash(SecureTokens.hash(token))
			.filter(invitation -> invitation.isOpenFor(email, clock.instant()))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND",
					"This invitation is not valid for your account, or it has expired"));
	}

	/** Stands in for "no family to leave out" when checking a new family's name. */
	private static final UUID NO_FAMILY = new UUID(0, 0);

	/** Trimmed, with runs of spaces made single. */
	static String cleanName(String raw) {
		return raw.strip().replaceAll("\\s+", " ");
	}

	/** Names match ignoring case and spacing. */
	static String nameKey(String name) {
		return cleanName(name).toLowerCase(Locale.ROOT);
	}

	private void requireNameFree(Collection<UUID> accountIds, UUID excludedFamily, String name, String message) {
		String key = nameKey(name);
		if (families.findOthersForAccounts(accountIds, excludedFamily).stream().anyMatch(f -> nameKey(f.name()).equals(key))) {
			throw new ApiException(HttpStatus.CONFLICT, "FAMILY_NAME_TAKEN", message);
		}
	}

	private static FamilySummary summary(Family family, FamilyRole role) {
		return new FamilySummary(family.id(), family.name(), role, family.photoUpdatedAt(), family.version());
	}

	private static ApiException alreadyMember() {
		return new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "This person is already in the family");
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
	}

}
