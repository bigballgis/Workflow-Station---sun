# 实施计划：BI Management 模块

## 概述

基于需求文档和技术设计文档，将 BI Management 模块的实现拆分为 8 个阶段：数据库建表与枚举/实体层、Repository 与 DTO 层、同步组件、Service 层、Controller 层、Superset API 客户端与 Guest Token、Admin Center 前端、User Portal 前端。每个阶段的任务按增量方式构建，后续任务依赖前序任务的产出。

## 任务

- [x] 1. 数据库建表、枚举与实体类
  - [x] 1.1 创建数据库 DDL 脚本
    - 在 `backend/admin-center/src/main/resources/db/migration/` 下创建 Flyway 迁移脚本（或手动执行的 SQL 文件）
    - 包含 `bi_dashboard_registry`、`bi_dashboard_assignment`、`bi_superset_role`、`bi_rbac_mapping` 四张表的 CREATE TABLE 语句及索引
    - _需求: 1.2, 2.1, 7.1, 7.9_

  - [x] 1.2 创建枚举类
    - 在 `com.admin.bi.enums` 包下创建 `DashboardStatus`（ACTIVE/AUTO_INACTIVE/MANUAL_INACTIVE）、`AssignmentTargetType`（USER/ROLE/BUSINESS_UNIT）、`LayoutMode`（SINGLE/MULTI/WIDGET）、`SupersetRoleStatus`（ACTIVE/INACTIVE）
    - _需求: 1.2, 2.1, 7.1_

  - [x] 1.3 创建实体类
    - 在 `com.admin.bi.entity` 包下创建 `BiDashboardRegistry`、`BiDashboardAssignment`、`BiSupersetRole`、`BiRbacMapping` 实体
    - 遵循项目现有 Entity 模式：`@Entity`, `@Table`, `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@EqualsAndHashCode(of = "id")`, `@EntityListeners(AuditingEntityListener.class)`, String ID（UUID 格式）
    - `BiSupersetRole` 使用 `@GeneratedValue(strategy = GenerationType.IDENTITY)` 的 Integer 主键
    - _需求: 1.2, 2.1, 7.1, 7.9_

- [x] 2. Repository 与 DTO 层
  - [x] 2.1 创建 Repository 接口
    - 在 `com.admin.bi.repository` 包下创建 `BiDashboardRegistryRepository`、`BiDashboardAssignmentRepository`、`BiSupersetRoleRepository`、`BiRbacMappingRepository`
    - `BiDashboardRegistryRepository` 包含按 `supersetDashboardId` 查询、按 status 筛选、按 title/tags 模糊搜索的方法
    - `BiDashboardAssignmentRepository` 包含按 dashboard_id 查询、按 target_type+target_id 查询、唯一性校验方法
    - `BiSupersetRoleRepository` 包含按 `supersetRoleId` 查询、按 status 筛选的方法
    - `BiRbacMappingRepository` 包含按 `sysRoleId` 查询、按 `sysRoleId` 删除的方法
    - _需求: 1.3, 1.13, 2.4, 2.5, 7.1, 7.9_

  - [x] 2.2 创建 Request DTO
    - 在 `com.admin.bi.dto.request` 包下创建 `DashboardRegistryUpdateRequest`（tags, isDefaultLanding）、`DashboardAssignmentCreateRequest`（dashboardId, targetType, targetId, layoutMode, displayOrder, isDefault）、`DashboardStatusUpdateRequest`（status）、`RbacMappingUpdateRequest`（supersetRoleIds）、`GuestTokenRequest`（dashboardId）
    - 使用 `@Valid` 和 Bean Validation 注解
    - _需求: 1.14, 2.1, 4.1, 7.9_

  - [x] 2.3 创建 Response DTO
    - 在 `com.admin.bi.dto.response` 包下创建 `DashboardRegistryResponse`、`DashboardAssignmentResponse`、`SyncResultResponse`（created, updated, autoInactivated, syncedAt）、`RbacMappingResponse`、`SupersetRoleResponse`、`UserDashboardResponse`、`GuestTokenResponse`（token, dashboardEmbedId）
    - _需求: 1.6, 1.13, 2.5, 2.8, 4.1, 7.7, 7.11_

