-- Spec §6 step 5: categories and the plan they carry (BR-14, BR-10).
--
-- Every column of the plan is NOT NULL. BR-14 says creating a category creates
-- its planned amount and frequency, so a row without them is not an incomplete
-- category, it is not a category.
CREATE TABLE category (
    id                UUID           PRIMARY KEY,
    user_id           UUID           NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    type              VARCHAR(20)    NOT NULL,
    name              VARCHAR(200)   NOT NULL,
    -- "group" is reserved in SQL, so the column carries the prefix and the
    -- entity maps it. Quoting the reserved word instead would leak into every
    -- hand-written query for the life of the schema.
    category_group    VARCHAR(200)   NOT NULL,
    -- Per occurrence. What it comes to over a period is BR-10's answer, which
    -- changes with the window and is therefore never stored.
    planned_amount    NUMERIC(19, 2) NOT NULL,
    planned_frequency VARCHAR(20)    NOT NULL,
    -- BR-10 counts from here. A weekly plan anchored to the 1st and one
    -- anchored to the 5th cost different amounts in the same month.
    anchor_date       DATE           NOT NULL,
    archived          BOOLEAN        NOT NULL DEFAULT FALSE,
    -- Per type, not per user: "Tutoring" can be something earned and something
    -- paid for, and the two live in different tables on every screen.
    CONSTRAINT category_name_is_unique_per_user_and_type UNIQUE (user_id, type, name),
    CONSTRAINT category_plan_is_not_negative CHECK (planned_amount >= 0)
);

CREATE INDEX category_by_user ON category (user_id);
