# Batch Form 事件扩展验收

日期：2026-11-03。承接[首轮报告](2026-11-03-batch-form-events.md)。本报告为后续扩大测试的结果，覆盖范围与限制以本报告为准。代码和测试数据均在本地，未提交 Git。

最终串行回归状态：PASS。独立入口以当前最终构建完整跑完 DW、Portal、BU/Role 与 MCY 页面/数据库验收，最终日志为 [batch-events] PASS。

## 测试范围与证据层次

表单事件八项都已进入真实页面验证：onCreated、onMounted、onChange、onReload、onReset、beforeSubmit、onSubmit、beforeFetch。onReload 在 DW Preview 的两条预览记录间切换触发；其余在 DW Preview 和 Portal 都验证。beforeSubmit 返回 false 阻止 Apply；onReset 抛错阻止关闭；beforeFetch 修改真实 HTTP 请求头、写入界面字段，并验证返回 false 时请求没有发出。

七项 hook 均有页面触发证据：load、mounted、value、watch、hidden、titleClick、deleted。deleted 使用真实条件字段的显示/移除验证；titleClick 点击真实标签；hidden 包括初始化显示和随后隐藏/显示。Batch 的 load/mounted 是逐条草稿生命周期，不能理解为对每条记录创建了一套真实 DOM。

控件真实操作覆盖 input 的 focus/input/blur/click/clear/change、数字 change、Switch change、Select change/visibleChange、DatePicker change/panelChange，以及人员 Lookup、BU Cascader、Role Select 的 change。回填结果检查实际输入 DOM 值；保存检查请求业务响应、刷新 API 数据和 MCY 样本的数据库值。

`batchEventMatrix.test.ts` 另外包含 85 项：八个表单事件、七个 hook、64 个支持控件类型/事件的分发组合，以及初始化顺序、逐行链式联动、循环限制、事件数组、异步/危险/语法错误拒绝、提交守卫和完整 API 状态。64 个组合验证脚本分发及参数，包括 `_on`/`_hook`；它们不等于 64 个真实控件交互都逐项做了浏览器操作。

API 覆盖读取/批量写值、字段标题寻址、form 快照、hidden/display、disabled、required、标签、选项增删清空重置、字段错误、通知、Lookup 筛选/刷新、焦点及状态恢复。页面实际验证了显隐、初始隐藏再显示、禁用后恢复、必填/字段错误拦截、改名/恢复、动态选项/恢复、通知/清除和真实焦点。Lookup 筛选/刷新目前为 API 状态单测；Lookup 选择并读取 email 为真实页面验收。

## 独立样本

- FU48 `MCY Debit Card_20260525`：仅作为只读模板。
- FU50128：MCY 结构的独立合成副本，三条合成交易。勾选两条，选人回填 email；Apply 前及 Cancel 后数据库不变；Apply 请求包含 ASSIGN 与 EDIT，两条记录的人员 ID/email 持久化正确，第三条业务数据不变。
- FU50131：新建的结构外键普通 Batch FU，使用 input、数字、Select、DatePicker、Switch、条件字段、原生 HTTP fetch 和事件 trace。DW Preview 批量新增两条、不保存设计/不申请真实主键；Portal 编辑第一条，第二条业务数据不变。初始化、编辑、守卫恢复和提交回填在同一组合中验证。
- FU50132：新建的人员/BU/Role Batch FU。BU 回填、Role 回填、人员回填及 Role→人员→同一 Role 再次选择的完整切换均验证；Apply 生成正式 ASSIGN+EDIT，人员/BU/Role 互斥结果正确，未选中记录业务数据不变。
- MI 附件数据在既有明确命名的合成 FU50084 中新建，请求流程仅含测试用户任务，无邮件/服务任务；附件经实际上传服务创建。随后完整 MI 回归入口通过。

运行实例 ID 由本地忽略文件 `.codex-batch-events-fixture.json`、`.codex-batch-matrix-state.json`、`.codex-batch-role-event-state.json` 保存。回归每次新建实例，避免在已经完成分派的数据上把重复选择误判为成功。

## 真实场景检查

