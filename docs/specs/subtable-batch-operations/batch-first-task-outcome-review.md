# Code Review：首个人工任务自动完成后的批量分派结果

## 结论

CONDITIONAL。本批只补齐首个人工任务自动完成后的范围内个人缺主反馈，不代表 A38 或 P4 完成。最终完整 MI 回归已通过，独立完整 staged 重审无 Blocker/未接受 Major，允许提交本窄批。

## Findings / 逐条处置

- Major（已修）：新建隔离合成 FU50050 `batch_verify_firstoutcome1017`（PROCESS50753/binding51074、TASK50754/binding51076），Intake→MI、个人-only；显式测试 listener 清空实际任务办理人。旧实例 `2302a6dc-c9d1-11f1-a0ed-d2843fd6a5be` 生成两条无办理人任务，Start 响应没有结果观察，UI 却显示绿色提交成功。初始 Start→MI 与普通 Complete 已有观察，首人工自动完成的返回计划被丢弃。
- 修复：`injectMiCollectionForFirstTask` 返回同一次严格冻结集合构造的节点/表/完整行键计划；Start 仅在引擎明确完成成功后保留服务器内存计划。原 TX2 记账结束后复用 `SubTableBatchCompletionObservation` 的两条 bulk 查询及范围联结。未知/失败查询明确 UNCONFIRMED；不重试 Complete，不补偿或改派任何任务。
- 独立审查 Major（已修）：引擎成功后 Owner/表单快照/下一任务读取失败，旧 catch 会填 firstStepError，让 UI 遮掉批量观察并提示首步未完成。新增“引擎成功、captureTaskFormSnapshot 抛错”反例。非空计划只在已成功的引擎动作后赋值，catch 此时保留计划而不伪报首步未完成；最终观察必为 FAILED 或 UNCONFIRMED，仍是警告而不是绿色成功，原异常日志保留。无计划的旧路径未改，不夹带全平台启动状态重构。
- Blocker：无已知项。其余 Major/Minor/Question：独立完整 staged 重审无新增项。测试不重放完成动作、不维修失败任务、不修改目录成员。

## 范围 / 验收映射 / 触发矩阵

只修改 Portal 首步调用链、其严格集合返回值、回归测试和已有验证脚本的一条断言；对应设计 A30/A35/A37/A38 的一个入口。没有新接口、DTO、数据库字段、共享 jar、配置格式、i18n、UI 布局或任务操作。

适用实现自主、修复验证、配置驱动、SAST、代码审查、MI 隔离与完整回归规则。观察计划不进入 formData、Flowable、客户端响应或邮件。前端既有三语 Start 警告复用；脚本额外禁止已完成首步出现 firstStepError。

## 编译 / 红→绿 / 测试

- 真实 UI 红：同一验证脚本在旧镜像启动新故障实例，期望 FAILED，实际 undefined，断言失败；截图 `2026-10-17_first-outcome-before.png` 亲眼确认绿色成功。只读 PostgreSQL 确认 2 条实际任务、0 条非空办理人。
- 新回归红阶段一：隔离构建副本临时去除成功后的计划赋值，`ProcessStartBatchFirstOutcomeTest` 5 项中 1 失败、0 错误；恢复赋值。
- 新回归红阶段二：增加成功后快照失败反例，修 catch 前 6 项中 1 失败、0 错误（firstStepError 不应是 Synthetic snapshot failure）；修后通过。
- 隔离 Maven 副本执行 `mvn -pl backend/user-portal -am package`，选择 `ProcessStart*Test,InternalEmailProcessStartComponentTest,SubTableBatch*Test,MiCollection*Test,TaskCompletionObservationTest,TaskProcessProperties,SubTableWriteIsolationTest,TaskApprovalCompletionSubTableGuardTest,SubTableBindingScopeGuardTest,MiSubTaskSubTableRowMergerTest,WorkflowEngine*Test,ActionPostEmail*Test`，并传 `-Dsurefire.failIfNoSpecifiedTests=false`：47 个实际 Surefire 套件、552 项测试、0 failure/error/skip。
- 独立 reviewer 核 repo→副本 5 份 Java SHA 一致并重跑 package：首步结果6、集合接线4、严格MI集合33、范围观察8、初始观察8，共59项0 failure/error/skip。最后注释澄清亦已入 jar。
- `pnpm -C frontend/user-portal run build`：4183 模块、21.53秒；已有大 chunk 提示未被压制。前端 TS 源未改；此前全量 typecheck 的383项既有错误仍未解决，不能称全类型检查 PASS。
- 最终 Portal jar SHA256 `7CBF8BC1DBF749852533D4B0C40B79CD8216CA4C67F378CF6DD3562D0812D570`。仅 `--no-deps` 重建并启动实际 Portal 后端与前端，健康后才测试。后端镜像 `sha256:e0599547304560b25699c1a5ac63cdd7beebab88ad01d073794340243a8381c5`，前端 `sha256:666a46b9cd3219d34e5678f2db8a53711daaacd93ce4f78208dd3b00d09ef7a0`。启动日志无 Unresolved compilation / APPLICATION FAILED。

