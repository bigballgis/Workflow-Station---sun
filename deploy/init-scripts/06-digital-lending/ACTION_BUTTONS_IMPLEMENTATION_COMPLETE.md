# Digital Lending - Action Buttons Implementation Complete

## 问题总结

User Portal 任务详情页面无法显示 BPMN 中定义的自定义 action 按钮。

## 根本原因

1. **架构限制**：Developer Workstation 和 `dw_*` 表只在 dev 环境存在，不会部署到 SIT/UAT/PROD
2. **缺少生产表**：没有生产环境可用的 action definitions 表
3. **缺少 API**：User Portal 后端没有返回任务的 actions
4. **缺少依赖**：User Portal 缺少 Flowable 依赖来解析 BPMN

## 解决方案

### 1. 创建生产环境 Action 表

**文件**: `deploy/init-scripts/00-schema/07-add-action-definitions-table.sql`

创建了 `sys_action_definitions` 表（使用 `sys_` 前缀，部署到所有环境）：

```sql
CREATE TABLE IF NOT EXISTS sys_action_definitions (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    action_name VARCHAR(100) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    description TEXT,
    config_json JSONB DEFAULT '{}'::jsonb,
    icon VARCHAR(50),
    button_color VARCHAR(20),
    is_default BOOLEAN DEFAULT false,
    ...
    CONSTRAINT fk_action_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES sys_function_units(id) ON DELETE CASCADE
);
```

### 2. 插入 Digital Lending Actions

**文件**: `deploy/init-scripts/06-digital-lending/07-copy-actions-to-admin.sql`

插入了 4 个 actions：

| ID | Action Name | Type | Icon | Color |
|----|-------------|------|------|-------|
| action-dl-verify-docs | Verify Documents | APPROVE | check-circle | primary |
| action-dl-approve-loan | Approve Loan | APPROVE | check | success |
| action-dl-reject-loan | Reject Loan | REJECT | times-circle | danger |
| action-dl-request-info | Request Additional Info | FORM_POPUP | file-alt | warning |

### 3. 创建 Backend Entities 和 Repositories

**Admin Center**:
- `backend/admin-center/src/main/java/com/admin/entity/ActionDefinition.java`
- `backend/admin-center/src/main/java/com/admin/repository/ActionDefinitionRepository.java`
- `backend/admin-center/src/main/java/com/admin/controller/ActionDefinitionController.java`

**User Portal**:
- `backend/user-portal/src/main/java/com/portal/entity/ActionDefinition.java`
- `backend/user-portal/src/main/java/com/portal/repository/ActionDefinitionRepository.java`
- `backend/user-portal/src/main/java/com/portal/dto/TaskActionInfo.java`

### 4. 创建 TaskActionService

**文件**: `backend/user-portal/src/main/java/com/portal/service/TaskActionService.java`

功能：
1. 从 Flowable 获取任务信息
2. 解析 BPMN 的 extensionElements 提取 actionIds
3. 从 `sys_action_definitions` 表查询 action 定义
4. 返回 `List<TaskActionInfo>`

### 5. 集成到 TaskQueryComponent

**文件**: `backend/user-portal/src/main/java/com/portal/component/TaskQueryComponent.java`

在 `getTaskById()` 方法中调用 `TaskActionService.getTaskActions()`，将 actions 添加到 `TaskInfo` DTO。

### 6. 添加 Flowable 依赖

**文件**: `backend/user-portal/pom.xml`

```xml
<dependency>
    <groupId>org.flowable</groupId>
    <artifactId>flowable-engine</artifactId>
    <version>7.0.1</version>
</dependency>
```

### 7. 更新测试文件

修复了测试文件以包含 `TaskActionService` mock：
- `backend/user-portal/src/test/java/com/portal/properties/TaskQueryProperties.java`
- `backend/user-portal/src/test/java/com/portal/properties/TaskProcessProperties.java`

## 数据流

```
1. User Portal 调用 TaskQueryComponent.getTaskById(taskId)
   ↓
2. TaskQueryComponent 调用 WorkflowEngineClient.getTaskById()
   ↓
3. TaskQueryComponent 调用 TaskActionService.getTaskActions(taskId)
   ↓
4. TaskActionService 从 Flowable 获取 BPMN 模型
   ↓
5. TaskActionService 解析 extensionElements 提取 actionIds
   ↓
6. TaskActionService 从 sys_action_definitions 表查询 actions
   ↓
7. TaskInfo 包含 actions 列表返回给前端
```

