# Code Review：初始个人分派异常观测

## 结论

CONDITIONAL。本批只补 Start 直接进入 MI、当前源表单显式 Batch Assign 的创建后异常观测和三语提示。不是完整 A38，也不是全 P4 放行。独立最终 staged 重审无Blocker/未接受Major，允许工程窄批提交。

## Findings / 逐条处置

- Major（已修）：合成 FU50048 `batch_verify_initialfail1017`，PROCESS50749/binding51066、TASK50750/binding51068，个人-only，Start直接MI。显式测试旗标加入 assignment listener，将刚设置的个人目标清空（null条件防递归），DW校验0错误/警告、Deploy成功。真实任务创建后无人办理，旧启动接口却success，页面绿色“Process submitted successfully”。反例实例 `049ebfe4-c9cc-11f1-b5f7-36c1412e9ef7` 保留，没有取消、修复或改派。
- 根因：创建前资格检查不能证明创建后的真实任务仍拥有目标；Start没有结果观测。现从同源严格预检成功集合生成节点/全个人模式计划，TX1/TX2完成后一次只读查询，按实际实例/定义/节点确认缺主任务，返回固定reasonCode及taskId，不返回人员或原异常。
- 方案审查（已收窄）：现有 `getProcessInstanceTasks` 是轻量列表，不含逐行 `_currentItem`。不能拿虚构带rowKey的单测声称真实逐行 VERIFIED。删除原型中的逐行匹配/VERIFIED/候选比较，只返回 FAILED 或 UNCONFIRMED；正常有办理人也不代表行目标已全部核实。没有新增逐任务HTTP详情循环，也没有返回完整变量袋。
- 角色/混合模式节点无assignee不能认定异常。只有严格集合在该节点全部为个人，且实际taskId、pid、definition、node一致时，null/空/空白办理人才明确 FAILED；其他情况、任务缺失、分页不全、查询失败均 UNCONFIRMED。失败不throw，不撤销此前动作，不自动重试/补偿引擎。
- UI保留 firstStepError 的原语义，独立分派提示不得借用“首步未完成”。新警告明确实例已创建、不要重复发起，不再同时显示成功toast。`duration: 0` 只表示当前会话不自动消失，不是持久状态。

## 范围 / 触发矩阵

命中：Portal启动/MI、配置驱动、错误边界、DTO只增字段、三语及UI截图、安全隐私、批量查询、提交前审查。无表结构、平台共享jar、包格式、部署配置或分派算法变化。新增辅助类独立承载编排，原启动大文件少量接线。普通无计划请求不增加查询/提示，不改旧首次人工任务Complete链。

## 修复验证：红→绿

- 实际脚本 `verify-batch-initial-mi-runtime.mjs` 先在故障FU复现真实assignee断言失败；再用 `verify-batch-initial-mi-outcome-ui.mjs` 走真实保存草稿→加载→点击Submit Application。旧接口缺新结果，期望FAILED断言失败；截图 `2026-10-17_initial-outcome-before.png` 确认绿色成功提示。没有拦截或伪造接口响应。
- 最终观测8项测试：隔离副本临时关闭缺主检测后1失败/0错误，其余7通过；恢复后8项全部通过。包括空白、别的节点/实例/定义、分页截断、查询异常、旧路径零查询、角色/混合不误报。预检32项另加返回源计划及关闭状态空计划断言。
- 隔离Portal package广集43类519项0失败/错误/跳过；最终isBlank增量再次package46项全部通过。独立reviewer核7份Java SHA并独立重跑46项，0失败/错误/跳过。
- `pnpm -C frontend/user-portal exec vite build`：4183模块，39.33秒通过。`vue-tsc --noEmit`仍383项既有错误；本次改动文件命中start.vue258事件签名，其在先前 `hermes-batch-role-display-typecheck.log` 同位置同错误，总数同为383；不是新错误。不能报告全类型检查PASS。
- 最终jar SHA256 `938E90B3363B2EF60F1F35448CCAE2960F8C3AE6CD1CF8A92173F5E445D8794F`；Portal镜像 `sha256:5d1e50b0c6f775759731db36be504fb549b73cbc231c1650abad4f74f64323de`，前端 `sha256:ebf7ad7805b42347c2508c920096e70c0970e4969e26010e848969e292a76960`。healthy后才复测。前端compose依赖也重建同jar引擎，现镜像0abf53a2，产品引擎源码未改。

