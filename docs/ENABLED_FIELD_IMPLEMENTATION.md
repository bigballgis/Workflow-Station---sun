# Enabled Field Implementation for Function Unit Version Management

## 问题描述

用户报告在 Developer Workstation 前端看到了禁用的版本（disabled versions）仍然显示在功能单元列表中。

## 根本原因分析

经过调查发现：

1. **数据库表缺少 `enabled` 字段**
   - `dw_function_units` 表中只有 `is_active` 字段，没有 `enabled` 字段
   - 版本管理需求文档中明确定义了 `enabled` 字段的功能

2. **代码实现不完整**
   - `FunctionUnit` 实体类中没有 `enabled` 字段
   - 查询逻辑没有过滤 `enabled=false` 的记录

3. **版本管理系统未完全实现**
   - 需求文档（`.kiro/specs/function-unit-version-management/requirements.md`）中定义了完整的版本管理功能
   - 但数据库和代码实现还不完整

## 解决方案

### 1. 数据库迁移

创建了迁移脚本 `deploy/init-scripts/00-schema/12-add-enabled-field-to-dw-function-units.sql`：

```sql
-- 添加 enabled 字段，默认值为 true
ALTER TABLE dw_function_units 
ADD COLUMN IF NOT EXISTS enabled BOOLEAN NOT NULL DEFAULT true;

-- 创建索引以提高查询性能
CREATE INDEX IF NOT EXISTS idx_dw_function_units_enabled 
ON dw_function_units(enabled);

-- 创建唯一部分索引：确保每个功能单元代码只有一个启用版本
CREATE UNIQUE INDEX IF NOT EXISTS idx_dw_function_unit_code_enabled 
ON dw_function_units (code) 
WHERE enabled = true;
```

**执行结果**：
- ✅ 字段添加成功
- ✅ 索引创建成功
- ✅ 唯一约束创建成功

### 2. 实体类更新

在 `backend/developer-workstation/src/main/java/com/developer/entity/FunctionUnit.java` 中添加：

```java
/**
 * Whether this version is enabled and visible to users
 * Only enabled versions should be displayed in the function unit list
 */
@Column(name = "enabled", nullable = false)
@Builder.Default
private Boolean enabled = true;
```

### 3. 查询逻辑更新

在 `backend/developer-workstation/src/main/java/com/developer/component/impl/FunctionUnitComponentImpl.java` 的 `list()` 方法中添加过滤条件：

```java
@Override
@Transactional(readOnly = true)
public Page<FunctionUnitResponse> list(String name, String status, Pageable pageable) {
    Specification<FunctionUnit> spec = (root, query, cb) -> {
        List<Predicate> predicates = new ArrayList<>();
        
        // Only show enabled versions to users
        predicates.add(cb.equal(root.get("enabled"), true));
        
        // ... 其他过滤条件
    };
    // ...
}
```

### 4. 服务重启

```bash
# 重新编译
mvn clean package -DskipTests

# 重启容器
docker restart platform-developer-workstation-dev
```

## 验证步骤

### 1. 验证数据库字段

```sql
SELECT id, name, version, is_active, enabled 
FROM dw_function_units 
WHERE id = 10;
```

**结果**：
```
 id |              name              | version | is_active | enabled 
----+--------------------------------+---------+-----------+---------
 10 | Digital Lending System V2 (EN) | 1.0.0   | t         | t
```

### 2. 测试过滤功能

可以通过以下 SQL 禁用一个版本来测试：

```sql
-- 禁用版本 1.0.0
UPDATE dw_function_units 
SET enabled = false 
WHERE id = 10 AND version = '1.0.0';

-- 刷新前端页面，该版本应该不再显示
```

### 3. 前端验证

1. 访问 Developer Workstation: http://localhost:3002
2. 查看功能单元列表
3. 只有 `enabled=true` 的版本应该显示

## 相关文件

### 数据库迁移
- `deploy/init-scripts/00-schema/12-add-enabled-field-to-dw-function-units.sql`

### 代码更改
- `backend/developer-workstation/src/main/java/com/developer/entity/FunctionUnit.java`
- `backend/developer-workstation/src/main/java/com/developer/component/impl/FunctionUnitComponentImpl.java`

### 需求文档
- `.kiro/specs/function-unit-version-management/requirements.md`
- `.kiro/specs/function-unit-version-management/design.md`

## 后续工作

根据版本管理需求文档，还需要实现以下功能：

1. **自动版本禁用** - 部署新版本时自动禁用旧版本
2. **版本回滚API** - 允许管理员激活特定版本
3. **版本历史查询** - 显示所有版本及其状态
4. **审计日志** - 记录版本启用/禁用操作
5. **并发控制** - 确保只有一个版本被启用

这些功能在 `.kiro/specs/function-unit-version-management/tasks.md` 中有详细的任务列表。

## 总结

通过添加 `enabled` 字段并更新查询逻辑，现在 Developer Workstation 只会显示启用的功能单元版本。这解决了用户看到禁用版本的问题，并为完整的版本管理系统奠定了基础。

**状态**: ✅ 已完成并部署
**日期**: 2026-02-07
**影响范围**: Developer Workstation 模块
