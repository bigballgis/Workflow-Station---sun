# Code Review 结果

日期：2026-10-30（香港时间）。模式：当前 staged changes，本会话复审；不声称独立双人审查。

## 结论

**CONDITIONAL**。

本次 403 修复代码未发现新增 Blocker 或未处置 Major；本地已复现的 Case / Transaction 归属问题通过页面与查库回归，可以按此修复范围本地提交。完整 MI 截图门禁仍因已在 HEAD 原版复现的 Details 主键串用缺陷失败，远程 Credit Card 原按钮未验证，不能写成全环境 PASS。

## Findings

### Blocker

- 本次新增：无。

### Major

- 本次新增：无。

### Minor

- R1，已关闭：`frontend/scripts/verify-1226-ownership.mjs` 的截图日期固定为 2026-10-30，未来执行会误标日期。已改为按 Asia/Hong_Kong 生成日期。
- R2，已关闭：上述脚本原先只断言 Save 的状态码，以及 Complete 后的任务状态，没有断言响应的业务成功标记。已补上 `success` 断言，并重新执行页面验证；保留数据库不变性和任务推进断言。
- 修正后重新审查完整 staged diff，未发现其他具体问题。

### Question

- 无待澄清的代码问题；验证范围限制见下文。

## 范围 / 验收映射

- 已有 Case 行缺交易 FK：服务端快照与配置主键 / 平台 UUID 判为已有行，跳过隐式 FK 补值；缺失、null、空字符串均有回归。
- 同表双 FK：归属优先当前 binding 的过滤 FK 与引用表，避免按整表字段顺序取第一个 FK。
- 新增交易行：预分配主键不等于已保存；当前关系或父行本地嵌套上下文仍可正常补 FK。已有前端新增、提交及查库证据。
- 读取：显式单 binding 也过滤；MI collection 不套用子行过滤；嵌套 MAIN 共享关系不拿交易主键过滤。
- 主表身份：MI 隔离保留画布外配置主键，避免 Case 区域被投影为空。
- Save / Approve / Reject 调用链：`useTaskActions.submitApprove` 在区分 APPROVE / REJECT 前调用同一个 `prepareBeforeComplete`，因此两者共享本次修复的补值链。静态调用链核查不替代原两个按钮的实测。
- 后端只读字段、资源归属、binding scope 校验未修改。

## 触发矩阵

- 已检查最小 diff、调用方、配置驱动、异常 / 空结果语义、MI 隔离、主表主键、截图与数据库证据、增量敏感信息。
- 新增配置持久化、表结构、接口协议、i18n：N/A，本次未变更。
- 没有提交本次运行的临时探针、类型基线目录或用户原有的 `manual-diagnostics/`。

## 编译与静态检查

- `pnpm run build`：本轮重新执行，通过。
- `node --check frontend/scripts/verify-1226-ownership.mjs`：通过。
- `git diff --cached --check`：通过。
- 全仓 typecheck 仍有旧错误。先前已用隔离 HEAD 副本对照，改动文件无新增类型诊断；不将 Vite build 通过描述成 typecheck 通过。

## 测试与运行证据

- 本轮 `pnpm run test:regression:mi`：32 文件、248 项通过。
- 当前完整代码的全量测试：2481 通过、1 失败；失败仍为修复前已有的 `viewDetailForm.test.ts / resolveLookupDisplayValues`，基线为 2470 通过、同一项失败。
- 本轮重新运行 `node scripts/verify-1226-ownership.mjs`：页面 Case 原行可见、交易区域无该 Case 行；Save 和 Complete 的响应 `success=true`；数据库 Case 业务字段和归属不变，当前任务结束，兄弟任务保留；pageerror 为 0。
- 新增交易行的真实 UI 与查库证据，见同目录 [修复验证报告](2026-10-30-multi-binding-403.md)。
- 完整 `pnpm run regression:mi` 在本轮找到的另一套隔离环境运行，未通过：未处理参与者的 Details 显示了另一条 People 行的主键，严格门禁因此拒绝通过。
- 为区分回归与旧缺陷，在隔离目录构建未修改 HEAD 的前端，仅替换测试浏览器读取的静态 HTML / assets；接口、账号、测试单及数据库均相同。HEAD 原版与当前代码都显示相同的错误主键，确认该失败在本次修改前已存在。没有修改生产容器路由或业务数据库来制造对照结果。

## Function Unit 通用性与生命周期

- 通用性：**静态 PASS**。产品代码只读取 binding、字段配置、快照和行身份，无具体 Credit Card 表名、业务字段名或 FU ID 判据。
- Save / Load、Portal runtime：本地双绑定载体页面与数据库验证通过；另一既有 MI FU 的完整门禁受到上述旧缺陷阻断。
- Export / Import、Clone、Version / Rollback、Admin JSON import、Admin deploy import：本次配置生产链 N/A。没有新增持久化属性、改包格式或改 ID remap；新增/导入 FU 的完整运行矩阵未验证，不宣称动态通用性全通过。
- Deploy / Activate：本地专用载体正常发布；前端新产物已构建并在前序验证中重建。远程部署未执行。

## 增量敏感信息

- 范围：staged 新增 / 修改的文本、文件名与截图；提交说明。
- 结果：未发现真实凭据、令牌或私钥。业务验证使用专用合成测试单；截图为本地合成账号。不是完整 SAST。

## 性能

- 未改变取数或增加网络请求；快照身份匹配为内存处理。未实测大数据量 P95，不宣称性能提升。没有把缓存、并行或其他性能改造混入本次修复。

## 残余风险 / 豁免

- 远程 Credit Card 原表单及 Request Approval / Request Complete 两个实际按钮未点击；用户已取消本地寻找同名 FU 的追加验证，回到本次报告问题的修复审查与提交。
- 原两来源节点、历史 / My Request 全矩阵、双用户并发、新建 / 导入 FU 全矩阵没有全部实测。
- 完整 MI 截图门禁和全仓类型 / 单测不能宣称全绿；本次提交范围为已验证的 403 归属修复，不是全仓 CI 放行或远程部署。

## 范围外问题

- 既有 Details 主键串用：严格 MI 门禁失败；当前版本和未修改 HEAD 在同一隔离测试单上均复现。没有在本次提交混入另一项 Link Form 兜底链修复。
- 既有全量单测、类型错误及原报告列出的远程验证缺口保留。

未推送或部署远程环境。
