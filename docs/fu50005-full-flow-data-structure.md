# FU 50005「Multi-Instance Subtask Demo」全流程数据结构（实测）

> 所有内容从 `dw_process_definitions` BPMN、`dw_form_stage_bindings`、
> `up_process_instance.variables` 实际读取，非示意。
> 目的：把整个 flow 每个节点产生/读写哪些数据顺一遍，作为方案 C 改造的完整依据。

---

## 1. 流程结构（BPMN）

```
StartEvent_1 (Start)
   │
   ▼
Activity_0z1px4l  「submit」        userTask   ── 表单: Main (PROCESS, 50193)
   │
   ▼
Activity_0hwtl8v  「assignment」    userTask   ── 表单: Assign Task (TASK, 50191)
   │
   ▼
Activity_1m8yirt  「multi」         subProcess ◄── ★ 多实例(MI)，按 subtable 每行开一个实例
   │   ┌──────────────────────────────────────────────┐
   │   │ Event_03ygley (start)                        │
   │   │      ▼                                       │
   │   │ Activity_0j8mz1c 「sub form1」 userTask       │ ── 表单: Sub task (TASK, 50192)
   │   │      ▼                                       │
   │   │ Gateway_kk_subtask_route 「Route」 exclusive  │
   │   │      ▼                                       │
   │   │ Activity_134mqyl 「sub form2」 userTask       │ ── 表单: Sub task (同一张 50192)
   │   │      ▼                                       │
   │   │ Event_0baw3ob (end)                          │
   │   └──────────────────────────────────────────────┘
   ▼
EndEvent_1 (End)
```

### 1.1 MI 子流程配置（BPMN 扩展属性）

```xml
collection      = "multiInstance_subtable_collection"
elementVariable = "currentItem"
subTableId      = "50331"        ← Sub-Task Config 的 Sub-table ID
subTableName    = "subtable"
assigneeMode    = "both"         ← 支持 user 和 role 两种分派
assigneeField   = "assignee"
roleField       = "role_code"
buField         = "bu_code"
```

### 1.2 节点 → 表单绑定（`dw_form_stage_bindings`）

| 节点 | 表单(Task 场景) | 表单(My Request 场景) |
|---|---|---|
| `Activity_0z1px4l` submit | Main (50193, PROCESS) | Main (My Request) (50603) |
| `Activity_0hwtl8v` assignment | Assign Task (50191, TASK) | Assign Task (My Request) (50601) |
| `Activity_0j8mz1c` sub form1 | Sub task (50192, TASK) | Sub task (My Request) (50602) |
| `Activity_134mqyl` sub form2 | Sub task (**同一张** 50192) | Sub task (My Request) (50602) |

> **一个节点绑两张表单**：Task 场景（办理人看）+ My Request 场景（发起人看）。
> 这就是为什么每张表会有「双倍」的 binding。

---

## 2. 表定义与绑定全景

### 2.1 5 张表（`dw_table_definitions`）

| table_id | table_name | 类型 | 用途 |
|---|---|---|---|
| 50332 | `main` | MAIN | 主表（Meeting 本体） |
| 50331 | `subtable` | SUB | 参与者（页面显示 **Participants**）★ MI 集合表 |
| 50330 | `attachment` | SUB | 附件 |
| 50333 | `people` | SUB | People（挂在 participant 行内） |
| 50334 | `meeting_remark` | ACTION | 弹窗备注 |

### 2.2 每张表被哪些 binding 引用（这是多副本的根源）

| 表 | binding 数 | binding id（所属表单） |
|---|---|---|
| `main` (50332) | **7** | 50538(Assign Task)、50543(Sub task)、50549(Main)、50611/50616/50622(My Request 系列)、50633(meeting_Detail) |
| `subtable` (50331) | **6** | 50539(Assign Task)、50544(Sub task)、50627(Main)、50612/50617/50625(My Request 系列) |
| `attachment` (50330) | **6** | 50542、50548、50553、50615、50621、50626 |
| `meeting_remark` (50334) | **3** | 50629、50635、50637 |
| `people` (50333) | **2** | 50547(Sub task)、50620(Sub task My Request) |
| RT 关联表 | 31 | `rt:test`、`rt:sys_users(-1000000001)` 等 |

---

## 3. 全流程数据演进（每个节点读写什么）

### ■ 阶段 0 — 流程启动前

用户在 Portal 填「新建申请」表单 → 提交。

### ■ 阶段 1 — `Activity_0z1px4l`「submit」（表单 Main / 50193）

**产生的变量：**

