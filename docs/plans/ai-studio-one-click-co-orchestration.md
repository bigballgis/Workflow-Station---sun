# 设计 Plan：AI Studio 一键生成改走 Content Organizer（DW 逐阶段编排）

> 状态：**设计已确认**（2026-11-03，用户拍板）：方案 B；【待确认】第 2–6 项全部按推荐。事实 1 已在 dev 实证（见下）。
> 2026-11-03 已按 playbook 实现（分支 `claude/one-click-co-orchestration`，见文末【实现记录】）。本文只定方案，不含实现代码。
>
> **CO / Automation 侧零新增**：一键生成的 5 步都是普通 PROPOSE，走现有 flow `ai-studio-co-agent` 和现有 agent setting
> 「Function Unit」（主管按 PHASE 路由），不新建 prompt / agent setting，也不新建 Automation flow，CO Instruction 不改。
> 5 步的顺序保证每个专家依赖的对象都已在上游（表单要流程与表、动作要表单与决策）。只有 UAT 发现专家因「下游清空」拒绝出数据时，
> 才在现有专家 Instruction 里补一句（风险 2）。
> 前置：顾问对话与 Propose change 已经 Automation → CO（`56e817440`、`4c7556adb`），dev / UAT 真人验证通过。
> 契约：[CO 输出契约](../design/developer-workstation-ai-studio-co-output-contract.md)、
> [CO Agent Instruction](../design/developer-workstation-ai-studio-co-agent-instructions.md)、
> [接入说明](../guides/examples/ai-studio/README.md)。

## 【背景 / 问题】

- 一键生成（Build with AI 弹窗 → One-click generate）现在是**一次** `scope=ALL` 的网关调用：
  `AiStudioOneClickComponentImpl#start` → `AiStudioChatServiceImpl#prepareOneClick`（请求线程序列化上下文）→
  作业线程 `runProposal` → `aiGenerationService.callAiModel`（GAIP）→ 预览 → 提案作业的 commit 钩子在无 ERROR 时
  自动 Apply（`applyProposal`，scope `ALL`，`AiWriteServiceImpl` 全量替换）→ 结果卡落进 PROCESS_DESIGN 线程。
- `prepareOneClick` 的 draft 没有 `staffId`，所以 `ai-generation.ai-studio.provider=AUTOMATION` 时一键生成**也走 GAIP**，
  是 AI Studio 里最后一条没迁走的路径。
- CO 的「Function Unit」agent setting 是主管 + 专家，委派语义是 `transfer_to_agent`：一次请求只有**一个**专家回答，
  主管拿不回控制权，也不接受 `MODE=ONE_CLICK`（Instruction 明确拒绝）。整套设计只能由 DW 自己逐阶段发 PROPOSE。

### 探查中发现、会直接决定方案的两个事实

1. **动作 ↔ 用户任务的绑定只在「流程与动作同一次写入」时才落到 BPMN。**
   `AiWriteServiceImpl#writeProcessDefinition` 里 `AiBpmnActionBindingWriter.bindStageActions(bpmn, 已落库动作, generatedData.actionDefinitions)`
   靠的是**本次** generatedData 里动作的 `stageIds`；`ActionDefinition` 实体不存 `stageIds`，`AiContextSerializer`
   也不输出它——BPMN 的 `actionIds` 是这条关系唯一的持久化位置。所以如果按契约原文「每步 Apply 后再发下一步」、
   ACTIONS 排在 PROCESS 之后单独 Apply，任务节点上**拿不到动作按钮的绑定**（表单不受影响：运行时读
   `dw_form_stage_bindings`，FORMS 切片自己写）。

   **核实结果（2026-11-03，代码 + dev 库）**：
   - 写入侧：`writeActionDefinitions` 只建 `ActionDefinition` 行，丢弃 `stageIds`；scope=ACTIONS 的数据不含
     `processDefinition`，`writeProcessDefinition` 不执行，BPMN 一字不动。反过来单独 Apply PROCESS 时，新 BPMN 来自模型
     （Instruction 禁止输出数字 id），本次又没有 `actionDefinitions`，`bindStageActions` 直接返回——**已有的绑定也一并丢失**。
   - 兜底：`ProcessBpmnStaleIdFixer` / `ActionBindingLifecycle.reconcile` 只能按 BPMN 里**已有的** `actionNames` 把失效 id
     找回，BPMN 里没有 `actionIds` / `actionNames` 时无从恢复；而且只在设计器保存流程时运行，AI 写入不经过它。
   - 运行时：引擎 `BpmnActionParser` 只读 BPMN 的 `actionIds` / `globalActionIds`；Portal `TaskActionService` 拿到空列表就
     返回空（代码注释明确不回退到流程全部动作），`ActionDefinitionLookupComponent` 按名字配对只用于没有 `source_id` 的旧发布。
     结论：**没有任何按名字兜底，任务上没有按钮**。
   - dev 实证：FU 50036 在 2026-09-17 先后 Apply 了 PROCESS → ACTIONS → PROCESS → PROCESS 四张提案（AI Studio 逐阶段）。
     那张 ACTIONS 提案里每个动作都带了正确的 `stageIds`（`Task_SubmitPayment` / `Task_ReviewPayment`），库里有 3 个动作
     （50386–50388），但现在 BPMN 的两个 userTask 上**都没有** `actionIds`。对照组 FU 50143 / 50144 是一键生成（`ALL`
     一次写入），每个 userTask 的 `actionIds` 都指向存活的动作。
   - 未验证：没有把 50036 部署到 Portal 实际打开任务看按钮（运行时结论来自代码）。
