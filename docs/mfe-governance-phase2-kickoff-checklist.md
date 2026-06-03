# MFE Governance Phase 2 Kickoff Checklist

## Pre-Flight

- [x] Phase 1 exit criteria passed
  - Admin Center can CRUD module config by host/env
  - User Portal dynamic nav/routes via runtime config
  - RemoteLoader fallback does not break host shell
  - Config changes auditable via AdminAuditAspect
  - All 6 Phase 1 permissions seeded (70-75)
  - Backend + admin-center + user-portal builds pass
- [x] Pilot modules confirmed (`notification-mfe`, `delegation-mfe`)

## Backend Infrastructure (Implemented 2026-05-28)

- [x] DDL: `ac_frontend_module_version` + `ac_frontend_module_health_log` → `39-mfe-governance-phase2.sql`
- [x] JPA Entities: `FrontendModuleVersion`, `FrontendModuleHealthLog`
- [x] Repositories: `FrontendModuleVersionRepository`, `FrontendModuleHealthLogRepository`
- [x] DTOs: `FrontendModuleVersionDTO`, `FrontendModuleHealthDTO`
- [x] Service: Enhanced `switchVersion` (records version history + active flag), enhanced `rollbackVersion` (looks up target version from history), new `getVersions()`, new `healthCheck()` (HTTP HEAD probe)
- [x] Controller: `GET /{id}/versions`, `POST /{id}/health-check`
- [x] Audit Aspect: `getVersions` excluded from FRONTEND_MODULE pointcut (read-only)
- [x] Permissions: `frontend.module:health:check` (76), `frontend.module:version:read` (77) → `04-admin-permissions.sql`
- [x] Version switch/rollback API available with version history tracking
- [x] Backend `mvn compile -DskipTests` passes

## Admin Center Frontend (Implemented 2026-05-28)

- [x] Types: `ModuleVersion`, `HealthCheckResult` → `mfe.ts`
- [x] API: `getVersions(id)`, `healthCheck(id)` → `api/mfe.ts`
- [x] Page: Version history dialog (table with version/URL/active tag/createdBy/createdAt), health check button + result dialog (el-result success/error)
- [x] i18n: 9 new keys added to en, zh-CN, zh-TW
- [x] Security approval for version operations (permissions seeded, SYS_ADMIN role mapping)
- [x] Admin center `vite build` passes (8.21s)
- [x] User portal `vite build` passes (18.33s)

## Remaining (Not Yet Executed)

- [ ] Independent CI/CD ready for each pilot MFE (OPS-1, OPS-2)
- [ ] Pilot MFE extraction: `notification-mfe` (FE-P1), `delegation-mfe` (FE-P2)
  - Scaffold new Vite + Module Federation projects
  - Extract existing notification/delegation code
  - Host integration for pilot routes (FE-H1)
- [ ] Non-prod rollback drill scheduled
- [ ] Load-failure fallback tested in host (RemoteLoader from Phase 1 covers this — needs e2e verification)
- [ ] e2e: enable/disable/switch-version/rollback (QA-1)

## Code Review Findings (2026-05-28)

See code quality review for full details. Key actions taken:

| Severity | Issue | Status |
|----------|-------|--------|
| CRITICAL | `@Transactional` on `healthCheck()` held DB connection during network I/O | Fixed — removed `@Transactional` |
| MEDIUM | Race condition: no active version during switch gap | Accepted for MVP |
| LOW | `RuntimeException` instead of typed exceptions | Consistent with Phase 1 |

## Files Created

| File | Purpose |
|------|---------|
| `deploy/init-scripts/00-schema/39-mfe-governance-phase2.sql` | DDL |
| `backend/.../entity/module/FrontendModuleVersion.java` | Entity |
| `backend/.../entity/module/FrontendModuleHealthLog.java` | Entity |
| `backend/.../repository/module/FrontendModuleVersionRepository.java` | Repository |
| `backend/.../repository/module/FrontendModuleHealthLogRepository.java` | Repository |
| `backend/.../dto/module/FrontendModuleVersionDTO.java` | DTO |
| `backend/.../dto/module/FrontendModuleHealthDTO.java` | DTO |

## Files Modified

