# Kong 认证授权全面整改 Bugfix Design

## Overview

集成 Kong API Gateway 后，三个模块（admin-center、developer-workstation、user-portal）的认证授权链路存在系统性问题。经过对所有安全相关源文件的逐一审查和三模块横向对比，共识别出 **16 个独立的 bug 条件**，涵盖以下 6 个维度：

1. **架构一致性缺陷**（C1–C4）：三个模块的 JWT 过滤器实现各不相同，违反统一架构原则
2. **前端路由与 API 路径错误**（C5–C8）：前端 baseURL 与 Kong 路由不匹配，导致请求路由到错误后端
3. **用户身份传递不可靠**（C9–C11）：硬编码回退值、X-User-Id 头部优先于 JWT、服务间调用无认证
4. **安全漏洞**（C12–C14）：JWT 验证不完整、token 黑名单缺失、logout 未失效 token
5. **代码风格不一致**（C15）：JWT 配置属性命名不统一
6. **回归风险**（C16）：principal 类型变更导致 `authentication.getName()` 行为改变

本设计以 `platform-security` 模块的 `JwtAuthenticationFilter` + `JwtTokenService` + `SecurityContextUtils` 为标准基线，统一所有模块的安全实现。

## Glossary

- **Bug_Condition (C)**: 导致认证授权失败或安全风险的输入条件集合
- **Property (P)**: 修复后的期望行为 — JWT token 被正确解析，用户身份统一从 SecurityContext 获取，API 请求正确路由
- **Preservation**: 现有的登录流程、admin-center 管理功能、Kong 已有路由、developer-workstation 权限检查等非 bug 路径的行为必须保持不变
- **platform-security 标准基线**: `com.platform.security.filter.JwtAuthenticationFilter` — 使用 `JwtTokenService.validateToken()` + `extractUserPrincipal()` 解析 JWT，将 `UserPrincipal` 对象设为 authentication principal，支持 token 黑名单检查
- **JwtTokenService**: `com.platform.security.service.JwtTokenService` — 标准 JWT 服务接口，提供 `validateToken()`、`extractUserPrincipal()`、`blacklistToken()`、`isBlacklisted()` 等方法
- **JwtTokenServiceImpl**: `com.platform.security.service.impl.JwtTokenServiceImpl` — 标准实现，使用 `JwtProperties`（prefix: `platform.security.jwt`）配置，依赖 `StringRedisTemplate` 做 token 黑名单
- **SecurityContextUtils**: `com.platform.security.util.SecurityContextUtils` — 从 `SecurityContextHolder` 获取 `UserPrincipal`，要求 principal 必须是 `UserPrincipal` 类型（非 String）
- **UserPrincipal**: `com.platform.common.dto.UserPrincipal` — 统一用户身份 DTO，包含 userId、username、email、displayName、roles、permissions、language、superAdmin
- **Kong**: API Gateway，负责路由转发、CORS、限流和追踪，不做 JWT 验证（JWT 验证由后端 JwtAuthenticationFilter 处理）
- **SecurityComponent**: `com.developer.component.SecurityComponent` — developer-workstation 自定义的安全组件接口，与 platform-security 的 `JwtTokenService` 功能重叠但 API 不同

## Bug Details

### 维度一：架构一致性缺陷

#### C1 — user-portal SecurityConfig 未注册 JwtAuthenticationFilter

**文件**: `backend/user-portal/src/main/java/com/portal/config/SecurityConfig.java`

user-portal 的 `SecurityConfig` 只配置了 `csrf.disable()` + `sessionManagement.STATELESS` + `permitAll()`，没有注入任何 JWT 过滤器，也没有调用 `addFilterBefore()`。对比 admin-center 和 developer-workstation 的 SecurityConfig，它们都注册了 JWT 过滤器。

这导致所有到达 user-portal 的请求在 Spring Security 层面都是未认证的，`SecurityContextHolder` 中没有 `Authentication` 对象，`SecurityContextUtils.getCurrentUser()` 始终返回 `Optional.empty()`。

**影响**: user-portal 的所有需要用户身份的端点（`/my-permissions`、`/tasks`、`/delegations` 等）无法从 SecurityContext 获取用户信息，只能依赖不可靠的 `X-User-Id` 请求头。

#### C2 — developer-workstation 使用自定义 JwtAuthenticationFilter 而非 platform-security 标准实现

**文件**: `backend/developer-workstation/src/main/java/com/developer/security/JwtAuthenticationFilter.java`

developer-workstation 实现了自己的 `JwtAuthenticationFilter`，依赖 `SecurityComponent`（而非 `JwtTokenService`），存在以下问题：
1. 将 `username`（String）设为 authentication 的 principal，而非 `UserPrincipal` 对象 — 导致 `SecurityContextUtils.getCurrentUser()` 返回 `Optional.empty()`
2. 只提取 roles，不提取 permissions — 导致权限信息丢失
3. 不提取 userId、email、displayName、language — 导致用户身份信息不完整
4. 没有 `shouldNotFilter()` 方法 — 对所有请求（包括 `/auth/login`）都执行 JWT 解析
5. 不检查 token 黑名单 — 已注销的 token 仍然有效

**影响**: `DeveloperPermissionInterceptor.getUserIdFromRequest()` 优先从 `X-User-Id` 头获取用户 ID，回退到 `authentication.getName()` 获取的是 username 而非 userId，导致权限检查使用错误的标识符。

#### C3 — admin-center 使用内联 JWT 过滤器而非 platform-security 标准实现

**文件**: `backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`

admin-center 在 `SecurityConfig` 中定义了一个匿名内部类 `OncePerRequestFilter` 作为 JWT 过滤器，直接使用 `io.jsonwebtoken` 库解析 JWT，而非使用 platform-security 的 `JwtTokenService`。存在以下问题：
1. 直接使用 `@Value("${jwt.secret}")` 读取密钥，而非使用 `JwtProperties`（prefix: `platform.security.jwt`）— 配置属性命名不一致
2. 不检查 token 黑名单 — 已注销的 token 仍然有效
3. 不验证 token issuer — 任何签名正确的 token 都被接受
4. 回退逻辑（C10 详述）创建的 `UserPrincipal` 的 roles 和 permissions 为空

**影响**: admin-center 的 JWT 验证与 platform-security 标准实现不一致，缺少黑名单检查和 issuer 验证。

#### C4 — developer-workstation SecurityComponentImpl 的 validateToken 存在严重安全漏洞

**文件**: `backend/developer-workstation/src/main/java/com/developer/component/impl/SecurityComponentImpl.java`

`SecurityComponentImpl.validateToken()` 方法中的 `parseWorkflowEngineToken()` 对 3 部分的标准 JWT token（header.payload.signature）**只解析 payload，不验证签名**（代码注释明确写着 "只解析 payload，不验证签名，因为 workflow-engine 可能使用不同的签名方式"）。这意味着：
1. 攻击者可以构造任意 payload 的 JWT token（只要格式正确），绕过签名验证
2. `parseWorkflowEngineToken()` 在 `validateToken()` 中优先于标准 JWT 验证执行
3. 只要 payload 中包含 `sub` 字段且有 `exp` 字段未过期，token 就被认为有效

**影响**: 这是一个严重的安全漏洞，允许 JWT 签名绕过。任何人都可以伪造 JWT token 访问 developer-workstation 的所有 API。

### 维度二：前端路由与 API 路径错误

#### C5 — developer-workstation 前端 user.ts 使用不存在的 Kong 路由 `/api/admin-center`

**文件**: `frontend/developer-workstation/src/api/user.ts`

