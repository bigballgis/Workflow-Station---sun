# Superset 统一 SSO 网关（生产 k8s/Istio）— Phase 1 "E"

把 Superset 登录接入平台统一 SSO。**作者/管理员**走 JWT 鉴权网关，**嵌入看板查看者**走不鉴权的
嵌入 host（guest token 认证）。对应 dev 的双端口方案（作者 :8087 / 嵌入 :8089）。

> **为什么不用 Istio ext_authz？** ext_authz provider 必须注册在 `meshConfig.extensionProviders`，
> 这需要 mesh 管理员权限，受公司策略限制无法自助完成。因此作者鉴权改由**一个普通的 nginx
> 反向代理 Deployment** 承担——它就是 dev `nginx-edge` `:8087` server block 的 k8s 版本，
> 不碰任何 mesh 配置、不用 EnvoyFilter。只有 `action: DENY` 的普通 AuthorizationPolicy 用来防绕过，
> 这类策略**不需要** provider 注册。

## 架构

```
                       ┌─ hermes-workflow-superset-author.<BASE_DOMAIN>  (作者 UI)
ingressgateway ───────┤    └─ VirtualService → nginx 网关 Deployment
                       │        (superset-author-proxy)
                       │        auth_request → admin-center
                       │          /api/v1/admin/internal/bi/superset/authorize
                       │          200 → 注入 X-Remote-User/Roles → Superset REMOTE_USER 登录
                       │          401 → 302 统一登录；403 → 无 BI 角色映射
                       │
                       └─ hermes-workflow-superset-internal-proxy.<BASE_DOMAIN>  (嵌入)
                            └─ 不鉴权（guest token）；VirtualService 剥离客户端 X-Remote-*
                            └─ = SUPERSET_PUBLIC_HOST（user-portal iframe 用）
```

两个 host 指向同一个 `hase-hermes-workflow-superset` workload。客户端伪造的 `X-Remote-*`
在**两个** VirtualService 都被 `headers.request.remove` 剥离；作者 host 经 nginx 网关
auth_request 后重新注入校验过的值。**防绕过**：`action: DENY` 的 AuthorizationPolicy 拒绝任何
带 `X-Remote-User` 且来源**不是**网关 ServiceAccount 的请求直达 Superset（详见下文“安全要点”）。

## 前置：镜像必须重建/新建

部署前 **必须** 准备三个镜像：
- **superset**：`deploy/superset/Dockerfile` 现在 COPY 了 `superset_security_manager.py`，且
  `superset_config.py`/k8s configmap 启用了 `AUTH_REMOTE_USER` + 自定义 SM + `RECAPTCHA_*`。
- **admin-center**：新增了 `BiSupersetAuthController`（`/internal/bi/superset/authorize`，支持
  `/authorize/**`）与 `BiRbacMappingService.getEffectiveSupersetRoleNames`。
- **superset-author-proxy（新）**：`deploy/superset/author-proxy/`（`FROM nginx:alpine` + 模板）。
  构建并推送到项目 nexus 仓库，使集群无需 dockerhub 即可拉取：
  ```bash
  cd deploy/superset/author-proxy
  docker build -t nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/superset-author-proxy:<tag> .
  docker push    nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2/superset-author-proxy:<tag>
  ```
  网关用 stock nginx 的 envsubst 在启动时把 `${INGRESS_HOST}`（统一登录 host，取自
  `workflow-platform-config`）和 `${POD_NAMESPACE}`（拼 in-cluster service DNS）渲染进配置；
  `NGINX_ENVSUBST_FILTER` 限定只替换这两个，不动 nginx 自身的 `$变量`。

> 用各自的构建/推送流程打 tag 推 nexus，再以新 `-ImageTag` 部署。三个镜像建议同 tag。

## 部署

