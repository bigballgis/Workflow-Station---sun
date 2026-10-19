# Code Review 结果：初次分派历史阶段检查

## 结论

CONDITIONAL。本批补齐已声明 ASSIGN 保存的历史目标检查，可提交窄增量；不代表完整生成前阶段竞态、无标记保存或 Complete 资格门禁已完成。

## Findings / 逐条处置

### Major（已修）

- `SubTableBatchAssignmentStageGuard` 原来仅调用 `getProcessInstanceTasks`，实际接口是 runtime task query、page=0/size=100。目标已完成或取消后不在活动列表，不能证明“从未生成”。新增两个反例先运行：7 项测试中 2 项失败，预期拒绝却未抛异常。
- 复用既有 `getTaskHistory`，服务端 `/api/v1/history/tasks` 使用 `HistoricTaskInstanceQuery.processInstanceId(...).list()`，无分页，明确返回 `activityId/processInstanceId/endTime/deleteReason`。目标精确节点 ID 只要出现在任意历史任务中即拒绝，不按完成/取消状态重新开放。
- 当前活动任务和历史任务条目须有精确节点及匹配实例身份，坏类型/缺身份不静默跳过。历史不可读、空历史、没有生命周期字段明确为 null 的当前宿主记录时拒绝；不将缺失历史当作目标未生成。

### Blocker / Minor / Question

- 完整增量复审未发现新增 Blocker；无新增业务选择。后续跨服务竞态、无标记分派字段差异识别属于原设计内尚待实施，不在本批宣称修复。

## 范围 / 验收映射 / 触发矩阵

- D7、A32：已声明批量分派不对已经出现过的目标任务重新配置。只改 Portal guard 和测试，不改旧任务接口、任务分派算法或历史存储。
- implementation-autonomy、bugfix-verification、code-review、config-over-heuristics、secure-coding-sast；历史字段名来自既有明确响应契约，目标节点 ID 来自冻结 AssignmentConfig，不猜列名或任务名。
- 非 ASSIGN 请求提前返回，无新增查询。多个 ASSIGN 声明共用本次 runtime/history 两次查询，不逐行发请求。

## 编译与静态检查

- `mvn -pl backend/user-portal -am package "-Dtest=SubTableBatch*Test,SubTableWriteIsolationTest,TaskApprovalCompletionSubTableGuardTest,SubTableBindingScopeGuardTest,MiSubTaskSubTableRowMergerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：BUILD SUCCESS；XML 报告核对 197 项、0 failures/errors/skipped，其中 stage guard 12 项。
- 新增覆盖完成/取消目标、历史不可读/空/异实例/坏节点、当前宿主已结束或生命周期字段缺失、活动列表坏条目、同批只读一次及普通 EDIT 不调用历史。
- `pwsh -NoProfile -File scripts/verify-batch-postgres.ps1`：PostgreSQL/JPA8 + H2 保存链12，共20项通过，专用临时容器已按标签核查清理；不改平台卷。
- 本批没有前端或共享 jar 源码变化，前端重复 build 和其他服务重建增量 N/A；既有全量类型检查383错误仍未闭合。

## 测试与运行证据

- 只重建 Portal backend（compose `up -d --no-deps --build user-portal`），镜像 manifest list `sha256:66d152570e54702f3b0443f92be4cac48b13056d38ec8a6e3a06a00f26414ddd`，healthy、启动成功。
- 使用新合成流程 `f302c62f-c89a-11f1-a63d-c6848797bef7` / 宿主任务 `f30f228d-c89a-11f1-a63d-c6848797bef7`：最终部署后真实 `verify-batch-assignment-workspace.mjs` PASS。目录、两类错误草稿保留、EDIT + ASSIGN 单次 submit、GET/reload、任务数量不变、pageErrors=0；因此当前宿主的真实历史响应通过了新严格检查。
- 已完成/取消目标拒绝本轮是精确契约单测证据，尚未在真实流程中完成目标后返回宿主执行破坏性负例；不把正向 Save 代替该运行时故障场景。
- 保存截图保留于 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-assignment-mixed.png` 及 `-narrow.png`，人员显示遮盖。
- 最终完整 `pnpm -C frontend run regression:mi`（固定旧 MI 合成实例）234 单测 + 6 浏览器场景全部通过，`OK — unit + screenshots`；没有只跑 unit-only。

## Function Unit 通用性与生命周期

- 精确 processInstanceId + 冻结 taskDefinitionKey 对比，不依赖测试 FU 或表名。普通旧请求不触发新增历史查询。
- 配置保存/导入导出/Clone/Rollback/Admin 两条 Import 未改协议或引用处理，增量 N/A；整体往返验收仍开放。
- 已部署引擎 dev/docker 配置均为 history-level full；没有历史证明的环境明确拒绝该新操作，不关闭历史检查兜底。

## 增量敏感信息 / 性能

- 增量 secret 扫描和完整 staged diff 在提交前复核，仅输出命中计数，不打印目录个人信息。无新增明文凭证/配置、无新 URL 拼接或日志字段。
- 每批新增一次完整历史 HTTP 查询；沿用既有接口，不新增按行请求。未做长流程历史负载压力测试，不宣称性能提升。

## 残余风险 / 范围外问题

- runtime/history 两次读不是 Flowable 与 Portal 的跨服务锁，仍可能在校验后执行阶段改变；需要后续按原设计补竞态证据和提交复核。
- 当前 guard 接收 ASSIGN 声明，省略声明的普通可写分派字段差异、自动保存后 Complete 已清除标记的资格复核仍待实现；本批不宣称省略标记无法绕过完整分派策略。
- 实际 BPMN/冻结字段全链一致、目标资格与角色候选池、创建失败可见性、Preview/New Request、启动失败隔离、hydration、多主体、生命周期和压测仍开放。完整 P4 未完成。
