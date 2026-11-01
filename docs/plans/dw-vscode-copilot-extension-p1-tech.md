# P1 技术方案：DW VS Code 扩展 — FU

> 业务方案：[dw-vscode-copilot-extension.md](dw-vscode-copilot-extension.md)（P0 已全部通过）。
> 本文定「怎么做」；拍板后实现期不打断（skill `implementation-autonomy`）。
> 方案里引用的类 / 方法 / 端点均已在 2026-11-01 的代码里 Grep 核实。

## 0. 对业务 Plan 的一处修正（需你确认）

业务 Plan 写的安全网是「apply 前先存 DW 版本快照」。**核实后做不到**：

- `dw_versions` 只在 **Publish** 时产生（`FunctionUnitComponentImpl.doPublish` → `VersionComponent.createVersion`），Publish 会先跑完整性校验、改 FU 状态，一个只有表的草稿 FU 根本发布不了。
- 现有能力里没有「不发布、只拍快照」的入口。

**替代方案（推荐）**：apply 前扩展自动调现有 `GET /export-import/function-units/{id}/export`，把 zip 备份存到扩展的全局存储 `<globalStorage>/backups/<host>/<fuId>/<时间戳>.zip`（不放工作区，免得被误提交进 git；命令 **DW: Open backups folder** 打开）。
恢复走现有 `POST /export-import/import`：同名导入 = 「snapshot current content, then replace with the imported package」（`ExportImportController` 的接口说明），既能恢复又会把出错版本留成一个版本。
可撤销的 7 个 scope 仍额外给出 `/undo`（内存、DW 重启即失）。

不新做服务端快照能力：那会动 `dw_versions` 语义和版本回滚链（skill `function-unit-version-rollback`），属于架构级改动，P1 不碰。

## 1. 改哪些文件

### 1.1 后端（developer-workstation）——只新增只读端点，不动写路径

| 文件 | 改什么 |
|---|---|
| `controller/AiStudioChatController.java` | 新增 3 个端点（见下表），权限 `@RequireDeveloperPermission("FUNCTION_UNIT_VIEW")` |
| `component/AiStudioChatComponent.java` + `component/impl/AiStudioChatComponentImpl.java` | 新增 `dryRun(AiStudioApplyRequest)`、`getDesignContext(Long functionUnitId)`、`getFormatSpec()` |
| `service/AiGenerationService.java` + `service/impl/AiGenerationServiceImpl.java` | 把现有 `private buildSchemaMetadata()` 提为接口方法 `getSchemaMetadata()`（原调用点 `body.put("schemaMetadata", …)` 改调它，行为不变） |
| `src/test/.../component/impl/AiStudioChatComponentImplTest.java` | 新增 dry-run / context / format-spec 用例 |

| 端点 | 作用 | 实现 |
|---|---|---|
| `POST /ai-generation/studio-chat/dry-run` | 只校验不写库；返回会新增/更新/替换什么 + ERROR/WARN | 入参复用 `AiStudioApplyRequest`；与 `applyProposal` **完全相同**的前两步：`retainAll(AiStudioChatServiceImpl.allowedSlices(scope))` 裁切片 → `normalizedData(sliced)`；然后 `previewer.preview(functionUnitId, scope, data)`。工作区校验 `assertCanAccess(id, VIEW)`。**不加** `aiLockService` 锁（只读） |
| `GET /ai-generation/studio-chat/design-context?functionUnitId=` | 读 FU 当前完整设计，给 Copilot 改已有设计用 | `assertCanAccess(id, VIEW)` → `aiGenerationService.serializeFunctionUnitContext(id)`（AI Studio 喂给模型的同一份 `FunctionUnitContextDTO`，字段名与 `generatedData` 同构） |
| `GET /ai-generation/studio-chat/format-spec` | 格式说明一次拿全 | `{ template: aiPromptTemplateComponent.get("GENERATION").content, schemaMetadata: aiGenerationService.getSchemaMetadata() }`；模板取各环境当前生效内容（UAT 已有自定义版本，P0 实测可用） |

### 1.2 后端（admin-center）——独立的固定回调列表

