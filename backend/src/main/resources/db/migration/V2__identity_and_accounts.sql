-- Spec §6 steps 2 and 3: the user, and where their money sits (BR-13).

CREATE TABLE app_user (
    id               UUID         PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    -- Optional on purpose: spec §0.7 asks for the minimum, and none of these
    -- change a figure. A profile with only a name is a usable profile.
    age              INTEGER,
    role             VARCHAR(200),
    country          VARCHAR(200),
    pay_cycle        VARCHAR(20)  NOT NULL,
    -- BR-8: every total is stated in this.
    default_currency VARCHAR(3)   NOT NULL,
    date_format      VARCHAR(20)  NOT NULL,
    week_start       VARCHAR(10)  NOT NULL,
    auto_convert_foreign_amounts BOOLEAN NOT NULL DEFAULT TRUE,
    round_goal_contributions_up  BOOLEAN NOT NULL DEFAULT TRUE,
    carry_unspent_budget         BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT app_user_age_is_plausible CHECK (age IS NULL OR (age BETWEEN 0 AND 150))
);

CREATE TABLE account (
    id                UUID           PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name              VARCHAR(200)   NOT NULL,
    kind              VARCHAR(20)    NOT NULL,
    -- ADR-6: minor units are the frontend's representation. Here money is a
    -- NUMERIC of fixed scale, so the database cannot hold a third decimal
    -- place that rounding would later have to invent an answer for.
    balance           NUMERIC(19, 2) NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    -- BR-13: false means labelled out of totals everywhere.
    include_in_totals BOOLEAN        NOT NULL DEFAULT TRUE,
    note              VARCHAR(500),
    CONSTRAINT account_name_is_unique_per_user UNIQUE (user_id, name)
);

CREATE INDEX account_by_user ON account (user_id);

-- BR-13: a pocket is a named sub-balance INSIDE an account. Its balance is
-- already part of the parent's and must never be added to it.
--
-- The foreign key is not decoration: there is no such thing as a free-floating
-- pocket, so the schema makes one unwriteable. ON DELETE CASCADE follows from
-- the same fact — a pocket without its account is not a pocket.
CREATE TABLE pocket (
    id         UUID           PRIMARY KEY,
    account_id UUID           NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    name       VARCHAR(200)   NOT NULL,
    balance    NUMERIC(19, 2) NOT NULL,
    CONSTRAINT pocket_name_is_unique_per_account UNIQUE (account_id, name)
);

CREATE INDEX pocket_by_account ON pocket (account_id);
