-- Password reset and Google sign-in. See docs/04-database-design.md section 4.
-- An account signed up with Google has no password until the user sets one through a reset.

ALTER TABLE user_account ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE user_account ADD COLUMN google_subject text;
ALTER TABLE user_account ADD CONSTRAINT user_account_google_subject_uk UNIQUE (google_subject);
ALTER TABLE user_account ADD CONSTRAINT user_account_sign_in_ck CHECK (password_hash IS NOT NULL OR google_subject IS NOT NULL);

-- Only a hash of each link's token is stored, so a database leak reveals no working link.
CREATE TABLE password_reset_token (
	id uuid PRIMARY KEY,
	user_account_id uuid NOT NULL REFERENCES user_account (id) ON DELETE CASCADE,
	token_hash text NOT NULL,
	created_at timestamptz NOT NULL,
	expires_at timestamptz NOT NULL,
	used_at timestamptz,
	CONSTRAINT password_reset_token_hash_uk UNIQUE (token_hash)
);

CREATE INDEX password_reset_token_account_idx ON password_reset_token (user_account_id, created_at);