## 实际结果 / 截图

- 故障FU最终实例 `ae47acad-c9cc-11f1-a0ed-d2843fd6a5be`，实际任务 `ae5392d5-c9cc-11f1-a0ed-d2843fd6a5be` / `ae4bcaa1-c9cc-11f1-a0ed-d2843fd6a5be`；启动仍success且实例确实已建立，独立结果FAILED，两个固定PERSON_TASK_UNASSIGNED定位，页面只警告，pageerror0。
- 正常既有FU50047 `batch_verify_initialmi1017` 实例 `be7c895e-c9cc-11f1-a0ed-d2843fd6a5be`，实际两任务 `be7fe507-c9cc-11f1-a0ed-d2843fd6a5be` / `be7d25e2-c9cc-11f1-a0ed-d2843fd6a5be` 均为正确目标；接口如实UNCONFIRMED，不误报缺主，也不虚构逐行核对通过，页面对应未核实警告，pageerror0。
- 固定只读psql核ACT_RU_TASK：旧失败/新失败/正常实例分别为“任务数2、非空办理人数0 / 2、0 / 2、2”。没有依赖HTTP200证明结果，没有目录成员写入或任务补偿。
- 截图绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`，保留 `2026-10-17_initial-outcome-before.png`、`2026-10-17_initial-outcome-failed.png`、`2026-10-17_initial-outcome-failed-narrow.png`、`2026-10-17_initial-outcome-unconfirmed.png`、`2026-10-17_initial-outcome-unconfirmed-narrow.png`。截图只截提示，无姓名/邮箱；人工查看1366桌面及600窄屏文字/关闭按钮无覆盖或截断。

## 生命周期 / 性能 / 安全

本批不改变Save/Load、Export/Import、Clone、Version/Rollback、Admin两条Import或部署产物格式；沿既有已冻结契约，无需新增remap。本批新建故障FU与既有正常FU两样本真实运行；不能由此宣布全部FU生命周期PASS。预期计划来自本次source严格成功集合，不由客户端标记、字段名猜测或全实例其他表单开关推导。

新增单次既有列表查询，限定100条；totalCount不全即未确认，未声称200行完整核对或性能提升。不引入逐行HTTP/SQL，也不把不可变快照送入引擎或浏览器。例外捕获仅保护已成功的引擎动作，返回明确未确认，不吞错冒充成功。新故障fixture旗标必须显式开启且assignment模式，默认设计不变。

## 最终门禁 / 残余风险 / 范围外

最终新镜像 `pnpm -C frontend run regression:mi`：234单测及6个浏览器场景全部通过，截图保留在两端既有目录中的Portal `verification-screenshots/2026-10-17_*`。独立最终staged15文件完整复审无新增Blocker/未接受Major，diff检查及两脚本node检查通过；270条非空新增行4类增量敏感扫描0命中，独立目视窄屏/桌面截图文字及关闭按钮无遮挡、无PII。报告仅补本签核结果，无产品再改。

普通Complete/首人工Complete后的观测、角色精确成员、错误非空办理人、逐行身份和多用户、刷新后GET持续可见性仍未闭合，不能宣称A38完成。当前所有有效direct初始计划都可能返回UNCONFIRMED；关闭提示不会改变实际任务，禁止把此状态当作重发许可。后续应使用安全、受权、裁剪的批量观测契约补足行身份，而不是N+1详情或全量变量。

既有383类型错误、邮件调度缺少 `we_action_email_deliveries`、My Applications旧process-key BPMN读取404均作为范围外记录，不夹带修复或报告全运行日志干净。其余P4门禁沿前批报告保持开放。
