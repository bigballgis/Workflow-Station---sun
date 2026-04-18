# apply-workflow-station-istio-generated.ps1 使用说明

本文档说明以下脚本的用途、参数和使用方式：

- [deploy/k8s/ps1/apply-workflow-station-all.ps1](apply-workflow-station-all.ps1)（**推荐**：一次执行 ConfigMap → Secret → Istio 清单）
- [deploy/k8s/ps1/apply-workflow-station-configmap.ps1](apply-workflow-station-configmap.ps1)
- [deploy/k8s/ps1/apply-workflow-station-secret.ps1](apply-workflow-station-secret.ps1)
- [deploy/k8s/ps1/apply-workflow-station-istio-generated.ps1](apply-workflow-station-istio-generated.ps1)
- [deploy/k8s/ps1/delete-workflow-station-istio-generated.ps1](delete-workflow-station-istio-generated.ps1)

## 目录与 `-Environment`

- ConfigMap 源文件目录：`deploy/k8s/config_map/<Environment>/`（默认 **preprod**）。
- Secret 源文件目录：`deploy/k8s/secret/<Environment>/`（默认 **preprod**）。
- 若需其它环境（如 `sit`），在仓库中准备 `config_map/sit/`、`secret/sit/` 后，部署时传入 `-Environment sit`。

`apply-workflow-station-istio-generated.ps1` 在 **`-InitializeDatabase`** 时，会按同一 `-Environment` 从上述路径读取 `configmap-workflow-platform-config.yml` 与 `secret-workflow-paltform.yml` 推导数据库连接。

## 概述

该脚本用于部署 [deploy/k8s](..) 目录下的 Istio 清单，并在执行时动态替换以下内容：

1. `namespace:` → 替换为传入的 `-Namespace`
2. `image:` 的 tag / `__IMAGE_TAG__` → 替换为传入的 `-ImageTag`
3. `__BASE_DOMAIN__` → 替换为传入的 `-BaseDomain`
4. `__INGRESS_HOST__` → 替换为传入的 `-IngressHost`
5. `__INGRESS_TLS_SECRET__` → 替换为传入的 `-IngressTlsSecret`
6. 镜像仓库前缀 → 可通过 `-ImageRepositoryPrefix` 统一替换
7. 可选：在首次 IKP 部署前调用 [deploy/init-scripts/init-database.ps1](../../init-scripts/init-database.ps1) 初始化外部 PostgreSQL

适用场景：

- 同一套 YAML 在 SIT、UAT、PROD 间切换
- 仅调整镜像 tag 或镜像仓库前缀
- 只部署部分服务，而不是整个目录

如果你只使用 `deploy/k8s` 目录部署，**推荐**一条命令：`apply-workflow-station-all.ps1`（见下文「一键部署」）。若分步执行，顺序为：

1. `apply-workflow-station-configmap.ps1`（可加 `-Environment`）
2. `apply-workflow-station-secret.ps1`（可加 `-Environment`）
3. `apply-workflow-station-istio-generated.ps1`（`-InitializeDatabase` 时需与上面同一 `-Environment`）

如需按组件分阶段部署，建议顺序为：

1. 第一批：`redis`、`n8n`、`kafka`、`workflow-station-superset`
2. 第二批：`kong`
3. 第三批：`workflow-engine`
4. 第四批：其余 backend
5. 第五批：frontend

## 处理范围

脚本默认处理 [deploy/k8s](..) 下的 YAML 文件，并自动忽略 `kustomization.yaml`。

说明：源 YAML 中的 `namespace` 使用 `__NAMESPACE__` 占位，镜像版本使用 `__IMAGE_TAG__` 占位；实际值分别由 `-Namespace` 和 `-ImageTag` 在渲染阶段统一写入。

为保持与 [workflow-platform-ingress-gateway.yaml](../workflow-platform-ingress-gateway.yaml) 一致，`developer-workstation` 相关清单默认不会随整套前端入口一起部署；只有显式 `-Select` 指定，或传入 `-IncludeDeveloperWorkstation` 时才会部署。

