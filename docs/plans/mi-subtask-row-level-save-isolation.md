# MI 子任务 Save 行级隔离设计 Plan

状态：已确认设计，待实施
关联：`.claude/skills/feature-design-plan` 产出

## 背景 / 问题

MI（多实例）子任务编辑 Participants 面板字段并 Save，编辑有时被撤销、有时污染其它参与人的行。

根因是存储/提交架构层面：`up_process_instance.variables`（JSONB）每个流程实例只有一份，MI 场景下所有参与人的行共享同一个数组；`TaskFormComponent.submitTaskForm` 用 `Map.putAll` 整体替换 `__subTables__`，而前端提交的 payload 本身对"其它参与人"就是残缺视角（hydrate 阶段只完整拿到自己那一行，其它参与人行被削薄成 `{id_idw, task_status, task_current_node}`）。两者叠加，导致每次 MI 子任务 Save 都可能用局部残缺视角覆盖掉其它参与人已经保存好的完整数据。

### 已确认的现状（有具体代码位置佐证）

1. **JSONB 结构**：`up_process_instance.variables`（JSONB）是每个流程实例一个，不是每行一个。`variables.__subTables__[bindingId]` 是一个数组，MI 场景下装的是所有参与人共享的行。DB 层面没有任何"这一行归属哪个子任务"的物理边界。`ProcessInstance` 实体上有 `@Version lockVersion`（乐观锁），但只保护"整个 variables 有没有被别人改过"，锁不住"数组里某一行有没有被别人改过"。

2. **后端提交路径**：`backend/user-portal/src/main/java/com/portal/component/TaskFormComponent.java` 的 `submitTaskForm` 方法（约412-536行）——读整个 `ProcessInstance.variables` → 拷到 `currentVariables` → `updatedVariables.putAll(inbound)`（约474行，浅层 `Map.putAll`，`inbound["__subTables__"]` 整体替换掉旧的 `__subTables__`）→ `processInstance.setVariables(updatedVariables)` → `processInstanceRepository.save(processInstance)`。典型的整列读-改-写，没有任何针对 `__subTables__` 数组内某一行的定向更新。

3. **前端提交的 payload 本身就已经是"局部视角"**：MI 子任务自己的 Task Detail 页面，hydrate 阶段就只会完整拿到自己那一行，其它参与人的行被裁剪成只剩身份字段。这份局部视角被整体塞进 `formData.__subTables__` 传给后端。

4. **已有的持久化"我归属哪一行"锚点**：前端 `_currentItem`/`currentItem`（BPMN 循环变量）已经在 `buildCurrentTaskFormSubmitPayload` 的 `...formData.value` 展开中原样透传进提交 payload（`useTaskForm.ts:82-86`, `180-190`）——不需要新的跨服务调用或前端改动即可在后端拿到这个锚点。`SubTableRowKeySupport.rowKeyFromCurrentItem`（`platform-common`，纯静态方法）可直接复用来解析出行 PK。

5. **user-portal 没有直接数据库/HTTP 访问路径拿到 `wf_extended_task_info`（`ExtendedTaskInfo`）**——该实体和其 `writeBackSubTableRow`（真正的按 PK+row_version 乐观锁 UPDATE）只在 `workflow-engine-core` 模块，且只在 MI 子任务**完成时**触发，对没有物理表的 FU 完全不生效。`WorkflowEngineClient` 也没有暴露"给 taskId 查询归属行"的接口。

6. **已确认排除**：不给每个 FU 的 MI SUB 表建物理表（用户明确否决）。

### 用户明确的设计目标（原话）

> 多实例子任务，就是按照子表主键拆分task，然后不同的人去处理各自的子任务，修改数据或者别的，子任务的数据互相不受影响

即：MI 子任务本质是"按子表主键拆分出来的独立任务"，每个任务处理人对自己那一行的编辑，必须与其它子任务/参与人的行完全隔离，互不影响、互不覆盖。

## 目标

MI 子任务的 Save 只影响、只落盘**当前任务归属的那一行**；其它参与人的行永远原样保留数据库现状（不被前端提交的残缺视角覆盖）。普通（非 MI）流程的子表提交行为保持不变。行级合并失败时必须明确报错，绝不静默降级导致数据被悄悄污染。

## 非目标（本阶段不做）

