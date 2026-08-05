# User Portal：权限自助（BU / UBR 申请与退出）

本文档记录 **user-portal** 侧「业务单元成员、BU 绑定角色（UBR）」相关的**自助申请、代办申请、退出**，以及 **`|C|=0`（无 UBR）** 时的门户访问模式。与 [portal-bu-rbac.md](./portal-bu-rbac.md)（工作台上下文、JWT、`BU_UNBOUNDED` 获得路径）互补：**本文管「门户能做什么」的产品与规则**；该文档管「身份从哪来」的硬约束。

**定稿日期**：2026-04-04（与产品逐项确认）。

---

## 1. 范围与非范围

### 1.1 范围内（User Portal）

- **加入 BU**（成员）、**申请 BU 绑定角色（UBR）**（在符合准入与成员规则的前提下，以**申请 + 审批**表达意图）。
- **退出 BU（成员）**：移除成员身份；须与「仅移除 UBR」区分（见 §3）。
- **仅移除 UBR**：用户**仍为**该 BU **成员**，只删除指定 `(BU, Role)`。
- **代办**：任意登录用户可为**任意其他用户**提交上述加入/退出类申请（见 §4）。
- **`|C|=0`**：用户仍可登录门户，进入 **「仅权限自助」** 模式（见 §5）。

### 1.2 范围外（明确禁止在 User Portal 出现）

- **虚拟组**：**不提供**加入/退出/申请虚拟组等任何能力。虚拟组仅由 **Admin Center（用户管理人员）** 与 **developer-workstation（开发人员）** 维护，与终端用户门户无关。
- 历史数据中若存在 `VIRTUAL_GROUP` 类型申请：门户 **不展示**（列表/详情均隐藏），见 §6。

---

## 2. 已确认的决策摘要

| 主题 | 决策 |
|------|------|
| 代办与受益人确认 | **无需**受益人确认；**审批人批准**后即可进入自动执行（§7）。 |
| 受益人撤销 | **可以**撤销「他人代自己提交、且尚未审批」的申请。 |
| 重复申请 | **允许**；对同一受益人/目标/同类可多次待审批；**静默**——不因「重复」向用户报错或打断（不强制 409 式交互）。 |
| 选人范围 | 全平台 **启用中**用户可搜（锁定/停用等不可用账号排除，与实现一致）。 |
| 退出语义 | **两种都要**：(1) 退出 BU 成员；(2) 仅移除指定 UBR、保留成员。 |
| 审批通过后执行 | **系统自动落库**（幂等、审计齐全），非「仅批过、人工再在 Admin 点执行」。 |
| 门户准入 | 须属于至少一个 ACTIVE 虚拟组（`SYSTEM`/`CUSTOM`/`DEVELOPER`）；否则登录失败（`PORTAL_ENTITLEMENT_DENIED`）。 |
| 自助模式判定 | 已准入前提下仅看 **`|C|=0`**（无有效 UBR 条数），**不**与其它角色/权限再做组合判断。 |
| 自助模式下额外功能 | **个人资料**、**通知中心**均 **开放**（通知内容仍须按权限与数据隔离实现，避免越权）。 |
| 历史 `VIRTUAL_GROUP` 单 | 门户 **完全隐藏**。 |

---

## 3. 退出语义（实现须区分）

| 类型 | 含义 | 审批通过后系统行为（原则） |
|------|------|---------------------------|
| **退出 BU（成员）** | 结束用户与该 BU 的**成员关系** | 删除成员关系，并**清除**该用户在该 BU 下**全部 UBR**（与数据模型一致，避免孤儿 UBR）。 |
| **仅移除 UBR** | 保留 **BU 成员**，只移除一条 **(businessUnitId, roleId)** | 仅删除对应 UBR 行；成员记录保留。 |

UI 与 API 须用**不同申请类型或明确子类型**表达，避免审批与执行歧义。

---

## 4. 代办（任何人帮任何人）

- **提交人**：当前登录用户（记录在案，用于审计）。
- **受益人**：申请所服务的用户（可与提交人不同）。
- **无**额外权限门槛：不要求「同组织」「同 BU」等（与常见企业风控不同，已按产品明确采纳）。
- 列表与详情须展示 **受益人** 与 **提交人**（若不同则显著标注「代办」）。

---

## 5. 「仅权限自助」模式（`|C|=0`）

