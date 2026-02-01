# 任务分配问题修复说明

## 问题描述

当使用 Fixed BU Role 分配任务时，Adam 用户无法在 My Applications 中看到可以认领的任务。

## 问题原因

1. **任务分配逻辑问题**：
   - `TaskAssignmentListener` 在设置候选人时，优先使用 `candidateGroup`（角色ID）
   - 对于 Fixed BU Role，应该使用 `candidateUsers`（具体用户列表）
   - 因为 Flowable 不知道业务单元的角色绑定关系，无法通过 `candidateGroup` 正确查询

2. **用户数据缺失**：
   - Adam 没有绑定 HERMES_DEV 角色
   - Adam 没有在 Web Frontend Team (DEV_WEB) 业务单元中

## 已完成的修复

### 1. 修复任务分配逻辑

修改了 `TaskAssignmentListener.java`，对于 Fixed BU Role 和基于业务单元的角色分配，优先使用 `candidateUsers`：

```java
if (result.getCandidateUsers() != null && !result.getCandidateUsers().isEmpty()) {
    // 优先使用候选用户列表（适用于 Fixed BU Role 等需要具体用户列表的情况）
    for (String candidateUser : result.getCandidateUsers()) {
        taskService.addCandidateUser(taskId, candidateUser);
    }
    log.info("Task {} set candidate users ({}): {}", taskId, result.getCandidateUsers().size(), result.getCandidateUsers());
}
```

### 2. 为 Adam 绑定角色和业务单元

已执行 SQL 为 Adam 绑定：
- 业务单元：Web Frontend Team (DEV_WEB, DEPT-DEV-WEB)
- 角色：Hermes Dev (HERMES_DEV, d0a8771a-e0ec-4d33-992b-14e32076a7c6)

```sql
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by) 
VALUES ('ubur-adam-...', 'bfe0805e-adcc-43cd-9c07-c368f3b947fb', 'DEPT-DEV-WEB', 'd0a8771a-e0ec-4d33-992b-14e32076a7c6', NOW(), 'system');
```

## 待处理的问题

### 现有任务没有候选人

当前运行中的任务 `2e714f7a-face-11f0-a8ee-fec7c790a04a` (Department Review) 没有设置候选人。

**解决方案**：
1. 重启 workflow-engine-core 服务（已修复的代码会自动处理新创建的任务）
2. 对于现有任务，需要手动添加候选人，或者重新提交申请

### 手动修复现有任务的候选人

如果需要修复现有任务，可以通过以下方式：

1. **通过 API 调用**（如果 workflow-engine-core 服务运行）：
   ```bash
   curl -X POST http://localhost:8081/api/v1/tasks/2e714f7a-face-11f0-a8ee-fec7c790a04a/candidates \
     -H "Content-Type: application/json" \
     -d '{"userIds": ["bfe0805e-adcc-43cd-9c07-c368f3b947fb"]}'
   ```

2. **直接操作数据库**（不推荐，但可以临时使用）：
   ```sql
   -- 注意：这需要了解 Flowable 的内部结构，不建议直接操作
   ```

## 验证步骤

1. **重启 workflow-engine-core 服务**：
   ```bash
   cd backend/workflow-engine-core
   mvn spring-boot:run
   ```

2. **使用 Robert Sun 重新提交申请**：
   - 访问 http://localhost:3001/processes/start/fu-purchase-request
   - 提交新的申请

3. **使用 Adam 登录并检查任务**：
   - 访问 http://localhost:3001
   - 登录 Adam 账户
   - 检查 My Applications 中是否有可认领的任务

## 代码变更

- `backend/workflow-engine-core/src/main/java/com/workflow/listener/TaskAssignmentListener.java`
  - 修改了任务分配逻辑，优先使用 `candidateUsers` 而不是 `candidateGroup` 对于 Fixed BU Role

## 数据库变更

- `sys_user_business_unit_roles` 表：为 Adam 添加了角色和业务单元绑定
