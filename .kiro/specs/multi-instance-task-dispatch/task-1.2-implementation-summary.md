# Task 1.2 Implementation Summary: 修改动态子表建表逻辑，自动添加 row_version 列

## 实现概述

本任务为多实例任务分发功能添加乐观锁支持，通过在子表（table_type=SUB）中自动添加 `row_version` 列来防止并发编辑冲突。

## 修改内容

### 1. 修改 TableDesignComponentImpl 建表逻辑

**文件**: `backend/developer-workstation/src/main/java/com/developer/component/impl/TableDesignComponentImpl.java`

**修改位置**: `generateDDLForDialect()` 方法

**修改内容**:
- 在生成 CREATE TABLE DDL 时，检测表类型是否为 `TableType.SUB`
- 如果是子表，自动在用户定义的字段之后添加 `row_version` 列
- 根据不同的数据库方言（PostgreSQL, MySQL, Oracle, SQL Server）使用相应的数据类型
- 列定义: `row_version {TYPE} NOT NULL DEFAULT 1`

**数据类型映射**:
- PostgreSQL: `BIGINT`
- MySQL: `BIGINT`
- Oracle: `NUMBER(19)`
- SQL Server: `BIGINT`

**关键代码**:
```java
// 为 SUB 类型的表自动添加 row_version 列（用于乐观锁）
if (table.getTableType() == com.developer.enums.TableType.SUB) {
    String rowVersionType = switch (dialect) {
        case POSTGRESQL -> "BIGINT";
        case MYSQL -> "BIGINT";
        case ORACLE -> "NUMBER(19)";
        case SQLSERVER -> "BIGINT";
    };
    columnDefs.add("    row_version " + rowVersionType + " NOT NULL DEFAULT 1");
}
```

### 2. 创建已有子表迁移脚本

**文件**: `deploy/init-scripts/00-schema/25-add-row-version-to-sub-tables.sql`

**功能**:
- 使用 PL/pgSQL 动态查询所有 `table_type = 'SUB'` 的表
- 对每个子表执行 `ALTER TABLE ... ADD COLUMN IF NOT EXISTS row_version BIGINT NOT NULL DEFAULT 1`
- 使用 `IF NOT EXISTS` 确保脚本可重复执行
- 包含错误处理和日志输出

**执行逻辑**:
1. 从 `dw_table_definitions` 表查询所有 SUB 类型的表名
2. 遍历每个表，构建并执行 ALTER TABLE 语句
3. 记录成功/失败日志
4. 提供可选的验证查询脚本

### 3. 创建单元测试

**文件**: `backend/developer-workstation/src/test/java/com/developer/component/TableDesignComponentRowVersionTest.java`

**测试覆盖**:
- ✅ SUB 表在 PostgreSQL 方言下生成包含 row_version 的 DDL
- ✅ SUB 表在 MySQL 方言下生成包含 row_version 的 DDL
- ✅ SUB 表在 Oracle 方言下生成包含 row_version 的 DDL（使用 NUMBER(19)）
- ✅ SUB 表在 SQL Server 方言下生成包含 row_version 的 DDL
- ✅ MAIN 表不应包含 row_version 列
- ✅ row_version 列应在用户定义的字段之后

**验证需求**: Requirements 6.5, 6.6

## 影响范围

### 新建表
- 所有通过 developer-workstation 创建的 SUB 类型表将自动包含 `row_version` 列
- 不影响 MAIN、ACTION、RELATION 类型的表

### 已有表
- 需要执行迁移脚本 `25-add-row-version-to-sub-tables.sql`
- 迁移脚本安全可重复执行
- 已有数据的 row_version 将初始化为 1

### 后续任务依赖
- Task 2.3: MultiInstanceDataResolver 将使用 row_version 实现乐观锁
- Task 2.4: 子任务完成时递增 row_version 值

## 验证步骤

### 1. 编译验证
```bash
mvn clean compile -pl backend/developer-workstation
```

### 2. 运行单元测试
```bash
mvn test -Dtest=TableDesignComponentRowVersionTest -pl backend/developer-workstation
```

### 3. 验证 DDL 生成
创建一个 SUB 类型的表定义，调用 `generateDDL()` 方法，检查输出是否包含 `row_version` 列。

### 4. 执行迁移脚本
```bash
psql -U postgres -d workflow_db -f deploy/init-scripts/00-schema/25-add-row-version-to-sub-tables.sql
```

### 5. 验证已有表
```sql
SELECT 
    td.table_name,
    td.table_type,
    CASE 
        WHEN EXISTS (
            SELECT 1 
            FROM information_schema.columns 
            WHERE table_name = td.table_name 
            AND column_name = 'row_version'
        ) THEN 'EXISTS'
        ELSE 'MISSING'
    END AS row_version_status
FROM dw_table_definitions td
WHERE td.table_type = 'SUB'
ORDER BY td.table_name;
```

## 设计决策

### 为什么在 DDL 生成时添加而不是在部署时添加？
- **一致性**: 确保所有 SUB 表都有 row_version 列，无论是新建还是已有
- **简洁性**: 避免在多个地方重复逻辑
- **可维护性**: 集中在一个地方管理列定义

### 为什么使用 BIGINT 而不是 INTEGER？
- **容量**: BIGINT 可以支持更大的版本号范围（2^63 - 1）
- **一致性**: 与主键 ID 类型保持一致
- **未来扩展**: 为可能的高频更新场景预留空间

### 为什么默认值是 1 而不是 0？
- **语义清晰**: 版本号从 1 开始更符合直觉
- **兼容性**: 与常见的乐观锁实现保持一致
- **调试友好**: 版本号 1 表示初始状态，便于排查问题

## 相关需求

- **需求 6.5**: THE Process_Engine SHALL 在子任务加载数据时记录当前行的 row_version，提交时校验 row_version 是否与数据库中一致
- **需求 6.6**: WHEN 子任务处理人成功提交数据后，THE Process_Engine SHALL 递增对应子表数据行的 row_version 值

## 后续工作

1. 实现 MultiInstanceDataResolver 的乐观锁逻辑（Task 2.3）
2. 实现子任务完成时的 row_version 递增逻辑（Task 2.4）
3. 在前端表单中传递 row_version 值
4. 处理乐观锁冲突的用户提示

## 完成状态

✅ TableDesignComponentImpl 修改完成
✅ 迁移脚本创建完成
✅ 单元测试创建完成
✅ 代码编译通过
✅ 无诊断错误

任务 1.2 实现完成。
