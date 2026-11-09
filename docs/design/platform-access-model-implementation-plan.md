# 平台访问模型 — 实施进度

| 属性 | 值 |
|---|---|
| 需求 | [platform-access-model-requirements.md](./platform-access-model-requirements.md)（PRD v1.6，已定稿） |
| 分支 | `feat/platform-access-model` |
| 上线方式 | 全部工作包做完后一次上线（PRD §10、C-6） |
| 更新 | 2026-11-06 |

> 每个工作包开工前先给出技术方案并确认；做完附 code-review 报告。未提交的改动以 `git status` 为准，提交须用户确认。

## 工作包

| # | 内容 | 依赖 | 状态 |
|---|---|---|---|
| WP1 | 凭证有效期与续期：15 分钟 / 8 小时、去掉代码兜底、三端续期复核账号、前端统一“401 先续期再重试”、上传重试、必填配置缺失即启动失败 | — | **完成**（review 第三轮 PASS，未提交） |
| WP2 | Admin 后端统一鉴权：接口权限表默认拒绝、Auditor 只读、服务间调用放行清单（按 `X-User-Id` 从库算角色）、反射守卫测试、`BiAdminAccessGuard` | — | **完成**（review 第三轮 PASS，未提交） |
| WP3 | 表结构与迁移脚本：两个 AD 组、成员来源、`team_code`、重建 SYSTEM 组、初始 SysAdmin（D-22）、清直接分配、TEAM_LEAD、MANAGER、Public、团队 code 重算、FU 唯一约束；统一角色解析（只认 ACTIVE 组）。验收须含：Admin `AdminAccessInterceptor` 按库算角色时调用统一后的解析（现 `UserRoleServiceImpl` 只读 `sys_role_assignments`，DW 还合并 `sys_user_roles`、`sys_virtual_group_roles`），并补测试（WP2 review） | WP2 | **完成**（review 第四轮 PASS，未提交） |
| WP4 | AD 同步：删除 `HERMES_BINDINGS`，按配置同步 SYSTEM / CUSTOM / 团队两份名单，两种成员来源并存；名单没有 AD 映射时删除它的 AD 来源行（D-29） | WP3 | **完成**（review 第三轮 PASS，未提交） |
| WP5 | 成员变更审计含 AD 同步（D-32）；Admin 虚拟组 / Role 页面：两份名单、成员来源与撤销人工成员、团队 code 按名称生成、SYSTEM 组保护、修复“最后一名管理员”校验、去掉 Bind Roles | WP3 | **完成**（review 第二轮 PASS，未提交） |
| WP6 | DW 按团队授权：TL 全权、删除 TEAM_LEAD、FU 唯一归属各路径、dev-groups 接口漏洞、克隆、停用团队与已删除团队（D-33）、Auditor 进入、Automation 页面、导出包带团队 code / 名称、DW 准入（续期时也校验） | WP3 | **完成**（review 第四轮 PASS，2 条 Minor 已改，未提交） |
| WP7 | Admin 两个导入接口带团队：预填与选择规则、缺团队提示（D-23） | WP3、WP6 | **完成**（review 第二轮 PASS，3 条 Minor 已改，随 WP6 第四轮重审；未提交） |
| WP8 | Portal：只认 CUSTOM 准入、删除 SysAdmin 全部旁路、DW View 空配置提示、失去准入的待办（认领池放宽、离职转给经理，D-42）、委托失效 | WP3 | **完成**（8a review 第二轮 PASS；8b review 第三轮 PASS，D-60 的 Portal 门禁随 WP9 第三轮复核通过；未提交） |
| WP9 | TL 进 Admin：登录与续期放行、按团队限定范围、公共资源只读且不显示写入口、Dashboard、BU / Role、BI | WP2、WP3、WP7 | **完成**（review 第三轮 PASS，0 Blocker、0 Major、4 Minor：`AuditSecrets.maskJson` 已改，另 3 条在帮助站；未提交） |
| WP10 | 清理 BU_UNBOUNDED 残留（D-64 连类型删除）、无调用方的旧接口、盘点 / 预检 / 断言 / 切换配置脚本、View 测试种子、旧文档更新、上线 / 切换清单 | 全部 | **完成**（review 第一轮 PASS，6 条 Minor 已改并验证，见 WP10 明细；未提交） |
| WP11 | 补测（第二阶段，用户 2026-11-06 定）：本地 Samba AD 域控测 AD 同步（AC-08 / 09 / 10、上线清单 W8）；第二套独立环境测跨环境导入（AC-38）；临时库按 k8s 顺序初始化测 AC-37 的 k8s 一侧；本地 SMTP 测带模板的邮件；非 Public 团队测 Automation 只读提示（AC-36）。企业真实 AD 仍标未验证 | WP1–WP10 | **完成**（见第二阶段 WP11 结果；发现的缺陷已分到 WP15–WP19） |
| WP12 | 运行时不读 `dw_*`：Portal 14 个文件（View 访问规则、表单绑定、表定义等）与 Admin 删 RT 前的 `hasBindings` 改读已发布版本，缺版本显式报错 | WP1–WP10 | 方案已定（2026-11-06），待实现 |
| WP13 | 文件存储迁出 DW 并加鉴权（`/upload/**` 现匿名可读，Portal 附件、引擎邮件附件、Automation 都经 DW 取文件）。**技术方案须用户确认后再实现** | WP12 | 方案已定（2026-11-06），待实现 |
| WP14 | 服务令牌按调用方分权：每个调用方一把令牌、限定可调接口；没有登录用户时不带用户身份透传；`/api/portal/internal/**` 不经 Kong 对外。**技术方案须用户确认后再实现** | WP1–WP10 | 方案已定（2026-11-06），待实现 |
| WP15 | 鉴权与会话遗留：登录先校验密码再报停用 / 锁定（防枚举）、跨端 cookie 回退、批量长循环中途凭证过期、非 XHR 入口续期、Portal 令牌加 `jti`、DW 不再接受其它端 cookie、BPMN 里邮件连接 / 模板 id 校验归属、DW 标签按团队过滤、Admin 不可达时不沿用过期能力缓存、推送与定时修复之间的互斥 | WP14 | 待做 |
| WP16 | Admin 与 platform-common 遗留：`Permission` 实体与表不符致 500、非法枚举 400 暴露类名、不支持的方法回 500 而非 405、死配置、FU 页面重复提示、RT 列表英文写死、时区写死 `+08:00`、启动 LDAP 同步不加锁 | WP1–WP10 | 待做 |
| WP17 | Portal 与引擎遗留：调不存在的 Admin 接口、废弃的 `/permissions/renew` 等接口、`getMyPermissions` 吞错、`GET /tasks/{id}` 无权限回 200、MI 多角色只取第一个、To Do 操作列不固定、委托处显示原始 id、D-18 站立委托端到端不可用、转办清委托不写历史、办理人存用户名的离职判定、引擎客户端 4xx 一律当空、邮件模板为空发出 "null" | WP1–WP10 | 待做 |
| WP18 | 关联表 / ER 图 / 帮助站遗留：RT 回滚不恢复外键 / Lookup / 主键规则 / 计算字段、ER 图 Save 后表从图上消失、ER 图连接点悬停移位、帮助站锚点不滚动 | WP1–WP10 | 待做 |
| WP19 | 初始化脚本、测试与环境遗留：`init-database.ps1` 漂移、`00-init-all.sh` 漏 `93-*`、k8s schema 包顺序与缺段、切换删任务历史、IT 测试 schema 缺列、DW 业务拒绝按 ERROR 打堆栈、dev 登录先查不可达 LDAP、DW 健康检查别名、数字信贷种子指向不存在的角色、验证脚本时序与清理 | WP1–WP10 | 待做 |

## WP1 明细

| 项 | 状态 |
|---|---|
| 四个服务 yml 的 `JWT_EXPIRATION` / `JWT_REFRESH_EXPIRATION` 改为必填（`${X}`） | 完成 |
| `JwtProperties` 去默认值并在启动时校验；`@Value` 默认值删除（Portal 2、DW 3、引擎 2） | 完成 |
| Portal、DW 续期凭证写死 7 天、三端续期 cookie 写死 7 天 → 读配置 | 完成 |
| 三端续期复核：Admin 账号状态 + SYS_ADMIN / AUDITOR；Portal（续期、切换工作台）、DW 账号状态 | 完成 |
| env 文件、UAT / preprod ConfigMap 改为 900000 / 28800000；`BUILD_GUIDE.md`、FU 开发指南同步 | 完成 |
| 上传队列 401：续期后重试一次 | 完成 |
| 测试配置补有效期；新增单测 13 个（已红→绿） | 完成 |
| **前端统一“401 先续期再重试”**：`frontend/shared/src/auth/sessionRefresh.ts` 共用一次续期；16 个平台会话 axios 实例（15 个文件，含三端认证实例，覆盖页面加载的 `/me`）+ 9 处直接 `fetch` 接入；三个主实例原有的独立续期去掉；上传队列走同一次续期。Activepieces、DSP 令牌不接入 | 完成 |
| **过期凭证必须回 401**（review Blocker，用户 2026-10-30 选方案 A）：Portal / Admin / DW 原来对无效或过期凭证按匿名放行，页面不刷新时前端永远收不到 401、不会续期，停用的人也一直能用。改为 `JwtAuthenticationFilter` 遇到无效或过期凭证直接回 401（带有效内部服务令牌的请求除外，保证 DW 后台部署不中断）；三端 access cookie 的有效期改为会话时长 8 小时，让过期凭证仍会被带上、能识别出来 | 完成 |
| **多标签页同时续期**（review Major）：续期凭证用一次即作废，两个标签页同时续期会把人踢下线 → 同源标签页用 Web Locks 排队续期 | 完成 |
| **必填配置真正必填**：`JWT_SECRET`、`ENCRYPTION_SECRET_KEY` 走 `@Value`/启动校验，缺失即启动失败；`SPRING_DATASOURCE_PASSWORD`、`SPRING_REDIS_PASSWORD` 经 Spring Boot 属性绑定时缺变量不会报错（review Major），新增 `RequiredConnectionSecrets` 严格解析这两项。DW `SecurityComponentImpl` 实际被 Spring 使用的是无参构造器、仍写死开发密钥和 30 天（之前误标“已改”）→ 删除无参构造器；引擎 `application-dev.yml` 的加密密钥默认值删除 | 完成 |
| **DW 密码登录**：检查的是不存在的 `DISABLED` 状态 → 与续期同一规则（非 ACTIVE 或锁定即拒绝），补单测 | 完成 |
| 账号锁定（LOCKED）即续期失败、已登录会话最多 15 分钟后掉线（用户 2026-10-30 确认，保持需求原文） | 完成 |
| 运行验证：三端 60 秒凭证过期后（1）刷新页面、（2）不刷新继续操作都不掉线；停用账号后不刷新也会回到登录页（`frontend/scripts/verify-session-silent-refresh.mjs`，`reload` / `in-page` / `revoke` 三种模式）；缺 DB / Redis 密码变量时 Admin 启动失败并报出变量名 | 完成（2026-10-30：9 项全部 PASS，截图在三端 `verification-screenshots/wp1-session-*`） |
| **Portal 页面不再直连 Admin**（重审 Major，由上面“过期凭证回 401”引出）：同一浏览器残留过期的 Admin cookie 时，Portal 页面直连 Admin `/users/{id}` 会 401、Portal 的续期救不回 → `userApi.getUserSummary` 改走已有的 Portal 接口 `POST /tasks/users/summaries`，删掉 `adminCenterAxios` 及其上 3 个无人调用的函数；新增守卫测试禁止 Portal 前端出现 `/api/v1/admin`（登录页除外）。实测：Admin + Portal 同浏览器、两边凭证都过期时，旧路径 401，新路径续期后 200 并返回显示名 | 完成 |
| Portal 转发给引擎的凭证与过滤器认定的不一致（转发取“第一个存在的 cookie”，过滤器认“第一个有效的”）→ 过滤器把认证通过的凭证放进请求属性，Portal 转发它 | 完成 |
| code-review | 首轮 FAIL（1 Blocker、4 Major、6 Minor、1 Question）→ 逐条处置；重审 FAIL（首轮 12 条全部关闭，新增 1 Major + 5 Minor）→ 逐条处置；**第三轮 PASS**（2026-10-30）。第三轮 Minor（过滤器写入“被接受的凭证”属性缺测试）已补断言；Question（软删除用户在 Portal 处理人名称里显示为 id）待用户确认 |

## WP2 技术方案

**调用方盘点（2026-10-30）：** 引擎、DW、Portal 三个后端调 Admin 约 110 处，多数不带任何凭证（Portal 还有 3 个 DELETE 成员接口）；带服务令牌的只有 `/internal/**`、BI、部署等少数。浏览器直连 Admin 的只剩 DW 页面上的选择器（BU 树、角色目录、开发组、用户搜索、Automation 桥）；Portal 页面已在 WP1 改走 Portal 后端。

1. **每个 Admin 接口声明访问对象。** 新注解 `@AdminAccess(value, services)`，标在 controller 类或方法上；统一拦截器 `AdminAccessInterceptor` 对 `/**` 生效，替代现有 `OrganizationMutationAccessInterceptor`、`ConfigMutationAccessInterceptor`、`SlaPolicyAccessInterceptor`（其中的例外——claim / delegate、assess-impact、SLA query——并入新表）。
   - 生效规则：方法注解优先；类注解**只对 GET / HEAD 生效**；其余一律 SYS_ADMIN。写接口不会因类上标了“可读”而被放宽，POST 形式的查询必须在方法上显式标注。
   - 访问对象：`PUBLIC`（登录、SSO、健康检查、自带鉴权的 AP 桥 nonce / SSO 内部令牌入口）；`SERVICE`（只认服务令牌，`/internal/**`，含 Vault 密码解析）；`SIGNED_IN`（任意已登录用户，Superset 作者入口——其自带 BI 角色映射校验）；`WORKSTATION`（SYS_ADMIN、AUDITOR、TECH_LEAD、DEVELOPER——DW 页面的选择器）；`ADMIN_READ`（SYS_ADMIN、AUDITOR；WP9 再加 TL）；`SYS_ADMIN`；`DEPLOYER`（SYS_ADMIN、TECH_LEAD、DEVELOPER——DW 部署走到的导入、校验、部署接口；WP7 收窄到 FU 所属团队）。
   - `services = true`：另外放行带有效服务令牌的后端调用，名单即上面的盘点。
   - 身份与角色：未登录 → 401（前端据此续期或去登录）；已登录但不在范围 → 403 `PERM_ACCESS_DENIED`。JWT 请求用凭证里的角色；服务调用（有效令牌 + `X-User-Id`）用库里现算的角色（PRD §7.1），不用凭证里的。
   - 反射守卫测试：admin-center 每个 handler 必须有显式策略（方法注解，或 GET + 类注解），并断言关键接口的取值（Vault 密码只给服务、三个部署接口是 DEPLOYER 等）。
2. **调用 Admin 的后端一律带服务令牌。** platform-common 新增 RestTemplate 拦截器：请求地址以本服务的 `admin-center.url` 开头时补 `X-Service-Token`，有登录用户时补 `X-User-Id`（已有的不覆盖）。Portal、引擎、DW 各注册一次，约 110 个调用点不逐个改。`service.internal-token` 改为必填（`${SERVICE_INTERNAL_TOKEN}`）；prod `.env` 补占位并进上线清单。
3. **BI：** 分配类写接口由统一表限定 SYS_ADMIN（修 AUDITOR 可写）；`BiAdminAccessGuard` 保持原样（取身份并按 SYS_ADMIN / AUDITOR 校验，对读接口与统一表重复但无害）。
4. **引擎 → Portal 流程完成回调：** 两端改用 `X-Service-Token` = `service.internal-token`，Portal 常量时间比对；删除 `platform.internal.service-token` 及默认值 `wf-internal-service-call`。
5. **不做：** TL 进 Admin 与按团队限定（WP6 / 7 / 9）、Portal 信任 `X-User-Id`（WP8）、AUDITOR 页面的写入口隐藏（现状已只读，若发现缺口记入 WP9）。
6. **验证：** 拦截器决策矩阵单测（访问对象 × 未登录 / AUDITOR / SYS_ADMIN / DEVELOPER / 服务令牌 / 错误令牌）、反射守卫、RestTemplate 拦截器单测、回调令牌单测；五个后端模块全量；Docker：SYS_ADMIN 走 Admin 主要页面、AUDITOR 读通写 403、纯 DEVELOPER 在 DW 用选择器并部署一个 FU、Portal 发起流程 / 待办 / 权限申请审批、引擎分派、匿名与伪造令牌调 Admin 被拒。

### WP2 明细

| 项 | 状态 |
|---|---|
| `@AdminAccess` + `AdminAccessInterceptor`（`com.admin.security`）；51 个 controller 全部标注；删除三个局部拦截器及其测试 | 完成 |
| 反射守卫 `AdminEndpointPolicyCoverageTest`（扫到 390+ 个接口）：读接口都有声明、盘点里的后端与 DW 页面调用全部放行、Vault 密码只给服务、三个部署接口是 DEPLOYER、BI 分配写接口只给 SYS_ADMIN、开放给非 SYS_ADMIN 的写接口必须在白名单 | 完成 |
| `ServiceTokenRequestInterceptor`（platform-security）注册到 Portal、引擎（两个 RestTemplate）、DW；`SERVICE_INTERNAL_TOKEN` 四个服务改为必填，prod `.env` 补占位，`BUILD_GUIDE` 补说明 | 完成 |
| 引擎 → Portal 流程完成回调改用 `X-Service-Token`，Portal 常量时间比对；`platform.internal.service-token` 与默认值删除 | 完成 |
| 全量：admin 1055 / 0F0E；portal 2204（8E 同基线）；DW 1507 全绿；engine 只有基线 5 类；platform-security 100 全绿 | 完成 |
| Docker 运行验证：`frontend/scripts/verify-admin-access-matrix.mjs` 27/27（匿名、伪造令牌、SYS_ADMIN、AUDITOR、DW 开发者、服务调用、部署按库里角色）；DW 克隆 → 部署 → Admin 配访问 → Portal 发起 → 引擎建任务并分派，全程 Admin 零拒绝；`frontend/scripts/verify-admin-access-pages.mjs` 三端 55 页无意外 401 / 403 / 5xx（AUDITOR 10 页由 Admin 前端既有路由权限挡在页面外，记 SKIP）；截图 `verification-screenshots/wp2-access-*` | 完成 |
| **review 首轮 Blocker：Portal 成员管理接口不鉴权**：`MemberController` 的移除 VG 成员 / BU 成员 / BU 角色（及两个成员列表）原来谁都能调，WP2 自动补服务令牌后会被 Admin 放行 → 五个接口改为只认登录态里的用户，且必须是目标 VG / BU 的审批人（不认请求头 `X-User-Id`），否则 401 / 403，且不会调用 Admin | 完成 |
| **review 范围外（高）：DW 部署目标地址可任意指定**，会把服务令牌和用户凭证发到任意地址 → 只允许空或本环境 `admin-center.url`（DW 前端从未传过；各环境令牌和 JWT 密钥不同，跨环境部署本来走不通） | 完成 |
| 引擎主 RestTemplate 也调用户配置的 webhook → 令牌拦截器只挂在调 Admin 的 `internalApiRestTemplate`（流程引用解析器自带令牌） | 完成 |
| 流程完成回调：Portal 令牌不对时回真正的 HTTP 403（原来 200 + 错误体，引擎会误记成功）；Portal、引擎各补单测 | 完成 |
| 守卫测试补齐 7 个漏列的后端调用；x-ray 两处与 Activepieces 集成文档的回调令牌说明已更新 | 完成 |
| 决定：同源跨端 cookie 回退保持现状。DW 页面调 Admin 选择器依赖它；能停在 Admin 页面上的只有 SYS_ADMIN、AUDITOR（WP9 后加 TL），其 DW 凭证带同样的角色；纯开发者登不进 Admin | 已定 |
| 决定：Admin `/virtual-groups/*/tasks/*/claim`、`/virtual-groups/tasks/*/delegate` 原来对任何已登录用户放行，现只给 SYS_ADMIN。全仓无调用方（Portal 用自己的 `/tasks/{id}/claim`） | 已定 |
| review 修复后的验证：全量 admin 1055 / 0F0E、portal 2210（新增 6 个通过，8E 同基线）、DW 1509 全绿（`SecureInputHandlingPropertyTest` 一次随机样例含 `\0` 失败，属既有偶发，删本地 `.jqwik-database` 后重跑通过）、engine 1142 只有基线 5 类；Docker：匿名调 Portal 成员管理 4 个接口均 401、非审批人 403、Admin 未收到任何成员调用；DW 克隆 50006 → 部署 → Portal 发起 → 审批 → 流程结束，引擎日志 “Successfully notified user-portal …”、Portal 状态 COMPLETED、全程 Admin 零拒绝；DW 部署到外部地址 → 400 `DEPLOY_TARGET_NOT_ALLOWED`；访问矩阵 27/27 | 完成 |
| **用户决定（2026-10-31，review 第二轮 Major）：Portal 审批人不能移出虚拟组成员、不能把人整体移出 BU**（保持 WP2 前的实际效果，PRD §5.5）→ 这两个接口直接 403、不调 Admin；BU 角色移除保留审批人校验；成员管理页去掉虚拟组“移除”按钮（C-8）；`AdminCenterClient` 与前端 api 中对应方法删除；新增 i18n `portal.business_unit_member_removal_not_in_portal` 三语。Docker：两个 DELETE 均 403 且提示正确；成员管理页无移除按钮（dev 无审批人数据、成员列表既有恒 503，表格行无法实拍，按钮去除以代码 + vue-tsc + 前端测试为证） | 完成 |
| **用户决定（2026-10-31）：DW 部署只能发往本环境 Admin**（PRD §8.1 已改） | 已定 |
| **用户决定（2026-10-31，WP1 遗留）：Portal 处理人名称对已软删除用户显示名字** → `UserDirectoryComponentImpl` 不再过滤已删除用户（批量处理的处理人标签同步受益）；Docker：临时把 wp2_dev 标为删除后 `/tasks/users/summaries` 仍返回 “WP2 Developer”，随即恢复 | 完成 |
| code-review | 首轮 FAIL（1 Blocker、0 Major、4 Minor、2 Question）→ 逐条处置；第二轮 FAIL（1 Major 需用户拍板、1 Minor、1 Question）→ 用户已拍板并实现；**第三轮 PASS**（2026-10-31）。第三轮 Minor（Admin “整体移出 BU” 已无调用方却仍放行服务令牌）已收紧为只给 SYS_ADMIN，守卫测试同步；临时验证账号 wp2_auditor / wp2_dev 已删除 |

## WP3 技术方案

**现状（2026-10-31 调查）：**
- 成员表唯一键 `(group_id, user_id)`，来源只靠 `added_by`（`HERMES_AD_SYNC` / `system` / 空）；虚拟组只有一个 `ad_group`。
- 三套角色解析读不同的表，都不看组状态：
  - `UserRoleServiceImpl`：只读 `sys_role_assignments`。三端登录、续期、`AdminAccessInterceptor` 都用它。
  - Admin `UserRoleRepository`：合并 `sys_user_roles`、`sys_virtual_group_roles` 与 UBR，BI、开发权限、FU 访问用它。
  - DW `RoleRepository` 三表合并，另有 `LegacyVirtualGroupRoleLookup`。
  - Admin 界面绑角色只写 `sys_virtual_group_roles`，登录看不到。
- 种子：
  - SYSTEM 组 6 个：`vg-sys-admins` / `-auditors` / `-tech-leads` / `-team-leads` / `-developers`，再加 LDAP 自建的 Hermes Default Users。
  - `admin`、`44027893` 是直接分配的 SYS_ADMIN；`developer` 在旧 SysAdmin、Technical Leads 组。
  - Platform Showcase 挂在 `vg-tech-leads` 上；Public 是 CUSTOM；k8s 种子另有已被 Public 取代的 `vg-default-dev-team`。
  - `LdapSyncService.HERMES_BINDINGS` 每 2 小时重建旧组（WP4 删除）。
- 脚本：
  - `00-init-all.sh` 只认到 99 号。
  - k8s all-in-one 手工同步，且已落后多版，补齐历史欠账不在本包。

**方案：**
1. **Schema**：`00-schema/100-platform-access-model.sql`，登记到 `00-init-all.sh`（新增 1xx 段）与 `init-database.ps1`，k8s schema all-in-one 追加本段。
   - `sys_virtual_groups` 加 `tech_lead_ad_group`。团队的 `ad_group` 是 Developer 名单的 AD 组；SYSTEM、CUSTOM 仍只用 `ad_group`。
   - `sys_virtual_group_members` 加两列，唯一键改为 `(group_id, user_id, list_type, source)`：
     - `list_type`：`MEMBER` / `DEVELOPER` / `TECH_LEAD`。
     - `source`：`AD` / `MANUAL`，按 `added_by = HERMES_AD_SYNC` 回填。
   - `sys_roles.type` 去掉默认值 `BU_UNBOUNDED`，实体同步修改。
   - `sys_function_units` 加 `team_code`，WP7 填写。
2. **迁移**：`90-post-seed/03-platform-access-model-switch.sql`。
   - 排在全部种子之后，单事务，任一检查不通过即 `RAISE` 整体回滚。
   - k8s 版追加到 seed all-in-one，初始 SysAdmin 用 `admin`（D-22）。
   - 步骤：
     1. 现有团队成员按 D-24 进名单。在删除旧 SYSTEM 组之前，按全局角色计算。
     2. 删除 Technical Leads、Team Leads、Developers、Hermes Default Users，连同成员、绑定、`sys_role_assignments` 和任务历史。
     3. 重建 System Administrators、Auditors：id 与 code 不变，清空成员和绑定，再写回固定绑定。
     4. 初始 SysAdmin：dev 用 `developer`（不存在则建，密码 `password`），并加入 `vg-e2e-workflow`。新组没有有效成员即 `RAISE`。
     5. 清除 `sys_role_assignments`、`sys_user_roles` 中 4 个平台角色和 TEAM_LEAD 的行，只留两条固定绑定。
     6. 删除 TEAM_LEAD：先删 `bi_rbac_mapping` 里的引用。
     7. MANAGER 改为 `is_system = false`；删除 Department Managers 组。
     8. 删除所有 CUSTOM 组的角色绑定。
     9. Public 改为 DEVELOPER 类型。
     10. 团队 code 按名称重算，Public 保留 `DEV_TEAM_PUBLIC`；重算后冲突或为空即 `RAISE`。
     11. `vg-default-dev-team` 没有成员、没有 FU 关联时删除。
     12. 种子修正：Platform Showcase 改挂 Public；删除指向已删组的 FU 关联；加 `dw_function_unit_dev_groups(function_unit_id)` 唯一约束。一个 FU 挂多个团队即 `RAISE`，提示先整理。
   - BU_UNBOUNDED 角色、CUSTOM 组成员、各环境的团队与 AD 映射由运维的切换配置脚本处理，不进迁移。
3. **统一角色解析**：platform-security 新增一个解析器，三端共用，只认 ACTIVE 组。
   - 平台角色：SYS_ADMIN、AUDITOR 取自 ACTIVE SYSTEM 组的固定绑定（`sys_role_assignments` 中的 VIRTUAL_GROUP 行）。
   - 团队角色：ACTIVE DEVELOPER 组的两份名单，结果是 `(团队 id, 团队 code, TECH_LEAD | DEVELOPER)`；同一团队两份都有时按 TL 算。
   - Portal 准入（WP8 用）：ACTIVE CUSTOM 组成员。
   - `UserRoleServiceImpl` 改为基于它。有效平台角色是 SYS_ADMIN / AUDITOR，外加“在任一有效团队名单里”时的 TECH_LEAD / DEVELOPER。后两者在 JWT 里只是粗粒度标记，按团队判断范围由 WP6 / WP9 做。
   - DW 的 `RoleRepository`、`LegacyVirtualGroupRoleLookup` 改用它，legacy 兜底删除。
   - Admin `UserRoleRepository` 里平台角色的来源同步修改；业务角色仍按 UBR。
   - `AdminAccessInterceptor` 自动受益，WP2 review 要求的测试在本包补上。
4. **不做**：AD 同步按名单、删除 `HERMES_BINDINGS`（WP4）；Admin 虚拟组 / 角色页面、“最后一名管理员”校验（WP5）；DW 按团队授权与 TEAM_LEAD 代码清理（WP6）；导入带团队（WP7）；Portal 准入（WP8）；Admin TL（WP9）；盘点脚本（WP10）。
5. **验证**：
   - 用一次性 postgres 容器跑全新初始化，全部 init 脚本无错。
   - 在现有 dev 库手工执行迁移（先备份相关表）。
   - SQL 断言：组、绑定、成员、唯一约束。
   - 解析器单测：停用组不给角色；两份名单时 TL 优先；直接分配不给平台角色。
   - 后端全量测试。
   - Docker 冒烟：`developer` 三端登录。
   - k8s all-in-one 在一次性容器上跑通。

**实现时对方案的调整（2026-10-31）：**
- **迁移拆成两个文件。** `90-post-seed/03-platform-access-model-dev-prepare.sql` 只给 dev：`developer` 不存在则建、加入 `E2E_WORKFLOW_SIMULATION`、Platform Showcase 改挂 Public。`04-platform-access-model-switch.sql` 是两种环境共用的切换，开头一行 `SET platform_access_model.initial_sys_admin = 'developer'`；k8s seed all-in-one 里的副本只有这一行改成 `'admin'`。`00-init-all.sh` 的 post-seed 循环本来就包含 03、04，不用改。
- **整个切换是一个 DO 块**：psql 和图形客户端里都是整体提交或整体回滚。成员表上 (group_id, user_id) 的旧唯一键已不存在、且新键 `uk_vg_member_list_source` 已存在的库直接跳过，因为再跑一次会把之后配好的 System Administrators 成员清掉。判断用“旧键已不存在”而不只看新键（review 首轮 Minor）：开着 Hibernate `ddl-auto=update` 的服务可能先把实体声明的新键建出来。
- **成员唯一键在切换脚本里换，不在 schema 里换。** schema 之后运行的种子还写着 `ON CONFLICT (group_id, user_id)`，提前换键会让全新初始化失败。所以 `00-schema/100` 只加列（带默认值 `MEMBER` / `MANUAL`）。
- **团队原有的 AD 映射在切换时清空，并在 NOTICE 里列出**（用户 2026-10-31 确认，D-29）。原映射同步进来的是普通成员；若直接当作 Developer 名单的 AD 组，下一次同步会把只读成员都变成 Developer，与 D-24 “两者都没有的不迁移”矛盾。映射由各环境的切换配置脚本重新设置（PRD §10 第 3 步）。迁入名单的 AD 来源行保留，由 WP4 的规则（名单没有映射就删它的 AD 来源行）在第一次同步时清掉。
- **D-24 里的 TECH_LEAD 只认人工来源**（用户 2026-10-31 决定，D-28；review 首轮 Question 1）。只经 AD 同步的成员关系得到的 TECH_LEAD 按 DEVELOPER 算：旧同步把一个 AD Developer 组同时灌进三个旧组，这个 TECH_LEAD 说明不了谁是 TL。按 D-28 认定的 TL 迁入名单时一律记为人工来源（用户 2026-10-31 决定，D-30；review 第三轮 Question），避免团队成员关系来自 AD 时被 WP4 第一次同步删掉；Developer 行沿用成员关系原来的来源。
- **初始 SysAdmin 账号不存在或不是 ACTIVE 时，切换直接拒绝**，库保持原样。
- **TEAM_LEAD 的引用一并删除**：`bi_rbac_mapping`、FU Access / Audit Access、View Access、Relation Table Access、数据权限、BI 分配中按 id 或 code 指向它的行。被删组的审批人配置也一起删除。
- **解析器**：平台角色的唯一入口是 `UserRoleServiceImpl`（三端都扫描它）。
  - SYSTEM 组只给 SYS_ADMIN / AUDITOR。WP4 之前 `HERMES_BINDINGS` 重建出来的 “Technical Leads → TECH_LEAD” 等绑定不会生效。
  - 账号必须 ACTIVE 且未删除（PRD §5.5 失效优先），这样服务调用凭 `X-User-Id` 算角色时也不例外。
  - 数据库出错直接抛出，不再“当作没有角色”。
  - 按团队取角色的接口留给 WP6 再加，本包没有调用方。
- **Admin 侧**：新增 `UserRoleIdResolver`（平台角色 + UBR），替换 BI、FU 访问、开发权限、个人摘要里的两条 JPQL 合并查询。`UserPermissionService.getUserRoles`（`/users/{id}/roles` 不带 profile 时）仍按虚拟组绑定取角色，原因：
  - 切换后虚拟组只剩两条固定绑定，它给出的 SYS_ADMIN / AUDITOR 与解析器一致；
  - 它的调用方（Portal 权限目录、Relation Table 访问）只关心业务角色；
  - 它所服务的“加入虚拟组得到 BU 角色、再加入 BU 激活”两阶段模型已被 D-03 取代，整体清理归 WP8 / WP10。
- **Portal**：登录和续期时“角色为空就读直接分配”的旧兜底删除（两处）。
- **`init-database.ps1` 只登记 schema 100，不登记 03 / 04。** 它与 Docker 初始化已经漂移：缺 93–99、`01-admin/08`、`90-post-seed/01`。直接加入切换脚本必然失败，因为它不建 Public、FU 无团队。记入范围外。

### WP3 明细

| 项 | 状态 |
|---|---|
| `00-schema/100-platform-access-model.sql`：成员 `list_type` / `source`（带默认值与 CHECK，按 `HERMES_AD_SYNC` 回填 AD）、团队 `tech_lead_ad_group`、`sys_roles.type` 去默认值、`sys_function_units.team_code`；登记 `00-init-all.sh`（1xx 段）与 `init-database.ps1`，k8s schema all-in-one 追加 | 完成 |
| `90-post-seed/03`（dev 准备）+ `04`（切换，单 DO 块、只执行一次）；k8s seed all-in-one 追加 `04`（初始 SysAdmin `admin`） | 完成 |
| 统一解析 `UserRoleServiceImpl`；DW `FunctionUnitWorkspaceAccessService` 与登录 / 续期 / SSO 改用它并删 `LegacyVirtualGroupRoleLookup`；Portal 删两处直接分配兜底；Admin `UserRoleIdResolver` 替换 7 个调用点与两条 JPQL；AD 同步写入的成员标 `source = AD` | 完成 |
| 新增测试：`UserRoleServiceImplTest`（H2 跑真实 SQL，9 例）、`AdminAccessInterceptorRoleResolutionTest`（服务调用按库算角色，4 例）；旧的 mock 属性测试 `UserRoleServiceProperties`（编码“直接分配给角色”）删除 | 完成 |
| 一次性 Postgres 验证：全新 Docker 初始化通过；真实 dev 库副本通过，重跑不改任何东西；D-24 合成数据（直接分配 TL、`sys_user_roles` DEV、经旧 SYSTEM 组两者都有、团队绑定 + AD 来源、只读成员）结果正确；FU 挂两个团队、名称撞 code / 纯中文、初始管理员停用三种情况都被拒绝且库不变；k8s all-in-one（先 Flowable）通过，只有 `admin` 在 System Administrators，没有 `developer`。review 后补测：用切换前的 dev 备份先手工建出新键（模拟 Hibernate），切换照常完整执行并删掉旧键，再跑一次跳过；D-28 合成数据中只经 AD 得到 TECH_LEAD + DEVELOPER 的成员进 Developer 名单（来源 AD），人工加入 Technical Leads 的成员进 TL 名单；D-30：团队成员关系来自 AD、又直接分配了 TECH_LEAD 的成员进 TL 名单，来源 MANUAL、`added_by = system` | 完成 |
| 全量：platform-security 105、admin 1059、DW 1505 全绿；portal 2212 / 8E 同基线 3 类；engine 红只在基线 5 类且逐类次数一致 | 完成 |
| dev 库已执行切换（备份在会话 scratchpad `dev-db-before-wp3.dump`）。Docker：`developer` 在 Admin / DW 是 SYS_ADMIN，Portal 进入 E2E_FINANCE / MANAGER；`admin`、`44027893` 在 Admin 无角色、`/users` 403（dev 开着登录旁路，所以登录本身返回 200）；Auditors 成员只有 AUDITOR；团队 Developer 在 DW 只有 DEVELOPER、Admin 只读接口 `/function-units` 403；`e2e_lina` 失去 Department Managers 后仍能进 Portal；访问矩阵 `verify-admin-access-matrix.mjs` 27/27（临时账号改为 Auditors 成员与临时团队 Developer 名单）。验证后临时账号 wp2_auditor / wp2_dev 与临时团队 WP3_CHECK_TEAM 已删除 | 完成 |

## WP4 技术方案

**现状（2026-10-31 调查）：**
- `LdapSyncService` 有两套成员来源：
  - Hermes 链：`ldap.group-sync.groups / roles / pattern` + `hermes-env` 拼出 Admin / User / Developer 三个 AD 组，按写死的 `HERMES_BINDINGS` 灌进 6 个 SYSTEM 组，每次同步前 `ensureHermesBindings` 重建组、角色（含 TEAM_LEAD）与绑定。
  - 数据驱动链：`syncCustomAdGroupBackedVirtualGroups` 只处理非 SYSTEM、配了 `ad_group` 的组，全部写成普通成员。
- 全量同步先删 Hermes 组的 AD 成员再拉；增量同步拉取成功才删。成员按 `memberOf` / 命中组 / 角色键三级推断落组，没命中落 Hermes Default Users。
- 删除一律 `deleteByGroupIdAndUserId`，一人多行后会连人工来源一起删；`existsByGroupIdAndUserId` 让已有人工行的人进不了 AD 行。
- 默认 AD 组名写在 `application.yml`（`Infodir-Hermes-Default-{env}-{role}`）与 `resolveGroupDefinitions` 的兜底里；dev / SIT / UAT `.env`、dev compose、UAT / preprod ConfigMap 都配了 Hermes 组变量。
- 启动（`ApplicationReadyEvent`）与每 2 小时（ShedLock）调增量；Admin `/ldap-sync/hermes-groups[/incremental]` 手动触发。JIT 登录只更新画像，不碰成员。

**方案：**
1. **同步只读数据库里的映射**，每次同步：
   - 名单清单：SYSTEM、CUSTOM 组的 `ad_group` → MEMBER；DEVELOPER 团队的 `ad_group` → DEVELOPER 名单、`tech_lead_ad_group` → TECH_LEAD 名单。Public 没有名单，跳过。停用的组照常同步（权限由解析器按状态挡）。
   - 每个不同的 AD 组 CN 只拉一次；拉到的用户 upsert 画像，并保留一层 EM / FM 补导入。
   - 逐份名单对账，只动 AD 来源：AD 里有、表里没有的加 AD 行；表里有、AD 里没有的删 AD 行；人工来源不动（§5.5、§5.6）。
   - 拉取抛异常的 AD 组：用到它的名单这次不删（现状增量的规则）。AD 组不存在返回空：照删（C-3 已接受）。
   - AD 组只读到一部分（有成员 DN 读不出属性、或被 `max-entries` 截断，`LdapClient` 返回“不完整”），或组内有成员写画像时抛异常：这个 AD 组算“不完整”，用到它的名单只补不删，避免把没读到、没写进去的人当成离开。没有 employeeID、映射不出来的人本来就进不了名单，不算不完整。
   - 读不出、读不全的 AD 组写进同步记录的说明（`message`），不只在日志里。
   - System Administrators 不会因同步失去最后一名有效成员（D-31，用户 2026-10-31 决定）：先算出要加、要删的行，若删完没有 ACTIVE 且未删除的成员，就不删它的 AD 行，同步记录写明。
   - 同步的成员增减逐人写审计（D-32）：与 WP5 的人工维护审计一起做，写进 Admin 操作审计。
   - 组里不属于本组类型的 AD 来源行（例如团队里遗留的普通成员行）一并删除。
   - 名单没有 AD 映射：删除它的全部 AD 来源行（D-29）。
   - 全量与增量合成同一次对账（不再“先删再拉”），保留两个入口、审计类型与 Admin 接口，避免改前端与历史记录。
2. **删除**：`HERMES_BINDINGS` 与 `ensureHermesBindings` / `ensureRole` / `ensureVirtualGroup` / `ensureVirtualGroupRole`、`resolveGroupDefinitions` / `resolveHermesEnvironment`、Hermes 成员清理与按 `memberOf` 推断落组的整套方法、`syncCustomAdGroupBackedVirtualGroups`（并入新对账）；`LdapProperties.hermesEnv`、`groupSync.env / groups / roles / pattern`（保留 `searchBaseDn`、`batchSize`）；`LdapConstants` 里的 Hermes 组 / 角色常量。同步不再创建任何组、角色、绑定（§5.2 第 5 步，AC-11）。
3. **配置清理**：`application.yml` 的 `hermes-env` 与 `group-sync.env / groups / roles / pattern`；dev / SIT / UAT `.env`、dev `.env.example`、dev compose、UAT / preprod ConfigMap 的对应变量。
4. **Repository**：成员表按 `(group_id, list_type, source)` 查询；按行删除，不再按 `(group, user)` 整体删。
5. **不做**：Admin 页面配置两个 AD 组、成员页显示来源与“撤销人工成员”、人工加入时允许与 AD 行并存（WP5）；Portal 审批入组（`MemberManagementService` 的 `existsByGroupIdAndUserId`，WP5 / WP8）。
6. **验证**：
   - 重写 `LdapSyncServiceTest`：加 / 删只动 AD 来源、人工保留、拉取失败不删、无映射清 AD 行、同一 AD 组映射多份名单只拉一次、团队两份名单分别落表、SYSTEM 组按映射同步、不建组 / 角色 / 绑定。
   - admin-center 全量。
   - Docker：重启 Admin，确认启动同步不再重建旧 SYSTEM 组与 TEAM_LEAD（AC-11）；手动删掉 dev 里被旧代码重建的组、角色与绑定（WP3 切换后才出现的残留），再重启确认不会回来。
   - **不做**：带组的本地 LDAP 实测。用户 2026-10-31 指示：需要特别配置的话先不验证。对账规则只由单测覆盖，结果中标“未验证（真实 AD）”。

### WP4 明细

| 项 | 状态 |
|---|---|
| `LdapSyncService` 重写：按虚拟组上的映射逐名单对账（只动 AD 来源，人工行不动），每个 AD 组只拉一次；读不出的 AD 组不动它的名单；有成员写画像抛异常的 AD 组只补不删；没有映射的名单与不属于本组类型的 AD 行删除（D-29）；保留一层 EM / FM 补导入；全量与增量走同一次对账，审计类型与 Admin 接口不变 | 完成 |
| 删除 `HERMES_BINDINGS` 及建组 / 建角色 / 建绑定、按 Hermes 角色拼组名、按 `memberOf` 推断落组的整套代码；`LdapProperties.hermesEnv`、`groupSync.env / groups / roles / pattern`；`LdapConstants` 的 Hermes 组 / 角色常量 | 完成 |
| 配置：`application.yml`、dev compose、dev `.env.example`、SIT / UAT `.env`、UAT / preprod ConfigMap 去掉 Hermes 组映射（UAT ConfigMap 原有的重复键一并去掉）；x-ray 部署文档同步；本地未入库的 `deploy/environments/dev/.env` 未动（compose 不再传这些变量） | 完成 |
| `VirtualGroupMemberRepository.findByGroupIdAndSource` | 完成 |
| `LdapSyncServiceTest` 重写（成员表用列表模拟、断言最终行，按 List 断言以发现重复写入）；admin-center 全量 1056 / 0F0E | 完成 |
| review 首轮修复：`LdapClient.fetchUsersInGroup` 返回是否读全，读不全的组只补不删；读不出 / 读不全的组写进同步记录说明；Public 没有名单（`VirtualGroup.PUBLIC_TEAM_ID`，platform-security 共享常量）；`ApWorkspaceResolver.isMember` 改用 exists（一人多行时原来会 500）；`VirtualGroupTargetResolver` 计数与列表按人去重；删掉无人使用的累加器标记与审计查询，改正过时注释与示例组名；测试补“人工行旁补 AD 行”“读不全只补不删”“写画像失败时两个组都只补不删”“Public 不拉取”与同步说明，测试数据换成中性值（LDAP 相关 50 例全绿） | 完成 |
| Docker：重建 Admin 后启动同步只读库里的映射（dev AD 不可达，日志逐名单记 “left … unchanged”），没有新建任何东西；随后清掉旧代码在 WP3 切换后重建的 4 个 SYSTEM 组、TEAM_LEAD 角色与两个 SYSTEM 组上的 AD 映射（dev 专用一次性 SQL），再重启：启动同步 0.5 秒结束、不访问 AD、没有任何组 / 角色 / 绑定回来（AC-11、AC-19）；`developer` 登录 Admin 仍是 SYS_ADMIN | 完成 |
| review 首轮 Question：D-31 已实现（先规划再执行，`VirtualGroup.isSystemAdministrators()`），测试补 3 例（AD 成员保留、仍有有效人工管理员时照删、停用账号不算有效成员）；D-32 归 WP5 | 完成 |
| review 第二轮 Major：超过 500 人的 AD 组分段读取丢最后一段且报“读全”（HEAD 已有，但本包让“读全”决定删不删）→ `LdapClient.readMemberRanges` 改为每次请求 `member;range=<起点>-*`，按返回的属性名（不分大小写，分段或普通 `member`）读到以 `-*` 结尾为止；第一段之后出错直接抛出，该组按读不出处理；`LdapClientMemberRangeTest` 6 例（分段 1800 人、普通 member、大小写、中途失败、不认分段、空组） | 完成 |
| review 第二轮 Minor：AD 组读到 0 人（不存在或为空）写进同步记录说明；`LdapClientIntegrationTest` 补“成员 DN 读不出”“max-entries 截断”两种读不全；Public 的 id 只定义一处（DW `DevGroupConstants`、`AiAutomationFlowCatalogReader`、Admin `ServiceTaskProperties` 默认值改为引用 `VirtualGroup.PUBLIC_TEAM_ID`） | 完成 |
| review 第三轮 PASS（无 Blocker / Major）；两条 Minor 已改：`LdapClient.fetchGroupMemberDns` 过时注释改为指向 `readMemberRanges`；`application.yml` 删掉写死的 `service-task.managed.public-group-id`，Automation 用 `ServiceTaskProperties` 默认值（`VirtualGroup.PUBLIC_TEAM_ID`）。这两处随 WP5 review 一并重审 | 完成 |
| 真实 AD / 本地 LDAP 实测 | **未验证**：用户 2026-10-31 指示需要特别配置时先不做；对账规则只由单测覆盖 |

## WP5 技术方案

**现状（2026-10-31 调查）：**
- `VirtualGroupManagerComponent`：
  - 新建可以手填 code；
  - 修改可以改名称、类型、AD 组（SYSTEM 只能改 AD 组）；
  - 删除只拦 SYSTEM；启停没有任何保护；
  - 加成员：只要这个人在组里有任意一行就报 `MEMBER_EXISTS`，没有名单和来源的概念；
  - 移除成员：按单行查询，一人多行时报错；“最后一名管理员”校验比较的是 `SYS_ADMINS`，从不触发；
  - 成员列表按行返回，一人多行时重复。
- Bind Roles：`VirtualGroupRoleController` 的 POST / DELETE 写 `sys_virtual_group_roles`；前端是 `VirtualGroupRolesDialog`。Portal 只调 GET。
- Role 页：`SYSTEM_ROLE_LIST_CODES`（前端 `roleList.ts`、后端 `RoleColumnSpec`）是 6 个 code，含 TEAM_LEAD、MANAGER；删除角色只查 `sys_user_roles` 的人数；新建角色可以选 BU_UNBOUNDED。
- `RoleMemberManagerComponent` 允许把平台角色直接写进 `sys_user_roles`；删除用户时的“最后一名管理员”保护读 `sys_user_roles`，恒为假。
- 审计有两套：`sys_member_change_logs` 只由 Portal 审批流写，没有界面；Admin 操作审计 `admin_audit_logs` 由 `AdminAuditAspect` 自动记录各 controller 的写操作（虚拟组加 / 减成员记为 CREATE / DELETE），Admin 审计页面展示。AD 同步的增删两边都不记。
- `/users/{id}/virtual-groups` 按成员行返回。

**方案：**
1. **审计（D-32）**：用 Admin 操作审计（`admin_audit_logs`，Admin 页面可见），不新增 schema。人工加 / 撤成员：切面已记录，补上目标用户、名单、来源；AD 同步的每条增删经 `SecurityAuditComponent.recordAudit` 记一条（CREATE / DELETE、虚拟组、操作人 `LDAP_SYNC_JOB`），细节里写用户、名单、来源 AD。
2. **成员接口（按名单、按来源）**：
   - 列表：每个“人 + 名单”一项，带 `sources`（AD / 人工 / 两者）；团队分 Developer、Technical Lead 两份。
   - 加成员：只加人工行；团队必须指定名单，SYSTEM / CUSTOM 只有成员；Public 拒绝。只有这份名单里已有人工行时才报 `MEMBER_EXISTS`，已是 AD 成员的人可以再加为人工成员（§5.5）。Portal 审批调用不带名单时按普通成员处理。
   - 撤销人工成员：只删人工行，返回剩余来源；没有人工行就报错。System Administrators 撤销后若没有有效成员（ACTIVE、未删除，AD 来源也算）就拒绝（AC-12，修 `SYS_ADMINS` 写错的问题）。
3. **组的增改删与启停**：
   - 新建团队：code 一律由名称生成，不收手填 code；名称不含字母或数字（code 为空）就拒绝；撞 code 拒绝（AC-39）。CUSTOM 保持现状。
   - 修改：任何组都不能改类型；SYSTEM 与团队不能改名称、code；团队可以设 Developer、Technical Lead 两个 AD 组；Public 不能设 AD 组。
   - 停用：SYSTEM 与 Public 拒绝。
   - 删除：SYSTEM 与 Public 拒绝；团队名下还有 FU 时拒绝（见下面的待确认）。
4. **去掉 Bind Roles**：删除 POST / DELETE `/virtual-groups/{id}/role` 与 `VirtualGroupRoleService` 的写方法；GET 保留，SYSTEM 组只读显示固定角色。
5. **Role**：
   - System 列表只剩 4 个平台角色（前后端两处常量）。
   - 新建角色的类型里没有 BU_UNBOUNDED，不传类型拒绝（AC-24 的 Admin 部分）。
   - 删除角色：被 UBR、BU 角色绑定、FU / View / Relation Table 访问配置、已发布流程分派引用时拒绝，并列出引用位置（§4.3）。流程分派的引用从 Admin FU 目录里已发布的流程内容判断，实现时确认格式。
   - 平台角色不能经 `/roles/{id}/members` 直接分配。
6. **其他**：删除用户时的“最后一名管理员”保护改为按 System Administrators 的有效成员判断；`/users/{id}/virtual-groups` 每个组一项，带名单与来源。
7. **前端（Admin）**：
   - 虚拟组列表：SYSTEM 页只读显示固定角色，不能删除、停用、改名；Developer 页的 Public 不能停用、删除、配 AD 组。
   - 表单：新建时填名称（团队不显示 code 输入，建好后只读显示）、类型、描述、AD 组；团队另有 Technical Lead AD 组。
   - 成员弹窗：团队分两页；列出来源；“撤销人工成员”只对有人工来源的行显示；加成员时选名单；System Administrators 加、撤都要二次确认。
   - 去掉 Bind Roles 按钮与弹窗；Role 页 System 列表只剩 4 个。
   - 三语 i18n；截图验证。
8. **不做**：DW 侧（WP6）、导入带团队（WP7）、Portal 准入（WP8）、TL 进 Admin（WP9）。
9. **验证**：后端单测（各项规则正反例）、admin 全量、Admin 前端测试与 vue-tsc、Docker + 截图（虚拟组三页、成员弹窗两份名单与来源、撤销人工成员、Role 页）。

**已确认（D-33，2026-10-31）：** 删除团队时 Admin 只看自己的 FU 目录（`sys_function_units` 里所属团队是它的 FU），不读 `dw_*`、不调 DW；DW 里挂在已删除团队上的 FU 在 WP6 按“团队不可用”处理（SYS_ADMIN 可见、标注、迁走）。

### WP5 明细

| 项 | 状态 |
|---|---|
| 虚拟组规则（`VirtualGroupManagerComponent`）：团队 code 一律由名称生成，手填拒绝，名称生成不出 code 拒绝；类型不能改；SYSTEM 只能改 AD 组；团队不能改名，可改描述与两个 AD 组；Technical Lead AD 组只有团队有；Public 不能配 AD 组、不能加成员、不能停用或删除；SYSTEM 组不能停用；团队在 Admin FU 目录里还有 FU 时不能删除（D-33）。错误信息走 i18n 三语 | 完成 |
| 成员：每个“人 + 名单”一项并带来源；加成员只加人工行（团队须选名单，SYSTEM / CUSTOM 只有成员），只有已有人工行才报重复；撤销只删这份名单的人工行（`DELETE …/members/{userId}?listType=`），AD 来源保留；System Administrators 撤销后没有有效成员时拒绝（AC-12，修正原来比较 `SYS_ADMINS` 永不触发）；列表与 `VirtualGroupHelper` 成员数按人去重 | 完成 |
| 成员维护权限：SYSTEM 组与团队名单的成员只有 SYS_ADMIN 能加、撤。浏览器调用本来只放 SYS_ADMIN；Portal 审批走的是服务调用（`PermissionApprovalComponent` → `VirtualGroupAccessComponent` → Admin `POST /virtual-groups/{id}/members`），按 `X-User-Id` 的操作人判断，审批人只能动 CUSTOM 组（`VirtualGroupManagerComponent.assertMayMaintainMembers`）；审批人只能配置在 CUSTOM 组上（`ApproverService`）。无调用方的 `MemberManagementService` 审批入组同样只收 CUSTOM；退出与清退只删人工行，没有人工行时报错 | 完成 |
| 去掉 Bind Roles：后端删 `POST / DELETE /virtual-groups/{id}/role` 与 `VirtualGroupRoleService` 写方法（GET 保留，SYSTEM 组的固定角色在列表只读显示）；前端删 `VirtualGroupRolesDialog`、`useVirtualGroupRoles`、对应 API 与 13 个 i18n key | 完成 |
| Role：System 列表只剩 4 个平台角色（前后端两处常量）；新建只收 BU_BOUNDED；删除角色时按 UBR、BU 绑定、FU / FU 审计 / Relation Table 访问、BI 分配与 RBAC 映射、已发布流程分派（`roleId` 与 `roleIds` 逗号列表的每一项，含旧导入存的 Base64 BPMN）、已发布主表视图的访问规则逐项查引用，有引用就拒绝并列出位置（`RoleReferenceFinder`）；平台角色不能经 `/roles/{id}/members` 直接分配 | 完成 |
| 删除用户时的“最后一名管理员”保护改为按 System Administrators 的有效成员判断；删掉恒为假的 `UserRepository.isUserAdmin` / `countActiveAdmins` | 完成 |
| `/users/{id}/virtual-groups` 每个组一项，带所在名单、来源与最早加入时间；按前端上下文过滤改为按组类型（Portal 看 CUSTOM、Admin 看 SYSTEM、DW 看团队） | 完成 |
| 审计（D-32）：人工加 / 撤成员的切面记录补上目标用户、名单、来源；AD 同步的每条增删经 `SecurityAuditComponent` 写一条 Admin 操作审计（操作人 `LDAP_SYNC_JOB`，细节为用户、名单、来源 AD），与成员变更同一事务；D-31 保留下来的不写 | 完成 |
| `VirtualGroupInfo` 增加 `publicTeam`、`systemAdministrators`，前端据此判断，不在前端再写一份 Public 的 id | 完成 |
| Admin 前端：列表去掉 Bind Roles，Public 不显示成员 / 停用 / 删除，操作列按 4 个按钮收窄；表单新建团队不显示 code 输入并提示“名称与 code 建好后不能改”，编辑时类型只读、SYSTEM 与团队名称只读，团队有 Developer / Technical Lead 两个 AD 组，Public 显示“没有名单与 AD 组”；成员弹窗团队分两份名单、显示来源、只对人工来源显示“撤销人工成员”、加成员时选名单、System Administrators 加与撤都二次确认；i18n 三语 | 完成 |
| 测试：`VirtualGroupRulesTest` 17 例、`RoleRulesTest` 7 例、`LdapSyncServiceTest` 补 D-32 两例（20 例）、成员相关属性测试改为按人工行断言并补“审批不能入 SYSTEM / 团队”；admin-center `mvn clean test` 1091 / 0F / 0E / 2 跳过。前端新增 `virtualGroupMembers.test.ts` 4 例，admin 全量 22 文件 95 例全绿；vue-tsc 本次改动文件 0 错误（其余 134 个在 shared 目录解析与 audit / profile 等未改文件，既有） | 完成 |
| review 首轮 FAIL（2 Major、6 Minor、2 Question）处置：Major 1 审批入组的 CUSTOM 限制原先加在无调用方的路径上 → 改在 Admin 成员接口按操作人判断，并限制审批人只能配在 CUSTOM 组；Major 2 删除角色漏查 `roleIds` 与视图访问规则 → `RoleReferenceFinder` 先粗筛再精确解析（含 Base64 BPMN）；Minor：SYSTEM 组改名称 / 描述 / TL AD 组改为明确拒绝（`SYSTEM_GROUP_AD_GROUP_ONLY`）、code 超过 50 字符报 `VIRTUAL_GROUP_CODE_TOO_LONG`、退出 / 清退没有人工行时报错、人工维护审计的名单按组解析（非团队组记 MEMBER）、`/users/{id}/virtual-groups` 补 `sources`、删掉因去掉 Bind Roles 而无人调用的 `validateBusinessRole*` 与属性测试、`UserRepository` 注释缩进；Q1 用户 2026-10-31 接受（上线清单）；Q2 用户 2026-10-31 决定不拦停用 / 锁定（残余风险） | 完成 |
| 第一轮修复的证据：`VirtualGroupRulesTest` 20 例、`RoleRulesTest` 10 例、新增 `ApproverRulesTest` 2 例；admin-center 全量 1098 / 0F / 0E / 2 跳过；Docker 第二轮接口实测 12 项全过（`frontend/scripts/_wp5-review2-checks.mjs`：服务调用以审批人身份往 System Administrators 加人被拒且不落库、往 CUSTOM 组可加、SYS_ADMIN 经服务调用仍可加、SYSTEM 组不能配审批人、SYSTEM 组改描述被拒只改 AD 组可以、`roleIds` 第二位与视图访问规则引用的角色不能删）；新增截图 `2026-10-31_wp5-form-edit-system-group.png` | 完成 |
| review 第二轮 PASS（无 Blocker / Major）；三条 Minor 已改：Admin 退出 / 清退接口（`MemberManagementService.exitVirtualGroup` / `removeVirtualGroupMember`）只收 CUSTOM 组（`EXIT_CUSTOM_GROUP_ONLY`），SYSTEM 组与团队成员只在虚拟组页面维护（那里有最后一名管理员保护和操作审计），错误信息改走 i18n；`RoleReferenceFinder` 遇到坏内容时报错带上 FU 名称与内容 id；本表补第二轮证据。这些改动随 WP6 review 一并重审 | 完成 |
| Docker：重建 Admin 前后端；接口实测 26 项全过（团队 code、名单必选、人工重复、成员按人与名单、成员数按人、AD 成员不能撤、团队名与类型不可改、Public 四项保护、SYSTEM 不能停用、最后一名管理员、Bind Roles 写入不落库、撤销保留 AD 来源、取消确认不写库、人工加 / 撤审计、无 FU 团队可删）；手动触发同步删除未映射名单上的 AD 行并写出 `LDAP_SYNC_JOB` 审计，Admin 审计页可见；截图 14 张在 `frontend/admin-center/verification-screenshots/2026-10-31_wp5-*.png`；测试团队与探针行已清理 | 完成 |

## WP6 技术方案

**现状（2026-10-31 调查）：**
- `FunctionUnitWorkspaceAccessService` 按“全局角色 × 组成员”判断：只要在任一团队是 TECH_LEAD，在所有自己所在的组里都按 TL 处理；成员行不分名单（`findVirtualGroupIdsByUserId` 取全部组，含 CUSTOM、SYSTEM、已停用组）。SYS_ADMIN 只能写 Public，其它团队只读。TEAM_LEAD 仍是一档能力。
- 新建、导入、改团队（`PUT /dev-groups`）都接受多个组 id；改团队只校验对源 FU 的权限，写入的组 id 不受限（A-31）；克隆复制源 FU 的全部团队（A-10）。WP3 已在库里加 `uq_dw_fu_dev_group_fu`，传两个会 500。
- 已停用团队对成员仍出现在切换器（灰显）；FU 挂在已删除团队上时谁都看不到（只有 SYS_ADMIN 选“全部”能看到，没有标注）。
- DW 登录、SSO 兑换、续期都不校验准入，只靠前端调 `/workspace-access`；纯 Auditor 被前端路由挡在 DW 外。
- 导出包（含 DW 部署 ZIP）不带团队；导入新 FU 按“当前所选团队”落位。
- Automation（Admin `ApWorkspaceResolver`）：CUSTOM 组也算 workspace；任何一行成员都能进；可写按全局能力角色；Auditor 进不了团队 workspace。
- 前端按全局角色 + 成员组判断按钮（`permission.ts`），团队选择器多选。

**方案：**
1. **逐团队能力（单一判定点 `FunctionUnitWorkspaceAccessService`）**：
   - 用户在团队 T 的身份 = T 的 Technical Lead / Developer 名单（`list_type`），T 须是 ACTIVE 的 DEVELOPER 组，用户须 ACTIVE、未删除。同一团队两份都有按 TL（§4.4）。新增 `TeamMembershipDao`（替代 `VirtualGroupMembershipDao` 的取组方法）一次查出“团队 id → TL/DEV”。
   - FU 所在 workspace = `dw_function_unit_dev_groups` 唯一的一行；workspace 状态分 Public / 有效团队 / 已停用 / 已删除。
   - 动作：VIEW（含导出）；MODIFY（编辑设计、发布、部署、回滚）；MANAGE（删除 / 彻底删除、恢复归档、导入为已有 FU 的新版本、移出）。
   - 规则：SYS_ADMIN 一切动作、所有 workspace（含 Public、已停用、已删除）；AUDITOR 只有 VIEW（全部 workspace）；Public 其他人只 VIEW；有效团队：TL 全部，Developer VIEW + MODIFY；已停用 / 已删除团队的 FU 只有 SYS_ADMIN 与 AUDITOR 可见。
   - 落位（新建、导入新 FU、克隆目标、移入）：目标必须正好一个，且是有效团队（调用者是 TL）或 Public（只有 SYS_ADMIN）。0 个、多个、非团队组、已停用、不存在都拒绝并给出原因；不再回退“当前所选团队”。
   - 克隆：源只要能看到（D-35，2026-11-01 用户定：PRD §6.2“克隆（目标为本 WS）”只约束目标；与“能导出就能当旧包导入”一致），目标按落位规则。（原方案“源须 MANAGE”已作废。）
   - 移动（`PUT /dev-groups`）：只收一个目标；源 MANAGE + 目标落位；原地更新那一行。
2. **列表与选择器**：成员看到自己所在名单的有效团队 + Public；SYS_ADMIN、AUDITOR 看全部团队（已停用的标注），“全部”下还能看到挂在已删除团队上的 FU。`my-dev-groups` 每个团队带 `techLead`、`developer`、`canCreate`，另给 `canWritePublic`；FU 响应加 `canManage`、`workspaceId`、`workspaceName`、`workspaceStatus`（PUBLIC / ACTIVE / INACTIVE / MISSING）。
3. **准入**：能进 DW = SYS_ADMIN、AUDITOR，或至少在一个有效团队名单上（统一解析给出的 TECH_LEAD / DEVELOPER 就是这个意思）。登录、SSO 兑换、续期都校验：登录与兑换拒绝并给出原因，续期回 401。前端路由与菜单按同一组角色判断，只为“无角色团队成员”存在的 `/workspace-access` 一并删除；去掉“任一 CUSTOM / DEVELOPER 组成员即可只读进入”的基线（`DeveloperPermissionChecker` 补 `function_unit:view` 的分支一并删除）。
4. **导出与导入带团队**：`ExportManifest` 加 `team { code, name }`（取 FU 当前所在 workspace；Public 为 `DEV_TEAM_PUBLIC`）；DW 部署走同一导出。DW 导入新 FU：包里有团队 → 本环境同 code 的团队，不存在提示“请先建团队 X”、已停用提示先启用，再按落位规则校验调用者；旧包没有团队 → 必须带 `targetGroupId`，否则返回 `IMPORT_WORKSPACE_REQUIRED`，前端据此让用户选择后重试。导入为已有 FU 的新版本：要求 MANAGE，归属不变。
5. **删除 TEAM_LEAD**：DW 前后端、Admin `DeveloperPermissionService`、`ApWorkspaceResolver` 及测试；`@PreAuthorize` 只剩 SYS_ADMIN / TECH_LEAD / DEVELOPER 的粗门禁，细判在第 1 条。种子 / init-scripts 里的 TEAM_LEAD 由 WP10 收尾（切换脚本已删角色）。
6. **Automation（D-20）**：只认 DEVELOPER 团队；进入 = SYS_ADMIN、AUDITOR（只读），或该团队 TL / Developer 名单；可写 = SYS_ADMIN 或该团队名单成员；可选列表按调用者逐团队算 `canWrite`，非 SYS_ADMIN / AUDITOR 只列自己的团队。
7. **前端（DW）**：去掉纯 Auditor 拦截，Auditor 全只读；`permission.ts` 改用后端给的逐团队 / 逐 FU 标记，删除全局角色推断与 TEAM_LEAD；新建、设置、移动改单选；克隆弹窗选目标团队；导入遇 `IMPORT_WORKSPACE_REQUIRED` 弹出选择；列表标注已停用 / 已删除团队；切换器成员只列有效团队。
8. **清理（范围外表登记的 WP6 项）**：DW 无调用方的旧角色方法（`DatabasePermissionEvaluator.hasRole`、`SecurityComponentImpl`、`RoleRepository.findRolesBy*`、`PermissionRepository`）先 grep 确认无调用再删。
9. **不做**：Admin 导入带团队（WP7）；Portal（WP8）；TL 进 Admin（WP9）；init-scripts 种子里的 TEAM_LEAD（WP10）。
10. **验证**：`FunctionUnitWorkspaceAccessService` 单测覆盖 AC-01/02/03/04/05/21/30 的规则表（多团队、两份名单、停用、删除、Public、Auditor）；DW 全量；Admin 全量（`ApWorkspaceResolver`、`DeveloperPermissionService`）；DW 前端 vitest + vue-tsc；Docker：临时团队与用户（TL / DEV / Auditor / 只在 CUSTOM 的用户）跑接口矩阵与截图（列表、新建、移动、克隆、导入、停用团队标注、Automation）。

### WP6 明细

| 项 | 状态 |
|---|---|
| `FunctionUnitWorkspaceAccessService` 重写：按用户在该团队的名单判断（`VirtualGroupMembershipDao.findTeamMemberships` 只读 DEVELOPER 组的两份名单，只认 ACTIVE 团队）；FU 的 workspace 分 Public / 有效 / 已停用 / 已删除；SYS_ADMIN 全部、AUDITOR 只读、TL 全部、Developer 查看 + 修改；落位（新建、克隆目标、旧包导入、移入）必须正好一个可新建的目标（`resolveTargetWorkspace`），包里的团队按 code 对上本环境团队（`resolvePackageWorkspace`，缺失 / 停用给出团队名）；FU 响应带 `canManage`、`workspaceId/Name/Status`；`my-dev-groups` 每个团队带 `techLead / developer / canCreate`，另给 `canWritePublic` | 完成 |
| 新建、克隆、移动、导入只落一个 workspace：克隆不再复制源 FU 的团队、源只要能看到（D-35）；移动原地更新那一行；导入新 FU 按包里的团队，旧包须带 `targetGroupId`（否则 `IMPORT_WORKSPACE_REQUIRED`），与包里团队冲突拒绝；导入为已有 FU 的新版本需 TL（`IMPORT_VERSION`），归属不变；JPA 实体补单列唯一约束注解 | 完成 |
| 导出包（含 DW 部署 ZIP）带 `team { code, name }`，取 FU 当前所在 workspace；团队已删除时不带（按旧包处理） | 完成 |
| DW 准入：登录 403、SSO 兑换 403、续期 401（`DeveloperWorkstationEntitlement`：SYS_ADMIN / AUDITOR / TECH_LEAD / DEVELOPER）；`DeveloperPermissionChecker` 去掉团队成员只读基线；删除 `/workspace-access` | 完成 |
| 删除 TEAM_LEAD（DW 前后端）；删除无调用方的旧角色代码 `DatabasePermissionEvaluator`、`SecurityComponent(Impl)`、DW `RoleRepository`、`PermissionRepository` 及只测它们的 6 份测试（`@EnableMethodSecurity` 保留） | 完成 |
| DW 前端：`permission.ts` 只读后端给的逐 FU / 逐团队标记；卡片、图标、文件夹弹层的克隆 / 恢复 / 删除看 `canManage`；新建与设置的团队改单选，设置里只有 `canManage` 才能移动；克隆弹窗选目标团队；导入遇旧包弹出团队选择；SYS_ADMIN / AUDITOR 可在切换器里打开已停用团队；卡片标注“团队已停用 / 所属团队已删除”；去掉纯 Auditor 拦截；SSO 兑换被拒时就地提示，不再来回跳登录；三语 i18n | 完成 |
| Admin：Automation workspace（D-20，`ApWorkspaceResolver`）只认 DEVELOPER 团队；进入 = SYS_ADMIN、AUDITOR（只读 Viewer 会话）或该团队两份名单之一；可写 = SYS_ADMIN 或该团队名单上的人（不再按全局能力角色）；可选列表逐团队算可写。`DeveloperPermissionService` 去掉 TEAM_LEAD，Developer 权限集合直接列出（内容不变）。`ApWorkspaceResolverTest` 12 例（Auditor 只读进入、别队角色不开本队、CUSTOM 组不是 workspace） | 完成 |
| 文档与脚本：`docs/guides/function-unit-development-guide.md` 的新建 / 发布 / 克隆流程与角色表改为当前门禁（去掉 TEAM_LEAD、补 SYS_ADMIN、写明团队内按名单判断、克隆带目标团队）；`frontend/scripts/verify-dw-read-only-navigation.mjs --create-options` 的期望值改读后端的 `canCreate` / `canWritePublic`（原按全局角色 + TEAM_LEAD 推算），developer 实跑通过 | 完成 |
| review 第一轮 FAIL（1 Blocker、1 Major、6 Minor、1 Question）：**子资源越权读写**（HEAD 既有，鉴权只看 URL 里的 FU id）→ 新增 `WorkspaceRouteOwnership`：路径里的表单、表、动作、决策、邮件连接 / 监控 / 模板、主表视图、版本、绑定、AI 会话、部署任务一律从库里查归属，不属于路径里的 FU / 表单按不存在（404），没有 FU id 的路由（`/actions/{id}`、`/api/forms/{formId}/**`、`/ai-generation/sessions/{id}`、`/function-units/deployments/{id}`）按查出的归属鉴权；拦截器补登记 `/api/forms/**`、`/actions/**`；取代 `FormBindingWorkspaceAccessService`；路由守卫测试 `WorkspaceRouteCoverageTest` 反射扫描全部 handler，路径变量必须是 FU id、已覆盖的子资源或带理由的豁免。请求体 / query 里的 id（盘点 17 处：AI 文档、关联表单数据、绑定表、表关系、表单绑定表、邮件监控目标、动作弹窗表单 / 邮件连接与模板、AI 会话、主表视图详情表单、lookup 视图、`/actions/batch`、`/form-stage-bindings` 等）用同一归属查询 `requireInUnit` 校验。Minor：SSO 兑换先判准入再写登录记录、账号状态与密码登录同一规则；团队名单查询（DW DAO、Admin `isActiveUserOnLists`）只认 ACTIVE 未删除账号与有效角色，DAO 改为 H2 真 SQL 测试；Admin 不可达且无缓存时 503 `PERMISSIONS_UNAVAILABLE`（不再当无权限）；13 个克隆脚本补 `targetGroupId`（6 个导入脚本上传的是带团队的原样导出包，无需改）；x-ray 文档两处；删除无调用方的 `memberGroupIds` 前后端与 `isPublicGroupSelected`；成员级缩进；英文 `selectTeam` 单数。Question → D-35（克隆只要求能看到源 FU；`CLONE` 动作删除，克隆按钮改看“有地方可落”）。补截图时另发现两处 UI 缺口并修复：图标视图磁贴常驻显示团队停用 / 删除标注（原先只在悬停气泡里）；停用团队进 Automation 页说明“团队已停用”（原先误报“不是成员”） | 完成 |
| review 第一轮修复的验证：DW `mvn -B clean test` 1513 / 0F / 0E / 8 skipped（新增归属解析 10 例、路由守卫 2 例、SSO 兑换 3 例、名单 H2 真 SQL 6 例、请求体 id 约 29 例）；Admin 全量 1130 / 0F；DW 前端 vitest 191 文件全绿，vue-tsc 160 = 基线；Docker（DW 前后端重建）`_wp6-dw-checks.mjs` 52 条全过（新增 7 条越权负例：经自己 FU 路径写 Public 表单 404、读别队表单 404、按 id / batch 读别队动作 403、写别队表单 lookup 403、写别队 AI 文档 403，以及 D-35 克隆两条）；`frontend/scripts/_wp6-review-shots.mjs` 补截图 5 张 `frontend/developer-workstation/verification-screenshots/2026-11-01_wp6r-*.png`（旧包选 workspace、SSO 被拒、Auditor 设计器只读、图标视图停用标注、停用团队 Automation） | 完成，待重审 |
| review 第二轮 FAIL（1 Blocker、3 Major、4 Minor、1 Question）：**匿名请求**（HEAD 既有：DW 安全链 `permitAll`、拦截器对匿名放行，无注解接口完全不鉴权）→ `SecurityConfig` 公开清单（`/auth/**`、文档、健康检查、`/error`，以及平台文件存储 `/upload/**`——运行时 Portal / 引擎 / Automation 匿名使用，既有，记入范围外）之外一律要求登录，未登录 401（前端据此续期）。**AI 路由以 query 冒充单元** → 路径里有单元以路径为准，query 只在路径没有单元时用，格式错误 400；`studio-thread/{id}` 也从路径取。**D-35 被拦截器挡住** → 克隆、流程模拟、查找探针这几个只读的 POST 按 VIEW。**`/internal/**` 匿名可读邮件模板** → 该接口无调用方（引擎发邮件读已发布快照），删除接口与测试。Minor：守卫测试补“`id` 只在单元路由上算单元、其它 `id` 写明理由”“拦截器确实从路径取到单元且已登记”两项（`DeveloperWebMvcConfig.WORKSPACE_PATHS` 共用）；拦截器单测 5 例；503 文案走 i18n；本表与技术方案按 D-35 改正。Question（存量配置引用失效绑定时保存报 404）：按“坏数据报出来、不静默保存”处理，dev 库此类数据 0 条。另补 Admin `isActiveUserOnLists` 的 H2 真 SQL 测试（SQL 提为常量 `ACTIVE_USER_ON_LISTS_SQL`）；WP7 review Minor 1 一并改：DW 部署失败提示读取 Admin 403 的 `error.message` | 完成 |
| review 第三轮 FAIL（1 Major、3 Minor、1 Question）：**SSE 收尾被安全链拦下**（第二轮的 `anyRequest().authenticated()` 引入：SSE 结束时的 ASYNC 再分派不经 JWT 过滤器、没有安全上下文，被拒时响应已提交，流被截断，AI 对话每轮结束都报错）→ `SecurityConfig` 最前面放行 `DispatcherType.ASYNC`（它延续的是已在 REQUEST 分派上通过鉴权的请求，外部无法直接发起）；新增 `SecurityConfigDispatchTest`，用真实 `SecurityConfig` 与真实 `JwtAuthenticationFilter` 测 SSE 再分派、匿名 401、文件存储与文档公开（去掉放行即红，报错与 Docker 相同：“response is already committed”）。Minor：文档放行对齐 springdoc 实际路径 `/api-docs/**`、`/swagger-ui.html`、`/swagger-ui/**`（与 Admin 相同）；范围外表里 `/upload/**` 改为准确的“匿名可读”两个接口；`DeploymentComponentImpl.extractAdminError` 改为包内静态方法，补 `DeploymentAdminErrorTest` 4 例（团队 403 的 `error.message`、导入 400 的顶层 `message`、拦截器 403、非 JSON 原样），部署第 3 步出错也走它（WP7 新增的团队 403 会落在这一步）。Question（查找探针按 VIEW 会让只读的人读到关联表实时行）→ 恢复 HEAD 的 MODIFY，克隆与流程模拟仍按 VIEW。验证：DW `mvn -B clean test` 1522 / 0F / 0E / 8 skipped；Docker（DW 重建）`ai-generation/events` 流挂到服务端 300s 超时，直连与经 edge 的 curl 都 exit 0（修前 18），期间日志 “already committed” 0 次；`_wp6-dw-checks.mjs` 58 项全过 | 完成 |
| review 第四轮 PASS（0 Blocker、0 Major、2 Minor；同时重审了 WP7 第二轮之后的 3 条 Minor 修改）。两条 Minor 已改：`FunctionUnitComponent.clone` 的接口注释改为 D-35 口径（能看到源 FU 即可）；开发指南 §7.9 与“导入落位 / 导入流程”按 WP6 导入契约重写（`targetGroupId`、按 `manifest.team` 的 code 落位、`IMPORT_WORKSPACE_REQUIRED` / `IMPORT_TEAM_NOT_FOUND` / `IMPORT_TEAM_INACTIVE` / `IMPORT_WORKSPACE_CONFLICT`、新版本须 TL），manifest 说明补 `team`，删掉过时的 `conflictStrategy`。只改注释与文档，不再单独重审 | 完成 |
| 验证：DW 后端 `mvn -B clean test` 1466 / 0F / 0E / 8 skipped（访问服务 20 例、导入落位 6 例、克隆 6 例、续期 / 登录准入 6 例等）；Admin `mvn -B clean test` 1100 / 0F / 0E / 2 skipped；DW 前端 vitest 191 个文件全绿，vue-tsc 本次改动文件 0 个新错误（`FunctionUnitCard.vue:9` 状态标签类型是既有错误）；Docker（DW、Admin 按当前代码重建）`frontend/scripts/_wp6-dw-checks.mjs` 42 项 + 清理 2 项全过，含 Automation 6 项（经 Admin `/internal/ap/token`：TL、Developer 在本队可写；Auditor 进任一团队只读；不在名单的团队、CUSTOM 组 403）；临时团队、FU、Auditor 成员用完即删并查库确认；截图 10 张 `frontend/developer-workstation/verification-screenshots/2026-10-31_wp6-*.png` | 完成，待 review |

## WP7 技术方案

**现状（2026-10-31 调查）：**
- DW 部署 = 导出 ZIP → Admin `POST /function-units-import/import`（multipart）→ `POST /function-units/{id}/validate` → `POST /function-units-import/{id}/deploy`，三个接口都是 `DEPLOYER`（SYS_ADMIN / TECH_LEAD / DEVELOPER，不看团队）。Admin 页面手动导入走 `POST /function-units/import`（JSON + Base64，SYS_ADMIN）。两者都进 `FunctionUnitImportComponent.importFunctionPackage`。
- WP6 起导出包 `manifest.json` 带 `team { code, name }`；Admin `FunctionUnitPackageParser` 把 manifest 读成 Map，目前不取 team。
- `sys_function_units.team_code` 已在 WP3 加列，无人写；删除团队（WP5，D-33）按 `COUNT(DISTINCT code) WHERE team_code = ?` 判断，会把“已移走的 FU 的旧版本行”也算进去。
- 同版本重复导入（部署中途失败重试）走幂等分支，直接返回、不写任何列。
- 手动导入对话框只有上传，没有团队；FU 目录列表没有团队列。

**方案：**
1. **FU 的团队（单一口径）**：一个 code 的所属团队 = 该 code 最新导入的版本行（`imported_at` 最新）的 `team_code`（PRD §8.1）。`FunctionUnitRepository` 加一个查询；删除团队检查、导入时的现有归属、validate / deploy 的团队判断、目录列表都用它。
2. **导入时确定最终团队（新 `FunctionUnitTeamService`，`FunctionUnitImportComponent` 两条路径共用）**：
   - 包里有团队：本环境按 code 找 DEVELOPER 组；不存在 → `FU_IMPORT_TEAM_NOT_FOUND`“请先建团队 X（CODE）”；已停用 → `FU_IMPORT_TEAM_INACTIVE`“团队 X 已停用，请先启用”。不论谁、走哪条路径、有没有另选团队都拒绝（D-23）。
   - DW 部署路径（`/function-units-import/import`）：最终团队 = 包里的团队。包里没有团队（DW 里所属团队已被删除）→ `FU_IMPORT_TEAM_REQUIRED`，提示先在 DW 把 FU 移到一个团队，或在 Admin 手动导入时选择。
   - 手动导入路径（`/function-units/import`）：请求必须带 `teamCode`，否则 `FU_IMPORT_TEAM_REQUIRED`；所选必须是本环境 ACTIVE 的 DEVELOPER 组（含 Public），否则 `FU_IMPORT_TEAM_INVALID`。
   - 权限（在导入组件里判，不靠调用方）：SYS_ADMIN 任意。DW 部署路径：最终团队的 Technical Lead 或 Developer。手动导入路径：所选团队的 Technical Lead。导入的是已有 FU 的新版本时，还须在它现有团队有同样的权限（手动路径：§8.1“导入的是别的团队已有 FU 的新版本时拒绝”；DW 路径：D-34，团队变了才多出这一条）。现有归属为空（存量 FU，C-5 靠重新导入补齐）只看最终团队。不满足 → 403 `FU_TEAM_FORBIDDEN`，说明缺哪个团队的权限。Technical Lead 进 Admin 在 WP9，规则先放在这里，WP9 只需放开接口。
   - 写入：新版本行写 `team_code`；同版本幂等重导入时若团队变了，只更新这一行的 `team_code` 与导入时间 / 导入人（这就是“本次导入的版本行”，触发器不保护这几列）；旧版本行不改。
3. **validate / deploy 收窄到团队（DEPLOYER 两个按 id 的接口）**：操作人须是 SYS_ADMIN，或该 FU 所属团队（第 1 条口径）的 Technical Lead / Developer；FU 没有所属团队（存量）只有 SYS_ADMIN。不满足 → 403。
4. **手动导入的预填（新接口 `POST /function-units/import/preview`，SYS_ADMIN，WP9 再放开给 TL）**：解析包，返回 code、名称、版本、包里的团队（含本环境状态 ACTIVE / INACTIVE / MISSING）、本环境该 code 现有的团队、调用者可选的团队（SYS_ADMIN = 全部 ACTIVE 的 DEVELOPER 组含 Public；Technical Lead = 自己担任 TL 的有效团队）。前端据此：包里团队存在且有效 → 预填、可改选；包里团队缺失 / 停用 → 显示原因、禁止导入；旧包 → 预填现有团队，没有则必须选。
5. **目录显示团队**：`FunctionUnitInfo` 加 `teamCode`、`teamName`（团队已被删除时 `teamName` 为空，前端标“团队已删除”）；FU 目录列表加“团队”列。
6. **删除团队检查**改为第 1 条口径：只数“最新导入行属于该团队”的 code。
7. **前端（Admin）**：导入对话框选完文件先调 preview，显示团队下拉（预填规则如上）与不可导入原因；提交带 `teamCode`；目录列表团队列；三语 i18n。
8. **DW**：不改。部署失败时 Admin 返回的 `message` 已经拼进 DW 的部署步骤提示（`DeploymentComponentImpl.extractAdminError`）。
9. **不做**：Technical Lead 登录 Admin、按团队过滤目录与其它 FU 操作（启用、停用、删除、Access）（WP9）；生产存量 FU 的团队靠重新导入补齐（C-5，不另做“设置团队”）。
10. **验证**：团队服务与导入组件单测（包里团队缺失 / 停用 / 改选绕过、DW 路径 Developer 与非名单、手动路径 TL 自己 / 别人团队 / 别的团队已有 FU、换团队要两边权限、同版本重导入改团队、旧包）；validate / deploy 团队判断单测；Admin 全量；Admin 前端 vitest + vue-tsc；Docker：DW 用 Developer 部署（Admin 目录显示团队）、部署到缺团队 / 停用团队的提示原样出现在 DW、另一团队的 Developer 直接调 Admin 三个接口 403、手工改包抢别的团队 FU 被拒、SYS_ADMIN 手动导入预填 / 改选 / 旧包必须选 / 缺团队不可导入、FU 移团队后由 TL 重新部署 Admin 跟着变、旧团队可删；截图（导入对话框三种状态、目录团队列）。

### WP7 明细

| 项 | 状态 |
|---|---|
| D-34（2026-10-31 用户确认）：换团队的新版本要原团队和新团队两边的部署权限 | 已写入 PRD |
| `FunctionUnitTeamService`：FU 的团队 = 该 code 最新导入行的 `team_code`（`FunctionUnitRepository.findTeamCodeOfLatestImport`）；导入定团队（包里团队缺失 / 停用按名称与 code 拒绝，任何路径、任何人、另选团队都绕不过；DW 部署按包、对话框按所选；SYS_ADMIN 任意，DW 路径要最终团队名单，对话框要所选团队 TL，换团队再要现有团队同样权限，存量无团队的只看最终团队）；validate / deploy 只给 SYS_ADMIN 与 FU 所在团队名单（无团队的只 SYS_ADMIN）；403 `FU_TEAM_FORBIDDEN`（新异常，不被导入控制器当成 400 吞掉）；名单查询只认 ACTIVE 账号与有效角色（`isActiveUserOnLists`，Automation 共用） | 完成 |
| `FunctionUnitPackageParser` 读 `manifest.team`；导入组件在写任何数据之前定团队，新版本行写 `team_code`，同版本重导入团队变了只更新这一行的团队与导入时间 / 人；`POST /function-units/import/preview`；`/function-units/import` 带 `teamCode`（MANUAL），`/function-units-import/import` 按包（DEPLOYMENT） | 完成 |
| 目录：`FunctionUnitInfo.teamCode/teamName`，列表“团队”列（每页一次批量查最新导入行，可按团队名筛选排序，团队已删除标注）；删团队检查改为只数最新导入行属于该团队的 code | 完成 |
| Admin 前端：导入逻辑抽到 `useFunctionUnitImport`（`useFunctionUnit.ts` 413 → 354 行）；对话框选文件即预览，预填包里团队 / 现有团队，缺失或停用的团队显示原因并禁止导入，旧包必须选；列表团队单元格；三语 i18n；后端 platform-common 三语 9 条 | 完成 |
| 验证：Admin `mvn -B clean test` 1130 / 0F / 0E / 2 skipped（团队服务 20 例、导入落位 5 例、解析 2 例、控制器 403 3 例）；Admin 前端 vitest 新增 6 例，vue-tsc 134 = 基线；Docker（Admin 前后端重建）`frontend/scripts/_wp7-admin-import-checks.mjs` 22 项检查 + 3 项清理全过（测试账号 lina / zhaomin / sunqiang，与 WP6 脚本不重叠）：DW 部署路径（本队 Developer 落位、非名单 403、缺团队按名拒绝、停用团队连 SYS_ADMIN 也拒、无团队包拒绝）、D-34（只在新团队的 Developer 403、两边都在的人可移、旧版本行不变）、validate / deploy 团队门、预览三种情形、对话框缺团队 / 改选绕不过 / SYS_ADMIN 改选、目录团队列、删团队按最新行；截图 `frontend/admin-center/verification-screenshots/2026-11-01_wp7-*.png`（4 张）；测试团队、DW 单元、目录行用完即删并查库确认。真实 DW“部署”按钮未在 Docker 里跑：它调用的就是上面直接实测的同一个 Admin 导入接口与身份，dev 里跑通会留下受触发器保护、删不掉的已部署目录行 | 完成，待 review |
| review 第一轮 CONDITIONAL（0 Blocker、0 Major、8 Minor、2 Question）：同版本重导入只在“这一行已是该 FU 最新导入行且团队相同”时不写（较旧版本重导入也会让所选团队生效）；目录导出的“团队”列有值（`FunctionUnitInfo.team` = 团队名，团队已删除时为 code，与筛选排序同口径）；“最新导入行”排序收成 `FunctionUnitRepository.LATEST_IMPORT_ORDER` 一处，四处查询都只在单表子查询上用它；FU 所属团队已停用 / 已删除时提示点名团队并说明只有 SYS_ADMIN 能处理（两条新三语文案）；导入对话框的可选团队与导入同用 `isActiveUserOnLists`；缩进；3 个回归脚本的 JSON 导入先预览取包里团队再带 `teamCode`。Question 1：删团队仍把已归档的 FU 算在内（可恢复），归档页也显示团队列；Question 2：对话框显示“当前团队”，选的团队不同时提示“导入后将从 X 移到 Y”。DW 侧把 Admin 403 的原因读出来（`DeploymentComponentImpl.extractAdminError` 读 `error.message`，随 WP6 第二轮修复一并改）。验证：Admin `mvn -B clean test` 1135 / 0F / 0E / 2 skipped（新增：重导入较旧版本改团队、停用团队文案、可选团队同口径、名单 SQL H2 3 例）；Admin 前端 vitest 23 文件全绿、vue-tsc 134 = 基线；Docker（Admin 前后端、DW 重建）`_wp7-admin-import-checks.mjs` 23 项检查 + 3 项清理全过（新增“对话框提示将从 A 移到 B”）；截图已更新 | 完成 |
| review 第二轮 PASS（3 Minor）已改：归档页团队列与列表页一样标注“团队已删除”；导入对话框的“当前团队”按状态标“团队已删除 / 已停用”（新三语文案 `functionUnit.teamInactive`）；开发指南 DW 部署第 2 步补 Admin 团队门禁（D-23 / D-34），删掉过时的 `conflictStrategy`；技术方案错误码改为 `FU_TEAM_FORBIDDEN`；`extractAdminError` 回归测试见 WP6 第三轮。验证：Admin 前端 vitest 23 个文件 / 101 例全过，vue-tsc 134 = 基线；Docker（Admin 前端重建）`_wp7-admin-import-checks.mjs` 28 项检查 + 3 项清理全过（新增：经 Admin 归档、归档页团队列、归档页“团队已删除”、对话框当前团队已删除 / 已停用；脚本清理先把自己的归档行改回 DRAFT，否则内容保护触发器不让删）；新增截图 `2026-11-01_wp7-archive-team-column.png`、`2026-11-01_wp7-archive-team-deleted.png`、`2026-11-01_wp7-import-current-team-deleted.png`、`2026-11-01_wp7-import-current-team-disabled.png`。这些改动随 WP6 第四轮一并重审 | 完成 |

## WP8 技术方案

**现状（2026-11-01 调查）：**
- 准入：`PortalEntitlementService` 把 SYSTEM / CUSTOM / DEVELOPER 任一 ACTIVE 组都算准入（附录 A-5 仍在），不看账号状态；登录、SSO、续期、切换工作台各自调用，账号检查散在 `AuthController` 私有方法里；平台里没有可共用的“Portal 准入”判定。
- 身份：`CurrentUserIdArgumentResolver` 在没有认证时直接信任请求头 `X-User-Id`（166 个参数），Portal 过滤器链对无凭证请求放行；没有后端依赖这个回退（引擎 / Admin 调 Portal 走服务令牌或内部令牌）。
- SYS_ADMIN 旁路 10 处：FU 发起 / 查看、View 可见性（含空配置）、“仅参与人”View、流程详情与历史、记录备注 5 处、备注附件来源、催办（`isSuperAdmin` 恒假，死代码）、认领池强制释放 / 指派（Admin `ForceUnclaimAuthorizationComponent`）、登录响应 `systemAdministrator` 标志与前端按钮 `allowedRoles` 对管理员全放行（PRD 未列）。
- §7.2 未按“当前 BU + Role”：FU 访问与审计按用户全部业务角色、View 按任意 BU × 任意 Role 拼合、认领池可见性不看当前工作台。直连 API 缺口：`/tasks/{id}/history`、`/tasks/{id}/sub-table-data/all` 无访问校验，`/tasks/{id}/transfer` Portal 侧不校验，引擎 `/tasks/{id}/assign` 只要求登录（无调用方）。
- 失去准入的待办：认领池候选人在建任务时冻结（之后获得 BU + Role 的人看不到）、包含停用 / 无准入的人；强制释放 / 指派只给 SYS_ADMIN、BU Approver、同 BU + Role 的 Leader；MI 角色行的 BU / Role 没带进判断；没有“下属”查询、`sys_users` 的经理列没有索引；引擎只给本人列任务。
- 站立委托：只在 Portal 列表与办理权限判断时生效，且端到端已不可用（按委托人列任务被引擎 403、提交被拒，既有问题）。

**分两段做（同一工作包，一次上线）：**

**WP8a 准入、身份、旁路、按当前 BU + Role**
1. **Portal 准入单一判定**：platform-security 新增 `PortalEntryService`（JdbcTemplate，与 `UserRoleServiceImpl` 同风格）：`hasPortalEntry(userId)` = 账号 ACTIVE 且未删除，且在至少一个 ACTIVE 的 CUSTOM 组（`list_type = MEMBER`，AD / 人工来源都算）；`withPortalEntry(ids)` 批量版供 7.3 用。账号状态 LOCKED 算失去准入；登录失败的临时锁（`locked_until`，状态仍 ACTIVE）不算（几分钟的锁不该让同事接走任务）。`PortalEntitlementService` 改为委托它；登录、SSO、续期、切换工作台一律用它（拒绝码不变）。
2. **身份只认凭证**：`CurrentUserIdArgumentResolver` 只取认证上下文，取不到回 401；前端不再发 `X-User-Id`。
3. **删除 SYS_ADMIN 全部旁路**（上表 10 处，含前端 `allowedRoles` 与 `systemAdministrator` 标志）；随之无用的 `isSystemAdministrator`、按 ADMIN 档案取角色的缓存一并删除。
4. **只按当前 BU + Role**：FU 发起 / 查看 / 审计按当前 Role（及 BU）判断；View 按当前 BU + Role 这一对匹配访问规则；认领池只显示当前工作台 BU + Role 的池任务（MI 角色行按行上的 BU / Role）。
5. **直连 API 同一口径**：任务历史、子表全量数据按“能看这个任务”校验；Portal 转办校验调用者是当前办理人；引擎 `/tasks/{id}/assign` 无调用方 → 删除。
6. **DW View 空配置提示**：保存 / 发布访问配置为空的 View 时确认“发布后没有人能看到”；改掉仍写着“只有 System Administrator 能看到”的提示文案与校验器注释（三语）。

**WP8b 失去准入的待办与委托（§7.3）**
1. **认领池 / 候选人任务**（旧虚拟组分派的任务不再处理，D-58）：办理人失去准入时，同池（同 BU + Role 的当前持有人，候选人列表按列表）有准入的成员可释放回池、自己认领、指派给同池另一名有准入的成员；任务标“办理人已失去准入”。判断放在 Portal 注解与引擎 `TaskClaimSupport` 两处（引擎不信 Portal 的结论）；指派目标须有准入；Admin `ForceUnclaimAuthorizationComponent` 去掉 SYS_ADMIN，其余规则（办理人仍有准入时：BU Approver、同 BU + Role 的 Leader）不变。
2. **池不冻结**：UBR 授予 / 撤销后，引擎按 BU + Role 重算该池未完成任务的候选人（新持有人加入、已不持有且未认领的移出）；Admin 写 UBR 的入口调用引擎内部接口（服务令牌）。孤儿修复不再只在“没有候选人”时补。
3. **办理人离职转给经理（D-42，取代原「下属失去准入的待办」页签）**：经理 = 原办理人档案上的 Entity Manager / Function Manager。办理人离职（账号 INACTIVE 或已删除）时，直派任务、以及池里已没有有准入的人但被他认领的任务，由引擎自动指派给他的 Entity Manager，不可用则 Function Manager（可用 = ACTIVE 且有准入），直接出现在经理的 To Do；只失去 CUSTOM 组、账号仍 ACTIVE 的不转。Admin 停用 / 删除用户后通知引擎（服务令牌），引擎定时兜底。没有经理页签。
4. **站立委托**：委托人或被委托人任一方失去准入，规则在列表、办理权限、委托页三处都不生效（规则不删，恢复准入后重新生效）。
5. **变更历史**：失去准入后的释放 / 认领 / 指派沿用现有类型；离职转给经理新增类型 `MANAGER_REASSIGN`，操作人 system，记录原办理人、新办理人与原因。
6. 前端：To Do 标注与操作（指派候选人只列有准入的池成员）、变更历史原因、三语 i18n。

**不做**：修复站立委托本身（既有缺陷，记入范围外）；Leader / Approver 能力按当前工作台收窄（Admin 侧不知道当前工作台，记入范围外）；预检报表（WP10）。

**验证**：`PortalEntryService` H2 真 SQL 测试（CUSTOM / SYSTEM / DEVELOPER、停用组、停用 / 删除 / LOCKED 账号、临时锁）；Portal、引擎、Admin、DW 全量；Docker：只在 SYSTEM / DEVELOPER 组的人登录被拒、加入 CUSTOM 后可进；无凭证带 `X-User-Id` 401；SYS_ADMIN 进 Portal 后 View / FU / 流程详情 / 备注与普通用户一致；多 UBR 用户切换工作台后 FU / View / 池只按当前一对；办理人失去准入后同池成员释放 / 认领 / 指派、离职后任务自动到经理 To Do、委托失效；截图（To Do 标注、变更历史、DW 空 View 确认）。

### WP8b 实现细化（2026-11-01）

**现状核实（读代码与 dev 库，2026-11-01）：**
- 引擎没有“凭服务令牌认人”的通道：`JwtAuthenticationFilter` 遇到有效服务令牌只是不回 401，请求仍按未认证被 `anyRequest().authenticated()` 拦下。引擎唯一的内部接口是 `RecordNoteInternalController`（`GET /api/v1/internal/record-note-targets`），它在 `SecurityConfig` 里单独 `permitAll`，在控制器里用 `X-Internal-Token` 做常量时间比对。Admin 现有调引擎的 `WorkflowEngineClient`（部署、启停）不带任何令牌。
- 分派类型不记在任务上：`TaskAssignmentListener` 只调 `setAssignee` / `addCandidateUser`；`wf_extended_task_info.assignment_type` 只有 MI 行写。要分清池、候选人列表、直派，得读 BPMN（`BpmnActionParser.getUserTaskExtensionPropertyValue`）和身份链接。
  - 引擎主代码没有 `addCandidateGroup`；VIRTUAL_GROUP 解析为空（附录 A-30）。旧虚拟组任务只可能是存量：候选组身份链接，或 MI 行的 `assignment_type = VIRTUAL_GROUP`。
- 池在建任务时冻结：
  - BU_ROLE / HIERARCHY_ROLE 只在建任务时 `addCandidateUser` 一次。
  - `TaskOrphanRepairService.restoreBuRoleClaimPool` 只在任务没有任何候选身份链接时才补（`hasCandidateIdentityLink`）。
  - 修复只在读路径上触发（`TaskQueryService.getUserTasks` / `getUserAllVisibleTasks`、`TaskClaimSupport.unclaimTask`），30 秒限频，只扫 `taskUnassigned()` 的前 `fetchLimit` 条；没有定时任务。
- 强制释放与指派：
  - 引擎：`TaskClaimSupport.actorMayForceUnclaim` 只问 `AdminCenterClient.canForceUnclaim`，不传办理人。`reassignClaim` 的目标必须是 Flowable 候选人（没有候选人时只认 MI VIRTUAL_GROUP 行的组成员）。
  - Portal：`ClaimForceUnclaimAnnotator` 只看 `BuRolePoolTasks.isClaimPoolTask`（BU_ROLE 类与 MI 按角色）。候选人列表、HIERARCHY_ROLE、旧虚拟组任务一律 `canForceUnclaim = canReassign = false`。
  - 前端：`TaskReassignDialog.vue` 的候选人取 `candidateUserIds` 去掉持有人，不看准入。
- 列表：
  - 引擎 `/claim-pool` 用 `ClaimPoolTaskQueries.visibleIncludingClaimed`，返回本人是候选人的全部任务，含已认领的。
  - Portal `TodoListQueryComponent.fetchAllClaimPoolTasksPaged` 再用 `BuRolePoolTasks.retainClaimPoolTasks` 只留 BU_ROLE 类与 MI 按角色。
  - 引擎的列表接口都只给本人，`userId` 与操作人不同即 403。
- 变更历史：
  - Portal `ChangeType` 只有 CLAIM / UNCLAIM / FORCE_UNCLAIM / REASSIGN。
  - 写入方法是 `TaskAssignmentHistoryRecorder.record(task, actor, type, newHolder)`，`oldValue = task.assignee`；`change_type` 列没有 CHECK 约束。
  - 转办、委托只写 `up_delegation_audit`。
- 站立委托：
  - 列表 `DelegatedTaskQueryComponent.loadStandingRuleDelegatedTasks` 与办理权限 `TaskPermissionEvaluator.canProcessTask`（委托规则分支）都直接调 `DelegationRuleRepository.findActiveDelegationsForDelegate`。
  - 委托页 `DelegationController` 直接返回 `DelegationRule` 实体。
  - 引擎里没有站立委托。
- Admin 写 UBR 的地方实际有 8 处，比交接清单多出 2 个已废弃的方法：
  - `UserBusinessUnitRoleManagerComponent`：`assign`（新建、改档位）、`remove`。
  - `MemberManagementService`：`addUserToBusinessUnitWithRoles`（审批通过）、`removeBusinessUnitRole`（废弃，无生产调用方）、`removeBusinessUnitMember`（无生产调用方）、`exitBusinessUnitRoles`（废弃接口 `/exit/business-units/{id}/users/{uid}/roles`）、`exitBusinessUnit`。
  - 没有原生 SQL 写这张表。数据库只在删用户、BU、角色时 `ON DELETE CASCADE`：Admin 删用户是软删，删 BU 时有成员即拒绝，删角色时有引用即拒绝。
- dev 数据：
  - 未删除账号 10 个，`entity_manager_id` / `function_manager_id` 全为空。
  - e2e 账号都是 CUSTOM 组 `E2E_WORKFLOW_SIMULATION` 的人工成员，也都是 `E2E_FINANCE`、`hase-hmdc` 的 BU 成员。
  - 只有 `E2E_FINANCE / MANAGER` 有两名持有人（`developer`、`e2e_lina`）。
  - 已部署的定义里，只有 `atm-20260623-gaevus` 有 BU_ROLE 节点（`hase-hmdc` + HMDC_Assign / Operator / Approver_Role，每个角色最多 1 名持有人）；`DigitalLendingProcessV2` 有 4 个 VIRTUAL_GROUP 节点（建任务时解析为空）。
  - 运行中的任务只有 3 个，都直派给 `developer`。`act_ru_identitylink`、`wf_extended_task_info`、`up_change_history`、`up_delegation_rule` 都为空。

**判据只写一处，Portal 与引擎都调用它**（已实现，见文末“引擎一侧”）。platform-security `com.platform.security.service.LostEntryTaskPolicy`，与 `PortalEntryService` 同样用 JdbcTemplate；Portal 本来就扫描 `com.platform.security.service`（WP8a 登记 `PortalEntryService` 时），引擎扫描整个 `com.platform.security`：
- 任务的表示：`TaskHolding(assigneeId, PoolKind kind, poolMemberIds)`。`PoolKind`：
  - `NONE`：分派给一个人（发起人、表单字段单人、写死的人、经理）。
  - `PEOPLE`：指定的一组人（如多选用户字段）。
  - `MEMBERSHIP`：按成员资格（BPMN BU + Role 与 HIERARCHY_ROLE、MANUAL_ASSIGN 回退成 BU + Role、MI 按角色的行、旧虚拟组任务）。
- `static mayTakeOver(actor, holding, withEntry)`：已认领、持有人没有准入、不是 NONE、操作人有准入且在池里 → 可以释放、接手、指派。
- `static takeoverTargets(holding, withEntry)`：池成员中有准入的人，去掉持有人。
- `static departedOwners(holding, withEntry, departed)`（D-42）：返回该由谁的经理接手，按优先顺序；空 = 留在原处。
  - 已认领：持有人离职，且（NONE，或池里没有其他有准入的人）→ 持有人。池里还有有准入的人时归第 1 条（AC-26），不转。
  - 未认领的 `PEOPLE`：候选人全部离职 → 全部候选人，保持候选人顺序（D-39）。
  - 其余（持有人只失去准入、未认领的 `MEMBERSHIP`）→ 空。
- `departedAmong(userIds)`：离职 = `status = 'INACTIVE'` 或已删除；LOCKED 不算。
- `managerFor(owners)`：按 owners 顺序，取第一个人的 Entity Manager，不可用则其 Function Manager，再不行看下一个人；可用 = `withPortalEntry`（ACTIVE、未删除、有 CUSTOM 组）。都不可用 → 空。读 `sys_users.entity_manager_id / function_manager_id`。
- 判断用的“有准入的人”“离职的人”由调用方一次批量算好再传进来，列表只查一次库。准入与离职都不写进任务：它们的变化（CUSTOM 组成员、账号状态）没有任务能跟的事件。
- 池成员由调用方提供：
  - BU + Role 池用 Flowable 候选人，第 2 条做完后就是当前 UBR 持有人。
  - 候选人列表用建任务时的名单。
  - 旧虚拟组任务用组成员（引擎新增 `VirtualGroupMembersClient`，读 Admin `/virtual-groups/{id}/members`）。**D-58（用户 2026-11-02）**：旧虚拟组分派的任务不再处理——平台已不按虚拟组分派，Portal 只承诺 BU + Role 池与候选人列表；Portal 注解里读组成员的分支（`assignmentType = VIRTUAL_GROUP`、候选组）永远命中不了，已删除，连同按虚构数据形态写的测试；引擎已有的保持不动，不为它新增。

**1. 失去准入的池任务**（AC-26、AC-33、D-17）
- 引擎 `TaskClaimSupport`：
  - `actorMayForceUnclaim`：Admin `canForceUnclaim` 通过（办理人仍有准入时的规则，不变），**或** `TaskHoldingResolver.mayTakeOverFromLostHolder(task, 操作人)` 允许。`TaskHoldingResolver` 把 Flowable 任务读成 `TaskHolding`：池成员 = 候选人 ∪ 候选组成员 ∪ MI VIRTUAL_GROUP 行的组成员，再交给 `LostEntryTaskPolicy.mayTakeOver`。
  - `reassignClaim`：在 `validateReassignTarget` 之后加“目标必须有准入”，所有情形都加（含 Leader / Approver，这就是原方案“指派目标须有准入”）。目标是操作人本人时就是“接手”，复用 `POST /{taskId}/reassign-claim`，不开新接口。
  - `unclaimTask` 不改：它本来就经过 `actorMayForceUnclaim`。
- Portal：
  - `ClaimForceUnclaimAnnotator.annotate`：范围从 `isClaimPool` 扩大到所有有池的任务（BU_ROLE 类、MI 按角色、`candidateUserIds` 非空；D-58 起不看候选组与虚拟组行）。一次批量 `withPortalEntry(持有人 ∪ 候选人)`。
  - `TaskInfo` 新增字段：`assigneeLostPortalEntry`、`canTakeOver`、`reassignTargetUserIds`。`canForceUnclaim` / `canReassign` = Admin 结果 OR 失去准入规则。
  - `fetchAllClaimPoolTasksPaged`：除 `retainClaimPoolTasks` 留下的任务外，再保留“持有人没有准入、本人是候选人”的其他任务（新 util `LostEntryTakeoverTasks`）。理由：候选人列表里已被认领的任务，其他候选人原来看不到（D-17）。
  - 历史：释放沿用 `TaskProcessComponent.unclaimTask` 记 FORCE_UNCLAIM；接手与池内指派沿用 `TaskReassignComponent.reassign` 记 REASSIGN（接手时新办理人 = 操作人）。

**2. 池不冻结**
- 契约（Admin 一侧已按此实现，见下）：
  - `POST {workflow-engine.url}/api/v1/internal/claim-pools/resync`
  - 请求头：`X-Service-Token` = `service.internal-token`，不带用户。
  - 请求体：`{"pools":[{"businessUnitId","businessUnitCode","roleId","roleCode"}]}`。id 和 code 都带，因为存量 BPMN 存 id，现行设计器存 code。
  - 返回：platform-common `ApiResponse<ClaimPoolResyncResult>`，`data = {tasksExamined, tasksUpdated, holdersReleased, tasksSkipped, tasksFailed}`。令牌错误回 401，请求体不合法回 400，Admin 查询失败回 503（失败点之后的任务不改）。
- 三个触发点，共用 `ClaimPoolMembershipSync`：
  - Admin 推送（`ClaimPoolInternalController`）。
  - 定时全量兜底（D-37）：`ClaimPoolResyncScheduler`，ShedLock `ClaimPool_fullResync`，默认首次 2 分钟后、之后每 10 分钟（`workflow.claim-pool.full-resync-initial-delay-ms` / `-delay-ms`），按当前 UBR 重算全部未完成的 BU + Role 池任务。它也覆盖不经过 Admin 的 UBR 变化（级联删除、脚本、手工 SQL）。Admin 推送失败时就靠它补上：Admin 侧保留 ERROR + `FALLBACK(external)`，注释写明兜底是这个定时任务。
  - 读路径的孤儿修复：`TaskOrphanRepairService.repairOrphanBuRolePoolTasks` 对未认领任务调 `resyncUnclaimed`（一轮共用一份持有人查询），不再只补“没有候选人”的任务；MI 行仍由原来的 MI 修复处理。认领后释放时，`restoreBuRoleClaimPool` 调 `resyncTask(taskId)`（重新读任务，看到刚释放的状态）。仍然从不 `setAssignee`。
- 找池任务（`ClaimPoolTaskLocator`）：
  - BPMN BU_ROLE / FIXED_BU_ROLE 节点：先 `SELECT DISTINCT proc_def_id_, task_def_key_ FROM act_ru_task`（`FlowableRuntimeTaskQueries`），按 BPMN 属性筛节点；Admin 推送时 BU ∈ {id, code}、角色 ∩ {id, code} 才算命中，命中后统一换成 code。再按（定义, 节点）查任务。
  - MANUAL_ASSIGN 且没选人、回退成 BU + Role 的（流程变量 `manualAssignBu_<defKey>` / `manualAssignRole_<defKey>`，变量名可由 BPMN 属性改写）。
  - MI 按角色的行（`ExtendedTaskInfoRepository.findOpenMiRoleRows`，`extended_properties` 里的 `businessUnitCode` / `roleCodes`）。读不出的行记 ERROR、跳过，不拖累其他池。
  - HIERARCHY_ROLE 池不重算（D-38）：它的 BU 是建任务时从锚点用户推出来的，没有存在任务上。
- 对每个任务：
  - 期望候选人 = 各角色 `AdminCenterClient.getUsersByBusinessUnitAndRole` 的并集，一轮内按（BU, 角色）只查一次。BU 不再准入某个角色（`isEligibleRole` 为假，含 code 不存在）时这条任务原样不动，计入 `tasksSkipped`。
  - 补上新持有人；移除已不持有的人。池里一个持有人都没有时候选人清空，之后有人获得该 BU + Role 就会被补进来。
  - **D-40 自动释放**：办理人在候选人里、但已不持有该 BU + Role → `unclaim`（有 MI 行时一并 `unclaimTask`）再移出候选人，计入 `holdersReleased`。办理人不在候选人里（经理转派的接手人、转办给池外的人）不算“失去该 BU + Role”，不释放：只有池成员资格才谈得上失去。
  - 一条任务在处理中途被别人改掉（Flowable 异常，如已完成）记 WARN、计入 `tasksFailed`，其余继续，下一轮再试。Admin 不可达则整轮停止（抛出），已做的部分保留。
- 释放写进变更历史：`up_change_history` 属于 Portal，引擎在一轮结束后调 Portal 内部接口，由 Portal 落库（后半段实现）。
  - 契约：`POST {user-portal.url}/api/portal/internal/task-assignment-events`，`X-Service-Token`，体 `{"poolMembershipReleases":[{"taskId","processInstanceId","previousAssigneeId","businessUnitCode","roleCodes":[...],"releasedAt":"ISO-8601"}]}`。
  - Portal 按现有的释放类型落库，操作人记为系统，并能看出原因是“失去该 BU + Role”。
  - 调用失败时引擎记 ERROR（带全部任务 id，`FALLBACK(external)`，审计旁路），释放本身不回滚。Portal 接口做好之前，每次释放都会记这条 ERROR。

**3. 办理人离职转给经理**（AC-27、AC-33 后半、D-39、D-42）
- 用户 2026-11-01 定 D-42，取代原「下属失去准入的待办」页签。该页签及其接口、`portal-entry-search`、经理转派对话框、两条错误文案已删除。
  - 直派任务：办理人只失去准入、账号仍 ACTIVE → 不转，由本人重新申请准入；办理人离职 → 自动指派给经理。
  - 池任务不变（第 1 条，AC-26）。已认领、池里没有其他有准入的人：只有持有人离职才转给经理。
  - 未认领的候选人列表：候选人全部离职才转（D-39）；按候选人顺序，取第一个有可用经理的候选人。未认领的 BU + Role 池、旧虚拟组任务不处理。
  - 两位经理都不可用：留在原处，记 ERROR，不另找兜底人。
- 引擎 `component/DepartedHolderReassignment`：
  - 找任务：办理人是离职者的任务（`taskAssigneeIds(...).active()`）∪ 离职者是候选人的未认领任务（`FlowableRuntimeTaskQueries.unclaimedTaskIdsWithCandidateIn`）。暂停中的任务不能改办理人，恢复后由定时兜底处理。
  - 逐条读成 `TaskHolding`（MI 行一次批量取）；整批一次 `withPortalEntry` 与 `departedAmong`（覆盖全部候选人，不只是被通知的人）；`departedOwners` 非空时调 `managerFor`。
  - 指派：`setAssignee(经理)`，有扩展行时 `claimTask(经理)`，两者在同一事务里。池（候选人）原样保留：经理不是候选人，D-40 不会把他释放；经理取消认领时任务回到原池，下一轮再转给经理。
  - 一条任务读不出池（旧虚拟组成员取不到）或中途被改（Flowable 异常）记日志、计入 `tasksFailed`，其余继续。返回 `DepartedReassignResult(tasksExamined, tasksReassigned, tasksStranded, tasksFailed)`。
  - 结束后经 `PortalAssignmentEventClient.reportDepartedReassignments` 报给 Portal 落变更历史；失败记 ERROR（`FALLBACK(external)`），指派不回滚。
- 触发：
  - Admin 推送：`POST /api/v1/internal/departed-users/tasks/reassign`，体 `{"userIds":[...]}`，只带 `X-Service-Token`（`DepartedUserTasksInternalController`；`SecurityConfig` 放行 `/api/v1/internal/departed-users/**`）。引擎自己再查一遍账号状态，没离职的 id 忽略。
  - 定时兜底：`DepartedHolderReassignScheduler`，ShedLock `DepartedHolders_fullPass`，默认首次 150 秒后、之后每 10 分钟（`workflow.departed-holders.full-pass-initial-delay-ms` / `-delay-ms`）。取所有未完成任务的办理人与未认领任务的候选人（`openTaskPeople`），其中离职的人照上处理。它兜住 Admin 推送失败、暂停后恢复的任务、池里最后一个有准入的同事也失去准入。没有可用经理的任务每轮都会再记一次 ERROR。
- Admin：`UserManagerComponent.updateUserStatus`（新状态 INACTIVE）与 `deleteUser`（软删，状态置 INACTIVE）调 `DepartedUserTasksService.userDeparted`：提交后推送，回滚不推送；失败记 ERROR + `FALLBACK(external)`，注释写明兜底是引擎定时任务。LDAP 同步只写 ACTIVE / LOCKED、批量导入只建用户、登录只会锁定，都不是离职入口。
- 经理接手后就是普通办理人：在 To Do 里办理或转办（现有 `/tasks/{id}/transfer`），用自己的身份提交。

**4. 站立委托**（AC-34、D-18）
- Portal 新组件 `EffectiveDelegationRules`：
  - `activeForDelegate(delegateId)`：`findActiveDelegationsForDelegate` 的结果中，去掉委托人或被委托人没有准入的规则。
  - `inactiveReasons(rules)`：返回 `DELEGATOR_LOST_PORTAL_ENTRY` / `DELEGATE_LOST_PORTAL_ENTRY`。
- 三处改用它：
  - 列表：`DelegatedTaskQueryComponent.loadStandingRuleDelegatedTasks`。
  - 办理权限：`TaskPermissionEvaluator.canProcessTask` 的委托规则分支。
  - 委托页：`DelegationComponent.getDelegationRules` / `getDelegationsForDelegate` / `getDelegatorIds` 与 `DelegationListQueryComponent.queryRules`。接口直接返回实体是存量写法，所以给 `DelegationRule` 加 `@Transient inactiveReason`。
- 规则的状态不改，也不删。
- 运行验证受既有缺陷限制：站立委托端到端已不可用（见上方现状）。Docker 只能验委托页的标注和接口返回，列表与办理权限以单测为证。
- **单次委托（D-55，用户 2026-11-01 定，取代原“不做”）**：任务页上把这一条任务委托给某人或某个 BU + Role（`wf_extended_task_info.delegated_to` / `delegated_bu_code` + `delegated_role_code`），与站立委托同口径——委托人或被委托人失去准入期间不生效，任务一直在委托人名下、委托人可以自己办，恢复准入后重新生效，委托本身不改不删。
  - 判定只在 `EffectiveDelegationRules`：`markSingleTaskDelegations`（批量，给 `TaskInfo.delegationInactiveReason` 填 `DELEGATOR_LOST_PORTAL_ENTRY` / `DELEGATE_LOST_PORTAL_ENTRY`）与 `singleTaskDelegationInForce`；委托给 BU + Role 时，被委托人就是正在看或正在办的那个成员。
  - Portal：被委托人的“委托”列表（`DelegatedTaskQueryComponent` 引擎叠加部分）去掉不生效的；办理权限 `TaskPermissionEvaluator.isSingleTaskDelegatee` 要求生效；列表与详情的 `annotateClaimState` 一并批量标注（无委托的任务不查库）。
  - 引擎不信 Portal：`SingleTaskDelegateCheck`（从 `TaskCompletionService` 抽出，后者 794 → 763 行）代办完成时要求持有人与操作人都有准入。
  - 前端：任务页“委托”一栏在“已委托给 X”后加“· 未生效：被委托人 / 委托人已失去 Portal 准入”（沿用委托页三语文案）。帮助站委托文章与 To Do 文章 `#lost-access` 三语同步。
  - 委托给 BU + Role 时组合整体不算失去准入（D-57），只按每个成员自己是否有准入。
  - **只对能委托的任务显示“委托”（D-56）**：引擎任务 DTO 带 `delegable`（`util/TaskDelegability`：有扩展行、有人持有、未完成——就是引擎委托时要的条件），Portal `TaskInfo.delegable` 照搬；任务页“委托”按钮只在 `delegable` 为真时出现（批量只有通过 / 驳回，没有委托入口）。不能委托的任务直接调委托接口，Portal 先由 `TaskDelegationGuard` 拒绝：400 + 三语 `portal.task_not_delegable`；引擎缺扩展行时的报错也从 “Task not found” 改为 “This task cannot be delegated”。不扩大委托范围。
  - **换持有人时委托结束（D-59，用户 2026-11-02 定）**：单次委托是持有人自己的安排，新持有人不继承。只在引擎清、只清一处：`ExtendedTaskInfo.endDelegationUnlessSameHolder(newHolder)`——持有人变了或持有结束（回到池里）就清掉委托并返回被委托人，只有同一持有人重复认领不清（review 第三轮 Minor 2 去掉了原先“新持有人是委托人就保留”的豁免）。`claimTask` / `unclaimTask` 先调它，所以认领、池内接手与指派（`TaskClaimSupport.reassignClaim`，含 Leader / Approver）、取消认领与强制释放（`unclaimTask`；扩展行未处于认领状态时也清）、D-40 系统释放（`ClaimPoolMembershipSync.release`）、D-42 转经理（`DepartedHolderReassignment.hand`）都经过它；转办（`transferTask`）本来就清。持有一结束（释放）委托即结束，之后谁认领（含原持有人自己）都不恢复，需要时由新持有人重新委托。系统释放与转经理的事件带 `endedDelegate` 报给 Portal。
  - 引擎代办判定的委托人取 `delegatedBy`，缺省才用持有人（`SingleTaskDelegateCheck`，与 Portal `EffectiveDelegationRules` 同口径，review 第二轮 Minor 2）。
  - **已有持有人的任务只认持有人（D-60，用户 2026-11-02 定，review 第三轮实现）**：
    - 完成：`TaskCompletionService.isCompleteAuthorized` 在任务有持有人（Flowable assignee）时只认持有人本人，或 `onBehalfOfUserId` = 持有人且 `SingleTaskDelegateCheck.mayActFor` 成立的被委托人；候选人、候选组成员、扩展行指派对象（`validateCompletePermission`）都不再放行。拒绝仍是 `WorkflowValidationException`（400，与现有拒绝同格式），文案 “This task is held by another user: only its holder or a delegate in effect can complete it”。无人持有的任务照旧：候选人、候选组成员、扩展行指派对象。
    - 同口径的另两个入口：`TaskActionService.transferTask` 有持有人时只有持有人能转办（原先没有扩展行的任务谁都能转办，有扩展行的按扩展行判断），无人持有照旧；`delegateTask`（引擎本来只委托有持有人的任务）委托人必须是持有人，不再按扩展行的指派对象判断，D-42 接手的经理因此也能委托。
    - 入口逐个核对：完成只有 `completeTask` 一处，单条 `/complete`、批量 `/batch/complete`、驳回（带 REJECT 的完成）、MI 子任务完成都经过它；认领（已有持有人即拒）、取消认领 / 强制释放、指派 / 接手各有规则，不变；`/check-permission` 只读、Portal 不调用；退回 `/return` 与 MI 前置任务的行指派 `/sub-table-rows/{rowId}/assign` 在引擎里根本不校验操作人（HEAD 既有），不是“候选人即可办”；用户已定维持现状，见范围外表。
    - 正当路径：持有人本人；单次委托（Portal 带 `onBehalfOfUserId`）；Portal 批量处理先认领再完成；MI 子任务由持有人完成；没有持有人也没有候选人时按发起人补持有人（先补再判断）；Portal 发起时自动完成第一步（先认领再完成）；无人持有的池任务照旧。引擎的完成只有用户令牌一条入口，没有服务令牌调用方。D-18 站立委托在引擎里没有路径：Portal 提交时不带 `onBehalfOfUserId`，HEAD 起引擎就拒绝（见第 4 条“运行验证受既有缺陷限制”），D-60 不改变它，所以 PRD 写“单次委托”与实现一致。

**5. 变更历史**
- Portal `ChangeType` 新增 `MANAGER_REASSIGN`（不超过 30 字符，不需要 DDL），`AssignmentChangeTypes.isAssignmentAction` 收录它。
- 离职转给经理（D-42）：`user_id = system`，`old_value` = 原办理人（未认领的候选人列表为空），`new_value` = 经理，`field_name` = `claimed_by`，`reason = ASSIGNEE_DEPARTED`。由引擎经 `POST /internal/task-assignment-events` 的 `departedReassignments` 报来，`TaskAssignmentHistoryRecorder.recordDepartedReassignment` 落库。
- 前端 `useChangeHistoryFormatting.ts`：`ASSIGNMENT_CHANGE_TYPES` 与标签同步新增；三语 `changeHistory.managerReassign`（“指派给经理”），原因显示“系统指派：办理人已离职”。
- 委托结束（D-59）：新增 `DELEGATION_ENDED`（16 字符，不需要 DDL），`field_name = delegated_to`，`old_value` = 被委托人（BU + Role 委托记 `BU / ROLE`），`reason = HOLDER_CHANGED`。用户操作（认领、取消认领、强制释放、接手 / 指派）由 `TaskAssignmentHistoryRecorder.record` 在同一批里多写一行，操作人是做这个操作的人；只在操作前的任务带委托、操作后重新读到的任务不再带时写，Portal 不再自己推算，review 第三轮 Minor 1；系统释放与转经理由 `InternalTaskAssignmentEventController` 按事件里的 `endedDelegate` 写，操作人 system。前端类型显示“委托结束 / Delegation ended / 委託結束”，字段位置“委托给 · 委托已结束：任务换了办理人”，旧值按用户解析为姓名（`ChangeHistoryComponent.ASSIGNEE_VALUE_FIELDS` 加 `delegated_to`）。

**6. 前端**（`frontend/user-portal`）
- 待办行与任务横幅（`TodoClaimRowActions.vue`、`TaskClaimBanner.vue`）：
  - `assigneeLostPortalEntry` 为真时显示“办理人已失去准入”标签。
  - `canTakeOver` 为真时显示“接手”按钮：调 `reassignTask(taskId, 本人)`，先二次确认。
  - 释放、指派沿用现有按钮与 `useTaskClaimActions.ts`。
  - `TaskReassignDialog.vue` 的候选人改用 `reassignTargetUserIds`。
- 经理页签：D-42 取消，已删除（见第 3 条）。`views/tasks/index.vue` 已 726 行，超过 600 行硬上限：把只给 `batchUrge` 用的内联对话框与办理人单元格抽成组件，行数只减不增。
- 委托页：`DelegationRulesList.vue` 按 `inactiveReason` 显示“已暂停生效：委托人 / 被委托人失去 Portal 准入”。
- 三语 i18n（en / zh-CN / zh-TW）：`task.assigneeLostEntry`、`task.takeOver*`、`changeHistory.managerReassign` 与系统原因、`delegation.inactive*`。后端拒绝文案放进 platform-common 的三语资源。

**Admin 一侧（本段已实现，未提交）**
- 新增类：
  - `com.admin.client.EngineInternalClient`（原 `ClaimPoolResyncClient`；D-42 起两个方法 `resyncClaimPools`、`reassignTasksOfDepartedUsers`）：
    - 用主 `RestTemplate`（带熔断），只带 `X-Service-Token`。
    - 引擎回非 2xx，或回包 `success` 不为 `true`，都抛出异常。
    - 缺 `workflow-engine.url` 或 `service.internal-token` 时启动失败。
  - `com.admin.dto.request.ClaimPoolResyncRequest`。
  - `com.admin.service.ClaimPoolResyncService.ubrChanged(businessUnitId, roleIds)`：
    - 在写入方的事务里把 id 解析成 code；BU 或角色不存在就抛出，写入随之回滚。
    - 注册 afterCommit 推送；没有事务时立即推送；回滚时不推送。
    - 同一次调用内角色去重。
- 调用点：8 处写入中，除改档位（MEMBER ↔ LEADER，池是全部持有人，档位不影响）外都调用它。只上报实际变化的角色：审批只报新授予的，退出只报真正删掉的。
- 失败处理：
  - 推送发生在提交之后，此时 UBR 已经生效，仍是权威数据。
  - 推送失败（引擎不可达、熔断打开、4xx/5xx、未确认）时记 ERROR，带 BU code、角色 code 和异常，接口不报错。否则已保存的授予 / 撤销会被报成失败，Portal 审批会对已经生效的角色显示错误。
  - 失败后由引擎的定时全量重算补上（D-37，`ClaimPoolResyncScheduler`）。
  - 推送在请求线程里同步执行（读超时 30 秒），所以 UBR 接口的响应时间会加上引擎重算的时间。
- 离职通知（D-42）：`com.admin.service.DepartedUserTasksService.userDeparted(userId)`，同样提交后推送、回滚不推送、失败只记 ERROR；调用点见第 3 条。
- 守卫测试 `UbrWriteSitesReportClaimPoolsTest`：扫描 `src/main/java`，要求 UBR 写入点就是登记的这 8 处，所在文件调用了 `ubrChanged`，且没有原生 SQL 写这张表。
- 验证：admin `mvn -B clean test` 1154 / 0F / 0E / 2 skipped。新增 20 例：client 4、service 7、`MemberManagementService` 上报 5、component 3、守卫 1。Admin → 引擎的真实调用**未验证**（Docker 未跑）。

**platform-security 与引擎一侧（2026-11-01 第二段前半已实现，未提交）**
- platform-security：
  - `service/LostEntryTaskPolicy`：判据见上。
  - `util/ServiceTokenVerifier`：入站 `X-Service-Token` 常量时间比对；缺 `service.internal-token` 即启动失败；不是 Spring bean，由用到的服务自己登记。
  - Admin `InternalServiceAuthenticator.hasValidServiceToken` 与引擎 `RecordNoteInternalController`（`X-Internal-Token`，缺配置时回 503，有单测守着这个行为）还各有一份比对，没有并过来，留作后续收口。
- 引擎新增：
  - 内部接口：`controller/ClaimPoolInternalController`（`SecurityConfig` 放行 `/api/v1/internal/claim-pools/**`，并登记 `ServiceTokenVerifier` bean）。
  - 池重算：`component/ClaimPoolMembershipSync`、`component/ClaimPoolTaskLocator`、`component/ClaimPoolResyncScheduler`。
  - 失去准入：`component/TaskHoldingResolver`。离职转给经理（D-42；原 `LostEntryTaskService` / `SubordinateLostEntryTaskController` 已删除）：`component/DepartedHolderReassignment`、`component/DepartedHolderReassignScheduler`、`controller/DepartedUserTasksInternalController`。
  - 客户端：`client/PortalAssignmentEventClient`；`client/VirtualGroupMembersClient`（`AdminCenterClient` 已 910 行，超硬上限，不再往里加）。
  - 查询：`repository/FlowableRuntimeTaskQueries`。
  - 工具：`util/MiRoleRowProperties`。
  - DTO：`dto/request/ClaimPoolResyncRequest`、`dto/request/DepartedUsersRequest`、`dto/response/ClaimPoolResyncResult`、`dto/response/DepartedReassignResult`。
- 引擎修改：
  - `TaskClaimSupport`：`actorMayForceUnclaim` 加失去准入规则；`reassignClaim` 目标须有准入。
  - `TaskOrphanRepairService`：BU Role 修复改走 `ClaimPoolMembershipSync`，删掉原来的 `hasCandidateIdentityLink` / `resolveBuRolePoolUserIds`，文件从 579 行降到 517 行。
  - `ExtendedTaskInfoRepository`：加 `findByTaskIdInAndIsDeletedFalse`、`findOpenMiRoleRows`。
  - `SecurityConfig`：见上。
- 错误文案沿用引擎现有写法（英文字面量，`WorkflowValidationException` / platform-common `BusinessException`）：“Target has no Portal access”。
- 验证：
  - platform-security `mvn -B clean test` 125 / 0F / 0E / 0 skipped（新增 `LostEntryTaskPolicyTest` 12 例、`ServiceTokenVerifierTest` 2 例），随后 `mvn -B clean install -DskipTests`，并解包确认已安装 jar 里没有 IDE 占位类。
  - 引擎 `mvn -B clean test`：汇总行 `Tests run: 1056, Failures: 3, Errors: 16, Skipped: 4`；红的只有基线 4 个类，逐类数字与基线一致（`TaskManagerComponentMultiInstanceTest` 1F/4E、`HistoryDataArchivalIntegrityProperties` 43E、`ProcessDefinitionVersionProperties` 1E、`ProcessInstanceDeletionProtectionProperties` 2F/4E）。
  - 新增或改动的测试：`ClaimPoolMembershipSyncTest` 8、`ClaimPoolTaskLocatorTest` 4、`TaskHoldingResolverTest` 6、`LostEntryTaskServiceTest` 6（D-42 后随经理页签删除）、`ClaimPoolInternalControllerTest` 3、`PortalAssignmentEventClientTest` 3、`MiRoleRowPropertiesTest` 3；`TaskClaimSupportReassignTest` +3、`TaskActionServiceForceUnclaimTest` +1；`TaskOrphanRepairServiceBuRolePoolTest` 改写（原“已有候选人就不动”一例按 WP8b-2 改为“已有池随当前持有人增减”）。
  - Docker 与 Admin → 引擎真实调用**未验证**。

**Portal 后端与前端（2026-11-01 第二段后半已实现，未提交）**
- 引擎补充：
  - 任务 DTO 新增 `poolKind`（`NONE` / `PEOPLE` / `MEMBERSHIP`），由 `util/TaskPoolKinds` 判定。`TaskHoldingResolver` 用的是同一条规则，Portal 只读这个字段，不再自己判断一遍。
  - MI 孤儿修复 `repairOrphanMiRoleTask` 改为一律进候选人池：只有一名持有人时也不再直接 `setAssignee`，依据是“按角色指派 1 人也进候选人池”，这样 D-40 才认得出来。
  - `TaskInfoAssembler` 透出的 `bpmnBusinessUnitId` 照 `TaskAssignmentListener` 的同一规则取：BU + Role 节点没有 `businessUnitId` 时取 `assigneeValue`（老 BPMN）。规则收在 `util/BpmnPoolBusinessUnit`，分派、D-36 过滤、池重算三处共用。
  - 修了一个空指针：`TaskPoolKinds` 遇到未知 / 空 `assigneeType` 时 `Set.of(..).contains(null)` 会抛 NPE，是全量测试暴露出来的。
- Portal：
  - 注解与新字段：`ClaimForceUnclaimAnnotator` 覆盖所有有池的任务，一次批量 `withPortalEntry`；`TaskInfo` 新增 `poolKind`、`assigneeLostPortalEntry`、`canTakeOver`、`reassignTargetUserIds`；`canForceUnclaim` / `canReassign` = Admin 规则 OR 失去准入规则。
  - 认领池列表：`fetchAllClaimPoolTasksPaged` 额外保留“持有人无准入、本人是候选人”的非 BU + Role 任务（D-17），由 `ClaimForceUnclaimAnnotator.heldByUsersWithoutEntry` 筛出。
  - 指派：`TaskReassignComponent` 接受所有有池的任务；目标不在 `reassignTargetUserIds` 时回 400，三语 `portal.reassign_target_not_allowed`。
  - 委托：新增 `EffectiveDelegationRules`。列表、办理权限、委托页三处都改用它；`DelegationRule` 加 `@Transient inactiveReason`；规则状态不改。
  - 经理页签（`SubordinateLostEntryTaskController` / `SubordinateLostEntryTaskComponent` / `WorkflowEngineLostEntryClient`、`GET /tasks/users/portal-entry-search` 与 `PortalUserSearchRepository`）：D-42 取消，连同测试已删除。`UserDirectoryComponentImpl` 恢复只依赖 `UserRepository`；按 id 取标签时不再滤掉已删除用户，变更历史里的离职者仍显示姓名。
  - 引擎的拒绝改用 platform-common `BusinessException` 抛出，由全局处理器按 403 / 400 / 404 / 503 返回：`PortalException` 只在部分 controller 有映射，原写法在这里会变成 500，也是实测发现的。
  - 三语文案：`portal.reassign_target_not_allowed`，放在 platform-common `messages_*.properties`（经理页签的两条随页签删除）。
  - 变更历史：
    - `ChangeType.MANAGER_REASSIGN`，并纳入 `AssignmentChangeTypes`。
    - 引擎事件接收：`POST /internal/task-assignment-events`（`InternalTaskAssignmentEventController`，`ServiceTokenVerifier` 校验，已列入 `PortalEndpointIdentityCoverageTest` 白名单并写明理由）。D-40 释放写 `FORCE_UNCLAIM`、`reason = POOL_MEMBERSHIP_LOST`；D-42 转给经理写 `MANAGER_REASSIGN`、`reason = ASSIGNEE_DEPARTED`；`user_id` 都是 system。请求体两份列表缺一份按空处理。
    - `up_change_history` 新增 `reason` 列：追加在 `00-schema/100-platform-access-model.sql` 末尾，k8s all-in-one 同步；`ChangeHistory`、`ChangeHistoryRecord` 同步加字段。
  - 注释去掉“SYS_ADMIN 可强制释放 / 指派”：`TaskReassignComponent`、`TaskInfo`、`ChangeType`。
- 前端（`frontend/user-portal`）：
  - To Do 行与详情横幅显示“办理人已失去准入”标签和“接手”按钮。行内只放得下两个按钮，接手排在取消认领之后、顶替强制释放的位置；详情横幅三个都有。
  - 指派对话框改用 `reassignTargetUserIds`。
  - 经理页签、转派对话框及其 composable / API / 测试随 D-42 删除，`TodoTab` 回到 `'all' | 'batch'`，`task.lostEntry.*` 文案删除；新增 `views/__tests__/todoPoolTakeOver.test.ts` 守住池任务的接手入口。
  - 变更历史显示 `MANAGER_REASSIGN`（“指派给经理”），以及系统原因“系统释放：办理人不再持有该 BU + Role”“系统指派：办理人已离职”。
  - 委托页标注规则未生效：状态列允许换行，原因不再被截断。
  - `views/tasks/index.vue` 从 726 行降到 673 行：抽出 `TodoAssigneeCell`、`TodoBatchUrgeDialog`，经理页签随 D-42 删除。
  - 三语 i18n 同步；`api/task.ts` 的 SYS_ADMIN 注释已改。

**测试与验证（2026-11-01，D-42 后重做）**
- 测试结果见 WP8 明细表“8b 验证”一行。
- Docker 造数：
  - 正式部署了一个测试 FU“WP8b Pool Check”（用户同意）：DW 克隆 → 改表单与 BPMN → 正常 DW 部署到 Admin → Admin 给 MANAGER 角色授权；脚本 `frontend/scripts/_wp8b-deploy-pool-check-fu.mjs`，幂等，设计有变化才重新部署。节点：Submit（发起人，Portal 发起时自动完成）→ Pool approval（`E2E_FINANCE` / `MANAGER` 认领池）→ Confirm（再给发起人，直派，用于 AC-27）→ End。目录行按约定保留（1.0.0、1.0.1、当前 1.0.2）。
  - AC-26、AC-27、D-40 和变更历史面板都走 Portal 自己的接口（发起、认领、释放、指派、办理、转办）。原先“临时流程不是已发布 FU、Portal 接口回 409”的环境限制已解除。
  - 临时 BPMN（`wp8b-lost-entry-check`，直接部署到引擎）只用来造这个 FU 没有的节点：指定多人（D-17、D-39）和直派给一个两位经理都不可用的人。
  - 脚本 `frontend/scripts/_wp8b-lost-entry-checks.mjs`，可复跑；`SKIP_BACKSTOP=1` 时跳过引擎停机那一段（停机时撤 UBR、停用用户，等定时兜底补上，最长约 5 分钟）。
- 用到的账号与临时改动：
  - developer：池成员，lina 的 Entity Manager。
  - e2e_lina：移出 `E2E_WORKFLOW_SIMULATION`（只失去准入）→ 再由 Admin 停用（离职）；Entity Manager 临时设为 developer。
  - e2e_zhangwei：移出该组；Entity Manager 临时设为 lina（lina 离职后即不可用），没有 Function Manager；先 LOCKED 再解锁，最后在引擎停机时停用。
  - e2e_wangfang：经 Admin 临时授予 FIN / MANAGER，引擎停机时撤销。
  - 一条临时委托规则：developer → zhangwei。
- 用完全部恢复，并由脚本查库确认：成员行、账号状态、经理列、UBR、wangfang 的 BU 成员行（撤 UBR 时被连带删除，已补回）、临时部署（级联删除）、两类流程实例（未结束的先撤回，再 purge 引擎运行与历史数据；Portal 侧按实例 id 删 `up_process_instance`、变更历史、表单数据行等）、通知、委托规则。
- 截图：`frontend/user-portal/verification-screenshots/2026-11-01_wp8b-todo-lost-entry-{1920,1366}.png`（To Do 标注完整可见、接手与“更多”，没有经理页签；review 第一轮后替换原 `2026-11-01_wp8b-todo-lost-entry.png`）、`2026-11-01_wp8b-todo-more-menu.png`、`2026-11-01_wp8b-d17-banner.png`、`2026-11-01_wp8b-change-history-departed.png`（变更历史面板：System、“Reassigned to manager”、原因“the assignee has left”、李娜 → Developer Tester）、`2026-11-01_wp8b-delegation-inactive.png`。原 `manager-tab`、`transfer-dialog` 两张随页签删除。
- dev 库准备：dev 库原先缺 `99-up-change-history-batches.sql` 的两列，导致 Portal 写变更历史全部失败（静默）。已手工补上这两列和新增的 `reason` 列。

**不做（在上面的“不做”之外）：**
- BU 移动后的 HIERARCHY_ROLE 池重算（D-38：HIERARCHY_ROLE 池不重算）。

**已定（用户 2026-11-01，PRD D-37–D-40）**
- Q1 → **D-37**：Admin 通知失败不回滚 UBR，保留 ERROR + `FALLBACK(external)`；引擎定时任务按当前 UBR 全量重算 BU + Role 池兜底，也覆盖不经过 Admin 的变化。
- Q2 → **D-38**：HIERARCHY_ROLE 池不纳入重算。
- Q3 → **D-39**：“分配给具体人的放经理转派，分配给 BU role 的不处理”。未认领、候选人全部失去准入的候选人列表任务进这些候选人的 EM / FM 的页签；BU + Role 池不处理；旧虚拟组任务按成员资格分派，归“不处理”。
- Q4 → **D-40**：撤销某人 UBR 后，他已认领的该 BU + Role 池任务自动释放回池（MI 按角色的行同理），重算时一并处理，定时兜底同样适用；变更历史沿用现有的释放类型，操作人记为系统，能看出是“失去该 BU + Role”而释放。
- **D-42**（用户 2026-11-01）：取消「下属失去准入的待办」页签。直派任务办理人只失去准入不转；离职（INACTIVE / 删除，LOCKED 不算）自动指派给 Entity Manager，不可用则 Function Manager（可用 = ACTIVE + Portal 准入），两位都不可用就留在原处记 ERROR。池任务不变；被认领、池里无人有准入的任务只有持有人离职才转。D-39 改为候选人全部离职才转，取第一个有可用经理的候选人。触发：Admin 停用 / 删除后通知引擎 + 引擎定时兜底。历史 `MANAGER_REASSIGN`、操作人 system、原因“办理人离职”。

**待拍板（D-42 实现时发现）**
- **经理有准入但没有任何 UBR（Portal 权限自助模式）时，转给他的任务他打不开**：`PortalSelfServiceAccessFilter` 只放行权限自助白名单，`/tasks/**`（含 To Do 列表）回 403。D-42 的“可用”只要求 ACTIVE + Portal 准入，这样的经理会被选中，任务到了他名下却看不到。原「经理页签在自助模式打不开」一题随页签取消，换成这一题。
  - A：“可用”再加“至少持有一条 UBR”，这样的经理跳过（接着看 Function Manager，再不行留在原处记 ERROR）。
  - B：保持 PRD 口径，把 To Do 与任务办理接口加进自助白名单（自助模式只看指派给本人的任务）。
  - C：保持现状，预检报表（§10）列出这类经理，切换前补 UBR。
  - 本段按 PRD 原文实现（等同 C），未改。
  - **已定（用户 2026-11-01，D-53）**：照样派给经理，经理申请到 UBR 后即可办理；保持现有实现，不加跳过逻辑。

**待拍板（D-55 实现时发现）**
- **单次委托给一个 BU + Role 时，“被委托人失去准入”怎么算**：被委托方是一个组合，不是一个人。现在已实现的部分两种口径都要：组合里没有准入的成员不能代办（每个成员按自己）；委托人失去准入时整条不生效。未定的是“组合整体”：
  - A：组合里一个有准入的持有人都没有时，算被委托人失去准入，任务页显示“未生效”（每次要向 Admin 查该组合的持有人，再批量判准入）。
  - B：组合不算失去准入，与 D-39“未认领的 BU + Role 池不处理，等有人获得该组合”同口径；没有任何成员能代办时任务仍在委托人名下，委托人照常可办，任务页不加标注。
  - 现状等同 B，未加组合层面的判断与标注。
  - **已定（用户 2026-11-01，D-57）**：B——组合整体不算失去准入，保持现有实现；帮助站委托文章三语已补这句（review 第二轮 Minor 6 改正结尾：组合整体不会标“未生效”，只有委托人失去准入时任务页才显示委托人那一条）。

**已定（D-59 实现时提出，用户 2026-11-02）**
- **持有人自己取消认领、或被强制释放时，委托就结束**：PRD 原先列举的是“释放后他人认领”。实现按“持有一结束即结束”：释放那一刻清委托（操作人记为释放的人），之后谁认领（含原持有人自己）都不恢复。理由：任务回到池里就没有持有人，委托无主；被委托人在无人持有期间也不能代办。用户选“释放即结束”，PRD D-59 已按此改写；帮助站本来就按这个写。
- **引擎完成任务收紧（D-60，用户 2026-11-02）**：任务已有持有人时，引擎只认持有人和生效中的单次委托，其他候选人直调引擎一律拒绝；无人持有的池任务维持现状。原登记在范围外表，转入 WP8b 实现。

**已定（D-60 实现时发现，用户 2026-11-02）**
- **D-60 同口径扩到转办和委托**：有持有人时只有持有人能转办、能委托（D-42 接手的经理因此也能委托）；PRD D-60 已补这一句。
- **引擎退回不校验操作人**：`POST /tasks/{id}/return`（`TaskCompletionService.returnTask`）只要登录就能把任意任务退回到历史节点，Portal 前面有 `canProcessTask`。它还承担发起人的“退回草稿”（My Requests → Draft，`ProcessComponent.returnProcessToFirstStep`），发起人往往不是当前持有人，所以不能直接套 D-60。用户选维持现状，登记范围外表。
- **MI 前置任务的行指派不校验操作人**：`POST /tasks/{id}/sub-table-rows/{rowId}/assign` 不读当前用户，谁登录都能改子表行的处理人（HEAD 既有）。用户选维持现状，登记范围外表。

**后半段的细节（2026-11-01 已定并实现）**
- D-40 落库：`FORCE_UNCLAIM`，`user_id = system`，`reason = POOL_MEMBERSHIP_LOST`，变更历史界面显示“系统释放：办理人不再持有该 BU + Role”。
- MI 孤儿修复单持有人直派改为一律进候选人池（见上）。

### WP8 明细

| 项 | 状态 |
|---|---|
| 8a-1 `PortalEntryService`（platform-security）：账号 ACTIVE 且未删除 + 至少一个 ACTIVE CUSTOM 组（MEMBER，AD / 人工）；状态 LOCKED 无准入，`locked_until` 临时锁不算；批量 `withPortalEntry`。H2 真 SQL 6 例。Portal 登录、SSO、续期、切换工作台改用它（删除 `PortalEntitlementService` 及其测试，拒绝码常量移到 `PortalSessionIssuerService`）；Portal 启动类登记该 bean。dev 库核对：`developer` 与全部 e2e 账号在 CUSTOM 组里，`admin` 不在（WP3 起已非管理员） | 完成 |
| 8a-2 `CurrentUserIdArgumentResolver` 只认凭证，取不到抛 `AuthenticationCredentialsNotFoundException`（全局处理为 401）；删除因此不可达的 18 处 `userId == null` 守卫，`GET /tasks/{id}` 改为无条件校验、`"anonymous"` 兜底删除；Portal 前端不再发 `X-User-Id`（守卫测试） | 完成 |
| 8a-3 删除 Portal 全部 SYS_ADMIN 旁路（D-05）：FU 门禁 `FunctionUnitAccessComponent`（`canAccessFunctionUnit` / `canAuditFunctionUnit` 只按当前角色，合并 `canAuditFunctionUnitAsActiveRole`，角色载荷按用户缓存 5 分钟）、View 可见性与参与过滤（`MainTableViewAccessResolver`、`PortalMainTableViewServiceImpl`）、申请查询 `ProcessApplicationQueryComponent`、记录笔记（`RecordNoteComponent`、`RecordNoteUploadSourceResolver`）、催办 `TaskProcessComponent.canUrgeTask`、登录返回的 `systemAdministrator`；前端 `actionAllowedRoles.ts` 去掉管理员例外 | 完成 |
| 8a-4 只按当前 BU + Role（§7.2）：View 规则只对会话里的 `activeBusinessUnitId` + `activeRoleId`（规则存 id，与 DW 保存一致），不把多条 UBR 的 BU 与 Role 拼在一起；没有当前组合（权限自助模式）什么都看不到。FU 门禁在没有当前角色时回退到全部门户角色：HEAD 既有（与新建申请目录同一方法），只在没有 UBR 的权限自助模式出现，那时 Portal 只开放 Permissions 页 | 完成 |
| 8a-5 直连 API 校验：`/tasks/{id}/history`（`processInstanceId` 须是该任务自己的实例）、`/tasks/{id}/sub-table-data/all` 与 `GET /tasks/{id}` 同用 `requireViewableTask`；转办先判 `canProcessTask`；删除无调用方的引擎 `/tasks/{id}/assign`（`TaskActionService.assignTask`、`TaskAssignmentRequest`、`MultiDimensionalTaskAssignmentProperties`）。实测补一处：FU 或 View 不对当前组合开放时 View 接口原先 500（`FunctionUnitAccessDeniedException` 只在 `ProcessController` 有映射，切换工作区后停在 View 页就会碰到）→ `PortalMainTableViewController` 映射为 403，`PortalMainTableViewAccessDeniedTest` | 完成 |
| 8a 文档：帮助站 View Design 访问控制、Allowed Roles 两页三语与 `llms.txt` / `llms-full.txt` 去掉“System Administrator 总能看到”；`docs/view-access-control-test-guide.md` 期望结果按新规则改写（其种子已在 a46995e9b 删除，文中注明须重建）；skill `view-access-control` 差距清单更新并同步到三份副本 | 完成 |
| 8a-4 认领池按当前工作区（D-36，用户 2026-11-01 选“认领与否都过滤”）：`WorkspaceTaskFilterComponent` 对写明池的 BU + Role 的任务（BPMN BU + Role 节点按 `bpmnBusinessUnitId` + `bpmnRoleIds`，MI 按角色分派的行按 `miBusinessUnitCode` + `miRoleCode`；新旧部署分别存 code / id，两种都认）只在当前组合等于池的组合时显示，已认领也一样；用户在哪个工作区都不持有这个池时（如孤儿修复直接派给了他），BPMN 池任务退回按任务身份显示，不会在所有工作区都消失；MI 行沿用原来的“不靠办理人放行”。To Do 的 Mine 与认领池两路都经这一处。`WorkspaceTaskFilterPoolScopeTest` 5 例，原 MI 按角色测试 5 例不变。Docker：`frontend/scripts/_wp8a-pool-scope-checks.mjs` 让 developer 分别在两个工作区拉 To Do，检查都过，但他名下没有 BU + Role 池任务，等于没测到；dev 里唯一落在其组合（E2E_FINANCE + MANAGER）上的池节点在 MI 子流程里，造数要有 MI 集合数据，没有做（为此发起的一条 `relatedowntable-check` 测试单已撤回）。**这一项的运行验证未覆盖**，只有单测 | 完成（运行验证缺口见左） |
| 8a-6 DW View：BU、Role 都空时照常保存，提示改为“已保存，发布后任何人都看不到”（警告样式，不拦截，与 skill `view-access-control` 一致）；三语 `accessControlHint` / `accessControlBuRoleRequired` 改写、新增 `savedWithoutAccess`；`MainTableViewAccessRulesValidator` 注释改口径；帮助站示意图 `public/guides/dw-view-design.png` 重截（Purchase Request 的 Budget Lines，原图里的 FU 已不在）。截图 `frontend/developer-workstation/verification-screenshots/2026-11-01_wp8a-dw-view-*.png`：保存请求在脚本里被拦截、原样返回，共享的 MI Demo 不被改动（状态与 `updated_at` 前后一致） | 完成 |
| 8a 验证：Portal 后端 `mvn -B clean test` 2248 / 0F / 8E（8E = 基线 3 类；含新增的 403 映射与 D-36 测试）；Portal 前端 vitest 327 个文件只有 `viewDetailForm.test` 1F（既有红）；引擎 `mvn -B clean test` 1019，红只在基线 4 个类且数字与基线一致（第 5 个 `MultiDimensionalTaskAssignmentProperties` 随 `/assign` 删除）；帮助站 `pnpm test` 22 / 22、typecheck 0，截图 `frontend/help/verification-screenshots/2026-11-01_wp8a-help-*.png`；Docker（Portal 前后端、引擎重建）`frontend/scripts/_wp8a-portal-checks.mjs` 13 项全过：无凭证 401、`X-User-Id` 不是凭证、developer（SYS_ADMIN）在 CUSTOM 组能进 / 摘掉后续期 401 且登录 `PORTAL_ENTITLEMENT_DENIED`、View 规则等于当前组合才可见 / 另一工作区 403 / 跨两条 UBR 拼出的组合不可见 / 空规则连 SYS_ADMIN 也看不到 / 只配 BU 不可见；改动过的成员行与规则行用完原样恢复并查库确认 | 完成 |
| review 第一轮 FAIL（3 Major、3 Minor、1 Question）：**M1 Portal 接受 DW / Admin 签发的令牌**（Bearer，HEAD 即如此；准入、当前 BU + Role 都只在 Portal 签发会话时检查）→ `PortalSelfServiceAccessFilter` 对已认证但缺 `portalAccessMode` 的请求回 401 `AUTH_PORTAL_SESSION_REQUIRED`（Portal 签发的令牌一律带它；`/auth/**` 不经 JWT 过滤器，续期不受影响；内部令牌调用不带 JWT，不受影响）；FU / 审计门禁在没有当前角色时只对权限自助会话回退到全部角色，其它一律无（`PortalUserSecurityUtils.isCurrentSessionSelfService`）。**M2 MI 按角色的行在哪个工作区都不匹配时全消失**（本包引入）→ 与 BPMN 池一样退回按任务身份显示（本人是办理人或候选人才显示）。**M3 匿名可读用户目录与 lookup 数据**（HEAD 即如此）→ `/tasks/users/search`、`/tasks/users/summaries`、`/relation-tables/{id}/search`、`/view-fields` 改为必须带凭证；新增守卫测试 `PortalEndpointIdentityCoverageTest`：每个处理方法带 `@CurrentUserId`，或在白名单里写明理由（auth、SSO、健康检查、internal、流程完成回调、自己读身份的成员管理与 BI）。Minor：skill 与帮助 `failAnd` 改为“当前工作区”口径并补 T9（跨 UBR 不拼合）；切换工作区时清 FU 角色缓存；删除只剩测试调用的四参数 `transferTask`、`UserPermissionController` 5 处走不到的守卫、无人引用的 `workflow.notification.assigned_*` 三语文案。Question → 用户 2026-11-01 定：D-36 只管显示，接口不拦（已写进 PRD）。范围外第 2 条顺手改：View 接口遇 FU 停用也回 403。验证：Docker（Portal、帮助站重建）`_wp8a-portal-checks.mjs` 21 项全过（新增：4 个接口匿名 401、DW 令牌两处 401 且带 `AUTH_PORTAL_SESSION_REQUIRED`、Portal 会话仍可搜用户）；`_wp8a-todo-smoke.mjs` To Do 正常加载、无接口报错，截图 `frontend/user-portal/verification-screenshots/2026-11-01_wp8a-todo-smoke.png`；新增 / 改写单测：过滤器 4、FU 门禁 2、D-36 MI 行 1、403 映射 1、守卫 1；Portal 后端 `mvn -B clean test` 2256 / 0F / 8E（8E = 基线 3 类）；帮助站 `pnpm test` 22 / 22、typecheck 0 | 完成 |
| review 第二轮 PASS（0 Blocker、0 Major、4 Minor、1 Question）。Minor 已改：`/auth/me`、`/me/avatar`、`/workspace-contexts`、`/switch-workspace`、`/change-password`、`/validate` 改用 `parsePortalSessionToken`（查黑名单、拒续期令牌、要求 `portalAccessMode`），DW / Admin 令牌与登出过的令牌都不能再换出 Portal 会话（一并修掉范围外第 1 条）；角色缓存里找不到当前角色、且缓存已超过 10 秒时重取一次（多副本下切换工作区后不再等满 5 分钟；只有这一处没写单测，组件没有可注入的时钟）；两处 Javadoc 改口径；`PortalUserSecurityUtils` 改用 `PortalSessionIssuerService` 的常量，不再依赖 controller。Question（老 BPMN 的 BU 写在 `assigneeValue`、没有 `businessUnitId` 时只按角色匹配）→ 引擎分派时就是用 `assigneeValue` 当 BU，`TaskInfoAssembler` 透出时照同一规则补上，随 WP8b 后半段改引擎时做。验证：`AuthControllerRefreshTest` 7 例（新增 Portal 会话可切换、他端令牌不能开会话、登出令牌不能开会话）；Docker（Portal 重建）`_wp8a-portal-checks.mjs` 24 项全过、连跑两次一致（新增：DW 令牌调 `/auth/me` 与 `/switch-workspace` 401、登出令牌换会话 401）；Portal 后端 `mvn -B clean test` 2259 / 0F / 8E（8E = 基线 3 类） | 完成（WP8a review 通过） |
| 8b-1 Admin：UBR 写入点 8 处在提交后通知引擎重算对应 BU + Role 池（`ClaimPoolResyncService` / `EngineInternalClient`，原 `ClaimPoolResyncClient`，只带服务令牌）；推送失败记 ERROR、不回滚 UBR（`FALLBACK(external)`，兜底是引擎定时全量重算，D-37）；守卫测试 `UbrWriteSitesReportClaimPoolsTest` | 完成 |
| 8b-2 platform-security：`LostEntryTaskPolicy`（接手；D-42 起改为离职判定 `departedOwners` / `departedAmong` / `managerFor`）与 `ServiceTokenVerifier`。引擎：池重算 `ClaimPoolMembershipSync` + `ClaimPoolTaskLocator`（BPMN BU + Role、MANUAL 回退、MI 角色行；HIERARCHY_ROLE 不算，D-38）、Admin 推送接口 `/api/v1/internal/claim-pools/resync`、定时兜底 `ClaimPoolResyncScheduler`（D-37）、D-40 自动释放并推给 Portal；`TaskClaimSupport` 失去准入可释放 / 接手 / 指派、指派目标须有准入；孤儿修复改走重算；原下属待办接口与转派随 D-42 删除（见 8b-6） | 完成 |
| 8b-3 引擎补充：任务 DTO 带 `poolKind`（`TaskPoolKinds`，引擎判、Portal 只读）；MI 孤儿修复单持有人也进候选人池（D-40 才认得出）；`bpmnBusinessUnitId` 对老 BPMN 取 `assigneeValue`（`BpmnPoolBusinessUnit`，分派 / D-36 / 重算共用，WP8a review Question） | 完成 |
| 8b-4 Portal 后端：注解与新字段（`assigneeLostPortalEntry`、`canTakeOver`、`reassignTargetUserIds`）、D-17 任务进认领池列表、指派目标须在 `reassignTargetUserIds`；委托三处经 `EffectiveDelegationRules`（D-18，规则不改）；`MANAGER_REASSIGN`；D-40 接收 `POST /internal/task-assignment-events`（服务令牌，守卫白名单写明理由），`up_change_history.reason`（schema 100 末尾 + k8s all-in-one）；三语错误文案；去掉 SYS_ADMIN 注释（`TaskReassignComponent`、`TaskInfo`、`ChangeType`、前端 `api/task.ts`） | 完成 |
| 8b-5 Portal 前端：To Do / 详情横幅“办理人已失去准入”与“接手”、指派对话框只列池内有准入的人、变更历史 `MANAGER_REASSIGN` 与两条系统原因、委托页“未生效”标注、三语 i18n；`views/tasks/index.vue` 726 → 673 行（经理页签随 D-42 删除） | 完成 |
| 8b-6 D-42 离职转给经理：platform-security 判定（离职 = INACTIVE 或已删除，经理先 EM 后 FM、可用 = ACTIVE + 准入，候选人按顺序）；引擎 `DepartedHolderReassignment` + 推送接口 `POST /api/v1/internal/departed-users/tasks/reassign` + 定时兜底 `DepartedHolderReassignScheduler`，结果报 Portal；Admin `updateUserStatus`（INACTIVE）/ `deleteUser` 提交后通知（`DepartedUserTasksService`）；Portal 落 `MANAGER_REASSIGN` / system / `ASSIGNEE_DEPARTED`。删除经理页签全部前后端代码、`portal-entry-search`、两条文案及其测试。PRD §7.3、AC-27 已改写 | 完成，待 review |
| 8b 验证（D-42 后重跑）：Admin `mvn -B clean test` 1162 / 0F / 0E / 2 skipped；platform-security 128 / 0F / 0E（随后 `clean install`，解包确认无 IDE 占位类）；platform-common 588 / 0F / 0E / 21 skipped（删文案后 `clean install`）；引擎 `mvn -B clean test` 汇总 1068 / 3F / 16E / 4 skipped，红只在基线 4 个类且逐类数字与基线一致；Portal 后端 2271 / 0F / 8E（8E = 基线 3 类）；Portal 前端 vitest 330 个文件只有 `viewDetailForm.test` 1F（既有红），vue-tsc 379（基线 381）。Docker（引擎、Admin、Portal 前后端重建）：`_wp8b-deploy-pool-check-fu.mjs` 33 / 33（FU 1.0.2，含 Confirm 节点，Portal 发起 / 认领 / 释放 / 审批 / 办理到 COMPLETED）；`_wp8b-lost-entry-checks.mjs` 60 / 60（连跑两次）：AC-26 经 Portal 接口（有准入时普通成员无操作；失去准入后标注 + 释放 / 接手 / 指派，历史行 FORCE_UNCLAIM / REASSIGN；池外或无准入目标 400）、D-17、D-42 只失去准入不转、LOCKED 不算离职、无令牌 401、AC-27（Admin 停用 lina → 两条 Confirm 自动到 developer 的 To Do，详情 200，历史 `MANAGER_REASSIGN` / system / lina → developer / `ASSIGNEE_DEPARTED`，developer 办理到 COMPLETED，另一条转办给 wangfang 由她办理到 COMPLETED）、D-39（zhangwei 在引擎停机时停用 → 定时兜底把全员离职的候选人列表任务交给 developer，历史无原办理人）、两位经理都不可用时任务留在原处且引擎记 ERROR、D-37 / D-40 定时兜底、D-18；数据恢复并查库确认。WP8a 脚本 `_wp8a-portal-checks.mjs` 24 / 24、`_wp8a-todo-smoke.mjs` 通过。截图见“测试与验证” | 完成，待 review |
| review 第一轮 FAIL（3 Major、7 Minor、1 Question）处置：**M1 AC-26 标注被截断** → `TodoAssigneeCell` 改为办理人与标注上下两行、标注可换行，列宽不动（共享列表 §6.6 不许手调默认宽）；脚本改查“看得见”：每个标注在单元格内且 `scrollWidth ≤ clientWidth`，1920 / 1366 各截一张。**M2 D-17 任务界面上释放不了、详情无横幅** → To Do 行在可接手时显示“接手”加“更多”下拉（释放、指派，各按权限出现）；详情横幅改为“BU + Role 池，或你能释放 / 接手 / 指派”时显示；Portal 释放判定与历史类型从“BU + Role 池”扩到所有池（`BuRolePoolTasks.hasPool`，名单任务被他人释放也记 `FORCE_UNCLAIM`）；查看权限：名单池的成员在持有人失去准入后可打开该任务（`TaskPermissionEvaluator.canViewTaskForm`，持有人仍有准入时不开），打开后在接手前只读（`detail.vue` 的 `claimLocked` 同时看 `canTakeOver` / `canForceUnclaim`）。**M3 帮助站** → To Do 文章与委托文章三语改写（去掉 SYS_ADMIN、去掉“建任务时冻结”；补失去准入时同池成员释放 / 接手 / 指派、标注、离职自动指派给经理与两条系统原因、池随 UBR 变化、委托未生效、行上“更多”菜单与横幅），新增 `#lost-access` 一节，`llms.txt` / `llms-full.txt` 同步；`pnpm test` 22 / 22、typecheck 0、build 通过，帮助站容器重建，截图 `2026-11-01_wp8b-help-todo-*.png`。Minor：1 `restoreBuRoleClaimPool` 遇 Admin 不可达记 ERROR（`FALLBACK(external)`，兜底定时重算），释放不回滚；单任务重算的持有人查询 30 秒内共用（一键取消认领按 BU + Role 只问一次）。2 引擎报来的系统事件在一个事务里整批落库，失败抛出、接口回 500，引擎记带任务 id 的 ERROR。3 引擎按 1000 条分批推送。4 新增 `PoolCodes`：推送、定时、单任务三条路径都在同一处把老 BPMN 的 BU / 角色 id 换成 code（BU 按 id 查 code，角色一次取 BU 绑定角色表；查到即换，查不到原样，结果缓存 10 分钟；推送带来的 id / code 直接登记）。5 旧虚拟组：引擎指派目标改按 `TaskHoldingResolver` 的池（候选人 ∪ 候选组成员 ∪ MI 虚拟组行成员），Portal 注解同口径把 `assignmentType=VIRTUAL_GROUP` 的 `assignmentTarget` 组成员算进池（D-58 后 Portal 这一支已删除，见 review 第二轮一行）。6 D-39 候选人顺序：`ASSIGNEE_FROM_VARIABLE` 节点按变量里的顺序，其余（及变量没列到的人）按用户 id 排序；解析逻辑抽到 `AssigneeUserIdNormalizer.userIdsOfVariable`，分派与重算共用（`TaskAssignmentListener` 826 → 792 行）。7 注释：`ChangeType.MANAGER_REASSIGN`、`ChangeHistory` / `ChangeHistoryRecord` / schema 注释补 `ASSIGNEE_DEPARTED`、引擎 `TaskReassignClaimRequest` 去掉 SYS_ADMIN、本文 `index.vue` 行数改为 673；两个经理列索引已无查询使用，从未发布的 `100-platform-access-model.sql` 与 k8s all-in-one 中删除，dev 库已 `DROP INDEX IF EXISTS`。Question：MI 写入器会给 MI 子流程里任何拿到办理人的节点写 MI 扩展行（非角色模式），BPMN BU + Role 节点在 MI 子流程中单人解析或回退时也会有；dev 库没有这类数据。已统一：`ClaimPoolTaskLocator.locate` 对 BPMN / 手动 BU + Role 任务也批量带上扩展行，定时与推送路径释放时一并清认领状态（测试 `aBpmnPoolTaskCarriesItsExtendedRowOnEveryPath`）。范围外顺手修：删除无调用方、绕过 D-18 的 `DelegationComponent.getActiveDelegationRule`；委托页 “Delegate To” 显示原始 id 是 HEAD 既有，登记范围外。另补 **D-36 运行验证**：developer 在 E2E_FINANCE + MANAGER 工作区 To Do 看得到池任务（未认领与本人已认领），切到 hase-hmdc + HMDC_Index_Role 看不到。验证：platform-security 128 / 0F / 0E；Admin 1162 / 0F / 0E / 2 skipped；引擎 1076 / 3F / 16E / 4 skipped（红只在基线 4 类，逐类数字一致）；Portal 后端 2277 / 0F / 8E（基线 3 类）；Portal 前端 vitest 330 个文件只有 `viewDetailForm.test` 1F（既有红），vue-tsc 379（基线 381）。Docker（引擎、Portal 前后端重建；Admin 本轮未改）：`_wp8b-lost-entry-checks.mjs` 78 / 78（新增：AC-26 标注 1920 / 1366 完整可见；D-17 在已发布 FU 上经 Portal 界面释放（To Do“更多”）、接手与指派（详情横幅）及历史 `FORCE_UNCLAIM` / `REASSIGN`；D-17 详情只读；D-36 两个工作区；D-17 的名单任务是给已发布 FU 的“Confirm”任务经 Flowable REST 加候选人造出的，FU 没有重新部署）；`_wp8b-deploy-pool-check-fu.mjs`（`VERIFY=0`，只核对不建实例）9 / 9；`_wp8a-portal-checks.mjs` 24 / 24；`_wp8a-todo-smoke.mjs` 通过；数据恢复并查库确认。截图：`2026-11-01_wp8b-todo-lost-entry-{1920,1366}.png`、`2026-11-01_wp8b-todo-more-menu.png`、`2026-11-01_wp8b-d17-banner.png`、`2026-11-01_wp8b-change-history-departed.png`、`2026-11-01_wp8b-delegation-inactive.png`。运行观察：引擎把任务转给经理不经过 Portal，经理的 To Do 有最长 15 秒 Mine 列表缓存才出现（脚本按此等待） | 完成，待 review |
| 8b-7 D-55 单次委托失去准入不生效：判定在 `EffectiveDelegationRules`（`markSingleTaskDelegations` / `singleTaskDelegationInForce`，委托人或被委托人无准入即不生效；BU + Role 委托以正在看 / 办的成员为被委托人）；Portal 被委托人列表去掉不生效的、`isSingleTaskDelegatee` 要求生效、详情与列表标注 `delegationInactiveReason`；引擎 `SingleTaskDelegateCheck`（自 `TaskCompletionService` 抽出）代办完成时要求两方都有准入；任务页“委托”一栏显示“· 未生效：…”；帮助站委托文章与 To Do 文章三语同步。BU + Role 组合整体的口径待拍板（A / B，现状 = B）。验证：单测 Portal `EffectiveDelegationRulesTest` +3、`TaskPermissionEvaluatorSingleDelegateTest` +1，引擎 `SingleTaskDelegateCheckTest` 4，前端 `taskDisplayDelegation.test` 2；全量 platform-security 128 / 0F / 0E、Admin 1162 / 0F / 0E / 2 skipped、引擎 1080 / 3F / 16E / 4 skipped（红只在基线 4 类，逐类一致）、Portal 后端 2281 / 0F / 8E（基线 3 类）、Portal 前端 vitest 331 个文件只有 `viewDetailForm.test` 1F、vue-tsc 379；`_wp8a-portal-checks.mjs` 24 / 24；Docker `_wp8b-lost-entry-checks.mjs` 89 / 89，其中 D-55 9 项：developer 经 Portal 把 Confirm 委托给 wangfang → wangfang 在“委托”里看得到、打得开 → 移出 CUSTOM 组后同一会话里看不到、打不开（详情 403），Portal 办理 403、引擎代办 400 → developer 仍在 To Do、详情 `delegationInactiveReason = DELEGATE_LOST_PORTAL_ENTRY`、任务页显示未生效 → 恢复准入后重新可见并代办到 COMPLETED。注：普通（非 MI）任务没有扩展行，引擎拒绝委托（Portal 回 500，HEAD 既有缺陷，登记范围外），脚本按引擎写法给该任务补了扩展行再走 Portal 委托接口。截图 `2026-11-01_wp8b-d55-delegation-not-in-effect.png`、`2026-11-01_wp8b-help-delegation-*.png` | 完成，待 review |
| 8b-8 D-56 只对能委托的任务显示“委托”、D-57 BU + Role 组合不算失去准入：引擎 `util/TaskDelegability` 判定并在任务 DTO 带 `delegable`（`TaskInfoAssembler`），缺扩展行时的报错改为 “This task cannot be delegated”；Portal `TaskInfo.delegable`（`EngineTaskMapper`）、`TaskDelegationGuard` 在委托前拒绝不可委托的任务（400 + 三语 `portal.task_not_delegable`）；任务页按 `delegable` 显示“委托”（`detail.vue` 传给 `TaskActionBar`）；帮助站委托文章三语补 D-56 / D-57。单测：引擎 `TaskDelegabilityTest` 2，Portal `TaskDelegationGuardTest` 2、`EngineTaskMapperDelegableTest` 1，前端 `TaskActionBarDelegate.test` 2。全量：platform-security 128 / 0F / 0E、platform-common 588 / 0F / 0E / 21 skipped（加文案后已 install）、Admin 1162 / 0F / 0E / 2 skipped、引擎 1082 / 3F / 16E / 4 skipped（红只在基线 4 类，逐类一致）、Portal 后端 2284 / 0F / 8E（基线 3 类）、Portal 前端 vitest 332 个文件只有 `viewDetailForm.test` 1F、vue-tsc 379；`_wp8a-portal-checks.mjs` 24 / 24。Docker `_wp8b-lost-entry-checks.mjs` 93 / 93，D-56 4 项：普通任务 `delegable = false`、直接委托回 400 “This task cannot be delegated: delegation is not available for it”（不再 500，也没有写扩展行）、补扩展行后 `delegable = true` 且照常可委托（接着跑 D-55 全部检查）、任务页没有“委托”按钮。dev 里已部署的流程没有任何节点绑定“委托”动作（`dw_action_definitions` 只有一条 DELEGATE，未在已发布 BPMN 中使用），所以界面上“不显示按钮”在 Docker 里只能看到结果，隐藏逻辑本身以组件测试为证；也没有 MI 子任务可试“仍能委托”，以补扩展行的任务代替。截图 `2026-11-01_wp8b-d56-not-delegable.png`、`2026-11-01_wp8b-help-delegation-*-{open,fail,see}.png` | 完成，待 review |
| review 第二轮 PASS（0 Blocker、0 Major、6 Minor、1 Question）处置：**D-58（用户定）旧虚拟组分派的任务不再处理** → Portal `ClaimForceUnclaimAnnotator` 删掉读虚拟组成员的分支（`assignmentType = VIRTUAL_GROUP`、候选组，永远命中不了），池只看 `candidateUserIds`；删掉 `ClaimForceUnclaimAnnotatorTest` 按虚构数据形态写的一例；引擎不动。帮助站 To Do 文章（认领横幅、`#lost-access` 的“池”）三语与 `llms-full.txt` 去掉旧虚拟组说法；本文 WP8b 第 1 条范围、池成员、注解范围与第一轮 M2 / Minor 5 的说法同步收窄。**D-59（用户定，原 Question）换持有人时清委托** → 引擎 `ExtendedTaskInfo.endDelegationUnlessMadeBy` 一处清，`claimTask` / `unclaimTask` 先调它：认领、池内接手与指派（含 Leader / Approver）、取消认领 / 强制释放、D-40 系统释放、D-42 转经理都经过；系统事件带 `endedDelegate`；Portal 新类型 `DELEGATION_ENDED`（`field_name = delegated_to`、`reason = HOLDER_CHANGED`；用户操作在同一批写，系统事件由内部接口写），变更历史三语“委托结束”“委托给 · 委托已结束：任务换了办理人”，旧值显示姓名；帮助站委托文章与 To Do 文章三语、llms 补这条。实现是“释放即结束”（含持有人自己取消认领），与 PRD“释放后他人认领”的措辞差一处，列在上方“待确认（D-59 实现时）”。Minor：2 引擎代办判定的委托人取 `delegatedBy`（缺省才用持有人），测试里持有人 ≠ 委托人；3 读路径修复只排除 MI 按角色的行（`MiRoleRowProperties.parse`），其余带扩展行的 BPMN 池任务照修，有测试；4 `resync(pools)` / `resyncAll()` 清掉 30 秒持有人缓存，有测试；5 脚本清理：委托审计按原因删（`WP8b check` / `WP8b D-55 check` / `WP8b D-59 check`），通知按临时流程 key、测试 FU 的流程 key 与名称、节点名、本次实例 id 删，清理检查同口径；dev 库残留先查准再删：`up_delegation_audit` 8 条（`TRANSFER_TASK`、“WP8b check”，对应任务都已不存在）、`up_notification` 109 条（“Process "wp8b-pool-check-…" has started”），另有 144 条指向已 purge 实例的 “New Task "Submit"” 通知（本脚本历次运行留下，原清理没覆盖到）一并删除；6 D-57 帮助文案改为“组合整体不会标未生效；只有委托人失去准入时，任务页才显示委托人那一条”（三语 + llms）。范围外：第 3 条已改——引擎 Leader / Approver 判定的 BU 改用 `BpmnPoolBusinessUnit`（老 BPMN 的 BU 写在 `assigneeValue`），有测试；第 1 条（任务页委托一栏显示原始用户 id）已登记范围外。实测新发现（HEAD 既有，登记范围外）：引擎完成任务时，任一候选人都能完成已被别人持有的任务（`TaskCompletionService.flowableRuntimeAuthorizesComplete`），直调引擎即可绕过持有人；Portal 会拦（403）。验证：单测 引擎 `ExtendedTaskInfoDelegationEndTest` 4、`SingleTaskDelegateCheckTest` +1、`ClaimPoolMembershipSyncTest` +3、`DepartedHolderReassignmentTest` +1、`TaskClaimSupportReassignTest` +2、`PortalAssignmentEventClientTest` 改；Portal `TaskAssignmentHistoryRecorderPoolReleaseTest` +2、`InternalTaskAssignmentEventControllerTest` 改、`ClaimForceUnclaimAnnotatorTest` −1；前端 `useChangeHistoryFormatting.test` +1。全量：引擎 `mvn -B clean test` 1093 / 3F / 16E / 4 skipped（红只在基线 4 类，逐类一致）；Portal 后端 2285 / 0F / 8E（基线 3 类）；Portal 前端 vitest 332 个文件、2518 例，只有 `viewDetailForm.test` 1F（既有红）；vue-tsc 379；帮助站 `pnpm test` 22 / 22、typecheck 0、build 通过。platform-security、platform-common、Admin 本轮未改，未重跑（WP9 在并行改 Admin）。Docker（引擎、Portal 前后端、帮助站重建；Admin 未动）：`_wp8b-lost-entry-checks.mjs` 105 / 105（最后三次连跑全过），其中 D-59 10 项：lina 有准入时委托给 wangfang（不在候选人里）→ wangfang 在“委托”里看得到 → lina 失去准入后 developer 打开任务页看到“委托给 … · 未生效：委托人已失去 Portal 准入” → 在横幅上接手 → 扩展行委托字段清空、历史 `DELEGATION_ENDED` / developer / wangfang / `HOLDER_CHANGED` → 让 lina 暂时恢复准入（委托若还在就会重新生效）：任务不带委托、wangfang 的列表里没有、Portal 办理 403、直调引擎代 developer / lina 都 400 → 任务页没有“委托给”一行，变更历史显示 “Delegation ended”“Delegated to · Ended: the task changed hands”、旧值王芳；`_wp8a-portal-checks.mjs` 24 / 24；数据恢复并查库确认（运行中任务 3、扩展行 0、本轮审计与通知 0、成员 3）。截图 `2026-11-02_wp8b-d59-{before-take-over,after-take-over,delegation-ended}.png`、`2026-11-02_wp8b-r2-help-*.png`，其余 `2026-11-02_wp8b-*.png` 为复跑重截 | 完成，待 review |
| review 第三轮 PASS（0 Blocker、0 Major、6 Minor）处置，并实现 D-60：**D-60** 引擎 `TaskCompletionService.isCompleteAuthorized` 在任务有持有人时只认持有人和生效的单次委托（细节见第 4 条“已有持有人的任务只认持有人”），候选人、候选组、扩展行指派不再放行；拒绝回 400 `WORKFLOW_VALIDATION_ERROR` “This task is held by another user: only its holder or a delegate in effect can complete it”。同口径改了转办（有持有人时只认持有人）和委托（委托人须是持有人）；退回、MI 行指派在引擎里根本不校验操作人，已定维持现状，见范围外表；D-18 站立委托在引擎里没有路径，PRD 措辞不变。**Minor 1** Portal `TaskAssignmentHistoryRecorder.record(before, after, …)`：操作前带委托、操作后重新读到的任务不再带，才写“委托结束”；删除 `delegateEndedBy`；三处调用方（认领、取消认领 / 强制释放、指派 / 接手）传入操作后重新读到的任务。**Minor 2** `ExtendedTaskInfo.endDelegationUnlessSameHolder`：持有人变了或持有结束就清，只有同一持有人重复认领不清；`ExtendedTaskInfoDelegationEndTest` 改为“同一持有人重复认领保留”“原委托人认领回到池里的任务不恢复”，Portal 对应例子同改。**Minor 3** 引擎 `TaskActionServiceForceUnclaimTest` +2（已认领的行取消认领；未认领的行清委托并保存），Portal `TaskAssignmentHistoryRecorderPoolReleaseTest` 加 UNCLAIM 一例；Docker 加“持有人自己取消认领”。**Minor 4** `reason` 注释（schema 100、k8s all-in-one、`ChangeHistory`、`ChangeHistoryRecord`）与两个事件类说明补上 `HOLDER_CHANGED` / D-59。**Minor 5** 分派相关映射拆到 `assignmentChangeLabels.ts`，`useChangeHistoryFormatting.ts` 308 → 293 行，行为不变。**Minor 6** PRD §7.3 表格第二行、D-17、D-54 注明“D-58 起不含旧虚拟组”。验证：单测——引擎 `TaskCompletionServiceHeldTaskTest` 10（持有人能办、候选人被拒、扩展行不再放行、生效委托能办、未生效委托被拒、代办对象不是持有人被拒、MI 子任务只在持有人完成时回写、无人持有时候选人照旧、无人持有时按扩展行照旧、发起人补持有人后完成）、`TaskActionServiceHeldTaskTest` 5（转办 / 委托）、`TaskActionServiceForceUnclaimTest` +2、`ExtendedTaskInfoDelegationEndTest` 5；Portal `TaskAssignmentHistoryRecorderPoolReleaseTest` 改写并加 UNCLAIM（共 7）、`TaskReassignComponentTest` / `ChangeHistoryBatchTest` 随签名改；Portal 批量“先认领再完成”沿用 `BatchTaskComponentTest`。全量：引擎 `mvn -B clean test` 1111 / 3F / 16E / 4 skipped（红只在基线 4 类，逐类一致）；Portal 后端 2286 / 0F / 8E（基线 3 类）；Portal 前端 vitest 332 个文件、2518 例，只有 `viewDetailForm.test` 1F；vue-tsc 379。Docker（引擎、Portal 前后端重建；Admin、帮助站未动）：`_wp8b-lost-entry-checks.mjs` 115 / 115（连跑两次），新增 10 项（造数 2、D-59 取消认领 6、D-60 2）：lina 委托后自己取消认领 → 扩展行委托清空，历史 UNCLAIM 和 DELEGATION_ENDED 都记在 lina 名下 → wangfang 的列表里没有，直调引擎代 lina 办理 400 → lina 重新持有后委托不恢复；developer（候选人）直调引擎完成 lina 持有的任务 400，提示“held by another user”；lina 经 Portal 办到 COMPLETED。生效委托仍能办理，由 D-55 一项证明（wangfang 代 developer 办到 COMPLETED）。`_wp8a-portal-checks.mjs` 24 / 24；数据恢复并查库确认 | 完成，待 review |
| D-60 的 Portal 门禁（转办在 WP8b 收尾时加，委托在 WP9 第二轮 review 补齐）：Portal `TaskPermissionEvaluator.mayTransfer` / `mayDelegate`（任务有持有人时只认持有人，被委托人不算），`annotateClaimState` 写进 `TaskInfo.canTransfer` / `canDelegate`（`delegable` 仍只表示 D-56 的任务级属性）；`TaskDelegationGuard.requireMayTransfer` / `requireMayDelegate` 返回 403 + 三语 `portal.task_transfer_holder_only` / `portal.task_delegate_holder_only`，直接接口（`/transfer`、`/delegate`）和 complete 带 TRANSFER / DELEGATE 动作两个入口都拦，原先引擎拒绝后 Portal 报 500；任务页按钮：转办看 `canTransfer`，委托看 `delegable && canDelegate`，`TaskActionBar` 两个开关缺省为 false（不传即隐藏）。单测：`TaskProcessComponentTransferTest` 7 例（两种动作 × 两个入口 + 持有人 / 无人持有）、`TaskDelegationGuardTest` 3 例、`TaskPermissionEvaluatorSingleDelegateTest`、`TaskActionBarTransfer.test` 4 例。Docker：`_wp8b-lost-entry-checks.mjs` 120 / 120（新增 3 项：被委托人直接委托 403、经 complete 委托 403，持有人与委托关系不变；任务页被委托人 `canDelegate=false`、持有人 `true`；持有人委托成功由 D-55 一项证明），数据恢复并查库确认；`_wp8a-portal-checks.mjs` 24 / 24 | 完成 |

## WP9 技术方案

**现状（2026-11-01 调查，读当前工作区代码与 dev 库）：**

1. **登录 / SSO / 续期。** `AuthServiceImpl.ensureAdminAccess` 只放 SYS_ADMIN、AUDITOR，登录（`login`）、SSO（`issueSsoSession`）、续期（`refreshLogin`）三处都调它；WP1 起续期已复核账号状态与角色（PRD §8.1 第 4 条对现有两种角色已满足，WP9 只需把 TL 加进去）。`/auth/me`（`getCurrentUser`）不复核。`admin.auth.developer-bypass-role-check` 打开时三处一律放行：dev 的 Admin 容器里 `ADMIN_DEV_BYPASS_ROLE_CHECK=true`（`docker-compose.dev.yml:502` 默认 true），所以 dev 下“非 TL 登录被拒”看不出来。
   - 凭证里的 TECH_LEAD 来自 `UserRoleServiceImpl.EFFECTIVE_ROLES_SQL`：账号 ACTIVE、未删除，且在某个 ACTIVE 的 DEVELOPER 组的 TECH_LEAD 名单上。它只是粗粒度标记，说明“在某个有效团队当 TL”，不说明是哪个团队。
2. **`@AdminAccess` 现状。** 51 个 controller、约 390 个处理方法（WP2 守卫扫描为 390+；本次用脚本按注解规则逐个列出，现状表据此整理）。访问对象：`ADMIN_READ` = SYS_ADMIN + AUDITOR；`WORKSTATION` = 再加 TECH_LEAD、DEVELOPER；`DEPLOYER` = SYS_ADMIN、TECH_LEAD、DEVELOPER（WP7 已按 FU 所属团队收窄）；没有注解的写接口一律 SYS_ADMIN。TL 现在能调的只有 WORKSTATION（用户、BU、角色、虚拟组的 GET，`/task-assignment/**`，`/internal/ap/*`）和 DEPLOYER 的三个接口，但登录本身被拒。
   - 除拦截器外，还有 controller 自己校验 SYS_ADMIN：`AutomationFlowController`、`AutomationFlowRunController`、`AutomationPieceController` 每个方法调 `isSystemAdmin()`；`LdapSyncController` 的 `status` / `auditLogs` 也是。BI 由 `BiAdminAccessGuard.requireAdminUserId` 按凭证角色认 SYS_ADMIN / AUDITOR，`BiDashboardAssignmentController`、`BiDataViewAssignmentController`、`BiGuestTokenController`、`BiAssignmentConfigurationComponentImpl` 在用。所以 AUDITOR 现在连 Automation 与 LDAP 同步记录的读接口也进不去。
   - 守卫 `AdminEndpointPolicyCoverageTest`：`backendCallersStillPassWithTheServiceToken`（Portal / 引擎 / DW 的后端调用必须放行服务令牌）、`developerWorkstationPagesStillReachTheirLookups`、`sensitiveEndpointsHaveTheirExactPolicy`（BI 分配写接口目前断言为 SYS_ADMIN）、`noWriteIsOpenedByAccident`（对非 SYS_ADMIN 开放的写接口只能是查询形态的 POST，或在服务调用白名单里）。
   - 服务调用：`SecurityConfig.ServiceCallAuthenticationFilter` 按 `X-User-Id` 建一个角色为空的身份；拦截器对 `services = true` 的接口见到有效令牌就直接放行，不再判角色。所以“按团队过滤”不能写成“当前用户不是 SYS_ADMIN 就过滤”：Portal 调 `GET /function-units`、`/{id}/access` 等时带的就是普通 Portal 用户。
3. **PRD §8.2 逐行对到接口**（“TL 现状”一列指登录放开后直接调接口的结果）：

| §8.2 行 | 接口（controller） | 现状访问对象 | WP9 目标 |
|---|---|---|---|
| Dashboard | `DashboardController` GET `/dashboard/stats`、`/activities`、`/user-trends` | ADMIN_READ（类） | 公共只读。最近活动 = `admin_audit_logs` 最新 N 条（`DashboardComponent.getRecentActivities`），不过滤 |
| FU 目录：查看 | `FunctionUnitController` POST `/function-units/query`、`/archived/query`、`/deployments/query`（方法 ADMIN_READ）；GET `/{id}`、`/code/{code}/versions`、`/{id}/deployments`、`/{id}/delete-preview`、`/{id}/access`、`/{id}/audit-access`（类 ADMIN_READ + services） | TL 403 | 团队读：列表只列本人所领团队的 FU；按 id / code 的读校验归属。原有 services 保留 |
| FU：Access / Audit Access | POST / PUT / DELETE `/function-units/{id}/access[/{accessId}]`，POST / DELETE `/{id}/audit-access[/{accessId}]` | SYS_ADMIN | 团队写（按 FU id 校验归属） |
| FU：启用、停用、删除（归档） | PUT `/{id}/enabled`、PUT `/batch/enabled`、DELETE `/{id}`、DELETE `/batch` | SYS_ADMIN | 团队写；批量要求每个 id 都属于本人团队，有一个不属于就整批 403 |
| FU：部署 | Admin 页“部署”按钮 POST `/function-units/{id}/deploy`（SYS_ADMIN）；“校验”按钮 POST `/{id}/validate`（DEPLOYER，WP7 已按团队） | | `/deploy` 改为团队写，只认本团队 TL（与 Admin 页可见范围一致）；validate 与 DW 部署的两个接口不变 |
| FU：导入 | POST `/function-units/import`、`/import/preview`（SYS_ADMIN）；DW 部署的 `/function-units-import/import`、`/{id}/deploy`（DEPLOYER） | | 前两个改为“SYS_ADMIN + 任一 TL”，团队规则已在 `FunctionUnitTeamService`（MANUAL 只认所选团队与原团队的 TL 名单、`selectableTeams` 只给 TL 自己的团队），即 WP7 说的“WP9 只需放开接口” |
| FU：Admin 页上的另外两个按钮 | 归档页“恢复” POST `/{id}/restore`；列表“回滚” POST `/deployments/{deploymentId}/rollback` | SYS_ADMIN | 待拍板 Q5 |
| FU：其余写接口 | `purge-runtime-data`、`new-version`、`deprecate`、`deployments` 的 create / execute / cancel、`approvals/*`、`/function-units-import/*` 的 enable / disable / activate / deploy-process / undeploy-process | SYS_ADMIN | 不变（PRD 没列，Admin 前端也不调用，回滚除外） |
| SLA | `SlaPolicyController` POST `/query`（ADMIN_READ）；GET `/{functionUnitCode}/history`、`/jobs`、`/jobs/{jobId}/items`（类）；PUT `/{functionUnitCode}`、POST `/{functionUnitCode}/recalculate`（SYS_ADMIN） | | 读为团队读，写为团队写（按 FU code）。列表 SQL 本身 `FROM dw_function_units`（`SlaPolicyComponent.MAPPING_FROM`，A-26 既有），见 Q11 |
| Relation Table 结构 | `RelationTableStructureController`：GET 列表、`/{id}`、`/{id}/versions`、`/{id}/access`、`/name-available`，POST `/query`（ADMIN_READ）；create / update / delete / deploy / rollback / enabled / portal-visibility / access 的增改删（SYS_ADMIN） | | 读为团队读，写为团队写（按表 id）；新建按请求体里的 FU |
| Relation Table 数据 | `RelationTableDataController`：GET `/tables`、`/function-units`、`/{tableId}`、`/audit-logs`、`/export`、`/search`、`/template`、`/view-fields`，POST `/{tableId}/query`（ADMIN_READ）；add / update / delete / import / status / primary-keys（SYS_ADMIN） | | 同上（按 tableId） |
| Automation flows、runs | `AutomationFlowController` GET 列表、`/{flowId}/export`、`/workspaces`，POST `/query`；`AutomationFlowRunController` POST `/query`、GET `/{runId}`（类 SYS_ADMIN + 方法自查） | TL、AUDITOR 都 403 | 团队读（只读）；写接口与 `SERVICE` 接口不变 |
| User Portal 审计 | `UserPortalAuditController` POST `/security/user-portal-audit-logs/list-query`、GET `/function-units`（ADMIN_READ），经 `UserPortalAuditClient` 转 Portal `/internal/audit-logs/*`；页面不用 POST `/query` | | 团队读（Portal 侧按 FU code 过滤） |
| Admin 操作审计 | `SecurityAuditController` 的 audit-logs 读接口与导出 | ADMIN_READ | 公共只读；policies 写不变；policies 读、compliance-report、anomalies、validate-password 页面不用，不开放 |
| BI：Data View Assignment | `BiDataViewAssignmentController`（读 ADMIN_READ，写 SYS_ADMIN）+ `BiAdminAccessGuard` | | 读为团队读，写为团队写（请求体 `functionUnitId`）；FU code 的来源见 Q11 |
| BI：Audience Assignment | `BiDashboardAssignmentController` 写（SYS_ADMIN） | | 任一 TL 可写，不限（D-15） |
| BI：Dashboard Registry | `BiDashboardRegistryController` 读 | ADMIN_READ | 公共只读；sync / update / status / delete 不变 |
| BI：RBAC Mapping | `BiRbacMappingController` 读 | ADMIN_READ | 公共只读（Q7）；写不变 |
| BU | `BusinessUnitController` POST、PUT `/{unitId}`、PUT `/{unitId}/move`、DELETE `/{unitId}`（SYS_ADMIN） | | 任一 TL 可写；原有删除校验（有子 BU、有成员即拒绝，`OrganizationManagerComponent.deleteBusinessUnit`）不变 |
| BU 下可申请的角色 | `BusinessUnitRoleController` POST / DELETE（SYS_ADMIN） | | 待拍板 Q6 |
| Role：Custom 列表 | `RoleController` POST `/roles`、PUT / DELETE `/roles/{roleId}`（SYS_ADMIN），POST `/roles/query`（ADMIN_READ） | | 写对任一 TL 开放；系统角色本来就对所有人拒绝改删（`RolePermissionManagerComponent` 的 `CANNOT_UPDATE_SYSTEM_ROLE` / `CANNOT_DELETE_SYSTEM_ROLE`），新建只收 BU_BOUNDED（WP5）；query 公共只读；`/permissions`、`/members` 的写不变 |
| BU 成员、UBR | `BusinessUnitController` 成员增删、`UserBusinessUnitRoleController` 增删 | SYS_ADMIN（部分 + services） | 不变（TL 只读，D-13） |
| 审批人、退出 | `ApproverController`（GET ADMIN_READ + services，写 SYS_ADMIN）、`ExitController`（SYS_ADMIN） | | GET 公共只读（保留 services）；写不变（D-19） |
| 团队成员、虚拟组 | `VirtualGroupController` GET（WORKSTATION）、POST `/query`（ADMIN_READ）；写 SYS_ADMIN | | query 公共只读；写不变 |
| 用户 | `UserController` GET（WORKSTATION）、POST `/users/query`（ADMIN_READ）；写 SYS_ADMIN | | query 公共只读；写不变 |
| 环境变量 | `EnvironmentVariableController` GET（ADMIN_READ）；写 SYS_ADMIN | | GET 公共只读 + 脱敏（见第 5 条） |
| 系统配置 | `ConfigController` GET 与 POST `/{configKey}/assess-impact`（ADMIN_READ）；写 SYS_ADMIN | | GET 公共只读 + 脱敏 |
| Automation Pieces | `AutomationPieceController` GET 列表、`/export`，POST `/query`（类 SYS_ADMIN + 方法自查） | TL、AUDITOR 都 403 | 公共只读；import / toggle / delete 不变 |
| LDAP 同步记录 | `LdapSyncController` GET `/status`、`/audit-logs`（类 SYS_ADMIN + 方法自查） | TL、AUDITOR 都 403 | 公共只读（Q8）；四个触发接口不变 |

   上表之外，TL 能看到的页面在加载与只读操作中还会调一些读接口（如 `/approvers/business-units/{id}`、`/approvers/virtual-groups/{id}`、`/business-units/{id}/role-leaders`），各段实现时按前端 `src/api/*.ts` 的调用点逐个列出，一并改为公共只读。其余没有页面使用、PRD 也没列的 ADMIN_READ 接口（`/monitor/**`、`/logs/**`、`/dictionaries/**`、`/permissions/**`、`/approvals/**`、`/permission-requests/**`、`/developer-permissions/**`、`/bi/guest-token` 等）保持 SYS_ADMIN + AUDITOR。

4. **“属于本团队”的判据与查询落点。** 都以 Admin 自己的表为准，不读 `dw_function_unit_dev_groups`，不调 DW：
   - **TL 领哪些团队**：库里没有现成的查询。`VirtualGroupMemberRepository.isActiveUserOnLists`（WP7）只回答“某个组里在不在”，`ApWorkspaceResolver`、`FunctionUnitTeamService` 都是逐组调用。需要新增一条“列出此人当 TL 的 ACTIVE DEVELOPER 组（id、code）”的查询，账号与角色条件与 `ACTIVE_USER_ON_LISTS_SQL` 相同。
   - **FU**：某个 code 的团队 = 最新导入行的 `sys_function_units.team_code`（`FunctionUnitRepository.findTeamCodeOfLatestImport`，排序常量 `LATEST_IMPORT_ORDER`；目录列表已在 `FunctionUnitListQueryComponent`、`FunctionUnitColumnSpec.TEAM_SQL`、`VirtualGroupManagerComponent` 删团队检查里用同一排序）。按 id 的接口：id 是版本行 id，先取 code 再查团队。
   - **SLA**：`ac_sla_policies.function_unit_code`，路径变量 `{functionUnitCode}`，按 FU code 判断。
   - **Relation Table**：`rt_table_function_units.function_unit_id` 指向 `sys_function_units.id`（版本行），所以要经过“行 → code → 最新导入团队”。没有链接的表是 Common（`RelationTableFunctionUnitResolver` 注释写明“visible to all Function Units”）。dev：6 张表，3 张被两个 FU code 共用，0 张 Common。数据页的 `permissionLevel` 已由 `RelationTableDataServiceImpl.resolveCurrentLevel` 给出（Admin 下默认 READ_WRITE，只有显式 READONLY 授权才只读），前端 `useRelationTableData` 按它显示写按钮。
   - **Automation**：flow / run 行带 AP `projectId`，`ApWorkspaceSql` 用 `externalId` 反查 `sys_virtual_groups.id`（团队 project 是 `前缀 + 组 id`，Public 是配置的 externalId）。单条 flow 已有 `resolveByExternalProjectId`。
   - **User Portal 审计**：Portal `UserPortalAuditListQueryComponent` 的 `up_change_history ch LEFT JOIN up_process_instance pi`，FU 条件是 `pi.function_unit_code = ?`，只能传一个 code。按团队过滤只能在 Portal 的 SQL 里做（共享列表要求真分页），所以 Portal 内部接口要能收一组 code。
   - **BI Data View**：`bi_data_view_assignment.function_unit_id` 是 `dw_function_units.id`（有外键）。FU code 只能从 dw 表取：`BiDataViewMetadataRepository.responses` 已 `JOIN dw_function_units` 带出 `fu_code`，`listFunctionUnits`、`BiDataViewAssignmentListQueryComponent` 也都在 dw 表上。见 Q11。
5. **存量 FU 没有团队（C-5）。** dev：7 个 code、33 个版本行，`team_code` 全部为空。`FunctionUnitTeamService.assertMayDeploy` 对没有团队的 FU 只放 SYS_ADMIN。生产上线后、重新导入前，TL 在目录、SLA、RT、BI、User Portal 审计里都看不到任何 FU（Q12）。D-61（用户 2026-11-02）：认领前仍不可见；TL 可以在 Admin 手动导入同 code 的包、选自己的团队把它认领到本队（导入时现有归属为空只看所选团队，WP7 规则不变），作为存量迁移的一种方式。
6. **敏感值现状。**
   - 环境变量：`EnvironmentVariableResponse.fromEntity` 对任何调用者都原样返回 `currentValue`、`defaultValue`、`vaultSecretPath`（VAULT 的值本来不在库里，但路径会返回）。
   - 系统配置：`ConfigController` 直接返回实体 `SystemConfig`（含 `configValue`、`defaultValue`、`encrypted`），`/{configKey}/value` 返回原值，`/history` 返回 `ConfigHistory.oldValue` / `newValue`，都不脱敏。dev 的 `admin_system_configs` 10 项（SMTP / IMAP 主机端口、会话超时等），没有 `encrypted = true` 的；`ac_environment_variables` 0 行。
7. **Admin 前端现状。**
   - 菜单（`AdminLayout.vue`）、路由守卫（`router/index.ts` → `utils/permission.ts` 的 `canAccessRoute`）、写按钮都看权限码：来自凭证里的 `permissions`（`sys_role_permissions`），再加前端写死的 `ROLE_PERMISSION_DEFAULTS` 与 `AUDITOR_DENIED_PERMISSIONS`。dev 库里 TECH_LEAD、DEVELOPER 没有任何权限码，TL 登录后凭证只有 `basic:access`，只能看到 Dashboard。
   - AUDITOR 的只读这样实现：权限码 `audit:read`、`log:read`、`user:read`、`sla:policy:view`。菜单只到 Dashboard、用户、组织、虚拟组、审计两页、SLA。页面按 `hasPermission(USER_WRITE)` 收起写按钮的有 `BusinessUnitTree.vue`（BU 写操作与成员 / 审批人 / 角色三个弹窗共用一个 `readOnly`）、`virtual-group/index.vue` 与成员弹窗、`UserList.vue`；`useRole.ts` 按 `role:write` / `role:delete`；`useSlaPolicies.ts` 按 `sla:policy:edit`。
   - **可以直接复用给 TL 的**：用户列表、虚拟组页与成员弹窗（TL 同样没有写权限）。**要改的**：BU 页（BU 写放给 TL、成员 / 审批人仍只读，现在一个开关管全部）；Role 页（按 Custom / System 区分）。**没有只读形态、因为 AUDITOR 进不去的页面**：系统配置（`config/index.vue` 的两个“保存”按钮不判权限）、环境变量（`EnvironmentVariablesPanel.vue` 6 个按钮不判权限）、Automation 三页、RT 结构 / 数据 / ER 图、FU 目录、BI 四页。
   - **WP2 时说过“若发现缺口记入 WP9”的 Auditor 缺口**：`UserDetailDialog.vue` 的“分配 BU 角色”、设为 Leader / Member、移除 UBR 按钮不判权限，AUDITOR 打开用户详情就能看到（读代码得出，未实跑）。
   - 系统配置页没有菜单项，只能从 `UserProfileDropdown.vue` 的“设置”进入。**Admin 前端没有 LDAP 同步页面**，也没有相关 i18n 文案。同步造成的成员增减按 WP5（D-32）记在 Admin 操作审计里，操作人是 `LDAP_SYNC_JOB`。
   - `getCurrentUser`（`/auth/me`）的结果不写回 localStorage 里的用户，菜单一直按登录时存下的角色与权限显示。
8. **Role 页“角色成员”弹窗**（范围外表里记给 WP9 的那一条）。`RoleMembersDialog.vue` 先拉全部虚拟组，按 `boundRoleId === role.id` 找绑定这个角色的组，再显示该组成员。切换后只有 System Administrators、Auditors 两个组绑定角色，所以 TECH_LEAD、DEVELOPER、所有 Custom 角色都显示“未绑定虚拟组”。后端 `GET /roles/{roleId}/members` 读的是 `sys_user_roles`（`RoleMemberManagerComponent.getRoleMembers`），这张表解析器不认，弹窗也没用它。
9. **引用查找。** `RoleReferenceFinder.find`（WP5）只在删除角色时使用：返回“种类：数量”的提示行，流程与视图会列出 FU 名称，FU Access / Audit Access / RT 访问 / BI 只有数量；有任何引用就拒绝删除。修改角色前没有提示。BU 没有引用查找：删除只拦子 BU 和成员。BU 被引用的地方有：已发布流程的 BU + Role 节点（BPMN 属性 `businessUnitId`，老写法 `assigneeValue`；引擎 `ClaimPoolTaskLocator` 两者都读）、已发布主表视图的访问规则、BI Audience 的 `BUSINESS_UNIT` 目标，FU / RT 访问配置如果有 BU 目标也算（dev 里只有 ROLE 目标）。
10. **dev 库现状（只读查询）。** DEVELOPER 组只有 Public，没有团队，也没有任何 TL；Auditors 没有成员；System Administrators 只有 `developer`；共 10 个账号（`admin`、`developer`、7 个 `e2e_*`）；`ac_sla_policies`、`bi_data_view_assignment`、`up_change_history` 都是 0 行。验证必须临时造团队、账号与数据（见“验证”）。

**关键技术决策（实现时不再问；判据来源写在括号里）：**

1. **调用者模型（单一判定点）。** 拦截器通过角色检查后，把 `AdminCaller` 放进请求属性：
   - 带有效服务令牌并按 `services = true` 放行的请求 → `SERVICE`，不做任何团队过滤。Portal / 引擎 / DW 的调用照旧，WP2 的守卫名单不变。
   - SYS_ADMIN → 读写全部。
   - AUDITOR → 读全部；同时是某团队 TL 的，写只到他领的团队（§4.4“AUDITOR 不增加写权限，也不收回此人独立取得的团队权限”）。
   - 只是 TL → 读写都只到他领的团队。领哪些团队每个请求现查一次库（第 4 条新增查询），不用凭证里的 TECH_LEAD，与 DW 一样“移出名单下一次请求即生效”（§7.1）。
   - 列表过滤、行标记、请求体校验都只读这个对象，不另判“是不是管理员”。
2. **新增四个访问对象**，`ADMIN_READ` 本身不变：
   - `SHARED_READ`：SYS_ADMIN、AUDITOR、TECH_LEAD，公共资源只读；
   - `TEAM_READ`：同上三种角色，TL 只到本人团队；
   - `TEAM_WRITE`：SYS_ADMIN、TECH_LEAD，TL 只到本人团队；
   - `LEAD_WRITE`：SYS_ADMIN、TECH_LEAD，不分团队（BU、Custom Role、BI Audience、FU 手动导入与预览）。
   - 不选“把 TECH_LEAD 直接加进 ADMIN_READ”：那样会一并放开 monitor、logs、dictionaries 等 PRD 没列的接口，而且每个团队资源的读接口都要记得自己过滤，漏一个就看到别队的数据。
3. **按路径的归属在拦截器里判。** `@AdminAccess` 增加 `owner`（`FUNCTION_UNIT_ID`、`FUNCTION_UNIT_CODE`、`RELATION_TABLE_ID`、`AUTOMATION_FLOW`、`AUTOMATION_RUN`、`DATA_VIEW_ASSIGNMENT`）和路径变量名。拦截器从 URI 模板变量取值，交给 `AdminTeamScope` 的对应解析器得出团队。请求体里的 id（RT 新建 / 修改的 FU 链接、FU 批量 ids、BI 的 `functionUnitId`）和列表过滤在组件里调同一个 `AdminTeamScope`。
   - 判据来源：WP6 review 首轮 Blocker（只看 URL 里的 FU id，子资源越权）之后 DW 用 `WorkspaceRouteOwnership` + `WorkspaceRouteCoverageTest` 解决，这里照搬：守卫测试要求每个 `TEAM_*` 处理方法要么声明了 `owner` 且路径里确有这个变量，要么在“列表过滤 / 请求体校验”白名单里并写明理由，白名单里的每一项都有单测。
4. **结果码。** 别的团队的资源，读和写都回 403 `PERM_ACCESS_DENIED`，文案点名缺哪个团队的权限；资源不存在回 404；没有团队、或团队已停用 / 已删除的 FU，文案沿用 WP7 的 `admin.fu.team_*_admin_only`（只有 SYS_ADMIN 能处理）。判据：AC-13、AC-28、AC-32 都写的是 403。
5. **列表过滤一次算好。** 每个请求先算出“本人所领团队的 FU code 集合”：在 `sys_function_units` 上按 code 取最新导入行，`team_code` 落在本人团队里的就算，与 `LATEST_IMPORT_ORDER` 同一口径，`FunctionUnitRepository` 加一个查询。再以 `code IN (…)` 拼进各列表 SQL（FU、部署记录、SLA、BI、User Portal 审计），不在每一行上做子查询。`sys_function_units` 在 code 上只有 `enabled = true` 的部分唯一索引，但目录是几十到几百行的量级，不加索引（performance-guardrails：先量，不预先优化）。
6. **前端按后端给的能力表显示。** 登录、SSO、续期、`/auth/me` 的 `UserLoginInfo` 增加 `adminAccess`（功能 → `NONE` / `READ` / `WRITE` / `TEAM`），由后端一个类 `AdminCapabilities` 按角色算出（Q1、Q2 的结论只写在这里）。团队资源的行上带标记：FU `canManage`、SLA `canEdit`、RT `canWrite`、BI `canEdit`。前端菜单、路由、按钮只读这两样。
   - 不选给 TECH_LEAD 写 `sys_role_permissions` 种子：权限码表达不了“只限本团队”，还要同时改 init-scripts 与 k8s all-in-one；也不选在前端再写一份“角色 → 页面”的表（与后端会漂移）。
   - 守卫：`AdminCapabilitiesConsistencyTest` 为每个功能列出代表性接口，断言能力表的级别与这些接口的访问对象一致。
   - 路由守卫每次调 `/me` 后写回本地用户，菜单跟着当前角色变（被移出名单后不必等重新登录）。
7. **脱敏。** 环境变量：VAULT 类型对非 SYS_ADMIN 不返回 `vaultSecretPath`、`currentValue`、`defaultValue`，另给 `configured`（路径非空）；TEXT 照常返回。系统配置：`encrypted = true` 的项对非 SYS_ADMIN 不返回 `configValue` / `defaultValue`，`/value`、`/history`、`/compare` 同样处理。判据是配置自带的 `encrypted` 标记，不按键名猜（§3.2）。在读接口出口复制出返回对象，不修改托管实体。脱敏是否也对 AUDITOR 生效，见 Q2。
8. **自查清理。** Automation 三个 controller 与 `LdapSyncController` 中对 TL 开放的读方法，删掉 `isSystemAdmin()` 自查（统一由拦截器把关，写方法的自查保持不动）。`BiAdminAccessGuard` 去掉角色判断，只保留“必须已登录、取用户 id”（WP2 时它与统一表重复但无害，现在它会挡住 TL）。

**分段实现（同一工作包、一次上线；每段前后端一起做，可单独验证）：**

**9a 骨架：登录放行、调用者、访问对象、能力表**
- 后端：
  - `service/impl/AuthServiceImpl.java`：`ensureAdminAccess` 加 TECH_LEAD，登录、SSO、续期三处自动生效；四个响应带 `adminAccess`。
  - `dto/response/LoginResponse.java`：`UserLoginInfo` 加 `adminAccess`。
  - `security/AdminAudience.java`：四个新访问对象。
  - `security/AdminAccess.java`、`AdminEndpointPolicy.java`：加 `owner` 与路径变量名。
  - `security/AdminAccessInterceptor.java`：新访问对象的角色表、写入 `AdminCaller`、`owner` 判定。
  - 新增 `security/AdminCaller.java`、`security/AdminTeamScope.java`（领导团队、FU code 集合、各 owner 解析器；本段只放 FU 两种）、`security/AdminOwner.java`、`security/AdminCapabilities.java`。
  - `repository/VirtualGroupMemberRepository.java`：新增“此人当 TL 的 ACTIVE 团队”的 native SQL 常量与方法。
  - `repository/FunctionUnitRepository.java`：新增“最新导入行属于这些团队的 code”查询。
  - `DashboardController`：改为 `SHARED_READ`。
- 前端：
  - `api/auth.ts`：`UserInfo.adminAccess`；`getCurrentUser` 结果写回本地。
  - `utils/permission.ts`：新增 `canRead` / `canWrite` / `isTeamScoped(feature)`，路由表改为按功能。
  - `router/index.ts`（meta 改为功能）、`layouts/AdminLayout.vue`（菜单）、`components/UserProfileDropdown.vue`（“设置”入口）。
- 本段结束时 TL 能登录，只看到 Dashboard。SYS_ADMIN、AUDITOR 的菜单与现在完全一致：Q1 如选“对齐”，在 9b 再放开 AUDITOR。
- 测试：
  - `AdminAccessInterceptorTest`：新访问对象 × 未登录 / SYS_ADMIN / AUDITOR / TL / AUDITOR + TL / 只是 DEVELOPER / 服务令牌。
  - `AdminEndpointPolicyCoverageTest`：`owner` 覆盖规则；对 TL 开放的写接口必须在白名单里。
  - `AuthServiceImpl`：TL 登录 / SSO / 续期通过；只是 Developer、TL 所在团队停用（AC-05）、已被移出 TL 名单时，登录被拒、续期 401。
  - 领导团队 SQL 用 H2 跑真 SQL（只认 TECH_LEAD 名单、ACTIVE 团队、ACTIVE 且未删除的账号、ACTIVE 的角色）。
  - 前端 `permission` 单测。

**9b 公共资源只读，以及不分团队的写（BU、Custom Role、BI Audience）**
- 后端，访问对象调整：
  - 改为 `SHARED_READ`：`UserController` 与 `VirtualGroupController` 的 `/query`；`RoleController` 的 `/query`；`ApproverController` 的 GET（保留 services）；`ForceUnclaimController` 的 `role-leaders` GET（保留 services）；`SecurityAuditController` 的 audit-logs 读与导出；`ConfigController` 与 `EnvironmentVariableController` 的 GET；`AutomationPieceController` 的读与导出（删自查）；`LdapSyncController` 的 GET（删自查）；`BiDashboardRegistryController`、`BiRbacMappingController`、`BiDashboardAssignmentController` 的读。
  - 改为 `LEAD_WRITE`：`RoleController` 的 create / update / delete；`BusinessUnitController` 的 create / update / move / delete；`BiDashboardAssignmentController` 的写。`BusinessUnitRoleController` 按 Q6。
  - `BiAdminAccessGuard` 按决策 8 处理。
  - 9b 实现时按前端 `src/api` 的调用点核对，TL 页面还用到的其它读接口一并列入。
- 后端，脱敏：
  - `dto/EnvironmentVariableResponse.java` 与 `EnvironmentVariableComponent` 的 list / getById 按调用者选全量或脱敏。
  - `ConfigController` / `ConfigManagerComponent` 的读方法对 `encrypted` 项脱敏。
- 后端，引用提示（§8.3，只提示不拦截；删除角色时既有的引用拒绝保持 WP5 原样）：
  - `component/RoleReferenceFinder.java` 增加“按 FU 列出”（FU code 与当前名称，覆盖 FU Access / Audit Access、已发布流程、已发布视图），复用现有扫描；
  - 新增 `component/BusinessUnitReferenceFinder.java`：判据见现状第 9 条，格式在实现时按 WP5 的做法逐项确认；
  - 新增两个接口 `GET /roles/{roleId}/references`、`GET /business-units/{unitId}/references`（`LEAD_WRITE`）。
- 后端，角色成员：新增只读接口 `GET /roles/{roleId}/holders`（`SHARED_READ`，内容按 Q10），实现在 `RoleMemberManagerComponent`；旧的 `/roles/{id}/members` 不动（WP10 清理）。
- 前端：
  - `views/organization/BusinessUnitTree.vue`：拆成“BU 写”与“成员 / 审批人写”两个开关，`BusinessUnitRolesDialog` 按 Q6；
  - `views/user/UserList.vue`、`views/user/components/UserDetailDialog.vue`：UBR 的分配 / Leader / Member / 移除按钮按能力隐藏，顺带补上 AUDITOR 的缺口；
  - `views/role/RoleList.vue`、`composables/modules/useRole.ts`：Custom 可写、System 只读；`views/role/components/RoleMembersDialog.vue` 改用 `/holders`；
  - `views/virtual-group/*`：`readOnly` 改读能力表；
  - `views/config/index.vue`：只读时表单禁用、不显示保存；
  - `views/config/EnvironmentVariablesPanel.vue`：只读时无写按钮，VAULT 显示“已配置 / 未配置”；
  - `views/automation-piece/index.vue`：只读；
  - `views/bi-management/DashboardRegistry.vue`、`RbacMapping.vue`：只读；
  - BU 编辑 / 删除与 Role 编辑 / 删除前弹出“被以下 FU 引用”（新组件 `components/ReferenceNotice.vue`）；
  - 三语 i18n。
- 测试：脱敏（TL、AUDITOR、SYS_ADMIN 三种调用者；接口响应里确实没有路径和值，AC-14 / AC-40）；BU 引用查找（流程新旧两种写法、视图规则、BI 目标）；Role 按 FU 列出；`holders` 三类角色；TL 改删 System 角色 403、改删 Custom 角色成功、建 BU_UNBOUNDED 被拒（AC-13 / AC-23）；TL 调 BU 成员 / UBR / 审批人 / 退出写接口 403（AC-29 / AC-35）。

**9c FU 目录与 SLA**
- 后端：
  - `FunctionUnitController`：按现状表逐个方法加注解与 `owner`；导入 / 预览改为 `LEAD_WRITE`；`/{id}/deploy` 改为 `TEAM_WRITE`；恢复与回滚按 Q5。
  - `component/FunctionUnitListQueryComponent.java`：列表与归档都加团队条件，行上给 `canManage`；`dto/response/FunctionUnitInfo.java` 加 `canManage`。
  - `component/FunctionUnitDeploymentListQueryComponent.java`：按部署记录所属 FU 的 code 过滤。
  - 批量启停 / 删除在 `FunctionUnitController` 入口先逐个校验，任一不属于就整批 403。
  - `SlaPolicyController` 加注解；`component/SlaPolicyComponent.java` 的 query 以 `fu.code IN (…)` 过滤，行上给 `canEdit`（`dto/SlaPolicyRow.java`）。
- 前端：
  - `views/function-unit/components/FunctionUnitListTab.vue`、`FunctionUnitArchiveTab.vue`、`index.vue`、`composables/modules/useFunctionUnit.ts`：行操作看 `row.canManage`，批量只能选 `canManage` 的行，导入按钮看能力表；导入对话框沿用 WP7（只列 TL 自己的团队）。
  - `views/sla-policy/index.vue`、`composables/modules/useSlaPolicies.ts`：`canEdit` 改为按行。
- 测试：
  - 列表 SQL（自己的团队、别队、无团队、已停用团队、换了团队的 FU 按最新导入行判断）。
  - 每个写接口对别队 403、对本队成功（AC-28）；直接调别队 FU 的 Access 403（AC-13）；手动导入 code 为 B 的包被拒（AC-28 第三项，WP7 已有，补 TL 走 Admin 入口的用例）。
  - Portal 服务调用 `GET /function-units`、`/{id}/access` 不受过滤（守卫 + 单测）。

**9d Relation Table 结构与数据**
- 规则：
  - TL 可见 = 至少一条链接的 FU code 属于本人团队；
  - 可写 = 至少一条链接，且全部链接的 FU code 都属于本人团队；
  - Common 表、新建或修改时能否连上别队的 FU，按 Q3；
  - 链接指向没有团队的存量 FU，视同别队；
  - lookup 搜索按 Q9。
- 后端：
  - `RelationTableStructureController`、`RelationTableDataController` 加注解与 `owner`（`RELATION_TABLE_ID`，路径变量分别是 `id` / `tableId`）；
  - `component/RelationTableStructureListQueryComponent.java` 与 `RelationTableDataServiceImpl.getDeployedTables` / `getDeployedTableFunctionUnitGroups`：可见性过滤；
  - `RelationTableStructureServiceImpl` 的 create / update：请求体里的 FU 必须满足可写规则；
  - `RelationTableDataServiceImpl.resolveCurrentLevel`：团队只读时返回 READONLY，前端现有的 `permissionLevel` 显示逻辑直接受益；
  - `dto/response/RelationTableResponse.java`：结构页也带 `permissionLevel`（或 `canWrite`）。
  - DW 部署带入的 RT 结构走 `RelationTableStructureImporter`（导入组件内），不受影响。
- 前端：`views/relation-table/structure/index.vue`、`form.vue`（只读时整表单只读）、`er-diagram.vue`（只画可见的表）、`data/index.vue`（已按 `permissionLevel`）。新建时 FU 选择器只列本人团队的 FU。
- 测试：
  - 四类表：本队独有、本队与别队共用、别队独有、Common；读、写、新建、改链接、数据增删改、导入、导出各一组；
  - 版本行与 code 的换算（链接到旧版本行 id）。

**9e Automation flows / runs、User Portal 审计、BI Data View**
- Automation：
  - `AutomationFlowController` 的 GET 列表、`/query`、`/{flowId}/export`、`/workspaces`，`AutomationFlowRunController` 的 `/query`、`/{runId}`，改为 `TEAM_READ` 并删掉自查。
  - `component/AutomationFlowListQueryComponent.java`、`AutomationFlowRunListQueryComponent.java`：以 `vg.id IN (本人团队 id)` 过滤，Public 按 Q4。
  - 单条 flow / run 按所在 `projectId` 判归属（`AUTOMATION_FLOW` / `AUTOMATION_RUN` 解析器）。
  - 前端 `views/automation-flow/index.vue`、`automation-run/index.vue`：TL 只读，隐藏导入 / 启停 / 删除 / 转让 / 连接检查。
- User Portal 审计：
  - Admin `dto/request/UserPortalAuditListQueryRequest.java` 与 Portal `dto/UserPortalAuditListQueryRequest.java` 加 `functionUnitCodes`（null = 不限，空列表 = 无结果，二者语义分开）。
  - Portal `component/UserPortalAuditListQueryComponent.java` 加 `pi.function_unit_code IN (…)`。
  - Admin `UserPortalAuditController` / `UserPortalAuditClient`：TL 调用时带上本人团队的 code；`/function-units` 下拉在 Admin 侧按同一集合过滤。
  - **会改 user-portal 两个文件，正与 WP8b 并行，开工前先确认 WP8b 已不再改这两个文件。**
- BI Data View：
  - `BiDataViewAssignmentController` 加注解。
  - `listFunctionUnits` 与 `BiDataViewAssignmentListQueryComponent` 按 `fu.code IN (…)` 过滤。
  - create / batch / update 在 `BiDataViewAssignmentServiceImpl` 里取请求体 `functionUnitId` 对应的 code，并校验；update 还要校验原行。code 来自现有的 `BiDataViewMetadataRepository` 查询：把 `requirePublishedView` 的 `COUNT(*)` 改为同时返回 code，不新增 dw 表，见 Q11。
  - `/function-units/{functionUnitId}/tables`、`/views` 同样校验。
  - `sensitiveEndpointsHaveTheirExactPolicy` 里 BI 写接口的断言改为：Data View → `TEAM_WRITE`，Audience → `LEAD_WRITE`。
  - 前端 `views/bi-management/DataViewAssignment.vue`：FU 下拉只有本人团队，行操作按 `canEdit`，看板仍可任选（D-15）。
- 测试：
  - AC-32 四项；
  - Automation 本队 / 别队 / Public；
  - Portal 审计 SQL 的 code 列表（H2 或组件测试）与 Admin 透传。

**9f 收尾与全量**
- 删除前端已无人使用的 `ROLE_PERMISSION_DEFAULTS`、`AUDITOR_DENIED_PERMISSIONS` 与按权限码的旧判断（grep 确认无调用）。
- 守卫补“TL 页面读接口都放行 TL”（照 `developerWorkstationPagesStillReachTheirLookups` 写一份）。
- 范围外表里“Role 页角色成员弹窗”一条改为已处理。
- 开发指南里 Admin 角色说明若有过时内容一并改（旧文档整体归 WP10）。
- 上线清单补一条：生产重新导入存量 FU 之前，TL 在 Admin 看不到任何 FU（C-5）。

**影响链：**
- Admin 后端：拦截器 ↔ 全部 controller。`services = true` 原样保留，Portal / 引擎 / DW 的约 110 个后端调用不受影响（`backendCallersStillPassWithTheServiceToken` 名单不改、仍须全过）。`WORKSTATION` 不变，DW 页面上的选择器不受影响。`DEPLOYER` 与 `FunctionUnitTeamService` 不变，DW 部署链不受影响。
- 前端 ↔ 后端：登录、续期、`/me` 响应加字段（旧前端会忽略它）；列表行加标记字段。
- Portal：只改内部审计接口的请求体（加可选字段，旧 Admin 不传即不限）；Portal 自己的页面与准入不变。
- 引擎、DW：不改。DW 页面在同源下借用 Admin 凭证的行为（WP2 决定保留）不变：TL 的 DW 凭证在 Admin 里与 Admin 凭证同权，团队范围按库现算。
- i18n：后端新文案（团队范围拒绝、引用提示）进 platform-common 三语，改完须 `clean install` platform-common；前端新文案三语。
- 配置 / Docker / K8s：不新增配置项；不改 schema 与种子。

**通用性：** 团队一律按 code 与 ACTIVE 状态从虚拟组记录读取，FU 的团队按最新导入行读取，RT 按链接表读取，Automation 按 project 的 externalId 读取。不按组名、FU 名、表名判断，也不写死任何团队。新导入的 FU 与重新导入后补上团队的存量 FU 走同一条路径；没有团队的存量 FU 一律只给 SYS_ADMIN（AUDITOR 只读）。

**验证：**
- 单测与守卫：见各段，另加：
  - 拦截器决策矩阵扩到 TL 与 AUDITOR + TL；
  - `AdminEndpointPolicyCoverageTest` 的 `owner` 覆盖与 TL 写白名单；
  - `AdminCapabilitiesConsistencyTest`；
  - 各 SQL 用 H2 跑真 SQL（领导团队、FU code 集合、RT 可见 / 可写分类）。
- 全量：
  - admin-center 在模块目录 `mvn -B clean test`；
  - user-portal 全量（基线 8E 三类）；
  - platform-common 改了 i18n 后 `clean install`；
  - Admin 前端 vitest + vue-tsc（基线 134）。
  - 部署打包从仓库根 `-pl backend/admin-center -am`。
- Docker（Admin 前后端重建；9e 另需重建 Portal 后端；与 WP8b 错开容器重建）：
  - 账号与团队（dev 现有数据造不出这些场景）：用 `developer` 经 Admin 接口建临时团队 `WP9 Team A` / `WP9 Team B`（code `WP9_TEAM_A` / `WP9_TEAM_B`）和临时账号：`wp9_tl_a`（A 的 TL）；`wp9_tl_b`（B 的 TL，同时是 A 的 Developer，验 §4.4）；`wp9_dev_a`（只是 A 的 Developer，应登录被拒）；`wp9_auditor`（Auditors 人工成员）；`wp9_auditor_tl`（Auditor 兼 A 的 TL）。不用 `e2e_*` 账号，避免与 WP8b 的脚本互相干扰。用完删除并查库确认。
  - 数据：
    - FU 照 `_wp7-admin-import-checks.mjs` 的导入与清理方式，按团队 A / B 各导入一个，不部署，留 DRAFT 以便删除；
    - SLA、BI、User Portal 审计依赖 dw 里已有、Portal 有历史的 FU（dev 审计 0 行）：以团队 A / B 导入这些 FU code 的新版本行，并写入带标记的临时审计行，用完删除；
    - RT 用 SYS_ADMIN 建四类表（本队、共用、别队、Common）；
    - 环境变量建 TEXT、VAULT 各一个；系统配置建一个 `encrypted = true` 的临时项。
    - 所有临时行用完恢复并查库确认。
  - 登录门禁：临时以 `ADMIN_DEV_BYPASS_ROLE_CHECK=false` 重启 Admin，验证 `wp9_dev_a` 被拒、`wp9_tl_a` 通过、把 `wp9_tl_a` 移出名单后重新登录被拒、续期 401（AC-15 的 TL 部分）、停用团队 A 后同样（AC-05）；验完恢复原值。
  - 接口矩阵：扩展 `frontend/scripts/verify-admin-access-matrix.mjs`，新增 `frontend/scripts/_wp9-admin-tl-checks.mjs`，覆盖 AC-13、AC-14（看接口响应）、AC-23、AC-28、AC-29（直调写接口）、AC-32、AC-35、AC-40；外加 Portal 发起 / 待办、DW Developer 部署各跑一次（服务调用与 DW 部署零拒绝）。
  - 页面巡检：`frontend/scripts/verify-admin-access-pages.mjs` 加 `techlead` 模式，TL 可见页面零意外 401 / 403 / 5xx，SYS_ADMIN 与 AUDITOR 模式结果与 WP2 时一致（AUDITOR 按 Q1 的结论更新期望）。
- 截图（`frontend/admin-center/verification-screenshots/<日期>_wp9-*.png`）：
  - TL 的菜单与 Dashboard，与 SYS_ADMIN 的 Dashboard 并排（最近活动一致）；
  - 用户列表与用户详情（没有 UBR 按钮）、AUDITOR 的用户详情（缺口已补）；
  - 组织页（BU 可建、改、删、移；成员 / 审批人弹窗只读）；
  - 虚拟组列表与成员弹窗（只读）；
  - Role 页（Custom 可写、System 只读）、角色成员弹窗（TL / DEV / Custom 三种）、BU 与 Role 修改前的引用提示；
  - FU 目录（只有本队、行操作）、导入对话框（只能选本队）、SLA；
  - RT 结构列表（可写 / 只读标注）、表单只读、数据页只读与可写、ER 图；
  - Automation flows、runs、pieces（只读）；
  - User Portal 审计（只有本队的 FU，下拉也只有本队）、Admin 审计；
  - BI 四页；
  - 环境变量（VAULT 显示“已配置”）、系统配置只读；
  - SYS_ADMIN 同页对照（写入口仍在）。

**不做：**
- DW、引擎不改；Admin 读 `dw_*` 的既有依赖（SLA、BI，A-26）不在本包消除，也不新增 dw 表的读取（Q11）。
- 不新增“设置所属团队”操作，存量 FU 靠重新导入（C-5）。
- BU、Role 不记录创建团队、不按团队过滤（D-12）；BI Audience 不按团队限制（D-15）。
- 不开放 PRD 没列、页面也不用的 ADMIN_READ / SYS_ADMIN 接口（见现状第 3 条表后的说明）。
- 不新建 LDAP 同步页面（除非 Q8 选 B）。
- Portal 的准入与业务权限不变；D-41（表单 lookup 保持现状）、D-42（离职员工的直派任务自动指派给经理）属于 WP8，不是 WP9 的待办。
- 不处理 FU 页重复弹错（范围外表已记录）。
- `/roles/{id}/members` 等无效旧接口归 WP10 清理。

**需要用户拍板（PRD 没写清，业务语义不同）：**

| # | 问题 | 选项 | 推荐 |
|---|---|---|---|
| Q1 | AUDITOR 的 Admin 范围。现状：前端菜单只到用户 / 组织 / 虚拟组 / 审计 / SLA；后端 ADMIN_READ 允许读 FU、RT、BI、环境变量、配置；Automation 与 LDAP 记录连后端都不让读。WP9 后 TL 能看的页面会比 AUDITOR 多 | A. 保持现状（§9“保持现状的只读范围”），新增的 `SHARED_READ` / `TEAM_READ` 不含 AUDITOR 未开放的那几类；B. AUDITOR = 全平台只读（§4.1），TL 能看的页面 AUDITOR 都能看（团队资源看全部），菜单随之放开 | B |
| Q2 | VAULT 路径、`encrypted` 配置值对 AUDITOR 是否也隐藏 | A. 只对 TL 隐藏；B. 对所有非 SYS_ADMIN 隐藏（AUDITOR 现在能看到 VAULT 路径） | B |
| Q3 | Relation Table 的两个边界：（1）Common 表（不关联任何 FU）对 TL；（2）TL 新建 / 修改时能否同时关联别队的 FU | （1）A. 只读可见；B. 不可见；C. 可写。（2）A. 不能，全部链接都须是本人团队的 FU；B. 至少一个是本队即可（连上别队后这张表立刻变成他只读） | （1）A；（2）A |
| Q4 | Public workspace 的 Automation flows / runs 对 TL | A. 只读可见（与 DW 一致，DW 里所有人都能以 Viewer 进 Public）；B. 不可见 | A |
| Q5 | Admin FU 页上的“恢复归档”“回滚到上一次部署”（PRD 只列了部署、导入、启停、删除） | A. 对本队 FU 开放（恢复是删除的逆操作，回滚属于部署）；B. 只给 SYS_ADMIN，TL 不显示这两个按钮 | A |
| Q6 | BU 下可申请的角色（`/business-units/{id}/roles` 的绑定 / 解绑）算“修改 BU”吗 | A. 算，TL 可写（TL 新建 Custom 角色后要绑到 BU 才能被申请）；B. 不算，与审批人一样只给 SYS_ADMIN（它也决定谁能申请到业务身份，同 D-19 的理由） | A（B 的理由也成立，请定） |
| Q7 | RBAC Mapping：§8.2 的 BI 行写“不开放”，公共资源行与 D-21 写“只读” | A. 只读（以 D-21 为准）；B. 不开放 | A |
| Q8 | LDAP 同步记录：Admin 前端没有这个页面（AC-29 写了“LDAP 同步页面”） | A. 不新建页面，两个 GET 对 TL 开放只读，同步带来的成员增减在 Admin 操作审计页可见；B. 新建只读页面（读 `/ldap-sync/audit-logs`，接共享列表套件） | A |
| Q9 | TL 编辑本队表的数据时，lookup 列指向他看不到的表（别队或 Common） | A. 与 D-41 同口径：能编辑这张表，就能经 lookup 搜索它引用的表（只限 lookup 字段）；B. 引用的表不可见时，lookup 不可用 | A |
| Q10 | Role 页“角色成员”弹窗显示什么 | SYS_ADMIN / AUDITOR 显示 SYSTEM 组成员（不变）；TECH_LEAD / DEVELOPER 显示各有效团队对应名单的人并带团队列。Custom 角色：A. 显示 UBR 持有人（用户、BU）；B. 不显示成员，提示“业务角色的持有人见组织 → BU” | 前两种按上面做；Custom 选 A |
| Q11 | BI Data View、SLA 的 FU code 来源。这两个模块的列表与校验都建在 `dw_*` 上（A-26），按团队过滤必须先由 dw 的 id 得到 FU code | A. 用模块现有 dw 查询带出的 code，再对到 `sys_function_units` 的团队：不新增 dw 表，不读 `dw_function_unit_dev_groups`，既有依赖不加深（生产没有 DW 时这两页本来就依赖 dw 行，这一点不变）；B. 先把这两个模块改为读已发布版本（另立项），在此之前 TL 不开放 BI Data View 与 SLA | A（这一条与“运行时不读 dw_*”的原则相关，必须由你定） |
| Q12 | 没有团队的存量 FU（生产上线后、重新导入前的全部 FU）对 TL | A. 不可见（C-5“只有 SYS_ADMIN 能管”，§8.2“非本队不可见”）；B. 只读可见 | A |

**已定（用户 2026-11-01，PRD D-43 – D-52）**：Q1 AUDITOR 全平台只读（D-43）；Q2 对所有非 SYS_ADMIN 隐藏（D-44）；Q3 Common 表对 TL 只读，TL 只能关联本队 FU（D-47）；Q4 只读可见（D-48）；Q5 开放（D-49）；Q6 算修改 BU、TL 可写（D-50）；Q7 只读，以 D-21 为准（D-52，推定）；Q8 **新建只读页**，只有 SYS_ADMIN 能触发同步（D-51，不是方案的推荐项，9b 要加这个页面）；Q9 与 D-41 同口径（D-52，推定）；Q10 Custom 角色显示 UBR 持有人（D-52，推定）；Q11 沿用现有查询带出的 FU code 再对团队（D-46）；Q12 不可见（D-45）。

**补充（用户 2026-11-02，PRD D-61 – D-63）**：RT `/search`、`/view-fields` 维持现状（能进 RT 数据页即可调用，D-52 补充）；没有团队的存量 FU 可由 TL 手动导入同 code 的包认领到本队（D-61，改写 D-45 的管理口径，认领前仍不可见）；别队 FU 的 code 与名称出现在 BU / Role 引用提示、共用关联表链接、Admin 审计里是接受的（D-62）；LDAP 同步记录的失败信息对 TL / AUDITOR 原样显示（D-63）。

### WP9 明细

| 项 | 状态 |
|---|---|
| 9a 骨架：`AdminAudience` 加 `SHARED_READ` / `TEAM_READ` / `TEAM_WRITE` / `LEAD_WRITE`，各访问对象的角色集收进枚举（`admitsAnyOf`、`teamScoped`）；`@AdminAccess` / `AdminEndpointPolicy` 加 `owner` + `ownerVariable`；新增 `AdminOwner`（FU id / code / 部署记录 / RT id / flow / run / Data View 分配 / BI 的 FU id）、`AdminCaller`（拦截器写入请求属性：服务令牌放行 = SERVICE，否则 = 令牌或 X-User-Id 的用户与角色）、`AdminTeamScope`（唯一判定点：领导团队每请求查一次库 `VirtualGroupMemberRepository.findTeamsLedBy`，FU code 集合 `FunctionUnitRepository.findCodesOfTeams` 按最新导入行；SYS_ADMIN / 服务读写全部，AUDITOR 读全部、写只到本人领的团队；无团队 / 团队停用 / 已删除各有文案；资源不存在交给 handler 404）、`AdminCapabilities`（功能 → NONE / READ / TEAM / WRITE，角色叠加取高）；拦截器对团队访问对象 + `owner` 的接口在 handler 前按路径变量判定，403 `PERM_ACCESS_DENIED` 点名团队；`AuthServiceImpl.ensureAdminAccess` 放行 TECH_LEAD，登录 / SSO / 续期 / `/me` 都带 `adminAccess`；Dashboard 改 `SHARED_READ`。前端：`permission.ts` 只读能力表（`canRead` / `canWrite` / `isTeamScoped` / `canAccessRoute(route)`），路由 meta 改 `feature`（+ `write`），菜单、设置入口按能力表；`getCurrentUser` 写回本地用户，存储用户带版本号让菜单 computed 随之刷新 | 完成，待 Docker 验证 |
| 9b 公共资源与不分团队的写：`SHARED_READ` = 用户 / 虚拟组 / 角色 query、审批人 GET（保留 services）、role-leaders、Admin 审计读与导出、系统配置与环境变量 GET、Automation Pieces 读与导出（删自查）、LDAP 状态与记录（删自查）、BI 看板库 / RBAC / Audience 读；`LEAD_WRITE` = Custom 角色增改删、BU 增改移删、BU 可申请角色绑定 / 解绑（D-50）、BI Audience 写；`BiAdminAccessGuard` 只取当前用户。System 角色改 / 删对所有人改为 403（`AdminForbiddenException`，AC-13）。脱敏（D-44，对所有非 SYS_ADMIN）：环境变量 VAULT 不返回路径 / 值、另给 `vaultConfigured`；系统配置 `encrypted` 项在列表 / 单项 / 分类 / 环境 / 历史 / 环境对比里去掉值，`/value` 403。引用提示：`PublishedContents`（共用已发布流程 / 视图扫描）、`RoleReferenceFinder.findForNotice`（按 FU code + 当前名称，覆盖 FU Access / Audit Access / 流程 / 视图，其余为计数行）、新增 `BusinessUnitReferenceFinder`（BPMN `businessUnitId` 与旧 `assigneeValue` 两种写法、视图 BUSINESS_UNIT 规则、FU 访问 / RT 访问 / BI Audience 的 BU 目标、UBR、可申请角色）；`GET /roles/{id}/references`、`GET /business-units/{id}/references`（LEAD_WRITE），`GET /roles/{id}/holders`（SHARED_READ，`RoleHolderQueryComponent`：SYSTEM 组成员 / 各有效团队名单 / UBR）。LDAP 同步记录只读页（D-51）：`POST /ldap-sync/audit-logs/query`（新 `LdapSyncRecordController`，不随 LDAP 开关消失）+ `LdapSyncAuditColumnSpec` / `LdapSyncAuditListQueryComponent`，前端 `views/ldap-sync`（共享列表套件，§6.7.3 已登记 required）挂在审计菜单。前端：BU 页拆“BU 写 / 成员审批人写”两个开关，可申请角色跟 BU 写；用户详情 UBR 按钮按 `organizationMembers` 隐藏（补 AUDITOR 缺口）；Role 页、虚拟组、系统配置（表单禁用、无保存）、环境变量（无写按钮，VAULT 显示已配置 / 未配置）、Automation Pieces、看板库、RBAC、Audience 按能力表；BU / Role 编辑弹窗与删除 / 跨父级移动确认里显示 `ReferenceNotice`；角色成员弹窗改用 `/holders`；三语 i18n | 完成，待 Docker 验证 |
| 9c FU 目录与 SLA：FU 的 query / archived / deployments 查询 `TEAM_READ` 并在 SQL 里按 code 过滤，行上 `canManage`；按 id / code 的读 `TEAM_READ` + `owner`（保留 services）；启停 / 删除 / 恢复 / 部署 / Access / Audit Access 增删 `TEAM_WRITE` + `owner`；回滚按部署记录 owner；批量启停 / 删除先逐个校验、任一不属于整批 403；导入 / 预览 `LEAD_WRITE`（团队规则仍在 `FunctionUnitTeamService`）；`/deployed/latest` 按可见 code 过滤并带 `canManage`（RT 表单只许选本队 FU）。SLA：query 按 `fu.code IN` 过滤（code 取自模块原查询，D-46），行上 `canEdit`，写与 history / jobs 带 FU code owner。前端：行操作 / 启停开关 / 批量可选行按 `canManage`，导入按钮按能力表，Access 弹窗只读模式；SLA 编辑 / 重算按行 `canEdit` | 完成，待 Docker 验证 |
| 9d Relation Table：结构列表 SQL 只留 Common 与本队链接的表（`appendVisibleRelationTables`），左栏只列本队 FU，行上 `canWrite`（全部链接都是本队才可写，Common 只 SYS_ADMIN 写）；ER 图的全量列表同样过滤；详情带 `canWrite`；新建 / 改链接必须至少一个、且全是本队 FU（`requireLinkableFunctionUnits`）；数据页可见性同口径，`resolveCurrentLevel` 团队不可写即 READONLY；结构与数据的单表读写全部带 `RELATION_TABLE_ID` owner。修复：`PUT/DELETE /relation-tables/structures/{id}/access/{accessId}` 原先不校验授权行属于路径里的表（团队放开后可越权改别队授权），现在服务按表 id 校验。前端：结构列表编辑变“查看”、部署 / 回滚 / 删除 / 启停按 `canWrite`，授权弹窗、版本弹窗只读模式；表单按 `canWrite` 整页只读，FU 选项只能选 `canManage`，Common 选项对团队范围用户禁用 | 完成，待 Docker 验证 |
| 9e Automation / User Portal 审计 / BI Data View（Admin 侧）：flows / runs 查询 `TEAM_READ`，SQL 按工作区组 id（本人团队 + Public，D-48）过滤，单条 export / run 带 owner，删自查；flows 页对只读用户隐藏导入 / 启停 / 转让 / 删除且不加载导入目标。User Portal 审计：`list-query` 与 `/function-units` 改 `TEAM_READ`，Admin 请求体加 `functionUnitCodes`（由 Admin 写入，浏览器传的值被覆盖；null = 不限，空 = 无结果），下拉在 Admin 侧过滤。BI Data View：query / 下拉按 code 过滤、行上 `canEdit`；新建 / 批量 / 修改校验请求体 `functionUnitId`，修改 / 启停 / 删除带分配 owner，表 / 视图下拉带 BI FU owner；code 经 `BiDataViewMetadataRepository` 两条新查询取自模块已在用的 `dw_function_units`（不新增 dw 表，D-46）；前端新建按钮、操作列按能力表与 `canEdit`。Portal 两个文件（2026-11-02 WP8b 收尾后改）：`dto/UserPortalAuditListQueryRequest.java` 加 `List<String> functionUnitCodes`；`component/UserPortalAuditListQueryComponent.java` 的 COUNT 与分页共用条件里 null 不加、空列表 `AND 1=0`、否则 `pi.function_unit_code IN (?, …)`（`UserPortalAuditListQuerySqlTest` +3）。旧 Portal 会忽略该字段，Admin 侧另有越界行整页拒绝（见 review 第一轮），Admin 与 Portal 同版本上线（范围外表） | 完成 |
| 9f 收尾：前端 `ROLE_PERMISSION_DEFAULTS`、`AUDITOR_DENIED_PERMISSIONS`、`hasPermission` 等按权限码的判断全部删除（grep 无残留）；守卫 `techLeadPagesStillReachTheirReads`；`AdminEndpointScan` 带 LDAP 开关扫描（原先 `@ConditionalOnProperty` 的 `LdapSyncController` 不在守卫里）；“Role 页角色成员弹窗”问题已由 `/holders` 处理（范围外表按要求未改）；开发指南未发现过时的 Admin 角色说明。上线清单待补：生产重新导入存量 FU 之前 TL 在 Admin 看不到任何 FU（C-5、D-45）；Admin 与 Portal 同版本上线（User Portal 审计按团队过滤依赖新 Portal） | 完成（两条已并入范围外表） |
| 与方案的出入：`GET /automation/flows`（页面不用）与 `/automation/flows/workspaces`（只给导入 / 转让对话框）保持 SYS_ADMIN；lookup 的 `/search`、`/view-fields` 按 D-52“与 D-41 同口径”实现为能进 RT 数据页即可调用、不按被引用表判定（Q9 表述里的“只限 lookup 字段”未单独校验）；角色持有人放在新组件 `RoleHolderQueryComponent`；BI Data View 的 FU code 用两条新查询而非改 `requirePublishedView`；新增部署记录 owner；403 异常统一为 `AdminForbiddenException`（团队范围与平台角色） | 记录 |
| 单测阶段验证（2026-11-02）：platform-common `mvn -B clean install` 588 / 0F / 0E；admin-center 模块目录 `mvn -B clean test` 1218 / 0F / 0E / 2 skipped（新增：拦截器 WP9 矩阵与 owner 判定、`AdminTeamScope` 16 例、领导团队 / 团队 code / RT 链接 / RT 可见性 H2 真 SQL、TL 登录 / SSO / 续期与停用团队 / 移出名单（真角色解析 SQL）、能力表一致性守卫、owner 覆盖与 TL 写白名单与 TL 页面读守卫、脱敏 TL / AUDITOR / SYS_ADMIN 序列化断言、BU 引用 H2、Role 按 FU 列出、持有人三类 H2、System 角色 403、各列表团队过滤）；Admin 前端 vitest 23 文件 / 101 例全过，vue-tsc 134 = 基线。未跑 user-portal、引擎的 mvn | 完成 |
| review 第一轮 FAIL（2 Blocker、3 Major、6 Minor、4 Question）处置：**Blocker 1** `POST /configs/{key}/assess-impact` 去掉 `@AdminAccess` 改 SYS_ADMIN 专用（`SecretMaskingTest` 补 AUDITOR / TL 403 与守卫断言）；**Blocker 2** Portal 两个文件已改，Admin 侧团队范围调用者拿到的页里只要有一行 `functionUnitCode` 不在可见集合 → 记 ERROR 日志并 502 `UPSTREAM_SCOPE_NOT_APPLIED`（新 `UpstreamScopeNotAppliedException`、i18n `admin.audit.up_scope_not_applied` 三语）；**Major 1** 审计快照统一经 `AuditSecrets` 掩码后落库（`AdminAuditAspect`、`SecurityAuditComponent` 三处），读取路径（详情 / 列表 / 按用户 / 按资源 / 导出）再掩一次覆盖存量；dev 库实查：USER 审计 167 行，含 `passwordHash` 等密钥键 0 行，`change_details` 21 行均为纯文本，不改库；**Major 2** RT 授权跨表：服务层（`RelationTableAccessServiceImpl`）与拦截器层（`RelationTableTeamScopeControllerTest`，MockMvc + 真拦截器 + 真 `AdminTeamScope`）都断言拒绝且无 save / delete；**Major 3** controller 层用例（`TeamScopeFixture`）：AC-32 绑别队 FU 403 / 本队成功 / 服务未调用，AC-28 批量混别队整批 403 不改，D-47 新建与改链接连别队 FU 403，User Portal 审计浏览器传的 `functionUnitCodes` 被覆盖与越界拒绝；**Minor** RT 结构列表 `'View'` 改 `t('common.view')`；续期后 `saveUser(response.user)` 写回 `adminAccess`（`main.ts` 的 refresher）；守卫补团队资源路径只能 TEAM_* 或列明例外（`teamResourcesReachTechLeadsOnlyTeamScoped`，例外 6 条带理由）、owner 与路径变量语义对表（`ownerVariablesSitWhereTheirOwnerIsNamed`）、扫描遇重复 key 直接失败、第二个路径变量的 9 个团队接口逐条登记服务层归属校验（`secondPathVariablesOfTeamEndpointsAreReviewed`）；`RoleHolderQueryComponent` 与 `UserRoleServiceImpl` 同口径（只算 ACTIVE 未删用户、`ra.valid_from / valid_to`、停用角色无人持有），未知角色类型回 400 `ROLE_TYPE_UNKNOWN`；BPMN 属性解析不依赖属性顺序与引号（`PublishedContents.properties`，两个 finder 共用）。**Question**：D-52 补充、D-61（`FunctionUnitTeamServiceTest` / `FunctionUnitImportTeamTest` 各 +1）、D-62、D-63 由用户 2026-11-02 定 | 完成 |
| 第一轮修复后验证（2026-11-02）：platform-security / platform-common 仓库根 `clean install` EXIT=0；admin-center 模块目录 `mvn -B -o clean test` 1254 / 0F / 0E / 2 skipped；user-portal 全量 2293 / 0F / 8E（基线 2290 / 0F / 8E，+3 为新增审计 SQL 用例；8E 仍是 ProcessStartBatchFirstOutcomeTest 6、ProcessOperationProperties 1、RecordNoteAcceptanceDatabaseTest 1）；Admin 前端 vitest 23 文件 / 101 例全过，vue-tsc 134 = 基线，build 通过 | 完成 |
| Docker 验证（2026-11-02，Admin / Portal 后端仓库根 `-pl … -am clean package` 后重建，Admin 前端重建）：`frontend/scripts/_wp9-admin-tl-checks.mjs` 造临时团队 `WP9_TEAM_A/B`、5 个 `wp9_*` 账号、FU 目录行（含 2 个 dw 有已发布视图的 FU 的 99.0.0 行）、四类 RT、TEXT / VAULT 环境变量、加密配置、临时看板与 User Portal 审计行；接口矩阵 138 / 0（AC-13、14、23、28、29、32、35、40，D-43、44、45、47、61，User Portal 审计含浏览器传值被覆盖与 Portal 直调对照，服务令牌调用与 DW Developer 部署零拒绝）；临时以 `ADMIN_DEV_BYPASS_ROLE_CHECK=false` 重启 Admin 跑登录门禁 15 / 0（AC-05、AC-15 的 TL 部分，续期 401），验完恢复 `true` 并确认容器环境；页面巡检 `verify-admin-access-pages.mjs` 新增 `techlead` 模式，techlead / auditor / sysadmin 各 24 页零意外 401 / 403 / 5xx；截图 `_wp9-admin-tl-shots.mjs` 24 / 0，44 张存 `frontend/admin-center/verification-screenshots/2026-11-02_wp9-*.png`。验证中发现并修复：RT 结构表单对不可写的表，字段表格里显式 `:disabled` 盖过外层 `el-form` 的只读（Element Plus 2.13 显式值优先），改为 `fieldLocked(row)`，Lookup / Computed 编辑器也传只读。Automation：dev 没有任何 flow（0 / 0），D-48 只验到查询放行。临时数据全部清理并查库确认（各计数回到验证前）；回归 `_wp8b-lost-entry-checks.mjs` 117 / 117、`_wp8a-portal-checks.mjs` 24 / 24 | 完成 |
| review 第二轮 FAIL（0 Blocker、1 Major、7 Minor）处置：**Major** D-60 委托同口径，见 WP8 明细“D-60 的 Portal 门禁”一行；**Minor** `TaskActionBar` 用 `withDefaults` 定 `canTransfer` / `canDelegate` 缺省 false，测试改为真不传；complete 带 TRANSFER 动作补单测；Dashboard 最近活动的 description 走 `AuditSecrets.maskJson`（`DashboardComponentRecentActivityTest`）；WP8b 明细补门禁一行；`_wp9-admin-tl-checks.mjs` cleanup 补删 `sys_member_change_logs`、LEFTOVERS 多一项，查库确认此前两次运行留下的 2 行（07:27、07:28 UTC，EXIT，用户和 BU 均已不存在）后删除，脚本头写明 gate 前后怎样切换和恢复旁路，gate 开头检查旁路已关；RT 只读表单标题改“查看表结构”三语，`FieldLookupEditor` 的 `disabled` 落到入口按钮、弹层和内部表单；`ROLE_TYPE_UNKNOWN` 文案改走 i18n `admin.role.type_unknown` 三语。帮助站配套：LDAP 同步记录页、表结构列表、表结构表单三个 `?`（`ldap-sync-guide-link`、`rt-structure-guide-link`、`rt-structure-form-guide-link`，三语 aria）；ER 图 Save 原先对图上所有带外键的表逐张 PUT，改为只 PUT 外键有变化的表，只读用户不显示 Save，要写的表里有任一张不可写也不显示（`erDiagramSave.test` 3 例）；TL 新建 / 编辑 RT 时 Function Unit 必填（占位文字与校验提示三语，`useTableStructureForm.test` +2）。验证：platform 仓库根 install EXIT=0；admin-center 1255 / 0F / 0E / 2 skipped；user-portal 2298 / 0F / 8E（基线三类）；Admin vitest 24 文件 / 106 例，vue-tsc 134；Portal vitest 2522 例（仅基线 viewDetailForm 1F），vue-tsc 379；两端 build 通过；仓库根打包后重建 Admin、Portal 前后端与 help 前端；Docker：matrix 138 / 0、gate 15 / 0（验完恢复旁路）、截图 31 / 0（50 张，含 3 个 ? 打开帮助文章、只读表单标题、ER 图 Save、TL 新建 RT 的 FU 必填）、页面巡检 techlead / auditor / sysadmin 各 24 / 24；临时数据与 `sys_member_change_logs` 查库为 0，Activepieces project 无新增 | 完成 |

## WP10 技术方案

方案定稿于 2026-11-03，Q1–Q5 由用户确定（D-64 已写入 PRD）：

| # | 决定 | 落点 |
|---|---|---|
| Q1 | 不建 `view_admin`；SYS_ADMIN 无旁路（AC-25）的场景用 `developer`；其余 `view_*` 测试账号进 E2E CUSTOM 组 | 10-7 |
| Q2 | AC-16 按“代码零引用 + 切换后库里零残留（断言 A-06）”验收；种子里的 TEAM_LEAD 文本按只增不改保留，每次初始化由 04 删除 | — |
| Q3 | 选 A：无调用方的旧 Admin 接口整族删除，连同 Portal `ROLE_ASSIGNMENT` 旧链路；盘点加一项列出待审批的这类申请 | 10-5、I-15 |
| Q4 | D-64：自建 BU_UNBOUNDED 角色切换时直接删除（连同授权、引用），代码连类型一起删 | 10-3、10-4 |
| Q5 | WP10 提供带校验的切换配置模板 | 10-6 |

- **BU_UNBOUNDED 数据用新增的 `90-post-seed/05`，不改 04：** init-scripts 只增不改；dev 已执行过 04（04 有“已执行即跳过”的判定），改 04 对已切换的库不生效；删除与 04 的其它步骤无依赖，05 幂等，任何库上都能直接执行；`00-init-all.sh` 的 post-seed 循环已含 `05-*`，全新初始化自动执行。05 是一个 DO 块：先删 7 张无外键的规则表（`sys_function_unit_access`、`sys_function_unit_audit_access`、`dw_function_unit_access`、`dw_main_table_view_access`、`rt_table_access`、`admin_data_permission_rules`、`bi_dashboard_assignment`）里按 id 或 code 指向这类角色的行和 `bi_rbac_mapping`，再删角色（其余授权级联删除），最后在约束仍含 BU_UNBOUNDED 时把 `chk_role_type` 收紧为 `ADMIN / AUDITOR / DEVELOPER / BU_BOUNDED`。收紧放在 05 而不放在 schema 段：已有环境的执行顺序是 100 → 04 → 05，删除之前存量行会让 ALTER 失败。`up_permission_request` 的申请记录、BPMN 与动作配置里的角色 code 不改写（属于各团队的设计，改已发布内容还要重算 checksum），由盘点 / 预检列出。
- **运维脚本**放在 `deploy/maintenance/platform-access-model/`（PRD §10：盘点与切换配置是运维脚本，不进 init-scripts）。01–03 只读，整体包在 `START TRANSACTION READ ONLY … ROLLBACK` 里；纯 SQL，psql 与图形客户端都能跑，每项一个结果集，首列为条目名：
  - `01-inventory-before-switch.sql`：在旧版本库上跑，判据照抄 04 / 05，预测切换结果（I-01～I-16）；
  - `02-precheck-after-switch.sql`：切换、配置、首次 AD 同步之后按新规则预检（P-01～P-10），判据与 `PortalEntryService`、`UserRoleServiceImpl` 一致；
  - `03-switch-assertions.sql`：A-01～A-14 为切换阶段，必须全部 PASS 才能启动新服务；A-15、A-16 为配置阶段，只告警；
  - `10-switch-configuration.template.sql`：按环境复制填写（团队、CUSTOM 组、两个 SYSTEM 组的 AD 组与人工成员）。先全量校验，一次报出全部问题后整体拒绝；通过后缺的新建、已有的只改 AD 组，成员写 MANUAL 行；可重跑，第二次 0 变更。这些改动不进 Admin 操作审计，脚本与输出作为记录保留。
- **不做：** 不改已有 init-scripts SQL；不删 `members`、`sys_user_roles`、`admin_permission_*` 等空表；不改写指向被删角色的 BPMN、动作配置与已发布快照；不处理 `up_permission_request` 的历史记录；引擎 `AssigneeType.fromLegacyCode` 把旧写法解析为空的那一处保留（AC-24）；盘点 / 预检 / 断言只读，不修数据；不改写历史性文档（`docs/specs/*`、`docs/verification/*`、ADR 正文等）。

### WP10 明细

| 项 | 状态 |
|---|---|
| 10-1 引擎：删除 `DeveloperWorkstationEmailTemplateClient` 及其测试（`getTemplate` 从未被调用，发邮件读已发布快照，`ConfiguredEmailSender` 改为直接取模板内容）；删除只有反射测试调用的 `resolveDwFunctionUnitRef`，以及 `AdminCenterClient` 里 3 个无调用方的旧取人方法（`getUsersByUnboundedRole`、`getUsersByVirtualGroupCode`、`getBuUnboundedRoles`） | 完成 |
| 10-2 DW：删除 `MemberController` 一族（controller、service、repository、实体、3 个 DTO、测试；契约测试与路由守卫豁免同步删，`members` 表保留）；删除 `DeployRequest.conflictStrategy`（后端从不读取；前端类型与 7 个部署脚本同步）。DW 前端：分派面板与动作角色目录不再拉取、列出 BU_UNBOUNDED 角色，三语删 3 个相关 key；Automation 只读提示里的 “Team Lead” 改为“团队的 Technical Lead 或 Developer 名单” | 完成 |
| 10-3 D-64 数据：新增 `90-post-seed/05-platform-access-model-drop-bu-unbounded-roles.sql`（见上）；k8s `init-platform-seed/all-in-one-for-gui.sql` 在 04 段之后原样追加，两份 README 同步 | 完成 |
| 10-4 D-64 代码：Admin `RoleType`、`EntityTypeConverter`、`RoleHelper`、三个列表列定义、`UserPermissionService`（删“无界角色不需 BU”的分支）、`TaskAssignmentQueryService` / `TaskAssignmentController`（删 `/roles/{roleId}/users`、`/virtual-groups/by-code/{code}/users`、`/roles/bu-unbounded`）、`FunctionUnitAccessService`、`UserPortalMembershipService`、`RoleHolderQueryComponent`、BI Data View 分配；Portal 会话签发与 `/auth` 不再带 unbounded 角色，权限摘要删 `buUnboundedRoles`，`BpmnParserService`、`ProcessStartAssigneeResolver` 删 `BU_UNBOUNDED_ROLE` 分支；Admin、Portal 前端类型与三语；platform-common 删 7 个无人使用的文案 key，platform-security 测试数据。引擎 `AssigneeType.fromLegacyCode` 把 `BU_UNBOUNDED_ROLE` / `VIRTUAL_GROUP` 解析为空，保留（AC-24） | 完成 |
| 10-5 Q3 旧接口：Admin `PermissionController` 整个删除（check、user、conflicts、delegations，以及第一轮 review 后删的 GET `/`、`/{id}`、`/resource/{resource}`：无调用方，且因 `Permission` 实体与表不一致一直 500，见范围外表）；`RoleController` 删 `/{id}/members*` 与 `/{id}/history*`（`/holders`、`/references` 保留）；删除 `PermissionConflictComponent`、`PermissionDelegationComponent`、`RoleMemberManagerComponent`、`PermissionHelper` 及其实体、仓库、DTO、枚举，`RolePermissionManagerComponent` 只留角色与权限维护；`AdminEndpointPolicyCoverageTest` 同步（调用方清单与写白名单删 `POST /roles/{}/members/{}` 和三个 task-assignment 接口；扫描下限改为 350，实际 379）。Portal：删 `POST /permissions/request-role`、废弃的 `POST /permissions/request`（第一轮 review 后删，它仍能建出 `ROLE_ASSIGNMENT` 申请）与 `ROLE_ASSIGNMENT` 审批分支，`RoleAccessComponent.assignRoleToUser` 一并删除（它是旧 `/roles/{id}/members` 的唯一调用方）。枚举值保留：存量待审批的这类申请没有审批人，批准和驳回都回 “You do not have permission to approve this request”，状态仍为 PENDING，只有申请人能撤回（`DELETE /permissions/requests/{id}`）；盘点 I-15、预检 P-07 会列出，切换前请申请人撤回，申请人已无法登录的在 P-07 签字时登记为已说明的差异，不手工改库。写入的 `sys_user_roles` 不参与角色解析，所以删除不改变任何人的有效权限 | 完成 |
| 10-6 运维脚本 01 / 02 / 03 / 10（见上）。验证全部在一次性容器里做：dev 库只读执行；切换前副本（还原自 WP3 前的 dump）上 01 的结果与 WP3 当时的记录一致；合成数据上“预测 = 实际”：名单迁移、团队 code、AD 映射、待审批申请、TEAM_LEAD 引用逐项一致，两个拒绝场景的列表与 04 的报错一致；D-64 演练：两个 BU_UNBOUNDED 角色在 16 张表里清零，BU_BOUNDED 对照角色原样保留，约束收紧后直接插入被拒，05 重跑无变更；配置模板：错误输入一次报出 9 类问题并整体拒绝，空模板 0 变更，填好的配置第一次生效、第二次 0 变更，之后 A-15、A-16 PASS | 完成 |
| 10-7 View 访问测试种子（Q1）：新增 `deploy/init-scripts/91-view-access-test/02-view-access-test-access-model.sql`（手工执行，不登记进初始化；单事务、幂等，切换前后都能跑）。目标单元改为 Multi-Instance Subtask Demo（原来的 MCY 在 dev 与全新初始化里都没有主表 View），View 按名称匹配；4 个 `view_*` 账号进 `E2E_WORKFLOW_SIMULATION`，另建两个 BU_BOUNDED 测试角色。`frontend/scripts/verify-view-access-control.mjs`：每个账号单独的浏览器上下文，只用 cookie 会话；`developer` 与 `view_allowed` 恰好看到 Meeting、People，其余三个账号的菜单里没有该单元；截图改为 Portal Views 页与 DW 中 Meeting 的访问配置。`docs/view-access-control-test-guide.md` 重写 | 完成 |
| 10-8 旧文档：`developer-workstation-workspace-rbac.md` 标为“已被取代”；按现行规则修订 `portal-permission-self-service.md`、`portal-bu-rbac.md`、`shared-list-components.md`、`portal-task-single-delegate.md`、`docs/design/README.md`、`docs/README.md`、FU 开发指南（删 §7.14 MemberController，重写 §8.5 等）、SLA 指南、OCR 示例、`20-email-inbound-reply/README.md`、x-ray 7 个文件、AP 需求与决策记录 | 完成 |
| 10-9 本文：新增“上线 / 切换清单”一节；范围外表里 WP10 的各行改为已处理，标“上线清单”的行注明对应步骤；登记 WP9 第三轮 review 与本包 Docker 验证发现的范围外问题 | 完成 |
| 单测（JDK 17，模块目录 `mvn -B clean test`）：引擎 1103 例（删 8 例；红的仍是基线 4 类，逐类次数一致）；DW 后端 1506 / 0F / 0E / 8 skipped（删 16 例）；DW 前端 vitest 191 个文件 / 1250 例全过，vue-tsc 160（基线）；platform-common / platform-security 仓库根 `clean install`：588 / 0F / 0E / 21 skipped、128 / 0F / 0E；Admin 1191 / 0F / 0E / 2 skipped（基线 1256，删 65 例：`PermissionHelperTest` 38、`PermissionCheckConsistencyProperties` 7、`BuUnboundedRoleImmediateEffectProperties` 4、`TaskAssignmentQueryServiceTest` 8，另有 6 个类共 8 例）；Admin vitest 24 个文件 / 107 例，vue-tsc 134（基线）；Portal 2298 / 0F / 8E（基线三类）；Portal vitest 2522 例（只有基线 `viewDetailForm` 1F），vue-tsc 379（基线） | 完成 |
| Docker 验证（2026-11-04）：仓库根 `-pl … -am clean package`（引擎、Admin、DW、Portal；解包确认没有 IDE 占位类，删掉的类不在 jar 里），三个前端 build，重建 7 个容器。dev 执行 05（没有角色可删，只收紧 `chk_role_type`）→ 03 切换阶段 A-01～A-14 PASS、A-15 WARN（dev 没有 AD，与之前相同）。`_wp9-admin-tl-checks.mjs` setup → matrix 138 / 0 → cleanup（LEFTOVERS 全 0）；`verify-admin-access-matrix.mjs` 27 / 27；`verify-admin-access-pages.mjs` 中 sysadmin / auditor / techlead 各 24 / 24，portal 13 / 13，developer 2 / 2（截图另存为 `wp10-access-*`，原有的 `wp2-access-*` 已还原）。ER 图 Save 经 Admin 界面实测（三张临时表）：带设置的 LOOKUP 字段 `lookup_config` 不变，复合外键 `["k1", "k2"]` 不变，新画的外键写入，只 PUT 被改的那张表；重新部署后图上两条线都在（截图 `frontend/admin-center/verification-screenshots/wp10-er-lookup-{1,2,3}-*.png`；跑了 3 次，2 次 13 / 13，另 1 次脚本拖拽没落在目标上，没连线也没保存）。View 种子 + `verify-view-access-control.mjs` 5 个场景全过（截图 `2026-11-04_portal-views-*.png`、`2026-11-04_view-design-access-control-mi-demo.png`），验完删除种子数据，查库与执行前逐项一致。`_wp8a-portal-checks.mjs` 24 / 24，`_wp8b-lost-entry-checks.mjs` 120 / 120。DW 部署（AC-22）：经 DW `/deploy` 重新部署常驻测试单元 WP8b Pool Check（请求仍带旧字段 `conflictStrategy`），SUCCESS，目录新增 1.0.3，团队仍为 Public；之后再跑 8b 回归（跳过 backstop）115 / 115。DW 设计器打开数字信贷的 `Task_DocumentVerification`（旧 `VIRTUAL_GROUP` 分派）无报错，分派类型显示原值。01 / 02 / 03 在 dev 再跑一次：除 A2 段新增或改列的 I-08、I-15、I-16、P-07、P-10 外，与 A 段结果一致；P-10 报出数字信贷种子 5 个动作的 allowed roles 指向 dev 中不存在的角色（见范围外表）。未验证：带模板的邮件发送（dev 没有任何邮件连接和模板）；DW Automation 非 Public 团队的只读提示截图（dev 只有 Public 团队）。开发旁路 `ADMIN_DEV_BYPASS_ROLE_CHECK` 全程为 `true`，没有跑 gate。环境：临时表、`view_*` 账号与角色、种子写入的规则全部删除并查库确认；与开始时相比，只有 `admin_audit_logs` +188（审计记录，保留）、WP8b Pool Check 新版本（`sys_function_units` +1、`sys_function_unit_access` +1）和 `chk_role_type`（05 收紧）不同 | 完成 |
| review 第一轮 PASS（0 Blocker、0 Major、6 Minor），6 条全部已改：**Minor 1** `ROLE_ASSIGNMENT` 旧链路：删 Portal 废弃的 `POST /permissions/request`（`PermissionController.submitRequest`、`PermissionComponent.submitRequest`、只被它用的 `PermissionRequestDto`、Portal 前端 `permissionApi.submitRequest` 与 `PermissionRequestDto` 类型；全仓含脚本、文档、Kong / nginx 无调用方），删除对应的 4 个属性测试；不新增 SYS_ADMIN 驳回能力（主会话定：保持“申请人撤回”）；10-5、I-15（01 注释）、P-07（02 注释）、上线清单 P2 / W9 按实际行为改写，`PermissionApprovalComponent.canApproveRequest` 的过时注释同步。**Minor 2** `03-switch-assertions.sql` A-04 条件改为 `x.code IS NULL OR x.role IS NULL OR NOT (…)`：dev 上回滚事务里插入指向不存在组的绑定后 A-04 为 FAIL，正常库仍 PASS。**Minor 3** 删除 Admin `PermissionController` 剩下的 3 个接口（整个类）、只被它用的 `PermissionRepository.findByResource` 与文案 `admin.permission.not_found_by_id`；Admin 前端删 `permissionApi`（`getTree`、`getByRole`）、`Permission` 类型、store 的 `permissions` 与 `fetchPermissionTree`；`Permission` 实体与 `/roles/{id}/permissions` 不动（实体与表不一致登记在范围外表）；守卫测试无需改（其中没有 `/permissions` 条目），扫描下限 350 仍满足。**Minor 4** `docs/view-access-control-test-guide.md` §5 拆为 5.1 执行前快照（`\copy` 4 个 View 的开关、`updated_at` 与规则，并检查种子的行此前不存在）、5.2 撤销（单事务：规则与开关按快照还原，删种子建的 4 个账号及其成员关系、UBR、登录记录、2 个角色、3 条 BU 角色绑定、功能单元访问）、5.3 只回到种子状态；只用种子自己的固定 id。dev 上按指南快照 → 种子 → 撤销，快照与执行前一致；另在一次性库里先放 3 条规则和一个开关再走一遍，规则（含 id）与开关原样还原。**Minor 5** 帮助站 RT Rollback 三语改为“外键、Lookup 设置、主键生成规则和公式会被回滚清空，回滚后须重新设置”，恢复清单补上 DECIMAL 的精度和小数位（按 `RelationTableDeployServiceImpl.rollback` 核对）；`llms-full.txt`、`llms.txt` 同步（CRLF 保持）。**Minor 6** `permission.ts` 过时注释随 `submitRequest` 删除；三语删 `portal.org_unit_id_required`；01 / 02 测试账号清单去掉 `view_admin`；`developer-workstation-workspace-rbac.md` 第 90 行去掉 “BU_UNBOUNDED 仅虚拟组”；10 模板的报错改为 `string_agg(DISTINCT …)`，两个团队名生成同一 code 时 “CUSTOM group … is also listed as a team code” 只出现一次（回滚副本反例实测，改前两次）。验证：platform-common / platform-security 仓库根 `clean install` 588 / 0F / 0E / 21 skipped、128 / 0F / 0E；Admin 1191 / 0F / 0E / 2 skipped（无变化）；Portal 2294 / 0F / 8E（删 4 例，8E 仍是基线三类）；Admin vitest 107、vue-tsc 134；Portal vitest 2522（只有基线 `viewDetailForm` 1F）、vue-tsc 379；help `pnpm test` 22 / 22、vue-tsc 0；三个前端 build 通过。仓库根 `-pl backend/admin-center,backend/user-portal -am clean package` 后重建 Admin、Portal 前后端与 help 容器，部署的静态文件与本地 build 逐个 md5 相同（help 102、Admin 122、Portal 165）。Docker：被删接口 Admin 25 个（含 `/permissions` 3 个与 `GET /permissions/check`、`POST /permissions/delegations`）、DW 5 个、Portal 2 个（`/permissions/request`、`/permissions/request-role`）全部 404；保留的 4 个 Admin 接口 200；Portal 业务单元角色申请照常：可选角色、提交（PENDING）、我的待办列出、页面显示、申请人撤回（截图 `frontend/user-portal/verification-screenshots/2026-11-05_wp10r1-portal-permissions.png`）；帮助页显示新 Rollback 文案、旧文案在部署包里为 0（`frontend/help/verification-screenshots/2026-11-05_wp10r1-help-rt-rollback.png`）；A-04 正反例如上；`_wp8a-portal-checks.mjs` 24 / 24，`verify-admin-access-matrix.mjs` 27 / 27（`_wp9` setup 建账号，跑完 cleanup，LEFTOVERS 全 0）。开发旁路全程 `true`，没跑 gate。环境：探针的 2 条申请、wp9 临时数据与其 1 条登录记录、postgres 容器里的临时文件都已删；与开始时相比只多了 `admin_audit_logs` +34 与常驻账号（developer、e2e_lina）的 16 条登录记录 | 完成 |
| review 第二轮 PASS（0 Blocker、0 Major、1 Minor）：第一轮 6 条 Minor 与 `git rm --cached` 误操作核实已处理；Docker 13 项（接口探针 72 / 0、A-04 七个用例、指南 §5 实走与一次性库演练、wp9 matrix 138 / 0、访问矩阵 27、页面巡检、wp8a 24、wp8b 120、界面截图 12 / 12）。Minor 已改：x-ray `modules/admin-center.md` 的 RoleController / PermissionController 描述与 finding #3、`PROJECT-X-RAY.md` 的权限冲突行与 Admin journey / Phase 3 缺口、`audit/testing-and-quality.md` 的 PermissionController 标注已删除；Portal `PermissionRequestType` 注释：`ROLE_ASSIGNMENT` 标为不再新建、存量只能由申请人撤回，三个废弃类型去掉“Use ROLE_ASSIGNMENT instead” | 完成 |
| review 第三轮（只审第二轮之后的增量）PASS（0 Blocker、0 Major、3 Minor），3 条已改：x-ray 的 LDAP 同步描述统一为“只读记录页在 `/audit/ldap-sync`；`ldap.enabled` 与 `ldap.sync-enabled` 都打开时启动时与按 `ldap.sync-cron` 定时同步，SYS_ADMIN 可经四个 `POST /ldap-sync/…` 触发接口手动发起，页面没有触发入口”（`PROJECT-X-RAY.md` 模块表、功能矩阵、Admin journey、方法说明；`modules/admin-center.md` 路由表补 `/audit/ldap-sync`、Observation、`LdapSyncRecordController`、§3 标题、finding #2；`/role` 行改为 holders 只读）；`architecture/database.md` 三张 `admin_permission_*` 表注明 WP10 后无代码使用；`PermissionRequestType.TEMPORARY` 注释写明废弃的 `/permissions/renew` 仍会新建且无人能审批；范围外表 `/roles/{id}/members` 一行补“WP10 已删” | 完成 |
| review 第四轮（只审第三轮之后的增量）PASS（0 Blocker、0 Major、3 Minor），3 条已改：LDAP 同步开关写全为 `ldap.enabled` + `ldap.sync-enabled`，手动触发写成四个接口（`PROJECT-X-RAY.md` Admin journey、`modules/admin-center.md` Observation 与 §3 正文，§3 去掉 “off in dev” 并注明 ShedLock 只锁定时同步、启动那次不锁；`authentication-authorization.md` 状态行同步）；`architecture/database.md` §5 改为 admin-center 有 14 张 `admin_*` 中 11 张的实体；`modules/admin-center.md` 模块图 `ROLE[Role + holders]` | 完成 |

## 第二阶段（2026-11-06 起）

用户 2026-11-06 定：补测第一阶段没实测的项，并修掉范围外表里能在代码里修的遗留问题（WP11–WP19，见工作包表）。WP1–WP10 先不提交，接着做。

- **不改**（按用户原决定）：Portal lookup 搜索不按 `rt_table_access`（D-41）；停用 / 锁定最后一名管理员不拦；引擎退回、MI 行指派、无人持有任务的认领 / 转办不校验操作人；存量 FU 可被任一团队 TL 经导入认领（D-61）。
- **代码里修不了**（生产环境与运维项，已在上线清单）：生产 ConfigMap 有效期、生产关 `ADMIN_DEV_BYPASS_ROLE_CHECK`、四个服务同一个 `SERVICE_INTERNAL_TOKEN`、preprod Secret 换密钥、上线顺序、开放后重新导入存量 FU 与告知 TL、Admin 与 Portal 同版本。
- **先出方案再实现**：WP13 文件存储、WP14 服务令牌。其余工作包照常连续推进，每包 code review PASS 后做下一包。

### WP11 结果（2026-11-06）

报告：会话 scratchpad `wp11a-report.md`、`wp11b-report.md`。脚本 `frontend/scripts/_wp11a-*.mjs`、`_wp11b-*.mjs`；截图 `2026-11-06_wp11a-*.png`（20 张）、`2026-11-06_wp11b-*.png`，在各前端 `verification-screenshots/`。

| 项 | 结果 |
|---|---|
| 第二套环境 env2（独立 compose，用 dev 现有镜像，Docker 全新初始化） | 一次跑完，`90-post-seed/04`、`05` 均执行，切换断言 A-01～A-14 全部 PASS；入口 `http://127.0.0.1:23000`，容器已停、卷保留（重启方法见 wp11a 报告第七节） |
| AC-38 跨环境导入（dev ↔ env2） | PASS。同名团队 code 一致、导入与 DW 部署自动归入；缺团队时 SYS_ADMIN / TL 手动导入、DW 部署所用导入接口、DW 导入都提示先建团队；停用团队提示先启用；没有自动建团队、没有归到 Public。“DW 部署到没有该团队的环境”单环境构造不出，改为直接调 DW 部署所用的导入接口 |
| AD 同步（env2 + 本地 Samba AD 域控，协议级实测；**企业真实 AD 仍未验证**） | PASS：上线清单 W5→W8 全流程；AC-08、AC-09、AC-10；团队两份名单、CUSTOM 组、两个 SYSTEM 组的映射同步；D-29 去掉映射删 AD 来源行；读不出 / 读不全 / 不可达写进同步记录；D-32 审计 19 条 `LDAP_SYNC_JOB`；AD 密码登录 Admin、Portal；D-31 不失去最后一名管理员。未执行：`max-entries` 截断（单测覆盖）、超 1500 人大组分段读取、AD 用户登录 DW |
| AC-37 k8s 一侧（一次性库，按 Flowable → schema 包 → seed 包） | PASS：System Administrators 只有 `admin`、没有 `developer`，两组 code / id 不变，无直接分配的平台角色；A-01～A-14 PASS，A-15 WARN（新库未配 AD 映射，预期） |
| 带模板的邮件（本地收信容器 `wp11b-mailpit`，界面 `http://127.0.0.1:18025`） | PASS：BPMN 发送任务、动作提交后邮件规则两种入口；模板渲染、变量、附件正确；发送期间 admin / portal / engine 读 `dw_*` 为 0；改 DW 模板不重新部署时发出的仍是已发布版本 |
| AC-36 Automation | PASS 32/32；非 Public 团队下的只读提示只对 Auditor 显示（已截图） |

发现的缺陷与去处：
- `LdapClient` 把 DN 当字符串传给 JNDI，DN 含 “/” 时读不出（含 “/” 的组永远读不出；组里有这样的成员时该组恒为“读不全”，离开 AD 组的人删不掉；这类用户首次账号同步前用 AD 密码也登录不了）。Major → WP16。
- AD 完全不可达时同步记录仍显示“成功”，只在说明列写明；同步记录页说明列在宽屏下被截断 → WP16。
- 上线清单 W8 要求在同步记录页核对“各组成员数”，该页只有汇总数 → 上线清单改为在虚拟组页核对（已改写 W8）。
- k8s schema 包缺的不只是 93–99：62～99 段有 25 个脚本的对象不存在（含 96 运行时版本化），新建 k8s 环境导入与发起都会失败；k8s 新库的 Flowable 只建到 7.0.0.0，而 uat / preprod `FLOWABLE_SCHEMA_UPDATE=false` 会让 7.2.0 引擎拒绝启动（按 Flowable 行为推断）→ WP19。
- DW 新建邮件连接时按自己的 SSRF 白名单重复校验全局 SMTP 主机，内网中继被拒（Admin 允许内网）。用户 2026-11-06 定：**全局 SMTP 主机只由 Admin 校验一次**，DW 不再校验它 → WP19。DW `SsrfProtection` 对 DNS 解析不了的主机直接放行 → WP19。
- DW 调 Admin 不带语言，部署失败时外层中文、原因英文 → WP19。
- 纯文本附件以 7bit 发出，LF 变 CRLF → WP17。

### WP13 决定（2026-11-06）

方案：会话 scratchpad `wp13-plan.md`。用户定：**D1 方案 A**（文件存储归 Portal 后端，原表原地改名 `up_*`，URL 格式不变，Kong 把 `/api/v1/upload` 改指 Portal）；**D2 模型乙**（读文件时声明所在任务 / 请求 / 关联表，按该页面的门禁校验并核对文件确在该范围已落库数据中；上传人始终可读；匿名 401）；**D9 生产没有部署 DW**——生产现在上传、下载、邮件附件、Email Monitor 附件、Automation 取文件都不可用，上线后才可用，没有存量文件要接管。其余按方案推荐：不做写时防注入（残余风险）、不提供删除接口、DW 预览上传只在浏览器内、Activepieces 用引擎本地签名的短期 URL（新 Secret `PLATFORM_FILE_SIGNING_KEY`，只给引擎与 Portal）、描述只限上传人改（上传人未知时能读者可改）。WP13 依赖 WP12；WP15 的“DW 不再接受其它端 cookie”须在 WP13 之后或同版本。dev compose / `.env` 随功能需要改（按配置同步规则）。

### WP12 决定（2026-11-06）

方案：会话 scratchpad `wp12-plan.md`（清单：Portal 13 个文件约 44 处、Admin 8 个文件约 17 处 + BI 外键、引擎经 platform-common 间接 1 处；分 12a～12e 五小包）。生产没有 DW、Admin 导入不写 `dw_*`，所以 Portal Views、靠 View 授权的申请详情、Portal 审计菜单、Admin SLA（到期日永远盖不上）、BI Data View Assignment、删关联表的绑定保护（dev 也坏：SQL 按错列关联）**现在在生产就是坏的**。用户定：
- View 数据从 Admin Center 读（经 Admin 取已部署版本，再读其发布内容）；跨版本身份用 **View 名**（同一 FU 内唯一，DW 保存 / 导出、Admin 导入都校验；改名后 BI 分派显示“不可用”需改绑）。
- View 随 FU 部署才在 Portal 生效（DW 只点“发布”不再直接生效，帮助站同步说明）。
- 导入时访问规则只有 id、且 id 在本环境不存在 → **拒绝导入**（DW 导出时 code 解析不到也报错）。
- SLA、BI 只列有生效版本的 FU；BI 分派目标消失时保留为“不可用”；去掉 BI 对 SYS_ADMIN 的放行。
- Admin 删 Automation flow 的引用检查只看已部署 FU。
- 运行时存储表 `dw_table_data_rows`、`dw_pk_sequences` **改名为 Portal 命名**（不再以 `dw_` 开头，避免误解）。

### WP14 决定（2026-11-06）

方案：会话 scratchpad `wp14-plan.md`。现状最严重的三条：持有 `SERVICE_INTERNAL_TOKEN` 并填 `X-User-Id` 即可以任何用户（含 SYS_ADMIN）调 Admin 全部 376 个接口；引擎部署 / 清理 / Flowable REST / `/actuator/heapdump` 不要凭证，k8s 经绕过 Kong 的直连主机对外可达；k8s 的 Superset 拿着整份平台 Secret。用户定：**A + C**（每个调用方一把静态令牌、被调方存哈希、接口声明允许的调用方；Kong 封 `/internal`、放行浏览器用的 `/internal/ap`）+ **U2**（转发用户自己的令牌，被调方核实；只有 12 个接口需要用户身份，DW 部署允许 8 小时内已过期未注销的令牌）。一并做：引擎无凭证接口收口（actuator 只留 health / info / metrics / prometheus）、删除 k8s 四个后端的直连主机（上线前运维确认无人在用）、Superset 只拿自己的键、k8s Kong Admin API 只监听本机。其余按方案推荐：只给服务的接口挪到 `/internal/`；SSO 令牌不并入；无调用方的服务接口不再放行或删除；Admin 本包不重新校验审批人资格；`PLATFORM_FILE_SIGNING_KEY` 与服务令牌一样只经 `secretKeyRef` 给需要的服务；Email Monitor 发起疑似现在就被引擎拒绝（推断），先实测。

### 其它决定（2026-11-06）

- 站立委托（D-18，WP17-64）：**A 与单次委托对齐**——只覆盖委托人持有的任务（直派 + 已认领，含池）；被委托人在 To Do“委托”页签看到，只能代为通过 / 驳回（带 `onBehalfOfUserId`），不能转办、再委托、退回；规则的类型与过滤字段先不生效；引擎自己判规则（判据放 platform-security，与 Portal 共用）。
- 时区（WP17-89）：平台默认时区为**香港时间（UTC+8）**——数据库默认时区设为 `Asia/Hong_Kong`（Docker 初始化、k8s 包、已有环境各一次），各服务 JVM / Hibernate 现用的 `Asia/Shanghai` 统一改为 `Asia/Hong_Kong`（同为 UTC+8、无夏令时，行为不变）。
- 数据库时区的设法（WP19 review M1，2026-11-07）：**只设平台账号的默认时区**（`ALTER ROLE CURRENT_USER SET timezone='Asia/Hong_Kong'`），不用 `ALTER DATABASE`——后者要库 owner，且 preprod `hmhkdev`（一库多 schema，Activepieces 同库）等共享库会被整体改掉。其它账号直接写平台表时先 `SET TIME ZONE 'Asia/Hong_Kong'`（写进上线清单）。
- dev 登录慢（范围外 47，WP16 第三轮查清：dev 指向的企业 LDAPS 在本机不可达，每次密码登录先等 2–9 秒 TLS 握手失败再回退本地；dev 容器里的 `/app/ldap-prod.jks` 是空目录）：用户 2026-11-07 定**不动**，验证脚本的登录超时放宽到 15 秒以上。
- WP16 审查第一轮（PASS，3 Minor、1 Question）的处置（主会话定，随 WP16 第四轮实施）：用户同步里真正写库失败的条目记为“部分成功”、该次不作为增量起点（下次自动重试）；没有员工编号的 AD 账号按设计不入库，两类同步都记为“跳过”、不算失败；同步锁运行期间自动续期、定时同步因锁被跳过时写 WARN、Redis 不可达时手动触发回 503；`LdapClient` 不再改 JVM 全局信任库（只给 LDAP 连接用自己的证书库）；另修 `hasGroupChangedSince` 死代码、批量停用 / 归档确认框关闭时多弹“Failed”、platform-common 繁体中文缺 10 个 `validation.*` 文案。
- Email Monitor 自动发起（WP17 第二轮实测 FAIL：Portal 内部发起接口没有用户凭证可转发，引擎 `/instances/pinned` 与第一步认领 / 完成要求登录用户，邮件一直重试建不出案件）：用户 2026-11-07 定——以监控规则里配置的系统发起人（`sys_email_monitor_rules.system_initiator_user_id`）身份发起，并**自动完成第一步**（与人在 Portal 发起时一样），变更历史记为邮件自动发起。引擎只接受 Portal 的服务调用，且核实发起人是该 FU 某条启用中监控规则配置的那个人。归 **WP14** 实现（服务身份方案的一部分）。
- k8s 种子包里的 E2E 测试账号（口令 `password`，HEAD 即如此）：用户 2026-11-07 定**维持现状**。
- 数字信贷种子（WP19-81）：**补建** SENIOR_MANAGER、RISK_MANAGER、CREDIT_OFFICER 等角色，并给 E2E 账号分配对应 BU 角色（新种子脚本，只增不改）。
- dev `.env` 被 git 忽略、只在本机生效；功能带来的配置改动按配置同步规则改 compose 与 `.env.example`，本机 `.env` 同步更新。

## 上线 / 切换清单

PRD §10 的执行版，汇总范围外表里标“上线清单”的各行。适用于已有数据的环境（UAT、preprod、生产）；全新环境由初始化流程执行同一批脚本（Docker：`00-init-all.sh`；k8s：Flowable 建表 → `init-platform-schema` → `init-platform-seed`（末尾含 04、05 两段）→ Flowable `MIGRATE-7.0.0-to-7.2.0`，或 `init-database.ps1 -SkipDemoData`）。先在 UAT 完整演练一遍。

**同版本约束（不能拆开上线）**

- 本需求一次上线：Admin、Portal、DW、引擎四个后端与三个前端，和 `00-schema/100`、`90-post-seed/04`、`05` 在同一个停机窗口内完成。
- **05 与新版 Admin / Portal 同版本，且先于新服务接流量。** 新代码已删除 BU_UNBOUNDED 类型，05 不执行就上新服务，Admin 遇到这类存量角色无法按类型处理；05 收紧 `chk_role_type` 之后，旧版 Admin 再建这类角色会被数据库拒绝。
- **Admin 与 User Portal 同版本（User Portal 审计）。** 团队范围由新 Admin 写进 `functionUnitCodes`，新 Portal 才按它过滤；旧 Portal 忽略该字段时，Admin 发现越界行即整页拒绝（502 `UPSTREAM_SCOPE_NOT_APPLIED`）。
- **`SERVICE_INTERNAL_TOKEN`**：admin-center、user-portal、DW、引擎配置同一个值（Activepieces 不得持有），四个服务同时上线；新 Admin 配旧调用方会全部 401。

**发布前（旧版本仍在运行）**

| # | 做什么 | 怎么做 / 核对 |
|---|---|---|
| P1 | 盘点 | 在旧版本库上执行 `deploy/maintenance/platform-access-model/01-inventory-before-switch.sql`（只读）。I-05 团队 code 为空或冲突、I-06 FU 不是恰好一个团队：04 会整体拒绝，先在旧界面改好；I-01 会失去 Portal 准入的人、I-03 / I-04 名单与 AD 映射：写进本环境的切换配置（P3）；I-11 缺经理：补齐；I-12 无人可见的 View、I-09 / I-16 旧分派与 allowed roles：交给所属团队；I-13 测试账号；I-14 / I-15 待审批申请：见 P2 |
| P2 | 整理 | 在旧界面里补齐 Entity / Function Manager；DW 里每个 FU 恰好挂一个团队；处理 I-14（目标组会被删除或不再是 CUSTOM）与 I-15 列出的待审批申请，切换后它们无法批准：入组申请与指向 TEAM_LEAD / BU_UNBOUNDED 角色的申请由审批人驳回或申请人撤回；`ROLE_ASSIGNMENT` 类没有审批人（批准、驳回都回“无权审批”），只能请申请人在 Portal 撤回（`DELETE /permissions/requests/{id}`）。申请人已无法登录的会留在 PENDING，到 W9 登记为已说明的差异，不手工改库 |
| P3 | 切换配置 | 复制 `10-switch-configuration.template.sql`，按本环境填写：团队（名称须与 DW 所在环境完全一致，code 才对得上）、CUSTOM 组（至少一个映射原 Hermes Default Users 的 AD 组，否则大量用户进不了 Portal）、两个 SYSTEM 组的 AD 组、需要人工加入的成员（要用 Portal 的管理员、两位经理都没有准入的经理）。迁移会清空团队原有的 AD 映射（D-29），所有经 AD 同步成员的团队都要在这里重新配置 |
| P4 | 初始 System Administrator | 定好本环境的初始 SYS_ADMIN 账号（D-22），改 04 的 `SET platform_access_model.initial_sys_admin`（dev 为 `developer`，k8s 副本为 `admin`）；该账号必须存在且 ACTIVE，否则 04 拒绝 |
| P5 | 环境配置（不在仓库，运维核对） | 生产 ConfigMap：`JWT_EXPIRATION` / `JWT_EXPIRATION_MS` 900000，`JWT_REFRESH_EXPIRATION` / `JWT_REFRESH_EXPIRATION_MS` 28800000；生产 Admin 的 `ADMIN_DEV_BYPASS_ROLE_CHECK` 为关或不设；`SERVICE_INTERNAL_TOKEN` 见上；preprod Secret 换掉其中的开发环境密钥；旧的 `LDAP_HERMES_*` / `LDAP_GROUP_*` 已不再读取，可以删除 |
| P6 | 备齐 | 同一次构建的四个后端、三个前端镜像；`00-schema/100-platform-access-model.sql`、`90-post-seed/04`、`05`、本环境的切换配置脚本、01–03 |

**停机窗口**

| # | 做什么 | 怎么做 / 核对 | 失败时 |
|---|---|---|---|
| W1 | 备份 | 整库 `pg_dump -Fc`（回滚就是还原它） | — |
| W2 | 停全部旧服务 | Admin、Portal、DW、引擎全部停掉后再执行脚本。新版 Admin 若在 04 之前启动，同步会删掉团队的普通成员行（D-24 / D-30 再也读不到）；旧版 Admin 在 04 之后运行，会重建旧 SYSTEM 组 | — |
| W3 | 迁移 | 用 `psql -v ON_ERROR_STOP=1` 依次执行 `00-schema/100` → `00-schema/101`（平台账号在本库的默认时区 `Asia/Hong_Kong`：`ALTER ROLE CURRENT_USER IN DATABASE <本库>`，必须以平台账号执行，不需要库 owner 或超级用户，该账号在本库的新会话生效；其它账号直接写平台表时先执行 `SET TIME ZONE 'Asia/Hong_Kong'`）→ `00-schema/102` → `00-schema/103`（WP12：BI 分派改按 FU code + View 名）→ `00-schema/104`（WP12：运行时存储表 `dw_table_data_rows`、`dw_pk_sequences` 改名为 `up_table_data_rows`、`up_pk_sequences`，必须与同批服务一起上线）→ `90-post-seed/04` → `90-post-seed/05`（dev 在 04 之前多一个 `03-platform-access-model-dev-prepare`）。k8s 环境只执行 schema 包里 100、101、102、103、104 五段和 seed 包末尾的 04、05 两段，**不得整文件重跑 seed 包**。保留 04 的 NOTICE 输出（未迁入名单的成员、被清空的 AD 映射等）：图形客户端可能不显示，k8s 包头的 `client_min_messages = warning` 也会把它隐藏，建议用 psql 单独执行这几段 | 04 拒绝时什么都没改：按报错修好后重跑 |
| W4 | 切换断言 | `03-switch-assertions.sql`：切换阶段 A-01～A-14 必须全部 PASS | 任一 FAIL：还原 W1，停止上线 |
| W5 | 部署新版本 | 四个后端、三个前端同一版本；Admin 首次启动设 `LDAP_SYNC_ENABLED=false`，否则启动同步会在团队配好 AD 组之前，按 D-29 清掉迁入名单的 AD 来源行 | 还原 W1 + 旧版本 |
| W6 | 切换配置 | 执行 P3 填好的脚本。它先全量校验，有问题一次报全并整体拒绝；可重跑，第二次应为 0 变更。保留脚本与输出（这些改动不进 Admin 操作审计） | 改配置后重跑 |
| W7 | 配置断言 | 再跑 `03-switch-assertions.sql`：配置阶段 A-15（CUSTOM 组有 AD 映射）、A-16（团队有 Technical Lead 来源）应为 PASS；出现 WARN 须确认是有意为之 | 补配置后回到 W6 |
| W8 | 打开 AD 同步 | Admin 的 `LDAP_SYNC_ENABLED` 改回 `true` 并重启；以 SYS_ADMIN 调 `POST /api/v1/admin/ldap-sync/full`（账号）与 `POST /api/v1/admin/ldap-sync/hermes-groups`（组成员）各一次（界面没有入口）；在 Admin「LDAP 同步记录」里看本次同步的结果与说明（读不出或只读到一部分的 AD 组写在说明里），再在虚拟组页逐组核对成员数与来源 | 查 AD 组名与映射，改配置后重跑 W6、W8 |
| W9 | 预检 | `02-precheck-after-switch.sql`，与 P1 的盘点逐项对照：P-01 无准入的用户、P-02 平台角色、P-03 组与名单、P-05 无人可见的 View、P-07 应为空（只允许剩下 P2 里申请人已无法登录、撤回不了的 `ROLE_ASSIGNMENT`，逐条登记为已说明的差异）、P-08 应为空、P-09 / P-10 交给团队。由 SYS_ADMIN 确认 | 差异说明不了就不开放，查清再定 |
| W10 | 冒烟 | AC-06、AC-07、AC-11、AC-17、AC-25、AC-26，以及一次 DW 部署（AC-22）。通过后开放 | 开放前可以无损回滚：还原 W1 + 回退全部服务 |

回滚只在开放前无损；开放后再回滚会丢掉开放之后产生的数据（PRD §10 第 6 步）。

**开放后**

- 尽快完成存量 FU 的重新导入 / 认领（C-5、D-45、D-61）：SYS_ADMIN 带团队重新导入，或该团队 TL 在 Admin 手动导入同 code 的包并选自己的团队。在此之前 TL 在 Admin 看不到任何 FU（目录、SLA、RT 链接、BI Data View、User Portal 审计都按 FU 的团队过滤），要事先告知 TL。
- 重新导入之前，删除团队不会被 FU 拦住（D-33 只看 `sys_function_units.team_code`）：删团队前先在 DW 确认该团队下没有 FU。
- 各团队按 P-05 给 View 配好 BU + Role，按 P-09 / P-10 改掉旧分派和指向不存在角色的 allowed roles，再重新发布。

**环境与脚本的已知问题**

- k8s 新建库的顺序（WP19 实测）：`init-flowable/create` → `init-platform-schema` → `init-platform-seed` → `init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql`。schema 包必须在 Flowable 建表之后（其中一段给 `act_hi_taskinst` 建索引，`30-` / `31-` 加宽 `act_*` 列）；**新建库也必须执行 MIGRATE**：`create` 只建到 7.0.0.0，uat / preprod `FLOWABLE_SCHEMA_UPDATE=false`，引擎报 `FlowableWrongDbException: version mismatch` 拒绝启动，执行 MIGRATE 后正常启动。`init-database.ps1 -SkipDemoData`（IKP `-InitializeDatabase` 默认）执行的就是这四步。
- 两个 all-in-one 由 `node deploy/k8s/init-data/build-all-in-one.mjs` 按 `00-init-all.sh` 的顺序生成（`--check` 查是否最新），`deploy/k8s/init-data/verify-init-parity.sh` 校验与 Docker 初始化建出的对象一致。旧包缺 62～99 段的 25 个脚本（含 96 运行时版本化），已重新生成。
- dev 的 `build-and-deploy.ps1` 曾在新建卷初始化后按文件名重放全部 `00-schema`（`66-` 在 05 之后重新放宽 `chk_role_type`，新 dev 的 A-14 FAIL），WP19 已改为只核对 `00-init-all.sh` 跑完、不再重放。
- 已有库不得整文件重跑 schema 包：其中 `00-schema/66` 会把 `sys_roles.chk_role_type` 重新放宽到含 `BU_UNBOUNDED`；若重跑过，须接着执行 seed 包末尾的 05 段（A-14 会查出）。
- 切换后不能直接重跑种子包（Platform Showcase 会挂回已删除的组等）；dev 重灌须补跑 `90-post-seed/01`、`03`，并跳过 `08/05`。

## 范围外、已记录的问题

| 问题 | 去处 |
|---|---|
| 生产实际 ConfigMap 不在仓库，需运维按 900000 / 28800000 同步 | 上线清单 P5 |
| 本地 dev 默认开启 `ADMIN_DEV_BYPASS_ROLE_CHECK`（SIT / UAT / preprod 为关）；上线前确认生产为关 | 上线清单 P5 |
| `SERVICE_INTERNAL_TOKEN` 改为必填（WP2）：prod `.env` 只有占位，生产 Secret / ConfigMap 不在仓库，须运维为 admin-center、user-portal、DW、引擎配置同一个值（Activepieces 不得持有）；四个服务必须同时上线，新 Admin 配旧调用方会全部 401 | 上线清单：同版本约束、P5 |
| 服务令牌权限很大：带有效令牌即可调 `services=true` 的写接口（如给人加角色），不限角色；令牌是后端调 Admin 的唯一钥匙。WP2 已堵住已知的泄露口（DW 部署目标地址、引擎 webhook），并要求调用方入口自己鉴权（Portal 成员管理） | 残余风险，已记录 |
| Portal `AdminCenterClient` 有一批调用对应的 Admin 接口不存在（`/permission-requests/virtual-group` 等创建 / 审批 / 取消、`applicable-business-units`、`activatable-roles`、`/users/{id}/preferences`），`getList` 返回类型不符致成员列表恒 503 | 既有问题，暂不处理 |
| platform-common `SecurityConfig.jwtExpirationSeconds` 默认 3600，无人读取（死配置） | 暂不处理 |
| 引擎 `ProcessCompletionListener` 写死默认令牌 `wf-internal-service-call`；对端 Portal `/processes/{id}/complete` 只检查令牌请求头非空，任何值都能把任意流程标成已完成 | WP2 已修 |
| Portal `CurrentUserIdArgumentResolver` 在没有认证时信任浏览器传来的 `X-User-Id`：不带凭证即可冒充任意用户（review 范围外，探测已证实） | WP8 |
| Admin 的 GET 接口匿名可读（如 `/business-units`） | WP2 已修（匿名一律 401） |
| DW、Portal 登录在校验密码之前就返回“账号已停用 / 锁定”，可枚举账号 | 暂不处理，已记录 |
| preprod Secret（`deploy/k8s/secret/preprod/secret-workflow-paltform.yml`）放的是开发环境密钥（`docs/x-ray` G6 已记） | 上线清单 P5：运维替换 |
| 会话 8 小时空闲后 access cookie 与续期凭证同时过期，Portal 页面不刷新时请求变成匿名、经 `X-User-Id` 照常可用；随 WP8 修复 `X-User-Id` 一并消失 | WP8 |
| 同源跨端 cookie 回退：某端自己的凭证过期、但同浏览器另一端的凭证有效时（如 Admin 页面里 `ac_` 过期、`dw_` 有效），页面内请求按另一端身份通过，收不到 401，本端续期复核要等下一次路由切换调 `/auth/me` 才触发；AC-15 “移出 System Administrators”在同时开着 DW 时页面内操作不受 15 分钟上限约束。HEAD 上已如此（过期 cookie 消失后同样回退），不是 WP1 引入 | WP2 / WP9：Admin 按库里的角色逐接口鉴权后，DW 凭证在 Admin 只剩 DW 侧角色，能做的事受限；是否改为“本端 cookie 存在即只认本端”在 WP2 定 |
| Portal 同步长循环（批量完成、批量认领）在一次请求中途遇到凭证过期时，后续转给引擎的调用会 401，结果部分失败；概率约为“操作时长 / 15 分钟” | 残余风险，已记录；如出现再改为批量结束前不再依赖用户凭证 |
| 非 XHR 入口不会续期：Superset 作者门 `/bi/login/` 的 auth_request、记录笔记编辑器里的内联 `<img>`；SPA 空闲 15 分钟后这些入口会看到登录跳转或图片裂开（Superset 登录后用自己的会话） | 残余风险，已记录 |
| `deploy/init-scripts/init-database.ps1` 与 `00-init-all.sh` 漂移（缺 schema 93–99、`01-admin/08` Public 组、`90-post-seed/01`）；用它初始化的新环境不会执行访问模型切换 | WP19 已修：它仍有调用方（IKP 部署脚本 `-InitializeDatabase`、BUILD_GUIDE），改为运行时读取 `00-init-all.sh`，执行同一批脚本、同一顺序（含 Flowable 升级步骤与 `90-post-seed/04`、`05`）；`-SkipDemoData`（IKP 默认）执行 k8s 四步；补上调用方一直在传、它却忽略的 `-DbSchema` / `-SkipDemoData`；每个脚本 `ON_ERROR_STOP`。一次性库实测与 Docker 初始化对象、数据逐行一致 |
| `00-init-all.sh` 的 Step 2 循环漏了 `93-*`（`93-action-post-email-rules.sql` 在全新初始化时不执行） | WP19 已修：Step 2 补 `93-*`；生成器检查每个编号的 `00-schema` 脚本都被 `00-init-all.sh` 执行 |
| k8s schema all-in-one 必须在 Flowable 建表之后执行（其中一段给 `act_hi_taskinst` 建索引），与 README / BUILD_GUIDE 写的顺序相反；且缺 93–99 | WP19 已修：两个 all-in-one 改由 `deploy/k8s/init-data/build-all-in-one.mjs` 按 `00-init-all.sh` 顺序生成（原 schema 包少 336 项对象）；`verify-init-parity.sh` 校验与 Docker 初始化对象一致（只差演示包的 `meeting` / `participants` 两张表）；README、BUILD_GUIDE 改为 Flowable create → schema → seed → MIGRATE，并写进上线清单“环境与脚本的已知问题” |
| WP4 之前 Admin 每次 LDAP 同步（启动、每 2 小时）都会按 `HERMES_BINDINGS` 重建 Technical Leads / Team Leads / Developers / Hermes Default Users、TEAM_LEAD 角色及其绑定，并给 System Administrators / Auditors 写回 AD 映射。dev 的 AD 不可达，重建的组没有成员；AD 可达的环境会被同步灌入成员，System Administrators / Auditors 也会经 AD 回填（WP4 与本包同时上线，生产不会出现）。新解析器不认 SYSTEM 组的 TECH_LEAD 等绑定 | WP4 删除 |
| 一人多行之后，Automation workspace 成员判断（`ApWorkspaceResolver`）与按虚拟组取人（`VirtualGroupTargetResolver`）的问题已在 WP4 修掉。Admin 成员接口的旧写法会出错或重复：`findByGroupIdAndUserId`（单行 Optional，移除成员）、`findByGroupId` 列表重复、`countByGroupId` 按行计数、`deleteByGroupIdAndUserId` 会连人工来源一起删、`existsByGroupIdAndUserId` 让 AD 同步跳过已有人工行的人。切换后只有“同一团队两份名单都有”的人是多行 | WP4（同步只动 AD 来源）、WP5（成员页按名单与来源） |
| Admin 按 `sys_user_roles` 判断管理员的旧查询（`UserRepository.isUserAdmin` / `countActiveAdmins`，删除用户时的“最后一名管理员”保护）切换后恒为假，现状 dev 库该表本来就为空 | WP5（与“最后一名管理员”校验一起改为按 System Administrators 成员） |
| Admin 仍允许经 `/roles/{id}/members` 把平台角色写进 `sys_user_roles`；解析器不认这张表，所以不生效，但界面会显示成功 | WP5 拒绝平台角色；WP10 已删（Q3，10-5） |
| DW 里按用户名查 `sys_user_roles` / 组绑定的旧方法（`DatabasePermissionEvaluator.hasRole`、`SecurityComponentImpl`、`RoleRepository.findRolesBy*`、`PermissionRepository`）没有调用方 | WP6 清理 |
| 加了 FU 唯一约束后，DW 新建 / 导入 / `PUT dev-groups` 仍接受多个团队（`FunctionUnitComponentImpl.resolveCreationTeamGroupIds`、`FunctionUnitImporter`），传两个会撞约束返回 500 而不是业务提示 | WP6（单选 + 后端校验） |
| 仍按旧表算角色的 Admin 接口：`/permissions/check`、`/permissions/user/{id}`、`/permissions/conflicts/detect/{id}`（`RoleRepository.findByUserId` 只读 `sys_user_roles`，全仓无调用方）；`/users/{id}/virtual-groups` 按成员行返回，两份名单都在的人会重复，带 `profileContext=DEVELOPER` 时团队因没有角色绑定被过滤，DW 个人摘要看不到自己的团队 | WP5（成员与组展示）、WP6（DW 团队展示）；WP10 已删（Q3：整族删除，连同 Portal `ROLE_ASSIGNMENT` 旧链路，WP10 明细 10-5） |
| 切换脚本删除被删 SYSTEM 组的 `sys_virtual_group_task_history`（外键无级联，不删就删不了组） | WP19 已修：`00-schema/100` 让 `group_id` 可空并加 `deleted_group_code` / `deleted_group_name`，04 改为保留历史行、记下组的 code 与名称、清空引用（一次性库实测：Technical Leads 的历史行保留，旧 04 会删掉）。dev 在切换前该表 0 行，没有丢历史；新列已在 dev 补执行 |
| “未迁入名单的团队成员”“被清空的团队 AD 映射”只在切换的 NOTICE 里列出，图形客户端可能看不到 | WP10 已处理：盘点 I-03（未迁入名单的成员）、I-04（被清空 / 随组删除的 AD 映射）；断言整理为 `03-switch-assertions.sql`（上线清单 W3、W4） |
| 上线顺序（WP4 review 首轮 Major）：先停旧服务 → 执行 `00-schema/100` 与 `90-post-seed/04` → 部署新版本，新版 Admin 首次启动设 `LDAP_SYNC_ENABLED=false` → 执行切换配置脚本 → 打开同步并手动触发一次。新版 Admin 若在 04 之前启动，同步会删掉团队的普通成员行（D-24 / D-30 再也读不到）；旧版 Admin 在 04 之后运行会重建旧 SYSTEM 组。PRD §10 第 4 步已写明 | 上线清单 W2、W5、W8 |
| 切换后不能直接重跑种子包：Platform Showcase 会挂回已删除的组，`08-digital-lending-v2-en/05` 的 `ON CONFLICT (group_id, user_id)` 已无对应唯一键；`00-init-all.sh` 的 Re-seed 提示已写明须补跑 `90-post-seed/01`、`03` 并跳过 08/05 | 已记录（dev） |
| 指向已删除组的待审批入组申请（`up_permission_request.virtual_group_id`）保留原样，审批时会失败 | WP10 已处理：盘点 I-14、预检 P-07 列出；上线清单 P2 切换前由审批人驳回或申请人撤回 |
| platform-common `GlobalExceptionHandler` 没有处理 `HttpRequestMethodNotSupportedException`：任何接口用不支持的方法调用都返回 500 而不是 405（如 `PATCH /business-units`）；去掉的 Bind Roles 写接口因此也是 500（不落库） | 既有问题，暂不处理 |
| Role 页“角色成员”弹窗按组绑定找成员：TECH_LEAD / DEVELOPER 现在来自团队名单，弹窗显示“未绑定虚拟组” | WP9（TL 进 Admin 时整理 Role 页） |
| 删除团队时 Admin 只看 `sys_function_units.team_code`（D-33），它在 WP7 才开始写，生产存量 FU 要重新导入才补齐（C-5）；在此之前删团队不会被 FU 拦住，DW 里挂在该团队上的 FU 按“团队已删除”处理（WP6）。用户 2026-10-31 接受 | 上线清单“开放后”：重新导入前删团队，先在 DW 确认团队下没有 FU |
| 存量 FU（导入时还没有团队）在第一次带团队导入之前没有所属团队：这次导入只看包里的最终团队，任一团队的部署人用手工改过 code 的包可以把它归到自己团队（D-34 只保护已有团队的 FU）。生产存量靠 SYS_ADMIN 重新导入补齐（C-5）。D-61（用户 2026-11-02）：Admin 手动导入同样如此，任一团队的 TL 可用同 code 的包选自己团队认领，作为存量迁移方式保留 | 残余风险：上线后尽快完成存量重新导入 / 认领 |
| 生产重新导入存量 FU 之前，TL 在 Admin 看不到任何 FU：目录、SLA、RT 链接、BI Data View、User Portal 审计都按 FU 团队过滤，存量 FU 没有团队（C-5、D-45）。认领途径：SYS_ADMIN 带团队重新导入，或该团队 TL 在 Admin 手动导入同 code 的包并选自己团队（D-61） | 上线清单“开放后”：尽快完成存量重新导入 / 认领，并告知 TL |
| Admin 与 User Portal 必须同版本上线（WP9）：User Portal 审计的团队范围由 Admin 写入 `functionUnitCodes`、新 Portal 才按它过滤；旧 Portal 忽略该字段时 Admin 发现越界行即整页拒绝（502 `UPSTREAM_SCOPE_NOT_APPLIED`），TL 看不到 User Portal 审计但不会看到别队记录 | 上线清单：同版本约束 |
| DW 保存 BPMN 时，发送任务里的 `connectionId` / `emailTemplateId` 来自请求体、不校验属于本 FU；运行时 Admin 按本 FU 路径取连接凭据，指向别的 FU 的 id 取不到，只是留下失效引用 | WP19 已修：保存时校验每个发送任务的连接与模板属于本 FU（与动作邮件块同一判定 `WorkspaceRouteOwnership.requireEmailReferencesInUnit`），不属于的拒绝：`SEND_TASK_EMAIL_REFERENCE_NOT_IN_UNIT`，三语文案在 DW 自己的 `i18n/dw-messages_*` |
| DW 兼平台文件存储：`/upload/**`（Portal 表单附件、引擎邮件附件、Automation 取文件）匿名可读——`GET /upload/files/{name}` 与 `POST /upload/file-transfers/query`（按存储名取文件描述），只靠存储名不可猜；上传要登录或服务令牌，删除要 `FUNCTION_UNIT_UPDATE`，改描述只限上传人；与“运行时不依赖 DW”的原则相悖（既有，WP6 review 第二轮收口 DW 匿名访问时保留此例外） | 范围外：文件存储迁出 DW 并加鉴权另立项 |
| DW `GET /function-units/tags` 返回全部单元的标签，不按团队过滤（WP6 review 第二轮，既有） | WP19 已修：按 `FunctionUnitWorkspaceAccessService.visibleFunctionUnitIds()`（与列表同一判定）过滤 |
| 引擎 `DeveloperWorkstationEmailTemplateClient.getTemplate` 无调用方（发邮件读已发布快照，只借用其内容 record）；DW 对应的 `/internal` 接口已删除 | WP10 已删（WP10 明细 10-1） |
| DW `MemberController`（`/members` 增删改，旧 `members` 表）只要求 `FUNCTION_UNIT_ASSIGN_DEV_GROUP`，任一团队 TL 都能改；与访问模型的关系不明（WP6 review 范围外） | WP10 已删（DW 前端没有调用，dev `members` 表 0 行；表保留，WP10 明细 10-2） |
| `DeveloperPermissionChecker` 在 Admin 不可达时沿用过期缓存（既有）：宕机期间已移出名单的人仍用旧的能力集合；逐 FU 判断照常按库算 | WP19 已修：缓存过期后 Admin 不可达即 503 `PERMISSIONS_UNAVAILABLE`（权限服务不可用），未过期的缓存照常用 |
| “最后一名管理员”只防删除用户与撤销成员；把唯一一名有效管理员停用或锁定（Admin 改用户状态）不拦。用户 2026-10-31 决定不拦 | 残余风险：真发生时改库恢复 |
| Admin 前端 FU 页面重复提示：请求拦截器已弹出具体错误，再抛出不带 `response` 的 `ApiError`，调用方 catch 里又弹一次通用提示（导入、预览、部署、恢复、启停、删除、批量）；只有 validate 按 `if (!(e instanceof ApiError))` 去重（WP7 review 第二轮，HEAD 即如此） | 暂不处理：FU 页面统一按 validate 的写法去重，单独修 |
| `ActionDefinitionRepositoryContainerIT` 的测试 schema（`testcontainers/action-definitions-schema.sql`）缺 `source_id` 列，HEAD 即如此；该 IT 不在 surefire 默认 includes 里，平时不暴露（WP7 review 第二轮） | 既有问题，暂不处理 |
| DW `BaseController` 把预期内的 4xx 业务拒绝（`WORKSPACE_EXACTLY_ONE`、`FunctionUnitWorkspaceAccessDeniedException` 等）按 ERROR 打完整堆栈，HEAD 即如此；WP6 让这类拒绝变多，日志噪声明显（WP6 review 第四轮） | WP19 已修：4xx 拒绝只打一行 WARN、不带堆栈，5xx 照旧 ERROR 带堆栈 |
| dev 环境 Admin 密码登录先对不可达的 LDAP 主机做 DN 查找再回退本地，偶尔超过 30s，脚本首次运行可能超时（WP6 review 第四轮，环境配置） | 暂不处理，已记录 |
| DW `DeployRequest.conflictStrategy` 疑似无人使用（Admin 导入早已不收冲突策略） | WP10 已删（后端从不读取；旧调用方仍带该字段也照常部署，Docker 实测） |
| DW `HealthAliasController` 按请求的 Host:端口回调 actuator：宿主机经映射端口 8083 访问 `/health/live` 返回 503，容器内 200，k8s 探针不受影响（WP6 review 第三轮，既有） | WP19 已修：进程内读 actuator 健康组（`HealthEndpoint`），不再回调 HTTP；待 dev Docker 实测 |
| Portal 运行时直接读 `dw_*` 表：14 个文件，如 `PortalMainTableViewServiceImpl` 读 `dw_main_table_view_access`（View 访问规则）、`dw_form_table_bindings`、`dw_table_definitions`；与“运行时不依赖 DW”的原则相悖。PRD 附录 A-26 只记了 Admin 的 SLA / BI（WP8a 实测时发现，HEAD 即如此，本需求没有加深） | 范围外：单独排查，改为读已发布版本 |
| Portal lookup 搜索（`/relation-tables/{id}/search`）不按 `rt_table_access` 校验：表单里的 lookup 字段对能用该表单的人都可查。是否改为与 `/data` 同一授权会改变谁能在表单里用 lookup，属业务口径（WP8a review 第一轮 M3；匿名访问已在本包堵上） | 用户 2026-11-01 决定保持现状（D-41），不改 |
| Portal `ServiceTokenRequestInterceptor`（WP2）在没有登录用户时也带服务令牌调 Admin；M3 的匿名用户目录即经此透传。现已在 Portal 入口要求凭证，拦截器行为本身未改（WP8a review 第一轮） | 残余风险，已记录 |
| Portal 签发的令牌没有 `jti`：同一用户同一秒、同一工作区登录两次得到同一个令牌，黑名单按令牌哈希记，登出其中一个会让另一个也失效（HEAD 即如此；与治理基线里“5 个 JWT 签发点缺 issuer / jti”同源，WP8a review 第二轮实测时发现） | 既有问题，暂不处理 |
| dev 里 20 个已发布 View 现在没人能看到：13 个访问配置为空，7 个（ATM，跨环境导入）的规则指向本环境不存在的 BU / Role id（规则存 id） | WP10 已处理：盘点 I-12、预检 P-05 列出（空配置、BU / Role 不成对、规则 id 本环境不存在）；开放后交各团队配置（上线清单“开放后”） |
| Portal `GET /tasks/{id}` 无权限时 HTTP 200、响应体 `success:false` / 403，同一控制器的 `/history` 等回 HTTP 403（HEAD 即如此，WP8a review 第二轮） | 既有问题，暂不处理 |
| 引擎 `TaskInfoAssembler` 对 MI 多角色的行只取第一个 `roleCodes`，按工作区匹配时只看第一个角色（HEAD 既有，WP8a review 第一轮） | 既有问题，暂不处理 |
| Portal To Do 的“操作”列没有 `fixed="right"`：1366 宽时要横向滚动才看得到按钮，与共享列表 §6.6“溢出时 Action 钉在右沿”不符（HEAD 即如此，WP8b review 第一轮复测时看到） | 既有问题，暂不处理 |
| 单次委托只对有扩展行（`wf_extended_task_info`，现在只有 MI 子任务有）的任务可用：普通任务点“委托”时引擎回 “Task not found”，Portal 报 500 “Failed to delegate task”（HEAD 即如此，D-55 实测时发现；dev 库扩展行为 0 条） | 已按 D-56 处理（8b-8）：只对能委托的任务显示“委托”，直接调接口回 400 + 三语文案；不扩大委托范围 |
| Portal 委托页 “Delegate To” 等列显示原始用户 id（如 `user-e2e-zhangwei`），不显示姓名（HEAD 即如此，WP8b review 第一轮） | 既有问题，暂不处理 |
| 引擎完成任务时，任一候选人都能完成已被别人持有的任务：`TaskCompletionService.flowableRuntimeAuthorizesComplete` 只看“是办理人或候选人”，不看任务已被谁持有；直调引擎（UAT Kong 经 `/api/workflow` 暴露引擎）即可绕过持有人，Portal 会拦（403）（HEAD 即如此，WP8b review 第二轮 D-59 实测时发现） | 已转入范围：用户 2026-11-02 定 D-60，收紧为“已被持有时只认持有人与生效的委托”，在 WP8b 实现 |
| 引擎退回不校验操作人：`POST /tasks/{id}/return`（`TaskCompletionService.returnTask`）只要登录就能把任意任务退回到历史节点，Portal 前面有 `canProcessTask`；发起人的“退回草稿”也走这个接口（HEAD 即如此，WP8b D-60 实现时发现） | 用户 2026-11-02 定维持现状；直调引擎（UAT Kong 经 `/api/workflow`）可绕过，靠 Portal 拦截与网络隔离 |
| MI 前置任务的行指派不校验操作人：`POST /tasks/{id}/sub-table-rows/{rowId}/assign` 不读当前用户，谁登录都能改子表行的处理人（HEAD 即如此，WP8b D-60 实现时发现） | 用户 2026-11-02 定维持现状；靠 Portal 拦截与网络隔离 |
| Portal 任务页“委托”一栏显示被委托人的原始用户 id（如 `Delegated to user-e2e-wangfang`），BU + Role 委托显示 code，不显示名称（HEAD 即如此，WP8b review 第二轮范围外第 1 条；变更历史“委托结束”一行的旧值已解析为姓名） | 既有问题，暂不处理 |
| D-18 站立委托端到端不可用：按委托人列任务被引擎 403；Portal 提交时不带 `onBehalfOfUserId`，引擎拒绝（HEAD 即如此；WP8b 只让失去准入的一方不生效，D-60 不改变它） | 既有问题，暂不处理（WP8b 不修站立委托本身） |
| 没有持有人、也没有扩展行的任务，引擎的认领（`TaskClaimSupport.claimTask` 只在扩展行存在时判权限）和转办（`TaskActionService.transferTask`）都不校验操作人：任何登录用户直连引擎都能认领或转办这类池任务，Portal 会拦（HEAD 即如此，WP8b review 第四轮发现） | 用户 2026-11-02 定 D-60 “无人持有维持现状”：实际范围是任何登录用户，不只是候选人；靠 Portal 拦截与网络隔离 |
| 转办会清掉单次委托（引擎 `transferTask` 调 `clearDelegation`），但不写“委托结束”：HEAD 上转办本来就不写变更历史，D-59 列举的情形里也没有转办（WP8b review 第四轮发现） | 既有问题，暂不处理 |
| 存量任务若办理人存的是用户名而不是 id（引擎 `engineActorMatchesPortalUser` 兼容用户名），离职判定按 id 查 `sys_users`，这类任务不会被识别为离职（WP8b review 第一轮；dev 库没有这类数据） | 既有数据形态，暂不处理 |
| Portal `/api/portal/internal/**` 经 Kong 对外可达，只靠服务令牌保护，与现有内部接口一致（WP8b review 第一轮） | 残余风险，已记录（见“服务令牌权限很大”） |
| Admin 推送、读路径修复、两个定时任务之间没有互斥（ShedLock 只管定时任务之间）：不同副本同时处理同一条任务时，可能写出重复的 `FORCE_UNCLAIM` / `MANAGER_REASSIGN` 历史行（WP8b review 第一轮） | 残余风险，已记录 |
| View 访问的测试种子 `deploy/init-scripts/91-view-access-test` 已在 a46995e9b 删除，dev 库没有 `view_*` 账号，`frontend/scripts/verify-view-access-control.mjs` 跑不起来 | WP10 已重建（Q1：不建 `view_admin`，SYS_ADMIN 场景用 `developer`，WP10 明细 10-7）；Docker 实测 5 个场景全过 |
| DW 也接受其它端的 cookie（`up_access_token` 在 `cookie-names` 里），平台任意有效令牌在 DW 都算已登录，靠逐路由判断挡住（实测只有 Portal 准入的人在 DW 拿不到数据）；路由守卫测试只查路径变量，以后新增一个没有路径变量、没有权限注解、又不在 `WORKSPACE_PATHS` 下的接口，会对平台任意令牌开放（WP6 review 第三轮） | 残余风险，已记录：新增 DW 接口须带 `@RequireDeveloperPermission` 或落在 `WORKSPACE_PATHS` 下 |
| `.cursor/skills/view-access-control` 等 skill 与 `docs/design/README.md` 的改动属于需求阶段（目标规则与文档索引），不是 WP1 代码 | 提交时与 WP1 代码分开 |
| ER 图 Save 清空被写表上 LOOKUP 字段的 Lookup 设置：请求体不带 `lookupConfig`，后端对 LOOKUP 字段按请求值覆盖；同一请求体还把复合外键截成第一个键列（HEAD 即如此，WP9 第三轮 review 范围外第 1 条） | 本分支已修：`er-diagram.vue` 回传 `lookupConfig`、`precision`、`scale`，图上没改的外键保留全部 `refPrimaryKeyFields`（`erDiagramSave.test`）；WP10 Docker 经 Admin 界面实测（WP10 明细 Docker 一行） |
| RT 的 Rollback 只恢复部分字段属性：外键、Lookup、主键生成规则、计算字段都不恢复（`RelationTableDeployServiceImpl`），帮助文章的说法比实际好（HEAD 即如此，WP9 第三轮 review 范围外第 2 条） | 既有问题，暂不处理 |
| 帮助站带 `#hash` 的链接打开后不会滚到对应小节：`scrollBehavior` 滚的是 window，内容在 `main.help-main` 里滚动；影响所有带锚点的 `?` 链接（HEAD 即如此，WP9 第三轮 review 范围外第 3 条） | 既有问题，暂不处理 |
| Admin 的 RT 列表页大量英文写死（Edit、Compare、Rollback、Access、“Table Structure”、“View ER Diagram” 等；HEAD 即如此，WP9 第三轮 review 范围外第 4 条） | 既有问题，暂不处理 |
| Admin 前端 `stores/user-portal-audit.ts`、`stores/audit.ts` 写死时区 `+08:00`；Portal `WorkflowEngineTaskClient` 对引擎的 4xx 一律返回空，调用方再统一报 500（WP9 第二轮 review 登记，第三轮复述） | 既有问题，暂不处理 |
| ER 图 Save 之后被写的表变成 UPDATED，而 ER 图与 User Portal 只列 DEPLOYED 的表，所以这张表从图上消失，直到重新部署（HEAD 即如此，WP10 Docker 实测时确认） | 既有行为，暂不处理 |
| ER 图字段行悬停时连接点会移位：`.er-handle:hover` 与 `.er-field-row:hover .er-handle` 的 `transform: scale(…)` 覆盖了 vue-flow 定位用的 `translate(50%, -50%)`，圆点向内下方跳约半个点；从圆点右半边按下会拖动整张卡片而不是连线（77a2a2c15 起，WP10 Docker 实测时发现） | 既有问题，暂不处理 |
| Admin 删除关联表前，`RelationTableStructureServiceImpl.hasBindings` 读 `dw_form_table_bindings`、`dw_table_definitions`，查询失败按“无绑定”放行；与“运行时不依赖 DW”的原则相悖（HEAD 即如此，WP10 实测时发现） | 范围外：与上面 Portal 读 `dw_*` 一条一起排查 |
| dev 种子数字信贷（`fu-20260403-a1b2c6`）5 个动作的 allowed roles 指向 dev 中不存在的角色 code（SENIOR_MANAGER、RISK_MANAGER、CREDIT_OFFICER 等），设计态与已发布的 1.0.0、1.0.1 都有；预检 P-10 报出（HEAD 即如此） | WP19 第二轮已修（用户定补建）：新种子 `01-admin/13-digital-lending-action-roles.sql`（登记 `00-init-all.sh`，进 k8s seed 包）建 8 个 BU 绑定角色、在共享财务中心可用，并给 e2e_zhaomin / sunqiang / zhoujie / wugang 各一个对应 BU 角色（Approve 由已有 MANAGER 的 e2e_lina 看到）。dev 已手工执行，P-10 不再报；Portal 实测五个受限动作对持有角色的账号显示、对没有的账号隐藏。注：这几个节点仍是旧的 VIRTUAL_GROUP 分派（P-09，归所属团队改） |
| Admin `Permission` 实体有 `description`，`sys_permissions` 表没有这一列（HEAD 即如此）：`GET` / `PUT /roles/{id}/permissions` 对任何人都 500；WP10 删掉的 `/permissions` 三个接口原先也因此 500（WP10 第一轮 review 范围外第 1 条） | 既有问题，暂不处理（实体映射另立项修） |
| Admin `GET /users/{id}/roles?type=<非法值>` 返回 400，内容暴露类名 `No enum constant com.admin.enums.RoleType.X`（`UserController`，HEAD 即如此，WP10 第一轮 review 范围外第 2 条） | 既有问题，暂不处理 |
| 引擎 `ConfiguredEmailSender`：模板的 subject / bodyHtml 为 null 时 `String.valueOf` 得到字面量 "null"，能通过 `hasText` 检查而被发出（HEAD 等价行为，旧代码的 null 判断本来就到不了；WP10 第一轮 review 范围外第 3 条） | 既有问题，暂不处理 |
| `frontend/scripts/verify-view-access-control.mjs` 的 DW 截图时序：访问面板出现后只等 1 秒，有时截到的 BU / Role 标签是原始 id，加长等待后显示名称（脚本问题，不是产品问题；WP10 第一轮 review 范围外第 4 条） | WP19 已修：改为等待两个下拉的标签都不再是存的 id（名称已渲染），不用固定等待 |
| Portal `UserPermissionController.getMyPermissions` 吞错：异常时返回 success 加空列表（HEAD 既有的静默兜底；WP10 第一轮 review 范围外第 5 条） | 既有问题，暂不处理 |
| Portal 废弃接口 `POST /permissions/renew` 仍能建出 PENDING 的 `TEMPORARY` 申请，而 `canApproveRequest` 对这类类型恒为 false，与 `ROLE_ASSIGNMENT` 同样谁也批不了；`/my`、`/expiring` 同属废弃接口，前端 `permissionApi.renewPermission` 有定义无调用；I-15 / P-07 不列 FUNCTION / DATA / TEMPORARY（dev 0 行；HEAD 即如此，WP10 第二轮 review 范围外第 1 条） | 既有问题，暂不处理；如要清理，删这三个接口与前端方法，或让 I-15 / P-07 也列出这几种类型 |
| `frontend/scripts/_wp9-admin-tl-checks.mjs` 的 cleanup 不删临时账号的 `sys_login_audit` 记录，每跑一次留下一批孤儿登录记录（脚本问题；WP10 第二轮 review 范围外第 2 条） | WP19 已修：cleanup 一并删除 `wp9_*` 账号的登录记录（只删本脚本的账号），LEFTOVERS 加 `loginAudit` |
| 直接用 SQL 插入的申请在 Portal 列表里的申请时间差一个时区：数据库按 UTC 写入，JVM 按 +08:00 读取；由应用创建的行显示正确（与上面写死 `+08:00` 同属既有时区问题；WP10 第二轮 review 范围外第 3 条） | WP19 第二轮已修（用户定香港时间）：`00-schema/101` 把平台账号的默认时区设为 `Asia/Hong_Kong`（第三轮按 review M1 由库级改为账号 + 本库级 `ALTER ROLE CURRENT_USER IN DATABASE`；Docker 初始化最先执行，k8s schema 包第一段），各服务 JVM / Hibernate / Jackson 的 `Asia/Shanghai` 改为 `Asia/Hong_Kong`；dev 已执行，新会话 `SHOW timezone` 为 `Asia/Hong_Kong`，psql 用默认值插入的申请在 Portal 显示正确时间（旧的 UTC 会话仍差 8 小时，作对照）。已存在的行不改 |
| Admin 启动时的 LDAP 同步（`LdapHermesGroupSyncComponent` 监听 `ApplicationReadyEvent`）没有 ShedLock，多副本时每个副本启动都各跑一次；定时同步有锁。`PROJECT-X-RAY.md` 里“admin-center 必须单副本，否则定时同步会重复跑”的说法也因此不准（HEAD 即如此；WP10 第四轮 review 范围外第 1 条） | 既有问题，暂不处理 |