## 前置要求

执行前请确认：

1. 本机已安装 `kubectl`
2. 当前 `kubectl context` 已指向目标 Kubernetes 集群
3. 当前账号具备 namespace 创建和资源部署权限
4. 如镜像来自私有仓库，目标集群已配置拉取凭据，例如 `nexus3`

## 参数说明

### 必填参数

- `-Namespace`
  - 目标 namespace
  - 示例：`ame-hase-bisp-poc`
  - 可选值：
    - `poc`：`ame-hase-bisp-poc`
    - `pprd`：`ame-hase-hermes-preprod`

- `-ImageTag`
  - 部署镜像的 tag
  - 示例：`sit-20260320`

### 可选参数

- `-BaseDomain`
  - 替换 YAML 中的 `__BASE_DOMAIN__`
  - 示例：`ikp402xsm.cloud.hk.hsbc`
  - 可选值：
    - `ikp402xsm.cloud.hk.hsbc`（对应 `poc`）
    - `ikp401xnp.cloud.hk.hsbc`（对应 `pprd`）

- `-IngressHost`
  - 替换 YAML 中的 `__INGRESS_HOST__`
  - 用于与 [workflow-platform-ingress-gateway.yaml](../workflow-platform-ingress-gateway.yaml) 一致的单域名多路径入口，例如：`hermes-sit.hk.hsbc`
  - 对应示例：
    - `ikp402xsm.cloud.hk.hsbc` → `hermes-sit.hk.hsbc`
    - `ikp401xnp.cloud.hk.hsbc` → `workflow-pprd.your-domain.com`

- `-IngressTlsSecret`
  - 替换 YAML 中的 `__INGRESS_TLS_SECRET__`
  - 用于 Istio ingressgateway 上的 TLS 证书 secret，例如：`workflow-platform-tls`

- `-ImageRepositoryPrefix`
  - 统一替换镜像仓库前缀
  - 示例：`nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2`

- `-Select`
  - 只部署部分文件
  - 支持完整文件名、basename、通配符
  - 示例：`admin-center,admin-center-frontend`

- `-IncludeDeveloperWorkstation`
  - 将 `developer-workstation` 与 `developer-workstation-frontend` 纳入默认部署集合
  - 不传时，这两个 DEV-only 清单默认排除

- `-RenderOnly`
  - 只渲染替换后的 YAML，不执行 `kubectl apply`

- `-InitializeDatabase`
  - 在执行 `kubectl apply` 之前，先调用 [deploy/init-scripts/init-database.ps1](../../init-scripts/init-database.ps1)
  - 适合 IKP 首次部署时，把数据库初始化纳入同一条部署命令
  - 默认会自动从以下文件推导 DB 连接参数：
    - [deploy/k8s/config_map/preprod/configmap-workflow-platform-config.yml](../config_map/preprod/configmap-workflow-platform-config.yml)
    - [deploy/k8s/secret/secret-workflow-paltform.yml](../secret/secret-workflow-paltform.yml)
  - 若不想自动推导，也可显式传 `-DbHost -DbPort -DbName -DbUser -DbPassword -DbSchema`

- `-DbHost` / `-DbPort` / `-DbName` / `-DbUser` / `-DbPassword` / `-DbSchema`
  - 覆盖数据库初始化时使用的连接参数
  - `-DbSchema` 会传给 `init-database.ps1`，通过 PostgreSQL `search_path` 保证 SQL 建表落到正确 schema（例如 `hmwfst`）

- `-IncludeDemoData`
  - 仅在 `-InitializeDatabase` 时生效
  - 允许执行 `init-database.ps1` 中的 wipe + demo seed 步骤
  - **默认不执行**，避免 IKP 首次部署时误导入 demo 数据

- `-ForceDatabaseInitialization`
  - 即使目标 schema 中已存在 `wf_extended_task_info` 标记表，也强制执行数据库初始化
  - 默认情况下，脚本检测到 marker table 已存在会直接跳过 DB init，避免重复初始化

- `-DryRun`
  - 执行 `kubectl apply --dry-run=client`

