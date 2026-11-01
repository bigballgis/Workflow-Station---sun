# 2026-11-01：MCY 全路径浏览器验收

后续 Batch Assign 专项补验：两份 FU 各6份新请求全部经Batch入口走到结束，见 [Batch Assign 全路径验收](2026-11-01-batch-assignment-all-paths.md)。下文保留本报告当轮的14份验证范围，不将单行Edit分派混算为Batch。

## 结论与范围

本轮相对 c21d0e7e7 修复三处平台通用缺陷：My Request 将未走过的 MI 出口标绿、REJECTED 终态的 MI 外框仍橙色、实际 Reject 的任务历史误显示 Approve。全部经过失败页面或失败测试取证后修复，构建并重建服务后复验。

最终部署上完成 14 份请求：两种外键配置各跑三种混合分支、三种整单同分支，共 12 份；另有两份从 Batch 分派与逐行回填继续至结束。不是把不同请求的局部操作拼成一条完整流程。

本次增量审查 PASS；全仓门禁仍 CONDITIONAL，已确认的基线失败见下文。原 FU 48 及用户任务 0d845607-d5a6-11f1-a759-6acd226d917b 未被修改或推进。

## 实际发布路径

1. Investigation 的 Request Approval → Approval 的 Approve → Mark Completed → 交易结束。
2. Investigation 的 Request Approval → Approval 的 Reject → 拒绝出口。
3. Investigation 的直接 Mark Complete → Mark Completed → 交易结束。

每个请求都有两笔 Transaction、各自的 Correspondence，以及独立 Case Correspondence。先新建 → 保存草稿 → 重开 → 核对两笔交易的子行隔离 → 编辑 A → 删除并替换 B 子行 → 编辑 Case 子行 → 再存并重开 → Submit → 分派 → 两笔交易各自办理 → 引擎结束 → My Request 重开 → Flow History 核对。

12 份使用冻结版本 1.0.0，按实际启用路径而非按钮名称推断。顺序包含优先办完一笔、先推进两笔再审批等不同交叉安排。

- 普通双绑定；A: Request Approval → Approve → Mark Completed；B: Mark Complete → Mark Completed；实例 043d9856-d5ad-11f1-875d-627eadcfb631
- 普通双绑定；A: Request Approval → Reject → 结束；B: Request Approval → Approve → Mark Completed；实例 1c0fec43-d5ad-11f1-875d-627eadcfb631
- 普通双绑定；A: Mark Complete → Mark Completed；B: Request Approval → Reject → 结束；实例 31f95bf2-d5ad-11f1-875d-627eadcfb631
- 显式额外 FK；A: Request Approval → Approve → Mark Completed；B: Mark Complete → Mark Completed；实例 45b6754d-d5ad-11f1-875d-627eadcfb631
- 显式额外 FK；A: Request Approval → Reject → 结束；B: Request Approval → Approve → Mark Completed；实例 5b2dc23a-d5ad-11f1-875d-627eadcfb631
- 显式额外 FK；A: Mark Complete → Mark Completed；B: Request Approval → Reject → 结束；实例 6ff8c999-d5ad-11f1-875d-627eadcfb631
- 普通双绑定；A: Request Approval → Approve → Mark Completed；B: Request Approval → Approve → Mark Completed；实例 83ab0e84-d5ad-11f1-875d-627eadcfb631
- 普通双绑定；A: Request Approval → Reject → 结束；B: Request Approval → Reject → 结束；实例 9af37179-d5ad-11f1-875d-627eadcfb631
- 普通双绑定；A: Mark Complete → Mark Completed；B: Mark Complete → Mark Completed；实例 aece9b26-d5ad-11f1-875d-627eadcfb631
- 显式额外 FK；A: Request Approval → Approve → Mark Completed；B: Request Approval → Approve → Mark Completed；实例 c24caf9f-d5ad-11f1-875d-627eadcfb631
- 显式额外 FK；A: Request Approval → Reject → 结束；B: Request Approval → Reject → 结束；实例 d9554a74-d5ad-11f1-875d-627eadcfb631
- 显式额外 FK；A: Mark Complete → Mark Completed；B: Mark Complete → Mark Completed；实例 ecb2b8a1-d5ad-11f1-875d-627eadcfb631

