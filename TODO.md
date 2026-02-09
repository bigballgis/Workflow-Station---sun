# TODO / 待整改项

---

## 🔴 P0 — 安全（上线前必须修复）

### 1. 所有后端服务 SecurityConfig 使用 `anyRequest().permitAll()`
- **描述**: admin-center、user-portal、workflow-engine 三个服务的 `SecurityConfig` 全部放开了所有请求，没有任何鉴权。
- **影响**: 任何人可以不登录直接调用所有 API，包括管理员接口。
- **涉及文件**:
  - `backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`
  - `backend/user-portal/src/main/java/com/portal/config/SecurityConfig.java`
  - `backend/workflow-engine-core/src/main/java/com/workflow/config/SecurityConfig.java`
- **方案**: 实现 JWT Filter，只对 `/api/v1/auth/login`、`/actuator/health`、`/swagger-ui/**` 等白名单放行，其余需要 Bearer Token。

### 2. ~~CORS 配置硬编码 localhost 且过于宽松~~ ✅ 已修复
- **描述**: 
  - developer-workstation 硬编码 `http://localhost:3000,3002,3003,3004,5173`
  - admin-center WebMvcConfig 硬编码 `http://localhost:3001,3000`
  - API Gateway 配置 `allowedOrigins: "*"`
- **影响**: 生产环境 CORS 不安全，开发环境地址不应出现在代码里。
- **涉及文件**:
  - `backend/developer-workstation/src/main/java/com/developer/config/SecurityConfig.java`
  - `backend/admin-center/src/main/java/com/admin/config/WebMvcConfig.java`
  - `backend/api-gateway/src/main/resources/application.yml`
- **方案**: CORS allowedOrigins 提取到环境变量/配置文件，按环境区分。

### 3. ~~Swagger/API 文档在生产环境未禁用~~ ✅ 已修复
- **描述**: SecurityConfig 中 `/swagger-ui/**`、`/v3/api-docs/**` 全部 permitAll，生产环境也暴露。
- **方案**: 通过 `@Profile("!prod")` 或配置开关控制 Swagger 是否启用。

---

## 🟠 P1 — 架构

### 4. API Gateway 被架空，无流量经过
- **描述**: 前端 nginx 直接 proxy_pass 到各后端服务，完全绕过 API Gateway。Gateway 实现的统一鉴权（JWT AuthenticationFilter）、限流（Redis RateLimitFilter）、请求日志均未生效。
- **涉及文件**:
  - `frontend/admin-center/nginx.conf`
  - `frontend/user-portal/nginx.conf`
  - `frontend/developer-workstation/nginx.conf`
  - `backend/api-gateway/` 整个模块
- **方案**:
  - A: 改 nginx 统一 proxy 到 API Gateway（标准微服务做法）
  - B: 移除 API Gateway，保持 nginx 直连（更简单）

### 5. User Portal 大量 Controller 只有 TODO 桩代码
- **描述**: PermissionRequestController、MemberController、ExitController、ApprovalController 共 20+ 个 TODO，所有接口返回 mock 数据，未调用 admin-center API。
- **涉及文件**:
  - `backend/user-portal/src/main/java/com/portal/controller/PermissionRequestController.java`
  - `backend/user-portal/src/main/java/com/portal/controller/MemberController.java`
  - `backend/user-portal/src/main/java/com/portal/controller/ExitController.java`
  - `backend/user-portal/src/main/java/com/portal/controller/ApprovalController.java`
- **方案**: 实现 REST Client（Feign 或 RestTemplate）调用 admin-center API，加 Circuit Breaker。

### 6. Admin Center 工作流引擎集成未完成
- **描述**: VirtualGroupTaskServiceImpl、DepartmentRoleTaskServiceImpl 中任务认领、委托、查询等方法全是 TODO 桩。
- **涉及文件**:
  - `backend/admin-center/src/main/java/com/admin/service/impl/VirtualGroupTaskServiceImpl.java`
  - `backend/admin-center/src/main/java/com/admin/service/impl/DepartmentRoleTaskServiceImpl.java`

### 7. User Portal 前端使用 Mock 登录
- **描述**: `user-portal/src/stores/user.ts` 中 login 方法生成 `token_${Date.now()}` 假 token，不调用后端 API。
- **涉及文件**:
  - `frontend/user-portal/src/stores/user.ts`
- **方案**: 对接真实的 auth API（与 admin-center 的 auth 接口一致）。

---

## 🟡 P2 — 配置与部署

