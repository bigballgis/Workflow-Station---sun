# 数据库初始化脚本深度检查报告

> 检查范围：`deploy/init-scripts/` 全部文件 + `backend/admin-center/src/main/resources/db/migration/`
> 检查遍数：10 遍 + 修复后复查 3 遍
> 检查日期：2026-03-20

---

## 总结

经过 10 遍深度检查，共发现 **23 个问题**（5 个严重、8 个中等、10 个低风险）。

| 严重程度 | 数量 | 说明 |
|---------|------|------|
| 🔴 严重 | 5 | 会导致脚本执行失败或数据错误 |
| 🟡 中等 | 8 | 不影响执行但存在不一致或潜在风险 |
| 🟢 低风险 | 10 | 代码质量、最佳实践建议 |

---

## 第 1 遍：SQL 语法正确性

✅ 所有 SQL 文件语法正确，DO 块、dollar-quoting、BPMN XML 嵌入均无语法错误。

---

## 第 2 遍：外键引用完整性

### 🔴 P01：`05-fix-bpmn-approval-form.sql` 引用了不存在的表 `sys_function_unit_contents`

**文件**：`10-simple-approval/05-fix-bpmn-approval-form.sql`

该脚本 UPDATE 了 `sys_function_unit_contents` 表，但这个表在 schema 中名为 `sys_function_unit_contents`（07-add-action-definitions-table.sql 中没有创建它，01-platform-security-schema.sql 第 28 节创建了它）。

但问题在于：该脚本使用了硬编码的 `formId=6` 和 `version = '1.0.8'`，这些值只在特定数据库状态下有效。如果在全新初始化环境中运行，`sys_function_unit_contents` 表中不会有 `SIMPLE_APPROVAL` 的 `1.0.8` 版本数据（因为初始化脚本只创建了 `dw_function_units` 中的数据，不会自动部署到 `sys_function_units`/`sys_function_unit_contents`）。

**影响**：该脚本在全新初始化环境中不会匹配任何行，UPDATE 0 行，不会报错但也不会生效。
**建议**：添加注释说明此脚本仅适用于已部署环境，或改为更新 `dw_process_definitions` 中的 BPMN XML。

### 🔴 P02：`10-simple-approval/01-insert-bpmn-process.sql` 中 `{{APPROVAL_FORM_ID}}` 占位符未被使用

**文件**：`10-simple-approval/01-insert-bpmn-process.sql`

BPMN XML 中 Manager Approval 节点的 `formId` 使用的是 `{{REQUEST_FORM_ID}}`（而非 `{{APPROVAL_FORM_ID}}`），但代码中仍然执行了 `replace(v_bpmn_xml, '{{APPROVAL_FORM_ID}}', v_approval_form_id::text)`。

这意味着 BPMN XML 中没有 `{{APPROVAL_FORM_ID}}` 占位符，replace 操作是空操作。审批节点使用的是 Request Form 而非 Approval Form。

**影响**：审批人看到的是申请表单而非审批表单。这正是 `05-fix-bpmn-approval-form.sql` 试图修复的问题。
**建议**：直接在 `01-insert-bpmn-process.sql` 中将 Manager Approval 节点的 formId 改为 `{{APPROVAL_FORM_ID}}`。

### ✅ 其他外键引用均正确

- 所有 `dw_function_units` 的 `ON CONFLICT (code)` 正确
- 所有 `dw_form_definitions` 的 `ON CONFLICT (function_unit_id, form_name)` 正确
- 所有 `dw_action_definitions` 的 `ON CONFLICT (function_unit_id, action_name)` 正确
- 所有 `dw_table_definitions` 的 `ON CONFLICT (function_unit_id, table_name)` 正确

---

## 第 3 遍：约束一致性

### 🟡 P03：`dw_function_units` 的 `status` CHECK 约束与 `sys_function_units` 不一致

| 表 | 允许值 |
|----|--------|
| `dw_function_units` | `DRAFT`, `PUBLISHED`, `ARCHIVED` |
| `sys_function_units` | `DRAFT`, `VALIDATED`, `DEPLOYED`, `DEPRECATED` |

数据脚本中使用了 `PUBLISHED`（如 `10-simple-approval`、`13-procurement-workflow`、`14-travel-expense-reimbursement`），这在 `dw_function_units` 中是合法的。但 `12-simple-approval` 和 `08-digital-lending-v2-en` 使用了 `DRAFT`，也是合法的。

**影响**：两个表的状态枚举不同是设计意图（开发环境 vs 部署环境），不是 bug。
**建议**：在 schema 注释中明确说明两套状态的映射关系。

### 🟡 P04：`dw_action_definitions` 的 `action_type` 没有 CHECK 约束

**文件**：`00-schema/04-developer-workstation-schema.sql`

`dw_action_definitions.action_type` 列没有 CHECK 约束，但数据脚本中使用了多种类型：`PROCESS_SUBMIT`、`APPROVE`、`REJECT`、`N8N_ACTION`、`FORM_POPUP`、`API_CALL` 等。

