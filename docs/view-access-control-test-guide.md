# View 访问管控 — 手测指南

适用环境：本地 Docker Dev（`http://localhost:3000`）  
功能单元：**MCY Debit Card**（`fu-20260505-thwmut`）  
规则说明：见 `.cursor/skills/view-access-control/SKILL.md`

---

## 1. 准备数据

### 1.1 导入测试账号与 View 规则（一次性 / 可重复）

```powershell
# 勿用 Get-Content | docker exec — PowerShell 会展开 SQL 中 BCrypt 的 $ 符号导致插入失败
docker cp deploy\init-scripts\91-view-access-test\01-view-access-test-users.sql platform-postgres-dev:/tmp/view-access-test.sql
docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -f /tmp/view-access-test.sql
```

### 1.2 确认服务已启动

```powershell
cd deploy\environments\dev
docker compose -f docker-compose.dev.yml --env-file .env ps user-portal developer-workstation
```

Portal 登录入口：`http://localhost:3000/login/`（统一 SSO）

---

## 2. 测试账号一览

**统一密码：`password`**

| 用户名 | 场景 ID | BU | Portal 业务角色 | SYS_ADMIN | MCY FU 门禁 | 用途 |
|--------|---------|-----|-----------------|-----------|-------------|------|
| `view_admin` | T1/T7 | 共享财务中心 | Manager + **SYS_ADMIN**（虚拟组） | **是** | 绕过 | 完整门户 + 全部 View |
| `view_allowed` | T3/T5/T6 | 共享财务中心 `bu-e2e-finance` | Department Manager | 否 | 通过（MANAGER） | BU+Role 匹配 |
| `view_wrong_bu` | T4 | 数字化部 `bu-e2e-it` | Department Manager | 否 | 通过 | BU 与 View 不匹配 |
| `view_wrong_role` | T4 | 共享财务中心 | Developer（非 Manager） | 否 | **不通过** | 无 FU 或 View 不可见 |
| `view_nofu` | — | 共享财务中心 | Auditor | 否 | **不通过** | 验证 FU 级门禁 |
| `developer` | 对照 | 共享财务中心 | Manager + **SYS_ADMIN** | **是** | 绕过 | 开发调试（非业务验收账号） |
| `admin` | 对照 | — | System Admin | **是** | 绕过 | 平台管理员 |

> **说明：** Portal 登录须至少一条 UBR 才会进入 **FULL** 模式；仅有虚拟组 SYS_ADMIN 而无 UBR 会落入 **权限自助模式**（只能进 Permissions，其它菜单报 `portal.self_service_access_denied`）。`view_admin` 种子已配置 BU+Manager + `vg-sys-admins`。修改账号或 SQL 后须 **退出重新登录**。

### 2.1 脚本写入的 MCY View 规则

| View | ID | BU | Role | 仅参与用户可见数据 |
|------|-----|-----|------|-------------------|
| HMDC Attachment | 50207 | （空） | （空） | 否 |
| HMDC Case | 50206 | `bu-e2e-finance` | `role-manager` | 否 |
| HMDC Transaction | 50205 | `bu-e2e-finance` | `role-manager` | **是** |

---

## 3. 测试步骤

### 场景 T1 — 空配置仅 SYS_ADMIN 可见（HMDC Attachment）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | DW 打开 FU `MCY Debit Card` → View Design → **HMDC Attachment**，确认 BU/Role **均未选** → Save | 保存成功 |
| 2 | Portal 用 **`view_allowed`** / `password` 登录 | — |
| 3 | 进入 **Views** → MCY Debit Card | **不应**出现 HMDC Attachment；Case/Transaction 可见性见 T3/T5 |
| 4 | 退出，用 **`view_admin`** 登录 | — |
| 5 | 同上路径 | **应**看到 HMDC Attachment（及全部已发布 View） |

**Pass：** 非 admin 看不到 Attachment；admin 能看到。

---

### 场景 T3 — BU + Role AND（HMDC Case）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | （脚本已写规则）Case = BU `共享财务中心` + Role `Department Manager` | — |
| 2 | **`view_allowed`** 登录 → MCY Views | **可见** HMDC Case，可打开有数据 |
| 3 | **`view_wrong_bu`** 登录 | **不可见** HMDC Case（BU 为数字化部） |

**Pass：** 匹配用户可见；BU 错误不可见。

---

