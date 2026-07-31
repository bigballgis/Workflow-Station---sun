# Requirements Document

## Introduction

本功能为 Developer Workstation 的 Function Unit 中的 Form Preview，以及部署后的 User Portal，新增 Sub Table 行数据录入弹窗。

当前行为：点击 Sub Table 的 Add 按钮时，直接在表格末尾插入一条空行。

目标行为：点击 Add 按钮时，弹出一个 Dialog，Dialog 内的表单字段与 Sub Table 设计器中配置的字段及样式完全一致；用户填写完毕后点击 Save 按钮，将该条数据追加到 Sub Table 中；原有直接插入空行的逻辑同时移除。

该改动同时作用于 Developer Workstation 的 Form Preview 和 User Portal 的运行时表单。

## Glossary

- **Form_Preview**: Developer Workstation 中用于预览表单设计效果的运行时视图。
- **User_Portal**: 部署后供最终用户使用的表单运行时环境。
- **Sub_Table**: 表单中嵌套的子表格控件，支持多行数据录入。
- **Sub_Table_Designer**: Developer Workstation 中用于配置 Sub Table 字段、类型及样式的设计器。
- **Add_Dialog**: 点击 Sub Table Add 按钮后弹出的 Dialog/Modal，内含与 Sub Table 设计一致的表单。
- **Edit_Dialog**: 点击 Sub Table 行 Edit 按钮后弹出的 Dialog/Modal，与 Add_Dialog 共用同一组件，但预填该行的现有数据。
- **Dialog_Form**: Add_Dialog / Edit_Dialog 内部的表单，字段与 Sub_Table_Designer 配置完全对应。
- **Row_Data**: 用户在 Dialog_Form 中填写并通过 Save 提交的一条子表格记录。
- **Field_Config**: Sub_Table_Designer 中每个字段的类型、标签、校验规则及样式配置。
- **PostgreSQL_Type**: 数据库字段的 PostgreSQL 数据类型，用于决定渲染的表单控件种类。
- **editingRow**: 原有行内编辑状态标识，本功能实现后应被移除。

---

## Requirements

### Requirement 1: 点击 Add 按钮弹出 Dialog

**User Story:** As a form user, I want a dialog to appear when I click the Sub Table Add button, so that I can fill in row data in a structured form before it is added to the table.

#### Acceptance Criteria

1. WHEN the user clicks the Add button of a Sub Table, THE Add_Dialog SHALL open and display the Dialog_Form.
2. WHEN the Add_Dialog is open, THE Add_Dialog SHALL prevent interaction with the background form until the dialog is closed.
3. THE Add_Dialog SHALL provide a visible close/cancel control that dismisses the dialog without saving any data.
4. WHEN the Add_Dialog is dismissed without saving, THE Sub_Table SHALL remain unchanged.

---

### Requirement 2: 移除原有直接插入空行逻辑

**User Story:** As a form user, I want clicking Add to open a dialog instead of inserting an empty row, so that the table only contains intentionally submitted data.

#### Acceptance Criteria

1. WHEN the user clicks the Add button of a Sub Table, THE Sub_Table SHALL NOT insert an empty row directly into the table.
2. THE Sub_Table SHALL only append a new row after the user submits data through the Add_Dialog Save button.

---

### Requirement 3: Dialog_Form 字段与 Sub_Table_Designer 配置完全一致

**User Story:** As a form designer, I want the dialog form fields to mirror the Sub Table designer configuration exactly, so that the data entry experience matches the intended design.

#### Acceptance Criteria

1. THE Dialog_Form SHALL render one form field for each column defined in the Sub_Table_Designer, in the same order.
2. THE Dialog_Form SHALL apply the same label, placeholder, required flag, and validation rules as defined in the Field_Config for each field.
3. THE Dialog_Form SHALL apply the same layout and style configuration as defined in the Field_Config for each field.
4. WHEN the Sub_Table_Designer configuration changes, THE Dialog_Form SHALL reflect the updated Field_Config upon the next opening of the Add_Dialog.

---

### Requirement 4: 根据 PostgreSQL 字段类型正确渲染表单控件

**User Story:** As a form user, I want each field in the dialog to use the appropriate input control for its data type, so that I can enter data in a natural and correct way.

#### Acceptance Criteria

