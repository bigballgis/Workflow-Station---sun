# View 访问管控 — 手测指南

适用环境：本地 Docker Dev（`http://localhost:3000`）  
功能单元：**Multi-Instance Subtask Demo**（`fu-20260422-23tfag`，DW id 50005）  
规则说明：见 `.cursor/skills/view-access-control/SKILL.md`（平台访问模型 D-04 / D-05：SYS_ADMIN 在 Portal 没有旁路）

---

## 1. 准备数据

### 1.1 前置：功能单元已部署

Multi-Instance Subtask Demo 须已在 Admin Center 部署并启用（dev 库已有；全新初始化后先在 DW 部署一次）。部署出新版本后须重新执行下面的种子：功能单元访问配置挂在 Admin 目录的版本行上。

### 1.2 导入测试账号与 View 规则（一次性 / 可重复）

先按 §5.1 记下 4 个 View 原来的规则与开关，测完按 §5.2 撤销；种子本身不可逆。

```powershell
# 勿用 Get-Content | docker exec — PowerShell 会展开 SQL 中 BCrypt 的 $ 符号导致插入失败
docker cp deploy\init-scripts\91-view-access-test\02-view-access-test-access-model.sql platform-postgres-dev:/tmp/view-access-test.sql
docker exec platform-postgres-dev psql -v ON_ERROR_STOP=1 -U platform_dev -d workflow_platform_dev -f /tmp/view-access-test.sql
```

种子做的事：建 4 个 `view_*` 账号（都进 CUSTOM 组 `E2E_WORKFLOW_SIMULATION`）、两个测试业务角色 `VIEW_TEST_OTHER` / `VIEW_TEST_NOFU`（BU_BOUNDED，绑定共享财务中心）、数字化运营部绑定 Department Manager；重置功能单元 4 个已发布 View 的规则（§2.1）；给功能单元已启用的版本行加 Department Manager、`VIEW_TEST_OTHER` 的访问。种子不把任何账号放进 SYSTEM 组，dev 的 System Administrator 仍只有 `developer`（AC-37）。

### 1.3 确认服务已启动

```powershell
cd deploy\environments\dev
docker compose -f docker-compose.dev.yml --env-file .env ps user-portal developer-workstation
```

Portal 登录入口：`http://localhost:3000/login/`（统一 SSO）

---

## 2. 测试账号一览

**统一密码：`password`**

| 用户名 | BU | 业务角色 | SYS_ADMIN | 功能单元门禁 | 期望看到的 View | 用途 |
|--------|-----|---------|-----------|-------------|----------------|------|
| `developer` | 共享财务中心 `E2E_FINANCE` | Department Manager（登录选这个工作台） | **是** | 通过 | Meeting、People | 验证 SYS_ADMIN 没有旁路（AC-25） |
| `view_allowed` | 共享财务中心 | Department Manager | 否 | 通过 | Meeting、People | BU+Role 匹配 |
| `view_wrong_bu` | 数字化运营部 `E2E_IT` | Department Manager | 否 | 通过 | 无（菜单里没有该功能单元） | BU 与 View 不匹配 |
| `view_wrong_role` | 共享财务中心 | `VIEW_TEST_OTHER` | 否 | 通过 | 无 | Role 与 View 不匹配 |
| `view_nofu` | 共享财务中心 | `VIEW_TEST_NOFU` | 否 | **不通过** | 无 | 验证功能单元门禁 |
| `admin` | — | — | 否（D-22） | — | 进不了 Portal（不在 CUSTOM 组） | 对照 |

> **说明：** 进 Portal 须是至少一个启用的 CUSTOM 组成员；SYS_ADMIN 不例外，且进入后同样只按当前 BU + Role 判断。须至少一条 UBR 才会进入 **FULL** 模式，没有 UBR 会落入 **权限自助模式**。`developer` 有多个工作台时选 共享财务中心 / Department Manager。修改账号或 SQL 后须 **退出重新登录**。

### 2.1 种子写入的 View 规则

| View | BU | Role | 仅参与用户可见数据 | 结果 |
|------|-----|------|-------------------|------|
| Meeting（主表） | `bu-e2e-finance` | `role-manager` | **是** | 财务中心经理可见 |
| People | `bu-e2e-finance` | `role-manager` | 否 | 财务中心经理可见 |
| Participants | `bu-e2e-finance` | （空） | 否 | 不成对：任何人不可见 |
| Attachment | （空） | （空） | 否 | 空配置：任何人不可见 |

---

## 3. 测试步骤

### 场景 T1 — 空配置任何人都不可见（Attachment）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | DW 打开 Multi-Instance Subtask Demo → View Design → **Attachment**，确认 BU/Role **均未选** | 右侧提示发布后无人可见 |
| 2 | Portal 用 **`view_allowed`** 登录 → **Views** → Multi-Instance Subtask Demo | **不应**出现 Attachment |
| 3 | 用 **`developer`**（工作台 共享财务中心 / Department Manager）登录，同上 | **不应**出现 Attachment |

