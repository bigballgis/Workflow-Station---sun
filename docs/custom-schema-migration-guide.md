# 自定义 Schema 迁移指南

生成时间: 2026-01-18

本指南将帮助您将项目从默认的 `public` schema 迁移到自定义 schema（例如 `workflow`）。

---

## 📋 目录

1. [修改概览](#修改概览)
2. [步骤 1: 创建自定义 Schema](#步骤-1-创建自定义-schema)
3. [步骤 2: 修改应用配置](#步骤-2-修改应用配置)
4. [步骤 3: 修改 Flyway 迁移脚本](#步骤-3-修改-flyway-迁移脚本)
5. [步骤 4: 修改 JPA 实体类](#步骤-4-修改-jpa-实体类)
6. [步骤 5: 修改数据库连接配置](#步骤-5-修改数据库连接配置)
7. [步骤 6: 迁移现有数据（可选）](#步骤-6-迁移现有数据可选)
8. [验证和测试](#验证和测试)

---

## 修改概览

使用自定义 schema 需要修改以下内容：

| 修改项 | 文件数量 | 说明 |
|--------|---------|------|
| **应用配置** | 5+ | 设置 Hibernate 默认 schema |
| **Flyway 脚本** | 8 | 在 CREATE TABLE 中指定 schema |
| **JPA 实体类** | 20+ | 在 @Table 注解中添加 schema |
| **数据库连接** | 5+ | 设置 search_path |

**建议的 Schema 名称**: `workflow` 或 `wf_platform`

---

## 步骤 1: 创建自定义 Schema

### 在 DBeaver 中执行

```sql
-- 创建自定义 schema（例如：workflow）
CREATE SCHEMA IF NOT EXISTS workflow;

-- 授予权限
GRANT ALL PRIVILEGES ON SCHEMA workflow TO platform;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA workflow TO platform;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA workflow TO platform;

-- 设置默认权限（新创建的表自动授权）
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow 
GRANT ALL ON TABLES TO platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow 
GRANT ALL ON SEQUENCES TO platform;

-- 验证创建
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name = 'workflow';
```

### 或者使用命令行

```bash
psql -h localhost -U platform -d workflow_platform -c "CREATE SCHEMA IF NOT EXISTS workflow;"
```

---

## 步骤 2: 修改应用配置

### 2.1 修改所有模块的 `application.yml`

在每个模块的 `application.yml` 中，添加 Hibernate 的默认 schema 配置：

**文件位置**:
- `backend/platform-security/src/main/resources/application.yml`
- `backend/workflow-engine-core/src/main/resources/application.yml`
- `backend/admin-center/src/main/resources/application.yml`
- `backend/developer-workstation/src/main/resources/application.yml`
- `backend/user-portal/src/main/resources/application.yml`

**修改内容**:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        default_schema: workflow  # ✅ 添加这一行
        # 或者使用物理命名策略
        # physical_naming_strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

**完整示例** (`backend/platform-security/src/main/resources/application.yml`):

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform
    username: platform
    password: platform123
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        default_schema: workflow  # ✅ 新增
  
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    schemas: workflow  # ✅ 新增：指定 Flyway 使用的 schema
```

### 2.2 修改 Flyway 配置

在 `application.yml` 中添加 Flyway schema 配置：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    schemas: workflow  # ✅ 指定 Flyway 使用的 schema
    default-schema: workflow  # ✅ 可选：设置默认 schema
```

---

## 步骤 3: 修改 Flyway 迁移脚本

### 3.1 修改所有迁移脚本

需要在所有 `CREATE TABLE` 语句前添加 schema 限定符。

**需要修改的文件**:
- `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql`
- `backend/platform-security/src/main/resources/db/migration/V2__init_data.sql`
- `backend/workflow-engine-core/src/main/resources/db/migration/V1__init_schema.sql`
- `backend/admin-center/src/main/resources/db/migration/V1__init_schema.sql`
- `backend/developer-workstation/src/main/resources/db/migration/V1__init_schema.sql`
- `backend/developer-workstation/src/main/resources/db/migration/V2__init_data.sql`
- `backend/developer-workstation/src/main/resources/db/migration/V3__init_process.sql`
- `backend/user-portal/src/main/resources/db/migration/V1__init_schema.sql`

### 3.2 修改示例

**修改前**:
```sql
CREATE TABLE IF NOT EXISTS sys_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    ...
);
```

**修改后**:
```sql
-- 设置默认 schema（可选，如果所有表都在同一个 schema）
SET search_path TO workflow;

CREATE TABLE IF NOT EXISTS workflow.sys_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    ...
);

-- 或者不使用 SET search_path，直接在每个表名前加 schema
CREATE TABLE IF NOT EXISTS workflow.sys_users (
    ...
);
```

### 3.3 批量替换脚本

可以使用以下 SQL 模式进行批量替换：

**查找模式**: `CREATE TABLE IF NOT EXISTS `
**替换为**: `CREATE TABLE IF NOT EXISTS workflow.`

**查找模式**: `CREATE INDEX IF NOT EXISTS `
**替换为**: `CREATE INDEX IF NOT EXISTS workflow.`

**查找模式**: `CREATE SEQUENCE IF NOT EXISTS `
**替换为**: `CREATE SEQUENCE IF NOT EXISTS workflow.`

**查找模式**: `ALTER TABLE `
**替换为**: `ALTER TABLE workflow.`

**查找模式**: `DROP TABLE IF EXISTS `
**替换为**: `DROP TABLE IF EXISTS workflow.`

---

## 步骤 4: 修改 JPA 实体类

### 4.1 在所有 @Table 注解中添加 schema

**需要修改的实体类**（示例）:
- `backend/platform-security/src/main/java/com/platform/security/model/User.java`
- `backend/admin-center/src/main/java/com/admin/entity/User.java`
- `backend/admin-center/src/main/java/com/admin/entity/VirtualGroup.java`
- `backend/developer-workstation/src/main/java/com/developer/entity/TableDefinition.java`
- `backend/developer-workstation/src/main/java/com/developer/entity/FieldDefinition.java`
- ... 以及其他所有实体类

### 4.2 修改示例

**修改前**:
```java
@Entity
@Table(name = "sys_users", indexes = {
    @Index(name = "idx_user_username", columnList = "username")
})
public class User {
    ...
}
```

**修改后**:
```java
@Entity
@Table(name = "sys_users", schema = "workflow", indexes = {  // ✅ 添加 schema = "workflow"
    @Index(name = "idx_user_username", columnList = "username")
})
public class User {
    ...
}
```

### 4.3 批量查找需要修改的文件

使用以下命令查找所有需要修改的实体类：

```bash
# 查找所有包含 @Table 注解的文件
grep -r "@Table" backend --include="*.java" | grep -v "schema ="
```

---

## 步骤 5: 修改数据库连接配置

### 5.1 在 JDBC URL 中设置 search_path

**修改前**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform
```

**修改后**:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=workflow
    # 或者
    url: jdbc:postgresql://localhost:5432/workflow_platform?searchpath=workflow
```

### 5.2 在连接池配置中设置

**HikariCP 配置**:
```yaml
spring:
  datasource:
    hikari:
      schema: workflow  # ✅ 设置默认 schema
```

---

## 步骤 6: 迁移现有数据（可选）

如果您已经有数据在 `public` schema 中，需要迁移到新 schema：

### 6.1 迁移脚本

```sql
-- 1. 创建新 schema（如果还没创建）
CREATE SCHEMA IF NOT EXISTS workflow;

-- 2. 迁移所有表到新 schema
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN 
        SELECT tablename 
        FROM pg_tables 
        WHERE schemaname = 'public'
    LOOP
        EXECUTE format('ALTER TABLE public.%I SET SCHEMA workflow', r.tablename);
    END LOOP;
END $$;

-- 3. 迁移所有序列
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN 
        SELECT sequence_name 
        FROM information_schema.sequences 
        WHERE sequence_schema = 'public'
    LOOP
        EXECUTE format('ALTER SEQUENCE public.%I SET SCHEMA workflow', r.sequence_name);
    END LOOP;
END $$;

-- 4. 迁移所有视图（如果有）
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN 
        SELECT table_name 
        FROM information_schema.views 
        WHERE table_schema = 'public'
    LOOP
        EXECUTE format('ALTER VIEW public.%I SET SCHEMA workflow', r.table_name);
    END LOOP;
END $$;

-- 5. 验证迁移
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'workflow' 
ORDER BY table_name;
```

### 6.2 迁移 Flyway 历史表

```sql
-- 迁移 Flyway 历史表
ALTER TABLE IF EXISTS public.flyway_schema_history SET SCHEMA workflow;
```

---

## 验证和测试

### 1. 验证 Schema 创建

```sql
-- 检查 schema 是否存在
SELECT schema_name 
FROM information_schema.schemata 
WHERE schema_name = 'workflow';

-- 检查表是否在新 schema 中
SELECT table_schema, table_name 
FROM information_schema.tables 
WHERE table_schema = 'workflow' 
ORDER BY table_name;
```

### 2. 验证应用配置

启动应用后，检查日志中是否有 schema 相关的错误。

### 3. 测试数据库操作

```sql
-- 测试查询
SELECT * FROM workflow.sys_users LIMIT 1;

-- 测试插入（如果有测试数据）
INSERT INTO workflow.sys_users (id, username, password_hash, status) 
VALUES ('test-001', 'testuser', 'hash', 'ACTIVE');
```

---

## 快速修改清单

- [ ] 创建自定义 schema（例如：`workflow`）
- [ ] 修改所有模块的 `application.yml`，添加 `default_schema: workflow`
- [ ] 修改所有模块的 Flyway 配置，添加 `schemas: workflow`
- [ ] 修改所有 Flyway 迁移脚本，在表名前添加 `workflow.`
- [ ] 修改所有 JPA 实体类，在 `@Table` 注解中添加 `schema = "workflow"`
- [ ] 修改数据库连接 URL，添加 `?currentSchema=workflow`
- [ ] 迁移现有数据（如果有）
- [ ] 测试应用启动
- [ ] 验证数据库操作正常

---

## 常见问题

### Q1: 是否必须修改所有实体类？

**A**: 如果设置了 Hibernate 的 `default_schema`，理论上不需要在每个 `@Table` 中指定 schema。但为了明确性和避免混淆，建议都加上。

### Q2: 可以同时使用多个 schema 吗？

**A**: 可以，但需要：
- 在每个 `@Table` 注解中明确指定 schema
- 在 Flyway 脚本中明确指定 schema
- 不使用全局的 `default_schema`

### Q3: 迁移后如何回退到 public schema？

**A**: 
1. 将所有表移回 public schema
2. 移除所有配置中的 schema 设置
3. 修改实体类移除 schema 属性
4. 修改 Flyway 脚本移除 schema 前缀

### Q4: Flowable 工作流引擎的表怎么办？

**A**: Flowable 的表（`act_*`）通常会自动创建。如果设置了 `default_schema`，它们也会创建在新 schema 中。如果需要分离，可以配置 Flowable 使用不同的 schema。

---

## 自动化脚本建议

可以创建一个脚本来自动化部分修改：

1. **批量替换 Flyway 脚本**：使用 sed 或 PowerShell 脚本
2. **批量修改实体类**：使用 IDE 的批量查找替换功能
3. **验证修改**：创建测试脚本验证所有表都在正确的 schema 中

---

**最后更新**: 2026-01-18
