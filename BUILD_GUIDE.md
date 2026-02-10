# Build & Deploy Guide — Workflow Platform

> 本文档面向 AI 助手（GPT / Claude / Kiro）和开发者，包含从零构建到部署的完整步骤。
> 所有命令均为 PowerShell (Windows)。

---

## 0. ⛔ AI 助手必读 — 关键规则

> **如果你是 GPT / Claude / Kiro 等 AI 助手，请先读完本节再执行任何操作。**

1. **Docker 多阶段构建不可用** — 本地 Docker Desktop 无法在容器内执行 `npm ci` 或 `mvn package`。所有环境必须使用"本地构建 + 复制"方式。
2. **前端必须使用 `Dockerfile.local`** — 前端目录下有两个 Dockerfile：`Dockerfile`（多阶段，不使用）和 `Dockerfile.local`（仅复制 dist/）。**永远使用 `Dockerfile.local`**。
3. **`npm run build` = `vite build`** — 前端 `package.json` 中 `build` 脚本已改为 `vite build`（无 `vue-tsc` 类型检查）。
4. **前端 `.dockerignore` 不能包含 `dist`** — 否则 `Dockerfile.local` 的 `COPY dist` 会失败。
5. **nginx 环境变量替换机制** — 前端 nginx.conf 是模板文件，`docker-entrypoint.sh` 在容器启动时用 `envsubst` 替换 `${*_URL}` 变量。**必须在 `envsubst` 命令中显式列出变量名**，否则 nginx 自身的 `$host`、`$uri` 等也会被替换导致 502。
6. **统一的 `*_URL` 变量命名** — 后端和前端 nginx 使用相同的 URL 变量名（如 `ADMIN_CENTER_URL`）。没有 `*_BACKEND_URL` 变量。
7. **`.sh` 和 `.sql` 文件必须是 LF 换行** — `.gitattributes` 已配置强制 LF。如果手动创建 `.sh` 文件，确保是 LF 而非 CRLF，否则容器内执行会报 `/bin/sh: bad interpreter`。
8. **admin-center 有 context-path** — healthcheck 路径是 `/api/v1/admin/actuator/health`，不是 `/actuator/health`。
9. **不部署 API Gateway 和 Kafka** — 前端 nginx 直连后端，Kafka 通过 Redis 模拟。
10. **环境变量名必须是 `ENCRYPTION_SECRET_KEY`** — 不是 `ENCRYPTION_KEY`。

---

## 1. 项目概览

| 项目 | 技术栈 | 说明 |
|------|--------|------|
| workflow-engine-core | Spring Boot 3.2 + Flowable 7 | BPMN 工作流引擎 |
| admin-center | Spring Boot 3.2 | 管理后台 API |
| developer-workstation | Spring Boot 3.2 | 开发者工作台 API |
| user-portal | Spring Boot 3.2 | 用户门户 API |
| admin-center-frontend | Vue 3 + Vite + Element Plus | 管理后台 UI |
| user-portal-frontend | Vue 3 + Vite + Element Plus | 用户门户 UI |
| developer-workstation-frontend | Vue 3 + Vite + Element Plus + BPMN.js | 开发者工作台 UI |

基础设施：PostgreSQL 16 + Redis 7（dev 环境本地容器，SIT/UAT/PROD 公司 K8S 托管）。

不部署的组件：API Gateway（已架空，前端 nginx 直连后端）、Kafka/Zookeeper（未使用）。

---

## 2. 环境要求

```
Java 17+          (推荐 Eclipse Temurin / Microsoft OpenJDK)
Maven 3.9+        (mvn --version)
Node.js 18+       (node --version)
npm 9+            (npm --version)
Docker Desktop    (docker --version, docker compose version)
```

---

## 3. 项目结构

