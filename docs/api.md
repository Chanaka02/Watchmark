# API contract (v1)

Status: health and all authentication/profile endpoints (Milestone 1) are
**implemented**; the rest is design. OpenAPI publication is planned with
Milestone 2.

Conventions:

- Base path `/api/v1`, JSON, same origin as the SPA.
- Authentication: `WMSESSION` session cookie. State-changing requests must send
  the `XSRF-TOKEN` cookie value in an `X-XSRF-TOKEN` header (see
  [security.md](security.md)).
- The current user always comes from the session. Routes under `/me`,
  `/library` and `/tracking` never take a user id.
- `showRef` = provider-qualified id, e.g. `tmdb:1399`.
- Errors: RFC 9457 `application/problem+json` with a stable `type` slug.
- Timestamps: ISO-8601 UTC instants (`2026-10-27T18:04:00Z`). Air dates: plain
  dates (`2026-11-02`).

| Status | Meaning |
|---|---|
| 400 | Malformed request / validation failure (with `errors[]`) |
| 401 | Not authenticated, or invalid credentials on login |
| 403 | CSRF token missing/invalid; password confirmation incorrect |
| 404 | Not found **or** belongs to another user |
| 409 | Conflict (undo expired, already undone, conflicting watch) |
| 422 | Business rule rejected (episode not aired, wrong purge confirmation) |
| 429 | Rate limited (auth, search) |
| 502/503 | Metadata provider failed / unavailable |

## Endpoints

| Method & path | Purpose | M |
|---|---|---|
| `GET /actuator/health` | Liveness (status only) | 0 ✅ |
| `GET /auth/csrf` | Set the `XSRF-TOKEN` cookie (`204`) | 1 ✅ |
| `POST /auth/register` | Create account and sign in (`201`) | 1 ✅ |
| `POST /auth/login` | Start session | 1 ✅ |
| `POST /auth/logout` | End session (`204`) | 1 ✅ |
| `GET /auth/me` | Current user or `401` | 1 ✅ |
| `GET /me/profile` · `PATCH /me/profile` | Display name, time zone | 1 ✅ |
| `PUT /me/password` | Change password; signs out other sessions (`204`) | 1 ✅ |
| `DELETE /me` | Delete account and all personal data; body `{"password"}` (`204`) | 1 ✅ |
| `GET /catalog/discover/{list}?page=` | `trending`, `popular`, `top-rated`, `airing` | 2 |
| `GET /catalog/search?q=&page=` | Search shows | 2 |
| `GET /catalog/shows/{showRef}` | Show details incl. seasons summary, genres, cast | 2 |
| `GET /catalog/shows/{showRef}/seasons/{n}` | Season with episodes | 2 |
| `GET /library?status=&sort=&page=` | My library (excludes removed) | 4 |
| `PUT /library/{showRef}` | Add or update status, rating, notes | 4 |
| `DELETE /library/{showRef}` | Remove, keep history (R10) | 4 |
| `POST /library/{showRef}/purge` | Permanently delete my data for show (R11) | 4 |
| `POST /library/{showRef}/rewatch` | Start a new cycle | 5 |
| `GET /library/{showRef}/progress` | Per-season progress, next episode | 5 |
| `PUT /tracking/episodes/{episodeId}/watch` | Mark watched (idempotent) | 5 |
| `DELETE /tracking/episodes/{episodeId}/watch` | Mark unwatched (soft delete) | 5 |
| `POST /tracking/shows/{showRef}/seasons/{n}/watch` | Mark eligible season episodes | 5 |
| `POST /tracking/episodes/{episodeId}/watch-up-to` | Mark everything up to here | 5 |
| `POST /tracking/operations/{operationId}/undo` | Undo an operation (R3) | 5 |
| `GET /me/history?cursor=` | Viewing history, newest first | 6 |
| `GET /me/dashboard` | Continue watching, recent, upcoming, recently added | 6 |
| `GET /me/calendar?from=&to=` | Upcoming episodes; unknown dates listed separately | 7 |
| `GET /me/stats?range=week\|month\|all` | Statistics (R2) | 8 |

## Examples

### Register

Password: 15+ characters, at most 72 bytes. `timeZone`: IANA region id.

