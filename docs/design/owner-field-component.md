# Owner 组件：Table 字段 + 取值来源（Creator / Current Assignee）

> **状态：方案已定稿（2026-08-19 改版）。2026-08-20 确认：08-17 Dataverse 式作废；fork 上那份正文一并作废，不得改回。** 改代码前先确认本文；确认后按 playbook 执行。
>
> **给后续 agent：** Owner 是已有 Table 列上的 **控件类型**，不是自动建列、也不是每表只能一列。
> - 设计师先在 Table Design 建 `VARCHAR` 列，拖进表单后把控件改成 `type:"owner"`。
> - 每个 Owner 控件选一个 **source**：`CREATOR` 或 `CURRENT_ASSIGNEE`。流转中按该来源 **自动赋值**，控件只读。
> - **一表 / 一表单可以有多个** Owner（典型：一列跟发起人，一列跟当前办理人）。
> - 值落在该列的行 JSON：`user:<userId>` + `<field>__display`。
> - **不**把 Owner 和实例列 `current_assignee` 合成一列；**不**改 BPMN 分派；改 Owner **不**转办任务。
> - Owner **不**控制行可见性。

## 相对旧方案（必读）

| 版本 | 结论 | 本文 |
|------|------|------|
| 2026-08-17 Dataverse 式 | 拖组件自动建列；每表一个；空才填创建人后可改派；**禁止**跟办理人同步；值 = User 或 `group:BU\|Role` | **作废**（2026-08-20 确认；fork 未合入 origin） |
| 更早 `valueSource=ASSIGNEE` + 全局 `OwnerAssigneeSync` | 所有 Owner 都跟办理人变 | **作废**（同步只发生在 `source=CURRENT_ASSIGNEE` 的那些列） |
| 2026-08-19（本文） | 先建列再改类型；source 二选一；可多个；自动赋值 | **现行** |

origin 上没有 Owner 实现。fork 曾提交 08-17 设计正文（未合入 origin），**以本文为准，不要按那份实现。** 若 fork 上还有 08-17 代码（每表一个、自动建列、空才填当前操作人、`allowGroup`），必须按本文改掉，不要在旧约束上加 source。

相关文档：

- 办理人真源（只读、不要改分派）：`up_process_instance.current_assignee` / `candidate_users`，`ProcessAssigneeSnapshot`，`UserDisplayNameResolver.resolveCurrentAssigneeDisplay`
- 任务分派模型（不要复用到本组件）：BPMN `assigneeType`、`UserTaskAssigneeConfigSection.vue`
- MI 行内分派（不要改）：[mi-subtask-bu-role-assignment.md](./mi-subtask-bu-role-assignment.md)
- View 访问管控：skill `view-access-control`（Owner **不**控制行可见性）
- 关联工单：HSWORKFLOW-815（View 缺 Current Assignee）——用 `CURRENT_ASSIGNEE` 列进 View 满足，**不**新增 View 系统列

---

## 1. 背景与目标

平台里「这条记录是谁创建的」和「这单现在谁在办」是两个问题。设计师要能在 **同一张表、同一张表单** 上用两个（或多个）字段分别回答，并在 Main Table View 里勾出来。

| 设计师选的 source | 字段回答的问题 | 流转时 |
|---|---|---|
| **Creator** | 谁创建了这条记录 | 写成流程发起人（子表行 = 建行人），之后不变 |
| **Current Assignee** | 当前 case handler 是谁 | 跟随办理人快照：流转 / 转办 / 认领 / 委派都更新 |

操作步骤：

1. Table Design：在已有 MAIN / SUB 表上新增（或使用已有）`VARCHAR` 列，例如 `case_owner`、`current_handler`。
2. 打开已绑定该表的表单，把该字段拖进画布（默认是 `input`）。
3. 把该控件类型改成 Owner，属性里选 source。
4. 保存表单。运行时按 source 自动写该列；View catalog 里勾该列即可展示。

**成功标准：**

- 同一表单可以同时有 Creator 列和 Current Assignee 列。
- 发起后 Creator 列 = 发起人；审批节点再保存，Creator 列仍是发起人（不会变成当前审批人）。
- 转办后 Current Assignee 列 = 新办理人，Creator 列不变。
- 两列都能在 View 显示，保存 / 刷新后与表单一致。

---

## 2. 非目标

