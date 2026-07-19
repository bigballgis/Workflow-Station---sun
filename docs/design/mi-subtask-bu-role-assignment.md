# 多实例子任务按 BU + Role 分派（逐行 Role 字段，共享认领池）

> 状态：**已实现**（2026-07-16 初版；2026-07-18 升级为三场景逐行模型）。

## 三场景升级（2026-07-18，最终模型）

初版是**节点级** `assigneeMode`（user/role 二选一）。按用户需求升级为**逐行判定 + 两个独立开关**，
满足三场景：

| 场景 | dw Process Design 配置 | 运行时（Preview / User Portal）逐行录入 |
|---|---|---|
| **A. 仅个人** | 只勾「允许按个人」→ 配 Assignee Field | 每行只能选**人**（现有 lookup 绑 sys_users） |
| **B. 仅 BU+Role** | 只勾「允许按角色」→ 配 Role Field + BU Field | 每行选 **BU + Role**（先选 BU，Role 随 BU 收敛） |
| **C. 两者都勾** | 勾两个 → 配 Assignee/Role/BU Field | 每行**二选一**：人 **或** BU+Role，**行级互斥** |

**核心改动（相对初版）：**
- **dw Process Design**（`UserTaskSubTaskConfigSection.vue` + `useUserTaskMultiInstance.ts`）：radio →
  两个 `el-checkbox`（`allowUser`/`allowRole`），派生 `assigneeMode`（`user`|`role`|`both`）写 BPMN。
  checkbox + 加载时回写 modeler（`UserTaskProperties.vue`）顺带修掉初版 radio 的 saveXML 时序 bug。
- **engine 逐行分派**（`MultiInstanceTaskWriter.handleElementVariableAssignment`）：不再按节点级模式硬分流，
  **逐行**看 `currentItem[roleField]` 有值→role 分支（BU_ROLE 池），否则 `assigneeField`→user 分支。
  `TaskOrphanRepairService.repairOrphanMultiInstanceTasks` 同步逐行。
- **user-portal collection**（`MiCollectionVariableBuilder`）：逐行带 assignee 或 role/bu code；
  行「可纳入」= assignee 或 role 任一非空（`rowHasAssigneeOrRole`）。
- **user-portal 运行时录入**（`SubTableAddDialog.vue` + 新 `useSubTableBuRoleCascade.ts`）：按 **field 名**
  （`bu_code`/`role_code`）在模板抢先渲染两个联动 select——BU select 选项来自
  `permissionApi.getApplicableBusinessUnits()`，Role select 选项随所选 BU 调
  `permissionApi.getBusinessUnitRoles(buId)` 收敛（复用普通任务 FIXED_BU_ROLE 的 admin-center 数据源，
  **不建 Relation Table、不改 lookup**）。行级互斥：填 assignee 禁用 BU/Role，反之亦然，清一个另一个复用。
  子表列 `bu_code`/`role_code` 存 **code**（后端解析用 code），BU select 内部维护 code→id 映射查 role。
- **场景显隐**由「表单里放了哪些控件」决定（设计者在 Form Design 放 assignee/BU/Role 控件的组合），
  不需从 BPMN 传节点配置到运行时表单。
- 子表 `subtable`(50331) 加 `bu_code` 列（`role_code` 初版已加）；三个 demo 表单
  （50191/50192/50193）config_json 加 `bu_code`+`role_code` select 控件；demo MI 节点配 `assigneeMode=both`。
- 后端单测 `TaskAssignmentListenerMiRoleModeTest` 补 both 混用逐行分流 2 例（共 6 例全绿）。

---

> 以下为 2026-07-16 初版设计（节点级 role 模式），保留供参考。末尾「最终实现与偏差」记录初版实际改动点。

## 背景

原先多实例（MI）子任务从每一行子表读取 **Assignee Field**，该字段存的是**具体用户 id**，
生成的子任务被 `setAssignee` 给那个人，因此只出现在他一个人的 **To Do** 列表里。

新需求：MI 子任务的某一行也能指向**某 BU 下的某个 Role**。当持有该角色的任意用户登录时，
这行对应的子任务要出现在他的 To Do 列表里（共享认领池——谁先认领谁负责）。

两个关键决策：
- **配置来源 = 逐行字段。** 每行子表用一列存 role code（BU code 可选），设计器里选一个
  **Role Field**（加可选 **BU Field**）——和 Assignee Field 完全同一个「逐行」模型。不同行可指向不同角色。
- **池行为 = 共享认领池。** 一个角色解析出 N 个用户，每行仍只产生**一个**子任务，对这 N 个人
  以 **candidate users** 形式可见（与普通 `BU_ROLE` 任务完全一致）。**不做**逐用户 fan-out，
  所以 MI 基数不变（每行仍只有一个实例）。

