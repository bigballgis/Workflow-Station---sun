# 流程实例 JSON 数据存储结构（实测）

> 样本：FU 50005「Multi-Instance Subtask Demo」
> 流程实例 `97a4e608-a557-11f1-ac5c-fa10e5ad8fb9`
> 所有内容均为从 `up_process_instance.variables` 实际 dump，非示意。

---

## 0. 三处存储总览

| 数据 | 存储位置 | 形态 |
|---|---|---|
| 主表(MAIN)字段值 | `up_process_instance.variables` **顶层** | 平铺的标量/对象 |
| 子表(SUB/ACTION)行 | `variables.__subTables__` | 按 key 分组的行数组 |
| 关联表(RT)行 | **`rt_table_data_rows`**（独立表，JSONB） | 不进流程变量 |

> 无物理业务表 —— `dw_table_definitions` 只存 schema（规则 `json-row-storage-no-physical-tables`）。

---

## 1. `up_process_instance.variables` 顶层结构

```jsonc
{
  // ── ① 主表(main / 50332)字段值：平铺在顶层 ──
  "id":            "Meeting-000007",     // string
  "I":             "...",                // string（设计器字段）
  "action":        "...",
  "decision":      "...",
  "approved":      true,                 // boolean
  "approvalStatus":"...",
  "approval_result":"...",
  "itemCount":     0,                    // number
  "totalPrice":    0,
  "maxItemPrice":  0,
  "requestItemsHasHighValue": false,     // boolean
  "fileupload":    null,                 // null
  "lookup":        { ...用户对象... },    // object（见 §4）
  "created_at":    "2026-09-01 00:18:24",
  "created_by":    "Developer Tester",
  "updated_at":    "2026-09-01 01:51:07",
  "updated_by":    "Developer Tester",

  // ── ② 平台运行时上下文 ──
  "functionUnitId":       "50005",
  "functionUnitCode":     "fu-20260422-23tfag",
  "initiator":            "user-dev",
  "currentUserId":        "user-dev",
  "activeBusinessUnitId": "hase-hmdc",
  "__request_id":         "3_Meeting-000007",

  // ── ③ 子表行数据（见 §2）──
  "__subTables__": { ... },

  // ── ④ MI 多实例集合变量（见 §3）──
  "multiInstance_subtable_collection": [ ... ],

  // ── ⑤ 历史节点快照：每完成一个任务写一个（见 §5）──
  "_snapshot_97aef754-a557-11f1-ac5c-fa10e5ad8fb9": { ... },
  "_snapshot_980072ed-a557-11f1-ac5c-fa10e5ad8fb9": { ... },

  // ── ⑥ UI 布局占位噪音：值恒为 null，后端零引用 ──
  "__subTable_50553": null,
  "__subTable_50627": null
}
```

---

## 2. `__subTables__` —— 子表行数据（当前问题所在）

### 2.1 实际形态：25 个 key，同一张表多份副本

```jsonc
"__subTables__": {
  // ═══ 表 50331 (subtable，页面显示 Participants) ═══
  //     6 个 binding + 3 个别名 = 9 个 key，内容可能不一致
  "50539":        [ {...}, {...} ],   // binding: Assign Task 表单        2 行
  "50627":        [ {...}, {...} ],   // binding: Main 表单                2 行
  "Participants": [ {...}, {...} ],   // 显示名别名                        2 行
  "participants": [ {...}, {...} ],   // 归一化别名                        2 行
  "50544":        [ {...} ],          // binding: Sub task 表单           ⚠ 1 行
  "subtable":     [ {...} ],          // table_name 别名                  ⚠ 1 行
  "50612": [], "50617": [], "50625": [],   // My Request 系列 binding

  // ═══ 表 50330 (attachment) ═══
  "50542": [], "50548": [], "50553": [], "50615": [], "50621": [], "50626": [],
  "Attachment": [], "attachment": [],

  // ═══ 表 50333 (people) ═══
  "50547": [], "50620": [], "People": [], "people": [],

  // ═══ 表 50334 (meeting_remark, ACTION) ═══
  "50629": [], "50635": [], "50637": [],
  "Meeting Remark": [], "meeting remark": [], "meeting_remark": [],

  // ═══ RT 关联表 binding（行数据其实在 rt_table_data_rows）═══
  "50540": [], "50541": [], "50545": [], "50546": [], "50550": [], "50551": [],
  "test": []
}
```

**key 有三种来源**：`binding id` / `table_name` / 显示名与归一化名。

### 2.2 一行完整数据（实测 dump）

