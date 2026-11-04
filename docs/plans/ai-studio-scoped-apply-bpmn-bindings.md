# 设计 Plan：AI Studio 逐阶段 Apply 后 BPMN 上的动作 / 表单绑定丢失

> 状态：**已确认并实现**（2026-11-03 用户回复「按推荐执行」：方案 A；动作 + 表单一起修；逐动作权威；task id 变了不按名字对应、只给 WARNING；只修 dev 存量；不加开关；不改上下文序列化）。
> 关联：`docs/plans/ai-studio-one-click-co-orchestration.md`【背景】事实 1（一键生成走方案 B，只写一次 `ALL`，不在本 Plan 范围）。
> 分支基线：`common_0731_SELF_AP` @ `4c7556adb`。

## 【背景 / 问题】

AI Studio 手动逐阶段 Apply 走 `AiStudioChatComponentImpl#applyProposal` → `AiWriteServiceImpl#applyGeneratedData(fuId, data, scope)`，
按 scope 先 `clearScopedData` 清掉对应的片，再把这次的片重新写进去（id 由数据库重新分配）。

用户任务和动作按钮的关系**只存在 BPMN 里**（userTask 上的 `custom:property actionIds / actionNames`，流程级是
`globalActionIds / globalActionNames`）。`ActionDefinition` 实体没有 stage 字段，也没有关系表。

### 已核实的事实（代码 + dev 库，2026-11-03）

1. **单独 Apply ACTIONS 不写 BPMN。** `writeActionDefinitions` 只建 `ActionDefinition` 行，丢掉提案里的 `stageIds`；
   scope=ACTIONS 的数据里没有 `processDefinition`，`writeProcessDefinition` 直接 `return false`，
   `AiBpmnActionBindingWriter.bindStageActions` 不会执行，BPMN 一字不动。
   - 如果 BPMN 原来**有**绑定：动作被清空重建、id 全变，BPMN 里的 `actionIds` 指向已删除的 id（失效）。
   - 如果原来**没有**绑定：这次的 `stageIds` 直接丢掉，任务上什么都没有。
2. **单独 Apply PROCESS 冲掉已有绑定。** 新 BPMN 来自模型（Instruction 禁止输出数字 id），这次的数据里又没有
   `actionDefinitions`，`bindStageActions(bpmn, 已有动作, null)` 第一行就返回。新 BPMN 写进库，旧 BPMN 上的
   `actionIds` 全部丢失。
3. **现有的兜底接不住。** `ProcessBpmnStaleIdFixer#fixStaleIds` → `ActionBindingLifecycle.reconcile(xml, namesById, true)`
   只能按 BPMN 里**已有**的 `actionNames` 把失效 id 换成新 id；BPMN 上没有 `actionIds/actionNames` 时无从恢复。
   而且它只在设计器保存流程（`ProcessDesignComponentImpl#save`）、导入、版本回滚时运行，AI 写入不经过它。
4. **运行时没有按名字兜底。** 引擎 `BpmnActionParser` 只读 `actionIds/globalActionIds`；Portal
   `TaskActionService` 拿到空列表就返回空（注释写明不回退到流程全部动作），`resolvePinned(catalogId, actionIds)`
   按 id 精确匹配。结论：**任务上没有按钮。**
5. **（新发现）表单有同一个缺口，而且会连带删掉 stage 绑定。** 表单的绑定存两处：`dw_form_stage_bindings`
   （FORMS 片自己写）和 BPMN 上的 `formId/formName/formReadOnly`（只有 `writeProcessDefinition` 里的
   `AiBpmnFormBindingWriter.bindStageForms` 写）。单独 Apply FORMS 时 BPMN 不动。之后设计器只要保存一次流程
   （手动保存或自动保存都走 `ProcessDesignComponentImpl#save`，它存 Base64），`ProcessBpmnFormStageBindingSync#sync` 就按 BPMN **反向**同步
   `dw_form_stage_bindings`：BPMN 上没有 `formId` → 该表单的 stage 绑定被删光 → 运行时任务表单退回只读 Process Form。
   「表单不受影响」只在 FORMS 之后不再保存流程时才成立。
