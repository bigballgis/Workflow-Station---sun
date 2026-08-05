# AI 协作开发手册 — Workflow Station

本手册说明**如何在日常开发中**与 Cursor / AI Agent 协作，使变更可控、可验收、可回归。  
规则的单一事实来源仍是 [`.cursor/rules/`](../../.cursor/rules/) + [`.cursor/skills/`](../../.cursor/skills/)（经同步脚本生成 Claude / Copilot / Kiro 副本）；本文为**操作流程摘要**。

> **Cursor 自动加载：** [`.cursor/rules/ai-development-playbook.mdc`](../../.cursor/rules/ai-development-playbook.mdc)（`alwaysApply: true`）— 开新会话即生效，无需每次粘贴全文。

相关文档：

- [ai-guidance-sync.md](./ai-guidance-sync.md) — Cursor 真源 → Copilot / Kiro / Claude 同步与 CI 防漂移
- [frontend-ai-rules.md](./frontend-ai-rules.md) — 前端治理摘要
- [backend-ai-rules.md](./backend-ai-rules.md) — 后端治理摘要
- [../guides/form-script-api.md](../guides/form-script-api.md) — 表单脚本 API
- [../../frontend/user-portal/MI_REGRESSION.md](../../frontend/user-portal/MI_REGRESSION.md) — MI 回归命令与场景

---

## 1. 三条控制线

| 控制线 | 做什么 | 项目资产 |
|--------|--------|----------|
| **边界** | 能改什么、不能改什么 | `.cursor/rules/*.mdc`、`frontend/CLAUDE.md`、`backend/CLAUDE.md` |
| **验收** | 怎样算完成 | Vitest、Playwright 截图、`regression:mi`、Docker logs |
| **闭环** | 同类问题不再犯 | `.kiro/issues/`、专项 rule、verify 脚本 |

---

## 2. 你怎么写 — 懒人写法（推荐）

**不必手填模板。** 说想法 + 触发语；AI 先输出「任务整理」，**你回复「确认」后才会改代码**。

### 万能触发语

```text
按 playbook 整理。
```

或：`/playbook`

**不要**写「整理并执行」—— playbook 默认 **先确认再执行**。

### 示例

**Bug：**
```text
My Request 里 Sub Task 又变成 4 条了，应该 3 条。applicationId=abc123。按 playbook 整理。
```

AI 回复任务整理 → 你看没问题 → 回复 **`确认`** → AI 才开始改代码。

**想改计划：** 回复 `改：模块用 dw` 或 `改：禁止动 shared.ts` → AI 更新任务整理 → 再次等你确认。

**跳过确认（仅当你信任描述已足够清楚时）：**
```text
… 按 playbook，无需确认直接执行。
```

### 何时多写两句

| 情况 | 建议补充 |
|------|----------|
| MI / 子表 | taskId + 期望行数/字段值 |
| Preview | ① Save 旁 还是 ② 工具栏 |
| perf | 「只动取数层，不改 merge 语义」 |

---

## 3. 手填模板（可选，复杂需求时用）

在 Issue、飞书或 Cursor 首条消息中使用：

```text
【模块】portal | dw | admin | backend-<service> | platform-common
【类型】fix | feat | perf | refactor
【Issue】#14xx（如有）
【验收】
  - 反例：当前 … → 错误 …
  - 正例：期望 …
  - taskId / applicationId / FU 名（如有）
【范围】
  - 允许：frontend/user-portal/src/...
  - 禁止：shared.ts 语义层 / platform-common / 其他模块
【验证】（预期命令，AI 完成后必须贴输出摘要）
  - npm run regression:mi / vitest … / mvn … / docker compose …
```

**原则：** 手填模板适合复杂需求；日常用 §2 懒人写法即可。

---

## 4. 开发中 — 人与 AI 分工

| 阶段 | 人 | AI |
|------|----|----|
| **任务整理** | 写想法 + `按 playbook 整理` | 输出任务整理块，**停止**，等确认 |
| **确认** | 回复 `确认` 或 `改：…` | 收到确认后才 Read/Edit/Shell |
| 定位 | 补充 taskId、截图 | grep + 根因 |
| 实现 | 语义与合并决策 | 最小 diff + ReadLints |
| 验证 | 对照验收项 | build、单测、regression、Docker、贴 logs |
| **code-review 就绪** | 可发 staged 审查指令 | 对照 `.cursor/skills/code-review` 自检至可 PASS |
| 收尾 | approve merge | 交付清单 + `.kiro` fixed（验证通过后） |

