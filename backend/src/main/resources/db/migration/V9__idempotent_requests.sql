-- Spec §4: "Idempotency key on all POSTs that create money records."
--
-- A retried POST is the ordinary case, not the exotic one: a flaky connection,
-- a double tap, a browser replaying a request. Without this, each retry writes
-- a second entry, and every figure built on top of it is quietly wrong while
-- looking entirely correct.
--
-- The key is scoped to the user, and the primary key says so. A key is chosen
-- by the client, so two people can pick the same one; without the user in the
-- key, the second person would be handed the first person's response.
CREATE TABLE idempotent_request (
    user_id         UUID                     NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    idempotency_key VARCHAR(200)             NOT NULL,
    -- The same key sent to a different endpoint is a mistake worth reporting,
    -- not a match. Replaying a loan as a transaction would be worse than
    -- creating a second one.
    endpoint        VARCHAR(200)             NOT NULL,
    -- The answer as it was given, replayed verbatim. Re-deriving it later could
    -- produce a different one, and a retry that answers differently from the
    -- original is not idempotent.
    response_body   TEXT                     NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, idempotency_key)
);
