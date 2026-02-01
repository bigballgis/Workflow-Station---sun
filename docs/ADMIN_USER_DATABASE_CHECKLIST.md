# Admin 用户数据库检查清单

生成时间：2026-01-31

## 概述

本文档用于检查 admin (admin-001) 用户在数据库中的完整配置，方便在不同环境之间对比验证。

## 问题描述

- **当前环境**：admin 用户登录管理员中心 (localhost:3000) 正常，有完整权限
- **另一台电脑**：admin 用户登录后没有权限
- **目的**：对比两个环境的数据库配置，找出差异

## 检查步骤

### 1. 检查 admin 用户基本信息

**表名**：`sys_users`

```sql
SELECT 
    id, 
    username, 
    email, 
    full_name, 
    status,
    deleted,
    created_at
FROM sys_users 
WHERE id = 'admin-001';
```

**期望结果**：
```
id:         admin-001
username:   admin
email:      admin@example.com
full_name:  System Administrator
status:     ACTIVE
deleted:    false (f)
```

**检查点**：
- [ ] 用户存在
- [ ] status = 'ACTIVE'
- [ ] deleted = false

---

### 2. 检查 admin 的角色分配

**表名**：`sys_user_roles`

```sql
SELECT 
    ur.id,
    ur.user_id,
    ur.role_id,
    r.code as role_code,
    r.name as role_name,
    r.type as role_type,
    ur.assigned_at
FROM sys_user_roles ur
LEFT JOIN sys_roles r ON ur.role_id = r.id
WHERE ur.user_id = 'admin-001';
```

**期望结果**：
```
id:          ur-admin-001-SYS_ADMIN_ROLE
user_id:     admin-001
role_id:     SYS_ADMIN_ROLE
role_code:   SYS_ADMIN
role_name:   System Administrator
role_type:   ADMIN
assigned_at: 2026-01-25 16:54:52.411613
```

**检查点**：
- [ ] 至少有一条记录
- [ ] role_id = 'SYS_ADMIN_ROLE'
- [ ] role_code = 'SYS_ADMIN'

---

### 3. 检查 SYS_ADMIN 角色详情

**表名**：`sys_roles`

```sql
SELECT * FROM sys_roles WHERE code = 'SYS_ADMIN';
```

**期望结果**：
```
id:          SYS_ADMIN_ROLE
code:        SYS_ADMIN
name:        System Administrator
type:        ADMIN
description: Full system access
status:      ACTIVE
is_system:   true (t)
```

**检查点**：
- [ ] 角色存在
- [ ] code = 'SYS_ADMIN'
- [ ] type = 'ADMIN'
- [ ] status = 'ACTIVE'
- [ ] is_system = true

---

### 4. 检查 SYS_ADMIN 角色的权限

**表名**：`sys_role_permissions` + `sys_permissions`

```sql
SELECT 
    rp.id,
    rp.role_id,
    rp.permission_id,
    p.code as permission_code,
    p.name as permission_name,
    p.resource,
    p.action
FROM sys_role_permissions rp
LEFT JOIN sys_permissions p ON rp.permission_id = p.id
WHERE rp.role_id = 'SYS_ADMIN_ROLE'
ORDER BY p.code;
```

**期望结果（9 条权限）**：

| permission_id | permission_code | permission_name | resource | action |
|---------------|-----------------|-----------------|----------|--------|
| perm-admin-audit-read | ADMIN:AUDIT:READ | View Audit Logs | audit | read |
| perm-admin-bu-read | ADMIN:BU:READ | View Business Units | business_unit | read |
| perm-admin-bu-write | ADMIN:BU:WRITE | Manage Business Units | business_unit | write |
| perm-admin-config-read | ADMIN:CONFIG:READ | View System Config | config | read |
| perm-admin-config-write | ADMIN:CONFIG:WRITE | Manage System Config | config | write |
| perm-admin-role-read | ADMIN:ROLE:READ | View Roles | role | read |
| perm-admin-role-write | ADMIN:ROLE:WRITE | Manage Roles | role | write |
| perm-admin-user-read | ADMIN:USER:READ | View Users | user | read |
| perm-admin-user-write | ADMIN:USER:WRITE | Manage Users | user | write |

**检查点**：
- [ ] 有 9 条权限记录
- [ ] 包含所有 ADMIN:* 权限
- [ ] 包含 read 和 write 权限

---

### 5. 检查所有权限是否存在

