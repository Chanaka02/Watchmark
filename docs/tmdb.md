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

## Obligations and how Watchmark meets them

| Requirement | Implementation (Milestone 2–3) |
|---|---|
| Use the TMDB logo, less prominent than Watchmark's own branding | Footer and About page, using TMDB's official logo asset. |
| Notice: "This product uses the TMDB API but is not endorsed or certified by TMDB." | Footer and About page, verbatim. |
| Do not cache TMDB content for longer than 6 months | `metadata_synced_at` on every metadata row; refresh job keeps rows under 5 months old and removes untracked stale rows; monitored. |
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
