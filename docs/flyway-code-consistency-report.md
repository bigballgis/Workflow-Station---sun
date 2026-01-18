# Flyway 迁移脚本与代码一致性检查报告

生成时间: 2026-01-14

---

## 1. sys_users 表对比

### Flyway 迁移脚本定义 (platform-security V1__init_schema.sql)

```sql
CREATE TABLE IF NOT EXISTS sys_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,              -- ⚠️ NOT NULL
    display_name VARCHAR(50),
    full_name VARCHAR(100) NOT NULL,          -- ⚠️ NOT NULL
    phone VARCHAR(50),                        -- ⚠️ 代码中缺失
    employee_id VARCHAR(50),
    position VARCHAR(100),
    entity_manager_id VARCHAR(64),
    function_manager_id VARCHAR(64),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    language VARCHAR(10) DEFAULT 'zh_CN',
    must_change_password BOOLEAN DEFAULT false,
    password_expired_at TIMESTAMP,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(50),
    failed_login_count INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    deleted BOOLEAN NOT NULL DEFAULT false,
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(64),
    ...
);
```

### 代码实体对比

#### 1.1 platform-security/model/User.java

| 字段 | Flyway | 代码 | 状态 |
|------|--------|------|------|
| `username` | VARCHAR(100) | VARCHAR(50) | ❌ **不一致** |
| `email` | NOT NULL | nullable | ❌ **不一致** |
| `full_name` | NOT NULL | nullable | ❌ **不一致** |
| `phone` | VARCHAR(50) | **缺失** | ❌ **不一致** |
| `business_unit_id` | 不存在 | 不存在 | ✅ 一致 |

**代码定义**:
```java
@Column(unique = true, nullable = false, length = 50)  // ❌ length=50 vs 100
private String username;

@Column(length = 100)  // ❌ nullable vs NOT NULL
private String email;

@Column(name = "full_name", length = 100)  // ❌ nullable vs NOT NULL
private String fullName;

// ❌ 缺少 phone 字段
```

#### 1.2 admin-center/entity/User.java

| 字段 | Flyway | 代码 | 状态 |
|------|--------|------|------|
| `username` | VARCHAR(100) | VARCHAR(100) | ✅ **一致** |
| `email` | NOT NULL | nullable | ❌ **不一致** |
| `full_name` | NOT NULL | nullable | ❌ **不一致** |
| `phone` | VARCHAR(50) | **缺失** | ❌ **不一致** |
| `business_unit_id` | V3添加, V4移除 | **存在** | ⚠️ **可能不一致** |

**代码定义**:
```java
@Column(name = "username", nullable = false, unique = true, length = 100)  // ✅ 匹配
private String username;

@Column(name = "email")  // ❌ nullable vs NOT NULL
private String email;

@Column(name = "full_name", length = 100)  // ❌ nullable vs NOT NULL
private String fullName;

@Column(name = "business_unit_id", length = 64)  // ⚠️ 需确认是否在数据库中存在
private String businessUnitId;

// ❌ 缺少 phone 字段
```

#### 1.3 developer-workstation/entity/User.java

| 字段 | Flyway | 代码 | 状态 |
|------|--------|------|------|
| `username` | VARCHAR(100) | 无长度限制 | ⚠️ **不明确** |
| `email` | NOT NULL | nullable | ❌ **不一致** |
| `full_name` | NOT NULL | **缺失** | ❌ **不一致** |
| `phone` | VARCHAR(50) | **缺失** | ❌ **不一致** |

**代码定义**:
```java
@Column(name = "username", nullable = false, unique = true)  // ⚠️ 无长度限制
private String username;

@Column(name = "email")  // ❌ nullable vs NOT NULL
private String email;

// ❌ 缺少 full_name 字段
// ❌ 缺少 phone 字段
```

#### 1.4 user-portal/entity/User.java

| 字段 | Flyway | 代码 | 状态 |
|------|--------|------|------|
| `username` | VARCHAR(100) | 无长度限制 | ⚠️ **不明确** |
| `email` | NOT NULL | nullable | ❌ **不一致** |
| `full_name` | NOT NULL | **缺失** | ❌ **不一致** |
| `phone` | VARCHAR(50) | **缺失** | ❌ **不一致** |

**代码定义**:
```java
@Column(name = "username", nullable = false, unique = true)  // ⚠️ 无长度限制
private String username;

@Column(name = "email")  // ❌ nullable vs NOT NULL
private String email;

// ❌ 缺少 full_name 字段
// ❌ 缺少 phone 字段
```

---

## 2. business_unit_id 字段历史

### 迁移脚本历史

1. **V1 (platform-security)**: 不包含 `business_unit_id`
2. **V3 (admin-center)**: 添加 `business_unit_id` (从 `department_id` 重命名)
3. **V4 (platform-security)**: 移除 `primary_business_unit_id` (改用 `sys_user_business_units` 关联表)

**结论**: `business_unit_id` 字段可能：
- 在 `admin-center` 的数据库中仍然存在（V3 迁移）
- 在 `platform-security` 的数据库中不存在（V4 移除了 `primary_business_unit_id`）
- 这会导致 `admin-center/entity/User.java` 中的 `business_unit_id` 字段在不同数据库环境中不一致

---

## 3. dw_field_definitions 表对比

### Flyway 迁移脚本 (developer-workstation V1__init_schema.sql)

```sql
CREATE TABLE IF NOT EXISTS dw_field_definitions (
    id BIGSERIAL PRIMARY KEY,
    table_id BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    data_type VARCHAR(50) NOT NULL,
    length INTEGER,
    precision_value INTEGER,
    scale INTEGER,
    nullable BOOLEAN DEFAULT TRUE,
    default_value VARCHAR(500),
    is_primary_key BOOLEAN DEFAULT FALSE,
    is_unique BOOLEAN DEFAULT FALSE,
    description TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    ...
);
```

