# Issue 跟踪仪表盘

> **活跃条目**：`.kiro/issues/index.yaml`（Open / Wontfix）  
> 本文件仅保留统计摘要和当前待处理清单。

## 统计 (截至 2026-10-21)

| 状态 | 数量 | 位置 |
|------|------|------|
| 🔓 Open | **29** | `index.yaml` |
| ⏸️ Wontfix | **2** | `index.yaml` |
| ✅ Fixed | **见 index.yaml** | `index.yaml` 内 `status: fixed` 条目（含 #1403–#1404、#1409+、#1461–#1495、#1497–#1500、#1504、#1512–#1519、#1521–#1530、#1532–#1539、#1541–#1636、#1639–#1650、#1653–#1656、#1658、#1663–#1665、#1667–#1669 等） |


按严重度的分布见 `index.yaml` 各条目的 `severity` 字段（`status: open` / `fixed` / `wontfix`）。

2026-10-21：#1692 已修复 Portal Select 三入口多选配置丢失及数组默认值标量化；全量2406项、build、容器重建及隔离响应UI红→绿通过。未验证实际业务保存回显及双FU样本，类型检查存量错误未变。见 `docs/verification/2026-10-21-select-multiple.md`。

2026-10-21：创建手工Select测试FU50097时另发现#1693：中文FU名称生成的流程code不满足XML ID规则，创建失败；暂用英文名称完成测试FU，范围外问题待处理。

2026-10-20：#1687 已修复，Email Monitor `HTML_RICH` 保留 editor 格式、发信 `${richText:field}` 净化后插入 HTML 正文；engine/DW 单测与截图回归通过。

2026-10-19：#1673 已修复，统一批量子表绑定 ID 解析并细化保存/发布错误提示；FU50022 校验通过，隔离副本50074真实界面保存后部署成功。

2026-10-19：#1674 已修复，保存提取事件原始源并兼容已发布的框架空适配器；原FU50022 Portal加载由400恢复200，副本50075真实保存不再写入空事件。未验证完整审批及任务批量写入。

2026-10-19：#1675 已修复，批量统一赋值显示选中数量、零选择时明确提示且合并重复错误。隔离UUID样本真实新增3行、统一赋值6并保存通过，原有2行保持不变。

2026-10-19：#1676 已修复，同表双外键批量增改遵循绑定归属而非全部FK非空。隔离50078同任务修前400、修后新增/修改保存成功，原ref数据不变，错误主记录关联仍403。

2026-10-19：#1677 已修复，Batch Assign批量解析已选人员姓名并保留搜索/统一分派选项，提交仍为用户ID。41项前端与4项后端测试通过；原任务UUID→姓名，两条样本反复打开取消后数据不变，pageerror0。未重跑完整审批流程。

2026-10-19：#1678 已修复，General按显式分派字段解析姓名，不改写用户ID；用户确认问题已经解决。隔离申请的数据载入阻断记录保留，不视为自动化页面验收通过。

2026-10-19：#1684–#1686 收口：Record Note单一申请身份、前端失败基线及嵌套FK/PK上下文已修正。Portal2378/DW1151全量通过，独立PG/H2事务31项与MI234+6通过；完整通知链路和未保存祖先行真实Add仍未独立验收。

2026-10-19 批处理最终增量：宽表输入1000→150、隐藏选项153→0；主键解析与审计精确读取/多值写入优化完成。最终Portal2381/DW1151、后端专项444、common470（21跳过）、PG/H2共31及MI234+6通过；两份分派样本重开姓名正确且存储ID不变。已有库需显式执行96号索引SQL。用户要求按“能用即可”收口，#1672严格性能优化保留open但不阻塞本轮；停止Clone/Restore旁支，不改权限，A39仍暂缓。证据见 `docs/specs/subtable-batch-operations/batch-wide-render-budget-review.md`。