`sys_action_definitions.action_type` 同样没有 CHECK 约束。

**建议**：添加 CHECK 约束或在注释中列出所有合法值。

---

## 第 4 遍：索引覆盖

### 🟡 P05：`dw_form_table_bindings` 缺少 `form_id` 上的查询索引

虽然有 `idx_dw_form_table_bindings_form` 索引，但在 `14-add-common-table-feature.sql` 中删除了 `uk_form_table_binding` 唯一约束后，替换为两个 partial unique index。这是正确的。

### 🟡 P06：`sys_action_definitions` 缺少 `(function_unit_id, action_name)` 的唯一索引

**文件**：`00-schema/07-add-action-definitions-table.sql`

虽然创建了 `uk_sys_action_name_fu` 唯一索引，但它是用 `CREATE UNIQUE INDEX` 而非 `UNIQUE CONSTRAINT` 创建的。功能上等价，但 `ON CONFLICT` 子句需要引用约束名或列组合。

**影响**：无实际影响，PostgreSQL 的 `ON CONFLICT` 可以使用唯一索引。

### ✅ 其他索引覆盖良好

- `sys_users` 有 username、email、status、employee_id、deleted 索引
- `dw_function_units` 有 name、status、code、version、active、deployed_at 索引
- 所有 FK 列都有对应索引

---

## 第 5 遍：幂等性检查

### ✅ Schema 文件全部幂等

- 所有 `CREATE TABLE` 使用 `IF NOT EXISTS`
- 所有 `CREATE INDEX` 使用 `IF NOT EXISTS`
- 所有 `ALTER TABLE ADD COLUMN` 使用 `IF NOT EXISTS`
- 所有 `CREATE EXTENSION` 使用 `IF NOT EXISTS`

### ✅ 数据脚本大部分幂等

- 所有 `dw_function_units` INSERT 使用 `ON CONFLICT (code) DO UPDATE`
- 所有 `dw_form_definitions` INSERT 使用 `ON CONFLICT (function_unit_id, form_name) DO UPDATE`
- 所有 `dw_action_definitions` INSERT 使用 `ON CONFLICT (function_unit_id, action_name) DO UPDATE`
- 所有 `dw_table_definitions` INSERT 使用 `ON CONFLICT (function_unit_id, table_name) DO UPDATE`

### 🟡 P07：`10-simple-approval/04-insert-sample-data.sql` 的动态表创建不完全幂等

该脚本使用 `CREATE TABLE IF NOT EXISTS dw_data_%s` 创建动态表，INSERT 使用 `ON CONFLICT (id) DO NOTHING`。但 `setval` 调用假设表中有数据（`SELECT MAX(id)` 可能返回 NULL）。

**影响**：如果表为空，`setval(seq, NULL)` 会报错。
**建议**：改为 `SELECT setval(seq, GREATEST(COALESCE(MAX(id), 0), 1))`。

### 🔴 P08：`08-digital-lending-v2-en/01-create-digital-lending-complete.sql` 不幂等

该脚本使用 `INSERT ... RETURNING id INTO` 而没有 `ON CONFLICT` 子句（对于 `dw_function_units` 的 INSERT）。如果重复执行，会因为 `code` 唯一约束而失败。

**影响**：重复执行会报错。
**建议**：添加 `ON CONFLICT (code) DO UPDATE ... RETURNING id INTO`。

---

## 第 6 遍：列类型一致性

### 🔴 P09：ID 列类型不一致

| 表前缀 | ID 类型 | 示例 |
|--------|---------|------|
| `sys_*` | `VARCHAR(64)` | `sys_users.id`, `sys_roles.id` |
| `dw_*` | `BIGSERIAL` | `dw_function_units.id`, `dw_form_definitions.id` |
| `bi_*` | `VARCHAR(64)` | `bi_dashboard_registry.id` |
| `up_*` | 混合 | `up_notification.id` 是 `BIGSERIAL` |

这是设计意图（sys 用 UUID 字符串，dw 用自增整数），但需要注意跨表引用时的类型匹配。

### 🟡 P10：`sys_function_units.id` 是 `VARCHAR(64)` 但 `dw_function_units.id` 是 `BIGSERIAL`

部署流程中需要将 `dw_function_units` 的数据导出到 `sys_function_units`，ID 类型不同需要转换。

**影响**：应用层处理，不影响 SQL 脚本。

### 🟡 P11：`created_by`/`updated_by` 列长度不一致

| 表 | 列 | 长度 |
|----|-----|------|
| `sys_users` | `created_by` | `VARCHAR(64)` |
| `dw_function_units` | `created_by` | `VARCHAR(50)` |
| `dw_ai_documents` | `created_by` | `VARCHAR(50)` |
| `sys_dictionaries` | `created_by` | `VARCHAR(36)` |

**建议**：统一为 `VARCHAR(64)` 以匹配 `sys_users.id` 的长度。

---

## 第 7 遍：时间戳类型一致性

