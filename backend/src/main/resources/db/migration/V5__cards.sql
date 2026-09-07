-- Spec §6 step 4: cards (BR-4, BR-5).
--
-- A card has no user_id of its own. It settles from an account, and the
-- account already has an owner, so ownership has exactly one source of truth
-- and there is no second column that could disagree with it. Every read
-- filters through `account.user_id`; a card whose owner drifted from its
-- account's owner is not a state this schema can reach.
CREATE TABLE card (
    id              UUID         PRIMARY KEY,
    account_id      UUID         NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name            VARCHAR(200) NOT NULL,
    kind            VARCHAR(20)  NOT NULL,
    -- BR-4: credit only. NULL on a debit card, and the check below says so.
    credit_limit    NUMERIC(19, 2),
    current_balance NUMERIC(19, 2),
    closing_day     INTEGER,
    due_day         INTEGER,
    CONSTRAINT card_name_is_unique_per_account UNIQUE (account_id, name),
    -- BR-4: both days must exist in every month, February included, which is
    -- what makes the bill date need no clamping rule anywhere.
    CONSTRAINT card_cycle_days_exist_in_every_month CHECK (
        (closing_day IS NULL OR closing_day BETWEEN 1 AND 28)
        AND (due_day IS NULL OR due_day BETWEEN 1 AND 28)
    ),
    -- BR-5 in the schema: a debit card has no cycle at all, and a credit card
    -- cannot be written without one. The domain says the same thing with a
    -- sealed type; this is the half of it that survives a manual UPDATE.
    CONSTRAINT card_carries_a_cycle_only_when_it_is_credit CHECK (
        (kind = 'CREDIT'
            AND credit_limit IS NOT NULL AND current_balance IS NOT NULL
            AND closing_day IS NOT NULL AND due_day IS NOT NULL)
        OR
        (kind = 'DEBIT'
            AND credit_limit IS NULL AND current_balance IS NULL
            AND closing_day IS NULL AND due_day IS NULL)
    )
);

CREATE INDEX card_by_account ON card (account_id);
