# 多绑定引起的 403：修复验证

2026-10-30，香港时间。本地 Docker 环境已完成核心复现、修复和浏览器验证；没有部署远程 HSBC Dev。

## 复现

- 读取用户目录 `28.1226，多绑定引起的403/错误报告.md` 后，先对实际提交准备函数运行失败测试。
- 已有 Case 行的交易外键分别缺失、为 null、为空字符串时，均被错误改成当前交易 ID：3 条测试全部失败。
- 本地原前端的专用任务 `20e769d2-d468-11f1-a7f8-86871052c760` 点击 Approve / Confirm 返回 403。当前本地后端首先报 `Binding is not allowed to write this sub-table row`，并非远程报告中的完全相同错误文案；未把两者混称为同一条服务端错误。
- 补测另发现：MI 隔离丢失画布外的主表主键；双外键行的结构归属又依赖字段顺序。这两项也分别先得到失败测试。

## 根因与改动文件

同表 canonical 数据中的已有 Case 行，被当成当前交易尚未补外键的新行；部分读取和嵌套同步函数又忽略 binding 声明的过滤外键。

- `useTaskDetailMiLinkChild.ts`：用服务端快照、配置主键或平台 UUID 识别已存行，不隐式补写其归属。未存行须匹配当前关系或能证明位于当前父行的本地嵌套编辑区，才进入补值链。
- `miLinkChildIdentity.ts`：将 filter FK 列名和目标表 ID 带入关系解析；优先读取当前 binding 的声明，保留 MI collection 及目标表检查。
- `miLinkChildRows.ts`、`PortalFormFields.vue`：显式关系按 FK 投影，空 FK 不再意味着本交易新行。
- 嵌套表单内声明指向 MAIN 的共享关系继续共享，不拿交易宿主主键过滤它；该反例已完成红→绿组件测试。
- `subTableFilterProjection.ts`：显式声明的单 binding 读取也过滤；保留原来的写入合并开关与 MI collection 行为。
- `useTaskDetailMiIsolation.ts`：保留主表配置主键，即使它未放在表单画布上。
- 新增 `miDualBindingOwnership.test.ts`、`miMainKeyProjection.test.ts`，并纳入 MI 单元门禁；扩展单 binding 投影测试。
- 新增 `frontend/scripts/verify-1226-ownership.mjs`，供本地专用测试单重复执行 Save / Complete、截图及数据库断言。

没有修改后端权限校验、业务表结构、旧发布快照或远程业务数据。

## 红→绿证据

- 相同提交准备测试：修复前 3 条失败，修复后通过。另补平台 UUID、新增但已分配主键、本地嵌套草稿、其他交易、双归属记录测试；该文件最终 8/8 通过。
- 同一条原失败任务：原前端提交 403；重建前端容器后提交 200；查 `act_hi_taskinst` 确认任务已结束，而 Case 行仍无交易外键。
- 本地嵌套样本流程 `304eabd9-d46a-11f1-a7f8-86871052c760`：页面 Case 区域保留 `case-only-1226`，交易区域为空。Save、Approve / Confirm 均通过，查库确认 Case 行业务字段和归属未变，兄弟任务仍存在。
- 该样本的兄弟任务 `312b28e7-d46a-11f1-a7f8-86871052c760`：在交易嵌套区域新增记录，保存弹窗后立即可见；主键已预分配。第一次补测捕获漏入 canonical store 的 403，修正双外键解析后同任务提交 200。
- 最终数据库：原 Case 行一条，仍无 `party_id`；新交易行 `MiFile-000015` 一条，`party_id` 仅指向 Bob 的交易。流程 COMPLETED，无重复行。浏览器 pageerror 为 0。

本地载体为 `p3-mi-dual-binding-test`。旧载体是顶层交易表、只读参与者，与报告的嵌套可编辑结构不符；仅通过正常 DW API 调整此专用测试单元的草稿并发布新版本 1.0.5，未修改旧快照。旧载体中未声明嵌套写入时的 Save 403 不作为本次根因证据。

## 截图

- [原前端提交 403](../../frontend/user-portal/verification-screenshots/2026-10-30_1226-before-submit.png)
- [Case 保留、交易区域为空](../../frontend/user-portal/verification-screenshots/2026-10-30_1226-after-rows.png)
- [交易区域新增记录](../../frontend/user-portal/verification-screenshots/2026-10-30_1226-transaction-added.png)
- [Save / 提交后的页面](../../frontend/user-portal/verification-screenshots/2026-10-30_1226-after-submit.png)

## 构建与检查

- `pnpm run build`：通过；最终构建后重建 `user-portal-frontend`，容器正常启动。
- MI 单元门禁：32 个文件、248 项通过。
- 全量测试：修复前 2470 通过、1 失败；最终 2481 通过、1 失败。唯一失败仍为 `viewDetailForm.test.ts / resolveLookupDisplayValues` 的原有展示值断言。
- Typecheck 未全绿。在隔离 HEAD 副本与当前工作区分别执行并比较，改动文件没有新增类型诊断；仓库已有大量类型错误，不能将 build 成功等同于类型检查通过。
- `git diff --check`：通过；增量检查未发现凭据或真实业务个人资料。

## Code-review 就绪

结论 **CONDITIONAL**。当前改动自审未发现具体 Blocker / Major；后端权限、FU 包结构、导入导出和发布快照格式均未改变。变更依赖配置和行身份，未硬编码 Credit Card 表名或字段名。完整 MI 截图门禁及远程原表单仍有以下缺口，未宣称 PASS。

本轮 staged 复审、两项验证脚本 Minor 的处置及重跑结果，见 [代码审查报告](2026-10-30-multi-binding-403-code-review.md)。本轮再次确认 Save / Complete 业务成功标记、数据库归属不变和兄弟任务保留。

## 未验证项

- 远程 Dev 的 Credit Card 原任务、原表单及 Request Approval / Request Complete 两个实际按钮，未部署或逐一点击。本地覆盖了 Save、Approve、嵌套新增和查库；不能替代远程两按钮验收。
- Transaction Assignee 来源入口、历史只读 / My Request 全矩阵、双用户并发隔离未全部实测。
- 新建 / 导入 FU 完整页面矩阵、手工构造非法只读 FK 请求未另行验证；本轮未修改后端校验。
- 完整 `pnpm run regression:mi` 已执行但未通过：自动发现载体无 attachment 样本；固定历史隔离样本未通过门禁的 pinned synthetic process 校验。尝试新建 MI 载体时，现有 clone 报 `Action form reference is not present in the Function Unit package`。未修改这些范围外问题，六组专项截图不计通过。
- 复审时找到另一套可登录的隔离 MI 载体，严格门禁仍因未处理 Details 显示其他行主键失败；已在未修改 HEAD 的前端、同一后端与数据上复现相同行为，确认是既有缺陷，未混入本次修复。
- 新交易行已在真实嵌套页面验证；旧载体的顶层交易 Add 缺父上下文会被既有校验拒绝，不作为本次新增验收。

本地提交状态以 Git 记录为准；未推送或部署远程环境。
