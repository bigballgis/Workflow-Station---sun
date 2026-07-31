# Implementation Plan: Sub Table Field Consistency

## Overview

修复 RequestItems Sub Table 中主表单与弹窗的渲染不一致问题，并新增 password、timerange、treeselect 三种控件类型。

## Tasks

- [x] 1. 修复 subTableAddDialogHelpers.ts — 扩展类型和初始值
  - [x] 1.1 在 `frontend/user-portal/src/components/subTableAddDialogHelpers.ts` 中新增 `password`、`timerange`、`treeselect` 到 `ColumnType` 联合类型
    - 更新 `DialogColumn.props` 接口，新增 `treeData`、`isRange`、`startPlaceholder`、`endPlaceholder`、`checkStrictly` 字段
    - 更新 `buildInitialRow`：`timerange` → `null`，`treeselect`（单选）→ `''`，`password` → `''`
    - 更新 `CONTROL_TYPE_MAP` 和 `resolveControlComponent`，新增三种类型映射
    - 新增并导出 `resolveDisplayValue(col, rawValue)` 函数，处理 radio/select/checkbox label 解析、password 掩码、timerange 格式化
    - _Requirements: 2.4, 5.4, 6.4_

  - [x] 1.2 编写属性测试 Property 3：buildInitialRow 覆盖所有类型
    - **Property 3: buildInitialRow covers all types**
    - **Validates: Requirements 5.5, 6.5**
    - 文件：`frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`

  - [x] 1.3 编写属性测试 Property 5：password 字段掩码显示
    - **Property 5: Password field masked display**
    - **Validates: Requirements 2.6**
    - 文件：`frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`

  - [x] 1.4 编写属性测试 Property 6：timerange 格式化显示
    - **Property 6: Timerange formatted display**
    - **Validates: Requirements 5.7**
    - 文件：`frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`

  - [x] 1.5 编写属性测试 Property 4：选项值到标签的解析
    - **Property 4: Option value to label resolution**
    - **Validates: Requirements 1.7, 3.3, 4.3**
    - 文件：`frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`

- [x] 2. 修复 deriveColumnsFromBinding（三个视图文件）
  - [x] 2.1 修改 `frontend/user-portal/src/views/tasks/detail.vue` 中的 `deriveColumnsFromBinding`
    - 修复 options 路径：在 return 前执行 `if (options) passProps.options = options`
    - 新增类型映射：`input/password` → `password`，`timePicker/isRange` → `timerange`，`treeSelect` → `treeselect`
    - 新增 prop keys：`isRange`、`valueFormat`、`startPlaceholder`、`endPlaceholder`、`treeData`、`checkStrictly`
    - _Requirements: 1.4, 1.5, 1.6, 2.3, 3.1, 4.1, 5.3, 6.3_

  - [x] 2.2 修改 `frontend/user-portal/src/views/applications/detail.vue` 中的 `deriveColumnsFromBinding`
    - 与 2.1 相同的修改
    - _Requirements: 1.4, 1.5, 1.6, 2.3, 3.1, 4.1, 5.3, 6.3_

  - [x] 2.3 修改 `frontend/user-portal/src/views/processes/start.vue` 中的 `deriveColumnsFromBinding`
    - 与 2.1 相同的修改
    - _Requirements: 1.4, 1.5, 1.6, 2.3, 3.1, 4.1, 5.3, 6.3_

  - [x] 2.4 编写属性测试 Property 1：Options 路径一致性

    - **Property 1: Options path consistency**
    - **Validates: Requirements 1.4, 1.5, 1.6, 3.1, 4.1**
    - 文件：`frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`


  - [x] 2.5 编写属性测试 Property 2：类型映射完整性

    - **Property 2: Type mapping completeness**
    - **Validates: Requirements 2.3, 5.3, 6.3, 8.4**
    - 文件：`frontend/user-portal/src/components/__tests__/subTableFieldConsistency.property.test.ts`

- [x] 3. Checkpoint — 确认 deriveColumnsFromBinding 修复通过测试
  - 确保所有测试通过，如有疑问请告知。

