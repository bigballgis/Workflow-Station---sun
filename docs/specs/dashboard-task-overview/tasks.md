# 实施计划

- [x] 1. 编写 Bug 条件探索测试
  - **Property 1: Bug Condition** - 团队任务指标缺失
  - **重要**: 在实施修复之前编写此属性基测试
  - **关键**: 此测试在未修复代码上必须失败 — 失败即确认 Bug 存在
  - **不要**在测试失败时尝试修复测试或代码
  - **说明**: 此测试编码了期望行为，修复后测试通过即验证修复正确性
  - **目标**: 发现证明 Bug 存在的反例
  - **Scoped PBT 方法**: 针对确定性 Bug，将属性范围限定到具体失败场景：属于至少一个 BU 的用户调用 `getTaskOverview`
  - 测试内容（来自设计文档 Bug 条件）：
    - Mock 用户属于 BU-A，BU-A 下有子 BU-B，两个 BU 各有成员
    - 调用 `DashboardComponent.getTaskOverview(userId)`
    - 断言返回的 `TaskOverview` 包含 `teamPendingCount`、`teamOverdueCount`、`teamCompletedTodayCount` 且不为 null
    - 断言团队指标值为所有团队成员（BU-A + BU-B 全部成员）的任务聚合统计
  - 在未修复代码上运行测试
  - **预期结果**: 测试失败（这是正确的 — 证明 Bug 存在，因为当前 `TaskOverview` 没有团队字段）
  - 记录发现的反例（例如："TaskOverview 不包含 teamPendingCount 字段" 或 "编译错误：teamPendingCount 不存在"）
  - 测试编写、运行并记录失败后标记任务完成
  - _Requirements: 1.1, 1.2, 2.1, 2.2_

- [x] 2. 编写保持性属性测试（在实施修复之前）
  - **Property 2: Preservation** - 个人指标和其他模块不受影响
  - **重要**: 遵循观察优先方法论
  - 观察：在未修复代码上运行以下场景并记录实际输出：
    - 调用 `getTaskOverview(userId)` 观察个人 `pendingCount`、`overdueCount`、`completedTodayCount` 的值
    - 调用 `getProcessOverview(userId)` 观察 `ProcessOverview` 的完整数据
    - 调用 `getPerformanceOverview(userId)` 观察 `PerformanceOverview` 的完整数据
    - 调用 `getRecentTasks(userId, 5)` 观察最近任务列表
  - 编写属性基测试（来自设计文档保持性需求）：
    - 对于任意用户 ID，个人级别 `pendingCount`、`overdueCount`、`completedTodayCount` 应与修复前一致
    - `ProcessOverview` 数据应与修复前完全一致
    - `PerformanceOverview` 数据应与修复前完全一致
    - 最近任务列表应与修复前完全一致
  - 在未修复代码上运行测试
  - **预期结果**: 测试通过（确认基线行为已被捕获）
  - 测试编写、运行并通过后标记任务完成
  - _Requirements: 3.1, 3.2, 3.3_

