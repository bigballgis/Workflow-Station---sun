# TRIM_LOG（web 部分）— `automation/packages/web/` 裁剪 + L1 嵌入移植

> 与 `TRIM_LOG.md` 同源台账；单独成文以避免与并行的 server 裁剪任务互相覆盖。
> 日期 2026-08-13。需求：FR-B08 ~ FR-B11、FR-D2（web features/routes）、NFR-1（前端产物）。

## 一、功能域删除（FR-D2）

### features（12 个，全部删除）

`agents` `alerts` `billing` `chat` `forms` `members` `piece-sets` `platform-admin`
`project-releases` `secret-managers` `tables` `templates`

保留 9 个：`authentication` `automations` `connections` `flow-runs` `flows` `folders`
`pieces` `projects` `variables`。

### routes（15 个目标）

删除目录/文件：`chat` `chat-with-ai` `mcp-authorize` `tables` `forms` `templates`
`project-release` `impact` `crash-test` `sign-up` `forget-password` `change-password`
`create-platform.tsx` `embed`（上游 iframe embed 路由，X-6 已否决 iframe；DW 走
`src/embed/mount-builder.tsx`）、`platform/`（整个 platform-admin 控制台页面树）。

四个路由数组文件：

- `auth-routes.tsx` 重写：仅留 `/sign-in` + `/verify-email`（身份走 HERMES SSO，但 builder
  内部仍需 token 机制，sign-in 保留）。
- `public-routes.tsx` 重写：仅留 `/authenticate` `/redirect` `/404`。
- `platform-routes.tsx` 重写为 `export const platformRoutes: never[] = []`（保留导出名，
  `guards/index.tsx` 的引用不变）。
- `project-routes.tsx`：删 impact / project-release（含 view-release）/ tables（含
  `HideTablesGuard` 与 `/tables` 重定向）/ releases 路由；`automationsPagePermissions`
  去掉 `READ_TABLE`。
- `guards/index.tsx`：删 chat-with-ai 路由组与 dev-only crash-test 路由。

### 连带清理（保留域里指向被删域的引用）