| 文件 | 改什么 |
|---|---|
| `PlatformSsoProperties.SsoClient` | 新增 `fixedRedirectUriPrefixes`（与环境无关的固定回调前缀） |
| `PlatformSsoProperties.SsoClient` | 新增 `loopbackRedirectPaths`（0.1.2 起：本机 loopback 回调的固定路径） |
| `PlatformSsoService.validateRedirectUri` | 允许 = `redirectUriPrefixes` ∪ `fixedRedirectUriPrefixes` 的 `startsWith` 前缀匹配，或 loopback 整串精确匹配 `http://127.0.0.1:<1–65535>` + `loopbackRedirectPaths` 之一；仍按 client 隔离 |
| `admin-center/src/main/resources/application.yml` | `platform.sso.clients.developer-workstation.loopback-redirect-paths: [/sso-callback]`（`fixed-redirect-uri-prefixes` 字段保留，0.1.2 起不再配 `vscode://`） |

**为什么不直接追加到 `redirect-uri-prefixes`**（P1 首版就是这么做的，UAT 实测失败）：UAT/生产的 configmap 用
`SPRING_APPLICATION_JSON` 整体定义了各 client 的 `redirect-uri-prefixes[0..3]`，Spring 绑定列表是**整表替换**不合并，
yml 里追加的条目在这些环境被静默丢弃，登录页报 `redirectUri not allowed`。独立字段不在覆盖里，yml 默认值得以保留，
各环境**不需要改 configmap**。单测 `PlatformSsoServiceRedirectTest` 用两层属性源复现了这一覆盖。`loopback-redirect-paths`
同理独立成列。

**loopback 为什么不用前缀 `http://127.0.0.1:`**：`startsWith` 会放过 `http://127.0.0.1:80@evil.com/…`——登录页用
`new URL(redirectUri)` 拼上 code 后 `window.location.href` 跳转，浏览器把 `127.0.0.1:80` 当 userinfo、主机是 `evil.com`，
code 直接落到对方手里（DW 的 exchange 不校验 redirect_uri，拿到 code 就能兑换）。所以对原始字符串整串正则匹配
（不经 URI 解析，避免与浏览器解析差异）：只认 `http`、主机字面量 `127.0.0.1`、显式十进制端口 1–65535、路径恰为配置值，
无 userinfo / query / fragment；`localhost`、`[::1]`、`https`、大写 scheme、路径穿越一律拒绝（单测逐条覆盖）。

### 1.3 扩展工程（新建 `tools/dw-copilot-vscode/`，替换 spike）

TypeScript，**零运行时依赖**（只用 VS Code API + Node 内置 `fetch`），`tsc` 编译、`@vscode/vsce` 打包。

| 文件 | 职责 |
|---|---|
| `package.json` | 扩展清单：id `workflow-station.dw-copilot`；`languageModelTools`（见 §1.4）、`@dw` participant、命令、视图、设置 `dwCopilot.environments` |
| `src/extension.ts` | 激活：注册工具 / participant / 树 / 命令 |
| `src/auth.ts` | SSO 登录（含 127.0.0.1 一次性回调监听，`AuthHost.listenLoopback` 可注入）、token 存取（SecretStorage，按环境分键）、401 时用 refresh 续期一次 |
| `src/dwClient.ts` | 唯一的 HTTP 出口：带 Bearer、超时、`ApiResponse` 解包；非 2xx 抛带 HTTP 状态 + 后端 `error.code/message` 的错误 |
| `src/tools.ts` | 工具实现（agent 模式工具与 `@dw` 共用） |
| （实现时合并）备份 / FU 树 / dry-run 预览 | 备份在 `tools.ts`，FU 树在 `extension.ts`；dry-run 摘要直接放进 apply 确认框，未单独做预览视图 |
| `src/participant.ts` | `@dw` 退路（spike 已验证的自带工具循环） |
| `test/*.test.ts` | `node --test` 单测纯逻辑（auth state 校验、refresh 轮换、错误映射、备份路径） |
| `README.md` | 安装、登录、使用 |

删除 `tools/dw-vscode-spike/`（被替代）。

### 1.4 工具清单（P1 = FU）

| 工具 | 后端 | 写？ | 确认框 |
|---|---|---|---|
| `dw_list_workspaces` | `GET /function-units/my-dev-groups` | 否 | 否 |
| `dw_list_function_units` | `GET /function-units?name=` | 否 | 否 |
| `dw_create_function_unit` | `POST /function-units`（必带 `virtualGroupIds`） | 是 | 是 |
| `dw_get_format_spec` | 新 `GET …/format-spec` | 否 | 否 |
| `dw_get_design` | 新 `GET …/design-context` | 否 | 否 |
| `dw_dry_run` | 新 `POST …/dry-run` | 否 | 否 |
| `dw_apply_design` | 现有 `POST …/apply` | 是 | 是，确认框里直接贴 dry-run 摘要 |
| `dw_undo` | 现有 `POST …/undo` | 是 | 是 |
| `dw_export_function_unit` | 现有 `GET /export-import/function-units/{id}/export` | 否（本地落文件） | 否 |

