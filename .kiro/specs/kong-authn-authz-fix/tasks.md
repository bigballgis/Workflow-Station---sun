# Implementation Plan

- [x] 1. Write bug condition exploration tests (BEFORE implementing fix)
  - **Property 1: Bug Condition** - JWT 认证架构不一致导致认证失败
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **NOTE**: This test encodes the expected behavior - it will validate the fix when it passes after implementation
  - **GOAL**: Surface counterexamples that demonstrate the bugs exist across all modules
  - **Scoped PBT Approach**: Use jqwik to generate random JWT claims (userId, username, roles, permissions) and verify behavior
  - Test C1: user-portal `SecurityConfig` 未注册 `JwtAuthenticationFilter` — 发送带有效 JWT token 的请求，断言 `SecurityContextHolder` 中有 `Authentication` 且 `principal instanceof UserPrincipal`（未修复代码上将失败：SecurityContext 为空）
  - Test C2: developer-workstation 自定义 `JwtAuthenticationFilter` 设置 String principal — 断言 `authentication.getPrincipal() instanceof UserPrincipal`（未修复代码上将失败：principal 是 String）
  - Test C4: `SecurityComponentImpl.validateToken()` 签名绕过 — 构造签名无效但 payload 含 `sub` 的 JWT token，断言 `validateToken()` 返回 false（未修复代码上将失败：返回 true）
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found (e.g., "SecurityContextUtils.getCurrentUser() returns Optional.empty()", "principal instanceof String == true", "validateToken(forgedToken) == true")
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.3, 1.4, 2.3_

- [x] 2. Write bug condition exploration tests - frontend API paths (BEFORE implementing fix)
  - **Property 2: Bug Condition** - 前端 API 路径与 Kong 路由不匹配
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **GOAL**: Surface counterexamples that demonstrate the frontend path bugs exist
  - **Scoped PBT Approach**: Use fast-check to generate arbitrary userId strings and verify API paths
  - Test C5/C6/C7: 验证 developer-workstation `user.ts` 和 `adminCenter.ts`、user-portal `user.ts` 的 `baseURL` 为 `/api/v1/admin`（未修复代码上将失败：baseURL 为 `/api/admin-center`）
  - Test C8: 验证 user-portal `auth.ts` 的 `baseURL` 为 `/api/portal/auth`（未修复代码上将失败：baseURL 为 `/api/v1/auth`）
  - Test C9: 验证 user-portal `request.ts` 在无 userId 时不设置 `X-User-Id` 头（未修复代码上将失败：回退为 `'user_1'`）
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found (e.g., "baseURL is '/api/admin-center' instead of '/api/v1/admin'", "X-User-Id header is 'user_1'")
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.1, 1.2_

- [x] 3. Write bug condition exploration tests - security vulnerabilities (BEFORE implementing fix)
  - **Property 3: Bug Condition** - 安全漏洞（logout 不失效 token、服务间调用无认证）
  - **CRITICAL**: This test MUST FAIL on unfixed code - failure confirms the bug exists
  - **DO NOT attempt to fix the test or the code when it fails**
  - **GOAL**: Surface counterexamples that demonstrate the security bugs exist
  - **Scoped PBT Approach**: Use jqwik to generate random token strings and verify blacklist behavior
  - Test C11: 验证 `UserPermissionController` RestTemplate 调用 admin-center 时 `HttpEntity` 携带认证头（未修复代码上将失败：HttpEntity 为 null）
  - Test C13: 验证 logout 端点调用 `JwtTokenService.blacklistToken()`（未修复代码上将失败：logout 直接返回 200 不做任何操作）
  - Test C12: 验证 AuthController 生成的 token 包含标准 claims（未修复代码上将失败：token 缺少 issuer/jti）
  - Run tests on UNFIXED code
  - **EXPECTED OUTCOME**: Tests FAIL (this is correct - it proves the bugs exist)
  - Document counterexamples found
  - Mark task complete when tests are written, run, and failures are documented
  - _Requirements: 1.2, 1.3, 1.4_

