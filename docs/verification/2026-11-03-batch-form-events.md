# Batch Form 事件与 Assign 回填验收

后续已扩大到八个表单事件、七个hook、DW/Portal真实组合与BU/Role切换，并补齐MI附件样本。最新结果和覆盖边界见[扩展验收报告](2026-11-03-batch-form-events-expanded.md)；下方为首轮历史记录。

日期：2026-11-03。本地实现，未提交 Git。

## 结果与原因

已按方案二实现共享事件解析/API、每条记录独立草稿。用户要求的 Batch Assign 选择人员并回填 email 已通过真实页面、提交请求和数据库核对。

原限制来自两层保存契约：前端 `batchFormOptionsIssue` 和后端 `BatchFormControlContract.validateOptions` 拒绝所有表单事件，仅允许控件 change。原运行时在 Apply 才逐行重放 change，API 也只有读取/写值，不能简单删除保存校验。普通 Form 使用完整事件解析和状态 API。因此，用户遇到的是 Batch 能力缺口，不是事件脚本必须改成另一种语法。

修复前用 MCY 结构副本在 DW 页面复现：保存报 `unsupported Batch field or event onChange ... field change events only`，没有发出保存 PUT。修复后同一表单和事件保存成功，重新读取配置仍包含脚本。

## 实现范围

- 普通 Form 和 Batch 共用 `frontend/shared/src/formEvents/` 的解析器、值 API 和界面状态 API；当前用户由各宿主注入，旧 import 入口保留。
- Batch 每条选中记录保留独立事件会话。控件 change、value hook、表单 onChange 即时执行；Apply 不再次重放 change。错误恢复全部选中草稿，初始化失败保持阻止提交。
- 接入表单 onCreated、onMounted、onReload、onReset、beforeSubmit、onSubmit、onChange 和原生请求 beforeFetch。控件其他事件与 hidden/deleted/titleClick/watch hook 由原生控件转发；load/mounted/value 由会话调度。
- 值、显隐、禁用、必填、标签、选项、错误、通知、Lookup 筛选/刷新与焦点通过共享 API 和当前预览记录状态连接。
- Assign 的 Lookup 对象可供脚本读取 email；人员身份提交仍由正式 ASSIGN 收集器负责。脚本不能直接写 ownership 字段，包含条件规则中的字段；PK/FK/计算字段和只读限制继续生效。
- 前后端保存契约同步，保留已有控件和 validator 范围。帮助页及三语说明同步。

这里的能力对齐指支持范围内的同步事件和 API。异步事件返回 Promise 会报错；自定义 validator、未支持控件没有随本次开放。beforeFetch 仅在真实原生请求链触发，不会凭空增加 Lookup 请求事件。批量生命周期按每条记录执行，界面显示所选预览记录的状态。

## 真实验证

来源 FU48：`MCY Debit Card_20260525`，只读取模板；验证 FU50128：带 `batch-verification-synthetic` 标签的独立副本。使用合成数据与无邮件/服务副作用的测试流程，未修改来源 MCY 配置。副本中的既有文本列 merchant_name 仅作为测试 email 回填目标，产品代码不依赖列名或 FU ID。

最终任务：`adc88a64-d737-11f1-81ef-1633fc20113b`。

复跑入口：`node frontend/scripts/verify-batch-form-events.mjs configure`，然后 `node frontend/scripts/verify-batch-form-events.mjs verify-ui`。fixture 状态文件保留在被 Git 忽略的 `.codex-batch-events-fixture.json`。

1. DW 实际 Save 发出成功 PUT，重新读取事件未丢失。
2. Portal 三条合成记录只勾选前两条，Batch Assign 选择另一个演示用户。外部 Email copy 输入框 DOM 值立即等于所选 Lookup 的 email。
3. Apply 前数据库不变；Cancel 确认后数据库仍与基线相同。
4. 重新选择并 Apply，请求包含 ASSIGN 和 EDIT 两类操作，服务端返回成功。
5. API 与 PostgreSQL process variables 中前两条 email 和人员 ID 均与选择一致。人员列允许保留 Lookup 对象，按其 id 核对正式分派身份。
6. 第三条所有业务字段保持原样。其 `_wsRowVersion` 增加 1：现有 `SubTableBindingScopeGuard` 对保存 scope 内的已认领记录统一更新并发令牌，本次没有修改该机制。
7. 重新加载、帮助页截图完成，浏览器 pageErrors 为 0。

证据：

- [DW 修复前保存失败](../../frontend/developer-workstation/verification-screenshots/2026-11-03-batch-events-save-before.png)
- [DW 修复后保存成功](../../frontend/developer-workstation/verification-screenshots/2026-11-03-batch-events-save-after.png)
- [Assign 回填草稿](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-events-email-draft.png)
- [保存并刷新](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-events-saved-after.png)
- [事件帮助页](../../frontend/help/verification-screenshots/2026-11-03-batch-events-help.png)
- 本地日志 `.codex-batch-ui.log` 最终记录 `selectedRowsBackfilled:2`、`cancelPreservedDatabase:true`、`databaseConfirmed:true`、`pageErrors:0`。截图已检查，敏感输入区域有遮罩，值的判断由 DOM 与数据库断言完成。

红→绿证据还包括：移除 reactive rules 后原生 Lookup 集成测试的输入 DOM 值为空，恢复后通过；移除 ownership 原值恢复后正式分派基线测试失败，恢复后通过。对应本地忽略日志 `.codex-batch-dom-red.log`、`.codex-batch-dom-green.log`、`.codex-batch-assignment-red.log` 位于 Portal。

# Code Review 结果

## 结论