- 不给任何 FU 的 MI SUB 表建物理表（已排除）。
- 不改动别名 key（数字 bindingId / tableName 字符串 / normalizeSubTableName）合并逻辑之外的前端 hydrate 路径——不动 `mergeAllSlicesForSharedProcessSubTableBinding`、`useTaskDetailMiIsolation.ts` 等 hydrate/展示逻辑，本次只收口在提交/持久化这一侧。
- 不引入新的跨服务调用或前端新增字段——现有 payload 里的 `_currentItem`/`currentItem` 已足够，前端零改动。
- 别名 key 只维护一份权威数据、其它别名读取时动态派生——这个更彻底的重构列为后续待办，本次不做。

## 模块

portal（user-portal 后端，`TaskFormComponent.submitTaskForm` 及协作类；前端不改动）

## 方案

### 方案 A（推荐，已确认）

`submitTaskForm` 增加 MI 感知的行级合并：

1. **MI 判定**：检查 `editableData`（字段权限过滤后）对应的 `formData` 中是否存在 `_currentItem`/`currentItem`，作为"当前任务是 MI 子任务"的判定信号。此信号依赖前端 `buildCurrentTaskFormSubmitPayload` 的 `...formData.value` 隐式透传（不是显式契约），**需在代码注释中记录这条技术债**。

2. **判定为 MI 子任务时**，对 `editableData["__subTables__"]` 里符合 MI 参与人集合特征的 binding（后端复刻前端 `isMiDashboardSubTableBinding` 同款判定：状态列/assignee 列/tableName 含 participants 等）：
   - 用 `SubTableRowKeySupport.rowKeyFromCurrentItem` 解析 `_currentItem` 得到当前任务归属行的 PK。
   - **PK 解析失败**（`_currentItem` 缺失、或按设计 PK 列解析不出值）→ **立即抛出明确异常**，本次 Save 整体失败，不做任何合并、不落库；前端沿现有异常处理链路提示用户。**绝不静默退回整体 `putAll`、绝不静默丢弃编辑。**
   - PK 解析成功 → 以**数据库当前 `ProcessInstance.variables.__subTables__[key]`**（该 binding 涉及的每个 alias key，事务内重新读取）为基线；在基线数组中按 PK 定位当前行的下标：
     - 找到 → 只用前端提交数组里同 PK 那一行的字段级数据合并覆盖到该下标；数组里其它任何 PK 的行，**无论前端 payload 是否包含、内容是否完整，一律不采纳，只保留数据库现有值**。
     - 基线中找不到该 PK（新参与人场景）→ append 该行（仅在 PK 解析成功、但确实是新行时才走此分支，不与"解析失败"混淆）。
   - 该 binding 涉及的**所有别名 key**（数字 bindingId、tableName 字符串、`normalizeSubTableName` 等）都执行同一套"按行合并"逻辑并写回同一份合并结果，不是对每个别名重新做整体替换。

3. **判定为非 MI 子任务时**：完全保持现状 `putAll` 整体替换行为，零回归。

4. **执行顺序**：行级合并逻辑只作用于 `filterSubTableFieldsInPlace`（现有字段权限过滤）**之后**的 `editableData`，不改变现有过滤顺序，不影响字段权限语义。

### 方案 B（不采纳）

前端显式在提交时把当前行 rowKey 作为顶层新字段传给后端，后端只信任这个显式字段，不依赖 `_currentItem` 的隐式透传。技术上契约更清晰，但需要改前端 `buildCurrentTaskFormSubmitPayload`/`saveCurrentTaskFormWithMiPersist`，扩大改动和回归范围。

### 推荐理由

方案 A：复用已在 payload 里的 `_currentItem`，后端单点收口，前端零改动，回归面最小。技术债（隐式耦合）已记录，不影响功能正确性，可留作后续独立加固。

## 影响面

| 层级 | 变更 |
|------|------|
| Component | `TaskFormComponent.submitTaskForm`（约412-536行）：`__subTables__` 处理从整体 `putAll` 改为 MI 感知的行级合并；新增协作方法（如 `mergeMiParticipantRowIntoBaseline`），遵循本文件既有的"同包协作类 + `@Lazy @Autowired` 字段注入"模式 |
| Component（新判定） | 新增 MI 判定 + PK 解析逻辑，复用 `platform-common` 的 `SubTableRowKeySupport.rowKeyFromCurrentItem` |
| Exception | 复用现有异常类型（如 `PortalException`）表达"PK 解析失败"，走现有 controller 层异常处理链路，不新增异常类型 |
| Entity / SQL | 无新增字段，无 schema 变更 |
| 前端 types / views | 无变更 |
| i18n | 若异常消息需要多语言展示，检查是否复用现有 i18n key；不新增前端逻辑 |
| 部署 / 配置 | 无 |