**表名**：`sys_permissions`

```sql
SELECT 
    id,
    code,
    name,
    type,
    resource,
    action
FROM sys_permissions
WHERE type = 'ADMIN'
ORDER BY code;
```

**期望结果（9 条 ADMIN 类型权限）**：

| id | code | name | type | resource | action |
|----|------|------|------|----------|--------|
| perm-admin-audit-read | ADMIN:AUDIT:READ | View Audit Logs | ADMIN | audit | read |
| perm-admin-bu-read | ADMIN:BU:READ | View Business Units | ADMIN | business_unit | read |
| perm-admin-bu-write | ADMIN:BU:WRITE | Manage Business Units | ADMIN | business_unit | write |
| perm-admin-config-read | ADMIN:CONFIG:READ | View System Config | ADMIN | config | read |
| perm-admin-config-write | ADMIN:CONFIG:WRITE | Manage System Config | ADMIN | config | write |
| perm-admin-role-read | ADMIN:ROLE:READ | View Roles | ADMIN | role | read |
| perm-admin-role-write | ADMIN:ROLE:WRITE | Manage Roles | ADMIN | role | write |
| perm-admin-user-read | ADMIN:USER:READ | View Users | ADMIN | user | read |
| perm-admin-user-write | ADMIN:USER:WRITE | Manage Users | ADMIN | user | write |

**检查点**：
- [ ] 所有 9 个 ADMIN 权限都存在
- [ ] 权限 ID 与 sys_role_permissions 中的 permission_id 匹配

---

### 6. 检查 admin 所属的虚拟组

**表名**：`sys_virtual_group_members`

```sql
SELECT 
    vgm.id,
    vgm.group_id,
    vgm.user_id,
    vg.code as group_code,
    vg.name as group_name,
    vg.type as group_type,
    vgm.joined_at
FROM sys_virtual_group_members vgm
LEFT JOIN sys_virtual_groups vg ON vgm.group_id = vg.id
WHERE vgm.user_id = 'admin-001';
```

**期望结果**：
```
id:         vgm-admin-sysadmins
group_id:   vg-sys-admins
user_id:    admin-001
group_code: SYS_ADMINS
group_name: System Administrators
group_type: SYSTEM
joined_at:  2026-01-25 16:54:52.408737
```

**检查点**：
- [ ] admin 是 System Administrators 虚拟组的成员
- [ ] group_id = 'vg-sys-admins'
- [ ] group_code = 'SYS_ADMINS'

---

### 7. 检查 System Administrators 虚拟组详情

**表名**：`sys_virtual_groups`

```sql
SELECT * FROM sys_virtual_groups WHERE code = 'SYS_ADMINS';
```

**期望结果**：
```
id:          vg-sys-admins
code:        SYS_ADMINS
name:        System Administrators
type:        SYSTEM
description: System administrators with full access
status:      ACTIVE
```

**检查点**：
- [ ] 虚拟组存在
- [ ] code = 'SYS_ADMINS'
- [ ] type = 'SYSTEM'
- [ ] status = 'ACTIVE'

---

### 8. 检查虚拟组的角色绑定

**表名**：`sys_virtual_group_roles`

```sql
SELECT 
    vgr.id,
    vgr.virtual_group_id,
    vgr.role_id,
    vg.code as group_code,
    r.code as role_code,
    r.name as role_name
FROM sys_virtual_group_roles vgr
LEFT JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id
LEFT JOIN sys_roles r ON vgr.role_id = r.id
WHERE vgr.virtual_group_id = 'vg-sys-admins';
```

**期望结果**：
```
id:                 vgr-sysadmins-sysadmin
virtual_group_id:   vg-sys-admins
role_id:            SYS_ADMIN_ROLE
group_code:         SYS_ADMINS
role_code:          SYS_ADMIN
role_name:          System Administrator
```

**检查点**：
- [ ] System Administrators 虚拟组绑定了 SYS_ADMIN 角色
- [ ] role_id = 'SYS_ADMIN_ROLE'

---

### 9. 检查 admin 的业务单元关联

**表名**：`sys_user_business_units`

```sql
SELECT 
    ubu.id,
    ubu.user_id,
    ubu.business_unit_id,
    bu.code as bu_code,
    bu.name as bu_name,
    ubu.created_at
FROM sys_user_business_units ubu
LEFT JOIN sys_business_units bu ON ubu.business_unit_id = bu.id
WHERE ubu.user_id = 'admin-001';
```

