# 表单弹窗 API 路由修复

## 问题描述

在实现表单弹窗功能时，前端调用 Admin Center 的 API 获取表单配置时失败：

```
Failed to load form: TypeError: we.getFunctionUnitContents is not a function
```

后续修复后，API 调用使用了硬编码的 localhost URL：
```typescript
http://localhost:8083/api/v1/admin/function-units/${functionUnitId}/contents
```

这种方式在生产环境中无法工作，因为：
1. 硬编码的 localhost 只在开发环境有效
2. 绕过了 API Gateway 的路由机制
3. 无法利用统一的认证和错误处理

第二次尝试修复后，发现 Admin Center 根本没有 `/contents` 端点（复数形式），导致 500 错误。

## 解决方案

### 1. Admin Center 添加新端点

在 Admin Center 的 FunctionUnitController 中添加 `/contents` 端点：

**FunctionUnitController.java** - 添加新端点：
```java
@GetMapping("/{id}/contents")
@Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
public ResponseEntity<java.util.List<java.util.Map<String, Object>>> getFunctionUnitContents(
        @Parameter(description = "功能单元ID") @PathVariable String id,
        @Parameter(description = "内容类型") @RequestParam String contentType) {
    log.info("Getting function unit contents for: {}, contentType: {}", id, contentType);
    
    java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
    
    try {
        // 获取所有内容
        java.util.List<com.admin.entity.FunctionUnitContent> contents = 
                functionUnitManager.getFunctionUnitContents(id);
        
        // 解析请求的内容类型
        com.admin.enums.ContentType requestedType;
        try {
            requestedType = com.admin.enums.ContentType.valueOf(contentType.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.error("Invalid content type: {}", contentType);
            return ResponseEntity.badRequest().build();
        }
        
        // 过滤指定类型的内容
        for (com.admin.entity.FunctionUnitContent content : contents) {
            if (content.getContentType() == requestedType) {
                java.util.Map<String, Object> contentMap = new java.util.HashMap<>();
                contentMap.put("id", content.getId());
                contentMap.put("contentType", content.getContentType().name());
                contentMap.put("contentName", content.getContentName());
                contentMap.put("contentData", content.getContentData());
                contentMap.put("sourceId", content.getSourceId());
                result.add(contentMap);
            }
        }
        
        log.info("Found {} contents of type {}", result.size(), contentType);
        return ResponseEntity.ok(result);
        
    } catch (Exception e) {
        log.error("Failed to get function unit contents for {}: {}", id, e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
    }
}
```

### 2. User Portal 后端添加代理端点

在 User Portal 后端添加新的 API 端点，作为 Admin Center API 的代理：

**ProcessComponent.java** - 添加新方法：
```java
/**
 * 获取功能单元特定类型的内容（用于表单弹窗等场景）
 */
public List<Map<String, Object>> getFunctionUnitContents(String functionUnitIdOrCode, String contentType) {
    log.info("Getting function unit contents for: {}, contentType: {}", functionUnitIdOrCode, contentType);
    
    try {
        // 先解析功能单元 ID（支持 code 或名称）
        String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
        log.info("Resolved function unit ID: {}", functionUnitId);
        
        RestTemplate restTemplate = new RestTemplate();
        String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/contents?contentType=" + contentType;
        log.info("Fetching function unit contents from: {}", url);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> response = restTemplate.getForObject(url, List.class);
        
        if (response != null) {
            log.info("Got {} contents of type {}", response.size(), contentType);
            return response;
        }
        
        return Collections.emptyList();
        
    } catch (Exception e) {
        log.error("Failed to get function unit contents for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
        return Collections.emptyList();
    }
}
```

**ProcessController.java** - 添加新端点：
```java
@GetMapping("/function-units/{functionUnitId}/contents")
@Operation(summary = "获取功能单元特定类型的内容", description = "获取功能单元的特定类型内容（如表单、流程等），用于表单弹窗等场景")
public ApiResponse<List<Map<String, Object>>> getFunctionUnitContents(
        @RequestHeader(value = "X-User-Id", required = false) String userId,
        @PathVariable String functionUnitId,
        @RequestParam String contentType) {
    List<Map<String, Object>> contents = processComponent.getFunctionUnitContents(functionUnitId, contentType);
    return ApiResponse.success(contents);
}
```

### 3. 前端使用相对路径

修改前端 API 调用，使用相对路径通过 User Portal 后端代理：

**process.ts** - 修改 API 调用：
```typescript
// 获取功能单元特定类型的内容
getFunctionUnitContents(functionUnitId: string, contentType: string) {
  return request.get<Array<{
    id: string
    contentType: string
    contentName: string
    contentData: string
    sourceId?: string
  }>>(`/processes/function-units/${functionUnitId}/contents`, {
    params: { contentType }
  })
}
```

## 架构优势

这种代理模式的优势：

1. **统一路由**：所有 API 调用都通过 User Portal 后端，便于管理和监控
2. **环境无关**：使用相对路径，在任何环境（开发、测试、生产）都能正常工作
3. **统一认证**：利用 User Portal 的认证机制，无需在前端处理跨服务认证
4. **错误处理**：统一的错误处理和日志记录
5. **解耦合**：前端不需要知道 Admin Center 的具体地址

## API 调用流程

```
前端 (User Portal Frontend)
  ↓ GET /api/portal/processes/function-units/{id}/contents?contentType=FORM
User Portal Backend
  ↓ GET http://localhost:8090/api/v1/admin/function-units/{id}/contents?contentType=FORM
Admin Center Backend
  ↓ 返回表单配置列表
User Portal Backend
  ↓ 返回给前端
前端解析并显示表单弹窗
```

## 部署步骤

1. 编译 Admin Center 后端：
```bash
mvn clean package -DskipTests -pl backend/admin-center -am
```

2. 部署 Admin Center 到 Docker 容器：
```bash
docker cp backend/admin-center/target/admin-center-1.0.0.jar platform-admin-center-dev:/app/admin-center.jar
docker restart platform-admin-center-dev
```

3. 编译 User Portal 后端：
```bash
mvn clean package -DskipTests -pl backend/user-portal -am
```

4. 部署 User Portal 到 Docker 容器：
```bash
docker cp backend/user-portal/target/user-portal-1.0.0-SNAPSHOT.jar platform-user-portal-dev:/app/user-portal.jar
docker restart platform-user-portal-dev
```

5. 构建前端：
```bash
cd frontend/user-portal
npx vite build
```

6. 部署前端到 Docker 容器：
```bash
docker cp frontend/user-portal/dist/. platform-user-portal-frontend-dev:/usr/share/nginx/html/
```

## 测试验证

1. 访问 User Portal：http://localhost:3001
2. 登录并进入任务详情页
3. 点击 "Perform Credit Check" 等 FORM_POPUP 类型的按钮
4. 验证表单弹窗正确打开并显示表单字段

## 相关文件

**Admin Center:**
- `backend/admin-center/src/main/java/com/admin/controller/FunctionUnitController.java`

**User Portal Backend:**
- `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`
- `backend/user-portal/src/main/java/com/portal/controller/ProcessController.java`

**User Portal Frontend:**
- `frontend/user-portal/src/api/process.ts`
- `frontend/user-portal/src/views/tasks/detail.vue`

## 日期

2026-02-08