CONDITIONAL。核心场景验证通过；最终复审未发现未处置的代码 Blocker/Major，但完整 MI 截图门禁未完成，全量类型检查保留基线错误。

## Findings

### Blocker

- 已处置：`frontend/shared/src/batch/BatchWorkspace.vue`，将事件草稿直接交给 Assign 收集器会掩盖原人员与目标人员差异。
  - 反例：事件会话已把原人员替换为 Lookup 对象，收集器可能不再生成正式 ASSIGN。
  - 影响：分派身份更新缺少正确操作意图。
  - 最小修复：进入正式收集器前恢复原 ownership 字段，只保留脚本回填的普通字段；未产生正式意图的 Lookup 草稿不提交身份。
  - 依据：implementation-autonomy、code-review 的通用性与授权要求。
  - 验证：原身份比较、无意图保护、真实 Lookup 集成与实际 ASSIGN+EDIT 请求均通过，复审关闭。

### Major

- 已处置：`frontend/shared/src/batch/BatchDesignedForm.vue`，普通 cloned rules 不响应原生 API 的值写入。
  - 反例：会话与 fapi 值已回填，但真实输入 DOM 仍为空。
  - 影响：用户无法看到回填结果。
  - 最小修复：使用 reactive rules 并保留原生表单实例，将会话状态同步到现有 API。
  - 依据：bugfix-verification 禁止只验内部状态。
  - 验证：同一输入 DOM 断言红→绿，实际页面输入值断言通过，复审关闭。
- 已处置：`frontend/shared/src/batch/BatchWorkspace.vue`，初始化失败可被下一次成功编辑清除；失败的控件更新可能继续记录 Assign 意图。
  - 最小修复：持久初始化错误阻止 Apply，失败时恢复所有草稿并刷新界面，change 返回接受状态后才记录意图。
  - 验证：初始化异常、跨行回滚、条件规则 ownership 写入保护测试通过，复审关闭。
- 验证缺口：完整 MI 截图回归在附件场景因本地没有附件行样本停止；属于环境数据缺口，不能计为通过。

- 已处置：beforeFetch 接入统一 handler 调度，确保保存契约允许的事件数组也能传入 config/data 并保留返回 false 的取消语义。新增参数与中断顺序测试通过，复审关闭。

### Minor

无待处置项。

### Question

无。

## 范围 / 验收映射

用户要求：批量选人时回填其他字段。覆盖 DW 保存、Portal 即时联动、Cancel、Apply、选择范围、真实持久化及重新加载。DW Preview 的完整人工操作未独立验收；共享渲染器与事件单测通过，不将其计为真实预览页面证据。BU/Role 组合、每个原生组件的全部事件未逐一浏览器验收。

## 触发矩阵（命中项）

共享事件解析、普通表单兼容、Batch 独立草稿、Assign 授权意图、Lookup、前后端契约、三语帮助、UI 构建与容器验证、增量敏感信息检查。

## 编译与静态检查

- 四个后端及依赖 `mvn -pl backend/developer-workstation,backend/admin-center,backend/user-portal,backend/workflow-engine-core -am package -DskipTests`：BUILD SUCCESS；四个后端容器已重建。
- DW、Portal、Help 的 `pnpm run build`：通过；三个前端 Docker 服务已重建，实际验证使用最终构建。
- `git diff --check`：通过。
- 两端 `vue-tsc --noEmit`：未通过。使用 HEAD 独立源码与相同依赖/声明文件复核，Portal 基线/当前均381项，DW均164项；LookupComponent 三个原诊断因新增 prop 顺移一行。没有把全量类型检查描述为通过。

## 测试与运行证据

- Common `SubTableBatchDefinitionTest`：6 项通过。
- DW `vitest run formCreate batchSubTableConfig useFormSave.batch`：107 项通过。
- Portal `vitest run formCreate batchEventSession batchRowEvents SubTableBatchWorkspace SubTableBatchAssignmentEditor`：101 项通过，其中 Workspace 23 项。
- `pnpm run regression:mi`：250 项单测通过；Details 与人员隔离截图通过；附件行样本缺失导致截图回归失败，后续场景未计为通过。
- MCY 副本实际 UI/API/数据库：通过，见上方证据。

## Function Unit 通用性与生命周期

- 通用性：配置驱动；产品代码无 MCY、目标列、人员 ID 硬编码；独立记录、只读及条件 ownership 单测通过。
- Save/Load：DW 页面、后端持久化、发布后 Portal 配置读取通过。
- Export/Import、Clone、Version/Rollback、Admin 两条 Import：本次未改存储结构、打包器或导入器，既有 event 字符串继续随原 rule/options 序列化；公共 Java 校验同步。静态检查通过，未独立执行全部生命周期回归，不声明运行验收通过。
- Portal runtime/parity：实际核心场景通过；普通 Form 事件专项通过。DW Preview 和全部 BU/Role 组合保留未验证标记。

## 增量敏感信息

扫描修改行及新增源文件中的私钥、密码/令牌赋值、AWS/GitHub key：0 候选。验证脚本复用既有登录助手，无新增凭据；只写明确标记的合成 FU。

## 性能

每次用户操作按已选记录执行，既有上限200条；事件循环设置1000次上限；保留原生表单实例，界面状态变化才 mergeRule。未新增依赖或后端查询；200条复杂脚本的浏览器性能未量测。

## 残余风险 / 豁免

无全量放行或豁免声明。当前结论为本地核心场景已验证、总审查 CONDITIONAL。完整 MI 附件截图需准备真实可用样本后重跑；全量类型债务、DW Preview、组合与生命周期运行验收按上述范围保留。

## 范围外问题

已有全量类型错误及本地附件样本缺口，未扩大本次代码修改。
