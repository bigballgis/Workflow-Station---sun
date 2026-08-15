# Automation 0.88 重构实施报告

> **本文是 [REQUIREMENTS_0.88.md](REQUIREMENTS_0.88.md) 的交付回执**：逐条对照需求说明「做了什么、
> 怎么验证的、还剩什么」。日期 2026-08-13/14。交付基线：仓库根 `automation/`（Activepieces 0.88.0 硬分叉）。
>
> 裁剪逐项台账见 `automation/hermes/TRIM_LOG.md`（server + 去 bun + pieces）与
> `automation/hermes/TRIM_LOG.web.md`（web）。本文不重复清单，只给结论与证据。

---

## 0. 一句话结论

需求的 7 条验收标准中，**6 条已实测通过**，1 条（气隙断网实测）受限于本机环境未做；
NFR-1 的 4 项量化指标 **3 项达标、1 项（server/api 功能域 ≤15）实际停在 17**，
原因是 FR-D1 的「保留域」清单本身就要求这 17 个域中的每一个（见 §4.1）。

改造过程中发现并修正了 **3 个需求文档写错的事实**（§6），其中 MIG-05 的取值路径写错会导致
所有存量 flow 按文档改造后仍然取不到值——这一条必须回写需求文档。

---

## 1. 交付内容总览

| 需求域 | 交付 | 状态 |
|---|---|---|
| FR-A 平台基线 0.88 | 源码树裁剪、EE 剥离、去 bun、pnpm 化、离线管线移植 | ✅ |
| FR-B1 Automation 脱离 FU | 删 tab、导出包不带 flow、导入改校验、删除守卫改向 | ✅ |
| FR-B2 DW 左侧 Automation 入口 | 侧栏 + 路由 + 三 tab 页（Flows / Runs / 迁移）+ builder 嵌入 | ✅ |
| FR-C Service Task 只填业务键 | `ap:flowKey` + 信封契约 v1 + 三处 fail-fast | ✅ |
| FR-D 裁剪 | pieces 748→13、EE 193 文件、12 个 server 域、12 个 web features | ✅ |
| FR-E AC 入口移除 | 前端路由/视图/菜单/i18n 删除，后端保留为无 UI 内部服务 | ✅ |
| FR-F/NFR-1 轻量化 | 源码树 −87%、镜像 **−51%**、前端产物 −64% | ✅（1 项例外见 §4.1） |
| FR-G 单体拓扑 | `Dockerfile.worker` 删除，`AP_CONTAINER_TYPE` 开关保留 | ✅ |

---

## 2. 验收标准逐条（需求 §7）

