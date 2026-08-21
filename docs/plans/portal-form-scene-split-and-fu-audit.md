# 设计 Plan — Portal 表单场景拆分 + FU 级审计入口 + View 详情页

> 状态：待评审
> 日期：2026-08-14
> 模块：portal · dw · admin · deploy
>
> 已经过 5 轮 review（一致性 / 实现者 / 运维 / 前端视角）。
> 未闭合项见文末【实现前须先确认】。

---

## 【背景 / 问题】

DW 的 Form Design 产出的表单，在 user-portal 的四个场景里共用一套渲染管线
（`FormRenderer.vue` + `GET /processes/function-units/{id}/content`），差异全靠
`viewContext`（`assigneeTodo` / `initiatorRequest`）+ `readonly` 两个 prop 支撑。

结构上其实**已有 per-task form**：TASK form 按 BPMN 节点挂载
（两条并行链路 —— `dw_form_stage_bindings` 表与 BPMN `custom:property`，详见议题一），
每个 form 有独立的 table binding 与 subForms。真正无处安放的是三类「非任务态」呈现：

1. **My Request** 不对应任何 userTask → 无处挂自己的设计 → 被迫用
   `subTablePortalViews.initiatorRequest` 在同一份 form 里塞第二套子表视图配置
   （两级 merge、10+ 分支点，即用户反馈的「很麻烦」）。
2. **View 点行详情** 同样不对应 task → 现在直接跳 `applications/detail.vue`，无独立设计。
3. **审计人员** 既非发起人也非流程参与者 → My Request 只列「我发起的」，To Do 只列
   「派给我的」→ **没有任何入口**能看到某 FU 下全部 request 并留 note。

### 现状渲染矩阵（调查结论）

| 场景 | 页面 | viewContext | readonly |
|---|---|---|---|
| New Request | `views/processes/start.vue` | 默认 `assigneeTodo` | 默认 false |
| To Do | `views/tasks/detail.vue` | `assigneeTodo` | 动态 |
| Completed | **同上**（+ snapshot query） | `assigneeTodo` | 强制 true |
| My Request | `views/applications/detail.vue` | `initiatorRequest` | **硬编码 true** |

---

## 【目标】

1. My Request 拥有独立于 To Do 的**完整可设计 form**（画布、子表、布局形态对等），
   骨架（流程图 / 节点快照 / Change History / Flow History）保持不变。
2. 新增 **FU 级审计入口**：被授权角色可看该 FU 下全部 request，内容与 My Request 完全一致。
3. View 点行进入**独立设计的只读详情页**。
4. 移除 `subTablePortalViews` 双轴配置。

---

## 【非目标】（本阶段不做）

- Completed 独立 form 设计 —— 已确认与 To Do 共用 `scene=TASK`。
- 参与者判定两套实现的统一 —— `ProcessApplicationQueryComponent.isProcessParticipant`
  与 `MainTableViewInvolvementChecker.isUserInvolved` 逻辑不一致，属既有技术债。
- `dw_function_unit_access` 孤儿表清理 —— 零调用点，与本次无关。
- 审计人与申请人 note 流隔离 —— 已确认**共享同一条流**，零改动。
- DETAIL 页的 Change History —— 已确认**不做**，View 详情只要表单。

---

## 【方案】

### 议题一：My Request 独立设计

- **方案 A**：给「form↔挂载点」加一根 `scene` 轴（`TASK` | `REQUEST`），
  同一挂载点可挂两份完整 form 设计。**因挂载有三处载体，三处都要加**（详见下方两小节）。
- **方案 B**：新 `form_type='DETAIL'` 承载 My Request。
  **拒**：My Request 要保留流程图 + 节点快照骨架，与「无骨架详情页」是不同物种。
- **推荐 A** —— 复用现有挂载机制，per-node 粒度天然满足「每个节点不同展示」。

**scene 轴的三处载体（缺一不可）**

| # | 载体 | 服务对象 | 改动 |
|---|---|---|---|
| 1 | `dw_form_stage_bindings` | per-node TASK form（链路 A） | 加 `scene` 列，唯一约束 `(form_id, stage_id)` → `(form_id, stage_id, scene)` |
| 2 | BPMN `custom:property` | per-node TASK form（链路 B） | task 元素加 `scene` 属性 |
| 3 | `dw_form_definitions` | per-FU PROCESS form | 加 `scene` 列，PROCESS 唯一性改按 scene 计数 |

**发起步骤**：PROCESS form 不是 userTask，没有 BPMN 节点 id 可挂。
**不进 `dw_form_stage_bindings`** —— 该表语义是「form 绑到 BPMN 节点」，塞入不存在于任何
BPMN 的假 stage id（如 `__PROCESS_START__`）会与现有按节点 id 反查 / 校验的逻辑冲突。

改为在 `dw_form_definitions` 上用 `(function_unit_id, form_type='PROCESS', scene='REQUEST')`
组合标识（即上表载体 3）；`PROCESS_FORM_ALREADY_EXISTS` 校验从「每 FU 唯一」
改为「每 FU 每 scene 唯一」。

#### form↔节点映射有两条并行链路，scene 必须都覆盖（已确认）

调查证实两条链路**都在实际使用**，且主力 demo 用的是后者：

| 链路 | 存储 | 实际用户 |
|---|---|---|
| A. stage binding 表 | `dw_form_stage_bindings(form_id, stage_id, read_only)` | FU 16（`MI_UserTask_<tableId>` 形态） |
| B. BPMN 扩展属性 | task 元素上 `custom:property name="formId"` | **FU 50005 / 48 / 24 —— 均无 stage binding 行** |

因此 scene 轴**两条都要带**：表加 `scene` 列，BPMN 扩展属性加 `scene` 属性。
运行时解析需按 scene 在两条链路上一致地反查，否则同一节点会解析出不同 form。

**这是本议题最大的实现风险** —— 漏掉链路 B，50005 这类主力 FU 的 scene 完全不生效。

##### 链路 B 有**四份独立实现**：DW 两份 + portal 两份

| 端 | 文件 | 说明 |
|---|---|---|
| DW | `utils/bpmnFormBindings.ts` | 解析 |
| DW | `utils/bpmnFormBindingUpdate.ts` | 写回 |
| **portal** | **`useApplicationDetailNodeFormMap.ts:130-146`** | **My Request，手写 DOM 遍历** |
| **portal** | **`useTaskDetailNodeFormMap.ts:86-122`** | **To Do，另一份手写 DOM 遍历** |

**漏掉 `useApplicationDetailNodeFormMap.ts` → My Request 永远解析到 TASK form，
scene 对 REQUEST 侧完全静默失效** —— 验收正例 2 不可能通过。

