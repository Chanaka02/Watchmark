# Roadmap

One milestone at a time; each ends with passing tests and a review before the
next starts.

| # | Milestone | Done when | Status |
|---|---|---|---|
| 0 | **Skeleton**: repo, Spring Boot + Vite, MySQL in Compose, Flyway baseline, health check, retention config, CI, docs | Local stack runs end to end; backend and frontend tests pass | ✅ done |
| 1 | **Authentication**: users, register/login/logout, Spring Session JDBC, CSRF, secure cookies, auth rate limits, problem+json errors, login/register pages | Auth + authorization failure tests pass (401/404 isolation) | ✅ done |
| 2 | **Metadata**: `MetadataProvider`, mock provider + fixtures, TMDB client (timeouts, 429), discover/search/details API and pages, attribution | Works with mock data; TMDB failure and missing-field tests pass | |
| 3 | **Metadata persistence**: store/refresh shows, seasons, episodes; 6-month cache enforcement; upstream-missing episodes (R8) | Refresh never changes user data (tested) | |
| 4 | **Library**: status, rating, notes; remove keeps history (R10); explicit purge (R11) | Rule tests R1, R10, R11 pass | |
| 5 | **Episode tracking**: watch/unwatch, season, up-to, undo (R3), eligibility, progress, season states, rewatch, retention purge job (R12), tracker UI | Rule tests R2–R9, R12 pass | |
| 6 | **Dashboard and history** | Shows only real data | |
| 7 | **Calendar** (list + month, time-zone aware) | Unknown dates shown separately, none invented | |
| 8 | **Statistics** (unique vs total, hours, genres, activity) | Verified against fixtures | |
| 9 | **Hardening**: accessibility, Playwright end-to-end tests, dependency scanning, error pages | Register → track episode end-to-end test passes | |
| 10 | **Deployment**: compare managed platform vs VPS + Compose + Caddy (cost, security, backups, maintenance), then deploy after approval | Documented deploy, tested backup restore, rollback guide | |

Carried forward: a profile/settings **page** (the profile, password and
account-deletion APIs exist since Milestone 1) is built with the dashboard in
Milestone 6.

Future (post-MVP): friends, activity feed, reviews, reactions, spoiler-safe
discussions, recommendations, notifications, import/export, light theme.
