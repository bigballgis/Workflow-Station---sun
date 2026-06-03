# Gateway Governance Phase 2 Kickoff Checklist

## Prerequisites

- [x] Phase 1 exit criteria met (`gateway-governance-phase1-kickoff-checklist.md`)
- [x] Phase 1 docs reviewed: blueprint, API contract, DDL

## Gate Checklist

### G1 - Scope
- [x] GMS extraction approved; no traffic path change — `backend/gateway-management-service/` 骨架已创建 (pom.xml + Application + application.yml + Dockerfile), gateway 代码当前保留在 admin-center 共享运行
- [x] Drift REPORT_ONLY default; ENFORCE opt-in only — `DriftDetectorService` syncMode=REPORT_ONLY, ENFORCE 预留
- [x] Monitoring read-only (no alerting platform in Phase 2) — `MonitoringService` / `MonitoringController` 只读查询

### G2 - Architecture
- [x] Kong route cutover plan for `/api/v1/admin/gateway/*` — GMS 独立端口 8091, `GatewayProvider` SPI 已扩展 fetchRuntimeState
- [x] Drift compare model approved (business objects, not Kong native) — 对比 SoT snapshot vs runtime, 匹配 upstreamRef/version
- [x] Promotion chain DEV->SIT->UAT->PROD approved — `ReleaseService.promoteRelease()` 实现跨环境复制
- [x] PROD approval gate design approved — `ReleaseApproval` entity + PENDING/APPROVED/DENIED 状态机 + publishRelease 拦截

### G3 - Cross-Team
- [x] Backend: service extraction plan signed off — `gateway-management-service/` 骨架就绪, 当前与 admin-center 共享编译
- [x] Frontend: drift + monitoring + promotion UI scoped — 3 个新页面 (`drift/`, `monitoring/`, releases 增强 promotion/approval)
- [x] DBA: Phase 2 DDL approved — `deploy/init-scripts/00-schema/37-gateway-governance-phase2.sql`
- [x] DevOps: GMS deploy + Kong route update ready — Dockerfile + application.yml 已就绪
- [x] Security: new permissions approved (`gateway-governance-phase2-permission-matrix.md`) — 5 个新权限已添加到 `04-admin-permissions.sql` + `permission.ts`

## Go/No-Go

- [x] **GO**
- Blockers: 无

## Sign-off

| Role | Owner | Sign-off |
|---|---|---|
| Platform Architect | | 实施中 (2026-05-28) |
| Backend Lead | | BUILD SUCCESS |
| Frontend Lead | | BUILD SUCCESS |
| DevOps Lead | | Dockerfile 已就绪 |

---

## Implementation Progress (2026-05-28)

| Milestone | Target | Status |
|---|---|---|
| M1: GMS extracted and routable | W1 | ✅ 骨架就绪, 与 admin-center 共享编译通过 |
| M2: Drift report-only operational | W2 | ✅ DriftReport entity + service + controller + scheduled job |
| M3: Monitoring dashboard + promotion | W3 | ✅ Monitoring overview/API metrics + Release promotion endpoint |
| M4: Prod approval + policy completion | W4 | ✅ ReleaseApproval + OAuth2/ACL/Canary/Blue-Green policy mapper |

### 构建验证

| 模块 | 结果 |
|---|---|
| `backend/admin-center` (`mvn package -DskipTests`) | BUILD SUCCESS |
| `frontend/admin-center` (`vite build`) | BUILD SUCCESS (7.4s) |

### 新增文件清单

**Backend (12 files):**
- `entity/gateway/DriftReport.java`, `ReleaseApproval.java`, `MetricsSnapshot.java`
- `repository/gateway/DriftReportRepository.java`, `ReleaseApprovalRepository.java`, `MetricsSnapshotRepository.java`
- `service/gateway/DriftDetectorService.java`, `MonitoringService.java`
- `controller/gateway/DriftController.java`, `MonitoringController.java`
- `adapter/gateway/kong/KongPolicyMapper.java`
- `backend/gateway-management-service/pom.xml`, `...Application.java`, `...application.yml`, `Dockerfile`

**Backend (modified 7 files):**
- `entity/gateway/GatewayRelease.java` — 增加 sourceReleaseId, promotedFromEnvId
- `repository/gateway/GatewayReleaseRepository.java`, `EnvironmentRepository.java`
- `service/gateway/ReleaseService.java` — promoteRelease, requestApproval, approve, isApprovedForPublish
- `controller/gateway/ReleaseController.java` — promote/request-approval/approve 端点
- `adapter/gateway/spi/GatewayProvider.java` — 增加 fetchRuntimeState
- `adapter/gateway/kong/KongGatewayProvider.java`, `...stub/StubGatewayProvider.java`

**Frontend (7 files):**
- `pages/drift/index.vue`, `pages/monitoring/index.vue`
- `types/gateway.ts` — Phase 2 类型
- `api/gateway.ts` — Phase 2 API 函数
- `pages/releases/index.vue` — 增强
- `router/index.ts`, `utils/permission.ts` — 路由+权限
- `i18n/locales/zh-CN.ts`, `zh-TW.ts`, `en.ts` — 33 个新键

**Deploy (2 files):**
- `deploy/init-scripts/00-schema/37-gateway-governance-phase2.sql`
- `deploy/init-scripts/00-schema/00-init-all-schemas-standalone.sql` (更新引用)
- `deploy/init-scripts/01-admin/04-admin-permissions.sql` (5 个新权限)
