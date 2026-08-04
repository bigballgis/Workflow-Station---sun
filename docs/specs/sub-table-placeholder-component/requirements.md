# 需求文档

## 简介

本功能在表单设计器（Form Designer）中新增一个"Sub Table 占位符"扩展组件，用于解决采购工作流（Procurement Workflow）中 Sub Table 占位符与具体 Sub Table 对应关系不清晰的问题。

当前系统中，`sub-table-position-control` 功能已支持在 `config_json.rule` 数组中插入 `type: "subTable"` 的占位符条目，并通过 `_bindingId` 关联具体的 `dw_form_table_bindings`。然而，在表单设计器画布上，该占位符的视觉呈现过于简陋，设计者无法直观地看到它对应的是哪个 Sub Table，也无法快速跳转到对应 Sub Table 的表单设计器页面进行编辑。

本功能通过增强 Sub Table 占位符组件的视觉展示、配置体验和导航能力，让表单设计者能够清晰地建立占位符与 Sub Table 的映射关系，并支持一键跳转到对应 Sub Table 的表单设计器页面。

## 词汇表

- **FormDesigner**：开发者工作站（developer-workstation）中的 Vue 3 组件（`FormDesigner.vue`），用于可视化设计表单并生成 `config_json`。
- **SubTablePlaceholder**：表单 `config_json.rule` 数组中 `type: "subTable"` 的特殊条目，通过 `_bindingId` 引用一个 `dw_form_table_bindings.id`，标记 Sub Table 在表单布局中的内联位置。
- **SubTablePlaceholderWidget**：FormDesigner 画布上渲染 SubTablePlaceholder 的可视化组件，即本功能增强的目标组件。
- **SubTableBinding**：`dw_form_table_bindings` 表中的一条记录，包含 Sub Table 的元数据（名称、描述、列定义等）。
- **BindingId**：`dw_form_table_bindings.id`，用于唯一标识一个 Sub Table 绑定关系。
- **Sub Table 表单设计器页面**：开发者工作站中用于设计特定 Sub Table 列结构的页面，通过路由参数（如 `bindingId`）区分不同的 Sub Table。
- **config_json**：存储在数据库中的表单配置 JSON，包含 `rule` 数组，由表单引擎消费。
- **designerSubBindings**：FormDesigner 中从后端加载的当前表单可用 Sub Table 绑定列表。
- **_bindingId**：SubTablePlaceholder rule 条目中的属性，值为对应 SubTableBinding 的 `id`。

## 需求

### 需求 1：Sub Table 占位符组件的增强视觉展示

**用户故事：** 作为表单设计者，我希望在表单设计器画布上能够直观地看到每个 Sub Table 占位符对应的是哪个 Sub Table，以便我能够清晰地理解表单布局与 Sub Table 的对应关系。

#### 验收标准

1. WHEN 一个 SubTablePlaceholder 已配置 `_bindingId`，THE SubTablePlaceholderWidget SHALL 在画布上显示对应 SubTableBinding 的名称（`tableName` 或 `tableDescription`）。
2. WHEN 一个 SubTablePlaceholder 尚未配置 `_bindingId`，THE SubTablePlaceholderWidget SHALL 在画布上显示明确的未配置提示文字（如"未选择 Sub Table"）。
3. THE SubTablePlaceholderWidget SHALL 以视觉上可区分的样式（如带边框的卡片、特定背景色或图标）在画布上展示，使其与普通表单字段明显区分。
4. WHEN `designerSubBindings` 列表更新时，THE SubTablePlaceholderWidget SHALL 同步更新显示的 Sub Table 名称，确保展示信息与当前绑定数据一致。

---

### 需求 2：Sub Table 占位符的绑定配置

**用户故事：** 作为表单设计者，我希望能够在表单设计器的配置面板中为 Sub Table 占位符选择对应的 Sub Table，以便建立占位符与具体 Sub Table 的明确映射关系。

#### 验收标准

1. WHEN 设计者在画布上选中一个 SubTablePlaceholder，THE FormDesigner SHALL 在右侧配置面板中显示一个下拉选择器，列出当前表单所有可用的 SubTableBinding。
2. THE 下拉选择器 SHALL 以"名称（描述）"或等效的可读格式展示每个 SubTableBinding 的标识信息，而非仅显示数字 ID。
3. WHEN 设计者在下拉选择器中选择一个 SubTableBinding，THE FormDesigner SHALL 将对应的 `id` 写入该 SubTablePlaceholder rule 条目的 `_bindingId` 属性。
4. WHEN 设计者清除下拉选择器的选择，THE FormDesigner SHALL 将该 SubTablePlaceholder rule 条目的 `_bindingId` 设置为 `null`。
5. IF 设计者尝试保存表单时存在 `_bindingId` 为 `null` 或未设置的 SubTablePlaceholder，THEN THE FormDesigner SHALL 显示验证错误提示并阻止保存操作。
6. WHERE 当前表单没有可用的 SubTableBinding，THE 下拉选择器 SHALL 显示空状态提示（如"暂无可用 Sub Table"）。

