# 设计 Plan — Function Unit 调用 Function Unit（跨 FU 子流程）

> 状态：**待确认** ｜ 日期：2026-09-19 ｜ 分支：`common_0731_SELF_AP`

## 【背景 / 问题】

平台今天**没有任何跨 Function Unit 的流程调用能力**（已全仓验证）：

| 事实 | 证据 |
|---|---|
| `calledElement` 在第一方源码中零命中（仅存在于 `dist/` 里 vendored 的 bpmn-js BPMN 2.0 元模型） | 全仓 grep |
| `callActivity` 仅作为被动字符串出现在 13 处（校验正则、图标映射、日志文案） | `ProcessBpmnValidator.java:43,51`、`NodePropertiesPanel.vue:324,357`、`BpmnProcessSimulator.java:434` |
| 现有"子流程"全部是 **embedded MI subProcess**（同一 process instance 内按子表行展开），不是流程组合 | `docs/specs/multi-instance-task-dispatch/` |
| 引擎有父子实例查询能力但是**死代码**（无 controller 暴露） | `ProcessEventManager.java:153` |
| `up_process_instance` 无父子列；Portal 前后端 `superProcessInstanceId` 零命中 | `03-user-portal-schema.sql:180-204` |

用户需求三条：
1. 主 FU 能调用子 FU，子 FU 可从主 FU 连接进去
2. DW 内显示 FU **调用层级关系图**（当前 FU 高亮、其它 FU 可点击跳转）
3. Portal 主流程能看到子流程的数据

## 【目标】

- 主 FU 通过 BPMN `callActivity` 调用子 FU，子 FU 起**独立 process instance**
- 支持两种调用基数：**调一次** 与 **按子表行调多次**
- 父子**强绑定**：任一侧终止，另一侧跟随（用户明确决定）
- DW 新增「调用层级」视图：当前 FU 高亮、可点击导航、双向边（谁调我 / 我调谁）
- Portal 主流程详情页**只读**展示子流程数据（直接用子 FU 的表单渲染）+ 子流程进度
- 产出两个可运行 demo

## 【非目标】（本阶段不做）

- ❌ 异步调用（不等待子流程结束就往下走）——只做同步等待
- ❌ 子流程数据**回写**主 FU 表字段（只读展示，不做字段映射回写）
- ❌ 子 FU 表单在主流程里**可编辑**
- ❌ 老的"已完成/驳回"实例状态**数据迁移**（读取路径兼容，不改历史数据）
- ❌ 边界事件 / 补偿 / 异常出口连线（异常路径按强绑定规则处理，不引入新 BPMN 元素）
- ❌ 重构现有 MI 机制（仅做隔离，见下）

## 【模块】

`dw`（前端+后端）｜ `engine` ｜ `portal`（前端+后端）｜ `deploy`

---

## 【方案】

### 方案 A：原生 BPMN CallActivity（**推荐，已采纳**）

`calledElement = 子 FU 的 code`，子 FU 起独立 process instance，父子关系走 Flowable 原生 `superProcessInstanceId`。

### 方案 B：平台层 serviceTask + 自建父子表

不用原生 callActivity，用 `${functionUnitCallDelegate}` 显式 `startProcess` 并登记自建表。

**拒绝理由**：等待语义（子结束唤醒父、撤回、超时、重试）要全部自己实现；多实例要**再写一套**，而 MI 正是本仓库 bug 最密集的领域；`getSubProcesses()` 等现成能力用不上。

### 方案 C：只做数据视图，不做真调用

**拒绝理由**：不满足需求 1（无法真正调用/发起子流程）。

### 推荐：**方案 A**

1. 两种调用基数中，A 是唯一**不用重造 MI 轮子**的（callActivity 原生支持 `multiInstanceLoopCharacteristics`）
2. "直接用子 FU 表单渲染" + "跨实例实时读" 都**要求子 FU 有真实独立实例**
3. 引擎侧父子查询代码**已写好**，只需接出
4. "独立发起 / 只能被调用"是**同一份流程定义的两种入口**，行为不会分叉

---

## 【关键设计决策】

### D1. 调用引用存 **code**，不存 id

`calledElement = <子 FU 的 code>`。依据：`BpmnProcessIdRewriter` 已强制 `process id ≡ FU code`（`BpmnProcessIdRewriter.java:45-76`），故 `calledElement` 天然是 Flowable 可解析的 key；且 code 跨环境稳定，id 会被 import 重映射。

