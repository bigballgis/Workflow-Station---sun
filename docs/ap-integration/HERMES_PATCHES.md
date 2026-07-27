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

> **⚠️ 两类补丁，别只 grep 源码。** 001/002 是**构建期改写脚本**，不在 vendor 树里 ——
> 它们在 Dockerfile 里重写已构建的产物。只按源码注释盘点会**漏掉这两个**，而它们恰恰改的是
> web 产物与 piece 运行时行为。二者都是 fail-loud（模式匹配不上就 `process.exit(1)`），
> 所以 AP 升级时构建会红，不会静默失效 —— 但它们不会主动告诉你自己的存在。

## 清单

| # | 类型 | 位置 | 内容 | 落地 | 裁决 |
|---|---|---|---|---|---|
| 001 | 构建期 | `deploy/pieces/patch-web-approvals.js` | 清空 `APPROVAL_PIECES_CONFIG` 并隐藏 Approvals 标签页。该标签页硬编码 6 个 SaaS piece，全部加载成功才渲染；白名单下 6 个全 404 → 永久骨架屏 | 2026-07-02 `821cf33c` | [Q9](DECISIONS.md#q9) |
| 002 | 构建期 | `deploy/pieces/patch-piece-ai-run-agent.js` | `piece-ai` 的 `run_agent`：补 `maxOutputTokens`（DeepSeek 把 reasoning token 计入预算，默认额度会让文档还没输出就耗尽）+ reasoning-delta 不再拼进输出 | 2026-07-16 `e5da4738` | — |
| 003 | 源码 | `api/src/app/pieces/community-piece-module.ts` | 开放 piece 删除端点给 Admin Center | 2026-07-26 `277f15ae` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 004 | 源码 | `api/src/app/pieces/metadata/utils/index.ts` | 恢复最小 platform 级 piece 可见性 | 2026-07-26 `277f15ae` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 005 | 源码 | `worker/src/lib/cache/code/pkg-runner.ts` | 气隙运行时 piece 安装（pnpm offline store） | 2026-07-26 `3811061d` | [D9 草案](D9_PIECE_ONLINE_ADMIN_DRAFT.md) |
| 006 | 源码 | `worker/src/lib/egress/lifecycle.ts` | `AP_SSRF_ALLOW_LIST` 接受主机名，不再只认 IP / CIDR | 2026-07-27 `d6ec1e33` | [D6](DECISIONS.md#d6) |
| 007 | 源码 | `engine/src/lib/operations/sync-webhook-release.ts` | run 进终态即释放 sync webhook 监听器（步骤内失败） | 2026-07-27 `6a50f83c` | [D10](DECISIONS.md#d10) |
| 008 | 源码 | `worker/src/lib/execute/jobs/execute-flow.ts` | 同上，覆盖引擎启动之前就终结的 run | 2026-07-27 `6a50f83c` | [D10](DECISIONS.md#d10) |

源码类的路径均相对 `activepieces/packages/server/`。

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