| 文件 | 处理 |
|---|---|
| `app/app.tsx` | 去 `RefreshAnalyticsProvider`（platform-admin） |
| `app/query-client.ts` | QUOTA_EXCEEDED 不再弹 billing 计划弹窗，回落统一错误 toast |
| `app/components/builder-layout/index.tsx`、`project-layout/index.tsx` | 去 `ManagePlanDialog` / `CreditsUsageAlert` 与随之无用的 edition flag |
| `app/components/flow-actions-menu.tsx` | 去 Push to Git（project-releases）、Change Owner（members）、Share（templates）三个菜单项及 gitSync 状态 |
| `app/components/project-settings/index.tsx` | 六标签页 → 仅 General；删 `alerts/ environment/ members/ pieces/ mcp-server/` 子目录 |
| `app/components/project-layout/project-dashboard-page-header.tsx` | 重写：去成员计数按钮 + 邀请对话框，设置弹窗固定 general |
| `app/components/project-layout/project-dashboard-layout-header.tsx` | 去 Releases 标签 |
| `app/components/sidebar/dashboard/index.tsx` | 去 Chat / Explore(templates) / Impact 三个入口、`SidebarUsageLimits`、Platform Admin 入口 |
| `app/components/sidebar/platform/` | 删除（platform 控制台已不存在） |
| `app/components/global-search/use-global-search-results.ts` + `static-pages.ts` | 去 tables 查询与结果分组；静态页仅留 Automations |
| `app/components/settings-hub/`、`billing-page-shell.tsx`、`locked-feature-guard.tsx`、`platform-layout.tsx`、`guards/template-details-wrapper.tsx` | 删除（仅被已删域引用） |
| `app/connections/secret-input.tsx` | 重写为纯受控 Input（secret-managers 域删除），导出 API 不变 |
| `app/builder/step-settings/agent-settings/`、`test-step/agent-test-step/` | 删除；`step-settings/index.tsx`、`test-sample-data-viewer.tsx`、`run-details/flow-step-input-output.tsx`、`state/run-state.ts` 摘除 agent 分支（timeline 标签、agent 默认输出） |
| `app/builder/state/chat-state.ts` | 删除；`builder-hooks.ts` 去 ChatState 切片，`builder/index.tsx` 去 `<ChatDrawer/>`，`test-flow-widget.tsx`/`test-runner-context.tsx` 去 chat-trigger 抽屉（chat-trigger 仍走 simulation 分支），`types/index.ts` 去 `ChatDrawerSource` |
| `features/automations/*` | 列表页去 tables：`use-automations-data.ts`（folder contents 只查 flows、rootTables 恒空）、`use-automations-mutations.ts`（去建表/改表/删表/导表）、`automations-filters.tsx`、`create-new-menu.tsx`、`automations-table(.tsx/-row.tsx)`、`automations-empty-state.tsx` 重写为 flows-only |
| `features/flows/` | 删 `change-owner-dialog.tsx` `share-template-dialog.tsx`，`flow-hooks.tsx` 去 `useCreateTemplateFromFlow` |
| `features/authentication/` | 删 `saml-login-form.tsx`（依赖 platform-admin 的 samlSsoApi）；`third-party-logins.tsx` 去两个 SAML 按钮，`auth-form-template.tsx` 去 SAML 分支 |
| `features/projects/` | `create-project-button.tsx` 去 billing 的 team-project 限额守卫；删 `platform-admin-project-alert-subscription-bulk-actions.tsx` |
| `lib/route-utils.ts` | 去 tables/releases/chat 路由键与 `NEW_TABLE_QUERY_PARAM`；`determineDefaultRoute` 去 `chatEnabled` 分支（两个调用方同步） |
| `lib/project-members-min.ts` | **新增**：members 功能域删了，但 owner 归属（flow 列表 Owner 过滤、连接 owner 过滤）仍需只读 `/v1/project-members`（该契约按裁定保留），故以最小客户端替代原 feature API |
| `test/` | 删除随域消失的测试目录：`features/{billing,chat,members,piece-sets,tables}`、`app/routes/{chat-with-ai,impact}`、`app/components/project-settings/mcp-server` |
| `tsconfig.app.json` / `tsconfig.spec.json` | 删除悬空的 `@activepieces/piece-ai` 路径映射（piece-ai 不在白名单） |

## 二、shiki 裁剪（NFR-1 前端产物）

- 新增 `src/lib/shiki-highlighter.ts`：`createHighlighterCore` + `createJavaScriptRegexEngine`
  （JS 正则引擎，避免额外的 oniguruma `.wasm` 资源——lib-mode 产物随 DW 自服务，少一个资产少一处路径问题），
  语言白名单 8 种：`json javascript typescript html css sql markdown shellscript`，
  主题仍为 `vitesse-light` / `vitesse-dark`。语言/主题以动态 `import()` 传入，落到独立 chunk。
- `components/prompt-kit/code-block.tsx` 唯一引入点改造：删 `from 'shiki'` 根导入
  （`bundledLanguages` 静态引用 376 个语法 ≈19MB），改用上述 highlighter；
  `isBundledLanguage` → `resolveLanguage()`（白名单 + 常见别名 js/ts/tsx/md/bash/…→ 白名单语言，
  未知语言回落 `plaintext`，属展示层回退：代码本身始终完整渲染）。
- 导入路径刻意写 `shiki/dist/core.mjs` / `shiki/dist/langs/*.mjs` / `shiki/dist/themes/*.mjs`：
  本包 `moduleResolution: "Node"`(node10) 读不到 package `exports`，只有物理存在的路径能通过
  TS 解析；这些路径同时命中 exports 的 `"./dist/*"`，Vite 解析结果一致。
- 自查：`grep -rn "from 'shiki'" src/` 零命中（仅注释提到）。

## 三、L1 嵌入移植（0.84 → 0.88）

### 新增文件

- `src/lib/host-config.ts`（原样移植 0.84，66 行）—— `window.__AP_HOST_CONFIG__` 懒读取。
- `src/embed/mount-builder.tsx`（移植 + 0.88 适配）——0.88 的 `app/app.tsx` 默认导出 `App`、
  `app/guards/index.tsx` 仍导出 `memoryRouter`（`ApRouter` 依 `embedState.isEmbedded` 选路由器），
  机制与 0.84 完全一致，无需额外改造；embedding 种子新增 0.88 的
  `hideGlobalSearch` / `hideActiveUsers` 两个开关。
