# Gateway Governance Phase 1 Implementation Plan

## Goal

Implement Gateway Governance Phase 1 as an embedded domain in Admin Center, delivering:
- API/Application metadata CRUD
- Release publish/rollback through Kong adapter
- Full audit trail and RBAC

## Implementation Order

Following the document's dependency chain: DB → BE → FE → QA

---

## Step 1: Database — DDL Migration

**File:** `deploy/init-scripts/00-schema/36-gateway-governance-schema.sql`

Next available number is 36 (current highest is 35).

Copy the Phase 1 DDL from `docs/gateway-governance-phase1-ddl.sql` with adjustments:
- Use `platform_dev` schema (current convention — check `ac_gateway_*` prefix alignment)
- Add `IF NOT EXISTS` guards (consistent with existing migration style)
- Note: The DDL uses `SET search_path TO platform_dev;` convention — check `00-init-all-schemas.sql` for exact pattern

**10 tables:**
1. `ac_gateway_api_definition` — API definitions
2. `ac_gateway_api_version` — API versions
3. `ac_gateway_application` — Applications
4. `ac_gateway_credential` — Credentials
5. `ac_gateway_access_policy` — Access policies (JWT, IP whitelist, etc.)
6. `ac_gateway_traffic_policy` — Traffic policies (rate limit, timeout, etc.)
7. `ac_gateway_environment` — Environment configs
8. `ac_gateway_release` — Release records
9. `ac_gateway_publish_history` — Publish history
10. `ac_gateway_audit_log` — Gateway audit log

**Verification:** Apply migration to dev DB, check `\dt ac_gateway_*`

---

## Step 1.5: Database — Gateway Permission Seed

**Files:**
- `deploy/init-scripts/01-admin/04-admin-permissions.sql` (preferred)
- or a new follow-up seed script under `deploy/init-scripts/01-admin/`

Add gateway permissions into `sys_permissions` and bind them into `sys_role_permissions`:

- `gateway:api:read`, `gateway:api:write`
- `gateway:application:read`, `gateway:application:write`
- `gateway:policy:read`, `gateway:policy:write`
- `gateway:release:read`, `gateway:release:execute`
- `gateway:environment:read`, `gateway:environment:write`
- `gateway:audit:read`

At minimum, bind all above to `role-sys-admin` in seed data.

**Verification:** `SELECT code FROM sys_permissions WHERE code LIKE 'gateway:%';` and role binding check in `sys_role_permissions`.

---

## Step 2: Backend — JPA Entities

**Directory:** `backend/admin-center/src/main/java/com/admin/entity/gateway/`

Create JPA entities for tables 1-7 and 9 (table 8 release + table 10 audit_log are separate):

1. `ApiDefinition.java` — maps to `ac_gateway_api_definition`
2. `ApiVersion.java` — maps to `ac_gateway_api_version`
3. `Application.java` — maps to `ac_gateway_application`
4. `Credential.java` — maps to `ac_gateway_credential`
5. `AccessPolicy.java` — maps to `ac_gateway_access_policy`
6. `TrafficPolicy.java` — maps to `ac_gateway_traffic_policy`
7. `Environment.java` — maps to `ac_gateway_environment`
8. `GatewayRelease.java` — maps to `ac_gateway_release`
9. `PublishHistory.java` — maps to `ac_gateway_publish_history`

Pattern: Follow existing entities like `FunctionUnit.java` — use `@Entity`, `@Table(name = "ac_gateway_...")`, `@Id @GeneratedValue(strategy = GenerationType.IDENTITY)`, Lombok `@Data`.

Audit log (`ac_gateway_audit_log`) may map to existing SecurityAuditComponent — evaluate whether to reuse or create a separate entity.

---

## Step 3: Backend — Repositories

**Directory:** `backend/admin-center/src/main/java/com/admin/repository/gateway/`

Spring Data JPA repositories:
- `ApiDefinitionRepository`
- `ApiVersionRepository`
- `ApplicationRepository`
- `CredentialRepository`
- `AccessPolicyRepository`
- `TrafficPolicyRepository`
- `EnvironmentRepository`
- `GatewayReleaseRepository`
- `PublishHistoryRepository`

All queries must include `tenantId` filter. Pattern: `findByTenantIdAnd...`

---

## Step 4: Backend — Adapter Layer (SPI + Kong)

**Directory:** `backend/admin-center/src/main/java/com/admin/adapter/gateway/`

### SPI Interface
`spi/GatewayProvider.java`:
```java
public interface GatewayProvider {
    PublishResult publishRelease(ReleaseSnapshot snapshot, Environment env);
    RollbackResult rollbackRelease(String targetReleaseId, Environment env);
}
```

