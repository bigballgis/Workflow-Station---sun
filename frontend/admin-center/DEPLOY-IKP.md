# Admin Center 在 IKP 上的部署说明

## 当前方案：可配置 DNS Resolver + 请求时解析

镜像通过 **docker-entrypoint.sh** 在启动时根据环境变量生成 nginx 配置，避免 nginx 在启动阶段解析不到 `workflow-engine` / `admin-center` 导致崩溃。

- **Docker**：默认 `NGINX_RESOLVER=127.0.0.11`（Docker 内置 DNS），无需设置。
- **IKP（如 Kubernetes）**：在部署里设置 `NGINX_RESOLVER` 为集群 DNS 地址，例如：
  - CoreDNS：`10.96.0.10` 或 `kube-dns.kube-system.svc.cluster.local`
  - 具体值以 IKP 文档或 `kubectl get svc -n kube-system` 中 DNS 服务 ClusterIP 为准。

示例（K8s Deployment env）：

```yaml
env:
  - name: NGINX_RESOLVER
    value: "10.96.0.10"
```

同一镜像在 Docker 和 IKP 上均可使用，只需在 IKP 上配置上述环境变量。

---

## 备选方案：前端不代理，由 IKP 统一转发 API

若 IKP 使用 Ingress / Gateway 统一暴露 API，可以改为：

1. **前端容器只提供静态资源**  
   nginx 只做静态站点，不配置 `/api` 的 proxy_pass，彻底不依赖后端服务名解析。

2. **API 由 IKP 路由**  
   浏览器请求同一域名下的 `/api/...`，由 Ingress 根据 path 转发到 workflow-engine、admin-center 等。

这样前端镜像无任何 upstream 依赖，启动顺序与 DNS 无关，更适合严格编排环境。实现步骤概要：

- 前端构建时或运行时使用 **单一 API 基础地址**（如 `VITE_API_BASE_URL` 或运行时注入的 `window.__API_BASE__`）。
- 请求发往该基础地址（或相对路径 `/api`），由 IKP 的 Ingress/Gateway 做路由。
- 本仓库中的 nginx 去掉 `/api` 的 location，或单独做一个“仅静态”的 nginx 配置/镜像用于 IKP。

如需采用此方案，需要在前端代码里把当前相对路径 `/api/v1/admin` 改为可配置的 base URL（或继续用相对路径并保证 IKP 上该域名下 `/api` 已正确转发）。
