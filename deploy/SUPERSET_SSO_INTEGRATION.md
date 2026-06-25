# Superset 统一 SSO 整改总览

把 Apache Superset（6.0）的登录接入平台统一 SSO：**作者/管理员**用平台账号一步进 Superset（不再单独登录），**嵌入看板查看者**不受影响。Superset 是 Apache-2.0，这套能力**零授权费**。

> 生产部署运维细节见 [k8s/SUPERSET_SSO_GATEWAY.md](k8s/SUPERSET_SSO_GATEWAY.md)。

---

## 1. 架构

同一个 Superset 实例，开**两个访问入口 + 封掉裸端口**：

```
            ┌─ 作者 UI（JWT 门禁）  dev :8087 / prod hermes-workflow-superset-author.*
            │    nginx auth_request / Istio ext_authz(CUSTOM AuthorizationPolicy)
浏览器 ─────┤      → admin-center /internal/bi/superset/authorize
            │      → 校验平台 JWT，查 bi_rbac_mapping，注入 X-Remote-User / X-Remote-Roles
            │      → Superset REMOTE_USER 登录（自定义 SecurityManager，JIT 建号 + 角色同步）
            │
            └─ 嵌入（不鉴权，guest token）  dev :8089 / prod hermes-workflow-superset-internal-proxy.*
                 = SUPERSET_PUBLIC_HOST，user-portal iframe 用；剥离客户端伪造的 X-Remote-*

裸 Superset 端口 8088：关闭（容器内 expose，不对外发布）。
两个入口都剥离客户端 X-Remote-*；只有作者入口经鉴权后注入校验过的值。
```

**关键点**：Superset 在 `AUTH_REMOTE_USER` 下「只要带 X-Remote-User 头就认你」——所以裸端口**必须**封死，否则任何人 `curl -H "X-Remote-User: admin"` 就能冒充登录。

**角色映射**：平台角色 → `ac_bi_rbac_mappings`(实体表 `bi_rbac_mapping`) → Superset 角色名 → `X-Remote-Roles`。用户在映射表里**没有角色 → authorize 返回 403**（不是作者，拒绝；避免账号泛滥）。

---

## 2. 改动清单（按区域）

### 2.1 Superset 镜像与配置 `deploy/superset/`

| 文件 | 改动 |
|---|---|
| `Dockerfile` | 删除焊死的弱密钥 `ENV SUPERSET_SECRET_KEY=replace_…`；`COPY superset_security_manager.py` |
| `superset_config.py` | `SECRET_KEY` 改为 fail-closed 读 env；CORS `*`→门户白名单(`SUPERSET_CORS_ORIGINS`)；`X-Frame-Options: ALLOWALL`→CSP `frame-ancestors`；`AUTH_TYPE=AUTH_REMOTE_USER` + `CUSTOM_SECURITY_MANAGER`；`RECAPTCHA_PUBLIC_KEY/PRIVATE_KEY`；`LOGOUT_REDIRECT_URL` |
| `superset_security_manager.py` 🆕 | 自定义 `PlatformRemoteUserSecurityManager`：`register_views()` 完整镜像 Superset 逻辑但把 `/login` 换成 REMOTE_USER 子类；`auth_user_remote_user()` JIT 建号 + 每次登录同步**角色 + email + 姓名**（firstname 用 `unquote_plus` 解码，匹配 Java URLEncoder 的 `+`=空格） |

### 2.2 后端 admin-center `backend/admin-center/`

| 文件 | 改动 |
|---|---|
| `bi/controller/BiSupersetAuthController.java` 🆕 | 网关鉴权端点 `GET /internal/bi/superset/authorize`（含 `/authorize/**`，供 Istio ext_authz 追加路径）。校验 JWT → 映射角色 → 返回 `X-Remote-User`/`X-Remote-Roles`/`X-Remote-Email`/`X-Remote-Firstname`（200）/ 401 / 403。email+displayName 从 `sys_users` 查（**平台 JWT 不含 email**）；firstname URL 编码防中文乱码 |
| `bi/service/BiRbacMappingService(+Impl).java` | 新增 `getEffectiveSupersetRoleNames(sysRoleIds)`（返回 Superset 角色**名**；原有只返回 ID） |
| `controller/AuthController.java` | 新增 `GET /auth/logout-redirect`：清 `ac_access_token` cookie + 拉黑 token + 302 到登录页（供 Superset 的 `LOGOUT_REDIRECT_URL`） |
| `bi/config/BiProperties.java` | 修正误导注释（实际 env 是 `BI_SUPERSET_USERNAME`，非 `BI_SUPERSET_ADMIN_USERNAME`） |

### 2.3 前端 admin-center `frontend/admin-center/`

