# 混合分派：不让角色未知掩盖已知个人失败

## 结论 / 范围

CONDITIONAL。本批仅收紧 A38 的已知个人失败报告，角色候选池未知不再阻止个人逐行核对；不将未领取角色任务当作失败，不宣称整个混合批次 VERIFIED。产品改动只有 `SubTableBatchPersonalOutcome` 删除提前 continue 的一行。

计划里有角色或缺少个人目标仍令 complete=false，但已冻结的个人目标继续经过原 pid/definition/node/table/完整行键、唯一匹配与任务去重校验。正确个人+角色仍 UNCONFIRMED；已知个人缺主或错误才 FAILED，且只有本范围那条个人 taskId。角色/未知行不进入个人循环。无任务重试、创建、改派、补偿、取消或目录写入，未修改 FU50007/业务实例。

## Findings / 处置

- 根因：`!personalOnly` 或个人目标数少于总行数直接 continue，连可证明的个人失败也跳过。独立方案复核确认这属于已批准范围内的最小收紧，无业务分叉。
- Blocker / Major / Minor：独立首轮完整三文件静态审查无新增 finding。
- 原个人/角色资格、保存、集合构造与分派算法不改。验证脚本只有显式 `FIXTURE_EXPECT_WRONG_PERSON=1` 且 mixed、非 roundTrip 才进入故障检查，默认正例断言保留。

## 红→绿与构建

- 新 mixed 测试先红：24 项中 1 失败，期望 FAILED、实际 UNCONFIRMED。四项新增用例覆盖正确混合、个人错误/空白、foreign 行及重复个人身份；unknown/role 不可伪造成功或泄露 taskId。
- 真实 UI 红：新合成 FU50054 `batch_verify_mixedwrong1017`，实例 `4d96543b-ca09-11f1-b00d-96c2314bbe84`。先普通保存角色配置，再真实工作区将第一行换个人、Apply/原 Save，随后 UI Approve/Confirm。旧镜像仍 UNCONFIRMED，对 FAILED 断言失败；固定 pid 只读 SQL 证明两任务中一条为故障个人、一条未领取角色。失败样本不修补、不删除。
- 相同脚本新实例 `a6f55c3b-ca09-11f1-b00d-96c2314bbe84` 通过：响应 FAILED，issues 恰好一条个人任务 `b62a557c-ca09-11f1-b00d-96c2314bbe84` 的 PERSON_TASK_TARGET_MISMATCH；另一角色任务 `b62b8e01-ca09-11f1-b00d-96c2314bbe84` 不被误报，真实候选集合精确等于单人角色池，空办理人、CANDIDATE_USERS、BU/Role 均正确，pageerror=0、directoryWrites=0。
- 已有正常 FU50035 `batch_verify_assigngate1015` 新实例 `c69fe56b-ca09-11f1-b00d-96c2314bbe84`：同样 UI 混合编辑，个人目标正确、角色未领取及候选集合精确正确，响应仍 UNCONFIRMED 而非虚假 VERIFIED，pageerror=0。重新打开/关闭保留全部数据和任务 ID，未选行仅原普通 Save 的平台版本+1，其余字段逐项不变。
- 最终 `mvn -pl backend/user-portal -am package "-Dtest=SubTableBatch*Test,TaskCompletionObservationTest,ProcessStartBatch*Test" "-Dsurefire.failIfNoSpecifiedTests=false"`：231 项全绿、0 失败/错误/跳过，BUILD SUCCESS，25.09 秒。
- jar SHA256 `557FBA51C5AE4826A37313B6052EEEE447C53E7D1187CC08A56288E43C1853CF`；只重建 Portal 后端，最终健康镜像 `sha256:c959cb451b0887a7c15d6724246618f2a57ef9ed1e964e5b66139ac5645eb3f8`。前端产品代码未改，继续使用已构建部署版本。一次启动未健康时登录 502，未发生业务写；待健康后才正式创建新样本。
- 完整 MI 与独立最终 staged 结果待实际结束后补记，不提前填 PASS。

## 截图与状态证据

`F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-17_mixed-personal-before-outcome.png` 及 `-narrow.png`；修后 `2026-10-17_mixed-personal-failed-outcome.png` 及 `-narrow.png`，已目视窄屏，无 PII/遮挡。

同目录原脚本工作区图 `2026-10-16_mixed-personal-{before,failed,normal}-workspace.png` 及各 `-narrow.png` 保留（原脚本沿用旧日期前缀，实际为本轮采集）；人员/BU/Role 控件按原脚本遮罩。截图不代替任务结果核实。

固定故障 pid 的 PostgreSQL 只读事务确认两任务、一条固定故障个人、一条空办理人；实际 candidate identity link 恰好一条。正常正例通过引擎任务详情按完整行键对应每条保存行，不靠任务顺序或只看 toast。

## 通用性 / 生命周期 / 性能 / 未验证项

触发实现自主、配置驱动、修复验证、代码审查、安全/隐私与 MI 门禁；新建合成故障 FU 和已有正常 FU 均验证。无 schema、DTO、设计配置、平台依赖或包格式变更；Save/Load、Export/Import、Clone、Rollback、Admin 两条 Import 的设计消费者无新增变更，N/A，不替代其完整发布验收。

不增 HTTP/SQL，继续两条有界 bulk 查询；不声明性能提升。角色候选池完整结果、混合全部目标 VERIFIED、刷新后持续 GET 以及创建竞态/全部 A01–A41 仍未闭合；不宣称 P4 完成，不启用生产配置。旧类型与邮件等无关问题不夹带修复。TEMP 保留 `batch-mixed-personal-{red,runtime-red,runtime-green,normal,package,deploy}.log`。

## 最终签核

最终部署镜像完整 MI 回归 234 项单测、6 个真实浏览器场景全部通过。独立 staged 4 文件重审无新增 Blocker/未接受 Major，独立 24 项测试绿，两份 Java 源 SHA 同源；cached diffcheck、脚本 nodecheck 通过，92 条非空新增行四类增量敏感检查 0 命中。独立目视告警及正常工作区窄屏无 PII；正常工作区截图的原遮罩覆盖部分底部按钮，因此不将它用于完整 footer 布局门禁。产品源未再修改，仅补真实结果；本批可提交，整体 CONDITIONAL。
