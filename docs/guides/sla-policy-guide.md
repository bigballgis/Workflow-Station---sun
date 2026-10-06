# SLA 到期日 — 使用与实现说明

> 案件到期日由平台按 **到期日 = 开始日 + 时效天数（自然日）** 自动计算；时效天数可在生产由管理员修改，
> 修改后自动重算所有未结案件。
> 最后更新：2026-10-06

## 目录

1. [概念与分工](#1-概念与分工)
2. [设计人员：在 DW 配置字段映射](#2-设计人员在-dw-配置字段映射)
3. [管理员：在 Admin Center 设置时效天数](#3-管理员在-admin-center-设置时效天数)
4. [业务用户：门户中的表现](#4-业务用户门户中的表现)
5. [行为规则](#5-行为规则)
6. [部署](#6-部署)
7. [实现参考](#7-实现参考)
8. [已知限制](#8-已知限制)

---

## 1. 概念与分工

配置分两半，归属不同：

| 部分 | 内容 | 维护人 / 位置 | 生命周期 |
|---|---|---|---|
| **字段映射** | 开始日取哪个字段（或提交时间）、到期日写到哪个字段 | 设计人员，Developer Workstation → Table Design | 设计期配置，随功能单元导出 / 导入 / 克隆 / 版本回滚 |
| **时效天数** | 整数 1–3650，按功能单元 | 管理员，Admin Center → SLA 策略 | 环境数据，**不**随功能单元导出，每个环境各自维护 |

只有两半都配齐时才会计算到期日。

---

## 2. 设计人员：在 DW 配置字段映射

1. 打开功能单元 → **Table Design** → 选中**主表**（PROCESS 表单 PRIMARY 绑定的那张表）。
2. 确保主表上有一个 **DATE** 类型、非公式的字段用来存到期日（如 `due_date`），没有就先 **Add Field**。
3. 字段列表最下方的虚拟行 **SLA Due Date** → 点 **Configure**。
4. 在弹窗中设置：
   - **开始日来源**
     - **主表字段**：再选一个 DATE / TIMESTAMP 字段（TIMESTAMP 只取日期部分）；
     - **提交时间**：以案件发起时间为开始日。
   - **到期日字段**：选第 2 步的 DATE 字段（不能与开始日同一字段）。
5. **确认** → 表上点 **Save**。
6. 若到期日字段已放进表单：在 Form Design 中**重新保存一次表单**，门户上该字段才会显示为只读
   （服务端无论如何都会覆盖该值，见 §5）。
7. 按常规流程部署功能单元。

注意：

- 子表不显示 SLA 行；保存时后端也会拒绝非主表的映射。
- 在字段网格里**改名**开始日 / 到期日字段时，映射会自动跟随新名字。
- **删除**被映射的字段后，保存会被拒绝并提示字段名，需回到弹窗重新选择。
- 取消 SLA：弹窗中点 **移除** → 保存表。

保存时的校验（失败返回 400，提示走三语 i18n）：

| 错误码 | 含义 |
|---|---|
| `TABLE_SLA_MAIN_ONLY` | 只能配置在主表 |
| `TABLE_SLA_START_SOURCE_REQUIRED` | 未选择开始日来源 |
| `TABLE_SLA_FIELD_MISSING` | 映射的字段不在本表 |
| `TABLE_SLA_DUE_TYPE` | 到期日字段不是 DATE |
| `TABLE_SLA_DUE_COMPUTED` | 到期日字段是公式字段 |
| `TABLE_SLA_START_TYPE` | 开始日字段不是 DATE / TIMESTAMP |
| `TABLE_SLA_SAME_FIELD` | 开始日与到期日为同一字段 |

---

## 3. 管理员：在 Admin Center 设置时效天数

入口：左侧菜单 **SLA 策略**（`/admin/sla-policies`）。

### 3.1 列表

列出所有**主表声明了 SLA 映射**的功能单元，支持共享列表的列筛选 / 排序 / 列宽 / 分页 / 导出。

- **时效天数**为「未设置」→ 该功能单元暂不计算到期日。
- **重算状态**为最近一次重算任务的状态。

### 3.2 设置 / 修改天数

**编辑** → 输入整数 1–3650 → 可填写修改原因（进审计）→ **保存**。

- 保存后后台自动重算该功能单元所有**未结案件**（`RUNNING` / `SUSPENDED`）的到期日；
  已完成、已撤回的案件不变。
- 提示「已保存，但重算未能启动」：天数已生效，只是门户暂时不可达；门户恢复后点 **重算** 补跑。

### 3.3 手动重算

**重算** → 确认 → 按**当前**天数重跑一遍。用于补跑失败的任务或核对数据。

### 3.4 详情抽屉

- **重算任务**：每次任务的总数 / 更新 / 未变 / 跳过 / 失败、完成时间、错误信息（最近 20 次）。
- 点击某个任务 → 列出被**更新 / 跳过 / 失败**的案件，含旧到期日、新到期日、原因（最多 500 条；未变的案件只计数）。
- **修改历史**：时间、旧值 → 新值、版本、修改人、原因、重算是否启动（最近 50 次）。

### 3.5 权限

| 角色 / 权限 | 能力 |
|---|---|
| SYS_ADMIN / SUPER_ADMIN | 全部 |
| `sla:policy:edit` | 查看 + 修改天数 + 重算 |
| `sla:policy:view` | 只读（种子数据默认授予 AUDITOR） |
| AUDITOR | 即使被授予 edit 也**不能**写 |

后端由 `SlaPolicyAccessInterceptor` 强制校验（无权限返回 403 `PERM_ACCESS_DENIED`），前端按同样的权限隐藏菜单和按钮。
AUDITOR 角色在 dev 里通过虚拟组 `AUDITORS`（`vg-auditors`）授予；直接写 `sys_user_roles` 不会进入令牌。
新授予的权限需要用户**重新登录**才生效。

### 3.6 审计

| 操作 | `admin_audit_logs` 记录 |
|---|---|
| 修改天数 | `UPDATE` / `SLA_POLICY` / 功能单元编码，含修改前后的策略快照 |
| 手动重算 | `CREATE` / `SLA_POLICY`，`{"functionUnitCode": …, "operation": "recalculate"}` |

可在 **审计日志 → Admin Center** 按资源类型「SLA 策略」筛选。另有 `ac_sla_policy_history` 记录每次修改与对应的重算任务。

---

## 4. 业务用户：门户中的表现

- 发起或保存案件时，到期日由服务端计算并写入；用户无需也无法修改（前端传入的值会被覆盖）。
- 管理员修改天数后，未结案件的到期日在后台批量更新，刷新即可看到。
- 报表（Superset）直接读取同一数据（`up_process_instance.variables`），重算完成即可查询到新值。

---

## 5. 行为规则

| 情况 | 结果 |
|---|---|
| 开始日 + 天数都有 | 写入 `yyyy-MM-dd` 格式的到期日，覆盖客户端传入的值 |
| 配了映射但未设天数 | **不计算，保留原值**（映射的可能是已有业务字段，不会清空） |
| 开始日为空 / 不是日期 | 保留原值；重算任务中记为「跳过」并写原因 |
| 功能单元未配置映射 | 完全不处理 |
| 连续多次修改天数 | 旧任务标「已被取代（SUPERSEDED）」，以最后一次为准 |
| 同一功能单元首次被多人同时设置 | 依次生效，历史版本连续，不会报错 |
| 重算时有人同时编辑同一案件 | 乐观锁冲突自动重试 1 次；仍冲突则记为失败，可点「重算」补跑 |
| 门户实例在任务中途重启 | 心跳超过 5 分钟的任务在下次启动时标为「失败（已中断）」，点「重算」补跑 |
| 任务状态 `PARTIAL` | 存在跳过或失败的案件，查看详情中的原因 |

服务端写入到期日的 6 个写路径：流程发起、流程表单保存、任务表单保存、任务快照、审批完成回写、视图 CSV 导入。

---

## 6. 部署

### 6.1 数据库脚本

新库初始化（`00-init-all.sh` / `init-database.ps1` / k8s 离线包）会自动执行；**已有环境需手动执行**：

```bash
docker exec -i platform-postgres-dev psql -U <user> -d <db> -f /docker-entrypoint-initdb.d/00-schema/88-ac-sla-policies.sql
```

```bash
docker exec -i platform-postgres-dev psql -U <user> -d <db> -f /docker-entrypoint-initdb.d/00-schema/89-dw-table-sla-config.sql
```

```bash
docker exec -i platform-postgres-dev psql -U <user> -d <db> -f /docker-entrypoint-initdb.d/00-schema/90-up-sla-recalc-jobs.sql
```

```bash
docker exec -i platform-postgres-dev psql -U <user> -d <db> -f /docker-entrypoint-initdb.d/01-admin/12-sla-policy-permissions.sql
```

- `docker exec` **必须带 `-i`**，否则 SQL 不会被执行且没有任何报错。
- **90 号脚本必须用普通 `psql -f` 执行，不能加 `-1` / `--single-transaction`**：
  `idx_up_pi_fu_status_id` 以 `CONCURRENTLY` 方式创建，以免锁住生产的 `up_process_instance`。
  若创建被中断，按脚本注释检查 `indisvalid`，为 `false` 时 `DROP INDEX CONCURRENTLY` 后重跑。
- GUI 离线包（`deploy/k8s/init-data/init-platform-schema/all-in-one-for-gui.sql`）只用于空库，
  其中该索引为普通 `CREATE INDEX`。

### 6.2 配置

| 变量 | 服务 | 说明 |
|---|---|---|
| `PORTAL_INTERNAL_API_TOKEN` | admin-center + user-portal（两边值必须一致） | admin-center 调用门户内部接口启动重算；未配置时修改天数后会显示「启动失败」 |
| `USER_PORTAL_BASE_URL` | admin-center | 门户内部地址（已有配置，沿用） |

UAT 的 `deploy/k8s/secret/uat/secret-workflow-platform.yml` 中为占位值 `CHANGE_ME_UAT_PORTAL_INTERNAL_API_TOKEN`，需运维填入真实值。

---

## 7. 实现参考

### 7.1 数据流

```
DW Table Design ──保存──▶ dw_table_definitions.sla_config        （映射，设计期）
Admin SLA 策略  ──保存──▶ ac_sla_policies / ac_sla_policy_history（天数，环境数据）
                └─提交后─▶ POST user-portal /internal/sla/recalc-jobs（X-Internal-Token）
                                  └─▶ SlaRecalcJobService（单线程，逐案独立事务）
                                        └─▶ up_process_instance.variables[dueDateField]
                                        └─▶ up_sla_recalc_jobs / up_sla_recalc_job_items
门户写路径 ───────▶ SlaDueDateEnricher.stamp（读映射 + 当前天数）──▶ variables[dueDateField]
```

映射的定位规则与 Request ID 相同：功能单元 PROCESS 表单的 PRIMARY 绑定表。

### 7.2 数据表

| 表 | 归属 | 用途 |
|---|---|---|
| `dw_table_definitions.sla_config` (JSONB) | DW | `{"startDateSource": "FIELD" \| "SUBMITTED_AT", "startDateField": "...", "dueDateField": "..."}` |
| `ac_sla_policies` | Admin | 每个功能单元一行：`lead_time_days`（CHECK 1–3650）、`version` |
| `ac_sla_policy_history` | Admin | 每次修改：前后天数 / 版本、修改人、原因、`recalc_job_id`、`dispatch_status` |
| `up_sla_recalc_jobs` | Portal | 重算任务：状态、计数、心跳；保留 90 天 |
| `up_sla_recalc_job_items` | Portal | 每案结果（只存 UPDATED / SKIPPED / FAILED） |

时间列统一为 `TIMESTAMPTZ`（门户 JVM 为 Asia/Shanghai、admin-center 为 UTC，避免两边口径不一）。

### 7.3 接口

Admin Center（前缀 `/api/v1/admin`）：

| 方法 | 路径 | 权限 |
|---|---|---|
| POST | `/sla-policies/query` | view |
| PUT | `/sla-policies/{functionUnitCode}` | edit — body `{"leadTimeDays": 30, "changeReason": "..."}` |
| POST | `/sla-policies/{functionUnitCode}/recalculate` | edit |
| GET | `/sla-policies/{functionUnitCode}/history` | view |
| GET | `/sla-policies/{functionUnitCode}/jobs` | view |
| GET | `/sla-policies/{functionUnitCode}/jobs/{jobId}/items` | view |

User Portal 内部接口（`/api/portal`，仅限服务间调用）：`POST /internal/sla/recalc-jobs`，
body `{"functionUnitCode", "policyVersion", "triggeredBy"}`，请求头 `X-Internal-Token`。

派发失败时，历史与响应中只返回错误码 `PORTAL_DISPATCH_FAILED`；完整异常仅写入 admin-center 日志。

### 7.4 主要代码

| 模块 | 文件 |
|---|---|
| DW 后端 | `dto/SlaConfig.java`、`service/SlaConfigValidator.java`；导出 / 导入 / 克隆见 `FunctionUnitExporter` / `FunctionUnitImportWriter` / `FunctionUnitCloner` |
| DW 前端 | `components/designer/SlaConfigDialog.vue`、`utils/slaConfigFields.ts`（改名跟随）、`utils/formFieldMeta.ts`（`markSlaDerivedFields` 只读） |
| Admin 后端 | `controller/SlaPolicyController.java`、`component/SlaPolicyComponent.java`、`component/PortalSlaRecalcClient.java`、`config/SlaPolicyAccessInterceptor.java`、`list/SlaPolicyColumnSpec.java` |
| Admin 前端 | `views/sla-policy/`、`composables/modules/useSlaPolicies.ts`、`api/slaPolicy.ts` |
| Portal 后端 | `component/SlaDueDateEnricher.java`、`component/SlaRecalcJobService.java`、`controller/InternalSlaRecalcController.java` |
| SQL | `deploy/init-scripts/00-schema/88-…`、`89-…`、`90-…`，`01-admin/12-sla-policy-permissions.sql` |

### 7.5 排查

```sql
-- 某功能单元的映射
SELECT td.sla_config FROM dw_function_units fu
JOIN dw_form_definitions fd ON fd.function_unit_id = fu.id AND fd.form_type = 'PROCESS'
JOIN dw_form_table_bindings ftb ON ftb.form_id = fd.id AND ftb.binding_type = 'PRIMARY'
JOIN dw_table_definitions td ON td.id = ftb.table_id
WHERE fu.code = '<fuCode>';

-- 最近的重算任务与失败明细
SELECT status, total_count, updated_count, skipped_count, failed_count, error_message, finished_at
FROM up_sla_recalc_jobs WHERE function_unit_code = '<fuCode>' ORDER BY submitted_at DESC LIMIT 5;

SELECT outcome, process_instance_id, old_due_date, new_due_date, reason
FROM up_sla_recalc_job_items WHERE job_id = '<jobId>' ORDER BY id;
```

门户对映射有 5 分钟缓存：重新部署功能单元、修改了映射后，最多 5 分钟生效（或重启 user-portal）。天数不缓存，修改后立即生效。

---

## 8. 已知限制

- 只按**自然日**计算，没有工作日 / 节假日日历。
- 每个功能单元只有一个时效天数，不支持按案件类型或优先级分档。
- 不联动任务级到期日（`ExtendedTaskInfo.dueDate`）、BPMN 定时器和逾期通知；
  Flowable 引擎中的流程变量不会同步重算后的值。
- 已结案件（COMPLETED / WITHDRAWN 等）保留结案时的到期日，不参与重算。
- 映射依赖运行时读取 `dw_*` 设计表（与 Request ID、公式字段相同）；生产环境 `dw_*` 数据来源待运维确认。
