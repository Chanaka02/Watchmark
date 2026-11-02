# Tracking rules

These rules are the specification for Milestones 4–5. Each rule lists the tests
that must exist and pass; per the agreed process those tests are written
**before** the implementation in that milestone.

| Rule | Status |
|---|---|
| R1, R10, R11 | Implemented: library (Milestone 4), watch history and operations (Milestone 5) |
| R2–R9, R12 | Implemented (Milestone 5): `ShowTimelineTests`, `TrackingApiTests`, `ProgressAndStatusTests`, `TrackingRetentionJobTests` |
| R2 statistics, R10 stats | User-wide totals exist (`WatchHistory.totals`); the statistics API follows in Milestone 8 |

Terms:

- **Regular episode:** season number ≥ 1. **Special:** season 0.
- **Cycle:** a full watch-through of a show by a user. 1 = first watch, 2+ = rewatches.
- **Active watch:** an `episode_watches` row with `deleted_at IS NULL`.
- **Today:** the current date in the user's configured IANA time zone.

## R1 — Isolation

Each user has an independent library and watch history. The user is always
taken from the session, never from a request parameter. Requests for another
user's resources return `404 Not Found` (not `403`), so existence is not leaked.

*Tests:* user B cannot read, change, undo or purge user A's library entries,
watches or operations; unauthenticated requests get `401`.

## R2 — Unique episodes vs. total watches

Each viewing is one `episode_watches` row tied to a cycle.

- **Unique episodes watched** = count of distinct `episode_id` among active watches.
- **Total watches** = count of active watches (a rewatch counts again).
- An episode can be watched at most once per cycle (enforced by the database).
  Rewatching a single episode outside a full rewatch is out of MVP scope.

*Tests:* a show watched once then rewatched gives `unique = N`, `total = 2N`;
watch rows from removed shows are counted (R10); soft-deleted rows are not.

## R3 — Reversibility and undo

Watch, unwatch, mark-season and watch-up-to each create one
`tracking_operations` row. Unwatching soft-deletes (`deleted_at`,
`deleted_by_op`) rather than deleting.

`POST /tracking/operations/{id}/undo`:

| Operation | Undo effect |
|---|---|
| WATCH / SEASON / UP_TO | Soft-delete the rows created by this operation that are still active. |
| UNWATCH | Restore (clear `deleted_at`) the rows this operation deleted, keeping their original `watched_at`. |

- Allowed only within `watchmark.tracking.undo-window` (default **24h**) of the
  operation's `created_at`; afterwards `409 Conflict` (`undo-window-expired`).
- An operation can be undone once; a second attempt returns `409`.
- Restoring a row whose episode has been re-watched since in the same cycle
  returns `409` (`conflicting-watch`) and changes nothing (all-or-nothing).
- Undo is an ordinary transaction; it does not create a new operation.

*Tests:* undo of each operation type; undo at 23h59m succeeds and at 24h00m01s
fails (with an injected `Clock`); double undo; conflicting restore; bulk undo
reverts exactly the created rows and leaves previously watched ones intact.

## R4 — No duplicate progress

The unique key `(user_id, episode_id, cycle, active_marker)` guarantees at most
one active watch per episode per cycle. Marking an already-watched episode is
idempotent (`200`, no new row, no new operation).

*Tests:* repeated `PUT` creates one row; two concurrent `PUT`s create one row;
bulk operations skip already-watched episodes.

## R5 — Eligibility

An episode may be marked watched when:

1. its `air_date` is on or before today, **or**
2. its `air_date` is unknown **and** a later regular episode of the same show
   has aired (it evidently aired; shown as "date unknown", never with an
   invented date).

Otherwise `422` (`episode-not-aired`).

*Tests:* aired today in the user's zone (e.g. `Pacific/Auckland` vs
`America/Los_Angeles` on the same instant); future date; unknown date with and
without a later aired episode; specials follow the same rule.

## R6 — Progress

- Specials are trackable but excluded from percentages.
- Denominator: regular episodes that are eligible **or** already watched in the
  current cycle (and not `upstream_missing`).
- Numerator: regular episodes actively watched in the current cycle.
- If a watched episode's release is later postponed, the watch is kept and
  progress does not decrease.

*Tests:* show with specials; postponed episode; missing-upstream episode;
empty show (0/0 is reported as 0%, not an error).

