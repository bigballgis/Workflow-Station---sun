# Batch 外层选行与统一表单 — 实现与验收记录

日期：2026-10-20（Asia/Hong_Kong）。工作区改动，未提交 Git。

## 行为

- 外层子表选择操作对象。Batch Edit / Batch Delete 未选行时禁用；Batch Add 独立使用。
- Batch Edit 打开一份 Batch Form，保留字段顺序、支持的布局、输入属性及分派组件位置。弹窗内没有逐行表格或复选框。
- 字段默认不修改；设置值、明确清空、恢复不修改分别记录意图。零和 false 不被当成空值。
- 字段编辑与分派共用打开弹窗时的选中范围。主键、外键、计算字段及只读字段受保护。
- 应用只更新页面草稿，继续走正常保存链路。选中行变化时阻止应用；未选中行的并发更新保留。
- Batch Add 填写一次并指定数量，继续使用原有主键和外键准备。Batch Delete 在外层确认。
- Portal 与 DW 预览共用批量布局和意图逻辑。不支持的控件明确报错，不静默省略。

## Code Review 结果

### 结论

静态重审 PASS；整体验收 CONDITIONAL：完整 MI 截图回归缺少附件样本，全库 typecheck 仍有既有错误。不能将本记录解读为完整发布门禁通过。

### Findings 与逐条处置

首轮静态审查 FAIL：

1. Major：`useBatchSelection.ts` 原先只清应用选择，Element Plus reserve-selection 可能重新带入已删除行。
   - 反例：选 A → 删除 A → 选 B → 旧 A 再次进入操作对象。
   - 修正：两端表格接入 `selectionTable`；清理组件内部选择；选择事件过滤失效身份；删除后恢复仍存在的选择。
   - 证据：`batchOuterSelection.test.ts` 三项测试；实际删除后重新选行及无修改 Apply 验证。
2. Major：`BatchFormLayout.ts` 未保留字段的 `rule.col`。
   - 反例：两个 span=12 字段会变成全宽堆叠。
   - 修正：共享布局使用 Row/Col 并保留 span/offset；消除根 Row 负边距导致的横向溢出。
   - 证据：字段分栏测试；最终弹窗 scrollWidth=clientWidth=928，scrollLeft=0。

第二轮静态审查 FAIL：

3. Major：`BatchWorkspace.vue` 对没有挂载的分派编辑器调用收集函数，会阻止普通字段编辑。
   - 反例：分派组件在未打开的 lazy 标签页中，修改普通字段后 Apply。
   - 修正：只有分派明确发生修改时才收集分派；未触碰的分派保留原值。
   - 证据：未挂载编辑器的普通字段修改回归测试。
4. Major：`SubTableBatchPreview.vue` 的删除入口遗漏 structuralFk 可编辑子表条件。
   - 反例：MI 参与者绑定在 DW 预览可以删行，而 Portal 不允许同一操作。
   - 修正：按钮及事件处理都检查 businessRows；测试同时检查隐藏按钮及直接调用处理函数不写数据。

最终独立静态重审：四项 Finding 均关闭，没有剩余具体正确性、安全或数据丢失问题。无未处置 Minor / Question。

### 范围 / 验收映射

修改范围：共享 batch 组件、Portal 子表接线、DW 子表预览、三语言提示、既有 Sub-Table 帮助文章、相关测试及验收脚本。未修改后端提交契约、BPMN、数据库 schema 或既有业务 FU 配置。

### 触发矩阵

已检查配置驱动的行身份、冻结 capability、原有 CRUD 权限、Portal/DW parity、弹窗标签、三语文案、热路径测试及可部署产物。

### 编译与静态检查

- `pnpm -C frontend/user-portal run build`：PASS。
- `pnpm -C frontend/developer-workstation run build`：PASS。
- `pnpm -C frontend/help run build`：PASS。
- `git diff --check`：PASS。
- 全库 `vue-tsc --noEmit`：未通过。包括 Portal 既有 FieldRenderer 未使用导入、既有 nullable 参数，以及 DW 既有 resolveRowUploadUrl 未使用绑定；HEAD 中可查到这些未改动代码。未修改类型检查规则，也未清理范围外代码。

### 测试与运行证据

