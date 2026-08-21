# Inline Form 组件（`inlineSubForm`）：把子表的表单直接内嵌进主表单

本文描述 **Inline Form**（`inlineSubForm`）表单组件：把某张 SUB 表设计好的表单**原地铺开**在主表单画布上，
不经链接、不开弹窗、上方也没有表格。

> **给后续 agent**：这个组件横跨两个前端应用、多条渲染路径，且踩在几个**反直觉的既有约束**上
> （PK 分配时序、四个解析分叉、无自嵌套守卫）。改它之前请先读「关键约束」一节——
> 大部分坑不看文档必然复现。
>
> 同类组件的既有经验见 [mi-assignment-mode-component.md](./mi-assignment-mode-component.md)，
> 其「关键约束 1」（`input:false` 只收 5 个 prop）对本组件同样成立。

## 背景与目标

把另一张 SUB 表的表单放进主表单，此前只有两条路，都不满足「落在我指定的位置」：

| 既有方案 | 形态 | 为什么不够 |
|---|---|---|
| `linkForm`（列上的 Link Form） | 表格单元格里一个超链接 → 点开弹窗 | 要点一下才看得到；且必须先有表格 |
| `subTable` + `showFormBelowTable` | 表格**下方**内嵌该子表的表单 | 必须先放 Sub-Table 组件；表格永远在上方；位置固定在表格之后 |

**目标形态**：像拖 Input 一样把组件拖到主表单任意位置，运行时该位置直接铺开目标 SUB 表的表单字段。

**契约**：
- **单行，MI 感知** —— 有 `currentMiRowId`（当前 MI 子任务自己的参与人行）时，渲染/回写该行（按
  `findMiIsolatedParentRow` 用 `id_idw`/`rowId`/`id` 等字段匹配）；无 `currentMiRowId` 时回退第 0 行。
  无数据时渲染空白可编辑表单，首次编辑创建第 0 行。**曾经**硬编码只认第 0 行——MI 集合子表天然多行
  （一个参与人一行），这会导致编辑一个子任务的字段时，实际读/写到另一个参与人的行（见 2026-08-19 修复）。
- **随主表单提交** —— 走既有 `__subTables__` 管道，无独立保存按钮
- **MVP 边界** —— 只支持放在**主表单**画布。放进 SUB 表单画布（嵌套）不支持，理由见「关键约束 3」

## 数据形态

```jsonc
// 主表单 rule（config_json.rule）
[
  { "type": "input", "field": "meeting_id" },
  {
    "type": "inlineSubForm",
    "_bindingId": 294,          // dw_form_table_bindings.id，binding_type = 'SUB'
    "title": "Inline Form",
    "props": { "_bindingId": 294 }   // 仅设计期存在，parseRule 保存时会删掉
  }
]
```

被内嵌的表单本身**不复制**——它仍然只有一份，存在 `config_json.subForms[294].rule`。
组件只持有一个指针（`_bindingId`），运行时靠 `binding.formFields` 取到已解析好的字段树。

`_bindingId` 在**顶层**与 **props** 之间的往返由 drag rule 的 `loadRule`/`parseRule`/`watch` 三件套负责，
落库后只保留顶层——这与 `subTable` 完全一致，是 `input:false` 组件唯一可用的传参通道。

## 渲染路径（改动必须同时考虑）

| # | 路径 | 入口 | 说明 |
|---|------|------|------|
| 1 | DW 设计器画布 | `InlineSubFormPlaceholderWidget` | 占位芯片，三态 unconfigured / valid / stale |
| 2 | DW Form Preview | `useFormPreviewBuild` → `FormPreviewItems`（`kind: 'inlineSubForm'`） | 复用 `kind:'fields'` 那套 `<form-create>` 包装 |
| 3 | Portal 运行时 | `FormRendererFields` → `SubTableInlineForm` | 三个页面共用：New Request / To Do / My Request |

第 2 条另有一个**独立的第二实现** `src/utils/savedFormPreviewBuilder.ts`（保存态预览）。
它与 `useFormPreviewBuild` **已经彼此漂移**（缺 hidden 守卫、不透传 allowAdd/Edit/Delete、
布局递归条件更窄），改动时按各自现状加分支，不要机械对拷。

## 关键约束（踩过的坑，改动前必读）

### 1. 解析层静默丢弃：四个分叉必须同时改

