# Code Review 结果 — Change History 批次与折叠展示

## 结论

CONDITIONAL。实现、相关测试和部署后的专项浏览器验证通过；完整前端类型检查存在基线错误，完整 MI 浏览器回归缺少附件样本，因此不宣称全仓检查 PASS。未提交或推送。

## 首轮检查 Findings 与逐条处置

### Blocker

无。

### Major

1. `ChangeHistoryPanel.vue:19`：帮助链接的 `aria-label` 被类型检查视为 HTML 属性，必需的 `ariaLabel` prop 未传入。
   - 反例：`pnpm run typecheck` 在该行产生 TS2345。
   - 影响：本轮新增类型错误。
   - 处置：改用对象 `v-bind` 传递组件 props，避免 ESLint 自动转换属性后再次引入问题。
   - 重审：最后一轮全量类型检查中，本组件、历史 composables、测试及 API 类型无报错。
2. `ChangeHistoryBatchLookupTest.java:101`：原有批量写入测试仍按 12 个占位符/行断言。
   - 反例：新增批次两列后，401 行实际 5614 个占位符，旧预期 4812。
   - 影响：不能验证新版大批量 SQL 绑定契约。
   - 处置：同步为 14 个占位符/行，200 行块的上限改为 2800。
   - 重审：相关后端测试及 PostgreSQL 大批量集成测试通过。

### Minor

`SubTableBatchPostgresTransactionIT.java`：批次时间使用纳秒精度的当前时刻，对 PostgreSQL 微秒存储错误地假设只截断而不舍入。改用固定、可精确存储的批次时间，继续逐条断言数据库中的批次 ID 和批次时间；没有放宽为非空断言。

### Question

无待确认业务问题。

## 完整范围重审

- 当前 Blocker：0；本轮尚未修复的 Major：0。
- 新增批次列可空；保留原明细时间；没有回填、删除、覆盖旧历史。
- 上下文在保存入口创建，主表、多个子表共享；大于 200 条走原生 SQL 时也写入两列。
- Claim、Unclaim、强制释放、转派和备注事件独立成组，并打断连续编辑。
- 无实际字段变更不写明细，因此不显示空批次。未改变业务保存接口的幂等或事务契约。
- 新旧历史均使用五分钟连续编辑展示。同用户、同流程、同任务实例、同 stage 才可合并；组内保留全部旧值、新值和事件。
- 在既有可见性和快照过滤后分组；计数只包含当前可见明细。修正同一任务记录绕过快照截止时间的分支。
- 折叠状态使用首个保存/旧记录的稳定标识，数据重取时保留；切换流程实例时清空。
- 三语言产品文案、现有任务帮助指南及帮助入口同步。

## 范围 / 验收映射

- 数据模型：`ChangeHistory`、`ChangeHistoryContext`、`ChangeHistoryRecord`、前端 `ChangeHistoryRecord`。
- 写入：普通字段/子表、原生批量 SQL、备注、任务分派、并发警告。
- 展示：共用 `ChangeHistoryPanel`、分组和折叠 composables；任务详情与申请详情自动使用同一组件。
- 迁移：追加 99 SQL、注册首次初始化顺序；现有库已连续执行两次验证幂等。
- 文档：`docs/schema-and-migration.md` 第 6 节；既有任务指南增加 Change History 小节。

## 触发矩阵

通用正确性、影响链、schema 只增不改、原生 SQL 绑定、三语 i18n、UI 截图、Portal MI、增量敏感信息。未新增依赖、配置项、接口权限或设计器能力。

## 编译与静态检查

- `mvn -pl backend/user-portal -am package ...`：PASS。
- Portal `pnpm run build`：PASS；Help `pnpm run build`：PASS。
- 本轮前端文件 ESLint：0 errors；组件的 5 条可选 prop 默认值 warning 为原有模式。
- `git diff --check`：PASS。
- 全量 `pnpm run typecheck`：未通过，当前 382 项错误；未修改 HEAD 副本同样失败（386 项）。本轮改动文件无报错，未降低检查标准或扩改无关组件。

