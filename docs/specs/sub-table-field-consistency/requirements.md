# Requirements Document

## Introduction

本功能针对 Procurement Workflow 的 RequestItems Sub Table，解决两个问题：

1. **渲染一致性修复**：主表单（FormRenderer.vue）与 Add/Edit 弹窗（SubTableAddDialog.vue）对同一字段的渲染控件不一致，导致用户在主表单看到的效果与弹窗中填写时的效果不同。具体表现为：`count`（数字）、`unit_price`（数字）、`item_code`（文本）、`description`（多行文本）等字段在弹窗中的渲染与主表单中的 subForm 渲染存在差异（如 `el-radio` 的 `:label` vs `:value` 属性、`el-checkbox` 的绑定方式、`el-input-number` 的样式等）。

2. **新增基础控件类型**：在 RequestItems Sub Table 中新增 `password`、`radio`（已有但需验证一致性）、`checkbox`（已有但需验证一致性）、`timerange`（时间范围选择器）、`treeselect`（树形下拉选择器）五种控件，同步更新数据库字段定义，并保证这些控件在主表单和弹窗中渲染完全一致。

## Glossary

- **FormRenderer**: `frontend/user-portal/src/components/FormRenderer.vue`，负责渲染主表单字段。
- **SubTableAddDialog**: `frontend/user-portal/src/components/SubTableAddDialog.vue`，点击 Sub Table Add/Edit 按钮后弹出的 Dialog，内含与 Sub Table 设计一致的表单。
- **SubTableField**: `frontend/user-portal/src/components/SubTableField.vue`，Sub Table 控件，包含表格展示和 Add/Edit 按钮。
- **deriveColumnsFromBinding**: 三个视图文件（tasks/detail.vue、applications/detail.vue、processes/start.vue）中将 subForm rule 转换为 DialogColumn 数组的函数。
- **DialogColumn**: SubTableAddDialog 接收的列配置类型，定义于 `subTableAddDialogHelpers.ts`。
- **ColumnType**: DialogColumn 的 type 字段枚举，决定 SubTableAddDialog 渲染哪种控件。
- **subForm rule**: 存储在数据库 `config_json.subForms[bindingId].rule` 中的 form-create 规则数组，描述 Sub Table 的字段配置。
- **RequestItems**: Procurement Workflow 中的 Sub Table，存储采购申请的行项目数据。
- **password**: 密码输入框控件（`el-input` type="password"），对应 `VARCHAR` 数据库字段。
- **radio**: 单选按钮组控件（`el-radio-group` + `el-radio`），对应 `VARCHAR` 或 `INTEGER` 数据库字段。
- **checkbox**: 多选框组控件（`el-checkbox-group` + `el-checkbox`），对应 `VARCHAR`（JSON 数组字符串）数据库字段。
- **timerange**: 时间范围选择器控件（`el-time-picker` type="timerange"），对应 `VARCHAR` 数据库字段（存储为 "HH:mm:ss,HH:mm:ss" 格式）。
- **treeselect**: 树形下拉选择器控件（`el-tree-select`），对应 `VARCHAR` 或 `INTEGER` 数据库字段。

---

## Requirements

### Requirement 1: 修复主表单与弹窗的渲染一致性

**User Story:** As a form user, I want the fields in the Sub Table Add/Edit dialog to render identically to how they appear in the main form's subForm section, so that the data entry experience is consistent and predictable.

#### Acceptance Criteria

