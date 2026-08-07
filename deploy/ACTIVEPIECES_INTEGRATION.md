# Activepieces 集成（社区版 + 网关共享账号）

把 Activepieces（AP）接入平台：**非生产**（dev/uat/sit）经统一 SSO 用**共享账号**进入 AP UI 搭建自动化；
**生产**只当 workflow **runtime**（不开 UI，只放 webhook）。对应 dev 的 `:8085` 网关方案，k8s 用 Istio。

> 与 [SUPERSET_SSO_INTEGRATION.md](SUPERSET_SSO_INTEGRATION.md) 同类，但 AP 的登录态在 **localStorage**（非 cookie），
> 故必须用**客户端登录桥**换 token，而不能像 Superset 那样服务端注入 header。

> **2026-06-30 起改用「跨域 SSO 握手（方案 B）」**：原先桥页/token 端点靠平台 JWT cookie 跟到 AP 域来鉴权——
> 但当 admin 域与 AP 域**不共享一个可安全用作 cookie Domain 的父域**时（IKP 实例就是：admin=`hermes-uat.hk.hsbc`、
> AP=`hermes-workflow-activepieces.<集群>`，唯一公共父域 `.hk.hsbc` 太宽不可用），cookie 跟不过去 → `/__ap/bridge` 401。
> 新方案改为：**在 admin 域换好 AP 会话、用一次性 nonce 把它带到 AP 域**，AP 域全程**不需要平台 cookie**，
> 故两域分属不同父域也能用，且**不再需要 `JWT_COOKIE_DOMAIN`**（见 §5）。

---

## 0. 新增能力索引（本文之外的设计真源）

> **本文定位 = 部署 / 配置 / 排障手册**（AP 桥、共享账号、flow 发布、BPMN service-task）。
> AP 集成后来扩成了**九层（L1–L9）源码级集成**，那部分的**设计真源在
> [`docs/ap-integration/`](../docs/ap-integration/)**，不在本文重复（避免两处真源打架）。
> 下表给出对照，改动相关能力时请以右列为准。

| 能力 | 现状 | 真源 |
|---|---|---|
| **L1 DW 内嵌编排器** —— Function Unit 的 **Automation** 标签直接挂 AP builder（lib-mode + Shadow DOM，**非 iframe**，X-6）；bundle 由 `activepieces/packages/web` 的 `vite.embed.config.mts` 产出，DW 的 `prebuild` 钩子拷进 `public/service-task-builder/` | dev 浏览器 E2E 通过 | `INTEGRATION_DESIGN.md` |
| **L2 Kong `/api/ap`** —— builder 的 REST + socket.io 经网关收编（socket.io path `/api/ap/socket.io`）；**另有 `/ap-cdn`** 把气隙镜像的 piece 图标转回 AP（lib-mode bundle 不带 publicDir，不收编则内嵌 builder 里图标全 404 成灰块） | dev 通过 | 同上 + `HERMES_PATCHES.md` 009 |
| **L7 per-user provisioning** —— 审计到人：managed-authn + signing-key，每用户签 RS256 外部 token 换 AP 会话（flow Owner 落真实用户，不再全是共享账号）。**dev 已启用**（`ACTIVEPIECES_MANAGED_ENABLED=true`） | dev 已启用 | 同上 + `DECISIONS.md` |
| **vendored 源码镜像** —— EE 剥离 + 去 bun + 预烘焙 pieces，`activepieces:0.84.0-ee-removed`（见 §5 前置） | 已落地 | `EE_REMOVAL_PLAN.md` |
| **自研 piece 开发** —— 从写代码到 DW 可用的全链路 + 可直接抄的完整示例 | 已落地（biz-calendar / hash-helper 实建） | [`PIECE_DEVELOPMENT_HOWTO.md`](../docs/ap-integration/PIECE_DEVELOPMENT_HOWTO.md) / [`PIECE_DEVELOPMENT_EXAMPLE.md`](../docs/ap-integration/PIECE_DEVELOPMENT_EXAMPLE.md) |
| **离线 piece 白名单投放** —— 白名单 + 预装（运行时半） | 已落地 | `activepieces/hermes/README.md` |
| **元数据 seed / 自研件元数据序列化** —— `piece_metadata` 行（设计器半） | 已落地 | `deploy/pieces/README.md` |
| **状态 / 未决口** —— 各层进度、待验证项、开放决策 | — | `STATUS.md` / `OPEN_GATES.md` / `DECISIONS.md` |

> **术语**：产品里这套能力对用户叫 **ServiceTask / 自动化流程（Automation）**，"Activepieces" 只在
> 部署与源码层出现（改名决策 D7）。DW 的 **Automation** 标签与 BPMN 的 **Service Task** 是一一绑定关系：
> 一个 `serviceType=ap` 的 service task 对应一条 flow，flow 不能脱离 BPMN 节点独立存在。

---

## 1. 方案与数据流（跨域 SSO 握手）

```
非生产（dev :8085 / k8s hermes-workflow-activepieces.<域>）:

  ① 浏览器在 admin 域点「Activepieces」
       GET /api/v1/admin/internal/ap/launch   （admin 域，平台 JWT cookie 在自己域有效）
       └ admin-center: 验平台 JWT → 共享账号服务端 sign-in AP 拿 {token,projectId}
                       → 签发一次性 nonce 存 Redis（默认 60s，单次）
                       → 返回 { bridgeUrl: "<AP桥页>#nonce=<票>" }
  ② 浏览器整页跳到 AP 域:  /__ap/bridge#nonce=<票>      （AP 域，无需任何平台 cookie）
       └ admin-center 返回桥页 HTML（不再门禁）；桥页 JS 读 location.hash 的 nonce、抹掉它
  ③   GET /__ap/token?nonce=<票>                       （AP 域，nonce 兑换，无需 cookie）
       └ admin-center: 单次消费 nonce → 返回 {token, projectId}
          桥页: localStorage.clear(); ['token']=token; ['projectId']=projectId; location.replace('/')
  ④   /（不门禁）── 反代 AP；AP 读 localStorage 已登录（共享账号 ADMIN）

生产（k8s）: 只 activepieces.<域>/api/v1/webhooks 经 Istio 暴露；UI 不开；不需要共享账号。
```

