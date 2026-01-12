# Flowable 集成重构实现计划

## 概述

本实现计划将 user-portal 模块重构为完全依赖 Flowable 工作流引擎，通过 workflow-engine-core 模块调用 Flowable API。

## 任务列表

### 阶段 1: 核心任务操作重构（已完成）

- [x] 1.1 实现 WorkflowEngineClient 基础方法
  - ✅ `isAvailable()` - 检查 workflow-engine-core 是否可用
  - ✅ `claimTask()` - 认领任务
  - ✅ `unclaimTask()` - 取消认领任务
  - ✅ `delegateTask()` - 委托任务
  - ✅ `transferTask()` - 转办任务
  - ✅ `completeTask()` - 完成任务
  - ✅ `startProcess()` - 启动流程
  - ✅ `deployProcess()` - 部署流程
  - _需求: 1, 2, 3, 4, 5, 6_

- [x] 1.2 重构 TaskProcessComponent 使用 Flowable
  - ✅ `claimTask()` - 通过 WorkflowEngineClient 调用 Flowable
  - ✅ `unclaimTask()` - 通过 WorkflowEngineClient 调用 Flowable
  - ✅ `delegateTask()` - 通过 WorkflowEngineClient 调用 Flowable
  - ✅ `transferTask()` - 通过 WorkflowEngineClient 调用 Flowable
  - ✅ `handleApproval()` - 通过 WorkflowEngineClient 调用 Flowable
  - ✅ 所有方法在 workflow-engine-core 不可用时抛出 IllegalStateException
  - _需求: 1, 2, 3, 4, 5_

- [x] 1.3 重构 ProcessComponent 使用 Flowable
  - ✅ `startProcess()` - 通过 WorkflowEngineClient 调用 Flowable
  - ✅ 在 workflow-engine-core 不可用时抛出 IllegalStateException
  - _需求: 6_

- [x] 1.4 实现 workflow-engine-core API 端点
  - ✅ `/api/v1/tasks/{taskId}/claim` - 认领任务
  - ✅ `/api/v1/tasks/{taskId}/unclaim` - 取消认领任务
  - ✅ `/api/v1/tasks/{taskId}/delegate` - 委托任务
  - ✅ `/api/v1/tasks/{taskId}/transfer` - 转办任务
  - ✅ `/api/v1/tasks/{taskId}/complete` - 完成任务
  - _需求: 1, 2, 3, 4, 5_

- [x] 1.5 实现 TaskManagerComponent 方法
  - ✅ `claimTask()` - 调用 Flowable TaskService.claim()
  - ✅ `unclaimTask()` - 调用 Flowable TaskService.unclaim()
  - ✅ `delegateTask()` - 调用 Flowable TaskService.delegateTask()
  - ✅ `transferTask()` - 调用 Flowable TaskService.setAssignee()
  - ✅ `completeTask()` - 调用 Flowable TaskService.complete()
  - _需求: 1, 2, 3, 4, 5_

### 阶段 2: 测试修复（已完成）

- [x] 2.1 修复 TaskProcessProperties 测试
  - ✅ 添加 WorkflowEngineClient 依赖
  - ✅ 添加 Flowable 调用的 Mock
  - _需求: 1, 2, 3, 4, 5_

- [x] 2.2 修复 TaskQueryProperties 测试
  - ✅ 添加 ProcessHistoryRepository 依赖
  - _需求: 8_

- [x] 2.3 修复 ProcessOperationProperties 测试
  - ✅ 添加 WorkflowEngineClient Mock
  - ✅ 使用 Spy 处理 getFunctionUnitContent 方法
  - _需求: 6_

### 阶段 3: 待完成工作

- [x] 3.1 实现任务回退功能
  - [x] 在 workflow-engine-core 添加 `/api/v1/tasks/{taskId}/return` 端点
  - [x] 在 TaskManagerComponent 实现 `returnTask()` 方法
  - [x] 在 WorkflowEngineClient 添加 `returnTask()` 方法
  - [x] 重构 `TaskProcessComponent.handleReturn()` 使用 Flowable
  - _需求: 7_

- [x] 3.2 完善任务查询功能
  - [x] 重构 TaskQueryComponent 从 Flowable 获取任务列表
  - [x] 移除对本地内存存储的依赖
  - [x] 确保多维度查询（用户、虚拟组、部门角色）正常工作
  - [x] 添加 `getUserAllVisibleTasks()` 方法到 WorkflowEngineClient
  - [x] 添加 `getTaskById()` 方法到 WorkflowEngineClient
  - [x] 添加 `countUserTasks()` 方法到 WorkflowEngineClient
  - [x] 更新 TaskQueryProperties 测试
  - [x] 更新 TaskProcessProperties 测试
  - _需求: 8_

### 阶段 4: 文档和架构指南

- [x] 4.1 创建架构指南文档
  - ✅ 创建 `.kiro/steering/workflow-engine-architecture.md`
  - ✅ 记录 Flowable 集成架构
  - ✅ 记录禁止本地回退的规则
  - ✅ 记录 API 调用路径
  - ✅ 记录错误处理模式

- [x] 4.2 创建需求规格文档
  - ✅ 创建 `.kiro/specs/flowable-integration/requirements.md`
  - ✅ 记录所有需求和验收标准
  - ✅ 记录已完成和待完成的工作

## 验证检查清单

### 已验证

- [x] `TaskProcessComponent.claimTask()` 在 workflow-engine-core 不可用时抛出异常
- [x] `TaskProcessComponent.unclaimTask()` 在 workflow-engine-core 不可用时抛出异常
- [x] `TaskProcessComponent.delegateTask()` 在 workflow-engine-core 不可用时抛出异常
- [x] `TaskProcessComponent.transferTask()` 在 workflow-engine-core 不可用时抛出异常
- [x] `TaskProcessComponent.handleApproval()` 在 workflow-engine-core 不可用时抛出异常
- [x] `ProcessComponent.startProcess()` 在 workflow-engine-core 不可用时抛出异常
- [x] 所有方法不包含本地回退逻辑
- [x] 测试用例正确 Mock Flowable 调用

### 待验证

- [x] `TaskProcessComponent.handleReturn()` 使用 Flowable
- [x] `TaskQueryComponent` 从 Flowable 获取任务列表

## 注意事项

1. **禁止本地回退**: 所有工作流操作必须通过 Flowable 完成，不允许本地回退实现
2. **错误消息**: 当 workflow-engine-core 不可用时，统一使用错误消息 "Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动"
3. **本地状态同步**: Flowable 操作成功后，需要同步更新本地任务状态以保持一致性
4. **审计日志**: 委托和转办操作需要记录审计日志
