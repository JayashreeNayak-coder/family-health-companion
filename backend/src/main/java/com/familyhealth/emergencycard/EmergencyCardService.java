package com.familyhealth.emergencycard;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import com.familyhealth.audit.AuditAction;
import com.familyhealth.audit.AuditService;
import com.familyhealth.common.ApiException;
import com.familyhealth.common.FieldValidationException;
import com.familyhealth.common.SecureTokens;
import com.familyhealth.emergencycard.EmergencyCardDtos.CardResponse;
import com.familyhealth.emergencycard.EmergencyCardDtos.ItemRequest;
import com.familyhealth.emergencycard.EmergencyCardDtos.ItemResponse;
import com.familyhealth.emergencycard.EmergencyCardDtos.LinkResponse;
import com.familyhealth.emergencycard.EmergencyCardDtos.PublicCard;
import com.familyhealth.emergencycard.EmergencyCardDtos.PublicItem;
import com.familyhealth.emergencycard.EmergencyCardDtos.SourceStatus;
import com.familyhealth.emergencycard.EmergencyCardDtos.UpdateRequest;
import com.familyhealth.profile.AccessLevel;
import com.familyhealth.profile.ProfileAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The emergency card (docs/06 section 6). Every owner action needs MANAGE. The public read
 * returns only the approved lines of an enabled card, and has no route to anything else.
 */
@Service
class EmergencyCardService {

	/** What {@link SecureTokens#generate()} produces: 32 bytes as unpadded base64url. */
	private static final Pattern TOKEN_SHAPE = Pattern.compile("[A-Za-z0-9_-]{43}");

	private final EmergencyCardRepository cards;

	private final ProfileAccess access;

	private final AuditService audit;

	private final Clock clock;

	private final String publicUrl;

	private final Map<CardItemType, CardSources> sources = new EnumMap<>(CardItemType.class);

	EmergencyCardService(EmergencyCardRepository cards, ProfileAccess access, AuditService audit, Clock clock,
			List<CardSources> sourceList, @Value("${app.public-url}") String publicUrl) {
		this.cards = cards;
		this.access = access;
		this.audit = audit;
		this.clock = clock;
		this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
		sourceList.forEach(source -> source.types().forEach(type -> {
			if (sources.put(type, source) != null) {
				throw new IllegalStateException("Two card sources answer for " + type);
			}
		}));
		for (CardItemType type : CardItemType.values()) {
			if (type.canHaveSource() && !sources.containsKey(type)) {
				throw new IllegalStateException("No card source answers for " + type);
			}
		}
	}

	@Transactional(readOnly = true)
	public CardResponse get(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		return cards.findByProfileId(profileId)
			.map(this::toResponse)
			.orElseGet(() -> new CardResponse(false, null, List.of(), null));
	}