**期望结果（2 条记录）**：
```
1. id: ubu-admin-hq, business_unit_id: DEPT-HQ, bu_code: HQ, bu_name: Head Office
2. id: ubu-admin-it, business_unit_id: DEPT-IT, bu_code: IT, bu_name: Information Technology
```

**检查点**：
- [ ] admin 关联了至少一个业务单元
- [ ] 业务单元存在于 sys_business_units 表中

---

### 10. 检查 admin 的业务单元角色

**表名**：`sys_user_business_unit_roles`

```sql
SELECT 
    ubur.*,
    bu.code as bu_code,
    bu.name as bu_name,
    r.code as role_code,
    r.name as role_name
FROM sys_user_business_unit_roles ubur
LEFT JOIN sys_business_units bu ON ubur.business_unit_id = bu.id
LEFT JOIN sys_roles r ON ubur.role_id = r.id
WHERE ubur.user_id = 'admin-001';
```

**期望结果**：
```
(0 rows) - admin 用户不需要业务单元角色，因为已经有系统管理员角色
```

**检查点**：
- [ ] 可以为空（admin 通过 SYS_ADMIN 角色获得权限）

---

## 快速检查脚本

将以下脚本保存为 `check_admin_permissions.sql`，在另一台电脑上执行：

```sql
-- ========================================
-- Admin 用户权限完整检查脚本
-- ========================================

\echo '1. 检查 admin 用户是否存在'
SELECT id, username, status, deleted FROM sys_users WHERE id = 'admin-001';

\echo ''
\echo '2. 检查 admin 的角色分配'
SELECT ur.user_id, ur.role_id, r.code, r.name 
FROM sys_user_roles ur 
LEFT JOIN sys_roles r ON ur.role_id = r.id 
WHERE ur.user_id = 'admin-001';

\echo ''
\echo '3. 检查 SYS_ADMIN 角色是否存在'
SELECT id, code, name, type, status, is_system 
FROM sys_roles 
WHERE code = 'SYS_ADMIN';

\echo ''
\echo '4. 检查 SYS_ADMIN 角色的权限数量'
SELECT COUNT(*) as permission_count 
FROM sys_role_permissions 
WHERE role_id = 'SYS_ADMIN_ROLE';

\echo ''
\echo '5. 检查 SYS_ADMIN 角色的具体权限'
SELECT p.code, p.name, p.resource, p.action 
FROM sys_role_permissions rp 
LEFT JOIN sys_permissions p ON rp.permission_id = p.id 
WHERE rp.role_id = 'SYS_ADMIN_ROLE' 
ORDER BY p.code;

\echo ''
\echo '6. 检查 admin 是否在 System Administrators 虚拟组'
SELECT vgm.user_id, vg.code, vg.name 
FROM sys_virtual_group_members vgm 
LEFT JOIN sys_virtual_groups vg ON vgm.group_id = vg.id 
WHERE vgm.user_id = 'admin-001';

\echo ''
\echo '7. 检查 System Administrators 虚拟组的角色绑定'
SELECT vgr.virtual_group_id, r.code, r.name 
FROM sys_virtual_group_roles vgr 
LEFT JOIN sys_roles r ON vgr.role_id = r.id 
WHERE vgr.virtual_group_id = 'vg-sys-admins';

\echo ''
\echo '========================================='
\echo '检查完成！'
\echo '========================================='
```

**执行方式**：
```bash
# macOS/Linux
docker exec -i platform-postgres psql -U platform -d workflow_platform < check_admin_permissions.sql

# 或直接执行
docker exec -i platform-postgres psql -U platform -d workflow_platform -f check_admin_permissions.sql
```

---

## 常见问题排查

### 问题 0：查询结果缺少 role_id 和 role_type 字段 ⚠️ **最常见**

**症状**：
```
期望结果包含：role_id, role_code, role_name, role_type
实际结果只有：role_code, role_name
```

**原因**：
1. `sys_roles` 表中缺少 `SYS_ADMIN_ROLE` 记录
2. `sys_user_roles` 表中的 `role_id` 指向了不存在的角色
3. LEFT JOIN 失败，导致角色相关字段为 NULL

**诊断步骤**：
```bash
# 在另一台电脑上执行诊断脚本
docker exec -i platform-postgres psql -U platform -d workflow_platform < fix_admin_role_issue.sql
```

