# ADR-11 — Where registered users live, how they are signed in, and what is never given back

Status: accepted
Date: 2026-09-06
Supersedes nothing. Constrains spec §6.2 (identity hardening) and §6.3 (Open Finance).

---

## Context

Until now the application had one user: a passwordless row seeded by
`V3__seed_single_user.sql` with a fixed, publicly-known UUID, resolved by a
`CurrentUser` adapter that returned a constant. That was honest scaffolding
while nothing was exposed, and a hole the moment anything is.

Isolation in this codebase is enforced **per repository** — `findAllForUser`,
`findByUserIdAndId` — against a user id supplied by that port. With one user
the filter is untested by construction: every query returns everything, and
would keep passing if the `WHERE` clause were deleted.

Seven feature slices remain (cards, categories, transactions, financing,
dashboard, goals, notifications). Each adds a table with a `user_id` and a
repository that must filter by it. **Adding authentication after those slices
means auditing seven more places for a leak; adding it before means every one
of them is written against a real principal and tested against a second user
from its first commit.**

Phase 3 (spec §6.3) will additionally hold consent tokens for financial
institutions. Deciding how secrets are custodied *before* there are any is
much easier than deciding it afterwards.

---

## Decision

### 1. Registered users live in `app_user`, in the application's own PostgreSQL database

One table, one row per person, alongside their financial data rather than in a
separate identity store or a third-party provider.

**Why not an external identity provider.** ADR-10 already refused social
sign-in: it does not reduce the security work, and linking an email signup to a
provider on the same address is a takeover vector for no product gain. The same
reasoning rules out an identity-as-a-service dependency for a single-tenant
personal finance app — it adds an outage surface, a data-processor
relationship, and a per-user cost, in exchange for solving a problem
(`register`, `login`, `hash a password`) that is about two hundred lines here.

**What the row holds.**

| Column | Purpose |
|---|---|
| `id` | UUID, the only user identifier that ever appears in another table |
| `email` | Stored **lower-cased**, unique. The login identifier |
| `password_hash` | A `DelegatingPasswordEncoder` string, prefix included |
| `created_at` | Audit |
| everything else | Profile and preferences, unchanged from V2 |

**What the row never holds:** a plaintext password, a password hint, a security
question, a recovery email, or anything from a financial institution. Secrets
that belong to a *provider* live in their own table (see §5) precisely so that
no query which reads a profile can reach them.

### 2. Passwords are hashed with `DelegatingPasswordEncoder`, BCrypt today and Argon2id at §6.2

Spec §6.2 makes Argon2id part of identity **hardening**, which is a later step
and a prerequisite for Phase 3. Storing anything weaker than a real
password-hashing function in the meantime would still be wrong, so the choice
is between BCrypt now and pulling BouncyCastle forward.

`DelegatingPasswordEncoder` writes the algorithm into the hash (`{bcrypt}…`)
and can verify any of them. Switching the default to `{argon2}` at §6.2
re-hashes each password on that user's next successful login, with no downtime
and no forced reset. That migration path is the reason to use the delegating
encoder from the first commit rather than a bare `BCryptPasswordEncoder`.

### 3. Sessions are opaque tokens stored hashed, delivered in an HttpOnly cookie — not JWTs

Spec §4's stack list says "Spring Security (JWT)". This deviates from that
parenthetical, deliberately, for the same class of reason as ADR-1 and ADR-2.

A JWT is the right tool when the party validating the token cannot ask the
issuer — several services, or a third party. **This application is one service
with a database it already queries on every request.** Against that, a
stateless token costs:

- **Revocation.** Signing out, changing a password, or losing a laptop must end
  a session *now*. A stateless JWT cannot be withdrawn, so revocation is bolted
  back on as a denylist or a version column — server state, which is the thing
  statelessness was supposed to avoid.
- **A signing key.** One more secret to hold, rotate and leak. An opaque token
  has no key at all.

So: **32 bytes from a `SecureRandom`, base64url-encoded.** The database stores
only a SHA-256 of it, for the same reason it stores only a hash of a password —
a leaked backup then contains no usable session. Logging out deletes the row.
"Sign out everywhere" deletes every row for that user. Expiry is a column.

The token reaches the browser as a cookie that is:

