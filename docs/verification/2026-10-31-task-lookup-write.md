# 普通任务 Lookup 缓存误提交：29055528

## 结论及范围

任务 `29055528-d4cf-11f1-892b-4a41fd8100f9` 的自动保存报错已取得同页面红绿证据。没有批准、拒绝或完成任务；业务行及原经办人保留。

用户指定的 `common_SELF_0731_AP` 不存在，实际同名主线为 `common_0731_SELF_AP`。本地及远端均为 `72f21d116`，与当前功能分支合并。新前端部署后仍复现，故合并本身不能解决该错误。

## 复现与根因

- 原运行脚本 `index-DK4D2xF8.js`，Retry save 返回 403，页面显示未保存告警。
- 合并后的脚本 `index-BMMA6XDZ.js`，同任务自动保存仍失败；未把旧页缓存误当新代码。
- 本任务是普通任务：没有 `we_assignment_task_states` 登记；数据库只保存实际的两份 DW store，而页面为 Host Lookup 加载了 `rt:sys_users` 展示缓存。
- `TaskSubTableSubmission.prepare` 对普通任务 Save/Complete 同样校验当前表单绑定。`rt:sys_users` 没有对应可写绑定，与业务 baseline 不同，因此拒绝是正确的。
- 前端 `useTaskForm` 只在 batch 或受保护任务启用可写投影，普通任务把展示缓存也当写请求提交。这是前后端覆盖范围不一致，不是需要添加 sys_users 写权限。

## 修复

所有 Task payload 统一按当前表单声明的可写绑定投影；不按 FU、字段名或 `rt:sys_users` 特判。显式空数组、删除声明、绑定 scope 和行版本继续保留。Save 成功时合并本地展示缓存，避免因未提交 Lookup 而擦掉显示值。后端校验未放宽。

同一缺陷维护已有 Issue（合并重编号后为 #1728），不另造重复记录。

## 同任务验证

- 修复后脚本 `index-ByJLMahD.js`，同任务自动保存返回 **200**；页面没有未保存告警，Host 仍显示 System Admin。
- Portal 日志：2026-10-31 10:13:04，`POST /api/portal/tasks/29055528-d4cf-11f1-892b-4a41fd8100f9/submit -> 200`。
- 同一单据 `28cbf534-d4cf-11f1-892b-4a41fd8100f9` 的 `lock_version` 从 **2→3**；参与行 `Test-000004` 的 `_wsRowVersion` 为 **1**。
- 前后数据库比较：参与行数 **1→1**，附件行数 **0→0**；排除系统审计及版本字段后，业务行完全相同；业务存储中没有 `rt:sys_users`。
- 原任务仍在 `act_ru_task`，没有推进流程。未通过删除缓存业务数据、更新流程定义或更改角色来回避问题。
- 合并后的 Engine/Portal 正常生产镜像更新并健康后，再次刷新同任务：10:17:49 保存仍为 200，`lock_version` **3→4**、行版本 **1→2**，原活动任务仍在。

截图保留本地：

- `frontend/user-portal/verification-screenshots/2026-10-31_assignment-lookup-before.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_assignment-lookup-after-merge-red.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_assignment-lookup-after.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_assignment-lookup-rows-preserved.png`

## 自动回归及合并防回退

普通/受保护双参数回归先红后绿：原实现 **1 failed / 5 passed**，修复后四文件 **31 passed**。包括实际保存错误清空、Lookup 页面缓存保留、READONLY 投影、删除到空、改派版本与在途 autosave。日志 `tmp/assignment-20261031-ordinary-cache-{red,green}.log`。

合并冲突保留双方启动契约：Engine 仍走互斥启动组件，并补回 `nextTaskName`；Portal 空 outcome 不覆盖 TX1，已接受首步完成仍由账本独占节点和 Owner 投影。新增 Engine 返回值断言及 Portal READY/PENDING_SYNC/SYNC_CONFLICT 三态不覆写、legacy 空节点保留六项测试。

合并提交：`2de46b1c3`。最终 `mvn -pl backend/workflow-engine-core,backend/user-portal,backend/developer-workstation -am package` 选择 Assignment/Task/SubTable/ProcessStart、EmailMonitor、HermesMaster 及 pinned start 相关测试，Common 99、Engine 547、DW 33、Portal 714，共 **1393 项，零失败/错误/跳过**。完整命令和输出位于 `tmp/assignment-20261031-merged-package-green.log`。初轮有四项测试因 mock 缺少新增 reader 依赖而失败，已补真实返回值断言并重跑；未在产品代码增加空依赖降级。

Portal 正常 Vite 构建、Dockerfile.local 镜像及 Compose 前端重建完成，容器镜像 ID 与新镜像一致；Engine/Portal 同样重新打包、构建及重建。DW 相关代码做了编译与专项测试；本轮没有对上游无关的 Automation/Hermes 聊天功能重做端到端验收。

最新产物下完整 `pnpm -C frontend run regression:mi`：**32 文件 250 项单测、全部 6 个浏览器截图场景通过**，日志 `tmp/assignment-20261031-merged-mi-gate-green.log`。门禁使用独占隔离 fixture。首轮截图启动发现隔离登录 dist 缺少应由容器入口生成的 `runtime-config.js`，返回 HTML 引发 SyntaxError；仅在忽略的测试 dist 补回空默认配置后重跑成功，未移除浏览器错误检查，也未修改正常登录服务配置。

合并冲突与本次修复均经独立重审 **PASS（上述限定范围）**，未发现未关闭 Blocker/Major。审查者另行核对日志及前后 JSON。合并后红截图停在页顶，403 证据依据实际页面状态和服务日志，不能单凭该截图判断。MI Fill 弹窗自行构造 payload，不是本次两处修改覆盖的入口；未宣称所有自定义弹窗和所有 FU 均已运行验收。
