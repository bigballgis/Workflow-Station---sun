# Dashboard 任务概览团队指标 Bugfix 设计

## 概述

Dashboard 的 Task Overview 卡片当前仅展示个人任务统计（pendingCount、overdueCount、completedTodayCount），缺少团队维度的聚合数据。本次修复将在后端 `DashboardComponent.getTaskOverview()` 中新增团队任务聚合逻辑：通过用户所属 BU 递归查找所有子 BU，收集全部团队成员 ID，分别统计团队待办、逾期、今日完成数。前端将 Task Overview 卡片改为两行六指标布局（个人 + 团队），移除 urgentCount 和 highPriorityCount 的展示。API 端点 `/dashboard/task-overview` 保持不变，仅在 DTO 中新增三个团队字段，向后兼容。

## 术语表

- **Bug_Condition (C)**: 当用户查看 Dashboard Task Overview 时，缺少团队级别的任务统计数据
- **Property (P)**: Task Overview 应同时返回个人指标和团队指标，前端应展示六个指标
- **Preservation**: 流程概览、个人绩效、快捷操作、最近任务等其他 Dashboard 模块不受影响
- **Team（团队）**: 当前用户所属 BU 及其所有递归子 BU 中的全部成员
- **BU（Business Unit）**: 组织架构中的业务单元，通过 `sys_business_units` 表的 `parent_id` 形成层级关系
- **DashboardComponent**: `backend/user-portal` 中负责聚合 Dashboard 数据的组件
- **TaskQueryComponent**: 通过 `WorkflowEngineClient` 从 Flowable 查询任务数据的组件
- **BusinessUnit**: `com.platform.security.entity.BusinessUnit`，包含 `parentId`、`path` 字段
- **UserBusinessUnit**: `com.platform.security.entity.UserBusinessUnit`，用户与 BU 的映射关系

## Bug 详情

### Bug 条件

当用户访问 Dashboard 页面时，`DashboardComponent.getTaskOverview(userId)` 仅以当前用户 `userId` 查询个人任务数据，不查询团队成员的任务数据。前端 Task Overview 卡片第二行错误地重复显示个人数据（如 `completedTodayCount`）和无关的 `urgentCount`/`highPriorityCount`，而非团队指标。

**形式化规约：**
```
FUNCTION isBugCondition(input)
  INPUT: input of type DashboardRequest { userId: String }
  OUTPUT: boolean
  
  userBUs := findBusinessUnitsByUserId(input.userId)
  RETURN userBUs IS NOT EMPTY
         AND taskOverviewResponse.teamPendingCount IS MISSING OR NULL
         AND taskOverviewResponse.teamOverdueCount IS MISSING OR NULL
         AND taskOverviewResponse.teamCompletedTodayCount IS MISSING OR NULL
END FUNCTION
```

### 示例

- 用户 A 属于 BU-Sales，BU-Sales 下有子 BU-Sales-East 和 BU-Sales-West，共 15 名成员。当前 Task Overview 仅显示用户 A 个人的 3 个待办，期望同时显示团队的 42 个待办。
- 用户 B 属于 BU-Engineering（无子 BU），共 8 名成员。当前仅显示用户 B 的 1 个逾期，期望同时显示团队的 5 个逾期。
- 用户 C 不属于任何 BU。当前仅显示个人指标，期望团队指标回退为与个人指标相同的值。
- 前端第二行当前显示 urgentCount 和 highPriorityCount，期望替换为 teamPendingCount、teamOverdueCount、teamCompletedTodayCount。

## 期望行为

### 保持不变的行为

