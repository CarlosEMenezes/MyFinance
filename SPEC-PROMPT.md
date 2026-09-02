# Budget Tracker — Build Specification & Working Agreement

You are implementing **Budget Tracker**, a responsive web application (React + TypeScript) backed by a **Java 17 / Spring Boot** REST API. It must be built so it can later be wrapped as a mobile app for Android and iOS, so the frontend is mobile-first responsive and all business logic lives in the backend or in framework-agnostic TypeScript modules — never in JSX.

A clickable HTML prototype of every screen exists and is the visual and behavioural reference. Read it before writing code. Where this document and the prototype disagree, this document wins.

---

## 0. How you must work (non-negotiable)

These rules govern every task in this project.

### 0.1 Create `CLAUDE.md` first

Before writing any production code, create a `CLAUDE.md` at the repository root. It is the project's persistent memory so that work does not depend on the context window. It must contain, and be **updated at the end of every task**:

- Project purpose and current phase.
- The full stack, versions and folder conventions.
- The domain glossary (Account, Pocket, Card, Category, Plan, Transaction, Instalment Plan, Loan, Goal, Notification).
- All business rules, restated as short numbered invariants (copy §3 of this document and keep it in sync).
- Architectural decisions, with a one-line rationale each (an ADR log).
- The test strategy and the current coverage floor.
- A "Done / In progress / Next" section, updated as work proceeds.
- A "Gotchas" section: anything you discovered the hard way, so it is never rediscovered.

Keep `CLAUDE.md` under ~500 lines. When a topic grows past that, move it to `docs/<topic>.md` and link it from `CLAUDE.md`.

### 0.2 Do not rely on the context window

- Persist decisions to disk (`CLAUDE.md`, `docs/`, ADRs) rather than holding them in conversation.
- When you need information from a large codebase, retrieve it deliberately: grep/search for the relevant files, read only those, and summarise findings back into `CLAUDE.md` or the task notes. Treat the repository as a retrieval corpus (RAG), not as something to be read whole.
- For long or repetitive work, loop: pick one item, complete it fully (test → code → refactor → commit → update `CLAUDE.md`), then pick the next. Never fan out across many half-finished items.

### 0.3 Test-Driven Development

- **Red → Green → Refactor**, always, for every unit of behaviour. Write the failing test first, then the minimum code to pass, then refactor with the tests green.
- **Never delete, skip, comment out, weaken or `@Disabled`/`.skip()` a test to make a build pass.** If a test fails, either the code is wrong (fix the code) or the specification changed (change the test deliberately, in its own commit, with the reason recorded in `CLAUDE.md`). Deleting tests is treated as breaking the build.
- Every bug fix begins with a failing regression test that reproduces the bug.
- Coverage floor: 90% on domain/business-rule code (`domain`, `service`, and the frontend `lib/` calculation modules), 70% overall. The build fails below the floor.
- Test naming states behaviour, not implementation: `expenseAfterClosingDayRollsToNextStatement()`, not `testBillDate2()`.

### 0.4 Finish one step before starting the next

Work strictly sequentially. A step is done when: tests written and green, code reviewed against the clean-code rules below, linter and formatter clean, documentation and `CLAUDE.md` updated, and the change committed. Do not begin the next step, do not scaffold ahead, do not leave TODOs standing in place of work. If a step turns out to be bigger than expected, split it and finish the first half properly.

### 0.5 Clean code

- Small functions, one reason to change, intention-revealing names, no abbreviations.
- No magic numbers or strings — named constants or configuration.
- No commented-out code, no dead code, no speculative generality.
- Comments explain *why*, never *what*. Code explains what.
- Guard clauses over nested conditionals. Maximum nesting depth 3.
- Immutability by default: `record` and `final` in Java, `readonly` and pure functions in TypeScript.
- Every money value is a `BigDecimal` in Java (scale 2, `RoundingMode.HALF_UP`) and integer **minor units** (cents) in TypeScript. **Never use `double`/`float`/JS `number` for money arithmetic** other than for display-only percentages.
- Dates are `LocalDate`, never `Date` or strings, inside the domain.

### 0.6 One reusable component per file

Every reusable UI component is developed individually, in its own folder, in isolation from the screens that consume it:

```
src/components/<ComponentName>/
  <ComponentName>.tsx          # presentation only, no data fetching
  <ComponentName>.test.tsx     # written first
  <ComponentName>.types.ts     # props contract
  index.ts                     # public export
```

A component is built and tested against its props before any page uses it. Pages compose components; components never import pages, never call the API, and never read global state directly — data arrives through props, actions leave through callbacks. This is what keeps maintenance cost down: a change to a component is a change to one folder with its own tests.

