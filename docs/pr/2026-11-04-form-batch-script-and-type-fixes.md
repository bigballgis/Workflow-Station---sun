# fix(forms): 对齐普通 Form 与 Batch 的异步脚本、计算字段类型及字段校验

## Summary

修复 Batch Assign 的 change 脚本调用 `api.updateRule`、返回异步用户查询时无法完成邮箱回填的问题；同时修复计算字段将文本编号转换为数字、导致后续 Batch 保存被整行类型校验拒绝的问题。普通 Form、DW Preview 与 Portal Batch 共用相应运行时和字段规则，保留 Batch 逐行隔离、整批接受及服务端权限校验。

例如，选择 Assignee 后，界面先显示查询说明，等待邮箱查询及联动完成后才允许 Apply；取消或换人后，旧响应不能回填。主表文本编号 `00123456789012345678901234` 复制到子表文本计算字段时，前导零和完整位数均保留；编辑其他字段也不会再被该历史计算值的错误 JSON 类型挡住。

## 变更类型与提交范围

- [x] fix — 脚本、字段类型和校验一致性修复。
- 今日已有提交：`c5c44f8b2`，`fix(forms): align async scripts across form and batch editors`。
- 本次补充提交：计算字段声明类型、存量 Batch 校验兼容及字段校验一致性。
- 关联问题：#1739、#1741、#1742、#1743；#1740 的既有脚本函数体提取问题仍为范围外问题。

## 修复前的具体问题

1. change 脚本执行 `api.updateRule(target, { info: ... })` 报 `api.updateRule is not a function`；仅补方法仍会遇到 Batch 不接受 Promise 的限制。
2. 普通 Form、Batch 和 DW Preview 的事件等待、原生事件包装及取消处理不同，可能出现重复执行、迟到回填或提交尚未完成的草稿。
3. 计算器将 VARCHAR 中的纯数字字符串当作数值，输出又未按目标字段类型处理。子表出现“声明 VARCHAR、实际 JSON number”，只编辑分派/普通字段也可能被 Batch 整行校验拦截。
4. Batch 固定按 boolean 检查 Switch，按标量检查多选和日期范围；邮箱/正则规则、日期时间及默认格式在不同入口处理不一致。
5. 脚本动态更新了 Select 选项后，后端仍用冻结的展示选项检查值；Batch Add 的第二次校验也重新使用旧选项。
6. DECIMAL 按原始 BigDecimal scale 判断精度，把可无损存储的 `0.5000000000` 错误拒绝；长度解析也会拒绝合法的 `3.0` 或相应数字字符串。

## 主要改动

### 1. 共享异步脚本运行时

- 在 `frontend/shared/src/formEvents` 统一脚本解析、作用域 API、受控请求、等待、取消和恢复；普通 Form 与 Batch 使用同一套相关实现。
- 增加受限 `api.updateRule(field, { info: string })`：更新临时文本说明，空字符串清除；非法字段或其他 patch 属性明确报错，状态不写入 FU 设计。
- 等待脚本显式返回的 Promise、事件数组及后续字段联动。保留同步脚本行为，以及 beforeSubmit / onSubmit / onReset 返回 false 的守卫语义。
- 操作期间阻止 Apply / Save 接受半成品；新输入、取消、模式切换和卸载使旧操作失效，旧 API 引用不能继续写入。
- Batch 每条选中记录保持独立上下文，全部成功后一起接受；任一行抛错则恢复到上次接受的状态。正式 ASSIGN 意图继续由现有收集器提交。
- fetch 仅允许同源 `/api/` GET，沿用当前会话；禁止身份覆盖、写请求和跨源重定向。单操作总超时 15 秒，最多并发四个不同 GET，相同请求复用且返回独立 Response。
- DW Preview 保留脚本来源、事件数组和真实 beforeFetch 上下文，修复重复 change、原生包装覆盖来源及状态投影问题。

### 2. 计算字段遵守声明类型

- Java 与 TypeScript 计算器读取现有 `dataType`；VARCHAR / CHAR / TEXT 保留文本语义，不因内容全部是数字而丢失前导零。
- 输出在目标字段边界转换：文本结果存为文本，数值目标保留十进制计算；非法数字、非整数结果或布尔类型不匹配仍走原有错误策略。
- 将类型元数据贯通发布目录、旧 DW、Relation Table、Action 弹出表单、普通主/子表预览和 DW/Admin 公式样例。
- 使用显式 `is_computed` 判断计算字段；计算字段缺少公式时仍明确报错，不因缺少 JSON 而静默跳过。
- 存量计算文本仍为 number 时，仅在独立校验副本中按实例发布版本重算，随后执行原有严格校验；实际保存仍走原服务端重算和事务。
- 校验副本保留被省略但聚合需要的子表，按配置主键建立索引匹配行；不按位置配行，不增加逐行全表扫描，不改写普通业务字段或提交范围。

