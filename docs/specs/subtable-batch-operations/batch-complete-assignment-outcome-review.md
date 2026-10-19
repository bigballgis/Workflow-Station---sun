# Code Review：普通 Complete 的初次分派异常反馈

## 结论

CONDITIONAL。只闭合普通 Portal Approve/Reject 完成后的个人缺主观测与三语警告，不宣称完整 A38 或 P4 完成。独立最终staged重审无Blocker/未接受Major，可提交本窄批；首个人工任务自动完成、刷新后持续可见性、错误非空人员和角色精确成员仍开放。

## 复现与逐条处置

- Major（已修）：新建合成 FU50049 `batch_verify_completefail1017`，PROCESS50751/binding51070、TASK50752/binding51072；Intake→Review→MI，个人-only，显式故障listener在assignment事件清空办理人。DW validate0错误/警告，正常Deploy。实例 `4991b39c-c9cd-11f1-a0ed-d2843fd6a5be` 的Save没有创建任务，真实UI Approve→Confirm后生成两条无人办理任务 `74ee0301-c9cd-11f1-a0ed-d2843fd6a5be` / `74eb6aed-c9cd-11f1-a0ed-d2843fd6a5be`，却显示绿色Operation successful，接口没有结果观测。
- 根因：创建前资格检查不是创建后实际结果；普通Complete的void链未将结果传回UI。冻结集合构造成功后返回服务器内存计划，原本地完成事务结束后才观测；独立DTO只增可选data。旧void调用继续委托原行为，无新能力/其他操作返回null，不增加读请求。观察异常只能UNCONFIRMED，不重试/补偿或回滚已经成功的引擎动作。
- 设计自检Major（已修）：仅按同pid/definition/node扫描不够；并行宿主同节点的其他角色池可能无assignee，不能当作本批个人缺主，更不能返回其taskId。改为复用已有两条批量读：live任务列表提供真实办理人，MI status提供taskId/节点/表/完整行键。先按taskId唯一联结，再与本次严格集合的冻结表名/PK集合/canonical row key相交；最后核live实例、定义、节点，只返回自己范围内明确缺主的个人任务。无客户端scope授权、无新引擎API、无逐行详情HTTP，也不使用ETI缓存办理人断言真实归属。
- 缺身份、重复taskId元数据、字段集合不完整、跨表/跨节点/其他行、live列表分页不全及查询失败均未确认。MI身份列表超过200也明确未确认，不静默截断。现有status里的历史兼容/元数据读取并不构成冻结行身份的完整保证；匹配不上不猜列名，不输出他scope定位。
- 页面收到FAILED或UNCONFIRMED后仍关闭完成对话框并离开已完成任务，只显示对应警告，明确动作已完成、不要重复完成。旧空data仍走原操作成功提示。duration0仅当前会话不自动消失，不是持久化状态。

## 范围 / 影响链

`MiCollectionVariableBuilder`冻结5参路径→`TaskApprovalCompletionComponent`服务器计划→`TaskProcessComponent`新@Transactional入口→原ActionPostEmail supplier→Controller事务外只读观察→可选TaskCompletionResult→task API envelope→useTaskActions→en/zh-CN/zh-TW。旧void方法、其他任务动作、缓存失效和邮件发布保留。ActionPostEmail只识原ProcessInstanceInfo或task上下文，服务器计划没有进入邮件、Flowable变量、完整快照或API响应。

帮助指南技能同步现有Sub-Table文章三语、llms索引/正文以及同一公开路由的实际验证；未新建文档站、导航叶或虚构功能。无Entity/SQL、数据库列、共享jar、包格式、环境变量或引擎源码修改。

## 红→绿 / 测试

- 同一合成流程的真实草稿行经Save→UI Complete，旧接口无观测及绿色成功为RED；不能重复完成已结束任务，GREEN使用同设计的新合成实例，没有重放旧动作、改派、修复或取消失败任务。
- 新前端3分支测试关闭warning条件后2失败，恢复后通过；新测试加既有canonicalization/delegate共17项通过。后端Controller观测线在隔离副本临时移除：5项2失败0错误；恢复后通过。
- Spring真实CGLIB事务代理+H2合成写测试：新入口中事务确实活跃、写入已提交；Controller调用观察时没有活动事务，且查询异常仍返回success+UNCONFIRMED。该测试证明调用边界，不冒充跨服务回滚或真实任务数据库的整批事务测试。
- 关闭完整PK身份匹配：8条scope测试3失败0错误，包含foreign行泄露反例；恢复后通过。独立reviewer核11份Java SHA，独立跑Controller5+scope8+initial8+collection33，共54项，0失败/错误/跳过；另独立前端3项通过。
- 隔离Portal package含ProcessStart*、SubTableBatch*、MI、任务隔离/完成、WorkflowEngine*及ActionPostEmail*：46类545项0失败/错误/跳过。没有IDE污染target/classes参与打包。
- Portal Vite build4183模块、22.57秒通过。vue-tsc仍383既有错误；本次相关代码/测试新增错误0。新测试beforeEach错误已修，未降规则。不能报告全类型检查PASS。
- 帮助页22项测试、125模块build及完整typecheck通过；新镜像公开页面验证151/151通过，含动作已执行不要重试和未核实含义的新增实际DOM断言。