### 0.7 Privacy and data minimisation

Budget Tracker holds a complete picture of a person's finances. Later phases
add capture mechanisms that read data the user did not type. These rules govern
all of them.

- **Minimise at the point of capture.** Parse on the device that captured the
  data and keep only the parsed fields. Raw notification text, raw spreadsheet
  cells and raw bank payloads are working data, never the record.
- **Nothing leaves the device without an explicit, revocable opt-in**, granted
  per capability and shown in Settings with a plain statement of what is sent.
- **Retention is bounded and stated.** Captured material not converted into a
  Transaction is purged after 30 days. The user can purge it immediately.
- **Credentials for third parties are never held by the frontend**, never
  logged, and encrypted at rest with a key that is rotatable without a data
  migration.
- **Every capture is attributable.** A Transaction records how it entered the
  system, so a user can always answer "why is this here?".
- A capability the user has not enabled must be **inert**, not merely hidden.

---

## 1. Product summary

Budget Tracker helps a person plan, organise and understand their financial situation. It is aimed at young people with irregular income — freelance work, part-time shifts, occasional jobs — who need to see the difference between what they **planned** and what actually **happened**, and to know what is coming due.

The core idea running through every screen: **every category carries a plan and a reality, and the app always shows both plus the variance.**

### Pages

1. **Overview (dashboard)** — total position, planned vs real breakdown, upcoming payments, category spend, accounts.
2. **Earnings** — income by category with plan vs real, jobs with rates, loans received.
3. **Expenses** — outgoings by category with plan vs real, payment method used, credit-card timing, instalment plans.
4. **Plan acquisition (goals)** — savings goals with progress, required contribution per period, and a what-if calculator.
5. **Accounts** — cash, bank accounts, savings and pockets.
6. **Cards** — debit and credit cards, each assigned to an account; limits, closing and due days.
7. **Categories & plan** — create categories, group them, and set every planned amount.
8. **Notifications** — everything coming due, with configurable lead times.
9. **Settings** — profile, default currency, date format, preferences.
10. **Import** — bring an existing finance spreadsheet in: column mapping, a
    worked example of the expected model, a downloadable pre-filled template,
    and a staged review of every row before anything reaches the ledger.

The clickable prototype covers pages 1–9 only. Pages and surfaces added after
it — Import, the detected-transaction review queue, tag and recurrence
controls — have no prototype reference. They are designed from the Industry
design system and assembled from the §5 component library, and are held to the
same visual system as everything else.

---

## 2. Domain model

Use these names verbatim in both codebases.

### User
`id`, `name`, `age`, `role`, `country`, `payCycle` (`WEEKLY | FORTNIGHTLY | MONTHLY | IRREGULAR`), `defaultCurrency` (ISO 4217), `dateFormat`, `weekStart`, `preferences`.

`preferences`: `autoConvertForeignAmounts` (bool), `roundGoalContributionsUp` (bool), `carryUnspentBudget` (bool).

### Account
`id`, `userId`, `name`, `kind` (`CASH | BANK | SAVINGS`), `balance` (Money), `currency`, `includeInTotals` (bool), `note`.

### Pocket
A named sub-balance inside an Account (typically a savings account). `id`, `accountId`, `name`, `balance`. A pocket's balance is part of its parent account's balance, never counted twice.

### Card
`id`, `userId`, `name`, `kind` (`CREDIT | DEBIT`), `accountId` (the account it settles from), and for credit cards: `creditLimit` (Money), `currentBalance` (Money, amount used), `closingDay` (1–28), `dueDay` (1–28).

### Category
`id`, `userId`, `type` (`EXPENSE | EARNING`), `name`, `group`, `plannedAmount` (Money), `plannedFrequency` (`WEEKLY | FORTNIGHTLY | MONTHLY`), `archived` (bool).

Default groups — expense: `Fixed`, `Variable`, `Debt`, `Ungrouped`. Earning: `Employment`, `Self-employed`, `Occasional`, `Ungrouped`. Groups are user-editable strings; the defaults are seeds, not an enum.

### Transaction
`id`, `userId`, `type` (`EXPENSE | EARNING | SAVING`), `categoryId`, `amount` (Money, **in the currency it was logged in**), `currency`, `amountInDefaultCurrency` (Money, computed at log time), `fxRate` (the rate used), `date` (LocalDate), `paymentMethod` (accountId or cardId), `note`, `instalmentPlanId` (nullable), `loanId` (nullable).

### InstalmentPlan
`id`, `userId`, `cardId`, `label`, `cashPrice` (Money), `instalmentCount` (int), `instalmentAmount` (Money), `frequency`, `instalmentsPaid` (int), `firstDueDate`.

