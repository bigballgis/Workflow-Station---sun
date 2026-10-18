# Code Review：引擎回写不得改造本地批量元数据

## 结论

PASS（修复后独立 staged 重审）；首轮 FAIL 的 source 泄露 Major 已补反例并修正，独立最终 22 项通过，无剩余本批 Blocker／未接受 Major。范围是 A19/A26 的两个已证实入口，不是全部写入口或 P4 完成声明。A39 不变。

## Findings 与逐条处置

### Blocker

- Call Activity `flowable:out` 可以把引擎字段直接映射到 `__subTableBatchRuntime__` / `__subTableBatchDeletedRows__`，覆盖合法本地值或给旧实例补造值。新增两个字段 × 有/无本地值的四个真实组件测试，修复前 12 项中 4 项失败。映射计划包含保留目标时，在任何值复制/保存前拒绝，沿原 best-effort caller refresh 边界记录通用错误；不撤销已经成功的工作流动作。
- 申请详情 scalar hydration 会把引擎返回的保留字段当作缺失业务值持久化。新增四项，修复前两个补造反例失败，原有两项保留值已通过；现在明确排除本地协议字段，正常服务输出仍补全。

### Major（独立首轮审查）

- `copyIntoCallRow` 把子实例本地变量合入输出来源，只拒绝保留 target 仍允许 `from=__subTableBatchRuntime__ → to=row.caption`，完整快照将进入普通业务列。删除记录同理。补两个 source 泄露反例，首版修复下 14 项中 2 项失败；现在同时拒绝逐行映射的保留 source，任何业务行均未写入。未顺手改旧 Call 行身份算法。

### Minor / Question

无新增业务方向问题。`SubTableBatchMetadata.isLocalField` 是两个服务端协议键的精确判断，被两个真实入口复用，不是按业务列名猜权限。

## 范围 / 验收映射

A19/A26：引擎不能构造、覆盖或通过业务映射外传本地快照/删除身份。旧实例不被赋予新能力；合法本地元数据原样保留。未更改启动顺序、任务动作、业务表结构或配置包格式。

## 触发矩阵

implementation-autonomy、bugfix-verification、code-review、secure-coding-sast；元数据边界、输入/输出隐私、MI 读取与持久化回写。可见 UI 无改动，N/A：不新增截图；完整 MI 场景保留现有截图证据。

## 编译与静态检查

隔离 Maven 副本 `-pl backend/user-portal -am package`，CalledProcessSync、ProcessApplicationQueryEngineScalarHydration、SnapshotMerge、ProcessInstanceHydration、ResponsePrivacy、WriteIsolation、TaskWriteIsolation 七类共 47 项，失败/错误/跳过均 0，BUILD SUCCESS。只有 Portal 模块改变；无 shared jar 变更。复制最终 fat jar 并重建 Portal Docker，不能以先前已部署首版代替最终版。

## 测试与运行证据

- 红→绿：Call 四个覆盖/补造反例；Scalar 两个补造反例；独立 finding 的两个逐行 source 泄露反例。保留合法业务输出、只更改目标行、旧实例等既有正例，未改断言或跳测试。
- `verify-batch-metadata-read-runtime.mjs` 只读明确的自有 paired clone（50066），两个实例分别增强/关闭，正常 GET 申请详情后本地两项元数据深等；响应没有内部字段名。无引擎变量注入、无数据库写入、无真实业务 FU 修改。不能用此运行检查代替恶意引擎输入单测。
- 首版及最终 source guard 产物后，完整 `pnpm -C frontend run regression:mi` 均 234+6 通过。最终 Portal image SHA256 `235a891cd89d051052619d6419a3f39cdf0b74f5723c63b157d669fcd9cb0208` healthy；最终 paired 读取再次通过，不把首版证据冒称最终二进制。
- 初次 shell 在 deploy cwd 用相对 jar 目标导致复制失败，随后停止接受该产物，改绝对路径复制并重建；最终运行只用正确 fat jar。

## Function Unit 通用性与生命周期

不依赖具体 FU、表、字段或 binding ID。两种保留字段、无快照旧实例和已有本地值成对覆盖。Call output 普通字段与 per-row 正例继续有效。Export/Import/Clone/Rollback/Admin 两条导入 N/A：没有改变任何设计配置序列化；新增限制只禁止把服务端内部命名空间当作业务输入/输出。运行时验证自有 paired clone，不声称全生命周期验证。

## 增量敏感信息

只检查变更行和新脚本/报告；单测业务值均 synthetic，错误不输出快照或引擎变量值。只读脚本 SQL 限制唯一 `batch-verification-...` process key 且先白名单验证，技术 ID 不是授权依据；HTTP 仍经正常登录及资源授权。独立最终 staged 186 行非空增量四类敏感候选零命中，diff check 与脚本语法通过。

## 性能

两个协议键常数时间判断；Call 按映射项线性检查，无新增 DB/HTTP；Scalar 在原遍历内过滤。没有容量提升声明。

## 残余风险 / 豁免

没有在真实 Flowable 故意写入伪造变量或运行恶意 Call BPMN；反例在真实组件、mock I/O 中验证，部署后只读成对复验。A39/#1666 仍延期，其他写入口枚举、New Request 工作区、全生命周期与容量门禁继续处理，不能由本批推导 P4 PASS。

## 范围外问题

复查没有改 Call 原 best-effort 刷新、旧身份匹配或其他工作流业务行为。容器采集到客户端断开相关 IOException/ClientAbort/AsyncRequestNotUsable，不能写成“零 ERROR”；健康和实际 API 断言通过。
