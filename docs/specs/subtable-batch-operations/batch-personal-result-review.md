# 个人分派逐行结果核对

## 结论

CONDITIONAL。本批补齐普通 Complete、发起时首人工步骤自动 Complete 后的个人结果比较；不代表 P4 或 A38 全部完成。未改 FU50007、其已发布配置或业务实例；所有写操作仅针对明确标记的合成验证 FU。角色池精确结果、直接从 Start 进入 MI 的完整结果核对、刷新后的结果持续展示及完整发布验收仍开放。

## 范围 / 验收映射

- A37/A38：严格集合预校验成功后，在服务器内存计划保存完整行键与预期个人标识；按真实实例、流程定义、节点、表、完整行键联结两条既有 bulk 查询。实际办理人来自 live task，绝不用 ETI 缓存办理人代替。
- 每个个人目标恰好匹配一个 live task，任务不能重复使用；全部目标及范围完整且人员相等才返回 VERIFIED。缺少、重复、坏上下文或身份不明时不能断言全部成功；明确本范围的缺主或错误非空办理人返回 FAILED。
- 响应只含状态、固定 reasonCode 和已证明属于本范围的 taskId，不返回预期/实际人员值。角色及混合模式目前保持 UNCONFIRMED。
- Apply/Save 不创建任务；仍由原 Complete/流转生成，不重试、取消、改派、补偿或重建任务。观察在动作完成后执行，不能撤销此前成功的流程动作。
- Portal 仅明确 VERIFIED 时沿用普通成功提示；FAILED/UNCONFIRMED/未知状态保持不可自动消失的警告。三语与既有帮助文章同步，不增第二站点或新设置。

## Findings 与逐条处置

### Blocker

无。

### Major 1：额外身份不明任务被过滤，可能误报全部核实

- 反例：一个正确 own task 加上同实例/定义/节点但缺少或重复 identity 的额外任务。旧 scoped 只留下正确任务，错误返回 VERIFIED，额外任务可能是 own 行的重复创建。
- 影响：无法证明一行一个任务，却提示全部结果已核实。
- 最小修复：`SubTableBatchCompletionObservation` 保留 `identitiesComplete`；相关 live task 无法唯一联结合法身份时，将 VERIFIED 收紧为 UNCONFIRMED。完整明确的 foreign 行仍可排除，未知 taskId 不进入响应，明确 own 失败仍可返回 FAILED。
- 依据：配置驱动的完整行身份、修复红绿验证、A38 不以 Complete 状态码代替证据。
- 验证：`anAdditionalUnresolvedTaskCannotBeSilentlyExcludedFromSuccess` 修前 VERIFIED/期望 UNCONFIRMED，修后通过；另测明确 foreign 行不误报失败。

### Major 2：缺少 live 流程上下文也被当作 foreign 排除

- 反例：正确 own task 加上缺 pid、definition 或 node 的额外任务，前置过滤旧逻辑直接跳过，仍可能 VERIFIED。
- 影响：与 Major 1 相同，信息不足被误当成可安全排除。
- 最小修复：foreign 判断前用 `hasLiveContext` 要求三项均为非空 String；否则保留不确定性，不暴露未知 taskId。
- 验证：`missingLiveContextCannotProveAnExtraTaskIsForeign` 先红（VERIFIED 对 UNCONFIRMED）后绿。

### Minor / Question

无新增。两条 Major 的完整增量已独立重审，静态闭合；最终 staged 签核另附。

## 修复验证

- 初始红：正确办理人仍 UNCONFIRMED；非空错误办理人也 UNCONFIRMED。相同两条后端测试先失败，再通过。
- 前端红：VERIFIED 仍发未确认警告，普通 success 未被调用。修后同测试通过；未知状态仍警告。
- Java：`mvn -pl backend/user-portal -am test "-Dtest=SubTableBatch*Test,TaskCompletionObservationTest,ProcessStartBatch*Test" "-Dsurefire.failIfNoSpecifiedTests=false"`，225 项通过，无失败/错误/跳过。
- 前端：`useTaskActionsAssignmentOutcome.test.ts` 5 项通过。
- 打包：隔离 Maven 源副本构建成功，避免根工作区 IDE 编译目标干扰；只复制本批实际 Java 变更。最初误在副本执行 git diff 导致没有复制新源的一次旧测试结果明确不计入证据，正式 red/green 均先从真实仓库同步源。
- Portal build 32.31 秒、Help build 2.00 秒通过。原 chunk 提示未隐藏。Portal 全量 vue-tsc 仍 383 项，与此前记录一致；没有宣称全量类型通过，未顺手修复全仓历史问题。
- 最终 jar SHA256 `37A929EA602C0DFDE1D29E9B5532FAEFF3F0C9E78B87687470466CB6D81957AA`。
- 仅重建实际 Portal 后端、前端及帮助站，无其他服务重启。后端健康；镜像分别为 `sha256:5c388e394e79d2b94bc2f6e73de39cf9e4f70bfda56a952025bc08b4f7cb770d`、`sha256:e641e232e29fd45ee3d9f648fa2629b06d907a466f433868f7a2f55e5e5c042c`、`sha256:dd0f6c3319b8af543cc9c6d3bd754789a854b18ca9b0827bb1bf89d216cd2bbe`。

