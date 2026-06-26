# Activepieces 集成（社区版 + 网关共享账号）

把 Activepieces（AP）接入平台：**非生产**（dev/uat/sit）经统一 SSO 用**共享账号**进入 AP UI 搭建自动化；
**生产**只当 workflow **runtime**（不开 UI，只放 webhook）。对应 dev 的 `:8085` 网关方案，k8s 用 Istio。

> 与 [SUPERSET_SSO_INTEGRATION.md](SUPERSET_SSO_INTEGRATION.md) 同类，但 AP 的登录态在 **localStorage**（非 cookie），
> 故必须用**客户端登录桥**换 token，而不能像 Superset 那样服务端注入 header。

---

## 1. 方案与数据流

```
非生产（dev :8085 / k8s hermes-workflow-activepieces.<域>）:

  浏览器 ──→ AP 网关
   │  ① /__ap/bridge（受平台 JWT 门禁）── admin-center 返回桥页 HTML
   │      桥页 JS: fetch /__ap/token
   │  ② /__ap/token（受平台 JWT 门禁）── admin-center 用共享账号服务端 sign-in AP → 返回 {token, projectId}
   │      桥页: localStorage['token']=token; localStorage['projectId']=projectId; location.replace('/')
   │  ③ /（不门禁）── 反代 AP；AP 读 localStorage 已登录（共享账号 ADMIN）
   └──────────────────────────────────────────────────────────────

生产（k8s）: 只 activepieces.<域>/api/v1/webhooks 经 Istio 暴露；UI 不开；不需要共享账号。
```

**安全模型**：换 token 这步（桥页 + token 端点）受平台 JWT 门禁；AP 自身流量（`/`、`/api/*`）不门禁，
因为 AP 前端调自己 API 用 AP token（Authorization 头）、不带平台 cookie，门禁会把这些 XHR 401→白屏。
未登录平台的人到 `/` 只看到 AP 原生登录页（无 token，无害）；**换 token 的唯一入口受门禁**，前提不变。

---

## 2. 核心机制

| 机制 | 说明 | 落点 |
|---|---|---|
| **登录桥页** | 取共享账号 AP token + projectId，写 localStorage，跳进 AP。dev/k8s 共用一份 | `backend/admin-center/.../resources/ap/ap-bridge.html`（admin-center `GET /internal/ap/bridge` 返回） |
| **token 端点** | 校验平台 JWT → 共享账号服务端 sign-in → 返回 `{token, projectId}` | `ApTokenController#token` / `ActivepiecesApiClient#signInShared` |
| **authz 端点** | 网关 auth_request 校验平台 JWT（200/401/404） | `ApTokenController#authz` |
| **完整会话** | AP 会话 = localStorage `token` + `projectId`（都裸存）。只写 token 会死循环 | 桥页 + token 端点 |
| **跨子域 cookie** | 平台 JWT 加 `Domain=.<基础域>`，让 AP 子域网关收到（也修好 Superset 作者网关同类问题） | `JwtProperties.cookieDomain` + `JWT_COOKIE_DOMAIN` |
| **前端运行时配置** | k8s 前端是一镜像 promote 到多环境，入口 URL 必须**运行时**注入 | `public/config.js` + `docker-entrypoint.sh` envsubst |
| **共享账号引导** | 空库时把共享账号 sign-up 成 AP 第一个用户（owner） | `deploy/scripts/ap-bootstrap-shared-account.js` + `ap-bootstrap-job.yaml` |

---

## 3. 改动清单

### 后端（admin-center / platform-security）
- **新增** `com.admin.ap` 包：
  - `controller/ApTokenController.java` —— `/internal/ap/authz`、`/bridge`、`/token`（`bridge.enabled` 关闭时全 404）
  - `client/ActivepiecesApiClient.java` —— 共享账号 sign-in，返回 `ApSession(token, projectId)`
  - `config/ActivepiecesProperties.java` + `config/ApConfig.java` —— `activepieces.*` 配置
  - `exception/ActivepiecesApiException.java`
  - `resources/ap/ap-bridge.html` —— 桥页（classpath 资源）
- **改** `JwtProperties.java` —— 加 `cookieDomain`
- **改** `AuthServiceImpl#setAuthCookie` / `AuthController#expiredCookie` —— 设/清 cookie 一致地加 Domain
- **改** `application.yml` —— `activepieces.*`、`platform.security.jwt.cookie-domain`
- **改** `application-docker.yml` —— `activepieces.internal-url=http://activepieces:80`

### 前端（admin-center）
- **新增** `public/config.js` —— 运行时配置（`window.__APP_CONFIG__.AP_BRIDGE_URL`）
- **改** `index.html` —— 引 `%BASE_URL%config.js`
- **改** `docker-entrypoint.sh` —— envsubst `AP_BRIDGE_URL` 注入 config.js
- **改** `src/env.d.ts` —— `Window.__APP_CONFIG__` 类型
- **改** `src/layouts/AdminLayout.vue` —— 「Activepieces」菜单入口（当前标签跳桥）
- **改** `src/views/sso/SsoCallback.vue` —— `ap-bridge` 回跳映射
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
- **改** `admin-center-frontend.yaml` —— 注入 `AP_BRIDGE_URL`（configmap，可选）
- **改** `config_map/{uat,preprod}/...` —— `JWT_COOKIE_DOMAIN`、`ACTIVEPIECES_BRIDGE_ENABLED=true`、
  `ACTIVEPIECES_INTERNAL_URL`、`ACTIVEPIECES_SHARED_EMAIL`、`AP_BRIDGE_URL`、`ACTIVEPIECES_POSTGRES_*`
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

