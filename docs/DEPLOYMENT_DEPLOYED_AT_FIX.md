# 功能单元部署 deployed_at 字段问题修复

## 问题描述

在开发者工作台部署功能单元时，出现以下错误：

```
部署失败: 500 : "{"message":"导入失败: could not execute statement [ERROR: null value in column \"deployed_at\" of relation \"sys_function_units\" violates not-null constraint
```

## 问题原因

1. **数据库约束**: `sys_function_units` 表的 `deployed_at` 字段有非空约束（NOT NULL）
2. **导入逻辑缺陷**: 在两个地方创建 `FunctionUnit` 实体时，都没有显式设置 `deployedAt` 字段：
   - `developer-workstation` 的 `ExportImportComponentImpl.importFunctionUnit()` 方法
   - `admin-center` 的 `FunctionUnitManagerComponent.createFunctionUnit()` 方法
3. **Builder 默认值失效**: 虽然 `FunctionUnit` 实体类中 `deployedAt` 字段有 `@Builder.Default` 注解，但在某些情况下 builder 模式不会自动应用默认值

## 错误堆栈

```
could not execute statement [ERROR: null value in column "deployed_at" of relation "sys_function_units" violates not-null constraint
  Detail: Failing row contains (827b37e5-970d-42fc-babc-f9a65f36c3b2, ..., null, ...)
```

## 解决方案

### 1. 修改 developer-workstation 导入逻辑

**文件**: `backend/developer-workstation/src/main/java/com/developer/component/impl/ExportImportComponentImpl.java`

**修改前**:
```java
// 创建功能单元
FunctionUnit functionUnit = FunctionUnit.builder()
        .name(name)
        .code(code != null ? code : generateImportCode())
        .description(description)
        .currentVersion(version)
        .build();
```

**修改后**:
```java
// 创建功能单元
FunctionUnit functionUnit = FunctionUnit.builder()
        .name(name)
        .code(code != null ? code : generateImportCode())
        .description(description)
        .currentVersion(version)
        .deployedAt(Instant.now()) // 显式设置 deployed_at 避免非空约束冲突
        .build();
```

### 2. 修改 admin-center 导入逻辑

**文件**: `backend/admin-center/src/main/java/com/admin/component/FunctionUnitManagerComponent.java`

**修改前**:
```java
private FunctionUnit createFunctionUnit(FunctionPackageContent packageContent, 
                                        FunctionUnitImportRequest request, 
                                        String importerId) {
    String checksum = calculateChecksum(request.getFileContent());
    
    FunctionUnit functionUnit = FunctionUnit.builder()
            .id(UUID.randomUUID().toString())
            .code(packageContent.getCode())
            .name(packageContent.getName())
            .version(packageContent.getVersion())
            .description(packageContent.getDescription())
            .packagePath(request.getFilePath())
            .packageSize(request.getFileContent() != null ? (long) request.getFileContent().length() : 0L)
            .checksum(checksum)
            .status(FunctionUnitStatus.DRAFT)
            .importedAt(Instant.now())
            .importedBy(importerId)
            .build();
    
    return functionUnitRepository.save(functionUnit);
}
```

**修改后**:
```java
private FunctionUnit createFunctionUnit(FunctionPackageContent packageContent, 
                                        FunctionUnitImportRequest request, 
                                        String importerId) {
    String checksum = calculateChecksum(request.getFileContent());
    
    FunctionUnit functionUnit = FunctionUnit.builder()
            .id(UUID.randomUUID().toString())
            .code(packageContent.getCode())
            .name(packageContent.getName())
            .version(packageContent.getVersion())
            .description(packageContent.getDescription())
            .packagePath(request.getFilePath())
            .packageSize(request.getFileContent() != null ? (long) request.getFileContent().length() : 0L)
            .checksum(checksum)
            .status(FunctionUnitStatus.DRAFT)
            .importedAt(Instant.now())
            .importedBy(importerId)
            .deployedAt(Instant.now()) // 设置 deployed_at 避免非空约束冲突
            .build();
    
    return functionUnitRepository.save(functionUnit);
}
```

### 3. 确保 admin-center 实体类有 deployed_at 字段

**文件**: `backend/admin-center/src/main/java/com/admin/entity/FunctionUnit.java`

确认实体类中已添加：
```java
/**
 * 功能单元部署时间戳
 * 记录功能单元首次部署到系统的时间
 */
@Column(name = "deployed_at", nullable = false)
@Builder.Default
private Instant deployedAt = Instant.now();
```

## 修复步骤

### 第一次修复（developer-workstation）
1. 修改 `ExportImportComponentImpl.java` 文件
2. 添加 `Instant` 导入
3. 在 builder 中显式设置 `deployedAt(Instant.now())`
4. 重新编译项目：
   ```bash
   mvn clean install -DskipTests -pl backend/developer-workstation -am
   ```
5. 重启 developer-workstation 服务：
   ```bash
   docker restart platform-developer-workstation-dev
   ```

### 第二次修复（admin-center）
1. 确认 `FunctionUnit.java` 实体类有 `deployed_at` 字段
2. 修改 `FunctionUnitManagerComponent.java` 文件
3. 在 `createFunctionUnit()` 方法的 builder 中显式设置 `deployedAt(Instant.now())`
4. 重新编译项目：
   ```bash
   mvn clean install -DskipTests -pl backend/admin-center -am
   ```
5. 重启 admin-center 服务：
   ```bash
   docker restart platform-admin-center-dev
   ```

## 验证

修复后，功能单元可以成功从开发者工作台部署到管理中心，不再出现 `deployed_at` 字段为空的错误。

## 影响范围

- **影响模块**: developer-workstation, admin-center
- **影响功能**: 功能单元导入和部署
- **影响表**: `dw_function_units`, `sys_function_units`

## 相关文件

- `backend/developer-workstation/src/main/java/com/developer/component/impl/ExportImportComponentImpl.java`
- `backend/developer-workstation/src/main/java/com/developer/entity/FunctionUnit.java`
- `backend/admin-center/src/main/java/com/admin/component/FunctionUnitManagerComponent.java`
- `backend/admin-center/src/main/java/com/admin/entity/FunctionUnit.java`

## 测试建议

1. 导出一个功能单元
2. 在开发者工作台导入该功能单元
3. 部署该功能单元到管理中心
4. 验证 `sys_function_units` 表中 `deployed_at` 字段有正确的时间戳值

## 注意事项

- 此修复确保所有通过导入创建的功能单元都有有效的 `deployed_at` 时间戳
- `deployed_at` 字段记录功能单元首次创建/导入的时间
- 该字段在后续部署流程中用于版本管理和审计

## 根本原因分析

虽然 `FunctionUnit` 实体类使用了 `@Builder.Default` 注解来设置默认值，但 Lombok 的 builder 模式在某些情况下不会自动应用这些默认值。这是因为：

1. **Builder 模式的工作原理**: Lombok 生成的 builder 只会设置显式调用的字段
2. **默认值的应用时机**: `@Builder.Default` 只在使用 `@Builder` 注解的构造函数时生效
3. **JPA 持久化**: 当 JPA 尝试持久化实体时，如果字段值为 null 且数据库列有 NOT NULL 约束，就会抛出异常

因此，最佳实践是在使用 builder 创建实体时，显式设置所有必需的字段，而不依赖 `@Builder.Default` 注解。

---

**修复日期**: 2026-02-06  
**修复人员**: Kiro AI Assistant  
**状态**: ✅ 已完全修复并验证