2. **替换型 scope 没有撤销**：`AiStudioUndoService` 只对 PROCESS / TABLE_RELATIONS 和 upsert 型 scope 发令牌，
   TABLES / FORMS / ACTIONS / DECISIONS / ALL 都不可撤销（重建后 id 变化、引用会断链，见该类注释）。逐步写库后在
   第 k 步失败，前 k-1 步无法用现成机制回滚。

## 【目标】

- `provider=AUTOMATION` 时，一键生成由 DW 按 **流程 → 表（含表关系）→ 表单 → 决策 → 动作** 依次发 5 个 PROPOSE 给 CO，
  每一步的 CURRENT DESIGN 带上本轮上游步骤的产出。
- 写入语义、预校验、自动 Apply、线程落卡、Requirements 文档追记与今天**完全一致**（仍是一次 `ALL` 全量替换）。
- 前端能看到「第 k/5 步：<阶段>」进度；Stop 在任何一步生效且不留半截设计。
- `provider=GAIP` 时一键生成**一行不变**。

## 【非目标】（本阶段不做）

- 视图、邮件三阶段（连接 / 模板 / 监控）、service task 绑定仍不进一键生成（契约第 3 节规则 4）。
- 不改 CO 的委派模式，不给 CO 配工具（dry-run 工具是另一条路，见契约第 4 节）。
- 不做「从失败的那一步续跑」、不做「预览 ERROR 时按步自动修复」（列入后续）。
- 不给一键生成加撤销（今天的 `ALL` 也没有；不在本次改变）。
- 不修手动逐阶段 Propose 的既有缺口（见【风险与回滚】第 5 条，单列后续）。
- 不改 `AiWriteServiceImpl` 的写入语义、不改 `AiStudioPhase` / `SCOPE_SLICES`。

## 【模块】

dw（后端 + developer-workstation 前端）｜docs（契约与接入说明随实现更新）。无 schema 变更、无 Kong / 部署变更。

## 【方案】

### 方案 A：逐步生成 + 逐步 Apply（契约原文的写法）

每步：PROPOSE → 预览 → 该步 scope 的 Apply → 下一步从库里重新序列化上下文。

- 优点：每步的 CURRENT DESIGN 是真实落库状态（带 id）；与手动逐阶段完全同一条路径。
- 缺点：
  - **动作绑定丢失**（事实 1）：必须再加一个「重写流程」的收尾写入才能把 `actionIds` 补进 BPMN，等于最后还要一次跨切片写入；
  - **失败 / Stop 留半截设计**：第 1 步就替换了旧流程，旧表单的 stage 绑定、旧动作随之失效；第 k 步失败时前面无法撤销（事实 2），
    要么新造整 FU 快照回放，要么让用户面对一个半新半旧的 FU；
  - 一次运行要写 5 次库、拿 5 次 AI 锁，期间队友会看到中间态，别人的 Apply 可能插在两步之间；
  - 每步都要在作业线程里重新序列化上下文（可行：`serializeFunctionUnitContext` 自带只读事务，预览器已在作业线程这样用），但多 5 次全量序列化。

### 方案 B：逐步生成、内存合并、最后一次 Apply（**推荐**）

