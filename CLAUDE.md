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
cd backend  && DB_USERNAME=… DB_PASSWORD=… ./mvnw spring-boot:run   # needs PostgreSQL
cd frontend && npm ci && npm run dev  # proxies /api to :8085
```

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
15. **BR-15 View state.** Earnings group by none/group/frequency; Expenses by none/group/account. Both sort by category/planned/real/variance. Expenses also filter by payment method — displayed totals respect the filter, while **dashboard totals always cover the whole period**.

---

## 5. Architectural decisions (ADR log)

| # | Decision | Rationale |
|---|---|---|
| **ADR-1** | Spring Boot **4.0.7**, not the spec's 3.x | Already in the pom and cached in `~/.m2`; Boot 4.x baseline is Java 17 (max 26, Maven ≥ 3.6.3), so it satisfies the real constraint. Downgrading is backwards motion. Full note: [docs/adr/0001-spring-boot-4.md](docs/adr/0001-spring-boot-4.md) |
| **ADR-2** | Replace the Expo/React-Native frontend with **Vite + React web** | The design is CSS custom properties, `color-mix()`, `:has()` and a 940px breakpoint — none exist in RN. [docs/adr/0002-vite-web-over-expo.md](docs/adr/0002-vite-web-over-expo.md) |
| **ADR-3** | Mobile shipped later via **Capacitor**, not a rewrite | Wraps the built web app; needs no source change, so §0.6 component work is not duplicated. [docs/adr/0003-capacitor-for-mobile.md](docs/adr/0003-capacitor-for-mobile.md) |
| **ADR-4** | PostgreSQL + Flyway, replacing MySQL + `ddl-auto=update` | Spec §4. `ddl-auto=update` leaves the schema unversioned and undoes reproducibility. |
| **ADR-5** | Hexagonal layering enforced by ArchUnit from day one | Spec §4. Cheap to add now, near-impossible to retrofit. |
| **ADR-6** | Money: `BigDecimal` scale 2 HALF_UP in Java, integer **minor units** in TS | Spec §0.5. No `double`/`float`/JS `number` for money arithmetic, ever. |

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
- [x] **`statementCycle.ts`** — BR-4 `billDateFor`, two independent month rolls, cycle days validated to 1–28. 27 tests, table-driven over the boundaries §4 asks for. 100% coverage.
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
- [ ] `SegmentedControl` → `TagChip` → `ProgressBar` → `MoneyText` → `VarianceText` → `AccountCard` → `CardSummary` → `NotificationRow` → `LeadTimeToggle` → `Checkbox` → `Dialog` → `LogEntryForm` → `InstalmentCalculatorPanel` → `WhatIfPanel` → `SidebarNav` → `BottomTabBar` → `PageHeader` → `PeriodPicker` → `FilterChips` → `EmptyState`

Both `lib/` modules sit at 100% line and function coverage; branch coverage is 97–98% because `noUncheckedIndexedAccess` requires `?? …` fallbacks on indexed reads that the surrounding validation already makes unreachable. Above the 90% floor, and preferable to casting the check away.

### Next
1. Finish the `lib/` modules above.
2. The reusable components in the order spec §5 lists them, each in its own folder per §0.6, starting with `Panel` (the `.blueprint` frame plus its four corner marks).
3. Then the backend vertical slices, §6 steps 2–10.

The prototype's `DCLogic` class is the reference implementation for every item in (1); [docs/design-reference.md](docs/design-reference.md) maps each rule to its line number in the handoff bundle.

---

## 8. Gotchas

Things discovered the hard way. Never rediscover these.

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
19. **Never let a component variant depend on out-specifying a design-system class.** `.card` and a CSS-module class have equal specificity, so which padding wins depends on stylesheet order, not intent — and a variant can silently lose in a production build while looking right in dev. `Panel` therefore uses `.blueprint` for the frame, owns its own layout, and exposes `density` as a **prop**. Add variants as props, not as overrides.
21. **Spec §5's 44px touch target vs the design's dense tables.** The design's inline plan field is 22px, right for a dense table under a mouse. `EditablePlanCell` keeps that and grows the target to 44px under `@media (pointer: coarse)` — density survives, the requirement is honoured where a finger is actually used. Use this pattern for any other sub-44px control.
20. **`noUncheckedIndexedAccess` types CSS-module lookups as `string | undefined`**, which collides with `exactOptionalPropertyTypes`. Build class lists as an array and `.filter(Boolean).join(' ')` — a template literal fails lint — and declare any prop that receives one as `?: string | undefined`. The same applies to **any forwarded optional** — passing a row's `dueNote?: string` into a `dueNote?: string` prop is rejected until the receiving prop admits `undefined`.