**不变行为：**
- 流程概览（ProcessOverview）卡片的展示和数据查询逻辑不受影响
- 个人绩效（PerformanceOverview）卡片的展示和数据查询逻辑不受影响
- 快捷操作区域不受影响
- 最近任务列表不受影响
- 个人级别的 pendingCount、overdueCount、completedTodayCount 查询逻辑保持不变
- avgProcessingHours 字段保留在 DTO 中（前端不展示但 API 兼容）
- urgentCount、highPriorityCount 字段保留在 DTO 中（前端不展示但 API 兼容）
- `/dashboard/task-overview` API 端点路径和 HTTP 方法不变

**范围：**
所有不涉及 Task Overview 卡片团队指标的功能应完全不受本次修复影响，包括：
- 其他 Dashboard 卡片（流程概览、个人绩效、快捷操作、最近任务）
- 任务列表页面 `/tasks` 的查询和展示
- 委托任务功能
- 流程发起和审批功能

## 假设的根因分析

基于 Bug 描述，最可能的原因如下：

1. **后端缺少团队聚合逻辑**: `DashboardComponent.getTaskOverview(userId)` 仅构建 `TaskQueryRequest` 时设置 `userId` 为当前用户，没有查询团队成员的任务。需要新增：查找用户所属 BU → 递归获取子 BU → 收集成员 ID → 批量查询任务。

2. **DTO 缺少团队字段**: `DashboardOverview.TaskOverview` 仅有 `pendingCount`、`overdueCount`、`completedTodayCount`、`avgProcessingHours`、`urgentCount`、`highPriorityCount`，缺少 `teamPendingCount`、`teamOverdueCount`、`teamCompletedTodayCount` 字段。

3. **user-portal 缺少 BU 数据访问层**: `user-portal` 模块当前没有 `BusinessUnitRepository` 和 `UserBusinessUnitRepository`。虽然 `PlatformSecurityConfig` 已配置 `@EntityScan(basePackages = {"com.platform.security.entity"})`，但 `@EnableJpaRepositories` 仅导入了 `RoleAssignmentRepository`。需要在 user-portal 中新建 Repository 接口或扩展配置以访问 BU 数据。

4. **前端布局未区分个人/团队**: 前端 `index.vue` 的 Task Overview 卡片第二行显示 `urgentCount`、`highPriorityCount` 和重复的 `completedTodayCount`，而非团队指标。需要重构为个人行 + 团队行的布局。

## 正确性属性

Property 1: Bug Condition - 团队任务指标正确聚合

_对于任意_ 属于至少一个 BU 的用户请求 Task Overview，修复后的 `getTaskOverview` 方法应返回 `teamPendingCount`、`teamOverdueCount`、`teamCompletedTodayCount`，其值为该用户所属 BU 及所有递归子 BU 中全部成员的任务聚合统计。团队指标应包含当前用户自身的任务。

**验证需求: 2.1, 2.2**

Property 2: Preservation - 个人指标和其他模块不受影响

_对于任意_ Dashboard 请求，修复后的代码应产生与原始代码完全相同的个人级别 `pendingCount`、`overdueCount`、`completedTodayCount` 值，以及完全相同的 `ProcessOverview`、`PerformanceOverview`、最近任务列表数据。

**验证需求: 3.1, 3.2, 3.3**

## 修复实现

### 所需变更

假设根因分析正确：

**文件**: `backend/user-portal/src/main/java/com/portal/config/PlatformSecurityConfig.java`

**变更 1: 扩展 JPA Repository 扫描**
- 在 `@EnableJpaRepositories` 中新增 `BusinessUnitRepository` 和 `UserBusinessUnitRepository` 的导入
- 或者在 `user-portal` 的 `repository` 包中新建这两个 Repository 接口（继承 JpaRepository，操作 `com.platform.security.entity` 中的实体）

---

**文件**: `backend/user-portal/src/main/java/com/portal/repository/BusinessUnitRepository.java`（新建）

**变更 2: 新建 BusinessUnitRepository**
- 创建 `BusinessUnitRepository extends JpaRepository<BusinessUnit, String>`
- 添加方法 `List<BusinessUnit> findByParentIdAndStatus(String parentId, String status)` 用于递归查找子 BU