每步只调 CO + 解析，**不写库**；把产出切片合并进一份内存里的「本轮设计」，下一步的 CURRENT DESIGN 就是它；
5 步都成功后，合并结果走今天一模一样的「预览 `ALL` → 无 ERROR 自动 Apply `ALL`」。

- 优点：
  - 写入仍是一次 `ALL`，`writeProcessDefinition` 同一次写入里拿得到表单与动作的 `stageIds`，表单 / 动作 / MI 子表的 id 注入全部照旧；
  - 任何一步失败或 Stop：**库里什么都没变**，不存在回滚问题；
  - commit 钩子、预览、线程落卡、Requirements 追记、作业 / Stop 语义全部原样复用；AI 锁只在最后持有几秒；
  - 作业线程里**不碰 JPA**，直到 commit 钩子（与今天相同）。
- 缺点：
  - 中间步骤的 CURRENT DESIGN 是生成数据形状、没有数据库 id——但专家 Instruction 本来就禁止输出数字 id（`formId` / `actionIds` / `subTableId` 由平台注入），影响有限；
  - 与契约第 1 节的「每步 Apply 后再发下一步」字面不一致，契约要随实现改成「每步结果并入下一步的 CURRENT DESIGN，最后一次 Apply」。

### 推荐：方案 B

理由：A 要额外补「重写流程」收尾和「整 FU 快照回滚」两套新东西才能正确，B 两者都不需要；B 与现有一键生成的提交 / 失败 / Stop 语义完全一致，爆炸半径最小。

## 【方案 B 详细设计】

### 1. 编排放在哪一层

| 层 | 职责 | 改动 |
|---|---|---|
| `AiStudioOneClickComponentImpl`（component） | 权限、作者与 SecurityContext 抓取、提交作业、commit 钩子（预览 + 自动 Apply + Requirements 追记）、线程落卡 | 基本不变：`work` 由「一次 `runProposal`」换成「交给编排器」；GAIP 分支保持现状 |
| `AiStudioChatServiceImpl#prepareOneClick`（service，请求线程） | AUTOMATION 时把编排要的一切在请求线程定下 | 新增 AUTOMATION 分支（见第 2 节） |
| **新类** `AiStudioOneClickOrchestrator`（`service/impl`，无状态、无 JPA） | 5 步循环：拼消息 → `AiStudioAutomationClient.chat` → `AiResponseParser.parseText`（+ 可修违规修复一次）→ 按 scope 裁剪 → 并入本轮设计 → 上报进度 | 新增 |
| `AiStudioProposalJobService` | 作业生命周期、取消、超时 | 增加进度上报通道（见第 4 节） |

不放进 component 的理由：编排是「模型调用 + 解析」的组合，和 `runProposal` / `agentProposal` 同层；component 只负责事务外的编排入口与写入，符合 `backend-architecture` 的分层。

**复用清单**

- `AiStudioAgentMessage.build("PROPOSE", phase, scope, currentDesignJson, schemaJson, formatSpec, documents, conversation, userRequest)`——每步一次，MODE 必须是 `PROPOSE`（CO 主管拒绝其他 MODE）。
- `AiStudioAutomationClient.chat(message, staffId)`——不变。
- `AiResponseParser.parseText` + `AiGenerationServiceImpl.REPAIRABLE_ERROR_CODES` + `buildRepairMessage`——即现有 `agentProposal` 的「可修违规带原话再问一次」，抽成编排器与 Propose 共用的一个方法，**每步最多修一次**。
- `AiStudioChatServiceImpl.allowedSlices(scope)`——每步裁剪；合并结果最后再按 `ONE_CLICK_SLICES` 裁一次（与今天一致）。
- `AiStudioProposalPreviewer.preview(fuId, "ALL", normalizedData)`、`AiStudioChatComponentImpl#applyProposal(scope=ALL)`——commit 钩子原样。
- `AiWriteServiceImpl` 全量替换语义——原样，不新增写入路径。
- `AiStudioUndoService`——不涉及（`ALL` 不发令牌，与今天一致）。

### 2. 上下文在哪个线程序列化、工号怎么带

`ProposalDraft` 注释的约束是：上下文序列化依赖请求事务里的 JPA 懒加载，必须在请求线程完成。方案 B 让作业线程完全不需要 JPA：

请求线程（`prepareOneClick`，AUTOMATION 分支）一次性准备：

