# 平台访问模型：团队 Workspace、Virtual Group 与 Portal 准入 — 需求规格（PRD）

| 属性 | 值 |
|---|---|
| 状态 | 已定稿，开发中（43 项决策全部确认，见 §11；进度见 [实施进度](./platform-access-model-implementation-plan.md)） |
| 版本 | 1.6 |
| 日期 | 2026-10-29（0.1：2026-10-28） |
| 适用范围 | Admin Center、Developer Workstation（DW）、User Portal、AD 同步、platform-security 角色解析 |
| 取代的目标规则 | [developer-workstation-workspace-rbac.md](./developer-workstation-workspace-rbac.md) 全文；[portal-permission-self-service.md](./portal-permission-self-service.md) §2「门户准入」与 §5 准入段 |

> 本文写的是**目标行为**。当前部署版本的实际行为见附录 A；两者冲突时，开发以本文为准，排障以部署版本为准。
> 与 [portal-bu-rbac.md](./portal-bu-rbac.md)（UBR 与工作台上下文）不冲突的部分继续有效。

---

## 1. 背景与目标

### 1.1 现状问题

1. **开发角色是全局的。** 用户只要在任何来源拿到 TECH_LEAD，在他所属的每个团队里都按 TECH_LEAD 处理；团队只决定“是不是成员”。
2. **AD 组一对多灌角色。** 一个 AD「Developer」组同时灌进 Technical Leads、Team Leads、Developers 三个 SYSTEM 组，每个 AD 开发人员同时拿到三个角色；一个 AD「Admin」组同时给 SYS_ADMIN 和 AUDITOR。
3. **TEAM_LEAD 与 TECH_LEAD 后端权限相同**（23 项全有），只在新建 FU 时选团队的方式不同。
4. **FU 可挂多个团队，也可一个不挂。** 一个不挂的 FU 连 SYS_ADMIN 都改不了（issue 1713）。
5. **任何 ACTIVE 虚拟组成员都能进 Portal**（SYSTEM / CUSTOM / DEVELOPER 都算）。
6. **Admin Center 只放行 SYS_ADMIN、AUDITOR 登录**，Technical Lead 没有 Admin 能力；同时 `/roles`、`/users`、`/relation-tables`、`/environment-variables` 等写接口后端没有角色校验，只靠前端路由挡。
7. **LDAP 同步按硬编码清单**每 2 小时重建 SYSTEM 组和角色绑定，AD 组名有硬编码回退。

### 1.2 目标

1. 平台角色精简为 4 个：System Administrator、Auditor、Technical Lead、Developer。删除 Team Lead；Department Manager 保留为业务角色，移到 Role 的 Custom 列表，Virtual Group 里不再有它的组。
2. SYSTEM 虚拟组只保留 System Administrators、Auditors 两个。System Administrator 拥有 Admin Center 全部权限，以及 DW 所有 Workspace 的全部权限。
3. CUSTOM 虚拟组是 Portal 的唯一入口：属于任意一个有效 CUSTOM 组即可进入；进入后的业务权限只看 UBR。CUSTOM 组不绑定角色，业务角色由用户在 Portal 自己申请。
4. DEVELOPER 虚拟组 = 团队 = DW Workspace，一一对应。每条团队记录配置两个 AD 组：Developer、Technical Lead。
   - Developer：保留现状能力，只在本团队 Workspace 内生效。
   - Technical Lead：在本团队 Workspace 内拥有 DW 全部操作权限；在 Admin Center 对本 Workspace 的 FU 有配置权限，并且可以维护全部 BU 和业务 Role（不分团队）。
   - 团队可停用，停用后 DW 不再加载该 Workspace。
5. 只有 System Administrator 维护 Virtual Group，可以在任何组（含团队的两份名单）里手工添加成员；手工成员与 AD 成员同权，AD 同步永远不删手工成员。

### 1.3 范围外

- 不写企业 AD；平台不创建 AD 组、不改 AD 成员。
- 不新增角色、虚拟组类型，不做逐人角色配置页，不做人工授权有效期。
- 不改 BU 申请审批、MI 数据语义。任务分派规则只有一处改动：办理人失去准入时放宽认领池的释放、指派（§7.3）。
- 现状 DW 流程设计器已经不提供“按虚拟组分派”“按 BU_UNBOUNDED 角色分派”，引擎也把这两种旧写法解析为空（附录 A-30）。本需求不恢复它们，只清理 Admin、Portal 里的残留代码。

---

## 2. 术语

| 术语 | 定义 |
|---|---|
| 团队 / Workspace | Admin Center 里一条 DEVELOPER 类型虚拟组记录。DW 的 Workspace 就是这条记录，二者一一对应 |
| 角色名单 | 团队上的两份成员名单：Technical Lead 名单、Developer 名单。每份映射一个 AD 组，也可人工加人 |
| Public | DEVELOPER 类型的系统保留团队，没有名单。SYS_ADMIN 可写，其他具备 DW 准入的人只读 |
| 本 Workspace FU | 所属团队是该团队的 FU |
| 有效 | 账号 ACTIVE，且所在组 / 团队 ACTIVE。任一失效，经由它得到的权限全部失效 |
| UBR | 用户 + BU + 业务 Role 的有效组合，是 Portal 业务权限的唯一依据 |
| AD 成员 / 人工成员 | 成员来源。AD = 同步写入；人工 = SYS_ADMIN 在 Admin 成员页添加，不写企业 AD |

---

## 3. 硬约束

1. **运行时不依赖 DW。** 生产不部署 DW（部署的是 Admin、Portal、引擎、Kong、Activepieces、Superset）。团队本身就是 Admin 里的虚拟组记录，Admin 能直接读到；但“哪个 FU 属于哪个团队”现状只存在 DW 的 `dw_function_unit_dev_groups` 里。生产库里有这张表，但没有 DW 往里写，是空的；Admin 运行时也不能读它。所以 FU 的所属团队 code 必须随 DW 部署、导出包一起带到 Admin 的 FU 目录（§8.1）。现状 Admin 的 SLA、BI 已在运行时读其他 `dw_*` 表（附录 A-26），不在本需求范围，但 Technical Lead 的范围判断不能依赖 DW 的团队关系表。
2. **只读显式配置。** 授权依据是组类型、角色名单、FU 所属团队，不按组名、用户名、AD 组名推断。团队跨环境按 code 识别，与角色、BU 的 code 化一致。
3. **后端强制，前端不给入口。** 列表、详情、搜索、导出、直接 API 同一口径，后端逐接口校验。前端按同一口径直接不提供做不了的操作（已确认）：只读的页面不显示新建、编辑、删除、导入、同步等按钮，表单只读展示，不能出现“点了保存才报 403”。后端的 403 只拦直接调接口的情况。
4. **身份口径一致。** 同一用户在 DW、Admin、Portal 解析出的有效角色必须一致。现状三套解析器读不同的表，且都不看组状态（附录 A-6），本需求要求收敛。
5. **Schema 与种子只增不改。** 删除、重建一律新增编号脚本；`deploy/k8s/init-data` 的 all-in-one 种子同步修改。

---

## 4. 角色

### 4.1 保留的 4 个角色

| 角色 | code | 唯一来源 | 作用域 |
|---|---|---|---|
| System Administrator | `SYS_ADMIN` | SYSTEM 组 System Administrators | 全平台 |
| Auditor | `AUDITOR` | SYSTEM 组 Auditors | 全平台只读 |
| Technical Lead | `TECH_LEAD` | 某团队的 Technical Lead 名单 | 仅该团队 |
| Developer | `DEVELOPER` | 某团队的 Developer 名单 | 仅该团队 |

- 这 4 个角色都只能从上表的组或名单获得，不能“直接分配给用户”，也不能经 `sys_role_assignments` 全局获得。切换时清除 `sys_role_assignments` 中这 4 个角色及 TEAM_LEAD 的行：直接分配给用户的全部清除；分配给组的只保留 System Administrators → SYS_ADMIN、Auditors → AUDITOR 两条固定绑定（绑定存在哪张表，以 §3.4 收敛后的统一解析为准）。现状种子里 `admin`、`44027893` 就是直接分配的 SYS_ADMIN（附录 A-33），不清的话他们会保留 SYS_ADMIN。
- Admin → Role 的 System 列表只显示这 4 个（现状 `SYSTEM_ROLE_LIST_CODES` 还含 TEAM_LEAD、MANAGER）。

### 4.2 删除 Team Lead

- 删除角色定义、开发权限映射，以及所有用户、组、分配上的引用；代码中的 TEAM_LEAD 分支一并删除（现状生产代码 13 个文件、58 行；连同测试、种子、文档共约 100 处）。
- **不做自动映射。** 现有 Team Lead 持有人切换后没有开发权限，直到 SYS_ADMIN 把他们放进某个团队的名单。切换前出报表（§10）。
- 历史审计里的 TEAM_LEAD 字样保留，但不产生任何权限。

### 4.3 Department Manager 移到 Custom 列表（已确认）

现状：`MANAGER`（Department Manager）是 `is_system=true` 的 BU_BOUNDED 业务角色，显示在 System 列表。E2E 邮件回复 FU（UAT 专用种子）的 Review / Confirm / Checker 节点按 BU + MANAGER 分派，数字信贷流程与部分测试也在用；另有 CUSTOM 组 Department Managers（`vg-managers`）绑定了它。

- 角色保留，code、id、类型（BU_BOUNDED）不变；改为 `is_system=false`，从 System 列表移除，出现在 Custom 列表（Custom 列表 = 非系统的 BU_BOUNDED 角色）。
- 删除 Virtual Group Department Managers，连同成员与角色绑定。
- 流程分派不受影响：BU + MANAGER 按用户的 UBR 取人，不经过这个组。
- 这个组的成员如果没有别的 CUSTOM 组，切换后会失去 Portal 准入，迁移盘点须列出（§10）。种子里的 E2E 账号（如 `user-e2e-lina`）同样要确认仍在某个 CUSTOM 组。
- 改为非系统角色后，Admin 允许修改、删除它。删除被流程、UBR 引用的角色必须拒绝，并提示引用位置。

### 4.4 多身份组合

- 按 Workspace 逐个判断。用户在 Team-A 是 Technical Lead、在 Team-B 是 Developer，则在 B 只有 Developer 能力，A 的能力不传播到 B。
- 同一团队里两份名单都有此人：按 Technical Lead 处理，成员页不重复计人。
- AUDITOR 不增加写权限，也不收回此人独立取得的团队权限。
- SYS_ADMIN 不需要加入任何团队。

---

## 5. Virtual Group

### 5.1 三种类型

| 类型 | 用途 | 算 Portal 准入 | 角色 | 维护者 |
|---|---|---|---|---|
| SYSTEM | 只有 System Administrators（SYS_ADMIN）、Auditors（AUDITOR）两个 | 否 | 固定绑定，只读显示 | SYS_ADMIN，受 §5.2 保护规则约束 |
| CUSTOM | 只管“谁能进 Portal” | 是 | **不绑定角色**（§5.3） | SYS_ADMIN |
| DEVELOPER | 团队 = Workspace，含两份角色名单 | 否 | 由名单决定，组本身不绑角色 | SYS_ADMIN |

- 所以 Admin 的 Virtual Group 页面不再有可编辑的 Bind Roles：SYSTEM 只读显示固定角色，CUSTOM、DEVELOPER 不显示。
- SYSTEM、CUSTOM 组各映射一个 AD 组；团队映射两个（§5.4）。都可以不映射、只放人工成员。AD 组名由各环境在 Admin 页面配置，代码与配置文件中不再有默认 AD 组名。
- 不设默认团队，不预置任何团队与 AD 组的对应关系。

### 5.2 SYSTEM 组重建