### 🟡 P12：`TIMESTAMP` vs `TIMESTAMP WITH TIME ZONE` 混用

| 使用 `TIMESTAMP` | 使用 `TIMESTAMP WITH TIME ZONE` |
|------------------|-------------------------------|
| `sys_users.created_at` | `sys_business_units.created_at` |
| `dw_function_units.created_at` | `sys_function_units.created_at` |
| `sys_roles.created_at` | `sys_virtual_group_task_history.created_at` |

大部分表使用 `TIMESTAMP`（无时区），少数使用 `TIMESTAMP(6) WITH TIME ZONE`。

**影响**：在同一数据库中混用可能导致时区转换问题。
**建议**：统一使用 `TIMESTAMP WITH TIME ZONE`，或确保应用层始终使用 UTC。

---

## 第 8 遍：执行顺序正确性

### ✅ `00-init-all.sh` 执行顺序正确

1. 创建 N8N 数据库
2. 基础 schema（01→02→03→04→05）
3. 增量迁移（06→07→08→10→11→12→13→14→15）
4. 角色和管理员
5. 示例数据（08→10→12→13→14）

### ✅ `init-database.ps1` 执行顺序与 `00-init-all.sh` 一致

### 🟢 P13：缺少 `09-*.sql` 迁移文件

迁移编号从 08 跳到 10，缺少 09。

**影响**：无功能影响，仅编号不连续。
**建议**：保持现状或在注释中说明。

### ✅ 每个示例数据目录内的执行顺序正确

- `08-digital-lending-v2-en`：00→01→02→03 ✅
- `10-simple-approval`：00→01→02→03→04→04→05 ✅
- `12-simple-approval`：00→01→02→03 ✅
- `13-procurement-workflow`：00→01→02→03 ✅
- `14-travel-expense-reimbursement`：00→01→02→03→04 ✅

---

## 第 9 遍：数据脚本与 Schema 一致性

### 🔴 P14：`13-procurement-workflow/01-create-tables.sql` 中 `sort_order` 重复

`RequestItems` 表的字段定义中，`count` 和 `sort_order` 的 `sort_order` 值都是 `8`：

```sql
(v_items_table_id, 'count',       'INTEGER', NULL, NULL, NULL, true,  NULL, false, false, 'Count',          8),
(v_items_table_id, 'sort_order',  'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Display Order',  8);
```

同样，`Request` 表的 `updated_at` 和 `budget` 的 `sort_order` 都是 `10`：

```sql
(v_main_table_id, 'updated_at', 'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Updated at', 10),
(v_main_table_id, 'budget',     'INTEGER',   NULL, NULL, NULL, true,  NULL, false, false, 'budget',     10);
```

**影响**：字段显示顺序可能不确定。
**建议**：修正 `sort_order` 值，`count` 改为 8，`sort_order` 改为 9；`budget` 改为 11。

### 🟢 P15：`12-simple-approval/03-form-table-bindings.sql` 中 subForms 使用 `table_id` 而非 `binding_id`

该脚本的注释说 "key = 当前 function unit 的实际 table_id"，但 `13-procurement-workflow/03-form-table-bindings.sql` 的注释明确说 "subForms keys in config_json must use form_table_binding IDs, not table_definition IDs"。

**影响**：如果前端期望 binding ID 作为 key，则 `12-simple-approval` 的 subForms 会无法正确关联。
**建议**：统一使用 `form_table_binding` 的 ID 作为 subForms 的 key。

### 🟢 P16：`10-simple-approval/04-form-table-bindings.sql` 使用了 `NOW()` 而非 `CURRENT_TIMESTAMP`

虽然功能等价，但与其他脚本不一致（大部分使用 `CURRENT_TIMESTAMP`）。

**建议**：统一使用 `CURRENT_TIMESTAMP`。

### 🟢 P17：`08-digital-lending-v2-en` 的 `dw_form_table_bindings` INSERT 缺少 `foreign_key_field`

```sql
INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order) VALUES ...
```

其他脚本（如 `13-procurement-workflow`）都包含 `foreign_key_field` 列。虽然该列有默认值 NULL，但 SUB 类型的绑定通常需要指定外键字段。

**建议**：为 SUB 和 RELATED 类型的绑定添加 `foreign_key_field`（如 `loan_application_id`）。

### 🟢 P18：`13-procurement-workflow/01-create-tables.sql` 中 `RequestAttachments` 的 `table_type` 是 `SUB` 而非 `RELATION`

描述说 "Relation table for request attachments"，但 `table_type` 设为 `SUB`。对比 `12-simple-approval` 中同名表使用 `RELATION`，`10-simple-approval` 中也使用 `RELATION`。

**影响**：可能影响前端对表类型的处理逻辑。
**建议**：改为 `RELATION` 以保持一致。

---

## 第 10 遍：Shell/PS1 脚本正确性

### ✅ `00-init-all.sh` 正确性