- `baseDesign`：`serializeFunctionUnitContext` → 转成 JSON 树（`Map`），作业线程只做纯 Map 操作；
- `schemaMetadata` JSON、`promptTemplateService.resolve("GENERATION")`（FORMAT SPECIFICATION）、文档列表、CONVERSATION 转写（`historyTranscript`）、用户需求文本；
- `staffId(userId)`：没有工号在请求线程就以 `AI_STUDIO_NO_EMPLOYEE_ID` 4xx，不等进作业才失败。

这些放进 draft（沿用「`staffId` 非空 = 走 CO」的现有约定；是扩展 `ProposalDraft` 还是另起一个 `OneClickDraft` record 留给实现期决定，不影响行为）。

作业线程：编排器只用 draft 里的不可变数据；直到 commit 钩子才进事务（预览器 `@Transactional(readOnly)`、`applyProposal` 自带锁与事务，均为今天已在作业线程运行的代码）。

### 3. 每一步的 CURRENT DESIGN 与 USER REQUEST

| 步 | PHASE | SCOPE | CURRENT DESIGN 里的核心切片 |
|---|---|---|---|
| 1 | PROCESS_DESIGN | PROCESS | 本步旧值（沿用命名用）；其余核心切片清空 |
| 2 | TABLE_DESIGN | TABLES | 上游 = 本轮流程；本步旧值 = 旧表 + 旧关系；下游清空 |
| 3 | FORM_DESIGN | FORMS | 上游 = 本轮流程、表、关系；本步旧值 = 旧表单；下游清空 |
| 4 | DECISION_DESIGN | DECISIONS | 上游 = 本轮流程、表、关系、表单；本步旧值 = 旧决策；下游清空 |
| 5 | ACTION_DESIGN | ACTIONS | 上游 = 本轮前四步；本步旧值 = 旧动作 |

规则：**上游用本轮结果，本步保留旧值（让重新生成时名称稳定），下游清空**。下游旧值会被这次 `ALL` 删掉，留着只会诱导专家去对齐马上消失的对象。非核心切片与目录（`orgCatalog`、`serviceTasks`、`automationFlows`、视图、邮件）原样保留，它们不被一键生成触碰。

决策排在动作前：`DECISION_TABLE` 动作要引用已存在的 `decisionKey`（契约第 6 节）。

USER REQUEST = 用户需求原文 + 一段系统行：一键生成第 k/5 步、只产出该 scope 的**完整**切片、CURRENT DESIGN 里的上游切片是本轮生成的权威结果、下游阶段随后由平台继续。CO Instruction **不需要改**（专家已按 PHASE/SCOPE 工作）；契约文档补一句这段系统行的含义即可。

**每步的「无数据块」处理**：PROCESS / TABLES / FORMS / ACTIONS 没有数据块 → 该步失败（专家按 Instruction 会在说明里写缺什么，原话作为错误信息）；DECISIONS 允许没有数据块或空数组（需求里没有决策是常态），视为 `decisionDefinitions: []`。

### 4. 进度反馈与 Stop

- **进度**：作业快照 `AiStudioProposalJobResponse` 增加只在内存里的 `progress`（当前步序号、总步数、当前 PHASE、已完成步的耗时）。不落库——DW 重启后未完成作业本来就判 `AI_STUDIO_PROPOSAL_INTERRUPTED`，进度没有意义，因此**无需 init-script**。
  `AiStudioProposalJobService#submit` 增加一个带进度回调的重载（`work` 接收一个进度 sink），现有三个调用方不变。
- ~~**队友可见**：每进一步发一次线程事件~~ → **本轮未做**（任务整理时确认）：现有事件的 `phase` 决定队友提示落在哪个线程，塞进步骤阶段会让提示乱跳；队友仍看到「某某正在生成」，只是没有第几步。
- **前端**：`AiStudioEntryDialog.vue` / `AiStudioWorkspace.vue` 现有轮询读到 `progress` 时显示「第 k/5 步：<阶段名>」；三语 i18n（en / zh-CN / zh-TW）。`noteDuration` 文案改为「通常 3–12 分钟」。GAIP 下 `progress` 为空，界面与今天一致。
- **Stop**：沿用 `cancel` → `future.cancel(true)`。编排器在**每步开始前**检查线程中断状态，已取消就不再发下一步；正在进行的 CO 调用能否被中断取决于 webhook 客户端 [待确认]，但无论能否中断，`run()` 的 `isTerminal` 守卫都会丢弃迟到结果，commit 钩子不会执行——**库里不会有任何改动**。CO 侧那次调用会跑完（成本），与今天一致。
- **最终结果卡**：说明文字 = 5 步专家说明按「### 1/5 Process Design …」分节拼接（专家会在说明里列人工步骤和依赖，比今天单次回答更有用），数据 = 合并后的 `ALL` 提案，落 PROCESS_DESIGN 线程（不变）。