1. 重建并推送 superset / admin-center / superset-author-proxy 三个镜像（见上）。
2. 应用 manifest（占位符由脚本替换）：
   ```powershell
   deploy/k8s/ps1/apply-workflow-station-istio-generated.ps1 `
     -Namespace <ns> -ImageTag <tag> -BaseDomain <domain> -IngressHost <host> `
     -IngressTlsSecret <secret> -Select superset
   ```
   会创建：`superset-author-proxy` 的 ServiceAccount / Deployment / Service / Sidecar，
   作者 VirtualService 改指向该网关，以及防绕过的 `...-deny-forged-remote-user` AuthorizationPolicy。
3. 同步 ConfigMap（`SUPERSET_CORS_ORIGINS` 等）与 Secret（`SUPERSET_SECRET_KEY`）：
   ```powershell
   deploy/k8s/ps1/apply-workflow-station-configmap.ps1 -Namespace <ns> -Environment <env> ...
   deploy/k8s/ps1/apply-workflow-station-secret.ps1 -Namespace <ns> -Environment <env>
   ```
   - `SUPERSET_PUBLIC_HOST` 维持指向**嵌入 host**（`hermes-workflow-superset-internal-proxy.*`）。
   - 作者入口（前端/门户跳转）指向**作者 host**（`hermes-workflow-superset-author.*`）。
   - `INGRESS_HOST` 必须在 `workflow-platform-config` 内正确设置（网关 401 跳登录用它）。
   - DNS：两个 host 都需解析到 ingressgateway，并按需配 TLS。
4. **PeerAuthentication（mTLS）必须开启**：防绕过策略按 `notPrincipals` 匹配来源 SA，
   只有 mTLS 才能填充 source principal。若命名空间未设 STRICT，需补一个 PeerAuthentication
   （或确认 mesh 默认 STRICT），否则 DENY 规则可能因无 principal 而失效。

## 验证（生产）

```bash
# 0) 网关自身就绪
kubectl -n <ns> get deploy hase-hermes-workflow-superset-author-proxy

# 1) 嵌入 host：不鉴权可达；伪造头被剥离（curl 注入 X-Remote-User 不应建会话）
curl -i https://hermes-workflow-superset-internal-proxy.<domain>/health

# 2) 作者 host 无 JWT → 网关 401 → 302 跳统一登录
curl -i https://hermes-workflow-superset-author.<domain>/superset/welcome/

# 3) 作者 host 带有效平台 JWT cookie → 200，Superset 建会话；
#    superset.ab_user 出现该用户并带映射角色（ac_bi_rbac_mappings → Superset 角色）
curl -i -b "ac_access_token=<jwt>" https://hermes-workflow-superset-author.<domain>/login/

# 4) 绕过验证：在集群内用别的 pod 直接打 Superset service 并伪造 X-Remote-User
#    → 应被 DENY（403 RBAC: access denied），证明 nginx 网关不可旁路
kubectl -n <ns> run probe --rm -it --image=curlimages/curl --restart=Never -- \
  curl -i -H "X-Remote-User: attacker" http://hase-hermes-workflow-superset/login/

# 5) 嵌入看板在 user-portal 正常渲染（与 dev /verify-ui 一致）
```

## 安全要点

- **防绕过靠两层**：(a) 两个 VirtualService 都 `remove` 客户端 `X-Remote-*`；(b) `action: DENY`
  AuthorizationPolicy 拒绝任何带 `X-Remote-User`、来源非网关 SA 的请求直达 Superset。后者覆盖
  集群内横向访问（绕过 ingress 的情形）。
- nginx 网关 auth_request 子请求**清空 Origin**（`proxy_set_header Origin "";`），否则浏览器
  POST 携带的 Origin 会触发 admin-center CORS 403 把整请求拒掉（dev 实测过的坑）。
- Superset workload 仍是 ClusterIP，勿暴露 NodePort/额外 Service。
- `SUPERSET_SECRET_KEY` 走 Secret，勿用占位符（否则 guest token 可伪造）。
- 未认证访问作者 host 由 nginx 直接 302 跳统一登录（带 `client_id=admin` + `state=superset-author`），
  无需 ext_authz 的 `headersToDownstreamOnDeny` 那套绕法。
