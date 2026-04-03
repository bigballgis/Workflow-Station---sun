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
9. **不部署 API Gateway** — 前端 nginx 直连后端。Kafka 使用 KRaft 模式（无 ZooKeeper），N8N 使用独立 PostgreSQL 数据库。
10. **环境变量名必须是 `ENCRYPTION_SECRET_KEY`** — 不是 `ENCRYPTION_KEY`。
11. **PostgreSQL 不部署** — SIT/UAT/PROD 使用公司现有 PostgreSQL 数据库。Redis、Kafka、N8N 在 K8S 中自行部署。

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

基础设施：PostgreSQL 16（公司现有）+ Redis 7（K8S 部署）+ Kafka 7.5（KRaft 模式，K8S 部署）+ N8N 自动化引擎（K8S 部署）。

不部署的组件：API Gateway（已架空，前端 nginx 直连后端）、PostgreSQL（使用公司现有数据库）。

---

## 2. 部署架构总览

### 2.1 各环境部署方式

| 环境 | 平台 | PostgreSQL | Redis | Kafka | N8N | 后端 (×4) | 前端 (×3) |
|------|------|-----------|-------|-------|-----|----------|----------|
| dev | Docker Desktop | 本地容器 | 本地容器 | 本地容器 | 本地容器 | 本地容器 | 本地容器 |
| sit | 公司 K8S | ⚠️ 公司现有 | K8S Pod | K8S Pod | K8S Pod | K8S Pod | K8S Pod |
| uat | 公司 K8S | ⚠️ 公司现有 | K8S Pod | K8S Pod | K8S Pod | K8S Pod | K8S Pod |
| prod | 公司 K8S | ⚠️ 公司现有 | K8S Pod | K8S Pod | K8S Pod | K8S Pod | K8S Pod |

> ⚠️ PostgreSQL 使用公司现有数据库，需要 DBA 提前创建好数据库和用户。

### 2.2 架构图

```
┌─────────────────────────────────────────────────────────┐
│                    Ingress (K8S) / Nginx                │
│         admin.company.com  portal.company.com           │
│         dev.company.com    n8n.company.com              │
└──────┬──────────────┬──────────────────┬────────────────┘
       │              │                  │
┌──────▼──────┐ ┌─────▼──────┐ ┌────────▼────────┐
│ Admin Center│ │ User Portal│ │ Dev Workstation  │
│  Frontend   │ │  Frontend  │ │   Frontend       │
│  (nginx)    │ │  (nginx)   │ │   (nginx)        │
└──────┬──────┘ └──┬────┬────┘ └────┬─────────────┘
       │           │    │           │
┌──────▼──────┐ ┌──▼────▼────┐ ┌───▼──────────────┐
│ Admin Center│ │ User Portal│ │ Dev Workstation   │
│  Backend    │ │  Backend   │ │   Backend         │
└──────┬──────┘ └──┬────┬────┘ └───┬──────────────┘
       │           │    │           │
       └───────────┼────┼───────────┘
                   │    │
            ┌──────▼────▼──────┐
            │ Workflow Engine  │
            │   (Flowable)     │
            └──────┬───────────┘
                   │
    ┌──────────────┼──────────────┐
    │              │              │
┌───▼─────┐  ┌────▼────┐  ┌─────▼────┐
│PostgreSQL│  │  Redis  │  │  Kafka   │
│(公司现有) │  │(K8S部署) │  │(K8S部署) │
└─────────┘  └─────────┘  └──────────┘

PostgreSQL ── N8N (独立数据库 n8n_{env})
```

### 2.3 需要部署的服务清单 (10 个)