### Kong Implementation
`kong/KongGatewayProvider.java`:
- Maps abstract policy model → Kong Admin API payloads
- Uses RestTemplate/WebClient to call Kong Admin API
- Returns domain-level results, not Kong-native objects

### Stub/Kong Mode (mandatory for Phase 1)

Add adapter mode switch so dev/test can run without Kong Admin API:

- `gateway.adapter.mode=stub|kong`
- `stub` mode returns deterministic mock publish/rollback responses (with mock revision)
- `kong` mode calls real Kong Admin API

Recommended implementation:

- `StubGatewayProvider` implements `GatewayProvider`
- conditional bean wiring via profile/property (`@ConditionalOnProperty`)
- integration tests run in `stub` mode by default

### DTOs
- `dto/ReleaseSnapshot.java` — snapshot of API versions + policies
- `dto/PublishResult.java` — success/failure + revision
- `dto/RollbackResult.java`

---

## Step 5: Backend — Services

**Directory:** `backend/admin-center/src/main/java/com/admin/service/gateway/`

### `ApiDefinitionService`
- CRUD for API definitions and versions
- Import OpenAPI spec
- Tenant-scoped queries

### `ApplicationService`
- CRUD for applications and credentials
- `secret_ref` approach for credential secrets

### `ReleaseService`
- Create release with snapshot from SoT metadata
- State machine: DRAFT → TESTING → PUBLISHED → ROLLED_BACK
- State transition validation
- Publish orchestration (load snapshot → call adapter → record history)
- Rollback orchestration

### `GatewayAuditService`
- Query audit logs by tenant, resource type, operator
- Map `ac_gateway_audit_log` entries

---

## Step 6: Backend — Controllers

**Directory:** `backend/admin-center/src/main/java/com/admin/controller/gateway/`

Base path: use controller-level `/gateway/**` only.
Reason: `admin-center` already has `context-path: /api/v1/admin`, so effective URL becomes `/api/v1/admin/gateway/**`.

### `ApiDefinitionController`
- `POST /apis` — create API definition
- `GET /apis` — list (paginated, with keyword/status filters)
- `GET /apis/{apiId}` — detail
- `PUT /apis/{apiId}` — update
- `POST /apis/{apiId}/versions` — create version
- `POST /apis/import-openapi` — import from OpenAPI

### `ApplicationController`
- `POST /applications` — create
- `GET /applications` — list
- `GET /applications/{appId}` — detail
- `PUT /applications/{appId}` — update
- `POST /applications/{appId}/credentials` — create credential

### `ReleaseController`
- `POST /releases` — create release
- `POST /releases/{releaseId}/submit-testing` — DRAFT→TESTING
- `POST /releases/{releaseId}/publish` — TESTING→PUBLISHED
- `POST /releases/{releaseId}/rollback` — PUBLISHED→ROLLED_BACK
- `GET /releases/{releaseId}` — detail
- `GET /releases/{releaseId}/history` — publish history

### `GatewayAuditController`
- `GET /audit` — query audit logs (paginated, filtered)

**Pattern:** Follow `ConfigController.java` style — `@RestController`, `@RequiredArgsConstructor`, `ResponseEntity` returns.

---

## Step 7: Backend — Audit Integration

**File:** `backend/admin-center/src/main/java/com/admin/audit/AdminAuditAspect.java`

Add pointcuts for gateway controllers:
```java
@Around("within(com.admin.controller.gateway.ApiDefinitionController) ...")
public Object auditGatewayApi(ProceedingJoinPoint pjp) throws Throwable {
    return audit(pjp, "GATEWAY_API");
}

@Around("within(com.admin.controller.gateway.ApplicationController) ...")
public Object auditGatewayApp(ProceedingJoinPoint pjp) throws Throwable {
    return audit(pjp, "GATEWAY_APP");
}

@Around("within(com.admin.controller.gateway.ReleaseController) ...")
public Object auditGatewayRelease(ProceedingJoinPoint pjp) throws Throwable {
    return audit(pjp, "GATEWAY_RELEASE");
}
```

Add `GATEWAY_API`, `GATEWAY_APP`, `GATEWAY_RELEASE` cases to `resolveMeta()` switch and `fetchEntityJson()` switch with the new repository beans.

---

## Step 8: Frontend — Permission Constants

**File:** `frontend/admin-center/src/utils/permission.ts`