### 5. 失败语义

| 情形 | 行为 | 库 |
|---|---|---|
| 第 k 步 CO 报错（`Content Organizer failed: …`、204 无回复、超时） | 作业 FAILED，`errorCode` 保留原码，`errorMessage` 前缀「第 k/5 步 <阶段>：」 | 不变 |
| 第 k 步可修违规（`REPAIRABLE_ERROR_CODES`） | 带 `buildRepairMessage` 原话重问该步一次；仍失败 → 同上 FAILED | 不变 |
| 第 k 步无数据块（DECISIONS 除外） | FAILED，新码 `AI_STUDIO_ONE_CLICK_STEP_NO_DATA`，消息带专家说明 | 不变 |
| Stop / 被新请求替换 / 超时 | CANCELLED / SUPERSEDED / TIMED_OUT（现有码） | 不变 |
| 5 步成功，合并预览有 ERROR | 不自动 Apply；结果卡展示 ERROR、Apply 禁用；用户可「让 AI 修正」（现有 follow-up） | 不变 |
| 5 步成功，自动 Apply 抛错 | 现有 `ONE_CLICK_AUTO_APPLY_FAILED` WARNING，卡片可手动 Apply | 不变 |
| 5 步成功，Apply 成功 | 与今天相同（无撤销令牌） | 一次 `ALL` 替换 |

已完成步骤的结果在失败时只进日志（每步耗时、切片条数），不落卡——MVP 里重试从第 1 步重新开始。

### 6. 总耗时与超时

- 单步实测 15 秒～2 分多钟；单次调用上限 `AI_STUDIO_TIMEOUT_SECONDS=310`（AP sync webhook 300s 先到）。
- 5 步典型 3～12 分钟；最坏（每步都修复一次且都接近上限）理论 5×2×310s ≈ 52 分钟，现实中不会出现。
- `ai-generation.studio.proposal-max-runtime-minutes`（默认 30，从 `startedAt` 起算）对一次运行整体生效：**推荐保持 30 不变**，超时由现有 sweep 判 `AI_STUDIO_PROPOSAL_TIMED_OUT` 并中断。编排器每步开始前另查一次剩余时间，剩余不足一个单步上限（310s）时直接以 `AI_STUDIO_PROPOSAL_TIMED_OUT` 结束，不发一个注定被掐断的调用。
- Kong 300s 只约束 HTTP 请求，提交 / 轮询都是短请求，不受影响。
- **线程池**：`proposal-workers` 默认 2，一次一键生成最长占一个 worker 30 分钟；两人同时一键生成会让所有 Propose change 排队直到 `AI_STUDIO_PROPOSAL_QUEUE_FULL`。建议 UAT 把 `AI_STUDIO_PROPOSAL_WORKERS` 调到 4（只改环境变量，不改默认值）[待确认]。

### 7. GAIP 路径

`provider=GAIP`：`prepareOneClick` 返回 `staffId=null` 的 draft，component 走今天的 `runProposal`（`callAiModel` 单次 `ALL`），一行不变。分支判据就是 draft 的 `staffId`，和 Propose change 用的是同一个判据，不新增开关。`application.yml` 里「一键生成暂时总走 GAIP」的注释和 `AiStudioChatServiceImpl` 类注释随实现更新。

## 【影响面】

| 层级 | 变更 |
|------|------|
| Service | 新 `AiStudioOneClickOrchestrator`；`AiStudioChatServiceImpl#prepareOneClick` 加 AUTOMATION 分支；`agentProposal` 的「解析 + 修复一次」抽成共用方法 |
| Component | `AiStudioOneClickComponentImpl#start` 的 `work` 按 draft 分流；commit 钩子 / 落卡不变 |
| 作业 | `AiStudioProposalJobService` 新增带进度 sink 的 `submit` 重载；`AiStudioProposalJobResponse` 加内存 `progress`；新线程事件类型 |
| Entity / SQL | 无（进度不落库） |
| 前端 types / views | `api/aiGeneration.ts` 作业类型加 `progress`；`AiStudioEntryDialog.vue`、`AiStudioWorkspace.vue` 显示步骤；线程事件处理加新类型 |
| i18n | `ai.studio.oneClick.*` 步骤文案与 `noteDuration`，en / zh-CN / zh-TW |
| 部署 / 配置 | 无新配置键；UAT 可选调 `AI_STUDIO_PROPOSAL_WORKERS` |
| 文档 | 契约第 1、3 节一键生成写法；`docs/guides/examples/ai-studio/README.md` 去掉 "still uses the AI gateway"；`application.yml` 注释 |
| 跨服务 | 无（flow `ai-studio-co-agent`、piece 1.0.5、CO Instruction 都不改） |