| # | 服务 | 类型 | K8S 清单 | 镜像 |
|---|------|------|---------|------|
| 1 | Redis | 基础设施 | `deployment-redis.yaml` | `redis:7.2-alpine` |
| 2 | Kafka | 基础设施 | `deployment-kafka.yaml` | `confluentinc/cp-kafka:7.5.3` |
| 3 | N8N | 基础设施 | `deployment-n8n.yaml` | `n8nio/n8n` (官方) |
| 4 | workflow-engine | 后端 | `deployment-workflow-engine.yaml` | `workflow-engine-core` |
| 5 | admin-center | 后端 | `deployment-admin-center.yaml` | `admin-center` |
| 6 | user-portal | 后端 | `deployment-user-portal.yaml` | `user-portal` |
| 7 | developer-workstation | 后端 | `deployment-developer-workstation.yaml` | `developer-workstation` |
| 8 | admin-center-frontend | 前端 | `deployment-frontend.yaml` | `admin-center-frontend` |
| 9 | user-portal-frontend | 前端 | `deployment-frontend.yaml` | `user-portal-frontend` |
| 10 | developer-workstation-frontend | 前端 | `deployment-frontend.yaml` | `developer-workstation-frontend` |

### 2.4 不部署的组件

| 组件 | 原因 |
|------|------|
| PostgreSQL | 使用公司现有数据库 |
| API Gateway | 已架空，前端 nginx 直连后端 |

---

## 3. 环境要求

```
Java 17+          (推荐 Eclipse Temurin / Microsoft OpenJDK)
Maven 3.9+        (mvn --version)
Node.js 18+       (node --version)
npm 9+            (npm --version)
Docker Desktop    (docker --version, docker compose version)
kubectl           (SIT/UAT/PROD 部署需要)
```

---

## 4. 项目结构

```
Workflow-Station---sun/
├── pom.xml                          # Maven 根 POM (多模块)
├── BUILD_GUIDE.md                   # 本文档
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
│   │   ├── configmap-{sit,uat,prod}.yaml
│   │   ├── secret-{sit,uat,prod}.yaml
│   │   ├── deployment-redis.yaml        # Redis (自行部署)
│   │   ├── deployment-kafka.yaml        # Kafka (自行部署)
│   │   ├── deployment-n8n.yaml          # N8N (自行部署)
│   │   ├── deployment-workflow-engine.yaml
│   │   ├── deployment-admin-center.yaml
│   │   ├── deployment-user-portal.yaml
│   │   ├── deployment-developer-workstation.yaml
│   │   ├── deployment-frontend.yaml     # 3 个前端合并
│   │   ├── ingress.yaml
│   │   ├── kustomization.yaml
│   │   └── deploy.ps1               # K8S 一键部署脚本
│   ├── init-scripts/                # 数据库初始化 SQL
│   └── README.md
└── TODO.md
```

---

## 5. 后端服务详细参数

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
    CMD wget -q -T 5 -O /dev/null http://localhost:8080/<healthcheck-path> || exit 1
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 6. 前端 nginx 环境变量替换机制（关键）

### 6.1 工作原理

前端容器使用 nginx 反向代理到后端。nginx.conf 中包含 `${ADMIN_CENTER_URL}` 等变量占位符。

流程：
1. `Dockerfile.local` 将 `nginx.conf` 复制为 `/etc/nginx/conf.d/default.conf.template`（模板）
2. 容器启动时执行 `docker-entrypoint.sh`
3. `docker-entrypoint.sh` 先验证必需环境变量是否存在（缺失则 `exit 1`）
4. 用 `envsubst` 将模板中的变量替换为实际值，输出到 `default.conf`
5. 启动 nginx

### 6.2 envsubst 的关键细节

`envsubst` 命令**必须显式指定要替换的变量列表**：

```sh
# ✅ 正确 — 只替换指定变量
envsubst '${ADMIN_CENTER_URL}' < template > default.conf

# ❌ 错误 — 会替换所有 $xxx，包括 nginx 的 $host, $uri, $http_upgrade 等
envsubst < template > default.conf
```

### 6.3 每个前端服务的环境变量

| 前端服务 | 需要的环境变量 | envsubst 变量列表 |
|---------|--------------|-----------------|
| admin-center-frontend | `ADMIN_CENTER_URL` | `'${ADMIN_CENTER_URL}'` |
| user-portal-frontend | `USER_PORTAL_URL`, `ADMIN_CENTER_URL`, `DEVELOPER_WORKSTATION_URL` | `'${USER_PORTAL_URL} ${ADMIN_CENTER_URL} ${DEVELOPER_WORKSTATION_URL}'` |
| developer-workstation-frontend | `DEVELOPER_WORKSTATION_URL`, `ADMIN_CENTER_URL` | `'${DEVELOPER_WORKSTATION_URL} ${ADMIN_CENTER_URL}'` |