Add gateway permission keys:
```typescript
GATEWAY_API_READ: 'gateway:api:read',
GATEWAY_API_WRITE: 'gateway:api:write',
GATEWAY_APP_READ: 'gateway:application:read',
GATEWAY_APP_WRITE: 'gateway:application:write',
GATEWAY_POLICY_READ: 'gateway:policy:read',
GATEWAY_POLICY_WRITE: 'gateway:policy:write',
GATEWAY_RELEASE_READ: 'gateway:release:read',
GATEWAY_RELEASE_EXECUTE: 'gateway:release:execute',
GATEWAY_ENV_READ: 'gateway:environment:read',
GATEWAY_ENV_WRITE: 'gateway:environment:write',
GATEWAY_AUDIT_READ: 'gateway:audit:read',
```

Add to `ROUTE_PERMISSIONS`:
```typescript
'/gateway': [PERMISSIONS.GATEWAY_API_READ, PERMISSIONS.GATEWAY_APP_READ, ...],
'/gateway/apis': [PERMISSIONS.GATEWAY_API_READ],
'/gateway/applications': [PERMISSIONS.GATEWAY_APP_READ],
'/gateway/releases': [PERMISSIONS.GATEWAY_RELEASE_READ],
'/gateway/audit': [PERMISSIONS.GATEWAY_AUDIT_READ],
// ... etc
```

Add default permissions to `ROLE_PERMISSION_DEFAULTS` for SYS_ADMIN/SUPER_ADMIN:
```typescript
SYS_ADMIN: [..., 'gateway:api:read', 'gateway:api:write', ...all gateway perms],
SUPER_ADMIN: [..., ...all gateway perms],
```

---

## Step 9: Frontend — Router

**File:** `frontend/admin-center/src/router/index.ts`

Add lazy-loaded routes under the parent layout:
```typescript
{
  path: 'gateway/apis',
  name: 'GatewayApis',
  component: () => import('@/domains/gateway/pages/apis/index.vue'),
  meta: { titleKey: 'gateway.apis', permissions: [PERMISSIONS.GATEWAY_API_READ] }
},
{
  path: 'gateway/applications',
  name: 'GatewayApplications',
  component: () => import('@/domains/gateway/pages/applications/index.vue'),
  meta: { titleKey: 'gateway.applications', permissions: [PERMISSIONS.GATEWAY_APP_READ] }
},
{
  path: 'gateway/releases',
  name: 'GatewayReleases',
  component: () => import('@/domains/gateway/pages/releases/index.vue'),
  meta: { titleKey: 'gateway.releases', permissions: [PERMISSIONS.GATEWAY_RELEASE_READ] }
},
{
  path: 'gateway/audit',
  name: 'GatewayAudit',
  component: () => import('@/domains/gateway/pages/audit/index.vue'),
  meta: { titleKey: 'gateway.audit', permissions: [PERMISSIONS.GATEWAY_AUDIT_READ] }
}
```

Phase 1 pages: apis, applications, releases, audit (4 pages). Access policies and traffic policies are managed within the API detail page. Environments page deferred.

---

## Step 10: Frontend — i18n Labels

**Files:** `frontend/admin-center/src/i18n/locales/{zh-CN,zh-TW,en}.ts`

Add `gateway:` namespace with labels for menu, pages, and common gateway terms:
- `gateway.title` — "Gateway Governance" / "网关治理" / "網關治理"
- `gateway.apis` — "API Management" / "API管理" / "API管理"
- `gateway.applications` — "Application Management" / "应用管理" / "應用管理"
- `gateway.releases` — "Release Management" / "发布管理" / "發佈管理"
- `gateway.audit` — "Gateway Audit" / "网关审计" / "網關審計"
- `gateway.createApi`, `gateway.createApp`, `gateway.createRelease`, etc.
- Release states: DRAFT, TESTING, PUBLISHED, ROLLED_BACK
- Error codes: `gateway.apiNotFound`, `gateway.invalidState`, etc.

---

## Step 11: Frontend — Menu Integration

**File:** `frontend/admin-center/src/layouts/AdminLayout.vue`

Add `Gateway Governance` submenu with `v-if="canReadGateway"` gate:
```html
<el-sub-menu v-if="canReadGateway" index="gateway">
  <template #title>
    <el-icon><Connection /></el-icon>
    <span>{{ t('gateway.title') }}</span>
  </template>
  <el-menu-item v-if="canReadGatewayApi" index="/gateway/apis">
    {{ t('gateway.apis') }}
  </el-menu-item>
  <el-menu-item v-if="canReadGatewayApp" index="/gateway/applications">
    {{ t('gateway.applications') }}
  </el-menu-item>
  <el-menu-item v-if="canReadGatewayRelease" index="/gateway/releases">
    {{ t('gateway.releases') }}
  </el-menu-item>
  <el-menu-item v-if="canReadGatewayAudit" index="/gateway/audit">
    {{ t('gateway.audit') }}
  </el-menu-item>
</el-sub-menu>
```