```
Workflow-Station---sun/
├── pom.xml                          # Maven 根 POM (多模块)
├── backend/
│   ├── platform-common/             # 公共库 (jar, 不部署)
│   ├── platform-cache/              # 缓存库 (jar, 不部署)
│   ├── platform-security/           # 安全库 (jar, 不部署)
│   ├── platform-messaging/          # 消息库 (jar, 不部署)
│   ├── workflow-engine-core/        # 工作流引擎 (Spring Boot, 可部署)
│   ├── admin-center/                # 管理后台 (Spring Boot, 可部署)
│   ├── developer-workstation/       # 开发者工作台 (Spring Boot, 可部署)
│   └── user-portal/                 # 用户门户 (Spring Boot, 可部署)
├── frontend/
│   ├── admin-center/                # 管理后台前端 (Vue 3)
│   ├── user-portal/                 # 用户门户前端 (Vue 3)
│   └── developer-workstation/       # 开发者工作台前端 (Vue 3)
├── deploy/
│   ├── environments/
│   │   ├── dev/                     # 本地 Docker 开发环境
│   │   │   ├── .env
│   │   │   ├── docker-compose.dev.yml
│   │   │   └── build-and-deploy.ps1
│   │   ├── sit/.env                 # SIT 参考配置
│   │   ├── uat/.env                 # UAT 参考配置
│   │   └── prod/.env                # PROD 参考配置
│   ├── scripts/
│   │   └── build-and-push-k8s.ps1   # K8S 镜像构建推送脚本
│   ├── k8s/                         # K8S 部署清单
│   └── init-scripts/                # 数据库初始化 SQL
└── TODO.md                          # 待整改项清单
```

---

## 4. 后端服务详细参数

| 服务 | Maven 模块路径 | JAR 文件名模式 | context-path | Healthcheck 路径 | JVM 内存 |
|------|---------------|---------------|-------------|-----------------|---------|
| workflow-engine | `backend/workflow-engine-core` | `workflow-engine-core-*.jar` | `/` | `/actuator/health` | 512m-1024m |
| admin-center | `backend/admin-center` | `admin-center-*.jar` | `/api/v1/admin` | `/api/v1/admin/actuator/health` | 256m-512m |
| developer-workstation | `backend/developer-workstation` | `developer-workstation-*.jar` | `/api/v1` | `/api/v1/actuator/health` | 256m-512m |
| user-portal | `backend/user-portal` | `user-portal-*.jar` | `/api/portal` | `/api/portal/actuator/health` | 256m-512m |

所有后端 Dockerfile 结构相同：
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S platform && adduser -S platform -G platform
COPY target/<service>-*.jar app.jar
RUN chown -R platform:platform /app
USER platform
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/<healthcheck-path> || exit 1
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 5. 前端 nginx 环境变量替换机制（关键）

### 5.1 工作原理

前端容器使用 nginx 反向代理到后端。nginx.conf 中包含 `${ADMIN_CENTER_URL}` 等变量占位符。

流程：
1. `Dockerfile.local` 将 `nginx.conf` 复制为 `/etc/nginx/conf.d/default.conf.template`（模板）
2. 容器启动时执行 `docker-entrypoint.sh`
3. `docker-entrypoint.sh` 先验证必需环境变量是否存在（缺失则 `exit 1`）
4. 用 `envsubst` 将模板中的变量替换为实际值，输出到 `default.conf`
5. 启动 nginx

### 5.2 envsubst 的关键细节

`envsubst` 命令**必须显式指定要替换的变量列表**：

```sh
# ✅ 正确 — 只替换指定变量
envsubst '${ADMIN_CENTER_URL}' < template > default.conf

# ❌ 错误 — 会替换所有 $xxx，包括 nginx 的 $host, $uri, $http_upgrade 等
envsubst < template > default.conf
```

### 5.3 每个前端服务的环境变量

| 前端服务 | 需要的环境变量 | envsubst 变量列表 |
|---------|--------------|-----------------|
| admin-center-frontend | `ADMIN_CENTER_URL` | `'${ADMIN_CENTER_URL}'` |
| user-portal-frontend | `USER_PORTAL_URL`, `ADMIN_CENTER_URL` | `'${USER_PORTAL_URL} ${ADMIN_CENTER_URL}'` |
| developer-workstation-frontend | `DEVELOPER_WORKSTATION_URL`, `ADMIN_CENTER_URL` | `'${DEVELOPER_WORKSTATION_URL} ${ADMIN_CENTER_URL}'` |

### 5.4 docker-entrypoint.sh 模板

```sh
#!/bin/sh
set -e

# 1. 验证必需变量
if [ -z "$ADMIN_CENTER_URL" ]; then
  echo "ERROR: ADMIN_CENTER_URL is not set" >&2
  exit 1
fi

# 2. envsubst 替换（只替换指定变量！）
envsubst '${ADMIN_CENTER_URL}' < /etc/nginx/conf.d/default.conf.template > /etc/nginx/conf.d/default.conf

# 3. 打印日志
echo "nginx config: ADMIN_CENTER_URL=$ADMIN_CENTER_URL"

# 4. 启动 nginx
exec nginx -g 'daemon off;'
```