---

## 7. 构建步骤（手动逐步执行）

以下命令全部在项目根目录执行。

### 7.1 后端 Maven 构建

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

### 7.2 前端 npm 构建

```powershell
# admin-center-frontend
Push-Location frontend/admin-center; npm install --prefer-offline --no-audit; npx vite build; Pop-Location

# user-portal-frontend
Push-Location frontend/user-portal; npm install --prefer-offline --no-audit; npx vite build; Pop-Location

# developer-workstation-frontend
Push-Location frontend/developer-workstation; npm install --prefer-offline --no-audit; npx vite build; Pop-Location
```

成功标志：每个前端输出 `✓ built in XXs`，`dist/` 目录生成。

### 7.3 Docker 镜像构建

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

验证（应看到 7 个镜像）：
```powershell
docker images "${registry}/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```

---

## 8. Dev 环境部署（本地 Docker Desktop）

### 8.1 一键部署

```powershell
cd deploy/environments/dev

# 完整构建 + 部署（Maven + npm + Docker + 启动容器）
.\build-and-deploy.ps1

# 构建并部署单个服务（Maven + Docker，不影响其他服务）
.\build-and-deploy.ps1 -Service admin-center

# 单个服务跳过 Maven（只重建 Docker 镜像并重启）
.\build-and-deploy.ps1 -Service admin-center -SkipMaven

# 构建并部署单个前端
.\build-and-deploy.ps1 -Service admin-center-frontend

# 跳过 Maven（只重建 Docker 镜像并重启）
.\build-and-deploy.ps1 -SkipMaven

# 跳过前端
.\build-and-deploy.ps1 -SkipFrontend

# 只重启服务（不构建任何东西）
.\build-and-deploy.ps1 -ServicesOnly

# 清除所有容器和数据卷，从零开始
.\build-and-deploy.ps1 -Clean
```

`-Service` 可选值：

| 类型 | `-Service` 值 | 说明 | Maven 模块 / 前端目录 |
|------|--------------|------|----------------------|
| 后端 | `workflow-engine` | 工作流引擎 | `backend/workflow-engine-core` |
| 后端 | `admin-center` | 管理后台 API | `backend/admin-center` |
| 后端 | `user-portal` | 用户门户 API | `backend/user-portal` |
| 后端 | `developer-workstation` | 开发者工作台 API | `backend/developer-workstation` |
| 前端 | `admin-center-frontend` | 管理后台 UI | `frontend/admin-center` |
| 前端 | `user-portal-frontend` | 用户门户 UI | `frontend/user-portal` |
| 前端 | `developer-workstation-frontend` | 开发者工作台 UI | `frontend/developer-workstation` |

### 8.2 服务端口映射

| 服务 | 容器内端口 | 宿主机端口 | URL |
|------|-----------|-----------|-----|
| PostgreSQL | 5432 | 5432 | `localhost:5432` |
| Redis | 6379 | 6379 | `localhost:6379` |
| Kafka (KRaft) | 29092/9092 | 9092 | `localhost:9092` |
| N8N | 5678 | 5678 | `http://localhost:5678` |
| workflow-engine | 8080 | 8081 | `http://localhost:8081` |
| admin-center | 8080 | 8090 | `http://localhost:8090` |
| user-portal | 8080 | 8082 | `http://localhost:8082` |
| developer-workstation | 8080 | 8083 | `http://localhost:8083` |
| admin-center-frontend | 80 | 3000 | `http://localhost:3000` |
| user-portal-frontend | 80 | 3001 | `http://localhost:3001` |
| developer-workstation-frontend | 80 | 3002 | `http://localhost:3002` |

### 8.3 数据库初始化

Dev 环境首次启动时，PostgreSQL 容器会自动执行 `deploy/init-scripts/` 下的 SQL 脚本（通过 Docker volume mount 到 `/docker-entrypoint-initdb.d`）。

如需手动初始化（非 Docker 场景）：
```powershell
cd deploy/init-scripts
.\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform_dev -DbUser platform_dev -DbPassword dev_password_123
```