| 文件 | 改动 |
|---|---|
| `src/views/sso/SsoCallback.vue` | 加 `SSO_EXTERNAL_RETURNS` 白名单：当 `state=superset-author` 时，换码种 cookie 后 `window.location` 跳回 Superset（`VITE_SUPERSET_AUTHOR_URL` 或 dev `http://localhost:8087/`），实现作者一步登录 |

### 2.4 dev 本地 `deploy/environments/dev/`

| 文件 | 改动 |
|---|---|
| `nginx-edge.conf` | 新增 `upstream superset_upstream` / `admin_center_api_upstream`；**作者网关 server :8087**（`auth_request` + 注入/剥离 `X-Remote-*` + **`proxy_set_header Origin ""`** + 401→带 SSO 参数的登录跳转 + 403→拒绝）；**嵌入源 server :8089**（不鉴权 + 剥离 `X-Remote-*`）；`/superset/` 跳转改指 :8087 |
| `docker-compose.dev.yml` | superset 封裸 8088（`expose` 不 `ports`）；edge 暴露 8087/8089；admin-center 注入 `SUPERSET_PUBLIC_HOST=:8089`、`APP_SECURITY_LOGOUT_REDIRECT_TARGET` |
| `.env` | 加 `SUPERSET_SECRET_KEY`(dev真值)、`SUPERSET_CORS_ORIGINS`；`SUPERSET_PUBLIC_HOST`→:8089；删除无用的 `BI_SUPERSET_ADMIN_*`（保留实际使用的 `BI_SUPERSET_USERNAME/PASSWORD`） |

### 2.5 k8s 生产 `deploy/k8s/`

| 文件 | 改动 |
|---|---|
| `workflow-station-superset.yaml` | 嵌入 VirtualService 加 `headers.request.remove`(剥离 X-Remote-*)；**新增作者 host**：Gateway + VirtualService + **`AuthorizationPolicy(CUSTOM)` → ext_authz provider `superset-bi-ext-authz`** |
| `config_map/preprod/superset-config.yml` | 与 dev 对齐（密钥/CORS/CSP/guest token/RECAPTCHA/LOGOUT） |
| `config_map/{preprod,uat}/configmap-workflow-platform-config.yml` | 加 `SUPERSET_CORS_ORIGINS`、`SUPERSET_LOGOUT_REDIRECT_URL`、`APP_SECURITY_LOGOUT_REDIRECT_TARGET` |
| `secret/{preprod,uat}/secret-…yml` | `SUPERSET_SECRET_KEY` 占位符→真值；`BI_SUPERSET_ADMIN_*` 改名为 app 实际读取的 `BI_SUPERSET_USERNAME/PASSWORD` |
| `SUPERSET_SSO_GATEWAY.md` 🆕 | 生产部署 runbook：meshConfig ext_authz provider、镜像重建、apply/verify |

### 2.6 环境变量一览

> dev 值在 `deploy/environments/dev/.env` + `docker-compose.dev.yml`；prod 值在
> `deploy/k8s/config_map/<env>/`（非敏感）与 `deploy/k8s/secret/<env>/`（敏感）。
> `__INGRESS_HOST__`/`__BASE_DOMAIN__`/`__NAMESPACE__` 由部署脚本替换。

**A. Superset 容器消费**（`superset_config.py` / k8s `superset-config.yml` 读取）

| 变量 | 必填 | dev 值 | prod 值（preprod 示例） | 用途 |
|---|:---:|---|---|---|
| `SUPERSET_SECRET_KEY` | ✅ | `dev-superset-secret-key-…`（.env） | Secret，真值（勿用占位符） | Flask `SECRET_KEY` + guest token 签名；**fail-closed**，缺失则启动失败 |
| `SUPERSET_CORS_ORIGINS` | ✅ | `http://localhost:3000,http://127.0.0.1:3000` | `https://__INGRESS_HOST__,http://__INGRESS_HOST__,…` | CORS 白名单 + CSP `frame-ancestors`（谁能调 API / iframe 嵌入） |
| `SUPERSET_RECAPTCHA_PUBLIC_KEY` | 否 | `""`（默认） | `""` | 不设会导致嵌入视图 500（见 bug 表）；置空即可 |
| `SUPERSET_RECAPTCHA_PRIVATE_KEY` | 否 | `""` | `""` | 同上 |
| `SUPERSET_LOGOUT_REDIRECT_URL` | 否 | 默认 `http://localhost:3000/api/v1/admin/auth/logout-redirect` | `https://__INGRESS_HOST__/api/v1/admin/auth/logout-redirect` | Superset Logout 跳向的平台登出端点（k8s 默认 `/login/`） |
| `SUPERSET_GUEST_ROLE_NAME` | 否 | 默认 `Gamma` | `Gamma` | 嵌入 guest token 使用的 Superset 角色 |
| `SUPERSET_GUEST_TOKEN_JWT_SECRET` | 否 | 默认=`SECRET_KEY` | 同 | 想独立轮换 guest token 密钥时才设 |
| `SUPERSET_SESSION_COOKIE_SAMESITE` | 否 | （dev 未用） | 默认 `Lax` | 仅 k8s superset-config 读取 |
| `SUPERSET_SESSION_COOKIE_SECURE` | 否 | （dev 未用） | 默认 `false` | 仅 k8s；HTTPS 下应为 `true` |
| `SQLALCHEMY_DATABASE_URI` / `SUPERSET_DATABASE_URI` | ✅ | compose 拼接（含 `?search_path=…schema`） | configmap 提供 | Superset 元数据库连接 |
| `SUPERSET_CONFIG_PATH` | ✅ | 镜像内置 | `/app/pythonpath/superset_config.py` | 加载自定义配置 |
| `MAPBOX_API_KEY` | 否 | — | `""` | 仅 k8s，可选地图 |

