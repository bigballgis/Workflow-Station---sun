---
name: feature-design-plan
description: >-
  Produce a feature or module design Plan before implementation: scope, options,
  blast radius, acceptance, verification, and phasing. Use when the user asks for
  设计方案, 技术设计, 功能设计, 写 Plan, /plan, architecture plan, feature design,
  先出方案, 设计评审, or explicitly wants a plan before coding. Not for small bug
  fixes — use ai-development-playbook task整理 instead.
---

# Feature Design Plan — 先方案，后实现

本 skill 是**设计阶段**编排层：产出可评审的设计 Plan，**不替代**现有 rules/skills，也不复制第二套架构规范。
详细检查清单见 [reference.md](reference.md)。

**与 playbook 的分工：**

| 阶段 | 资产 | 产出 |
|------|------|------|
| 设计（本 skill） | `feature-design-plan` | 设计 Plan：背景、方案权衡、影响面、分期、验收 |
| 执行 | `ai-development-playbook` | 任务整理：允许/禁止路径、最低验证命令 → 确认后改代码 |

---

## Quick Start

1. **判定是否启用本 skill**（命中任一即启用，勿与 playbook 混用为同一输出）：
   - 用户要「方案 / 设计 / Plan / 架构」且**尚未**要求立刻改代码
   - 跨模块、新 API、schema、前后端联动、新 FU 能力、>5 文件爆炸半径
   - 用户写 `/plan` 或「先出设计 Plan」
2. **只读探查**：`Read` / `Grep` / `Glob` / `AskQuestion`（≤2 题）；**禁止** `Edit` / `Write` / 改代码 `Shell` / `git commit`。
3. **按 §1 加载相关 rules**（引用名即可，勿复制长文）。
4. **输出 §3 固定模板**；不确定标 `[待确认]`。
5. **等待用户确认**；确认后若用户要开始实现 → 引导其回复「按 playbook 执行」或粘贴 playbook 手填模板。

**不启用本 skill 的情况：** 单一 bug、路径明确的小 fix、用户已给完整验收且说「直接改」→ 用 playbook 任务整理即可。

---

## 1. 设计前必读（按需加载）

| 主题 | 引用 |
|------|------|
| 模块边界 / 技术栈 | `project-context.mdc` |
| 领域术语 / 表前缀 | `domain-model.mdc` |
| 爆炸半径 | `cross-cutting.mdc` |
| 分层 / ApiResponse | `code-quality-standards.mdc`、`backend-architecture.mdc`（后端） |
| Schema 唯一来源 | `init-scripts-append-only.mdc`、`deployment-infra.mdc` |
| JSON 行业务表 | `json-row-storage-no-physical-tables.mdc` |
| Portal ↔ DW parity | `portal-design-parity.mdc` |
| MI 子表语义 | `portal-mi-subtable-my-request.mdc` |
| FK/PK 运行时 | `form-preview-fk-pk-runtime.mdc` |
| FU 导入导出 / 回滚 | skill `function-unit-portability`、`function-unit-version-rollback` |
| View 访问 | skill `view-access-control` |
| 性能 / MI 热路径 | `performance-guardrails.mdc`、`performance-change-safety.mdc` |
| 错误处理 | `error-handling-governance.mdc` |

完整触发矩阵：[reference.md §规则触发矩阵](reference.md#规则触发矩阵)

---

## 2. 设计过程（只读）

1. **澄清目标**：问题是什么？成功长什么样？**非目标**是什么？
2. **现状探查**：Grep 符号、读目标文件 + 直接依赖；**禁止凭文件名猜测 API**（`ai-guardrails.mdc`）。
3. **方案 ≥2 个**（若明显唯一方案，说明为何无需备选）。
4. **影响面清单**：Entity / SQL / DTO / 前端 types / i18n / Kong / Kafka / 跨服务 client。
5. **风险与回滚**：数据迁移、兼容、feature flag、分期交付。
6. **验收**：必须含**反例 + 正例**；有则写 taskId / applicationId / FU 名。
7. **验证计划**：推断最低命令（build / vitest / `regression:mi` / mvn / Docker / 截图）。

**禁止：** 在设计 Plan 里写大段实现代码；禁止 scope creep（「顺便重构 X」除非单列非目标或后续分期）。

---

## 3. 输出模板（必须按此结构）

```markdown
## 设计 Plan

【背景 / 问题】
…

【目标】
…

【非目标】（本阶段不做）
…

【模块】portal | dw | admin | engine | platform-* | deploy

【方案】
- **方案 A**：…
- **方案 B**：…
- **推荐**：A / B — 理由（复杂度、爆炸半径、与现有模式一致性）

【影响面】
| 层级 | 变更 |
|------|------|
| API / Component | … |
| Entity / SQL | … |
| 前端 types / views | … |
| i18n | … |
| 部署 / 配置 | … |

【数据与契约】
- 新增/变更字段、兼容策略（只增不改）
- 跨服务 DTO 与调用方

【分期】
- **MVP**：…
- **后续**：…

【风险与回滚】
…

【验收】
- 反例：当前 … → 错误 …
- 正例：期望 …
- taskId / applicationId / FU：（有则写，无则 [待确认]）

【验证】（实现后最低命令）
- …

【待确认】
- …

---
请确认以上设计 Plan。确认后若要开始实现，请回复 **按 playbook 执行**（或补充/修正项）。
```

---

## 4. 确认后的交接

用户确认设计 Plan 后：

1. **仍不自动改代码**——除非用户在同一轮明确写「Plan 已确认，按 playbook，无需确认直接执行」。
2. 默认引导用户发起执行：
   ```text
   按 playbook 执行（先输出任务整理，等我确认）。
   （粘贴本 Plan 的【模块】【验收】【范围】【验证】）
   ```
3. 实现阶段遵循 `ai-development-playbook.mdc`；Plan 中的【非目标】写入 playbook【禁止】。

---

## 5. 用户可复制 — 开场白

**懒人版：**

```text
（一两句话描述要做什么功能/改造）
先出设计 Plan，不要改代码。
```

或：`/plan`

**完整版：**

```text
/feature-design-plan

【背景】…
【目标】…
【约束】禁止动 platform-common / 必须兼容现有 API
【偏好】优先最小 MVP，后续再扩展
```

---

## 6. 示例（摘要）

**需求：** Portal 任务详情增加「关联流程实例」只读卡片。

- **非目标：** 不改 BPMN 设计器；不做跨租户查询。
- **方案 A：** user-portal 新 Component + engine 现有 API；**方案 B：** portal 直连 Flowable REST（拒：绕过分层）。
- **影响面：** `TaskQueryComponent`、新 DTO、i18n 三语、`tasks/detail.vue`。
- **验证：** `mvn -pl backend/user-portal -am package -DskipTests` + portal build + 截图。

完整示例见 [reference.md §示例](reference.md#示例)。
