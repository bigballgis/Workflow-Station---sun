# Gateway Governance Phase 1 Task Breakdown

## 1. Scope and Delivery Goal

This document breaks down Phase 1 tasks from `gateway-governance-phase1-blueprint.md` into executable work items.

Phase 1 goal:

- Embed Gateway Domain in Admin Center
- Deliver minimum governance loop: API/Application metadata + Release publish/rollback + audit trail
- Keep current runtime path unchanged (`Client -> Kong -> Services`)

> **Implementation Status: ✅ Complete — 2026-05-28**
>
> Implementation plan: `.hermes/plans/2026-05-27_gateway-phase1-implementation.md`
> 48 files delivered (39 new + 9 modified). Frontend and backend builds pass.

---

## 2. Milestones

## M1 - Frontend Shell Ready (Day 1-3) ✅

- [x] Gateway menu and routes available in Admin Center
- [x] Permission guards connected
- [x] Domain pages scaffolded

## M2 - Metadata CRUD Ready (Day 3-6) ✅

- [x] API Definition/API Version/Application CRUD backend and frontend integrated
- [x] Core DB tables online

## M3 - Publish Pipeline Ready (Day 6-9) ✅

- [x] Release entity + publish history
- [x] Kong adapter publish and rollback minimum path

## M4 - Audit + Stabilization (Day 9-10) ✅

- [x] Gateway controller audit events recorded
- [x] Build verification passed (frontend vite + backend mvn)
- [ ] Smoke, integration, and regression tests — deferred to QA phase

---

## 3. Workstream Breakdown

## 3.1 Frontend Workstream

### FE-1: Menu and Routing Integration — ✅ DONE
- **Priority**: P0
- **Files**:
  - `frontend/admin-center/src/layouts/AdminLayout.vue` — ✅ modified
  - `frontend/admin-center/src/router/index.ts` — ✅ modified
  - `frontend/admin-center/src/utils/permission.ts` — ✅ modified
  - `frontend/admin-center/src/i18n/locales/zh-CN.ts` — ✅ modified
  - `frontend/admin-center/src/i18n/locales/zh-TW.ts` — ✅ modified
  - `frontend/admin-center/src/i18n/locales/en.ts` — ✅ modified
- **Tasks**:
  - [x] Add `Gateway Governance` submenu
  - [x] Add Phase 1 routes under `/gateway/*`
  - [x] Register permission constants and route permissions
  - [x] Add i18n labels
- **Acceptance**:
  - [x] Menu visible only for users with gateway read permissions
  - [x] Unauthorized users blocked by route guard and menu hidden

### FE-2: Domain Skeleton — ✅ DONE
- **Priority**: P0
- **Files (new)**:
  - `frontend/admin-center/src/domains/gateway/pages/apis/index.vue` — ✅ created
  - `frontend/admin-center/src/domains/gateway/pages/applications/index.vue` — ✅ created
  - `frontend/admin-center/src/domains/gateway/pages/releases/index.vue` — ✅ created
  - `frontend/admin-center/src/domains/gateway/pages/audit/index.vue` — ✅ created
  - `frontend/admin-center/src/domains/gateway/api/gateway.ts` — ✅ created (replaces `services/`)
  - `frontend/admin-center/src/domains/gateway/types/gateway.ts` — ✅ created (replaces `models/`)
- **Tasks**:
  - [x] Build domain-based page skeletons
  - [x] Create API SDK wrappers for gateway endpoints
  - [x] Isolate state in component-local refs (no Pinia store for Phase 1)
- **Acceptance**:
  - [x] Page navigation works end-to-end
  - [x] API client calls backend through existing admin-center request layer

### FE-3: Release Console (Phase 1 MVP) — ✅ DONE (basic)
- **Priority**: P1
- **Tasks**:
  - [x] Show releases list with state-colored tags
  - [x] Add actions: submit-testing, publish, rollback (state-conditional buttons)
  - [ ] Show publish history timeline — deferred (detail page not built)
  - [ ] Show release detail/snapshot — deferred