- **门户准入（先于自助/FULL）**：用户必须属于至少一个 **ACTIVE** 虚拟组，类型为 `SYSTEM` / `CUSTOM` / `DEVELOPER` 任一（含 Hermes Default Users 等 SYSTEM 组，以及 AD 同步的 CUSTOM 业务组）。无合格成员身份时 **拒绝发放 Portal JWT**（`loginErrorCode=PORTAL_ENTITLEMENT_DENIED`），并返回可读说明（申请 AD 组并等待同步 / 联系管理员）。与「仅 CUSTOM」无关——SYSTEM 与 DEVELOPER 同样准入，以便兼容现网并支持按小组拆分 AD 审批。
- **吊销时效（已接受）**：准入在发 JWT、`/auth/refresh`、切换工作台时复核；**不**在每个 `/me`/业务请求上复核。成员被移出全部虚拟组后，在 access token 过期前仍可能短暂可用（JWT 常规模型）；refresh 失败后会话结束。产品接受该延迟。
- **触发条件（自助模式）**：已通过门户准入，且用户有效 UBR 集合 **`|C|=0`**（定义同 [portal-bu-rbac.md](./portal-bu-rbac.md) §2、§4.1）。
- **行为**：
  - 允许登录与刷新 token（无工作台上下文时 JWT 可不携带 `activeBusinessUnitId` / `activeRoleId`，与现有登录逻辑对齐方向）。
  - **隐藏**全功能菜单入口：待办、流程发起、BI、Relation Tables 等依赖完整工作台的能力（**前后端**均需约束：前端裁剪 + 后端接口拒绝）。
  - **保留**：BU/角色相关申请与退出、我的申请、登出、改密、语言、**个人资料**、**通知中心**。
- **体验**：进入后应有简短说明（Banner/文案）：当前无 BU 工作台权限，可使用权限自助功能。

---

## 6. 历史数据与虚拟组类型

- 门户 **禁止新建** `VIRTUAL_GROUP` 类申请。
- 对已有 `VIRTUAL_GROUP` 记录：门户 **不展示**（避免终端用户看到虚拟组概念）；治理与审计可在 **Admin** 或离线报表处理。

---

## 7. 审批通过后的自动执行

- 审批通过后由**系统**执行对应数据变更（成员表、UBR 表等），须：
  - **幂等**（重复回调/重复批准不造成脏数据）；
  - **审计**（谁批、何时、关联申请单 ID、受益人、提交人）；
  - 与 Admin 直接维护路径使用**相同业务校验**（准入、成员前置条件等），避免双轨不一致。

---

## 8. 实现清单（落地时自检）

- [x] 门户准入：`PortalEntitlementService` + `issuePortalSession` / refresh / switch-workspace；无合格 VG → `PORTAL_ENTITLEMENT_DENIED`。
- [x] `LoginResponse` / `/auth/me` 与 JWT claim **`portalAccessMode`**：`FULL` | `PERMISSION_SELF_SERVICE_ONLY`（**`|C|=0` → 后者**）。
- [x] 前端路由与 `PortalLayout` 按 `portalAccessMode` 裁剪菜单；深链重定向至 `/permissions`；顶栏 **`SelfServiceBanner`**。
- [x] 后端 **`PortalSelfServiceAccessFilter`**：自助模式仅白名单 `auth`、`permissions`、`permission-requests`（只读/兼容 GET）、`notifications`、`preferences`、`my-permissions`、`exit`（成员列表等）。
- [x] 权限申请：`submitted_by_user_id` 列；**`applicantId`=受益人**；`POST` 体可选 **`beneficiaryUserId`**；**`GET /permissions/users/search`** 搜 ACTIVE 用户。
- [x] **`BUSINESS_UNIT_EXIT`** 类型 + **`POST /permissions/request-business-unit-exit`**；审批通过调用 admin **exit BU**；**直连 `POST /exit/business-unit` 返回 403**（须走申请）。
- [x] 虚拟组：`/permissions/*virtual*` 与旧 **`/permission-requests/virtual-*`** 等返回 403；列表与「我的申请」查询 **排除 `VIRTUAL_GROUP_JOIN`**。
- [x] 取消申请：仅 **受益人**（`applicantId`）可撤待审批单（前后端一致）。
- [ ] 可选：`/auth/refresh` 响应外再刷新 **`localStorage` 中 `user.portalAccessMode`**（当前以登录/切换工作台时写入为准）。

与 [portal-bu-rbac.md](./portal-bu-rbac.md) §4、§6：无 UBR 时不写 active BU/Role 至 JWT，与现有登录逻辑一致。

---

## 9. 相关文档

- [portal-bu-rbac.md](./portal-bu-rbac.md) — UBR、JWT 工作台上下文、`BU_UNBOUNDED` 与虚拟组（管理侧）约束。
- [developer-workstation-workspace-rbac.md](./developer-workstation-workspace-rbac.md) — 设计站工作区（与本文门户自助正交）。

---

*随 API、DDL 或审批流实现变更更新本文。*