架构上这很省事：普通 `BU_ROLE` 任务在**任务创建时就把 role/BU 预解析成具体用户 id**
（1 人→assignee，多人→candidate users），而 To Do 查询匹配 `taskAssignee` / `taskCandidateUser`。
所以**只要 MI writer 用同样方式把 role/BU 解析成用户，To Do 列表零改动即可展示这些子任务。**

## 设计概述

给 MI 子任务配置加一个**分派模式**：`user`（默认）vs `role`。
- `user` 模式 → 行为不变（`assigneeField` → `setAssignee`）。
- `role` 模式 → 新增 `roleField`（+ 可选 `buField`），指定子表里存 **role code** 和 **BU code**
  的列。运行时每行子任务用现成的 `TaskAssigneeResolver` 把 role+BU 解析成用户，再按
  setAssignee（1 人）/ addCandidateUser（多人）落地。

逐行值的载体是 **MI collection item**（`currentItem`）——和 assignee id 走的同一条通道。
`assigneeType` 仍是 `ELEMENT_VARIABLE`（MI 底层不变），`assigneeMode` 是它内部的一个开关。

## 后端改动

### 1. 把 role/BU code 带进 MI collection item
`backend/user-portal/src/main/java/com/portal/component/MiCollectionVariableBuilder.java`
- 从子流程 BPMN 读 `assigneeMode` / `roleField` / `buField`（新增
  `BpmnMiXmlSupport.extractAssigneeModeFromSubProcess` / `extractRoleFieldFromSubProcess` /
  `extractBuFieldFromSubProcess`），role 模式下逐行把 role/BU **code** 值放进 item map。
- 行的「是否可纳入 MI」判定：user 模式看 `assigneeField`（含 assignee 兜底键），role 模式看 `roleField`
  （直接读列，不套 assignee 兜底键，见 `resolveMiEligibilityRaw`）。`assigneeField` 老路径原封不动。

### 2. 逐行解析 role/BU 并落地池
`backend/workflow-engine-core/src/main/java/com/workflow/listener/MultiInstanceTaskWriter.java`
- `handleElementVariableAssignment` 里读完子表信息后，同样双读（内存态 `getExtensionProperty`
  + 部署 XML `bpmnActionParser.getUserTaskExtensionPropertyValue`）出 `assigneeMode`/`roleField`/`buField`。
- `assigneeMode != "role"` → 走原 user 路径（`setAssignee`）。
- `assigneeMode == "role"` → 走新 `handleRoleModeAssignment`：读 `currentItem[roleField]`（role code，
  单个或列表）+ 可选 `currentItem[buField]`（BU code，空则回退进程 `activeBusinessUnitId` 经
  `owner.mapActiveBusinessUnitIdToCode` 转 code）；调
  `resolver.resolveWithRoleIds("BU_ROLE", roleCodes, buCode, initiator, anchor, buCode)`，按结果
  setAssignee（1 人）/ addCandidateUser（多人）落地，并写 ExtendedTaskInfo；空/错保持 CREATED。

### 3. 孤儿修复对齐
`backend/workflow-engine-core/src/main/java/com/workflow/component/TaskOrphanRepairService.java`
- `repairOrphanMultiInstanceTasks` 读到 `assigneeMode == "role"` 时走新 `repairOrphanMiRoleTask`：
  从 `currentItem` 重读 role/BU code，`isEligibleRole` + `getUsersByBusinessUnitAndRole` 重解析并
  重做 setAssignee/addCandidateUser——镜像 `repairOrphanBuRolePoolTasks` 的兜底逻辑，成功后清除失败留痕。

### 4. To Do 查询 + workspace 可见性收敛（2026-07-19 增补）
`TaskQueryService.getUserAllVisibleTasks` 已匹配 `taskAssignee` + `taskCandidateUser`，任务能被拉到。
但**可见性语义**按用户要求区分两种分派：

- **按人分派**（`assigneeMode != role`）→ 该人切换到**任何** role 的 workspace，To Do 里都出现该子任务。
- **按角色分派**（`assigneeMode == role`，含单持有者角色）→ 多 role 用户登录后，**只有切到该 role 的
  workspace** 才能在 To Do 看到；切到别的 role 时隐藏（共享认领池按 role 隔离）。

实现走一条跨层信号链，把 role 分派信号从引擎透传到 portal 过滤器：
1. **engine `TaskInfoAssembler`**：`buildTaskInfoFromFlowableTask` 读
   `ExtendedTaskInfoRepository.findByTaskIdAndIsDeletedFalse(taskId)` 的 `extendedProperties`，解析出
   `assigneeMode` / `roleCodes`（取首个）/ `businessUnitCode`，填进 `TaskListResult.TaskInfo` 新字段
   `miAssigneeMode` / `miRoleCode` / `miBusinessUnitCode`。
