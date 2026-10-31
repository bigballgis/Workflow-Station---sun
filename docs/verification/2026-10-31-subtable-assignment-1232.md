# 子表 Edit、Link Form 与 Batch Form 修复验收（1232）

日期：2026-10-31，香港时间。范围：本地 Docker / `http://localhost:3000`。

已在真实前端复现并修复。未部署公司远程 Dev，也未修改原 MCY FU 48 的配置或版本。
测试副本为 FU 50123：`MCY 1232 UI verification`，从 MCY Debit Card 克隆；首轮验收版本为 1.0.2，后续 Edit 排版补验使用 1.0.3。
该副本保留在本地，便于复查。

## 复现、根因及修复

1. **Lookup 保存 409**：Link Form 选择用户后，任务页自动保存 `/submit` 返回
   `Frozen assignment difference context is invalid`。
   比较器把 ID 与同一用户对象视为不同分派，目标提取只接受字符串。
   现在按 binding 的 `assignmentConfig.assigneeField` 严格解析身份；相同身份不产生虚假变化。
   无身份对象、多目标、失效用户、阶段和权限检查继续拒绝非法写入。
2. **Edit 与设计控件不同**：设计 / Link Form 的 Lookup 只查询和显示 USERNAME，
   Edit 却改用平台专用人员查询，展示 USERNAME / DISPLAY NAME / FULL NAME。
   现在存在设计 sourceRule 时使用设计控件；没有设计子字段的历史 Assignment 标记仍保留原适配。
   同时删除显示名辅助函数中把 Lookup 对象压成 ID 的写入，保留 email 等事件输入。
3. **Complete 仍拒绝对象**：补测确认 MI 集合构造器另有只接受标量的路径。
   现在任务集合边界复用同一严格身份解析，仅输出任务需要的人员 ID，原业务行仍保留 Lookup 对象。
4. **Batch Import Fields 路由错误**：Batch 画布的导入弹窗把普通 Form 中的字段标为已导入。
   现在目标解析、去重和写画布使用同一个活动画布选择；同表多绑定优先当前 binding。
   导入 Batch Form 不修改普通 Form 或 List View。
5. **Batch Lookup / email 回填**：本地版本原来同时拒绝 Lookup 和非空 change 事件，
   配置发布后 New Request 整页加载失败。现已支持普通 Batch Lookup 和 Assign 人员 Lookup。
   change 在 Apply 时对每个勾选行分别执行，使用独立的行 API；只能写已声明且可编辑的字段。
   未勾选行不执行事件。普通回填字段需要 Batch Edit 权限。
   字段事件以外的生命周期脚本、自定义 validator、受保护字段写入及其他 API 方法明确拒绝。
   支持范围校验接入 DW 保存提示、共享发布 / Admin 包导入校验及 Portal 快照捕获。

新增行事件复用两端已有脚本解析器；Batch 渲染器不执行一份“统一值”的字段脚本。
允许的行 API 为 `api.getValue`、`api.setValue` 和 `api.form`。
原嵌套子表 / Notes 布局与读写上下文未重写。

## 同一路径红 → 绿证据

- **保存**：失败任务 `4897eed6-d4e2-11f1-9a83-160f56d111fe`，Link Form 选人后 `/submit` 409；
  重建后点击同一页面 Retry save 成功。查库确认用户对象仍含 id 和 email。
- **Edit**：修复前人员查询显示三个平台字段；重建后同一设计的 Edit 仅显示 USERNAME，
  选择用户后 email 正常回填，保存 / 刷新后仍存在。
- **导入**：修复前 Batch 弹窗把普通 Form 已有字段禁选；修复后 Batch 可独立导入 Transaction Date，
  活动画布出现该字段，点击 Save 后重新读取配置仍包含该字段。单测另断言普通 Form 和 List View 未变化。
