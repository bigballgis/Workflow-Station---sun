# 修复功能单元内容获取 500 错误

## 问题描述

访问 `http://localhost:3001/processes/start/fu-20260114-paed3z` 发起流程时，页面显示加载失败 500 错误：
```
{"code":"INTERNAL_ERROR","message":"系统内部错误","timestamp":"2026-01-14T09:21:18.007469Z","path":"/api/v1/admin/function-units/46ec97c3-fe7c-4e76-bcbc-99fe38f8fcb4/content","details":null}
```

## 问题原因

后端日志显示：
```
Caused by: java.lang.ClassNotFoundException: com.admin.controller.FunctionUnitController$1
	at com.admin.controller.FunctionUnitController.getFunctionUnitContent(FunctionUnitController.java:471)
```

**根本原因**：在 `FunctionUnitController.getFunctionUnitContent` 方法中，使用 `switch` 语句处理枚举类型 `ContentType` 时，Java 编译器生成了匿名内部类（`FunctionUnitController$1`），但在运行时找不到这个类，导致 `ClassNotFoundException`。

这通常发生在：
1. 代码修改后没有重新编译
2. 类加载器问题
3. 热加载/热部署问题

## 修复方案

将 `switch` 语句改为 `if-else` 语句，避免编译器生成匿名内部类：

```java
// 修改前（使用 switch）
switch (content.getContentType()) {
    case FORM:
        forms.add(contentMap);
        break;
    case PROCESS:
        processes.add(contentMap);
        break;
    case DATA_TABLE:
        dataTables.add(contentMap);
        break;
    default:
        break;
}

// 修改后（使用 if-else）
com.admin.enums.ContentType contentType = content.getContentType();
if (contentType == com.admin.enums.ContentType.FORM) {
    forms.add(contentMap);
} else if (contentType == com.admin.enums.ContentType.PROCESS) {
    processes.add(contentMap);
} else if (contentType == com.admin.enums.ContentType.DATA_TABLE) {
    dataTables.add(contentMap);
}
```

## 验证

修复后，应该能够：
1. 访问 `http://localhost:3001/processes/start/fu-20260114-paed3z`
2. 正常加载功能单元内容（流程定义、表单、数据表等）
3. 不再出现 500 错误

## 相关文件

- `/backend/admin-center/src/main/java/com/admin/controller/FunctionUnitController.java` - 功能单元控制器

## 注意事项

1. **重新编译**：修改代码后需要重新编译并重启服务
2. **清理编译缓存**：如果问题仍然存在，需要清理编译缓存：
   ```bash
   cd backend/admin-center
   mvn clean compile
   ```
3. **完全重启**：修改代码后需要完全停止并重启服务，不能只依赖热加载

## 验证结果

修复后，API 正常返回功能单元内容：
- 基本信息：id, name, code, version, description, status
- 流程定义：processes 数组包含 BPMN XML
- 表单定义：forms 数组包含表单配置 JSON
- 数据表定义：dataTables 数组（如果有）

现在可以正常访问 `http://localhost:3001/processes/start/fu-20260114-paed3z` 发起流程。
