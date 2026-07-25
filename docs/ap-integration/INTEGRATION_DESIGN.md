# Document 4 — Activepieces 0.84.0 集成设计（INTEGRATION_DESIGN）

> **定位**：把 Document 1（需求）+ Document 2（源码逆向）+ Document 3/3.5（依赖与 EE 剥离）
> 的结论落成**可施工的分层集成设计**。本文回答的是 **"怎么建"**，不是 "为什么"（在 [DECISIONS](DECISIONS.md)）
> 也不是 "是否可行"（在 [OPEN_GATES](OPEN_GATES.md)）。
>
> **阅读起点**：[STATUS.md](STATUS.md)。本文的每一层都标注**冻结状态**与其**门禁**。

更新日期：2026-07-24

---

## 0. 冻结纪律（每层必须遵守）

沿用 [OPEN_GATES](OPEN_GATES.md) 的原则：**Gate 未过 ≠ 不能设计，而是对应层不得冻结**。
本文每层用三态标注，施工顺序据此排：

| 标记 | 含义 | 可否据以施工 |
|---|---|---|
| 🟢 **FROZEN** | 依赖的 Gate/决策已闭合，设计可作为施工依据 | 可 |
| 🟡 **DRAFT** | 方案已具体化，但门禁为黄（PoC/回归待做），细节可能随验证微调 | 可并行施工，但留回退 |
| 🔴 **BLOCKED** | 依赖红灯 Gate，设计只作占位，**不得据以施工** | 否 |

> **禁止**：把 🟡/🔴 层的设计当成已定案下发实施；任何"提前施工"须在本文该层显式记录风险与回退点。

---

## 0.5 实施进度快照（2026-07-24）

> **本节是本文最新的事实锚。** 自骨架成文后，**地基与 EE 剥离已实际施工并端到端验证**，
> 多个层从"设计"转为"已实施"。下方九层表与各层已同步更新；此处给一屏总览。

**已建成并验证（工作分支 `ap-vendor-ee-removal`，8 个提交）**：

| 交付 | 提交 | 验证 |
|---|---|---|
| **P0-1 Vendor**：AP 0.84.0 pristine 进仓库根 `activepieces/`（D5） | `de4f6469` | 逐字节等于官方 tag |
| **P0-2 去 bun**：工具链 bun→pnpm/Node（X-4） | `af5c77f0` | `tsc` 绿基线（含 ee） |
| **L8 EE 剥离**：删 `app/ee/`（166 文件）+ 12 CE 重写 | `91f01d97` | `tsc` 0、`turbo build` 通、boot 通 |
| **去 bun 收尾**：Dockerfile + 运行时 piece 安装器 | `5ef02e12` `7cd0b185` | 镜像构建、**AI Generate 端到端** |
| **部署接入**：dev compose + k8s 镜像引用 | `d284fd14` `5dcb33fc` | dev 实跑我的镜像、csv/AI Generate 通 |

**端到端实证**（EE-removed 镜像 `activepieces:0.84.0-ee-removed`，对 dev 库拷贝）：
全栈启动（api+engine+worker via pm2）、全新库迁移 **354 条零错误（G16 实证）**、`GET /v1/flags→200`、
**csv flow SUCCEEDED**（http+csv piece pnpm 装载执行）、**AI Generate 契约完整**（deepseek custom provider 正常）。