`adminCenterAxios` 的 `baseURL` 设为 `/api/admin-center`，但 Kong 配置（`deploy/kong/kong.yml.template`）中没有 `/api/admin-center` 路由。Kong 只有以下路由指向 admin-center：`/api/v1/admin`。

nginx 将 `/api/admin-center/...` 转发到 Kong 后，Kong 无匹配路由，请求失败。

**影响**: developer-workstation 前端 `UserProfileDropdown` 无法获取用户的 Business Units、Virtual Groups、Roles 数据，全部显示为空。

#### C6 — developer-workstation 前端 adminCenter.ts 使用不存在的 Kong 路由 `/api/admin-center`

**文件**: `frontend/developer-workstation/src/api/adminCenter.ts`

与 C5 相同的问题。`adminCenterAxios` 的 `baseURL` 设为 `/api/admin-center`，Kong 中无此路由。

**影响**: developer-workstation 的流程设计器中，获取虚拟组、业务单元、角色等数据的 API 调用全部失败。

#### C7 — user-portal 前端 user.ts 使用不存在的 Kong 路由 `/api/admin-center`

**文件**: `frontend/user-portal/src/api/user.ts`

与 C5 相同的问题。`adminCenterAxios` 的 `baseURL` 设为 `/api/admin-center`，Kong 中无此路由。

**影响**: user-portal 前端 `UserProfileDropdown` 通过 `userApi.getBusinessUnits()` 等方法获取用户数据失败。

#### C8 — user-portal 前端 auth.ts baseURL 错误

**文件**: `frontend/user-portal/src/api/auth.ts`

`authRequest` 的 `baseURL` 设为 `/api/v1/auth`。user-portal 后端的 `server.servlet.context-path` 是 `/api/portal`，其 `AuthController` 的 `@RequestMapping("/auth")` 映射到 `/api/portal/auth`。

Kong 配置中 `/api/v1/auth/login` 会匹配 developer-workstation 的路由（`/api/v1`），因为 developer-workstation 的 context-path 是 `/api/v1`。这导致 user-portal 的登录请求被路由到 developer-workstation 后端。

**影响**: user-portal 用户登录时，请求到达 developer-workstation 的 `AuthController` 而非 user-portal 的 `AuthController`。如果用户在 developer-workstation 的 `sys_users` 表中不存在，登录失败；如果存在，返回的 token 和用户信息来自 developer-workstation 的数据，可能与 user-portal 的预期不一致。

### 维度三：用户身份传递不可靠

#### C9 — user-portal 前端 request.ts X-User-Id 硬编码回退 `'user_1'`

**文件**: `frontend/user-portal/src/api/request.ts`（第 48 行）

请求拦截器中：`config.headers['X-User-Id'] = userId || 'user_1'`。当 localStorage 中没有 `userId` 且 `user` 对象解析失败时，回退为硬编码的 `'user_1'`。

**影响**: 后端 `UserPermissionController` 从 `@RequestHeader("X-User-Id")` 获取用户 ID，收到 `'user_1'` 后查询该用户的数据，返回错误用户的权限信息。

#### C10 — admin-center JWT 过滤器 X-Username/X-User-Id 回退时 roles 和 permissions 为空

**文件**: `backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`（第 120-133 行）

当 JWT 解析失败后，回退到从 `X-Username` / `X-User-Id` 头部创建 `UserPrincipal`，但 `roles(Collections.emptyList())` 和 `permissions(Collections.emptyList())`。

**影响**: 通过 RestTemplate 从 user-portal 调用 admin-center 的内部请求（无 JWT token，带 X-Username 头），创建的 `UserPrincipal` 没有任何角色和权限，如果后续有权限检查逻辑会失败。

#### C11 — user-portal RestTemplate 调用 admin-center 无认证头

**文件**: `backend/user-portal/src/main/java/com/portal/controller/UserPermissionController.java`

`UserPermissionController` 通过 `restTemplate.exchange()` 调用 admin-center API（如 `/api/v1/admin/users/{userId}/roles`），但 `HttpEntity` 没有设置 `Authorization` 头或 `X-User-Id` / `X-Username` 头。

**影响**: admin-center 收到的请求没有任何认证信息，JWT 过滤器跳过（无 Authorization 头），回退逻辑也跳过（无 X-Username 头），`SecurityContextHolder` 中没有认证信息。如果 admin-center 的端点有权限检查，请求会被拒绝。

### 维度四：安全漏洞

#### C12 — 三个模块的 AuthController 都不使用 platform-security 的 JwtTokenService

**文件**:
- `backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`（内联过滤器）
- `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java`
- `backend/user-portal/src/main/java/com/portal/controller/AuthController.java`

三个模块的 `AuthController` 都直接使用 `io.jsonwebtoken` 库生成和解析 JWT token，而非使用 platform-security 的 `JwtTokenService`。这导致：
1. 生成的 token 不包含 `issuer` claim — platform-security 的 `JwtTokenServiceImpl` 会设置 `issuer("platform")` 并在验证时检查
2. 生成的 token 不包含 `tokenType` claim — 无法区分 access token 和 refresh token
3. 生成的 token 不包含 `jti`（JWT ID）claim — 无法做 token 黑名单
4. 使用 `@Value("${jwt.secret}")` 读取密钥，而 `JwtProperties` 使用 `platform.security.jwt.secret` — 如果两个配置值不同，过滤器和 AuthController 使用不同的密钥
5. 不调用 `blacklistToken()` — logout 端点不会将 token 加入黑名单

**影响**: 即使 platform-security 的 `JwtAuthenticationFilter` 被正确注册，由于 AuthController 生成的 token 缺少 issuer，如果 `JwtProperties.validateIssuer` 为 true（默认值），`JwtTokenServiceImpl.validateToken()` 会因 issuer 不匹配而拒绝所有 token。

#### C13 — 三个模块的 logout 端点都不失效 token

**文件**:
- `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java` — `logout()` 直接返回 200
- `backend/user-portal/src/main/java/com/portal/controller/AuthController.java` — `logout()` 直接返回 200
- `backend/admin-center/src/main/java/com/admin/service/impl/AuthServiceImpl.java` — `logout(token)` 只打日志 `log.info("User logged out")`，不做 blacklist
- admin-center 的 `AuthController` 调用 `authService.logout(token)`，但 `AuthServiceImpl.logout()` 是空实现

三个模块的 `logout()` 方法都不调用 `JwtTokenService.blacklistToken()` 将 token 加入黑名单。

**影响**: 用户注销后，旧的 access token 仍然有效，直到自然过期（默认 24 小时）。攻击者如果获取到 token，可以在用户注销后继续使用。

#### C14 — WebSocketAuthInterceptor JWT 密钥长度不足时不做 padding

**文件**: `backend/user-portal/src/main/java/com/portal/config/WebSocketAuthInterceptor.java`

`WebSocketAuthInterceptor` 直接使用 `Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8))` 创建密钥，没有像其他地方那样检查 `keyBytes.length < 32` 并做 padding。如果 `jwt.secret` 配置值长度不足 32 字节，会抛出 `WeakKeyException`。

此外，该拦截器直接使用 `io.jsonwebtoken` 库解析 JWT，而非使用 `JwtTokenService`，与其他安全组件不一致。

**影响**: WebSocket 连接认证可能因密钥长度不足而失败，且不检查 token 黑名单。

### 维度五：代码风格不一致

#### C15 — JWT 配置属性命名不统一

