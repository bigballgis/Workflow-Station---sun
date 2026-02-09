# 虚拟组类型修复

**日期**: 2026-02-02  
**问题**: 系统默认的5个虚拟组显示为 `Custom` 类型，应该显示为 `System` 类型

## 问题描述

在管理中心的虚拟组列表中，5个系统默认虚拟组显示为 `Custom` 类型：
- SYSTEM_ADMINISTRATORS (系统管理员组)
- AUDITORS (审计员组)
- MANAGERS (部门经理组)
- DEVELOPERS (工作流开发者组)
- DESIGNERS (工作流设计师组)

这些组应该标记为 `SYSTEM` 类型，表示它们是系统默认的、不可编辑的虚拟组。

## 根本原因

初始化 SQL 脚本在创建虚拟组时没有指定 `type` 字段，导致使用了表的默认值 `CUSTOM`。

## 修复方案

### 1. 更新 SQL 初始化脚本

修改文件: `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql`

**修改前**:
```sql
INSERT INTO sys_virtual_groups (id, code, name, description, status, created_at, updated_at)
VALUES 
('vg-sys-admins', 'SYSTEM_ADMINISTRATORS', '系统管理员组', 'Virtual group for system administrators with full system access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
```

**修改后**:
```sql
INSERT INTO sys_virtual_groups (id, code, name, type, description, status, created_at, updated_at)
VALUES 
('vg-sys-admins', 'SYSTEM_ADMINISTRATORS', '系统管理员组', 'SYSTEM', 'Virtual group for system administrators with full system access', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
```

所有5个虚拟组的 INSERT 语句都添加了 `type = 'SYSTEM'`。

同时更新了 `ON CONFLICT` 子句，确保在重新运行脚本时也会更新 `type` 字段：
```sql
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    type = EXCLUDED.type,  -- 新增
    description = EXCLUDED.description,
    updated_at = CURRENT_TIMESTAMP;
```

### 2. 更新现有数据库数据

执行 SQL 更新语句：
```sql
UPDATE sys_virtual_groups 
SET type = 'SYSTEM', 
    updated_at = CURRENT_TIMESTAMP 
WHERE code IN (
    'SYSTEM_ADMINISTRATORS', 
    'AUDITORS', 
    'MANAGERS', 
    'DEVELOPERS', 
    'DESIGNERS'
);
```

**执行结果**:
```
UPDATE 5

         code          |      name      |  type
-----------------------+----------------+--------
 AUDITORS              | 审计员组       | SYSTEM
 DESIGNERS             | 工作流设计师组 | SYSTEM
 DEVELOPERS            | 工作流开发者组 | SYSTEM
 MANAGERS              | 部门经理组     | SYSTEM
 SYSTEM_ADMINISTRATORS | 系统管理员组   | SYSTEM
```

### 3. 重启服务

重启 Admin Center 服务以清除缓存：
```bash
docker restart platform-admin-center-dev
```

## 验证结果

### 数据库验证
```sql
SELECT code, name, type 
FROM sys_virtual_groups 
WHERE code IN ('SYSTEM_ADMINISTRATORS', 'AUDITORS', 'MANAGERS', 'DEVELOPERS', 'DESIGNERS')
ORDER BY code;
```

所有5个虚拟组的 `type` 字段都已更新为 `SYSTEM`。

### 前端验证

在管理中心的虚拟组列表页面，这5个虚拟组现在应该显示为 `System` 类型，而不是 `Custom` 类型。

作为系统类型的虚拟组，它们应该：
- 不能被删除
- 不能修改类型
- 可能有其他编辑限制（取决于前端实现）

## 影响范围

### 修改的文件
1. `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql` - 初始化脚本
2. `docs/DATABASE_INITIALIZATION_COMPLETE.md` - 文档更新

### 数据库更改
- 更新了 `sys_virtual_groups` 表中5条记录的 `type` 字段

### 服务重启
- `platform-admin-center-dev` - 已重启

## 后续建议

### 1. 前端验证
建议在前端代码中添加对 `SYSTEM` 类型虚拟组的特殊处理：
- 禁用删除按钮
- 禁用类型修改
- 添加视觉标识（如图标或标签）

### 2. 后端验证
建议在后端 API 中添加保护逻辑：
```java
// 示例：防止删除系统虚拟组
if ("SYSTEM".equals(virtualGroup.getType())) {
    throw new BusinessException("Cannot delete system virtual group");
}
```

### 3. 数据库约束
可以考虑添加数据库触发器或约束，防止意外修改系统虚拟组：
```sql
CREATE OR REPLACE FUNCTION prevent_system_group_deletion()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.type = 'SYSTEM' THEN
        RAISE EXCEPTION 'Cannot delete system virtual group: %', OLD.code;
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_system_group_deletion
BEFORE DELETE ON sys_virtual_groups
FOR EACH ROW
EXECUTE FUNCTION prevent_system_group_deletion();
```

## 总结

✅ SQL 初始化脚本已更新，包含 `type = 'SYSTEM'`  
✅ 数据库中5个虚拟组的类型已更新为 `SYSTEM`  
✅ Admin Center 服务已重启  
✅ 文档已更新

现在这5个系统默认虚拟组正确地标记为 `SYSTEM` 类型，在前端界面中应该显示为不可编辑的系统组。
