# Form Design：按 scene 成对建表单 / ACTION 限 To Do / Views Form 分类

> 状态：**实施中**（2026-08-17 立项）。
>
> 本文覆盖 Developer Workstation `Form Design` 标签页里 **Create Form 行为**与
> **表单列表分类**的三项改动。涉及的 scene 机制（To Do / My Requests 双设计）源自
> [../plans/portal-form-scene-split-and-fu-audit.md](../plans/portal-form-scene-split-and-fu-audit.md)。

## 背景：三个问题

Form Design 把 `formType`（PROCESS / TASK / ACTION / DETAIL）与
`scene`（TASK=To Do / REQUEST=My Requests）当成两个自由正交的维度让设计师手选，导致：

| # | 问题 | 现象 |
|---|---|---|
| 1 | PROCESS / TASK 要手动建两遍 | To Do 与 My Requests 是同一步骤的两份独立设计，得建两次、分别挂节点 |
| 2 | ACTION 能建在 REQUEST 下但永远打不开 | 全程零报错：designer 不拦、后端只校验 `formType`、运行时也不报错 |
| 3 | DETAIL 表单归属是半成品 | 第三个 tab 被 `v-if="sceneCounts.DETAIL > 0"` 挡住，没有 DETAIL 表单时整个 tab 消失，设计师发现不了这个分类 |

问题 2 的结构性原因：Portal 里 `FORM_POPUP` 的派发点只有
`frontend/user-portal/src/views/tasks/detail.vue` 一处；My Request 页（`views/applications/`）
按钮是硬编码的 Urge / Withdraw，**没有动作按钮机制**。动作按钮来源是 BPMN UserTask 的
`actionIds` 扩展属性，而 My Requests 不对应任何 userTask（见 `FormScene.java` 类注释）
⇒ REQUEST scene 的 ACTION 表单**结构上不可能有入口**。

---

## 关键调研结论

### ★ To Do ↔ My Requests 的对应靠 **node id**，不靠名字

这条决定了整个方案的形状。同一个 `<bpmn:userTask id="X">` 上并存两组扩展属性
（`utils/bpmnFormBindingUpdate.ts:57-64`）：

| scene | BPMN 属性 |
|---|---|
| To Do | `formId` / `formName` / `formReadOnly` |
| My Requests | `requestFormId` / `requestFormName` |

读取端 `utils/bpmnFormBindings.ts:144-155` 从**同一节点**取 `requestFormId` 生成 REQUEST
绑定，map key 是 `nodeId::scene`；`dw_form_stage_bindings` 唯一键同为
`(form_id, stage_id, scene)`，REQUEST 表单绑到**同一个 `stage_id`**。

⇒ **表单名不同，照样正常挂流程、照样按 process flow 展示。**
种子 `17-Multi-Instance-Subtask-Demo/00-init-kk.sql:264` 里
`'Assign Task (My Request)'` 的后缀纯粹是为绕开 `uk_form_name_fu UNIQUE (function_unit_id, form_name)`，
**没有任何逻辑依赖它**。唯一用到名字的地方是 scene **内**的兜底匹配
（`useApplicationDetailNodeFormMap.ts:169` 明确带 `sceneOf(f) === …` 过滤），跨 scene 不会串。

**决策：不改名字唯一约束**，成对创建时 REQUEST 那份自动加 `(My Request)` 后缀。
⇒ 不动 DB 约束、不动 `ProcessBpmnStaleIdFixer`、不动导入导出 / clone / 回滚的按名解析。
这砍掉了约 70% 的工作量与全部 P0 风险（详见文末「被否决的方案」）。

### form ↔ view 是单向 N:1

真相源只有一列 `dw_main_table_view_configs.detail_form_id`（nullable，`ON DELETE SET NULL`）。
`FormDefinition` 上**没有任何 view 引用**。多个 view 可共用同一个 DETAIL 表单，无约束禁止。
`mainTableViewApi.list(fuId)` 已返回每个 view 的 `mainTableId` + `detailFormId`
⇒ 反向索引纯客户端可算，零新增查询接口。

### ★★ 但复用 `updateView` 写回会踩两个雷

`MainTableViewServiceImpl.java:89-119`：

