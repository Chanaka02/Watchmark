# Deployment options (Milestone 10, step 1)

Status: **decided: option A (VPS + Docker Compose + Caddy)**, prepared and
tested locally; see [deployment.md](deployment.md). Nothing has been
provisioned or paid for. Prices
were checked on the providers' own pages on 2026-11-06 and change often;
re-check before buying.

## What has to run

| Part | Needs |
|---|---|
| Backend (Spring Boot 4, Java 25) | Always on (two scheduled jobs: metadata refresh, retention purge; sessions in MySQL). About 300–450 MB RAM. One instance only: the jobs have no distributed lock. |
| MySQL 8.4 | Real MySQL (generated columns, CHECK constraints, foreign keys). About 400 MB RAM with a small buffer pool. Persistent disk, backups. |
| Frontend | Static files (393 kB main bundle), served on the same origin as `/api` so session cookies stay first-party. |
| HTTPS | Required: `SESSION_COOKIE_SECURE=true`. Needs a domain name. |
| Secrets | DB password, TMDB read access token: environment variables, never in Git. |

So: about **1.2 GB RAM in total**, which means a 2 GB machine at minimum and 4
GB to be comfortable. Free tiers that sleep when idle are ruled out: they stop
the scheduled jobs.

TMDB terms (non-commercial use, attribution, at most 6 months of caching) put
no restriction on where the app is hosted; the app already enforces caching
and attribution (see [tmdb.md](tmdb.md)).

## The options

### A. VPS + Docker Compose + Caddy (recommended)

One small Linux server runs the same Compose setup as development, plus Caddy
as the HTTPS reverse proxy (automatic Let's Encrypt certificates) that also
serves the frontend.

| Provider, plan | RAM / vCPU / disk | Price per month |
|---|---|---|
| Hetzner CPX12 (Germany/Finland) | 2 GB / 1 / 40 GB | €11.99 excl. VAT, + backups from €1.20 |
| Hetzner CPX22 (Germany/Finland) | 4 GB / 2 / 80 GB | €19.99 excl. VAT |
| Hetzner CPX02 (Singapore) and up | priced higher than Germany | from €7.99 for 1 GB (too small) |
| DigitalOcean Basic | 2 GB / 1 / 50 GB | $12, + $3.60 daily backups |
| DigitalOcean Basic | 4 GB / 2 / 80 GB | $24, + $7.20 daily backups |

Hetzner's cheaper "Cost-Optimized" plans were marked *currently not available*.

- **Cost:** flat and predictable, roughly **€13–25 per month** all in.
- **Security:** you own OS updates, the firewall and SSH. The plan automates
  them: unattended security updates, firewall allowing only 22/80/443, SSH keys
  only, MySQL not exposed to the internet, and HSTS and CSP set in Caddy.
- **Backups:** provider snapshots (whole server, daily) **plus** a nightly
  `mysqldump` copied off the server, with a restore that is actually tested.
- **Maintenance:** about an hour a month (updates, checking backups). Upgrades
  are `docker compose pull && up -d`; rollback means redeploying the previous
  image tag.
- **Fit:** the best one. It is the setup already used and tested locally, and
  memory, the expensive resource for a JVM plus MySQL, is cheap here.

### B. Managed platform (Railway as the closest fit)

Railway runs the backend and a MySQL container from the repository, with no
server to administer.

- **Cost:** $5 per month includes $5 of usage, then about **$10 per GB of RAM**
  and $20 per vCPU per month. With about 1.2 GB always on, plus a little CPU,
  that is an estimated **$15–20 per month**, and it can creep up with usage.
- **Security:** the platform patches the OS; you still own app settings and
  secrets.
- **Backups:** the pricing page does not mention volume backups, so this must
  be checked. A nightly `mysqldump` to external storage would be needed anyway.
- **Maintenance:** less operating work, but MySQL is still a container that you
  upgrade, not a managed database.
- **Fit:** good developer experience, but the usage billing is weakest exactly
  where this app is heavy (always-on memory).

### Not recommended

- **Render:** lists managed Postgres only, no MySQL. MySQL would run as a
  private service with a disk: more cost, no managed backups.
- **DigitalOcean App Platform + Managed MySQL:** the managed MySQL alone is $15
  per month (1 GB), before the app itself. It is solid but costs about twice as
  much as option A for a personal project.
- **Free or sleeping tiers:** stop the scheduled jobs and lose warm caches.

## Recommendation

**Option A on Hetzner CPX22 (4 GB) in Germany or Finland**, about €20 per month
plus backups, or CPX12 (2 GB) at about €12 if you accept tighter memory. If you
mostly use the app from Sri Lanka, compare Singapore (higher price, lower
latency) or DigitalOcean's Singapore or Bangalore regions.

A domain is needed for HTTPS: your own (about $10–15 per year at any
registrar), or a free subdomain service if you prefer not to buy one.

## What the rest of Milestone 10 does

**Possible now, at no cost, and tested locally in Docker on this machine:**

1. Production `Dockerfile` for the backend, and a production Compose file
   (backend, MySQL, Caddy serving the frontend and proxying `/api`).
2. `Caddyfile` with HTTPS, HSTS, the Content-Security-Policy from
   [security.md](security.md), and forwarded headers for real client IPs.
3. Backup and restore scripts (nightly `mysqldump` with retention, plus an
   off-server copy), and a **restore drill**: back up, wipe, restore, and verify
   that the app and its data come back.
4. A deployment guide (first install, updates, rollback by image tag,
   rotating secrets) and a server hardening checklist.

**Needs your decision, and payment by you:** renting the server and the domain.
I will not create accounts, provision or pay for anything. With the server and
domain in place, the guide takes it from there; I can walk you through it or
run the steps over SSH if you ask me to.