- [x] 4. Write preservation property tests (BEFORE implementing fix)
  - **Property 4: Preservation** - 现有登录、路由和权限检查流程不变
  - **IMPORTANT**: Follow observation-first methodology
  - **Observe on UNFIXED code**:
  - Observe: admin-center 前端 `request.ts` 的 `baseURL` 为 `/api/v1/admin` — 不受修复影响
  - Observe: developer-workstation 前端 `index.ts` 的 `baseURL` 为 `/api/v1` — 不受修复影响
  - Observe: admin-center `SecurityConfig` 的 `permitAll()` 路径列表（`/auth/**`, `/api-docs/**` 等）— 不受修复影响
  - Observe: developer-workstation `SecurityConfig` 的 `permitAll()` 路径列表 — 不受修复影响
  - Observe: developer-workstation `DeveloperPermissionInterceptor` + `DeveloperPermissionChecker` 权限检查逻辑 — 不受修复影响
  - Observe: developer-workstation `SecurityPermissionConfig` + `DatabasePermissionEvaluator` 方法级安全 — 不受修复影响
  - Observe: Kong 已有路由（`/api/v1/admin`, `/api/v1`, `/api/portal`）配置 — 不受修复影响
  - Write property-based tests:
  - (jqwik) For all valid JWT claims, admin-center `JwtAuthenticationFilter` 成功解析后 `UserPrincipal` 字段与 claims 一致（验证修复后标准过滤器行为与原有成功路径一致）
  - (jqwik) For all valid permission check requests, `DeveloperPermissionInterceptor` 从 SecurityContext 获取 userId 后权限检查结果与原有逻辑一致
  - (fast-check) For all admin-center frontend API calls, `baseURL` 保持为 `/api/v1/admin`
  - (fast-check) For all developer-workstation frontend API calls (non-adminCenter), `baseURL` 保持为 `/api/v1`
  - Verify tests pass on UNFIXED code
  - **EXPECTED OUTCOME**: Tests PASS (this confirms baseline behavior to preserve)
  - Mark task complete when tests are written, run, and passing on unfixed code
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 5. Fix Group A: 统一后端 JWT 认证架构（C1, C2, C3, C4, C12, C15）

  - [x] 5.1 统一 JWT 配置属性命名（Change A1）
    - 在 `backend/admin-center/src/main/resources/application.yml` 添加 `platform.security.jwt` 配置节
    - 在 `backend/developer-workstation/src/main/resources/application.yml` 添加 `platform.security.jwt` 配置节
    - 在 `backend/user-portal/src/main/resources/application.yml` 添加 `platform.security.jwt` 配置节
    - 配置 `secret`, `expiration-ms`, `refresh-expiration-ms`, `issuer: platform`, `validate-issuer: false`
    - 保留现有 `jwt.secret` 配置（向后兼容）
    - **密钥兼容性风险**: `JwtTokenServiceImpl` 构造函数直接调用 `Keys.hmacShaKeyFor(secret.getBytes())` 不做 padding，而各模块 AuthController 的 `getSigningKey()` 对 < 32 字节的密钥做 `Arrays.copyOf(keyBytes, 32)` padding。如果 `JWT_SECRET` 环境变量值 < 32 字节，`JwtTokenServiceImpl` 会抛 `WeakKeyException`。默认开发密钥 `your-256-bit-secret-key-for-development-only`（46 字节）不受影响。生产环境必须确保 `JWT_SECRET` ≥ 32 字节
    - _Bug_Condition: C15 — JWT 配置属性命名不统一，platform-security 的 JwtProperties 无法读取配置_
    - _Expected_Behavior: JwtProperties 能正确读取 platform.security.jwt.* 配置_
    - _Preservation: 现有 @Value("${jwt.secret}") 引用不受影响_
    - _Requirements: 2.3, 2.4_

  - [x] 5.2 修改 ComponentScan 允许 platform-security JWT 组件被扫描（Change A2）
    - 修改 `backend/admin-center/src/main/java/com/admin/AdminCenterApplication.java` 的 `@ComponentScan`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/DeveloperWorkstationApplication.java` 的 `@ComponentScan`
    - 修改 `backend/user-portal/src/main/java/com/portal/UserPortalApplication.java` 的 `@ComponentScan`
    - 在 `basePackages` 中添加 `"com.platform.security.filter"`
    - 修改 `excludeFilters` 正则允许 `JwtTokenServiceImpl` 被扫描
    - 修改 `excludeFilters` 正则允许 `JwtProperties` 被扫描
    - _Bug_Condition: C1, C2, C3 — ComponentScan 排除了 platform-security JWT 组件_
    - _Expected_Behavior: JwtAuthenticationFilter, JwtTokenServiceImpl, JwtProperties 被 Spring 容器扫描和注册_
    - _Preservation: 其他被排除的 platform-security 组件仍然被排除_
    - _Requirements: 2.3_

  - [x] 5.3 修复 JwtTokenServiceImpl.extractUserPrincipal() 的 userId 兼容性（新增）
    - **CRITICAL**: 各模块 AuthController 生成的 JWT token 将 userId 存储在 `sub` (subject) claim 中，没有单独的 `userId` claim。但 `JwtTokenServiceImpl.extractUserPrincipal()` 从 `claims.get("userId", String.class)` 获取 userId，不回退到 `claims.getSubject()`。这导致解析 AuthController 生成的 token 时 `UserPrincipal.userId` 为 null
    - 修改 `backend/platform-security/src/main/java/com/platform/security/service/impl/JwtTokenServiceImpl.java`
    - 在 `extractUserPrincipal()` 方法中，`userId` 获取逻辑改为：先从 `claims.get("userId")` 获取，如果为 null 则回退到 `claims.getSubject()`
    - 同样修改 `extractUserId()` 方法添加相同的回退逻辑
    - _Bug_Condition: AuthController 生成的 token 没有 `userId` claim，只有 `sub` claim_
    - _Expected_Behavior: extractUserPrincipal() 能正确从 AuthController 生成的 token 中提取 userId_
    - _Preservation: JwtTokenServiceImpl.generateToken() 生成的 token（包含 `userId` claim）仍然正确解析_
    - _Requirements: 2.3, 2.4_

  - [x] 5.4 user-portal SecurityConfig 注册 JwtAuthenticationFilter（Change A3）
    - 修改 `backend/user-portal/src/main/java/com/portal/config/SecurityConfig.java`
    - 注入 `com.platform.security.filter.JwtAuthenticationFilter`
    - 调用 `addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)`
    - 保留现有 `csrf.disable()` + `sessionManagement.STATELESS` + `permitAll()` 配置
    - 注意：platform-security 的 `shouldNotFilter()` 使用 `/api/auth/` 路径，但 user-portal 的 context-path 是 `/api/portal`，实际 auth 路径为 `/api/portal/auth/...`。`shouldNotFilter` 不会匹配，但不影响功能，因为 auth 端点（login）不携带 JWT token，过滤器会在无 Authorization 头时直接跳过
    - _Bug_Condition: C1 — user-portal SecurityConfig 未注册 JwtAuthenticationFilter_
    - _Expected_Behavior: JWT token 被解析，UserPrincipal 设置到 SecurityContextHolder_
    - _Preservation: 现有 permitAll 路径和 CORS 配置不变_
    - _Requirements: 2.3_

  - [x] 5.5 admin-center SecurityConfig 替换内联 JWT 过滤器（Change A4）
    - 修改 `backend/admin-center/src/main/java/com/admin/config/SecurityConfig.java`
    - 注入 `com.platform.security.filter.JwtAuthenticationFilter`
    - 移除内联 `jwtAuthenticationFilter()` bean 方法
    - 移除 `@Value("${jwt.secret}")` 字段和 `getSigningKey()` 方法
    - 在 `securityFilterChain()` 中使用注入的 `jwtAuthenticationFilter`
    - 保留 `PasswordEncoder` bean 和 `AuditRequestFilter` bean
    - 添加 `ServiceCallAuthenticationFilter` 处理 X-Username/X-User-Id 回退（在 JwtAuthenticationFilter 之后执行），回退逻辑需保留原有行为：排除 `xUserId == "system"` 的情况，roles 和 permissions 设为空列表
    - 注意：原内联过滤器的 `shouldNotFilter` 使用 `path.contains("/actuator/")` 匹配 `/api/v1/admin/actuator/...`，而 platform-security 标准过滤器使用 `path.startsWith("/actuator/")`。由于 actuator 端点不携带 JWT token，过滤器会在无 Authorization 头时直接跳过，不影响功能
    - _Bug_Condition: C3, C10 — admin-center 内联 JWT 过滤器不检查黑名单，回退 principal 无 roles/permissions_
    - _Expected_Behavior: 使用 platform-security 标准 JwtAuthenticationFilter，ServiceCallAuthenticationFilter 处理服务间调用回退_
    - _Preservation: PasswordEncoder, AuditRequestFilter, permitAll 路径不变_
    - _Requirements: 2.3, 2.4_

  - [x] 5.6 developer-workstation: 删除自定义 JwtAuthenticationFilter 并替换为 platform-security 标准实现（Change A5 + A6）
    - **CRITICAL 执行顺序**: 必须在同一步完成以下两个操作，否则会出现 bean 冲突（两个 `@Component` 类都叫 `JwtAuthenticationFilter`，Spring 会报 `ConflictingBeanDefinitionException`）
    - 步骤 1: 删除 `backend/developer-workstation/src/main/java/com/developer/security/JwtAuthenticationFilter.java`
    - 步骤 2: 修改 `backend/developer-workstation/src/main/java/com/developer/config/SecurityConfig.java`，将注入的 `com.developer.security.JwtAuthenticationFilter` 改为 `com.platform.security.filter.JwtAuthenticationFilter`
    - 确认无其他文件引用 `com.developer.security.JwtAuthenticationFilter`（grepSearch 验证）
    - 保留 CORS 配置和 `PasswordEncoder` bean
    - _Bug_Condition: C2 — developer-workstation 自定义 JwtAuthenticationFilter 设置 String principal，且与 platform-security 标准实现 bean 名称冲突_
    - _Expected_Behavior: 使用 platform-security 标准 JwtAuthenticationFilter，principal 为 UserPrincipal 类型_
    - _Preservation: CORS 配置、PasswordEncoder、permitAll 路径不变_
    - _Requirements: 2.3_

  - [x] 5.7 修复 SecurityComponentImpl 签名绕过漏洞（Change A7）
    - 修改 `backend/developer-workstation/src/main/java/com/developer/component/impl/SecurityComponentImpl.java`
    - 修改 `parseWorkflowEngineToken()` 方法：对 3 部分 JWT token 必须验证签名
    - 保留 2 部分自定义 token 格式的解析逻辑（workflow-engine 兼容）
    - _Bug_Condition: C4 — parseWorkflowEngineToken 对标准 JWT 不验证签名_
    - _Expected_Behavior: 签名无效的 JWT token 被拒绝，validateToken() 返回 false_
    - _Preservation: 2 部分 workflow-engine 自定义 token 格式仍然被支持_
    - _Requirements: 2.3, 2.4_

  - [x] 5.8 DeveloperPermissionInterceptor 优先从 SecurityContext 获取 userId（Change A8）
    - 修改 `backend/developer-workstation/src/main/java/com/developer/security/DeveloperPermissionInterceptor.java`
    - 修改 `getUserIdFromRequest()` 方法：优先从 `SecurityContextUtils.getCurrentUser()` 获取 userId
    - 回退到 `X-User-Id` 头（兼容）
    - 移除 `authentication.getName()` 回退
    - _Bug_Condition: C2 — getUserIdFromRequest 从 authentication.getName() 获取的是 username 而非 userId_
    - _Expected_Behavior: 优先从 SecurityContext 的 UserPrincipal 获取 userId_
    - _Preservation: DeveloperPermissionChecker 和 DatabasePermissionEvaluator 权限检查逻辑不变_
    - _Requirements: 2.4_

  - [x] 5.9 修复 authentication.getName() 回归风险（Change A9）
    - **CRITICAL**: 修复后 principal 从 String 变为 UserPrincipal，`authentication.getName()` 不再返回 username 字符串，而是返回 Lombok toString()。所有使用 `authentication.getName()` 获取 username 的代码必须同步修改
    - 修改 `backend/developer-workstation/src/main/java/com/developer/DeveloperWorkstationApplication.java` — `auditorProvider()` 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/security/DatabasePermissionEvaluator.java` — 改为 `SecurityContextUtils.getCurrentUsername().orElse(null)`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/security/UserContextService.java` — 改为 `SecurityContextUtils.getCurrentUsername().orElse(null)`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/component/impl/ExportImportComponentImpl.java` — 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/component/impl/FunctionUnitComponentImpl.java` — 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/component/impl/VersionComponentImpl.java` — 改为 `SecurityContextUtils.getCurrentUsername().orElse("system")`
    - _Bug_Condition: C16 — authentication.getName() 在 principal 变为 UserPrincipal 后返回 toString() 而非 username_
    - _Expected_Behavior: 所有获取 username 的代码使用 SecurityContextUtils.getCurrentUsername()_
    - _Preservation: JPA 审计记录、权限检查、用户上下文获取的功能行为不变_
    - _Requirements: 2.4, 3.2, 3.5_

