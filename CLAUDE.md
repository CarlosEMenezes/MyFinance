# Budget Tracker — project memory

This file is the project's persistent memory. It is updated at the end of **every** task.
Governing document: [SPEC-PROMPT.md](SPEC-PROMPT.md). Where this file and the spec disagree, the spec wins — and this file must then be corrected.

---

## 1. Purpose and current phase

Budget Tracker helps a person with irregular income plan, organise and understand their finances. Every category carries a **plan** and a **reality**, and the app always shows both plus the variance.

**Current phase:** §6 step 1 — Foundation. Repo layout, toolchain, CI, hexagonal skeleton, design-system tokens.

The visual and behavioural reference is a Claude Design prototype, exported as `Financial Planning Web App-handoff.zip` (kept at the repo root). It contains all nine screens. See [docs/design-reference.md](docs/design-reference.md) for the screen-by-screen map from the prototype to the components that implement it.

---

## 2. Stack and versions

| Layer | Choice | Version |
|---|---|---|
| JDK | BellSoft Liberica | **17** (`JAVA_HOME=C:\Program Files\BellSoft\LibericaJDK-17\`) |
| Framework | Spring Boot | **4.0.7** (see ADR-1 — spec says 3.x) |
| Build | Maven via `./mvnw` **only** | wrapper pins 3.9.16 |
| Database | PostgreSQL (prod) · H2 (test) · Testcontainers (integration) | — |
| Migrations | Flyway | — |
| Frontend | React + TypeScript strict, Vite | React **18.3**, Vite **6**, TS **5.7** |
| Routing / server state | React Router 6 · TanStack Query 5 | — |
| Test (Java) | JUnit 5 · AssertJ · ArchUnit 1.3 · H2 | — |
| Test (web) | Vitest **3** · RTL · jsdom | Vitest 3 is required — see gotcha 12 |
| Node | pinned in `.nvmrc` | **24** (LTS) |

Not yet added, because nothing uses them and spec §0.4/§0.5 forbid scaffolding ahead. Add each with the step that needs it: Spring Security + JWT (step 2), Testcontainers (first integration test), Mockito (first mocked-port test), React Hook Form + Zod (step 5), MSW (step 6), Playwright (step 11). `springdoc-openapi` was dropped from the inherited pom — its 2.x line targets Boot 3 and the spec never asks for OpenAPI; revisit if a Boot 4 line ships.

**Toolchain gotcha:** Maven **4.0.0-rc-5** is on `PATH` globally, but the wrapper pins **3.9.16**. Always invoke `./mvnw`, never bare `mvn`, so builds are reproducible.

### Folder conventions

```
BudgetTracker/
  CLAUDE.md  SPEC-PROMPT.md  .gitignore  .nvmrc
  docs/            adr/ + long-form topics split out of this file
  .github/workflows/ci.yml
  backend/         Maven project, package root ie.budgetTracker
    src/main/java/ie/budgetTracker/
      domain/          entities, value objects, domain services — NO Spring, NO JPA
      application/     use-case services, ports, dto/, mappers
      infrastructure/  JPA adapters, FX client, schedulers, security
      api/             REST controllers, request/response records, exception handling
  frontend/
    src/components/  one folder per reusable component (spec §0.6)
    src/features/    dashboard, earnings, expenses, goals, accounts, cards,
                     categories, notifications, settings
    src/lib/         money, dates, formatting, period, statementCycle,
                     instalments, loans, goals, variance — pure, 100% tested
    src/types/  src/styles/  src/test/
```

Dependency rule (ArchUnit-enforced): `api → application → domain`, `infrastructure → application`. `domain` depends on nothing.

### Running it

```bash
cd backend  && ./mvnw verify          # tests run on H2 — no database needed
docker compose up -d                  # postgres:17.5 on 127.0.0.1:5432
cd backend  && ./mvnw spring-boot:run # reads DB_USERNAME / DB_PASSWORD from the environment
cd frontend && npm ci && npm run dev  # fake API in-browser; see below
```

`compose.yml` pins **the same `postgres:17.5`** the Testcontainers integration
tests start, so local development, CI and the integration tests all meet one
database version. It binds to `127.0.0.1` only: the application is meant to be
reachable from other machines on the network, the database is not.

**To reach it from a phone**, run the backend with
`-Dspring-boot.run.profiles=local` and the frontend with `npm run dev:lan`. The
profile exists for one reason — see gotcha 36 — and must never be used
anywhere else.

`npm run dev` serves the app against MSW's browser worker, answering from the same `src/test/handlers.ts` the tests use, because the frontend was finished before the backend. Once a server is listening on :8085, `VITE_USE_MOCK_API=false npm run dev` proxies `/api` to it instead. A production build never contains the worker: `import.meta.env.DEV` is statically false, so the dynamic import is dropped.

`DB_USERNAME` and `DB_PASSWORD` have **no defaults** — a fallback password in version control is a credential in version control. The app fails at startup if they are unset. `.env` files are gitignored; never commit one. Secrets for the `dev`/`test`/`prod` GitHub environments: [docs/ci-secrets.md](docs/ci-secrets.md). **CI itself requires no secrets** — tests run on H2 and Testcontainers — and it must stay that way so pull requests from forks keep working.

---

## 3. Domain glossary

| Term | Meaning |
|---|---|
| **Account** | Where money sits: `CASH \| BANK \| SAVINGS`. May be excluded from totals. |
| **Pocket** | A named sub-balance **inside** an Account. Already part of the parent balance — never counted twice. |
| **Card** | `CREDIT \| DEBIT`, settles from an Account. Credit cards carry `creditLimit`, `currentBalance`, `closingDay`, `dueDay` (1–28). |
| **Category** | `EXPENSE \| EARNING`, in a user-editable `group`, carrying `plannedAmount` + `plannedFrequency`. |
| **Plan** | The set of planned amounts across all categories, normalised to the selected period. |
| **Transaction** | `EXPENSE \| EARNING \| SAVING`. Stored in the currency logged, plus `amountInDefaultCurrency` and the `fxRate` used. |
| **InstalmentPlan** | A card purchase split into `n` instalments of `A` against a `cashPrice`. |
| **Loan** | Money received as `principal`, repaid as `n` instalments of `A`. **Not income.** |
| **Job** | A source of earnings with a rate: `FIXED_PER_JOB \| HOURLY \| PER_SESSION`. |
| **Goal** | A savings target: `targetAmount` by `targetDate`, ranked, optionally bound to a Pocket. |
| **Notification** | **Derived** from card bills, loans, instalments, direct debits and subscriptions. Only `readAt` is persisted. |

---

## 4. Business rules — invariants

Each rule has at least one test **named after it**, referencing the BR number.

1. **BR-1 Money now.** `availableNow = Σ account.balance (includeInTotals) + Σ loan principals logged here + Σ earnings − Σ expenses not paid by credit card`. `owed = Σ card currentBalance + Σ remaining instalments×amount + Σ remaining loan instalments×amount`. `totalMoneyNow = availableNow − owed` — shown in red, may be negative.
2. **BR-2 Borrowing moves both sides.** A loan raises `availableNow` by the principal *and* `owed` by `instalmentCount × instalmentAmount`. Net effect on `totalMoneyNow` is exactly the interest. A loan never appears in the earnings breakdown.
3. **BR-3 Loan repayments become planned expenses.** Each loan with instalments outstanding contributes `instalmentAmount × periodsPerMonth(frequency)` to a derived read-only row "Loan repayments"; instalment plans likewise as "Card instalments". `periodsPerMonth`: weekly 52/12, fortnightly 26/12, monthly 1.
4. **BR-4 Credit-card statement cycle.** `purchaseDay <= closingDay` → statement closing **this** month, else **next**. Bill due on `dueDay` of the closing month, **plus one month when `dueDay <= closingDay`**. The planned-expense date is this computed bill date, never the purchase date, and the log form must state it before saving.
5. **BR-5 Debit cards have no cycle.** Spend leaves the assigned account the same day.
6. **BR-6 Instalments and implied interest.** `financedTotal = A×n`, `interest = financedTotal − P`. **Interest free when `interest <= 0.01 × n`** — display exactly `0%`, do not run the solver on rounding noise. Otherwise bisect `P = A(1−(1+i)^−n)/i` over `(0, 3]`, then `APR = (1+i)^periodsPerYear − 1` (52 / 26 / 12). Cap display at `>900% APR`.
7. **BR-7 Loans, mirrored.** Same maths. Settlement figure today = `A(1−(1+i)^−remaining)/i`, or `remaining × A` when interest free. **Early-payoff saving = `remaining × A − settlementFigure`** — must be shown.
8. **BR-8 Multi-currency.** Store the logged currency, `amountInDefaultCurrency` and `fxRate`. Totals in default currency; opening an entry shows the original. Converted rows carry a currency tag. Rates come from a live provider, cached, last-updated shown. **A failed lookup blocks the save with a clear error — never guess a rate.**
9. **BR-9 Planned vs real.** Every category row is a ghost "planned" line above the real line, plus a variance. Earnings: `real − planned` (over plan good/green). Expenses: shown so under plan is green, over plan red. Zero is neutral grey. Never colour a variance without this convention.
10. **BR-10 Period normalisation.** Planned amounts store their own frequency, normalised to the selected period. The plan summary always states the per-month equivalent.
11. **BR-11 Goals.** `gap = targetAmount − savedAmount`. Required contribution = `gap / periodsUntilTarget`, periods = daily `months×30.4`, weekly `months×4.33`, monthly `months`. What-if moves the date 1–36 months and changes frequency, live. Feasibility compares the per-month requirement to spare (`planned in − planned out`) and states the surplus or the shortfall. Goals are ranked with progress and a pace marker.
12. **BR-12 Notifications.** Derived queue from card bills, loans, instalments, direct debits, subscriptions, sorted ascending by days remaining. Shown when `daysUntilDue <= max(enabled lead days)`, leads any subset of {10, 5, 2}. Unread count drives the nav badge. Read state is per item and persisted.
13. **BR-13 Accounts and pockets.** `includeInTotals = false` → labelled out of totals everywhere. Pocket balances are already inside the parent and must not be double-counted.
14. **BR-14 Categories drive the plan.** Creating a category creates its planned amount and frequency. Planned amounts are editable on Categories **and** inline on Earnings/Expenses. Derived rows (loan repayments, card instalments) are read-only and rendered as **text, not inputs**.
15. **BR-15 View state.** Earnings, Expenses **and Plan acquisition** each group, sort and filter, on different axes because they have different shapes. Earnings: group none/group/frequency/tag, filter tag. Expenses: group none/group/account/tag, filter payment method + tag. Plan acquisition: group none/tag, sort rank/target date/progress/amount remaining, filter tag. Displayed totals respect the filter; **dashboard totals always cover the whole period**. View state lives in the feature hook, never in a component.


### Phase 2 rules (spec §6.1 onward — not yet implemented)

16. **BR-16 Recurrence is bounded.** A `RecurrenceRule` steps from `anchorDate` by `frequency × interval` and stops per `endMode` (`NEVER | ON_DATE | AFTER_OCCURRENCES`). Inside the window counting is **exactly BR-10** — real dates, month clamping, no averaging. `NEVER` is the default and **must reproduce BR-10 unchanged; every BR-10 test stays green and unmodified**.
17. **BR-17 Two frequency vocabularies, kept apart.** `RecurrenceFrequency` (`DAILY | WEEKLY | FORTNIGHTLY | MONTHLY | YEARLY` + `interval`) is for recurrence rules **only**. BR-3/6/7/10's `Frequency` (`WEEKLY | FORTNIGHTLY | MONTHLY`) is **not extended** — `periodsPerYear` is defined for 52/26/12 only and a daily or yearly value would yield a meaningless APR. The type system must make the mix impossible.
18. **BR-18 Tags label, they do not act.** A tag on a Transaction, Category or Goal adds grouping and filtering (BR-15) and nothing else. Tagging a Goal **never** allocates toward `savedAmount` — BR-11 has one source of truth. `TagTone` is a closed enum of six (`NEUTRAL` + `TAG_1`…`TAG_5`), drawn from design-system ramps at matched OKLCH lightness. No picker, no hex, no API that accepts a colour.
19. **BR-19 An import is staged, never applied.** A sheet becomes an `ImportedSheet` plus one `StagedRow` per row. **Nothing reaches the ledger without per-row confirmation.** Unparseable rows stage as `UNRESOLVED` with field-level problems and cannot be accepted. Commit reports written, skipped and rejected counts.
20. **BR-20 Duplicates are flagged, not merged.** A matching `contentHash` warns before proceeding. A row matching an existing Transaction on date, amount, currency and payment method is `DUPLICATE`, **defaults to not importing**, and the app never merges or edits the Transaction it matched.
21. **BR-21 Imported foreign amounts defer conversion.** A **scoped exception to BR-8** — manual entry still blocks on a missing rate. An imported row stores `amount` + `currency` with `amountInDefaultCurrency` and `fxRate` **null**, state `AWAITING_CONVERSION`. Such rows are **excluded from every total** (BR-1, BR-9 real, BR-15) and any screen showing an affected total **must state how many**. Never guess a rate to make a figure look complete.
22. **BR-22 Capture is a port; adapters are platform-specific.** One domain, several mechanisms. **iOS cannot read other apps' notifications — no public API exists.** That adapter is Android-only; iOS parity comes from Open Finance. A capability the platform cannot provide is **absent**, not disabled. All capture is opt-in, off by default, revocable, parsed on-device (§0.7).
23. **BR-23 A detection is a candidate, not a transaction.** A `DetectedTransaction` sits `UNREVIEWED` until the user confirms. The app may suggest, never decide. Dismissed stays dismissed. The resulting Transaction records `source = DETECTED`.
24. **BR-24 A detection is matched before it is offered.** Matched on amount, currency, ±3 days, and payment method where known. A match is `MATCHED` and not offered, so a hand-logged expense is never presented twice. Matching is advisory: it never edits, merges or deletes.
25. **BR-25 One column contract, three consumers.** `docs/sheet-import-format.md` feeds the parser, the template and the Import page explanation. The template is **generated per user**, pre-filled with their own categories, accounts and cards, and a test asserts a fresh template parses cleanly. Breaking any of the three fails the build.

### Phase 1.5 rules (spec §6.0 — specified, built with step 11)

**Numbered after Phase 2 and built before it.** BR numbering is append-only, so these read last while belonging with step 11 in time.

26. **BR-26 Offline is read-only.** The only thing cached is the most recent successful `GET /dashboard` payload, per period window fetched. Every other page is unavailable offline and says so. Nothing is recomputed on the device — ADR-7 does not relax when the network drops. **Every write is refused**, never queued: a replayed write would land against an FX rate that has moved (BR-8), a bill date from a cycle that may have changed (BR-4), and a plan that may have been edited (BR-14), and an entry that looked saved and was not is worse than one plainly refused.
27. **BR-27 Signing out wipes the cache.** Removed from the device on explicit sign-out *and* on any 401. Keyed to the user it was fetched for, and a payload whose user does not match the signed-in one is discarded rather than shown — on a shared device, one person's figures must never greet the next. No credential and no session token is ever cached; ADR-11 already puts the session beyond JavaScript's reach.


---

## 5. Architectural decisions (ADR log)

| # | Decision | Rationale |
|---|---|---|
| **ADR-1** | Spring Boot **4.0.7**, not the spec's 3.x | Already in the pom and cached in `~/.m2`; Boot 4.x baseline is Java 17 (max 26, Maven ≥ 3.6.3), so it satisfies the real constraint. Downgrading is backwards motion. Full note: [docs/adr/0001-spring-boot-4.md](docs/adr/0001-spring-boot-4.md) |
| **ADR-2** | Replace the Expo/React-Native frontend with **Vite + React web** | The design is CSS custom properties, `color-mix()`, `:has()` and a 940px breakpoint — none exist in RN. [docs/adr/0002-vite-web-over-expo.md](docs/adr/0002-vite-web-over-expo.md) |
| **ADR-3** | Mobile shipped later via **Capacitor**, not a rewrite | Wraps the built web app; needs no source change, so §0.6 component work is not duplicated. [docs/adr/0003-capacitor-for-mobile.md](docs/adr/0003-capacitor-for-mobile.md) |
| **ADR-8** | Transaction capture is a **port**; the Android notification listener is one adapter | iOS has no public API for reading other apps' notifications and none is expected. Treating capture as a port means Open Finance becomes a second adapter rather than a rewrite, and is how iOS reaches parity (spec BR-22) |
| **ADR-9** | Imported foreign amounts **defer** conversion; unconverted rows are excluded from every total and the count is stated on screen | An imported row carries a historical date, for which a live rate is the wrong number. A historical FX lookup would be a guess dressed as a fact. A total that silently omits rows is a wrong total, so the omission is shown (spec BR-21) |
| **ADR-11** | Users live in this application's own `app_user` table; sessions are **opaque tokens stored hashed, in an HttpOnly `SameSite=Strict` cookie**, not JWTs; Open Finance consent tokens are never readable through any API | One service with a database it already queries gains nothing from a stateless token, and loses instant revocation and a signing key to it. Isolation is enforced in the schema, in every repository, and in a two-user test — the last being the only one that fails loudly. Full note: [docs/adr/0011-accounts-sessions-and-token-custody.md](docs/adr/0011-accounts-sessions-and-token-custody.md) |
| **ADR-10** | **No social sign-in and no SMS 2FA.** TOTP (RFC 6238) with ten single-use recovery codes | Social sign-in does not reduce the security work, and linking an email signup to a provider on the same address is a takeover vector for no product gain. SMS costs per message and is the weakest common factor (spec §6.2) |
| **ADR-7** | The frontend may compute a figure the user has **not saved yet**; anything the server returns is rendered, never recomputed | Resolves spec §5's "no business calculation in the frontend" against its own what-if exception. [docs/adr/0007-business-rule-boundary.md](docs/adr/0007-business-rule-boundary.md) |
| **ADR-13** | Deploy on **one `t3.micro` EC2** running Caddy, the app and PostgreSQL in Compose — not CloudFront + S3 + RDS. **Proposed, nothing provisioned** | The `SameSite=Strict` session cookie (ADR-11) means the pages and `/api` must answer on **one origin**; a split of `app.` and `api.` hostnames simply cannot hold a session. One box gets that for free, costs ~€8/month after any allowance, and the compose file runs anywhere — so leaving AWS is a DNS change, not a rewrite. Full note, with the runbook: [docs/adr/0013-deploying-on-aws-free-tier.md](docs/adr/0013-deploying-on-aws-free-tier.md) |
| **ADR-4** | PostgreSQL + Flyway, replacing MySQL + `ddl-auto=update` | Spec §4. `ddl-auto=update` leaves the schema unversioned and undoes reproducibility. |
| **ADR-5** | Hexagonal layering enforced by ArchUnit from day one | Spec §4. Cheap to add now, near-impossible to retrofit. **Amended once:** `whereLayer(DOMAIN).mayOnlyBeAccessedByLayers(...)` now admits `INFRASTRUCTURE` as well as `APPLICATION`. A repository port is declared in the application layer *in terms of the domain model* — that is what makes it a port — so the adapter implementing it cannot avoid naming those types, and the rule without it made the design spec §4 describes unimplementable. `API` is still excluded, and that exclusion is what moved the wire DTOs into `application/**/dto`, where they belong: the frozen contract is no longer wired straight onto domain enums. `domainDependsOnNothingInThisApplication` is untouched and is what actually protects the domain. |
| **ADR-6** | Money: `BigDecimal` scale 2 HALF_UP in Java, integer **minor units** in TS | Spec §0.5. No `double`/`float`/JS `number` for money arithmetic, ever. |
| **ADR-12** | The nine pages were built **first**, against the frozen `frontend/src/types/api.ts`; the backend follows behind it, satisfying it. Deviates from spec §6's vertical slices | The churn vertical slicing prevents is pages built against an invented API shape. Freezing the contract before any page existed prevents the same churn by a different route — verified, not assumed: zero pages existed and exactly one component (`CardSummary`) leaked a business rule, which was refactored to take server-supplied dates as props. The cost is two implementations of BR-4/6/7/9/10/11; the mitigation is that both cite [docs/business-rule-vectors.md](docs/business-rule-vectors.md) and **neither may be edited to agree with the other, only with it**. Full note: [docs/adr/0012-frontend-first-against-a-frozen-contract.md](docs/adr/0012-frontend-first-against-a-frozen-contract.md) |

---

## 6. Test strategy

Red → Green → Refactor, always. The failing test is written **first**.

**Never delete, skip, comment out, weaken, `@Disabled` or `.skip()` a test to make a build pass.** If a test fails, either fix the code or change the test deliberately, in its own commit, with the reason recorded here. Deleting a test breaks the build.

Every bug fix starts with a failing regression test. Test names state behaviour: `expenseAfterClosingDayRollsToNextStatement()`, not `testBillDate2()`.

### Coverage floors — the build fails below them
| Scope | Floor |
|---|---|
| `domain`, `service`, frontend `lib/` | **90%** |
| Overall | **70%** |

Enforced by JaCoCo (`check` bound to `verify`) and Vitest `coverage.thresholds`.

### Layers
1. Domain unit tests (fast, no Spring) — highest density, table-driven over boundary cases: closing day 1 and 28, due before/after closing, month-end, leap years, zero interest, one instalment, interest-free with rounding.
2. Application service tests with mocked ports.
3. `@DataJpaTest` repositories.
4. `@WebMvcTest` controllers — status codes and payload shape.
5. Full-stack integration on Testcontainers Postgres.
6. ArchUnit: the dependency rule, and "no JPA annotations in `domain`".

---

## 7. Done / In progress / Next

**The build order, stated once so the checklist below is not the only record of it.**
This project was built in **two stages, not eleven vertical slices**: the whole
frontend first, against the frozen contract in `frontend/src/types/api.ts`, and
then the whole backend behind it, one feature at a time, satisfying that same
contract. That deviates from spec §6 deliberately — **ADR-12** and the paragraph
appended to spec §6 record why, what it costs and what holds the two sides
together. Read them before adding a step in either direction.

Where the two stages meet: the frontend is finished and must not be touched
while a backend slice is being built. Until the final swap, `git diff
--name-only` should show nothing under `frontend/src`.


### Done — §6 step 1, Foundation
- Imported the Claude Design handoff bundle and read every file in it.
- Audited the pre-existing repo and the installed toolchain.
- `CLAUDE.md`, `.gitignore`, `.nvmrc`, ADRs 1–3, `docs/monorepo-migration.md`.
- `backend/`: Boot 4.0.7 on Java 17, PostgreSQL + Flyway + H2, hexagonal packages, ArchUnit rules, JaCoCo floors. **`./mvnw verify` green — 7 tests, the first time this project has compiled.**
- Proved the ArchUnit rules bite: a probe class in `domain` with `@Entity` and a `double` field failed `domainCarriesNoPersistenceAnnotation` and `noFieldIsAFloatingPointNumber`, then was removed.
- `frontend/`: Vite 6 + React 18 + TS strict, Industry tokens and the shell/grid ported. **Build, lint, format and 2 tests green at 100% coverage.**
- `.github/workflows/ci.yml` running both gates.

### In progress — `lib/` pure modules, tests first
- [x] **`money.ts`** — branded `Money` as integer minor units, HALF_UP parsing that agrees with `BigDecimal`, `format`/`formatSigned`. 29 tests, 100% lines.
- [x] **`dates.ts`** — branded `CalendarDate` as an ISO string, epoch-day integer arithmetic, `addMonths` clamping for BR-10, the three `dateFormat` renderings. 43 tests, 100% lines.
- [x] **`period.ts`** — **BR-10 is the default**: `occurrencesIn`/`plannedAmountIn` count real dates and are the real cost. `smoothedMonthlyEquivalent` (52/12) is the narrow BR-3 exception, named so it cannot be mistaken for a real figure. Plus BR-6 `periodsPerYear`. 26 tests, 100% coverage.
- [x] **`variance.ts`** — BR-9. `real − planned` for **both** category types; only the `VarianceTone` differs. Yields a tone, never a colour. 13 tests, 100% coverage.
- [x] **`statementCycle.ts`** (+ `nextDueDateOnOrAfter`) — BR-4 `billDateFor`, two independent month rolls, cycle days validated to 1–28. 27 tests, table-driven over the boundaries §4 asks for. 100% coverage.
- [x] **`instalments.ts`** — BR-6 `analyseInstalmentPlan`: rounding tolerance of one cent per instalment before the solver runs, bisection on the annuity identity, APR compounded by frequency, `>900%` display cap. 21 tests, 100% coverage.
- [x] **`loans.ts`** — BR-7 `analyseLoan`: reuses BR-6's solver rather than copying it, adds the settlement figure (remaining instalments discounted to today) and the early-payoff saving. 17 tests, 100% coverage.
- [x] **`goals.ts`** — BR-11 `planGoal`, `progressPercent`, `assessFeasibility`. Horizon is months multiplied out (daily 30.4, weekly 4.33), **not** BR-10 calendar counting — a goal is a smooth target, not a schedule. 21 tests, 100% coverage.

**`lib/` is complete: 192 tests, 100% line and function coverage.**

### In progress — the §5 component library, in the order the spec lists them
- [x] **`Panel`** — the blueprint frame; always draws all four registration marks so no consumer can omit them. `density` is a prop rather than a class to override (see gotcha 19).
- [x] **`KpiCard`** — built on `Panel`, so the frame exists in one place only. `value` is a `ReactNode` so a page can pass a `MoneyText` and keep formatting at one edge.
- [x] **`MoneyText`** — the single edge where a `Money` becomes text. Always tabular numerals.
- [x] **`VarianceText`** — takes raw planned/real and derives figure *and* tone itself, so there is no way to render a variance and get BR-9 wrong.
- [x] **`EditablePlanCell`** — BR-14 inline plan editing. Holds the draft locally and parses once on commit, so a half-typed `75.` never rewrites the plan.
- [x] **`GhostPlanRow`** — the planned line above every real line, as its own `<tr>` so the two align without grid arithmetic.
- [x] **`PlanVsRealTable`** — a real `<table>`, not the prototype's CSS grid: headers associate with cells and each category reads as a pair of rows. Totals are given, never summed from visible rows (BR-15). Derived rows render as text (BR-14).
- [x] **`SegmentedControl`** — native radios, so arrow-key navigation and the checked state come from the platform and the visual state cannot disagree with the form state.
- [x] **`TagChip`** — three variants; the design system's `accent-2` is a machine-derived stand-in and is deliberately not exposed.
- [x] **`ProgressBar`** — hairline frame, solid fill, and the BR-11 pace tick, described in `aria-valuetext` because a lone line is meaningless read aloud.
- [x] **`AccountCard`** — BR-13. Pockets are introduced as *already part of* the balance, because the one dangerous misreading is treating them as money on top.
- [x] **`CardSummary`** — BR-4/BR-5 as a discriminated union: a debit card has no closing day *in the type*, so one cannot be written. Explains the cycle with the card's own dates.
- [x] **`Checkbox`** — a real `<input type="checkbox">`, not the prototype's styled `<button>`, so the announced state and the drawn state cannot drift.
- [x] **`LeadTimeToggle`** — BR-12 lead time with the count of what it currently catches.
- [x] **`NotificationRow`** — BR-12. Urgency is stated in text as well as colour; read items recede rather than disappear.
- [x] **`Dialog`** — focus moves in, Tab is trapped, Escape closes, focus returns to the opener. A portal, so a dialog opened inside a panel is not clipped by it.
- [x] **`InstalmentCalculatorPanel`** — BR-6/BR-7 live, for money not yet committed (ADR-7). One panel serves both sides: spreading a purchase and taking a loan are the same annuity.
- [x] **`LogEntryForm`** — BR-8 blocks the save when no rate can be had; BR-4 states the real bill date before saving. Composes `Dialog`, `SegmentedControl`, `Checkbox`, `InstalmentCalculatorPanel`.
- [x] **`WhatIfPanel`** — BR-11's slider, the other case §5 names for local computation. Only `monthlySpare` is supplied, because it depends on the whole plan.
- [x] **`SidebarNav`** / **`BottomTabBar`** — real `NavLink`s, so the back button and open-in-new-tab work and `aria-current` marks the page rather than the accent fill carrying it alone. A component does **not** hide itself: the 940px swap lives in the shell's `.tabbar` rule.
- [x] **`PageHeader`** — a `banner` landmark carrying the page's only `h1`.
- [x] **`PeriodPicker`** — names the window *and* states the dates it covers, because BR-10 makes those edges decide how many times a weekly plan lands.
- [x] **`FilterChips`** — BR-15's payment-method filter. Chips, but radios underneath: single-select, so the platform owns arrow keys and checked state.
- [x] **`Field`** — not in the prototype. A labelled control with room for a hint and an error, both wired with `aria-describedby` rather than nested in the label (gotcha 26). The error is a live region: a message that only appears visually leaves a form that silently refuses to save.
- [x] **`EmptyState`** — not in the prototype; drawn from the system. Keeps the blueprint frame, because an unframed empty region reads as a rendering failure rather than a state.

**The §5 component library is complete: 25 components, 460 tests, 99.9% lines.**

### In progress — the nine pages
- [x] **Test infrastructure** — MSW seeded from the prototype's own `state` block, so a page's figures can be compared against the design directly. Unhandled requests **fail** rather than warn: a request no handler answers is a page asking for something the contract does not promise.
- [x] **`lib/http.ts`** — the one place JSON crosses into the app. A non-2xx throws, so an RFC 7807 problem body can never be rendered as data.
- [x] **Accounts** — BR-13 end to end. The total counts only included accounts and **never** the pockets, and says so when it has left something out.
- [x] **Cards** — BR-4/BR-5. The seam where the API's nullable `Card` becomes `CardSummary`'s discriminated union; a credit card missing its cycle degrades to the debit presentation rather than crashing or inventing a date.
- [x] **Categories** — BR-14 inline editing with an optimistic mutation that rolls back on failure, and BR-10 made legible: each row shows ×how-many-times beside the per-occurrence amount.
- [x] **`lib/planRows.ts`** — BR-15 grouping and sorting. Pure, never mutates the rows it is given, shared by Earnings and Expenses.
- [x] **`features/dashboard`** — one `useDashboard` query behind Earnings, Expenses and Overview, so the three can never disagree about what a period contains (spec §4: one call, computed server-side).
- [x] **Earnings** — BR-9 and BR-15. Grouping and sorting are view state in the hook; the table renders whatever order it is handed, which is why one component serves both pages on different axes.
- [x] **Expenses** — BR-14 derived rows render as text, BR-15 filter with totals that describe what is on screen and say so. Inline plan editing writes through `useUpdatePlanAmount`, shared with Earnings.
- [x] **Overview** — BR-1 and BR-2 rendered, never recomputed. One breakdown table with headed sections, so "Net for period" is the foot of a single reckoning rather than a third number beside two others.
- [x] **Goals** — BR-11. Progress against a pace marker, and the what-if judged against what the *whole plan* leaves spare rather than against the goal itself.
- [x] **Notifications** — BR-12. The queue is derived and sorted server-side; the page filters it to the widest enabled lead time, which is view state in the hook exactly as BR-15’s filters are. "Mark all read" clears only what is on screen, so a lead time cannot silently dismiss a warning nobody saw.
- [x] **Settings** — BR-8 made visible: the default currency states what every total is denominated in, and the FX rates are shown in the direction the provider quoted them, with the time they were pulled. Each field saves on its own; text fields hold a draft and commit on blur, so a half-typed name is never persisted.
- [x] **The app shell** — `App.tsx` holds the two navigations, the routes and the log dialog. Both navs are always in the tree; the 940px swap is `app.css`, so no JavaScript reads the viewport. BR-12's unread count is read once and handed to both, so they cannot show different badges.
- [x] **Creating accounts, cards and categories** — a button on each page opening a `Dialog`. The prototype keeps these as always-open panels beside the list; a dialog was chosen so three pages are not dominated by forms, and it matches the log-entry flow already there. Each carries the rule its object obeys: a pocket is written against its parent and never moves the parent's balance (BR-13); a debit card loses the cycle fields rather than disabling them, and cycle days are held to 1-28 (BR-4, BR-5); a category is created with its planned amount, because BR-14 does not allow one without.
- [x] **Runnable in a browser** — `npm run dev` starts MSW's browser worker against the *same* `handlers.ts` the tests use, so what a browser shows is what the tests assert. `import.meta.env.DEV` gates it, so the dynamic import is dropped from a production build entirely. Opt out with `VITE_USE_MOCK_API=false` once a server is on :8085.

**The frontend is complete: 9 pages, 25 components, 10 `lib/` modules, 617 tests, 98.9% lines.**

**Import (spec §1 page 10) is deliberately absent.** It is spec §6.1 step 13, in Phase 2, which "begins only when steps 1-11 are complete". A nav entry pointing at a page that does not exist is worse than no entry: `navItems.tsx` records why.

Both `lib/` modules sit at 100% line and function coverage; branch coverage is 97–98% because `noUncheckedIndexedAccess` requires `?? …` fallbacks on indexed reads that the surrounding validation already makes unreachable. Above the 90% floor, and preferable to casting the check away.

### Next

**Phase 2 exists and is specified (spec §6.1–§6.3): tags/recurrence, sheet import, transaction detection, then identity hardening and Open Finance. It begins only when step 11 is done and the app runs end to end. Nothing from BR-16–BR-25 is implemented.**
**The frontend is done. Everything remaining is backend.**

### In progress — the Java domain, tested against the shared vectors
- [x] **[docs/business-rule-vectors.md](docs/business-rule-vectors.md)** — the numbers both implementations cite. Extracted from the TypeScript tests, which already asserted every one of them. **A vector there is not an example, it is the assertion**; when a Java test and its TypeScript counterpart disagree, one has drifted from that file and the file is right.
- [x] **`MoneyCalculator`** — BigDecimal scale 2 HALF_UP, which is exactly what the frontend's digit-string parser does. `0.005 → 0.01` and `-0.005 → -0.01` on both sides.
- [x] **`Frequency`** — BR-17's narrow vocabulary, 52/26/12. Carries both `periodsPerYear` (BR-6's compounding) and `periodsPerMonth` (BR-3's average), named so the two cannot be mistaken for each other.
- [x] **`StatementCycleCalculator`** — BR-4, the 16-row vector table green on the first run. Cycle days validated to 1–28 in `StatementCycle` itself, so nothing downstream ever asks what happens on the 31st of February.
- [x] **`InstalmentCalculator`** — BR-6. Tolerance checked **before** the solver, bisection on the annuity identity, APR compounded by frequency. Agrees with the TypeScript solver to 8 decimal places.
- [x] **`LoanCalculator`** — BR-7. Reuses BR-6's solver rather than copying it, and adds the settlement figure and the early-payoff saving. Credit-union loan settles at €2,029.59 saving €220.01, to the cent.
- [x] **`PlanNormaliser`** — BR-10 counted on real dates: a month holding five paydays plans five. Monthly occurrences are computed from the *original* anchor each time, never by stepping from an already-clamped one, so 31 Jan → 28 Feb → **31** Mar.
- [x] **`VarianceCalculator`** — BR-9. `real − planned` for both kinds, only the tone differs, so a column of variances can be summed without asking what kind each row is.
- [x] **`GoalCalculator`** — BR-11. The horizon is months multiplied out (daily 30.4, weekly 4.33), deliberately *not* BR-10's real-date counting: a goal has no anchor date, so counting calendar contributions would answer a question nobody asked. `ContributionFrequency` is its own enum for the same reason BR-17 keeps vocabularies apart — it carries DAILY, which has no meaningful `periodsPerYear`.
- [x] **`PositionCalculator`** — BR-1, BR-2, and BR-3's derived rows. Loan principals are added to `availableNow` as their own term, exactly as BR-1 states, and the whole repayment goes on the other side, so borrowing nets out to the interest and nothing else. `AccountBalance` cannot see pockets at all, which is the only design that makes BR-13's double-count impossible rather than merely avoided.
- [x] **`DuePaymentQueue`** — BR-12. Derived every time, never stored, so a paid card drops out on its own. The lead-time counts are taken over the *whole* queue rather than the visible part, because the number says what turning an option on would add.

**The domain layer is complete: `./mvnw verify` green — 185 tests, ArchUnit and JaCoCo floors held.**

### In progress — persistence and the API, per feature
- [x] **Schema** — `V2__identity_and_accounts.sql`. Money is `NUMERIC(19,2)`, so the database cannot hold a third decimal place that rounding would later have to invent an answer for. A pocket's foreign key is not optional: there is no such thing as a free-floating pocket, so the schema makes one unwriteable.
- [x] **`/users/me`** — GET and PATCH. Every field optional, because Settings saves one at a time.
- [x] **`/accounts`, `/accounts/{id}/pockets`** — BR-13 end to end, with `@DataJpaTest` against the real migrations on H2 and `@WebMvcTest` against the frozen wire shape.
- [x] **Cards (§6 step 4)** — BR-4 and BR-5 end to end. `V5__cards.sql`, `CardEntity`/`JpaCardRepository`, `CardService`, `CardController`, and the frozen `Card` shape with its three server-computed cycle dates. **A card has no `user_id`:** it settles from an account, and the account already has an owner, so ownership has one source of truth rather than two that could disagree — every read filters through `account.user_id`. BR-5 is stated three times, and each says something the others cannot: a **sealed domain type** (a `DebitCard` has nowhere to put a closing day), a **check constraint** (the half that survives a hand-written UPDATE), and a **service rule** that refuses a debit body carrying a cycle instead of ignoring the extra fields.
- [x] **The first Testcontainers integration test** — spec §4's fifth layer, added with the slice that needed it rather than ahead of it. H2 in PostgreSQL mode is close enough to run the migrations and not close enough to be believed about check constraints or dialect. `IntegrationTest` starts one pinned `postgres:17.5` for the JVM and fixes the clock at the prototype's own "today", so an end-to-end run asserts the Visa fixture's dates **to the day** rather than something merely self-consistent.
- [x] **Categories & plan (§6 step 5)** — BR-14 and BR-10 end to end. `V6__categories.sql` (every plan column `NOT NULL`, because a category without a plan is not an incomplete category — it is not a category), `CategoryEntity`/`JpaCategoryRepository`, `CategoryService`, `CategoryController`, and the frozen `CategoryList` shape. **`PeriodResolver` is new domain**: which dates "this month" or "this week" actually covers, with the week beginning where the *user* says it does. That is not presentation — BR-10 counts landings on real dates, so those two edges decide whether a weekly plan lands four times or five.
- [x] **Transactions (§6 step 6)** — BR-8 and BR-4 end to end. `V7__transactions.sql`, `TransactionEntity`/`JpaTransactionRepository`, `TransactionService`, `POST /transactions`, and BR-8's `GET /fx/rates`. The payment method is **two nullable foreign keys with a check constraint**, not one loose id: what the method *is* decides BR-4, BR-5 and BR-1, and an untyped column would make that question be re-answered by a join everywhere it is asked.
- [x] **BR-8's rates, for real** — `ExchangeRates` in the domain (quoted in the provider's own direction, never inverted for convenience), an `ExchangeRateProvider` port, and a cached adapter over Frankfurter — ECB reference rates, no API key, so no credential to hold. Three behaviours, each tested: **cached** with its age published; **a stale rate is still a real rate**, served with its own `fetchedAt` when the provider is unreachable; and **nothing cached is a refusal** — a 503 that says the entry was not saved. There is no rate of 1 hiding anywhere in that path.
- [x] **Financing (§6 step 7)** — BR-6, BR-7 and BR-2 end to end. `V8__financing.sql`, `InstalmentPlanEntity`/`LoanEntity`, `FinancingService`, `/instalment-plans`, `/loans`, and the two **preview** endpoints spec §4 names — which write nothing, and exist so the log form can show a figure for money not yet committed *and* so the frontend's own pure functions have an authoritative answer to agree with (ADR-7). **`POST /transactions` now creates the plan atomically**, closing the refusal the previous slice shipped.
- [x] **Dashboard (§6 step 8)** — `GET /dashboard`, the whole overview in one call. BR-1, BR-2, BR-3, BR-9, BR-10 and BR-15 assembled server-side, so Overview, Earnings and Expenses are three readings of one calculation rather than three calculations that could disagree about what August contained. **`PeriodWindows` was extracted** in the same commit: two endpoints resolving the window separately would eventually resolve it differently, and BR-10 counts on those exact dates.
- [x] **BR-1 was incomplete, and the dashboard is what surfaced it.** `PositionInputs` had accounts, cards, plans, loans and borrowings but neither of BR-1's last two terms — *"+ Σ earnings − Σ expenses not paid by credit card"*. Added, with tests naming the rule, including the one that matters: **card spending is not subtracted from what is available**, because it has not left an account and is already counted on the other side as what is owed. Subtracting it in both places charged the same purchase twice.
- [x] **A card purchase counts in the month its bill falls** — the period query reads `COALESCE(planned_expense_date, entry_date)`. Reading it by the day it was spent would put an August purchase into August while its plan sat in September, and the variance would be nonsense in both months. Asserted end to end.
- [x] **Goals (§6 step 9)** — BR-11 end to end. `V10__goals.sql`, `GoalEntity`, `GoalService`, `/goals`. **Nothing derived is stored**: the gap, the contribution and the pace marker all move with today, so a stored copy would be a figure that was true on the morning it was written. `GoalCalculator` gained **`pacePercent`** — measured in days, so the marker never sits still for a month and then jumps. It is what turns a bar into a judgement: 40% saved says nothing until you know whether the plan said 25% or 70%.
- [x] **`savedAmount` has one writer, and the schema says so.** BR-18 is explicit that tagging a goal never allocates toward it, so the port has no "allocate" method to become a second source of truth when Phase 2 arrives.
- [x] **No what-if endpoint, deliberately.** Spec §5 names the slider as a case for the frontend's own pure function and ADR-7 allows it: a round trip per drag is the cost that exception exists to avoid.
- [x] **Notifications (§6 step 10)** — BR-12 end to end. `V11__notifications.sql` creates **no notifications table**, because a notification is not a thing anybody creates: the queue is derived on every read from card bills, loans and instalments, so **a paid card drops out of it on its own** — there is no row for anything to forget to delete. Only two things are persisted: **read state** (against the stable key, so an item marked read stays read across a recomputation) and the **lead-time settings**. Unread is the *absence* of a row rather than a row saying false — one state, one representation.
- [x] **`DuePayments` is shared** by the dashboard's upcoming panel and the notifications queue. Two assemblies would eventually disagree, and a warning that appears on one screen and not the other is worse than one that appears on neither. Extracted in this commit; the dashboard was refactored onto it.
- [x] **Turning every warning off still shows money already due.** With no lead enabled the widest is zero, which admits anything due today or overdue. Asserted, because the tempting implementation hides it.

**Direct debits and subscriptions are two of BR-12's five sources and have no rows yet** — they arrive with recurrence in Phase 2 (BR-16). That is an absence of data, not a gap in the rule: nothing in `DuePayments` would need changing to include them.

**Spec §6 steps 1–10 are complete. `./mvnw verify` green — 510 tests, on H2 and on real PostgreSQL, with ArchUnit and the coverage floors held.**

**`./mvnw verify` green — 420 tests.**

**Neither table stores an interest figure, and neither should.** BR-6 solves the periodic rate from the terms and BR-7 discounts the remaining instalments back to today; both answers move as instalments are paid, so a stored APR would be a number that was true once, sitting beside terms that have since changed. The credit-union vector — settlement **€2,029.59**, saving **€220.01** — is now asserted end to end through the real API on real PostgreSQL, not only in the calculator's own unit test.

- [x] **Idempotent money writes (spec §4)** — `V9__idempotent_requests.sql` and `IdempotentRequests`. An `Idempotency-Key` on `POST /transactions` or `POST /loans` is **honoured when present, never required**: `lib/http.ts` sends none and the contract is frozen (ADR-12), so requiring one would break every page that works today. A retry is answered with the **stored answer, replayed verbatim** — re-deriving it could produce a different one, and a retry that answers differently is not idempotent. The key is scoped to the user **in the primary key**, because a key is chosen by the client and two people can pick the same one. The same key on a different endpoint is a **409**, not a match: replaying a loan as a transaction would be worse than creating a second one.

**`./mvnw verify` green — 510 tests.**

**One thing is deliberately refused rather than half-done.** `CreateTransactionRequest.financing` is part of the frozen contract and the log form sends it, but instalment plans are spec §6 step 7 — the very next slice. Until then a request carrying it is answered **400, naming the field**. Accepting the terms and dropping them would save the entry as an ordinary purchase, leave no instalment plan behind, and say so on no screen; a refusal is visible, and it is one commit long.

`AppException` gained a fifth kind, `UNAVAILABLE` → **503**. BR-8 needs the caller to tell "we could not ask" from "you asked for the wrong thing", because only one of those is worth retrying.

Two things the plan slice settled and later slices inherit. **The window travels with the list**, in `PeriodWindowResponse`, so no page has to assume what a period covers. And **`type` is not editable**: an expense category that became an earning one would take its whole history to the other side of BR-9 and silently reverse every variance already recorded against it, so `CategoryEntity.apply` has no path that touches it.

`AppException` gained a `field`, and a fourth kind (`INVALID` → 400). Bean validation names one field at a time; a rule spanning two — a credit card needing *both* cycle days — cannot be expressed that way without inventing a property name, so it is stated in the service and reaches the wire in the same `errors` shape. A form reading the problem body cannot tell which of the two refused it, which is the point.

- [x] **Registration, sign-in and sign-out** — [ADR-11](docs/adr/0011-accounts-sessions-and-token-custody.md). Built **before** the seven remaining slices on purpose: isolation is enforced per repository, so retrofitting after them would mean auditing seven more places for a leak. `UserIsolationTest` creates two users and proves the second cannot read or touch the first's rows, and **another user's id answers 404, never 403** — a 403 confirms the id exists.
- [x] **`/login` and `/register`** — one form, two routes, so a bookmark and the back button behave. The guard renders nothing until the session is known, because letting the pages mount first would fire nine authenticated requests for somebody who is not signed in.

**Sessions are opaque tokens stored hashed, in an HttpOnly cookie — not JWTs.** Deviates from spec §4's stack note, deliberately, and ADR-11 records why: one service with a database it already queries gains nothing from a stateless token and loses revocation and a signing key to it. **No JavaScript anywhere handles a credential**, and `lib/http.ts` has no `Authorization` header by design.

**Still not built, and the app must not be exposed publicly until it is:** spec §6.2's TOTP, recovery codes, rate limiting on the auth endpoints, and password reset. BCrypt is in use via `DelegatingPasswordEncoder`, so §6.2's Argon2id is a change of default that re-hashes on next sign-in rather than a forced reset.

### Then — what happens when

Each row is triggered by the row above being finished. Nothing here is started early.

| Trigger | Work |
|---|---|
| **Now** | Cards → Categories & plan → Transactions → Financing → Dashboard → Goals → Notifications. One feature per commit, in that order, each following the eight steps below. |
| ~~Once every endpoint is live~~ **Done at the API level** | The swap has been run against real PostgreSQL: register, create an account and a category, log an expense, read `GET /dashboard`. September 2026 counts **four** Saturdays for a weekly plan anchored to 2026-01-03 (BR-10), `availableNow` drops by the expense (BR-1), and `GET /fx/rates` returns live ECB rates. **The browser pass is still to do.** |
| **Once every endpoint is live** | **Swap the fake API for the real one.** Set `VITE_USE_MOCK_API=false`, run the backend, and confirm every page renders the same figures it did against fixtures. Any difference is a drift bug the shared vectors should have caught. **Do not delete `handlers.ts`** — it is also the test double behind all 674 frontend tests. What goes is the browser worker, not the file. |
| ~~Step 11 (Hardening) begins~~ **Done** | The **Phase 1.5** amendment is written: **BR-26** and **BR-27** in spec §3 and in §4 above, §0.7 extended to cover the cached payload, spec §6.0 added as step **17** (append-only numbering — it belongs with step 11 in time), and `StaleDataNotice` added to §5. Specified, not yet built. |
| **Step 11** | Playwright journeys for the five critical flows, performance pass, accessibility audit, documentation — including the offline cache just specified. |
| **Before any public exposure** | Finish spec §6.2: TOTP, ten single-use recovery codes, rate limiting on the auth endpoints, password reset, and **Argon2id as the `DelegatingPasswordEncoder` default** — which re-hashes on next sign-in rather than forcing a reset. |
| **After step 11** | Phase 2 (§6.1): tags and recurrence → sheet import → detection. **Nothing from BR-16–BR-25 before this point.** |

### How each remaining backend slice is built

In this order, every time. The two-user isolation test is not optional per slice: per ADR-11 it is the only one of the three isolation mechanisms that fails loudly when the other two are got wrong.

1. **Flyway migration** — a new `V{n}__{feature}.sql`. Never edit an applied one. One column per `ALTER TABLE` (gotcha 35).
2. **JPA entity + Spring Data repository** in `infrastructure`; the **port interface** in `application`.
3. **`@DataJpaTest`** against the real migrations on H2, **including a two-user isolation test**.
4. **Application service** with the port mocked, exercising the domain calculators.
5. **DTOs in `application/**/dto`** — never in `api`. The dependency rule forbids `api` reaching the domain, which is what put them there, and it keeps the frozen contract off domain enums.
6. **Controller** — pure HTTP, no domain import at all.
7. **`@WebMvcTest` extending `WebSliceTest`**, so the slice runs the real security chain rather than Boot's default (gotcha 33).
8. **Testcontainers integration test** for the slice end to end.

Non-negotiable throughout: the contract is frozen (`types/api.ts` — money as integer minor units, dates as ISO `YYYY-MM-DD`; if a DTO cannot match it, **stop and say so** rather than changing the frontend); no entity is ever exposed directly; another user's row answers **404, never 403**; RFC 7807 problem bodies with a `title` worth showing a person (400 with field-level errors, 404 unknown id, 409 domain-rule violation); every rendered figure computed server-side (ADR-7).

The prototype's `DCLogic` class is the reference implementation for the business rules; [docs/design-reference.md](docs/design-reference.md) maps each rule to its line number in the handoff bundle.

---

## 8. Gotchas

Things discovered the hard way. Never rediscover these.

1. **`RecurrenceFrequency` must never reach `periodsPerYear`.** BR-17 keeps two frequency vocabularies apart. `periodsPerYear` is defined for 52/26/12 only; a `DAILY` or `YEARLY` value would return `undefined` and produce a meaningless APR from BR-6's solver. Instalment plans and loans take BR-6's `Frequency`; recurrence rules take `RecurrenceFrequency`. Do not widen the shared type to "simplify".
1. **A `Secure` cookie is silently discarded over plain HTTP.** Signing in over `http://192.168.…` returns **201 with a `Set-Cookie`**, the browser drops it, and every request after it is a 401 — so the failure looks like "sign-in is broken" when sign-in worked perfectly. `app.cookie.secure` defaults to `true` (ADR-11); `application-local.yml` turns it off for LAN testing and says why. Never in a deployed environment.
1. **A third-party URL can go stale and no test will ever tell you.** `api.frankfurter.app` began answering **301** to `api.frankfurter.dev/v1`, and the JDK HTTP client does not follow redirects — so every BR-8 rate lookup failed, and with it *every* transaction save. No test can catch this, because no test may depend on a third party being reachable, and every test that touches FX rightly stubs the provider. It was caught by running the application for the first time. When something works in every test and fails the moment it is real, suspect an outbound URL.
1. **Spring Boot 4 ships Jackson 3, and there is no `com.fasterxml.jackson.databind.ObjectMapper` bean.** Jackson 2 is still on the classpath transitively, so the old import compiles and the application then fails to start with "No qualifying bean of type ObjectMapper". The bean is `tools.jackson.databind.ObjectMapper` (a `JsonMapper`). Annotations stay in `com.fasterxml.jackson.annotation` — `@JsonInclude` is unchanged — and Jackson 3 made its exceptions **unchecked**, so `writeValueAsString` no longer needs a catch. Same family as gotcha 10: if something that worked under Boot 3 is silently absent under Boot 4, look for the module or the package that moved.
1. **A nested Spring Data repository interface is never scanned.** `JpaTransactionRepository` declared its `JpaRepository` as an inner interface, which compiles, starts, and then fails every context load with "No qualifying bean of type ...$Entries". Repository interfaces have to be top-level types in a scanned package. Keep them beside the adapter that uses them, as every other slice does.
1. **A controller cannot bind a query parameter to a domain enum.** ArchUnit's layer rule reads method *parameters*, so `list(@RequestParam PeriodKind period, …)` is `api → domain` and fails the build — even though nothing is calculated. Take the raw `String` and convert in the application layer. This is not a workaround: the conversion earns its keep, because an unknown value then comes back as a 400 naming the parameter and listing what it accepts, instead of a framework message nobody wrote.
1. **Boot 4 does not manage the Testcontainers version.** Under Boot 3, `spring-boot-dependencies` imported the Testcontainers BOM, so `org.testcontainers:postgresql` needed no `<version>`. Under Boot 4 the same dependency fails the build with *"'dependencies.dependency.version' ... is missing"*. Import `testcontainers-bom` yourself and pin it — which is better anyway: unpinned, a rebuild could quietly change which database version the integration tests ran against.
1. **A JPA entity gets its owner from its parent, not from a copy of the owner id.** `card` has no `user_id`: it settles from an account, and `account` already has one. Two owner columns are two things that can disagree, and the one that disagrees silently is the one that leaks. The cost is that a per-user unique constraint is not expressible in the table (card names are unique per *account* in the schema, and per user in the service); the gain is that `findByAccountUserId...` is the only way to read a card at all. Prefer this shape for every table hanging off an owned row.
1. **MSW handlers are the contract standing in for the backend, not a second backend.** `frontend/src/test/handlers.ts` exists because the pages were written before the API (ADR-12). **A handler may never answer something `types/api.ts` does not promise** — a page inventing an endpoint is how a frozen contract stops meaning anything, and it is as possible in a test as in a browser. If a page needs a field, add it to `types/api.ts` and to the backend DTO, in that order. **The file itself is not deleted when the real API lands**, despite what an earlier draft of ADR-12 said: it is also the test double `src/test/server.ts` builds every frontend suite from. What becomes obsolete is the `npm run dev` browser worker, not the handlers.
1. **`DetectedTransaction` must never be called `Notification`.** BR-12's `Notification` is *derived, outbound, future money*. A detection is *stored, inbound, money already moved*. Different type, different table, different name. Reusing the name would put an inbox and an outbox in one model.
1. **A tag on a Goal must never write to `savedAmount`.** BR-18 makes tags organisational only. BR-11's `savedAmount` has exactly one source of truth; auto-allocation would make a tag a second, and change BR-11's arithmetic without changing BR-11.
1. **The backend never compiled.** `BudgetTrackerController` declared `List<User> getAllUsers()` and `User getUserById(Long)` with **empty bodies** — a hard "missing return statement" error. Every `model/`, `repository/` and `dto/` class was an empty stub, and the repositories were plain **classes**, not `JpaRepository` interfaces. Nothing was salvageable but the Maven shell.
2. **The root `.gitignore` contained `**/*`** — it ignored every file in the project. Replaced.
3. **The frontend was never started.** `FrontEnd/BudgetTracker/` was the stock Expo Router template ("Tab One" / "Tab Two", `EditScreenInfo`, `Colors.ts` with `#2f95dc`) with no `node_modules`.
4. **Two nested git repos, different owners' remotes** (`CarlosEMenezes/BugeTracker`, `Kauakb/BugeTracker-frontEnd` — note the "Buge" typo), and the root is **not** a repo. `BackEnd` carries ten uncommitted deletions on `main`. No git history has been touched; see [docs/monorepo-migration.md](docs/monorepo-migration.md) for the commands, to run when ready.
5. **`occurrencesIn` vs `periodsPerMonth`.** The prototype counts **real dates**, so a month holding five paydays plans five. Spec BR-3 uses the 52/12 average. Both are correct in their place: real-date counting for **category** plans (BR-10), the 52/12 average for the **derived** loan/instalment rows (BR-3). Each gets its own test; do not "unify" them.
6. **Two Maven versions.** 4.0.0-rc-5 on `PATH`, 3.9.16 in the wrapper. Use `./mvnw` only.
7. **`_ds_bundle.js` is an empty stub** — it declares a namespace and exports nothing. The design system is entirely in `styles.css`. Don't look for components in the bundle.
8. **`support.js` is prototype runtime**, not app code. It implements `sc-for`/`sc-if`/`DCLogic` for the Claude Design player. It is never ported.
9. The prototype hard-codes "today" as **31-08-2026** and a fixed FX table. Both are fixtures; real code takes a clock and a live FX provider (BR-8).
10. **Spring Boot 4 split auto-configuration into per-technology modules.** `flyway-core` alone is inert: migrations are **silently never applied** — no error, no warning, no log line, and a green build. The fix is `org.springframework.boot:spring-boot-flyway`. `FlywayBaselineTest` is the regression test; never delete it. Expect the same trap for other technologies (`spring-boot-jdbc`, `spring-boot-jpa`, …) — if something auto-configured under Boot 3 silently does nothing under Boot 4, this is why.
11. **ArchUnit fails a rule that matched no classes.** The hexagonal packages start empty, so `archRule.failOnEmptyShould=false` in `src/test/resources/archunit.properties` is what lets the architecture tests exist before the code they govern. `layeredArchitecture()` additionally needs `.withOptionalLayers(true)` or it errors on an empty layer.
12. **Vitest and Vite major versions must be paired.** Vitest 2 bundles its own Vite 5; with Vite 6 in the project you get two copies of Vite's types and `tsc` fails with a wall of "Plugin is not assignable to Plugin". Vitest 3 + Vite 6 is the working pair. Check `ls node_modules/vitest/node_modules` — if a nested `vite` is there, the versions are mismatched.
13. **Import `defineConfig` from `vitest/config`, not `vite`**, or the `test` block is a type error. The `/// <reference types="vitest/config" />` comment is not enough.
14. **npm 11 blocks package install scripts by default**, warning about `esbuild`'s postinstall. It is harmless here — esbuild ships its platform binary as an optional dependency — so `npm ci` works in CI without approving scripts. Don't "fix" it by disabling the protection.
15. **Never pipe a build into `tail` and read `$?`** — you get `tail`'s exit code, so a failed build reads as success. Redirect to a file, capture the real exit code, then grep the file. This masked a compile failure once already.
16. **`Money` is a branded number, so unary minus on it fails lint** (`@typescript-eslint/no-unsafe-unary-minus`). Use `multiply(money, -1)` or `subtract(ZERO, money)`. The same will apply to any other branded numeric type added later.
17. **Parse money from text, never through `parseFloat`.** `fromDecimal` reads the digit string and rounds half away from zero, so `0.005` becomes 1 cent instead of being mangled into binary floating point first. `Math.round` alone is wrong here — it rounds half towards positive infinity, so `-0.005` would disagree with the backend's `HALF_UP`.
18. **BR-9's variance sign: the spec and the prototype disagree, and the spec wins.** The prototype flips the sign for expenses so underspending shows positive. `variance.ts` computes `real − planned` for both types and varies only the tone. A single sign convention is also what lets a column of variances be summed and sorted without asking what kind of row each one is. If a screen looks "wrong" against the prototype here, this is why — do not flip it back.
19. **Never let a component variant depend on out-specifying a design-system class.** *This bit twice.* `Dialog` composed the design system's `.dialog`, whose `background: transparent` has the same specificity as the module's opaque rule — so stylesheet order won and the modal rendered see-through with the page readable behind it. Same fix as `Panel`: take the frame from `.blueprint` and own everything else. Grep for a literal design-system class name in a `className` list before adding one.
    The original note: `.card` and a CSS-module class have equal specificity, so which padding wins depends on stylesheet order, not intent — and a variant can silently lose in a production build while looking right in dev. `Panel` therefore uses `.blueprint` for the frame, owns its own layout, and exposes `density` as a **prop**. Add variants as props, not as overrides.
26. **A hint inside a `<label>` becomes part of the field's accessible name.** `getByLabelText('Default currency')` could not find a select whose label also wrapped "Every total is stated in this…", because the announced name was the whole paragraph. A hint *describes*, it does not *name*: put it outside the label and wire it with `aria-describedby`.
27. **A `display:none` element has no accessible name.** `getAllByRole('navigation', { name: 'Main', hidden: true })` finds one nav, not two: `hidden: true` admits the element but the accname algorithm still computes `''` for it. Reach a hidden landmark by role and class, not by label — and note the corollary, that `css: true` in the Vitest config means jsdom really does apply `app.css`, so the 940px rules are live in tests.
28. **A grid item's `min-width` is `auto`, so `1fr` does not mean "share the space".** A long option label or a wide input pushed a `1fr` column past its track and the log dialog scrolled sideways on every keystroke. Every grid or flex container holding a form control needs `> * { min-width: 0 }`. The same dialog also jittered because a scrollbar appearing and disappearing reflowed the content: `scrollbar-gutter: stable` on the scrolling element, and scroll the *body*, not the box — the blueprint corner marks are drawn outside it and a scroll container clips them.
31. **Boot 4 split the *test slices* into modules too.** `@DataJpaTest` and `@WebMvcTest` do not resolve from `spring-boot-starter-test` alone — they need `spring-boot-data-jpa-test` and `spring-boot-webmvc-test`, and they moved package (`org.springframework.boot.data.jpa.test.autoconfigure`, `org.springframework.boot.webmvc.test.autoconfigure`). Same trap as gotcha 10; expect it for every other slice.
33. **Boot 4 test slices do not pick up your `SecurityConfig`.** `@WebMvcTest` loads `Filter` beans but not the application's security configuration, so a slice quietly runs Boot's *default* chain and passes or fails for reasons that have nothing to do with your rules — a POST came back 403 from a CSRF filter I had disabled. `@Import` it explicitly.
34. **`queryClient.clear()` does not notify mounted observers.** It empties the cache but leaves existing observers bound to the query objects it removed, so after signing out the shell kept rendering the previous session's figures until something else triggered a refetch — which on a signed-out app is nothing. Write the new value first (`setQueryData(authKey, null)`), let the guard unmount the tree, then `removeQueries` the rest.
35. **H2 rejects a multi-column `ALTER TABLE`.** `ADD COLUMN a, ADD COLUMN b` is fine on PostgreSQL and a syntax error on H2 in PostgreSQL mode. One statement per column, or the migrations stop running in tests — which is the only place they are checked before production.
32. **Jackson binds an ABSENT `Optional` record component to `Optional.empty()`.** A PATCH DTO cannot use a record to tell "not mentioned" from "clear it" — saving a name would wipe the age beside it. Use a class with setters: a setter runs only when the key is present in the body, so a field left null really was left out. Caught by a test, not in review.
30. **The ArchUnit float ban covers record components, and constants.** `noFieldIsAFloatingPointNumber` failed on BR-6's bisection bracket (`private static final double HIGHEST_RATE`) and would fail on any `double` in a record. Locals, parameters and return types are fine — the solver works in `double` internally. Anything that is *state* is a `BigDecimal`, converted at the boundary. This is right, not an obstacle: a rate serialised from a `double` carries its binary representation across the wire.
29. **`list-style: none` does not remove the list's padding.** The browser's `padding-inline-start: 40px` survives it, which is what pushed the instalment figures out of the dialog. Always pair it with `padding-left: 0`.
25. **`vi.useFakeTimers()` freezes MSW.** Faking the whole event loop stops `fetch` ever resolving, so every test in the file times out at 5s. Fake only the clock: `vi.useFakeTimers({ toFake: ['Date'] })`.
24. **A stateful fake backend, or every optimistic update looks broken.** The MSW handlers remember writes and are reset between tests. A handler that accepts a `PATCH` and then serves the original row again makes an optimistic update flash the new figure and revert on refetch — which is indistinguishable from a real rollback bug.
23. **A component must not decide whether it is shown.** `BottomTabBar` originally carried its own `display: none` outside the 940px breakpoint, which took it out of the accessibility tree entirely and made every test fail to find it. Visibility is a layout decision and belongs to the shell (`.tabbar` in `app.css`); the component supplies only its column layout.
22. **Never put `pointer-events: none` on a visually hidden form control.** The design system does it on its radio inputs; copied onto a checkbox it makes the control unclickable by anything targeting the input rather than the label — assistive technology included. Hiding with `opacity: 0` and zero size is enough.
21. **Spec §5's 44px touch target vs the design's dense tables.** The design's inline plan field is 22px, right for a dense table under a mouse. `EditablePlanCell` keeps that and grows the target to 44px under `@media (pointer: coarse)` — density survives, the requirement is honoured where a finger is actually used. Use this pattern for any other sub-44px control.
20. **`noUncheckedIndexedAccess` types CSS-module lookups as `string | undefined`**, which collides with `exactOptionalPropertyTypes`. Build class lists as an array and `.filter(Boolean).join(' ')` — a template literal fails lint — and declare any prop that receives one as `?: string | undefined`. The same applies to **any forwarded optional** — passing a row's `dueNote?: string` into a `dueNote?: string` prop is rejected until the receiving prop admits `undefined`.
