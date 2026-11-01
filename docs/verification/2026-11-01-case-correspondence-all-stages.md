# FU50125：各stage直接显示Case Correspondence

## 结论

已在MCY dual binding 1101 1793499506034（FU50125）配置并发布1.0.3。Assignment表单和Investigation/Approval/Mark Completed共用的Sub Case表单，均在底部直接显示只读Case Correspondence，不需要切回Case Submission。结束后的默认General也显示。

本轮6份新请求、34个任务stage视图检查全部通过，六组均从New Request、草稿重开、Batch人员分派，按批准/拒绝/直接分支走到引擎结束。每个stage只显示本请求的一条Case note，没有混入两笔Transaction note；Case字段内容、顺序和行版本在完成前后不变。

## 配置范围与审查

- 新增Assignment表单50898的只读SUB绑定51464、Sub Case表单50899的只读SUB绑定51465；均指向既有Correspondence表50593，按已声明的Case过滤FK82247/related_case读取，bindingMode=READONLY、structuralFk、FULL。
- 两个画布各放置一个Case Correspondence子表，明确禁用Add/Edit/Delete；列为Correspondence ID、Correspondence note、Case ID。
- Case Submission的编辑配置不变，现有绑定、字段权限、Batch设计及BPMN完全不变，已对发布后配置逐项读回核对。
- 发布94ff2c87-7650-4f23-9927-0bbd32a7d536成功，新请求明确冻结为1.0.3。没有修改平台代码或增加任何MCY/表名/列名判断；这里只使用设计器标准配置。
- 本轮只调整FU50125，FU48和50126不作这项布局变更。

旧请求2541a42c-d5bd-11f1-8c70-6e7849f291cc及用户任务a171c92f-d5bd-11f1-8c70-6e7849f291cc仍冻结1.0.2，不会自动使用新布局，也未被本轮推进。

旧版本隔离也已读接口核对：带已授权processInstanceId获取其原catalog，仍没有新绑定51464/51465；缺少该流程上下文时旧catalog请求返回403，未绕过此保护。证据.codex-1101-case-placed-old-version-context.log。

## 六份完整流程

- 批准／直接完成：`9ac3edaa-d5be-11f1-8c70-6e7849f291cc`；6个办理stage逐页核对Case只读显示，最终0活动任务。
- 拒绝／批准：`f10fc1af-d5be-11f1-8c70-6e7849f291cc`；6个办理stage逐页核对Case只读显示，最终0活动任务。
- 直接完成／拒绝：`175f08f6-d5bf-11f1-8c70-6e7849f291cc`；5个办理stage逐页核对Case只读显示，最终0活动任务。
- 批准／批准：`3af65da5-d5bf-11f1-8c70-6e7849f291cc`；7个办理stage逐页核对Case只读显示，最终0活动任务。
- 拒绝／拒绝：`625540a6-d5bf-11f1-8c70-6e7849f291cc`；5个办理stage逐页核对Case只读显示，最终0活动任务。
- 直接完成／直接完成：`8630f057-d5bf-11f1-8c70-6e7849f291cc`；5个办理stage逐页核对Case只读显示，最终0活动任务。

## 浏览器和数据判据

每份New Request有两笔Transaction各自的Correspondence及一条Case Correspondence；草稿重开、A编辑、B删除替换、Case编辑，再保存重开。Task中先Claim，逐页核对Case表格恰好1行、没有Add/Edit/Delete按钮，再Batch人员分派并办理后续节点。各请求使用不同Case note与Case主键，检查不是只看同名标题。

每次完成都检查成功响应body和持久化推进，Case/Transaction三条Correspondence字段、顺序及_wsRowVersion与基线逐值相等；历史逐taskId匹配实际动作，节点/出口颜色符合执行历史，pageerror=0。最终6份均有引擎结束时间、无活动任务，6个Case主键及Case行主键均不同；Case行仅有本Case FK，交易行仍各属自身交易。

结束后重新打开My Request的默认General，直接检查Case子表1行可见，不通过点击Case Submission来代替默认布局验收。

## 截图

目录frontend/user-portal/verification-screenshots/：
- Assignment：2026-11-01_case-placed-9ac3edaa-0-case-view.png。
- Investigation：2026-11-01_case-placed-9ac3edaa-1-case-view.png。
- Approval：2026-11-01_case-placed-9ac3edaa-3-case-view.png。
- Mark Completed：2026-11-01_case-placed-9ac3edaa-4-case-view.png。
- 默认General结束重开：2026-11-01_case-placed-9ac3edaa-default-general.png。

## 证据、失败记录及边界

配置前备份.codex-1101-case-placed-before-*；配置读回审查.codex-1101-case-placed-audit-ipv4.log；发布.codex-1101-case-placed-published.json；六份结果.codex-1101-case-placed-results.json，主日志.codex-1101-case-placed-suite.log及首份flow-0-resume.log。本地Playwright驱动frontend/scripts/_1101-case-placed-*.mjs为忽略文件，不作为产品代码提交。

首次Case标题选择器错误地要求单绑定标题带(related_case)后缀，任务仍停留Assignment、尚未完成。实际截图表格正常；修正测试定位为Case Correspondence后，同一实例完整通过，数据行数/内容/只读断言未放宽。另有DW登录超时，IPv4正常登录后配置审查通过；未修改认证或超时保护。

本轮仅配置发布与验收文档，没有镜像产物或平台源码变动，因此不重复构建或重跑全仓代码测试。既有代码门禁状态继续见之前case-node-primary-identity报告，不宣称全仓无失败。

未验证：远程环境、独立双账号权限/并发、所有FU生命周期，以及未配置的Role/撤回/退回路径。测试由FULL developer办理。本轮六份仅针对普通分层双绑定FU50125，不把它当作显式额外FK副本50126的新布局验收。

Mark Complete动作配置仍为REJECT，最终REJECTED表示原动作语义；这里完成指引擎结束，不是将业务状态改成COMPLETED。

用户可从新建请求入口创建1.0.3的单进行复测。上述完整验证单均已结束，其申请详情与流程图节点仍可只读查看各表单。