- **New Request**：相同测试副本的 Lookup + change 配置先显示事件不支持，重建后正常打开并提交。
- **Batch 保存**：最终流程 `b2209700-d4e7-11f1-a759-6acd226d917b`，选中 A、B 两行，C 不选。
  Assign 内 Lookup 与普通 Lookup 都选择真实用户；Apply、自动保存和刷新通过。
  测试副本用 merchant_name 承载 email，脚本附加各行 card_number 后缀，证明 A、B 分别读取自己的行。
  普通 Lookup 的 authorization_code 对象也保留。查库 / API 重读断言 C 的业务字段未变化。
  `_wsRowVersion` 由既有服务端范围并发控制推进，单独排除于业务字段不变断言。
- **Complete**：同一失败任务 `217f9876-d4e7-11f1-a759-6acd226d917b`，原先返回
  `Instance MI collection source cannot be verified`；补修并重建后，同一任务提交 200，数据库出现 3 个
  `Transaction Investigation`，全部分派给测试用户。最终新流程也完整验证同样结果。
- 浏览器验收脚本断言 `pageerror = 0`；验收并未以 HTTP 200 代替业务状态检查。

截图保留在仓库的 verification-screenshots 中：

- `frontend/user-portal/verification-screenshots/2026-10-31_1232-before-frozen-error.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-before-edit-picker.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-after-edit-picker.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-before-batch-start.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-after-edit-email.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-batch-before-apply.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-after-batch-persisted.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-after-complete.png`
- `frontend/developer-workstation/verification-screenshots/2026-10-31_1232-before-batch-import.png`
- `frontend/developer-workstation/verification-screenshots/2026-10-31_1232-after-batch-import.png`
- `frontend/help/verification-screenshots/2026-10-31_1232-batch-guide.png`（真实帮助页包含逐行事件说明）

## 测试与构建

- Portal 后端：`mvn -pl backend/user-portal -am package -Dtest=SubTableBatch*Test,TaskSubTableSubmissionTest -Dsurefire.failIfNoSpecifiedTests=false`：314 项通过，BUILD SUCCESS。
- platform-common：`SubTableBatchDefinitionTest`：6 项通过。
- Portal：事件隔离、Lookup 对象、Batch Workspace / Assignment Editor、AddDialog：54 项通过。
- DW：字段导入、Batch 配置与 Preview：31 项通过。
- `pnpm run regression:mi`：250 项单测及 6 个真实浏览器场景通过；既有独立 MI FU 50029 / 流程
  `13bbd2b2-c86e-11f1-a63d-c6848797bef7`，覆盖 Details、行隔离、附件、跨步骤 People、UUID 和办理人切片。
  重复登录曾触发限流，最终使用已有合法会话运行，未修改限流或产品接口。
- 四个后端模块 package、Portal / DW / Help 前端 build 成功；已重建相关本地服务。
- 新增身份、显示对象、MI 集合和导入回归测试均留有修复前失败记录。
- 浏览器脚本：`frontend/scripts/verify-1232-assignment.mjs`。

## 改动位置

- `AssignmentPersonIdentity`、`SubTableBatchAssignmentDifferences`、`SubTableBatchChangeGuard`、
  `SubTableBatchMiCollection`：身份比较、资格和意图检查、任务集合输出。
- `BatchFormControlContract` / `SubTableBatchDefinition`、`SubTableBatchFormRules`、Lookup 值及行元数据校验：
  同步支持范围，保留严格权限和目标校验。
- `SubTableAddDialog` / `useSubTableAssignment`：使用设计控件并保留对象。
- `useFieldImport` / `FormDesigner`：活动画布接线及去重。
- `frontend/shared/src/batch`、Portal / DW 宿主：Lookup 渲染、独立行事件及逐行 patch。
- 三语文案、现有 Sub-Table Guidelines 和 llms 索引同步更新。

## Code review

结论：**CONDITIONAL**。本轮静态和运行重审未发现新增 Blocker / 未处置 Major。

首轮发现及处置：

