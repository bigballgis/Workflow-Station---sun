# Batch Form 与普通 Form：异步脚本运行时验收

日期：2026-11-04。范围：本地源码、测试、DW / Portal / Help 构建及本地 Docker 部署。DEV FU27、DEV FU35 和用户提供的业务任务未保存、分派或重新发布。本地修复及验收完成：完整 Batch 组合门禁 PASS，固定样本的完整 MI 门禁 PASS。全仓库检查的既有失败保留，整体审查 CONDITIONAL。

## 问题与实现

旧实现有两个独立缺口：共享 `PortalFormApi` 没有 `updateRule`；Batch 行会话遇到 Promise 直接拒绝。因此仅补一个方法无法使文档中的查询脚本正常工作。前次同步事件支持没有覆盖这两个能力。

本次将普通 Form 与 Batch 使用的脚本解析、作用域 API、受控 fetch、等待及取消行为集中到 `frontend/shared/src/formEvents`。Batch 仍保持逐行上下文和整批接受，正式人员/角色分派继续使用已有 ASSIGN 收集器。没有改后端保存契约、数据库结构、FU 序列化格式或冻结快照。

- `updateRule(field, { info: string })` 只更新临时文本说明。空字符串清除说明；非法字段或其他 patch 属性明确报错。说明参与恢复，不写入设计配置。
- 支持显式返回的 Promise、事件数组的确定顺序及返回 false 的提交/关闭守卫。同步脚本仍保持同步执行。
- 每次操作使用独立作用域；新输入、取消、模式切换、卸载使旧操作失效。旧 API 引用无法迟到写入。
- 同源 `/api/` GET 使用当前会话，不允许脚本指定凭证、身份头、写请求或跨源重定向。单操作总超时 15 秒、不同 GET 最多并发 4 个、相同请求复用且返回独立 Response。
- pending 时阻止 Apply / Save 接受半成品；异常恢复到上次已接受状态。多行异常整批恢复，字段业务错误阻止提交。
- 普通 Form 的组件 change、value hook、Form onChange 及后续引擎计算一起完成后才发出模型更新；父子表数据和声明的身份字段保留各自更新来源。
- DW Preview 保留脚本来源与事件数组，避免原生注入函数覆盖来源、重复执行 change、规则投影丢失输入、界面重绘反复触发生命周期。
- Help `/help/form-events` 及 en / zh-CN / zh-TW 同步说明此契约。

这不是对原生 form-create 全部实例 API 的开放。普通表单是单表单上下文；Batch 是每条选中行独立执行后整批接受，这个差别保留。

## 原脚本与样本

原文：`F:\hermes1.0\31.1299,script error\内容.md`。测试模块 `frontend/scripts/fixtures/batch-email-scripts.mjs` 保存相同脚本正文，仅换行统一为 LF 并加既有 `$FNX:` 包装。正文 SHA-256：`9c754f54fd2ba5da30aa087e23cab1011fc213ab9dbd6b877817d89b6a1dcb61`。

原脚本的 catch 只写 info 并正常结束，所以原脚本成功路径可兼容，但其业务失败不能自动当成字段校验失败。负面验收使用保留 ID 选择器及 fetch 主逻辑的 guarded 版本：开始时清除旧错误，失败用 `setFieldError`，成功清除错误。运行时不猜测说明文案含义。

- 本地 MCY 模板副本 FU50137：最小无邮件/服务任务 BPMN，普通主表、普通子表和 Batch 三入口；两条选中行及一条未选中行。
- 通用 FU50131：不同字段名、表名、主键和绑定，事件矩阵。
- BU / Role 样本 FU50132，以及既有 email / MI 专项样本。
- DW 的 Assign 目录有意使用 demo 身份，无法通过真实 admin 用户接口查询这些 demo ID。DW Batch 原脚本通过声明的 Probe 输入、真实行 assignee ID 执行；Portal 使用实际 Assign 控件验证分派和持久化。未改产品目录或候选人校验来伪造成功。

## 浏览器验收覆盖