## R7 — Season states

| State | Condition |
|---|---|
| COMPLETE | Every regular episode is watched and none are future/unknown. |
| CAUGHT_UP | Every eligible episode is watched; future or unknown episodes remain. |
| IN_PROGRESS | At least one, but not all, eligible episodes watched. |
| NOT_STARTED | None watched. |

Mark-season affects only eligible, unwatched episodes, in one transaction, and
returns `created`, `alreadyWatched` and `notEligible` counts.

*Tests:* each state; mark-season on a partly aired season; on season 0.

## R8 — Episodes removed upstream

If a refresh no longer finds an episode at the provider, it sets
`upstream_missing_at` instead of deleting it. Such episodes are excluded from
progress denominators but their watch rows remain in history and statistics.
If the episode reappears, the flag is cleared.

*Tests:* refresh removes a watched episode → row kept, progress recalculated,
stats unchanged; refresh never alters `user_shows` or `episode_watches`.

## R9 — Status automation and rewatch

- Tracking an episode of a show not in the library adds it as `WATCHING`
  (or restores a removed entry, R10).
- When the show reaches COMPLETE for the current cycle and the provider status
  is ended/canceled, status becomes `COMPLETED`. A returning series stays
  `WATCHING` and is shown as caught up.
- `ON_HOLD` and `DROPPED` are never changed automatically.
- Rewatch increments `current_cycle` and sets `WATCHING`; earlier cycles are
  untouched.

## R10 — Removing a show preserves history

`DELETE /library/{showRef}` sets `user_shows.removed_at`. The show disappears
from library, dashboard and calendar.

- Watch history, operations, rating and notes are **kept**.
- Viewing totals (R2, hours, genres, activity) **still include** its history.
  Library counts (watching, completed series) exclude removed entries.
- Re-adding or tracking the show clears `removed_at` and restores cycle,
  progress, rating and notes.

*Tests:* remove → stats unchanged, library excludes show; re-add → progress
identical to before removal.

*Implemented (Milestone 4):* `LibraryApiTests.removingKeepsTheEntryDataAndReAddingRestoresIt` (rating, notes, cycle and original added date restored) and `MetadataRefreshJobTests.showsInARemovedLibraryEntryAreStillReferenced` (stored metadata is kept). The stats and progress parts are added with Milestones 5 and 8.

## R11 — Explicit permanent deletion

`POST /library/{showRef}/purge` with the exact show name as confirmation
hard-deletes, for that user and show: the `user_shows` row, all
`episode_watches` rows (active and soft-deleted) and the
`tracking_operations` that only reference those rows. It is irreversible and
not undoable. Account deletion purges everything for the user.

*Tests:* wrong confirmation → `422`, nothing deleted; purge removes only that
user's data for that show; other shows and users unaffected.

*Implemented (Milestone 4):* the confirmation compares the stored show name
ignoring case and repeated whitespace. Purge works on removed entries too;
re-adding afterwards starts a fresh entry. Watch rows and operations are added
to the purge in Milestone 5. Stored metadata no user references any more is
deleted by the daily refresh job (after a one-hour grace period).

## R12 — Retention of soft-deleted rows

A scheduled job permanently deletes soft-deleted watch rows.

**Records affected** — only these:

| Record | Purged when |
|---|---|
| `episode_watches` with `deleted_at IS NOT NULL` | `deleted_at` is older than `soft-delete-retention` (default **30 days**). |
| `tracking_operations` | older than `soft-delete-retention` **and** no `episode_watches` row references it any more. |

**Never affected by the purge job:** active watches, removed library entries
(`user_shows.removed_at`), ratings, notes, users, and metadata.

**Conditions** ("deletion policy permits it"):

- `watchmark.tracking.purge-enabled` is `true` (default). When `false`, nothing
  is purged.
- A row that was restored (by undo) has `deleted_at = NULL` again and is
  therefore never eligible.
- `soft-delete-retention` must be ≥ `undo-window`; the application refuses to
  start otherwise, so an undoable operation can never lose its rows.

The job runs daily at 04:00 UTC (`watchmark.tracking.purge-cron`, env
`TRACKING_PURGE_CRON`; `-` disables it).