| Attribute | Value | Why |
|---|---|---|
| `HttpOnly` | yes | JavaScript cannot read it, so an XSS cannot exfiltrate the session |
| `SameSite` | `Strict` | the browser does not attach it to cross-site requests, which is what makes CSRF a non-issue for this API |
| `Secure` | yes outside dev | never sent over plain HTTP |
| `Path` | `/api` | not sent with static assets |

**This is why the frontend has no token handling at all** and why `lib/http.ts`
needs no `Authorization` header: the browser attaches the cookie, and the app
never sees it. Storing a JWT in `localStorage` — the common SPA pattern — would
put the session inside reach of every script on the page.

### 4. One user cannot see another user's anything

Enforced in three independent places, because a single mechanism is a single
point of failure:

1. **Schema.** Every user-owned table carries `user_id NOT NULL REFERENCES
   app_user (id) ON DELETE CASCADE`. There is no row without an owner.
2. **Repository.** Every finder takes the user id and filters on it. There is
   no `findById` on a user-owned entity that does not also match the owner.
3. **Test.** Every feature slice has a test that creates **two** users and
   asserts the second cannot read, edit or delete the first's rows. This is
   not optional per slice: it is the only one of the three that fails loudly
   when the other two are got wrong.

**Another user's row answers 404, never 403.** A 403 confirms the id exists,
which tells an attacker enumerating ids exactly which ones are real. As far as
the API is concerned, someone else's data does not exist.

### 5. Open Finance consent tokens are never readable through any API

Ahead of §6.3, so that the constraint exists before the feature does:

- Consent tokens live in **their own table**, keyed by user, and never as a
  column on `app_user`. A query that reads a profile must not be able to reach
  a bank credential by accident.
- They are **encrypted at rest with a key held outside the database**, rotatable
  without re-consenting, per spec §6.3.
- **No DTO, no endpoint, no log line, and no error message ever contains one.**
  There is no "show me my connection details" screen and there will not be one:
  the token authorises *the server* to fetch on the user's behalf, and the user
  has no use for its value. What the user is shown is which institution is
  connected, when consent expires, and a button to revoke it.
- Revoking consent deletes the token rather than marking it inactive.

The distinction spec §6.3 draws matters here and is worth restating: this
application is an OAuth **client** holding a consent token for an institution.
That is not the same feature as signing a user in with a social provider, which
ADR-10 refuses. Nothing in this ADR licenses the latter.

### 6. What is deliberately not built yet

Spec §6.2 owns these, and building them badly now would be worse than building
them properly later:

- **TOTP second factor and ten single-use recovery codes.**
- **Rate limiting on authentication endpoints.** Needed before public exposure;
  it needs a store and a policy, and it is §6.2's to specify.
- **Password reset by email.** Needs an email provider and a signed, expiring,
  single-use token — a whole flow, not a field.

**The application must not be exposed publicly until §6.2 is done.** Until
then it is safe on a private network and unsafe on the open internet, and that
sentence belongs in the README as well as here.

---

## How to set this up

**Locally.** Nothing to configure. Run the migrations, open the app, and
register. The first person to register is simply the first row; there is no
bootstrap admin and no privileged account, because there are no roles.

**In an environment.** Two secrets, both already the pattern established by
`DB_USERNAME` / `DB_PASSWORD` in [ci-secrets.md](../ci-secrets.md) — supplied by
the environment, never defaulted in code, and the application fails at startup
if they are missing:

| Variable | Purpose |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | unchanged |
| `APP_COOKIE_SECURE` | `true` everywhere a browser reaches the app over HTTPS. `false` only for local HTTP development |

There is deliberately **no** session-signing secret to manage. That absence is
one of the reasons for choosing opaque tokens.

**When a second person is added**, nothing changes operationally: they
register, and the `user_id` filters already separate them. Nothing in this
design assumes a single user, and the two-user isolation tests exist to keep
that true.

---

## Consequences

**Good.** Sessions can be revoked instantly and individually. There is no
signing key to rotate or leak. The browser holds nothing a script can read.
Isolation is enforced at three levels and tested at the level that fails
loudest. Every remaining feature slice is written against a real principal
instead of being retrofitted.

**Costs.** One indexed database read per authenticated request — negligible
here, and the price of revocation. A deviation from the spec's stack note,
recorded here rather than made quietly. And the app is single-node until
sessions are shared, which for a personal finance application is not a
constraint worth paying to remove yet.

**Revisit if** the API is ever consumed by something that is not this
first-party browser client — a native app that cannot hold cookies, or a
second service. That is the point at which a bearer token earns its
complexity, and not before.