- [x] 3. 修复 Dashboard Task Overview 缺少团队指标

  - [x] 3.1 扩展 JPA Repository 扫描配置
    - 修改 `backend/user-portal/src/main/java/com/portal/config/PlatformSecurityConfig.java`
    - 在 `@EnableJpaRepositories` 中新增对 `com.portal.repository` 包的扫描，使新建的 `BusinessUnitRepository` 和 `UserBusinessUnitRepository` 能被 Spring 管理
    - _Bug_Condition: isBugCondition(input) — user-portal 无法访问 BU 数据，因为 JPA 仅扫描了 RoleAssignmentRepository_
    - _Requirements: 2.2_

  - [x] 3.2 新建 BusinessUnitRepository
    - 创建 `backend/user-portal/src/main/java/com/portal/repository/BusinessUnitRepository.java`
    - 继承 `JpaRepository<BusinessUnit, String>`
    - 添加方法 `List<BusinessUnit> findByParentIdAndStatus(String parentId, String status)` 用于递归查找子 BU
    - _Bug_Condition: 缺少 BU 数据访问层导致无法查询团队层级_
    - _Requirements: 2.2_

  - [x] 3.3 新建 UserBusinessUnitRepository
    - 创建 `backend/user-portal/src/main/java/com/portal/repository/UserBusinessUnitRepository.java`
    - 继承 `JpaRepository<UserBusinessUnit, String>`
    - 添加方法 `List<UserBusinessUnit> findByUserId(String userId)` 查找用户所属 BU
    - 添加方法 `List<UserBusinessUnit> findByBusinessUnitIdIn(List<String> businessUnitIds)` 批量查找 BU 成员
    - _Bug_Condition: 缺少用户-BU 映射数据访问层导致无法收集团队成员_
    - _Requirements: 2.2_

  - [x] 3.4 TaskOverview DTO 新增团队字段
    - 修改 `backend/user-portal/src/main/java/com/portal/dto/DashboardOverview.java`
    - 在 `TaskOverview` 内部类中新增：`teamPendingCount`（Long）、`teamOverdueCount`（Long）、`teamCompletedTodayCount`（Long）
    - 保留现有字段 `pendingCount`、`overdueCount`、`completedTodayCount`、`avgProcessingHours`、`urgentCount`、`highPriorityCount` 不变
    - _Expected_Behavior: TaskOverview 应同时包含个人和团队字段_
    - _Preservation: 所有现有字段保持不变，API 向后兼容_
    - _Requirements: 2.1, 2.3, 3.2_

  - [x] 3.5 DashboardComponent 新增团队聚合逻辑
    - 修改 `backend/user-portal/src/main/java/com/portal/component/DashboardComponent.java`
    - 注入 `BusinessUnitRepository` 和 `UserBusinessUnitRepository`
    - 在 `getTaskOverview(userId)` 方法中新增团队聚合逻辑：
      1. 通过 `userBusinessUnitRepository.findByUserId(userId)` 获取用户所属 BU ID 列表
      2. 对每个 BU ID，递归调用 `businessUnitRepository.findByParentIdAndStatus(buId, "ACTIVE")` 收集所有子 BU ID
      3. 通过 `userBusinessUnitRepository.findByBusinessUnitIdIn(allBuIds)` 收集所有团队成员 userId（去重）
      4. 对每个团队成员查询待办任务并聚合 `teamPendingCount`、`teamOverdueCount`
      5. 对每个团队成员查询 Flowable 已完成任务并聚合 `teamCompletedTodayCount`
      6. 当用户不属于任何 BU 时，团队指标回退为个人指标值
    - 使用 try-catch 包裹团队查询逻辑，失败时回退为个人指标值并记录警告日志
    - _Bug_Condition: isBugCondition(input) where userBUs IS NOT EMPTY AND teamPendingCount IS MISSING_
    - _Expected_Behavior: expectedBehavior(result) — teamPendingCount/teamOverdueCount/teamCompletedTodayCount 为团队成员任务聚合值_
    - _Preservation: 个人指标查询逻辑不变，avgProcessingHours/urgentCount/highPriorityCount 计算不变_
    - _Requirements: 2.1, 2.2, 2.3, 2.5, 3.1, 3.3_

  - [x] 3.6 前端 TaskOverview 接口新增团队字段
    - 修改 `frontend/user-portal/src/api/dashboard.ts`
    - 在 `TaskOverview` 接口中新增 `teamPendingCount: number`、`teamOverdueCount: number`、`teamCompletedTodayCount: number`
    - _Requirements: 2.1, 2.4_

  - [x] 3.7 前端 Task Overview 卡片改为六指标布局
    - 修改 `frontend/user-portal/src/views/dashboard/index.vue`
    - 第一行（个人）：`pendingCount`（我的待办）、`overdueCount`（我的逾期）、`completedTodayCount`（我的今日完成）
    - 第二行（团队）：`teamPendingCount`（团队待办）、`teamOverdueCount`（团队逾期）、`teamCompletedTodayCount`（团队今日完成）
    - 移除 `urgentCount` 和 `highPriorityCount` 的展示
    - 更新 `taskOverview` ref 初始值，新增三个团队字段默认值为 0
    - _Expected_Behavior: 卡片展示 3 个人指标 + 3 团队指标_
    - _Preservation: 流程概览、个人绩效、快捷操作、最近任务区域不变_
    - _Requirements: 2.4, 2.6, 3.1_

  - [x] 3.8 验证 Bug 条件探索测试现在通过
    - **Property 1: Expected Behavior** - 团队任务指标正确聚合
    - **重要**: 重新运行任务 1 中的同一测试 — 不要编写新测试
    - 任务 1 的测试编码了期望行为
    - 当此测试通过时，确认期望行为已满足
    - 运行任务 1 中的 Bug 条件探索测试
    - **预期结果**: 测试通过（确认 Bug 已修复）
    - _Requirements: 2.1, 2.2_

  - [x] 3.9 验证保持性测试仍然通过
    - **Property 2: Preservation** - 个人指标和其他模块不受影响
    - **重要**: 重新运行任务 2 中的同一测试 — 不要编写新测试
    - 运行任务 2 中的保持性属性测试
    - **预期结果**: 测试通过（确认无回归）
    - 确认修复后所有测试仍然通过（无回归）

- [x] 4. 检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请询问用户。