- `-OutputDir`
  - 指定渲染后文件输出目录
  - 不传时自动生成临时目录

- `-Environment`（`apply-workflow-station-istio-generated.ps1`）
  - 与 `config_map/<Environment>/`、`secret/<Environment>/` 子目录名一致，供 `-InitializeDatabase` 解析 DB 连接；默认 `preprod`。

**`apply-workflow-station-configmap.ps1` / `apply-workflow-station-secret.ps1` 补充**

- `-Environment`：读取 `config_map/<Environment>/` 或 `secret/<Environment>/` 下 YAML；默认 `preprod`。
- `-RenderOnly`：只渲染到 `-OutputDir`，不调用 `kubectl`（无需本机已配置集群上下文也可生成文件）。
- `-OutputDir`：与 `-RenderOnly` 联用，输出目录内为扁平文件名。

**`apply-workflow-station-all.ps1`**

- 依次调用上述三个脚本；`-Environment`、`-NamespaceToken`、`-BaseDomain`、`-IngressHost` 等会传给子脚本。
- `-RenderOnly -OutputDir <根目录>`：在 `<根目录>` 下生成 `config_map/`、`secret/`、`istio/` 三个子目录。

## 快速开始

在仓库根目录执行以下命令。

### 0. 一键部署：ConfigMap + Secret + Istio（preprod 目录）

```powershell
.\deploy\k8s\ps1\apply-workflow-station-all.ps1 `
  -Namespace ame-hase-hermes-preprod `
  -ImageTag sit-20260320 `
  -Environment preprod `
  -NamespaceToken ame-hase-hermes-preprod `
  -IngressHost hermes-sit.hk.hsbc `
  -IngressTlsSecret workflow-platform-tls `
  -BaseDomain ikp401xnp.cloud.hk.hsbc
```

其它环境：将 `-Environment` 改为 `sit` 等，并确保 `config_map/<名>/`、`secret/<名>/` 下已有对应 YAML。

### 0b. 一键仅渲染（不执行 kubectl）

在根目录下生成 `rendered\preprod-bundle\config_map\`、`secret\`、`istio\` 三套已替换占位符的文件：

```powershell
.\deploy\k8s\ps1\apply-workflow-station-all.ps1 `
  -Namespace ame-hase-hermes-preprod `
  -ImageTag sit-20260320 `
  -Environment preprod `
  -NamespaceToken ame-hase-hermes-preprod `
  -IngressHost hermes-sit.hk.hsbc `
  -IngressTlsSecret workflow-platform-tls `
  -BaseDomain ikp401xnp.cloud.hk.hsbc `
  -RenderOnly `
  -OutputDir .\deploy\k8s\rendered\preprod-bundle
```

### 1. 部署全部 Istio 清单（含 developer-workstation）

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -IncludeDeveloperWorkstation
```

### 1c. 首次 IKP 部署：先自动初始化数据库，再部署 Kubernetes 资源

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -InitializeDatabase -DbSchema hmwfst
```

说明：

- 这条命令默认 **不导入 demo 数据**
- 数据库连接参数会优先从 `config_map/<Environment>/configmap-workflow-platform-config.yml` 与 `secret/<Environment>/secret-workflow-paltform.yml` 推导（默认 `Environment=preprod`，见 [config_map/preprod](../config_map/preprod/configmap-workflow-platform-config.yml)、[secret/preprod](../secret/preprod/secret-workflow-paltform.yml)）
- 若 marker table 已存在，数据库初始化会自动跳过

### 1a. 先应用 config_map

```powershell
.\deploy\k8s\ps1\apply-workflow-station-configmap.ps1 `
  -Namespace ame-hase-hermes-preprod `
  -NamespaceToken ame-hase-hermes-preprod `
  -Environment preprod `
  -BaseDomain ikp401xnp.cloud.hk.hsbc `
  -IngressHost hermes-sit.hk.hsbc
