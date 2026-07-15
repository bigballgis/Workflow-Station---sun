# Workflow Station — Claude 工作约定

低代码工作流平台：Maven 多模块后端 + 3 个独立 Vue 前端。本文件由 `.cursor/rules` 转换而来，
是 **每次会话都加载** 的全局规则；与目录强相关的规则放在子目录的 `CLAUDE.md`（进入对应目录时自动加载）。

## 规则装载方式（Cursor → Claude 映射）

| Cursor 概念 | Claude 等价物 |
|---|---|
| `alwaysApply: true` | 根 `CLAUDE.md`（或子目录 `CLAUDE.md`）中 `@import` —— 始终在上下文中 |
| `globs: backend/**` | `backend/CLAUDE.md` —— 处理该目录文件时自动加载 |
| `globs: frontend/**` | `frontend/CLAUDE.md` |
| `globs: deploy/**` | `deploy/CLAUDE.md` |
| `.cursor/skills/*` | `.claude/skills/*`（如 `/verify-ui` 截图验证） |

规则正文仍保存在 `.cursor/rules/*.mdc`（单一事实来源），下方通过 `@` 引用。
若日后删除 `.cursor/`，需把这些 `.mdc` 迁入 `.claude/rules/` 并更新引用路径。

---

## 全局规则（始终适用）

> 下方区块由 `.claude/scripts/sync-cursor-rules.mjs` 自动维护（SessionStart 钩子触发）。
> **不要手动编辑**——新增/删除规则只改 `.cursor/rules/*.mdc`，下次会话自动同步。

<!-- BEGIN cursor-rules:auto -->
@.cursor/rules/project-context.mdc
@.cursor/rules/domain-model.mdc
@.cursor/rules/reasoning-protocol.mdc
@.cursor/rules/ai-guardrails.mdc
@.cursor/rules/cross-cutting.mdc
@.cursor/rules/change-playbook.mdc
@.cursor/rules/code-quality-standards.mdc
@.cursor/rules/debug-mode-docker-workflow.mdc
@.cursor/rules/error-handling-governance.mdc
@.cursor/rules/frontend-screenshot-verification.mdc
@.cursor/rules/issue-radar.mdc
@.cursor/rules/performance-guardrails.mdc
@.cursor/rules/security-guard.mdc
<!-- END cursor-rules:auto -->

---

## 目录相关规则（进入对应目录自动加载）

- **前端** → [frontend/CLAUDE.md](frontend/CLAUDE.md)：Vue/Pinia/i18n 规范、Portal 设计 parity、MI 子表数据、FK/PK 运行时、截图验证、性能变更安全、测试
- **后端** → [backend/CLAUDE.md](backend/CLAUDE.md)：分层架构、REST API、JPA 实体、测试
- **部署** → [deploy/CLAUDE.md](deploy/CLAUDE.md)：Docker/K8s 构建与配置同步

## 技能

- `/verify-ui` —— UI 改动后用 Playwright 截图验证（见 `.claude/skills/verify-ui-fix-with-screenshot/`）
