# 数据库 SQL 文件对比报告

## 文件概览

| 文件 | 大小 | 格式 | 创建时间 | 用途 |
|------|------|------|----------|------|
| `workflow_platform_v1.sql` | 3.2 MB | COPY FROM stdin | 2026-01-31 | pg_dump 完整导出 |
| `workflow_platform_executable_clean_fixed.sql` | 789 KB | INSERT | 2026-01-26 | 清理后的可执行版本 |

## 主要区别

### 1. 数据格式

**workflow_platform_v1.sql:**
- 使用 `COPY FROM stdin` 格式（PostgreSQL 原生格式）
- 需要 `psql` 命令行工具执行
- 不能在 DBeaver 等 GUI 工具中直接执行

**workflow_platform_executable_clean_fixed.sql:**
- 使用 `INSERT` 语句格式
- 可以在任何 SQL 客户端执行（包括 DBeaver）
- 已经过清理和优化

### 2. 表数量

| 文件 | CREATE TABLE 数量 |
|------|-------------------|
| workflow_platform_v1.sql | 140 表 |
| workflow_platform_executable_clean_fixed.sql | 141 表 |

### 3. 数据内容

#### Flowable 工作流引擎表（act_* 表）

| 文件 | 包含数据的表数量 | 说明 |
|------|-----------------|------|
| workflow_platform_v1.sql | 62 个 Flowable 表有数据 | **包含运行时数据**（流程实例、任务等） |
| workflow_platform_executable_clean_fixed.sql | 346 个 INSERT 语句 | **只包含基础配置数据**（无运行时数据） |

**关键差异：**
- `workflow_platform_v1.sql` 包含：
  - ✅ 流程定义（Process Definitions）
  - ✅ 流程实例（Process Instances）
  - ✅ 任务实例（Task Instances）
  - ✅ 历史数据（History）
  - ✅ 变量数据（Variables）
  
- `workflow_platform_executable_clean_fixed.sql` 包含：
  - ✅ Flowable 表结构
  - ✅ 数据库变更日志（Liquibase/Flyway）
  - ❌ **不包含**流程实例和任务数据

#### 应用表（sys_*, dw_*, admin_*, up_*, wf_* 表）

| 文件 | 包含数据的表数量 | 说明 |
|------|-----------------|------|
| workflow_platform_v1.sql | 69 个应用表有数据 | 完整的应用数据 |
| workflow_platform_executable_clean_fixed.sql | 361 个 INSERT 语句 | 清理后的应用数据 |

**关键差异：**
- `workflow_platform_v1.sql` 包含：
  - ✅ 所有用户数据
  - ✅ 所有角色和权限
  - ✅ 所有功能单元配置
  - ✅ 所有表单和动作定义
  - ✅ 所有业务数据
  
- `workflow_platform_executable_clean_fixed.sql` 包含：
  - ✅ 基础用户数据（admin, 测试用户）
  - ✅ 系统角色和权限
  - ✅ 示例功能单元（采购申请）
  - ✅ 清理后的配置数据
  - ❌ **不包含**所有业务数据

### 4. 文件结构

**workflow_platform_v1.sql:**
```sql
-- PostgreSQL dump header
SET statements...
CREATE EXTENSION...
CREATE TABLE...
COPY table_name FROM stdin;
data_row_1
data_row_2
\.
-- More tables...
ALTER TABLE... ADD CONSTRAINT...
CREATE INDEX...
```

**workflow_platform_executable_clean_fixed.sql:**
```sql
-- 清理脚本 header
-- 执行顺序说明
SET session_replication_role = 'replica';
CREATE EXTENSION...
DROP TABLE IF EXISTS... CASCADE;
CREATE TABLE...
INSERT INTO table_name VALUES (...), (...);
ALTER TABLE... ADD CONSTRAINT...
CREATE INDEX...
ALTER SEQUENCE... OWNED BY...
```

### 5. 特殊处理

**workflow_platform_executable_clean_fixed.sql 的优化：**

1. ✅ **删除重复索引**
   - 移除 `idx_user_username`, `idx_user_email`, `idx_user_status`
   
2. ✅ **修复约束**
   - `sys_users` 表 CHECK 约束更新为 4 值（ACTIVE, DISABLED, LOCKED, PENDING）
   
3. ✅ **移除废弃列**
   - 移除 `sys_users.department_id` 列
   
4. ✅ **添加缺失索引**
   - `idx_sys_users_entity_manager`
   - `idx_sys_users_function_manager`
   
5. ✅ **添加唯一约束**
   - `dw_form_table_bindings` 添加 `uk_form_table_binding`
   