## 测试与运行证据

- 历史相关后端选择集：118 tests，0 failures/errors。另运行事件相关选择集：22 tests，0 failures/errors（含重复覆盖的批次测试）。
- 真实、独立 PostgreSQL：主表 + 401 条第一子表 + 1 条第二子表，共 403 条共享批次；重复无变化保存不增记录；失败 JDBC 块全部回滚。
- 前端历史面板：32 tests PASS，含五分钟边界、连续链、同秒、不同主体/任务/stage、独立事件、旧数据、405 条同批、刷新折叠与快照过滤。
- MI：234 unit tests PASS；两个 live 浏览器场景 PASS。附件场景无可用样本，后续场景未完成，不记为完整回归通过。
- Portal、Portal Frontend、Help Frontend 已重建部署；Portal 后端 healthy。启动无 schema/SQL 异常；浏览器关闭流连接产生过既有 SSE broken-pipe 日志，与批次写入无关。
- 原实例 API：部署前后均 90 条，移除新增的空批次字段后 SHA-256 摘要一致（含内容和顺序）。
- 原完成快照：过滤后 76 条可见明细，显示 13 个分组；收起后触发真实历史 API 刷新，仍保持收起。
- 浏览器 fixture：8 条可见明细/7 组，验证独立事件、时间间隔、换人、排除未来记录、键盘操作及 1366px 视口。fixture 只拦截响应，不写业务数据库。
- 帮助入口指向现有任务指南的 Change History 小节。

截图和机器摘要在 `frontend/user-portal/verification-screenshots/`：

- `2026-10-26_change-history-live-expanded.png`
- `2026-10-26_change-history-live-collapsed.png`
- `2026-10-26_change-history-mixed-fixture.png`
- `2026-10-26_change-history-1366-fixture.png`
- `2026-10-26_change-history-evidence.json`
- `2026-10-26_change-history-baseline-checks.txt`

复跑专项浏览器脚本：从 `frontend` 设置 `CH_TASK_URL` 为带 processInstanceId 的已完成任务 URL；可选设置 `CH_BASELINE` 为基线摘要文件，然后执行 `node scripts/verify-change-history-batches.mjs`。

## Function Unit 通用性与生命周期

- 通用性：静态 PASS。产品代码无 FU、表、字段或流程标识硬编码；按已有运行时记录字段及事件类型处理。
- Save/Load、Export/Import、Clone、Version/Rollback、Admin 两条 Import：设计产物生命周期 N/A，新增字段仅属于 Portal 运行时审计。
- Portal runtime：已验证原有实例；尚未进行新建 FU 到完整流程的端到端运行，不能宣称双 FU 运行 PASS。

## 增量敏感信息

扫描本轮新增行及新增源码/脚本/文档；无新增凭证、token、私钥。浏览器脚本复用已有登录 helper，临时 PostgreSQL 随机密码不写入文件。只保存历史内容摘要，不保存 API 明文业务载荷。

## 性能

未增加历史查询次数或 SQL 逐字段查询；批量块仍为 200 行。前端在现有响应上排序/归组，不新增接口请求。未做大规模性能基准，不宣称性能提升。

## 残余风险 / 验证缺口

- 旧记录缺少准确保存 ID，按既有时间作连续编辑归组；不用于恢复精确点击边界。
- 批次关联不提供新的整批事务保证，保留现有独立审计事务行为。
- 全量类型检查、完整 MI 浏览器回归及新建 FU 全流程验证未全部通过/完成，结论保留 CONDITIONAL。

## 范围外问题

- 两项既有 `SubTableBatchPostgresTransactionIT.unmarkedInvalidValueCannotBypassMetadataOrIncrementEntityVersion` 断言失败，已在未修改 HEAD 副本中复现；未扩改子表校验逻辑。
- Developer 测试账号对指定完成任务的表单权限不足；历史 API 可读，专项截图验证的是历史面板，不代表该账号的整张任务表单可用。
