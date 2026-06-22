## 17-Multi-Instance-Subtask-Demo

Hand-maintained SQL init for Function Unit `Multi-Instance Subtask Demo` (`fu-20260422-23tfag`).

### Files

| File | Purpose |
|---|---|
| `00-init-kk.sql` | Full idempotent init: cleanup + INSERT snapshot (exported from dev DB; includes `dw_main_table_view_*`, `rt_view_*`, `rt_lookup_*`) |
| `01-add-save-action.sql` | Standalone SAVE action patch (legacy; already in `00-init-kk.sql`) |
| `02-add-subtask-progress-fields.sql` | Standalone patch for `task_status` / `task_current_node` (legacy; already in `00-init-kk.sql`) |

Docker first-time init loads `00-init-kk.sql` automatically via `00-init-all.sh` (Step 5d).

### Apply manually

```powershell
psql -v ON_ERROR_STOP=1 -f "deploy/init-scripts/17-Multi-Instance-Subtask-Demo/00-init-kk.sql" "postgresql://platform_dev:dev_password_123@localhost:5432/workflow_platform_dev"
```

Or via Docker:

```powershell
Get-Content deploy/init-scripts/17-Multi-Instance-Subtask-Demo/00-init-kk.sql | docker exec -i platform-postgres-dev psql -v ON_ERROR_STOP=1 -U platform_dev -d workflow_platform_dev
```

Use `01-*` / `02-*` only when patching an existing database without re-running the full init.