1. **L118 无条件 `config.setStatus(MainTableViewStatus.DRAFT)`**，而 Portal 侧三处查询全是
   `WHERE v.status = 'PUBLISHED'`（`PortalMainTableViewServiceImpl.java:67,123` 及 FK 下钻）。
   ⇒ 从表单侧改一下选择，**会把这个 view 从 Portal 上整个弄消失**——正是本次最反对的静默后果。
2. **L106 `setDetailFormId(request.detailFormId())` 是唯一没有 null 守卫的 setter**，
   而前端 `mainTableViewApi.update` L128 是 `payload.detailFormId ?? null`
   ⇒ 任何不带该字段的局部 payload 都会清掉已有绑定。

**结论：不能拿 `updateView` 当「只改一列」用**，需新增窄端点（见阶段 3.2）。

### ★ Portal 侧「点行进详情」已经完全做好

`useMainTableViewPage.ts:403-426` 的 `openRow`：view 有 `detailFormId` ⇒ 跳
`/views/{fuCode}/detail?viewId=…&rowKey=…`，由 `main-table-views/detail.vue` 用该 DETAIL
表单只读渲染那一行；没有则回退申请页；两者皆无时给提示（不会呆着不动）。
触发是**单击**（`main-table-views/index.vue:190` 的 `@row-click`），不是双击。

⇒ 运行时链路无需新建，本次唯一缺的是**设计期**的选择入口。

---

## 实施方案

### 阶段 1：成对创建（后端）

放后端而非前端连发两次 POST：`create()` 已是 `@Transactional`，一次调用建两行天然原子；
前端两次 POST 若第二次失败会留下半对孤儿。

- `FormDefinitionRequest` 加 `Boolean createBothScenes`（缺省 null=false，向后兼容）。
- `FormDesignComponentImpl.create()`：`createBothScenes` 且 `formType ∈ {PROCESS, TASK}` 时同事务建两行：
  - TASK 行用原名；REQUEST 行用 `原名 + " (My Request)"`
  - 两行 `configJson` 均为空画布 `{rule:[],options:{}}`（设计师自己设计字段）
  - PROCESS 对**两个 scene 各自**跑 `validateProcessFormUniqueness`，任一被占则整体失败
  - 后缀名已存在时走既有 409 `CONFLICT_FORM_NAME_EXISTS`，不自动改名
  - 返回 TASK 那行（前端选中它）

> 不改 `uk_form_name_fu`、不改 repository 的 exists 方法、**不动任何 `.sql`**。

### 阶段 2：ACTION 只能建在 To Do —— 任何错误都报在页面上

- **前端明示**：`FormCreateDialog.vue` 选中 ACTION 时**不隐藏** scene 单选，而是把 REQUEST
  选项 `disabled` + 锁 TASK + 下方一行说明。直接隐藏等于又一次静默（设计师不知道为何少了选项），
  禁用 + 说明才是「报在页面上」。
- `useFormLifecycle.ts` `handleCreateFormTypeChange`：ACTION 强制 `scene='TASK'`（与现有 DETAIL 分支并列）。
- **后端硬拦**（防 API 直调）：`create()` / `update()` 里 `formType == ACTION && scene == REQUEST`
  ⇒ 抛 `DeveloperBusinessException("INVALID_ACTION_FORM_SCENE", …)`。
- **纪律**：`useFormLifecycle.ts:438` 的 catch 已是 `ElMessage.error(e.response?.data?.message || …)`，
  保持这条路径；**本次所有新增 catch 一律要有用户可见提示，不许 `catch {}` 吞掉**。

### 阶段 3：Views Form tab

**3.1 改名 + 常驻 + 按表分组**（`FormListSidebar.vue`）
- 去掉 `v-if="sceneCounts.DETAIL > 0"` 让 tab 常驻；label 换 `t('form.viewsForm')` = **Views Form / 视图表单**。
- 内部 `SceneTab` 的 `'DETAIL'` 成员保持不变（纯内部类型，改名无收益且触及 `sceneOf`）。
- 仅 DETAIL tab 激活时改分组渲染，照搬 `MainTableViewDesignTab.vue:34-52` 的 `viewGroups` 形状；
  另两个 tab 保持现有扁平 `DesignerListTable`。