6. **dev 实证 FU 50036**（`lll`，PUBLISHED）：
   - 3 个动作 50386–50388（09-15 建），3 个表单 50200–50202（09-15 建）。
   - 当前 BPMN（Base64 存储，说明最后一次是**设计器保存**，09-17 16:48:51；AI 写入存明文）两个 userTask
     `Task_SubmitPayment` / `Task_ReviewPayment` **既没有 `actionIds` 也没有 `formId`**；`dw_form_stage_bindings`
     对这三个表单**一行都没有**。
   - 线程里的提案：FORMS 卡（#144）带正确的 `stageBindings`，ACTIONS 卡（#152）每个动作都带正确的 `stageIds`；
     之后又 Apply 了 PROCESS #287、#297 和 TABLES #300。（#138–#154 的 `applied_at` 完全相同，是 09-17 共享线程迁移时补的，
     真实 Apply 时间是 09-15，和实体的 `created_at` 一致。）
   - 动作这条链路和事实 1/2 完全吻合；表单那条和事实 5 吻合。但**中间到底哪一次设计器保存删掉了 stage 绑定**，
     已经没有日志可以确认，记为推断。
   - 全库普查：有动作的 FU 共 11 个，只有 50036 的 userTask 一个 `actionIds` 都没有；一键生成的 50143 / 50144 绑定完整。
7. **AI Generate（`AiGenerationComponentImpl`）的情况。**
   - Apply：前端 `AiPanel.vue#handleApply` 从不传 `regenerateScope`，后端总是按 `ALL` 全量替换。只要模型这次同时
     输出了流程和带 `stageIds` 的动作，绑定就是对的——这条路本身**不受**事实 1/2 影响。
   - **但撤销（`undoLastApply`）有同类问题**：快照来自 `AiContextSerializer`，动作不带 `stageIds`；重放 `ALL` 时动作
     重建换了 id，快照 BPMN 里留着旧 `actionIds`（已删除），`bindStageActions` 没有 `stageIds` 可用 → 撤销后任务没按钮。
     快照 BPMN 里有 `actionNames`，所以「按名字找回失效 id」就能修好它。
   - 同理：MODIFY 模式下模型如果把未改动的动作照抄上下文（上下文里没有 `stageIds`），全量替换后这些动作也丢绑定。
     没有找到任何地方拒绝缺 `stageIds` 的动作——`AiBpmnSemanticGuard` 注释里说的
     `AiResponseParser` 抛 `AI_ACTION_STAGE_BINDING_INVALID`，代码里**不存在**（只出现在可修复错误码清单里）。

## 【目标】

1. 单独 Apply ACTIONS 后，提案里的 `stageIds` 写进**现有** BPMN 的 `actionIds/actionNames`；原来就有、这次没提到的
   绑定按动作名找回新 id，找不回的失效 id 删掉。
2. 单独 Apply PROCESS 后，新 BPMN 上的 userTask 沿用旧 BPMN 里同一个 task id 的动作绑定；流程级 `globalActionIds`
   同样沿用。沿用不了的（task id 变了）在提案预览里给出 WARNING，不静默。
3. 单独 Apply FORMS 后，把新表单的 `stageBindings` 写进现有 BPMN 的 `formId/formName/formReadOnly`，删掉指向已删除表单
   的 `formId`，让后续设计器保存不再删 stage 绑定。
4. AI Generate 的撤销重放后绑定完整（由目标 1/2 的同一个「写入后对齐」步骤顺带解决）。
5. 运行时语义**不变**：引擎 / Portal 仍只认 `actionIds`，不加按名字兜底。

## 【非目标】（本阶段不做）

- 不改一键生成（方案 B 只写一次 `ALL`，另案）。
- 不新增动作 ↔ 阶段的关系表、不改 `ActionDefinition` 实体、不改导出 / 导入 / 克隆 / 回滚格式（见方案 C）。
- 不给引擎 / Portal 加按动作名或「流程全部动作」的兜底。
- 不改 Instruction / CO agent setting / 提示词模板（`AiContextSerializer` 是否输出动作的 `stageIds` 列为待确认 6，默认不做）。
- 不修 DECISIONS 片的同类问题：`clearScopedData(DECISIONS)` 重建后 serviceTask 上的 `decisionId` 属性（`FunctionUnitSnapshotRestorer`
  回滚时会改写它）同样会失效。运行时是否按 id 读没有核实，单列后续。
- 不修 AI Generate MODIFY 部分 scope 时 `ALL` 全量替换可能丢掉模型没输出的片——这是另一个问题，本 Plan 只记录、不处理。
- 不做批量自动修复存量数据的工具（见待确认 4）。