6. ✅ **修复序列关联**
   - 所有序列添加 `OWNED BY` 语句

7. ✅ **同步用户角色**
   - 确保 `sys_user_roles` 表与 `sys_role_assignments` 同步

## 使用场景

### workflow_platform_v1.sql

**适用场景：**
- ✅ 完整数据库备份和恢复
- ✅ 迁移到新环境（包含所有数据）
- ✅ 数据分析和审计
- ✅ 保留所有历史记录

**执行方式：**
```bash
# 必须使用 psql 命令行
psql -h host -U user -d database -f workflow_platform_v1.sql

# 或使用 Docker
docker exec -i postgres-container psql -U user -d database < workflow_platform_v1.sql
```

**不适用：**
- ❌ DBeaver 等 GUI 工具（COPY FROM stdin 不支持）
- ❌ 初始化新环境（包含太多运行时数据）

### workflow_platform_executable_clean_fixed.sql

**适用场景：**
- ✅ 初始化新的开发环境
- ✅ 在 DBeaver 等 GUI 工具中执行
- ✅ 快速搭建测试环境
- ✅ 清理后的数据结构参考

**执行方式：**
```sql
-- 可以在任何 SQL 客户端执行
-- DBeaver: 右键 → Execute SQL Script
-- 或直接复制粘贴执行
```

**不适用：**
- ❌ 完整数据迁移（缺少运行时数据）
- ❌ 生产环境恢复（数据不完整）

## 数据完整性对比

| 数据类型 | workflow_platform_v1.sql | workflow_platform_executable_clean_fixed.sql |
|---------|--------------------------|---------------------------------------------|
| 表结构 | ✅ 完整 | ✅ 完整 |
| 系统用户 | ✅ 所有用户 | ⚠️ 仅基础用户（admin + 测试用户） |
| 角色权限 | ✅ 完整 | ✅ 完整 |
| 功能单元 | ✅ 所有功能单元 | ⚠️ 仅示例（采购申请） |
| 表单定义 | ✅ 所有表单 | ⚠️ 仅示例表单 |
| 动作定义 | ✅ 所有动作 | ⚠️ 仅示例动作 |
| 流程定义 | ✅ 所有流程 | ⚠️ 仅示例流程 |
| 流程实例 | ✅ 包含 | ❌ 不包含 |
| 任务实例 | ✅ 包含 | ❌ 不包含 |
| 历史数据 | ✅ 包含 | ❌ 不包含 |
| 业务数据 | ✅ 完整 | ❌ 不包含 |

## 推荐使用

### 场景 1：Windows + Azure PostgreSQL + DBeaver

**推荐文件：** `workflow_platform_v2_final.sql`（从 v1 转换而来）

**原因：**
- ✅ 可以在 DBeaver 中直接执行
- ✅ 包含完整数据
- ✅ JSON 转义已修复

**生成方式：**
```bash
python3 convert_copy_to_insert_v3.py workflow_platform_v1.sql temp.sql
python3 fix_json_escaping.py temp.sql workflow_platform_v2_final.sql
```

### 场景 2：初始化新开发环境

**推荐文件：** `workflow_platform_executable_clean_fixed.sql`

**原因：**
- ✅ 数据干净，无运行时数据
- ✅ 可以在任何工具中执行
- ✅ 文件小，执行快

### 场景 3：完整数据迁移

**推荐文件：** `workflow_platform_v1.sql`

**原因：**
- ✅ 包含所有数据
- ✅ 原生格式，性能最好

**执行方式：**
```bash
psql -h azure-server.postgres.database.azure.com -U user -d database -f workflow_platform_v1.sql
```

## 总结

| 特性 | workflow_platform_v1.sql | workflow_platform_executable_clean_fixed.sql |
|------|--------------------------|---------------------------------------------|
| 数据完整性 | ⭐⭐⭐⭐⭐ 完整 | ⭐⭐⭐ 基础数据 |
| 执行兼容性 | ⭐⭐ 仅 psql | ⭐⭐⭐⭐⭐ 任何工具 |
| 文件大小 | 3.2 MB | 789 KB |
| 执行速度 | ⭐⭐⭐⭐⭐ 快（COPY） | ⭐⭐⭐ 中等（INSERT） |
| 适用场景 | 完整备份/恢复 | 初始化开发环境 |

**建议：**
- 如果需要完整数据 + DBeaver 执行 → 使用 `workflow_platform_v2_final.sql`
- 如果只需要初始化环境 → 使用 `workflow_platform_executable_clean_fixed.sql`
- 如果可以用 psql + 需要完整数据 → 使用 `workflow_platform_v1.sql`
