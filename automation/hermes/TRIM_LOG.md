# TRIM_LOG — automation/（0.88 硬分叉）裁剪与改造台账

> 多个并行任务共同追加。每条注明日期、需求编号、改动文件。

## 2026-08-13　去 bun + 气隙离线管线移植 + 主 Dockerfile 重写（FR-A02/A07/A08/A09/A10, FR-G01/G02, D-12/D-13）

### 运行时切点：bun-runner → pkg-runner（FR-A02/A07）

- `packages/server/sandbox/src/lib/utils/bun-runner.ts` **删除**，新增 `pkg-runner.ts`（HERMES-PATCH-005）：
  - `install()` 改 spawn `pnpm`：`install --ignore-scripts --config.node-linker=isolated
    --config.confirmModulesPurge=false [offlineArgs] [--filter ./pieces/<name>-<ver> ...]`；
    `AP_PIECES_OFFLINE_INSTALL=true` 时追加 `--offline --registry=https://registry.npmjs.org/
    --config.store-dir=${AP_PIECES_OFFLINE_STORE_DIR:-/usr/src/app/pnpm-offline-store}`
    （0.84 pkg-runner 注释全文移植：registry 是缓存命名空间非网络目标，须与烘焙层一致）。
  - `build()` 保持 esbuild 不动；`sanitizeFilterPath` 白名单正则保留。
- 调用方跟随改名：`cache/pieces/piece-installer.ts`（含 wideEvent `bunInstall`→`pkgInstall`、
  日志文案）、`cache/flow/code/code-builder.ts`；注释残留 bun 措辞清理：`resolver.ts`、`types.ts`。
- `piece-installer.ts#createRootPackageJson` 补写 `pnpm-workspace.yaml`（`packages: pieces/**`）
  与 `.npmrc`（`node-linker=isolated` + `ignore-workspace-root-check=true`）——pnpm 不读
  package.json `workspaces`，且引擎 loader 按 `pieces/<name>-<ver>/node_modules/<name>` 解析，
  必须 isolated 布局（npm hoist ⇒ PieceNotFound）。
- **bundle.tgz 依赖值保持裸绝对路径**（不加 `file:` 前缀）：0.84 ARCHIVE 分支即为裸路径且生产验证过；
  本次本地 pnpm E2E 亦验证裸 .tgz 绝对路径可解析。
- 测试跟随：`test/lib/cache/piece-installer.test.ts`、`test/lib/cache/flow/code/code-builder.test.ts`
  的 mock 路径/导出名与 bun 措辞的用例名更新。

### 离线管线适配 0.88（FR-A09/A10）

- `hermes/prewarm-pieces.sh` 重写适配 0.88：
  - 缓存路径 `cache/v11/common` → `cache/v13/common`（`sandbox/src/lib/cache/cache-paths.ts`
    `LATEST_CACHE_VERSION='v13'`）。
  - 布局对齐 0.88 installer：每件 `pieces/<name>-<ver>/bundle.tgz`（REGISTRY 件构建期从
    `registry.npmjs.org/<name>/-/<basename>-<ver>.tgz` 下载——即运行时 bundle 端点 307 重定向的
    同一来源；自研件从 `hermes/tarballs/` 拷贝），package.json 依赖=bundle.tgz 绝对路径，与
    `createPiecePackageJson()` 逐字节等价。烘 bundle.tgz 的额外收益：即使 `ready` 丢失触发重装，
    `saveBundlesToDiskIfNotCached()` 见 tarball 在场即跳过网络取件。
  - 验证步收紧为断言 `node_modules/<name>`（引擎 loader 的实际解析目标）。
  - **本地 E2E PASS**：13 件（11 registry + 2 自研）全部 prewarmed，isolated 布局 + ready 齐备。
- `hermes/seed-offline-store.mjs` 逻辑不变（tarball 锚点），注释更新为 sandbox/pkg-runner 与 0.88
  分发模型措辞；**本地 E2E PASS**（framework 0.28.2 / common 0.12.3 / shared 0.78.1 / tslib 2.6.2 闭包入库）。
- `hermes/check-tsconfig-paths.mjs` 路径列表对 0.88 无需改动（tsconfig.base.json +
  packages/web/tsconfig.{app,spec}.json 均在）；实跑发现 web 两个 tsconfig 的
  `@activepieces/piece-ai` 悬空映射（web 不在本任务范围，另行处理）。
- `hermes/README.md` 更新：v13、sandbox 路径、0.88 bundle.tgz 双形态说明、0.88 piece 源码树现状。

### 主 Dockerfile 重写（FR-A08，D-12 单镜像）

以 0.88 上游 Dockerfile 为骨架、0.84 hermes 版为模式：

- `ARG NODE_IMAGE`（内网镜像可覆盖）移植；base 保留 0.88 的 C.UTF-8 / REDISMS_VERSION=7.4.2 /
  无 apt cache mount 与 libcap2 的理由注释。
- **删除 bun 下载层**；全局层改 `npm i -g node-gyp npm@11.11.0 pnpm@9.15.9 pm2@6.0.10 esbuild@0.25.0`。
- isolated-vm 预装改 `cd /usr/src && npm install --no-fund --no-audit isolated-vm@6.0.2`
  （版本按 0.88 上游 6.0.2；机制按 0.84：装在 /usr/src 由父目录解析。0.84 用的是
  `@luminati-io/isolated-vm@6.0.2-lum.2` 别名——若 6.0.2 原包在目标构建机 gyp 编译失败，退回该别名）。
- build 阶段：`COPY .npmrc package.json pnpm-lock.yaml pnpm-workspace.yaml` + packages/，
  `pnpm install --frozen-lockfile`（store cache mount），turbo build 四 filter 不变，
  sourcemap 删除与 migration-manifest 生成保留；**上游构建期 pieces 裁剪段删除**（树已物理裁剪）。
- run 阶段：`pnpm install --prod --frozen-lockfile`（两阶段均 frozen，D-13：任何构建阶段不得联网重解析）；
  比上游多拷 `pnpm-workspace.yaml`。
- 末层照 0.84 全量移植：`COPY hermes/` → `seed-offline-store.mjs`（pnpm offline store +
  /root/.cache/pnpm 元数据缓存）→ `prewarm-pieces.sh`（v13 布局）。
- HEALTHCHECK 保留 0.88 的（WORKER 免探活分支即 FR-G04 的分体通路）；ENTRYPOINT/EXPOSE 不变；
  `docker-entrypoint.sh` 未动（`AP_CONTAINER_TYPE:-WORKER_AND_APP` 默认保留，FR-G01/G04）。
- `.dockerignore`：保留 `dist` + `**/dist` 双条目（0.84 教训），新增 `**/.turbo`、`cache`
  （防本地 prewarm 测试残留经 `COPY . .` 入镜像），显式注释 hermes/（含 tarballs）必须入上下文。

### 杂项 bun 清理

- `tools/setup-dev.js`：装 bun/`bun install` → 装 pnpm@9.15.9/`pnpm install`。
- `tools/scripts/pr-size-check.ts`：排除模式 `bun.lock` → `pnpm-lock.yaml`；
  `pr-size-check.test.ts`：`bun:test` → `vitest`（根 devDeps 已有 vitest 3.2.6），夹具同步。
- `tools/aggregate-offenders.mjs` / `analyze-tail.mjs`：包名提取正则 `.bun/<pkg>@` →
  `.pnpm/<@scope+name>@`（`+` 还原为 `/`）；`analyze-tail.mjs` 实跑通过。
- `tools/add-core-deps.mjs` 注释措辞 bun → pnpm；删除遗留 `tools/repoint-to-core.mjs.bak`。
- `turbo.json`：`bundle` 任务去掉对已删 `@activepieces/cli#build` 的依赖；
  `@activepieces/engine#test` 去掉对已删 `@activepieces/piece-approval#build` 的依赖
  （注意：engine 两个测试文件仍 import piece-approval，engine 包不在本任务范围，需另行处理）。
- `.nvmrc` v24.14.0 与 Dockerfile `node:24.14.0-bullseye-slim` 一致，未动。
- 自查 grep：`Dockerfile` / `docker-entrypoint.sh` / `sandbox/src` / `hermes` 中 "bun"
  仅存在于解释去 bun 的注释中；非注释命中为零。

### 待实际 docker build 验证的遗留项

1. `pnpm install --frozen-lockfile` 两阶段在镜像内首跑（pnpm-lock.yaml 刚生成）。
2. isolated-vm@6.0.2 在 bullseye/node24 的 gyp 编译（0.84 曾改用 luminati 别名）。
3. 镜像内 pnpm 9.15.9 与本地验证用的 pnpm 10 的 lockfile/store 版本差异（store v10 vs v5/v9）。
4. 自研 tarball 仍 pin 0.84 基线（shared 0.78.1 / framework 0.28.2）——离线闭包管线已验证，
   但两件自研 piece 是否需针对 0.88 运行时重打 tarball 待运行期验证。
5. web tsconfig `@activepieces/piece-ai` 悬空映射（CI check 会红）与 engine 测试的
   piece-approval import——均在他人/后续范围。

---

## 2026-08-13　server 侧 EE 剥离 + 功能域裁剪 + 0.84 CE 重写移植（FR-A03 / FR-D2）

> 范围：`packages/server/**` 与 `packages/core/shared/**`。参考实现是 0.84 分叉
> （`activepieces/`，只读），方案见 `docs/ap-integration/EE_REMOVAL_PLAN.md`（G1-G20）与
> `HERMES_PATCHES.md`。**未编译验证**（node_modules 未就绪，见文末遗留风险）。

### A. 删除：EE 目录

| 项 | 数量 | 裁定理由 |
|---|---|---|
| `api/src/app/ee/`（26 个子域） | **193 个 .ts** | AP 商业版代码，CE 交付不得包含（D6/AG-EE）。其中仍需要的 4 项已按 0.84 方案在 CE 侧重写（见 C）。 |
| `api/src/app/ee/database/` 迁移 | 20 个（含在上面 193 内） | G16 方案 A：全新库自足，统一后的 MIT 迁移（`Unify*` 等）会重建 CE 重写仍用到的所有表（signing_key / audit_event / project_role / project_member / concurrency_pool）。`postgres-connection.ts` 的 20 条 import 与数组项同步摘除。 |
| `worker/src/lib/execute/jobs/ee/agent/` | 5 个 .ts | EE agent 执行链，worker 侧唯一 `@ai-sdk/*` + MCP 消费方（HERMES-PATCH-015 / VT-17 同款裁定）。`job-registry.ts` 的 lazy loader 置空——`getHandler` 本就对未注册类型 `throw`，保持 fail-loud。 |
| `utils/src/agent-ai-utils.ts` | 1 个 .ts（562 行）+ 其测试 | 上一条删除后零消费方，且是 `server/utils` 最后一个 `@ai-sdk/*` import 方。**结果：`packages/server` 全树 `@ai-sdk` 真实 import 归零**（仅剩解释性注释）。 |

### B. 删除：功能域整目录（12 个）

| 域 | .ts 数 | 裁定理由与连带处理 |
|---|---|---|
| `mcp` | 73 | 气隙内无 MCP 客户端（PATCH-015/D12）。`server.ts` 的三条根路由注册同删。 |
| `ai` | 19 | AI Generate 已改 HTTP piece 直连模型端点（VT-15）。`flags` 的 `PGVECTOR_AVAILABLE` 等改常量。 |
| `tables` | 13 | 非工作流域。`folder.service` 摘除 tables 关联（`numberOfTables` 恒 0）；两个 flow-version 迁移的 tables 依赖内联处理（迁移链不可断）。 |
| `tool-search` | 9 | 依赖 embedding 索引；`piece-install-service` / `piece-sync-service` 摘除索引调用，安装/同步主逻辑不动。 |
| `template` | 8 | 模板市场是 SaaS 面；`trigger-source-service` 摘除引用。 |
| `knowledge-base` | 6 | RAG 面，无消费方；`database/seeds` 摘掉 `knowledgeBaseSeed`。 |
| `teams-bot` | 5 | Teams 集成，气隙不可达。 |
| `analytics` | 4 | `platform-analytics` 依赖 ee-authorization。 |
| `agents` | 3 | EE chat/agent 面。 |
| `user-invitations` | 3 | 0.84 同样删除；`authentication.service` / `authentication-utils` 摘除邀请分支。 |
| `event-destinations` | 2 | 外发事件目标，气隙不可达。 |
| `action-run` | 2 | 仅被 mcp 引用。**sandbox/worker/engine 的 action 执行链路保留不动**（那是执行链内部机制）。 |

`app.ts`：edition switch 的 CLOUD / ENTERPRISE 两支整体删除，只留 COMMUNITY；无条件注册的
`alertsModule` / `billingUsageReportModule` / `platformAnalyticsModule` / `agentsModule` 一并摘除；
`rbacMiddleware` preHandler 移除（项目 RBAC 由 CE `rbac-service` 在 `authorize.ts` 内执行）。

### C. 移植：0.84 的 CE 重写与补丁

