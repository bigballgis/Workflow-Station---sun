# 多实例子任务（Portal + JSON 子表）排查与修复记录

本文记录 User Portal 中「按 Developer Workstation 设计的多实例子流程」无法在待办中产生子任务、且主流程在 assignment 后误显示已完成的**成因、证据与代码修复**，便于后续演进与巡检。

---

## 1. 现象与约束

### 期望行为

- 设计器配置了 **multiInstance** 子流程、`flowable:collection`（集合变量）、子表、`assigneeField`。
- 子表数据以 **JSON** 存放在流程变量 **`__subTables__`** 中（不依赖为每张逻辑表单独建 PostgreSQL 业务表）。
- 用户完成前置 **User Task**（如 KK 流程的 **assignment** / Process Submit→`APPROVE`）后，应按子表 **行数** 产生多实例子任务；每行 **`assignee`** 对应用户在待办中可见。

### 实际问题

1. 完成 assignment 后 **不出现**子任务。
2. 「我的申请」等处显示 **整条流程已完成**，未进入子任务阶段。

---

## 2. 链路说明（简述）

完成审批走 `TaskProcessComponent.handleApproval`，在调用引擎 **`completeTask`** 之前会做：

1. **`mergeSubTablesFromTaskInfoForMi`** — 合并 `TaskInfo`/本地快照中的 **`__subTables__`**（避免前端只提交增量导致子表为空）。
2. **`injectMiCollectionFromBpmn`** — 拉 BPMN XML，从前置任务的出线 **BFS** 找到最近的 **multiInstance SubProcess**，读取 **collection** 与内层 UserTask 的 **`assigneeField`** / **`subTableName`**。
3. **`buildMiCollectionVariable`** — 用 **`__subTables__`** 组装 Flowable 所需 **集合变量**（列表元素含 `rowKey`、`rowId`、`assignee` 等）。

若集合为空或未注入，Flowable 常表现为 **实例数 0** 的子流程瞬时完成，令牌继续到全局 **EndEvent**，从而产生「无外显子任务 + 整条 COMPLETED」的表象。

前台 **Process Submit** 类按钮在本次实现中与 **`completeTask` + APPROVE** 等同（见 `useCustomActions.ts`）。

---

## 3. 根因（运行时证据）

在 User Portal 容器日志中，`[MI]` / 临时调试打点（已移除）曾出现：

- **`injectMiCollectionFromBpmn:miFound`**：BPMN 与 collection、assignee 配置 **正常可读**。
- **`buildMiCollectionVariable`** 报 **`no_eligible_rows`**：**`selectRowsForMiCollection` 打分始终为 0**（任一子表绑定列表均无「同时具备有效主键列 + assignee」的行）。

Multi-Instance Subtask Demo 样板数据中（见 `deploy/init-scripts/17-Multi-Instance-Subtask-Demo/00-init-kk.sql`）：

- 设计器 **`subtable`** 的 **PK 字段名为 `id_idw`**（`dw_field_definitions.is_primary_key`）。
- 子表表单字段也是 **`id_idw`**，JSON 行里自然也是该 key。
- 但 **`PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns`** 若在 **PostgreSQL** 中存在物理表 **`subtable`**，且其 **PRIMARY KEY 列名为 `id`**，会先命中 **catalog**，得到 **`pk = [id]`**。

于是在变量行中只有 **`id_idw`**、没有 **`id`** 时，按 **`id`** 取主键失败 → 无合格行 → 集合为空 → 多实例 0 → 整条流程走完。

---

## 4. 代码层面的修复摘要

以下内容对应一次合并提交中对多实例路径的补强（分支与提交以仓库历史为准）。

### 4.1 User Portal — `TaskProcessComponent`