- **分组键用仓库既有的表解析顺序**（抽共用 helper），照 `useTableFieldRules.ts:56-62` 三段式：
  `PRIMARY binding.tableId` → `form.boundTableId` → **`bindings.length === 1` 时取那唯一一条**。
- **需给 sidebar 新增 `tables` prop**：`FormDesigner.vue:6-14` 目前只传 `has-tables`(布尔) 与
  几个查询函数，`store.tables` 只给了 `FormCreateDialog`(L425)；分组要按表顺序驱动。
- **两个兜底**：① 无表绑定的 DETAIL 表单归入 `form.unboundTableGroup` 分组
  （表单的 `bound_table_id` 可空，直接照搬 View Design 的 `.filter(g => g.views.length > 0)`
  会让它们整个消失）；② **空表分组要保留**，否则设计师无法给尚无表单的 view 选表单
  ——这与 View Design 的过滤策略相反，是有意的。
- `more` 下拉：DETAIL 行隐藏 `bindNode`（对无流程步骤的表单无意义）。

**3.2 在 Views Form tab 里给 view 选表单（可编辑）**
- `FormDesigner.vue` 载入时多调 `mainTableViewApi.list(functionUnitId)`。
- 每个表分组下同时列出该表的 views（`v.mainTableId === group.table.id`），每个 view 一个下拉，
  选项 = 该表分组下的 DETAIL 表单 + 「无详情页」(null)，当前值 = `v.detailFormId`。
- **新增窄端点**（不复用 `updateView`，理由见上文「两个雷」）：
  `PATCH /api/v1/function-units/{fuId}/main-table-views/{viewId}/detail-form`
  body `{ detailFormId: number|null }` —— 只 set 这一列、**不碰 status**、不碰 fields/accessRules。
- N:1 语义：一个 view 只能有一个详情表单（改选会顶掉原来的）；一个表单可被多个 view 共用。
- 写成功后刷新本地 views；失败必须 `ElMessage.error`。
- 表单行侧另加一列「所属视图」，`el-tag` 列出引用它的 view 名（照 `#cell-boundNodeId` slot 写法），
  空时显示 `form.notUsedByAnyView`。
- 与 View Design 面板是同一份数据的两个入口，两边都能改，不做互斥。

**3.3 修 View Design 下拉跨表误挂**（顺带的既有 bug）
- `useMainTableViewDesigner.ts:215-217` 的 `detailFormOptions` 只 `filter(formType === 'DETAIL')`，
  可以给 A 表的 view 挂 B 表的表单，Portal 上静默渲染成空白
  （`detail.vue` 纯按字段名取值，从不校验表身份）。
- 紧接的 L218-231 已有现成按表过滤写法（per-form `getFormBindings` +
  `binds.some(b => b.tableId === props.view.mainTableId)`，catch 时保守放行）。
  ⇒ **两处合并成一次遍历**，别再起第二轮 per-form 并发请求（现为 N 表单 N 次 HTTP，翻倍不可接受）。
- 保留「当前已选中的表单」始终在选项里，否则历史跨表数据一进面板就被清空。
- 与 3.1 **必须共用同一个表解析 helper**，否则会出现「在 Views Form 归到 A 表分组、
  但 A 表的 view 下拉里选不到它」的错位。

### 阶段 4：i18n + 小修

三份 locale（`en.ts` / `zh-CN.ts` / `zh-TW.ts`）同步新增 `form.viewsForm`、
`form.unboundTableGroup`、`form.notUsedByAnyView`、`form.usedByViews`、
`form.actionFormTodoOnlyHint`、`form.pairCreateHint`、`form.viewDetailFormPicker` /
`form.viewDetailFormNone`；后端 `messages_en/zh_CN/zh_TW.properties` 加
`form.action_form_todo_only` + `form.action_form_use_todo_scene`（与前端 tip 是**两套独立文案**）。

顺带修 `useFormLabels.ts:8-15` 的 `formTypeLabel` 缺 `DETAIL` 键（现在渲染裸串 "DETAIL"），
以及 `formDesigner.ts` 的 `FORM_TYPE_SORT_ORDER` 缺 `DETAIL`（DETAIL 表单排序算出 `NaN`）。