**兜底会跨 scene 误匹配**：两处都是「先按 `sourceId` 匹配，失败再按 `formName` 匹配」
（`:146-148` / `:122`）。TASK 版与 REQUEST 版**可能同名** → 静默错配、无报错。
加 scene 后必须让 formName 兜底也带 scene 判别，或直接移除该兜底。

**既有差异**：`useTaskDetailNodeFormMap.ts:98-115` 支持 `subProcess` 下钻，
`useApplicationDetailNodeFormMap.ts:126` **不下钻** —— FU 50005 有 2 个 userTask 在 MI 子流程内，
My Request 侧对 MI 子任务的解析本就比 To Do 弱，加 scene 会放大这个差异。

**未配置 REQUEST form**：**不回退**，显示中性文案「未配置此场景的表单设计」
（**不用**「My Request 表单」措辞 —— 审计入口复用同一详情页，审计人并非在看自己的 request）。

### 议题二：审计授权

- **方案 A**：复用 Main Table View 授权（`canAccessProcessDetail` 已内建 view 放行路径）。
  代价：审计权与 view 可见性耦合，且 `restrict_to_involved_users=false` 会同时放开该 view 行数据。
- **方案 B**：新建 `sys_function_unit_audit_access` 表。**← 采纳**
- **方案 C**：扩展 `sys_function_unit_access.access_type='AUDIT'`。**必须排除**，见下。

#### 为何排除方案 C（关键证据）

`FunctionUnitAccessComponent.java:571-580` 只过滤 `targetType == "ROLE"`，
**全代码库无一处按 `access_type` 过滤**（`DEVELOPER` 值从未被写入或读取，
`USER` 是 `FunctionUnitAccessService.java:73,:132` 的硬编码字面量）。

新增 `AUDIT` 行会被当作**发起权**，审计角色将出现在 New Request 列表。
需同步修补**跨两服务五处读取点**：

1. `canAccessFunctionUnit`（`FunctionUnitAccessComponent.java:85-109`）
2. `checkFunctionUnitAccess`（同上 `:276-284`）
3. `filterAccessibleFunctionUnits`（同上 `:338-365`，New Request 列表）
4. `getFunctionUnitAllowedRoles`（同上 `:515-550`，权限目录）
5. admin-center `hasAccess`（`FunctionUnitAccessService.java:203-222`）

而 `access_type` 是**无 CHECK 约束的裸 String** —— 漏改无编译错误、无测试失败、
**fail-open 静默提权**。

附带缺陷：`existsByFunctionUnitIdAndRoleId` 不含 access_type → 同角色无法同时持 USER+AUDIT；
`copyAccessFromSiblingVersions` 去重同缺 → 重部署静默丢行。

#### 放行粒度：另开判据方法，**不改** `canAccessProcessDetail`（已确认）

`canAccessProcessDetail` 是共用判权方法，当前至少四处调用：
`GET /processes/{id}`、`GET /processes/{id}/history`、
`RecordNoteComponent.checkAccess`、`RecordNoteComponent.requireArchiveAccess`。

**若直接给它加审计分支，四处全部放行** —— 包括 N5 要求拒绝的 archive zip 导出。
代码将与验收标准直接冲突。

因此新增独立判据 `canAuditProcessDetail(userId, detail)`：

```
canAuditProcessDetail = canAccessProcessDetail(原逻辑，不动)
                        || 用户持有该 detail 所属 FU 的审计授权
```

**共 6 个生产调用点**（核实确认，非 3 个），逐个判定：

| # | 调用点 | 用哪个 | 理由 |
|---|---|---|---|
| 1 | `ProcessController.java:284` `GET /processes/{id}` | `canAuditProcessDetail` | 审计人要进详情 |
| 2 | `ProcessController.java:396` 详情类接口 | `canAuditProcessDetail` | Flow History 要看 |
| 3 | `RecordNoteComponent.java:253` 笔记访问 | `canAuditProcessDetail` | 审计人要读写 note |
| 4 | `RecordNoteComponent.java:150` `requireArchiveAccess` | **不动** | 审计人不得导出（N5） |
| 5 | `RecordNoteComponent.java:137` `adoptDraftNotes` | **不动** | **草稿转正是写操作**，审计人不该有 |
| 6 | `RecordNoteComponent.java:196` `resolveAuditInstanceId` | **不动** | 审计归属判定，非访问控制 |

转发层 `ProcessComponent.java:212-213` 需同步暴露新方法。
另有 5 处测试断言需同步：`ProcessApplicationQueryComponentDetailAccessTest.java:63-125`、
`RecordNoteAuditTest.java:102,118`。

**#5 是盲目全替换会踩的坑** —— `adoptDraftNotes` 把草稿笔记锚定到正式实例，是写操作。

**这是 fail-closed 设计**：今后新增的接口若调旧方法，审计人默认无权限；
要放行必须显式改为新方法。与议题二选新建表（而非扩展 `access_type`）
的理由同源 —— 默认拒绝优于默认放行。

#### 必须自带分支级日志（现状是排障黑洞）

核实确认：`canAccessProcessDetail`（`ProcessApplicationQueryComponent.java:580-624`）
**六条 `return false` 路径全部无日志** —— 不是参与者？无 PUBLISHED view？
BU/Role 不匹配？`restrict_to_involved` 且未参与？**运维完全无法区分**。
调用方仅有一行 `log.warn("User {} attempted to access process {} …")`（`ProcessController.java:285`），
说不出被哪一环拒绝；`RecordNoteComponent.java:138/151` 直接抛 FORBIDDEN，**连 warn 都没有**。

`canAuditProcessDetail` **必须为每条拒绝分支记明确原因**
（无审计授权 / 授权的 FU 不匹配 / FU code 缺失），否则会复制同一个黑洞 ——
审计场景比普通查看更需要可追溯性。

#### 授权变更的生效延迟

- `sys_function_unit_audit_access` 若照 `getFunctionUnitAllowedRoleCodes` 的
  **live fetch** 模式（`FunctionUnitAccessComponent.java:559` 注释明确不缓存）→ **配好即生效**。
- 但判定审计人需读**用户侧角色**，会命中 `userRoleCodesCache`（TTL **5 分钟**，`:63`）→
  **「刚给用户加了审计角色但 5 分钟内不生效」会真实发生**。
  已有缓解：授权变更时调 `clearUserRolesCache(userId)`（`:596-599`），或明确接受 5 分钟窗口。
- **降级风险**：`getUserRoleCodesForProfile` :460-464 异常时返回空集 ——
  admin-center 挂掉时静默降级为「无任何角色」即**拒绝一切**，表现为「审计人突然全被拒」，
  且仅一行 `log.error`、无告警。

### 议题三：View 详情页

新 `form_type='DETAIL'` + 新页面，**仅 DETAIL form 只读渲染，无 Change History**。

**挂载：每个 view 挂一份自己的 DETAIL form**（已确认）。

