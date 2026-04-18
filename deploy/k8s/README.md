# Istio 风格部署清单

这组文件位于仓库路径 `deploy/k8s`，用于部署 Workflow Station 的 Istio 版本 Kubernetes 清单。

## 推荐用法

- 清单目录：本目录 [`deploy/k8s`](.)

- 一键部署（推荐）：[deploy/k8s/ps1/apply-workflow-station-all.ps1](ps1/apply-workflow-station-all.ps1)（ConfigMap / Secret / Istio 按序执行；见 [ps1/README.md](ps1/README.md)）

- 仅 Istio 清单：[deploy/k8s/ps1/apply-workflow-station-istio-generated.ps1](ps1/apply-workflow-station-istio-generated.ps1)

- 脚本说明：[deploy/k8s/ps1/README.md](ps1/README.md)

建议直接使用部署脚本，不要手工修改 YAML。

说明：本目录下的源文件使用 `__NAMESPACE__` 和 `__IMAGE_TAG__` 作为占位值；实际部署时分别由脚本根据 `-Namespace` 和 `-ImageTag` 动态渲染，不应手工写死环境 namespace 或版本号。

脚本支持在执行时动态替换：

1. `namespace`

2. `image tag`

3. `base domain`

4. `ingress host`

5. `ingress tls secret`

6. 镜像仓库前缀

其中前端入口已调整为与 [workflow-platform-ingress-gateway.yaml](workflow-platform-ingress-gateway.yaml) 对齐的单域名多路径模式：

- `/login`

- `/admin`

- `/portal`

- `/dev`（DEV-only，可选）

前端共享一个 Istio Gateway：`workflow-platform-ingress-gateway`。

该 Gateway 支持：

- HTTP 80 自动跳转 HTTPS

- HTTPS 443 + TLS secret

## 文件列表

- `admin-center.yaml`

- `workflow-engine.yaml`

- `user-portal.yaml`

- `admin-center-frontend.yaml`

- `user-portal-frontend.yaml`

- `platform-login-frontend.yaml`

- `workflow-platform-ingress-gateway.yaml`

- `kong.yaml`

- `n8n.yaml`

- `workflow-station-superset.yaml`

- `redis.yaml`

- `kafka.yaml`

可选 DEV-only 文件：

- `developer-workstation.yaml`

- `developer-workstation-frontend.yaml`

## 说明

如果需要：

- 查看脚本参数、命令示例、常见问题，请看 [deploy/k8s/ps1/README.md](ps1/README.md)

- **推荐**使用 [`apply-workflow-station-all.ps1`](ps1/apply-workflow-station-all.ps1) 一次部署（或 `-RenderOnly` 仅渲染到本地目录）。ConfigMap / Secret 源文件按环境分子目录：`config_map/<Environment>/`、`secret/<Environment>/`（默认 `preprod`）。分步执行或参数说明见 [ps1/README.md](ps1/README.md)。

默认行为说明：

- `developer-workstation` 相关清单默认不随整套 Istio 清单部署

- 如需开放 `/dev`，请在脚本中显式传入 `-IncludeDeveloperWorkstation`，或使用 `-Select` 单独指定