- Major：Edit 保存显示名时仍把对象转换成 ID → 删除转换，红→绿测试及真实页面保存通过。
- Major：Complete 的冻结 MI 集合路径仍只接受字符串 → 复用严格身份解析；同任务 500→200，查库确认 3 个任务。
- Major：不能直接放开任意 Batch 脚本 → 仅支持字段 change；行 API 和可写字段均有边界，失败时不提交部分结果。
- Major：Batch 导入仍会调用刷新普通 Form 的元数据函数 → Batch 分支不再调用该刷新，单测断言普通画布和刷新函数均未触碰。
- Minor：新增画布 ref 在 setup 中的初始化顺序 → 延迟读取 tab；单测和真实 DW 页面通过。

通用性：业务判据读取 binding / assignmentConfig / sourceRule；产品代码不写死 MCY 或样本字段。
现有完整配置透传未增加 schema。Save/Load、Clone 后发布、DW→Admin Deploy ZIP 和 Portal 已运行验证；
两条 Admin Import 静态共用同一校验。独立手动导入、独立新建 FU 及版本回滚未执行。

## 未验证项与限制

- 未对公司远程 Dev 的原 Credit Card 任务操作或部署；本轮结论限本地真实环境。
- 未执行完整审批至全部子任务完成、全部上传 / Notes 组合及独立双账号授权验收。
- 未逐项验收 BU / Role 使用自定义 Lookup 的组合；本轮 UI 验收覆盖人员 Lookup 和普通 Batch Lookup。
- 全量 `vue-tsc --noEmit` 仍有存量错误，未宣称类型检查全绿；本轮新增 Batch / Lookup / 事件文件无新增类型错误。
- 全量后端仓库测试未执行；上面列出的是相关模块专项测试和完整 MI 门禁。
- 代码未提交、未推送。

## 补验：Edit 与设计布局、控件属性一致性

用户反馈任务 `3d94cc33-d4ef-11f1-a759-6acd226d917b` 的 Details 和 Edit 排版仍不同。
读取该任务实际 1.0.3 配置确认：Assignee email 是 miAssignment 的子字段，输入框配置了 placeholder 和 showWordLimit。
用户任务由 liam 领取，本轮没有释放领取或改写该任务；在同版本独立测试任务
`610653da-d4f0-11f1-a759-6acd226d917b` 上复现和验证。

- **复现**：Playwright 在同一任务打开 Edit，email 位于 Assign 容器外；ARN 显示占位文字 `ARN`，且无字数提示。
- **根因**：布局分组只识别 assignee / BU / Role，未保留容器的其他设计子字段；画布转 DialogColumn 时丢失 placeholder / showWordLimit，模板另用字段标题补充空占位文字。
- **修复**：保留设计子字段归属，整个子字段序列的末项负责关闭 Assign 容器；传递占位文字、字数提示；明确配置为空时保持为空。
  业务身份、事件及保存逻辑未变更。
- **红→绿**：相同 Playwright 检查由 emailInsideAssignment=false、emailClosesAssignment=false、arnPlaceholder=ARN、wordCount=0，
  变为 true、true、Please input ARN、1；下拉框与数字框的空占位文字也有 DOM 断言。
  原始 3 条新增测试修复前失败，补修后相关 61 项测试通过。
- **运行验证**：Portal build 并重建 Docker 前端后，同任务 Edit / Details 截图与 DOM 检查通过；
  Edit、Link Form、Batch 回填重新保存并刷新通过，未勾选行仍保持不变。页面 pageerror=0。
  本轮完整 MI 门禁 250 项单测和 6 个浏览器场景通过；最后一次占位文字补修重建后，再跑 6 个场景全部通过。
- **截图**：`frontend/user-portal/verification-screenshots/2026-10-31_1232-parity-before-edit.png`、
  `2026-10-31_1232-parity-after-edit.png`、`2026-10-31_1232-parity-after-details.png`。
- **脚本**：`frontend/scripts/verify-1232-edit-parity.mjs`，复用已认证本地会话；没有模拟产品接口。
- **重审处置**：复查发现未配置占位文字的控件仍回退到字段标题，已补修并增加空值断言。
  本轮未发现新增未处置 Blocker / Major；整体审查仍为 CONDITIONAL（前述类型检查与远程验收缺口）。
