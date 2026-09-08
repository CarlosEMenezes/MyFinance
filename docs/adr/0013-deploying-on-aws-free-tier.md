# ADR-13 — Deploying on AWS: one small instance, not a distributed system

**Status:** Proposed
**Date:** 2026-09-08

## Context

The application runs. Spec §6 steps 1–10 are complete, the swap from MSW to the
real API has been verified, and it now needs somewhere to live that is not a
laptop.

[docs/ci-secrets.md](../ci-secrets.md) has carried a section headed *"When a
deployment target is chosen"* saying the decision was "deliberately unfilled".
This fills it.

Nothing here is provisioned. This is a shape to argue with before any money or
DNS is committed to it.

> [!WARNING]
> **This ADR does not authorise a public deployment.** Spec §6.2 — TOTP,
> ten single-use recovery codes, rate limiting on the authentication endpoints,
> password reset — is not built. Until it is, whatever is deployed must be
> reachable only by you: a security group locked to your own IP, or a Tailscale
> network, or both. An application holding a complete picture of somebody's
> finances, with no rate limiting on its login endpoint, does not go on the open
> internet.

## What actually decides this

Four constraints come from decisions already made. They rule out more options
than cost does.

**1. One origin, because the session cookie is `SameSite=Strict`.**
[ADR-11](0011-accounts-sessions-and-token-custody.md) put the session in a
`SameSite=Strict` cookie, which a browser will not attach to a request to a
different site. So `app.example.com` serving the pages and `api.example.com`
serving `/api` **cannot work** — the API would never see a session. The
frontend and the API must answer on one hostname. This is the single biggest
constraint on the topology, and it is not negotiable without reopening ADR-11.

**2. HTTPS from the first request.** The same cookie is `Secure`. Over plain
HTTP the browser accepts the sign-in response and silently discards the cookie,
so the application appears to sign you in and then behaves as though you are
not. There is no useful HTTP deployment, only a confusing one.

**3. The application has no credential defaults.** `application.yml` reads
`${DB_USERNAME}` and `${DB_PASSWORD}` with no fallback and fails at startup
without them. That is deliberate, and it means the deployment has to supply
them from somewhere that is not the repository.

**4. Flyway owns the schema.** Deploying *is* starting the application: it
migrates on boot. There is no separate migration step to orchestrate, and no
window where the code is newer than the schema.

## Decision

**One `t3.micro` EC2 instance in `eu-west-1` (Ireland), running Docker Compose:
Caddy terminating TLS, the Spring Boot application, the built frontend as static
files, and PostgreSQL 17.5 on a volume.**

Caddy serves `/` from the static build and reverse-proxies `/api/*` to the
application on `:8085`. One hostname, one certificate, one origin — which is
what constraint 1 requires and what every other topology has to work to
achieve.

`eu-west-1` because it is the nearest region to Ireland, which is where the
application's own default currency and date format assume its user is.

### Why not something more production-shaped

| Option | Why not |
|---|---|
| **CloudFront + S3 + EC2 + RDS** | The obvious "proper" answer, and it does satisfy constraint 1 if CloudFront routes `/api/*` to the EC2 origin under one distribution. But it is four services to reason about instead of one, and RDS is roughly **€15/month** once its 12-month allowance ends. Worth revisiting when there is more than one user. |
| **Elastic Beanstalk** | Still EC2 underneath, plus a layer that decides things for you. When it misbehaves you debug both. |
| **App Runner** | No free tier at all. |
| **Lambda + API Gateway** | A poor fit for this application specifically. Spring Boot cold starts are seconds, and `CachedExchangeRateProvider` caches rates **in memory per instance** — every cold start would re-fetch, turning a once-an-hour call into one per invocation. Sessions would survive, being database-backed, but that is the only part that would. |
| **Lightsail** | Free for three months, then billed. A deadline, not a free tier. |
| **Fly.io / Render** | Genuinely good fits, and cheaper to operate. Excluded only because AWS was asked for. Worth knowing they exist if the AWS bill ever surprises you. |

### The cost, honestly

> [!IMPORTANT]
> **Verify the free tier at signup rather than trusting this paragraph.** AWS
> changed the free tier in 2025 to a credit-based model for new accounts —
> a fixed credit allowance over a limited window, rather than the familiar "750
> hours of t3.micro every month for 12 months". Which one applies depends on
> when the account was created. **Do not assume.**

Either way, step 1 of the runbook is a budget alarm, because the failure mode of
a free tier is not a hard stop — it is a bill.

Rough steady-state cost after any allowance ends: **around €8/month** for a
`t3.micro` plus a small EBS volume, in `eu-west-1`. Compare with roughly €23 for
the CloudFront/RDS shape.

## Runbook

Each step is one thing, and the order matters.

**1. Set a budget alarm before anything else.**
AWS Budgets → cost budget → **$1/month**, alert at 100% of *forecast* and 100%
of *actual*, to an address you read. This is the single most valuable five
minutes on a new AWS account. Free tiers do not stop, they lapse.

