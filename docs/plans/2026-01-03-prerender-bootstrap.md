# Prerender Bootstrap on First Use

**Goal:** If the server-side `config/ae2web/prerender/` cache is empty (fresh install), proactively render a complete set
of icons/translations instead of relying on the web UI scrolling to gradually surface missing icons.

## Detection

Client checks `GET /api/prerender/stats` on first poll after login.

If `renderedTotalCount == 0`, treat this as a first-use bootstrap.

## Behavior

During bootstrap:

- Client fetches missing icons with `scope=all` (active inventory + craftables + registry base icons).
- Uses a bounded request size (`limit=2000`) to avoid large JSON payloads/timeouts.
- Processes claim + render in batches (claim batching already caps to avoid HTTP 413).
- Repeats quickly (`1s` poll delay) until the server reports no more missing icons for `scope=all`.

After bootstrap completes, client falls back to normal steady-state polling (`scope=active`).

## Notes

- Bootstrap only happens once per client session.
- Steady-state remains event-driven by AE2/ME activity: new NBT variants continue to be discovered via `scope=active`.
