# Code Review 结果：批量初次分派的实际角色候选核对

## 结论

独立 reviewer 最终完整 staged 26 文件签核：无新增 Blocker 或未接受 Major，本窄批可 CONDITIONAL 提交；完整 P4 仍未放行。
范围为保存后的 Complete、直接进入 MI 和首个人工步骤进入 MI 的实际分派结果核对，不改任务分派算法、不自动补偿任务。

## Findings / 逐条处置

- Blocker：独立工作区重审未发现新增项。
- Major：旧观察器即使角色候选正确也只返回 UNCONFIRMED，错误候选组也不能定位为 FAILED；本批按真实候选身份链接逐行比较，已红→绿。
- SQL 边界反例：多组 BU/Role 请求不能用独立 IN 的笛卡尔组合拉入未请求配对；新增反例先失败，改为参数化 `(b.code,r.code) IN (:targets)`，H2 和实际 PostgreSQL 均通过。
- Minor/Question：无未处理产品 finding。审查要求准确区分目录只读和合成 catalog 的测试访问授权写入，本文已明确。

## 范围 / 验收映射

- A28/A29/A31/A35/A37/A38：单人角色池仍是未领取的角色任务；混合个人/角色、多成员候选集合、创建后目标不匹配不能假报成功。
- Complete/start 严格预校验阶段，按本次冻结分派字段与实时有效 BU/Role 解析去重的 ACTIVE、未删除候选用户集合；保存的资格查询仍为单次 EXISTS，不逐行拉目录。
- 候选集合只存在服务端临时 Plan，不进入业务行、表单响应、Flowable 变量或持久快照；资格失败仍阻止引擎动作。
- 创建后复用原批量任务/MI 元数据读取，按实例、定义、节点、表及完整行键唯一连接；比较实际 Flowable candidate identity links，而不是把配置列当结果。
- 未领取角色任务须模式、BU/Role 关联一致，实际 candidate users 精确等于预校验集合，且无额外 candidate groups。已领取、缺少字段、重复/不完整身份或不可确认的响应保持 UNCONFIRMED。
- 角色模式/BU/Role 来自既有辅助任务元数据，不冒充实时目录或直接 Flowable 身份链接；实际候选身份才来自 live task converter。
- 已证明的错误返回 FAILED，issues 只含本范围任务 ID 和原因码，不暴露候选用户清单。失败发生在已提交动作后，不提示重复 Complete/Start。
- Portal 与 Help 三语将“个人办理人错误”推广为“分派目标缺失或不匹配”，文档同步 llms-full。

## 编译 / 测试 / 静态检查

- 隔离 Maven 副本与本次13个 Java 文件 SHA 全部一致，避免 IDE 编译产物污染。
- `mvn -pl backend/user-portal -am package "-Dtest=SubTableBatch*Test,TaskCompletionObservationTest,ProcessStartBatch*Test" "-Dsurefire.failIfNoSpecifiedTests=false"`：244 项，0失败/错误/跳过，BUILD SUCCESS，24.887s。
- 独立 reviewer 复跑 RoleEligibility、CompletionObservation、InitialMiOutcome：51项全绿。
- 首轮角色结果测试30项中3项失败；最终全绿。配对查询反例9项中1项错误；修后全绿。
- Portal build 23.54s，Help build 1.26s；UI outcome 单测5项全绿，Help真实浏览器152/152通过。
- Portal typecheck 383条历史诊断，与 `batch-person-outcome-typecheck.log` 原始逐条比较差异0；不声明全量类型通过。
- 完整 `pnpm -C frontend run regression:mi`：234项单测、6个真实浏览器场景全绿，`MI_FIXTURE_ONLY=1`，仅隔离样本。

## 真实红→绿与多入口证据