**2. Region and account.** Work in `eu-west-1`. Enable MFA on the root user and
then stop using it; create an IAM user or Identity Center user for daily work.

**3. Security group.** Inbound: `443` and `22`, **both restricted to your own
IP**, not `0.0.0.0/0`. Outbound: leave open — the application calls the FX
provider. Port 80 is needed only briefly for the Let's Encrypt HTTP challenge;
Caddy can use the TLS-ALPN challenge on 443 instead and avoid opening it at all.

**4. The instance.** Amazon Linux 2023, `t3.micro`, 20 GB gp3 (the free-tier EBS
allowance is 30 GB). Key pair for SSH. No public IP is needed if you reach it
over Tailscale, which is the better answer while §6.2 is outstanding.

**5. Docker.**

```bash
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user   # log out and back in
```

**6. Secrets in SSM Parameter Store** — standard parameters, no charge:

```bash
aws ssm put-parameter --name /budgettracker/prod/db-username \
  --type SecureString --value '…'
aws ssm put-parameter --name /budgettracker/prod/db-password \
  --type SecureString --value "$(openssl rand -base64 24)"
```

Attach an instance role granting `ssm:GetParameter` on
`/budgettracker/prod/*` and `kms:Decrypt`, and nothing else. The instance reads
them at boot; they never enter the repository, a GitHub secret, or a shell
history.

**7. The compose file.** Not committed — spec §0.4 forbids scaffolding ahead of
the step that needs it, and there is no deploy to run yet. It is here so the
shape is reviewable:

```yaml
services:
  db:
    image: postgres:17.5
    environment:
      POSTGRES_DB: budgettracker
      POSTGRES_USER: ${DB_USERNAME:?}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?}
    volumes: [db:/var/lib/postgresql/data]
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U "$$POSTGRES_USER" -d budgettracker']
      interval: 10s
    restart: unless-stopped

  api:
    image: ghcr.io/…/budgettracker:${TAG}
    environment:
      DB_URL: jdbc:postgresql://db:5432/budgettracker
      DB_USERNAME: ${DB_USERNAME:?}
      DB_PASSWORD: ${DB_PASSWORD:?}
      # No `local` profile here. The cookie must keep its Secure flag.
    depends_on:
      db: {condition: service_healthy}
    restart: unless-stopped

  web:
    image: caddy:2
    ports: ['443:443', '80:80']
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - ./dist:/srv:ro          # the frontend build
      - caddy-data:/data        # certificates survive a restart
    depends_on: [api]
    restart: unless-stopped

volumes: {db: {}, caddy-data: {}}
```

**8. The `Caddyfile`** — three lines do the whole job, TLS included:

```
budget.example.com {
    handle /api/* { reverse_proxy api:8085 }
    handle { root * /srv; try_files {path} /index.html; file_server }
}
```

`try_files … /index.html` because React Router owns the paths: a hard refresh on
`/goals` must reach the app, not a 404.

**9. Build and ship.** `./mvnw spring-boot:build-image` produces an OCI image
through Paketo buildpacks with no Dockerfile to maintain. Push to GitHub
Container Registry — free, and it keeps ECR's 500 MB allowance out of the
picture. Build the frontend with `npm run build` and copy `dist/` to the
instance.

**10. DNS.** An A record to the instance. Caddy obtains the certificate on first
start; nothing else to configure.

**11. Backups.** Not optional, and not provided by anything above:

```bash
docker exec db pg_dump -U "$DB_USERNAME" budgettracker | gzip \
  | aws s3 cp - "s3://…/budgettracker-$(date +%F).sql.gz"
```

Nightly cron, S3 lifecycle rule expiring after 30 days. **Restore it once**, to
a scratch database, before believing in it. An untested backup is a hope.

**12. Teardown.** Terminate the instance, delete the EBS volume and the
Elastic IP if one was allocated. An unattached Elastic IP is billed — it is the
commonest way an "empty" AWS account keeps charging.

## Consequences

**Accepted:**

- **No managed backups.** Step 11 is a cron job you own; RDS would do it for you
  at roughly €15/month.
- **One box.** A restart is downtime, and there is nothing to fail over to. For
  a personal finance planner with one user, an occasional minute offline costs
  nothing.
- **Patching is yours.** `dnf update` and image rebuilds are a standing chore.

**Gained:**

- **One origin for free**, which the `SameSite=Strict` cookie requires and which
  every other topology has to be configured into.
- **Portability.** The compose file runs on any Linux box, including your
  laptop. If AWS stops being the answer, moving is a DNS change and a `docker
  compose up`, not a rewrite.
- **A small, comprehensible surface.** One instance, one certificate, one
  process to restart when something is wrong at 11pm.

## Before the first public deploy

Not a wish list — the gate:

- [ ] Spec §6.2 complete: TOTP, recovery codes, rate limiting, password reset.
- [ ] `app.cookie.secure` left at its default of `true`; the `local` profile
      never present in a deployed environment.
- [ ] The budget alarm firing to an address you actually read.
- [ ] A backup restored to a scratch database at least once.
- [ ] Security group reviewed once more, after everything else is working.