`dw_apply_design` 内部顺序固定：**dry-run → 有 ERROR 直接返回错误给模型（不弹框）→ 导出备份 → 弹确认框（带摘要 + 备份路径）→ apply**。

## 2. 关键技术决策

| 决策 | 选择 | 为什么不用另一种 |
|---|---|---|
| 登录回调（0.1.2 起） | 本机 loopback（RFC 8252）：`signIn` 在 `127.0.0.1` 上开临时 Node `http` 服务，端口由系统分配，`redirect_uri=http://127.0.0.1:<port>/sso-callback`；只认该路径 + 本次 `state`、只收一次，回一个「可以关闭此页」的页面（失败时显示原因）；成功 / 失败 / 5 分钟超时 / 浏览器没打开都关闭服务。监听失败、超时都报明「可能是本机安全软件拦了 127.0.0.1」 | 0.1.1 用 `vscode://workflow-station.dw-copilot/sso-callback` + UriHandler：UAT 公司机同时装了 1.141（免安装版）和卸不掉的 1.120（全机版），`vscode://` 协议登记在 1.120 名下，code 被交给 1.120 窗口、发起登录的窗口超时。随机端口不撞端口；**不保留 `vscode://` 做后备**：一次登录只能带一个 `redirect_uri`，后备只能在「监听失败」时启用，而 Trellix 这类拦截更可能表现为浏览器连不上（扩展侧无法察觉），后备又正是会被交错窗口的那条路；白名单里也就不再保留 `vscode://` 一项 |
| 登录流程 | 浏览器打开 `<env>/login/?client_id=developer-workstation&redirect_uri=…&state=<随机>` → 回调校验 `state` → `POST /api/v1/auth/sso/exchange {code}` | 复用 DW 网页同一条 SSO（含 DSP 免密），零新后端；`state` 不匹配直接拒绝 |
| 准入（只有能进 DW 的人能用） | 照搬 DW 网页路由的进入规则：`GET /api/v1/auth/me` 取角色 → 有 `SYS_ADMIN/TECH_LEAD/TEAM_LEAD/DEVELOPER` 完整进入；纯 `AUDITOR` 拒绝；其余看 `GET /function-units/workspace-access` 的 `canView`，true 则只读进入（状态栏标 read-only），否则拒绝。登录、每次续期、启动/切环境时各查一次；被拒即清掉会话 | 能登录 ≠ 能进 DW：exchange 只挡锁定/停用账号。`workspace-access` 对 AUDITOR 也返回 true，所以先查角色。写操作仍由后端各接口权限把关，准入只保证与 DW 网页入口一致 |
| 续期 | 401 时调 `POST /api/v1/auth/refresh {refreshToken}`（body 传，已核实支持），保存新的一对 token；同一环境的并发续期合并为一次；refresh 返回 400/401 → 清 token、提示重新登录，其他错误保留会话 | refresh token 会轮换（旧的进黑名单）：并发 401 各自续期会让后一次用掉已作废的 token、把新会话删掉；只重试一次，不循环 |
| 多环境 | 设置 `dwCopilot.environments`：`[{name, url}]`（站点 origin），默认空，**application scope**（工作区设置不能覆盖，防恶意仓库把登录引到钓鱼站）；状态栏显示当前环境，切换命令 | 不内置任何公司域名；token 按 origin 分开存 |
| 格式说明 | 服务端一次返回模板 + schemaMetadata | 扩展不内置格式，DW 升级不需扩展发版 |
| 预检 | 服务端 dry-run 复用 `AiStudioProposalPreviewer` | 与 AI Studio 提案卡同源，零第二套校验规则 |
| `preview.checked=false` | 扩展当作「预检未完成」，**不放行** apply，把原因回给模型 | Previewer 自身在异常时置 `checked=false`（存量行为）；把「没检查」当「检查通过」= 兜底，禁止 |
| UI 语言 | 扩展界面仅英文 | 使用者是 IT 开发者；三语 i18n 规则针对三个 Vue 前端 |

