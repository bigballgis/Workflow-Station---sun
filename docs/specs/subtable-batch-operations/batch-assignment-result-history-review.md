# Code Review：批量初次分派结果的持久回读

## 结论

独立 reviewer 对完整 staged 35 文件最终签核：无新增 Blocker/未接受 Major，本持久回读窄批可 CONDITIONAL 提交。当前静态及运行验证已完成；完整 P4 仍未放行，A39 用户暂缓并非豁免。

## Findings / 逐条处置

- Major 1：审计写入失败提示覆盖已知 FAILED/UNCONFIRMED。已改为共用 feedback，同时显示真实分派结果与记录失败；两入口及反例测试通过。
- Major 2：已完成流程或 time-only 历史快照可能显示后来结果。审计采用独立时间截止；缺失/非法截止仅允许精确 source task，不能取得后续其他任务结果。RUNNING/COMPLETED 反例通过。
- Question：把审计混进 ProcessComponent 会改变归档 CSV。该处已完全恢复，只在既有授权 Controller 合并；TaskHistory 的本地 BPMN fallback 排除审计类型。
- Blocker/Minor：最终独立重审无新增项；没有接受或隐去的重大 finding。

## 范围 / 验收映射

- A38：Start/Complete 实际任务核对后，复用 up_process_history 记录状态、任务 ID、原因码。不是任务当前状态，不重跑动作、不改派、不补偿取消。
- 只记录服务端 Plan；旧路径无 Plan 不新增记录。无新表、列、路由或 FU 配置。候选目录、业务行和完整快照不进入记录。
- 独立 REQUIRES_NEW 写入在原动作提交之后；写失败返回 historyRecorded=false，原分派结果不丢失，也不能将已提交动作报告成可重试。
- My Requests → application detail → Flow History 回读。沿用 canAuditProcessDetail；detail 缺失不查询审计。无新浏览权限。
- 审计与 BPMN 时间线分开，历史任务快照单独投影；不进入引擎变量或归档 CSV。
- Help、Portal 三语同步；没有修改用户 FU50057、FU50007 或用户给出的任务。

## 编译与静态检查

- 隔离副本最终 package：258 测试，0失败/错误/跳过，BUILD SUCCESS，24.148s。独立 reviewer 核对 12 个 Java 源 SHA 一致，复跑关键13测试通过。
- 应用详情及反馈/快照相关：18文件61测试通过；独立前端19测试通过。
- Portal build 20.93s，Help build 1.11s；真实重建 user-portal、user-portal-frontend、platform-help-frontend，后端 healthy。
- 最终 vue-tsc 仍有383项既有诊断；与 batch-role-result 基线移除行号后完全一致，新增0。不能称全量类型检查通过。
- 产物 jar SHA256：BCC93CEA1FB1EF1FB65BDE3041B6074DFFA67BD415F02B5FBD0C1F78CA479632。
- 部署镜像：Portal backend fcc44fb0099da26c3cdb5671efc4ea184bf2426eacdfe85d04752faa572d8025；Portal UI f835af8c43bdda475e44f9c5b7bf0fb17bde6f7861486f7434511ca7bfe475c8；Help 077c280899964ca304cf647767bdecea207500df3cff2be2151edbe2c10d9d9e。

## 测试与运行证据

- 红：部署前合成 first-MI 错误候选组，Start 返回 FAILED，但 history 无相应记录，脚本精确断言失败。未回填该旧实例。
- 绿：同一合成 FU 的新实例 cdb9a43f-ca3c-11f1-b00d-96c2314bbe84 返回 FAILED、historyRecorded=true；两条实际任务的候选组不匹配原因一致，导航/刷新后仍可读。
- 正常 Complete：合成 FU batch_verify_assigngate1015，新实例 e4d79c93-ca3c-11f1-b00d-96c2314bbe84；UI 逐行个人+两成员角色池，实际任务数量2，个人1、未领取角色池1，VERIFIED、historyRecorded=true，刷新后回读一致。
- PostgreSQL 只读查询两实例：BATCH_ASSIGNMENT_VERIFICATION=2，SUBMIT=2；没有为旧红例补造记录。
- directoryWrites=0；正常测试帮助器可能通过既有 Admin API 给当前测试角色授予合成 catalog 访问，不修改真实业务 FU 或目录成员。
- 完整 regression:mi：234单测 + 6个必要真实截图场景通过，非 unit-only。
- Help 浏览器检查152/152通过；首次151/152是旧文字断言未同步，已更新为同时检查 Flow History 位置、记录时点、非当前状态、审计失败及保存值不证明任务创建，不减验收内容。
- 截图根目录：F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\。
  - 2026-10-17_batch-history-before-history.png：红例。
  - 2026-10-17_batch-history-failed-desktop.png、2026-10-17_batch-history-failed-narrow-scrolled.png：异常回读，窄屏可滚到末条原因。
  - 2026-10-16_batch-history-complete-history-desktop.png、2026-10-16_batch-history-complete-history-reloaded.png：正常回读；文件名前缀沿用既有脚本，实际执行日期为10月17日。

## Function Unit 通用性与生命周期

不修改设计配置、ID重写、导入、Clone、Rollback或部署包格式，相关生命周期 N/A；两套既有合成 FU 分别走 Start失败与 Complete成功。旧 BPMN fallback、安全拒绝和缺失实例均有测试。

## 性能 / 敏感信息

每次观察新增一次独立审计事务；每次已有历史读取新增一次实例+类型查询，不逐行查询目录或新增 HTTP。沿用既有无分页历史读取，未做大规模性能压测。
提交前 staged 532 行非空新增行敏感模式扫描零命中，独立 reviewer 四类增量检查零命中；cached diffcheck 与三个脚本 node 语法检查通过。独立目视截图仅合成数据、测试账号界面及技术任务 ID，无真实目录 PII、邮箱或候选成员清单。

## 残余风险 / 豁免

A39：引擎成功、本地写入失败且取消/清理都失败时的持久隔离，已按用户要求暂缓，.kiro #1666 OPEN/critical；cf8a3780c 仅记录，不修复、不豁免 P4。此次不调整启动顺序。
记录本身损坏/查询失败会明确显示无法读取；数据库宕机的真实故障注入未执行，写失败语义有独立事务测试。完整 P4 不宣称完成。
