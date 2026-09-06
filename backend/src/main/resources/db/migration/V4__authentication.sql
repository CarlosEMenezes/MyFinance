-- Spec §6 step 2's other half: auth. See docs/adr/0011.

-- The placeholder goes first. A passwordless row with a fixed, publicly-known
-- id was honest scaffolding while nothing was exposed and is a hole the moment
-- something is. Its accounts cascade with it; nothing real is lost, because
-- nothing real has been entered.
DELETE FROM app_user WHERE id = '00000000-0000-4000-8000-000000000001';

-- One column per statement. H2 in PostgreSQL mode does not accept several
-- ADD COLUMN clauses in a single ALTER, and these migrations have to run
-- unchanged on both engines or the tests stop testing the real schema.
--
-- Email is stored lower-cased so that "Carlos@..." and "carlos@..." are one
-- person. The uniqueness constraint would not catch that on its own.
ALTER TABLE app_user ADD COLUMN email VARCHAR(320);
ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(200);
ALTER TABLE app_user ADD COLUMN created_at TIMESTAMP WITH TIME ZONE;

-- Added nullable above and tightened here, which is the pattern for adding a
-- required column to a populated table. This one happens to be empty now, but
-- writing it the other way would fail the first time it is not.
UPDATE app_user SET created_at = CURRENT_TIMESTAMP WHERE created_at IS NULL;

ALTER TABLE app_user ALTER COLUMN email SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN password_hash SET NOT NULL;
ALTER TABLE app_user ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE app_user ADD CONSTRAINT app_user_email_is_unique UNIQUE (email);

-- ADR-11: an opaque session token, stored as a SHA-256 of itself.
--
-- The row holds a hash for the same reason app_user holds a password hash: a
-- leaked backup then contains no usable session. Signing out deletes the row,
-- and "sign out everywhere" deletes every row for the user, which is the whole
-- reason this is a table rather than a signed token nobody can withdraw.
CREATE TABLE user_session (
    -- 64 hex characters of SHA-256. VARCHAR rather than CHAR so that
    -- Hibernate's schema validation agrees with it on both engines.
    token_hash   VARCHAR(64)              PRIMARY KEY,
    user_id      UUID                     NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX user_session_by_user ON user_session (user_id);
CREATE INDEX user_session_by_expiry ON user_session (expires_at);
