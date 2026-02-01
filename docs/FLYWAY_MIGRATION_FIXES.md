# Flyway 迁移脚本修复说明

## 修复内容

### 1. 修复 `wf_process_variables.binary_value` 列类型

**问题**: 数据库中 `wf_process_variables.binary_value` 列类型为 `oid`，但应该为 `bytea`。

**原因**: 
- PostgreSQL 的 `oid` 类型是大型对象（Large Object）的引用，数据存储在系统表中
- `bytea` 类型直接将二进制数据存储在表中，更适合现代应用
- 实体类 `ProcessVariable.java` 中已定义为 `bytea` 类型

**修复脚本**: `backend/workflow-engine-core/src/main/resources/db/migration/V2__fix_binary_value_type.sql`

**修复逻辑**:
1. 检查列是否存在且类型为 `oid`
2. 如果存在，删除列并重新创建为 `bytea` 类型
3. 如果列不存在，创建为 `bytea` 类型
4. 如果列已为 `bytea`，跳过修复

**影响**: 
- 如果表中已有使用 `oid` 存储的二进制数据，修复后会丢失（因为 `oid` 引用无法直接转换为 `bytea`）
- 当前数据库中该列没有数据，所以修复是安全的

### 2. 确保 `sys_login_audit` 表存在

**问题**: `platform-security` 模块扫描实体时，需要确保 `sys_login_audit` 表存在。

**原因**:
- `sys_login_audit` 表在 `platform-security` 的 `V1__init_schema.sql` 中已定义
- 但为了确保在所有环境中都存在，需要额外的验证和修复脚本

**修复脚本**: `backend/platform-security/src/main/resources/db/migration/V3__ensure_sys_login_audit.sql`

**修复逻辑**:
1. 检查表是否存在
2. 如果不存在，创建表及所有必需的列和索引
3. 如果存在，验证结构并添加缺失的列和索引

**表结构**:
```sql
CREATE TABLE sys_login_audit (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id VARCHAR(64),
    username VARCHAR(50) NOT NULL,
    action VARCHAR(20) NOT NULL,
    ip_address VARCHAR(45),
    user_agent TEXT,
    success BOOLEAN DEFAULT true,
    failure_reason VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**索引**:
- `idx_login_audit_user` - 用户ID索引
- `idx_login_audit_username` - 用户名索引
- `idx_login_audit_created` - 创建时间索引
- `idx_login_audit_action` - 操作类型索引

## 迁移脚本执行顺序

1. `platform-security/V1__init_schema.sql` - 创建基础表结构（包括 `sys_login_audit`）
2. `platform-security/V2__fix_user_status_constraint.sql` - 修复用户状态约束
3. `platform-security/V2__init_data.sql` - 初始化数据
4. `platform-security/V3__ensure_sys_login_audit.sql` - 确保 `sys_login_audit` 表存在
5. `workflow-engine-core/V1__init_schema.sql` - 创建工作流引擎表（包括 `wf_process_variables`）
6. `workflow-engine-core/V2__fix_binary_value_type.sql` - 修复 `binary_value` 列类型

## 验证

执行修复后，可以通过以下 SQL 验证：

```sql
-- 验证 binary_value 列类型
SELECT column_name, data_type, udt_name 
FROM information_schema.columns 
WHERE table_schema = 'public' 
AND table_name = 'wf_process_variables' 
AND column_name = 'binary_value';
-- 应该返回: binary_value | bytea | bytea

-- 验证 sys_login_audit 表存在
SELECT EXISTS (
    SELECT 1 
    FROM information_schema.tables 
    WHERE table_schema = 'public' 
    AND table_name = 'sys_login_audit'
) as table_exists;
-- 应该返回: true
```

## 注意事项

1. **数据丢失风险**: 如果 `wf_process_variables.binary_value` 列中有使用 `oid` 存储的数据，修复后会丢失。当前数据库中没有数据，所以是安全的。

2. **幂等性**: 两个修复脚本都是幂等的，可以安全地多次执行。

3. **兼容性**: 修复后的 `bytea` 类型与 JPA 实体类定义一致，确保 ORM 映射正常工作。

4. **性能**: `bytea` 类型比 `oid` 更适合小到中等大小的二进制数据，查询性能更好。

## 相关文件

- `backend/workflow-engine-core/src/main/java/com/workflow/entity/ProcessVariable.java` - 实体类定义
- `backend/platform-security/src/main/java/com/platform/security/model/LoginAudit.java` - 登录审计实体类
- `backend/workflow-engine-core/src/main/resources/db/migration/V1__init_schema.sql` - 原始表定义
- `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` - 原始表定义
