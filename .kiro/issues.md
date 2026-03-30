# 已知问题追踪

> 代码审查中发现的问题集中记录于此。每个问题标注优先级、状态和发现来源。
> 新问题追加到对应分类末尾，修复后将状态改为 ✅ 并注明修复的 spec/commit。

## 状态说明

- 🔴 Critical — 安全漏洞或数据损坏风险，需尽快修复
- 🟡 Major — 功能缺陷或架构问题，影响用户体验
- 🟢 Minor — 代码质量、性能优化、无效代码
- ✅ Fixed — 已修复

---

## 认证授权 (authn/authz)

### 🟡 ISSUE-001: admin-center `AuthController.refresh()` 返回 refreshToken 而非新 accessToken
- **文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java:73-88`
- **描述**: `refresh()` 返回 `Map.of("accessToken", refreshToken, ...)`，把传入的 refreshToken 原样返回，没有生成新 access token
- **影响**: admin-center 的 token refresh 功能不工作
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🟡 ISSUE-002: user-portal 和 developer-workstation 后端缺少 `/auth/refresh` 端点
- **文件**: `backend/user-portal/src/main/java/com/portal/controller/AuthController.java`, `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java`
- **描述**: Kong 配置了 refresh 路由，前端有 refresh 逻辑，但后端没有 `/auth/refresh` 端点
- **影响**: 三个前端应用的 token refresh 功能都不工作
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🟢 ISSUE-003: platform-security `JwtAuthenticationFilter.shouldNotFilter()` 路径对所有模块无效
- **文件**: `backend/platform-security/src/main/java/com/platform/security/filter/JwtAuthenticationFilter.java`
- **描述**: `shouldNotFilter()` 使用 `path.startsWith("/api/auth/")`，但 `getRequestURI()` 返回包含 context-path 的完整路径，三个模块都不匹配。不影响功能（auth 端点不携带 JWT token）
- **建议**: 改为 `path.contains("/auth/login")` 或使用 `getServletPath()`
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🟢 ISSUE-004: `JwtTokenServiceImpl` 密钥创建不做 padding
- **文件**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`
- **描述**: 构造函数直接 `Keys.hmacShaKeyFor(secret.getBytes())`，不做 padding。各模块 AuthController 对 < 32 字节密钥做 `Arrays.copyOf(keyBytes, 32)` padding。如果 `JWT_SECRET` < 32 字节会抛 `WeakKeyException`
- **影响**: 默认开发密钥（46 字节）不受影响，生产环境需确保 ≥ 32 字节
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🔴 ISSUE-005: `JwtTokenServiceImpl.hashToken()` 使用 `String.hashCode()` 做黑名单 key
- **文件**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`
- **描述**: `hashToken()` 使用 `Integer.toHexString(token.hashCode())`，32-bit 哈希碰撞概率高，可能导致合法 token 被误判为已黑名单
- **建议**: 改为 SHA-256 哈希
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🔴 ISSUE-006: admin-center `AuthController` 暴露调试端点
- **文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java`
- **描述**: `/auth/test-password` 和 `/auth/generate-hash` 端点无 `@Profile("dev")` 限制，生产环境可访问
- **影响**: 攻击者可利用这些端点验证密码或生成哈希
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

## 代码质量

### 🟡 ISSUE-007: user-portal 12+ 处 `new RestTemplate()` 直接创建实例
- **文件**: `ProcessComponent.java`(7处), `ProcessFormComponent.java`(2处), `ProcessDraftComponent.java`(1处), `TaskFormComponent.java`(1处)
- **描述**: 不使用 Spring 管理的 bean，不携带认证头，不复用连接池，每次调用创建新 HTTP 连接
- **建议**: 统一使用 Spring 管理的 `RestTemplate` bean 并配置拦截器
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🟡 ISSUE-008: user-portal 多个 Controller 使用 `@RequestHeader("X-User-Id")` required=true
- **文件**: `TaskController.java`, `MemberController.java`, `ProcessController.java`, `ProcessFormController.java`, `TaskFormController.java`
- **描述**: 依赖前端通过请求头传递用户身份，而非从 JWT SecurityContext 获取。本次修复只改了 `UserPermissionController`
- **建议**: 统一从 SecurityContext 获取，`X-User-Id` 头作为可选回退
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🟢 ISSUE-009: developer-workstation `user.ts` 的 `changePassword` 调用不存在的端点
- **文件**: `frontend/developer-workstation/src/api/user.ts`
- **描述**: `changePassword` 调用 `/auth/change-password`，但 admin-center 没有此端点
- **影响**: developer-workstation 的修改密码功能不工作
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)