三个模块使用不同的配置属性名读取 JWT secret：
- admin-center: `@Value("${jwt.secret}")` — 对应 `application.yml` 中的 `jwt.secret`
- developer-workstation `SecurityComponentImpl`: `@Value("${security.jwt.secret}")` — 对应 `application.yml` 中的 `security.jwt.secret`
- developer-workstation `AuthController`: `@Value("${jwt.secret}")` — 对应 `application.yml` 中的 `jwt.secret`
- user-portal `AuthController`: `@Value("${jwt.secret}")` — 对应 `application.yml` 中的 `jwt.secret`
- user-portal `WebSocketAuthInterceptor`: `@Value("${jwt.secret}")` — 对应 `application.yml` 中的 `jwt.secret`
- platform-security `JwtProperties`: `@ConfigurationProperties(prefix = "platform.security.jwt")` — 对应 `platform.security.jwt.secret`

**影响**: 如果要统一使用 platform-security 的 `JwtTokenService`，需要确保所有模块的 `application.yml` 都配置了 `platform.security.jwt.secret`，或者修改 `JwtProperties` 的 prefix 以兼容现有配置。当前各模块的 `application.yml` 都没有 `platform.security.jwt` 配置节。

#### C16 — developer-workstation 中 `authentication.getName()` 在 principal 变为 UserPrincipal 后行为改变

**文件**:
- `backend/developer-workstation/src/main/java/com/developer/DeveloperWorkstationApplication.java` — `auditorProvider()` 使用 `authentication.getName()` 做 JPA 审计
- `backend/developer-workstation/src/main/java/com/developer/security/DatabasePermissionEvaluator.java` — 使用 `authentication.getName()` 获取 username 做权限检查
- `backend/developer-workstation/src/main/java/com/developer/security/UserContextService.java` — 使用 `authentication.getName()` 获取 username
- `backend/developer-workstation/src/main/java/com/developer/security/DeveloperPermissionInterceptor.java` — `getUserIdFromRequest()` 回退到 `authentication.getName()`
- `backend/developer-workstation/src/main/java/com/developer/component/impl/ExportImportComponentImpl.java` — 使用 `authentication.getName()` 获取 username
- `backend/developer-workstation/src/main/java/com/developer/component/impl/FunctionUnitComponentImpl.java` — 使用 `authentication.getName()` 获取 username
- `backend/developer-workstation/src/main/java/com/developer/component/impl/VersionComponentImpl.java` — 使用 `authentication.getName()` 获取 username

当前 developer-workstation 的自定义 `JwtAuthenticationFilter` 将 String username 设为 principal，`authentication.getName()` 返回 username 字符串。修复后 principal 变为 `UserPrincipal` 对象（不实现 `java.security.Principal` 也不实现 `UserDetails`），`UsernamePasswordAuthenticationToken.getName()` 会调用 `principal.toString()`，返回 Lombok `@Data` 生成的 `toString()` 结果（如 `UserPrincipal(userId=xxx, username=xxx, ...)`），而非 username 字符串。

**影响**: 这是一个严重的回归风险。所有使用 `authentication.getName()` 获取 username 的代码在修复后都会得到错误的值，导致 JPA 审计记录错误、权限检查失败、用户上下文获取失败。

**Formal Specification:**
```
FUNCTION isBugCondition(request)
  INPUT: request of type HttpRequest (包含 URL path, headers, JWT token, origin)
  OUTPUT: boolean
  
  // C1: user-portal 无 JWT 过滤器
  IF request.target == user-portal-backend
     AND request.header("Authorization") STARTS WITH "Bearer "
     AND SecurityContextHolder.getContext().getAuthentication() == null
     RETURN true
  
  // C2: developer-workstation JWT 过滤器设置 String principal 而非 UserPrincipal
  IF request.target == developer-workstation-backend
     AND request.header("Authorization") STARTS WITH "Bearer "
     AND authentication.principal IS String (not UserPrincipal)
     RETURN true
  
  // C3: admin-center 内联 JWT 过滤器不检查黑名单和 issuer
  IF request.target == admin-center-backend
     AND request.header("Authorization") STARTS WITH "Bearer "
     AND (token.isBlacklisted OR token.issuer != "platform")
     AND authentication IS NOT null  // 过滤器仍然接受了 token
     RETURN true
  
  // C4: developer-workstation SecurityComponentImpl 签名绕过
  IF request.target == developer-workstation-backend
     AND request.header("Authorization") STARTS WITH "Bearer "
     AND token.signature IS invalid
     AND token.payload CONTAINS "sub" field
     AND SecurityComponentImpl.validateToken(token) == true
     RETURN true
  
  // C5/C6/C7: 前端使用不存在的 Kong 路由 /api/admin-center
  IF request.path STARTS WITH "/api/admin-center/"
     AND request.origin IN [developer-workstation-frontend, user-portal-frontend]
     RETURN true
  
  // C8: user-portal 前端 auth baseURL 错误
  IF request.origin == user-portal-frontend
     AND request.path STARTS WITH "/api/v1/auth/"
     AND request.intendedTarget == user-portal-backend
     RETURN true
  
  // C9: X-User-Id 硬编码回退
  IF request.origin == user-portal-frontend
     AND request.header("X-User-Id") == "user_1"
     AND actualUserId != "user_1"
     RETURN true
  
  // C10: admin-center 回退 principal 无 roles/permissions
  IF request.target == admin-center-backend
     AND request.header("Authorization") IS null OR invalid
     AND request.header("X-Username") IS NOT null
     AND createdPrincipal.roles == EMPTY
     RETURN true
  
  // C11: user-portal RestTemplate 调用 admin-center 无认证头
  IF request.source == user-portal-backend (RestTemplate)
     AND request.target == admin-center-backend
     AND request.header("Authorization") IS null
     AND request.header("X-User-Id") IS null
     RETURN true
  
  // C12: AuthController 生成的 token 缺少 issuer/jti/tokenType
  IF token.generatedBy IN [admin-center-AuthController, dw-AuthController, portal-AuthController]
     AND (token.issuer IS null OR token.jti IS null OR token.tokenType IS null)
     RETURN true
  
  // C13: logout 不失效 token
  IF request.path ENDS WITH "/auth/logout"
     AND request.header("Authorization") STARTS WITH "Bearer "
     AND token NOT added to blacklist after logout
     RETURN true
  
  // C14: WebSocket JWT 密钥不做 padding
  IF request.target == user-portal-websocket
     AND jwtSecret.length < 32
     RETURN true
  
  // C15: JWT 配置属性命名不统一（设计时检查，非运行时）
  // 此条件在代码审查时检查，不在运行时触发
  
  // C16: authentication.getName() 回归风险
  IF request.target == developer-workstation-backend
     AND authentication.principal IS UserPrincipal
     AND codeUsesAuthenticationGetName == true
     AND returnValue != principal.username
     RETURN true
  
  RETURN false
END FUNCTION
```

### Examples

