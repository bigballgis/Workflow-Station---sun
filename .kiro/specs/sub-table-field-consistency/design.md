# Design Document: Sub Table Field Consistency

## Overview

本功能解决 Procurement Workflow RequestItems Sub Table 中两个相关问题：

1. **渲染一致性修复**：`deriveColumnsFromBinding` 将 options 放在顶层 `options` 字段，但 `SubTableAddDialog.vue` 从 `col.props?.options` 读取，导致 radio/checkbox/select 字段在弹窗中无法显示选项。同时 `el-radio` 和 `el-checkbox` 的 value 绑定属性需要统一。

2. **新增控件类型**：在现有 `ColumnType` 枚举中新增 `password`、`timerange`、`treeselect` 三种类型（`radio` 和 `checkbox` 已存在但需修复），并在 SubTableAddDialog、FormRenderer、deriveColumnsFromBinding、buildInitialRow 中同步支持，同时更新数据库字段和 subForm rule。

---

## Architecture

### 问题根因分析

```
deriveColumnsFromBinding (tasks/detail.vue 等)
  ├── 将 options 放在返回对象的顶层: { field, label, type, options: [...], props: {...} }
  └── props 中不包含 options

SubTableAddDialog.vue
  ├── select: 读取 col.props?.options  ← 读不到！
  ├── radio:  读取 col.props?.options  ← 读不到！
  └── checkbox: 读取 col.props?.options ← 读不到！
```

**修复方案**：在 `deriveColumnsFromBinding` 中，将 options 同时写入 `props.options`（保持顶层 `options` 向后兼容），并在 `SubTableAddDialog.vue` 中统一从 `col.props?.options ?? col.options` 读取。

### 变更文件清单

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `frontend/user-portal/src/components/subTableAddDialogHelpers.ts` | 修改 | 新增 `password`、`timerange`、`treeselect` 类型；更新 `buildInitialRow`、`CONTROL_TYPE_MAP` |
| `frontend/user-portal/src/components/SubTableAddDialog.vue` | 修改 | 新增 3 种控件渲染；修复 options 读取路径；修复 radio/checkbox value 绑定 |
| `frontend/user-portal/src/components/SubTableField.vue` | 修改 | 新增 password 掩码显示、timerange 格式化、treeselect label 解析；radio/checkbox/select 显示 label |
| `frontend/user-portal/src/views/tasks/detail.vue` | 修改 | `deriveColumnsFromBinding` 新增类型映射；options 同时写入 `props.options`；新增 prop keys |
| `frontend/user-portal/src/views/applications/detail.vue` | 修改 | 同上 |
| `frontend/user-portal/src/views/processes/start.vue` | 修改 | 同上 |
| `frontend/user-portal/src/components/FormRenderer.vue` | 修改 | 新增 password、timerange、treeselect 控件渲染 |
| `frontend/developer-workstation/src/components/designer/SubTableAddDialog.vue` | 修改 | 与 user-portal 版本同步 |
| `frontend/developer-workstation/src/components/designer/FormDesigner.vue` | 修改 | `deriveColumnsFromBinding` 同步新增类型映射 |
| `deploy/init-scripts/13-procurement-workflow/05-add-new-field-types.sql` | 新增 | 新增数据库字段和 subForm rule 更新 |

---

## Components and Interfaces

### 1. subTableAddDialogHelpers.ts — ColumnType 扩展

```typescript
export type ColumnType =
  | 'text'
  | 'textarea'
  | 'number'
  | 'select'
  | 'radio'
  | 'checkbox'
  | 'switch'
  | 'date'
  | 'datetime'
  | 'upload'
  | 'user'
  | 'department'
  | 'password'    // 新增
  | 'timerange'   // 新增
  | 'treeselect'  // 新增
```

### 2. DialogColumn — props 扩展

```typescript
export interface DialogColumn {
  field: string
  label: string
  type?: ColumnType
  required?: boolean
  placeholder?: string
  minWidth?: number
  options?: Array<{ label: string; value: any }>  // 保留顶层 options（向后兼容）
  props?: {
    // 现有字段...
    options?: Array<{ label: string; value: any }>  // 新增：与顶层 options 保持同步
    treeData?: Array<{ label: string; value: any; children?: any[] }>  // 新增：treeselect 数据
    isRange?: boolean        // 新增：timePicker range 模式
    valueFormat?: string     // 新增：时间格式
    startPlaceholder?: string
    endPlaceholder?: string
    multiple?: boolean       // treeselect 多选
    checkStrictly?: boolean  // treeselect 父子不关联
    [key: string]: any
  }
}
```