| 落点 | 来源 | 说明 |
|---|---|---|
| `api/src/app/signing-key/`（4 文件） | 0.84 同名目录 | RS256 密钥对（PKCS8 私钥），`platformAdminOnly`；实体映射 MIT 迁移建的 `signing_key` 表。 |
| `api/src/app/managed-authn/`（3 文件） | 同上 | `POST /v1/managed-authn/external-token` public；RS256+kid 校验，CE 自有 zod 契约（externalUserId / externalProjectId / firstName / lastName / email? / role / platformRole）；每次握手同步角色显示名 + `ensurePersonalProject` + 共享 TEAM project。 |
| `api/src/app/audit-logs/`（3 文件） | 同上 | `applicationEvents` 监听器落库 + `GET /v1/audit-events`。**admin-center 仍是合规审计真源**，这里是 AP 侧运维审计。 |
| `api/src/app/project/` +7 文件 | 同上 | `project.controller`（PATCH-018：POST 建 TEAM project、fail-loud 400）、`project.module`、`project-role.service`、`project-member.service` 与 3 个实体。 |
| `core/security/v2/authz/rbac-service.ts` | 同上 | 含共享 project bypass + JWT permissions 快路径。 |
| 3 个 stub | 同上 | `workers/worker-group-stub.ts`（CE 无 worker group，canary 恒 false）、`workers/job-queue/concurrency-pool-stub.ts`、`app-connection/secret-manager-stub.ts`。 |
| `user/user.module.ts` | 同上 | CE 影子 `/v1/users`（G8/R10），builder 头像/头部要读 `GET /v1/users/:id`。 |
| `database/migration/postgres/1824000000000-HermesLocalizeCdnAssets.ts` | 0.84 的 `1794000000000-*` | 品牌与 piece 图标指向自服务 `/ap-cdn`（气隙，DECISIONS X-2/X-3）。重编号至上游最后一条迁移之后，`release` 改 `'0.88.0'`，注册进 `getMigrations()` 末尾。 |
| **PATCH-003** | `pieces/community-piece-module.ts` | 补 `DELETE /v1/pieces`。 |
| **PATCH-006** | `engine/src/lib/network/dns-lookup-guard.ts` | `AP_SSRF_ALLOW_LIST` 接受主机名。**0.88 架构不同**：0.84 是 worker 的 iptables/egress proxy 在启动期解析主机名；0.88 改成引擎内 DNS/socket guard 按已解析 IP 匹配，故改为在 DNS 路径上直接匹配请求的主机名（大小写与 FQDN 末点归一）。裸 IP 直连仍需 IP/CIDR 条目——**不做隐式放宽**。 |
| **PATCH-007** | `engine/src/lib/operations/sync-webhook-release.ts` + `flow.operation.ts` | run 进终态即释放阻塞在 sync webhook 的调用方（否则要等满 300s 才拿到兜底 204）。**非终态坚决不释放**（PAUSED/QUEUED 的响应要等 resume）。0.88 传输从 worker socket 改为 HTTP 回调 `engineRunApi.sendFlowResponse`。测试逐状态锁定。 |
| **PATCH-010** | `flags/theme.ts` | 白标：primaryColor `#db0011`、websiteName `Automation Studio`、`/hermes-full-logo.svg`、`/hermes-mark.svg`。 |
| **PATCH-012** | `app.ts` | 摘 `appEventRoutingModule`（未鉴权的 `/v1/app-events/:pieceUrl`，服务的四个 SaaS 件不在白名单里）。`app-event-routing.service.ts` 保留：flow-trigger 的 APP_WEBHOOK 分支仍用它。 |
| **PATCH-015** | `app.ts` / `server.ts` / worker / utils | 0.88 不是"停用"而是**整域删除**（见 A、B）。 |

**PATCH-004 裁定为过时、不移植**：0.88 上游已删除 `platform.filteredPieceNames` /
`filteredPieceBehavior` 两列，且 FR-E05 移除了 admin-center 的 piece 管理 UI、metadata registry
只 seed 13 个白名单件——`filterPiecesBasedOnPlatform` 要过滤的输入本身已不存在。**不恢复这两列**。

### D. `packages/core/shared/src/lib/ee` 契约裁剪

保留 11 个子目录：`managed-authn` `signing-key` `project-members` `audit-events`（CE 重写在用）、
`agent`（flow-version 迁移链 v7/v16/v22 读历史 agent step 的 `AgentPieceProps`）、`billing`
（`OPEN_SOURCE_PLAN` / `hasActiveSubscription`）、`authn` `git-repo` `oauth-apps` `otp`
`secret-managers`（web 存活面仍 import）。删除 `alerts` `api-key` `embed-subdomain`
`event-destinations` `piece-set` `product-embed` `scim`，`src/index.ts` 导出同步修剪。
`test/ee/piece-set-visibility.test.ts` 随之删除（孤儿）。

`project-entity.ts` 摘除 `pieceSet` 关系与 `PieceSet` 类型（piece_set 实体已随 ee 删除）；
`pieceSetId` **列保留**——MIT 迁移仍会建它，摘掉的只是 TypeORM 关系，不产生 schema 变更。

### E. 测试

- 删除：`test/integration/cloud`（EE/CLOUD 面）、`test/unit/{ee,app/ee}`、被删域的
  `test/integration/ce/{ai-provider,ai-tools,knowledge-base,mcp,tables,tool-search}` 与
  `test/unit/app/{agents,agent,mcp,action-run,tool-search,knowledge-base,ai}`、
  `unit/app/flows/flow-run/flow-run-ai-usage-tracker.test.ts`（被测源文件已删）。
- 移植自 0.84：`integration/ce/managed-authn/external-token.test.ts`、`ce/signing-key/`（2 个）、
  `ce/audit-events/`、`engine/test/operations/sync-webhook-release.test.ts`。
- 夹具收敛：`helpers/mocks/index.ts` 删掉 api-key / oauth-app / ai-provider / platform-plan /
  template / user-invitation / git-repo / otp / tables 系列工厂；`MockBasicSetupParams.plan`
  **整个删掉而不是留成空转**（CE 恒 `OPEN_SOURCE_PLAN`，留着等于静默忽略调用方的意图）；
  `describe-with-auth` 去掉 `[SERVICE]` 变体（SERVICE token 由 EE api-key 铸造，本构建无从获得）。
- `helpers/db.ts` 从 0.84 补 `findBy`（managed-authn CE 测试要断言"个人项目只建一次"）。

### F. 自查结果（grep）

| 检查 | 结果 |
|---|---|
| `grep -rn "from '.*ee/" packages/server/api/src --include='*.ts' \| grep -v "/app/ee/"` | **0** |
| `app.ts` 中 `app/ee` 的非注释命中 | **0**（2 处均为 HERMES 说明注释） |
| 12 个被删域名 + `ee/` 的 import（api/worker/engine/utils/sandbox 的 src 与 test 全扫） | **0** |
| `packages/server` 全树 `@ai-sdk/*` / `from 'ai'` 真实 import | **0** |
| `ls packages/server/api/src/app` 顶层目录 | **21**（下方说明） |

顶层 21 项 = 4 个基础设施（`core` `database` `helper` `health`）+ 17 个功能域：
`app-connection` `audit-logs` `authentication` `file` `flags` `flows` `managed-authn` `pieces`
`platform` `project` `signing-key` `store-entry` `trigger` `user` `variable` `webhooks` `workers`。
**NFR-1 的 ≤15 未达成（17）**：剩下的每一个都在 CE 运行链上（builder 挂载、flow 执行、
webhook、连接、审计到人握手），再删就要动产品功能，需产品裁决而不是工程裁剪。

### G. 遗留风险（必须编译/运行期验证）

1. **未编译**。node_modules 未就绪，全部改动仅经 grep 与逐文件对照 0.84 自查。`tsc` 首跑预计
   仍有零散未使用 import / 类型口径问题需要收敛。
2. **HERMES-PATCH-008 未移植**（worker 侧 sync webhook 兜底释放）。0.88 的
   `WorkerToApiContract`（`packages/core/execution`）**没有 `sendFlowResponse`**——0.88 只有引擎能
   经 `POST /v1/engine/flow-response` 发布响应。补齐需要跨包新增契约方法 + api 侧 handler + worker 调用，
   超出本次 `packages/server` 的改动边界。**当前缺口**：引擎启动之前就失败的 run（piece 供给失败、
   缺 flow version、sandbox 超时/OOM）仍要等满 `AP_WEBHOOK_TIMEOUT_SECONDS` 才回 204。PATCH-007
   覆盖的是引擎已启动的主路径。
3. **两个 flow-version 迁移的 tables 依赖已内联**（`migrate-v11-tables-to-v2`、
   `migrate-v18-tables-find-records-field-ids`）——迁移链完整性需要用真实历史 flow 版本回归。
4. **rbac / project 域适配**为 0.84→0.88 结构迁移（`securityAccess` helper、
   `project-service.getAllForUser` / `applyProjectsAccessFilters` 等），运行期 RBAC 判定需要
   `test/integration/ce` 与浏览器 E2E 双跑确认。
5. `platform_plan` 表不再被 TypeORM 托管、也不再由测试播种，但 MIT 迁移仍会建表——存量库里的
   旧行成为孤儿数据（不影响读写，`getPlan` 恒返回 `OPEN_SOURCE_PLAN`）。
6. PATCH-006 的主机名匹配只覆盖 DNS 路径；若某 piece 自行解析后直连 IP，仍需 IP/CIDR 条目。
   这是有意的（不做隐式放宽），但与 0.84 的 proxy 行为存在差异，运维文档需同步。

---

## 2026-08-13　主会话：pieces 白名单裁剪 / pnpm 化 / 自研件 0.88 重建 / deploy 重指（FR-D3/D5/D6/D7, FR-A06, NFR-1）

### piece 树裁剪（FR-D3/D4/D5，D-3）

- `packages/pieces/core/` 27 件 → **9 件**（`csv data-mapper file-helper http pdf schedule
  text-helper webhook xml`）；`packages/pieces/community/` 725 件 → **2 件**（`json postgres`）；
  `packages/pieces/custom/` 整目录删除。
- 自研 2 件从 0.84 树 vendored 进 `packages/pieces/community/{biz-calendar,hash-helper}`
  （只带 `src/ package.json tsconfig*`，剥离 0.84 遗留的 `node_modules/ dist/`——两者合计 434MB）。
- **FR-D05（9/11 件迁 `core/`）落地方式**：白名单机制本就按包名而非路径定位（`pieces.json` 是
  唯一真源），因此路径迁移无需改机制；受影响的是 `tsconfig.base.json` 的 paths 与 pnpm workspace
  glob，两者均已按新树重写（见下）。
- `hermes/pieces.json` 版本按 0.88 实测值全量更新（11 件全有跳动，`text-helper` 0.5.1→0.6.4、
  `postgres` 0.2.6→0.3.0 跨 minor）。`pieces.e2e-fixtures.json` 随 `packages/tests-e2e` 一并删除（FR-D09）。
- `packages/server/api/package.json` 移除 4 条 `workspace:*` piece 依赖
  （slack / square / facebook-leads / intercom）——它们的唯一使用者是
  `trigger/app-event-routing/app-event-routing.module.ts`，该模块已按 HERMES-PATCH-012 删除
  （模块此前只保留了未注册的死代码，构建期才暴露）。

### tsconfig / workspace（FR-A06）

- `tsconfig.base.json`：723 条 `@activepieces/*` paths 中删除 **709 条悬空条目**，补回 2 条自研件，
  最终 **26 条**（全部目标存在，`hermes/check-tsconfig-paths.mjs` 可验）。
- 根 `package.json`：`packageManager` bun@1.3.3 → **pnpm@9.15.9**；workspaces 去掉 `cli`/`tests-e2e`/
  `pieces/custom`；`resolutions` 复制为 `pnpm.overrides` + `pnpm.onlyBuiltDependencies`；
  删 `prebuild`(install-bun) / `test:e2e` / `cli` 脚本。
- 新增 `pnpm-workspace.yaml`（12 条），重写 `.npmrc`（去掉上游的 `@activepieces:registry` 与
  `NPM_TOKEN` 发布配置——硬分叉不发包），删除 `bun.lock`(1.6MB) 与 `bunfig.toml`。
- `pnpm install` 生成 **`pnpm-lock.yaml`（825KB）**，供 Dockerfile 两阶段 `--frozen-lockfile`。

### 上游资产删除（FR-A04 + 减重）

`CLAUDE.md`(11 处) `AGENTS.md` `.claude/` `.cursor/` `.agents/` `.mcp.json` `skills-lock.json`
`.agentignore`+两个符号链接 · `docs/`(45MB) `.github/`(256KB) `.devcontainer/` `crowdin.yml`
`depot.json` `benchmark/` `smoke-test/` `.verdaccio/` `.zap/` `.all-contributorsrc` ·
`brain/` 只保留 `decisions/`（ADR 是裁定依据，其余 10 个子目录是上游 AI 检索语料）。

### 自研件在 0.88 framework 重建（FR-D07）

**上游 0.88 的 piece 分发体例已变**：实测 `piece-text-helper@0.6.4` 的 tarball 是
`main: ./src/index.js` 的**单文件 esbuild bundle**，`dependencies` 只剩真实外部依赖
（`jsdom`），`@activepieces/*` 全部内联。0.84 的 tsc 产物体例（`dist/src/**` + pin
`@activepieces/{framework,common,shared}` 版本）在 0.88 **不可用**——0.88 工作区版本
（framework 0.36.0 / common 0.12.8 / shared 0.129.0）**均未发布到 npm**（实测 404），
按老体例打包会让 `seed-offline-store.mjs` 在构建期 404 炸掉（已实际触发一次）。

因此两个自研件改为与上游同体例：`npx esbuild src/index.ts --bundle --platform=node
--format=cjs --outfile=dist/src/index.js`，tarball 的 package.json 为
`{name, version, main:'./src/index.js', dependencies:{}, files:[...]}`。
产物 `hermes/tarballs/activepieces-piece-{biz-calendar,hash-helper}-1.0.0.tgz`
（35.5KB / 31.3KB），`require()` 冒烟通过。

**连带结论（FR-A09）**：自研件闭包为空 ⇒ `seed-offline-store.mjs` 成为空操作，
`AP_PIECES_OFFLINE_INSTALL` 的 fail-closed 语义不变但已无外部闭包需要烘焙。这比 0.84
的气隙姿态更强（0.84 需要从 npm 取 framework/shared/tslib），脚本保留以备将来引入
带真实外部依赖的自研件。

### deploy / 构建链重指（0.84 → 0.88）

- `deploy/environments/dev/docker-compose.dev.yml`：build context `../../../activepieces`
  → `../../../automation`，image tag `0.84.0-ee-removed` → **`0.88.0-ee-removed`**，注释同步。
- 路径引用批量重指（`activepieces/{hermes,dist,packages}` → `automation/…`）：
  `deploy/pieces/{generate-metadata-seed.js,serialize-piece-metadata.js,fetch-pieces.sh,mirror-ap-cdn.mjs,README.md}`、
  `deploy/scripts/build-and-push-k8s.ps1`、`deploy/environments/dev/build-and-deploy.ps1`、
  `deploy/kong/kong.yml.template`、`deploy/k8s/activepieces.yaml`、`deploy/ACTIVEPIECES_INTEGRATION.md`、
  `frontend/developer-workstation/scripts/sync-service-task-builder.mjs`、`BUILD_GUIDE.md`。
- **piece 元数据 seed 重生成**（设计器半）：`sh fetch-pieces.sh` 抓 11 件 0.88 元数据 + tarball +
  图标（图标落 `automation/packages/web/public/piece-icons/`），`serialize-piece-metadata.js`
  重新序列化 2 个自研件，`generate-metadata-seed.js` 产出 `deploy/pieces/metadata/pieces-seed.sql`
  （13 件）。旧 0.84 版本的 metadata json 被覆盖——版本不匹配时该脚本 fail-loud，是其设计意图。

### 编译收敛（FR-A03/D01 的实证部分）

