# Bugfix Requirements Document

## Introduction

集成 Kong Gateway 后，user-portal 和 developer-workstation 的用户信息面板（Business Units、Virtual Groups、Roles）全部显示为空。根本原因涉及三个层面的问题：

1. **Kong 路由缺失**：前端 `user.ts` 通过 `/api/admin-center/users/{userId}/...` 路径请求 admin-center 数据，但 Kong 没有配置 `/api/admin-center` 路由，导致请求无法到达后端。
2. **user-portal 缺少 JWT 认证过滤器**：user-portal 的 `SecurityConfig` 没有注册 `JwtAuthenticationFilter`，导致所有请求在 Spring Security 层面都是未认证的，`SecurityContextUtils.getCurrentUser()` 始终返回空。
3. **X-User-Id 头部回退机制不可靠**：user-portal 的 `UserPermissionController` 依赖前端通过 `X-User-Id` 请求头传递用户身份，而前端 `request.ts` 拦截器在找不到 userId 时回退为硬编码的 `'user_1'`，导致查询到错误用户的数据（或空数据）。

## Bug Analysis

### Current Behavior (Defect)

1.1 WHEN 前端 developer-workstation 的 `UserProfileDropdown` 通过 `/api/admin-center/users/{userId}/business-units`、`/api/admin-center/users/{userId}/virtual-groups`、`/api/admin-center/users/{userId}/roles` 请求用户数据 THEN 请求经 nginx 转发到 Kong 后因无匹配路由而失败（Kong 仅配置了 `/api/v1/admin` 路由，未配置 `/api/admin-center` 路由），导致 Business Units、Virtual Groups、Roles 全部显示为空

1.2 WHEN 前端 user-portal 的 `UserProfileDropdown` 通过 `permissionApi.getMyPermissionView()` 请求 `/api/portal/my-permissions` THEN 后端 `UserPermissionController` 从 `X-User-Id` 请求头获取用户身份，但该头部值可能为 `'user_1'`（硬编码回退值），导致查询到错误用户的数据或空数据

1.3 WHEN user-portal 后端收到带有 JWT token 的请求 THEN 由于 `SecurityConfig` 未注册 `JwtAuthenticationFilter`，JWT token 不会被解析，`SecurityContextHolder` 中没有认证信息，`SecurityContextUtils.getCurrentUser()` 返回空

1.4 WHEN admin-center 后端的 JWT 过滤器无法从 Authorization 头解析到有效 token 时 THEN 回退到从 `X-Username` / `X-User-Id` 头部创建 `UserPrincipal`，但该 principal 的 roles 和 permissions 列表为空，导致权限验证功能不正确

### Expected Behavior (Correct)

2.1 WHEN 前端 developer-workstation 的 `UserProfileDropdown` 请求用户的 Business Units、Virtual Groups、Roles 数据 THEN 系统 SHALL 通过正确的 Kong 路由（`/api/v1/admin/users/{userId}/...`）将请求转发到 admin-center 后端，并正确返回用户数据

2.2 WHEN 前端 user-portal 的 `UserProfileDropdown` 请求 `/api/portal/my-permissions` THEN 系统 SHALL 从 JWT token 中解析出真实的 userId，而非依赖可能不准确的 `X-User-Id` 请求头，确保查询到正确用户的 Business Units、Virtual Groups、Roles 数据

2.3 WHEN user-portal 后端收到带有 JWT token 的请求 THEN 系统 SHALL 通过注册的 `JwtAuthenticationFilter` 解析 JWT token，将用户身份信息（userId、roles、permissions）设置到 `SecurityContextHolder` 中

2.4 WHEN 后端服务需要获取当前用户身份时 THEN 系统 SHALL 优先从 `SecurityContextHolder`（JWT 解析结果）获取用户信息，仅在 JWT 不可用时才回退到请求头，且回退时不使用硬编码默认值

### Unchanged Behavior (Regression Prevention)

3.1 WHEN 用户通过 user-portal 或 developer-workstation 的登录页面提交正确的用户名和密码 THEN 系统 SHALL CONTINUE TO 返回包含 accessToken、refreshToken 和 user 信息的登录响应，并将 token 和用户信息保存到 localStorage

3.2 WHEN 前端发送带有有效 JWT token 的 API 请求到 admin-center THEN 系统 SHALL CONTINUE TO 正确解析 JWT token 中的 userId、username、roles、permissions，并设置到 SecurityContextHolder

3.3 WHEN JWT token 过期且前端持有有效的 refreshToken THEN 系统 SHALL CONTINUE TO 通过 `/auth/refresh` 端点获取新的 accessToken，并自动重试失败的请求

3.4 WHEN Kong 接收到匹配已有路由（`/api/v1/admin`、`/api/v1`、`/api/portal`）的请求 THEN 系统 SHALL CONTINUE TO 正确转发请求到对应的后端服务，保持 `strip_path: false` 和 `preserve_host: false` 配置不变

3.5 WHEN 用户通过 admin-center 管理界面进行用户/角色/权限管理操作 THEN 系统 SHALL CONTINUE TO 正常工作，不受本次修复影响