### 3. buildInitialRow — 新增类型初始值

```typescript
export function buildInitialRow(columns: DialogColumn[]): Record<string, any> {
  const row: Record<string, any> = {}
  for (const col of columns) {
    switch (col.type) {
      case 'number':    row[col.field] = undefined; break
      case 'switch':    row[col.field] = false; break
      case 'checkbox':  row[col.field] = []; break
      case 'date':
      case 'datetime':
      case 'timerange': row[col.field] = null; break  // 新增 timerange
      case 'treeselect':
        row[col.field] = col.props?.multiple ? [] : ''  // 新增 treeselect
        break
      default:          row[col.field] = ''  // text, textarea, password, radio, select, user, department
    }
  }
  return row
}
```

### 4. SubTableAddDialog.vue — 新增控件渲染

**修复 options 读取路径**（统一使用 `col.props?.options ?? col.options`）：

```html
<!-- select -->
<el-option v-for="opt in (col.props?.options ?? col.options ?? [])" ... />

<!-- radio -->
<el-radio v-for="opt in (col.props?.options ?? col.options ?? [])" :value="opt.value">
  {{ opt.label }}
</el-radio>

<!-- checkbox -->
<el-checkbox v-for="opt in (col.props?.options ?? col.options ?? [])" :value="opt.value">
  {{ opt.label }}
</el-checkbox>
```

**新增 password 控件**：
```html
<el-input
  v-else-if="col.type === 'password'"
  v-model="formData[col.field]"
  type="password"
  show-password
  :placeholder="col.placeholder || col.label"
  clearable
/>
```

**新增 timerange 控件**：
```html
<el-time-picker
  v-else-if="col.type === 'timerange'"
  v-model="formData[col.field]"
  is-range
  value-format="HH:mm:ss"
  :start-placeholder="col.props?.startPlaceholder || 'Start time'"
  :end-placeholder="col.props?.endPlaceholder || 'End time'"
  style="width: 100%"
/>
```

**新增 treeselect 控件**：
```html
<el-tree-select
  v-else-if="col.type === 'treeselect'"
  v-model="formData[col.field]"
  :data="col.props?.treeData || []"
  :multiple="col.props?.multiple"
  :check-strictly="col.props?.checkStrictly !== false"
  :placeholder="col.placeholder || col.label"
  clearable
  style="width: 100%"
/>
```

### 5. SubTableField.vue — 表格列显示增强

新增辅助函数 `resolveDisplayValue`，将存储值转换为可读标签：

```typescript
function resolveDisplayValue(col: Column, rawValue: any): string {
  if (rawValue === null || rawValue === undefined) return '-'
  
  const options = col.props?.options ?? (col as any).options
  
  if (col.type === 'password') return '••••••'
  
  if (col.type === 'radio' || col.type === 'select') {
    if (!options) return String(rawValue)
    const opt = options.find((o: any) => o.value === rawValue)
    return opt ? opt.label : String(rawValue)
  }
  
  if (col.type === 'checkbox') {
    if (!Array.isArray(rawValue) || !options) return String(rawValue)
    return rawValue
      .map((v: any) => options.find((o: any) => o.value === v)?.label ?? v)
      .join(', ')
  }
  
  if (col.type === 'timerange') {
    if (Array.isArray(rawValue) && rawValue.length === 2) {
      return `${rawValue[0]} - ${rawValue[1]}`
    }
    return String(rawValue)
  }
  
  if (col.type === 'treeselect') {
    // For treeselect, the stored value is the node value; display as-is
    // (full label resolution would require traversing treeData)
    return String(rawValue)
  }
  
  return String(rawValue)
}
```

### 6. deriveColumnsFromBinding — 修复与扩展

**关键修复**：将 options 同时写入 `props.options`：

```typescript
// 修复前
return {
  field: r.field,
  label: r.title || r.field,
  type,
  required,
  ...(options ? { options } : {}),
  ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
}

// 修复后：options 同时写入 props.options
if (options) passProps.options = options
return {
  field: r.field,
  label: r.title || r.field,
  type,
  required,
  ...(options ? { options } : {}),  // 保留顶层 options（向后兼容）
  ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
}
```

