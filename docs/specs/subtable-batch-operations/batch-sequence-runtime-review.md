# Code Review：序列主键整批分配与第二行失败

## 结论 / 范围

CONDITIONAL；本批只扩展合成fixture及验收脚本，没有产品改动。新增prefixedSequence测试模式与受限策略校验，原manual/uuid保持兼容；不新增线上环境变量，不宣称P3/P4全部完成。

## Finding / 修复验证

脚本探针原以完整URL的endsWith匹配allocate接口，但真实请求带`?taskId=...`，导致漏计。真实序列测试保存后明确断言“应一次分配count2”时失败（实际捕获0），不是产品分配失败。改为URL.pathname匹配后同一条断言通过；未删减分配次数要求。此前Cancel的“零allocate”证据受此漏计限制，本批用修正探针重新验证，不把旧探针的绿当作证明。

## 实际运行

合成FU50034（TASK50719/binding51006），冻结策略prefixedSequence、prefix=SYNTHETIC-、padWidth6。成功进程 `210075fd-c890-11f1-a63d-c6848797bef7`，任务 `210bbfe4-c890-11f1-a63d-c6848797bef7`。

- `FIXTURE_TASK_ID=... node frontend/scripts/verify-batch-membership-runtime.mjs`：PASS。Add→Cancel没有allocate/submit，完整基线不变；两条新增中第二条必填空时Apply失败，弹窗/草稿保留，没有allocate/submit且服务器原行不变；填合法后一次allocate请求count2，两键不同且按冻结prefix产生、FK正确，Apply后4行；重复Add409和伪造控件403均零业务变更；删除到空reload仍空；pageerror/console.error均零。
- `FIXTURE_TASK_ID=... node frontend/scripts/complete-batch-verification-fixture.mjs`：PASS，真实Complete后COMPLETED且空集合不复活、响应无保留元数据。
- 修正探针后的兼容重跑：UUID任务 `6a8765a3-c890-11f1-a63d-c6848797bef7`、人工只读默认值任务 `6e0e90dc-c890-11f1-a63d-c6848797bef7`，相同membership脚本均PASS，重新覆盖Cancel零分配/零保存、Add/Delete及拒绝路径。
- 第一轮进程 `d371b4d4-c871-11f1-a63d-c6848797bef7` 在探针断言处失败前已合法保存4行，保留该合成样本，没有伪称整次零写入、没有回滚/删除已保存业务数据。新进程重跑证明探针修正后的全流程。
- `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-sequence-add-persisted.png` 已目视，四条合成行。initial/delete-empty/narrow同前缀截图保留，不删除。截图不展示自动PK，PK格式/唯一性由真实GET读回断言证明，不拿截图代替数据检查。

## 限制 / 安全 / 生命周期

该场景初始两行由测试启动payload预置身份，只证明后续Batch Add的真实序列分配，不证明流程发起初始序列生成。失败不承诺号段无间隙，仍不承诺跨服务全局事务。脚本读取冻结字段元数据，不按字段名猜策略；策略枚举仅测试生成器。产品产物为已部署前序补丁，无本批build/MI重跑必要；无新FU包结构，Export/Import/Clone/Rollback/Admin消费者本批N/A，完整往返仍未完成。字段类型383既有错误、多用户、Assign同工作区与真实分派链、Preview/New Request/启动失败隔离及完整P4保留。最终脚本语法、diff、增量敏感内容由staged复审核验。
