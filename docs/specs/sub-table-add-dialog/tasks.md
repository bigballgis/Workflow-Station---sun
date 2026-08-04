# Implementation Plan: Sub Table Add Dialog

## Overview

将 Sub Table 行数据录入方式从"直接插入空行"改为"弹出 Dialog 填写后提交"。实现分为：新建 SubTableAddDialog 组件、改造 SubTableField、扩展 deriveColumnsFromBinding、更新 FormDesigner Preview 逻辑，以及对应的属性测试。

## Tasks

- [x] 1. 新建 SubTableAddDialog 组件（user-portal）
  - [x] 1.1 创建 `frontend/user-portal/src/components/SubTableAddDialog.vue`
    - 接收 `visible`、`columns`、`title`、`mode`（`'add' | 'edit'`）、`initialData` props
    - 使用 `el-dialog` 包裹 `el-form`，根据 `columns` 动态渲染 10 种控件（text/textarea/number/select/radio/checkbox/switch/date/datetime/upload/user/department）
    - 实现 `buildInitialRow(columns)` 初始化各类型字段默认值（number→undefined，switch→false，checkbox→[]，date/datetime→null，其余→''）
    - 实现 `buildRules(columns)` 从 columns 生成 el-form required 校验规则
    - Save 按钮触发 el-form 校验，通过后 emit `save` 并关闭；Cancel 直接关闭
    - edit 模式下用 `initialData` 预填表单；upload 字段回填文件名
    - _Requirements: 1.1, 1.2, 1.3, 3.1, 3.2, 3.3, 4.1–4.10, 5.1, 5.4, 5.5, 7.1, 7.2_

  - [x] 1.2 为 `buildRules` 和 `buildInitialRow` 编写单元测试
    - 验证各类型字段初始值正确
    - 验证 required 字段生成对应 rule
    - _Requirements: 3.2, 4.1–4.10_

  - [x] 1.3 编写属性测试 Property 2：Dialog form mirrors column configuration
    - **Property 2: Dialog form mirrors column configuration**
    - **Validates: Requirements 3.1, 3.2, 3.4**
    - 文件：`frontend/user-portal/src/components/__tests__/SubTableAddDialog.property.test.ts`

  - [x] 1.4 编写属性测试 Property 3：Control type mapping is correct
    - **Property 3: Control type mapping is correct**
    - **Validates: Requirements 4.1–4.10**
    - 文件：`frontend/user-portal/src/components/__tests__/SubTableAddDialog.property.test.ts`

- [x] 2. 改造 SubTableField（user-portal）
  - [x] 2.1 修改 `frontend/user-portal/src/components/SubTableField.vue`
    - 移除 `handleAdd` 中直接 push 空行及 `editingRow` 行内编辑逻辑
    - 新增 `dialogVisible` ref 和 `dialogMode` ref（`'add' | 'edit'`）及 `editingRowIndex` ref
    - `handleAdd` 改为设置 mode='add' 并打开 Dialog
    - Edit 按钮改为设置 mode='edit'、记录行索引、传入 `initialData` 并打开 Dialog
    - 新增 `handleDialogSave(rowData)`：add 模式 push 新行，edit 模式替换对应索引行，均 emit `update:modelValue`
    - 引入并注册 `SubTableAddDialog` 组件
    - _Requirements: 1.1, 1.4, 2.1, 2.2, 5.2, 5.3, 7.3, 7.4, 7.7, 7.8_

  - [x] 2.2 编写属性测试 Property 1：Cancel preserves table state
    - **Property 1: Cancel preserves table state**
    - **Validates: Requirements 1.4, 2.1**
    - 文件：`frontend/user-portal/src/components/__tests__/SubTableAddDialog.property.test.ts`

  - [x] 2.3 编写属性测试 Property 4：Valid save appends exactly one row
    - **Property 4: Valid save appends exactly one row**
    - **Validates: Requirements 5.2, 5.3**
    - 文件：`frontend/user-portal/src/components/__tests__/SubTableAddDialog.property.test.ts`

  - [x] 2.4 编写属性测试 Property 5：Invalid save does not modify table
    - **Property 5: Invalid save does not modify table**
    - **Validates: Requirements 5.4, 5.5**
    - 文件：`frontend/user-portal/src/components/__tests__/SubTableAddDialog.property.test.ts`

- [x] 3. Checkpoint — 确认 user-portal 核心逻辑通过测试
  - 确保所有测试通过，如有疑问请告知。

- [x] 4. 扩展 deriveColumnsFromBinding（user-portal 三个视图）
  - [x] 4.1 扩展 `frontend/user-portal/src/views/tasks/detail.vue` 中的 `deriveColumnsFromBinding`
    - 补全 textarea/select/radio/checkbox/switch/datetime/user/department 类型映射
    - 透传 `options` 和 `props`（action、accept、multiple、precision 等）
    - _Requirements: 3.1, 3.2, 3.3, 4.1–4.10, 6.2_

  - [x] 4.2 扩展 `frontend/user-portal/src/views/applications/detail.vue` 中的 `deriveColumnsFromBinding`
    - 与 4.1 相同的映射逻辑
    - _Requirements: 3.1, 3.2, 3.3, 4.1–4.10, 6.2_

  - [x] 4.3 扩展 `frontend/user-portal/src/views/processes/start.vue` 中的 `deriveColumnsFromBinding`
    - 与 4.1 相同的映射逻辑
    - _Requirements: 3.1, 3.2, 3.3, 4.1–4.10, 6.2_

- [x] 5. 同步改动至 developer-workstation
  - [x] 5.1 创建 `frontend/developer-workstation/src/components/designer/SubTableAddDialog.vue`
    - 与 user-portal 版本保持一致（可直接复用或共享逻辑）
    - _Requirements: 6.1, 6.3, 6.4, 6.6_

  - [x] 5.2 修改 `frontend/developer-workstation/src/components/designer/SubTableField.vue`
    - 与 user-portal SubTableField 相同的改造逻辑
    - _Requirements: 6.1, 6.3, 6.4, 6.6_

  - [x] 5.3 修改 `frontend/developer-workstation/src/components/designer/FormDesigner.vue`
    - `handlePreview` 改用 `deriveColumnsFromBinding` 派生 Sub Table 列（支持全部 12 种类型）
    - `previewTableRows` 初始化改为 `[]`（移除直接 push 空行逻辑）
    - 在 Preview Dialog 内引入 `SubTableAddDialog`，每个 sub binding 维护独立的 `previewDialogVisible` 状态
    - _Requirements: 6.1, 6.3, 6.4_

  - [x] 5.4 为 developer-workstation SubTableAddDialog 编写属性测试（Property 1–5）
    - 文件：`frontend/developer-workstation/src/components/designer/__tests__/SubTableAddDialog.property.test.ts`
    - **Validates: Requirements 6.1, 6.3, 6.4, 6.6**

- [x] 6. Final Checkpoint — 确保所有测试通过了，。。。。。。。。。。。。。。。。。。。。。快捷键节目
  - 确保所有测试通过，如有疑问请告知。

## Notes

- 标有 `*` 的子任务为可选测试任务，可跳过以加快 MVP 交付
- 每个任务均引用具体需求条款以保证可追溯性
- 属性测试使用 `fast-check`，每个属性最少运行 100 次迭代
- edit 模式与 add 模式共用同一 `SubTableAddDialog` 组件，通过 `mode` prop 区分行为