- 即时回填和逐条独立上下文；未选中记录保留。
- Cancel 不落库；Apply 不重放 change，人员分派与普通字段更改分别提交。
- 错误脚本/非法写入恢复全部选中草稿；初始化错误不会被无关编辑清除。
- readonly、disabled、主键、外键、计算字段和 ownership 写入限制；脚本不能替代正式分派意图。
- beforeSubmit 拒绝后可编辑恢复；setFieldError 拦截后 clearFieldError 恢复；onReset 拒绝关闭后可恢复。
- 初始隐藏字段通过初始化脚本显示；后续隐藏/显示、禁用/恢复、动态选项/恢复原选项均检查实际控件。
- 条件字段删除 hook、标题点击 hook、日期面板切换、预览行切换。
- beforeFetch 的原生循环引用上下文、事件数组、false 取消、抛错中止及写值回滚；内部 Promise 适配用于原生请求链，不代表开放异步用户脚本。
- BU/Role→人员→BU/Role 切换时清理事件草稿；再次选择相同值仍能正确触发回填。清理多字段/多条记录中任何一处事件失败时全部恢复。
- textarea/password/email 和 datetime 的显示类型不应被后端误当成 validator 类型；真正的 validate[].type 与边界/选项限制继续校验。

## 此轮发现并处理的问题

1. DW 设计器只读 parser 把脚本临时 props.disabled 提升为 rule.disabled，解除禁用后控件仍锁定。隔离 Batch 原生实例的这层 parser，保留 Workspace 的设计/权限锁；真实数字控件测试先红后绿，静态 readonly 仍锁定。
2. resetOptions 仅删除事件状态，原生规则仍残留动态选项。同步时明确恢复设计器选项；同一回归先红后绿，页面重新出现 Original 选项。
3. 使用了当前打包版本不存在的 api.focus。改为使用原生 el 引用定位真实控件；带真实 Element Plus Input 的焦点测试先红后绿，页面 activeElement 断言通过。
4. beforeFetch 把包含循环引用的原生 API 上下文当字段值做 JSON 复制，且写值缺少统一刷新/回滚入口。保留上下文引用，经 Workspace 事务入口执行；循环引用和 DOM 写值测试先红后绿。
5. beforeFetch 的 false 被原生全局 hook 忽略。使用原生要求的 Promise 拒绝语义适配，抛错也中止请求；真实 HTTP 取消反例先红后绿，正常请求仍发出带验证头的 GET。
6. 覆盖了设计器配置的标签宽度。恢复配置优先、缺省 auto；原有全量测试中的反例恢复通过。
7. 后端把 props.type=textarea/password/email/datetime 当成 validate[].type。只对真实 validator 读取其 type，保留控件边界和元数据校验；后端红绿测试及真实 Portal 保存通过。
8. Role→人员→相同 Role 时，显示选择已清理但事件会话仍保存旧值，第二次 change 不触发。模式切换时通过统一事务清理已选择值，失败保留原模式；单测及真实切换先红后绿。
9. 初始化脚本取消隐藏后，渲染器又使用静态 hidden/display 作为回退，字段仍不可见。使用会话的有效显隐状态，并单独保留 Assign 非活动模式的强制隐藏；两项测试先红后绿，页面初始显示/再次切换通过。

## 测试与构建

- DW 相关专项：109 项通过，其中包含真实数字控件与设计器 readonly parser 的集成测试。
- Portal 相关专项：252 项通过，Workspace 36 项，事件矩阵 85 项。
- 后端 Common 6 项、Portal Batch/Lookup 302 项通过。命令：`mvn -pl backend/user-portal -am '-Dtest=SubTableBatch*Test,BatchLookup*Test' '-Dsurefire.failIfNoSpecifiedTests=false' test`。
- Portal 全量：2741 项通过、1 项基线失败（350 个文件）。未通过项为 `viewDetailForm.test.ts` 的未配置 Lookup 显示列反例，HEAD 独立源码、相同依赖下同样失败（17通过/1失败），未当作本次新增回归。
- 完整 MI 入口：最终容器上再次执行 `pnpm run regression:mi`，250 项单测及六个现有截图脚本通过，日志 `frontend/.codex-batch-mi-postfix.log` 以 `[mi-regression] OK — unit + screenshots` 结束。附件实际有一条记录，collection 无文件行串入。注意：现有若干脚本在没有 People inline 的任务上只做基础页面断言并引用单测，不能把入口成功夸大为所有 MI 组合都有正例截图。
- 两端构建通过，Docker 前端已重建；Portal 后端在 package 成功后重建并恢复 healthy。最初公共 jar 修改涉及的四个后端已在首轮统一重建。扩展后端测试首轮曾与 package 并行而受到 target/classes 临时缺失干扰，改为串行后308项全部通过，未将构建干扰当成产品问题。
- 全量类型检查仍有既有诊断：Portal 381、DW 164；与 HEAD 独立源码相比，诊断数量及文件/错误码分布一致；LookupComponent 随新增 prop 顺移一行，未改动文件的部分 union 文本顺序不同，无新增本次代码诊断。没有把类型检查记为通过。
- `git diff --check` 与增量密钥扫描通过。无新增依赖、产品数据库迁移或 FU 打包结构变化。

