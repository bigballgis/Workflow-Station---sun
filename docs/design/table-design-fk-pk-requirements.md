# Table Design 外键 / 主键规则 — 产品需求规格（PRD）

| 属性 | 值 |
|---|---|
| 状态 | 已定稿（待开发） |
| 版本 | 1.0 |
| 日期 | 2026-05-28 |
| 适用范围 | Developer Workstation、User Portal、Admin Center Relation Table |

---

## 1. 背景与目标

### 1.1 问题

- Table Design 字段网格缺少**外键列**配置；表关系弹窗（`dw_table_relations`）与结构外键（`dw_foreign_keys`）、表单绑定（`foreign_key_field`）三套概念未打通。
- Form Preview / User Portal 新增子表行时，**不会**根据主外键关系自动填充；主键无生成规则（仅手填或 UUID fallback）。
- Admin Center Relation Table 字段列表缺少统一的 Display Name 体验，且无与 DW 对齐的 PK/FK 能力。

### 1.2 目标

1. 在 **DW Table Design** 与 **AC Relation Table 结构** 中支持字段级 FK/PK 元数据。
2. **Form Preview（DW）** 与 **User Portal** 运行时行为一致：自动填 FK、后端分配 PK、父行未就绪则阻断。
3. 外键值**用户不可修改**；设计师控制 FK 字段 **readonly（默认）/ hidden**。
4. 统一字段显示名为 **`displayName`**；Field Name 由 Display Name **预填且可编辑**。
5. **MI** 等特殊子表模式与结构 FK **分流**，不破坏 MCY 等现有 Function Unit。

### 1.3 不在范围

- 按业务表名动态 `CREATE TABLE`（行数据仍为 JSON/JSONB，见 `.cursor/rules/json-row-storage-no-physical-tables.mdc`）。
- AC 外键引用 Developer Workstation 表（仅 AC 内已部署 Relation Table）。
- 多对多中间表自动生成（本需求仅覆盖字段级 FK 与运行时填充）。

---

## 2. 术语

| 术语 | 说明 |
|---|---|
| **结构 FK** | 字段元数据中 `isForeignKey=true`，指向另一张表的 PK |
| **FK 元数据** | Source of Truth：DW `dw_foreign_keys` + 字段扩展；AC `rt_field_definitions` 等价扩展 |
| **`foreign_key_field`** | 表单绑定上的历史字段；**由 FK 元数据派生**，不再作为手工配置源 |
| **`bindingLinkMode`** | 绑定级语义，与结构 FK 分离（如 MI 参与者行关联） |
| **`displayName`** | 字段显示名（API 统一字段名，替代 DW `description`、AC 作显示名的 `comment`） |
| **同级子表** | 同一表单下平行存在的多个 SUB binding；彼此**不发生**结构 FK 关系（若业务需要关联，须在 Table Design 中提升为新一层主从） |

---

## 3. 架构原则

### 3.1 三层关联模型

```
结构层（Source of Truth）
  Field FK 元数据 + dw_foreign_keys / AC 等价表
       ↓
表单层
  bindingLinkMode（MI 等）+ 派生的 foreign_key_field（只读）
       ↓
运行时层（Preview / Portal / AC 数据页）
  共享 TS/Java 库：encode PK、resolve FK、guard 父行、allocate PK
```

**冲突处理：** 结构 FK **以字段 FK 元数据为准**；MI 场景走 **`bindingLinkMode = miParticipantRow`**，不走结构 FK 的「填主表 PK」逻辑。

### 3.2 Portal / Preview Parity

Form Preview 弹窗与 User Portal 运行时 **必须** 使用同一套填充/阻断/编码函数（见 `.cursor/rules/portal-design-parity.mdc`）。

### 3.3 存储

- DW / Portal 流程变量 / AC：`Map<String, Object>` JSON 行，**无** per-table 物理业务表。
- PK 自增 / 前缀序号 **必须后端分配**，不可仅依赖前端或 DB SERIAL。

---

## 4. 模块 0：Display Name 与 Field Name

### 4.1 统一 `displayName`

| 模块 | 变更 |
|---|---|
| API / DTO / Entity | 字段显示名统一为 **`displayName`** |
| Developer Workstation | 原 `description`（字段显示名）→ **`displayName`** |
| Admin Center | 原用作显示名的 `comment` → **`displayName`**；若需备注可另增 `description`（可选） |
| 前端 | 列标题 i18n：Display Name / 显示名称 |

