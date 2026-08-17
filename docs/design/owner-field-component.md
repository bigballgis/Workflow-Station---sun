# Owner 归属组件：表单设计 + View 设计（Dataverse 式）

> **状态：方案已定稿（2026-08-17 改版），未实现。** 改代码前先确认本文；确认后按 playbook 执行。
>
> **给后续 agent：** Owner 是落表的「归属」字段，对齐 Dataverse 的 Owner 列：
> - 值是 **一个用户** 或 **一个 BU+Role 组**（对应 Dataverse 的 User / Team）。
> - 默认 = 创建人（主表 = 流程发起人；子表行 = 建行人）；之后**可改派**，改派是人做的，不是系统同步的。
> - **不跟** `current_assignee` 走。转办 / 认领 / 流转 **不改** Owner。
> - 不接 `AssigneeType` / `TaskAssignmentListener` / MI `AssignmentConfig`；不控制行可见性。
> - 列由保存表单时按 `lookup` 同一条 provision 路径生成；不先建 Table 字段，不给每张表默认追加。
>
> 历史包袱：本文曾有一版「`valueSource=ASSIGNEE`、Owner 跟办理人变、只读 + `OwnerAssigneeSync`」。
> **该方案已废弃**（用户 2026-08-17 确认走 Dataverse 式）。看到旧对话 / 旧摘要提到
> `OwnerAssigneeSync`、「跟着 assignee 变」一律以本文为准，不要实现同步机制。

相关文档：

- 任务分派模型（不要复用到本组件）：BPMN `assigneeType`、`UserTaskAssigneeConfigSection.vue`
- MI 行内分派（不要改，但 BU/Role 选择器照它抄）：[mi-subtask-bu-role-assignment.md](./mi-subtask-bu-role-assignment.md)、[mi-assignment-mode-component.md](./mi-assignment-mode-component.md)
- View 查询语义：[portal-main-table-view-query.md](./portal-main-table-view-query.md)
- View 访问管控：skill `view-access-control`（Owner **不**控制行可见性）

---

## 1. 背景与目标

对齐 Power Apps Dataverse 的 Owner 列：每行数据有一个「归属方」，默认是创建它的人，
之后可以改派给另一个人或一个群体。我们平台里对应 Dataverse Team 的一等公民是
**BU+Role**（`sys_business_units` + `sys_roles` + `sys_user_business_unit_roles`）。

| Dataverse | 本平台 Owner 组件 |
|---|---|
| Owner 列类型，值 = User 或 Team | `type:"owner"` 字段，值 = 用户 或 BU+Role 组 |
| 默认 = 创建记录的用户 | 默认 = 主表行的流程发起人 / 子表行的建行人 |
| Assign 动作改派 | 表单上直接改（该节点表单可编辑即是可改派） |
| Owner 驱动行级安全 | **不做**。纯归属展示 + 筛选维度 |

设计师操作（与 `lookup` 相同，**不是**先建表字段再绑）：

1. 打开已绑定 MAIN / 某张 SUB 的表单，拖入 Owner。
2. 属性面板配置是否允许选「BU+Role 组」（允许选人是恒有的）。
3. 保存表单 → `FormConfigJsonTableProvisioner` 在该表自动建列。Table Design / View 随后能勾选。
4. 每张表最多一个 Owner。主表一张、每个子表各可以有一张。

**成功标准：** 发起保存后 Owner = 发起人；任意节点上把 Owner 改成李四或「财务部·审批员」，
保存后表单和 View 都显示新归属；转办任务**不影响** Owner。

---

## 2. 非目标

- **不跟办理人同步。** 没有 `OwnerAssigneeSync`，不碰 `setCurrentAssignee` 的任何写点。
- **不改** `AssigneeType`、`TaskAssigneeResolver`、`TaskAssignmentListener`、BPMN 扩展属性、MI `AssignmentConfig`。
- **不用** Owner 控制 View 行可见性（「owned by me」筛选、行级 ACL 都是后续）。
- **不做**独立的 Assign 端点 / 改派权限模型。MVP：该节点表单上 Owner 可编辑 = 可改派；
  设计师想锁死就在对应表单把它设只读。
- **不做** Owner 改派通知、不反写任务办理人。
- **不在每张表默认追加**（不进 `SystemAuditFields` / `TableAuditFieldInitializer`）；**不要求**先在 Table Design 建字段。

