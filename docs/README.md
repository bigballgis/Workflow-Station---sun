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
| [architecture-diagram.md](./architecture/architecture-diagram.md) | 部署与模块关系示意 |
| [architecture-optimization-plan.md](./architecture/architecture-optimization-plan.md) | 架构优化方案（不迁 schema 版） |
| [p1-1-split-platform-common-plan.md](./architecture/p1-1-split-platform-common-plan.md) | P1-1 拆分 `platform-common` god module 设计 |

## 数据库 · Database — [`database/`](./database/)

| 文档 | 说明 |
|------|------|
| [schema-and-migration.md](./schema-and-migration.md) | 数据库：`init-scripts` 与 Flyway 双轨说明（根路径锚点） |
| [demo-data-requirements.md](./demo-data-requirements.md) | Demo：英文界面与种子数据约定（根路径锚点） |
| [schema-single-source-init-scripts-plan.md](./database/schema-single-source-init-scripts-plan.md) | Schema 单一事实来源方案（init-scripts 唯一来源，清退 Flyway） |
| [flyway-unification-plan.md](./database/flyway-unification-plan.md) | Flyway 单一事实来源改造方案 |
| [database-review-report.md](./database/database-review-report.md) | 数据库初始化脚本深度检查报告 |
| [legacy-flyway-migrations/](./legacy-flyway-migrations/) | 历史 Flyway 迁移归档 |

## 设计规格 · Design — [`design/`](./design/)

| 文档 | 说明 |
|------|------|
| [feature-blueprint.md](./design/feature-blueprint.md) | 1.0 功能全景蓝图（面向 2.0 的功能树） |
| [table-design-fk-pk-requirements.md](./design/table-design-fk-pk-requirements.md) | Table Design / Relation Table：外键、主键规则、Preview↔Portal parity（PRD v1.0） |
| [portal-bu-rbac.md](./design/portal-bu-rbac.md) | 门户：业务单元角色（UBR）、工作台上下文与权限摘要 |
| [portal-permission-self-service.md](./design/portal-permission-self-service.md) | 门户：权限自助（申请 / 代办 / 退出、`\|C\|=0` 模式） |
| [developer-workstation-workspace-rbac.md](./design/developer-workstation-workspace-rbac.md) | 设计器：功能单元工作区隔离（Technical Lead / Team Lead / Developer） |
| [user-profile-information-architecture.md](./design/user-profile-information-architecture.md) | 三端个人中心 / 顶栏用户信息展示边界 |
| [mail-monitor-and-task-due-reminder-design.md](./design/mail-monitor-and-task-due-reminder-design.md) | 邮箱监控入子表 & 任务即将过期通知技术方案 |

## 开发指南 · Guides — [`guides/`](./guides/)

| 文档 | 说明 |
|------|------|
| [function-unit-development-guide.md](./guides/function-unit-development-guide.md) | 功能单元（Function Unit）完整开发文档（developer-workstation 权威长文） |
| [form-script-api.md](./guides/form-script-api.md) | 表单脚本 API：Form/Component event、`$FNX:` 与 `[[FORM-CREATE-PREFIX…]]`、PortalFormApi 等 |
| [local-developer.md](./guides/local-developer.md) | 本地开发环境速记 |

## AI 治理规则 · AI Rules — [`ai-rules/`](./ai-rules/)

| 文档 | 说明 |
|------|------|
| [frontend-ai-rules.md](./ai-rules/frontend-ai-rules.md) | 前端代码治理与 AI 协作规则摘要 |
| [backend-ai-rules.md](./ai-rules/backend-ai-rules.md) | Hermes — 后端代码治理与规则摘要 |

> 会话级规则的单一事实来源仍是 `.cursor/rules/*.mdc`（经 SessionStart 钩子同步进各级 `CLAUDE.md`）。本目录为可读性摘要。

## 安全 · Security — [`security/`](./security/)

| 文档 | 说明 |
|------|------|
| [bank-grade-hardening-review.md](./security/bank-grade-hardening-review.md) | 外资银行级要求 — 审查结论与待决策项 |

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
- 设计规格（工具管理）：[../.kiro/specs/](../.kiro/specs/)
- 各级目录约定：`CLAUDE.md`（根 / `frontend/` / `backend/` / `deploy/`，进入目录自动加载）
