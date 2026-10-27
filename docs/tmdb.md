# TMDB usage

Checked against TMDB's live terms of use and developer docs on 2026-10-27.
Re-check before any public launch or change of business model.

## Licence position

- Watchmark is a **personal, non-commercial** project. TMDB's free licence
  does not permit commercial use; commercial use requires a separate written
  agreement with TMDB.
- Treated as commercial (so **not allowed** without an agreement): charging
  users, ads or other revenue derived from TMDB data, and using TMDB content to
  train ML/AI systems.
- **Open question before public launch:** the terms also classify use "in
  connection with a 'destination' website, search engine, or interactive
  query-response system" as commercial. Get written confirmation from TMDB
  before opening Watchmark to the public.
- The metadata provider is abstracted (`MetadataProvider`), so switching
  providers or moving to a commercial agreement does not affect user data.

## Implementation status (Milestone 2)

- `TmdbMetadataProvider` calls `/trending/tv/week`, `/tv/popular`,
  `/tv/top_rated`, `/tv/on_the_air`, `/search/tv` (`include_adult=false`),
  `/tv/{id}?append_to_response=credits` and `/tv/{id}/season/{n}`, with
  `language=en-US`.
- Every response field is treated as optional (TMDB's schema marks nothing as
  nullable). Empty strings and unparsable dates become `null`; entries without
  ids or episode numbers are dropped; adult titles are excluded.
- Failures: `404` becomes show/season not found. `429` (honouring
  `Retry-After`), `5xx`, a rejected token, timeouts (3 s connect, 5 s read) and
  unreadable JSON become `503 metadata-unavailable`. Upstream details are
  logged, never returned.
- Outgoing requests are paced to 20 per second (`RequestPacer`).
- Responses are cached in memory: lists 1 h, search 15 min, shows and seasons
  6 h. Failures are not cached.
- **Tests use hand-written fixtures shaped on the documented schema**
  (`backend/src/test/resources/tmdb/`), not recordings of the live API. Once a
  token is available, verify against real responses and add recorded fixtures.
- Image sizes (`w342`, `w1280`, `w185`, `w300`) are configuration values;
  confirm them against `/configuration` when a token is available.

## Obligations and how Watchmark meets them

| Requirement | Implementation (Milestone 2–3) |
|---|---|
| Use the TMDB logo, less prominent than Watchmark's own branding | Footer on every page while TMDB is the active source, using TMDB's official logo (`frontend/public/tmdb-logo.svg`, 12 px high). |
| Notice: "This product uses the TMDB API but is not endorsed or certified by TMDB." | Footer, verbatim, while TMDB is the active source (not shown for mock data). An About page is planned. |
| Do not cache TMDB content for longer than 6 months | In-memory caches expire within 6 hours (Milestone 2). From Milestone 3, stored rows carry `metadata_synced_at` and a refresh job keeps them under 5 months old. |
| Purge all TMDB content if access ends | Metadata tables are separate from personal data and can be truncated. |
| Do not use TMDB as an image host | Store image paths only; images load from TMDB's CDN. |
| Rate limits (~40 req/s upper bound, may change; honour 429) | Client-side limit of ~20 req/s, `Retry-After` handling, caching. |

## Getting a token (do this yourself; never paste it in chat or commit it)

1. Sign in at themoviedb.org.
2. **Account Settings → API** (desktop browser recommended).
3. Apply for a developer key for personal, non-commercial use and accept the terms.
4. Copy the **API Read Access Token** shown on that page.
5. Put it in your local `.env`: `TMDB_READ_ACCESS_TOKEN=…`

Requests send it as `Authorization: Bearer <token>`, from the backend only.
Without a token the backend uses the mock provider (fictional data clearly
labelled as mock in the UI).
