# Build & Deploy Guide — Workflow Platform

> 本文档面向 AI 助手（GPT / Claude / Kiro）和开发者，包含从零构建到部署的完整步骤。
> 所有命令均为 PowerShell (Windows)。

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

## 4. ⚠️ 关键约束（必读）

### 4.1 Docker 多阶段构建不可用
本地 Docker Desktop 无法执行多阶段构建（npm ci / Maven 在 Docker 内部会失败）。
**所有环境必须使用"本地构建 + 复制"方式**：

- 后端 Dockerfile：只有 JRE 运行层，`COPY target/*.jar`
- 前端 Dockerfile.local：只有 nginx 层，`COPY dist/`
- 前端 Dockerfile（多阶段）存在但 **不使用**

### 4.2 构建产物位置
| 类型 | 构建命令 | 产物位置 | Docker COPY |
|------|----------|----------|-------------|
| 后端 | `mvn package` | `backend/*/target/*.jar` | `COPY target/*.jar app.jar` |
| 前端 | `npx vite build` | `frontend/*/dist/` | `COPY dist /usr/share/nginx/html` |

### 4.3 .dockerignore 注意
前端 `.dockerignore` 中 **不能** 包含 `dist`，否则 `Dockerfile.local` 无法 COPY。
当前已正确配置（dist 不在排除列表中）。

---

## 5. 构建步骤（手动逐步执行）

以下命令全部在项目根目录执行。

### 5.1 后端 Maven 构建

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

### 5.2 前端 npm 构建

```powershell
# admin-center-frontend
cd frontend/admin-center
npm install --prefer-offline --no-audit
npx vite build
cd ../..

# user-portal-frontend
cd frontend/user-portal
npm install --prefer-offline --no-audit
npx vite build
cd ../..

# developer-workstation-frontend
cd frontend/developer-workstation
npm install --prefer-offline --no-audit
npx vite build
cd ../..
```

成功标志：每个前端输出 `✓ built in XXs`，`dist/` 目录生成。

产物验证：
```powershell
Test-Path frontend/admin-center/dist/index.html
Test-Path frontend/user-portal/dist/index.html
Test-Path frontend/developer-workstation/dist/index.html
```

### 5.3 Docker 镜像构建

#### 后端镜像（4 个）

```powershell
$registry = "harbor.company.com/workflow"
$tag = "latest"

docker build -t "${registry}/workflow-engine-core:${tag}" backend/workflow-engine-core
docker build -t "${registry}/admin-center:${tag}" backend/admin-center
docker build -t "${registry}/developer-workstation:${tag}" backend/developer-workstation
docker build -t "${registry}/user-portal:${tag}" backend/user-portal
```

#### 前端镜像（3 个，使用 Dockerfile.local）

```powershell
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

## 6. 一键构建脚本

### 6.1 Dev 环境（本地 Docker Desktop）

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

### 6.2 SIT/UAT/PROD 环境（K8S 镜像）

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

## 7. Dev 环境部署详情

### 7.1 服务端口映射

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

### 7.2 数据库初始化

Dev 环境首次启动时，PostgreSQL 容器会自动执行 `deploy/init-scripts/` 下的 SQL 脚本（通过 Docker volume mount 到 `/docker-entrypoint-initdb.d`）。

如需手动初始化（非 Docker 场景）：
```powershell
cd deploy/init-scripts
.\init-database.ps1 -DbHost localhost -DbPort 5432 -DbName workflow_platform_dev -DbUser platform_dev -DbPassword dev_password_123
```

### 7.3 默认登录账号

```
用户名: admin
密码:   password
```

### 7.4 常用 Docker Compose 命令

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

## 8. SIT 环境部署详情

### 8.1 镜像 Registry

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

### 8.2 推送镜像到 Harbor

```powershell
# 先登录
docker login harbor.company.com