1. 删除现有全部 SYSTEM 组：System Administrators、Auditors、Technical Leads、Team Leads、Developers、Hermes Default Users，连同它们的成员、角色绑定，以及 `sys_role_assignments` 中指向它们的行（该表的 `target_id` 没有外键，删组不会级联）。
2. 新建 System Administrators（绑定 SYS_ADMIN）、Auditors（绑定 AUDITOR），**沿用原 code**（`SYSTEM_ADMINISTRATORS`、`AUDITORS`），id 沿用该环境现有的那一条，清空成员与绑定后重建（已确认，D-16）。跑过种子的环境 id 是 `vg-sys-admins`、`vg-auditors`；由 LDAP 同步自动建组的环境是随机 UUID（附录 A-29）。`44027893` 不迁移；`admin` 只在 k8s 环境作为初始 SysAdmin 放进新组（第 3 步）。加上 §4.1 清除直接分配，其余账号切换后失去 SysAdmin；实现时须确认没有脚本、测试依赖它们。
3. **初始 SysAdmin 账号，各环境与现状一致（已确认，D-22）。**
   - **k8s 环境（UAT、preprod、生产）：** 用现有的 `admin`（k8s 的 all-in-one 种子没有 `developer`，附录 A-33），迁移时作为人工成员放进新 System Administrators。不新建 `developer`。
   - **dev 环境（`deploy/init-scripts`）：** 用 `developer`，规则如下。现状种子账号 `developer`（`user-dev`，密码 `password`）在旧 System Administrators、Technical Leads 两个组里，并在「共享财务中心」持有 Department Manager UBR；它只在 `05` 种子里。重建后：
     - 账号不存在则创建，已存在则保留。密码仍为公开的 `password`（已确认）。
     - 作为人工成员加入新 System Administrators。旧 Technical Leads 组删除后不再需要它：SYS_ADMIN 已覆盖 DW 全部 Workspace。
     - 加入 CUSTOM 组「E2E 流程仿真用户组」（`vg-e2e-workflow`），否则按 §7.1 它进不了 Portal。
     - 财务中心 MANAGER 的 UBR 保留。
   - 两种环境的初始账号都沿用现状密码；要进 Portal 的，须在切换配置脚本里放进某个 CUSTOM 组。
4. **不能出现“无人是 SYS_ADMIN”的窗口。** 迁移脚本在新 System Administrators 组至少有 1 名有效成员时才提交，否则整体回滚。dev 环境是 `developer`，k8s 环境是 `admin`。旧组的其他成员不迁移，由 SYS_ADMIN 配好 AD 映射后经同步回填。
5. 启动、定时同步、初始化脚本都不得再创建 SYSTEM 组或开发角色绑定。现状 `LdapSyncService` 的硬编码清单 `HERMES_BINDINGS` 每 2 小时会把旧组重建回来，必须删除。
6. 保护规则：
   - 不可删除，不可改类型、名称，不可停用（现状后端的启停接口没有 SYSTEM 校验）。
   - 角色绑定不可改。
   - AD 映射与成员由 SYS_ADMIN 维护；对 System Administrators 增减成员需二次确认。
   - 拒绝移除 System Administrators 的最后一名有效成员。现状这条校验比较的是 `SYS_ADMINS`，实际 code 是 `SYSTEM_ADMINISTRATORS`，所以从不触发，需修复。

### 5.3 CUSTOM：Portal 准入

- 准入规则见 §7.1。
- **CUSTOM 组只管成员能不能进 Portal，不绑定角色（已确认）。** 用户在哪个 BU 担任什么 Role，由用户在 Portal 的权限自助（setup）里自己申请，不在 Virtual Group 配置。切换时删除所有 CUSTOM 组的角色绑定。
- 绑定角色的只剩 SYSTEM（固定）和 DEVELOPER（由两份名单决定）。
- BU + Role 的申请不受影响：某个 BU 下能申请哪些角色，看的是该 BU 自己绑定的角色，不看虚拟组。
- **不带 BU 的角色只有系统自带的 4 个（已确认，D-14）。** 即 SYS_ADMIN、AUDITOR、TECH_LEAD、DEVELOPER，经 SYSTEM / DEVELOPER 组获得（§4.1）。业务角色一律是 BU + Role，从 Portal 申请。所以：
  - 不再有自建的 BU_UNBOUNDED 业务角色。Admin 新建角色时不提供这个类型。`BU_UNBOUNDED_ROLE` 分派现状 DW 已不提供、引擎已不解析（附录 A-30），只需清理 Admin（`TaskAssignmentQueryService`）、Portal（`BpmnParserService`、`ProcessStartAssigneeResolver`）、DW（`useUserTaskAssignee.ts` 的旧值兼容）里的残留分支。
  - 现状 Admin 建角色接口已要求必填类型，但建表默认值和实体默认值仍是 `BU_UNBOUNDED`（`01-platform-security-schema.sql:69`、附录 A-34），绕过接口直接写库时会落成它。去掉这两处默认值。
  - 存量自建的 BU_UNBOUNDED 角色在切换前盘点（§10），切换时一律删除，不转为 BU_BOUNDED（D-64）；还需要的业务权限由 SYS_ADMIN 另建 BU_BOUNDED 角色、持有人从 Portal 重新申请。仓库种子里没有这类角色，也没有流程用 `BU_UNBOUNDED_ROLE` 分派。
  - 代码里不再保留 `BU_UNBOUNDED` 这个角色类型（D-64）：存量角色由切换迁移删除，任何环境都不会留下。
  - 系统自带的 4 个角色在库里的类型是 ADMIN / AUDITOR / DEVELOPER，本来就不是 BU_UNBOUNDED，不受影响。

### 5.4 DEVELOPER：团队

- Admin → Virtual Group → Developer 列表里的一条记录就是一个团队，也就是 DW 的一个 Workspace。
- 每条团队记录配置两个 AD 组：Developer AD 组、Technical Lead AD 组，分别同步成两份名单。SYS_ADMIN 也可以在每份名单里人工加人。现状虚拟组只有一个 `ad_group` 字段，需要扩成两个。
- 不再需要第三份“团队成员”名单；不在任何名单里的人不是团队成员，也没有只读基线。
- 两份名单可以逐步配置，空名单不授予该角色。没有 Technical Lead 的团队由 SYS_ADMIN 新建 FU，系统不会把 Developer 自动升为 Technical Lead。
- 同一个 AD 组可以被多个团队的名单映射。人工成员只属于添加它的那份名单，不跨名单、不跨团队复制。
- **停用团队：**
  - DW 不再加载该 Workspace：成员看不到它及其 FU，团队切换器不列出。
  - 经该团队得到的 Technical Lead / Developer 权限（含 Admin 权限）全部失效。
  - 已发布到 Portal 的 FU 照常运行。
  - SYS_ADMIN 仍能在 DW 看到这些 FU，标注“已停用”，并能把它们移到其他团队（已确认，D-09）。
  - 该团队的 Automation workspace 同样不可进入（现状已是如此）。
- **删除团队：** Admin FU 目录里还有所属团队是它的 FU 时拒绝，须先迁移。Admin 不读 DW 的表、不调 DW（D-33）；DW 里仍挂在已删除团队上的 FU 与停用团队同样处理：SYS_ADMIN 在 DW 看得到、标注出来并能迁走。
- **团队 code 由团队名称生成（已确认）**，保证同一个 FU 的包能部署到其它环境：每个环境用同一个名称建团队，code 就一样，部署、导入时按 code 自动对上。
  - 规则沿用现有代码（`VirtualGroupManagerComponent.generateCode`）：转大写，非字母数字换成 `_`，合并连续的 `_`，去掉首尾 `_`。例如 “Payment Ops” → `PAYMENT_OPS`。建团队时不再允许手填 code。
  - 名称必须含英文字母或数字。纯中文名称生成的 code 为空，拒绝创建，提示改用英文名称。
  - 两个名称生成同一个 code（如 “Pay-Ops” 和 “Pay Ops”）时，后建的拒绝。
  - 团队建好后名称和 code 都不能改；否则新环境按新名称建出的 code 对不上。需要说明的内容写在描述里。
  - 存量团队在迁移时按名称重算 code（Public 保留 `DEV_TEAM_PUBLIC`）。重算后冲突或为空的，列入盘点，由 SYS_ADMIN 在切换前改名。团队在 DW、Automation 里都按 id 关联，重算 code 不影响它们。
- 团队 code 全局唯一；跨环境以 code 识别同一团队。

### 5.5 成员维护

**谁能改：** 只有 SYS_ADMIN 能创建、修改、删除组，配置 AD 映射，启停，维护成员。Technical Lead、Developer、Auditor 都没有写权限。

**同组同权：** 人工成员与 AD 成员获得完全相同的权限。

**来源规则：**
- 成员列表显示来源：AD、人工，或两者都有。
- AD 同步只增删 AD 来源，**从不删除人工来源**（已确认；适用于所有组和团队的两份名单）。
- 撤销人工来源不影响 AD 来源，反之亦然；只要还剩一个来源，用户就仍是成员。按钮文案写“撤销人工成员”而不是“移除成员”，撤销后显示剩余来源。
- 现状每组每人只有一行，已是 AD 成员的人无法再加为人工成员（`MEMBER_EXISTS`），需要允许两种来源并存。

**审计：** 不加“原因”“到期时间”字段。系统审计记录操作人、时间、目标用户、组或名单、动作；AD 同步的增减同样逐人记录，操作人记为 AD 同步（D-32）。

**失效优先：** 用户停用、组停用、团队停用优先于任何来源。

**谁能看成员：**

| | SYS_ADMIN | Technical Lead | Developer | Auditor |
|---|---|---|---|---|
| 所有组的成员 | ✓ | ✓ 只读（入口在 Admin，D-21） | ✗ | ✓ 只读 |
| 本团队两份名单 | ✓ | ✓ 只读（入口在 Admin） | ✗，只能看自己的角色 | ✓ 只读 |

这些限制同时作用于列表、详情、搜索、导出接口；不影响业务表单里已有的选人组件。

### 5.6 AD 同步

| 现状 | 要求 |
|---|---|
| AD 组找不到（CN 不存在或拼错）时返回空列表，把该组 **AD 成员**全部删掉 | **接受，不修**（已确认）。只删 AD 来源，人工成员保留 |
| 全量同步先删 AD 成员再拉取，拉取失败则 AD 成员丢到下次同步 | **接受，不修**（已确认）。同上，只影响 AD 来源 |
| 所有同步路径删除前都过滤 `added_by = HERMES_AD_SYNC`，人工成员不会被删 | 保持；新增的团队两份名单走同一规则；补自动化测试守住 |
| 已是 AD 成员的人不能再加为人工成员（`MEMBER_EXISTS`）。之后 AD 把他移除，SYS_ADMIN 想保留的人也跟着没了 | 允许两种来源并存（§5.5），AD 移除时人工来源保留 |
| 停用的组仍让成员拿到角色 | 停用组的成员不得经由该组得到任何权限 |
| 同步可以删光 System Administrators 的成员 | 不能让它失去最后一名有效成员（D-31） |
| 名单（或 SYSTEM / CUSTOM 组）没有 AD 映射时，同步跳过它，之前同步进来的 AD 成员留着 | 同步删除这份名单的 AD 来源行，人工来源不动（D-29）。切换后团队原映射被清空，迁入名单的 AD 来源行因此在第一次同步时被清掉，由新配置的 AD 组回填 |

---

## 6. Developer Workstation

### 6.1 准入与可见范围

- 能进入 DW：SYS_ADMIN；AUDITOR（所有 Workspace 只读，已确认 D-08）；至少在一个有效团队名单里的用户。只有 CUSTOM 成员资格的用户不能进入。
- 能看到的 Workspace：SYS_ADMIN、AUDITOR 看全部；其他人看自己所在名单的有效团队，再加 Public。
- DW 内的 Automation 页面同一口径（已确认，D-20）：SYS_ADMIN 进所有团队的 workspace；Technical Lead、Developer 进本团队；Auditor 只读。

### 6.2 操作权限

Technical Lead 在本 Workspace 内拥有**全部操作权限**；Developer 保留**现状能力**。两者都只在本 Workspace 内生效。