```

仅渲染到目录（不 kubectl）：

```powershell
.\deploy\k8s\ps1\apply-workflow-station-configmap.ps1 `
  -Namespace ame-hase-hermes-preprod `
  -NamespaceToken ame-hase-hermes-preprod `
  -Environment preprod `
  -BaseDomain ikp401xnp.cloud.hk.hsbc `
  -IngressHost hermes-sit.hk.hsbc `
  -RenderOnly `
  -OutputDir .\deploy\k8s\rendered\preprod-configmap
```

### 1b. 再应用 secret

```powershell
.\deploy\k8s\ps1\apply-workflow-station-secret.ps1 `
  -Namespace ame-hase-hermes-preprod `
  -Environment preprod
```

仅渲染：

```powershell
.\deploy\k8s\ps1\apply-workflow-station-secret.ps1 `
  -Namespace ame-hase-hermes-preprod `
  -Environment preprod `
  -RenderOnly `
  -OutputDir .\deploy\k8s\rendered\preprod-secret
```

### 2. 指定镜像仓库前缀

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260320 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -ImageRepositoryPrefix nexus3.hk.hsbc:18080/hsbc-238092-cmbhase-bisp/workflow-station2
```

### 3. 只部署部分服务

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260320 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -Select admin-center,admin-center-frontend
```

### 3a. 第一批先部署基础组件 + Superset

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -Select redis,n8n,kafka,workflow-station-superset
```

### 4. 只渲染，不真正部署

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260320 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -RenderOnly -OutputDir .\deploy\k8s\rendered\istio-pprd
```

### 5. 试运行校验

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260320 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -DryRun
```

### 6. 显式包含 developer-workstation

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260320 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -IncludeDeveloperWorkstation
```

### 7. 删除同一组 Istio 清单

```powershell
.\deploy\k8s\ps1\delete-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -IncludeDeveloperWorkstation
```

### 8. 只渲染待删除文件，不真正删除

```powershell
.\deploy\k8s\ps1\delete-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -IncludeDeveloperWorkstation -RenderOnly -OutputDir .\deploy\k8s\rendered\istio-delete-pprd
```

## 选择性部署说明

`-Select` 支持以下形式：

- 完整文件名：`admin-center.yaml`
- 文件 basename：`admin-center`
- 通配符：`*frontend*.yaml`

删除脚本同样支持 `-Select` 和 `-IncludeDeveloperWorkstation`，参数含义与部署脚本一致。

## 执行流程

脚本执行顺序如下：

1. 读取 [deploy/k8s](..) 下的 YAML 文件
2. 根据参数替换 `namespace`、`image tag`、`base domain`、`ingress host`、`ingress tls secret`、镜像前缀
3. 将渲染后的文件写入临时目录或 `-OutputDir`
4. 检查目标 namespace 是否存在
5. 若 namespace 不存在且不是 `-DryRun`，自动创建 namespace
6. 对每个渲染后的 YAML 执行 `kubectl apply -f`

如果传入 `-InitializeDatabase`，则会在第 4 步和第 5 步之间先执行：

1. 解析 DB 连接信息
2. 检查目标 schema 中是否已存在 `wf_extended_task_info`
3. 若不存在，则调用 [deploy/init-scripts/init-database.ps1](../../init-scripts/init-database.ps1) 进行首次初始化
4. 默认只做基础 schema / 增量 / admin 初始化，不执行 demo seed；除非显式传入 `-IncludeDemoData`

删除脚本执行顺序如下：

1. 用与部署脚本相同的参数渲染出一套 YAML
2. 对渲染目录执行 `kubectl delete -f`
3. 默认清理临时渲染目录；如需保留，可传 `-KeepRenderedFiles`

## 常见问题

### 1. 提示脚本执行被禁止

先在当前 PowerShell 会话执行：

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
```

### 2. 提示找不到 kubectl

说明本机未安装 `kubectl`，或未加入 PATH。

### 3. 渲染后的文件仍包含 `__BASE_DOMAIN__`

说明没有传 `-BaseDomain`，但某些 YAML 仍依赖该变量。

### 4. 出现 `domain name "...hsbc\`` invalid` 或 namespace 末尾带反引号

