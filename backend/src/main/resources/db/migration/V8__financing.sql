-- Spec §6 step 7: instalment plans and loans (BR-6, BR-7, BR-2, BR-3).
--
-- Neither table stores an interest figure. BR-6 solves the periodic rate from
-- the terms and BR-7 discounts the remaining instalments back to today, and
-- both answers change as instalments are paid. A stored APR would be a figure
-- that was true once, sitting beside terms that have moved on.
CREATE TABLE instalment_plan (
    id                UUID           PRIMARY KEY,
    -- Owned through the card, which is owned through its account, which has a
    -- user. One source of truth for ownership, as with `card` itself.
    card_id           UUID           NOT NULL REFERENCES card (id) ON DELETE CASCADE,
    label             VARCHAR(200)   NOT NULL,
    cash_price        NUMERIC(19, 2) NOT NULL,
    instalment_count  INTEGER        NOT NULL,
    instalment_amount NUMERIC(19, 2) NOT NULL,
    frequency         VARCHAR(20)    NOT NULL,
    instalments_paid  INTEGER        NOT NULL DEFAULT 0,
    -- BR-4: the first instalment lands on a bill date, not on the purchase day.
    first_due_date    DATE           NOT NULL,
    CONSTRAINT instalment_plan_terms_describe_a_real_plan CHECK (
        cash_price > 0 AND instalment_count > 0 AND instalment_amount > 0
        AND instalments_paid >= 0 AND instalments_paid <= instalment_count
    )
);

CREATE INDEX instalment_plan_by_card ON instalment_plan (card_id);

-- BR-2: a loan is not income. The principal lands in an account and the whole
-- repayment goes on the other side of the position, so the deposit account is
-- part of what a loan *is* rather than a note about it.
CREATE TABLE loan (
    id                 UUID           PRIMARY KEY,
    deposit_account_id UUID           NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    label              VARCHAR(200)   NOT NULL,
    principal          NUMERIC(19, 2) NOT NULL,
    instalment_count   INTEGER        NOT NULL,
    instalment_amount  NUMERIC(19, 2) NOT NULL,
    frequency          VARCHAR(20)    NOT NULL,
    instalments_paid   INTEGER        NOT NULL DEFAULT 0,
    first_due_date     DATE           NOT NULL,
    CONSTRAINT loan_terms_describe_a_real_loan CHECK (
        principal > 0 AND instalment_count > 0 AND instalment_amount > 0
        AND instalments_paid >= 0 AND instalments_paid <= instalment_count
    )
);

CREATE INDEX loan_by_account ON loan (deposit_account_id);

-- One column per statement: a multi-column ALTER is valid on PostgreSQL and a
-- syntax error on H2, where the migrations are checked (CLAUDE.md gotcha 35).
ALTER TABLE transaction_entry ADD COLUMN instalment_plan_id UUID REFERENCES instalment_plan (id);
ALTER TABLE transaction_entry ADD COLUMN loan_id UUID REFERENCES loan (id);