## 【数据与契约】

- DW → CO 消息格式不变，仍是 `AiStudioAgentMessage` 的分节消息、MODE=PROPOSE；每次一键生成 = 5 次（含修复最多 10 次）调用。
- 作业快照 `progress` 为只增字段，旧前端忽略即可；GAIP 下为空。
- 结果卡的 `proposalScope` 仍是 `ALL`，`proposal` 仍是六切片——与 GAIP 产物同构，前端卡片、follow-up、手动 Apply 全部不变。

## 【分期】

- **MVP**：方案 B 全部（编排器、请求线程准备、进度、Stop、失败语义、前端步骤显示、文档更新）；dev mock E2E + UAT 真实 CO 验证。
- **后续**：
  1. 失败续跑：缓存已完成步骤，重试从失败那一步开始；
  2. 按步预览：每步把累计结果做一次 `ALL` 预览，ERROR 只落在本步切片时带原话修复本步（需要把预览 ERROR 转成修复消息）；
  3. follow-up 只重跑受影响的步骤（今天 follow-up 会重跑 5 步）；
  4. 手动逐阶段 Apply ACTIONS / PROCESS 丢失 BPMN `actionIds` 的既有缺口（已实证，见风险 5）：可选修法是 ACTIONS 写入后用本次 `stageIds` 补丁现有 BPMN、PROCESS 写入时用库里现有动作按旧绑定回填，另出 Plan。

## 【风险与回滚】

1. **CO 专家看到的 CURRENT DESIGN 是生成数据形状**（无 id）：`FunctionUnitContextDTO` 的六个核心切片与 `AiGeneratedData` 同名同构（MODIFY 模式就是靠这一点），差异只在序列化器多带的字段。单测锁定合并后每个切片的形状；UAT 首跑重点看表单步是否正确引用本轮表名。
2. **专家把「下游清空」理解成依赖缺失而拒绝出数据**：USER REQUEST 的系统行明说下游由平台随后生成；mock 与 UAT 各跑一次确认。若真实 CO 仍拒绝，再在 Instruction 里加一句一键生成说明（Instruction 改动需要用户在 CO 里操作）。
3. **worker 占用**：见第 6 节。
4. **回滚**：实现落在新类 + 分支上；回退只需 `AI_STUDIO_PROVIDER=GAIP`（全 AI Studio 回网关）。是否要一个只管一键生成的独立开关，见【待确认】。
5. **（既有缺口，已实证，非本次引入）手动逐阶段**：单独 Apply ACTIONS 不把动作写进 BPMN 的 `actionIds`；单独 Apply PROCESS 会把已有的动作绑定冲掉。运行时没有按名字兜底，任务上没有按钮（证据见【背景】事实 1）。方案 B 的一键生成只写一次 `ALL`，不受影响（对照组 50143 / 50144）；手动逐阶段的修复单列后续，不在本 Plan 修。

## 【验收】

- 反例（今天）：`provider=AUTOMATION` 下点 One-click generate → 日志 `provider=GAIP`，dev 无网关 key 时失败或 UAT 走 GAIP 额度。
- 正例：
  1. `provider=AUTOMATION`，新建空 FU，弹窗写需求 → 前端依次显示 1/5…5/5 → 结果卡 scope `ALL`、无 ERROR、自动 Applied；设计器里流程 / 表 / 表单 / 决策 / 动作齐全，**任务节点上动作按钮已绑定**（BPMN 含 `actionIds`），表单 stage 绑定正确；Requirements 文档多一版 `AI_ONE_CLICK`。
  2. 已有设计的 FU 再跑一次 → 整体替换，表名在需求不变时保持稳定。
  3. 第 3 步时点 Stop → 作业 CANCELLED，设计器内容与点击前完全一致，不再有第 4 步的 CO 调用。
  4. 让 mock 在 FORMS 步返回错误 → 作业 FAILED，信息含「第 3/5 步 Form Design」，库不变。
  5. 无工号用户 → 立即 `AI_STUDIO_NO_EMPLOYEE_ID`，不创建作业。
  6. `provider=GAIP` → 行为、日志与今天一致，`progress` 为空。