All three values are configuration properties (`application.yml` or
environment variables such as `WATCHMARK_TRACKING_SOFTDELETERETENTION=60d` —
Spring's relaxed binding drops the dashes in environment variable names);
changing them needs no code change.

*Tests:* rows deleted 29d23h ago kept, 30d00h01s ago purged (injected `Clock`);
restored rows never purged; `purge-enabled=false` purges nothing; operations
purged only after their rows; configuration validation (already implemented).

## Clarifications decided in Milestone 5

These fill gaps in the rules above; the tests in `backend/src/test/java/app/watchmark/tracking`
cover each one.

**Eligibility (R5)**

- An upstream-missing episode (R8) cannot be newly watched: `422 episode-unavailable`.
  Existing watches of it are kept.
- "A later regular episode has aired" means a regular episode with a higher
  (season, episode) position whose air date is on or before today. For a
  **special** with an unknown date, the evidence is a later *special* (higher
  episode number in season 0) that has aired, because specials have no
  position relative to regular episodes.

**Progress and states (R6, R7)**

- Per season and for the whole show, using the current cycle and only episodes
  that are not upstream-missing: `total` = all episodes, `eligible` = episodes
  that are eligible **or** already watched, `watched` = watched episodes.
- State: `NOT_STARTED` when `watched = 0`; otherwise `COMPLETE` when
  `watched = total`, `CAUGHT_UP` when `watched = eligible`, else `IN_PROGRESS`.
  The show state uses regular episodes only; specials get a state per season but
  never affect the show's percentage or state.
- **Next episode:** the first eligible, unwatched regular episode after the
  furthest watched one (or the first eligible one if nothing is watched). `null`
  when caught up. Skipped earlier episodes stay visible as unwatched in their season.

**Bulk actions (R7)**

- Mark-season and watch-up-to skip episodes that are already watched
  (`alreadyWatched`), not yet eligible (`notEligible`) or upstream-missing (not counted).
- Watch-up-to on a regular episode covers regular episodes up to and including it
  (all earlier seasons). On a special it covers specials up to it.
- If nothing is created, no operation is recorded (`operationId: null`).

**Unwatch**

- Unwatch affects only the current cycle. Unwatching an episode that is not
  watched in the current cycle is a no-op (`200`, `removed: 0`, `operationId: null`).
- Unwatch never adds or restores a library entry.

**Undo (R3)**

- Undoing a WATCH, SEASON or UP_TO operation soft-deletes its rows with
  `deleted_by_op = NULL` (the undo is not an operation). Undoing an UNWATCH
  restores its rows and clears both `deleted_at` and `deleted_by_op`.
- Unknown operations and other users' operations: `404 operation-not-found`.
  Already undone: `409 already-undone`. The window check is inclusive: an
  operation exactly `undo-window` old can still be undone.

**Status automation (R9)**

- Watching (single, season, up-to) adds the show as `WATCHING` when it is not in
  the library, restores a removed entry (R10, keeping its status), and moves
  `PLAN_TO_WATCH` to `WATCHING`.
- After every tracking change, including unwatch and undo, on an entry that is
  in the library: `WATCHING` or `PLAN_TO_WATCH` becomes `COMPLETED` when the
  show state is `COMPLETE` and the provider status is ended or canceled; a
  `COMPLETED` entry whose current cycle is no longer complete returns to
  `WATCHING`. `ON_HOLD` and `DROPPED` are never changed automatically.
- Metadata refreshes never change library entries (R8), so a finished show that
  gains episodes is re-evaluated on the next tracking change.

**Rewatch**

- `POST /library/{showRef}/rewatch` starts cycle `n + 1` and sets `WATCHING`
  (even from `ON_HOLD` or `DROPPED`, because the user asked for it). It requires
  at least one watch in the current cycle (`409 nothing-to-rewatch`), so empty
  cycles cannot pile up. Rewatch is not an operation and cannot be undone.
- Operations from an earlier cycle can still be undone within the window; they
  only touch their own rows.

**Purge (R11) and account deletion**

- Each operation stores its show, so purging a show deletes the user's watches
  for its episodes, then that user's operations for that show, then the library
  entry, in one transaction.
- Account deletion deletes the user's watches and operations explicitly before
  the user row (the watch → operation foreign key is RESTRICT, so the order is
  not left to cascade ordering).
