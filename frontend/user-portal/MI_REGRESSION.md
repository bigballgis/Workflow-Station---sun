# MI 回归 — 单元 + 截图

完整回归 = **Vitest 单测** + **Playwright 截图断言**（缺一不可）。

## 命令

```bash
# 完整回归（默认，需 portal @ localhost:3000）
cd frontend && npm run regression:mi

# 仅单元（portal 未启动时的临时手段，不算完整回归）
cd frontend && npm run regression:mi:unit-only

# 仅截图场景（跳过单元）
cd frontend && npm run regression:mi:screenshots
```

## 场景 ↔ 单测 ↔ 截图

| 场景 ID | Issue | Playwright 脚本 | 覆盖单测 | 截图 slug |
|---------|-------|-----------------|----------|-----------|
| 1441-myrequest-details | #1441 | `verify-myrequest-details-modal.mjs` | mcyInitiator, mergeMi, dropSubsumed | `app-*-details-060-unprocessed.png`, `…-061-filled.png` |
| 1440-sex-toggle | #1440 | `verify-sex-toggle-isolation.mjs` | linkFormMiIsolation, inlineFormBelowTable | `task-6c6c-sex-before/after.png` |
| 1438-attachment | #1438 | `verify-mi-attachment-rows.mjs` | subTableRowMetaFields | `task-093962-attachment-table.png`, `…-subtask-grid.png` |
| 1439-subform2 | #1439 | `verify-subform2-people-carry-forward.mjs` | subForm2CarryForward | `task-75d662-subform2-people.png` |
| 1435-inline-uuid | #1435 | `verify-mi-people-inline-uuid.mjs` | subTableRowRuntime, linkFormMiIsolation | `task-09367-people-inline-uuid.png` |
| assignee-slice | miSubProcessScope | `verify-mi-assignee-subtask-slice.mjs` | miSubProcessScope | `task-6c6c-assignee-subtask-slice.png` |

映射源码：`frontend/scripts/mi-regression-scenarios.mjs`  
截图目录：`verification-screenshots/`（**验证后保留，禁止删除**）

规则：`.cursor/rules/performance-change-safety.mdc`
