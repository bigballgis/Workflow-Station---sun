# `__subTables__` 单一真相源改造 — 实施计划

> 目标：消除「同一行存成多份别名副本、副本之间可能不一致」这一结构性缺陷。
> 背景：MI 子任务「改了值 Save 后又变回旧值」查了七轮，根因不是某条代码路径写错，
> 而是数据结构本身没有单一真相源。

---

## 1. 问题事实（均为实测，非推断）

### 1.1 一张表存了 6 份副本

FU 50005 的一个流程实例，`__subTables__` 有 **25 个 key**。其中表
`dw_table_definitions.id = 50331`（`table_name = subtable`，页面显示 **Participants**）
占了 6 个：

| key | 来源 |
|---|---|
| `50539` | binding — Assign Task 表单 |
| `50544` | binding — Sub task 表单 |
| `50627` | binding — Main 表单 |
| `50612` / `50617` / `50625` | binding — My Request 系列表单 |
| `subtable` | `table_name` 别名 |
| `Participants` / `participants` | 显示名 / 归一化别名 |

### 1.2 副本会不一致（真实状态，不是理论风险）

```
50539 = 2 行      50627 = 2 行      Participants = 2 行
50544 = 1 行      subtable = 1 行   ← 行数都对不上
```

### 1.3 失败机制

- **前端**：不同写入路径（previousForms 摄入 / 当前表单摄入 / `syncMainSubTableRows`）
  各写不同的 key 子集 → 同一行出现两个版本
- **后端**：`MiSubTaskSubTableRowMerger` 用
  `for (entry : submittedSubTables.entrySet())` **逐 key 独立处理**
  → 哪个 key 的版本最终生效，取决于遍历顺序

实测后端日志（用户改 Name 为 `FINAL-CHECKv`、删除 Reviewer）：

```
key=50627        name=FINAL-CHECK    reviewer={Developer Tester}   ← 旧
key=50539        name=FINAL-CHECK    reviewer={Developer Tester}   ← 旧
key=Participants name=FINAL-CHECKv   reviewer=null                 ← 用户的修改
key=participants name=FINAL-CHECKv   reviewer=null                 ← 用户的修改
key=subtable     name=FINAL-CHECKv   reviewer=null                 ← 用户的修改
key=50544        name=FINAL-CHECKv   reviewer=null                 ← 用户的修改
```

用 API 直接提交矛盾 payload 可稳定复现：**旧值赢**。

> **结论：这不是实现 bug，是数据模型缺陷。** 修单条路径必然打地鼠 —— 前后修了
> binding key、myRow、删除条件、merge 方向四处，每次都还有别的路径漏 key。

---

## 2. 目标结构

### 2.1 Key 规则（基于实测的干净二分）

`dw_form_table_bindings` 共 101 条：

| 分类 | 数量 | 特征 |
|---|---|---|
| DW 子表 | **70** | 只有 `table_id` |
| Relation Table | **31** | 只有 `relation_table_id` |
| 两者都有 | **0** | 无歧义 |
| 两者都无 | **0** | 无孤儿 |

因此 key 规则完全确定：

```ts
function subTableStoreKey(binding): string {
  if (binding.tableId != null)         return `dw:${binding.tableId}`
  if (binding.relationTableId != null) return `rt:${binding.relationTableId}`
  throw new Error('binding has neither tableId nor relationTableId')  // 实测不存在
}
```

> 用 `dw:` / `rt:` 前缀而非裸数字，避免与现有的裸 binding id key 混淆，
> 也让新旧结构在同一个 map 里可以共存（过渡期需要）。

### 2.2 目标结构（最终版，用 FU 50005 真实数据写出）

> ⚠️ 注意：现结构里的数字 key **是 `binding_id`，不是 `table_id`**。
> 实测 `table_id`(50331) 作为 key 出现 **0 次** —— 现在根本没有按表存。

#### 改造前（现状，25 个 key）

```jsonc
"__subTables__": {
  // 表 50331 (subtable) 一张表 = 9 个 key，且内容不一致
  "50539": [2行],  "50627": [2行],  "Participants": [2行], "participants": [2行],
  "50544": [1行],  "subtable": [1行],           // ⚠ 与上面分叉
  "50612": [], "50617": [], "50625": [],

  // 表 50330 (attachment) = 8 个 key
  "50542":[], "50548":[], "50553":[], "50615":[], "50621":[], "50626":[],
  "Attachment":[], "attachment":[],

  // 表 50333 (people) = 4 个 key
  "50547":[], "50620":[], "People":[], "people":[],

  // 表 50334 (meeting_remark) = 6 个 key
  "50629":[], "50635":[], "50637":[],
  "Meeting Remark":[], "meeting remark":[], "meeting_remark":[],

  // RT binding = 7 个 key
  "50540":[], "50541":[], "50545":[], "50546":[], "50550":[], "50551":[], "test":[]
}
```