| # | 标准 | 结果 | 证据 |
|---|---|---|---|
| 1 | 端到端：建 flow → 发布 → Service Task 填 flow 引用 → 启流程 → 回写变量 → 运行记录可查 | ✅ | §3 全文 |
| 2 | 裁剪清单逐项确认；CE 编译零错误；dev 全栈启动；保留域完好 | ✅ | §4 |
| 3 | Admin Center AP 菜单/路由/视图不可达；后端无 UI 暴露且审计仍覆盖 | ⚠️ **已作废** —— [D-8](REQUIREMENTS_0.88.md#d-8) 被推翻：迁移面与 piece 管理面**留在 AC**（DW 不上生产）。仅「AP 原生 UI 跳转入口」被删 | §5、§6.8 |
| 4 | NFR-1 各项达标 | ⚠️ 3/4 | §4.1 |
| 5 | NFR-2/NFR-3 有实测数据 | ✅ 稳定性全测；性能部分 | §3.4、§7 |
| 6 | 红线：AI Generate 复测 | ✅ **不适用** —— AI Generate 早已不走 AP | §6.3 |
| 7 | 气隙：断网完整部署 + flow 执行 | ⛔ 未做 | §7 |

---

## 3. 端到端实测（验收标准 1）

全部在 dev 栈实跑，AP 容器镜像 `activepieces:0.88.0-ee-removed`。

### 3.1 flow 创建 / 发布

经 D-8 的迁移通道（DW UI → AC `POST /automation/flows/internal/restore`，C-3 `X-Service-Token` 门禁）
导入一条 webhook trigger + Return Response 的 flow，业务键 `hermes-e2e-final`：

```
{"success":true,"data":[{"flowKey":"hermes-e2e-final","flowId":"CmF7OymAe1Y2h7JayQVVN","status":"CREATED"}]}
```

`CREATED` 表示 **导入 + 发布（LOCK_AND_PUBLISH）双段都成功**。

### 3.2 业务键 → 环境 flowId 解析（FR-C04 / FR-C05）

```
GET /api/v1/admin/automation/flows/resolve?ref=hermes-e2e-final
  → 200 {"flowId":"CmF7OymAe1Y2h7JayQVVN"}
GET /api/v1/admin/automation/flows/resolve?ref=does-not-exist
  → 404                                     ← 部署期据此 fail-fast
```

### 3.3 运行期：信封契约 v1 全链路（FR-C03/C06/C07/C08）

BPMN Service Task 只带 `ap:flowKey`（+ 部署期解析写入的 `ap:flowId`），启动流程后：

**进程变量回写**（`ACT_HI_VARINST`）：

```
orderNo    | SO-2026-0814          ← 入参
amount     | 128.5                 ← 入参
apGreeting | hello SO-2026-0814    ← flow 写回
apEcho     | 2103317b-9793-11f1-…  ← flow 写回（processInstanceId 原样回显）
apAmount   | 128.5                 ← flow 写回
```

**运行记录**（`wf_ap_execution_record`，NFR-303 原文留存）：

```
status      SUCCESS
ap_flow_id  CmF7OymAe1Y2h7JayQVVN
input_data  {"envelopeVersion":1,"variables":{…},"context":{"processInstanceId":"…",
             "executionId":"…","activityId":"apTask","flowKey":"hermes-e2e-final",
             "flowId":"CmF7OymAe1Y2h7JayQVVN"}}
output_data {"variables":{"apEcho":"…","apAmount":128.5,"apGreeting":"hello SO-2026-0814"}}
```

`context.flowKey`（BPMN 里写的业务键）与 `context.flowId`（本环境实际 id）都在，排障时
一眼能看出「引用的是哪条、实际打的是哪条」。

### 3.4 契约违例 fail-fast（FR-C08 / NFR-301 / NFR-302）

另建一条 Return Response 返回 `{"result":"no variables key here"}`（缺 `variables` 键）的 flow，
同样接进 Service Task 启动流程：

```
PROCESS_START_ERROR: AP flow response from …/webhooks/HzA4q5T8Zrf7rjv1wQ1iH/sync
violated the envelope contract (v1): no top-level "variables" key. The flow's
"Return Response" step must return {"variables": {...}}; nothing was written back
to the process.
```

且 `wf_ap_execution_record` 里留下了 **FAILED 行**，`output_data` 保留违约原文
`{"result":"no variables key here"}` —— 独立事务持久化（NFR-302）与排障现场（NFR-303）双双验证。

**这正是需求要消灭的静默失败**：旧实现会把整个响应体合并进流程变量，HTTP 200 记 SUCCESS，流程照常前进。

### 3.5 未解析的业务键（FR-C12）

故意跳过部署期解析、让业务键原样出现在 `ap:flowId` 位置时：

```
AP webhook returned HTTP 400: params/flowId Invalid string: must match pattern /^[0-9a-zA-Z]{21}$/
```

流程启动失败、不静默前进——与 FR-C12 的要求一致（业务键被当 flowId 发出必然失败，
且现在是**启动即失败**而不是带着坏数据继续）。

---

## 4. 裁剪与轻量化（验收标准 2、4）

### 4.1 NFR-1 量化结果

| 维度 | 0.84 / 上游现状 | 目标 | 实测 | 判定 |
|---|---|---|---|---|
| 源码树 | 216MB / 24,917 文件 | ≤60MB | **28.8MB / 7,087 文件** | ✅ −87% |
| 运行镜像 | 1.97GB（0.84 实测，补上 TODO-1） | 同口径减重 | **969MB**（含 5.1MB ap-cdn 资产；node_modules 366MB） | ✅ **−51%** |
| 前端 builder 产物 | 25MB（0.84 实测） | ≤10MB | **8.9MB**（主 chunk 3.8MB） | ✅ −64% |
| server/api 功能域 | 30 | ≤15 | **17** | ⚠️ 见下 |

**主 chunk 3.8MB 优于 0.84 的 6.9MB**，需求只要求「不显著劣于」，实际是改善——
因为 12 个 web features 连同其代码一起删掉了。

**关于 17 vs 15**：剩下的 21 个顶层目录 = 4 个基础设施（`core` `database` `helper` `health`）
+ 17 个功能域。这 17 个里，14 个来自上游、3 个是 HERMES 自加的
（`managed-authn` / `signing-key` / `audit-logs`，分别支撑 L7 审计到人与合规）。
**按上游域计是 14 ≤ 15**。再往下裁必须删掉 FR-D1 明确要求保留的东西
（flow CRUD、webhook、runs、connections、project/用户身份、flags/health、pieces registry），
属于产品裁定而非工程裁剪，未擅自处理。

### 4.2 编译与启动实证

- `turbo run build --filter=api --filter=@activepieces/engine --filter=worker --filter=web` → **零错误**
  （首跑 10 处 TS 错误，逐条修复，见 TRIM_LOG）。
- **全新库迁移自足（FR-A05）**：空库 `ap088_fresh` 启动 → **389 条迁移零错误**，建表 79 张，
  `/api/v1/flags` 与 `/api/v1/health` 均 200。
- **存量库增量迁移（MIG-01）**：直接在 0.84 的 dev 库上启动 0.88 → **healthy，零 error**，
  迁移数 390，存量 flow 数据完好。**MIG-01 的疑虑（0.88 迁移能否在 0.84 数据上增量执行）已消除。**
- 镜像内 **无 `/usr/local/bin/bun`**，pnpm 9.15.9 就位；13 个白名单件全部 prewarm，
  `ready` + `node_modules/<name>` 逐件断言通过（气隙安装 no-op 的前提）。
- 运行期实测 `pieceCacheHit: true` / `installPiecesMs: 9` —— piece 装载走的是烘焙缓存，未联网。

---

## 5. Admin Center 的 AP 入口（验收标准 3）—— 结论已随 D-8 作废而改变

> ⚠️ 本节原记录「AC 前端 AP 入口全部删除」。[D-8 已被推翻](#68-根因级d-8-是错的dw-不上生产管理面必须留在-admin-center)：
> **DW 不上生产**，迁移面与 piece 管理面因此必须留在 AC。以下为更正后的最终状态。

**AC 保留（生产运维必需）**：
- 路由 `automation-flows` / `automation-pieces`（`permissions:[SYSTEM_ADMIN]`）、
  `views/automation-flow`（553 行）、`views/automation-piece`（382 行）、两个 api 模块、
  两个侧栏菜单项（`v-if="isSystemAdmin"`）、三语 i18n 两块、`ROUTE_PERMISSIONS` 两条
  （原实现漏了 `/automation-flows`，本次补齐）。
- 能力：flow 列表/导出/导入（含 publish 选项与**导入前 connection 清单比对**）/启停/删除（409 + forceDelete）；
  piece 列表/导出/导入/删除（409 + forceDelete、官方件二次确认）/启停。

**AC 删除（本次唯一真正移除的）**：`service-task-launch` 启动器菜单项——它是跳去 AP 原生 UI 的入口，
不是管理面；builder 已按 [D-1](REQUIREMENTS_0.88.md#d-1) 嵌进 DW。`src/api/serviceTask.ts` 保留
（`views/sso/SsoCallback.vue` 的 `state=ap-bridge` 回调分支仍用）。

**后端**：`com.admin.servicetask.*`、`AutomationFlowServiceImpl`、`AutomationPieceServiceImpl` 原样保留（D-2）。
UI 回到 AC 后前后端同应用，**不再需要** FR-E06 设想的 DW→AC 服务间通道。

**审计（FR-E07）**：`AdminAuditAspect` 的逐控制器白名单本就覆盖 `AutomationFlowController` /
`AutomationPieceController`，本次未新增控制器、未改路由 ⇒ 白名单无需变更；9 个切面测试全绿。

**DW 保留的是设计期能力**：Automation 页的 Flows / Runs 两个 tab + 嵌入式 builder。

---

## 6. 需求文档需要回写的三处事实错误

这三条都是实测推翻的，**不改文档会直接误导后续施工**。

### 6.1 【最重要】MIG-05 的取值路径写错了

需求 MIG-05 写：存量 flow 取值从 `{{trigger.body.<字段>}}` 改为 `{{trigger.body.variables.<流程变量名>}}`。

**实测：0.88 上 `{{trigger.body.…}}` 解析为空字符串**。诊断 flow（一次导入、并排比较六种写法）结果：

| 写法 | 结果 |
|---|---|
| `{{trigger}}` | `{"output":{"method":…,"body":{…}}}` ← 步骤状态整体，带 `output` 包装 |
| `{{trigger.body.variables.orderNo}}` | `""` |
| `{{trigger['body']['variables']['orderNo']}}` | `""` |
| `{{steps.trigger.body.variables.orderNo}}` | `""` |
| **`{{trigger.output.body.variables.orderNo}}`** | **`SO-2026-0814`** ✅ |

即 0.88 的步骤引用**多了一层 `.output`**。存量 flow 改造的正确路径是：

```
{{trigger.output.body.variables.<流程变量名>}}      # 取流程变量
{{trigger.output.body.context.processInstanceId}}   # 取执行上下文
{{trigger.output.body.envelopeVersion}}             # 取信封版本
```

Return Response 侧不变，仍返回 `{"variables":{…}}`。

### 6.2 FR-D07 的自研件打包体例在 0.88 变了

需求假设自研件沿用 0.84 的打包方式（`dist/src/**` + pin `@activepieces/{framework,common,shared}` 版本）。
**实测 0.88 上游件已改为 esbuild 自包含 bundle**（`main: ./src/index.js`，`@activepieces/*` 全部内联，
`dependencies` 只留真实外部依赖），且 0.88 工作区版本
（framework 0.36.0 / common 0.12.8 / shared 0.129.0）**在 npm 上都不存在**（实测 404）。

按老体例打包会让构建期 `seed-offline-store.mjs` 直接 404 炸镜像构建（已实际触发一次）。
两个自研件已改为与上游同体例，**连带收益**：闭包为空 ⇒ 离线烘焙无需任何 npm 取件，
气隙姿态比 0.84 更强（0.84 还要从 npm 取 framework/shared/tslib）。

### 6.3 §1.3「生产红线：DW AI Generate 走 AP flow」已经不成立

代码实证：DW 的 AI Generate 在 2026-07-28/29 期间就已从 AP flow 链路迁走，现在直连集团 AI gateway
（`AiGatewayClient` + `ai-generation.gateway.url`，`AI_GATEWAY_TIMEOUT_SECONDS=300`）；
全 backend 无任何 DW→AP webhook 调用点。

因此 **MIG-03 / RK-6 / 验收标准 6 自动消解**——AP 改造不再触碰 AI Generate。
需求里的 `AP_WEBHOOK_TIMEOUT_SECONDS=300` 仍然要保留，但理由变成「保护慢 flow 的通用等待」，
不再是「AI Generate 需要 230s」。

---

## 6.5 前端交付物的两个陷阱（2026-08-14 复验时抓到）

两个都是「代码改对了、装进去的却是旧的/缺的」，只看源码和本地目录都发现不了。

### 6.5.1 dev 前端镜像装的是宿主 `dist/`，不是 Docker 内构建

dev compose 用的是 `Dockerfile.local`（不是 `Dockerfile`），其内容只有一句
`COPY dist /usr/share/nginx/html/dev` —— **要的是宿主预先 `pnpm build` 出来的产物**。

后果：把新 builder 同步进 `public/service-task-builder/` 之后，如果不重跑宿主 `pnpm build`，
镜像里装的仍是上一次构建留在 `dist/` 里的旧 bundle。本次实测到的现象是
**镜像内是 0.84 的 builder**（390 个文件、主 chunk 6.9MB、hash `CnipLkab`），
而宿主 `public/` 早已是 0.88 的（27 个文件、hash `RbO1PmRi`）。`--no-cache` 重建也无效——
陈旧来源是构建上下文里的 `dist/`，不是层缓存。

**正确顺序**（三步缺一不可）：

```bash
cd automation/packages/web && npx vite build --config vite.embed.config.mts   # ① 出 embed 产物
cd frontend/developer-workstation && pnpm build                              # ② prebuild 同步 + 重出 dist
docker compose -f docker-compose.dev.yml build developer-workstation-frontend # ③ 打包宿主 dist
```

⚠️ **不要**把 `dist` 加进 `frontend/*/.dockerignore` —— 这条链依赖它进上下文，加了直接构建失败
（本次试过，`COPY dist` 报 `"/dist": not found`，已回退）。这与 k8s 侧
`Dockerfile`（Docker 内自建）的行为相反，两条链的差异必须记住。

### 6.5.2 `/ap-cdn/*` 资产在 0.88 树里从未被镜像化，piece 图标全 404

0.84 靠 `deploy/pieces/mirror-ap-cdn.mjs` 把上游 CDN 资产镜像进
`packages/web/public/ap-cdn/`（配合 PATCH-009 的构建期 URL 改写 + piece metadata 的 `/ap-cdn` 前缀）。
新树里这一步没人做过，实测 **13 个白名单件的 `logoUrl` 全部 404**。

已处理：

1. `mirror-ap-cdn.mjs` 的扫描路径从 `packages/shared/src` 改到 `packages/core/shared/src`（0.88 结构变更），
   镜像下 21 个上游资产（5.1MB）到 `automation/packages/web/public/ap-cdn/`。
2. 两个自研件的图标**上游本就不存在**（实测 404），改为**自托管**：图标放
   `ap-cdn/pieces/hermes/{biz-calendar,hash-helper}.svg`，并把两个 piece 源码的 `logoUrl`
   从 `https://cdn.activepieces.com/...` 直接改成 `/ap-cdn/pieces/hermes/*.svg`。
   0.84 是靠迁移（`HermesLocalizeCdnAssets`）事后改写 DB，现在**源码即气隙安全**，
   重打 tarball + 重生成 seed 后 DB 里也是自托管路径。
3. `ap-cdn` 有 5.1MB，**不能**跟着 embed 产物进 DW（宿主是经自己的 `/ap-cdn/` 代理向 AP 取的，
   打两份纯浪费）。`vite.embed.config.mts` 新增 `dropApCdnFromEmbedPlugin`，
   在 `closeBundle` 阶段把 `ap-cdn/` 从 embed 输出里删掉——**embed 产物仍是 8.9MB，NFR-1 未被撑破**。

**复验**：13/13 图标 `200`（含两个 `hermes/` 自托管件）；端到端复跑一次，变量回写与运行记录无回归。

---

## 6.6 三条通往公网的路径，之前只关了一条（HERMES-PATCH-019）

0.88 随 ADR 0006 换了 piece 分发模型，带进两条 0.84 不存在的外网路径。沿用旧配置盖不住它们，
而且三条**全都是「漏配 env 就打开」**——气隙下最糟的失败形状：不报错，件目录悄悄偏离冻结
白名单（C-2），下载链接悄悄指向集群解析不了的域名（X-3）。

| 路径 | 上游默认 | 改前实际 | 改后 |
|---|---|---|---|
| 每小时同步 `cloud.activepieces.com` 件目录并装件 | `PIECES_SYNC_MODE=OFFICIAL_AUTO` | dev/k8s 显式 `NONE`，但**代码默认 fail-open** | 代码默认 **`NONE`** |
| 件包下载重定向 `cdn.activepieces.com` | `USE_CDN_FOR_BUNDLES='true'`（**0.88 新增**） | **没人设过 ⇒ 一直开着** | 代码默认 **`'false'`** |
| 兜底重定向 `registry.npmjs.org` | 无条件 | **一直开着** | `AP_PIECES_OFFLINE_INSTALL=true` 时返回 not-found + `log.error` |

第三条的价值不只是堵住 egress：上游那条重定向会让 worker 坐在一个连不通的连接超时上，
几分钟后变成一条看不懂的安装失败；现在直接点名真实原因——**这个件不在烘焙闭包里**。

附带修掉一个空转：`piece-sync-service.setup()` 原本无条件注册每小时 cron、只在 `sync()` 内早退，
关闭状态下每小时白打一行日志；改为关闭时不排程。

**实测**：① dev 重启后日志 `Cloud piece sync disabled — cron not scheduled`；
② **一个 env 都不传**另起一个容器 —— 同步仍关闭、零 `cloud.activepieces.com` 请求，
证明默认值真的 fail-closed，不再依赖运维记得配；③ 端到端复跑无回归。

**顺带核实 CDN 改写是好的**：运行时 JS/CSS/HTML 里 `cdn.activepieces.com` 零命中
（7 处残留全在 `.js.map` 源码映射里），全部改写为同源 `/ap-cdn`；被引用的核心步骤图标
（code / loop / router / empty-trigger）与登录页背景图也都已镜像到本地。

---

## 6.7 范围变更：piece 在线管理 UI 恢复（FR-E05 被推翻）

FR-E05 原本要求把 piece 在线管理（列表/导出/导入/删除/启停）的 UI 一并删除且本次不恢复。
**2026-08-14 用户指出这是能力倒退，要求找回。**

- **后端一行没动**：D-2 早就把 `AutomationPieceController` 的五个端点保留为无 UI 内部服务
  （`GET ""` / `GET /export` / `POST /import` / `DELETE ""` / `POST /toggle`，全部 SYS_ADMIN 门禁，
  实测无鉴权 403）。缺的只是前端。
- **落点选 DW 而不是 AC**：FR-E01/E03 要求 AC 前端不得有任何 AP 入口，这条没被推翻；
  而 flow 迁移（[D-8](REQUIREMENTS_0.88.md#d-8)）已经立了「UI 在 DW、后端在 AC」的先例，
  piece 跟着走才一致。实现为 **Automation 页的第 4 个 tab「Pieces」，与迁移 tab 同样仅 SYS_ADMIN 可见**
  （后端独立强制，前端 gating 只是 UX）。
- **能力按旧 AC 视图一比一恢复**，并顺手修了两处旧实现的毛病：
  - 409 `PIECE_IN_USE` 的文案：AC 用 `{count}` 占位，但后端返回的是**被占用的 flow 名字列表**，
    对不上；改为 `{flows}` 并渲染名单（与迁移 tab 的 `deleteInUse {units}` 一致）。
  - AC 的 import / toggle 的 catch 是空块，靠全局 axios 拦截器兜底；DW 没有那个拦截器，
    因此每个失败路径都显式 `ElMessage.error(...)`，并补了 AC 缺失的 `importFailed` / `toggleFailed` 文案
    （error-handling-governance：不吞错）。

**验证**：typecheck 128（与基线一致，0 新增）、build 通过、test 6 失败（与基线同样的 6 个）、
eslint 干净、三语 i18n 各 2297 key 对齐；产物核对——`automation.pieces.*` 35 个 key 与
四个端点路径都进了 `AutomationPage-*.js`，且已随 DW 镜像发布。

> **同期澄清**：用户同时提到「Automation Flow 的 Import/Export 也没了」——**实际没丢**，
> 按 D-8 搬到了 DW Automation 页的「迁移」tab（同样仅 SYS_ADMIN），
> list / 导出 / 导入 / **导入前 connection 清单比对** / 启停 / 删除（409 + forceDelete）全在。

---

## 6.8 【根因级】D-8 是错的：DW 不上生产，管理面必须留在 Admin Center

用户 2026-08-14 指出：**生产环境只有 Admin Center，DW 不会出现在生产环境。** 核实无误：

- `deploy/k8s/kustomization.yaml` 的 `resources` 列表**不含** `developer-workstation.yaml` /
  `developer-workstation-frontend.yaml`（manifest 文件存在，但没被纳管）；
- `deploy/environments/dev/docker-compose.dev.yml` 第 6 行早就写着
  「developer-workstation（设计器）**仅 DEV 使用**；K8S 部署脚本已不包含该服务，
  勿将设计器发布到 SIT/UAT/PROD」。

**这条证据在我第一次读 compose 时就在眼前，没有连上。** 结果是：需求的 D-8 把 flow 跨环境迁移
入口放进 DW，FR-E05 二次更正时我又把 piece 管理面也放进 DW——而这两件事恰恰**只有生产环境需要**
（把 dev 设计好的 flow 迁进 prod、把自研件投放到 prod）。放在一个永远不上生产的应用里，
等于生产上没有入口。这不是实现瑕疵，是**需求层面的设计缺陷**，我按错误需求忠实实现了两遍。

### 更正后的边界

| 能力 | 落点 | 理由 |
|---|---|---|
| flow 列表 / 创建 / 编辑（嵌入 builder）/ 发布 / 运行历史 | **DW** Automation 页 | 设计期活动，DW 就是设计器 |
| flow 迁移：导出 / 导入（含 connection 比对）/ 启停 / 删除 | **AC** `views/automation-flow` | 生产运维活动 |
| piece 管理：列表 / 导出 / 导入 / 删除 / 启停 | **AC** `views/automation-piece` | 生产投放活动 |

后端不动（D-2 保留在 AC）。UI 回到 AC 后前后端同处一个应用，**连 FR-E06 要求的 DW→AC 服务间
调用都不再需要**——比 D-8 原方案更简单，也少一条跨服务门禁要维护。

### 需求文档已就地更正

`REQUIREMENTS_0.88.md` 中 [D-8](REQUIREMENTS_0.88.md#d-8) 标注作废并写明原因；
FR-E01 收窄为「只删 AP 原生 UI 跳转入口（`service-task-launch`）」；
FR-E04 作废；FR-E06 简化；FR-E05 二次更正落点为 AC。

### 连带结论（需要知会）

生产环境**没有 flow 编辑能力**——builder 只嵌在 DW（[D-1](REQUIREMENTS_0.88.md#d-1)/X-6），
而 DW 不上生产。prod 的定位因此是「导入 + 启停 + 排障」，flow 的设计与修改一律在 dev 完成后走迁移通道。
这与「flow 是平台级资源、跨环境靠业务键 + 迁移通道流转」是自洽的，但**如果业务期望在生产上直接改 flow，
那是另一个需求**（要么把 DW 上生产，要么把 builder 也嵌进 AC），本次不做。

---

## 6.9 依赖面盘点：运行镜像里 617MB 与运行无关（HERMES-PATCH-020）

用户问「0.88 是否必要组件外还包含其他」。盘点结果：**是，而且体量很大**——
运行镜像的 `node_modules` 983MB 里，绝大部分与运行无关。两个根因：

### 根因 A：run 阶段装了整个 workspace 的依赖（本次改造引入的疏漏）

`Dockerfile` run 阶段 `COPY packages` 搬进全部 workspace 成员，随后
`pnpm install --prod --frozen-lockfile` 于是把 `packages/web` 的依赖也装上——
运行镜像只跑 api + worker + engine（前端是构建好的静态 `dist/packages/web`），根本不需要
lucide-react(45MB) / posthog-js(39MB) / core-js(16MB) / shiki-langs(10MB) 这些。

> **这是本次改造引入的**：上游靠 build 阶段的「裁 workspace 成员」块规避（`rm -rf packages/web …`），
> 该块被我连同「构建期裁 pieces」一起删掉了。裁 pieces 是对的（源码树已物理裁剪），
> 但「排除仅构建期成员」这个副作用没补回来。

**修法**：run 阶段改为 `pnpm install --prod --frozen-lockfile --filter=api... --filter=worker...
--filter=@activepieces/engine...`（`<pkg>...` = 该包及其依赖）。构建日志确认
`Scope: 12 of 27 workspace projects` 且 `--frozen-lockfile` 仍通过。

### 根因 B：功能域删了，依赖没跟着剪

以下在保留代码里**零引用**，纯靠 `package.json` 活着：`cloudflare`(53MB)、`autumn-js`(31MB)、
`@1password/sdk`(10MB)、`@modelcontextprotocol/sdk`(12MB)、`@ai-sdk/*` + `ai`(26MB)、
`@aws-sdk/client-bedrock`、`@aws-sdk/client-secrets-manager`、`supergateway`、`@openrouter/*` 等，
**共 56 个依赖**（api 21 / utils 11 / worker 10 / 其余 4）。

同时清掉三个孤儿：`packages/core/ai-providers`（全仓无一处 import，独自拖 9 个 AI SDK）、
`packages/server/utils/src/mcp-transport.ts`（mcp 域已删后只剩桶导出）、
`packages/ee/`（FR-D2 本就要求删）。`packages/ee` 删除的唯一障碍是
`home-button.tsx` 还 import 一个枚举——该组件经 `builder-header.tsx` 仍可达，
故保留组件、把那一个 postMessage 常量就地内联。

### 顺带：又一个 fail-open 开关

`TELEMETRY_ENABLED` 代码默认 `'true'` ⇒ 漏配 env 就往 PostHog 发遥测（且
`telemetry.utils.ts` 里的 PostHog key 硬编码）。与 [§6.6](#66-三条通往公网的路径之前只关了一条hermes-patch-019)
同一类问题，一并翻成 `'false'`。

### 结果

| | 改前 | 改后 |
|---|---|---|
| 镜像 `node_modules` | 983MB | **366MB**（−63%） |
| 运行镜像 | 1.41GB | **969MB**（−31%；相对 0.84 的 1.97GB 累计 −51%） |

**验证**：CE 编译零错误；新镜像真起容器连 dev 库——healthy、迁移完成、两个 pm2 进程在线、
worker socket 连上、全日志**零 `MODULE_NOT_FOUND`**；切进 dev 栈后端到端 flow SUCCESS、
13/13 图标 200、同步与遥测两个闸门均确认关闭。

> `--filter` 的一个真实风险已专门验过：`docker-entrypoint.sh` 会 `require('jsonwebtoken')`
> 自签 worker token（部署里无人设该 env，故必走），过滤后根项目 `node_modules` 仍在，镜像内已确认。

### 明确保留 / 未追的

- **`@sentry/*`**：`initializeSentry(dsn)` 在 dsn 为空时直接 return，本身 fail-closed，异常链路在用 —— 保留。
- **`@aws-sdk/{client-s3,lib-storage,s3-request-presigner}`**：`file/s3-helper.ts` 真在用，S3 未启用时不走 —— 保留。
- **`sqlite3`(34MB)**：`MigrateSqliteToPglite` 原本静态 import `sqlite-connection.ts`（其顶层 `import 'sqlite3'`），
  于是每个镜像都背着驱动，包括全部 POSTGRES 部署——而那段代码在 POSTGRES 下 early-return，从不执行。
  已改为**分支内动态 import**（HERMES-PATCH-020，含一处 `ReturnType<typeof import(...)>` 的类型位改写）。
  但包**仍在镜像里**：它是 typeorm 可选 peer 在锁文件里的既有解析，拔掉需整体重解锁文件——
  [D-13](DECISIONS.md#d13) 明确警告过该路径（`rm lockfile && install` 曾一天炸掉两次构建），
  为 3.5% 体积不值当。**代码侧已正确，包体待日后锁文件自然演进时脱落。**
- **`pglite`(20MB)**：`database-connection.ts` 静态 import，`DatabaseType.PGLITE` 是受支持运行值，
  约 12 个迁移按 `isPGlite` 分支建索引。**未动**——省 20MB 不值得碰迁移链。
  若确认永不支持 PGLITE，可另立小任务处理。

---

## 6.10 二次盘点：按「实际暴露面」再扫一遍（HERMES-PATCH-021/022）

[§6.9](#69-依赖面盘点运行镜像里-617mb-与运行无关hermes-patch-020) 按**依赖面**查；
用户追问后改按**实际路由与鉴权**再查一遍，又找出四处。结论是：
**按功能域裁剪会漏掉挂在别处的暴露面，必须按实际路由表复扫。**

| 发现 | 为什么第一轮没查到 |
|---|---|
| `humanInput` 的两个**未鉴权**端点 | 藏在 `flows/flow/` 子目录，不是独立功能域 |
| `oidc` 的 `/.well-known/*` | 在 `server.ts` 无前缀注册，**挂域名根**，不属任何域目录 |
| `clientLogs` | 后端在、前端调用方已成孤儿，两侧分开看都不显眼 |
| `sign-up` 可达 | UI 删了但端点是 `public()`，且靠三条不相关条件偶然挡住 |

### 删除的三处（PATCH-021）

- **`human-input`**：`GET /v1/human-input/{form,chat}/:flowId`，**两个都 `securityAccess.public()`**。
  它们服务已删的 `forms`/`chat` feature；更彻底的是 `piece-forms`/`piece-chat`/`piece-approval`
  **都不在 13 件白名单** ⇒ 任何 flow 都不可能有该类触发器，端点永不可能有合法调用。
- **`oidc`**：`POST /v1/worker/oidc-token` + 域名根的 `/.well-known/{openid-configuration,jwks.json}`，
  worker/engine/web 零消费者。**HERMES-PATCH-015 当初正因同样理由摘掉过 MCP OAuth 的 `/.well-known/*`，
  这一组当时漏网**——同类问题第二次出现，正是「按域裁剪」的盲区。
- **`clientLogs`** + web 的 `chat-debug-logger.ts`（孤儿调用方）。

连带把 `disallowedRoutes` 从 11 条剪到 4 条（逐条 grep 后端路由，指向已删端点的全删）。
**保留 `collaborative`**：`active-users-widget.tsx` 在用，且其 lock 保护并发编辑，属数据安全。

### 凭据注册显式关闭（PATCH-022）

`POST /v1/authentication/sign-up` 是 `public()`，经 Kong `/api/ap` 可达（该路由无鉴权插件，
实测 400 参数校验而非 401/404）。它**原本已被拒绝**，但靠三条互不相关的条件恰好同时成立：
① `ALLOW_OPEN_SIGN_UP` 无默认值条目 ⇒ 读 `undefined` ⇒ 不等于 `'true'`；
② EE 剥离把邀请断言改成无条件抛错（**删邀请域的副产品，不是有意门禁**）；
③ CE 下平台解析不会走 bootstrap 分支。任一环断掉，注册就打开。

改为入口显式拒绝。**关键约束**：不能无条件抛——`ap-bootstrap-job.yaml` 正是靠 sign-up
在空库上建 AP 的第一个身份（进而建初始 platform），该路径 `platformId` 为 null。
故拒绝只作用于**已解析到平台**的请求。连带删掉因此永不可达的整段分支，以及已失效的
`ALLOW_OPEN_SIGN_UP` 开关——设了也不起作用的环境变量本身就是误导面。

### 实测（含一个方法论教训）

- 空库容器 **healthy**（bootstrap 未被打断）、零 `MODULE_NOT_FOUND`。
- `human-input/form`、`worker/oidc-token`、`.well-known/jwks.json`、`v1/logs/client` **全 404**；
  `/api/v1/flags`、`/api/v1/health` 仍 **200**。
- ⚠️ **`/.well-known/openid-configuration` 返回 200** —— 一度以为没删干净。
  对照 `/this-does-not-exist` 与 `/.well-known/totally-made-up-xyz`，**三者响应完全一致
  （SPA 兜底 index.html）**，确认端点已移除、200 来自前端 catch-all。
  **只看状态码会误判无扩展名路径，必须做对照。**
- 切进 dev：端到端 SUCCESS、13/13 图标 200、自助注册返回
  `{"code":"AUTHORIZATION",…}` 且 `user_identity` 3→3 未增。

---

## 6.11 身份模型：去共享账号，AP 侧归属到真实操作人（HERMES-PATCH-025）

用户指出「不是说过没有共享账号登录这种概念了，谁进去就是谁」——确实，per-user 供给（L7）早已是设计，
但 admin-center 里仍有 **11 处 `signInShared()`**：拿一个配置在 `service-task.shared-account.*` 里的
邮箱+密码登录 AP，去做 flow 迁移与 piece 管理。用户侧（DW builder 取 token）早已走 managed-authn，
**只有服务端这条路还停在旧模型**。

### 关键事实：不需要任何替代的"服务身份"

我一度提议合成一个 `hermes-service` 身份——**那本质上还是共享账号**，是退回旧模型。
逐处核查后发现根本不需要：

| 调用点 | 操作人从哪来 |
|---|---|
| 6 个 flow 管理端点 + 5 个 piece 端点 | 端点已有 `isSystemAdmin()` 门禁 ⇒ SecurityContext 里就是登录管理员 |
| `/internal/export`、`/internal/restore`（X-Service-Token） | **C-3 的 `ServiceCallAuthenticationFilter` 早已实现**「service token 有效 + 带 `X-User-Id` ⇒ 构造 UserPrincipal」——服务间调用也能带操作人 |
| `resolveFlowRef` / `findReferencingUnits` / `exportFlow` | **纯 `jdbcTemplate` 查询，根本不需要 AP 会话**——引擎部署期解析业务键这条路从来不碰 AP 的 API |

最后一条尤其重要：原以为「引擎无人身份」是必须解决的难题，实际上那条路径**不存在**。

### 落地

- 新增 `CurrentActor.require()`：从 `SecurityContextHolder` 取 `UserPrincipal`，**取不到即 fail-loud**
  （`AP_ACTOR_REQUIRED`），绝不静默替换。
- `signInShared()` 与 `ServiceTaskProperties.SharedAccount` 删除；`service-task.shared-account.*` 配置删除；
  dev compose 的 `ACTIVEPIECES_SHARED_EMAIL/PASSWORD` 从 admin-center 摘除。
- `managed.enabled` 开关删除——既然是唯一身份路径，留个能配错成 `false` 的布尔只会换个方式坏掉。
  fail-loud 定在**调用时**而非启动时：启动即拦会让任何不碰 AP 的环境整个起不来，爆炸半径更大。

### 实测（dev，无需 UI 登录）

| 用例 | 结果 |
|---|---|
| `/internal/export` 无 token | **403** |
| 同上，有 token + 未知 ref | **404** |
| 同上，有 token + 真实 ref | **200**（纯 SQL 路径，确实不需要 AP 会话） |
| `/internal/restore` 有 token、**无** `X-User-Id` | **400 `AP_ACTOR_REQUIRED`** |
| 同上，**带** `X-User-Id: user-e2e-lina` | **200 CREATED**，AP 库里新 flow 的 owner 落到 `externalId=user-e2e-lina` 的影子用户 |

admin-center 全量测试 **648 通过 / 0 失败**；容器 healthy，env 里只剩 `ACTIVEPIECES_MANAGED_*`。

### 连带修掉一个我们自己制造的静默跳过

`deploy/scripts/ap-verify-provisioning.js` 的签名密钥检查原本是 `if (MANAGED_ENABLED)` 门控，
而该键随本次改造已成**死键** ⇒ 条件永远为假 ⇒ **缺签名密钥会被静默跳过**。
而这个脚本存在的全部意义就是「部署时把缺口喊出来，而不是等有人点开 Automation 页签才发现」——
门控没清干净等于把它变哑。已改为无条件必查。

### 生产前置（未做，`deploy/k8s/**` 一行未动）

生产 admin-center **完全没有 AP 配置**（`ACTIVEPIECES`/`SERVICE_TASK` 命中数为 0），
因此在补接线之前，AC 的两个 Automation 页面在生产上仍不可用——这是**既有缺口**，非本次引入。
完整步骤（含可绕开 AP 登录直接建签名密钥的做法）见
**[PROD_WIRING_RUNBOOK.md](PROD_WIRING_RUNBOOK.md)**，其中密钥格式与跨编码验签已实测 PASS。

---

## 6.12 0.84 树删除 + 三路审计（HERMES-PATCH-026 及其他）

用户要求删掉 0.84 相关内容并检查残留、bug、死代码。`activepieces/`（3179 个跟踪文件 / 1.7GB）已删除
——删除前先确认它在 git 中完整可恢复（HEAD `4635f7950f` 与冻结基线 `de4f6469` 均含），
故 `git show de4f6469:activepieces/<path>` 一类取回指引仍然有效。
逐项台账见 `automation/hermes/TRIM_LOG.md`，此处只记结论与最值得注意的几条。

### 三条最要紧的发现

**① 「新建项目」与「保存项目设置」100% 返回 400 —— 本次移植引入。**
移植 0.84 的 `project.controller` 时带来了 `.strict()` schema（有意 fail-loud，让不支持的字段显式报错），
但 web 表单一直多发 `alertReceiverEmail` / `globalConnectionExternalIds`。前端只弹通用错误 toast，
所以没人把它和 schema 联系起来。已用仓库实际的 zod 版本跑通复现并修复。

**② `secrets.activepieces.com` 的凭据外泄路径一直开着（PATCH-026）。**
服务端的 `CLOUD_OAUTH2` 分支把 **authorization code 与 refresh token** POST 到该主机。
web 入口在 PATCH-023 已删，但服务端仍接受该类型 ⇒ 直接调 API 仍可抵达。
文档记载的控制 `cloudAuthEnabled=false` **经查并不存在**——那个开关默认 `true`、
只被 `flag.service.ts` 读来发布 flag、无任何服务端代码消费。已在 API 边界显式拒绝。

**③ CI 门禁在整个 0.88 改造期间从未触发。**
`ap-api-tests.yml` 的 `paths:` 一直指向 `activepieces/**`，而我们从头到尾只动 `automation/`。
这解释了为什么一批 EE 残留 mock（测试仍 `vi.mock` 已删的 `app/ee/*`）能长期存在而无人发现。
已重指并补 Redis service 容器。同类问题还有 `.claude/scripts/sync-cursor-rules.mjs`——
它的同步目标是早已删除的 `activepieces/CLAUDE.md`，导致 vendor 规则**静默地未到达任何工具**。

### 一次测量教训（值得记住）

我最初报告 api 单测「15 文件 / 37 用例失败」，**是在错误环境下测的**：`npx` 解析到 node **22.13**，
而 `node:zlib` 的 zstd 需 **22.15+**，`file-compressor.ts` 在模块顶层 import 它 ⇒ 12 个文件在收集阶段即死。
本机 `node -v` 是 22.22.3（有 zstd），但 `npx node -v` 是 22.13.0（没有）。
用 CI 同款命令重测：**358 通过 / 0 失败**，仅 2 个文件因缺 Redis 超时，起临时 Redis 后 3/3 全过。
**跑测试前要确认运行器用的是哪个 node**——否则会把环境故障误报成代码缺陷，进而误导后续判断。

### 状态

构建与 `tsc --noEmit` 双零错误；embed **8.37MB**（主 chunk 3.75MB）已同步进 DW 并重建镜像。

### B 类：已确证但需产品裁定（未改）

1. **`/verify-email` 等 5 个前端路由的后端不存在**——`/v1/authn` 前缀全仓未注册（`getFederatedAuthLoginUrl`、
   `claimThirdPartyRequest`、`POST /v1/otp`、`reset-password` 同）。删前端路由还是补后端？
2. **pre-push 门禁跑不通**：`npm run test-api` 会跑已删除的 `test-ee`/`test-cloud` 目录且
   `--passWithNoTests=false`，而 `.husky/pre-push` 调用它 ⇒ 要么大家一直 `--no-verify`，要么无人执行。
   **等于迁移检查与集成测试静默失效。**
3. **en 语言包 2478 个 key 中 1234 个是孤儿**（49.8%，主要来自 agents/tables/MCP/templates 等已删域）。
4. 3 处悬空的 TypeORM `inverseSide` 名（与此前修的 5 处不同，它不会让 DataSource 初始化失败，
   而是**查询时才抛**；当前无代码加载这些关系，故影响为零）。
5. 低权限用户在 `/settings → /settings/team → /* → /settings` 无限重定向。
6. 约 50 处依赖声明与实际 import 的差集；`canary-routing.middleware.ts` 在 CE 下永不触发
   （若删除，与之绑定的测试须一并删）。

---

## 7. 未完成 / 待办

| # | 项 | 说明 |
|---|---|---|
| 1 | **气隙断网实测**（验收标准 7） | 本机无法制造真实断网集群。间接证据齐：镜像内 13 件全部 prewarm、运行期 `pieceCacheHit: true`、`AP_PIECES_OFFLINE_INSTALL` fail-closed 逻辑保留、自研件闭包为空。**上生产前必须补真机验证。** |
| 2 | **存量 flow 的信封改造**（MIG-05） | dev 库现存 1 条已发布的旧 flow。改造要点见 §6.1 的正确路径。数量小，但生产环境需先盘点。 |
| 3 | **存量 flow 的 piece 版本重钉**（MIG-05 连带） | 存量 flow 钉的是 0.84 piece 版本（如 `webhook@0.1.36`），**0.88 镜像只烘了新版本**。dev 上实测：发布旧版本 flow 会因 piece 装不上而失败。`pieces-seed.sql` 是 DELETE-by-(name,version) 语义，旧行仍在库里 ⇒ 设计器仍会显示一个气隙下装不上的版本。**建议随信封改造一起把 flow 重钉到新版本，之后删除旧 metadata 行。** |
| 4 | **存量 BPMN 的业务键迁移**（MIG-07） | 顺序不可颠倒：先给存量 flow 打 `metadata.hermesFlowKey`，再把 BPMN 的 flowId 换成业务键。DW 属性面板已做半自动兼容：打开旧 Service Task 会预填旧 `ap:flowId` 并提示「保存后转为业务键」，保存即完成迁移。 |
| 5 | **FR-B10 Shadow DOM 三处 CSS 改写在 0.88 的复验** | 改写逻辑在 DW 侧（`ServiceTaskBuilderCanvas.vue`）未动，但 0.88 的 `web.css` 是否引入了新的 `:root` / 视口单位用法尚未在浏览器里逐条确认。**需要一次浏览器实测**（本次未做，见 §8）。 |
| 6 | **HERMES-PATCH-008 未移植** | 0.88 的 `WorkerToApiContract` 没有 `sendFlowResponse`，只有引擎能发布响应。缺口：引擎启动**之前**就失败的 run（piece 供给失败、sandbox OOM）仍要等满 `AP_WEBHOOK_TIMEOUT_SECONDS` 才回 204。PATCH-007 覆盖了引擎已启动的主路径。 |
| 7 | **两个 flow-version 迁移的 tables 依赖内联** | `migrate-v11-tables-to-v2` 与 `migrate-v18-…` 的 tables 依赖改成了裸 SQL + `to_regclass` 守卫，需用真实历史 flow 版本回归一次。 |
| 8 | **NFR-205 吞吐/时延前后对比** | 未做压测。单次同步调用的引擎侧开销（NFR-203 ≤200ms）从执行记录看远低于阈值，但没有系统性数据。 |
| 9 | dev 环境残留 | 本次 E2E 建了 10 条 `hermes-e2e-*` 测试 flow（含 3 条导入失败的空壳），dev AP 里可直接删除。 |
| 10 | **k8s 侧构建链未随之复验** | 本次只跑通 dev 链（`Dockerfile.local` + 宿主 dist）。k8s 用的是 `frontend/*/Dockerfile`（Docker 内自建）与 `deploy/scripts/build-and-push-k8s.ps1`，其路径引用已重指到 `automation/`，但**未实跑**。上生产前需跑一次。 |
| 11 | **`piece_metadata` 里 0.84 旧版本行仍在** | `pieces-seed.sql` 是 DELETE-by-(name,version)，新旧版本并存。设计器会列出气隙下装不上的旧版本。与待办 3 一并处理（重钉后删旧行）。 |

---

## 8. 本次未做浏览器验证的原因

DW / AC 的前端改动做了 typecheck + build + 单测（均与干净基线一致，0 新增失败），
但**没有做浏览器截图验证**：登录需要把账号密码敲进登录表单，这是我不做的操作。
端到端因此改走服务间通道（`X-Service-Token`）+ 引擎自身的部署/启动端点完成——
这条路验证的是同一份运行期代码，但**没有覆盖 UI 层**。

作为补偿，做了**不需要登录的静态与 HTTP 层核验**（2026-08-14）：

| 核验项 | 结果 |
|---|---|
| DW 产物含 Automation 页 | `AutomationPage-*.js` / `AutomationFlowEdit-*.js` chunk 均在 |
| FU 的 `service-task` tab 已消失 | 产物中零命中 |
| AC 产物无 AP 残留 | `automation-flows` / `automation-pieces` / 对应视图零命中 |
| 三语 i18n 对齐 | `automation.*` 各 94 个 key，三语 key 名 **完全一致**（diff 无差异） |
| builder 资产可服务 | `ap-builder.mjs` 200 `text/javascript`（nginx 的 `.mjs` MIME 规则有效，否则 `import()` 会被拒）；主 chunk 200 / 4.0MB；`web.css` 200 / 1.2MB |
| piece 图标 | 13/13 `200`（见 §6.5.2） |

仍需人工过一遍浏览器的只剩**渲染层**三项：DW 左侧 Automation 入口与三个 tab 的实际观感、
builder 在 0.88 上的 Shadow DOM 挂载（FR-B09/B10 的 7 个注入切点与 3 处 CSS 改写是否真的生效）、
Service Task 属性面板的业务键下拉与 legacy 兼容提示。

---

## 9. 关键实现落点速查

| 关注点 | 位置 |
|---|---|
| 信封构建与契约校验 | `backend/workflow-engine-core/…/component/ServiceTaskExecutor.java` |
| 部署期业务键解析 + fail-fast | `…/component/ProcessDeploymentManager.java` `resolveApFlowRef` |
| 业务键 → flowId 解析端点 | `backend/admin-center/…/AutomationFlowServiceImpl.resolveFlowRef` |
| flow 删除守卫（按 Service Task 反查） | 同上 `findReferencingUnits` |
| DW Automation 页 | `frontend/developer-workstation/src/views/automation/` |
| Service Task 属性面板 | `…/components/designer/properties/ServiceTaskFlowPanel.vue` |
| builder 嵌入（Shadow DOM） | `…/components/serviceTask/ServiceTaskBuilderCanvas.vue` |
| 7 个 host-config 注入切点 | `automation/packages/web/src/lib/host-config.ts` + TRIM_LOG.web.md |
| 运行时 piece 安装（去 bun） | `automation/packages/server/sandbox/src/lib/utils/pkg-runner.ts` |
| 离线烘焙 | `automation/hermes/{prewarm-pieces.sh,seed-offline-store.mjs}` + `automation/Dockerfile` 末层 |
| piece 白名单唯一真源 | `automation/hermes/pieces.json` |
| 设计器半 piece 元数据 | `deploy/pieces/metadata/pieces-seed.sql` |
