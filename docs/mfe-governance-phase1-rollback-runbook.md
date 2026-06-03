# MFE Governance — Rollback Runbook (Phase 1)

## Overview
The MFE module registry supports version-level rollback through the `POST /{id}/rollback-version` API.
This runbook covers rollback procedures for frontend modules managed through the registry.

## Pre-requisites
- Access to admin-center API (authenticated)
- `frontend.module:version:rollback` permission
- Known stable version to roll back to

## Rollback Procedures

### 1. API-based Rollback (via Admin Center UI or API)

**Via Admin Center UI:**
1. Navigate to MFE Governance page (`/mfe/modules`)
2. Locate the module to rollback
3. Click "Rollback" button in the operations column
4. Enter the target version (e.g. "1.0.2")
5. Click Confirm

**Via curl:**
```bash
curl -X POST http://localhost:8090/api/v1/admin/frontend-modules/{id}/rollback-version \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-a" \
  -d '{"targetVersion": "1.0.2"}'
```

**Verification:**
```bash
# Check current version
curl -s "http://localhost:8090/api/v1/admin/frontend-modules?hostApp=user-portal&env=DEV" \
  -H "X-Tenant-Id: tenant-a" | python3 -m json.tool | grep version

# Verify runtime API reflects the rollback
curl -s "http://localhost:8090/api/v1/admin/frontend-modules/runtime?hostApp=user-portal&env=DEV" \
  -H "X-Tenant-Id: tenant-a" | python3 -m json.tool
```

### 2. Version Switch (with URL update)

Use this when the rollback also requires a different remote entry URL:

```bash
curl -X POST http://localhost:8090/api/v1/admin/frontend-modules/{id}/switch-version \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-a" \
  -d '{
    "version": "1.0.2",
    "remoteEntryUrl": "https://cdn.example.com/module/1.0.2/remoteEntry.js"
  }'
```

### 3. Emergency Disable

If a module causes host shell failures:

```bash
curl -X POST http://localhost:8090/api/v1/admin/frontend-modules/{id}/disable \
  -H "X-Tenant-Id: tenant-a"
```

Disabled modules are immediately removed from the runtime API response.
Re-enable: `POST /{id}/enable`

### 4. Direct DB Rollback (Emergency)

If the API is unavailable:
```sql
-- View module state
SELECT id, module_code, version, remote_entry_url, enabled
FROM ac_frontend_module_registry
WHERE tenant_id = 'tenant-a' AND host_app = 'user-portal' AND env = 'DEV';

-- Rollback version
UPDATE ac_frontend_module_registry
SET version = '1.0.2',
    remote_entry_url = 'https://cdn.example.com/module/1.0.2/remoteEntry.js',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 1;

-- Disable module
UPDATE ac_frontend_module_registry
SET enabled = false, updated_at = CURRENT_TIMESTAMP
WHERE id = 1;
```

## Audit Trail

All rollback/switch/disable operations are audited via `AdminAuditAspect`:
```sql
SELECT * FROM sys_audit_logs
WHERE resource_type = 'FRONTEND_MODULE'
ORDER BY created_at DESC LIMIT 10;
```

## Rollback Drill Results (2026-05-28)

| Step | Operation | Result |
|------|-----------|--------|
| 1 | Create module v1.0.0 | ✅ |
| 2 | Switch to v1.0.3 + new URL | ✅ version=1.0.3 |
| 3 | Rollback to v1.0.2 | ✅ version=1.0.2 |
| 4 | Verify runtime reflects v1.0.2 | ✅ |
| 5 | Disable module | ✅ runtime returns [] |
| 6 | Re-enable module | ✅ runtime returns module |
