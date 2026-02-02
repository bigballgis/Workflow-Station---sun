# 数据库初始化指南

## 概述

本指南说明如何初始化 Workflow Platform 数据库，包括创建表结构、系统角色、虚拟组和测试用户。

## 初始化内容

### 1. 数据库表结构
- **Platform Security** (sys_*): 用户、角色、权限、组织架构
- **Workflow Engine** (wf_*): 工作流任务、流程变量、历史记录
- **User Portal** (up_*): 用户偏好、委托规则、通知设置
- **Developer Workstation** (dw_*): 功能单元、流程定义、表单定义
- **Admin Center** (admin_*): 密码历史、权限委托、系统配置

### 2. 系统默认角色（5个）

| 角色代码 | 角色名称 | 类型 | 说明 |
|---------|---------|------|------|
| SYS_ADMIN | 系统管理员 | ADMIN | 完全系统访问权限 |
| AUDITOR | 审计员 | ADMIN | 审计日志和监控访问权限 |
| MANAGER | 部门经理 | BU_BOUNDED | 部门工作流和审批权限 |
| DEVELOPER | 工作流开发者 | DEVELOPER | 开发工作站访问权限 |
| DESIGNER | 工作流设计师 | DEVELOPER | 流程和表单设计权限 |

### 3. 虚拟组（5个）

| 组代码 | 组名称 | 绑定角色 |
|-------|--------|---------|
| SYSTEM_ADMINISTRATORS | 系统管理员组 | SYS_ADMIN |
| AUDITORS | 审计员组 | AUDITOR |
| MANAGERS | 部门经理组 | MANAGER |
| DEVELOPERS | 工作流开发者组 | DEVELOPER |
| DESIGNERS | 工作流设计师组 | DESIGNER |

### 4. 测试用户（5个）

| 用户名 | 密码 | 角色 | 说明 |
|-------|------|------|------|
| admin | password | SYS_ADMIN | 系统管理员 |
| auditor | password | AUDITOR | 审计员 |
| manager | password | MANAGER | 部门经理 |
| developer | password | DEVELOPER | 工作流开发者 |
| designer | password | DESIGNER | 工作流设计师 |

## 初始化方法

### 方法一：使用自动化脚本（推荐）

#### Windows PowerShell

```powershell
# 进入脚本目录
cd deploy/init-scripts

# 运行初始化脚本
.\init-database.ps1 `
    -DbHost localhost `
    -DbPort 5432 `
    -DbName workflow_platform `
    -DbUser postgres `
    -DbPassword yourpassword
```

#### Linux/Mac Bash

```bash
# 进入脚本目录
cd deploy/init-scripts

# 设置环境变量
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=workflow_platform
export DB_USER=postgres
export DB_PASSWORD=yourpassword

# 运行初始化脚本
./init-database.sh
```

### 方法二：手动执行 SQL 脚本

如果你更喜欢手动控制每一步：

```bash
# 1. 创建所有数据库表结构
psql -h localhost -p 5432 -U postgres -d workflow_platform \
    -f deploy/init-scripts/00-schema/00-init-all-schemas.sql

# 2. 创建系统角色和虚拟组
psql -h localhost -p 5432 -U postgres -d workflow_platform \
    -f deploy/init-scripts/01-admin/01-create-roles-and-groups.sql

# 3. 创建测试用户
psql -h localhost -p 5432 -U postgres -d workflow_platform \
    -f deploy/init-scripts/01-admin/02-create-test-users.sql
```

### 方法三：使用 Docker Compose

如果使用 Docker Compose，数据库会在容器启动时自动初始化：

```bash
# 启动 PostgreSQL 容器
docker-compose up -d postgres

# 等待数据库就绪
docker-compose logs -f postgres
```

## 验证初始化

### 1. 检查表数量

```sql
SELECT 
    CASE 
        WHEN table_name LIKE 'sys_%' THEN 'Platform Security'
        WHEN table_name LIKE 'wf_%' THEN 'Workflow Engine'
        WHEN table_name LIKE 'up_%' THEN 'User Portal'
        WHEN table_name LIKE 'dw_%' THEN 'Developer Workstation'
        WHEN table_name LIKE 'admin_%' THEN 'Admin Center'
        ELSE 'Other'
    END AS schema_group,
    COUNT(*) as table_count