1. WHEN a `radio` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL use `el-radio-group` with `el-radio` components where each `el-radio` binds its value via the `:value` prop (not `:label`), matching the behavior of FormRenderer.
2. WHEN a `checkbox` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL use `el-checkbox-group` with `el-checkbox` components where each `el-checkbox` binds its value via the `:value` prop (not `:label`), matching the behavior of FormRenderer.
3. WHEN a `number` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL render `el-input-number` with `style="width: 100%"` and the same `precision`, `min`, `max` props as configured in the subForm rule.
4. WHEN a `select` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL read options from `col.props?.options` (not `col.options`), consistent with how `deriveColumnsFromBinding` passes options through the `props` object.
5. WHEN a `radio` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL read options from `col.props?.options`, consistent with how `deriveColumnsFromBinding` passes options through the `props` object.
6. WHEN a `checkbox` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL read options from `col.props?.options`, consistent with how `deriveColumnsFromBinding` passes options through the `props` object.
7. THE SubTableField table display column SHALL show human-readable labels for `radio`, `checkbox`, `select` fields by mapping stored values back to their configured option labels.

---

### Requirement 2: 新增 password 控件支持

**User Story:** As a form designer, I want to add a password field to the RequestItems sub table, so that sensitive item-related data can be entered securely.

#### Acceptance Criteria

1. WHEN a field has `type: 'password'` in the subForm rule, THE SubTableAddDialog SHALL render an `el-input` with `type="password"` and `show-password` enabled.
2. WHEN a `password` field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL mask the input value with asterisks by default, with a toggle to show/hide the password.
3. THE `deriveColumnsFromBinding` function SHALL map form-create rule type `'input'` with `props.type === 'password'` to DialogColumn type `'password'`.
4. THE `ColumnType` union in `subTableAddDialogHelpers.ts` SHALL include `'password'` as a valid type.
5. THE RequestItems sub table SHALL have a `password_field` column of type `VARCHAR(255)` in the database, with a corresponding form rule of type `input` with `props.type = 'password'`.
6. WHEN a `password` field value is displayed in the SubTableField table, THE SubTableField SHALL show masked text (e.g., `••••••`) instead of the raw value.

---

### Requirement 3: 验证并修复 radio 控件一致性

**User Story:** As a form user, I want the radio button group in the Sub Table dialog to work correctly with the configured options, so that I can select a single value from the predefined choices.

#### Acceptance Criteria

1. WHEN a `radio` type field is configured in the subForm rule with `options`, THE `deriveColumnsFromBinding` function SHALL pass those options through `props.options` in the resulting DialogColumn.
2. WHEN a `radio` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL display all configured options as `el-radio` buttons within an `el-radio-group`.
3. WHEN a user selects a radio option and saves, THE SubTableField SHALL display the selected option's label (not raw value) in the table cell.
4. THE RequestItems sub table SHALL have an `item_status` field with radio options: Pending/Approved/Rejected.

---

### Requirement 4: 验证并修复 checkbox 控件一致性

**User Story:** As a form user, I want the checkbox group in the Sub Table dialog to work correctly with the configured options, so that I can select multiple values from the predefined choices.

#### Acceptance Criteria

1. WHEN a `checkbox` type field is configured in the subForm rule with `options`, THE `deriveColumnsFromBinding` function SHALL pass those options through `props.options` in the resulting DialogColumn.
2. WHEN a `checkbox` type field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL display all configured options as `el-checkbox` components within an `el-checkbox-group`.
3. WHEN a user selects multiple checkbox options and saves, THE SubTableField SHALL display the selected options' labels (comma-separated) in the table cell.
4. THE `buildInitialRow` function SHALL initialize `checkbox` type fields with an empty array `[]`.
5. THE RequestItems sub table SHALL have a `tags` field with checkbox options: Fragile/Perishable/Hazardous/Bulk.

---

### Requirement 5: 新增 timerange 控件支持

**User Story:** As a form user, I want to select a time range (start time and end time) for a sub table field, so that I can specify time intervals for procurement items.

#### Acceptance Criteria