## 真实新镜像 / 数据结果

Portal jar SHA256 `0962AA571E66C766F5B94462B784B55390905E9736F70D45E3D27C3B4FB76FD5`；Portal `sha256:16d43a2b58fc12b06179264a7d193fe551957b1b07c89204e76d7a1f1a8ea539`，前端 `sha256:11645c97eaecc9dc26f795d11ba86b796082a67cbf3aec72f2bac4bcd6ed2c61`，Help `sha256:becc1a642efcca7b18bbd2107557c83b4e528de348f11406ebd29692d674074f`。打包→复制jar→对应服务重建，healthy后才验证，没有只跑test就测旧镜像。

- 故障FU新实例 `7e4a2e94-c9cf-11f1-a0ed-d2843fd6a5be`，真实任务 `ad38f389-c9cf-11f1-a0ed-d2843fd6a5be` / `ad383135-c9cf-11f1-a0ed-d2843fd6a5be`。Save无新增任务，完成success+FAILED两个本人范围taskId，页面只有缺主警告、pageerror0。
- 正常既有FU50046 `batch_verify_assignuuid1017` 使用不同entry_token/owner_ref/item_label/units及UUID主键：实例 `c8ddd342-c9cf-11f1-a0ed-d2843fd6a5be`，实际任务 `e076ef67-c9cf-11f1-a0ed-d2843fd6a5be` / `e074cc83-c9cf-11f1-a0ed-d2843fd6a5be` 均为正确目录目标；返回UNCONFIRMED，不误报缺主，也不虚构全部逐行目标已验证，只有未确认警告、pageerror0。
- 固定只读ACT_RU_TASK查询，旧失败/新失败/正常实例为“任务数/非空办理人数”2/0、2/0、2/2。不以HTTP200或toast证明实际任务；没有目录成员写入或已生成任务补偿。
- 最终新镜像完整 `pnpm -C frontend run regression:mi`：234单测+6浏览器场景全部通过，截图保留2026-10-17系列。

## 截图（绝对路径）

目录 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`：`2026-10-17_complete-outcome-before.png`、`2026-10-17_complete-outcome-failed.png`、`2026-10-17_complete-outcome-failed-narrow.png`、`2026-10-17_complete-outcome-uuid-unconfirmed.png`、`2026-10-17_complete-outcome-uuid-unconfirmed-narrow.png`。提示截图不含人员/邮箱；人工查看1366及600窄屏文字/关闭按钮未被遮挡。

帮助截图 `F:\hermes1.0\Workflow-Station---sun\frontend\developer-workstation\verification-screenshots\2026-10-17_help-sub-table-batch-outcome.png` 及 `2026-10-17_help-sub-table-batch-outcome-narrow.png`；使用现有帮助入口，文字显示完整，不含业务样本或PII。没有删除任何截图。

## FU 生命周期 / 安全 / 性能

通用性由冻结source controls、严格构建集合、真实节点/表/完整PK及实例定义驱动，新建故障FU和既有UUID FU均真实验证。未修改Save/Load、Export/Import、Clone、Version/Rollback、Admin两条Import、Deploy产物格式，因此无新增容器remap；不能由本批宣称全生命周期放行。

仅有新计划时调用两条已有批量查询；live上限100且总数不完整明确未确认，身份元数据超过200明确未确认；不承诺200行全核对或性能提升。历史status内部元数据/姓名解析成本未由本批重构，压测仍开放。没有新增跨服务global事务、独立批量任务操作、额外任务分派通知或工作流算法。

## 最终门禁 / 残余与范围外

独立最终staged27文件完整重审无新增Blocker/未接受Major，diff与脚本语法检查通过；506条非空新增行4类增量secret扫描0命中。独立目视FAILED、UUID UNCONFIRMED和Help窄屏截图无覆盖/PII。另核历史独立首Task测试实际使用 `ProcessStartBatchFirstCollectionTest`，非摘要中的错名；实际Surefire XML有效。本报告只补签核，无产品再改。

首个人工任务自动完成的结果计划尚未接出；无JWT生产者/间接初始路由、角色精确成员、错误非空目标、全部模式和刷新后持续可见性仍开放。内部旧void入口仍不返回新UI观测。新客户端与后端均部署后才有警告，不宣称旧客户端已具备新反馈。完整A38、P4及全部A01-A41未结束。

既有383类型错误、邮件调度缺表及旧process-key BPMN404仍范围外；没有顺手改平台调度、历史任务或现网数据。