2026-10-14：#1682（原目标分支 #1669）已修复 DW Public 只读设计器返回列表、子表导航及错误保存确认；两个 Public FU 的截图与交互回归已保留，账号本人登录验证仍待实测。

2026-10-14：#1683（原目标分支 #1670）已修复同名导入、绑定视图和创建目标的工作区权限缺口及 SYS_ADMIN 方法门禁；143 后端/48 前端回归通过，服务端拒绝路径与浏览无隐式写入已验证。

2026-09-18 复核补丁已并回 #1636 / #1639：同表交集行只递增一次版本、删除必须带读取版本、同父表不同 FK 正确投影，旧实例写校验改读 pinned catalog。

---

## 当前待处理 (Open)

| ID | 严重度 | 分类 | 描述 |
|----|--------|------|------|
| 1679 | major | quality | TaskActionService 引擎/lookup 异常静默返回空列表（目标分支原 #1666，合并时避重编号） |
| 1666 | critical | architecture | Batch A39：引擎启动后本地落库及补偿均失败缺持久隔离；用户要求暂缓，仍阻塞 P4 发布 |
| 1402 | major | architecture | VirtualGroupTaskServiceImpl 与工作流引擎未集成 |
| 1405 | major | bug | ACTION 表单弹窗提交无服务端 API |
| 1406 | minor | quality | 多实例状态接口用户名为 User-{id} 占位 |
| 1407 | minor | quality | 动作测试仍为占位实现（流程 simulate 已接入 BpmnProcessSimulator） |
| 1408 | minor | quality | 表单 boundTable 深绑定校验缺失 |
| 1501 | major | bug | Main Table View / Relation Tables 列表未应用 Input 敏感打码 |
| 1531 | minor | quality | Form Business Rules 未交付脚手架（DW 无入口 / Portal 无传参 / 库内 0 数据）— 技术债，非线上缺陷 |
| 1599 | minor | bug | FU 部署新 catalog 版本后 Portal 发起 403 version mismatch，最长持续 5 分钟（processKeyCache 未失效） |
| 1540 | minor | quality | Views CSV export：FE 注释称不分页但 body 仍带 size；BE 用 maxRows 覆盖 — 契约需澄清 |
| 1616 | major | bug | Meeting Assign Participants：引擎 SubTableDataInjector 查物理表 participants.main_record_id |
| 1617 | major | bug | Showcase Submit Application：部署缺少 DMN showcase_amount_tier |
| 1637 | major | bug | FormConfigJsonTableProvisioner 建子表时 FK 列标 `isForeignKey=true` 但不写 `ref_table_id`，运行时消费者（按 ref_table_id 关联）读不到目标 |
| 1638 | minor | quality | developer-workstation 基线红：MemberControllerTest 15 个 403、AuditorReadOnlyControllerContractTest 1 个 upload 端点缺 `@RequireDeveloperPermission`（已在 HEAD worktree 证实非新引入） |
| 1651 | major | security | Vault 失败日志和部分 400 响应带上未脱敏的原始响应体，脱敏可被绕过（调试期间暂不改） |
| 1652 | minor | quality | Vault 403 单测的响应体没有 JWT，脱敏逻辑未被断言（随 #1651 再补） |
| 1657 | major | architecture | `we_email_processed_messages` 的 REVIEW/FAILED 没有任何 API/UI/告警 → 「转人工复核」实为静默丢弃（2026-10-07 已实现又按用户要求撤销，暂不做） |
| 1659 | major | deploy | init-scripts 注册表漂移：00-schema 下 4 个 SQL 不会在全新库执行（87 两处都漏，83/84/85 的 dw-binding 三个文件漏在 init-database.ps1） |
| 1660 | minor | quality | workflow-engine-core 基线红：TaskManagerComponentMultiInstanceTest 5 个用例因 taskClaimSupport 未注入 NPE（已在 HEAD worktree 证实非新引入） |
| 1661 | major | bug | FU 克隆/导入/回滚时 FORM_POPUP Action 的 `configJson.formId` 不重映射，弹窗仍指向源 FU 表单 |
| 1662 | major | bug | platform-messaging `DefaultErrorHandler` recoverer 空实现，Kafka 重试用尽后消息静默丢弃（DLT 从未写入） |
| 1666 | major | quality | TaskActionService 引擎/lookup 异常 catch→emptyList，与「无 actionIds」无法区分（Allowed Roles 轮次 3-A 暂不修） |
| 1688 | major | security | Send Email HTML 正文 `${subTableField}`/`${lookupField}` 未转义；标量值内 `${x}`/`${env:KEY}` 会被二次展开 |
| 1689 | minor | quality | DW 重新部署后已打开页面懒加载旧 chunk 404，Email 模板 Body 等异步组件静默空白（无 preloadError 处理） |
### Wontfix

