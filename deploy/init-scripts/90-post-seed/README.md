# 90-post-seed/

Scripts that **must** run on every fresh DB init **after all seed packages have
loaded**. They do not create schema, do not insert seed data, and are not
tied to a single Function Unit — they only reconcile state introduced by the
seed step (e.g. pushing BIGSERIAL sequences past explicit-id seed rows).

## Why this directory exists

`deploy/init-scripts/` previously had three implicit stages:

1. **`00-schema/`** — DDL: `CREATE TABLE` / `ALTER TABLE` / constraints.
2. **`01-admin/`, `08-/15-/16-/17-/18-`** — seed data (users / roles, demo
   Function Units).
3. **`99-maintenance/`** — on-demand repair scripts (Flowable repair, wipe FU,
   widen legacy columns), called manually except where `00-init-all.sh`
   explicitly invokes one (e.g. `00-wipe-all-function-units.sql`).

There was no clean home for scripts that **must run every init** but **must
also run AFTER the seed step**:

- `00-schema/` is wrong: not DDL, and the schema wildcard loop runs BEFORE
  seed when tables are still empty (setval would no-op).
- `99-maintenance/` is wrong: by name and convention these are on-demand
  repair scripts.
- `08-/15-/16-/17-/18-` is wrong: cross-cutting, not tied to one FU.

`90-post-seed/` fills the gap.

## Naming & ordering

- Directory prefix `90-` simply means "comes after the `18-` Function Unit
  seed packages in lexical sort"; the real execution order is enforced by
  the explicit `$PSQL -f ...` calls in `00-init-all.sh`, not by directory
  name.
- File prefix inside this directory uses two-digit `00-`, `01-`, ... so
  future post-seed alignment scripts have a natural ordering.

## Adding a new script here

1. Drop it in this directory with a `NN-<descriptive-name>.sql` filename.
2. Register it in `deploy/init-scripts/00-init-all.sh` in the appropriate
   sub-step (currently `[5f/6]`). The runner is a **whitelist loader**, not
   a `*.sql` wildcard — unregistered files will silently be ignored.
3. Keep the script idempotent (safe to re-run).
4. Avoid public-schema DDL here. DDL belongs in `00-schema/`.