- **C1 示例**: user-portal 前端发送 GET `/api/portal/my-permissions` 带有效 JWT token，后端 `UserPermissionController` 从 `@RequestHeader("X-User-Id")` 获取用户 ID（值为 `'user_1'`），查询到错误用户的数据
- **C2 示例**: developer-workstation 后端解析 JWT token 后，`SecurityContextHolder.getContext().getAuthentication().getPrincipal()` 返回 String 类型的 username `"admin"`，`SecurityContextUtils.getCurrentUser()` 返回 `Optional.empty()`
- **C3 示例**: 用户在 admin-center 注销后，旧 token 仍然可以通过 admin-center 的 JWT 过滤器验证（无黑名单检查）
- **C4 示例**: 攻击者构造 JWT token `eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsImV4cCI6OTk5OTk5OTk5OX0.fake_signature`，developer-workstation 的 `SecurityComponentImpl.validateToken()` 返回 true（因为 `parseWorkflowEngineToken()` 只解析 payload 不验证签名）
- **C5/C6/C7 示例**: developer-workstation 前端 `UserProfileDropdown` 调用 `userApi.getBusinessUnits('user123')`，发送 GET `/api/admin-center/users/user123/business-units`，nginx 转发到 Kong，Kong 无匹配路由返回 404
- **C8 示例**: user-portal 前端调用 `login({ username: 'admin', password: '...' })`，请求发送到 `/api/v1/auth/login`，被 Kong 路由到 developer-workstation 后端
- **C9 示例**: user-portal 前端在 localStorage 中没有 userId 和 user 对象时，发送请求头 `X-User-Id: user_1`
- **C10 示例**: user-portal 后端通过 RestTemplate 调用 admin-center（无 JWT token，带 X-Username 头），admin-center 创建的 `UserPrincipal` 的 roles 和 permissions 为空列表
- **C11 示例**: `UserPermissionController.getUserRoles()` 调用 `restTemplate.exchange(url, HttpMethod.GET, null, ...)` — `HttpEntity` 为 null，无任何认证头
- **C12 示例**: user-portal `AuthController.generateToken()` 生成的 token 不包含 `iss` claim，如果 platform-security 的 `JwtTokenServiceImpl` 启用了 `validateIssuer`，该 token 会被拒绝
- **C13 示例**: 用户点击注销，前端调用 `/api/portal/auth/logout`，后端返回 200 但不将 token 加入黑名单，旧 token 仍可使用 24 小时
- **C14 示例**: 如果 `jwt.secret` 配置为 `"short-key"`（少于 32 字节），`WebSocketAuthInterceptor` 抛出 `WeakKeyException`，WebSocket 连接认证失败

## Expected Behavior

### Preservation Requirements

**Unchanged Behaviors:**
- admin-center 前端的登录流程（`/api/v1/auth/login` → admin-center 后端）必须继续正常工作
- admin-center 前端的 `request.ts` 拦截器（baseURL: `/api/v1/admin`）必须继续正常工作
- developer-workstation 前端的登录流程（`/api/v1/auth/login` → developer-workstation 后端）必须继续正常工作
- developer-workstation 前端的 `index.ts` 拦截器（baseURL: `/api/v1`）必须继续正常工作
- Kong 已有路由（`/api/v1/admin`、`/api/v1`、`/api/portal`）的转发行为不变
- Kong 的 `strip_path: false` 和 `preserve_host: false` 配置不变
- 所有模块的 token refresh 机制必须继续正常工作
- developer-workstation 的 `DeveloperPermissionInterceptor` + `DeveloperPermissionChecker` 权限检查逻辑不变
- developer-workstation 的 `SecurityPermissionConfig` + `DatabasePermissionEvaluator` 方法级安全不变
- user-portal 的 `UserPermissionController` 通过 RestTemplate 调用 admin-center 的模式不变（但需要添加认证头）
- admin-center 的用户/角色/权限管理功能不受影响
- 三个前端应用的路由守卫逻辑不变（检查 localStorage 中的 token）
- user-portal 的 WebSocket 通知功能不变

**Scope:**
所有不涉及上述 15 个 bug 条件的请求路径应完全不受本次修复影响。

## Hypothesized Root Cause

基于对所有安全相关源文件的逐一审查，各 bug 的根本原因如下：

### 架构层面

1. **platform-security 模块设计为共享库但未被充分使用**: platform-security 提供了完整的 `JwtAuthenticationFilter` + `JwtTokenService` + `SecurityContextUtils` 标准实现，但三个应用模块都没有使用它。admin-center 和 developer-workstation 各自实现了自己的 JWT 过滤器，user-portal 完全没有 JWT 过滤器。

2. **ComponentScan 排除规则过于激进**: 三个模块的 `@ComponentScan.excludeFilters` 都排除了 `com.platform.security.service.impl.*`（仅保留 `UserRoleServiceImpl`）和 `com.platform.security.config.*`，导致 `JwtTokenServiceImpl`、`JwtProperties`、`JwtAuthenticationFilter` 都不会被自动扫描。这是有意为之的（避免依赖冲突），但导致了各模块自行实现 JWT 逻辑。

3. **代码由早期 AI 模型生成**: 三个模块的安全实现风格各异，说明是在不同时间点由不同版本的 AI 模型生成的，没有统一的架构设计指导。

### 前端层面

4. **前端 API 路径与 Kong 路由不匹配**: developer-workstation 和 user-portal 的前端 `user.ts` 和 `adminCenter.ts` 使用 `/api/admin-center` 作为 baseURL，但 Kong 中没有配置此路由。这可能是在 Kong 集成之前，前端直接通过 nginx 代理到 admin-center 时的遗留配置。

5. **user-portal auth.ts 复制自 developer-workstation**: user-portal 的 `auth.ts` 的 `baseURL` 设为 `/api/v1/auth`，与 developer-workstation 的 `auth.ts` 完全相同。这说明 user-portal 的 `auth.ts` 是从 developer-workstation 复制过来的，没有修改 baseURL 以匹配 user-portal 的 context-path `/api/portal`。

### 安全层面

6. **SecurityComponentImpl 的 workflow-engine token 兼容逻辑引入了签名绕过**: `parseWorkflowEngineToken()` 方法试图兼容 workflow-engine 的自定义 token 格式，但对标准 3 部分 JWT token 也只解析 payload 不验证签名，导致严重的安全漏洞。

7. **logout 端点未实现 token 失效**: 三个模块的 `logout()` 方法都是空实现（直接返回 200），没有调用 `JwtTokenService.blacklistToken()` 将 token 加入 Redis 黑名单。

## Correctness Properties

Property 1: Bug Condition - 所有模块的 JWT 过滤器统一使用 platform-security 标准实现

_For any_ 带有有效 JWT token 的请求到达任何后端模块（admin-center、developer-workstation、user-portal）时，修复后的 `JwtAuthenticationFilter`（来自 platform-security）SHALL 通过 `JwtTokenService.validateToken()` 验证 token（包括签名验证、过期检查、黑名单检查、issuer 验证），并通过 `JwtTokenService.extractUserPrincipal()` 提取 `UserPrincipal` 对象设置到 `SecurityContextHolder` 中。

**Validates: Requirements 2.3**

Property 2: Bug Condition - 前端 API 路径与 Kong 路由匹配

_For any_ 前端请求，当 developer-workstation 或 user-portal 的组件请求 admin-center 的数据（用户 Business Units、Virtual Groups、Roles、虚拟组列表、业务单元树等）时，修复后的前端代码 SHALL 使用与 Kong 已配置路由匹配的 API 路径（`/api/v1/admin/...`），确保请求正确到达 admin-center 后端。

**Validates: Requirements 2.1**

Property 3: Bug Condition - user-portal 前端 auth 路径正确

_For any_ user-portal 前端发起的认证请求（login、logout、refresh、me、validate），修复后的 auth.ts SHALL 使用 `/api/portal/auth` 作为 baseURL，确保请求被 Kong 路由到 user-portal 后端而非 developer-workstation 后端。

**Validates: Requirements 2.2, 2.4**

Property 4: Bug Condition - 前端不使用硬编码 userId 回退

_For any_ user-portal 前端发起的 API 请求，当 localStorage 中没有有效的 userId 时，修复后的 request.ts 拦截器 SHALL 不设置 `X-User-Id` 头（而非回退为硬编码的 `'user_1'`）。

**Validates: Requirements 2.2, 2.4**

Property 5: Bug Condition - 服务间调用携带认证信息