过渡期可读旧字段别名；**写入只使用 `displayName`**。

### 4.2 Field Name 生成与编辑

| 场景 | 行为 |
|---|---|
| 新建字段 | 输入 `displayName` 后 **实时预填** `fieldName`（slug 规则） |
| Field Name 输入框 | **可编辑**；非只读 |
| Display Name 变更 | 若 `fieldName` 仍为「上次自动生成值」且用户**未手动改过** → 继续跟随更新 |
| 用户手动编辑过 `fieldName` | 之后 Display Name 变更 **不再自动覆盖** `fieldName` |
| 已保存字段 | 已有绑定/数据的字段，Display Name 变更 **永不强制** 改 `fieldName` |
| 保存校验 | `fieldName` 须符合命名规则（字母开头，字母数字下划线）；同表内不可重名 |

**Slug 规则：** 去首尾空格 → 非字母数字转 `_` → 合并连续 `_` → 小写 → 若非字母开头则前缀 `f_` → 重名后缀 `_2`, `_3`…

**可选 UI：** 「根据 Display Name 重新生成」按钮（不覆盖已 `touched` 的 Field Name）。

### 4.3 Admin Center 字段列表

- 字段定义表格增加 **Display Name** 列（绑定 `displayName`）。
- Field Name 列：预填 + 可编辑（规则同 4.2）。

### 4.4 Developer Workstation 字段列表

- 现有「显示名称」列对齐为 **`displayName`**。
- Field Name：预填 + 可编辑（规则同 4.2）。

---

## 5. 模块 A：字段 FK / PK 元数据

### 5.1 外键字段定义

每个字段可配置：

```typescript
interface FieldFkMeta {
  isForeignKey: boolean
  refTableId: number              // DW: 同一 Function Unit 内表；AC: 已部署 Relation Table
  refPrimaryKeyFields: string[]     // 引用表 PK 列名（支持联合 PK）
}

interface FieldUiMeta {
  /** 仅外键字段 */
  fkDisplayMode: 'readonly' | 'hidden'  // 默认 readonly
}
```

**保存校验：**

- FK 不可引用自身表。
- `refPrimaryKeyFields` 必须是目标表已标记 PK 的列（联合 PK 须列全）。
- FK 字段类型/长度须能容纳 **合成 PK 字符串**（见 5.3）。
- 保存时同步 `dw_foreign_keys`（DW）/ AC 等价 FK 表。

**AC 引用范围：** `refTableId` **仅允许** Admin Center 内 **已部署（deployed）** 的 Relation Table；未部署表、DW 表均不可选，保存校验拒绝。

**DW 引用范围：** 同一 Function Unit 内的 `dw_table_definitions`。

### 5.2 主键生成规则 `pkGeneration`

配置在主键字段上：

| 策略 | 说明 |
|---|---|
| `manual` | 用户手填（默认，兼容现有） |
| `uuid` | 后端生成 UUID |
| `autoIncrement` | 后端递增整数 |
| `prefixedSequence` | `{prefix}{zeroPaddedSeq}`，如 `CASE-000001` |

**配置项（`prefixedSequence` / `autoIncrement`）：**

- 作用域：`perTable`（默认）/ `perFunctionUnit` / `perPrefix`
- 起始值、位数、前缀字符串

**后端 API（DW / AC 共用模式）：**

```
POST .../primary-keys/allocate
Body: { tableId, fieldName, count?: 1 }
Response: { values: ["CASE-000042", ...] }
```

- 实现：DB 序号表或 Redis 计数；**保证并发不重复**。
- 调用时机：新增行保存前或打开新增对话框前（由实现统一）；**不在前端自增**。

### 5.3 联合主键 → 外键字段编码

联合 PK 写入**单个 FK 字段**，格式与平台 `rowKey` / `SubTableRowKeySupport` 对齐：

```
单列 PK:  "42"
联合 PK:  "line_no=3\u001ftenant_id=ACME"
           // 字段名按字母序排序，key=value，分隔符 U+001F
```

- 解析/比较复用 Portal 已有 `canonicalRowKeyFromPayload` 语义。
- FK 字段 `VARCHAR` 的 `length` 须 ≥ 合成串最大长度（保存时校验）。

### 5.4 「数据模型关联配置」弹窗 — 双向同步

