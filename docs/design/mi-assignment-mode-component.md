# Assignment Mode 组件：容器化与三端渲染（DW 设计器 / DW Preview / User Portal）

本文描述 **Assignment Mode**（`miAssignment`）表单组件的形态演进与三处渲染路径。
分派模型本身（BPMN 契约、后端解析、共享认领池）见
[mi-subtask-bu-role-assignment.md](./mi-subtask-bu-role-assignment.md)，本文不重复。

> **给后续 agent**：这个组件横跨两个前端应用、四条渲染路径，且踩过多个**反直觉的框架约束**。
> 改它之前请先读「关键约束」和「排查手册」两节——大部分坑不看文档必然复现。

## 背景与目标

MI 子任务可以「按人分派」或「按 BU + Role 分派」。对应三个真实表字段
（`assignee` / `bu_code` / `role_code`，字段名来自 BPMN 契约，**没有固定列名回退**）。

演进过程（都是用户反馈驱动）：

| 阶段 | 形态 | 问题 |
|------|------|------|
| 初始 | 纯 marker + 两个 radio | 弹窗里只有 radio，**没地方选人/BU**，字段散落在框外 |
| v1 | marker 作锚点，把三字段「拉」到旁边 + CSS 画框 | 看着是一体，实际仍是平级 rule；**拖组件字段不跟着走** |
| v2 | 真容器：三字段作 `children` | 成员写死、字段**拖不动** |
| v3（当前）| 自由 drop 容器 + 一次性自动收纳 | —— |

**当前目标形态**：组件是一个可拖入/拖出的容器，预留区域放 assignee / BU / role；
成员与顺序由**表单作者**决定；存量表单首次打开时自动把三字段收进去一次。

## 数据形态

```jsonc
// 子表单 rule（config_json.subForms[bindingId].rule）
[
  { "type": "input", "field": "main_id" },
  { "type": "input", "field": "name" },
  {
    "type": "miAssignment",
    "_miAdopted": true,          // 一次性收纳标记，见「关键约束 3」
    "children": [                 // 顺序 = 作者拖拽结果，不是契约顺序
      { "type": "lookup", "field": "assignee" },
      { "type": "select", "field": "bu_code" },
      { "type": "select", "field": "role_code" }
    ]
  }
]
```

字段本身仍是**独立的 rule**，保留各自的属性面板（Assignee 是 lookup，配置很重：
表 id、搜索列、回填视图），所以没有把它们硬编码进组件内部。

## 四条渲染路径（改动必须四处同时考虑）

| # | 路径 | 入口 | 该路径的职责 |
|---|------|------|--------------|
| 1 | DW 设计器画布 | `useTableFieldRules.buildEffectiveSubFormConfig` | 拖拽编辑；展示全部字段（不按 mode 过滤） |
| 2 | DW Form Preview 行弹窗 | `useFormPreviewBuild` → `SubTableFormDialog` | 演示交互；按 mode 过滤；BU/Role 用**假数据** |
| 3 | Portal 行弹窗（Add/Edit） | `SubTableAddDialog` | 真实录入；BU→Role 级联查 admin-center |
| 4 | Portal 子表列表单元格 | `SubTableField` | role 分派行的 Assignee 列回退显示角色名 |

路径 3、4 覆盖 **发起流程 / To Do / My Requests(Completed)** 三个页面——
它们共用同一套组件，`stampAssignmentConfigsOnForms` 分别在
`views/processes/start.vue`、`composables/taskDetail/useTaskDetailFuLoader.ts`、
`composables/applicationDetail/useApplicationDetailLoaders.ts` 调用。

## 关键约束（踩过的坑，改动前必读）

### 1. `input: false` 的自定义组件收不到任何 `rule.props` / `rule.on`

fc-designer 里声明 `input: false` 的组件（`miAssignment` / `subTable` / `linkForm`），
form-create **完全不转发** `rule.props` 和 `rule.on`。运行时实测组件只收到：

```
["onFc.updateValue", "onFc.el", "class", "id", "style"]
```

所以「当前 mode」和「切换回调」只能走 **provide/inject**（`MI_ASSIGNMENT_MODE_KEY`），
这也是该 widget 拿 BPMN 契约（`MI_ASSIGNMENT_CONFIG_KEY`）的同一条通道。
另注：回调 prop 不要用 `on*` 前缀，Vue 会当成 emit 监听器截走。

widget 用 `inject` 是否存在来判断自己在**画布**还是**运行时**（`isDesignCanvas`）：
画布上全字段展示、卡片变静态说明；运行时才按 mode 切换。

### 2. 自定义容器必须登记到 Portal 的展平白名单

`subFormCanvasColumns.ts` 的 `flattenSubFormRuleLayoutContainers` 用
`LAYOUT_CONTAINER_TYPES` 白名单展平布局容器。**未登记的容器会被整体保留**，
而它自身没有 `field`，随后被 `isDialogMappableSubFormRule` 过滤掉——
**children 一起陪葬**。这曾导致 Portal 弹窗只有 4 个字段、assignee/BU/role 全不见。

> 今后新增任何「持有 children 的自定义容器组件」，都要同步加进这个白名单。

### 3. 自动收纳必须是**一次性**的（`_miAdopted`）

存量表单没有容器 rule，加载时会自动把三字段收进去。但如果每次加载都收，
作者把字段**拖出去**后下次打开又被吸回来，静默覆盖布局。
`_miAdopted` 标记在「首次收纳」和「新建容器」时写入，之后成员与顺序完全归作者。

同理，**preview 不得按契约重排 children**——已在容器内的保持作者顺序，
只把仍散在外面的追加进去。

### 4. 门控一律看 BPMN 契约，不看 marker 是否存在