`turbo run build --filter=api --filter=@activepieces/engine --filter=worker --filter=web`
首跑 10 处 TS 错误，逐条修复后**零错误**：

| 文件 | 错误 | 处置 |
|---|---|---|
| `trigger/app-event-routing/app-event-routing.module.ts` | 4× TS2307 找不到已删的 4 个 piece | 整文件删除（PATCH-012 早已摘掉其注册，剩下的是死代码）；`app.ts` 注释更新 |
| `pieces/piece-sync-service.ts` | 4× `unknown` 不可赋值 | pnpm 解析到 undici-types v6，`Response.json()` 返回 `Promise<unknown>`（bun 的 hoist 树给的是 `any`）——在边界处显式 cast，形状不变 |
| `project/project.controller.ts` `user/user.module.ts` | 2× `.optional()` 不存在 | 0.88 用 zod-mini，实例方法改函数式 `z.optional(X)` |

### 前端产物减重（NFR-1）

`vite.embed.config.mts` 首次产出 26MB，逐项定位后：
- `packages/web/public/chat-suggestions/`（**14MB**，3 张 4-5MB 的卡片背景 SVG）——属已删的
  `chat` feature，随域删除；
- `packages/web/public/locales/` 11 种语言 → **保留 `en zh zh-TW`**（与三个 HERMES 前端的语言集一致），1.4MB → 512KB。

最终 **8.9MB**（0.84 为 25MB；主 chunk 3.8MB vs 0.84 的 6.9MB），达标 NFR-1 的 ≤10MB。

---

## 2026-08-14　前端交付链修正：dev 镜像源 + ap-cdn 资产镜像化

### dev 前端镜像走 `Dockerfile.local`（COPY 宿主 dist）

`deploy/environments/dev/docker-compose.dev.yml` 的 `developer-workstation-frontend` /
`admin-center-frontend` 用的是 `Dockerfile.local`，其内容只有 `COPY dist …` —— **不在 Docker 内构建**。
因此 embed 产物换新后，必须按序跑：① `vite build --config vite.embed.config.mts`
→ ② 宿主 `pnpm build`（prebuild 钩子同步 + 重出 dist）→ ③ compose build。
漏掉 ② 会让镜像继续装上一次的 bundle（本次实测装的是 0.84 的 `mount-builder-CnipLkab.mjs`，
主 chunk 6.9MB），`--no-cache` 也救不了——陈旧来源在构建上下文的 `dist/` 里。

**`dist` 不得加进 `frontend/*/.dockerignore`**（试过，`COPY dist` 直接 `"/dist": not found`，已回退）。
这与 k8s 侧 `Dockerfile`（Docker 内自建）行为相反，两条链差异需长期记住。

### `/ap-cdn/*` 资产镜像化（气隙 X-3，13 件图标全 404 的修复）

- `deploy/pieces/mirror-ap-cdn.mjs`：扫描路径 `packages/shared/src` → `packages/core/shared/src`
  （0.88 结构变更）；镜像 21 个上游资产（5.1MB）到 `automation/packages/web/public/ap-cdn/`。
- 自研件图标上游不存在（实测 404），改**自托管**：资产落 `ap-cdn/pieces/hermes/`，
  且把 piece 源码的 `logoUrl` 直接改成 `/ap-cdn/pieces/hermes/*.svg`
  （0.84 靠 `HermesLocalizeCdnAssets` 迁移事后改写 DB；现在源码即气隙安全，迁移对这两件成为 no-op）。
  两件已按新 logoUrl 重新 esbuild 打包、重生成 metadata 与 `pieces-seed.sql`。
- `vite.embed.config.mts` 新增 `dropApCdnFromEmbedPlugin`：`closeBundle` 阶段把 `ap-cdn/`
  从 embed 输出删掉——宿主经自己的 `/ap-cdn/` 代理向 AP 取，打两份纯浪费。
  **embed 产物维持 8.9MB**，NFR-1 未被撑破；AP 镜像 1.40GB → 1.41GB。
- 复验：13/13 `logoUrl` 全部 200；端到端复跑一次，变量回写与运行记录无回归。

---

## 2026-08-14　HERMES-PATCH-019：关掉三条通往公网的路径（X-3 / C-2）

0.88 随 ADR 0006 改了 piece 分发模型，带来两条 0.84 没有的外网路径；连同原有的云端目录同步，
共三条，且**全部是「漏配 env 就打开」**——对气隙环境是最糟的失败形状：不报错，件目录悄悄
偏离冻结白名单，下载链接悄悄指向解析不了的域名。硬分叉自己定默认值，一律翻成 fail-closed。

| 路径 | 上游默认 | 改后 | 落点 |
|---|---|---|---|
| 每小时同步 `cloud.activepieces.com/api/v1/pieces` | `PIECES_SYNC_MODE=OFFICIAL_AUTO` | **`NONE`** | `helper/system/system.ts` |
| 件包下载重定向 `cdn.activepieces.com` | `USE_CDN_FOR_BUNDLES='true'`（0.88 新增） | **`'false'`** | 同上 |
| 兜底重定向 `registry.npmjs.org` | 无条件 | **`AP_PIECES_OFFLINE_INSTALL=true` 时返回 not-found + log.error** | `pieces/piece-bundle.ts` |

附带：`piece-sync-service.ts` 的 `setup()` 原本无条件注册每小时 cron、只在 `sync()` 内早退，
关闭状态下每小时白打一行日志；改为关闭时**直接不排程**。

`AP_PIECES_OFFLINE_INSTALL` 直接读 `process.env`（不走 AppSystemProp），与 worker 侧
`pkg-runner.ts` 保持同一个拼写——一个开关两处读，不给两半失配的机会。

dev compose 与 `deploy/k8s/activepieces.yaml` 显式重申这两个开关并加注释：运维在配置文件里
就能看见闸门，不必去读镜像默认值。

**实测**：① dev 重启后日志 `Cloud piece sync disabled — cron not scheduled`；
② **一个 env 都不传**另起容器 → 同步仍关闭、零 `cloud.activepieces.com` 请求（默认值真的 fail-closed）；
③ 端到端复跑通过，无回归。

### 顺带核实（结论是好的，留档免得日后重查）

运行时 JS/CSS/HTML 里 `cdn.activepieces.com` **零命中**——PATCH-009 的构建期改写有效，
7 处残留全在 `.js.map` 源码映射里。被引用的核心步骤图标（code/loop/router/empty-trigger）
与登录页背景图均已镜像到本地 `/ap-cdn/`。

---

## 2026-08-14　piece 管理 UI 恢复 + 文档/源码对齐

### FR-E05 被推翻：piece 在线管理 UI 恢复到 DW（不是 AC）

用户指出「Admin Center 的 piece Import/Export 入口没了」。后端从未删（D-2 保留
`AutomationPieceController` 五端点，SYS_ADMIN 门禁、无鉴权 403），缺的只是前端。
落点按 D-8 的先例选 **DW Automation 页第 4 个 tab「Pieces」**（仅 SYS_ADMIN），
而非放回 AC——FR-E01/E03「AC 前端不得有 AP 入口」未被推翻。
新增 `src/api/automationPiece.ts` + `src/views/automation/components/PiecesPanel.vue`，
三语各 35 个 `automation.pieces.*` key。详见 IMPLEMENTATION_0.88.md §6.7。

顺带修掉旧 AC 实现的两处毛病：① 409 `PIECE_IN_USE` 文案用 `{count}` 占位但后端返回的是
flow 名字列表，改渲染名单；② import/toggle 的 catch 是空块（靠 AC 全局 axios 拦截器兜底），
DW 无该拦截器，改为逐条显式报错并补齐缺失文案（error-handling-governance）。

> **同期澄清**：Automation Flow 的 Import/Export **没丢**，按 D-8 在 DW 的「迁移」tab
> （list/导出/导入/connection 比对/启停/删除全在）。

### 文档与源码对齐

- `docs/ap-integration/PIECE_DEVELOPMENT_EXAMPLE.md` 更新到 0.88（与已更新的 HOWTO 一致）。
- 该文档逐段核对仓库源码时揪出两处陈旧代码，已修：
  - 两个自研件的 `minimumSupportedRelease` 仍是 `'0.36.1' // 必须 ≤ 我们的 0.84.0`。
    低于 0.88 context V2 下限的值会被 `Piece` 构造器**静默抬到 0.82.0**——不报错，
    但让人误判兼容范围。改为实值 `'0.82.0'` 并注明 clamp 行为。
  - `deploy/pieces/serialize-piece-metadata.js` 的报错文案与两处注释仍指向
    `npm run build-piece`（随 `packages/cli` 删除已不可执行），改指 HOWTO §3.1 的 esbuild 配方。
- 改了 piece 源码 ⇒ 重打 tarball、重跑 `serialize-piece-metadata.js` + `generate-metadata-seed.js`、
  重建镜像并灌库。

**回归实测**：13/13 图标 200；镜像内 13 件 `ready`+`node_modules` 齐备；
`Cloud piece sync disabled — cron not scheduled` 仍在；端到端 SUCCESS、变量回写正常。

---

## 2026-08-14　返工：迁移面与 piece 管理面移回 Admin Center（D-8 作废）

**根因是需求错了，不是实现错了。** 用户指出「生产环境只能使用 Admin Center，DW 不会出现在生产环境」，
核实无误：`deploy/k8s/kustomization.yaml` 的 resources 不含 developer-workstation(-frontend)；
`docker-compose.dev.yml` 第 6 行早写着「设计器仅 DEV 使用，勿发布到 SIT/UAT/PROD」。
而需求 D-8 恰恰把 flow 跨环境迁移入口放进 DW，FR-E05 二次更正时 piece 管理面也照此放进 DW——
这两件事只有**生产**才需要（把 dev 的 flow 迁进 prod、把自研件投放 prod），
放在永不上生产的应用里等于生产没有入口。按错误需求实现了两遍才发现。

### 更正后的边界

| 能力 | 落点 |
|---|---|
| flow 列表/创建/编辑(嵌 builder)/发布/运行历史 | **DW**（设计期） |
| flow 迁移：导出/导入(含 connection 比对)/启停/删除 | **AC**（生产运维） |
| piece 管理：列表/导出/导入/删除/启停 | **AC**（生产投放） |

- AC 从 git 原件恢复：两个视图（553/382 行）、两个 api、路由 2 条、菜单 2 项、
  `ROUTE_PERMISSIONS` 2 条（原 HEAD 少了 `/automation-flows` 一条，顺手补齐）、三语 i18n 两块。
- 带回 DW 移植期修的两处：① piece 409 文案 `{count}` → `{flows}`（后端返回的是 flow 名字列表）；
  ② import/toggle 空 catch 补显式失败提示（新增 `importFailed`/`toggleFailed` 三语 key）。
- 一处有意偏离原件：flow 导入弹窗 `label-width` 110px → `auto`（`portal-dialog-form-labels`，
  英文下 "Publish & enable" 有折行风险）。
- DW 拆除 `MigrationPanel.vue` / `PiecesPanel.vue` / `api/automationFlow.ts` / `api/automationPiece.ts`
  与对应三语 key，保留 Flows/Runs（Flows 走 `api/automation.ts` 直连 AP，不依赖被删模块）。
- **连带简化**：UI 回到 AC 后前后端同处一个应用，FR-E06 要求的 DW→AC 服务间调用对迁移面不再需要。

**验证**：AC typecheck 13/build ok/test 28 通过、DW typecheck 128/build ok/test 6 失败——
全部与基线逐项一致，零新增；三语 key 数 AC 1008、DW 2227，均对齐。
产物核对：AC 含 `AutomationFlows`/`AutomationPieces` 路由与 8 个端点，DW 无管理面残留。
镜像重建上线，`/admin/` 与 `/dev/` 均 200，端到端 flow 复跑 SUCCESS。

> **连带结论（已知会用户）**：builder 只嵌在 DW（D-1/X-6），DW 不上生产 ⇒ 生产环境没有 flow 编辑能力，
> 定位是「导入 + 启停 + 排障」。若业务要求在生产直接改 flow，属另一个需求（DW 上生产，或 builder 也嵌进 AC）。

## 2026-08-14　依赖减重：删已删功能域的遗留依赖 + run 阶段只装运行期成员（NFR-1）

镜像 `node_modules` **983MB**，体积前列里塞满与运行无关的东西：`cloudflare 52.7MB`、
`lucide-react 45.2MB`、`date-fns 38.9MB`、`posthog-js 38.7MB`、`autumn-js 30.9MB`、
`pdf-lib 23.1MB`、`pglite 19.7MB`、`core-js 15.6MB`、`@1password/sdk-core 9.9MB`、
`@shikijs/langs 9.9MB`。两个互相独立的根因。

### 根因 A：run 阶段装了整个 workspace（含前端）

`Dockerfile` run 阶段 `COPY --from=build .../packages ./packages` 搬进**全部** workspace 成员，
随后的 `pnpm install --prod --frozen-lockfile` 就把 `packages/web`（lucide-react / posthog-js /
shiki / core-js / date-fns…）与 `packages/ee/embed-sdk` 的依赖一并装上。运行镜像只跑
api + worker + engine——前端在这里是**构建好的静态 `dist/packages/web`**，不需要任何 node_modules。

上游本来靠 build 阶段的「裁 workspace 成员」块（`rm -rf packages/web packages/cli …` 后重装锁文件）
顺带规避掉；那块在 2026-08-13 去 bun/裁 pieces 时随「构建期裁 pieces」一起删了。**裁 pieces 是对的**
（源码树已物理裁剪、锁文件已对齐），但连带丢掉了「排除仅构建期成员」这一效果，于是前端依赖开始进运行镜像。

改法**不是**把成员从 `pnpm-workspace.yaml` 里摘掉（那会改变锁文件的成员集合，`--frozen-lockfile`
直接失败），而是在 install 时过滤：

```
pnpm install --prod --frozen-lockfile \
  --filter=api... --filter=worker... --filter=@activepieces/engine...
```

`<pkg>...` = 该包**及其依赖**。实测 `Scope: 12 of 27 workspace projects`，
`Lockfile is up to date, resolution step is skipped`——`--frozen-lockfile` 仍然通过。
pnpm 把根工程（`.`）一并纳入，所以 `docker-entrypoint.sh` 里
`node -e "require('jsonwebtoken')"`（自动签发 `AP_WORKER_TOKEN` 那段，deploy 全环境都没显式设，
必走）依旧能从根 `node_modules` 解析到——已在镜像内实测确认。

### 根因 B：功能域删了，`package.json` 的依赖没跟着剪