_For any_ user-portal 后端通过 RestTemplate 调用 admin-center API 的请求，修复后的代码 SHALL 在请求头中携带当前用户的认证信息（JWT token 或 X-User-Id + X-Username 头），确保 admin-center 能识别请求来源的用户身份。

**Validates: Requirements 2.4**

Property 6: Bug Condition - logout 端点失效 token

_For any_ 用户注销请求，修复后的 logout 端点 SHALL 将当前 access token 加入 Redis 黑名单，确保该 token 在自然过期前不能再被使用。

**Validates: Requirements 2.4**

Property 7: Preservation - 现有登录、路由和权限检查流程不变

_For any_ 通过 admin-center 或 developer-workstation 前端发起的登录请求、token 刷新请求、Kong 已有路由的转发、developer-workstation 的权限检查、admin-center 的管理操作，修复后的代码 SHALL 产生与修复前完全相同的行为，保持所有现有功能正常工作。

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

## Fix Implementation

### Changes Required

假设根因分析正确，以下是按优先级排列的修复方案：

### 修复组 A：统一后端 JWT 认证架构（C1, C2, C3, C4, C12, C15）

**目标**: 三个模块统一使用 platform-security 的 `JwtAuthenticationFilter` + `JwtTokenService`

**前置条件**: 解决 `@ComponentScan` 排除规则和 JWT 配置属性命名问题

---

**File**: `backend/admin-center/src/main/resources/application.yml`
**File**: `backend/developer-workstation/src/main/resources/application.yml`
**File**: `backend/user-portal/src/main/resources/application.yml`

**Change A1 — 统一 JWT 配置属性命名**:
1. 在三个模块的 `application.yml` 中添加 `platform.security.jwt` 配置节，值引用现有的 `${JWT_SECRET}` 环境变量
2. 保留现有的 `jwt.secret` 配置（向后兼容 AuthController 中的 `@Value("${jwt.secret}")`），后续 AuthController 改造后可移除

```yaml
# 添加到三个模块的 application.yml
platform:
  security:
    jwt:
      secret: ${JWT_SECRET:your-256-bit-secret-key-for-development-only}
      expiration-ms: ${JWT_EXPIRATION:86400000}
      refresh-expiration-ms: ${JWT_REFRESH_EXPIRATION:604800000}
      issuer: platform
      validate-issuer: false  # 初始设为 false，待 AuthController 改造后改为 true
```

注意：`validate-issuer` 初始设为 `false`，因为现有 AuthController 生成的 token 不包含 issuer claim。待 AuthController 改造为使用 `JwtTokenService` 后，再改为 `true`。

---

**File**: `backend/admin-center/src/main/java/com/admin/AdminCenterApplication.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/DeveloperWorkstationApplication.java`
**File**: `backend/user-portal/src/main/java/com/portal/UserPortalApplication.java`

**Change A2 — 修改 ComponentScan 允许 platform-security JWT 组件被扫描**:

三个模块都需要修改 `@ComponentScan.excludeFilters`，允许以下类被扫描：
- `JwtAuthenticationFilter`（`com.platform.security.filter` 包）
- `JwtTokenServiceImpl`（`com.platform.security.service.impl` 包）
- `JwtProperties`（`com.platform.security.config` 包）

具体修改：
1. 在 `basePackages` 中添加 `"com.platform.security.filter"`
2. 修改 `excludeFilters` 正则，允许 `JwtTokenServiceImpl` 被扫描：将 `"com\\.platform\\.security\\.service\\.impl\\.(?!UserRoleServiceImpl).*"` 改为 `"com\\.platform\\.security\\.service\\.impl\\.(?!UserRoleServiceImpl|JwtTokenServiceImpl).*"`
3. 修改 `excludeFilters` 正则，允许 `JwtProperties` 被扫描：将 `"com\\.platform\\.security\\.config\\..*"` 改为 `"com\\.platform\\.security\\.config\\.(?!JwtProperties).*"`

依赖链验证：
- `JwtAuthenticationFilter` → 依赖 `JwtTokenService`（接口）
- `JwtTokenServiceImpl` → 依赖 `JwtProperties` + `StringRedisTemplate`
- `JwtProperties` → `@ConfigurationProperties(prefix = "platform.security.jwt")`
- `StringRedisTemplate` → 三个模块都已配置 Redis（已验证 `application.yml`）

---

**File**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`

**Change A2.5 — 修复 extractUserPrincipal() 的 userId 兼容性**:

各模块 AuthController 生成的 JWT token 将 userId 存储在 `sub` (subject) claim 中，没有单独的 `userId` claim。但 `JwtTokenServiceImpl.extractUserPrincipal()` 从 `claims.get("userId", String.class)` 获取 userId，不回退到 `claims.getSubject()`。

修改 `extractUserPrincipal()` 方法：
```java
String userId = claims.get(CLAIM_USER_ID, String.class);
if (userId == null) {
    userId = claims.getSubject(); // 回退到 sub claim（AuthController 生成的 token）
}
```

同样修改 `extractUserId()` 方法添加相同的回退逻辑。

---

**File**: `backend/user-portal/src/main/java/com/portal/config/SecurityConfig.java`

**Change A3 — user-portal SecurityConfig 注册 JwtAuthenticationFilter**:
1. 注入 `com.platform.security.filter.JwtAuthenticationFilter`（来自 platform-security）
2. 调用 `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
3. 添加 `@EnableMethodSecurity` 注解（与 developer-workstation 保持一致）

---

**File**: `backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`

**Change A4 — admin-center SecurityConfig 替换内联 JWT 过滤器为 platform-security 标准实现**:
1. 注入 `com.platform.security.filter.JwtAuthenticationFilter`
2. 移除内联的 `jwtAuthenticationFilter()` bean 方法（约 80 行代码）
3. 在 `securityFilterChain()` 中使用注入的 `jwtAuthenticationFilter`
4. 保留 `PasswordEncoder` bean
5. 保留 `AuditRequestFilter` bean
6. 移除 `@Value("${jwt.secret}")` 字段和 `getSigningKey()` 方法

关于回退逻辑（C10）：admin-center 的内联过滤器有一个 X-Username/X-User-Id 回退逻辑，用于服务间调用。platform-security 的标准 `JwtAuthenticationFilter` 没有此逻辑。有两个选择：
- 方案 a：移除回退逻辑，要求服务间调用携带 JWT token（更安全）
- 方案 b：在 admin-center 中添加一个额外的过滤器处理 X-Username/X-User-Id 回退（兼容现有模式）

推荐方案 b：添加一个独立的 `ServiceCallAuthenticationFilter`，在 `JwtAuthenticationFilter` 之后执行，仅当 SecurityContext 中没有认证信息且请求来自内部服务时，从 X-Username/X-User-Id 头创建 `UserPrincipal`。

---

**File**: `backend/developer-workstation/src/main/java/com/developer/config/SecurityConfig.java`

**Change A5 — developer-workstation SecurityConfig 替换自定义 JWT 过滤器为 platform-security 标准实现**:
1. 将注入的 `com.developer.security.JwtAuthenticationFilter` 改为 `com.platform.security.filter.JwtAuthenticationFilter`
2. 保留 CORS 配置和 `PasswordEncoder` bean

---

**File**: `backend/developer-workstation/src/main/java/com/developer/security/JwtAuthenticationFilter.java`

**Change A6 — 移除 developer-workstation 自定义 JwtAuthenticationFilter**:

**CRITICAL**: Change A5 和 A6 必须在同一步原子执行。platform-security 的 `JwtAuthenticationFilter` 和 developer-workstation 的自定义 `JwtAuthenticationFilter` 都标注了 `@Component`，bean 名称都是 `jwtAuthenticationFilter`。如果两者同时存在于 Spring 容器中，会抛 `ConflictingBeanDefinitionException`。