### 8. 服务间 URL 默认值不一致
- **描述**: Java `@Value` 注解中 `admin-center.url` 默认 `http://localhost:8090`，但 `workflow-engine.url` 有的默认 `http://localhost:8091` 有的默认 `http://localhost:8081`。Docker profile 中用 `platform-admin-center` 容器名，但 docker-compose service name 是 `admin-center`。
- **涉及文件**:
  - `backend/user-portal/src/main/java/com/portal/client/WorkflowEngineClient.java` (8091)
  - `backend/admin-center/src/main/java/com/admin/client/WorkflowEngineClient.java` (8091)
  - `backend/developer-workstation/src/main/java/com/developer/client/WorkflowEngineClient.java` (8091)
  - `backend/*/src/main/resources/application-docker.yml` (platform-xxx-xxx 容器名)
- **方案**: 统一所有默认值；Docker profile 中容器名与 docker-compose service name 对齐。

### 9. Flyway 数据库迁移全部禁用
- **描述**: 所有服务 `flyway.enabled: false`，依赖手动 SQL 脚本初始化。
- **影响**: 无法追踪 schema 变更历史，多环境 schema 可能不一致。
- **方案**: 启用 Flyway，将现有 init-scripts 转为 migration 脚本。

### 10. ~~API Gateway docker-compose 缺少路由所需的环境变量~~ ✅ 已修复
- **描述**: API Gateway 的 `application.yml` 引用了 `WORKFLOW_ENGINE_URL`、`ADMIN_CENTER_URL`、`DEVELOPER_WORKSTATION_URL`、`USER_PORTAL_URL`，但 docker-compose 中 api-gateway 服务未传入这些变量。
- **涉及文件**: 所有环境的 `docker-compose.*.yml` 中 api-gateway 服务定义
- **方案**: 在 api-gateway 的 environment 中补充这些变量。

---

## 🟢 P3 — 代码质量

### 11. 前端残留中文硬编码
- **描述**: 
  - `frontend/user-portal/src/views/tasks/index.vue` 中有中文注释和中文代码注释（HTML 注释中的中文不影响用户，但 JS 注释中的中文说明代码可能有未 i18n 的逻辑）
  - `frontend/user-portal/` 整体未做 i18n 改造（按之前约定不修改 user-portal）
- **备注**: user-portal 前端暂不处理（用户要求）

### 12. 后端测试代码中有中文硬编码
- **描述**: `PerformanceIntegrationTest.java` 中 `@DisplayName`、`System.out.println` 全是中文。
- **涉及文件**: `backend/workflow-engine-core/src/test/java/com/workflow/integration/PerformanceIntegrationTest.java`
- **方案**: 改为英文，保持测试输出一致性。

### 13. platform-common 中有未使用的配置类
- **描述**: `ApiConfig`、`MonitoringConfig`、`MessagingConfig`、`WorkflowConfig` 等定义了 `userServiceUrl`、`notificationServiceUrl`、`smsProviderUrl`、`alertNotificationUrl` 等字段，但实际服务中未使用这些配置。
- **涉及文件**:
  - `backend/platform-common/src/main/java/com/platform/common/config/ApiConfig.java`
  - `backend/platform-common/src/main/java/com/platform/common/config/MonitoringConfig.java`
  - `backend/platform-common/src/main/java/com/platform/common/config/MessagingConfig.java`
  - `backend/platform-common/src/main/java/com/platform/common/config/WorkflowConfig.java`
- **方案**: 清理未使用的配置字段，或标记为 future use。

### 14. JWT Token 存储在 localStorage
- **描述**: 前端（admin-center、user-portal）将 JWT token 存在 localStorage，存在 XSS 攻击风险。
- **涉及文件**:
  - `frontend/admin-center/src/api/auth.ts`
  - `frontend/user-portal/src/api/auth.ts`
- **方案**: 改用 HttpOnly Cookie 存储 token（需要后端配合设置 Set-Cookie）。

### 15. Developer Workstation ProcessService 工作流集成未完成
- **描述**: `ProcessService.java` 中 `startProcess` 方法有 TODO，未实际调用 Flowable。
- **涉及文件**: `backend/developer-workstation/src/main/java/com/developer/service/ProcessService.java`

---

## 📋 整改优先级总结

| 优先级 | 编号 | 简述 | 工作量 |
|--------|------|------|--------|
| P0 | 1 | SecurityConfig permitAll | 2-3天 |
| P0 | 2 | CORS 硬编码 | 0.5天 |
| P0 | 3 | Swagger 生产禁用 | 0.5天 |
| P1 | 4 | API Gateway 架空 | 2-3天 |
| P1 | 5 | User Portal TODO 桩代码 | 3-5天 |
| P1 | 6 | Admin Center 工作流集成 | 2-3天 |
| P1 | 7 | User Portal Mock 登录 | 1天 |
| P2 | 8 | 服务间 URL 默认值不一致 | 0.5天 |
| P2 | 9 | Flyway 迁移禁用 | 2天 |
| P2 | 10 | Gateway 缺环境变量 | 0.5天 |
| P3 | 11-15 | 代码质量杂项 | 各0.5-1天 |