```jsonc
{
  "id_idwvvbz": "Test-000006",          // 设计器主键（PK）
  "id":         "Meeting-000007",       // 冗余
  "row_id":     "10bc0a36-41e7-...",    // 前端本地身份（每份快照可能不同！）
  "main_id":    "Meeting-000007",       // 外键 → main 表

  "name":       "FINAL-CHECK",          // 业务字段
  "test":       "",
  "assignee":   "",
  "bu_code":    "hase-hmdc",
  "role_code":  "HMDC_Index_Role",

  "reviewer": {                          // 用户型字段：整个用户对象内联
    "id": "user-dev", "username": "developer",
    "email": "developer@e2e.workflow.local",
    "full_name": "Developer Tester", "display_name": "Developer Tester",
    "employee_id": "E26-DEV-001", "status": "ACTIVE", "language": "zh_CN"
  },

  "task_status":       "IN_PROGRESS",   // MI 进度镜像列
  "task_current_node": "sub form1",     //   （Sub-Task Config 可配置列名）

  "created_at": "...", "created_by": "...",   // 审计字段
  "updated_at": "...", "updated_by": "...",

  // ── 嵌套：行内还可以有自己的 __subTables__（link-form 子表）──
  "__subTables__": { "50547": [], "People": [], "people": [] }
}
```

> ⚠ `row_id` 是**前端生成的本地身份**，同一物理行在不同快照里值不同 —
> 不能用它做跨快照的行匹配（曾导致两个参与者的行被合并）。

---

## 3. MI 多实例集合变量

```jsonc
"multiInstance_subtable_collection": [
  { "rowId":  "Test-000005",
    "rowKey": { "id_idwvvbz": "Test-000005" },   // 按设计器 PK 构造
    "assignee": "user-dev" },
  { "rowId":  "Test-000006",
    "rowKey": { "id_idwvvbz": "Test-000006" },
    "role_code": "HMDC_Index_Role", "bu_code": "hase-hmdc" }   // 角色分派时
]
```

每个 MI 子任务的 execution 上还有 `currentItem` 变量（同样结构），
是该子任务「我负责哪一行」的唯一权威来源。

---

## 4. 用户型字段的存法

用户字段（`lookup`、`reviewer`、`assignee`…）**内联整个用户对象**，不是 id 引用：

```jsonc
{ "id":"user-dev", "username":"developer", "email":"...",
  "full_name":"Developer Tester", "display_name":"Developer Tester",
  "employee_id":"E26-DEV-001", "status":"ACTIVE", "language":"zh_CN" }
```

清空时置为 `null`（不是 `""`）。

---

## 5. 历史节点快照

每完成一个任务写一个独立变量，**与 `__subTables__` 完全无关**：

```jsonc
"_snapshot_<taskId>": {
  "taskId":            "97aef754-a557-11f1-ac5c-fa10e5ad8fb9",
  "taskDefinitionKey": "Activity_0z1px4l",
  "assignee":          "user-dev",
  "completedAt":       "2026-08-31T16:18:25.723153774Z",
  "fieldValues":       { ... }
}
```

Process Flow 上点历史节点看当时数据，读的就是这些。

---

## 6. 关联表(RT)行数据 —— 不在流程变量里

存在独立表 `rt_table_data_rows`（JSONB）：

```
table_id=2  →     3 行     table_id=14 → 20034 行
table_id=28 →    17 行     table_id=29 →    10 行
```

binding 通过 `relation_table_id` 指向 `rt_table_definitions`。
虚拟表 `-1000000001` = `sys_users`（平台注入的只读用户表）。

---

## 7. 表 / 表单 / 绑定的对应关系（元数据层）

### 7.1 表定义 `dw_table_definitions`（FU 50005）

| table_id | table_name | 类型 |
|---|---|---|
| 50332 | `main` | MAIN |
| 50331 | `subtable` | SUB（显示 Participants） |
| 50330 | `attachment` | SUB |
| 50333 | `people` | SUB |
| 50334 | `meeting_remark` | ACTION |

### 7.2 一张表单绑多张表 —— 以 **Sub task** 为例

| binding | 类型 | 绑定目标 | 外键 |
|---|---|---|---|
| 50543 | PRIMARY | `dw:main` | — |
| 50544 | SUB | `dw:subtable` | `id_idwvvbz` |
| 50547 | SUB | `dw:people` | `id` |
| 50548 | SUB | `dw:attachment` | `main_id` |
| 50635 | ACTION | `dw:meeting_remark` | `main_id` |
| 50545 | RELATED | `rt:sys_users`(-1000000001) | — |
| 50546 | RELATED | `rt:test` | — |

`main` 被 7 张表单各绑一次、`subtable` 被 6 张表单各绑一次
→ **这就是同一张表产生多个 binding id、进而在 `__subTables__` 里产生多份副本的根源。**

### 7.3 绑定目标的干净二分（101 条 binding 实测）

```
70 条 → 只有 table_id           （DW 设计器表）
31 条 → 只有 relation_table_id  （RT 关联表）
 0 条 → 两者都有 / 都没有
```

---

## 8. 表间关系靠什么维系

**不靠 `__subTables__` 的 key 名**，靠两处：

1. **行内字段值**：`attachment.main_id` → main；`people` 通过行内嵌套挂在 participant 下
2. **表定义外键** `dw_foreign_keys`：`50330→50332`、`50331→50332`、`50334→50332`

因此改造 `__subTables__` 的 key 结构不会破坏表间关系。