## 真实运行 / 截图

- 同一故障设计新实例 `17151a2b-c9d2-11f1-a0ed-d2843fd6a5be`：首步完成，两条实际任务 `1726a6d1-c9d2-11f1-a0ed-d2843fd6a5be` / `1725bc6d-c9d2-11f1-a0ed-d2843fd6a5be` 均缺主，响应 FAILED 正确定位本范围任务；没有 firstStepError，UI 无绿色成功、明确不要再次提交，pageerror0。
- 既有正常 FU50045 `batch_verify_firstmi1016` 新实例 `1fc7839a-c9d2-11f1-a0ed-d2843fd6a5be`：两条任务 `1fd87300-c9d2-11f1-a0ed-d2843fd6a5be` / `1fd565bc-c9d2-11f1-a0ed-d2843fd6a5be` 实际办理人正确，返回 UNCONFIRMED 而非错误缺主；不伪称完整逐行分派核实，pageerror0。
- 固定只读 psql 核对旧故障/新故障/正常三实例分别为 2/0、2/0、2/2（实际任务数/非空办理人数）。没有用 HTTP200 或成功 toast 代替任务证据。
- 既有 `verify-batch-first-task-runtime.mjs` 另验证同正常 FU：无效目标实例 `2e5a9753-c9d2-11f1-a0ed-d2843fd6a5be` 留 Intake；合法目标实例 `2e8d4038-c9d2-11f1-a0ed-d2843fd6a5be` 按两份 canonical rowKey 正确生成2任务，伪造单条 collection 被替换，完整批量快照未进入引擎。
- 截图绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`：保留 `2026-10-17_first-outcome-before.png`、`2026-10-17_first-outcome-failed.png`、`2026-10-17_first-outcome-failed-narrow.png`、`2026-10-17_first-outcome-unconfirmed.png`、`2026-10-17_first-outcome-unconfirmed-narrow.png`。仅截图提示，无姓名/邮箱；人工检查桌面及600窄屏文字和关闭按钮无覆盖/截断。

## Function Unit 通用性与生命周期

通用性：冻结源表单、实际部署、严格准备行键驱动；无 FU/字段名业务分支。新建故障 FU + 已有正常 FU 双样本真实验证。Save/Load、Export/Import、Clone、Version/Rollback、Admin 两条 Import、Deploy 的格式均未改，本批不需要 remap；不冒充这些完整生命周期已重测。Portal 旧无快照/无可达 Assign 路径返回 null，无新增结果查询；原设计/渲染 parity 不变。

## 性能 / 安全 / 兼容

每次有计划的成功首步只复用2条既有有界 bulk 查询，无逐行 HTTP/SQL。live任务与MI身份按实际 pid/definition/node/table/完整键联结，不用 ETI 缓存办理人断言结果，不泄露其他范围任务ID。分页/身份无法证实时未确认；不声称200行压力或吞吐已通过。失败前不发表计划，完成后观察失败不回滚已成功动作。

无新 Controller/URL/SQL/XML/auth 代码、敏感日志或凭证；普通请求和旧 void 调用可忽略返回值，所有服务内调用已搜索。保留既有响应形状和三语提示，不新增权限。

## 未验证 / 残余风险 / 范围外

完整 A38 仍缺角色精确候选、错误非空办理人、刷新后结果持续可见性；当前观察不会给 VERIFIED。首步无计划的后成功记账失败旧提示缺口仍开放，未扩大修改。初始非直接路径、启动本地失败的持久隔离、hydration来源及其余既有 P4 门禁保持开放。

已有383 TS 错误、邮件调度缺 `we_action_email_deliveries`、旧 process-key BPMN 404 不属本批，不夹带修复。截图、合成失败任务均保留，不作自动清理。

## 最终门禁

新镜像完整 `pnpm -C frontend run regression:mi`：234 项单测、6 个真实浏览器场景全部通过，截图保留。独立完整 staged 7 文件重审无 Blocker/未接受 Major，原 Major 已按反例处置并重审闭合；`git diff --cached --check`、脚本 nodecheck 通过。173 条非空新增行、文件名及拟提交说明的4类增量敏感检查0命中；独立查看 FAILED/UNCONFIRMED 窄屏截图无PII或遮挡。日志证据在本机 TEMP 的 `batch-first-outcome-{red,post-red,package,frontend-build,mi}.log`。最终报告只补真实门禁结果，产品代码未再改；整体仍 CONDITIONAL，不启用生产配置。
