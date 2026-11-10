package com.familyhealth.emergencycard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.familyhealth.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * A profile's emergency card (docs/06 section 6). Off by default. While on, it has exactly one
 * working link, whose token is stored only as a hash. Turning it off forgets the hash.
 */
@Entity
@Table(name = "emergency_card")
class EmergencyCard extends BaseEntity {

	@Column(name = "profile_id", nullable = false, updatable = false)
	private UUID profileId;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "token_hash")
	private String tokenHash;

	@Column(name = "token_rotated_at")
	private Instant tokenRotatedAt;

	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "card_id", nullable = false, updatable = false)
	@OrderBy("displayOrder")
	private List<EmergencyCardItem> items = new ArrayList<>();

	protected EmergencyCard() {
	}

	EmergencyCard(UUID profileId) {
		this.profileId = profileId;
	}

	UUID profileId() {
		return profileId;
	}

	boolean enabled() {
		return enabled;
	}

	Instant tokenRotatedAt() {
		return tokenRotatedAt;
	}

	List<EmergencyCardItem> items() {
		return List.copyOf(items);
	}

	void replaceItems(List<EmergencyCardItem> replacement) {
		items.clear();
		items.addAll(replacement);
	}

	/** Turns the card on with a new link, or gives an enabled card a new link. The old one stops working. */
	void useToken(String hash, Instant now) {
		enabled = true;
		tokenHash = hash;
		tokenRotatedAt = now;
	}

	void disable() {
		enabled = false;
		tokenHash = null;
		tokenRotatedAt = null;
	}

}
