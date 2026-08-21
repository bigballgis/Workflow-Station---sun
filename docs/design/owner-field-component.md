# Owner 组件：Table 字段 + 取值来源（Creator / Current Assignee）

> **状态：方案已定稿（2026-08-21 修订）。** 改代码前先确认本文；确认后按 playbook 执行。
>
> **给后续 agent：** Owner 是已有 Table 列上的 **控件类型**，不是自动建列、也不是每表只能一列。
> - 设计师先在 Table Design 建 `VARCHAR` 列，拖进表单后把控件改成 `type:"owner"`。**主表、子表都可以。**
> - 每个 Owner 控件选一个 **source**：`CREATOR` 或 `CURRENT_ASSIGNEE`。流转中按该来源 **自动赋值**，控件只读。
> - **一表 / 一表单可以有多个** Owner（典型：一列 Creator，一列 Current Assignee）。
> - 值落在**该 Owner 列自己的行 JSON**：一个人 `user:<userId>`；未领的一堆人 `user:<id1>,user:<id2>`；另有 `<field>__display` 姓名缓存。
> - Owner 的 source 名叫 Current Assignee，**只表示取值规则**，**不是**门户系统列 / My Requests / 申请详情头上的 Current Assignee，也**不要**和实例列 `current_assignee` / `candidate_users` 合成或读路径互相覆盖。
> - **不**改 BPMN 分派；改 Owner **不**转办任务。Owner **不**控制行可见性。

## 相对旧方案（必读）

| 版本 | 结论 | 本文 |
|------|------|------|
| 2026-08-17 Dataverse 式 | 拖组件自动建列；每表一个；空才填创建人后可改派；**禁止**跟办理人同步 | **作废** |
| 更早 `valueSource=ASSIGNEE` + 全局 `OwnerAssigneeSync` | 所有 Owner 都跟办理人变 | **作废**（同步只发生在 `source=CURRENT_ASSIGNEE` 的那些列） |
| 2026-08-19 | 先建列再改类型；source 二选一；可多个；自动赋值；系统风格只读控件 | 骨架仍有效 |
| 2026-08-21（本文） | Owner 的 Current Assignee **≠** 系统 Current Assignee；主表+子表都可用；未领把一堆 `user:` 写进 **Owner 列** | **现行** |

**相对 origin：Owner 尚未合入。** `origin` 上没有 `OwnerField.vue` / `OwnerFieldComponent` / `OwnerFieldFormReconciler`。08-17 的实现只存在于未合并的 fork / 本地分支，**不要当 origin 基线去「打补丁」**。

实现策略：

- **从 origin 开工：** 按本文 **新增** 全套（对账、提交、同步、只读控件）。不要先落地 08-17 再改。
- **若在仍含 08-17 文件的 fork 上继续：** 那些文件必须 **按本文改语义**（去掉每表一个、自动建列、空才填当前用户、可改派选组），不是在旧约束上加 source。合入 origin 时以本文为准，不要把 08-17 行为带进主干。

相关文档：

- Owner `CURRENT_ASSIGNEE` **只借用**「当前任务办理人怎么算」：已领 = 那一个人，未领 = 候选人展开后的 user 列表。赋值结果写进 **Owner 字段**。
- 系统 Current Assignee（My Requests、申请详情头、`up_process_instance.current_assignee` / `candidate_users`）**先分开**：不要合成存储，不要用系统列覆盖 Owner JSON，也不要把 Owner 当成那套 UI 的替身。
- 任务分派模型（不要复用到本组件）：BPMN `assigneeType`、`UserTaskAssigneeConfigSection.vue`
- MI 行内分派（不要改）：[mi-subtask-bu-role-assignment.md](./mi-subtask-bu-role-assignment.md)
- View 访问管控：skill `view-access-control`（Owner **不**控制行可见性）
- 关联工单：HSWORKFLOW-815 可用主表 Owner 列在 View 里**展示同类信息**；那仍是 Owner 字段，不是新增系统列 `current_assignee`

---

## 1. 背景与目标

平台里「这条记录是谁创建的」和「这单现在谁在办」是两个问题。设计师要能在 **同一张表、同一张表单** 上用两个（或多个）字段分别回答，并在 Main Table View 里勾出来。

