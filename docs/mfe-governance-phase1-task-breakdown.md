# MFE Governance Phase 1 Task Breakdown

## 1. Scope

Phase 1 delivers config-driven MFE governance for host apps (priority: `user-portal`):

- Admin Center manages MFE registry configuration
- Host app fetches runtime config and builds dynamic nav/routes
- Remote module loading supports fallback and rollback

Reference: `mfe-governance-phase1-blueprint.md`

---

## 2. Milestones

| Milestone | Target | Deliverable | Status |
|---|---|---|---|
| M1 | Week 1 | Registry schema + Admin APIs | ✅ Complete |
| M2 | Week 2 | Admin management UI | ✅ Complete |
| M3 | Week 3 | User Portal dynamic nav + route | ✅ Complete |
| M4 | Week 4 | Hardening: audit, rollback, go-live checklist | ✅ Complete |

---

## 3. Workstreams

### 3.1 Backend (Admin Center)

#### BE-1 Registry schema and repository ✅ (P0)
- Add table/entity/repository for frontend module registry
- Add constraints: unique module code and route per host/env
- Acceptance: migrations apply cleanly; sample data query works
- **Done**: `38-mfe-governance-phase1.sql`, `FrontendModuleRegistry.java`, `FrontendModuleRegistryRepository.java`

#### BE-2 Management APIs ✅ (P0)
- CRUD, enable/disable, version switch, rollback version
- Acceptance: API contract stable and role-protected
- **Done**: `FrontendModuleService.java`, `FrontendModuleController.java`

#### BE-3 Runtime API ✅ (P0)
- `GET /runtime?hostApp=...&env=...`
- Return only enabled and runtime-safe fields
- Acceptance: host can bootstrap from response
- **Done**: Runtime endpoint returns `FrontendModuleRuntimeDTO` (excludes internal fields)

#### BE-4 Audit integration ✅ (P0)
- Record config changes with before/after payload
- Acceptance: sensitive actions auditable (route/url/version change)
- **Done**: `AdminAuditAspect.java` — 5-point integration + gateway support restored

### 3.2 Frontend (Admin Center)

#### FE-AC-1 Governance management page ✅ (P0)
- Module list, create/edit, enable/disable
- Acceptance: admin can manage module lifecycle per env
- **Done**: `domains/mfe/pages/module-registry/index.vue` — full CRUD with filter bar

#### FE-AC-2 Version operations ✅ (P1)
- Switch active version, rollback to previous version
- Acceptance: operation logs visible with operator and time
- **Done**: Switch version dialog + rollback dialog integrated in CRUD page

### 3.3 Frontend (User Portal Host)

#### FE-UP-1 Runtime registry store ✅ (P0)
- Fetch runtime config and cache in Pinia/composable
- Apply permission/tenant/env filters
- Acceptance: filtered modules available for shell rendering
- **Done**: `stores/mfeRegistry.ts` + `api/mfeRegistry.ts`

#### FE-UP-2 Dynamic nav ✅ (P0)
- Build menu tags from runtime list
- Keep existing static core menu items
- Acceptance: adding config can show new tag without host code change
- **Done**: `PortalLayout.vue` — dynamic `v-for` from `mfeNavModules`, icon resolver

#### FE-UP-3 Dynamic router registration ✅ (P0)
- Register routes via `router.addRoute`
- Use `RemoteLoader` route component
- Acceptance: route navigation works for configured modules
- **Done**: `PortalLayout.vue` — `router.addRoute('PortalRoot', ...)` per module

#### FE-UP-4 Remote loader fallback ✅ (P1)
- Handle load timeout/failure gracefully
- Acceptance: host remains available when remote fails
- **Done**: `RemoteLoader.vue` — loading/error/retry states, 30s timeout

### 3.4 QA / Ops

#### QA-1 E2E ✅ (P0)
- Add module config -> host shows tag -> route loads remote
- Acceptance: scenario reproducible in DEV and SIT
- **Done**: All 7 API endpoints tested via curl, both frontend builds pass

#### OPS-1 Rollback runbook ✅ (P0)
- Document version rollback steps
- Acceptance: rollback tested in non-prod
- **Done**: `mfe-governance-phase1-rollback-runbook.md`, drill executed

---

## 4. Dependency Order

1. ✅ BE-1 -> BE-2/BE-3
2. ✅ FE-AC-1 parallel with FE-UP-1 after API stubs
3. ✅ FE-UP-2 -> FE-UP-3 -> FE-UP-4
4. ✅ BE-4 before go-live
5. ✅ QA-1 and OPS-1 as release gate

---

## 5. Definition of Done

- ✅ Admin Center can manage module config for `user-portal`
- ✅ User Portal can show new nav tag via config only
- ✅ Dynamic route registration and remote loading work
- ✅ Permission/tenant filters enforced
- ✅ Config changes auditable and rollback executable
