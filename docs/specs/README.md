# 功能规格 · Specs

本目录收录**需求 / 设计 / 任务三段式规格文档**（原 `.kiro/specs/`，2026-07-30 迁入 `docs/` 统一归集）。
每份规格是一次功能或缺陷治理的完整立项记录，回答「当初为什么这么做、边界在哪」——
与 `docs/design/`（长期有效的产品设计规格）互补：**本目录偏历史立项快照，`design/` 偏现行契约**。

> **状态列以文档自身的 `tasks.md` 勾选状态为准**，并在有出入时按仓库实际情况标注。
> 状态不代表代码现状的持续校验；改动相关功能时以代码与 `docs/design/` 为准，本目录用于追溯设计意图。

## 文档结构约定

| 文件 | 内容 |
|------|------|
| `requirements.md` | 需求文档：简介、术语表、EARS 风格验收条件 |
| `bugfix.md` | 缺陷型规格的需求文档（替代 `requirements.md`）：现状缺陷分析 + 期望行为 |
| `design.md` | 技术设计：数据模型、接口、组件改动点、测试策略 |
| `tasks.md` | 实施任务清单（`- [x]` 已完成 / `- [ ]` 未完成 / `- [ ]*` 可选测试任务） |

---

## 已落地 · Implemented

| 规格 | 主题 | 任务进度 |
|------|------|----------|
| [relation-tables](./relation-tables/) | Relation Tables 公共数据表：Admin Center 表结构 / 表数据管理、版本与部署、DW 表绑定与 Lookup 组件、Portal 只读可见性 | 91/91 |
| [multi-instance-task-dispatch](./multi-instance-task-dispatch/) | BPMN 多实例子流程动态任务分发：子表逐行派单、手动分配、数据注入与回写、级联取消 | 70/70 |
| [bi-management](./bi-management/) | Admin Center BI Management：Superset Dashboard 同步（Dashboard Registry）、按 User/Role/BU 分配、Portal Landing 嵌入 | 62/62 |
| [kong-authn-authz-fix](./kong-authn-authz-fix/) | Kong 网关接入后用户信息面板（BU / Virtual Group / Role）全空：缺 `/api/admin-center` 路由 + 认证透传 | 34/34 |
| [sub-table-placeholder-component](./sub-table-placeholder-component/) | 表单设计器「Sub Table 占位符」扩展组件：画布上直观显示对应子表并可跳转其设计器 | 27/27 |
| [sub-table-field-consistency](./sub-table-field-consistency/) | 子表字段渲染一致性：主表单 `FormRenderer` 与 `SubTableAddDialog` 控件渲染对齐 | 25/25 |
| [sub-table-add-dialog](./sub-table-add-dialog/) | 子表行录入弹窗（替代「直接插空行」），覆盖 DW Form Preview 与部署后的 User Portal | 21/21 |
| [sub-table-position-control](./sub-table-position-control/) | 子表位置控制：`config_json.rule` 中以 `type: "subTable"` + `_bindingId` 占位符控制子表在表单中的落点 | 15/15 |
| [dashboard-task-overview](./dashboard-task-overview/) | Dashboard 的 Task Overview 卡片扩展为「个人指标 + 团队指标」两组 | 13/13 |
| [rbac-mapping-manual-creation](./rbac-mapping-manual-creation/) | BI RBAC Mapping 改为手动创建模式：不再自动罗列全部系统角色，支持删除映射 | 13/13 |
| [table-status-lifecycle-fix](./table-status-lifecycle-fix/) | 表状态生命周期 `INIT → DEPLOYED → UPDATED → DEPLOYED`，修复编辑后从 Table Data 消失、`disabled` 表未过滤 | 10/10 |
| [database-config-update](./database-config-update/) | 全项目数据库配置统一到 PostgreSQL + 标准化凭据 | 17/21 ⚑ |

⚑ `database-config-update` 未勾的 4 项均为验证步骤（本地 / Docker Compose 启动、连通性、集成测试），实现任务已全部完成。

## 部分完成 · Partial

| 规格 | 主题 | 任务进度 |
|------|------|----------|
| [purchase-workflow-rejection-fix](./purchase-workflow-rejection-fix/) | 采购审批被拒时：流程图误将全部节点标绿（应仅高亮实际路径）、子表单数据未持久化 | 12/47 |

主链路修复任务已完成；未勾的 35 项中 **20 项是标记为可选的 `[ ]*` 属性测试 / 单测**，其余为 Checkpoint 验证、
历史查询性能优化、错误处理与日志增强、流程变量大数据外部存储（文档内自标「可选增强」）、发布文档。

## 仅需求与设计 · Design-only

