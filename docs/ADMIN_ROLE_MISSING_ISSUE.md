# Admin 角色缺失问题说明

## 问题描述

在另一台电脑上检查 admin 用户的角色分配时，发现查询结果**缺少关键字段**。

### 期望结果
```
id:          ur-admin-001-SYS_ADMIN_ROLE
user_id:     admin-001
role_id:     SYS_ADMIN_ROLE          ← 缺失
role_code:   SYS_ADMIN
role_name:   System Administrator
role_type:   ADMIN                   ← 缺失
assigned_at: 2026-01-25 16:54:52.411613
```

### 实际结果
```
id:          ur-admin-001-SYS_ADMIN_ROLE
user_id:     admin-001
role_code:   SYS_ADMIN
role_name:   ADMIN                   ← 错误（应该是 System Administrator）
assigned_at: 2026-01-25 16:54:52.411613
```

## 🔴 影响

**是的，这会严重影响登录权限！**

### 为什么会影响权限？

1. **role_id 缺失** → LEFT JOIN 失败 → 角色关联断裂
2. **role_type 缺失** → 系统无法识别这是 ADMIN 类型角色
3. **权限加载失败** → 无法从 `sys_role_permissions` 加载权限
4. **结果**：用户登录后没有任何权限

### 权限验证流程

```
用户登录
  ↓
查询 sys_user_roles (获取 role_id)
  ↓
JOIN sys_roles (获取角色详情和 type)  ← 这里失败了！
  ↓
查询 sys_role_permissions (获取权限列表)  ← 无法执行
  ↓
权限检查失败 → 无权限访问
```

## 🔍 根本原因

查询使用了 LEFT JOIN：

```sql
SELECT 
    ur.user_id,
    ur.role_id,
    r.code as role_code,
    r.name as role_name,
    r.type as role_type
FROM sys_user_roles ur
LEFT JOIN sys_roles r ON ur.role_id = r.id  ← 这里 JOIN 失败
WHERE ur.user_id = 'admin-001';
```

**可能的原因**：

1. **sys_roles 表中缺少 SYS_ADMIN_ROLE 记录**
   - `sys_user_roles.role_id` 指向 'SYS_ADMIN_ROLE'
   - 但 `sys_roles` 表中没有 id='SYS_ADMIN_ROLE' 的记录
   - LEFT JOIN 返回 NULL

2. **sys_user_roles 中的 role_id 值错误**
   - role_id 指向了一个不存在的 ID
   - 或者 role_id 为 NULL

3. **数据库导入不完整**
   - 导出时没有包含 sys_roles 表的数据
   - 或导入时出错

## ✅ 解决方案

### 方案 1：执行完整修复脚本（推荐）

```bash
# 在另一台电脑上执行
docker exec -i platform-postgres psql -U platform -d workflow_platform < fix_admin_permissions_complete.sql
```

这个脚本会：
- ✅ 创建/更新 SYS_ADMIN_ROLE 角色
- ✅ 创建/更新所有 ADMIN 权限
- ✅ 分配权限给 SYS_ADMIN 角色
- ✅ 确保 admin 用户存在
- ✅ 正确分配角色给 admin 用户
- ✅ 创建虚拟组并添加成员
- ✅ 自动验证修复结果

### 方案 2：诊断后手动修复

```bash
# 1. 先诊断问题
docker exec -i platform-postgres psql -U platform -d workflow_platform < fix_admin_role_issue.sql

# 2. 根据诊断结果手动修复
```

### 方案 3：重新导入数据库

如果问题严重，可以从工作正常的电脑重新导出数据库：

```bash
# 在工作正常的电脑上
/opt/homebrew/opt/postgresql@16/bin/pg_dump \
    -h localhost -p 5432 \
    -U platform -d workflow_platform \
    --clean --if-exists \
    > workflow_platform_complete.sql

# 在另一台电脑上
docker exec -i platform-postgres psql -U platform -d workflow_platform < workflow_platform_complete.sql
```

## 📋 验证步骤

修复后，执行以下查询验证：