1. 删除 `com.developer.security.JwtAuthenticationFilter` 文件
2. 修改 `SecurityConfig` 的 import 为 `com.platform.security.filter.JwtAuthenticationFilter`
3. 所有引用此类的地方改为引用 `com.platform.security.filter.JwtAuthenticationFilter`

---

**File**: `backend/developer-workstation/src/main/java/com/developer/component/impl/SecurityComponentImpl.java`

**Change A7 — 修复 SecurityComponentImpl 的签名绕过漏洞**:
1. 移除 `parseWorkflowEngineToken()` 方法中对 3 部分 JWT token 不验证签名的逻辑
2. 对于标准 JWT token（3 部分），必须使用 `Jwts.parser().verifyWith(getSigningKey())` 验证签名
3. 保留 2 部分自定义 token 格式的解析逻辑（用于 workflow-engine 兼容）

---

**File**: `backend/developer-workstation/src/main/java/com/developer/security/DeveloperPermissionInterceptor.java`

**Change A8 — DeveloperPermissionInterceptor 优先从 SecurityContext 获取 userId**:
1. 修改 `getUserIdFromRequest()` 方法，优先从 `SecurityContextUtils.getCurrentUser()` 获取 userId
2. 回退到 `X-User-Id` 头（用于兼容）
3. 移除 `authentication.getName()` 回退（因为现在 principal 是 `UserPrincipal`，`getName()` 返回的是 username 而非 userId）

---

**File**: `backend/developer-workstation/src/main/java/com/developer/DeveloperWorkstationApplication.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/security/DatabasePermissionEvaluator.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/security/UserContextService.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/component/impl/ExportImportComponentImpl.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/component/impl/FunctionUnitComponentImpl.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/component/impl/VersionComponentImpl.java`

**Change A9 — 修复 authentication.getName() 回归风险**:
修复后 principal 从 String 变为 `UserPrincipal`，`authentication.getName()` 不再返回 username 字符串。所有使用 `authentication.getName()` 获取 username 的代码必须改为使用 `SecurityContextUtils.getCurrentUsername()` 或从 `UserPrincipal` 直接获取。

具体修改：
1. `DeveloperWorkstationApplication.auditorProvider()`: 将 `authentication.getName()` 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
2. `DatabasePermissionEvaluator`: 将 `authentication.getName()` 改为 `SecurityContextUtils.getCurrentUsername().orElse(null)`
3. `UserContextService`: 将 `authentication.getName()` 改为 `SecurityContextUtils.getCurrentUsername().orElse(null)`
4. `ExportImportComponentImpl`: 将 `authentication.getName()` 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
5. `FunctionUnitComponentImpl`: 将 `authentication.getName()` 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
6. `VersionComponentImpl`: 将 `authentication.getName()` 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`

### 修复组 B：修复前端 API 路径（C5, C6, C7, C8）

**File**: `frontend/developer-workstation/src/api/user.ts`

**Change B1 — 修复 developer-workstation user.ts baseURL**:
1. 将 `adminCenterAxios` 的 `baseURL` 从 `/api/admin-center` 改为 `/api/v1/admin`

---

**File**: `frontend/developer-workstation/src/api/adminCenter.ts`

**Change B2 — 修复 developer-workstation adminCenter.ts baseURL**:
1. 将 `adminCenterAxios` 的 `baseURL` 从 `/api/admin-center` 改为 `/api/v1/admin`

---

**File**: `frontend/user-portal/src/api/user.ts`

**Change B3 — 修复 user-portal user.ts baseURL**:
1. 将 `adminCenterAxios` 的 `baseURL` 从 `/api/admin-center` 改为 `/api/v1/admin`

---

**File**: `frontend/user-portal/src/api/auth.ts`

**Change B4 — 修复 user-portal auth.ts baseURL**:
1. 将 `authRequest` 的 `baseURL` 从 `/api/v1/auth` 改为 `/api/portal/auth`

### 修复组 C：修复用户身份传递（C9, C10, C11）

**File**: `frontend/user-portal/src/api/request.ts`

**Change C1 — 移除 X-User-Id 硬编码回退**:
1. 将 `config.headers['X-User-Id'] = userId || 'user_1'` 改为仅在 userId 存在时设置头部：
   ```typescript
   if (userId) {
     config.headers['X-User-Id'] = userId
   }
   ```

---

**File**: `backend/user-portal/src/main/java/com/portal/controller/UserPermissionController.java`

**Change C2 — UserPermissionController 优先从 SecurityContext 获取 userId**:
1. 修改 `getMyPermissions()` 等方法，优先从 `SecurityContextUtils.getCurrentUserId()` 获取 userId
2. 回退到 `@RequestHeader("X-User-Id")` 参数（改为 `required = false`）
3. 如果两者都为空，返回 401

---

**Change C3 — UserPermissionController RestTemplate 调用携带认证信息**:
1. 在 `getUserRoles()`、`getUserVirtualGroups()`、`getUserBusinessUnits()` 等方法中，创建 `HttpHeaders` 并设置 `X-User-Id` 和 `X-Username` 头（从 SecurityContext 获取）
2. 使用 `HttpEntity<>(headers)` 替代 `null` 作为 `restTemplate.exchange()` 的第三个参数

### 修复组 D：修复安全漏洞（C13, C14）

**File**: `backend/user-portal/src/main/java/com/portal/controller/AuthController.java`
**File**: `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java`

**Change D1 — logout 端点失效 token**:
1. 在 user-portal 和 developer-workstation 的 `logout()` 方法中，从 `Authorization` 头提取 token
2. 在 admin-center 的 `AuthServiceImpl.logout()` 中，调用 `JwtTokenService.blacklistToken(token)` 替代当前的空实现
3. 注入 `JwtTokenService`（通过 Change A2 已可用）
4. 调用 `blacklistToken(token)` 将 token 加入 Redis 黑名单

注意：admin-center 的 `AuthController` 来自 platform-security 模块（`com.platform.security.controller.AuthController`），其 logout 实现需要单独检查。但由于三个模块的 `@ComponentScan` 都排除了 `com.platform.security.controller.*`，admin-center 使用的是自己的 AuthController（如果有的话）或 platform-security 的。需要进一步确认。

---

**File**: `backend/user-portal/src/main/java/com/portal/config/WebSocketAuthInterceptor.java`

**Change D2 — WebSocketAuthInterceptor 统一使用 JwtTokenService**:
1. 注入 `JwtTokenService`（通过 Change A2 已可用）
2. 使用 `jwtTokenService.validateToken(token)` 和 `jwtTokenService.extractUserPrincipal(token)` 替代直接使用 `io.jsonwebtoken` 库
3. 移除 `@Value("${jwt.secret}")` 字段和手动密钥创建逻辑

### 修复组 E：admin-center 前端 request.ts X-User-Id 回退值改进

**File**: `frontend/admin-center/src/api/request.ts`

**Change E1 — admin-center request.ts X-User-Id 回退值改进**:
1. 将 `const userId = localStorage.getItem('userId') || 'system'` 改为仅在 userId 存在时设置头部
2. 与 user-portal 的修复保持一致的模式

## Testing Strategy

### Validation Approach

测试策略分两阶段：首先在未修复代码上运行探索性测试确认 bug 存在，然后在修复后验证 bug 已解决且现有行为未被破坏。

### Exploratory Bug Condition Checking

**Goal**: 在实施修复之前，通过测试确认 bug 的存在并验证根因分析。

**Test Plan**: 编写测试模拟各 bug 条件下的请求，在未修复代码上运行观察失败。

**Test Cases**:
1. **C1 user-portal JWT 测试**: 发送带有效 JWT token 的请求到 user-portal，验证 `SecurityContextHolder` 中无认证信息（将在未修复代码上失败）
2. **C2 developer-workstation principal 类型测试**: 发送带 JWT token 的请求到 developer-workstation，验证 `authentication.getPrincipal() instanceof String`（将在未修复代码上失败）
3. **C4 签名绕过测试**: 构造一个签名无效但 payload 包含 `sub` 字段的 JWT token，验证 `SecurityComponentImpl.validateToken()` 返回 true（将在未修复代码上失败）
4. **C5/C6/C7 Kong 路由测试**: 验证 `/api/admin-center/users/{userId}/business-units` 路径在 Kong 中无匹配路由（将在未修复代码上失败）
5. **C8 auth baseURL 测试**: 验证 user-portal 前端 auth 请求被路由到 developer-workstation（将在未修复代码上失败）
6. **C9 X-User-Id 回退测试**: 验证当 localStorage 无 userId 时，请求头包含硬编码的 `user_1`（将在未修复代码上失败）
7. **C12 token issuer 测试**: 验证 AuthController 生成的 token 不包含 `iss` claim（将在未修复代码上失败）
8. **C13 logout token 失效测试**: 验证 logout 后旧 token 仍然有效（将在未修复代码上失败）

**Expected Counterexamples**:
- C1: `SecurityContextUtils.getCurrentUser()` 返回 `Optional.empty()`
- C2: `authentication.getPrincipal() instanceof String` 为 true
- C4: `SecurityComponentImpl.validateToken(forgedToken)` 返回 true
- C5/C6/C7: Kong 返回 404 或无匹配路由错误
- C8: 登录请求到达 developer-workstation 的 AuthController
- C9: 后端收到 `X-User-Id: user_1`
- C12: token 的 `iss` claim 为 null
- C13: logout 后旧 token 仍通过验证

### Fix Checking

**Goal**: 验证对于所有触发 bug 条件的输入，修复后的代码产生期望行为。

**Pseudocode:**
```
FOR ALL request WHERE isBugCondition(request) DO
  result := processRequest_fixed(request)
  ASSERT expectedBehavior(result)
