# 部署规则（deploy/**）

处理 `deploy/`、`Dockerfile*`、`docker-compose*`、`application*.yml`、`nginx.conf` 时自动加载。
继承根 [CLAUDE.md](../CLAUDE.md) 的全局规则。

## 部署规则（自动同步）

> 下方区块由 `.claude/scripts/sync-cursor-rules.mjs` 自动维护。**不要手动编辑**——
> 新增/删除部署规则只改 `.cursor/rules/*.mdc`（`globs: deploy/**` 等），下次会话自动归位到这里。

<!-- BEGIN cursor-rules:auto -->
@../.cursor/rules/deployment-infra.mdc
@../.cursor/rules/docker-k8s-config-sync.mdc
<!-- END cursor-rules:auto -->

> 关键提醒：前端用 `Dockerfile.local`、不用多阶段构建；`.sh`/`.sql` 必须 LF；
> 改环境变量必须同一会话内同步 K8s ConfigMap/Secret（见 `deploy/CONFIG_SYNC.md`）。
> 改可部署单元后按根 `debug-mode-docker-workflow` 规则重建对应 Compose 服务并核对日志。
>
> **Schema 唯一来源 = `deploy/init-scripts/00-schema/`**（快照式）。Flyway 已清退（2026-06），
> 后端不再有 `db/migration`，历史归档于 `docs/legacy-flyway-migrations/`。新增/改表只改 00-schema，
> **不要再写 Flyway 迁移，也不要再做"Flyway↔init 双轨同步"**（双轨已消除）。
> **init-scripts SQL 只增不改**：禁止编辑已有 `.sql`，变更一律新建递增编号文件（见 `init-scripts-append-only.mdc`）。