| 规格 | 主题 | 说明 |
|------|------|------|
| [vue3-frontend-architecture-refactor](./vue3-frontend-architecture-refactor/) | admin-center 前端从「vibe coding」渐进重构到工程化架构：分层、composable 抽取、API 归位、Pinia 规范 | `tasks.md` 为空文件，从未排期；其结论已部分沉淀为 `.cursor/rules` 的 `vue-frontend` / `pinia-composable` 规则 |

## 未落笔 · Not implemented

以下两份规格设想把业务流程实现为**独立的 Spring Boot 后端模块**。仓库中不存在对应模块或实现类
（`backend/` 下无 `leave-management` / `approval-workflow` 模块），平台最终走的是
「Function Unit + BPMN 引擎」的通用建模路线，而非为单个业务定制模块。保留作设计思路参考。

| 规格 | 主题 | 任务进度 |
|------|------|----------|
| [approval-workflow-system](./approval-workflow-system/) | 通用审批流程功能单元：BPMN 2.0 流程定义 + 申请提交 / 主管审批 / 状态流转 / 审批历史 REST API | 1/87 |
| [leave-management](./leave-management/) | 请假管理：Portal 发起申请 + 预定义审批流，集成组织架构与工作流引擎 | 0/53 |

---

## 已放弃 / 未落笔的空规格

`.kiro/specs/` 下另有 **21 个只有 Kiro specId 状态文件（`.config.kiro`）、从未写入正文**的规格目录。
它们记录了「这些题目当年立过项但没落笔」，**保留在 `.kiro/specs/` 原地**，此处仅登记以免重复立项：

`ai-context-awareness`、`ai-function-unit-generation`、`ai-function-unit-generation-refactor`、
`ai-panel-layout-i18n`、`api-documentation`、`database-flyway-consistency-fix`、
`dmn-decision-table-integration`、`form-type-constraint-mismatch-fix`、`frontend-i18n-hardcoded-chinese`、
`function-unit-design-deep-audit`、`function-unit-design-review`、`function-unit-versioned-deployment`、
`k8s-environment-variables`、`kafka-in-app-messaging`、`kong-gateway-integration`、
`n8n-output-autofill-generalization`、`n8n-workflow-integration`、`process-task-form-separation`、
`travel-expense-reimbursement`、`xml-document-viewer`、`xml-viewer-fixes`

其中若干题目后来以别的形式落了地或被取代，追溯时请优先看这些位置：

| 空规格题目 | 实际去向 |
|------------|----------|
| `n8n-workflow-integration`、`n8n-output-autofill-generalization` | 自动化编排最终选型 Activepieces，见 [`ap-integration/`](../ap-integration/) |
| `kong-gateway-integration` | 缺陷侧记录见 [kong-authn-authz-fix](./kong-authn-authz-fix/)；部署配置见 `deploy/` |
| `database-flyway-consistency-fix` | 见 [schema-and-migration.md](../schema-and-migration.md) 与 [database/flyway-unification-plan.md](../database/flyway-unification-plan.md)（Flyway 方向已废弃，改 init-scripts 单一来源） |
| `frontend-i18n-hardcoded-chinese` | 现存量与风险评估见 [x-ray/audit/shared-and-crosscutting.md](../x-ray/audit/shared-and-crosscutting.md)；约束见规则 `i18n-rules` |
| `function-unit-versioned-deployment` | 见 skill `function-unit-version-rollback` 与 `function-unit-portability` |
| `dmn-decision-table-integration` | 决策表能力已在 developer-workstation 落地（`dw_decision_definitions`） |

---

## 相关文档

- 现行产品设计规格：[`docs/design/`](../design/)（含 [table-design-fk-pk-requirements.md](../design/table-design-fk-pk-requirements.md)、[mi-subtask-bu-role-assignment.md](../design/mi-subtask-bu-role-assignment.md)）
- 多实例子表排障复盘：[troubleshooting/multi-instance-subtask-fix.md](../troubleshooting/multi-instance-subtask-fix.md)
- **问题台账（Open / Fixed / Wontfix）**：`.kiro/issues.md` + `.kiro/issues/index.yaml` —— **仍在 `.kiro/` 原地**，
  因 `.cursor/rules`（`issue-radar`、`debug-mode-docker-workflow`、`cross-cutting`）、skills 与
  `.github/pull_request_template.md` 均按该固定路径引用。
- 会话级 AI 规则的唯一真源是 `.cursor/rules/*.mdc`；`.kiro/steering/` 与 `.kiro/skills/` 是
  `sync-cursor-rules.mjs` 生成的镜像（已 gitignore），**不要**把它们的内容复制进本目录。
