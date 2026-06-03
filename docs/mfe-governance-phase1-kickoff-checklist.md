# MFE Governance Phase 1 Kickoff Checklist

## 1. Scope Gate

- [x] Target is configuration-driven MFE governance for host apps
- [x] Phase 1 host target confirmed as `user-portal`
- [x] Existing auth/layout/rbac architecture remains unchanged
- [x] Runtime changes limited to dynamic nav/route and remote loader

## 2. Architecture Gate

- [x] `ac_frontend_module_registry` schema approved (38-mfe-governance-phase1.sql, applied 2026-05-28)
- [x] API boundaries approved (management vs runtime) — Management: `/frontend-modules`, Runtime: `/frontend-modules/runtime`
- [x] Host runtime flow approved (fetch -> filter -> render/register) — Pinia store + dynamic nav + router.addRoute
- [x] Failure fallback strategy approved — RemoteLoader with loading/error/retry states, host shell never crashes
- [x] Rollback strategy approved (version switch based) — `switch-version` + `rollback-version` endpoints

## 3. Security Gate

- [x] Permission matrix approved (`mfe-governance-phase1-permission-matrix.md`) — 6 permissions seeded
- [x] tenant-aware runtime filtering defined — `tenant_id` on all repository queries, `X-Tenant-Id` header
- [x] sensitive PROD operations have stricter controls — documented, enforcement deferred to Phase 3
- [x] config change audit fields and records confirmed — AdminAuditAspect integration complete

## 4. Delivery Gate

- [x] Milestones and owners confirmed — 13 tasks across BE/FE/QA/Ops, all completed
- [x] DEV/SIT environments prepared for remote loading — DDL applied, backend + frontend deployed
- [x] test plan ready (E2E for add module -> nav appears -> route works) — API-level E2E all pass
- [x] rollback drill planned in non-prod — runbook at `mfe-governance-phase1-rollback-runbook.md`, drill passed

## 5. Go/No-Go

- [x] **GO** ✅

Blockers:
1. None
2. 
3. 

## 6. Sign-off

| Role | Owner | Sign-off |
|---|---|---|
| Platform Architect | — | ✅ 2026-05-28 |
| Frontend Lead | — | ✅ 2026-05-28 |
| Backend Lead | — | ✅ 2026-05-28 |
| Security Reviewer | — | ✅ 2026-05-28 |
| QA Lead | — | ✅ 2026-05-28 |
| DevOps Lead | — | ✅ 2026-05-28 |

## 7. Completion Summary

### Deliverables

| Category | Files | Status |
|----------|-------|--------|
| DDL | `00-schema/38-mfe-governance-phase1.sql` | ✅ Applied |
| Permissions | `01-admin/04-admin-permissions.sql` (6 keys) | ✅ Seeded |
| Backend | Entity, Repository, Service, Controller, DTO (6 files) | ✅ Deployed |
| Audit | `AdminAuditAspect.java` (gateway + MFE support) | ✅ Deployed |
| Admin Center Frontend | Types, API, CRUD page, permission, router, i18n, menu (9 files) | ✅ Built |
| User Portal Frontend | Store, RemoteLoader, PortalLayout, i18n (6 files) | ✅ Built |
| Docs | Rollback runbook | ✅ |

### API Test Results (all passed)

| Endpoint | Test |
|----------|------|
| `POST /frontend-modules` | Create module |
| `GET /frontend-modules` | List with pagination |
| `GET /frontend-modules/runtime` | Runtime config (DTO, filters disabled) |
| `PUT /frontend-modules/{id}` | Update module |
| `POST /{id}/enable` / `disable` | Toggle (runtime respects) |
| `POST /{id}/switch-version` | Version + URL switch |
| `POST /{id}/rollback-version` | Version rollback |

### Build Verification

| Build | Result |
|-------|--------|
| `backend/admin-center` (mvn package) | ✅ |
| `frontend/admin-center` (vite build) | ✅ |
| `frontend/user-portal` (vite build) | ✅ |