**为什么会有 6 份**：表 50331 被 6 张表单各绑一次 →
`50539`(Assign Task)、`50544`(Sub task)、`50612`(Assign Task My Request)、
`50617`(Sub task My Request)、`50625`(Main My Request)、`50627`(Main)。
binding = 「某张表单里的某张表」，不是数据身份，所以按它存必然多份。

#### 改造后（目标，5 个 key）

```jsonc
"__subTables__": {

  // ═══ 表 50331 (subtable / 页面显示 Participants) ═══
  //     唯一数据源。6 个 binding、3 个别名全部取消。
  "dw:50331": [
    { "id_idwvvbz": "Test-000005",           // 设计器主键(PK)
      "name":       "dd",
      "main_id":    "Meeting-000007",        // 外键 → dw:50332
      "assignee":   "user-dev",
      "role_code":  "",
      "bu_code":    "",
      "reviewer":   { "id":"user-dev", "username":"developer", ... },
      "task_status":       "IN_PROGRESS",    // MI 镜像列(Sub-Task Config 可配名)
      "task_current_node": "sub form1",
      "created_at":"...", "created_by":"...",
      "updated_at":"...", "updated_by":"...",
      "__subTables__": { "dw:50333": [] }    // 行内嵌套同样用 dw: 命名空间
    },
    { "id_idwvvbz": "Test-000006",
      "name":       "FINAL-CHECK",
      "main_id":    "Meeting-000007",
      "role_code":  "HMDC_Index_Role",
      "bu_code":    "hase-hmdc",
      "reviewer":   null,                    // 清空 = null
      "task_status":"IN_PROGRESS",
      "__subTables__": { "dw:50333": [] }
    }
  ],

  // ═══ 其余 DW 表：各一个 key ═══
  "dw:50330": [],        // attachment       (原 8 个 key)
  "dw:50333": [],        // people           (原 4 个 key)
  "dw:50334": [],        // meeting_remark   (原 6 个 key)

  // ═══ RT 关联表：rt: 命名空间 ═══
  //     (31/101 个 binding 没有 table_id，只有 relation_table_id)
  "rt:1":            [],  // test
  "rt:-1000000001":  []   // 虚拟表 sys_users
}
```

**25 个 key → 6 个 key。同一批行只存一份。**

#### 去掉的东西

| 去掉 | 原因 |
|---|---|
| binding id key（`50539`/`50544`/`50627`…） | binding 不是数据身份；一表 6 binding → 6 份副本 |
| table_name 别名（`subtable`） | 重复存储 |
| 显示名/归一化别名（`Participants`/`participants`） | 重复存储 |
| **按名字兜底查找** | 兜底只在「按 binding 存导致某些 binding 没数据」时才需要；按表存一份后该场景不存在 |

#### binding 如何取数据（不再存，只解析）

```ts
// binding 50544 (Sub task) 要数据
//   → binding.table_id = 50331
//   → 读 __subTables__["dw:50331"]
// binding 50627 (Main) 要数据
//   → binding.table_id = 50331
//   → 读 __subTables__["dw:50331"]        ← 同一份，永不分叉

function storeKey(binding) {
  if (binding.tableId != null)         return `dw:${binding.tableId}`
  if (binding.relationTableId != null) return `rt:${binding.relationTableId}`
  throw new Error('binding has neither')   // 实测 0 条
}
```

#### 不变的部分

- 行内字段（含 `main_id` / `id_idwvvbz` 等外键）**一个字节不变** → 表间关系不受影响
- 主表(MAIN)字段仍平铺在 `variables` 顶层
- `_snapshot_<taskId>` 历史快照独立，不受影响
- RT 行数据仍在 `rt_table_data_rows`

---

## 3. 关键决策：别名投影只在内存，不落库

**JSON 序列化会断开共享引用。** 前端内存里让别名指向同一数组，落库后仍是多份独立副本，
下次读回来又会各自漂移。

因此：

| 阶段 | 做法 |
|---|---|
| 前端内存 | 可生成别名投影（同一引用），方便现有 52 处读取点无感 |
| **提交给后端** | **只发 `dw:` / `rt:` 命名空间，不发别名** |
| 后端存储 | 原样存，**不展开别名** |
| 后端读取 | 按 `binding.table_id` / `relation_table_id` 解析 |
| 返回给前端渲染 | 可再生成别名投影 |

这样「多副本不一致」从结构上不可能发生。

---

## 4. 改造范围（实测）

| 位置 | 数量 | 说明 |
|---|---|---|
| 前端「写 4 个别名 key」扇出 | **14 处 / 7 文件** | 核心改造点 |
| 前端 `getSavedSubTableRows` 调用 | 52 处 | 走 `resolveSubTableRowsForBinding` 单一出口，改出口即可 |
| 前端 `__subTables__` 直接引用 | 236 处 | 多数是读，需逐一确认无绕过写入 |
| 后端 `__subTables__` 引用 | 190 处 / **49 类** | 不止 MI：邮件抽取、计算字段、变更历史、主表视图、已完成快照 |
| 行内嵌套 `__subTables__` | 4 实例 | link-form 子表（participant → People），结构同构，需递归 |

