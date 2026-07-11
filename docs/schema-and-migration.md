# 数据库 Schema 与迁移

项目采用 **双轨** 管理数据库结构，二者需同时考虑，避免「新环境能起、老环境缺列」或反之。

## 1. `deploy/init-scripts/`（基线 / 演示）

- **用途**：Docker Postgres 首次初始化、`init-database.ps1`、DBA 手工建库后的 **全量或大块 DDL + 种子**。
- **典型内容**：`00-schema/` 各文件、`01-admin/` 权限与用户等。
- **特点**：按目录顺序执行；适合 **从零** 搭库与 Demo 数据。

新加表时：若希望 **全新 Docker 库** 自动具备该表，应在 `init-scripts` 中增加或更新对应 SQL（并与 Flyway 脚本语义一致）。

## 2. Flyway（增量迁移）

各模块 `application.yml` 中 **默认 `spring.flyway.enabled: true`**（admin-center / user-portal / developer-workstation）。路径为各自模块内：

| 服务 | `spring.flyway.locations`（典型） |
|------|----------------------------------|
| admin-center | `classpath:db/migration/admin-center`, `classpath:db/migration/platform-security` |
| user-portal | `classpath:db/migration/user-portal` |
| developer-workstation | `classpath:db/migration/developer-workstation` |

- **用途**：已有环境的 **版本化增量**（`Vxxx__description.sql`）。
- **workflow-engine-core**：当前模块 **未** 引入 Flyway；表结构依赖 `deploy/init-scripts` 与 Flowable/配置中的 schema 策略（见该服务 `application.yml`）。

### 2.1 与本地 Docker Compose 的关系（易错点）

`deploy/environments/dev/docker-compose.dev.yml` 对 **admin-center、user-portal、developer-workstation** 设置了环境变量 **`SPRING_FLYWAY_ENABLED=false`**。因此：

- 在 **Dev 容器** 内启动时 **不会** 跑 Flyway；数据库结构依赖 Postgres 首次挂载时执行的 **`deploy/init-scripts/`**。
- 在宿主机 **`mvn spring-boot:run`**（未设置该变量）或 **K8S**（若未覆盖 Flyway 开关）时，通常 **会** 按 `application.yml` 执行 Flyway。

若你希望 Dev 容器与 K8S 行为一致、在容器内也执行 Flyway，可去掉 compose 中的 `SPRING_FLYWAY_ENABLED` 或设为 `true`，并确认与 init 脚本无重复 DDL 冲突。

## 3. 变更检查清单（简）

1. 新表/新列：**Flyway 脚本**（升级路径）+ 必要时 **`init-scripts`**（新库路径）。
2. 仅种子数据：通常只改 `init-scripts`；若生产也需同一数据，走运维流程而非 Flyway（视项目策略）。
3. 表前缀约定：`dw_`（developer）、`ac_`（admin）、`up_`（portal）、`we_`（workflow）— 与领域模型规则一致。

## 4. 相关文档

- [function-unit-development-guide.md](./guides/function-unit-development-guide.md) §16 Flyway 约定
- `.cursor/rules/project-context.mdc`（Schema 管理摘要）
