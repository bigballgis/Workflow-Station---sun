# 工作流引擎架构指南

## 核心原则

**本项目使用 Flowable 作为工作流引擎，所有工作流相关功能必须通过 Flowable 实现。**

## 架构概述

```
┌─────────────────┐     HTTP      ┌──────────────────────┐     Flowable API    ┌─────────────┐
│   user-portal   │ ────────────> │ workflow-engine-core │ ─────────────────> │   Flowable  │
│                 │               │                      │                     │   Engine    │
│ - ProcessComponent              │ - ProcessController  │                     │             │
│ - TaskProcessComponent          │ - TaskController     │                     │             │
│ - TaskQueryComponent            │ - ProcessEngineComponent                   │             │
│ - WorkflowEngineClient          │ - TaskManagerComponent                     │             │
└─────────────────┘               └──────────────────────┘                     └─────────────┘
```

## 关键规则

### 1. 禁止本地回退实现

- **不允许**在 `user-portal` 中实现任何工作流逻辑的本地回退
- 当 `workflow-engine-core` 不可用时，必须抛出异常，而不是使用本地实现
- 错误消息示例：`"Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动"`

### 2. 模块职责划分

#### user-portal 模块
- 通过 `WorkflowEngineClient` 调用 `workflow-engine-core` 的 REST API
- 负责用户界面相关的业务逻辑（权限检查、收藏、草稿等）
- 不直接操作 Flowable 引擎

#### workflow-engine-core 模块
- 封装所有 Flowable 引擎操作
- 提供 REST API 供其他模块调用
- 包含 `ProcessEngineComponent` 和 `TaskManagerComponent`

### 3. API 调用路径

| 功能 | user-portal | workflow-engine-core | Flowable |
|------|-------------|---------------------|----------|
| 启动流程 | `ProcessComponent.startProcess()` | `ProcessController.startProcessInstance()` | `RuntimeService.startProcessInstanceByKey()` |
| 完成任务 | `TaskProcessComponent.handleApproval()` | `TaskController.completeTask()` | `TaskService.complete()` |
| 认领任务 | `TaskProcessComponent.claimTask()` | `TaskController.claimTask()` | `TaskService.claim()` |
| 委托任务 | `TaskProcessComponent.delegateTask()` | `TaskController.delegateTask()` | `TaskService.delegateTask()` |
| 部署流程 | `WorkflowEngineClient.deployProcess()` | `ProcessController.deployProcessDefinition()` | `RepositoryService.createDeployment()` |

### 4. 配置要求

```yaml
# user-portal/application.yml
workflow-engine:
  url: http://localhost:8091
  enabled: true  # 必须为 true
```

### 5. 错误处理模式

```java
// 正确的实现方式
public void someWorkflowOperation() {
    if (!workflowEngineClient.isAvailable()) {
        throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
    }
    
    Optional<Map<String, Object>> result = workflowEngineClient.someOperation();
    
    if (result.isEmpty()) {
        throw new PortalException("500", "操作失败");
    }
    
    // 处理结果...
}

// 错误的实现方式 - 禁止使用
public void someWorkflowOperation() {
    if (workflowEngineClient.isAvailable()) {
        // 调用 Flowable
    } else {
        // 本地实现 - 禁止！
    }
}
```

## 已完成的重构

以下组件已重构为使用 Flowable：

### user-portal
- `ProcessComponent.startProcess()` - 通过 Flowable 启动流程
- `TaskProcessComponent.claimTask()` - 通过 Flowable 认领任务
- `TaskProcessComponent.unclaimTask()` - 通过 Flowable 取消认领任务
- `TaskProcessComponent.delegateTask()` - 通过 Flowable 委托任务
- `TaskProcessComponent.transferTask()` - 通过 Flowable 转办任务
- `TaskProcessComponent.handleApproval()` - 通过 Flowable 完成任务

### workflow-engine-core
- `ProcessController` - 调用 `ProcessEngineComponent`
- `TaskController` - 调用 `TaskManagerComponent`
  - `/api/v1/tasks/{taskId}/claim` - 认领任务
  - `/api/v1/tasks/{taskId}/unclaim` - 取消认领任务
  - `/api/v1/tasks/{taskId}/delegate` - 委托任务
  - `/api/v1/tasks/{taskId}/transfer` - 转办任务
  - `/api/v1/tasks/{taskId}/complete` - 完成任务

### WorkflowEngineClient
- `claimTask()` - 认领任务
- `unclaimTask()` - 取消认领任务
- `delegateTask()` - 委托任务
- `transferTask()` - 转办任务
- `completeTask()` - 完成任务
- `deployProcess()` - 部署流程
- `startProcess()` - 启动流程

## 待完成的工作

以下功能可能需要进一步完善：

1. `TaskProcessComponent.handleReturn()` - 回退任务（需要实现 Flowable 回退逻辑）
2. `TaskQueryComponent` - 任务查询（需要从 Flowable 获取任务列表）

## 任务分配机制

### 9种标准分配类型

任务分配使用以下9种标准类型，定义在 `AssigneeType` 枚举中：

#### 直接分配类型（3种）