**Pass：** 两个账号都看不到 Attachment（SYS_ADMIN 没有旁路）。

### 场景 T3 — BU + Role AND（People）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | **`view_allowed`** 登录 → Views | **可见** People |
| 2 | **`view_wrong_bu`** 登录 | 菜单里没有该功能单元（BU 为数字化运营部） |

### 场景 T5 — 不完整配置不可见（Participants）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | **`view_allowed`** 登录 → Views | **不可见** Participants（只配了 BU） |
| 2 | DW 打开 Participants，补选 Role（须与 BU 成对）→ Save → Publish | Portal 按 AND 可见；测完重新执行种子恢复 |

### 场景 T4 — Role 不匹配 / 无功能单元门禁

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | **`view_wrong_role`** 登录 | 菜单里没有该功能单元（能访问功能单元，但没有 View 对它开放） |
| 2 | **`view_nofu`** 登录 | 同上（功能单元门禁就不通过） |

### 场景 T6 — 仅参与用户可见数据（Meeting）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | 确认库中有该功能单元的流程实例，发起人 / 办理人**不是** `view_allowed` | 可用现有数据 |
| 2 | **`view_allowed`** 打开 Meeting | 行数 **少于** 主表全量（查库对比；只含参与过的流程） |
| 3 | **`view_allowed`** 发起一条流程后再看 Meeting | **多看到** 至少该发起记录 |

### 场景 T7 — SYS_ADMIN 同样受参与过滤

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | **`developer`** 打开 Meeting | 只看到自己参与的行，与普通用户同一规则 |

### 场景 DW — 设计态 Save 回显

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | DW **`developer`** 登录 → FU 50005 → View Design → People | 回显 BU 共享财务中心 + Role Department Manager |
| 2 | 访问控制改选后 **Save** → 刷新页面 | BU/Role/开关 **回显正确** |
| 3 | BU、Role 都不选 → **Save** | 保存成功，提示发布后无人可见；测完重新执行种子恢复 |

---

## 4. 自动校验

```powershell
cd frontend
node scripts/verify-view-access-control.mjs
```

脚本逐个账号登录 Portal，校验 Views 菜单与该功能单元的 View 列表（`developer`、`view_allowed` 恰好是 Meeting、People；其余三个账号菜单里没有该功能单元），并截图 DW 中 Meeting 的访问配置与每个账号的 Portal Views 页。

---

## 5. 恢复 / 撤销

种子会删掉并重写 4 个已发布 View 的规则、改“仅参与用户”开关，并新建账号、角色、BU 角色绑定（其中 `bur-view-it-mgr` 让数字化运营部也能申请 Department Manager）和功能单元访问。只是“重新执行种子”只能回到种子状态，回不到执行前；要回到执行前，按下面两步做。

### 5.1 执行种子前：记下原状态

把下面的 SQL 存为 `view-access-snapshot.sql`，与 §1.2 一样 `docker cp` 到 `platform-postgres-dev:/tmp/` 后用 `psql -v ON_ERROR_STOP=1 … -f /tmp/view-access-snapshot.sql` 执行。快照文件写在 postgres 容器的 `/tmp`，容器重建会丢；要长期保留就 `docker cp` 出来。

```sql
-- 4 个 View 的“仅参与用户”开关与 updated_at
\copy (SELECT v.id, v.restrict_to_involved_users, v.updated_at FROM dw_main_table_view_configs v JOIN dw_function_units fu ON fu.id = v.function_unit_id WHERE fu.code = 'fu-20260422-23tfag' AND v.status = 'PUBLISHED' AND v.view_name IN ('Meeting', 'People', 'Participants', 'Attachment')) TO '/tmp/view-access-before-views.csv' WITH (FORMAT csv, HEADER)
-- 它们的访问规则
\copy (SELECT a.id, a.view_config_id, a.target_type, a.target_id FROM dw_main_table_view_access a WHERE a.view_config_id IN (SELECT v.id FROM dw_main_table_view_configs v JOIN dw_function_units fu ON fu.id = v.function_unit_id WHERE fu.code = 'fu-20260422-23tfag' AND v.status = 'PUBLISHED' AND v.view_name IN ('Meeting', 'People', 'Participants', 'Attachment'))) TO '/tmp/view-access-before-rules.csv' WITH (FORMAT csv, HEADER)
-- 种子要建的行此前应当都不存在；不为 0 时不要用 5.2 撤销（它会连原有的行一起删掉）
SELECT (SELECT count(*) FROM sys_users WHERE id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu')
          OR username IN ('view_allowed', 'view_wrong_bu', 'view_wrong_role', 'view_nofu'))
     + (SELECT count(*) FROM sys_roles WHERE id IN ('role-view-test-other', 'role-view-test-nofu')
          OR code IN ('VIEW_TEST_OTHER', 'VIEW_TEST_NOFU'))
     + (SELECT count(*) FROM sys_business_unit_roles WHERE id IN ('bur-view-it-mgr', 'bur-view-fin-other', 'bur-view-fin-nofu'))
       AS seed_rows_already_there;
```