## 3. 影响链

- **DW 后端**：新增 3 个只读端点 → 只调用已有 `AiStudioProposalPreviewer` / `serializeFunctionUnitContext` / `AiPromptTemplateComponent` → 无 Entity / SQL / init-scripts 变更；`getSchemaMetadata()` 提升可见性，唯一既有调用点同步改。
- **admin-center**：SSO client 多 `fixedRedirectUriPrefixes`、`loopbackRedirectPaths` 两个字段，只有 `developer-workstation` 配了 `loopbackRedirectPaths`；其他 client 为空列表，校验行为不变。0.1.1 扩展（`vscode://` 回调）在新 admin-center 上不再能登录，须升到 0.1.2。
- **Kong**：`/api/v1/ai-generation` 已路由到 DW（`developer-workstation-sse-route`），新端点自动覆盖，**不改**。
- **CORS / CSRF**：扩展走 Node `fetch` 无 Origin，Bearer 头鉴权（P0 已验证 POST 可用），不涉及。
- **前端三端**：不改。
- **部署**：UAT / dev 需重新发布 **developer-workstation** 与 **admin-center** 两个镜像（按 memory：镜像在 hermes1.0 工作区 build/push，IKP_deployment 做部署）。

## 4. 通用性

- 后端端点全按 `functionUnitId` 参数化，不读任何 FU 名 / 表名 / 字段名。
- 新建 FU（空设计）和已有 FU（P0 的 388、dev 夹具）都走同一条：`design-context` 对空 FU 返回空切片，dry-run 对空 FU 的 `replacesExisting=0`。
- 验收用**双样本**：一个新建空 FU（建表单）+ 一个已有表的 FU（在其上加表单，验证 `design-context` 被 Copilot 用来对齐表名字段名）。

## 5. 验证方式

| 层 | 命令 / 动作 | 通过标准 |
|---|---|---|
| DW 单测 | `mvn -pl backend/developer-workstation -am clean package`（JDK17）含 `AiStudioChatComponentImplTest` 新用例 | dry-run：错 dataType → `issues` 含 ERROR + fieldPath，且 **repository 零 save 调用**；越权 FU → 抛工作区异常；context / format-spec 返回非空 |
| 扩展单测 | `npm test`（`node --test`） | state / 路径不匹配被拒且不兑换、回调只收一次、成功失败超时都关闭监听、真实 127.0.0.1 监听走通；refresh 轮换后旧 token 不再使用；非 2xx 错误带 code |
| 扩展冒烟 | `@vscode/test-electron` 隔离 profile（P0 同款） | 9 个工具注册可见 |
| 本机 dev E2E | 重建 DW + admin-center 容器；扩展走 SSO 登录（localhost） | 登录成功；双样本 FU 各跑一轮：dry-run 拦住反例 → 备份 zip 落盘 → apply → 设计器截图 |
| 公司机 UAT E2E | 镜像发布后用 bbox 在公司机跑同样两轮 | 同上；另验公司 Chrome 能打开 `http://127.0.0.1:<port>/sso-callback`（Trellix 未拦），且回到发起登录的那个 VS Code 窗口 |
| 恢复演练 | 用备份 zip 走 DW Import 同名导入 | FU 回到 apply 前，Version Management 多一个版本 |

验证跑不了的项（例如镜像还没发 UAT）如实标 CONDITIONAL。

## 6. 不做什么

- 不做 Automation / Superset 工具（P2 / P3）。
- 不做服务端快照、不改 `dw_versions` / 回滚链。
- 不改 `applyProposal` 写路径、不改 undo 存储（内存）。
- 不做 MCP server；不做 JetBrains。
- 不发布到任何公共扩展市场；P1 交付 VSIX 文件（分发渠道是 P4）。
- 不改三个 Vue 前端、不加 i18n key。
- 不修 refresh cookie 7 天 vs token 3 天的不一致（记为范围外问题，扩展按 token 实际过期处理）。

## 7. 范围外问题（发现但不改）

1. `DeveloperSsoExchangeService` refresh cookie `setMaxAge(7 天)` 与 `JWT_REFRESH_EXPIRATION`（UAT 3 天）不一致。
2. `AiStudioUndoService` 撤销记录只在内存，DW 重启即失。
3. `AiStudioProposalPreviewer.preview` 异常时只 `log.warn` 并置 `checked=false`（扩展侧已按「未通过」处理，不改服务端）。