```sql
-- 1. 检查 sys_roles 表
SELECT id, code, name, type, status 
FROM sys_roles 
WHERE code = 'SYS_ADMIN';

-- 期望结果：
-- id: SYS_ADMIN_ROLE
-- code: SYS_ADMIN
-- name: System Administrator
-- type: ADMIN
-- status: ACTIVE

-- 2. 检查 sys_user_roles 表
SELECT user_id, role_id 
FROM sys_user_roles 
WHERE user_id = 'admin-001';

-- 期望结果：
-- user_id: admin-001
-- role_id: SYS_ADMIN_ROLE

-- 3. 检查完整的角色分配（带 JOIN）
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

-- 期望结果：所有字段都有值，没有 NULL

-- 4. 检查权限数量
SELECT COUNT(*) as permission_count
FROM sys_role_permissions
WHERE role_id = 'SYS_ADMIN_ROLE';

-- 期望结果：9 个权限
```

## 🚀 修复后的操作

1. **重启后端服务**
   ```bash
   # 停止 admin-center 服务
   # 启动 admin-center 服务
   mvn spring-boot:run -pl backend/admin-center -DskipTests
   ```

2. **清除浏览器缓存**
   - 清除 localStorage
   - 清除 sessionStorage
   - 或使用无痕模式

3. **重新登录**
   - 用户名：admin
   - 密码：admin123

4. **验证权限**
   - 能看到所有菜单
   - 能访问用户管理
   - 能访问角色管理
   - 能访问业务单元管理

## 📝 预防措施

### 1. 使用完整的数据库导出

```bash
# 导出时使用 --clean --if-exists 选项
pg_dump -h localhost -p 5432 \
    -U platform -d workflow_platform \
    --clean --if-exists \
    > workflow_platform_complete.sql
```

### 2. 验证导出文件

```bash
# 检查导出文件是否包含 sys_roles 表的数据
grep "INSERT INTO.*sys_roles" workflow_platform_complete.sql

# 检查是否包含 SYS_ADMIN_ROLE
grep "SYS_ADMIN_ROLE" workflow_platform_complete.sql
```

### 3. 导入后验证

```bash
# 导入后立即执行检查脚本
docker exec -i platform-postgres psql -U platform -d workflow_platform < check_admin_permissions.sql
```

## 🔗 相关文件

- **诊断脚本**：`fix_admin_role_issue.sql`
- **完整修复脚本**：`fix_admin_permissions_complete.sql`
- **检查脚本**：`check_admin_permissions.sql`
- **完整检查清单**：`docs/ADMIN_USER_DATABASE_CHECKLIST.md`

## 💡 技术细节

### LEFT JOIN 行为

```sql
-- 当 sys_roles 中没有匹配的记录时
SELECT ur.role_id, r.code, r.name, r.type
FROM sys_user_roles ur
LEFT JOIN sys_roles r ON ur.role_id = r.id
WHERE ur.user_id = 'admin-001';

-- 结果：
-- role_id: SYS_ADMIN_ROLE (来自 sys_user_roles)
-- code: NULL (来自 sys_roles，但 JOIN 失败)
-- name: NULL
-- type: NULL
```

### 为什么显示 "ADMIN" 而不是 NULL？

可能的原因：
1. 查询工具的显示问题
2. 应用层的默认值处理
3. 数据库中有另一个 code='SYS_ADMIN' 但 id 不同的记录

### 正确的数据关系

```
sys_users (admin-001)
    ↓ (user_id)
sys_user_roles (ur-admin-001-SYS_ADMIN_ROLE)
    ↓ (role_id = 'SYS_ADMIN_ROLE')
sys_roles (SYS_ADMIN_ROLE)
    ↓ (role_id)
sys_role_permissions (9 条记录)
    ↓ (permission_id)
sys_permissions (9 个 ADMIN:* 权限)
```

---

**文档生成时间**：2026-01-31  
**问题严重程度**：🔴 高（影响登录权限）  
**修复难度**：⭐ 简单（执行脚本即可）  
**预计修复时间**：< 5 分钟
