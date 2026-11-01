# 双层 Correspondence 展示与只读补齐写入隔离验收

## 结论与查看入口

MCY dual binding 1101 1793499506034（FU50125）已发布1.0.4。外层Case Correspondence保持按当前Case过滤；交易子表Details中补齐按当前Transaction过滤的只读Transaction Correspondence。Assignment和各交易办理节点使用一致层级，方便对照New Request中的两类数据。

新建入口：http://localhost:3000/portal/processes/start/mcy-dual-binding-1101-179349950603-20261101-5srx1c 。确认版本v1.0.4。旧请求仍使用其冻结版本，不自动迁移；用户此前a171c92f-d5bd-11f1-8c70-6e7849f291cc任务的1.0.2布局不受修改。

只读样本：http://localhost:3000/portal/applications/22b3906a-d5c6-11f1-8c70-6e7849f291cc 。General可看Case，交易行Details可看各自Transaction；流程图可选择历史节点。

## 配置范围

- Assignment 50898新增只读绑定51466，嵌入交易父绑定51443的subForm；Sub Case 50899新增51467，嵌入51446；Sub Case列表补Details链接。
- 子绑定读取既有Correspondence表50593，使用设计器已声明的Transaction过滤FK82248，structuralFk、READONLY、FULL，禁用增改删。列表同时显示Transaction ID和Case ID。
- 原外层Case绑定51464/51465保持；New Request 50897的既有可编辑Case/Transaction设计保持。
- 发布ad8d7429-8699-4c47-90c5-400ae56437d2成功。读回配置并剔除本次新增绑定、控件及链接后，与备份逐值相等：原字段权限、Batch配置、事件脚本及BPMN未变。
- 此次布局配置仅发布FU50125；FU48和50126未改设计。测试配置中的具体业务ID没有进入平台运行时代码。

## 修复验证

- **复现**：同一表增加第二个只读绑定后，Batch人员分派自动保存403，提示Multi-binding writes require binding scopes from the pinned form。客户端未提交Correspondence，只提交可写交易store。
- **根因**：Save和Complete先恢复省略的已存store，后续guard把这些未变的服务端基线误认为客户端显式写入，要求其提供scope。
- **改动**：在恢复前捕获显式store集合，只允许“未显式提交且与持久化基线完全相等”的store略过该项scope要求；显式写入（含旧bindingId别名）和任何变化继续严格校验。旧调用构造器保留严格语义。无新增HTTP参数、schema或FU设计字段。
- **红→绿证据**：新增4项回归先运行1失败，修复后4通过；原失败任务46b271d5-d5c1-11f1-8c70-6e7849f291cc原单重试，自动保存从403到200，两笔实际分派落库；继续办理到引擎结束，持久化Correspondence内容、顺序及行版本不变。
- **安全反例**：另建60a7706e-d5c7-11f1-8c70-6e7849f291cc，显式提交原样多绑定store却省略scope，真实接口仍403，持久化variables完全不变。该反例仅用API构造非法请求，正常流程均通过浏览器操作。
- **未验证项**：独立双账号与并发压力、远程环境、重新Export/Import/Clone/Rollback未执行；没有新增配置格式，以上生命周期不涉及新字段透传。隔离检查覆盖多个不同Case和Transaction，但不冒称独立双主体授权验收。

## 六组新请求完整浏览器流程

每组均从New Request建立两笔交易、各自一条Correspondence及一条Case Correspondence；保存草稿、重开、A编辑、B删除替换、Case编辑、再保存重开后提交。随后Batch选人，逐行打开Edit核对独立email回填，办理所有交易直到引擎结束，重开My Request。实际使用Playwright Chromium直连127.0.0.1:3101，无响应替换或API模拟。

- approve / direct：`22b3906a-d5c6-11f1-8c70-6e7849f291cc`；6次Case、9次交易Details检查；引擎已结束，活动任务0。
- reject / approve：`4bc5a21c-d5c6-11f1-8c70-6e7849f291cc`；6次Case、9次交易Details检查；引擎已结束，活动任务0。
- direct / reject：`7688be43-d5c6-11f1-8c70-6e7849f291cc`；5次Case、8次交易Details检查；引擎已结束，活动任务0。
- approve / approve：`9ea1a37f-d5c6-11f1-8c70-6e7849f291cc`；7次Case、10次交易Details检查；引擎已结束，活动任务0。
- reject / reject：`c9d0ef70-d5c6-11f1-8c70-6e7849f291cc`；5次Case、8次交易Details检查；引擎已结束，活动任务0。
- direct / direct：`eed05eb1-d5c6-11f1-8c70-6e7849f291cc`；5次Case、8次交易Details检查；引擎已结束，活动任务0。