所有通过项以新构建的真实 DOM、网络提交及刷新结果为准。错误/缺失 email、逆序响应和超时通过本地 Playwright 请求故障注入构造；成功路径使用真实本地用户接口。

- 原脚本修前报 `api.updateRule is not a function` 且邮箱 DOM 空，保存修前截图。
- 原脚本：Portal 普通主表、普通子表、实际 Batch Assign；DW 普通主表、普通子表及 Batch Probe，邮箱 DOM 等于真实查询邮箱。
- 两条选中行回填及保存后 API / 数据库回读；第三行 assignee、email、业务字段不变。
- pending Apply 禁用，A→B 旧响应不能覆盖 B，取消后无迟到回填，重新打开可正常操作。
- 人员模式退出取消旧查询；BU / Role→人员→同 BU / Role 的正式分派与普通字段编辑均保留。
- HTTP 500、缺失 email、两行一成功一失败、恢复后重试、实际 15 秒超时；错误不能生成部分 ASSIGN。
- 异步 beforeSubmit 返回 false、onSubmit 修改字段后保存、setFieldError 阻断、onReset 抛错及返回 false 均保持弹窗。
- 同步和异步矩阵均覆盖 onCreated、onMounted、onChange、onReset、beforeSubmit、onSubmit、beforeFetch；DW 加 onReload。
- 七个 hook：load、mounted、value、watch、hidden、deleted、titleClick。
- 实际输入、focus、blur、click、clear、select visibleChange、switch、number、date change/panelChange。
- label、disabled 恢复、required、hidden/display、options、notification、focus、fieldError；beforeFetch 修改实际请求头、取消请求并保留循环 config 引用。
- 事件数组的异步顺序、原生包装后来源保留，普通 Form 与 Batch 均覆盖。
- DW Preview Apply 仅更新预览，重新打开检查真实输入；GET 设计配置保持一致，无后台业务写请求。

最终 guarded 本地实例：process `24084c72-d821-11f1-a1af-aeb2e07f8cc8`，task `2416f2d5-d821-11f1-a1af-aeb2e07f8cc8`；绑定 store `dw:hmdc_transaction_verify_50137`。普通表单与 Batch 的源配置均在隔离 FU50137，不使用源业务任务保存来验证。

数据库回读：ASYNC-CARD-1 / 2 为 user-admin、邮箱等于该本地测试用户邮箱、event_trace 为 submit-finished；ASYNC-CARD-3 仍为 user-dev、邮箱及 trace 为空。读回来自 `up_process_instance.variables.__subTables__`，不是只读组件内部状态。

## 可重复执行

首次准备本地副本，在仓库根目录执行 `node frontend/scripts/verify-batch-async-events.mjs prepare`。脚本仅接受固定 localhost，检查测试标签；本地 fixture 元数据 `.codex-*.json` 不提交。

完整入口：

```powershell
pnpm -C frontend run regression:batch-events
pnpm -C frontend run regression:mi
```

Batch 门禁包含专项单测、同步矩阵、异步矩阵、BU / Role、既有 email、原文脚本和 guarded 脚本的 Portal / DW 页面验证。其用例由明确维护的脚本列表注册，开发后不会自动生成任意测试；新增覆盖必须加入维护的入口。身份组之间等待 60 秒，保留网关限流。

独立复验：

```powershell
node frontend/scripts/verify-batch-async-events.mjs original
node frontend/scripts/verify-batch-async-events.mjs ui
node frontend/scripts/verify-batch-async-events.mjs dw-original
node frontend/scripts/verify-batch-async-events.mjs guarded
node frontend/scripts/verify-batch-async-events.mjs ui
node frontend/scripts/verify-batch-async-events.mjs dw
```

MI 本地复验固定有实际数据的既有样本，避免自动选择落入刚生成的非 MI 任务：

```powershell
$env:TASK_ID = '41281eac-d5ae-11f1-875d-627eadcfb631'
$env:APP_ID = 'f7a904c8-d73a-11f1-81ef-1633fc20113b'
pnpm -C frontend run regression:mi
```

