# hermes/ — HERMES 对 vendor 树的构建期附加物（automation/ = 0.88 硬分叉）

这个目录**不是上游 Activepieces 的一部分**，是 HERMES 加进来的构建期物料。
本份是 `automation/`（0.88 树）的版本：安装逻辑已从 `packages/server/worker` 迁到
`packages/server/sandbox`，缓存版本是 `v13`，piece 分发改为「bundle.tgz 本地 tarball」
（上游 ADR 0002/0006）——与 0.84 `activepieces/hermes/` 的差异见下。

> **0.84 树（仓库根 `activepieces/`）已于 2026-08-14 删除**，本文提到它只作对照。
> 需要当年的 `hermes/` 内容走 git 历史：`git show 4635f7950:activepieces/hermes/<file>`。

> **2026-07-30（[D12](../../docs/ap-integration/DECISIONS.md#d12)）**：AP 子树已转为**硬分叉 + 深度裁剪**，
> `automation/` 视为 HERMES 自有源码，**不再有 rebase 到新上游 tag 这件事**。
> 原先"rebase 时本目录整体保留、逐个复核假设"的说明随之作废。

| 文件 | 角色 | 挂载点 |
|---|---|---|
| `pieces.json` | **piece 白名单**（`name` + `version`，自研件另加 `tarball`）——手改的唯一入口 | 下面两处共用 |
| `prewarm-pieces.sh` | 构建期把白名单里的 piece 按 sandbox `piece-installer.ts` 的原样布局（含每件的 `bundle.tgz`）装进 `cache/v13/common` 并写 `ready`，使运行时安装**和 bundle 端点下载**都成为 no-op（气隙必需，X-3 / FR-A09 / FR-A10） | `../Dockerfile` run 阶段最后一步 |
| `tarballs/*.tgz` | npm 包留档（审计 / 内网发布源）；**声明了 `tarball` 的自研件直接从这里装**——它们不在任何公共 registry 上 | 同上 |
| `check-tsconfig-paths.mjs` | 断言三个 tsconfig 的 `paths` 映射都指向存在的文件（悬空映射会让 import 解析到空，tsc 报错的位置离病因很远）| 不在构建里，CI `vendor-trim-check.yml` 跑 |
| `pieces.e2e-fixtures.json` | **CI 专用**的第二份清单：`test/integration/ce` 钉住的四个旧版 piece（webhook@0.1.29 / subflows@0.4.11 / data-mapper@0.3.15 / delay@0.3.26），全走 registry、无 `tarball` 字段 | 不在镜像里（已 `.dockerignore`），CI `ap-api-tests.yml` 用同一个 `prewarm-pieces.sh` 跑 |

> `pieces.e2e-fixtures.json` **不是白名单**，别拿它 prewarm 镜像：它钉的版本比 `pieces.json`
> 旧，是集成测试的固定夹具。两份清单在镜像里并存只会诱发"prewarm 错文件"，所以它被
> `.dockerignore` 挡在构建上下文之外。
>
> **跑 prewarm 的 pnpm 必须和运行时安装器的 pnpm 同版本**（`package.json` 的 `packageManager`）。
> 不一致时，运行时第一次 `--filter` 安装会把已 prewarm 成员的 `node_modules` 清空、却留下
> `ready` 标记，而 `pieceCheckIfAlreadyInstalled` 只看目录在不在 —— 于是永远不重装，
> 一路 `PieceNotFound`。镜像里两者天然同版本；CI 里靠 `corepack enable` + 一条版本断言保证。
> 事故复盘见 [TRIM_LOG.md](TRIM_LOG.md) 2026-08-15。

> **`trim-vendor-pieces.mjs` 已于 2026-08-07 删除（[D13](../../docs/ap-integration/DECISIONS.md#d13)）**。
> 它做两件事，只有一件还有理由存在：*执行*裁剪（写成可重放脚本 + `--check`，为的是 rebase 之后
> 逐条重放 —— D12 判定 rebase 不会发生），和*断言* tsconfig 没有悬空映射（与上游无关，已单独拆成
> `check-tsconfig-paths.mjs`）。它第三条不变量「community/ 只许有白名单里的件」**故意没有保留**：
> community/ 正是自研件的所在地，那条检查等于给它本该保护的流程收税——每加一个自研件都得先去脚本的
> KEEP 里登记。裁剪结果现在就是树的状态：`packages/pieces/{core,custom}` 已整体删除，
> `community/` 留 4 个（`biz-calendar` / `hash-helper` 自研件 + 白名单件 `json` / `postgres` 的源码）。

> `patch-piece-ai-run-agent.js`（HERMES-PATCH-002）已于 2026-07-28 删除：AI Generate 改用
> HTTP piece 直连模型端点，`piece-ai` 的 `run_agent` 链路作废，补丁没有可打的对象了。

白名单条目两种形态：

```json
{ "name": "@activepieces/piece-csv", "version": "0.4.15" }
{ "name": "@activepieces/piece-hash-helper", "version": "1.0.0",
  "tarball": "activepieces-piece-hash-helper-1.0.0.tgz" }
```

0.88 里两种形态最终落盘完全一致：每件都是 `pieces/<name>-<ver>/bundle.tgz` + 指向它绝对路径的
`package.json`（运行时 installer 对 REGISTRY / ARCHIVE 一视同仁，都从 API 的
`/v1/engine/pieces/bundle` 端点下成 tarball 再装）。区别只在构建期 tarball 从哪来：
前者构建机从 `registry.npmjs.org/<name>/-/<basename>-<ver>.tgz` 下载（即 bundle 端点
307 重定向的同一来源）；后者从本目录 `tarballs/` 拷贝——自研件不在任何公共 registry 上。
声明了却找不到文件即 fail-loud。

## 白名单的两半

`pieces.json` 同时决定两件事，**必须同步投放，版本必须一致**：

- **运行时半**（可执行 npm 包）→ 本目录 `prewarm-pieces.sh` 烘进镜像；
- **设计器半**（`piece_metadata` 表行）→ `deploy/pieces/generate-metadata-seed.js` 读**同一个文件**
  生成 `deploy/pieces/metadata/pieces-seed.sql`，由各环境对共享库执行。

改白名单的完整流程见 [`deploy/pieces/README.md`](../../deploy/pieces/README.md)
与 [`docs/ap-integration/PIECE_DEVELOPMENT_HOWTO.md`](../../docs/ap-integration/PIECE_DEVELOPMENT_HOWTO.md)。

## 升级上游时必查

- `cache/v13` 里的 `v13` 是 `packages/server/sandbox/src/lib/cache/cache-paths.ts` 的
  `LATEST_CACHE_VERSION`；变了就要改 `prewarm-pieces.sh`。
- prewarm 写的 `package.json` / `pnpm-workspace.yaml` / `.npmrc` 与 `pkgRunner().install()`
  的命令行是**逐字复刻** `piece-installer.ts`；上游改布局，运行时会重装（联网环境静默变慢，
  气隙环境直接 `PieceNotFound`）。
- **piece 源码树已收敛到 6 个包**：`framework` / `common` + `community/` 下 4 个
  （`biz-calendar` / `hash-helper` 自研件，`json` / `postgres` 是白名单件的源码）。
  上游的 694 个 community 件与整个 `packages/pieces/{core,custom}` 都已从树上删除
  （最后一步 2026-08-07，[D13](../../docs/ap-integration/DECISIONS.md#d13)）；
  需要读被删的上游源码时从 `de4f6469` 取回（VT-09 约定）。三步的动机不同，别合并理解：
  - **011** 先删 8 个带厂商 AI SDK 的件（`@anthropic-ai/sdk` / `openai` / `@google/genai` /
    `@google/generative-ai` / `@huggingface/*`）——起因是这些包在受限内网装不下来。
  - **012** 摘掉 `/v1/app-events/:pieceUrl` 这个 `securityAccess.public()` 端点，
    api 对 `slack` / `square` / `facebook-leads` / `intercom` 的最后 4 个 import 随之消失。
  - **013** 推广到全量：**按名字或按依赖过滤都不可靠**——47 个 `*-ai` 件用 `httpClient` 直连
    模型 API，不带任何 SDK 依赖，011 的筛法一个都抓不到。所以改用白名单，其余全删。
  - **2026-07-28 追删 `community/ai`**：AI Generate 已改用 HTTP piece（已在白名单）直连模型
    端点，`piece-ai` 的 run_agent 链路作废，连同 HERMES-PATCH-002 一起删除。
    注意这**不省任何安装量**——`@ai-sdk/*` 由 `server/{api,worker,engine}` 本身硬依赖
    （lock 里仍有 238 处引用），删 piece-ai 是政策口径上的"树里不留 AI 件"，不是清依赖。
