# Design reference — mapping the prototype to the build

The visual and behavioural reference is the Claude Design handoff bundle, kept at the repo root as `Financial Planning Web App-handoff.zip`. Extract it to read along:

```bash
unzip -o "Financial Planning Web App-handoff.zip" -d /tmp/handoff
```

Line numbers below refer to `financial-planning-web-app/project/Budget Tracker.dc.html` (2157 lines).

## What is in the bundle

| File | What it is | Do we port it? |
|---|---|---|
| `Budget Tracker.dc.html` | All nine screens plus the Log-entry dialog in one `x-dc` template, with a `DCLogic` subclass supplying every value | The markup is the visual spec; the `DCLogic` maths is the reference for `lib/` |
| `_ds/…/styles.css` | The "Industry" design system — tokens and component classes | **Yes, verbatim** → `frontend/src/styles/tokens.css` |
| `_ds/…/readme.md` | Written guidance for the system | Read it; it explains the intent behind the tokens |
| `_ds/…/_ds_bundle.js` | Empty stub — declares a namespace, exports nothing | No. There is nothing in it. |
| `support.js` | Claude Design's prototype runtime (`sc-for`, `sc-if`, `DCLogic`) | **No.** Prototype scaffolding. |

The `<helmet>` block (lines 13–34) carries the shell and grid CSS — ported to `frontend/src/styles/app.css`.

## Business rules → prototype source

The prototype already implements the rules. These are the reference implementations; each gets tests written **first**, named after its BR number.

| Prototype (line) | Rule | TypeScript | Java |
|---|---|---|---|
| `occurrencesIn` (1208) | BR-10 | `lib/period.ts` | `PlanNormaliser` |
| `impliedRate`, `apr`, `financeStats` (1305–1350) | BR-6 | `lib/instalments.ts` | `InstalmentCalculator` |
| `billDate` (1324) | BR-4 | `lib/statementCycle.ts` | `StatementCycleCalculator` |
| `mkRow` `invert` flag (1464–1476) | BR-9 | `lib/variance.ts` | — |
| `liquid`, `owed`, `netNow` (1548–1557) | BR-1, BR-2 | — | `PositionCalculator` |
| `goals`, `per`, `feasible` (1559–1598) | BR-11 | `lib/goals.ts` | `GoalCalculator` |
| `dueAll`, `enabledLeads`, `unreadCount` (1386–1411) | BR-12 | — | `DuePaymentQueue` |
| loan `settle`, `saveStr` (1741–1753) | BR-7 | `lib/loans.ts` | `LoanCalculator` |

### Two recurrence models — both correct, do not unify

The prototype's `occurrencesIn` counts **real dates**: a month holding five paydays plans five, never 52/12. Spec BR-3 uses the `periodsPerMonth` average (weekly 52/12, fortnightly 26/12, monthly 1).

Both are right, in different places:

- **Category plans** use real-date counting (BR-10). The user set "€160 each week" and wants the five weeks this month.
- **Derived loan and instalment rows** use the 52/12 average (BR-3), because they are a smoothed commitment figure, not a schedule.

Each gets its own test. The prototype states this itself at line 1204 and again in `planWindowNote` (2022).

## Component ↔ design mapping

Build order is spec §5's list. Each lives in its own folder with its own tests before any page uses it (§0.6).

| Component | Design source |
|---|---|
| `Panel` | `.card.blueprint` + four `<i class="corner …">`, e.g. line 122 |
| `KpiCard` | line 122 — kicker, 31px condensed figure, note |
| `PlanVsRealTable`, `GhostPlanRow`, `EditablePlanCell` | lines 153–168: italic ghost "planned" row above the real row |
| `SegmentedControl` | `.seg` / `.seg-opt`, line 104 |
| `TagChip` | `.tag-accent` / `-neutral` / `-outline` |
| `ProgressBar` | line 208 — bordered bar, accent fill, 1px pace tick |
| `MoneyText`, `VarianceText` | tabular numerals + the BR-9 colour convention |
| `AccountCard` | line 588 — balance, kind tag, nested pockets, "out of totals" |
| `CardSummary` | line 678 — usage bar, closing/due/next-bill triple, cycle note |
| `NotificationRow` | line 784 — days-remaining numeral, tag, amount, read toggle |
| `LeadTimeToggle`, `Checkbox` | line 809 — the square 15px box |
| `Dialog` | `.dialog-backdrop`, line 1025 |
| `LogEntryForm` | lines 1028–1140 — type segment, FX preview, card-timing hint |
| `InstalmentCalculatorPanel` | `fin.lines` + verdict block, lines 1123–1133 |
| `WhatIfPanel` | line 536 — months slider, frequency segment, feasibility |
| `SidebarNav` | line 39 |
| `BottomTabBar` | line 1014 |
| `PageHeader` | line 97 |
| `PeriodPicker` | line 104 |
| `FilterChips` | line 405 |
| `EmptyState` | not in the prototype — design it from the system |

## Fixtures

The prototype's `state` block (1236–1294) is realistic seed data — accounts, cards, instalment plans, loans, earnings, expenses, goals. Use it for MSW handlers and test fixtures so screens are exercised against the same numbers the design was drawn with.

Two values in it are fixtures, not behaviour: "today" is hard-coded to **31-08-2026** (line 1356) and `RATES` (1194) is a frozen FX table. Real code takes an injected clock and a live FX provider (BR-8).
