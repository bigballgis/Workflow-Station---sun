# 表单脚本 API — Form Event / Portal 运行时

本文档说明 **Workflow Station** 中表单控件在**设计时**与**门户运行时**如何隐藏、取值、赋值。实现以 form-create 设计器契约为基础，门户侧由 `PortalFormApi` 统一执行。

| 场景 | 配置位置 | 适用 API |
|------|----------|----------|
| **设计时固定隐藏** | Developer Workstation → Form Design → 控件属性 | `rule.hidden` / Basis **Hidden**（`_hidden`） |
| **运行时动态脚本** | 设计器表单设置 → **Form event** → `onChange` | `options` / `api`（`PortalFormApi`） |
| **运行时声明式逻辑** | Action Design → 业务逻辑（条件显隐、联动等） | `BusinessLogicEngine`（非本文脚本 API） |

---

## 1. 数据存储结构

保存表单时，`dw_form_definitions.config_json` 典型结构：

```json
{
  "rule": [ /* form-create 组件树，每项含 type、field、title、hidden 等 */ ],
  "options": {
    "form": { "labelPosition": "left" },
    "onChange": "[[FORM-CREATE-PREFIX-function (field, value, options) { ... }-FORM-CREATE-SUFFIX]]"
  },
  "subForms": { /* 子表 bindingId → { rule, options } */ },
  "subTablePortalViews": { /* ... */ }
}
```

- **`rule`**：画布组件树；标量字段用 **`field`** 作为流程变量 / 表单数据的键（如 `legal_hold`、`case_number`）。
- **`options.onChange`**：表单级 **Form event** 脚本；User Portal 的 `FormRenderer` 在字段变更时执行（见 `frontend/user-portal/src/utils/formCreateEventRuntime.ts`）。

---

## 2. 运行时 API（`PortalFormApi`）

在 **Form event → onChange** 中，第三个参数为 `options`，与 **`api` 为同一对象**（可混用）。

| 方法 | 说明 |
|------|------|
| `getValue(field)` | 读取当前表单数据中某字段的值（见下文「字段名解析」） |
| `setValue(field, value)` | 设置单个字段 |
| `setValue({ field1: v1, field2: v2 })` | 批量设置多个字段 |
| `hidden(status, field?)` | **动态隐藏**：`hidden(true, 'x')` 从 DOM 移除；`hidden(false, 'x')` 显示 |
| `display(status, field?)` | **动态折叠**：`display(true, 'x')` 不占位隐藏（CSS）；`display(false, 'x')` 显示 |
| `hiddenStatus(field)` | 是否被 `hidden()` 隐藏 |
| `displayStatus(field)` | 是否处于 `display()` 隐藏态 |
| `form` | 只读对象，等同于当前整表 `formData` |

**字段名解析**：`getValue` / `setValue` / `hidden` / `display` 的参数可使用 **绑定键 `field`** 或 **设计器标题 `title`/label**。若 label 与 key 不一致，会解析到实际 key（与 `createFieldKeyResolver` 一致）。

实现参考：

- `frontend/user-portal/src/utils/formCreateEventRuntime.ts` — `createPortalFormApi`
- `frontend/user-portal/src/components/FormRenderer.vue` — `createFormEventApi`、`handleFieldChange`、`isFieldVisible`

### 2.1 隐藏某一个控件

**推荐：运行时按条件隐藏（Form event）**

```javascript
// field：触发变更的字段；value：新值；options / api：PortalFormApi
function (field, value, options) {
  const legalHold = field === 'legal_hold'
    ? value
    : options.getValue('legal_hold')

  const showLegalHold = legalHold === true || legalHold === 1 || legalHold === '1'
  // hidden(true) = 隐藏；hidden(false) = 显示
  options.hidden(!showLegalHold, 'legal_hold')
}
```

| API | 行为 |
|-----|------|
| `options.hidden(true, 'legal_hold')` | 运行时从页面移除该控件（不占位） |
| `options.hidden(false, 'legal_hold')` | 重新显示 |
| `options.hidden(true)` | 省略第二参数时，作用于**当前表单所有字段键**（慎用） |
| `options.hidden(true, ['a', 'b'])` | 同时隐藏多个字段 |

**与 `display` 的区别**（form-create 语义）：

| | `hidden` | `display` |
|---|----------|-----------|
| 隐藏方式 | 不渲染 DOM | 仍渲染，CSS 不可见 |
| 典型用法 | 条件整段去掉字段 | 保留布局占位 |

```javascript
options.display(true, 'case_number')   // 隐藏但占位（少见）
options.display(false, 'case_number') // 显示
```

**设计时固定隐藏（不跑脚本）**

在 Form Design 中选中组件：

- **Props → Hide**：写入 `rule.hidden: true`（保存后进 `config_json.rule`）
- **Basis → Hidden**：设计器内部 `_hidden`（画布「Show hidden」关闭时不显示该控件）