### Loan
`id`, `userId`, `label`, `principal` (Money, received), `instalmentCount`, `instalmentAmount` (Money), `frequency`, `instalmentsPaid`, `firstDueDate`, `depositAccountId`.

### Job
`id`, `userId`, `categoryId`, `name`, `rateType` (`FIXED_PER_JOB | HOURLY | PER_SESSION`), `rate` (Money), `unitsLogged`, `status`.

### Goal
`id`, `userId`, `name`, `targetAmount` (Money), `targetDate`, `savedAmount` (Money), `contributionFrequency` (`DAILY | WEEKLY | MONTHLY`), `pocketId` (nullable), `rank` (int).

### Notification
Derived, not stored as user content: `key`, `label`, `detail`, `dueDate`, `amount`, `sourceType` (`CARD_BILL | LOAN | INSTALMENT | DIRECT_DEBIT | SUBSCRIPTION`), `readAt` (nullable — this *is* persisted).

### NotificationSettings
`leadDays` (subset of `{10, 5, 2}`), `channels` (`push`, `email`, `weeklySummary`).

### Tag
`id`, `userId`, `name`, `tone` (`TagTone`), `archived` (bool).

`TagTone` is a closed enum of exactly six values: `NEUTRAL`, `TAG_1` … `TAG_5`.
There is no colour picker and no free-form colour field. See BR-18.

A tag may be attached to a **Transaction**, a **Category** or a **Goal**.
Attachment is many-to-many and carries no meaning beyond grouping and
filtering.

### RecurrenceRule
`id`, `userId`, `frequency` (`RecurrenceFrequency`), `interval` (int ≥ 1),
`anchorDate` (LocalDate), `endMode` (`NEVER | ON_DATE | AFTER_OCCURRENCES`),
`endDate` (LocalDate, nullable), `occurrenceLimit` (int, nullable).

`RecurrenceFrequency` = `DAILY | WEEKLY | FORTNIGHTLY | MONTHLY | YEARLY`.
**This is a distinct type from the `plannedFrequency` / instalment `frequency`
used by BR-3, BR-6, BR-7 and BR-10.** See BR-17.

`interval` multiplies the frequency: `WEEKLY` with `interval` 2 is every two
weeks. `endDate` is set only when `endMode` is `ON_DATE`; `occurrenceLimit`
only when `endMode` is `AFTER_OCCURRENCES`.

### ImportedSheet
`id`, `userId`, `filename`, `contentHash`, `importedAt`, `columnMapping`,
`status` (`MAPPING | STAGED | COMMITTED | DISCARDED`), `rowCount`.

### StagedRow
`id`, `importedSheetId`, `rowNumber`, `rawValues` (the original cells),
`proposedType`, `proposedAmount`, `proposedCurrency`, `proposedDate`,
`proposedCategoryId`, `proposedPaymentMethodId`, `proposedTagIds`,
`confidence` (0–1, display only), `status`
(`PENDING | ACCEPTED | REJECTED | DUPLICATE | UNRESOLVED`),
`duplicateOfTransactionId` (nullable), `problems` (list of field-level
messages).

### DetectedTransaction
`id`, `userId`, `capturedAt`, `source` (`ANDROID_NOTIFICATION | OPEN_FINANCE |
SHARED`), `sourceLabel` (the app or institution), `parsedAmount`,
`parsedCurrency`, `parsedDate`, `parsedMerchant`, `suggestedCategoryId`
(nullable), `suggestedPaymentMethodId` (nullable),
`status` (`UNREVIEWED | LOGGED | DISMISSED | MATCHED`),
`matchedTransactionId` (nullable).

**This is not a `Notification`.** BR-12's `Notification` is derived, outbound
and about money going out in future. A `DetectedTransaction` is stored,
inbound, and about money that has already moved. The two must never share a
type, a table or a name.

### Transaction — added fields
`source` (`MANUAL | IMPORTED | DETECTED`), `tagIds` (list, may be empty),
`recurrenceRuleId` (nullable).

`amountInDefaultCurrency` and `fxRate` become **nullable**, and are null only
while a row is awaiting conversion under BR-21.

---

## 3. Business rules (implemented in the prototype — implement all of them)

Each rule gets at least one unit test named after it.

**BR-1 — Money now.**
`availableNow = Σ(account.balance where includeInTotals) + Σ(loan.principal for loans logged in this app) + Σ(logged earnings) − Σ(logged expenses not paid by credit card)`.
`owed = Σ(credit card currentBalance) + Σ(remaining instalments × amount) + Σ(remaining loan instalments × amount)`.
`totalMoneyNow = availableNow − owed`. It is displayed in red and may be negative.