| ID | 分类 | 描述 |
|----|------|------|
| 095 | quality | auth.ts 三端重复 — 无共享包，维持各应用内模块 |
| 1520 | architecture | 并入 #1531 — mathjs 属未交付脚手架，非双引擎并存 |

---

## 复发问题模式（排查索引）

| 主记录 | 场景 | 相关 |
|--------|------|------|
| **#179** | MI 待办/申请：Link Form、表格下内联 **subtable2** 跨节点不带数、空白、id 串人、Current node 串行 | #177、#178、#1383 |
| **#1392** | MI 子任务主表字段透传：BPMN previous-form BFS 误从 MI 子流程 startEvent 起跑 / 缺父子流程感知 / 同名约定无诊断；同时存在 SubTaskForm.vue 死代码副管线违反 portal-design-parity | #1396 |
| **#1396** | MI 字段透传 Path 2 readonly 快照剩余两条 BFS 缺口：`MI 子任务 → 后续主流程`（subProcess 内部 user task 不可达）+ `intra-MI`（同 MI 内 sub form1 → sub form2 兄弟不可达）；引入 subProcess 内部递归 + 内部 BFS 一并补齐 | #1392 |

| **#1435** | MI People 内联表单：Save 后 id 空 / hydrate 被跨参与者 PK 覆盖 / UUID 被 scrub 覆写 | #1437、#1438 |
| **#1438** | MI sub form1 **Shared Attachment**：新增 1 条后旧附件 UI 消失；foreign-id 误删合法 file 行；Sub Task 泄漏纯 id+file | #1435、`portal-mi-subtable-my-request.mdc` §Shared Attachment |
| **#1439** | MI **sub form2** People 未继承 sub form1 的 age/sex/name；stale `sub_task_id` + collapse pick-one | #1437、`portal-mi-subtable-my-request.mdc` §sub form1→2 |
| **#1440** | MI People **内联表单**编辑（如开 sex）串入**上一参与者** age/id；nested 写入全 slice + inline merge 未 scope | #1437、#1439、`portal-mi-subtable-my-request.mdc` §nested scope |
| **#1441** | **My Request** Details / Link Form 弹窗：IN_PROGRESS 子任务 People 空白（parentChildTaskStatusesMatch 误杀无 task_status 子行） | SubTableField.vue `handleLinkFormClick` |
| **#1442** | 待办 **Sub Task Edit** 改 assignee：UI Assignee 列 stale；**MI 子任务仍分给旧人**（__subTables__ slice 64 stale vs 66 编辑，collection 先写入 64） | SubTableField.vue、useTaskForm.ts、TaskProcessComponent.selectRowsForMiCollection |
| **#1443** | MI 办理人 **sub form2**：Sub Task 子表空 + People 未带入 sub form1 age/sex；link-child scrub 把 collection 行 `id_idw` 误删 | #1435、scrub `skipSliceKeys`/`buildMiCollectionSliceKeySet`、`miCollectionIdIdwScrub.test.ts` |
| **#1444** | MI 办理人 People **Save 后表单回显旧值**：persisted 已更新但 inline form 仍显示 stale age；foreign 占位行 seed/collapse 污染 + flatten/hydrate  sibling slice 63 覆盖 30 | #1435、#1443、`flattenNestedSubTableRowsIntoPayload`、`hydrateMiLinkChildBindingsFromFullSnapshot`、task e921c696 Test-000071 |
| **#1466** | **Email Monitor** 启动流程后 Portal My Request / 待办不可见 | EmailMonitorPortalSyncComponent + internal hydrate API（已 fixed 2026-06-30） |
| **#1512** | **Email Monitor** hydrate 因引擎 GET 403 失败（无 JWT） | engineSnapshot 随 hydrate 下发，跳过引擎 GET（已 fixed 2026-08-04） |
| **#1513** | **Main Table View** 列表可见但 View Record 403（非参与者） | canAccessProcessDetail 对齐列表视图规则（已 fixed 2026-08-04） |
| **#1514** | **My Requests** manualTask 流程图节点不着色 | bpmnHumanWorkflowTasks 解析 manualTask 等（已 fixed 2026-08-04） |
| **#1515** | **Email Monitor** 收信后建案失败 `missing_function_unit_id` | 启动变量补 `functionUnitCode`（已 fixed 2026-08-04） |
| **#1467** | **Email Monitor** 转发邮件内层 HTML 未抽取 | ImapInboundMailClient nested message/rfc822（已 fixed 2026-06-30） |