### 5.5 如果新增后端 URL 变量

假设要给 user-portal-frontend 新增一个 `WORKFLOW_ENGINE_URL`：
1. 在 `nginx.conf` 中使用 `${WORKFLOW_ENGINE_URL}`
2. 在 `docker-entrypoint.sh` 中添加验证 + 更新 envsubst 列表：
   ```sh
   envsubst '${USER_PORTAL_URL} ${ADMIN_CENTER_URL} ${WORKFLOW_ENGINE_URL}' < template > conf
   ```
3. 在 `Dockerfile.local` 中添加 `ENV WORKFLOW_ENGINE_URL=http://workflow-engine:8080`
4. 在 `docker-compose.dev.yml` 和 K8S `deployment-frontend.yaml` 中传入该变量

---

## 6. 构建步骤（手动逐步执行）

以下命令全部在项目根目录执行。

### 6.1 后端 Maven 构建

```powershell
# 编译所有后端模块（跳过测试），生成 JAR 到各 target/ 目录
mvn clean package -DskipTests -pl backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging,backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal -am
```

成功标志：`BUILD SUCCESS`，8 个模块全部 SUCCESS。

产物验证：
```powershell
# 确认 4 个可部署 JAR 存在
Get-ChildItem backend/workflow-engine-core/target/*.jar -Exclude *original*
Get-ChildItem backend/admin-center/target/*.jar -Exclude *original*
Get-ChildItem backend/developer-workstation/target/*.jar -Exclude *original*
Get-ChildItem backend/user-portal/target/*.jar -Exclude *original*
```

### 6.2 前端 npm 构建

```powershell
# admin-center-frontend
Push-Location frontend/admin-center; npm install --prefer-offline --no-audit; npx vite build; Pop-Location

# user-portal-frontend
Push-Location frontend/user-portal; npm install --prefer-offline --no-audit; npx vite build; Pop-Location

# developer-workstation-frontend
Push-Location frontend/developer-workstation; npm install --prefer-offline --no-audit; npx vite build; Pop-Location
```

成功标志：每个前端输出 `✓ built in XXs`，`dist/` 目录生成。

产物验证：
```powershell
Test-Path frontend/admin-center/dist/index.html
Test-Path frontend/user-portal/dist/index.html
Test-Path frontend/developer-workstation/dist/index.html
```

### 6.3 Docker 镜像构建

#### 后端镜像（4 个）

```powershell
$registry = "harbor.company.com/workflow"
$tag = "latest"

docker build -t "${registry}/workflow-engine-core:${tag}" backend/workflow-engine-core
docker build -t "${registry}/admin-center:${tag}" backend/admin-center
docker build -t "${registry}/developer-workstation:${tag}" backend/developer-workstation
docker build -t "${registry}/user-portal:${tag}" backend/user-portal
```

#### 前端镜像（3 个，必须使用 Dockerfile.local）

```powershell
# ⚠️ 注意 -f 参数指定 Dockerfile.local，不是默认的 Dockerfile
docker build -f frontend/admin-center/Dockerfile.local -t "${registry}/admin-center-frontend:${tag}" frontend/admin-center
docker build -f frontend/user-portal/Dockerfile.local -t "${registry}/user-portal-frontend:${tag}" frontend/user-portal
docker build -f frontend/developer-workstation/Dockerfile.local -t "${registry}/developer-workstation-frontend:${tag}" frontend/developer-workstation
```

验证：
```powershell
docker images "${registry}/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

应看到 7 个镜像。

---

## 7. 一键构建脚本

### 7.1 Dev 环境（本地 Docker Desktop）

```powershell
cd deploy/environments/dev

# 完整构建 + 部署（Maven + npm + Docker + 启动容器）
.\build-and-deploy.ps1

# 跳过 Maven（只重建 Docker 镜像并重启）
.\build-and-deploy.ps1 -SkipMaven

# 跳过前端
.\build-and-deploy.ps1 -SkipFrontend

# 只重启服务（不构建任何东西）
.\build-and-deploy.ps1 -ServicesOnly

# 清除所有容器和数据卷，从零开始
.\build-and-deploy.ps1 -Clean
```

### 7.2 SIT/UAT/PROD 环境（K8S 镜像）

```powershell
cd deploy/scripts

# 构建所有镜像（不推送）
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag latest -SkipTests -NoPush

# 构建并推送
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests

# 只构建后端
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag latest -SkipTests -SkipFrontend -NoPush

# 只构建前端
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag latest -SkipTests -SkipBackend -NoPush

# 只推送（不重新构建）
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag latest -PushOnly
```

---

## 8. Dev 环境部署详情

### 8.1 服务端口映射

| 服务 | 容器内端口 | 宿主机端口 | URL |
|------|-----------|-----------|-----|
| PostgreSQL | 5432 | 5432 | `localhost:5432` |
| Redis | 6379 | 6379 | `localhost:6379` |
| workflow-engine | 8080 | 8081 | `http://localhost:8081` |
| admin-center | 8080 | 8090 | `http://localhost:8090` |
| user-portal | 8080 | 8082 | `http://localhost:8082` |
| developer-workstation | 8080 | 8083 | `http://localhost:8083` |
| admin-center-frontend | 80 | 3000 | `http://localhost:3000` |
| user-portal-frontend | 80 | 3001 | `http://localhost:3001` |
| developer-workstation-frontend | 80 | 3002 | `http://localhost:3002` |

### 8.2 docker-compose 环境变量传递

前端容器在 `docker-compose.dev.yml` 中需要传入正确的环境变量：

```yaml
# admin-center-frontend — 只需 1 个变量
admin-center-frontend:
  environment:
    ADMIN_CENTER_URL: http://admin-center:8080

# user-portal-frontend — 需要 2 个变量
user-portal-frontend:
  environment:
    USER_PORTAL_URL: http://user-portal:8080
    ADMIN_CENTER_URL: http://admin-center:8080

# developer-workstation-frontend — 需要 2 个变量
developer-workstation-frontend:
  environment:
    DEVELOPER_WORKSTATION_URL: http://developer-workstation:8080
    ADMIN_CENTER_URL: http://admin-center:8080
```

⚠️ 这些 URL 使用 Docker 网络内部的服务名（如 `admin-center`），不是 `localhost`。

### 8.3 数据库初始化

Dev 环境首次启动时，PostgreSQL 容器会自动执行 `deploy/init-scripts/` 下的 SQL 脚本（通过 Docker volume mount 到 `/docker-entrypoint-initdb.d`）。

如需手动初始化（非 Docker 场景）：
```powershell
cd deploy/init-scripts
.\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform_dev -DbUser platform_dev -DbPassword dev_password_123
```

### 8.4 默认登录账号

```
用户名: admin
密码:   password
```

### 8.5 常用 Docker Compose 命令

```powershell
$compose = "deploy/environments/dev/docker-compose.dev.yml"
$env = "deploy/environments/dev/.env"

# 查看所有容器状态
docker compose -f $compose --env-file $env ps

# 查看某个服务日志
docker compose -f $compose --env-file $env logs -f admin-center

# 重启某个服务
docker compose -f $compose --env-file $env restart admin-center

# 停止所有服务
docker compose -f $compose --env-file $env down

# 停止并删除数据卷（清空数据库）
docker compose -f $compose --env-file $env down -v
```

---

## 9. SIT/UAT/PROD 环境部署详情

### 9.1 架构差异

| | Dev | SIT/UAT/PROD |
|---|---|---|
| 平台 | Docker Desktop (本地) | 公司 K8S |
| PostgreSQL | 本地容器 | 公司托管 |
| Redis | 本地容器 | 公司托管 |
| docker-compose | 有 | 无（用 K8S 清单） |
| 镜像 Registry | 本地 | Harbor (`harbor.company.com/workflow`) |

### 9.2 镜像 Registry

```
harbor.company.com/workflow/
├── workflow-engine-core:latest
├── admin-center:latest
├── developer-workstation:latest
├── user-portal:latest
├── admin-center-frontend:latest
├── user-portal-frontend:latest
└── developer-workstation-frontend:latest
```

### 9.3 K8S ConfigMap 中的 URL 变量

K8S 中后端和前端使用相同的 URL 变量名，值为 K8S Service 名称：

```yaml
# deploy/k8s/configmap-sit.yaml
data:
  ADMIN_CENTER_URL: "http://admin-center-service:8080"
  WORKFLOW_ENGINE_URL: "http://workflow-engine-service:8080"
  DEVELOPER_WORKSTATION_URL: "http://developer-workstation-service:8080"
  USER_PORTAL_URL: "http://user-portal-service:8080"
```

### 9.4 K8S 前端 Deployment 环境变量