六组共34个任务页面Case视图检查、52次交易Details检查全部通过。每个阶段Case表恰好1行；交易Details恰好1条自己的记录，不混入Case或另一交易。每次完成验证成功body和实际持久化；三条Correspondence字段、顺序及_wsRowVersion均与办理前基线逐值相等；普通配置的Case行只填Case FK，交易行只填自身Transaction FK。

逐taskId核对操作历史和备注，逐已执行活动核对节点/出口颜色；均0活动任务且引擎end_time存在，pageerror=0。当前发布业务将Mark Complete定义为REJECT，Portal终态REJECTED符合既有动作配置，没有为测试修改动作。

另用现有FU48（1.0.133）和显式额外FK副本FU50126（1.0.2）各新建1份（FU48：2fb6dd27-d5c6-11f1-8c70-6e7849f291cc；FU50126：7ae2a2ee-d5c6-11f1-8c70-6e7849f291cc），Batch人员分派VERIFIED，从草稿走完approve/direct混合全流程并重开；用于检验既有配置与显式双FK兼容性，不计入以上34/52。

## 截图

目录：frontend/user-portal/verification-screenshots/。

- 外层Case：2026-11-01_tx-placed-22b3906a-0-case-view.png。
- A交易Details：2026-11-01_tx-placed-22b3906a-0-1101-A-detail.png。
- B交易Details：2026-11-01_tx-placed-22b3906a-0-1101-B-detail.png。
- Investigation：2026-11-01_tx-placed-22b3906a-1-1101-B-detail.png。
- 结束重开：2026-11-01_tx-placed-22b3906a-final-1101-A-detail.png及-default-general.png。

前两张已实际打开图像核对。截图和会话文件按仓库规则不提交，保留本地路径。

## 测试、部署与基线

相关模块测试和打包：`mvn -pl backend/user-portal -am package -Dnet.bytebuddy.experimental=true -Dtest=*Task*,*SubTable*,!SubTableBatchPostgresTransactionIT -Dsurefire.failIfNoSpecifiedTests=false`。Portal1193项中1174通过、19条件跳过，Common48通过；最终复跑BUILD SUCCESS。ByteBuddy标志仅适配本机Java21测试，不改变生产Java17。

先package再重建dev user-portal Docker服务，healthy后才做浏览器验证，镜像中已包含修复。新增4项测试包含省略原样允许、显式原样拒绝、展示补齐改变拒绝、bindingId别名拒绝；焦点组34通过。

完整`regression:mi`：250单测及6浏览器场景通过。为本机浏览器代理/localhost连接问题，测试驱动仅将地址改为127.0.0.1:3101并禁用Chromium代理，使用正常密码登录会话，不改产品代码、不mock接口。

扩展后端全量（只排除要求一次性DB的SubTableBatchPostgresTransactionIT）：Portal2463项，2349通过、112跳过、2错误。两错误分别为ProcessOperationProperties缺发布start-content测试契约、RecordNoteAcceptanceDatabaseTest无法取得验收DB连接；将三个实现文件暂还原HEAD后仅复跑这两类得到相同2错误，再完整恢复修改并重跑相关package成功。SubTableBatchPostgresTransactionIT在改前基线已因缺BATCH_TEST_JDBC_URL失败，没有连接开发业务库代替一次性DB，也没有修改测试绕过。扩展全量并非全绿。

## 证据索引和边界

- .codex-1101-tx-placed-before-*、-after-forms.json、-audit.log、-published.json：备份/发布/配置不变量审查。
- .codex-1101-tx-placed-autosave-diagnose.log / -autosave-green.log：同任务403→200；原diagnose JSON在绿色复跑时被覆盖，不能充当红色原请求证据。
- .codex-1101-nested-readonly-red.log / -green2.log：测试红→绿；中间green.log仍失败，未算通过。
- .codex-1101-nested-readonly-package.log / -rebuild.log / -final-package.log：打包与Docker部署。
- .codex-1101-nested-readonly-full.log / -full-baseline-errors.log：扩展全量及原代码同错误复核。
- .codex-1101-nested-readonly-mi.log：完整MI门禁。
- .codex-1101-tx-fixed-results.json、-suite.log、-flow-0..5.log：六组新流程。
- .codex-1101-tx-fixed-fu48-flow.log、-extra-flow.log：两种既有配置完整流程。
- .codex-1101-tx-guard-result.json：真实403及variables未变；-tx-fixed-guard-flow.log：随后完整合法UI办理。

失败实验如实保留：Sub Case Details最初定位器误匹配表头，改为表体链接后重跑；额外安全反例最初在Claim前等Batch按钮超时，后调整先Claim；再次流程驱动传错Case note后超时，按实际已存值恢复同一任务，无产品改动。这些失败没有计入六组34/52通过结果。