| 设计师选的 source | 字段回答的问题 | 流转时 |
|---|---|---|
| **Creator** | 谁创建了这条记录 | 写成流程发起人（子表行 = 建行人），之后不变 |
| **Current Assignee** | 按「当前任务办理人」同一套规则取值 | 未领 → 一堆人写进本字段；领了 / 已分派到一人 → 写那一个人。主表、子表都这样 |

操作步骤：

1. Table Design：在已有 MAIN / SUB 表上新增（或使用已有）`VARCHAR` 列，例如 `case_owner`、`current_handler`。
2. 打开已绑定该表的表单，把该字段拖进画布（默认是 `input`）。
3. 把该控件类型改成 Owner，属性里选 source。
4. 保存表单。运行时按 source 自动写该列；View catalog 里勾该列即可展示。

**成功标准：**

- 同一表单可以同时有 Creator 列和 Current Assignee 列（主表或子表）。
- 发起后 Creator 列 = 发起人；审批节点再保存，Creator 列仍是发起人（不会变成当前审批人）。
- Current Assignee 列：未领 = 一堆 `user:` 写在本列；领了 / 转办后 = 那一个人。Creator 列不变。
- 两列都能在 View 显示，保存 / 刷新后与表单一致。系统 Current Assignee UI 不被这个字段替换。

---

## 2. 非目标

- **不**把 Owner 与系统 Current Assignee（实例列 `current_assignee` / `candidate_users`、My Requests 列、申请详情头）合成一个存储或一套 UI。Owner 是表单字段。
- **不改** `AssigneeType`、`TaskAssigneeResolver`、`TaskAssignmentListener`、BPMN 扩展、MI `AssignmentConfig`。
- **不**用 Owner 控制 View 行可见性（「owned by me」、行级 ACL 另开）。
- **不做**改 Owner 就转办 / 认领（单向：按办理规则取值 → 写入本字段，永不反向）。
- **不**在每张表默认追加 Owner 列；**不**在保存表单时自动 `INSERT dw_field_definitions`。
- **本期不做**表单内手改派、不做 BU+Role 组选择器（08-17 的 `allowGroup` / 选组 UI 退出 MVP；存量 `group:` 值仍须能只读展示）。
- **不**在读路径用系统 Current Assignee 覆盖 Owner 列已写入的值。

### 2.1 与现有人员概念

| 概念 | 真源 | Owner 怎么用它 |
|---|---|---|
| 发起人 `start_user_id` | 实例列，发起时写死 | **仅** `CREATOR` 主表列的赋值来源 |
| 建行人 | 子表行首次保存的操作人 | **仅** `CREATOR` 子表列的赋值来源 |
| 当前用户任务的办理人 / 候选人 | 引擎任务：已领 = assignee；未领 = candidate 展开后的 user 列表 | **仅** `CURRENT_ASSIGNEE` 的**取值规则**。算出的 ID **写进 Owner 列**（主表行或子表行）。不要把系统列当成这个字段 |
| 系统 Current Assignee UI / 实例列 | My Requests、详情头、`up_process_instance.*` | **先分开**。可以碰巧同一批人，禁止合成、禁止读路径互盖 |
| 审计 `created_by` | 行 JSON，系统填写 | 无关；不要复用审计列当 Owner |

「从表单 initiator 拿值」= 读流程实例发起人，不是读当前登录用户。发起页上两者碰巧相同；审批页上不同，必须以发起人为准。

显示名：**ID 不变，名字跟用户档案走。** Creator / Current Assignee 读表单和 View 时都按 user ID **现查**（`UserDisplayNameResolver`）。`__display` 只是缓存，投影以现查为准。人改名后，Owner 两列都显示新名。

系统列 Initiator 仍读冻结的 `start_user_name`（平台现状）。Owner **不**为了迁就它而保存旧名。若产品要求 Initiator 也现查，另开，不在本期 Owner 范围。

---

## 3. 概念模型

`type: "owner"`，`input: true`，`field` **必须**等于该表上已存在的 `dw_field_definitions.field_name`（`VARCHAR`）。

Table Design **没有** `OWNER` 数据类型。Owner 只存在于 form-create rule。

### 3.1 值模型

自动赋值只写用户：