- [x] 6. Fix Group B: 修复前端 API 路径（C5, C6, C7, C8）

  - [x] 6.1 修复 developer-workstation user.ts baseURL（Change B1）
    - 修改 `frontend/developer-workstation/src/api/user.ts`
    - 将 `adminCenterAxios` 的 `baseURL` 从 `/api/admin-center` 改为 `/api/v1/admin`
    - _Bug_Condition: C5 — baseURL `/api/admin-center` 在 Kong 中无匹配路由_
    - _Expected_Behavior: 请求通过 Kong `/api/v1/admin` 路由正确到达 admin-center 后端_
    - _Preservation: 其他 axios 实例的 baseURL 不变_
    - _Requirements: 2.1_

  - [x] 6.2 修复 developer-workstation adminCenter.ts baseURL（Change B2）
    - 修改 `frontend/developer-workstation/src/api/adminCenter.ts`
    - 将 `adminCenterAxios` 的 `baseURL` 从 `/api/admin-center` 改为 `/api/v1/admin`
    - _Bug_Condition: C6 — baseURL `/api/admin-center` 在 Kong 中无匹配路由_
    - _Expected_Behavior: 请求通过 Kong `/api/v1/admin` 路由正确到达 admin-center 后端_
    - _Preservation: 其他 axios 实例的 baseURL 不变_
    - _Requirements: 2.1_

  - [x] 6.3 修复 user-portal user.ts baseURL（Change B3）
    - 修改 `frontend/user-portal/src/api/user.ts`
    - 将 `adminCenterAxios` 的 `baseURL` 从 `/api/admin-center` 改为 `/api/v1/admin`
    - _Bug_Condition: C7 — baseURL `/api/admin-center` 在 Kong 中无匹配路由_
    - _Expected_Behavior: 请求通过 Kong `/api/v1/admin` 路由正确到达 admin-center 后端_
    - _Preservation: 其他 axios 实例的 baseURL 不变_
    - _Requirements: 2.1_

  - [x] 6.4 修复 user-portal auth.ts baseURL（Change B4）
    - 修改 `frontend/user-portal/src/api/auth.ts`
    - 将 `authRequest` 的 `baseURL` 从 `/api/v1/auth` 改为 `/api/portal/auth`
    - 注意：user-portal 后端 `AuthController` 没有 `/auth/refresh` 端点（只有 login、logout、me、validate），developer-workstation 后端也没有。这意味着 token refresh 功能在修复前后都不工作（已有缺陷，非本次修复引入的回归）。admin-center 是唯一有 `/auth/refresh` 端点的模块
    - _Bug_Condition: C8 — baseURL `/api/v1/auth` 被 Kong 路由到 developer-workstation 而非 user-portal_
    - _Expected_Behavior: 认证请求通过 Kong `/api/portal` 路由正确到达 user-portal 后端_
    - _Preservation: developer-workstation 前端的 auth baseURL 不变_
    - _Requirements: 2.2_