**新增类型映射**：

```typescript
if (r.type === 'input') {
  if (rProps.type === 'textarea') type = 'textarea'
  else if (rProps.type === 'password') type = 'password'  // 新增
  else type = 'text'
} else if (r.type === 'timePicker') {
  type = rProps.isRange === true ? 'timerange' : 'time'   // 新增 timerange
} else if (r.type === 'treeSelect') {
  type = 'treeselect'                                      // 新增
}
```

**新增 prop keys**：

```typescript
const propKeys = [
  'action', 'accept', 'multiple', 'precision', 'min', 'max',
  'rows', 'maxlength', 'fileNameTargetField',
  'isRange', 'valueFormat', 'startPlaceholder', 'endPlaceholder',  // 新增
  'treeData', 'checkStrictly',                                       // 新增
]
```

### 7. FormRenderer.vue — 新增控件渲染

在现有控件列表后新增：

```html
<!-- password -->
<template v-else-if="field.type === 'password'">
  <el-input
    v-model="formData[field.key]"
    type="password"
    show-password
    :placeholder="field.placeholder"
    clearable
  />
</template>

<!-- timerange -->
<template v-else-if="field.type === 'timerange'">
  <el-time-picker
    v-model="formData[field.key]"
    is-range
    value-format="HH:mm:ss"
    :start-placeholder="field.startPlaceholder || $t('common.startTime')"
    :end-placeholder="field.endPlaceholder || $t('common.endTime')"
    style="width: 100%"
    popper-class="form-renderer-popper"
  />
</template>

<!-- treeselect -->
<template v-else-if="field.type === 'treeselect'">
  <el-tree-select
    v-model="formData[field.key]"
    :data="field.treeData || []"
    :multiple="field.multiple"
    check-strictly
    :placeholder="field.placeholder"
    clearable
    style="width: 100%"
    popper-class="form-renderer-popper"
  />
</template>
```

---

## Data Models

### 新增数据库字段（RequestItems 表）

| 字段名 | 数据类型 | 说明 | 对应控件 |
|---|---|---|---|
| `password_field` | `VARCHAR(255)` | 密码字段示例 | password |
| `work_time_range` | `VARCHAR(50)` | 工作时间范围，格式 "HH:mm:ss,HH:mm:ss" | timerange |
| `product_category` | `VARCHAR(100)` | 产品分类（树形选择） | treeselect |

### 新增 subForm rule 条目

```json
[
  {
    "name": "ref_Fpassword001",
    "type": "input",
    "field": "password_field",
    "props": { "type": "password", "placeholder": "Please input password" },
    "title": "Password Field",
    "_fc_drag_tag": "input"
  },
  {
    "name": "ref_Fworktimerange001",
    "type": "timePicker",
    "field": "work_time_range",
    "props": {
      "isRange": true,
      "valueFormat": "HH:mm:ss",
      "startPlaceholder": "Start time",
      "endPlaceholder": "End time"
    },
    "title": "Work Time Range",
    "_fc_drag_tag": "timePicker"
  },
  {
    "name": "ref_Fproductcategory001",
    "type": "treeSelect",
    "field": "product_category",
    "props": {
      "placeholder": "Please select category",
      "treeData": [
        { "label": "Electronics", "value": "electronics", "children": [
          { "label": "Computers", "value": "computers" },
          { "label": "Phones", "value": "phones" }
        ]},
        { "label": "Office Supplies", "value": "office", "children": [
          { "label": "Stationery", "value": "stationery" },
          { "label": "Furniture", "value": "furniture" }
        ]}
      ]
    },
    "title": "Product Category",
    "_fc_drag_tag": "treeSelect"
  }
]
```

### form-create rule type → ColumnType 完整映射表

