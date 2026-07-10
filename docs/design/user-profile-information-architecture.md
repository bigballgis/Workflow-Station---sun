# 用户信息展示信息架构（分端）

本文约定三个前端应用中「个人中心 / 顶栏用户菜单」应展示的内容边界与术语，与仓库内领域模型约定（`.cursor/rules/domain-model.mdc`）及门户工作台 RBAC 对齐。

## 1. 分端定位

| 应用 | 用户心智 | 信息重点 |
|------|----------|----------|
| **user-portal** | 终端业务用户 | 当前工作台（BU·角色）、流程与权限申请相关的成员身份、虚拟组、生效角色 |
| **admin-center** | 平台管理员 | 平台账户、登录身份、管理操作所需的权限/角色摘要（不强调门户「工作台」叙事） |
| **developer-workstation** | 设计/实施人员 | 设计器登录账户即可；不强调业务单元/虚拟组（与功能单元设计无直接关系） |

## 2. 字段矩阵（是否展示）

| 信息项 | user-portal | admin-center | developer-workstation |
|--------|-------------|--------------|-------------------------|
| 显示名、用户名、邮箱 | ✓ | ✓ | ✓ |
| 界面语言 | ✓（展示为可读名称） | ✓ | ✓ |
| 账户 ID（技术主键） | ✓（归入账户区，次要） | ✓（次要） | ✓（次要） |
| **当前工作台**（JWT：BU + 角色） | ✓ **主展示** | ✗ | ✗ |
| 业务单元**成员**（成员身份） | ✓ | ✓（与「用户管理」语义一致） | ✗ |
| 虚拟组成员 | ✓ | ✓ | ✗ |
| 生效角色（聚合列表） | ✓（可注明与门户权限关系） | ✓ | 可选一句说明，不拉组织明细 |
| 权限码列表 | ✗（门户用能力/菜单体现） | 可选折叠/数量 | ✗ |
| 修改密码 | ✓ | ✓ | ✓ |

## 3. 交互分层

- **顶栏下拉**：窄空间 → 账户摘要 + **门户**增加「当前工作台」一行；更多明细进「个人中心」。
- **个人中心页**：分卡片/分区展示，避免单表堆砌；技术字段（账户 ID）与业务字段分区。

## 4. 术语

- 使用「账户编号」或 i18n 等价文案，避免裸露 `User ID`。
- 「当前工作台」与顶栏 `WorkspaceContextBar` 一致：业务单元名称 · 角色名称。
- 门户角色列表旁可加简短说明：含 BU 绑定角色与平台级（虚拟组带来）角色的聚合视图。

## 5. 相关代码入口

- 门户：`frontend/user-portal/src/views/profile/index.vue`、`UserProfileDropdown.vue`
- **实现注意**：顶栏下拉挂在 `PortalLayout` 上跨路由常驻，不可用「仅 `computed(() => getUser())`」依赖 localStorage；需在路由变化或 `getCurrentUser` 刷新后写入 `ref`，否则从个人中心返回时工作台摘要可能不更新。
- **权限视图解析**：门户 `getMyPermissionView` 载荷解析集中在 `frontend/user-portal/src/utils/myPermissionView.ts`，由个人中心与 `UserProfileDropdown` 共用。
- 管理端：`frontend/admin-center/src/views/profile/index.vue`、`UserProfileDropdown.vue`
- 设计器：`frontend/developer-workstation/src/views/profile/index.vue`、`UserProfileDropdown.vue`