- [x] 7. Fix Group C: 修复用户身份传递（C9, C10, C11）

  - [x] 7.1 移除 user-portal request.ts X-User-Id 硬编码回退（Change C1）
    - 修改 `frontend/user-portal/src/api/request.ts`
    - 将 `config.headers['X-User-Id'] = userId || 'user_1'` 改为仅在 userId 存在时设置头部
    - _Bug_Condition: C9 — 无 userId 时回退为硬编码 'user_1'_
    - _Expected_Behavior: 无 userId 时不设置 X-User-Id 头_
    - _Preservation: 有 userId 时仍然正常设置 X-User-Id 头_
    - _Requirements: 2.2, 2.4_

  - [x] 7.2 UserPermissionController 优先从 SecurityContext 获取 userId（Change C2）
    - 修改 `backend/user-portal/src/main/java/com/portal/controller/UserPermissionController.java`
    - 修改 `getMyPermissions()` 等方法：优先从 `SecurityContextUtils.getCurrentUserId()` 获取 userId
    - 将 `@RequestHeader("X-User-Id")` 改为 `required = false` 作为回退
    - 两者都为空时返回 401
    - _Bug_Condition: C9, C10 — 依赖不可靠的 X-User-Id 头获取用户身份_
    - _Expected_Behavior: 优先从 JWT 解析的 SecurityContext 获取 userId_
    - _Preservation: 现有 RestTemplate 调用 admin-center 的模式不变_
    - _Requirements: 2.2, 2.4_

  - [x] 7.3 UserPermissionController RestTemplate 调用携带认证信息（Change C3）
    - 修改 `backend/user-portal/src/main/java/com/portal/controller/UserPermissionController.java`
    - 在 `getUserRoles()`, `getUserVirtualGroups()`, `getUserBusinessUnits()` 等方法中创建 `HttpHeaders`
    - 设置 `X-User-Id` 和 `X-Username` 头（从 SecurityContext 获取）
    - 使用 `HttpEntity<>(headers)` 替代 `null` 作为 `restTemplate.exchange()` 的第三个参数
    - _Bug_Condition: C11 — RestTemplate 调用 admin-center 无认证头_
    - _Expected_Behavior: 服务间调用携带 X-User-Id 和 X-Username 头_
    - _Preservation: RestTemplate 调用的 URL 和 HTTP 方法不变_
    - _Requirements: 2.4_