- [x] 3. 自定义异常类
  - [x] 3.1 创建 BI 模块自定义异常
    - 在 `com.admin.exception` 包下创建 `DashboardNotFoundException`、`DashboardInactiveException`、`DashboardHasAssignmentsException`、`DuplicateAssignmentException`、`AssignmentTargetNotFoundException`、`SupersetSyncException`、`SupersetApiException`
    - 继承现有 `AdminBusinessException` 模式
    - _需求: 1.8, 1.16, 2.2, 2.3, 2.4, 4.3_

- [x] 4. 检查点 - 确保编译通过
  - 确保所有实体、Repository、DTO、异常类编译无误，如有问题请向用户确认。

- [x] 5. Dashboard 同步组件与配置
  - [x] 5.1 新增 BI 配置属性类
    - 在 `com.admin.bi.config` 包下创建 `BiProperties`（使用 `@ConfigurationProperties(prefix = "bi")`）
    - 包含 Superset 连接配置（host, adminUsername, adminPassword, guestTokenTimeoutSeconds）和同步配置（cron, enabled）
    - 在 `application.yml` 中新增 `bi.superset.*` 和 `bi.sync.*` 配置项
    - _需求: 1.7, 4.4, 7.8_

  - [x] 5.2 实现 DashboardSyncComponent
    - 在 `com.admin.bi.component` 包下创建 `DashboardSyncComponent`
    - 使用 `JdbcTemplate` 查询 Superset 的 `public.dashboards JOIN public.embedded_dashboards`，筛选 `published = true`
    - 实现完整同步逻辑：新增（ACTIVE）、更新（保留本地扩展字段）、AUTO_INACTIVE 恢复为 ACTIVE、MANUAL_INACTIVE 保持不变、不再符合条件的设为 AUTO_INACTIVE
    - 返回 `SyncResultResponse`（created/updated/autoInactivated）
    - 使用 `@Scheduled` 实现定时同步（cron 表达式从配置读取）
    - 异常处理：捕获数据库连接异常，记录日志，已有数据不变
    - _需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.10_

  - [x] 5.3 编写 Dashboard 同步正确性属性测试
    - **Property 1: Dashboard 同步正确性**
    - 使用 jqwik 验证各种 Superset 数据与本地 Registry 状态组合下的同步结果
    - **验证需求: 1.1, 1.2, 1.3, 1.4, 1.5, 1.9, 1.10**

  - [x] 5.4 编写同步摘要准确性属性测试
    - **Property 2: 同步摘要准确性**
    - 验证 SyncResultResponse 中 created/updated/autoInactivated 数量与实际变更一致
    - **验证需求: 1.6, 7.7**

  - [x] 5.5 编写同步错误恢复属性测试
    - **Property 3: Dashboard 同步错误恢复**
    - 验证同步过程中发生异常时，Registry 数据保持不变
    - **验证需求: 1.8**

- [x] 6. Superset 角色同步组件
  - [x] 6.1 实现 SupersetRoleSyncComponent
    - 在 `com.admin.bi.component` 包下创建 `SupersetRoleSyncComponent`
    - 使用 `JdbcTemplate` 查询 Superset 的 `public.ab_role` 表
    - 实现同步逻辑：新增（ACTIVE）、更新 name、不存在的标记 INACTIVE、INACTIVE 恢复为 ACTIVE
    - 返回同步摘要（created/updated/inactivated）
    - 使用 `@Scheduled` 实现定时同步
    - _需求: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_

  - [x] 6.2 编写 Superset 角色同步正确性属性测试
    - **Property 13: Superset 角色同步正确性**
    - 验证各种 ab_role 数据与本地 bi_superset_role 状态组合下的同步结果
    - **验证需求: 7.1, 7.2, 7.3, 7.4, 7.5**

  - [x] 6.3 编写 Superset 角色同步错误恢复属性测试
    - **Property 14: Superset 角色同步错误恢复**
    - 验证同步异常时 bi_superset_role 数据保持不变
    - **验证需求: 7.6**

- [x] 7. 检查点 - 确保同步组件编译通过并通过测试
  - 确保所有同步组件编译无误，如有问题请向用户确认。