`dw_main_table_view_configs` 加 `detail_form_id BIGINT REFERENCES dw_form_definitions(id) ON DELETE SET NULL`。
View Design 面板加「详情表单」选择器（可新建 / 可选已有 / 可留空）。
因 view 是 per-table（MAIN 与 SUB 都能建），主表 view 与子表 view 可各自设计不同详情页。

**未配置 detail_form_id**：与议题一同口径 —— **不回退**，点行进入后显示
「未配置此视图的详情表单」。`ON DELETE SET NULL` 保证 form 被删时 view 不悬空。

**无 view 的 FU**：已确认**不特殊处理**。View 详情页本就从 view 点行进入，
没配 view 的 FU 天然没有这个入口（现状 9 个 FU 中仅 2 个配了 view）。需要时先建 view。

**跨环境引用用 code，不用 id**（已确认）：导出时写 form 的 **code / name**，
不写数字 id —— 与现有名称化模式一致（`MainTableViewPortability` 用 `mainTableName`、
access 用 `targetCode`）。详见【数据与契约】的导入顺序小节。

连带效果：不依赖 `processInstanceId`，子表 view 点行同样可用；
`openRow` 跳转条件放宽，顺手补掉现在的静默无响应。

---

## 【安全前置：批次 0】

**与本功能无关，现在就存在。已确认先修洞再做功能。**

| 接口 | 位置 | 问题 |
|---|---|---|
| `GET /processes/{id}/change-history` | `ChangeHistoryController.java:30-39` | 无 `@CurrentUserId`，下游签名不收 userId，**不可能判权** |
| `GET /processes/{id}/change-history/sensitive-masks` | `ChangeHistoryController.java:41-46` | 同上（`ChangeHistorySensitiveMaskResolver.resolveByProcessInstanceId(String)` 只收 pid） |
| `GET /processes/{id}/form` | `ProcessFormController.java:30-37` | 同上 |

任何已登录用户拿任意 `processInstanceId`（URL 明文可见）即可读他人数据。
`PortalSelfServiceAccessFilter` 对 GET 直接放行（`:189-191`）。

**修法**：两个 controller（三个端点）加 `@CurrentUserId`，
调 `processComponent.canAccessProcessDetail`。下游 component 方法签名需一并加 userId
（`ChangeHistoryComponent.getChangeHistory(String,String,String)` 与
`ChangeHistorySensitiveMaskResolver.resolveByProcessInstanceId(String)` 当前均**不收 userId**）。

**`PortalSelfServiceAccessFilter` 不动** —— filter 与 controller 判权是两层职责，
filter 保持对 GET 放行即可；顺手改它会无谓扩大回归面。

**独立提前的理由**：修洞是**收紧**权限、回归面覆盖所有详情页 / 任务页 / My Request；
本功能是**放宽**权限。混在一起时任何 403 都无法归因。

### 批次 0 回归面（收紧后最易炸的点）

| 调用场景 | 位置 | 参数分支 |
|---|---|---|
| My Request 详情 Change History tab | `applications/detail.vue:349-355` | 基础 |
| To Do / Completed 详情 | `tasks/detail.vue` | 带 `taskId`；Completed 走 snapshot 路由 |
| 子表行变更过滤 | 同上 | 带 `rowIdentifier` |
| 敏感字段掩码 | `/change-history/sensitive-masks`（`ChangeHistoryController.java:41-46`） | **同样无 userId，需一并修** |
| 表单数据 | `ProcessFormController.java:30-37`，My Request 详情主数据源 | 基础 |

`ChangeHistorySensitiveMaskResolver.resolveByProcessInstanceId(String)` 同样只收
processInstanceId，属同一批。

---

## 【影响面】

| 层级 | 变更 |
|------|------|
| **Entity / SQL** | 新 `00-schema/65-*.sql`（append-only，当前最大编号 `64-`）：<br>① `dw_form_stage_bindings` 加 `scene VARCHAR(16) NOT NULL DEFAULT 'TASK'` + CHECK；**先 `DROP CONSTRAINT IF EXISTS dw_form_stage_bindings_form_id_stage_id_key`** 再建 `(form_id, stage_id, scene)` 唯一约束<br>② **`dw_form_definitions` 加 `scene VARCHAR(16) NOT NULL DEFAULT 'TASK'` + CHECK**（承载 PROCESS form 的 REQUEST 版本；存量行由 DEFAULT 归为 TASK）<br>③ 新表 `sys_function_unit_audit_access`，照 `sys_function_unit_access`（`01-platform-security-schema.sql:638-649`）—— 注意 admin 侧用 **`VARCHAR(64)`** 主键/FK，非 BIGINT<br>④ `dw_form_definitions.form_type` CHECK 放开 `'DETAIL'`<br>⑤ **`dw_main_table_view_configs` 加 `detail_form_id BIGINT REFERENCES dw_form_definitions(id) ON DELETE SET NULL`**<br>⑥ **必须在 `00-init-all.sh` 追加 `65-*.sql` 一行**（见下） |
| **admin API** | 新 audit access CRUD（entity / repo / service / controller ~4 文件）；FU 删除级联加删；**不复用** `FunctionUnitAccessService` 以隔离提权风险 |
| **portal API** | ① 新 `GET /processes/audit-function-units` —— 返回当前用户有审计权的 FU 列表，**菜单显隐与 FU 选择器依赖它**（N6 验收前提）<br>② 新 `GET /processes/fu-applications?functionUnitCode=&status=`（复用已有 `findByFunctionUnitCodeOrderByStartTimeDesc`，需补 FU+status 分页组合）<br>③ **新增 `canAuditProcessDetail`**，在 3 处调用点替换（**`requireArchiveAccess` 不换**，见议题二放行粒度小节）<br>④ **`requireFunctionUnitContentAccess` 放行审计人**<br>⑤ **note 删除与编辑全面禁止**（见下） |
| **dw API** | `FormDesignComponentImpl` stage binding 校验带 scene；PROCESS 唯一性改按 scene 计数；`FormStageBindingController` 查询加 scene 参数 |
| **前端 dw** | 详见下方【前端改动面 · DW 设计器】—— 含**前端 PROCESS 唯一性校验**（阻塞项）、`FormNodeBindDialog` **props 破坏性变更**、form 列表 scene 列、BPMN 解析/写回两文件 |
| **前端 portal** | 详见下方【前端改动面 · user-portal】—— 含菜单**无权限机制**需新建模式、**两份手写 BPMN 解析必须都改**、路由守卫表达不了 per-FU 授权、新 View 详情页**路由契约待定** |
| **前端 admin** | `AccessConfigDialog.vue` 加 audit 授权区（或独立弹窗）+ `useFunctionUnitAccessConfig.ts` + `api/functionUnit.ts` |
| **i18n** | 三语（en / zh-CN / zh-TW）：审计菜单、空状态文案、audit 授权 UI |
| **部署** | 无新服务；schema 走 init-scripts |

