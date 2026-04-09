# Low-Code Workflow Platform

Enterprise low-code workflow platform for HSBC, providing visual process design, workflow automation, and business process management capabilities.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│          Kong Gateway / Ingress + per-app Nginx                 │
│     (routing, plugins; JWT validation remains on backends)       │
└─────────────────────────┬───────────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┬────────────────┐
         │                │                │                │
         ▼                ▼                ▼                ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────────┐
   │  Admin   │    │ Workflow │    │   User   │    │  Developer   │
   │  Center  │    │  Engine  │    │  Portal  │    │ Workstation  │
   └────┬─────┘    └────┬─────┘    └────┬─────┘    └──────┬───────┘
        │               │               │                  │
        └───────────────┴───────────────┴──────────────────┘
                          │
              ┌───────────┴───────────┐
              │                       │
         ┌────▼────┐            ┌─────▼─────┐
         │ Kafka   │            │  Redis    │
         │ (Async) │            │  (Cache)  │
         └─────────┘            └───────────┘
                          │
                    ┌─────▼─────┐
                    │PostgreSQL │
                    │   16.5    │
                    └───────────┘
```

浏览器访问 API 的推荐路径为 **前端 nginx → Kong → 后端**（见 [docs/architecture-diagram.md](docs/architecture-diagram.md)）；上图为逻辑组件关系，未画出 Kong 跳线。

## Tech Stack

| Layer | Technology |
|-------|------------|
| Backend | Java 17, Spring Boot 3.2 |
| Spring Cloud BOM | Declared in parent `pom.xml` for dependency alignment only — **not** a deployable Spring Cloud Gateway app |
| API edge | Kong (`deploy/kong/`, `deployment-kong.yaml`); frontends use nginx → Kong or direct backends per environment |
| Frontend | Vue 3, TypeScript, Element Plus |
| Database | PostgreSQL 16.5 |
| Cache | Redis 7.2 |
| Messaging | Apache Kafka |
| Workflow | Flowable 7.0.0 |
| Container | Docker, Kubernetes（`deploy/k8s/` 清单 + `deploy.ps1`；无内置 Helm chart） |

## Modules

### Backend Services
- `workflow-engine-core` - Flowable-based workflow engine
- `admin-center` - User, role, and permission management
- `developer-workstation` - Visual process and form designer
- `user-portal` - Task inbox and process initiation

### Shared Libraries
- `platform-common` - Shared DTOs, exceptions, utilities
- `platform-security` - JWT authentication, encryption
- `platform-cache` - Redis cache service
- `platform-messaging` - Kafka event publishing

### Frontend Applications
- `frontend/admin-center` - Admin management UI
- `frontend/developer-workstation` - Developer tools UI
- `frontend/user-portal` - End user portal UI
- `frontend/login` - Unified login shell（`/login/`，与 K8S `deployment-platform-login-frontend.yaml`、本地 Compose `platform-login-frontend` 对应）

## Quick Start

### Prerequisites
- Java 17+
- Node.js 18+（推荐 20 LTS；与 `BUILD_GUIDE.md` §3 一致）
- Docker & Docker Compose
- Maven 3.9+

### Local Development

**推荐**：使用开发 Compose 一键构建并启动（含 PostgreSQL、Redis、Kafka、N8N、Kong、四后端、三业务前端、**platform-login**、**edge-nginx** 单源入口等），见 `BUILD_GUIDE.md` §8。

```powershell
cd deploy/environments/dev
.\build-and-deploy.ps1
```

**仅基础设施（自行本地跑 JAR / Vite）**：

```powershell
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml --env-file .env up -d postgres redis kafka n8n
```

（Kafka 为 KRaft 模式，**不需要** ZooKeeper。）

后端构建：

```bash
mvn clean install -DskipTests
```

各服务在独立终端启动（端口以各模块 `application.yml` 为准），例如：

```bash
cd backend/workflow-engine-core && mvn spring-boot:run
cd backend/admin-center && mvn spring-boot:run
```

前端（任选一应用）：

```bash
cd frontend/user-portal && npm install && npm run dev
```

### Full Stack with Docker

完整栈请使用 **`deploy/environments/dev/docker-compose.dev.yml`**（勿使用已移除的仓库根目录 `docker-compose --profile full` 旧命令）。见上文 `.\build-and-deploy.ps1` 或：

```powershell
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml --env-file .env up -d --build
```

（需已按 Compose 文件头说明在宿主机完成 `mvn package` / `npm run build`。）

## Configuration

环境变量随部署方式变化；**本地 Docker Dev 以 `deploy/environments/dev/.env` 为单一事实来源**（示例：`POSTGRES_PASSWORD=dev_password_123`、`REDIS_PASSWORD=dev_redis_123`、`JWT_SECRET`、`ENCRYPTION_SECRET_KEY` 等）。K8S 见各环境 `deploy/k8s/configmap-*.yaml` 与 `secret-*.yaml`。

## API Documentation

API documentation (per service, when enabled; Springdoc 3.x 典型路径):
- Workflow Engine（宿主机默认端口见 `.env` 中 `WORKFLOW_ENGINE_PORT`，常为 **8081**）: `http://localhost:8081/swagger-ui/index.html`，OpenAPI：`http://localhost:8081/v3/api-docs`
- Other services: 见各模块 `springdoc` 配置与 context-path（如 admin-center 需带 `/api/v1/admin` 前缀）

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report
```

## Deployment

Kubernetes 资源位于 `deploy/k8s/`（含 Kong、前后端与中间件）。推荐用脚本应用（处理 namespace、镜像与多文件顺序）：

```powershell
cd deploy/k8s
.\deploy.ps1 -Environment sit
```

完整流程见根目录 **BUILD_GUIDE.md** 与 **deploy/README.md**。

`kubectl apply -k deploy/k8s`：**当前 `kustomization.yaml` 固定引用 `configmap-sit.yaml` / `secret-sit.yaml`**，仅适合作为 SIT 命名空间的手动基线；UAT/PROD 请优先使用 **`deploy/k8s/deploy.ps1`** 或自行改 `resources` 后再 apply，并替换镜像仓库与 Secret。

本仓库**未**附带 Helm chart；若生产使用 Helm，需自建 chart 或与上述清单对齐。

## Documentation

- [BUILD_GUIDE.md](BUILD_GUIDE.md) — 构建与多环境部署
- [deploy/README.md](deploy/README.md) — `deploy/` 目录说明
- [docs/README.md](docs/README.md) — **文档索引**（技术栈、架构、Schema/Flyway、Demo 约定、门户/设计器 RBAC）
- [技术栈（中文）](docs/tech-stack.md) · [Tech stack (EN)](docs/tech-stack-en.md)
- [架构示意](docs/architecture-diagram.md) · [Schema 与迁移](docs/schema-and-migration.md) · [Demo 数据约定](docs/demo-data-requirements.md)
- [功能单元开发指南](documentation/function-unit-development-guide.md)（深度）
- 设计规格目录：[.kiro/specs/](.kiro/specs/)

## License

Proprietary - HSBC Internal Use Only