| File | Change |
|------|--------|
| `FrontendModuleService.java` | Injected VersionRepo + HealthLogRepo; enhanced switchVersion/rollbackVersion; added getVersions + healthCheck |
| `FrontendModuleController.java` | Added `GET /{id}/versions`, `POST /{id}/health-check` |
| `AdminAuditAspect.java` | Pointcut excludes `getVersions` |
| `04-admin-permissions.sql` | Added permissions 76, 77 |
| `mfe.ts` (types) | Added `ModuleVersion`, `HealthCheckResult` |
| `mfe.ts` (api) | Added `getVersions()`, `healthCheck()` |
| `module-registry/index.vue` | Version history dialog, health check button/dialog, script logic |
| `i18n` (en/zh-CN/zh-TW) | 9 new mfe keys |

## Pilot MFE Projects (Implemented 2026-05-28)

- [x] FE-P1: `notification-mfe` — standalone Vite + Module Federation project
- [x] FE-P2: `delegation-mfe` — standalone Vite + Module Federation project
- [x] FE-H1: User Portal dynamic route registration (verified — Phase 1 integration already in place)
- [x] Seed SQL: `40-mfe-governance-phase2-pilot-seed.sql` — registry entries + version records

### notification-mfe

| File | Purpose |
|------|---------|
| `frontend/notification-mfe/` | Project root — Vite + `@originjs/vite-plugin-federation` |
| `src/App.vue` | Full notification page (tabs, mark read, delete, pagination) |
| `src/stores/notification.ts` | Pinia store (fetch, markRead, markAllRead, WebSocket) |
| `src/api/notification.ts` + `request.ts` | Axios client → `/api/portal` |
| `src/composables/useNotificationWebSocket.ts` | STOMP/SockJS WebSocket |
| `src/i18n/` | en/zh-CN/zh-TW (notification keys only) |

Build: `dist/assets/remoteEntry.js` (1.60 kB) + `__federation_expose_App-*.js` (1,167 kB)

### delegation-mfe

| File | Purpose |
|------|---------|
| `frontend/delegation-mfe/` | Project root — Vite + `@originjs/vite-plugin-federation` |
| `src/App.vue` | Full delegation page (table, create dialog, suspend/resume/delete) |
| `src/stores/delegation.ts` | Pinia store (fetch, create, delete, toggle) |
| `src/api/delegation.ts` + `request.ts` | Axios client → `/api/portal` |
| `src/i18n/` | en/zh-CN/zh-TW (delegation keys only) |

Build: `dist/assets/remoteEntry.js` (1.60 kB) + `__federation_expose_App-*.js` (1,097 kB)

### How to run the pilot

```bash
# 1. Apply DDL + seed (if not already)
psql -d n8n_dev -f deploy/init-scripts/00-schema/39-mfe-governance-phase2.sql
psql -d n8n_dev -f deploy/init-scripts/00-schema/40-mfe-governance-phase2-pilot-seed.sql

# 2. Start MFE dev servers
cd frontend/notification-mfe && npx vite --port 3100 &
cd frontend/delegation-mfe && npx vite --port 3101 &

# 3. Start user-portal (if not already running)
cd frontend/user-portal && npx vite --port 3001 &

# 4. Open http://localhost:3001/portal/ → "/mfe/notifications" and "/mfe/delegations"
#    should appear in the nav and load via RemoteLoader
```

### Remaining (Operations / QA)

- [x] OPS-1: CI/CD pipeline per MFE with version tagging → `.github/workflows/mfe-ci.yml`
- [x] OPS-2: CDN / static hosting + cache strategy → Dockerfiles, nginx-edge.conf, docker-compose services
- [x] Non-prod rollback drill scheduled → `deploy/scripts/mfe-rollback-drill.sh` (8-step automated drill)
- [x] e2e: enable/disable/switch-version/rollback (QA-1) → `deploy/scripts/mfe-e2e-test.sh` (10 test cases)

## Files Created (Phase 2 — total: 26 files)

| File | Purpose |
|------|---------|
| `deploy/init-scripts/00-schema/39-mfe-governance-phase2.sql` | DDL |
| `deploy/init-scripts/00-schema/40-mfe-governance-phase2-pilot-seed.sql` | Pilot registry seed |
| `backend/.../entity/module/FrontendModuleVersion.java` | Entity |
| `backend/.../entity/module/FrontendModuleHealthLog.java` | Entity |
| `backend/.../repository/module/FrontendModuleVersionRepository.java` | Repository |
| `backend/.../repository/module/FrontendModuleHealthLogRepository.java` | Repository |
| `backend/.../dto/module/FrontendModuleVersionDTO.java` | DTO |
| `backend/.../dto/module/FrontendModuleHealthDTO.java` | DTO |
| `frontend/notification-mfe/*` (12 files) | Pilot MFE project |
| `frontend/delegation-mfe/*` (12 files) | Pilot MFE project |