- **未验证项**：本轮不宣称全部 Form Design 控件、所有嵌套布局与弹窗外框像素完全一致；
  重点验收用户截图涉及的 Assign 子字段归属、占位文字和字数提示。独立双账号与远程环境未补验。

## 全面对照复查：修复前的问题记录（后续处置见下节）

日期：2026-10-31。本轮是检查，不修改产品实现。**设计一致性结论：不通过，仍存在已复现 Major 问题。**
前文“修复通过”仅对应列出的原始失败路径，不代表所有控件和布局已完全对齐。

### 检查范围与样本

静态对照了 Edit 的规则到列转换、布局分组、全部控件渲染分支、字段隐藏 / 只读、校验规则转换、事件 API、子表 / Notes 安放位置；
再用真实 DW Preview、Portal Edit、Portal Details 三方验证反例。

独立 FU 50124 `1232 Edit comprehensive audit 1793427072904` 从 FU 50123 克隆，发布为 1.0.0。
Form 50895、SUB binding 51432、流程 `e173425d-d4f1-11f1-a759-6acd226d917b`、
任务 `e18b85a9-d4f1-11f1-a759-6acd226d917b`。
只在新副本加入检查配置，原 FU 48、用户正在使用的 FU 50123 和 liam 的任务未改动。
在检查弹窗中试点数字加号和打开下拉框后取消，没有保存这些试验值或完成任务。

### 真实界面已复现的剩余差异

1. **Major：行 / 列布局丢失。** 相同 elRow / elCol span=12，DW Preview 左右两个字段同一行，Edit 上下两行；
   DOM 坐标断言 columnsSameRow 为 DW=true、Edit=false。Details 也变为单列。
   根因：`dialogFormLayout.ts:65` 递归展开容器，输出为纵向 column 列表，未渲染栅格、span 和 gutter。
2. **Major：Tabs / Collapse 容器消失。** DW 有各 1 个 tabs / collapse，Edit 两项均为 0，连标签 / 面板标题都没有；Details 两项均为 1。
   `subFormCanvasColumns.ts:41` 的布局展开集合未包含 Tabs / Collapse，未带 field 的父节点又被字段过滤器排除。
   即使补齐字段提取，`dialogFormLayout.ts:57` 也仅提取其字段，不生成可切换的 Tabs / Collapse 容器。
   本轮确认容器消失；没有把测试配置中所有 pane 内字段的完整交互宣称为已验收。
3. **Major：时间 / 日期范围控件改变类型。** `timePicker` 在 DW / Details 是时间选择器，Edit 是普通文本框（time=0）；
   `datePicker + type=daterange` 在 DW / Details 有双端范围选择器，Edit 只有单日期（range=0）。
   `subFormCanvasColumns.ts:161` 把范围类型映射成 date；普通 time 无 Edit 专用分支，落入文本兜底（`SubTableAddDialog.vue:627`）。
   本轮未保存范围值，不能声称已经发生持久化数据损坏。
4. **Major：数字步长和按钮位置丢失。** 设计 step=5、controlsPosition=right、precision=2，
   DW 从 0 点一次加号得到 5.00；Edit 得到 1.00，且按钮在左右两侧。Details 也按 1 增加。
   Edit 数字分支 `SubTableAddDialog.vue:211` 未绑定 step / controlsPosition，后者也未列入属性传递集合。
5. **Major：Select 搜索和禁用选项失效。** 设计 filterable=true，一项 option.disabled=true。
   DW 搜索输入没有 readonly，目标选项含 is-disabled；Edit 搜索输入 readonly，选项不含 is-disabled。Details 同样失效。
   `subFormCanvasColumns.ts:200` 只保留 option 的 label / value；Edit Select 也未绑定 filterable 或 option.disabled。