### 场景 T5 — 不完整配置不可见（历史仅 BU 数据）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | 若库中存在 **仅 BU、无 Role** 的 access 行（旧数据） | Portal 非 admin **不可见** 该 View |
| 2 | DW 打开该 View，补选 Role（须与 BU 成对）→ Save | Save 成功；Publish 后 Portal 按 AND 可见 |

**Pass：** 不完整配置对非 admin 不可见；DW 须成对保存。

---

### 场景 T5b — BU + Role 成对（HMDC Transaction）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | Transaction = BU `共享财务中心` + Role `Department Manager` | — |
| 2 | **`view_allowed`** 登录 | **可见** HMDC Transaction |
| 3 | **`view_wrong_bu`** 登录 | **不可见** HMDC Transaction |

**Pass：** 成对配置下，BU+Role 均匹配才可见。

---

### 场景 T4 — Role 不匹配 / 无 FU 门禁

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | **`view_wrong_role`** 登录 | MCY **不在** Views 菜单（FU 门禁：无 MANAGER） |
| 2 | **`view_nofu`** 登录 | 同上，无 MCY |

**Pass：** 无 FU 权限用户进不了 MCY View 菜单。

---

### 场景 T6 — 仅参与用户可见数据（非 admin）

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | 确认 Transaction View 已开 **`restrictToInvolvedUsers`**（脚本已开） | — |
| 2 | 确保库中有 MCY 流程实例：发起人/办理人 **不是** `view_allowed` 的记录若干条 | 可用现有种子数据 |
| 3 | **`view_allowed`** 打开 HMDC Transaction | 行数 **少于** admin 全量（仅参与相关流程） |
| 4 | 用 **`view_allowed`** 发起一条 MCY 流程后再看 Transaction | **多看到** 至少该发起记录 |

**Pass：** 参与过滤生效。

---

### 场景 T7 — SYS_ADMIN 全量数据

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | **`view_admin`** 打开 HMDC Transaction（已开参与限制） | 行数 = **全 FU 全量**（不受参与过滤） |
| 2 | 对比 **`view_allowed`** 同行数 | admin **≥** allowed |

**Pass：** SYS_ADMIN 绕过 `restrictToInvolvedUsers`。

---

### 场景 DW — 设计态 Save 回显

| 步骤 | 操作 | 期望 |
|------|------|------|
| 1 | DW **`developer`** 登录 → FU 48 → View Design → HMDC Case | — |
| 2 | 访问控制选 BU + Role → **Save** → 刷新页面 | BU/Role/开关 **回显正确** |
| 3 | 右侧说明文案 | 含「BU 与 Role 均未配置时，仅 System Administrator…」 |

---

## 4. API 快速校验（可选）

```powershell
# 需先登录 Portal 拿到 Cookie，或经 Kong 带会话调用
# GET /api/portal/main-table-views/function-units
# GET /api/portal/main-table-views/function-units/fu-20260505-thwmut/views
```

对比不同用户返回的 `viewCount` / 列表项 ID 是否与上表一致。

---

## 5. 恢复 / 重置

重新执行 `01-view-access-test-users.sql` 可重置 MCY 三条 View 的 access 规则。  
若在 DW 手工改过 View，Save 会覆盖 `dw_main_table_view_access` 并置为 DRAFT，需 **Publish FU** 后 Portal 才读 PUBLISHED 快照（若你们环境以 status 为准，Publish 后再测）。

---

## 6. 常见问题

| 现象 | 排查 |
|------|------|
| 所有 View 都看不到 | FU 未 Publish / `sys_function_unit_access` 无 MANAGER / 用户无 UBR |
| admin 也看不到 Attachment | 未用 `view_admin` 或 `developer`；检查 SYS_ADMIN 虚拟组 |
| `view_admin` 权限自助 / access_denied | 无 UBR → 重新执行 SQL 并 **退出重登**；勿用旧 JWT |
| Views 菜单为空 | 重建 user-portal（见下）；FU 门禁按 **catalog UUID** 查 access，code 须 resolve |
| 重建 user-portal | 在**仓库根目录**执行：`mvn -pl backend/user-portal -am package -DskipTests`，再 `docker compose ... up -d --build user-portal` |
| 改 DW 后 Portal 不变 | View config 仍为 DRAFT；Publish FU 或确认 status=PUBLISHED |
| Role 下拉 No data | 须先选 BU；选项来自该 BU 准入角色 API，非全局 `/roles` |
| Save 提示 BU/Role 成对 | 只填一侧时不允许保存；两者均空表示仅 SYS_ADMIN |
| 登录失败 | `developer-workstation` / `user-portal` 容器是否 healthy |