`extractFieldsRecursive` 有**四份**实现，兜底都是 `if (item.field)`。
没有 `field`、又没有专属分支的组件类型会被**无声丢掉**——不报错、不告警，Portal 上什么都不显示。

现有 `linkForm` **拖拽组件**就是这样死的：DW 画布能放能配，运行时永远不出现。

四处：

| 文件 | 备注 |
|---|---|
| `components/formRendererHelpers/formRendererRuleParsing.ts` | 共享副本，用提升的局部变量 `props` |
| `composables/taskDetail/useTaskDetailFieldExtraction.ts` | |
| `composables/applicationDetail/useApplicationDetailFormSchema.ts` | **异类**：分支内层包 `if (!ctx.skipSubTable)`，`continue` 在内层 if 之外 |
| `composables/processStart/useProcessStartFieldExtractor.ts` | |

> 注意：现有的 `extractFieldsRecursiveMiAssignmentHidden.test.ts` 只覆盖**三个**分叉，
> 刻意把 `formRendererRuleParsing.ts` 当参考实现排除在外。但那一份恰恰是本组件的主渲染路径，
> 所以本组件的回归测试测**四个**。

### 2. binding 不登记就不加载

`formRendererSubTableBindings.ts` 的 `collectPlacedSubTableBindingIds`（:42）原本只认 `type === 'subTable'`。
不放行新类型，该 binding 会被 `start.vue` 的 `computeNeededSubTableBindingIds` 过滤出 `subTableBindings`，
于是 `resolveBinding()` 返回 `undefined`，组件静默空白。**这是最容易漏、症状最像"组件坏了"的一处。**

同文件需一并放行的还有 `collectSubTableFieldsFromLayout`(:72)、
`removeSubTableFieldsByBindingIds`(:177)、`collectRuleBindingIds`(:213)。

反过来，`ensureSubTableBindingsOnFormLayout`(:142-165) **保持只合成 `type:'subTable'`**：
改了 :42 之后该 binding 已被视为「已放置」，不会再被追加一个重复的独立表格——防重渲染是免费的副产品。

### 3. 没有任何自嵌套守卫，而本组件把潜在环变成真环

`SubTableBindingSelect` 只按 `bindingType === 'SUB'` 过滤，**没有** `excludeSelf`、没有祖先链检查，
组件甚至不知道自己被渲染在哪个 binding 的设计里。`useFormSave` 的两道守卫也只查 `_bindingId` 存在性与 SUB 类型。

`walkFormCreateRules` 的 `WeakSet` 只防**同一对象引用**被重复访问，防不住 binding 级自引用
（每次展开都是新对象）；`collectSubTableRules` 连 WeakSet 都没有。

`subTable` 至今没炸，是因为它渲染的是**表格**，天然不展开子表单。
**本组件直接展开目标子表单**——binding X 的子表单里放一个指向 X 的 Inline Form，就是无限递归。

故：运行时字段解析带**已访问 bindingId 集合**，命中即返回空并告警；MVP 阶段组件只支持放在主表单画布。

### 4. 主表 PK 在首次编辑时并不存在，且**故意**不提前分配

这条与直觉相反，直接决定写入路径怎么写。

主表 PK 只在两处**惰性**分配：
- 提交时 `ensureMainPrimaryKey`（`start.vue:806`，由 `handleSubmit` 在校验前调用）
- 子表行**保存**时 `finalizeSubTableRowOnSave` → `ensureParentRowsForChildAdd`

而且 Portal 的 Add 弹窗路径显式传 `deferPkAllocationUntilSave: true`——**打开弹窗时故意不分配**。
原因见 `.cursor/rules/form-preview-fk-pk-runtime.mdc` 的 issue **R3**：
Add 时就分配，用户一取消就"烧掉"一个序列号。

**所以本组件绝不能在首次按键时分配 PK 或回填 FK**——那不仅复现 R3，还比弹窗更糟：
每打开一次表单就烧一个号（弹窗至少要用户主动点 Add）。

已核实**提交链路不存在统一的 FK 回填环节**：`buildStartFormSubTablesPayload` 只是原样拷 `b.data`，
后端 `ProcessSubTablePrimaryKeyEnricherComponent` 只管 PK 不管 FK。

**当前实现（MVP）**：`handleInlineSubFormUpdate` 只推行、不碰 PK/FK
（单测 `inlineSubFormComponent.test.ts` 锁死了这一点：断言新建行的键集恰为用户输入的字段）。
对**已存在主记录**的场景（To Do / My Request，主 PK 早已分配）功能完整。

