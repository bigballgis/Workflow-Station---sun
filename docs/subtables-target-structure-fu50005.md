# 方案 C 改造后 — FU 50005 全流程数据结构（目标态）

> 本文只描述**改造完成后**的结构。表 id / binding / 字段名 / 节点 id 均取自 FU 50005 实测。
> 现状（错误结构）见 [subtables-single-source-of-truth-plan.md](./subtables-single-source-of-truth-plan.md) §2.2。

---

## 0. 核心规则

```ts
// __subTables__ 的 key 只有两种，一张表一个 key。用 table NAME 而非 id：
// id 是自增主键，FU clone / import / 跨环境部署都会变；name 稳定。
function storeKey(binding) {
  if (binding.tableName)         return `dw:${normalize(binding.tableName)}`  // dw:subtable
  if (binding.relationTableName) return `rt:${normalize(binding.relationTableName)}` // rt:test
  throw new Error('binding has no resolvable table name')
}
const normalize = (s) => String(s).trim().toLowerCase()   // 与 DB 的 lower() 唯一索引对齐
```

| 规则 | 说明 |
|---|---|
| **一张表 = 一个 key** | 无论被多少张表单绑定 |
| **用 name 不用 id** | id 在 clone / import / 新环境都会重映射；name 稳定 |
| **binding 不再是数据身份** | 只是「表单↔表」的连接，读取时解析出表名 |
| **无别名 key** | 不再有 `Participants` / `participants` 这类显示名副本 |
| **无按名字兜底** | 按表存一份后，「某 binding 没数据」的场景不存在 |
| **嵌套同构** | 行内 `__subTables__` 同样用 `dw:` / `rt:` |
| **`rowId` 去掉** | 冗余（见 §5.1） |

### 0.1 name 唯一性 —— DB 层已强制（实测）

```
dw_table_definitions:
  uk_dw_table_name           UNIQUE (table_name)
  ux_dw_table_name_lower     UNIQUE (lower(table_name))     ← 大小写不敏感

rt_table_definitions:
  rt_table_definitions_table_name_key  UNIQUE (table_name)
  ux_rt_table_name_lower               UNIQUE (lower(table_name))
```

**不是约定，是数据库唯一索引强制的**，且大小写不敏感 —— 所以 key 统一用 `lower()` 归一化。

| 范围 | 唯一性 | 保障 |
|---|---|---|
| DW 表名（全局） | ✅ 唯一 | DB 唯一索引 |
| RT 表名（全局） | ✅ 唯一 | DB 唯一索引 |
| **DW 名 vs RT 名** | ⚠️ **无联合约束**（当前实测 0 冲突，但理论可撞） | **靠 `dw:` / `rt:` 前缀隔离** |

前缀不是装饰：DW 和 RT 是两张独立表、各自唯一，跨表可能同名（实测还有 2 个 **id** 就撞了）。

### 0.2 虚拟表特例

`sys_users`（id `-1000000001`）**不在 `rt_table_definitions` 里**，是平台注入的只读虚拟表，
但 binding 上带 `tableName: "sys_users"` → key 为 `rt:sys_users`，规则统一。

---

## 1. FU 50005 的表与 key 映射

| table_id | table_name | 类型 | **目标 key** | 被几张表单绑 |
|---|---|---|---|---|
| 50332 | `main` | MAIN | *(顶层平铺，不进 `__subTables__`)* | 7 |
| 50331 | `subtable`（显示 Participants） | SUB | **`dw:subtable`** | 6 |
| 50330 | `attachment` | SUB | **`dw:attachment`** | 6 |
| 50333 | `people` | SUB | **`dw:people`** | 2 |
| 50334 | `meeting_remark` | ACTION | **`dw:meeting_remark`** | 3 |
| 1 | `test` | RT | **`rt:test`** | — |
| -1000000001 | `sys_users`（虚拟） | RT | **`rt:sys_users`** | — |

> MAIN 表字段平铺在 `variables` 顶层，不占 `__subTables__` 的 key。

---

## 2. 流程走完后的完整变量（目标态）