## 真实运行证据

- 新合成 FU50052 `batch_verify_wrongperson1017`，显式故障旗标只在新合成设计加入任务创建时的错误非空目标；不维修或修改已有任务。实例 `174ea2c5-ca06-11f1-b00d-96c2314bbe84`：Save 前后任务仍只有宿主；真实 UI Complete 后两条任务确实存在且为故障目标，返回 FAILED、两条 `PERSON_TASK_TARGET_MISMATCH`，页面仅警告，pageerror=0。
- 已有 UUID 主键 FU50046 `batch_verify_assignuuid1017` 新实例 `300176e4-ca07-11f1-b00d-96c2314bbe84`：不同 PK/FK/普通列命名，实际两条任务目标正确，返回 VERIFIED/空 issues，普通成功提示，pageerror=0。
- 首人工自动 Complete：已有 FU50045 新实例 `45d8e4e2-ca07-11f1-b00d-96c2314bbe84`，真实两任务逐行正确，VERIFIED，无 firstStepError，pageerror=0。
- 既有缺主合成 FU50050 新实例 `52621ee1-ca07-11f1-b00d-96c2314bbe84`：首步完成、两条真实缺主任务，返回 FAILED/两条 PERSON_TASK_UNASSIGNED，无 firstStepError，页面警告，pageerror=0。
- 直接从 Start 进入 MI 的既有 FU50047 新实例 `602364b0-ca07-11f1-b00d-96c2314bbe84`：两条实际任务正确，但该入口仍使用轻量列表，保持 UNCONFIRMED，不冒称完整行目标已核实。该缺口后续单独收敛。
- 固定以上五个合成 pid 的 PostgreSQL 只读事务核实：各恰好两条 AssignedWork；缺主样本恰好两条空办理人，其余均非空；错目标样本两条都为显式故障目标。未打印目录人员标识、姓名或邮箱。
- 帮助站实际部署后 `node scripts/verify-help-portal.mjs`，152/152，通过；说明覆盖办理人缺失与不匹配、动作已执行勿重复、保存不等于任务创建。

截图均保留，已目视错误告警窄屏、正常成功及帮助窄屏，无 PII/遮挡：

- `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-17_personal-wrong-target.png` 及 `-narrow.png`
- 同目录 `2026-10-17_personal-uuid-verified.png`、`2026-10-17_personal-first-step-verified.png`、`2026-10-17_personal-first-step-missing.png`、`2026-10-17_personal-direct-initial-unconfirmed.png` 及各自 `-narrow.png`
- `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_help-sub-table-batch-outcome.png` 及 `-narrow.png`

## 通用性 / 生命周期 / 性能

- 通用性：冻结配置字段、节点/表/完整键驱动，非列名猜测；新建合成故障 FU 与既有 UUID/人工键 FU 运行通过。
- Save/Load、Export/Import、Clone、Version/Rollback、Admin 两条导入、Deploy：不修改设计格式/快照/重写器；本批新增计划仅服务器临时内存，不持久化、不导出、不进入 Flowable 或通用 formData，因此上述生命周期代码 N/A，不取代前序完整发布门禁。
- 只复用两条有界 bulk 查询，无逐行 HTTP/SQL；上限/分页不全明确未确认。未压测，不宣称吞吐提升或 200 行全部结果可核实。
- 完整 MI、最终 staged 检查及增量 secret 扫描结果待实际执行后补记。

## 最终签核

最终部署镜像完整 `pnpm -C frontend run regression:mi`：234 项单测、6 个真实浏览器场景全部通过，不是 unit-only。既有 subprocess shell deprecation 提示未屏蔽。Portal typecheck 与上一份 `batch-input-constraints-portal-typecheck.log` 的 383 条 TS error 逐项比较，差异 0。实际日志没有 Unresolved compilation / APPLICATION FAILED；已知旧 catalog-code BPMN 查询 404 及浏览器切页 Broken pipe 不夹带修复。

独立最终 staged 24 文件重审：两条 Major 已逐条修正，无新增 Blocker/未接受 Major；独立 Maven 18 项、前端 5 项测试通过，三份关键 Java 源 SHA 与构建副本同源。cached diffcheck、三脚本语法检查通过；270 条非空新增行四类增量敏感检查 0 命中。独立目视错误目标窄屏、首步成功和帮助窄屏无 PII/遮挡。报告此次只追加真实签核记录，产品源未再改；本代码批次可提交，整体功能仍 CONDITIONAL。

## 未验证项 / 范围外

角色候选池精确结果、混合模式全行结果、直接初始 MI 完整核对、刷新后持久可见性、预校验到创建间资格竞态及全部 A01–A41 仍未由本批闭合；不能开放生产配置或报告 P4 完成。全部合成任务、失败样本和截图保留，不取消/改派以掩盖结果。既有全量类型问题、邮件调度表缺失等不夹带修复。
