# Activepieces 0.84.0 架构逆向分析（Architecture Reverse Engineering Report）

> **Document 2 / 10** — 前置：[REQUIREMENTS.md](REQUIREMENTS.md)（Q1–Q9 + Q4a 已裁决）。
> 状态：**六条逆向线已完成落盘（均基于 0.84.0 快照实测）；⚠️ 尚未冻结**——阻塞项见
> **[OPEN_GATES.md](OPEN_GATES.md)**；决策见 **[DECISIONS.md](DECISIONS.md)**；总览见 **[STATUS.md](STATUS.md)**。
> 分析对象：0.84.0 源码快照（已验证与官方 tag `0.84.0` commit `05354b37` 逐字节一致）；
> 只读分析，未做任何 install/build。
> 日期：2026-07-22（末次更新 07-23）
>
> ⚠️ **本文描述的是「改造前的上游 0.84.0」，不是现状。** 文中 `bun install` / `bunfig.toml` /
> `bun-runner.ts` 等均为**上游原貌**——HERMES 侧已按 CR-01 去 bun（运行时装包改 pnpm，
> `bun-runner.ts` → `pkg-runner.ts`，`bunfig.toml` 已删）。**现状请看 [STATUS.md](STATUS.md)**；
> 本文作为基线快照**刻意不随改造更新**，否则会丧失"上游长什么样"的对照价值。

## 0. 已核实基线（分析前提）

- 包布局：`packages/{cli, ee, pieces, server/{api,engine,worker,utils}, shared, tests-e2e, web}`
- 根工具链：`packageManager: bun@1.3.3`、`bun.lock`、`bunfig.toml`、根含 `turbo.json`（构建编排以 §3 逆向结论为准）
- `packages/ee/` + `server/api/src/app/ee/` 商业许可。⚠️ 约束是 **No EE Business Features**，
  **不是"目录不进构建"**（实测 CE 有 105 处 import `app/ee/**`，排除即编译失败——见 DEPENDENCY_MAP §2.2.4）
- `packages/web` = **React 19** + Vite SPA（0.84 实测 react/react-dom/@types 均 19，`@xyflow/react` 12.3.5、`radix-ui` 1.4.3 统一包）；`packages/server/*` = Fastify/TypeORM/Node 服务
- 裁决约束回顾：Q3 纯 builder 组件；Q2 全面去 bun；Q4 per-user 供给；Q5 独立 schema；Q7 部署期解析

## 1. Builder 依赖闭包（Q3 抽取边界）✅ 已按 0.84.0 核实

> 路径相对 `packages/web/`。完整 12 项耦合清单见分析线原始输出；此处为决策级摘要。

### 1.1 入口与结构

- 路由：`/projects/:projectId/flows/:flowId`（`src/app/routes/project-routes.tsx:92-105`，链上有
  登录守卫 → 权限守卫(READ_FLOW) → BuilderLayout → lazy `FlowBuilderPage`）。
- 页面：`src/app/routes/flows/id/index.tsx` → `ReactFlowProvider` → `BuilderStateProvider` →
  `src/app/builder/index.tsx`（header + FlowCanvas + step-settings/runs/versions 面板）。
- **builder 的 zustand store 不是模块级单例**：`BuilderStateProvider` 每次 useRef 创建，socket 与
  queryClient 作为 `BuilderInitialState` 注入（`builder-hooks.ts:45-47`）——**对抽取天然友好**。

### 1.2 🎁 重大利好：CE web 已内置 embedding 运行模式

- `src/app/guards/index.tsx:71-78`：模块级同时创建 `createMemoryRouter` 与 `createBrowserRouter`，
  按 `embedState.isEmbedded` 二选一——**MemoryRouter 路径是现成的**；
- `src/components/providers/embed-provider.tsx`：`EmbeddingState` 含 18 个开关
  （hideSideNav/disableNavigationInBuilder/hideFlowNameInBuilder…）——**开关体系保留、改由宿主 props 注入**；
- `src/app/routes/embed/index.tsx` 的 postMessage/iframe 握手与 managed-authn 是 EE/iframe 语义，
  同进程挂载不需要，整页排除。
- builder 内路由 API 仅 hook 级（useParams/useNavigate/useSearchParams/Link，共 4 个文件），
  无 data-router loader——**宿主用裁剪版路由表 + MemoryRouter 包裹即可**（AP 自己 embed 就这么干）。

### 1.3 🎁 许可利好：builder 闭包对 ee-embed-sdk 仅 1 个触点

web/src 全部 3 个 ee-embed-sdk import 中，2 个在 embed 路由（抽取时排除）；唯一进入 builder
闭包的是 `src/components/custom/home-button.tsx:1,22`（经 builder-header），且只用了**一个字符串
枚举成员**——删按钮或本地常量替换即可彻底切断。**ee 代码对画布无实质功能依赖**（NFR-S03 可满足）。

### 1.4 核心切点（纯组件化的整改清单，按优先级）

| # | 耦合 | 位置 | 解法 |
|---|---|---|---|
| 1 | **`ApStorage` 模块级单例**（token/projectId 唯一物理落点，硬编码 localStorage） | `src/lib/ap-browser-storage.ts:1-15` | **改为可注入 Storage/会话 provider——一处改动覆盖全局**（api/socket/hooks 都经它） |
| 2 | `authenticationSession`：硬编码 key、`logOut()`→`window.location.href='/sign-in'` | `src/lib/authentication-session.ts:14-15,45-63,130-133` | 会话读取走宿主注入；logOut/switch 改宿主回调 |
| 3 | `API_BASE_URL = window.location.origin` 模块级常量 | `src/lib/api.ts:14-17` | 注入 baseURL（同域部署下影响小，仍建议注入化） |
| 4 | 401 强跳 `/sign-in` | `src/lib/api.ts:38-52` | 改注入 `onUnauthorized` 回调 |
| 5 | socket.io 模块级单例（`io(API_BASE_URL,{path:'/api/socket.io'})`） | `src/components/providers/socket-provider.tsx:8-13` | 注入化；test run 进度强依赖 ws，宿主须代理 `/api/socket.io` |
| 6 | queryClient onError 硬耦合 error-dialog 与 **billing 弹窗**（EE 特性拖挂） | `src/app/query-client.ts:5-36` | 错误处理注入化，剥离 `features/billing` 依赖 |
| 7 | 登录守卫强制 suspense 预取 + SocketProvider 挂载 | `src/app/components/allow-logged-in-user-only-guard.tsx:21-40` | 抽取时以轻量 provider 替代 |
| 8 | `useReloadPageIfProjectIdChanged` 整页重载 | `state/builder-state-provider.tsx:29` | 去掉整页重载逻辑 |

低危保留项：canvas-state/theme 的 localStorage 偏好持久化（可并入 #1 的注入 provider）；
i18n 模块级初始化 + `/locales` 运行时 fetch（宿主同源提供或注入 loadPath）。

### 1.5 宿主/网关 API 契约 —— **按生命周期分四段**（Document 3 交付物）

> 笼统列一堆端点会掩盖"哪些是白屏级硬依赖、哪些只是某功能不可用"的区别，也无法提前发现
> 依赖面膨胀。**统一按下列四阶段分类**，每个端点标注所属阶段 + 缺失时的降级表现。

| 阶段 | 含义 | 缺失后果 |
|---|---|---|
| **Required to Mount** | 画布渲染前必须成功（含 `useSuspenseQuery` 阻塞的） | **白屏/挂起**——最硬依赖 |
| **Required to Edit** | 编辑 flow：改步骤、选 piece、配 connection、存草稿、发布、版本 | 画布能看不能改 |
| **Required to Run** | 触发测试运行：test flow / test step / webhook 模拟 | 不能调试 |
| **Required to Observe** | 查看运行结果与历史：run 列表/详情/步骤日志/实时进度 | 看不到执行结果 |

#### ✅ 枚举已完成（2026-07-22）——**规模远超预期：39 个同源端点 + 2 个外部域 + 16 个 WS 事件**

> 早前文档只列了 5 个挂载前置，**严重低估**。完整明细（39 行端点表 + WS 事件表 + 源码位置）
> 见分析线原始输出，此处为决策级摘要。

**统一前缀**：`lib/api.ts:16` → `API_URL = ${window.location.origin}/api`，故全部为 `/api/v1/*`。

| 阶段 | 端点数（含跨阶段计入） | 其中"必需" |
|---|---|---|
| Mount | 10 | 6（**其中 4 个是 `useSuspenseQuery` 白屏级**） |
| Edit | **25** | 8 |
| Run | 9 HTTP + 3 组 WS | 5 |
| Observe | 6 HTTP + 3 组 WS listen | 5 |

**Mount 段的 4 个白屏级硬依赖**（缺失 = 永久 loading，**不是降级**）：
`GET /v1/flags`、`GET /v1/platforms/:platformId`、`GET /v1/projects`、**`GET /v1/users/:userId`**
（最后一个是新发现——`badge-celebrate.tsx:20` 触发，早前遗漏）。
另有两个 spinner 级：`GET /v1/flows/:flowId`、`GET /v1/sample-data`。

#### ⚠️ 对宿主/网关设计的 9 条硬约束（Document 4 直接输入）

1. **测试与观测 100% 依赖 socket.io，无 HTTP 兜底**：`TEST_FLOW_RUN`（emit）、`TEST_STEP_PROGRESS`/
   `TEST_STEP_FINISHED`、`UPDATE_RUN_PROGRESS`（listen）是结果的唯一通道。宿主只代理 REST 的话，
   **画布能编辑但完全不能测试/看进度，且按钮点击后永久 pending（Promise 不 reject）**。
   路径固定 `/api/socket.io`，握手 auth = `{token, projectId}`。**Kong 必须支持 websocket 升级。**
2. **`POST /v1/flows/:flowId` 是单点写路径**：承载全部 `FlowOperationType`（改步骤/增删/移动/发布/
   版本/sample data…）。失败即 `flowUpdatesQueue.halt()`（`flow-state.ts:214`）→ **整个编辑会话
   静默停止落库，用户只看到一次 toast**。**这是最需要 SLA 保障的端点。**
3. **`POST /v1/pieces/options` 不是普通 CRUD**：唯一的动态属性/下拉端点，服务端会**同步跑引擎作业**
   （`EXECUTE_PROPERTY` + 等待 worker 响应），且**隐式要求 flow 已存在于服务端**、要求 worker 在线、
   可能长耗时。**网关须为它单独放宽超时**，且不能当无状态代理。
4. **`/v1/pieces/:name` 路径含斜杠且未 encode**（如 `/api/v1/pieces/@activepieces/piece-slack`，
   依赖服务端 `/:scope/:name` 双段路由）——**网关路径匹配/重写极易踩坑**。