- **不**把 Owner 与 `up_process_instance.current_assignee` 合成一个存储字段。
- **不改** `AssigneeType`、`TaskAssigneeResolver`、`TaskAssignmentListener`、BPMN 扩展、MI `AssignmentConfig`。
- **不**用 Owner 控制 View 行可见性（「owned by me」、行级 ACL 另开）。
- **不做**改 Owner 就转办 / 认领（单向：办理人 → 字段，永不反向）。
- **不**在每张表默认追加 Owner 列；**不**在保存表单时自动 `INSERT dw_field_definitions`。
- **本期不做**表单内手改派、不做 BU+Role 组选择器（08-17 的 `allowGroup` / 选组 UI 退出 MVP；存量 `group:` 值仍须能只读展示）。
- **不**把 Current Assignee source 用在子表行上（整单办理人 ≠ 行办理人）。MI 行办理人另开。

### 2.1 与现有人员概念

| 概念 | 真源 | Owner 怎么用它 |
|---|---|---|
| 发起人 `start_user_id` | 实例列，发起时写死 | **仅** `CREATOR` 主表列的赋值来源 |
| 建行人 | 子表行首次保存的操作人 | **仅** `CREATOR` 子表列的赋值来源 |
| 办理人 `current_assignee` + `candidate_users` | 实例列，任务生命周期更新 | **仅** `CURRENT_ASSIGNEE` 主表列的赋值来源 |
| 审计 `created_by` | 行 JSON，系统填写 | 无关；不要复用审计列当 Owner |

「从表单 initiator 拿值」= 读流程实例发起人，不是读当前登录用户。发起页上两者碰巧相同；审批页上不同，必须以发起人为准。

---

## 3. 概念模型

`type: "owner"`，`input: true`，`field` **必须**等于该表上已存在的 `dw_field_definitions.field_name`（`VARCHAR`）。

Table Design **没有** `OWNER` 数据类型。Owner 只存在于 form-create rule。

### 3.1 值模型

自动赋值只写用户：

| 形态 | 主值 | `__display` |
|---|---|---|
| 用户 | `user:<userId>` | 显示名，如 `张三` |
| 空 | 缺省 / `""` | 无则 View / 表单显示 `-` |

- 标量字符串，禁止对象、禁止数组。
- `__display` 只存名字本身，不存「待认领」等 UI 措辞。
- 存量 `group:<buCode>|<roleCode>` 只读展示（解析失败按校验错误处理，禁止静默变 input）。

### 3.2 source = CREATOR

| 场景 | 写入 |
|---|---|
| 主表，发起保存 | 写 `user:<startUserId>`（实例发起人，不是「当时若为空就填当前登录人」） |
| 子表行，该行首次有值前 | 写 `user:<建行人>` |
| 之后任何保存 / 转办 / 流转 | **不覆盖** |

字段在 Portal **只读**。客户端改值，后端提交时忽略，仍按发起人 / 已有值处理。

禁止：审批节点发现空字段就填当前操作人（会把 Creator 变成审批人）。

### 3.3 source = CURRENT_ASSIGNEE

只允许出现在 **MAIN** 表绑定的表单上。子表 rule 选此 source → 保存表单失败。

| 时机 | 写入 |
|---|---|
| 发起结束后第一次同步下一节点 | 按办理人快照写该列（及所有同 source 的主表 Owner 列） |
| 认领 / 取消认领 / 转办 / 委派 / 办完进入下一节点 | 同上，挂在现有 `ProcessInstanceSyncComponent.updateProcessInstanceAssignee`（及发起路径里同等的 node/assignee 更新） |
| 流程结束 | **保留最后办理人**，不要清空（结案 View 仍能看到人） |
| 表单提交 | 以后端快照为准，**覆盖**客户端提交值 |

快照复用 `ProcessAssigneeSnapshot` + `UserDisplayNameResolver.resolveCurrentAssigneeDisplay`（与 Flowable 任务办理人 / My Requests **同一套**，不要在 Owner 里再解析一遍 BPMN `assigneeType`）：

- **1 个人**（`setAssignee`）→ 主值 `user:<id>`，`__display` = 姓名。
- **候选池 / BU+Role 下多人**：引擎已经把组**展开成** `candidate_users`（具体 userId 列表）。主值保持空（不要伪造单个 `user:`，**不要**写成 `group:<bu>\|<role>`——那是 08-17 作废的「行归属组」，不是办理人池）。`__display` 按 resolver 写成 `name1, name2, name3`（或多个灰底 tag）；读路径若主值为空，**再按实例快照投影一次**。认领后变回上一条（一个人）。
- 多实例：快照仍是引擎 `tasks.get(0)`。多个 `CURRENT_ASSIGNEE` 列写成同一快照，不是所有并行办理人的并集。本期接受，不在 Owner 里修 MI 快照。

