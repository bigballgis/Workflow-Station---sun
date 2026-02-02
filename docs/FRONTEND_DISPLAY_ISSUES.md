# 前端显示问题

**日期**: 2026-02-02  
**状态**: 🔍 调查中

## 问题描述

管理中心前端有两个页面显示不正常：

### 1. 角色列表页面 (Role List)
**URL**: http://localhost:3000/role/list

**问题**:
- 表格显示空白
- 角色名称、代码、类型、描述等列都没有数据
- 只显示操作按钮（Edit, Role Members, Delete）

**后端 API 验证**:
```bash
curl http://localhost:8090/api/v1/admin/roles
```

**API 返回正常**:
```json
[
  {
    "id":"role-auditor",
    "name":"审计员",
    "code":"AUDITOR",
    "type":"ADMIN",
    "description":"System auditor with read-only access to audit logs and system monitoring",
    "status":"ACTIVE",
    "isSystem":true
  },
  ...
]
```

### 2. 组织架构页面 (Organization)
**URL**: http://localhost:3000/organization

**问题**:
- 左侧业务单元树显示 "No Data"
- 右侧详情区域显示 "No Data"
- 无法加载业务单元数据

**后端 API 验证**:
```bash
curl http://localhost:8090/api/v1/admin/business-units/tree
```

**API 返回错误**:
```json
{
  "code":"BIZ_ERROR",
  "message":"Business logic error occurred",
  "timestamp":"2026-02-02T05:50:31.044756193Z",
  "path":"/api/v1/admin/business-units/tree",
  "traceId":"7c63b8b3"
}
```

**后端日志**:
```
2026-02-02 05:50:31 [admin-center] [http-nio-8080-exec-6] WARN  c.p.c.e.GlobalExceptionHandler - Business exception [7c63b8b3]: NullPointerException - null
```

## 根本原因分析

### 角色列表问题
- 后端 API 返回数据正常
- 问题可能在前端：
  1. 前端未正确解析 API 响应
  2. 前端表格列配置错误
  3. 前端数据绑定问题

### 组织架构问题
- 后端 API 抛出 `NullPointerException`
- 数据库中有10个业务单元数据（已验证）
- 问题可能在后端：
  1. `OrganizationManagerComponent.getBusinessUnitTree()` 方法中某个对象为 null
  2. 可能是 `BusinessUnitTree.fromEntity()` 转换时出错
  3. 可能是 `EntityTypeConverter.toBusinessUnitStatus()` 转换时出错

## 数据库验证

### 角色数据 ✅
```sql
SELECT id, code, name, type, status FROM sys_roles ORDER BY code;
```
结果：5个角色，数据完整

### 业务单元数据 ✅
```sql
SELECT id, code, name, parent_id, level FROM sys_business_units ORDER BY level, code;
```
结果：10个业务单元，数据完整

### 虚拟组数据 ✅
```sql
SELECT code, name, type, status FROM sys_virtual_groups ORDER BY type, code;
```
结果：5个虚拟组，类型已修复为 SYSTEM

## 调试步骤

### 1. 检查前端控制台
需要在浏览器中打开开发者工具，查看：
- Network 标签：检查 API 请求和响应
- Console 标签：检查 JavaScript 错误
- 确认前端是否正确调用了后端 API
- 确认前端是否正确解析了 API 响应

### 2. 启用详细日志
修改 `backend/admin-center/src/main/resources/application.yml`：
```yaml
logging:
  level:
    com.admin: DEBUG
    com.platform: DEBUG
```

### 3. 添加异常堆栈打印
修改 `GlobalExceptionHandler` 以打印完整的异常堆栈：
```java
@ExceptionHandler(NullPointerException.class)
public ResponseEntity<ErrorResponse> handleNullPointerException(NullPointerException ex, HttpServletRequest request) {
    log.error("NullPointerException occurred", ex);  // 添加 ex 参数
    // ...
}
```

### 4. 检查前端 API 配置
检查前端是否正确配置了 API 基础 URL：
- `frontend/admin-center/src/api/config.ts`
- 确认 baseURL 是否指向 `http://localhost:8090/api/v1/admin`

## 临时解决方案

### 方案 1: 重新编译前端
```bash
cd frontend/admin-center
npm run build
docker restart platform-admin-center-frontend-dev
```

### 方案 2: 清除浏览器缓存
- 按 Ctrl+Shift+Delete
- 清除缓存和 Cookie
- 刷新页面

### 方案 3: 重启后端服务
```bash
docker restart platform-admin-center-dev
```

## 下一步行动

1. **立即**: 在浏览器中打开开发者工具，检查前端错误
2. **短期**: 启用详细日志，重现问题，获取完整异常堆栈
3. **中期**: 修复后端 NullPointerException
4. **长期**: 添加前端错误处理和后端异常日志

## 相关文件

### 后端
- `backend/admin-center/src/main/java/com/admin/controller/BusinessUnitController.java`
- `backend/admin-center/src/main/java/com/admin/component/OrganizationManagerComponent.java`
- `backend/admin-center/src/main/java/com/admin/dto/response/BusinessUnitTree.java`
- `backend/admin-center/src/main/java/com/admin/util/EntityTypeConverter.java`
- `backend/platform-common/src/main/java/com/platform/common/exception/GlobalExceptionHandler.java`

### 前端
- `frontend/admin-center/src/views/role/RoleList.vue`
- `frontend/admin-center/src/views/organization/BusinessUnitTree.vue`
- `frontend/admin-center/src/api/role.ts`
- `frontend/admin-center/src/api/businessUnit.ts`
- `frontend/admin-center/src/api/config.ts`

## 注意事项

- 角色 API 工作正常，说明后端基础设施没问题
- 业务单元 API 抛出异常，需要修复
- 前端可能需要重新编译或清除缓存
- 建议先修复后端异常，再检查前端问题
