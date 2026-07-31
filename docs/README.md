# 文档索引 — Workflow Station

本目录是项目文档的**统一归集地**，按主题分类。根目录仅保留三份入口文档：
[README.md](../README.md)（架构速览）、[BUILD_GUIDE.md](../BUILD_GUIDE.md)（构建 / 多环境部署）、
[PROJECT_ARCHITECTURE.md](../PROJECT_ARCHITECTURE.md)（项目架构）。

> 目录约定：新增技术 / 产品说明请放入下方对应子目录，并在本索引登记一行。
> `schema-and-migration.md`、`demo-data-requirements.md`、`view-access-control-test-guide.md`
> 因被 `.cursor/rules`、`docker-compose`、skills / 脚本按固定路径引用，**保留在 `docs/` 根**，请勿移动。

## 架构 · Architecture — [`architecture/`](./architecture/)

| 文档 | 说明 |
|------|------|
| [tech-stack.md](./architecture/tech-stack.md) | 技术栈与版本（中文） |
| [tech-stack-en.md](./architecture/tech-stack-en.md) | Tech stack (English) |
| [architecture-blueprint.md](./architecture/architecture-blueprint.md) | 架构蓝图：系统职责 / 数据归属 / 依赖禁令 F1–F7（2026-07 逐条核对，[html](./architecture/architecture-blueprint.html) 为同内容样式化导出版） |
| [architecture-diagram.md](./architecture/architecture-diagram.md) | 部署与模块关系示意 |
| [architecture-optimization-plan.md](./architecture/architecture-optimization-plan.md) | 架构优化方案（不迁 schema 版） |
| [p1-1-split-platform-common-plan.md](./architecture/p1-1-split-platform-common-plan.md) | P1-1 拆分 `platform-common` god module 设计 |

## 数据库 · Database — [`database/`](./database/)

| 文档 | 说明 |
|------|------|
| [schema-and-migration.md](./schema-and-migration.md) | 数据库：`init-scripts` 与 Flyway 双轨说明（根路径锚点） |
| [demo-data-requirements.md](./demo-data-requirements.md) | Demo：英文界面与种子数据约定（根路径锚点） |
| [schema-single-source-init-scripts-plan.md](./database/schema-single-source-init-scripts-plan.md) | Schema 单一事实来源方案（init-scripts 唯一来源，清退 Flyway；**已于 2026-06 执行完毕，历史记录**） |
| [flyway-unification-plan.md](./database/flyway-unification-plan.md) | Flyway 单一事实来源改造方案（**已废弃**——未采纳的方向 ①，仅留档） |
| [database-review-report.md](./database/database-review-report.md) | 数据库初始化脚本深度检查报告（2026-03；Flyway 章节已作废，实体修复结论仍有效） |
| [legacy-flyway-migrations/](./legacy-flyway-migrations/) | 历史 Flyway 迁移归档 |

## 设计规格 · Design — [`design/`](./design/)

**完整清单见 [`design/README.md`](./design/README.md)**——那里按**功能域**分组
（同一功能常有多篇：模型一篇、组件/交互一篇），并约定了文件名前缀。
新增设计文档只需在该索引登记一次，此处不再平铺，避免两处维护、漏登记。