**安全模型**：换 token 的鉴权点挪到了**已认证的 admin 域 `/launch`**（cookie 在自己域有效）；
nonce 不可猜（UUID）、单次消费、短时效（默认 60s），且 **AP token 从不进入 URL**（只在 nonce 兑换时由服务端返回）。
AP 自身流量（`/`、`/api/*`）不门禁——AP 前端调自己 API 用 AP token（Authorization 头）、不带平台 cookie，
门禁会把这些 XHR 401→白屏。未登录平台的人到 AP 域只看到 AP 原生登录页（无 token，无害）。

> **同源回退（dev）**：admin 与 AP 在 dev 是同源（都 localhost，cookie 不分端口），故 `/__ap/token` 不带 nonce 时
> **回退到老的 cookie 校验**；桥页无 nonce 时也带 `credentials:include`。dev 行为与历史一致，不回归。
> dev `:8085` edge 对 `/__ap/bridge`、`/__ap/token` 仍保留 auth_request（无 cookie 的直接访问会 302 跳登录），
> 在方案 B 下属冗余但无害（登录态浏览器照样过）。

---

## 2. 核心机制

| 机制 | 说明 | 落点 |
|---|---|---|
| **launch 入口（方案 B 核心）** | 在 **admin 域**命中（cookie 有效）：验平台 JWT → 共享账号 sign-in 拿会话 → 签发一次性 nonce → 返回 `{bridgeUrl: "<桥页>#nonce="}` | `ApTokenController#launch`（`GET /api/v1/admin/internal/ap/launch`） |
| **一次性 nonce 存储** | 不可猜 UUID、单次消费、短 TTL（默认 60s）；状态在 **Redis**（多副本安全），复用 `PlatformSsoService` 范式 | `ap/service/ApBridgeNonceStore.java` |
| **登录桥页** | 读 URL fragment 的 nonce（抹掉历史）→ `?nonce=` 兑换 → 写 localStorage 跳 AP。**无 nonce 时回退 cookie（dev 同源）** | `resources/ap/ap-bridge.html`（admin-center `GET /internal/ap/bridge` 返回，**不再门禁**） |
| **token 端点** | 带 `?nonce=` → 单次兑换会话（**无需 cookie**）；无 nonce → 回退平台 JWT 校验 + 现场 sign-in（dev） | `ApTokenController#token` / `ActivepiecesApiClient#signInShared` |
| **完整会话** | AP 会话 = localStorage `token` + `projectId`（都裸存）。只写 token 会死循环 | 桥页 + token/launch 端点 |
| **跨域 nonce 握手** | 取代「跨子域 cookie」：AP 域**无需平台 cookie**，故 admin 与 AP 可分属不同父域，**不再需要 `JWT_COOKIE_DOMAIN`** | `ApBridgeNonceStore` + `bridge.public-url` 配置 |
| **桥页公网地址** | `/launch` 拼 `bridgeUrl` 用的 AP 桥页地址；前端也用它作菜单显隐开关 | `activepieces.bridge.public-url` ← env `AP_BRIDGE_URL` |
| **前端运行时配置** | k8s 前端是一镜像 promote 到多环境，开关 URL 必须**运行时**注入 | `public/config.js` + `docker-entrypoint.sh` envsubst |
| **共享账号引导** | 空库时把共享账号 sign-up 成 AP 第一个用户（owner）。**没引导=sign-in 401=点击报 502**（见 §8） | `deploy/scripts/ap-bootstrap-shared-account.js` + `ap-bootstrap-job.yaml` |

> `ApTokenController#authz`（网关 auth_request 校验平台 JWT，200/401/404）保留以兼容 dev `:8085` edge；
> 方案 B 下 k8s ap-gateway 不需要它。

---

## 3. 改动清单

### 后端（admin-center / platform-security）
- **新增** `com.admin.ap` 包：
  - `controller/ApTokenController.java` —— `/internal/ap/launch`（方案 B 入口）、`/bridge`、`/token`、`/authz`（`bridge.enabled` 关闭时全 404）
  - `service/ApBridgeNonceStore.java`（**方案 B 新增**）—— Redis 单次 nonce（issue/consume）
  - `client/ActivepiecesApiClient.java` —— 共享账号 sign-in，返回 `ApSession(token, projectId)`
  - `config/ActivepiecesProperties.java` + `config/ApConfig.java` —— `activepieces.*` 配置（方案 B 加 `bridge.public-url`、`bridge.nonce-ttl-seconds`）
  - `exception/ActivepiecesApiException.java`（sign-in 失败 → 映射 502）
  - `resources/ap/ap-bridge.html` —— 桥页（方案 B：读 fragment 的 nonce，无 nonce 回退 cookie）
- **改** `application.yml` —— `activepieces.*`（含 `bridge.public-url: ${AP_BRIDGE_URL:}`、`bridge.nonce-ttl-seconds`）
- **改** `application-docker.yml` —— `activepieces.internal-url=http://activepieces:80`
- **（方案 A 遗留，AP 已不再需要）** `JwtProperties.cookieDomain` + `AuthServiceImpl#setAuthCookie`/`AuthController#expiredCookie` 的 `Domain`
  + `platform.security.jwt.cookie-domain`（`JWT_COOKIE_DOMAIN`）——**Superset 作者网关可能仍依赖**，AP 这条链已用 nonce 替代，别为了 AP 去配它。

### 前端（admin-center）
- **新增** `src/api/ap.ts`（**方案 B 新增**）—— `launchActivepieces()` 调 `/internal/ap/launch` 拿 `bridgeUrl`
- **新增** `public/config.js` —— 运行时配置（`window.__APP_CONFIG__.AP_BRIDGE_URL`，方案 B 下仅作菜单显隐开关）
- **改** `index.html` —— 引 `%BASE_URL%config.js`
- **改** `docker-entrypoint.sh` —— envsubst `AP_BRIDGE_URL` 注入 config.js
- **改** `src/env.d.ts` —— `Window.__APP_CONFIG__` 类型
- **改** `src/layouts/AdminLayout.vue` —— 「Activepieces」菜单：**先调 `launchActivepieces()` 再整页跳** `bridgeUrl`
- **改** `src/views/sso/SsoCallback.vue` —— `state=ap-bridge` 回跳改为**先 mint 再跳**（不能再跳裸 URL）
- **改** `i18n/{zh-CN,zh-TW,en}.ts` —— `menu.activepieces`

### 部署（dev）
- **改** `docker-compose.dev.yml` —— AP 服务（关宿主 8086、改 healthcheck 用 node、`AP_TELEMETRY_ENABLED=false`）、
  admin-center 注入 AP env、edge 映射 8085 + 注入 `AP_BRIDGE_URL`
