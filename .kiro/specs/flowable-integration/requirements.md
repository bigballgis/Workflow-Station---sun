# Flowable 集成重构需求文档

## 介绍

本需求文档定义了将 `user-portal` 模块重构为完全依赖 Flowable 工作流引擎的工作。所有工作流相关功能必须通过 `workflow-engine-core` 模块调用 Flowable，禁止任何本地回退实现。

## 架构原则

```
┌─────────────────┐     HTTP      ┌──────────────────────┐     Flowable API    ┌─────────────┐
│   user-portal   │ ────────────> │ workflow-engine-core │ ─────────────────> │   Flowable  │
│                 │               │                      │                     │   Engine    │
└─────────────────┘               └──────────────────────┘                     └─────────────┘
```

**核心规则**：当 `workflow-engine-core` 不可用时，必须抛出异常 `"Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动"`，而不是使用本地实现。

## 术语表

- **WorkflowEngineClient**: user-portal 中的 HTTP 客户端，用于调用 workflow-engine-core 的 REST API
- **TaskProcessComponent**: user-portal 中的任务处理组件，负责任务认领、委托、转办、完成等操作
- **ProcessComponent**: user-portal 中的流程组件，负责流程启动等操作
- **TaskQueryComponent**: user-portal 中的任务查询组件，负责查询用户待办任务
- **TaskManagerComponent**: workflow-engine-core 中的任务管理组件，封装 Flowable TaskService
- **ProcessEngineComponent**: workflow-engine-core 中的流程引擎组件，封装 Flowable RuntimeService

## 需求

### 需求 1: 任务认领功能

**用户故事**: 作为任务处理人，我希望能够认领分配给虚拟组或部门角色的任务，以便开始处理该任务。

#### 验收标准

1. WHEN 用户调用 `TaskProcessComponent.claimTask()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 `/api/v1/tasks/{taskId}/claim` 接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException` 并显示错误消息 "Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动"
3. WHEN Flowable 认领成功后，THE 系统 SHALL 同步更新本地任务状态（assignmentType 设为 "USER"，assignee 设为当前用户）
4. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 2: 取消认领功能

**用户故事**: 作为任务处理人，我希望能够取消已认领的任务，以便其他人可以处理该任务。

#### 验收标准

1. WHEN 用户调用 `TaskProcessComponent.unclaimTask()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 `/api/v1/tasks/{taskId}/unclaim` 接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. WHEN Flowable 取消认领成功后，THE 系统 SHALL 恢复任务的原始分配类型和分配人
4. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 3: 任务委托功能

**用户故事**: 作为任务处理人，我希望能够将任务委托给其他用户处理，以便在我无法处理时由他人代为完成。

#### 验收标准

1. WHEN 用户调用 `TaskProcessComponent.delegateTask()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 `/api/v1/tasks/{taskId}/delegate` 接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. WHEN Flowable 委托成功后，THE 系统 SHALL 更新本地任务状态（assignmentType 设为 "DELEGATED"，记录委托人和被委托人）
4. THE 系统 SHALL 记录委托审计日志
5. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 4: 任务转办功能

**用户故事**: 作为任务处理人，我希望能够将任务转办给其他用户，以便完全移交任务处理权。

#### 验收标准

1. WHEN 用户调用 `TaskProcessComponent.transferTask()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 `/api/v1/tasks/{taskId}/transfer` 接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. WHEN Flowable 转办成功后，THE 系统 SHALL 更新本地任务状态（assignmentType 设为 "USER"，assignee 设为目标用户，清除委托信息）
4. THE 系统 SHALL 记录转办审计日志
5. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 5: 任务完成功能

**用户故事**: 作为任务处理人，我希望能够完成任务（审批通过或拒绝），以便流程继续执行。

#### 验收标准

1. WHEN 用户调用 `TaskProcessComponent.handleApproval()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 `/api/v1/tasks/{taskId}/complete` 接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. WHEN Flowable 完成任务成功后，THE 系统 SHALL 从本地任务存储中移除该任务
4. THE 系统 SHALL 传递审批动作（APPROVE/REJECT）和表单数据作为流程变量
5. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 6: 流程启动功能

**用户故事**: 作为业务用户，我希望能够启动新的流程实例，以便开始业务流程处理。

#### 验收标准

1. WHEN 用户调用 `ProcessComponent.startProcess()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 `/api/v1/processes/instances` 接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. WHEN Flowable 启动流程成功后，THE 系统 SHALL 返回流程实例信息
4. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 7: 任务回退功能（待实现）

**用户故事**: 作为任务处理人，我希望能够将任务回退到之前的节点，以便重新处理。

#### 验收标准

1. WHEN 用户调用 `TaskProcessComponent.handleReturn()` 时，THE 系统 SHALL 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的回退接口
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. THE 系统 SHALL 支持回退到指定的历史节点
4. THE 系统 SHALL NOT 实现任何本地回退逻辑

### 需求 8: 任务查询功能（待实现）

**用户故事**: 作为业务用户，我希望能够查询我的待办任务列表，以便了解需要处理的工作。

#### 验收标准

1. WHEN 用户调用 `TaskQueryComponent` 的查询方法时，THE 系统 SHALL 通过 `WorkflowEngineClient` 从 Flowable 获取任务列表
2. IF `workflow-engine-core` 不可用，THEN THE 系统 SHALL 抛出 `IllegalStateException`
3. THE 系统 SHALL 支持按用户、虚拟组、部门角色等多维度查询
4. THE 系统 SHALL NOT 使用本地内存存储作为主要数据源

## 已完成的重构

| 功能 | user-portal 组件 | WorkflowEngineClient 方法 | workflow-engine-core API | 状态 |
|------|-----------------|-------------------------|------------------------|------|
| 认领任务 | `TaskProcessComponent.claimTask()` | `claimTask()` | `/api/v1/tasks/{taskId}/claim` | ✅ 完成 |
| 取消认领 | `TaskProcessComponent.unclaimTask()` | `unclaimTask()` | `/api/v1/tasks/{taskId}/unclaim` | ✅ 完成 |
| 委托任务 | `TaskProcessComponent.delegateTask()` | `delegateTask()` | `/api/v1/tasks/{taskId}/delegate` | ✅ 完成 |
| 转办任务 | `TaskProcessComponent.transferTask()` | `transferTask()` | `/api/v1/tasks/{taskId}/transfer` | ✅ 完成 |
| 完成任务 | `TaskProcessComponent.handleApproval()` | `completeTask()` | `/api/v1/tasks/{taskId}/complete` | ✅ 完成 |
| 启动流程 | `ProcessComponent.startProcess()` | `startProcess()` | `/api/v1/processes/instances` | ✅ 完成 |
| 回退任务 | `TaskProcessComponent.handleReturn()` | - | - | ⏳ 待实现 |
| 任务查询 | `TaskQueryComponent` | `getUserTasks()` | `/api/v1/tasks` | ⏳ 待完善 |

## 参考文件

- `#[[file:backend/user-portal/src/main/java/com/portal/client/WorkflowEngineClient.java]]`
- `#[[file:backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java]]`
- `#[[file:backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java]]`
- `#[[file:backend/workflow-engine-core/src/main/java/com/workflow/controller/TaskController.java]]`
- `#[[file:backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java]]`
- `#[[file:.kiro/steering/workflow-engine-architecture.md]]`
