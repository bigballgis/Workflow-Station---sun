# 技术栈（中文）

与根目录 `README.md` 一致，此处便于单独引用。

## 后端

| 项 | 版本 / 说明 |
|----|-------------|
| 运行时 | Java 17 |
| 框架 | Spring Boot 3.2 |
| 工作流 | Flowable 7 |
| API 文档 | springdoc / Swagger（按环境开关） |
| 持久化 | Spring Data JPA，PostgreSQL 16.x |
| 缓存 | Spring Data Redis，Redis 7.2 |
| 消息 | Spring Kafka，Kafka 7.5（KRaft，无 ZooKeeper） |
| 安全 | JWT（jjwt）、BCrypt；边缘路由与插件由 **Kong** 承担 |
| 迁移 | **Flyway**（上述三模块各自 `db/migration`；`application.yml` 默认启用，但 **Dev Compose 对三服务设 `SPRING_FLYWAY_ENABLED=false`**，见 [schema-and-migration.md](../schema-and-migration.md)） |

父 POM 声明 **Spring Cloud BOM** 仅用于依赖对齐；**不包含**可部署的 Spring Cloud Gateway 应用（历史 `api-gateway` 模块已移除）。

## 前端

| 项 | 说明 |
|----|------|
| 框架 | Vue 3.4 + TypeScript 5 |
| 构建 | Vite 5 |
| UI | Element Plus |
| 状态 | Pinia |
| 国际化 | vue-i18n 11（演示约定见 [demo-data-requirements.md](../demo-data-requirements.md)） |

独立应用：

- `frontend/admin-center` — 管理后台
- `frontend/user-portal` — 用户门户
- `frontend/developer-workstation` — 开发者设计器
- `frontend/login` — 统一登录壳（K8S 路径 `/login/`，与多应用同源入口配合）

## 测试

- 后端：JUnit 5、jqwik（属性测试）
- 前端：Vitest、fast-check（按各 `package.json`）

## 部署

- 本地开发：`deploy/environments/dev/docker-compose.dev.yml` + `build-and-deploy.ps1`
- Kubernetes：`deploy/k8s/` + `deploy.ps1`（**默认不含** `developer-workstation`，见 `kustomization.yaml` 注释）
- API 边缘：`deploy/kong/` 声明式配置 + `deployment-kong.yaml`