> 历史教训：`assignee-code-id-mismatch-orphan` —— BPMN 存 id 导致 import/部署后分派全成孤儿。

### D2. 子表单引用以 **name** 为锚点

`childFormName` 为准，`childFormId` 仅作快速路径。理由同 D1：id 在 import 时被重映射，子 FU 独立导入后表单 id 会变。

### D3. FU 调用模式：`startup_mode`

`dw_function_units` 新增列，取值 `STANDALONE` / `CALLABLE` / `BOTH`，**缺省 `STANDALONE`**（存量 FU 行为完全不变）。

用途：① 设计期校验（`STANDALONE` 被 call → 报错）② call 节点的 FU 选择器过滤 ③ Portal 发起入口控制。

> 不靠"有没有角色授权"来**推断**是否可被调用 —— 那是间接信号猜业务语义，违反 `config-over-heuristics`。

### D4. 父子实例关系：Flowable 原生 + `up_process_instance` 加列

**不建独立关联表**（避免第二份父子关系真相与 Flowable 自身记录不一致）。
在已有 `up_process_instance` 上加 `parent_process_instance_id` + `call_activity_id` + 索引，用于 Portal 侧**查询优化**（避免每次穿透到引擎）。真相源仍是 Flowable。

### D5. 数据路径：跨实例**实时读**，不回写快照

子 FU 数据在它自己的 `up_process_instance.variables`（独立 JSONB blob）。主 FU 详情页按父子关系实时读子实例。

> 理由：单一真相源、永不过期。本仓库在 `__subTables__` 副本分叉上吃过大量亏（见 `subtables-canonical-key-refactor`）。

### D6. 权限：能看主请求 → 能看子摘要（只读）

主 FU 参与人在主详情页可**只读**看到子流程数据与进度，判据是"你能看这个主 FU 请求"，**不要求子 FU 角色**。
但**不**给进子 FU 目录、**不**给打开子 FU 独立详情页、**不**给办理子 FU 任务 —— 那些仍走子 FU 自己的 `canAccessFunctionUnit`。

### D7. 父子强绑定（用户明确决定）

| 触发 | 方向 | 结果 |
|---|---|---|
| 子流程被**拒绝/驳回** | 子 → 父 | 父流程也失败（`REJECTED`） |
| 子流程被**撤回/取消** | 子 → 父 | 父流程也撤回/取消 |
| 父流程被**撤回** | 父 → 子 | 子流程级联终止，显示撤回 |

⚠️ **子→父 是"逆流"的**：Flowable 原生只保证「子正常结束 → 父继续」。子**非正常终止**时父会**卡在 call 节点**，必须由平台代码显式反查 `superProcessInstanceId` 去终止父流程。

⚠️ **必须加级联防重入标记**（父终止又级联子，可能循环）。

> "撤回"在本仓库 = **硬删除 Flowable 实例**（`runtimeService.deleteProcessInstance`），不是退回起点。"退回第一步"是另一个独立功能（`DRAFT` / `changeActivityState`）。

### D8. ⚠️ 前置改造：REJECTED 终态（**范围扩张，用户已拍板"一并修，全局生效"**）

**现状缺陷**：`up_process_instance.status` **从未写过 `REJECTED`**。全仓只有 `RUNNING` / `COMPLETED` / `WITHDRAWN`。
驳回与通过走同一条路（都是 `completeTask`，只是 `decision=no`），流程正常结束写 `COMPLETED`（`TaskApprovalCompletionComponent.java:317,364`）。
"被拒绝"目前靠**前端字符串匹配结束节点名字**猜出来（`statusMatcher.ts:7` 的 `['rejected','拒绝','驳回']`）。

后果：① Portal 的 `REJECTED` 页签是**死的**（`applications/index.vue:292`）② `markProcessAsCompleted` 收到 `lastActivityName` 却**直接丢弃**恒写 COMPLETED（`ProcessComponent.java:874-907`，已实读确认）③ 结束事件改名就识别不出

**改法**：读流程变量 **`approvalStatus`**（`TaskApprovalCompletionComponent.java:120-124` 已在写），**不猜节点名**。

### D9. 与现有 MI 的隔离（用户要求"新旧共存，互不影响"）

**引擎侧 MI 判定天然隔离，零改动。** 依据实读源码：

