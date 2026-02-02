# 数据库初始化快速参考

## 🚀 快速开始

### Windows
```powershell
cd deploy/init-scripts
.\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform -DbUser postgres
```

### Linux/Mac
```bash
cd deploy/init-scripts
./init-database.sh
```

### Docker
```bash
# 角色和虚拟组
docker exec -i <container> psql -U <user> -d <db> < 01-admin/01-create-roles-and-groups.sql

# 测试用户
docker exec -i <container> psql -U <user> -d <db> < 01-admin/02-create-test-users.sql
```

## 📋 默认配置

### 5个系统角色
| 代码 | 名称 | 类型 |
|-----|------|------|
| SYS_ADMIN | 系统管理员 | ADMIN |
| AUDITOR | 审计员 | ADMIN |
| MANAGER | 部门经理 | BU_BOUNDED |
| DEVELOPER | 工作流开发者 | DEVELOPER |
| DESIGNER | 工作流设计师 | DEVELOPER |

### 5个虚拟组
- SYSTEM_ADMINISTRATORS → SYS_ADMIN
- AUDITORS → AUDITOR
- MANAGERS → MANAGER
- DEVELOPERS → DEVELOPER
- DESIGNERS → DESIGNER

### 5个测试用户（密码都是 `password`）
| 用户名 | 角色 | 访问权限 |
|-------|------|---------|
| admin | SYS_ADMIN | 完全系统访问 |
| auditor | AUDITOR | 审计和监控 |
| manager | MANAGER | 部门管理 |
| developer | DEVELOPER | 开发工作站 |
| designer | DESIGNER | 流程设计 |

## 🔍 验证命令

### 检查角色
```sql
SELECT code, name, type FROM sys_roles WHERE is_system = true ORDER BY code;
```

### 检查虚拟组
```sql
SELECT code, name FROM sys_virtual_groups ORDER BY code;
```

### 检查用户
```sql
SELECT username, display_name, email FROM sys_users WHERE deleted = false ORDER BY username;
```

### 检查用户-角色映射
```sql
SELECT u.username, vg.code as group_code, r.code as role_code
FROM sys_virtual_group_members vgm
JOIN sys_users u ON vgm.user_id = u.id
JOIN sys_virtual_groups vg ON vgm.group_id = vg.id
JOIN sys_virtual_group_roles vgr ON vgr.virtual_group_id = vg.id
JOIN sys_roles r ON vgr.role_id = r.id
ORDER BY u.username;
```

## 🌐 登录测试

### Admin Center (http://localhost:8081)
- admin / password
- auditor / password

### User Portal (http://localhost:8082)
- manager / password
- 所有用户都可以访问

### Developer Workstation (http://localhost:8083)
- developer / password
- designer / password

## 🔒 生产环境

### 修改密码
```sql
UPDATE sys_users 
SET password_hash = '$2a$10$YOUR_NEW_BCRYPT_HASH',
    must_change_password = true
WHERE username = 'admin';
```

### 删除测试用户
```sql
UPDATE sys_users 
SET deleted = true, status = 'INACTIVE'
WHERE username IN ('auditor', 'manager', 'developer', 'designer');
```

## 📚 详细文档
- [INITIALIZATION_GUIDE.md](INITIALIZATION_GUIDE.md) - 完整初始化指南
- [README.md](README.md) - 脚本说明文档