6. **Minor：嵌套 Card 被扁平化。** DW / Details 同时有 outer card、inner card；Edit 只有 outer card，inner card 标题和边界消失，字段仍显示。
   `dialogFormLayout.ts:65` 展开内层 Card，模板只按外层 group 生成卡片（`SubTableAddDialog.vue:65`）。
7. **Minor：无数据字段的展示控件被省略。** DW Preview 中的 `fcTitle` 章节标题在 Edit 不出现（sectionTitle：DW=1、Edit=0）；Details 有对应标题。
   `subFormCanvasColumns.ts:109` 只接收带 field 的规则，章节标题等无 field 展示节点没有对应 Edit 入口。
   Alert / Divider / 其他展示控件的代码路径同样需要核对，但本轮只把 DW 中确实显示的 fcTitle 作为已复现证据。
8. **Minor：Switch 两侧文案丢失。** DW 显示配置的 Audit disabled / Audit enabled，Edit 两侧文案都没有；Details 也没有。
   Switch 的 activeText / inactiveText 未在 Edit 属性集合和模板中透传。

### 代码确认、尚未逐项进行界面实测的差异

- **表单级标签 / 样式配置**：Edit `SubTableAddDialog.vue:32` 使用自动测量 label-width、固定 label-position=left，
  普通弹窗宽度固定 600px（`:923`），未按设计 options 恢复标签位置、组件尺寸和完整 rule.style / col 样式。
  这是静态缺口；本轮没有另外构造全部 top / right 标签和响应式配置组合。
- **Nested Sub-Table / Notes 位置**：Edit 在普通字段表单结束后分别循环渲染 nestedSubTables、recordNoteFields（`:643`、`:689`），
  因而不能表达它们在设计字段之间或某个容器内部的任意位置。
  位置问题属于静态确认；本轮没有新建带附件、Notes 和多层子表的完整界面组合。
- 其他控件属性，例如 textarea 的 autosize、日期自定义格式、评分 / 滑块的额外展示属性，并未做全部值域与组合测试，不能据现有专项绿灯宣称它们完全一致。

### Details 也存在差异，不能替代 DW Preview 作为唯一标准

本轮设计 showWordLimit=false、maxlength=80，DW 和 Edit 都不显示计数，Details 仍显示 0 / 80。
`FieldRenderer.vue:13` / `:45` 依据 maxlength 开关计数，未依据 showWordLimit。
此外，本轮 Details 也有多列变单列、数字步长丢失、Switch 文案丢失及 Select 配置丢失。
`props.hidden` 在三方还表现为不同的整行 / 控件隐藏范围；这与 rule.hidden 的语义需分别核对，本轮没有把它简单归因为 Edit 单方缺陷。

### 已通过项与证据

- 前轮同任务的 Assign 子字段归属、配置 placeholder、showWordLimit=true、空占位文字断言已通过；本轮 Edit 的 showWordLimit=false 也与 DW 一致。
- 本轮 readonly 输入框：DW / Edit / Details 都不可编辑。没有据此推断所有权限、容器隐藏和事件组合都已通过。
- 本轮真实浏览器 pageerror=[]；相关现有 61 项测试通过。这些测试没有覆盖上述全部显示反例，因此单测通过不能视为设计一致性通过。
- 本轮未改产品代码，未额外重建镜像；检查的是上一轮已构建部署的实际页面。

截图（均保留）：

- `frontend/developer-workstation/verification-screenshots/2026-10-31_1232-comprehensive-dw-preview.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-comprehensive-edit.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-comprehensive-details.png`

### 处理方向与限制

共同根因是 Edit 把设计规则树再次转换成列清单，再单独维护一套控件模板；容器、无字段节点和未列入白名单的属性在转换中丢失。
后续修复应以原始设计规则树为渲染依据，同时保留已有行级权限、PK / FK、Lookup 身份和保存契约；
不宜仅继续逐条给缺失属性打补丁。

该次复查完成时，上面的问题尚未修复；后续已分轮处理，最新结果见下节。
没有逐项覆盖全部控件属性组合、语言 / 分辨率、远程环境、导入回滚生命周期或双账号权限，也未宣称全量设计 parity 已通过。