## 数据与契约

- 无新增/变更字段。契约变化：MI 子任务提交时，服务端不再信任 payload 里除"当前行"外的其它参与人行数据；PK 解析失败时行为从"静默保存（可能损坏数据）"变为"显式报错"，这是一个刻意的行为收紧，需要在验收里明确覆盖。
- 兼容策略：非 MI 判定信号缺失时，完全退回现状 `putAll` 行为，非 MI 场景零回归。

## 并发处理

现状 `ProcessInstance` 已有 `@Version lockVersion`（JPA 乐观锁），保护整个 variables 是否被并发修改。行级合并发生在现有写事务内（`taskFormWriteTx().executeWithoutResult`），基于事务内重新读取的最新 `currentVariables` 定位行、合并。由于合并单位收窄为"一行"而非"整个数组"，两个子任务先后提交时，后一次提交基于的基线已经包含前一次提交的结果，各自按行合并、互不覆盖。不需要额外的应用层重试或数据库行锁，现有事务边界和乐观锁机制已足够覆盖此并发场景。

## 分期

- **MVP**：本 Plan 描述的 MI 感知行级合并，覆盖 MI 参与人集合类 binding 及其所有别名 key；PK 解析失败显式报错。
- **后续**：别名 key 收敛为"只维护一份权威数据（数字 bindingId），其它别名读取时动态派生"——涉及前端 hydrate 层多处读取路径，爆炸半径大，留作独立后续 Plan。

## 风险与回滚

- **风险 1**：MI 判定信号（`_currentItem` 存在性）对遗留/边缘 FU 可能不可靠——误判"非 MI"时零劣化（退回现状）；误判"MI"时会触发 PK 解析，解析失败则显式报错（用户会感知到，而非之前的"看似成功实则数据受损"）。这是本方案刻意接受的行为：宁可让极少数边缘场景报错，也不允许任何场景静默丢数据。
- **风险 2**：行为收紧后，某些此前"凑巧能存但已经在丢数据"的边缘 FU，可能从"看似正常"变成"报错"——这是预期之内、且被认为是正确方向的变化，需要在发布沟通里说明。
- **回滚**：新逻辑集中在一个新增方法调用点，出问题可直接改回调用旧的整体 `putAll` 路径，改动集中、易回退。

## 验收

- 反例 1（当前 bug）：MI 子任务 A（参与人 Test-000014）Save 编辑 Name；随后子任务 B（参与人 Test-000015）Save 编辑 Name → 刷新任一任务，会看到对方编辑丢失或字段被削减。
- 正例 1（修复后）：同样操作序列 → 刷新两个任务，两边 Name 编辑都完整保留，互不影响。
- 正例 2（新增，报错场景）：模拟 `_currentItem` 缺失/PK 解析失败的 MI 子任务提交 → Save 请求应返回明确错误，前端提示失败，`up_process_instance.variables` 不应有任何变化（不落库）。
- FU：`fu-20260422-23tfag`（Multi-Instance Subtask Demo），需要一个全新发起的流程实例（避免复用已被之前测试轮污染的 Meeting 数据）。

## 验证（实现后最低命令）

- `cd backend/user-portal && mvn -o test -Dspring-security.version=6.3.4`（含现有 `TaskFormComponentSubTableFieldPermissionTest`，确认不回归）
- 新增单测：
  - 两个 MI 参与人前后 Save，断言两行数据互不覆盖
  - 非 MI 任务 Save，断言行为与现状 `putAll` 完全一致
  - `_currentItem` 缺失/PK 解析失败场景，断言抛出异常且 `variables` 未被修改
- `mvn -o package -DskipTests` + 重建 `user-portal` Docker 服务
- Playwright 复现：新发起一个 Meeting 流程实例 → 打开两个不同参与人的子任务 → 分别编辑 Name 并 Save → 刷新两边验证互不覆盖，存入 `frontend/user-portal/verification-screenshots/`

## 待确认

- "找不到匹配行时 append"分支（新参与人加入 MI 集合）目前是否还有其它现有代码路径（如管理员后台分派）依赖类似的追加语义，需要在实现阶段用 Explore 再确认一次以确保不冲突。
- 异常类型具体用 `PortalException` 还是需要一个更具体的自定义异常类（便于前端区分"这是 MI 行定位失败"和其它保存失败）——留待实现阶段按现有异常体系惯例决定。
