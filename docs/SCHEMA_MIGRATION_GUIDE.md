# PostgreSQL Schema 切换指南

## 概述

本指南说明如何将数据库从默认的 `public` schema 切换到自定义 schema（如 `workflow`）。

## 1. 创建新 Schema

```sql
-- 创建新 schema
CREATE SCHEMA IF NOT EXISTS workflow;

-- 授权给 platform 用户
GRANT ALL ON SCHEMA workflow TO platform;
GRANT ALL ON ALL TABLES IN SCHEMA workflow TO platform;
GRANT ALL ON ALL SEQUENCES IN SCHEMA workflow TO platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT ALL ON TABLES TO platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT ALL ON SEQUENCES TO platform;
```

执行命令：
```bash
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "CREATE SCHEMA IF NOT EXISTS workflow; GRANT ALL ON SCHEMA workflow TO platform; GRANT ALL ON ALL TABLES IN SCHEMA workflow TO platform; GRANT ALL ON ALL SEQUENCES IN SCHEMA workflow TO platform; ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT ALL ON TABLES TO platform; ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT ALL ON SEQUENCES TO platform;"
```

## 2. 修改各模块配置

### 2.1 Admin Center

文件：`backend/admin-center/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=workflow}
    # 或者使用 searchpath
    # url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform?searchpath=workflow}
  
  jpa:
    properties:
      hibernate:
        default_schema: workflow
```

### 2.2 Developer Workstation

文件：`backend/developer-workstation/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=workflow}
  
  jpa:
    properties:
      hibernate:
        default_schema: workflow
```

### 2.3 User Portal

文件：`backend/user-portal/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=workflow}
  
  jpa:
    properties:
      hibernate:
        default_schema: workflow
```

### 2.4 Workflow Engine Core

文件：`backend/workflow-engine-core/src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=workflow}
  
  jpa:
    properties:
      hibernate:
        default_schema: workflow
```

## 3. Flyway 迁移配置

如果使用 Flyway，需要配置 schema：

```yaml
spring:
  flyway:
    enabled: true
    schemas: workflow
    default-schema: workflow
    locations: classpath:db/migration
```

## 4. 迁移现有数据（可选）

如果需要将 `public` schema 中的数据迁移到新 schema：

```sql
-- 方法1：移动表到新 schema
ALTER TABLE public.sys_users SET SCHEMA workflow;
ALTER TABLE public.sys_roles SET SCHEMA workflow;
-- ... 对所有表执行

-- 方法2：复制数据到新 schema
-- 先在新 schema 创建表结构，然后复制数据
INSERT INTO workflow.sys_users SELECT * FROM public.sys_users;
INSERT INTO workflow.sys_roles SELECT * FROM public.sys_roles;
-- ... 对所有表执行
```

### 批量迁移脚本

```bash
# 生成迁移所有表的 SQL
docker exec -i platform-postgres psql -U platform -d workflow_platform -t -c "
SELECT 'ALTER TABLE public.' || tablename || ' SET SCHEMA workflow;'
FROM pg_tables
WHERE schemaname = 'public'
  AND tablename LIKE 'sys_%' OR tablename LIKE 'dw_%' OR tablename LIKE 'admin_%';
" > migrate_tables.sql

# 执行迁移
Get-Content -Path "migrate_tables.sql" -Raw | docker exec -i platform-postgres psql -U platform -d workflow_platform
```

## 5. 验证配置

### 5.1 检查当前 schema

```sql
-- 查看当前 schema
SHOW search_path;

-- 查看所有 schema
SELECT schema_name FROM information_schema.schemata;

-- 查看指定 schema 中的表
SELECT table_name FROM information_schema.tables WHERE table_schema = 'workflow';
```

### 5.2 测试连接

```bash
# 使用新 schema 连接
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "SET search_path TO workflow; SELECT current_schema();"
```

## 6. Docker Compose 配置（可选）

如果使用 Docker Compose，可以在环境变量中配置：

```yaml
services:
  admin-center:
    environment:
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/workflow_platform?currentSchema=workflow
```

## 7. 注意事项

### 7.1 Schema 搜索路径

PostgreSQL 使用 `search_path` 来查找对象。有两种方式指定 schema：

1. **currentSchema** - 只使用指定的 schema
   ```
   jdbc:postgresql://localhost:5432/workflow_platform?currentSchema=workflow
   ```

2. **searchpath** - 按顺序搜索多个 schema
   ```
   jdbc:postgresql://localhost:5432/workflow_platform?searchpath=workflow,public
   ```

### 7.2 Hibernate 配置

Hibernate 的 `default_schema` 配置会在所有 SQL 语句中添加 schema 前缀：

```yaml
hibernate:
  default_schema: workflow
```

生成的 SQL：
```sql
SELECT * FROM workflow.sys_users;
```

### 7.3 跨 Schema 引用

如果需要跨 schema 引用表，必须使用完全限定名：

```sql
SELECT * FROM workflow.sys_users u
JOIN public.legacy_data l ON u.id = l.user_id;
```

### 7.4 权限问题

确保用户对新 schema 有足够的权限：

```sql
-- 检查权限
SELECT * FROM information_schema.role_table_grants 
WHERE grantee = 'platform' AND table_schema = 'workflow';

-- 授予权限
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA workflow TO platform;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA workflow TO platform;
```

## 8. 回滚方案

如果需要回滚到 `public` schema：

1. 修改所有 `application.yml`，移除 schema 配置
2. 将表移回 `public` schema：
   ```sql
   ALTER TABLE workflow.sys_users SET SCHEMA public;
   ```

## 9. 推荐配置

### 开发环境

使用 `public` schema（默认），简单直接。

### 生产环境

使用自定义 schema（如 `workflow`），原因：
- 更好的组织结构
- 避免与其他应用冲突
- 更清晰的权限管理
- 支持多租户架构

## 10. 完整示例

### 创建并切换到新 schema

```bash
# 1. 创建 schema
docker exec -i platform-postgres psql -U platform -d workflow_platform << EOF
CREATE SCHEMA IF NOT EXISTS workflow;
GRANT ALL ON SCHEMA workflow TO platform;
GRANT ALL ON ALL TABLES IN SCHEMA workflow TO platform;
GRANT ALL ON ALL SEQUENCES IN SCHEMA workflow TO platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT ALL ON TABLES TO platform;
ALTER DEFAULT PRIVILEGES IN SCHEMA workflow GRANT ALL ON SEQUENCES TO platform;
EOF

# 2. 迁移表（如果需要）
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
DO \$\$
DECLARE
    r RECORD;
BEGIN
    FOR r IN 
        SELECT tablename FROM pg_tables 
        WHERE schemaname = 'public' 
        AND (tablename LIKE 'sys_%' OR tablename LIKE 'dw_%' OR tablename LIKE 'admin_%')
    LOOP
        EXECUTE 'ALTER TABLE public.' || r.tablename || ' SET SCHEMA workflow';
    END LOOP;
END \$\$;
"

# 3. 验证
docker exec -i platform-postgres psql -U platform -d workflow_platform -c "
SELECT table_schema, table_name 
FROM information_schema.tables 
WHERE table_schema = 'workflow' 
ORDER BY table_name;
"
```

## 参考资料

- [PostgreSQL Schema Documentation](https://www.postgresql.org/docs/current/ddl-schemas.html)
- [Hibernate Schema Configuration](https://docs.jboss.org/hibernate/orm/current/userguide/html_single/Hibernate_User_Guide.html#configurations-database-connection)
- [Spring Boot Database Initialization](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization)