**现状（截图所示）：** Table Designer 工具栏的 **Relations / Data Model Relation Config** 弹窗（对应实体 `dw_table_relations`）由设计师**手工**填「源表 + 源字段 + 关系类型 + 目标表 + 目标字段」；与字段级 FK 元数据是两套独立配置。MCY 中 `HMDC_Case.case_number → HMDC_Transaction.row_id` 把 MI 参与者列误标为外键，即源于此。

**新规则：** 弹窗与字段 FK 元数据 **双向同步**，**字段 FK 元数据是 Source of Truth**；弹窗保留新增/编辑/删除入口，所有写操作**最终落地到字段 FK 元数据**。

#### 5.4.1 派生展示（读路径）

打开弹窗时，逐表扫描字段 FK 元数据，生成关系行：

| 列 | 取值 |
|---|---|
| 源表 | FK 所在字段的 `tableId` |
| 源字段 | FK 字段 `fieldName`（联合 PK FK 仍为单字段，按 5.3 合成存储） |
| 目标表 | `refTableId` |
| 目标字段 | `refPrimaryKeyFields`（联合 PK 显示为 `(a, b, c)`） |
| 关系类型 | 默认按推断（见 5.4.3），可手动覆盖并持久化 |

#### 5.4.2 手动新增 / 编辑 / 删除（写路径）

| 操作 | 同步到字段 FK 元数据 |
|---|---|
| **新增**「源表.源字段 → 目标表.目标字段」 | 源字段 `isForeignKey = true`、`refTableId = 目标表`、`refPrimaryKeyFields = [目标字段...]`；若源字段尚未存在，**禁止**保存并提示「请先在字段定义里创建该字段」 |
| **编辑** 已有关系（改目标表/字段/类型） | 更新源字段的 FK 元数据；保存校验同 5.1 |
| **删除** 关系 | 源字段 `isForeignKey = false`，清空 `refTableId / refPrimaryKeyFields`，并清空 `fkDisplayMode` |
| **关系类型**（One-to-One / One-to-Many / Many-to-Many） | 持久化到字段元数据的 `relationCardinality?: 'oneToOne' \| 'oneToMany' \| 'manyToMany'`（默认未设置则按 5.4.3 推断） |

写操作完成后：

1. **字段定义页同步刷新**：源字段表格里立即看到 `isForeignKey` 勾选、`refTable / refField` 等列被填上；联合 PK 自动写入 `refPrimaryKeyFields`。
2. **保存校验**与字段编辑入口完全一致：
   - 不可引用自身表
   - 不可指向同级 SUB（见 7.3）
   - 引用列必须是目标表已标 PK 的列
   - AC 仅允许已部署 RT
3. 任一校验失败 → 弹窗内 inline 错误，**不写**字段元数据。

#### 5.4.3 关系类型推断（默认）

未显式设置 `relationCardinality` 时按以下规则展示：

- 目标 PK 字段同时具备 `UNIQUE` 约束 / 单行 PRIMARY MAIN 表 → `ONE_TO_ONE`
- 否则 → `ONE_TO_MANY`
- **关联表（中间 SUB 表带两条 FK，分别指向两张主表）** → 两张主表之间显示 `MANY_TO_MANY`（Phase 2 可选）

设计师在弹窗手动改类型 → 写入 `relationCardinality` 覆盖推断值。

#### 5.4.4 UI 要求

1. 弹窗顶部说明：「关系与字段外键设置双向同步。在此处的任何修改将同步到对应字段。」
2. 「源字段」「目标字段」下拉只列**当前表已有字段**；联合 PK 用多选。
3. 删除关系前确认提示：「删除后将清除 {源表.源字段} 的外键设置，确定继续？」
4. 字段定义页编辑 FK 后，弹窗内容**实时同步**（建议同一 store / 组件层订阅）。

#### 5.4.5 历史数据迁移

升级时扫描 `dw_table_relations`：

| 情况 | 处理 |
|---|---|
| 与字段 FK 元数据一致 | 保留；视为派生 |
| 字段未标 FK 但 relations 表有记录 | **以 relations 为准**，回填字段 FK 元数据；同时校验目标列是 PK |
| 目标列不是 PK（如 MCY 的 `→ row_id` 实际为 MI 参与者关联） | **不**回填为 FK；标记为待人工确认，建议改为 `bindingLinkMode = miParticipantRow` 后清除该 relations 记录 |
| 两边都没有 | 无操作 |

迁移完成后，运行时与 UI 仅依赖字段 FK 元数据；`dw_table_relations` 仅保留 `relationCardinality` 持久化用途（或合并进字段元数据，二选一由实现决定）。