### Note 不可变（新增决策，影响现有行为）

已确认：**note 一经写入，任何人都不能删除，也不能修改。**

| 端点 | 现状 | 改为 |
|---|---|---|
| `DELETE /record-notes/{id}` | owner 或 SYS_ADMIN 可软删（`RecordNoteComponent.java:105-108`） | **全面禁止** —— 移除端点或恒 403 |
| `PUT /record-notes/{id}` | owner 可改（`allowEditOwn` 开关 + 非作者抛 `NOT_OWNER`） | **全面禁止** |
| `GET /record-notes/archive/{pid}` | 参与者 / SYS_ADMIN 可导出 zip | **审计人不放行**（已确认不需要），维持现有参与者门 |

连带影响：

- DW 设计器的 `allowDelete` / `allowEditOwn` 两个组件 prop 失去意义
  （`main.ts:495-509` 属性面板），应从面板移除或标记为废弃。
- `ChangeType.RECORD_NOTE_UPDATE` / `RECORD_NOTE_DELETE` 两个审计类型将不再产生新记录
  （枚举保留以兼容存量数据）。
- `RecordNoteAuditTest` 中针对 update / delete 的断言需改为「应被拒绝」。
- 前端 `RecordNoteField.vue` 的编辑 / 删除按钮需移除。

### 前端改动面（前端视角核实补全）

原 Plan 前端只有两行概括，以下为核实后的实际改动面。

#### DW 设计器

| # | 改点 | 说明 |
|---|---|---|
| 1 | **`useFormLifecycle.ts:395-401`** | **前端也有一份 PROCESS 唯一性校验**。不改则「建第二份 PROCESS form」被前端弹窗直接挡掉，**验收正例 3 走不到后端**（阻塞项） |
| 2 | `FormNodeBindDialog.vue:22-50,:72-81` | props 现为 `isNodeSelected(id)` / `isNodeReadOnly(id)` / `setNodeReadOnly(id, readOnly)` —— **单键寻址**。加 scene 是**破坏性签名变更**，须改为 `(id, scene)`；`useFormNodeBinding.ts` 的选中集合数据结构同步改 |
| 3 | `FormListSidebar.vue:200-238` | 五列无 scene；`formType` 标签只有二值配色（`:33`），TASK/ACTION 已同色，加 DETAIL 更混；`boundNodeId` 列（`:66-84`）按 formId 反查**不带 scene** → **同节点两份 form 显示完全相同的节点标签** |
| 4 | 同上 `:198` | 列宽持久化 `storageKey = ${fuId}:forms`，**加列后存量用户 localStorage 与新列集不匹配**，须确认 `useDesignerListGrid` 迁移行为 |
| 5 | `useFormLifecycle.ts:93,:403-415,:450-455` | 新建对话框 `createForm` reactive 无 `scene` 字段；`stageBindings` 构造（`:410-415`）无 scene；`handleCreateFormTypeChange` 仅在 TASK 分支懒加载节点 |

**preview 无需改造**：`mapFormCreateRulesReadonlyDeep` 已在 `useFormPreviewBuild.ts:270,:342`
无条件调用，**DW preview 已全局只读**。

但**设计态只读 ≠ 运行态只读** —— portal 走 `FormRenderer` 的 `readonly` prop，
两条链路无关，「所见即所得」需单独验（参 `form-preview-fk-pk-runtime` 规则）。

**auto-save 竞态**：`useFormAutoSave.ts` 的 `suspended` 守卫（`:36,:52,:76,:96`）已封堵主竞态。
残留风险：`handleSelectForm` 在 `:233` 用 `nextTick + setTimeout(100)` 复位，
`relationViewState` deep watch（`:136-144`）异步 flush 若落在复位之后会触发一次无谓保存。
**scene 场景下 TASK/REQUEST 高频切换会反复穿越这个 100ms 窗口。**

#### user-portal

| # | 改点 | 说明 |
|---|---|---|
| 1 | `PortalLayout.vue:17-162` | **菜单无任何权限机制** —— 现有显隐只有 `showFullPortal`（11 项共用一个布尔）、`hasBiDashboards`、`viewFunctionUnits` 三种。审计入口最接近 **Views 子菜单模式**（`:139-161`），非简单菜单项 |
| 2 | 同上 `:335-342` | `onMounted` 现为 `syncPortalAccess` → `Promise.all([checkBi, loadViewFu])`。审计接口须并入 `Promise.all`，否则首屏多一个串行 RTT |
| 3 | 同上 | **必须 `v-if="auditFunctionUnits.length > 0"`** —— Views 子菜单本身始终渲染（`:139-141` 只判 `showFullPortal`），照抄会让无权用户**先看到菜单再消失**，违反 N6 |
| 4 | 同上 `:328-332` | 现有 `catch { viewFunctionUnits = [] }` 静默降级。审计接口 500 时菜单静默消失 —— 与本 Plan 对后端「要分支日志」的要求自相矛盾，须给可见错误态 |
| 5 | `router/index.ts:83-88,:185-239` | `applications/:id` **无 meta 权限**；守卫的 `requiredRoles` 是**静态角色名数组**（`:18`），**表达不了 per-FU 动态授权** → 审计列表页只能靠页面内 403，会先渲染空页再报错，**此状态需定义** |
| 6 | `applications/detail.vue:201,:294,:399` | **三处硬编码 `view-context="initiatorRequest"`** —— 该 prop 语义即「发起人视角」，审计人复用时语义不符 |

#### View 详情页路由契约（**未定义，须补**）

`viewId` **当前不在 URL 里** —— views 路由是 `views/:functionUnitCode?`
（`router/index.ts:143-148`），view 切换是页面内状态。
新详情页至少需要 `functionUnitCode + viewId + 行标识` 三段：

- 改现有 views 路由带 viewId → 影响 `PortalLayout.vue:150` 菜单 index 与 `:325-327` replace 逻辑
- 或详情页用 query 参数

**子表 view 的行主键可能是复合键**（父表 FK + 行序号），单值 `rowId` 表达不了。
本 Plan 声称「子表 view 点行同样可用」，但**未验证子表行可寻址** —— 实现前须先确认。

`MainTableViewDesigner.vue` 的「详情表单」选择器建议插在 `:593` `portalToolbar` 之后、
`:611` `accessControl` 之前（同属 portal 呈现配置）；须沿用 `.section-label` 样式
（`portal-dialog-form-labels` 规则）。

#### i18n 量化

**约 31–35 个新 key × 3 语 × 3 应用 = 9 个文件**：

| 应用 | 新增 key |
|---|---|
| portal：`menu.audit` + 审计列表页(~8-12) + 两处空状态(2) + View 详情页(~4) | ~15-19 |
| DW：scene 标签 / 选项 / 列头 / DETAIL 类型 / 绑定提示 | ~8 |
| admin：audit 授权 UI | ~8 |