12 个功能域（ai / mcp / agents / tables / knowledge-base / tool-search / …）与 EE 目录删除后，
依赖声明留在原地。逐个 grep 确认**零引用**（含 `require()`、动态 import、`@types/*` 配对）后移除：

| package.json | 移除 |
|---|---|
| `server/api` | `@1password/sdk`、`@ai-sdk/*`（10 个：amazon-bedrock/anthropic/mcp/azure/google/google-vertex/openai/openai-compatible/provider/replicate）、`@aws-sdk/client-bedrock`、`@aws-sdk/client-secrets-manager`、`@modelcontextprotocol/sdk`、`@openrouter/ai-sdk-provider`、`@openrouter/sdk`、`ai`、`ai-gateway-provider`、`autumn-js`、`cloudflare`、`supergateway` — 共 21 个 |
| `server/utils` | `@activepieces/ai-providers`、`@modelcontextprotocol/sdk`、`@ai-sdk/*`（7 个）、`@openrouter/ai-sdk-provider`、`ai` — 共 11 个 |
| `server/worker` | `@ai-sdk/*`（8 个）、`@openrouter/ai-sdk-provider`、`ai` — 共 10 个 |
| `server/engine` / `pieces/framework` | `ai` |
| `web` | `ai`、`ee-embed-sdk` |

> `cloudflare` / `autumn` / `openrouter` 在源码里仍有大量命中，但**全是同名标识符与文案**
> （`AIProviderName.CLOUDFLARE_GATEWAY`、`AUTUMN_FREE_PLAN`、i18n 文案、迁移里的列名），
> 不是这几个 npm 包。逐条核对过才删。

### 孤儿包/文件

- **`packages/core/ai-providers/`（整包删除）** — 全仓无 `from '@activepieces/ai-providers'`，
  它自己拖着 9 个 AI SDK。连带清理 `tsconfig.base.json` paths、`server/api/vitest.config.ts` alias。
  （workspace 登记走 `packages/core/*` 通配，无需单独摘。）
- **`server/utils/src/mcp-transport.ts`（删除）** — mcp 域已删后零消费方，只被 `src/index.ts`
  桶导出；**它是全树最后一个 `@modelcontextprotocol/sdk` import 方**。
- **`packages/ee/`（整目录删除，FR-D2）** — EE 许可，X-6 早已否决 iframe embed。
  唯一障碍是 `web/src/components/custom/home-button.tsx` 从 `ee-embed-sdk` 取一个枚举值；
  该组件经 `builder-header.tsx` 在 `embedState.isEmbedded` 时仍可达，**故不能连组件一起删**，
  改为就地内联那一个 postMessage `type` 字面量（保持逐字节一致）。
  同步清理：`pnpm-workspace.yaml`、根 `package.json`（workspaces + `lint-core`/`test-unit` 的
  `--filter=ee-embed-sdk`）、`tsconfig.base.json`（`ee-embed-sdk` 与 `@ee/*` 两条 paths）、
  `turbo.json` 的 `ee-embed-sdk#bundle`、`web/vite.config.mts` 与 `vite.embed.config.mts` 的 alias、
  `web/tsconfig.app.json` 与 `tsconfig.spec.json` 的 paths、`tools/repoint-to-core.mjs` 的 ROOTS。

### 顺带：`TELEMETRY_ENABLED` 默认翻成 fail-closed

`helper/system/system.ts` 的 `[AppSystemProp.TELEMETRY_ENABLED]: 'true'` → `'false'`，
注释沿用 HERMES-PATCH-019 的写法——**同一类问题**：漏配 env 就往 PostHog 发遥测，
而 `telemetry.utils.ts` 里的 PostHog project key 是**硬编码**的，运维什么都不配就得到一条可用的外网出口。
dev / k8s 已显式设 false，此处是兜底。镜像内实测编译产物为 `TELEMETRY_ENABLED]: 'false'`。

### 量化收益

| 指标 | 改前（`0.88.0-ee-removed`） | 改后（`0.88.0-slim`） | 降幅 |
|---|---|---|---|
| 镜像 `node_modules` | **983MB** | **366MB** | **−617MB / −63%** |
| 镜像总大小 | **1.41GB** | **969MB** | **−441MB / −31%** |
| install 作用域 | 27 个 workspace 成员 | **12 个** | — |

### 验证

1. `npx turbo run build --filter=api --filter=@activepieces/engine --filter=worker --filter=web`
   → **13/13 成功，零 TS 错误**。
2. `pnpm install --no-frozen-lockfile` 重生成锁文件，随后 `--frozen-lockfile` 复验通过。
3. `docker build -t activepieces:0.88.0-slim .` 成功；`--frozen-lockfile` 在 run 阶段过滤后仍通过。
4. **运行验证**（防 `--filter` 漏装导致运行期 require 失败）：新镜像连 dev 的 Postgres/Redis
   （`--network platform-dev-network`，临时库 `ap088_slim_test`）起容器 →
   HEALTHCHECK **healthy**；迁移全量跑完；`/api/v1/health` **200**、`/api/v1/flags` **200**；
   pm2 两进程（app + worker）均 `online`；worker `Connected to API server via Socket.IO`
   并 `Installed engine in sandbox`；**全日志零 `MODULE_NOT_FOUND` / `Cannot find module`**。
   13 件 prewarm piece 的 `ready` 标记齐备、`dist/packages/web` 静态产物完好（与基线镜像逐项一致）。
   测完容器与临时库均已清理。

> 新镜像 tag 为 `0.88.0-slim`，**未覆盖** `0.88.0-ee-removed`，待主会话确认后再切。

### 只报告不动手：`sqlite3` 与 `pglite`

两者体积可观（`sqlite3` 5.3MB + `typeorm` 因它多带一份编译变体、`@electric-sql/pglite` 20MB），
但**都不是死代码**，删除会牵动迁移链，本次不动：

- **`sqlite3`（约可省 34MB，含 typeorm 变体）**：`AP_DB_TYPE` 只剩 `POSTGRES` / `PGLITE`
  （`DatabaseType` 枚举里 SQLITE 早已删除），dev compose 与 `deploy/k8s/activepieces.yaml`
  都写死 `POSTGRES`。但 `sqlite-connection.ts` 仍**活着**：postgres 迁移列表里的
  `MigrateSqliteToPglite1765308234291` 顶层 `import { createSqlLiteDataSourceForMigrations }`，
  而该文件顶层又有 `import 'sqlite3'`。**这是加载期依赖，不是调用期依赖**——迁移内部虽然
  `databaseType !== PGLITE` 就早退（POSTGRES 下实测日志 `Skipping - not PGLite`），
  但模块图在 require 阶段就已把 sqlite3 拉进来（构建期生成 migration-manifest 那步也印证了这点）。
  安全的删法有两条：① 把该 import 改成 PGLITE 分支内的动态 `await import()`——**几行改动、
  收益立刻兑现**，代价是 PGLITE 用户首次迁移时才发现缺包；② 连 `sqlite-connection.ts` +
  142 个 sqlite 迁移目录 + 该迁移一起删——彻底但影响面大，且会让「从 0.84 sqlite 库升级」这条
  历史路径永久消失。**建议走 ①，但需要单独确认我们是否还承诺 sqlite→pglite 的升级路径。**
- **`pglite`（20MB）**：不是测试用依赖。`database-connection.ts` 顶层
  `import { createPGliteDataSource } from './pglite-connection'`，后者顶层 import
  `@electric-sql/pglite` + `@electric-sql/pglite/vector` + `typeorm-pglite`，
  且 `DatabaseType.PGLITE` 是受支持的运行取值，另有 ~12 个迁移用 `isPGlite` 分叉建索引。
  与 sqlite3 同一形状（加载期耦合）。我们全环境跑 POSTGRES，
  **若确认永不支持 PGLITE**，删掉 `PGLITE` 分支可再省约 20MB；否则维持现状。

---

---

## 2026-08-14　HERMES-PATCH-020：依赖面盘点，运行镜像 −441MB

用户问「0.88 是否必要组件外还包含其他」。盘点结论：**是**。详见
IMPLEMENTATION_0.88.md §6.9，此处只记落点。

| | 改前 | 改后 |
|---|---|---|
| 镜像 node_modules | 983MB | **366MB** |
| 运行镜像 | 1.41GB | **969MB**（相对 0.84 的 1.97GB 累计 −51%） |

- **Dockerfile run 阶段**：`pnpm install --prod --frozen-lockfile` → 追加
  `--filter=api... --filter=worker... --filter=@activepieces/engine...`。
  修的是本次改造自己引入的疏漏：删「构建期裁 pieces」块时，连带丢掉了上游用它排除
  `packages/web` 等仅构建期成员的副作用，导致前端依赖进了运行镜像。
- **删 56 个零引用依赖**（api 21 / utils 11 / worker 10 / 其余 4），均逐个 grep 确认。
- **删 3 个孤儿**：`packages/core/ai-providers`、`packages/server/utils/src/mcp-transport.ts`、
  `packages/ee/`（`home-button.tsx` 仍可达，故保留组件、内联其唯一用到的 postMessage 常量）。
- **`TELEMETRY_ENABLED` 默认 `'true'` → `'false'`**（与 PATCH-019 同类：漏配 env 即向 PostHog 发遥测，
  且该文件里的 PostHog key 是硬编码的）。
- **sqlite3 惰性化**：`MigrateSqliteToPglite` 改为分支内 `await import('../../sqlite-connection')`
  （另有一处 `ReturnType<typeof import(...)>` 类型位改写，否则值 import 会把驱动拉回来）。
  代码侧已不再静态引入，但**包仍在镜像**——typeorm 可选 peer 在锁文件里的既有解析，
  拔除需整体重解锁文件（D-13 警告过该路径），为 3.5% 体积不追。

**保留判定**：`@sentry/*`（dsn 空即 return，fail-closed，异常链路在用）、
`@aws-sdk/{client-s3,lib-storage,s3-request-presigner}`（s3-helper 在用）、
`pglite`（受支持运行值 + 约 12 个迁移分支，不值得碰）。

**验证**：CE 编译零错误；新镜像真起容器 healthy、零 `MODULE_NOT_FOUND`；
切进 dev 栈后端到端 SUCCESS、13/13 图标 200、同步与遥测闸门均确认关闭。
`--filter` 风险点（entrypoint 的 `require('jsonwebtoken')`）已单独验过。

---

## 2026-08-14　HERMES-PATCH-021/022：按「实际暴露面」二次盘点

第一轮（PATCH-020）按**依赖面**查；本轮改按**实际路由与鉴权**查，又找出四处——
说明按功能域裁剪会漏掉挂在别处的暴露面。

### PATCH-021　删除三处无消费者的暴露面

| 删除 | 依据 |
|---|---|
| `flows/flow/human-input/`（`GET /v1/human-input/{form,chat}/:flowId`） | **两个都是 `securityAccess.public()`（未鉴权）**；服务的 web `forms`/`chat` feature 已随 FR-D2 删除；且 `piece-forms`/`piece-chat`/`piece-approval` **都不在 13 件白名单**，任何 flow 都不可能有该类触发器 ⇒ 永不可能有合法调用 |
| `core/security/oidc/`（`POST /v1/worker/oidc-token` + `/.well-known/openid-configuration` + `/.well-known/jwks.json`） | worker/engine/web 三处零消费者；discovery 在 `server.ts` **无前缀注册 ⇒ 落在域名根**。**同类先例**：HERMES-PATCH-015 已因相同理由摘掉 MCP OAuth 的 `/.well-known/*`，本组当时漏网 |
| `helper/logs/`（`/v1/logs`）+ web `lib/chat-debug-logger.ts` | 唯一调用方随 chat feature 删除后成孤儿 |

连带：`packages/web/src/lib/api.ts` 的 `disallowedRoutes` 从 11 条剪到 4 条——逐条 grep 后端路由，
指向已删端点的（`/v1/human-input`、`/v1/otp`、`/v1/user-invitations/accept`、4 条 `/v1/authn/*`）全部移除。

**保留**：`collaborativeModule`（web `active-users-widget.tsx` 在用；embed 下虽 `hideActiveUsers: true`，
但 **lock 保护并发编辑**，属数据安全）。

### PATCH-022　凭据注册显式关闭

`POST /v1/authentication/sign-up` 是 `securityAccess.public()`，经 Kong `/api/ap` 路由可达
（该路由无任何鉴权插件，实测返回 400 参数校验而非 401/404）。它**原本已被拒绝**，但靠的是三条
互不相关的条件恰好同时成立：① `ALLOW_OPEN_SIGN_UP` 在默认值表里无条目 ⇒ 读到 `undefined` ⇒ 不等于 `'true'`；
② EE 剥离把 `assertUserIsInvitedToPlatformOrProject` 改成无条件抛错（**是删邀请域的副产品，不是有意门禁**）；
③ CE 下 `getPlatformIdForRequest` 解析到最老平台，不会走 bootstrap 分支。任一环断掉注册就打开。

改为在 `signUp()` 入口显式拒绝。**关键约束**：不能无条件抛——`deploy/k8s/ap-bootstrap-job.yaml`
正是靠 sign-up 在空库上创建 AP 的第一个身份（进而建初始 platform），该路径 `platformId` 为 null。
因此拒绝**只作用于已解析到平台的请求**。连带删除了因此永不可达的「注册进已有平台」整段分支
（认证路径上的死代码易致误判），以及已然失效的 `ALLOW_OPEN_SIGN_UP` 开关（枚举 + validator 条目）——
一个设了也不起作用的环境变量本身就是误导面。

### 实测

- 空库起容器 **healthy**（bootstrap 路径未被打断），零 `MODULE_NOT_FOUND`。
- 端点：`human-input/form` / `worker/oidc-token` / `.well-known/jwks.json` / `v1/logs/client` **全部 404**；
  `/api/v1/flags`、`/api/v1/health` 仍 **200**。
- `/.well-known/openid-configuration` 返回 200 —— 经对照 `/this-does-not-exist` 与
  `/.well-known/totally-made-up-xyz` **三者响应完全一致（SPA 兜底 index.html）**，确认端点已移除，
  200 来自前端 catch-all 而非 OIDC 路由。**只看状态码会误判，必须做对照。**
- 切进 dev 后：端到端 flow SUCCESS、13/13 图标 200、自助注册返回
  `{"code":"AUTHORIZATION","message":"Credential sign-up is disabled…"}` 且 `user_identity` 3→3 未增。

---

## 2026-08-14　HERMES-PATCH-024：telemetry no-op + 最后的外网孤儿

