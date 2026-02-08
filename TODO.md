# TODO / Known Issues

## Architecture

### API Gateway 未被使用
- **优先级**: 中
- **状态**: 待处理
- **描述**: 当前前端 nginx 直接 proxy_pass 到各后端服务（admin-center、user-portal、developer-workstation），完全绕过了 API Gateway。Gateway 实现了统一鉴权（JWT）、限流（Redis 滑动窗口）、请求日志等功能，但没有流量经过它。
- **影响**: Gateway 的 `AuthenticationFilter`、`RateLimitFilter` 均未生效，鉴权由各后端 Spring Security 独立处理。
- **方案选择**:
  - **方案 A**: 改前端 nginx 配置，所有 `/api/*` 统一 proxy 到 API Gateway，由 Gateway 做路由分发 — 标准微服务做法
  - **方案 B**: 移除 API Gateway 服务，保持 nginx 直连后端，限流在 nginx 层实现 — 架构更简单
- **涉及文件**:
  - `frontend/admin-center/nginx.conf`
  - `frontend/user-portal/nginx.conf`
  - `frontend/developer-workstation/nginx.conf`
  - `frontend/*/docker-entrypoint.sh`
  - `backend/api-gateway/` (整个模块)
  - 所有环境的 `docker-compose.*.yml`