Add computed permission checks in `<script>`:
```typescript
const canReadGateway = computed(() =>
  hasPermission(PERMISSIONS.GATEWAY_API_READ) ||
  hasPermission(PERMISSIONS.GATEWAY_APP_READ) ||
  hasPermission(PERMISSIONS.GATEWAY_RELEASE_READ) ||
  hasPermission(PERMISSIONS.GATEWAY_AUDIT_READ)
)
const canReadGatewayApi = computed(() => hasPermission(PERMISSIONS.GATEWAY_API_READ))
// ... etc
```

Import `Connection` icon from `@element-plus/icons-vue`.

---

## Step 12: Frontend — API Layer

**Directory:** `frontend/admin-center/src/domains/gateway/`

### `api/gateway.ts`
API client using the existing axios instance pattern:

```typescript
import request from '@/api/request'

// API Definitions
export function createApi(data) { return request.post('/gateway/apis', data) }
export function listApis(params) { return request.get('/gateway/apis', { params }) }
export function getApi(apiId) { return request.get(`/gateway/apis/${apiId}`) }
export function updateApi(apiId, data) { return request.put(`/gateway/apis/${apiId}`, data) }
export function createApiVersion(apiId, data) { return request.post(`/gateway/apis/${apiId}/versions`, data) }
export function importOpenApi(data) { return request.post('/gateway/apis/import-openapi', data) }

// Applications
export function createApp(data) { return request.post('/gateway/applications', data) }
export function listApps(params) { return request.get('/gateway/applications', { params }) }
export function getApp(appId) { return request.get(`/gateway/applications/${appId}`) }
export function updateApp(appId, data) { return request.put(`/gateway/applications/${appId}`, data) }
export function createCredential(appId, data) { return request.post(`/gateway/applications/${appId}/credentials`, data) }

// Releases
export function createRelease(data) { return request.post('/gateway/releases', data) }
export function submitTesting(releaseId) { return request.post(`/gateway/releases/${releaseId}/submit-testing`) }
export function publishRelease(releaseId) { return request.post(`/gateway/releases/${releaseId}/publish`) }
export function rollbackRelease(releaseId, data) { return request.post(`/gateway/releases/${releaseId}/rollback`, data) }
export function getRelease(releaseId) { return request.get(`/gateway/releases/${releaseId}`) }
export function getReleaseHistory(releaseId) { return request.get(`/gateway/releases/${releaseId}/history`) }

// Audit
export function listAuditLogs(params) { return request.get('/gateway/audit', { params }) }
```

### `types/gateway.ts`
TypeScript interfaces:
```typescript
export interface ApiDefinition { id: number; apiCode: string; name: string; domain: string; basePath: string; protocol: string; status: string; ... }
export interface ApiVersion { id: number; apiDefinitionId: number; version: string; lifecycleStatus: string; upstreamRef: string; ... }
export interface Application { id: number; appCode: string; name: string; owner: string; status: string; ... }
export interface GatewayRelease { id: number; releaseNo: string; state: ReleaseState; environmentCode: string; snapshotJson: any; ... }
export type ReleaseState = 'DRAFT' | 'TESTING' | 'PUBLISHED' | 'ROLLED_BACK'
export interface PublishHistory { id: number; operation: string; result: string; runtimeRevision: string; ... }
export interface GatewayAuditLog { id: number; action: string; resourceType: string; resourceId: string; ... }
```

---

## Step 13: Frontend — Pages

### 13a. API Management Page
**File:** `frontend/admin-center/src/domains/gateway/pages/apis/index.vue`

- Table listing APIs with columns: apiCode, name, domain, basePath, status, created_at
- Search bar with keyword + status filter
- "Create API" button → dialog form (apiCode, name, domain, basePath, protocol)
- Click row → API detail sub-page showing:
  - API metadata (editable)
  - API versions list
  - "Create Version" form (version label, upstream ref, OpenAPI doc)
  - Per-version: access policies and traffic policies
- "Import OpenAPI" button → upload/dialog for OpenAPI spec

### 13b. Application Management Page
**File:** `frontend/admin-center/src/domains/gateway/pages/applications/index.vue`

- Table listing apps with columns: appCode, name, owner, status, created_at
- Search bar with keyword filter
- "Create Application" button → dialog (appCode, name, owner)
- Click row → application detail showing:
  - App metadata (editable)
  - Credentials list
  - "Create Credential" button → dialog (type: API_KEY/JWT/OAUTH2, display name, expiry)

