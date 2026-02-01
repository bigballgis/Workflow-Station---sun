# Organization（业务单元）与 Virtual Group（虚拟组）关联关系说明

## 概述

Organization（业务单元/Business Unit）和 Virtual Group（虚拟组）之间**没有直接的数据库关联关系**，它们通过 **Role（角色）** 间接关联，形成灵活的权限管理机制。

## 核心关联机制

### 1. 通过 Role 间接关联

```
Virtual Group ──绑定──> Role ──类型决定──> Business Unit（可选）
```

- **Virtual Group** 可以绑定一个 **Role**（通过 `sys_virtual_group_roles` 表）
- **Role** 有两种类型：
  - **BU_BOUNDED**（业务单元绑定型）：需要配合业务单元使用
  - **BU_UNBOUNDED**（业务单元无关型）：独立生效，不需要业务单元

### 2. 数据库表结构

#### Virtual Group 相关表
- `sys_virtual_groups`：虚拟组表
- `sys_virtual_group_members`：虚拟组成员表（用户 ↔ 虚拟组）
- `sys_virtual_group_roles`：虚拟组角色绑定表（虚拟组 ↔ 角色）

#### Business Unit 相关表
- `sys_business_units`：业务单元表
- `sys_user_business_units`：用户业务单元关联表（用户 ↔ 业务单元）
- `sys_business_unit_roles`：业务单元角色绑定表（业务单元 ↔ 角色）

#### Role 相关表
- `sys_roles`：角色表
  - `type` 字段：`BU_BOUNDED` 或 `BU_UNBOUNDED`

## 用户权限获取流程

### 场景 1：BU_BOUNDED 角色（需要业务单元）

```
用户加入 Virtual Group 
  ↓
获得虚拟组绑定的 BU_BOUNDED 角色
  ↓
用户还需要加入对应的 Business Unit
  ↓
权限激活 ✅
```

**示例：**
- Virtual Group: "财务组"
- 绑定 Role: "财务专员"（BU_BOUNDED 类型）
- 用户加入 "财务组" → 获得 "财务专员" 角色
- 用户还需要加入 "财务部"（Business Unit）→ 权限才激活

### 场景 2：BU_UNBOUNDED 角色（不需要业务单元）

```
用户加入 Virtual Group 
  ↓
获得虚拟组绑定的 BU_UNBOUNDED 角色
  ↓
权限立即生效 ✅
```

**示例：**
- Virtual Group: "系统管理员组"
- 绑定 Role: "系统管理员"（BU_UNBOUNDED 类型）
- 用户加入 "系统管理员组" → 立即获得 "系统管理员" 权限

## 关键代码位置

### 1. 虚拟组角色绑定
- **实体类**：`backend/admin-center/src/main/java/com/admin/entity/VirtualGroupRole.java`
- **服务类**：`backend/admin-center/src/main/java/com/admin/service/VirtualGroupRoleService.java`
- **前端组件**：`frontend/admin-center/src/views/virtual-group/components/VirtualGroupRolesDialog.vue`

### 2. 角色类型定义
- **枚举类**：`backend/admin-center/src/main/java/com/admin/enums/RoleType.java`
  - `BU_BOUNDED`：业务单元绑定型
  - `BU_UNBOUNDED`：业务单元无关型

### 3. 权限激活逻辑
- **用户权限服务**：`backend/workflow-engine-core/src/main/java/com/workflow/service/UserPermissionService.java`
- **任务分配解析器**：`backend/workflow-engine-core/src/main/java/com/workflow/service/TaskAssigneeResolver.java`

## 前端提示信息

在虚拟组绑定角色时，前端会显示以下提示：

### BU_BOUNDED 角色提示
> **业务单元绑定型**：需要配合业务单元使用，用户加入虚拟组后还需申请加入业务单元才能激活权限

### BU_UNBOUNDED 角色提示
> **业务单元无关型**：独立生效，用户加入虚拟组后立即拥有该角色权限

## 实际应用场景

### 场景 A：部门角色（BU_BOUNDED）
- **虚拟组**："技术部开发组"
- **绑定角色**："开发工程师"（BU_BOUNDED）
- **业务单元**："技术部"
- **权限激活条件**：用户必须同时是 "技术部开发组" 的成员，并且属于 "技术部" 业务单元

### 场景 B：跨部门角色（BU_UNBOUNDED）
- **虚拟组**："项目A团队"
- **绑定角色**："项目成员"（BU_UNBOUNDED）
- **权限激活条件**：用户只需加入 "项目A团队" 虚拟组即可，不需要特定的业务单元

## 总结

1. **Virtual Group 和 Business Unit 没有直接关联**
2. **通过 Role 类型决定是否需要 Business Unit**
3. **BU_BOUNDED 角色**：需要 Virtual Group + Business Unit
4. **BU_UNBOUNDED 角色**：只需要 Virtual Group

这种设计允许：
- 灵活的权限管理（可以跨部门分配角色）
- 精确的权限控制（部门内角色需要业务单元验证）
- 简化的权限分配（跨部门角色无需业务单元）
