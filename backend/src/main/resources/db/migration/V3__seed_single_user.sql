-- The one profile the app currently serves.
--
-- Spec §6 step 2 pairs identity with auth; auth is not built yet, and the
-- frontend has no sign-in screen to build it against. Until it exists there is
-- exactly one user, seeded here with a fixed id so that every environment
-- agrees on which row `CurrentUser` resolves to.
--
-- This row is the placeholder, and it is a migration rather than code so that
-- it is versioned and removable in one step: when auth arrives, this becomes a
-- migration that deletes it and the adapter reads the authenticated principal
-- instead.
INSERT INTO app_user (
    id, name, age, role, country, pay_cycle, default_currency, date_format, week_start,
    auto_convert_foreign_amounts, round_goal_contributions_up, carry_unspent_budget
) VALUES (
    '00000000-0000-4000-8000-000000000001',
    'You', NULL, NULL, NULL, 'IRREGULAR', 'EUR', 'DD_MM_YYYY', 'MONDAY',
    TRUE, TRUE, FALSE
);