**AC 同步：** Admin Center Relation Table 若提供类似关系总览/编辑页，按同规则双向同步到字段 FK 元数据。

---

## 6. 模块 B：外键字段 UI 行为

| 场景 | 行为 |
|---|---|
| Preview / Portal / AC 数据页 | FK 值由系统写入；**用户不可编辑**（disabled，且不接受用户覆盖提交） |
| 导入表单设计器后 | FK 字段默认 **`fkDisplayMode: readonly`** |
| 设计师配置 | 每 FK 字段：`readonly` \| `hidden` |
| `hidden` | 页面不渲染；**保存/提交 JSON 仍包含 FK 值** |
| 非 FK 字段 | 不适用 `fkDisplayMode` |

---

## 7. 模块 C：外键自动填充

### 7.1 总则

- **凡存在结构 FK 关系，新增行时自动填充**（不限 MAIN→SUB）。
- 填充源为**被引用表在当前上下文中的已存在行**的 PK（联合 PK 按 5.3 合成）。
- 任一 FK 无法解析父行 → **阻断新增**，弹框提示（中英文 i18n）。

### 7.2 主表（PRIMARY）未就绪

若子表 FK 链依赖 MAIN 的 PK，而 MAIN **尚无 PK / 未保存**：

- **禁止**新增该子表行。
- 提示：「请先创建 {主表 displayName} 记录，再添加 {当前表 displayName} 数据。」

### 7.3 同级子表为平行关系（不支持 SUB → SUB FK）

同一表单下的多个 SUB binding 视为**平行兄弟**，**禁止**在它们之间配置结构 FK。

**理由：**

- 同级子表没有稳定的「当前父行」语义（用户可能未选中、可能并发编辑多行）。
- 用单条选中行做 FK 来源会与 MI / Link Form / Form Preview 的多视图状态冲突。
- 简化模型：FK 永远指向**更高一层**的主表，避免同级耦合。

**强制约束：**

1. Table Designer 配置 FK 时，若 `refTableId` 与当前表处于**同一表单的同级 SUB**，保存校验失败：
   > 「同级子表之间不支持外键关联。如需关联，请将 {引用表 displayName} 改为更高一层的主表，再把当前表作为它的子表。」
2. AC 同理：FK 不可指向同一上下文下的同级 RT。
3. 已有数据若误配置（历史 FU 不应存在此情况）→ 保存时检测并报错，不静默接受。

**业务真要关联怎么办：**

- 在 Table Design 中**重新建模**：把被引用的表设为 `MAIN`（或上层 SUB），当前表作为它的子表，形成新一层主从。
- 自动填充链按 7.2（主表未就绪阻断）+ 7.4（结构 FK 自动填）执行，不再有「同级互引」的特殊路径。

### 7.4 支持的引用关系

| 关系 | 父行来源 |
|---|---|
| SUB → MAIN（PRIMARY） | 主表单 PRIMARY binding 的 `formData`（须已有 PK） |
| 多层嵌套 SUB → 上层表 | 上层表的当前行 `formData`（嵌套 binding 上下文） |
| 任意表 → RELATION（AC 已部署） | Lookup 选中行 / RELATED binding 已加载数据 |
| AC 表间 FK | 仅 AC 已部署 Relation Table 互引 |

**不支持：** 同级 SUB ↔ SUB（见 7.3）。

### 7.5 共享运行时上下文

```typescript
interface RowAddContext {
  primaryFormData: Record<string, unknown>           // MAIN binding 当前数据
  ancestorRows: Record<number, Record<string, unknown>>  // refTableId → 上层主表行（多层主从时）
  bindingLinkModes: Map<number, BindingLinkMode>
}

type BindingLinkMode = 'structuralFk' | 'miParticipantRow'
```

核心函数（建议 `platform-common` 或 `frontend/shared` + 后端等价）：

- `slugFieldName(displayName)`
- `encodeCompositePrimaryKey(pkFields, row)`
- `resolveForeignKeyValues(fkMetas, ctx)` — 仅解析 MAIN / 上层 / RELATION，不解析同级 SUB
- `guardBeforeChildRowAdd(targetTableId, fkMetas, ctx)`
- `validateFkAgainstSiblingSubs(fkMeta, formBindings)` — 保存校验，拒绝同级互引
- `allocatePrimaryKeys(api, tableId, fieldName, count)`

---

## 8. 模块 D：Form Preview & User Portal

### 8.1 接入点