- `pnpm -C frontend/user-portal exec vitest run Batch batch`：19 文件、292 测试 PASS。
- `pnpm -C frontend/developer-workstation exec vitest run Batch batch`：8 文件、61 测试 PASS。
- `pnpm -C frontend/user-portal run test:regression:mi`：29 文件、234 测试 PASS。
- `pnpm -C frontend regression:mi`：完整流程未通过。已完成的一次执行在附件行场景报 `FAIL: no To Do or My Request with attachment rows`；后续重复扫描未完成后取消，不计通过。
- `node frontend/scripts/verify-help-portal.mjs`：153/153 PASS。
- `verify-batch-outer-selection.mjs`：真实 Portal 编辑一行、正常保存、未选中业务值保留、批量新增两行、主外键准备、删除一行、删除后重选和无修改应用 PASS。
- `verify-batch-outer-assignment.mjs`：真实人员分派保存 PASS，提交只有一条 ASSIGN rowPatch，另一行分派保持不变。没有完成任务或创建子任务。
- `verify-batch-outer-preview.mjs`：真实 DW 预览新增、外层选一行、填写一次修改，另一行保留；预览期间无写请求，浏览器无错误。
- `verify-batch-outer-readonly.mjs`：已有旧批量实例外层选行、打开、取消 PASS；最终分派弹窗横向溢出检查 PASS，未改业务值。

测试脚本通过 `FIXTURE_TASK_ID` / `FIXTURE_UNIT_ID` 固定目标。会写入数据的脚本拒绝非验收 FU；保存验证需新发起的两行验收实例。

### 截图

- [Portal 外层选行](../../frontend/user-portal/verification-screenshots/2026-10-20_batch-outer-selection.png)
- [Portal 已有实例统一表单](../../frontend/user-portal/verification-screenshots/2026-10-20_batch-outer-existing-demo.png)
- [Portal 分派表单](../../frontend/user-portal/verification-screenshots/2026-10-20_batch-outer-assignment.png)
- [Portal 保存后的行](../../frontend/user-portal/verification-screenshots/2026-10-20_batch-outer-persisted.png)
- [DW 统一编辑表单](../../frontend/developer-workstation/verification-screenshots/2026-10-20_batch-outer-dw-edit.png)

截图已人工查看，标签完整、无弹窗内部复选框。最终 Portal 分派表单与 DW 预览截图在重建部署后捕获。

### Function Unit 通用性与生命周期

- 新建测试 FU：50086（uniform20261020，CRUD）、50087（uniformassign20261020，分派），均由正常 DW 创建、Validate、Deploy API 生成，带验收标签。
- 旧实例：2026-10-19 创建的已有批量 FU 实例通过只读 UI 检查。
- 行身份与分派字段来自配置，无具体业务表、字段、FU 标识写入产品逻辑。
- Save/Load、Export/Import、Clone、Version/Rollback、Admin 两条 Import：配置格式未改变，无需新增持久化/迁移处理；未重新执行生命周期 round-trip，不能声称该矩阵已完整运行验证。
- Portal 后端：提交契约不变，真实保存确认 EDIT / ADD / DELETE / ASSIGN 仍被接受。
- Portal/DW：共用渲染和操作意图，真实两端 UI 验证。

### 增量敏感信息

已检查改动行及新增脚本/组件，未添加凭证、私钥或具体业务账号到产品逻辑。验收脚本使用现有登录 helper 与环境变量。截图聚焦合成数据表格/弹窗。

### 性能

一次只渲染一份表单，不再在弹窗为每行挂载编辑器。未做性能优化承诺或基准比较。批量操作继续限制最多 200 行。MI 完整截图门禁仍未通过。

### 残余风险 / 范围外问题

- 缺少 MI 附件测试样本，需准备样本后重跑完整回归。
- 全库类型检查存在既有错误，本轮未扩大修复范围。
- 原报障任务的自动化登录上下文未取得可操作子表，因此已有实例兼容性改用已存在的旧验收实例检查；未修改原报障任务。
- 原保存链路会递增未选中行的版本元数据；验证确认未选中行的业务值和身份不变。
- 保留两个独立验收 FU 及测试实例，供复验使用；未删除或重置原有 FU。

### 部署

已重建并启动本地 `user-portal-frontend`、`developer-workstation-frontend`、`platform-help-frontend`。未重启后端或执行数据库迁移。