1. WHEN a field has `type: 'timerange'` in the subForm rule (form-create type `'timePicker'` with `props.isRange === true`), THE SubTableAddDialog SHALL render an `el-time-picker` with `is-range` enabled.
2. WHEN a `timerange` field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL display two time inputs (start and end) within a single `el-time-picker` component.
3. THE `deriveColumnsFromBinding` function SHALL map form-create rule type `'timePicker'` with `props.isRange === true` to DialogColumn type `'timerange'`.
4. THE `ColumnType` union in `subTableAddDialogHelpers.ts` SHALL include `'timerange'` as a valid type.
5. THE `buildInitialRow` function SHALL initialize `timerange` type fields with `null`.
6. THE RequestItems sub table SHALL have a `work_time_range` column of type `VARCHAR(50)` in the database, with a corresponding form rule of type `timePicker` with `isRange: true`.
7. WHEN a `timerange` value is displayed in the SubTableField table, THE SubTableField SHALL show the formatted time range string (e.g., `"09:00:00 - 17:00:00"`).

---

### Requirement 6: 新增 treeselect 控件支持

**User Story:** As a form user, I want to select a value from a hierarchical tree structure in the Sub Table dialog, so that I can categorize items using a tree-based taxonomy.

#### Acceptance Criteria

1. WHEN a field has `type: 'treeselect'` in the subForm rule (form-create type `'treeSelect'`), THE SubTableAddDialog SHALL render an `el-tree-select` component.
2. WHEN a `treeselect` field is rendered in SubTableAddDialog, THE SubTableAddDialog SHALL pass the configured `treeData` (from `col.props?.treeData`) as the `:data` prop to `el-tree-select`.
3. THE `deriveColumnsFromBinding` function SHALL map form-create rule type `'treeSelect'` to DialogColumn type `'treeselect'`.
4. THE `ColumnType` union in `subTableAddDialogHelpers.ts` SHALL include `'treeselect'` as a valid type.
5. THE `buildInitialRow` function SHALL initialize `treeselect` type fields with `''` (empty string for single-select) or `[]` (for multi-select).
6. THE RequestItems sub table SHALL have a `product_category` column of type `VARCHAR(100)` in the database, with a corresponding form rule of type `treeSelect` with sample hierarchical options.
7. WHEN a `treeselect` value is displayed in the SubTableField table, THE SubTableField SHALL show the selected node's label (not raw value) in the table cell.

---

### Requirement 7: 同步更新数据库字段和 subForm rule

**User Story:** As a developer, I want the database table definitions and form configuration to be updated with the new field types, so that the system is fully configured end-to-end.

#### Acceptance Criteria

1. THE RequestItems table in `dw_field_definitions` SHALL have new columns for `password_field` (VARCHAR 255), `work_time_range` (VARCHAR 50), and `product_category` (VARCHAR 100).
2. THE Request Form's `config_json.subForms[bindingId].rule` SHALL include form rules for all new fields: `password_field` (input/password), `work_time_range` (timePicker/range), `product_category` (treeSelect).
3. THE SQL migration script SHALL be idempotent (safe to re-run using `ON CONFLICT DO UPDATE`).
4. THE subForm rule in the database SHALL use the same options format as the existing fields for consistency.

---

### Requirement 8: FormRenderer 新增对应控件渲染支持

**User Story:** As a form user, I want the main form (FormRenderer) to also render password, timerange, and treeselect fields correctly when they appear in the main form context, so that the rendering is consistent across all form contexts.

#### Acceptance Criteria

1. WHEN a field of type `'password'` is encountered in FormRenderer, THE FormRenderer SHALL render an `el-input` with `type="password"` and `show-password` enabled.
2. WHEN a field of type `'timerange'` is encountered in FormRenderer, THE FormRenderer SHALL render an `el-time-picker` with `is-range` enabled, `value-format="HH:mm:ss"`, and appropriate start/end placeholders.
3. WHEN a field of type `'treeselect'` is encountered in FormRenderer, THE FormRenderer SHALL render an `el-tree-select` with `:data` bound to `field.treeData`, `check-strictly` enabled, and `clearable`.
4. THE `convertFormCreateRule` function in the view files SHALL map `'input'` with `props.type === 'password'` to type `'password'`, `'timePicker'` with `props.isRange === true` to type `'timerange'`, and `'treeSelect'` to type `'treeselect'`.