# 构建并推送
.\deploy\scripts\build-and-push-k8s.ps1 -Registry harbor.company.com/workflow -Tag latest -SkipTests
```

### 8.3 K8S 部署

```powershell
# 1. 更新 ConfigMap/Secret（填入真实的 DB/Redis 地址和密码）
#    deploy/k8s/configmap-sit.yaml
#    deploy/k8s/secret-sit.yaml

# 2. 部署
cd deploy/k8s
.\deploy.ps1 -Environment sit -Tag latest

# 3. 验证
kubectl get pods -n workflow-platform-sit
kubectl get svc -n workflow-platform-sit
```

### 8.4 SIT 环境变量参考

见 `deploy/environments/sit/.env`，所有 `CHANGE_ME` 需替换为真实值：
- `POSTGRES_HOST` / `POSTGRES_PASSWORD`
- `REDIS_HOST` / `REDIS_PASSWORD`
- `JWT_SECRET`
- `ENCRYPTION_SECRET_KEY`

---

## 9. 后端 Dockerfile 说明

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
    CMD wget --spider http://localhost:8080/actuator/health || exit 1
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

注意：admin-center 的 healthcheck 路径是 `/api/v1/admin/actuator/health`（有 context-path）。

---

## 10. 前端 Dockerfile.local 说明

所有前端 Dockerfile.local 结构相同：

```dockerfile
FROM nginx:alpine
RUN apk add --no-cache gettext
COPY dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
ENV ADMIN_CENTER_URL=http://admin-center:8080
EXPOSE 80
ENTRYPOINT ["/docker-entrypoint.sh"]
```

`docker-entrypoint.sh` 使用 `envsubst` 将 nginx.conf 模板中的 `${*_URL}` 替换为实际环境变量值（只替换指定变量，不影响 nginx 自身的 `$host`、`$uri` 等）。

---

## 11. 服务间依赖关系

```
PostgreSQL ──┬── workflow-engine ──┬── admin-center ──┬── admin-center-frontend
             │                    │                  └── developer-workstation-frontend
Redis ───────┘                    ├── user-portal ───── user-portal-frontend
                                  └── developer-workstation ── developer-workstation-frontend
```

启动顺序：PostgreSQL + Redis → workflow-engine → admin-center → (user-portal, developer-workstation) → 前端。

---

## 12. 运行测试

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

## 13. 故障排查

| 问题 | 原因 | 解决 |
|------|------|------|
| Docker build 前端报 `/dist: not found` | `.dockerignore` 包含了 `dist` | 从 `.dockerignore` 中删除 `dist` |
| Maven build 报 `platform-common` 找不到 | 没有加 `-am` 参数 | 加 `-am` 自动构建依赖模块 |
| 后端容器启动后立即退出 | `target/` 下没有 JAR | 先执行 `mvn package` |
| 前端 nginx 502 Bad Gateway | 后端容器未启动或未就绪 | 等待后端 healthcheck 通过 |
| PostgreSQL 容器启动慢 | 首次初始化执行 SQL 脚本 | 等待 healthcheck healthy |
| admin-center healthcheck 失败 | context-path 是 `/api/v1/admin` | healthcheck 路径需包含 context-path |
| `npm install` 报权限错误 | node_modules 权限问题 | 删除 `node_modules` 重试 |

---

## 14. 完整构建流程总结（复制粘贴即可）

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

# 4. Docker 构建前端镜像
docker build -f frontend/admin-center/Dockerfile.local -t "${r}/admin-center-frontend:${t}" frontend/admin-center
docker build -f frontend/user-portal/Dockerfile.local -t "${r}/user-portal-frontend:${t}" frontend/user-portal
docker build -f frontend/developer-workstation/Dockerfile.local -t "${r}/developer-workstation-frontend:${t}" frontend/developer-workstation

# 5. 验证
docker images "${r}/*" --format "table {{.Repository}}\t{{.Tag}}\t{{.Size}}"
```
