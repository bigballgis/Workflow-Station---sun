# Bugfix 需求文档

## 简介

Dashboard 页面（`/dashboard`）的 Task Overview 卡片目前只显示个人级别的任务统计。需要扩展为同时显示个人指标和团队指标两组数据：
- 个人指标：My Pending Tasks、My Overdue Tasks、My Completed Today
- 团队指标：Team Pending Tasks、Team Overdue Tasks、Team Completed Today

其中 "Team" 定义为：当前用户所属的 BU（Business Unit）及该 BU 下所有子 BU 的全部成员。

## Bug 分析

### 当前行为（缺陷）

1.1 Task Overview 卡片仅显示个人级别的 pendingCount、overdueCount、completedTodayCount，缺少团队级别的统计数据。

1.2 后端 `getTaskOverview` 方法仅查询当前用户 `userId` 的任务数据，没有基于 BU 层级的团队级别聚合查询。

1.3 前端 Task Overview 卡片没有区分个人指标和团队指标的展示区域。

### 期望行为（正确）

2.1 后端 `TaskOverview` DTO 应新增团队级别字段：`teamPendingCount`、`teamOverdueCount`、`teamCompletedTodayCount`。

2.2 后端 `getTaskOverview` 应：
  - 通过 `sys_user_business_units` 表查询当前用户所属的 BU
  - 通过 `sys_business_units` 表的 `parent_id` 递归查询该 BU 及其所有子 BU
  - 收集这些 BU 下所有成员的用户ID
  - 分别聚合团队待办数（teamPendingCount）、团队逾期数（teamOverdueCount）、团队今日完成数（teamCompletedTodayCount）

2.3 后端应保留现有的个人级别字段（`pendingCount`、`overdueCount`、`completedTodayCount`），继续查询当前用户个人的任务数据。

2.4 前端 Task Overview 卡片应同时展示六个指标：
  - 第一行（个人）：My Pending Tasks、My Overdue Tasks、My Completed Today
  - 第二行（团队）：Team Pending Tasks、Team Overdue Tasks、Team Completed Today

2.5 当用户不属于任何 BU 时，团队指标应回退到与个人指标相同的值，确保 Dashboard 正常运行。

2.6 前端 Task Overview 部分移除 urgentCount 和 highPriorityCount 的显示。

### 不变行为（回归防护）

3.1 当用户查看流程概览、个人绩效、快捷操作或最近任务部分时，系统应继续以现有行为显示，不受影响。

3.2 `/dashboard/task-overview` API 端点应继续返回 `TaskOverview` DTO 结构，新增字段为向后兼容的扩展，不破坏现有字段。

3.3 当 Flowable 工作流引擎不可用时，系统应继续优雅地处理错误并记录警告，返回零计数而非崩溃。
