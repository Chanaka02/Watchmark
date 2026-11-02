# API contract (v1)

Status: health, authentication/profile (Milestone 1), catalog (Milestone 2)
library (Milestone 4) and tracking/progress (Milestone 5) endpoints are
**implemented**; the rest is design. The generated OpenAPI
document is served at `/v3/api-docs` when `API_DOCS_ENABLED=true`.

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
| `GET /catalog/source` | Active data source `{provider, mock}`; public | 2 ✅ |
| `GET /catalog/discover/{list}?page=` | `trending`, `popular`, `top-rated`, `on-the-air`; `page` 1–500 | 2 ✅ |
| `GET /catalog/search?q=&page=` | Search shows; `q` 1–100 characters; 60 per minute per user | 2 ✅ |
| `GET /catalog/shows/{showRef}` | Show details incl. seasons summary, genres, cast | 2 ✅ |
| `GET /catalog/shows/{showRef}/seasons/{n}` | Season with episodes (0 = specials) | 2 ✅ |
| `GET /library?status=&sort=` | My library (excludes removed); `sort` = `RECENT` (default), `TITLE`, `ADDED`; includes counts per status | 4 ✅ |
| `GET /library/{showRef}` | My entry for one show; `404 not-in-library` when absent or removed | 4 ✅ |
| `PUT /library/{showRef}` | `{status}`: add (`201`), change or restore a removed entry (`200`) | 4 ✅ |
| `PUT /library/{showRef}/rating` | `{rating: 1–10 \| null}` | 4 ✅ |
| `PUT /library/{showRef}/notes` | `{notes: string \| null}`, at most 2000 characters; blank clears | 4 ✅ |
| `DELETE /library/{showRef}` | Remove, keep history, rating and notes (R10) (`204`) | 4 ✅ |
| `POST /library/{showRef}/purge` | `{confirmName}`: permanently delete my data for the show (R11) (`204`) | 4 ✅ |
| `POST /library/{showRef}/rewatch` | Start the next cycle; needs a watch in the current one (`409 nothing-to-rewatch`); not undoable | 5 ✅ |
| `GET /library/{showRef}/progress` | Overall and per-season progress, next episode, history totals | 5 ✅ |
| `GET /library/{showRef}/progress/seasons/{n}` | One season's stored episodes with id, eligibility and watched state | 5 ✅ |
| `PUT /tracking/episodes/{episodeId}/watch` | Mark watched (idempotent); adds or restores the library entry (R9) | 5 ✅ |
| `DELETE /tracking/episodes/{episodeId}/watch` | Mark unwatched in the current cycle (soft delete); no-op when not watched | 5 ✅ |
| `POST /tracking/shows/{showRef}/seasons/{n}/watch` | Mark the season's eligible episodes (0 = specials) | 5 ✅ |
| `POST /tracking/episodes/{episodeId}/watch-up-to` | Mark every eligible episode up to and including this one | 5 ✅ |
| `POST /tracking/operations/{operationId}/undo` | Undo an operation within the undo window (R3) | 5 ✅ |
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

### Catalog

Fields documented as nullable in `CatalogDtos` really are `null` when the
provider has no value; nothing is invented. Images are absolute URLs or
`null`. Air dates are calendar dates without a time.

```http
GET /api/v1/catalog/discover/popular?page=2

200 OK
{ "items": [ { "ref": "tmdb:90001", "name": "Fixture Harbour", "firstAirDate": "2021-03-14",
               "overview": "...", "posterUrl": "https://image.tmdb.org/t/p/w342/....jpg",
               "voteAverage": 8.1 } ],
  "page": 2, "totalPages": 500, "totalResults": 182390 }
```

```http
GET /api/v1/catalog/shows/tmdb:90001/seasons/1

200 OK
{ "showRef": "tmdb:90001", "seasonNumber": 1, "name": "Series 1", "overview": null,
  "airDate": "2021-03-14", "posterUrl": "...",
  "episodes": [ { "externalId": "700101", "seasonNumber": 1, "episodeNumber": 1,
                  "name": "Arrival", "overview": "...", "airDate": "2021-03-14",
                  "runtimeMinutes": 58, "stillUrl": "..." },
                { "externalId": "700103", "seasonNumber": 1, "episodeNumber": 3,
                  "name": "Episode 3", "overview": null, "airDate": null,
                  "runtimeMinutes": null, "stillUrl": null } ] }
```

Provider failure:

```http
503 Service Unavailable
Retry-After: 30
{ "type": "/problems/metadata-unavailable", "title": "Show data unavailable", "status": 503,
  "detail": "Show information is temporarily unavailable. Please try again shortly." }
```

Other catalog problems: `404 /problems/show-not-found` (also for malformed or
other-provider refs), `404 /problems/season-not-found`, and `400
/problems/validation-failed` (bad `page`, `q` or season number).

### Library

Rating and notes have their own endpoints so "clear" (`null`) is never
confused with "leave unchanged". Adding a show stores its metadata first if no
user has added it before; a provider outage then returns `503` and nothing is
created.