	@Transactional
	public CardResponse replaceItems(UUID accountId, UUID profileId, UpdateRequest request) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		EmergencyCard card = cards.findByProfileId(profileId).orElse(null);
		if (card == null) {
			card = new EmergencyCard(profileId);
		}
		else {
			card.requireVersion(request.version());
		}
		card.replaceItems(toItems(profileId, request.items()));
		card = cards.saveAndFlush(card);
		audit.recordForProfile(AuditAction.EMERGENCY_CARD_UPDATED, accountId, profileId,
				Map.of("items", String.valueOf(request.items().size())));
		return toResponse(card);
	}

	/** Turns the card on and returns its link, the only time the link is shown. */
	@Transactional
	public LinkResponse enable(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		EmergencyCard card = cards.findByProfileId(profileId)
			.filter(existing -> !existing.items().isEmpty())
			.orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "CARD_EMPTY",
					"Add at least one item to the card before turning it on"));
		if (card.enabled()) {
			throw new ApiException(HttpStatus.CONFLICT, "CARD_ALREADY_ENABLED",
					"The card is already on. Make a new link to get one you can see");
		}
		return newLink(accountId, card, AuditAction.EMERGENCY_CARD_ENABLED);
	}

	/** A new link for an enabled card. The old link stops working at once. */
	@Transactional
	public LinkResponse rotate(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		EmergencyCard card = cards.findByProfileId(profileId)
			.filter(EmergencyCard::enabled)
			.orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "CARD_NOT_ENABLED", "The card is off"));
		return newLink(accountId, card, AuditAction.EMERGENCY_CARD_LINK_ROTATED);
	}

	/** Takes effect at once. Turning it on again makes a new link. */
	@Transactional
	public CardResponse disable(UUID accountId, UUID profileId) {
		access.require(accountId, profileId, AccessLevel.MANAGE);
		Optional<EmergencyCard> found = cards.findByProfileId(profileId);
		if (found.isEmpty()) {
			return new CardResponse(false, null, List.of(), null);
		}
		EmergencyCard card = found.get();
		if (card.enabled()) {
			card.disable();
			card = cards.saveAndFlush(card);
			audit.recordForProfile(AuditAction.EMERGENCY_CARD_DISABLED, accountId, profileId, Map.of());
		}
		return toResponse(card);
	}

	/** Anyone with the link. Unknown, malformed, disabled, and replaced links all look the same: not found. */
	@Transactional(readOnly = true)
	public PublicCard read(String token) {
		if (token == null || !TOKEN_SHAPE.matcher(token).matches()) {
			throw notFound();
		}
		EmergencyCard card = cards.findByTokenHashAndEnabledTrue(SecureTokens.hash(token)).orElseThrow(this::notFound);
		return new PublicCard(card.items()
			.stream()
			.map(item -> new PublicItem(item.itemType(), item.displayText()))
			.toList());
	}

	private LinkResponse newLink(UUID accountId, EmergencyCard card, AuditAction action) {
		String token = SecureTokens.generate();
		card.useToken(SecureTokens.hash(token), clock.instant());
		card = cards.saveAndFlush(card);
		audit.recordForProfile(action, accountId, card.profileId(), Map.of());
		return new LinkResponse(publicUrl + "/emergency-card#" + token, toResponse(card));
	}

	private List<EmergencyCardItem> toItems(UUID profileId, List<ItemRequest> requested) {
		List<FieldValidationException.FieldError> errors = new ArrayList<>();
		List<EmergencyCardItem> items = new ArrayList<>();
		int people = 0;
		for (int i = 0; i < requested.size(); i++) {
			ItemRequest item = requested.get(i);
			String field = "items[" + i + "]";
			if (item.type() == CardItemType.PERSON && ++people > 1) {
				errors.add(new FieldValidationException.FieldError(field + ".type", "only one name line is allowed"));
				continue;
			}
			Instant sourceUpdatedAt = null;
			if (item.sourceRecordId() != null) {
				if (!item.type().canHaveSource()) {
					errors.add(new FieldValidationException.FieldError(field + ".sourceRecordId",
							"this kind of line can't come from a record"));
					continue;
				}
				Optional<Instant> changed = sources.get(item.type())
					.lastChanged(item.type(), profileId, item.sourceRecordId());
				if (changed.isEmpty()) {
					errors.add(new FieldValidationException.FieldError(field + ".sourceRecordId",
							"must be a record of this kind for this profile"));
					continue;
				}
				sourceUpdatedAt = micros(changed.get());
			}
			items.add(new EmergencyCardItem(item.type(), item.text().strip(), i, item.sourceRecordId(),
					sourceUpdatedAt));
		}
		if (!errors.isEmpty()) {
			throw new FieldValidationException(errors);
		}
		return items;
	}

	private CardResponse toResponse(EmergencyCard card) {
		List<ItemResponse> items = card.items()
			.stream()
			.map(item -> new ItemResponse(item.itemType(), item.displayText(), item.sourceRecordId(),
					sourceStatus(card.profileId(), item)))
			.toList();
		return new CardResponse(card.enabled(), card.tokenRotatedAt(), items, card.version());
	}

	private SourceStatus sourceStatus(UUID profileId, EmergencyCardItem item) {
		if (item.sourceRecordId() == null) {
			return SourceStatus.NONE;
		}
		return sources.get(item.itemType())
			.lastChanged(item.itemType(), profileId, item.sourceRecordId())
			.map(changed -> micros(changed).isAfter(micros(item.sourceUpdatedAt())) ? SourceStatus.CHANGED
					: SourceStatus.CURRENT)
			.orElse(SourceStatus.DELETED);
	}

	/** PostgreSQL keeps microseconds, so compare at that precision. */
	private static Instant micros(Instant instant) {
		return instant.truncatedTo(ChronoUnit.MICROS);
	}

	private ApiException notFound() {
		return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "This emergency card link doesn't work");
	}

}