#### 8.3.1 开发者工作台：部署任务表 `dw_deployment_jobs`

一键部署到 **admin-center** 的异步任务状态持久化在此表（支持多实例 `developer-workstation` 与进程重启后仍可查进度/历史）。与 Flyway **`V309__create_dw_deployment_jobs.sql`** 定义一致。

| 列 | 说明 |
|----|------|
| `id` | 部署任务 UUID（与接口 `deploymentId` 一致） |
| `function_unit_id` | 关联 `dw_function_units.id`，级联删除 |
| `target_admin_url` | 本次调用的管理中心基址 |
| `status` | `PENDING` / `DEPLOYING` / `SUCCESS` / `FAILED` / `ROLLED_BACK` |
| `progress` | 0–100 |
| `message` | 摘要信息 |
| `version_number` / `change_log` | 发布版本与变更说明 |
| `steps_json` | 步骤列表 JSON |
| `started_at` / `completed_at` / `updated_at` | 时间戳（`TIMESTAMPTZ`） |

**已有数据库增量执行**（未走完整 `init-database.ps1` 时）：

```powershell
cd deploy/init-scripts
# 按实际连接修改 -h -p -U -d；密码可用环境变量 PGPASSWORD
psql -h localhost -p 5432 -U platform -d workflow_platform -v ON_ERROR_STOP=1 -f 00-schema/26-add-dw-deployment-jobs.sql
```

新环境：`00-init-all.sh` / `init-database.ps1` 已包含 `26-add-dw-deployment-jobs.sql`，一般无需单独执行。

**相关环境变量**见下文 **§12.2.1 developer-workstation 专项**。

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

## 9. SIT/UAT/PROD 环境部署（公司 K8S）

### 9.1 部署前准备

#### 9.1.1 PostgreSQL 数据库准备（DBA 操作）

PostgreSQL 使用公司现有数据库，需要 DBA 提前完成以下操作：

```sql
-- 1. 创建应用数据库
CREATE DATABASE workflow_platform_{env} OWNER platform_{env};

-- 2. 创建 N8N 专用数据库
CREATE DATABASE n8n_{env} OWNER platform_{env};

-- 3. 创建数据库用户（如果不存在）
CREATE USER platform_{env} WITH PASSWORD 'your_strong_password';
GRANT ALL PRIVILEGES ON DATABASE workflow_platform_{env} TO platform_{env};
GRANT ALL PRIVILEGES ON DATABASE n8n_{env} TO platform_{env};
```

其中 `{env}` 替换为 `sit` / `uat` / `prod`。

#### 9.1.2 数据库 Schema 初始化

首次部署需要初始化数据库 schema。使用 `init-database.ps1` 脚本：

```powershell
cd deploy/init-scripts
.\init-database.ps1 -DbHost {postgres-host} -DbPort 5432 -DbName workflow_platform_{env} -DbUser platform_{env} -DbPassword {password}
```

或者手动执行 SQL：
```powershell
# 使用 standalone schema 文件
cd deploy/init-scripts
psql -h {host} -p 5432 -U platform_{env} -d workflow_platform_{env} -f 00-schema/00-init-all-schemas-standalone.sql
```

若库已初始化、仅需补开发者部署任务表，可单独执行：

```powershell
cd deploy/init-scripts
psql -h {host} -p 5432 -U platform_{env} -d workflow_platform_{env} -v ON_ERROR_STOP=1 -f 00-schema/26-add-dw-deployment-jobs.sql
```

#### 9.1.3 更新 K8S 配置

1. 修改 `deploy/k8s/configmap-{env}.yaml` 中的 PostgreSQL 连接地址：
   ```yaml
   SPRING_DATASOURCE_URL: "jdbc:postgresql://{your-postgres-host}:5432/workflow_platform_{env}"
   ```

2. 修改 `deploy/k8s/configmap-{env}.yaml` 中的 N8N 数据库地址：
   ```yaml
   DB_POSTGRESDB_HOST: "{your-postgres-host}"
   DB_POSTGRESDB_DATABASE: "n8n_{env}"
   ```