`BpmnActionParser.getMultiInstanceSubProcessSubTableName`（`:124`）是 MI 总闸门（`MultiInstanceTaskWriter.java:68-72` 拿不到即 return），要求三条**同时**成立：
1. 在**本流程定义 XML** 里按 id 找到该 userTask（`:143`）
2. 该 userTask 有**祖先 `subProcess`**（`findAncestorSubProcess:488-497`，严格匹配 `localName == "subProcess"`）
3. 该 subProcess 内含 `multiInstanceLoopCharacteristics`（`:148`）

子 FU 的任务**三条全不满足**（属于另一个流程定义、祖先是 `<process>` 根）。callActivity 也不是 `subProcess`。
→ **子 FU 任务永不会被误判成 MI 子任务**，`wf_extended_task_info` 的 MI 字段不会被写。

| # | 位置 | 是否要改 |
|---|---|---|
| ① 引擎 MI 判定链 | **零改动**（三重条件天然排除） |
| ② `MiCollectionVariableBuilder.java:247` BFS | **要改（收紧）** —— 唯一真正的串扰点 |
| ③ 两个 Portal 解析器 + `ProcessDiagram.vue:88` | **只加不改**（现状严格按 `localName === 'subProcess'` 判定，callActivity 现在是"不可见"而非"误判"） |
| ④ `ProcessBpmnValidator.validateMultiInstance` | **零改动**（正则只匹配带 MI 的 subProcess；callActivity 走新增的 `validateCallActivity`） |

**关于 ②**：`findElementByBpmnId`（`BpmnMiXmlSupport.java:29-41`）按 id 匹配**任意元素类型**，callActivity 会被当无名节点**穿透**，BFS 继续走到后面的 MI subProcess。改为**遇 callActivity 即停**。
现存 BPMN 无任何 callActivity（已全仓验证）→ **该改动对存量 FU 是恒等变换**，只在"主 FU 同时有 call 节点和 MI 子流程"的新场景下生效。不改反而会串扰。

### D10. 调用层级视图（需求 2）

**这是独立的跨 FU 导航视图，不是 BPMN 画布的装饰**：

| | BPMN 流程图 | FU 调用层级图 |
|---|---|---|
| 节点 | 流程内任务/网关 | **Function Unit** |
| 边 | 顺序流 | **调用关系** |
| 范围 | 单个 FU 内部 | **跨多个 FU** |

- **数据来源**：扫描 `dw_process_definitions.bpmn_xml`（**设计态**，base64 需先解码）提取 `calledElement`。由**后端新接口**派生，不存表（对齐 `TableRelationController` 的 derive 模式），**禁止**前端拉全部 BPMN 自己解析（性能）
- **双向边**：显示"谁调我" + "我调谁"。只显示单向的话当前 FU 永远是根，看不出层级位置
- **深度：一次铺开整棵树**（用户决定）。不做按层展开/折叠。从当前 FU 出发，**双向**递归遍历至叶子，一次性渲染完整连通子图
  - 递归须带 `visited` 集合防环（即使设计期已拒绝环，存量/未部署数据仍可能成环，**渲染端不得因此死循环**）
  - FU 数量大时靠 VueFlow 的 pan/zoom + `fitView` 承载，不做分页
- **环**：设计期**拒绝**循环调用（运行时 A→B→A 会无限递归起实例）。布局算法本身环安全（`computeRanks:62` 有迭代上限）
- **无权访问的 FU**：FU 列表后端按 dev-group 过滤，树里可能出现用户无权访问的 FU → 显示为灰色不可点击节点，**不得崩溃或静默跳过**

**基建复用**（已验证）：

| 资产 | 结论 |
|---|---|
| `frontend/shared/src/relationDiagramLayout.ts:141` `layoutRelationDiagram` | ✅ **直接用** —— 完全通用（只吃 `{id,height}` 和 `{source,target}`），零 ER 耦合 |
| VueFlow `^1.48.2` | ✅ DW 已装（`package.json:29-31`），无需加依赖 |
| `RelationDiagramEditor.vue` | ✅ **当模板抄**，不继承。重点抄拖拽-vs-点击消歧（`:525-547`）—— 正是"可拖又可点击跳转"的已解决问题 |
| Tab 插入点 `FunctionUnitEdit.vue:103-207` | ✅ 加 `call-hierarchy` pane，遵循现有 `v-if="activeTab===..."` 懒挂载约定 |