**解决方案**：
```bash
# 执行完整修复脚本（推荐）
docker exec -i platform-postgres psql -U platform -d workflow_platform < fix_admin_permissions_complete.sql
```

**手动修复**：
```sql
-- 1. 检查 sys_user_roles 中的 role_id
SELECT role_id FROM sys_user_roles WHERE user_id = 'admin-001';

-- 2. 检查该 role_id 是否存在于 sys_roles
SELECT * FROM sys_roles WHERE id = '上一步查询到的role_id';

-- 3. 如果角色不存在，创建 SYS_ADMIN_ROLE
INSERT INTO sys_roles (id, code, name, type, description, status, is_system)
VALUES (
    'SYS_ADMIN_ROLE',
    'SYS_ADMIN',
    'System Administrator',
    'ADMIN',
    'Full system access',
    'ACTIVE',
    true
);

-- 4. 更新 sys_user_roles 中的 role_id
UPDATE sys_user_roles
SET role_id = 'SYS_ADMIN_ROLE'
WHERE user_id = 'admin-001';
```

**验证修复**：
```sql
-- 应该能看到完整的字段
SELECT 
    ur.id,
    ur.user_id,
    ur.role_id,
    r.code as role_code,
    r.name as role_name,
    r.type as role_type
FROM sys_user_roles ur
LEFT JOIN sys_roles r ON ur.role_id = r.id
WHERE ur.user_id = 'admin-001';
```

---

### 问题 1：admin 用户不存在

**症状**：查询 sys_users 返回 0 行

**解决**：
```sql
-- 插入 admin 用户
INSERT INTO sys_users (id, username, password_hash, email, full_name, status, language, deleted)
VALUES (
    'admin-001',
    'admin',
    '$2a$10$bTB3yyVtzpJw17uMI9pShOzAkm07MKZa2EyQhc4izBO1MdXXEiUiO',  -- 密码: admin123
    'admin@example.com',
    'System Administrator',
    'ACTIVE',
    'zh_CN',
    false
);
```

---

### 问题 2：admin 没有角色分配

**症状**：查询 sys_user_roles 返回 0 行

**解决**：
```sql
-- 分配 SYS_ADMIN 角色给 admin
INSERT INTO sys_user_roles (id, user_id, role_id, assigned_at, assigned_by)
VALUES (
    'ur-admin-001-SYS_ADMIN_ROLE',
    'admin-001',
    'SYS_ADMIN_ROLE',
    CURRENT_TIMESTAMP,
    'system'
);
```

---

### 问题 3：SYS_ADMIN 角色不存在

**症状**：查询 sys_roles 返回 0 行

**解决**：
```sql
-- 创建 SYS_ADMIN 角色
INSERT INTO sys_roles (id, code, name, type, description, status, is_system)
VALUES (
    'SYS_ADMIN_ROLE',
    'SYS_ADMIN',
    'System Administrator',
    'ADMIN',
    'Full system access',
    'ACTIVE',
    true
);
```

---

### 问题 4：SYS_ADMIN 角色没有权限

**症状**：查询 sys_role_permissions 返回 0 行或少于 9 行

**解决**：执行完整的权限初始化脚本（见下方）

---

### 问题 5：admin 不在 System Administrators 虚拟组

**症状**：查询 sys_virtual_group_members 返回 0 行

**解决**：
```sql
-- 将 admin 加入 System Administrators 虚拟组
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES (
    'vgm-admin-sysadmins',
    'vg-sys-admins',
    'admin-001',
    CURRENT_TIMESTAMP,
    'system'
);
```

---

## 完整权限初始化脚本

如果另一台电脑的数据库缺少权限数据，执行以下脚本：