2. **portal `EngineTaskMapper`**：从引擎返回 Map 按同名 key 映射到 portal `TaskInfo` 的对应字段。
3. **portal `WorkspaceTaskFilterComponent.filterFixedBuRoleTasksForActiveWorkspace`**：主循环加
   **role-scoped gate**（优先于 assignee/candidate 放行）——
   `isMiRoleScopedTask(t)`（`miAssigneeMode=role` 且有 `miRoleCode`）为真时，仅当
   `resolveActiveRoleCode(userId)`（由 `getCurrentActiveRoleId` 经 `listWorkspaceContexts` 反查 roleId→roleCode）
   与 `miRoleCode` 大小写不敏感相等才保留，否则隐藏——即便当前用户恰是该任务 assignee（单持有者角色）。
   非 role 任务路径不变（仍走 assignee/candidate 优先放行 + FIXED_BU_ROLE 的 BU 收敛）。

> 说明：仅当 JWT 带 `activeBusinessUnitId`（workspace 模式）时进入此过滤；用户在 user portal 切换 role
> 必然带 active BU/role，故 role-scoped gate 生效。非 workspace 模式（无 active BU）沿用早返回、不收敛。

## 前端改动（developer-workstation 设计器）

- `UserTaskSubTaskConfigSection.vue`：加**分派方式** radio（*By user field* / *By role field*）。
  user 模式显示 Assignee Field；role 模式显示 Role Field（必填）+ BU Field（可选），选项都来自
  所选子表的 `fieldDefinitions`。
- `useUserTaskMultiInstance.ts` / `useUserTaskState.ts` / `types.ts`：加 `assigneeMode`/`roleField`/`buField`
  ref 与 `handleAssigneeModeChange`/`handleRoleFieldChange`/`handleBuFieldChange`；切模式时清另一模式的属性，
  切 role 时按 `/^(role|role_code|role_id|role_codes)$/i` 自动预选 roleField。写入走
  `bpmnExtensions.ts` 的 `setExtensionProperty`（toRaw 安全路径）。
- `UserTaskProperties.vue` `loadPropertiesAsync`：加载时读回 `assigneeMode`（默认 `user`）/`roleField`/`buField`。
- i18n：三个 locale（en/zh-CN/zh-TW）加 `assignmentModeLabel`/`assignmentModeUser`/`assignmentModeRole`/
  `roleFieldLabel`/`roleFieldTip`/`buFieldLabel`/`buFieldTip`/`selectRoleField`/`selectBuField`。

## 数据 / 种子说明
不改 schema。子表只需一列存 **role code**（BU code 可选）——设计器把 `roleField`/`buField` 指向
现有子表列即可。该功能对任何子表带此类列的 FU 都可用。demo 数据适配见
`docs/demo-data-requirements.md`。

## 最终实现与偏差

- **ExtendedTaskInfo 的 assignmentType**：plan 里初拟用 `BU_ROLE`，但 `AssignmentType` 枚举无该值，
  实际按结果落地：单人 assignee → `AssignmentType.USER`，多人候选池 → `AssignmentType.CANDIDATE_USERS`；
  role/BU code 与候选 id 存进 `extendedProperties`（`assigneeMode=role` / `roleCodes` / `businessUnitCode`）。
- **resolver 类型参数**：role 分支传 `"BU_ROLE"` 而非 `"ELEMENT_VARIABLE"`，因为后者是 `isListenerOnly()`
  会被 `resolveWithRoleIds` 拒绝；`BU_ROLE` 正是我们要的 0/1/多池解析。
- **`TaskAssignmentListener` 放开的可见性**：新增 `taskAssigneeResolver()` 访问器，`notifyCandidateTask`
  与 `mapActiveBusinessUnitIdToCode` 由 private 提升为 package-private，供 `MultiInstanceTaskWriter` 复用。
- **role code 规范化**：`resolveRoleCodesFromItem`（writer）/ `miRoleCodesFromItem`（repair）支持
  `List` / JSON 数组文本 / 逗号串 / 单值，统一走 `AssigneeRoleIdsSupport.parseRoleIds`。
- **workspace 可见性（2026-07-19）**：新增跨层信号链（见「### 4」）。关键取舍——role-scoped gate
  放在 assignee/candidate 放行**之前**，否则单持有者角色任务（走 `setAssignee`，assignee=本人）会被
  assignee 放行而无视 active role，违反「只在切到该 role 才可见」。active role→code 反查复用
  `PortalWorkspaceAuthService.listWorkspaceContexts`（无新增 SQL），存 roleCode 而非 id（跨环境 import 友好，
  对齐 [[assignee-code-matching]] 的 code 化路线）。