- FU：dev 新建测试 FU [待确认编号]；UAT 用真实 CO（agent setting「Function Unit」ACTIVE 行）跑正例 1、3。

## 【验证】（实现后最低命令）

- 单测（JDK17，见 memory「Build with JDK17」）：
  - `AiStudioOneClickOrchestratorTest`：顺序固定为 PROCESS→TABLES→FORMS→DECISIONS→ACTIONS；第 k 步的 CURRENT DESIGN 含本轮上游、含本步旧值、下游为空；每步 MODE=PROPOSE、PHASE/SCOPE 正确；每步按 `allowedSlices` 裁剪；可修违规只修一次；不可修错误立即失败并带步号；DECISIONS 无数据块视为空；其他步无数据块 → `AI_STUDIO_ONE_CLICK_STEP_NO_DATA`；中断后不发下一步；剩余时间不足时提前结束；进度回调按步触发；说明文字按步分节拼接。
  - `AiStudioChatServiceImplTest`：`prepareOneClick` 在 AUTOMATION 下带 `staffId` 与全部预计算数据、无工号 4xx；GAIP 下 `staffId=null`、消息与今天一致。
  - `AiStudioOneClickComponentImplTest`：AUTOMATION 走编排器、GAIP 走 `runProposal`；commit 钩子与落卡行为对两条路径一致；失败 / 取消时不调用 `applyProposal`。
  - `AiStudioProposalJobServiceImplTest`：进度 sink 更新快照与事件；取消后进度不再变化；已有三种 `submit` 行为不变。
  - 前端 vitest：`AiStudioEntryDialog.test.ts` 有 / 无 `progress` 两种渲染。
- `mvn -pl backend/developer-workstation -am clean package`（JDK17）；developer-workstation 前端 build + vitest。
- dev：mock-content-organizer（`deploy/environments/dev/docker-compose.dev.yml`，开 LLM）跑验收正例 1–6；UI 改动按 `/verify-ui` 截图。
- UAT：真实 CO 跑正例 1、3，记录每步耗时。

## 【待确认】（需要用户拍板）

1. ~~方案 A 还是 B~~ → **已定 B**（2026-11-03）。契约第 1 节「每步 Apply 后再发下一步」随实现改写。

> 2026-11-03：以下第 2–6 项**全部按推荐拍板**——上游用本轮 / 本步留旧值 / 下游清空；失败重试从第 1 步重来；超时共用 30 分钟；
> 回退只用 `AI_STUDIO_PROVIDER`，不加独立开关；UAT 把 `AI_STUDIO_PROPOSAL_WORKERS` 调到 4。
2. **CURRENT DESIGN 的组装规则**：推荐「上游用本轮结果 / 本步保留旧值 / 下游清空」；另一种是全部保留旧值（更像今天的 GAIP 一键生成，但下游旧对象可能误导专家）。
3. **失败时是否保留已完成步骤**：MVP 推荐全部丢弃、重试从第 1 步开始（续跑放后续）；还是 MVP 就要续跑？
4. **超时**：推荐保持 `proposal-max-runtime-minutes=30` 共用；还是给一键生成单独一个更长的上限（需要作业按类型带各自上限）？
5. **回退开关**：推荐只用现有 `AI_STUDIO_PROVIDER`；是否要一个只把一键生成切回 GAIP 的独立开关（例如 UAT 真实 CO 质量不稳时，顾问对话留在 CO、一键生成回网关）？
6. UAT 是否把 `AI_STUDIO_PROPOSAL_WORKERS` 调到 4。

---
请确认以上设计 Plan。确认后若要开始实现，请回复 **按 playbook 执行**（或补充/修正项）。

## 【实现记录】（2026-11-03）