入口：admin 左侧菜单「Activepieces 自动化」→ 跳 `http://localhost:8085/__ap/bridge`。
首次空库需引导共享账号（见 §6）。

---

## 5. k8s / 生产部署

**生产（runtime only）**：照常部署（`activepieces.yaml` 在默认集里），Istio 只放 `/api/v1/webhooks`，
不带 `-IncludeApBridgeGateway` → 无 UI 网关、无共享账号 Job。configmap/secret 不设 bridge → admin-center 端点 404。

**uat/sit（有 UI）**：
1. **DNS**：`hermes-workflow-activepieces.<BASE_DOMAIN>` 指向 ingressgateway（按需 TLS）。
2. **secret**（每环境一次）：填 `ACTIVEPIECES_SHARED_PASSWORD`、`ACTIVEPIECES_ENCRYPTION_KEY`（32-hex）、
   `ACTIVEPIECES_JWT_SECRET`、AP 库密码。
3. **部署带开关**：
   ```
   ./apply-workflow-station-all.ps1 -Environment <uat|preprod> -BaseDomain <域> ... -IncludeApBridgeGateway
   ```
   纳入 `ap-gateway.yaml` + `ap-bootstrap-job.yaml`。
4. Job 自动把共享账号建成 AP 第一个用户（空库时）。

> **跨子域前提**：admin-center 必须把平台 JWT cookie 设为 `Domain=.<BASE_DOMAIN>`（configmap `JWT_COOKIE_DOMAIN`），
> 否则 AP 子域收不到 cookie、换不到 token。

---

## 6. 共享账号引导（幂等）

新环境（空库）共享账号不存在；AP「第一个 sign-up 用户 = owner（ADMIN，自动建 platform+project）」，
有 owner 后 sign-up 变 invitation-only。脚本 `deploy/scripts/ap-bootstrap-shared-account.js`：
sign-in 探测 → 已存在则跳过；不存在则 sign-up；未配置则跳过。

- **k8s**：随 `-IncludeApBridgeGateway` 部署的 Job 自动跑；或手动 `kubectl exec deploy/activepieces -- node - < deploy/scripts/ap-bootstrap-shared-account.js`（带 env）。
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
- **MVP（方案 B）**：`deploy/scripts/ap-export.js` + `ap-import.js`（复用 bootstrap 的 http 调用），手动跑通一条 flow。
- **完整（方案 A）**：加 git 目录约定 + 生产发布 Job。

**待定（实现前需拍板）**：① git 里 flow 的组织（一 flow 一 JSON vs 按项目分）；② 发布触发（CI 一步 vs k8s 一次性 Job）；③ 生产 connection（手动建一次 vs 脚本化引导）。

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

---

## 9. 已知限制 / 待办

- **flow 发布通道**：方案已定（见 §7，git 为源 + API 导入；Git Sync 经实测 CE 不可用）。**尚未实现**——待拍板 §7 三个待定项后落地 MVP。
- **k8s 未集群验证**：`ap-gateway.yaml`、`ap-bootstrap-job.yaml`、cookie-domain、configmap/secret 均为清单，待真集群验证。
- **会话过期无自动跳登录**：k8s 未认证直接访问 AP host 返回 401（没用 ext_authz）。正常入口（从已登录 admin 点）不受影响。
- **AP 共享平台库 `public`（已定案：维持不变）**：AP 用通用表名（user/project/flag/file/folder/platform/flow/app_connection…），
  与平台 135 张前缀表（`ac_/sys_/dw_/up_/we_`）**实测不冲突**。
  - 独立 **schema** 方案**不可行**（实测）：AP 0.84 的连接两条路径都不暴露 TypeORM `schema` 选项，只能经
    `POSTGRES_URL` 的 `search_path` 注入；但 search_path-only 时 AP 从零跑迁移会断（只建出 `migrations` 表，
    随后 `SELECT * FROM flow` 报 relation 不存在）。n8n 能用 schema 是因为它显式传了 TypeORM `schema`，AP 没有。
  - 唯一能做**硬隔离**的是给 AP **独立 database**（dev 可仿 `n8n_dev`，生产由 DBA 开）。当前不冲突，故维持 public。
- **Istio Job sidecar**：bootstrap Job 关了 sidecar 注入；若 mesh 为 STRICT mTLS 需另行处理（见 yaml 注记）。

---

## 10. 验证状态

- ✅ **dev 全链路已实测**：admin 入口 → 桥 → 换 token → 进 AP（共享账号 ADMIN，/flows 正常、不循环）。
- ✅ 引导脚本三路径已测：幂等成功 / 未配置跳过 / 密码不符明确报错。
- 📦 **k8s/生产**：清单齐全、YAML 校验通过、ps1 开关接通；需集群侧验证（DNS/secret/ext_authz/mTLS）。

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

### 11.6 待办

- **AP Action 模式（用户态）**：后端 `POST /api/v1/ap/execute` 已就绪，但**前端用户态 Action UI 未实现**（随 n8n 一并移除了 n8n 的 Action 对话框/自动填充）。若需要，按 §11.3 后端能力补一套 AP Action 面板即可。
- **端到端实测**：dev 起 AP + 建一条带 Webhook 触发 + Return Response 的 flow，部署一条含 AP service task 的 BPMN，跑通"触发→映射回写"。