三份 locale **已行数漂移**（en 1066 / zh-CN 1060 / zh-TW 1059），说明已有 key 未同步。

#### 前端测试覆盖（重要风险）

命中这条链路的单测**仅 3 个文件**：`mergeSubTablePortalViews.test.ts`、
`linkOnlySubTableFields.test.ts`、`withSubTableBindingIdInProps.test.ts`。

**双刃**：删除改动小（成本低），但**TASK 侧回归几乎没有测试网**。
「portal 905 全绿」**不构成批次 3 的安全证据** —— 905 个测试里只有 2 个碰这条链路。
批次 3 必须补 TASK 侧回归测试，或依赖人工验证 To Do 内联表单 / Link Form。

### 零改动项（调查已确认）

- **RecordNote readonly 豁免** —— `RecordNoteField.vue:259` 已 `provide(formContextKey, undefined)`
  切断 el-form disabled 传播；`FormRendererFields.vue:264/274` 硬编码 `:readonly="false"`。
  「只评论不改数据」现状即可用。
- **评论不进 Change History** —— `fieldName` 写为 `__record_note__`，
  被 `isInternalField` 的 `startsWith("__")` 过滤（`ChangeHistoryComponent.java:354`）；
  第二道门 `isUserVisible` 也拦。前端 `recordNoteAdd` 等标签映射是**死代码**。
  <br>注意：admin-center 全局审计用精确匹配黑名单，**不套前缀规则**，评论在那里可见。
- **评论流共享** —— 流键 `(TABLE, processInstanceId, tableId)` 不含用户 / form / scene，
  审计人与申请人天然同流。

---

## 【数据与契约】

### ⚠️ Schema 生效路径 —— 两个静默失败点（核实确认）

**① 新 SQL 文件放进目录不会自动执行。**
`00-init-all.sh:74-130` **不是通配符循环，是逐个编号硬编码的白名单**，最后一行止于 `64-*.sql`。
新建 `65-*.sql` 后**必须在该列表追加一行**，否则连全新环境都不会执行它 ——
文件静静躺着、DDL 从不生效、**且没有任何报错**。

**② init-scripts 只在首次初始化时执行。**
Docker Postgres entrypoint 语义：数据目录非空则整个 `docker-entrypoint-initdb.d` **完全跳过**
（`00-init-all.sh:5`；规则 `init-scripts-append-only.mdc:42-45`）。
**存量环境（dev / uat / preprod）必须手工执行**：
`docker exec … psql -f /docker-entrypoint-initdb.d/00-schema/65-….sql`，或删卷重建。

**③ 匿名唯一约束必须显式 DROP。**
`dw_form_stage_bindings` 的 `UNIQUE(form_id, stage_id)` 是**内联匿名约束**
（`16-add-decision-and-relations-tables.sql:73-80`），Postgres 自动命名为
`dw_form_stage_bindings_form_id_stage_id_key`。不 DROP 它，同一节点就配不了
TASK + REQUEST 两条 —— **本方案的核心需求直接失效**。
`CREATE ... IF NOT EXISTS` 不会替换已有约束。

先例可直接照搬：`63-dw-email-connection-name-by-direction.sql:3`（DROP 自动名 UNIQUE）、
`38-dw-main-table-view-tables.sql:17-22`（per-FU → per-table 索引改造，注释即权威警告）。

### 兼容性

- **只增不改**：`scene` 在 `dw_form_stage_bindings` 与 `dw_form_definitions` **两处**
  均带 `DEFAULT 'TASK'`，存量行自动归为 TASK，To Do / Completed 行为不变；
  `detail_form_id` 可空，存量 view 不受影响。
- **审计授权表独立键空间**，不碰任何现有读取点，结构上不可能提权。
- **`AUDITOR` 划清界限**：admin-center 已有 `AUDITOR` 角色
  （`AuthServiceImpl.java:428` 的 `SYS_ADMIN || AUDITOR`），user-portal 不认它。
  本功能**不复用该名**，避免误以为配了 AUDITOR 就有 FU 审计权。

### FU 可移植性（核实确认：新列**不会**自动跟着走）

两张表**已在**覆盖范围内，但所有序列化都是**逐字段手写** `map.put` / builder，
**无反射、无 DTO 自动映射** —— 新列必须逐处手工加。

**`scene` 需改 5 处**（注意导入写入点在 `FunctionUnitImportWriter`，**不在** `FunctionUnitImporter`）：

| # | 位置 | 改动 |
|---|---|---|
| 1 | `FunctionUnitExporter.serializeFormStageBinding()` :653-659 | `map.put("scene", …)` |
| 2 | `FunctionUnitExporter.serializeForm()` :591-610 | form 的 scene |
| 3 | `FunctionUnitImportWriter.importFormStageBindings()` :467-473 | builder `.scene(…)` |
| 4 | `FunctionUnitImportWriter.importFormShell()`（:308 上游） | form scene |
| 5 | `FunctionUnitCloner` :442-447 | builder `.scene(…)` |

**`detail_form_id` 需改 3 处**（走独立的 `MainTableViewPortability`；克隆另走一条路）：

| # | 位置 |
|---|---|
| 1 | `MainTableViewPortability.export()` :50-59 |
| 2 | `MainTableViewPortability.importAll()` :109-120 builder |
| 3 | `MainTableViewServiceImpl.cloneViewsForFunctionUnit()` :248-259 builder |

#### detail_form_id 跨环境：用 code，并调整导入顺序（已确认）

数字 id 跨环境必变，直接搬运会指向错误 form 或悬空。**导出写 form code，导入按 code 反查** ——
与现有名称化模式一致（`MainTableViewPortability.java:51` 用 `mainTableName`、
`:213` 用 `targetCode`；`FunctionUnitExporter.java:661-662` 已有 `formIdToName` 基础设施可复用）。

**导入顺序须调整**：当前 `FunctionUnitImporter` 中 **views(:172-176) 先于 forms(:185-190)**，
导 view 时 form 尚不存在，code 无法解析。已确认改为 **forms 先于 views**。

**注意**：`MainTableViewPortability.importAll()` :96 会 `deleteByFunctionUnitId` 全删重建；
`FunctionUnitImporter.java:180` 导入后无条件调 `seedDefaultViewsForFunctionUnit`。
调顺序时须回归这两条路径。解析失败的口径参照现有两种先例择一：
`:103-107` warn-skip（view 引用主表）或 `:263-267` 抛错（access 引用角色/BU）—— 建议抛错，
与「无回退」总基调一致。

