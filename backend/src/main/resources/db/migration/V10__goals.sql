-- Spec §6 step 9: savings goals (BR-11).
--
-- Nothing derived is stored. The gap, the contribution and the pace marker all
-- move with today's date, so a stored one would be a figure that was true on
-- the morning it was written.
CREATE TABLE goal (
    id                     UUID           PRIMARY KEY,
    user_id                UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name                   VARCHAR(200)   NOT NULL,
    target_amount          NUMERIC(19, 2) NOT NULL,
    target_date            DATE           NOT NULL,
    -- BR-11 has exactly one source of truth for this, and it is this column.
    -- Nothing else may write to it: a tag on a goal never allocates toward it
    -- (BR-18), which is why Phase 2 cannot quietly become a second source.
    saved_amount           NUMERIC(19, 2) NOT NULL DEFAULT 0,
    contribution_frequency VARCHAR(20)    NOT NULL,
    -- Optional: a goal may be bound to a pocket, and most are not. The pocket
    -- balance stays BR-13's business either way - binding names a place, it
    -- does not move money.
    pocket_id              UUID           REFERENCES pocket (id) ON DELETE SET NULL,
    -- "rank" is a window function in SQL and a fight waiting to happen as a
    -- column name.
    priority_rank          INTEGER        NOT NULL,
    -- BR-11's pace marker measures from here. Without it the marker would have
    -- to assume every goal started today, and every goal would look on pace.
    started_on             DATE           NOT NULL,
    CONSTRAINT goal_target_is_worth_saving_for CHECK (target_amount > 0),
    CONSTRAINT goal_saved_is_not_negative CHECK (saved_amount >= 0),
    CONSTRAINT goal_name_is_unique_per_user UNIQUE (user_id, name)
);

CREATE INDEX goal_by_user ON goal (user_id);