门户加载表单时会跳过 `hidden: true` 的规则项（`isFormCreateRuleHidden` / `extractFieldsRecursive`），**无需**再写 `onChange`。

### 2.2 获取某一个字段的值

```javascript
function (field, value, options) {
  const caseNo = options.getValue('case_number')
  const hold = options.getValue('legal_hold')

  // 或使用整表对象
  const all = options.form
  const hold2 = all.legal_hold
}
```

在 **onChange 回调**里，若当前变更字段就是 `field`，可直接用参数 `value`；读**其他字段**请用 `getValue`。

表单初次加载时会执行一次 onChange（`field === '__bootstrap__'`，`value === null`），用于根据初始数据设置显隐：

```javascript
function (field, value, options) {
  if (field === '__bootstrap__') {
    const hold = options.getValue('legal_hold')
    options.hidden(!hold, 'some_other_field')
    return
  }
  // 正常 onChange 逻辑...
}
```

### 2.3 给某一个控件设置值

```javascript
function (field, value, options) {
  if (field === 'case_number') {
    options.setValue('legal_hold', false)
  }

  // 批量
  options.setValue({
    case_number: 'CASE-001',
    legal_hold: true,
  })
}
```

赋值会合并进 Portal `formData`，并触发 `update:modelValue`（可写表单场景）。

**按 label 赋值**（当绑定键与显示名不同）：

```javascript
// 若界面上 label 为 "Case Number"，绑定键为 case_number，两种写法均可
options.setValue('case_number', 'X')
options.setValue('Case Number', 'X')  // 解析到 case_number
```

---

## 3. 在设计器中配置 Form event

1. 打开 **Developer Workstation → Form Design**。
2. 点击画布空白或表单根节点，打开右侧 **表单设置**（非单个字段）。
3. 找到 **Form event** / **表单事件**，编辑 **`onChange`** 函数体。
4. 保存表单（写入 `config_json.options.onChange`）。

设计器会把函数序列化为 `[[FORM-CREATE-PREFIX-function ...}-FORM-CREATE-SUFFIX]]` 字符串；门户原样解析执行。

**安全限制**：脚本禁止 `eval`、`Function`、`import`、`require`、`window`、`document` 等（见 `containsDangerousFormScript`）。解析失败时静默跳过并 `console.warn`。

---

## 4. 门户中的调用链（便于排查）

```
用户改字段
  → FormRenderer.handleFieldChange(key, value)
  → runFormOptionsOnChange(key, value)  // 若 formOptions.onChange 存在
  → createPortalFormApi(...)            // getValue / setValue / hidden / display
  → isFieldVisible(fieldKey)            // 控制是否渲染
```

父页面通过 `v-model` 获取整表数据，例如：

- 流程发起：`processes/start.vue` — `:form-options="formFormOptions"`
- 待办详情：`tasks/detail.vue` — `formOptions` 来自 `config_json.options`
- 我的申请：`applications/detail.vue` — 同上

---

## 5. 设计器画布：Show hidden

Form Design 顶栏 **Show hidden** 仅影响**设计态预览**，不改变保存的 `rule`：

| Show hidden | Hidden 已勾选的控件 |
|-------------|---------------------|
| 关 | 画布上不显示（便于编辑可见字段） |
| 开 | 显示 + 橙色虚线 **Hidden** 标记 |

实现：`useFormDesignerCanvasChrome` + `syncDesignerHiddenFieldMarkers`（`frontend/developer-workstation/src/utils/formDesignerCanvasChrome.ts`）。

---

## 6. 示例：Legal Hold 控制其他字段

```javascript
function (field, value, options) {
  const hold = field === 'legal_hold'
    ? value
    : options.getValue('legal_hold')

  const on = hold === true || hold === 1 || hold === '1'
  options.hidden(!on, 'investigation_notes')
}
```

---

## 7. 相关源码与测试

| 文件 | 说明 |
|------|------|
| `frontend/user-portal/src/utils/formCreateEventRuntime.ts` | `PortalFormApi` 定义与解析 |
| `frontend/user-portal/src/utils/__tests__/formCreateEventRuntime.test.ts` | 取值、赋值、hidden 用例 |
| `frontend/user-portal/src/components/FormRenderer.vue` | 运行时挂载 API |
| `frontend/developer-workstation/src/utils/formCreateRuleUtils.ts` | 设计态 `hidden` / `_hidden` 判定 |
| `frontend/developer-workstation/src/utils/formDesignerCanvasChrome.ts` | 设计器画布隐藏标记 |

---

## 8. 与 Action Design 业务逻辑的关系

**Action Design** 中的条件显隐、公式、联动由 `BusinessLogicEngine` 在 `FormRenderer` 内与 Form event **并行**生效：先应用引擎 `visibilityChanges`，再叠加 `hidden()` / `display()` 的脚本状态。

若仅需简单「当 A=1 时隐藏 B」，可优先用声明式配置；复杂逻辑再用本文 **Form event API**。