⚠️ **两个已知坑**：
1. 布局算法 x 轴是**镜像的**（`:189-192`，让被引用最多的排最左）。调用层级要 caller→callee 从左到右，**方向相反**，需反转
2. **FU→FU 跳转是本仓库第一次出现**。`FunctionUnitEdit.vue` 仅 `onMounted` 取数（`:619-621`），同路由换参数 Vue Router **不重新挂载** → 点节点跳转后页面数据不变。必须加 `watch` 或路由 `:key`
3. `RelationDiagramEditor.vue:229` 的 `useVueFlow()` **无实例 id**，新组件须传显式 id（如 `useVueFlow('fu-call-hierarchy')`）避免共享状态

---

## 【影响面】

| 层级 | 变更 |
|------|------|
| **Entity / SQL** | 新增 init-script（从 `83-` 起）：`dw_function_units.startup_mode`；`up_process_instance.parent_process_instance_id` + `call_activity_id` + 索引。`status` 列**无 CHECK 约束**，加 `REJECTED` 值无需迁移 |
| **DW 前端** | 新 `CallActivityProperties.vue`；`NodePropertiesPanel.vue:223-261` 加 `isCallActivityElement` 分支；新 `CallHierarchyView.vue` + tab；FU 属性加 startup mode；`api/functionUnit.ts` 两个 interface 补 `code` 字段（后端 DTO `FunctionUnitResponse.java:22` **已有** code，仅前端类型缺声明）；`FunctionUnitEdit.vue` 路由参数 watch 修复 |
| **DW 后端** | 新 `validateCallActivity`（**独立于** `validateMultiInstance`，含环检测）；新 `/function-units/{id}/call-hierarchy` 派生接口；Exporter/Importer/Cloner/VersionComponentImpl 带 `startup_mode` |
| **引擎** | 部署期 `resolveCalledElement`（模仿 `ProcessDeploymentManager.resolveApFlowRef:496`，**解析不到 fail deploy**）；新跨实例级联终止器；`getSubProcesses()` 接出 controller |
| **Portal 后端** | 父子链路**批量**查询；REJECTED 终态（`markProcessAsCompleted` 等 3 处）；级联上溯；子 FU content 加载；`WithdrawnProcessIds.java:25`（硬编码只认 `WITHDRAWN`，**必须扩展为终态集合**，否则被级联终止实例的任务在待办里仍可操作） |
| **Portal 前端** | 两个 BPMN 解析器加 callActivity **独立分支**；`ProcessDiagram.vue:88` type union 加成员；子流程数据只读区块；状态徽章/图表着色 |
| **MI 隔离** | `MiCollectionVariableBuilder.java:247` BFS 遇 callActivity 即停（**唯一**落在 MI 路径的改动） |
| **i18n** | DW 三语（`en.ts` / `zh-CN.ts` / `zh-TW.ts`）：`functionUnit.callHierarchy` + `callHierarchy.*`；Portal 三语状态标签（`application-status-labels.json` **已有** `REJECTED` 键） |
| **deploy** | 两个 demo 目录 + **两个 runner 注册**（`00-init-all.sh` 与 `init-database.ps1`，不注册则静默不执行） |

---

## 【数据与契约】

- **只增不改**：所有新列可空/带缺省，存量行为恒等
- `startup_mode` 缺省 `STANDALONE` → 存量 FU 不可被调用，须显式开启
- 存量实例 `parent_process_instance_id` 为 NULL
- **存量"驳回"实例不迁移**：状态仍是 `COMPLETED`，靠 `statusMatcher.ts` 关键词回退显示；新实例走真状态
- **可移植性**（`function-unit-portability-consumers` 规则）：新增跨 FU 引用是一条**新依赖边**，须贯穿 DW Exporter/Importer/Cloner/Rollback + Admin 两条 HTTP 导入路径
  - ⚠️ **Clone 语义**：克隆主 FU 时 `calledElement` 指向的子 FU **不跟着克隆**，副本仍调用原子 FU。这是**正确**的（子 FU 是独立实体），但须在 UI 明示
  - ⚠️ 历史坑：`FormTableBinding.bindingLinkMode` 的 `@Builder.Default` 曾导致"克隆 6 个 MI binding 产出 0 个"。新字段避开同样模式