字段在 Portal **只读**。

### 3.4 唯一性（允许多个）

| 范围 | 规则 |
|---|---|
| 一张 MAIN / SUB 表 | **可以有多个** Owner 控件；每个绑不同的 `field` |
| 同一表单 | 同一 `field` 不能出现两次 |
| 同表多张表单 | 同一 `field` 若都是 Owner，**source 必须相同**（禁止一张 CREATOR、另一张 CURRENT_ASSIGNEE） |
| 列不存在 / 非 VARCHAR / PK / 审计 / 公式列 | 保存失败 |
| 画布 | `only: false`（取消每表一个的画布限制） |

不再禁止「绑到已有业务列」——那是本方案的主路径。
不再保存时自动建列。Table Design 删列后表单仍挂 Owner → 保存失败。

---

## 4. 数据契约

### 4.1 `props.ownerConfig`

```jsonc
{ "source": "CREATOR" }           // 缺省
{ "source": "CURRENT_ASSIGNEE" }
```

JSON 字符串，对齐 `lookupConfig`。非法 JSON / 未知 source → 保存失败。运行时禁止 fallback 成 `input`。

忽略（本期不读、不写进新保存的 config）：`allowGroup`。读旧 config 时若只有 `allowGroup`、没有 `source`，视为 `CREATOR`。

### 4.2 form-create rule

```jsonc
{
  "type": "owner",
  "field": "current_handler",   // 必须已是该表 VARCHAR 列
  "title": "Current Assignee",
  "props": { "ownerConfig": "{\"source\":\"CURRENT_ASSIGNEE\"}" }
}
```

- `mapDataType("owner")` 仍为 `VARCHAR`（粘贴补表等路径）；**日常保存不再 provision 新列**。
- 控件类型存在于 rule，不存在于 `dw_field_definitions.data_type`。

### 4.3 运行态（主表 variables 示例）

```jsonc
{
  "case_owner": "user:u-initiator",
  "case_owner__display": "Alice",
  "current_handler": "user:u-bob",
  "current_handler__display": "Bob"
}
```

### 4.4 View

列本来就在 Table catalog 里（设计师建过字段）。无新 `columnType`。
投影继续优先 `<field>__display`（`PortalMainTableViewServiceImpl` 已有）。
筛选 / 排序 / 分组走现有 `jsonTextExpr` 文本语义。
设计师要在 View Design **勾上**这些列；存量 View 不会自动追加。

HSWORKFLOW-815：把主表某 VARCHAR 列做成 Owner + `CURRENT_ASSIGNEE`，再在 View 勾选该列，即可展示当前办理人。不必加 `current_assignee` 系统列。

---

## 5. 设计器行为

### 5.1 产品步骤

```text
Table Design 建 VARCHAR 列
  → 表单把该字段拖上画布（input）
  → 属性面板将控件类型改为 Owner，选择 Creator 或 Current Assignee
  → 保存（对账：列存在、类型合法、同列 source 一致、子表不得选 Current Assignee）
  → View 勾选该列
```

属性面板需要「控件类型」：至少 Input ↔ Owner（仅 VARCHAR）。选 Owner 后出现 Source。
`useTableFieldRules` 按 dataType 生成 `input` 时，**不得覆盖**已保存的 `type:"owner"` / `ownerConfig`。

Palette 仍可保留 Owner 作为快捷方式，但 `field` 必须改成已有列名才能保存；`only: false`；不自动建列。

### 5.2 落点

| 步骤 | 文件 |
|------|------|
| 注册 | `frontend/developer-workstation/src/main.ts`（`only: false`；`ownerConfig` 含 source） |
| 属性 | `OwnerConfigEditor.vue`：Source 二选一；去掉「每表一个 / 允许选组」作为主交互 |
| 改类型 | FormDesigner 属性面板 Control type（VARCHAR → Owner） |
| Hydration | `useTableFieldRules.ts`：保留已有 owner rule |
| Preview | `useFormPreviewColumns.ts` |
| 子表类型 | `designerSubTableField/types.ts` |
| 对账 | `OwnerFieldFormReconciler`：按本文 §3.4 重写（可多个、必须已有列、禁止自动建列、禁止绑审计/PK） |

