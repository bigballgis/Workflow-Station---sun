# 数据库表名统一报告

## 已完成的统一工作

### 统一使用的表结构

所有服务现在统一使用以下表：

| 表名 | 用途 | ID类型 | 共享范围 |
|------|------|--------|----------|
| `sys_users` | 用户表 | varchar(64) | 全局共享 |
| `sys_user_roles` | 用户角色关联 | varchar(64) | 全局共享 |
| `sys_roles` | 角色定义 | varchar(64) | 全局共享 |
| `sys_departments` | 组织/部门 | varchar(64) | 全局共享 |
| `sys_permissions` | 权限定义 | varchar(64) | 全局共享 |
| `sys_role_permissions` | 角色权限关联 | varchar(64) | 全局共享 |
| `sys_login_audit` | 登录审计 | UUID | 全局共享 |
| `sys_role_assignments` | 角色分配 | varchar(64) | 全局共享 |
| `sys_virtual_groups` | 虚拟组 | varchar(64) | 全局共享 |
| `sys_virtual_group_members` | 虚拟组成员 | varchar(64) | 全局共享 |
| `sys_virtual_group_task_history` | 虚拟组任务历史 | varchar(64) | 全局共享 |

### 虚拟组说明

虚拟组是跨服务共享的，用户可以通过虚拟组获得角色权限，然后登录任何前端：
- Admin Center
- User Portal  
- Developer Workstation

虚拟组表从 `admin_*` 前缀改为 `sys_*` 前缀，因为它们是全局共享的。

### 已修改的实体类

1. `platform-security/model/User.java` - 改为 `sys_users`，ID改为String
2. `developer-workstation/entity/User.java` - 改为 `sys_users`，ID改为String
3. `user-portal/entity/User.java` - 改为 `sys_users`，ID改为String
4. `admin-center/entity/User.java` - 改为 `sys_users`
5. `admin-center/entity/VirtualGroup.java` - 改为 `sys_virtual_groups`
6. `admin-center/entity/VirtualGroupMember.java` - 改为 `sys_virtual_group_members`
7. `admin-center/entity/VirtualGroupTaskHistory.java` - 改为 `sys_virtual_group_task_history`
8. `platform-security/repository/UserRepository.java` - ID类型改为String
9. `platform-security/model/LoginAudit.java` - userId改为String
10. `platform-security/service/impl/LoginAuditService.java` - 参数类型改为String
11. `platform-security/dto/UserInfo.java` - 移除toString()调用
12. `platform-security/resolver/VirtualGroupTargetResolver.java` - SQL查询改为sys_virtual_groups
13. `platform-security/service/impl/UserRoleServiceImpl.java` - SQL查询改为sys_virtual_groups

### SQL文件整理

初始化脚本（按顺序执行）：
- `01-schema.sql` - 数据库表结构定义
- `02-init-data.sql` - 系统配置数据（角色、权限、字典等，不包含用户）

测试数据脚本（仅用于开发/测试环境）：
- `90-test-organization.sql` - 测试组织架构
- `91-test-users.sql` - 测试用户数据（包含admin用户）

清理脚本：
- `99-cleanup-old-tables.sql` - 删除旧表（谨慎使用）

**注意**: 生产环境（SIT/UAT/PROD）的用户数据应由运维人员单独管理，不使用测试脚本。

### 需要删除的旧表

运行 `99-cleanup-old-tables.sql` 可删除：
- `sys_user` (旧UUID表)
- `sys_user_role` (旧角色关联表)
- `users`, `user_roles`, `user_organizations`
- `organizations`, `roles`, `permissions`, `role_permissions`
- `admin_virtual_groups` (旧虚拟组表，已迁移到sys_virtual_groups)
- `admin_virtual_group_members` (旧虚拟组成员表)
- `admin_virtual_group_task_history` (旧虚拟组任务历史表)

## 测试账号

密码统一为: `test123`

### Admin Center
- admin (系统管理员)

### User Portal
- hr.manager (HR经理)
- corp.manager (公司银行经理)
- hr.specialist, corp.director, corp.analyst, corp.officer

### Developer Workstation
- tech.director (技术总监)
- core.lead, channel.lead, risk.lead (团队负责人)
- dev.john, dev.mary, dev.peter, dev.lisa, dev.alex, dev.emma (开发人员)