### 代码实体 (developer-workstation/entity/FieldDefinition.java)

```java
@Column(name = "sort_order", nullable = false)  // ✅ 匹配
private Integer sortOrder;
```

**结论**: ✅ **dw_field_definitions 表完全匹配**

---

## 4. dw_table_definitions 表对比

### Flyway 迁移脚本 (developer-workstation V1__init_schema.sql)

```sql
CREATE TABLE IF NOT EXISTS dw_table_definitions (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    table_type VARCHAR(20) NOT NULL,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ...
);
```

### 代码实体 (developer-workstation/entity/TableDefinition.java)

```java
@CreatedDate
@Column(name = "created_at", nullable = false, updatable = false)
private Instant createdAt;  // ⚠️ Instant vs TIMESTAMP

@LastModifiedDate
@Column(name = "updated_at")
private Instant updatedAt;  // ⚠️ Instant vs TIMESTAMP
```

**结论**: ⚠️ **类型差异** - 代码使用 `Instant`，数据库使用 `TIMESTAMP`（通常可以兼容，但需要注意时区）

---

## 5. 问题总结

### 🔴 高优先级问题

1. **sys_users.username 长度不一致**
   - **问题**: `platform-security/User.java` 使用 `length=50`，但 Flyway 定义 `VARCHAR(100)`
   - **影响**: 可能导致数据截断或约束冲突
   - **建议**: 统一修改为 `length=100`

2. **sys_users.email NOT NULL 约束不一致**
   - **问题**: Flyway 定义为 `NOT NULL`，但所有代码实体都为 `nullable`
   - **影响**: 插入 NULL 值会导致数据库错误
   - **建议**: 要么移除 Flyway 的 `NOT NULL`，要么在所有实体中添加 `nullable = false`

3. **sys_users.full_name NOT NULL 约束不一致**
   - **问题**: Flyway 定义为 `NOT NULL`，但所有代码实体都为 `nullable`
   - **影响**: 插入 NULL 值会导致数据库错误
   - **建议**: 要么移除 Flyway 的 `NOT NULL`，要么在所有实体中添加 `nullable = false`

4. **sys_users.phone 字段缺失**
   - **问题**: Flyway 定义了 `phone VARCHAR(50)`，但所有代码实体都缺少此字段
   - **影响**: 无法通过 JPA 访问 phone 字段
   - **建议**: 在所有 User 实体中添加 `phone` 字段，或从 Flyway 中移除（如果不需要）

### 🟡 中优先级问题

5. **sys_users.business_unit_id 字段不一致**
   - **问题**: `admin-center/User.java` 包含 `business_unit_id`，但迁移历史显示该字段在 V4 中被移除
   - **影响**: 在不同数据库中可能行为不一致
   - **建议**: 确认是否仍然使用 `business_unit_id`，如果不使用，从代码中移除

6. **User 实体字段不完整**
   - **问题**: `developer-workstation/User.java` 和 `user-portal/User.java` 缺少多个字段
   - **影响**: 无法通过 JPA 访问这些字段
   - **建议**: 统一所有 User 实体的字段定义，或明确说明某些服务只需要部分字段

### 🟢 低优先级问题

7. **Instant vs TIMESTAMP 类型差异**
   - **问题**: 代码使用 `Instant`，数据库使用 `TIMESTAMP`
   - **影响**: 通常可以正常工作，但需要注意时区处理
   - **建议**: 保持现状，但确保时区配置正确

---

## 6. 修复建议

### 方案 A: 修改 Flyway 迁移脚本（推荐用于 email 和 full_name）

如果业务逻辑允许 `email` 和 `full_name` 为空，修改 Flyway 迁移脚本：

```sql
-- 在平台安全 V1 迁移脚本中
ALTER TABLE sys_users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE sys_users ALTER COLUMN full_name DROP NOT NULL;
```

### 方案 B: 修改代码实体（推荐用于 username 长度）

统一所有 User 实体的字段定义，使其与 Flyway 一致：

```java
// platform-security/User.java
@Column(unique = true, nullable = false, length = 100)  // 改为 100
private String username;

// 添加 phone 字段
@Column(length = 50)
private String phone;
```

### 方案 C: 创建新的迁移脚本修复不一致

创建新的迁移脚本统一所有差异：

```sql
-- V6__fix_user_table_consistency.sql
-- 1. 确保 email 和 full_name 可以为空（如果业务允许）
ALTER TABLE sys_users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE sys_users ALTER COLUMN full_name DROP NOT NULL;

-- 2. 确保 username 长度正确
-- 如果当前数据长度都 <= 100，则无需修改

-- 3. 确保 phone 字段存在（如果 Flyway V1 中已有，则无需修改）
-- ALTER TABLE sys_users ADD COLUMN IF NOT EXISTS phone VARCHAR(50);
```

---

## 7. 检查清单

- [ ] 统一 `username` 长度为 100
- [ ] 决定 `email` 和 `full_name` 的 NOT NULL 约束（业务需求决定）
- [ ] 在所有 User 实体中添加 `phone` 字段（或从 Flyway 中移除）
- [ ] 确认 `business_unit_id` 字段的使用情况
- [ ] 统一所有 User 实体的字段定义
- [ ] 测试数据库约束与代码实体的一致性

---

## 8. 下一步行动

1. **立即修复**: `username` 长度不一致问题
2. **评估需求**: 确认 `email`、`full_name`、`phone` 的业务需求
3. **创建迁移**: 如果需要，创建新的迁移脚本统一差异
4. **更新代码**: 根据决定更新所有 User 实体类
5. **测试验证**: 在测试环境中验证修复