| 操作 | SYS_ADMIN | Technical Lead（本 WS） | Developer（本 WS） | Auditor |
|---|---|---|---|---|
| 查看、导出 | ✓ | ✓ | ✓ | ✓ |
| 编辑设计（表单、流程、表、动作、决策、AI 生成） | ✓ | ✓ | ✓ | ✗ |
| 发布、部署到 Admin | ✓ | ✓ | ✓ | ✗ |
| 版本回滚 | ✓ | ✓ | ✓ | ✗ |
| 新建 FU、导入为新 FU、导入为已有 FU 的新版本 | ✓ | ✓ | ✗ | ✗ |
| 克隆（目标为本 WS） | ✓ | ✓ | ✗ | ✗ |
| 删除（归档）、彻底删除、恢复归档 | ✓ | ✓ | ✗ | ✗ |
| 移动到其他 Workspace | ✓ | 源、目标都是 Technical Lead 时 ✓ | ✗ | ✗ |
| 写 Public 内的 FU | ✓ | ✗ | ✗ | ✗ |

- SYS_ADMIN 对所有 Workspace（含 Public）都有全部权限，不需要是成员。现状 SYS_ADMIN 只能**查看**非本人团队的 FU，不能修改。
- Public：所有具备 DW 准入的人可查看、导出；非 SYS_ADMIN 不能克隆、移入、移出、修改。
- **现状有漏洞须修：** 维护 FU 团队的接口（`PUT /function-units/{id}/dev-groups`）只校验对源 FU 有权限，写入的组 id 不做任何限制（附录 A-31）。所以 Technical Lead 现在能把 FU 挂进 Public，或挂到自己不在的团队、甚至非团队组。按 §6.3 改为单选，并校验调用者在目标团队有新建权限。

### 6.3 FU 唯一归属

- 每个 FU 必须且只能属于一个 Workspace（Public 也是一个 Workspace）。前端单选，后端校验，数据库对 `function_unit_id` 加唯一约束。这条规则覆盖新建、导入、克隆、移动、恢复归档的每条路径。
- 缺归属不默认成 Public，而是报错。
- **新建：** Technical Lead 在自己是 Technical Lead 的有效团队里选；SYS_ADMIN 可选任意团队或 Public。
- **导入：** 导出包带所属团队 code 和名称（§8.1）。新 FU 归入本环境同 code 的团队，调用者须在该团队有新建权限；本环境没有这个团队或已停用时，与 Admin 相同，提示“请先建团队 X”（D-23）。旧包不带团队时必须显式选择目标 Workspace。已有 FU 的新版本沿用原归属。
- **克隆：** 生成新 FU，目标 Workspace 单选，且调用者须在目标有新建权限。不再复制源 FU 的团队；现状会把源的全部团队（含 Public）一起复制。
- **版本回滚、恢复归档：** 不改归属，也不恢复已撤销的授权。

### 6.4 存量数据整理

- 出清单：没有团队的 FU、挂了多个团队的 FU、挂在非团队组上的 FU（例如 Platform Showcase 挂在 SYSTEM 组 `vg-tech-leads` 上）。SYS_ADMIN 逐个指定唯一归属，全部整理完再加唯一约束。
- **Public 改为 DEVELOPER 类型（已确认）。** 现状 Public（`vg-dev-public`）是 CUSTOM 类型，不改的话它的成员会被当成 Portal 准入。改为 DEVELOPER 类型的保留团队：不可停用、不可删除、没有名单。id 不变，所以 Automation 的 Public workspace（按同一 id 关联）不受影响。

---

## 7. User Portal

### 7.1 准入

```text
Portal 准入 = 账号有效 AND 至少属于一个有效 CUSTOM 组（AD 或人工来源都算）
```

