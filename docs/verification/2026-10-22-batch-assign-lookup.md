# Batch Assign Lookup 修复与本地验证（2026-10-22）

## 结论与范围

> 2026-10-23 最新：用户确认的方案二已实施并重建 Portal 前后端。旧任务 Request Approval 同任务红转绿，原 FU 新请求 Add/Edit/批量分派/进入审批，以及第二 FU 不同字段映射保存通过；具体范围、审查 PASS 与未验证项见文末“方案二”节。下述早期数字和限制为历史记录。

方案 A 已实现，并已直接通过组件库修改原 FU `MCY Debit Card_20260525`，页面部署为 **1.0.132**。原 FU 的 Portal 页面导入两条合成交易、发起、领取、Batch Edit 选人、确认分派和重新加载均已通过；数据库确认两行分派实际写入，原流程生成两个真实 Investigation 子任务。最终 Portal 相关回归 **332 项通过**（另有 Common 5 项）；发布修复相关回归 214 项通过，四个后端服务已打包重建。原 UAT 实例、Lookup 的真实业务编辑与完整生命周期组合仍未验收，交付审查保持 **CONDITIONAL**。

初始 Lookup 修复位于 Portal 后端，不改变数据库结构、客户端协议或产品 UI。后续按用户要求补充原 FU 页面配置及关系表发布修复，见文末续验记录。纯分派逐行核对完整差异与冻结配置，仅调整未改业务值的类型复检；分派值、权限、归属、阶段、资格及版本检查继续执行。普通编辑、新增和删除保留原有契约，含 Lookup 的真实业务编辑不在本次成功范围内。

## 复现与根因

Lookup 对象是合法控件运行值。旧实现只要一个冻结字段变化，就对整行按表字段类型复检；因此文本列中的未改 Lookup 对象阻断独立分派。

新失败测试直接进入 `TaskFormComponent.submitTaskForm` 及 Complete 校验入口，使用配置主键、五个对象值和真实 JDBC 事务。修前失败信息为 `Batch field has invalid text type: lookup_0`，不是仅重写条件做推算。

本地旧服务的 v2 测试任务 `85e673f7-cdea-11f1-aede-9e7f0023ea5e` 也复现 `Batch field has invalid text type: case_type`；脚本断言数据库完整变量不变。该实例在第一次修复镜像上通过同类分派保存，五个 Lookup 对象和未选择行保持原样；该次成功见会话执行输出。最终镜像的完整运行回归另使用下面列出的新测试实例，不能将两者混写为同一请求精确重放。

## 初轮 Lookup 修复文件与调用链

- `SubTableBatchAssignmentOnly.java`：匹配冻结控件/绑定、完整配置主键、唯一 ASSIGN 意图；对比完整键和值，拒绝未知字段；只对既有纯分派行收窄业务字段类型检查。版本字段继续由作用域 Guard 负责。
- `SubTableBatchTaskPermissions.java`：先保留全部现有只读权限检查，再判定纯分派；即使分派字段没变化，也检查声明行的差异，防止无变化意图夹带未知字段。无声明时不获得豁免。
- `SubTableBatchChangeGuard.java`：将分派值与意图匹配检查提取为共享方法，原始提交与规范化行共用判断；不新增另一份人员/角色模式规则。
- `TaskFormComponent.java`：四处通用校验调用传入已有声明，覆盖过滤前、事务内、作用域合并后及 Complete。无需改 `SubTableBatchPayloadGuard`、新增/编辑 Guard、阶段 Guard 或生产前端。
- 两个新测试类覆盖真实保存/完成调用、不同主键、人员/角色模式、权限、类型、字段增删、嵌套对象、冲突意图、未知字段、陈旧版本和目标阶段等边界。
- `frontend/scripts/verify-batch-lookup-assignment-runtime.mjs`：可重跑的本地合成样本准备与运行验证，使用既有登录/部署辅助脚本，凭据仅从环境读取，不写入源码或本报告。

## 同组测试红转绿

临时关闭纯分派判定后执行：

```powershell
mvn -o -pl backend/user-portal -am `
  '-Dtest=SubTableBatchLookupAssignmentTest,SubTableBatchAssignmentOnlyTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

当时的 24 项新增用例结果：`Tests run: 24, Failures: 3, Errors: 2, Skipped: 0`，包含保存、Complete 和角色切换的 Lookup 类型失败。随后已恢复修复代码，未保留调试开关。

审查另补 `noOpAssignmentCannotCarryAnUnknownField`：修正前单独运行，结果为 1 项失败，断言信息 `Expected ... PortalException to be thrown, but nothing was thrown`。修正后该用例包含在最终回归中通过。

最终执行：

```powershell
mvn -o -pl backend/user-portal -am `
  '-Dtest=SubTableBatch*Test,TaskFormComponent*Test,TaskApprovalCompletion*Test' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -o -pl backend/user-portal -am package -DskipTests