**已知限制**：New Request 首次提交时，若该子表行需要 FK 指回主表，FK 目前不会被自动填上——
因为提交链路没有统一回填环节。若演示/验收发现此场景必须支持，按既定方案接
`finalizeSubTableRowOnSave`（带 `autoEnsurePrimaryRecord`），**触发点放在主表单提交前**、
而非按键时，并把 `primaryFormDataPatch` 经 `ctx.handlePrimaryFormDataPatch` 写回主表单。
绝不可改成按键时分配——那会复现 R3 且更严重。

### 5. `PortalFormFields` 的兜底是"渲染成空壳"，不是丢弃

未识别的 `type` 会落到通用兜底（`:360` / `:378`，按 `inColumn` 二选一），
渲染成一个带 label 的普通表单项而非消失。这与约束 1 的静默丢弃是**两个不同层**：
约束 1 在解析层，这条在渲染层。排查时别混淆。

### 6. `FormRendererFields.vue` 两个 arm 不对称

`el-col` arm 渲染 `SubTableInlineForm` + `RecordNoteField`；`inColumn` arm **只**渲染 `SubTableField`。
本组件两个 arm 都要渲染，否则拖进 Col 布局里就是白板。

（此处「两个 arm」指组件是否被放在 **Col 容器内**，与约束 3 的「不支持放进 SUB 画布」是两回事。）

### 7. ctx 的 provide 处有强制类型转换，漏写不报错

`FormRenderer.vue` 末尾的 `as unknown as FormRendererFieldsContext` 使 provide 对象**不受接口约束**：
在接口里声明了新方法、却忘了加进 provide，**不会编译报错**，只会在运行时变成 `undefined`。
故新增 ctx 方法必须有单测兜底。

### 8. 导入/克隆的 ID 重映射按类型门控

`FormConfigJsonBindingIdRewriter.java` 用字面量 `"subTable"` 门控 `_bindingId` 重映射。
新类型不加进去，FU 导入 / 克隆 / 版本回滚后组件仍指向**源环境**的 bindingId——静默错乱，不报错。

同理 `FormCreateRuleToFieldMapper.SKIP_TYPES` 要加上新类型，否则 Table Design 会为这个纯占位组件建物理列。

## 排查手册

| 症状 | 先查 |
|---|---|
| DW 画布能放能配，Portal 什么都不显示 | 约束 1：四个解析分叉是否都加了分支 |
| Portal 上组件位置空白，但没报错 | 约束 2：`collectPlacedSubTableBindingIds` 是否放行；`resolveBinding` 是否返回 undefined |
| 拖进 Col 里不显示，不在 Col 里正常 | 约束 6：`inColumn` arm 是否漏了 |
| 页面卡死 / 主线程无响应 | 约束 3：是否配成了自引用 binding |
| 属性面板选了表，画布仍显示"未配置" | `withSubTableBindingIdInProps` 是否放行新类型（落库只有顶层 `_bindingId`） |
| Add/Edit 弹窗里多出一个名为 Inline Form 的输入框 | `isDialogMappableSubFormRule` 是否排除新类型 |
| 导入/克隆后绑定错表 | 约束 8：`FormConfigJsonBindingIdRewriter` |
| 序列号莫名跳号 | 约束 4：是否在按键时分配了 PK |

## 相关文件

**DW**：`components/designer/InlineSubFormPlaceholderWidget.vue`、`main.ts`（component + addDragRule）、
`utils/formDesigner.ts`（`collectSubTableRules` / `withSubTableBindingIdInProps`）、
`composables/formDesigner/useFormPreviewBuild.ts`、`utils/savedFormPreviewBuilder.ts`、
`components/designer/{formPreviewTypes.ts,FormPreviewItems.vue,SubTableBindingSelect.vue}`

**Portal**：`composables/formRenderer/useInlineSubFormComponent.ts`（新增）、
`components/formRendererHelpers/{formRendererRuleParsing.ts,formRendererTypes.ts,formRendererSubTableBindings.ts}`、
`components/{FormRendererFields.vue,FormRenderer.vue,SubTableInlineForm.vue,formRendererFieldsContext.ts}`、
上述四个解析分叉

**后端**：`util/FormCreateRuleToFieldMapper.java`、`util/FormConfigJsonBindingIdRewriter.java`