Transactions awaiting conversion under BR-21 are excluded from every term of
this rule, and any figure that has excluded them must say so.

**BR-2 — Borrowing moves both sides.**
Logging a loan increases `availableNow` by the principal **and** increases `owed` by the full repayable amount (`instalmentCount × instalmentAmount`). The net effect on `totalMoneyNow` is therefore exactly the interest. A loan is **not** income and must never appear in the earnings breakdown.

**BR-3 — Loan repayments become planned expenses.**
Every loan with instalments outstanding contributes `instalmentAmount × periodsPerMonth(frequency)` to a derived, read-only planned expense row "Loan repayments", and produces an entry in Upcoming and in Notifications. Same for instalment plans, as "Card instalments".
`periodsPerMonth`: weekly = 52/12, fortnightly = 26/12, monthly = 1.

**BR-4 — Credit-card statement cycle.**
Given a purchase date, a card `closingDay` and a `dueDay`:
- If `purchaseDay <= closingDay`, the purchase joins the statement closing **this** month; otherwise the **next** month.
- The bill is due on `dueDay` of the closing month, plus one month when `dueDay <= closingDay`.

So a card closing on 25 and due on 5: a purchase on 20-08 is due 05-09; a purchase on 26-08 is due 05-10. A card closing on 10 and due on 28: a purchase on 05-08 is due 28-08; a purchase on 12-08 is due 28-09.
The planned-expense date for a card purchase is this computed bill date, **never** the purchase date. The log form must state the computed date to the user before saving.

**BR-5 — Debit cards have no cycle.** Spend on a debit card leaves its assigned account the same day.

**BR-6 — Instalments and implied interest.**
The user enters cash price `P`, instalment count `n`, instalment amount `A`, and frequency.
- `financedTotal = A × n`, `interest = financedTotal − P`.
- The plan is **interest free** when `interest <= 0.01 × n` (one cent of rounding per instalment is tolerated). In that case the displayed rate is exactly `0%` — do not run the solver on rounding noise.
- Otherwise solve the periodic rate `i` from the annuity present-value identity `P = A × (1 − (1+i)^−n) / i` by bisection over `(0, 3]` to convergence, then `APR = (1+i)^periodsPerYear − 1`, with `periodsPerYear` = 52 weekly, 26 fortnightly, 12 monthly. Cap the display at `>900% APR`.

**BR-7 — Loans use the same maths, mirrored.**
Principal received `P`, `n` instalments of `A`. Interest = `A×n − P`. The **settlement figure today** is the present value of the remaining instalments at the implied rate: `A × (1 − (1+i)^−remaining) / i`, or simply `remaining × A` when interest free. **Early-payoff saving** = `remaining × A − settlementFigure`. The UI must show this so a user can decide to pay a loan off upfront.

**BR-8 — Multi-currency.**
A transaction is stored in the currency it was logged in, together with `amountInDefaultCurrency` and the `fxRate` used at log time. Overviews and all totals show the default currency. Opening an individual entry shows the original logged currency and amount. Converted rows are marked with a currency tag in lists. Rates come from a live FX provider, cached, with the last-updated timestamp shown in the UI; a failed lookup blocks the save with a clear error rather than guessing a rate.

**BR-9 — Planned vs real.**
Every category row renders as a ghost "planned" line above the real line, plus a variance. Variance sign convention: for **earnings**, `real − planned` (over plan is good, green); for **expenses**, `real − planned` shown so that under plan is good (green) and over plan is bad (red). Zero variance is neutral grey. Never colour a variance without applying this convention.

**BR-10 — Period normalisation.**
Planned amounts are stored with their own frequency and normalised to the selected period for display. The plan summary always states the per-month equivalent.

**BR-11 — Goals.**
`gap = targetAmount − savedAmount`. Required contribution = `gap / periodsUntilTarget(frequency)` where periods are: daily = months × 30.4, weekly = months × 4.33, monthly = months. The what-if control lets the user move the target date (1–36 months) and change frequency, recomputing live. Feasibility compares the per-month requirement against current spare (`planned in − planned out`) and states either the surplus left over or the shortfall. Goals are ranked and show progress plus a pace marker.

**BR-12 — Notifications.**
The due-payment queue is derived from card bills, loan repayments, instalments, direct debits and subscriptions, sorted ascending by days remaining. An item is shown when `daysUntilDue <= max(enabled lead days)`. Enabled leads are any subset of {10, 5, 2}. Unread count drives the navigation badge. Read state is per item and persisted.