| 应用 | 组件/路径 |
|---|---|
| DW Form Preview | `SubTableField`、`SubTableAddDialog`、`FormPreviewItems` |
| User Portal | `SubTableField`、`SubTableAddDialog`、`FormRenderer` |
| 流程发起 / 待办 / My Request | 所有「新增子表行」路径 |

### 8.2 行为要求

1. 与模块 C 相同 guard / fill / encode 逻辑。
2. PK 非 `manual` 时调用后端 `allocate` API。
3. FK 字段 readonly/hidden 与表单设计器配置一致。
4. 报错文案、阻断条件与 Preview **完全一致**。

---

## 9. 模块 E：Admin Center Relation Table

### 9.1 结构页

- 字段列表：**Display Name** + **Field Name**（预填可编辑，模块 0）。
- FK / PK 元数据 UI 与 DW 对齐（5.1、5.2）。
- FK 引用下拉：**仅已部署** Relation Table。

### 9.2 数据页

- 新增行：FK 自动填充、PK 后端分配、父行/主表 guard（模块 C）。
- 与 DW Preview 同一套 shared runtime。

---

## 10. MI 与 `bindingLinkMode`

### 10.1 背景

现有 MCY 等 FU 中 `foreign_key_field = row_id` 表示 **MI 参与者行关联**，不是「引用主表 PK」。Portal 已有专门逻辑（见 `.cursor/rules/portal-mi-subtable-my-request.mdc`）。

### 10.2 规则

| 模式 | 用途 | 结构 FK 自动填 MAIN PK |
|---|---|---|
| `structuralFk` | 常规定义在字段上的 FK | ✅ 按模块 C |
| `miParticipantRow` | MI 多实例参与者切片 | ❌ 走现有 `_currentItem` / `primaryKeyFields` |

- 同一 binding **可同时**：MI 参与者关联 + 结构 FK（如 `case_id → MAIN`）— 两套逻辑并行，互不覆盖。
- 历史数据迁移：MCY 等 binding 需显式标记 `miParticipantRow`，避免被结构 FK 逻辑误处理。

### 10.3 回归

- MCY：HMDC Transaction / Attachment、My Request 全案、file-only 附件、Link Form。
- 测试参考：`mcyInitiatorMyRequest.test.ts`、`miSubProcessScope.test.ts`。

---

## 11. 表单绑定 `foreign_key_field`

- **不再**作为设计师手工配置源。
- 保存 binding 时由系统根据 FK 元数据 **派生**（只读展示），或逐步废弃 UI 编辑。
- 与 FK 元数据冲突时 **以 FK 元数据为准**。
- MI binding 使用 `bindingLinkMode`，不依赖 `foreign_key_field` 表达结构关系。

---

## 12. 数据模型与迁移（实施备忘）

### 12.1 Developer Workstation

| 对象 | 变更 |
|---|---|
| `dw_field_definitions` | 增加 `display_name`（或 rename `description`）、`pk_generation_json`、`fk_display_mode` 等 |
| `dw_foreign_keys` | 已有；UI 写入 CRUD |
| DTO | `FieldDefinitionRequest/Response`：`displayName`、FK/PK 扩展 |

### 12.2 Admin Center

| 对象 | 变更 |
|---|---|
| `rt_field_definitions` | `display_name`、FK/PK 扩展；FK 目标表约束 |
| 序号 API | 新服务或扩展现有 `RelationTableDataService` |
| DTO | `RelationFieldDTO`：`displayName` 替代显示用途的 `comment` |

### 12.3 迁移

- Flyway + `deploy/init-scripts/` 双轨（见 `docs/schema-and-migration.md`）。
- 读 API 过渡期可返回 `displayName` + 旧字段别名；写 API 只接受 `displayName`。

---

## 13. 实施分期

| 阶段 | 内容 |
|---|---|
| **S1** | `displayName` 统一 + Field Name 预填/可编辑（DW + AC UI/DTO/迁移） |
| **S2** | FK/PK 元数据模型、保存校验、API 下发、`dw_foreign_keys` UI |
| **S3** | 后端 PK `allocate` API（DW + AC） |
| **S4** | 共享 runtime（encode / fill / guard / allocate 客户端） |
| **S5** | DW Preview + Portal + AC 数据页接入 |
| **S6** | `bindingLinkMode`、MI 共存、MCY 全量回归 |

---

## 14. 验收标准