- [x] 8. Service 层实现
  - [x] 8.1 实现 BiDashboardRegistryService
    - 在 `com.admin.bi.service` 包下创建接口，在 `com.admin.bi.service.impl` 下创建实现类
    - 实现：分页查询（支持 title/tags/status 筛选）、获取详情、更新本地扩展字段、切换状态（启用/禁用）、删除（检查关联分配）、触发手动同步
    - _需求: 1.6, 1.11, 1.12, 1.13, 1.14, 1.15, 1.16_

  - [x] 8.2 编写 Dashboard 状态切换往返属性测试
    - **Property 4: Dashboard 状态手动切换往返**
    - 验证 enable(disable(dashboard)) 恢复为 ACTIVE
    - **验证需求: 1.11, 1.12**

  - [x] 8.3 编写 Dashboard 列表筛选正确性属性测试
    - **Property 5: Dashboard 列表筛选正确性**
    - 验证筛选结果的完整性和准确性
    - **验证需求: 1.13**

  - [x] 8.4 编写本地扩展字段更新往返属性测试
    - **Property 6: 本地扩展字段更新往返**
    - 验证更新 tags/is_default_landing 后其他字段不变
    - **验证需求: 1.14**

  - [x] 8.5 编写 Dashboard 删除与分配关联守卫属性测试
    - **Property 7: Dashboard 删除与分配关联守卫**
    - 验证有关联分配时拒绝删除，无关联时成功删除
    - **验证需求: 1.15, 1.16**

  - [x] 8.6 实现 BiDashboardAssignmentService
    - 创建接口和实现类
    - 实现：创建分配（校验 Dashboard 存在且 ACTIVE、Target 存在、唯一性）、分页查询（支持 targetType/dashboardTitle 筛选）、更新分配、删除分配、获取用户有效 Dashboard 列表（合并 User/Role/BU 维度，去重，优先级 USER > ROLE > BU，按 displayOrder 排序）
    - 需要注入 UserPermissionService 或相关 Service 获取用户的 Role IDs 和 Business Unit IDs
    - _需求: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 2.8_

  - [x] 8.7 编写 Assignment 创建验证属性测试
    - **Property 8: Assignment 创建验证**
    - 验证创建条件：Dashboard 存在且 ACTIVE、Target 存在、无重复
    - **验证需求: 2.1, 2.2, 2.3, 2.4**

  - [x] 8.8 编写用户 Dashboard 合并去重与优先级属性测试
    - **Property 9: 用户 Dashboard 合并去重与优先级**
    - 验证合并结果仅含 ACTIVE Dashboard、按 displayOrder 排序、去重优先级正确
    - **验证需求: 2.5, 2.6**

  - [x] 8.9 编写 Assignment 列表筛选正确性属性测试
    - **Property 10: Assignment 列表筛选正确性**
    - 验证筛选结果满足所有指定条件
    - **验证需求: 2.8**

  - [x] 8.10 实现 BiRbacMappingService
    - 创建接口和实现类
    - 实现：查询 RBAC 映射列表（支持 roleName/roleType 筛选）、更新映射（全量替换）、获取所有已同步 Superset 角色列表、触发手动同步
    - 仅允许映射 ACTIVE 状态的 Superset_Role
    - _需求: 7.7, 7.9, 7.10, 7.11, 7.12, 7.13_

  - [x] 8.11 编写 RBAC 映射全量替换属性测试
    - **Property 15: RBAC 映射全量替换**
    - 验证更新后映射恰好等于提交的 Superset_Role 集合
    - **验证需求: 7.9, 7.10**

  - [x] 8.12 编写 RBAC 映射 ACTIVE 约束属性测试
    - **Property 16: RBAC 映射 ACTIVE 约束**
    - 验证仅 ACTIVE 的 Superset_Role 可被映射，INACTIVE 的在有效映射查询中被排除
    - **验证需求: 7.12, 7.13**