版本快照复用 `functionUnitExporter.buildVersionSnapshotPayload()`（`VersionComponentImpl.java:373`），
Exporter 改好后快照侧自动跟上，但须确认 `FunctionUnitSnapshotRestorer` 也走
`MainTableViewPortability.importAll`。

#### 审计授权表的传播

`sys_function_unit_access` **完全不在** Exporter / Cloner / 版本快照覆盖内，
现仅靠导入时启发式 `copyAccessFromSiblingVersions`（`FunctionUnitAccessService.java:154-184`）。
新审计表**必须自建传播逻辑**，否则 FU 重部署后授权丢失。

**漏掉 `scene` 的症状是 My Request 全空**（无回退设计），而非「配置丢失但可用」。

---

## 【分期】

| 批次 | 内容 |
|---|---|
| **0（独立前置）** | 修**三个**越权读漏洞（`/change-history`、`/change-history/sensitive-masks`、`/form`） |
| **1（MVP）** | scene 轴 + REQUEST form 设计 + 审计授权表 + 审计入口 |
| **2** | DETAIL form + View 详情页 |
| **3** | 删 `subTablePortalViews` 的 **`initiatorRequest` 轴**（范围已修正，见下） |

### ⚠️ 批次 3 范围修正：只删一根轴，不是整体删

**原表述「删 `subTablePortalViews`」是错的**（前端核实推翻）。该配置承载的
**不只是双轴，还有单轴的 TASK 侧显示逻辑**：

| 方法 | 位置 | 谁在用 |
|---|---|---|
| `subTableMode` | `useSubTablePortalViews.ts:31-35` | **两个 context 都读**（`formBelowTable`/`tableOnly`/`summaryWithLinkFormModal` 三态） |
| `resolveAssigneeTodoFormSource` | `:112-127` | **完全不判 viewContext** |
| `linkFormScrollToInlineEnabled` | `:72-75` | **`assigneeTodo` 专属** |
| `subTableShowTaskStatusInitiator` | `:91-103` | `:94-97` 分支服务 TASK |
| `resolveInlineFormSourceBinding` | `:195-214` | `:206` 显式 `assigneeTodo` 分支 |

整体删会**打掉 To Do 的「表格下内联表单」与「Link Form 目标解析」** ——
与本 Plan「scene 只影响 REQUEST 侧、TASK 行为不变」的前提直接冲突。

**已确认范围：只删 `initiatorRequest` 轴，`assigneeTodo` 半边保留不动。**

**可删**（确认为 `initiatorRequest` 专属）：
- `shouldRenderPlacedSubTableField` :43-60（`:44` 非 initiatorRequest 直接 true）
- `subTableCompactLookupCells` :63-66
- `subTableShowViewDetailInitiator` :105-109（**`:108` 恒 false，已是死代码**）
- `useApplicationDetailSubTaskDialog.ts` 4 分支
- `formRendererPortalViews.ts` 的 `initiatorRequest` 解析路径
- DW 侧 `useSubTablePortalViews.ts` + `useFormSave.ts` 的 `initiatorRequest` 收集

**`viewContext` prop 不能删**（原表述说退役是错的）：
`tasks/detail.vue:280` 在 **To Do 页面内**用 `initiatorRequest` 渲染 process form 区块；
custom action 弹窗自设 viewContext（`customActionFormPopup.ts:82,:152`、
`useCustomActions.ts:64,:94,:159`）。**它是「区块呈现语义」，不是「哪个页面」。**

**删除面补漏**（原表述遗漏）：
- `savedFormPreviewBuilder.ts:422`（第二条 preview 链路）
- `useFormLifecycle.ts:39,:133` 的 `subTablePortalViewsState`

**`FormRendererFields.vue` 是 16 个消费点，不是 ~10 个**，且 `:286-319` 与 `:378-411`
是两个近乎重复的渲染分支（placed vs unplaced），**两边都要改**。

**后置理由**：纯删除且不可逆。存量 `initiatorRequest` 配置需迁为 REQUEST form 初始设计，
而 `summaryWithLinkFormModal` 这一形态**只存在于要删的那根轴上**（详见风险表），
须先确认它能在 REQUEST form 画布上表达，否则本批次阻塞。

**批次 1 顺带修复**：placed / unplaced 子表解析不一致
（前者用 merge 后的 widget+binding portalViews，后者只读 binding 级不 merge）——
注意批次 1 到 3 之间，两分支的行为差异会持续存在。

---

## 【风险与回滚】

| 风险 | 缓解 |
|---|---|
| **上线即破坏性**：无回退设计下，存量 FU 的 My Request 全显示「未配置」 | 需人工补配 REQUEST form。**量级可控**（核实：seed 里 forms 29 / stage bindings 4 / views 11，共 9 个 FU），非千行级工程，但仍须排期 |
| **schema 静默失败**（两处） | ① 新 `65-*.sql` 不追加进 `00-init-all.sh:74-130` 白名单 → 全新环境也不执行且无报错；② 存量库需手工 psql（init 只跑首次）。详见【数据与契约】 |
| **匿名唯一约束不 DROP → 方案直接失效** | `dw_form_stage_bindings_form_id_stage_id_key` 必须显式 DROP，否则同节点配不了 TASK+REQUEST 两条 |
| **导入顺序调整的回归面** | forms 提到 views 之前，须回归 `MainTableViewPortability.importAll()` 的全删重建（:96）与 `seedDefaultViewsForFunctionUnit`（`FunctionUnitImporter.java:180`）两条路径 |
| **权限拒绝无日志** | 现状六条拒绝分支全静默，审计人被拒时运维无法定位。`canAuditProcessDetail` 须自带分支级日志 |
| 角色缓存 5 分钟 | 新配审计角色可能延迟生效；授权变更时调 `clearUserRolesCache` 或明确接受窗口 |
| FU content 接口是**独立第二道门**，判 `sys_function_unit_access` 发起权 | 漏放行 → 审计人过了 `/processes/{id}` 但表单渲染不出，页面空白。放行判据须收敛到具体 FU，保留 `requireEnabledFunctionUnit` 门 |
| **BPMN 扩展属性与 stage binding 双链路** | 已确认两条都在用，**主力 demo（50005/48/24）走 BPMN 链路且无 stage binding 行**。两条都必须带 scene 并一致解析；漏掉 BPMN 链路 = scene 对主力 FU 完全失效 |
| **portal 侧 BPMN 解析漏改** | 链路 B 共**四份实现**（DW 2 + portal 2）。漏 `useApplicationDetailNodeFormMap.ts` → scene 对 My Request **静默失效**；`formName` 兜底**跨 scene 误匹配且无报错** |
| **批次 3 打掉 To Do 功能** | 整体删 `subTablePortalViews` 会使内联表单 / Link Form 解析失效。**已修正为只删 `initiatorRequest` 轴** |
| **前端 PROCESS 校验挡住验收** | `useFormLifecycle.ts:395-401` 前端先弹窗拦截，正例 3 走不到后端 |
| **前端测试网薄** | 仅 3 个单测覆盖该链路，「905 全绿」**不构成批次 3 安全证据**；须补 TASK 侧回归 |
| 批次三迁移：`summaryWithLinkFormModal` 无归宿 | 该形态**只存在于 `initiatorRequest` 轴**（正是要删的那轴），见 FU 50005 form 50192 的 binding 50544/50547。删除前必须确认它能在 REQUEST form 画布上表达；不能则批次三阻塞 |
| 审计列表性能 | **不要**沿用 `PortalMainTableViewServiceImpl.java:392` 的 `PageRequest.of(0,5000)` 全量拉取模式，走真分页 |
| **note 变为不可变**，是产品行为变更 | 用户写错只能追加新 note 更正，无法删改。DW 的 `allowDelete` / `allowEditOwn` prop 废弃；`RecordNoteField.vue` 编辑 / 删除按钮移除；`RecordNoteAuditTest` 断言改为「应被拒绝」。**存量已写入的 note 不受影响，但今后无法修正** |

