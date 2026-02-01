# 虚拟组 type 字段修复总结

生成时间: 2026-01-18

## 问题描述

1. **默认值不一致**：
   - Flyway 脚本 `V1__init_schema.sql` 定义：`type VARCHAR(50) DEFAULT 'CUSTOM'`
   - 数据库实际默认值：`'STATIC'::character varying`

2. **缺少 CHECK 约束**：
   - Flyway 脚本中定义了约束：`CONSTRAINT chk_virtual_group_type CHECK (type IN ('SYSTEM', 'CUSTOM'))`
   - 但数据库中实际没有这个约束

3. **枚举类型不匹配**：
   - Java 枚举 `VirtualGroupType` 只支持 `SYSTEM` 和 `CUSTOM`
   - 数据库中曾经存在 `STATIC` 类型的记录，导致 Hibernate 映射失败

## 修复内容

### 1. 修复默认值

**脚本**: `deploy/scripts/fix-virtual-group-type-default.sql`

```sql
ALTER TABLE sys_virtual_groups 
ALTER COLUMN type SET DEFAULT 'CUSTOM';
```

**结果**: ✅ 默认值已从 `'STATIC'` 更新为 `'CUSTOM'`

### 2. 更新现有数据

**脚本**: `deploy/scripts/fix-virtual-group-static-type.sql`

```sql
UPDATE sys_virtual_groups 
SET type = 'SYSTEM' 
WHERE type = 'STATIC';
```

**结果**: ✅ 5 条 `STATIC` 类型的记录已更新为 `SYSTEM`

### 3. 添加 CHECK 约束

**脚本**: `deploy/scripts/add-virtual-group-type-constraint.sql`

```sql
ALTER TABLE sys_virtual_groups 
ADD CONSTRAINT chk_virtual_group_type 
CHECK (type IN ('SYSTEM', 'CUSTOM'));
```

**结果**: ✅ 约束已成功添加

## 修复后的状态

### 数据库结构

| 字段 | 类型 | 默认值 | 约束 |
|------|------|--------|------|
| `type` | VARCHAR(50) | `'CUSTOM'` | `CHECK (type IN ('SYSTEM', 'CUSTOM'))` |

### 当前数据

所有虚拟组的 `type` 字段现在都是 `SYSTEM` 或 `CUSTOM`：

| ID | Name | Type | Status |
|----|------|------|--------|
| `vg-all-managers` | All Managers | SYSTEM | ACTIVE |
| `vg-all-developers` | All Developers | SYSTEM | ACTIVE |
| `vg-team-leads` | Team Leaders | SYSTEM | ACTIVE |
| `vg-senior-devs` | Senior Developers | SYSTEM | ACTIVE |
| `vg-approvers` | Approvers | SYSTEM | ACTIVE |

### 约束验证

- ✅ CHECK 约束已生效，阻止插入无效的 `type` 值
- ✅ 默认值与 Flyway 脚本一致
- ✅ 枚举类型与数据库约束一致

## 相关文件

1. **修复脚本**:
   - `deploy/scripts/fix-virtual-group-type-default.sql` - 修复默认值
   - `deploy/scripts/fix-virtual-group-static-type.sql` - 更新现有数据
   - `deploy/scripts/add-virtual-group-type-constraint.sql` - 添加约束

2. **Flyway 脚本**:
   - `backend/platform-security/src/main/resources/db/migration/V1__init_schema.sql` - 表结构定义

3. **Java 代码**:
   - `backend/admin-center/src/main/java/com/admin/enums/VirtualGroupType.java` - 枚举定义
   - `backend/admin-center/src/main/java/com/admin/entity/VirtualGroup.java` - 实体定义

## 注意事项

1. **CREATE TABLE IF NOT EXISTS 的限制**:
   - 如果表已存在，`CREATE TABLE IF NOT EXISTS` 不会更新表结构
   - 需要手动执行 ALTER TABLE 来修复不一致

2. **约束的重要性**:
   - CHECK 约束确保数据库层面的数据完整性
   - 即使应用层有验证，数据库约束提供额外的保护

3. **未来维护**:
   - 如果需要在枚举中添加新类型，需要同时更新：
     - Java 枚举 `VirtualGroupType`
     - Flyway 脚本中的 CHECK 约束
     - 数据库中的 CHECK 约束

## 验证命令

```sql
-- 检查默认值
SELECT column_name, column_default 
FROM information_schema.columns 
WHERE table_name = 'sys_virtual_groups' AND column_name = 'type';

-- 检查约束
SELECT conname, pg_get_constraintdef(oid) 
FROM pg_constraint 
WHERE conrelid = 'sys_virtual_groups'::regclass AND contype = 'c';

-- 检查数据
SELECT DISTINCT type FROM sys_virtual_groups ORDER BY type;
```