## 分轮修复与最终重审

用户授权连续修复，只有 code review 达到 PASS 的范围才允许 commit。本轮修复已部署本地；
**整体 code review 为 CONDITIONAL，未 commit / push。** 原因是全量门禁仍有已证实的基线失败，不能把专项通过写成全量 PASS。

### 第一轮：控件类型与属性

- Edit 支持普通时间、日期范围、日期时间范围，保留 valueFormat / format；两端共用 `DesignedDateTimeField.vue`，
  范围数组直接透传，不压成标量。数字保留 step / controlsPosition；Select 保留 filterable 和 disabled 选项。
- 任务、发起、我的申请的字段转换共用 `applyDesignerControlProps`，同步开关文字、值、数字配置和明确的 showWordLimit 开关。
- 布尔 / 自定义字符串 Switch 的 required 校验与值类型保持一致；保留只读、字段权限及原有保存处理。
- 同一检查任务中，时间 / 范围控件数由 0 变 1，数字加号从 1.00 变 5.00，搜索框可输入、禁用选项恢复 is-disabled。

### 第二轮：设计树、容器和展示节点

- 新增 `SubTableDialogLayout`，递归渲染现有 FormField 设计树，保留 Row / Col、Tabs、Collapse、嵌套 Card、无字段展示节点。
  原来的行数据、FK / PK、表单事件、Lookup、分派及 Save 逻辑继续由原 Dialog 承担。
- 补齐 elRow / elCol 别名，以及 Tabs / Collapse 的子字段提取。命名 Tab 默认打开第一个页签。
- Nested Sub-Table / Notes 在设计节点对应位置渲染；只有历史设计未提供位置的附加描述才在末尾显示；按 binding / key 去重。
  位置和隐藏树有组件回归断言。本轮未另造全套带 Notes 上传、复杂祖孙外键的交互流程。
- 同一页面的两列由上下变左右，Tabs / Collapse 从 0 变 1，内层 Card 标题和章节标题恢复。

### 第三轮：跨入口一致性与视觉终审

- Edit、Details 和 DW Preview 均支持已配置的顶部标签、小尺寸控件；普通 Edit 外框与 DW 子表预览使用同一 700px 上限，保留嵌套表的扩展宽度。
- DW Preview 的 props.hidden 按整项隐藏处理；Details 在没有事件覆盖时遵守设计隐藏标记。
- Batch 保留设计者明确配置的标签宽度，只有未配置时才使用 auto，原有 213px / right 回归断言恢复通过。
- 视觉复查进一步复现 fcTitle 默认字号 16px / 24px 的差异，改为复用现有原生标题组件；Edit / Details computed font-size 均为 24px。
- 修正占位文字的语义：**未配置**时使用原生默认值（如 Select 的 “Select”）；**明确配置为空字符串**时保持空白。
  前文早期“未配置保持空”的表述仅反映当时实现，已由本次三方对照纠正。原字符串 placeholder 继续原样保留。

### 真实运行证据

- 设计检查 FU 50124，普通布局任务 `e18b85a9-d4f1-11f1-a759-6acd226d917b`。
  顶部标签 / 小尺寸的独立任务 `d7b73fb8-d4f6-11f1-a759-6acd226d917b`；仅更改独立检查副本配置。
- 使用本轮修复前 index 快照启动隔离的本地页面（3159），同一真实后端、同一任务和同一检查断言失败；
  最终 Docker 页面（3000）通过。修复前后截图分开保存，没有用“无报错”代替 DOM / 业务断言。
- DOM 检查包括同一行坐标、时间 / 范围控件类型、Tabs / Collapse、内层卡片、章节标题、字号、开关文字、
  数字步长、下拉搜索与禁用状态、showWordLimit=false、readonly，以及缺省占位文字。
- 新流程 `6b27e31b-d4f8-11f1-a759-6acd226d917b`，任务 `6b5004e7-d4f8-11f1-a759-6acd226d917b`：
  Edit、Link Form、Batch 两行回填、保存并重读通过，未选中的 C 行不变；Complete 后查库确认 3 个分派后的 Investigation 任务。