**回滚**：批次 1 的 `scene` 列可保留（DEFAULT 'TASK' 无害）；审计表可留空；
前端解析退回不带 scene 即恢复原行为。

---

## 【验收】

### 批次 0
- 反例：当前任意登录用户带他人 `processInstanceId` 调 `/change-history`、
  `/change-history/sensitive-masks`、`/form` → **200 返回他人数据**
- 正例：非参与者调用 → 403；参与者 / 发起人 / SYS_ADMIN 正常
- 回归：**3 个端点 × 全部参数分支**（`taskId` / `rowIdentifier` / snapshot），
  覆盖 My Request 详情、To Do 详情、Completed 快照三个页面入口

### 议题一（scene）
- 反例：当前 My Request 与 To Do 共用一份 form，子表差异只能靠 `subTablePortalViews` 两级 merge
- 正例 1（**链路 A**，FU 16）：stage binding 挂的节点可挂两份独立 form；
  My Request 渲染 REQUEST 版本，To Do 渲染 TASK 版本，各自 preview 正常
- 正例 2（**链路 B**，FU 50005）：BPMN `custom:property` 挂的节点同上 ——
  **这是主力形态，必须单独验**
- 正例 3（**载体 3**，FU 50005 form 50193）：发起步骤在 My Request 里渲染
  REQUEST 版 PROCESS form；同一 FU 可同时存在 `PROCESS/TASK` 与 `PROCESS/REQUEST` 两份，
  且再建第三份时仍报 `PROCESS_FORM_ALREADY_EXISTS`
- 正例 4：未配置 REQUEST form 的节点显示「未配置此场景的表单设计」而非白屏

### 议题二（审计入口）
- 反例：审计角色用户登录后，My Request 只见自己发起的，无入口见他人 request
- 正例：admin 给角色配 FU audit 授权后，portal 出现审计入口，列出该 FU 全部 request；
  进详情可见表单 / Change History / Flow History，可写 note；note 与申请人共享同一条流

**负向验收（全部必须）**

| # | 场景 | 期望 |
|---|---|---|
| N1 | 仅有 audit 授权的角色查看 New Request 列表 | **不出现**该 FU（audit ≠ 发起权） |
| N2 | 审计人访问**未授权 FU** 的 request（列表与详情直连 URL 两路） | 列表不含；详情 403 |
| N3 | 任何人（含 owner / SYS_ADMIN / 审计人）删除 note | 403 |
| N4 | 任何人（含 owner）编辑 note | 403 |
| N5 | 审计人调 `GET /record-notes/archive/{pid}` | 403（`requireArchiveAccess` 保持调旧判据） |
| N6 | 无 audit 授权的普通用户访问审计入口 | 菜单不显示；直连接口 403 |
| N7 | 审计人调 `GET /processes/{id}` 与 `/history` | **200**（正向对照，证明 N5 不是全面拒绝而是粒度正确） |

N2 是权限功能的核心正确性 —— 授权必须收敛到**具体 FU**，不能因为持有任一 FU 的
audit 授权就放行全部。

### 议题三（View 详情）
- 正例 1：View 点行进入该 view 自己的 DETAIL form 详情页，只读，**无 Change History**
- 正例 2：**同一 FU 的两个 view 配不同 DETAIL form，点行分别进入各自设计**
  （主表 view 与子表 view 各配一份，验证 per-view 挂载真的生效）
- 正例 3：未配 `detail_form_id` 的 view 点行 → 显示「未配置此视图的详情表单」
- 正例 4：跳转不再以 `processInstanceId` 为前提（子表 view 行亦可进）；
  空值时给提示（现为静默无响应）
- 正例 5：删除被 view 引用的 DETAIL form → `detail_form_id` 置 NULL，view 不报错

### 验收目标 FU（已确认：用多实例 demo）

| FU | code | 名称 | 验什么 |
|---|---|---|---|
| **50005** | `fu-20260422-23tfag` | Multi-Instance Subtask Demo | **主目标** —— BPMN 链路的 scene、`subTablePortalViews` 全形态迁移、审计入口 |
| **16** | `fu-20260403-a1b2c5` | Meeting Participant Info Collection | **补充** —— stage binding 链路的 scene |

**为何 50005 是主目标**

- 4 个 userTask（2 个在 MI 子流程内），分属 `ELEMENT_VARIABLE` / `FIXED_BU_ROLE` 两种派单，
  加一个排他网关 —— 能压到 To Do 与 My Request 在 MI 子任务上的差异
- 3 张 SUB 表（Participants 作 MI 数据源 / Attachment / People），form 50192 一份表单挂 3 个子表绑定
- **`subTablePortalViews` 覆盖面唯一完整**：3 份 form 共 5 个绑定条目，铺满
  `tableOnly` / `formBelowTable` × `mirrorTodo` / `summaryWithLinkFormModal` × `linkForm` / `subForm`。
  批次三删除后的迁移效果只有这里能一次全验完（ATM / MCY 各仅 1–2 条且模式重复）
- 有 PROCESS form（50193），可验「PROCESS 唯一性改按 scene 计数」

**为何需要 FU 16 补充**

50005 **没有任何 `dw_form_stage_bindings` 行** —— 它的 form↔节点映射走 BPMN
`custom:property name="formId"` 扩展属性。FU 16 是仓库里唯一带**真实 MI stage binding 行**的 demo
（`16-meeting-participant-collection/05-form-stage-bindings.sql:39-41`），
其 stage id 形如 `MI_UserTask_<tableId>`（拼接生成）—— 迁移脚本最易漏的形态。

### 前端专项验收（本轮新增）