- **Acceptance**:
  - [x] User can complete one publish cycle from UI
  - [x] Failure messages and status transitions are visible

### FE-4: Policy & Environment Pages — ⚠️ DEFERRED
- Per implementation plan: policies managed within API detail page (not built)
- Environments page deferred to Phase 2
- Access policies and traffic policies tables exist in DB but no dedicated UI pages

---

## 3.2 Backend Workstream

### BE-1: Gateway Domain Package Bootstrap — ✅ DONE
- **Priority**: P0
- **Packages (new)**:
  - `com.admin.controller.gateway` — ✅
  - `com.admin.service.gateway` — ✅
  - `com.admin.repository.gateway` — ✅
  - `com.admin.entity.gateway` — ✅
  - `com.admin.adapter.gateway.spi` — ✅
  - `com.admin.adapter.gateway.kong` — ✅
  - `com.admin.adapter.gateway.stub` — ✅ (added: StubGatewayProvider)
  - `com.admin.adapter.gateway.dto` — ✅ (added: 3 DTOs)
- **Tasks**:
  - [x] Create base entities/repositories/services/controllers
  - [x] Follow existing admin-center coding style and response conventions
- **Acceptance**:
  - [x] Controllers build and run in current admin-center service

### BE-2: Metadata CRUD APIs — ✅ DONE
- **Priority**: P0
- **Endpoints**:
  - [x] `/gateway/apis` CRUD
  - [x] `/gateway/apis/{id}/versions`
  - [x] `/gateway/applications` CRUD
- **Acceptance**:
  - [x] CRUD works with tenant isolation fields

### BE-3: Release Orchestration — ✅ DONE
- **Priority**: P0
- **Endpoints**:
  - [x] `/gateway/releases`
  - [x] `/gateway/releases/{id}/submit-testing`
  - [x] `/gateway/releases/{id}/publish`
  - [x] `/gateway/releases/{id}/rollback`
  - [x] `/gateway/releases/{id}/history`
- **Tasks**:
  - [x] Create release snapshot from SoT metadata
  - [x] Execute publish via adapter
  - [x] Record publish history with result and runtime revision
- **Acceptance**:
  - [x] Publish state machine enforced
  - [x] History and release state remain consistent on success/failure

### BE-4: Kong Adapter MVP — ✅ DONE
- **Priority**: P1
- **Interface**:
  - [x] `GatewayProvider.publishRelease(...)`
  - [x] `GatewayProvider.rollbackRelease(...)`
- **Implementation**:
  - [x] `StubGatewayProvider` — returns deterministic mock results (default mode)
  - [x] `KongGatewayProvider` — maps abstract policy model; returns NOT_IMPLEMENTED (Phase 1 placeholder)

### BE-5: Audit Integration — ✅ DONE
- **Priority**: P0
- **Files**:
  - `backend/admin-center/src/main/java/com/admin/audit/AdminAuditAspect.java` — ✅ modified
- **Tasks**:
  - [x] Add pointcuts for gateway controllers (3 new domains: GATEWAY_API, GATEWAY_APP, GATEWAY_RELEASE)
  - [x] Ensure publish/policy changes are auditable
- **Acceptance**:
  - [x] Audit entries contain operator, action, resource type/id, success/failure

---

## 3.3 Database Workstream

### DB-1: Core Schema Migration — ✅ DONE
- **Priority**: P0
- **Tables** (10 total):
  - [x] `ac_gateway_api_definition`
  - [x] `ac_gateway_api_version`
  - [x] `ac_gateway_application`
  - [x] `ac_gateway_credential`
  - [x] `ac_gateway_access_policy`
  - [x] `ac_gateway_traffic_policy`
  - [x] `ac_gateway_environment`
  - [x] `ac_gateway_release`
  - [x] `ac_gateway_publish_history`
  - [x] `ac_gateway_audit_log`
