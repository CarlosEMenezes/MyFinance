# Budget Tracker

Personal finance planning for someone whose income is irregular.

Every category carries a **plan** and a **reality**, and the app always shows
both plus the variance between them. That is the whole idea: a month is not
"did I overspend", it is "against what, and by how much, and can I still make
the goal I set".

> [!WARNING]
> **Do not expose this publicly yet.** Registration and sign-in work, but the
> hardening in [SPEC-PROMPT.md](SPEC-PROMPT.md) §6.2 — TOTP, recovery codes,
> rate limiting on the auth endpoints, password reset, Argon2id — is not built.
> See [Security](#security) below.

---

## Running it

**Prerequisites:** JDK 17 (BellSoft Liberica), Node 24 (see `.nvmrc`), and
Docker — for the integration tests, and for the PostgreSQL the application runs
against.

### Tests, which need nothing running

```bash
cd backend  && ./mvnw verify          # unit + slice tests on H2, integration on Testcontainers
cd frontend && npm ci && npm test     # Vitest + RTL against MSW
```

`./mvnw`, never a bare `mvn` — the wrapper pins Maven 3.9.16 and a different
version is on most machines' `PATH`.

### The application

A PostgreSQL is needed. There is one in `compose.yml`:

```bash
cp .env.example .env          # then edit it
docker compose up -d          # postgres:17.5 on 127.0.0.1:5432
```

```powershell
cd backend;  .\mvnw spring-boot:run                        # :8085
cd frontend; $env:VITE_USE_MOCK_API='false'; npm run dev   # proxies /api to :8085
```

```bash
cd backend  && ./mvnw spring-boot:run
cd frontend && VITE_USE_MOCK_API=false npm run dev
```

The application reads `.env` itself, so nothing needs exporting. It does that
through an `optional:` config import, which finds nothing on a deployed
instance — there the credentials arrive as real environment variables from SSM
(ADR-13).

`DB_USERNAME` and `DB_PASSWORD` still have **no defaults**. With no `.env` and
no environment variables the application refuses to start, which is the point:
a fallback password in version control is a credential in version control, and
it silently becomes the production one.

The database container publishes to `127.0.0.1` only. The application is meant
to be reachable from other machines; the database is not.

### On your LAN, from a phone

```powershell
docker compose up -d
cd backend;  .\mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
cd frontend; npm run dev:lan
```

```bash
docker compose up -d
cd backend  && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm run dev:lan
```

> [!NOTE]
> **The quotes around `-Dspring-boot.run.profiles=local` are load-bearing in
> PowerShell.** Unquoted, it is split into `-Dspring-boot` and
> `.run.profiles=local`, and Maven answers `Unknown lifecycle phase
> ".run.profiles=local"` — which reads like a Maven problem and is not.

Then open `http://<your-machine-ip>:5173` on the other device. Vite binds every
interface with `--host`, and its `/api` proxy runs on your machine — so the
phone sees one origin and the API is reached locally.

> [!IMPORTANT]
> **The `local` profile is not optional here.** The session cookie is `Secure`
> (ADR-11), and browsers only accept a `Secure` cookie over HTTPS. Without the
> profile, signing in over `http://192.168.…` returns 201 and the browser then
> silently discards the cookie — so every request after it comes back 401 while
> the sign-in itself looked like it worked. `application-local.yml` turns
> `Secure` off for exactly this case, and says why. Never use it in production.

### The frontend on its own

```bash
cd frontend && npm run dev
```

Without `VITE_USE_MOCK_API=false` the app runs against MSW's browser worker,
answering from the same `src/test/handlers.ts` the tests use — so what the
browser shows is what the tests assert. A production build never contains it:
`import.meta.env.DEV` is statically false, so the dynamic import is dropped.

---

## How it is put together

```
backend/     Java 17 · Spring Boot 4 · PostgreSQL + Flyway
  domain/          entities, value objects, calculators — no Spring, no JPA
  application/     use-case services, ports, DTOs
  infrastructure/  JPA adapters, the FX client, security
  api/             REST controllers and error handling
frontend/    Vite · React 18 · TypeScript strict
  components/  one folder per reusable component
  features/    one folder per page
  lib/         pure money, date and rule functions — 100% tested
  types/api.ts the frozen API contract
```

The dependency rule is `api → application → domain` and
`infrastructure → application`, enforced by ArchUnit on every build. The domain
depends on nothing.

**Money never touches a float.** `BigDecimal` at scale 2, HALF_UP in Java;
integer minor units in TypeScript. ArchUnit fails the build on a `double` field
anywhere, including record components.

---

## Where the rules live

The business rules are numbered BR-1 to BR-27 in [SPEC-PROMPT.md](SPEC-PROMPT.md)
§3, and every one of them has at least one test named after it. A few worth
knowing before reading the code:

| Rule | What it decides |
|---|---|
| **BR-1, BR-2** | What you actually have. Borrowing raises what is available *and* what is owed, so a loan nets out to its interest and is never income |
| **BR-4** | A credit-card purchase is not owed on the day you spend it. Two independent month rolls decide when the bill falls |
| **BR-9** | `real − planned` on both sides of the plan; only the colour differs |
| **BR-10** | Plans are counted on **real dates**, so a month holding five paydays plans five — never an average |
| **BR-8** | A rate that cannot be fetched **blocks the save**. Nothing is ever guessed |

Two implementations of BR-4, BR-6, BR-7, BR-9, BR-10 and BR-11 exist — once in
Java for anything persisted, once in TypeScript for figures the user has not
committed to yet. They are held identical by
[docs/business-rule-vectors.md](docs/business-rule-vectors.md), and **neither
may be edited to agree with the other, only with that file**.

## Decisions worth reading first

Full log in [CLAUDE.md](CLAUDE.md) §5; the long notes are in [docs/adr/](docs/adr/).

- **[ADR-7](docs/adr/0007-business-rule-boundary.md)** — the frontend may
  compute a figure you have not saved yet; anything the server returns is
  rendered, never recomputed.
- **[ADR-11](docs/adr/0011-accounts-sessions-and-token-custody.md)** — sessions
  are opaque tokens stored hashed, in an HttpOnly cookie, not JWTs. One user
  cannot see another's anything, and another user's id answers **404, never
  403**.
- **[ADR-12](docs/adr/0012-frontend-first-against-a-frozen-contract.md)** — the
  nine pages were built first against a frozen contract, and the backend
  followed. What that cost, and what stops the two drifting.

---

## Testing

Red → green → refactor, always; the failing test is written first. Test names
state behaviour and cite their rule — `expenseAfterClosingDayRollsToNextStatement()`,
not `testBillDate2()`.

| Layer | What it covers |
|---|---|
| Domain unit tests | The calculators, table-driven over the boundary cases |
| Application tests | Services with the ports mocked |
| `@DataJpaTest` | Repositories against the real migrations, **including a two-user isolation test per slice** |
| `@WebMvcTest` | Controllers through the real security chain |
| Testcontainers | Each slice end to end on real PostgreSQL |
| ArchUnit | The dependency rule, and no JPA or floats in the domain |

Coverage floors fail the build: **90%** for domain, application and frontend
`lib/`; **70%** overall.

**A test is never deleted, skipped or weakened to make a build pass.** If one
fails, either the code is wrong or the test is changed deliberately, in its own
commit, with the reason recorded in [CLAUDE.md](CLAUDE.md).

---

## Security

Built: registration, sign-in and sign-out; per-user isolation enforced in the
schema, in every repository, and in a two-user test that fails loudly when the
other two are got wrong; BCrypt through `DelegatingPasswordEncoder`; sessions
as opaque tokens stored hashed in an HttpOnly `SameSite=Strict` cookie. No
JavaScript anywhere handles a credential.

**Not built, and required before this is reachable from the internet:** TOTP,
ten single-use recovery codes, rate limiting on the authentication endpoints,
password reset, and Argon2id as the encoder default (a change of default that
re-hashes on next sign-in, not a forced reset).

CI needs **no secrets** — the tests run on H2 and Testcontainers — and it must
stay that way so pull requests from forks keep working.

---

## What is not built

Spec §6 steps 1–10 are complete. Still ahead, in order:

1. **The swap** — run against the real API and confirm every page renders the
   figures it rendered against fixtures.
2. **Step 11** — Playwright journeys, a performance pass, an accessibility
   audit, and the offline mode specified as Phase 1.5 (BR-26, BR-27).
3. **§6.2** — the security work above, before any public exposure.
4. **Phase 2** — tags and recurrence, spreadsheet import, transaction
   detection. Specified as BR-16 to BR-25; none of it implemented.

There is no Import page and no `/jobs` endpoint on purpose: nothing asks for
them yet, and a nav entry pointing at a page that does not exist is worse than
no entry.