| 形态 | 主值（标量字符串） | `__display` |
|---|---|---|
| 一个人 | `user:<userId>` | `张三` |
| 一堆人（未领） | `user:<id1>,user:<id2>,user:<id3>` | `张三, 李四, 王五`（`", "` 分隔，与姓名展示同一套） |
| 空 | 缺省 / `""` | 无则 View / 表单显示 `-` |

- 主值是 **VARCHAR 标量字符串**，禁止 JSON 对象、禁止 JSON 数组。多人用逗号拼接多个 `user:<id>`，不是数组。
- `__display` 只是缓存（给尚不能现查的导出等用），**表单和 View 展示以按 ID 现查为准**。
- 不存「待认领」等 UI 措辞。
- 若读到存量 `group:<buCode>|<roleCode>`（仅 fork 旧数据）：只读展示组名；origin 新路径不写入 group。

### 3.2 source = CREATOR

| 场景 | 写入 |
|---|---|
| 主表，发起保存 | 写 `user:<startUserId>`（实例发起人）。`__display` 按该 ID 现查写入，仅作缓存 |
| 子表行，该行首次有值前 | 写 `user:<建行人>`；`__display` 按该 ID 现查 |
| 之后任何保存 / 转办 / 流转 | **不覆盖主值**（人不变）。读路径按 ID 现查姓名，改名后显示新名 |

字段在 Portal **只读**。客户端改值，后端提交时忽略，仍按发起人 / 已有值处理。

禁止：审批节点发现空字段就填当前操作人（会把 Creator 变成审批人）。

### 3.3 source = CURRENT_ASSIGNEE

**主表、子表都可以选。** 不要再为子表保存失败（旧约束 `OWNER_ASSIGNEE_ON_SUB` 作废）。

这是 Owner 字段的取值来源，**不是**系统 Current Assignee 功能：

| 任务状态 | 写入本 Owner 列 |
|---|---|
| 还没领（候选人池） | 主值 = 展开后的全部 user：`user:<id1>,user:<id2>,…`；`__display` = 对应姓名 `name1, name2, …` |
| 已领 / 已分派到一个人 | 主值 = `user:<id>`；`__display` = 那人姓名 |
| 流程结束 | **保留最后一次写入**，不要清空 |

| 时机 | 写入 |
|---|---|
| 发起结束后第一次同步下一节点 | 按当时任务的办理规则取值，写入该表单绑定表上所有 `CURRENT_ASSIGNEE` Owner 列（主表写主表行，子表写各行） |
| 认领 / 取消认领 / 转办 / 委派 / 办完进入下一节点 | 同上 |
| 表单提交 | 以后端算出的值为准，**覆盖**客户端提交值 |

取值只**借用**与系统 Current Assignee 相同的规则（已领看 assignee，未领看 candidate 展开后的 user）。算出的 ID **只写进 Owner 列 JSON**。

- 禁止主值留空却只把姓名塞进 `__display`。
- 禁止把候选人 ID 留在实例列、不写 Owner 列。
- 禁止写成 JSON 数组，或伪造单个 `user:` 代表整池。
- 禁止新路径写入 `group:`。
- **读路径（表单、View）读 Owner 列自己的主值**，用 `parseStoredUserIds` 拆一个或多个 `user:<id>` 后现查姓名。不要用系统实例列在读时盖掉 Owner JSON。
- 主表字段和子表字段各自存各自行上的值。普通子表没有行级任务时，与主表使用同一「当前用户任务」快照，但结果写在**该子表行**上。MI 行有对应任务时，用该行任务的办理人/候选人（不改 MI `AssignmentConfig`）。

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

### 4.3 运行态（字段 JSON，主表 variables / 子表行同形）

一个人（已分派 / 已认领）：

```jsonc
{
  "case_owner": "user:u-initiator",
  "case_owner__display": "Alice",
  "current_handler": "user:u-bob",
  "current_handler__display": "Bob"
}
```

source = Current Assignee 且任务未领（一堆人）——ID **写在 Owner 列主值**，不是空字符串：

```jsonc
{
  "current_handler": "user:u-lina,user:u-zhang",
  "current_handler__display": "李娜, 张三"
}
```