### 5.2 测完：撤销种子

前提：5.1 的两个快照文件还在，且当时 `seed_rows_already_there` 为 0。把下面的 SQL 存为 `view-access-undo.sql`，同样 `docker cp` 后 `psql -v ON_ERROR_STOP=1 … -f` 执行。整段是一个事务，任一步失败都不改动。删的都是种子自己的固定 id（`fua-view-test-…` 只在原来没有同样授权时才建，按前缀删不会误删原有授权）。

```sql
BEGIN;
CREATE TEMP TABLE before_views (id BIGINT, restrict_to_involved_users BOOLEAN, updated_at TIMESTAMP) ON COMMIT DROP;
\copy before_views FROM '/tmp/view-access-before-views.csv' WITH (FORMAT csv, HEADER)
CREATE TEMP TABLE before_rules (id BIGINT, view_config_id BIGINT, target_type VARCHAR, target_id VARCHAR) ON COMMIT DROP;
\copy before_rules FROM '/tmp/view-access-before-rules.csv' WITH (FORMAT csv, HEADER)

-- 1. 4 个 View 的规则、开关、updated_at 回到快照
DELETE FROM dw_main_table_view_access WHERE view_config_id IN (SELECT id FROM before_views);
INSERT INTO dw_main_table_view_access (id, view_config_id, target_type, target_id)
SELECT id, view_config_id, target_type, target_id FROM before_rules;
UPDATE dw_main_table_view_configs v
SET restrict_to_involved_users = b.restrict_to_involved_users, updated_at = b.updated_at
FROM before_views b WHERE v.id = b.id;

-- 2. 种子建的功能单元访问、账号（连同成员关系、UBR、登录记录）、BU 角色绑定、角色
DELETE FROM sys_function_unit_access WHERE id LIKE 'fua-view-test-%';
DELETE FROM sys_virtual_group_members WHERE user_id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu');
DELETE FROM sys_user_business_unit_roles WHERE user_id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu');
DELETE FROM sys_user_business_units WHERE user_id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu');
DELETE FROM sys_login_audit WHERE user_id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu');
DELETE FROM sys_users WHERE id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu');
DELETE FROM sys_business_unit_roles WHERE id IN ('bur-view-it-mgr', 'bur-view-fin-other', 'bur-view-fin-nofu');
DELETE FROM sys_roles WHERE id IN ('role-view-test-other', 'role-view-test-nofu');
COMMIT;

-- 核对：应为 0
SELECT (SELECT count(*) FROM sys_users WHERE id IN ('user-view-allowed', 'user-view-wrong-bu', 'user-view-wrong-role', 'user-view-nofu'))
     + (SELECT count(*) FROM sys_roles WHERE id IN ('role-view-test-other', 'role-view-test-nofu'))
     + (SELECT count(*) FROM sys_function_unit_access WHERE id LIKE 'fua-view-test-%') AS seed_rows_left;
```

手测里用这些账号发起过流程（场景 T6 第 3 步）、提交过申请的，那些业务数据不在撤销范围内，须另行处理（撤回流程、撤回申请）后再执行撤销。

### 5.3 只回到种子状态

重新执行 `02-view-access-test-access-model.sql` 即可把 4 个 View 的规则与“仅参与用户”开关重置为 §2.1。  
Portal 运行时直接读 `dw_main_table_view_access`；在 DW 手工改过 View 后，须 **Publish FU** 后 Portal 才按 PUBLISHED 的 View 列出。

---

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 所有 View 都看不到 | 功能单元未部署或已停用 / 部署新版本后没重跑种子（功能单元访问挂在版本行上）/ 用户无 UBR |
| `developer` 也看不到 Attachment | 符合预期：空配置任何人都看不到，SYS_ADMIN 没有旁路 |
| `view_*` 登录被拒 `PORTAL_ENTITLEMENT_DENIED` | 不在 CUSTOM 组：重新执行种子 |
| 权限自助 / access_denied | 无 UBR → 重新执行 SQL 并 **退出重登**；勿用旧 JWT |
| Views 菜单为空 | 功能单元门禁按 **catalog UUID** 查 access，code 须 resolve；确认 Admin 里该功能单元已启用 |
| 改 DW 后 Portal 不变 | View config 仍为 DRAFT；Publish FU 或确认 status=PUBLISHED |
| Role 下拉 No data | 须先选 BU；选项来自该 BU 准入角色 API，非全局 `/roles` |
| Save 提示 BU/Role 成对 | 只填一侧时不允许保存；两者均空表示发布后无人可见 |
| 登录失败 | `developer-workstation` / `user-portal` 容器是否 healthy |