```http
GET /api/v1/auth/csrf
204 No Content
Set-Cookie: XSRF-TOKEN=…; Path=/; SameSite=Lax

POST /api/v1/auth/register
X-XSRF-TOKEN: …
{ "email": "ana@example.com", "password": "correct horse battery staple",
  "displayName": "Ana", "timeZone": "Asia/Colombo" }

201 Created
Set-Cookie: WMSESSION=…; Max-Age=2592000; Path=/; HttpOnly; SameSite=Lax
{ "id": 42, "email": "ana@example.com", "displayName": "Ana", "timeZone": "Asia/Colombo" }
```

Validation failure:

```http
400 Bad Request
Content-Type: application/problem+json
{ "type": "/problems/validation-failed", "title": "Validation failed", "status": 400,
  "detail": "One or more fields are invalid.",
  "errors": [ { "field": "password", "message": "must be at least 15 characters and at most 72 bytes" } ] }
```

Other outcomes: `409 /problems/email-taken`, `429 /problems/rate-limited`
(with `Retry-After`).

### Login

```http
POST /api/v1/auth/login
X-XSRF-TOKEN: …
{ "email": "ana@example.com", "password": "correct horse battery staple" }

200 OK   (new WMSESSION cookie; same body as register)

401 Unauthorized
{ "type": "/problems/invalid-credentials", "title": "Invalid credentials", "status": 401,
  "detail": "The email or password is incorrect." }
```

### Update profile

```http
PATCH /api/v1/me/profile
X-XSRF-TOKEN: …
{ "timeZone": "Europe/London" }          (omitted fields are unchanged)

200 OK
{ "id": 42, "email": "ana@example.com", "displayName": "Ana", "timeZone": "Europe/London" }
```

Password change (`PUT /me/password`, `{currentPassword, newPassword}`) and
account deletion (`DELETE /me`, `{password}`) return `403
/problems/password-incorrect` when the confirmation password is wrong.

### Mark an episode watched

```http
PUT /api/v1/tracking/episodes/5521/watch

200 OK
{ "operationId": "0b7f5c1e-…", "watchId": 9015, "alreadyWatched": false,
  "progress": { "watched": 24, "eligible": 73, "percent": 32.9 },
  "nextEpisode": { "id": 5522, "season": 3, "episode": 5 } }
```

If already watched in the current cycle: `200` with `"alreadyWatched": true`
and `"operationId": null`.

### Mark a season, then undo

```http
POST /api/v1/tracking/shows/tmdb:1399/seasons/1/watch

200 OK
{ "operationId": "6f1c…", "created": 8, "alreadyWatched": 2, "notEligible": 0,
  "season": { "watched": 10, "eligible": 10, "state": "COMPLETE" } }

POST /api/v1/tracking/operations/6f1c…/undo

200 OK
{ "operationId": "6f1c…", "reverted": 8,
  "season": { "watched": 2, "eligible": 10, "state": "IN_PROGRESS" } }
```

### Undo too late

```http
409 Conflict
Content-Type: application/problem+json
{ "type": "/problems/undo-window-expired", "title": "Undo window expired",
  "status": 409, "detail": "Tracking actions can be undone for 24 hours." }
```

### Progress

```http
GET /api/v1/library/tmdb:1399/progress

200 OK
{ "show": { "ref": "tmdb:1399", "name": "…", "status": "WATCHING", "cycle": 1 },
  "overall": { "watched": 23, "eligible": 73, "percent": 31.5 },
  "seasons": [
    { "number": 0, "watched": 1, "eligible": 4, "total": 6, "special": true },
    { "number": 1, "watched": 10, "eligible": 10, "total": 10, "state": "COMPLETE" },
    { "number": 3, "watched": 3, "eligible": 10, "total": 10, "state": "IN_PROGRESS" } ],
  "nextEpisode": { "id": 5521, "season": 3, "episode": 4, "name": "…", "airDate": "2013-04-21" },
  "watchedEpisodeIds": [ … ] }
```

### Statistics

```http
GET /api/v1/me/stats?range=all

200 OK
{ "uniqueEpisodes": 812, "totalWatches": 905,
  "hoursWatched": 611.5, "episodesWithUnknownRuntime": 14,
  "seriesCompleted": 17, "seriesWatching": 6,
  "topGenres": [ { "name": "Drama", "watches": 402 } ],
  "activity": [ { "period": "2026-10", "watches": 38 } ] }
```