```

结果：`Tests run: 321, Failures: 0, Errors: 0, Skipped: 0`、测试与打包均 `BUILD SUCCESS`。这是相关套件结果，没有执行或宣称整个仓库的全量测试。构建仍有既有的 JaCoCo class execution-data mismatch 警告，未以删除覆盖率门禁或降低检查要求规避。

## 初轮 Portal 构建与运行产物

按 `deploy/environments/dev` 的既有 Compose 配置执行：

```powershell
docker compose -f docker-compose.dev.yml --env-file .env up -d --build --no-deps user-portal
```

最终容器状态 `healthy`，镜像 ID：

```text
sha256:e9ffb0a43e7747188c61a94a14e6fafdee966a2379eb55b68c31b8cdf5c692d4
```

本机编译产物与运行容器 `BOOT-INF/classes` 中 `SubTableBatchTaskPermissions.class` 的 SHA-256 相同：

```text
b741d10e46cd26f54df7d5212008ab58318173e31e5ce3f162b1b705f26d1a61
```

确认新镜像和实际类一致后才执行最终回归，未以新源码搭配旧容器作通过证据。只重建受影响的 Portal 后端，未改公共模块或重建无关服务。

## 初轮合成样本的准备方式

来源是用户指定的 `MCY Debit Card_20260525`，本地设计 ID `48`，code `fu-20260505-thwmut`。初轮只读取其配置，未修改或部署原 FU；后续直接操作原 FU 的记录见续验节。

重要差异：本地该版本五个业务选项为 Select，原 UAT 则为 Lookup。测试副本采用来源 FU 的主表、交易子表字段与子表表单结构，使用独立表名及合成值，将五个业务选项模拟为只读 Lookup 对象。对象显示契约采用 `dropdown_name`；不配置可操作的外部 Lookup 数据源，本测试不验证搜索、选择或回填。归属外键从现有显式 FK 字段读取后在副本中配置，简化流程不执行来源 FU 的邮件、服务任务或其他业务动作。

成功测试 FU ID `50102`，code `batch-verification-lookup-17926539-20261022-857tr6`，本次最终实例为：

- catalog ID：`b5c05919-80ad-4ff8-bc3f-1470dde5f998`。
- process ID：`93801af1-cdef-11f1-aede-9e7f0023ea5e`。
- Review task ID：`938faba0-cdef-11f1-aede-9e7f0023ea5e`。
- store：`dw:hmdc_transaction_verify_50102`，快照版本 `2`。
- 五个业务字段：`case_type`、`incoming_channel`、`billing_currency`、`dispute_currency`、`dispute_reason`。

## 初轮合成样本运行结果

通过真实应用接口验证；请求、响应、数据库对照和引擎任务核对由脚本断言。页面截图只补充合成样本页面状态，不作为“实际通过按钮提交”的声明。

- 纯人员分派保存成功；选中行目标正确，五个 Lookup 对象结构和值保持原样，未选择行完整不变。
- 六类负例分别在 Save 和 Complete 拒绝：只读 Lookup 内部篡改、未知字段、无变化分派夹带未知字段、缺失意图、无效人员、陈旧行版本。每次都比较完整数据库变量，确认没有部分写入。
- Complete 请求携带新的待分派变化，正确写入两个目标；引擎实际生成两个 `AssignedWork` 子任务，公开 DTO 的 `currentAssignee` 与各行目标一致。
- 第二个测试账号保存自己的 MI 行后，第一个账号的行完整不变；两行 Lookup 值继续保持原样。
- 页面运行时 `pageerror=0`。人员模式有真实环境验证，角色/BU 切换由本次新增及已有后端测试覆盖，未额外执行角色目录的真实运行组合。

最后一次运行输出（已移除登录身份日志）：

```jsonl
{"step":"lookup-tamper","saveRejected":true,"completeRejected":true,"databaseUnchanged":true}
{"step":"unknown-field","saveRejected":true,"completeRejected":true,"databaseUnchanged":true}
{"step":"no-op-unknown-field","saveRejected":true,"completeRejected":true,"databaseUnchanged":true}
{"step":"missing-intent","saveRejected":true,"completeRejected":true,"databaseUnchanged":true}
{"step":"invalid-target","saveRejected":true,"completeRejected":true,"databaseUnchanged":true}
{"step":"stale-version","saveRejected":true,"completeRejected":true,"databaseUnchanged":true}
{"step":"after","success":true,"error":null,"lookupFieldsPreserved":true,"otherRowPreserved":true,"snapshotVersion":2}
{"step":"complete","pendingAssignmentPersisted":true,"lookupObjectsPreserved":true,"generatedChildren":2,"actualAssigneesMatch":true}
{"step":"second-actor-own-row-save","otherActorRowUnchanged":true,"lookupObjectsPreserved":true}
```

截图保留：

- `frontend/user-portal/verification-screenshots/2026-10-22-lookup-assignment-before.png`：旧服务的合成样本任务页面，错误以接口响应与数据库断言为证。
- `frontend/user-portal/verification-screenshots/2026-10-22-lookup-assignment-after.png`：新服务保存后的合成交易子表页面。

复跑需从环境提供 `LOGIN_USER`、`LOGIN_PASS`、`LOOKUP_TARGET_USER_ID`；双主体检查另提供 `LOOKUP_SECOND_LOGIN_USER`、`LOOKUP_SECOND_LOGIN_PASS`。准备时提供 `LOOKUP_SOURCE_FU_ID=48`、`LOOKUP_SOURCE_FU_NAME=MCY Debit Card_20260525`；可指定 `LOOKUP_FIXTURE_UNIT_ID=50102` 重用测试设计。先运行脚本 `prepare`，再运行 `after`。`before` 必须针对未修复镜像和未提交测试任务执行，不能在当前成功镜像上伪造红灯。脚本限定 localhost，按标记校验可写测试 FU；运行定位写入 git 忽略的本地 `.codex-batch-lookup-fixture.json`。

## Code Review 首轮报告与处置

### 结论

首轮 **FAIL**，以下两项均在本次范围内处理；重审完整改动后的交付结论为 **CONDITIONAL**，不将本地证据等同于 UAT 放行。

### Findings

Blocker：无。

Major R1：`SubTableBatchAssignmentOnly.validate` 初版只校验变化的分派字段。

- 反例：切换到 ROLE 时，基线的人员字段已是非法对象但未变化；旧意图检查的空值转换可能将非字符串当成空。
- 影响：收窄整行校验后可能漏掉非法的分派模式组合。
- 最小修复：保留未改业务值，校验冻结映射中的全部分派字段，并拒绝 PK/FK/计算字段映射。
- 依据：方案 7.4、7.9 的安全与值契约；`secure-coding-sast` 输入边界、`code-review` 正反例门禁。
- 处置：已修；`SubTableBatchAssignmentOnly.java` 的 `targetFields` 校验及 `unchangedMalformedPersonIsNotEmptyInRoleMode` 测试覆盖。

Major R2：`SubTableBatchTaskPermissions.validateControl` 初版在 `metadataChanged=false` 时不执行声明行完整差异检查。

- 反例：有效但无变化的 ASSIGN 意图夹带 `_arbitrary` 字段；新增用例先确认未抛异常。
- 影响：不能满足“完整行差异验证”的约束，未知键可能漏检。
- 最小修复：声明行的纯分派检查独立于标量字段变化触发条件；仅实际需要时仍调用原整行类型校验。
- 依据：方案 7.9；`error-handling-governance`、`code-review` 正确性与完整输入检查。
- 处置：已修；`SubTableBatchTaskPermissions.java` 的 `assignmentOnly` 先行判定、`noOpAssignmentCannotCarryAnUnknownField` 及最终接口负例覆盖。

Minor：验证脚本初版函数过长；已按源配置读取、测试设计准备、发布、请求构建、负例、截图及第二主体检查拆分，无新依赖。运行断言也已按引擎公开 DTO 的 `currentAssignee` 字段核对，并重跑全部脚本流程通过。

Question：原 UAT 发布窗口及原实例验收需按原文档第 9.5 节完成，不属于本地脚本能够代替的证据。

### 初轮重审范围 / 验收映射与触发矩阵

完整范围包含四个 Portal 实现文件、两个新增后端测试类、运行验证脚本、证据截图、报告及问题台账。命中权限、输入、配置驱动、异常处理、保存/完成调用链、FU 运行时与回归门禁；无 Entity、SQL、前端产品 DOM、设计产物或跨服务契约变更。

编译和测试证据见上文；最终 staged review 的 `git diff --cached --check`、脚本语法检查及新增文本敏感信息扫描均通过，未发现凭据、个人邮箱或产品业务字段特判。已读取完整实现差异及新增测试/脚本，重审未遗留本次代码 Blocker/Major；这是一轮本地自审，没有声称独立第二 reviewer 已放行。

### Function Unit 通用性与生命周期

- 通用性：静态通过；产品逻辑只读取冻结映射和配置主键，未出现来源 FU 名称/业务字段特判。不同主键、表名及人员/角色组合由测试覆盖。
- Save/Load、Portal backend：保存四阶段及实际重载/查库通过。
- v1/v2、无快照：相关既有兼容测试与新增 v1 用例通过，实际运行样本为 v2。
- Export/Import、Clone、Version/Rollback、Admin 两条 Import、Deploy/Activate：初轮未改生产设计数据格式；后续关系表发布修复已同步 DW ZIP/版本快照及 DW/Admin Import，见续验节。完整生命周期组合端到端回归未执行。
- Portal frontend/parity：无生产界面或协议变化，无需重建前端；保留页面状态截图。未触及前端 MI 热路径，未执行 `regression:mi` 全管道。
- 双 FU 运行样本：初轮只有合成 FU；续验已补原 FU 48 的真实页面运行。原 FU 本地业务选项为 Select，Lookup 对象场景仍由合成 FU 与保存/完成回归测试证明，原 UAT 的五个 Lookup 实例未验收。

### 增量敏感信息、性能与残余风险

凭据只用于登录环境，不进入源码、报告或截图。原始登录日志未纳入版本控制。新增产品逻辑没有网络/数据库 I/O，没有按 FU 名或业务字段名决策；未做性能压测，不宣称性能提升。

范围外记录 #1695：运行中的 DW 克隆测试副本无开发组而无法编辑/删除；当前工作区已有 `inheritDevGroups`；续验已因关系表发布修复重建 DW，克隆继承与旧草稿清理仍未复验。草稿 `50098`、`50099` 经正常清理接口仍被 403 拒绝，保留待管理员处理；空测试 FU `50101` 已归档。成功测试 FU `50102` 及合成实例保留供复核，初轮未写原 FU；续验已按用户授权修改、发布原 FU，并仅创建合成请求。未写 UAT 数据。

回退方式为部署既有 Portal 后端镜像；无 schema、快照迁移或客户端协议需要回滚。未测试原 UAT、角色目录全部运行组合、退回后保存、新旧服务混跑及完整导入/克隆/回滚流程。对应环境验收仍由原环境流程负责，待上述条件具备后补证，不以本地通过替代。


## 原 FU 组件库配置与页面续验（2026-10-22）

前述 `Batch verification Lookup 1792653963263`（设计 FU 50102）是 API 创建的合成结构样本，不能视为“通过组件库修改原 FU”的证据。用户指出这一差异后，本轮直接操作原设计 FU 48（`MCY Debit Card_20260525`），未再使用 API 新建流程或交易行来替代页面验收。

### 已完成的原 FU 页面配置

- 在 Form Design 编辑 `Transaction Request Form - Transaction Assignment`（form 164）。
- 在主表画布选中 `HMDC Transaction (row_id)` 子表，开启 `Allow Batch Assign`。
- 在该子表的 Batch Form，通过组件库加入 `Assignment Mode`，配置基础 Input `assignee_id` / `Assignee`。流程的 Transaction Investigation 已配置按 `assignee_id` 分派；保存原流程后设计器识别到 Person 模式。
- 人员输入须用 Batch Form 支持的基础字段，运行时分派编辑器会转换为人员选择器。本轮曾配置 Lookup，Portal 明确拒绝 `Batch field control is unsupported: assignee_id`；已在设计器恢复 Input 并重新发布。这一中间失败不计为成功验证。
- 用户明确要求永久停用 Email。Start Event 的邮件触发已关闭，孤立模板 `Email trigger: Start`（id 1）也已停用。模板页面无启用编辑项；正常更新接口又因邮箱连接已停用而拒绝更新，因此只对本地该模板执行受名称、FU、id、模板类型共同限制的 `enabled=false` 更新；页面刷新确认 `Enabled=No`。未启用邮箱、未发送邮件。
- Admin 的原 FU 本地发布目录初始无 Access，已通过页面为既有 `hase-hmdc / HMDC_Index_Role` 配置发起入口；未给用户新增角色。

### 发布快照阻塞与修复

版本 1.0.129 因未绑定的启用邮件模板失败。停用后，1.0.130 导出、上传与校验成功，但 Admin 发布返回 `VERSION_SNAPSHOT_INCOMPLETE`。查库显示三个表单均含 RELATED 虚拟 User 绑定（平台稳定 id `-1000000001`），但发布包只有 MAIN/SUB 三张表，RELATED 的 `tableName` 也为空。

用户授权一并修复发布链路后：

- 提取共享平台 User 结构定义 `SystemUserRelationTable`，复用原设计器公开的八个用户字段，只携带结构，不携带用户行。
- DW ZIP 与版本快照导出虚拟关系表结构，并从实际关系表结构填充 RELATED 绑定表名；同时覆盖普通关系表，避免只修 User 样本。
- DW Import 与 Admin Import 对平台虚拟表保留稳定身份，不创建或覆盖 `rt_table_definitions` 中的业务表；保留冲突身份及结构拒绝校验。Admin 两条 HTTP 导入入口共用现有 parser/importer，无新增旁路。
- 新增虚拟表导出测试先失败（expected 1 table, got 0），修后通过。相关回归 214 项（Common 9、Admin 142、DW 63）全通过；四个后端模块 `mvn ... package -DskipTests` 为 BUILD SUCCESS，四个本地容器重建后均 healthy。
- 页面发布 1.0.131 成功；修正 Batch Form 控件后最终发布 1.0.132 成功。1.0.131 的 catalog 为 `85a78fa6-aeff-4de3-81d3-e1fdd36fedaf`，含 `sys_users` DATA_TABLE；三个表单的 RELATED 表名均为 `sys_users`，本地 `rt_table_definitions` 中该表名计数仍为 0。

### 最终原 FU 页面验收结果

1. Portal 使用 HMDC 录入工作身份，从 New Requests 点击原 FU 1.0.132。通过页面 Import 上传[仅两条合成交易的 CSV](fixtures/2026-10-22-mcy-batch-rows.csv)，显式测试主键为 `LOCAL_BATCH_UI_20261022_A/B`。用户开启 Edge 扩展文件 URL 访问后上传成功。
2. 页面发起还复现新发布入口的 PK 默认值不一致：源 FU 三个主键均未配置生成策略，旧入口默认 UUID，新 `allocateCatalog` 却将 null 当 manual，空对象也未采用默认值。`PortalPrimaryKeyAllocationComponent` 已复用现有 `toPkConfig`；新增四例先有两个失败（null→manual，空对象→null），修后通过，显式 manual 和 prefixedSequence 不变。未改变原 FU 的主键配置、字段、外键或业务编号格式。
3. 页面 `Case Submit` 成功创建原流程请求；原 Case Submission 完成后进入 Transaction Assignment。按该节点原有候选人配置，使用已有 developer 身份领取任务。未改候选人或节点流程。
4. 选择两条交易 → Batch Edit → Assign by person → 实际人员目录选择 liam → Apply to page → Confirmed all Assignment → Confirm。
5. 同一页面提交先被新增完整差异检查拒绝：`Batch assignment changes an unknown field: [assignee_display_name]`，数据库未写入。原因是页面在选人后自动补充平台显示元数据。修正仅从纯分派差异中排除**未在设计表中声明**的该显示字段；同名业务列仍按配置校验，其他未知键仍拒绝。新增用例先失败再通过，同时验证夹带未知键和声明同名业务列仍被拒绝。
6. 重建 Portal 后，保持同一任务与同一页面分派状态重试 Confirm，提交成功。数据库逐字段比对：两个 `assignee_id` 均写入 liam 的用户 ID，并新增相应显示名；其他全部业务值、业务主键、外键、平台行身份与已有版本值均与分派前一致。五个原有 Select 字段保留原值。
7. 原 Assignment 任务完成，实际生成两个 `Transaction Investigation` 子任务；均分派给 liam。重新打开请求页面，版本仍为 1.0.132，两行均显示 liam 与 In Progress。本轮停在 Investigation，保留供用户后续操作，未完成其后审批业务。

原 FU 最终 catalog：`aa9063f4-130d-41da-b4b6-4295a87119d1`。测试请求：`4f6ad3f6-ce07-11f1-94a0-9a0010fd8b49`。原分派任务：`4fa85282-ce07-11f1-94a0-9a0010fd8b49`（已完成）。两个实际子任务：`6c8eb49c-ce16-11f1-94a0-9a0010fd8b49`、`6c996300-ce16-11f1-94a0-9a0010fd8b49`。

复核入口：[原 FU 设计器](http://localhost:3000/dev/function-units/48)、[已保存测试请求](http://localhost:3000/portal/applications/4f6ad3f6-ce07-11f1-94a0-9a0010fd8b49)。可用用户提供的测试身份查看两个新子任务；凭据不写入文档。

### 最终构建与重审

- 发布相关回归：Common 9 / Admin 142 / DW 63，共 214，通过。后续跨模块扩展回归共 560 项通过；最后两个 Portal 修正后，Portal 332 / Common 5 项再次通过。均为相关用例集，不声称全仓全量测试通过。
- 最终 Portal package：BUILD SUCCESS；本地镜像 `sha256:8f64dfc0a502e6c8df519fc6eb951908916b6037fbdd71a7f59189508916f41c`，healthy。共享模块更新时 DW、Admin、Engine、Portal 均已重建；后续仅 Portal 改动仅重建 Portal。
- 续验发现并处置：R3（Major）User 结构与 RELATED 表名未发布 → 共享平台结构 + DW/Admin 两侧导入 + 原 FU 页面发布成功；R4（Major）发布 PK 默认值改变 → 复用旧默认 + 四项正反例 + 实际 Case Submit 成功；R5（Major）选人显示元数据被误判 → 以设计字段声明区分业务列 + 正反例 + 同一待办页面红转绿。
- 重审触发矩阵：权限与完整差异、平台元数据、FU ZIP/版本快照、两条 Admin 导入消费者、共享模块下游打包、普通关系表与平台 User 双样本、PK 默认与显式策略、安全敏感信息、实际页面和数据库证据。无生产前端代码、schema、K8s 或环境参数变更；未执行前端 MI 全管道及完整 Clone/Rollback 组合，不能将这些写为已通过。
- 本地自审无遗留的本次代码 Blocker/未接受 Major；最终状态 CONDITIONAL，限于原 UAT 和上述未验证范围，未声称独立 reviewer 放行。正常业务任务未用于测试，全部写入都属于本地合成请求。

截图位于：

- `frontend/developer-workstation/verification-screenshots/2026-10-22-mcy-final-batch-form.png`：最终 Input + Assignment Mode 配置。
- `frontend/developer-workstation/verification-screenshots/2026-10-22-mcy-email-disabled.png`：邮件模板永久停用。
- `frontend/developer-workstation/verification-screenshots/2026-10-22-mcy-deploy-blocked.png`、`2026-10-22-mcy-deploy-success.png`、`2026-10-22-mcy-deploy-132-success.png`：发布失败与成功。
- `frontend/user-portal/verification-screenshots/2026-10-22-mcy-ui-imported.png`：页面导入两条合成交易。
- `frontend/user-portal/verification-screenshots/2026-10-22-mcy-ui-batch-person.png`：真实 Batch Edit 选择 liam。
- `frontend/user-portal/verification-screenshots/2026-10-22-mcy-ui-saved-reloaded.png`：保存后重载，两行处理人和状态回显。

早期 `mcy-original-batch-form.png` 保留中间 Lookup 配置，不作为最终配置证据。Portal 控制台保留本轮真实失败的历史错误；最后成功提交并重载没有新增错误，不声称整个调试会话错误数为零。

## Transaction Assignment 新增/编辑回归修复（2026-10-23）

### 复现与根因

用户报告任务 `96b5fc9d-ce84-11f1-ac67-1ab7605b2169`，对应实例 `968158b1-ce84-11f1-ac67-1ab7605b2169`、原 FU 48 / 1.0.132。本轮通过 Edge 页面以该任务持有人身份操作：

- 原有两行已分派给 liam。Add 输入合成标记 `LOCAL_ADD_20261023` / `LOCAL_ADD_VERIFY` 后 Save，控制台在 10:09:06 返回 403：`Task submission changes a read-only or unavailable table field`。
- 重载丢弃失败草稿，编辑第一行的 Merchant Name 为 `LOCAL_EDIT_20261023` 后 Save，10:10:13 返回相同 403。Docker 请求日志确认该任务 `/submit` 被拒绝，数据库仍只有原两行，修改未落库。

根因是 `SubTableBatchTaskPermissions.validateControl` 将四个系统审计字段的新增值/更新值按普通 READONLY 业务字段拒绝。Add 弹窗 Save 时填充 created/updated 字段，Edit Save 时刷新 updated 字段，均为现有 `rowInit.ts` 契约；现有 `TaskFormComponent.filterSubTableFieldsInPlace` 也明确保留 `SystemAuditFields`，新增批处理校验未与此契约一致。

### 最小修复与红转绿

仅在上述权限校验中复用 `SystemAuditFields.isAuditField`，系统审计值不再按设计器只读业务值拒绝；普通业务字段只读校验、完整行元数据校验、主键/外键/作用域及分派校验继续执行。没有按 FU/业务表名硬编码，也未更改设计权限、快照、前端产品代码或 schema。

新增 `SubTableBatchTaskPermissionsTest` 两例：普通新增含四个审计字段、已分派行编辑刷新审计字段。两例均另断言将业务 amount 设为 READONLY 后仍拒绝。本轮先在修复前运行：8 项测试中 2 失败，均为上述错误；加入修复后同组及相关回归通过。

```powershell
mvn -o -pl backend/user-portal -am `
  '-Dtest=PortalCatalogPrimaryKeyAllocationTest,PortalBatchPrimaryKeyAllocationComponentTest,SubTableBatch*Test,TaskFormComponent*Test,TaskApprovalCompletion*Test,ProcessSubTablePrimaryKeyEnricherComponentTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：Portal **334** 项、Common **5** 项，0 failures / errors / skipped。证据日志 `.codex-mcy-1023-red.log` / `.codex-mcy-1023-green.log`（本地忽略文件，不提交原始日志）。随后 `mvn -o -pl backend/user-portal -am package -DskipTests` 成功，重建 `user-portal` 容器且 healthy；运行镜像 `sha256:710f7446c5b2317a1f61ae24518e375ebec0dd8e5c0ee640b7e232b3edf64f78`。

### 同一任务页面与数据库验收

重启后重载同一任务，从数据库两行基线重新操作，全部写入来自页面，未通过 API/SQL 创建或修改交易行：

1. 同样 Add 数据保存成功，重载后有第三行，UUID `9bd4147e-d7a6-4948-a6ea-d30f3ff6f85b`，Card Number 为 `LOCAL_ADD_20261023`，Merchant Name 为 `LOCAL_ADD_VERIFY`，FK 为原 case，四个审计字段填充成功。
2. 第一行 `95bf1640-31fc-4d5e-ae2e-d5d7e6ac69bb` 编辑 Merchant Name 为 `LOCAL_EDIT_20261023` 保存成功。
3. 第二行 `ccfa23c2-59e1-4dc1-8112-a26726a18a41` 编辑 Merchant Name 为 `LOCAL_EDIT_SECOND_20261023` 保存成功。
4. 查库确认两行修改实际保存，原 `assignee_id` 均仍为 liam；创建时间分别保持 09:53:40 / 09:53:44，更新时间分别刷新至 10:15:13 / 10:15:24，主键、外键未变。Docker 三次 `/submit` 均返回 200；成功阶段没有新增浏览器 error。
5. 保留三行和上述测试标记供用户复核，新增行尚未分派。任务仍停留 Transaction Assignment，未点击 Confirmed all Assignment 或推进后续业务。

截图：`frontend/user-portal/verification-screenshots/2026-10-23-mcy-crud-before.png` 是失败后的现场（错误文本以控制台和 Docker 403 为证，截图不包含已消退提示）；`2026-10-23-mcy-crud-after.png` 是重载后的三行；`2026-10-23-mcy-edit-after.png` 是重载再打开编辑弹窗后的持久化字段值。

### 本轮增量自审

Finding：新增批处理权限校验误拒绝普通 CRUD 的平台审计字段，影响 Add 和分派后的 Edit。处置：复用平台唯一审计字段判据，与既有 writer 契约一致；新增/编辑正例及只读业务字段负例均通过。复审未发现本轮增量的遗留 Blocker/Major。无独立 reviewer 放行；整体报告仍为 CONDITIONAL。

未验证：原 UAT、另一个新建 FU 的页面、该新增行后续分派/流程完成、完整导入/克隆/回滚组合。本轮只修 Portal 后端的普通子表保存校验，未修改前端合并/MI 行隔离或生命周期产物，未重跑前端 MI 全管道。既有审计值由子表弹窗填充的协议未在本轮重构为后端生成。

## 方案二：普通写入与 batch 附加权限分离（2026-10-23）

### 为什么前几轮会反复出现问题

先前将“实例曾启用 batch”当成普通写入的权限入口，并按整表对象相等判断是否修改。页面正常带回的审计值、MI 状态/当前节点、显示元数据和行版本都会导致差异；Assignment 的业务编辑与后续只读 Investigation 提交因此陆续触发同一 403。前几轮分别绕开 Lookup 对象和审计字段，只消除了已经碰到的差异，没有纠正普通写入与批量声明的边界。

方案比选：继续逐字段加豁免不能解决权限来源混杂；直接取消后端只读校验会失去真实篡改保护。采用用户确认的方案二：普通写入统一准备和授权，batch 只追加显式批量操作的约束。不会承诺此后任何配置问题均不会出现；本次将权限判据、身份和系统值的契约固定下来，并增加跨阶段、跨字段名及嵌套组合证据。

### 实现与通用性

- `TaskSubTableSubmission`：Save、Complete 在过滤/合并前使用同一入口，从实例固定发布版本取得当前任务绑定、完整主键、字段权限和控件类型。真实只读业务变更、重复身份及未知变更拒绝；未改业务值不做整行标量复检。
- `CatalogPinnedTaskFormLoader` / `BpmnMiXmlSupport`：绑定元数据及 MI 系统字段来自发布快照与 BPMN 属性，未写死 MCY 的 FU ID、表名、PK 或 status 字段。重命名主键、重命名 MI 状态列有测试。
- 系统审计值由服务端维护；计算字段和 MI 运行时回显保持服务端基线。自动 PK 分配保留，移到公用准备入口的严格身份检查之前，采用服务器发布上下文；手工 PK 不自动生成。
- 同表多个 binding 的行 claim 使用权限交集；未触碰原行无需新 claim。嵌套子表按实际父行 binding 的控件 placement 及 FK 元数据验证；省略的 nested stores 保留，旧任务留下的未放置 slice 只允许原样回显。
- `SubTableBatchTaskPermissions` 仅处理明确声明的 batch；原 batch 阶段、资格、意图、并发与 MI 行隔离 guard 保留，事务内仍复核。未改变 batch v1/v2 快照、表结构或 FU 配置。
- Lookup 身份从关系表字段元数据读取，不再猜 `searchFields[0]`；搜索、显示、PK 分开。虚拟 User 元数据公开既有 id 主键。已选标签可直接点击重选，身份加载失败可重试；BPMN 配置的标量人员字段使用 ID 存储控件。一般 Lookup 仍遵循原对象格式和设计显示列。

### 同任务红转绿与真实页面结果

所有业务写入由 Edge 页面完成，SQL 只读核对，没有通过 API/SQL 替代页面创建交易或推进流程。

1. **旧实例同任务**：`2232c503-ce89-11f1-ac67-1ab7605b2169` 在 10:37:42 点击 Request Approval → Confirm 返回原 403。最终镜像重启后同任务同操作于 11:20:04 成功；数据库确认 Investigation end_time 实际写入，创建 Transaction Approval `a3b61ef3-ce90-11f1-ac67-1ab7605b2169`。其余两条 Investigation 仍未结束，三行主键/外键/处理人及业务值保留。
2. **原 FU 新请求**：页面 Add 两条合成交易 → Case Submit → Assignment 认领 → Add 第三条 `PLAN2-UI-003` 并选择 liam → Save 成功。选择三行 Batch Edit 分派 liam，再逐条 Edit 修改第一行 Merchant Name → Save、刷新均成功。数据库确认三行，原两个 row_id 与 case_id 不变，三行 assignee_id 均为 liam。第一行测试 Merchant Name 实际为 `Plan2 ordinary workflow APlan2 edited after batch`（页面输入采用追加）。
3. **原 FU 新请求后续阶段**：Confirmed all Assignment → Confirm 创建三条 Investigation。`55cace36-ce91-11f1-ac67-1ab7605b2169` 再次 Request Approval 成功，于 11:25:28 创建 Approval `64cd6c7f-ce91-11f1-ac67-1ab7605b2169`；其余两条 Investigation 保留供复测。
4. **Assignee 显示与可编辑**：批量分派后的 Edit Record 不再显示 UUID，可直接点击已选项展开人员列表并重新选择。该 FU 原 Lookup 显示列是 username，标签实际显示 `123456`，下拉列表显示对应 `liam`；列表分派徽标显示 liam。没有强制改掉设计器显示配置，也没有把账号标签冒称为姓名。
5. **第二 FU 不同字段映射**：现有 `Batch Assign Demo 20261019`，主键 entry_token、外键 owner_ref、分派 handler_code / unit_code / pool_code。页面新增 `PLAN2-ALT-KEYS` 后发起，普通 Edit 将 units 从 1 改为 2，再 Batch Edit 分派 liam，刷新/查库通过：handler_code 正确保存用户 ID，原 entry_token / owner_ref 保留。该 FU 页面没有提供后续 Complete 动作，仅验证到保存/刷新；未用 API 绕过页面补交。

复测入口：

- 原 FU 新请求剩余 Investigation：[55c5c51e](http://localhost:3000/portal/tasks/55c5c51e-ce91-11f1-ac67-1ab7605b2169)、[55c94792](http://localhost:3000/portal/tasks/55c94792-ce91-11f1-ac67-1ab7605b2169)。
- 原 FU 新建审批：[64cd6c7f](http://localhost:3000/portal/tasks/64cd6c7f-ce91-11f1-ac67-1ab7605b2169)。请求实例 ac6a35aa-ce90-11f1-ac67-1ab7605b2169。
- 第二 FU 保存现场：[8631db55](http://localhost:3000/portal/tasks/8631db55-ce91-11f1-ac67-1ab7605b2169)。请求实例 8623aa36-ce91-11f1-ac67-1ab7605b2169。

截图位于 `frontend/user-portal/verification-screenshots/`：`2026-10-23-plan2-approval-before.png`、`2026-10-23-plan2-original-approval-after.png`、`2026-10-23-plan2-assignee-editable.png`、`2026-10-23-plan2-crud-batch-reloaded.png`、`2026-10-23-plan2-alternate-fields-reloaded.png`。before 截图错误提示已消退，原 403 以控制台及后台日志为证。

### 测试、部署与审查

- 初始失败证据：普通无 batch Investigation 单测在旧全局 guard 下失败；LookupField 用 username 搜索列与 UUID 存量值的测试在旧逻辑下失败；临时关闭新 policy 后 7 例失败，恢复通过。见本地忽略日志 `.codex-plan2-red.log`、`-lookup-red.log`、`-policy-red.log`。
- 相关后端最终宽回归 **364 项通过**，另 Common 5 项；覆盖 batch、普通权限、MI 保存、Complete、真实事务、系统 User、自动 PK。最后 nested 回显增量修复后 policy **11 项通过**。前端 Lookup/子表弹窗 **60 项通过**，前端 MI **234 项通过**；此前更宽的子表专项 76 项通过。没有宣称全仓全量测试通过。
- Portal Vite build、Maven package 成功，重新 build/recreate Portal 前后端。后端 healthy；最终 backend image `sha256:eda3e4aa5be76b951802fd9987c8ea9ca12e2a69ac350af6ed789285ef86d408`，frontend `sha256:9200a1fa232221b1b851e939ec7848a020f2f949fb232bf084ccafaa2b359d61`。上述页面验证均在这两个镜像重启之后。
- 类型检查仍有 **383 条存量错误**；对比本轮修改前同工作区基线，归一化文件路径和错误文本后无新增。类型检查不是通过状态。
- 独立只读 reviewer 初审 FAIL：同表多 binding 交集、nested placement/跨任务回显、Lookup 失败无法重试。逐条修复并增加正反例；另外恢复服务端自动 PK 分配顺序。最终重审 **PASS（本轮增量代码）**，无遗留确凿 Blocker/Major；独立 reviewer 复跑 LookupField 7 项通过，其余测试与页面证据由主任务执行。PASS 不等同于原 UAT 或整个仓库通过。

**未验证项**：原 UAT 五个 Lookup 业务数据（本地原 FU 为 Select）、完整审批结束/所有 MI 分支完成、另一账号的实际页面隔离回归、全新设计/导入 FU 的整套页面生命周期、导入/克隆/回滚组合；第二 FU 后续 Complete 无页面动作而未执行。跨字段名和权限负例由自动化及第二现有 FU 保存补充，不将其写成全生命周期验收。总体交付仍为 CONDITIONAL，以上本地问题路径已有红转绿证据。

补充红转绿审计：临时关闭多 binding 与 nested 修复后，policy 11例出现2 failures/1 error；恢复源文件后11例通过（`.codex-plan2-review-red.log` / `-review-restored-green.log`）。临时关闭 Lookup 身份失败清缓存后7例中1例失败，恢复后7例通过（`-retry-red.log` / `-retry-green.log`）。所有源文件通过 finally 原样恢复。新请求的审批截图另见 `2026-10-23-plan2-new-approval-after.png`。

浏览器日志边界：新建请求表单在前端重建前打开，11:20:19 提交成功后的跳转曾因旧 tab 引用已替换的 hashed chunk 报动态模块加载错误；请求及Case Submission实际已保存。随后用最终前端重新打开 Assignment 和后续任务完成所有操作，此时间之后未见新增 error。不能将本轮整个 tab 日志描述为零错误。

### 提交前 staged review 更正

核验类型检查原始日志后，存量错误实际为 **383** 条（此前364为计数笔误）；本轮复跑与修改前基线383条逐项归一比较，无新增/消失的错误签名。类型检查仍为失败，不是构建通过的替代证据。

另发现初轮 `missingIntentKeepsOrdinarySaveValidation` 没有普通写入的 pinned binding，仅有 batch scope overlay，因此缺声明时因绑定缺失而拒绝，未证明“普通编辑必须有batch声明”。补齐真实绑定后旧失败断言变红：Expected RuntimeException but nothing was thrown。已改为普通分派无batch声明保存成功，并断言5个Lookup对象和另一行不变；脚本将缺少整个batch声明的负例改为“明确声明ASSIGN但缺assignment意图”。前文2026-10-22 missing-intent运行输出是历史证据，方案二下不再作为合法普通编辑拒绝的依据。修正后的新脚本负例本轮只做语法/静态审查，未重新执行完整fixture脚本；测试与最新审查结果见 `2026-10-23-staged-code-review.md`。

## 金额编辑间歇保存失败诊断（2026-10-23 15:25起）

本节为日志与页面复现诊断，**尚未修改产品代码或声称已修复**。用户任务607e5b3d-ceb2-11f1-ac67-1ab7605b2169，实例605eec01-ceb2-11f1-ac67-1ab7605b2169，原FU发布1.0.132。

首次取证窗口为15:15–15:25（Asia/Hong_Kong，UTC+08:00）。Portal容器15:19左右重建，现存日志只覆盖本次容器启动后；同时检查了engine、Kong和Portal前端日志。该任务提交在15:22:13、15:23:01返回HTTP400，业务码4003：`Batch field has invalid text type: billing_amount`。相邻15:21:56、15:22:03、15:22:20、15:22:38、15:22:43、15:22:51、15:22:55、15:23:06、15:23:15多次保存200。相应失败没有Process variables updated记录，未发现该任务对应的原read-only 403或网关5xx；其他FU解析404与启动CONFIGURATION_AUDIT ERROR不是这两次失败原因。

已读发布schema：HMDC_Transaction.billing_amount和dispute_amount均VARCHAR。页面Edit Record两列实际为数字输入框。TaskSubTableSubmission.java:93–95仅对differs字段调用标量metadata校验；SubTableBatchAddedRowGuard.java:86要求VARCHAR值必须为String。故更改金额的Number被拒绝，而保留原金额、修改其他字段或不改动保存可以通过。该字段在既有process variables中已存JSON number，尚未做类型归一；本次没有改变FU字段类型或业务值。

页面复现：15:26:44将第一行Billing Amount由1点增至2后Save，控制台及后台同样400。重新打开Edit仍显示2，数据库仍1，说明本地乐观编辑没有清晰回退到持久状态。Cancel并刷新后Edit显示1；15:28:26不改金额Save返回200，库中第一行金额仍1、三条交易均在。测试期间另有用户成功保存，因此不以lock_version全局不变作为失败请求的证据。

截图：`2026-10-23-billing-amount-unsaved-local.png`为失败后仍显示2的编辑弹窗，`2026-10-23-billing-amount-after-reload.png`为刷新后显示1。`2026-10-23-billing-amount-save-failed.png`抓取时提示已消退，错误文本以console/后台日志为证。原始日志保留本地忽略文件`.codex-recent-portal.log`、`.codex-recent-engine.log`、`.codex-recent-kong.log`、`.codex-recent-front.log`及`.codex-billing-repro-portal.log`，不提交原始用户payload。

结论：已确认两条问题——服务端对配置为数字控件的文本列执行了不兼容的类型校验，以及自动保存失败后UI仍展示未保存值且弹窗关闭。不是本次已证实的数据库成功保存后又删除；对用户提到的其他删除/新增间歇现象，本轮没有逐项复现，不能外推全部原因相同。缓存有基于输入fingerprint的5秒enrichment复用，但当前已复现路径不需要缓存即可触发，尚无证据将其归因于缓存竞态。

修复方向：先统一按发布schema和已配置控件定义的值编码/验证契约，普通Save与batch共用，覆盖VARCHAR+数字控件、DECIMAL+数字控件、空值、零、金额精度等正反例；不能对billing_amount写白名单，也不能直接关闭业务校验。自动保存失败时应明确保留待重试草稿或恢复服务端基线，弹窗/成功反馈须等待实际保存结果。具体实现及红转绿验证待下一步执行。

### 再次核对Git历史后的责任边界

用户指出batch加入前该FU没有此类报错。对照实际Git历史后确认这是校验范围扩大的回归，不能归因成FU原有配置问题：

- `a40a85f96`（2026-10-18）新增严格标量类型校验，并在SubTableBatchTaskPermissions的metadataChanged路径将其应用于batch-enabled实例的普通写入；更早`27700287b^`的普通提交没有这一整行metadata类型复检。
- `6762b6ee5`（2026-10-23，本轮已提交修复）虽将batch专属guard改为显式声明才执行，却在TaskSubTableSubmission:93–95给普通变化字段直接复用了同一batch标量校验。TaskFormComponent:691 / :759和Complete:132均调用共用准备，无需存在batch标记，故静态影响面还包括没有启用batch的子表保存。
- 当前严格检查把VARCHAR+Number拒绝；原来的控件值经过权限/行归属过滤后可作为原生JSON值保存。没有证据表明需要用户修改FU类型才能恢复原有功能。
- 既有TaskSubTableSubmissionTest.form把amount定义为INTEGER，验证“INTEGER+数字”和无效字符串，却没有覆盖现场的“VARCHAR字段+数字输入控件+历史JSON number”组合。因此相关回归绿不能证明此组合兼容，前次review也漏掉了这一点。
- 前端useSubTableDialogForm通过emit(save)将行交给useSubTableRowDialog，后者先更新rows并emit modelValue；task autosave随后异步提交，失败路径记录错误而不会把成功等待传回最初的弹窗保存动作。页面复现中失败后仍显示2、库中1，与此链路一致。

后续修复应把普通保存的兼容契约与batch附加约束重新划清，保持权限、PK/FK、MI隔离和并发保护；不能只替换Batch错误文本或要求更改该FU配置。本次复核未改产品代码、测试、FU或提交历史。

## 2026-10-23 后续修复与隔离审计

上述“仅诊断”是修复前记录。后续已实施普通值契约恢复、声明范围收窄、保存失败状态及版本化写入隔离修正，并重建本地服务。真实页面完成 MCY 的新增、分派后编辑、删除合成行和双页 409 冲突验证；另一套字段名的 Demo FU 也完成保存与刷新验证。原三条用户记录保留，测试金额恢复。

逐项改动、红→绿证据、独立审查 finding 及处置、仍保留的历史 V1 行为和初始化无效 autosave 疑点，见 [后续分离审计报告](2026-10-23-batch-separation-followup.md)。本轮未从页面重跑 Investigation→Request approval 全生命周期，不能以 CRUD 回归替代。