## 【模块】

dw（`backend/developer-workstation`）。前端只多显示后端给的 WARNING 文本（提案卡已经原样渲染 `issue.description`），不需要改代码，也不需要 i18n。

## 【方案】

核心思路：BPMN 是这条关系**唯一**的持久化位置，那就让每一次 scoped 写入在结束前把「现有 BPMN」和「这次写下去的实体」对齐。
`writeMultiInstanceAssignment` 已经是这个模式的先例：本轮没写 process 时，它会去 smartDecode 存量 BPMN、补 `subTableId`
再写回去。

### 方案 A（推荐）：写入服务里加一个「BPMN 绑定对齐」步骤

`applyGeneratedData` 里：

1. **清空之前**（只在本次会替换流程、或会重建动作 / 表单时）从存量 BPMN 读出旧绑定快照：
   `userTaskId → [actionName…]`，加上流程级 `globalActionNames`。存名字，不存 id——id 在 `ALL` / `ACTIONS` 写入里会变；
   `action_name` 在 FU 内有唯一约束（`uk_action_name_fu`），是动作在 AI 写入里本来就用的业务键（`bindStageActions` 按名字找已落库动作）。
2. **flush 之后**（现在 `writeProcessDefinition` 之后、`writeMultiInstanceAssignment` 之前）对「当前流程」做一遍对齐。
   当前流程可能是本次新写的，也可能是存量（需要 `XmlEncodingUtil.smartDecode`；有改动时以明文写回，与紧随其后的 `writeMultiInstanceAssignment` 一致——它本来就会把存量 BPMN 写成明文，读取方都用 `smartDecode`）。
   规则按动作逐个算，最后每个 userTask 的列表一次写定：

   | 情况 | 该动作挂在哪些 userTask |
   |---|---|
   | 这次写入带了这个动作，且有非空 `stageIds` | 以 `stageIds` 为准（**逐动作权威**：提到了就全听它的，包括从旧任务上撤下） |
   | 这次写入带了这个动作，但没有 `stageIds` | 沿用旧位置：写入的 BPMN 自己带绑定时以它为准（设计器存的、撤销快照）；一个绑定都不带（模型新出的流程）才按 task id 继承写前 BPMN |
   | 这次没写动作（scope=PROCESS） | 沿用旧快照：同一个 task id 原来挂的名字 → 现在的 id |
   | 旧快照里的名字现在找不到对应动作 | 从该任务上删掉（不留失效 id） |

   - 任务内顺序：先按本次提案里动作的顺序，再接沿用下来的、按旧顺序。
   - `globalActionIds`：同样的来源规则按名字沿用；名字找不到的删掉；这次带了 `stageIds` 的动作从流程级列表移除（逐动作权威）。
   - 表单：本次写了表单（FORMS / ALL）而流程是存量时，调用现有 `AiBpmnFormBindingWriter.bindStageForms`；然后删掉指向
     已不存在表单的 `formId/formName/formReadOnly`（含 REQUEST 场景的 `requestFormId/requestFormName`）。
     本次写了流程时，`bindStageForms` 已经在 `writeProcessDefinition` 里跑过，不重复。
3. 实现直接改在 `AiBpmnActionBindingWriter` 里（复用它的容器选择与命名空间处理）：`capture`（写前快照）→ `plan`（按名字算最终放置，
   写入与预览共用）→ `rebind`（落到 XML）；原来的 `bindStageActions` 被 `rebind` 取代。读属性复用 platform-common 的
   `BpmnActionBindings.properties / ids / names`。表单侧在 `AiBpmnFormBindingWriter` 加 `dropStaleForms`。
4. **预览**（`AiStudioProposalPreviewer`，Apply 前同一套检查）：
   - ACTIONS：`stageIds` 里有存量 BPMN 不存在的 userTask id → WARNING（列出动作名和 id）。
   - PROCESS：旧 BPMN 上挂了动作、但新 BPMN 里没有同 id userTask 的 → WARNING「这些动作将不再挂在任何任务上：…，
     请随后 Apply 一次 ACTIONS 提案或在设计器里绑定」。
   - 对齐后 FU 有动作、但某个 userTask 一个都没挂 → WARNING（不阻止 Apply：有的任务本来就不需要按钮）。
   - （实现时补的第四条）FU 还没有流程、提案里的动作却带了 `stageIds` → WARNING：这些 `stageIds` 无处可写（动作不存 stage），流程建好后要再绑一次。
   - 一律 WARNING 不是 ERROR：结果仍然可用，只是需要人补一步；描述是后端英文原文，前端提案卡原样显示。