MI 初次复验的末组曾失败。只读调查确认真实 API 和 DOM 均有两行业务数据，辅助脚本把首个 checkbox 选择列当作行 ID，过滤成空。已修正 `mi-regression-helpers.mjs` 的三处身份读取，明确跳过 `.el-table-column--selection`；同一 pinned 任务、同一断言从失败变为两行通过。失败日志和截图保留，产品表格与权限未改。

相邻身份组应等待限流窗口。original / guarded 只部署明确标记的本地测试 FU，创建新实例；不会替换已有实例冻结配置。

## 门禁、证据和审查

`pnpm -C frontend run regression:batch-events` 整组 PASS：DW119 / Portal271 专项、同步/异步矩阵、BU / Role、既有 email、原文与 guarded 的普通/Batch 组合全部执行通过。

- DW 全量：1275 项通过，保留同两处 locale 基线失败（`decision-i18n.test.ts` 加载失败、`i18n-locale-key-consistency.test.ts` 失败，均为 `assignmentExclusion` 作用域问题）。
- Portal 全量：2760 项通过，保留同一项 `viewDetailForm.test.ts` Lookup 显示列基线失败。
- 类型检查：DW 164→162、Portal 381→380，按文件、TS 代码及诊断内容对照，无新增；Help typecheck 通过。
- DW / Portal / Help 均已 build，并按 `Dockerfile.local` 重建受影响本地服务。容器内 `/dev/index.html`、`/portal/index.html`、`/help/index.html` 与本地 dist SHA-256 逐个相同；已重新加载 edge Nginx，防止容器 IP 缓存造成旧页面。
- `git diff --check` 通过；增量源码、fixture、文档 secret 扫描未发现私钥、JWT 字面量或凭证字面量。临时日志与本地 fixture 元数据未纳入 Git。

专项单测：DW119、Portal271 通过。最新固定样本 `pnpm -C frontend run regression:mi` 整组 PASS：250 单测及六组浏览器截图均通过。记录 `.codex-async-mi-pinned-complete.log`，最后一组实际识别两行，而非忽略空表通过。

截图证据（保存在各应用目录，不删除）：

- [修前报错](../../frontend/user-portal/verification-screenshots/2026-11-04-async-red-original.png)
- [Portal 原脚本普通主表](../../frontend/user-portal/verification-screenshots/2026-11-04-async-original-portal-main.png)
- [Portal 原脚本 Batch](../../frontend/user-portal/verification-screenshots/2026-11-04-async-original-before-apply.png)
- [DW 原脚本普通主表](../../frontend/developer-workstation/verification-screenshots/2026-11-04-async-original-dw-main.png)
- [DW 原脚本普通子表](../../frontend/developer-workstation/verification-screenshots/2026-11-04-async-original-dw-ordinary.png)
- [DW 原脚本 Batch](../../frontend/developer-workstation/verification-screenshots/2026-11-04-async-original-dw-batch.png)
- [DW 异步事件矩阵](../../frontend/developer-workstation/verification-screenshots/2026-11-03-batch-event-matrix-dw-async.png)
- [Portal 异步事件矩阵](../../frontend/user-portal/verification-screenshots/2026-11-03-batch-event-matrix-portal-async.png)

本地过程日志（均被忽略但保留）：`.codex-async-browser-red.log`、`.codex-async-complete-gate-final2.log`、`.codex-async-mi-final.log`、`.codex-async-mi-pinned-complete.log`、`.codex-async-dw-full-final2.log`、`.codex-async-portal-full-final2.log`。失败过程日志也保留，没有把中间失败冒充最终通过。

已复核的修复发现及处置：