- **设计器兜底**：在无物理表或精确名失败时，对 **`dw_table_definitions.table_name`** 做 **模糊匹配**，仍用 **`PostgresPhysicalTablePrimaryKeys`**（含 **`dw_field_definitions`** 兜底）解析 PK 列。
- **BPMN `subTableName`**：collection 片段与设计器表名不一致时，用扩展属性 **`subTableName`** 再试解析。
- **`__subTables__` 推断**：前述仍失败且行内存在 **`id` + assignee** 时，可尝试单列 **`id`** 的 JSON 推断路径（兜底场景）。

### 4.2 平台通用 — `SubTableRowKeySupport`

- 当解析出的单列 PK 为 **`id`**，但变量行中只有 **`id_idw`** 时：**将 `id_idw` 的值映射为逻辑 PK `id`**（`rowKeyFromVariableRow` / `rowKeyFromCurrentItem` 等分支），与设计器 **`id_idw` PK** + 物理/目录 **`id`** 不一致场景对齐。
- **注意**：这是针对 **「逻辑列名为 id、存储列为 id_idw」** 的固定别名；若将来 PK 改名为任意其他名字，应以 **§5** 的元数据一致性为准。

### 4.3 Workflow Engine — `TaskManagerComponent` / `SubTableDataInjector`

- 若运行时变量里 **集合已存在且非空**（Portal 已注入），**跳过** JDBC **`SubTableDataInjector`**。
- **无物理子表**时不要执行 `SELECT`，避免 **`relation does not exist`**；并明确日志指向 Portal 侧的 JSON 注入路径。

### 4.4 平台通用 — `PostgresPhysicalTablePrimaryKeys`

- 强化：**information_schema 无 PK** 时使用 **`dw_table_definitions` / `dw_field_definitions`** 中取主键列（与设计器保持一致）。

---

## 5. 主键列名为何会「变」、改名后会怎样？

PK **列名字符串**来自：

1. **优先**：PostgreSQL 上同名表的 **PRIMARY KEY 约束列**（若能查到）。
2. **否则**：**`dw_field_definitions`** 中对应该表且 **`is_primary_key = true`** 的 **`field_name`**。

若你在设计器中 **只改表单展示** 而不同步 **`dw_field_definitions`**（或 PG 仍保留旧 PK），后端读到的 PK **不会**自动变成你想要的列名。**JSON 行中的 key** 也需与解析出的 PK 列（或既定别名逻辑）一致。

当前代码对 **`id` / `id_idw`** 有专门映射；其它自定义 PK 名应依赖 **元数据一致 + 变量行字段一致**，避免 PG 与设计器两套定义冲突。

---

## 6. 验证建议

1. 完成产生多实例的前置任务后，Portal 日志中应出现 **`[MI] Built collection 'multiInstance_*_collection' with N items`**（`N > 0`）。
2. 引擎侧不出现「集合为空却仍去 JDBC」的误注入；子任务 assignee 与 **`__subTables__`** 中行一致。
3. KK：assignment 完成后流程 **不应**立刻全局结束（除非 BPMN 设计本身就是 0 实例即结束且无后续）。

---

## 7. 相关代码入口（检索用）

| 组件 | 路径 |
|------|------|
| 审批完成 → MI 注入 | `backend/user-portal/.../TaskApprovalCompletionComponent.java`（入口原在 `TaskProcessComponent`，后拆出）|
| MI 集合变量组装 | `backend/user-portal/.../MiCollectionVariableBuilder.java`（`injectMiCollectionFromBpmn` / `buildMiCollectionVariable` 现居于此）|
| PK 解析 / 设计器兜底 | `backend/platform-common/.../PostgresPhysicalTablePrimaryKeys.java` |
| 行主键 / `id_idw` 别名 | `backend/platform-common/.../SubTableRowKeySupport.java` |
| 引擎侧集合与物理表判定 | `backend/workflow-engine-core/.../TaskManagerComponent.java`, `SubTableDataInjector.java` |

---

## 8. 历史备注

调试阶段曾写过 **`[MI_AGENT]`** NDJSON + `/tmp/debug-*.log` 埋点；问题确认后已从 `TaskProcessComponent` **移除**，避免生产噪声。留存事实以本节与提交说明为准。