**BR-13 — Accounts and pockets.**
An account may be excluded from totals (`includeInTotals = false`) and is then labelled as out of totals everywhere. Pockets sit inside an account; their balances are already part of the parent balance and must not be double-counted.

**BR-14 — Categories drive the plan.**
Creating a category creates its planned amount and frequency. Planned amounts are editable both on the Categories page and inline on the Earnings/Expenses tables. Derived rows (loan repayments, card instalments) are read-only and must be rendered as text, not inputs.

**BR-15 — View state.**
Earnings, Expenses and Plan acquisition each support grouping, sorting and
filtering. The three do not share axes, because they do not share a shape.

| Page | Group by | Sort by | Filter by |
| --- | --- | --- | --- |
| Earnings | none / group / frequency / tag | category, planned, real, variance | tag |
| Expenses | none / group / account / tag | category, planned, real, variance | payment method, tag |
| Plan acquisition | none / tag | rank, target date, progress, amount remaining | tag |

Goals carry no planned/real pair and no payment method, so they take the axes
BR-11 already defines rather than inheriting the category ones.

Displayed totals respect the active filter, while dashboard totals always cover
the whole period. View state is owned by the feature hook, never by a
presentational component.

**BR-16 — Recurrence is bounded.**
A `RecurrenceRule` generates occurrences from `anchorDate` forward, stepping by
`frequency × interval`, and stops according to `endMode`: never, on `endDate`
inclusive, or after `occurrenceLimit` occurrences. Occurrences before the
anchor are never generated, and none after the bound.

Inside the active window, counting is exactly BR-10 — real calendar dates,
month-length clamping, no averaging. Outside it the count is zero. `endMode`
`NEVER` is the default and must reproduce BR-10's existing behaviour unchanged;
every BR-10 test must still pass untouched.

**BR-17 — Two frequency vocabularies, kept apart.**
`RecurrenceFrequency` (`DAILY | WEEKLY | FORTNIGHTLY | MONTHLY | YEARLY`, with
an `interval`) applies **only** to recurrence rules.

The `Frequency` of BR-3, BR-6, BR-7 and BR-10 (`WEEKLY | FORTNIGHTLY |
MONTHLY`) applies to category plans, instalment plans and loans, and is **not
extended**. BR-6's `periodsPerYear` is defined for 52, 26 and 12 only; feeding
it a daily or yearly frequency would produce a meaningless APR. An instalment
plan or loan must never reference a `RecurrenceFrequency`, and the type system
must make that impossible rather than merely discouraged.

**BR-18 — Tags label, they do not act.**
A tag may be attached to a Transaction, a Category or a Goal. Attachment is
purely organisational: it adds a grouping and filtering dimension (BR-15) and
nothing else.

Tagging a Goal specifically does **not** allocate matching transactions toward
it. BR-11's `savedAmount` has exactly one source of truth and a tag must never
become a second. Auto-allocation, if ever built, is a separate feature with its
own rule, because it changes BR-11's arithmetic.

`TagTone` is a closed enum of six values — `NEUTRAL` plus `TAG_1` … `TAG_5`.
The five carry hue; all six are drawn from the design system's ramps at the
**same OKLCH lightness step** as the existing accent tints, with the 100-step
as fill and the 800-step as text, so contrast is inherited rather than invented
per colour. There is no colour picker, no hex field, and no API that accepts a
colour value — only a tone name. Adding a seventh tone is a design-system
change, not a user action.

**BR-19 — An import is staged, never applied.**
Importing a sheet creates an `ImportedSheet` and a `StagedRow` per data row.
**Nothing reaches the ledger without explicit per-row confirmation.** A row
becomes a Transaction only when its status is `ACCEPTED` and the user commits.
Discarding an import removes the staged rows and leaves the ledger untouched.

A row that cannot be parsed into a valid Transaction is staged `UNRESOLVED`
with field-level `problems`, and cannot be accepted until they are resolved.
Import never partially succeeds silently: the commit reports how many rows were
written, skipped and rejected.

**BR-20 — Duplicates are flagged, not merged.**
Re-importing a sheet whose `contentHash` matches a previous `ImportedSheet`
warns before proceeding.

Within an import, a staged row matching an existing Transaction on date,
amount, currency and payment method is marked `DUPLICATE`, carries
`duplicateOfTransactionId`, and **defaults to not being imported**. The user
may override. The app never merges or edits an existing Transaction on the
strength of a match.

**BR-21 — Imported foreign amounts defer conversion.**
This is a scoped exception to BR-8, which continues to govern manual logging
unchanged: a manual entry in a foreign currency still blocks on a missing rate.

An imported row carries a historical date, for which a live rate is the wrong
number. Such a row is stored with `amount` and `currency`, and with
`amountInDefaultCurrency` and `fxRate` **null**, in state
`AWAITING_CONVERSION`.