1. 缺失 info API、Promise 被拒绝：共享 API + 操作运行时 + 等待契约。
2. 异步 input / clear 被重复 change 取消、结构 hook 丢失：统一输入所有者并按当前操作排队，deleted 在原生值刷新后派发。
3. 角色代码未记入正式收集器、BU 查询抢先修改基线：接受后再更新，按代次隔离目录结果。
4. 普通 Form 取消恢复到已输入一半的状态：独立保存上次已接受快照；外部数据更换显式重置基线。
5. DW 原生 onChange 投影后跳过实际控件事件：实测红例和专门单测先红后绿，区分真实控件回调与程序投影。
6. 原生注入丢失脚本/数组来源，主表重复 change：保留序列化来源，主表统一派发；规则投影在无 overlay 变化时保持实例引用，避免生命周期反馈循环。
7. 普通 Form blur 提前发出半成品模型，父页面回传取消查询：删除提前发出路径，由共享 Host 在操作接受后发出模型；延迟查询单测先红后绿，浏览器固定延迟 600ms 后检查邮箱。
8. 数组在子表 Preview sanitize 和设计器默认值/持久化流程被清空或只保留第一项：完整保留数组，补 native metadata、shadow、保存/加载回归；浏览器用两段异步 Probe 脚本验证顺序和 disabled 恢复。
9. MI 回归辅助脚本把选择列当身份导致假空表：跳过明确的 selection 列；同任务浏览器红→绿，不放松行数断言。

这些发现均已处置；复审未发现本次已报告问题的未处理 Blocker / Major。因全量测试与类型检查保留基线失败，整体审查为 CONDITIONAL，不能写成全仓库 PASS。

范围外既有脚本函数体解析器不识别字符串/注释里的括号，已记录 #1740；本轮没有宣称支持任意 JavaScript 语法。全量测试和类型检查的已有失败单独列出，不能写成全量通过。

上述首轮验证时未 commit，也未向 DEV 发布。后续同步与提交前复核见文末。回滚为整体恢复 DW / Portal 前端运行时版本；不需要数据库迁移。旧异步脚本在旧版本会恢复为明确失败，不能承诺回滚后继续兼容。


## 用户任务追查：1163b550 的字段与 Lookup 契约不匹配

用户随后在 `http://localhost:3000/portal/tasks/1163b550-d826-11f1-a1af-aeb2e07f8cc8` 报告 `Batch change cannot write assignee_email: it must be declared and editable.`。此前隔离 FU50137 明确创建了真实的 assignee_email 字段，因此其浏览器通过记录不能覆盖这条配置；此前交付结论的适用范围需要收窄。

已通过该任务真实 `/form-data` 响应只读核对（本地诊断元数据保存在忽略文件 `.codex-user-report-config.json`，不包含业务字段值）：

- 冻结 Batch 绑定 ID 为 51443。
- 标题 Assignee 的实际控件是 `lookup`，绑定 `assignee_id`，系统用户查询显示字段含 username / email。
- 标题 Assignee email 的实际技术字段为 `merchant_name`，placeholder 仍是 Please input Merchant Name。
- rowDesign 中不存在 `assignee_email`；`merchant_name` 的 fieldWriteMode 明确为 EDITABLE。
- 脚本第一处 `api.setValue(target, '')` 因目标未声明被拒绝，尚未执行 updateRule 或 fetch。该拒绝符合写入守卫契约，不能通过放宽守卫消除。
- LookupField 单选 `handleSelect` 实际 emit 整行对象；原 Markdown 脚本按字符串 ID 校验。因此仅改 target 后还会遇到 Invalid assignee ID，需要按 Lookup 对象处理，或明确换回 ID 选择器。

配置修正应明确业务意图：若当前控件确实继续复用 merchant_name 存邮箱，脚本目标应写 merchant_name，并从选中 Lookup 行读取 email；若需要独立邮箱列，应正式新增 assignee_email 并重新绑定控件、发布新配置。改显示标题不会改技术字段。已有任务使用冻结配置，不能假设重新发布会替换它。

未修改该业务 FU、未替换冻结配置、未 Apply / 保存该任务。此次自动化登录账号没有该任务的办理控件，因此没有取得 Liam 账号下的重新选值或配置修正后的浏览器通过证据；只读配置证据及用户报错与写入守卫完全吻合。不得将此前隔离样本的绿证据冒充该任务已修复。