### 3. 普通 Form 与 Batch 字段校验对齐

- Switch 按设计的 active/inactive 值处理，覆盖 boolean、0/1、Y/N；普通 Form、DW 和 Batch 默认值转换复用共享实现。
- 多选 Select 保留数组，逐项检查当前选项；必填空数组拒绝。DW 的单项数组默认值不再变成标量，保留 allowCreate 行为。
- 日期范围保留二元数组；日期时间的 date 校验复用普通 Form 使用的 async-validator 内置校验器。
- 共用缺省日期格式：日期 `YYYY-MM-DD`，日期时间 `YYYY-MM-DD HH:mm:ss`；保留显式配置，避免 Batch 产生 Date/ISO 值后被 TIMESTAMP 保存校验拒绝。
- 扩展声明式校验契约，覆盖邮箱、正则、数组、枚举等支持的规则；前后端检查规则范围，保留配置的错误提示。
- 共用 `$required` / required / mode: required 和 maxlength / maxLength 的解析。
- 校验读取每行会话的隐藏、显示、必填和选项状态；Batch Add 二次校验同样使用当前会话。
- 区分展示选项与固定值域：`api.setOptions` 可以改变运行时选项，显式 enum 继续作为约束。人员、BU/Role 和权限判断保留独立服务端守卫。
- DECIMAL/NUMERIC 先无损去尾零再校验精度，正确处理 DECIMAL(p,p) 的零；真正超出小数位或整数位仍拒绝，不自动舍入。
- 三个前端宿主明确声明/解析共享 async-validator 依赖，更新锁文件和构建/测试解析配置。

### 4. 帮助及回归入口

- 同步 Form Events 与计算字段的 en、zh-CN、zh-TW 帮助说明及 llms 索引。
- 保留原 Markdown 脚本测试样本，并提供使用 setFieldError 的 guarded 版本。
- 新增计算文本、字段校验及真实浏览器验证脚本；字段专项已注册到 `regression:batch-events`，后续维护通过该入口执行。
- 修复 MI 验证辅助脚本把 Batch 选择列误当作行身份的问题；未修改 MI 产品行身份规则。

## 影响面

- 后端：`platform-common`、`user-portal`。Common 更新需要重建所有直接下游：Portal、DW、Admin、Engine。
- 前端：shared、user-portal、developer-workstation、admin-center、help。
- 保存路径：Task Save / Complete、Batch 校验、计算重算及相关普通表单预览。
- 没有新增数据库表/字段、接口、环境变量或 FU 包格式；没有改主外键、ownership、MI scope、候选人资格、BU/Role 授权或冻结定义摘要。

MI 不变量 I1–I7 通过现有完整 MI 门禁回归，覆盖 My Request、file-only、共享附件、父行 scope、Link Form 行身份、form-below-table 与缓存身份；具体单测/浏览器映射见 [MI_REGRESSION](../../frontend/user-portal/MI_REGRESSION.md)。这不表示每条不变量都新增了一次人工 UI 操作。

## 验证结果

### 浏览器与持久化

- 原 Markdown 脚本及 guarded 版本：Portal 普通主表、普通子表、实际 Batch Assign；DW 普通主表、普通子表及 Batch 输入入口均验证。以真实邮箱 DOM、保存、刷新及查库为判据。
- 等待时 Apply 禁用、A→B 逆序响应、Cancel/重开、人员/角色切换、HTTP 失败、缺少 email、混合行失败、15 秒超时和恢复重试均覆盖。
- 同步及异步事件矩阵覆盖表单生命周期、提交/重置/fetch 守卫、七个 hook、输入/focus/blur/clear/change，以及字段说明、标签、选项、可见性、禁用和错误状态。
- 旧冻结计算文本任务：同一任务只改 Caption，保存 400→200；查库确认文本逐字符等于源值，数值计算列仍为 number，未选中行业务字段未变。
- 新副本验证 Save / Complete，数据库状态为 COMPLETED；DW/Admin 公式样例预览保留长编号和前导零。
- 旧冻结字段样本：同一任务由 Numeric switch 阻止 Apply 变为保存成功，没有替换冻结配置。
- 缺省日期格式样本：同一任务由 `4003 / invalid timestamp: occurred` 变为成功；自定义开关、多选、范围、日期时间、动态选项和 DECIMAL 计算均经 API/数据库核对。
- 普通 Form 与 DW Preview 对照：非法邮箱/正则阻止保存，修正后通过；DW Apply 仅改变预览，API 重读设计不变。
- 测试使用本地合成 FU，未保存或重新发布源业务任务，未访问或部署远程 DEV/UAT。

