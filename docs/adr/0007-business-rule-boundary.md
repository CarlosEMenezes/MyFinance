# ADR-7 — Where a business rule may live

**Status:** Accepted
**Date:** 2026-09-02

## Context

Spec §5 says "No business calculation in the frontend", and then names an exception: the goal slider and the instalment preview "must call the corresponding `/preview` endpoint **or** use a shared, separately tested pure function in `lib/`". Spec §4 says `GET /dashboard` is "computed server-side. The frontend must not recompute business figures."

Meanwhile spec §4 requires Java domain services — `StatementCycleCalculator`, `InstalmentCalculator`, `LoanCalculator`, `PlanNormaliser`, `GoalCalculator` — that implement BR-4, BR-6, BR-7, BR-10 and BR-11. The `lib/` modules already implement the same rules in TypeScript.

So two implementations of several rules will exist, and without a stated boundary each new screen is an opportunity to put a figure on the wrong side.

An audit before writing any page found the leak was small: only `CardSummary` computed a persisted business figure (BR-4 bill dates), and `lib/period`, `lib/instalments`, `lib/loans` and `lib/goals` had no component consumer at all.

## Decision

**The frontend may compute a figure the user has not saved yet. Every figure that comes back from the server is rendered, never recomputed.**

| Concern | Owner | Why |
|---|---|---|
| Money and date formatting and parsing (`lib/money`, `lib/dates`) | Frontend | Spec §5: "Format only at the edge, via `lib/money.ts`" |
| BR-9 variance sign and tone (`lib/variance`) | Frontend | A display convention, not a figure. BR-14 inline editing changes `planned` on every keystroke and the variance must follow without a round-trip |
| Optimistic pre-save preview (`lib/instalments`, `lib/loans`, `lib/goals`, `lib/period`, `lib/statementCycle`) | Frontend, **live feedback only** | Spec §5's stated exception. BR-4's "the log form must state the computed date to the user before saving" is the same category |
| BR-1, BR-2, BR-3, BR-10 totals, BR-12, and the authoritative placement or settlement of anything **persisted** | Backend | Spec §4 |

Concretely: the Cards page renders a `nextBillDate` the API sends; the log form computes the bill date locally as the user types, and the value that is stored is the one the server computes on save.

## Consequences

- The API contract in `frontend/src/types/api.ts` must carry every computed field the backend owes — `variance`, `nextBillDate`, `settlementFigure`, `earlyPayoffSaving`, `impliedApr`, `occurrencesInPeriod`, `monthlyEquivalent`. If a screen needs a figure, it is a field, not a calculation.
- `CardSummary` receives its three dates as props rather than importing `lib/statementCycle`.
- **The duplication is real and cannot be designed away**, because the spec asks for both. The defence is that both sides are tested against the *same numeric vectors*, extracted to `docs/business-rule-vectors.md`. A Java test and a TypeScript test asserting `0.02111472` for the same plan is the only thing that catches drift.
- A standing check: `grep -rl "lib/statementCycle\|lib/period\|lib/loans\|lib/instalments\|lib/goals" frontend/src/components` should return nothing. Those belong to feature-level form components, not to presentational ones.

## Alternative rejected

Delete the overlapping `lib/` modules and make every preview a round-trip to `/preview`. Rejected because the goal slider recomputes on every drag and the log form's date hint on every keystroke; a network round-trip per frame is the reason spec §5 offers the pure-function alternative in the first place.
