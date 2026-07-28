# HERMES-PATCH 台账

[Q8](DECISIONS.md#q8) 采用 **0.84.0 frozen baseline + controlled fork**，并要求"放弃跟随上游 **≠**
放弃上游可追溯性，必须保留完整的 baseline、patch、变更原因与许可审计能力"。Q8 只画了结构：

```
AP 0.84.0 官方 Tag（frozen baseline）
   ├── HERMES-PATCH-001
   └── ...
```

本文件是那份结构的实体清单。

## 怎么用

- **加补丁**：取下一个编号，在代码里写 `HERMES-PATCH-0NN`，并在下表加一行。
- **盘点**：`grep -rn "HERMES-PATCH-0" activepieces/ deploy/` 应与下表**逐条对上**。
  对不上说明有人漏登记，以表为准去补代码标记（反过来也一样）。
- **许可审计**：下表即"我们对 MIT 上游做了哪些修改"的完整回答。
- **将来 rebase 到新 tag**：下表是逐条重放与重验的施工图。

> **⚠️ 002 是构建期改写脚本，别只 grep 源码。** 它不改 vendor 源码，而是重写已构建的产物
> （fail-loud：模式匹配不上就 `process.exit(1)`）。它现在**没有挂在任何构建里**，见下表备注。
> 001 原本也是这一类（`deploy/pieces/patch-web-approvals.js` 正则改压缩后的 web bundle），
> AP 源码 vendor 进仓库后已**改写为源码补丁**，脚本删除。

## 清单

| # | 类型 | 位置 | 内容 | 落地 | 裁决 |
|---|---|---|---|---|---|
| 001 | 源码 | `web/src/app/builder/pieces-selector/index.tsx` | 摘掉 Approvals 标签页与 `<ApprovalsTabContent>` 渲染。该标签页硬编码 6 个 SaaS piece，全部加载成功才渲染；白名单下 6 个全 404 → 永久骨架屏。**不渲染**而非仅隐藏标签：组件在 tab 判断之前就发这 6 个查询 | 2026-07-02 `821cf33c`；2026-07-27 由构建期脚本改为源码补丁 | [Q9](DECISIONS.md#q9) |
| 002 | 构建期（**仅联网环境手工执行**） | `activepieces/hermes/patch-piece-ai-run-agent.js` | `piece-ai` 的 `run_agent`：补 `maxOutputTokens`（DeepSeek 把 reasoning token 计入预算，默认额度会让文档还没输出就耗尽）+ reasoning-delta 不再拼进输出。**不接进 Dockerfile 是裁决，不是欠账**：`piece-ai` 在 `669f7207` 已按「气隙下 AI 件无用」移出白名单，镜像里没有可打的副本。dev 里该 piece 仍由运行时联网安装，需要时按文件头的 `docker cp` + `docker exec` 手工打 | 2026-07-16 `e5da4738`；2026-07-27 随预烘焙层迁入 vendor 树 | — |
| 003 | 源码 | `api/src/app/pieces/community-piece-module.ts` | 开放 piece 删除端点给 Admin Center | 2026-07-26 `277f15ae` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 004 | 源码 | `api/src/app/pieces/metadata/utils/index.ts` | 恢复最小 platform 级 piece 可见性 | 2026-07-26 `277f15ae` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 005 | 源码 | `worker/src/lib/cache/code/pkg-runner.ts` | 气隙运行时 piece 安装（pnpm offline store） | 2026-07-26 `3811061d` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 006 | 源码 | `worker/src/lib/egress/lifecycle.ts` | `AP_SSRF_ALLOW_LIST` 接受主机名，不再只认 IP / CIDR | 2026-07-27 `d6ec1e33` | [D6](DECISIONS.md#d6) |
| 007 | 源码 | `engine/src/lib/operations/sync-webhook-release.ts` | run 进终态即释放 sync webhook 监听器（步骤内失败） | 2026-07-27 `6a50f83c` | [D10](DECISIONS.md#d10) |
| 008 | 源码 | `worker/src/lib/execute/jobs/execute-flow.ts` | 同上，覆盖引擎启动之前就终结的 run | 2026-07-27 `6a50f83c` | [D10](DECISIONS.md#d10) |
| 009 | 源码（构建插件） | `web/vite-plugins/ap-cdn-rewrite.js` + `web/vite.config.mts` + `web/vite.embed.config.mts` | 构建期把硬编码的 `https://cdn.activepieces.com` 改写成同源 `/ap-cdn`。上游在 16 个文件约 47 处写死该域名，气隙下全部加载失败；改写而非逐处改源码，是为了保持 vendor diff 干净（[Q8](DECISIONS.md#q8)）并自动覆盖将来合并进来的新引用。资产由 `deploy/pieces/mirror-ap-cdn.mjs` 联网抓进 `web/public/ap-cdn/` | 2026-07-26 `7df25489` | X-2 / X-3 |
| 010 | 源码 | `api/src/app/flags/theme.ts` + `web/index.html`（+ `web/public/hermes-*.svg`） | 白标：`websiteName` 改 Automation Studio（与 admin-center 入口菜单一致），logo / favicon 从 `cdn.activepieces.com/brand/*` 改同源相对路径 | 2026-07-26 `7df25489` | X-2 / X-3 |

源码类的路径相对 `activepieces/packages/`（003–008 在 `server/` 下，001 / 009 在 `web/` 下，010 两侧都有）。

009 归为源码类而非构建期：插件本身在 vendor 树里、随 `vite build` 自动生效，无需外部脚本挂载 —— 这正是 002 还没做到的形态。

## 回归网

只有 007 / 008 带专属测试。其余是补登记时的既有状态，不是"已验证"的意思。

| # | 测试 | 覆盖 |
|---|---|---|
| 007 | `engine/test/operations/sync-webhook-release.test.ts` | 逐 `FlowRunStatus` 穷举：终态释放、非终态（PAUSED / QUEUED）不释放、缺 id 不发、发布失败不外溢 |
| 008 | `worker/test/lib/execute/jobs/execute-flow.test.ts` | 引擎启动前终结的 run 也释放；正常跑完 worker 不插手；缺 id 不发；best-effort |

**007 的非终态分支在 dev 环境测不到**，故必须靠单测：能暂停的 piece（`core/delay`、`core/approval`、
`core/subflows`）都不在已装白名单里，唯一装了的 `core/webhook` 是「先答后停」——
`return_response_and_wait_for_next_webhook` 通过 `createWaitpoint({ responseToSend })` 在同一步就把
响应发出去了（`piece-executor.ts` 的 `getResponse()` 对 `case 'paused'` 返回 `responseToSend`），
所以调用方在暂停前已被回答，那里即使判断写反也观察不到症状。**一旦 `core/delay` 进白名单**
（"等 N 分钟再继续"这类需求），暂停就变成"还没人回答过"，这条判断立刻承重。

## 待办

- 003 / 004 / 005 的 D9 尚在草案态（`D9_PIECE_ONLINE_ADMIN_DRAFT.md`），评审通过后回填正式裁决号。
- 002 无对应裁决号；它是 AI Generate 链路的运行时修复，建议在文档里补一条来源。