| # | 场景 | 期望 |
|---|---|---|
| F1 | FU 50005 的 My Request 渲染 REQUEST form | **成功** —— 证明 `useApplicationDetailNodeFormMap.ts` 已带 scene |
| F2 | TASK 版与 REQUEST 版 **同名**时解析 | 不发生跨 scene 误匹配 |
| F3 | 批次 3 后 To Do 的「表格下内联表单」与 Link Form 目标解析 | **行为不变**（`assigneeTodo` 轴保留） |
| F4 | DW form 列表中同节点两份 form | 视觉可区分（scene 列或标签） |
| F5 | 建第二份 PROCESS form（不同 scene） | 前端不拦截，后端按 scene 计数放行 |
| F6 | 无审计权用户加载 portal | 审计菜单**从不闪现**（非「先显示后消失」） |

### Schema 与可移植性验收（静默失败点，必须显式验）

| # | 场景 | 期望 |
|---|---|---|
| S1 | 全新环境 `docker compose down -v` 后重建 | `65-*.sql` **确实执行**（查 `\d dw_form_stage_bindings` 有 `scene` 列） |
| S2 | 存量库手工执行 `65-*.sql` | 加列成功，存量行 `scene='TASK'` |
| S3 | 同一 `(form_id, stage_id)` 插入 TASK + REQUEST 两条 | **成功**（证明旧匿名约束已 DROP） |
| S4 | FU 导出 → 导入到另一环境 | `scene` 与 `detail_form_id` **均正确还原**（后者按 code 解析到正确 form） |
| S5 | FU 克隆 | 同 S4 |
| S6 | 版本回滚 | 同 S4 |
| S7 | 导入一个 detail form code 不存在的包 | 按既定口径抛错（非静默丢弃） |

### 验收前置：实例数据需手工产生

**全仓 `deploy/init-scripts/` 零条 `up_process_instance` INSERT** —— 所有 seed 只建设计态。
已确认采用**手工跑流程**方式（不补种子）。

FU 50005 走到 MI 子任务态的路径：

1. submit（`Activity_0z1px4l`，`INITIATOR`）
2. assignment（`Activity_0hwtl8v`）—— 在 Participants 子表填 `assignee` / `role_code` / `bu_code`
3. MI 展开 → sub form1（`Activity_0j8mz1c`，`ELEMENT_VARIABLE`）出现在受让人 To Do
4. sub form2（`Activity_134mqyl`）需 `E2E_FINANCE` / `MANAGER` 用户且 `decision == 'yes'` 才走到

**审计入口验收需 ≥2 条不同发起人的实例**，否则验不出「看到他人 request」与 N2（FU 隔离）。

---

## 【验证】（实现后最低命令）

```bash
# 后端（须进模块目录，backend/ 根 pom 无 modules，-pl 会失败）
cd backend/user-portal && mvn package
cd backend/admin-center && mvn package -Dspring-security.version=...
cd backend/developer-workstation && mvn package

# 前端（三个前端各跑）
npm run type-check && npm run test:unit

# Docker（Dockerfile 只 COPY target 里的 jar，必须先 package 否则镜像是旧的）
docker compose build user-portal admin-center developer-workstation

# 截图验证
/verify-ui
```

**基线**：2026-07-12 起三后端 + 两前端全绿（engine 784 / portal 905 / admin 602，0F / 0E），
**任何红都是真回归**。

---

## 【实现前须先确认】

设计决策已全部拍板，以下是**需要打开代码才能定**的实现细节，
建议在对应批次开工时先花少量时间确认，而非在设计阶段猜：

| # | 事项 | 影响批次 | 为何未在设计阶段定 |
|---|---|---|---|
| 1 | **子表 view 的行是否可寻址** —— 主键可能是复合键（父表 FK + 行序号） | 2 | Plan 声称「子表 view 点行同样可用」，但未验证。若不可寻址，议题三需收窄为仅主表 view |
| 2 | **View 详情页路由形态** —— viewId 进 path 还是 query | 2 | 进 path 会影响 `PortalLayout.vue:150` 菜单 index 与 `:325-327` replace 逻辑，需看实际代码权衡 |
| 3 | **审计列表页无权限时的页面状态** —— 守卫表达不了 per-FU 授权，只能页面内 403 | 1 | 需定义「先渲染空页再报错」的具体呈现 |
| 4 | **admin-center audit 授权 UI 交互** —— 独立 tab 还是并入现有 `AccessConfigDialog` | 1 | 取决于现有弹窗的空间与 `assignedIds` 结构改造成本 |
| 5 | **`summaryWithLinkFormModal` 能否在 REQUEST form 画布表达** | 3 | **批次 3 的阻塞前提**，须在动手删除前验证 |
| 6 | **`useDesignerListGrid` 的列配置迁移行为** —— 加列后存量 localStorage 是否兼容 | 1 | 需看该 composable 实现 |
| 7 | 验收用的具体 applicationId | 全部 | 实例数据须手工跑流程产生 |

**第 5 项是硬阻塞** —— 若该形态无法表达，批次 3 需重新设计迁移路径。

---

## 附：决策记录

| 项 | 决定 |
|---|---|
| scene 取值 | `TASK`（To Do + Completed）/ `REQUEST`（My Request + 审计） |
| 发起步骤 | PROCESS form 做 REQUEST 版本，**走 `dw_form_definitions` 的 `(form_type, scene)` 组合**；**不**塞假 stage key 进 stage binding 表 |
| 未配置 REQUEST form | 不回退，显示提示文案 |
| 审计入口内容 | 与 My Request 完全一致（复用详情页） |
| 审计授权 | 新建 `sys_function_unit_audit_access`，admin-center CRUD |
| `AUDITOR` 角色 | 明确划清界限，不复用 |
| note 删除 / 编辑 | **任何人都不可**（含 owner 与 SYS_ADMIN），note 不可变 |
| note 导出 zip | 审计人不放行 |
| 评论流 | 审计人与申请人共享 |
| 评论进 Change History | 不进（现状即是） |
| View 详情页 | 仅 DETAIL form，**无 Change History**；**每个 view 挂一份**（`detail_form_id`） |
| 审计放行粒度 | **新增 `canAuditProcessDetail`**，不改 `canAccessProcessDetail`；archive 保持旧判据（fail-closed） |
| 越权漏洞 | **先修洞再做功能**（批次 0 独立前置） |
| form↔节点双链路 | **两条都支持** scene（表 + BPMN 扩展属性） |
| 验收 FU | 50005（主）+ 16（stage binding 链路补充） |
| 实例数据 | **手工跑流程**，不补 seed |
| DETAIL form 跨环境引用 | **用 code 不用 id**；导入顺序改为 **forms 先于 views** |
| 无 view 的 FU | 不特殊处理 —— 没 view 即没详情页入口 |
| 批次 3 范围 | **只删 `initiatorRequest` 轴**；`assigneeTodo` 半边与 `viewContext` prop 均保留 |