详情与根因清单见 `index.yaml` 中 `id: "179"`（`recurrence: pattern`）。改 `shared.ts` / `tasks/detail.vue` / `FormRenderer.vue` / `SubTableField.vue` 时优先跑上述单测并做三参与者手测；最新一例（#1383）是当前参与者打开 sub form1 时 inline subtable2 被预填上一参与者的输入，根因在 `isolateMiSubTaskData` 重建 myRow.__subTables__ 时遗漏 MI 过滤、且 `syncMiLinkChildRowsIntoParentNested` 在空数据时不回写残留。#1438：Attachment UUID 不得进入 `foreignSubTableRowIds`；有 `file` 的行在 `isLeakedForeignRowOnSharedAttachment` 中 MUST 先判定保留；`patchFormDataSubTablesFromCurrentBindings` 对 shared attachment MUST merge 全量快照。手测 task 093962c4 Attachment 3 行。#1440：`patchMiParentRowsWithNestedChildSlice` MUST `scopeMiLinkChildRowsForParentRow`；`syncMainSubTableRows` binding.data 用 merge 后 `out`；inline `mergeRowsForInlineFormTarget` nested 须 `pickMiLinkChildRowsForParent`。手测 `verify-sex-toggle-isolation.mjs` task 6c6c5cc6。#1444：Save 后 variables 正确但 People inline 仍 stale → flatten 须让顶层 slice 覆盖 nested；hydrate 须让当前 binding slice 覆盖 stale sibling（如 63 vs 30）；seed/collapse 不得处理 foreign 占位行。手测 `frontend/scripts/_cap-e921.mjs` task e921c696 Test-000071。改 `useBpmnParser.ts` 时优先跑 `useBpmnParserPreviousForms.test.ts`（8 case 含场景 1/2/2-regression/3/4 + boundary + dedup + missing-current）。

---

## 跟踪系统说明

issue 详情为机器可读 YAML：

- **待处理 / Wontfix**：`.kiro/issues/index.yaml`
- **已修复**：`index.yaml` 中 `status: fixed` 条目

查看所有未修复问题：
```bash
grep "status: open" .kiro/issues/index.yaml
```

查看特定分类（活跃）：
```bash
grep -A2 "category: security" .kiro/issues/index.yaml | grep "status: open"
```

按 ID 检索已修复条目：
```bash
grep -A20 'id: "1404"' .kiro/issues/index.yaml
```