3. 修改 `deploy/k8s/secret-{env}.yaml` 中所有 `CHANGE_ME` 值为真实密码。

### 9.2 构建并推送镜像

```powershell
cd deploy/scripts

# 构建并推送所有镜像到 Harbor
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests

# 只构建后端
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests -SkipFrontend

# 只构建前端
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -SkipTests -SkipBackend

# 只推送（不重新构建）
.\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag v1.0.0 -PushOnly
```

### 9.3 K8S 部署

```powershell
cd deploy/k8s

# 部署到 SIT
.\deploy.ps1 -Environment sit -Tag v1.0.0

# 部署到 UAT
.\deploy.ps1 -Environment uat -Tag v1.0.0

# 部署到 PROD
.\deploy.ps1 -Environment prod -Tag v1.0.0

# Dry-run（只验证不实际部署）
.\deploy.ps1 -Environment sit -Tag v1.0.0 -DryRun
```

### 9.4 部署顺序

`deploy.ps1` 会按以下顺序部署：

1. 创建 Namespace
2. 应用 ConfigMap
3. 应用 Secret
4. 部署基础设施：Redis → Kafka → N8N
5. 部署后端：workflow-engine → admin-center → user-portal → developer-workstation
6. 部署前端：admin-center-frontend → user-portal-frontend → developer-workstation-frontend
7. 应用 Ingress

### 9.5 验证部署

```powershell
# 查看所有 Pod 状态
kubectl get pods -n workflow-platform-{env}

# 查看服务
kubectl get svc -n workflow-platform-{env}

# 查看 Ingress
kubectl get ingress -n workflow-platform-{env}

# 查看某个 Pod 日志
kubectl logs -f deployment/workflow-engine -n workflow-platform-{env}

# 检查 Pod 健康状态
kubectl describe pod -l app=workflow-engine -n workflow-platform-{env}
```

### 9.6 K8S Service 内部 URL

所有服务在 K8S 内部通过 Service 名称互相访问：

| Service 名称 | 端口 | 用途 |
|-------------|------|------|
| `redis-service` | 6379 | Redis 缓存 |
| `kafka-service` | 29092 | Kafka 消息队列 |
| `n8n-service` | 5678 | N8N 自动化引擎 |
| `workflow-engine-service` | 8080 | 工作流引擎 |
| `admin-center-service` | 8080 | 管理后台 API |
| `user-portal-service` | 8080 | 用户门户 API |
| `developer-workstation-service` | 8080 | 开发者工作台 API |
| `admin-center-frontend-service` | 80 | 管理后台 UI |
| `user-portal-frontend-service` | 80 | 用户门户 UI |
| `developer-workstation-frontend-service` | 80 | 开发者工作台 UI |

### 9.7 镜像 Registry

```
harbor.company.com/workflow/
├── workflow-engine-core:latest       # 后端 (自建)
├── admin-center:latest               # 后端 (自建)
├── developer-workstation:latest      # 后端 (自建)
├── user-portal:latest                # 后端 (自建)
├── admin-center-frontend:latest      # 前端 (自建)
├── user-portal-frontend:latest       # 前端 (自建)
└── developer-workstation-frontend:latest  # 前端 (自建)

# 基础设施使用官方镜像（不推送到 Harbor）
redis:7.2-alpine                      # Docker Hub
confluentinc/cp-kafka:7.5.3           # Docker Hub
n8nio/n8n                             # Docker Hub
```

---

## 10. 各环境配置差异对比

| 配置项 | SIT | UAT | PROD |
|--------|-----|-----|------|
| SWAGGER_ENABLED | true | false | false |
| FLOWABLE_SCHEMA_UPDATE | true | false | false |
| JWT_EXPIRATION | 24h | 12h | 8h |
| JWT_REFRESH_EXPIRATION | 7d | 3d | 1d |
| LOG_LEVEL_ROOT | INFO | INFO | WARN |
| LOG_LEVEL_SQL | WARN | WARN | ERROR |
| PASSWORD_MIN_LENGTH | 8 | 10 | 12 |
| MAX_FAILED_ATTEMPTS | 5 | 3 | 3 |
| SESSION_TIMEOUT | 30min | 30min | 15min |
| HIKARI_MAX_POOL_SIZE | 15 | 20 | 50 |
| Backend replicas | 2 | 2 | 2+ |