5. 写入时把对齐做了什么记一行 `info`（每个 task 前后的动作名），自动修复要留痕（错误处理治理红线 1）。

**为什么不按任务名找**：PROCESS 提案改了 task id 时，按 userTask 的 `name` 猜对应关系是典型的「靠名字猜语义」
（`config-over-heuristics`）。改了 id 就当新任务，WARNING 告诉用户，比悄悄挂错按钮好。这条列为待确认 3。

### 方案 B：只在两个点上补，不做统一对齐

- ACTIONS 写入后：用这次的 `stageIds` 调 `bindStageActions(存量 BPMN, …)`，再 `ActionBindingLifecycle.reconcile(…, true)` 找回其余失效 id。
- PROCESS 写入时：把旧 BPMN 的 `actionIds/actionNames` 按 task id 拷到新 BPMN。
- 改动最少，但：`reconcile` 找不回的失效 id 会**留着**（它的设计是保守不删）；`bindStageActions` 只会追加 / 覆盖提到的任务，
  不会把一个动作从没提到的旧任务上撤下来；表单、AI Generate 撤销要再各补一处。三四个点各写一套规则，后面很难说清最终语义。

### 方案 C（不推荐）：把动作的 stage 绑定落库（新表或 `ActionDefinition` 加列）

和表单的 `dw_form_stage_bindings` 对称，`AiContextSerializer` 也能直接输出 `stageIds`。但：

- 要新 init-script、改实体，导出 / 导入 / 克隆 / 版本快照 / Admin 导入 / Deploy ZIP 全部要带上（`function-unit-portability`）；
- 设计器「Bind Process Node」只写 BPMN，等于又多一份要和 BPMN 双向同步的数据——表单这边事实 5 的问题正是双写带来的；
- 运行时仍只读 BPMN，落库并不减少对齐工作。

### 方案 D（拒绝）：运行时按动作名 / 流程全部动作兜底

Portal 已经明确不回退到流程全部动作；按名字兜底会把审批按钮挂到提交环节上，违反 `config-over-heuristics`。

**推荐：方案 A。** 一处规则覆盖 ACTIONS / PROCESS / FORMS / `ALL`（含 AI Generate 撤销），最终语义可以用一张表说清；
不动 schema、不动运行时、不动导入导出。

## 【影响面】

| 层级 | 变更 |
|------|------|
| API / Component | 无新接口。`AiWriteServiceImpl#applyGeneratedData` 加「清空前读旧绑定」与「flush 后对齐」两步；`AiStudioProposalPreviewer` 加三条 WARNING |
| 工具类 | 新 `AiBpmnBindingCarrier`（暂名）；`AiBpmnActionBindingWriter#bindStageActions` 并入新规则（保留签名或改为内部调用，[实现时定]）；表单侧新增「删除失效 formId」 |
| Entity / SQL | 无 |
| 前端 types / views | 无（WARNING 文本原样显示） |
| i18n | 无 |
| 部署 / 配置 | 无开关（方案 A 改的是写入正确性，不需要回退开关；见待确认 5） |
| 其他调用方 | `AiGenerationComponentImpl#applyGeneratedData` / `undoLastApply`、`AiStudioUndoService#undo`（PROCESS / TABLE_RELATIONS 整片还原）都走 `applyGeneratedData`，自动带上对齐；要逐个回归 |

## 【数据与契约】