---

**文件**: `backend/user-portal/src/main/java/com/portal/repository/UserBusinessUnitRepository.java`（新建）

**变更 3: 新建 UserBusinessUnitRepository**
- 创建 `UserBusinessUnitRepository extends JpaRepository<UserBusinessUnit, String>`
- 添加方法 `List<UserBusinessUnit> findByUserId(String userId)` 查找用户所属 BU
- 添加方法 `List<UserBusinessUnit> findByBusinessUnitIdIn(List<String> businessUnitIds)` 批量查找 BU 成员

---

**文件**: `backend/user-portal/src/main/java/com/portal/dto/DashboardOverview.java`

**变更 4: TaskOverview DTO 新增团队字段**
- 在 `TaskOverview` 内部类中新增：
  - `private Long teamPendingCount;`
  - `private Long teamOverdueCount;`
  - `private Long teamCompletedTodayCount;`

---

**文件**: `backend/user-portal/src/main/java/com/portal/component/DashboardComponent.java`

**变更 5: getTaskOverview 新增团队聚合逻辑**
- 注入 `BusinessUnitRepository` 和 `UserBusinessUnitRepository`
- 在 `getTaskOverview(userId)` 方法中新增：
  1. 通过 `userBusinessUnitRepository.findByUserId(userId)` 获取用户所属 BU ID 列表
  2. 对每个 BU ID，递归调用 `businessUnitRepository.findByParentIdAndStatus(buId, "ACTIVE")` 收集所有子 BU ID
  3. 通过 `userBusinessUnitRepository.findByBusinessUnitIdIn(allBuIds)` 收集所有团队成员 userId
  4. 对每个团队成员 userId，查询其待办任务并聚合 teamPendingCount、teamOverdueCount
  5. 对每个团队成员 userId，查询 Flowable 已完成任务并聚合 teamCompletedTodayCount
  6. 当用户不属于任何 BU 时，团队指标回退为个人指标值
- 使用 try-catch 包裹团队查询逻辑，失败时回退为个人指标值并记录警告日志

---

**文件**: `frontend/user-portal/src/api/dashboard.ts`

**变更 6: TaskOverview 接口新增团队字段**
- 在 `TaskOverview` 接口中新增 `teamPendingCount`、`teamOverdueCount`、`teamCompletedTodayCount`

---

**文件**: `frontend/user-portal/src/views/dashboard/index.vue`

**变更 7: Task Overview 卡片改为六指标布局**
- 第一行（个人）：pendingCount（我的待办）、overdueCount（我的逾期）、completedTodayCount（我的今日完成）
- 第二行（团队）：teamPendingCount（团队待办）、teamOverdueCount（团队逾期）、teamCompletedTodayCount（团队今日完成）
- 移除 urgentCount 和 highPriorityCount 的展示
- 更新 `taskOverview` ref 的初始值，新增三个团队字段默认值为 0

## 测试策略

### 验证方法

测试策略分两阶段：首先在未修复代码上发现反例以确认 Bug，然后验证修复的正确性和现有行为的保持。

### 探索性 Bug 条件检查

**目标**: 在实施修复前，发现能证明 Bug 存在的反例。确认或否定根因分析。如果否定，需要重新假设。

**测试计划**: 编写单元测试调用 `DashboardComponent.getTaskOverview(userId)`，断言返回的 `TaskOverview` 包含非空的团队字段。在未修复代码上运行这些测试以观察失败。

**测试用例**:
1. **团队字段缺失测试**: 调用 `getTaskOverview`，断言 `teamPendingCount` 不为 null（未修复代码将失败）
2. **团队聚合测试**: Mock 用户属于一个有子 BU 的 BU，断言团队指标包含子 BU 成员的任务（未修复代码将失败）
3. **无 BU 用户回退测试**: Mock 用户不属于任何 BU，断言团队指标等于个人指标（未修复代码将失败）
4. **前端字段绑定测试**: 检查前端模板是否绑定 `teamPendingCount` 等字段（未修复代码将失败）