---

## 11. 服务间依赖关系

```
PostgreSQL(公司) ──┬── workflow-engine ──┬── admin-center ──┬── admin-center-frontend
                   │                    │                  └── developer-workstation-frontend
Redis(K8S) ────────┘                    ├── user-portal ───── user-portal-frontend
                                        └── developer-workstation ── developer-workstation-frontend
Kafka(K8S) ─────────────────────────────── workflow-engine + user-portal

PostgreSQL(公司) ── N8N(K8S) (独立数据库 n8n_{env})
```

启动顺序：Redis + Kafka → N8N → workflow-engine → admin-center → (user-portal, developer-workstation) → 前端。

---

## 12. 环境变量完整清单

### 12.1 后端通用环境变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `SERVER_PORT` | 服务端口 | `8080` |
| `SPRING_PROFILES_ACTIVE` | Spring Profile | `docker` / `sit` |
| `SPRING_DATASOURCE_URL` | JDBC URL | `jdbc:postgresql://postgres:5432/workflow_platform_dev` |
| `SPRING_DATASOURCE_USERNAME` | DB 用户名 | `platform_dev` |
| `SPRING_DATASOURCE_PASSWORD` | DB 密码 | `dev_password_123` |
| `SPRING_REDIS_HOST` | Redis 主机 | `redis` / `redis-service` |
| `SPRING_REDIS_PASSWORD` | Redis 密码 | `dev_redis_123` |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka 连接地址 | `kafka:29092` / `kafka-service:29092` |
| `JWT_SECRET` | JWT 签名密钥 | 256-bit 字符串 |
| `JWT_EXPIRATION` | JWT 过期时间(ms) | `86400000` |
| `JWT_REFRESH_EXPIRATION` | 刷新令牌过期时间(ms) | `604800000` |
| `ENCRYPTION_SECRET_KEY` | AES-256 加密密钥 | 32 字节字符串 |
| `CORS_ALLOWED_ORIGINS` | CORS 允许的源 | `http://localhost:3000,...` |
| `SWAGGER_ENABLED` | 是否启用 Swagger | `true` / `false` |

### 12.2 后端服务间 URL 变量

| 变量名 | 使用者 | 说明 |
|--------|--------|------|
| `ADMIN_CENTER_URL` | workflow-engine, user-portal, developer-workstation | 管理后台地址 |
| `WORKFLOW_ENGINE_URL` | admin-center, user-portal, developer-workstation | 工作流引擎地址 |

#### 12.2.1 developer-workstation 专项

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `DEVELOPER_DEPLOY_REQUIRE_ADMIN_AUTH` | 一键部署到 admin 前是否要求当前 HTTP 请求已带 `Authorization: Bearer …`（生产经 Kong 转发用户 JWT 时应为 `true`） | `true`（默认） |
| `ADMIN_CENTER_URL` | 与 `application.yml` 中 `admin-center.url` 一致；部署目标非默认时需覆盖 | `http://admin-center:8080` |

说明：`DEVELOPER_DEPLOY_REQUIRE_ADMIN_AUTH=false` 仅建议用于本地自动化或测试；生产环境应依赖 **Kong + JWT**，由前端/网关携带令牌，服务端再转发至 admin-center 的 `function-units-import` 接口。

### 12.3 前端 nginx 反向代理 URL 变量

| 变量名 | 使用者 | 说明 |
|--------|--------|------|
| `ADMIN_CENTER_URL` | admin-center-frontend, user-portal-frontend, developer-workstation-frontend | 管理后台 API 地址 |
| `USER_PORTAL_URL` | user-portal-frontend | 用户门户 API 地址 |
| `DEVELOPER_WORKSTATION_URL` | developer-workstation-frontend | 开发者工作台 API 地址 |

### 12.4 N8N 相关环境变量