- [x] 8. Fix Group D: 修复安全漏洞（C13, C14）

  - [x] 8.1 logout 端点失效 token（Change D1）
    - 修改 `backend/user-portal/src/main/java/com/portal/controller/AuthController.java`
    - 修改 `backend/developer-workstation/src/main/java/com/developer/controller/AuthController.java`
    - 修改 `backend/admin-center/src/main/java/com/admin/service/impl/AuthServiceImpl.java` — 当前 `logout()` 只打日志不做 blacklist
    - 在 user-portal 和 developer-workstation 的 `logout()` 方法中从 `Authorization` 头提取 token
    - 在 admin-center 的 `AuthServiceImpl.logout()` 中调用 `JwtTokenService.blacklistToken(token)`
    - 注入 `JwtTokenService`，调用 `blacklistToken(token)` 将 token 加入 Redis 黑名单
    - _Bug_Condition: C13 — 三个模块的 logout 都不失效 token（user-portal/developer-workstation 直接返回 200，admin-center 只打日志）_
    - _Expected_Behavior: logout 后 token 被加入 Redis 黑名单，后续请求被拒绝_
    - _Preservation: logout 仍然返回 HTTP 200 响应_
    - _Requirements: 2.4_

  - [x] 8.2 WebSocketAuthInterceptor 统一使用 JwtTokenService（Change D2）
    - 修改 `backend/user-portal/src/main/java/com/portal/config/WebSocketAuthInterceptor.java`
    - 注入 `JwtTokenService`
    - 使用 `jwtTokenService.validateToken(token)` 和 `jwtTokenService.extractUserPrincipal(token)` 替代直接使用 `io.jsonwebtoken`
    - 移除 `@Value("${jwt.secret}")` 字段和手动密钥创建逻辑
    - _Bug_Condition: C14 — WebSocket JWT 密钥不做 padding，不检查黑名单_
    - _Expected_Behavior: WebSocket 认证使用 JwtTokenService 标准实现，支持黑名单检查_
    - _Preservation: WebSocket 连接认证流程不变，仍然从 query parameter 或 header 获取 token_
    - _Requirements: 2.4_

