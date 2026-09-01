# Automation（Activepieces 0.88.0）重构需求规格

> **Document 1' — 取代 [REQUIREMENTS.md](REQUIREMENTS.md)（0.84 版）作为需求真源。**
> 状态：**✅ 已确认，可作为设计阶段输入**（2026-08-13）。11 条决策全部拍板，无遗留开放问题：
>
> | | 决策 |
> |---|---|
> | [D-1](#d-1) | flow 编辑仍嵌入 AP builder，挂载点迁到 DW 左侧 Automation 页 |
> | [D-2](#d-2) | Admin Center 只删 UI，后端能力保留为无 UI 内部服务 |
> | [D-3](#d-3) | piece 白名单 = 现有 13 个，不扩充 |
> | [D-4](#d-4) | FU 导出包不再随带 Automation flow |
> | [D-5](#d-5) | 取消 input/output mapping，改固定信封契约 |
> | [D-6](#d-6) | 空 body 一律 fail-fast |
> | [D-7](#d-7) | Automation 入口沿用 FU 的四角色 |
> | [D-8](#d-8) | flow 跨环境迁移入口放进 DW 的 Automation 页 |
> | [D-9](#d-9) | 基线锁定 0.88.0 |
> | [D-10](#d-10) | Service Task 填环境无关业务键，不填 raw flow id |
> | [D-11](#d-11) | 轻量化按 NFR-1 建议值，无外部硬上限 |
> | [D-12](#d-12) | **暂不分体**：单镜像 `WORKER_AND_APP`，`Dockerfile.worker` 删除（分体能力留档，不关死） |
> | [D-13](#d-13) | 去 bun：运行时切点唯一，构建期只剩主 Dockerfile；新增 ADR 0006 的 npm egress 冲突 |
> 日期：2026-08-13　源码：仓库根 `automation/`（AP 0.88.0，2026-08-12 上游发布）
>
> **实施状态（2026-08-14）**：已按本文完成改造并实测，交付回执见
> **[IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md)**。本文中被实测推翻的三处事实
> （§1.3 AI Generate 红线、MIG-05 取值路径、FR-D07 打包体例）已就地标注更正。
>
> 上位约束仍看 **[DECISIONS.md](DECISIONS.md)**（ADR）。本文只回答「**要什么**」，
> 不回答「怎么建」（Document 4）与「能不能建」（[OPEN_GATES.md](OPEN_GATES.md)）。

---

## 0. 本文与既有资产的关系

本次是**方向性变更**，不是 0.84 方案的增量：

| 既有 | 处置 |
|---|---|
| `activepieces/`（0.84.0 硬分叉，含 EE 剥离 / 去 bun / 离线管线 / HERMES-PATCH） | **已于 2026-08-14 删除**（本表原写「保留至新方案验收」——验收已过，使命结束）。只存在于 git 历史：`git show de4f6469:activepieces/<path>` |
| `docs/ap-integration/REQUIREMENTS.md`（0.84 需求） | 被本文取代；其 §1 平台事实仍有效 |
| L1 Shadow-DOM builder 嵌入（`ServiceTaskBuilderCanvas.vue` + `mount-builder.tsx` + 7 注入切点） | **保留并复用**——适配 0.88，挂载点从 FU 的 Service Task tab 迁到 DW 左侧 Automation 页（[D-1](#d-1)） |
| Admin Center AP 管理面（automation-flow / automation-piece） | **前端删除，后端保留**（[D-2](#d-2)，见 FR-E） |
| DECISIONS.md 的 X 约束、D6 沙箱基线、C-1/C-3 补偿控制 | **继续有效**，本次不重开 |

---

## 1. 基线事实（2026-08-13 实测）

以下均在本机对 `automation/`（0.88.0）实际执行命令核实。任一条失效需回到本节修订。

### 1.1 0.88.0 源码结构

| 项 | 实测值 |
|---|---|
| 体积 / 文件数 | 216MB / 24,917 文件（无 `.git`/`node_modules`/`dist`） |
| `package.json` version | `0.88.0` |
| packages 顶层 | `cli` `core` `pieces` `server` `web` `ee` `tests-e2e` |
| 体积分布 | **`pieces` 124M**、`web` 24M、`server` 17M、`core` 1.6M、其余 <400K |
| server 子包 | `api` `engine` `worker` `utils` **`sandbox`（0.84 无，已独立成包）** |
| `packages/core` | **0.84 无此包**（原 `packages/shared` 的继任者，需重新确认契约来源） |
| 锁文件 | **`bun.lock`（1.6M）—— 上游仍用 bun** |
| EE 面 | `packages/server/api/src/app/ee` **193 文件**（0.84 为 166）；`packages/core/shared/src/lib/ee` 43 文件；`packages/ee/` 仅剩 LICENSE + embed-sdk |
| community pieces | **725 个**；另有 `core/` 22 个、`common` 1 个 |
| 白名单件在 0.88 的位置 | **9/11 个已从 `community/` 迁到 `core/`**（仅 `json` `postgres` 仍在 community）；11 个全部有版本跳动 |
| builder 代码量 | **27,512 行**（0.84 为 21,699，**+27%**）；新增 `step-data/` 子模块 |

### 1.2 0.88 相对 0.84 新增的功能域（全部是本次裁剪的候选）

`packages/web/src/features/`：`agents` `alerts` `automations` `billing` `chat` `forms`
`members` `piece-sets` `platform-admin` `project-releases` `secret-managers` `tables` `templates`

`packages/server/api/src/app/`：`action-run` `agents` `ai` `analytics` `event-destinations`
`knowledge-base` `mcp` `tables` `teams-bot` `template` `tool-search` `user-invitations`

### 1.3 HERMES 侧现状

| 项 | 事实 |
|---|---|
| DW 路由 | 仅 `function-units`（列表 + 编辑）、`profile`、`403`、`ai-studio`——**左侧无一级功能导航** |
| Automation 现位置 | `FunctionUnitEdit.vue` 的第 6 个 tab（`name="service-task"`，label `functionUnit.automation`） |
| Service Task 配置项 | `ap:flowId` `ap:webhookUrl` `ap:inputMapping` `ap:outputMapping` `ap:timeoutSeconds`（记录不生效）`ap:retryCount` |
| 运行链路 | 引擎 POST `/api/v1/webhooks/{flowId}/sync` → **flow 必须以 Return Response 结尾**，否则 204 → `ApFlowNoResponseException` |
| 跨环境 | BPMN 存源环境 flowId，部署期由 `ServiceTaskFlowRefResolver` 经 admin-center `/automation/flows/resolve` 按 `metadata.hermesFlowKey` 换成本环境实值 |
| Admin Center AP 资产 | 前端 `views/automation-flow`、`views/automation-piece`（路由 `automation-flows` / `automation-pieces`）；后端 `com.admin.servicetask.*`（token 桥 / nonce / API client）+ `AutomationFlowServiceImpl` + `AutomationPieceServiceImpl` |
| 生产红线 | ~~DW **AI Generate 走 AP flow**~~ —— ⚠️ **2026-08-14 代码实证已不成立**：AI Generate 早在 2026-07-28/29 就迁离 AP，改为直连集团 AI gateway（`AiGatewayClient`），全 backend 无 DW→AP webhook 调用点。**MIG-03 / RK-6 / 验收标准 6 随之消解**。`AP_WEBHOOK_TIMEOUT_SECONDS=300` 仍保留，理由改为「保护通用慢 flow」 |
| builder 交付物 | `automation/dist/…/web-embed` → `frontend/developer-workstation/public/service-task-builder/`（27MB，其中 376 个 shiki 语法块约 19MB） |

---

## 2. 需求总览

用户原话 → 需求编号的映射，便于逐条验收：

| # | 用户表述 | 需求 |
|---|---|---|
| 1 | 按新需求改造使用 **0.88 版本**的 AP | **FR-A** |
| 2 | **Automation 要从 FU 里面拿出来** | **FR-B1** |
| 3 | **DW 左侧增加 Automation 的标签页** | **FR-B2** |
| 4 | **service task 只需要输入设计好的 flow id** | **FR-C** |
| 5 | 保留 AP Automation flow **正常运行的必要功能，其余全部删掉** | **FR-D** |
| 6 | **Admin Center 的 AP 入口也删除** | **FR-E** |
| 7 | 保证 AP **足够轻量化** | **FR-F / NFR-1** |
| 8 | 保证**性能兼备稳定** | **NFR-2 / NFR-3** |

---

## 3. 功能需求

### FR-A　平台基线切换到 0.88.0

| ID | 需求 | 优先级 |
|---|---|---|
| FR-A01 | 交付基线为 `automation/`（0.88.0）；`activepieces/`（0.84.0）不再演进，**已于 2026-08-14 从工作树删除**（保留在 git 历史） | 必须 |
| FR-A02 | 全环境禁 bun（继承 X-4，[D-13](#d-13)）——0.88 的具体范围见 FR-A2 | 必须 |
| FR-A03 | EE 剥离（继承 D3/AG-EE）：删 `app/ee/` + `core/shared/src/lib/ee`，CE 编译零错误、可启动、dev 端到端处理请求 | 必须 |
| FR-A04 | 上游自带 AI 引导文件（`CLAUDE.md` `AGENTS.md` `.claude/` `.cursor/` `.agents/` `.mcp.json`）须删除——继承 VT-19：它们绕过本仓库规则真源，且外链 `craftspace.app` MCP、GitHub plugin marketplace、`npx context7`，气隙下是纯失败面 | 必须 |
| FR-A05 | 数据库迁移在**全新库**上自足（0.84 实证 354 条零错误，0.88 须重新实证） | 必须 |
| FR-A06 | 0.88 的 `packages/core` 取代 0.84 `packages/shared`：前端类型契约（原 `ap-contracts`）的派生源须相应重定位 | 必须 |

> ⚠️ **0.88 的 EE 面比 0.84 更大**（193 vs 166 文件）。FR-A03 的工作量不能沿用 0.84 的估算，须在设计阶段重新测量。

**FR-A2　去 bun 的实际范围（2026-08-13 实测）**

0.88 的 bun 依赖分三处，**运行时切点比 0.84 收敛，但构建与分发面更宽**：

| 面 | 位置 | 处置 |
|---|---|---|
| **运行时（唯一切点）** | `packages/server/sandbox/src/lib/utils/bun-runner.ts` 的 `install()` —— spawn `bun install --ignore-scripts --filter ./<piece>`。同文件的 `build()` **已经用 esbuild、不用 bun** | 换 pnpm，须逐字复刻引擎加载器期望的目录布局 |
| **构建期** | `Dockerfile` 与 **`Dockerfile.worker` 两个都装 bun**（从 GitHub 下 `bun-v1.3.1` 二进制）并跑 `bun install --frozen-lockfile` | 两个 Dockerfile 都要改；`bun.lock` / `bunfig.toml` 换成 pnpm 锁文件 |
| **最终镜像** | 两个镜像的 run 阶段都 `COPY` 了 `/usr/local/bin/bun` | 去掉；镜像内不得残留 bun 二进制 |

| ID | 需求 | 优先级 |
|---|---|---|
| FR-A07 | 运行时 piece 安装改 pnpm，布局须与引擎的 piece 加载器解析方式一致。0.84 的教训：npm hoist 会导致 `PieceNotFound`，必须复刻 isolated 布局；0.88 改用「合成 `fast-workspace` + `--filter`」形式，**须重新验证 pnpm 能产出等价布局** | 必须 |
| FR-A08 | 主 `Dockerfile` 去 bun：构建期与运行期都不得依赖 bun 二进制，镜像内不得残留 `/usr/local/bin/bun`。因 [D-12](#d-12) 不分体，`Dockerfile.worker` 直接删除（FR-G02），去 bun **只有一条战线** | 必须 |
| FR-A09 | **ADR 0006 的分发模型与气隙冲突**：0.88 把 piece 统一成「下载 tgz 链接后 `bun install`」，其 ADR 明写 *"the link is the piece's own tarball, so `bun install` still needs npm egress for transitive deps"*。X-3 要求运行时零外网 ⇒ 须保留 0.84 的离线闭包方案（预烘焙 offline store + `AP_PIECES_OFFLINE_INSTALL` 闭包外 fail-closed），并确认它能嫁接到 0.88 的链接式分发上 | 必须 |
| FR-A10 | 离线 piece 物料烘进**主镜像**（沿用 0.84 的 `Dockerfile` 末层做法，位置不变）。注意安装逻辑已迁至 `packages/server/sandbox`，脚本路径须相应更新 | 必须 |

### FR-B　Automation 从 Function Unit 中独立

<a id="fr-b1"></a>
**FR-B1　解除 Automation 与 FU 的从属关系**

| ID | 需求 | 优先级 |
|---|---|---|
| FR-B01 | `FunctionUnitEdit.vue` 中 `name="service-task"` 的 Automation tab **移除** | 必须 |
| FR-B02 | Automation flow 成为**平台级资源**，其生命周期不依附任何 FU；创建 flow 不需要先有 FU | 必须 |
| FR-B03 | 现有"FU ↔ flow 绑定"派生功能按 [D-4](#d-4) 处置，逐项见 FR-B12 ~ FR-B14 | 必须 |
| FR-B12 | **FU 导出包不再随带 Automation flow**（[D-4](#d-4)）：`ExportManifest` 及 FU 导入器中 flow 的打包/补齐逻辑移除；FU 的导出/导入/克隆/版本快照不再涉及 flow | 必须 |
| FR-B13 | 因 D-4，FU 导入不再隐式补齐缺失 flow ⇒ **目标环境必须先有 flow**。导入时若 BPMN 引用的 flow id 在本环境不存在，须**显式报错或给出明确的未就绪提示**，不得静默导入一个跑不起来的 FU | 必须 |
| FR-B14 | flow 删除守卫**保留但改向**：由"扫 DW 侧 FU 引用"改为"按 flow id 反查 Service Task 引用"，防止删掉正在被 BPMN 使用的 flow。悬空绑定徽标与恢复功能随 FU 绑定关系一并移除 | 必须 |
| FR-B04 | 存量数据不得丢失：已建 flow 与已绑定的 Service Task 在改造后仍可查、可运行 | 必须 |

<a id="fr-b2"></a>
**FR-B2　DW 左侧一级导航新增 Automation**

| ID | 需求 | 优先级 |
|---|---|---|
| FR-B05 | DW 左侧新增 **Automation** 一级入口（当前 DW 无一级功能导航，需要同时引入侧边导航容器） | 必须 |
| FR-B06 | 该入口下可完成 flow 的**列表、创建、编辑、发布/启停、删除** | 必须 |
| FR-B06a | **修订（2026-08-31）**：「查看运行历史」不再在 DW。运行记录是生产运维视角，而 DW 只在 dev 存在（不进 `deploy/k8s/kustomization.yaml`），故与 piece 目录、flow 迁移一样迁到 Admin Center 的 `/automation-runs`；DW Automation 页只留设计期能力 | 必须 |
| FR-B07 | 入口沿用 FU 的四角色（`SYS_ADMIN` / `TECH_LEAD` / `TEAM_LEAD` / `DEVELOPER`，[D-7](#d-7)），与 `FunctionUnits` 路由的 `requiredRoles` 保持一致 | 必须 |
| FR-B15 | **DW 路由守卫的 `resolveWorkspaceAccess()` 兜底不适用于 Automation**：该兜底是给"无 DW 能力角色、但所属团队拥有 FU"的成员开的只读通道，其判定依据是 team→FU 归属；flow 已是平台级资源（FR-B02）、与 FU 解耦（[D-4](#d-4)），该依据不再成立。无四角色之一者一律 403，不得因团队拥有某个 FU 就看到全平台 flow | 必须 |
| FR-B16 | flow **不做 BU/Role 级行数据可见性管控**（[D-7](#d-7)）：四角色是页面级准入，进入后可见全部 flow。若日后需要行级隔离，另立需求 | 必须 |
| FR-B08 | 编辑形态 = **继续嵌入 AP builder**（[D-1](#d-1)）：沿用 lib-mode + Shadow DOM，挂载点从 Service Task tab 迁到左侧 Automation 页 | 必须 |
| FR-B09 | L1 的 7 个 host-config 注入切点（storage / apiUrl / socketBaseUrl / socketPath / onUnauthorized / portalContainer / embedding）须在 0.88 上重建并验证——builder 增长 27% 且新增 `step-data/`，切点位置不保证仍成立 | 必须 |
| FR-B10 | Shadow DOM 的三处 CSS 改写（`:root`→`:host`、`@property` 初始值回落、`100vh`→`100cqh`）须在 0.88 的样式表上重新验证；0.88 若改用新的主题变量或布局单位，须补充对应改写 | 必须 |
| FR-B11 | builder 产物交付沿用「本地构建 → 拷进 DW `public/` → DW nginx 自服务」的气隙路径（无 registry、运行时无外网），拷贝脚本的三态行为（默认容忍 / `REQUIRED` 硬失败 / `SKIP` 并清理）保持不变 | 必须 |

### FR-C　Service Task 简化为「只填 flow id」

| ID | 需求 | 优先级 |
|---|---|---|
| FR-C01 | BPMN Service Task 的 AP 配置面板简化为**仅需输入一个 flow 引用**，其值为**环境无关业务键**（[D-10](#d-10)），非 raw flow id；可辅以下拉选择已有 flow，但不得强制 | 必须 |
| FR-C02 | Service Task 配置面板中**不再嵌入 builder**；设计 flow 一律去 FR-B 的 Automation 入口 | 必须 |
| FR-C03 | **取消 `ap:inputMapping` / `ap:outputMapping`**，改用固定信封契约（[D-5](#d-5)，结构见 FR-C3） | 必须 |
| FR-C06 | **空 body 一律 fail-fast**（[D-6](#d-6)）：实际发出的请求体为空时立即报错中断，不得让流程带着空数据继续。杜绝当前"不配映射 → 发 `{}` → HTTP 200 → 记录 SUCCESS → 流程照常前进"的静默失败 | 必须 |
| FR-C07 | 入参与出参的"未配置/不合约"行为必须**对称**。当前实现不对称（出参不配则整体合并、入参不配则发空 map），是 FR-C06 那个陷阱的根因，新方案不得重现 | 必须 |

**FR-C3　固定信封契约（[D-5](#d-5)）**

取消映射后，Service Task 与 AP flow 之间靠**约定结构**通信。以下为建议形态，设计阶段细化后冻结：

**入参**（引擎 → AP webhook body）

```json
{
  "variables": { "<流程变量名>": "<值>", "...": "..." },
  "context": {
    "processInstanceId": "...",
    "executionId": "...",
    "flowId": "..."
  }
}
```

**出参**（AP Return Response → 引擎）

```json
{ "variables": { "<写回的流程变量名>": "<值>" } }
```

| ID | 需求 | 优先级 |
|---|---|---|
| FR-C08 | 出参**必须**带 `variables` 对象；缺失该键视为不合约，按 FR-C06 报错（**不得**回退成"整体合并"——那是静默兜底） | 必须 |
| FR-C09 | `variables` 为**全量流程变量**（取消映射后无从筛选）。是否需要脱敏、字段白名单或前缀约定，设计阶段评估后确定——涉及敏感字段外发，须过 `security-guard` | 必须 |
| FR-C10 | 信封结构须**带版本标识**，便于日后演进而不破坏存量 flow | 应当 |
| FR-C04 | 跨环境可携带性由**业务键 + 部署期解析**保证（[D-10](#d-10)）：BPMN 存业务键，部署时经 admin-center `/automation/flows/resolve` 换成本环境真实 flowId。BPMN 产物因此天然环境无关 | 必须 |
| FR-C11 | 每条 flow 必须有**全局唯一的业务键**，落在 `metadata.hermesFlowKey`；创建 flow 时分配（录入或生成），且**不可随意变更**（变更即断开所有引用它的 Service Task） | 必须 |
| FR-C12 | **部署期解析失败必须 fail-fast**。当前 `ServiceTaskFlowRefResolver` 在 `resolve-url` 或 `service.internal-token` 未配置时返回 `UNAVAILABLE`，**部署按原引用继续**——在 raw flowId 模型下尚可（原引用碰巧就是 flowId），在业务键模型下是致命的：业务键会被原样当成 flowId 发到运行期，必然 404。改造后：解析不可用或解析不到，一律**部署失败** | 必须 |
| FR-C13 | BPMN 扩展属性命名须反映语义变更（`ap:flowId` → 表达业务键的新名），并定义存量 BPMN 的读取兼容规则 | 必须 |
| FR-C05 | 配置错误须**显式失败**：flow id 不存在、flow 未发布、flow 无 Return Response 结尾等情形必须在设计期或部署期报错，不得留到运行期静默通过 | 必须 |

### FR-D　AP 功能裁剪（保留运行必需，其余删除）

**FR-D1　必须保留（flow 端到端运行的最小闭包）**

| 域 | 保留理由 |
|---|---|
| flow CRUD + 版本 + 发布/启停 | FR-B06 直接依赖 |
| **webhook trigger（含 `/sync`）+ Return Response** | 引擎唯一调用入口；Return Response 是同步结果的唯一发布点 |
| engine（执行链路、表达式求值） | 运行核心 |
| worker + 队列 + sandbox（D6 基线 `SANDBOX_CODE_ONLY`+`STRICT`） | 运行核心 + 安全基线 |
| pieces 运行时装载 + piece metadata registry | 步骤能力来源 |
| connections（piece 认证） | 无连接则大量 piece 不可用 |
| runs / 运行历史 | **排障唯一现场**，不可删 |
| project + 用户身份最小面（per-user 供给、L7） | 审计到人与资源归属 |
| flags / health | 启动与探活 |

**FR-D2　要求删除（0.88 中存在但与 flow 运行无关）**

| 层 | 删除目标 |
|---|---|
| web features | `agents` `alerts` `billing` `chat` `forms` `members` `piece-sets` `platform-admin` `project-releases` `secret-managers` `tables` `templates` |
| web routes | `chat` `chat-with-ai` `mcp-authorize` `tables` `forms` `templates` `project-release` `impact` `crash-test` `sign-up` `forget-password` `change-password`（身份统一走 HERMES SSO） |
| server/api | `agents` `ai` `analytics` `event-destinations` `knowledge-base` `mcp` `tables` `teams-bot` `template` `tool-search` `user-invitations` `action-run`（除非被 flow 运行依赖） |
| packages | `ee/embed-sdk`（EE 许可，已否决）、`tests-e2e`（按需）、`cli`（按需） |
| 构建物 | **`Dockerfile.worker`**（[D-12](#d-12) 不分体，见 FR-G02） |
| pieces | 收敛到 **FR-D3 的 13 个白名单件**，其余（`community/` 725 个 + `core/` 中未列入的）不进仓库、不进镜像、不进 `piece_metadata` |
| 前端资产 | shiki 语法高亮语言集裁剪（现 376 个语言块约 19MB，builder 实际只需少数几种） |

| ID | 需求 | 优先级 |
|---|---|---|
| FR-D01 | 删除后 CE 编译零错误、可启动、dev 端到端跑通一条真实 flow | 必须 |
| FR-D02 | 删除清单须逐项留档裁定理由（沿用 `VENDOR_TRIM_CHECKLIST.md` 体例） | 必须 |
| FR-D03 | 删除**不得**破坏 AI Generate 生产链路（§1.3 红线） | 必须 |
| FR-D04 | piece 白名单最终集 = **FR-D3 的 13 个**（[D-3](#d-3) 已确认，不再扩充） | 必须 |

**FR-D3　piece 白名单（唯一真源：`automation/hermes/pieces.json`，13 个）**

上游件 11 个 —— **2026-08-13 已逐个核对存在于 0.88**，但位置与版本均有变动：

| piece | 白名单版本（0.84 期） | 0.88 位置 | 0.88 版本 | 备注 |
|---|---|---|---|---|
| `piece-webhook` | 0.1.36 | **`core/`** | 0.1.40 | |
| `piece-http` | 0.11.10 | **`core/`** | 0.11.18 | CODE step 无 `fetch`，HTTP 调用全靠它 |
| `piece-schedule` | 0.1.17 | **`core/`** | 0.1.21 | |
| `piece-csv` | 0.4.15 | **`core/`** | 0.4.20 | |
| `piece-json` | 0.1.8 | `community/` | 0.1.11 | |
| `piece-xml` | 0.1.15 | **`core/`** | 0.1.19 | |
| `piece-pdf` | 0.5.5 | **`core/`** | 0.5.9 | |
| `piece-file-helper` | 0.1.24 | **`core/`** | 0.1.28 | |
| `piece-text-helper` | 0.5.1 | **`core/`** | **0.6.4** | ⚠️ minor 跨版本，行为变更风险最高 |
| `piece-data-mapper` | 0.3.16 | **`core/`** | 0.3.20 | |
| `piece-postgres` | 0.2.6 | `community/` | **0.3.0** | ⚠️ minor 跨版本 |

自研件 2 个（走 `tarball` 字段，源在 `automation/hermes/tarballs/`）：

| piece | 版本 | 说明 |
|---|---|---|
| `piece-biz-calendar` | 1.0.0 | 业务日历，`PIECE_DEVELOPMENT_EXAMPLE.md` 的配套实例 |
| `piece-hash-helper` | 1.0.0 | 2026-07-25 文档回归验证件 |

| ID | 需求 | 优先级 |
|---|---|---|
| FR-D05 | **0.88 中 9/11 个白名单件已从 `community/` 迁到 `core/`**：白名单机制、预烘焙脚本、离线 store seed、tsconfig paths 校验等一切按路径定位的逻辑须相应适配 | 必须 |
| FR-D06 | 全部 11 个上游件在 0.88 上有版本跳动，其中 `text-helper`（0.5.1→0.6.4）与 `postgres`（0.2.6→0.3.0）跨 minor —— 须逐个回归，确认存量 flow 的步骤配置不被破坏 | 必须 |
| FR-D07 | 2 个自研件须在 0.88 的 piece framework 上重新构建并验证。⚠️ **2026-08-14 实测：打包体例已变**——0.88 上游件是 esbuild 自包含 bundle（`main: ./src/index.js`，`@activepieces/*` 内联，`dependencies` 只留真实外部依赖），且 0.88 工作区版本（framework 0.36.0 / common 0.12.8 / shared 0.129.0）在 npm 上均不存在（404）。沿用 0.84 的 tsc + pin 版本体例会让构建期离线烘焙 404 失败。详见 [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md) §6.2 | 必须 |
| FR-D08 | 白名单唯一真源仍为 `pieces.json`（新树下路径相应调整）；`AP_PIECES_OFFLINE_INSTALL=true` 时闭包外依赖 **fail-closed**，不得放宽 | 必须 |
| FR-D09 | e2e 夹具件（`pieces.e2e-fixtures.json`：`webhook@0.1.29` `subflows@0.4.11` `data-mapper@0.3.15` `delay@0.3.26`）**不进生产镜像**，仅供测试；其去留随 FR-D2 中 `tests-e2e` 的裁定一并决定 | 应当 |

### FR-E　移除 Admin Center 的 AP 入口

| ID | 需求 | 优先级 |
|---|---|---|
| FR-E01 | ~~删除前端路由 `automation-flows` / `automation-pieces` 及对应视图、菜单、i18n~~ —— ⚠️ **2026-08-14 收窄**（[D-8](#d-8) 作废）：这两个管理页**保留在 AC**，因为 DW 不上生产。本条只剩「删除 AP 原生 UI 跳转入口（`service-task-launch` 启动器）」 | 必须 |
| FR-E02 | 后端 `com.admin.servicetask.*`（token 桥 / nonce / API client）、`AutomationFlowServiceImpl`、`AutomationPieceServiceImpl` **保留**（[D-2](#d-2)）——它们承载 per-user token 供给与 flowId 跨环境解析，是 FR-B/FR-C 的依赖 | 必须 |
| FR-E03 | 保留的后端定位为**无 UI 的内部服务**：不得从 Admin Center 前端可达，审计覆盖须继续有效（审计切面是逐控制器白名单，删 UI 不影响，但改动控制器时须同步） | 必须 |
| FR-E04 | ~~flow 跨环境迁移的操作入口迁到 DW~~ —— ⚠️ **2026-08-14 作废**（[D-8](#d-8)）：迁移面（启停/删除/导出/导入/**导入前 connection 清单比对**）**留在 Admin Center**。DW 只做设计期：flow 列表/创建/编辑/发布/运行历史 | 必须 |
| FR-E06 | 迁移操作的**后端在 admin-center**（[D-2](#d-2)）。⚠️ **2026-08-14 简化**：UI 随 [D-8](#d-8) 作废回到 AC 后，前后端同处一个应用，**不再需要跨服务调用**；原本要求的 DW→AC 服务间通道对迁移面不再适用。**仍不得**把迁移逻辑复制到任何其他后端 | 必须 |
| FR-E07 | 审计不得断：AC 的审计切面是**逐控制器白名单**，迁移操作经 DW→AC 后必须仍命中被审计的控制器；若调用路径改变，须同步更新白名单。AP 资源无前后镜像，布尔参数必须写 `newValue` | 必须 |
| FR-E05 | ~~piece 在线管理（导出/import/delete/启停）的 UI 一并移除；本次不恢复~~ —— ⚠️ **2026-08-14 用户推翻**：能力必须保留。AC 前端仍不恢复（FR-E01/E03 不变），UI **留在 Admin Center**（`views/automation-piece`，仅 SYS_ADMIN）——2026-08-14 二次更正：先前曾按 [D-8](#d-8) 落到 DW，但 DW 不上生产，故与迁移面一同回到 AC，后端沿用 D-2 保留的 `AutomationPieceController` 五端点。落地见 [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md) §6.7 | 必须 |

### FR-F　轻量化

| ID | 需求 | 优先级 |
|---|---|---|
| FR-F01 | 源码树、运行镜像、前端产物三个维度都要减重，各自设定量化目标（NFR-1） | 必须 |
| FR-F02 | 气隙可交付性不得退化（继承 X-3）：无公网、无运行时拉取、piece 离线预装闭包 fail-closed | 必须 |
| FR-F03 | 裁剪须可持续：新增上游功能域不得自动回流，需有清单/CI 守卫 | 应当 |

### FR-G　部署拓扑：单体 `WORKER_AND_APP`，暂不分体（[D-12](#d-12)）

本次**不做** worker/app 分体部署，全环境（含生产 IKP）沿用单镜像单 Deployment，`AP_CONTAINER_TYPE`
取默认值 `WORKER_AND_APP`。

> **但 0.84 的"不可拆分运行体"结论仍应作废**——0.88 上游已原生支持分体，这是**能力可用、本次不用**，
> 不是做不到。事实留档见下表，以便后续需要扩容或加固时直接启用。

**0.88 分体能力实测留档（本次不启用）**

| 事实 | 值 |
|---|---|
| 运行模式开关 | `AP_CONTAINER_TYPE` = `APP` / `WORKER` / `WORKER_AND_APP`（默认） |
| worker 镜像 | 上游自带 `Dockerfile.worker`，esbuild 单文件，无 workspace `node_modules` |
| worker 的 DB / Redis | **完全不连**（worker 源码中 `typeorm`/`ioredis`/`bullmq` 零命中） |
| worker↔app 通信 | HTTP + socket.io，经 `AP_FRONTEND_URL` + `AP_WORKER_TOKEN` |
| 并发模型 | 上游 ADR 0001：一 worker = 一沙箱 = 同时一个 job，靠副本横向扩 |

| ID | 需求 | 优先级 |
|---|---|---|
| FR-G01 | 单镜像单 Deployment，`AP_CONTAINER_TYPE=WORKER_AND_APP`（默认值，无需显式配置） | 必须 |
| FR-G02 | **`Dockerfile.worker` 列入删除清单**（FR-D2）：不分体即不需要第二个镜像，删掉可同时减少去 bun 的战线与镜像维护面 | 必须 |
| FR-G03 | 执行并发受单 pod 资源约束，须给出容量口径：`AP_WORKER_CONCURRENCY` 取值、单 pod 资源上限、以及并发达上限时的表现（排队而非失败） | 必须 |
| FR-G04 | **不得关死分体这条路**：`AP_CONTAINER_TYPE` 开关及相关配置不得在裁剪中被删除或硬编码，保证后续可平滑启用分体 | 必须 |
| FR-G05 | C-1 NetworkPolicy 沿用单 pod 假设，本次无需重画 | 必须 |

---

## 4. 非功能需求

### NFR-1　轻量化量化目标（**已确认，[D-11](#d-11)**）

下列为验收门槛（2026-08-13 用户确认按建议值执行）：

| 维度 | 现状（实测） | 建议目标 |
|---|---|---|
| 源码树 | 216MB / 24,917 文件 | ≤ 60MB。主刀 = [D-3](#d-3) 把 `pieces` 从 748 个件裁到 13 个（124MB → 个位数 MB）；次刀 = `docs/` 45MB 与 FR-D2 的功能域 |
| 运行镜像 | 0.84 版单镜像（**未测**） | 仍是**单镜像**（[D-12](#d-12) 不分体）。待 [TODO-1](#todo-1) 测出基线后按同口径减重比例设定；**无外部硬上限** |
| 前端 builder 产物 | 27MB（主 chunk 6.9MB + CSS 1.2MB + shiki 19MB） | ≤ 10MB。**主 chunk 不设下降目标**——[D-1](#d-1) 保留嵌入且 0.88 builder 比 0.84 大 27%，减重全部来自裁 shiki 语言集与功能域；主 chunk 只要求"不显著劣于 0.84 的 6.9MB" |
| server/api 功能域 | 30 个 | ≤ 15 个 |

### NFR-2　性能

| ID | 需求 |
|---|---|
| NFR-201 | Automation 入口列表首屏可交互 ≤ 2s（dev 环境、100 条 flow 规模） |
| NFR-202 | builder 首次可交互（点开一条 flow → 画布可操作）≤ 5s（dev、冷缓存）；且**不劣于 0.84 现状**——须先测 0.84 基线再对比。裁 shiki 与裁功能域是达标的主要手段 |
| NFR-203 | Service Task 同步调用的**引擎侧开销**（不含 flow 自身执行）≤ 200ms |
| NFR-204 | 等待归属不变：超时由 AP 侧 `AP_WEBHOOK_TIMEOUT_SECONDS` 控制，HTTP 客户端读超时须更长——**禁止**把 `ap:timeoutSeconds` 接到客户端（会腰斩慢流程，AI Generate 需 ~230s） |
| NFR-205 | 裁剪后 flow 执行吞吐与时延不劣于 0.84 现状（须有前后对比数据） |

### NFR-3　稳定性

| ID | 需求 |
|---|---|
| NFR-301 | 失败必须可见：沿用"204 = 未达 Return Response = 失败"的判定，禁止把 2xx 当成功 |
| NFR-302 | 失败记录须独立事务持久化（不得随调用方事务回滚一起消失） |
| NFR-303 | 运行记录须留存 input/output 原文，作为排障第一现场 |
| NFR-304 | 不引入静默兜底：配置缺失、解析关闭、piece 缺失等一律显式报错（继承 `error-handling-governance` 与 `ai-guardrails`） |
| NFR-305 | 存量 flow 与存量 BPMN 绑定在升级后可运行，或提供可执行的迁移步骤 |

### NFR-4　安全（继承，不重开）

D6 沙箱基线 `SANDBOX_CODE_ONLY`+`STRICT`；C-1 NetworkPolicy；C-2 piece 冻结；C-3 服务间 `X-Service-Token` 门禁。裁剪不得削弱其中任何一项。

---

## 5. 明确不做（Out of Scope）

| 项 | 理由 |
|---|---|
| 把 builder 重写成 Vue | 实测 ≈40,000 行、11.5–13 人月，收益不足（2026-08-13 评估） |
| iframe 嵌入 | 用户已否决（X-6） |
| 对齐上游 tag / 支持后续 rebase | D12/D13：硬分叉 + 纯自维护 |
| 用官方镜像跑 UI | X-2/X-3 气隙与自建镜像要求 |
| 替换 Flowable BPMN 引擎 | AP 是自动化补充层，不替代引擎 |

---

## 6. 兼容与迁移

| ID | 需求 |
|---|---|
| MIG-01 | 存量 flow 数据从 0.84 库迁到 0.88 的路径须验证（0.88 迁移集能否在 0.84 数据上增量执行，尚未测） |
| MIG-02 | 存量 BPMN 中的 `ap:*` 扩展属性在新配置模型下的兼容或转换规则须明确 |
| MIG-03 | AI Generate flow（deepseek-v4-pro）须**按 D-5 信封契约改造**后在 0.88 上复测通过，方可切换。它是生产依赖，改造窗口须与业务约定 |
| MIG-05 | **全部存量 flow 须按 D-5 改造**：Return Response 改为返回 `{"variables":{…}}`。取值路径见下方 ⚠️ 更正。须先盘点存量 flow 数量与受影响步骤，作为工期输入 |
| MIG-06 | 改造期的兼容策略须明确：新旧信封是否并行支持一个过渡版本，还是一次性切换（一次性切换要求所有 flow 与引擎同时上线） |
| MIG-07 | **存量 BPMN 从 raw flowId 迁到业务键**（[D-10](#d-10)）：须先给存量 flow 补 `metadata.hermesFlowKey`，再把 BPMN 中的 flowId 批量换成对应业务键。顺序不可颠倒——先换 BPMN 会让未打键的 flow 解析不到，叠加 FR-C12 的 fail-fast 会直接部署失败 |
> ⚠️ **MIG-05 取值路径更正（2026-08-14 实测，见 [IMPLEMENTATION_0.88.md](IMPLEMENTATION_0.88.md) §6.1）**：
> 本表原写的 `{{trigger.body.variables.<名>}}` 是 0.84 的形状，**在 0.88 上解析为空字符串**。
> 0.88 的步骤引用多了一层 `.output`，正确路径为：
>
> ```
> {{trigger.output.body.variables.<流程变量名>}}
> {{trigger.output.body.context.processInstanceId}}
> {{trigger.output.body.envelopeVersion}}
> ```
>
> 六种写法的并排实测结果见实施报告 §6.1。Return Response 侧不变。

| MIG-04 | 回退方案：新旧并行到哪个节点、如何回退到 0.84 须在设计阶段给出。⚠️ **2026-08-14 起 `activepieces/` 已从工作树删除**，回退等于从 git 历史（`4635f7950`）恢复整棵树 |

---

## 7. 验收标准

1. **端到端**：在 DW 左侧 Automation 入口新建一条 flow → 发布 → 在某 FU 的 BPMN Service Task 中填入其 flow id → 启动流程 → flow 执行 → 输出回写进程变量 → 运行记录可查。全程无需进入 Function Unit 的 Automation tab（该 tab 已不存在）。
2. **裁剪**：FR-D2 清单逐项确认已删；CE 编译零错误；dev 全栈启动；FR-D1 保留域功能完好。
3. **Admin Center**：AP 相关菜单、路由、视图不可达；若后端保留则无 UI 暴露且审计仍覆盖。
4. **轻量化**：NFR-1 各项指标达标（数值以 OQ-8 确认值为准）。
5. **性能与稳定**：NFR-2 / NFR-3 各项有实测数据支撑，与 0.84 现状对比不劣化。
6. **红线**：AI Generate 在 0.88 上复测通过。
7. **气隙**：断网环境完成一次完整部署与 flow 执行。

---

## 8. 决议与开放问题

### 8.1 已决议（2026-08-13，用户拍板）

<a id="d-1"></a>
**D-1　flow 编辑形态 = 继续嵌入 AP builder，只换挂载位置**

沿用 lib-mode + Shadow DOM，从 FU 的 Service Task tab 迁到 DW 左侧 Automation 页。
落到 FR-B08 ~ FR-B11。

> **连带后果（须在设计阶段正视）**：L1 全套要在 0.88 上重建——builder 增长 27%、新增 `step-data/`，
> 7 个注入切点与 3 处 Shadow DOM CSS 改写都不保证仍成立。且 27MB 前端产物与 FR-F 轻量化直接冲突，
> **轻量化只能从裁 shiki 语言集（19MB）和裁功能域下手，不能指望裁 builder 本身**。

<a id="d-2"></a>
**D-2　Admin Center 只删 UI，后端能力保留**

删前端菜单/路由/视图；`com.admin.servicetask.*` 与 Automation flow/piece 服务保留为无 UI 内部服务。
落到 FR-E01 ~ FR-E05。

> **连带后果**：FR-C 的跨环境解析链路得以保留（[OQ-5](#oq-5) 因此可选"维持现状"）；
> 但 flow 跨环境迁移的**操作入口**没了，须按 FR-E04 另找落点。

<a id="d-12"></a>
**D-12　暂不分体：单镜像 `WORKER_AND_APP`，`Dockerfile.worker` 删除**

落到 FR-G01 ~ FR-G05。

> **性质是"能力可用、本次不用"**，不是做不到：0.88 上游已原生支持分体（`AP_CONTAINER_TYPE` +
> 自带 `Dockerfile.worker`），0.84 的"不可拆分运行体"结论仍应作废并留档。
>
> **本次的简化收益**：去 bun 只需处理一个 Dockerfile（FR-A08）；离线 piece 物料仍烘在主镜像、
> 位置不变（FR-A10）；C-1 NetworkPolicy 不必重画；无 worker↔app 锁步发布约束。
>
> **放弃的收益（记账，后续需要时再取）**：worker 不连 DB/Redis 带来的执行面数据层隔离
> ——这是 D6 沙箱降级后本可获得的关键纵深补偿，单体形态下**拿不到**，piece 代码仍与持有 DB
> 凭据的进程同处一个 pod。另有：爆炸半径隔离、执行面独立扩缩容。
>
> **因此 FR-G04 是硬约束**：裁剪时不得删掉或硬编码 `AP_CONTAINER_TYPE`，否则日后启用分体
> 要重新改造代码。

<a id="d-13"></a>
**D-13　去 bun 覆盖两个镜像，运行时切点唯一**

落到 FR-A02、FR-A2、FR-A07 ~ FR-A10。

> **0.88 的范围变化**：运行时 spawn bun 的地方**收敛到唯一一处**（`bun-runner.ts` 的 `install()`；
> 同文件 `build()` 已改用 esbuild），比 0.84 干净；但构建面**翻倍**——`Dockerfile` 与
> `Dockerfile.worker` 都装 bun、都跑 `bun install --frozen-lockfile`，且两个 run 阶段都 COPY 了 bun 二进制。
> **新增冲突**：上游 ADR 0006 把 piece 分发改成"下载 tgz 链接后 `bun install`"，并明写仍需 npm egress
> 取传递依赖 —— 与 X-3 气隙正面冲突，须靠离线闭包方案兜住（FR-A09）。

<a id="d-11"></a>
**D-11　轻量化目标按 NFR-1 建议值执行，无外部硬上限**

源码树 ≤60MB、前端 builder 产物 ≤10MB、server/api 功能域 ≤15 个；运行镜像目标待 [TODO-1](#todo-1) 测出基线后补。
无来自 K8s 配额或气隙介质的硬性上限。

> **连带后果**：源码树与前端两项的达成路径已明确且风险低（分别靠 [D-3](#d-3) 裁 pieces 的 124MB
> 与裁 shiki 的 19MB）；**功能域 ≤15 个是唯一需要逐项裁定的**——FR-D2 的删除清单即是它的施工依据，
> 每删一域都要回归一次端到端 flow（RK-5）。

<a id="d-9"></a>
**D-9　基线锁定 0.88.0**

接受"发布仅数日"的新鲜度，不退到 0.87.0。落到 FR-A01。

> **连带后果**：上游 0.88.x 补丁若修的是我们依赖的路径，须自行判断是否择优 backport
> （[D12](DECISIONS.md#d12)/[D13](DECISIONS.md#d13) 下不做 rebase）。新版本的已知问题面尚未被社区充分暴露，
> 早期实施中遇到的异常须先怀疑上游、再怀疑改造。

<a id="d-10"></a>
**D-10　Service Task 填环境无关业务键，不填 raw flow id**

BPMN 存业务键，部署期解析成本环境真实 flowId。落到 FR-C01、FR-C04、FR-C11 ~ FR-C13。

> **连带后果**：① 现有解析端点已支持（`AutomationFlowServiceImpl` 按 `id = ? OR metadata->>'hermesFlowKey' = ?`
> 查询），**机制可复用**；② 但 `ServiceTaskFlowRefResolver` 的 `UNAVAILABLE → 按原引用继续` 兜底
> **必须改为 fail-fast**（FR-C12）——业务键被当 flowId 发出去必然 404，且是运行期才炸；
> ③ 业务键的唯一性与不可变性成为硬约束（FR-C11）；④ 存量 BPMN 存的是 raw flowId，须迁移（MIG-07）。

<a id="d-8"></a>
**D-8　~~flow 跨环境迁移入口放进 DW 的 Automation 页~~ —— ⚠️ 2026-08-14 作废，回到 Admin Center**

> **本决策是错的，已推翻。** `deploy/k8s/kustomization.yaml` 的部署集**不含**
> developer-workstation / developer-workstation-frontend，`docker-compose.dev.yml` 第 6 行亦明写
> 「设计器仅 DEV 使用，勿发布到 SIT/UAT/PROD」——**DW 永远不上生产**。而 flow 跨环境迁移
> （以及 piece 投放，见 [FR-E05](#fr-e)）恰恰是**生产环境**才需要的操作：把它放进 DW，
> 等于生产上根本没有入口。
>
> **更正后的落点**：迁移面与 piece 管理面**留在 Admin Center**（它是唯一上生产的管理端）；
> DW 只保留**设计期**能力——flow 列表/创建/编辑（嵌入 builder）/发布/运行历史。
> FR-E01 相应收窄：AC 删除的只是「AP 原生 UI 跳转入口」（`service-task-launch` 启动器），
> **不含** automation-flow / automation-piece 两个管理页。
>
> 连带后果不变的部分：② 后端仍在 AC（[D-2](#d-2)），本就无需跨服务调用，比 D-8 原方案更简单；
> ③ 审计切面照旧命中 AC 控制器，无需改白名单；④ connection 仍不跟着 flow 迁移，目标环境须预建同名。

> **连带后果**：① 与 FR-F 轻量化有张力——DW 多出一块管理面，须控制其复杂度；
> ② **后端不搬**（[D-2](#d-2)），DW 只做 UI，经 C-3 `X-Service-Token` 调 AC，禁止在 DW 后端复制一份迁移逻辑；
> ③ 审计切面是逐控制器白名单，调用路径变化时必须同步更新，否则迁移操作**静默脱审**（FR-E07）；
> ④ connection 仍不跟着 flow 迁移，目标环境须预建同名——这条约束不因换入口而改变。

<a id="d-7"></a>
**D-7　Automation 入口沿用 FU 的四角色**

`SYS_ADMIN` / `TECH_LEAD` / `TEAM_LEAD` / `DEVELOPER`，与 `FunctionUnits` 路由一致。落到 FR-B07、FR-B15、FR-B16。

> **连带后果**：DW 路由守卫里的 `resolveWorkspaceAccess()` 只读兜底**不能**照搬——它按
> team→FU 归属放行，而 flow 已与 FU 解耦，照搬会让"团队恰好拥有某个 FU"的无角色成员看到
> **全平台 flow**（FR-B15）。另：四角色是**页面级**准入，本次不做 flow 的行级 BU/Role 隔离（FR-B16）。

<a id="d-5"></a>
**D-5　取消 input/output mapping，改用固定信封契约**

Service Task 配置面板只剩 flow id；数据靠约定结构传递。落到 FR-C03、FR-C3、FR-C08 ~ FR-C10。

> **连带后果（成本主要在存量侧）**：**所有存量 flow 都要改造**——原先按映射后的扁平字段名取值
> （如 `{{trigger.body.name}}`），改信封后要按 `{{trigger.body.variables.name}}` 取，并且
> Return Response 必须返回 `{"variables":{…}}`。**AI Generate 是生产依赖，也在其中**（MIG-03/MIG-05）。

<a id="d-6"></a>
**D-6　空 body 一律 fail-fast**

请求体为空即报错中断，不放行。落到 FR-C06、FR-C07。

> **连带后果**：纯触发型（确实不需要入参）的 flow 也会被拦。设计阶段须确认是否存在这类 flow；
> 若存在，只能通过"信封始终至少带 `context`"来自然满足——**不得**为它开静默放行的口子。

<a id="d-4"></a>
**D-4　FU 导出包不再随带 Automation flow**

flow 是平台级资源（FR-B02），不搭 FU 导出的便车。落到 FR-B12 ~ FR-B14。

> **连带后果**：flow 跨环境流转**完全依赖** FR-E04 的迁移通道——原先"FU 导入时补齐缺失 flow"
> 这条隐式路径消失，[OQ-7](#oq-7) 的落点问题因此从"锦上添花"变成**必答**。

<a id="d-3"></a>
**D-3　piece 白名单 = 现有 13 个，不扩充**

以 `automation/hermes/pieces.json` 为唯一真源（11 上游 + 2 自研），清单与 0.88 核对结果见 FR-D3。
0.88 的 725 个 community piece 与 `core/` 中未列入者一律不进。

> **连带后果**：`packages/pieces` 的 124MB 是本次减重最大的一刀；同时 9/11 个件在 0.88 中已
> **从 `community/` 迁到 `core/`**，且全部有版本跳动（`text-helper` 与 `postgres` 跨 minor）——
> 白名单机制与预烘焙链路要改路径，存量 flow 要做 piece 版本回归（FR-D05 ~ FR-D07）。

### 8.2 开放问题

**无。** 全部开放问题已闭合（D-1 ~ D-11），本文可作为设计阶段的输入。

唯一待补的不是决策而是**测量**：NFR-1 的运行镜像目标须先测 0.84 现行镜像基线，再据此设定
（见 [TODO-1](#todo-1)）。该项不阻塞设计启动。

<a id="todo-1"></a>
**TODO-1**（设计阶段第一批）：测量 `activepieces:0.84.0-ee-removed` 镜像实际大小，
填入 NFR-1 表的"运行镜像"行，目标按同口径的减重比例设定。

---

## 9. 风险

| ID | 风险 | 影响 | 缓解 |
|---|---|---|---|
| RK-1 | 0.88 EE 面比 0.84 更大（193 vs 166 文件），剥离工作量不能沿用旧估算 | 工期 | 设计阶段重新测量 CE→ee 引用面 |
| RK-2 | 0.88 仍用 bun，去 bun 改造须完全重做 | 工期 | 运行时切点唯一（`bun-runner.install()`）且 [D-12](#d-12) 后只剩一个 Dockerfile，范围可控；但 0.88 改用"合成 workspace + `--filter`"，pnpm 能否产出等价布局须先验证（FR-A07） |
| RK-11 | 单体形态下 piece 代码与持有 DB 凭据的进程同处一个 pod，D6 沙箱降级的残余风险面**未获得**分体本可提供的数据层隔离 | 安全纵深 | 现有补偿链（C-1 NetworkPolicy / C-2 piece 冻结 / C-3 桥加固 + DB 角色只授 `activepieces` schema）继续有效；FR-G04 保住后续启用分体的通路 |
| RK-14 | 单 pod 承载全部执行并发，重负载下 API 与执行相互影响，一条失控 flow 可拖垮整个 AP | 可用性 | FR-G03 明确并发口径与资源上限；压测纳入 NFR-205 的对比数据 |
| RK-12 | 上游 ADR 0006 的 piece 分发假设运行时有 npm egress，与 X-3 气隙正面冲突 | 气隙不可用 | FR-A09：保留离线闭包 + `AP_PIECES_OFFLINE_INSTALL` fail-closed，并验证其能嫁接到链接式分发；断网实测列为必过项 |
| RK-13 | 0.88 新增 `packages/server/sandbox` 独立包，piece 安装与沙箱逻辑位置与 0.84 不同 | 改造定位 | 去 bun 与离线化的落点改为该包；相关脚本路径同步更新 |
| RK-3 | `packages/shared` → `packages/core` 结构变更，类型契约派生链断裂 | 前端构建 | FR-A06 先行验证 |
| RK-4 | **[D-1](#d-1) 已选定嵌入 builder**，而 builder 增长 27%、产物 27MB —— 与 FR-F 轻量化正面冲突 | 范围冲突 | 轻量化改从 shiki 语言集（19MB）与功能域裁剪取量；NFR-1 的前端目标须据此复核 |
| RK-9 | L1 的 7 个注入切点与 3 处 Shadow DOM CSS 改写在 0.88 上可能失效（builder 结构与样式已变） | 前端可用性 | FR-B09/FR-B10 早期做一次 0.88 挂载 PoC，先于其他前端工作 |
| RK-10 | 白名单件在 0.88 全部版本跳动且 9/11 换了目录，`text-helper`（0.5.1→0.6.4）、`postgres`（0.2.6→0.3.0）跨 minor | 存量 flow 静默失效 | FR-D05/FR-D06：先改路径定位逻辑，再逐件跑存量 flow 回归；跨 minor 的两件优先 |
| RK-5 | 裁剪误删 flow 运行依赖，症状可能延迟到运行期才出现 | 稳定性 | 每批裁剪后跑端到端 flow 回归 |
| RK-6 | AI Generate 是生产依赖，改造期间不可用即事故 | 生产 | 新旧并行，切换前复测（MIG-03） |
| RK-7 | 0.88 数据迁移能否在存量 0.84 库上增量执行未验证 | 数据 | MIG-01 早期验证，必要时走导出/导入 |
| RK-8 | DW 当前无一级导航容器，FR-B05 会触及全局布局 | 前端爆炸半径 | 按 `portal-design-parity` 与截图验证规则执行 |
