# 列表 FILE 列：按文件名筛选（设计）

> **状态：已实现（2026-08-28）。** 方案 A：查询时 SQL 抽文件名；`Kind.FILE`，禁止当 TEXT 对裸 URL `ILIKE`。
>
> **与** [shared-list-components.md](./shared-list-components.md) **§6.3.2 的关系：**
> FILE 列按抽出的文件名筛选/排序；BYTEA 仍 display-only。抽名权威在
> `frontend/shared/src/list/fileNames.ts` 与 `ListFileNameSql`。
>
> **给后续 agent：**
> - **禁止**把 `FILE` 降成 `Kind.TEXT` 再对裸 JSON 做 `ILIKE`——用户看到的是文件名，库里是 URL。
> - 筛选用的「文件名」必须与 Portal 格子 / CSV 同一套抽取规则（见 §3）。
> - **本期一行 DW 不改**（与 shared-list 同约束），除非分期明确要改上传落库格式。

---

## 1. 背景与目标

Main Table View / Relation Table 列表格子上，`FILE` 列显示的是**文件名**（如 `invoice.pdf`），
来自前端 `extractFileLinks` / `fileDisplayText`（`frontend/shared/src/list/fileNames.ts`）。存储值常见是：

| 形态 | 示例 |
|------|------|
| 上传 URL 字符串 | `/api/v1/upload/files/336fd6f4.jpg?originalName=lilong.JPG` |
| 对象 | `{ "url": "…", "name": "a.pdf" }` |
| 多文件数组 | `[{url,name}, …]` 或 URL 字符串数组 |
| 空 | `null` / `""` / `[]` → 格子 `-` |

当前 `MainTableViewColumnSpec.kindOf` 对 `FILE` 返回 `Kind.FILE`，列头 Filter by 按抽出的文件名筛。

**成功标准：**

- ATM Attachment（或任意 `data_type=FILE` 的视图列）列头有 Filter by。
- 条件「文件名包含 `invoice`」只命中展示名含 `invoice` 的行，**不**因 URL 路径里偶然含该串而命中。
- 无文件的行：可用 No data / Has data（或等价 `isNull` / `isNotNull`）筛出。
- 多文件：任一文件名满足条件即命中（OR）。
- 与 CSV 导出、格子链接文案同一套文件名规则。

---

## 2. 非目标

- **不**改上传 API、不强制历史数据重写落库（MVP 在现有存储上抽名；规范化落库可作后续）。
- **不**按文件内容 / MIME / 大小筛。
- **不做**文件预览、批量下载筛选结果。
- **不**把幽灵列（视图字段名对不上表字段）写进本文——那是 View 配置问题，与 FILE 能力无关。
- **本期不改** `frontend/developer-workstation` / `backend/developer-workstation`（除非后续分期明确要统一上传写入 `name` 字段）。
- **不**为 FILE 开分组（`groupable = false`）：文件名基数大，分组无意义。

---

## 3. 文件名抽取契约（前后端必须一致）

与现有前端 `fileDisplayText` / `fileLinkFrom` 对齐，**权威顺序**：

1. 若值为对象且 `name`（非空字符串）→ 用 `name`。
2. 否则取 URL（字符串本身，或对象的 `url` / `fileUrl` / `path` / `downloadUrl`）。
3. 从 URL query 取第一个非空：`originalName` → `fileName` → `filename` → `name`；`decodeURIComponent`。
4. 否则取 URL path 最后一段（去 query/hash）再 decode。
5. 非上传引用（不匹配 `/(api/vN/)?upload/files/`）→ **不参与文件名筛**（该元素视为无有效文件名；整格若无任何有效链接则等同无文件）。

多值：对数组每个元素按上序抽名，得到文件名列表 `names[]`。

**筛选语义：**

| 算子 | 含义 |
|------|------|
| `contains` / `notContains` / `eq` / `ne` / `startsWith` / `endsWith` | 对 `names[]` 任一元素比较（大小写不敏感，与 TEXT 一致） |
| `isNull` | 无有效文件链接（空 / 非上传引用） |
| `isNotNull` | 至少有一个有效文件链接 |

`ne` / `notContains`：与现有封闭/文本惯例一致——「没有任何名字命中正条件」；空文件行是否算入 `ne` 须与 `ListFilterSql` 文本 `ne` 对 null 的处理对齐（实现时写单测钉死，禁止 silent）。

---

## 4. 方案权衡

### 方案 A — 查询时 SQL 抽文件名（推荐 MVP）

新增列 kind（见 §5）：`FILE` 可筛、可按文件名排序（可选同期或二期），`groupable=false`。

`ListFilterSql`（或 MTV/RT 专用扩展）对 FILE 列不用裸 `col->>'file'`，而用与 §3 等价的
**PostgreSQL 表达式**得到「可比文件名」（单文件 URL 字符串路径优先落地；对象 / 数组用
`jsonb` 分支）。

| 优点 | 缺点 |
|------|------|
| 不改存量数据、不改上传写路径 | SQL 表达式复杂；对象/多文件要仔细测 |
| 与当前格子行为可对齐 | 缺 `originalName` 时只能比 path 末段（常为 uuid），与用户记忆的原名可能不一致——但格子也是同一规则，诚实 |
| 爆炸半径主要在 portal 列表 SQL + 共享弹窗 | |

### 方案 B — 规范落库：始终存 `{url, name}` + 可选旁路列

上传成功时强制写入结构化对象（或并列 `file` + `file__name`）。筛 `name` / `file__name`。