### 4.1 前端 14 处扇出的精确位置

```
src/composables/processStart/useProcessStartSubTables.ts:82
src/composables/taskDetail/useTaskDetailMiIsolation.ts:276
src/composables/taskDetail/useTaskDetailMiLinkChild.ts:90
src/composables/taskDetail/useTaskDetailMiPersist.ts:192, 230
src/composables/taskDetail/useTaskDetailMiResync.ts:243, 295
src/composables/taskDetail/useTaskDetailSubTableSync.ts:169, 295, 313, 372, 377
src/composables/tasks/useTaskForm.ts:43, 49
```

---

## 5. 分步实施（每步独立可验证、可回滚）

### 步骤 1 — 新增 store 抽象（纯新增，零行为变化）

新建 `frontend/user-portal/src/composables/tasks/subTableStore.ts`：

```ts
export function subTableStoreKey(binding): string          // dw:<id> | rt:<id>
export function writeSubTableRows(store, binding, rows)    // 只写规范 key
export function readSubTableRows(store, binding)           // 规范 key 优先，回退旧别名
export function projectAliasesForRender(store, bindings)   // 内存投影（递归嵌套）
export function stripAliasesForSubmit(store, bindings)     // 提交前只留规范 key
```

**验证**：单测覆盖 key 规则（含 RT、虚拟表 `-1000000001`、嵌套递归）。此步不接线，
主流程零影响。

### 步骤 2 — 前端读取兼容（先读后写，保证可回滚）

`resolveSubTableRowsForBinding` 增加：**先查规范 key，查不到再走现有别名逻辑**。

**验证**：全量前端测试；此时结构未变，行为应完全一致。

### 步骤 3 — 前端写入收敛（14 处 → 1 处）

7 个文件的扇出全部换成 `writeSubTableRows`；提交前调用 `stripAliasesForSubmit`。

**验证**：
- 单测：喂矛盾 payload，断言输出只有规范 key 且内容一致
- 真实环境：MI 改值 / 清空字段 / 删除 Reviewer，后端日志逐 key 核对
- `pnpm run regression:mi` + 截图验证（记忆规则要求）

### 步骤 4 — 后端读取兼容

统一解析入口：按 `binding.table_id` / `relation_table_id` 找 `dw:` / `rt:` key，
找不到再回退旧别名。**49 个类多数不用逐个改**（都经由同一 resolver）。

**验证**：三后端套件全绿（engine / portal / admin），对比既有基线。

### 步骤 5 — 后端停止展开别名

`MiSubTaskSubTableRowMerger` 等不再逐 key 处理，只认规范 key。

**验证**：MI 全链路真实环境验证 + 全量测试。

### 步骤 6 — 清理

移除过渡期的旧别名回退分支。

---

## 6. 已排除的顾虑（实测证据）

| 顾虑 | 结论 | 证据 |
|---|---|---|
| 表间关系丢失 | **不受影响** | 关系在字段值（`main_id`、`id_idwvvbz`）+ `dw_foreign_keys`，与 key 名无关 |
| 历史节点回看 | **不受影响** | 走 `_snapshot_<taskId>` 独立变量，与 `__subTables__` 无关 |
| `__subTable_<id>`（单数）冲突 | **不是存储** | `LAYOUT_ONLY_FIELD_KEY_PREFIXES` 布局占位符，值恒 `null`，后端零引用 |
| FU 设计 / 导入导出 / 版本回滚 | **不受影响** | `sys_function_unit_contents`、`dw_versions` 中 `__subTables__` 计数为 **0** |
| 同 FU 内表名重复 | **无** | `table_id` 天然唯一 |
| 存量数据迁移 | **不需要** | 用户确认所有 case 用新建数据 |

---

## 7. 仍存在的风险

| # | 风险 | 应对 |
|---|---|---|
| 1 | **前后端须同步上线**：结构变了，旧前端 + 新后端不兼容 | 步骤 2/4 先做「读兼容」，让新旧结构共存，降低同步压力 |
| 2 | **236 处前端引用中可能有绕过 store 的直接写** | 步骤 3 前先全量排查；写入后加运行时断言（dev 环境）捕获漏网 |
| 3 | **触碰 MI 热路径**（记忆中标注反复出问题） | 每步跑 `regression:mi` + 截图验证；分步提交便于二分定位 |
| 4 | **后端 49 个类涉及非 MI 功能**（邮件、计算字段、变更历史等） | 步骤 4 走统一 resolver，避免逐类改；三后端套件对比既有基线 |
| 5 | 嵌套层递归遗漏 | store 层统一递归，单测覆盖嵌套结构 |

---

## 8. 建议

- 按步骤 1→6 顺序推进，**每步独立提交并跑全量测试**，任何一步出问题可单独回滚
- 步骤 1、2 风险极低（纯新增 + 只读兼容），可先落地建立信心
- 步骤 3 是收益拐点：完成后「多副本不一致」在前端侧即消失
- 步骤 5 完成后结构性根治