- **改** `nginx-edge.conf` —— `:8085` server（authz/bridge/token + 反代 AP）
- **改** `.env` —— `ACTIVEPIECES_*`、共享账号、合法 32-hex 加密密钥
- **改** `build-and-deploy.ps1` —— 注记（前端入口 URL 走运行时,非构建时）

### 部署（k8s / 生产）
- **新增** `deploy/k8s/activepieces.yaml` —— AP Deployment + ClusterIP + Istio **仅 webhook**（所有环境，含生产 runtime）
- **新增** `deploy/k8s/ap-gateway.yaml` —— 非生产 AP 登录桥网关（Istio Gateway+VirtualService）
- **新增** `deploy/k8s/ap-bootstrap-job.yaml` —— 非生产 共享账号引导 Job
- **新增** `deploy/scripts/ap-bootstrap-shared-account.js` —— 幂等引导脚本（node）
- **改** `admin-center-frontend.yaml` —— 注入 `AP_BRIDGE_URL`（configmap；方案 B 下作菜单开关）
- **改** `config_map/{uat,preprod}/...` —— `ACTIVEPIECES_BRIDGE_ENABLED=true`、`ACTIVEPIECES_INTERNAL_URL`、
  `ACTIVEPIECES_SHARED_EMAIL`、`AP_BRIDGE_URL`（admin-center 用它拼 `bridgeUrl`、前端用它作开关）、`ACTIVEPIECES_POSTGRES_*`
  - `JWT_COOKIE_DOMAIN` —— **AP 方案 B 已不需要**；仅 Superset 作者网关若仍走跨子域 cookie 才保留。
- **改** `secret/{uat,preprod}/...` —— `ACTIVEPIECES_{ENCRYPTION_KEY,JWT_SECRET,SHARED_PASSWORD,POSTGRES_PASSWORD,REDIS_PASSWORD}`
- **改** `kustomization.yaml` —— 收录 `activepieces.yaml`
- **改** `ps1/apply-workflow-station-{all,istio-generated}.ps1` —— `-IncludeApBridgeGateway` 开关
  （默认排除 `ap-gateway.yaml` + `ap-bootstrap-job.yaml`，非生产用开关纳入）

---

## 4. dev：配置与运行

`.env` 关键项：
```
ACTIVEPIECES_GATEWAY_PORT=8085
ACTIVEPIECES_BRIDGE_ENABLED=true
ACTIVEPIECES_INTERNAL_URL=http://activepieces:80
ACTIVEPIECES_SHARED_EMAIL=hermes-svc@platform.local
ACTIVEPIECES_SHARED_PASSWORD=<dev 密码>
ACTIVEPIECES_ENCRYPTION_KEY=<32 位 hex>      # openssl rand -hex 16
ACTIVEPIECES_JWT_SECRET=<任意>               # 改了会让旧 token 失效
```

构建/部署（任一）：
```
./build-and-deploy.ps1 -Service admin-center           # 后端端点
./build-and-deploy.ps1 -Service admin-center-frontend  # 入口按钮 + 运行时配置
./build-and-deploy.ps1 -Service edge-frontend          # :8085 网关
# 或 docker compose -f docker-compose.dev.yml build/up 对应服务
```

入口：admin 左侧菜单「Activepieces 自动化」→ 前端调 `/internal/ap/launch` 现签 nonce → 跳
`http://localhost:8085/__ap/bridge#nonce=...`。dev 同源下也可不带 nonce 走 cookie 回退。
首次空库需引导共享账号（见 §6），否则点击报 502。

---

## 5. k8s / 生产部署

> **前置:AP 镜像现在是「仓库内源码构建」,不再是镜像上游二进制。**
> k8s manifest 引用的是 `<Registry>/workflow-station2/activepieces:0.84.0-ee-removed` ——
> 由**本仓库 `activepieces/` 源码树 + `activepieces/Dockerfile`** 构建的 HERMES vendored 镜像:
> **EE 剥离 + 去 bun(X-4,运行时装包改 pnpm) + 末层预烘焙白名单 pieces(`activepieces/hermes/pieces.json`,X-3 气隙)**。
> 与 dev compose 的 `activepieces` 服务同源同构。
>
> ⚠️ **不要再用 `mirror-thirdparty-images-k8s.ps1` 同步 AP**——那条路径拉的是上游
> `activepieces/activepieces:0.84.0` 二进制:**既没剥 EE、没去 bun、也没预装 pieces**,气隙集群里跑不通
> (该脚本的 activepieces 条目属历史遗留,其余 redis/kafka/kong 仍照常用它)。
>
> 正确做法:**`build-and-push-k8s.ps1` 现在会一并构建并推送它**(2026-08-07 起),
> 用 `-ApImageTag` 覆盖标签、`-SkipActivepieces` 跳过(平台代码单独发版时常用),
> 或 `-Services activepieces` 只出这一个镜像。它**不跟 `-Tag` 走** —— `activepieces.yaml`
> 把标签钉在 vendored AP 版本上,改标签要连 manifest 一起改。
> 等价的手工命令仍然有效:`docker build -t <Registry>/workflow-station2/activepieces:0.84.0-ee-removed activepieces/`
> → push 到 Nexus;或 `docker save | gzip` 带进内网 `docker load`(见 `activepieces/hermes/README.md`)。
> `ap-bootstrap-job.yaml` 用的也是同一个镜像,一并就位。
>
> 镜像只含**运行时半**(piece 的可执行包)。**元数据半**(`piece_metadata` 行)不在镜像里,但**已不需要手工灌**:
> `ap-bootstrap-job.yaml` 的 `ap-provision-db` initContainer 会从 `ap-pieces-seed` ConfigMap
> (渲染时由 `pieces-seed.sql` gzip 注入)自动执行,**并在灌完发一条 Redis 消息让 AP 的 registry 缓存失效**
> ——不用重启 AP(重启 Deployment 才需要 RBAC)。缓存失效不能省,原因见 §8 末行。

**生产（runtime only）**：照常部署（`activepieces.yaml` 在默认集里），Istio 只放 `/api/v1/webhooks`，
不带 `-IncludeApBridgeGateway` → 无 UI 网关、无共享账号 Job。configmap/secret 不设 bridge → admin-center 端点 404。

