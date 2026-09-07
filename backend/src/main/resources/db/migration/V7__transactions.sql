-- Spec §6 step 6: money that actually moved (BR-8, BR-4).
--
-- Two nullable foreign keys rather than one loose "payment_method_id": the
-- wire carries one id because a person picks one thing from one list, but what
-- that thing is decides the arithmetic (BR-4 defers a card purchase to a bill,
-- BR-5 does not, BR-1 counts only expenses not paid by credit card). A single
-- untyped column would leave that question to be re-answered by a join
-- everywhere it is asked.
CREATE TABLE transaction_entry (
    id                         UUID           PRIMARY KEY,
    user_id                    UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    category_id                UUID           NOT NULL REFERENCES category (id),
    type                       VARCHAR(20)    NOT NULL,
    -- BR-8: all three, always. The amount as typed, the amount every total is
    -- stated in, and the rate between them. Any two without the third is a
    -- figure that cannot be explained later.
    amount                     NUMERIC(19, 2) NOT NULL,
    currency                   VARCHAR(3)     NOT NULL,
    amount_in_default_currency NUMERIC(19, 2) NOT NULL,
    fx_rate                    NUMERIC(20, 10) NOT NULL,
    entry_date                 DATE           NOT NULL,
    account_id                 UUID           REFERENCES account (id),
    card_id                    UUID           REFERENCES card (id),
    note                       VARCHAR(500),
    -- BR-4: the bill date a credit-card expense lands on, which is not
    -- entry_date. NULL when the money left at once.
    planned_expense_date       DATE,
    CONSTRAINT transaction_has_exactly_one_payment_method CHECK (
        (account_id IS NOT NULL AND card_id IS NULL)
        OR (account_id IS NULL AND card_id IS NOT NULL)
    ),
    CONSTRAINT transaction_amount_is_not_negative CHECK (amount >= 0),
    CONSTRAINT transaction_rate_is_positive CHECK (fx_rate > 0)
);

CREATE INDEX transaction_by_user ON transaction_entry (user_id);
CREATE INDEX transaction_by_user_and_date ON transaction_entry (user_id, entry_date);
CREATE INDEX transaction_by_category ON transaction_entry (category_id);
