# MI 阶段与办理人展示统一

## 范围与契约

基线 `49e28c4f6`。用户确认统一 My Requests 与任务的列表、详情：MI 内层任务显示 `multi: sub stage`；未领取的 BU + Role 任务显示实际 BU 与全部 Role 名称，其余显示实际候选人；领取后显示实际领取人。首页最近申请与 Audit 共用展示字段同步消费。

展示只增加 `currentAssigneeDisplay` / `assigneeDisplay`；原 Owner、候选池、办理权限、经办责任、冻结版本和业务数据不由展示链修改。权限碰撞回归覆盖“外部用户显示名等于 BU + Role 文字”不能成为参与者。多个活动阶段以 `阶段 → 办理人` 配对。

Engine 从任务冻结定义和运行时分派状态读取事实；ROW / MANUAL 的 BU、Role 读取当前源的批量最小字段快照，既有资格校验仍使用原单项接口。其他候选人来自实际 Flowable 候选池，展示读取不重算或写候选池。备选范围按当前 poolSource 与冻结备选配置读取。目录名称失效时保留配置代码。

## 红绿证据

- `tmp/task-presentation-stage-red.log`：旧实现两个 MI 阶段断言失败；最终 Engine 阶段测试通过。
- `tmp/task-presentation-front-red.log`：旧格式化器忽略 BU + Role / 实际本人姓名；最终同两例通过。
- `tmp/task-presentation-security-red.log`：新增内部端点缺少精确 Security matcher，合法服务 token 被拒；最终真实 FilterChain 测试通过，普通 JWT 不能代替内部 token。
- `tmp/task-presentation-portal-package.log`：新增 Application 文本筛选回归复现旧 Owner `multi` 混入；`tmp/task-presentation-query-final-package.log` 中同例通过。
- 同单据 `71dde005-d4d3-11f1-89d4-1a43c7588052` 修改前页面两个字段均为 `multi`；新镜像下为 `multi: sub form2`、`共享财务中心 + Department Manager`。

## 构建与测试

- Engine / Common 受影响 package：554 / 97 项，零失败、错误、跳过；日志 `tmp/task-presentation-affected-package.log`。该轮 Portal 旧 SQL 断言 3 项失败，不能将整轮报告为通过。
- Portal 最终完整受影响 package（含 TaskQueryProperties、TaskProcessProperties）：1230 项通过，Common 97 项通过；日志 `tmp/task-presentation-portal-final-package.log`。
- 查询收尾变更另行重新 package：33 项通过，覆盖 Application 展示筛选、My/Audit SQL、目录真实 PostgreSQL 解析；日志 `tmp/task-presentation-query-final-package.log`。不重复累加上述测试数。
- 前端格式化器 7 项通过；列表视图回归见 `tmp/task-presentation-front-views.log`。
- 正常 Vite build、Dockerfile.local 与 Engine/Portal 正常镜像均重新构建并重建服务；容器镜像 ID 与新镜像一致，后端健康。
- 完整 MI 门禁：32 文件、250 项单测及全部 6 个浏览器截图场景通过，日志 `tmp/task-presentation-mi-final-gate.log`。首轮隔离 Portal 与 Engine 同时启动使既有客户端初始化 available=false，任务权限读取失败；Engine 健康后重启隔离 Portal，未绕过 fixture 保护或权限校验。
- 最终首页一行共用展示消费修改后重新构建前端、镜像与服务；Playwright 再验证原申请详情，断言 BU + Role 文本恰好一处并保存截图，日志 `tmp/task-presentation-playwright-final-screenshot.log`。

## 页面与数据库核验

以原用户在共享财务中心 / Department Manager 工作台测试：

1. 申请详情和任务详情都显示新阶段路径和 BU + Role。
2. 页面 Claim 后，两处 Current Assignee 均为 `liam`；数据库 native assignee 为该用户 ID。
3. 页面 Unclaim 后恢复 BU + Role；原活动任务仍为 `9ad786ec-d4d3-11f1-89d4-1a43c7588052`，没有 Approve、Reject 或流程推进。
4. My Requests、To Do 列表均显示相同范围；同页旧 MCY FU 也显示 BU + Role，并行阶段保留对应关系。普通直接分派显示具体人名。
5. 仅展示读取前后原申请仍 RUNNING、current_node=sub form2、lock_version=5，原 candidate_users 未改变。显式领取/取消领取测试后版本正常增至 9，最终 assignee 为空、候选池与阶段保持原值；这些测试操作有正常审计记录。

本地截图目录 `frontend/user-portal/verification-screenshots/`：

- `2026-10-31-task-presentation-before.png`
- `2026-10-31-task-presentation-application-after.png`
- `2026-10-31-task-presentation-task-unclaimed.png`
- `2026-10-31-task-presentation-task-claimed.png`
- `2026-10-31-task-presentation-application-claimed.png`
- `2026-10-31-task-presentation-my-requests-list.png`
- `2026-10-31-task-presentation-todo-list.png`
- `2026-10-31_task-presentation-final.png`（最终 Playwright）

## 独立审查与限制

完整展示接线及前后端批量接口经独立终审 PASS。两项 P2 已处置并重审：Application keyword 不再匹配隐藏旧 Owner / 候选人，原生搜索保留其他列与发起人别名；Application contains/notContains 仅按新展示文字比较，USER eq/ne 仍按真实身份别名。Task 列表既有 raw ID/name 兼容搜索保留。没有未关闭的权限 finding。

普通分页保持 SQL 分页，本地 My Requests 实测 144–320ms。按展示字段排序/筛选或 API keyword 需要遍历当前用户有权查看的记录再分页：约 217 条申请，关键词查询实测 897–1503ms，超过常规 500ms 目标；本次未承诺生产规模下的搜索延迟。500+ 条分页完整性有测试，不代表负载测试。My Requests 前端工具栏既有搜索仍按 Process Title quick filter；未将这项范围外交互改成全列搜索。

未重新运行全平台所有模块测试；未逐一运行所有 FU、所有备选分支的页面验收。Audit、完成任务与动态源边界通过专项测试；本轮人工页面证据主要覆盖用户指定 FU 与既有 MCY FU。首页 Team Requests 为独立团队 DTO，未改变其既有业务展示。