承 PATCH-023（web 外链清理）之后的收尾，均为**已确认零消费者或已被上游开关关住**的项：

- **`telemetry-provider.tsx` 整体改 no-op**。原实现 `posthog.init()` 带**硬编码的上游 project key**
  （`phc_7F92…`）、`ui_host: 'https://us.posthog.com'`，以及 `cross_subdomain_cookie: true`
  ——最后这项会去认领 Activepieces 营销站在 `.activepieces.com` 上设的身份 cookie。
  它原本已双重门控（`TELEMETRY_ENABLED` 经 PATCH-020 已 fail-closed + embed 检查，而 DW 嵌入必然 `isEmbedded`），
  **但「不执行」不等于「不存在」**：key、host、session-recording 采样器都还在发布的 JS 里，离生效只差一个 flag。
  保留 `useTelemetry` 契约为 no-op，10 个调用点无需改动，将来接自建 sink 也留着口子。
- **`flow-hooks.tsx` 的 `useFetchNpmPackageVersion` 删除**：浏览器直连 `registry.npmjs.org`，且零调用点。
- **`packages/core/shared` 的 `supportUrl` / `feedbackUrl` 删除**（含 `index.ts` 桶导出）：
  指向 `community.activepieces.com` / `feedback.activepieces.com`，随 PATCH-023 删掉消费者后成孤儿。
- **SAML SSO 文档链接的 i18n 条目删除**（三语各 1 条）：SAML 登录路径随 FR-D2 已删，该翻译零消费者。

**一处经核查后决定不动**：`add-npm-dialog.tsx` 仍含 `registry.npmjs.org` 调用，但它由上游的
`ALLOW_NPM_PACKAGES_IN_CODE_STEP` 开关控制，而该 flag 的服务端取值是
`EXECUTION_MODE !== SANDBOX_CODE_ONLY`——我们 dev 与 k8s **都是 `SANDBOX_CODE_ONLY`**（D6 沙箱基线），
**实测运行中的 flag 就是 `false`**，按钮在任何环境都不渲染。上游已用正确的机制关住，
再删一遍只会多一处将来要维护的分歧。

**产物**：embed 8.9MB → **8.37MB**；主 chunk **3.75MB**（0.84 为 6.9MB）。
产物内 `phc_7F92` / `us.posthog.com` / `community.activepieces.com` / `feedback.activepieces.com` /
上游 docs·sales·logos·cloud·github **全部归零**；`cdn.activepieces.com` 0（构建期改写为 7 处 `/ap-cdn/`）。

---

## 2026-08-14　0.84 参考树删除

仓库根 `activepieces/`（0.84 硬分叉，**3179 个跟踪文件 / 磁盘 1.7GB**）删除。
FR-A01 原定「保留至新方案验收」——0.88 已交付并实测通过，使命结束。

**可恢复性已先行确认**（删除的前置条件）：该树在 git 中被完整跟踪，
HEAD `4635f7950f` 与冻结基线 `de4f6469` 均含之，因此 `git show de4f6469:activepieces/<path>`
一类的取回指引**仍然有效**——PIECE_DEVELOPMENT_HOWTO §10 无需修改。

删除前修掉两处**真实路径依赖**（否则构建会静默用错文件）：
- `deploy/environments/dev/build-and-deploy.ps1` 的构建指纹输入数组处于**半迁移状态**——
  同一个数组里前三项仍指 `activepieces/{Dockerfile,pnpm-lock.yaml,tsconfig.base.json}`、
  后三项已指 `automation/`，等于用旧树的文件算新树的指纹。
- `deploy/scripts/probe-npm-registry-coverage.ps1` 的锁文件清单同理。

**删除后的全仓残留引用清扫（同日）**——扫描口径
`grep -rn "activepieces/"`（排除 `node_modules` / `dist` / `target`），2834 命中中
2609 条是 `@activepieces/*` **npm 包名**（与目录无关，不动），其余按三类处置：

| 类 | 处置 |
|---|---|
| 指向工作树的路径 | 改指 `automation/` 对应文件（改前逐个确认新树里存在） |
| 镜像名 `activepieces/activepieces`、k8s service、compose 服务名、URL 路径 `/activepieces/`、`*.activepieces.com`、`git show <sha>:activepieces/…` | **不动** |
| 把 0.84 树描述成现存资产的文档表述 | 改为「已删除，可从 git 历史取回」；0.84 专属文档改为顶部加归档横幅，正文不逐句改写 |

操作性改动（非文档）：

- `.claude/scripts/sync-cursor-rules.mjs`：同步目标 `activepieces/CLAUDE.md` → `automation/CLAUDE.md`
  （bucket 与 glob 分类键一并从 `activepieces` 改为 `automation`），并补上 `automation/CLAUDE.md`
  wrapper —— 否则规则同步会静默 `skip (missing)`，`activepieces-vendor` 规则对任何工具都不再生效。
- `.cursor/rules/activepieces-vendor.mdc`：`globs` → `automation/**`（连带三份生成副本），
  `packages/shared` → `packages/core/shared`。
- `.github/workflows/vendor-trim-check.yml`：路径过滤 / `check-tsconfig-paths.mjs` / `working-directory`
  全部改指 `automation`（`node automation/hermes/check-tsconfig-paths.mjs` 本地实跑 exit 0）。
- `.github/workflows/ap-api-tests.yml`：**未改指，加了 INERT 横幅**。它不是路径重命名能修的——
  `hermes/pieces.e2e-fixtures.json` 与其中两个 tarball（`piece-subflows@0.4.11`、`piece-delay@0.3.26`）
  在 `automation/hermes/` 下不存在，且 prewarm workspace 从 `cache/v11` 改成了 `cache/v13`。
  在补齐并跑出一次真绿之前，这道门是停的。
- `.gitignore`：`activepieces/.claude/` → `automation/.claude/`（该忽略规则本已失效）。
- `deploy/k8s/activepieces.yaml`、`deploy/environments/dev/docker-compose.dev.yml`、
  `deploy/scripts/{build-and-push-k8s,probe-npm-registry-coverage}.ps1`、`deploy/pieces/README.md`、
  `frontend/developer-workstation/Dockerfile`、`ServiceTaskBuilderCanvas.vue`、`BUILD_GUIDE.md`、
  `docs/x-ray/architecture/infrastructure-deployment.md`：注释与文档里的路径重指。
- `frontend/admin-center` 三份 i18n 的 `deleteOfficialConfirm`：**用户可见文案**里的白名单路径
  `activepieces/hermes/pieces.json` → `automation/hermes/pieces.json`。

---

## 2026-08-14　api 单测修复 + `ap-api-tests` 门禁重指

**起因**：`.github/workflows/ap-api-tests.yml` 的 `paths:` 一直指着 `activepieces/**`，而 0.88 的树是
`automation/**`——这道门在整个 0.88 改造期**一次都没触发过**。后果不是抽象的：15 个单测文件里的
`vi.mock('.../src/app/ee/**')` 指向 EE 剥离（AG-EE / EE_REMOVAL_PLAN G5）时删掉的模块，没人发现。

### 一、单测：EE 残留的处置

处置原则是**看被测生产代码的现状**，不是改 mock 路径去指 stub（那只会把断言改成永远走 null 分支，
留一堆名不副实的测试）。

**整文件删除**

- `test/unit/app/core/canary/worker-group.service.test.ts`（10 用例）——被测的
  `src/app/ee/platform/platform-plan/worker-group.service` 已随 G5 删除。*本次会话开始后由并行的
  死代码审计 agent 先行删除，此处仅记账。*
- `test/unit/app/core/canary/canary-proxy.integration.test.ts`（8 用例）——整个文件用真 Fastify +
  `@fastify/reply-from` 验证**真实 HTTP 转发到 canary 实例**。CE 里 `isCanaryPlatform` 恒为 false
  （`workers/worker-group-stub.ts`），转发分支不可达；其中 3 个 "fall through" 用例与
  `canary-routing.middleware.test.ts` 重复。

**删除 EE 用例 + 改写为 CE 用例**（生产代码仍在，只是被 stub 关住）

| 文件 | 删 | 留/改 |
|---|---|---|
| `core/canary/canary-routing.middleware.test.ts` | 2 个"为 canary 平台转发"用例；连同已失效的 ee `vi.mock`（**不再 mock stub**，直接跑真 stub） | 3 个守卫用例 + **新增 2 个 CE 锁**：principal / flowId 两条平台解析路径都走通，但 CE 永不转发 |
| `workers/machine/machine-list-filter.test.ts` | 2 个"按平台 worker group 过滤 dedicated worker"用例 + ee `vi.mock` | 5 个 CE 用例；其中"不返回他平台 dedicated worker"改写成 **PLATFORM-scope worker 对任何平台都不可见**（含 platformId == groupId 的刁钻情形）。PROJECT-scope 在 CE 仍然活着（worker 在 socket 握手里自报，见 `machine-controller.ts`），保留 |
| `workers/job-queue/interceptors/rate-limiter-interceptor.test.ts` | **16 个**：CLOUD 套餐档位（FREE/PLUS/TEAM/ENTERPRISE/未知档位共 10 个）、"pool override"、整个 `pool concurrency` 块（5 个） | 16 个 CE 用例 + **新增 1 个 CE 锁**：Redis 里**残留**的 pool 映射 / pool limit / platform plan 三种 key 一律无效——限额仍是 `DEFAULT_CONCURRENT_JOBS_LIMIT`、ZSET 仍按 projectId 建。比"这些 key 不存在"强，它证明的是**即使存在也不生效** |

**新增：stub 契约测试**（stub 的返回值就是本分叉的产品行为，必须有测试锁住，否则将来改回去没人拦）

- `test/unit/app/workers/worker-group-stub.test.ts`：`getWorkerGroupId → null`、
  `isCanaryPlatform → false`、`isWorkerGroupsEnabled → false`。
- `test/unit/app/workers/job-queue/concurrency-pool-stub.test.ts`：`getProjectPoolId → null`、
  `getPoolLimit → null`。

### 二、单测：与 EE 无关的两处真实缺陷（顺手修）

- `workers/machine/machine-service.test.ts`：`system` mock 缺 `getNumber`，导致 `onConnection()` 里的
  worker-capacity 失效广播走进**真 Redis** 路径，两个用例都死在
  `system.getNumber is not a function`。修法是让 mock 的 `system.get(ENVIRONMENT)` 返回
  `ApEnvironment.TESTING`——这正是生产代码用来跳过跨实例广播的开关。顺带把第二个用例传的
  `'my-worker-group'` 字符串改成合法的 `WorkerGroupAssignment`（PROJECT scope）。
- `workers/job-queue/job-broker.test.ts`：mock job 的 `data` 只有 `projectId`/`platformId` 两个字段，
  过不了 `tryDequeue` 里的 `JobData.safeParse` → 每个用例都被判 invalid-schema、job 被 moveToFailed、
  递归后返回 null。补成完整合法的 `EXECUTE_FLOW` payload（`payload` 是
  `{ type: 'inline', value }` 判别联合，不是裸对象）。另删掉一条对 `timeoutInSeconds` 的断言——
  该字段已不在 `ConsumeJobRequest` 契约里，一直读到 `undefined`。

### 三、`ap-api-tests.yml`：重指并真正可跑

- `paths:` → `automation/packages/server/api/**` + `automation/packages/core/shared/**`
  （`packages/shared` 已迁 `packages/core/shared`）；`working-directory` 与 `node-version-file` 同步。
- **移除 INERT 横幅**，改成记录这段历史的说明。
- **加 redis service container**：单测里有两个文件（`job-queue/active-invariant.test.ts`、
  `helper/system-jobs/remove-deprecated-jobs.test.ts`）直连 `AP_REDIS_HOST`/`PORT` 跑**真 BullMQ**，
  而 `.env.tests` 里写的是 compose 主机名 `redis`，在 runner 上解析不到。step env 覆写成
  `localhost:6379`（dotenv 不覆盖已存在的变量，所以 step env 稳赢 `.env.tests`）。
  原文件头声称"api suite needs NO services"是**错的**，已改正。
- **移除 prewarm 步骤与 CE 集成测试步骤**，只留单测门。判断依据：`test-unit` 完全不需要
  `hermes/pieces.e2e-fixtures.json` 与那四个 tarball（本地实测：无 prewarm、PATH 上无 esbuild、
  零 piece 安装，39 文件 361 用例全绿）；需要它们的只有
  `test/integration/ce/flows/flow-run/execute-flow-e2e.test.ts`。**没有取回夹具**：五个文件都在
  git 历史里（`git show 4635f7950:activepieces/hermes/{pieces.e2e-fixtures.json,tarballs/*.tgz}`，
  四个 tarball 全在），但取回等于把 FR-D09 刚清空的 piece 二进制塞回树里；更要紧的是
  **CE 集成套件本身没有在 0.88 上跑绿过**，armed 一道没验证过的门就是重犯这次被修的错。
  重新接回集成腿的配方写进了 workflow 头部（含 `cache/v11` → `v13` 这个 0.88 才有的坑）。

### 四、数字（同一环境：node v24 级运行时 + 可达 redis）

| | 文件 | 用例 |
|---|---|---|
| 修复前 | 6 failed / 32 passed（38） | **32 failed** / 350 passed（382） |
| 修复后 | **0 failed** / 39 passed（39） | **0 failed** / 361 passed（361） |

`pnpm --filter api run test-unit`（CI 里跑的那条命令，带 `--bail 1`）本地实跑绿。
`npx turbo run build --filter=api` 10/10 成功，零 TS 错误。

**两个环境陷阱**（不是代码问题，踩过一次记在这）：

1. `npx` 会把一个 **node 22.13** 顶到 PATH 前面，而 `node:zlib` 的 zstd 是 22.15 才有、
   `file-compressor.ts` 在模块顶层 import 它——于是 **12 个文件在 collect 阶段就死**，
   报 `promisify(undefined)`，看上去像一大片代码坏了。用 `node node_modules/vitest/vitest.mjs`
   或 `pnpm --filter api run test-unit` 绕开。CI 里由 `.nvmrc` + zstd 断言步骤挡住。
2. `.env.tests` 的 `AP_REDIS_HOST=redis` 在宿主上解析不到（dev 的 redis 是容器名
   `platform-redis-dev`，且 `requirepass`，而这两个测试**不发密码**）。本地验证用的是一个临时
   无密码 redis：`docker run -d --rm -p 6380:6379 redis:7.2-alpine` +
   `AP_REDIS_HOST=localhost AP_REDIS_PORT=6380`。

---