- `test/lib/host-config.test.ts`（移植 0.84，jsdom 环境，仓库已有同类用例先例）。
- `vite.embed.config.mts`（移植 + alias 适配 0.88 拆包：`@activepieces/shared` →
  `packages/core/shared/src`，另加 `core-utils` / `core-formula` / `core-piece-types` /
  `core-execution` 四个新 alias；`outDir: ../../dist/packages/web-embed`、
  lib entry `src/embed/mount-builder.tsx` → `ap-builder.mjs`、`cssCodeSplit:false`、
  `define` 注入 `import.meta.env.AP_EMBED_BUILD='true'`）。
- `vite-plugins/ap-cdn-rewrite.js`（PATCH-009 移植），在 `vite.config.mts` 与
  `vite.embed.config.mts` 两处注册（`enforce: 'pre'`）。

### 7 个注入切点在 0.88 的落点

| # | 切点 | 0.88 文件:行 |
|---|---|---|
| 1 | storage | `src/lib/ap-browser-storage.ts:1,13`（`apHost.getConfig().storage ?? localStorage`） |
| 2 | onUnauthorized（logOut） | `src/lib/authentication-session.ts:14,136` |
| 3 | apiUrl | `src/lib/api.ts:12,74`（`request()` 内 `apHost.getApiUrl()` 懒解析；:19-21 保留 API_URL 兼容常量与说明） |
| 4 | onUnauthorized（401/SESSION_EXPIRED） | `src/lib/api.ts:56`（`globalErrorHandler`） |
| 5 | socket | `src/components/providers/socket-provider.tsx:15,17`（懒建单例 `getSocket()` + `getSocketBaseUrl()/getSocketPath()`） |
| 6 | embedding 种子 | `src/components/providers/embed-provider.tsx:69`（`useState` 惰性初值展开） |
| 7 | portalContainer | `components/ui/`：`dialog` `drawer` `sheet` `context-menu` `dropdown-menu`（各 2 处）`hover-card` `popover` `select` `tooltip`；另加 0.88 新增的 `components/custom/multi-select.tsx`、`components/prompt-kit/file-upload.tsx`、`app/builder/piece-properties/text-input-with-mentions/components/function-hover-popover.tsx` 与 `function-search-popover.tsx`（后两者是 0.88 新增的 `createPortal(..., document.body)`，Shadow DOM 下必须改） |

### 其它移植

- `src/i18n.ts`：`AP_EMBED_BUILD` 时 locales 相对 bundle URL 解析（`backend.loadPath`）。
- `index.html`（PATCH-010）：favicon/logo 指向同源 `/hermes-mark.svg`、`/hermes-mark-192.png`、
  `/hermes-mark-180.png`；从 0.84 拷入 `public/hermes-mark.svg`、`hermes-mark-180.png`、
  `hermes-mark-192.png`、`hermes-full-logo.svg`。
- PATCH-001：`app/builder/pieces-selector/index.tsx` 摘除 Approvals 页签**与** 0.88 的
  AI & Agents 页签（后者依赖已删的 `/v1/ai-providers` 与不在白名单的 `piece-ai`），
  同时删除 `approvals-tab-content.tsx` 与 `ai-tab-content/` —— 二者都在“选中页签”早退之前
  发起 piece 查询，只隐藏页签仍会 404 刷屏。

## 四、自查

- 被删 features 的 import：`grep -rn "features/{agents,alerts,billing,chat,forms,members,piece-sets,platform-admin,project-releases,secret-managers,tables,templates}"` → 零命中。
- 全量本地 import 解析（含动态 `import()`，src + test）→ 0 处指向不存在的文件。
- esbuild 逐文件语法解析（src + test 全部 .ts/.tsx）→ 0 失败。
- `from 'shiki'` 根导入 → 零命中。
- `window.__AP_HOST_CONFIG__` 机制齐备（host-config + mount-builder + 测试）。

## 五、遗留风险（需构建/运行期验证）