**uat/sit（有 UI）**：
1. **DNS**：`hermes-workflow-activepieces.<BASE_DOMAIN>`（或你的 AP 主机，IKP 实例是
   `hermes-workflow-activepieces.<集群域>`）指向 ingressgateway（按需 TLS）。
2. **secret**（每环境一次）：填 `ACTIVEPIECES_SHARED_PASSWORD`、`ACTIVEPIECES_ENCRYPTION_KEY`（32-hex）、
   `ACTIVEPIECES_JWT_SECRET`、AP 库密码。
3. **configmap**：`AP_BRIDGE_URL = http(s)://<AP 主机>/__ap/bridge`（admin-center envFrom 自动注入 →
   `bridge.public-url`，`/launch` 用它拼跳转地址；前端 config.js 也用它作菜单开关）。
4. **部署带开关**：
   ```
   ./apply-workflow-station-all.ps1 -Environment <uat|preprod> -BaseDomain <域> ... -IncludeApBridgeGateway
   ```
   纳入 `ap-gateway.yaml` + `ap-bootstrap-job.yaml`。
5. Job 自动把共享账号建成 AP 第一个用户（空库时）。**务必确认 Job 跑成功**——没建共享账号，
   点 AP 会 502（见 §8「Service temporarily unavailable」）。

> **方案 B：admin 域与 AP 域可以不同父域，无需 `JWT_COOKIE_DOMAIN`。**
> 鉴权在 admin 域 `/launch` 完成（cookie 在自己域有效），AP 域只收一次性 nonce。
> 所以 IKP 上 admin（`hermes-uat.hk.hsbc`）与 AP（`hermes-workflow-activepieces.<集群>`）分属不同父域**没问题**，
> 也**不必**把 admin 搬到与 AP 同父域的 internal-proxy 主机下。**唯一要求**：admin-center 这两条路径在各自域可达——
> - admin 域：`/api/v1/admin/internal/ap/launch`（走 Kong/`/api/v1/admin` 路由，带平台 cookie）
> - AP 域：`/__ap/bridge`、`/__ap/token`（ap-gateway VirtualService rewrite 到 admin-center-service）
>
> **ap-gateway 不需要 ext_authz / AuthorizationPolicy**：`/__ap/token` 自己校验 nonce，桥页 HTML 无机密。

---

## 6. 供给引导（幂等）

**AP 把全部状态放在 Postgres 里。** 新环境、重建卷、或手工 drop 掉 AP 那套表之后，AP 自己的 migration 会把
schema 建回来但**数据一条不剩**——没有 platform / project / signing_key / piece_metadata。**重打镜像不恢复任何一项。**
症状是 DW 的 Automation 页签报错，而各处日志都是绿的（桥拿着一个已不存在的 signing key 去签名，AP 只回 401）。

一个可用的 AP 需要四项，前三项现已自动化：

| # | 项 | 谁来做 | 幂等条件 |
|---|---|---|---|
| 1 | platform + 默认 project | `ap-bootstrap-shared-account.js` | sign-in 带 platformId 即跳过 |
| 2 | project `externalId`（默认 `hermes-main`） | `ap-provision-db.js` | 只填 `NULL`；已有别的值则**报错不改写** |
| 3 | `piece_metadata`（设计器半） | `ap-provision-db.js` | seed 是逐件 DELETE+INSERT，每次部署重放 |
| 4 | **signing-key**（L7 per-user 才需要） | ⚠️ **仍是手工** | 见下 |

- **dev**：`build-and-deploy.ps1` 的 `Invoke-ApProvisioning` 自动跑全部四项（含 signing-key，私钥回写 `.env`）。
- **k8s**：`ap-bootstrap-job.yaml`（随 `-IncludeApBridgeGateway` 纳入）——两个 initContainer 依次做 1、2、3，
  主容器 `ap-verify` 只读校验四项，**缺哪项就让整个 Job 失败**并打印逐条修复命令。
  用 initContainer 而不是并列容器，是因为同 Pod 的 containers 并行启动，而这几步有严格先后。

> **第 2 步的顺序很关键**：`getOrCreateProject` 按 `externalId` 查项目，查不到就**自己新建一个**。
> 若在第一次 managed 换取之后才 stamp，共享账号和 per-user 账号会分处两个 project，互相看不见对方的 flow。