| 优点 | 缺点 |
|------|------|
| SQL 简单、索引友好 | 须改上传写路径 + **存量回填**；触达表单/子表，易越出「仅列表」 |
| 长期干净 | DW / 多写点；与「本期不改 DW」冲突大 |

### 方案 C — 把 FILE 当 TEXT（禁止）

对存储字符串 `ILIKE`。

| 优点 | 缺点 |
|------|------|
| 一行改动 | **不诚实**：比到 path/query；uuid 段误命中；与格子文件名不一致 |

**推荐：方案 A（MVP）。** 方案 B 作为后续「存储规范化」分期，不阻塞按文件名筛上线。
**禁止方案 C。**

---

## 5. Kind 与 UI 契约

| 项 | 决定 |
|----|------|
| `PortalListColumnMeta.Kind` | 新增 **`FILE`**（不要复用 TEXT） |
| `filterable` | `true` |
| `sortable` | MVP：**按抽出的文件名**字典序 ASC/DESC；菜单文案可用 A–Z（与 TEXT 同文案键，或单独 `sortByFileName`——实现时二选一并写进 i18n） |
| `groupable` | `false` |
| 算子 | 与 TEXT 开放值算子相同：`contains` / `notContains` / `eq` / `ne` / `startsWith` / `endsWith` / `isNull` / `isNotNull` |
| 弹窗 | 复用 `ListFilterDialog` 文本输入（无 options、非 USER、非 date picker） |
| `kindOf("FILE")` | 返回 `Kind.FILE`，不再 `null` → displayOnly |

Relation Table 若也有 `FILE` 类型字段，**同一套** kind / 算子 / 抽名（`RelationTableColumnSpec` 同步）。

---

## 6. 影响面（实现时）

| 层级 | 变更 |
|------|------|
| `PortalListColumnMeta` | 增 `Kind.FILE`；`operatorsFor` / 工厂方法；构造校验 |
| `MainTableViewColumnSpec` / `RelationTableColumnSpec` | `kindOf` 识别 FILE；columnRef 或 filter 编译走文件名表达式 |
| `ListFilterSql`（或旁路） | FILE 谓词 +（可选）sortExpression |
| 前端 `columnMeta` / `ListFilterDialog` / `listHeaderMenu` | 识别 FILE；算子 i18n 可复用 TEXT |
| 单测 | 抽名与 SQL 用例：有/无 originalName、对象 name、多文件 OR、空文件 isNull |
| 设计交叉 | 合入后改 shared-list §6.3.2 一句；本文状态改「已定稿 / 已实现」 |
| DW | MVP **零 diff** |

---

## 7. 分期

| 期 | 内容 |
|----|------|
| **MVP** | 方案 A；单文件 URL 字符串 + 常见 `{url,name}`；多文件数组 OR；算子上表；ATM Attachment 截图验收；DW 不改 |
| **MVP+** | 排序按文件名；Relation Tables 对齐（若尚未同路径） |
| **后续** | 方案 B 落库规范化 + 存量回填；上传写点统一只写结构化对象 |

---

## 8. 风险与诚实边界

| 风险 | 处理 |
|------|------|
| 历史数据只有 uuid path、无 `originalName` | 筛的是 path 末段——与格子一致；产品文案可说明「按界面显示的文件名」 |
| SQL 与前端抽名漂移 | 单测用同一批 fixture；注释标明「mirrors fileDisplayText」不够——要有共享用例表或后端单测字符串与前端 vitest 同源样例 |
| 表达式性能 | FILE 列筛选少；不做函数索引除非慢查询日志证明需要 |
| 非上传杂串写在 FILE 列 | 视为无有效文件 → isNull 路径；不按 TEXT 乱比 |

---

## 9. 验收

**反例**

- 存储 `…/upload/files/abc123?originalName=report.pdf`，筛 Contains `abc123` → **不命中**（除非显示名也含）。
- 把 FILE 当 TEXT 对整 URL `ILIKE` → **不允许合入**。

**正例**

- 同上，筛 Contains `report`（或 `report.pdf`）→ 命中。
- `{url:"…/x", name:"合同.pdf"}`，筛 Contains `合同` → 命中。
- 两文件 `a.pdf` + `b.pdf`，筛 Contains `b` → 命中。
- 空 FILE，筛 No data / isNull → 命中；Has data → 不命中。

**FU / 视图：** ATM Attachment（`atm-20260623-gaevus` / view 含 `file`）或等价 FILE 列。

---

## 10. 验证计划（实现阶段）

- `mvn -pl backend/user-portal -am test`（ColumnMeta / ColumnSpec / ListFilterSql FILE 用例）
- 前端 shared list + portal vitest（kind FILE、弹窗文本分支）
- `pnpm run build`（portal；若动 shared 则相关 app）
- 重建 `user-portal`（+ 必要时 frontend）
- Playwright 截图：`verification-screenshots/YYYY-MM-DD_list-filter-file-by-name.png`

---

## 11. 待确认

1. **排序是否进 MVP**，还是仅筛选？
2. **多文件 `ne`**：是否「所有文件名都不等于 X」且空行算入（对齐 TEXT `ne`）？
3. 范围是否包含 **Relation Tables** 与已接入 shared-list 的其它菜单，还是先 **Main Table Views only**？

确认本文后回复 **确认** / **按 playbook 执行**（并附上待确认项选择）再改代码。

MVP 已按方案 A 落地：筛选 + 按抽出文件名排序；Relation Tables 与 Views 同一套 `Kind.FILE`。
`%` / `_` 在列表 ILIKE 中为字面量（`ESCAPE '\'`）。