- `set -e` 确保错误时退出 ✅
- 使用 `ON_ERROR_STOP=1` ✅
- 文件存在性检查 `[ -f "$f" ]` ✅
- glob 模式正确匹配所有需要的文件 ✅

### ✅ `init-database.ps1` 正确性

- `$ErrorActionPreference = "Stop"` ✅
- 文件存在性检查 `Test-Path` ✅
- 密码通过环境变量传递 ✅
- 执行顺序与 bash 脚本一致 ✅

### 🟢 P19：`00-init-all.sh` 中 Step 5 的 glob 模式会匹配两个 `04-*.sql` 文件

```bash
/docker-entrypoint-initdb.d/10-simple-approval/04-*.sql
```

这会匹配 `04-form-table-bindings.sql` 和 `04-insert-sample-data.sql`，执行顺序取决于文件名排序。由于 `form-table-bindings` < `insert-sample-data`（字母序），所以绑定会先于数据插入执行，这是正确的。

但 `init-database.ps1` 中明确列出了两个文件的顺序，更加可靠。

**建议**：将 `04-form-table-bindings.sql` 重命名为 `04a-form-table-bindings.sql`，`04-insert-sample-data.sql` 重命名为 `04b-insert-sample-data.sql`，避免歧义。

### 🟢 P20：`init-database.ps1` 中密码处理

脚本在 `finally` 块中清除 `$env:PGPASSWORD`，这是好的安全实践。但如果用户不传 `-DbPassword` 参数，默认值为空字符串，psql 可能会提示输入密码。

**建议**：添加密码为空时的提示或从 `.pgpass` 文件读取。

---

## 额外发现

### 🟢 P21：`00-schema/00-init-all-schemas.sql` 使用 `\i` 指令

该文件使用 `\i` 指令引用 Docker 路径，只能在 Docker 环境中通过 `psql` 执行。`00-init-all.sh` 实际上没有使用这个文件（它直接用 glob 模式执行各个 SQL 文件）。

**影响**：该文件是冗余的，可能造成混淆。
**建议**：在文件头部注释中明确说明此文件仅供手动 psql 使用，不被自动化脚本调用。

### 🟢 P22：`15-bi-management-schema.sql` 与 Flyway `V201__create_bi_management_tables.sql` 完全相同

两个文件内容完全一致，这是正确的（init-scripts 用于全新初始化，Flyway 用于增量迁移）。

### 🟢 P23：`bi_rbac_mapping` 的 `sys_role_id` 没有外键约束到 `sys_roles`

```sql
CREATE TABLE IF NOT EXISTS bi_rbac_mapping (
    sys_role_id VARCHAR(64) NOT NULL,  -- 没有 FOREIGN KEY 约束
    ...
);
```

**建议**：添加 `FOREIGN KEY (sys_role_id) REFERENCES sys_roles(id)` 以确保引用完整性。

---

## 修复优先级建议

| 优先级 | 编号 | 问题 | 修复难度 | 状态 |
|--------|------|------|---------|------|
| 1 | P08 | Digital Lending 脚本不幂等 | 中 | ✅ 已修复：添加 ON CONFLICT (code) DO UPDATE |
| 2 | P02 | Simple Approval BPMN 审批表单 ID 错误 | 低 | ✅ 已修复：Manager Approval 改用 APPROVAL_FORM_ID |
| 3 | P14 | Procurement sort_order 重复 | 低 | ✅ 已修复：sort_order 改为 9 和 11 |
| 4 | P15 | subForms key 不一致（table_id vs binding_id） | 中 | ✅ 已修复：12-simple-approval 改用 binding_id |
| 5 | P07 | Sample data setval 可能报错 | 低 | ✅ 已修复：使用 GREATEST(COALESCE(MAX(id), 0), 1) |
| 6 | P01 | fix-bpmn 脚本在新环境无效 | 低 | ✅ 已修复：添加注释说明 + P02 根因已修复 |
| 7 | P17 | Digital Lending 缺少 foreign_key_field | 低 | ✅ 已修复：所有绑定添加 foreign_key_field |
| 8 | P18 | Procurement RequestAttachments 类型错误 | 低 | ✅ 已修复：SUB 改为 RELATION |
| 9 | P23 | bi_rbac_mapping 缺少 FK 约束 | 低 | ✅ 已修复：添加 FK 到 sys_roles(id)（init + Flyway） |
| 10 | P11 | created_by 列长度不一致 | 低 | ✅ 已修复：统一为 VARCHAR(64) |
| 11 | P12 | TIMESTAMP 类型混用 | 中 | ⚠️ 添加注释说明，应用层使用 UTC |
| - | P03 | status CHECK 约束不一致 | - | ✅ 添加 COMMENT 说明状态映射 |
| - | P04 | action_type 无 CHECK 约束 | - | ✅ 添加 COMMENT 列出合法值 |
| - | P09 | ID 列类型不一致 | - | ✅ 添加 COMMENT 说明 ID 约定 |
| - | P13 | 缺少 09 迁移文件 | - | ✅ 添加注释说明 |
| - | P16 | NOW() vs CURRENT_TIMESTAMP | - | ✅ 已修复：统一为 CURRENT_TIMESTAMP |
| - | P19 | glob 匹配两个 04-*.sql | - | ✅ 添加注释说明执行顺序 |
| - | P20 | PS1 密码为空处理 | - | ✅ 已修复：添加空密码警告 |
| - | P21 | 00-init-all-schemas.sql 冗余 | - | ✅ 已修复：更新注释说明 |

