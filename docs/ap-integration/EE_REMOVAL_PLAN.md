# Activepieces EE 剥离与 CE 边界实施方案

> ## ⚠️ 归档：本文描述 0.84 方案，其源码树已删除
>
> 本文的对象是仓库根的 `activepieces/`（Activepieces 0.84.0 硬分叉）。**该目录已于 2026-08-14
> 删除**——0.88 重构（`automation/`）交付并验证后，它作为参考实现的使命结束。凡本文写成
> `activepieces/...` 的路径，**在工作树里都已不存在**；要看当年的源码走 git 历史：
> `git show de4f6469:activepieces/<path>`（0.84 vendor 基线）、
> `git checkout de4f6469 -- activepieces/<dir>`（取整目录），
> 或 `git show 4635f7950:activepieces/<path>`（删除前最后一个提交）。
>
> **现行真源：`automation/`（0.88 vendor 时即为 ee-removed 树）；剥离结果见 `automation/hermes/TRIM_LOG.md`**
>
> 本文**不逐句改写**，作为决策依据的历史记录原样保留。


> **Document 3.5 / 10** — 由 [D3](DECISIONS.md#d3) 新增。
> 前置：[DEPENDENCY_MAP.md §2.2](DEPENDENCY_MAP.md)（EE 依赖实测）。
> 后继：本文档是 **Document 4 的硬前置**（经 [AG-EE](OPEN_GATES.md)）。
> 状态：**§2 / §3(Phase 0) / §4 / §5 ✅ 已完成（2026-07-23）；剩余：§1 两向清单补齐 + §4.7 八条未核实项补测 + §6 CI Guard 落地**。
> 日期：2026-07-23

---

## 0. 为什么需要独立文档

EE 剥离已不是"模块处置"，而是独立工程，涵盖：许可边界 + 代码依赖分析 + Core→EE 反向引用 +
EE API 替代 + 数据库模型替代 + 前端/运行时/构建期依赖替代 + 测试替代 + CI Guard。

**约束基线（[DECISIONS.md](DECISIONS.md)）**：
`No EE Business Features / No EE Proprietary Runtime Capability`——
**不是**"`packages/ee/` 目录不进构建"（该表述已作废：CE 有 105 处 import `app/ee/**`，排除即编译失败）。

---

## 1. EE 依赖清单（六向）✅ 部分实测

| 方向 | 现状 | 数据来源 |
|---|---|---|
| **Core → EE** | **37 文件 / 约 105 条 import**（清单见 DEPENDENCY_MAP §2.2.4） | ✅ 实测 |
| **Web → EE** | 3 文件 + 3 处构建配置（`ee-embed-sdk`） | ✅ 实测 |
| **Runtime → EE** | **13 条 EE-4 硬依赖**（见 §4） | ✅ 实测 |
| **Build → EE** | `getEntities()` 19 个 ee 实体；迁移数组 20 个 ee 迁移；workspaces 含 `packages/ee/embed-sdk`；`tsconfig.base.json:20-21` 两个死别名 | ✅ 实测 |
| **EE → Core** | ⏳ **待补**（EE 模块反向依赖 CE 的面，影响删除顺序） | — |
| **Test → EE** | ⏳ **待补**（`test/integration/ee/`、`test/unit/app/ee/`、`tests-e2e/scenarios/ee/`） | — |

**零依赖已确认**：`packages/pieces`、`server/engine`、`server/utils` → ee 均为 **0**。

### 1.1 许可边界（原文引述，**非法律解释**）

**条款未覆盖的三处 "ee" 目录**（按条款文本属 MIT，**须法务确认**）：
`packages/shared/src/lib/ee/**`（40 文件，含 `rolePermissions`）、
`server/worker/src/lib/execute/jobs/ee/`（3 文件）、各测试目录下的 `ee/`。

`app/ee/` 下 172 个 `.ts` **无任何许可头**——许可完全依赖根 LICENSE 的路径条款。

> **合规评估状态**：[D4](DECISIONS.md#d4) 确认 **尚未启动**。在得到正式意见前按重实现推进。

---

## 2. ✅ 逐条替代方案（37 个 CE 文件 / 105 条 import，按功能组裁定）

> 处置口径：**Delete**（删除，CE 不需要）｜**Stub**（保留调用点，无副作用实现）｜
> **Replace with CE**（改走 CE 已有能力）｜**Reimplement**（按最小契约重写，**不复制 EE 代码**）｜
> **HERMES-native**（由 HERMES 既有能力提供）。分类依据见 §3.2 能力盘点。

### 2.1 按功能组裁定

| # | 功能组 | CE 文件（相对 `packages/server/api/src/app/`） | 处置 | 最小契约 / 说明 |
|---|---|---|---|---|
| G1 | **模块注册（EE 分支）** | `app.ts`（36 条 import） | **Delete** | 删 `case CLOUD` / `case ENTERPRISE` 两个分支（`:243-305`）+ 对应 import。**同时删无条件注册的 `alertsModule`(:203)、`licenseKeysModule`(:208)**（C11/C12） |
| G2 | **授权判定** | `core/security/v2/authz/authorize.ts:102`、`core/websockets.service.ts:105` | **Reimplement (b)** | `assertProjectAccess({principal, permission, projectId})`：**优先只读 HERMES JWT 的 `roles[]`/`permissions[]`**（结论 2，零回调）；ENGINE 校验 `principal.projectId`；SERVICE 校验同 platform。**两处必须同时改** |
| G3 | **API Key 认证** | `core/security/v2/authn/authenticate.ts:24` | **Delete** | CE 无 API key（`apiKeysEnabled=false`）；删 `Bearer sk-` 分支 |
| G4 | **Secret 解析** | `app-connection-service.ts:93,365,402`、`app-connection-worker-controller.ts:38`、`oauth2-util.ts:122`、`credentials-oauth2-service.ts:123` | **Stub (c)** | `resolveObject(v)→v`、`resolveString(k)→k`、`containsSecretManagerReference()→false`。HERMES 无 HTTP 侧 secret manager（C9），AP 保留 `AP_ENCRYPTION_KEY` |
| G5 | **并发池 / worker group** | `rate-limiter-interceptor.ts:53,63,112`、`job-queue.ts:177`、`machine-service.ts:100`、`core/canary/canary-routing.middleware.ts:19` | **Stub (c)** | `getProjectPoolId()→null`、`getPoolLimit()→null`、`getWorkerGroupId()→null`、`isCanaryPlatform()→false`。HERMES 无任何租户配额（C13） |
| G6 | **project / platform 控制器** | `platform/platform.controller.ts:21-24`、`platform.service.ts:30,31`、`user/user-service.ts:175,187` | **Reimplement (c)** | `/v1/projects`、`/v1/platforms`、`/v1/worker/project` + `deletePersonalProjectForUser`。**v1 单 platform + 单共享 project ⇒ 大幅简化**（无多 project CRUD / 配额 / 成员管理，C6/C7） |
| G7 | **用户查询（EE 分支）** | `user/user-service.ts:28,29,279` | **Delete 分支** | `getUsersForProject` 的 CE 分支已在 `:276` 提前 return，直接删 `:279-280` 及 import |
| G8 | **`/v1/users` 控制器** | `ee/users/user.module.ts`（经 `app.ts:211`） | **Reimplement (c)** | 收窄为**影子记录 CRUD**（身份源在 HERMES，C3） |
| G9 | **邀请体系** | `user-invitations/user-invitation.module.ts:27-29`、`user-invitation.service.ts:6-9` | **Delete / 简化** | AG-06 已裁决走内部 provisioning 端点、**不经邀请链路**；仅保留 `provisionUserInvitation` 中被复用的最小逻辑（若有） |
| G10 | **告警** | `flows/flow-run/flow-run-hooks.ts:5,36` | **Delete** | `admin_alerts` 是空壳、无下游消费者（C11） |
| G11 | **计划/配额门禁** | `flows/flow/flow.controller.ts:98,108`、`platform.service.ts:277,289`、`mcp/mcp-permissions.ts:18` | **Delete** | 上游或下游已有 CE 短路，把 CE 分支提为唯一分支 |
| G12 | **piece 过滤（EE）** | `pieces/metadata/piece-metadata-service.ts:26,82`、`pieces/metadata/utils/index.ts:3,23` | **Delete** | EE 的 piece 白/黑名单过滤；我方用构建期预装白名单（FR-F01） |
| G13 | **邮件发送** | `flags/flag.service.ts:306`、`user/badges/badge-service.ts:6` | **Stub / HERMES-native (b)** | `isSmtpConfigured()→false`（AG-06 要求 SMTP 保持未配置无关，此处是 AP 自身发信）；badges 整体 Delete |
| G14 | **flags 中的 EE 项** | `flags/flag.service.ts:6,7,166` | **需补测（AG-EE.5）** | `federatedAuthnService.getThirdPartyRedirectUrl` 是否有 edition 门未核实；影响前端启动 |
| G15 | **实体注册** | `database/database-connection.ts:10-28`（19 条） | **Reimplement 3 + Delete 16** | 依 MIT 区 DDL 重写 `ProjectRole`/`ProjectMember`/`ConcurrencyPool` 三个 EntitySchema；其余 16 个删（**须先核 FK**，已知 `user_invitation → project_role`） |
| G16 | **迁移注册** | `database/postgres-connection.ts:5-24`（20 条） | **Delete（方案 A）** | 只支持全新库（X-2/X-3 一次性 air-gapped 集成），从 MIT 基线重建 |
| G17 | **role seed** | `database/seeds/role-seed.ts:3` | **保留** | import 改指向 G15 重写后的实体 |
| G18 | **其余 EE 功能面** | `tables/table/{controller,service}`、`template/{controller,service}`、`analytics/*`、`mcp/oauth/*`、`helper/embed-security.ts`、`ai/ai-provider-service.ts:20,21`、`core/canary/*` | **Delete / 补测** | 多数属 EE-2 直接删；`ai-provider-service` 与 `embed-security` 需确认是否触及我方 AI Generate 链路（**AG-EE.5**） |
| G19 | **前端 embed-sdk** | `web/src/app/routes/embed/*`（2 文件）、`components/custom/home-button.tsx:1,22` | **Delete + 本地枚举** | embed 路由整删；`home-button` 的 `ActivepiecesClientEventName` 用本地常量替换（EE-1） |
| G20 | **死别名** | `tsconfig.base.json:20-21` | **Delete** | 指向不存在目录、零引用 |

### 2.2 待补的两向清单（AG-EE.1）

| 方向 | 状态 | 影响 |
|---|---|---|
| **EE → Core** | ⏳ 待枚举 | 影响**删除顺序**：若 EE 模块反向依赖 CE，删除时需先断反向边 |
| **Test → EE** | ⏳ 待枚举 | `test/integration/ee/`、`test/unit/app/ee/`、`tests-e2e/scenarios/ee/` —— 随对应功能一并删；不影响生产构建 |

---

## 3. ⭐ Integration Phase 0 — HERMES Capability Inventory（**前置阶段，非本文档的一节**）

> [D3](DECISIONS.md#d3) 强化裁决：**本节升格为 Integration Phase 0**，是 §2/§4 的**前置阶段**。
>
> **执行顺序（不可颠倒）**：
> ```
> HERMES Capability Inventory → EE Logic Mapping → Delete / Reuse / Replace 裁定
>                                    ↓
>                        再决定哪些 AP 代码真正需要集成
> ```
> **绝不能先按 AP 原架构实现，再发现 HERMES 已经有同等能力。** 这是集成项目最典型的返工来源。

### 3.1 ⚠️ 三分类（**不是二分类** —— 漏掉中间类会导致"以为能复用，实际还得写"）

| 分类 | 含义 | AP 侧处置 |
|---|---|---|
| **HERMES-owned** | HERMES 完全承担该能力 | **Delete** |
| **HERMES-governed, AP-local** | **策略/数据源在 HERMES，但 AP 内部仍需最小本地判定实现** | **最小实现 + 从 HERMES 取策略** |
| **AP-owned** | HERMES 无对应能力 | **保留 / 重实现** |

**为什么必须有中间类**：典型是 `authz/authorize.ts:102`——AP **每个 project 级请求**都要做一次
授权判定。即使**决策来源**是 HERMES，**判定点**也不可能每次回调 HERMES（延迟不可接受，
且 AP↔HERMES 之间没有这种同步调用通道）。所以 AP 侧仍需一个本地最小实现。
若按二分类记成"HERMES 已有 RBAC → Delete"，实施时会发现 AP 跑不起来，然后临时补一个没设计过的实现。

### 3.2 ✅ 能力盘点表（2026-07-23 完成，三条盘点线实测）

图例：**(a)** HERMES-owned → AP 侧 Delete ｜ **(b)** HERMES-governed·AP-local → AP 保留最小本地实现、策略取自 HERMES ｜ **(c)** AP-owned → 保留/重实现

| # | AP 能力 | HERMES 是否已有 | 分类 | AP 侧处置 | 关键证据 |
|---|---|---|---|---|---|
| C1 | **Authentication**（sign-up/sign-in/OTP/密码重置） | ✅ 完整：LDAP 权威 + 本地 BCrypt 回退 + DSP 免密 | **(a)** | **Delete 全部** | `PlatformSsoService`、`LdapAuthenticator`、`DspSsoService` |
| C2 | **SSO / SAML** | ✅ 自建 authorization-code 流（非 OIDC/SAML） | **(a)** | **Delete AP SSO/SAML** | `PlatformSsoService:50-186`、`SsoInternalController` |
| C3 | **User 模型** | ✅ `sys_users`（扁平一级，无 identity/user 两级） | **(a)** 身份源<br>**(c)** AP 影子记录 | AP `user`/`user_identity` 降为**影子记录**，身份源在 HERMES | `platform-security/entity/User.java:31` |
| C4 | **授权判定（每请求 allow/deny）** | ⚠️ **无统一内核**——四套各自实现；看似统一的 `PermissionService` **是死代码** | **(b)** | **保留 AP 本地判定**（Q4a 补丁方向被印证正确） | `PermissionServiceImpl` 的 `@ConditionalOnBean(PermissionRepository)` 无生产实现 |
| C5 | **Role/Permission 数据** | ✅ `sys_roles`/`sys_permissions`（模块级粒度，11 个平台权限点） | **(b)** | 角色数据取自 HERMES；**AP 权限码自持** + 建 `ap_rbac_mapping` | 先例：`sys_developer_role_permissions`(DW)、`bi_rbac_mapping`(Superset) |
| C6 | **Platform（顶级租户）** | ✅ **BU**（无限层级树） | **(a)/(b)** | v1 单 platform，BU 作映射源 | `BusinessUnit.java:25`（AP platform 是扁平单层） |
| C7 | **Project（工作区）** | ❌ **无对应物**——FU 是"可发布的版本化应用包"，语义不同 | **(c)** | **保留 AP project**（v1 单共享 project，Q4a 已裁决） | `sys_function_units` 无 `business_unit_id` |
| C8 | **project-role / project-member** | ❌ 无 | **(c)** | 重实现（EE 剥离范围） | — |
| C9 | **Secret 管理** | ⚠️ 仅 AES 原语（`AesEncryptionService`，**Java 库无 HTTP 面**）；无 KMS/Vault/轮换 | **(c)** 加密自持<br>**(b)** 密钥分发 | **保留 `AP_ENCRYPTION_KEY`**；密钥经 K8s Secret 供给 | `platform-security/.../AesEncryptionService.java:23`；⚠️ 勿用 `ConfigurationEncryptionService`（疑死代码） |
| C10 | **Audit** | ⚠️ 有规范+`admin_audit_logs`，但**只覆盖 admin-center AOP、无写入 API** | **(b)** | AP 本地写审计；**需新建 HERMES 汇聚接口** | `SecurityAuditController` 只有查询/导出；Kafka 无 audit topic |
| C11 | **Alerts** | ⚠️ **空壳**：表+API 齐全，但 `createAlert()` **零调用方**、无规则求值器、`notify_channels` 无人读 | **Delete** | v1 不做 | `SystemMonitorComponent:146` 无生产调用 |
| C12 | **Billing / Plan / License** | ❌ 零（内部系统 X-1，`QUOTA_EXCEEDED` 定义但零引用） | **Delete** | Delete（注意 CE 反向引用需 stub） | 全仓 grep 零业务命中 |
| C13 | **并发/配额** | ⚠️ 仅 Kong **按 IP** 限流；**无任何租户/BU 配额** | **(c)** | **保留 AP `concurrencyPool`** | 无配额表；`QUOTA_EXCEEDED` 零引用 |
| C14 | **邮件发送** | ✅ 三套实现 + **内部 HTTP 取凭据接口** | **(b)** | AP 用自己的 SMTP piece，凭据经 HTTP 取 | `/internal/function-units/{id}/connections/{id}/credentials` ⚠️**该端点当前无鉴权** |
| C15 | **站内信/通知** | ✅ Kafka → user-portal → WebSocket | **(b)** | AP 可投 Kafka（新增依赖边，Doc4 记录） | `NotificationEvent` + `KafkaTopics` |
| C16 | **日志集中采集** | ❌ **完全没有**（无 filebeat/fluentd/ELK/Loki/OTel，K8s 无日志 sidecar） | **(c)** + **平台缺口** | AP 日志止于 `kubectl logs` | 全仓 grep 零命中 |
| C17 | **指标（Prometheus）** | ⚠️ 端点有（Micrometer），**仓库内无采集端配置** | **(c)** | AP CE 无 Prometheus 端点，不强求对齐 | `deploy/k8s/` 无 ServiceMonitor |
| C18 | **DB 迁移工具** | ❌ **无迁移工具**（Flyway 2026-06 已清退，只剩 append-only init-scripts） | **(c)** | AP 用 TypeORM 自带迁移，**零冲突** | `docs/schema-and-migration.md` §2 |

### 3.3 ✅ Phase 0 的六条关键结论

1. **"HERMES 已有 RBAC → AP 侧 Delete" 被明确证伪**（C4）。HERMES 没有统一授权内核，
   而且**它自己内部就是"远程取策略 + 本地缓存 5min + 本地判定"**（`FunctionUnitAccessComponent`、
   `DeveloperPermissionChecker` 两处独立实现同一模式）。**AP 照搬这个既有模式即可**，
   这也印证了 [Q4a](DECISIONS.md#q4a) 的 core 层本地补丁方向。
2. **🎁 更省事的路径**：HERMES **JWT 里已经带 `roles[]` / `permissions[]` / `activeBusinessUnitId`**
   ⇒ AP 每请求判定**只读 JWT、零回调**；仅资源级判定才调 REST。
3. **AP 的认证域可整体删除**（C1/C2）——AP 只需一个**入站信任端点**：接受 HERMES 已证实的身份，
   幂等建/取本地 AP user、签 AP 自己的 token。
4. **权限码不要塞进 `sys_permissions`**（C5）——平台已接受"子系统自带权限枚举 + 映射表"
   （DW、Superset 两个先例），AP 建 `ap_rbac_mapping` 是最低摩擦。
5. **独立 schema 是与既有做法一致的**（C18）——**n8n 已用 `n8n` schema、Superset 已用 `superset` schema**，
   当前 AP 与平台表混在 `public` 反而是异类。`CREATE SCHEMA activepieces` 应作为**新的 append-only
   init 脚本**（如 `00-schema/56-*.sql`）才符合平台规则。
6. **三个必须新建的 HERMES 能力**（都不在原 13 条 EE-4 内，是 Phase 0 反推出来的）：
   - **内部身份供给端点**（包装已有的 `issueCodeForUser`/nonce，受 `X-Platform-Sso-Internal` 保护）→ AG-06.2；
   - **用户状态变更事件通道**（Kafka `platform.user.events` 或同步回调）→ AG-06.3，**目前零基础设施**；
   - **审计写入接口**（`POST /internal/audit/logs` 或 Kafka audit topic）→ 否则 AP 操作不进平台审计。

### 3.4 ⚠️ Phase 0 附带发现的平台既有问题（不属本次集成，但影响集成设计）

| # | 发现 | 对集成的影响 |
|---|---|---|
| P-1 | **`ServiceCallAuthenticationFilter`**：无 JWT 时带 `X-User-Id`/`X-Username` 头即被认证为该用户；`UserController` **无端点级授权**；**Kong 无 jwt 插件**（模板 `:207` 自述"不做 JWT 验证"） | **AP 执行用户编写的 flow 且自带 http piece** ⇒ AP egress 白名单**绝不能放行 admin-center 管理端点**（AG-05） |
| P-2 | `/api/v1/admin/internal/**`（含**明文 SMTP 凭据**接口）无鉴权，且 Kong 有 `/api/v1/admin` 前缀路由 | C14 复用前必须先加服务间鉴权（仿 `SSO_INTERNAL_TOKEN`） |
| P-3 | **K8s 设了 `SPRING_PROFILES_ACTIVE=uat` 但仓库无 `application-uat.yml`** ⇒ `application-docker.yml` 的覆盖在 K8s **全部不生效** | 直接后果：**K8s 上 DW、workflow-engine、AP 三者共用 Redis DB 0**。过渡期建议先给 AP 加 `AP_REDIS_DB=9` |
| P-4 | `deploy/k8s/redis.yaml` 用 Istio Gateway 把 **6379 暴露到集群外** | AP 独立 Redis 清单**不要照抄这段** |
| P-5 | 死代码勿当资产：`platform-security.PermissionService`、`admin_data_permission_rules`/`admin_column_permissions`（行/列级权限）、`ConfigurationEncryptionService`/`SecureCredentialManager` | 盘点时已排除 |

---

## 4. ✅ 工作量精确拆分（Q8 成本判断的依据）

### 4.0 ⚠️ 首先澄清一个口径问题

D3 要求把"1000–1300 行"拆成 EE removal / Bun→pnpm / Vue 集成 / offline / RBAC / HERMES API / 安全修复。
**核实后发现：这个数字本身只覆盖 EE 剥离（P1–P6 / M1–M3 / N1–N11），从未包含其余工作流。**
其余是**独立且额外**的工作包。下表按真实归属重新分解。

### 4.1 EE 剥离本体（= 原 1000–1300 行的真实构成）

| 工作项 | 行数 | 处置 | 依据 |
|---|---|---|---|
| 删死别名（G20） | ~2 | Delete | — |
| `user-service` EE 分支（G7） | ~3 | Delete | CE 本就短路 |
| Stub：并发池 / workerGroup / secretManagers（G4/G5） | ~35 | Stub | C9/C13 |
| embed-sdk 解耦（G19） | ~7 处 | Delete | EE-1 |
| role-seed import 改指向（G17） | ~1 | 保留 | — |
| `projectRoleService` 最小重写 | ~40 | Reimplement | 因 `user_invitation` FK |
| **授权判定替代（G2）** | **~60** | Reimplement | **JWT 已带 roles/permissions ⇒ 主路径零回调（结论 2）** |
| 3 个 EntitySchema 重写（G15） | ~120 | Reimplement | 依 MIT DDL（§4.2） |
| WS 路径改造（G2 第二切点） | ~60 | Reimplement | 漏则"能编辑不能调试" |
| `deletePersonalProjectForUser`（G6） | ~30 | Reimplement | — |
| SMTP / badges（G13） | ~80 | Stub/Delete | — |
| 8 个 edition-gated 调用点（G11/G12） | ~50 | Delete | — |
| `app.ts` 删 EE 分支（G1） | ~90 | Delete | — |
| `getEntities()` 裁剪 19→3（G15） | ~35 | Delete | 须先核 FK |
| 20 个 ee 迁移处置（G16） | ~20 | Delete（方案 A） | 只支持全新库 |
| `/v1/users` 重写（G8） | **~100 → 偏下限** | Reimplement | 影子记录即可（C3） |
| **`/v1/projects`+`/v1/platforms` 重写（G6）** | **~300–500 → 偏下限** | Reimplement | **单 platform + 单共享 project 大幅简化（C6/C7）** |
| G9/G14/G18 待补测项 | ⏳ 待定 | — | AG-EE.5 |
| **EE 剥离小计** | **≈ 1000–1300**（因 C3/C6/C7 简化，**预期落在下限附近**） | | 逐条裁定见 §4.4 |

### 4.2 其余工作包（**不属 EE 剥离，原估算未包含**）

| 工作包 | 估算 | 归属 Gate | 说明 |
|---|---|---|---|
| **Bun → pnpm 迁移** | 中等（锁重建 + Dockerfile 重写 + 脚本改写 + CI） | Q2 / Doc5 | 引擎/测试/turbo 与 bun 解耦，**主要风险在锁文件版本漂移**，非行数 |
| **Vue 宿主集成**（builder 抽取 + 8 注入切点 + Shadow DOM 打包） | **大**（AG-04 剩余部分） | AG-01/02/04 | 真实 builder 体量未验，是**前端侧最大不确定项** |
| **`ap-contracts` 裁剪层 + Codegen** | 中等 | AG-02.7 | D5/D2 引入 |
| **Offline / air-gapped 改造** | 小-中 | FR-F03A/B | 关运行时安装 + 构建期预装（既有 `deploy/pieces/` 可复用） |
| **沙箱/网络安全基线** | 中等 | AG-05 | isolate + STRICT + egress 白名单 |
| **⭐ HERMES 侧新建能力（Phase 0 反推，原完全不在估算内）** | | | |
| ・内部身份供给端点 | ~150 | AG-06.2 | 包装 `issueCodeForUser`/nonce + `X-Platform-Sso-Internal` + 审计 |
| ・**用户状态变更事件通道** | ~200 | AG-06.3 | **零基础设施**：Kafka `platform.user.events` topic + 事件类 + 发布点 + AP 侧消费 |
| ・审计写入接口 | ~100 | C10 | `POST /internal/audit/logs` 或 Kafka audit topic |
| ・SSO redeem 补 email 字段 | ~10 | AG-06 | AP 建账号需要 |
| ・`ap_rbac_mapping` 表 + 映射服务 | ~120 | C5 | 仿 `bi_rbac_mapping` |
| **HERMES 侧小计** | **≈ 580** | | **这是 Phase 0 最重要的成本发现** |

### 4.3 对 Q8 成本判断的结论

| 口径 | 结论 |
|---|---|
| EE 剥离本体 | ≈ 1000–1300 行，**因 Phase 0 的三处简化预期落在下限** |
| **HERMES 侧新建** | **≈ 580 行（原估算完全遗漏）** |
| 其余工作包 | Bun 迁移 / Vue 集成 / offline / 安全基线——**按工作包而非行数评估**，其中 **Vue 集成是最大不确定项** |
| **对 [Q8](DECISIONS.md#q8) 的影响** | **不改变裁决**。总量上升但结构清晰：EE 剥离是**一次性**的，HERMES 侧新建是**平台能力补齐**（本就该有，AP 只是触发器），二者都不产生"持续跟随上游"的义务 ⇒ ~~frozen baseline + controlled fork 仍是正确方向~~ **（2026-07-30：Q8 已由 [D12](DECISIONS.md#d12) 取代为硬分叉 + 深度裁剪；本行"不产生跟随义务"的论证在新裁决下同样成立，只是结论更进一步）** |

### 4.4 ✅ EE-4 硬依赖裁定（13 条，**已依 Phase 0 能力盘点定案**）

| # | ee 代码 | CE 触发点 | Phase 0 依据 | **裁定处置** |
|---|---|---|---|---|
| R1 | `rbacService` → `getRole` → `projectRoleService` | `authz/authorize.ts:102`（**每个 project 级 HTTP 请求**） | **C4**：HERMES 无统一授权内核，自身也是"本地判定+远程取策略" | **(b) Reimplement**：core 层最小判定（~60 行）。**优先只读 JWT 的 `roles[]`/`permissions[]`，零回调**（C4/结论 2）；资源级才调 REST |
| R2 | `projectMemberService.getRole` | `websockets.service.ts:105`（每次 WS 握手） | 同 R1 | **(b) 随 R1**——**两处切点必须同时改**（漏 WS = "能编辑不能调试"） |
| R3–R5 | `secretManagersService.resolve*` | 读连接 / **engine 取连接** / oauth2 ×2 | **C9**：HERMES 只有 Java 侧 AES 原语、无 HTTP 面；每次读连接回调 HERMES 是反模式 | **(c) Stub**：identity 函数（~15 行）。AP 保留 `AP_ENCRYPTION_KEY` |
| R6 | `concurrencyPoolService` | `rate-limiter-interceptor`（每次入队 job） | **C13**：HERMES **无任何租户/BU 配额**（`QUOTA_EXCEEDED` 零引用） | **(c) Stub**：返回 null = 无限制（~10 行） |
| R7 | `workerGroupService` | `job-queue.ts:177`、`machine-service.ts:100` | HERMES 无 worker group 概念 | **(c) Stub**：返回 null/false（~10 行） |
| R8 | **`/v1/projects`、`/v1/platforms`、`/v1/worker/project`** | `app.ts:307`（COMMUNITY 分支注册 ee 模块） | **C7**：FU ≠ AP project，**无对应物**；**C6**：BU 树 vs AP 扁平 platform | **(c) Reimplement（最大块 ~300–500 行）**。v1 单 platform + 单共享 project ⇒ **可大幅简化**（无需多 project CRUD/配额/成员管理） |
| R9 | `deletePersonalProjectForUser` | `user-service.ts:175,187` | 随 R8 | **(c) Reimplement**（~30 行） |
| R10 | `/v1/users`、`/v1/alerts`、`licenseKeysModule` | `app.ts:203,208,211` 无条件注册 | **C3** users 需保留影子记录；**C11** alerts 空壳；**C12** license 零 | `/v1/users`→**(c) Reimplement**（~100 行，收窄为影子记录 CRUD）；`/v1/alerts`+`licenseKeys`→**Delete** |
| R11 | role-seed（3 个默认角色） | 每次启动 | 随 R1/R5 | **(c) 保留**（若保留 `project_role` 表；因 `user_invitation` 有 FK） |
| R12 | 19 个 ee EntitySchema | `database-connection.ts` 顶层求值 | **§4.6**：表 DDL 在 MIT 迁移区 | **依 MIT DDL 重写 3 个**（project_role / project_member / concurrency_pool）+ **删 16 个**（须先核 FK） |
| R13 | 20 个 ee 迁移 | 无条件迁移数组 | **C18**：平台无迁移工具，AP TypeORM 零冲突；且**只支持全新库**（air-gapped 一次性集成 X-2/X-3） | **方案 A：直删**（不做老库升级，从 MIT 基线重建） |

**裁定后的分布**：Delete 3 项（alerts / licenseKeys / billing）｜Stub 4 项（R3-R5 / R6 / R7）｜
Reimplement 5 项（R1+R2 / R8 / R9 / R10-users / R12）｜保留 1 项（R11）。

### 4.5 Phase 0 带来的三处工作量下调

| 项 | 原估 | 下调后 | 依据 |
|---|---|---|---|
| R1/R2 授权判定 | ~60 行 | **~60 行但更简单** | JWT 已带 roles/permissions ⇒ 主路径零回调（结论 2） |
| R8 `/v1/projects` | ~300–500 行 | **偏下限** | v1 单 platform + 单共享 project ⇒ 无需多 project CRUD/配额/成员（C6/C7） |
| R10 `/v1/users` | ~100 行 | **偏下限** | 身份源在 HERMES，AP 侧只需影子记录（C3） |

**但同时新增三项 HERMES 侧工作**（Phase 0 反推，原不在估算内）：
内部身份供给端点 ｜ 用户状态变更事件通道（**零基础设施**）｜ 审计写入接口。

### 4.6 🎁 关键利好：表 DDL 在 MIT 区

`project_member` 表由 **MIT 迁移** `1764867709704-UnifyCommunityWithEnterprise.ts:25-34`
**专为 COMMUNITY** 创建；`project_role`（`1731424289830`）、`concurrency_pool`（`1775800000000`）同理。
而它们的 `EntitySchema`（TS）在 `ee/`。

⇒ **`EntitySchema` 可依 MIT 区 DDL 独立重写，无需复制任何 EE 文件。**

### 4.7 未核实项（8 条，影响估算精度）

R14–R21：`otpService`、`embedSubdomainService`、`flag.service.ts` 里的
`federatedAuthnService`/`smtpEmailSender`、`platformTemplateService`、`gitRepoService.onDeleted`、
`projectStateService`、`chatRpcHandlers` —— **须逐个读被调函数体确认有无内部 edition 短路**。
另：16 个 ee 实体是否有 CE 侧 FK（仅确认 `user_invitation → project_role` 一条）。

---

## 5. ✅ EE 剥离是否真的需要全部重实现 —— 不需要

依 Phase 0 三分类逐条归入（§2 已给出每组处置）：

| 分类 | 条目 | 占比判断 |
|---|---|---|
| **可由 HERMES 提供**（HERMES-owned → Delete） | C1 认证全域、C2 SSO/SAML、C11 alerts、C12 billing/license | **AP 认证域整体删除是最大的一笔节省** |
| **AP 需最小本地实现**（HERMES-governed·AP-local） | C4 授权判定、C5 权限码映射、C10 审计、C14 邮件凭据、C15 通知 | 核心是 G2 的 ~60 行；**JWT 已带 roles/permissions ⇒ 主路径零回调** |
| 可删除 | G1 EE 分支、G3 apiKey、G10 alerts、G11 计划门禁、G12 piece 过滤、G16 迁移、G18 多数、G19 embed、G20 死别名 | 条目最多 |
| 可配置禁用 | —（0.84 的 edition 门多在**下游函数体内**，不是配置开关，故实际都走 Delete） | 少 |
| 可替代（CE 已有能力） | G7（CE 分支已存在，直接提为唯一分支） | 少 |
| **必须重实现** | G2 授权、G6 project/platform、G8 users、G15 三个实体、`projectRoleService` | **仅 5 项**，且 G6/G8 因单 platform/影子记录**大幅简化** |

**结论**：13 条 EE-4 中 **Delete 3 / Stub 4 / Reimplement 5 / 保留 1**——
**"全部重实现"是错误预设**，真正必须重写的只有 5 项，其中 2 项因 Phase 0 结论显著收窄。

## 6. CI Guard 重定义

**旧规则（作废）**：扫描产物中是否含 `packages/ee` 目录代码。
**新规则**：扫描**已裁定必须移除的具体单元清单**（本文档 §2 产出）——
清单化、可审计、与"CE 能运行"不冲突。同步更新 [GW-11](REQUIREMENTS.md)。

---

## 7. 交付检查表（= [AG-EE](OPEN_GATES.md) 退出条件）

- [x] **§3 Integration Phase 0 完成**（能力盘点 18 项 C1–C18，三分类无"待定"）✅ 2026-07-23
- [ ] §1 六向清单齐全（**待补 EE→Core、Test→EE**，见 §2.2）
- [x] §2 每条依赖处置已定（20 个功能组 G1-G20）✅ **依据 §3 三分类**
- [x] §4 工作量精确拆分 ✅（EE 剥离本体 ≈1000-1300 + **HERMES 侧新建 ≈580（原遗漏）** + 其余工作包按包评估）
- [ ] §4.7 八条未核实项补测完成（AG-EE.5）
- [x] §5 分类完成 ✅ **结论：不需要全部重实现——必须重写的仅 5 项**
- [ ] §6 CI Guard 规则落地
- [ ] **EE-4 清零**：CE 可在"无 EE 专有业务功能与专有运行能力"下编译、启动、处理请求