## 2026-08-14　删除 0.84 树后的三路审计（残留 / 死代码 / 测试）

### 一、0.84 残留引用（2834 处命中，38 处真改）

严格三分：**2609 处 `@activepieces/*` npm 包名**与约 170 处域名/镜像名/k8s service 名/
`git show <sha>:activepieces/...` 历史路径**一律不动**；38 处工作树路径重指 `automation/`。
8 份 0.84 专属文档（REQUIREMENTS / INTEGRATION_DESIGN / ARCHITECTURE_ANALYSIS / DEPENDENCY_MAP /
EE_REMOVAL_PLAN / VENDOR_TRIM_CHECKLIST / HERMES_PATCHES / STATUS）**加状态横幅而非删除或逐句改写**
——它们作为决策依据的历史记录，改写等于篡改记录。`DECISIONS.md` 只加读法说明（仍是有效 ADR 真源）。

**两处是真实缺陷，不是文字问题**：
- `.claude/scripts/sync-cursor-rules.mjs` 的同步目标是 `activepieces/CLAUDE.md`——该文件在 FR-A04
  删上游 AI 引导文件时就没了。于是 `activepieces-vendor.mdc` **静默地未到达任何工具**。
  已重指 `automation/` 并补建 `automation/CLAUDE.md`；重跑同步确认输出从
  `claude/activepieces` 变为 `claude/automation`。
- `.gitignore` 4 条规则、`vendor-trim-check.yml` 的 CI 路径同样指向已删目录，均已失效，已修。

### 二、死代码与 bug 审计（A 类已修）

| 编号 | 发现 | 性质 |
|---|---|---|
| A1 | **「新建项目」「保存项目设置」100% 返回 400** | **本次移植引入**：0.84 的 `project.controller` 带来 `.strict()` schema（有意 fail-loud），但 web 表单一直多发 `alertReceiverEmail` / `globalConnectionExternalIds`。用仓库实际 zod 版本跑通复现：`unrecognized_keys`。已修两个对话框；顺带移除 Alert Receiver Email 字段（其说明承诺发邮件，而 SMTP 已随 EE 删除、`SMTP_CONFIGURED` 恒 false） |
| A2 | 6 个零注册 `SystemJobName` **在庇护僵尸调度器** | `removeDeprecatedJobs` 跳过所有仍在枚举里的名字 ⇒ 改造前部署遗留在 Redis 的调度器永不被清理，会持续触发进 `getJobHandler` 的 throw。已移出枚举并加入 `deprecatedJobs` |
| A3/A4 | `shared/lib/ee/{agent,secret-managers}` 全死（555 行 + 3 测试） | **纠正前一轮的误判**：当时的「保留」理由经核实是错的（`AgentPieceProps` 实际在 `core/piece-types`；`secret-input.tsx` 早被 FR-D2 削成普通输入框）。零消费者，已删 |
| A5 | `billing-and-telemetry.ts` 的 `captureBillingEvent` **绕过 `telemetryEnabled` 直接 posthog.capture()** | 与 PATCH-019/020 同类的 fail-open，只是在门禁之外。无调用方故无真实泄漏，已连同模块删除 |
| A6 | `web/components/prompt-kit/` 12 文件 1931 行零外部引用 | 且其中 `markdown.tsx:304` 是 web 包**唯一**的 TS 错误。删除后 `tsc --noEmit` 归零 |
| A7-A11 | `helper/errors/catalog.ts`、`list-folders-response.ts`（`FolderDto` 的逐字节重复）、`setPlatformOAuthService`（零调用却让 `oauth2Handler` 看起来可覆盖）、`AGENTS_CONFIGURED` 硬编码 true→false、两个引用已删源码的孤儿测试 | 均零消费者 |

### 三、api 单测与 CI 门禁

**`.github/workflows/ap-api-tests.yml` 的 `paths:` 一直指向 `activepieces/**`**，
而 0.88 的树是 `automation/` ⇒ **该门禁在整个 0.88 改造期间一次都没触发过**，
这正是上述 EE 残留 mock 长期无人发现的原因。已重指并加 Redis service 容器
（原头部注释称「api 套件不需要任何服务」是错的——两个文件真的在驱动 BullMQ）。

⚠️ **一次测量教训**：我最初报「15 文件 / 37 用例失败」是**在错误环境下测的**——
`npx` 解析到 **node 22.13**，而 `node:zlib` 的 zstd 需 22.15+，`file-compressor.ts` 顶层 import 它
⇒ 12 个文件在收集阶段即死。本机 `node -v` 是 22.22.3（有 zstd），`npx node -v` 是 22.13.0（没有）。
用 CI 同款命令 `pnpm --filter api run test-unit` 重测：**358 通过 / 0 失败**，
仅 2 个文件因 `ENOTFOUND redis` 超时；起临时 Redis 后 **3/3 全过**。
**B 类 100% 是环境问题，零真实失败。**

A 类（EE 残留 mock）确实存在并已修：删 EE 断言（`canary-proxy` 整文件 8 例、
`rate-limiter` 32→17 等），**保留并补齐 CE 断言**——新增 `worker-group-stub.test.ts`、
`concurrency-pool-stub.test.ts`，以及一条「Redis 里残留的旧 pool/plan 键是惰性的」的用例
（比断言键不存在更强）。改测试时另揪出两个非 EE 缺陷：`machine-service.test.ts` 的 `system` mock
缺 `getNumber` 导致走真 Redis；`job-broker.test.ts` 的 mock job 过不了 `JobData.safeParse`，
**每个用例都静默走了「schema 非法」分支**——测试一直在测错误的东西。

### 四、HERMES-PATCH-026：拒绝 CLOUD_OAUTH2（气隙凭据外泄）

`app-connection-service.ts` 的 `CLOUD_OAUTH2` 分支会把 **authorization code 与 refresh token
POST 到 `https://secrets.activepieces.com`**。web 入口随 PATCH-023 已删，但**服务端仍接受该类型**，
直接调 API 仍可抵达上游主机。`DEPENDENCY_MAP` 记载的控制手段 `cloudAuthEnabled=false`
**并不存在**：`AppSystemProp.CLOUD_AUTH_ENABLED` 默认 `'true'`、仅被 `flag.service.ts` 读来发布一个 flag、
**无任何服务端代码消费**。已在 API 边界显式抛 `INVALID_APP_CONNECTION` 并删除其后不可达的分支体。
自建 `OAUTH2` 与 `PLATFORM_OAUTH2` 不受影响（凭据不出集群）。

### 产物

embed **8.37MB**（主 chunk 3.75MB），已同步进 DW 并重建镜像，`/dev/` 200。
构建与 `tsc --noEmit` 双零错误。

---

## 2026-08-15　CE 集成套件跑绿 + `ap-api-tests` 补上第二条腿

