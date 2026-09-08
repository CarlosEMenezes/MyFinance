# CI/CD secrets and environments

How `dev`, `test` and `prod` are configured on GitHub, what belongs in each, and what must never go in.

## The rule that keeps `main` clean

Nothing sensitive is committed because **the application has no credential defaults**. `backend/src/main/resources/application.yml` reads `${DB_USERNAME}` and `${DB_PASSWORD}` with no fallback, so the app fails loudly at startup rather than quietly running on a password that is sitting in version control. `.env` files are gitignored. Keep both properties true and there is nothing to leak.

## Today: CI needs no secrets at all

`.github/workflows/ci.yml` runs on every push and pull request and requires **no** secrets:

- Backend tests use in-memory **H2** (`src/test/resources/application.yml`), not PostgreSQL.
- Integration tests will use **Testcontainers**, which starts its own throwaway PostgreSQL. A container needs Docker, not a credential.
- Frontend tests are pure and run in jsdom.

This is worth protecting. A secret-free `ci.yml` means pull requests from forks work, and a malicious PR has nothing to steal. **Do not add `environment:` to the existing CI jobs.** Environments are for deployment jobs, which do not exist yet.

## Secret or variable? Get this right first

GitHub gives you both, per environment. The distinction is not cosmetic:

| | Use for | Read as | In logs |
|---|---|---|---|
| **Secret** | Anything that grants access: passwords, signing keys, API keys, tokens | `${{ secrets.NAME }}` | Masked |
| **Variable** | Non-sensitive config: URLs, hostnames, ports, log levels, feature flags | `${{ vars.NAME }}` | Visible |

Putting a hostname in a secret is a common mistake. It does not make you safer, and it makes every log line containing that host unreadable as `***`, which is genuinely painful to debug.

## What to add, and when

Add each one **when the step that needs it lands**, not now. An unused secret is an unused attack surface.

### Now — nothing

Leave all three environments empty. Nothing in the codebase reads a secret yet.

### With spec §6 step 2 (Identity & settings — auth) and the first deployment

Per environment (`dev`, `test`, `prod`):

| Name | Kind | Why |
|---|---|---|
| `DB_URL` | variable | JDBC URL, no credential in it. Matches `${DB_URL:...}` in `application.yml`. |
| `DB_USERNAME` | **secret** | |
| `DB_PASSWORD` | **secret** | Different value in every environment. Never reuse prod's anywhere. |

**There is no `JWT_SECRET`, and there will not be one.** An earlier draft of
this document listed one, written before the decision was made. [ADR-11](adr/0011-accounts-sessions-and-token-custody.md)
replaced JWTs with opaque session tokens stored hashed in the database: there is
no signing key, so there is no key to distribute, rotate or leak. The row is
removed rather than left "for later", because a secrets document that lists a
credential nobody needs is how that credential ends up being created.

### With BR-8 (multi-currency, live FX provider)

**No secret is needed.** The provider chosen is Frankfurter, which serves
European Central Bank reference rates and requires no API key — which is the
main reason it was chosen, since a credential nobody holds is a credential
nobody can lose.

| Name | Kind | Why |
|---|---|---|
| `BUDGETTRACKER_FX_BASE_URL` | variable | Provider endpoint, read by `CachedExchangeRateProvider`. Defaults to `https://api.frankfurter.app`. Point `dev` and `test` at a stub if you would rather they made no outbound call at all. |
| `BUDGETTRACKER_FX_CACHE_FOR` | variable | ISO-8601 duration, default `PT1H`. |

If the provider is ever swapped for one that does need a key, that key is a
**secret**, and the note above about `VITE_` prefixes applies with full force:
it must be read by the backend and never reach the bundle.

### When a deployment target is chosen

A shape is now proposed: [ADR-13](adr/0013-deploying-on-aws-free-tier.md) — a
single EC2 instance running the application and its database behind Caddy.
Nothing is provisioned, and nothing may be exposed publicly until spec §6.2 is
finished, so the table below is what to add **when the first deploy actually
happens**, not now.

| Name | Kind | Why |
|---|---|---|
| `AWS_DEPLOY_ROLE_ARN` | variable | The IAM role GitHub assumes through OIDC. An ARN is not a credential. |
| `AWS_REGION` | variable | `eu-west-1`. |
| `DEPLOY_HOST` | variable | The instance address. A hostname in a secret only makes logs unreadable. |

**Prefer OIDC federation over long-lived keys** — GitHub mints a short-lived
token per run, and there is no standing credential to leak. AWS, Azure, GCP and
Fly.io all support it. With OIDC there is no `AWS_ACCESS_KEY_ID` and no
`AWS_SECRET_ACCESS_KEY` anywhere, which is the point.

The database credentials do not belong to GitHub at all under ADR-13: they live
in SSM Parameter Store and are read by the instance at boot, so a compromised
workflow cannot read them.

Only if OIDC is unavailable: `DEPLOY_TOKEN` / `REGISTRY_PASSWORD` as environment
secrets on `prod` and `dev`.

### Never a secret

`VITE_API_BASE_URL` and anything else prefixed `VITE_` is **inlined into the JavaScript bundle at build time and served to every visitor**. It is public by construction. Put it in a *variable*. Putting an API key behind a `VITE_` prefix publishes it — if the frontend appears to need a secret, the call belongs on the backend instead.

## Protection rules — the part that actually protects `prod`

Secrets scoped to an environment are only as safe as that environment's rules. Without these, anyone who can push a branch can add a workflow that declares `environment: prod` and print your production database password.

In **Settings → Environments → `prod`**:

- **Deployment branches and tags** → *Selected branches* → `main` only. This is the important one.
- **Required reviewers** → yourself. Deploys then pause for approval.
- Optionally a **wait timer** for a window to cancel a bad deploy.

For `dev` and `test`, branch restrictions can be looser — but only because they hold no production data. Make sure that stays true: `test` should never point at the production database.

Two more, both non-negotiable:

- **Never use `pull_request_target` in a workflow that checks out PR code and touches secrets.** It runs with write permissions and repository secrets against untrusted code — it is the standard way repositories get compromised.
- **Never expose secrets at workflow or job level** with a blanket `env:` block. Pass each one to the single step that needs it, so a third-party action three steps later cannot read it.

## How a deployment job will consume them

For reference, once there is something to deploy:

```yaml
deploy:
  needs: [backend, frontend]
  if: github.ref == 'refs/heads/main'
  runs-on: ubuntu-latest
  environment: prod        # <- this line is what unlocks prod's secrets
  steps:
    - uses: actions/checkout@v4
    - name: Run migrations
      env:                 # scoped to this step only, never the whole job
        DB_URL: ${{ vars.DB_URL }}
        DB_USERNAME: ${{ secrets.DB_USERNAME }}
        DB_PASSWORD: ${{ secrets.DB_PASSWORD }}
      run: ./mvnw -B flyway:migrate
```

## If a secret is ever exposed

Rotate it first, investigate second. A secret that reached a log, a bundle or a commit is compromised even after the commit is removed — removing it from history does not un-publish it.