## Batch 入口的完整流程

测试副本 FU 50125 / 50126 发布到 1.0.2，配置使用自身表单中声明的人员 Lookup，MI Assignment 容器以及可编辑的回填字段。开启 Allow Batch Edit 与 Allow Batch Assign；选择 Assign by person 后勾选两行并选择用户，Apply to page 对每行分别运行 change。

测试沿用已有 merchant_name 列作为回填目标，Batch 显示标题为 Assignee email，脚本在 email 后附当前行 card_number，证明读取的是当前行上下文。此字段映射仅属于测试 FU 的设计配置，不是平台判断条件；平台不要求业务必须使用这些列名。

两份均经 Batch → 两行 Edit 检查回填 → Confirmed all Assignment → Request Approval / 直接完成 → Approve → 两个最终任务 → 引擎结束。查库确认两行 email 各自保留交易号后缀，人员值仍为 Lookup 对象。结束后重新打开 My Request，点击 Assignment 节点，再打开 Details，浏览器字段值仍正确。

- 普通双绑定；版本 1.0.2；实例 101523e7-d5af-11f1-875d-627eadcfb631
- 显式额外 FK；版本 1.0.2；实例 1eaadb14-d5b0-11f1-875d-627eadcfb631

负例也保留：旧 1.0.1 仅允许 Batch Assign，回填普通字段被拒绝；发布新版本后重新打开旧任务仍拒绝。未将只读字段放行，没有把最新设计覆盖旧冻结版本。配置缺少 miAssignment 时，发布校验明确拒绝。表格数据加载未完成即打开工作区时，既有 stale 检测也保持有效；测试等待加载完成后办理，没有删除或绕过检测。

## 每份请求的判据

- 每次完成返回成功 body，任务确实推进；按请求的每行分支核对 Investigation / Approval / Mark Completed 数量及实际结束事件数。
- 每次完成后将 Correspondence 的字段值、数组顺序和行版本与基线逐值比较，未出现无意写入或串行。
- 最终活动任务数为 0，Flowable 有结束时间，Portal 当前节点/处理人为空。
- 按实际历史活动 ID 对照浏览器 SVG computed stroke：执行过的节点正确着色，没走过的出口及人工节点保持灰色，MI 外框不残留橙色。
- Flow History 每个 taskId 的 operationType 等于该次请求的实际操作，用户备注保留，不显示内部审计标记；浏览器 pageerror=0。

## 修复与红 → 绿

### 未执行的出口被标绿

原 My Request 在 MI 已结束时直接将所有内部 endEvent 标绿，并按结束节点名称猜拒绝状态。真实实例 2a037e33-d5a8-11f1-a759-6acd226d917b 没有拒绝出口历史，页面却标绿。

修复：复用 Task 已有的 resolveEndEventDiagramStatus；历史活动 ID 优先，别的同名活动不能匹配该出口，保留真实无 ID 记录的兼容入口。删除只服务于原出口推断的 completedMultiInstanceSubProcesses 扫描。

两文件初始回归测试 5 项失败、1 项通过；修复后通过。真实同一实例中的未执行 Rejected 出口恢复灰色。

### 拒绝结束的 MI 外框仍为 Current Step

全拒绝实例 d9e0a289-d5aa-11f1-a759-6acd226d917b 引擎已结束，但未走 Mark Completed，外框仍橙色。原代码只处理 COMPLETED 终态。

修复：已进入的 MI 容器在 COMPLETED / REJECTED 两种正常终态均不再标 current；不要求可选业务状态列。未进入的容器仍 pending。新增回归先红后绿；同一失败实例的外框恢复绿色，未执行任务保持灰色。

### Reject 被历史接口当成 Approve

原生 Flowable 正常完成任务的 deleteReason 为空，历史装配器据此默认 APPROVE。实际点击 Reject、路由也已拒绝，Flow History 却显示 Approve，页面和失败测试均已复现。

修复：在现有 TaskCompletionService 完成事务、现有授权校验之后，以实际操作写入 taskId 绑定的原生审计 comment，随后完成任务；读取历史时优先使用该证据。使用 workflow-completion:操作 类型，正文为空，避免旧读取器把内部标记当用户备注；原用户备注写入位置与顺序保留。无额外查询或 schema 迁移。