前一天（[上文](#2026-08-14api-单测修复--ap-api-tests-门禁重指)）只 arm 了单测门，
CE 集成腿留作后续，理由是"它在 0.88 上从没跑绿过，arm 一道没验证过的门等于重犯刚修的错"。
这次把它跑绿并接进门禁。**52 文件 / 372 用例全绿、`--bail 1`、exit 0**——就是 CI 里那条命令。

### 一、夹具：只取回清单，不取回二进制

`hermes/pieces.e2e-fixtures.json` 从 `git show 4635f7950:activepieces/hermes/pieces.e2e-fixtures.json`
取回，**但把四条的 `"tarball"` 字段全删了**。四个版本（webhook@0.1.29 / subflows@0.4.11 /
data-mapper@0.3.15 / delay@0.3.26）实测在 registry.npmjs.org 上都还在（HTTP 200），
prewarm 的 registry 分支能直接下——于是 FR-D09 清空的 `hermes/tarballs/` 里**一个 piece 二进制都不用放回**。

该文件**已加进 `automation/.dockerignore`**：它是 CI 专用，`pieces.json` 才是运行时白名单唯一真源，
镜像里多一份清单只会诱发"prewarm 错文件"。构建上下文是 `automation/`（见
`deploy/environments/dev/docker-compose.dev.yml` 的 `context: ../../../automation`），
所以 `.dockerignore` 这条确实生效，`COPY hermes/ ./hermes/` 会跳过它。

### 二、pnpm 版本必须两边一致（本次最贵的坑）

第一次全量跑：**2 文件 9 用例红**，其中 8 个是 `execute-flow-e2e.test.ts` 报
`PieceNotFoundError: Piece not found for package: @activepieces/piece-webhook-0.1.29`。
prewarm 明明验证过 `pieces/<name>-<ver>/node_modules/<name>` 存在并写了 `ready`。

真因：**prewarm 与运行时安装器跑的不是同一个 pnpm**。

- prewarm 从 shell 起，拿到系统 pnpm **10.28.0**；
- 运行时安装器 spawn 的是裸 `pnpm`（`pkg-runner.ts`），而测试是由
  `pnpm --filter api exec vitest` 拉起的，`pnpm exec` 把 `packageManager` 钉住的
  **9.15.9** 的 bin 目录塞到 PATH 最前面 —— 于是安装器用的是 9.15.9。

pnpm 9 对着 pnpm 10 写出来的 workspace 做一次 `--filter` 安装（就是装 e2e 那个 ARCHIVE 件），
**会把其余成员的 `node_modules` 清空**，`ready` 标记却原样留着。A/B 实测：

| prewarm | 运行时 install | 结果 |
|---|---|---|
| pnpm 10.28.0 | pnpm 10.28.0（纯 shell） | 四个成员 `node_modules` 完好 |
| pnpm 10.28.0 | pnpm 9.15.9（`pnpm exec` 环境下） | **四个成员 `node_modules` 全空** |
| pnpm 9.15.9 | pnpm 9.15.9 | 完好 |

版本对齐后 `execute-flow-e2e` 9/9 绿。CI 里 `corepack enable` 就能让两侧一致，
但**门禁不靠"应该一致"**：prewarm 步骤加了断言，把 `pnpm --version` 与 `package.json`
的 `packageManager` 比对，不等就 fail。

### 二之二、`pieceCheckIfAlreadyInstalled` 加固（让"被清空"能自愈）

版本对齐只是**不再触发**清空，不等于清空之后能恢复。上面那个状态
（`node_modules` 被清空、`ready` 还在）之所以致命，是因为
`piece-installer.ts#pieceCheckIfAlreadyInstalled` 判"已装"用的是两个都太弱的条件：

1. **只看 `node_modules` 目录在不在**，不看 `node_modules/<pieceName>` 在不在
   —— 而后者才是引擎 loader 真正解析的路径（`piece-loader.ts#resolveInstalledPieceEntry`）。
   改成探 `node_modules/<pieceName>`。`fileExists` 走 `access()`、会跟随符号链接，
   所以 pnpm isolated 布局那个指向 `.pnpm` 的软链只在**目标还在**时算数，
   悬空链接读作"不存在"——正是想要的语义。
2. **`usedPiecesMemoryCache` 这个进程内 memo 会抢在磁盘检查之前答复**。
   实测：只做上面第 1 条、memo 保留，重跑错配场景**依旧 12 次 `PieceNotFound`**
   —— 因为清空发生在 worker 运行期间（同一 workspace 的另一次安装），
   memo 里存的是清空**之前**的快照，磁盘检查根本没机会跑，进程不重启就一直坏。
   **已连同删除**。它省下的是每次 provision 每个 piece 两次 `access()`，
   而同一次 provision 本来就要拷引擎、编译 code step、可能还要 spawn pnpm；
   两个 syscall 换正确性，值。

**同一个错配场景（prewarm pnpm 10 + 运行时 pnpm 9），同一个测试文件，改前改后**：

| | `execute-flow-e2e` | `PieceNotFoundError` | 运行时重装 |
|---|---|---|---|
| 加固前 | **8 failed / 1 passed** | 12 次 | 无（`ready` 在 → 判"已装"） |
| 加固后 | **9 passed** | **0 次** | webhook / subflows / delay 各一次，按需 |

即：清空照样发生（版本还是错配的），但运行时**认出来并重装了**，跑绿。
版本断言防的是"别再触发"，这一条防的是"触发之后还能起来"。

新增 3 条 sandbox 单测把三个分支钉住（`piece-installer.test.ts` 32 → 35 用例）：
`ready` 在但包没了 → 重装并清掉 `ready`；`node_modules/<name>` 是悬空软链 → 重装；
**同进程内**装好之后再被清空 → 不重启也能重装。原有那条
"piece already installed — pnpm install never called" 的夹具同步改成建
`node_modules/<pieceName>`（原来只建空 `node_modules`，在新语义下不算已装）。

> 生产影响：镜像里 prewarm 与运行时同一个 pnpm，本来就不该触发清空；这条加固管的是
> **触发之后**——将来镜像升 pnpm 而 `ready` 缓存跨版本残留在挂卷上时，
> 运行时会重装而不是永久 `PieceNotFound`。

### 三、真缺陷一枚：删除拥有 personal project 的用户必 500

`platform-user-community.test.ts` 的
`Removes a user who owns a personal project on the first attempt` 拿到 **500 而非 204**：

```
update or delete on table "user" violates foreign key constraint "fk_project_owner_id" on table "project"
```

`userService.delete()` 先 `softDeletePersonalProject()` 再 `userRepo().delete()`，
但 soft delete **把行留在表里**，`fk_project_owner_id` 是 `ON DELETE NO ACTION`
（`postgres/1676238396411-initialize-schema.ts`，此后从未改过），硬删用户必然撞约束。
这条用例是 0.88 重写期**新写的**（0.84 树里没有），从来没跑过，断言的是没人实现过的行为。

修法（已征询并采纳）：**移交所有权，保留硬删语义**。
`softDeletePersonalProject` → `retirePersonalProject`，在 soft delete **之前**把
`project.ownerId` 改成平台 owner（`assertNotPlatformOwner` 本来就查了 platform，改为返回
`ownerId` 复用，不多一次查询）。先改 owner 再 soft delete，是为了绕开
"TypeORM `update()` 会不会过滤软删行" 这个不确定性。`delete()` 与 `removeFromPlatform()`
两条路径一致处理。

影响面值得记一笔：managed-authn 每次握手给每个用户建 personal project
（`ensurePersonalProject`），所以这条 500 对本部署是**每个用户都踩**，不是边角。

### 三之二、**昨天 arm 的单测门在干净 checkout 上根本跑不起来**

加固 `pieceCheckIfAlreadyInstalled` 之后重跑错配场景，结果**一点没变**（还是 12 次
`PieceNotFound`）。查下去发现改动压根没生效：`packages/server/sandbox/dist/` 是
**8-14 11:32 的旧产物**，而 `turbo run build --filter=api` **不建 sandbox**（api 不依赖它）。
集成测试是通过 `worker/src` → `@activepieces/sandbox` 加载的，而后者
`main: ./dist/src/index.js` —— 于是跑的一直是旧 dist。

顺着这条线查出更要紧的事：**每个 workspace 包都走 `main: ./dist/src/index.js`**，
而 api 的 `vitest.config.ts` 只把四个别名到 src（`shared` / `pieces-framework` /
`pieces-common` / `server-utils`）。把所有 `dist/` 删掉模拟干净 checkout 后实测：

```
pnpm --filter api run test-unit   →  34 failed | 5 passed (39)
Error: Failed to resolve entry for package "@activepieces/core-utils"
```

**即 2026-08-14 arm 的单测门，在 CI runner 上（无 dist）会整片 collect 失败。**
当时那次"本地实跑绿"是在一台恰好留着旧 `dist/` 的机器上跑的 —— 和这道门本来要防的
"没验证过就 arm" 是同一类错误，只是换了个面目。

修法：**Build 步骤前移到单测之前**，四个 filter 缺一不可：

```
npx turbo run build --filter=api --filter=@activepieces/engine \
                    --filter=@activepieces/sandbox --filter=worker
```

- `api` → 单测要的 `core/*`、`pieces/*`、`server-utils` 各 dist；
- `@activepieces/engine` → `dist/packages/engine/main.js`（`engine-installer.ts` 就复制它）；
- `@activepieces/sandbox` → CE e2e 经 `worker/src` 加载它，**`--filter=api` 不会建**；
- `worker` → 同一张图，显式列出，免得将来新增 import 又静默断掉。

冷启动实测（先 `rm -rf` 全部 dist）：build 21.5s → 单测 39/361 全绿 → prewarm → CE 全绿。

### 四、门禁形态（`.github/workflows/ap-api-tests.yml`）

job 更名 `api unit + CE integration tests`，`timeout-minutes` 30 → 45，步骤序列：

1. **Build**（新增，在单测**之前**）— 见上一节，四个 filter。
2. **Unit tests** — 不变。
3. **Prewarm** — `AP_PREWARM_WORKSPACE="$PWD/cache/v13/common" sh hermes/prewarm-pieces.sh
   "$PWD/hermes/pieces.e2e-fixtures.json"`，前置 pnpm 版本断言。
   **清单路径必须是绝对路径**：脚本先 `cd "$WORKSPACE"` 再 `require()` 清单，传相对路径直接 MODULE_NOT_FOUND。
4. **CE integration tests** — `PATH` 前置 `$PWD/node_modules/.bin`（CODE 步骤 spawn 裸 `esbuild`），
   env `AP_EDITION=ce` + `AP_REDIS_HOST=localhost` + `AP_REDIS_PORT=6379`。

（`execute-flow-e2e.test.ts` 文件头注释里的 `cache/v7/common/main.js` 与 "bun must be
available" 都是 0.84 遗留，已改写成现在这四条前置条件。）

### 四之二、顺手修掉一个会让门禁间歇性变红的 `afterAll`

冷启动那次全量跑出现过 **`1 failed | 51 passed (52)` 文件 vs `372 passed (372)` 用例**
——用例全过、文件却红。原因在 `execute-flow-e2e.test.ts` 的 teardown：

```ts
afterAll(async () => {
    worker.stop()          // ← async，没 await
    await app.close()
}, 15_000)
```

`worker.stop()` 是 `async` 且干实事（`drainInFlightJobs()` → `runtime.shutdown()` → 断 socket）。
不 await 就让 `app.close()` 与还活着的 socket、还在飞的 job 抢跑，机器一慢 teardown 就超
15 秒预算。`packages/server/worker` 自己的两个测试是 `await worker.stop()` 的，只有 api 这两个
e2e 漏了。已给 `execute-flow-e2e.test.ts` 与 `piece-options-e2e.test.ts` 补上 `await`，
hook 预算 15s → 30s。这类"用例全绿但文件红"如果留着，就是一道会间歇性变红的门禁 ——
和"没验证过就 arm"是同一类问题。

**没有用 `pnpm --filter api run test-ce`**：那条 package script 开头是
`export $(cat .env.tests | xargs)`，无条件 export 会把 step env 覆盖掉、
`AP_REDIS_HOST` 打回解析不到的 `redis`。直接调 vitest，让 `vitest.setup.ts` 里的 dotenv
（不覆盖已存在变量）去兜底加载 `.env.tests`。

redis service container 沿用 job 级已有的那个。CE 套件其实主要走 `AP_REDIS_TYPE=MEMORY`
（`redis-memory-server` 会下载 redis 源码在 `node_modules/.cache/redis-memory-server` 里 `make`，
首次几分钟 —— timeout 放宽到 45 分钟的原因之一），env 是与单测腿对齐的保险。

### 五、数字

**冷启动全序列实测**（先 `rm -rf` 全部 `dist/` 与 `cache/`，逐步照 CI 的顺序跑）：

| 步骤 | 命令 | 结果 |
|---|---|---|
| Build | `turbo run build --filter=api --filter=@activepieces/engine --filter=@activepieces/sandbox --filter=worker` | 12/12，19.3s，零 TS 错误 |
| Unit | `pnpm --filter api run test-unit` | **39 文件 / 361 用例全绿** |
| Prewarm | `prewarm-pieces.sh pieces.e2e-fixtures.json` | 4 件，pnpm 9.15.9 |
| CE 集成 | `vitest run test/integration/ce --bail 1` | **52 文件 / 372 用例全绿**，exit 0，97s |

另跑：`@activepieces/sandbox` 包自测 **16 文件 / 225 用例全绿**（含新增 3 条）；
`eslint` 改动文件零 error。

本地验证环境：node v22.22.3（≥22.15，有 zstd）、prewarm 与运行时同为 pnpm 9.15.9、
临时无密码 redis `docker run -d --rm -p 6380:6379 redis:7.2-alpine`。

---

## 2026-08-14　HERMES-PATCH-027：修掉 `check-migrations` 的两处 schema 漂移

`npm run test-api`（pre-push 钩子调的那条）原本链了 `test-ee` / `test-cloud` 两条指向已删目录的腿，
必然失败；删掉后链条变成 `turbo run check-migrations test-ce --filter=api`。`test-ce` 直接绿，
**`check-migrations` 红**——`migration:generate --check` 报出两条"实体没有、数据库有"的差异：

```
ALTER TABLE "project" DROP CONSTRAINT "fk_project_piece_set_id"
DROP INDEX "public"."audit_event_platform_id_created_id_desc_idx"
```

两条都是**裁剪的副作用**，不是上游缺陷；也都不能靠"让检查闭嘴"解决
（给脚本加 `|| true`、或去掉 `--check`，等于把刚修好的守卫重新变哑）。

### 一、`audit_event_platform_id_created_id_desc_idx`：实体表达不了 DESC，所以让实体**不管**它

`1820000000000-AddAuditEventPlatformIdCreatedIdIndex`（上游 0.86.4）建的是

```sql
CREATE INDEX ... ON "audit_event" ("platformId", "created" DESC, "id" DESC)
```

**TypeORM 的 `EntitySchema` 没有任何方式表达列级排序方向**（`EntitySchemaIndexOptions` 只有
`columns / unique / spatial / fulltext / where / parser / sparse / nullFiltered`，没有 `order`）。
所以实体**无法忠实声明**这条索引——照 `['platformId','created','id']` 写出来是一条无序索引，
既不是数据库里那条，也会让 schema builder 认为定义不符而 DROP + CREATE。

**上游为什么没这个问题**：`audit-logs` 在上游是 EE 功能，CE 版**根本不注册 `AuditEventEntity`**，
TypeORM 从来不比对这张表。我们做了 CE 重实现并在 `database-connection.ts:69` 注册了它
（G15 pattern，与 `signing_key` 同一处理），比对面就随之打开、漂移浮现。

索引本身**必须留着**：它是审计列表查询的覆盖索引（`platformId` 过滤 + `buildPaginator` 的
`created`/`id` 倒序），删掉就是拿性能换门禁通过。

**做法**：在 `audit-event-entity.ts` 的 `indices` 里补一条**只报名字、带 `synchronize: false`** 的声明。
TypeORM 0.3.31 的路径已核对：

- `EntitySchemaTransformer.js:257` —— `synchronize: index.synchronize === false ? false : true`，
  `EntitySchema` 的 per-index 开关确实会落到 `IndexMetadata`；
- `RdbmsSchemaBuilder.dropOldIndices()` —— 名字对上后先看 `indexMetadata.synchronize === false` 就
  `return false`，**在比对列之前短路**，所以 DESC 表达不出来这件事不再有影响，也不再生成 DROP；
- `RdbmsSchemaBuilder.createNewIndices()` 与 `Table.create()` —— 都 `filter(... synchronize === true)`，
  所以也不会反过来生成一条 CREATE。

**没有**给整个 `EntitySchema` 挂 `synchronize: false`（该选项在 `EntitySchemaOptions:95` 存在，
`RdbmsSchemaBuilder:132` 的 `entityToSyncMetadatas` 会整表跳过）：那是把 `audit_event` 的**所有**列、
关系、索引一起移出漂移检测——为了一条索引把整张表变成盲区，等于局部关掉刚修好的守卫。
把豁免收窄到这一条索引，表的其余部分仍在检测中。

### 二、`fk_project_piece_set_id`：新增迁移把数据库对齐到实体（方案 1）

`piece_set` 是 EE 域、随 FR-A03 删除（shared 契约 `ee/piece-set`、实体、service 都没了），
但 MIT 区的 `1807000000000-CreatePieceSetTable` **仍然**会建 `piece_set` 表、`project."pieceSetId"` 列
和 FK `fk_project_piece_set_id`；`project-entity.ts` 保留了列（L85）与索引
`idx_project_piece_set_id`（L122），**但不再声明指向 `piece_set` 的关系** ⇒ 数据库有 FK、实体没有 ⇒ 要求 DROP。

新增 `packages/server/api/src/app/database/migration/postgres/1825000000000-HermesDropProjectPieceSetFk.ts`，
形制看齐 1820 / 1824（HERMES 时间戳段接在 1824 之后、`name`/`breaking`/`release` 三字段、幂等 DDL），
并注册进 `postgres-connection.ts` 的 `getMigrations()` 末尾（PGLITE 复用同一份列表，
见 `pglite-connection.ts:12`，所以**只有这一个注册点**）：

- `up()`：`ALTER TABLE "project" DROP CONSTRAINT IF EXISTS "fk_project_piece_set_id"`；
- `down()`：先探 `piece_set` 表是否还在、再探约束是否已存在，然后按 1807 的原定义重建
  （`ON DELETE SET NULL`）。两侧都幂等。
- **没有 `isPGlite()` 分支**：这条迁移不发 `CONCURRENTLY`，事务安全，POSTGRES / PGLITE 走同一条路径。

**为什么选方案 1（只删约束）而不是"彻底清除"**（另删 `project.pieceSetId` 列、
`idx_project_piece_set_id` 索引、`piece_set` 表并改实体）：后者更干净，但**破坏性且不可逆**，
收益只是少一列全 NULL 的惰性列。留下的三样东西没有任何代码读写，是死重不是隐患；
而"把 FK 加回来"是一条 ALTER，"把删掉的行找回来"不是。生产上的不可逆操作要有明确收益才做。

### 三、验证

| # | 命令 | 结果 |
|---|---|---|
| 1 | `npx turbo run check-migrations --filter=api` | `No changes in database schema were found` → **✅ No missing migrations detected**，11/11 tasks |
| 2 | `check-migrations` + CE 集成套件 | check-migrations 绿；CE **52 文件 / 372 用例全绿**（见下面的 node 前置） |
| 3 | `npx turbo run build --filter=api --force` | 10/10，19.4s，零 TS 错误 |
| 4 | `pnpm --filter api run test-unit` | **358 passed / 0 failed**，2 文件因无 Redis 超时（环境），与基线一字不差 |
| 5 | 真 Postgres 全量迁移（临时库 `ap_migcheck_027` on `platform-postgres-dev`，`postgres:16.5-alpine`） | 390 条迁移零错误，1825 最后执行成功 |
| — | `eslint` 改动文件 | 零 error（顺手修掉 `postgres-connection.ts` 里 1824 import 位置的 `import-x/order`，那条在改动前就红） |

第 5 项细节（**不是**跑 PGLITE，是 dev 栈里的真 PG 16.5）：

- 空库从头跑完全部迁移，`HermesDropProjectPieceSetFk1825000000000 has been executed successfully`；
- 跑完核对：`fk_project_piece_set_id` **0 行**；`project."pieceSetId"` 列**还在**；
  `idx_project_piece_set_id` 与 `audit_event_platform_id_created_id_desc_idx` **都还在**，
  后者 `indexdef` 确认仍是 `("platformId", created DESC, id DESC)`；`piece_set` 表还在；
- `migration:revert` 实测 `down()` 可逆：约束被重建，`pg_constraint.confdeltype = 'n'`（= SET NULL），
  与 1807 的定义一致；再 `migration:run` 重新落地；
- `up()` 的 DDL 手工再跑一遍 → `NOTICE: ... does not exist, skipping`，幂等确认；
- 临时库用完 `DROP DATABASE`（**没有**新建容器，直接用宿主 ts-node 打 `localhost:5432`）。

### 四、`npm run test-api` 在本机整链跑不通，但不是代码问题（宿主 node 被劫持）

整链跑 `npm run test-api` 时 `test-ce` **50/52 文件在 import 期就炸**：

```
TypeError: The "original" argument must be of type function. Received undefined
  ❯ packages/server/api/src/app/file/file-compressor.ts:5
    const zstdCompress = promisify(zstdCompressCallback)   // node:zlib
```

`node:zlib` 的 `zstdCompress` 是 **Node 22.15+** 才有的（`.nvmrc` 要 v24.14.0）。宿主 shell 的
`node -v` 是 v22.22.3（有 zstd），但 **npm / npx 解析出来的是另一个 node**：

```
$ npm exec -- node -e "console.log(process.execPath, process.version)"
/Users/qiweige/node_modules/node/bin/node v22.13.0
```

**用户家目录里躺着一个 `~/node_modules/node`（npm 的 `node` 包，v22.13.0）**。仓库在 `~` 之下，
npm 逐级向上拼 `node_modules/.bin` 时把它拼进了 PATH 最前面，于是所有 `npm run` / `npx` 起的进程
都掉到 22.13.0——低于 zstd 的门槛。**与本次改动无关**（改的是实体索引声明与一条迁移，碰不到 zlib）。
处置：`rm -rf ~/node_modules`（或至少 `~/node_modules/node`）；仓库侧不需要改。

绕开它、用宿主 node 直接起 vitest，并补齐 CI 里那两个前置（`prewarm-pieces.sh` 灌四件夹具、
`PATH` 前置 `node_modules/.bin` 供 CODE 步骤 spawn 裸 `esbuild`）：

```
export $(cat .env.tests | xargs)
export PATH="<repo>/automation/node_modules/.bin:$PATH"
AP_EDITION=ce /Users/qiweige/.local/bin/node node_modules/vitest/vitest.mjs \
  run test/integration/ce --bail 1 --passWithNoTests=false
→ Test Files 52 passed (52) / Tests 372 passed (372)
```

（中途少任一前置的实测：只缺 prewarm+PATH 时 `execute-flow-e2e` 那条 `webhook → data mapper → code`
是 `FAILED`，51/52；补齐后全绿。CI 里这两步本来就在，见
[上文](#2026-08-15ce-集成套件跑绿--ap-api-tests-补上第二条腿)。）

### 五、仍是人工判断的地方

1. `audit_event` 的那条 `synchronize: false` 是**局部盲区**：将来若有人改这条索引的定义（列、顺序），
   `check-migrations` 不会提醒。索引定义的唯一真源就是 1820 那条迁移，实体里的声明只是占位。
2. `project."pieceSetId"` / `idx_project_piece_set_id` / `piece_set` 表按方案 1 **留在库里**。
   哪天确认要清，得单开一条 breaking 迁移并同步删实体里的列与索引——那是不可逆动作，要单独裁决。
3. 上面第四节那个 node 劫持是**宿主环境**问题，pre-push 钩子在这台机器上仍会因此红；
   仓库里没有能修它的地方。

---

## 2026-08-15　HERMES-PATCH-028：移除 AP 原生登录页与 `/v1/authn` 死调用

身份模型是「谁进去就是谁」——AP 会话只能由 admin-center 按当前操作人经 managed-authn 换取。
但 AP 自带的登录表单还在，而且它 POST 的 `/v1/authentication/sign-{in,up}` 是 `public()` 端点。

**前端**：`features/authentication/` 整个目录删除（14 文件 / 2,912 行）——登录/注册表单、重置密码、
验证邮箱、修改密码、第三方登录、密码强度校验、auth 模板与动画。删前逐个确认 12 个导出在目录外
零消费者。

`/sign-in` 路由**保留但换成无凭据的会话过期页**：五处守卫（`allow-logged-in-user-only-guard`、
`project-layout`、`default-route`、`project-route-wrapper` ×2）在无会话时会 `<Navigate to="/sign-in">`，
删掉路由它们只会落到 404 页、什么也不说。按钮回 `/__ap/bridge`——那个路径 dev nginx 与 k8s
VirtualService 都有，不是环境特定的。

`/verify-email` 路由删除：它调 `POST /v1/authn/local/verify-email`，而本分叉**没有注册 `/v1/authn`
这个前缀**（实测 404，对照组 `POST /v1/authentication/sign-in` 与 `/v1/platforms` 均返 400，
所以那些 404 是"没有这条路由"而不是"参数不对"）。

`api/authentication-api.ts` 从 9 个方法砍到 2 个，只留还能打到已注册路由的：`switchPlatform`
（`lib/authentication-session.ts` 在用）与 `getCurrentProjectRole`（其实不是 authn 路由，
在 `/v1/project-members` 下）。

`app/routes/redirect.tsx` **只删一半**：第三方登录分支两头都死（后端 404，且成功后跳的
`/create-platform` 路由已随 FR-D2 移除），但同一文件里的 `window.opener.postMessage` 是
**piece OAuth 连接弹窗**在用（`features/connections/utils/oauth2-utils.ts:73` 监听它），
整文件删掉会打断所有需要 OAuth 授权的 piece 连接。

**后端**：`/sign-in` 端点**不删**，改为在控制器加注释说明「集群内可达、边缘终止」的安排。
它必须活着——`deploy/scripts/ap-{bootstrap-shared-account,export,import,import-to-id,verify-provisioning}.js`
与两个 Jenkinsfile 都靠它，且它们走 `AP_INTERNAL_URL` 直连 ClusterIP，不经任何网关。
暴露面由网关侧解决（见 Kong 的 `activepieces-authn-block-route`、dev edge nginx、k8s ap-gateway
VirtualService 三处 404 终止）。

> 为什么不是 IP 白名单：uat/preprod 的使用者本就在公司内网，客户端 IP 是私网段，按 IP 放行
> 等于不设防。真正的分界是「是否经过网关」。

---

## 2026-08-18　HERMES-PATCH-030：自研 piece 启停，替代上游删掉的 platform 级过滤

上游 `DropPlatformPieceFilters1809000000000`（**自带 `breaking = true`**）删掉
`platform.filteredPieceNames` / `filteredPieceBehavior`，替代机制 piece_set 属 EE、已随 EE 剥离。
admin-center 的启停开关因此两头落空——`listPieces()` 的第一步就查那个已删列，
**Automation Pieces 整页 500**（2026-08 UAT 事故）。

代码里 `pieces/metadata/utils/index.ts` 的 HERMES-PATCH-004 注释早已指明缺口与决策点
（"needs a schema decision on the platform entity"），本条就是那个决定。

**存储**：新建 HERMES 自有表 `hermes_piece_block`（`pieces/hermes-piece-block.entity.ts` +
迁移 `1826000000000`），不把两列加回 `platform`。复活一个上游明确删掉的列，会让后来人读迁移链时
无从判断谁是权威；`hermes_` 前缀天然不冲突；且能记下**谁在何时**停用的，
`platform` 上那个字符串数组做不到。

**过滤点**：`pieceMetadataService.list()`，**且只在 list()**。`get()` 保持不过滤，
所以已经引用了被停用 piece 的存量 flow 照常加载、照常执行——停用是「不让人再选它」，
不是「把已有的打断」，与 0.84 的 BLOCKED 语义一致。

**不加缓存**：piece 列表本就每次读库（`dedupe` 只是并发去重不是结果缓存），屏蔽表也每次读，
写入即生效。故意不加——那只会引入「点了停用但设计器还看得见」的窗口期。

dev 实测：写入屏蔽行后（未重启 AP）目录 13→12、csv 消失；`GET /v1/pieces/<被停用>` 仍 200 且
actions 完整；删除屏蔽行后回到 13。

> 踩坑：`check-migrations` 报漂移两次，两次都对。先是实体写 `CURRENT_TIMESTAMP` 而 TypeORM
> 期望 `now()`（Postgres 等价，字面量不同）；改齐后仍红，那是本地 PGLITE 测试库里表已按旧默认
> 建好、`CREATE TABLE IF NOT EXISTS` 不会重建，重置测试库才过。

---

## 2026-08-18　HERMES-PATCH-031：piece bundle 回拉改用集群内地址，修好 Import Piece

Admin Center 的「Import Piece (.tgz)」**必然 500**，日志只有一个光秃秃的
`TypeError: fetch failed`，冒到管理员那里是 `ENGINE_OPERATION_FAILURE`，看不出任何线索。

根因：`piece-installer.ts` 的 `saveBundlesToDiskIfNotCached()` 用 `publicApiUrl` 去 fetch
`/v1/engine/pieces/bundle?archiveId=...`，而 `publicApiUrl` 派生自 `AP_FRONTEND_URL`——
那是**浏览器视角**的地址。这个 fetch 却跑在 **AP 容器内**。dev 里 `AP_FRONTEND_URL` 是
`http://localhost:8085/`（边缘网关），容器内 8085 无监听（实测 `curl` 返 `000`），
于是每次 ARCHIVE 件安装都死在这里。

新增 `AP_INTERNAL_API_URL`，**只覆盖这一处**的 base；webhook URL 仍走对外地址，那个确实必须
外部可达。不设时回退原行为，所以「公网地址恰好能从 Pod 内解析」的部署不受影响。

配置三处同步：`docker-compose.dev.yml`（`http://localhost:80`）、uat/preprod ConfigMap 的
`ACTIVEPIECES_SELF_INTERNAL_URL`（`http://activepieces-service:80`）、`activepieces.yaml` 的 env
接线（`optional: true`）。

dev 实测：修复前 500；修复后上传 243,572 字节的自研件，`file` 表存下**同样字节数**，
元数据提取出 `displayName=Import Smoke Test` / `actions=echo`，`pieceType=CUSTOM`、`archiveId` 非空。

> ⚠️ uat/preprod 的 `ACTIVEPIECES_FRONTEND_URL` 是 `http://hermes-workflow-activepieces.<域>/`，
> **能否从 Pod 内解析回自己未实测**。若不能，那两个环境的 Import 会以完全相同的方式失败——
> 新增的 `ACTIVEPIECES_SELF_INTERNAL_URL` 正是为此。

## 2026-08-27　HERMES-PATCH-032：运行时 piece 安装脱离共享 workspace + 离线 store 真正装满

UAT `https://hermes-uat.hk.hsbc/admin/automation-pieces` 上传自研件 `alphabet-sorter@1.0.0`
失败（UI 显示为"超时"，实为 71 秒后 `ERR_PNPM_META_FETCH_FAIL` → `ENGINE_OPERATION_FAILURE`
→ HTTP 400）。两个独立缺陷叠在一起，任何一个单独修都不够。

### 缺陷一：`--filter` 只窄化"装什么"，不窄化"解析什么"

`EXECUTION_MODE=UNSANDBOXED`（`system.ts` 的默认值）下 `getCustomPiecesPath()` 返回
`getGlobalCacheCommonPath()`——**ARCHIVE 件和全部预热官方件共用 `cache/v13/common` 一个
workspace**。`piece-installer.ts` 过去在这个 workspace 根跑
`pnpm install --filter ./pieces/<新件>`，而每个成员的依赖值都是本地 `bundle.tgz` 绝对路径
（`createPiecePackageJson`），pnpm 每次都会重新解析这些本地 tarball 的传递依赖。于是装一个
**零依赖**的自研件，会去拉 `@zip.js/zip.js` / `unpdf` / `jsdom` / `pg-format`——正是
file-helper / pdf / text-helper / postgres 这四个白名单件的依赖，撞上刻意 fail-closed 的
`NPM_CONFIG_REGISTRY=…invalid/`，重试 10s + 1min 后失败。

**改法**：每个件在**自己的目录**里跑 `pnpm install --ignore-workspace`
（`pkg-runner.ts` 新增 `ignoreWorkspace` 参数；`piece-installer.ts` 的批量+个别回退两段
合并为 `installPiecesIndividually()`，逐件安装、逐件回滚）。新件从此只可能需要**它自己的
闭包**，气隙要求变成可以按件核对。附带解决 `pieceCheckIfAlreadyInstalled` 注释里记的
sibling-pruning 隐患：运行时不再有任何 workspace 级安装，也就不可能清空兄弟件的
`node_modules`。仓库内早有先例——`seed-offline-store.mjs` 自己就用 `--ignore-workspace`，
`PIECE_DEVELOPMENT_HOWTO.md` §2 的 dev 手工预装也是。

失败信息现在带上 pnpm 原文（`Failed to install: <name>@<ver>: <pnpm message>`），
否则管理员在 Admin Center 只看得到"导入失败"。

### 缺陷二：离线 store 一直是空的（8 KB）

`seed-offline-store.mjs` 只读 `pieces.json` 里带 `tarball` 字段的条目，而该字段过去只有两个
自研件带，且两个都零依赖 —— 烘出来的"离线 store"实际是空的。所以补
`AP_PIECES_OFFLINE_INSTALL=true` 不但不够，还会让同一次上传更快地以
`ERR_PNPM_NO_OFFLINE_META` 失败。

**改法**：13 个白名单件**全部**补上 `tarball`（`.tgz` 本就在 `hermes/tarballs/` 里且已 git
跟踪；11 个官方件的 SHA-1 与 registry 的 `dist.shasum` 逐个核对一致）。store 随之含四个真实
外部依赖的完整闭包。脚本对无 `tarball` 的条目改为打 WARNING（此前静默）。
顺带：构建期这 13 个件不再访问 npmjs。

### 验证（本地，pnpm 9.15.9 —— 与镜像同版本）

| 场景 | 结果 |
|---|---|
| 复现：workspace 根 `--filter` 装零依赖件，registry 指向 `.invalid` | 失败，`ERR_PNPM_META_FETCH_FAIL GET …/@zip.js%2Fzip.js`，重试 10s+1min（= 线上 71s） |
| 改后：件自己目录 `--ignore-workspace`，同一个 `.invalid` registry | **成功，691ms**，`node_modules/@activepieces/piece-hash-helper` 符号链接就位、`require()` 通过 |
| 预热的兄弟件（file-helper）在上面两次运行之后 | 仍可 `require()`，未被 prune —— 两种布局可共存 |
| P1 后烘 store | 21 MB / 66 包（此前 0 B）|
| 气隙离线装**有依赖**的件（file-helper，`--offline` + 该 store + `.invalid` env） | **成功，353ms**，`@zip.js+zip.js@2.8.15` 从 store 解析 |
| 对照：同一次离线安装换用**改动前**的 store | 失败，`ERR_PNPM_NO_OFFLINE_META Failed to resolve @zip.js/zip.js@2.8.15` |

`packages/server/sandbox` 全量测试 226 passed（`piece-installer.test.ts` 36 个，含新增的
"never installs at the shared workspace root" 回归用例）、`tsc --noEmit` 与 `eslint` 无 error。

### 仍未做（不在本次范围）

- **UAT 配置漂移**：线上 Pod 的 `AP_PIECES_OFFLINE_INSTALL` / `AP_INTERNAL_API_URL` 为空，
  但 `deploy/k8s/activepieces.yaml` 里两者都已接线 —— UAT 跑的不是当前清单/镜像，需重新部署。
- `prewarm-pieces.sh` 仍在 workspace 根做一次性安装（构建期、可联网、快），只更新了注释说明
  它与运行时布局的关系；两种布局互不影响已实测。
- `code-builder.ts` 的 CODE 步安装没有传 `ignoreWorkspace`，维持原行为。注意
  `cache/v13/codes/<hash>` 之上没有自己的 `pnpm-workspace.yaml`，pnpm 会一路上溯到
  `/usr/src/app/pnpm-workspace.yaml`（AP monorepo）——**未验证其影响**，独立排查。
