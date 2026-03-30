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

### ✅ ISSUE-001: admin-center `AuthController.refresh()` 返回 refreshToken 而非新 accessToken
- **文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java`
- **描述**: `refresh()` 返回 `Map.of("accessToken", refreshToken, ...)`，把传入的 refreshToken 原样返回
- **修复于**: known-issues-fix (2026-03-30) — 改为调用 `authService.generateAccessTokenForUser()` 生成新 token

### ✅ ISSUE-002: user-portal 和 developer-workstation 后端缺少 `/auth/refresh` 端点
- **文件**: `backend/user-portal/src/main/java/com/portal/controller/AuthController.java`, `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java`
- **描述**: Kong 配置了 refresh 路由，前端有 refresh 逻辑，但后端没有 `/auth/refresh` 端点
- **修复于**: known-issues-fix (2026-03-30) — 两个模块都添加了 `/auth/refresh` 端点

### ✅ ISSUE-003: platform-security `JwtAuthenticationFilter.shouldNotFilter()` 路径对所有模块无效
- **文件**: `backend/platform-security/src/main/java/com/platform/security/filter/JwtAuthenticationFilter.java`
- **描述**: 使用 `getRequestURI()` 返回包含 context-path 的路径，导致 `/api/auth/` 不匹配
- **修复于**: known-issues-fix (2026-03-30) — 改为 `getServletPath()` + `/auth/` 路径

### 🟢 ISSUE-004: `JwtTokenServiceImpl` 密钥创建不做 padding
- **文件**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`
- **描述**: 构造函数直接 `Keys.hmacShaKeyFor(secret.getBytes())`，不做 padding。生产环境需确保 `JWT_SECRET` ≥ 32 字节
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### ✅ ISSUE-005: `JwtTokenServiceImpl.hashToken()` 使用 `String.hashCode()` 做黑名单 key
- **文件**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`
- **描述**: 32-bit 哈希碰撞概率高，可能导致合法 token 被误判为已黑名单
- **修复于**: known-issues-fix (2026-03-30) — 改为 SHA-256 哈希

### ✅ ISSUE-006: admin-center `AuthController` 暴露调试端点
- **文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java`
- **描述**: `/auth/test-password` 和 `/auth/generate-hash` 端点无环境限制
- **修复于**: known-issues-fix (2026-03-30) — 删除了调试端点

## 代码质量

### � ISSUE-007: user-portal 12+ 处 `new RestTemplate()` 直接创建实例
- **文件**: `ProcessComponent.java`(7处), `ProcessFormComponent.java`(2处), `ProcessDraftComponent.java`(1处), `TaskFormComponent.java`(1处)
- **描述**: 不使用 Spring 管理的 bean，不携带认证头，不复用连接池
- **建议**: 创建单独 spec 统一重构为 Spring 管理的 RestTemplate bean
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### 🟡 ISSUE-008: user-portal 多个 Controller 使用 `@RequestHeader("X-User-Id")` required=true
- **文件**: `TaskController.java`, `MemberController.java`, `ProcessController.java`, `ProcessFormController.java`, `TaskFormController.java`
- **描述**: 依赖前端通过请求头传递用户身份，而非从 JWT SecurityContext 获取
- **建议**: 创建单独 spec 统一从 SecurityContext 获取
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

### � ISSUE-009: developer-workstation `user.ts` 的 `changePassword` 调用不存在的端点
- **文件**: `frontend/developer-workstation/src/api/user.ts`
- **描述**: `changePassword` 调用 `/auth/change-password`，但 admin-center 没有此端点
- **发现于**: kong-authn-authz-fix 审查 (2026-03-30)

## 构建与测试

### ✅ ISSUE-010: admin-center 编译失败 — FunctionUnitManagerComponent 缺少 Collectors import
- **修复于**: kong-authn-authz-fix 部署阶段 (2026-03-30)

### 🟢 ISSUE-011: platform-security RoleEntityPropertyTest 缺少嵌入式数据库
- **文件**: `backend/platform-security/src/test/java/.../RoleEntityPropertyTest.java`
- **描述**: `Failed to replace DataSource with an embedded database`
- **发现于**: kong-authn-authz-fix 任务 11 检查点 (2026-03-30)

### 🟢 ISSUE-012: developer-workstation 多个集成测试因缺少 ApplicationContext 失败
- **文件**: `MemberControllerTest`, `VersionControllerIntegrationTest` 等
- **描述**: 50 个 errors，ApplicationContext 启动失败（缺少 DB/Redis 连接）
- **发现于**: kong-authn-authz-fix 任务 11 检查点 (2026-03-30)

### ✅ ISSUE-013: user-portal 三个表缺少 lock_version 列
- **修复于**: kong-authn-authz-fix 部署阶段 (2026-03-30)

### ✅ ISSUE-014: ProcessComponent 解析 admin-center 响应时使用错误的字段名
- **修复于**: kong-authn-authz-fix 部署验证阶段 (2026-03-30)

### ✅ ISSUE-015: UserProfileDropdown 未正确解包 ApiResponse
- **修复于**: kong-authn-authz-fix 部署验证阶段 (2026-03-30)

### ✅ ISSUE-016: ProcessFormComponent 解析 admin-center 响应时使用错误的字段名
- **修复于**: kong-authn-authz-fix 部署验证阶段 (2026-03-30)