前端 Pod 从 ConfigMap 读取 URL 变量，传给容器的 `docker-entrypoint.sh`：

```yaml
# admin-center-frontend Pod
env:
- name: ADMIN_CENTER_URL
  valueFrom:
    configMapKeyRef:
      name: workflow-platform-config
      key: ADMIN_CENTER_URL

# user-portal-frontend Pod
env:
- name: USER_PORTAL_URL
  valueFrom: { configMapKeyRef: { name: workflow-platform-config, key: USER_PORTAL_URL } }
- name: ADMIN_CENTER_URL
  valueFrom: { configMapKeyRef: { name: workflow-platform-config, key: ADMIN_CENTER_URL } }

# developer-workstation-frontend Pod
env:
- name: DEVELOPER_WORKSTATION_URL
  valueFrom: { configMapKeyRef: { name: workflow-platform-config, key: DEVELOPER_WORKSTATION_URL } }
- name: ADMIN_CENTER_URL
  valueFrom: { configMapKeyRef: { name: workflow-platform-config, key: ADMIN_CENTER_URL } }
```

### 9.5 SIT 环境变量参考

见 `deploy/environments/sit/.env`，所有 `CHANGE_ME` 需替换为真实值：
- `POSTGRES_HOST` / `POSTGRES_PASSWORD`
- `REDIS_HOST` / `REDIS_PASSWORD`
- `JWT_SECRET`
- `ENCRYPTION_SECRET_KEY`

### 9.6 推送镜像到 Harbor

```powershell
# 先登录
docker login harbor.company.com

# 构建并推送
.\deploy\scripts\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag latest -SkipTests
```

---

## 10. 服务间依赖关系

```
PostgreSQL ──┬── workflow-engine ──┬── admin-center ──┬── admin-center-frontend
             │                    │                  └── developer-workstation-frontend
Redis ───────┘                    ├── user-portal ───── user-portal-frontend
                                  └── developer-workstation ── developer-workstation-frontend
```

启动顺序：PostgreSQL + Redis → workflow-engine → admin-center → (user-portal, developer-workstation) → 前端。

---

## 11. 运行测试

```powershell
# 后端单元测试（全部）
mvn test

# 后端单元测试（单个模块）
mvn test -pl backend/admin-center

# 前端测试
cd frontend/developer-workstation
npx vitest --run
```

---

## 12. 常见问题与故障排查

### 12.1 构建阶段

| 问题 | 原因 | 解决 |
|------|------|------|
| Docker build 前端报 `COPY failed: file not found in build context: dist` | `.dockerignore` 包含了 `dist` | 从 `.dockerignore` 中删除 `dist` |
| Docker build 前端报 `COPY failed: file not found` | 没有先执行 `npm run build` | 先执行 `npx vite build` 生成 `dist/` |
| Maven build 报 `platform-common` 找不到 | 没有加 `-am` 参数 | 加 `-am` 自动构建依赖模块 |
| 后端容器启动后立即退出 | `target/` 下没有 JAR | 先执行 `mvn package` |
| `npm install` 报权限错误 | node_modules 权限问题 | 删除 `node_modules` 重试 |

### 12.2 运行阶段

| 问题 | 原因 | 解决 |
|------|------|------|
| 前端 nginx 502 Bad Gateway | 后端容器未启动或未就绪 | 等待后端 healthcheck 通过 |
| 前端 nginx 报 `no resolver defined to resolve xxx` | `envsubst` 没有替换 `${*_URL}` 变量 | 检查 `docker-entrypoint.sh` 中 envsubst 是否列出了所有变量 |
| 前端容器启动失败 `ERROR: xxx_URL is not set` | docker-compose 或 K8S 没有传入必需的环境变量 | 在 docker-compose `environment` 或 K8S `env` 中添加缺失变量 |
| 前端 nginx 所有 `$host`、`$uri` 变成空 | `envsubst` 没有指定变量列表，替换了所有 `$xxx` | 在 envsubst 命令中显式列出变量：`envsubst '${VAR1} ${VAR2}'` |
| PostgreSQL 容器启动慢 | 首次初始化执行 SQL 脚本 | 等待 healthcheck healthy |
| admin-center healthcheck 失败 | context-path 是 `/api/v1/admin` | healthcheck 路径需包含 context-path |
| `.sh` 文件在容器内报 `bad interpreter` | Windows CRLF 换行 | 确保 `.sh` 文件是 LF 换行（`.gitattributes` 已配置） |
| `docker-entrypoint.sh: Permission denied` | 文件没有执行权限 | Dockerfile 中需要 `RUN chmod +x /docker-entrypoint.sh` |