主值不是 JSON 数组。界面按逗号拆 `user:` 段成多个灰底 tag。姓名缓存 `__display` 用 `", "` 拼接；展示以按 ID 现查为准。

### 4.4 View

列本来就在 Table catalog 里（设计师建过字段）。无新 `columnType`。
设计师要在 View Design **勾上**这些列；存量 View 不会自动追加。

**展示：** 读 **本列主值** 里每个 `user:<id>`，按 ID 现查姓名（页内 batch）。不要用过期 `__display`，也不要用系统 Current Assignee / 冻结 `start_user_name` 盖掉本列。单元格是普通列表文字（可带与 Lookup 只读态同款的灰底姓名 tag + 头像），不是独立系统列。

**筛选 / 排序（本期）：** SQL 走 `variables->>'field'`。一个人可等值 `user:<id>`；一堆人是逗号拼接，等值筛会漏，MVP 以展示为准。按「是否包含某 user」另开，禁止 silently 滤错。

HSWORKFLOW-815：主表 VARCHAR 做成 Owner + `CURRENT_ASSIGNEE` 再勾进 View，可以在 View **看到同类办理人信息**。那是 Owner 字段，**不是**系统 Current Assignee 列。

---

## 5. 设计器行为

### 5.1 产品步骤

```text
Table Design 建 VARCHAR 列
  → 表单把该字段拖上画布（input）
  → 属性面板将控件类型改为 Owner，选择 Creator 或 Current Assignee
  → 保存（对账：列存在、类型合法、同列 source 一致；主表/子表都允许 Creator 或 Current Assignee）
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
| 对账 | DW 新增 `OwnerFieldFormReconciler`（fork 上若已有则按 §3.4 改语义，禁止保留每表一个 / 自动建列） |

### 5.3 View 设计器

无新系统列。普通字段目录勾选即可。

---

## 6. Portal 运行时

### 6.1 渲染与风格：`OwnerField.vue`

本期两种 source 都 **只读**（无选人/选组下拉）。非法 `ownerConfig` 报错，禁止 fallback 成输入框。展示姓名来自按 ID 现查，空显示 `-`。

**样式必须跟现有系统，禁止另做一套皮肤。** 对齐 Portal `LookupField.vue` 只读态 + `ws-theme.scss` 令牌；DW Preview 与 Portal 同结构（`portal-design-parity`）。

| 元素 | 跟谁抄 |
|---|---|
| 外框 | Lookup 输入框：高 32px、边框 `#dcdfe6` / `--el-border-color`、圆角 `var(--ws-radius-input, 8px)` |
| 只读 | Lookup `.is-readonly`：背景 `--el-disabled-bg-color` `#f5f7fa`，禁点击 |
| 已选姓名 | Lookup `.lookup-selected-tag`：高 24px、灰底 `#f0f2f5`、圆角 4px、字 13px `#909399` |
| 头像 | 可保留圆形字头（22px），主色 `--primary-color` `#db0011`；不要大面积自定义 pill / 自绘 Tab |
| View 单元格 | 普通表文字，或同一灰底 tag；不要在列表里用弹层/下拉 |
| DW 属性 Source | Element Plus `el-radio-group` / `el-select`，不要自绘选项条 |

不要：独立配色圆角、可点开的选人面板（本期只读）、User/Group radio 作为主界面。fork 上若已有可编辑下拉，合入前改成只读 Lookup 态。

池任务多名：一个字段内多个灰底 tag，或一条 `name1, name2, name3` 文本（与 My Requests 同文案）；不要用组建筑图标冒充 BU+Role。

### 6.2 提交：`OwnerFieldComponent.applyOnSubmit`

按该字段的 `source` 分支，**禁止**再对所有 Owner 统一「空才填当前 userId」：

- `CREATOR`：主值为空时写 `user:<发起人>`（主表用实例 `startUserId`；子表行用建行人）。`__display` 按该 ID 现查。非空则保留主值，忽略客户端改人；`__display` 仍按主值 ID 现查覆盖（改名生效，人不变）。
- `CURRENT_ASSIGNEE`：按当前任务办理规则算出一人或一堆人，**覆盖写入本 Owner 列**（主表行或子表行）。`__display` 按这些 ID 现查写入。提交不是唯一写点，见 §6.3。

