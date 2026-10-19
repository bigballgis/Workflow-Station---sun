# 批量增删改 + Batch Assign 完整示例

`00-init.sql` 是独立、可在 SQL 客户端执行的 PostgreSQL 脚本。创建一个新的设计态 FU：

- 名称：`Sub-Table Batch CRUD + Assign Demo`
- Code：`sub-table-batch-demo`
- 默认开发组：`developer groups`
- 流程：Start → Create batch rows → Batch CRUD and Assign → 并行多实例子流程 → End。
- 三个表单：发起、批量维护/分派、当前行子任务；包含字段、FK、绑定、子表列表列、Sub Form、Batch Form、节点绑定和完整流程图布局。

## 执行

先应用本版本全部 `00-schema` 脚本。目标数据库必须已有一个有效的开发组。
脚本不会创建账号、开发组、角色或修改权限，不依赖本机账号及硬编码 ID。

在项目根目录运行（按实际环境替换容器名、数据库用户和数据库名）：

```powershell
docker cp deploy/init-scripts/25-sub-table-batch-demo/00-init.sql platform-postgres-dev:/tmp/sub-table-batch-demo.sql
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/sub-table-batch-demo.sql
```

也可在数据库客户端直接执行整个 `00-init.sql`。如果开发组名称不同，在**同一连接**先执行：

```sql
SET workflow.batch_demo_group = '你的开发组名称';
```

脚本用单个原子 DO 块创建全部元数据：失败不会留下半个 FU。重复执行时，识别到自己的示例 Code/标记就跳过，保留用户后续修改；若 Code 已属于其他 FU，则明确报错。不会删除、重置或覆盖原有 demo，也不会自动安装到每个环境，因此不加入 `00-init-all.sh`。

## 发布

1. 在 Developer Workstation 切换到相应开发组，找到这个 FU。
2. 点击 **Validate → Deploy**，开启 Auto Enable。SQL 只创建设计，不伪造部署记录、版本快照或 Flowable 引擎数据。
3. 在 Admin Center 中为这个 FU 配置测试用户所在角色的使用权限。开发组归属不等于 Portal 使用权限。
4. 用具备权限的用户进入 Portal，新建申请。每次输入不同的 **Case key** 和一个 **Request title**。

## 测试批量增删改

1. 在发起表单的子表里点击 **Batch Edit**；新增 3 行。
2. `Line key` 使用 UUID 自动生成；`case_ref` 由 Structural FK 自动带入，不手填。
3. Caption 默认 `New line`，Amount 默认 `0`。可以逐行修改，也可以勾选业务字段表格的行后使用 **Set value**。
4. 删除其中一行，点击 **Apply to page**，再正常保存/提交。
5. 提交后流程停在 **Batch CRUD and Assign**（首个 Create batch rows 任务按平台正常发起规则自动完成）。该步骤也能增删改行。

## 测试批量 Assign

1. 打开 **Batch CRUD and Assign** 待办，点击 **Batch Edit**。
2. 在**分派区域的表格**中选择行（与业务字段表格的行勾选相互独立）。
3. 可逐行或统一选择 **Assign by person**，从目录中选择真实用户；也可选择 **Assign by role**，先选 BU 再选该 BU 下的角色。
4. 每行只使用一种模式：用户 ID 写入 `assignee_id`，或 BU/角色代码写入 `bu_code` / `role_code`，不把姓名当 ID，也不两种模式同时填。
5. **Apply to page → Save** 保存分派。此时尚未创建子任务；所有行配置完整后点击 **Approve**。
6. 每行生成一个 **Handle assigned row** 子任务。按人模式直接分给所选用户；角色模式进入相应 BU + Role 候选队列，由成员领取。所有子任务完成后主流程结束。
7. 子任务表单使用 `miParticipantRow` + `line_key`，只处理当前行。已生成的任务不能再用 Batch Assign 改派；子任务不开放批量增删改/Assign，业务字段仍可逐行编辑。

## 组件配置保证

Sub-Table 与组件库标准契约一致：`type: "subTable"`，标题和 `_bindingId` 位于根节点，操作开关位于 `props`；**不包含 `field`、`value`、`validate`**，不会被包装成普通输入字段。`name` 只是设计器组件引用，不是业务字段。

前置表单绑定为 `structuralFk`，同时允许 Add/Edit/Delete/Assign；子任务绑定为 `miParticipantRow`。两个前置表单各自有 `schemaVersion: 1` Batch Form，包含 Caption、Amount 和组件库的 `miAssignment` 标记。分派规则来自 BPMN 的 Sub-Task Config，不使用字段事件脚本。

本包仅维护 `dw_*` 设计元数据，不创建 `batch_crud_assign_case` / `batch_crud_assign_line` 物理业务表，不预置任何真实用户、BU、角色和运行中任务。

## 回归验证

可选的 `verify.sql` 用于尚未创建此 FU 的测试数据库：通过 `psql -v ON_ERROR_STOP=1 -f verify.sql` 执行。
它在事务中安装两次，检查标准组件、动态绑定 ID、UUID、Batch Form/开关、子任务范围、BPMN 节点和布局，并模拟用户修改以检查重跑保护，最后 **ROLLBACK**，不保留示例数据（PostgreSQL sequence 分配值不回退）。
如果此 FU 已存在，它会明确拒绝测试，不会先删掉已有配置。

2026-10-19 本地验证：SQL 安装、重复执行保留编辑、缺少开发组回滚、DW Validate、正常 Deploy 均通过；通过 Portal 正常 API 实测批量 ADD、EDIT、带行版本声明的 DELETE、PERSON/ROLE ASSIGN，Approve 后引擎实际生成两条不同当前行的子任务。此记录是数据库/API 验证，不代表已自动点击验证全部 UI。