---

## 【分期】

### P1 — 调用基础 + 级联（**级联不可拆到 P2**）

call 节点配置面板、`calledElement` 解析与部署校验、`startup_mode`、父子关系列、**终止级联（父↔子双向）**、MI BFS 隔离。

> ⚠️ **级联必须在 P1**：子流程非正常终止时 Flowable **不会**让父流程失败，父会**永久卡在 call 节点**。P1 不含级联就是会卡死的半成品。

### P2 — REJECTED 终态 + 子拒绝级联

全局 REJECTED 终态改造（D8）、`WithdrawnProcessIds` 扩展、Portal 状态显示、"子拒绝 → 父失败"。

### P3 — Portal 子流程数据展示

跨实例实时读、子 FU 表单只读渲染、子流程进度、两个 BPMN 解析器 + diagram type。

### P4 — DW 调用层级视图

派生接口、`CallHierarchyView.vue`、环检测、导航修复。

### P5 — 按行调多次（call + 多实例）

独立的 `CallActivityCollectionBuilder`（**不复用** MI 的 `MiCollectionVariableBuilder`——语义不同：MI 集合项是"给每行分派处理人"，call 集合项是"启动子 FU 的输入数据"）。

### P6 — 两个 demo

`21-`（调一次）+ `22-`（按行调多次）。

---

## 【风险与回滚】

| 风险 | 缓解 |
|---|---|
| **级联循环**（父终止→级联子→子终止→级联父） | 级联防重入标记；不照抄 `MultiInstanceCanceller` 的吞异常（`:109-117`）——父该失败却静默没失败是正确性 bug |
| **权限绕过**（子办理人间接终止无权访问的主流程） | `withdrawProcess:251` 要求调用者是 `startUserId`，级联必然不满足。**只允许系统内部级联路径绕过**（带内部标记），**不得**简单放宽守卫暴露到用户 API |
| **REJECTED 改造爆炸半径**（影响所有 FU 的驳回显示） | P2 独立分期；存量不迁移；关键词回退保留；需回归验证状态徽章/图表着色/列表筛选 |
| **跨实例读 N+1** | 子实例**一次按 `superProcessInstanceId` 批量拿**，不逐个；复用 FU content 5min TTL 缓存。历史事故：`portal-perf-fu-resolve-negative-cache`、`todo-endpoint-n1-fix` |
| **部署顺序依赖** | `calledElement` 在**部署时**对**已部署**流程定义解析 → **子 FU 必须先于主 FU 部署**。写入 demo README 且作为验收步骤 |
| 回滚 | 各期独立可回滚；`startup_mode` 缺省值保证存量零影响 |

---

## 【验收】

### 需求 1：FU 调用 FU
- **反例（当前）**：DW 画布放一个 callActivity → 属性面板只有 ID/Name 两个字段；部署后无任何调用行为
- **正例**：call 节点可选子 FU（仅列 `CALLABLE`/`BOTH`）；发起主流程走到该节点 → 子 FU 起独立实例 → 子流程办完 → 主流程继续
- **反例（校验）**：`calledElement` 指向不存在/`STANDALONE` 的 FU → **部署失败并报明确错误**，不静默放行
- **反例（环）**：A 调 B、B 调 A → 设计期校验拒绝

### 需求 2：调用层级视图
- **正例**：DW 打开 FU A → 「调用层级」tab → 图中 A **高亮**，显示 A→B、C→A 双向边；点击 B **跳转到 B 的编辑页且数据真的刷新**（非仅 URL 变）
- **反例**：树中含无权访问的 FU → 灰色不可点击，**不崩溃**

### 需求 3：Portal 子流程数据
- **正例**：主流程发起人在 My Request 详情页看到子流程区块：子 FU 表单**只读**渲染的数据 + 子流程当前进度
- **反例（权限）**：该用户**无**子 FU 角色 → 仍能在主详情页看到只读数据；但直接访问子 FU 目录/详情页**被拒**

### 强绑定
- 子驳回 → 父状态变 `REJECTED`（**查库确认**，不看 HTTP 200）
- 父撤回 → 子实例终止且显示撤回
- 子撤回 → 父撤回

### MI 共存（**用户明确要求**）
- **反例**：主 FU 同时有 call 节点与 MI 子流程 → MI 分派**照常工作**，call 不干扰
- 现有 MI demo（FU 50005）**行为零变化**
- **跨参与者验证**：用第二个参与者保存一次，**查库确认第一个参与者字段没被覆盖**