### 12.3 环境变量完整清单

#### 后端通用环境变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `SERVER_PORT` | 服务端口 | `8080` |
| `SPRING_PROFILES_ACTIVE` | Spring Profile | `docker` / `sit` |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://postgres:5432/workflow_platform_dev` |
| `SPRING_DATASOURCE_USERNAME` | DB 用户名 | `platform_dev` |
| `SPRING_DATASOURCE_PASSWORD` | DB 密码 | `dev_password_123` |
| `SPRING_REDIS_HOST` | Redis 主机 | `redis` |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | `dev_redis_123` |
| `JWT_SECRET` | JWT 签名密钥 | 256-bit 字符串 |
| `JWT_EXPIRATION` | JWT 过期时间(ms) | `86400000` |
| `JWT_REFRESH_EXPIRATION` | 刷新令牌过期时间(ms) | `604800000` |
| `ENCRYPTION_SECRET_KEY` | AES-256 加密密钥 | 32 字节字符串 |
| `CORS_ALLOWED_ORIGINS` | CORS 允许的源 | `http://localhost:3000,...` |
| `SWAGGER_ENABLED` | 是否启用 Swagger | `true` / `false` |

#### 后端服务间 URL 变量

| 变量名 | 使用者 | 说明 |
|--------|--------|------|
| `ADMIN_CENTER_URL` | workflow-engine, user-portal, developer-workstation | 管理后台地址 |
| `WORKFLOW_ENGINE_URL` | admin-center, user-portal | 工作流引擎地址 |

---

## 13. 完整构建流程总结（复制粘贴即可）

```powershell
# ============================================
# 从零构建全部 Docker 镜像（约 3-5 分钟）
# ============================================

# 0. 确认工具版本
java -version; mvn --version; node --version; npm --version; docker --version

# 1. Maven 构建后端 JAR
mvn clean package -DskipTests -pl backend/platform-common,backend/platform-cache,backend/platform-security,backend/platform-messaging,backend/workflow-engine-core,backend/admin-center,backend/developer-workstation,backend/user-portal -am

# 2. npm 构建前端 dist
Push-Location frontend/admin-center; npm install --prefer-offline --no-audit; npx vite build; Pop-Location
Push-Location frontend/user-portal; npm install --prefer-offline --no-audit; npx vite build; Pop-Location
Push-Location frontend/developer-workstation; npm install --prefer-offline --no-audit; npx vite build; Pop-Location

# 3. Docker 构建后端镜像
$r = "harbor.company.com/workflow"; $t = "latest"
docker build -t "${r}/workflow-engine-core:${t}" backend/workflow-engine-core
docker build -t "${r}/admin-center:${t}" backend/admin-center
docker build -t "${r}/developer-workstation:${t}" backend/developer-workstation
docker build -t "${r}/user-portal:${t}" backend/user-portal

# 4. Docker 构建前端镜像（必须用 Dockerfile.local！）
docker build -f frontend/admin-center/Dockerfile.local -t "${r}/admin-center-frontend:${t}" frontend/admin-center
docker build -f frontend/user-portal/Dockerfile.local -t "${r}/user-portal-frontend:${t}" frontend/user-portal
docker build -f frontend/developer-workstation/Dockerfile.local -t "${r}/developer-workstation-frontend:${t}" frontend/developer-workstation

# 5. 验证（应看到 7 个镜像）
docker images "${r}/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"

# 6. 推送到 Harbor（可选）
docker login harbor.company.com
docker push "${r}/workflow-engine-core:${t}"
docker push "${r}/admin-center:${t}"
docker push "${r}/developer-workstation:${t}"
docker push "${r}/user-portal:${t}"
docker push "${r}/admin-center-frontend:${t}"
docker push "${r}/user-portal-frontend:${t}"
docker push "${r}/developer-workstation-frontend:${t}"
```

---

## 14. .gitattributes 配置

项目根目录的 `.gitattributes` 强制 `.sh` 和 `.sql` 文件使用 LF 换行：

```
*.sh text eol=lf
*.sql text eol=lf
```

这确保 Windows 上 checkout 的 shell 脚本在 Linux 容器内可以正常执行。如果手动创建 `.sh` 文件，请确认编辑器保存为 LF 格式。