- 最终页面 pageerror=[]。原 MCY FU 48 与用户 liam 已领取任务未被改写或释放。

截图：

- `frontend/user-portal/verification-screenshots/2026-10-31_1232-comprehensive-before-edit.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-comprehensive-after-edit.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-comprehensive-after-details.png`
- `frontend/developer-workstation/verification-screenshots/2026-10-31_1232-comprehensive-dw-preview.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-options-edit.png`
- `frontend/user-portal/verification-screenshots/2026-10-31_1232-options-details.png`
- `frontend/developer-workstation/verification-screenshots/2026-10-31_1232-comprehensive-dw-top-preview.png`

复验脚本：`frontend/scripts/verify-1232-form-parity.mjs`、`verify-1232-form-parity-metrics.mjs`、
`verify-1232-assignment.mjs`。合法本地会话文件不纳入 Git。

### Code review 首轮 findings 与逐条处置

首轮结论为 **FAIL**，发现如下实现 / 交付问题：

- **Blocker：新增日期包装组件的 modelValue 使用 unknown，绑定原生控件时不满足其类型。**
  已改为原生支持的字符串、数字、Date 及对应数组 / null 联合类型；新增组件和 helper 无新增类型诊断。
- **Major：只恢复章节节点不足以证明显示一致，字号仍为 Portal 16px、DW 24px。**
  已复用原生标题组件，并增加运行时字号断言；同页面 16px→24px，截图保留。
- **Major：把缺省 placeholder 和显式空字符串合并，会覆盖原生默认行为。**
  已分开处理；Select 缺省 “Select” 与显式空字符串分别断言，实际页面由失败转通过。
- **Major：继续扩大现有超标文件违反长度约束。**
  日期 / 时间两处重复模板抽为真实共用组件；辅助节点映射和 DW 预览归一化移至小模块。
  本轮未扩大原超标 Portal / DW SFC，保留原有职责边界。
- **Major：全量测试显示 Batch 覆盖设计标签宽度。**
  修正 runtimeOptions 合并顺序，213px / right 用例通过；复验 Batch 逐行回填与保存。
- **Minor：旧 assignment 测试仍要求 Lookup 变成字符串 ID，与已批准的保留对象契约冲突。**
  更新该断言，明确验证 id / username / display_name 对象保留，同时仍断言不调用 legacy assign API。

重审已核对增量与完整本次范围：设计判据来自元数据，不写死业务 FU / 字段名；只读和保存契约未绕过；
无新增 schema 或配置持久化格式。Clone 后发布、普通布局及选项变体均真实运行；
本轮纯渲染修改没有新增导入 / 回滚序列化字段。没有以运行时读取 sourceRule 代替后端权限检查。

### 全量门禁与剩余限制

- Portal 完整单测最终为 **2599 通过、1 失败（343 个文件）**。修复前快照为 **2587 通过、3 失败**；
  两个本任务相关失败已处置。最后失败为
  `src/composables/mainTableView/__tests__/viewDetailForm.test.ts` 的“未配显示列应留空”用例；
  基线同样失败，属于独立 Main Table View Lookup 显示路径，本轮没有改它或弱化断言。
- Portal 新控件 / 布局 / 校验专项通过；DW 预览测试 3 项通过。新增测试分别保留了修复前失败或临时关闭对应修复后失败的记录。
- Portal / DW 均 build 成功并重建 Docker。相关服务正常运行。
- 最终 `pnpm run regression:mi` 的 250 项单测及 6 个真实浏览器截图场景全部通过；复用原有合法会话，不修改限流和产品接口。
- TypeScript 对照使用相同依赖与修复前 index 快照：基线 **383** 条、最终 **381** 条诊断；
  逐文件 / 错误码比较无新增，新增文件无类型诊断。**全量 typecheck 仍失败**，不能写成全绿。