> **signing-key 为什么不自动化**（裁决 [D11](../docs/ap-integration/DECISIONS.md#d11)，2026-07-29——
> **已定案，不是暂缓**）：私钥只在创建时返回一次，要写进
> `workflow-platform-secrets`。让 Job 自动写 Secret 需要一个能改 Secret 的 ServiceAccount + RBAC，
> 气隙/合规集群未必批得下来；且 Job 重跑会轮换掉正在使用的密钥。
> **另注：k8s 侧 admin-center 目前根本没接 managed 鉴权**——`admin-center.yaml` 与各环境
> configmap/secret 里都没有 `ACTIVEPIECES_MANAGED_*` 三个键，L7 只在 dev 启用。要在集群上用，
> 得先补这套接线，再谈 signing-key 的自动化。

第 1 步的脚本 `deploy/scripts/ap-bootstrap-shared-account.js` 幂等地把共享账号建好并**完成 onboarding**。

> **⚠️ AP 0.84 CE 关键行为（实测 2026-06-30）**：sign-up **只建 `user_identity`，不自动建 platform/project**——
> 账号停在 **ONBOARDING** 态，sign-in 返回 `projectId=null` 的 ONBOARDING token。登录桥需要 projectId，缺了 AP 会循环/卡 onboarding。
> 所以光 sign-up **不够**，必须再调 **`POST /api/v1/platforms {name}`**（用 ONBOARDING token）完成 onboarding，
> AP 才会建 platform + 默认 project，之后 sign-in 才返回 **USER token 带非空 projectId**。
> **引导脚本已包含这一步**（`ACTIVEPIECES_PLATFORM_NAME` 可定制 platform 名，默认 `Hermes Automation`）。

脚本流程：sign-in 探测 → 401 则 sign-up 建身份 → 若仍无 platform（ONBOARDING）则 `POST /platforms` → 复核 projectId 非空。
幂等：已 onboard（sign-in 带 platformId）直接跳过；未配置共享账号则跳过。

- **k8s**：随 `-IncludeApBridgeGateway` 部署的 Job 自动跑；或手动 `kubectl exec deploy/activepieces -- node - < deploy/scripts/ap-bootstrap-shared-account.js`（带 env）。
  Job 里的三个脚本**不是内嵌副本**——`ap-bootstrap-job.yaml` 只放占位符，渲染时由
  `apply-workflow-station-istio-generated.ps1` 从 `deploy/scripts/` 原样注入，和 dev 跑的是同一份文件。
  （曾经那里是手抄副本并且漂移了：停在 sign-up 就返回、缺了 `POST /v1/platforms`，于是空库上 Job 报成功
  退出而 AP 里什么都没有。）
- **dev**：`docker exec -e ACTIVEPIECES_SHARED_EMAIL=... -e ACTIVEPIECES_SHARED_PASSWORD=... -e AP_INTERNAL_URL=http://localhost:80 -i platform-activepieces-dev node - < deploy/scripts/ap-bootstrap-shared-account.js`

**铁律**：secret 里的密码设好后别改（改了不会改 AP 已存在账号的密码 → 登录失败 → 循环）。projectId 动态取、不硬编码。

---

## 7. Flow 发布（非生产 → 生产）

**目标**：非生产（uat/sit）用 UI 搭好/测好 flow → 受控发布到生产（runtime-only、无 UI）→ 可审计、可回滚。

**AP CE 能力盘点（实测）**：
- ✅ flow 操作 `IMPORT_FLOW` / `LOCK_AND_PUBLISH` / `CHANGE_STATUS` 可用 → 可编程导入+发布+启用。
- ❌ `git-repos`（Git Sync）/ `project-releases` 返回 404 → **EE 功能，CE 不可用**。
- 故 CE 的发布只能走 **flow 导出/导入 API**。

**方案对比**：

| 方案 | 隔离 | 审计/回滚 | CE 可行 | 评 |
|---|---|---|---|---|
| **A. git 为源 + API 导入**（推荐） | 强 | git 历史 | ✅ | 审计/回滚最佳 |
| B. 直接 API 导出→导入（无 git） | 强 | 弱 | ✅ | MVP |
| C. 非生产/生产共用同一 AP 库 | 无 | 无 | ✅ | 生产=非生产数据，**否决** |
| D. AP 内置 Git Sync | 强 | git | ❌ | CE 不支持 |

**推荐方案 A —— git 单一事实来源 + API 发布**：
```
非生产 AP ──导出──→ git(deploy/ap-flows/<flow>.json)──发布脚本/Job──→ 生产 AP
  (UI 搭/测)        (评审 + 版本 + 审计)                  (纯 API，服务端 token)
```
1. **搭建**：非生产经桥进 AP 搭/测 flow。
2. **导出**：脚本用共享账号 token 调 `GET /api/v1/flows/:id` 拿 JSON → 提交 git。
3. **发布**：CI 或一次性 Job 读 git JSON，对生产 AP（`activepieces-service:80`，生产共享账号 token）：
   - 不存在 → `POST /api/v1/flows` 建 → `IMPORT_FLOW` 灌版本 → `LOCK_AND_PUBLISH` → `CHANGE_STATUS=ENABLED`；
   - 已存在 → `IMPORT_FLOW` 覆盖新版本 → 重发布。按 flow 名/外部 id 对齐，**幂等**。
4. **回滚** = 重新导入上一个 git 版本。

**关键注意（坑）**：
- **连接/密钥不跟着导**：flow 引用的 connection 是 per-环境凭据，导出 JSON 不含密钥。**生产须预先建好同名 connection**（可另写一次性脚本），否则 flow 跑不起来。
- **webhook URL 自动适配**：生产 `AP_WEBHOOK_URL` 指向生产域，触发地址天然不同，flow 不用改。
- **projectId 不跨环境**：导入用目标环境自己的 projectId（token 端点已动态取）。
- **生产无 UI**：发布纯走 API（服务端 token），不依赖桥。

**实现路径**：
- ✅ **MVP（方案 B）已落地并实测**：`deploy/scripts/ap-export.js` + `ap-import.js`（自包含 node，照 bootstrap 范式）+
  git 目录 `deploy/ap-flows/`（见其 [README](ap-flows/README.md)）。dev 实测:导出 aptest → JSON → 幂等 re-import（创建/覆盖/发布/启用全 200）。
  各 AP CE flow 操作实测可用:`POST /flows`(201)/`IMPORT_FLOW`(200)/`LOCK_AND_PUBLISH`(200)/`CHANGE_STATUS`(200)。
- **导出 + 发布都走 Jenkins(已定案)**：整条 test→git→prod 生命周期由 **Jenkins** 驱动(不是 k8s Job):
  - **导出**:[deploy/ci/Jenkinsfile.ap-flows-export](ci/Jenkinsfile.ap-flows-export) —— 从非生产导出(`FLOW=all`/单条)→ 写 `deploy/ap-flows/` → 提交推分支 → 开 PR。
  - **发布**:[deploy/ci/Jenkinsfile.ap-flows-publish](ci/Jenkinsfile.ap-flows-publish) —— 手动触发带参数(选环境/选 flow + prod 二次确认)→ 读 git JSON → 跑 `ap-import.js` 发到目标 AP。
  - 凭据走 Jenkins credentials;两份模板里都标了 **按环境填的 TODO**(agent/AP_URL/credentialsId/git-push 凭据)。
- **admin-center 不再需要 AP 发布/导出 UI**：CI 全包了。admin 的「Activepieces」菜单(进 AP 搭 flow 的 SSO 桥)**保留**,那是另一回事。

**三个待定项的决策**：① git 组织=**一 flow 一 JSON**,按 `displayName` 幂等对齐;② 触发=**Jenkins 流水线(导出+发布两个 job)**;③ 生产 connection=**手动预建同名**(ap-export 导出时会提示引用了哪些 connection)。

**已知边界(实测)**:① connection 不跟着导,生产须预建同名(见 §11.5);② **flowId 跨环境会变**——目标环境新建 flow 有新 id,ap-import.js 末尾打印,BPMN 的 `ap:flowId` 按目标环境填。

---

## 8. 关键踩坑与修正（排障必读）

| 现象 | 根因 | 修正 |
|---|---|---|
| AP crash-loop `AP_ENCRYPTION_KEY invalid` | 0.84 要求 **32 位 hex** | 用 `openssl rand -hex 16` |
| AP 容器一直 unhealthy | AP 镜像**无 wget/curl** | healthcheck 改 `node -e` 探 `/api/v1/flags` |
| admin 入口按钮不显示 | docker 前端是 `vite build`=生产模式，`import.meta.env.DEV===false`，DEV 兜底被 tree-shake | 走**运行时** `window.__APP_CONFIG__.AP_BRIDGE_URL`（entrypoint envsubst） |
| 改 nginx-edge.conf 后 `nginx -s reload` 不生效/截断 | Docker-for-Mac 单文件 bind-mount，Edit 换 inode | `docker compose up -d --force-recreate edge-frontend` |
| AP 白屏，`/api/* 302` 跳登录 | 平台 JWT 门禁加到了 `location /`，拦了 AP 自身 XHR | 门禁只加 `/__ap/bridge`+`/__ap/token`，AP 流量不门禁 |
| 进 AP 后 `/flows ↔ /sign-in` 死循环 + WebSocket 报错 | 桥页只写了 `token`、漏 `projectId`，AP 没当前项目；WebSocket 报错只是循环导航的**症状** | token 端点返回 `{token,projectId}`，桥页 `localStorage.clear()` 后两者都写 |
| 改 `AP_JWT_SECRET` 后又循环 | 浏览器旧 token（旧密钥签、未过期）被 AP 判无效 | 桥页每次 `localStorage.clear()` 写新 token；密钥别乱改 |
| **点 AP 报「Service temporarily unavailable」/ `/launch` 502** | 共享账号没引导：AP sign-in 返 `401 INVALID_CREDENTIALS` → `ActivepiecesApiException` → 502 | 跑引导脚本建共享账号（§6）；确认密码与 `ACTIVEPIECES_SHARED_PASSWORD` 一致。日志看 `signInShared`/`INVALID_CREDENTIALS` |
| **`/launch` 502 且日志「public-url is not configured」** | `AP_BRIDGE_URL` 没注入 → `bridge.public-url` 空 | configmap/compose 设 `AP_BRIDGE_URL=<AP 主机>/__ap/bridge`，重启 admin-center |
| **`/launch` 500「No mapping / No endpoint」** | 运行的是**旧镜像**，没有方案 B 的 `/launch` | 重建镜像：Dockerfile 是 `COPY target/*.jar`，**必须先 `mvn ... package`** 出新 jar 再 `docker build`（仅 restart 容器不更新代码） |
| 进 AP `/flows ↔ /sign-in` 循环、sign-in 返回 `projectId=null`、token 类型 `ONBOARDING` | AP 0.84 CE 的 sign-up **不自动建 platform/project**，账号卡 ONBOARDING（见 §6） | **重跑引导脚本**——新版会 `POST /api/v1/platforms` 完成 onboarding（建 platform+默认 project），之后 sign-in 带非空 projectId。旧版脚本只 sign-up、留下这个坑 |
| 清空 `piece_metadata` 后 pieces **又自己回来了** | **后台同步 Job（`AP_PIECES_SYNC_MODE` 默认 `OFFICIAL_AUTO`）** 启动即从 cloud.activepieces.com 重新拉元数据入库。（注：曾同时配的 `AP_PIECES_SOURCE=DB` 经 0.84.0 全仓 grep 核实**不存在、从未被读取**，已删除） | 设 `AP_PIECES_SYNC_MODE=NONE`（compose 与 k8s 均已加），再清表 |
| psql 导入 piece 元数据后，列表 `/api/v1/pieces` 有、设计器单查 404 `piece_metadata_not_found` | piece registry 缓存在 AP **进程内存**（piece-cache.ts），只在经 AP 自身 API 改动时用 Redis pubsub 失效；直接写表不触发。列表直查 DB、单查走缓存 | 导入 seed 后**重启 AP**（dev `docker restart`；k8s `rollout restart`） |

---

## 9. 已知限制 / 待办

- **flow 发布通道**：✅ **MVP 已实现并实测**（见 §7,git 为源 + ap-export.js/ap-import.js + `deploy/ap-flows/`；Git Sync 经实测 CE 不可用）。剩生产 k8s 发布 Job(方案 A 完整形态)待做。
- **方案 B（跨域 nonce 握手）已在 dev 实测通过**（2026-06-30）：admin 域 `/launch` 200 → AP 域 `#nonce=` → `/__ap/token?nonce=` 200 → 进 AP。
  代码 `4382f303 on common_0627`。**k8s/IKP 真集群仍待验证**（DNS、secret、ap-gateway rewrite、共享账号 Job）。
- **AP 不再依赖 `JWT_COOKIE_DOMAIN`**：admin 与 AP 可分属不同父域。若 Superset 作者网关仍走跨子域 cookie，那条单独保留。
- **会话过期/直接访问 AP host**：无有效 nonce 时桥页换不到 token（无自动跳平台登录，没用 ext_authz）。
  正常入口（从已登录 admin 点「Activepieces」→ `/launch` 现签 nonce）不受影响。
- **AP 共享平台库 `public`（已定案：维持不变）**：AP 用通用表名（user/project/flag/file/folder/platform/flow/app_connection…），
  与平台 135 张前缀表（`ac_/sys_/dw_/up_/we_`）**实测不冲突**。
  - 独立 **schema** 方案**不可行**（实测）：AP 0.84 的连接两条路径都不暴露 TypeORM `schema` 选项，只能经
    `POSTGRES_URL` 的 `search_path` 注入；但 search_path-only 时 AP 从零跑迁移会断（只建出 `migrations` 表，
    随后 `SELECT * FROM flow` 报 relation 不存在）。n8n 能用 schema 是因为它显式传了 TypeORM `schema`，AP 没有。
  - 唯一能做**硬隔离**的是给 AP **独立 database**（dev 可仿 `n8n_dev`，生产由 DBA 开）。当前不冲突，故维持 public。
- **Istio Job sidecar**：bootstrap Job 关了 sidecar 注入；若 mesh 为 STRICT mTLS 需另行处理（见 yaml 注记）。
- **外网下载封禁（IKP 合规，2026-07-02）**：compose 与 `deploy/k8s/activepieces.yaml` 均已设
  `AP_PIECES_SYNC_MODE=NONE` + `AP_CLOUD_AUTH_ENABLED=false` + `AP_TELEMETRY_ENABLED=false`
  （曾配的 `AP_PIECES_SOURCE=DB` 经 0.84.0 全仓 grep 核实该版本无此变量、从未生效，2026-07-22 已删；断外网靠 `AP_PIECES_SYNC_MODE=NONE`）——
  AP 不再从 cloud.activepieces.com / 公网 npm 同步或下载任何 pieces；piece 目录只来自 `piece_metadata` 表，
  **全新部署 = 空目录**。后果：flow 设计器里没有可选 piece（含 `piece-webhook`），要用 pieces 必须内部投放
  （DB ARCHIVE 或内部 npm 源）。历史外部数据已于 2026-07-02 全量清除（piece_metadata 10k+ 行与容器缓存）。
- **离线投放 pieces = 元数据(DB) + 包(内部 npm 源) 两半**（pieces MIT 开源，可自由镜像）：
  ①运行时装包用的是 **pnpm**（X-4：全环境禁 bun，vendored 镜像已去 bun）——worker `piece-installer` →
  `pnpm install --ignore-scripts --config.node-linker=isolated`，workspace 在容器 `/usr/src/app/cache/v11/common/`。
  **`node-linker=isolated` 不能改**：引擎的 piece 加载器按 `pieces/<name>-<ver>/node_modules/<name>` 解析，
  hoist 会让它找不到（原 bun 的 isolated 布局与此一致，去 bun 时必须复刻）。pnpm 认 `NPM_CONFIG_REGISTRY`。
  ②已加 `NPM_CONFIG_REGISTRY`：k8s 走 configmap `ACTIVEPIECES_NPM_REGISTRY`（uat/preprod 默认
  **fail-closed 的 `.invalid` URL**——**空值会静默回落公网 npmjs（实测）**，接 Nexus npm repo 时替换）；
  dev compose `${ACTIVEPIECES_NPM_REGISTRY:-}` 默认空=公网（仅本机 dev 可接受）。
  ③元数据行获取：dev 临时开 `AP_PIECES_SYNC_MODE=OFFICIAL_AUTO` 同步→ `COPY (SELECT ... WHERE name IN (白名单))`
  导出→改回 NONE 清表→导入集群库；`piece_metadata` 表即 piece 白名单。
  ④**气隙硬开关 `AP_PIECES_OFFLINE_INSTALL=true`**：置真后 pnpm 只从镜像内烘焙的离线 store
  （`AP_PIECES_OFFLINE_STORE_DIR`，默认 `/usr/src/app/pnpm-offline-store`）解析，闭包外依赖 **fail-closed**（直接失败，
  不静默回落公网）。此时命令行里的 `--registry` 只是 pnpm 离线元数据缓存的**命名空间**（按 registry 主机名归档），
  不是网络目标——改它会让缓存找不到。
  *（历史注记：旧版本靠镜像根 `bunfig.toml` 的 `minimumReleaseAge` 拦新包，该文件随去 bun 已删除，不必再查。）*
  ⑤**已落地的投放通道**：白名单 + 预装脚本在 `activepieces/hermes/`（预装是 `activepieces/Dockerfile`
  的最后一层），元数据 seed SQL 在 `deploy/pieces/`，运行时零联网，详见 `activepieces/hermes/README.md`
  与 `deploy/pieces/README.md`；Nexus npm 源改为兜底防线，非必需。

---

## 10. 验证状态

- ✅ **AP 桥全链路已实测（dev）**：admin 入口 → 桥 → 换 token → 进 AP（共享账号 ADMIN，/flows 正常、不循环）。
- ✅ **方案 B 跨域 nonce 握手已实测（dev，2026-06-30）**：`/launch` 200 带 `bridgeUrl` → `/__ap/token?nonce=` 200；
  `/launch` 无 cookie 返 401、bogus nonce 返 401、`signInShared` 200。
- ✅ 引导脚本三路径已测：幂等成功 / 未配置跳过 / 密码不符明确报错。
- ✅ **BPMN service-task → AP 端到端已实测（dev，2026-06-26）**：部署含 AP 节点的 BPMN（部署期自动绑 `${apTaskExecutor}`）→
  起实例 → 调 aptest sync webhook（`http://activepieces:80/api/v1/webhooks/<flowId>/sync`）→ Return Response 结果按 outputMapping
  回写流程变量 → 流程走到 End；`wf_ap_execution_record` 出 SUCCESS。
- ✅ **flow 发布通道已实测（dev）**：`ap-export.js` 导出 aptest → JSON → 幂等 re-import（create/overwrite/publish/enable 全 2xx）。
- ✅ **纯自动化流程门户完成状态修复已实测**：见 §11.5「卡 RUNNING」。
- 📦 **k8s/生产**：清单齐全、YAML 校验通过、ps1 开关接通；Jenkins 发布流水线模板就绪；需集群侧验证（DNS/secret/ext_authz/mTLS、Jenkins 凭据与 AP 可达性）。

---

## 11. BPMN Service Task 调用 AP flow（Path B，已实现）

让 **BPMN 流程**走到某一步时自动触发一个 **AP flow** 并把结果写回流程变量。**取代已弃用的 n8n service-task 通道**
（n8n 已整体移除，仅 developer-workstation 的 AI 生成 webhook 例外保留——那是另一套独立服务）。

### 11.1 设计抉择（两条都选了"轻"的）

| 抉择 | 选定 | 原因 |
|---|---|---|
| **连接建模** | **共享实例 + flowId**（不建 `ac_*_config` 配置表） | AP 是每环境单实例 runtime；service task 只存 `ap:flowId`，**跨环境可移植**（n8n 存全量 URL，promote 时要改）。webhook URL 由引擎按环境拼。AP CE webhook 免鉴权，无需 apiKey。 |
| **执行模式** | **同步 webhook**（无回调/无 Redis/无超时扫描） | POST 到 AP 的 sync webhook，flow 末尾用 **Return Response** 直接把结果回在 HTTP 响应里；引擎拿到就地映射回流程变量。比异步回调少一整套 token 机制，flow 作者也不用每条都接回调节点。 |

### 11.2 数据流

```
BPMN 流程 ──→ service task (serviceType=ap, ap:flowId=xxx)
   │  部署时 ProcessDeploymentManager 给该 task 绑定 flowable:delegateExpression=${apTaskExecutor}
   ▼
ApTaskExecutor.execute()  (workflow-engine, 同步 JavaDelegate)
   │  ① 读 ap:* 扩展属性；inputMapping 从流程变量抽数据；/api/* 相对路径转 file-service 绝对 URL
   │  ② 拼 URL = <activepieces.webhook-base-url>/api/v1/webhooks/<flowId>/sync（SSRF 校验，AP 主机加白）
   │  ③ 同步 POST（指数退避重试 ap:retryCount 次）；建 wf_ap_execution_record（SERVICE_TASK）
   ▼
AP flow 同步执行 → Return Response 返回 JSON
   │  ④ outputMapping 把响应映射成流程变量 → execution.setVariables(...)；记录置 SUCCESS
   ▼
Flowable 继续往下（失败则抛异常，可被 BPMN 错误边界捕获）
```

### 11.3 落点（代码）

**后端（workflow-engine-core，全部 `com.workflow`）**
- `component/ApTaskExecutor.java` —— `@Component("apTaskExecutor")`，`JavaDelegate`。`execute()` 走 service task 同步；
  `executeSynchronous(ApActionRequest)` 供未来 Action 模式（已暴露 REST，前端未接）。
- `component/ProcessDeploymentManager.java#bindActivepiecesServiceTasks` —— **部署期绑定**：用 Flowable `BpmnXMLConverter`
  解析 BPMN，给带 `serviceType=ap`/`ap:flowId` 的 service task 注入 `flowable:delegateExpression="${apTaskExecutor}"`。
  *（这步是 n8n 当年从设计器这条路一直没接通的关键缺口——designer 只写扩展属性、从不写 delegate，故 n8n service task 实际从未经设计器跑通；AP 在部署期补上。）*
- `entity/ApExecutionRecord.java` → 表 `wf_ap_execution_record`（无 callback_token；status/source_type 同 n8n）。
- `repository/ApExecutionRecordRepository.java`、`util/ApVariableMappingUtil.java`（input/output 映射，支持点号嵌套路径）。
- `dto/request/ApActionRequest.java`、`dto/response/ApExecutionResult.java`。
- `controller/ApExecutionController.java` —— `GET /api/workflow/ap/executions[/{id}]`（执行记录查询）、`POST /api/v1/ap/execute`（Action 同步）。
- 配置：`activepieces.webhook-base-url`（`application.yml` 默认 `http://localhost:8086`；`application-docker.yml` `http://activepieces:80`；prod 经 `ACTIVEPIECES_WEBHOOK_BASE_URL`）。`RestTemplate` 读超时 10 分钟，够同步等长 flow。

**前端（developer-workstation 设计器）**
- `components/designer/properties/ServiceTaskProperties.vue` —— 服务类型新增 `ap`（替原 `n8n`），选中渲染 `ApTaskPropertiesPanel`。
- `components/designer/properties/ApTaskPropertiesPanel.vue` —— 配 flowId / 可选 URL 覆盖 / 超时 / 重试 / 输入输出映射表。
- `utils/apConfigSerializer.ts` + `api/ap.ts` —— 把配置序列化进 `<custom:property name="ap:*">` 扩展属性。
- i18n：`properties.serviceTypeAp` + `properties.ap*`（en/zh-CN/zh-TW）。

### 11.4 BPMN 扩展属性（写在 service task 的 `<extensionElements>` 里）

| 属性 | 必填 | 含义 |
|---|---|---|
| `serviceType` | 是 | 固定 `ap`（绑定标记之一） |
| `ap:flowId` | 是 | AP flow 的 webhook flow id（绑定标记之一；拼 URL 用） |
| `ap:webhookUrl` | 否 | 完整 sync webhook URL 覆盖；留空则按环境拼 |
| `ap:inputMapping` | 否 | `[{"source":"流程变量","target":"AP参数"}]` |
| `ap:outputMapping` | 否 | `[{"source":"AP输出字段","target":"流程变量"}]`（支持 `a.b.c` 嵌套） |
| `ap:timeoutSeconds` | 否 | 默认 120 |
| `ap:retryCount` | 否 | 默认 3（指数退避） |

### 11.5 关键注意（坑）

- **AP flow 必须以 Return Response 结尾**：同步 webhook 靠它回结果；否则 outputMapping 拿不到数据。
- **connection 不跨环境**：flow 引用的凭据是 per-环境的，导出不含密钥——**生产须预建同名 connection**（见 §7）。
- **同步占线程**：service task 同步等待至多 `ap:timeoutSeconds`（受 RestTemplate 10 分钟读超时上限约束）。长流程慎用、设合理超时。
- **SSRF**：URL 经 `SsrfProtection.validate`，仅把 `webhook-base-url` 的主机加白（docker 私网名 `activepieces` 因此放行；指向其它私网主机仍被拦）。
- **文件**：inputData 里 `/api/*` 相对路径自动转 `file-service` 绝对 URL，便于 AP 经 Docker 网络取文件。
- **纯自动化流程门户"卡 RUNNING"（已修，2026-06-26）**：无用户任务的流程（`Start → AP → End`）在 start 调用内**瞬间跑完**；
  门户申请状态靠引擎 `PROCESS_COMPLETED` 回调 user-portal `POST /api/portal/processes/{id}/complete` 翻 COMPLETED，但该端点要
  **非空 `X-Internal-Service-Token` 头**、引擎原来从不发（403 被拒）→ 申请永远 RUNNING。审批流靠"完成最后一个任务时置状态"
  把这个潜伏 bug 遮住了，纯自动化流程才暴露。修复：`ProcessCompletionListener` 调用时带该头（`platform.internal.service-token`）。
- **想在门户里像"正常申请"（有待办/可交互）**：纯自动化流程没人工步骤、瞬间结束，门户看不到"下一步"。
  在 AP 节点前/后加一个**用户任务**（`assigneeType=INITIATOR` + 挂一个动作如 `PROCESS_SUBMIT`，动作存 `sys_action_definitions`），
  流程才会停在待办、走正常完成路径。**正经做法是在设计器里建表单+任务**；只想要"有个步骤"则加一个无表单的用户任务即可。
- **申请编号显示 `-` 属正常**：全库表定义 0 个用 `request_id_config`——编号功能没人用，`business_key` 空即显示 `-`，非缺陷。

### 11.6 待办

- **AP Action 模式（用户态）**：后端 `POST /api/v1/ap/execute` 已就绪，但**前端用户态 Action UI 未实现**（随 n8n 一并移除了 n8n 的 Action 对话框/自动填充）。若需要，按 §11.3 后端能力补一套 AP Action 面板即可。
- **k8s/生产集群验证**：dev 端到端已通；生产需真集群跑一遍（含 Jenkins 发布流水线填好 3 处 env 信息后试跑、生产预建 connection）。
