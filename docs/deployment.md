# Deployment guide (VPS + Docker Compose + Caddy)

The chosen setup from [deployment-options.md](deployment-options.md): one Linux
server runs three containers.

```
internet ──443/80──▶ web (Caddy: HTTPS, security headers, frontend, /api proxy)
                         │ private Docker network
                         ▼
                      backend (Spring Boot)  ──▶  mysql (MySQL 8.4, volume)
```

Only Caddy has published ports. MySQL and the backend are reachable only inside
the Docker network, and Actuator is not exposed at all.

Everything below marked *tested locally* was run end to end on a developer
machine with the same files (HTTPS on `localhost:8443`, mock catalogue).
Steps marked *server only* (operating system, firewall, DNS) can only be done
on the real server.

| File | Purpose |
|---|---|
| `backend/Dockerfile` | Backend image: built with JDK 25, runs on a JRE as uid 10001, heap sized from the container limit. |
| `deploy/web.Dockerfile` | Web image: frontend build plus Caddy; validates the Caddyfile at build time. |
| `deploy/Caddyfile` | HTTPS (automatic certificates), HSTS, CSP and other headers, caching, SPA fallback, `/api` proxy. |
| `deploy/compose.yml` | The production stack, with memory limits sized for a 2–4 GB server. |
| `deploy/production.env.example` | Template for `deploy/production.env` (git-ignored; secrets). |
| `deploy/backup.sh`, `deploy/restore.sh` | Database backup (checked, compressed, with retention) and restore. |

## 1. Prepare the server (*server only*)

Ubuntu 24.04 LTS, 2 GB RAM minimum (4 GB recommended). As root, once:

```sh
adduser --disabled-password deploy && usermod -aG sudo deploy
# Copy your SSH public key to /home/deploy/.ssh/authorized_keys, then:
sed -i 's/^#\?PasswordAuthentication .*/PasswordAuthentication no/; s/^#\?PermitRootLogin .*/PermitRootLogin no/' /etc/ssh/sshd_config
systemctl restart ssh

apt update && apt -y upgrade
apt -y install unattended-upgrades ufw git
dpkg-reconfigure -plow unattended-upgrades       # automatic security updates

ufw default deny incoming && ufw default allow outgoing
ufw allow OpenSSH && ufw allow 80/tcp && ufw allow 443/tcp && ufw allow 443/udp
ufw enable
```

Install Docker Engine and the Compose plugin from Docker's own repository
(docs.docker.com/engine/install/ubuntu), then `usermod -aG docker deploy`.
Docker publishes ports past ufw's rules, which is why only Caddy publishes any.

Turn on the provider's automatic server backups or snapshots as well; they
complement the database dumps below.

## 2. Domain (*server only*)

Point an `A` record (and `AAAA` for IPv6) for your domain at the server. Caddy
obtains and renews the Let's Encrypt certificate itself once ports 80 and 443
reach it.

## 3. First installation (*tested locally*)

As `deploy`:

```sh
git clone https://github.com/Chanaka02/Watchmark.git /srv/watchmark && cd /srv/watchmark
cp deploy/production.env.example deploy/production.env && chmod 600 deploy/production.env
# Edit deploy/production.env: SITE_ADDRESS, two random DB passwords
# (openssl rand -base64 32), TMDB_READ_ACCESS_TOKEN.

TAG=$(git rev-parse --short HEAD)
docker build -t watchmark-backend:$TAG backend
docker build -f deploy/web.Dockerfile -t watchmark-web:$TAG .
sed -i "s/^BACKEND_IMAGE=.*/BACKEND_IMAGE=watchmark-backend:$TAG/; s/^WEB_IMAGE=.*/WEB_IMAGE=watchmark-web:$TAG/" deploy/production.env

docker compose -f deploy/compose.yml --env-file deploy/production.env up -d --no-build
docker compose -f deploy/compose.yml --env-file deploy/production.env ps
```

Flyway creates the schema on the first start. Check:

```sh
curl -sI https://your.domain/ | grep -iE 'strict-transport|content-security'
curl -s https://your.domain/api/v1/catalog/source      # {"provider":"tmdb","mock":false}
```

With `METADATA_PROVIDER=tmdb`, a missing or wrong token stops the backend at
startup instead of silently showing fictional shows.

## 4. Backups (*tested locally*)

Nightly, from the deploy user's crontab (`crontab -e`):

```
15 2 * * * cd /srv/watchmark && deploy/backup.sh >> backups/backup.log 2>&1
```