- [x] 9. Superset API 客户端与 Guest Token Service
  - [x] 9.1 实现 SupersetApiClient
    - 在 `com.admin.bi.client` 包下创建 `SupersetApiClient`
    - 使用 `RestTemplate` 封装 Superset REST API 调用
    - 实现：先调用 `/api/v1/security/login` 获取 access token，再调用 `/api/v1/security/guest_token/` 获取 Guest Token
    - 从 `BiProperties` 读取 Superset 连接配置
    - 处理超时和错误，抛出 `SupersetApiException`
    - _需求: 4.1, 4.3, 4.4_

  - [x] 9.2 实现 BiGuestTokenService
    - 创建接口和实现类
    - 实现：验证用户是否被分配了请求的 Dashboard（调用 AssignmentService）、根据用户 Sys_Role 查询 RBAC 映射获取 Superset_Role 列表（合并去重）、调用 SupersetApiClient 获取 Guest Token
    - 未分配时返回 403 Forbidden
    - _需求: 4.1, 4.2, 4.3, 7.14, 7.15_

  - [x] 9.3 编写 Guest Token 授权守卫属性测试
    - **Property 11: Guest Token 授权守卫**
    - 验证未分配 Dashboard 的用户请求 Guest Token 返回 403
    - **验证需求: 4.2**

  - [x] 9.4 编写 Guest Token 角色合并属性测试
    - **Property 17: Guest Token 角色合并**
    - 验证多 Sys_Role 用户的 Superset_Role 为去重并集
    - **验证需求: 7.14, 7.15**

- [x] 10. 检查点 - 确保 Service 层和 Guest Token 编译通过并通过测试
  - 确保所有 Service、Client 编译无误，如有问题请向用户确认。

- [x] 11. Controller 层实现
  - [x] 11.1 实现 BiDashboardRegistryController
    - 在 `com.admin.bi.controller` 包下创建，使用 `@RestController`, `@RequestMapping("/bi/dashboards")`, `@RequiredArgsConstructor`, `@Slf4j`
    - 实现：POST `/sync`（手动同步）、GET（分页查询）、GET `/{id}`（详情）、PUT `/{id}`（更新本地字段）、PUT `/{id}/status`（切换状态）、DELETE `/{id}`（删除）
    - 使用 `@RequestHeader("X-User-Id")` 获取用户上下文
    - 返回 `ResponseEntity`，使用 `PageResult<T>` 分页
    - _需求: 1.6, 1.11, 1.12, 1.13, 1.14, 1.15, 1.16, 3.2_

  - [x] 11.2 实现 BiDashboardAssignmentController
    - 创建 `@RestController`，`@RequestMapping("/bi/assignments")`
    - 实现：POST（创建分配）、GET（分页查询）、PUT `/{id}`（更新）、DELETE `/{id}`（删除）、GET `/user/{userId}`（用户有效 Dashboard 列表）
    - _需求: 2.1, 2.5, 2.7, 2.8_

  - [x] 11.3 实现 BiRbacMappingController
    - 创建 `@RestController`，`@RequestMapping("/bi/rbac")`
    - 实现：POST `/superset-roles/sync`（同步 Superset 角色）、GET `/superset-roles`（角色列表）、GET `/mappings`（映射列表）、PUT `/mappings/{sysRoleId}`（更新映射）
    - _需求: 7.7, 7.9, 7.10, 7.11_

  - [x] 11.4 实现 BiGuestTokenController
    - 创建 `@RestController`，`@RequestMapping("/bi/guest-token")`
    - 实现：POST（获取 Guest Token）
    - _需求: 4.1, 4.2_

  - [x] 11.5 编写审计日志完整性属性测试
    - **Property 12: 审计日志完整性**
    - 验证变更操作（同步、更新、删除、分配创建/删除、状态切换）后存在对应审计日志
    - **验证需求: 3.3**

- [x] 12. 检查点 - 确保后端全部编译通过并通过测试
  - 确保所有 Controller、Service、Component 编译无误，运行全部测试通过，如有问题请向用户确认。

