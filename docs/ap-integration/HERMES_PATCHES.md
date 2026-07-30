# HERMES-PATCH 变更日志

> **⚠️ 2026-07-30 定位变更（[D12](DECISIONS.md#d12)）**：本文件原是 [Q8](DECISIONS.md#q8)
> "frozen baseline + controlled fork" 的实体清单，承担**逐条重放到新上游 tag** 的施工图职责。
> Q8 已被 D12 取代——`activepieces/` 现视为 HERMES 自有源码，rebase 不会发生。
>
> **本文件因此降级为"为什么改"的变更日志**：
> - **不再承担 replay 语义**，不再要求裁剪写成可重放脚本、不再维护重放顺序；
> - **不再要求每个新改动都挂一个编号**——改 AP 代码就是改我们自己的代码，按常规提交即可；
> - 编号继续递增，只为**交叉引用**已有条目（代码里的 `HERMES-PATCH-0NN` 标记保持有效）。
>
> **"改了什么"已经免费**：基线是干净的单提交
> `de4f6469 vendor(ap): pristine Activepieces 0.84.0 source baseline`，
> `git diff de4f6469..HEAD -- activepieces/` 随时给出完整答案。
> 本文件留下的是 git diff **给不出**的那一半：动机、否决过的做法、踩过的坑。

## 怎么用

- **读**：想知道某处 AP 代码为什么长这样，先查下表；查不到就 `git log` / `git blame`。
- **写**：值得解释的改动（有非显然动机、有踩坑、有否决过的替代做法）在下表加一行，
  编号取下一个；纯机械改动不必登记。
- **许可审计**：MIT 义务是保留 `activepieces/LICENSE` 与版权声明（文件级，与本表无关）；
  需要回答"我们对上游做了什么"时用上面那条 `git diff`，本表提供人类可读的动机层。

> **⚠️ 002 已作废并删除（2026-07-28）**，它曾是唯一的「构建期改写产物」类补丁。
> 001 原本也是这一类（`deploy/pieces/patch-web-approvals.js` 正则改压缩后的 web bundle），
> AP 源码 vendor 进仓库后已**改写为源码补丁**，脚本删除。

## 清单

| # | 类型 | 位置 | 内容 | 落地 | 裁决 |
|---|---|---|---|---|---|
| 001 | 源码 | `web/src/app/builder/pieces-selector/index.tsx` | 摘掉 Approvals 标签页与 `<ApprovalsTabContent>` 渲染。该标签页硬编码 6 个 SaaS piece，全部加载成功才渲染；白名单下 6 个全 404 → 永久骨架屏。**不渲染**而非仅隐藏标签：组件在 tab 判断之前就发这 6 个查询 | 2026-07-02 `821cf33c`；2026-07-27 由构建期脚本改为源码补丁 | [Q9](DECISIONS.md#q9) |
| ~~002~~ | ~~构建期~~ **已作废（2026-07-28，脚本已删）** | ~~`activepieces/hermes/patch-piece-ai-run-agent.js`~~ —— AI Generate 改用 HTTP piece 直连模型端点，`piece-ai` 的 run_agent 链路不复存在，补丁没有可打的对象；`community/ai` 已随 013 从 vendor 树删除。下面是作废前的原始记录： | `piece-ai` 的 `run_agent`：补 `maxOutputTokens`（DeepSeek 把 reasoning token 计入预算，默认额度会让文档还没输出就耗尽）+ reasoning-delta 不再拼进输出。**不接进 Dockerfile 是裁决，不是欠账**：`piece-ai` 在 `669f7207` 已按「气隙下 AI 件无用」移出白名单，镜像里没有可打的副本。dev 里该 piece 仍由运行时联网安装，需要时按文件头的 `docker cp` + `docker exec` 手工打 | 2026-07-16 `e5da4738`；2026-07-27 随预烘焙层迁入 vendor 树 | — |
| 003 | 源码 | `api/src/app/pieces/community-piece-module.ts` | 开放 piece 删除端点给 Admin Center | 2026-07-26 `277f15ae` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 004 | 源码 | `api/src/app/pieces/metadata/utils/index.ts` | 恢复最小 platform 级 piece 可见性 | 2026-07-26 `277f15ae` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 005 | 源码 | `worker/src/lib/cache/code/pkg-runner.ts` | 气隙运行时 piece 安装（pnpm offline store） | 2026-07-26 `3811061d` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 006 | 源码 | `worker/src/lib/egress/lifecycle.ts` | `AP_SSRF_ALLOW_LIST` 接受主机名，不再只认 IP / CIDR | 2026-07-27 `d6ec1e33` | [D6](DECISIONS.md#d6) |
| 007 | 源码 | `engine/src/lib/operations/sync-webhook-release.ts` | run 进终态即释放 sync webhook 监听器（步骤内失败） | 2026-07-27 `6a50f83c` | [D10](DECISIONS.md#d10) |
| 008 | 源码 | `worker/src/lib/execute/jobs/execute-flow.ts` | 同上，覆盖引擎启动之前就终结的 run | 2026-07-27 `6a50f83c` | [D10](DECISIONS.md#d10) |
| 009 | 源码（构建插件） | `web/vite-plugins/ap-cdn-rewrite.js` + `web/vite.config.mts` + `web/vite.embed.config.mts` | 构建期把硬编码的 `https://cdn.activepieces.com` 改写成同源 `/ap-cdn`。上游在 16 个文件约 47 处写死该域名，气隙下全部加载失败；改写而非逐处改源码，是为了保持 vendor diff 干净（[Q8](DECISIONS.md#q8)）并自动覆盖将来合并进来的新引用。资产由 `deploy/pieces/mirror-ap-cdn.mjs` 联网抓进 `web/public/ap-cdn/` | 2026-07-26 `7df25489` | X-2 / X-3 |
| 010 | 源码 | `api/src/app/flags/theme.ts` + `web/index.html`（+ `web/public/hermes-*.svg`） | 白标：`websiteName` 改 Automation Studio（与 admin-center 入口菜单一致），logo / favicon 从 `cdn.activepieces.com/brand/*` 改同源相对路径 | 2026-07-26 `7df25489` | X-2 / X-3 |
| 011 | vendor 树裁剪（**已被 013 吸收**，标记落在 `trim-vendor-pieces.mjs` 头部） | `packages/pieces/community/{avian,claude,deepseek,google-gemini,google-vertexai,hugging-face,localai,openai}`（删除）+ `tsconfig.base.json` + `pnpm-lock.yaml` | 删掉 8 个厂商 AI 件。`pnpm-workspace.yaml` 含 `packages/pieces/community/*`，根 `pnpm install` 会把这 8 个件的 `@anthropic-ai/sdk` / `openai` / `@google/genai` / `@google/generative-ai` / `@huggingface/*` 全部拉下来；而它们既不在 `pieces.json` 白名单、也不被 api import，Dockerfile 构建期已 `rm -rf`，对成品零贡献 —— 唯一效果是公司内网装不下来。**保留 `community/ai`**：只依赖 `@ai-sdk/*`，与 `server/{api,worker}` 的硬依赖重合，删了不省安装量，且是 AI Generate 链路与 FR-K02 的源码化对象 —— **该理由已于 2026-07-28 失效**（AI Generate 改用 HTTP piece），`ai` 已随 013 追删 | 2026-07-28 | — |
| 012 | 源码 | `server/api/src/app/app.ts` + 删 `server/api/src/app/trigger/app-event-routing/app-event-routing.module.ts` + `server/api/package.json` | 摘掉 `/v1/app-events/:pieceUrl`。该端点是 `securityAccess.public()`（**不鉴权**），而 Kong 的 `/api/ap` 路由按约定不验 JWT、透明转发，等于给内网任何一台机器开了个匿名入口去跑 slack / intercom 的 payload 解析代码。它服务的 4 个 SaaS 件不在 `pieces.json` 白名单里（设计器选不到 → `app_event_routing` 恒为空表），气隙下 SaaS 也打不进来，即"零功能 + 常驻攻击面"。**保留 `app-event-routing.service.ts` 与 `app_event_routing` 表**：`flow-trigger-side-effect.ts` 的 `TriggerStrategy.APP_WEBHOOK` 分支仍引用，是与具体 piece 无关的通用逻辑，删了编译不过 | 2026-07-28 | — |
| 013 | vendor 树裁剪（脚本化） | `activepieces/hermes/trim-vendor-pieces.mjs` + `tsconfig.base.json` + `pnpm-lock.yaml` + `Dockerfile` | 把 `packages/pieces/community/` 从 686 收敛到 **4**（`biz-calendar` / `hash-helper` / `json` / `postgres`，理由逐条写在脚本的 `KEEP` 里；`ai` 曾在清单里，2026-07-28 随 002 作废一并追删），同时摘掉 633 条失效的 `tsconfig.base.json` path 映射。**011 的筛法（按 SDK 依赖）被证明不够**：47 个 `*-ai` 件用 `httpClient` 直连模型 API，一个 SDK 依赖都不带；按名字筛同样漏。故改白名单式。裁剪写成脚本而非一次性删除，是为了 rebase 可重放（`--check` 模式可进 CI）。Dockerfile 构建期那段 `find … ! -name slack …` 随之简化为 `rm -rf packages/pieces/{core,custom,community}` | 2026-07-28 | — |
| 016 | 依赖治理 | 根 `activepieces/package.json` + `server/api/package.json` | **关掉 FOSS Guard 对 `fast-xml-parser@5.2.5` 的隔离**：把 `@aws-sdk/client-s3` 3.974.0→3.997.0、`@aws-sdk/s3-request-presigner` **3.894.0**→3.997.0（后者才是把 `@aws-sdk/core@3.894.0` 钉住的元凶），使 `core` 解析到 3.976.0/3.977.3、`xml-builder` 到 3.972.36/37 —— 上游自该版本起把 `fast-xml-parser` 换成了 `fast-xml-builder@1.3.0`。顺带删根上零 import 的 `@aws-sdk/client-bedrock`（被删的 bedrock provider 遗留）与 `@aws-sdk/client-secrets-manager`（AG-EE 已桩掉 secret manager，G4）。**这是"类 ② 依赖"的样板**：S3 是在跑的功能，只能升级不能删（见 [D12](DECISIONS.md#d12) 的两类划分）。**注意**：`fast-xml-parser` 并未从树上消失——根与 api 直接声明 `^5.5.6`→**5.7.0**，那不是被隔离的版本 | 2026-07-30 | [VT-21](VENDOR_TRIM_CHECKLIST.md#vt-21) |
| 015 | 功能面移除 | 删 `server/api/src/app/mcp/`（69 文件）+ `server/api/src/app/ai/`（14 文件）；改 `app.ts` / `server.ts` / `database/database-connection.ts` / `server/api/package.json` | 移除 AP 自带 **MCP server** 与 **AI provider 代理**。二者在气隙内都没有消费方（没有 MCP 客户端；AI Generate 已改 HTTP piece 直连模型端点，`piece-ai` 早已删除），且是 api 侧 `@ai-sdk/*` 的唯一 import 方。**顺带关掉一个比 012 更靠外的暴露面**：上游把 MCP OAuth 三组端点注册在**域名根**（`/.well-known/*`、`/mcp/*`、`/mcp/platform/*`），连 `/api` 前缀都没有。**迁移与表一律不动**（既成事实，只摘 TypeORM 托管，无 schema 变更）。api 侧摘依赖 14 个（10×`@ai-sdk/*` + `ai` + `@aws-sdk/client-bedrock` + `ai-gateway-provider` + `cloudflare`）；另删上游自带的孤儿测试 `test/unit/app/chat/chat-compaction.test.ts`（它 import 的 `src/app/chat/` **自 `de4f6469` 起就不存在**，在 0.84.0 里本就是断的）。**注意**：`app/agents/` 仍 import `@modelcontextprotocol/sdk`，那是另一个功能面。**第二步（同日）**：删 `worker/.../jobs/ee/`（EE chat agent 3 文件）、`engine/src/lib/tools/`（agent tools）、`server/utils/src/chat-ai-utils.ts`；连带摘 `shared` 的 `EXECUTE_CHAT_AGENT`（5 处，因 `job-registry` 是穷举 `Record`）与 `pieces-framework` 的 `agent` 上下文契约（已核实保留件与自研件都不用 `context.agent`）。依赖声明合计摘 42 处，**含仓库根 `package.json` 的 11 处——中途曾因核实 glob 不含根文件而误报"已清干净"**。`@ai-sdk/*` 厂商包在锁里归零，锁条目 5147→5030。**给 `packages/web` 补上 `"ai"` 显式声明**：`web/src/features/chat/` import 的 `getToolName`/`isToolUIPart` 是运行时函数，此前一直靠 `@openrouter/ai-sdk-provider` 的 peer 提升解析，摘掉会静默断掉 web | 2026-07-30 | [D12](DECISIONS.md#d12) / [VT-17](VENDOR_TRIM_CHECKLIST.md#vt-17) |
| 014 | 源码 | `activepieces/crowdin.yml` + `activepieces/package.json` | 关掉 Crowdin 双向同步。`push-i18n`（`crowdin upload sources`）会拿**本仓库的**源串去改写上游 Activepieces 的 Crowdin 项目 —— 013 之后 source 匹配只剩 **29** 个（裁剪前约 700），一次上传在上游项目里等同于批量删除源串；`pull-i18n` 则会把上游最新译文灌进 Q8 冻结基线，静默改掉 vendored i18n。两者都需 `CROWDIN_PERSONAL_TOKEN` 且目标是公网 SaaS，气隙部署下既不需要也不该发生。两个 npm script 改为 fail-loud 拒跑，配置文件保留（vendor diff 干净）并在头部写明理由。**自研件不在 source 匹配里**：`biz-calendar` / `hash-helper` 只有手写的 `src/i18n/zh.json`，没有 `translation.json` | 2026-07-29 | — |

源码类的路径相对 `activepieces/packages/`（003–008 / 012 在 `server/` 下，001 / 009 在 `web/` 下，010 两侧都有）；
011 / 013 是 vendor 树裁剪，不是源码补丁——**grep `HERMES-PATCH-0` 时它们的标记落在
`activepieces/hermes/README.md` 与 `trim-vendor-pieces.mjs` 里，不在被删掉的代码里**（删除留不下标记）。

009 归为源码类而非构建期：插件本身在 vendor 树里、随 `vite build` 自动生效，无需外部脚本挂载 —— 这是构建期改写类补丁应有的归宿（002 至死没做到，最后随链路作废一起删了）。

**009 的 `/ap-cdn` 是根绝对路径，跨 origin 不自带**（2026-07-29 补）：镜像资产只随 AP 独立应用发布
（`web/public/` 是它的 publicDir → 镜像内 `dist/packages/web/ap-cdn/`），而 DW 内嵌的 builder 是
**lib-mode 产物、不带 publicDir**，跑在 DW 自己的 origin 上；`logoUrl`（`generate-metadata-seed.js`
落库时同样改写成 `/ap-cdn/`）于是打到 DW 而 404 —— 表现为 Automation 页 piece 图标全变灰块
（Router / Code 是内联 SVG，不受影响，容易误判成"只坏了几个件"）。修法是把 `/ap-cdn` 收编回 AP：
Kong `activepieces-cdn-route`（`kong.yml.template` + 两份 k8s configmap），DW nginx 与 dev edge nginx
各加一个 `location ^~ /ap-cdn/` 转发到 Kong，k8s 侧由 `developer-workstation-frontend.yaml` 的
VirtualService 补 `/ap-cdn/` 前缀。**动 embed 挂载点或新起一个 host origin 时，这条路由要跟着走。**

## 回归网

只有 007 / 008 / 012 带专属测试。其余是补登记时的既有状态，不是"已验证"的意思。

| # | 测试 | 覆盖 |
|---|---|---|
| 007 | `engine/test/operations/sync-webhook-release.test.ts` | 逐 `FlowRunStatus` 穷举：终态释放、非终态（PAUSED / QUEUED）不释放、缺 id 不发、发布失败不外溢 |
| 008 | `worker/test/lib/execute/jobs/execute-flow.test.ts` | 引擎启动前终结的 run 也释放；正常跑完 worker 不插手；缺 id 不发；best-effort |
| 012 | `api/test/unit/app/flows/trigger/flow-trigger-side-effect.test.ts` | APP_WEBHOOK 启用即抛 `FEATURE_DISABLED`，报错含 patch 号 / piece 名 / flowId；MANUAL 等其余策略不受影响；**disable 仍能删监听器**（存量孤儿行必须清得掉） |

**007 的非终态分支在 dev 环境测不到**，故必须靠单测：能暂停的 piece（`core/delay`、`core/approval`、
`core/subflows`）都不在已装白名单里，唯一装了的 `core/webhook` 是「先答后停」——
`return_response_and_wait_for_next_webhook` 通过 `createWaitpoint({ responseToSend })` 在同一步就把
响应发出去了（`piece-executor.ts` 的 `getResponse()` 对 `case 'paused'` 返回 `responseToSend`），
所以调用方在暂停前已被回答，那里即使判断写反也观察不到症状。**一旦 `core/delay` 进白名单**
（"等 N 分钟再继续"这类需求），暂停就变成"还没人回答过"，这条判断立刻承重。

## 待办

- **011 / 012 / 013 的遗留项单独立账**：见 [VENDOR_TRIM_CHECKLIST.md](VENDOR_TRIM_CHECKLIST.md)。
  其中 VT-01（镜像构建）/ VT-02（`test-api`）/ VT-03（容器启动）是**尚未验证的构建路径**。
  ~~VT-04（rebase 重放顺序）~~ 已随 [D12](DECISIONS.md#d12) 关闭——但**它记录的 11 条悬空
  tsconfig 映射是真 bug，转入 VT-16 处理**（`--check` 只认多行写法的 `@activepieces/piece-*`，
  漏掉了 6 条无 `@` 前缀的单行映射，以及 `@ee/*` / `ee-auth` / `ui-feature-forms` / `Aminos`）。
- 003 / 004 / 005 的 D9 尚在草案态（`D9_PIECE_ONLINE_ADMIN_DRAFT.md`），评审通过后回填正式裁决号。
