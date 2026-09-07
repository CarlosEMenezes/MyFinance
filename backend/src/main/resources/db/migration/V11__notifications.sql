-- Spec §6 step 10: the derived notification queue (BR-12).
--
-- There is no notifications table, and there must not be one. BR-12 derives
-- the queue from card bills, loans, instalments, direct debits and
-- subscriptions every time it is asked, so a paid card drops out of it on its
-- own. A stored notification would have to be deleted by something, and
-- whatever forgot to delete it would leave a warning about money that has
-- already moved.
--
-- Only read state is persisted, which is the one thing that cannot be derived.
CREATE TABLE notification_read (
    user_id          UUID                     NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- The key is the queue item's stable identity. It has to survive a
    -- recomputation, or an item marked read would come back unread.
    notification_key VARCHAR(200)             NOT NULL,
    read_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id, notification_key)
);

-- BR-12: leads are any subset of {10, 5, 2}. Stored as a short list rather
-- than three flags, because the rule is about a set and three booleans would
-- allow a fourth lead time to be added without anybody noticing the rule said
-- otherwise.
CREATE TABLE notification_settings (
    user_id        UUID        PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    lead_days      VARCHAR(50) NOT NULL DEFAULT '10,5,2',
    push           BOOLEAN     NOT NULL DEFAULT TRUE,
    email          BOOLEAN     NOT NULL DEFAULT FALSE,
    weekly_summary BOOLEAN     NOT NULL DEFAULT TRUE
);