### 13c. Release Management Page
**File:** `frontend/admin-center/src/domains/gateway/pages/releases/index.vue`

- Table listing releases with columns: releaseNo, environment, state, description, created_by, created_at
- State displayed as colored tag (DRAFT=gray, TESTING=blue, PUBLISHED=green, ROLLED_BACK=red)
- "Create Release" button → dialog (environment, name, select API versions, description)
- Row actions:
  - DRAFT → "Submit Testing" button
  - TESTING → "Publish" button (with confirmation)
  - PUBLISHED → "Rollback" button (with reason input)
- Click row → release detail showing:
  - Release metadata and snapshot content
  - Publish history timeline

### 13d. Gateway Audit Page
**File:** `frontend/admin-center/src/domains/gateway/pages/audit/index.vue`

- Table listing audit logs with columns: action, resource_type, resource_id, operator, result, created_at
- Filters: resource type dropdown, action dropdown, date range
- Click row → detail dialog showing before/after JSON diff

---

## Step 14: Build & Verify

```bash
# From repository root
cd "/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun"

# Frontend
cd frontend/admin-center && npx vite build && cd ../..

# Backend (Maven build)
cd backend && mvn -pl admin-center -am package -DskipTests && cd ..

# Docker
docker compose -f deploy/environments/dev/docker-compose.dev.yml build admin-center admin-center-frontend
docker compose -f deploy/environments/dev/docker-compose.dev.yml up -d --force-recreate admin-center admin-center-frontend
```

---

## Files to Create/Modify

### Database (1 new)
- `deploy/init-scripts/00-schema/36-gateway-governance-schema.sql` — NEW

### Backend (~25 new, 1 modified)
- `backend/admin-center/src/main/java/com/admin/entity/gateway/` — 9 NEW entities
- `backend/admin-center/src/main/java/com/admin/repository/gateway/` — 9 NEW repositories
- `backend/admin-center/src/main/java/com/admin/adapter/gateway/spi/GatewayProvider.java` — NEW
- `backend/admin-center/src/main/java/com/admin/adapter/gateway/kong/KongGatewayProvider.java` — NEW
- `backend/admin-center/src/main/java/com/admin/adapter/gateway/dto/` — 3 NEW DTOs
- `backend/admin-center/src/main/java/com/admin/service/gateway/` — 4 NEW services
- `backend/admin-center/src/main/java/com/admin/controller/gateway/` — 4 NEW controllers
- `backend/admin-center/src/main/java/com/admin/audit/AdminAuditAspect.java` — MODIFIED (add 3 pointcuts)

### Frontend (~10 new, 4 modified)
- `frontend/admin-center/src/utils/permission.ts` — MODIFIED
- `frontend/admin-center/src/router/index.ts` — MODIFIED
- `frontend/admin-center/src/layouts/AdminLayout.vue` — MODIFIED
- `frontend/admin-center/src/i18n/locales/zh-CN.ts` — MODIFIED
- `frontend/admin-center/src/i18n/locales/zh-TW.ts` — MODIFIED
- `frontend/admin-center/src/i18n/locales/en.ts` — MODIFIED
- `frontend/admin-center/src/domains/gateway/api/gateway.ts` — NEW
- `frontend/admin-center/src/domains/gateway/types/gateway.ts` — NEW
- `frontend/admin-center/src/domains/gateway/pages/apis/index.vue` — NEW
- `frontend/admin-center/src/domains/gateway/pages/applications/index.vue` — NEW
- `frontend/admin-center/src/domains/gateway/pages/releases/index.vue` — NEW
- `frontend/admin-center/src/domains/gateway/pages/audit/index.vue` — NEW

---

## Risks and Mitigations

1. **Backend path prefix** — `admin-center` already uses `context-path: /api/v1/admin`; gateway controllers must use `/gateway/**` (not duplicate `/api/v1/admin` in controller mapping).
2. **Tenant context** — Verify how tenant_id flows in current JWT/security context. Gateway queries must filter by tenant.
3. **Kong Admin API connectivity** — Kong may not be available in dev env. Phase 1 must support `stub` adapter mode for no-Kong testing.
4. **Permission migration** — New gateway permission keys must be seeded into `sys_permissions` and bound in `sys_role_permissions` (at least SYS_ADMIN) in init scripts.

---

## Estimated Effort

- DB migration: 0.5 day
- Backend entities + repos: 1 day
- Backend services + controllers: 2 days
- Backend adapter + audit: 1 day
- Frontend permission + routing + i18n: 0.5 day
- Frontend pages (4 pages): 3 days
- Integration testing + fixes: 2 days
- **Total: ~10 days** (aligns with 2-week Phase 1 plan)