- 不新增字段。BPMN 上仍是 `actionIds`（JSON 数字数组）+ `actionNames`（JSON 字符串数组），一一对应；写入格式与 `AiBpmnActionBindingWriter#writeActionProperties` 相同，容器命名空间只认 `custom.bpmn.io` 与 `workflow.platform/schema/custom` 两种（同该类注释）。
- 存量 BPMN 的编码：读用 `smartDecode`；有改动时写回明文（同 `writeMultiInstanceAssignment`），没改动就不碰。
- 「ID 变化」的规则（任务要求说明的部分）：
  - **ACTIONS 再次 Apply**：所有动作 id 都会变。旧 BPMN 里的 `actionIds` 全部失效 → 按上表重算，绝不留失效 id。
  - **FORMS 再次 Apply**：所有表单 id 都会变。`dw_form_stage_bindings` 由 FORMS 片重写；BPMN 上的 `formId` 按新 `stageBindings` 重写，
    指向已删除表单的属性删掉。之后设计器保存时 `ProcessBpmnFormStageBindingSync` 看到的就是一致的 BPMN，不会再删 stage 绑定。
  - **TABLES 再次 Apply**：`subTableId` 已由 `writeMultiInstanceAssignment` 处理；表单的表绑定由现有 `detachFormReferencesToTables` 处理（会 warn 要求重绑），本 Plan 不改。
  - **PROCESS 再次 Apply**：动作 / 表单 id 不变，按 task id 沿用；表单靠已落库的 stage 绑定（`bindStageForms`，现有逻辑）。

## 【分期】

- **MVP**（一次交付）：方案 A 的对齐步骤（动作 + 表单）、三条预览 WARNING、单测、dev 验证（含 Portal 打开任务看按钮）。
- **后续**（单列，不在本轮）：
  - DECISIONS 片重建后 serviceTask `decisionId` 失效（先核实运行时是否按 id 读）。
  - 让解析阶段真正拒绝「有流程却缺 `stageIds`」的动作（补上 `AI_ACTION_STAGE_BINDING_INVALID`，或改掉 Guard 里失实的注释）。
  - `AiContextSerializer` 是否输出动作的 `stageIds`（从 BPMN 反推），让模型在 ACTIONS 阶段看到现状（待确认 6）。
  - AI Generate MODIFY 部分 scope 下 `ALL` 全量替换的丢片问题。

## 【风险与回滚】

- **把按钮挂错**：唯一会改变「挂在哪」的输入是这次提案的 `stageIds`（与今天一键生成同一来源）和旧 BPMN 的同 id task；不引入任何新猜测。
- **存量 BPMN 解析失败**：本轮没写 process 时沿用 `writeMultiInstanceAssignment` 的做法，抛 `AI_EXISTING_BPMN_INVALID` 指向存量数据，整个 Apply 回滚（同一事务）。
- **逐动作权威的副作用**：ACTIONS 提案里给某个动作写了 `stageIds`，会把它从没提到的旧任务上撤下——这是「scope=ACTIONS 是整片替换」的本意，但和用户在设计器里手工加的绑定可能冲突。预览 WARNING 列出「将从哪些任务撤下」，让用户在 Apply 前看到（待确认 2）。
- **撤销**：PROCESS 的 30s 撤销重放旧 BPMN，旧 BPMN 自带当时的绑定，对齐后不变；替换型 scope 本来就不可撤销，不受影响。
- **回滚**：纯代码改动，无数据迁移；回退提交即可。已被新逻辑修好的 BPMN 是正确数据，不需要回退。

## 【验收】

- 反例（今天）：
  1. FU 已有流程（两个 userTask，无动作）→ 单独 Apply ACTIONS 提案（`stageIds` 正确）→ BPMN 上没有 `actionIds`；部署后 Portal 打开任务，**没有按钮**。
  2. FU 流程与动作都已绑定 → 单独 Apply PROCESS 提案 → 新 BPMN 上 `actionIds` 全部消失。
  3. FU 已有流程 → 单独 Apply FORMS 提案 → 在流程设计器里保存一次流程（不改任何东西）→ `dw_form_stage_bindings` 被清空。
- 正例：
  1. 反例 1 的同样操作 → 两个 userTask 的 `actionIds/actionNames` 与提案 `stageIds` 一致；设计器 Bind 面板显示已绑定；部署到 Portal 打开任务，**按钮出现且只出现该任务的那几个**。
  2. 反例 2 的同样操作，新 BPMN 保留原 task id → 绑定不变；把其中一个 task id 改掉 → 该任务无绑定，提案卡预览有 WARNING 列出受影响动作。
  3. 已绑定的 FU 再次单独 Apply ACTIONS（改了一个动作名、删了一个动作）→ 没出现在提案里的旧 id 全部消失，不残留失效 id；提到的按 `stageIds`。
  4. 反例 3 的同样操作 → BPMN 上 `formId` 指向新表单 id；再打开设计器保存，`dw_form_stage_bindings` 不变。
  5. AI Generate：Apply 后 30s 内撤销 → 任务按钮恢复为 Apply 前的绑定（今天会丢）。
  6. 一键生成 / AI Generate 的 `ALL` 写入：带 `stageIds` 的动作结果与今天一致（对照 50143 / 50144）；不带的按 task id 沿用写前位置（今天会落空）——这是有意的行为变化，与撤销共用一条规则。
