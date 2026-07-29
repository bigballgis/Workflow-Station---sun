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
| VT-04 | rebase 重放顺序陷阱（脚本加断言） | P1 | ✅ **已完成（2026-07-29）** |
| VT-05 | app-events 死链无提示 | P1 | ✅ **已完成（2026-07-29）** |
| VT-06 | `--check` 接进 CI | P1 | ✅ **已完成（2026-07-29）** |
| VT-07 | `SUPPORTED_APP_WEBHOOKS` flag 说谎 | P1 | ⬜ 未做 |
| VT-08 | crowdin 翻译源塌缩 | P2 | ⬜ 待裁决 |
| VT-09 | 上游 piece 源码不再可读的补救约定 | P2 | ⬜ 待裁决 |
| VT-10 | codegraph 索引重建 | P2 | ✅ **已自愈（2026-07-29 复查）** |
| VT-11 | **公司机器报错原文**（VT-12 的前置） | **P0** | ⬜ 未取得 |
| VT-12 | `@ai-sdk/*` 仍在 api/worker/engine 硬依赖 | P1 | ⬜ 阻塞于 VT-11（**未被 HTTP piece 迁移解决**） |
| VT-13 | `piece-ai` 保留与否的政策裁决 | P1 | ✅ **已关闭（2026-07-28，已删除）** |
| VT-15 | AI Generate 产物与功能开关 | **P0** | 🟡 **功能已停用（2026-07-28），产物待用户后续处理** |
| VT-14 | 17876 个删除独立成 commit | P1 | ✅ **已完成（`a2194c06`）** |

> ⚠️ **VT-11 是整件事的根因位**。VT-01～VT-10 都是这次改动自身的收尾，值得做；
> 但"公司装不上"这个原始问题是否已解决，在 VT-11 之前**没有任何证据**。
> 现在 VT-01/03/10/13/14 全部闭合，这一点反而更刺眼：**自身收尾快做完了，原问题一步没动。**

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

### VT-08 crowdin 翻译源塌缩

- [ ] 裁决：调整 [`crowdin.yml:11`](../../activepieces/crowdin.yml:11) 的 glob，还是接受现状

`packages/pieces/**/**/src/i18n/translation.json` 现在只剩 4 个 community 件（加 `core/*`）的翻译源。
如果还有人跑 crowdin 同步，行为会与裁剪前显著不同。

### VT-09 上游 piece 源码不再本地可读

- [ ] 裁决并写进 [PIECE_DEVELOPMENT_HOWTO.md](PIECE_DEVELOPMENT_HOWTO.md)：
      将来要对某个白名单件做**源码级**补丁时，先把该件重新 vendor 回来的操作约定

**不受影响的**：自研件流程完好——`community/<name>/` 目录仍在，样例件 `biz-calendar` 在保留清单里，
文档里的路径全部仍然成立（已逐条核过）。加白名单件也不需要源码（走 npm 版本号解析）。
**受影响的**只有"照着上游某个件抄写法"和"对上游件打源码补丁"这两件事。

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
（本机未设，照样装通）。剩下两种可能，解法完全不同：

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

## VT-15 AI Generate：功能已停用，产物待处理 🟡

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

### 剩下的（用户自行处理时再定）

- [ ] 这几处 piece-ai 版产物没动，因为**里面那三段 system prompt 是仅存的一份**
      （旧 n8n 模板已随 n8n 删除，Java 侧无副本）——重做 AI Generate 时要从这里取：

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
