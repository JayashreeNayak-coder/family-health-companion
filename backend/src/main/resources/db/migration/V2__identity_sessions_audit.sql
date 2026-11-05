-- Phase 4: accounts, server-side sessions, and the audit log.
-- See docs/04-database-design.md sections 3 and 7.

CREATE TABLE user_account (
	id uuid PRIMARY KEY,
	email text NOT NULL,
	password_hash text NOT NULL,
	display_name text NOT NULL,
	status text NOT NULL,
	created_at timestamptz NOT NULL,
	updated_at timestamptz NOT NULL,
	version bigint NOT NULL,
	CONSTRAINT user_account_email_uk UNIQUE (email),
	CONSTRAINT user_account_email_lowercase_ck CHECK (email = lower(email)),
	CONSTRAINT user_account_status_ck CHECK (status IN ('ACTIVE', 'DISABLED'))
);

-- Append-only. Actor and target are deliberately not foreign keys, so history outlives deleted records.
-- action has no CHECK because each later phase adds actions; AuditAction in the code lists them.
CREATE TABLE audit_event (
	id uuid PRIMARY KEY,
	occurred_at timestamptz NOT NULL,
	actor_account_id uuid,
	action text NOT NULL,
	target_type text,
	target_id uuid,
	profile_id uuid,
	outcome text NOT NULL,
	metadata jsonb,
	CONSTRAINT audit_event_outcome_ck CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX audit_event_profile_idx ON audit_event (profile_id, occurred_at DESC);
CREATE INDEX audit_event_actor_idx ON audit_event (actor_account_id, occurred_at DESC);

-- Spring Session JDBC's own tables, copied from its schema-postgresql.sql. Flyway owns all schema changes.
CREATE TABLE spring_session (
	primary_id char(36) NOT NULL,
	session_id char(36) NOT NULL,
	creation_time bigint NOT NULL,
	last_access_time bigint NOT NULL,
	max_inactive_interval int NOT NULL,
	expiry_time bigint NOT NULL,
	principal_name varchar(100),
	CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
	session_primary_id char(36) NOT NULL,
	attribute_name varchar(200) NOT NULL,
	attribute_bytes bytea NOT NULL,
	CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
	CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
		REFERENCES spring_session (primary_id) ON DELETE CASCADE
);