### 2.1 与现有人员概念的边界

| 概念 | 真源 | 与 Owner 的关系 |
|---|---|---|
| 发起人 `start_user_id/name` | 实例列，发起时写死 | 只是 Owner 的**默认值来源**，之后各走各的 |
| 办理人 `current_assignee` | 实例列，任务生命周期更新 | **无关**。转办不改 Owner |
| 审计 `created_by` 等 | 行 JSON，`SystemAuditFieldFiller` | 照旧。Owner 可改派，审计字段不可 |

---

## 3. 概念模型

`type: "owner"`，`input: true`、有 `field`。列在保存表单时 provision 进 `dw_field_definitions`。

### 3.1 值模型：USER 或 GROUP

| 形态 | 主值（落库） | `__display` |
|---|---|---|
| 用户 | `user:<userId>` | 用户显示名，如 `张三` |
| BU+Role 组 | `group:<buCode>\|<roleCode>` | `<BU名> / <Role名>`，如 `财务部 / 审批员` |

- 仍是标量字符串（View `jsonTextExpr` 筛选 / 公式可用），禁止对象、禁止数组。
- GROUP 存 **code**（与 MI FIXED_BU_ROLE 落库约定一致，见 `useSubTableBuRoleCascade.ts` 头注释）；
  分隔符用 `|`，避免 code 内含 `:` 的歧义。
- GROUP 的成员是**读时动态**的：库里只存组标识，不物化成员名单。今天在组里的人明天退出，行不变。

### 3.2 默认值：空才填创建人

| 场景 | 默认写入 |
|---|---|
| 主表：发起表单保存 | Owner 为空 → 写 `user:<发起人>`（即当前登录用户） |
| 子表行：行首次保存 | Owner 为空 → 写 `user:<建行人>` |
| 之后任何保存 | **非空不覆盖**。用户改成什么就是什么 |

「空才填」而不是「每次保存都写」：Owner 可改派，覆盖会把改派冲掉。
这与审计字段（每次由后端强制写）刻意不同。

### 3.3 改派

- 有权编辑该表单（节点表单该字段未设只读）的用户，直接在 Owner 控件里换人 / 换组，随表单提交落库。
- 改派是普通字段变更：**进变更历史**（谁在哪一步把 Owner 从 A 改成 B——这是期望的审计效果，不是噪音）。
- 后端不信任客户端 `__display`：提交时按主值重新解析（见 §6.3）。

### 3.4 唯一性：每张表一个

| 范围 | 规则 |
|---|---|
| 一张 MAIN / SUB 表 | 最多一个 Owner（一个 `field`） |
| 同表多张表单 | 必须复用同一列、同一 `ownerConfig` |
| 不同表 | 互不影响 |

校验：画布 `only: true`（作用域实现前实测）+ 保存表单时按 `tableId` 对账。冲突则保存失败，禁止静默。

---

## 4. 数据契约

### 4.1 `props.ownerConfig`

```jsonc
{ "allowGroup": true }   // false = 只能选人
```

JSON 字符串，对齐 `lookupConfig`。缺省视为 `true`；非法 JSON → 保存失败；运行时禁止 fallback 成 `user` / `el-input`。

### 4.2 form-create rule

```jsonc
{
  "type": "owner",
  "field": "owner",
  "title": "Owner",
  "props": { "ownerConfig": "{\"allowGroup\":true}" }
}
```

- `addDragRule` 照抄 `lookup`：`input: true`、`only: true`；子表画布也能拖。
- `mapDataType`：`owner` → `VARCHAR`。
- 默认 `field` 为 `owner`；若表已有同名字段，设计器必须改名或报错，**禁止**静默绑到既有业务列
  （provision 的按名去重会跳过建列，等于偷偷复用别人的列）。

### 4.3 运行态

```jsonc
{ "owner": "user:u123", "owner__display": "张三" }
{ "owner": "group:FIN|APPROVER", "owner__display": "财务部 / 审批员" }
```

`__display` 只存名字本身，不存「待认领」之类 UI 措辞（措辞会把语言写死在库里）。

### 4.4 View

保存过含 Owner 的表单后，列出现在 catalog，显示优先 `__display`。
筛选 / 排序 / 分组走现有 `jsonTextExpr` 文本语义（按 display 文本 contains / 排序）。
「owned by me」（含命中我所在的组）是后续项，本期不做。没拖过 Owner 的表没有这一列。