```jsonc
{
  // 主表(50332)字段 —— 平铺在顶层，不进 __subTables__
  "id": "Meeting-000007", "I": "...", "approved": true,
  "itemCount": 0, "totalPrice": 0, "maxItemPrice": 0,
  "lookup": { ...用户对象... }, "fileupload": null,
  "created_at": "...", "created_by": "...",

  // 平台上下文
  "functionUnitId": "50005", "functionUnitCode": "fu-20260422-23tfag",
  "initiator": "user-dev", "currentUserId": "user-dev",
  "activeBusinessUnitId": "hase-hmdc",
  "__request_id": "3_Meeting-000007",

  // 该表单绑的子表 → 写进 __subTables__
  //   Main 表单绑了: 50549(main,PRIMARY) 50627(subtable) 50553(attachment)
  //                  50550(rt:test) 50551(rt:sys_users)
  "__subTables__": { "50627": [...], "50553": [], ... }
}
```

**完成时写快照：** `_snapshot_<taskId> = { taskId, taskDefinitionKey:"Activity_0z1px4l", assignee, completedAt, fieldValues }`

### ■ 阶段 2 — `Activity_0hwtl8v`「assignment」（表单 Assign Task / 50191）

用户在 Participants 表格里**增删行、指定每行的 assignee / role_code / bu_code**。

**写入的 key**（该表单的 binding）：

```
50538 PRIMARY dw:main         → 顶层字段
50539 SUB     dw:subtable     → __subTables__["50539"]      ★ 关键
50542 SUB     dw:attachment   → __subTables__["50542"]
50540 RELATED rt:sys_users    → __subTables__["50540"]
50541 RELATED rt:test         → __subTables__["50541"]
```

⚠️ **问题起点**：同一批 participant 行写进 `50539`，
但 Main 表单读的是 `50627`、Sub task 读的是 `50544` —— 三个 key 从此开始各存一份。

### ■ 阶段 3 — `Activity_1m8yirt`「multi」MI 子流程展开

引擎读 `subTableId=50331` 对应的行，为**每一行**开一个子流程实例。

**产生集合变量：**

```jsonc
"multiInstance_subtable_collection": [
  { "rowId": "Test-000005",
    "rowKey": { "id_idwvvbz": "Test-000005" },   // 按设计器 PK 构造
    "assignee": "user-dev" },                     // assigneeMode=both: 用户分派
  { "rowId": "Test-000006",
    "rowKey": { "id_idwvvbz": "Test-000006" },
    "role_code": "HMDC_Index_Role",               //            角色分派
    "bu_code": "hase-hmdc" }
]
```

**每个子实例的 execution 上：**

```jsonc
"currentItem": { "rowId":"Test-000006", "rowKey":{"id_idwvvbz":"Test-000006"}, ... }
```

> `currentItem` 是**execution 级**变量（每个参与者一份），
> 是「这个子任务负责哪一行」的唯一权威来源。
> **不能写进流程级 `variables`** —— 那样所有子任务会读到同一个值（曾导致此 bug）。

### ■ 阶段 4 — `Activity_0j8mz1c`「sub form1」（表单 Sub task / 50192）

每个参与者办理自己那一行。表单绑了 **7 张表**：

```
50543 PRIMARY dw:main            → 顶层
50544 SUB     dw:subtable        → __subTables__["50544"]   ★ 只写这个 key
50547 SUB     dw:people          → participant 行内嵌套 __subTables__
50548 SUB     dw:attachment      → __subTables__["50548"]
50635 ACTION  dw:meeting_remark  → __subTables__["50635"]
50545 RELATED rt:sys_users
50546 RELATED rt:test
```

**MI 进度镜像列**写回 participant 行：
`task_status = "IN_PROGRESS"`、`task_current_node = "sub form1"`

⚠️ **bug 现场**：用户改 Name / 删 Reviewer → 只更新了 `50544`；
`50539`(Assign Task 写的) 和 `50627`(Main 写的) 还是旧值 →
后端逐 key 处理时旧值胜出 → 「改了又变回去」。

### ■ 阶段 5 — `Gateway_kk_subtask_route`「Route」→ `Activity_134mqyl`「sub form2」

同一张表单 50192，`task_current_node` 更新为 `"sub form2"`。

### ■ 阶段 6 — 子流程结束 → `EndEvent_1`

所有参与者完成后，MI 子流程结束，流程走到 End。

---

## 4. 当前完整数据结构（25 个 key）