An unconverted Transaction is **excluded from every total** — from BR-1's
`availableNow` and `owed`, from BR-9 real figures, and from BR-15 totals. A
screen showing a total that has excluded rows must state how many, e.g.
"€2,412.30 · 7 entries awaiting conversion". A total that silently omits rows
is a wrong total. The app never guesses a rate to make a figure appear
complete.

Conversion is resolved later, per row, by supplying a rate for that row's date.

**BR-22 — Capture is a port; adapters are platform-specific.**
Detecting a transaction the user did not type is one domain concept with
several capture mechanisms behind a single port. The domain — the
`DetectedTransaction`, the matcher of BR-24, and the review queue of BR-23 — is
written once and is identical for every adapter.

Sanctioned adapters:

| Adapter | Platform | Mechanism |
| --- | --- | --- |
| Notification listener | Android only | Reads wallet and bank notifications with the user's explicit special-access grant |
| Open Finance | Both | Account aggregation under PSD2 or equivalent |
| Manual share | Both | The user shares a message or receipt into the app |

**iOS cannot read other applications' notifications.** There is no public API
for it, and none is expected. Any claim that the notification adapter is
cross-platform is false; iOS parity comes from the Open Finance adapter, not
from this one. A capability the current platform cannot provide is absent from
the UI, not shown disabled.

All capture is opt-in, off by default, and revocable. Parsing happens on the
capturing device, and raw captured text is subject to §0.7.

**BR-23 — A detection is a candidate, not a transaction.**
A `DetectedTransaction` never becomes a Transaction automatically. It sits
`UNREVIEWED` until the user opens the app and is prompted with what was found.
Logging one requires the user to confirm or choose a category; the app may
suggest, never decide. Dismissing one keeps it dismissed and does not
re-present it.

The resulting Transaction records `source = DETECTED`.

**BR-24 — A detection is matched before it is offered.**
Before a candidate is shown, it is matched against existing Transactions on
amount, currency, a date window of ±3 days, and payment method where known. A
match sets status `MATCHED` and the candidate is not offered, so an expense the
user already logged by hand is never presented twice.

Matching is advisory and non-destructive: it never edits, merges or deletes the
Transaction it matched.

**BR-25 — One column contract, three consumers.**
The spreadsheet column contract is defined once, in
`docs/sheet-import-format.md`, and has exactly three consumers: the parser, the
downloadable template, and the explanation shown on the Import page.

The template is **generated per user** and pre-filled with that user's own
categories, accounts and cards, so the columns a person has to fill in are
already meaningful to them. It is produced from the same contract as the
parser, and a test asserts that a freshly generated template parses cleanly
through the parser with no problems reported. A change to the contract that
breaks any of the three fails the build.

---

## 4. Backend — Java 17 + Spring Boot

### Stack
Java 17, Spring Boot 3.x, Spring Web, Spring Data JPA, Spring Validation, Spring Security (JWT), Flyway, PostgreSQL (H2 for tests), Maven, JUnit 5, AssertJ, Mockito, Testcontainers, ArchUnit.

### Architecture — hexagonal, enforced by ArchUnit

```
ie.budgetTracker
  domain/          entities, value objects, domain services — NO Spring, NO JPA annotations
  application/     use-case services, ports (interfaces), DTOs, mappers
  infrastructure/  JPA adapters, FX client, schedulers, security
  api/             REST controllers, request/response records, exception handling
```

Dependency rule: `api → application → domain`, `infrastructure → application`. `domain` depends on nothing. Write the ArchUnit test that enforces this in the first sprint and never weaken it.

### Domain services (pure, unit-tested first, no framework)
- `MoneyCalculator` — BigDecimal arithmetic, scale 2, HALF_UP.
- `StatementCycleCalculator` — BR-4.
- `InstalmentCalculator` — BR-6, including the bisection solver, tolerance rule and APR conversion.
- `LoanCalculator` — BR-7, including settlement figure and early-payoff saving.
- `PositionCalculator` — BR-1, BR-2.
- `PlanNormaliser` — BR-3, BR-10.
- `GoalCalculator` — BR-11.
- `DuePaymentQueue` — BR-12.
- `RecurrenceCalculator` — BR-16, BR-17. Occurrence generation and bounded
  counting. Must not accept a `Frequency` from BR-6's vocabulary, nor supply
  one.
- `SheetParser` — BR-19, BR-25. Cells to proposed values, with field-level
  problems.
- `SheetTemplateGenerator` — BR-25. The per-user pre-filled template, from the
  same contract the parser reads.
