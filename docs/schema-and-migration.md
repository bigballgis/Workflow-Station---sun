# 数据库 Schema 与迁移

> **单一来源 = `deploy/init-scripts/00-schema/`。Flyway 已清退（2026-06）。**
> 规则摘要见 `.cursor/rules/deployment-infra.mdc`、`.cursor/rules/init-scripts-append-only.mdc`、
> `.cursor/rules/project-context.mdc`；本文件是配套的详解。

## 1. 唯一来源：`deploy/init-scripts/`

- **用途**：Docker Postgres 首次初始化、`init-database.ps1`、DBA 手工建库后的**全量 DDL + 种子**。
- **结构**：`00-schema/`（快照式 DDL + 递增编号增量脚本）、`01-admin/`（权限与用户）等，按 `00-init-all.sh` 编排顺序执行。
- **规则**：**只增不改**——任何 DDL/DML/种子变更**必须新建**递增编号 `.sql`，禁止编辑已有文件（详见 `init-scripts-append-only.mdc`）。

新加表 / 列时：在 `deploy/init-scripts/00-schema/` **新建**递增编号脚本即可，全新 Docker 库会在首次挂载时自动执行。

## 2. Flyway 已清退（历史）

2026-06 起 Flyway 从后端**彻底移除**：

- 各模块 `pom.xml` **已删除 Flyway 依赖**。
- `application.yml` 中 `spring.flyway.enabled` **固化为 `false`**（不再依赖 env 覆盖）。
- 后端**不再有** `db/migration` 迁移脚本；历史迁移**归档**于 `docs/legacy-flyway-migrations/`。
- **禁止双轨**：不要再写 Flyway 迁移，也不要做「Flyway ↔ init-scripts 双轨同步」。

> 若在旧代码 / 旧文档里看到「双轨」「Flyway 增量」等说法，均为过时表述，以本文件与 `deployment-infra.mdc` 为准。

## 3. 变更检查清单

1. **新表 / 新列**：仅在 `deploy/init-scripts/00-schema/` **新建**递增编号脚本（幂等、LF 换行）。
2. **仅种子数据**：同样新建 init 脚本；生产同步走运维流程。
3. **改 schema 后验证**：删卷重建 Postgres（`dev_postgres_dev_data`）或按 `BUILD_GUIDE.md` 手工迁移；读 `docker logs platform-postgres-dev` 确认 init 无 ERROR。
4. **表前缀约定**：`dw_`（developer）、`ac_`（admin）、`up_`（portal）、`we_`（workflow）——与 `domain-model.mdc` 一致。

## 4. 相关文档

- `.cursor/rules/deployment-infra.mdc`（Schema 管理规则）
- `.cursor/rules/init-scripts-append-only.mdc`（只增不改细则）
- `docs/legacy-flyway-migrations/`（已归档的历史迁移）
