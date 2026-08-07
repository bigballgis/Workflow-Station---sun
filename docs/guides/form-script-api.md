# 表单脚本 API — Form Event / Component Event / Portal 运行时

本文档说明 **Workflow Station** 中表单在**设计时**与 **User Portal 运行时**的脚本能力：隐藏、取值、赋值，以及 **表单级（Form event）** 与 **控件级（Component event）** 的配置与执行约定。实现以 [form-create](https://www.form-create.com/) / `@form-create/designer` 契约为基础，门户侧由 `PortalFormApi` 统一注入。

| 场景 | 配置位置 | 存储键 | 运行时 API |
|------|----------|--------|------------|
| **设计时固定隐藏** | Form Design → Props **Hide** / Basis **Hidden** | `rule.hidden` / `_hidden` | 不执行脚本，加载时跳过渲染 |
| **表单级脚本** | 画布根 → **Form** → **Form event** | `config_json.options.*` | `options` / `api`（`PortalFormApi`） |
| **控件级脚本** | 选中控件 → **Component** → **Event** | 画布 `_on` / `_hook`；保存 `on` / `hook` | `api` + `rule` / `self`（同 `PortalFormApi`） |
| **默认空事件** | 拖入 / 加载 / 选中 / 保存前补齐 | 同上 | 空函数体不执行（`isEmptyFormCreateHandler`） |
| **声明式联动** | Action Design → 业务逻辑 | 引擎配置 | `BusinessLogicEngine`（非本文 API） |

---

## 1. 数据存储结构

保存表单时，`dw_form_definitions.config_json` 典型结构：

```json
{
  "rule": [
    {
      "type": "switch",
      "field": "legal_hold",
      "title": "Legal Hold",
      "on": {
        "change": "$FNX:",
        "blur": "$FNX:",
        "focus": "$FNX:"
      },
      "hook": {
        "load": "$FNX:",
        "mounted": "$FNX:",
        "value": "$FNX:",
        "deleted": "$FNX:",
        "watch": "$FNX:",
        "hidden": "$FNX:",
        "titleClick": "$FNX:"
      }
    }
  ],
  "options": {
    "form": { "labelPosition": "left" },
    "onChange": "[[FORM-CREATE-PREFIX-function onChange(field, value, options) {\n}-FORM-CREATE-SUFFIX]]",
    "onSubmit": "[[FORM-CREATE-PREFIX-function onSubmit(formData, api) {\n}-FORM-CREATE-SUFFIX]]"
  },
  "subForms": {
    "binding-uuid": { "rule": [], "options": {} }
  },
  "subTablePortalViews": {}
}
```

| 键 | 说明 |
|----|------|
| `rule` | 画布组件树；标量字段必须有 **`field`**（流程变量 / `formData` 键） |
| `rule.on` | 控件 DOM / 交互事件（**保存后** `getRule()` 导出键） |
| `rule.hook` | 控件生命周期（**保存后**；门户与 `rule._hook` 均识别） |
| `rule._on` / `rule._hook` | **仅设计器画布内存**；Event 面板绑定 `_on`，角标统计 `_on` + `_hook` |
| `options` | 表单级 **Form event**；主表门户主要执行 `onChange`；**子表弹窗**见 §3 全表 |
| `subForms` | 子表 / Link Form 内嵌表单的 `rule` + `options`，控件事件语义与主表相同 |

### 1.1 事件序列化格式（两种）

| 层级 | 设计器编辑器 | 典型持久化字符串 | 空函数判定 |
|------|--------------|------------------|------------|
| **Form event** | `FnConfig`（具名 `function onChange(...)`） | `[[FORM-CREATE-PREFIX-function onChange(field, value, options) { }-FORM-CREATE-SUFFIX]]` | 花括号内无代码 |
| **Component event** | `EventConfig` + `FnEditor`（仅函数体） | `$FNX:` 或 `$FNX:\n{body}\n` | 前缀后无代码 |

门户 `parseFormCreateEventHandler` **同时支持**上述两种格式；控件级另支持将历史误存的 `[[FORM-CREATE-PREFIX-function …]]` 规范为 `$FNX:`（设计器种子逻辑）。

### 1.2 设计器 `loadRule` / `parseRule` 键名转换

fc-designer 在内存与导出之间做别名转换（见 `FcDesigner.vue` `loadRule` / `parseRule`）：

```
保存 JSON (on, hook)  ──loadRule──►  画布 (_on, _hook)  ──parseRule/getRule──►  保存 JSON (on, hook)
```

| 导出 / DB | 画布（编辑态） | Event 面板读取 |
|-----------|----------------|----------------|
| `rule.on` | `rule._on` | `modelValue` = `activeRule._on` |
| `rule.hook` | `rule._hook` | 合并为 `hook_load` 等显示名 |

**Workflow Station 种子逻辑**（`formCreateDefaultEvents.ts`）按上下文写入正确键：

- 有 `_fc_id` / `_menu` 的画布节点 → **`_on` + `_hook`**
- `getRule()` 返回、保存前 walk 的树 → **`on` + `hook`**

### 1.3 字段默认值（Table Design / Form Basis）

| 来源 | 存储位置 | 预览 / 门户 |
|------|----------|-------------|
| **Table Design** → Default Value | `dw_field_definitions.defaultValue` | **Form Design 画布**：打开表单 / `setRule` 前 `hydrateDesignerRulesFromLatestTableDefaults`（`tableOverridesRule`，覆盖陈旧 `rule.value`）；**预览**：`handlePreview` 同上；**保存**：`walkRulesApplyTableFieldDefaultsToPersistedRules` 写入 `rule.value` 供门户读取 |
| **Form Design** → Basis → Default value | 部分控件无此项；有则 `rule.value` / `rule.props.value` | `resolveRuleDefaultValue` → `seedFormDataFromRules` |

实现：`frontend/*/src/utils/formCreateRuleDefaults.ts`（设计器与门户各一份，需保持同步）。

**Select / Radio：** 默认值应使用选项的 **value**（如 `1`），或填写与 **label** 完全一致的文案；任意字符串（如 `test select`）若既不匹配 value 也不匹配 label，下拉框会保持空白。

**门户：** `convertFormCreateRule` 调用 `applyRuleDefaultToFormField`；`FormRenderer.initFormData` 在 `modelValue` 为空时应用 `field.defaultValue`。

**导出/导入后 Event 脚本：** 加载表单时 `inflateComponentEventsForDesigner` 将持久化的 `on`/`hook` 复制到 `_on`/`_hook`，供设计器 Event 面板显示。

### 1.4 MI Assignment Mode 编排组件

`miAssignment` 是采集子表 `subForms[bindingId].rule` 中的无数据编排节点，不含 `field`，也不保存
BPMN 配置。唯一配置源是对应 MI 内层 UserTask 的 `assigneeMode`、`assigneeField`、`roleField`、
`buField`、`subTableName` 扩展属性。

- `both`：组件位置渲染“按个人 / 按角色”radio，并按 BPMN 字段名显隐两组已有控件。
- `user` / `role`：不渲染 radio，只显示该模式对应的已有控件。
- 未配置：设计态显示告警，Portal 运行时不渲染。
- BPMN 已配置但目标子表缺少组件：设计器保存与部署校验阻断。

**Breaking change（D-3）**：运行时不再根据 `assignee` / `bu_code` / `role_code` 等固定列名推断
Assignment Mode，也不自动迁移存量表单。已有 MI 采集子表必须在 Form Design 中拖入
`miAssignment` 后重新保存、部署。

---

## 2. 运行时 API（`PortalFormApi`）

在 **Form event** 与 **Component event** 中，`api` 与表单级脚本里的 `options` **为同一对象**（参数名可混用）。

| 方法 | 说明 |
|------|------|
| `getValue(field)` | 读取当前表单某字段值 |
| `setValue(field, value)` | 设置单个字段 |
| `setValue({ field1: v1, field2: v2 })` | 批量设置 |
| `hidden(status, field?)` | `hidden(true, 'x')` 从 DOM 移除；`hidden(false, 'x')` 显示 |
| `display(status, field?)` | `display(true, 'x')` 不占位隐藏（CSS）；`display(false, 'x')` 显示 |
| `hiddenStatus(field)` | 是否被 `hidden()` 隐藏 |
| `displayStatus(field)` | 是否处于 `display()` 隐藏态 |
| `setFieldError(field, message)` | 在字段下方显示红色校验文案（门户 `FormRenderer`） |
| `clearFieldError(field)` | 清除该字段由脚本注入的错误 |
| `form` | 只读对象，等同于当前整表 `formData` |

**字段名解析**：`getValue` / `setValue` / `hidden` / `display` 可使用绑定键 **`field`** 或设计器标题 **`title`/label**；不一致时解析到实际 key（`createFieldKeyResolver`）。

**实现**：`frontend/user-portal/src/utils/formCreateEventRuntime.ts`、`FormRenderer.vue`。

### 2.1 隐藏某一个控件

```javascript
function (field, value, options) {
  const legalHold = field === 'legal_hold'
    ? value
    : options.getValue('legal_hold')

  const showLegalHold = legalHold === true || legalHold === 1 || legalHold === '1'
  options.hidden(!showLegalHold, 'legal_hold')
}
```

| API | 行为 |
|-----|------|
| `options.hidden(true, 'legal_hold')` | 运行时从页面移除该控件 |
| `options.hidden(false, 'legal_hold')` | 重新显示 |
| `options.hidden(true)` | 省略第二参数时作用于**当前表单所有字段键**（慎用） |
| `options.hidden(true, ['a', 'b'])` | 同时隐藏多个字段 |

**`hidden` vs `display`**：`hidden` 不渲染 DOM；`display` 仍占位、CSS 隐藏。

**设计时固定隐藏**：Props → **Hide**（`rule.hidden: true`）或 Basis → **Hidden**（`_hidden`）。门户仍会解析该字段并默认隐藏（保留在布局树中）；用 **`options.hidden(false, 'field')`** 可在运行时显示（Form event `onChange` / 初次 `__bootstrap__`）。

### 2.2 获取某一个字段的值

```javascript
function (field, value, options) {
  const caseNo = options.getValue('case_number')
  const hold = options.getValue('legal_hold')
  const hold2 = options.form.legal_hold
}
```

- 当前变更字段可直接用参数 `value`；读**其他字段**用 `getValue`。
- 初次加载会执行一次表单 `onChange`：`field === '__bootstrap__'`，`value === null`。

### 2.3 给某一个控件设置值

```javascript
function (field, value, options) {
  if (field === 'case_number') {
    options.setValue('legal_hold', false)
  }
  options.setValue({ case_number: 'CASE-001', legal_hold: true })
}
```

赋值合并进 `formData` 并触发 `update:modelValue`（可写场景）。支持按 label 解析键名。

### 2.4 跨字段校验：开始日期早于结束日期

在 **Form → Form event → `onChange`** 中写（任一侧日期变更都会触发）。字段名改成你表单里的 **Field**（示例 `startdate` / `enddate`）：

```javascript
var start = api.getValue('startdate')
var end = api.getValue('enddate')
if (!start || !end) {
  api.clearFieldError('enddate')
  return
}
var s = new Date(String(start)).getTime()
var e = new Date(String(end)).getTime()
if (isNaN(s) || isNaN(e)) {
  api.clearFieldError('enddate')
  return
}
if (s >= e) {
  api.setFieldError('enddate', 'Start date must be before end date.')
} else {
  api.clearFieldError('enddate')
}
```

也可挂在 **startdate** / **enddate** 的 **`change`** 上，函数体相同，把 `api` 写成 `$inject.api`。

日期控件请用 **`change`**，不要用 **`blur`**。提交时 `FormRenderer.validate()` 会检查脚本错误并**阻止提交**（与 Element Plus / Action Design 校验并列）。

**门户显示**：`setFieldError` 写入 `scriptFieldErrors` 并在对应 `el-form-item` 下渲染红字（`data-field-key` 与字段 **Field** 一致）。

---

## 3. 表单级 Event（Form → Form event）

存储在 `config_json.options`。`FORM_LEVEL_EVENT_DEFS` 会为每一项写入**默认空具名函数**。

| 事件名 | 设计器参数 | 主表 FormRenderer | 子表 Add/Edit 弹窗（`SubTableAddDialog`） |
|--------|------------|-------------------|------------------------------------------|
| `onChange` | `field, value, options` | **是** — 每次字段变更 + 挂载时 `__bootstrap__` | **是** — 同左（`subForms[bindingId].options`） |
| `onSubmit` | `formData, api` | 否 | **是** — 校验通过后、写入行前 |
| `onReset` | `api` | 否 | **是** — 关闭弹窗清空模型前 |
| `onCreated` | `api` | 否 | **是** — 打开弹窗 bootstrap |
| `onMounted` | `api` | 否 | **是** — 打开弹窗 bootstrap（nextTick） |
| `onReload` | `api` | 否 | **是** — 弹窗仍打开时切换 `mode` / `initialData` 重初始化 |
| `beforeSubmit` | `formData, data` | 否 | **是** — 校验后；返回 `false`、抛错或解析失败则中止保存 |
| `beforeFetch` | `config, data` | 否 | **N/A** — 弹窗无 form-create 远程 fetch 管道 |

子表 Form event 存储在 `config_json.subForms[bindingId].options`（与主表 `config_json.options` 同形）。Preview 经 form-create 原生执行；Portal 弹窗由 `useSubTableDialogComponentEvents` 对齐上述生命周期。

**`onChange` 示例**（跨字段联动）

```javascript
function (field, value, options) {
  if (field === 'case_number') {
    const n = value === 1 || value === '1' || value === true
    if (n) options.setValue('legal_hold', true)
  }
}
```

---

## 4. 控件级 Event（Component → Event）

### 4.0 设计器 UI 与默认种子

| UI | 行为 |
|----|------|
| **Event** 行 + 橙色角标 | 统计 `activeRule._on` 键数 + `activeRule._hook` 键数 |
| **Edit** 弹窗左侧列表 | 已存在的 `change`、`hook_load` 等（空 `$FNX:` 也会列出） |
| **Create** 下拉 | 按控件 `type` 追加 `event` 列表中的 DOM 事件，或 7 个 `hook_*` 生命周期 |

**自动补齐时机**（`FormDesigner.vue` + `formCreateDefaultEvents.ts`）：

| 时机 | 机制 |
|------|------|
| 从 DB 加载表单 | `walkRulesEnsureComponentEvents` → `setRule` → `patchDesignerRulesDefaultEvents` |
| 拖入新控件 | `config.updateDefaultRule`（按 `switch`、`input` 等类型） |
| 选中控件 | `config.beforeActiveRule` |
| 画布结构变更 | `@active` / `@change-field` → `patchDesignerRulesDefaultEvents` |
| 手动保存 | `handleSaveForm` 内再次 `walkRulesEnsureComponentEvents` |

仅含 **`field`** 的 rule 会进入门户事件索引；`elCard`、`subTable`（无 field）等布局节点不参与门户字段级回调。

### 4.1 生命周期 Hook（`hook` / `_hook`）

设计器 **Event** 面板显示名带 `hook_` 前缀；**保存键**无此前缀。

| 设计器显示 | 保存键 `hook.*` | 画布键 `_hook.*` | 门户执行 |
|------------|-----------------|------------------|----------|
| `hook_load` | `load` | `load` | **是** — `FormRenderer` 挂载后全字段 |
| `hook_mounted` | `mounted` | `mounted` | **是** — 在 `load` 之后 |
| `hook_value` | `value` | `value` | **是** — 字段变更时，在 `on.change` 之后 |
| `hook_hidden` | `hidden` | `hidden` | 否 |
| `hook_watch` | `watch` | `watch` | 否 |
| `hook_deleted` | `deleted` | `deleted` | 否 |
| `hook_titleClick` | `titleClick` | `titleClick` | 否 |

默认空函数：`$FNX:`（设计器 `FnEditor` body 模式）。

### 4.2 交互事件 `on` / `_on`（按控件 type）

由 `getComponentEventNamesForRule` 决定种子列表；各 type 在类型专属事件基础上**均追加** `blur`、`focus`（布局类为 `click` + 全套 hook）。

| type | 默认种子 DOM 事件（`on`） |
|------|---------------------------|
| `input` / `textarea` / `password` | `change` |
| `inputNumber` / `radio` / `checkbox` / `switch` / `slider` / `rate` | `change` |
| `select` | `change`, `removeTag`, `visibleChange` |
| `datePicker` | `change`, `calendarChange`, `panelChange` |
| `timePicker` | `change` |
| `dateRange` | `change`, `calendarChange` |
| `timeRange` | `change` |
| `cascader` | `change`, `expandChange`, `removeTag` |
| `upload` | `remove`, `preview`, `error`, `progress`, `exceed` |
| `elTreeSelect` | `change`, `removeTag` |
| `tree` | `nodeClick`, `checkChange`, `nodeExpand`, `nodeCollapse` |
| `elTabs` | `tabClick`, `tabChange`, `tabAdd`, `tabRemove` |
| `elTransfer` | `leftCheckChange`, `rightCheckChange` |
| `lookup` / `subTable` / `linkForm` | `change` |
| 布局（`elCard`、`fcRow`、`col` 等，无 `field`） | `click` |

**门户当前执行**：

| 事件 | 门户行为 |
|------|----------|
| `on.change` / `hook.value` | 控件 `v-model` 变更时执行 |
| `on.blur` | **input / textarea / password / money** 等已绑 DOM 失焦；**select / radio / 日期 / 数字 / user / 树选等** 在**改值时同步执行** `on.blur`（避免只写 blur 不生效） |
| `on.focus`、`click`、`removeTag`、`visibleChange`、upload 的 `remove`/`preview`/… | **未执行**（设计器可配，门户未接 DOM） |
| `hook.load` / `hook.mounted` | 表单挂载时执行 |
| `hook.hidden` / `hook.watch` / … | **未执行** |

**Form Preview（设计器 Preview 弹窗）**：通过 `formCreatePreviewEvents.ts` 将 `$FNX:` 绑定到 form-create 的 `on.blur` / `on.change`；`change` 回调同时执行 `hook.value`（与门户 `handleFieldChange` 链一致）。保存表单后 Preview 与门户行为应对齐。

### 4.3 脚本参数与编辑器写法

设计器 **Component → Event → Edit** 中只编辑**函数体**；保存时自动加 `$FNX:` 前缀。运行时注入参数：

| 注入名 | 含义 |
|--------|------|
| `field` | 绑定键（门户注入） |
| `value` | 当前控件新值（`change` / `hook_value`） |
| `api` / `options` | `PortalFormApi` |
| `rule` / `self` | 当前控件 rule |
| `option` | 表单全局 options |
| `args` | 原始 DOM 回调参数数组 |

**`on.change` 示例**（仅本控件变更时联动）

```javascript
// 设计器 Event 编辑器内只写函数体，例如：
if (rule.field === 'case_number' && (value === 1 || value === '1')) {
  api.setValue('legal_hold', true)
}
```

**`blur` 示例**（失焦时用**最终**输入值判断，适合 Case Number → Legal Hold）

```javascript
// 挂在 case_number 的 blur 上；删除同字段的 hook_value，避免每键触发
if (value === 'abc') {
  api.setValue('legal_hold', true)
} else {
  api.setValue('legal_hold', false)
}
```

**Select 显隐 `fileupload`（`select === 1` 时隐藏）** — 可写在 **`change`** 或 **`blur`**（门户在 select 改值时会同步跑 `blur` 脚本）：

```javascript
var hide = $inject.value === 1 || $inject.value === '1'
$inject.api.hidden(hide, 'fileupload')
```

选项的 **value** 须与设计器 Options 里配置的一致（显示 `Option02` 时 value 可能是 `'2'` 而非 `1`）。打开表单时若已有值，在同字段加 **`hook_load`**，函数体相同。

**`hook.value` 示例**（每次改值都会触发，不适合「仅失焦后」逻辑）

```javascript
// hook_load / hook_mounted 中可读当前值：
const hold = api.getValue(rule.field)
if (!hold) api.hidden(true, 'investigation_notes')
```

等价完整签名（便于理解，不必手写）：

```javascript
// on.change
function (value, api, rule, self, option, args) { /* 函数体 */ }

// hook.*
function (api, rule, self, option, args) { /* 函数体 */ }
```

### 4.4 门户执行顺序

```
FormRenderer onMounted
  → bootstrapComponentHookEvents('load')   // 各字段 hook.load
  → bootstrapComponentHookEvents('mounted')
  → bootstrapFormOptionsOnChange()         // onChange('__bootstrap__', null)

用户修改字段 key
  → handleFieldChange(key, value)
  → runComponentEventsOnFieldChange
       → rule.on.change
       → rule.hook.value   （config 中为 hook 或 _hook）
  → runFormOptionsOnChange → options.onChange
  → emit('update:modelValue')（若存在表单/控件脚本）
  → isFieldVisible（叠加 BusinessLogicEngine）
```

### 4.5 表单级 vs 控件级 — 如何选

| 需求 | 推荐 |
|------|------|
| 任意字段变化都要处理 | **Form `onChange`** |
| 仅当某字段自己变化 | **Component `change`** 或 **`hook_value`** |
| 输入完成并**失焦**后再处理（避免每键触发） | **Component `blur`**（不要用 `hook_value`） |
| 表单初次加载按初始值显隐 | **Form `onChange`** + `__bootstrap__` |
| 某字段挂载后初始化 | **`hook_load`** / **`hook_mounted`** |
| 仅设计器占位、不需门户 | 可保留空 `$FNX:`；门户见 §4.1–4.2「门户执行」列 |

### 4.6 排查：Event 角标为 0 或弹窗为空

| 现象 | 常见原因 | 处理 |
|------|----------|------|
| 角标 **0** | 画布 rule 无 `_on` / `_hook` | 刷新页面；选中该控件；或保存一次表单 |
| 弹窗左侧无列表 | 事件未写入 `$FNX:` 或写在 `on` 而非 `_on` | 确认已部署含 `beforeActiveRule` 的版本 |
| 保存后门户不执行 | `config_json.rule` 无 `on`/`hook` 或非空体被跳过 | 检查保存结果 JSON；确认 `form-config` 传入 `FormRenderer` |
| 点 **确定** 提示 *Please save the event currently being edited* | 旧版须先点编辑器 **保存** 再点弹窗确定 | 使用含 `HermesEventConfig` 的设计器：**确定** 会自动提交当前编辑中的事件 |
| 关闭 Event 再打开，脚本消失 | 点了底部 **取消**（丢弃本次弹窗内修改） | 内层 **保存** 或 **确定** / 点 **X** 关闭会写入 `_on` / `_hook`；要进库仍需 **保存表单** |

---

## 5. 在设计器中配置

### 5.1 Form event

1. **Developer Workstation → Form Design**。
2. 点击画布空白，右侧 **Form** → **Form event**。
3. 编辑 `onChange`、`onSubmit` 等（具名函数模板）→ **保存表单**。

### 5.2 Component event

1. 选中具体控件（需有 **field**，如 `legal_hold`）。
2. 右侧 **Component** → **Event** → **Edit**。
3. 左侧选择 `change`、`hook_value` 等 → 右侧**只写函数体**（不要粘贴整段 `function hook_value(...) { }`；若已粘贴，点 **保存** 会自动剥掉外层）→ 点编辑器 **保存** 或弹窗 **确定** / **X** 关闭（三者都会写入画布；底部 **取消** 才丢弃）→ **保存表单**。

保存后 `config_json.rule[].on` / `hook` 写入 `$FNX:` 脚本；门户读取 `form-config.rule` 执行。

**安全限制**：脚本禁止 `eval`、`import`、`require`、`window`、`document` 等（`containsDangerousFormScript`）。解析失败时跳过并 `console.warn`。

---

## 6. 门户集成（`FormRenderer`）

| Prop | 作用 |
|------|------|
| `form-options` | `config_json.options`（至少 `onChange`） |
| `form-config` | 含 `rule` 的 `config_json`，供控件级事件索引 |
| `form-create-rules` | 可选；优先于 `form-config.rule` |
| `v-model` / `modelValue` | 整表数据 |

| 页面 | 接入 |
|------|------|
| `processes/start.vue` | `:form-options` + `:form-config` |
| `tasks/detail.vue` | `:form-config="mainFormConfig"` 等 |
| `applications/detail.vue` | `:form-config` / `mainFormConfig` |

---

## 7. 设计器画布：Show hidden

顶栏 **Show hidden** 仅影响**设计态预览**，不改变保存的 `rule`：

| Show hidden | Hidden 已勾选控件 |
|-------------|-------------------|
| 关 | 画布不显示 |
| 开 | 显示 + 橙色虚线 **Hidden** 标记 |

实现：`useFormDesignerCanvasChrome`、`formDesignerCanvasChrome.ts`。

---

## 8. 示例合集

### 8.1 Legal Hold 控制其他字段（Form event）

```javascript
function (field, value, options) {
  const hold = field === 'legal_hold'
    ? value
    : options.getValue('legal_hold')

  const on = hold === true || hold === 1 || hold === '1'
  options.hidden(!on, 'investigation_notes')
}
```

### 8.2 初始加载显隐（Form event + bootstrap）

```javascript
function (field, value, options) {
  if (field === '__bootstrap__') {
    const hold = options.getValue('legal_hold')
    options.hidden(!hold, 'investigation_notes')
    return
  }
}
```

### 8.3 单字段变更（Component `change`）

```javascript
if (rule.field === 'case_number' && (value === 1 || value === '1')) {
  api.setValue('legal_hold', true)
}
```

### 8.4 挂载后初始化（Component `hook_load`）

```javascript
if (rule.field === 'legal_hold') {
  const hold = api.getValue('legal_hold')
  api.hidden(!hold, 'investigation_notes')
}
```

---

## 9. 相关源码与测试

| 文件 | 说明 |
|------|------|
| `frontend/developer-workstation/src/utils/formCreateDefaultEvents.ts` | `FORM_LEVEL_EVENT_DEFS`、`TYPE_ON_EVENTS`、`$FNX:` 种子、`buildDesignerUpdateDefaultRule` |
| `frontend/developer-workstation/src/components/designer/FormDesigner.vue` | `beforeActiveRule`、`updateDefaultRule`、`walkRulesEnsureComponentEvents` |
| `frontend/user-portal/src/utils/formCreateEventRuntime.ts` | `PortalFormApi`、Form/Component 解析（含 `$FNX:`） |
| `frontend/user-portal/src/utils/formCreateComponentEvents.ts` | 从 `rule.on` + `rule.hook`/`_hook` 收集并执行 |
| `frontend/user-portal/src/components/FormRenderer.vue` | 运行时调用链 |
| `frontend/developer-workstation/src/utils/__tests__/formCreateDefaultEvents.test.ts` | 默认事件种子（含 `_on`/`_hook` 画布规则） |
| `frontend/user-portal/src/utils/__tests__/formCreateComponentEvents.test.ts` | 控件事件索引 |
| `frontend/user-portal/src/utils/__tests__/formCreateEventRuntime.test.ts` | `PortalFormApi` |

---

## 10. 与 Action Design 业务逻辑的关系

**Action Design** 条件显隐、公式、联动由 `BusinessLogicEngine` 与脚本 **并行**：先应用引擎 `visibilityChanges`，再叠加 `hidden()` / `display()`。

简单「当 A=1 隐藏 B」可优先声明式配置；复杂跨字段逻辑使用 **Form / Component event API**。
