# Gateway Governance Phase 3 Kickoff Checklist

## Phase 3 Goal

Extract the Gateway domain from admin-center as a standalone `gateway-mfe` micro frontend using `@originjs/vite-plugin-federation`, enabling independent build/deploy/rollback of gateway pages without admin-center host redeploys.

## Pre-Flight

- [ ] Phase 1+2 exit criteria passed
  - [ ] All 10 gateway tables exist in DB (`ac_gateway_*`)
  - [ ] All 16 gateway permissions seeded (46-56 for Phase 1, 57-61 for Phase 2)
  - [ ] Gateway roles created (GATEWAY_VIEWER/OPERATOR/ADMIN/SECURITY_AUDITOR)
  - [ ] AdminAuditAspect has GATEWAY_API, GATEWAY_APP, GATEWAY_RELEASE, GATEWAY_DRIFT pointcuts
  - [ ] Admin Center gateway pages (6 sub-pages) work end-to-end
  - [ ] Gateway environment data seeded (DEV/SIT/UAT/PROD)
- [ ] Pilot MFE pattern validated (notification-mfe + delegation-mfe from MFE Phase 2)

## Phase 3 Tasks

### 1. gateway-mfe Project Scaffold

- [ ] Create `frontend/gateway-mfe/` directory
- [ ] `package.json` — deps: vue, pinia, element-plus, axios, vue-i18n, vue-router, @element-plus/icons-vue
- [ ] `vite.config.ts` — `@originjs/vite-plugin-federation` with `name: 'gateway_mfe'`, `shared: []`
- [ ] `tsconfig.json`, `index.html`, `env.d.ts`
- [ ] `Dockerfile` (nginx:alpine) + `nginx.conf` (CORS + caching)
- [ ] `.dockerignore`

### 2. API Layer

- [ ] `src/api/request.ts` — standalone axios instance (`baseURL: /api/v1/admin`, headers from localStorage)
- [ ] `src/api/gateway.ts` — copy from `admin-center/src/domains/gateway/api/gateway.ts`, fix import: `@/api/request`

### 3. Types + Permissions

- [ ] `src/types/gateway.ts` — copy from `admin-center/src/domains/gateway/types/gateway.ts`
- [ ] `src/utils/permission.ts` — PERMISSIONS constants (16 keys) + `hasPermission()` from localStorage

### 4. i18n Extraction

- [ ] Extract `gateway: { ... }` block from admin-center's `en.ts`, `zh-CN.ts`, `zh-TW.ts`
- [ ] Add MFE-specific keys: `gatewayMfeLoading`, `gatewayMfeError`, `retry`

### 5. Page Migration (6 pages)

- [ ] Copy `admin-center/src/domains/gateway/pages/` → `gateway-mfe/src/pages/`
- [ ] Fix all import paths: `@/domains/gateway/api/gateway` → `@/api/gateway`
- [ ] Fix all import paths: `@/domains/gateway/types/gateway` → `@/types/gateway`
- [ ] Fix `res.data.content` → `res.content` bug in all 6 pages
- [ ] Grep verify: zero remaining `@/domains/` references

### 6. App.vue + Hash Router

- [ ] `src/App.vue` — `<router-view />` only
- [ ] `src/router/index.ts` — `createWebHashHistory()` with 6 routes:
  - `#/apis`, `#/applications`, `#/releases`, `#/audit`, `#/drift`, `#/monitoring`

### 7. GatewayRemoteLoader.vue (Admin Center Host)

- [ ] Create `admin-center/src/components/remote/GatewayRemoteLoader.vue`
- [ ] Load `remoteEntry.js` from `VITE_GATEWAY_MFE_URL` env var (default: `/mfe-gateway/assets/remoteEntry.js`)
- [ ] Access `window.gateway_mfe.get('./App')` factory
- [ ] Create fresh Vue app with pinia, element-plus, i18n
- [ ] Three states: loading (el-skeleton), error (el-result + retry), mounted
- [ ] Cleanup on unmount (remove script tag, clear window.gateway_mfe)
- [ ] Hash sync: after mount, extract sub-path from `window.location.pathname`

### 8. Admin Center Router Changes

- [ ] Replace 6 individual gateway routes with 1 catch-all:
  - `path: 'gateway/:pathMatch(.*)*'`
  - `component: () => import('@/components/remote/GatewayRemoteLoader.vue')`
- [ ] Consolidate `/gateway/*` route permissions into single entry

### 9. Admin Center Menu Update

- [ ] Add `canReadGatewayDrift` + `canReadGatewayMonitoring` computed guards
- [ ] Add drift + monitoring menu items to AdminLayout.vue
- [ ] Update `canReadGateway` to include DRIFT + MONITORING perms

### 10. DDL — Deployment Tracking

- [ ] Create `deploy/init-scripts/00-schema/41-gateway-governance-phase3.sql`
  - Table: `ac_gateway_mfe_deploy` (id, remote_name, version, remote_entry_url, deployed_at, deployed_by, active)
