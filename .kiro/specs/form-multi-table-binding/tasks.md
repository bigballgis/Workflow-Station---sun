# Implementation Plan: Form Multi-Table Binding

## Overview

本实现计划将表单多表绑定功能分为后端数据层、后端服务层、前端组件三个阶段实现，确保每个阶段都可独立测试和验证。

## Tasks

- [x] 1. 后端数据层实现
  - [x] 1.1 创建枚举类型和实体
    - 创建 `BindingType` 枚举（PRIMARY, SUB, RELATED）
    - 创建 `BindingMode` 枚举（EDITABLE, READONLY）
    - 创建 `FormTableBinding` 实体类
    - _Requirements: 1.1, 3.1_

  - [x] 1.2 创建数据库迁移脚本
    - 创建 `dw_form_table_bindings` 表
    - 添加索引和约束
    - 迁移现有 `bound_table_id` 数据到新表
    - _Requirements: 6.1_

  - [x] 1.3 创建 Repository 接口
    - 创建 `FormTableBindingRepository`
    - 实现按表单ID查询、按表ID检查存在性等方法
    - _Requirements: 6.2, 6.3_

  - [x] 1.4 编写属性测试：绑定持久化往返
    - **Property 6: Binding persistence round-trip**
    - **Validates: Requirements 6.1, 6.2, 6.3**

- [x] 2. 后端服务层实现
  - [x] 2.1 创建 DTO 类
    - 创建 `FormTableBindingRequest` 请求DTO
    - 创建 `FormTableBindingResponse` 响应DTO
    - _Requirements: 1.1, 2.1, 3.1_

  - [x] 2.2 扩展 FormDesignComponent
    - 添加 `createBinding()` 方法
    - 添加 `updateBinding()` 方法
    - 添加 `deleteBinding()` 方法
    - 添加 `getBindings()` 方法
    - 实现外键字段验证逻辑
    - _Requirements: 1.1, 2.1, 2.2, 2.3, 6.3_

  - [x] 2.3 更新 TableDesignComponent
    - 修改删除表时检查是否被表单绑定引用
    - _Requirements: 6.4_

  - [x] 2.4 编写属性测试：外键验证
    - **Property 2: Foreign key validation**
    - **Validates: Requirements 2.1, 2.2, 2.3**

  - [x] 2.5 编写属性测试：绑定模式配置
    - **Property 3: Binding mode configuration**
    - **Validates: Requirements 3.1, 3.2, 3.3**

- [x] 3. 后端 API 层实现
  - [x] 3.1 创建绑定管理 Controller
    - 实现 POST /forms/{formId}/bindings 创建绑定
    - 实现 GET /forms/{formId}/bindings 获取绑定列表
    - 实现 PUT /forms/{formId}/bindings/{id} 更新绑定
    - 实现 DELETE /forms/{formId}/bindings/{id} 删除绑定
    - _Requirements: 1.1, 6.2, 6.3_

  - [x] 3.2 更新现有表单 API
    - 修改获取表单详情时包含绑定信息
    - _Requirements: 6.2_

- [x] 4. Checkpoint - 后端功能验证
  - 确保所有后端测试通过
  - 使用 API 测试工具验证接口
  - 如有问题请询问用户

- [x] 5. 前端类型和 API 定义
  - [x] 5.1 更新 TypeScript 类型定义
    - 添加 `TableBinding` 接口
    - 添加 `BindingType` 和 `BindingMode` 类型
    - 更新 `FormDefinition` 接口包含 bindings 数组
    - _Requirements: 1.1, 3.1_

  - [x] 5.2 添加绑定管理 API 方法
    - 添加 `createBinding()` API 调用
    - 添加 `getBindings()` API 调用
    - 添加 `updateBinding()` API 调用
    - 添加 `deleteBinding()` API 调用
    - _Requirements: 6.2, 6.3_

- [x] 6. 前端绑定管理组件
  - [x] 6.1 创建 TableBindingManager 组件
    - 显示当前绑定列表（主表、子表、关联表）
    - 支持添加新绑定
    - 支持编辑绑定配置
    - 支持删除绑定
    - _Requirements: 1.1, 1.4, 2.1, 3.3_

  - [x] 6.2 创建添加绑定对话框
    - 选择要绑定的表
    - 选择绑定类型（子表/关联表）
    - 选择绑定模式（可编辑/只读）
    - 配置外键字段（子表必填）
    - _Requirements: 1.3, 2.1, 3.2_

  - [x] 6.3 集成到 FormDesigner
    - 在表单设计器中添加"管理表绑定"按钮
    - 替换原有的单表绑定功能
    - _Requirements: 1.2, 1.3_

  - [x] 6.4 编写属性测试：多表绑定支持
    - **Property 1: Multi-table binding support**
    - **Validates: Requirements 1.1, 1.3, 1.4**

- [x] 7. 前端字段导入增强
  - [x] 7.1 更新字段导入对话框
    - 支持从多个绑定表中选择
    - 显示字段来源表信息
    - 子表字段生成列表组件
    - _Requirements: 4.1, 4.2, 4.3_

  - [x] 7.2 编写属性测试：多表字段导入
    - **Property 4: Field import from multiple tables**
    - **Validates: Requirements 4.1, 4.2, 4.4**

- [x] 8. 前端子表组件
  - [x] 8.1 创建 SubTableField 表单组件
    - 配置数据源（选择绑定的子表）
    - 配置显示列
    - 支持分页
    - _Requirements: 5.1, 5.2, 5.3_

  - [x] 8.2 实现子表数据加载
    - 根据主表记录ID加载子表数据
    - 支持只读模式展示
    - _Requirements: 3.4, 5.2_

  - [x] 8.3 实现子表编辑功能
    - 可编辑模式下支持行内添加
    - 可编辑模式下支持行内编辑
    - 可编辑模式下支持行内删除
    - _Requirements: 5.4_

  - [x] 8.4 编写属性测试：子表组件配置
    - **Property 5: Sub-table component configuration**
    - **Validates: Requirements 5.2, 5.3, 5.4**

- [x] 9. Final Checkpoint - 完整功能验证
  - 确保所有测试通过
  - 验证完整的绑定管理流程
  - 验证子表数据展示和编辑
  - 如有问题请询问用户

## Notes

- All tasks including tests are required for this implementation
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties
- Unit tests validate specific examples and edge cases
