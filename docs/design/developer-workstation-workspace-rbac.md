# Developer Workstation：功能单元工作区隔离（Technical Lead / Team Lead / Developer）

本文档描述 **developer-workstation** 中围绕 **FunctionUnit（功能单元）** 的工作区访问模型：与 admin-center 的开发者角色、虚拟开发组、JWT 权限如何配合，以及实现落点与已知边界。

## 目标

- **Technical Lead**：可访问全部功能单元（与平台 `ADMIN` 类型角色一致的全量放行）。
- **Team Lead**：对自己 **创建** 的功能单元拥有完整操作；删除与「分配虚拟开发组」单独约束（见下）。
- **Developer**：仅可访问被 **分配到自己所属虚拟开发组** 的功能单元（只读/修改按操作类型区分）。
- 列表与详情一致：**不可见即不可枚举**（分页列表按可见 ID 集合过滤）。

## 核心规则

| 维度 | 说明 |
|------|------|
| 创建者标识 | 与 JPA Auditing 一致，使用 **`FunctionUnit.createdBy` = 当前用户 username**（字符串），不是 userId。 |
| Team Lead 删除 | 仅当 `createdBy` 等于当前 username 时允许删除（`WorkspaceAccessAction.DELETE`）。 |
| Team Lead 分配组 | 仅创建者可 `PUT /function-units/{id}/dev-groups`（`ASSIGN_DEV_GROUPS`）；需同时具备 JWT 权限 `FUNCTION_UNIT_ASSIGN_DEV_GROUP`。 |
| Developer 可见性 | 用户所属虚拟组 ID 来自 `sys_virtual_group_members`；功能单元侧映射表为 `dw_function_unit_dev_groups`（`virtual_group_id` 存 `sys_virtual_groups.id`）。 |
| 平台管理员 | `RoleRepository.userHasActiveAdminTypeRole(userId)` 为真时，工作区规则全放行。判定与登录侧一致：除 `sys_user_roles`、`sys_virtual_group_roles` 外，包含 **`sys_role_assignments`**（`USER` 直接分配与 `VIRTUAL_GROUP` 经组成员展开，且尊重 `valid_from`/`valid_to`）。测试账号 `44027893` 等若仅有 `sys_role_assignments` 中的 `SYS_ADMIN`，此前未合并该表会导致列表为空。 |

## 数据模型

- **表**：`dw_function_unit_dev_groups`（Flyway / `deploy/init-scripts/00-schema/28-dw-function-unit-dev-groups.sql`）。
- **实体**：`FunctionUnitDevGroupAssignment`；**仓库**：`FunctionUnitDevGroupAssignmentRepository`。
- **组成员查询**：`VirtualGroupMembershipDao`（`JdbcTemplate` → `sys_virtual_group_members`）。

## HTTP 层

- **拦截器**：`FunctionUnitWorkspaceAccessInterceptor`（注册于 `DeveloperWebMvcConfig`）。
- **路径模式**：
  - `/function-units/**`
  - `/export-import/function-units/**`
  - `/ai-generation/**`（部分接口通过路径或 query 解析 `functionUnitId`，见下）
- **动作映射**：
  - `GET` / `HEAD` → `VIEW`
  - 其他方法 → `MODIFY`（默认）
  - `DELETE /function-units/{id}` → `DELETE`
  - `PUT /function-units/{id}/dev-groups` → `ASSIGN_DEV_GROUPS`；`GET` 同路径 → `VIEW`
- **403 响应**：`WorkspaceExceptionHandler` / 拦截器内写 JSON：`WORKSPACE_FORBIDDEN`。

## 组件层（ defense in depth）

以下在拦截器之外再次校验，避免绕过 URL 模式或 **请求体中的功能单元 ID**（例如 AI 对话无 path 变量）：

- `FunctionUnitComponentImpl`：`list` 过滤、`getByIdAsResponse` / `update` / `publish` / `clone` / `validate` / `getVersionHistory`、删除与 dev-groups 维护等。
- `DeploymentComponentImpl.deployToAdminCenter`：`MODIFY`。
- `ExportImportComponentImpl.exportFunctionUnit`：`VIEW`。
- `AiGenerationComponentImpl`：`chatStream` / `applyGeneratedData` / `undoLastApply`：`MODIFY`。

## Admin Center 权限

- 枚举：`DeveloperPermission.FUNCTION_UNIT_ASSIGN_DEV_GROUP`。
- 角色映射：`DeveloperPermissionService` — **Team Lead** 含分配组、创建、删除等；**Developer** 不含创建、删除、分配组。

初始化数据：`deploy/init-scripts/01-admin/02-init-developer-permissions.sql`。

## API 契约（设计站）

- `GET /function-units/{id}/dev-groups` → `List<String>`（虚拟组 id）
- `PUT /function-units/{id}/dev-groups` + body `DevGroupAssignmentRequest`（`virtualGroupIds`）
- `FunctionUnitResponse` 含 `assignedVirtualGroupIds`（与 GET dev-groups 一致数据源）

## AI 控制器与异常映射

`BaseController.handleRequest` 会捕获所有异常并返回通用 500。对需由 `AiExceptionHandler` 映射的 **`AiLockConflictException`（409）**、**`AiValidationFailedException`（422）**、**`AiGenerationException`**，`AiGenerationController` 中 **`acquireLock`、`applyGeneratedData`、`undoLastApply`** 改为直接调用组件并返回成功响应，使上述异常向上抛出并由 `AiExceptionHandler` 处理。

## 已知边界与后续可做事项

1. **按名称的旧路由**（如 `/api/function-units/{name}`）若仍存在，**未**纳入本拦截器的 path 规则；需单独评估或统一改为按 ID。
2. **`POST /ai-generation/chat/stream`** 的 `functionUnitId` 仅在 **JSON body**，拦截器无法读取；已在 `AiGenerationComponentImpl.chatStream` 内断言。
3. **其他未携带功能单元 ID 的 AI 子接口**：若未来增加，需同样保证能从 path、query 或 body 解析并校验。
4. **未登录用户**：拦截器在 `getCurrentUserId` 为空时直接放行，由 Spring Security 与其他过滤器负责认证。

## 相关类索引

| 类 | 职责 |
|----|------|
| `FunctionUnitWorkspaceAccessService` | `canAccess` / `assertCanAccess` / `visibleFunctionUnitIds` |
| `FunctionUnitWorkspaceAccessDeniedException` | 业务拒绝 |
| `WorkspaceAccessAction` | VIEW / MODIFY / DELETE / ASSIGN_DEV_GROUPS |
| `FunctionUnitWorkspaceAccessInterceptor` | 按 URL 解析 ID 并断言 |
| `WorkspaceExceptionHandler` | 403 统一格式 |

## 相关文档

- 门户终端用户的 **业务单元角色（UBR）、工作台上下文、BU_UNBOUNDED 仅虚拟组** 等规则见 [portal-bu-rbac.md](./portal-bu-rbac.md)（与本文 developer 工作区模型互补，不重复展开）。

---

*文档版本与实现一致时可随 DDL 或路由变更更新本节。*