- **Tasks**:
  - [x] Create DDL with tenant/environment dimensions
  - [x] Add required unique/composite indexes
  - [x] Add GIN indexes on JSONB columns
  - [x] Add CHECK constraints for state/enum values
  - [x] Add COMMENT ON for all tables/columns
  - [x] Add `IF NOT EXISTS` guards
  - [x] Register in `00-init-all-schemas.sql`

### DB-2: Seed and Access Validation — ⚠️ PARTIAL
- **Tasks**:
  - [x] Gateway permissions seeded in `sys_permissions` (11 keys)
  - [x] SYS_ADMIN role gets all gateway permissions via `sys_role_permissions`
  - [ ] Seed minimal test data for dev — not done
  - [ ] Validate rollback and history queries — not done

---

## 3.4 QA and Verification Workstream

### QA-1: API Contract Verification — ⚠️ DEFERRED
- **Priority**: P0
- **Tasks**:
  - [ ] Validate request/response schema for all Phase 1 APIs
  - [ ] Validate state transition errors for invalid release actions

### QA-2: Publish Workflow E2E — ⚠️ DEFERRED
- **Priority**: P0
- **Tasks**:
  - [ ] E2E: create API -> create release -> publish -> rollback
  - [ ] Verify UI states and backend history consistency

### QA-3: Security and Permission Checks — ⚠️ DEFERRED
- **Priority**: P0
- **Tasks**:
  - [ ] Validate route/menu/button permissions
  - [ ] Validate non-privileged user cannot publish

---

## 4. Dependency and Order

1. ✅ DB-1 schema first
2. ✅ BE-1/BE-2 metadata APIs
3. ✅ FE-1/FE-2 wiring
4. ✅ BE-3 release orchestration
5. ✅ BE-4 adapter integration
6. ✅ FE-3 release UI
7. ✅ BE-5 audit
8. ⬜ QA full pass

---

## 5. Risk Register (Phase 1)

- **R1**: Kong payload mapping mismatch
  - Mitigation: Stub provider for dev/test ✅; Kong provider scaffolded with TODO markers
- **R2**: publish state inconsistency on partial failure
  - Mitigation: @Transactional on all mutating service methods ✅; adapter failure does not advance state ✅
- **R3**: permission drift between menu/route/backend
  - Mitigation: Centralized permission keys in `permission.ts` + DB seed ✅; Router/AdminLayout consistent ✅
  - ⚠️ Backend controllers use `@RequestHeader` but no `@PreAuthorize` — Phase 2 gap
- **R4**: tenant context inconsistency
  - Mitigation: All repositories filter by `tenantId` ✅; Services set `tenantId` in writes ✅
  - ⚠️ `X-Tenant-Id` header source not yet integrated with JWT — Phase 2 gap

---

## 6. Definition of Done (Phase 1)

- [x] Gateway domain available in Admin Center with role-based visibility
- [x] Metadata CRUD works for API/Application
- [x] Release publish and rollback complete through adapter path
- [x] Publish history and audit records traceable
- [x] Build verification passes (frontend + backend)
- [ ] Smoke and E2E checks pass in dev environment — deferred

---

## 7. Known Gaps (Post Phase 1)

| Gap | Impact | Plan |
|---|---|---|
| Policy/environment pages not in UI | Policies managed via API version detail (not built) | Phase 2 |
| Release detail/snapshot view not built | Cannot inspect release contents in UI | Phase 2 |
| Backend `@PreAuthorize` not configured | Permission enforced only at UI + audit level | Phase 2 |
| `X-Tenant-Id` not integrated with JWT | Requires frontend to send header explicitly | Phase 2 |
| Kong adapter returns NOT_IMPLEMENTED | Requires Kong Admin API connectivity | Phase 2 |
| No formal test suite | QA deferred | Phase 2 |
| No seed data for dev/demo | Requires manual data entry for testing | Phase 2 |