5. **首屏请求放大 ≈ 2N + 2M**：sample-data 每步 OUTPUT/INPUT 各一次（`2N`，且**阻塞画布渲染**），
   piece metadata 每步打两次（pinned + latest，`step-utils.tsx:99,104`）。**20 步的 flow 首屏 ~80 个请求**
   ——网关限流阈值必须留够。
6. **长期轮询与心跳**：`/v1/flow-runs` 15s、`/v1/flow-runs/:id` 5s、WS presence/lock 各 30s。
   画布长期挂着会持续产生流量。
7. **`/v1/flags` 是隐藏的配置总线**：builder 内 13 处 `useFlag`，其中 `WEBHOOK_URL_PREFIX` 直接决定
   webhook 测试打到哪个 URL；缺 key 时 `useFlag` 返回 null，多数 UI **静默隐藏而非报错**。
   宿主必须提供完整 flags map。
8. **`lib/api.ts:41-52` 会劫持宿主导航**：任何 `SESSION_EXPIRED`/`INVALID_BEARER_TOKEN` 响应
   → `logOut()` + `window.location.href='/sign-in'`，**直接把 DW 页面踢走**。已在 §1.4 切点 4 列为必改项，
   此处再次确认其严重性。
9. **可裁剪面**：`ChatDrawer` 在 `app/builder/index.tsx:236` **无条件挂载**，把 EE 的 `features/chat`
   与 `/v1/human-input`、`/v1/webhooks` 拉进闭包——若不需要 chat，这是可剪掉的一大块依赖。

#### 🔒 两个安全/协作相关的新发现（影响 AG-03）

- **CE 下权限校验在前端静默放行**：builder 调 `/v1/project-members/role`（**EE-only，CE 返回 404**），
  失败时 `hooks/authorization-hooks.ts:42` 让 `checkAccess` 全部 fallback 为 `true`。
  即 **CE 的 builder UI 不做客户端权限收敛**——服务端 RBAC 仍生效，但 UI 会展示用户实际无权执行的操作。
  需在 AG-03 权限回归中确认"UI 可见 ≠ 可执行"不会造成误导或异常。
- **flow 编辑锁是既有机制**：`LOCK_RESOURCE`/`UNLOCK_RESOURCE`/`RESOURCE_LOCKED`（30s 心跳），
  他人持锁时**画布强制只读**（`use-flow-lock.ts:19-28`）。**这对我们的共享 project 模型是有利的**
  （天然防止并发编辑冲突），但必须在 DW UI 中正确呈现"被他人锁定"状态，否则用户会困惑于
  "为什么画布不能改"。列入 Document 4 的 UX 设计项。

#### builder 会调用的 EE-only 端点（CE 不注册，降级表现已知）

`/v1/oauth-apps`（OAuth2 回落手填 client id）、`/v1/project-members/role`（见上）、
`/v1/global-connections`、`/v1/secret-managers`。**均非阻塞**，但需在 Document 4 明确降级预期。
注意 `/v1/projects` 虽在 `ee/` 目录下，**COMMUNITY 也注册**，不是 EE-only。

### 1.6 排除清单（不进抽取产物）

auth 路由（sign-in/up/forget…）、platform-admin 全套、public 路由（forms/chat/mcp-authorize/404）、
connections/runs 列表页/tables/templates/variables/automations 列表、embed 路由、
`features/billing`、`features/platform-admin`、team/projects 管理页。

## 2. 认证与用户供给 API 面（Q4）✅ 已按 0.84.0 核实

### 2.1 核心机制（路径相对 `packages/server/api/`）

- **两级用户模型**：`user_identity`（全局登录身份：email 唯一、bcrypt 密码、verified、tokenVersion）
  ↔ `user`（platform 内成员：platformRole=ADMIN/MEMBER/OPERATOR、status=ACTIVE/INACTIVE）。
- **JWT**：HS256，secret=`AP_JWT_SECRET`（生产必须显式配置）；USER token 有效期 **7 天**、
  **无 refresh 端点**（过期只能重新 sign-in）；失效靠 `tokenVersion` bump（改密码/移出 platform 即全端失效）。
  **USER token 不含 projectId**（platform 级），project 每请求解析——平台侧须自己记录每用户 projectId。
- **invitation-only 门禁**：存在 platform 后，一切直接 sign-up 一律 403
  （`authentication.service.ts:26`），除非该 email 有 ACCEPTED 邀请。CE 自动 verify 邮箱（无需 SMTP）。
- **CE 可用的管理端点**：`GET/POST/DELETE /v1/users/*`（列用户/改角色与状态[可禁用]/删除，需 platform ADMIN）；
  **没有"创建用户"API、没有改密/重置密码 API（EE-only）** → 凭据必须平台侧托管，轮换=重建账号。
  **无 API Key/SERVICE token（EE-only）** → 平台服务只能持 admin 的 USER token（7 天，需自动续签）。

### 2.2 ⚠️ 关键发现：CE 下"多用户共享单 project"原生不可行（冲击 Q4 落地假设）

已核实的证据链：

1. PROJECT 型邀请被 `projectRolesEnabled=false`（CE OPEN_SOURCE_PLAN）挡死（`user-invitation.service.ts:77`）；
2. `projectMemberModule` 仅 CLOUD/ENTERPRISE 注册（app.ts:251/282），CE 下 `/v1/project-members/*` 404，
   且该模块本就没有"添加成员"端点（只能邀请 accept）；
3. team project 上限 = 1（`teamProjectsLimit=ONE`）且无法加成员；
4. CE 原生模型 = **每用户一个 PERSONAL project**（sign-up 自动创建，owner 全权），
   互相不可见；platform ADMIN/OPERATOR 可见全部 project（`user-service.ts:255`）。

**结论：Q4 裁决中的"单 platform 单 project、成员逐用户供给"在 CE 无法原样落地**，
需补充子决策（见 §2.4 Q4a）。

### 2.3 per-user 供给路径 —— ⚠️ **现有 HTTP 链路仅作"已验证可行性证明"，不作为长期架构**

**已核实可跑通的链路**（纯 HTTP、无 EE）：一次性引导 sign-up（首用户）→ ONBOARDING token →
`POST /v1/platforms` → admin USER token；每用户：`POST /v1/user-invitations`
`{type:PLATFORM,email,platformRole:MEMBER}` → 从响应体取 invitation link（`user-invitation.service.ts:255`）
→ `POST /v1/user-invitations/accept` → 随机密码 `POST /v1/authentication/sign-up` → 得 USER token + projectId。

**❌ 但此链路有两个不可接受的架构缺陷（2026-07-22 评审裁定，不得作为长期方案）**：

1. **AP 的运维配置状态成了平台 provisioning 的前提**——该链路依赖"**SMTP 必须保持未配置**"
   （配了 SMTP 则 link 走邮件、响应体不回传 token，自动化直接断链）。让被集成系统的一项运维配置
   反向约束我方业务逻辑，是**错误的架构边界**：任何人给 AP 配上 SMTP 就会静默打断用户供给。
2. **平台必须托管每个用户的随机密码**——纯粹的安全负担（密码存储、轮换、泄露面），
   且 CE 无改密 API 导致轮换=重建账号，进一步放大负担。

**✅ 替代方案源码调研已完成（2026-07-22）——结论：Option B 可行且有 AP 自身生产代码作模板**

**决定性发现 1：邀请门禁在编排层，不在下层原语。** `assertUserIsInvitedToPlatformOrProject`
（`authentication-utils.ts:14`）的**唯一生产调用点**是 `authentication.service.ts:26` 的 `signUp`。
下面四个 domain service **全部无门禁**：`userIdentityService.create`（`user-identity-service.ts:11`）、
`userService.getOrCreateWithProject`（`user-service.ts:53`）、
`authenticationUtils.getProjectAndToken`（`authentication-utils.ts:33`）、
`accessTokenManager.generateToken`（`access-token-manager.ts:11`）。
→ **直接调下层 domain service 天然绕开邀请链路（含 SMTP 依赖）。**

**决定性发现 2：AP 自己就有两条同款产线代码可逐行照抄** ——
`ee/managed-authn/managed-authn-service.ts:27-100`（外部 JWT → get-or-create identity/user →
upsert project member → **直接 `generateToken` 签 USER token**）与 `ee/scim/scim-user-service.ts:30-97`。
二者都不经 `signUp`、都不受邀请门禁约束。**我们要做的就是把"外部 JWT 提取"换成"内部 key 鉴权"。**

**决定性发现 3：随机密码"生成即丢弃"是 AP 官方模式,与"平台托管密码"根本不同。**
`cryptoUtils.generateRandomPassword()`（`server/utils/src/crypto.ts:7`）在 AP 自身代码中三处使用
（federatedAuthn / managed-authn / SCIM）：密码生成后**不返回给任何人、不落盘明文**，任何人（含平台）
都无法用它登录，唯一入口是服务端签发的 token。`user_identity.password` 非空是 schema 约束，
用随机值填充即满足，**不构成密码托管**。

**决定性发现 4：服务端自签 token 有现成范例。** `generateToken`（`access-token-manager.ts:11`）是
**通用无密码 JWT 签发器**（无 principal 类型分支、无 DB 查询、不碰 password）；WORKER token 就是
`docker-entrypoint.sh:11-24` 用 `AP_JWT_SECRET` 自签的。USER token 自签只需
`{user.id, platformId, identity.tokenVersion}` + identity.verified=true + user.status=ACTIVE。
`getProjectAndToken(userId, platformId, projectId)` 是**现成的"给定 userId 出 {token,projectId}"内部函数**，
且自带 verified/status/project 三道校验（不要绕过它自己签，用它免费拿到校验）。

**决定性发现 5：内部端点鉴权有 AP 自己的模板。** `ee/platform/admin/admin-platform.controller.ts:17-35`
——模块级 `app.addHook('preHandler', checkCertainKeyPreHandler)` 比对 key header，路由声明
`securityAccess.public()`。照抄即可（建议用独立 `AP_INTERNAL_PROVISIONING_KEY`，加内网/mTLS 限制）。

---

**方案对比与选型建议**

| | Option A（credential broker） | **Option B（内部 provisioning 端点）** |
|---|---|---|
| 解决 ① SMTP 依赖 | ✅（仅 token 侧） | ✅ **全链路**（不碰 user_invitation/smtpEmailSender） |
| 解决 ② 托管密码 | ✅ | ✅ |
| **解决"建用户"** | ❌ **不覆盖**——用户从哪来？若仍靠邀请链路则 SMTP 依赖回归 | ✅ 覆盖 |
| 独立可用 | **否**（必须配 B 的建用户部分） | **是** |
| 改动面 | 3 文件 / ~40 行 | 3 文件 / ~90 行 |
| AP 内模板 | 无 | **managed-authn-service.ts 逐行可抄** |

