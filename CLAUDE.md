# Workflow Station — Claude 工作约定

低代码工作流平台：Maven 多模块后端 + 3 个独立 Vue 前端。本文件由 `.cursor/rules` 转换而来，
是 **每次会话都加载** 的全局规则；与目录强相关的规则放在子目录的 `CLAUDE.md`（进入对应目录时自动加载）。

## 规则装载方式（Cursor → 多工具映射）

| Cursor 概念 | Claude | Copilot | Kiro |
|---|---|---|---|
| `alwaysApply: true` | 根 `CLAUDE.md` `@import` | `.github/copilot-instructions.md` | `.kiro/steering` `inclusion: always` |
| `globs: …` | `frontend`/`backend`/`deploy/CLAUDE.md` | `.github/instructions/*.instructions.md` | `.kiro/steering` `fileMatch` |
| `.cursor/skills/*` | `.claude/skills/*` | `.github/skills/*` | `.kiro/skills/*` |

**唯一真源**仍是 `.cursor/rules/*.mdc` + `.cursor/skills/`。同步脚本同时生成 Claude / Copilot / Kiro 副本；
维护规范见 `docs/ai-rules/ai-guidance-sync.md` 与规则 `ai-guidance-sync`。

---

## 全局规则（始终适用）

> 下方区块由 `.claude/scripts/sync-cursor-rules.mjs` 自动维护（folderOpen / SessionStart / CI）。
> **不要手动编辑**——新增/删除规则只改 `.cursor/rules/*.mdc`，然后同步。

<!-- BEGIN cursor-rules:auto -->
@.cursor/rules/project-context.mdc
@.cursor/rules/domain-model.mdc
@.cursor/rules/reasoning-protocol.mdc
@.cursor/rules/ai-guardrails.mdc
@.cursor/rules/cross-cutting.mdc
@.cursor/rules/change-playbook.mdc
@.cursor/rules/ai-development-playbook.mdc
@.cursor/rules/ai-guidance-sync.mdc
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

- `/plan` —— 功能/模块设计 Plan（先方案后实现，见 `.cursor/skills/feature-design-plan/`）
- `/verify-ui` —— UI 改动后用 Playwright 截图验证（见 `.claude/skills/verify-ui-fix-with-screenshot/`）