**预期反例**:
- `TaskOverview` 对象中不存在 `teamPendingCount`、`teamOverdueCount`、`teamCompletedTodayCount` 字段
- 可能原因：DTO 缺少字段、后端无团队聚合逻辑、前端未绑定团队数据

### 修复检查

**目标**: 验证对于所有满足 Bug 条件的输入，修复后的函数产生期望行为。

**伪代码:**
```
FOR ALL input WHERE isBugCondition(input) DO
  result := getTaskOverview_fixed(input.userId)
  ASSERT result.teamPendingCount IS NOT NULL
  ASSERT result.teamOverdueCount IS NOT NULL
  ASSERT result.teamCompletedTodayCount IS NOT NULL
  
  teamMemberIds := collectTeamMemberIds(input.userId)
  expectedPending := countPendingTasks(teamMemberIds)
  expectedOverdue := countOverdueTasks(teamMemberIds)
  expectedCompleted := countCompletedToday(teamMemberIds)
  
  ASSERT result.teamPendingCount == expectedPending
  ASSERT result.teamOverdueCount == expectedOverdue
  ASSERT result.teamCompletedTodayCount == expectedCompleted
END FOR
```

### 保持性检查

**目标**: 验证对于所有不满足 Bug 条件的输入，修复后的函数产生与原始函数相同的结果。

**伪代码:**
```
FOR ALL input WHERE NOT isBugCondition(input) DO
  ASSERT getTaskOverview_original(input).pendingCount == getTaskOverview_fixed(input).pendingCount
  ASSERT getTaskOverview_original(input).overdueCount == getTaskOverview_fixed(input).overdueCount
  ASSERT getTaskOverview_original(input).completedTodayCount == getTaskOverview_fixed(input).completedTodayCount
  ASSERT getDashboardOverview_original(input).processOverview == getDashboardOverview_fixed(input).processOverview
  ASSERT getDashboardOverview_original(input).performanceOverview == getDashboardOverview_fixed(input).performanceOverview
END FOR
```

**测试方法**: 推荐使用属性基测试（Property-Based Testing）进行保持性检查，因为：
- 自动生成大量测试用例覆盖输入域
- 捕获手动单元测试可能遗漏的边界情况
- 对所有非 Bug 输入的行为不变性提供强保证

**测试计划**: 先在未修复代码上观察个人指标和其他模块的行为，然后编写属性基测试捕获该行为。

**测试用例**:
1. **个人指标保持测试**: 验证修复后个人 pendingCount、overdueCount、completedTodayCount 与修复前一致
2. **流程概览保持测试**: 验证修复后 ProcessOverview 数据与修复前完全一致
3. **个人绩效保持测试**: 验证修复后 PerformanceOverview 数据与修复前完全一致
4. **API 兼容性保持测试**: 验证 `/dashboard/task-overview` 返回的 JSON 仍包含所有原有字段

### 单元测试

- 测试 `getTaskOverview` 对有 BU 用户返回正确的团队指标
- 测试 `getTaskOverview` 对无 BU 用户的回退逻辑
- 测试递归子 BU 查找的正确性（多层嵌套）
- 测试团队成员 ID 收集的去重逻辑
- 测试 Flowable 不可用时的优雅降级

### 属性基测试

- 生成随机 BU 层级结构和用户分配，验证团队指标等于所有团队成员个人指标之和
- 生成随机用户 ID，验证个人指标在修复前后保持一致
- 生成随机 Dashboard 请求，验证非 TaskOverview 模块的数据不受影响

### 集成测试

- 测试完整 Dashboard 数据加载流程，验证六个指标均有值
- 测试 BU 层级变更后团队指标的更新
- 测试前端 Task Overview 卡片正确渲染六个指标