**B. admin-center 消费**（BI 模块铸 guest token + 登出）

| 变量 | dev 值 | prod 值 | 用途 |
|---|---|---|---|
| `SUPERSET_HOST` | `http://superset-final:8088` | `http://hase-hermes-workflow-superset.__NAMESPACE__:80` | **内部** Superset 地址（后端→Superset 调 API 铸 guest token） |
| `SUPERSET_PUBLIC_HOST` | `http://localhost:8089` | `http://hermes-workflow-superset-internal-proxy.__BASE_DOMAIN__/` | **浏览器**侧嵌入域名，回给 portal iframe（= 嵌入入口） |
| `SUPERSET_DB_SCHEMA` | `superset` | `superset` | 元数据 schema（`bi.superset.db-schema`） |
| `BI_SUPERSET_USERNAME` | `adama` | Secret，真实 Superset 管理账号 | guest token 铸造用的 Superset 服务账号（**注意：app 读这个，不是 `BI_SUPERSET_ADMIN_USERNAME`**） |
| `BI_SUPERSET_PASSWORD` | `admin123` | Secret | 同上密码 |
| `APP_SECURITY_LOGOUT_REDIRECT_TARGET` | `/login/?client_id=admin&redirect_uri=…8087…callback…&state=superset-author`（URL编码） | 同结构，`redirect_uri` 用 `__INGRESS_HOST__` | `/auth/logout-redirect` 清完 cookie 后跳的登录页（带 SSO 参数，能再登） |

**C. dev 端口 / 镜像 / 前端构建**

| 变量 | 默认 | 用途 |
|---|---|---|
| `SUPERSET_GATE_PORT` | `8087` | dev edge 作者网关宿主端口 |
| `SUPERSET_EMBED_PORT` | `8089` | dev edge 嵌入源宿主端口（8086 被 activepieces 占用） |
| `SUPERSET_BASE_IMAGE` | nexus 镜像引用 | 部署用 Superset 镜像 |
| `VITE_SUPERSET_AUTHOR_URL` | dev 回退 `http://localhost:8087/` | admin **前端构建参数**：作者一步登录跳回的 Superset 地址（prod 设为作者 host） |

> **已删除（无用，勿再用）**：`BI_SUPERSET_ADMIN_USERNAME` / `BI_SUPERSET_ADMIN_PASSWORD` —— application.yml 实际绑定的是 `BI_SUPERSET_USERNAME/PASSWORD`，ADMIN 变体全仓库无引用，已清理。

---

## 3. 排查中发现并修复的真实 bug