客户端提交的 `__display` 仍不信任，两种 source 都以服务端按 ID 现查为准。

发起路径：`applyOnSubmit` 发生时若实例的 `startUserId` 已确定，Creator 用它，不要用「当前登录用户」当语义来源（发起时二者应相同，测试仍要钉死 startUserId）。

### 6.3 按办理规则回写 Owner 列（仅 CURRENT_ASSIGNEE）

任务认领 / 取消认领 / 转办 / 委派 / 节点切换时，把算出的一人或一堆 `user:` **写进**该实例上所有 `source=CURRENT_ASSIGNEE` 的 Owner 列（主表 variables + 已有子表行）。可以挂在现有 `ProcessInstanceSyncComponent` 同类写点之后，但写入目标是 **Owner JSON**，不要改系统实例列的语义，也不要把系统列和 Owner 当成同一字段。

不要在 `TaskAssignmentListener` 里写 Owner。
不要改非 Owner 列。
不要改 `source=CREATOR` 列。

**不要**在 My Requests 列表 enrich 里回写 Owner JSON。View / 表单读 Owner 列自己的主值。

### 6.4 子表

- `CREATOR`：行首次保存写建行人，之后不覆盖。
- `CURRENT_ASSIGNEE`：**允许。** 按该行所处当前用户任务的办理规则写入该行 Owner 列（未领一堆 `user:`，已领一个人）。普通子表无行级任务时与主表同一任务快照，值仍落在行 JSON。
- 触达 `SubTableField.vue` / 对话框只加只读展示。`regression:mi` 全套。不改 `AssignmentConfig` / `shared.ts` merge。

### 6.5 View

- 两种 source 都读 **本列主值**（一个或多个 `user:<id>`），按 ID **现查**姓名（页内 batch，禁止逐行 HTTP）。不要用过期 `__display`，也不要用系统 Current Assignee 盖本列。
- 空主值显示 `-`。

验收必须刷 View，不能只看详情。改名后 Owner 列应显示新名（ID 仍是原列表）。

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
| DW 后端 | **新增** `OwnerFieldFormReconciler`（按 §3.4；fork 旧文件则改语义） |
| Portal 前端 | `OwnerField.vue` **只读 + Lookup/ws-theme 同款**；extractors 带上 `source`；DW Preview 对齐 |
| Portal 后端 | `OwnerFieldComponent`；`ProcessInstanceSyncComponent`（及发起 assignee 更新）写 CURRENT_ASSIGNEE 列 |
| i18n | Source 选项、控件类型、校验错误（en / zh-CN / zh-TW） |
| 测试 | 对账多列 / 必须已有列 / 跨表单 source；Creator 不被审批人覆盖；转办更新 Assignee 列、不动 Creator 列 |

**禁止改：** `platform-common` 语义、`AssigneeType` / `TaskAssignmentListener`、MI `AssignmentConfig`、`shared.ts` merge（除非渲染强制，先报告）。

---

## 9. 分期

**本期（本文 MVP）：** 先建列、改类型、source 二选一、主表+子表、可多个、自动赋值、只读、View 勾选。Current Assignee 未领写一堆 `user:` 进 Owner 列。不与系统 Current Assignee 合成。

**后续（另开）：** 手改派 / BU+Role 组；「owned by me」；独立 Assign；系统列 Current Assignee 自己的增强。

---

## 10. 风险与回滚

| 风险 | 处理 |
|------|------|
| 用当前操作人填 Creator | 主表钉死 `startUserId`；单测：审批保存不得改 Creator |
| 改名后 Owner 仍显示旧名 | 表单 / View 读路径按 ID 现查；`__display` 只是缓存。单测：改 displayName 后 Owner 显示新名、主值 ID 不变 |
| Assignee 与系统 Current Assignee 读路径混成一套 | 禁止。Owner 读本列；系统列走原路径 |
| 转办不经表单，Owner Assignee 列不更新 | 同步挂在任务办理写点，回写 Owner JSON，不挂在表单提交 |
| 池任务主值留空 / 只写 display | 主值写 `user:id1,user:id2` |
| 子表不许选 Current Assignee | 约束已作废；对账允许 SUB |
| hydration 把 owner 冲回 input | 已保存 type 优先 |
| 按姓名筛 View | MVP 不保证；SQL 滤的是 `user:<id>`。勿假装滤 display |
| 视觉自成一套 | 对照 Lookup 只读态 + `ws-theme` 截图验收 |
| fork 把 08-17 合进 origin | 禁止。合入必须以本文为准 |
| 同列两张表单 source 不一致 | 保存失败 |