| form-create rule.type | props 条件 | DialogColumn.type |
|---|---|---|
| `input` | 无 / `props.type` 不是 textarea/password | `text` |
| `input` | `props.type === 'textarea'` | `textarea` |
| `input` | `props.type === 'password'` | `password` ✨新增 |
| `inputNumber` | — | `number` |
| `select` | `props.multiple !== true` | `select` |
| `select` | `props.multiple === true` | `checkbox` |
| `radio` | — | `radio` |
| `switch` | — | `switch` |
| `datePicker` | `props.type === 'date'` | `date` |
| `datePicker` | `props.type === 'datetime'` | `datetime` |
| `timePicker` | `props.isRange !== true` | `time` |
| `timePicker` | `props.isRange === true` | `timerange` ✨新增 |
| `treeSelect` | — | `treeselect` ✨新增 |
| `upload` | — | `upload` |
| `userSelect` / `user` | — | `user` |
| `departmentSelect` / `department` | — | `department` |

---

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system — essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Options 路径一致性

*For any* column of type `select`, `radio`, or `checkbox` produced by `deriveColumnsFromBinding`, the resulting `DialogColumn` should have `props.options` set to the same array as the top-level `options` field, so that `SubTableAddDialog` can always read options from `col.props?.options`.

**Validates: Requirements 1.4, 1.5, 1.6, 3.1, 4.1**

### Property 2: 类型映射完整性

*For any* form-create rule with type in `['input', 'inputNumber', 'select', 'radio', 'switch', 'datePicker', 'timePicker', 'treeSelect', 'upload', 'userSelect', 'departmentSelect']`, `deriveColumnsFromBinding` should produce a `DialogColumn` with a non-undefined `type` that matches the expected mapping table (including the new `password`, `timerange`, `treeselect` mappings).

**Validates: Requirements 2.3, 5.3, 6.3, 8.4**

### Property 3: buildInitialRow 覆盖所有类型

*For any* array of `DialogColumn` objects (including the new `password`, `timerange`, `treeselect` types), `buildInitialRow` should return an object where every column's field is present as a key, and the value matches the expected initial value for that type (`null` for timerange, `''` for password, `''` or `[]` for treeselect).

**Validates: Requirements 5.5, 6.5**

### Property 4: 选项值到标签的解析

*For any* column of type `radio`, `select`, or `checkbox` with a configured options array, and any stored value that exists in that options array, `resolveDisplayValue` should return the corresponding option label (not the raw value).

**Validates: Requirements 1.7, 3.3, 4.3**

### Property 5: password 字段掩码显示

*For any* column of type `password` with any non-empty stored value, `resolveDisplayValue` should return the masked string `'••••••'` regardless of the actual value.

**Validates: Requirements 2.6**

### Property 6: timerange 格式化显示

*For any* column of type `timerange` where the stored value is an array of two time strings `[startTime, endTime]`, `resolveDisplayValue` should return a string in the format `"startTime - endTime"`.

**Validates: Requirements 5.7**

---

## Error Handling

| 场景 | 处理方式 |
|---|---|
| `treeData` 为空或未配置 | `el-tree-select` 显示空树，用户无法选择，不报错 |
| `timerange` 值格式不正确（非数组） | `resolveDisplayValue` 降级为 `String(rawValue)` |
| `password` 字段在 edit 模式下预填 | 正常预填原始值到 `formData`，`el-input` 以密码掩码显示 |
| options 为空数组 | radio/checkbox/select 显示空选项组，不报错 |
| `deriveColumnsFromBinding` 遇到未知 rule type | type 保持 `undefined`，SubTableAddDialog 降级为 text input |

---

## Testing Strategy

### 单元测试

1. `buildInitialRow` — 验证 `password` → `''`，`timerange` → `null`，`treeselect`（单选）→ `''`，`treeselect`（多选）→ `[]`
2. `deriveColumnsFromBinding` — 验证 `input/password` → `password`，`timePicker/isRange` → `timerange`，`treeSelect` → `treeselect`
3. `deriveColumnsFromBinding` — 验证 options 同时出现在顶层和 `props.options`
4. `resolveDisplayValue` — 验证 radio/select 返回 label，checkbox 返回逗号分隔 labels，password 返回 `'••••••'`，timerange 返回格式化字符串

### 属性测试（Property-Based Tests）

使用 `fast-check`，每个属性最少运行 100 次迭代。

