## 17-Multi-Instance-Subtask-Demo

Hand-maintained SQL init for Function Unit `Multi-Instance Subtask Demo` (`fu-20260422-23tfag`).

### Files

| File | Purpose |
|---|---|
| `00-init-kk.sql` | Full idempotent init: cleanup + INSERT snapshot (re-exported from dev DB **2026-07-19**, then reconciled with real dev-DB drift observed **2026-08-20**; folds in the SAVE action, `task_status` / `task_current_node`, `request_id_config`, the Participants list-view PK column fix `id` → `id_idw` on bindings 50064/50066, the "Meeting Remark" ACTION table + FORM_POPUP "Add Remark" action, `dw_foreign_keys` rows, the `Withdraw` action rename/config, MI Assignment container adoption + `recordNote` nodes on several forms, form-table-binding/view-config resync, the new `meeting_room` relation table, and the `test` relation table's version upsert fix — landing on `current_version=6`) |
| `01-add-save-action.sql` | Standalone SAVE action patch (legacy; already in `00-init-kk.sql`) |
| `02-add-subtask-progress-fields.sql` | Standalone patch for `task_status` / `task_current_node` (legacy; already in `00-init-kk.sql`) |
| `03-set-main-table-request-id-config.sql` | Patch `dw_table_definitions.request_id_config` on MAIN (`I` + `id`, separator `_`); included in fresh `00-init-kk.sql` |
| `04-create-meeting-remark-physical-table.sql` | Creates the physical `meeting_remark` table (DDL only) backing the "Add Remark" FORM_POPUP action |

Docker first-time init loads `00-init-kk.sql` and `03-set-main-table-request-id-config.sql` automatically via `00-init-all.sh` (Step 4d). `01`, `02`, and `04` are **not** wired into `00-init-all.sh` — apply them manually (see below) when standing up a DB that needs them.

### Apply manually

```powershell
psql -v ON_ERROR_STOP=1 -f "deploy/init-scripts/17-Multi-Instance-Subtask-Demo/00-init-kk.sql" "postgresql://platform_dev:dev_password_123@localhost:5432/workflow_platform_dev"
```

Or via Docker:

```powershell
Get-Content deploy/init-scripts/17-Multi-Instance-Subtask-Demo/00-init-kk.sql | docker exec -i platform-postgres-dev psql -v ON_ERROR_STOP=1 -U platform_dev -d workflow_platform_dev
Get-Content deploy/init-scripts/17-Multi-Instance-Subtask-Demo/04-create-meeting-remark-physical-table.sql | docker exec -i platform-postgres-dev psql -v ON_ERROR_STOP=1 -U platform_dev -d workflow_platform_dev
```

Use `01-*` / `02-*` only when patching an existing database without re-running the full init. `04-*` is additive and idempotent (`IF NOT EXISTS`) — safe to run after `00-*` on both a fresh install and an already-drifted database.
