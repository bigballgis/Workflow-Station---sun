# Change History review findings 修复与重审

## 处置

- Major：`sameScope` 将两个空 taskInstanceId 视为同一任务，导致同一用户短时间内不同退回轮次的 Process Form 保存可能合并。Process Form 当前没有可靠的任务/退回轮次标识，因此不推断轮次：有 batchId 的一次真实保存仍保持完整，但无任务标识的不同保存不再合并；旧记录无批次且无任务标识时，仅合并原始时间完全相同的明细。此规则通用于所有缺少任务标识的记录，没有按 FU 或 stage 名称硬编码。
- 此处存在明确限制：同一退回轮次的多次无任务标识保存也会分组。要安全恢复这类记录的五分钟合并，需要后端提供可靠的轮次标识。有任务实例 ID 的正常任务继续使用五分钟规则。
- Minor：两份浏览器脚本固定的“未来”样本时间实际早于新任务快照。改为在浏览器时区解析 URL snapshotTime，再以其前一小时生成历史样本、后一秒生成未来样本；标题断言随样本时间生成。脚本支持独立证据目录，保留此前验证产物。
- 三语 Help 与 llms 索引同步说明无任务标识时的边界。

## 验证

1. 红：新增 taskless 边界测试后、修复前执行 `pnpm exec vitest run src/composables/changeHistoryPanel/__tests__/useChangeHistoryBatches.test.ts`，3 failed / 18 passed。覆盖跨保存、旧记录不同时间和新旧混合三个反例。
2. 绿：修复后 `pnpm exec vitest run src/composables/changeHistoryPanel/__tests__`，42/42 通过，包括超过 200 条明细、五分钟边界、独立事件、快照过滤及搜索脱敏等现有测试。
3. `node --test scripts/__tests__/change-history-fixture-clock.test.mjs`：4/4，覆盖旧/新快照、跨日时间和无效输入。
4. Portal 与 Help `pnpm run build` 均通过；使用 dev compose 和 Dockerfile.local 重建并启动两个前端容器后运行 Playwright。
5. 两份脚本在 10 月 23 日旧任务及 10 月 26 日新任务上运行。旧任务 API 90 条、展示 76 条/13 组，原始字段摘要仍为 `2f51260763cb89dd619a3e6e892183e625d6b809008c5a4e6346975e191ac92a`；新任务 48 条/7 组。排序、搜索、刷新保持收起、快照排除未来样本均通过，浏览器 pageerror 为零。
6. 浏览器拦截样本验证 taskless：两个保存各两条明细分成两组；旧记录同时间两条成组，下一秒单独成组。共 7 条/4 组。仅拦截响应，未写入业务历史。截图已人工检查。
7. 变更的分组源码及测试 ESLint 通过。全量 vue-tsc 仍有 382 条既有错误，ChangeHistory/changeHistory 文件零命中；没有调整类型规则或掩盖错误。

浏览器复现命令（在 frontend 目录，CH_TASK_URL 为对应完整任务 URL）：

```powershell
$env:CH_VERIFY_OUTPUT_DIR='user-portal/verification-screenshots/review-fixes-new-task'
node scripts/verify-change-history-batches.mjs
node scripts/verify-change-history-toolbar.mjs
```

旧任务使用 `review-fixes-legacy-task`，并设置 `CH_BASELINE=user-portal/verification-screenshots/change-history-baseline.json`，避免覆盖旧证据。

证据目录：`frontend/user-portal/verification-screenshots/review-fixes-new-task/` 与 `review-fixes-legacy-task/`，分别包含真实页面、taskless 边界截图及 JSON 断言结果。

## 重审结论：CONDITIONAL

原 Major 和 Minor 已处置，增量重审无新增 Blocker/Major。复核原 staged 审查涉及的批次写入、实际保存原子性、快照先过滤再分组、脱敏后搜索、独立事件及兼容读取边界；本轮未修改后端或迁移，沿用上一轮 138 项后端测试通过证据。增量敏感信息检查未发现新增真实凭证。

整体不能给无条件 PASS：全量类型检查有既有错误；此前完整 MI 浏览器回归受缺少附件样本影响未全通过，本轮未触及 MI 热路径；没有通过真实写操作制造两轮退回业务流程。跨轮次反例由单元测试及响应拦截样本覆盖，不宣称已完成真实退回流程的端到端验证。

FU 生命周期：本轮只调整通用历史展示及验证工具，不修改 FU 设计配置或导入、导出、克隆、版本回滚格式，生命周期同步 N/A。无新增数据库变更；部署前端即可，回退前端不会改变已有历史数据。
