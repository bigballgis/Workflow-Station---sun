# 自动禁用旧版本功能 ✅

## 功能概述

在部署新版本功能单元时，系统现在会自动禁用同一 code 的旧版本，避免唯一约束冲突。

## 功能特性

### 1. 自动版本管理

当导入新版本的功能单元时：
- **默认行为**: 自动禁用同一 code 的所有其他版本，新版本启用
- **可选行为**: 可以选择以禁用状态导入新版本，保持旧版本启用

### 2. 新增参数

在 `FunctionUnitImportRequest` 中新增 `enableOnImport` 参数：

```java
/**
 * 导入后是否启用（默认为 true）
 * 如果为 true，将自动禁用同一 code 的其他版本
 * 如果为 false，新版本将以禁用状态导入
 */
@Builder.Default
private Boolean enableOnImport = true;
```

## 使用场景

### 场景 1: 正常版本升级（默认）

**需求**: 部署新版本并立即启用，禁用旧版本

**操作**:
```json
{
  "code": "DIGITAL_LENDING_V2_EN",
  "version": "1.0.2",
  "fileContent": "...",
  "enableOnImport": true  // 或省略此字段（默认为 true）
}
```

**结果**:
- 1.0.1 版本自动禁用 (enabled = false)
- 1.0.2 版本创建并启用 (enabled = true)
- 用户立即看到新版本

### 场景 2: 灰度发布/测试部署

**需求**: 先部署新版本但不启用，保持旧版本运行，测试后再手动切换

**操作**:
```json
{
  "code": "DIGITAL_LENDING_V2_EN",
  "version": "1.0.2",
  "fileContent": "...",
  "enableOnImport": false
}
```

**结果**:
- 1.0.1 版本保持启用 (enabled = true)
- 1.0.2 版本创建但禁用 (enabled = false)
- 用户继续使用旧版本
- 管理员可以在 Admin Center 中测试新版本
- 测试通过后，手动启用 1.0.2，系统自动禁用 1.0.1

### 场景 3: 回滚到旧版本

**需求**: 新版本有问题，需要回滚到旧版本

**操作**:
1. 在 Admin Center 中找到旧版本（如 1.0.1）
2. 点击"启用"按钮
3. 系统自动禁用当前版本（1.0.2）

## 技术实现

### 修改的文件

1. **FunctionUnitImportRequest.java**
   - 新增 `enableOnImport` 字段

2. **FunctionUnitManagerComponent.java**
   - 修改 `importFunctionPackage` 方法
   - 修改 `createFunctionUnit` 方法
   - 在创建新版本前调用 `disableOtherVersions`

### 核心逻辑

```java
// 5. 如果启用新版本，自动禁用同一 code 的其他版本
boolean enableNewVersion = request.getEnableOnImport() != null ? request.getEnableOnImport() : true;
if (enableNewVersion) {
    List<String> disabledVersions = disableOtherVersions(packageContent.getCode(), null, importerId);
    if (!disabledVersions.isEmpty()) {
        log.info("Auto-disabled {} version(s) of {}: {}", 
                disabledVersions.size(), packageContent.getCode(), disabledVersions);
    }
}

// 6. 创建功能单元（带启用状态）
FunctionUnit functionUnit = createFunctionUnit(packageContent, request, importerId, enableNewVersion);
```

### 数据库约束

系统依赖以下唯一约束确保同一 code 只有一个启用版本：

```sql
CREATE UNIQUE INDEX idx_function_unit_code_enabled 
ON sys_function_units (code) 
WHERE enabled = true;
```

## API 使用示例

### 示例 1: 使用 PowerShell 部署（启用新版本）

```powershell
$body = @{
    code = "DIGITAL_LENDING_V2_EN"
    version = "1.0.2"
    fileContent = [Convert]::ToBase64String([IO.File]::ReadAllBytes("package.zip"))
    fileName = "digital-lending-v2-en-1.0.2.zip"
    enableOnImport = $true
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8083/api/v1/admin/function-units/import" `
    -Method POST `
    -Body $body `
    -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $token" }