END FOR
```

```
FUNCTION expectedBehavior(result)
  // C1: user-portal JWT token 被正确解析
  IF result.bugType == C1:
    ASSERT SecurityContextHolder.getContext().getAuthentication() != null
    ASSERT authentication.getPrincipal() instanceof UserPrincipal
    ASSERT principal.getUserId() == expectedUserId
  
  // C2: developer-workstation principal 类型正确
  IF result.bugType == C2:
    ASSERT authentication.getPrincipal() instanceof UserPrincipal
    ASSERT principal.getUserId() == expectedUserId
    ASSERT principal.getRoles() IS NOT EMPTY
    ASSERT principal.getPermissions() IS NOT EMPTY
  
  // C3: admin-center 使用标准 JWT 过滤器
  IF result.bugType == C3:
    ASSERT blacklistedToken IS rejected
    // issuer 验证初始关闭，后续开启
  
  // C4: 签名绕过被修复
  IF result.bugType == C4:
    ASSERT SecurityComponentImpl.validateToken(forgedToken) == false
  
  // C5/C6/C7: 前端请求正确到达 admin-center
  IF result.bugType IN [C5, C6, C7]:
    ASSERT result.response.status == 200
    ASSERT result.response.body CONTAINS user data
  
  // C8: auth 请求到达正确后端
  IF result.bugType == C8:
    ASSERT result.targetBackend == "user-portal"
  
  // C9: 不使用硬编码 userId
  IF result.bugType == C9:
    ASSERT request.header("X-User-Id") != "user_1"
  
  // C10: 回退 principal 有认证信息
  IF result.bugType == C10:
    ASSERT principal.getUserId() IS NOT null
  
  // C11: 服务间调用携带认证头
  IF result.bugType == C11:
    ASSERT request.header("X-User-Id") IS NOT null
  
  // C12: token 包含标准 claims
  IF result.bugType == C12:
    // 初始阶段不要求 issuer（validate-issuer: false）
    ASSERT token.jti IS NOT null OR token IS generated by JwtTokenService
  
  // C13: logout 后 token 失效
  IF result.bugType == C13:
    ASSERT JwtTokenService.isBlacklisted(token) == true
    ASSERT JwtTokenService.validateToken(token) == false
  
  // C14: WebSocket 使用 JwtTokenService
  IF result.bugType == C14:
    ASSERT WebSocketAuthInterceptor uses JwtTokenService
END FUNCTION
```

### Preservation Checking

**Goal**: 验证对于所有不触发 bug 条件的输入，修复后的代码与原始代码产生相同结果。

**Pseudocode:**
```
FOR ALL request WHERE NOT isBugCondition(request) DO
  ASSERT processRequest_original(request) = processRequest_fixed(request)
END FOR
```

**Testing Approach**: 属性测试推荐用于保持性检查，因为：
- 自动生成大量测试用例覆盖输入域
- 捕获手动单元测试可能遗漏的边界情况
- 对所有非 bug 输入的行为不变提供强保证

**Test Plan**: 先在未修复代码上观察正常路径的行为，然后编写属性测试验证修复后行为一致。

**Test Cases**:
1. **登录流程保持**: 验证 admin-center 和 developer-workstation 的登录流程在修复后继续正常工作
2. **Token 刷新保持**: 验证三个模块的 token refresh 机制在修复后继续正常工作
3. **Kong 已有路由保持**: 验证 `/api/v1/admin`、`/api/v1`、`/api/portal` 路由在修复后继续正确转发
4. **admin-center JWT 成功路径保持**: 验证带有效 JWT token 的请求到 admin-center 继续正确解析
5. **developer-workstation 权限检查保持**: 验证 `DeveloperPermissionInterceptor` 和 `DatabasePermissionEvaluator` 在修复后继续正常工作
6. **admin-center 管理操作保持**: 验证用户/角色/权限管理操作在修复后继续正常工作
7. **前端路由守卫保持**: 验证三个前端应用的路由守卫在修复后继续正常工作

### Unit Tests

- 测试 platform-security `JwtAuthenticationFilter` 在 user-portal SecurityConfig 注册后，带有效 JWT 的请求能正确设置 SecurityContext
- 测试 platform-security `JwtAuthenticationFilter` 在 user-portal SecurityConfig 注册后，无 JWT 的请求 SecurityContext 为空
- 测试 platform-security `JwtAuthenticationFilter` 在 developer-workstation SecurityConfig 注册后，principal 为 `UserPrincipal` 类型
- 测试 platform-security `JwtAuthenticationFilter` 拒绝黑名单中的 token
- 测试 `SecurityComponentImpl.validateToken()` 拒绝签名无效的 JWT token
- 测试 `DeveloperPermissionInterceptor.getUserIdFromRequest()` 优先从 SecurityContext 获取 userId
- 测试 `UserPermissionController` 优先从 SecurityContext 获取 userId
- 测试 `UserPermissionController` RestTemplate 调用携带 X-User-Id 头
- 测试 user-portal `logout()` 将 token 加入黑名单
- 测试 developer-workstation `logout()` 将 token 加入黑名单
- 测试 `WebSocketAuthInterceptor` 使用 `JwtTokenService` 验证 token
- 测试前端 request.ts 在无 userId 时不设置 X-User-Id 头

### Property-Based Tests

- 生成随机 JWT token claims（userId、username、roles、permissions），验证 platform-security `JwtAuthenticationFilter` 正确解析并设置 `UserPrincipal`
- 生成随机 HTTP 请求（有/无 JWT token、有/无 X-User-Id 头），验证 admin-center `ServiceCallAuthenticationFilter` 回退逻辑的行为正确
- 生成随机用户身份信息，验证三个模块的 `JwtAuthenticationFilter` 构建的 `UserPrincipal` 与输入一致
- 生成随机 JWT token（有效/无效签名/过期/黑名单），验证 `SecurityComponentImpl.validateToken()` 的行为正确

### Integration Tests

- 端到端测试：user-portal 前端登录（`/api/portal/auth/login`）→ 获取 token → 请求 `/my-permissions` → 验证返回正确用户数据
- 端到端测试：developer-workstation 前端登录 → 请求用户 Business Units/Virtual Groups/Roles（通过 `/api/v1/admin/users/{userId}/...`）→ 验证数据正确
- 端到端测试：user-portal 后端通过 RestTemplate 调用 admin-center API（携带认证头）→ 验证请求正确到达并返回数据
- 端到端测试：用户注销 → 验证旧 token 被拒绝
- 端到端测试：developer-workstation 前端 adminCenter.ts 通过 `/api/v1/admin/...` 获取虚拟组、业务单元、角色数据 → 验证数据正确

## Known Issues (Out of Scope)

以下问题在本次审查中发现，但不在本次 bugfix 范围内，记录供后续修复。

### KI-1: admin-center `AuthController.refresh()` 返回 refreshToken 而非新 accessToken

**文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java`（第 73-88 行）

