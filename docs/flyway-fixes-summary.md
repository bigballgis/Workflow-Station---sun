# Flyway 修复总结

生成时间: 2026-01-14

## 已完成的修复

### 1. sys_users 表 - email 字段

**修复前:**
```sql
email VARCHAR(255) NOT NULL,
```

**修复后:**
```sql
email VARCHAR(255),
```

**修改位置:** `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` 第18行

**原因:** 代码实体中所有 User 类的 `email` 字段都是 `nullable`，与 Flyway 的 `NOT NULL` 约束不一致。

---

### 2. sys_users 表 - full_name 字段

**修复前:**
```sql
full_name VARCHAR(100) NOT NULL,
```

**修复后:**
```sql
full_name VARCHAR(100),
```

**修改位置:** `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` 第20行

**原因:** 代码实体中所有 User 类的 `full_name` 字段都是 `nullable`，与 Flyway 的 `NOT NULL` 约束不一致。

---

### 3. platform-security/User.java - username 长度

**修复前:**
```java
@Column(unique = true, nullable = false, length = 50)
private String username;
```

**修复后:**
```java
@Column(unique = true, nullable = false, length = 100)
private String username;
```

**修改位置:** `backend/platform-security/src/main/java/com/platform/security/model/User.java` 第34行

**原因:** Flyway 迁移脚本中定义为 `VARCHAR(100)`，admin-center/User.java 也使用 `length=100`，需要统一。

---

### 4. platform-security/User.java - email 字段注解

**修复前:**
```java
@Column(length = 100)
private String email;
```

**修复后:**
```java
@Column(name = "email", length = 255)
private String email;
```

**修改位置:** `backend/platform-security/src/main/java/com/platform/security/model/User.java` 第40行

**原因:** 
- 添加明确的 `name = "email"` 映射
- 长度从 100 改为 255，与 Flyway 定义一致

---

## 修复后的状态

### Flyway 迁移脚本 (V1__init_schema.sql)

```sql
CREATE TABLE IF NOT EXISTS sys_users (
    id VARCHAR(64) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,      -- ✅ 保持 100
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),                         -- ✅ 已移除 NOT NULL
    display_name VARCHAR(50),
    full_name VARCHAR(100),                     -- ✅ 已移除 NOT NULL
    phone VARCHAR(50),
    ...
);
```

### 代码实体对比

| 字段 | Flyway | platform-security | admin-center | 状态 |
|------|--------|-------------------|--------------|------|
| `username` | VARCHAR(100) | VARCHAR(100) ✅ | VARCHAR(100) ✅ | ✅ **一致** |
| `email` | nullable | nullable ✅ | nullable ✅ | ✅ **一致** |
| `full_name` | nullable | nullable ✅ | nullable ✅ | ✅ **一致** |
| `phone` | VARCHAR(50) | 缺失 | 缺失 | ⚠️ 代码中缺失（但 Flyway 保留）|

---

## 注意事项

### 对于已存在的数据库

如果数据库已经运行了旧的 V1 迁移脚本，需要手动执行以下 SQL 来修复约束：

```sql
-- 移除 email 的 NOT NULL 约束
ALTER TABLE sys_users ALTER COLUMN email DROP NOT NULL;

-- 移除 full_name 的 NOT NULL 约束
ALTER TABLE sys_users ALTER COLUMN full_name DROP NOT NULL;
```

### 对于新数据库

新的数据库将直接使用修复后的 V1 迁移脚本，自动保持一致。

---

## 剩余问题

### phone 字段

- **Flyway:** 包含 `phone VARCHAR(50)` 字段
- **代码:** 所有 User 实体都缺少 `phone` 字段
- **建议:** 
  - 如果业务需要 phone 字段，在所有 User 实体中添加
  - 如果不需要，可以考虑从 Flyway 中移除（但可能影响现有数据库）

### business_unit_id 字段

- **admin-center/User.java:** 包含 `business_unit_id` 字段
- **Flyway V1:** 不包含此字段
- **历史:** V3 迁移添加，V4 迁移移除 `primary_business_unit_id`
- **建议:** 确认业务需求，如果不需要，从 admin-center/User.java 中移除

---

## 验证清单

- [x] email 字段 - Flyway 与代码一致（nullable）
- [x] full_name 字段 - Flyway 与代码一致（nullable）
- [x] username 长度 - platform-security 代码与 Flyway 一致（100）
- [x] email 长度 - platform-security 代码与 Flyway 一致（255）
- [ ] phone 字段 - 需要决定是否添加到代码中
- [ ] business_unit_id 字段 - 需要确认业务需求

---

## 下一步

1. **测试验证:** 在测试环境中验证修复后的迁移脚本和代码
2. **数据库迁移:** 如果生产数据库已存在，执行上述 ALTER TABLE 语句
3. **代码审查:** 确认 phone 和 business_unit_id 字段的业务需求