## 可复跑入口

在 `frontend` 运行 `pnpm run regression:batch-events`；可传 `-- --unit-only` 或 `-- --ui-only`。该入口已注册到 package.json，串行运行 DW/Portal 专项、通用事件 UI、BU/Role 切换及 MCY email 场景。不会自动生成新脚本，也未将通用 Batch 场景混入 MI 专项集合。

这是本地有状态的验收入口，需要上述三个忽略的 fixture 状态文件和运行中的本地服务；缺失时明确报错，不能在无样本的 CI 中视为通过。全新环境应先用平台 API 创建带 `batch-verification-synthetic` 标签的独立测试 FU，再初始化状态文件和相应 create/prepare 脚本；不能指向业务 FU。当前本地样本可直接复跑。

回归入口在认证组之间等待60秒，MCY配置与页面验证之间也等待60秒，保留网关限流。首轮串行入口的 MCY登录曾遇429，日志保留在 `frontend/.codex-batch-event-regression-rate-limit.log`；未关闭限流或把失败计为通过。

## 截图

- [DW 事件矩阵（含预期拦截提示）](../../frontend/developer-workstation/verification-screenshots/2026-11-03-batch-event-matrix-dw.png)
- [Portal 保存后重开核对](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-event-matrix-portal-positive.png)
- [BU/Role切换回填稳定画面](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-role-event-positive.png)
- [MCY email 草稿](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-events-email-draft.png)
- [MCY 保存刷新](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-events-saved-after.png)

红→绿日志保留在各 app 的 `.codex-batch-*-red.log`、根目录 `.codex-batch-backend-type-red.log`、`.codex-batch-role-mode-ui-before.log`、`.codex-batch-fetch-cancel-ui-before.log`、`.codex-batch-initial-visibility-ui-before.log`。最终串行入口日志为 `frontend/.codex-batch-event-regression.log`。

# Code Review 结果

## 结论

CONDITIONAL：本次范围内的已发现代码问题均修复并复审关闭；全量类型基线错误、一个全量单测基线失败和下列覆盖边界仍保留。

## Findings / 逐条处置

上方九项均有具体反例、最小修改与同一判据的红→绿证据；无未处置的本次代码 Blocker/Major。重点复审了：静态 readonly 不能被脚本解除；人员字段只能由正式 ASSIGN 提交；模式清理跨字段/跨记录原子恢复；原生请求失败与取消能真正阻断 HTTP；初始显隐不暴露 Assign 非活动模式字段。没有通过放宽服务端写入权限、候选人资格、静态选项或数据类型限制使测试变绿。

### Blocker

- 无未处置项。首轮 ownership/正式 ASSIGN 意图问题已通过原身份比较、无意图保护、真实 Lookup 与实际 ASSIGN+EDIT 请求复审关闭，详见首轮报告。

### Major

- 已关闭：`frontend/shared/src/batch/BatchDesignedForm.vue:94` 的临时禁用固化，`:57` 的选项重置，原生控件焦点引用与初始化显隐同步。反例、影响、最小修复与红绿证据见上方第1、2、3、9项。
- 已关闭：`frontend/shared/src/batch/BatchDesignedForm.vue:130`、`rowEvents.ts`、`BatchWorkspace.vue` 的 beforeFetch 循环上下文、界面刷新及请求取消/失败链。反例、修复及真实 HTTP 证据见第4、5项。
- 已关闭：`backend/user-portal/src/main/java/com/portal/component/SubTableBatchFormRules.java:45` 的显示类型误校验，及 `frontend/shared/src/batch/BatchAssignmentEditor.vue:62` 的模式旧值。见第7、8项及对应后端/真实切换证据。
- 已关闭：设计器标签宽度覆盖及顶层 readonly/disabled 保护，既有反例与新增锁定测试均通过。

上述处置依据 bugfix-verification 的同判据红→绿、code-review 的普通路径兼容/完整运行要求及 implementation-autonomy 的最小修改要求；剩余验证缺口列在下方，不计为通过。

### Minor

无待处置项。

### Question

无必须由用户补充数据或审批才能完成本轮验收的问题。

## 范围 / 验收映射与触发矩阵

