# hermes/ — HERMES 对 vendor 树的构建期附加物

这个目录**不是上游 Activepieces 的一部分**，是 HERMES fork（[Q8](../../docs/ap-integration/DECISIONS.md#q8)
frozen baseline + controlled fork）加进来的构建期物料。rebase 到新上游 tag 时，本目录整体保留，
逐个复核里面的假设是否还成立。

| 文件 | 角色 | 挂载点 |
|---|---|---|
| `pieces.json` | **piece 白名单**（`name` + `version`，自研件另加 `tarball`）——手改的唯一入口 | 下面两处共用 |
| `prewarm-pieces.sh` | 构建期把白名单里的 piece 按 worker `piece-installer.ts` 的原样布局装进 `cache/v11/common` 并写 `ready`，使运行时安装成为 no-op（气隙必需，X-3 / FR-F03A） | `../Dockerfile` run 阶段最后一步 |
| `tarballs/*.tgz` | npm 包留档（审计 / 内网发布源）；**声明了 `tarball` 的自研件直接从这里装**——它们不在任何公共 registry 上 | 同上 |
| `patch-piece-ai-run-agent.js` | HERMES-PATCH-002，DeepSeek reasoning 模型的 `run_agent` 修正 | **不接进 Dockerfile**（`piece-ai` 已按「气隙下 AI 件无用」移出白名单），仅联网 dev 手工执行 |

白名单条目两种形态：

```json
{ "name": "@activepieces/piece-csv", "version": "0.4.15" }
{ "name": "@activepieces/piece-hash-helper", "version": "1.0.0",
  "tarball": "activepieces-piece-hash-helper-1.0.0.tgz" }
```

前者按版本号从 registry 解析（只在构建机联网）；后者把 tarball 拷进 `pieces/<name>-<ver>/`
再以本地路径依赖安装，与 installer 的 ARCHIVE 分支同构。声明了却找不到文件即 fail-loud。

## 白名单的两半

`pieces.json` 同时决定两件事，**必须同步投放，版本必须一致**：

- **运行时半**（可执行 npm 包）→ 本目录 `prewarm-pieces.sh` 烘进镜像；
- **设计器半**（`piece_metadata` 表行）→ `deploy/pieces/generate-metadata-seed.js` 读**同一个文件**
  生成 `deploy/pieces/metadata/pieces-seed.sql`，由各环境对共享库执行。

改白名单的完整流程见 [`deploy/pieces/README.md`](../../deploy/pieces/README.md)
与 [`docs/ap-integration/PIECE_DEVELOPMENT_HOWTO.md`](../../docs/ap-integration/PIECE_DEVELOPMENT_HOWTO.md)。

## 升级上游时必查

- `cache/v11` 里的 `v11` 是 `packages/server/worker/src/lib/cache/cache-paths.ts` 的
  `LATEST_CACHE_VERSION`；变了就要改 `prewarm-pieces.sh`。
- prewarm 写的 `package.json` / `pnpm-workspace.yaml` / `.npmrc` 与 `pkgRunner().install()`
  的命令行是**逐字复刻** `piece-installer.ts`；上游改布局，运行时会重装（联网环境静默变慢，
  气隙环境直接 `PieceNotFound`）。
