# Code Review：首任务自动完成的冻结分派边界

## 结论

CONDITIONAL。本窄批无未接受 Blocker/Major，不代表 P4 全部完成。范围是已有首个人工任务自动完成前的 MI collection 校验；没有首个人工任务、启动即进入 MI 的流程仍需独立启动门禁。

## Findings 与逐条处置

- Major（已修）：ProcessStart 首任务自动完成调用旧四参数 collection builder，并用 putIfAbsent 保留客户端预置 collection。增强实例可能绕过冻结字段映射和当前目标资格。现在只从已提交本地实例读取服务端快照，增强路径使用专用首任务入口，校验成功后整体发布 collection；旧实例仍沿四参数及预建集合保留语义。快照不进入引擎变量。
- Major（独立首审发现，已修）：严格 BFS 不能越过下一人工任务，要求其未来参与者已配置，否则正常 Intake→Review→MI 会卡在 Intake。增强路径在下一 userTask 停止，等该宿主提交时校验；原非增强路径不变，callActivity look-ahead 保留。移除边界反跑：21项中1失败0错误；恢复后全部通过。
- 非 Batch Assign 的首步预建集合保留，不用新能力覆盖既有会议等准备链。新增专门正例验证；没有通过静默回落绕过已冻结 Assign 的错误。
- Blocker / Minor / Question：当前无新增项。没有修改任务改派、目录、数据库结构、普通保存或补偿撤销逻辑。

## 复现、构建和测试

- ProcessStartBatchFirstCollectionTest 在旧实现上3项中2失败0错误；改后3项通过。覆盖服务端基线、替换伪造集合、旧路径兼容和失败不发布。
- SubTableBatchMiCollectionTest 共21项，新增首步严格映射、非 Assign 预建集合、下一人工宿主边界及 callActivity 用例。
- 隔离源码副本执行 Portal Maven package：ProcessStart*Test、InternalEmailProcessStartComponentTest、SubTableBatch*Test、MiCollection*Test、TaskProcessProperties、SubTableWriteIsolationTest、TaskApprovalCompletionSubTableGuardTest、SubTableBindingScopeGuardTest、MiSubTaskSubTableRowMergerTest、WorkflowEngineClientTest、WorkflowEngineInstanceBpmnClientTest，共494项0失败/错误/跳过。日志 batch-first-collection-package.log 留在隔离副本，不提交日志或 jar。
- 独立 reviewer 同源关键测试30项0失败/错误/跳过，4份关键源码/测试 SHA256 一致；人工宿主 finding 重审闭合。
- Portal jar SHA256 `2269BB638669E44AA77B98EB7170585DCF54D2784D56A7810D95A347934F8E25`；重建运行镜像 `sha256:f9ba105a03d7ed23d93aa8ae16fbc2e347b42ee0711447c4acd130b94c62b465`，healthy 后运行下列场景。没有用 mvn test 产物假装部署。

## 真实运行证据

- 新合成 FU50045 `batch_verify_firstmi1016`，DW validate 0错误/警告，Deploy 后实际 Intake→MI。创建脚本仅显式 FIXTURE_DIRECT_MI=1 且 assignment 模式允许，默认原图不变。
- 无效个人目标：实例 `5a1dfd01-c974-11f1-8f36-4ee612046d7e` 返回 FIRST_STEP_NOT_COMPLETED，实际只保留 Intake `5a1e2433-c974-11f1-8f36-4ee612046d7e`，没有 MI 子任务，两行配置可见；失败对象保留，不自动修复或取消。
- 合法目标：新实例 `5a5cb3d6-c974-11f1-8f36-4ee612046d7e` 实际生成 `5a6b333a-c974-11f1-8f36-4ee612046d7e` / `5a68e946-c974-11f1-8f36-4ee612046d7e`。两份 canonical 行替换客户端单条伪造 collection，真实 assignee、两份冻结行身份正确，候选集合为空，Flowable variables 无本地保留快照。
- 已有合成 FU50036 普通 Intake→Review→MI：新实例 `231a7893-c975-11f1-8f36-4ee612046d7e` 正常到 Review `2326fbfa-c975-11f1-8f36-4ee612046d7e`，保存角色配置不会创建任务；后续 Complete 实际生成 `2d9a010e-c975-11f1-8f36-4ee612046d7e` / `2d97de29-c975-11f1-8f36-4ee612046d7e`。各1名候选且未领取，BU/Role 与目录配置一致、两份身份不同、pageErrors=0，目录写入0。
- 脱敏截图保留于 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-16_first-host-preserved-workspace.png` 及 `2026-10-16_first-host-preserved-workspace-narrow.png`。本批无 Vue/CSS/UI 产品变更，不宣称全 UI parity 验收。
- 当前新镜像完整 `pnpm -C frontend run regression:mi`：29文件234单测及6浏览器场景全部通过，最终 OK — unit + screenshots；不是 unit-only。

## 通用性、性能、安全与生命周期

- 按本地快照、实际实例 BPMN 和 form/binding 配置解析，产品代码无合成 FU/列名判据；新建直接 MI 与已有普通宿主两类流程有真实结果。
- 仅增加首步一次本地实例读取；目标资格仍批量查询，未增加逐行 HTTP。未作性能提升宣称或压测通过宣称。
- 无 schema、序列化字段、导入/导出/Clone/Rollback 格式变化，生命周期写出端本批 N/A；消费既有冻结契约，先前迁移证据不冒充本批所有生命周期复验。
- 独立最终 staged 7文件完整复审无新增 Blocker/未接受 Major；node --check 两脚本、cached diff --check 通过，238条非空新增行四类敏感模式0命中，可提交 CONDITIONAL。独立审查未重复执行真实 runtime，运行证据取自上述同镜像实测，不冒充 reviewer 独立复跑。

## 残余风险 / 未验证项

无初始 userTask 的直接 MI 启动、所有非标准启动来源、持久化失败双重补偿失败的隔离、执行时资格竞态/引擎分派异常可见性、混合有效/失效角色成员仍开放；没有宣称跨服务整批事务。现有前端383项类型错误未在本批修复，不报告整体 build/P4 PASS。