覆盖共享事件/API、DW Preview、Portal runtime、保存发布、Assign 人员/BU/Role、草稿事务、前后端校验、真实持久化和重复运行入口。修复共享 renderer 会影响普通结构外键 Batch 与 MI 分派前 Batch，两类独立样本均测试。普通 Form 共用解析/API 的既有专项通过，DW 全局只读 parser 本身没有修改。

## Function Unit 通用性与生命周期

产品代码不依赖 MCY、演示人员、业务列名或 FU ID。Save/Load、发布后 runtime 和新/已有模板两类样本通过。Export/Import、Clone、Version/Rollback、Admin 两条导入路径未改存储结构，公共格式校验已同步；未独立重跑全部生命周期操作，不声明这些路径已运行验收。

## 编译、静态检查、测试、增量敏感信息与性能

见上方实际命令与日志。增量密钥扫描为0候选；无新增依赖。Batch仍限制200条，事件循环仍限制1000轮；未做200条复杂脚本的浏览器性能基准。

## 残余风险 / 未验证项

- 多选 Select.removeTag、日期范围 calendarChange 等扩展事件有分发单测，未逐个做真实浏览器选择；它们涉及的数组值/范围值不作为本轮完整可保存能力验收。
- Lookup 筛选与刷新、部分 options 增删变体仅做 API 状态单测，未逐项观察真实查询请求。
- 服务端仍执行发布设计中的静态 required、选项、边界和元数据约束。界面脚本调整不等于可以绕过这些约束；将动态新增选项或动态放宽必填作为服务端契约需要另行设计，未在本轮放开。
- 用户脚本返回 Promise 仍被明确拒绝；本轮只支持同步脚本。虚拟逐条生命周期与真实单条 Form 的 DOM 生命周期不能视为完全相同。
- 全量测试/类型基线问题保留；完整 FU 生命周期运行矩阵和极限性能未验证。无全量放行豁免。

## 范围外问题

克隆旧 MI 样本50084/50005时，动作表单引用校验拒绝包，记录于 #1737，未修改克隆逻辑。为加强 MI 样本曾尝试旧 inline 保存与增强 API seed，分别被 nested binding scope / 版本上下文保护拒绝，未绕开保护；这些尝试没有计为正例。可用附件样本与现有 MI 回归入口的通过记录单独保留。


# 提交前复审（2026-11-03）

## 结论

CONDITIONAL：本次暂存范围无新增 Blocker 或未处置 Major，可提交当前改动；全量旧失败、类型基线和已列明的覆盖边界继续保留，不将整体门禁描述为 PASS。

## Findings

### Blocker

无。

### Major

无本次新增项。此前各项修复经完整暂存范围复审，没有发现遗漏的宿主调用方、ownership 提交绕过或前后端事件格式不一致。

### Minor

- `frontend/shared/src/formEvents/api.ts`：暂存检查发现文件末尾多余空行；已去除，重跑 `git diff --cached --check` 通过。仅空白调整，不改变运行行为。

### Question

无。

## 范围 / 验收映射与触发矩阵

审查完整56个暂存文件，包含相关新共享模块、测试、三语帮助、回归入口及验收文档。命中共享运行时、Batch事务、原生控件、分派权限、前后端校验、i18n及测试脚本。无依赖升级、产品表迁移或打包结构变更。

## 编译与静态检查

核验本轮四后端打包、三前端 build、Docker及实际页面证据；编译产物与被审实现一致。类型基线与 HEAD 独立源码对照见上文，未降低规则。暂存 diff 卫生及敏感信息增量扫描通过。

## 测试与运行证据

复审实际执行 `pnpm -C frontend run regression:batch-events -- --unit-only`，DW109与Portal252项通过；实际重跑后端Batch/Lookup Common6和Portal302项，通过且 BUILD SUCCESS。核验最终完整Batch事件入口与最终容器MI入口的业务断言、截图及数据库证据，均通过。全量Portal2741通过/1项原代码同失败，继续记录。

## Function Unit 通用性与生命周期

新建结构外键、人员/BU/Role及MCY模板副本三类样本通过。既有rule/options序列化和引用结构不变；存储/打包/ID重映射算法未改，未独立重跑的生命周期项维持上文标记。

## 增量敏感信息

检查全部暂存新增/修改行、新文件和拟提交message，无密钥候选；仅提交源码、测试、帮助和验收记录，不包含本地fixture状态、日志或截图生成物。

## 性能

复核200行与1000轮限制、保留原生实例及状态指纹更新；未新增后端查询。极限性能仍未量测。

## 残余风险 / 豁免与范围外问题

沿用上文已明确的测试/类型基线及能力边界，无新增豁免。#1737模板问题为范围外台账记录，不作为本次代码回归。