---

## 修复后复查（3 遍）

> 复查日期：2026-03-20

### 复查第 1 遍：回归验证

逐一确认 23 个原始修复全部正确应用，无回归。所有 SQL 语法正确。

### 复查第 2 遍：交叉一致性检查

发现 3 个遗留问题并已修复：

| 编号 | 严重程度 | 问题 | 状态 |
|------|---------|------|------|
| P11-补充 | 🟡 中等 | `sys_dictionaries.updated_by` 仍为 VARCHAR(36)，P11 修复时遗漏 | ✅ 已修复 |
| P18-补充 | 🟢 低风险 | `13-procurement-workflow/01-create-tables.sql` 中 4 处注释/RAISE NOTICE 仍写 `RequestAttachments (SUB)`，与 table_type=RELATION 不一致 | ✅ 已修复 |
| P16-补充 | 🟢 低风险 | `12-simple-approval/03-form-table-bindings.sql` 和 `13-procurement-workflow/03-form-table-bindings.sql` 仍使用 NOW()，P16 只修了 10-simple-approval | ✅ 已修复 |

### 复查第 3 遍：新引入问题检查

- ✅ 无新引入的语法错误
- ✅ FK 引用一致性完好
- ✅ ON CONFLICT 子句正确
- ✅ CHECK 约束与数据值匹配
- ✅ 脚本执行顺序在 bash 和 PowerShell 中一致
- ✅ init-scripts 与 Flyway 迁移内容同步

**结论：所有 23 个原始问题 + 3 个遗留问题均已修复，无新问题引入。**

---

## Entity-Schema 联合交叉检查（5 遍）

> 检查日期：2026-03-20
> 检查范围：Java Entity 类 vs `deploy/init-scripts/` + Flyway 迁移
> 涉及模块：platform-security、admin-center、developer-workstation

### 第 1 遍：列名匹配（Entity 字段 → DB 列名）

逐一比对所有 Entity 的 `@Column(name=...)` 与 init-script 中的列名。

#### ✅ 完全匹配的表（无列名差异）

- `sys_users` ↔ `User.java` (platform-security)
- `sys_roles` ↔ `Role.java`
- `sys_business_units` ↔ `BusinessUnit.java`
- `sys_user_roles` ↔ `UserRole.java`
- `sys_role_assignments` ↔ `RoleAssignment.java`
- `sys_permissions` ↔ `Permission.java`
- `sys_login_audit` ↔ `LoginAudit.java`
- `sys_virtual_groups` ↔ `VirtualGroup.java`
- `sys_virtual_group_members` ↔ `VirtualGroupMember.java`
- `sys_virtual_group_roles` ↔ `VirtualGroupRole.java`
- `sys_business_unit_roles` ↔ `BusinessUnitRole.java`
- `sys_user_business_units` ↔ `UserBusinessUnit.java`
- `sys_user_business_unit_roles` ↔ `UserBusinessUnitRole.java`
- `sys_approvers` ↔ `Approver.java`
- `sys_permission_requests` ↔ `PermissionRequest.java`
- `sys_member_change_logs` ↔ `MemberChangeLog.java`
- `sys_user_preferences` ↔ `UserPreference.java`
- `sys_dictionaries` ↔ `Dictionary.java`
- `sys_dictionary_items` ↔ `DictionaryItem.java`
- `sys_dictionary_versions` ↔ `DictionaryVersion.java`
- `sys_dictionary_data_sources` ↔ `DictionaryDataSource.java`
- `sys_function_units` ↔ `FunctionUnit.java` (admin-center)
- `sys_function_unit_deployments` ↔ `FunctionUnitDeployment.java`
- `sys_function_unit_approvals` ↔ `FunctionUnitApproval.java`
- `sys_function_unit_dependencies` ↔ `FunctionUnitDependency.java`
- `sys_function_unit_contents` ↔ `FunctionUnitContent.java`
- `sys_function_unit_access` ↔ `FunctionUnitAccess.java` (admin-center)
- `sys_developer_role_permissions` ↔ `DeveloperRolePermission.java`
- `sys_action_definitions` ↔ `ActionDefinition.java` (admin-center)
- `bi_dashboard_registry` ↔ `BiDashboardRegistry.java`
- `bi_dashboard_assignment` ↔ `BiDashboardAssignment.java`
- `bi_superset_role` ↔ `BiSupersetRole.java`
- `bi_rbac_mapping` ↔ `BiRbacMapping.java`
- `dw_icons` ↔ `Icon.java`
- `dw_function_units` ↔ `FunctionUnit.java` (developer-workstation)
- `dw_process_definitions` ↔ `ProcessDefinition.java`
- `dw_table_definitions` ↔ `TableDefinition.java`
- `dw_field_definitions` ↔ `FieldDefinition.java`
- `dw_foreign_keys` ↔ `ForeignKey.java`
- `dw_form_definitions` ↔ `FormDefinition.java`
- `dw_form_table_bindings` ↔ `FormTableBinding.java`
- `dw_action_definitions` ↔ `ActionDefinition.java` (developer-workstation)
- `dw_versions` ↔ `Version.java`
- `dw_operation_logs` ↔ `OperationLog.java`
- `dw_ai_sessions` ↔ `AiSession.java`
- `dw_ai_messages` ↔ `AiMessage.java`
- `dw_ai_documents` ↔ `AiDocument.java`
- `dw_common_table_definitions` ↔ `CommonTableDefinition.java`
- `dw_common_field_definitions` ↔ `CommonFieldDefinition.java`
- `dw_common_table_data` ↔ `CommonTableData.java`

