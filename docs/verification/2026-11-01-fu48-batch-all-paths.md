# 原始MCY FU48：Batch Assign配置与全路径验收

## 结论

用户补充要求原始MCY也参与后，已在原始FU48 MCY Debit Card_20260525完成Batch人员控件配置并发布1.0.133。原始结构下6份全新请求全部从Batch分派走到引擎结束。加上FU50125、50126此前12份，三份FU共18份完整Batch流程通过，合计102次页面任务完成。

此次只改FU业务设计配置，没有新增产品代码或MCY专用运行时判断。平台代码仍为f864ef183，后续提交仅为验收文档。

## 配置改动与审查

- 修改前版本1.0.132，Assignment表单164，交易binding281。Allow Batch Assign已经为true，但Batch Form中assignee_id是普通Input，和已声明的人员Lookup不一致。
- 只将Batch Form的Input替换为该表单Edit设计已经声明的Lookup，放入原miAssignment容器；保留其原始目录绑定与字段配置。没有添加业务列、列名判断、额外脚本或普通字段的批量写权限。
- 对发布后配置逐项读回：除Batch Form外三份表单配置完全一致，所有tableBindings与BPMN原文完全一致。
- 部署b1a7bcba-de6b-4ac0-9a1d-76e38fc4d74a状态SUCCESS；1.0.133快照、MI校验、包导出、Admin上传/校验及部署均成功后才测试。
- 新请求均明确冻结为1.0.133；旧单保持原发布版本，没有回写旧快照。

原始MCY没有测试副本的Correspondence表，也没有独立assignee_email业务字段。本轮不新增这些设计，不把merchant_name改为email或添加测试回填脚本；测试实例中的Merchant Name保持原业务值。原始MCY目录Lookup展示人员email，不等同于向独立email列回填。

## 六组实例

- 批准／直接完成：`a0f06b56-d5ba-11f1-8c70-6e7849f291cc`，Batch分派VERIFIED、最终0活动任务、引擎有结束时间。
- 拒绝／批准：`caa94433-d5ba-11f1-8c70-6e7849f291cc`，Batch分派VERIFIED、最终0活动任务、引擎有结束时间。
- 直接完成／拒绝：`de4b5be2-d5ba-11f1-8c70-6e7849f291cc`，Batch分派VERIFIED、最终0活动任务、引擎有结束时间。
- 批准／批准：`f039346f-d5ba-11f1-8c70-6e7849f291cc`，Batch分派VERIFIED、最终0活动任务、引擎有结束时间。
- 拒绝／拒绝：`2e939322-d5bb-11f1-8c70-6e7849f291cc`，Batch分派VERIFIED、最终0活动任务、引擎有结束时间。
- 直接完成／直接完成：`5226f011-d5bb-11f1-8c70-6e7849f291cc`，Batch分派VERIFIED、最终0活动任务、引擎有结束时间。

## 每份均实际执行

1. New Request增加两笔Transaction，填写不同Card Number与Merchant Name。
2. Save Draft、重新打开；编辑A，删除B并新增替换B，再保存、重开核对两行。
3. Case Submit → Assignment → Claim → 勾选两行 → Batch Edit → Assign by person → Lookup选人员 → Apply to page；分别打开两行Edit检查选中人员。
4. Confirmed all Assignment，必须收到服务端batchAssignmentVerification.status=VERIFIED。
5. 按指定批准/拒绝/直接路径逐节点点击、填写备注并确认办理；交替先办完一笔与交叉推进。
6. 引擎结束后重新打开My Request，核对Flow History、SVG节点和结束事件颜色，再点Case Submission核对两行交易。

本轮原始MCY共34次任务完成、12行人员Lookup持久化，人员身份均为选中的user-dev。每次完成后交易主键、父Case键、Card Number、Merchant Name及行顺序保持不变；附件空集合也不被误写。没有使用单行分派代替Batch。

审批、拒绝出口、跳过的人工节点、MI容器均按真实活动历史检查；Flow History逐taskId等于实际请求操作，用户备注保留，内部审计标记不展示。6份pageerror均为0。

## 截图

位于frontend/user-portal/verification-screenshots/：

- 批准／直接完成：`2026-11-01_fu48-fullflow-a0f06b56-completed.png`、`2026-11-01_fu48-0-case-final.png`。
- 拒绝／拒绝：`2026-11-01_fu48-fullflow-2e939322-completed.png`、`2026-11-01_fu48-4-case-final.png`。
- 直接完成／直接完成：`2026-11-01_fu48-fullflow-5226f011-completed.png`、`2026-11-01_fu48-5-case-final.png`。
- 实际Batch选择人员：`2026-11-01_fu48-fullflow-a0f06b56-batch-selected.png`。

DW配置前后截图另存frontend/developer-workstation/verification-screenshots/2026-11-01_fu48-batch-config-before.png与after.png。设计器打开时有既有dirty提示，未点Save；不能用该截图的提示判断是否保存，以配置读回、部署SUCCESS及新请求冻结版本实际运行证明生效。

## 证据与测试边界

实际浏览器使用Docker部署的Portal 3101、DW3102端口。3000入口登录POST曾超时，3102也曾有一次登录超时及一次等待load事件超时；正常登录重跑成功。没有修改认证、超时策略或应用响应来使测试通过，未宣称3000入口的间歇访问问题已解决。

第5组最初在Add保存时等待弹窗关闭超时，该次不算通过。Kong日志定位到2026-11-01 14:10:59的主键分配POST返回429；限流窗口过去后，带请求记录重跑相同步骤，4次主键分配均200、Draft/Start成功，随后整条拒绝路径通过。保留限流保护，没有加自动重发写请求、修改限额或放宽测试断言。失败首次尝试没有生成已提交流程实例；最终六份清单均为真实完整通过的请求。

本地验证驱动frontend/scripts/_1101-fu48-suite.mjs与start/flow脚本，均在忽略目录，不作为平台产品代码。日志.codex-1101-fu48-matrix.log、结果.codex-1101-fu48-matrix-results.json、逐实例.codex-1101-fu48-fullflow-实例ID.json；配置对照日志.codex-1101-fu48-config-audit.log。修改前完整表单、FU元信息及BPMN备份为.codex-1101-fu48-before-*。

最后两组续跑日志.codex-1101-fu48-matrix-resume.log；第5组重跑的主键/草稿/发起响应记录.codex-1101-fu48-start-4-resume.log。首次429失败与重跑成功的证据均保留。

这是设计配置发布与浏览器验收，无产品代码、依赖、schema或镜像产物变化，因此没有重复构建前后端或重跑全仓代码门禁。此前源代码修复的基线测试/类型/lint问题仍见case-node-primary-identity报告，不冒称全仓全绿。

本次每条路径指原始MCY已启用的三条分支及六种两交易组合。人员模式开启，未配置的Role、撤回/退回等不计作已通过；由FULL developer跨角色办理，不代替独立双账号、并发、远程环境或全生命周期验收。附件仅验证空集合保持，没有执行实际文件上传。

Mark Complete动作原配置为REJECT，所以最终业务状态REJECTED；完成指引擎已结束。没有修改动作配置来强制显示COMPLETED。旧任务没有升级或推进。

完整副本12份证据见 [FU50125/50126 Batch全路径验收](2026-11-01-batch-assignment-all-paths.md)。本报告补足原始FU48，历史报告当轮排除原始MCY的说明保留其时间范围。