```http
PUT /api/v1/library/tmdb:1399
X-XSRF-TOKEN: ...
{ "status": "WATCHING" }

201 Created
{ "show": { "ref": "tmdb:1399", "name": "...", "posterUrl": "...", "firstAirDate": "2011-04-17",
            "status": "ENDED" },
  "status": "WATCHING", "rating": null, "notes": null, "cycle": 1,
  "addedAt": "2026-11-02T10:00:00Z", "updatedAt": "2026-11-02T10:00:00Z" }
```

```http
GET /api/v1/library?status=WATCHING&sort=TITLE

200 OK
{ "entries": [ { ...same shape as above... } ],
  "counts": { "PLAN_TO_WATCH": 0, "WATCHING": 2, "COMPLETED": 1, "ON_HOLD": 0, "DROPPED": 0 } }
```

```http
POST /api/v1/library/tmdb:1399/purge
{ "confirmName": "Wrong name" }

422 Unprocessable Content
{ "type": "/problems/confirmation-mismatch", "title": "Confirmation does not match", "status": 422,
  "detail": "Type the show's name exactly as shown to confirm." }
```

Other library problems: `404 /problems/not-in-library`, `404
/problems/show-not-found`, `409 /problems/concurrent-update` (the entry changed
at the same time elsewhere), `400 /problems/validation-failed`.

### Mark an episode watched

Episode ids are Watchmark's stored ids, listed by
`GET /library/{showRef}/progress/seasons/{n}` (catalog pages use the provider's
`externalId`; the two are matched by it).

```http
PUT /api/v1/tracking/episodes/5521/watch
X-XSRF-TOKEN: ...

200 OK
{ "operationId": "019a4c1e-7b2d-7c3e-9f00-5b1c2d3e4f50", "showRef": "tmdb:1399",
  "created": 1, "alreadyWatched": 0, "notEligible": 0 }
```

Already watched in the current cycle: `200` with `"created": 0`,
`"alreadyWatched": 1` and `"operationId": null` (nothing to undo).
Not aired yet in the user's time zone: `422 /problems/episode-not-aired`. No
longer listed by the provider: `422 /problems/episode-unavailable`. Unknown id:
`404 /problems/episode-not-found`.

```http
DELETE /api/v1/tracking/episodes/5521/watch

200 OK
{ "operationId": "019a4c20-…", "showRef": "tmdb:1399", "removed": 1 }
```

### Mark a season, then undo

```http
POST /api/v1/tracking/shows/tmdb:1399/seasons/1/watch

200 OK
{ "operationId": "019a4c22-…", "showRef": "tmdb:1399", "created": 8, "alreadyWatched": 2, "notEligible": 0 }

POST /api/v1/tracking/operations/019a4c22-…/undo

200 OK
{ "operationId": "019a4c22-…", "type": "SEASON", "showRef": "tmdb:1399", "reverted": 8 }
```

`watch-up-to` returns the same shape as a season. Clients refetch progress after
any tracking request.

### Undo problems

```http
409 Conflict
Content-Type: application/problem+json
{ "type": "/problems/undo-window-expired", "title": "Undo window expired",
  "status": 409, "detail": "Tracking actions can be undone for 24 hours." }
```

Also `409 /problems/already-undone`, `409 /problems/conflicting-watch` (the
episode was marked watched again since the unwatch; nothing changes) and
`404 /problems/operation-not-found` (unknown, malformed or another user's).

### Progress

```http
GET /api/v1/library/tmdb:1399/progress

200 OK
{ "showRef": "tmdb:1399", "cycle": 1, "status": "WATCHING", "canRewatch": true,
  "overall": { "watched": 23, "eligible": 73, "total": 73, "percent": 31.5, "state": "IN_PROGRESS" },
  "history": { "uniqueEpisodes": 24, "totalWatches": 24 },
  "seasons": [
    { "number": 0, "special": true, "watched": 1, "eligible": 4, "total": 6, "percent": 25.0, "state": "IN_PROGRESS" },
    { "number": 1, "special": false, "watched": 10, "eligible": 10, "total": 10, "percent": 100.0, "state": "COMPLETE" },
    { "number": 3, "special": false, "watched": 3, "eligible": 10, "total": 10, "percent": 30.0, "state": "IN_PROGRESS" } ],
  "nextEpisode": { "id": 5521, "season": 3, "episode": 4, "name": "…", "airDate": "2013-04-21" } }
```

`overall` covers regular episodes of the current cycle; `history` covers every
cycle and episodes no longer listed upstream. `404 /problems/not-in-library`
when the show is not (or no longer) in the library.

```http
GET /api/v1/library/tmdb:1399/progress/seasons/2

200 OK
{ "showRef": "tmdb:1399", "cycle": 1, "number": 2, "special": false,
  "watched": 1, "eligible": 2, "total": 3, "percent": 50.0, "state": "IN_PROGRESS",
  "episodes": [
    { "id": 5530, "externalId": "63056", "episode": 1, "name": "…", "airDate": "2012-04-01",
      "eligible": true, "watched": true, "watchedAt": "2026-11-02T10:00:00Z", "upstreamMissing": false } ] }
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
