-- Phase 17: the emergency card. See docs/04-database-design.md section 7 and docs/06 section 6.
-- Off by default. Only a hash of the link's token is stored, so a database leak reveals no working link.
-- Disabling clears the hash, so turning the card on again always makes a new link.

CREATE TABLE emergency_card (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	enabled boolean NOT NULL,
	token_hash text,
	token_rotated_at timestamptz,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT emergency_card_profile_uk UNIQUE (profile_id),
	CONSTRAINT emergency_card_token_hash_uk UNIQUE (token_hash),
	CONSTRAINT emergency_card_enabled_ck CHECK (enabled = (token_hash IS NOT NULL)),
	CONSTRAINT emergency_card_token_time_ck CHECK ((token_hash IS NULL) = (token_rotated_at IS NULL))
);

-- Each item holds the text the user approved, never a live reference: editing a record can't change
-- or expand a public card. source_record_id only lets the owner's screen flag a changed record. It is
-- not a foreign key, because it can point at several record tables and the text outlives the record.
CREATE TABLE emergency_card_item (
	id uuid PRIMARY KEY,
	card_id uuid NOT NULL REFERENCES emergency_card (id) ON DELETE CASCADE,
	item_type text NOT NULL,
	display_text text NOT NULL,
	display_order integer NOT NULL,
	source_record_id uuid,
	source_updated_at timestamptz,
	CONSTRAINT emergency_card_item_type_ck CHECK (item_type IN ('PERSON', 'ALLERGY', 'CONDITION', 'MEDICATION', 'EMERGENCY_CONTACT', 'NOTE')),
	CONSTRAINT emergency_card_item_text_ck CHECK (char_length(display_text) BETWEEN 1 AND 300),
	CONSTRAINT emergency_card_item_source_ck CHECK ((source_record_id IS NULL) = (source_updated_at IS NULL)),
	CONSTRAINT emergency_card_item_free_text_ck CHECK (item_type NOT IN ('PERSON', 'NOTE') OR source_record_id IS NULL)
);

CREATE INDEX emergency_card_item_card_idx ON emergency_card_item (card_id, display_order);