| 功能域 | 覆盖 |
|------|------|
| [MI 多实例子任务分派](./design/README.md#mi-多实例子任务分派) | 分派模型（BPMN 契约 / 认领池）+ Assignment Mode 组件三端渲染 |
| [User Portal 身份与权限](./design/README.md#user-portal-身份与权限) | UBR 模型与工作台上下文 + 权限自助申请/退出 |
| [Developer Workstation](./design/README.md#developer-workstation) | 功能单元工作区隔离（Technical Lead / Team Lead / Developer） |
| [Table Design 建模规则](./design/README.md#table-design-建模规则) | 外键 / 主键规则 PRD |
| [邮件与提醒](./design/README.md#邮件与提醒) | 邮箱监控入子表 & 任务过期通知（未实现） |
| [跨端主题](./design/README.md#跨端主题) | 功能总蓝图、个人中心信息架构 |

## 功能规格 · Specs — [`specs/`](./specs/)

需求 / 设计 / 任务三段式**立项快照**（2026-07-30 从 `.kiro/specs/` 迁入）。与上方 `design/` 的分工：
`design/` 是现行契约，`specs/` 是「当初为什么这么做」的历史记录。完整清单与状态见
**[specs/README.md](./specs/README.md)**，下表仅列体量最大的几份：

| 文档 | 说明 |
|------|------|
| [specs/README.md](./specs/README.md) | **规格索引** — 16 份规格的主题、任务进度、落地状态；附 21 个「已放弃 / 未落笔」空题目及其实际去向 |
| [relation-tables/](./specs/relation-tables/) | Relation Tables：表结构 / 表数据管理、版本与部署、DW 表绑定与 Lookup 组件（已落地） |
| [multi-instance-task-dispatch/](./specs/multi-instance-task-dispatch/) | BPMN 多实例子流程动态任务分发（已落地，含 11 份任务实施小结） |
| [bi-management/](./specs/bi-management/) | Superset Dashboard 同步 / 分配 / Portal 嵌入（已落地） |
| [vue3-frontend-architecture-refactor/](./specs/vue3-frontend-architecture-refactor/) | admin-center 前端工程化重构（仅需求 + 设计，未排期） |

## 开发指南 · Guides — [`guides/`](./guides/)

| 文档 | 说明 |
|------|------|
| [function-unit-development-guide.md](./guides/function-unit-development-guide.md) | 功能单元（Function Unit）完整开发文档（developer-workstation 权威长文） |
| [form-script-api.md](./guides/form-script-api.md) | 表单脚本 API：Form/Component event、`$FNX:` 与 `[[FORM-CREATE-PREFIX…]]`、PortalFormApi 等 |
| [local-developer.md](./guides/local-developer.md) | 本地开发环境速记 |
| [email-sending-implementation-guide.md](./email-sending-implementation-guide.md) | 邮件发送实现指南（SMTP 连接 / 引擎发信链路；被 `scripts/email-smtp-test` 引用，根路径锚点） |

## AI 治理规则 · AI Rules — [`ai-rules/`](./ai-rules/)

| 文档 | 说明 |
|------|------|
| [ai-development-playbook.md](./ai-rules/ai-development-playbook.md) | **AI 协作开发手册** — 任务模板、验证命令、PR 自检、Issue 闭环 |
| [cursor-user-rules-paste.md](./ai-rules/cursor-user-rules-paste.md) | 可选：复制到 Cursor **User Rules**（本仓库已内置 project rule） |
| [frontend-ai-rules.md](./ai-rules/frontend-ai-rules.md) | 前端代码治理与 AI 协作规则摘要 |
| [backend-ai-rules.md](./ai-rules/backend-ai-rules.md) | 后端代码治理与规则摘要 |
| [rules-skills-reorg-plan.md](./rules-skills-reorg-plan.md) | Rule / Skill 整改方案（**2026-07-15 已执行完毕**，历史决策记录，根路径） |

> 会话级规则的单一事实来源仍是 `.cursor/rules/*.mdc`（经 SessionStart 钩子同步进各级 `CLAUDE.md`）。本目录为可读性摘要。

## 安全 · Security — [`security/`](./security/)

| 文档 | 说明 |
|------|------|
| [bank-grade-hardening-review.md](./security/bank-grade-hardening-review.md) | 外资银行级要求 — 审查结论与待决策项 |
| [SAST_REMEDIATION_SUMMARY.md](./SAST_REMEDIATION_SUMMARY.md) | SAST（Checkmarx）两轮扫描治理总结（2026-07 一次性记录，根路径；编码规范见 skill `secure-coding-sast`） |

## 测试 · Testing

| 文档 | 说明 |
|------|------|
| [view-access-control-test-guide.md](./view-access-control-test-guide.md) | View 访问管控手测指南（被 skills / 脚本引用，根路径锚点） |

## 排障与复盘 · Troubleshooting — [`troubleshooting/`](./troubleshooting/)

| 文档 | 说明 |
|------|------|
| [multi-instance-subtask-fix.md](./troubleshooting/multi-instance-subtask-fix.md) | 多实例（JSON 子表）子任务：现象、根因（id vs id_idw）、Portal/引擎修复 |
| [session-logs/](./troubleshooting/session-logs/) | 历史 AI 调试 / 修复会话记录（**gitignored**，仅本地留存） |

## 其他成文资料 · Elsewhere in the repo

- 部署目录速查：[../deploy/README.md](../deploy/README.md)（Activepieces / Superset / config-sync 等指南就近置于 `deploy/`）
- **问题台账**：`.kiro/issues.md`（仪表盘摘要）+ `.kiro/issues/index.yaml`（Open / Wontfix 明细）、
  `.kiro/issues/fixed-archive.yaml`（已修归档）。**保留在 `.kiro/` 原地**——`.cursor/rules`（`issue-radar`、
  `debug-mode-docker-workflow`、`cross-cutting`）、skills 与 `.github/pull_request_template.md` 均按该固定路径引用。
- 各级目录约定：`CLAUDE.md`（根 / `frontend/` / `backend/` / `deploy/`，进入目录自动加载）
- `.kiro/steering/` 与 `.kiro/skills/` 是 `sync-cursor-rules.mjs` 从 `.cursor/` 生成的镜像（已 gitignore），
  **不是文档**，请勿引用或复制其内容；唯一真源是 `.cursor/rules/*.mdc` 与 `.cursor/skills/`。
