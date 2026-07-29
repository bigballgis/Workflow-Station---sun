# Activepieces 0.84 集成需求规格（Requirements Specification）

> **Document 1 / 10** — 对应立项文档 `AP_integration.md` §4 与 §16 要求的首个交付物。
> **⚠️ `AP_integration.md` 是仓库外的立项文档**（本仓库从未收录，git 全历史无记录）；下文所有
> 「AP_integration.md §x」引用均指该外部文档，本仓库内**无法跳转**。其内容已被本文吸收并就本仓库具体化，
> 因此**不影响使用**；其中 §0 关于微前端的表述**与本仓库事实不符**，已在下方 §1 表格中纠正留档。
> 状态：**待冻结**。决策见 **[DECISIONS.md](DECISIONS.md)**（ADR，全局约束唯一来源）；
> 阻塞项见 **[OPEN_GATES.md](OPEN_GATES.md)**；当前状态见 **[STATUS.md](STATUS.md)**。
> 日期：2026-07-22（末次更新 07-23）　分支：`common_0710_AP_independent`（该分支已合流，现行分支见仓库）
>
> ⚠️ **本文是需求规格，其中对上游现状的描述（如「运行时用 bun install 装 piece」）指的是「改造前的 0.84.0」**；
> 需求本身（FR-F03A 等）已按 CR-01 落地为 pnpm + 构建期预装。**实施现状请看 [STATUS.md](STATUS.md)**。
>
> 集成路线：**不用 iframe、不用官方镜像跑 UI**。AP 0.84.0 源码 vendor 进本仓库、自维护、自构建镜像；
> 画布以**源码级组件**进入 Developer Workstation（下称 DW）。
> **性质（D1）**：这是 *frozen vendor + controlled fork*，非简单 vendor。

---

## 1. 背景与已验证事实（Baseline）

以下事实均在 2026-07-22 于本机/本仓库核实，是全部需求的前提。若任何一条失效，需回到本节修订。

### 1.1 平台现状

| 项 | 事实 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.2，Maven 多模块（`backend/`），**构建必须用 JDK17**（JDK25 会让 Lombok 静默失效） |
| API 边缘 | Kong（另有 dev nginx edge :8085 作为 AP 登录桥，见 1.2） |
| 前端 | Vue 3 + TypeScript + Vite + Pinia，pnpm workspace（成员：`packages/*` + 下述 4 个应用） |
| 前端应用 | `admin-center`、`developer-workstation`、`user-portal`、`login`——**4 个独立 SPA，各自构建部署** |
| 微前端 | **无**（2026-07-22 核实：qiankun 0 引用；AP_integration.md §0 声称的 qiankun 与事实不符。`gateway-mfe`/`workflow-mfe`/`delegation-mfe`/`notification-mfe` 目录是废弃实验遗留——仅 dist+node_modules、无源码、不在 workspace 内，历史提交 `8ea8f0ab "mfe && gateway not done"`） |
| 流程引擎 | 自有 Flowable BPMN 引擎（`backend/workflow-engine-core`）——**AP 是自动化补充层，不替代它** |
| 数据库/中间件 | PostgreSQL（共享实例）、Redis |
| 部署 | Docker Compose（dev）+ K8s/Istio（uat/preprod/prod，HSBC IKP），前端镜像"构建一次多环境 promote" |

### 1.2 当前分支已保留的 AP 集成资产（brownfield，本次集成必须兼容或明确替代）