### 删掉 create 时那段静默丢弃的 `stageBindings`

前端 `handleCreateForm` 曾把选中的节点拼成 `stageBindings` 一起 POST，但
`FormDefinitionRequest` **没有这个字段**，而 `JacksonConfig` 用的 `Jackson2ObjectMapperBuilder`
默认关闭 `FAIL_ON_UNKNOWN_PROPERTIES`（`JsonUtils.java:23` 也显式关过一次）
⇒ Jackson 直接丢弃、不报错、照样返回 200。前端以为绑定已提交，实际从未落库
——**一段发了没人收的死代码**，而且是最难查的那类静默失败。

选择**删前端发送**而不是给后端加字段：节点绑定的真实链路是 `useFormNodeBinding` 的 BPMN 写入
（写 `formId` / `requestFormId`）+ `FormStageBindingController`，语义已完整承载；
后端再加一条落库路径只会和它打架、产生重复绑定。
创建对话框里的节点选择器**保留**——它仍是「先想清楚这个步骤挂哪些节点」的前置提示与必填校验。

---

## 留档不修（既有债，与本次无关）

- ~~**My Requests 主表单区渲染的是 To Do 设计**~~ **已修**（2026-08-18）：
  `useApplicationDetailBpmnCurrentForm.ts` 的 `parseBpmnXmlAndGetFormId` 现在按节点是否有
  `requestFormId`/`requestFormName` 选 REQUEST 设计，否则回退 TASK（与
  `useApplicationDetailNodeFormMap.ts` 的 `hasRequestDesign` 同一套判断）；
  `useApplicationDetailLoaders.ts` 的名字兜底也已按 `scene` 过滤，不再跨 scene 误配。
  previous-forms 列表（`parseBpmnXmlAndGetPreviousFormIds`）**仍只读 TASK 设计**，未改动——
  它服务于只读的历史步骤留痕面板，语义与「当前节点该显示哪份设计」不同，超出本次修复范围。
- 删除 DETAIL 表单会静默把所有引用它的 view 的 `detail_form_id` 置 null，无警告。
- DETAIL 表单里的 sub-table 控件在详情页会被跳过不渲染（`detail.vue` 有意为之：
  独立记录页没有流程/绑定上下文）。设计 Views Form 时别指望子表出数据。

## 被否决的方案：真同名（改唯一约束）

初版方案要把 `uk_form_name_fu` 改成 `UNIQUE (function_unit_id, form_name, scene)` 以实现两份
**完全同名**。查实「名字与挂流程无关」后否决，因为代价与收益严重失衡：

- **P0 静默改错**：`ProcessBpmnStaleIdFixer.java:46-48,70` 按裸名建 `HashMap`（后者胜），
  L70 用它重写 **To Do 的 `formId`**，每次流程设计保存都跑 ⇒ 无声把 To Do 绑定指向 My Requests 表单。
- **P0 直接 500**：`ProcessBpmnValidator.java:410-422` 的 `findByFunctionUnitIdAndFormName` 返回
  `Optional`，两行匹配时抛 `IncorrectResultSizeDataAccessException`，外层只 catch `NumberFormatException`。
- **P0 回滚崩**：`FunctionUnitSnapshotFactory` 不存 `scene`，legacy 快照还原时两行都变 TASK ⇒ 撞约束。
- **P1**：`BpmnIdRewriter` / `FunctionUnitCloner` / `FunctionUnitImporter` /
  `FunctionUnitImportWriter`（`linkedFormName` 名字优先于精确 id 映射）/ `MainTableViewPortability`
  的按名 map 全部二义；user-portal 另有 12 处 `f.name === …formName` 兜底。
- **SQL 连带**：5 个种子文件 15 处 `ON CONFLICT (function_unit_id, form_name)` 会因推断子句
  匹配不到唯一索引而全部报错；而 init-scripts 是只增不改的
  ⇒ 需额外造一个 `WHERE scene='TASK'` 部分唯一索引来保兼容。

⇒ 现方案**零 SQL 改动**：不新建 `66-*.sql`、不动 `00-init-all.sh`、不动那 15 处 `ON CONFLICT`、
不碰 `all-in-one-for-gui.sql`（它停在 63-，连 `scene` 列都没有，动了必炸）。
