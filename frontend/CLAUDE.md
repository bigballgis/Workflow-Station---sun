# 前端规则（frontend/**）

处理 `frontend/` 下文件时自动加载。继承根 [CLAUDE.md](../CLAUDE.md) 的全局规则。

## 前端规则（自动同步）

> 下方区块由 `.claude/scripts/sync-cursor-rules.mjs` 自动维护。**不要手动编辑**——
> 新增/删除前端规则只改 `.cursor/rules/*.mdc`（`globs: frontend/**`），下次会话自动归位到这里。

<!-- BEGIN cursor-rules:auto -->
@../.cursor/rules/form-preview-fk-pk-runtime.mdc
@../.cursor/rules/i18n-rules.mdc
@../.cursor/rules/performance-change-safety.mdc
@../.cursor/rules/pinia-composable.mdc
@../.cursor/rules/portal-design-parity.mdc
@../.cursor/rules/portal-dialog-form-labels.mdc
@../.cursor/rules/portal-mi-subtable-my-request.mdc
@../.cursor/rules/testing.mdc
@../.cursor/rules/vue-frontend.mdc
<!-- END cursor-rules:auto -->

> 提醒：改动可见 UI 后必跑 Playwright 截图；触达 MI 热路径必跑 `pnpm run regression:mi`。
> 截图存 `frontend/<app>/verification-screenshots/`，**禁止验证后删除**。见技能 `/verify-ui`。
