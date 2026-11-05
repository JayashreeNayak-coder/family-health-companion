-- Phase 5: families, health profiles, access grants, and invitations.
-- See docs/04-database-design.md section 3 and docs/06-security-and-ai-safety.md section 3.

CREATE TABLE family (
	id uuid PRIMARY KEY,
	name text NOT NULL,
	created_by uuid NOT NULL REFERENCES user_account (id),
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL
);

CREATE INDEX family_created_by_idx ON family (created_by);

CREATE TABLE family_membership (
	id uuid PRIMARY KEY,
	family_id uuid NOT NULL REFERENCES family (id) ON DELETE CASCADE,
	user_account_id uuid NOT NULL REFERENCES user_account (id),
	role text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT family_membership_uk UNIQUE (family_id, user_account_id),
	CONSTRAINT family_membership_role_ck CHECK (role IN ('MANAGER', 'MEMBER'))
);

CREATE INDEX family_membership_account_idx ON family_membership (user_account_id);

CREATE TABLE health_profile (
	id uuid PRIMARY KEY,
	family_id uuid NOT NULL REFERENCES family (id) ON DELETE CASCADE,
	linked_account_id uuid REFERENCES user_account (id),
	full_name text NOT NULL,
	date_of_birth date,
	is_dependent boolean NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT health_profile_family_linked_uk UNIQUE (family_id, linked_account_id),
	CONSTRAINT health_profile_dependent_unlinked_ck CHECK (NOT is_dependent OR linked_account_id IS NULL)
);

CREATE INDEX health_profile_linked_account_idx ON health_profile (linked_account_id);

CREATE TABLE profile_access_grant (
	id uuid PRIMARY KEY,
	profile_id uuid NOT NULL REFERENCES health_profile (id) ON DELETE CASCADE,
	user_account_id uuid NOT NULL REFERENCES user_account (id),
	level text NOT NULL,
	granted_by uuid NOT NULL REFERENCES user_account (id),
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT profile_access_grant_uk UNIQUE (profile_id, user_account_id),
	CONSTRAINT profile_access_grant_level_ck CHECK (level IN ('VIEW', 'EDIT', 'MANAGE'))
);

CREATE INDEX profile_access_grant_account_idx ON profile_access_grant (user_account_id);
CREATE INDEX profile_access_grant_granted_by_idx ON profile_access_grant (granted_by);

-- Only a hash of the token is stored. A set profile_id means accepting also links that profile to the account.
CREATE TABLE family_invitation (
	id uuid PRIMARY KEY,
	family_id uuid NOT NULL REFERENCES family (id) ON DELETE CASCADE,
	profile_id uuid REFERENCES health_profile (id) ON DELETE CASCADE,
	invited_email text NOT NULL,
	role text NOT NULL,
	token_hash text NOT NULL,
	invited_by uuid NOT NULL REFERENCES user_account (id),
	expires_at timestamptz NOT NULL,
	accepted_at timestamptz,
	accepted_by uuid REFERENCES user_account (id),
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT family_invitation_token_uk UNIQUE (token_hash),
	CONSTRAINT family_invitation_email_lowercase_ck CHECK (invited_email = lower(invited_email)),
	CONSTRAINT family_invitation_role_ck CHECK (role IN ('MANAGER', 'MEMBER'))
);

CREATE INDEX family_invitation_family_idx ON family_invitation (family_id);
CREATE INDEX family_invitation_profile_idx ON family_invitation (profile_id);
CREATE INDEX family_invitation_invited_by_idx ON family_invitation (invited_by);
CREATE INDEX family_invitation_accepted_by_idx ON family_invitation (accepted_by);