```sql
-- ========================================
-- 1. 创建 ADMIN 类型权限
-- ========================================

INSERT INTO sys_permissions (id, code, name, type, resource, action, description) VALUES
('perm-admin-user-read', 'ADMIN:USER:READ', 'View Users', 'ADMIN', 'user', 'read', 'View user list and details'),
('perm-admin-user-write', 'ADMIN:USER:WRITE', 'Manage Users', 'ADMIN', 'user', 'write', 'Create, update, delete users'),
('perm-admin-role-read', 'ADMIN:ROLE:READ', 'View Roles', 'ADMIN', 'role', 'read', 'View role list and details'),
('perm-admin-role-write', 'ADMIN:ROLE:WRITE', 'Manage Roles', 'ADMIN', 'role', 'write', 'Create, update, delete roles'),
('perm-admin-bu-read', 'ADMIN:BU:READ', 'View Business Units', 'ADMIN', 'business_unit', 'read', 'View business unit list'),
('perm-admin-bu-write', 'ADMIN:BU:WRITE', 'Manage Business Units', 'ADMIN', 'business_unit', 'write', 'Create, update, delete business units'),
('perm-admin-config-read', 'ADMIN:CONFIG:READ', 'View System Config', 'ADMIN', 'config', 'read', 'View system configuration'),
('perm-admin-config-write', 'ADMIN:CONFIG:WRITE', 'Manage System Config', 'ADMIN', 'config', 'write', 'Update system configuration'),
('perm-admin-audit-read', 'ADMIN:AUDIT:READ', 'View Audit Logs', 'ADMIN', 'audit', 'read', 'View audit logs')
ON CONFLICT (id) DO NOTHING;

-- ========================================
-- 2. 将权限分配给 SYS_ADMIN 角色
-- ========================================

INSERT INTO sys_role_permissions (id, role_id, permission_id, created_at) VALUES
('rp-sysadmin-perm-admin-user-read', 'SYS_ADMIN_ROLE', 'perm-admin-user-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-user-write', 'SYS_ADMIN_ROLE', 'perm-admin-user-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-role-read', 'SYS_ADMIN_ROLE', 'perm-admin-role-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-role-write', 'SYS_ADMIN_ROLE', 'perm-admin-role-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-bu-read', 'SYS_ADMIN_ROLE', 'perm-admin-bu-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-bu-write', 'SYS_ADMIN_ROLE', 'perm-admin-bu-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-config-read', 'SYS_ADMIN_ROLE', 'perm-admin-config-read', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-config-write', 'SYS_ADMIN_ROLE', 'perm-admin-config-write', CURRENT_TIMESTAMP),
('rp-sysadmin-perm-admin-audit-read', 'SYS_ADMIN_ROLE', 'perm-admin-audit-read', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
```

---

## 涉及的数据库表总结

| 表名 | 用途 | admin 相关记录数 |
|------|------|-----------------|
| `sys_users` | 用户基本信息 | 1 条 |
| `sys_user_roles` | 用户角色分配 | 1 条 (SYS_ADMIN) |
| `sys_roles` | 角色定义 | 1 条 (SYS_ADMIN_ROLE) |
| `sys_role_permissions` | 角色权限关联 | 9 条 (ADMIN:*) |
| `sys_permissions` | 权限定义 | 9 条 (ADMIN 类型) |
| `sys_virtual_group_members` | 虚拟组成员 | 1 条 (System Administrators) |
| `sys_virtual_groups` | 虚拟组定义 | 1 条 (SYS_ADMINS) |
| `sys_virtual_group_roles` | 虚拟组角色绑定 | 1 条 |
| `sys_user_business_units` | 用户业务单元关联 | 2 条 (HQ, IT) |
| `sys_user_business_unit_roles` | 用户业务单元角色 | 0 条 |

---

## 权限验证流程

管理员中心的权限验证流程：

1. **用户登录** → 验证 `sys_users` 表中的用户名和密码
2. **加载角色** → 从 `sys_user_roles` 获取用户的角色
3. **加载权限** → 从 `sys_role_permissions` 获取角色的权限
4. **权限检查** → 前端/后端检查用户是否有 `ADMIN:*` 权限

**关键权限**：
- `ADMIN:USER:READ` - 查看用户列表
- `ADMIN:USER:WRITE` - 管理用户
- `ADMIN:ROLE:READ` - 查看角色
- `ADMIN:ROLE:WRITE` - 管理角色
- `ADMIN:BU:READ` - 查看业务单元
- `ADMIN:BU:WRITE` - 管理业务单元
- `ADMIN:CONFIG:READ` - 查看系统配置
- `ADMIN:CONFIG:WRITE` - 管理系统配置
- `ADMIN:AUDIT:READ` - 查看审计日志

---

## 联系与支持

如果在另一台电脑上检查后仍有问题，请提供：
1. 上述 10 个检查步骤的查询结果
2. 前端控制台的错误信息
3. 后端日志中的权限相关错误

---

**文档生成时间**：2026-01-31  
**适用环境**：管理员中心 (Admin Center)  
**数据库**：workflow_platform  
**用户**：admin (admin-001)