**→ 建议采用 Option B，把 A 的 mint-token 作为 B 的降级子路径**（`provision-session` 在第 1 步命中
已有用户时直接跳到签 token，即等价于 A）。

**Option B 端点与调用序列（Document 4 直接落地用）**：

```
POST /v1/internal/provision-session      (模块级 internal-key preHandler + securityAccess.public())
Body: { externalUserId, email, firstName, lastName, platformId?, platformRole?, sharedProjectId?, projectRole? }
200 : AuthenticationResponse { ..., token, projectId }
```
1. `userService.getByPlatformAndExternalId({platformId, externalId})`（`user-service.ts:216`）— **幂等短路**
2. `userIdentityService.getIdentityByEmail(email)`（`:72`）
3. 若无 → `userIdentityService.create({email, password: cryptoUtils.generateRandomPassword(), firstName,
   lastName, provider: JWT, **verified: true**, trackEvents:false, newsLetter:false})`（`:11`）
   —— `verified:true` 是关键，否则第 7 步抛 `EMAIL_IS_NOT_VERIFIED`
4. `userService.getOrCreateWithProject({identity, platformId})`（`user-service.ts:53`）— 幂等
5. （可选）`userService.update({id, platformRole, externalId, status: ACTIVE})`（`:78`）
6. ~~（加入共享 project）`projectMemberService.upsert(...)`~~ → **已删除**（见下）
7. `authenticationUtils.getProjectAndToken({userId, platformId, projectId: sharedProjectId ?? null})`（`:33`）

**✅ 原第 6 步已因 §2.7 裁决而取消**：共享 project 的访问改由 **CE core 层 RBAC 小补丁**放行
（切点 `authz/authorize.ts` + `websockets.service.ts`），因此 provisioning **不需要**给每个用户写
`project_member` 行——既简化了供给流程（少一次 DB 写入、少一处幂等考量），也**避免了对 `ee/`
project-member 服务的依赖**，同时保住最小权限（不必给 OPERATOR）。

**残留耦合（B 方案，均可接受）**：`AP_JWT_SECRET` 必须存在（本就是部署必需）；平台持有单个
`AP_INTERNAL_PROVISIONING_KEY`（可轮换、不可用于 UI 登录，远优于托管 N 个用户密码）；
每个新用户会多一个 personal project（`getOrCreateWithProject` 无条件创建，可接受或改用
`userService.create` 自管归属）；首次 platform bootstrap 仍需人工 sign-up 一次。

**🚫 直写 DB 的定性（措辞修正）**：早先把"直写 identity+user+project 三表"列为普通备选是**错误**。
正式定性为 **Break-glass / migration-only fallback**——仅限灾难恢复、数据迁移等一次性场景，
**绝不作为常规 provisioning path**（绕过全部 domain 校验、与未来 AP 内部变更强耦合、一致性无保障）。
使用须经架构评审并留审计记录。

其余已核实约束（与方案选择无关，均成立）：停用=`POST /v1/users/:id` status=INACTIVE（不能停用 owner）；
email 全局唯一；admin USER token 7 天需自动续签。

### 2.4 Q4a 子决策 → **已裁决（2026-07-22，用户拍板：选 C）**

| 选项 | 做法 | 代价 |
|---|---|---|
| A. 接受 CE 原生模型 | 每开发者一个 personal project；flow 归属个人 | FU 的 flow 其他开发者打不开（协作断裂），需靠"注册表+导出导入"接力，体验差 |
| B. 全员 platform ADMIN/OPERATOR | 配置级（供给时赋 OPERATOR 即可见全部 project） | 越权面大：OPERATOR 对所有 project 有 Editor 权（API 层），最小权限原则受损 |
| C. HERMES 小补丁：共享 project 授权 | 在 **CE（MIT）core 层**给"指定共享 project"放行 platform 成员（**切点已核实见 §2.7；注意 `rbac-service`/`project-member.service` 实际都在 `ee/`，真正的 core 切点是 `authz/authorize.ts` 与 `websockets.service.ts`**） | 受控源码补丁；是 vendor+自维护路线的题中之义 |
| D. 启用 ee 的 project-members | ❌ 违反 NFR-S03（ee 商业许可），**不可选** | — |

**裁决 = C**（用户 2026-07-22 拍板）：在 CE（MIT）代码的 RBAC 判定链上给指定共享 project 放行
platform 成员，不碰 ee/ 代码；v1 形态 = 单 platform + 指定共享 project（FU flow 归属地）+
每用户自动 personal project（保留不作协作载体）。

### 2.6 ⚠️ Q4a-Sec：共享 project 的资源所有权验证门（"一处补丁"是低估——2026-07-22 修正）

> 早先把 C 描述为"一处受控源码补丁"**低估了工作量**。RBAC 补丁只解决"能否进入 project"，
> 但**多用户共享 project 并执行 flow**真正的成败点，是 project 内各资源的**所有权与作用域模型**。

**核心失败场景**（必须证伪或证实）：
```
User A ── 建 Flow A ── 依赖 Connection A（用户 A 创建）
User B ── 经补丁能看到 Flow A ── 但执行时 Connection A 若是 user-scoped 则不可见 → 执行失败
```
若 Connection/Credential/Webhook 在 AP 0.84 CE 是 **user-scoped（带 ownerId 且执行按 owner 解析）**，
则共享 project 方案有致命缺口，RBAC 补丁补不上——Q4a 需重新设计。

**验证面（不止 assertAccessToProject 一处，逐面查清 scope）**：
Project visibility / Flow read / Flow write / Flow delete / Flow version / Run / Run history /
Socket 订阅 / project-scoped 查询 / project-scoped 变更 / **Piece·Connection 所有权** /
**Credential 访问** / Webhook。每一面是 project-scoped 还是 user-scoped。

**✅ 定论（2026-07-22 源码逆向，全部带实体+service 行号）：共享方案结构上成立，无 user-scoped
致命缺口。** 你担心的失败场景被证伪——App Connection 在 0.84 CE 已是「global connections」模型
（`app-connection.entity.ts:46-50` 用 `projectIds: string[]` + platformId + scope；`ownerId` 可空
且**只做展示**），list/get/引擎执行全部按 `projectIds ArrayContains(projectId)` 过滤、**从不看 owner**。

**逐资源 scope 判定表**：

| 资源 | 作用域 | owner 字段 | 读/用/改是否按 owner 过滤 | 共享 OK |
|---|---|---|---|---|
| Flow | **project**（`flow.entity.ts:34`） | ownerId 可空/元数据 | 否（`flow.service.ts:133`） | ✅ |
| Flow Version | project（随 flow） | updatedBy 仅记录 | 否 | ✅ |
| **App Connection** | **project**（projectIds 数组）+platform | ownerId 可空/仅展示 | **否**（`app-connection-service.ts:170-176,309-337`；引擎 `app-connection-worker-controller.ts:17-40`） | ✅ |
| 凭据加密 | **全局单一 key**（`AP_ENCRYPTION_KEY`，`helper/encryption.ts:66`） | — | — | ✅ |
| ai_provider（LLM key） | **platform**（`ai-provider-entity.ts:39`，无 projectId） | — | — | ✅ |
| trigger_source / webhook | **project/flow**（`trigger-source-entity.ts`，无 ownerId；执行以 project 身份） | 无 | 否 | ✅ |
| Flow Run | **project**（`flow-run-entity.ts:29`；triggeredBy 仅手动测试记录） | 无 | 否（`flow-run-service.ts:82-88`） | ✅ |
| Piece metadata | **platform**（`piece-metadata-entity.ts:46`） | — | — | ✅ |
| Socket 订阅 | **projectId room**（`core/websockets.service.ts:38`） | — | 否 | ✅ |

**「User A 建 Connection、User B 用同 project Flow 执行」= 能跑通**。全链路唯一 owner 无关的卡点是
RBAC 准入（`project-member.service.ts:127-162` `getRole()`：非 owner、非 platform ADMIN/OPERATOR、
无 `project_member` 行 → 返回 null → 拒），HTTP 与 WebSocket 共用此 gate。一旦放行，Flow/Connection/
Run 的 service 层**全部只按 projectId 过滤**，A 的 connection 对 B 完全可见可用；引擎按
`projectIds ArrayContains([shared])` 命中 → 全局 key 解密 → 执行成功。

**放行方式三选一**（用户已裁决走 CE RBAC 小补丁，详见 §2.7）：
- (a) 供给时给共享 project 插 `project_member` 行（零源码，但依赖 `ee/` 的 project-member 体系）；
- (b) `platformRole: OPERATOR`（零源码、不碰 ee/，但对平台内全部 project 有 Editor 权，越权面大）；
- **(c) ✅ 已选：CE core 层 RBAC 小补丁**——见 §2.7。
**无需改动任何 Connection/Flow/Run/Trigger 的 service/entity**——它们本就 project-scoped。

### 2.7 ✅ 裁决：CE RBAC 小补丁（指定 shared project 放行）—— 切点已核实

> 2026-07-22 用户拍板。同时**修正本文档早前一处错误**：§2.4 曾称"core 层 `rbac-service`"，
> 但实测 `rbac-service.ts` 在 `ee/authentication/project-role/`、`project-member.service.ts` 在
> `ee/projects/project-members/`——**二者均属 `ee/`，不可作为补丁点**（违反 NFR-S03）。
> 真正的 core（MIT）切点是下面两处，均已实测定位。

**⚠️ 必须同时打两处——HTTP 与 WebSocket 是两条独立路径**：