1. WHEN a field has PostgreSQL type `varchar(255)` and no special sub-type, THE Dialog_Form SHALL render a single-line text input control.
2. WHEN a field has PostgreSQL type `text`, THE Dialog_Form SHALL render a multi-line textarea control.
3. WHEN a field has PostgreSQL type `int4` or `numeric` and no special sub-type, THE Dialog_Form SHALL render a numeric input control that accepts only numeric values.
4. WHEN a field has PostgreSQL type `varchar` or `int4` and is configured as a single-select (radio/dropdown) sub-type, THE Dialog_Form SHALL render a single-select control populated with the configured options.
5. WHEN a field has PostgreSQL type `varchar[]`, THE Dialog_Form SHALL render a multi-select control populated with the configured options.
6. WHEN a field has PostgreSQL type `bool`, THE Dialog_Form SHALL render a toggle/switch control with true and false states.
7. WHEN a field has PostgreSQL type `date`, THE Dialog_Form SHALL render a date picker control.
8. WHEN a field has PostgreSQL type `timestamp`, THE Dialog_Form SHALL render a date-time picker control.
9. WHEN a field has PostgreSQL type `varchar` and is configured as a file or image sub-type, THE Dialog_Form SHALL render a file/image upload control.
10. WHEN a field has PostgreSQL type `varchar` and is configured as a personnel or department sub-type, THE Dialog_Form SHALL render a personnel/department selector control.

---

### Requirement 5: Save 按钮将数据追加到 Sub Table

**User Story:** As a form user, I want to click Save in the dialog to add the filled data as a new row in the Sub Table, so that my input is persisted in the table.

#### Acceptance Criteria

1. THE Add_Dialog SHALL display a Save button that is always visible within the dialog.
2. WHEN the user clicks the Save button and all required fields pass validation, THE Sub_Table SHALL append the Row_Data as a new row at the end of the table.
3. WHEN the user clicks the Save button and all required fields pass validation, THE Add_Dialog SHALL close automatically after the row is appended.
4. IF a required field in the Dialog_Form is empty when the user clicks Save, THEN THE Dialog_Form SHALL display a validation error message for each empty required field and SHALL NOT close the dialog or append any row.
5. IF a field value fails its configured validation rule when the user clicks Save, THEN THE Dialog_Form SHALL display the corresponding validation error message and SHALL NOT close the dialog or append any row.

---

### Requirement 6: Form Preview 与 User Portal 行为一致

**User Story:** As a developer, I want the Add Dialog and Edit Dialog behavior to work identically in both Form Preview and User Portal, so that the design-time preview accurately reflects the production runtime.

#### Acceptance Criteria

1. THE Form_Preview SHALL implement the Add_Dialog behavior described in Requirements 1–5.
2. THE User_Portal SHALL implement the Add_Dialog behavior described in Requirements 1–5.
3. WHEN the same Sub Table configuration is rendered in Form_Preview and User_Portal, THE Add_Dialog SHALL produce identical field layout, control types, and validation behavior in both environments.
4. THE Form_Preview SHALL implement the Edit_Dialog behavior described in Requirement 7.
5. THE User_Portal SHALL implement the Edit_Dialog behavior described in Requirement 7.
6. WHEN the same Sub Table row is edited in Form_Preview and User_Portal, THE Edit_Dialog SHALL produce identical pre-fill behavior, field layout, control types, and validation behavior in both environments.

---

### Requirement 7: 点击 Edit 按钮弹出 Dialog 并预填现有数据

**User Story:** As a form user, I want a dialog to appear pre-filled with the existing row data when I click the Edit button of a Sub Table row, so that I can modify the data and save the changes without inline editing.

#### Acceptance Criteria

1. WHEN the user clicks the Edit button of a Sub Table row, THE Edit_Dialog SHALL open using the same SubTableAddDialog component as the Add_Dialog.
2. WHEN the Edit_Dialog opens, THE Dialog_Form SHALL pre-fill each field with the current value of the corresponding column in that row.
3. WHEN the user clicks the Save button in the Edit_Dialog and all required fields pass validation, THE Sub_Table SHALL update the existing row in place with the new Row_Data and SHALL NOT append a new row.
4. WHEN the user clicks the Save button in the Edit_Dialog and all required fields pass validation, THE Edit_Dialog SHALL close automatically after the row is updated.
5. IF a required field in the Edit_Dialog Dialog_Form is empty when the user clicks Save, THEN THE Dialog_Form SHALL display a validation error message for each empty required field and SHALL NOT close the dialog or modify any row.
6. IF a field value fails its configured validation rule when the user clicks Save in the Edit_Dialog, THEN THE Dialog_Form SHALL display the corresponding validation error message and SHALL NOT close the dialog or modify any row.
7. WHEN the Edit_Dialog is dismissed without saving, THE Sub_Table row SHALL remain unchanged with its original data.
8. THE Sub_Table SHALL NOT use inline row editing (editingRow) for any row; all row edits SHALL be performed through the Edit_Dialog.