## API 响应示例

```json
{
  "code": "SUCCESS",
  "data": {
    "taskId": "xxx",
    "taskName": "Verify Documents",
    "actions": [
      {
        "actionId": "action-dl-verify-docs",
        "actionName": "Verify Documents",
        "actionType": "APPROVE",
        "icon": "check-circle",
        "buttonColor": "primary",
        "configJson": "{}"
      },
      {
        "actionId": "action-dl-approve-loan",
        "actionName": "Approve Loan",
        "actionType": "APPROVE",
        "icon": "check",
        "buttonColor": "success",
        "configJson": "{}"
      },
      {
        "actionId": "action-dl-reject-loan",
        "actionName": "Reject Loan",
        "actionType": "REJECT",
        "icon": "times-circle",
        "buttonColor": "danger",
        "configJson": "{}"
      },
      {
        "actionId": "action-dl-request-info",
        "actionName": "Request Additional Info",
        "actionType": "FORM_POPUP",
        "icon": "file-alt",
        "buttonColor": "warning",
        "configJson": "{\"formId\": 7}"
      }
    ]
  }
}
```

## 下一步

### Frontend 集成

前端需要更新任务详情页面来渲染自定义 action 按钮：

```vue
<template>
  <div class="task-actions">
    <el-button
      v-for="action in task.actions"
      :key="action.actionId"
      :type="getButtonType(action.buttonColor)"
      :icon="action.icon"
      @click="handleAction(action)"
    >
      {{ action.actionName }}
    </el-button>
  </div>
</template>

<script setup>
const getButtonType = (color) => {
  const colorMap = {
    'primary': 'primary',
    'success': 'success',
    'danger': 'danger',
    'warning': 'warning'
  };
  return colorMap[color] || 'default';
};

const handleAction = (action) => {
  if (action.actionType === 'FORM_POPUP') {
    // Parse configJson to get formId
    const config = JSON.parse(action.configJson);
    openFormDialog(config.formId);
  } else {
    // Complete task with this action
    completeTask(task.taskId, action.actionId);
  }
};
</script>
```

## 测试步骤

1. 启动一个新的 Digital Lending 流程
2. 打开 "Verify Documents" 任务
3. 调用 API: `GET /api/portal/tasks/{taskId}`
4. 验证响应包含 4 个 actions
5. 前端应该显示 4 个自定义按钮而不是通用按钮

## 部署到其他环境

### SIT/UAT/PROD 部署步骤

1. **运行 schema 脚本**:
   ```sql
   -- 创建 sys_action_definitions 表
   \i deploy/init-scripts/00-schema/07-add-action-definitions-table.sql
   ```

2. **导入 Function Unit 时自动创建 actions**:
   - Export/Import 组件已经支持导出/导入 actions
   - 当导入 Digital Lending function unit 时，actions 会自动创建

3. **或手动插入 actions**:
   ```sql
   -- 插入 Digital Lending actions
   \i deploy/init-scripts/06-digital-lending/07-copy-actions-to-admin.sql
   ```

## 架构优势

1. **生产环境可用**: `sys_action_definitions` 表部署到所有环境
2. **与 Function Unit 关联**: 通过 FK 关联到 `sys_function_units`
3. **支持导入导出**: Export/Import 组件已支持 actions
4. **灵活配置**: `config_json` 字段支持任意配置（formId, apiEndpoint 等）
5. **前后端分离**: 后端提供 actions 数据，前端负责渲染

## 状态

- ✅ 创建 `sys_action_definitions` 表
- ✅ 插入 Digital Lending actions
- ✅ 创建 Backend Entities 和 Repositories
- ✅ 创建 TaskActionService
- ✅ 集成到 TaskQueryComponent
- ✅ 添加 Flowable 依赖
- ✅ 更新测试文件
- ✅ User Portal 重新编译和部署
- ⏳ Frontend 集成（待完成）

---

**日期**: 2026-02-06  
**状态**: Backend 实现完成，等待 Frontend 集成  
**优先级**: High