- SYSTEM、DEVELOPER 组的成员资格不再算准入。**SYS_ADMIN 也不能直接进 Portal**，必须是某个 CUSTOM 组（一个 AD 组或人工成员）的成员（已确认，D-04）。AUDITOR、Technical Lead、Developer 身份和历史 UBR 同样不能替代 CUSTOM 成员资格。
- 切换前须把需要用 Portal 的管理员放进某个 CUSTOM 组，否则他们切换后进不了 Portal。
- 失去一个 CUSTOM 但还有其他 CUSTOM：保留准入。最后一个失效：撤销准入。
- **撤权时效（D-06）：** 适用于 Portal 准入、团队名单、System Administrators、Auditors 的移除，以及账号、组、团队停用。
  - **移除后立即生效：之后的登录被拒绝（已确认）。**
  - **正在使用的会话最多保留旧权限 15 分钟（已确认）。** 做法：
    - 三端 access token 有效期改为 15 分钟（共用配置 `JWT_EXPIRATION`）。
    - **代码里不设兜底值（已确认）。** 有效期只从配置读取，缺配置时服务启动失败并报出缺哪个配置。现状兜底散在多处，全部删除（清单见附录 A-20）：各服务 yml 里 `JWT_EXPIRATION`、`JWT_REFRESH_EXPIRATION` 的默认值（DW 的 access 为 30 天）；Java `@Value` 默认值（Portal 2 处 24 小时、DW 3 处 30 天、引擎 1 处）；DW 构造器里写死的 30 天；`JwtProperties` 字段默认值（access 1 小时、续期 7 天）；Portal、DW 生成续期凭证时写死的 7 天（各 2 处）。续期 cookie 的有效期也改为跟随续期凭证配置，不再写死 7 天。本地开发与测试同样须显式配置（`.env`、`application-test.yml`）。
    - **必填配置一律真正必填（已确认，2026-10-30）。** 仓库原写法 `${JWT_SECRET:?JWT_SECRET is required}` 在 Spring 里不是必填：`:` 之后整段是默认值，缺配置时服务用字面量 `?JWT_SECRET is required` 当密钥照常启动。`JWT_SECRET`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_REDIS_PASSWORD`、`ENCRYPTION_SECRET_KEY` 改为 `${X}`，缺了即启动失败；DW、引擎代码与 dev 配置里写死的开发密钥默认值一并删除。
    - 三端续期（refresh）都重新校验：账号 ACTIVE，且仍有该端准入。Portal 看有效 CUSTOM；Admin 看 SYS_ADMIN、AUDITOR 或有效团队的 Technical Lead；DW 看 SYS_ADMIN、AUDITOR 或有效团队名单。不满足返回 401，前端回到登录页。
    - **前端所有请求遇到 401 先续期再重试，正常使用无感。** 现状（2026-10-30 实测）三端 17 个 axios 实例（15 个文件）只有 3 个主实例会续期，Admin、Portal 页面加载时的 `/me` 检查也不续期：凭证一过期，刷新页面或新开标签页就回到登录页。改为三端共用一次续期（`frontend/shared/src/auth/sessionRefresh.ts`，续期凭证用一次即作废，各实例各自续期会互相顶掉），平台会话的请求全部接入；Activepieces 自己的令牌不在此列。上传队列同样走这一次续期，续期成功后重试本次上传。
    - **后台任务不得依赖用户 access token 的剩余有效期。** DW 部署是后台异步任务：请求开始时取下用户凭证，之后上传、校验、部署几步才用它调 Admin（附录 A-25）。它同时带了内部服务令牌和 `X-User-Id`。WP1 起各服务遇到无效或过期凭证一律回 401（前端据此续期），但带有效服务令牌的请求例外，改由服务令牌认出操作人，所以凭证改为 15 分钟后部署不会中途失败（2026-10-30 实现 WP1 时核实并有单测）。由此带来的要求：Admin 的后端鉴权（§8.1）对服务调用必须按 `X-User-Id` 从库里算角色，不能用凭证里的角色——服务令牌认出来的身份角色为空，凭证里的角色也可能已过时。其他把用户凭证留给后台使用的地方已排查（WP1 review）：只有上面的 DW 部署，以及 Portal 在请求线程里把凭证转给引擎；后者在一次很长的同步批量操作中途遇到凭证过期时会部分失败，记入实施进度的残余风险。
    - **会话多久过期（已确认，D-06c）：** 会话 = 续期凭证（refresh token）。每次续期都会换发一张新的续期凭证，所以它实际是**空闲超时**：只要持续在用就不会过期，连续这么久不用才要重新登录。
      - 现状：只有 Admin 读配置（UAT 3 天、preprod 7 天、`deploy/environments/prod/.env` 1 天）；Portal、DW 写死 7 天、不读配置；cookie 三端写死 7 天。
      - 三端统一 8 小时空闲超时（`JWT_REFRESH_EXPIRATION`），不另设绝对上限。理由：每 15 分钟续期一次时已重新校验账号和权限，绝对上限对权限安全没有额外帮助；过期后跳统一登录页，统一登录页会自动尝试免密登录（DSP），能免密的用户只看到一次跳转。
  - 不选“每个请求都查库”：三端过滤器都要改，每个请求多一次查询；15 分钟对平台已足够。DW 的团队范围判断本来就按请求读库，移出团队名单在 DW 下一次请求即生效。
  - 现状（三端都做不到“最多 15 分钟”）：

| 端 | 登录时 | 续期时 | access token 有效期 |
|---|---|---|---|
| Portal | 校验准入（任一 ACTIVE 虚拟组） | 重新校验准入；切换工作台也校验 | 代码默认 24 小时；UAT 12 小时；preprod 24 小时；`deploy/environments/prod/.env` 8 小时 |
| Admin | 必须有 SYS_ADMIN 或 AUDITOR | 重新计算角色写进新凭证，但**不再检查**是否仍有 SYS_ADMIN、AUDITOR，也未见账号状态检查；被移出的人能一直续期，而多数后端接口不校验角色（§8.1） | 同上 |
| DW | 后端不拦，进入限制只在前端 | 重新计算角色；TECH_LEAD 等全局角色在旧凭证里一直有效到过期 | 代码默认 **30 天**；k8s 环境被共用配置覆盖为 12 / 24 小时 |

  - 仓库里的 `deploy/environments/prod/.env` 写的是 access 8 小时、续期 1 天；它是否就是生产实际值，以生产 ConfigMap 为准。

| 状态 | 行为 |
|---|---|
| 没有有效 CUSTOM | 拒绝登录（`PORTAL_ENTITLEMENT_DENIED`），不降级成权限自助 |
| 有 CUSTOM、没有有效 UBR | 仅权限自助，以及个人资料、通知中心等现有白名单；内容按用户隔离 |
| 有 CUSTOM、有有效 UBR | 按当前 BU + Role 工作，继续校验 FU、View、行数据、任务身份 |

### 7.2 进入后的业务权限

- 只按当前 UBR（BU + Role）以及 FU、View、行数据、任务身份校验。不把不同 UBR 的 BU 和 Role 拼成一个不存在的组合。
- To Do 里的认领池任务（含已被认领的）只在对应 BU + Role 的工作区显示（D-36）。
- 附件、备注、导出、直接 API 同一口径。
- **取消 SYS_ADMIN 在 Portal 里的全部业务旁路（已确认，D-05）。** SYS_ADMIN 经 CUSTOM 进入后和普通用户一样，只按当前 BU + Role 判断。现状有旁路、需要逐处删除的地方：
  - FU 访问（发起、查看）。
  - View 可见性：现状访问配置为空的 View 只有管理员可见，取消后对所有人不可见。DW 保存这样的 View 时须提示“发布后没有人能看到”。
  - “仅参与人”View：现状 SYS_ADMIN 能看全部行，取消后只看自己参与的。
  - 流程详情与历史。
  - 记录备注的读、写、归档：去掉 SYS_ADMIN 放行，其余门禁不变。
  - 备注附件上传来源校验。
  - 催办（现状按 `isSuperAdmin` 放行）。
  - 认领池强制释放、重新指派。
  - 位置见附录 A-32；实现时须全仓搜索 `isSystemAdministrator`、`isSuperAdmin`、`SYS_ADMIN`，确认 Portal 里没有遗漏。
- 规则 `view-access-control`（`.cursor/skills/`）已于 2026-10-29 改为本条：空配置无人可见，SYS_ADMIN 无旁路。代码仍是旧行为。该 skill 末尾的「与现有实现的差距」只列了 View、FU 门禁相关的旁路，其余以本节为准。

### 7.3 失去准入后的待办

- 不删除业务数据，不自动清理 UBR，不自动完成或丢弃任务。
- **失去准入的办理人**：账号停用，或已没有有效 CUSTOM 组。为避免与现有的“孤儿任务修复”（`TaskOrphanRepairService`，处理没有候选人的任务）混淆，界面称“办理人已失去准入的待办”。
- **离职的办理人（D-42）**：账号 INACTIVE 或已删除。LOCKED 不算离职。离职者必然也失去了准入；账号仍 ACTIVE、只是没有 CUSTOM 组的，不算离职。
- 处理方式（D-07、D-17、D-39、D-42）：

| 任务来源 | 办理人失去准入、账号仍 ACTIVE | 办理人离职 |
|---|---|---|
| BU + Role 认领池（含已被认领） | 同 BU + Role 有准入的任何成员在 Portal To Do 看到“办理人已失去准入”，可释放回池、自己接手、指派给同池另一名有准入的成员 | 同左；已认领、池里又没有其他有准入的成员时，自动指派给离职者的经理 |
| 按候选人列表分派；以及旧流程遗留的按虚拟组分派任务（含已被认领；D-58 起不含旧虚拟组） | 同上（D-17） | 同上。未认领的候选人列表任务：候选人全部离职才自动指派给经理（D-39）；未认领的 BU + Role 池、旧虚拟组任务不处理 |
| 直接指派给某个人（发起人、表单字段指定、BPMN 写死的人等） | 不转。任务留在本人名下，由本人重新申请进入 Portal | 自动指派给其经理 |

- **自动指派给经理（D-42）：** 先 Entity Manager；没有或不可用，则 Function Manager。“可用” = 账号 ACTIVE 且有 Portal 准入。候选人列表任务按候选人顺序，取第一个有可用经理的候选人。任务直接出现在经理的 To Do，经理接手后就是办理人，自己办理或转办，用自己的身份提交。
- **两位经理都不可用：** 任务留在原处，引擎记 ERROR 日志，不另找兜底人。
- **何时触发：** Admin 把用户改为 INACTIVE 或删除，事务提交后通知引擎；引擎另每 10 分钟扫一遍所有未完成任务，兜住漏掉的通知、暂停后恢复的任务，以及池里最后一个有准入的同事也失去准入的情形。
- **不设 Admin 兜底（已确认，D-07），也没有经理专用页面**（D-42 取消了「下属失去准入的待办」页签）。前提是每个用户都有 Entity Manager 和 Function Manager。
- 认领池任务没人认领、池里暂时也没有有准入的成员：不会卡死。之后任何人获得该 BU + Role 并有准入，就能在池里看到并认领。
- 缺经理视为数据问题，不做兜底。预检报表（§10）列出两类用户：没有 Entity / Function Manager 的；两位经理都没有 Portal 准入的。后者离职时，直派待办会留在原处没人接，须在切换前补齐。

- 现状对比：认领池的强制释放、重新指派只允许 SYS_ADMIN、该 BU 的 Approver 和 Leader（`ForceUnclaimAuthorizationComponent`）。办理人仍有准入时保持这个规则（D-05 后去掉 SYS_ADMIN）；办理人已失去准入时，放宽到同 BU + Role 的普通成员。
- **经理须有 Portal 准入。** Entity / Function Manager 取自用户档案的 `entity_manager_id`、`function_manager_id`（AD 同步写入）。AD 同步会把组外的经理补导入为用户，但不放进任何虚拟组，所以他们不一定能进 Portal，须由 SYS_ADMIN 放进某个 CUSTOM 组；没有准入的经理视为不可用。
- MI 子任务逐条处理，规则相同。
- **站立委托（已确认，D-18）：** 委托人或被委托人任一方失去准入，该委托规则即停止生效，任务按本节处理。恢复准入后规则重新生效（规则本身不删除）。
- 接手、转派后新办理人用自己的身份办理，不能代原办理人提交。
- 每次释放、认领、转派都写任务变更历史（现有 `up_change_history`），记录操作人、时间、原办理人、新办理人。系统自动做的也写：池成员资格失效的释放（D-40）记 `FORCE_UNCLAIM`，离职转给经理记 `MANAGER_REASSIGN`，操作人都是 system，并记原因（界面显示“办理人已不再持有该 BU + Role”“办理人离职”）。

---

## 8. Technical Lead 的 Admin Center 权限

### 8.1 前置条件（上线阻断项）

1. **登录放行。** 在有效团队 Technical Lead 名单里的用户可以登录 Admin，只看到 §8.2 所列菜单。现状只放行 SYS_ADMIN、AUDITOR。
2. **后端逐接口授权（已确认要加）。** 所有 Admin 接口（读和写）在后端校验角色；对 Technical Lead 开放的接口再校验范围。现状 `/roles`、`/users`、`/relation-tables`、`/environment-variables` 等没有后端校验，必须先补齐再放开登录，否则 Technical Lead 直接调 API 就能拿到这些写权限。
   - **不能挡住 DW 部署。** DW 部署时带的是操作人的身份，而 DW 里 Developer 也能部署（§6.2）。所以 Admin 导入、部署接口放行的是“在该 FU 所属团队有部署权限的人”：SYS_ADMIN、该团队的 Technical Lead 和 Developer。Developer 不能登录 Admin，只能经 DW 部署走到这两个接口。
   - **不能挡住服务间调用。** DW、Portal、引擎都会调 Admin 接口，调用点很多：
     - DW：开发权限、用户、环境变量（含解析 Vault 密码）、系统 SMTP/IMAP、Automation flows、SSO 兑换。
     - Portal：`AdminCenterClient` 有 21 个接口（权限申请与审批、成员、退出、认领池强制释放判定等），另有十余个类直接拼 Admin 地址调用（FU 访问、虚拟组、流程、任务等）。
     - 引擎：用户信息、任务分派数据。
     - 带内部服务令牌的调用按“哪个服务、调哪些接口”的清单放行，不按用户角色判断；清单在实现时全仓搜索列全。解析 Vault 密码这类接口只对服务调用开放，任何用户角色都不能直接调。
   - 现状有一个本地开关 `admin.auth.developer-bypass-role-check` 可跳过 Admin 登录的角色检查（默认关），生产配置须确认是关的。
3. **FU 的所属团队带到 Admin。** 团队就是 Admin 里的 Developer 虚拟组记录（与 DW Workspace 是同一条），按 code 识别。缺的是“FU → 团队”这条关系，它现在只存在 DW 里。
   - **现状两条路径都不带团队。** DW 的“部署”是先导出 ZIP，再调 Admin 的 `/function-units-import/import`（文件上传）；Admin 页面手动导入调的是 `/function-units/import`（JSON + Base64）。两个接口最后进同一个导入组件。导出包里没有团队，两个接口和导入组件也都没有团队字段（附录 A-13、A-19、A-24）。
   - Admin 的 FU 目录（`sys_function_units`）增加“所属团队 code”；DW 导出包带上 FU 当前所属团队的 code 和名称。两条路径因此都能带团队。
   - `sys_function_units` 每个版本一行。所属团队写在本次导入的版本行上，旧版本行不改；判断“这个 FU 属于哪个团队”一律按 FU code 取最新导入的版本行。
   - **DW 部署：** 团队取包里的团队 code，即 DW 中该 FU 当前所在的 Workspace，不用选。FU 在 DW 里换了团队，下次部署时 Admin 跟着更新；这次部署的人须在原团队和新团队都有部署权限（D-34）。DW 部署只能发往本环境配置的 Admin 地址（部署请求会带服务令牌和用户凭证，WP2 起不再接受任意目标地址）。
   - **目标环境没有这个团队时（已确认，D-23）：** 不论是谁、走哪条路径（DW 部署、Admin 手动导入），都拒绝并提示“请先建团队 X（code）”；团队已停用的，提示“团队 X 已停用，请先启用”。不自动建团队，不默认成 Public，也不能改选别的团队绕过。
   - **Admin 手动导入（已确认）：** 导入时确认团队。
     - 包里带团队：预填包里的团队；该团队在本环境不存在或已停用时，按上一条拒绝。
     - 旧包不带团队：预填本环境该 FU 已有的所属团队，没有则必须选。
     - SYS_ADMIN 可改选任意有效团队。
     - Technical Lead 只能选自己担任 Technical Lead 的团队；导入的是别的团队已有 FU 的新版本时拒绝。
     - 选的是“这个 FU 属于哪个团队”，不是“以哪个团队的身份导入”。操作人始终是登录的人，审计记录的也是他。
     - 区分方式：Admin 页面的导入接口（`/function-units/import`）必须带所选团队；DW 部署用的接口（`/function-units-import/import`）不带，按包里的团队。两种都在导入组件里校验操作人在最终团队有部署权限。
   - **生产存量 FU 靠重新导入补齐（已确认）**，不另做“设置所属团队”操作。重新导入之前它们没有所属团队，只有 SYS_ADMIN 能在 Admin 管理；Portal 运行不受影响。
   - 生产要先建好同 code 的团队并映射 Technical Lead AD 组，生产的 Technical Lead 才能在 Admin 管自己的 FU。
4. **刷新令牌时复核角色。** 现状 Admin 刷新令牌时不复核 SYS_ADMIN / AUDITOR。

### 8.2 范围

| Admin 功能 | “属于本 Workspace”的判据 | Technical Lead 权限 |
|---|---|---|
| Dashboard | — | 查看，与 SYS_ADMIN 看到的相同，含「最近活动」（已确认，D-21） |
| Function Unit 目录 | FU 所属团队 = 本团队 | 查看；配置 Access（谁能发起、查看）、Audit Access；部署、导入、启用、停用、删除（已确认，D-10）。导入时只能选自己担任 Technical Lead 的团队（§8.1） |
| SLA 策略 | `ac_sla_policies.function_unit_code` 属于本 WS | 配置 |
| Relation Table 结构与数据 | `rt_table_function_units` 只关联本 WS 的 FU | 新建、修改。新建时必须关联至少一个本 WS FU；被其他 Workspace 的 FU 共用的表只读，修改需 SYS_ADMIN |
| Automation flows、runs | AP workspace = 本团队 | 只读查看 flow 与执行记录 |
| 审计 | User Portal 审计按 `functionUnitCode` 属于本 WS 过滤；Admin 操作审计属于公共资源 | User Portal 审计：只读本 WS 的。Admin 操作审计：只读全部（D-21 起，原 D-11 “不开放”作废） |
| BI | Data View Assignment 关联的是 `dw_function_units.id`（附录 A-26），按团队过滤只能经 FU code 对到 `sys_function_units` 的所属团队，不能读 DW 的团队关系表；Audience Assignment 不分团队（D-15） | Data View Assignment（已确认）：Technical Lead 只能把看板绑到本团队 FU 的 View，看板可任选；SYS_ADMIN 可把任何看板绑到任何 FU 的 View。Audience Assignment：本期不限，可把任何看板分给任何用户、Role、BU（已确认，D-15）；敏感看板靠 SYS_ADMIN 配的 Superset 角色限制（RBAC Mapping）控制。Dashboard Registry 只读选用；RBAC Mapping 不开放 |
| Organization：BU | 不分团队（§8.3） | 全部 BU：新建、修改、移动、删除 |
| Role：Custom 列表（BU_BOUNDED） | 不分团队（§8.3） | 全部业务角色：新建、修改、删除；System 列表的 4 个角色不开放 |
| BU 成员、UBR | 公共资源 | 只读；不能增删，SYS_ADMIN 可以（已确认，D-13）。用户的业务身份走 Portal 申请审批 |
| BU 审批人（Approver）、BU 退出 | 公共资源 | 只读；只有 SYS_ADMIN 能维护（已确认，D-19）。审批人决定谁能拿到 BU 业务身份，与 UBR 同类 |
| 团队成员 | 本团队两份名单 | 只读 |
| 环境变量 | 公共资源 | 只读：TEXT 类型显示值；VAULT 类型只显示键名和“已配置”，不显示 Vault 路径和值 |
| 用户管理、Virtual Group（含所有组的成员）、系统配置、Automation Pieces、LDAP 同步记录、RBAC Mapping | 公共资源 | 只读（已确认，D-21）。页面不显示任何写操作入口（含触发 LDAP 同步），表单只读展示；直接调写接口返回 403（§3.3）。系统配置里的密码、密钥类值不显示，与 VAULT 同样处理 |
| 其他团队的 FU 及其 SLA、Relation Table、Automation、User Portal 审计、Data View Assignment | 属于其他团队，不是公共资源 | 不可见 |

### 8.3 BU、Role 这类非 FU 资源

FU、SLA、Relation Table、Automation、User Portal 审计都能顺着“FU → 团队”判断是不是本 Workspace 的。BU 和业务 Role 是全平台共用的，不属于任何 FU，也不属于任何团队。

**所有 Technical Lead 都可以维护全部 BU 和业务 Role，不区分是哪个团队建的（已确认，D-12）。** 不记录创建团队，不按团队过滤。

- 范围：Organization 里的全部 BU；Role 的 Custom 列表。System 列表的 4 个平台角色仍只有 SYS_ADMIN 能改。
- 一个团队的修改会影响所有引用它的 FU，这一点已接受。修改、删除前提示被哪些 FU 引用，仅作提示，不拦截。删除仍受数据完整性约束：被流程分派、UBR 引用的角色拒绝删除（§4.3）。
- Relation Table 仍按 FU 判断（§8.2），被其他团队 FU 共用的表只读。
- Admin 中的配置权限不等于 Portal 中的数据访问权限。

---

## 9. Auditor

- **Admin：** 保持现状的只读范围。修复现状 BI 分配的写接口对 AUDITOR 放行的问题（`BiAdminAccessGuard`）。
- **DW：** 所有 Workspace 只读（已确认，D-08）。后端已支持全局只读，现状只是前端拦截。
- **Portal：** 同样需要 CUSTOM 准入，进入后按 UBR，没有全局业务读取。

---

## 10. 迁移与切换

**上线方式（已确认）：本需求的全部内容一次上线。** 没有“新旧并存期”，旧版本里做不了的配置都在停机窗口内完成。

1. **盘点（只读报表，发布前在旧版本上跑）：**
   - 切换后会失去 Portal 准入的用户：只靠 SYSTEM、DEVELOPER 组进 Portal 的人（典型是 Hermes Default Users 的成员），以及只靠 Department Managers、Public 这两个即将删除或改类型的组进 Portal 的人。
   - 当前持有 TECH_LEAD、TEAM_LEAD、DEVELOPER 的用户及其来源。
   - FU 归属异常（§6.4）。
   - 所有 CUSTOM 组的角色绑定（切换时删除），以及 BU_UNBOUNDED 角色、`BU_UNBOUNDED_ROLE` 分派的现网使用情况（D-14）。
   - 引用 TEAM_LEAD 的 BPMN、FU Access、BI 映射。
   - 没有 Entity / Function Manager 的用户；两位经理都没有 Portal 准入的用户（§7.3）。
   - 访问配置为空的已发布 View（D-05 后无人可见，含每张表自动生成的默认 View），交给各团队在切换前配好 BU + Role。
   - 回归脚本、E2E 用到的测试账号切换后是否仍在某个 CUSTOM 组；否则 `regression:mi` 等全部登录失败。
   - 按名称重算后 code 冲突或为空的团队（§5.4）。
   - 仍用旧写法（`VIRTUAL_GROUP`、`BU_UNBOUNDED_ROLE`）分派的已发布流程（例如种子里的数字信贷流程）。这些节点现状就分派不到人，切换不会让它们更坏，但应交给所属团队改成现行分派方式。
2. **发布前整理（旧版本运行中，用现有界面就能做）：**
   - 补齐缺 Entity / Function Manager 的用户。
   - 有 DW 的环境：把每个 FU 整理成恰好挂一个团队（现有界面可增删团队），否则唯一约束加不上。
3. **准备各环境的切换配置脚本**（运维脚本，按环境一份，不进 `init-scripts`；先在 UAT 演练）：
   - 团队：名称（code 自动生成）、Developer AD 组、Technical Lead AD 组。名称须与 DW 所在环境完全一致，code 才对得上。迁移会清空团队原有的 AD 映射（D-29），所以每个经 AD 同步成员的团队都要在这里重新配置。
   - CUSTOM 组及其 AD 映射，至少有一个映射原 Hermes Default Users 的 AD 组，否则开放后大量用户进不了 Portal。
   - 需要用 Portal 的管理员、两位经理都没有准入的那批经理，加入某个 CUSTOM 组。
   - System Administrators、Auditors 的 AD 映射。
4. **停机窗口内切换：**
   1. 备份相关表。
   2. **先停掉全部旧版本服务，再手工执行迁移脚本，最后部署新版本。** 顺序不能换：新版 Admin 一启动就会做 AD 同步，若此时迁移还没执行，团队成员仍是迁移前的普通成员行，会被当作不属于任何名单而删除，迁移时便读不到（D-30 认定的 Technical Lead 会悄悄丢失）；旧版 Admin 在迁移后运行，又会把旧 SYSTEM 组和 TEAM_LEAD 重建回来。新版 Admin 首次启动时设 `LDAP_SYNC_ENABLED=false`，第 3 步做完后再打开（第 4 步）：否则启动同步会在团队配好 AD 组之前，按 D-29 清掉迁入名单的 AD 来源行。已存在的库不会自动执行新增的 `init-scripts`，dev 用 psql，k8s 环境执行 `deploy/k8s/init-data` 中同步追加的部分；全新环境由初始化流程执行。迁移脚本编号排在所有种子之后：全新初始化时，`01-create-roles-and-groups` 仍会先建旧组，`05`、`15-platform-showcase`、`90-post-seed` 等往旧组加成员、把 FU 挂到旧组，再由迁移脚本清掉。迁移内容：
      - 重建 SYSTEM 组，初始化 `developer` 账号（§5.2）。
      - 删除 TEAM_LEAD；清除 `sys_role_assignments` 中 4 个平台角色及 TEAM_LEAD 的行，只保留两个 SYSTEM 组的固定绑定（§4.1）。
      - MANAGER 改为非系统角色，删除 Department Managers 组。
      - 删除 CUSTOM 组的角色绑定；删除存量自建的 BU_UNBOUNDED 角色及其授权、引用（D-64）。
      - Public 改为 DEVELOPER 类型。
      - 加 FU 唯一约束。
   3. 执行本环境的切换配置脚本。
   4. 把 Admin 的 `LDAP_SYNC_ENABLED` 改回 `true` 并重启，再手动触发一次 AD 同步，核对各组成员数；读不出或只读到一部分的 AD 组会写在同步记录的说明里。
   5. 按新规则跑一遍预检，确认“谁会失去什么”与发布前的盘点一致，由 SYS_ADMIN 确认。
   6. 冒烟：AC-06、AC-07、AC-11、AC-17、AC-25、AC-26，以及一次 DW 部署（AC-22）。通过后开放。
5. **开放后：** 生产存量 FU 由 SYS_ADMIN 重新导入补上所属团队（§8.1），不阻塞开放。之后按 §12 逐条验收。
6. **回滚：** 恢复第 4.1 步的备份 + 回退全部服务版本。只在开放前可以无损回滚；开放后再回滚，会丢掉开放之后产生的数据（新发起的流程、办理记录、申请、成员变化），须另行评估。

---

## 11. 决策

### 11.1 已确认（2026-10-29 ~ 31，共 43 项）

| # | 决定 | 落点 |
|---|---|---|
| D-01 | Department Manager 保留为业务角色，移到 Role 的 Custom 列表；删除 Department Managers 虚拟组 | §4.3 |
| D-02 | SYSTEM 组全部删掉重建，成员不迁移；初始 SysAdmin 账号见 D-22 | §5.2 |
| D-03 | CUSTOM 组只管成员能否进 Portal，不绑定角色；BU + Role 由用户在 Portal 权限自助里申请 | §5.3 |
| D-04 | SYS_ADMIN 不能直接进 Portal，必须是某个 CUSTOM 组的成员；其他身份同理 | §7.1 |
| D-05 | 取消 SYS_ADMIN 在 Portal 内的全部业务旁路，一律按当前 BU + Role 判断 | §7.2 |
| D-06 | 移除后立即生效，之后的登录被拒；正在使用的会话最多保留旧权限 15 分钟（三端 access token 15 分钟，续期时重新校验）；会话 8 小时空闲超时，不设绝对上限；凭证有效期代码里不设兜底，缺配置启动失败 | §7.1 |
| D-07 | 失去准入者的待办：认领池任务由同 BU + Role 成员在 Portal 释放、认领、指派；直接指派给个人的任务由原办理人的 Entity Manager、Function Manager 在 Portal 转派；不设 Admin 兜底，每个用户都必须有两位经理（经理路径已由 D-42 改写：办理人离职才自动指派给经理，只失去准入不转） | §7.3 |
| D-08 | 只有 AUDITOR 身份的用户可以进入 DW，所有 Workspace 只读 | §6.1、§9 |
| D-09 | 团队停用后，SYS_ADMIN 仍能在 DW 看到并迁走它的 FU（标注“已停用”） | §5.4 |
| D-10 | Technical Lead 在 Admin 可以部署、导入、启停、删除本 Workspace 的 FU；SYS_ADMIN 对所有 FU 都可以 | §8.2 |
| D-11 | ~~Technical Lead 不能查看 Admin 操作审计~~ 已被 D-21 取代：可只读查看。User Portal 审计仍只看本团队 FU 的 | §8.2 |
| D-12 | 所有 Technical Lead 都能维护全部 BU 和 Custom 列表的业务 Role，不分团队 | §8.3 |
| D-13 | Technical Lead 不能在 Admin 直接给人加 BU 成员、UBR；SYS_ADMIN 可以 | §8.2 |
| D-14 | 不带 BU 的角色只有系统自带的 4 个；不再有自建的 BU_UNBOUNDED 业务角色，业务角色一律 BU + Role 从 Portal 申请 | §5.3 |
| D-15 | BI Audience Assignment 本期不限：所有 Technical Lead 都能把任何看板分给任何人。“只给 SYS_ADMIN”“给看板加所属团队”两种做法都有局限，暂不采用；以后要收紧再另议。Data View Assignment 本期就限制：Technical Lead 只能绑到本团队 FU 的 View，SYS_ADMIN 不限 | §8.2 |
| D-16 | 重建的 System Administrators、Auditors 沿用原 id / code；`admin`、`44027893` 不迁移 | §5.2 |
| D-17 | 按候选人列表分派的任务、旧流程遗留的按虚拟组分派任务，办理人失去准入时与认领池同规则（D-58 起不含旧虚拟组） | §7.3 |
| D-18 | 委托人或被委托人任一方失去准入，委托规则停止生效 | §7.3 |
| D-19 | BU 审批人、BU 退出只有 SYS_ADMIN 能维护 | §8.2 |
| D-20 | DW 内 Automation 页面与 DW 同一权限口径 | §6.1 |
| D-21 | Technical Lead 看 Admin Dashboard 与 SYS_ADMIN 相同；用户、虚拟组及成员、系统配置、Automation Pieces、LDAP 同步记录、RBAC Mapping、Admin 操作审计、BU 成员与审批人等公共资源都可只读查看；密钥类值不显示 | §8.2 |
| D-22 | 初始 SysAdmin 各环境与现状一致：dev 用 `developer`（密码 `password`），k8s 环境用现有的 `admin`；迁移时放进新 System Administrators | §5.2 |
| D-23 | 部署或导入时目标环境没有这个团队：不论是谁、走哪条路径，都提示“请先建团队 X”；已停用的提示先启用；不自动建、不默认 Public、不能改选绕过 | §8.1、§6.3 |
| D-24 | 迁移时现有团队（DEVELOPER 组）的成员按现有全局角色进名单：持有 TECH_LEAD 的进该团队 Technical Lead 名单，持有 DEVELOPER 的进 Developer 名单，两者都没有的（现状只读）不迁移、列入盘点；来源沿用原来的（AD / 人工）（2026-10-31） | §5.4、§10 |
| D-25 | Portal 审批人不能把人移出虚拟组或整个 BU（保持现状，§5.5）；只能移除 BU 角色（2026-10-31） | §5.5 |
| D-26 | DW 部署只能发往本环境的 Admin（2026-10-31） | §8.1 |
| D-27 | Portal 处理人名称对已软删除的用户仍显示名字（2026-10-31） | — |
| D-28 | D-24 里的“持有 TECH_LEAD”只认人工来源：直接分配、人工加入的组、组绑定。只经 AD 同步得到的 TECH_LEAD（现状一个 AD Developer 组同时灌三个旧组，A-2）按 Developer 迁移；真正的 Technical Lead 由切换配置脚本配好 TL 的 AD 组后经同步回填（2026-10-31） | §10 |
| D-29 | 切换时清空各团队原有的单个 AD 映射（原映射同步的是普通成员，直接当作 Developer 组会把只读成员变成 Developer）；按 D-24 迁入名单的 AD 来源行保留。AD 同步的通用规则：某份名单没有 AD 映射时，同步删除这份名单的 AD 来源行，人工来源不动（2026-10-31） | §5.6、§10 |
| D-30 | 按 D-28 认定的 Technical Lead 迁入名单时记为人工来源（不论其团队成员关系是否来自 AD），AD 同步不会删除，直到 SYS_ADMIN 撤销；Developer 名单行仍沿用成员关系原来的来源（2026-10-31） | §10 |
| D-31 | AD 同步不得让 System Administrators 失去最后一名有效成员：这次同步的删除会让它没有有效成员时，保留它的 AD 来源行，并在同步记录里写明；其他组照常（2026-10-31） | §5.2、§5.6 |
| D-32 | AD 同步带来的成员增减逐人写进系统审计，操作人记为 AD 同步，与人工维护用同一套审计（2026-10-31） | §5.5 |
| D-33 | 删除团队时 Admin 只看自己的 FU 目录（`sys_function_units.team_code`），不读 DW；DW 里挂在已删除团队上的 FU 按“团队不可用”处理，SYS_ADMIN 在 DW 可见、标注并迁走（同 D-09）（2026-10-31） | §5.4 |
| D-34 | Admin 导入已有 FU 的新版本、且团队与 Admin 现有归属不同时，操作人须在原团队和新团队都有部署权限（SYS_ADMIN，或两边的 Technical Lead / Developer；手动导入两边都须是 Technical Lead）。DW 里移团队本来就要两边 TL，由他或 SYS_ADMIN 做移动后的第一次部署；只在新团队的 Developer 被拒。堵住用手工改过的包把别的团队的 FU 挪走（2026-10-31） | §8.1 |
| D-35 | 克隆只要求能看到源 FU（自己名单所在的有效团队、Public；SYS_ADMIN / AUDITOR 看全部），目标必须是自己能新建的团队（Technical Lead）或 Public（SYS_ADMIN）。与“能导出就能当旧包导入”一致，不设只挡正常路径的限制（2026-11-01） | §6.2 |
| D-36 | 认领池任务（BU + Role 池、MI 按角色分派的行），无论是否已被认领，都只在对应 BU + Role 的工作区显示，与 MI 按角色分派一致；切换工作区后已认领的池任务暂时从 To Do 消失，切回来才看到。只管 To Do 里是否显示：认领、办理接口与任务页不按工作区拦（邮件链接在任何工作区都能打开）（2026-11-01） | §7.2 |
| D-37 | UBR 授予 / 撤销后由 Admin 通知引擎重算该 BU + Role 池的候选人；通知失败（引擎不可达）不回滚 UBR，由引擎定时按当前 UBR 全量重算兜底，也覆盖不经过 Admin 的变化（2026-11-01） | §7.3 |
| D-38 | 按层级角色分派（HIERARCHY_ROLE）的候选人池不随 UBR 变化重算，候选人仍在建任务时确定（2026-11-01） | §7.3 |
| D-39 | 未认领、候选人全部失去准入的任务：分配给具体人的（候选人列表）交给这些候选人的 Entity / Function Manager（D-42 改写：候选人全部离职时自动指派给第一个有可用经理的候选人的经理，不再有「下属失去准入的待办」页签）；分配给 BU + Role 的（以及按成员资格分派的旧虚拟组任务）不处理，等有人获得该组合后在池里认领（2026-11-01） | §7.3 |
| D-40 | 撤销某人的 UBR 时，他已认领的该 BU + Role 池任务自动释放回池，由池里其他成员认领（2026-11-01） | §7.3 |
| D-42 | 改写 D-07 的经理路径（用户 2026-11-01 定）：直接指派给个人的任务，办理人账号仍 ACTIVE（只是失去 CUSTOM 准入）时**不转**，由本人重新申请进入 Portal；办理人**离职**（账号 INACTIVE 或已删除；LOCKED 不算离职）时，任务**自动指派**给其 Entity Manager，没有或不可用则 Function Manager，任务直接出现在经理的 To Do，经理自己办理或转办；「下属失去准入的待办」页签因此取消。认领池任务不变（AC-26：办理人失去准入即可由同池成员释放、接手、指派）。D-39 的候选人列表任务同理：只有候选人全部离职才自动指派给经理 | §7.3 |
| D-53 | 离职员工的任务照样自动指派给经理，即使经理暂时没有任何 UBR（Portal 权限自助模式打不开 To Do）；经理申请到 UBR 后即可办理（2026-11-01） | §7.3 |
| D-54 | 按候选人名单 / 旧虚拟组分派的任务（D-17）：持有人失去准入后，名单里的其他成员可以打开该任务（接手前只读、没有办理按钮）；持有人仍有准入时照旧只有持有人能打开（2026-11-01；D-58 起不含旧虚拟组） | §7.3 |
| D-55 | 单次委托的被委托人失去准入时，这条委托不生效，任务回到委托人可以处理；被委托人恢复准入后委托重新生效。与 D-18 站立委托同口径（2026-11-01） | §7.3 |
| D-56 | 单次委托只对能委托的任务开放：Portal 只对能委托的任务显示“委托”按钮，不能委托的任务直接调接口回明确的业务错误而不是 500；普通任务的委托能力不在本需求里扩大（HEAD 上普通任务委托即失败，2026-11-01） | §7.3 |
| D-57 | 单次委托给 BU + Role 组合时，组合整体不算“被委托人失去准入”：每个成员没有准入就不能代办，但组合里没人有准入时委托不标“未生效”，任务仍在委托人名下照常可办；与 D-39 同口径（2026-11-01） | §7.3 |
| D-58 | 旧虚拟组分派的任务不再处理：平台已不再把任务分派给虚拟组，D-17、D-54 只承诺候选人名单任务；旧虚拟组任务的接手、只读打开不做界面支持（2026-11-02） | §7.3 |
| D-59 | 任务换持有人（池内接手、池内指派、D-42 离职转经理）或持有结束（持有人自己取消认领、强制释放、D-40 系统释放）时，清掉原持有人做的单次委托；委托是原持有人的个人安排，新持有人不继承；任务回池那一刻委托就结束，之后谁认领（含原持有人自己）都不恢复，需要时重新委托；变更历史记一笔委托结束（2026-11-02） | §7.3 |
| D-60 | 任务已有持有人时，引擎完成任务只认持有人和生效中的单次委托，其他候选人直调引擎一律拒绝（与 Portal 口径一致，不再只靠 Portal 拦截和网络隔离）；转办、委托同口径：有持有人时只有持有人能转办、能委托；无人持有的池任务维持现状；引擎退回与 MI 前置任务的行指派不在此列（用户 2026-11-02 定维持现状，登记范围外）（2026-11-02） | §7.3 |
| D-61 | 改写 D-45 的管理口径（用户 2026-11-02 定）：还没有所属团队的存量 FU，TL 可以经 Admin 手动导入同 code 的包、选自己的团队把它认领到本队（覆盖生产版本），作为存量迁移的一种方式；认领前对 TL 仍不可见。已有团队的 FU 仍按 WP7 / D-34 规则 | §8.1、C-5 |
| D-62 | 别队 FU 的 code 与名称对 TL 可见是有意接受的（用户 2026-11-02 定）：BU / Role 引用提示、共用关联表的链接列表、Admin 操作审计（D-21）里会出现；只显示名称，不能打开或修改。§8.2「其他团队的 FU 不可见」指 FU 目录与管理操作 | §8.2 |
| D-63 | LDAP 同步记录只读页（D-51）对 TL、AUDITOR 原样显示失败信息（截断的异常文本，可能含 LDAP 主机名或 DN），方便排查；不含密码（用户 2026-11-02 定） | §8.2 |
| D-64 | 细化 D-14（用户 2026-11-03 定）：不带 BU 的角色只保留系统自带的那几个（库里类型是 ADMIN / AUDITOR / DEVELOPER）；存量自建的 BU_UNBOUNDED 角色切换时直接删除，不转为 BU_BOUNDED；代码里连 `BU_UNBOUNDED` 类型一起删除 | §5.3、§10 |
| D-43 | AUDITOR 在 Admin 改为全平台只读：TL 能看的页面 AUDITOR 都能看，团队资源看全部团队（2026-11-01） | §8.2、§9 |
| D-44 | 环境变量的 Vault 路径、系统配置里的加密 / 密码类值，对所有非 SYS_ADMIN（含 TL、AUDITOR）隐藏，只显示键名与“已配置”（2026-11-01） | §8.2 |
| D-45 | 还没有所属团队的存量 FU（重新导入前）对 TL 不可见，只有 SYS_ADMIN 能管；写进上线清单（2026-11-01） | §8.1、C-5 |
| D-46 | SLA 策略与 BI Data View 按团队过滤：沿用两模块现有查询带出的 FU code，再对到 `sys_function_units` 的所属团队；不新增 dw 表、不读 DW 的团队关系表；两模块既有的 dw_* 依赖另立项迁移（2026-11-01） | §8.2、附录 A-26 |
| D-47 | Relation Table：不关联任何 FU 的 Common 表对 TL 只读；TL 新建或修改表时只能关联本队 FU，让别队共用须 SYS_ADMIN（2026-11-01） | §8.2 |
| D-48 | Public workspace 的 Automation flows 与执行记录对 TL 只读可见，与 DW 一致（2026-11-01） | §8.2 |
| D-49 | 本队 FU 的“恢复归档”和“回滚”对 TL 开放（2026-11-01） | §8.2 |
| D-50 | 给 BU 绑定 / 解绑可申请的角色算“修改 BU”，TL 可写；申请仍须 BU 审批人批准（2026-11-01） | §8.3 |
| D-51 | 新建只读的 LDAP 同步记录页：SYS_ADMIN、TL、AUDITOR 都能看，只有 SYS_ADMIN 能触发同步（2026-11-01） | §8.2、AC-29 |
| D-52 | 按已有决策推定（2026-11-01）：RBAC Mapping 对 TL 只读（D-21 覆盖 §8.2 BI 行的“不开放”）；TL 编辑本队表数据时可经 lookup 字段搜索其引用的表，与 D-41 同口径（用户 2026-11-02 确认：能进 RT 数据页就能调 `/search`、`/view-fields`，不另校验目标表是否被本队表的 lookup 字段引用）；角色成员弹窗对 Custom 角色显示持有该角色的用户与 BU | §8.2、§8.3 |
| D-41 | 表单里的 lookup 搜索（`/relation-tables/{id}/search`）保持现有逻辑：能用这个表单就能查它 lookup 的关联表，不另按关联表授权校验；只要求是已登录的 Portal 会话（2026-11-01） | §7.2 |
| C-7 | 团队 code 由团队名称生成，建好后名称、code 都不能改，保证 FU 包跨环境部署时按 code 自动对上 | §5.4 |
| C-8 | 只读的页面直接不提供写操作入口，不能等保存时才报 403；后端 403 只拦直接调接口 | §3.3 |
| C-9 | 缺 `JWT_SECRET` 等必填密钥时服务启动失败，不再用占位字符串当默认值 | §7.1 |
| C-10 | 前端所有平台会话请求遇 401 统一先续期再重试（方案 A），三端共用一次续期 | §7.1 |
| C-1 | Admin 所有接口补后端角色校验 | §8.1 |
| C-2 | Public 改为 DEVELOPER 类型 | §6.4 |
| C-3 | AD 组找不到、全量同步先删再拉，这两个风险接受；但任何同步都不能删人工成员 | §5.6 |
| C-4 | Technical Lead 在本 Workspace 内拥有 DW 全部操作权限 | §6.2 |
| C-5 | 团队 = Admin 里的 Developer 虚拟组记录 = DW Workspace；导出包带 FU 所属团队 code。DW 部署按包里的团队；Admin 手动导入时选团队（SYS_ADMIN 任意，Technical Lead 只能选自己的团队）；生产存量 FU 靠重新导入补齐 | §3、§8.1 |
| C-6 | 本需求全部内容一次上线，停机窗口内完成迁移与配置 | §10 |

### 11.2 待确认

无。

---

## 12. 验收标准

| # | 场景 | 期望 |
|---|---|---|
| AC-01 | 用户在 Team-A 的 Technical Lead 名单、Team-B 的 Developer 名单 | 在 A 能新建、删除 FU；在 B 只能编辑、发布；直接调 B 的删除 API 返回 403 |
| AC-02 | Developer 调用新建、删除、克隆、恢复归档、移动 API | 全部 403 |
| AC-03 | SYS_ADMIN 不在任何团队 | 能修改、删除、移动任意团队的 FU，也能写 Public |
| AC-04 | 新建、导入、克隆、移动时传 0 个或 2 个 Workspace | 后端拒绝；数据库中不存在挂了 0 个或 ≥2 个 Workspace 的 FU |
| AC-05 | 停用 Team-A | A 的成员在 DW 看不到 A；A 的 Technical Lead 失去 Admin 权限；A 的已发布 FU 在 Portal 照常可用 |
| AC-06 | 用户只在 System Administrators 组、不在任何 CUSTOM 组 | Portal 登录被拒，错误码 `PORTAL_ENTITLEMENT_DENIED` |
| AC-07 | 用户在 CUSTOM 组、没有 UBR | 只能进入权限自助；直接调待办、发起接口返回 403 |
| AC-08 | SYS_ADMIN 人工把某用户加入某 CUSTOM 组，随后跑 AD 同步 | 该用户仍是成员，能进 Portal |
| AC-09 | 用户同时是 AD 与人工成员，撤销人工来源 | 仍是成员，页面显示剩余来源为 AD |
| AC-10 | 某组同时有 AD 成员和人工成员，把它映射的 AD 组名改成不存在的名字后同步 | AD 成员被删（已接受）；人工成员全部保留，仍能进 Portal |
| AC-11 | 重启 Admin、手动触发 LDAP 同步 | 不再出现 Technical Leads、Team Leads、Developers、Hermes Default Users 组 |
| AC-12 | 尝试移除 System Administrators 最后一名有效成员 | 拒绝 |
| AC-13 | Technical Lead 直接调其他 Workspace FU 的 Access 配置 API、修改 System 列表的角色、修改用户、维护 Virtual Group | 全部 403 |
| AC-14 | Technical Lead 查看环境变量 | TEXT 显示值；VAULT 不返回值和路径（以接口响应为准，不只看页面） |
| AC-15 | 把用户移出最后一个 CUSTOM 组；把 SYS_ADMIN 移出 System Administrators；把 Technical Lead 移出团队名单 | 立即重新登录被拒；已登录的会话不超过 15 分钟后续期失败，回到登录页；期间正常操作不受打断（自动续期），包括上传和进行中的 DW 部署 |
| AC-31 | 删掉某服务的 `JWT_EXPIRATION` 或 `JWT_REFRESH_EXPIRATION` 配置后启动；登录后 8 小时不操作 | 服务启动失败并报出缺哪个配置；8 小时后再操作回到登录页 |
| AC-32 | Team-A 的 Technical Lead 把任一看板分给某 BU；把看板绑到 A 的 FU 的 View；绑到 Team-B 的 FU 的 View；SYS_ADMIN 把看板绑到 Team-B 的 FU 的 View | 第一、二、四项成功；第三项 403（直接调接口也是 403） |
| AC-33 | 按候选人列表分派的任务被 A 认领后，A 失去准入 | 同一列表里有准入的人能释放、认领、指派；列表里没人有准入、且 A 已离职（账号 INACTIVE 或已删除）时，任务自动指派给 A 的 Entity Manager（不可用则 Function Manager），出现在经理的 To Do；A 账号仍 ACTIVE 时任务不转（D-42） |
| AC-34 | 委托人 A 设了站立委托给 B，随后 A 失去准入；另一条委托的被委托人 C 失去准入 | 两条委托都停止生效：B 看不到 A 的任务；C 的委托任务回到原办理人；恢复准入后委托重新生效 |
| AC-35 | Technical Lead 调 Admin 维护 BU 审批人、BU 退出的 API | 403；SYS_ADMIN 成功 |
| AC-36 | DW 的 Automation 页面：SYS_ADMIN、Team-A 的 Technical Lead、Team-A 的 Developer、Auditor 分别进入 | SYS_ADMIN 能进所有团队的 workspace；A 的 TL、Developer 只能进 A；Auditor 只读 |
| AC-37 | 迁移后查 System Administrators、Auditors 与 `sys_role_assignments`；分别用 `developer`、`admin` 登录 Admin | 两组 code、id 与原来相同；`sys_role_assignments` 里没有直接分配给用户的平台角色。dev 环境：只有 `developer` 在 System Administrators，`admin` 不再有 SysAdmin。k8s 环境：只有 `admin` 在 System Administrators，没有 `developer` 账号 |
| AC-38 | 在两个环境分别用名称 “Payment Ops” 建团队；DW 部署、导出包导入另一环境；再把 FU 部署到一个没有该团队的环境 | 两个环境的 code 都是 `PAYMENT_OPS`，部署和导入不用选团队即自动归入；没有该团队的环境，SYS_ADMIN 与 Technical Lead 手动导入、DW 部署都提示“请先建团队 PAYMENT_OPS”；团队已停用时提示先启用；没有自动建出团队，FU 也没有被归到 Public |
| AC-39 | 新建团队：名称为纯中文；名称与已有团队生成同一 code；建好后改名 | 前两项被拒绝并提示原因；改名不允许 |
| AC-40 | Technical Lead 打开 Dashboard、Admin 操作审计、用户列表、虚拟组成员、系统配置 | Dashboard 与 SYS_ADMIN 看到的相同；其余只读可见；系统配置里的密码、密钥类值不返回（以接口响应为准） || AC-16 | 代码与种子中搜索 `TEAM_LEAD` | 只剩历史审计相关的读取 |
| AC-17 | 全新初始化后用 `developer` / `password` 登录 | Admin 全部菜单可用；DW 能看到并修改任意 Workspace 的 FU；Portal 能进入，并有「共享财务中心」Department Manager 工作台 |
| AC-18 | 查看 Admin → Role 与 Virtual Group | System 列表只有 4 个角色；Department Manager 在 Custom 列表；没有 Department Managers 组；E2E 邮件回复流程的 Review 节点仍分派到财务中心 MANAGER 持有人 |
| AC-19 | 查看 CUSTOM、DEVELOPER 组；查 `sys_virtual_group_roles` | 页面没有 Bind Roles；表中只剩 System Administrators、Auditors 两条绑定 |
| AC-20 | Public | 显示在 Developer 列表，不能停用、删除；Automation 的 Public workspace 照常可用 |
| AC-21 | Team-A 的 Technical Lead 在 A 内新建、导入、克隆、删除、恢复归档 FU，并把 FU 移到另一个自己是 Technical Lead 的团队 | 全部成功 |
| AC-22 | Team-A 的 Developer 从 DW 部署 FU；SYS_ADMIN 在 Admin 手动导入一个团队 code 为 A 的包；导入一个不带团队 code 的旧包 | DW 部署成功，Admin FU 目录显示团队 A，部署过程不用选团队；手动导入时下拉预填 A，SYS_ADMIN 可改选任意有效团队；旧包必须选团队才能导入 |
| AC-23 | Team-A 的 Technical Lead 修改 Team-B 建的 BU、Custom 列表角色；新建 BU 和角色 | 全部成功；修改前提示被哪些 FU 引用 |
| AC-24 | 在 Admin 新建角色；代码中搜索 `BU_UNBOUNDED_ROLE` | 类型里没有 BU_UNBOUNDED；不传类型调建角色 API 被拒绝；`BU_UNBOUNDED_ROLE` 只剩把旧写法解析为空的那一处 |
| AC-25 | SYS_ADMIN 在某 CUSTOM 组、只有 BU-A + Role-X 的 UBR | 只看到对 BU-A + Role-X 开放的 FU 和 View；访问配置为空的 View 看不到；“仅参与人”View 只有自己参与的行；直接调他没参与的流程详情、强制释放认领池 API 返回 403 |
| AC-26 | 认领池任务被 A 认领后，A 被移出最后一个 CUSTOM 组 | 同 BU + Role 的普通成员在 To Do 看到“办理人已失去准入”，能释放、认领、指派给同池他人；A 仍有准入时普通成员不能这样做 |
| AC-27 | 直接指派给 B 的任务，B 账号停用；B 的 Entity Manager M 有 Portal 准入 | 任务自动出现在 M 的 To Do，M 可办理或转办；变更历史记原 / 新办理人 |
| AC-28 | Team-A 的 Technical Lead 在 Admin 部署、导入、停用、删除 A 的 FU；对 Team-B 的 FU 做同样操作；导入一个团队 code 为 B 的包 | A 的全部成功；B 的 403；导入须改选 A，否则拒绝 |
| AC-29 | Technical Lead 打开 BU 成员、UBR、审批人、用户、虚拟组、系统配置、LDAP 同步页面；再直接调这些写接口 | 页面上没有新建、编辑、删除、导入、同步按钮，表单只读；直接调接口全部 403；SYS_ADMIN 页面有入口、调用成功 || AC-30 | 只有 AUDITOR 身份的用户进入 DW；停用 Team-A 后 SYS_ADMIN 进入 DW | Auditor 能看所有 Workspace、所有写操作 403；SYS_ADMIN 看到 A 的 FU 标“已停用”，能移到其他团队 |

---

## 附录 A：现状差距（代码证据，2026-10-28 HEAD `49a9898bc`）

| # | 现状 | 位置 |
|---|---|---|
| A-1 | DW 角色全局判定：“是 FU 任一团队的成员” AND “在任何来源持有该角色” | `backend/developer-workstation/.../security/FunctionUnitWorkspaceAccessService.java:85-115`、`:373-382` |
| A-2 | 一个 AD Developer 组灌三个 SYSTEM 组；一个 AD Admin 组同时给 SYS_ADMIN 与 AUDITOR；启动时及每 2 小时重建 | `backend/admin-center/.../ldap/LdapSyncService.java:84-90`、`LdapHermesGroupSyncComponent.java:26-40` |
| A-3 | TEAM_LEAD 与 TECH_LEAD 权限相同 | `deploy/init-scripts/01-admin/02-init-developer-permissions.sql:10-44` |
| A-4 | FU 与团队多对多；`function_unit_id` 有外键（级联删除），`virtual_group_id` 没有外键；没有团队的 FU 无人能改 | `deploy/init-scripts/00-schema/28-dw-function-unit-dev-groups.sql`；issue 1713 |
| A-5 | Portal 准入 = 任一 ACTIVE 的 SYSTEM / CUSTOM / DEVELOPER 组成员 | `backend/user-portal/.../service/PortalEntitlementService.java:25-48` |
| A-6 | 角色解析有三套，都不过滤组状态：platform-security `UserRoleService` 只读 `sys_role_assignments`（Admin 登录、续期用它）；Admin `UserRoleRepository` 合并 `sys_user_roles` + `sys_virtual_group_roles` + `sys_user_business_unit_roles`（BI、FU 访问、开发权限等用它）；DW `RoleRepository` 合并 `sys_user_roles` + `sys_virtual_group_roles` + `sys_role_assignments` | `platform-security/.../UserRoleServiceImpl.java`、`admin-center/.../UserRoleRepository.java:84-97`、`admin-center/.../AuthServiceImpl.java:72`、`:219`、`developer-workstation/.../RoleRepository.java:85-148` |
| A-7 | Admin 登录只认 SYS_ADMIN、AUDITOR（另有默认关闭的本地开关 `admin.auth.developer-bypass-role-check` 可跳过）；多数写接口无后端角色校验。拦截器只覆盖 business-units、virtual-groups、approvers、exit、configs、sla-policies；另有少数 Controller 自己校验 SYS_ADMIN（Automation、LDAP 同步、BI） | `admin-center/.../service/impl/AuthServiceImpl.java:421-433`、`admin-center/.../config/WebMvcConfig.java:33-53` |
| A-8 | “至少保留一名系统管理员”校验比较的 code 写错，从不触发 | `admin-center/.../component/VirtualGroupManagerComponent.java:339` |
| A-9 | Public 是 CUSTOM 类型 | `deploy/init-scripts/01-admin/08-fu-public-group-migration.sql:21-25` |
| A-10 | 克隆复制源 FU 的全部团队（含 Public） | `developer-workstation/.../component/impl/FunctionUnitCloner.java:130`、`:302-312` |
| A-11 | BU_UNBOUNDED 角色只能经虚拟组获得；`BU_UNBOUNDED_ROLE` 分派按组绑定取人 | `platform-security/.../UserRoleServiceImpl.java:140-143`、`admin-center/.../service/TaskAssignmentQueryService.java:180-203` |
| A-12 | AD 组找不到时返回空列表，导致该组 AD 成员被全部删除 | `admin-center/.../ldap/LdapClient.java:290-293`、`LdapSyncService.java:240-243` |
| A-13 | Admin FU 目录没有所属团队字段 | `deploy/init-scripts/00-schema/01-platform-security-schema.sql:517-545` |
| A-14 | MANAGER 被 BPMN 分派引用：E2E 邮件回复流程（UAT 专用种子，`00-init-all.sh` 不加载，全新初始化没有它）、数字信贷流程、部分测试 | `deploy/init-scripts/20-email-inbound-reply/init.sql:3-5`、`:13`、`:658-695` |
| A-15 | 所有 AD 同步删除路径都只删 `added_by = HERMES_AD_SYNC` 的成员，人工成员不受影响 | `admin-center/.../ldap/LdapSyncService.java:552`、`:616`、`:782`、`:857` |
| A-16 | 种子账号 `developer` 在旧 System Administrators、Technical Leads 组，并持有财务中心 MANAGER UBR | `deploy/init-scripts/01-admin/05-e2e-test-users-and-business-units.sql:274-331` |
| A-17 | Role 页面：System 列表 = 写死的 6 个 code；Custom 列表 = 非系统的 BU_BOUNDED；BU_UNBOUNDED 两边都不显示 | `frontend/admin-center/src/utils/roleList.ts:6-22` |
| A-18 | Portal 申请某 BU 下的角色时，校验的是该 BU 自己绑定的角色，与虚拟组无关 | `user-portal/.../component/PermissionRequestSubmissionComponent.java:169-177` |
| A-19 | DW 导出包不带 FU 的团队：`FunctionUnitExporter` 不读写团队；读写团队的是新建、导入、克隆、团队维护接口、响应组装、权限判断与 AI Automation 目录 | `developer-workstation/src/main/java` 中引用 `DevGroup` 的 16 个文件；`FunctionUnitComponentImpl.java:622-650`、`AiAutomationFlowCatalogReader.java:74` |
| A-20 | 凭证有效期与兜底：各服务 yml 的 `JWT_EXPIRATION` 兜底 24 小时（DW 30 天，2026-04-23 `629e1f966` 为本地开发少登录而改）、`JWT_REFRESH_EXPIRATION` 兜底 7 天；UAT 12 小时 / 3 天，preprod 24 小时 / 7 天，`deploy/environments/prod/.env` 8 小时 / 1 天；`@Value` 默认值 Portal 2 处 24 小时、DW 3 处 30 天、引擎 1 处；DW 构造器写死 30 天；`JwtProperties` 字段默认 access 1 小时、续期 7 天；Portal、DW 续期凭证各写死 7 天 2 处（只有 Admin 读续期配置）；续期 cookie 三端写死 7 天 | `backend/*/src/main/resources/application.yml`、`deploy/k8s/config_map/{uat,preprod}/configmap-workflow-platform-config.yml`、`deploy/environments/prod/.env:38-39`；`platform-security/.../config/JwtProperties.java:25-30`、`.../JwtTokenServiceImpl.java:111`；`user-portal/.../controller/AuthController.java:80`、`:449`、`user-portal/.../PortalSessionIssuerService.java:59`、`:190`；`developer-workstation/.../controller/AuthController.java:52`、`:119`、`:196`、`:368`、`.../DeveloperSsoExchangeService.java:41`、`:72`、`:144`、`.../SecurityComponentImpl.java:51`、`:60`；`workflow-engine-core/.../SecurityManagerComponent.java:94-95`；`admin-center/.../AuthServiceImpl.java:237` |
| A-21 | 续期：Admin、DW 重新计算角色但不校验准入；Portal 登录、续期、切换工作台都校验准入 | `admin-center/.../AuthServiceImpl.java:207-232`、`developer-workstation/.../AuthController.java:161-202`、`user-portal/.../PortalSessionIssuerService.java:78`（登录）、`user-portal/.../controller/AuthController.java:204`（续期）、`:362`（切换工作台） |
| A-22 | 三个前端 401 自动续期重试；上传队列 401 时会续期但不重试，仍报“请重新登录后再上传” | `frontend/{admin-center,user-portal}/src/api/request.ts`、`frontend/developer-workstation/src/api/index.ts:58`、`frontend/shared/src/upload/queuedUploadRequest.ts:152-155`、`frontend/user-portal/.../useFieldUpload.ts:113` |
| A-23 | Entity / Function Manager 来自 `sys_users.entity_manager_id`、`function_manager_id`；AD 同步补导入的经理不进任何虚拟组 | `00-schema/01-platform-security-schema.sql:30-31`、`admin-center/.../ldap/LdapSyncService.java:297-321`、`:588`、`:691` |
| A-24 | DW 部署 = 导出 ZIP + 调 Admin `/function-units-import/import`（文件上传），并转发操作人凭证；Admin 页面手动导入调 `/function-units/import`（JSON + Base64）；两者最后进同一个导入组件；接口、组件、包解析都没有团队字段 | `developer-workstation/.../component/impl/DeploymentComponentImpl.java:280-299`、`:355`；`admin-center/.../controller/FunctionUnitImportController.java:53`、`controller/FunctionUnitController.java:94`、`frontend/admin-center/src/api/functionUnit.ts:241-242`、`component/FunctionUnitImportComponent.java`、`component/FunctionUnitPackageParser.java` |
| A-25 | DW 部署在后台线程执行：请求开始时取下用户凭证，之后上传、校验、部署各步才用；同时带内部服务令牌 + `X-User-Id`。Admin 的 `JwtAuthenticationFilter` 遇到无效 / 过期凭证只跳过不拒绝，随后 `ServiceCallAuthenticationFilter` 凭服务令牌按 `X-User-Id` 建出角色为空的身份 | `developer-workstation/.../component/impl/DeploymentComponentImpl.java:81-87`、`:129-170`、`:398-413`；`admin-center/.../config/SecurityConfig.java:200-235`；`platform-security/.../filter/JwtAuthenticationFilter.java:44-81` |
| A-26 | BI Data View Assignment 外键指向 DW 表 `dw_function_units`（到 `dw_table_definitions` 的外键已删，改按 `view_id` 关联）；Admin 的 BI、SLA 在运行时读 `dw_*` 表；看板注册表没有 FU 维度，只有 tags；Audience Assignment 的目标可以是 USER / ROLE / BUSINESS_UNIT | `deploy/init-scripts/00-schema/15-bi-management-schema.sql:11-76`、`97-bi-assignment-view-scope.sql`、`98-bi-assignment-retain-unavailable-table.sql`；`admin-center/.../BiDataViewAssignmentServiceImpl.java:196`、`SlaPolicyComponent.java:55` |
| A-27 | `sys_function_units` 每个版本一行（`version`、`previous_version_id`） | `deploy/init-scripts/00-schema/01-platform-security-schema.sql:517-545` |
| A-28 | 种子里的数字信贷流程有 4 个节点仍用旧写法 `assigneeType=VIRTUAL_GROUP` | `deploy/init-scripts/08-digital-lending-v2-en/02-insert-bpmn-process.sql:171`、`:195`、`:212`、`:283` |
| A-29 | 现有 SYSTEM 组 code：`SYSTEM_ADMINISTRATORS`、`AUDITORS`。跑过种子的环境 id 是 `vg-sys-admins`、`vg-auditors`；code 不存在时 LDAP 同步自动建组，id 为随机 UUID（Hermes Default Users 总是随机 UUID） | `deploy/init-scripts/01-admin/01-create-roles-and-groups.sql:83`、`:93`；`admin-center/.../ldap/LdapSyncService.java:503-515` |
| A-30 | DW 分派方式选项里没有 `VIRTUAL_GROUP`、`BU_UNBOUNDED_ROLE`（只剩旧值兼容分支）；引擎 `AssigneeType.fromLegacyCode` 把这两种旧写法解析为 `null`；引擎 `UserPermissionService` 仍能对已有的 `VIRTUAL_GROUP` 类任务按组成员判定权限 | `frontend/developer-workstation/src/components/designer/properties/UserTaskAssigneeConfigSection.vue:14-66`、`composables/userTaskProperties/useUserTaskAssignee.ts:84`、`:152`；`workflow-engine-core/.../enums/AssigneeType.java:93-105`；`workflow-engine-core/.../service/UserPermissionService.java:58-70` |
| A-31 | 维护 FU 团队的接口只校验对源 FU 的 `ASSIGN_DEV_GROUPS` 权限，写入的组 id 不做限制：可挂进 Public、自己不在的团队或非团队组 | `developer-workstation/.../component/impl/FunctionUnitComponentImpl.java:622-641`、`controller/FunctionUnitController.java:200-206` |
| A-32 | Portal 里的 SYS_ADMIN 旁路（View、FU 门禁之外）：流程详情、记录备注读写归档、备注附件上传来源、催办（`isSuperAdmin`） | `user-portal/.../component/ProcessApplicationQueryComponent.java:659`、`RecordNoteComponent.java:178`、`:191`、`:247`、`:288`、`:338`、`RecordNoteUploadSourceResolver.java:53`、`TaskProcessComponent.java:563` |
| A-33 | `developer` 账号只在 `init-scripts` 的 `05` 种子里，k8s all-in-one 种子没有；`admin`、`44027893` 除了在 `vg-sys-admins` 组里，还在 `sys_role_assignments` 直接分配了 SYS_ADMIN（注释写明 Admin 登录只认这张表） | `deploy/init-scripts/01-admin/05-e2e-test-users-and-business-units.sql:274-331`；`01-create-admin-only.sql:43-58`；`deploy/k8s/init-data/init-platform-seed/all-in-one-for-gui.sql` |
| A-34 | 建角色接口已要求必填类型（`@NotNull`）；建表与实体默认值仍是 `BU_UNBOUNDED` | `admin-center/.../controller/RoleController.java:245-246`；`00-schema/01-platform-security-schema.sql:69`；`platform-security/.../entity/Role.java:44` |