| 变量名 | 说明 | 示例值 |
|--------|------|--------|
| `DB_TYPE` | 数据库类型 | `postgresdb` |
| `DB_POSTGRESDB_HOST` | N8N 数据库主机 | `sit-postgres.internal` |
| `DB_POSTGRESDB_PORT` | N8N 数据库端口 | `5432` |
| `DB_POSTGRESDB_DATABASE` | N8N 数据库名 | `n8n_sit` |
| `DB_POSTGRESDB_USER` | N8N 数据库用户 | Secret |
| `DB_POSTGRESDB_PASSWORD` | N8N 数据库密码 | Secret |
| `N8N_ENCRYPTION_KEY` | N8N 加密密钥 | Secret |
| `WEBHOOK_URL` | N8N Webhook 外部访问地址 | `https://sit-n8n.company.com` |
| `DOUBAO_MODEL_ID` | 豆包模型 ID（发票识别） | Secret |
| `DOUBAO_API_KEY` | 豆包 API Key（发票识别） | Secret |

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

# 6. 推送到 Harbor
docker login harbor.company.com
docker push "${r}/workflow-engine-core:${t}"
docker push "${r}/admin-center:${t}"
docker push "${r}/developer-workstation:${t}"
docker push "${r}/user-portal:${t}"
docker push "${r}/admin-center-frontend:${t}"
docker push "${r}/user-portal-frontend:${t}"
docker push "${r}/developer-workstation-frontend:${t}"

# 7. 部署到 K8S
cd deploy/k8s
.\deploy.ps1 -Environment sit -Tag latest
```

---

## 14. 常见问题与故障排查

### 14.1 构建阶段

| 问题 | 原因 | 解决 |
|------|------|------|
| Docker build 前端报 `COPY failed: dist` | `.dockerignore` 包含了 `dist` | 从 `.dockerignore` 中删除 `dist` |
| Docker build 前端报 `file not found` | 没有先执行 `npm run build` | 先执行 `npx vite build` 生成 `dist/` |
| Maven build 报 `platform-common` 找不到 | 没有加 `-am` 参数 | 加 `-am` 自动构建依赖模块 |
| 后端容器启动后立即退出 | `target/` 下没有 JAR | 先执行 `mvn package` |

### 14.2 运行阶段

| 问题 | 原因 | 解决 |
|------|------|------|
| 前端 nginx 502 Bad Gateway | 后端容器未启动或未就绪 | 等待后端 healthcheck 通过 |
| 前端 nginx 报 `no resolver defined` | `envsubst` 没有替换 `${*_URL}` 变量 | 检查 `docker-entrypoint.sh` 中 envsubst 变量列表 |
| 前端容器启动失败 `xxx_URL is not set` | 没有传入必需的环境变量 | 在 docker-compose 或 K8S env 中添加缺失变量 |
| 前端 nginx `$host`、`$uri` 变空 | `envsubst` 没有指定变量列表 | 显式列出变量：`envsubst '${VAR1} ${VAR2}'` |
| admin-center healthcheck 失败 | context-path 是 `/api/v1/admin` | healthcheck 路径需包含 context-path |
| `.sh` 文件报 `bad interpreter` | Windows CRLF 换行 | 确保 `.sh` 文件是 LF 换行 |
| Kafka 连接失败 | K8S 中未配置 `SPRING_KAFKA_BOOTSTRAP_SERVERS` | 检查 ConfigMap 中是否有该配置 |

### 14.3 数据库相关

| 问题 | 原因 | 解决 |
|------|------|------|
| 后端启动报 `Connection refused` | PostgreSQL 地址配置错误 | 检查 `SPRING_DATASOURCE_URL` 中的主机地址 |
| N8N 启动报数据库连接失败 | N8N 数据库未创建 | 让 DBA 创建 `n8n_{env}` 数据库 |
| Flowable 表不存在 | Schema 未初始化 | 运行 `init-database.ps1` 或设置 `FLOWABLE_SCHEMA_UPDATE=true` |

---

## 15. .gitattributes 配置

项目根目录的 `.gitattributes` 强制 `.sh` 和 `.sql` 文件使用 LF 换行：

```
*.sh text eol=lf
*.sql text eol=lf
```

这确保 Windows 上 checkout 的 shell 脚本在 Linux 容器内可以正常执行。