```jsonc
"__subTables__": {
  // ── 表 50331 subtable/Participants：9 个 key，内容已分叉 ──
  "50539": [2行],  "50627": [2行],  "Participants": [2行], "participants": [2行],
  "50544": [1行],  "subtable": [1行],                    // ⚠ 与上面不一致
  "50612": [], "50617": [], "50625": [],

  // ── 表 50330 attachment：8 个 key ──
  "50542":[], "50548":[], "50553":[], "50615":[], "50621":[], "50626":[],
  "Attachment":[], "attachment":[],

  // ── 表 50333 people：4 个 key ──
  "50547":[], "50620":[], "People":[], "people":[],

  // ── 表 50334 meeting_remark：6 个 key ──
  "50629":[], "50635":[], "50637":[],
  "Meeting Remark":[], "meeting remark":[], "meeting_remark":[],

  // ── RT binding：7 个 key ──
  "50540":[], "50541":[], "50545":[], "50546":[], "50550":[], "50551":[], "test":[]
}
```

---

## 5. 方案 C 改造后的完整结构（6 个 key）

```jsonc
{
  // ═══ 顶层：主表(50332)字段，不变 ═══
  "id": "Meeting-000007", "approved": true, "totalPrice": 0,
  "lookup": {...}, "created_at": "...", "updated_by": "...",
  "functionUnitId": "50005", "initiator": "user-dev",
  "__request_id": "3_Meeting-000007",

  // ═══ MI 集合变量：不变 ═══
  "multiInstance_subtable_collection": [
    { "rowId":"Test-000005", "rowKey":{"id_idwvvbz":"Test-000005"}, "assignee":"user-dev" },
    { "rowId":"Test-000006", "rowKey":{"id_idwvvbz":"Test-000006"},
      "role_code":"HMDC_Index_Role", "bu_code":"hase-hmdc" }
  ],

  // ═══ 历史节点快照：不变（每完成一个任务一个）═══
  "_snapshot_97aef754-...": { "taskId":"...", "taskDefinitionKey":"Activity_0z1px4l",
                              "assignee":"user-dev", "completedAt":"...", "fieldValues":{...} },
  "_snapshot_980072ed-...": { ... },

  // ═══ 子表：按 table_id 存一份 ═══
  "__subTables__": {

    "dw:50331": [                              // subtable / Participants
      { "id_idwvvbz":"Test-000005",            // 设计器主键
        "name":"dd", "main_id":"Meeting-000007",
        "assignee":"user-dev", "role_code":"", "bu_code":"",
        "reviewer":{ "id":"user-dev", ... },
        "task_status":"IN_PROGRESS", "task_current_node":"sub form1",
        "created_at":"...","created_by":"...","updated_at":"...","updated_by":"...",
        "__subTables__": { "dw:50333": [] }    // people 嵌套，同样 dw: 命名空间
      },
      { "id_idwvvbz":"Test-000006",
        "name":"FINAL-CHECK", "main_id":"Meeting-000007",
        "role_code":"HMDC_Index_Role", "bu_code":"hase-hmdc",
        "reviewer":null,                        // 清空 = null
        "task_status":"IN_PROGRESS", "task_current_node":"sub form1",
        "__subTables__": { "dw:50333": [] }
      }
    ],

    "dw:50330": [],          // attachment      (原 8 key)
    "dw:50333": [],          // people          (原 4 key)
    "dw:50334": [],          // meeting_remark  (原 6 key)

    "rt:1":           [],    // Relation Table: test
    "rt:-1000000001": []     // 虚拟表 sys_users
  }
}
```

### 各节点在新结构下的读写

| 节点 | 表单 | 写入 | 读取 |
|---|---|---|---|
| submit | Main | 顶层字段 + `dw:50331`/`dw:50330` | 同 |
| assignment | Assign Task | `dw:50331`（增删参与者行） | 同 |
| **MI 展开** | — | 读 `dw:50331` 生成 collection | — |
| sub form1 | Sub task | `dw:50331` 中**自己那一行** + `dw:50333` 嵌套 | 同 |
| sub form2 | Sub task | 同上，更新 `task_current_node` | 同 |

**所有节点读写同一个 `dw:50331`** → 不可能出现「Assign Task 写的和 Sub task 写的不一致」。

---

## 6. 关键不变量

| 项 | 说明 |
|---|---|
| 行内字段 | 一个字节不变（含 `main_id`、`id_idwvvbz` 等外键） |
| 表间关系 | 靠字段值 + `dw_foreign_keys`(50330→50332、50331→50332、50334→50332)，与 key 无关 |
| 主表字段 | 仍平铺在 `variables` 顶层 |
| MI 集合变量 | 结构不变 |
| `currentItem` | 仍是 execution 级，**绝不写入流程级 variables** |
| 历史快照 | `_snapshot_<taskId>` 独立，Process Flow 点历史节点仍正常 |
| RT 行数据 | 仍在 `rt_table_data_rows`，`__subTables__` 里的 `rt:` key 只是绑定占位 |