- `ImportDuplicateDetector` — BR-20.
- `DetectionMatcher` — BR-24.

Each is a plain class with a constructor and pure methods. These carry the highest test density in the project — table-driven tests over boundary cases (closing day 1, 28, due before/after closing, month-end, leap years, zero interest, one instalment, interest-free with rounding).

### API surface (REST, JSON, `/api/v1`)
`/users/me`, `/accounts`, `/accounts/{id}/pockets`, `/cards`, `/categories`, `/transactions`, `/instalment-plans`, `/loans`, `/jobs`, `/goals`, `/notifications`, `/notifications/settings`, `/dashboard`, `/fx/rates`.

Standard CRUD verbs plus:
- `POST /transactions` accepts an optional instalment block and creates the plan atomically.
- `POST /loans` records principal, terms and deposit account in one transaction.
- `GET /dashboard?period=MONTH&from=&to=` returns the whole overview payload in one call, computed server-side. **The frontend must not recompute business figures.**
- `POST /instalment-plans/preview` and `POST /loans/preview` return the interest calculation without persisting, so the log form can show live figures.

Conventions: 400 with a field-level error list for validation, 404 for unknown ids, 409 for domain-rule violations, RFC 7807 `application/problem+json` bodies. Idempotency key on all POSTs that create money records. No entity is ever exposed directly — always a DTO record.

Added endpoints: `/tags`, `/recurrence-rules`, `/detected-transactions`,
and `/imports` with `/imports/{id}/rows`, `POST /imports/{id}/commit`,
`GET /imports/template` (returns the pre-filled CSV of BR-25).

`POST /imports` accepts the file and returns a staged sheet; it never writes to
the ledger. Commit is the only endpoint that does, and it reports counts of
written, skipped and rejected rows.

### Testing layers
1. Domain unit tests (fast, no Spring).
2. Application service tests with mocked ports.
3. `@DataJpaTest` for repositories.
4. `@WebMvcTest` for controllers, asserting status codes and payload shape.
5. Full-stack integration tests on Testcontainers Postgres for each use case.
6. ArchUnit tests for the dependency rule and for "no JPA annotations in domain".

---

## 5. Frontend — React

### Stack
React 18 + TypeScript (strict), Vite, React Router, TanStack Query for server state, React Hook Form + Zod for forms, Vitest + React Testing Library, MSW for API mocking, Playwright for end-to-end flows, ESLint + Prettier. No component library — the visual system is defined below.

### Structure

```
src/
  components/     one folder per reusable component (see §0.6)
  features/       one folder per domain area: dashboard, earnings, expenses, goals,
                  accounts, cards, categories, notifications, settings
                  each with: api.ts, hooks.ts, <Feature>Page.tsx, components/, __tests__/
  lib/            money.ts, dates.ts, formatting.ts, period.ts — pure, 100% tested
  types/          shared domain types mirroring the API contract
  test/           MSW handlers, fixtures, render helpers
```

### Rules
- **Money in the frontend is integer minor units.** Format only at the edge, via `lib/money.ts`.
- Components are presentational and take props; data comes from feature hooks built on TanStack Query. No `fetch` inside a component.
- No business calculation in the frontend. The one exception is optimistic what-if UI (the goal slider and the instalment preview), which must call the corresponding `/preview` endpoint or use a shared, separately tested pure function in `lib/` — never inline arithmetic in JSX.
- Every form validates with a Zod schema that mirrors the backend contract.
- Accessibility is part of "done": labelled inputs, keyboard-operable controls, visible focus ring, 44px minimum touch targets, correct roles for tabs and dialogs.
- Responsive: sidebar navigation on desktop; below 940px the primary pages move to a bottom tab bar and the setup pages to an icon row in the top bar.

### Reusable components to build individually (each in its own folder, tested first)
`Panel` (bordered frame with corner marks), `KpiCard`, `PlanVsRealTable`, `GhostPlanRow`, `EditablePlanCell`, `SegmentedControl`, `TagChip`, `ProgressBar` (with pace marker), `MoneyText`, `VarianceText`, `AccountCard`, `CardSummary`, `NotificationRow`, `LeadTimeToggle`, `Checkbox`, `Dialog`, `LogEntryForm`, `InstalmentCalculatorPanel`, `WhatIfPanel`, `SidebarNav`, `BottomTabBar`, `PageHeader`, `PeriodPicker`, `FilterChips`, `EmptyState`.

Build them in that order. A page is only assembled once its components exist and are green.

Added components, each in its own folder per §0.6: `TagPicker`,
`RecurrenceEditor`, `ColumnMappingTable`, `ImportRowReview`,
`DetectedTransactionCard`, `AwaitingConversionNotice`.