- 已有角色-only FU50037：同一工作区 Save→UI Complete 脚本，旧镜像返回 UNCONFIRMED、不满足 VERIFIED；新镜像实例 `71987bab-ca0d-11f1-b00d-96c2314bbe84` 返回 VERIFIED。两个任务仍未领取，各一条正确 candidate user，无候选组。
- 新合成故障 FU50056：明确创建监听器追加固定合成 candidate group。旧镜像实例 `bdd691fa-ca0c-11f1-b00d-96c2314bbe84` 返回 UNCONFIRMED、不满足 FAILED；新镜像 `8ce2b7fa-ca0d-11f1-b00d-96c2314bbe84` 返回两个准确任务的 ROLE_TASK_CANDIDATES_MISMATCH。没有删除/修改生成的任务或目录。
- 之前尝试清空候选的 FU50055 注入无效，实际候选仍存在：不计为故障反例，不修补该样本。
- 新 FU50058 直接进入 MI：正常 UI Start，实例 `a4d75dd4-ca2e-11f1-b00d-96c2314bbe84` VERIFIED，实际两条任务候选集合精确正确。
- 新 FU50059 首个人工步骤进入 MI：实例 `c4025790-ca2e-11f1-b00d-96c2314bbe84` VERIFIED，未显示可重试的 firstStepError。
- 新 FU50060 首步骤异常候选组：实例 `4ddd2c1e-ca2f-11f1-b00d-96c2314bbe84` FAILED；issues恰好对应两条任务，页面仅警告“已创建，不要重试”。正常和异常启动样本 pageerror 均0。
- PostgreSQL 对上述三个固定启动实例只读事务确认各2任务、0办理人；正常各2条候选用户链接/0组链接，异常为2用户链接/2组链接。
- 最终 helper 抽取后，已有 FU50035 新实例 `119e3d2f-ca30-11f1-b00d-96c2314bbe84`：真实工作区统一角色→第一行切个人→原自动保存→UI Complete，返回 VERIFIED；另一行2成员角色池未领取，实际候选集合精确匹配。未选行除原保存链行版本增量外所有字段保留。pageerror0。
- 合成 catalog 测试访问授权由正常 Admin API 增加到当前测试角色；这是测试 FU 访问授权写入，不是目录全只读。用户/BU/Role及成员关系只查询，不写入。

## 部署与截图

最终 jar SHA256 `0546E84FC9633DA239716A91BA66375B6D399D5B452E047B2A34F45B83F3991F`。
已重建且健康 Portal backend 镜像 `sha256:07768282773d334bb1732861bfd8103938c8abf44410781696654d62ff642124`。
已部署 Portal frontend `sha256:9a4ef20776de150d7ea3601f684d6109c14d6eef55b8120fba4672bb53dfb71f`、Help `sha256:59ac9c23f15d3f892702c90d6482395f9a1c978e9d704c5f5d998b582269a2c9`；不需重建引擎。

截图均保留在绝对目录 `F:\hermes1.0\Workflow-Station---sun\frontend\user-portal\verification-screenshots\`：

- `2026-10-17_role-extra-before-outcome.png` / `role-extra-failed-outcome.png` 及各 `-narrow.png`。
- `2026-10-17_role-result-verified-outcome.png` 及 `-narrow.png`。
- `2026-10-17_role-direct-verified.png`、`role-first-verified.png`、`role-first-extra-failed.png` 及各 `-narrow.png`。
- `2026-10-17_role-final-multi-mixed-outcome.png` 及 `-narrow.png`。
- 工作区截图原脚本仍沿用 `2026-10-16_role-final-multi-mixed-workspace.png` 及 `-narrow.png`，实际为本轮采集；目录选择控件已遮罩。

## 通用性 / 生命周期 / 安全 / 性能

新建与既有合成 FU、多入口、个人/角色与两种成员数均验证。无业务列名启发式、无 schema、DTO、部署配置或 FU 包格式变更。Export/Import/Clone/Rollback/Admin两导入的设计消费者本批无新增契约，沿用现有链，不以本批替代全生命周期发布门禁。
独立 reviewer 最终 staged 453 条非空新增行四类敏感模式扫描0命中，五个脚本 nodecheck 和 cached diffcheck 通过。已核验244项package、完整MI234+6及最终混合工作区日志，独立同源51项测试全绿，抽看失败窄屏警告和正常混合成功图无PII/遮挡。
资格查询为去重目标的一次有界参数化查询，结果核对复用原两条 bulk 读取；不声称性能优化或跨服务原子事务。

## 残余风险 / 未验证项

结果仍是动作后的即时核对，不是持久化的分派审计页面；刷新后的持续失败结果入口、所有创建竞态、完整A01–A41和启动失败隔离/hydration证据仍须独立闭合。不得宣称P4全完成或启用生产配置。
未改FU50007/FU50057或用户提供的业务任务，未做目录成员改动、运行中任务改派、领取、撤销或补偿。