```jsonc
{
  // ═════ ① 主表 main(50332) 字段：平铺顶层 ═════
  "id":            "Meeting-000007",
  "I":             "...",
  "approved":      true,
  "approvalStatus":"...",
  "approval_result":"...",
  "action":        "...",
  "decision":      "...",
  "itemCount":     0,
  "totalPrice":    0,
  "maxItemPrice":  0,
  "requestItemsHasHighValue": false,
  "fileupload":    null,
  "lookup":        { "id":"user-dev", "username":"developer",
                     "email":"developer@e2e.workflow.local",
                     "full_name":"Developer Tester", "display_name":"Developer Tester",
                     "employee_id":"E26-DEV-001", "status":"ACTIVE", "language":"zh_CN" },
  "created_at":"2026-09-01 00:18:24", "created_by":"Developer Tester",
  "updated_at":"2026-09-01 01:51:07", "updated_by":"Developer Tester",

  // ═════ ② 平台运行时上下文 ═════
  "functionUnitId":       "50005",
  "functionUnitCode":     "fu-20260422-23tfag",
  "initiator":            "user-dev",
  "currentUserId":        "user-dev",
  "activeBusinessUnitId": "hase-hmdc",
  "__request_id":         "3_Meeting-000007",

  // ═════ ③ MI 集合变量（去掉冗余的 rowId，见 §5.1）═════
  "multiInstance_subtable_collection": [
    { "rowKey":{"id_idwvvbz":"Test-000005"},
      "assignee":"user-dev" },                                   // user 分派
    { "rowKey":{"id_idwvvbz":"Test-000006"},
      "role_code":"HMDC_Index_Role", "bu_code":"hase-hmdc" }     // role 分派
  ],

  // ═════ ④ 历史节点快照（结构不变，每完成一个任务一条）═════
  "_snapshot_97aef754-a557-11f1-ac5c-fa10e5ad8fb9": {
    "taskId":"97aef754-a557-11f1-ac5c-fa10e5ad8fb9",
    "taskDefinitionKey":"Activity_0z1px4l",
    "assignee":"user-dev",
    "completedAt":"2026-08-31T16:18:25.723153774Z",
    "fieldValues":{ ... }
  },
  "_snapshot_980072ed-a557-11f1-ac5c-fa10e5ad8fb9": { ... },

  // ═════ ⑤ 子表数据：一张表一个 key ═════
  "__subTables__": {

    // ── subtable(50331) / Participants —— MI 集合表 ──
    "dw:subtable": [
      {
        "id_idwvvbz": "Test-000005",              // 设计器主键(PK)
        "main_id":    "Meeting-000007",           // 外键 → main(50332)
        "name":       "dd",
        "test":       "",
        "assignee":   "user-dev",                 // user 分派
        "role_code":  "",
        "bu_code":    "",
        "reviewer":   { "id":"user-dev", "username":"developer",
                        "email":"developer@e2e.workflow.local",
                        "full_name":"Developer Tester",
                        "display_name":"Developer Tester",
                        "employee_id":"E26-DEV-001",
                        "status":"ACTIVE", "language":"zh_CN" },
        "task_status":       "COMPLETED",         // MI 镜像列（列名由 Sub-Task Config 配置）
        "task_current_node": "sub form2",
        "created_at":"...", "created_by":"...",
        "updated_at":"...", "updated_by":"...",

        // 行内嵌套子表：people(50333) 挂在这一行下
        "__subTables__": {
          "dw:people": [
            { "id":"p-1", "sub_task_id":"Test-000005",   // 外键 → 本行
              "sex":true, "age":"30" }
          ]
        }
      },
      {
        "id_idwvvbz": "Test-000006",
        "main_id":    "Meeting-000007",
        "name":       "FINAL-CHECK",
        "test":       "",
        "assignee":   "",                          // role 分派时 assignee 为空
        "role_code":  "HMDC_Index_Role",
        "bu_code":    "hase-hmdc",
        "reviewer":   null,                        // 用户清空 = null
        "task_status":       "IN_PROGRESS",
        "task_current_node": "sub form1",
        "created_at":"...", "created_by":"...",
        "updated_at":"...", "updated_by":"...",
        "__subTables__": { "dw:people": [] }
      }
    ],

    // ── attachment(50330)：FK main_id → main ──
    "dw:attachment": [
      { "id":"att-1", "main_id":"Meeting-000007", "file":"notes.pdf" }
    ],

    // ── people(50333)：顶层通常为空，实际行挂在 participant 行内嵌套 ──
    "dw:people": [],

    // ── meeting_remark(50334)：ACTION 表，FK main_id ──
    "dw:meeting_remark": [
      { "id":"rm-1", "main_id":"Meeting-000007", "remark":"...", "created_by":"..." }
    ],

    // ── RT 关联表：行数据在 rt_table_data_rows，这里只是绑定占位 ──
    "rt:test":           [],
    "rt:sys_users": []
  }
}
```

**`__subTables__` 共 6 个 key**（原 25 个）。

---

## 3. 各节点在目标结构下的读写

| # | 节点 | 表单 | 写 | 读 |
|---|---|---|---|---|
| 1 | `Activity_0z1px4l`「submit」 | Main (50193) | 顶层主表字段、`dw:attachment` | 同 |
| 2 | `Activity_0hwtl8v`「assignment」 | Assign Task (50191) | **`dw:subtable`**（增删参与者、填 assignee/role_code/bu_code） | `dw:subtable`、`dw:attachment` |
| 3 | `Activity_1m8yirt`「multi」MI 展开 | — | 生成 `multiInstance_subtable_collection`；每个 execution 挂 `currentItem` | 读 **`dw:subtable`**（`subTableId=50331`） |
| 4 | `Activity_0j8mz1c`「sub form1」 | Sub task (50192) | **`dw:subtable`** 中**自己那一行** + 行内 `dw:people`、`dw:attachment`、`dw:meeting_remark` | 同 |
| 5 | `Gateway_kk_subtask_route`「Route」 | — | — | 读主表/子表字段做分支判断 |
| 6 | `Activity_134mqyl`「sub form2」 | Sub task (50192) | 同 #4，更新 `task_current_node="sub form2"` | 同 |
| 7 | `EndEvent_1` | — | — | — |