---

## 5. 设计器行为

### 5.1 产品步骤

```text
打开表单 → 拖 Owner →（可选）关掉「允许选组」→ 保存表单 → 该表自动多一列
```

禁止：Table Design 先加字段再绑。属性面板**没有**「绑定已有表字段」。
同表第二张表单拖 Owner 时必须复用同列同配置。

### 5.2 落点

| 步骤 | 文件 |
|------|------|
| 注册 | `frontend/developer-workstation/src/main.ts`（`addDragRule`，照 `lookup`） |
| 画布 | `OwnerWidget.vue` |
| 属性 | 开关：允许选 BU+Role 组 |
| Preview | `useFormPreviewColumns.ts` |
| 子表类型 | `designerSubTableField/types.ts` + `'owner'` |
| 建列 | `FormCreateRuleToFieldMapper`（`owner` → `VARCHAR`）+ `FormConfigJsonTableProvisioner` |
| 对账 | 表单保存按 §3.4；同名既有字段冲突报错 |

### 5.3 View 设计器

无新 `columnType`。拖过并保存后才出现在列目录。

---

## 6. Portal 运行时

### 6.1 渲染与选择器：`OwnerField.vue`

- 展示态：显示 `__display`；无值显示 `-`。非法 `ownerConfig` 报错，禁止 fallback 成输入框。
- 编辑态（表单该字段可编辑时）两种输入方式：
  - **选人**：远程搜索，复用 `userApi.searchUsers`（`FormRenderer.handleUserSearch` 同款，防抖 ≥300ms、per-instance timer）。
  - **选组**（`allowGroup` 时）：BU 级联树 + Role 下拉，照抄 `useSubTableBuRoleCascade.ts` 的模式
    （`permissionApi.getBusinessUnitsTree` + `getBusinessUnitRoles(buId)`，BU cascader value=id、落库存 code）。
- 浮层遵守 code-quality §6.6：对话框内用 `nextZIndex()`，必要时 Teleport。

### 6.2 默认值写入：`OwnerDefaultFiller`

后端在发起落库 / 子表行首次保存时执行「空才填创建人」（§3.2）。放后端不放前端：
前端预填可以做（发起页直接显示自己，体验好），但**以后端为准**，防止客户端删掉默认值绕过。

### 6.3 提交校验与 display 解析（后端）

提交里出现 Owner 字段时：

1. 校验主值格式：`user:<id>` 或 `group:<buCode>|<roleCode>`，且 `allowGroup=false` 时拒绝 group。
2. 校验存在性并**重解析 `__display`**：
   - user → `UserDisplayNameResolver`；
   - group → portal 既有 BU/Role 目录解析（`PermissionController` 背后组件，数据源 admin-center；
     参考 `VirtualGroupAccessComponent` 的 BU/Role 名称解析）。
3. 解析失败 → 校验错误返回，**禁止**静默保留客户端 display 或存空（error-handling 红线 1）。

不剥离、不只读：Owner 是用户可编辑字段，客户端值是合法输入，防线是校验而不是剥离。

### 6.4 子表

- 每行各有自己的 Owner，默认建行人，行内可改派。
- 触达 `SubTableField.vue` / 子表对话框只加类型渲染。`regression:mi` 全套。
  不改 `AssignmentConfig` / `shared.ts` merge。

### 6.5 View

读 `__display`。改派保存后刷 View 应看到新归属——验收必须刷 View，不能只看详情页。

---

## 7. 后端

- 无新 HTTP 端点（选择器数据源全部复用既有 `/permissions/*` 与用户搜索代理）。
- DW：`mapDataType` + provision + 跨表单对账 + 同名冲突检查。不改审计四字段初始化。
- Portal：`OwnerDefaultFiller`（空才填）+ 提交校验 / display 重解析。**不碰**任何办理人写点。
- 无新表 / 新列 / 新 env。

---

## 8. 影响面

| 层级 | 变更 |
|------|------|
| DW 前端 | `main.ts`；`OwnerWidget.vue`；属性面板；Preview；子表 union |
| DW 后端 | `mapDataType`；provision；保存对账 + 同名冲突 |
| Portal 前端 | `OwnerField.vue`（搜人 + BU/Role 级联）；`FieldRenderer`；子表渲染 |
| Portal 后端 | `OwnerDefaultFiller`；提交校验 + display 重解析 |
| i18n | palette、属性开关、选人 / 选组 tab、空态、校验错误（三语） |