### 5.3 View 设计器

无新系统列。普通字段目录勾选即可。

---

## 6. Portal 运行时

### 6.1 渲染：`OwnerField.vue`

- 两种 source 都 **只读**：展示 `__display`，无值显示 `-`。
- 非法 `ownerConfig` 报错，禁止 fallback 成输入框。
- 08-17 的选人 / 选组编辑态本期不作为 Creator/Assignee 的交互；未迁移的旧表单若仍可编辑，以实现时的兼容策略为准（建议：有 `source` 则只读）。

### 6.2 提交：`OwnerFieldComponent.applyOnSubmit`

按该字段的 `source` 分支，**禁止**再对所有 Owner 统一「空才填当前 userId」：

- `CREATOR`：主值为空时写 `user:<发起人>`（主表必须能拿到实例 `startUserId`；子表行写建行人）。非空则保留并重解析 `__display`。忽略客户端把 Creator 改成别人。
- `CURRENT_ASSIGNEE`：用当前实例快照覆盖主值 / `__display`（提交不是唯一写点，见 §6.3）。

`__display` 仍不信任客户端，服务端重解析（user → `UserDisplayNameResolver`）。

发起路径：`applyOnSubmit` 发生时若实例的 `startUserId` 已确定，Creator 用它，不要用「当前登录用户」当语义来源（发起时二者应相同，测试仍要钉死 startUserId）。

### 6.3 办理人同步（仅 CURRENT_ASSIGNEE 列）

允许改 `ProcessInstanceSyncComponent`（以及发起结束后写 node/assignee 的同等路径）：在更新 `current_assignee` / `candidate_users` 之后，把 **该实例主表 variables** 里所有 `source=CURRENT_ASSIGNEE` 的 Owner 列写成快照。

不要在 `TaskAssignmentListener` 里写 Owner。
不要改非 Owner 列。
不要改 `source=CREATOR` 列。

列表补全办理人（`ProcessApplicationQueryComponent` enrich）若会回写实例 assignee，同样要更新这些 Owner 列，否则 View 与 My Requests 会短暂不一致。实现时与 enrich 同一事务 / 同一写线程，避免只读线程写 JPA。

### 6.4 子表

- 只允许 `CREATOR`：行首次保存写建行人，之后不覆盖。
- 触达 `SubTableField.vue` / 对话框只加只读展示。`regression:mi` 全套。不改 `AssignmentConfig` / `shared.ts` merge。

### 6.5 View

读 `__display`；`CURRENT_ASSIGNEE` 且主值为空时按实例快照投影（与 §3.3 池任务一致）。验收必须刷 View，不能只看详情。

---

## 7. 后端

- 无新 HTTP 端点、无新表 / 新物理列 / 新 env。业务表仍是 JSON 行存储。
- DW：`OwnerFieldFormReconciler` 按 §3.4；`FormCreateRuleToFieldMapper` 保留 `owner → VARCHAR` 供粘贴路径。
- Portal：`OwnerFieldComponent` 按 source 分支；同步写点见 §6.3。
- **禁止**再实现「所有 Owner 都不碰 assignee 写点」或「所有 Owner 都跟 assignee」。

---

## 8. 影响面

| 层级 | 变更 |
|------|------|
| 文档 | 本文 + `docs/design/README.md` Owner 节 |
| DW 前端 | palette `only`；`OwnerConfigEditor` source；控件类型切换；hydration 保 type |
| DW 后端 | `OwnerFieldFormReconciler` 重写约束 |
| Portal 前端 | `OwnerField.vue` 只读；extractors 带上 `source` |
| Portal 后端 | `OwnerFieldComponent`；`ProcessInstanceSyncComponent`（及发起 assignee 更新）写 CURRENT_ASSIGNEE 列 |
| i18n | Source 选项、控件类型、校验错误（en / zh-CN / zh-TW） |
| 测试 | 对账多列 / 必须已有列 / 跨表单 source；Creator 不被审批人覆盖；转办更新 Assignee 列、不动 Creator 列 |

**禁止改：** `platform-common` 语义、`AssigneeType` / `TaskAssignmentListener`、MI `AssignmentConfig`、`shared.ts` merge（除非渲染强制，先报告）。

---

## 9. 分期

**本期（本文 MVP）：** 先建列、改类型、source 二选一、可多个、自动赋值、只读、View 勾选、HSWORKFLOW-815 用 Assignee 列满足。