| # | 切点（均在 `packages/server/api/src/app/core/`，MIT） | 现状 | 补丁位置 |
|---|---|---|---|
| P1 | `security/v2/authz/authorize.ts:93-103` `assertAccessToProject` | `:102` 委派给 **ee/** `rbacService.assertPrinicpalAccessToProject` | 在 `:102` 委派**之前**短路 |
| P2 | `websockets.service.ts:96-115` `validateProjectId` | `:105` **直接**调 **ee/** `projectMemberService.getRole` | 在 `:105` **之前**短路 |

**P2 极易被漏**：WebSocket 连接**不经过 `authorize.ts`**（`websockets.service.ts:32` 在握手时自行校验）。
只打 P1 的话，HTTP 全通但**画布 test run 的实时进度会静默鉴权失败**——症状是"能编辑不能调试"，
且排查方向容易跑偏。**两处必须同时改，并各自有回归用例。**

**设计约束（Document 4 落地时必须遵守）**：

1. **不得无差别放行**：短路只解决"能否访问该 project"，**仍须保留路由要求的 Permission 粒度**——
   把共享 project 的访问映射到一个明确角色的权限集（建议以 seed 的 `Editor` project_role 为
   单一事实来源读取其 permissions，而非在 core 里另抄一份映射表）。
2. **严格限定作用域**：仅对**配置的那一个 shared projectId** + 仅 `USER` principal + 仅同 platform 生效；
   其余一律走原逻辑。
3. **fail-safe 默认**：shared project id 经 env 配置（如 `AP_HERMES_SHARED_PROJECT_ID`），
   **未配置时补丁完全惰性**，行为与上游一致——避免配置缺失导致越权。
4. **HERMES-PATCH 标记**（NFR-C02）+ 两处各自的权限回归用例（并入 AG-03）。

**相对另两方案的收益**：
- vs (a) `project_member` 行：**不依赖 `ee/` 的 project-member 服务**，且**供给流程少一步 DB 写入**
  ——AG-06 Option B 调用序列的第 6 步可直接删除，provisioning 更简单；
- vs (b) `OPERATOR`：**保住最小权限**——用户不会因此获得他人 personal project 的 Editor 权。

**残留风险**：补丁位于上游代码内，未来若 AP 重构该判定链需重新对位（Q8 已裁决纯自维护，
不跟随上游，故风险可控；HERMES-PATCH 标记 + AG-03 回归是保障）。

**3 个非阻塞细节**（进 Document 4/8 注意项）：① connection 的 TABLE 鉴权取 `projectIds[0]`
（`projectIdExtractor.ts:43`）——共享场景建议 connection 只挂 shared 一个 project 避免歧义；
② list 需客户端显式带 `projectId` query（AP 前端切 project 后即如此）；③ `getOwners` 展示层
在 CE 只回 platform ADMIN 列表，不影响功能。

**对 Q4a 决策的影响**：C 方案成立且更轻——**建议优先用"零源码 project_member 数据行"落地**，
仅当需要"platform-wide 自动授权、不想逐用户写行"时才上 `getRole` 源码补丁。二者留给 Document 4 定。

### 2.5 其他风险与运维要点

- admin USER token 7 天过期：平台侧供给服务需自动 sign-in 续签（凭据托管已是既定前提）。
- email 全局唯一（`user_identity`）：企业邮箱天然满足；测试环境重建账号须先删旧 identity。
- CE 下 `assertEmailAuthIsEnabled`/域名白名单在 COMMUNITY 短路放行，无阻碍。
- 未核实项：`AP_EDITION=ENTERPRISE` 无 licence 时 plan 特性的运行时降级路径（与我们无关，仅 COMMUNITY 结论有效）。

## 3. Bun 依赖面与去 bun 迁移矩阵（Q2/CR-01）✅ 已按 0.84.0 核实

> 全部结论基于 0.84.0 快照实测（路径相对快照根）。

### 3.1 基线事实

- `.nvmrc` = **v24.14.0**（Node 基线明确）；`packageManager: bun@1.3.3`；锁文件仅 `bun.lock`（1.9MB 文本）。
- 构建编排 = **Turbo 2.9.14**（`turbo.json`，**不是 Nx**）；测试全仓 **Vitest 3.0.8**（无 bun test）。
- workspaces 含 `packages/shared`、`server/{api,engine,utils,worker}`、`web`、`pieces/*`、`ee/embed-sdk`；`resolutions.rollup → @rollup/wasm-node`。
- 0.84 是**单一 `Dockerfile`**（无 Dockerfile.worker，0.86 才拆分）；根 package.json **无 `trustedDependencies`**（0.86 才有）。

### 3.2 三大定性结论

1. **零 Bun 运行时 API**：全仓无 `Bun.*` / `bun:*` 专有 API。唯一相关代码是
   `packages/server/api/src/app/mcp/tools/mcp-utils.ts:55` 的堆栈清洗正则 `node_modules/.bun/`
   （迁 pnpm 后改 `.pnpm/`，低危）。
2. **flow 引擎子进程本来就是 Node**：`packages/server/worker/src/lib/sandbox/fork.ts:7`
   （`child_process.fork` + `--expose-gc/--max-old-space-size`）、`sandbox/isolate.ts:114`
   （isolate 沙箱内 `process.execPath` = Node）。engine 包 build 走 esbuild。**引擎执行路径与 bun 无关。**
3. **唯一进镜像的运行时 bun 依赖 = worker 的动态依赖安装**：
   - `packages/server/worker/src/lib/cache/code/bun-runner.ts`（`bun install --ignore-scripts --filter ./<piece>`；`build()` 用的是 esbuild 不是 bun）
   - 调用方：`cache/pieces/piece-installer.ts:98/161`（运行时装 piece）、`cache/code/code-builder.ts:159`（**CODE step 声明 npm 依赖时也走运行时 bun install**）
   - `Dockerfile` run 层（line 96–124）`FROM base` 继承 bun 二进制 + `bun install --production`。

### 3.3 对我们决策的直接影响

- **FR-F03A/B（关停运行时安装）正好切中要害**：不重写 bun-runner，直接禁用两条运行时安装路径 +
  构建期全量预装（`ready` 标记机制），bun 的最大迁移风险（bun `--filter`/共享锁语义 ≠ pnpm）随之消失。
- **⚠️ 两条运行时安装路径风险不同，已拆为两个 FR**（2026-07-22 评审）：
  - `cache/pieces/piece-installer.ts:98/161` → **FR-F03A**，影响面 = piece 目录内容（运维/资产），
    失败模式 = 某 piece 在画布不可选；
  - `cache/code/code-builder.ts:159` → **FR-F03B**，影响面 = 开发者能写什么（产品能力），
    失败模式 = flow 作者写不出某类逻辑 → **必须设计期 fail-loud 校验 + 替代指引**，不可运行期才失败。
  能力矩阵（构建期 piece 依赖 ✅ / 运行时 piece 安装 ❌ / CODE step 内置依赖 ✅ /
  CODE step 外部 npm ❌ / 任何运行时 npm·bun install ❌）见 REQUIREMENTS F 组。
- 镜像构建期动作：删 base 层 bun 下载（Dockerfile line 31–43）、`bun install --frozen-lockfile` →
  `pnpm install --frozen-lockfile`、isolated-vm 改 npm/pnpm 安装（原生编译需验证）。
- **易漏项**：`bunfig.toml` 的 `minimumReleaseAge=259200s`（供应链防护）→ pnpm `minimumReleaseAge=4320`
  分钟，**必须等价保留**；`tools/scripts/utils/publish-npm-package.ts:75` 解析 bun.lock 做版本 pin →
  改解析 pnpm-lock.yaml；`.npmrc` `legacy-peer-deps=true` → pnpm `strict-peer-dependencies=false`；
  `resolutions` → `pnpm.overrides`。
- 开发/CI 态（低风险批次）：`tools/scripts/install-bun.js`（删）、`tools/setup-dev.js`、
  cli 翻译工具 `generate-translation-file-for-piece.ts:25`、12 个 GitHub workflows（仅参考）、devcontainer。

### 3.4 迁移矩阵（按风险排序，完整 24 项见分析线原始输出）

| 触点 | 位置（0.84 实测） | 难度 | 处置 |
|---|---|---|---|
| 运行时 piece 安装 | `server/worker/src/lib/cache/pieces/piece-installer.ts:98,161`（经 `code/bun-runner.ts`） | 🔴→🟢 | **按 FR-F03A 关停**（fail-closed），不做 pnpm 改写 |
| 运行时 CODE step 依赖安装 | `server/worker/src/lib/cache/code/code-builder.ts:159`（esbuild bundle `:170` 保留） | 🔴→🟢 | **按 FR-F03B 关停** + 设计期校验（产品能力约束，需 fail-loud） |
| Dockerfile run 层 bun | `Dockerfile:96-124` | 🔴 | 自建镜像重写：去 bun 二进制、`pnpm install --prod`、锁文件换 pnpm-lock |
| Dockerfile base/build 层 | `Dockerfile:31-43,55-56,64-93` | 🟡 | 删 bun 层；isolated-vm 原生编译验证 |
| bunfig.toml 安全策略 | `bunfig.toml` | 🟡 | `minimumReleaseAge` 等价迁移（不可漏） |
| 锁文件重建 | `bun.lock` → frontend pnpm 锁（Q1 并入） | 🟡 | 全量重解析，关键依赖版本核对（CR-04 对账） |
| 发布工具读 bun.lock | `tools/scripts/utils/publish-npm-package.ts:75` | 🟠 | 改解析 pnpm-lock.yaml |
| 堆栈清洗正则 | `server/api/.../mcp-utils.ts:55` | 🟢 | `.bun/`→`.pnpm/` |
| 引擎 fork/isolate、esbuild、Vitest、Turbo、docker-entrypoint（PM2+node） | 各处 | 🟢 | **无需改动**（与 bun 解耦） |

## 4. 执行引擎与沙箱真实实现（FR-L 组）✅ 已按 0.84.0 核实

> 路径相对快照根。**核心结论：0.84 官方默认沙箱强度为"弱"，安全完全依赖外部容器/网络策略兜底。**

### 4.1 执行链路

- 队列 = BullMQ（Redis）`workerJobs` + `runsMetadata`；但 **remote worker 不直连 BullMQ**——
  API 侧出队（`job-broker.ts`），经 socket.io RPC 下发给长轮询的 worker（`worker/src/lib/worker.ts`）。
- engine = **独立子进程**（`worker/src/lib/sandbox/fork.ts:7` fork / `isolate.ts:120` spawn），
  经本地 WS-RPC + 一次性 32 字节 token `timingSafeEqual` 握手通信；`UNSANDBOXED` 下 engine 子进程
  **常驻复用**（每 box 一个），isolate 模式每 job 新建销毁。worker 并发默认 `AP_WORKER_CONCURRENCY=5`。

### 4.2 双层可插拔沙箱（都由 `AP_EXECUTION_MODE` 选）

| Mode | 进程层（worker→engine） | code 步骤层（engine→用户码） | code 可用 npm 依赖 |
|---|---|---|---|
| **UNSANDBOXED（默认）** | fork，**无隔离** | require 子进程，**无隔离** | 允许 |
| SANDBOX_CODE_ONLY | fork，无隔离 | **isolated-vm** | 禁 |
| SANDBOX_PROCESS | isolate 二进制（ns/uid/mount） | require 子进程 | 允许 |
| SANDBOX_CODE_AND_PROCESS | isolate 二进制 | **isolated-vm** | 允许 |

- **默认 UNSANDBOXED**（`api/.../system.ts:39`）；**官方 compose 未设 AP_EXECUTION_MODE** → CE 镜像
  默认两层都不隔离（fork + require）。镜像已预装 iptables/isolate/isolated-vm，具备升级能力但默认不启用。
- `isolated-vm@6.0.2` 唯一生产使用点 = `engine/src/lib/core/code/v8-isolate-code-sandbox.ts:12`
  （无 require、ExternalCopy 传值、`.run` 未设 timeout 靠外层 kill）。
- **社区 piece 永远在 engine 进程内跑，从不进 isolated-vm**——只有"用户手写 code 步骤"才可能下沉隔离层。

### 4.3 资源与网络边界

- 内存：fork 用 `--max-old-space-size`（默认 1GB）；isolated-vm code 层 128MB 硬顶；
  **isolate 二进制模式未传 `--mem/--time/--cg-mem`（default.cf 也没设）→ 仅受容器整体 cgroup 约束**。
- 时长：worker 侧 `setTimeout → tree-kill SIGKILL`（flow `FLOW_TIMEOUT_SECONDS=600`）；CPU 无独立强制。
- 网络：**默认 `AP_NETWORK_MODE=UNRESTRICTED` = 无任何出网限制**。STRICT 才激活三层
  （engine JS SSRF guard monkey-patch dns/socket + egress 代理 + iptables owner-uid 锁定），
  且 **iptables 层仅在 isolate 模式生效**。默认模式下用户 code 子进程既无 JS guard 也无 iptables。

### 4.4 沙箱强度评级：默认 **弱（D）**

真隔离仅有 engine↔worker 进程边界（不隔离用户负载与 engine）。默认模式下：用户 code/piece 与 engine
同进程同权限，可读写 `cache/v11`（跨 flow 的 code bundle/pieces）、读 `process.env` 的 `engineToken`/
内部 API URL（可冒充 engine 调内部 API）、任意出网（含云 metadata 169.254.169.254）、spawn 子进程。

**剩余风险（容器非 root + NetworkPolicy 兜底后仍存在）**：① 同容器跨 flow 文件面横向读取；
② engineToken/内部 API 凭据进程内可读（NetworkPolicy 必放行到 API host，堵不住）；③ 默认无出网限制；
④ loopback 面（WS-RPC 端口）；⑤ 默认无 per-run CPU 限额；⑥ code 依赖供应链 RCE=engine 权限 RCE；
⑦ isolated-vm 逃逸依赖无 0-day。

### 4.5 对我们的决定性影响（→ NFR-S02 + Document 4/8）

- **本平台运行的是"自己人写的技术 flow"，不是不可信租户代码**——威胁模型比 AP SaaS 温和，但
  **默认 UNSANDBOXED 仍不可直接上生产**（engineToken 泄露面 + 跨 flow 文件面真实存在）。

#### 4.5.1 ⚠️ Production **Candidate** Baseline（候选，**非冻结配置**）

```
AP_EXECUTION_MODE = SANDBOX_CODE_AND_PROCESS
AP_NETWORK_MODE   = STRICT          （需 NET_ADMIN + iptables/isolate，镜像已备）
```
理由：只有该组合才同时获得三层纵深（isolated-vm 隔离 code + isolate ns/uid 隔离进程 +
iptables owner 出网锁定）。**但这只是候选值，不得直接写成生产配置。**

**为什么不能直接冻结**：静态源码分析只能证明"这两个开关能开、语义上更强"，**不能证明我们的整套
运行形态在该模式下仍然工作**。已知至少三处结构性变化会动摇现有设计：
1. isolate 模式**每 job 新建/销毁沙箱**（非 UNSANDBOXED 的常驻复用），rootfs 经挂载
   host `cache/v11/common` → sandbox `/root/common`——**FR-F03A 构建期预装 pieces + `ready` 标记
   机制是在 UNSANDBOXED 下验证的，在 isolate rootfs 下能否被 engine `import()` 解析未经验证**；
2. STRICT 的 iptables owner 链（uid 60000-60999）默认 REJECT 非白名单出网——
   **AI Generate flow 调 deepseek 是生产依赖，若未正确加入 egress 白名单即造成回归事故**；
3. 沙箱每 job 新建 + isolate 进程开销对**同步 webhook 全链路耗时**（Path B 10s / AI 300s 预算）
   的影响未测——`AP_WEBHOOK_TIMEOUT_SECONDS` 与四层超时嵌套（§2.4 铁律 2）可能需重新标定。

#### 4.5.2 🔒 Security Architecture Gate（SG）—— 候选基线转正的前置门

> 与 §6.5 的 Q3 PoC 同级：**这是架构门，不是普通配置项**。SG 全绿之前，Document 4/8 不得把
> 上述组合写成"生产基线"，只能写"候选基线 + 待 SG 验证"。任一项失败 → 回到 4.5.3 的降级阶梯。

| # | 验证项 | 必须证明 | 关联既有需求 |
|---|---|---|---|
| SG-1 | **Piece loading** | 构建期预装的白名单 pieces 在 isolate rootfs（`/root/common`）下可被 engine `import()`；`ready` 标记机制仍生效；**零运行时安装** | FR-F03A、GW-7 |
| SG-2 | **Code execution** | CODE step 在 isolated-vm 内正常执行（含 AI flow 的 Build/Parse 两个 Code 步）；仅用内置依赖时无报错 | FR-F03B、BR-22 |
| SG-3 | **Network deny** | 默认拒绝出网生效（云 metadata 169.254.169.254 不可达）；**且 deepseek/内部服务等白名单出网正常** | FR-K03、NFR-S02 |
| SG-4 | **Webhook** | Path B 同步 webhook（引擎→AP→回值）在该模式下端到端正常，含 `/sync` 返回值回填 | FR-E01、GW-2 |
| SG-5 | **Socket** | engine↔worker 的 WS-RPC（端口段 52001-53000）在 iptables 锁定下不被误杀；画布 test run 进度事件正常 | §1.5、FR-I01 |
| SG-6 | **Redis** | worker 队列消费/分布式锁在 STRICT 出网策略下正常（AP 独立 Redis DB 号前提下） | §5.3 |
| SG-7 | **Filesystem** | isolate mount 约束（所有挂载须在 `/root/` 下）与我方 cache 布局兼容；跨 job 无残留、无跨 flow 可读面 | §4.4 剩余风险① |
| SG-8 | **Performance** | 每 job 新建沙箱的开销可接受：Path B 常规 piece < 10s、AI flow ≤ 300s 仍满足；四层超时嵌套重新标定 | NFR-P01、§2.4 铁律 2 |

#### 4.5.3 降级阶梯（SG 某项失败时的有序回退，避免"全有或全无"）

1. `SANDBOX_CODE_AND_PROCESS` + `STRICT`（候选目标）
2. `SANDBOX_CODE_AND_PROCESS` + `UNRESTRICTED` + **容器/NetworkPolicy 兜底出网**（若 SG-3 的
   iptables 与白名单调和不了）
3. `SANDBOX_CODE_ONLY` + `STRICT`（若 isolate 进程层与 FR-F03A 预装机制不可调和——SG-1 失败）
4. `UNSANDBOXED` + `STRICT` + **强化外部兜底**（最低限度；必须在 Document 8 记录为已知残留风险
   并附补偿控制：容器非 root、只读 rootfs、NetworkPolicy、engineToken 轮换）

**任何降级都必须在 Document 8 留档**：降到哪一级、因为哪项 SG 失败、补偿控制是什么、复评时间点。
- CODE step 能力收窄（呼应 §3.3 与 FR-F03B）：AP 原生按模式控制——`SANDBOX_CODE_ONLY` 本就禁 npm 依赖，
  `SANDBOX_CODE_AND_PROCESS` 允许。**v1 取舍：采用 `SANDBOX_CODE_AND_PROCESS`（保留进程+网络隔离），
  同时用 FR-F03B 的设计期校验禁止 CODE step 声明外部 npm 依赖**（既满足去 bun，又不牺牲隔离强度）。
  注意二者是**不同层的约束**：沙箱模式管隔离，FR-F03B 管依赖来源——Document 4/8 需分别落实。

## 5. 持久化、迁移与独立 schema 落地点（Q5）✅ 已按 0.84.0 核实

### 5.1 独立 schema（Q5）落地清单——改造面小且集中

AP 0.84 **原生不支持指定 schema**（两处 `new DataSource` 均无 schema 属性，全仓无 search_path/
`AP_POSTGRES_SCHEMA`），落地只需（路径相对 `packages/server/api/src/app/`）：

1. `helper/system/system-props.ts`：增 `POSTGRES_SCHEMA` 枚举（env `AP_POSTGRES_SCHEMA`，默认 public）；
2. `database/postgres-connection.ts` **L792 与 L810** 两处 `new DataSource` 加 `schema:` ——
   一处改动覆盖全部实体表 + 迁移建表 + `migrations` 记账表；
3. `database/index.ts`（L4-10）`initialize()` 前 `CREATE SCHEMA IF NOT EXISTS`（TypeORM 不自动建）；
4. 3 个早期迁移的 `table_schema='public'` 参数化（`1674788714498:14`、`1676238396411:16`、
   `1677894800372:13`——全仓仅此 3 处硬编码 public；全新 schema 首装本就安全，为正确性建议改）；
5. 应用层裸 SQL 全为非限定表名，随 schema 自动跟随，**无需逐条改**；
6. `CREATE EXTENSION vector`（knowledge-base）是 **DB 级**，与 schema 无关（我们不用可忽略，建表仍会跑）。

### 5.2 迁移体系与基线收编（NFR-D01）

- **启动自动迁移，硬编码**：`postgres-connection.ts:783` `migrationsRun:true` + `main.ts:62`
  `runMigrations:true`，多副本安全靠分布式锁 `database-migration-lock`；`synchronize:false`。
  关闭自动迁移需代码级 env 化（建议做：生产改独立迁移 job，审计友好）。
- **0.84 基线 = getMigrations() 注册 373 条**（postgres；0.86.2 为 389）。收编方案：导出 373 条名单
  固化为 `baseline-0.84.0.manifest`；自维护迁移时间戳 > `1793000000000` 追加在数组末尾；
  **0.84 原生自带** `rollback-migrations.ts`（`rollbackToManifest`/`rollbackToVersion`/
  `verifyDatabaseState`）与 `Migration.release/breaking` 元数据——受控 append-only 的守卫工具现成。
- 迁移不分 edition：EE/CLOUD 表照建（空表）。0.84 实测 **55 个 ORM 实体 + agent/agent_runs/mcp_tool/
  issue/todo/todo_comment 等非实体表 ≈ 61+ 张**；0.84 有 tables 产品（table/field/record/cell）、
  mcp_*、knowledge_base_*、chat_conversation、user_badge（0.86 才删）；无 ai_tool_config 等 0.86 新表。

#### 5.2.1 迁移 ownership —— **AP 与 HERMES 不得共用 migration lifecycle**（Document 4 定案）

目标形态：同一个 PostgreSQL 实例、两套**互不干涉**的 schema 与迁移生命周期。

```
HERMES DB
├── public          ← HERMES 表；由 HERMES 自有迁移机制管理
└── activepieces    ← AP 表 + AP 自己的 `migrations` 记账表；由 AP 迁移集管理
```

**推荐方案（倾向明确）：独立的 AP Migration Job，只管理 `activepieces` schema。**

```
AP Migration Job  →  仅 activepieces schema
HERMES Migration  →  仅 public schema
```
**不采用**"HERMES migration job 同时驱动两套 schema"——那会把 vendor 边界重新搅浑（HERMES 的发布
流程要理解 AP 的迁移集、AP 升级要动 HERMES 的迁移编排）。

**支撑理由**：

1. **vendor 边界清晰**：AP 迁移集是 vendored 上游资产（0.84 基线 373 条 + 我方 append-only 增量），
   它的演进节奏、回滚工具（`rollback-migrations.ts` 的 release/breaking 元数据）都自成体系，
   不应嵌进 HERMES 的发布编排。
2. **记账表天然分离**：设置 DataSource `schema` 后，AP 的 `migrations` 表落在 `activepieces` schema，
   与 HERMES 自己的迁移记账互不可见——**无需额外设计即成立**。
3. **发布节奏解耦**：合并 job 会让"HERMES 发版"被 AP 迁移成败阻塞，反之亦然；分离后
   AP 升级/回滚不牵动平台发布窗口。
4. **回滚独立**：AP 有自己的按清单/按版本回滚工具；混在一起时一侧回滚可能被另一侧的记账顺序卡住。
5. **故障隔离**：AP 迁移失败不应阻断 HERMES 应用启动（反之亦然）。

**落地要点**：

- **权限即边界（关键，把约定变成强制）**：给 AP 独立 DB 角色，**仅对 `activepieces` schema 授予
  DDL/DML**，对 `public` 无权或只读。这样"AP 迁移越界改平台表"在数据库层面就不可能发生，
  不依赖流程纪律。同理 HERMES 角色对 `activepieces` 无 DDL 权。
- **schema 创建与授权属于基础设施一次性动作**（DBA / 部署编排），**不归任何一方的迁移集**——
  因为 TypeORM 不会自动建 schema（§5.1 第 3 点），且建 schema 需要的权限高于日常迁移。
- **AP 迁移执行方式**：按 §5.2.2 的正式 Migration Lifecycle 治理（`AP_AUTO_MIGRATE` 分环境 +
  生产独立 Migration Job + manifest 漂移校验），不是简单的"加个开关"。

#### 5.2.2 AP Migration Lifecycle（正式化治理，非配置项）

> 既然 0.84 基线要冻结为 manifest（NFR-D01），迁移就是**受治理的资产**，须有明确的产物、
> 执行者、门禁与回滚路径。以下为 Document 4 的落地设计输入。

**① 治理产物（入 git，评审对象）**

| 产物 | 内容 | 可变性 |
|---|---|---|
| `baseline-0.84.0.manifest` | 冻结的上游 373 条 migration 名（0.84 `getMigrations()` 全集） | **不可变**（仅在有意升级 AP 基线时整体替换并重新评审） |
| `hermes-migrations.manifest` | 我方 append-only 增量（时间戳 > `1793000000000`） | 只增不改不删 |
| 期望态 | `baseline + hermes` 两份合集 | 用于漂移校验 |

**② 环境配置矩阵（采用 `AP_AUTO_MIGRATE`）**

| 环境 | `AP_AUTO_MIGRATE` | 迁移执行者 | 理由 |
|---|---|---|---|
| dev / 本地 | **`true`** | 应用启动时自动跑 | 降低本地心智负担，改 schema 即生效 |
| test / uat / preprod / **prod** | **`false`** | **独立 AP Migration Job** | 受控、可审计、与应用启动解耦 |

代码改动点（vendored CE，HERMES-PATCH 标记）：`postgres-connection.ts:783` 的 `migrationsRun`
与 `main.ts:62` 的 `runMigrations` 均改读该 prop（`AppSystemProp.AUTO_MIGRATE`，默认 `true` 保持
上游行为兼容）。AP 原生分布式锁 `database-migration-lock` 保留，仍保证多副本/多 job 并发安全。

**③ 部署时序（生产，硬顺序）**

```
1. AP Migration Job 启动（独立 k8s Job / CI 步骤，用仅对 activepieces schema 有 DDL 权的角色）
2.   → 前置校验 verifyDatabaseState()：实际 migrations 记账表 ⊆ 期望态，且顺序一致
3.   → 执行 runMigrations()（仅 activepieces schema）
4.   → 后置校验：执行后记账表 == baseline + hermes manifest（**零漂移**）
5. 校验通过 → 应用 Pod 才允许滚动（AP_AUTO_MIGRATE=false，Pod 自身永不跑迁移）
6. 任一步失败 → 阻断应用部署，不进入半迁移状态
```

**④ 变更门禁（新增一条自维护迁移的准入）**

- 时间戳 > 基线最大值 `1793000000000`，追加在 `getMigrations()` 数组**末尾**；
- 必须填 `Migration` 的 `release` / `breaking` 元数据（`migration.ts:3-7` 原生支持）；
- 同步写入 `hermes-migrations.manifest`（**manifest 与代码不一致即 CI 失败**）；
- 走代码评审；`breaking: true` 的迁移额外需架构评审 + 回滚预案。

**⑤ 漂移检测（防止"谁手工在库上动了 DDL"）**

CI/运维定期跑 `verifyDatabaseState()` + manifest 比对：记账表出现**期望态之外的行**（如误引入上游
新迁移、手工执行）立即告警。这是 Q8「纯自维护、不跟随上游」能落地的实际保障——否则某次误操作
把上游迁移引进来，之后无人知晓。

**⑥ 回滚路径**

用 AP 原生 `rollback-migrations.ts`：`rollbackToManifest({ targetMigrationNames })` 回到指定 manifest
状态，或 `rollbackToVersion()` 按 `release` 语义版本回滚；`breaking` 迁移受 `--force` 保护。
**回滚同样只在 `activepieces` schema 内进行**，不触碰 HERMES 迁移状态（§5.2.1 的 ownership 隔离
在回滚场景同样成立）。

### 5.3 Redis —— **生产必须独立实例，逻辑 DB 号仅够开发/测试**

**AP 的 Redis 用途远不止普通缓存 key**：BullMQ 队列（`workerJobs` + `runsMetadata`，0.84 已是双队列，
attempts:2 指数退避 8min）、PubSub（`piece-registry-invalidation` 频道）、分布式锁（含
`database-migration-lock`）、并发池状态（`concurrency-pool:*`）、metadata 缓存。

**无任何 key 前缀**（BullMQ 默认 `bull:*`、ioredis 无 keyPrefix、业务 key 与 pubsub 频道全裸命名空间）
——但**key 冲突只是最表层的问题**。

#### 5.3.1 隔离分级（生产 vs 开发，**不是等价备选**）

| 环境 | 方案 | 定位 |
|---|---|---|
| **生产 / preprod** | **独立 Redis 实例（或托管 Redis）** | **必须**——理由见 5.3.2 |
| dev / 测试 | 独立逻辑 DB 号（`AP_REDIS_DB`） | 够用，仅隔离 keyspace |

#### 5.3.2 为什么逻辑 DB 号在生产不够（逻辑 DB **不隔离**下列任何一项）

1. **⚠️ eviction policy —— 正确性问题，非性能问题**：BullMQ 要求 `maxmemory-policy: noeviction`，
   而该策略是**实例级全局配置**。若平台 Redis 为缓存用途配了 `allkeys-lru`/`volatile-*`，
   AP 的队列 job 会在内存压力下**被静默驱逐** → **工作流执行凭空丢失、无错误日志**。
   逻辑 DB 号对此毫无防护。
2. **persistence（RDB/AOF）同样是实例级**：平台若为纯缓存关闭持久化，AP 队列即失去崩溃恢复能力
   （与 NFR-A02"worker 崩溃重启后队列任务不丢"直接冲突）。
3. **failure domain 共享**：实例 OOM / 重启 / 主从切换 / 误 `FLUSHALL` 一并影响平台与 AP。
4. **CPU 共享且 Redis 单线程**：任一侧的慢命令或大 key 操作阻塞另一侧；AP 队列积压时的
   高频轮询会挤占平台业务缓存的响应。
5. **memory 共享**：AP run 数据/队列增长可挤爆平台缓存，反之亦然，无配额边界。
6. （若平台用 Redis Cluster）BullMQ 需 hash tag 支持，与通用缓存用法的集群配置可能不兼容。

#### 5.3.3 落地要求

- 生产：AP 独立实例，`maxmemory-policy=noeviction`、开启持久化、容量与队列增长挂钩监控
  （队列积压、失败 job 保留 `AP_REDIS_FAILED_JOB_RETENTION_DAYS=30`/`MAX_COUNT=100000` 需纳入容量测算）。
- dev：`AP_REDIS_DB` 独立号即可，但**须在文档标注"与生产拓扑不同"**，避免"dev 能跑"被误当作生产验证。
- 前缀级隔离（`new Queue` 的 `prefix` + ioredis `keyPrefix` + pubsub 频道名）属代码级改造，
  **不作为替代独立实例的方案**——它同样解决不了 5.3.2 的 1–5 项。

### 5.4 ⚠️ 生产配置纠错（立即可做的存量清理）

- **`AP_PIECES_SOURCE` 在 0.84.0 整仓库不存在**——我们 compose/k8s 里的 `AP_PIECES_SOURCE=DB`
  是无效配置（从未被读取）。真正生效的是 `AP_PIECES_SYNC_MODE=NONE`
  （`PieceSyncMode={OFFICIAL_AUTO,NONE}`，NONE=禁云端元数据同步，语义正是我们要的）。
  → 处置：删除各环境的 `AP_PIECES_SOURCE`，保留 `SYNC_MODE=NONE`（防误导后人）。
- 关键默认值（`helper/system/system.ts` 实测）：`AP_EXECUTION_MODE=UNSANDBOXED`（默认！见 §4 沙箱）、
  `AP_WEBHOOK_TIMEOUT_SECONDS=30`（我们已覆盖为 300）、`AP_FLOW_TIMEOUT_SECONDS=600`、
  `AP_DB_TYPE=POSTGRES`、`AP_DEFAULT_CONCURRENT_JOBS_LIMIT=5`；`AP_ENCRYPTION_KEY`/`AP_JWT_SECRET` 无默认必须显式配。

## 6. Web 构建链与样式隔离面（Q1/Q3/CR-03）✅ 已按 0.84.0 核实

### 6.1 技术栈事实（0.84.0 实测，`packages/web/`）

- **React 19** + react-router-dom 6.11.2 + zustand 4.5.4 + TanStack Query 5.51.1；画布 = **@xyflow/react 12.3.5**（React Flow）+ dnd-kit；编辑器大户：@tiptap/*（20 包）、CodeMirror 6、shiki 4；socket.io-client 4.8.1。
- **Tailwind v4.1.17（CSS-first）**：无 tailwind.config 文件，配置全在 `src/styles.css`（889 行）；
  PostCSS 走 `@tailwindcss/postcss`；shadcn `components.json` `prefix:""`（工具类无前缀）、ui 组件 42 个。
- 构建：Vite（标准 SPA 产物，无 lib mode）+ Turbo 编排；`vite.config.mts` alias 仅 4 条：
  `@`→`./src`、`@activepieces/shared`→`../../packages/shared/src`、`@activepieces/pieces-framework`、
  **`ee-embed-sdk`→`../../packages/ee/embed-sdk/src`**。
- 遥测：0.84 是 **@segment/analytics-next + posthog-js**（无 Sentry），断网/合规须构建期禁用。
- i18n：i18next + **i18next-http-backend 运行时 fetch `/locales/{lng}/translation.json`**（非内联），
  语言包在 `public/locales/`（11 语言）；字体全本地（Inter + Sentient @font-face），仅 index.html
  的 fallback favicon 是外链（`https://activepieces.com/favicon.ico`，须改本地）。

### 6.2 ⚠️ 许可红线新发现：web 依赖 `ee-embed-sdk`

`packages/web` 的 package.json 与 vite alias 均引用 **`ee-embed-sdk`（workspace，EE 商业许可）**。
按 NFR-S03（构建产物零 ee 代码），抽取 builder 组件时必须：定位 web/src 对 `ee-embed-sdk` 的全部
import 点 → 以桩替换或裁掉相应功能（embed/postMessage 相关，正好是我们不需要的官方嵌入通道）。
**列入 Document 4 必办项与 GW-11 扫描范围。**（import 点清单待分析线 1 的 builder 闭包结果补充。）

### 6.3 样式隔离风险清单（0.84 实测行号）

全局污染源全部在 `src/styles.css`（0.84 的 `styles/globals.css` 是死文件、未被 import）：

| # | 风险 | 证据 | 严重度 |
|---|---|---|---|
| 1 | Tailwind v4 preflight 全局 reset（污染 Element Plus 按钮/边框/列表/图片） | `styles.css:1` `@import 'tailwindcss'` | ★★★★★ |
| 2 | 自写全局规则：`*` 边框色（357–365、536–538）、全局滚动条（544–588）、`body` 背景/字体（590–592、654–660）、`button`/`[role=button]` 光标（596–598）、`body pointer-events !important`（632–634）、`[data-state]` 属性选择器（636–651） | `styles.css` 各行号 | ★★★★★ |
| 3 | Portal 逃逸到 `document.body`：radix Portal ×9 文件（dialog/sheet/popover/dropdown/select…）、sonner、vaul、cmdk；`createPortal/document.body` 18 处 | `components/ui/*` | ★★★★☆ |
| 4 | focus-trap 抢焦点（radix focus-scope）与 EP dialog 同屏冲突 | dialog/sheet/popover | ★★★☆☆ |
| 5 | 全局事件监听 30 处（main.tsx 的 error/unhandledrejection 等不得随组件带入） | `src/main.tsx` 等 | ★★☆☆☆ |
| 6 | z-index：0.84 上限 `z-[200]`（比 0.86 的 9999 温和），与 EP(2000+) 需统一规划 | grep 实测 | ★★☆☆☆ |
| 7 | 无前缀工具类理论撞名（EP 用 `.el-` 前缀，实际概率低） | `components.json` | ★★☆☆☆ |

### 6.4 打包与隔离对策（**候选方向，未经 PoC 验证——见 §6.5**）

> ⚠️ 措辞纪律：本节是**候选方向**，不是已验证结论。静态源码分析只能证明"builder React tree
> 可从路由系统切出"；**"独立 lib → 宿主 React → Shadow DOM → ReactFlow → Radix Portal →
> Socket.io → TanStack Query → i18n → Tailwind v4"整条链路能否稳定运行，必须由 PoC 实证**，
> 不得在 PoC 前写成定论。

- **候选主方向：Vite library mode 预打包 builder + Shadow DOM 挂载**（React 19 root render 进 shadow root），
  意图一次性挡住 preflight、自写全局规则、字体改写、focus/portal/z-index 冲突。内部源码依赖仅 3 个
  （shared / pieces-framework / ee-embed-sdk→桩）。**此方向的每一环节均列入 §6.5 强制 PoC。**
- **候选回退方向（若 Shadow DOM 阻塞）**：不用 Shadow DOM，改 v4 分层导入去 preflight
  （`@import 'tailwindcss/theme.css' layer(theme)` + utilities，不引 preflight.css）+ PostCSS 给全局选择器
  加 `.ap-builder` 作用域 + Tailwind `prefix`。工程量更大，但规避 Shadow DOM 与 React/ReactFlow 的已知摩擦。
- 断网清单（与打包方向无关，均须做）：禁用 segment/posthog 初始化、favicon 本地化、`public/locales` 随宿主同源部署。

### 6.5 Q3 落地 PoC —— **✅ P1–P4 已实测通过（2026-07-22）**

**PoC 环境**：独立最小复现（scratchpad `q3-poc`），**精确对齐 0.84 版本**——react/react-dom **19.0.0**、
@xyflow/react **12.3.5**、radix-ui **1.4.3**、tailwindcss **4.1.17**；宿主 = Vue3 + Element Plus（模拟 DW）+
`@vitejs/plugin-vue` 与 `@vitejs/plugin-react` 同 Vite 共存；builder = React 19 root 挂进 **Shadow DOM**，
Tailwind CSS 以 `?inline` 注入 shadow `<style>`。全部经浏览器真实交互 + JS 断言核实。

| PoC | 结论 | 实测证据 |
|---|---|---|
| P1 React 19 单实例 | **✅ PASS** | React 19.0.0/ReactDOM 19.0.0 在 shadow 内挂载，零 console error，无 Invalid hook call |
| P2 Shadow DOM + React Flow | **✅ PASS** | fitView 算出真实 viewport（`scale(1.47)`，ResizeObserver 在 shadow 内工作）；**真实拖拽**节点 `translate(40,40)→(-26,91)` 且只动被拖节点；边跟随重路由；**真实滚轮缩放** `scale 1.47→1.94`（d3-zoom）；minimap + 4 control 按钮齐全 |
| P3 Shadow DOM + Radix Portal | **✅ PASS** | DropdownMenu **和** Dialog（含 overlay）经 `container` prop 全部渲染在 shadow root 内（`getRootNode()===shadow`），**零泄漏到 document.body**；两个不同基元均验证 |
| P4 Shadow DOM + Tailwind v4 | **✅ PASS** | preflight + 全部 CSS（28997 字符含 ReactFlow 样式）**全在 shadow root、不在 document.head**；宿主 EP **零污染**：原生 li 仍 `disc` 圆点、EP 按钮仍 EP 蓝 `rgb(64,158,255)` |
| P5 Socket.io + Query + i18n | ⏭ **未纳入本 PoC（低风险）** | 三者是数据/网络层，不与 shadow 边界产生 DOM 冲突（socket.io 任意环境可用、TanStack Query 纯 JS、i18n 仅 loadPath 配置）；并入 Layer 1 实建时验证 |

**关键工程结论（进 Document 4 Layer 1 设计）**：
1. **样式隔离靠"CSS 以 inline 字符串注入 shadow `<style>`"实现**——不能让 Tailwind 进 document.head，否则 preflight 污染 EP。
2. **Radix 每个用 portal 的组件都必须显式传 `container`**（指向 shadow mountPoint）——DropdownMenu.Portal / Dialog.Portal 均需，实建时对 builder 用到的全部 radix 组件逐个接线。
3. **shadow root 只 attachShadow 一次**，React root 在其内反复 mount/unmount——PoC 实测 **11 次挂卸循环零累积/零泄漏**（1 shadow root、1 renderer、light DOM 零残留）。`attachShadow` 不可重复调用是必须绕开的坑。
4. React Flow 容器需显式尺寸。

**诚实边界（残留风险，非本 PoC 覆盖）**：本 PoC 验证的是**框架级链路**（React19 → Shadow DOM →
ReactFlow → Radix → Tailwind v4）——这一层**empirically 稳**。**未验证**的是真实 AP builder 的**体量**：
CodeMirror/tiptap/更多 radix 组件/完整 builder-state/socket 驱动的 test run 等,以及 §1.4 的 8 个
注入切点在真实代码上的改造。**结论：Q3 主方向（lib mode + Shadow DOM）从"候选"升级为"经框架级 PoC
验证可行"；残留风险转移到"AP 特有组件的量 + 8 切点改造",不再在核心渲染链路。** §6.4 回退方向
（scope+prefix）降级为"仅当真实 builder 引入某个 shadow-不兼容组件时才需"。

## 7. 综合结论与 Document 3/4 输入 ✅

### 7.1 六条分析线一句话结论

| 线 | 结论 | 对集成的意义 |
|---|---|---|
| 1 Builder 闭包 | Q3 React tree 可从路由切出（**整体运行链路待 §6.5 PoC 实证**）；CE 已内置 embedding 模式（MemoryRouter+开关体系）可复用；ee 触点仅 1 处 | Q3 路径可行，核心是 8 个注入切点 + 5 项 PoC |
| 2 authn/供给 | per-user 供给可全自动（PLATFORM 邀请链）；**但 CE 无共享 project → Q4a=C 补丁** | Q4 落地路径清晰，需一处 RBAC 补丁 |
| 3 bun | 引擎/测试/构建均与 bun 解耦；运行时 bun 仅两处（piece 安装 / CODE step 依赖），FR-F03A/B 关停即消 | 去 bun 风险低 |
| 4 沙箱 | 默认 UNSANDBOXED 强度弱；须强制 SANDBOX_CODE_AND_PROCESS+STRICT | 安全基线硬约束，见 7.3 |
| 5 持久化 | 独立 schema 一处改；迁移 append-only 有原生工具；Redis 无前缀需独立 DB | Q5 风险低 |
| 6 web/样式 | Tailwind v4 preflight+全局规则污染 EP；候选 lib mode+Shadow DOM（**待 §6.5 PoC**） | Q3 隔离方案候选，未定 |

### 7.2 模块依赖与"不可独立抽取"判定（供 Document 3 DEPENDENCY_MAP）

- **前端 builder**：可抽取，但依赖三个 workspace 内部包（`shared`/`pieces-framework`/`ee-embed-sdk→桩`）
  与全局单例（ApStorage/api/socket/queryClient/router）；单例是"改造后可解"而非"不可切断"。
- **server engine/worker**：**不可拆分**——worker 经 socket.io RPC 调 API 出队、engine 子进程由 worker
  fork/isolate、piece 在 engine 进程内 import；三者是一个紧耦合运行体，只能整体作为 TS 服务运行
  （印证 CR-05：不做 TS→Java 翻译，Java 侧只经 HTTP）。
- **shared 包**：前后端类型契约（flow schema/TypeBox），前端 builder 与 server 都依赖——是天然的
  跨语言契约边界（DW 可 import 用于类型安全）。

### 7.3 架构风险 Top 10（给 Document 4/8，按严重度）

1. **默认沙箱弱（UNSANDBOXED+UNRESTRICTED）不可上生产**：engineToken/跨 flow 文件面暴露。
   **候选基线** `SANDBOX_CODE_AND_PROCESS+STRICT` **须过 Security Architecture Gate（§4.5.2，
   SG-1~SG-8）方可转正**——它与 FR-F03A 预装 pieces、isolate rootfs、AI Generate 出网白名单、
   同步 webhook 耗时预算均有联合验证问题。**SG 是最高优先级实验，且带降级阶梯（§4.5.3），
   任何降级须在 Document 8 留档补偿控制。**
2. **worker 运行时 bun 安装（两条路径，风险分离）**：FR-F03A 关运行时 piece 安装（运维/资产影响）；
   FR-F03B 关 CODE step 依赖安装（**产品能力影响**——需设计期 fail-loud 校验 + 替代指引，
   否则开发者会在运行期才发现 flow 写不出来）。能力矩阵见 REQUIREMENTS F 组。
3. **Tailwind v4 preflight + 全局规则污染 Element Plus**：**§6.5 PoC 已实测 lib mode + Shadow DOM 方案可行**（P2/P3/P4 全绿，EP 零污染）——风险从"高"降为"中"，残留在真实 builder 组件体量而非渲染链路。
4. **CE 无共享 project**（Q4a=C）：**Q4a-Sec 所有权验证门已过（§2.6）**——所有资源
   （Flow/Connection/Run/Trigger/凭据）在 0.84 CE 均 **project-scoped，ownerId 仅展示、不参与访问控制**，
   共享方案无 user-scoped 缺口。落地降级为"供给时给共享 project 插 project_member 行"（可零源码），
   风险从"高"降为"低"。唯一注意：connection TABLE 鉴权取 projectIds[0]，共享场景 connection 只挂 shared 一个 project。
5. **ee-embed-sdk 进构建产物**（web 依赖）：桩掉 home-button 的 1 处枚举，CI guard 强制（GW-11）。
6. **Redis 共用（生产须独立实例，§5.3）**：AP 用 Redis 做 BullMQ 队列/PubSub/分布式锁/并发池，
   且无 key 前缀。**逻辑 DB 号仅够 dev**——生产共用实例的真正风险是 **eviction policy 实例级全局**
   （平台若配 `allkeys-lru`，AP 队列 job 会被静默驱逐 = **工作流执行丢失**，与 NFR-A02 冲突）、
   persistence 配置共享、failure domain 与 CPU/内存共享。生产必须独立 Redis 实例 + `noeviction` + 持久化。
7. **builder API 依赖面远超预期（§1.5）**：**39 个同源端点 + 16 个 WS 事件**（早前文档只列 5 个）。
   要害：① 测试/观测 **100% 依赖 socket.io 无 HTTP 兜底**（Kong 必须支持 ws 升级）；
   ② `POST /v1/flows/:id` 是单点写路径，失败即整个编辑会话静默停止落库；
   ③ `POST /v1/pieces/options` 会同步跑引擎作业、需放宽超时且非无状态；
   ④ 首屏请求放大 ~2N+2M（20 步 flow ≈ 80 请求）；⑤ `/v1/pieces/:name` 路径含未编码斜杠，
   网关重写易踩坑。
8. **per-user 供给的架构边界问题（P0-4）→ ✅ 已有可落地解**：现有 HTTP 邀请链路的两个缺陷
   （依赖"AP SMTP 未配置"+ 平台托管密码）经调研确认可**彻底消除**——采用 **Option B：vendored CE
   内新增内部 provisioning 端点**，由 AP 自身 domain service 执行（有 `managed-authn-service.ts`
   逐行模板，邀请门禁在 `signUp` 编排层故天然绕开）。风险从"高"降为"低"。**剩余待定**：加入共享
   project 走 `project_member` 行（精确但触 ee/ 目录）还是 `platformRole:OPERATOR`（不碰 ee/ 但越权面大）
   ——与 Q4a 的交叉决策，Document 4 定。直写 DB 仍**仅 break-glass**。
9. **独立 schema 的 3 处硬编码 public 迁移**：参数化，否则重跑误判。
10. **bun→pnpm 锁文件重建的版本漂移**：并入 frontend 单锁（Q1），关键依赖（typebox/fastify/TypeORM）逐一核对。

### 7.4 对 Document 3/4 的直接输入

- **Document 3（DEPENDENCY_MAP）**：以 7.2 为骨架——标注 engine/worker/api 为"不可拆分运行体"、
  builder 的注入切点图、shared 作为跨语言契约边界；**外加 §1.5 的四段式 API 契约表
  （Mount / Edit / Run / Observe，含 WebSocket 事件与缺失降级表现）——它同时是 Document 4
  网关路由设计的输入**。

  **Document 3 必须正面回答的核心问题（模块处置矩阵）**：
  > "Activepieces 到底**哪些模块进入 HERMES**，**哪些被裁掉**，**哪些通过 HTTP 保留**，
  > **哪些必须整体部署**？"

  须对 0.84 的每个包/模块给出四选一的处置结论 + 理由 + 证据：

  | 处置类别 | 含义 | 已知候选（待 Document 3 逐项定案） |
  |---|---|---|
  | **A. 进入 HERMES 代码库** | vendored 并进我方构建产物 | builder 组件（裁剪后的 `packages/web` 子集）、`packages/shared`（类型契约） |
  | **B. 裁掉** | 不构建、不部署、不分发 | `packages/ee/**`（商业许可，NFR-S03）、非 builder 路由（auth/platform-admin/public/embed 等，§1.6）、approval·todos piece（Q9） |
  | **C. 通过 HTTP 保留** | 作为独立服务运行，Java/DW 侧只经 HTTP 交互 | `packages/server/api`（flows/runs/webhooks/pieces/authn…）——既有 `ActivepiecesApiClient`/`ApTaskExecutor` 即此模式（CR-05：不做 TS→Java 翻译） |
  | **D. 必须整体部署（不可拆分）** | 紧耦合运行体，只能整体起 | `server/api` + `server/engine` + `server/worker`（§7.2 已证：worker 经 socket.io RPC 向 API 取任务、engine 由 worker fork/isolate、piece 在 engine 进程内 import） |

  注意 C 与 D 并非互斥：`server/api` 既是"对我方以 HTTP 交互"（C），又属于"必须与 engine/worker
  整体部署"的运行体（D）——Document 3 需明确区分**交互方式**与**部署单元**这两个维度。
  另需标注边界情形：`packages/pieces`（框架 + 白名单 piece 进构建期预装，属 A/D 混合）、
  `packages/cli`（仅开发态工具，是否随树保留待定）、`packages/tests-e2e`（裁或留作回归资产待定）。
- **Document 4（INTEGRATION_DESIGN）九层**：
  - Layer 1（DW canvas-host）：**先做 §6.5 P1–P5 PoC**，据结果定打包/隔离方案（候选 lib mode +
    Shadow DOM）+ 8 切点注入 provider；PoC 是 Layer 1 设计的前置门，不是可选项；
  - Layer 2（Workflow API）：AP server-api 经 Kong（含 websocket），生产仅暴露 webhooks；
  - Layer 3–5（core/engine/worker）：整体 TS 服务；沙箱/网络配置写作**候选基线
    `SANDBOX_CODE_AND_PROCESS+STRICT` + 待 SG 验证**（§4.5.2），**不得直接写成生产基线**；
  - Layer 9（持久化）：独立 schema（一处 DataSource 改）+ **独立 AP Migration Job 只管
    `activepieces` schema、DB 角色权限强制边界**（§5.2.1）+ **生产独立 Redis 实例**（§5.3）。
- **强制验证项清单**（进 TEST_STRATEGY）：**SG-1~SG-8 安全架构门**（§4.5.2，含 piece loading ×
  isolate rootfs、STRICT 下 deepseek 出网白名单、Path B/socket/Redis/性能）、GW-8（Shadow DOM
  样式隔离）、GW-11（无 bun/无 ee 扫描）、GW-12（workspace 合并回归）、Q4a 共享 project 权限回归。

### 7.5 版本纪律备注（见 §8 之后续）

全部结论基于 0.84.0 快照（与官方 tag 逐字节一致）。分析过程中数个子代理初次落到 Desktop 的
0.86.2 clone，已全部按 0.84.0 快照重核；0.86 vs 0.84 的实质差异（PIECES_SOURCE 废除、
core/ 包重构、Dockerfile 拆分、user_badge/ai_tool_config 表增删、迁移 373→389）已在各节标注，
**禁止将 0.86 结论用于 0.84 落地**。

---

## 8. Open Architecture Gates → 已迁出

> 本章内容已迁至 **[OPEN_GATES.md](OPEN_GATES.md)**（阻塞项唯一总账），并按 D2 重定义了 AG-02、
> 按 D3 新增了 AG-EE；原 `SG-1~8` 与 `P1~P5` 已并入 AG-05 / AG-04 的子项编号，不再独立编号。
>
> 本文档（Document 2）的定位回归**源码逆向证据**；决策见 [DECISIONS.md](DECISIONS.md)，
> 当前状态见 [STATUS.md](STATUS.md)。
