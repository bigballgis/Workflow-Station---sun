# 测试数据和工作流状态

## 当前状态

### ✅ 已导入
1. **数据库表结构** - 所有表已创建
2. **系统角色** - 5个系统默认角色已创建
3. **虚拟组** - 5个虚拟组已创建
4. **测试用户** - 5个测试用户已创建并分配角色

### ❌ 未导入
1. **测试组织数据** - `02-test-data/` 目录
2. **采购工作流定义** - `04-purchase-workflow/` 目录

## 问题说明

在 `deploy/init-scripts/` 目录下，以下路径存在结构问题：

```
02-test-data/
├── 01-organization.sql/          # 这是一个目录，不是文件
├── 02-organization-detail.sql/   # 这是一个目录，不是文件
├── 03-users.sql/                 # 这是一个目录，不是文件
├── 04-department-managers.sql/   # 这是一个目录，不是文件
├── 04-role-assignments.sql/      # 这是一个目录，不是文件
└── 05-virtual-groups.sql/        # 这是一个目录，不是文件

04-purchase-workflow/
├── 04-01-function-unit.sql/      # 这是一个目录，不是文件
├── 04-02-tables.sql/             # 这是一个目录，不是文件
├── 04-03-fields-main-fixed.sql/  # 这是一个目录，不是文件
└── ... (其他都是空目录)
```

这些带 `.sql` 后缀的实际上是**空目录**，而不是 SQL 文件。

## 数据库验证

当前数据库中的数据：

### 用户数据
```sql
SELECT COUNT(*) FROM sys_users WHERE deleted = false;
-- 结果: 7 个用户 (包括 admin, auditor, manager, developer, designer, super_admin, testadmin)
```

### 组织数据
```sql
SELECT COUNT(*) FROM sys_business_units;
-- 结果: 0 (没有组织数据)
```

### 工作流数据
```sql
SELECT COUNT(*) FROM dw_function_units;
-- 结果: 0 (没有功能单元)

SELECT COUNT(*) FROM dw_process_definitions;
-- 结果: 0 (没有流程定义)
```

## 建议方案

### 方案一：使用现有的基础配置（推荐）

当前已有的配置足以启动和测试系统：
- ✅ 5个系统角色
- ✅ 5个虚拟组
- ✅ 5个测试用户
- ✅ 完整的数据库表结构

**优点**：
- 系统可以立即启动
- 可以通过 UI 手动创建组织和工作流
- 更灵活，适合开发和测试

**使用方法**：
1. 启动应用服务
2. 使用 `admin/password` 登录 Admin Center
3. 通过 UI 创建组织结构
4. 使用 `developer/password` 登录 Developer Workstation
5. 通过 UI 设计和部署工作流

### 方案二：创建测试数据 SQL 脚本

如果需要预置的测试数据和工作流，需要创建实际的 SQL 文件：

#### 1. 创建测试组织数据

创建文件：`deploy/init-scripts/02-test-data/01-create-test-organization.sql`

```sql
-- 创建测试组织
INSERT INTO sys_business_units (id, code, name, type, parent_id, level, path, status, created_at, updated_at)
VALUES 
('bu-root', 'ROOT', '总公司', 'COMPANY', NULL, 1, '/ROOT/', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-it', 'IT_DEPT', 'IT部门', 'DEPARTMENT', 'bu-root', 2, '/ROOT/IT_DEPT/', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-hr', 'HR_DEPT', '人力资源部', 'DEPARTMENT', 'bu-root', 2, '/ROOT/HR_DEPT/', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('bu-finance', 'FINANCE_DEPT', '财务部', 'DEPARTMENT', 'bu-root', 2, '/ROOT/FINANCE_DEPT/', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;
```

#### 2. 创建测试工作流

创建文件：`deploy/init-scripts/04-purchase-workflow/01-create-purchase-workflow.sql`

```sql
-- 创建采购功能单元
INSERT INTO dw_function_units (id, code, name, description, icon, status, created_at, updated_at)
VALUES 
('fu-purchase', 'PURCHASE', '采购管理', '采购申请和审批流程', 'shopping-cart', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- 创建采购流程定义
-- (需要根据实际的 BPMN 定义来创建)
```

### 方案三：清理空目录

删除这些空的 `.sql` 目录，保持目录结构清晰：

```powershell
# Windows PowerShell
Remove-Item "deploy/init-scripts/02-test-data/*" -Recurse -Force
Remove-Item "deploy/init-scripts/04-purchase-workflow/*" -Recurse -Force
```

```bash
# Linux/Mac
rm -rf deploy/init-scripts/02-test-data/*
rm -rf deploy/init-scripts/04-purchase-workflow/*
```

## 当前可用的初始化脚本

以下脚本已经可以正常使用：

### 自动化脚本
- `init-database.sh` - Linux/Mac 完整初始化
- `init-database.ps1` - Windows 完整初始化
- `00-init-all.sh` - Docker 容器初始化

### SQL 脚本
- `00-schema/00-init-all-schemas.sql` - 所有表结构
- `01-admin/01-create-roles-and-groups.sql` - 角色和虚拟组
- `01-admin/02-create-test-users.sql` - 测试用户

## 推荐的初始化流程

### 开发环境
```bash
# 1. 初始化数据库（已完成）
cd deploy/init-scripts
./init-database.sh  # 或 Windows: .\init-database.ps1

# 2. 启动应用服务
docker-compose up -d

# 3. 通过 UI 创建测试数据
# - 登录 Admin Center (admin/password)
# - 创建组织结构
# - 登录 Developer Workstation (developer/password)
# - 设计和部署工作流
```

### 生产环境
```bash
# 1. 只初始化表结构和系统角色
psql -f 00-schema/00-init-all-schemas.sql
psql -f 01-admin/01-create-roles-and-groups.sql

# 2. 不导入测试用户
# 3. 通过 UI 创建实际的用户和组织
```

## 总结

**当前状态**：
- ✅ 核心系统配置已完成（角色、虚拟组、测试用户）
- ❌ 测试组织数据和工作流未导入（目录结构有问题）

**建议**：
- 使用方案一：直接使用现有配置，通过 UI 创建数据（最简单）
- 或使用方案三：清理空目录，保持项目整洁

系统已经可以正常启动和使用，测试数据可以通过 UI 手动创建。