`TagChip` gains a `tone` prop constrained to `TagTone`. It accepts a tone name,
never a colour.

BR-16's bounded counting extends `lib/period.ts` rather than adding a parallel
module, and every existing BR-10 test must remain green and unmodified.

### Visual system (follow exactly — it is a wireframe/blueprint aesthetic)
- Ground `#f2f2f3`, text `#1d1f20`, single steel accent `#5980a6`, with a 100–900 tonal ramp. Over-plan red `#8f3a3a`, under-plan green `#3d6b4a`.
- Barlow Condensed for headings, Barlow for body.
- Square corners everywhere. Cards and panels are transparent line drawings with a 1px hairline border and four `+` registration marks at the corners. The primary button is the only solid filled object.
- Tabular numerals for every money figure, right-aligned in tables.
- No decorative colour beyond the accent; no emoji; icons are Lucide at stroke-width 1.5.

---

## 6. Delivery plan (finish each step before starting the next)

1. **Foundation** — repo layout, CI (build + test + coverage gate + lint), `CLAUDE.md`, ArchUnit rules, Flyway baseline, Money value object with full test suite.
2. **Identity & settings** — user, profile, preferences, default currency; auth.
3. **Accounts & pockets** — BR-13, plus the Accounts page and its components.
4. **Cards** — BR-4, BR-5, `StatementCycleCalculator` with exhaustive boundary tests, then the Cards page.
5. **Categories & plan** — BR-14, BR-10, then the Categories page with inline editing.
6. **Transactions** — logging with multi-currency (BR-8), card-cycle placement (BR-4), then the Earnings and Expenses pages with plan-vs-real, grouping, sorting and filtering (BR-9, BR-15).
7. **Financing** — instalment plans and loans (BR-6, BR-7, BR-2, BR-3), including the preview endpoints and the calculator panels.
8. **Dashboard** — BR-1, the aggregate endpoint, then the Overview page.
9. **Goals** — BR-11, including the what-if calculator.
10. **Notifications** — BR-12, the derived queue, read state, lead-time settings, and the badge.
11. **Hardening** — Playwright journeys for the five critical flows, performance pass, accessibility audit, documentation.

At each step: tests first, one component per folder, `CLAUDE.md` updated, commit, then move on.

## 6.1 Phase 2 — reducing manual entry

Begins only when steps 1–11 are complete and the app is running end to end.

12. **Tags, recurrence and bounds** — BR-16, BR-17, BR-18, and the BR-15
    amendment. First, because it is the only one of the three that changes
    existing rules and existing tested code, and both later steps attach tags
    and recurrence to what they create.
13. **Sheet import** — BR-19, BR-20, BR-21, BR-25, and the Import page. Its
    staged-review flow is the model step 14 reuses.
14. **Detection, Android adapter** — BR-22, BR-23, BR-24, the capture port, and
    the review queue. The port is defined here even though only one adapter
    exists, so Phase 3 adds an adapter rather than a rewrite.

## 6.2 Phase 2.5 — identity hardening

Prerequisite for Phase 3. No third-party sign-in is built.

15. **Account security** — Argon2id password hashing, TOTP second factor
    (RFC 6238), ten single-use recovery codes issued once at enrolment, and
    rate limiting on all authentication endpoints.

    SMS second factors are **not** used: they cost per message and are the
    weakest common factor. Social sign-in is **not** built: it does not reduce
    the security work, and account-linking between an email signup and a
    provider on the same address is a takeover vector for no product gain.

## 6.3 Phase 3 — Open Finance

16. **Account aggregation** — the second capture adapter behind BR-22's port,
    and the route by which iOS reaches parity.

    Note that "OAuth" here means Budget Tracker acting as a **client** holding
    consent tokens for a financial institution. That is a different feature
    with a different risk profile from signing a user in with a social
    provider, which §6.2 deliberately does not build. Requires: the consent
    lifecycle including expiry and re-authorisation, encrypted token custody
    with rotatable keys, and an ADR recording the trade-off that an aggregator
    sees the user's full transaction history.

---

## 7. Definition of done (every task)

- [ ] Failing test written before the implementation.
- [ ] All tests green; **no test deleted, skipped or weakened**.
- [ ] Coverage floors met.
- [ ] Business rules referenced by their BR number in the test names.
- [ ] Reusable UI built in its own folder with its own tests.
- [ ] No business logic in JSX; no floating-point money.
- [ ] Lint and format clean; no dead or commented-out code.
- [ ] `CLAUDE.md` updated (decisions, gotchas, Done/Next).
- [ ] Committed with a message that names the behaviour, not the files.