## 构建与测试

### 🟡 ISSUE-010: admin-center 编译失败 — FunctionUnitManagerComponent 缺少 Collectors import
- **文件**: `backend/admin-center/src/main/java/com/admin/component/FunctionUnitManagerComponent.java`, `backend/admin-center/src/main/java/com/admin/controller/FunctionUnitController.java`
- **描述**: 3 个编译错误，缺少 `java.util.stream.Collectors` import，导致 `mvn test` 无法运行
- **影响**: admin-center 模块无法编译和测试
- **修复于**: kong-authn-authz-fix 部署阶段 (2026-03-30) — 添加了缺失的 import

### 🟢 ISSUE-011: platform-security RoleEntityPropertyTest 缺少嵌入式数据库
- **文件**: `backend/platform-security/src/test/java/.../RoleEntityPropertyTest.java`
- **描述**: `Failed to replace DataSource with an embedded database` — 测试需要嵌入式数据库但未配置
- **影响**: 该测试类始终报错
- **发现于**: kong-authn-authz-fix 任务 11 检查点 (2026-03-30)

### 🟢 ISSUE-012: developer-workstation 多个集成测试因缺少 ApplicationContext 失败
- **文件**: `MemberControllerTest`, `VersionControllerIntegrationTest`, `SpringSecurityAnnotationIntegrationTest`, `VersioningEndToEndTest`
- **描述**: 50 个 errors，ApplicationContext 启动失败（缺少 DB/Redis 连接）
- **影响**: 集成测试无法在无外部依赖环境下运行
- **发现于**: kong-authn-authz-fix 任务 11 检查点 (2026-03-30)


### ✅ ISSUE-013: user-portal 三个表缺少 lock_version 列
- **文件**: `deploy/init-scripts/00-schema/03-user-portal-schema.sql`
- **描述**: JPA 实体 `ProcessInstance`、`DelegationRule`、`ProcessDraft` 使用 `@Version` + `lock_version` 列做乐观锁，但 `up_process_instance`、`up_delegation_rule`、`up_process_draft` 表的 SQL 脚本没有定义此列
- **影响**: 查询 `up_process_instance` 时报 `column pi1_0.lock_version does not exist`
- **修复于**: kong-authn-authz-fix 部署阶段 (2026-03-30) — 创建了 `17-add-lock-version-to-user-portal-tables.sql` 迁移脚本


### ✅ ISSUE-014: ProcessComponent 解析 admin-center 响应时使用错误的字段名
- **文件**: `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`
- **描述**: `getAvailableProcessDefinitions()` 检查 `response.containsKey("content")`，但 admin-center 返回的是 `ApiResponse` 格式 `{success, data: [...]}` 而非分页格式 `{content: [...]}`。导致功能单元列表始终为空
- **影响**: New Requests 页面不显示可发起的流程
- **修复于**: kong-authn-authz-fix 部署验证阶段 (2026-03-30) — 添加了对 `data` 字段的兼容处理

### ✅ ISSUE-015: UserProfileDropdown 未正确解包 ApiResponse
- **文件**: `frontend/user-portal/src/components/UserProfileDropdown.vue`
- **描述**: 直接访问 `data.businessUnits` 但 axios 拦截器返回的是 `ApiResponse` 包装对象，实际数据在 `data.data.businessUnits`
- **影响**: 用户信息面板的 Business Units、Virtual Groups、Roles 显示为空
- **修复于**: kong-authn-authz-fix 部署验证阶段 (2026-03-30) — 添加了 `response.data || response` 兼容处理


### ✅ ISSUE-016: ProcessFormComponent 解析 admin-center 响应时使用错误的字段名
- **文件**: `backend/user-portal/src/main/java/com/portal/component/ProcessFormComponent.java`
- **描述**: `fetchProcessFormDefinition()` 和 `checkProcessFormExists()` 调用 `/function-units/{id}/forms` 后检查 `response.containsKey("content")`，但该 API 返回 ApiResponse 格式 `{data: [...]}`
- **影响**: 流程表单加载失败
- **修复于**: kong-authn-authz-fix 部署验证阶段 (2026-03-30) — 添加了对 `data` 字段的兼容处理