1. **未执行任何构建**（按分工由主会话统一编译）。TS 类型层面仅做了 import 可解析性与语法校验，
   `tsc` 可能仍报被删域残留的类型引用（尤其并行任务正在删 `packages/core/shared/src/lib/ee` 契约时，
   web 侧 `ProjectMemberWithUser` / `ListProjectMembersRequestQuery` 必须随 project-members 契约保留，
   否则 `src/lib/project-members-min.ts` 会断）。
2. `shiki/dist/*.mjs` 深路径导入依赖 shiki 4.0.2 的产物布局；升级 shiki 时需复核
   （若届时 tsconfig 改为 `moduleResolution: "Bundler"`，应换回 `shiki/core`、`shiki/langs/*`）。
   JS 正则引擎对个别语法（oniguruma 特有回溯）兼容性略低于 WASM 引擎，需在真实代码块上肉眼验证一次。
3. `hideTables` 等开关仍保留在 `EmbeddingState`（ee/embed-sdk 契约形状），但 tables 域已删，
   开关不再有作用面；未动 embed-sdk。
4. `/create-platform` 路由已删，但 `sign-in-form.tsx`、`redirect.tsx`、
   `allow-logged-in-user-only-guard.tsx`、`default-route.tsx` 里仍有指向它的 `navigate`，
   目前会落到 `/*` → `DefaultRoute`。HERMES SSO 场景不会走到该分支，但若要彻底干净需再清一轮。
5. `features/authentication` 里 `ChangePasswordForm` / `ResetPasswordForm` 已无路由引用
   （对应路由已删），保留在保留域内、不影响编译。
6. FR-B10（Shadow DOM 三处 CSS 改写：`:root`→`:host`、`@property` 初值回落、`100vh`→`100cqh`）
   **不在本次改动范围**，仍需针对 0.88 的 `styles.css` 重新验证。

---

## 六、HERMES-PATCH-023 —— 上游外链与死入口清理（2026-08-14）

**动机**：`automation/` 是 0.88.0 硬分叉（D12/D13 纯自维护）+ 气隙生产（X-3 运行时零外网）。
指向上游站点的链接**全部是死链**；更糟的是它们指向**上游文档与上游 release**，而我们裁掉了 12 个
功能域、pieces 只剩 13 个、信封契约也改了 —— **指错的文档比没有文档更坏**。
处置原则：删组件优于留空壳；不留死按钮（不允许点了没反应或指向 `#`）；依赖外部服务且无替代者
让它明确不可用（隐藏入口），不静默降级。

### 6.1 整组件删除（3 个文件）

| 文件 | 承载的外链 | 理由 |
|---|---|---|
| `src/app/components/help-and-feedback.tsx` | `activepieces.com/docs`、`github.com/activepieces/activepieces/releases`、`supportUrl`(community.activepieces.com) | 组件**只有**这三个外链，没有任何本地能力。渲染点 `sidebar/sidebar-user.tsx`（import + `<HelpAndFeedback/>`）一并移除 |
| `src/app/components/request-trial.tsx` | `www.activepieces.com/sales?…`（带 firstName/lastName/email/flags 查询串） | 上游付费引导。CE + 硬分叉下无意义；且它把**用户姓名、邮箱与整份 flags 快照**编进 URL 发往上游销售页，本身就是不该保留的外发。删除前已确认**全仓零引用**（连 `FeatureKey` 类型也无外部消费者） |
| `src/features/authentication/components/integration-logos-overlay.tsx` | 12 个 `www.activepieces.com/logos/*.{svg,png}`（roblox / redbull / rakuten / posthog / plivo / nedap / moneygram / fundingsocieties / experience.com / docusign / contentful / alan） | 上游客户 logo 墙。删除前已确认**全仓零引用**（本就是 0.88 引入后未接线的孤儿组件）。注意它原本带 `onError → display:none` 的静默隐藏，正是"12 个 404 但界面看不出问题"的典型静默降级 |

### 6.2 入口/链接摘除（保留组件本体）