**Cursor 用法：**

- UI / parity → 引用 skill：`/verify-ui` 或 `.claude/skills/verify-ui-fix-with-screenshot/`
- MI 子表 → `@.cursor/rules/portal-mi-subtable-my-request.mdc`
- 长对话（>10 轮）→ 新开会话，粘贴任务模板 + 上次 commit hash

---

## 5. 按模块 — 最小验证命令

改动完成后，**至少**跑下表对应项；PR 描述中粘贴命令与结果摘要。

### 4.1 Developer Workstation（表单 / Preview / 设计器）

```bash
cd frontend/developer-workstation && npm run build
# 可见 UI 改动
cd frontend && npm run verify:screenshot -- --app dw --url "http://localhost:3000/dev/..." --name <slug>
```

Docker（改 DW 前端后）：

```bash
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml --env-file .env up -d --build developer-workstation-frontend
```

**注意：** Form Preview 有两条路径 — Save 旁 Preview ① vs 画布工具栏 Preview ②；需求里写清测哪条。

### 4.2 User Portal（非 MI 热路径）

```bash
cd frontend/user-portal && npm run build
cd frontend/user-portal && npx vitest run <相关测试文件>
cd frontend && npm run verify:screenshot -- --app portal --url "http://localhost:3000/portal/..." --name <slug>
```

Docker：`user-portal-frontend`（若兼改后端 API 则同时 `user-portal`）。

### 4.3 MI 热路径（触达任一则完整 regression）

热路径文件（节选）：

- `frontend/user-portal/src/views/applications/detail.vue`
- `frontend/user-portal/src/views/tasks/detail.vue`
- `frontend/user-portal/src/composables/tasks/shared.ts`
- `frontend/user-portal/src/components/SubTableField.vue`
- `frontend/user-portal/src/components/FormRenderer.vue`

**完整门禁（默认，缺一不可）：**

```bash
cd frontend && npm run regression:mi
```

- Phase 1：单元（`test:regression:mi`）
- Phase 2：Playwright 截图（`verify-mi-regression-all.mjs`）
- 截图保留在 `frontend/user-portal/verification-screenshots/`，**禁止验证后删除**

Portal 未启动时仅 `npm run regression:mi:unit-only` 为**临时手段**，不算完整通过。

**Invariant 自检（MI 语义改动必填）：** 见 `.cursor/rules/performance-change-safety.mdc` I1–I7。

### 4.4 后端单服务

```bash
mvn -pl backend/<module> -am package -DskipTests
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml --env-file .env up -d --build <service>
docker compose -f docker-compose.dev.yml --env-file .env logs --tail=300 <service>
```

| 变更路径 | 典型 Compose service |
|----------|----------------------|
| `backend/user-portal/**` | `user-portal` |
| `backend/developer-workstation/**` | `developer-workstation` |
| `backend/admin-center/**` | `admin-center` |
| `backend/workflow-engine-core/**` | `workflow-engine` |
| `backend/platform-common/**` | 上述**全部**依赖它的服务 |

### 4.5 性能优化（perf PR）

- **与 fix/feat 分 PR** — 禁止同一 diff 既优化又改 merge/filter 语义
- 默认只动「取数层」（cache、并行 API、prefetch）；动语义层须先 failing test
- 触达 MI 热路径 → 仍须 `npm run regression:mi`

---

## 6. 提 PR 前 — 自检清单

GitHub 会自动加载 [`.github/pull_request_template.md`](../../.github/pull_request_template.md)。合并前确认：

- [ ] 变更类型正确（fix / feat / perf / refactor）
- [ ] MI 热路径已跑完整 `regression:mi`（或说明豁免理由）
- [ ] 可见 UI 有截图路径
- [ ] 改可部署代码已 build + 重建相关 Docker service
- [ ] 无 `_tmp_*` 等临时文件入 diff
- [ ] i18n 三语同步（如有用户可见文案）
- [ ] 改 `platform-common` 已评估爆炸半径
- [ ] **Code-review 就绪**（见 §6.1）：staged 审查预期 PASS（或 CONDITIONAL 且无 Blocker/未接受 Major）

### 6.1 Code-review 门禁 — playbook 交付必须可过审

按 playbook **整理并修改**的内容，宣称完成前必须达到：用 **code-review skill** 审查当前 **staged changes** 时结论为 **PASS**（或仅 `CONDITIONAL`：无 Blocker/未接受 Major，且缺口已记录）。