### 自动化、构建及审查

- 完整 `regression:batch-events`：最终串行 PASS，DW121 / Portal286 单测及所有注册的浏览器组合通过。
- 完整 `regression:mi`：250 单测及六组浏览器场景通过；固定有实际数据的 TASK_ID / APP_ID。
- 最终 Portal Batch 后端专项：400 项通过。计算字段阶段另有 Common126、Portal 计算/Batch343 及前端计算专项通过，统计有交集，不相加作为独立总数。
- 提交前再抽查字段/工作区/普通规则/默认值专项：81 项通过。
- Common 与四个后端下游 package 成功；Portal、DW、Admin、Help build 成功，相关本地 Docker 服务已重建并核对运行产物。
- DW/Admin frozen-lockfile 离线安装检查通过；增量敏感字面量与 diff 卫生检查通过。
- 代码审查为 **CONDITIONAL**：未发现未处置的新增 Blocker/Major，以下基线与未验证项保留，不将全仓库检查标为 PASS。

### 已知基线和未验证项

- Portal 最终全量：2781 通过，同一 Lookup 显示列既有用例失败；DW 全量：1277 通过，保留两个 locale 相关失败文件。
- 类型诊断 Portal380 / DW162 / Admin135，与干净基线比较无新增；不称为 typecheck 全绿。
- 标准 Portal Maven testCompile 仍受两处缺少 Testcontainers 依赖的既有测试阻碍；后端专项由实际 javac 编译后执行 Surefire，不能称为后端全量通过。
- Relation Table 真实 CRUD、远程 UAT、Export/Import/Clone/Version/Rollback 的独立完整浏览器矩阵未全部重跑；相关代码和双副本运行证据见验收记录。
- 过程中容器重建导致的 502、并行回归触发的登录限流均不计作通过；最终串行回归通过，未修改限流配置。

## 发布与兼容性

1. 先检查目标环境已有迁移已按其版本执行。本修复本身不新增 schema；本地重启新构建服务时曾补执行仓库现有 109 迁移，不代表目标环境可以跳过既有迁移检查。
2. 重建并发布打入新 Common 的四个后端：user-portal、developer-workstation、admin-center、workflow-engine；部署时 DW 仅限本来就部署该服务的开发环境。
3. 同步发布 Portal / DW / Admin 前端及 Help，重新打开页面后复验实际任务，不以本地 Docker 结果代替 UAT 验收。
4. 既有公式若将文本列当数字使用，需要核对并显式使用 VALUE；声明为数值的列保持数值运算。来源值已经损坏时无法猜测恢复前导零。
5. 原脚本 catch 仅写 info 会正常结束；需要阻止业务失败提交时，必须 setFieldError 或继续抛错。info 不是错误状态。
6. Batch 回填目标必须声明且可编辑；数组控件应绑定匹配的存储类型（本次样本使用 JSON），不通过放宽 VARCHAR 校验解决配置错误。
7. 运行时升级不会替换现有任务的冻结脚本文本；若需修改冻结脚本的业务错误处理，另行安排存量方案。历史错误计算文本在正常保存时通过权威来源重算，不是全库批量迁移。
8. 完整原生 updateRule、多选 Lookup、附件等新 Batch 控件和自定义 validator 脚本不包含在本次兼容承诺内。

回滚须配套恢复后端 Common 及相关前端版本；已经保存的业务数据不会随应用回滚自动撤销。旧运行时也会重新失去本次新增的异步/API 兼容能力。

## 验收文档与截图

- [异步脚本运行时验收](../verification/2026-11-04-batch-form-async-events.md)
- [计算字段文本类型验收](../verification/2026-11-04-computed-field-text-types.md)
- [普通 Form / Batch 字段校验验收及完整 Review](../verification/2026-11-04-batch-form-field-parity.md)
- Portal 控件：`frontend/user-portal/verification-screenshots/2026-11-04-batch-field-parity-portal-controls.png`
- 普通 Form：`frontend/user-portal/verification-screenshots/2026-11-04-ordinary-field-parity.png`
- 日期格式同任务红→绿：`2026-11-04-batch-field-parity-date-default-red.png` / `2026-11-04-batch-field-parity-date-default-green.png`，位于 Portal 截图目录。
- DW：`frontend/developer-workstation/verification-screenshots/2026-11-04-batch-field-parity-dw-controls.png`
- 更多计算字段、原文/guarded 脚本和 MI 截图见上述验收文档；本地日志、会话、fixture JSON 和截图保留在本地，不纳入源码提交。
