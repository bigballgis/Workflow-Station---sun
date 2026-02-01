# 后端启动时执行 DDL 的原因分析

## 📋 问题

后端服务启动时为什么会执行 DDL（Data Definition Language）语句？

---

## 🔍 原因分析

后端启动时执行 DDL 有**三个主要原因**：

### 1. ⚠️ Hibernate `ddl-auto: update`（主要原因）

**影响的服务**:
- `admin-center`
- `user-portal`
- `developer-workstation`

**配置**:
```yaml
jpa:
  hibernate:
    ddl-auto: update  # ⚠️ 会自动创建/更新表结构
```

**行为**:
- 启动时，Hibernate 会检查实体类与数据库表结构是否一致
- 如果不一致，**自动执行 DDL** 来创建或修改表
- 例如：`CREATE TABLE`, `ALTER TABLE`, `ADD COLUMN` 等

**示例**:
```sql
-- Hibernate 自动生成的 DDL
CREATE TABLE IF NOT EXISTS sys_users (...);
ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS new_field VARCHAR(100);
```

---

### 2. ✅ Flyway 数据库迁移（正常行为）

**影响的服务**:
- `workflow-engine-core` (enabled: true)

**配置**:
```yaml
flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: true
```

**行为**:
- 启动时，Flyway 会检查 `db/migration/` 目录下的 SQL 脚本
- 执行**未运行过的迁移脚本**（按版本号顺序）
- 这是**正常的数据库版本管理**行为

**示例**:
```sql
-- db/migration/V1__init_schema.sql
CREATE TABLE IF NOT EXISTS sys_users (...);
```

---

### 3. ⚠️ Flowable 自动更新 Schema

**影响的服务**:
- `workflow-engine-core`

**配置**:
```yaml
flowable:
  database-schema-update: true  # ⚠️ 自动更新 Flowable 表结构
```

**行为**:
- Flowable 工作流引擎启动时，会检查自己的表结构
- 如果版本不匹配，**自动执行 DDL** 来更新表结构
- 例如：创建 `ACT_*` 系列表（Flowable 的工作流表）

**示例**:
```sql
-- Flowable 自动生成的 DDL
CREATE TABLE IF NOT EXISTS ACT_RU_EXECUTION (...);
ALTER TABLE ACT_RU_TASK ADD COLUMN IF NOT EXISTS ...;
```

---

## 📊 各服务 DDL 执行情况

| 服务 | Hibernate ddl-auto | Flyway | Flowable | 执行 DDL？ |
|------|-------------------|--------|----------|-----------|
| **workflow-engine-core** | `validate` | ✅ enabled | ✅ true | ✅ 是（Flyway + Flowable） |
| **admin-center** | `update` ⚠️ | ❌ disabled | ❌ 无 | ✅ 是（Hibernate） |
| **user-portal** | `update` ⚠️ | ❌ disabled | ❌ 无 | ✅ 是（Hibernate） |
| **developer-workstation** | `update` ⚠️ | ❌ disabled | ❌ 无 | ✅ 是（Hibernate） |
| **api-gateway** | 未配置 | ❌ 未配置 | ❌ 无 | ❌ 否 |

---

## ⚠️ 问题与风险

### 1. `ddl-auto: update` 的风险

**问题**:
- ❌ **生产环境不安全**：可能意外修改表结构
- ❌ **不可控**：自动生成的 DDL 可能不符合预期
- ❌ **数据丢失风险**：删除列、修改类型可能导致数据丢失
- ❌ **性能影响**：启动时执行 DDL 会延长启动时间

**示例问题**:
```sql
-- Hibernate 可能执行危险的 DDL
ALTER TABLE sys_users DROP COLUMN old_field;  -- 数据丢失！
ALTER TABLE sys_users ALTER COLUMN id TYPE VARCHAR(200);  -- 可能失败
```

---

### 2. `flowable.database-schema-update: true` 的风险

**问题**:
- ❌ 自动更新 Flowable 表结构，可能破坏现有工作流数据
- ❌ 版本升级时可能不兼容

---

## ✅ 推荐配置（生产环境）

### 方案 1: 使用 Flyway（推荐）

**优点**:
- ✅ 版本控制：所有 DDL 都在迁移脚本中
- ✅ 可追溯：知道每个变更的历史
- ✅ 可回滚：可以编写回滚脚本
- ✅ 团队协作：所有开发者使用相同的迁移脚本

**配置**:
```yaml
jpa:
  hibernate:
    ddl-auto: validate  # ✅ 只验证，不执行 DDL
    # 或
    ddl-auto: none      # ✅ 完全禁用 DDL

flyway:
  enabled: true         # ✅ 使用 Flyway 管理
  locations: classpath:db/migration
  baseline-on-migrate: true
```

---

### 方案 2: 完全禁用自动 DDL

**配置**:
```yaml
jpa:
  hibernate:
    ddl-auto: none  # ✅ 完全禁用

flyway:
  enabled: false    # ✅ 禁用 Flyway

flowable:
  database-schema-update: false  # ✅ 禁用 Flowable 自动更新
```

**适用场景**:
- 数据库由 DBA 手动管理
- 使用外部工具管理 schema

---

## 🔧 修复建议

### 高优先级（生产环境必须修复）

1. **admin-center**: `ddl-auto: update` → `ddl-auto: validate` 或 `none`
2. **user-portal**: `ddl-auto: update` → `ddl-auto: validate` 或 `none`
3. **developer-workstation**: `ddl-auto: update` → `ddl-auto: validate` 或 `none`

### 中优先级（建议修复）

4. **workflow-engine-core**: `flowable.database-schema-update: true` → `false`（如果使用 Flyway）

---

## 📝 配置对比

### 当前配置（开发环境）

```yaml
# admin-center, user-portal, developer-workstation
jpa:
  hibernate:
    ddl-auto: update  # ⚠️ 自动执行 DDL
```

### 推荐配置（生产环境）

```yaml
# 所有服务
jpa:
  hibernate:
    ddl-auto: validate  # ✅ 只验证，不执行 DDL

flyway:
  enabled: true         # ✅ 使用 Flyway 管理
```

---

## 🎯 总结

**为什么执行 DDL**:
1. ⚠️ **Hibernate `ddl-auto: update`** - 自动创建/更新表（3个服务）
2. ✅ **Flyway 迁移** - 执行迁移脚本（1个服务，正常行为）
3. ⚠️ **Flowable 自动更新** - 自动更新工作流表（1个服务）

**建议**:
- ✅ 开发环境：可以保留 `ddl-auto: update`（方便开发）
- ❌ 生产环境：必须改为 `ddl-auto: validate` 或 `none`
- ✅ 使用 Flyway 统一管理所有 DDL 变更

**当前状态**: 多个服务使用 `ddl-auto: update`，这在生产环境中是不安全的。