`isAssignmentConfigured(assignmentConfig)` 即可渲染。曾经要求
`hasMiAssignmentMarker(formFields)`，导致：
- 弹窗：存量表单（无 marker）不渲染区块
- 列表：`rowRoleCode` 恒为空 → role 分派行 Assignee 列显示「未分派」而不是角色名

marker/容器只决定**放在哪**，不决定**是否渲染**。

### 5. 切换 mode 不得引起布局抖动

两种 mode 字段数不同（1 vs 2）、标签宽度不同（"Business Unit" > "Assignee"），
`labelWidth: 'auto'` 会按**当前可见字段**重算，导致：
- 弹窗整体高度变化
- 其它字段输入框左边缘整体平移

修法：
- **高度**：给字段区/收尾行预留较高分支的高度（画布豁免——那里要能随拖入字段增高）。
- **宽度**：用隐藏量尺按**真实 label 字体**测出两种 mode 下最宽的标签，作为
  label 列**下限**；`auto` 在下限之上仍生效，长标签不会被截断或折行
  （守 `portal-dialog-form-labels` 规则）。
  - 不要按字符数估算宽度——不同字体/语言会差几像素导致折行。
  - 不要测「已渲染的 label」——`auto` 模式下 Element Plus 会给 label 写**内联宽度**，
    读到的是被约束后的值，永远追不上。
  - Element Plus 给 label 设了 `min-width: max-content`，覆盖它需要额外的
    `.el-form` 特异性 + `!important`。

## 代码落点

**共用契约**（两份镜像，改一处必须同步另一处）
- `frontend/developer-workstation/src/utils/miAssignmentConfig.ts`
- `frontend/user-portal/src/utils/miAssignmentConfig.ts`
- 关键函数：`isAssignmentConfigured` / `fieldsHiddenByMode` / `fieldsOwnedByMode` /
  `assignmentChildFieldOrder` / `nestAssignmentFieldsIntoContainer`（DW 侧）

**DW**
- `src/main.ts` —— `addDragRule('miAssignment')`：`drag:true` / `mask:false`（子组件可选中）
- `src/components/designer/MiAssignmentPlaceholderWidget.vue` —— 卡片选择器 + drop 区 + 空态
- `src/components/designer/SubTableFormDialog.vue` —— preview 行弹窗；mode 过滤、label 下限
- `src/composables/formDesigner/useTableFieldRules.ts` —— 画布加载时收纳（`createIfMissing`）
- `src/composables/formDesigner/useFormPreviewBuild.ts` —— preview 加载时收纳

**Portal**
- `src/components/SubTableAddDialog.vue` —— 区块、卡片、稳定 label 宽度
- `src/components/SubTableField.vue` —— 列表 Assignee 列的 role 回退（`rowRoleCode`）
- `src/components/subTableAddDialogHelpers/subFormCanvasColumns.ts` —— 展平白名单
- `src/components/subTableAddDialogHelpers/dialogFormLayout.ts` —— 区块首次放置
- `src/components/formRendererHelpers/formRendererRuleParsing.ts` —— 下钻容器 children

**i18n**（两应用各 en / zh-CN / zh-TW）
`subTable.assignMode` / `assignByPerson` / `assignByRole` / `assignByPersonHint` /
`assignByRoleHint` / `sharedRole`；DW 另有 `form.miAssignmentTitle` /
`miAssignmentDropHint` / `miAssignmentOwnedFieldsNote`。

## 排查手册

| 症状 | 先查 |
|------|------|
| 弹窗少了 assignee/BU/role | 容器类型是否在 `LAYOUT_CONTAINER_TYPES`（约束 2）。**对比表格列 vs 弹窗字段**：表格有、弹窗没有 → dialogColumns 解析链问题，不是 config 没到 |
| 区块整个不显示 | 是否还在要求 marker（约束 4）；`assignmentConfig` 是否为空 |
| 点卡片没反应 | mode 是否还想走 `rule.props`/`rule.on`（约束 1）。prod 构建读不到 Vue 实例，用 `window.__xxx` + Playwright `evaluate` 定位 |
| 拖出去的字段又回来了 | `_miAdopted` 是否丢了（约束 3） |
| 切 mode 布局抖动 | 约束 5 |
| Assignee 列空白 | `rowRoleCode` 取不到值 → 多半是约束 4 |

**注意**：抓网络原始 JSON 看到 `assignmentConfig=null` 是**假象**——
`stampAssignmentConfigsOnForms` 是在内存对象上做的，网络响应是 stamping 前的快照。

**截图验证登录**：一律用 `developer/password` 直连，不要走 SSO
（headless 下会弹回登录页）。见技能 `verify-ui-fix-with-screenshot`。

## 验证状态（截至 2026-07-30）

已量化验证：
- DW Preview：模式切换 弹窗高度 delta **0**（原 +40）、label 偏移 **0**（原 8px）
- Portal 发起流程：高度 delta **0**（原 +22）、label 偏移 **0**（原 29px）
- Portal 弹窗字段：person → `[Assignee]`，role → `[Business Unit, Role Code]`
- DW 画布：三字段在容器内，`looseOutside: []`

**未验证（环境限制）**：
- **To Do / Completed 两页**——`act_ru_task`、`act_hi_procinst` 均为空，
  环境里没有流程实例可打开。这两页与发起流程页共用组件，修复理应生效但无截图佐证。
- **列表 Assignee 列显示 role**——没能造出带 role 的真实行数据，
  目前仅有单元测试与代码推理支撑。
- **画布拖入/拖出手感**——Playwright 模拟拖拽对 vuedraggable 不生效，
  属测试手段限制，非功能证伪。