```

### 示例 2: 使用 PowerShell 部署（禁用状态）

```powershell
$body = @{
    code = "DIGITAL_LENDING_V2_EN"
    version = "1.0.2"
    fileContent = [Convert]::ToBase64String([IO.File]::ReadAllBytes("package.zip"))
    fileName = "digital-lending-v2-en-1.0.2.zip"
    enableOnImport = $false  # 以禁用状态导入
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8083/api/v1/admin/function-units/import" `
    -Method POST `
    -Body $body `
    -ContentType "application/json" `
    -Headers @{ Authorization = "Bearer $token" }
```

### 示例 3: 使用 curl 部署

```bash
curl -X POST http://localhost:8083/api/v1/admin/function-units/import \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "code": "DIGITAL_LENDING_V2_EN",
    "version": "1.0.2",
    "fileContent": "...",
    "fileName": "digital-lending-v2-en-1.0.2.zip",
    "enableOnImport": true
  }'
```

## 日志输出

### 成功禁用旧版本

```
INFO  c.a.c.FunctionUnitManagerComponent - Auto-disabled 1 version(s) of DIGITAL_LENDING_V2_EN: [1.0.1]
INFO  c.a.c.FunctionUnitManagerComponent - Function package imported successfully: 4737ac68-42c5-4571-972e-e7ad0c6c7253
```

### 以禁用状态导入

```
INFO  c.a.c.FunctionUnitManagerComponent - Importing function package with enabled=false
INFO  c.a.c.FunctionUnitManagerComponent - Function package imported successfully: 5848bd79-53d6-4682-a83f-f8bc8d7d8d64
```

## 验证方法

### 1. 查询所有版本

```sql
SELECT id, code, version, enabled, created_at 
FROM sys_function_units 
WHERE code = 'DIGITAL_LENDING_V2_EN' 
ORDER BY created_at DESC;
```

预期结果（启用新版本）:
```
                  id                  |         code          | version | enabled |          created_at
--------------------------------------+-----------------------+---------+---------+----------------------------
 5848bd79-53d6-4682-a83f-f8bc8d7d8d64 | DIGITAL_LENDING_V2_EN | 1.0.2   | t       | 2026-02-08 08:30:00
 4737ac68-42c5-4571-972e-e7ad0c6c7253 | DIGITAL_LENDING_V2_EN | 1.0.1   | f       | 2026-02-08 08:08:39
```

### 2. 验证唯一约束

尝试同时启用两个版本应该失败：

```sql
-- 这个操作会失败
UPDATE sys_function_units 
SET enabled = true 
WHERE code = 'DIGITAL_LENDING_V2_EN' AND version = '1.0.1';
```

错误信息:
```
ERROR: duplicate key value violates unique constraint "idx_function_unit_code_enabled"
Detail: Key (code)=(DIGITAL_LENDING_V2_EN) already exists.
```

## 向后兼容性

- **默认行为**: `enableOnImport` 默认为 `true`，保持现有行为
- **旧代码**: 不传递 `enableOnImport` 参数的请求仍然正常工作
- **数据库**: 不需要数据库迁移，使用现有的 `enabled` 字段

## 最佳实践

### 1. 生产环境部署

```
1. 在测试环境验证新版本
2. 使用 enableOnImport=false 部署到生产环境
3. 在 Admin Center 中测试新版本
4. 确认无误后，手动启用新版本
5. 监控系统运行状态
6. 如有问题，快速回滚到旧版本
```

### 2. 开发环境部署

```
1. 使用 enableOnImport=true（默认）
2. 直接部署并启用新版本
3. 快速迭代测试
```

### 3. 版本命名规范

遵循语义化版本规范：
- **主版本号**: 不兼容的 API 修改
- **次版本号**: 向下兼容的功能性新增
- **修订号**: 向下兼容的问题修正

示例: `1.0.0` → `1.0.1` (bug fix) → `1.1.0` (new feature) → `2.0.0` (breaking change)

## 故障排除

### 问题 1: 部署失败 - 唯一约束冲突

**错误信息**:
```
duplicate key value violates unique constraint "idx_function_unit_code_enabled"
```

**原因**: 尝试启用新版本，但旧版本仍然启用

**解决方案**:
1. 检查 `enableOnImport` 参数是否正确设置
2. 手动禁用旧版本:
   ```sql
   UPDATE sys_function_units 
   SET enabled = false 
   WHERE code = 'DIGITAL_LENDING_V2_EN' AND version = '1.0.1';
   ```
3. 重新部署

### 问题 2: 新版本部署后用户看不到

**原因**: `enableOnImport` 设置为 `false`

**解决方案**:
1. 在 Admin Center 中找到新版本
2. 点击"启用"按钮
3. 或使用 API:
   ```bash
   curl -X PUT http://localhost:8083/api/v1/admin/function-units/{id}/enable \
     -H "Authorization: Bearer $TOKEN"
   ```

### 问题 3: 需要同时运行多个版本

**说明**: 系统设计不支持同一 code 的多个版本同时启用

**替代方案**:
1. 使用不同的 code（如 `DIGITAL_LENDING_V2_EN` 和 `DIGITAL_LENDING_V3_EN`）
2. 或在不同的环境中部署不同版本

## 相关文档

- 功能单元版本管理: `docs/FUNCTION_UNIT_VERSION_MANAGEMENT_IMPLEMENTATION.md`
- 部署指南: `docs/DEPLOYMENT_SUCCESS_SUMMARY.md`
- API 文档: Admin Center Swagger UI

## 更新历史

- **2026-02-08**: 实现自动禁用旧版本功能
- **2026-02-08**: 添加 `enableOnImport` 参数支持灰度发布

