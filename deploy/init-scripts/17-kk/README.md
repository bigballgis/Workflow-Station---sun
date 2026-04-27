## 17-kk (Function Unit `kk`) init script generator

This folder contains a **generator** that exports the current `kk` snapshot from the dev database into a runnable SQL init script.

### Files

- `generate-kk-init.ps1`: connect to Postgres and generate `00-init-kk.sql`
- `../99-maintenance/01-add-save-action-to-kk.sql`: idempotent patch to ensure `SAVE` action exists and is bound in BPMN (Base64-safe)

### Usage (PowerShell)

From repo root:

```powershell
.\deploy\init-scripts\17-kk\generate-kk-init.ps1
```

It generates:

- `deploy/init-scripts/17-kk/00-init-kk.sql`

You can then apply it:

```powershell
psql -v ON_ERROR_STOP=1 -f "deploy/init-scripts/17-kk/00-init-kk.sql" "postgresql://platform_dev:dev_password_123@localhost:5432/workflow_platform_dev"
```