FROM information_schema.tables 
WHERE table_schema = 'public' 
    AND table_type = 'BASE TABLE'
GROUP BY schema_group
ORDER BY schema_group;
```

预期结果：
- Platform Security: ~30 tables
- Workflow Engine: ~4 tables
- User Portal: ~10 tables
- Developer Workstation: ~11 tables
- Admin Center: ~14 tables

### 2. 检查角色和虚拟组

```sql
-- 检查角色
SELECT code, name, type, description 
FROM sys_roles 
WHERE is_system = true
ORDER BY code;

-- 检查虚拟组
SELECT code, name, description 
FROM sys_virtual_groups
ORDER BY code;

-- 检查角色绑定
SELECT 
    vg.code as group_code,
    vg.name as group_name,
    r.code as role_code,
    r.name as role_name
FROM sys_virtual_group_roles vgr
JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id
JOIN sys_roles r ON vgr.role_id = r.id
ORDER BY vg.code;
```

### 3. 检查测试用户

```sql
-- 检查用户
SELECT username, display_name, email, status 
FROM sys_users
WHERE deleted = false
ORDER BY username;

-- 检查用户组成员关系
SELECT 
    u.username,
    u.display_name,
    vg.code as group_code,
    vg.name as group_name
FROM sys_virtual_group_members vgm
JOIN sys_users u ON vgm.user_id = u.id
JOIN sys_virtual_groups vg ON vgm.group_id = vg.id
ORDER BY u.username;
```

## 测试登录

初始化完成后，可以使用以下凭据登录系统：

### Admin Center (http://localhost:8081)
- **admin** / password - 完全系统访问权限
- **auditor** / password - 审计和监控权限

### User Portal (http://localhost:8082)
- **manager** / password - 部门管理权限
- 所有用户都可以登录用户门户

### Developer Workstation (http://localhost:8083)
- **developer** / password - 工作流开发权限
- **designer** / password - 流程设计权限

## 重新初始化

如果需要重新初始化数据库（**警告：会删除所有数据**）：

```sql
-- 删除所有表
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
GRANT ALL ON SCHEMA public TO postgres;
GRANT ALL ON SCHEMA public TO public;
```

然后重新运行初始化脚本。

## 生产环境注意事项

### 安全建议

1. **修改默认密码**：生产环境必须修改所有测试用户的密码
2. **删除测试用户**：生产环境应删除不需要的测试用户
3. **限制权限**：根据实际需求调整角色权限
4. **启用审计**：确保审计日志功能已启用

### 修改密码

```sql
-- 修改用户密码（需要使用 BCrypt 加密）
UPDATE sys_users 
SET password_hash = '$2a$10$YOUR_NEW_BCRYPT_HASH',
    must_change_password = true,
    updated_at = CURRENT_TIMESTAMP
WHERE username = 'admin';
```

### 删除测试用户

```sql
-- 软删除测试用户
UPDATE sys_users 
SET deleted = true, 
    status = 'INACTIVE',
    updated_at = CURRENT_TIMESTAMP
WHERE username IN ('auditor', 'manager', 'developer', 'designer');
```

## 故障排除

### 常见问题

1. **外键错误**
   - 确保按正确顺序执行脚本
   - 先执行 schema 脚本，再执行 admin 脚本

2. **权限错误**
   - 确保数据库用户有 CREATE 权限
   - 检查 PostgreSQL 用户权限设置

3. **表已存在**
   - 脚本使用 `ON CONFLICT` 子句，可以安全重复执行
   - 如需完全重建，先删除 schema

4. **psql 命令未找到**
   - Windows: 安装 PostgreSQL 客户端工具
   - Linux: `sudo apt-get install postgresql-client`
   - Mac: `brew install postgresql`

## 支持

如有问题，请查看：
- [README.md](README.md) - 详细的脚本说明
- [../k8s/README.md](../k8s/README.md) - K8s 部署说明
- [../../docs/](../../docs/) - 项目文档
