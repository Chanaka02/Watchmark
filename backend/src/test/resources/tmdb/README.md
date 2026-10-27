# TMDB test fixtures

These JSON files are **hand-written**, shaped after the response schemas in
TMDB's API reference (developer.themoviedb.org, checked 2026-10-28). They are
**not** recorded from the live API, and the shows, people and ids in them are
invented.

They intentionally include the irregularities the mapper must survive: empty
date strings, `null` images, missing ids, adult entries, unsorted seasons and
episodes, empty `episode_run_time`, zero runtimes and `null`
`next_episode_to_air`.

Once a TMDB token is available, add recorded responses alongside these (with
the token stripped) to confirm the real API still matches.