- [x] 4. 修复并扩展 SubTableAddDialog.vue（user-portal）
  - [x] 4.1 修改 `frontend/user-portal/src/components/SubTableAddDialog.vue`
    - 修复 select/radio/checkbox 的 options 读取：改为 `col.props?.options ?? col.options ?? []`
    - 新增 password 控件渲染（`el-input` type="password" show-password）
    - 新增 timerange 控件渲染（`el-time-picker` is-range）
    - 新增 treeselect 控件渲染（`el-tree-select`）
    - _Requirements: 1.1, 1.2, 1.4, 1.5, 1.6, 2.1, 2.2, 3.2, 4.2, 5.1, 5.2, 6.1, 6.2_

- [x] 5. 修复 SubTableField.vue — 表格列显示增强
  - [x] 5.1 修改 `frontend/user-portal/src/components/SubTableField.vue`
    - 引入并使用 `resolveDisplayValue` 函数（从 `subTableAddDialogHelpers.ts` 导入）
    - 更新 `Column` 接口，新增 `password`、`timerange`、`treeselect` 到类型联合
    - 更新表格列的 `#default` slot：对所有非 upload 字段使用 `resolveDisplayValue(col, scope.row[col.field])` 替代直接显示 `scope.row[col.field]`
    - _Requirements: 1.7, 2.6, 3.3, 4.3, 5.7, 6.7_

- [x] 6. 修改 FormRenderer.vue — 新增控件渲染
  - [x] 6.1 在 `frontend/user-portal/src/components/FormRenderer.vue` 中新增 password、timerange、treeselect 控件渲染
    - 在现有 `time` 控件之后新增 `timerange` 渲染（`el-time-picker` is-range）
    - 在现有 `text` 控件之后新增 `password` 渲染（`el-input` type="password" show-password）
    - 在现有 `businessUnit` 控件之后新增 `treeselect` 渲染（`el-tree-select`）
    - 同时更新 Tab 布局模式和普通平铺模式两处渲染逻辑
    - _Requirements: 8.1, 8.2, 8.3_

- [x] 7. 同步更新 developer-workstation
  - [x] 7.1 修改 `frontend/developer-workstation/src/components/designer/SubTableAddDialog.vue`
    - 新增 password 控件渲染（`el-input` type="password" show-password）
    - 新增 timerange 控件渲染（`el-time-picker` is-range）
    - 新增 treeselect 控件渲染（`el-tree-select`）
    - 修复 select/radio/checkbox 的 options 读取：改为 `col.props?.options ?? col.options ?? []`
    - _Requirements: 1.1, 1.2, 2.1, 5.1, 6.1_

  - [x] 7.2 修改 `frontend/developer-workstation/src/components/designer/FormDesigner.vue` 中的 `deriveColumnsFromBinding`
    - 新增类型映射：`input/password` → `password`，`timePicker/isRange` → `timerange`，`treeSelect` → `treeselect`
    - 修复 options 路径：在 return 前执行 `if (options) passProps.options = options`
    - 新增 prop keys：`isRange`、`valueFormat`、`startPlaceholder`、`endPlaceholder`、`treeData`、`checkStrictly`
    - _Requirements: 2.3, 5.3, 6.3_

- [x] 8. 新增数据库迁移脚本
  - [x] 8.1 创建 `deploy/init-scripts/13-procurement-workflow/05-add-new-field-types.sql`
    - 向 RequestItems 表新增字段：`password_field`（VARCHAR 255）、`work_time_range`（VARCHAR 50）、`product_category`（VARCHAR 100）
    - 使用 `ON CONFLICT DO UPDATE` 保证幂等性
    - 更新 Request Form 的 `config_json.subForms[bindingId].rule`，在现有规则末尾追加三个新字段的 form-create rule（password input、timePicker isRange、treeSelect）
    - _Requirements: 7.1, 7.2, 7.3, 7.4_

- [x] 9. Final Checkpoint — 确保所有测试通过
  - 确保所有测试通过，如有疑问请告知。

## Notes

- 标有 `*` 的子任务为可选测试任务，可跳过以加快 MVP 交付
- 修复 options 路径是最高优先级，影响 radio/checkbox/select 所有字段的正常显示
- `el-radio` 和 `el-checkbox` 的 `:value` vs `:label` 是 Element Plus v2 的重要变更，已在 user-portal SubTableAddDialog 中正确实现，developer-workstation 版本也已正确
- 属性测试使用 `fast-check`，每个属性最少运行 100 次迭代
