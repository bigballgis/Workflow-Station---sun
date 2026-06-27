# Legacy Flyway 迁移（已归档，停用）

这些是各后端服务历史上的 Flyway 迁移脚本，于 **2026-06 清退**。

## 为什么归档

实测确认（见 [../schema-single-source-init-scripts-plan.md](../schema-single-source-init-scripts-plan.md)）：
- **Flyway 在所有环境都是关的**：dev `docker-compose.dev.yml`、preprod、uat 的 configmap 都
  `SPRING_FLYWAY_ENABLED=false`；live 库有 179 张表但 **0 张 `flyway_schema_history`**。
- 后端迁移**从未在任何部署中执行过**（workflow-engine-core 甚至没装 flyway 依赖，其
  `V500__init_schema.sql` 纯属文档）。
- schema 的真实来源一直是 **`deploy/init-scripts/00-schema/`**。

维护"后端迁移 + init-scripts"两套是双轨漂移的根源。现把现实正式化：
**init-scripts 为唯一 schema 来源**，这些迁移移出运行路径、留作演进史追溯。

## 现在改 schema 怎么做

**唯一来源 = `deploy/init-scripts/00-schema/`。** 新增/改表只改那里（快照式：表+列+索引写在一起）。
不要再在这里加迁移——这里的文件**不会被执行**。

## 这些迁移的效果在哪

全部已包含在 `deploy/init-scripts/00-schema/` 当前态里（本次清退前已审计并补齐 drift：
`rt_pk_sequences`、`uk_dw_table_name` 全局唯一、`idx_dw_field_definitions_fk_ref`、
以及 P0-1 的 `rt_table_data_rows` pg_trgm/分页索引——后者原为 `admin-center/V214`，
已镜像到 `00-schema/21-add-rt-relation-tables.sql`）。

## 若将来要恢复 Flyway

git 历史 + 本目录可完整恢复：把对应 `<svc>/` 移回 `backend/<svc>/src/main/resources/db/migration/`、
恢复 3 个服务 pom 的 flyway 依赖与根 pom 的版本/插件、把 `application.yml` 的 `flyway.enabled` 改回。
但需先解决既有问题：重复版本号（admin V206×2、dw V321/V322×2）、缺基础表 baseline、
跨服务版本号冲突（platform-security V210 ↔ admin-center V210）。详见
[../flyway-unification-plan.md](../flyway-unification-plan.md)。

## 目录

- `platform-security/` — sys_* 相关（曾被 admin-center 一并加载）
- `admin-center/` — sys_*/rt_*/bi_* 增量（含已归档的 V214 索引）
- `developer-workstation/` — dw_* 增量
- `user-portal/` — up_*/portal_* 增量
- `workflow-engine-core/` — wf_* 增量（从未由 flyway 执行）