- 当时总体审查保留 **CONDITIONAL**，按当时“只有 PASS 才 commit”的要求未提交；后续提交决定见文末。
- 本地截图和反例均已修复；未对所有任意 CSS、全部控件属性组合、远程公司 Dev、独立双账号和完整审批生命周期作穷举验收。

## 追修：Details 选择人员后的 email change 未执行

用户任务 `f943dcdb-d4fb-11f1-a759-6acd226d917b` 实际加载 1.0.3，普通 Form 和 Batch Form 均已有 Lookup change 脚本，
不是缺少配置。普通脚本写入 merchant_name（标签 Assignee email）；Batch 测试脚本另外附加各行 card_number 后缀，用于证明逐行上下文。

- **复现**：独立流程 `67286170-d4fd-11f1-a759-6acd226d917b` / 任务 `673978cc-d4fd-11f1-a759-6acd226d917b`，
  初始 email 为空，在 Details 选择用户后仍为空；UI 断言明确失败。
- **根因**：`useSubTableLinkFormDialog.updateLinkedFormField` 仅更新值，没有执行该字段已配置的 change。
- **验证纠正**：前轮脚本先在 Edit 填入 email，再到 Details 检查非空，沿用旧值不能证明 Details 执行了回填。
  本次将测试改为从空值开始选择并断言真实回填；此前对应“Details 回填通过”的证据不充分，以本节替代。
- **修复**：`useLinkFormChangeEvents` 从包含 Assign / Tabs / Collapse 的字段树提取当前行事件，复用 Edit 的事件解释器和行 API；
  可编辑 Details 的字段变化调用该解释器，只读入口不执行编辑脚本，没有更改用户任务或 FU 配置。
- **红→绿**：同一独立任务、同一配置，Details 选人后 email 从空值变为真实选择对象的 email；保存 / API 重读通过。
  另验证清除选择、不同弹窗模型隔离、只读入口；13 项相关测试通过。
- **Batch 行为**：按用户已批准的方案，change 在 Apply to page 时对每个勾选行执行，弹窗统一输入区不即时预演行事件。
  UI 重验确认 Apply 后 A / B 分别获得本行回填值，刷新后仍存在，未选中 C 行不变。
  Lookup 下方资料表的 Email 是展示数据，不等于写入独立的 Assignee email 字段。
- **截图**：`frontend/user-portal/verification-screenshots/2026-10-31_1232-details-email-after.png`、
  `frontend/user-portal/verification-screenshots/2026-10-31_1232-batch-email-after-apply.png`。
- 已 build / 重建 Portal；完整 MI 250 项 + 6 个浏览器场景通过。新增代码和测试无新增类型诊断。
  当时总体 code review 仍 CONDITIONAL，未提交；本次未重新穷举其他生命周期、动态显隐 API 与所有权限组合。


## 最终提交复核与基线风险接受

用户本地复测后确认“应该没啥问题”，并明确授权“只要没有新增问题……commit 代码，并生成 prmessage”。
据此按增量无回归标准提交，不将既有全量失败描述为通过，也不改变检查配置或削弱断言。

- 最终只读复核：本次范围未发现新增 Blocker / Major；运行时读取设计字段树、sourceRule、lookupConfig、assignmentConfig 与 binding 元数据，未加入 MCY / HMDC、具体 FU ID 或业务字段名判据。
- 最新专项复验：前端 5 个测试文件、13 项通过；后端人员身份、批量规则与 MI 集合 52 项通过，Maven BUILD SUCCESS。
- 既有全量结果仍为 Portal 2599 通过 / 1 个基线失败，TypeScript 基线 383 条 / 对照结果 381 条，逐文件和错误码无新增诊断；全量门禁仍非全绿。
- 增量敏感信息检查未发现新凭证；会话、日志、本地截图及隔离基线目录不纳入提交。暂存 diff 空白检查通过。
- 本次增量审查通过用户确认的提交标准；完整门禁结论仍保留 CONDITIONAL，基线 Main Table View Lookup 测试与类型债务留待独立处理。没有推送或创建远程 PR。