回滚：表单把控件改回 input 即停止自动赋值；JSON 键保留，View 仍可读。

---

## 11. 验收

**反例：** 只能拖一个自动建列的 Owner；无法在同一表单同时展示发起人列和当前办理人列；转办后「归属」和「办理人」无法分开；Creator 在审批保存后变成审批人。

**正例：**

1. Table Design 建 `case_owner`、`current_handler`（VARCHAR）→ 主表或子表表单改成 Owner，source 分别为 Creator / Current Assignee → 保存成功。
2. 同一表单两个 Owner 都渲染；发起后 `case_owner` = 发起人；`current_handler` 未领时为 `user:id1,user:id2,…`，领了后为 `user:<那人>`。
3. 审批节点保存表单（不改这两列）→ `case_owner` 仍是发起人。
4. 转办任务 → `current_handler` 变为新办理人，`case_owner` 不变；刷新 View 两列都对。
5. 子表可以配 Creator 或 Current Assignee；Current Assignee 写在该行 JSON，不写系统实例列。
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

单测至少覆盖：§3.4 对账；Creator 钉死发起人；Assignee 随任务办理写点更新且不改 Creator；未领写入多 `user:`；子表允许 CURRENT_ASSIGNEE；`mapDataType("owner") == VARCHAR`；非法 source。

UI：DW 属性面板 Source；Portal **只读 Lookup 风格**（灰底 tag + 头像，32px 输入框）；View 两列姓名。截图进 `verification-screenshots/`，对照同页 Lookup / 普通只读输入。

---

## 13. 代码落点（实现时）

**DW：** `OwnerConfigEditor.vue`；`main.ts`；`useTableFieldRules.ts`；**新增** `OwnerFieldFormReconciler.java`（及测试）；FormDesigner 控件类型。

**Portal：** **新增** `OwnerFieldComponent`；`ProcessInstanceSyncComponent` + 发起 assignee 更新；`OwnerField.vue` / `OwnerChip.vue`（只读、抄 Lookup + `ws-theme`）；View 投影现查。

**不要改：** `TaskAssignmentListener`、`AssigneeType`、MI `AssignmentConfig`、`shared.ts` merge。

**不要把 fork 上未合并的 08-17 Owner 行为带进 origin。**

---

## 14. 已确认（2026-08-19，2026-08-21 修订）

1. 先 Table Design 建字段，再在表单把控件改成 Owner（不是拖组件自动建列）。
2. 组件上两个选项：Creator / Current Assignee；流转按选项自动赋值；控件只读。
3. Creator = 表单 / 流程 **initiator**（`start_user_id`），不是当前操作人。显示名按 ID **现查**；人改名后 Owner 显示新名，ID 不变。不冻结 `start_user_name`。
4. Owner source Current Assignee = **取值规则**（未领一堆人，领了一个人），结果写进 **本 Owner 列**。主表、子表都可以。**不要**和系统 Current Assignee（My Requests / 详情头 / 实例列）合成或读路径互盖。
5. **不限制**一个表单 / 一张表只能一个 Owner。
6. 字段进 View（勾选已有列 + `__display`），不为 815 单独加系统列。
7. 不合成一列、不反写任务分派、不控制行可见性。
8. 手改派 / `allowGroup` 退出本期 MVP。
9. **origin 无 Owner 代码**；08-17 实现未合入。从 origin 新增，或把 fork 文件改成本文后再合。
10. **控件样式抄现有系统**（Lookup 只读态 + `ws-theme`），不要新皮肤。
11. **2026-08-21：** 未领时 Owner 主值是 `user:id1,user:id2`，不是空主值 + 仅 display。子表允许 Current Assignee。`OWNER_ASSIGNEE_ON_SUB` 作废。

---

确认本文后若要开工，回复：

```text
按 playbook 执行（先输出任务整理，等我确认）。
```
