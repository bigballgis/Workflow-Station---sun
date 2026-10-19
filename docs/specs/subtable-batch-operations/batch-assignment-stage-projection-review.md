# Code Review 结果：初次分派阶段的有效能力投影

## 结论

CONDITIONAL。本批闭合已生成目标仍显示 Assign 命令的反例，未宣称 D7/P2/P4 整体完成。

## Findings / 逐条处置

### Major（已修）

- `TaskFormComponent.getTaskFormData` 只返回冻结开关，未结合实际阶段。Assign-only 子任务已经生成后仍显示 Batch Edit。新增实际 GET/H2 反例原实现 `expected false but true`；同一真实子任务的脚本部署前也明确 `true !== false`，不是只看页面按钮猜测。
- `SubTableBatchAssignmentStageGuard.project` 与写校验复用同一 `startedTargets`：当前节点就是目标时直接禁用；其余目标按本实例 runtime/history 检查，曾生成/完成/取消均不能初次分派。多个控件/目标每次总共两次 HTTP，不逐行或逐控件请求。
- 投影只复制响应控件及 flags，收紧 `allowBatchAssign`；不改冻结快照、摘要、字段规则、其他三个批量开关及原单行按钮。无 Assign 的旧控件不增加阶段查询，写边界仍独立重查，不相信响应过的开关。
- 合法结束宿主的只读投影关闭 Assign、仍返回表单，不能把结束当作历史不可读。历史不可用或身份不明则明确报读错误，不静默返回可用权限；该错误不改业务数据。写校验仍要求 active host，不沿只读分支放行。

### Review 建议及处置

- 独立 reviewer 要求不同 target 的多控件用例：已补当前目标 false、未来目标 true，且 runtime/history 各1次，避免将不同控件 OR 合并或一并禁用。
- 新增投影8项，覆盖生成后、生成前、当前目标、查询去重、未知历史、旧普通控件、结束宿主只读、不同目标独立性；同时保留原 Stage12与Persistence3的独立回归。

### Blocker / Minor / Question

- 独立 reviewer 对完整staged五文件最终复审无新增 Blocker/未接受 Major，允许本窄工程批次 CONDITIONAL 提交。全局发布门禁仍保留，不以窄批次放行生产。

## 范围 / 触发矩阵

- D6/D7、A03/A18/A32：Assign-only 生成后无新增批量入口；混合模式保留其合法普通 Batch Edit。范围是 Portal 服务端有效投影及同判据阶段校验，非任务改派。
- implementation-autonomy、code-review、bugfix-verification、config-over-heuristics、secure-coding-sast、verify-ui-fix-with-screenshot；无新接口、字段类型、Entity、共享 jar、环境变量或业务表。

## 编译与测试

- 同源隔离目录 package：214项0失败/错误/跳过。独立 reviewer 比对源码哈希，Projection8+Stage12+Persistence3，共23项0失败/错误/跳过。
- `scripts/verify-batch-postgres.ps1` 在同隔离副本执行：PG/JPA9+H2真实事务12，共21项通过；专用容器按名称和标签验证后清理，平台卷/数据库未动。
- 最终重新 package 含既有 `frontend/user-portal/src/i18n/application-status-labels.json` 资源，jar内验证 `BOOT-INF/classes/list/application-status-labels.json` 存在。源码不放宽 Java/TS 编译约束；jar SHA256 `F403B3B11009429D9E8B32879C6029129B2C8EB1DB1270F28DB7957E92AFB8F7`。
- Portal前端 Vite build通过；未修改其产品源码，既有 Sass/chunk 警告保留，383项全量类型错误未关闭，不报告typecheck PASS。

## 部署 / 真实运行 / 截图

- 最终 Portal backend `sha256:79d7c4538110c875e9e5f7ba6037d0c298197b07a111146a2fda096308d13822` healthy，frontend `sha256:0433107d763d2f1e8c625f6ce067d814d410f541597a04ad14aacc4142aa085f`；启动失败/损坏字节码计数0。
- 原混合实例 `e13ec1a2-c902-11f1-a615-4efaf416e537`、Assign-only `8f1c7e89-c903-11f1-a615-4efaf416e537`：生成后响应 Assign=false；两项任务各自一行，普通批量入口按其他开关独立保留/隐藏；两个无标记阶段越界请求均 already started，GET完整canonical不变，pageErrors0。
- 最终镜像新实例 `97126da0-c906-11f1-a615-4efaf416e537` / 宿主 `972077ae-c906-11f1-a615-4efaf416e537`：生成前 Assign-only 工作区保存仍合法；正常 Save→Complete生成2项实际任务、2份身份，生成后 Assign=false、无批量入口；拒绝伪造修改且数据不变，pageErrors0。没有修补/重派/取消已有任务。
- SQL只读确认既有 Assign-only 实例的冻结两份表单开关仍2/2为true，投影没有反向修改快照。
- 截图已目视核验，姓名列遮盖、原三个工具按钮保留。绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`：`2026-10-16_assign-only-stage-child-1.png`、`2026-10-16_assign-mixed-stage-child-1.png` 及两组 child-2；最终新实例 `2026-10-16_assign-final-stage-child-1.png` / child-2。截图保留不删。
- 最终完整 `pnpm -C frontend run regression:mi`：29文件234单测+6浏览器全部通过，`OK — unit + screenshots`；未使用unit-only替代。

## Function Unit 通用性与生命周期

- 冻结控件/binding/目标节点及当前实例状态驱动，不按 assignee 列名、FU名称或空taskId猜测。旧无快照实例和没有Assign的控件不进入新增查询。
- 设计包格式/Clone/Copy/Export/Import/Rollback/Admin两导入未变，本增量N/A；整体生命周期验收未完成，后续仍需真实round-trip。

## 敏感信息 / 性能 / 残余风险

- 完整staged diff whitespace检查通过，250新增行四类secret扫描0命中；脚本只输出任务/实例ID与计数，不输出人员目录值。
- 显式阶段读不做授权TTL缓存；完整history数据规模和200行负载未压测，不能宣称性能门禁全部通过。
- 冻结分派字段映射贯穿MI collection/engine、Complete目标资格复核、角色池、失败结果可见性、Preview/New Request及启动失败隔离仍开放。当前读资格不能替代保存时再查，也未解决跨服务TOCTOU。
