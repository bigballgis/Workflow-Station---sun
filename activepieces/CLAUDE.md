# Activepieces 子树规则（activepieces/**）

处理 `activepieces/` 下文件时自动加载。继承根 [CLAUDE.md](../CLAUDE.md) 的全局规则。

`activepieces/` 是硬分叉进来的自有源码（[D12](../docs/ap-integration/DECISIONS.md#d12)）、
按纯自维护对待（[D13](../docs/ap-integration/DECISIONS.md#d13)），不以对齐上游为设计约束。

> **上游自带的 `CLAUDE.md` / `AGENTS.md`（软链到 AGENTS.md）/ `.claude/` / `.cursor/` / `.agents/`
> 已于 2026-08-07 随 VT-19 删除**：它们会在进入本目录时被加载，绕过本仓库
> 「`.cursor/rules` 是唯一真源」的约定（见根 [CLAUDE.md](../CLAUDE.md) 与规则 `ai-guidance-sync`），
> 而且 `.claude/settings.json` 自带一个走公网 `npx` 的 MCP server —— 气隙部署下是纯失败面。
> 其中仍然成立的约定已收编进 `.cursor/rules/activepieces-vendor.mdc`，由下方自动区块落到这里。

## 子树规则（自动同步）

> 下方区块由 `.claude/scripts/sync-cursor-rules.mjs` 自动维护。**不要手动编辑**——
> 新增/删除本子树规则只改 `.cursor/rules/*.mdc`（`globs: activepieces/**`），下次会话自动归位到这里。

<!-- BEGIN cursor-rules:auto -->
@../.cursor/rules/activepieces-vendor.mdc
<!-- END cursor-rules:auto -->

> 关键提醒：新建 TypeORM 实体必须手动进 `getEntities()`；所有查询带 `projectId`/`platformId`；
> 出网 HTTP 走 `safeHttp`；改 `packages/shared` 要 bump 版本并 `turbo run build` 才看得到新导出；
> 锁文件是提交进仓库的资产，改 manifest 必须 `pnpm install --lockfile-only` 一起提交。
>
> 文档入口：[docs/ap-integration/](../docs/ap-integration/) —— `DECISIONS.md`（裁决）、
> `HERMES_PATCHES.md`（改动动机与踩坑）、`PIECE_DEVELOPMENT_HOWTO.md`（自研 piece 全流程，已实测）、
> `VENDOR_TRIM_CHECKLIST.md`（裁剪批次）。