用户后续授权新增独立邮箱字段并优化脚本，已经在本地源 FU 配置、新版本测试请求上完成，详细结果见 [实际 FU 配置验收](2026-11-04-mcy-assignee-email-configuration.md)。上段描述的是授权修改之前的调查状态；旧任务冻结配置始终没有替换。

## 同步远端后的提交前复核（2026-11-04）

实际分支名为 `common_0731_SELF_AP`，远端不存在用户输入的 `commom_0731_SELF_AP`。已 fetch 并将本地 common 分支快进到 `0df293791`，随后在 `liam_common_0731_task1299_script_error` 执行 merge；因当前分支祖先已在上游，结果为快进，没有生成额外 merge commit。原未提交内容先以包含未跟踪文件的 stash 备份，合并后 apply 恢复，无冲突，备份保留。

审查模式为 staged，覆盖共享 API / parser / 操作边界、DW 普通与原生 Preview、Portal 普通与子表弹窗、Batch 分派收集器、三语帮助、测试及实际 FU 配置工具。上游同步内容作为基线，不混称为此次脚本修复。

### Findings 与处置

- **Major（已修复）**：`inFormEventOperation` 的独立异步调用只有 finally，没有在拒绝时 cancel，因此失败脚本暂存的通知仍会执行。新增独立脚本用例先复现通知数量 1（期望 0），补充拒绝时 cancel 并继续抛错后，同一用例通过，所在文件 7 项全部通过。日志 `.codex-sync-notification-red.log` / `.codex-sync-notification-green.log`。主 Host 和 Batch 已有失败取消路径，不复制新的通知机制。
- **Blocker**：本次改动未发现未处理项。
- **Minor（已修复）**：帮助页新增说明含未转义对象大括号，翻译引擎把它当插值，整段规则说明不可见。改为三语文字描述 info 属性；浏览器同一段落从缺少 Promise 说明变为可见，并保留红/绿截图。示例中的省略号还会触发现有签名识别器，已改为完整提示文本，确认示例以可复制代码块显示。
- **其他 Major / Minor / Question**：本次增量复审未发现需要继续处置的项；下述基线失败和独立计算字段问题单独列出。

### 编译、测试、运行与敏感信息

- DW、Portal、Help build 通过；最后通知修正后重新构建 DW / Portal。既有大 chunk 与模块循环引用告警保留，未放宽构建配置。
- 合并后的干净基线先记录类型检查，再恢复改动：DW 164→162，Portal 381→380；缺失 `vitest` 类型及既有源码/测试诊断仍在。原测试 mock 缺失 API 的诊断增加了 `updateRule` 方法名，属于同一条既有不完整 mock，不计作新增运行时编译错误。Help 类型检查通过。
- DW 全量单独复跑：1275 通过，保留同 2 个 locale 测试文件失败（1 个断言失败及 1 个 suite 加载失败，均为 `assignmentExclusion` 解析作用域）。初次与 Portal 并行全量时另有原生数值控件用例 5 秒超时，单文件 2/2 及第二次全量均通过；保留初次日志，不隐藏中间失败。
- Portal 全量：2768 通过，保留同一 `viewDetailForm.test.ts` Lookup 显示列断言失败；随后新增失败通知用例独立运行 7/7 通过。
- staged diff 卫生检查通过；新增脚本执行 `node --check` 通过；新增行扫描未发现私钥、JWT 或 password / secret / token 字面量。忽略文件中的本地会话、配置备份、日志及截图不纳入提交。
- 合并后完整 `regression:batch-events` PASS：同步/异步矩阵、人员/角色、设计器保存、原文/错误标记脚本的 DW / Portal 普通与 Batch 路径均通过；最后通知用例纳入后的专项复跑 DW119 / Portal277 全部通过。日志 `.codex-sync-batch.log`、`.codex-sync-batch-unit-final.log`。
- 最终 DW / Portal 容器部署后完整 `regression:mi` PASS：250 单测及六组浏览器脚本，日志 `.codex-sync-mi.log`。当前样本没有的 People 控件由现有对应单测覆盖，保留脚本明确的 N/A 输出，不声称浏览器实际操作了不存在的控件。
- 最终产物重新验证实际 FU50125：新请求 `9ffa71dc-d836-11f1-a1af-aeb2e07f8cc8`、任务 `a00c9a9c-d836-11f1-a1af-aeb2e07f8cc8`，两行 Lookup 邮箱回填、Apply / Save / 刷新、第三行隔离、普通 Edit 清空/重选/Cancel 均通过，浏览器无未捕获异常。只读查库确认三行中两行有 email、三行 merchant_name 保留、三行卡号均为 string。测试任务已通过正常 Unclaim 释放，未完成分派节点。日志 `.codex-sync-source-fu-prepare.log`、`.codex-sync-source-fu-ui.log`、`.codex-sync-source-fu-release.log`。
- DW / Portal / Help 容器入口与相应 dist 的 SHA-256 一致。Help 最后文案修正后再次 build、typecheck、重建容器和截图通过；[说明缺失截图](../../frontend/help/verification-screenshots/2026-11-04-form-events-async-red.png)、[说明与代码块恢复](../../frontend/help/verification-screenshots/2026-11-04-form-events-async.png)。实际 FU 和事件矩阵截图沿用本文件及配置验收文档中的路径，均保留。

