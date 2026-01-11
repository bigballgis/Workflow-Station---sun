# 功能单元管理实现任务

## 概述

本任务列表基于功能单元管理需求和设计文档，实现 Admin Center 的删除和启用/禁用功能，以及 User Portal 的功能单元访问控制。

## 任务

- [x] 1. 数据库和实体层变更
  - [x] 1.1 创建数据库迁移脚本添加 enabled 字段
    - 创建 `deploy/init-scripts/17-function-unit-enabled.sql`
    - 添加 `enabled` 字段到 `admin_function_units` 表
    - 添加索引优化查询
    - _Requirements: 2.1, 2.3_
  - [x] 1.2 更新 FunctionUnit 实体类
    - 添加 `enabled` 字段，默认值为 `true`
    - _Requirements: 2.3_

- [x] 2. Admin Center 后端 - 删除功能
  - [x] 2.1 创建 DeletePreviewResponse DTO
    - 包含功能单元名称、各类关联数据数量、运行实例信息
    - _Requirements: 1.4_
  - [x] 2.2 实现 getDeletePreview 方法
    - 在 FunctionUnitManagerComponent 中实现
    - 统计表单、流程、数据表、权限配置、部署记录数量
    - 检查运行中的流程实例
    - _Requirements: 1.4, 1.8_
  - [x] 2.3 实现 deleteFunctionUnitCascade 方法
    - 级联删除所有关联内容
    - 检查运行实例并阻止删除
    - _Requirements: 1.8, 1.9_
  - [x] 2.4 添加删除预览 API 端点
    - `GET /function-units/{id}/delete-preview`
    - _Requirements: 1.4_
  - [x] 2.5 编写属性测试 - 删除预览数量正确性
    - **Property 1: 删除预览数量正确性**
    - **Validates: Requirements 1.4**
  - [x] 2.6 编写属性测试 - 级联删除完整性
    - **Property 4: 级联删除完整性**
    - **Validates: Requirements 1.9**

- [x] 3. Admin Center 后端 - 启用/禁用功能
  - [x] 3.1 创建 EnabledRequest 和 EnabledResponse DTO
    - _Requirements: 2.3_
  - [x] 3.2 实现 setEnabled 方法
    - 在 FunctionUnitManagerComponent 中实现
    - 更新 enabled 字段
    - _Requirements: 2.3, 2.6, 2.7_
  - [x] 3.3 添加启用状态切换 API 端点
    - `PUT /function-units/{id}/enabled`
    - _Requirements: 2.3_
  - [x] 3.4 编写属性测试 - 启用状态切换正确性
    - **Property 5: 启用状态切换正确性**
    - **Validates: Requirements 2.3, 2.6, 2.7**

- [x] 4. Checkpoint - 后端功能验证
  - 确保所有后端测试通过
  - 验证 API 端点正常工作
  - 如有问题请询问用户

- [x] 5. Admin Center 前端 - 删除功能
  - [x] 5.1 添加删除预览 API 调用
    - 在 `functionUnit.ts` 中添加 `getDeletePreview` 方法
    - _Requirements: 1.4_
  - [x] 5.2 创建删除确认对话框组件
    - 红色警告样式
    - 显示关联数据数量
    - 名称输入确认
    - 3秒倒计时按钮
    - _Requirements: 1.2, 1.3, 1.4, 1.5, 1.6, 1.7_
  - [x] 5.3 在功能单元列表添加删除按钮
    - 集成删除确认对话框
    - 处理删除成功/失败反馈
    - _Requirements: 1.1, 1.10_

- [x] 6. Admin Center 前端 - 启用/禁用功能
  - [x] 6.1 添加启用状态切换 API 调用
    - 在 `functionUnit.ts` 中添加 `setEnabled` 方法
    - _Requirements: 2.3_
  - [x] 6.2 在功能单元列表添加启用/禁用开关
    - 使用 Element Plus Switch 组件
    - 禁用时显示确认提示
    - _Requirements: 2.1, 2.2_
  - [x] 6.3 更新 FunctionUnit 类型定义
    - 添加 `enabled` 字段
    - _Requirements: 2.1_

- [x] 7. Checkpoint - Admin Center 功能验证
  - 确保删除功能正常工作
  - 确保启用/禁用开关正常工作
  - 如有问题请询问用户

- [x] 8. User Portal 后端 - 访问控制
  - [x] 8.1 更新 FunctionUnitAccessComponent
    - 在获取可访问功能单元时过滤 enabled=false 的记录
    - _Requirements: 2.4, 3.1_
  - [x] 8.2 添加功能单元启用状态检查
    - 在获取功能单元详情时检查 enabled 状态
    - 禁用时返回 403 错误
    - _Requirements: 2.5, 4.9_
  - [x] 8.3 编写属性测试 - 禁用状态访问控制
    - **Property 6: 禁用状态访问控制**
    - **Validates: Requirements 2.4, 2.5, 4.9**
  - [x] 8.4 编写属性测试 - 功能单元列表过滤
    - **Property 7: 功能单元列表过滤**
    - **Validates: Requirements 3.1**

- [x] 9. User Portal 前端 - 访问控制
  - [x] 9.1 处理功能单元禁用状态
    - 在启动页面检查功能单元状态
    - 禁用时显示提示并阻止访问
    - _Requirements: 2.5, 4.9_

- [x] 10. Final Checkpoint - 完整功能验证
  - 确保所有测试通过
  - 验证 Admin Center 删除和启用/禁用功能
  - 验证 User Portal 访问控制
  - 如有问题请询问用户

## 注意事项

- 所有任务均为必做任务
- 每个任务都引用了具体的需求以便追溯
- 属性测试验证核心正确性属性
- Checkpoint 任务用于阶段性验证