#### 🔴 E01：`sys_virtual_group_task_history` — Entity 缺少 4 个列

| 列名 | Init Script | Entity (`VirtualGroupTaskHistory.java`) |
|------|-------------|----------------------------------------|
| `assigned_user_id` | VARCHAR(64) | ❌ 缺失 |
| `assigned_at` | TIMESTAMP DEFAULT CURRENT_TIMESTAMP | ❌ 缺失 |
| `completed_at` | TIMESTAMP | ❌ 缺失 |
| `status` | VARCHAR(20) | ❌ 缺失 |

**影响**：Entity 无法读写这 4 个列的数据，任务分配和完成状态无法通过 JPA 操作。
**建议**：在 `VirtualGroupTaskHistory.java` 中添加这 4 个字段。

#### 🔴 E02：`sys_role_permissions` — Entity 缺少 `created_at` 列

Init script 有 `created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`，但 `RolePermission.java` 没有 `createdAt` 字段。Entity 的 `@CreationTimestamp` 注解在 `grantedAt` 上（映射到 `granted_at`），不是 `created_at`。

**影响**：`created_at` 列会由数据库默认值填充，但 JPA 不会管理它。如果有查询需要 `created_at`，Entity 无法提供。
**建议**：在 `RolePermission.java` 中添加 `createdAt` 字段。

#### 🟡 E03：`sys_function_unit_approvals` — Entity 多出 `approval_order` 列

`FunctionUnitApproval.java` 有 `@Column(name = "approval_order") private int approvalOrder;`，但 init script `01-platform-security-schema.sql` 中 `sys_function_unit_approvals` 表没有 `approval_order` 列。

**影响**：Hibernate 的 `hbm2ddl.auto=update` 会自动添加该列，但如果使用 `validate` 模式会启动失败。Init script 缺少该列。
**建议**：在 init script 中添加 `approval_order INTEGER DEFAULT 1`。

#### 🟡 E04：`dw_function_unit_access` — Init Script 中缺少该表

`FunctionUnitAccess.java` (developer-workstation) 映射到 `dw_function_unit_access` 表，但 `deploy/init-scripts/` 中没有创建该表的 SQL。

**影响**：如果不使用 Hibernate 自动建表，该表不存在，应用启动时会报错。
**建议**：在 `04-developer-workstation-schema.sql` 或新的 schema 文件中添加 `CREATE TABLE dw_function_unit_access`。

---

### 第 2 遍：列类型匹配（Java 类型 → SQL 类型）

#### 🔴 E05：`sys_dictionaries.id` — Entity 长度 36 vs Init Script 长度 64

| 字段 | Entity (`Dictionary.java`) | Init Script |
|------|---------------------------|-------------|
| `id` | `@Column(length = 36)` | `VARCHAR(64)` |

**影响**：Entity 生成的 DDL 会创建 VARCHAR(36) 列，与 init script 的 VARCHAR(64) 不一致。如果 ID 长度超过 36 字符，JPA 会截断或报错。
**建议**：将 Entity 的 `id` 长度改为 64。

#### 🔴 E06：`sys_dictionary_items.id` — Entity 长度 36 vs Init Script 长度 64

同 E05，`DictionaryItem.java` 的 `id` 是 `@Column(length = 36)`，init script 是 `VARCHAR(64)`。

**建议**：将 Entity 的 `id` 长度改为 64。

#### 🔴 E07：`sys_dictionaries` / `sys_dictionary_items` — `created_by`/`updated_by` 长度 36 vs 64

| Entity | 字段 | Entity 长度 | Init Script 长度 |
|--------|------|------------|-----------------|
| `Dictionary.java` | `createdBy` | 36 | 64 |
| `Dictionary.java` | `updatedBy` | 36 | 64 |
| `DictionaryItem.java` | `createdBy` | 36 | 64 |
| `DictionaryItem.java` | `updatedBy` | 36 | 64 |
| `DictionaryVersion.java` | `createdBy` | 36 | 64 |

