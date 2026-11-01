# Case Correspondence 只读显示与 Batch 配置复核

## 结论

用户确认的 FU 是 MCY dual binding 1101 1793499506034（50125），不是原始 MCY FU 48。本轮没有修改任何 FU 的设计配置或发布版本，没有推进用户的请求。

用户请求 309fbb11-d5b4-11f1-875d-627eadcfb631 的 Case Correspondence 没丢：数据库和详情 API 中，note=3 的行及 related_case 均存在，另两条 note=1/2 属于各自的 Transaction。

默认 General 使用 Transaction Request Form - Sub Case，该表单没有放置 Case Correspondence，这是设计配置。点击流程图 Case Submission 应能查看 HMDC Case Form；修复前这里也显示 No Data，则是平台读取缺陷。本轮已修复，原请求无需重新提交或补录。

## 修复验证

- **复现**：Playwright 打开原链接，点击 Case Submission、等待加载完成，Case Correspondence 为 No Data；同时 read-only 查库及 API 核实三条记录仍在。
- **根因**：ApplicationDetailNodeFormMap 为非当前节点只传主表 tableId/name，遗漏发布配置中的 primaryKeyFields/fieldDefinitions。FormRenderer 的现有外键投影无法确定主表身份，按安全规则返回空切片。
- **红→绿证据**：新增 nodeFormPrimaryIdentity.test.ts 两项均先失败；补齐元数据传递后两项及既有投影组共22项通过。构建、重建 Portal frontend 后，原单同一节点显示且仅显示 note=3。没有改变 FK 投影规则、权限、写入归属或未知主键时返回空的保护。
- **未验证项**：原用户的独立账号登录未代替操作；以 FULL developer 只读查看该单。Export/Import/Rollback、未配置的 Role 路径及远程环境没有重跑。没有修改其业务数据。

实际补充样本：

- 101523e7-d5af-11f1-875d-627eadcfb631：普通双绑定，Case 节点显示1行 Case note，不显示两条 Transaction note。
- 1eaadb14-d5b0-11f1-875d-627eadcfb631：显式额外 Case FK，Case 节点显示3行，符合设计声明的重叠归属；没有为了强行互斥而隐藏合法行。
- 所有上述页面 pageerror=0。原用户 Correspondence 字段、顺序和版本与初始 API 快照逐值一致。API 的交易状态 overlay 与数据库原值可能不同，不将它当业务数据修改。

## Batch 配置及实际入口

FU 50125 的最新发布版本已经是1.0.2，Assignment 表单50898、交易 binding51443 已开启 Allow Batch Edit 和 Allow Batch Assign，Batch Form 使用声明的人员 Lookup 与 MI Assignment。本轮核实已开启，没有重复发布或覆盖用户设计。

入口：新建该 FU 的 Request，进入 Transaction Assignment，Claim 后勾选交易，点 Batch Edit，再选 Assign by person、人员 Lookup、Apply to page，最后 Confirmed all Assignment。此模板只配置人员模式，Role 卡片禁用是现有配置，不是批量开关失效。

新请求地址：
http://localhost:3000/portal/processes/start/mcy-dual-binding-1101-179349950603-20261101-5srx1c

已有单按创建时的发布版本冻结。自己创建的旧测试任务33b742d3-d5ae-11f1-875d-627eadcfb631（1.0.0）在 Claim 后打开 Batch，复现 Unsupported Batch Form control: input。其旧配置是普通 Input，不是最新人员 Lookup。仅打开工作区，未 Apply、未改行，随后 Unclaim 恢复。没有改写旧版本快照来让旧单冒用新配置。

用户给出的309fbb11请求已经结束，为只读详情，本身不是批量分派入口。

## 本轮完整流程

新建请求9c756819-d5b6-11f1-8c70-6e7849f291cc（FU50125，1.0.2）：

1. New Request 两笔交易，各自新增 Correspondence，另增 Case Correspondence。
2. Save Draft、重开，编辑A子行、删除替换B子行、编辑Case子行，再保存重开。
3. Case Submit，进入 Assignment，Claim、勾选两笔、Batch 人员分派、Apply，两行 Edit 检查各自email/交易号后缀。
4. Confirmed all Assignment，响应 body 的 batchAssignmentVerification.status=VERIFIED，issues为空且historyRecorded=true。
5. A走 Request Approval → Approve → Mark Completed，B走直接 Mark Complete → Mark Completed，最终引擎结束、有end_time、活动任务数0。
6. 每一步完成后 Correspondence 值、顺序、版本不变；历史实际操作逐任务匹配，最终 My Request 重开 Case Submission 后仍显示1行正确Case note，pageerror=0。

模板的 Mark Complete actionType 仍是 REJECT，所以整单终态 REJECTED；没有修改该配置来改变测试结果。

## 门禁

- pnpm exec vitest run src/composables/applicationDetail/__tests__/nodeFormPrimaryIdentity.test.ts：修复前2失败，修复后2通过；联跑 subTableFilterProjection.test.ts 共22通过。
- pnpm test：基线2611通过/1失败，本轮2613通过/同1失败，均为既有 viewDetailForm.test.ts。没有修改既有断言或跳过测试。
- pnpm typecheck：基线381、本轮381；归一化因新增源码行而移动的位置后，逐项诊断无新增。两次均未通过，不宣称类型全绿。
- 三个受影响文件定向ESLint：新测试无诊断，两个存量模块37条既有no-explicit-any。对HEAD原文和最终原文分别以stdin重跑，对照规则、消息和源码文本逐项一致，无新增；未顺手清理旧any或降低规则。
- pnpm build 成功；docker compose -f docker-compose.dev.yml --env-file .env up -d --build user-portal-frontend 成功，容器日志确认新产物运行。
- pnpm run regression:mi：250单测及6个真实浏览器场景通过，复用正常登录的会话和既有FU50029，无接口mock。
- 没有后端、数据库schema、依赖、用户文案或设计配置格式变更。

日志保留于忽略目录：.codex-1101-case-node-red.log、case-node-green.log、case-baseline-unit.log、case-final-unit.log、case-baseline-types.log、case-final-types.log、case-build.log、case-rebuild.log、case-mi.log、case-primary-new.log、case-primary-flow.log（共同前缀 .codex-1101-）。初次服务重建期间登录返回上游未就绪，等待服务并正常登录后重跑成功，不修改应用或认证规则。

## 截图

位于 frontend/user-portal/verification-screenshots/，保留本地截图供人工复查：

- 修复前同节点：2026-11-01_user-case-correspondence-waited.png。
- 原单修复后：2026-11-01_case-primary-309fbb11-after.png，note=3一行可见。
- 普通双绑定：2026-11-01_case-primary-101523e7-after.png。
- 显式双外键：2026-11-01_case-primary-1eaadb14-final.png，三行合法重叠可见。
- 新完整流程结束后：2026-11-01_case-primary-9c756819-final.png。
- 最新版真实Batch人员选择：2026-11-01_fullflow-9c756819-batch-selected.png；最终完成：2026-11-01_fullflow-9c756819-completed.png。

默认General未放置的子表仍不自动追加，保持设计parity。若希望进入详情默认就展示Case Correspondence，应在Sub Case/My Requests对应设计中显式配置该绑定及控件；本轮未擅自改变布局。