| 资产 | 位置 | 状态 |
|---|---|---|
| 共享账号登录桥 + nonce 跨域 SSO | `backend/admin-center` `com.admin.servicetask`（ServiceTaskTokenController / ServiceTaskBridgeNonceStore / ServiceTaskApiClient；[D7](DECISIONS.md#d7) 改名前为 `com.admin.ap`） | 已提交，dev 验证 |
| BPMN Service Task → AP 同步 webhook（Path B） | `backend/workflow-engine-core`：`ApTaskExecutor`、`ProcessDeploymentManager#bindActivepiecesServiceTasks`（部署期注入 delegate）、`wf_ap_execution_record` | 已提交，dev 端到端实测通过 |
| DW 设计器 AP 配置面板 | `frontend/developer-workstation`：`ServiceTaskProperties.vue`、`ApTaskPropertiesPanel.vue`、`apConfigSerializer.ts`、`api/ap.ts` | 已提交 |
| AI Generate 走 AP flow | DW 后端 `AiGenerationServiceImpl` → AP 同步 webhook（flow `QnU0ytf5oBaxL9rbwOU2Z`，deepseek-v4-pro，`AP_WEBHOOK_TIMEOUT_SECONDS=300`） | 生产依赖，**不得破坏** |
| 离线 pieces 管线 | 白名单 `activepieces/hermes/pieces.json`（13 piece）+ 同目录 `prewarm-pieces.sh`（并入 `activepieces/Dockerfile` 末层，镜像 `activepieces:0.84.0-ee-removed`）；元数据半 `deploy/pieces/`（seed SQL / tarball 留档）；断外网 `AP_PIECES_SYNC_MODE=NONE`（0.84 无 `AP_PIECES_SOURCE` 变量，曾配的已删） | 已提交，dev 验证；断网实测待做 |
| Flow 发布通道 | `deploy/ap-flows/`（导出 JSON 入 git）+ `ap-export.js` / `ap-import.js` / `ap-import-to-id.js` + `deploy/ci/Jenkinsfile.ap-flows-publish` | 已提交 |
| 文档 | `deploy/ACTIVEPIECES_INTEGRATION.md`、`deploy/ACTIVEPIECES_USER_GUIDE.md` | 已提交 |

**已废弃/已否决**：iframe 嵌入 UI（用户否决）；n8n 全套（已删除）；官方 embed-sdk（EE 许可）。
之前 iframe 方向的未提交代码已全部 discard，工作树干净（仅本文档相关新增）。

### 1.3 Activepieces 0.84.0 已验证事实

**Vendor 源已确定**：`/Users/qiweige/Downloads/activepieces-0.84.0/`（用户 2026-07-22 下载的源码
快照，177MB、无 node_modules、无 .git）。已与官方 clone（`/Users/qiweige/Desktop/AP/activepieces`，
remote = github.com/activepieces/activepieces）的 tag `0.84.0`（commit `05354b37`，2026-05-29
"chore(release): v0.84.0"）做**全量内容 diff：逐字节一致，零差异**。可直接作为 vendor 拷贝源。
注意：Desktop 的 clone 当前停在 master（0.86.2），仅供对照，禁止从 master 取任何代码。

`0.84.0` tag 的真实结构（`git show 0.84.0:...` 核实）：

| 包 | 内容 | 许可 |
|---|---|---|
| `packages/web` | **React 19** + Vite SPA（画布/builder 在此，`src/{app,features,components,api}`）。0.84 已改名 `web`（不再叫 react-ui） | CE (MIT) |
| `packages/server/api` | Fastify API 服务（flows/runs/webhooks/pieces/auth/…） | CE |
| `packages/server/engine` | 流程执行引擎（step 执行、表达式解析） | CE |
| `packages/server/worker` | Worker/队列消费、piece-installer（**运行时用 bun install 装 piece**） | CE |
| `packages/server/utils` | 服务端共享工具 | CE |
| `packages/shared` | Flow schema / 类型契约（前后端共用） | CE |
| `packages/pieces` | Piece 框架 + 社区 pieces | CE |
| `packages/cli` | 脚手架 CLI | CE |
| `packages/ee` | 企业版代码，**含 `embed-sdk/`**，自带 LICENSE | **商业许可**。⚠️ 约束**不是**"目录不进构建"——实测 CE 核心有 105 处 import `app/ee/**`，排除即编译失败。按 **No EE Business Features** 原则逐条裁定（NFR-S03 / DEPENDENCY_MAP §2.2） |

构建工具链：根 `package.json` 声明 `packageManager: bun@1.3.3`，锁文件 `bun.lock`，Nx monorepo。
**公司明令禁止 bun（2026-07-22 确认）**——vendor 后必须整体迁移到 pnpm + Node，构建、CI、
镜像运行时任何环节都不得出现 bun（含二进制），详见 CR-01/CR-02 与 [DECISIONS.md#q2](DECISIONS.md#q2)。
注意：**当前在跑的官方基底镜像 `0.84.0-pieces` 内含 bun**（worker piece-installer，bun 1.3.1），
自建镜像时必须一并剥除——这也是尽快完成 vendor 的合规动因之一。

### 1.4 环境约束

- 本机磁盘可用 ~10GB（此前 98% 满，已部分腾出）。AP 源码 `install` + 构建（node_modules + Nx cache）
  预估需 6–10GB，**任何 install/build 前必须先确认磁盘余量**，必要时清 docker build cache。
- 生产环境断外网：pieces 不允许运行时从公网 npm 拉取（已有 fail-closed registry 配置与预装机制）。

---

## 2. 目标与非目标

### 2.1 目标

1. **G1 — 画布进 DW**：AP flow 画布作为源码级组件挂载进 DW 的 Function Unit 工作区
   （Service Task / 自动化设计场景），与 DW 同域、同会话语境，无 iframe、无独立 AP 门户跳转。
2. **G2 — 源码自主权**：AP 0.84.0 CE 源码 vendor 进本仓库，团队可修改画布/引擎/piece 框架、
   自研 piece、自建镜像，集成完成后**不依赖上游仓库**。
3. **G3 — 平台归属**：身份、权限、组织（BU）、网关、日志、部署全部收敛到既有平台体系，
   不出现第二套用户/权限/组织系统。
4. **G4 — 存量不回退**：1.2 节全部 brownfield 能力（Path B service task、AI Generate、离线
   pieces、Jenkins 发布通道）持续可用；替代方案上线前旧链路不下线。

### 2.2 非目标（明确不做）

- **不**替换自有 Flowable BPMN 引擎——BPMN 仍是业务主流程，AP 承担"技术自动化/集成"子流程
  （逐场景职责边界见 §2.3 Workflow Ownership Matrix）。
- **不**替换现有认证、RBAC、Kong、用户/BU 模型。
- **不**升级 AP 到 0.85/0.86（本地 checkout 的 master 仅作参考，禁止混入）。
- **不**使用 EE 专有业务功能与专有运行能力（Git Sync、SSO piece、审计日志、计费等）——对应能力由平台侧自建。
  ⚠️ 注意这**不等于**"`packages/ee/` 目录不进构建"（该表述已作废，见 NFR-S03 / AG-EE）。
- **不**在 v1 追求 AP 的多语言 i18n 深度对齐（AP UI 自带 i18n，v1 允许画布区域英文）。

### 2.3 Workflow Ownership Matrix（Flowable ↔ Activepieces 职责边界）

> 本节是两引擎共存的**裁决依据**：Document 4 的集成设计、后续所有 code review 与新需求排期
> 都以此为准。修改本矩阵须走文档评审，不允许实现期临场发挥。

**三条判定法则（优先于矩阵逐行查表）**：

1. **等"人"归 Flowable，等"系统"归 AP。** 任何需要人参与的等待（审批、填表、认领、催办）
   一律 Flowable Human Task，进 User Portal 统一待办；AP 内不得出现面向业务用户的待办。
2. **业务状态机归 Flowable。** 流程实例的启动/流转/挂起/撤回/终止、业务数据
   （`up_process_instance` 及子表）的读写归属平台；AP 只通过变量映射取输入、还输出。
3. **系统对系统的技术动作归 AP。** HTTP 集成、数据转换、AI 调用、文件处理下沉为 AP flow，
   由 Flowable service task 经同步 webhook 调用（Path B），对 BPMN 呈现为"一个步骤"。

| # | 场景 | Owner | 说明 / 交互模式 |
|---|---|---|---|
| W-01 | Human Task（审批 / 填表 / 认领 / 转办） | **Flowable** | portal 统一待办、BU/Role 分派、MI 子任务体系；唯一的人工节点载体 |
| W-02 | 业务流程编排（多环节状态机、网关、并行、子流程） | **Flowable** | BPMN 是业务流程的唯一事实来源 |
| W-03 | 流程实例生命周期（启动 / 挂起 / 撤回 / 终止 / 催办） | **Flowable** | |
| W-04 | 业务表单与业务数据落库 | **Flowable / 平台** | AP 禁止直写业务表（JSON-row 存储只有平台可写） |
| W-05 | 第三方系统集成（HTTP / SaaS / 内部服务） | **AP** | service task → sync webhook（FR-E01）；**新集成不得再手写 Java delegate**，存量 delegate 逐步下沉为 piece |
| W-06 | 数据转换 / 文件处理（CSV / JSON / XML / PDF / Excel→CSV） | **AP** | 白名单 pieces（FR-F01） |
| W-07 | AI / LLM 调用（run_agent、AI Generate） | **AP** | FR-K 组 |
| W-08 | 流程内定时（SLA、超时提醒、延时节点） | **Flowable** | BPMN timer / boundary event——凡挂在流程实例上的时间语义都在这里 |
| W-09 | 纯技术定时任务（数据同步、清理、对账拉取） | **AP** | schedule trigger（BR-12）；仅限与任何流程实例无关的任务 |
| W-10 | 接收外部 webhook 事件 | **AP** | 生产仅暴露 AP `/api/v1/webhooks`；需拉起业务流程时由 AP 调引擎 REST（FR-D04，v1 COULD） |
| W-11 | 技术调用的重试 / 超时 / 错误分支 | **AP**（flow 内部） | 对 Flowable 只呈现最终成败；流程级补偿与人工介入走 Flowable boundary event。**逐语义细分见 §2.4** |
| W-12 | 审批语义 | **Flowable（强制）** | Flowable 是业务审批/待办的唯一 Source of Truth；approval/todos piece **已按 Q9 裁决从 v1 白名单移除**，重新开放须过架构评审 |
| W-13 | 执行监控与追溯 | 各管各 + 关联 | BPMN 实例 → 平台监控；AP run → 画布 run 详情；经 FR-I04 run-id 互相关联 |

**反模式清单（设计评审与 code review 红线）**：

- ❌ 在 AP flow 里编排审批链或任何人工环节（制造第二套待办）
- ❌ 在 Flowable Java delegate 里手写第三方 HTTP 集成（应下沉为 AP piece / flow）
- ❌ AP schedule flow 直写业务表或驱动业务状态（绕过引擎）
- ❌ 同一场景同时上 Flowable timer 与 AP schedule（双头定时）
- ❌ 用 AP flow 互调 webhook 模拟业务子流程（BR-08 的 webhook 互调仅限纯技术编排）

**灰区兜底**：拿不准时连问两个问题——"这一步失败需要人来处理吗？"（是 → Flowable 错误边界）；
"这一步产出的是业务状态还是纯数据？"（业务状态 → Flowable）。两问后仍模糊的场景不许拍脑袋，
列入 [OPEN_GATES.md](OPEN_GATES.md) 对应 Gate 人工裁决。

### 2.4 Execution Responsibility Matrix（执行语义归属：谁拥有 Retry / Timeout / 取消 / 幂等）

> §2.3 定的是"哪类流程归谁"，本节定的是**同一次调用穿过四层时，每个执行语义只归一层**。
> 执行链：`Flowable(BPMN) → ApTaskExecutor(Java 桥) → AP flow(编排层) → piece step(动作层)`。

**两条铁律**：

1. **单一自动重试层**：整条链上只允许 **AP step 层**自动重试（AP 0.84 原生
   `errorHandlingOptions.retryOnFailure`）。桥、Flowable、AP run 层一律不自动重试——
   两层同时重试 = 重试风暴 × 副作用翻倍。
2. **超时嵌套原则**：内层超时必须严格小于外层，否则外层先断、内层还在跑（结果丢失+误判失败）。
   当前基线：`piece step < AP 同步 webhook（AP_WEBHOOK_TIMEOUT_SECONDS=300s）< 桥 RestTemplate
   读超时（600s）< BPMN 层无限等（同步调用期间引擎线程持有）`。任何一处调整须四层联动复核（NFR-P01 关联）。

| # | 执行语义 | Owner | 其余层的行为 | 说明 |
|---|---|---|---|---|
| E-01 | **自动重试**（瞬时故障：网络抖动 / 5xx / 限流） | **AP step 层** | 桥不重试；Flowable 不自动重试；AP run 层不自动重试 | 铁律 1；重试次数/退避在 piece step 上配置，flow 作者负责 |
| E-02 | **业务重试**（失败后整体重新发起） | **Flowable** | AP 无感知——每次业务重试是一次全新的 webhook 调用 + 新 `wf_ap_execution_record` | 必须显式 BPMN 设计：error boundary → 网关（自动重走 / 转人工）；禁止隐式循环 |
| E-03 | **人工重试** | **平台侧**（从平台执行记录 / 流程实例重发） | **禁止在 AP UI 对 Path B 的 run 点 Retry**——sync run 重跑后无等待方，输出回填不到流程变量，平台与 AP 状态漂移 | 画布 UI 的 run-retry 入口对 Path B 来源的 run 需隐藏或警示（Document 4 细化） |
| E-04 | **超时** | 各层自持，嵌套受铁律 2 约束 | — | 超时即失败：桥按 E-06 统一归类，不做"超时后再等等"的兜底 |
| E-05 | **幂等性** | **AP flow 作者**（MUST：设计为幂等，或在 flow 说明中声明不可重试并关闭 E-01） | 桥透传幂等键（`wf_ap_execution_record` 的 executionId）进 webhook payload，供 flow 去重 | 有外部副作用（发邮件/写第三方）的 flow 是重点评审对象 |
| E-06 | **错误分类与传播** | **桥（ApTaskExecutor）** | AP 只如实返回失败；Flowable 只消费桥抛出的异常/BpmnError | 唯一映射点：AP 4xx/5xx/超时/连接失败 → 落 record → 抛出的异常类型表在 Document 4 冻结 |
| E-07 | **取消 / 中止** | **Flowable**（终止流程实例） | 已在途的 AP 同步调用**不可取消**（同步语义），只能等其自然结束并丢弃结果；AP 侧不提供业务取消入口 | 长任务（AI 300s）终止实例后 AP 仍会跑完——运行手册需注明这不是 bug |
| E-08 | **执行状态记录** | 双录 + 关联 | 平台 `wf_ap_execution_record` = **业务事实源**（成败以它为准）；AP run = 技术细节源（步骤日志） | FR-I04 的 run-id 关联是排障通道，不是状态同步机制 |
| E-09 | **并发 / 限流** | **AP worker**（全局并发配置） | Flowable 侧 v1 不做针对 AP 的并发闸门 | 租户级配额留待 Q4 多租户演进一并设计 |
| E-10 | **最终失败处置（死信）** | **Flowable** | AP 不保留死信队列语义——失败结果交还桥后，AP 侧责任终止 | 终局只有两种：BPMN 错误路径自动处置，或转人工任务（W-01） |

---

## 3. 概念映射（AP ↔ 平台）

| Activepieces 概念 | 平台既有概念 | 策略 | 说明 |
|---|---|---|---|
| User / user_identity | 平台 User（SSO） | **适配（Q4 已裁决）** | **per-user 映射**：画布会话对应当前 DW 登录用户，首次进画布自动 provision AP 账号（凭据服务端托管）；`hermes-svc` 降级为纯系统服务账号 |
| Platform | （无对应，单实例） | **收敛为 1** | 单 platform（"Hermes Automation"），不暴露 platform 管理 UI |
| Project | Business Unit / Function Unit | **适配** | v1 单 project、成员=逐用户供给的 AP 账号；BU↔project 映射作为 v2 多租户演进项 |
| Role / Permission | 平台 RBAC | **替代** | AP 自身 RBAC 不对用户暴露；入口/操作权限由平台 RBAC 判定 |
| Flow / FlowVersion | 新概念，挂靠 Function Unit | **新增+关联** | 平台侧需要 FU↔flow 的注册关系（FR-J 组） |
| Connection（piece 连接） | 新概念 | **新增** | 凭据由 AP `AP_ENCRYPTION_KEY` 加密存储；管理入口收进平台 |
| Secret / 加密 | 平台密钥外置规范 | **并存** | AP_ENCRYPTION_KEY 走既有 secret 管理（32-hex，已踩坑） |
| Webhook | 引擎 ApTaskExecutor 调用面 | **复用** | `/api/v1/webhooks/<flowId>/sync` 是 Path B 契约，不得破坏 |
| Piece | 平台"连接器"资产 | **新增** | 白名单 + 离线预装 + 自研 piece SDK |
| API Gateway（AP 自带无） | Kong | **复用** | AP server-api 收进统一网关域（Q6） |

---

## 4. 业务需求（BR，MoSCoW）

对 AP_integration.md（仓库外立项文档，见文首说明）§4.1 列出的全部能力逐项分级。
分级依据：DW 场景实际需要 + brownfield 已依赖。

### 4.1 设计态（Authoring）

| ID | 能力 | 级别 | 理由 |
|---|---|---|---|
| BR-01 | 可视化 flow 编排（画布） | **MUST** | 集成核心目标 G1 |
| BR-02 | Flow 创建 / 编辑 | **MUST** | 同上 |
| BR-03 | Flow 版本管理（draft/locked version） | **MUST** | AP 原生具备；发布通道依赖 |
| BR-04 | Flow 发布（publish/enable） | **MUST** | Path B 与 AI Generate 均要求已发布 flow |
| BR-05 | 分支 / 条件（Router/Branch） | **MUST** | 0.84 原生 Router |
| BR-06 | 循环（Loop on items） | **MUST** | 0.84 原生 |
| BR-07 | 变量 / 表达式 / 动态属性 | **MUST** | piece 配置的基础 |
| BR-08 | Subflow（flow 调 flow） | **COULD** | 0.84 CE 无原生 subflow；可用 webhook 互调模拟 |
| BR-09 | 画布内单步测试 / Test flow | **MUST** | 调试体验核心；AI flow 调参依赖 |

### 4.2 运行态（Execution）

| ID | 能力 | 级别 | 理由 |
|---|---|---|---|
| BR-10 | 手动执行（test run） | **MUST** | |
| BR-11 | Webhook 执行（含 `/sync` 同步返回） | **MUST** | Path B + AI Generate 的生命线 |
| BR-12 | 定时执行（Schedule trigger） | **SHOULD** | schedule piece 已在白名单；**仅限与流程实例无关的纯技术任务**（§2.3 W-08/W-09，流程内时间语义归 Flowable timer） |
| BR-13 | Polling trigger | **COULD** | 断外网环境可轮询的目标有限 |
| BR-14 | Action 执行 / 重试（retry） | MUST / **SHOULD** | 执行 MUST；自动重试 SHOULD 且**只在 AP step 层**（§2.4 E-01 单一自动重试层铁律） |
| BR-15 | 超时控制 | **MUST** | 已踩坑（30s→300s）；四层超时须满足 §2.4 铁律 2 的嵌套次序，任一层调整须联动复核 |
| BR-16 | 错误处理（失败分支/continue on failure） | **MUST** | |
| BR-17 | Pause / Resume（delay 等技术性等待） | **SHOULD** | **业务审批禁走 AP**（§2.3 W-12）；本条仅覆盖纯技术等待（delay/轮询窗口）；approval/todos piece 已按 Q9 移出白名单 |
| BR-18 | 执行历史 / Run 详情 / 步骤级日志 | **MUST** | 排障必需（`wf_ap_execution_record` 只记录引擎侧调用） |
| BR-19 | 并行执行 / 并发控制 | **SHOULD** | worker 并发配置即可，v1 不做租户级配额 |

### 4.3 Piece / 扩展体系

| ID | 能力 | 级别 | 理由 |
|---|---|---|---|
| BR-20 | Piece 目录（离线白名单） | **MUST** | 断外网是硬约束，延续 `deploy/pieces/` 机制 |
| BR-21 | **自研 custom piece**（开发→构建→投放） | **MUST** | 用户 vendor 源码的核心动机之一 |
| BR-22 | Piece 内 Code 执行（JS code step） | **MUST（能力收窄）** | AI flow 已用 Code step。v1 **仅支持内置/预打包依赖，禁外部 npm 依赖**（FR-F03B）——设计期校验拒绝并给替代指引 |
| BR-23 | 代码执行沙箱隔离 | **MUST** | 安全底线，见 NFR-S 组 |
| BR-24 | Connections（piece 鉴权连接管理） | **MUST** | http/piece 鉴权基础；跨环境需预建同名 connection |
| BR-25 | MCP 集成 | **COULD** | 0.84 有 MCP 能力，但断外网+安全评审未过，v1 不开 |
| BR-26 | AI 能力（run_agent + custom provider） | **MUST** | AI Generate 生产依赖（含 maxOutputTokens patch） |

### 4.4 平台整合

| ID | 能力 | 级别 | 理由 |
|---|---|---|---|
| BR-27 | 画布嵌入 DW（源码级，非 iframe） | **MUST** | G1；用户明确否决 iframe |
| BR-28 | 单点会话（进画布不二次登录） | **MUST** | 复用/演进现有 token 桥（同域后可简化） |
| BR-29 | 平台 RBAC 控制画布入口与操作 | **MUST** | G3 |
| BR-30 | flow ↔ Function Unit 归属关系 | **MUST** | 治理 flowId 漂移、支撑 FU 导入导出/版本化（见 FR-J） |
| BR-31 | 环境晋升（dev→uat→prod flow 发布） | **MUST** | 延续 git + Jenkins 通道 |
| BR-32 | 审计（谁改了哪个 flow、谁发布） | **SHOULD** | AP CE 无审计日志（EE 功能），平台侧补；Q4 per-user 落地后 AP 侧操作天然带个人归属，审计按人追溯 |

---

## 5. 功能需求矩阵（FR）

`Source in AP` 均为 `0.84.0` tag 内已核实的目录。Target System 不留空。

### A 组 — 画布源码级集成（DW）

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-A01 | AP 源码 vendor 进仓库（tag 0.84.0 全量） | MUST | 整仓 | **仓库根 `activepieces/`（自有 pnpm workspace + 独立锁）**；前端仅 `frontend/packages/ap-contracts/` 裁剪层（[D5](DECISIONS.md#d5)） | 拷贝源=已验证的 0.84.0 快照；弃 bun.lock 改 pnpm；记录上游 tag/commit 与路径映射（NFR-C02）。ee 处置见 NFR-S03 / AG-EE |
| FR-A02 | 画布（builder）以 React root 挂载进 DW Vue 页签 | MUST | `packages/web/src`（app/features/builder） | DW `frontend/developer-workstation` | 同文档 DOM 挂载，非 iframe；样式隔离（Shadow DOM 或 CSS scope）必须验证 |
| FR-A03 | 挂载组件的宿主契约：传入 flowId/token/projectId/locale，回调 onSave/onPublish/onClose | MUST | `packages/web/src/app`（路由与全局状态裁剪） | DW + 新"canvas-host"包 | **Q3 已裁决：抽"纯 builder 组件"**——不带登录页/platform-admin/全局路由，不依赖全局 localStorage 会话，React 栈封装在组件内；抽取边界以 Document 2 的依赖闭包逆向为准 |
| FR-A04 | 画布 API 请求指向平台网关域下的 AP server-api | MUST | `packages/web/src/api` | Kong 路由 + AP server-api | 同域后消除跨域/cookie 问题（Q6） |
| FR-A05 | 会话注入：mount 前获取**当前 DW 登录用户**的 AP token+projectId，经宿主契约传入组件（Q3 纯组件化后不再写全局 localStorage） | MUST | web 端 auth | admin-center `com.admin.ap`（从 signInShared 演进为 per-user 会话供给，Q4） | 同域（Q6）后 nonce 跨域握手随桥一并退役 |
| FR-A06 | DW 与画布双向联动：从 Service Task 面板一键打开对应 flow；画布保存/发布后回填 flowId/版本状态 | MUST | — | DW `ApTaskPropertiesPanel.vue` + canvas-host | 替代现开新标签去 :8085 的体验 |
| FR-A07 | 宿主兼容：DW 为独立 Vue3 SPA（无微前端沙箱），React 画布的全局副作用（style 注入、全局事件、history 操作）不得影响 DW 其余页面 | MUST | — | DW + canvas-host | 无 qiankun 一层反而简化：只需验证纯 SPA 内的样式隔离与卸载清理（GW-8） |
| FR-A08 | 画布构建产物进 DW 部署链（Vite 构建、非运行时 CDN） | MUST | `packages/web` vite.config | frontend 构建 + Dockerfile.local | 构建一次多环境 promote 的约束不变 |

### B 组 — Flow 生命周期

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-B01 | Flow CRUD API | MUST | `packages/server/api`（flows 模块） | AP server-api（自建镜像） | CE 的 DELETE /flows 返 400 的缺陷需 fork 修复或 UI 删 |
| FR-B02 | Flow 版本（draft→locked，LOCK_AND_PUBLISH） | MUST | server/api + shared | AP server-api | 发布通道脚本已依赖该操作序列 |
| FR-B03 | Flow 导出/导入 JSON（无密钥、displayName 幂等） | MUST | server/api（import/export） | `deploy/scripts/ap-export.js` / `ap-import.js`（复用） | schemaVersion 20 契约；lastUpdatedDate 必填坑已记录 |
| FR-B04 | 指定 flowId 原地导入（保 id 不漂移） | MUST | 同上 | `ap-import-to-id.js`（复用） | AI Generate flow 原地更新依赖 |

### C 组 — 执行引擎 / Worker

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-C01 | 引擎按 flow 定义执行步骤（表达式/变量解析） | MUST | `packages/server/engine` + `packages/shared` | AP 引擎（保持 TS，自建镜像内） | **不翻译成 Java**（语言策略见 Document 6，倾向已明确：引擎留 TS） |
| FR-C02 | Worker 队列消费 / 并发 / 超时 / 取消 | MUST | `packages/server/worker` | AP worker | Redis 队列沿用 |
| FR-C03 | 同步 webhook 全链路超时可配置（≥300s） | MUST | server/api webhook 模块 | AP + compose/k8s env | `AP_WEBHOOK_TIMEOUT_SECONDS` 已落地，纳入配置基线 |
| FR-C04 | 执行记录查询 API（runs / step logs） | MUST | server/api（flow-runs） | AP server-api；平台侧 `ApExecutionController` 保留 | 平台记录（引擎调用视角）与 AP run（内部视角）并存，需能互相关联（run id 透传 SHOULD） |

### D 组 — 触发器

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-D01 | Webhook trigger（含 /sync） | MUST | server/api webhooks | AP + Kong（生产仅放行 `/api/v1/webhooks`，沿用既有 Istio 规则思路） | Path B 契约 |
| FR-D02 | Schedule trigger | SHOULD | schedule piece + worker | AP | 白名单已含 schedule piece |
| FR-D03 | Manual/Test trigger | MUST | web + server/api | 画布内 | |
| FR-D04 | 外部事件触发平台 BPMN（AP→引擎反向） | COULD | — | workflow-engine REST | v1 不做；有需求时 AP http piece 调引擎 API 即可 |

### E 组 — 与自有 BPMN 引擎联动（brownfield 保持）

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-E01 | BPMN service-task 同步调 AP flow（Path B 全链路不回归） | MUST | webhooks /sync | `ApTaskExecutor` + `ProcessDeploymentManager`（现状保持） | 输入/输出变量映射、`wf_ap_execution_record` 落库 |
| FR-E02 | 设计器 AP 面板选择 flow（替代手填 flowId） | SHOULD | server/api flows 列表 | DW `ApTaskPropertiesPanel.vue` + `api/ap.ts` | 依赖 FR-J01 注册关系或直接列 project flows |
| FR-E03 | SSRF 防护维持（AP 主机白名单） | MUST | — | workflow-engine `SsrfProtection` | 既有 `validate(url,{AP主机})` 机制不变 |
| FR-E04 | 桥层执行语义合规：不自动重试、透传幂等键（executionId 进 webhook payload）、错误分类唯一映射 | MUST | — | `ApTaskExecutor` + `ApVariableMappingUtil` | 落实 §2.4 E-01/E-05/E-06；幂等键透传是对现状 Path B 的增量改动 |

### F 组 — Piece 体系

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-F01 | 离线 piece 目录（DB source、SYNC_MODE=NONE、fail-closed registry） | MUST | server（piece metadata） | `deploy/pieces/` 机制延续 | 白名单 **10 piece**：原 12 个按 Q9 移除 approval、todos（webhook/http/schedule + csv/json/xml/pdf/file-helper/text-helper/data-mapper） |
| FR-F02 | 自研 piece：本仓库内开发、构建、预装进镜像 | MUST | `packages/pieces`（框架）+ `packages/cli` | vendor 树内新增 piece 包 + `deploy/pieces/` 预装链 | vendor 后不再依赖 fetch-pieces.sh 拉公网 tarball（自研件） |
| FR-F03A | **关闭运行时 piece 安装**：全部 piece 构建期预装（`ready` 标记机制保持），worker 的 piece-installer 运行时安装路径禁用/移除（fail-closed） | MUST | `server/worker/src/lib/cache/pieces/piece-installer.ts`（0.84 实测 `:98` 批量 / `:161` 单个回退） | vendor 源码 + 镜像构建链 | 原生走 `bun install`，禁 bun（CR-01）后不做 npm 改写兜底。**影响面=piece 目录内容（运维/资产）**：缺 piece 是构建期问题，运行时不补装；失败模式=该 piece 在画布不可选 |
| FR-F03B | **关闭运行时 CODE step 依赖安装**：CODE step 不得声明外部 npm 依赖，仅可用内置/预打包依赖；校验前置到设计期（保存/发布时拒绝带外部依赖的 CODE step，fail-loud） | MUST | `server/worker/src/lib/cache/code/code-builder.ts`（0.84 实测 `:159` install / `:170` esbuild bundle） | vendor 源码 + DW 设计器校验 | 同样源自禁 bun。**影响面=开发者可写什么（产品能力）**，与 FR-F03A 风险不同：失败模式=flow 作者写不出某类逻辑，故**必须在设计期给出明确报错与替代指引**，不能等到运行期才失败。esbuild 打包环节保留（不依赖 bun） |

**Piece / CODE step 能力矩阵（v1 定稿）**：

| 能力 | v1 | 说明 |
|---|---|---|
| 构建期 piece 依赖（预装进镜像） | ✅ | 白名单 10 piece，`ready` 标记机制 |
| 运行时 piece 安装 | ❌ | FR-F03A，fail-closed |
| CODE step 内置依赖 | ✅ | 运行时已有/预打包的依赖可用 |
| CODE step 外部 npm 依赖 | ❌ | FR-F03B，设计期校验拒绝 |
| 任何运行时 npm/bun install | ❌ | CR-01 禁 bun + 断外网红线的共同结论 |
| FR-F04 | 既有 patch 升级为源码修改 | MUST | ~~piece-ai（run_agent）~~ | vendor 源码直接改 | **部分达成（2026-07-27）**，逐条状态见 [HERMES_PATCHES.md](HERMES_PATCHES.md)。✅ `patch-web-approvals.js` 已按 Q9 处置：脚本删除，改为源码级摘掉 Approvals 标签页（HERMES-PATCH-001）——产物里 `APPROVAL_PIECES_CONFIG` 与 6 个 SaaS piece 名已被 tree-shake，比原先「置空数组」更彻底。🚫 `patch-piece-ai-run-agent.js`（maxOutputTokens + reasoning 剥离）**本条已失去对象**：`piece-ai` 于 `669f7207` 移出白名单——气隙下 AI 件够不到模型提供方，留在目录里是死重。镜像里没有该 piece 的副本，转源码无从谈起。脚本保留仅供**联网 dev** 手工对运行中容器施用（该环境下 piece 仍由 npm 运行时安装，两个缺陷是活的）。**不要为满足本条而把 piece-ai 加回白名单** |

### G 组 — Connections / Secrets

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-G01 | Connection 管理（画布内建/选择） | MUST | web + server/api（app-connections） | AP（嵌入画布内） | 凭据 AES 加密存 AP 表 |
| FR-G02 | `AP_ENCRYPTION_KEY` 纳入平台 secret 管理（32-hex 校验） | MUST | server 配置 | deploy configmap/secret | 已有踩坑记录（非 hex crash-loop） |
| FR-G03 | 跨环境 connection 预建约定（同名） | MUST | — | 发布通道文档 + Jenkins 检查项 | flow 导入不带 connection，是已知坑 |
| FR-G04 | AI provider key 管理（custom provider，值须带 `Bearer ` 前缀） | MUST | server（ai-providers，无 PATCH） | 运维手册 + 引导脚本 | 0.84 原样发 apiKeyHeader 值的行为可在 vendor 后修正（SHOULD） |

### H 组 — 身份 / 权限 / 多租户

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-H01 | **per-user 账号供给**（Q4）：首次进画布时平台侧自动为当前 DW 用户创建 AP 账号并加入共享 project；用户无感；平台用户停用时 AP 账号联动禁用 | MUST | server/api（authn/users）+ **vendored CE 内部端点（Option B）** | admin-center `com.admin.ap`（供给服务）+ DW 后端 | **供给机制不得依赖"AP SMTP 未配置"、不得由平台托管用户密码**（P0-4 裁定，见 ARCHITECTURE_ANALYSIS §2.3）：Document 4 在 Option A（credential broker）/ Option B（AP domain service 内部端点）中选定；**直写 DB 仅 break-glass**。0.84 CE onboarding 坑（sign-up 不建 platform、invitation-only 403）须由所选方案处理；`ap-bootstrap-shared-account.js` 保留但仅引导**系统服务账号** |
| FR-H02 | 画布入口受平台 RBAC 控制（角色可见性） | MUST | — | DW 路由守卫 + 平台权限 | AP 自身权限不暴露 |
| FR-H03 | AP 管理面（platform admin UI）不对普通用户开放 | MUST | web（platform admin 路由） | canvas-host 裁剪 | Q3 纯 builder 组件天然不含这些路由 |
| FR-H04 | per-user 映射 v1 落地；per-BU（BU↔project）多租户 | MUST / COULD | server（projects/members） | per-user 见 FR-H01；per-BU 留 v2 | v2 演进时 per-user 供给逻辑须可扩展到多 project 归属 |

### I 组 — 运行历史 / 调试 / 可观测

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-I01 | 画布内查看 run 历史与步骤详情 | MUST | web（runs 视图） | canvas-host | |
| FR-I02 | AP server/worker 日志进平台日志体系 | MUST | — | docker/k8s 日志采集 | stdout 结构化即可，v1 不改 AP 日志格式 |
| FR-I03 | 健康检查（无 wget/curl，用 `node -e` 探 /api/v1/flags） | MUST | — | compose/k8s 探针 | 已有踩坑约定 |
| FR-I04 | 引擎侧执行记录与 AP run 关联字段 | SHOULD | — | `ApExecutionRecord` 增列 | 排障时从平台记录跳到 AP run |

### J 组 — flow 归属与环境晋升

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-J01 | 平台侧 flow 注册表：FU ↔ AP flow（含各环境 flowId 映射） | MUST | — | DW 后端新表（命名遵循平台前缀规范） | **Q7 已裁决：部署期解析**——`ProcessDeploymentManager` 在 BPMN 部署期经注册表将逻辑 flow 引用改写为当前环境实 flowId（与 delegate 注入同一改写点）；映射变更后须重新部署流程定义（运维手册注明） |
| FR-J02 | git 为 flow 定义的单一事实来源（`deploy/ap-flows/` 一 flow 一 JSON） | MUST | — | 现有机制 | |
| FR-J03 | Jenkins 手动发布（选环境/选 flow/prod 二次确认） | MUST | — | `Jenkinsfile.ap-flows-publish`（复用） | |
| FR-J04 | FU 导入导出/版本快照携带 flow 关联信息 | SHOULD | — | FunctionUnitExporter/Importer 体系 | 遵循 function-unit-portability 规则；v1 至少带注册关系，flow JSON 本体仍走 git |

### K 组 — AI 能力（brownfield 保持）

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-K01 | AI Generate 全链路不回归（契约 {reply,document,…} 不变） | MUST | webhooks /sync + **HTTP piece** | DW 后端 + AP flow | 超时 300s、SSE 心跳保持。**2026-07-28 更新**：模型调用由 `piece-ai` run_agent 改为 HTTP piece 直连，run_agent patch 一并作废（见 FR-K02）。**2026-07-29：功能整体停用**（`ai-generation.enabled` 缺省 false + 前端 feature flag），旧的 piece-ai 版产物（flow JSON / 生成脚本 / 导出件）已删除，prompt 可从 git `6436f537` 取回。恢复时按新链路重建，见 [VT-15](VENDOR_TRIM_CHECKLIST.md) |
| ~~FR-K02~~ | ~~run_agent 修改源码化（maxOutputTokens、reasoning-delta 剥离）~~ | **作废** | ~~piece-ai~~ | — | **2026-07-28 作废**：AI Generate 已改用 HTTP piece 直连模型端点，`run_agent` 链路不复存在 → 需求失去标的。`piece-ai` 已从 vendor 树删除，HERMES-PATCH-002 同时作废并删除脚本。**这不是欠账，是标的消失** |
| FR-K03 | AI provider 断外网可控（仅配置的 baseUrl 可出网） | MUST | server（网络策略） | k8s NetworkPolicy/Istio | 安全评审项 |

### L 组 — 沙箱 / 代码执行

| ID | 能力 | 级别 | Source in AP | Target System | 说明 |
|---|---|---|---|---|---|
| FR-L01 | Code step 在受控沙箱执行（0.84 CE 的 isolate 模式核实后定级） | MUST | server/engine + worker（sandbox 模式） | AP 容器 | Document 2 必须逆向 0.84 实际沙箱实现（isolated-vm/子进程/无隔离），据此定 NFR-S02 |
| FR-L02 | 资源限制（内存/超时）与网络策略（默认拒绝出网） | MUST | worker 配置 | 容器/网络层兜底 | 即使 AP 层隔离弱，容器+NetworkPolicy 必须兜住 |

---

## 6. 非功能需求（NFR）

| ID | 维度 | 要求 |
|---|---|---|
| NFR-P01 | 性能 | 同步 webhook 链路（引擎→AP→返回）常规 piece < 10s；AI flow ≤ 300s（与全链路超时对齐）；画布首屏加载 < 5s（构建产物按需分包） |
| NFR-P02 | 性能 | 画布挂载/卸载不泄漏：反复进出 DW 页签 20 次内存无持续增长（React root 正确 unmount） |
| NFR-A01 | 可用性 | AP 服务不可用时：DW 主功能（表单/BPMN 设计）不受影响；Service Task 执行失败落 `wf_ap_execution_record` 并走 BPMN 错误处理；画布区域显式报错（fail-loud，不静默空白） |
| NFR-A02 | 可靠性 | worker 崩溃重启后队列任务不丢——**前提是独立 Redis 实例 + `noeviction` + 持久化开启**（CR-08）；共用实例且平台配了 LRU 驱逐时该保证不成立 |
| NFR-S01 | 安全 | 遵循 secure-coding-sast 规则：AP 相关 URL 构造走 SafeUrlInput/SSRF 白名单；不落任何硬编码凭据；PII 不进日志 |
| NFR-S02 | 安全 | **0.84 默认 `UNSANDBOXED`+`UNRESTRICTED`（两层不隔离、无出网限制），不可上生产**。候选基线 `AP_EXECUTION_MODE=SANDBOX_CODE_AND_PROCESS` + `AP_NETWORK_MODE=STRICT`，**须过 Security Architecture Gate（SG-1~SG-8：piece loading / code execution / network deny / webhook / socket / Redis / filesystem / performance，见 ARCHITECTURE_ANALYSIS §4.5.2）方可转正为生产基线**；SG 失败按 §4.5.3 降级阶梯有序回退并在 Document 8 留档补偿控制。无论最终落到哪一级：AP 容器非 root 运行、默认无公网出口、仅开放白名单 egress（含 AI provider）为不可放弃的外部兜底 |
| NFR-S03 | 许可合规 | **表述修正（2026-07-22）：约束是 "No EE Business Features / No EE Proprietary Runtime Capability"，不是"`packages/ee/` 目录不进构建"**——因为已发现 CE 核心代码存在对 ee 目录的引用（`user-service.ts:29` 等），按路径一刀切会与"CE 能运行"冲突。每条 CE→EE 依赖须按 **EE-1（embed SDK，移除）/ EE-2（企业专有业务功能，移除）/ EE-3（共享技术依赖，EXTRACT·REIMPLEMENT·REPLACE 三选一）/ EE-4（CE 运行时硬依赖，必须闭合）** 逐条裁定；**EE-4 清零**方可进 Document 4。CI guard 相应地**从"扫目录"改为"扫已裁定必须移除的具体单元"**（GW-11）。LICENSE 文件保留。详见 DEPENDENCY_MAP §2.2 |
| NFR-S04 | 安全 | AP 表内凭据（connection/ai_provider）永不入 git、不进 flow 导出 JSON（现状已满足，回归项） |
| NFR-M01 | 多租户/隔离 | v1 单 platform 单 project、成员=per-user 供给账号（Q4）；数据库独立 schema（Q5 已裁决） |
| NFR-O01 | 可观测 | AP api/worker 容器日志进统一采集；关键失败（webhook 4xx/5xx、piece 安装失败、队列积压）可在平台侧告警 |
| NFR-O02 | 审计 | flow 发布动作在 Jenkins 留痕（操作人/环境/flow）；平台侧注册表变更走既有审计字段规范 |
| NFR-D01 | 数据一致 | AP TypeORM 迁移在 fork 后冻结为受控迁移集（0.84 基线 373 条 + append-only 增量，遵循 init-scripts-append-only 规则）；禁止自动执行未审计的上游迁移 |
| NFR-D02 | 迁移 ownership 隔离 | **AP 与 HERMES 不得共用 migration lifecycle**：同实例双 schema（`public`=HERMES / `activepieces`=AP），**独立 AP Migration Job 只管理 `activepieces` schema**，HERMES 迁移只管 `public`。**边界靠 DB 角色权限强制**：AP 角色仅对 `activepieces` 有 DDL/DML、对 `public` 无权或只读；HERMES 角色对 `activepieces` 无 DDL。schema 创建与授权属基础设施一次性动作，不归任何一方迁移集。详见 ARCHITECTURE_ANALYSIS §5.2.1 |
| NFR-D03 | **AP Migration Lifecycle（正式治理，非配置项）** | 产物：`baseline-0.84.0.manifest`（冻结上游 373 条，不可变）+ `hermes-migrations.manifest`（append-only 增量），入 git 受评审。环境矩阵：**dev `AP_AUTO_MIGRATE=true`（启动自动迁移）／test·uat·preprod·prod `AP_AUTO_MIGRATE=false` + 独立 AP Migration Job**。部署硬时序：Job 前置 `verifyDatabaseState()` → 执行迁移 → 后置校验记账表 == baseline+hermes（零漂移）→ 校验通过应用 Pod 才滚动；任一步失败阻断部署。变更门禁：时间戳 > 基线最大值、追加数组末尾、必填 `release`/`breaking` 元数据、同步 manifest（不一致则 CI 失败）、`breaking` 需架构评审+回滚预案。**漂移检测**：定期比对记账表与期望态，出现期望外的行即告警（Q8 纯自维护的实际保障）。回滚用原生 `rollbackToManifest`/`rollbackToVersion`，**仅在 `activepieces` schema 内**。详见 §5.2.2 |
| NFR-C01 | 兼容 | 现有 dev/uat/preprod 部署清单（compose + k8s）演进为自建镜像 `<registry>/activepieces:0.84.0-hermes.N`，标签含内部修订号 |
| NFR-C02 | 溯源（Q8 已裁决：纯自维护） | 完全放弃跟随上游，不保留 rebase 通道；vendor 基线（tag 0.84.0 / commit 05354b37 / 路径映射）记录存档用于溯源与排障；`HERMES-PATCH` 标记降为 SHOULD（帮助区分"我们改的 vs 上游原样"）；订阅上游安全公告，涉 0.84 的 CVE 自行评估手工移植 |
| NFR-C03 | CI 隔离 | 按路径过滤：**`activepieces/**` 变更不触发 frontend 4 应用重建**（二者已是独立 workspace，D5）；`frontend/packages/ap-contracts/**` 变更须触发依赖它的应用构建 + **Codegen 新鲜度校验**（AG-02.7）；各自锁文件变更时跑对应 workspace 的 install + 受影响包构建 |
| NFR-R01 | 容灾 | AP 数据（flows/connections/runs）纳入既有 PostgreSQL 备份策略；恢复演练至少覆盖"flow 全量从 git 重导入"路径 |

---

## 7. 兼容性需求（CR）

| ID | 兼容对 | 要求与已知事实 |
|---|---|---|
| CR-01 | AP 0.84 ↔ Bun（**公司禁令**） | **bun 全面禁止**：构建、CI、开发态、容器运行时一律不得出现（含镜像内 bun 二进制）。落地要求：① `activepieces/` 子树弃 `bun.lock`，用 pnpm 重建锁文件并核对解析差异；② `packageManager` 字段改 pnpm；③ 所有 `bun run/bunx` 脚本改 pnpm/node 等价；④ 自建镜像剥除 bun（现官方 `0.84.0-pieces` 镜像内含 bun 1.3.1，属存量违规，vendor 自建镜像时消除）；⑤ CI guard："产物镜像无 bun 二进制"与 NFR-S03（无 ee 代码）同级红线 |
| CR-02 | AP 0.84 ↔ Node.js | Document 5（BUN_TO_NODE_MIGRATION）从"是否迁移"改为"**如何迁移**"：逐组件枚举 bun 专有 API/行为依赖（server/api、worker、engine、web 构建、pieces 构建）并给出 Node/pnpm 等价改法；**凡 Node 下无法等价运行的组件必须改写**（禁令之下无"隔离保留 bun"选项）。有利事实：AP 在早期版本长期用 pnpm+Node（bun 是后来才切的），代码层 bun 专有依赖预计有限——但以矩阵实测为准，不得预设 |
| CR-03 | packages/web ↔ Vue3/Vite（DW 独立 SPA） | **React 19**（0.84 实测 react/react-dom/@types 均 19）+ Vite 产物挂载进 Vue 宿主：须验证 ① 样式隔离（tailwind/radix 全局样式 vs Element Plus）② React portal/全局事件在同文档共存 ③ 双框架共存的包体积预算 ④ **react/react-dom 单实例**（宿主与 builder 必须 dedupe 到同一 React 19，多实例会致 hooks/Context 崩溃）。DW 现无 React 依赖，引入 react/react-dom **19** 为新增前端依赖。（无微前端沙箱介入，见 §1.1） |
| CR-04 | `ap-contracts` ↔ frontend workspace（**范围经 D5 缩小**） | AP 后端依赖**不再进入** frontend 锁（两个独立 workspace）。前端侧剩余对账面：① `ap-contracts` 的 `@sinclair/typebox` 版本与平台既有 pin（commit 3befe97f）统一；② builder 组件引入的 react/react-dom **19** 与宿主 dedupe（AG-01）；③ workspace 级 `overrides` 对 `ap-contracts` 的波及；④ `ap-contracts` 变更须过 Codegen 新鲜度校验（AG-02.7） |
| CR-05 | AP server ↔ Java17/SB3.2 | 语言边界：AP server/engine/worker 保持 TS 独立服务，Java 侧只经 HTTP（既有 `ActivepiecesApiClient`/`ApTaskExecutor` 模式）。不做 TS→Java 翻译，除非 Document 6 论证某模块必要 |
| CR-06 | AP ↔ Kong（**Q6 已裁决**） | AP server-api 纳入 Kong 路由（画布同域调用 + 生产仅暴露 webhooks 的差异化策略）；:8085 nginx edge 桥与 nonce 握手**并行一个版本后退役**，退役列入下一版本收尾清单 |
| CR-07 | AP ↔ PostgreSQL（**Q5 已裁决**） | AP 表迁**独立 schema（同实例）**：TypeORM schema 配置、连接串、备份策略同步调整；dev 现状（共库 public，通用表名 user/project/flow/…，email 在 `user_identity`）做一次性迁移；`piece_metadata` 进程内缓存坑（直写库须重启/走 API）纳入运维手册 |
| CR-08 | AP ↔ Redis | **生产：AP 必须使用独立 Redis 实例（或托管 Redis），配置 `maxmemory-policy=noeviction` + 开启持久化**；dev/测试：独立逻辑 DB 号（`AP_REDIS_DB`）可接受但须标注与生产拓扑不同。理由：AP 用 Redis 承载 BullMQ 队列/PubSub/分布式锁/并发池，而 **eviction policy 与 persistence 是实例级全局配置**——共用实例时平台的缓存策略（如 `allkeys-lru`）会导致 AP 队列 job 被静默驱逐 = 工作流执行丢失（与 NFR-A02 冲突）；failure domain、CPU（Redis 单线程）、内存亦全部共享。key 前缀隔离属代码级改造，**不能替代实例隔离**。详见 ARCHITECTURE_ANALYSIS §5.3 |
| CR-09 | 磁盘/构建资源 | vendor 后首次 install+build 预算 ≥ 8GB 空闲；CI 构建机同样评估。构建前置检查纳入脚本 |
| CR-10 | 版本纪律 | 一切以 tag `0.84.0` 为准；本地 checkout master(0.86.2) 仅供对照。0.86 的包结构变化（core/ 出现等）**不得**混入 |

---

## 8. 约束与"不得破坏"规则

继承 AP_integration.md（仓库外立项文档，见文首说明）§15，结合本仓库具体化：

1. 不替换现有认证/RBAC/Kong/用户/BU 模型；不引入第二套身份或权限系统。
2. **公司明令禁止 bun**：全仓（含 `activepieces/` 子树）、CI、镜像运行时任何环节不得使用或包含 bun；`frontend/` 与根构建链保持 npm/pnpm 不变。
3. 不破坏 1.2 节任何 brownfield 链路；替代先行、旧链路后退役（尤其 AI Generate 与 Path B）。
4. **No EE Business Features / No EE Proprietary Runtime Capability**（**不是**"`packages/ee/` 目录不进构建"——该表述已作废，见 NFR-S03）：EE 专有业务功能与专有运行能力不得使用/分发；每条 CE→EE 依赖按 EE-1~EE-4 逐条裁定；EE-4 须清零。
5. AP 数据库迁移不自动执行未审计版本；平台侧新表遵循 JSON-row-storage 与命名规范。
6. 断外网红线：运行时不得从公网拉取 piece/元数据/遥测（fail-closed 配置保持）。
7. 磁盘余量检查为一切 install/build 的前置步骤。
8. 不在架构评审（Document 2–4 + 决策门）通过前修改生产代码——本文档冻结是第一道门。

---

## 9. 验收标准（Golden Workflows）

集成完成的判定以下列端到端场景全绿为准（dev 环境，均可自动化）：

| # | 场景 | 覆盖 |
|---|---|---|
| GW-1 | DW 内打开 Function Unit → 画布页签加载 → 新建 flow（Webhook→Code→Return Response）→ 保存→发布 | FR-A02/03/05/06、FR-B01/02 |
| GW-2 | BPMN 流程含 AP service-task → 部署 → 起实例 → AP flow 同步返回 → 变量回填 → `wf_ap_execution_record` SUCCESS | FR-E01（回归） |
| GW-3 | AI Generate 三阶段对话产出英文 REQUIREMENTS/DESIGN 文档并 Apply 成功 | FR-K01（回归） |
| GW-4 | Router 分支 + Loop + 失败重试 flow 在画布内 test run，run 详情可查 | BR-05/06/14、FR-I01 |
| GW-5 | 自研 hello-world piece：树内开发 → 构建 → 预装镜像 → 画布可选用并执行 | FR-F02、FR-F03A |
| GW-6 | flow 导出入 git → Jenkins 导入另一环境（flowId 映射经注册表解析）→ BPMN 侧无需改 XML 即可执行 | FR-J01/02/03 |
| GW-7 | 断网 × 沙箱联合：在**生效的沙箱/网络基线**下（AG-05 定档），AP 容器无公网出口时上述 1–6 全部可用；piece 目录仅白名单；预装 piece 在 isolate rootfs 下可加载 | FR-F01、FR-F03A、NFR-S02、AG-05 |
| GW-8 | 画布页签反复进出 20 次无内存泄漏、无样式串扰（DW 其余页面 UI 正常） | NFR-P02、CR-03 |
| GW-9 | AP 服务停机时 DW 其他功能正常、画布区域显式报错 | NFR-A01 |
| GW-10 | 权限：无 AP 权限角色看不到画布入口；SYS_ADMIN 可见 | FR-H02 |
| GW-11 | 合规扫描（CI 自动）：自建 AP 镜像与全部前端产物中 **无 bun 二进制**；且**无"已裁定必须移除的 EE 单元"**（按 Document 3.5 产出的具体清单扫描，**非按 `packages/ee` 目录**——目录式规则已作废，见 NFR-S03） | CR-01、NFR-S03 |
| GW-13 | per-user 供给：新 DW 用户首次进画布 → 自动获得 AP 账号与共享 project 访问 → 可编辑可 test run；重复进入幂等；平台停用该用户后 AP 侧同步失效 | FR-H01、AG-06 |
| GW-14 | 共享 project 协作：用户 A 建 flow 并配 Connection → 用户 B 打开同一 flow 可见可执行；A 持锁时 B 画布显示只读；越权操作被服务端拒绝 | FR-H02、AG-03、Q4a |
| GW-12 | workspace 回归：`ap-contracts` 裁剪层并入 frontend workspace 后，`admin-center` / `developer-workstation` / `user-portal` / `login` 四应用 install+build 全绿，关键页面 Playwright 冒烟通过（依赖解析漂移不改变既有行为） | D2、CR-04、AG-02.5 |

---

## 10. 架构关键决策 → 已迁出

> Q1–Q9、Q4a 及后续 D1–D4 的完整裁决正文已迁至 **[DECISIONS.md](DECISIONS.md)**
> （ADR，全局约束唯一事实来源），并在那里补充了项目性质约束 X-1~X-7。
> 本文档只做引用，不再重复裁决正文。

**摘要索引**：Q1 vendor 边界（→ frozen vendor + controlled fork）｜Q2 禁 bun｜Q3 纯 builder 组件｜
Q4 per-user 身份 + Q4a CE core RBAC 补丁｜Q5 独立 schema｜Q6 Kong 收编、旧桥并行一版后退役｜
Q7 flow 注册表 + 部署期解析｜Q8 Frozen Baseline + Controlled Fork｜Q9 移除 approval/todos piece｜
D1 成本模型重估｜D2 AG-02 重定义｜D3 新增 Doc3.5｜D4 合规评估尚未启动。

---

## 11. 后续文档路线

> 当前状态与下一步见 **[STATUS.md](STATUS.md)**；阻塞关系见 **[OPEN_GATES.md](OPEN_GATES.md)**。

```
Doc1 REQUIREMENTS（本文）
   ↓
Doc2 ARCHITECTURE_ANALYSIS  ✅ 六线逆向完成
   ↓
Doc3 DEPENDENCY_MAP         ✅ 初稿
   ├──────────────┬──────────────┐
   ▼              ▼              ▼
 AG-02        Doc3.5          AG-05
 前端 workspace  EE_REMOVAL_PLAN  沙箱联合验证
 （最高优先级）   （D3 新增）
   └──────────────┴──────────────┘
                  ▼
            Doc4 INTEGRATION_DESIGN   🔒 被 Gate 阻塞
                  ↓
            Doc5–10（Bun 迁移 / TS-Java 策略 / DB 迁移 / 安全模型 / 测试 / 实施计划）
```

| # | 文档 | 状态 | 备注 |
|---|---|---|---|
| 1 | REQUIREMENTS.md | 待冻结 | 本文 |
| 2 | ARCHITECTURE_ANALYSIS.md | 待冻结 | 六线逆向完成；决策/门禁已迁出 |
| 3 | DEPENDENCY_MAP.md | 初稿 | 模块处置矩阵（A/B × C/D） |
| **3.5** | **EE_REMOVAL_PLAN.md** | **⏳ 待编写** | **D3 新增**：EE 剥离与 CE 边界实施方案；含 1000–1300 行的**精确组成拆分**与 **HERMES 既有能力复用核查** |
| 4 | INTEGRATION_DESIGN.md | 🔒 阻塞 | 受 AG-02 / AG-EE / AG-05 / AG-03 / AG-06 阻塞，Gate 未过的 Layer 只能写"候选 + 待验" |
| 5 | BUN_TO_NODE_MIGRATION.md | 待编写 | 因禁令升级为强制迁移方案；vendor 后第一批实施 |
| 6–10 | TS→Java 策略 / DB 迁移 / 安全模型 / 测试策略 / 实施计划 | 待编写 | 依序 |

**前置动作（不改生产代码）**：源码分析基于已验证的 0.84.0 快照；
任何 install/build 前先确认磁盘余量 ≥8GB。