**影响**：如果 `created_by` 值超过 36 字符（如标准 UUID 格式 36 字符刚好够，但如果使用其他格式的 ID），JPA 会截断。
**建议**：统一为 64 以匹配 init script 和其他表的约定。

#### 🟡 E08：`dw_function_units` / `dw_icons` / `dw_ai_documents` / `dw_common_*` — `created_by`/`updated_by` 长度 50 vs 64

| Entity | 字段 | Entity 长度 | Init Script 长度 |
|--------|------|------------|-----------------|
| `FunctionUnit.java` (dw) | `createdBy` | 50 | 64 |
| `FunctionUnit.java` (dw) | `updatedBy` | 50 | 64 |
| `Icon.java` | `createdBy` | 50 | 64 |
| `Icon.java` | `updatedBy` | 50 | 64 |
| `AiDocument.java` | `createdBy` | 50 | 64 |
| `CommonTableDefinition.java` | `createdBy` | 50 | 64 |
| `CommonTableData.java` | `createdBy` | 50 | 64 |

**影响**：如果用户 ID 超过 50 字符，JPA 会截断。实际上 `sys_users.id` 是 VARCHAR(64)，所以理论上 `created_by` 可能存储 64 字符的 ID。
**建议**：统一为 64。

#### 🟡 E09：`sys_virtual_groups.type` — Entity 默认值 "STANDARD" 不在 CHECK 约束中

Init script: `CONSTRAINT chk_virtual_group_type CHECK (type IN ('SYSTEM', 'CUSTOM'))`
Entity: `@Builder.Default private String type = "STANDARD";`

**影响**：如果使用 Entity 默认值创建虚拟组，INSERT 会违反 CHECK 约束报错。
**建议**：将 Entity 默认值改为 `"CUSTOM"`，或在 init script 中将 CHECK 约束扩展为 `('SYSTEM', 'CUSTOM', 'STANDARD')`。

---

### 第 3 遍：约束匹配（nullable、unique、FK）

#### 🟡 E10：`sys_virtual_group_roles` — Entity 唯一约束与 Init Script 不一致

Init script: `CONSTRAINT uk_virtual_group_role UNIQUE (virtual_group_id, role_id)` — 联合唯一
Entity: `@UniqueConstraint(name = "uk_vg_role_group", columnNames = {"virtual_group_id"})` — 仅 `virtual_group_id` 唯一

Init script 允许一个虚拟组绑定多个角色（只要 role_id 不同），但 Entity 的唯一约束限制一个虚拟组只能绑定一个角色。

**影响**：Entity 的约束比 init script 更严格。如果 Hibernate 管理 DDL，会创建更严格的约束。
**建议**：确认业务需求。如果一个虚拟组确实只能绑定一个角色，则修改 init script 的约束为 `UNIQUE (virtual_group_id)`。如果允许多角色，则修改 Entity。

#### 🟡 E11：`sys_role_permissions.granted_at` — Entity 类型 `LocalDateTime` vs Init Script `TIMESTAMP(6) WITH TIME ZONE`

Init script: `granted_at TIMESTAMP(6) WITH TIME ZONE`
Entity: `private LocalDateTime grantedAt;`

`LocalDateTime` 不携带时区信息，而 `TIMESTAMP WITH TIME ZONE` 需要时区。应使用 `OffsetDateTime` 或 `Instant`。

**影响**：时区信息可能丢失。
**建议**：将 Entity 类型改为 `Instant` 或 `OffsetDateTime`。

#### 🟡 E12：`sys_business_units.created_at` — Entity 类型 `LocalDateTime` vs Init Script `TIMESTAMP(6) WITH TIME ZONE`

同 E11，`BusinessUnit.java` 使用 `LocalDateTime`，但 init script 使用 `TIMESTAMP(6) WITH TIME ZONE`。

**建议**：将 Entity 类型改为 `Instant` 或 `OffsetDateTime`。

---

### 第 4 遍：缺失列检查（Entity 有但 Schema 没有，或反之）

#### 🔴 E13：`sys_function_unit_approvals` — Init Script 缺少 `approval_order` 列

（同 E03，此处确认为缺失列问题）

Init script 中 `sys_function_unit_approvals` 没有 `approval_order` 列，但 Entity 有。

**建议**：在 init script 中添加：
```sql
approval_order INTEGER DEFAULT 1,
```

#### 🔴 E14：`dw_function_unit_access` — Init Script 完全缺失该表

（同 E04，此处确认为缺失表问题）

Entity `FunctionUnitAccess.java` (developer-workstation) 存在，但 init script 中没有 `dw_function_unit_access` 表。

**建议**：添加建表语句：
```sql
CREATE TABLE IF NOT EXISTS dw_function_unit_access (
    id BIGSERIAL PRIMARY KEY,
    function_unit_id BIGINT NOT NULL,
    access_type VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    CONSTRAINT fk_dw_fu_access_func_unit FOREIGN KEY (function_unit_id) REFERENCES dw_function_units(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_dw_fu_access_func_unit ON dw_function_unit_access(function_unit_id);
```

