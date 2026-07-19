# 本地开发环境速记

- **完整构建 / 多环境部署流程**：见根目录 [BUILD_GUIDE.md](../../BUILD_GUIDE.md)。
- **本地 Compose 环境**：`deploy/environments/dev/docker-compose.dev.yml`（配合 `build-and-deploy.ps1`）。
- **DB 连接信息**：`deploy/environments/dev/.env`。
- **Schema 唯一来源**：`deploy/init-scripts/00-schema/`（Flyway 已清退，见 [schema-and-migration.md](../schema-and-migration.md)）。
- **部署目录速查**：[deploy/README.md](../../deploy/README.md)（Activepieces / Superset / config-sync 等）。