**标准审查指令（完成后应能扛住）：**

```text
使用 code-review skill 审查当前 staged changes。
不要修改、提交或推送；按 Blocker/Major/Minor/Question 输出，给出 PASS/CONDITIONAL/FAIL 结论。
```

| 要求 | 说明 |
|------|------|
| 真源 | `.cursor/skills/code-review/SKILL.md` + `reference.md` |
| Staging | 相关源码/测试/i18n/SQL/配置一并 staged；漏提交 = Major/Blocker |
| 自检范围 | 需求/正反例、最小 diff、影响链、编译、测试/UI/MI、安全与增量 secret、FU 矩阵（未触达 N/A）、性能（命中才查） |
| 放行 | 与 skill §9 一致：无 Blocker/未接受 Major + 真实 build/测试证据 + secret 干净 → 才可写 PASS |
| 审查时 | **只读**：不改代码、不 commit、不 push；固定格式输出结论 |
| FAIL | 视为未完成，先修再交付 |

规则镜像：`.cursor/rules/ai-development-playbook.mdc` §4.1。

---

## 7. Bug 闭环 — Issue → 测试 → 规则

每个 **bug 修复合并后**，至少完成以下**一项**（推荐多项）：

1. **单测** — 纳入 `vitest.mi-regression.config.ts` 或模块 `__tests__`
2. **截图脚本** — 纳入 `frontend/scripts/mi-regression-scenarios.mjs`
3. **专项规则** — 典型坑写入 `.cursor/rules/*.mdc`（如 #1441 Details 弹窗）

**`.kiro/issues/index.yaml`：**

- 验证通过后写**一条** `status: fixed`（同源 bug 不要多条 open）
- 摘要：现象 + 根因 + 主要改动路径

---

## 8. 分支与 PR 策略

| 类型 | 分支命名示例 | 注意 |
|------|--------------|------|
| fix | `fix/lookup-readonly-events` | 先 failing test 再修 |
| feat | `feat/gateway-policy-ui` | 先接口契约 |
| perf | `perf/detail-prefetch-cache` | 与 fix 分 PR；声明只动取数层 |
| refactor | `refactor/split-shared-ts` | 行为不变 + 全量 regression |

Commit：Conventional Commits — `fix(portal): …`、`feat(dw): …`

---

## 9. 特殊场景速查

### Portal ↔ Designer parity

Form Preview 弹窗 = Portal 运行时标准。改 Preview 或 Portal 任一侧，另一侧必须对齐。  
规则：`.cursor/rules/portal-design-parity.mdc`

### 发起人 My Request × MI 子表

发起人全案 vs 办理人切片语义不同，**禁止混用** To Do 的 MI 过滤逻辑。  
规则：`.cursor/rules/portal-mi-subtable-my-request.mdc`

### 表单脚本（change / blur / setValue）

规则：`.cursor/rules/form-preview-fk-pk-runtime.mdc`、`docs/guides/form-script-api.md`  
Lookup 等自定义组件须走组件事件派发链（Preview ①/② + Portal 均已接入）。

### 存量大文件拆分

`code-quality-standards.mdc` 三阶段整改须**负责人书面同意**后再启动；约束：只重组、行为不变。

---

## 10. 迭代节奏建议

**每个 bug / 小 feature：** 任务模板 → 最小验证 → PR 模板 → issue 闭环  

**每周 / 每 sprint：**

- 扫 `.kiro/issues.md` 待处理项
- 检查是否有「已 merge 但未补测试」的 MI 相关 PR
- 确认 `verification-screenshots/` 未被误删

**大功能启动前：** Plan 模式或先要「影响文件清单 + 验证计划」，确认后再 Agent 实现。

---

## 11. 快速复制 — Cursor 首条消息

**懒人版 — 只写想法 + 触发语（须回复「确认」后才执行）：**

```text
（一两句话描述你要什么）
按 playbook 整理。
```

**完整版 — 手填字段 + 明确要求先确认：**

```text
按 playbook 执行（先输出任务整理，等我确认）。

【模块】portal
【类型】fix
【Issue】#14xx
【验收】taskId=…；期望 …；禁止 Sub Task 4 条
【范围】仅 applications/detail.vue + shared.ts；禁止改 backend
【验证】npm run regression:mi；截图路径写入 PR
```

---

*最后更新：2026-07-17*
