package com.familyhealth.profile;

import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.PageResponse;
import com.familyhealth.family.FamilyMembers;
import com.familyhealth.family.InvitedProfileLinks;
import com.familyhealth.identity.AccountDirectory;
import com.familyhealth.identity.AccountDirectory.AccountSummary;
import com.familyhealth.profile.ProfileDtos.CreateProfileRequest;
import com.familyhealth.profile.ProfileDtos.GrantResponse;
import com.familyhealth.profile.ProfileDtos.ProfileKind;
import com.familyhealth.profile.ProfileDtos.ProfileResponse;
import com.familyhealth.profile.ProfileDtos.UpdateProfileRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileService implements InvitedProfileLinks {

	private final HealthProfileRepository profiles;

	private final ProfileAccessGrantRepository grants;

	private final ProfileAccess access;

	private final FamilyMembers members;

	private final AccountDirectory accounts;

	private final AuditService audit;

	private final ProfileFiles files;

	ProfileService(HealthProfileRepository profiles, ProfileAccessGrantRepository grants, ProfileAccess access,
			FamilyMembers members, AccountDirectory accounts, AuditService audit, ProfileFiles files) {
		this.files = files;
		this.profiles = profiles;
		this.grants = grants;
		this.access = access;
		this.members = members;
		this.accounts = accounts;
		this.audit = audit;
	}

	/**
	 * Any member may create their own profile. Only a manager may create one for someone else, and
	 * the creator receives MANAGE on what they create (docs/06 section 3, rules 2 and 3).
	 */
	@Transactional
	public ProfileResponse create(UUID accountId, UUID familyId, CreateProfileRequest request) {
		boolean self = request.kind() == ProfileKind.SELF;
		if (self) {
			members.requireMember(familyId, accountId);
			if (profiles.existsByFamilyIdAndLinkedAccountId(familyId, accountId)) {
				throw selfProfileExists();
			}
		}
		else {
			members.requireManager(familyId, accountId);
		}
		HealthProfile profile = profiles.save(new HealthProfile(familyId, self ? accountId : null,
				request.fullName().trim(), request.dateOfBirth(), request.kind() == ProfileKind.DEPENDENT));
		grants.save(new ProfileAccessGrant(profile.id(), accountId, AccessLevel.MANAGE, accountId));
		return ProfileResponse.from(profile, AccessLevel.MANAGE);
	}

	@Transactional(readOnly = true)
	public PageResponse<ProfileResponse> list(UUID accountId, Pageable pageable) {
		return PageResponse.from(profiles.findForAccount(accountId, pageable),
				row -> ProfileResponse.from((HealthProfile) row[0], (AccessLevel) row[1]));
	}

	@Transactional(readOnly = true)
	public ProfileResponse get(UUID accountId, UUID profileId) {
		AccessLevel level = access.require(accountId, profileId, AccessLevel.VIEW);
		return ProfileResponse.from(load(profileId), level);
	}

	@Transactional
	public ProfileResponse update(UUID accountId, UUID profileId, UpdateProfileRequest request) {
		AccessLevel level = access.require(accountId, profileId, AccessLevel.EDIT);
		HealthProfile profile = load(profileId);
		profile.requireVersion(request.version());
		profile.updateDetails(request.fullName().trim(), request.dateOfBirth());
		return ProfileResponse.from(profiles.saveAndFlush(profile), level);
	}

	/** Foreign keys cascade to everything the profile owns. Stored files go once that commits. */
	@Transactional
	public void delete(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		HealthProfile profile = load(profileId);
		files.deleteAllAfterCommit(profileId);
		profiles.delete(profile);
		audit.recordForProfile(AuditAction.PROFILE_DELETED, accountId, profileId, Map.of());
	}

	@Transactional(readOnly = true)
	public List<GrantResponse> listGrants(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		HealthProfile profile = load(profileId);
		List<ProfileAccessGrant> rows = grants.findByProfileId(profileId);
		Map<UUID, AccountSummary> summaries = accounts
			.summaries(rows.stream().map(ProfileAccessGrant::userAccountId).toList());
		return rows.stream().map(row -> {
			AccountSummary account = summaries.get(row.userAccountId());
			return new GrantResponse(account.id(), account.displayName(), account.email(), row.level(),
					account.id().equals(profile.linkedAccountId()));
		}).sorted(Comparator.comparing(GrantResponse::displayName)).toList();
	}

	@Transactional
	public GrantResponse putGrant(UUID accountId, UUID profileId, UUID targetId, AccessLevel level) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		HealthProfile profile = lockProfile(profileId);
		if (!members.isMember(profile.familyId(), targetId)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "This person isn't in the family");
		}
		ProfileAccessGrant grant = grants.findByProfileIdAndUserAccountId(profileId, targetId).orElse(null);
		if (grant != null && grant.level() == AccessLevel.MANAGE && level != AccessLevel.MANAGE) {
			requireMayLoseManage(profile, targetId);
		}
		if (grant == null) {
			grant = new ProfileAccessGrant(profileId, targetId, level, accountId);
		}
		else {
			grant.change(level, accountId);
		}
		grants.save(grant);
		audit.recordForProfile(AuditAction.GRANT_CHANGED, accountId, profileId,
				Map.of("targetAccountId", targetId.toString(), "level", level.name()));
		AccountSummary account = accounts.findById(targetId).orElseThrow(ProfileService::notFound);
		return new GrantResponse(targetId, account.displayName(), account.email(), level,
				targetId.equals(profile.linkedAccountId()));
	}

	@Transactional
	public void deleteGrant(UUID accountId, UUID profileId, UUID targetId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		HealthProfile profile = lockProfile(profileId);
		ProfileAccessGrant grant = grants.findByProfileIdAndUserAccountId(profileId, targetId)
			.orElseThrow(ProfileService::notFound);
		if (grant.level() == AccessLevel.MANAGE) {
			requireMayLoseManage(profile, targetId);
		}
		grants.delete(grant);
		audit.recordForProfile(AuditAction.GRANT_REVOKED, accountId, profileId,
				Map.of("targetAccountId", targetId.toString()));
	}

	@Override
	@Transactional
	public void checkCanOffer(UUID inviterAccountId, UUID familyId, UUID profileId) {
		access.require(inviterAccountId, profileId, AccessLevel.MANAGE);
		HealthProfile profile = load(profileId);
		if (!profile.familyId().equals(familyId)) {
			throw notFound();
		}
		if (profile.linkedAccountId() != null) {
			throw alreadyLinked();
		}
	}

	@Override
	@Transactional(readOnly = true)
	public String profileName(UUID profileId) {
		return load(profileId).fullName();
	}

	@Override
	@Transactional
	public void link(UUID profileId, UUID adultAccountId, KeepAccess keepAccess) {
		HealthProfile profile = lockProfile(profileId);
		if (profile.linkedAccountId() != null) {
			throw alreadyLinked();
		}
		if (profiles.existsByFamilyIdAndLinkedAccountId(profile.familyId(), adultAccountId)) {
			throw selfProfileExists();
		}
		for (ProfileAccessGrant grant : grants.findByProfileId(profileId)) {
			if (grant.userAccountId().equals(adultAccountId)) {
				continue;
			}
			if (keepAccess == KeepAccess.NONE) {
				grants.delete(grant);
			}
			else {
				AccessLevel kept = AccessLevel.valueOf(keepAccess.name());
				if (grant.level().includes(kept) && grant.level() != kept) {
					grant.change(kept, adultAccountId);
				}
			}
		}
		ProfileAccessGrant own = grants.findByProfileIdAndUserAccountId(profileId, adultAccountId)
			.orElseGet(() -> new ProfileAccessGrant(profileId, adultAccountId, AccessLevel.MANAGE, adultAccountId));
		own.change(AccessLevel.MANAGE, adultAccountId);
		grants.save(own);
		profile.linkTo(adultAccountId);
		audit.recordForProfile(AuditAction.PROFILE_LINKED, adultAccountId, profileId,
				Map.of("keepAccess", keepAccess.name()));
	}

	/** The profile's owner always keeps MANAGE, and someone must always hold it. */
	private void requireMayLoseManage(HealthProfile profile, UUID targetId) {
		if (targetId.equals(profile.linkedAccountId())) {
			throw new ApiException(HttpStatus.CONFLICT, "LINKED_ACCOUNT_GRANT",
					"The person this profile belongs to always keeps full access");
		}
		if (grants.countByProfileIdAndLevel(profile.id(), AccessLevel.MANAGE) <= 1) {
			throw new ApiException(HttpStatus.CONFLICT, "LAST_MANAGER",
					"At least one person must keep manage access to this profile");
		}
	}

	private HealthProfile load(UUID profileId) {
		return profiles.findById(profileId).orElseThrow(ProfileService::notFound);
	}

	private HealthProfile lockProfile(UUID profileId) {
		return profiles.findByIdForUpdate(profileId).orElseThrow(ProfileService::notFound);
	}

	private static ApiException alreadyLinked() {
		return new ApiException(HttpStatus.CONFLICT, "PROFILE_ALREADY_LINKED",
				"This profile is already linked to an account");
	}

	private static ApiException selfProfileExists() {
		return new ApiException(HttpStatus.CONFLICT, "SELF_PROFILE_EXISTS",
				"This account already has its own profile in this family");
	}

	private static ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
	}

}
