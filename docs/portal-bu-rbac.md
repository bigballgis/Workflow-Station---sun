# User Portal：业务单元角色（UBR）与工作台上下文

本文档描述 **user-portal** 中与 **业务单元（BU）+ 角色** 相关的终端用户模型，与 admin-center 中的 `sys_user_business_unit_roles` 等表一致；与 [developer-workstation-workspace-rbac.md](./developer-workstation-workspace-rbac.md)（设计器工作区）互补。

## 目标

- 用户可持有 **绑定业务单元** 的角色（BU-bound / `BU_BOUNDED`）：权限在「某 BU + 某角色」上下文中解释。
- 用户也可持有 **不绑定具体 BU** 的平台/业务角色（BU-unbounded / `BU_UNBOUNDED`）：由 admin 侧角色配置决定，门户在权限摘要中单独归类。
- **工作台上下文**：登录后可在允许的 `(businessUnitId, roleId)` 组合之间切换，后续请求在该上下文中解析权限（JWT 中带 workspace 声明，具体字段以 `JwtTokenServiceImpl` / `LoginResponse` 为准）。

## 后端落点（索引）

| 区域 | 说明 |
|------|------|
| `PortalWorkspaceAuthService` | 查询 `sys_user_business_unit_roles`，校验 `hasContext`，按 `roleId` 拉取权限码 |
| `AuthController` | 登录、刷新、`/me`；签发含工作台信息的令牌 |
| `UserPermissionController` | `/permissions` 摘要：聚合 UBR 行、无界角色、`virtualGroups` 兼容字段（门户侧可为空数组） |
| `PermissionComponent` / `PermissionController` | 权限申请、列表、审批相关；与 `PermissionRequest`、`submittedBy` 等字段配合 |
| `FunctionUnitAccessComponent` | 流程发起前校验用户业务角色是否允许访问某功能单元 |
| `PortalSelfServiceAccessFilter` / `SecurityConfig` | 自助权限等路径的访问模式（与 `PORTAL_ACCESS_MODE_*` 等常量配合） |

## 与管理后台的关系

- 权威用户/角色/BU 数据在 **admin-center** 库（共享 PostgreSQL schema 或同一库实例，取决于部署）。
- user-portal 通过 **JDBC / RestTemplate** 读取或调用 admin 暴露的能力；**不要在门户侧重复造一套 RBAC 源数据**。

## 相关文档

- 设计器工作区（功能单元 ID、虚拟组）：[developer-workstation-workspace-rbac.md](./developer-workstation-workspace-rbac.md)
- 领域术语：`.cursor/rules/domain-model.mdc`