- FU：dev 50036（存量坏数据，修复后重新 Apply 一次 ACTIONS 提案即为正例 1 的现场）；另建一个新 FU 跑正例 2–5。

## 【验证】（实现后最低命令）

- 单测（JDK17，Homebrew Cellar 路径，见 memory「Build with JDK17」）：
  - `AiBpmnBindingCarrierTest`（新）：上表四种情况各一例；顺序规则；`globalActionIds` 沿用 / 删除；Base64 与明文两种存量编码原样回写；两种属性容器命名空间；不认 `camunda:properties`；失效 `formId` 删除、REQUEST 场景属性同理；无变化时返回原字符串。
  - `AiWriteServiceTest` / `AiScopedWriteProperties`：scope=ACTIONS 后存量 BPMN 带上新 id；scope=PROCESS 后旧绑定按 task id 沿用；scope=FORMS 后 `formId` 更新；scope=DECISIONS / TABLE_RELATIONS / 邮件等不改 BPMN 的绑定属性；`ALL` 结果与今天一致；存量 BPMN 坏 → `AI_EXISTING_BPMN_INVALID`。（mock 环境没有 JPA 分配 id，测试里在 `flush` 的 answer 中给新实体补 id。）
  - `AiStudioProposalPreviewerTest`：三条 WARNING 的出现与不出现。
  - `AiStudioUndoServiceTest`、AI Generate 撤销路径：撤销后绑定完整。
  - 现有 `AiBpmnActionBindingWriterTest`、`AiBpmnFormBindingWriterTest`、`AiBpmnSemanticGuardTest` 全绿。
- `mvn -pl backend/developer-workstation -am clean package`（JDK17，必须 clean，见 memory）。
- dev：
  1. 重建 DW 后端容器（`build` + `up --no-deps`）。
  2. 按验收正例 1–5 在 AI Studio 里操作；每步后查库：`dw_process_definitions`（smartDecode 后看 userTask 属性）、`dw_action_definitions`、`dw_form_stage_bindings`。
  3. 部署 FU（50036 与新 FU）→ 用有该 FU BU+Role 的账号登录 Portal → 发起一单 → 打开待办任务，截图确认按钮（`/verify-ui`，截图放 `verification-screenshots/`）；同时确认 Portal `task actions` 接口返回的 actionIds 与 BPMN 一致。
  4. 没在 Portal 实际看到按钮之前，不报「已修复」（`bugfix-verification`）。

## 【待确认】（需要用户拍板）

1. **修复范围**：只修动作（任务原始范围），还是**动作 + 表单一起修**（事实 5，同一个对齐步骤，多十几行）？推荐一起修。
2. **ACTIONS 提案的绑定语义**：推荐「逐动作权威」——带了 `stageIds` 的动作完全以它为准（会从没提到的旧任务上撤下），没带的沿用旧位置；另一种是「只增不撤」（提到的加上，旧的都保留，只删失效 id），更保守但无法通过 AI 把按钮从某个任务上拿掉。
3. **PROCESS 提案改了 task id**：推荐不按任务名对应、只给 WARNING；还是要按 userTask `name` 完全相同时沿用？
4. **存量坏数据**：推荐只修 dev 50036（修复上线后在 AI Studio 里重新 Apply 一次 ACTIONS 提案，同时当作验证）。UAT / 生产是否有同类 FU 不知道——要不要我写一个**只读**的普查 SQL（找「有动作但 userTask 全无 `actionIds`」的 FU）给运维跑？不做自动修复工具。
5. **开关**：推荐不加开关（这是写入正确性修复）；如果你希望 UAT 先观察，可加一个默认开启的 `ai-generation.write.rebind-bpmn-bindings`。
6. **`AiContextSerializer` 输出动作 `stageIds`**（从 BPMN 反推）：推荐本轮不做，避免改模型输入；对齐步骤已经让「模型照抄上下文不带 `stageIds`」不再丢绑定。

---
请确认以上设计 Plan。确认后若要开始实现，请回复 **按 playbook 执行**（或补充/修正项）。