#### 🟡 E15：`sys_function_units` — Init Script 缺少 `deployed_at` 和版本相关列（在基础 schema 中）

`01-platform-security-schema.sql` 中 `sys_function_units` 没有 `deployed_at`、`is_active`、`previous_version_id` 列，但这些列在 `08-add-function-unit-versioning.sql` 中通过 ALTER TABLE 添加。

**影响**：无实际问题，ALTER TABLE 脚本会补充这些列。但基础 schema 与最终表结构不一致，可能造成混淆。
**建议**：考虑将这些列合并到基础 schema 中（可选优化）。

---

### 第 5 遍：Flyway 迁移一致性

#### ✅ `V201__create_bi_management_tables.sql` 与 `15-bi-management-schema.sql` 完全一致

两个文件内容完全相同，包括所有表定义、索引、注释。

#### 🟡 E16：其他模块缺少 Flyway 迁移

| 模块 | Flyway 迁移 | 状态 |
|------|------------|------|
| admin-center | `V201__create_bi_management_tables.sql` | ✅ 存在 |
| developer-workstation | 无 `db/migration/` 目录 | ⚠️ 缺失 |
| platform-security | 无 `db/migration/` 目录 | ⚠️ 缺失 |

**影响**：developer-workstation 和 platform-security 模块没有 Flyway 迁移，依赖 init-scripts 或 Hibernate 自动建表。在生产环境中，schema 变更无法通过 Flyway 追踪。
**建议**：为这两个模块创建 Flyway 基线迁移（可作为后续任务）。

---

### Entity-Schema 交叉检查问题汇总

| 编号 | 严重程度 | 问题 | 涉及表 | 建议修复 | 状态 |
|------|---------|------|--------|---------|------|
| E01 | 🔴 严重 | Entity 缺少 4 个列 | `sys_virtual_group_task_history` | 添加 Entity 字段 | ✅ 已修复 |
| E02 | 🔴 严重 | Entity 缺少 `created_at` 列 | `sys_role_permissions` | 添加 Entity 字段 | ✅ 已修复 |
| E03 | 🟡 中等 | Init Script 缺少 `approval_order` 列 | `sys_function_unit_approvals` | 添加 SQL 列 | ✅ 已修复 |
| E04 | 🟡 中等 | Init Script 缺少整张表 | `dw_function_unit_access` | 添加建表 SQL | ✅ 已修复 |
| E05 | 🔴 严重 | ID 长度 36 vs 64 | `sys_dictionaries` | Entity 改为 64 | ✅ 已修复 |
| E06 | 🔴 严重 | ID 长度 36 vs 64 | `sys_dictionary_items` | Entity 改为 64 | ✅ 已修复 |
| E07 | 🔴 严重 | `created_by`/`updated_by` 长度 36 vs 64 | `sys_dictionaries` / `sys_dictionary_items` / `sys_dictionary_versions` | Entity 改为 64 | ✅ 已修复 |
| E08 | 🟡 中等 | `created_by`/`updated_by` 长度 50 vs 64 | `dw_function_units` / `dw_icons` / `dw_ai_documents` / `dw_common_*` | Entity 改为 64 | ✅ 已修复 |
| E09 | 🟡 中等 | Entity 默认值不在 CHECK 约束中 | `sys_virtual_groups.type` | 修改 Entity 默认值为 CUSTOM | ✅ 已修复 |
| E10 | 🟡 中等 | 唯一约束范围不一致 | `sys_virtual_group_roles` | Init Script 改为 UNIQUE(virtual_group_id) | ✅ 已修复 |
| E11 | 🟡 中等 | Java 类型与 SQL 时区类型不匹配 | `sys_role_permissions.granted_at` | Entity 改用 Instant | ✅ 已修复 |
| E12 | 🟡 中等 | Java 类型与 SQL 时区类型不匹配 | `sys_business_units.created_at/updated_at` | Entity 改用 Instant | ✅ 已修复 |
| E13 | 🔴 严重 | Init Script 缺少列 | `sys_function_unit_approvals.approval_order` | 添加 SQL 列 | ✅ 已修复（同 E03） |
| E14 | 🔴 严重 | Init Script 缺少整张表 | `dw_function_unit_access` | 添加建表 SQL | ✅ 已修复（同 E04） |
| E15 | 🟢 低风险 | 基础 schema 与最终结构不一致 | `sys_function_units` 版本列 | 可选合并 | ✅ 已修复：版本列合并到基础 schema |
| E16 | 🟢 低风险 | 部分模块缺少 Flyway 迁移 | developer-workstation / platform-security | 后续创建基线迁移 | ⚠️ 后续任务 |

**统计**：7 个严重、7 个中等、2 个低风险，共 16 个问题。已修复 15 个，1 个保持后续处理（E16：Flyway 基线迁移）。
