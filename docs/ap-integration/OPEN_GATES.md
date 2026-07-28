# Open Architecture Gates（AG）— 唯一阻塞项总账

> **编号纪律**：本文件是**唯一的"阻塞项"总账**。
> - `AG-xx` = 架构门（阻塞设计定案）
> - `AG-xx.n` = 该门下的具体验证子项（原 `SG-1~8`、`P1~P5` 已并入，不再独立编号）
> - `GW-x`（Golden Workflow）= **最终验收**，不是门禁，见 [REQUIREMENTS §9](REQUIREMENTS.md)
> - `Q/D-x`（决策）见 [DECISIONS.md](DECISIONS.md)
>
> Gate 未通过 ≠ 不能工作，而是**对应设计不得冻结**。

---

## 状态总览

| ID | Gate | 状态 | 阻塞对象 |
|---|---|---|---|
| **AG-02** | **Frontend workspace 边界**（ap-contracts 落地） | 🟢 **核心已验证**（独立编译/零后端依赖/无 bun/DW 消费全绿）；余 CI 与 Codegen 实施 | Doc4 前端集成层 |
| AG-EE | **EE 剥离闭合**（EE-4 清零） | 🟢 **出口标准已达成**（删 `app/ee/` + 12 CE 重写；CE 编译 0 error、启动、dev 端到端处理请求）；余文档/CI 子项 .1/.5/.6 | — |
| AG-01 | React version consistency | 🟡 版本已定，真实 dedupe 待验 | Doc4 Layer 1 |
| AG-03 | 共享 project 完整 RBAC + 资源所有权 | 🟢 HTTP RBAC 回归 PASS（2026-07-24） | — |
| AG-04 | Builder 组件化 | 🟢 浏览器 E2E PASS + 已接进 DW Service Task 页签（2026-07-24） | — |
| AG-05 | Sandbox + offline + prebuilt piece 联合验证 | 🟢 **8/8 子项 PASS**；**基线经 [D6](DECISIONS.md#d6) 降级为 `SANDBOX_CODE_ONLY`+`STRICT`**（不再申请 SYS_ADMIN）；余：**C-3 已实现并实测**，C-1 待集群（填 CIDR + 显式 apply） | Doc4 Layer 3–5 |
| AG-06 | Per-user provisioning 策略 | 🟢 实现完成 + dev 端到端 PASS（Option A，2026-07-24） | — |

> **AG-EE 为 D3 新增**，其实施方案见 Document 3.5（`EE_REMOVAL_PLAN.md`）。

---

## AG-02 — Frontend workspace 边界 🟢 **核心已验证（2026-07-23 实测）**

> 已按 [D2](DECISIONS.md#d2)/[D5](DECISIONS.md#d5) 重定义。**实证结论见下，三处前提被推翻。**

### ✅ 实测结果

| 子项 | 结果 | 证据 |
|---|---|---|
| AG-02.2 独立编译 | **✅ PASS** | AP `shared` 脱离 monorepo（去 `paths`/`baseUrl`）后 **202 模块、0 错误**；产物 202 js + 202 d.ts / 4.1M；839 个导出符号 |
| AG-02.3 零 AP 后端依赖 | **✅ PASS** | `npm install` 后 **无 fastify / typeorm / bullmq / isolated-vm / ioredis / pg**；shared 仅 8 个轻量运行时依赖（zod / dayjs / nanoid / semver / socket.io-client / deepmerge-ts / ipaddr.js / tslib） |
| AG-02.4 pnpm/npm 唯一化 | **✅ PASS** | 全链路 **npm，零 bun**；shared 无服务端耦合（fs/typeorm/fastify/bullmq/ioredis 全 0 命中） |
| **DW 侧消费** | **✅ PASS** | 最小 Vite+Vue 工程 `import { FlowOperationType, FlowTriggerType, FlowActionType, isNil }` + `import type { FlowVersion, PopulatedFlow }` → **282 模块转换、构建成功、124 kB（gzip 28 kB）**，tree-shaking 生效 |
| AG-02.5 应用无回归 | **范围缩小** | **仅 DW 受影响**（见下 F3），其余 3 应用完全不碰 |

### 🔑 三处被实测推翻的前提

**F1 — AG-02.1 的前提错了：AP shared 用的是 zod，不是 TypeBox。**
`packages/shared` 依赖 **zod 4.3.6**（117 文件使用），**typebox 0 命中**；`packages/web` 同样只有 zod。
而 HERMES frontend **完全没有 zod 依赖**，其 `@sinclair/typebox 0.34.52` pin（commit `3befe97f`）
是**传递依赖版本锁定，与 AP 无关**。⇒ **不存在 typebox 版本冲突**；zod 是纯新增依赖，无冲突。

**F2 — frontend 不是 pnpm workspace（也不是 npm workspace）。**
全仓**无 `pnpm-lock.yaml`**；4 个应用各有自己的 `package-lock.json` + 独立 `node_modules`；
`frontend/package.json` **无 `workspaces` 字段**；构建脚本逐应用 `npm install`。
`pnpm-workspace.yaml` 与 `packages/core`（2026-06 建）**均为未生效的遗留物**——`packages/core`
**零应用引用**，其 README 自述用于"消除复制粘贴"，但至今没接线。
⇒ **D2/D5 中"frontend workspace 成员"的表述需按实际机制改写**（见下 §交付机制）。

**F3 — 爆炸半径是 1 个应用，不是 4 个。**
仅 **DW** 需要 ap-contracts；admin-center 的 AP 代码只是 HTTP 调用（`api/ap.ts` 的 `launchActivepieces`），
不需要 flow schema 类型；user-portal / login 与 AP 零关系。
⇒ CR-04 的"全 workspace 依赖对账"风险**进一步下降**；AG-02.5 实际只需 DW 单应用回归。

### ⚠️ 实测发现的两个必须处理的技术点

**T1 — CJS 产物无法被 Vite 具名导入（必须产出 ESM）**
AP shared 的 `package.json` 是 `"type": "commonjs"`，`tsc` 产物用 `__exportStar` 桶式再导出。
**Rollup/Vite 无法静态分析 `__exportStar` 链**，导致 `"isNil" is not exported`（运行时其实存在）。
✅ **解法已验证**：ap-contracts 增加 **ESM 构建**（`module: esnext` + `moduleResolution: bundler`）
并配 dual exports（`import` → ESM、`require` → CJS）。改用 ESM 后 282 模块正常转换、构建通过。

**T2 — `lib/ee` 可干净剥离，且应当剥离**
`shared/src/index.ts` 的 barrel **导出了 19 个 `lib/ee/*` 模块**（chat/billing/api-key/otp/
project-members/oauth-apps/audit-events/git-repo/…）。实测：
- 移除全部 19 行 → **0 编译错误**；非-ee 模块**无任何内部交叉引用**；
- 类型级探针（web 实际导入的 **440 个符号**）：带 ee 时 **全部解析**，去 ee 后**仅 5 个失败**——
  `CreateApiKeyRequest` / `CreateOtpRequestBody` / `ListProjectMembersRequestQuery` /
  `UpdateProjectMemberRoleRequestBody` / `UpsertOAuth2AppRequest`，
  **5 个全部属于 Doc3.5 已裁定删除的 EE 域**，且只被非-builder 页面使用。
✅ **裁定：ap-contracts 剥离 `lib/ee`**，彻底消除许可歧义（`shared/lib/ee` 虽按 LICENSE 文本属 MIT，
但 [D4](DECISIONS.md#d4) 合规评估未启动，剥离后无需等结论）。

### 📦 交付机制（AG-02.8，实测可行方案）

因 F2（无 workspace），`ap-contracts` 的交付方式实测采用 **`file:` 依赖**：
```json
"dependencies": { "@hermes/ap-contracts": "file:../packages/ap-contracts" }
```
**已在 PoC 中验证可用**（npm 原生支持，构建通过）。备选：构建期拷贝进 DW 源码树 / 私有 registry。
三者**都须离线可用**（X-3）；`file:` 方案天然满足，是当前最低摩擦选项。

### 剩余未验子项

| 子项 | 状态 |
|---|---|
| AG-02.1（改述为 **zod 对齐**，非 typebox） | ✅ 无冲突（HERMES 无 zod，纯新增） |
| AG-02.5 DW 真实工程回归 | ⏳ 待在真实 DW 上跑（PoC 用的是最小 Vite 工程） |
| AG-02.6 CI 路径过滤 | ⏳ 待实施 |
| AG-02.7 Schema Drift（Codegen + CI 新鲜度校验） | ⏳ 待实施——**canonical = `activepieces/packages/shared`**，ap-contracts 为派生物 |
| AG-02.8 builder 组件交付 | ✅ **已定案 = 构建期拷贝**（`prebuild` 把 web-embed 拷进 DW `public/service-task-builder/`，DW 自身 nginx 提供；无 registry、运行时不出网；产物不入库）。⚠️ nginx 默认 mime.types 无 `.mjs`，须显式 `default_type text/javascript` |

### 失败回退

依赖冲突不可调和 → 局部 `overrides` 隔离；若整体不可调和 → 回 [DECISIONS.md](DECISIONS.md) 重议 Q1/D5。

---

## AG-EE — EE 剥离闭合 🔴

### 须证明

**EE-4（CE 运行时硬依赖）清零**：全部转为 EE-1/EE-2 移除，或 EE-3 经
EXTRACT / REIMPLEMENT / REPLACE 解决，使 CE 可在"无 EE 专有业务功能与专有运行能力"前提下
编译、启动、处理请求。

### 现状（实测，DEPENDENCY_MAP §2.2.4）

- CE 有 **105 处 import `app/ee/**`**（37 文件）；排除 ee **编译期即失败**；
- **13 条 EE-4 硬依赖打在主干**：每个 project 级 HTTP 请求（`authorize.ts:102`）、每次 WS 握手、
  每次读连接/flow 执行取连接、每次入队 job、`/v1/projects` 唯一实现等；
- 19 个 ee 实体无条件注册、20 个 ee 迁移在无条件数组中；
- **利好**：表 DDL 在 MIT 迁移区、实体映射才在 EE 区 ⇒ EntitySchema 可依 MIT DDL 独立重写。

### 子项

| # | 子项 | 通过标准 |
|---|---|---|
| AG-EE.1 | 依赖清单完备 | EE→Core / Core→EE / Web→EE / Build→EE / Test→EE / Runtime→EE 六向清单齐全 |
| AG-EE.2 | 逐条替代方案 | 每条标注 Delete / Stub / Replace with CE / Reimplement / **HERMES-native** |
| AG-EE.3 | **工作量精确拆分** | 1000–1300 行按 EE removal / Bun→pnpm / Vue 集成 / offline / RBAC / HERMES API / 安全修复 分类（当前只有总量，不足以支撑成本判断） |
| AG-EE.4 | **HERMES 既有能力复用核查** | 逐条确认 RBAC / Authentication / User / Project / Permission / Audit / Billing / SSO —— **HERMES 已有的不得重新实现 AP EE 逻辑** |
| AG-EE.5 | 8 条未核实项补测 | R14–R21（otp / embed-subdomain / flags 里的 federatedAuthn·smtp / template / git-sync / project-state / chat-rpc）的 edition 门 |
| AG-EE.6 | CI Guard 重定义 | GW-11 从"扫 `packages/ee` 目录"改为"扫已裁定必须移除的具体单元" |

### 关联

实施方案 = **Document 3.5**（`EE_REMOVAL_PLAN.md`，见 [D3](DECISIONS.md#d3)）。
合规评估（R-B）状态见 [D4](DECISIONS.md#d4)：**尚未启动**；在得到正式合规意见前按 R-A（重实现）推进。

---

## AG-01 — React version consistency 🟡

- **须证明**：0.84 web 的 React 主版本事实无误；**且** DW 宿主与 builder dedupe 到**唯一** React 实例。
- **已有证据**：0.84 实测 `react`/`react-dom`/`@types` 均 **19**（早前文档误写 18，已修正）；
  AG-04.1 在独立 PoC 中验证 React 19 单实例挂载零报错。
- **剩余缺口**：真实 DW（合并后单一 pnpm 锁）中的 dedupe **未验**；与 AG-02 强耦合。
- **退出条件**：DW 真实工程内 react/react-dom 解析为单一 19.x，builder 挂载无 hook 错误。
- **失败回退**：pnpm `overrides` 强制去重；仍不行则 builder 预打包内联 React（放弃 external）。

---

## AG-03 — 共享 project 完整 RBAC + 资源所有权 🟢 HTTP RBAC 回归 PASS（2026-07-24）

- **已证（ARCHITECTURE_ANALYSIS §2.6）**：Flow / App Connection / Run / Trigger / Webhook
  **全部 project-scoped**，`ownerId` 仅展示、不参与访问控制；凭据全局 key 加密；
  "User A 建 Connection、User B 同 project 执行"链路成立。
- **放行方式修正**：L7 落地后 **不打 Q4a bypass 补丁**——per-user 用户是真实 project_member（带角色），
  忠实 G2 表驱动 RBAC 天然正确（见 [Doc4 §5.3](INTEGRATION_DESIGN.md)）。
- **dev 实测**：同一 `POST /v1/flows`（WRITE_FLOW）Editor 201 建流 / Viewer 403；`GET /v1/flows`（READ_FLOW）两者 200
  ⇒ per-user principal 因 `project_role` 不同而 allow/deny，表驱动 RBAC 在 CE 生效。

| # | 子项 | 处置 |
|---|---|---|
| AG-03.1 | 完整权限面回归 | ✅ HTTP 侧 flow 读/写代表性实测 allow/deny；全端点同构 `securityAccess.project(permission)` |
| AG-03.2 | 补丁符合四约束 | **不适用**——未打 Q4a 补丁，改用忠实 RBAC（无 bypass 可审计） |
| AG-03.3 | HTTP + WS 双路径 | HTTP ✅；WS 在 CE 为 **membership-gated**（`validateProjectId`，flow-操作级权限属 EE）——上游忠实行为，非回归；per-user 隔离仍在 |
| AG-03.4 | 前端权限静默放行 | 移交 **L1**（`checkAccess` fallback，属 builder UI） |
| AG-03.5 | flow 编辑锁行为 | 移交 **L1**（`RESOURCE_LOCKED` 呈现，属 builder UI） |

---

## AG-04 — Builder 组件化 🟢 浏览器 E2E PASS（2026-07-24）

- **已过（框架级，ARCHITECTURE_ANALYSIS §6.5）**：精确 0.84 版本下——

| # | 子项（原 P1~P5） | 结果 |
|---|---|---|
| AG-04.1 | React 19 单实例 | ✅ PASS |
| AG-04.2 | Shadow DOM × React Flow（fitView/拖拽/边重路由/滚轮缩放/minimap） | ✅ PASS |
| AG-04.3 | Shadow DOM × Radix Portal（DropdownMenu + Dialog 双基元） | ⚠️ **PoC PASS 但结论过窄**——"零泄漏 body" 只在**显式传了 container 的调用点**成立；真实 builder 多数 Portal 调用点没传，仍逃逸到 body 而完全失样式。须全局改道（注入点 #7 `portalContainer`），见 [Doc4 §6.8](INTEGRATION_DESIGN.md) |
| AG-04.4 | Shadow DOM × Tailwind v4（preflight 隔离） | ⚠️ **隔离方向 PASS，反向失效未覆盖**——宿主零污染成立，但 shadow 内 **`:root` 变量不匹配** + **Chromium 不注册 shadow 内 `@property`**，致主题变量与 border/shadow/transform 静默失效；须 `adaptCssForShadowRoot()`，见 [Doc4 §6.8](INTEGRATION_DESIGN.md) |
| AG-04.5 | Socket.io + TanStack Query + i18n 注入链路 | ⏭ 未纳入（低风险，随实建验证） |

- **注入切点已实施（2026-07-24）**：7 个注入切点已在真实 AP 代码改造（含首要 `ApStorage` 单例，以及真实 builder 才暴露的 #7 `portalContainer`），
  经单一宿主配置面 `window.__AP_HOST_CONFIG__`（`lib/host-config.ts`，懒读、全可选、未设时回退 standalone）。
  tsc 0 新增 error + vitest 3/3。见 [Doc4 §6.2](INTEGRATION_DESIGN.md)。
- **lib-mode 构建 + 宿主包装已实施（2026-07-24）**：`src/embed/mount-builder.tsx`（`mountApBuilder`，绕 iframe SDK 用
  `isEmbedded→memoryRouter`）+ `vite.embed.config.mts` → **`vite build` ✓ 17s，6784 模块**，产物 = `ap-builder.mjs`（导出 mountApBuilder）
  + 6.8MB bundle + 1.2MB `web.css`。DW 侧 `ServiceTaskBuilderCanvas.vue`（Shadow DOM + web.css inline 注入 + `mountApBuilder`），vue-tsc 0 error。见 [Doc4 §6.4](INTEGRATION_DESIGN.md)。
- **浏览器 E2E PASS（2026-07-24）**：web-embed 产物 + host 页起于 :5173，`attachShadow` + inline `web.css` +
  `mountApBuilder`（token=L7 per-user，REST/socket 经 L2 Kong `/api/ap`）。实测 **builder 渲染 + flow 加载（REST 全 200）+
  socket.io 连接 + CSS 隔离正确 + 点 Trigger 弹 piece 选择器（Radix Portal 在 shadow 内工作）**。见 [Doc4 §6.5](INTEGRATION_DESIGN.md)。
  唯一非致命 404 = `/v1/users/:id`（G8 收窄，builder 优雅降级）。
- **已接进 DW（2026-07-24）**：`FunctionUnitEdit` 第 2 位 `Service Task` 页签 → `ServiceTaskDesigner` 读 FU 的 BPMN 取
  `serviceType=ap` 任务的 `ap:flowId` → 取桥会话 → 挂 `ServiceTaskBuilderCanvas`。DW 内实测渲染出该 FU 的 csv flow 与
  step 设置面板，调用全经 Kong `/api/ap` 且 200（含 `@scope/name` piece 元数据、`pieces/options`、`POST /flows/:id`）。
- **AG-02.8 交付方式定案 = 构建期拷贝**：`prebuild` 脚本把 web-embed 拷进 DW `public/service-task-builder/`，
  由 DW 自身 nginx 提供；无 registry、运行时不出网（合 X-3）；产物 gitignore 不入库。
  ⚠️ nginx 默认 mime.types 无 `.mjs` → 须显式 `default_type text/javascript`，否则模块被浏览器拒绝。
- **收尾（非阻塞）**：bundle 瘦身（剪 sign-in chrome）；内嵌 AP 改写宿主 `document.title`；
  CE user 模块对 `/v1/users/:id` 返影子记录消 404。
- **约束**：符合 [X-6](DECISIONS.md)——不得以"React SPA 直接并入"处理。
- **失败回退**：放弃 Shadow DOM，改 Tailwind v4 分层去 preflight + PostCSS scope + prefix。

---

## AG-05 — Sandbox + offline + prebuilt piece 联合验证 🟢 **全部子项已实测通过（2026-07-23）**

> **⚠️ 基线已变更（[D6](DECISIONS.md#d6)，2026-07-23）**：本 Gate 全程验证的是**候选**基线
> `SANDBOX_CODE_AND_PROCESS`+`STRICT`。因安全策略不允许提权（`CAP_SYS_ADMIN`），
> **现行基线降为 `SANDBOX_CODE_ONLY`+`STRICT`**。下方结论中**凡依赖 isolate 进程沙箱的部分
> （AG-05.3 网络矩阵、P-1 缓解）在现行基线下不成立**——见该节的降级注记。
> 其余结论（piece 离线加载、socket、队列、性能、AI Generate 端到端）在降级后已复测有效。

- **须证明**：安全**候选**基线 `SANDBOX_CODE_AND_PROCESS` + `NETWORK_MODE=STRICT` 与
  离线预装 piece（FR-F03A）+ 断外网 + Path B / AI Generate 生产链路**能同时成立**。
- **背景**：0.84 默认 `UNSANDBOXED` + `UNRESTRICTED`（两层不隔离、无出网限制），不可上生产。
- **环境**：dev 环境完整在线（`platform-activepieces-dev` 健康，镜像 `activepieces:0.84.0-ee-removed`——2026-07-27 起 piece 预烘焙已并入该镜像最后一层，不再有独立的 `0.84.0-pieces` 派生镜像）。

### ✅ 已实测结论

| 子项 | 结果 | 证据 |
|---|---|---|
| **AG-05.1 Piece loading** | **✅ PASS** | 忠实复现 `isolate.ts` 的挂载参数后，isolate 沙箱内 `/root/common/pieces/@activepieces` **27 个预装 piece 全部可见**、**`ready` 标记生效**、`node_modules` 就位、**`require()` 成功**（`REQUIRE_OK exports=1`），耗时 1.13s |
| **AG-05.7 Filesystem（部分）** | **✅ 挂载可行** | `cache/v11/common` → `/root/common` 绑定挂载正常；`--no-default-dirs` + 显式 dir 列表可用；`assertMountInsideRoot` 的 `/root/` 约束与我方 cache 布局兼容 |

### 🔑 决定性发现：isolate 需要 `CAP_SYS_ADMIN`

实测 capability 矩阵（同一镜像、同一 isolate 调用）：

| 容器权限 | isolate `--run` |
|---|---|
| `--privileged` | ✅ 可用 |
| **`--cap-add SYS_ADMIN`** | **✅ 可用（最小充分集）** |
| `--cap-add SYS_ADMIN --cap-add NET_ADMIN` | ✅ 可用 |
| **默认（无 cap，= 当前 dev 配置）** | **❌ `Cannot run proxy, clone failed: Operation not permitted`** |

⇒ **最小需求 = `CAP_SYS_ADMIN`（用于 namespace clone），不需要完整 privileged**；
`NET_ADMIN` 是 STRICT 模式 iptables 锁定另需的。

### ⚠️ 架构悖论（必须进 Document 4 / Document 8）

> **为了更好地隔离容器内的用户代码，必须削弱容器自身相对宿主的隔离。**

启用 isolate 沙箱要求给 AP 容器授予 `CAP_SYS_ADMIN`（+ STRICT 时的 `NET_ADMIN`）。
在 K8s 中这意味着 **AP Pod 需要提升的 securityContext**——而 **Pod Security Standards 的
`restricted` profile 明确禁止 `SYS_ADMIN`**。因此必须在两者间取舍：

| 选项 | 容器内隔离 | 容器↔宿主隔离 | K8s 可行性 |
|---|---|---|---|
| `SANDBOX_CODE_AND_PROCESS` + SYS_ADMIN | 强（isolate + isolated-vm） | **弱化** | 需 `baseline`/`privileged` PSS，须安全评审豁免 |
| `SANDBOX_CODE_ONLY`（仅 isolated-vm） | 中（code 层隔离，进程层不隔离） | **保持** | `restricted` PSS 可行 |
| `UNSANDBOXED` + 外部兜底 | 无 | 保持 | 当前状态，不可上生产 |

**这是 §4.5.3 降级阶梯之外的新维度**——原阶梯只考虑"功能能否跑通"，
未考虑"集群安全策略是否允许"。**须由平台安全评审拍板**，不是纯工程选择。

### 📌 另一实测细节：isolate 不隔离网络

`isolate.ts:96` 显式传 **`--share-net`** ⇒ 沙箱与宿主**共享网络命名空间**。
网络管控**完全依赖 iptables 的 uid owner 链**（uid 60000-60999），
而那需要 `NET_ADMIN`。⇒ **没有 NET_ADMIN 时，即使开了 isolate，出网也完全不受限**。

### ✅ 候选基线实跑结果（2026-07-23，dev 环境真实切换 `SANDBOX_CODE_AND_PROCESS` + `STRICT`）

配置：compose 加 `cap_add: [SYS_ADMIN, NET_ADMIN]` + 两个 env；容器 20s 内 healthy。
启动日志确认：`executionMode: SANDBOX_CODE_AND_PROCESS` / `Egress proxy listening on loopback:39875` /
**`Kernel-level SSRF lockdown applied`**（uid 60000-60999，1000 boxes）。

| 子项 | 结果 | 证据 |
|---|---|---|
| **AG-05.1 Piece loading** | **✅ PASS** | 真实 flow 执行日志：`Installed engine in sandbox` + `Installed pieces in sandbox pieces=["piece-webhook@0.1.36","piece-csv@0.4.15"] path=/usr/src/app/cache/v11/common` → **从预装缓存加载，零运行时下载** |
| **AG-05.2 Code execution** | **✅ PASS（但见 T3）** | CODE 步确实在 isolated-vm 内执行——错误栈帧为 `at Object.code (<isolated-vm>:31:15)` |
| **AG-05.3 Network deny** | **✅ PASS** | 见下方矩阵；**云 metadata、内网服务全阻断；公网（deepseek）放行** |
| **AG-05.5 Socket** | **✅ PASS** | `[WebSocket] Sandbox connected sandboxId=...`；iptables 显式 ACCEPT `52001:53000` |
| **AG-05.6 Redis/队列** | **✅ PASS** | worker 持续 poll、`runsMetadataQueue` 正常、`[workerRpc#completeJob] status=OK` |
| **AG-05.7 Filesystem** | **✅ PASS** | `cache/v11/common` → `/root/common` 挂载正常；`/root/` 约束与 cache 布局兼容 |
| **AG-05.8 Performance** | **✅ PASS** | 简单 flow 端到端 **≈360ms**；**AI Generate 全链路 18s**（含 LLM 往返）——远低于 300s 预算，每 job 新建沙箱开销未成瓶颈 |
| **AG-05.4 Webhook 同步响应** | **✅ PASS** | **AI Generate 端到端实跑：HTTP 200，契约完整 `{reply, document, documentType, phaseComplete, generatedData}`，documentType=REQUIREMENTS、phaseComplete=true、document 3633 字符，**总耗时 18s** |

#### AG-05.3 网络管控实测矩阵

以沙箱 uid **60000** 发起（root 作对照）：

| 目标 | 直连（iptables） | 经 egress 代理 |
|---|---|---|
| 云 metadata `169.254.169.254` | ⛔ EHOSTUNREACH | ⛔ **403** |
| 公网 `1.1.1.1` / `example.com` | ⛔ EHOSTUNREACH | ✅ **200** |
| **`api.deepseek.com`** | ⛔ EHOSTUNREACH | ✅ **200 → AI Generate 出网不受影响** |
| **平台 `admin-center` (IP 直连)** | ⛔ **EHOSTUNREACH** | ⛔ **403** |
| egress 代理 `127.0.0.1:39875` | ✅ 放行 | — |
| WS-RPC 段 `127.0.0.1:52001` | ✅ 放行（ECONNREFUSED = 无监听，非拦截） | — |
| 对照：root 出公网 / 连 admin-center | ✅ 可达（owner 规则只匹配 60000-60999） | — |

**egress 代理的真实语义 = 阻断内网 / 放行公网**（SSRF 守卫），**不是外网白名单**；
`AP_SSRF_ALLOW_LIST` 是"私网目标的例外放行"，**公网出网无需配置**。

> **🔒 对 P-1 的直接缓解**：沙箱代码**无法**访问 admin-center（直连与代理双双阻断）。
> STRICT 是那条 `X-User-Id` 冒充攻击路径的有效控制。

> **⚠️ 上表与上述 P-1 结论仅在 `SANDBOX_CODE_AND_PROCESS`（isolate 模式）下成立。**
> 经 **[D6](DECISIONS.md#d6)** 降级到 `SANDBOX_CODE_ONLY` 后实测：iptables `AP_EGRESS_LOCKDOWN`
> 链**不再安装**（`OUTPUT` policy `ACCEPT`，0 条规则），进程**全部 uid=0 无隔离**，
> **原始 socket 直连 admin-center 连通** ⇒ **P-1 缓解失效**。
> 仍保留的是**应用层** egress 代理（http piece 打内网/metadata 仍 403 `Egress blocked`）。
> 残余风险面 = **piece 代码**（进程内、可绕代理），**不是** CODE step（isolated-vm 无网络原语）。
> 补偿控制见 [D6 的 C-1~C-4](DECISIONS.md#d6)。

### 🔴 T3 — 重大能力回归：isolated-vm 内无 `fetch` / `require` / Node 内置

实测：同一个 flow 在 `UNSANDBOXED` 下**成功 7 次**（2026-07-08/09），
切换到 `SANDBOX_CODE_AND_PROCESS` 后**首次运行即失败**：

```
ReferenceError: fetch is not defined
    at Object.code (<isolated-vm>:31:15)
```

**这不是配置问题，是 isolated-vm 的设计**（`v8-isolate-code-sandbox.ts` 的 `wrapCjsModule`
故意不提供 `require`，且不注入 Node 全局）。⇒ **任何在 CODE step 里用 `fetch` / `require` /
Node 内置的既有 flow，在 `SANDBOX_CODE_ONLY` 与 `SANDBOX_CODE_AND_PROCESS` 下都会断。**

**影响评估**：
- ✅ **AI Generate（生产依赖）已端到端实证不受影响**——沙箱基线下 HTTP 200、契约完整、18s 完成。
  原因：两个 CODE 步 `fetch(`/`require(`/`process.`/`Buffer` **均 0 命中**（纯 prompt 拼装 + 正则解析）；
  且 **piece 跑在 engine 进程内、不进 isolated-vm**，`run_agent` 调 deepseek 走完整 Node + egress 代理。
- **T3 影响面已全量扫描（dev 环境）**：**含 CODE step 的 flow 共 2 个，受影响仅 1 个**——
  `csv`（测试/演示 flow，其 "Fetch File" 步用 `fetch`）；**AI Generate 干净**。
  ⇒ **当前影响面可控**，但需在 Document 4 立为 flow 编写规范。
- ⇒ **与 FR-F03B 叠加**：CODE step 既不能装外部 npm 依赖（去 bun），又不能用 Node 内置/fetch（isolated-vm）
  ⇒ **CODE step 的能力面比原先设想的窄得多**，须在 Document 4 明确并给 flow 作者替代方案
  （HTTP 调用改用 http piece，而非 CODE 内 fetch）。

### ⏳ 剩余

| 子项 | 状态 |
|---|---|
| ~~AG-05.4 / AI Generate / T3 扫描~~ | **✅ 全部完成** |
| ~~生产环境 K8s 落地 / PSS 豁免~~ | **✅ 已裁决 —— [D6](DECISIONS.md#d6) 降级，不再申请 SYS_ADMIN 豁免** |
| ~~T3 补救~~ | **✅ 完成** — dev `csv` flow 的 "Fetch File" 已由 CODE(`fetch`) 改为 **http piece**（`send_request`），下游引用改 `{{ step_4.body }}`，实跑 **SUCCEEDED** |
| **D6 补偿控制 C-3**（桥加固） | ✅ **已实现并实测（2026-07-23）** — admin-center 凭 `X-Service-Token` 才信任裸 `X-User-Id`；AP 无密钥；三态测试 500/500/201，见 [D6 C-3](DECISIONS.md#d6) |
| **D6 补偿控制 C-1**（NetworkPolicy） | 🟡 **manifest 已编写（operator-gated）** — `deploy/k8s/networkpolicy/activepieces-egress-networkpolicy.yaml`；allowlist=DNS/istiod/redis/外部PG/LLM，切断 AP→HERMES 横向；**待填环境 CIDR + 集群应用/验证**（无 k8s 环境，未运行时验证），见 [D6 C-1](DECISIONS.md#d6) |
| flow 编写规范 | 把 T3 + FR-F03B 的 CODE step 约束写入 Document 4 与开发者文档 |

### 降级阶梯（原 §4.5.3，**新增安全策略维度**）

1. `SANDBOX_CODE_AND_PROCESS` + `STRICT`（目标，**需 SYS_ADMIN + NET_ADMIN**）
2. `SANDBOX_CODE_AND_PROCESS` + `UNRESTRICTED` + NetworkPolicy 兜底（需 SYS_ADMIN）
3. **`SANDBOX_CODE_ONLY` + `STRICT`（不需 SYS_ADMIN，PSS restricted 可行）** ← ✅ **现行基线（D6，2026-07-23）**
4. `UNSANDBOXED` + `STRICT` + 强化外部兜底（最低限度）

**任何降级须在 Document 8 留档补偿控制与复评时间点** —— D6 的 C-1~C-4 即本次留档，
**Document 8 尚未创建，须在 Doc4 之后补齐**。

---

## AG-06 — Per-user provisioning 策略 🟢 实现完成 + dev 端到端 PASS（2026-07-24）

- **选型修正（实施期）**：最终取 **Option A（CE 重写 managed-authn + signing-key）**，非早期表格的 Option B。
  原因：EE 剥离（`91f01d97`）已删 ee 的 managed-authn/signing-key，而 managed-authn 本就是"外部系统按
  external-id 供给用户"的专用免密路径——CE 重写它比新造 Option B 端点更 faithful，且 `externalUserId=DW userId`
  天然满足审计到人。共享 project 用 `externalProjectId` getOrCreate（不再依赖 Q4a 的"不写 project_member"——
  managed-authn 本就 upsert project_member，角色 Editor）。
- **端到端实测（dev，重建 EE-removed 镜像后）**：
  - 引导 `POST /v1/signing-keys` → PKCS8 私钥（generator 改 pkcs8，Java `PKCS8EncodedKeySpec` 原生解析）；
  - RS256 外部 token（kid header）→ `POST /v1/managed-authn/external-token` → per-user AP token；
  - **幂等** alice 重跑同 apUserId/projectId；**隔离** bob 不同 apUserId、同共享 projectId；
  - **审计到人** `user.externalId == DW userId`（DB 核验）；共享 project `externalId=hermes-shared`、两用户同绑 Editor。
- **HERMES 侧**：`ServiceTaskTokenController#launch` 按 `service-task.managed.enabled` 分流 `signInManaged`/`signInShared`，
  admin-center BUILD SUCCESS（jjwt 0.12 RS256）。**默认 false → 回退共享账号，不回归 dev**。
- **余（非 AG-06 本体）**：AG-06.3 生命周期"停用联动"、AG-06.4 端点暴露面加固（managed-authn 现为 `public()`，
  凭外部 token 签名鉴权——须确保 signing key 私钥仅 HERMES 持有 + 内网限制）留作运维/加固项。

- **原始须证明**：选定一个**不依赖 AP SMTP 配置状态、不由平台托管用户密码**的供给方案，
  并验证幂等性、生命周期（停用/离职联动）、审计可追溯。
- **已有证据（ARCHITECTURE_ANALYSIS §2.3）**：Option B（vendored CE 内部 provisioning 端点）
  已具体化到可实现——邀请门禁在 `signUp` 编排层故下层 domain service 天然绕开；
  AP 自身 `ee/managed-authn` 与 `ee/scim` 是逐行模板；`generateRandomPassword` 生成即丢弃是官方模式；
  `getProjectAndToken` 是现成免密出 token 函数。Option A 单独不成立（不覆盖建用户）。

| # | 子项 | 通过标准 |
|---|---|---|
| AG-06.1 | 选型拍板 | Option B（+ A 作降级子路径） |
| AG-06.2 | 端点实现 | 内部 provisioning 端点，调用 AP 自身 domain service |
| AG-06.3 | 幂等 + 生命周期 | 重复调用幂等；平台用户停用 → AP 账号联动禁用 |
| AG-06.4 | 鉴权与暴露面 | 内网限制 / mTLS；端点等价"任意用户模拟"，须严格限制并审计 |

- **注**：加入共享 project 的方式已由 [Q4a](DECISIONS.md#q4a) 解决（RBAC 补丁），
  **provisioning 不需写 `project_member` 行**。
- **失败回退**：直写 DB **仅 break-glass / migration-only**，永不作为常规路径。

---

## 附：Gate 与文档的阻塞关系

```
AG-02 ─┬─→ Doc4 前端集成层 / builder 打包策略 / 前端依赖架构
AG-01 ─┤
AG-04 ─┘

AG-EE ────→ Doc4 全部 AP 服务端 Layer（经 Doc3.5）

AG-05 ────→ Doc4 Layer 3–5（engine/worker/沙箱/网络基线）

AG-03 ─┬─→ Doc4 身份与权限层
AG-06 ─┘
```

**编写纪律**：Gate 未通过的 Layer 在 Document 4 中只能写"候选方案 + 待验"，不得写成既定设计。