- [x] 13. Admin Center 前端实现
  - [x] 13.1 创建 BI Management API 服务
    - 在 `frontend/admin-center/src/api/biManagement.ts` 中封装所有 BI Management 后端 API 调用
    - 使用现有 `request.ts` 中的 `get/post/put/del` 方法
    - 包含：Dashboard 同步、列表查询、更新、状态切换、删除、分配 CRUD、RBAC 映射查询/更新、Superset 角色同步/列表
    - _需求: 6.1, 6.2, 6.3, 6.9, 6.12, 6.13_

  - [x] 13.2 创建 Dashboard Registry 页面
    - 在 `frontend/admin-center/src/views/bi-management/DashboardRegistry.vue` 中实现
    - 表格列：Dashboard_Title、Embed_ID、Superset_Dashboard_UUID、Tags、Is_Default_Landing、Dashboard_Status（显示为"有效"/"手动失效"/"自动失效"）、Last_Synced_At
    - 顶部"同步 Dashboard"按钮，同步完成后显示摘要（新增/更新/自动失效数量）并刷新列表
    - 筛选：按 title、tags、status 筛选
    - 操作按钮：编辑（仅 Tags、Is_Default_Landing）、启用/禁用（AUTO_INACTIVE 时禁用"启用"按钮）、删除
    - _需求: 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8_

  - [x] 13.3 创建 Dashboard Assignment 页面
    - 在 `frontend/admin-center/src/views/bi-management/DashboardAssignment.vue` 中实现
    - 表格列：Dashboard_Title、Assignment_Target_Type、Target Name、Layout_Mode、Display Order
    - 新增表单：Dashboard 下拉（仅 ACTIVE）、Target Type 选择后动态加载 User/Role/BU 下拉、Layout_Mode、Display Order、Is_Default
    - 操作按钮：编辑、删除
    - 筛选：按 targetType、dashboardTitle 筛选
    - _需求: 6.9, 6.10_

  - [x] 13.4 创建 RBAC Mapping 页面
    - 在 `frontend/admin-center/src/views/bi-management/RbacMapping.vue` 中实现
    - 表格列：Sys_Role Name、Sys_Role Code、Sys_Role Type、已映射 Superset_Role 列表、Last_Updated_At
    - 顶部"同步 Superset Role"按钮
    - 编辑映射：穿梭框或多选复选框展示所有 ACTIVE 的 Superset_Role，已映射的默认选中
    - 筛选：按 roleName、roleType 筛选
    - _需求: 6.12, 6.13, 6.14, 6.15_

  - [x] 13.5 配置 Admin Center 路由
    - 在 `frontend/admin-center/src/router/index.ts` 中新增 BI Management 路由组
    - 一级菜单 "BI Management"，包含三个子路由：`/bi-management/dashboard-registry`、`/bi-management/dashboard-assignment`、`/bi-management/rbac-mapping`
    - 所有路由要求 `requiresAuth: true`
    - _需求: 6.1, 6.11_

- [x] 14. User Portal 前端实现
  - [x] 14.1 创建 User Portal BI Dashboard API 服务
    - 在 `frontend/user-portal/src/api/biDashboard.ts` 中封装 API 调用
    - 包含：获取当前用户有效 Dashboard 列表、获取 Guest Token
    - _需求: 5.1, 5.6_

  - [x] 14.2 创建 Dashboard Landing 页面
    - 在 `frontend/user-portal/src/views/landing/DashboardLanding.vue` 中实现
    - 使用 `@superset-ui/embedded-sdk` 的 `embedDashboard` 方法渲染
    - 布局模式：SINGLE（全屏）、MULTI（标签页，默认显示 Is_Default）、WIDGET（网格卡片，点击展开全屏）
    - 空状态提示
    - Guest Token 过期自动重新请求并刷新渲染
    - _需求: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7_

  - [x] 14.3 配置 User Portal 路由
    - 在 `frontend/user-portal/src/router/index.ts` 中新增或修改 Landing Page 路由，指向 DashboardLanding 组件
    - _需求: 5.1_

- [x] 15. 检查点 - 确保前端编译通过
  - 确保 Admin Center 和 User Portal 前端编译无误，如有问题请向用户确认。

- [x] 16. 端到端集成与收尾
  - [x] 16.1 集成联调与数据流验证
    - 确保后端 Controller → Service → Repository → Database 数据流完整
    - 确保前端 API 调用 → 后端接口 → 数据库的完整链路
    - 验证 Superset 同步 → 本地注册 → 分配 → Guest Token → 嵌入渲染的完整流程
    - _需求: 1.1-1.16, 2.1-2.8, 4.1-4.4, 5.1-5.7, 7.1-7.15_

- [x] 17. 最终检查点 - 确保所有测试通过
  - 确保所有测试通过，如有问题请向用户确认。

## 备注

- 标记 `*` 的任务为可选任务，可跳过以加速 MVP 交付
- 每个任务引用了具体的需求编号以确保可追溯性
- 属性测试验证通用正确性属性，单元测试验证具体示例和边界情况
- 检查点任务确保增量验证，及时发现问题
- 后端使用 Java 17 + Spring Boot 3.2，前端使用 Vue 3 + TypeScript + Element Plus
- 属性测试使用 jqwik 1.8.2（已在 pom.xml 中配置）