---

## 实现记录（2026-11-04）

- 提交：`79665428d`（Plan）→ `9550819b6`（实现）→ 审查修正（见下）。分支 `claude/happy-banach-3ac58a`，基于 `common_0731_SELF_AP@4c7556adb`，未合入。
- 独立审查发现并已修：表单多容器时把刚写的活 `formId` 一并删掉（改为按容器处理）；撤销重放会让原本没按钮的任务继承按钮（改为「写入 BPMN 自带绑定就视为完整」）；
  流程级动作不受逐动作权威约束；ALL 写入在存量 BPMN 读不了时误报（ALL 会清掉它，改为 warn）；`ACTION_BINDING_EMPTY_TASK` 只在本提案放置动作或该任务原来有按钮时才报。
- 已知取舍：同一任务里，带 `stageIds` 的动作排在沿用下来的前面，设计器里手排的顺序在混合情况下不保留。
- 已知取舍：「写入 BPMN 自带任一绑定即视为完整」是全有或全无的。PROCESS 提案若违反 Instruction 在某个任务上带了 `actionIds`，其他任务就不再继承旧绑定；预览会对原来有按钮的任务报 `ACTION_BINDING_EMPTY_TASK`。
- 复审：`a23ab6318` 独立复审 PASS。
- dev 验证（DW 侧，热替 4 个类）：scratch FU 50152 先在旧代码上复现（ACTIONS 后无 `actionIds`；FORMS 后无 `formId`，原样设计器保存一次 stage 绑定被删光），
  新代码下 FORMS / 设计器保存 / ACTIONS / PROCESS（同 id）全部正确，dry-run 三种 WARNING 符合预期。FU 50036 用原提案 #144 / #152 重新 Apply 后两个任务都有表单与按钮。
- **Portal 已验证（2026-11-04，用户批准后给 dev 库灌了 96 号脚本）**：scratch FU 50155 先一次 ALL 写表/表单/流程（不含动作），
  再**单独** Apply ACTIONS、两次**单独** Apply PROCESS（同 task id，第二次给两个任务加了 `assigneeType=PROCESS_INITIATOR`），绑定始终保持；
  部署为 `1.0.2` 后在 Portal 发起一单：发起页显示 `submit_request`，提交后待办「Review Request (v2)」显示 `approve_request` / `reject_request`，
  任务表单 `scoped_portal_review_form` 正常渲染。截图：`verification-screenshots/scoped-apply-bindings-01-start-submit-button.jpg`、
  `verification-screenshots/scoped-apply-bindings-02-review-task-approve-reject.jpg`（未提交进仓库）。
- **新发现（既有缺陷，未修）**：单独 Apply FORMS 时，`writeFormDefinitions` 只按**本次写入**的表解析 `tableBindings[].tableName`，
  scoped FORMS 提案不带表 → `dw_form_table_bindings.table_id` 全为 NULL；设计器里表单失去表绑定，部署时 admin-center
  `assertReadyForActivation` 报 `VERSION_SNAPSHOT_INCOMPLETE`。FU 50036 用 #144 重 Apply 后就是这个状态（重 Apply 前表绑定为 0 行，可部署但表单无表）。
  **已修（用户 11-04「Fix it here」）**：`writeTableDefinitions` 在本次不带表时返回 FU 现有表的名称目录（与预览/校验的
  `existingTableFieldsFor` 同一目录）。同一缺陷也让单独 Apply TABLE_RELATIONS 把关系全部跳过（只 warn）——清空后一条不写，
  连带 TABLE_RELATIONS 的撤销也是空操作——一并修好。单测先红后绿（`formsScope_bindsFormsToTheExistingTables`、
  `tableRelationsScope_linksTheExistingTables`）。dev：50036 重 Apply #144 后表单绑回 `biz_order` / `biz_order_item`，
  部署为 `1.0.3` 成功，Portal 发起页显示 `submit_car_wash_payment` 与提交表单字段
  （`verification-screenshots/scoped-apply-bindings-03-fu50036-start-after-repair.jpg`）。50036 审批任务指派给实体经理，未在 Portal 走到审批步。
- 全量 DW 测试 1544 个，仅 `ExportImportComponentImplTest.importFunctionUnit_rewritesBpmnIdsAfterImport` 报错，干净基线上同样报错。