这通常不是 YAML 模板本身有问题，而是命令行里把 Markdown 结尾反引号一起复制进了 `-BaseDomain`、`-IngressHost` 或 `-Namespace`。

例如下面这种值是错误的：

- `ikp402xsm.cloud.hk.hsbc\``
- `ame-hase-bisp-poc\``

正确值应该是：

- `ikp402xsm.cloud.hk.hsbc`
- `ame-hase-bisp-poc`

如果报错里出现类似下面内容：

- `Namespace: "ame-hase-bisp-poc\``
- `cannot get resource "secrets" ... namespace "ame-hase-bisp-poc\``

优先检查 `-Namespace` 是否多复制了 Markdown 结尾反引号。

建议先用 `-RenderOnly` 检查渲染结果，再执行正式部署。

### 5. `-IncludeDeveloperWorkstation` 被当成 `-Select` 值

如果报错类似：

- `No manifest matched '-IncludeDeveloperWorkstation'`

通常是以下两种原因之一：

1. 命令行末尾误复制了 Markdown 反引号
2. 使用的是修复前版本的脚本

现在脚本已经修复，`-IncludeDeveloperWorkstation` 会被正确识别，不会再被当成 manifest 名称。

如需快速确认，可先执行：

- `-RenderOnly`

并检查输出里是否包含：

- `developer-workstation.yaml`
- `developer-workstation-frontend.yaml`

### 6. 前端 SSO 回调地址不匹配

如果前端改为单域名多路径访问，需要传入 `-IngressHost`，并确保 [deploy/k8s/config_map/preprod/configmap-workflow-platform-config.yml](../config_map/preprod/configmap-workflow-platform-config.yml) 中的 `SSO_REDIRECT_*`（https）与 `SSO_REDIRECT_*_HTTP_PREFIX`（http）与浏览器实际访问协议一致，路径为 `/admin/sso/callback`、`/portal/sso/callback`、`/dev/sso/callback`。`CORS_ALLOWED_ORIGINS` 需同时包含 `https://` 与 `http://` 形式的入口域名（若允许 HTTP）。

前端相关 Istio 清单现已合并为一个共享 Gateway：`workflow-platform-ingress-gateway`。如果使用 `-Select` 做前端单独部署，请一并包含 `workflow-platform-ingress-gateway`。

### 7. HTTPS/TLS 未生效

共享 Gateway 已配置 Istio 侧 `80 -> 443` 跳转和 `443/TLS` 监听。请传入 `-IngressTlsSecret`，并确保该 secret 已存在于 Istio ingressgateway 所在 namespace。

### 8. 镜像拉取失败

请检查：

1. `-ImageRepositoryPrefix` 是否正确
2. 镜像 tag 是否存在
3. 集群中是否已配置镜像拉取凭据

### 9. 如何删除某次用部署命令创建的 Istio 资源

请使用与部署时相同的一组参数执行删除脚本。最常见的做法是把原部署命令中的脚本名改成：

- `delete-workflow-station-istio-generated.ps1`

例如，下面这条部署命令：

```powershell
.\deploy\k8s\ps1\apply-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc
```

对应删除命令就是：

```powershell
.\deploy\k8s\ps1\delete-workflow-station-istio-generated.ps1 -Namespace ame-hase-hermes-preprod -ImageTag sit-20260414 -IngressHost hermes-sit.hk.hsbc -IngressTlsSecret workflow-platform-tls -BaseDomain ikp401xnp.cloud.hk.hsbc -IncludeDeveloperWorkstation
```

## 相关脚本

一键部署或一键渲染请优先使用 [apply-workflow-station-all.ps1](apply-workflow-station-all.ps1)。若分步执行，顺序为：

- [apply-workflow-station-configmap.ps1](apply-workflow-station-configmap.ps1)
- [apply-workflow-station-secret.ps1](apply-workflow-station-secret.ps1)
- [apply-workflow-station-istio-generated.ps1](apply-workflow-station-istio-generated.ps1)
- [delete-workflow-station-istio-generated.ps1](delete-workflow-station-istio-generated.ps1)