**关键**：#1/#2/#4/#6 全部读写**同一个 `dw:subtable`**。
不同表单只是不同视图，数据只有一份 —— 结构上不可能出现「Assign Task 写的和 Sub task 写的不一致」。

---

## 4. binding 如何取数（不再存数据）

```
binding 50539 (Assign Task 表单)      → table_id=50331 → 读 __subTables__["dw:subtable"]
binding 50544 (Sub task 表单)         → table_id=50331 → 读 __subTables__["dw:subtable"]
binding 50627 (Main 表单)             → table_id=50331 → 读 __subTables__["dw:subtable"]
binding 50612/50617/50625 (My Request)→ table_id=50331 → 读 __subTables__["dw:subtable"]
                                                          ↑ 六个 binding，同一份数据

binding 50541 (RELATED)               → relation_table_id=1 → 读 __subTables__["rt:test"]
```

---

## 5.1 去掉 `rowId`（冗余）

现状：

```java
// MiCollectionVariableBuilder.java:330-332
Object rowId = null;
if (pkCols.size() == 1) {          // ← 只有单列主键才生成
    rowId = rowKey.get(pkCols.get(0));   // 值 = 主键的值，与 rowKey 完全重复
}
```

| | `rowKey` | `rowId` |
|---|---|---|
| 是否总存在 | ✅ 是 | ❌ 仅单列主键 |
| 支持联合主键 | ✅ | ❌ |
| 内容 | `{id_idwvvbz:"Test-000005"}` | `"Test-000005"`（同一个值） |

`rowId` 是 BPMN 表达式的便捷别名（`${currentItem.rowId}` 比
`${currentItem.rowKey.id_idwvvbz}` 好写），但**信息完全冗余**，且在联合主键下缺失
——代码里做行匹配用它会静默失效。**目标结构中去掉。**

### 影响与迁移

| 影响点 | 处理 |
|---|---|
| `MiCollectionVariableBuilder` 生成 | 不再 `item.put("rowId", ...)` |
| `SubTableRowKeySupport.rowKeyFromCurrentItem` 的 `rowId` 回退分支 | 删除（已知只在 `rowKey` 缺失时触发） |
| BPMN 里已写 `${currentItem.rowId}` 的表达式 | ⚠️ **需排查**：改用 `${currentItem.rowKey.<pk>}`，或保留一个只读派生值 |
| `rowIdVariable` BPMN 配置项（默认 `currentItem.rowId`） | 随之调整 |

### 实测扫描结果（风险可控）

```
dw_process_definitions 中引用 currentItem.rowId：4 条
全部形如：<custom:property name="rowIdVariable" value="currentItem.rowId" />
```

**4 条全是 `rowIdVariable` 的默认配置值，没有一条是用户手写的条件表达式。**
即：没有业务逻辑依赖 `currentItem.rowId` 求值，去掉它不会破坏已部署流程的判断逻辑。

处理方式：`rowIdVariable` 默认值改为 `currentItem.rowKey`（或按 PK 解析），
前端 `extractMiParticipantRowIdFromCurrentItem` 已支持从 `rowKey` map 取值。

---

## 5. MI 行级隔离在目标结构下如何工作

MI 子任务只能改**自己那一行**，机制不变，但判定更简单：

```
1. 从 execution 级变量 currentItem 取 rowKey = { id_idwvvbz: "Test-000006" }
   （currentItem 绝不写入流程级 variables —— 否则所有子任务读到同一个值）

2. 在 __subTables__["dw:subtable"] 里按 PK 找到自己那一行

3. 只替换那一行，其余参与者的行原样保留
```

现结构下需要对 6 个 key 逐一做这件事（漏一个就不一致）；
目标结构只有一个 key，**不存在漏同步的可能**。

---

## 6. 与现状的对照

| 项 | 现状 | 目标 |
|---|---|---|
| `__subTables__` key 数 | **25** | **6** |
| subtable(50331) 占几个 key | **9**（6 binding + 3 别名） | **1** |
| 同一批行存几份 | **6 份，实测已不一致**（50539=2行 / 50544=1行） | **1 份** |
| 别名 key | 有（`subtable`/`Participants`/`participants`） | **无** |
| 按名字兜底查找 | 有 | **无** |
| 能否出现同行两版本 | **能**（当前 bug 根源） | **结构上不可能** |

---

## 7. 保持不变的部分

| 项 | 说明 |
|---|---|
| 行内字段 | 一个字节不变，含 `main_id` / `id_idwvvbz` / `sub_task_id` 等外键 |
| 表间关系 | 靠字段值 + `dw_foreign_keys`（50330→50332、50331→50332、50334→50332），与 key 无关 |
| 主表字段 | 仍平铺 `variables` 顶层 |
| `multiInstance_subtable_collection` | 结构不变 |
| `currentItem` | 仍是 execution 级 |
| `_snapshot_<taskId>` | 不变，Process Flow 点历史节点正常 |
| RT 行数据 | 仍在 `rt_table_data_rows` |
| 表/表单/binding 元数据 | `dw_*` 表完全不动 |