`backup.sh` writes `backups/watchmark-<UTC time>.sql.gz` (a consistent
`mysqldump`), checks that the dump is complete before keeping it, and deletes
dumps older than 14 days (`KEEP_DAYS`).

**Copy the dumps off the server.** A backup on the machine it protects is lost
with that machine. Any storage you control works, for example a provider
storage box via `rsync` or object storage via `rclone`, added as a second cron
line after the backup. This is a paid service you choose; it is not set up here.

## 5. Restore (*tested locally: backup, wipe, restore, verified*)

```sh
deploy/restore.sh backups/watchmark-20261106T020000Z.sql.gz
```

It asks you to type the database name (it replaces **all** data), stops the
backend, loads the dump and starts the backend again. Rehearse it every few
months, ideally on a throwaway copy of the stack; an untested backup is a hope,
not a backup.

The local drill: a user with 8 viewings, 400 minutes and a rating of 9, then
backup, then `docker compose down -v` (database deleted, sign-in fails), then
restore. Sign-in worked again and every number matched.

## 6. Updating (*tested locally*)

```sh
cd /srv/watchmark
deploy/backup.sh                                  # always, before an update
git pull
TAG=$(git rev-parse --short HEAD)
docker build -t watchmark-backend:$TAG backend
docker build -f deploy/web.Dockerfile -t watchmark-web:$TAG .
sed -i "s/^BACKEND_IMAGE=.*/BACKEND_IMAGE=watchmark-backend:$TAG/; s/^WEB_IMAGE=.*/WEB_IMAGE=watchmark-web:$TAG/" deploy/production.env
docker compose -f deploy/compose.yml --env-file deploy/production.env up -d --no-build
```

Flyway applies new migrations when the backend starts. Old images stay on the
server under their tags, which is what makes rollback possible; clear out old
ones now and then with `docker image ls watchmark-*` and `docker image rm`.

## 7. Rollback (*tested locally*)

Set the previous tags in `deploy/production.env` (or pass them inline) and
restart:

```sh
BACKEND_IMAGE=watchmark-backend:<previous> WEB_IMAGE=watchmark-web:<previous> \
  docker compose -f deploy/compose.yml --env-file deploy/production.env up -d --no-build
```

**If the release you are leaving added a database migration**, the older
backend refuses to start (Flyway finds a newer schema than it knows). Then
restore the backup taken before the update (step 5) and roll back as above.
That is why step 6 always starts with a backup.

## 8. Day-to-day operation

- **Logs:** `docker compose -f deploy/compose.yml --env-file deploy/production.env logs -f backend web`.
  Caddy's access log contains client IP addresses and paths. Docker's default
  log driver never rotates, so cap it in `/etc/docker/daemon.json`
  (`"log-opts": {"max-size": "10m", "max-file": "5"}`) and restart Docker.
- **Uptime:** point any external uptime checker at
  `https://your.domain/api/v1/catalog/source` (public, cheap, touches the backend).
- **Disk:** keep an eye on `df -h` (MySQL volume, backups, old images).
- **Metadata refresh:** the backend refreshes stored shows nightly and logs an
  ERROR if any show cannot be refreshed within TMDB's six-month limit.

## 9. Secrets

| Secret | Where | Rotating it |
|---|---|---|
| DB passwords | `deploy/production.env` (mode 600) | Change in MySQL (`ALTER USER`), then in the file, then `up -d`. |
| TMDB token | `deploy/production.env` | Replace, then `up -d`. If TMDB access ends, the terms require deleting TMDB content: see [tmdb.md](tmdb.md). |
| TLS keys | Caddy volume `caddy-data` | Automatic. |

Never commit `deploy/production.env`; `.gitignore` and `.dockerignore` both
exclude it, and the built images were checked to contain no `.env` files or
passwords.

## 10. Production checklist

- [x] HTTPS everywhere with HSTS (Caddy).
- [x] `SESSION_COOKIE_SECURE=true` (set in `compose.yml`; verified `Secure` cookies).
- [x] Real client IPs for rate limits (`SERVER_FORWARD_HEADERS_STRATEGY=native`).
- [x] Content-Security-Policy (verified: no violations on any page, Playwright `csp.spec.ts`).
- [x] Database not exposed; Actuator not exposed; containers run with memory limits.
- [x] Backups with a tested restore; rollback by image tag.
- [ ] *Server only:* SSH keys only, firewall, unattended upgrades, provider backups, off-server dump copies.