**关键实施发现**（已入决策/记忆，施工必读）：
- **EE 剥离用编译器驱动**：删 `ee/` → tsc 逐文件逼出 CE→ee 面（108→0）。三处避雷见 [DECISIONS D6/G2](DECISIONS.md#d6)。
- **G2 授权忠实表驱动重写**（AP principal 无 permissions，Q4a/JWT 零回调后置）；**ee-authorization 是混合体**（所有权授权必须保留）。
- **G16 迁移安全**：20 ee 迁移是历史迁移，CE 表由保留的 MIT `Unify` 迁移重建 → 全新库自足（实测 354 条通）。
- **去 bun 两处运行时坑**：piece 安装器 spawn `bun`（改 pnpm **isolated linker**，非 npm——npm hoist 致 `PieceNotFound`）；Dockerfile 全三阶段。
- **⚠️ air-gap 未闭合**：镜像运行时经 pnpm 从 registry 装 piece → 断网 prod 会断，须**构建期预烘焙 11 piece**（已删 14 个 AI/搜索 piece，见 §pieces）（机制现成 `deploy/pieces/`，待 prewarm 去 bun）。

**L7 身份供给已实施**（2026-07-24，Option A）：AP 侧 CE 重写 managed-authn+signing-key（tsc/eslint 0 error）、
HERMES 侧 :8085 桥加 per-user 分流（admin-center BUILD SUCCESS）；余重建镜像 + signing-key 引导 + AG-06 端到端（见 §7.2.1）。

**L2 网关已实施**（2026-07-24）：AP builder API（REST+socket.io）收编进 Kong `/api/ap/*`，dev 实测 per-user token 透传 + WS 升级全绿（见 §4.4）。

**L1 已实施 + 浏览器 E2E PASS**（2026-07-24）：7 注入切点 + lib-mode 构建（✓17s/6.8MB）+ DW Shadow-DOM 包装；**浏览器实测真实 builder 挂载渲染、flow 加载（REST 经 Kong 全 200）、socket.io 连接、Tailwind CSS 隔离、点 Trigger 弹 piece 选择器（Radix Portal in shadow）**。见 §6.5。

**收尾（非阻塞）**：L1 接 DW 的 FU→Service Task 视图 + web-embed 交付落地（AG-02.8）、
C-1 集群 NetworkPolicy、air-gap piece 预烘焙。

---

## 1. 部署拓扑与九层模型

### 1.1 一张图：AP 在 HERMES 中的位置

```
                         浏览器（DW 用户）
                               │
                   ┌───────────┴────────────┐
                   │   Developer Workstation │  ← Vue 3 宿主
                   │   FU → Service Task(AP) │
                   │   [L1] AP Builder 组件   │  （lib-mode + Shadow DOM，挂载进 DW）
                   └───────────┬────────────┘
                               │ REST + socket.io（经 Kong）
                   ┌───────────┴────────────┐
     [L2] 网关     │          Kong           │  ← websocket 升级、路由、限流、鉴权边界
                   └───────────┬────────────┘
                               │ /api/v1/*  +  /api/socket.io
       ┌───────────────────────┴───────────────────────┐
       │            Activepieces 运行体（单部署单元 D）    │
       │  [L3] server/api core（含 Q4a RBAC 小补丁）      │
       │  [L4] engine（execution）                        │
       │  [L5] worker + 沙箱（isolated-vm / D6 基线）      │
       │  [L6] pieces（白名单，离线预装，进 engine 进程）   │
       └───────┬───────────────────────┬────────────────┘
               │ TypeORM               │ BullMQ
       ┌───────┴────────┐      ┌───────┴────────┐
[L9]   │ Postgres        │      │ Redis           │  ← 独立 schema / 生产独立实例
       │ schema=activepieces    │ （AP 专用）      │
       └────────────────┘      └────────────────┘

横切：[L7] 身份与供给（per-user，共享 project）  [L8] EE 剥离与许可边界
安全：D6 沙箱基线 + C-1 NetworkPolicy + C-2 piece 冻结 + C-3 桥加固
```

### 1.2 九层模型（施工索引）

| 层 | 名称 | 主职责 | 源码处置（Doc3） | 门禁 | 冻结 |
|---|---|---|---|---|---|
| **L1** | DW Canvas Host | builder 挂载进 DW（注入切点+lib-mode 构建+Shadow-DOM 挂载） | A（vendored 裁剪 `packages/web` 子集） | AG-01/02/04 | 🟢 **浏览器 E2E PASS**；余接 DW 视图/交付 |
| **L2** | Workflow API & Gateway | Kong `/api/ap/*` 路由 REST+socket.io（per-user 透传），生产仅暴露 webhooks | C（HTTP 保留 `server/api`） | 契约已枚举；AG-03(UI) | 🟢 Kong 路由已实测 |
| **L3** | Core (server/api) | 认证桥、忠实 G2 表驱动 RBAC（Q4a bypass 不需要） | C+D | AG-03 | 🟢 HTTP 回归 PASS |
| **L4** | Engine | 执行链路、EXECUTE_PROPERTY | D（不可拆分） | AG-05/D6 | ✅ **已实施**（dev 实跑，flow 执行通） |
| **L5** | Worker & Sandbox | isolated-vm、网络基线、egress | D（不可拆分） | AG-05/D6 + C-1/C-3 | ✅ **已实施**（D6 基线 in dev）；C-1 待集群 |
| **L6** | Pieces | 白名单 + 离线预装，进 engine 进程 | A/D 混合 | AG-05（FR-F03） | ✅ **运行时装载已实施**；⚠️ air-gap 预烘焙未做 |
| **L7** | Identity & Provisioning | per-user 映射，共享 project，供给端点 | HERMES 新建 ≈580 行 | AG-03/06 + Q4a | 🟢 已实施 + dev 端到端 PASS |
| **L8** | EE Removal & Licensing | 删 app/ee + CE 重写 | B（裁掉 ee/**）+ 5 CE 重写 | AG-EE | ✅ **已实施并端到端验证** |
| **L9** | Persistence | 独立 schema、AP Migration Job、独立 Redis | 一处 DataSource 改 | Q5/§5 | ✅ **迁移已实证**（全新库 354 条）；独立 schema/Redis 待 prod |

> **进度**：地基（L4/L5/L6/L8/L9）与去 bun/vendor **已建成并 dev 实跑**（见 [§0.5](#05-实施进度快照2026-07-24)）；
> 上层 **L1/L2/L3/L7 全部已实施并实测**（2026-07-24）：L1 **浏览器 E2E PASS**（真实 builder 经 Shadow-DOM 挂载、flow 加载、socket 连接、piece 选择器交互）。**九层集成端到端全部跑通**；余收尾（L1 接 DW 视图/交付落地、C-1 集群、air-gap 预烘焙）。EE（L8）在 R-A 下已实施，D4 合规评估仍待正式意见。

### 1.3 横切关注（不属单层，贯穿全栈）

| 代号 | 关注 | 落点 |
|---|---|---|
| X-Contracts | 类型契约同步 | `ap-contracts`（AG-02，Codegen 派生自 vendored shared） |
| X-Security | D6 沙箱降级的补偿控制链 | C-1（L5 网络）+ C-2（L6 piece 冻结）+ C-3（L3 桥）+ C-4（复评） |
| X-Offline | air-gap：无公网、无运行时拉取 | L6 预装、L9 迁移基线、镜像三推 |
| X-Bridge-Retire | :8085 桥并行一版后退役（[Q6](DECISIONS.md#q6)） | L2 收编入 Kong |

---

## 2. L9 — 持久化层 ✅ 迁移已实证（余独立 schema/Redis 待 prod）

> 依据 [Q5](DECISIONS.md#q5) + [ARCHITECTURE_ANALYSIS §5](ARCHITECTURE_ANALYSIS.md)（已按 0.84 核实）。
> **迁移体系已实证**（见 §2.2）：全新库跑 EE-removed 迁移集 **354 条零错误、75 表齐全**，
> 证明 G16 删 20 ee 迁移后全新库自足。独立 schema + 独立 Redis 为 prod 形态，dev 仍共享（下方保留设计）。

### 2.1 独立 schema（同实例）

- AP 全部表落 **`activepieces` schema**，与 HERMES 表（`ac_`/`dw_`/`up_`/`we_` 前缀，`public`）物理隔离。
- **改造点集中在一处 DataSource 配置**（AP 侧 TypeORM 连接设 `schema: 'activepieces'`）；不改表名、不改实体。
- DB 角色边界（强制）：AP 的 DB user **只授予 `activepieces` schema 权限**，无 `public` 访问 ⇒
  即便 L5 网络控制被绕，AP 也无法从 DB 层触达 HERMES 业务数据。**这是 C-1 之外的数据层纵深。**

### 2.2 迁移 ownership（AP 自持，独立 Job）

- **AP migration 由独立 Migration Job 运行**，只管 `activepieces` schema；**不进 HERMES Flyway/init-scripts**
  （HERMES schema 唯一来源是 `deploy/init-scripts/00-schema/`，见 deploy 规则，二者互不侵入）。
- `AP_AUTO_MIGRATE` 矩阵（[对应 P2 决策]）：**dev/sit 可 `migrationsRun: true`**（在线滚动）；
  **生产 = Job 一次性执行 + 应用容器 `migrationsRun: false`**（air-gap：不接受应用启动时的隐式迁移）。
- 基线收编（§5.2）：**0.84 基线 = `getMigrations()` 注册 373 条**（postgres），固化为 `baseline-0.84.0.manifest`；
  自维护增量以时间戳 `> 1793000000000` **append-only** 追加数组末尾（0.84 原生自带 `rollback-migrations.ts` 作守卫）。
  ⚠️ **0.86.2 的 389 条不适用于 0.84 落地**（[Q8](DECISIONS.md#q8) frozen fork，禁用 0.86 结论）。

### 2.3 Redis（生产独立实例）

- **生产必须独立 Redis 实例**，不与 HERMES 共用——eviction/persistence 是**实例级**配置，逻辑 DB 号不够
  （[§5.3](ARCHITECTURE_ANALYSIS.md)）。dev 可共用实例分 DB 号。
- AP 队列（`workerJobs` + `runsMetadata`，0.84 已统一）跑在该实例；BullMQ 语义要求持久化策略与 HERMES 解耦。

### 2.4 施工清单（L9）

1. AP DataSource 设 `schema: activepieces` + 独立 DB user（仅该 schema 权限）。
2. 独立 Migration Job（生产）；应用容器关 `migrationsRun`。
3. 生产独立 Redis 实例 + AP 专用连接串。
4. 存量纠错（§5.4 列出的生产配置清理）随本层一并处理。

---

## 3. L4/L5/L6 — 执行·沙箱·piece ✅ 已实施（dev 实跑；余 air-gap 预烘焙 + C-1 集群）

> 依据 [AG-05](OPEN_GATES.md)（8/8 实测）+ [D6](DECISIONS.md#d6)（基线降级裁决）。
> **这是本次集成安全姿态的核心层**，D6 后基线已冻结，补偿控制链就位。

### 3.1 执行链路（L4 Engine）

- 单部署单元（**D，不可拆分**）：`server/api` + `engine` + `worker` 紧耦合——worker 经 socket.io RPC
  向 api 取任务、engine 由 worker fork、**piece 在 engine 进程内 import**（不进 isolated-vm）。
- `POST /v1/pieces/options`（EXECUTE_PROPERTY）会**同步跑引擎作业**——网关侧特殊处理见 [L2](#4-l2--workflow-api--gateway-🟡-draft)。

### 3.2 沙箱基线（L5）—— **现行 = D6 第 3 级**

| 项 | 值 | 来源 |
|---|---|---|
| `AP_EXECUTION_MODE` | **`SANDBOX_CODE_ONLY`**（isolated-vm，无 isolate 进程沙箱） | [D6](DECISIONS.md#d6) |
| `AP_NETWORK_MODE` | **`STRICT`** | D6 |
| 容器 capability | 仅 **`NET_ADMIN`**，**无 `SYS_ADMIN`** ⇒ K8s PSS `restricted` 可行 | D6 |

**能力面后果（T3，须写入 flow 编写规范）**：isolated-vm 内**无 `fetch`/`require`/Node 内置**；
叠加 FR-F03B（不能装外部 npm 依赖）⇒ **CODE step 只能写纯 JS，HTTP 调用一律改用 http piece**。
dev 已据此把 `csv` flow 的 Fetch File 步改为 http piece 并实跑 SUCCEEDED。

### 3.3 D6 补偿控制链（L5 网络 + 跨层）

| 控制 | 层 | 状态 | 落点 |
|---|---|---|---|
| **C-1** NetworkPolicy（default-deny egress） | L5 | 🟡 manifest 已编写 | `deploy/k8s/networkpolicy/`；allowlist=DNS/istiod/redis/外部PG/LLM，切断 AP→HERMES 横向 |
| **C-2** piece 白名单冻结 + 离线预装 | L6 | 🟢 已有约束 | 见 [§3.4](#34-pieces-l6) |
| **C-3** 桥加固（`X-Service-Token`） | L3 | ✅ 已实现并实测 | admin-center 凭密钥才信任裸 `X-User-Id`；AP 无密钥 |
| **C-4** 提权放开时回退第 1 级 | 跨层 | ⏳ 留待 | 安全策略/隔离技术变化时复评 |

> **应用层 egress 代理仍在**（piece 打内网/metadata 仍 403），但只覆盖走默认 agent 的库；
> raw socket / http piece `use_proxy` 自定义 agent 可绕 ⇒ **C-1 是 piece 层的兜底,不可省**。

### 3.4 Pieces（L6）—— ⚠️ 实施与设计有一处偏差（air-gap 未闭合）

- **piece 加载机制（已实施，去 bun）**：piece 由 worker 的 `pkg-runner.ts` 装进缓存 workspace
  （`cache/v11/common`），**用 `pnpm install --config.node-linker=isolated`**（不能用 npm——
  npm hoist 会让 engine 的 `piece-loader.ts` 找不到 piece：它在 `pieces/<name>-<ver>/node_modules/<name>` 解析，
  正是 pnpm isolated / 原 bun 的**每-成员 node_modules** 布局）。CODE step 由同文件 `build`（esbuild）编译。
- piece 跑在 engine 进程内（非 isolated-vm），走完整 Node + 应用层 egress 代理；**其出网由 C-1 在 L5 兜底**。
- 白名单：25 个（`deploy/pieces/pieces.json` 27 − approval/todos，[Q9](DECISIONS.md#q9)）；http piece 是 CODE step 的 HTTP 替代（T3）。
- **⚠️ 偏差：当前镜像运行时从 npm registry 装 piece，air-gap（X-3）未闭合。** 设计要求**构建期离线预装**
  （FR-F03A / C-2）。机制现成（`deploy/pieces/` 的 prewarm 层），**唯一待改 = prewarm 脚本去 bun**
  （`bun install` → `pnpm install --config.node-linker=isolated` + 写 pnpm-workspace.yaml/.npmrc，
  base 换 `activepieces:0.84.0-ee-removed`）。见记忆 `ap-debun-runtime-piece-installer`。**这是 prod 部署前的红线。**

### 3.5 施工清单（L4/L5/L6）

1. ✅ 三合一运行体（api+engine+worker 单容器 pm2）——**已实施，dev 实跑**。
2. ✅ D6 基线环境变量（`docker-compose.dev.yml` / `activepieces.yaml` 均已落）。
3. ✅ 去 bun 运行时（pkg-runner pnpm isolated + esbuild）——**AI Generate/csv 端到端验证**。
4. 🔴 **air-gap piece 构建期预烘焙**（11 piece，AI piece 已删，prewarm 去 bun）——**prod 前必做**。
5. 🟡 C-1 NetworkPolicy 填环境 CIDR + 集群验证（生产依赖前必须跑通）。
6. 🟡 flow 编写规范（T3 + FR-F03B）写入开发者文档。

---

## 4. L2 — Workflow API & Gateway 🟢 Kong 路由已实施并 dev 实测（2026-07-24）

> 依据 [ARCHITECTURE_ANALYSIS §1.5](ARCHITECTURE_ANALYSIS.md)（39 端点 + 16 WS 事件 + 9 条硬约束）。
> **2026-07-24**：AP builder API（REST + socket.io）已收编进 Kong（`/api/ap/*` 前缀），
> 替代 :8085 独立 origin 桥（X-Bridge-Retire / Q6）；dev 实测 REST/WS/per-user token 透传全绿（见 §4.4）。

### 4.1 生产暴露面（最小化）

- **生产仅暴露 webhooks**（`/api/v1/webhooks/*`，Path B 同步响应）——AI Generate / Service Task 走此路径。
- **builder 全套 REST + socket.io 仅在 DW 设计态经 Kong 暴露**（[L1](#6-l1--dw-canvas-host-🟡-draft) 挂载时）；
  生产 portal 运行态不需要 builder API 面。

### 4.2 Kong 路由的 9 条硬约束（§1.5 直接输入，逐条落设计）

1. **必须支持 websocket 升级**：`/api/socket.io`，握手 auth `{token, projectId}`。测试/观测 100% 靠它，
   无 HTTP 兜底 ⇒ 不支持 = 画布能编辑但永久 pending。
2. **`POST /v1/flows/:flowId` 是单点写路径**，失败即静默停止落库 ⇒ **最高 SLA**，网关不得随意熔断/重写。
3. **`POST /v1/pieces/options` 同步跑引擎作业** ⇒ **单独放宽超时**，不能当无状态代理。
4. **`/v1/pieces/:name` 含未 encode 斜杠**（`/@activepieces/piece-x`）⇒ 网关路径匹配/重写须按 `/:scope/:name` 双段。
5. **首屏放大 ≈ 2N+2M**（20 步 ~80 请求）⇒ 限流阈值留够。
6. **长轮询/心跳**（15s/5s/30s）⇒ 画布常驻产生持续流量，纳入容量规划。
7. **`/v1/flags` 是配置总线**（13 处 `useFlag`，缺 key 静默隐藏）⇒ 宿主须提供完整 flags map。
8. **`lib/api.ts:41-52` 劫持宿主导航**（`SESSION_EXPIRED`→`window.location='/sign-in'`）⇒ **L1 切点 4 必改**。
9. **`ChatDrawer` 无条件挂载**拉进 EE chat 依赖 ⇒ 不需 chat 则在 L1 剪除（也利于 L8）。

### 4.3 前端相关（移交 L1）

- CE 下 builder 客户端权限静默放行（`/v1/project-members/role` 404 → `checkAccess` fallback true）⇒
  **"UI 可见 ≠ 可执行"**。**服务端 RBAC 已在 [L3](#5-l3--core-serverapi) 证实真实 enforce**（Viewer POST /v1/flows→403），
  故即便前端放行、写操作仍被后端挡；L1 挂载时须让 UI 正确反映（禁用按钮），避免误导。
- flow 编辑锁（`LOCK_RESOURCE` 30s 心跳）对共享 project 有利（防并发），但 DW UI 须正确呈现"被他人锁定"。

### 4.4 施工记录（2026-07-24）：AP builder API 收编进 Kong

**路由方案**（`deploy/kong/kong.yml.template` + `deploy/k8s/config_map/preprod/kong-declarative-config.yml`）：
AP 原生 API 在 `/api/v1/*` + `/api/socket.io`，与平台自身 `/api/v1/*` 冲突 ⇒ 用独立前缀 `/api/ap/*` 收编
（替代 :8085 独立 origin，退役 bridge）。**Kong 不验 JWT**（本网关既有约定）——鉴权由 AP 自身完成（浏览器持
L7 的 per-user AP token）。

| service | url | route（strip_path） | 超时 | 用途 |
|---|---|---|---|---|
| `activepieces-builder-service` | `http://activepieces:80/api` | `/api/ap` ✂ | 300s | REST：`/api/ap/v1/*` → AP `/api/v1/*` |
| `activepieces-builder-ws-service` | `http://activepieces:80/api/socket.io` | `/api/ap/socket.io` ✂ | 24h | socket.io（WS 升级），保住 socket.io 段 |
| plugin rate-limiting（REST 路由级） | — | — | 3000/min | 覆盖首屏 2N+2M 放大 + 长轮询 |

**9 条硬约束落点**：#1 WS 升级→独立 WS service（24h 超时 + protocols http/https，实测 `ENGINE_OPEN`）；
#2 flow 写最高 SLA→不加 retries/熔断；#3 pieces/options 同步引擎→REST 超时 300s；#4 `/@scope/name` 未编码斜杠
→strip 只切 `/api/ap`、其余原样透传（Kong 3.x 不重复解码）；#5 首屏放大→路由级 3000/min；#6 长轮询/心跳→WS 不限流；
#7 `/v1/flags`→经 REST 透传（实测 200）；#8 导航劫持 / #9 ChatDrawer→L1 客户端剪除。

**dev 实测（经 Kong :8000）**：

| 验证 | 结果 |
|---|---|
| `GET /api/ap/v1/flags`（public REST） | 200（AP flags，路径改写 `/api/ap/*`→`/api/v1/*` ✓） |
| `GET /api/ap/v1/flows` + per-user token | 200（**per-user token 经 Kong 透传** ✓） |
| `GET /api/ap/v1/flows` 无 token | 403（拒绝 ✓） |
| socket.io（真实 socket.io-client，WS transport） | `ENGINE_OPEN`（**WS 升级 + 路由透传** ✓） |
| 既有平台路由 | 未回归（DW 上游仍可达） |

**L1 契约**（builder 客户端必须配合）：API base = `/api/ap`，socket.io `path` = `/api/ap/socket.io`
（AP 前端默认硬编码 `/api` + `/api/socket.io`，L1 挂载时须改写这两处基址）。

**prod 注记**：纯运行态 prod（portal only）不需 builder API 面 ⇒ 生产 Kong 应删这两 service（Doc4 §4.1），
仅经 Istio 暴露 `/api/v1/webhooks`（见 `deploy/k8s/activepieces.yaml`）。preprod configmap 已含注记。

---

## 5. L3 — Core (server/api) 🟢 HTTP RBAC 回归 PASS（per-user，2026-07-24）

> 认证桥 + 共享 project RBAC。门禁 AG-03。**L7 落地后本层随之收敛**——见 §5.3 关键发现。

### 5.1 已定（可施工部分）

- **C-3 桥加固已实现并实测**（本层承接）：admin-center 侧 `X-Service-Token` 门禁；见 [D6 C-3](DECISIONS.md#d6)
  与记忆 `service-header-identity-trust`。

### 5.2 授权路径（实证）

DW 用户经 L7 供给为**真实 AP 用户 + project_member（带角色）**后，授权走 AP 原生 v2 链：
`authorizeOrThrow`（`authz/authorize.ts`）→ `AuthorizationType.PROJECT` 路由 → `rbacService.assertPrinicpalAccessToProject`
→ USER 类型 `project_member → project_role → grantAccess(permissions.includes(routePermission))`。
**此路径在 CE 无 edition gate、真实 enforce**（对比：`assertUserHasPermissionToFlow` 的 flow-操作级检查在 CE `return` no-op，
是上游本身设计，非我方改动）。

**dev 实测（重建镜像后，per-user 双角色打同一共享 project）**：

| 端点 / 权限 | Editor | Viewer |
|---|---|---|
| `GET /v1/flows`（READ_FLOW） | 200 放行 | 200 放行 |
| `POST /v1/flows`（WRITE_FLOW） | 201 建流成功 | **403 拒绝** |

同一 WRITE_FLOW 端点因 principal 的 `project_role` 不同而 allow/deny ⇒ **表驱动 per-user RBAC 在 CE 生效**。
角色经外部 token 的 `role` claim 供给（`Editor`/`Viewer`/`Admin`=`DefaultProjectRole`）。全权限面同构（都用
`securityAccess.project(permission)`），flow 读写即代表性证据。

### 5.3 关键发现：per-user 模式下 Q4a bypass 不需要

早期 Doc4 把 L3 定为"[Q4a](DECISIONS.md#q4a) 共享-project RBAC 小补丁（两处切点放行）"。**该补丁的前提是
_共享账号_模型**（单账号需全权 / JWT-carries-permissions 快路径）。**L7 落地后前提消失**：每个 DW 用户是**真实
project_member（带角色）**，忠实 G2 表驱动 RBAC 天然给出正确权限——**无需任何 bypass 补丁**（`rbac-service.ts`
注释里"Q4a bypass 待后加"那层可以不加）。这比打 bypass 更优：无放行豁免可审计、权限粒度原生保留。

### 5.4 AG-03 子项处置

- **AG-03.1 完整权限面回归**：HTTP 侧以 flow 读/写代表性证明（allow/deny 均实测）；全端点同构（`securityAccess.project`）。✅
- **AG-03.2 补丁符合四约束**：**不适用**——未打 Q4a 补丁，改用忠实 RBAC（见 §5.3）。
- **AG-03.3 HTTP + WS 双路径**：HTTP ✅；**WS 在 CE 是 membership-gated**（`websockets.service.ts` 的 `validateProjectId`
  校验 project 成员即放行；flow-操作级权限属 EE，CE 不细分）——即"同 project 成员皆可 TEST flow"，是上游 CE 忠实行为，
  非回归。per-user 隔离仍在（非成员连不上）。
- **AG-03.4 前端静默放行 / AG-03.5 flow 编辑锁**：属**前端（L1）**关注点（`checkAccess` fallback、`RESOURCE_LOCKED` 呈现），
  随 builder 挂载在 L1 确认。

---

## 6. L1 — DW Canvas Host 🟢 浏览器 E2E PASS（2026-07-24）

> builder 组件挂载进 DW。门禁 AG-01（React 版本一致）/ AG-02（workspace 边界，核心已验证）/ AG-04（builder 组件化）。

### 6.1 已定

- 打包候选 = **lib mode + Shadow DOM**（§6.5 P1–P4 框架级 PoC 已过）；React **19**（AG-01，须 dedupe）。
- 契约经 **`ap-contracts`**（AG-02：vendored shared 用 zod 4.3.6、ESM 产物、`lib/ee` 可剥离）。

### 6.2 施工记录（2026-07-24）：7 个注入切点已在真实 AP 代码落地

AG-04 的剩余缺口是"8 注入切点在真实代码上的改造（首要 `ApStorage` 单例）"。已实施——**新增单一宿主配置面
`window.__AP_HOST_CONFIG__`**（`packages/web/src/lib/host-config.ts` 的 `apHost`，懒读、字段全可选、**未设时逐字回退
standalone AP 行为**，故独立 AP 不受影响）。6 切点全部改为经它注入（爆炸半径极小：`API_BASE_URL` 仅 socket-provider 用、
`ApStorage` 仅 2 文件）：

| # | 切点 | 文件 | 改造 |
|---|---|---|---|
| 1 | `ApStorage` 模块单例（token/projectId 唯一物理落点） | `lib/ap-browser-storage.ts` | `getInstance()` 优先用 `apHost.storage`，回退 localStorage——**一处覆盖 api/socket/hooks** |
| 2 | `authenticationSession.logOut()`→`window.location='/sign-in'` | `lib/authentication-session.ts` | 有 `onUnauthorized` 则回调、不导航 |
| 3 | `API_BASE_URL/API_URL = origin/api` 模块常量 | `lib/api.ts` | `request()` 改**懒读** `apHost.getApiUrl()`（宿主可指向 Kong `/api/ap`） |
| 4 | 401/session-expired 强跳 `/sign-in` | `lib/api.ts` | `globalErrorHandler` 有 `onUnauthorized` 则回调 |
| 5 | socket.io 模块单例 `io(origin,{path:'/api/socket.io'})` | `components/providers/socket-provider.tsx` | **改懒建**，`apHost.getSocketBaseUrl()/getSocketPath()`（宿主指向 `/api/ap/socket.io`） |
| 6 | queryClient onError 硬耦合 billing 弹窗 | `app/query-client.ts` | `disableBillingDialogs` 时不开 EE manage-plan 弹窗 |

**验证**：`tsc -p tsconfig.app.json` 0 新增 error（唯一既有 error 在 `prompt-kit/markdown.tsx`，vendored 基线 `de4f6469` 起就在、
与 L1 无关，vite/esbuild 构建不受影响）；**vitest 3/3 PASS**（`test/lib/host-config.test.ts`：默认回退 / L2 `/api/ap` 前缀注入 / 懒读）。
低危保留项（#7 canvas/theme 偏好、#8 i18n `/locales`）可并入 `apHost.storage` 或同源提供，随挂载再定。

### 6.3 DW 挂载契约（供 L1 续建）

宿主（DW）挂载 builder 前设：
```
window.__AP_HOST_CONFIG__ = {
  apiUrl:      `${origin}/api/ap`,          // L2 Kong REST 前缀
  socketBaseUrl: origin,
  socketPath:  '/api/ap/socket.io',          // L2 Kong socket.io
  storage:     <注入的 Storage，写入 L7 per-user AP token/projectId>,
  onUnauthorized: (reason) => <DW 侧重新供给 token / 提示，不导航>,
  disableBillingDialogs: true,               // 无 billing 宿主
}
```
per-user token 由 L7 桥（`/launch`→managed 换取）写入注入的 `storage`；builder 经 L2 `/api/ap/*` 访问 AP。
AP 原生 `EmbeddingProvider`（18 开关：`hideSideNav`/`disableNavigationInBuilder`/`hidePageHeader`…）复用作 UI-chrome 注入。

### 6.4 施工记录（2026-07-24）：lib-mode 构建 + 宿主包装已落地

**AP 侧 mount 入口 + lib-mode 构建（✅ 构建通过）**：
- `src/embed/mount-builder.tsx`：`mountApBuilder(config)` —— 设 `__AP_HOST_CONFIG__`（token/api/socket/embedding.isEmbedded）、
  写 per-user session、`memoryRouter.navigate('/projects/:projectId/flows/:flowId')`、`createRoot(container).render(<App/>)`、返回 unmount。
  **绕开 AP 原生 `ee-embed-sdk` 的 iframe+postMessage**（X-6 拒绝 iframe），复用 `isEmbedded→memoryRouter` + host-config 注入。
- `src/components/providers/embed-provider.tsx`：`EmbeddingProvider` 初始 state 从 `apHost.embedding` 播种（isEmbedded + 18 chrome 开关）。
- `vite.embed.config.mts`：lib 模式（复用 alias/dedupe/react/tailwind，去 checker/html 插件），入口 = mount-builder，全量打包含 React。
- **实测**：`vite build` **✓ 17.11s，6784 模块**；产物 `dist/packages/web-embed/`：`ap-builder.mjs`（入口，`export mountApBuilder`）、
  `mount-builder-*.mjs` **6.8MB**（gzip 1.73MB，含 react-flow/codemirror/shiki/全 builder）、`web.css` **1.2MB**、懒加载 chunk（shiki 语言）+ `locales/`。

**DW 侧宿主包装（✅ vue-tsc 0 error）**：`frontend/developer-workstation/src/components/serviceTask/ServiceTaskBuilderCanvas.vue`——
`attachShadow({mode:'open'})` → **fetch `web.css` inline 注入 shadow `<style>`**（AG-04.4，不进 document.head）→
`import(bundleUrl)` → `mountApBuilder({container: shadow 内 div, flowId, projectId, token, apiUrl:'/api/ap', socketPath:'/api/ap/socket.io', onUnauthorized})`；
`onBeforeUnmount` 调 unmount。props 传 per-user token（L7）+ Kong 前缀（L2）+ `bundleUrl/cssUrl`（交付方式，AG-02.8）。

### 6.5 浏览器 E2E ✅ PASS（2026-07-24）：真实 builder 挂载并交互

在浏览器里把 web-embed 产物 + host 页起于 :5173（CORS 白名单），host 页 `attachShadow` + inline 注入 `web.css` +
`import('./ap-builder.mjs')` + `mountApBuilder({flowId, projectId, token:<L7 per-user>, apiUrl:'http://localhost:8000/api/ap'(经 Kong),
socketBaseUrl:'http://localhost:8000', socketPath:'/api/ap/socket.io'})`。实测：

| 验证 | 结果 |
|---|---|
| builder 挂载 + 渲染（Shadow DOM：画布/Trigger 节点/工具栏/Publish） | ✅ 截图确认 |
| flow 加载（`GET /api/ap/v1/flows/:id`→200 + versions/sample-data/variables/ai-providers 全 200） | ✅ 经 Kong + per-user token + CORS 预检全过 |
| **socket.io 连接**（`connected to socket`，经 Kong `/api/ap/socket.io`） | ✅ 浏览器端到端 WS |
| Tailwind CSS inline 注入 shadow（暗色主题/节点/工具栏样式正确） | ✅ AG-04.4 真实 builder 坐实 |
| **交互 + Radix Portal in Shadow**：点 Trigger 节点 → piece 选择器弹出（Search + Explore/Apps/Utility + Webhook/Schedule 列表） | ✅ AG-04.3 真实 builder 坐实 |

**唯一非致命 404**：`GET /api/ap/v1/users/:id`（builder header 取当前用户）——G8 EE 剥离把 `/v1/users` 收窄为影子记录所致，
builder 优雅降级、正常渲染。**收尾项（非 L1）**：CE user 模块对该 id 返影子记录以消 404。

### 6.6 接进 DW ✅ 已完成（2026-07-24）：Service Task 页签跑真实 builder

E2E 之后已把挂载链接进 DW 应用本体（不再是独立 host 页）：

- **页签**：`FunctionUnitEdit.vue` 第 2 位加 `Service Task`（`ServiceTaskDesigner.vue`）。它读本 FU 的 BPMN，
  取 `serviceType=ap` 服务任务的 `ap:flowId`，向桥要 AP 会话，再挂 `ServiceTaskBuilderCanvas`；多个任务给下拉切换，
  另有空态 / 错误重试态。
- **会话**：`fetchServiceTaskSession()` → `GET /internal/ap/token`（同源、平台 cookie；`managed.enabled` 时为 per-user）。
  同时修了 `/token` 同源分支写死共享账号的疏漏，与 `/launch` 一致按 managed 分流。
- **交付（AG-02.8 定案）= 构建期拷贝**：`scripts/sync-service-task-builder.mjs` 作 `prebuild`，把 web-embed 产物拷进
  `public/service-task-builder/`，由 **DW 自己的 nginx** 提供（无 registry、运行时不出网，合 X-3）。产物是构建物，**gitignore 不入库**。
- **一处必踩的坑**：nginx 默认 `mime.types` **无 `.mjs`**，会以 `application/octet-stream` 下发致浏览器拒绝 `import()`；
  已加 `location ~* \.mjs$ { types {} default_type text/javascript; }`。

**DW 内实测**（截图 + 网络）：页签渲染出该 FU 绑定的 csv flow（webhook→http→csv）及其 step 设置面板；调用全经 Kong `/api/ap` 且全 200，
其中三条正好坐实 L2 硬约束：**`/v1/pieces/@activepieces/piece-*`（#4 未编码斜杠双段）**、
**`POST /v1/pieces/options`（#3 同步引擎作业）**、**`POST /v1/flows/:id`（#2 单点写路径）**。

### 6.8 ⚠️ Shadow DOM 的三个非显然失效点（真实 builder 才暴露）

AG-04 的框架级 PoC（AG-04.2/.3/.4）在**小样本**上全绿，但真实 builder 挂进 shadow root 后又暴露三处失效。
三者共同特征：**在 shadow 树之外都是 no-op，所以上游永远不会遇到**；且都**不报错**，只是样式/弹层静默失效，
布局类照常工作因而极易被忽略。判定手法：拿 embed 与 :8085 独立版的**同一面板**对照。

| # | 现象 | 根因 | 修法 |
|---|---|---|---|
| ⓪ | unpublished-changes 横幅变裸文本、必填红星/开关/pill 边框全丢 | AP 把主题变量（`--background`/`--border`/`--radius`…）声明在 **`:root`**，而 **shadow root 内 `:root` 永不匹配** ⇒ Tailwind v4 的 `bg-background`/`border-border` 等全部解析为空 | 宿主注入样式时 `css.replace(/:root\b/g, ':host')`。注意 Tailwind 自身 `@layer theme` 已写成 `:root,:host` 双写，**出问题的是 AP 应用层**那份 |
| ⓪″ | piece 设置面板所有输入框/卡片边框消失、间距松散 | **Chromium 忽略 shadow 样式表里的 `@property` 注册** ⇒ Tailwind v4 读注册变量的 utilities（`border-style:var(--tw-border-style)`、shadow、transform…）全部 guaranteed-invalid | 抽出全部 `@property` 的 `initial-value`，以 `@layer properties { *, ::before, ::after, ::backdrop { … } }` 追加——**正是 Tailwind 给无 `@property` 浏览器的原生兜底**（44 个变量） |
| ⓪′ | 点"+"加步骤：巨大无样式 SVG/文本散落页面、压在画布后 | Radix **Portal 默认挂 `document.body`（shadow 外）**，样式表在 shadow 内 ⇒ 弹层完全失样式 | **新增注入点 #7 `portalContainer`**：`components/ui/` 的 popover/tooltip/select/dialog/dropdown-menu/sheet/drawer/context-menu/hover-card 各 Portal 用 `container ?? apHost.getPortalContainer()`，另 `prompt-kit/file-upload` 的裸 `createPortal` 同改；standalone 时该值 undefined，行为不变 |

前两项合成宿主侧一个函数 `adaptCssForShadowRoot()`（`ServiceTaskBuilderCanvas.vue`）。
快速自检：`getComputedStyle(el).getPropertyValue('--tw-border-style')` 为空即中了 ⓪″。

> **给 AG-04.3 的更正**：PoC 记的"Radix Portal 零泄漏 body"只在**显式传了 container 的调用点**成立；
> 真实 builder 里绝大多数 Portal 调用点没传，默认仍逃逸到 body。**凡向 Shadow DOM 挂 React/Radix 组件树，
> Portal 容器必须全局改道**，不能依赖个别调用点。

### 6.7 收尾（2026-07-24）

**已完成**：
- **宿主 `document.title` 不再被改写**：AP 每页会设自己的标题（这是唯一逃出 shadow root 的部分）。挂载层用
  `MutationObserver` 钉住宿主标题、卸载时还原。实测页签保持 `Edit Function Unit - Workflow Platform`。
- **i18n locales 路径**（注入点 #8 闭合）：原从 origin 根 `/locales/...` 取，在宿主里会 404、界面退化成裸 key。
  embed 构建定义 `AP_EMBED_BUILD`，`i18n.ts` 据此把 locale 基址解析为 **bundle 自身 URL**。
  实测 `/dev/service-task-builder/locales/en/translation.json` → 200。
**代码已完成、待部署验证**：
- **`GET /v1/users/:id` 404**：该路由原由 `ee/users/user.module` 提供，EE 剥离后未补（G8/R10 的"影子 /v1/users"）。
  已在 CE 忠实重写 `user/user.module.ts`（`GET /:id` + `POST /me` + `DELETE /me/profile-picture`，
  依赖全在 CE：`userService.getOneByIdAndPlatformIdOrThrow` 本就返回 `UserWithBadges`）并注册进 `app.ts`；
  `tsc` 0 error。**尚未随镜像部署**——重建 `activepieces` 镜像需要整个 monorepo 构建，本机内存/负载不足时会在
  `web:build` 阶段颠簸（实测 free ≈60MB、load>25 时 20 分钟无进展），须在机器空闲时重建再复验该 404 消失。

**仍未做**：
- **可选瘦身**：从 bundle 剪 sign-in/sign-up chrome（当前全量产物 21MB 已可用，收益小、回归风险不划算）。
- **C-1 集群验证**：manifest 静态校验通过（1 个 NetworkPolicy、`policyTypes:[Egress]`、5 条 egress 规则），
  但需 operator 填 `__NAMESPACE__` / `__AP_EGRESS_POSTGRES_CIDR__` / `__AP_EGRESS_LLM_CIDR__` 并在真实集群 apply
  （**未纳入 kustomization**，需显式 apply）。
- **air-gap 预烘焙镜像**：实现缺口已闭合（prewarm 去 bun + BASE_IMAGE + 11 piece（AI piece 已删）），但**预烘焙镜像尚未构建与断网验证**。

---

## 7. L7 — Identity & Provisioning 🟢 已实施（两侧编译通过，待端到端复测）

> per-user 映射 + 共享 project。门禁 AG-03/AG-06 + Q4a。**这是上层的地基**（见 §7.3 依赖链）。
> **2026-07-24 更新**：Option A 已施工——AP 侧 CE 重写 managed-authn + signing-key（tsc/eslint 0 error），
> HERMES 侧 :8085 桥加 per-user 分流（admin-center BUILD SUCCESS）。**余 = 重建镜像 + signing-key 引导 + AG-06 端到端**。

### 7.0 ⚠️ 关键发现（2026-07-24）：AG-06 的模板已被 EE 剥离删除

AG-06 原定 "per-user provisioning = 以 AP 自带 **`managed-authn`** 为模板"。**但 `managed-authn` 是 ee**，
已随 EE 剥离（commit `91f01d97`）删除。其 `externalToken()` 正是 per-user 供给：
凭 HERMES 签发的外部 token → `externalTokenExtractor`（用 **signing-key** 验签）→ getOrCreate project+user → 返回 AP token。
**`signing-key` 实体也在 G15 删了。** ⇒ AG-06 的落地路径失效，L7 方案必须修订（见 §7.1 选项）。

### 7.1 当前状态与修订方案

**当时（改名前）= 共享账号**：`ServiceTaskTokenController`（原 `ApTokenController`，:8085 桥）用服务端持有的**共享 AP 账号**签发 token
（nonce 换 token 写 localStorage）。所有 DW 用户共用一个 AP 账号 ⇒ **per-user（Q4）尚未做**，RBAC 退化为"单账号全权"。

**修订选项**（因 managed-authn/signing-key 删除，三选一）：
| 选项 | 做法 | 代价 |
|---|---|---|
| **A. CE 重写 managed-authn**（推荐） | 把 managed-authn 4 文件 + signing-key 实体/验签在 CE 重写（多为 CE-logic getOrCreate，依赖的 projectService/projectMemberService 已在 G2 重写就位） | 中；恢复 AG-06 原设计，最faithful |
| **B. 走 CE 常规认证** | HERMES 按 DW 用户经 AP 的 CE sign-up/sign-in 建 AP 用户 + 发 per-user token | 中；不需外部 token 机制，但编排更手工 |
| **C. 扩展现有桥** | 保留 :8085 桥但从"共享账号"改"per-user 账号"（HERMES 建 AP 用户 + 桥发 per-user token） | 小-中；复用桥，但仍需 per-user 建号 |

- HERMES 侧仍需**新建 ≈580 行**（Phase 0 反推）：身份供给端点 / 用户状态事件通道 / 审计写入接口。

### 7.2 定案（2026-07-24）：per-user + Option A

**用户裁决：要 per-user（审计到人）。** ⇒ 机制取 **Option A（CE 重写 managed-authn + signing-key）**——
它是"外部系统按 external-id 供给用户"的专用路径：HERMES 签外部 token（`externalUserId`=DW 用户 id）→
AP getOrCreate 用户（`user.externalId`=DW 用户 id，audit 天然映射回 DW 人）+ 绑 shared project → 返回 AP token。
per-user 在此模型主要为**归属/审计 + 会话隔离**；访问控制仍由 HERMES 层 + C-3 兜底（AP 内 RBAC 冗余，走 G2 表驱动默认）。

**施工面**：
- AP 侧（vendored CE 重写）：`signing-key` 实体/服务（验外部 token 签名）+ managed-authn 4 文件（service/controller/module/external-token-extractor）+ app.ts 注册。依赖 projectService/projectMemberService 已在 G2 就位。
- HERMES 侧：按 DW 用户签外部 token 的端点/服务 + 接入 :8085 桥（桥从"共享账号"改"per-user 外部 token"）。
- 待验：AG-06 端到端（DW 用户 → 桥 → AP getOrCreate → per-user token → builder 会话归属到人）。

### 7.2.1 施工记录（2026-07-24）：Option A 已落地

**AP 侧（vendored CE，8 文件 + 2 注册，tsc/eslint 0 error）**：
| 文件 | 角色 |
|---|---|
| `signing-key/signing-key-entity.ts` | 依 MIT DDL 忠实重写（表 `signing_key` 建于 MIT 迁移 `1698602417745`+`1698698190965`，`generatedBy` 已被 `1709669091258` 移走 → 实体列 = id/created/updated/platformId/publicKey/algorithm/displayName） |
| `signing-key/signing-key-generator.ts` | RSA-4096；**私钥改 PKCS8**（上游为 PKCS1）——AP 从不重解私钥，HERMES(Java) 用 `PKCS8EncodedKeySpec` 原生解析，免 BouncyCastle（合 X-3 air-gap）。公钥留 PKCS1（jose 可验）。 |
| `signing-key/signing-key-service.ts` | add/list/get/delete（`get` 供 extractor 按 kid 查 publicKey） |
| `signing-key/signing-key-module.ts` | controller+module，`/v1/signing-keys`，`platformAdminOnly`（去掉 ee embed 特性钩子） |
| `managed-authn/lib/external-token-extractor.ts` | 验 kid → publicKey → RS256 验签 → 映射 ExternalPrincipal（faithful；projectRole 默认 EDITOR） |
| `managed-authn/managed-authn-service.ts` | **共享-project 简化版**：删 concurrencyPool(G5桩)/projectLimits(已删)/pieces 过滤，只留 getOrCreate project+user + projectMember.upsert + 发 token |
| `managed-authn/managed-authn-module.ts` | controller+module，`/v1/managed-authn/external-token`，`public()`（认证靠 body 里的外部 token） |
| 注册 | `app.ts` COMMUNITY 分支注册两 module；`database-connection.ts` getEntities 加 `SigningKeyEntity`（表 DDL 已在 MIT 区，无需新迁移） |

**HERMES 侧（admin-center，BUILD SUCCESS）**：
- `ActivepiecesProperties.Managed`：`enabled` / `signingKeyId`(=kid) / `privateKey`(PKCS8 secret) / `projectExternalId`(默认 `hermes-shared`) / `tokenTtlSeconds`。
- `ActivepiecesApiClient.signInManaged(UserPrincipal)`：jjwt 0.12 签 RS256 外部 token（header `kid`；claims `externalUserId=DW userId` / `externalProjectId` / `firstName` / `lastName`）→ POST `/api/v1/managed-authn/external-token` → 返回 per-user `ApSession`。PKCS8 私钥解析后 `volatile` 缓存。
- `ServiceTaskTokenController#launch`：`managed.enabled ? signInManaged(user) : signInShared()`——**默认关闭 → 回退共享账号，不回归 dev**。

**引导（一次性，重建镜像后）**：`POST /v1/signing-keys`（平台 admin）→ 拿 `{id, privateKey}` → `id`→config `service-task.managed.signing-key-id`、`privateKey`→secret `service-task.managed.private-key`、置 `service-task.managed.enabled=true`。私钥仅创建时返回一次。

**待复测**：重建 EE-removed 镜像（现含两 module）→ 引导 signing-key → AG-06 端到端（DW 用户经 `/launch` → managed 换取 → 核 AP `user.externalId == DW userId`、`projectId==共享 project`）。

### 7.3 依赖链（决定上层施工顺序）

当前一切走**共享账号**，per-user 未通 ⇒ **L7 是上层地基**：
```
L7 (per-user 身份供给)  →  L3 (per-user RBAC 回归)  →  L2 (per-user builder API 网关)  →  L1 (builder 挂载 UI)
   ↑ managed-authn 删除，方案待定       ↑ 现共享账号=单账号全权，per-user 才有意义
```
⇒ **L3/L2/L1 的真实施工都依赖 L7 先落地**。当前 L3 只能验"共享账号"基线；L2 现为桥(admin-center)非 Kong 全量 builder API；L1 尚未挂载。

---

## 8. L8 — EE Removal & Licensing ✅ 已实施并端到端验证（2026-07-24）

> 方法论见 [Document 3.5（EE_REMOVAL_PLAN）](EE_REMOVAL_PLAN.md) 的 G1–G20；本节是**施工记录**。
> 提交 `91f01d97`（EE 剥离）+ `5ef02e12`/`7cd0b185`（去 bun 收尾）。**R-A（重写）路径已完成，
> D4 合规评估仍待正式意见**——但 R-A 不被 D4 阻塞（若 R-B 放宽，可回收部分重写，非返工）。

### 8.1 施工方法：编译器驱动

删 `packages/server/api/src/app/ee/`（172 文件）→ 逐轮跑 `tsc -p tsconfig.app.json`，
让编译器逐文件逼出 CE→ee 依赖面（**108 → 96 → 87 → 83 → 72 → 58 → 21 → 17 → 9 → 1 → 0**）。
每个错误按 Doc3.5 分组处置，**逐文件审读**（非盲删——三处避雷见 §8.3）。

### 8.2 处置结果（37 文件 CE→ee 面）

| 处置 | 内容 | 关键文件 |
|---|---|---|
| **Stub**（G4/G5） | secret-managers→identity、concurrency/worker-group→null | `app-connection/secret-manager-stub.ts`、`workers/*-stub.ts` |
| **Delete** | alerts、piece 过滤、apikey/otp、email、embed、chat、git-sync、plan/stripe/openrouter、SAML、custom template、CLOUD-only 端点、active-flows 限额 | ~20 文件删分支/删调用 |
| **Reimplement（G2 安全核心）** | 忠实表驱动 RBAC：project_role/project_member 实体+服务+rbac-service，原生语义 1:1 | `project/project-role.*`、`project/project-member.*`、`core/security/v2/authz/rbac-service.ts` |
| **Reimplement（G6/G15）** | /v1/projects CE 控制器（Mount 白屏级）、concurrency_pool 实体（project.poolId FK）、ee-authorization 所有权授权 | `project/project.{controller,module}.ts`、`project/concurrency-pool.entity.ts`、`core/security/platform-authorization.ts` |
| **G15 实体** | 保留 3（project_role/member/concurrency_pool）+ 删 16 ee 实体 | `database/database-connection.ts` |
| **G16 迁移** | 删 20 ee 历史迁移（方案 A 直删） | `database/postgres-connection.ts` |

### 8.3 三处避雷（逐文件审读的价值）

1. **`ee-authorization` 是混合体**：计划门 no-op、但 `platformMustBeOwnedByCurrentUser` 是**真实 admin 所有权授权**——
   整体删或统一 no-op 会把管理端点对所有人开放。已在 CE 位置忠实重写，保留所有权检查。
2. **`ai-provider` 的 EE 剥离没伤 AI Generate**：`enrichWithKeysIfNeeded`（openrouter+plan）只服务托管 credits provider，
   **AI Generate 的 deepseek custom provider 完全不走此路径**——端到端实测确认（契约完整、doc 5340 字）。
3. **G2 授权**：AP `UserPrincipal` **不携带 permissions**，plan 的"JWT 零回调"在 vanilla AP 不成立 →
   **忠实表驱动重写**（project_member→project_role.permissions），Q4a shared-project 放行/JWT 零回调作 HERMES 集成层**后置**。

### 8.4 G16 迁移安全性（实证）

20 个 ee 迁移是 2023–24 **历史迁移**；CE 需要的表（platform/project_member/project_role/flow_template/concurrency_pool）
由**保留的 MIT `migration/` 目录**（尤其 `1764867709704-UnifyCommunityWithEnterprise`）独立重建。
⇒ 方案 A 直删在全新库安全，**实测：全新库 354 迁移零错误、75 表齐全**。

### 8.5 验证

`tsc` 0 + `turbo build --filter=api` 通 + 全栈 boot（edition=ce）+ **AI Generate/csv flow 端到端 SUCCEEDED**。
详见 [§0.5](#05-实施进度快照2026-07-24) 与记忆 `ee-migration-schema-ownership`。

---

## 9. 强制验证项（进 TEST_STRATEGY / Document 5）

汇总各层的验收门（详见各 Gate）：

| 项 | 归属层 | 门 |
|---|---|---|
| C-1 集群内 egress 验证（AP→HERMES 必失败，redis/DNS/AI-Generate 必成功） | L5 | D6 C-1 |
| C-3 三态（无/错/正确 token → 500/500/201） | L3 | ✅ 已过 |
| Shadow DOM 样式隔离（GW-8）、无 bun/无 ee 扫描（GW-11）、workspace 合并回归（GW-12） | L1/L8 | AG-02/EE |
| Q4a 共享 project 权限回归、"UI 可见≠可执行" | L2/L3/L7 | AG-03 |
| AG-05 8 子项（已过）+ 生产 K8s 落地（D6 后不再需 SYS_ADMIN 豁免） | L4/L5 | ✅/D6 |
| Golden Workflows GW-1~GW-14 终验 | 全栈 | [REQUIREMENTS §9](REQUIREMENTS.md) |

---

## 10. 施工路线图（更新：地基已建成）

| 阶段 | 层 | 状态 |
|---|---|---|
| **P0 基座** | Vendor + 去 bun + Docker 镜像 + dev/k8s 部署接入 | ✅ **完成**（`de4f6469`…`5dcb33fc`） |
| **P1 地基** | L8 EE 剥离、L4/L5/L6 执行沙箱、L9 持久化（迁移） | ✅ **已实施并 dev 实跑**；余 air-gap 预烘焙 + C-1 集群 |
| **P2 上层并行** | L1 builder（AG-04 真实挂载）、L2 网关、L3 RBAC 回归（AG-03）、L7 供给（AG-06） | 🟡 **未施工**（下一阶段，各自黄门） |
| **P-prod 前置** | ① air-gap piece 预烘焙（25，prewarm 去 bun）② C-1 集群 NetworkPolicy + C-3 已就位 ③ 独立 schema/Redis（Q5） | 🔴 **prod 红线**，dev 不阻塞 |
| **P4 收编** | X-Bridge-Retire（:8085 桥退役，Kong 收编） | [Q6](DECISIONS.md#q6)，L2 稳定后 |

> **地基（P0/P1）已完成并在 dev 实跑**；下一步是 **P2 上层**（builder/网关/身份，本 session 未动）
> 与 **P-prod 前置**（air-gap 预烘焙 + C-1）。D4 合规评估仍待正式意见，但 R-A 实施不被其阻塞。