- [ ] Register in both `00-init-all-schemas.sql` and `00-init-all-schemas-standalone.sql`

### 11. Docker + nginx Integration

- [ ] Add `gateway-mfe` service to `docker-compose.dev.yml` (port 3220)
- [ ] Add `depends_on: gateway-mfe` to `edge-frontend`
- [ ] Add `/mfe-gateway/` proxy block to `nginx-edge.conf`

### 12. Build Verification

- [ ] `cd frontend/gateway-mfe && npm install && npx vite build`
  - Verify: `dist/assets/remoteEntry.js` exists
- [ ] `cd frontend/admin-center && npx vite build`
  - Verify: NO gateway page chunks in dist (they're now in gateway-mfe)
  - Verify: `GatewayRemoteLoader-*.js` exists
- [ ] Backend `mvn compile -DskipTests`

### 13. Database Seeding

- [ ] Apply Phase 3 DDL: `psql -d n8n_dev -f 41-gateway-governance-phase3.sql`
- [ ] Restart admin-center: `docker restart platform-admin-center-dev`

### 14. Phase 3 Exit Criteria

- [ ] All 6 gateway pages load via GatewayRemoteLoader (no admin-center rebuild needed)
- [ ] Gateway MFE can be built and deployed independently
- [ ] Version switch works without touching admin-center
- [ ] Hash-based routing preserves deep links (`/admin/gateway/drift`)
- [ ] Failed MFE load shows error fallback, admin-center shell remains available

## PITFALLS

| # | Pitfall | Mitigation |
|---|---------|------------|
| 1 | Federation `name` must use underscores — RemoteLoader does `.replace(/-/g, '_')` | Use `gateway_mfe`, NOT `gateway-mfe` |
| 2 | Copied pages inherit `res.data.content` bug (should be `res.content`) | Grep and fix all 6 pages after copy |
| 3 | Hash router required for multi-page MFE | `createWebHashHistory()` not `createWebHistory()` |
| 4 | `docker-entrypoint-initdb.d` is first-boot only | Apply DDL + seed manually to running DB |
| 5 | JPA metadata cache survives DDL changes | Restart admin-center after DDL |
| 6 | Gateway environments must be explicitly seeded | Run env seed SQL if not done |
| 7 | Script tag not cleaned up on unmount | Remove in `onBeforeUnmount` |
| 8 | Self-contained bundle duplicates Element Plus CSS | Acceptable for Phase 3; optimize in Phase 4 |

## Phase 3 File Manifest

### Created (gateway-mfe/)

| File | Purpose |
|------|---------|
| `package.json` | Dependencies |
| `vite.config.ts` | Federation plugin config |
| `tsconfig.json` | TypeScript config |
| `index.html` | Dev entry |
| `env.d.ts` | Type declarations |
| `Dockerfile` | nginx:alpine |
| `nginx.conf` | Static serving + CORS + cache |
| `.dockerignore` | Build exclusions |
| `src/main.ts` | Dev bootstrap |
| `src/App.vue` | Router shell |
| `src/router/index.ts` | Hash router (6 routes) |
| `src/api/request.ts` | Standalone axios |
| `src/api/gateway.ts` | Gateway API functions |
| `src/types/gateway.ts` | TypeScript interfaces |
| `src/utils/permission.ts` | Permission helpers |
| `src/i18n/en.ts` | English locale |
| `src/i18n/zh-CN.ts` | Chinese locale |
| `src/i18n/zh-TW.ts` | Traditional Chinese locale |
| `src/pages/apis/index.vue` | API Definitions page |
| `src/pages/applications/index.vue` | Applications page |
| `src/pages/releases/index.vue` | Releases page |
| `src/pages/audit/index.vue` | Audit page |
| `src/pages/drift/index.vue` | Drift page |
| `src/pages/monitoring/index.vue` | Monitoring page |

### Created (Admin Center Host)

| File | Purpose |
|------|---------|
| `components/remote/GatewayRemoteLoader.vue` | Loads + mounts gateway-mfe |

### Created (DDL)

| File | Purpose |
|------|---------|
| `deploy/init-scripts/00-schema/41-gateway-governance-phase3.sql` | `ac_gateway_mfe_deploy` table |

### Modified

| File | Change |
|------|--------|
| `admin-center/router/index.ts` | Replace 6 gateway routes → 1 catch-all |
| `admin-center/utils/permission.ts` | Consolidate gateway permissions |
| `admin-center/layouts/AdminLayout.vue` | Add drift + monitoring menu items |
| `docker-compose.dev.yml` | Add gateway-mfe service |
| `nginx-edge.conf` | Add /mfe-gateway/ proxy |
| `00-init-all-schemas.sql` | Register 41-*.sql |
| `00-init-all-schemas-standalone.sql` | Register 41-*.sql |