### FU 通用性、生命周期与性能

- 新隔离 FU50137 与已有 FU50125 使用不同绑定和配置，运行时从规则/权限/分派配置读字段，产品代码不按 MCY 名称分支。专项配置工具明确锁定授权的本地 FU，不属于通用产品运行时。
- Save/Load 与数组脚本保留由单测及实际设计器保存回读覆盖；已有导出/导入/Clone/Version 数据结构未新增字段、包装格式未变，未修改这些后端消费者。没有凭此宣称所有生命周期端到端重新验证。
- API 和执行边界由共享模块承担；同一操作 GET 复用、有界请求并发、逐行隔离及取消均有正反例。MI 辅助脚本仅排除明确标记的 checkbox 列，不改变数据隔离判据。
- 整体结论 **CONDITIONAL**：新增审查问题已修复；全仓库测试和类型检查仍有上述既有失败，不能称全量 PASS。不推送远端，不向 UAT / DEV 发布。

### 范围外：计算字段与 VARCHAR Batch 校验冲突

仅调查本地与 Git 历史，未连接 UAT、修改计算引擎、放宽 Batch 校验或迁移数据。

- 本地数据库 19 个 `card_number` 字段均为 VARCHAR，`is_computed=false`，没有计算定义；实际 FU50125 的交易表 50590 同样如此。已验证新请求的三条交易行 `card_number` 均为 JSON string，没有输出卡号原值。这与用户提供的 UAT 冻结配置不同，因此本地这次未触发该链路。
- 通用代码缺陷仍在：使用合成带前导零文本，经现有 `ComputedValues.fromRowValue` 得到 Number，经 `ComputedFieldRowEvaluation.toStoredValue` 得到 BigDecimal，再调用现有 VARCHAR guard 明确得到 `Batch field has invalid text type: synthetic_text`。没有写数据库。证据 `.codex-sync-computed-java.log`。
- 前端探针同样证明纯数字文本在解释器中变成 number，前导零丢失；但 `computedFieldRuntime.toModelValue` 返回精确十进制**字符串**，并非直接存成 JS Number。故不能照搬“前端必然发生浮点精度丢失”的说法。探针日志 `.codex-sync-computed-ts.log`，临时探针已删除，不提交确认旧缺陷的恒绿测试。
- 当前仓库历史：`142f3694b`（2026-08-13）引入输入转换；`98a712e55`（2026-08-17）引入计算结果落库；`a40a85f96`（2026-10-18）加入严格文本类型校验并校验整行。与用户提供的另一仓库 commit hash / 导入日期不同；这些日期不是 UAT 部署日期。本次 merge 未修改这些位置。
- 后续修复需要保留源字段文本类型（尤其前导零）并约束目标字段输出类型，同时设计存量错误类型的修复依据。只在输出时转 String 无法恢复已经丢掉的前导零，不能仅放宽 VARCHAR guard。本次脚本提交不包含该独立修复。
