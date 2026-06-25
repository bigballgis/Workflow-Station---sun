# Superset 统一 SSO 网关（生产 k8s/Istio）— Phase 1 "E"

把 Superset 登录接入平台统一 SSO。**作者/管理员**走 JWT 鉴权的 Istio ext_authz 网关，
**嵌入看板查看者**走不鉴权的嵌入 host（guest token 认证）。对应 dev 的双端口方案
（作者 :8087 / 嵌入 :8089）。

## 架构

```
                       ┌─ hermes-workflow-superset-author.<BASE_DOMAIN>  (作者 UI)
ingressgateway ───────┤    └─ AuthorizationPolicy(CUSTOM) → ext_authz → admin-center
                       │        /api/v1/admin/internal/bi/superset/authorize
                       │        200 → 注入 X-Remote-User/Roles → Superset REMOTE_USER 登录
                       │        401/403 → 拒绝
                       │
                       └─ hermes-workflow-superset-internal-proxy.<BASE_DOMAIN>  (嵌入)
                            └─ 不鉴权（guest token）；VirtualService 剥离客户端 X-Remote-*
                            └─ = SUPERSET_PUBLIC_HOST（user-portal iframe 用）
```

两个 host 指向同一个 `hase-hermes-workflow-superset` workload。客户端伪造的 `X-Remote-*`
在**两个** VirtualService 都被 `headers.request.remove` 剥离；只有作者 host 经 ext_authz
重新注入校验过的值。

## 前置：镜像必须重建（含新代码/配置）

部署前 **必须** 重建并推送两个镜像（本仓库改动不会自动生效）：
- **superset**：`deploy/superset/Dockerfile` 现在 COPY 了 `superset_security_manager.py`，且
  `superset_config.py`/k8s configmap 启用了 `AUTH_REMOTE_USER` + 自定义 SM + `RECAPTCHA_*`。
- **admin-center**：新增了 `BiSupersetAuthController`（`/internal/bi/superset/authorize`，支持
  `/authorize/**`）与 `BiRbacMappingService.getEffectiveSupersetRoleNames`。

> 用各自的构建/推送流程打 tag 推 nexus，再以新 `-ImageTag` 部署。

## 前置：meshConfig 注册 ext_authz provider（集群运维）

`extensionProviders` 不在本仓库（由 istio-system 的 mesh 配置维护）。运维需在 IstioOperator
`spec.meshConfig` 或 istio configmap 的 `meshConfig` 增加（`<NAMESPACE>` 替换为实际命名空间）：

```yaml
meshConfig:
  extensionProviders:
  - name: superset-bi-ext-authz          # 必须与 AuthorizationPolicy.provider.name 一致
    envoyExtAuthzHttp:
      service: admin-center-service.<NAMESPACE>.svc.cluster.local
      port: 8080
      # Envoy 会把原始请求路径追加到 pathPrefix 之后调用 authz（故端点支持 /authorize/**）
      pathPrefix: /api/v1/admin/internal/bi/superset/authorize
      includeRequestHeadersInCheck:
      - cookie            # 平台 JWT 在 cookie（ac_access_token / access_token）
      - authorization     # 或 Bearer
      headersToUpstreamOnAllow:
      - x-remote-user
      - x-remote-roles
      headersToDownstreamOnDeny:
      - content-type
```

## 部署

1. 重建并推送 superset / admin-center 镜像（见上）。
2. 运维注册 ext_authz provider（见上），重启/重载 istiod。
3. 应用 manifest（占位符由脚本替换）：
   ```powershell
   deploy/k8s/ps1/apply-workflow-station-istio-generated.ps1 `
     -Namespace <ns> -ImageTag <tag> -BaseDomain <domain> -IngressHost <host> `
     -IngressTlsSecret <secret> -Select superset
   ```
4. 同步 ConfigMap（`SUPERSET_CORS_ORIGINS` 等）与 Secret（`SUPERSET_SECRET_KEY`）：
   ```powershell
   deploy/k8s/ps1/apply-workflow-station-configmap.ps1 -Namespace <ns> -Environment <env> ...
   deploy/k8s/ps1/apply-workflow-station-secret.ps1 -Namespace <ns> -Environment <env>
   ```
   - `SUPERSET_PUBLIC_HOST` 维持指向**嵌入 host**（`hermes-workflow-superset-internal-proxy.*`）。
   - 作者入口（前端/门户跳转）指向**作者 host**（`hermes-workflow-superset-author.*`）。
   - DNS：两个 host 都需解析到 ingressgateway，并按需配 TLS。

## 验证（生产）

```bash
# 1) 嵌入 host：不鉴权可达；伪造头被剥离（curl 注入 X-Remote-User 不应建会话）
curl -i https://hermes-workflow-superset-internal-proxy.<domain>/health

# 2) 作者 host 无 JWT → ext_authz 拒绝（401/403）
curl -i https://hermes-workflow-superset-author.<domain>/superset/welcome/

# 3) 作者 host 带有效平台 JWT cookie → 200，Superset 建会话；
#    superset.ab_user 出现该用户并带映射角色（ac_bi_rbac_mappings → Superset 角色）
curl -i -b "ac_access_token=<jwt>" https://hermes-workflow-superset-author.<domain>/login/

# 4) 嵌入看板在 user-portal 正常渲染（与 dev /verify-ui 一致）
```

## 安全要点

- Superset workload 不得绕过两个 VirtualService 直达（Sidecar 已限制；勿暴露 NodePort/额外 Service）。
- 两个 host 都剥离客户端 `X-Remote-*`；只有作者 host 经 ext_authz 注入。
- `SUPERSET_SECRET_KEY` 走 Secret，勿用占位符（否则 guest token 可伪造）。
- 未认证访问作者 host 当前返回 401（非自动跳登录）；如需登录跳转 UX，可让 authorize 端点对
  未认证返回 302 + `location`，并把 `location` 加入 `headersToDownstreamOnDeny`。