## Current Step（MI 感知的「当前步骤」，2026-07-19）

需求：To Do/Completed 列表与详情、My Requests 详情都要展示「当前步骤」——**普通节点显节点名，
流程处于多实例子任务内部时显外层多实例 subProcess 的 name（如 "multi"）**，而非某个具体内层子任务名。
终态（COMPLETED 等）显示 `-`。

- **值语义**：与 `taskName`（内层/具体节点名，如 "sub form1"）区分——新增 `currentStepName`：MI 内=外层
  多实例 subProcess name（无 name 回退 subProcess id），否则=taskName。
- **engine（To Do/Completed 链路）**：`TaskInfoAssembler.resolveCurrentStepName(pdId, taskDefKey, taskName)`
  用 `repositoryService.getBpmnModel(pdId).getFlowElement(taskDefKey)`，沿 `FlowElement.getSubProcess()` 父链
  上溯，找最近一层带 `multiInstanceLoopCharacteristics` 的 subProcess 取其 name。结果按
  `pdId+taskDefKey` 缓存（MI 归属是静态的，避免列表逐行解析 BpmnModel）。三个 builder（flowable task /
  historic task / ExtendedTaskInfo）都填。`TaskListResult.TaskInfo.currentStepName` → portal `TaskInfo`
  同名字段（`EngineTaskMapper` 映射）。
- **My Requests 详情链路**（不经 TaskInfoAssembler）：`ProcessInstanceInfo.currentStepName`；engine 的 MI 状态
  响应本就有 `multiInstanceActivityName`（`MultiInstanceStatusController` 用 `getActivityName` 取 MI subProcess
  name），portal `MiOverlayComponent.getMiActivityName(pid)` 读其已缓存的 MI 状态 payload 取出；
  `ProcessApplicationQueryComponent.getProcessDetail` 在 RUNNING 且有 in-flight MI 行时把 currentStepName
  设为该 MI name，否则=currentNode。
- **前端**：`TaskBasicInfo.vue` 加 Current Step 描述项（`currentStepName || taskName`，已完成→`-`，传
  `:is-completed="isCompletedTask"`）；To Do（`tasks/index.vue`）+ Completed（`tasks/completed.vue`）列表加
  Current Step 列；My Requests `displayCurrentStepLabel` 改用 `currentStepName || currentNode`（终态仍 `-`）。
  前端类型 `api/task.ts` `TaskInfo` + `api/process.ts` `ProcessInstanceInfo` 加 `currentStepName?`。i18n
  复用既有 `task.currentStep` key。

## 验证记录

1. **后端单测**：新增 `TaskAssignmentListenerMiRoleModeTest`（4 例：单人→assignee、多人→candidate 池、
   空池→CREATED、无 BU→CREATED），连同既有 `TaskAssignmentListenerElementVariableTest` /
   `TaskOrphanRepairServiceTest` 全部真实执行通过，无回归。
2. **后端编译**：`workflow-engine-core`（随测试）+ `user-portal`（`BUILD SUCCESS`）真实编译通过。
   注意 `backend/` 无聚合 pom，`mvn -pl <module>` 会 reactor 失败，须进模块目录跑。
3. **前端**：`developer-workstation` `npm run typecheck` 通过（exit 0）。
4. **workspace 可见性单测（2026-07-19）**：新增 `WorkspaceTaskFilterRoleScopeTest`（5 例，`mockStatic`
   `SecurityContextUtils`）——active role 匹配→可见、不匹配即便是 assignee→隐藏、无 active role→隐藏、
   按人分派→任何 workspace 可见、role code 大小写不敏感匹配。全部真实执行通过；engine + user-portal
   `mvn package -DskipTests` 均 `BUILD SUCCESS`。
5. **docker 镜像重建纪律**：后端 `Dockerfile` 是 `COPY target/*.jar app.jar`（拿宿主已构建 jar，非容器内
   `mvn package`）。**只 `mvn compile` 不足**——必须先在宿主 `mvn package` 生成新 jar，否则 docker 的
   `COPY target/*.jar` 层命中缓存复用旧 jar，改动不进镜像（见 [[todo-endpoint-n1-fix]] 假 baseline 坑）。
4. **待做**：`/verify-ui` 截图（切 role 模式、导出 XML 验 `assigneeMode=role` 等属性）、端到端 Docker
   （两个 role 持有者看到同一可认领子任务）、`npm run regression:mi`。