`refresh()` 方法返回 `Map.of("accessToken", refreshToken, ...)` — 注释写着"简化实现"，实际上把传入的 refreshToken 原样返回作为 accessToken，没有生成新的 access token。

**影响**: admin-center 的 token refresh 功能实际上不工作（返回的 accessToken 是 refreshToken）。

### KI-2: user-portal 和 developer-workstation 后端缺少 `/auth/refresh` 端点

**文件**:
- `backend/user-portal/src/main/java/com/portal/controller/AuthController.java` — 只有 `/auth/login`、`/auth/logout`、`/auth/me`、`/auth/validate`
- `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java` — 同上

Kong 配置了 `portal-auth-refresh-route`（`/api/portal/auth/refresh`）和 `dw-auth-refresh-route`（`/api/v1/auth/refresh`），但后端没有对应的 Controller 方法。

**影响**: 三个前端应用的 token refresh 功能都不工作。admin-center 有端点但实现是假的（KI-1），user-portal 和 developer-workstation 完全没有端点。

### KI-3: developer-workstation `user.ts` 的 `changePassword` 调用不存在的端点

**文件**: `frontend/developer-workstation/src/api/user.ts`

`changePassword` 调用 `adminCenterAxios.post('/auth/change-password', data)`，但 admin-center 后端没有 `/auth/change-password` 端点。

**影响**: developer-workstation 的修改密码功能不工作。

### KI-4: platform-security `JwtAuthenticationFilter.shouldNotFilter()` 路径对所有模块无效

**文件**: `backend/platform-security/src/main/java/com/platform/security/filter/JwtAuthenticationFilter.java`

`shouldNotFilter()` 使用 `path.startsWith("/api/auth/")`，但 `getRequestURI()` 返回包含 context-path 的完整路径：
- admin-center: `/api/v1/admin/auth/login` — 不匹配
- developer-workstation: `/api/v1/auth/login` — 不匹配
- user-portal: `/api/portal/auth/login` — 不匹配

**影响**: `shouldNotFilter` 中的 `/api/auth/` 排除规则对所有模块都不生效。不影响功能（auth 端点不携带 JWT token，过滤器在无 Authorization 头时直接跳过），但是无效代码。建议改为 `path.contains("/auth/login")` 或使用 `getServletPath()` 替代 `getRequestURI()`。

### KI-5: `JwtTokenServiceImpl` 密钥创建不做 padding，与各模块 AuthController 不一致

**文件**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`

构造函数直接调用 `Keys.hmacShaKeyFor(secret.getBytes())`，不做 padding。而各模块 AuthController 的 `getSigningKey()` 对 < 32 字节的密钥做 `Arrays.copyOf(keyBytes, 32)` padding。

**影响**: 如果 `JWT_SECRET` 环境变量值 < 32 字节，`JwtTokenServiceImpl` 会抛 `WeakKeyException`，而 AuthController 正常工作。默认开发密钥（46 字节）不受影响。生产环境必须确保 `JWT_SECRET` ≥ 32 字节。

### KI-6: `JwtTokenServiceImpl.hashToken()` 使用 `Integer.toHexString(token.hashCode())` 做黑名单 key

**文件**: `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`

`hashToken()` 使用 Java `String.hashCode()` 生成 token 的 Redis 黑名单 key。`hashCode()` 有较高的碰撞概率（32-bit），可能导致不同 token 映射到相同的 key，造成误判（合法 token 被误认为已黑名单）。

**影响**: 低概率的 token 黑名单误判。建议改为 SHA-256 哈希。

### KI-7: user-portal 中 12+ 处 `new RestTemplate()` 直接创建实例

**文件**:
- `backend/user-portal/src/main/java/com/portal/component/ProcessComponent.java`（7 处）
- `backend/user-portal/src/main/java/com/portal/component/ProcessFormComponent.java`（2 处）
- `backend/user-portal/src/main/java/com/portal/component/ProcessDraftComponent.java`（1 处）
- `backend/user-portal/src/main/java/com/portal/component/TaskFormComponent.java`（1 处）

这些组件直接 `new RestTemplate()` 创建实例调用 admin-center/developer-workstation API，不使用 Spring 管理的 bean，不携带认证头，不复用连接池。

**影响**: 每次调用都创建新的 HTTP 连接，性能差。不携带认证头（当前因 `permitAll()` 不影响功能，但不符合安全最佳实践）。建议统一使用 Spring 管理的 `RestTemplate` bean 并配置拦截器自动添加认证头。

### KI-8: user-portal 多个 Controller 使用 `@RequestHeader("X-User-Id")` required=true

**文件**:
- `TaskController.java` — `claimTask`、`unclaimTask`、`completeTask`、`delegateTask`、`transferTask`、`urgeTask`、`batchUrgeTasks`、`getTaskStatistics`
- `MemberController.java` — `getVirtualGroupMembers`、`getBusinessUnitMembers`、`removeVirtualGroupMember`、`removeBusinessUnitRole`、`getApprovalScope`
- `ProcessController.java` — `startProcess`、`getMyApplications`
- `ProcessFormController.java` — `submitTaskForm`
- `TaskFormController.java` — `submitTaskForm`

这些端点都使用 `@RequestHeader("X-User-Id") String userId`（`required = true`），依赖前端通过请求头传递用户身份，而非从 JWT SecurityContext 获取。

**影响**: 架构不一致。应统一从 SecurityContext 获取用户身份，`X-User-Id` 头作为可选回退。本次修复只改了 `UserPermissionController`（直接影响用户信息面板的端点），其他 Controller 留待后续统一整改。

### KI-9: admin-center `AuthController` 暴露调试端点

**文件**: `backend/admin-center/src/main/java/com/admin/controller/AuthController.java`

`/auth/test-password` 和 `/auth/generate-hash` 端点用于调试，不应在生产环境暴露。当前没有环境条件判断或 `@Profile("dev")` 注解限制。

**影响**: 安全风险。攻击者可以使用 `/auth/generate-hash` 生成密码哈希，或使用 `/auth/test-password` 验证密码。