- [x] 9. Fix Group E: admin-center 前端改进

  - [x] 9.1 admin-center request.ts X-User-Id 回退值改进（Change E1）
    - 修改 `frontend/admin-center/src/api/request.ts`
    - 将 `const userId = localStorage.getItem('userId') || 'system'` 改为仅在 userId 存在时设置头部
    - 与 user-portal 的修复保持一致的模式
    - _Bug_Condition: 硬编码回退值 'system' 可能导致错误的用户身份_
    - _Expected_Behavior: 无 userId 时不设置 X-User-Id 头_
    - _Preservation: 有 userId 时仍然正常设置 X-User-Id 头_
    - _Requirements: 2.4_

- [x] 10. Verify bug condition exploration tests now pass

  - [x] 10.1 Verify JWT architecture exploration tests pass
    - **Property 1: Expected Behavior** - JWT 认证架构统一后所有模块正确解析 JWT
    - **IMPORTANT**: Re-run the SAME tests from task 1 - do NOT write new tests
    - The tests from task 1 encode the expected behavior for C1, C2, C4
    - When these tests pass, it confirms the JWT architecture bugs are fixed
    - Run bug condition exploration tests from task 1
    - **EXPECTED OUTCOME**: Tests PASS (confirms bugs are fixed)
    - _Requirements: 2.3_

  - [x] 10.2 Verify frontend API path exploration tests pass
    - **Property 2: Expected Behavior** - 前端 API 路径与 Kong 路由匹配
    - **IMPORTANT**: Re-run the SAME tests from task 2 - do NOT write new tests
    - The tests from task 2 encode the expected behavior for C5, C6, C7, C8, C9
    - When these tests pass, it confirms the frontend path bugs are fixed
    - Run bug condition exploration tests from task 2
    - **EXPECTED OUTCOME**: Tests PASS (confirms bugs are fixed)
    - _Requirements: 2.1, 2.2_

  - [x] 10.3 Verify security vulnerability exploration tests pass
    - **Property 3: Expected Behavior** - 安全漏洞已修复
    - **IMPORTANT**: Re-run the SAME tests from task 3 - do NOT write new tests
    - The tests from task 3 encode the expected behavior for C11, C12, C13
    - When these tests pass, it confirms the security bugs are fixed
    - Run bug condition exploration tests from task 3
    - **EXPECTED OUTCOME**: Tests PASS (confirms bugs are fixed)
    - _Requirements: 2.4_

  - [x] 10.4 Verify preservation tests still pass
    - **Property 4: Preservation** - 现有登录、路由和权限检查流程不变
    - **IMPORTANT**: Re-run the SAME tests from task 4 - do NOT write new tests
    - Run preservation property tests from task 4
    - **EXPECTED OUTCOME**: Tests PASS (confirms no regressions)
    - Confirm all preservation tests still pass after fix (no regressions)
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5_

- [x] 11. Checkpoint - Ensure all tests pass
  - Run full backend test suite: `mvn test` in each module
  - Run full frontend test suite: `npm run test` in each frontend app
  - Ensure all exploration tests (tasks 1, 2, 3) now PASS
  - Ensure all preservation tests (task 4) still PASS
  - Ensure no pre-existing tests are broken
  - Ask the user if questions arise