1. DW/AC 新建字段：填 Display Name → Field Name 预填；可改 Field Name；未 touched 时随 Display Name 更新。
2. 配置 SUB→MAIN FK：MAIN 无 PK 时点子表新增 → 弹框阻断，不打开对话框。
3. MAIN PK 为 `prefixedSequence`，保存后新增 SUB 行 → FK = MAIN PK；FK 不可编辑。
4. FK 设 `hidden` → 界面不显示；提交 JSON 含 FK。
5. 联合 PK `(tenant_id, line_no)` → FK 为规范合成串；merge 不丢关联。
6. 配置同级 SUB↔SUB FK → 保存校验**失败**并给出改为「上层主表 / 下层子表」的建议文案。
7. 多层嵌套主从（MAIN → SUB1 → SUB2）：新增 SUB2 行时 FK 自动等于 SUB1 当前行 PK；SUB1 当前行无 PK 时按 7.2 阻断。
8. 两用户并发新增 → 后端序号无重复。
9. AC FK 引用未部署表 → 保存失败；仅已部署 RT 可选。
10. MCY MI：`row_id` 参与者、Attachment、My Request 全案行为不变。
11. Preview 与 Portal 同一操作路径结果一致。
12. 「数据模型关联配置」弹窗：默认展示由字段 FK 派生的关系；弹窗与字段定义**双向同步**。
13. 在弹窗 **新增** 关系 → 对应字段在「字段定义」表格中立即显示为外键（`isForeignKey` 勾选 + `refTable / refField` 填上）。
14. 在弹窗 **编辑** 关系（改目标 / 关系类型）→ 字段 FK 元数据同步更新；保存校验与字段入口一致。
15. 在弹窗 **删除** 关系 → 对应字段 `isForeignKey` 取消并清空引用配置。
16. 弹窗中指向同级 SUB 或非 PK 列 → 保存失败，给出错误文案；与字段入口校验一致。
17. 升级迁移：旧 `dw_table_relations` 与字段 FK 一致的派生展示；冲突且目标非 PK（MI 类 `→ row_id`）→ 不回填 FK，提示改用 `bindingLinkMode`。

---

## 15. 风险与缓解

| 风险 | 缓解 |
|---|---|
| 设计师误把同级子表配成 FK 关系 | Table Designer 保存校验拒绝 + 文案引导改为新一层主从 |
| 多层嵌套主从中「上层当前行」未明确 | 嵌套 binding 上下文沿主从层级单向解析；同级永不互相解析 |
| 联合 PK 合成串与历史纯标量 PK 不一致 | 新 FU 启用；旧 FU 迁移或保持 `manual` |
| `foreign_key_field` 历史误配 | `bindingLinkMode` 显式标记 + 以 FK 元数据为准 |
| hidden FK 在 form-create 中被丢弃 | save pipeline 显式 merge FK 字段 |
| JSON 存储无 DB sequence | 必须后端 allocate API |

---

## 16. 相关文档与规则

- `docs/schema-and-migration.md` — Schema 双轨迁移
- `.cursor/rules/json-row-storage-no-physical-tables.mdc` — 禁止 per-table 物理表
- `.cursor/rules/portal-design-parity.mdc` — Preview ↔ Portal 一致
- `.cursor/rules/portal-mi-subtable-my-request.mdc` — MI / My Request 子表数据
- `.cursor/rules/domain-model.mdc` — RelationTable / TableDefinition 术语

---

## 17. 决策记录（Changelog）

| 日期 | 决策 |
|---|---|
| 2026-05-28 | 初稿：FK/PK、Preview/Portal parity、后端序号 |
| 2026-05-28 | FK 用户不可改；凡有 FK 自动填；联合 PK 合成单列 |
| 2026-05-28 | 主表未保存禁止加子表；AC 需 PK/FK；统一 `displayName` |
| 2026-05-28 | Field Name 可编辑（非只读）；SUB→SUB 方案 A |
| 2026-05-28 | AC FK 仅已部署 RT；`foreign_key_field` 以 FK 元数据为准 + `bindingLinkMode` |
| 2026-05-28 | 全文整理为 PRD v1.0 |
| 2026-05-28 | **同级子表为平行关系，禁止 SUB→SUB FK**；要关联须重建模为新一层主从（撤销原方案 A） |
| 2026-05-28 | 「数据模型关联配置」弹窗改为**只读派生视图**，关系由字段 FK 自动生成，不再手工新增 |
| 2026-05-28 | 撤销上一条：弹窗**保留**新增/编辑/删除入口，与字段 FK 元数据**双向同步**；字段 FK 仍是 Source of Truth |
