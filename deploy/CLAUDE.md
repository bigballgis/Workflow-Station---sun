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