---

### 需求 3：跳转到对应 Sub Table 的表单设计器页面

**用户故事：** 作为表单设计者，我希望能够通过点击 Sub Table 占位符组件上的跳转按钮，直接导航到对应 Sub Table 的表单设计器页面，以便我能够快速查看或编辑该 Sub Table 的列结构配置。

#### 验收标准

1. WHEN 一个 SubTablePlaceholder 已配置有效的 `_bindingId`，THE SubTablePlaceholderWidget SHALL 在组件上显示一个可点击的跳转入口（如链接按钮或图标按钮）。
2. WHEN 设计者点击已配置 `_bindingId` 的 SubTablePlaceholder 上的跳转入口，THE FormDesigner SHALL 导航到对应 Sub Table 的表单设计器页面，并传递正确的 `bindingId` 路由参数。
3. WHEN 一个 SubTablePlaceholder 尚未配置 `_bindingId`，THE SubTablePlaceholderWidget SHALL 禁用或隐藏跳转入口，防止导航到无效页面。
4. WHEN 跳转操作触发时，THE FormDesigner SHALL 在当前窗口/标签页内直接导航到对应 Sub Table 的表单设计器页面，而不是新开标签页。

---

### 需求 4：Sub Table 占位符在设计器组件面板中的注册

**用户故事：** 作为表单设计者，我希望能够从表单设计器的组件面板中找到并拖拽 Sub Table 占位符组件到画布上，以便我能够在表单布局中的任意位置插入 Sub Table。

#### 验收标准

1. THE FormDesigner SHALL 在组件面板（palette）中提供一个可拖拽的"Sub Table 占位符"条目，归类于扩展组件或布局组件分组下。
2. WHEN 设计者将"Sub Table 占位符"条目拖拽到表单画布上，THE FormDesigner SHALL 在拖放位置插入一个 `type: "subTable"`、`_bindingId: null` 的 rule 条目。
3. THE FormDesigner SHALL 支持在同一表单中插入多个 SubTablePlaceholder，每个占位符可独立配置不同的 `_bindingId`。
4. WHEN 设计者保存表单时，THE FormDesigner SHALL 将每个 SubTablePlaceholder 的 `_bindingId` 序列化到 `config_json.rule` 对应条目中。

---

### 需求 5：占位符与 Sub Table 映射关系的一致性保障

**用户故事：** 作为表单设计者，我希望系统能够检测并提示占位符与 Sub Table 之间的映射异常（如重复绑定、绑定已删除），以便我能够维护表单配置的正确性。

#### 验收标准

1. WHEN 表单中存在两个或多个 SubTablePlaceholder 配置了相同的 `_bindingId`，THE FormDesigner SHALL 在配置面板或画布上显示重复绑定警告提示。
2. WHEN FormDesigner 加载表单配置时，IF 某个 SubTablePlaceholder 的 `_bindingId` 在当前 `designerSubBindings` 列表中不存在，THEN THE FormDesigner SHALL 在该占位符上显示"绑定已失效"的警告标识。
3. THE SubTablePlaceholderWidget SHALL 区分"未配置"（`_bindingId` 为 null）和"绑定失效"（`_bindingId` 有值但对应 binding 不存在）两种异常状态，并以不同的视觉样式展示。

---

### 需求 6：与现有 Sub Table 位置控制功能的兼容性

**用户故事：** 作为开发者，我希望新增的 Sub Table 占位符组件增强功能与现有的 `sub-table-position-control` 功能完全兼容，以便现有表单配置无需迁移即可正常工作。

#### 验收标准

1. THE SubTablePlaceholderWidget SHALL 使用与 `sub-table-position-control` 规范中定义的相同 rule 数据结构（`type: "subTable"`、`_bindingId`），不引入新的 rule 字段或破坏性变更。
2. WHEN FormRenderer 渲染包含 SubTablePlaceholder 的表单时，THE FormRenderer SHALL 继续按照 `sub-table-position-control` 规范中定义的逻辑进行内联渲染，本功能的变更仅限于 FormDesigner 侧的展示与交互。
3. WHEN 现有表单的 `config_json` 中已包含通过其他方式（如 SQL 脚本）写入的 `subTable` rule 条目，THE FormDesigner SHALL 能够正确加载并展示这些条目，不丢失 `_bindingId` 数据。
