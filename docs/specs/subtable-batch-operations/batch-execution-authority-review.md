# Code Review：增强实例的任务执行域写入权威

## 结论

CONDITIONAL；本批关闭客户端 `_currentItem` 冒充执行域的缺口，不代表 P2/P3/P4 全部完成。第二 reviewer 完整 staged 重审：两个 Major 均已闭合，无新 Blocker/Major；脚本语法、增量敏感信息、diff 及 Surefire 独立核验通过。最终完整 MI 234+6通过；UI失败及多主体限制仍保留。

## Findings 与处置

- 已修：增强实例 Save/Complete 的行隔离直接信任客户端 item。新增引擎 opt-in `includeWriteContext=true`，按实际 processDefinitionId 的部署 BPMN 找 MI 与配置 elementVariable；读取任务执行域 VariableInstance，拒绝过程根变量、错误实例、缺模型/元素/配置/身份或引擎查询失败。无按列名猜测。
- 已修（首轮 Major）：Complete isolation 后 Owner 又从旧 TaskInfo 恢复陈旧参与者。严格 authority 贯穿 Owner；普通任务的权威结论是“非 MI”，不能恢复旧 B。最终引擎变量合并及本地 snapshot 合并后再次剥离过程级 item，无 Owner 组件时也执行。
- 已修（重审兼容 finding）：Save 的严格替换最初也清理了 legacy baseline。现在仅增强实例清理；旧实例保持“客户端有 item 才覆盖”的原语义，含 currentItem alias 回归。
- 测试装配/编译问题：新 fixture AssertJ 泛型、括号错误已修；IDE ECJ 覆盖 target 曾产生 Flexible Constructor Bodies 运行错误，clean Maven 重编后通过。没有降低 Java 版本或测试标准。

## 范围 / 验收映射

A16/A19/A26/A32：服务端实际执行上下文、不可冒充行范围、无 batch 标记不能绕过。仅扩展现有 task detail 的可选响应；默认查询不加载严格上下文，旧实例继续原路径。新增响应只给 rowKey，不给 MI 行其他数据；Portal 仍执行原用户/任务权限，不把行身份查询当作授予权限。

## 编译与测试

- `mvn -pl backend/user-portal,backend/workflow-engine-core -am clean package "-Dtest=TaskWriteContextResolverTest,SubTableBatch*Test,SubTableWriteIsolationTest,TaskApprovalCompletionSubTableGuardTest,SubTableBindingScopeGuardTest,MiSubTaskSubTableRowMergerTest" "-Dsurefire.failIfNoSpecifiedTests=false"`：引擎7项、Portal182项全部通过，BUILD SUCCESS；随后 legacy 兼容修正后 Portal183项及 package 再通过。
- RED：临时让 context resolver 返回客户端原数据，两个真实 Complete MiMerger/ScopeGuard 越域用例均在“应拒绝但未抛异常”处失败，2失败、0错误。恢复权威解析后通过；突变不保留。
- PostgreSQL/JPA 与 H2 同脚本重跑：7+12项、0失败/错误/跳过，BUILD SUCCESS。
- 7项引擎测试：普通节点、嵌套 MI、直接 MI、配置变量、过程根变量、缺配置/坏身份、引擎错误。Portal 覆盖伪造/省略 item、有/无 formData、无 batch 标记、Owner 陈旧 B、普通任务和 legacy alias。

## 部署与实际运行

- 新 jar 已分别重建 workflow-engine 和 user-portal，均 healthy；没有共享 jar 源码、数据库结构、Kong 或环境变量变更。
- 合成 FU50029 为独立 clone，只启用 People 的 Edit；源 FU 只读。两条实际任务返回各自执行域身份，实际定义 ID 不按 process key 读取最新版。
- `MI_FIXTURE_PROCESS_ID=532dc136-c867-11f1-ada1-7eab7c802be1 node frontend/scripts/verify-batch-mi-authority.mjs`：PASS。真实 Save 持久化、行版本递增；伪造另一执行域的 Save/Complete 均403且数据未变；合法任务用错误客户端 item 仍按真实执行域完成，另一任务保留；过程变量无 item 残留，完成任务不再拥有 active write context。
- 此样本是一个操作者、两个执行域，不是两用户；完整两用户/BU/角色验收仍未完成。Task Todo 索引短暂保留已完成任务，验证改为先核对引擎 active context，再有界等待索引收敛，非只看 toast/HTTP。
- 最终完整 `pnpm -C frontend run regression:mi`：234单测+6浏览器场景通过，保留旧实例截图。新启用非 MI Edit-only 页面另测 PASS、pageerror=0，逐行 Apply/保存、只读伪造拒绝与版本均核对；桌面截图 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-editonly-desktop.png` 已目视检查，窄屏同目录 `2026-10-15_batch-editonly-narrow.png`。这不替代下节增强 MI 页面失败项。

## 已复现但本批未修的 UI 门禁

增强 MI 的真实 inline Save 当前403：只读参与者数组回传了 assignee_display_name 和嵌套 __subTables__，触发先于本批 authority resolver 的现有 TaskPermissions 检查。字段差异已记录，未放宽服务器权限。

本批服务端验证明确使用 `MI_FIXTURE_API_SEED=1` 的 API 合成准备和 canonical 业务 payload，脚本输出 uiVerified=false；它不是页面 Save 验收通过。该页面提交投影问题作为下一 concern 继续修，不能报告完整批量功能已可用。

## FU 通用性 / 安全 / 性能

基于部署模型、配置变量与 rowKey 契约；无 FU/业务列名硬编码。只增可选内部响应，不改变包格式，Export/Clone/Rollback 消费者本 concern N/A，完整生命周期验收保留。客户端 URL 就地 requirePathToken；引擎失败不吞为空。每次增强写入一次额外严格查询，非逐行 HTTP；未压测，不宣称性能提升。

## 残余风险 / 未验证

UI echo 投影、不同操作者、Owner 完整目录端到端、P2 同工作区 Assign、真实 BPMN 冻结分派 Complete 全链、Preview、New Request 摘要、启动跨边界/hydration 和完整生命周期仍未闭合。只读上下文可能在查询后遭任务阶段变化，后续写入/Complete 原鉴权及乐观锁仍必须执行；不承诺跨服务原子性。