### taskId / FU
- 现有 MI 回归基线 FU：**50005**（`fu-20260422-23tfag`）
- 新 demo FU：`[待确认 — 实现时分配]`

---

## 【验证】（实现后最低命令）

```bash
# 后端（注意：backend/ 根 pom 无 modules，-pl 会失败，须进模块目录）
cd backend/developer-workstation && mvn package -DskipTests
cd backend/workflow-engine-core && mvn test
cd backend/user-portal && mvn test
# ⚠️ 后台任务通知的 exit code 反映管道最后一环，验证 mvn 结果必须 Read output 看 BUILD SUCCESS/Tests run

# 前端
cd frontend/developer-workstation && pnpm build && pnpm test
cd frontend/user-portal && pnpm test && pnpm run regression:mi   # MI 回归必跑

# Docker（改后端必须重建镜像；Dockerfile 只 COPY target 里的 jar，mvn test 不打包）
docker compose build user-portal workflow-engine developer-workstation
docker compose restart user-portal   # FU content 有 5min TTL 缓存

# 截图验证（UI 改动必须，见 skill verify-ui-fix-with-screenshot）
# - DW 调用层级视图
# - Portal 主流程详情页子流程区块
# - 强绑定三种终止场景的状态显示
```

**验证纪律**（来自仓库既往事故）：
- 查库确认业务真推进，**不认 HTTP 200**
- MI 相关改动：单测全绿 ≠ 无回归，必须跑全量 + 真实 UI
- CSS/布局终审证据必须是运行时 computed style + 截图

---

## 【Demo 设计】（已定）

**目标**：验证「FU 调用单个子 FU」与「FU 调用多个子 FU 实例」两条链路可跑通。业务外壳刻意保持最简，不掩盖机制本身。

### `21-fu-call-single/` — 调一次

```
Purchase Request (主 FU, code: fu-call-demo-purchase)
  ①Start → ②Submit Request (userTask) → ③[callActivity] Vendor Check → ④Approve (userTask) → ⑤End
                                              ↓ calledElement
Vendor Check (子 FU, code: fu-call-demo-vendor, startup_mode=CALLABLE)
  ①Start → ②Review Vendor (userTask，填 result + comment) → ③End
```

覆盖：调用配置、部署期解析、子 FU 独立实例、主详情页只读渲染子表单、子流程进度、强绑定三场景。

### `22-fu-call-multi/` — 按行调多次

```
Onboarding Request (主 FU, code: fu-call-demo-onboarding)
  ①Start → ②Fill Checklist (userTask，子表 checklist_items 多行)
         → ③[callActivity + multiInstance] Item Review  ← 每行起一个独立子实例
         → ④Confirm (userTask) → ⑤End
                    ↓ calledElement
Item Review (子 FU, code: fu-call-demo-item-review, startup_mode=CALLABLE)
  ①Start → ②Review Item (userTask) → ③End
```

覆盖：`CallActivityCollectionBuilder`、N 个子实例并行、主详情页按 `callActivityId` 分组展示 N 份子数据。
⚠️ **同时验证与 MI 共存**：22- 的主 FU 会**额外**放一个 MI subProcess 节点，确认 call 多实例与 MI 多实例互不干扰（对应验收项「MI 共存」）。

**约束**（来自仓库规则）：BPMN 存 base64；不建物理表；内容全英文；用序列分配 id；两个 runner 都要注册；**子 FU 必须先于主 FU 部署**。

## 【已定：`startup_mode` UI 位置】

**P1 只做 FU 属性页**（编辑既有 FU 时可改——这是让存量 FU 变成可被调用的必经入口）。
新建 FU 向导**不加**该字段：新建时缺省 `STANDALONE`，需要被调用时去属性页改。

> 理由：向导每多一个字段就多一分新建阻力，而"这个 FU 要不要被调用"通常是**设计到一半才确定**的事，不是新建时就能回答的。后续若实测有需求再补进向导。

## 【待确认】

1. 新 demo FU 的 id 范围（实现时分配，避开 17- 的 50000–50700、18- 的 48/112–114/6736–6855）

---

请确认以上设计 Plan。确认后若要开始实现，请回复 **按 playbook 执行**（或补充/修正项）。
