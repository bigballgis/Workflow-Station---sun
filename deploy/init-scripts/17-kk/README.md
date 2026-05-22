## 17-kk (Function Unit `Multi-Instance Subtask Demo`) init script generator

This folder contains a **generator** that exports the current `Multi-Instance Subtask Demo` (`fu-20260422-23tfag`) snapshot from the dev database into a runnable SQL init script.

### Files

- `generate-kk-init.ps1`: connect to Postgres and generate `00-init-kk.sql`
- `01-add-save-action.sql`: idempotent patch to ensure `SAVE` action exists and is bound in BPMN (Base64-safe)

### Usage (PowerShell)

From repo root:

```powershell
.\deploy\init-scripts\17-kk\generate-kk-init.ps1
```

It generates `deploy/init-scripts/17-kk/00-init-kk.sql` — **developer-workstation catalog only** (`dw_*` plus lookup-related `rt_*` metadata). It does **not** seed admin users, `rt_table_access`, or dev-specific audit user IDs (`created_by` / `updated_by` are normalized to `system`).

You can then apply it:

```powershell
psql -v ON_ERROR_STOP=1 -f "deploy/init-scripts/17-kk/00-init-kk.sql" "postgresql://platform_dev:dev_password_123@localhost:5432/workflow_platform_dev"
```