| 文件 | 处置 |
|---|---|
| `app/builder/builder-header/builder-header.tsx` | 删 `SHOW_COMMUNITY` 门控的 **Support 按钮**（`openNewWindow(supportUrl)` → community.activepieces.com）。连带清 `supportUrl`/`ApFlagId`/`CircleHelp`/`flagsHooks`/`useNewWindow` 五个随之空转的 import |
| `app/builder/pieces-selector/no-results-found.tsx` | 删 `SHOW_COMMUNITY` 门控的 **Request Piece 按钮**（`feedbackUrl` → feedback.activepieces.com）。向上游许愿 piece 对"13 个白名单件"的分叉毫无意义；连带清 flag、`useEmbedding` 与两个 import |
| `app/components/account-settings/language-toggle.tsx` | 删 "Help translate Activepieces →" 链接（`www.activepieces.com/docs/about/i18n`）。本仓 locale 文件自维护；连带清 `flagsHooks`/`ApFlagId`/`Link` |
| `app/builder/run-details/flow-step-input-output.tsx` | 删 truncated-logs 的 "Learn more" `<a>`；**截断提示文案保留**（它自身解释了截断语义，不是链接的壳） |
| `features/pieces/components/install-piece-dialog.tsx` | markdown 里 `[custom piece](…/build-pieces/building-pieces/create-action)` 去掉链接、**保留文字**；上游 piece 编写文档与本仓 piece 契约已分叉 |
| `app/builder/…/components/function-search-popover.tsx` + `…/tiptap-editor.tsx` | 删常量 `DEFAULT_FORMULAS_DOCS_URL`（`…/docs/flows/using-formulas`）。`docsUrl` 改为**仅宿主提供**（`embedState.formulasDocsUrl`）；宿主没给就不渲染 "See All" 链接，而不是回落到上游文档。顺带修了 PATCH-007 遗留的 `apHost` import 顺序 lint error |

> **`SHOW_COMMUNITY` 不是保险丝**：`flag.service.ts` 里它取 `edition !== ENTERPRISE`，而我们是 CE，
> 所以上面三处"被 flag 门控"的入口在生产里**全都是渲染出来的**，不是死代码。

### 6.3 外部服务依赖（fail-loud，不静默降级）

| 文件 | 处置 |
|---|---|
| `features/connections/api/oauth-apps.ts` | 删 `listCloudOAuth2Apps()`（`GET secrets.activepieces.com/apps`，上游托管的 OAuth app 注册表） |
| `features/connections/hooks/oauth-apps-hooks.ts` | `usePiecesOAuth2AppsMap()` 去掉 cloud 分支，`cloudOAuth2App` 恒为 `null`，只由 `/v1/oauth-apps` 填充；连带清 `platformHooks` |
| `app/connections/oauth2-connection-settings.tsx` | `redirectUrl` 去掉 `CLOUD_OAUTH2 → secrets.activepieces.com/redirect` 分支，仅留 `THIRD_PARTY_AUTH_PROVIDER_REDIRECT_URL` |

connections 走自建，**不得**把 OAuth 交换代理给外部服务。删掉列表拉取后 `CLOUD_OAUTH2` 通路
**按构造为空**（选项根本不出现），而不是"点了连接才 502"——这就是隐藏入口而非静默兜底：
`AppConnectionType.CLOUD_OAUTH2` 枚举本身在 `packages/core` 契约里，本次不动。

### 6.4 `cloud.activepieces.com`（上游 hosted-dev 兜底）

`lib/api.ts` 的 `isRunningCloudInDevMode`（`import.meta.env.MODE === 'cloud'`）与
`lib/host-config.ts` 的 `defaultOrigin()` 都会在该 mode 下把 base 指向 `cloud.activepieces.com`。
本仓**没有任何配置设置该 mode**，且气隙部署绝不能解析站外 origin ——
两处一律回落 `window.location.origin`，`isRunningCloudInDevMode` 导出删除，
唯一消费者 `components/providers/telemetry-provider.tsx` 的
`telemetryFlagEnabled && !isRunningCloudInDevMode` 简化为只看 `TELEMETRY_ENABLED` flag
（对任何非 cloud 构建行为完全等价）。

### 6.5 明确保留（不是遗漏）