新增 writer / reader 两项先失败后通过；另有 5 项覆盖正式操作、旧备注、损坏标记拒绝。最终 14 份新请求每一步实际操作与历史一致。旧完成记录缺少真实操作证据时维持旧兼容判断，不按节点名称或当前流程变量猜填旧记录。

## 编译、部署与门禁

- Portal frontend build 成功，user-portal-frontend 重建后验证；最终前端全量 2611 通过、1 项基线失败。TypeScript 修复前/后 381/381，逐项诊断无新增。
- Workflow Engine 及依赖 package 成功。相关组共 458 项，0 失败/错误，197 条件跳过，261 项实际通过；新增 7 项全部通过。随后重建 workflow-engine，健康检查通过。
- 引擎扩展组在修复前已跑：473 项、4 失败/17 错误/197 跳过，其中包含两个刻意新增的反例。修复后同组 478 项、2 失败/17 错误/197 跳过；无新增失败。剩余三类失败均为既有 H2 与 Postgres assignment 表/SQL 不兼容及对应断言；只在成功 package 命令中排除这些已对照确认的类，不修改测试或断言。
- 完整 MI：250 项单测、6 个浏览器场景通过，使用既有 FU 50029。最终运行曾因旧验证 Cookie 过期失败；用正常密码登录刷新会话后完整重跑通过，不修改应用或测试断言。
- 早一轮 Portal 后端的 633 项（613 实际通过、20 条件跳过）与两项 Postgres IT 基线失败的说明继续见前份报告。本轮未更改 Portal 后端。
- 对整个修复范围的 79 个运行时文件扫描新增业务名称判据，MCY / HMDC / 测试 FU ID / Correspondence 测试列名为 0 候选；逐处检查判据来自配置、任务身份与实际历史。会话、Cookie、临时脚本、日志、截图均不提交。

本轮构建及数据检查日志保留在本地忽略目录，主要为 .codex-1101-outcome-deploy-package.log、.codex-1101-allpaths-final2-unit.log、.codex-1101-allpaths-final2-types.log、.codex-1101-audit12.log、.codex-1101-batch-enabled-resume2.log、.codex-1101-delivery-mi-fresh.log。

## 截图

实际检查过的图片位于 frontend/user-portal/verification-screenshots，远程 PR 需要另附。

- 修复前未执行出口标绿：2026-11-01_end-node-false-green-before.png。
- 同实例修复后：2026-11-01_end-node-false-green-after.png。
- 修复前拒绝终态仍橙色：2026-11-01_fullflow-d9e0a289-failed.png。
- 同实例修复后：2026-11-01_mi-rejected-frame-after.png。
- 修复前历史误标：2026-11-01_reject-history-before.png。
- 最终全拒绝：2026-11-01_fullflow-9af37179-completed.png / 2026-11-01_fullflow-9af37179-history.png。
- 最终全直接完成：2026-11-01_fullflow-ecb2b8a1-completed.png。
- Batch 保存后重开 Details：2026-11-01_batch-persisted-email-details.png，Merchant Name 中显示对应交易 B 的 email 后缀。
- 两份 Batch 完整结束：2026-11-01_fullflow-101523e7-completed.png、2026-11-01_fullflow-1eaadb14-completed.png。
- 旧版本只读写入仍拒绝：2026-11-01_batch-old-version-write-denied.png。

文件名 completed 表示实例已经结束，不表示整单状态为 COMPLETED。测试 FU 的 Mark Complete 动作配置为 REJECT，最终 REJECTED 是实际配置结果；未按按钮名字修改平台或改业务设计来让图变绿。

## 未验证边界

这里的每条路径指上述 MCY 发布流程实际启用的三条路径。未配置的撤回/退回/加签/Role 分派不作为已通过分支；没有覆盖真实公司远程部署、原 Credit Card 发布版本、多主体并发交错或所有旧引擎接口形状。测试由具备 FULL 权限的 developer 跨节点办理，不能代替独立双账号权限验收。相关权限及 FK/版本 guard 保留，未新增授权例外。

Export / Import / Rollback / Admin 包导入本轮没有新增配置格式或字段；未重跑全部生命周期。旧历史没有实际操作证据的不能追溯恢复。本次只做本地提交，不推送远程。