| 类型 | 代码 | 说明 | 是否需要认领 |
|------|------|------|-------------|
| 职能经理 | `FUNCTION_MANAGER` | 发起人的职能经理 | 否（直接分配） |
| 实体经理 | `ENTITY_MANAGER` | 发起人的实体经理 | 否（直接分配） |
| 流程发起人 | `INITIATOR` | 流程发起人 | 否（直接分配） |

#### 基于当前人业务单元的角色分配（2种）

| 类型 | 代码 | 说明 | 是否需要认领 | 需要参数 |
|------|------|------|-------------|---------|
| 当前人业务单元角色 | `CURRENT_BU_ROLE` | 当前处理人所在业务单元中拥有指定角色的用户 | 是 | roleId |
| 当前人上级业务单元角色 | `CURRENT_PARENT_BU_ROLE` | 当前处理人上级业务单元中拥有指定角色的用户 | 是 | roleId |

#### 基于发起人业务单元的角色分配（2种）

| 类型 | 代码 | 说明 | 是否需要认领 | 需要参数 |
|------|------|------|-------------|---------|
| 发起人业务单元角色 | `INITIATOR_BU_ROLE` | 发起人所在业务单元中拥有指定角色的用户 | 是 | roleId |
| 发起人上级业务单元角色 | `INITIATOR_PARENT_BU_ROLE` | 发起人上级业务单元中拥有指定角色的用户 | 是 | roleId |

#### 其他角色分配类型（2种）

| 类型 | 代码 | 说明 | 是否需要认领 | 需要参数 |
|------|------|------|-------------|---------|
| 指定业务单元角色 | `FIXED_BU_ROLE` | 指定业务单元中拥有指定角色的用户 | 是 | businessUnitId, roleId |
| BU无关型角色 | `BU_UNBOUNDED_ROLE` | 拥有指定BU无关型角色的用户（通过虚拟组） | 是 | roleId |

### 核心组件

- `AssigneeType` - 分配类型枚举 (`workflow-engine-core`)
- `TaskAssigneeResolver` - 处理人解析服务 (`workflow-engine-core`)
- `TaskAssignmentListener` - 任务创建监听器 (`workflow-engine-core`)
- `AdminCenterClient` - 用户/业务单元/角色信息查询客户端 (`workflow-engine-core`)
- `TaskAssignmentQueryService` - 任务分配查询服务 (`admin-center`)
- `TaskAssignmentController` - 任务分配查询API (`admin-center`)

### 分配流程

1. 流程启动时，`initiator` 变量被设置为发起人ID
2. 任务创建时，`TaskAssignmentListener` 监听 `TASK_CREATED` 事件
3. 监听器从 BPMN 扩展属性中读取 `assigneeType`、`roleId`、`businessUnitId`
4. `TaskAssigneeResolver` 根据分配类型解析实际处理人
5. 直接分配类型：设置 `assignee`
6. 认领类型：设置 `candidateUsers`

### BPMN 配置示例

```xml
<!-- 直接分配类型 -->
<bpmn:userTask id="Task_Approval" name="主管审批">
  <bpmn:extensionElements>
    <custom:properties>
      <custom:property name="assigneeType" value="ENTITY_MANAGER"/>
      <custom:property name="assigneeLabel" value="实体经理"/>
    </custom:properties>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- 基于角色的分配类型 -->
<bpmn:userTask id="Task_Review" name="财务审核">
  <bpmn:extensionElements>
    <custom:properties>
      <custom:property name="assigneeType" value="INITIATOR_BU_ROLE"/>
      <custom:property name="roleId" value="role-finance-reviewer"/>
      <custom:property name="assigneeLabel" value="发起人业务单元角色: 财务审核员"/>
    </custom:properties>
  </bpmn:extensionElements>
</bpmn:userTask>

<!-- 指定业务单元角色 -->
<bpmn:userTask id="Task_HQ_Approval" name="总部审批">
  <bpmn:extensionElements>
    <custom:properties>
      <custom:property name="assigneeType" value="FIXED_BU_ROLE"/>
      <custom:property name="businessUnitId" value="bu-headquarters"/>
      <custom:property name="roleId" value="role-approver"/>
      <custom:property name="assigneeLabel" value="指定业务单元角色: 总部审批员"/>
    </custom:properties>
  </bpmn:extensionElements>
</bpmn:userTask>
```

### 角色类型说明

- **BU_BOUNDED 角色**：与业务单元绑定的角色，用户在特定业务单元中拥有该角色
- **BU_UNBOUNDED 角色**：与业务单元无关的角色，通过虚拟组分配给用户

## 参考文件

- `backend/user-portal/src/main/java/com/portal/client/WorkflowEngineClient.java`
- `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`
- `backend/user-portal/src/main/java/com/portal/component/TaskProcessComponent.java`
- `backend/user-portal/src/main/java/com/portal/service/BpmnParserService.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/controller/ProcessController.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/controller/TaskController.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/component/ProcessEngineComponent.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/component/TaskManagerComponent.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/enums/AssigneeType.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/service/TaskAssigneeResolver.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/listener/TaskAssignmentListener.java`
- `backend/workflow-engine-core/src/main/java/com/workflow/client/AdminCenterClient.java`