**Property 1 实现：**
```typescript
// Feature: sub-table-field-consistency, Property 1: Options path consistency
fc.assert(fc.property(
  fc.array(fc.record({
    field: fc.string({ minLength: 1 }),
    label: fc.string({ minLength: 1 }),
    type: fc.constantFrom('select', 'radio', 'checkbox'),
    options: fc.array(fc.record({ label: fc.string(), value: fc.string() }), { minLength: 1 })
  }), { minLength: 1 }),
  (rules) => {
    const columns = deriveColumnsFromBinding({ bindingId: 'test' }, {
      test: { rule: rules.map(r => ({ type: r.type === 'checkbox' ? 'select' : r.type, field: r.field, title: r.label, props: { multiple: r.type === 'checkbox', options: r.options } })) }
    })
    columns.forEach((col, i) => {
      expect(col.props?.options).toBeDefined()
      expect(col.props?.options).toEqual(col.options)
    })
  }
), { numRuns: 100 })
```

**Property 2 实现：**
```typescript
// Feature: sub-table-field-consistency, Property 2: Type mapping completeness
fc.assert(fc.property(
  fc.record({
    type: fc.constantFrom('input', 'inputNumber', 'select', 'radio', 'switch', 'datePicker', 'timePicker', 'treeSelect', 'upload'),
    field: fc.string({ minLength: 1 }),
    props: fc.record({
      type: fc.constantFrom('text', 'textarea', 'password', 'date', 'datetime'),
      multiple: fc.boolean(),
      isRange: fc.boolean(),
    })
  }),
  (rule) => {
    const columns = deriveColumnsFromBinding({ bindingId: 'test' }, { test: { rule: [{ ...rule, title: rule.field }] } })
    expect(columns[0].type).toBeDefined()
  }
), { numRuns: 100 })
```

**Property 3 实现：**
```typescript
// Feature: sub-table-field-consistency, Property 3: buildInitialRow covers all types
fc.assert(fc.property(
  fc.array(fc.record({
    field: fc.string({ minLength: 1 }),
    type: fc.constantFrom('text', 'textarea', 'number', 'select', 'radio', 'checkbox', 'switch', 'date', 'datetime', 'upload', 'password', 'timerange', 'treeselect'),
  }), { minLength: 1 }),
  (columns) => {
    const row = buildInitialRow(columns as DialogColumn[])
    columns.forEach(col => {
      expect(Object.keys(row)).toContain(col.field)
      if (col.type === 'timerange') expect(row[col.field]).toBeNull()
      if (col.type === 'password') expect(row[col.field]).toBe('')
      if (col.type === 'checkbox') expect(Array.isArray(row[col.field])).toBe(true)
    })
  }
), { numRuns: 100 })
```

**Property 4 实现：**
```typescript
// Feature: sub-table-field-consistency, Property 4: Option value to label resolution
fc.assert(fc.property(
  fc.constantFrom('radio', 'select', 'checkbox'),
  fc.array(fc.record({ label: fc.string({ minLength: 1 }), value: fc.string({ minLength: 1 }) }), { minLength: 1 }),
  fc.nat(),
  (type, options, rawIdx) => {
    const col = { field: 'f', label: 'F', type, props: { options } } as DialogColumn
    if (type === 'checkbox') {
      const selectedValues = [options[rawIdx % options.length].value]
      const result = resolveDisplayValue(col, selectedValues)
      expect(result).toBe(options[rawIdx % options.length].label)
    } else {
      const selectedValue = options[rawIdx % options.length].value
      const result = resolveDisplayValue(col, selectedValue)
      expect(result).toBe(options[rawIdx % options.length].label)
    }
  }
), { numRuns: 100 })
```

**Property 5 实现：**
```typescript
// Feature: sub-table-field-consistency, Property 5: Password field masked display
fc.assert(fc.property(
  fc.string({ minLength: 1 }),
  (rawValue) => {
    const col = { field: 'pwd', label: 'Password', type: 'password' } as DialogColumn
    expect(resolveDisplayValue(col, rawValue)).toBe('••••••')
  }
), { numRuns: 100 })
```

**Property 6 实现：**
```typescript
// Feature: sub-table-field-consistency, Property 6: Timerange formatted display
fc.assert(fc.property(
  fc.tuple(
    fc.string({ minLength: 1 }),
    fc.string({ minLength: 1 })
  ),
  ([start, end]) => {
    const col = { field: 'tr', label: 'Time Range', type: 'timerange' } as DialogColumn
    expect(resolveDisplayValue(col, [start, end])).toBe(`${start} - ${end}`)
  }
), { numRuns: 100 })
```

**测试文件位置：**
- `frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`
