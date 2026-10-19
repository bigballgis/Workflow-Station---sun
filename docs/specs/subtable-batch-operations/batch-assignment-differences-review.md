# Code Review 结果：无客户端标记的分派差异阶段校验

## 结论

CONDITIONAL。本批只闭合当前表单冻结 Assign 控件对应 canonical 分派字段的实际变化检查，不依赖客户端 ASSIGN 声明。整体 D7/P4 仍未完成，真实 Complete 诊断另发现引擎循环变量硬编码，已保留失败样本并列为紧接着的实现项。

## Findings / 逐条处置

### Major（本批已修）

- 原 StageGuard 只在客户端有 ASSIGN 声明时调用；省略该字段的普通 Save 可走可写字段路径。新增生产 Save 入口 H2 反例在原实现下先失败，再接入服务端差异检查；修复后已完成目标的无标记分派修改拒绝，JDBC 业务写入计数为0，前后结构化 JSON 完全一致。
- `SubTableBatchAssignmentDifferences` 仅按冻结 tableName、配置复合 PK 和 assignee/role/BU 精确映射比较完整 canonical 行。不按 assignee 等列名猜测业务含义；普通字段、行序变化、省略 store、相等目标不触发阶段查询，明确清空已有目标会触发检查。未启用冻结 Assign 的控件不获得新策略。
- StageGuard 使用类型化 Submission，将声明目标和服务端派生目标去重。Save 在 scope 合并后、当前事务落库前检查；Complete 在 isolate/canonical 校验后、MI collection/引擎操作前检查。早期声明校验继续检查操作/字段权限，不重复执行阶段 HTTP。

### 已发现、紧接着处理（整体发布门禁）

- 对合成流程正常 Complete 后，Portal collection 正确构建2行，Flowable实际创建2个 AssignedWork，但两者均无 assignee；To Do 查询0项。实际 BPMN 循环变量为 item，MultiInstanceTaskWriter 固定读取 currentItem/_currentItem；日志明确 currentItem null。此为原 D7 配置驱动链缺口，不能通过改 fixture 变量名掩盖，不在本批宣称解决。
- 失败流程 `6b77ff15-c89e-11f1-a63d-c6848797bef7`、任务 `dcd4af9f-c89e-11f1-a63d-c6848797bef7` / `dcd572f1-c89e-11f1-a63d-c6848797bef7` 均保留，不撤销、不改派、不补偿重复创建。只读SQL核实2个真实未分派任务、流程仍 RUNNING；Complete成功不是分派成功。本分支不能因此开启生产配置或宣称 P4 PASS。

### Blocker / Minor / Question

- 本窄差异检查增量复审无新增 Blocker；循环变量问题归后续原设计内工程实现，未要求新的业务方向或放宽验收。

## 范围 / 触发矩阵

- D7、A19/A32/A34：省略标记不能让当前冻结映射字段跳过阶段检查；保留原单行在生成前的合法保存，不将普通单行清空规则改成批量清空规则，也不改已有无快照实例。
- implementation-autonomy、code-review、bugfix-verification、config-over-heuristics、secure-coding-sast；不新增端点、Entity、共享 jar、环境变量或业务表。

## 编译与静态检查

- Portal reactor package 使用既有批量/写隔离/Complete/scope/MI merger 指定集合：206 项0失败/错误/跳过，BUILD SUCCESS。新增纯差异6、真实H2入口3，保留stage12；普通生成前单行保存仍成功，生成后无标记拒绝，Complete payload边界无标记拒绝。
- PostgreSQL验证脚本：PG/JPA9 + H2真实事务12，共21项0失败/错误/跳过；新增真实JSONB/EntityVersion在无标记阶段拒绝后均不变。专用容器按标签清理，不动平台库/卷。
- 没有前端产品源码变化，不重复build；既有383项全量类型错误仍未关闭。测试初始化只写专用H2/临时PG，不向平台库注入快照或历史。

## 部署 / 运行证据

- 重建Portal backend（--no-deps），镜像 `sha256:29baf1be93ff812efe8e3100746c840b9b181a46a4d883ea50b838919109defe`，healthy，启动fatal计数0。首轮脚本恰逢服务重建，登录upstream错误；服务healthy后重跑，不将该轮记PASS。
- 新合成流程 `6b77ff15-c89e-11f1-a63d-c6848797bef7` / 原宿主 `6b8544d3-c89e-11f1-a63d-c6848797bef7`：真实混合工作区脚本PASS（EDIT+ASSIGN、草稿错误、保存/GET/reload、无新增任务、pageErrors0）；随后真实无标记普通Save为第二行设置既有已选择的合法个人并读回成功，证明合法生成前路径未被阶段检查误伤。
- 后续真实 Complete 生成/分派诊断 FAIL，原因和保留任务见上，不能计为负例写拒绝PASS。
- 最终完整 MI：234单测 + 6浏览器PASS，`OK — unit + screenshots`；没有使用unit-only替代。
- 截图保留于 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\2026-10-15_batch-assignment-mixed.png` 与 `-narrow.png`（人员显示已遮盖）。

## Function Unit 通用性与生命周期

- 当前表单可用冻结控件、真实行身份和表映射，不硬编码产品FU/字段。未知/重复身份、坏canonical结构明确409。
- 未修改FU配置包/Clone/Import/Rollback/Admin消费者，增量N/A；完整生命周期验收仍开放。其他表单上的全实例分派策略未借此次差异检查宣称闭合。

## 敏感信息 / 性能

- 提交前完整staged diff/增量secret扫描，仅输出计数，不输出人员或凭证。真实验证脚本不打印分派目标用户。
- 每次实际分派变化，runtime/history各查询一次；不做授权TTL缓存、不逐行HTTP。Save外部阶段读在canonical合并后事务内发生，但无跨服务锁，不宣称解决TOCTOU；长历史/200行负载未压测。

## 残余风险 / 范围外问题

- 没有差异的已保存目标在Complete仍需针对即将生成节点复核资格；本批未完成该预校验。角色池、真实候选与失败可见性、严格冻结MI构造/任意循环变量、跨服务竞态仍开放。
- Preview/New Request、启动失败隔离、hydration、多主体与完整生命周期、压力测试仍开放。无快照旧实例及没有冻结Assign的控件不受此新阶段策略影响。