| Bug | 现象 | 根因 | 修复 |
|---|---|---|---|
| **嵌入 500 黑屏** | 看板 iframe 显示 Superset「Internal server error 500」 | Superset 6.0 嵌入视图 `common_bootstrap_payload` 无条件读 `RECAPTCHA_PUBLIC_KEY`，原配置没设 → `KeyError` | `superset_config.py` + k8s configmap 加 `RECAPTCHA_PUBLIC_KEY/PRIVATE_KEY=""` |
| **List Users 空白页** | Settings 菜单出现「List Users」，点开空白 | 自定义 SM 的 `register_views` 跳过了 Superset 的逻辑（Superset 6.0 本应移除遗留 FAB user/role/group 视图+菜单） | `register_views` 完整镜像 Superset 逻辑，只把 `/login` 换成 REMOTE_USER 子类。注意 `superset.views.auth` 须**惰性导入**（配置加载极早，顶层导入会 "App not initialized yet"） |
| **登录后「Unexpected error」** | 作者登入 Superset 弹错误 toast（对**所有**用户、与角色无关） | Superset 前端埋点 POST `/superset/log/` 带 `Origin: http://localhost:8087`；auth_request 子请求把 Origin 转发给 admin-center → CORS 白名单无 :8087 → admin-center 返回 403 → 网关拦下 | `_superset_authz` 加 `proxy_set_header Origin ""`（内部调用不需 Origin）。生产 Istio ext_authz 只转发 cookie/authorization，不受影响 |
| **`BI_SUPERSET_ADMIN_*` 死变量** | — | app 实际读 `BI_SUPERSET_USERNAME`（application.yml），`BI_SUPERSET_ADMIN_*` 无人引用；且 k8s 只有 ADMIN 名、缺真正读的名 → 生产 guest token 凭据失效 | dev 删除、k8s 改名为 `BI_SUPERSET_USERNAME/PASSWORD` |
| **登出回不去** | Superset Logout 无效 / 登出后落到裸 `/login` 登不回 | 网关 SSO 下 Superset 自带登出无效（cookie 还在会被登回）；裸 `/login` 缺 SSO 参数无法提交 | 新增 `/auth/logout-redirect`(清 cookie)；`LOGOUT_REDIRECT_URL` 指向它；登出目标设为带 SSO 参数的登录页 |

---

## 4. 登录 / 登出流程（dev）

**作者登录（一步到位）**
```
:8087 (无会话) → 网关 401 → 302 /login/?client_id=admin&redirect_uri=…&state=superset-author
  → developer/password → /admin/sso/callback（换码种 ac_access_token cookie）
  → SsoCallback 识别 state=superset-author → window.location 跳回 :8087
  → 网关 authz 200 + 注入 X-Remote-* → Superset REMOTE_USER 登录 → /superset/welcome/
```

**登出**
```
Superset Settings → Logout → /logout/（清 Superset 会话）
  → 302 LOGOUT_REDIRECT_URL = /api/v1/admin/auth/logout-redirect
  → 清 ac_access_token cookie + 拉黑 → 302 带 SSO 参数的 /login/（可直接再登）
```

**嵌入查看（不变）**：user-portal iframe → guest token（后端用 `BI_SUPERSET_USERNAME` 服务账号铸造）→ 经嵌入源 :8089 加载。

---

## 5. 已完成的验证（dev，截图见 `frontend/admin-center/verification-screenshots/`）

- ✅ 安全加固：CSP frame-ancestors 生效、CORS 放行门户/拒绝恶意源、SECRET_KEY 非弱值
- ✅ 网关：无/无效 JWT、伪造 X-Remote-* 一律拒；裸 8088 不可达；嵌入源剥离伪造头
- ✅ 作者 SSO：平台 JWT → authz 200 → 注入 → REMOTE_USER 登录 → 建号带映射角色
- ✅ 一步登录 + 登出 + 再登入闭环
- ✅ 嵌入看板渲染（`2026-06-25_superset-embed-c2-rendered.png`）
- ✅ List Users 不再空白、`/superset/log/` 不再 403

---

## 6. 生产部署待办（详见 runbook）

1. **重建并推送镜像**（含本仓库改动）：`superset`（新 SM/config）、`admin-center`（authorize 端点 + logout-redirect）、`admin-center-frontend`（SsoCallback）。
2. **集群运维**在 meshConfig 注册 ext_authz provider `superset-bi-ext-authz`（指向 `admin-center-service:8080`，`includeRequestHeadersInCheck: [cookie, authorization]`）。
3. apply manifest + 同步 configmap/secret；作者/嵌入两个 host 配 DNS/TLS。
4. 设 `VITE_SUPERSET_AUTHOR_URL` = 生产作者 host（admin 前端构建参数）。
5. 按 runbook 4 步验证。

---

## 7. 注意事项

- **谁能进作者 UI**：只有在 `bi_rbac_mapping` 有角色映射的用户。当前 dev 映射示例：`role-sys-admin/role-tech-lead→Admin`、`role-developer→Gamma`。无映射 → 403。
- **Gamma 看不到看板**：Superset 的 Gamma/Public 角色默认无任何 dashboard 权限，需在 Superset 给角色授权对应 dashboard，否则登入后是空的（「No results」）。这是 Superset 权限模型，非 bug。
- **Superset 用户不在 Superset 里管**：首次 SSO 登录自动 JIT 建号，角色由平台 `bi_rbac_mapping` 决定；故 Superset 的 `/users/list/` 已（按原版 Superset）移除，访问返回 Access Denied。
- **后端构建用 JDK17**：本机 Maven 默认 JDK25 会让 Lombok 静默失效；`export JAVA_HOME=/opt/homebrew/opt/openjdk@17` 再构建。