**禁止改：** `platform-common` 语义、`AssigneeType` / `TaskAssignmentListener`、MI `AssignmentConfig`、
`ProcessInstanceSyncComponent` 及一切办理人写点。

---

## 9. 分期

MVP：组件 + USER/GROUP 两种值 + 默认创建人 + 表单内改派 + 每表一个 + View 列。

后续：「owned by me」筛选（含组成员命中）、独立 Assign 动作与改派权限、改派通知、行级可见性。

---

## 10. 风险与回滚

| 风险 | 处理 |
|------|------|
| 默认值把改派冲掉 | §3.2「空才填」+ 单测：改派后再保存，值不回退 |
| 客户端伪造 `__display` | §6.3 后端按主值重解析 |
| GROUP code 变更（BU/Role 改 code） | 与 MI FIXED_BU_ROLE 同风险面，本期随平台现状；display 落库不受影响 |
| 组解析失败（BU/Role 已删除） | 提交时校验报错；存量行 display 仍可读 |
| 同名既有字段 | 设计器报错，禁止静默复用（§4.2） |
| 同表两个 Owner / 配置不一致 | 保存失败 |

回滚：去掉 palette。已写入的 JSON 键保留，View 列仍可读。

---

## 11. 验收

**反例（当前）：** 没有可拖的 Owner；表单上没有默认创建人、可改派、可选 BU+Role 组的归属列。

**正例：**

1. 拖 Owner 保存表单后有列；发起后 `variables.owner` = `user:<发起人id>`，display 为发起人名。
2. 审批节点把 Owner 改成李四 → 保存后表单、View、变更历史都体现改派。
3. 改成「财务部 / 审批员」组 → 主值 `group:FIN|APPROVER`，display 为组名；View 可按该文本筛选。
4. **转办任务 → Owner 不变。**
5. 改派后再次保存表单（不动 Owner）→ 值不回退成创建人。
6. `allowGroup=false` 的表单提交 group 值 → 校验失败。
7. 同表第二个 Owner / 第二张表单换列名 → 保存失败。
8. 子表行：新增行默认建行人，行内改派后随行保存生效。

---

## 12. 验证

```bash
cd frontend/developer-workstation && pnpm run build
cd frontend/user-portal && pnpm run build
mvn -pl backend/developer-workstation,backend/user-portal -am package -DskipTests
cd frontend && pnpm run regression:mi
cd deploy/environments/dev && docker compose -f docker-compose.dev.yml --env-file .env \
  up -d --build user-portal user-portal-frontend developer-workstation developer-workstation-frontend
```

单测：`ownerConfig` 非法；同表冲突 / 同名字段冲突；空才填（发起 / 子表行）；改派不回退；
主值格式与 `allowGroup` 校验；display 重解析；`mapDataType("owner") == VARCHAR`。

---

## 13. 代码落点

**DW：** `main.ts`；`OwnerWidget.vue`；`useFormPreviewColumns.ts`；`designerSubTableField/types.ts`；
`FormCreateRuleToFieldMapper`；`FormConfigJsonTableProvisioner`。

**Portal：** `OwnerField.vue`（复用 `userApi.searchUsers` + `useSubTableBuRoleCascade` 模式）；
`OwnerDefaultFiller`；提交校验 / display 重解析；子表渲染。

**不要改：** `TaskAssignmentListener`、`AssigneeType`、`ProcessInstanceSyncComponent`、
`shared.ts` merge（除非渲染强制，先报告）。

---

## 14. 已确认

1. 走 **Dataverse 式**：值 = 用户或 BU+Role 组，默认创建人，可改派；**不跟办理人同步**（旧「ASSIGNEE 跟着变 + OwnerAssigneeSync」方案作废，2026-08-17）。
2. 每张表一个 Owner；拖组件建列，不每表默认有，不先建 Table 字段。
3. 值落表，标量字符串 `user:<id>` / `group:<buCode>|<roleCode>` + `__display`。
4. 不控制行可见性，不接 BPMN 分派，「owned by me」等后续另开。

---

确认本文后若要开工，回复：

```text
按 playbook 执行（先输出任务整理，等我确认）。
```