- **`cdn.activepieces.com`（7 处：`step-utils.tsx` 4、`auth-animation.tsx` 2、`auth-form-template.tsx` 1）**
  —— 构建期由 `vite-plugins/ap-cdn-rewrite.js`（PATCH-009）改写成同源 `/ap-cdn`，资产已镜像进
  `packages/web/public/ap-cdn/`。它们是**源码字面量**，运行时不出网；改掉反而破坏改写机制。
  本次已在产物上复验：embed bundle 里 `cdn.activepieces.com` **零命中**，
  `/ap-cdn/pieces/new-core/*.svg` 四个改写结果均在。
- **源码注释里的第三方 issue/PR 链接**（`react-hook-form`、`ueberdosis/tiptap`、`shadcn-ui/ui`、
  `radix-ui/primitives`）—— 解释"为什么这么写"的出处，删了等于丢失决策依据，保留。
- 本次新增的 `// HERMES-PATCH-023:` 注释里提到被删的域名时**刻意不带 `https://` 前缀**，
  以免自查 grep 把说明文字当成残留外链。

### 6.6 i18n（`public/locales/{en,zh,zh-TW}/translation.json`）

删除 8 个**仅被上述入口使用**的 key：`Help & Feedback`、`Changelog`、`Community Support`、
`Contact Sales`、`Support`、`Request Piece`、`Help translate Activepieces →`、`Learn more`。
三份同删同一集合。`See All`（公式面板页脚，宿主提供 docsUrl 时仍渲染）**保留**。

| locale | before | after |
|---|---|---|
| en | 2487 | 2479 |
| zh | 2172 | 2164 |
| zh-TW | 2172 | 2164 |

`zh` 与 `zh-TW` key 集合完全一致；`en` 多出的 329 个是**上游既有漂移**（改动前后差值恒定，非本次引入）。

### 6.7 验证

- `npx turbo run build --filter=web` → 7/7 成功；`tsc --noEmit -p tsconfig.app.json` → **1 个错误，
  且是既有基线**（`components/prompt-kit/markdown.tsx:304`，ReactMarkdown `code` renderer 的
  `ref` 型变问题，本次未触碰该文件；`vite build` 不做类型检查所以历来未暴露）。本次改动引入 **0** 个 TS 错误。
- `npx vitest run` → 36 文件 / 351 用例全绿。`function-search-popover.test.tsx` 的第三个用例
  （断言回落到上游 formulas 文档）随常量删除而移除——"宿主没给 URL 就不渲染链接"已由第一个用例覆盖。
- embed 产物 `npx vite build --config vite.embed.config.mts`：
  **9,092,033 B → 9,062,857 B（-29,176 B / -28.5 KB）**。`du -sh` 显示 8.8M→8.9M 是 `cp` 后
  块分配差异，按字节实测是**变小**。
- 外链自查 grep（`https?://…(activepieces|discord|calendly|cal\.com)…`，`packages/web/src`）：
  **清理前 30 处 / 6 个主机** → **清理后 7 处，全部是 `cdn.activepieces.com`**（6.5 保留项）。

### 6.8 本次未处理（新发现，建议另开补丁）

1. `app/builder/step-settings/code-settings/add-npm-dialog.tsx:56` 与
   `features/flows/hooks/flow-hooks.tsx:502` 在**浏览器运行时**直接 `GET registry.npmjs.org/<pkg>`
   （code step 添加 npm 包时的版本查询）。气隙环境下必然失败/挂起，属真实死入口，不在本批范围。
2. `components/providers/telemetry-provider.tsx` 仍 `posthog.init(<上游硬编码 project key>)`，
   `ui_host: 'https://us.posthog.com'`。虽由 `TELEMETRY_ENABLED` flag 门控且 `api_host` 走同源
   `/ingest`，但硬编码的上游 key + 站外 ui_host 应当一并裁掉。
3. `packages/core/shared/src/lib/core/{support-url,feedback-url}.ts` 里的 `supportUrl`
   (`community.activepieces.com`) / `feedbackUrl` (`feedback.activepieces.com`) 两个常量，
   本批清理后在 **web 侧零消费、server 侧亦零引用**（已 grep 确认），可随下一次 `packages/core`
   裁剪一并删除；本批按分工不动 `packages/core`。