**后续（另开）：** 手改派 / BU+Role 组；子表行级办理人；「owned by me」；独立 Assign；View 系统列 `current_assignee`（仅当产品要求不拖 Owner 也能看到办理人时再做）。

---

## 10. 风险与回滚

| 风险 | 处理 |
|------|------|
| 用当前操作人填 Creator | 主表钉死 `startUserId`；单测：审批保存不得改 Creator |
| 转办不经表单，Assignee 列不更新 | 同步挂在 assignee 写点，不挂在表单提交 |
| 池任务写成某一个 user: | 主值留空，display / 投影走 resolver |
| hydration 把 owner 冲回 input | 已保存 type 优先 |
| 旧 FU：自动建列 + 每表一个 + 可改派 | 实现时兼容：无 `source` 视为 CREATOR；已 provision 的列继续用；编辑态收敛为只读 |
| 同列两张表单 source 不一致 | 保存失败 |

回滚：表单把控件改回 input 即停止自动赋值；JSON 键保留，View 仍可读。

---

## 11. 验收

**反例：** 只能拖一个自动建列的 Owner；无法在同一表单同时展示发起人列和当前办理人列；转办后「归属」和「办理人」无法分开；Creator 在审批保存后变成审批人。

**正例：**

1. Table Design 建 `case_owner`、`current_handler`（VARCHAR）→ 表单改成 Owner，source 分别为 Creator / Current Assignee → 保存成功。
2. 同一表单两个 Owner 都渲染；发起后 `case_owner` = 发起人，`current_handler` = 第一办理节点快照。
3. 审批节点保存表单（不改这两列）→ `case_owner` 仍是发起人。
4. 转办任务 → `current_handler` 变为新办理人，`case_owner` 不变；刷新 View 两列都对。
5. 子表只能配 Creator；配 Current Assignee 保存失败。
6. 未在 Table Design 建列就放 Owner → 保存失败。
7. 同表第二张表单把 `current_handler` 设成 Creator → 保存失败（source 冲突）。
8. 同一表单可以有两个 Creator 列（绑不同 field）或两个 Current Assignee 列；不报「每表只能一个」。

---

## 12. 验证

```bash
cd frontend/developer-workstation && pnpm run build
cd frontend/user-portal && pnpm run build
mvn -pl backend/developer-workstation,backend/user-portal -am test
cd frontend && pnpm run regression:mi
cd deploy/environments/dev && docker compose -f docker-compose.dev.yml --env-file .env \
  up -d --build user-portal user-portal-frontend developer-workstation developer-workstation-frontend
```

单测至少覆盖：§3.4 对账；Creator 钉死发起人；Assignee 随 `updateProcessInstanceAssignee` 更新且不改 Creator；`mapDataType("owner") == VARCHAR`；非法 source / 子表 Assignee。

UI：DW 属性面板 Source；Portal 只读人头 / 姓名；View 两列。截图进 `verification-screenshots/`。

---

## 13. 代码落点（实现时）

**DW：** `OwnerConfigEditor.vue`；`main.ts`；`useTableFieldRules.ts`；`OwnerFieldFormReconciler.java`（及测试）；FormDesigner 控件类型。

**Portal：** `OwnerFieldComponent`（source 分支，提交签名需能拿到 `startUserId`）；`ProcessInstanceSyncComponent` + 发起 assignee 更新；`OwnerField.vue` 只读；View 投影（池任务主值为空时）。

**不要改：** `TaskAssignmentListener`、`AssigneeType`、MI `AssignmentConfig`、`shared.ts` merge。

---

## 14. 已确认（2026-08-19）

1. 先 Table Design 建字段，再在表单把控件改成 Owner（不是拖组件自动建列）。
2. 组件上两个选项：Creator / Current Assignee；流转按选项自动赋值；控件只读。
3. Creator = 表单 / 流程 **initiator**（`start_user_id`），不是当前操作人。
4. Current Assignee = 现有办理人快照逻辑；转办 / 流转更新该列。
5. **不限制**一个表单 / 一张表只能一个 Owner。
6. 字段进 View（勾选已有列 + `__display`），不为 815 单独加系统列。
7. 不合成一列、不反写任务分派、不控制行可见性。
8. 手改派 / `allowGroup` 退出本期 MVP。

---

确认本文后若要开工，回复：

```text
按 playbook 执行（先输出任务整理，等我确认）。
```
