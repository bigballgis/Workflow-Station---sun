# Vendor 树裁剪遗留 checklist（HERMES-PATCH-011 / 012 / 013）

> 2026-07-28 建。三个 patch 的内容见 [HERMES_PATCHES.md](HERMES_PATCHES.md#清单)，
> 保留清单与重放脚本见 [`activepieces/hermes/trim-vendor-pieces.mjs`](../../activepieces/hermes/trim-vendor-pieces.mjs)。
>
> **背景一句话**：公司内网 `pnpm install` 卡在 `@anthropic-ai/sdk@0.39.0`。追查发现该包来自上游
> vendor 进来的 694 个 community piece 之一（`piece-claude`），这些件既不在 `pieces.json` 白名单、
> 也不进成品镜像，唯一效果是把几百个第三方包拖进每一次 install。于是把 community 收敛到 4 个（首轮留下的 `piece-ai` 已于同日追删），
> 并顺手摘掉了 `/v1/app-events` 这个未鉴权端点。
>
> **本文件只收"这次裁剪留下的待办"**，不是 AP 集成的总账——那是
> [OPEN_GATES.md](OPEN_GATES.md)（阻塞门）和 [STATUS.md](STATUS.md)（现状）。
>
> **2026-07-29 更新**：裁剪已落为独立 commit
> [`a2194c06`](#vt-14-独立成-commit--已完成2026-07-28)（VT-14 关闭）；VT-03b 的浏览器渲染在验的过程中
> 反而挖出一个**与裁剪无关的存量缺口**（`/ap-cdn` 图标路由，`7d7e55f6` 已修，VT-03 全绿）；
> VT-10 由 codegraph 文件监听自愈。**P0 现在只剩 VT-11 与 VT-15 的产物尾巴。**

---

## 状态总览

| ID | 项 | 等级 | 状态 |
|---|---|---|---|
| VT-01 | 镜像构建实测 | **P0** | ✅ **通过（2026-07-28）** |
| VT-02 | `test-api` 三套集成测试 | **P0** | 🟡 **check-migrations 绿；三套集成测试被存量断裂挡住，跑不起来** |
| VT-03 | 容器启动 + builder 冒烟 | **P0** | ✅ **全绿（2026-07-29）**：服务端 + dev 真实环境 + 经 Kong + **浏览器渲染** |
| VT-04 | rebase 重放顺序陷阱（脚本加断言） | P1 | ⛔ **已作废（2026-07-30，[D12](DECISIONS.md#d12)）** — rebase 不会发生；**但它发现的 11 条悬空 tsconfig 映射转入 VT-16** |
| VT-05 | app-events 死链无提示 | P1 | ✅ **已完成（2026-07-29）** |
| VT-06 | `--check` 接进 CI | P1 | ✅ **已完成（2026-07-29）** |
| VT-07 | `SUPPORTED_APP_WEBHOOKS` flag 说谎 | P1 | ⬜ 未做 |
| VT-08 | crowdin 翻译源塌缩 | P2 | ✅ **已处置（2026-07-29，HERMES-PATCH-014）** |
| VT-09 | 上游 piece 源码不再可读的补救约定 | P2 | ✅ **已完成（2026-07-29）** |
| VT-10 | codegraph 索引重建 | P2 | ✅ **已自愈（2026-07-29 复查）** |
| VT-11 | **公司机器报错原文** (VT-12 的前置) | **P0** | ✅ **已取得（2026-07-30）**：见下方实测记录 |
| VT-12 | `@ai-sdk/*` 仍在 api/worker/engine 硬依赖 | **P0** | ✅ **已关闭（2026-07-30，VT-17）**：10 个厂商 provider 包在锁文件里**归零**；`ai` 仅剩 web 一个显式声明 |
| VT-16 | tsconfig 悬空映射 11 条 + `--check` 漏检单行写法 | P1 | ⬜ 未做（VT-04 的遗产） |
| VT-17 | **裁剪批 A**：AP AI 代理 / MCP server / EE chat / engine agent tools | **P0** | ✅ **已完成（2026-07-30，HERMES-PATCH-015）**：api + worker + engine + framework + server-utils + 根 package.json |
| VT-21 | `fast-xml-parser@5.2.5`：升 S3 链（`xml-builder@3.972.36+` 改用 `fast-xml-builder`） | **P0** | ✅ **已完成（2026-07-30，HERMES-PATCH-016）**：锁中 0 次；顺带摘掉 2 个零 import 的 aws 包 |
| VT-22 | `isolated-vm@6.0.2` Nexus 元数据不全（**卡着 `vitest@3.0.8` 的升级路径**） | **P0** | ⬜ 未做——不由我们控制，需 Nexus 管理方补全/重同步 |
| VT-18 | **裁剪批 B**：`packages/pieces/core/` 27 个件 | P1 | ⬜ 未做 |
| VT-19 | **裁剪批 C**：上游工程脚手架与 AP 自有 agent 规则 | P2 | ⬜ 未做 |
| VT-20 | **裁剪批 D**：AP 自带 embed 路由 + `packages/ee/embed-sdk` | P2 | ⬜ 未做（风险最高，需 builder 回归） |
| VT-13 | `piece-ai` 保留与否的政策裁决 | P1 | ✅ **已关闭（2026-07-28，已删除）** |
| VT-15 | AI Generate 产物与功能开关 | **P0** | ✅ **已完成（2026-07-29）**：功能停用 + 产物清理 |
| VT-14 | 17876 个删除独立成 commit | P1 | ✅ **已完成（`a2194c06`）** |

> ⚠️ VT-11 已证明这不是单一 `@anthropic-ai/sdk` 问题，而是公司 FOSS Guard 对锁文件中多个精准版本的
> 供应链隔离。删除无用 piece 仍然正确，但不能替代对**真正在跑的**依赖的升级、放行或内网镜像治理。
>
> **2026-07-30 修订（[D12](DECISIONS.md#d12)）**：这句话里的"保留依赖"当时被默认成不可动。
> 复查后发现被隔离的 `fast-xml-parser` 那条链上，**没有一个包是我们在跑的功能需要的**——
> 它们属于 AP 的 AI 代理 / MCP server / EE chat，全部无消费方。裁剪范围因此从 piece 扩到功能面
> （VT-17～VT-20），"隔离项"与"该删的东西"在很大程度上是同一批。

### VT-11 公司环境原始失败与依赖链（2026-07-30）

在公司 Nexus 上执行 `pnpm install --frozen-lockfile`，首个可复现失败为
`expr-eval@2.0.2` 被 FOSS Guard quarantine (HTTP 403)。锁文件证明依赖链不是本地
`packages/shared` 引入，而是 `packages/cli` 错误解析了 registry 包：
`packages/cli` → `@activepieces/pieces-framework@0.32.0` →
`@activepieces/shared@0.95.1` → `expr-eval@2.0.2`；同时 CLI 还直接解析了
`@activepieces/shared@0.96.2` → `expr-eval@2.0.2`。

已将 CLI 的三个内部依赖统一为 `workspace:*`，并同步修正 lockfile importer 为本地 `link:`。
重跑 frozen install 时锁文件校验通过，且不再请求 `expr-eval`，证明该修正有效。随后依次暴露新的、
彼此独立的隔离项：

- 全量开发依赖安装：`vitest@3.0.8` quarantine；同主版本 `3.2.7` 已通过公司 Nexus 实际下载和运行验证，
  但全仓升级的 lockfile 重算又被 Nexus 中不完整的 `isolated-vm@6.0.2` metadata 阻断。
- `--prod --frozen-lockfile`：`fast-xml-parser@5.2.5` quarantine，来源是
  `@aws-sdk/xml-builder@3.894.0` / `3.972.0` 的传递依赖。

因此 VT-11 的结论是：`expr-eval` 根因已从代码摘除，但完整 install 仍需继续升级上述保留依赖，
或由 FOSS Guard/Nexus 管理方对冻结版本完成风险复核与放行；不得通过绕过 Nexus 或放宽生产运行时联网解决。

> **⚠️ 2026-07-30 补充 —— VT-11 触发了策略变更 [D12](DECISIONS.md#d12)，并因此多出第三条路径。**
> 上面这段结论只列了两条路（升级 / 求放行），前提是"保留依赖"必须保留。**但那批依赖没有一个是我们
> 在跑的功能需要的**，见下方 VT-17。硬分叉之后，**删掉持有依赖的功能面**优于另外两条：依赖随功能消失，
> 不需要放行、不需要升级、install 面永久缩小。原结论的最后一句（不得绕过 Nexus / 不得放宽生产联网）
> **仍然有效**。

---

## 裁剪批次（D12 之后，2026-07-30 立项）

> 判定标准：**不在成品镜像里跑，或跑但没有消费方 ⇒ 删**。
> 每批的闸门（缺一不可）：`pnpm install` → `turbo run build --filter=web --filter=@activepieces/engine
> --filter=api --filter=worker` → 镜像构建 → dev 起容器 + builder 冒烟。**分批过闸，不许攒着一起验。**
>
> **明确保留，别误删**（每条都有实证，不是保守）：
> - `packages/cli` —— 自研件开发链路依赖它（[PIECE_DEVELOPMENT_HOWTO](PIECE_DEVELOPMENT_HOWTO.md)），
>   且 VT-11 的 `workspace:*` 修复正落在这里；
> - `packages/web` 的 AP 独立应用形态 —— `/ap-cdn` 资产只随它发布，砍掉内嵌 builder 图标全裂；
> - `app-event-routing.service.ts` + `app_event_routing` 表 —— HERMES-PATCH-012 已论证删了编译不过。

### <a id="vt-16"></a>VT-16 — tsconfig 悬空映射（P1，VT-04 的遗产）

`node -e` 逐条 `existsSync` 校验 `tsconfig.base.json` 的 `paths`，**当前 11 条指向已不存在的目录**：

| 悬空映射 | 来源 |
|---|---|
| `activepieces/piece-{microsoft-dynamics-365-business-central, microsoft-dynamics-crm, microsoft-sharepoint, snowflake, zuora, cashfree-payments}` | HERMES-PATCH-013 裁剪的漏网 —— **无 `@` 前缀 + 单行写法**，`pruneDeadPieceMappings` 只认多行的 `@activepieces/piece-*` |
| `Aminos` | 同上，连包名规范都不符（上游遗留） |
| `@ee/*`、`@activepieces/ee-auth`、`@activepieces/ee/billing/ui` | EE 剥离（AG-EE）的漏网 |
| `ui-feature-forms` | 上游早已删除的包 |

**做法**：直接删这 11 条；`--check` 的检测逻辑改为**遍历全部 `paths` 做存在性校验**
（不再按前缀匹配 piece），这样将来任何形态的悬空映射都逃不掉。

### <a id="vt-17"></a>VT-17 — 裁剪批 A：AP AI 代理 / MCP server / EE chat（**P0，= VT-12 的解法**）

> **⚠️ 立项时的依赖链写错了，2026-07-30 实施时用 `pnpm why` 更正**：
> `fast-xml-parser@5.2.5` 的持有者是 **S3 文件存储链**（`@aws-sdk/s3-request-presigner@3.894.0` +
> `@aws-sdk/client-s3@3.974.0` → `middleware-sdk-s3` → `core` → `xml-builder@{3.894.0,3.972.0}`），
> **不是 `@ai-sdk/amazon-bedrock`**。VT-11 原文没有指认 Bedrock，那一跳是立项时推断的。
> ⇒ **VT-17 不解决 `fast-xml-parser`**，那条转入 [VT-21](#vt-21)（有便宜解法）。
> VT-17 仍然成立，但它的收益是**摘掉 `@ai-sdk/*` 这一整族**，而不是关掉那个具体的 quarantine。

`@ai-sdk/*` 共 10 个（`amazon-bedrock` / `anthropic` / `azure` / `google` / `google-vertex` /
`openai` / `openai-compatible` / `provider` / `replicate` / `mcp`）+ `ai@^6.0.0`，
声明在 `server/api`、`server/worker`、`server/engine` 三个 `package.json`。真实 import 方只有 4 处：

| import 方 | 是什么 | 我们跑吗 |
|---|---|---|
| `server/api/src/app/ai/providers/` | AP 的 AI provider 代理 | **否** — AI Generate 已改 HTTP piece 直连模型端点（VT-15） |
| `server/api/src/app/mcp/`（448K，api 下第二大目录） | AP 自带 MCP server，供外部 AI agent 驱动 AP | **否** — 气隙内没有 MCP 客户端 |
| `server/worker/src/lib/execute/jobs/ee/chat/` | EE chat agent | **否** — AG-EE 只清了 `server/api/src/app/ee`，这个目录还在。[EE_REMOVAL_PLAN](EE_REMOVAL_PLAN.md) 已把它列为"条款未覆盖的三处 ee 目录"之一（**按 `LICENSE:5` 的路径条款它属 MIT**，法务确认待办）——但**删它不需要等法务**：理由是无消费方，不是许可 |
| `server/engine/src/lib/tools/index.ts`（+ `piece-executor.ts`） | 引擎侧 tool 调用胶水 | **否**，但**与 piece 执行主链路同文件，最需谨慎** |

**顺序**：先删前三个消费方 → 再看 engine 侧还剩什么 → 最后才从三个 `package.json` 摘依赖并重算锁。
engine 的 `piece-executor.ts` 是 piece 执行主链路（PATCH-007 的回归网也压在这条线上），
**动它必须连带跑 `engine/test/`**。

**验收**：`pnpm install --prod --frozen-lockfile` 不再解析 `@ai-sdk/*` / `ai@^6`。
这条要在公司机器上实测，宿主机全绿不算数（VT-11 的教训）。
（`fast-xml-parser` 不在本项验收内，见上方更正与 [VT-21](#vt-21)。）

#### 批 A 第一步已完成（2026-07-30，HERMES-PATCH-015）

删 `app/mcp/`（69 文件）+ `app/ai/`（14 文件）；摘接线点 `app.ts`（4 import + 4 注册）、
`server.ts`（2 import + 3 注册）、`database-connection.ts`（5 实体 + 5 import）。

- **迁移文件一律未动**——`mcp_*` / `ai_provider` 表由历史迁移建出，是既成事实；
  这次摘掉的只是 TypeORM 托管，**不产生 schema 变更**。
- **顺带关掉一个根路径暴露面**：上游把 MCP OAuth 的三组端点注册在**域名根**
  （`/.well-known/*`、`/mcp/*`、`/mcp/platform/*`），**连 `/api` 前缀都没有** ——
  比 HERMES-PATCH-012 摘掉的 `/v1/app-events` 更靠外。
- **api 侧依赖摘除 14 个**：10 × `@ai-sdk/*` + `ai` + `@aws-sdk/client-bedrock` +
  `ai-gateway-provider` + `cloudflare`（后三个是被删的 provider 文件的专属依赖；
  `cloudflare` 在 src 下仅有的两处匹配是迁移 SQL 里的 `cloudflareId` 列名字符串，不是 import）。
- **删掉一个上游自带的孤儿测试** `test/unit/app/chat/chat-compaction.test.ts`：
  它 import 的 `src/app/chat/chat-compaction` **自 `de4f6469` 纯净基线起就不存在**，
  即在 AP 0.84.0 里本来就是断的（**不是这次删出来的**，`git ls-tree HEAD` 已核）。
  它是 api 侧 `ai` 包的唯一残留引用。这也是 VT-02"存量断裂"的一部分。

**闸门结果**：`tsc -p tsconfig.app.json --noEmit` EXIT=0；
`turbo run build --filter=web --filter=@activepieces/engine --filter=api --filter=worker --force`
**9/9 successful**（依赖摘除 + `node_modules` 剪枝后复跑仍 9/9）。

**install 面实测收益（诚实计数）**：锁文件包条目 **5147 → 5119（−28）**。
之所以只有 28、且 `@ai-sdk/` 在锁里仍有 210 处引用——**worker 还声明着 9 个、engine 还声明着 `ai`**。
`@ai-sdk/*` 要整族消失，必须等 `worker/.../ee/chat/` 与 `engine/src/lib/tools/` 一起处理完。
**本步不要单独拿去公司机器验收**，那会得到"几乎没改善"的错误结论。

**剩余**：`server/api/src/app/agents/mcp-tool-validator.ts` 仍 import `@modelcontextprotocol/sdk`
—— 那是 **agents 模块**（与已删的 `app/mcp/` 不是一回事），属另一个功能面，另立项。

#### 批 A 第二步已完成（2026-07-30，同 HERMES-PATCH-015）

| 位置 | 动作 |
|---|---|
| `worker/src/lib/execute/jobs/ee/` | 删（3 文件 663 行：`execute-chat-agent` / `chat-worker-tools` / `chat-mcp-client`）+ `job-registry.ts` 摘一条 |
| `shared/src/lib/automation/workers/job-data.ts` | 摘 `EXECUTE_CHAT_AGENT` **5 处**：枚举、优先级 switch case、`NON_SCHEDULED_JOB_TYPES`、`ExecuteChatAgentJobData` schema+type、`JobData` union |
| `api/src/app/workers/job-queue/job-queue.ts` | `OneTimeJobAddParams` 类型联合摘一项（**纯类型，无运行时行为**） |
| `engine/src/lib/tools/` | 删（`index.ts` 413 行 + `tsort.ts`，后者仅被前者使用） |
| `engine/src/lib/handler/piece-executor.ts` | 摘 `agent.tools` 注入 + 2 个 import |
| `pieces/framework/src/lib/context/index.ts` | 摘 `agent: AgentContext` + `AgentContext` + `ConstructToolParams` + `from 'ai'` import |
| `pieces/framework/src/lib/test/index.ts` | 摘测试桩里的 `agent` |
| `server/utils/src/chat-ai-utils.ts` | 删 + `index.ts` 摘 2 处 export（**它的唯一消费方就是被删的 `ee/chat`**，已用 `git show HEAD:` 佐证） |

**为什么必须动 `shared` 和 `pieces-framework`**（比 api 那次宽，不是范围蔓延）：
- `job-registry` 是 `Record<WorkerJobType, JobHandler>`，**穷举类型**——不摘枚举就编译不过；
- `agent.tools` 是 `piece-executor` 注入给 piece 的**上下文契约**，声明在 framework。
  已核实 **保留的 4 个件与 2 个自研件都不使用 `context.agent`**，且没有任何生产者入队
  `EXECUTE_CHAT_AGENT`（`git ls-tree` + 全树 grep）。

**依赖摘除合计 42 处声明**：api 14、worker 9、server-utils 9（含 `@openrouter/ai-sdk-provider`）、
engine 1、framework 1、**根 `package.json` 11**（10×`@ai-sdk/*` + `ai`）+ `ai-gateway-provider`。

> ⚠️ **根 `package.json` 差点被漏掉**：中途一次"已清干净"的核实用的 glob 是
> `packages/*/package.json packages/*/*/package.json`，**不含仓库根文件**，于是报早了一次。
> 复核时 `pnpm why @ai-sdk/anthropic` 显示厂商包仍在锁里，才查到根声明。
> **核依赖清除必须带上根 `package.json`。**

**web 的隐式依赖被显式化**：`web/src/features/chat/` 从 `'ai'` import 的
`getToolName` / `isToolUIPart` 是**运行时函数不是类型**，而 web **从未声明过 `ai`**——
一直靠 `@openrouter/ai-sdk-provider` 的 peer 提升出来解析。摘掉那些包会静默断掉 web，
故给 `packages/web` 补上 `"ai": "6.0.170"`。**这是修一个既存的隐患，不是新增依赖。**
（web 的 chat-with-ai 前端本身仍在，属另一批。）

**闸门结果**：
- `turbo build`（web/engine/api/worker）**9/9 successful**；
- `tsc --noEmit`：worker / api / server-utils / shared / pieces-framework **全 OK**；
  engine 6 个报错**全在 `network/dns-lookup-guard.ts`**（`@types/node` 的 dns 签名问题），
  **已用 git worktree 对基线核实：HEAD 上报同样的错**，属存量。
- 单测见下方「本批建立的测试基线」。

**install 面实测**：锁文件包条目 **5147 → 5030（−117）**；
`@ai-sdk/{anthropic,openai,google,azure,amazon-bedrock,replicate,google-vertex,mcp,openai-compatible}`
在锁里**全部归零**。`ai@6.0.170` 保留（web 需要），它不在 FOSS Guard 隔离清单上。

#### 本批建立的测试基线（**三条都已对基线核实为存量红，不是本次改动**）

| 套件 | 结果 | 判定依据 |
|---|---|---|
| engine `flow-codes.test.ts > 执行需要 npm 包的代码` | 1 红 / 9 绿 | git worktree 跑 HEAD：**同一断言同样红**（`expected 'FAILED' to be 'RUNNING'`） |
| worker `isolate.test.ts > argv 静态 --dir 顺序` | 1 红 / 35 绿 | 同上，HEAD 同样红 |
| api `rate-limiter-interceptor.test.ts` 6 红（`expected 'REJECT' got 'ALLOW'`） | — | **AG-EE 的必然结果**：`concurrency-pool-stub.ts` 的 `getPoolLimit`/`getProjectPoolId` 恒返回 `null`（EE_REMOVAL_PLAN G5/R7，"HERMES 无租户配额 C13"）⇒ 拦截器恒 ALLOW，这批上游 EE 配额测试**设计上不可能通过** |

> ⚠️ **给 api 套件取基线的坑**：worktree 若软链主树的 `node_modules`，基线代码里还 import 的
> `@ai-sdk/*` 已被剪枝 ⇒ 大量文件加载即失败（29 文件红但只收集到 69 个测试），
> **这种对比无效**。api 那三条改用"读实际报错 + 定位到 AG-EE 的桩"来判定。

#### 镜像构建 + dev 冒烟（2026-07-30 补，覆盖 VT-17 与 VT-21 两批）

**镜像构建**：`docker compose -f deploy/environments/dev/docker-compose.dev.yml build activepieces`
成功；镜像 **2.07GB → 1.98GB（−90MB）**。
`pnpm install` 阶段 **resolved 2994 / reused 2874 / downloaded 4** ——
裁剪后的依赖闭包在离线 store 里是完整的，这本身就是气隙可行性的一个信号。

**容器**：`up -d` 后 **30 秒转 healthy，RestartCount=0**。

**端点判据**（容器内 `curl`，旧镜像 → 新镜像）：

| 探针 | 旧 | 新 | 判定 |
|---|---|---|---|
| `/api/v1/flags` | 200 | **200** | 健康 |
| `/api/v1/ai-providers` | 403 | **404**（`application/json`，`Route not found`） | AI provider 代理已摘 |
| `/mcp` | 405 | **200 `text/html`** | 见下方 ⚠️ |
| `/.well-known/oauth-authorization-server` | **200 无鉴权** | **200 `text/html`** | 见下方 ⚠️ |
| `/api/ap/v1/flags`（经 Kong :8000 / edge :3000） | 200 | **200** | L2 通道未受影响 |

> ⚠️ **根路径端点的移除表现为「SPA 兜底」而不是 404，直接断言 404 会误判。**
> AP 的静态兜底对任何**非 `/api`** 路径都返回 `index.html`（`text/html` 200）。
> 判据要用**响应体**：`/mcp` 与 `/.well-known/*` 现在与不存在的 `/zzz-does-not-exist`
> 返回**完全相同的 HTML**，即路由确实没了。`/api` 之下没有兜底，所以 `ai-providers`
> 是干净的 JSON 404。
>
> 顺带实证了 [HERMES-PATCH-015](HERMES_PATCHES.md) 说的暴露面：改动前
> `/.well-known/oauth-authorization-server` 在域名根**无鉴权返回 200**。

**PATCH-009 的 `/ap-cdn` 图标链路**：三个入口（edge :8085 / DW 前端 :3102 / Kong :8000）
取真实资产均 200；`/api/v1/pieces` 返回 **13 个件**（与 `hermes/pieces.json` 白名单一致），
其 `logoUrl` 经 edge 逐条请求 **13/13 全部 200**。

> **查图标别按 `<piece-name>.png` 找**：实际 `logoUrl` 大多是 `/ap-cdn/pieces/new-core/*.svg`
> （自研件在 `hermes/`，`postgres`/`xml` 才是根下的 `.png`）。
> 按 `<name>.png` 检查会得到"11 个缺失"的假警报——**新旧镜像该目录都是 22 个文件、缺的完全一样**。

**未覆盖：浏览器渲染（VT-03b）**。DW 走统一 FQDN 的 `/dev/` 路径，落到登录页；
**代输凭据不在允许范围内**，故这一层留待人工登录后驱动。
可用的替代证据是上面那条"13/13 logoUrl 全 200"——builder 图标渲染依赖的正是它。

### <a id="vt-18"></a>VT-18 — 裁剪批 B：`packages/pieces/core/` 27 个件（P1）

与 HERMES-PATCH-013 处理 community 的**完全同一个论证**，只是当时没把 `core/` 收进来：

- **源码里 0 处真 import** —— `grep -rn "from '@activepieces/piece-"` 在 server/web 下无匹配；
  出现的 `@activepieces/piece-*` 全是**字符串常量**（数据库迁移里的 piece 名、MCP 工具的元数据）；
- Dockerfile 构建期已 `rm -rf packages/pieces/{core,custom,community}` ⇒ **对成品镜像零贡献**；
- 白名单 13 件里，11 件走 registry tarball（`hermes/pieces.json` + `hermes/tarballs/`），
  2 件是自研（`biz-calendar` / `hash-helper`，源码在 `community/`）⇒ **运行时不从 `core/` 取任何东西**。

**注意**：迁移文件里的字符串常量**不能跟着删**——那是历史迁移的既成事实，改了会破坏 `check-migrations`。
删目录 + 清 tsconfig 映射即可。`packages/pieces/custom/` 只剩一个 `README.md`，一并处理。

### <a id="vt-19"></a>VT-19 — 裁剪批 C：上游工程脚手架（P2）

纯上游开发设施，气隙内无消费方，且**零编译风险**（不在任何 tsconfig / workspace 里）：
`.github/`（196K，上游 CI）、`.devcontainer/`、`.verdaccio/`、`benchmark/`、`.husky/`、
`.all-contributorsrc`、`CONTRIBUTING.md`、`crowdin.yml`（HERMES-PATCH-014 当初"留着只为 diff 干净"，
D12 之后**直接删**，连同 `package.json` 里那两个 fail-loud 的 npm script）、
AP 自带的 `docker-compose*.yml` 与 `deploy/`（我们用 `deploy/` 根目录那套）。

**顺手解决一个隐性污染**：`activepieces/` 下有上游自己的 `.claude/`（含 `settings.json`、`agents`、
`rules`、`skills`）、`.cursor/`、`.agents/`（796K）以及多份 `CLAUDE.md` / `AGENTS.md`。
它们会在进入该目录工作时被加载，**与本仓库 `.cursor/rules` 唯一真源的约定冲突**
（见根 [CLAUDE.md](../../CLAUDE.md) 与规则 `ai-guidance-sync`）。

### <a id="vt-21"></a>VT-21 — `fast-xml-parser@5.2.5` 的真实解法：升 S3 链（**P0**）

VT-11 的第三个隔离项，从 VT-17 分出来（见 VT-17 顶部的更正）。持有链：

```
@aws-sdk/s3-request-presigner@3.894.0 + @aws-sdk/client-s3@3.974.0
  → @aws-sdk/middleware-sdk-s3@{3.894.0, 3.972.0}
  → @aws-sdk/core@{3.894.0, 3.972.0}
  → @aws-sdk/xml-builder@{3.894.0, 3.972.0}
  → fast-xml-parser@5.2.5      ← quarantine
```

**为什么便宜**：`@aws-sdk/xml-builder@3.972.36` 起，上游把 `fast-xml-parser` 换成了另一个包
`fast-xml-builder@1.3.0`。所以不需要求放行、也不需要换实现，只要把 S3 这几个包对齐到能解析出
`xml-builder@3.972.36+` 的版本，隔离项自然消失。

> ⚠️ 立项时写的是"`xml-builder@3.972.36` 的条目里根本没有 `dependencies` 块"——**那是看错了**：
> 当时 grep 到的是锁文件 `packages:` 段的元数据条目（只有 `resolution` / `engines`），
> 依赖在 `snapshots:` 段。结论不变（不再依赖 `fast-xml-parser`），但机制是**换包**不是**去依赖**。

**注意两件事**：
1. **S3 是在跑的功能**（文件存储，`api/src/app/file/s3-helper.ts`），不能用"删功能面"的办法，这是类 ② 依赖；
2. 升 aws-sdk 会带动一批 `@smithy/*` 重算——**锁文件重算本身在公司 Nexus 上被 `isolated-vm@6.0.2`
   的不完整 metadata 堵过一次**（VT-11）。所以这项必须**在公司机器上验证**，宿主机绿不算数。

#### 已完成（2026-07-30，HERMES-PATCH-016）

**改动**（根 `package.json` 与 `api/package.json` 两处都改，**根文件不能漏**——VT-17 的教训）：

| 依赖 | 前 | 后 | 理由 |
|---|---|---|---|
| `@aws-sdk/client-s3` | 3.974.0 | **3.997.0** | 其 `@aws-sdk/core` 是 caret 范围 `^3.973.13`，重解析即取到 ≥3.976.0 那支 |
| `@aws-sdk/s3-request-presigner` | **3.894.0** | **3.997.0** | 真正把 `core@3.894.0` 钉住的就是它 |
| `@aws-sdk/client-bedrock` (根) | 3.1017.0 | **删** | 零源码 import——被删的 `ai/providers/bedrock-provider.ts` 的遗留 |
| `@aws-sdk/client-secrets-manager` (根) | 3.997.0 | **删** | 零源码 import——AG-EE 已把 secret manager 桩掉（G4） |

**解析结果**：`@aws-sdk/core` → 3.976.0 / 3.977.3；`xml-builder` → 3.972.36 / 3.972.37。

**验收**：`fast-xml-parser@5.2.5` 在锁文件中 **0 次**。
锁条目 5030 → **4988**（本批 −42；VT-17+VT-21 合计 5147 → 4988，**−159**）。

> **`fast-xml-parser` 本身并没有消失**，也不该消失：根与 `api/package.json` **直接声明**
> `"fast-xml-parser": "^5.5.6"`，解析到 **5.7.0**——**不是**被隔离的 5.2.5。别把这条记成"已移除该库"。

**闸门**：`turbo build` 9/9；`api tsc --noEmit` OK；
`api` 单测 **17 failed | 15 passed (32 files) / 38 failed | 162 passed (200)**——
与升级前**逐位相同**，零新增失败（那 38 条的成因见 VT-17 的测试基线表）。

**VT-11 四个隔离项的最新账**：

| 项 | 锁中出现 | 状态 |
|---|---|---|
| `expr-eval@2.0.2` | **0** | ✅ `55023fd4` 的 `workspace:*` 修复 |
| `fast-xml-parser@5.2.5` | **0** | ✅ VT-21 |
| `vitest@3.0.8` | **0** | ✅ `259f34c5`（2026-07-30）——见下方"降级而非升级" |
| `isolated-vm@6.0.2` | 2 | ⬜ **仍在**——它不是 quarantine，是 Nexus 上元数据不全 |

#### `vitest@3.0.8` 的解法是**降级**，不是升级（2026-07-30，`259f34c5`）

上一版这张表写的是"⬜ 仍在，被 `isolated-vm@6.0.2` 的不完整 metadata 阻断"——那是把
**升到 3.2.7** 当成了唯一出路。实际走的是反方向：**3.0.3 在公司 Nexus 上是被供的**，
于是 21 个 package.json（仓库根、`shared`、`pieces-framework`、四个 server 包、
`packages/pieces/core/` 下 14 个件）的声明点全部改到 3.0.3，**不加 pnpm override**，
锁文件因此对"实际请求的是什么"保持诚实。`isolated-vm` 不再是这条的前置障碍。

> 坑：第一遍只改到 7 个文件，因为 sweep 用了 `packages/*/package.json` +
> `packages/*/*/package.json` 两级 glob，而 `packages/pieces/core/<name>/package.json`
> 深一层。与 VT-17 漏掉仓库根 manifest 同型——**依赖 sweep 一律 `find` 全树，不要 glob**。

#### 收尾：`@vitest/pretty-format` 的浮动版（2026-07-30）

`vitest@3.0.3` 对六个 `@vitest/*` 同伴是精确 pin，唯独 `@vitest/pretty-format` 写的是
`^3.0.3`，于是它浮到了 **3.2.7**——是 vitest 家族里唯一会随 registry 漂移的包。已加
`pnpm.overrides` 钉到 `3.0.3`，锁中该包收敛为单版本。

**同时修掉一个存量失配**：锁文件头部的 `overrides:` 记着三条 manifest 里根本不存在的条目
（`@aws-sdk/util-format-url@3.972.40→3.972.37`、`@smithy/core@3.31.1→3.30.0`、
`@smithy/signature-v4@5.6.12→5.6.10`，形状即 VT-21 那批的 FOSS Guard 规避降级，
锁提交了、manifest 没提交）。后果是 `pnpm install --frozen-lockfile` **必挂**
（`ERR_PNPM_LOCKFILE_CONFIG_MISMATCH`）——包括 `activepieces/Dockerfile` 的镜像构建那一步。
三条已补登进 `pnpm.overrides`；若当时任其在重算中丢失，`@smithy/core` 会退回被躲开的 3.31.1。
锁 packages 条目 2970 → 2967，`--frozen-lockfile` 现已通过。

**闸门**（HEAD worktree 取基线，同一 worktree 套新锁复跑，逐位相同）：
`engine` 6 failed | 318 passed (324)；`worker` 1 failed | 212 passed (213)。

> ⚠️ **测试环境雷**：`~/node_modules/` 下存在一份散装 npm 安装（含 `node@22.13.0`）。
> pnpm/npx 逐级向上拼 `node_modules/.bin`，凡是放在家目录下的工作树都会被降到那个 node，
> 而 `zlib.zstdDecompress` 要 22.15.0 才有——`engine` 会炸成 `21 files failed`，
> 全是 `promisify(...) received undefined`，**看起来像依赖问题，其实不是**。
> 取基线请用 `/tmp` 下的 worktree（这也是本节数字的取法）。

⇒ **VT-11 四项里只剩 `isolated-vm@6.0.2` 的 Nexus 元数据**。它已不卡 `vitest`，
但仍卡公司机器上的**完整锁重算**；不由我们控制（需 Nexus 管理方补全或镜像重同步），
**属类 ② 里"只能求放行"的那一格**。

### <a id="vt-20"></a>VT-20 — 裁剪批 D：AP 自带 embed 路由 + `packages/ee/embed-sdk`（P2，风险最高）

`packages/ee/embed-sdk` 的消费方是 `web/src/app/routes/embed/{index,embedded-connection-dialog}.tsx`
与 `web/src/components/custom/home-button.tsx`——即 **AP 官方的 iframe 内嵌方案**。
我们走的是 [Q3](DECISIONS.md#q3) 的 lib-mode + Shadow DOM 纯 builder 组件，与之无关。

**但这批必须最后做、且单独过回归**：动的是 `packages/web` 内部，而 builder 组件正是从那里
lib-mode 构建出来的。`home-button.tsx` 是**共用组件**（不止 embed 路由在用），
不能跟着 embed 路由一起删，只能摘掉其中的 embed 分支。
**闸门加一条**：DW 内嵌 builder 的浏览器渲染冒烟（同 VT-03b）。

---

## P0 — 验证缺口（改完从没在真实构建路径上跑过）

这一类最危险，因为宿主机 `pnpm install` + `turbo build` 全绿会让人误以为已经验证。
立项时覆盖到的只有"宿主机能装能编"，**镜像、集成测试、运行时三层全空**。

> **2026-07-29 结算**：镜像层（VT-01）与运行时层（VT-03，含浏览器）都已补上并全绿；
> 集成测试层（VT-02）**至今是空的**，且不是本次改动的锅——见下文那条归属 AG-EE 的存量断裂。
> 也就是说：这个仓库的 api 集成测试对本轮裁剪**没有提供过任何回归信号**，运行时证据全部来自手工 A/B。

### VT-01 镜像构建实测 ✅ 通过（2026-07-28）

- [x] `docker build -f activepieces/Dockerfile -t activepieces:vt01-test activepieces/` → **exit 0**，
      2.07GB（与裁剪前的 `activepieces:0.84.0-ee-removed` 同尺寸——裁掉的 690 个件本就不进成品镜像，
      这从反面印证了它们确实是纯粹的构建期死重）

逐个检查点的实测结果：

| # | 步骤 | 结果 |
|---|---|---|
| 1 | build 阶段 `pnpm install --frozen-lockfile` | ✅ `Scope: all 43 workspace projects` |
| 2 | `turbo run build`（web / engine / api / worker） | ✅ |
| 3 | **`rm -rf packages/pieces/{core,custom,community}` + `pnpm install`** | ✅ `Scope: all 12 workspace projects`——**下面那个悬念解开了：`community` 父目录整个删掉，pnpm 对匹配不到的 glob 确实不报错** |
| 4 | run 阶段 `pnpm install --prod` | ✅ 12 projects |
| 5 | `prewarm-pieces.sh` | ✅ 13 个白名单件全部烘入 `cache/v11/common/pieces/@activepieces/`，**13 个 `ready` 标记齐全**，无任何 AI 件残留 |

> 原先的疑点（保留作记录）：
[`Dockerfile:81`](../../activepieces/Dockerfile:81) 现在写 `rm -rf packages/pieces/{core,custom,community}`，
**连 `community` 父目录一起删**（原来是 `find … -mindepth 1` 只删子目录），而
[`pnpm-workspace.yaml:14`](../../activepieces/pnpm-workspace.yaml:14) 仍声明 `packages/pieces/community/*`。
当时只能靠推理判断没事，现已由上表第 3 行实测证实。

> **2026-07-29 补：dev 构建脚本的新鲜度判断曾会让这些验证白做。**
> `build-and-deploy.ps1` 里 AP 镜像的新鲜度**只看 `activepieces/Dockerfile` 一个文件的 mtime**，
> 而 HERMES 的源码补丁全都落在 `packages/server` / `packages/web` 下 —— 在已构建过镜像的机器上
> 拉这个分支，脚本会报 "image fresh, skipping"，然后拿旧镜像跑新代码，**无任何提示**。
> 当场实测：旧监视集最新 mtime `04:39`，早于镜像的 `06:20`，VT-05 的改动确实不会进镜像。
> 已改为监视 `Dockerfile` + `pnpm-lock.yaml` + `tsconfig.base.json` + `hermes/` +
> `packages/server` + `packages/web/src`（两次扫描 438ms）。
> **`pnpm-lock.yaml` 是其中最关键的一条**：删除不改动留存文件的 mtime，裁掉 690 个目录之后，
> 锁文件是"vendor 树变过"唯一稳定的信号。

### VT-02 `test-api` 🟡 已执行（2026-07-28），结论是"测不了"

不需要外部 Postgres / Redis —— `packages/server/api/.env.tests` 用的是 `AP_DB_TYPE=PGLITE`
+ `AP_REDIS_TYPE=MEMORY`（嵌入式库、内存 Redis），不碰 dev 环境。

> ⚠️ **turbo 会中止后续任务**：`npx turbo run check-migrations test-ce test-ee test-cloud
> --filter=api --concurrency=1` 里 `test-cloud` 先跑先挂，其余三个只打印 "cache bypass" 就没执行
> （汇总里那 6 个 successful 全是 `api:build` 的依赖链，容易误读成"大部分过了"）。
> **要逐个 `pnpm --filter api run <task>` 跑才拿得到完整信号。**

| 任务 | 结果 |
|---|---|
| `check-migrations` | ✅ **PASS** —— `No changes in database schema were found` / `✅ No missing migrations detected`。本轮没动任何实体，这条符合预期 |
| `test-cloud` | ❌ 37 个文件全挂，`Tests no tests`（**一个断言都没执行到**） |
| `test-ce` | ❌ 42 个文件 40 挂 2 过；跑起来的那 2 个文件里 22 个用例全过 |
| `test-ee` | ❌ 9 个文件全挂 |

**三套集成测试的失败与本次改动无关，且能证死。** 全部失败归为两类，无一例外：

1. **`Failed to load url .../src/app/ee/…`** —— 测试 helper 仍在 import 已被 AG-EE 删除的 EE 模块：
   [`test/helpers/mocks/index.ts:78-80`](../../activepieces/packages/server/api/test/helpers/mocks/index.ts:78)
   引 `ee/api-keys/api-key-service`、`ee/oauth-apps/oauth-app.entity`、`ee/platform/platform-plan/platform-plan.entity`；
   ee 套另有 `ee/secret-managers/secret-manager-cache`、`ee/platform/concurrency-pool/concurrency-pool.service`。
   证据：`git ls-files activepieces/packages/server/api/src/app/ee` **为空**（HEAD 里就没有这个目录），
   且 `git status` 对该路径**为空**（本轮一个字节没碰）。
2. **`TypeError: The "original" argument must be of type function`** ——
   `file-compressor.ts:5` 的 `promisify(zstdCompressCallback)`，本地 Node 的 zlib 没有该导出。同样是未触碰的文件。

**所以 VT-02 的诚实结论不是"通过"，而是"测不了"**：`test/helpers/mocks/index.ts` 在**收集阶段**就崩，
套件根本没走到起服务、发请求那一步 —— 它既没有证伪 HERMES-PATCH-012，也**没有为它提供任何证据**。
全量 grep `app-event` / `openapi` / 路由清单断言：**零命中**，连相关用例都不存在。

> PATCH-012 的运行时证据只有 [VT-03](#vt-03-容器启动--已验2026-07-28) 那组 A/B 对照。
> 那组是真跑起来的服务、真发的 HTTP 请求，可信度高于这里跑不起来的集成测试。

### 这条存量断裂归属 AG-EE，不归本 checklist

- [ ] `src/app/ee/` 已删但测试 helper / ee 集成用例仍在 import 它 → **CE、EE、Cloud 三套集成测试当前全部无法收集**。
      这属于 [OPEN_GATES.md](OPEN_GATES.md) 里 AG-EE 的"余文档/CI 子项"，不是 vendor 裁剪引入的。
      在它修好之前，**这个仓库的 api 集成测试对任何改动都不具备回归能力**——这一点值得单独让人知道。

已确认**不会**受影响的：`worker` 的 `webhook-url.test.ts`（只断言 URL 字符串拼接，不打请求）、
`flow-trigger-side-effect.test.ts`（mock 掉了 service，而 service 被保留）。

### VT-03 容器启动 🟢 已验（2026-07-28）

用**一次性的空 Postgres / Redis** 起 `activepieces:vt01-test`，刻意不接 dev 的库与网络——
dev 的 AP 正跑着，第二个实例会抢 BullMQ 任务、写 worker 注册表。空库还顺带验了迁移从零跑一遍。

- [x] 容器起来，`/api/v1/flags` **9 秒**返回 200（这正是 dev compose 自己的 healthcheck 探针，
      200 即证明 `app.register` 链走完 —— PATCH-012 摘掉一个 register 没有断链）
- [x] TypeORM 迁移空库跑完：`migrations` 表 **354** 行
- [x] `GET /` 返回 200，web bundle 在服务
- [x] **HERMES-PATCH-012 的 A/B 对照**（同一段探测代码分别在两个镜像内跑）：

  | 路由 | 旧镜像 `0.84.0-ee-removed`（未打 012） | 新镜像 `vt01-test`（已打 012） |
  |---|---|---|
  | `/api/v1/app-events/slack` | GET=500 POST=**400**（端点存在，空表 → 400） | GET=404 POST=**404**（已消失） |
  | `/api/v1/flags` | GET=200 | GET=200 |
  | `/api/v1/authentication/sign-in` | POST=400 | POST=400 |

  两条无关路由逐字相同，只有 app-events 从"存在"变 404 —— 效果精确，无误伤。

> 两个教训记下来：① `AP_ENCRYPTION_KEY` 必须是 **32 位十六进制**，compose 里那个
> `dev-activepieces-key-change-me` 只是占位符，照抄会在 `validateEnvPropsOnStartup` 崩溃并被 PM2
> 反复重启；② 探路由时 **GET 不要带 body**，Fastify 会一律判 400，把真实状态码全部掩盖
> （第一版对照组就是这么废掉的）。
>
> 日志里两条 `Socket.IO connection error`（level 50）是**存量噪音**：dev 那个 healthy 容器里同样的
> 报错有 **599 条**。

### VT-03c dev 环境已换成新产物 🟢（2026-07-28）

按用户要求把 dev 的 AP 容器换成 VT-01 的构建产物。**留了退路**：旧镜像另存
`activepieces:pre-vt01-rollback`（`sha256:e6f67f0b…`），新产物 `sha256:450e5c27…` 顶上 compose
使用的 `activepieces:0.84.0-ee-removed` tag，再 `docker compose up -d --no-deps activepieces`。

- [x] **5 秒 healthy**，`running image` 确认为 `450e5c27…`
- [x] **打在真实 dev 库上**（不是 VT-03 那个空库）—— 日志里**没有任何 migration 记录**，
      证明这轮改动没引入 schema 变更，对已迁移的库是安全的
- [x] 容器内路由复验：`app-events` 404 / `flags` 200，与空库那轮一致
- [x] **经 Kong 复验 embed 实际走的那条链**（宿主 → `:8000`）：

  | 经 Kong 的路径 | 结果 |
  |---|---|
  | `/api/ap/v1/flags` | **200** —— embed 的 API base 通 |
  | `/api/ap/v1/app-events/slack` | **404** |

  第二行顺带把 PATCH-012 的安全动机**实证**了一遍：这条路径此前**确实**能经 Kong 被匿名打到
  （Kong 对 `/api/ap` 不验 JWT、透明转发），现在没了。

> 日志里唯一的 level=50 是**我自己探测造成的**：`POST /api/v1/authentication/sign-in` 带 `{}`
> 触发 `body/email`、`body/password` 校验失败 → 400。不是故障。

回滚（若需要）：

```bash
docker tag activepieces:pre-vt01-rollback activepieces:0.84.0-ee-removed && docker compose -f deploy/environments/dev/docker-compose.dev.yml up -d --no-deps activepieces
```

### VT-03b 浏览器渲染 ✅ 已验（2026-07-29，commit `7d7e55f6`）

- [x] DW 设计态里嵌的 AP builder 在浏览器里正常渲染（web bundle 这次重新构建过，6774 modules）

走仓库自己的 `/verify-ui`（Playwright）流程验的，截图在
`frontend/developer-workstation/verification-screenshots/2026-07-29_ap-cdn-*.png`（5 张：
Automation tab + piece picker 的 apps / utility / 滚动态）。Shadow DOM 挂载与
`:root`→`:host` 主题变量重写**都正常**——历史上那个"主题变量全空、builder 整体静默降级"的坑没有复发。

> **但这一轮不是白看的：它挖出了一个此前无人发现的存量缺口。**

#### 顺带发现：`/ap-cdn` piece 图标全裂（**与本次裁剪无关**）

打开 Automation tab，凡是 `logoUrl` 指向 `/ap-cdn/` 的件（HTTP、Webhook、Text Helper、CSV…）
图标**全是灰色占位方块**。Router 和 Code 看着正常，只因为它们的图标是内联 SVG——
这个巧合把"整条路由缺失"伪装成了"少数几个件坏了"，很容易误判。

**归属要说清楚：这不是 `a2194c06` 引入的回归，而是 embed 路径从第一天起就有的洞。**
镜像里 `/ap-cdn` 的镜像盘在 AP 自己的 `publicDir`（`packages/web/public/ap-cdn`），
而 DW 内嵌的 builder 是 **Vite lib-mode 产物，根本不产出 publicDir**；
Kong 与 edge 也从来没有路由过 `/ap-cdn`。独立 AP 应用一直是好的，所以没人碰到。

**修法**（已落 `7d7e55f6`）：把 `/ap-cdn` 路由回 AP 服务，而不是把资源拷进 DW 镜像——
DW 的两个 Dockerfile 对 dist 落点不一致（`Dockerfile.local` → `html/dev`，`Dockerfile` → html 根），
拷进去会在 dev 变成 `/dev/ap-cdn/`，而 `logoUrl` 是**根绝对路径**，改了就会让独立 AP 应用 404。
两种方案都躲不掉 edge/Istio 路由，于是取路由方案：

| 层 | 改动 |
|---|---|
| Kong | `activepieces-cdn-service` + `activepieces-cdn-route`（`/ap-cdn`，`strip_path false`），**route 级 3000/min** —— piece picker 一帧内拉全部已装件的图标，全局 600/min 会把它们变回灰块（429） |
| DW nginx + dev edge nginx | `location ^~ /ap-cdn/` → Kong（走 Kong 而非直连 AP，DW nginx 只有 `KONG_PROXY_URL`，不必新增上游环境变量） |
| k8s | preprod/uat 两份 Kong configmap 同路由；DEV-only 的 DW-frontend VirtualService 加 `/ap-cdn/` 前缀 |

证据：`/ap-cdn/pieces/new-core/webhooks.svg` 在 `:3000`（edge）与 `:3102`（DW 直连）均 200，未知路径仍 404；
Playwright 在 FU 50030 的 Automation tab 上录到 **12 个不同 `/ap-cdn` 资源全部 200**，含自研件的
`/ap-cdn/pieces/hermes/*.svg`。

> **给将来搬 embed 宿主的人**：`/ap-cdn` 是**根绝对**的跨应用引用，宿主一换就得重新接这条路由。
> 这条约定已写进 [HERMES_PATCHES.md](HERMES_PATCHES.md) 的 009 条目。

---

## P1 — 这次亲手埋下的雷（现在无害，将来咬人）

### VT-04 rebase 重放的顺序陷阱 ✅ 已完成（2026-07-29）

- [x] [`trim-vendor-pieces.mjs`](../../activepieces/hermes/trim-vendor-pieces.mjs) 加 fail-loud 前置断言
      `assertNothingStillNeedsDoomedPieces()`
- [x] `web/tsconfig.app.json` / `tsconfig.spec.json` 纳入映射清理范围（`TSCONFIGS` 常量）

**闸门怎么判**（不是硬编码那 4 个件名，所以上游将来新增同类依赖也拦得住）：

1. 递归扫 `packages/**/package.json`（跳过 `node_modules`、`dist`、待删目录自身），
   任何一个 manifest 只要还依赖待删件的包名就拒跑；包名以各件 `package.json` 的 `name` 为准，
   不靠"目录名加前缀"猜。
2. 外加一条 012 专项探针：`app-event-routing.module.ts` 还在 = 补丁没重放。
3. 树已收敛（无件可删）时整个断言直接返回 —— 否则日常 `--check` 会被这些探针刷噪音。

拒跑时直接把正确顺序打出来（先重放 012 → 再跑本脚本 → 最后 `pnpm install --lockfile-only`），
而不是留一句"找不到 @activepieces/piece-slack"让人自己猜。

**三个场景实测**（scratchpad 里造的模拟 rebase fixture，验完即删）：

| 场景 | 结果 |
|---|---|
| 012 未重放（api 仍依赖 slack + 控制器还在） | 拒跑，exit 1，两条问题都列出；`--check` 同样拒 |
| 重放 012 后（去依赖 + 删控制器） | 正常删掉 slack，exit 0 |
| 收敛后 `--check` | OK，exit 0 |

> fixture 里没有 web 的两个 tsconfig，于是打出了 `WARN: … 不存在，跳过（上游布局变了？）` ——
> 这正是设计意图：**上游改布局要吵，不能静默跳过**。

#### 顺带修掉一个真实存量缺陷

写断言时发现旧的映射清理器只认**三行写法**（且写死 6 空格缩进 + 必须有尾逗号），而上游把短名字的件
格式化成了**单行**：

```json
"@activepieces/piece-ai": ["packages/pieces/community/ai/src/index.ts"],
```

结果是删 `piece-ai` 那次脚本报"摘掉 0 条"，我误以为已清理干净——**实际它压根没看见**。
`a2194c06` 提交时树里还留着 **8 条**指向已删目录的悬空映射：
`piece-box` `piece-dub` `piece-exa` `piece-mcp` `piece-mem` `piece-rss` `piece-zoo` `piece-ai`。

已在本次一并修掉：清理器现在两种写法都认，并会把摘掉最后一条后产生的悬空逗号补正
（`],` → `]`），写回前用 `JSON.parse` 校验，解析不过就拒写。复扫两种写法均**零残留**，
三个 tsconfig 全部合法，`turbo build` 9/9 绿。

> 这 8 条不影响编译（没有任何代码 import 它们，所以构建一直是绿的），
> 但它正是"脚本是唯一重放机制"这个承诺的反例——静默漏掉的东西，下一轮 rebase 会原样再漏一次。

**症状**：rebase 到新上游 tag 后，上游的 `app.ts` 和 `api/package.json` 会重新 import
`slack` / `square` / `facebook-leads` / `intercom`，而 trim 脚本照样把这 4 个目录删掉
→ **树直接编译不过**，报错是"找不到 `@activepieces/piece-slack`"，完全指不到真正原因
（忘了先补 HERMES-PATCH-012）。

**修法**：脚本在删之前先检查 `packages/server/api/package.json` 是否还依赖这 4 个件、
`app.ts` 是否还 import `appEventRoutingModule`；命中就 `process.exit(1)` 并直接告诉施工者
「先重放 HERMES-PATCH-012，再跑本脚本」。十几行，一次写好永久受用。

### VT-05 app-events 死链已 fail-loud ✅ 已完成（2026-07-29）

- [x] `handleAppWebhookTrigger()` 改为**启用即抛** `ErrorCode.FEATURE_DISABLED`
      （[flow-trigger-side-effect.ts:150](../../activepieces/packages/server/api/src/app/trigger/trigger-source/flow-trigger-side-effect.ts:150)）
- [x] 专属单测 3 例，已登记进 [HERMES_PATCHES.md 的回归网](HERMES_PATCHES.md#回归网)

**为什么选在启用时炸，而不是在生成 URL 处**：`handleAppWebhookTrigger` 是唯一同时拿得到
`flowId` 和 `pieceName` 的地方，报错能指名道姓：

> `Piece "@activepieces/piece-x" (flow abc) uses TriggerStrategy.APP_WEBHOOK … removed by
> HERMES-PATCH-012. Either drop that piece from hermes/pieces.json, or revert 012 …`

而 `webhook-url.ts` 只是个纯字符串拼接工具，在那里抛会波及它的既有测试，且拿不到足够上下文
——报错会退化成"另一种说法的 404"。

**原先写 `app_event_routing` 行的逻辑一并删掉**：既然到不了那一步，留着只会让人误以为它还有效。

**disable 路径刻意不拦**，并有专门用例守着：012 之前建过监听器的项目必须还能清掉存量行，
在 disable 上抛错会留下永远删不掉的孤儿行。

实测：单测 10/10 通过；`turbo build --filter=api --filter=worker` 7/7；
eslint 对该文件 3 条 warning **与 HEAD 逐条相同**（只有行号从 216 挪到 219），零新增。

**现状**：整条链完好无损，只有尽头的门被摘了——
- [`webhook-url.ts:9`](../../activepieces/packages/server/worker/src/lib/execute/utils/webhook-url.ts:9) 仍在生成 `/v1/app-events/<appName>`
- [`flow-trigger-side-effect.ts:143`](../../activepieces/packages/server/api/src/app/trigger/trigger-source/flow-trigger-side-effect.ts:143) 仍在写 `app_event_routing` 行
- `AppEventRoutingEntity` 仍注册在 [`database-connection.ts:58`](../../activepieces/packages/server/api/src/app/database/database-connection.ts:58)

**触发条件**：哪天有人把一个 `TriggerStrategy.APP_WEBHOOK` 的件加进 `pieces.json` 白名单。
用户会拿到一个**必然 404 且毫无线索**的 webhook URL——链路上没有任何一处会说"端点被我们摘了"。

这些代码**不能删**（`APP_WEBHOOK` 分支是与具体 piece 无关的通用逻辑，删了编译不过），
所以修法是在 `webhook-url.ts` 或白名单校验处加一条显式的「本部署不支持 APP_WEBHOOK 策略」断言。

### VT-06 `--check` 接进 CI ✅ 已完成（2026-07-29）

- [x] 新增 [`.github/workflows/vendor-trim-check.yml`](../../.github/workflows/vendor-trim-check.yml)
      （风格对齐仓库既有的 `ai-guidance-sync.yml`；触发条件 `activepieces/**`，只读文件系统，秒级）

**先把 `--check` 补成完整不变量再接进去**，否则接了也白接 —— 它原先只查 piece 收敛，
查不出 VT-04 修掉的那类死映射，正是同一个坑会二次漏过的地方。现在一次调用查三条：

1. `community/` 只剩 `KEEP` 里那几个件；
2. 三个 tsconfig 里没有指向已删目录的 path 映射（**单行与三行两种写法都查**）；
3. 没有任何 workspace manifest 还依赖待删件 —— 即 VT-04 那条重放顺序断言。

job 里还加了第二步 **`pnpm install --frozen-lockfile --lockfile-only`**：
`--check` 只保证"树是收敛的"，不保证锁文件跟着走。少了这步，一次漏跑
`pnpm install --lockfile-only` 就会让镜像构建阶段的 `--frozen-lockfile` 失败，
而那要等到构建才暴露 —— 这个 job 秒级就能挡下。

实测：
- `--check` 正向通过，且**确认是只读的**（跑完 `git status` 对三个 tsconfig 为空）
- 负向注入一条指向已删目录的映射 → `FAIL: 1 条指向已删目录的 tsconfig path 映射`，exit 1
- 两个 workflow 的 YAML 都能解析；`packageManager: pnpm@9.15.9` 已在
  `activepieces/package.json` 里声明，`corepack enable` 能拿到正确版本
- 本地模拟第二步：`Scope: all 43 workspace projects`，497ms

### VT-07 `SUPPORTED_APP_WEBHOOKS` flag 会说谎

- [ ] 核对各环境 `APP_WEBHOOK_SECRETS` 的实际取值，决定是清空还是让 flag 恒空

[`flag.service.ts:340`](../../activepieces/packages/server/api/src/app/flags/flag.service.ts:340) 的
`getSupportedAppWebhooks()` 是从 `APP_WEBHOOK_SECRETS` 环境变量算出来的，**与那 4 个件无关**。
如果该变量里还配着 slack / square，前端仍会收到「支持这些 app webhook」的 flag，而端点已经没了。

> ⚠️ 别顺手把变量删空：[`machine-service.ts:42`](../../activepieces/packages/server/api/src/app/workers/machine/machine-service.ts:42)
> 对它是 `getOrThrow`，清掉会让 worker machine 配置直接抛。

---

## P2 — 治理面（不阻塞，但会慢慢发霉）

### VT-08 crowdin 同步已关停 ✅ HERMES-PATCH-014（2026-07-29）

**查下去发现问题不是"数量塌缩"，而是这个配置指向公网 SaaS，而 fork 跑它两个方向都是错的。**
所以没有去调 glob 让数字好看，而是把两个入口堵死：

- [x] [`package.json`](../../activepieces/package.json) 的 `pull-i18n` / `push-i18n` 改为 fail-loud 拒跑（实测两条均 exit 1）
- [x] [`crowdin.yml`](../../activepieces/crowdin.yml) 头部写明理由；文件保留以维持 vendor diff 干净

| 方向 | 跑了会怎样 |
|---|---|
| `push-i18n`（`crowdin upload sources`） | 拿**本仓库的**源串改写**上游 Activepieces** 的 Crowdin 项目。013 之后 source 匹配只剩 **29** 个（裁剪前约 700）—— 一次上传在上游项目里等同于批量删除源串 |
| `pull-i18n`（`crowdin pull`） | 把上游最新译文灌进 [Q8](DECISIONS.md#q8) 冻结基线，静默改掉 vendored i18n |

两者都需要 `CROWDIN_PERSONAL_TOKEN` 且目标是 `api.crowdin.com`，气隙部署下既不需要也不该发生。

> **自研件不在 source 匹配里，不会外泄**：`biz-calendar` / `hash-helper` 只有手写的
> `src/i18n/zh.json`，**没有 `translation.json`** —— 29 个 source 全部是上游件。
> 这一条是查出来的，不是假设：先前担心的"in-house 串被推到上游项目"并不成立。

### VT-09 上游 piece 源码的取回约定 ✅ 已完成（2026-07-29）

- [x] [PIECE_DEVELOPMENT_HOWTO.md](PIECE_DEVELOPMENT_HOWTO.md) 新增 §10「需要读或改**上游** piece 的源码时」，
      §9 的坑表补两行

**立项时的判断偏悲观了：源码根本没丢，也不需要"重新 vendor"。** 冻结基线 commit
`de4f6469` 里 692 个件全在，一条命令就取得回来：

```bash
git show de4f6469:activepieces/packages/pieces/community/<name>/src/index.ts   # 只读一眼
git checkout de4f6469 -- activepieces/packages/pieces/community/<name>        # 取回整个件
```

**不受影响的**：自研件流程完好——`community/<name>/` 目录仍在，样例件 `biz-calendar` 在保留清单里，
文档里的路径全部仍然成立（已逐条核过）。加白名单件也不需要源码（走 npm 版本号解析）。
**受影响的**只有"照着上游某个件抄写法"和"对上游件打源码补丁"这两件事。

文档里写清了取回之后的二选一：**只是参考**就看完删掉；**要长期保留**则必须走四步
（`KEEP` 加条目并写理由 → `pnpm install --lockfile-only` → 需要时登记 HERMES-PATCH → `--check` 自检），
并点明**忘了第一步会被 VT-06 的 CI job 挡下，那是设计好的**。

### VT-10 codegraph 索引 ✅ 已自愈（2026-07-29 复查）

- [x] 无需手工重建 —— daemon 的文件监听在删除发生时就把符号摘掉了

原先担心"索引里还留着 690 个已删件，符号搜索会返回幽灵结果"。实测**没有幽灵**：

| 探针 | 结果 |
|---|---|
| `zuora` | 零命中 |
| `salesforce` | 仅 1 条，`web/src/features/authentication/…/auth-animation.tsx`（活文件里的字符串常量） |
| `runAgent` | 仅 1 条，`web/src/app/builder/test-step/agent-test-step/index.tsx`（活文件；`community/ai` 的符号已消失） |

> 结论：codegraph daemon 当时正在运行（`.codegraph/daemon.pid` 早于裁剪），删除被实时消费掉了。
> **前提是删除发生时 daemon 在跑**——若将来在 daemon 停止期间做同等规模的删除，这条得重新验。

---

## P0/P1 — 原问题（**尚未闭合**）

### VT-11 拿到公司机器的报错原文 ⬅ **先做这个**

- [ ] 取得 `ERR_PNPM_*` 代码或 HTTP 状态码

**整轮裁剪是在根因未确认的情况下做的。** 已排除的一个嫌疑：`.npmrc` 里
`//registry.npmjs.org/:_authToken=${NPM_TOKEN}` 的 `NPM_TOKEN` 未设**只是 WARN 不是 error**
（本机未设，照样装通）。

> 2026-07-29 更新：这条嫌疑已彻底消除 —— 该 token 行连同 `@activepieces:registry` 公网 pin
> **已从 `activepieces/.npmrc` 删除**（理由见该文件顶部 HERMES-PATCH 注释）。补充实测：token 未设时
> pnpm 9.15.9 虽只 WARN，但会**丢弃整个 `.npmrc`**，所以那两行此前在任何路径（含 AP 镜像构建）
> 都是不生效的 —— 既不会导致 install 失败，也从未真的把 scope 顶到公网。删除后 registry 完全跟随
> 机器默认配置（公司私服可直接代理）。同期公司机器报的 `Activepieces workspace deps are missing`
> 与本条无关：那是 `build-and-push-k8s.ps1` 在跑 pnpm 之前的目录存在性检查（AP workspace 从未装依赖）。

剩下两种可能，解法完全不同：

| 若报错是 | 含义 | 那么 |
|---|---|---|
| 网络超时 / 代理未配 / ECONNREFUSED | 环境问题 | 本次裁剪顺带消灭了几百个包的失败面，大概率已够 |
| registry 按名字策略封禁 AI 包 | 政策问题 | **本轮白做**，见 VT-12 |

### VT-12 `@ai-sdk/*` 仍在 api / worker / engine 硬依赖

- [ ] 阻塞于 VT-11。若确认是策略封禁，则需要公司加白、搭内网 registry 镜像，或按下表逐层摘

> ⚠️ **常见误解：换用 HTTP piece 并没有解决这一条。** HTTP piece 换掉的是 flow 里的那一步，
> 不是 AP 服务端的 AI provider 层。`@ai-sdk/*` 的使用方与 `piece-ai` 无关：

| 使用方 | 规模 | 摘除难度 |
|---|---|---|
| `api/src/app/ai/` —— `ai_provider` 实体 + 控制器 + 8 个 provider 适配器（anthropic / openai / bedrock / azure / google / openrouter / cloudflare / openai-compatible） | **14 个文件** | **中**：迁走 piece-ai 之后这层大概率已无人使用（provider 配置本就是给 run_agent 走代理用的，HTTP piece 自带 URL 和 key）。动手前需确认没有别的模块引用 `ai_provider` 表 |
| `engine/src/lib/handler/piece-executor.ts`、`engine/src/lib/tools/index.ts` | 2 个文件 | **高**：引擎核心的 piece tool-calling，除非确认没有任何 piece 用它 |
| `worker/.../ee/chat/*`、`server/utils/chat-ai-utils.ts`、`web/src/features/chat/*` | 7 个文件 | 中：chat / agent 链路 |

只摘第一层**不能**让 `@ai-sdk/*` 从 install 里消失（engine 那两处仍在）。
所以若确认是策略封禁，这条的现实解法多半在公司侧（加白 / 内网镜像），而不在代码侧。

### VT-13 `piece-ai` ✅ 已删除（2026-07-28）

前提由用户确认：**AI Generate 已改用 HTTP piece 直连模型端点**，`piece-ai` 的 `run_agent`
链路不再被使用。`@activepieces/piece-http@0.11.10` 早已在白名单里（[pieces.json:7](../../activepieces/hermes/pieces.json:7)）
且 tarball 齐备，气隙下跑得起来。于是连带完成：

- [x] 删 `packages/pieces/community/ai`，`KEEP` 从 5 个降到 4 个
- [x] 删 `hermes/patch-piece-ai-run-agent.js`（HERMES-PATCH-002 随之作废）
- [x] 摘掉 `web/tsconfig.app.json` / `tsconfig.spec.json` 里指向该目录的悬空 path 映射
      （**这两处不在 trim 脚本的清理范围内**——脚本只管 `tsconfig.base.json`，是个盲区）
- [x] 台账 002 标作废、013 改 4 个件；`FR-K02` 标作废、`FR-K01` 改写为 HTTP piece
- [x] 复验：`pnpm install --frozen-lockfile`（**43** 个工作区项目）+ `turbo build` 9/9 绿

> **收益要说清楚：零安装量。** `@ai-sdk/*` 在 lock 里仍有 238 处引用，因为它们由
> `server/{api,worker,engine}` 本身硬依赖——删 piece-ai 是政策口径上的"树里不留 AI 件"，
> 不是清依赖。真要清依赖看 VT-12。

---

## VT-15 AI Generate：功能已停用，产物已清理 ✅

**2026-07-28 用户裁决：先停用整个 AI Generate，之后自行处理。** 已落地的停用（默认即生效，
各环境不需要加任何环境变量）：

- [x] 后端 [`AiGenerationController`](../../backend/developer-workstation/src/main/java/com/developer/controller/AiGenerationController.java)
      加 `@ConditionalOnProperty(prefix="ai-generation", name="enabled", havingValue="true")`，
      [`application.yml`](../../backend/developer-workstation/src/main/resources/application.yml) 新增
      `ai-generation.enabled: ${AI_GENERATION_ENABLED:false}` → 停用期间 `/ai-generation/**` 全部 404
- [x] 前端 [`utils/featureFlags.ts`](../../frontend/developer-workstation/src/utils/featureFlags.ts)
      新增 `AI_GENERATION_ENABLED = false`，[`FunctionUnitEdit.vue`](../../frontend/developer-workstation/src/views/function-unit/FunctionUnitEdit.vue:37)
      的入口按钮与 `<AiPanel>` 都加 `v-if` → 组件不创建，`api/aiGeneration.ts` 的请求一个都发不出去
- [x] [`build-ai-fu-flow.js`](../../deploy/scripts/build-ai-fu-flow.js) 加 fail-loud 闸门，
      默认拒跑（要跑须显式 `--i-know-this-is-obsolete`）
- [x] 复验：DW 后端 `mvn compile` BUILD SUCCESS（**JDK17**）；前端 `vue-tsc` 对这两个文件零报错
      （全仓 116 个 TS error 全部是存量，无一在改动文件里）

> **恢复时必须两侧同开**：只开后端 = 看不到入口；只开前端 = 点进去全 404。两处注释里都写了这句。
> 会话 / 文档 / 锁的历史数据都还在库里，业务逻辑没动，恢复不需要数据迁移。

### 产物清理 ✅（2026-07-29）

**当初留着它们是因为"三段 system prompt 是仅存的一份"。这个顾虑在 VT-09 里已经被推翻过一次：
源码不在工作区不等于丢了，git 里全在。** 所以三个产物全部删除，取回方式写在下面。

删除的直接理由比"陈旧"严重得多 —— **`ai-function-unit-gen.json` 是 CI 流水线里的活雷**：
[`Jenkinsfile.ap-flows-publish`](../../deploy/ci/Jenkinsfile.ap-flows-publish:67) 的 `FLOWS` 默认值是
`all`，而 `all` 就是 `ls deploy/ap-flows/*.json`。只要它还躺在那个目录里，**任何一次常规的
"发布全部 flow" 都会把 piece-ai 版的 flow 推进生产 AP** —— 而 piece-ai 既不在白名单、
也已不在 vendor 树里，推上去必然哑火。

- [x] 删 `deploy/ap-flows/ai-function-unit-gen.json`（拆掉上面那颗雷）
- [x] 删 `deploy/pieces/AI Function Unit Generation.json`（BOM 前缀的编辑器导出副本）
- [x] 删 `deploy/scripts/build-ai-fu-flow.js`（重建脚本；此前只加了闸门，现在整个删掉）
- [x] 连带修掉 5 处引用，复扫零悬空：`ap-import-to-id.js` 的用法示例、uat/preprod 两份
      configmap 的注释、`docs/x-ray/architecture/ai-and-integrations.md` 三处、`REQUIREMENTS.md` 的 FR-K01

**三段 prompt 的取回方式**（与 [VT-09](#vt-09-上游-piece-源码的取回约定--已完成2026-07-29) 同一套路）：

```bash
git show 6436f537:deploy/scripts/build-ai-fu-flow.js        # 三段 phase prompt 都在里面
git show 6436f537:deploy/ap-flows/ai-function-unit-gen.json # flow 定义本体
```

> uat/preprod 的 `__AI_GEN_FLOW_ID__` **有意保留**：占位符不替换也不影响部署（configmap 脚本
> 只校验 `__BASE_DOMAIN__` / `__INGRESS_HOST__` 两个），功能关着就没有东西会去调它。
> 注释里写清了恢复时的顺序。

### 原先记录的其余项（保留作参照）

- [x] ~~这几处 piece-ai 版产物没动~~ —— 已于 2026-07-29 全部删除，见上：

| 文件 | 危险度 |
|---|---|
| [`deploy/scripts/build-ai-fu-flow.js:460`](../../deploy/scripts/build-ai-fu-flow.js:460) | **最高——它是"重建这个 flow"的脚本**。谁再跑一次，就会生成一个基于 `piece-ai` 的 flow，而 `piece-ai` 既不在白名单也已不在 vendor 树里 → 气隙环境直接哑火 |
| [`deploy/ap-flows/ai-function-unit-gen.json:85`](../../deploy/ap-flows/ai-function-unit-gen.json:85) | 高——uat/preprod 的 configmap 注释指名要"把这个文件经 Jenkins 导入本环境 AP"再回填 `__AI_GEN_FLOW_ID__` |
| `deploy/pieces/AI Function Unit Generation.json` | 中——导出件 |
| [`docs/x-ray/architecture/ai-and-integrations.md:41,115`](../../docs/x-ray/architecture/ai-and-integrations.md:41) | 中——架构描述仍写着 `piece-ai run_agent (provider=custom, model=deepseek-v4-pro)` |

- [ ] 停用后这些接线全部变成惰性配置，**没有清理，也不影响运行**：
      [`AiGenerationServiceImpl.java:66`](../../backend/developer-workstation/src/main/java/com/developer/service/impl/AiGenerationServiceImpl.java:66)
      的 webhook 地址、uat/preprod 两份 configmap 的 `__AI_GEN_FLOW_ID__`、dev
      `docker-compose.dev.yml:533`、以及 AP networkpolicy 里那条 LLM egress 规则
      （**这条现在开着一个没人用的 443 出口，重做时按新链路重新定位**）。
- [ ] DW 后端 `ai-function-unit-generation` 那一整套 property 测试仍然活着（未跑，控制器测试是
      standalone MockMvc + `@InjectMocks`，不加载 Spring 上下文，故不受 `@ConditionalOnProperty` 影响）。
- [ ] **UI 截图验证未做**（仓库规则 `/verify-ui`）：改动是隐藏一个按钮，需要构建前端 + 重建
      Docker 服务 + Playwright 截图才能验，成本不低，故留给恢复时一并做。

---

## 提交面

### VT-14 独立成 commit ✅ 已完成（2026-07-28）

- [x] `a2194c06 build(ap): trim vendored community pieces to 4; disable AI Generate`
      —— **17920 files changed, 1002 insertions(+), 1354004 deletions(-)**

担心的那摊前端改动确实分开了，各自成 commit：`4416e951 build(frontend): move the frontends from
npm to pnpm` + `3da0e767 build(deploy): finish the pnpm switch in the two build scripts`。

> **一处如实说明**：`a2194c06` 并不是"纯删除"commit——它同时带上了 VT-15 的 AI Generate 停用
> （DW 后端 `@ConditionalOnProperty` + 前端 feature flag + `build-ai-fu-flow.js` 闸门）和本 checklist 本身。
> 这是刻意的：停用 AI Generate 是**删 `piece-ai` 的直接后果**，拆开会让任一半单独 checkout 时处于不自洽状态
> （树里没有 `piece-ai`，功能却还开着并指向它）。review 时按 `docs/` 与 `backend/`+`frontend/`
> 两组路径过滤即可绕开那 17876 个删除。

---

## 已完成（存档，不必再做）

| 项 | 证据 |
|---|---|
| `pnpm install --frozen-lockfile` | ✅ workspace 从 725 → **44** 个项目 |
| `turbo run build`（web / engine / api / worker） | ✅ 9/9 |
| `turbo run lint --filter=api` | ⚠️ 85 errors **全为上游存量**；`app.ts` 上那 3 条是 `setPlatformOAuthService` / `flagHooks` / `exceptionHandler` 未使用，与本次无关；grep `app-event` 零命中 |
| `turbo run test`（engine / shared / web） | ⚠️ 1 red：`codeExecutor > should execute code that requires an npm package successfully` |
| `pnpm-lock.yaml` | ✅ 净删 18156 行，**零版本漂移**（111 处 `+` 是 `community/ai` 的块换了位置） |
| HERMES-PATCH 标记盘点 | ✅ grep 出 001–013 连号无缺 |

> **那条 red 不是本次改坏的**：fixture 目录
> `packages/server/engine/test/resources/codes/flowVersionId/hello_world_npm/` 的 `.gitignore`
> 特意 un-ignore 了 `node_modules/**`（注释写着要把 mock 包提交进来），但
> `git log --diff-filter=A` 显示该目录自 `de4f6469 vendor(ap): pristine 0.84.0 baseline`
> 起就只进过 `.gitignore` 和 `index.js` 两个文件——被 `require('hello-world-npm')` 的那个
> mock 包**从来没进过仓库**。这条从 vendor 导入那天起就是红的。
