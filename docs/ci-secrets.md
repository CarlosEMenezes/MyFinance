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
| `JWT_SECRET` | **secret** | HS256 signing key, ≥ 256 bits, unique per environment. Generate with `openssl rand -base64 48`. A shared key means a `dev` token authenticates against `prod`. |

### With BR-8 (multi-currency, live FX provider)

| Name | Kind | Why |
|---|---|---|
| `FX_API_KEY` | **secret** | |
| `FX_API_URL` | variable | Provider endpoint. Point `dev` and `test` at a sandbox or a stub so tests never burn real quota. |

### When a deployment target is chosen

No hosting decision has been made yet, so this is deliberately unfilled. When it is, **prefer OIDC federation over long-lived keys** — GitHub mints a short-lived token per run and there is no standing credential to leak. AWS, Azure, GCP and Fly.io all support it.

Only if OIDC is unavailable: `DEPLOY_TOKEN` / `REGISTRY_PASSWORD` as environment secrets on `prod` and `dev`.

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