- **设计缺陷修正（任务整理时发现）**：`AiResponseParser#parseText` 只在同一份数据里有流程时才做 stage 绑定校验与守门修复
  （`normalizeBpmn` → `AiBpmnSemanticGuard` R1–R4 + `validateStageBindings`），按步解析会让表单 / 动作步跳过它们。
  新增 `AiResponseParser#checkCrossSlices`，编排器在表单步、动作步并入后对累计结果再跑一次，可修违规经 `askAgent` 带原话重问该步一次；
  动作步的自动修复直接进提案，效果与网关单次解析整套设计一致。实测 R3/R4 只在已有 `actionDefinitions` 时生效，表单步校验不会凭空补动作。
- **落点**：`AiStudioOneClickOrchestrator`（新，service/impl，无 JPA）；`AiStudioChatService#prepareAgentOneClick` / `runAgentOneClick`；
  `askAgent` 由 Propose change 与一键生成共用；`AiStudioProposalJobService#submitWithProgress` + 快照 `progress`（只在内存）；
  `AiStudioOneClickComponentImpl` 按 draft 分流；前端 `AiStudioWorkspace.vue` 显示「第 k/5 步」。
- **未做**：队友侧步骤进度（见第 4 节）；dev / UAT 真实环境验证见交付说明。
- **dev 端到端（mock-content-organizer + DeepSeek，2026-11-03/04）**：
  - 正例 1：FU 50154 五步 66 秒，自动 Apply；3 个 userTask 的 `actionIds` 全部指向存活动作（50412–50416），表单 stage 绑定齐全，
    Requirements v1（`AI_ONE_CLICK`），线程卡已标记 Applied、说明按步分节。截图
    `frontend/developer-workstation/verification-screenshots/2026-11-03_ai-studio-one-click-co-progress.png`（「Step 3 of 5: Form Design」）。
  - 正例 2（Stop）：FU 50153 第 3 步时 Stop → 立即 CANCELLED、进度停在 3/5；进行中的第 3 步 CO 调用没被打断（跑完后结果被丢弃），
    之后没有任何 CO 请求，库不变。
  - 失败路径（真实发生）：模型在表步用了标记块外的 ```` ```json ```` 围栏 → `Step 2/5 (Table Design): Content Organizer returned no data: …`，库不变。
  - 既有约束撞上：`dw_table_definitions` 表名**全平台唯一**（`uk_dw_table_name`），首跑生成的 `leave_request` 与 FU 50143 冲突 →
    自动 Apply 失败、卡片带 WARNING（与网关路径同样会撞）；预览不查跨 FU 表名，模型也看不到别的 FU。
    **已修（2026-11-04，方案 B）**：`AiTableNameAvailability`（别的 FU 的 dw 表 + 任何 rt 表，大小写不敏感，本 FU 自己的不算）
    ① 预览 / dry-run / Apply 共用的 `AiStudioProposalReferenceValidator` 报 ERROR `AI_TABLE_NAME_TAKEN`，网关 Apply 同样；
    ② 生成期作为可修违规重问一次：一键生成在表这一步、Propose change 的 TABLES/ALL、网关 `doCallAi`（不看 scope：AI Generate 的 scope 是逗号多选，Apply 照写回传的表）；
    ③ `generation.txt` 加一条「tableName 全平台唯一、新表起本 FU 专属名」。dev 证据（FU 50153，占用方 FU 50143 在另一工作区）：
    修前 dry-run `issues=[]`、Apply HTTP 500 `uk_dw_table_name`；修后 dry-run ERROR `AI_TABLE_NAME_TAKEN`（大写 `LEAVE_REQUEST` 同样）、
    Apply HTTP 400 带可读说明、空闲名无问题；一键生成五步后自动 Apply 成功，表名 `oc_co_182247_leave_request`（提示词生效，未触发重问）；
    Copilot TABLES 提案明确要求 `leave_request` → 日志 `rejected by platform validation (AI_TABLE_NAME_TAKEN), asking once more` →
    第二次回答改用本 FU 专属名、预览无问题。残留风险：一键生成流程步排在表步前，若多实例节点已引用某子表名而表步因撞名改了它，
    BPMN 会对不上（dev 未遇到）。预览**不会**报出：没有任何校验比对 BPMN 的 `subTableName` 与表切片，
    写入时 `AiBpmnMiSubTableWriter` 只记 warn、`subTableId` 留空——这是流程先于表生成的既有缺口，撞名重问只是多了一个触发点。
  - 未在 dev 验证：无工号 / GAIP 分支（单测覆盖）；UAT 真实 CO。
- **后续候选（未做）**：「无数据块」也按可修违规重问一次；新的一键生成（非 follow-up）不带上一轮未应用卡片的 CONVERSATION。

