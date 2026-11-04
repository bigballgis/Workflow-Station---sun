# 计算字段：文本类型与 Batch 保存校验

2026-11-04。本地修复及验收；未发布或访问 UAT / DEV。整体代码审查为 CONDITIONAL，基线限制见末节。

## 问题与判据

旧运行时将纯数字字符串解释为数值，计算结果没有遵守目标 VARCHAR 类型。于是子表中一个未在 Batch 表单显示的计算字段，就能使编辑另一个字段的 Save 返回 400。前导零也在解释时丢失；前端使用精确十进制实现，不应误称为所有路径都经过 JS 浮点计算。

修复判据：声明为文本的长编号从主表复制到子表后，DOM、刷新响应与数据库都与源字符串逐字符一致；数字计算列仍为数字；同一旧任务的同一 Batch 编辑由 400 变为 200。不能通过放宽 VARCHAR 校验或重写冻结配置取得成功。

## 实现

- Java / TS 共享解释器读取字段声明：VARCHAR / CHAR / TEXT 保留文本，空值语义保持，数值列仍使用既有十进制计算。无 schema 的表达式样例保留原有入口兼容性。
- 输出按目标类型处理：文本写成文本，数值目标保留十进制；非法数值、非整数结果或布尔类型不匹配进入原有错误策略。没有改变 Table Design 对不匹配公式的保存限制。
- FU 目录冻结版本、旧 DW 元数据路径、Relation Table、Action 弹出表单，以及 Portal 主表/子表预览都透传现有 `dataType`；没有新增 FU 序列化配置。
- DW / Admin 公式样例预览使用同一类型规则。文本需要参与数值运算时显式写 `VALUE(text)`；实际数值列无需改公式。三语帮助同步说明该区别。
- 对存量计算文本字段仍为 number 的提交，只在独立校验副本中，使用实例绑定的发布版本重算计算结果，继续执行严格校验。普通字段、分派声明、作用域和原始提交不被改写；真实保存仍走原有服务端重算和事务。
- 校验副本复用既有 omitted-store 合并，保留聚合所需的其他子表；按配置的主键建立索引，不按位置匹配，不逐行扫描整张表。无旧类型冲突时直接返回，不增加重算。

来源字符串本身已经损坏时，无法靠猜测补回前导零。本次恢复依靠仍完整的权威来源值，不做全库猜测式补零或直接修改流程变量。

## 红 → 绿

- 前端 `computedFieldRuntime.test.ts` 同一 VARCHAR 复制用例：修前丢失前导零，修后保留完整长编号；日志 `.codex-computed-front-red.log` / `.codex-computed-front-green.log`。
- 后端目录版本测试使用 HEAD 中五个原实现文件独立编译为基线，再执行同一个测试方法：修前字符串断言失败、实际是 BigDecimal；新实现同一断言通过。日志 `.codex-computed-catalog-red.log` / `.codex-computed-catalog-green.log`。独立基线避免 IDE 自动编译 `target/classes` 导致红证据失真。
- 旧运行时上创建隔离 FU50139，原始子表字段确为 JSON number。任务 `2816bf2c-d83d-11f1-a1af-aeb2e07f8cc8` 的 Batch 表单仅编辑 Caption，保存 400，错误为 `invalid text type`；部署后同任务相同操作 200，刷新和只读查库确认文本恢复。
- 同一旧实例过程 `27fbbcd1-d83d-11f1-a1af-aeb2e07f8cc8` 的两行结果都精确等于主表字符串，超过 JS 安全整数范围的位数及前导零均保留。另一个数值计算列仍为 JSON number；未选中行的 Caption 保持原值。
- 缺省子表边界测试先失败，补充既有 omitted-store 合并后通过；没有改变返回的原始提交范围。中间测试编译/失败日志也保留，不当作产品修复证据。

## 浏览器与持久化

- FU50139：旧冻结任务恢复、新运行时新建请求、Batch Apply / 自动保存 / 刷新、普通 Edit 只读输入 DOM，均通过。
- 另建 FU50140，不同目录与物理表名，验证新建流程、Batch 保存和未选中行业务值保留。
- FU50140 测试副本原本没有完成动作。通过正式设计接口增加审批动作并发布新版本，只对新请求补测 Complete；未替换任何旧实例的冻结配置。
- 过程 `3ca455ea-d842-11f1-a1af-aeb2e07f8cc8`、任务 `3cb2fc35-d842-11f1-a1af-aeb2e07f8cc8` 经真实 Approve → Confirm 完成；数据库状态 COMPLETED，两行复制文本与源值一致，数值计算结果仍为 number。
- DW 与 Admin 真实公式弹窗均显示完整长编号及前导零。Admin 使用未保存的结构草稿，没有写入其他 Relation Table。
- 真实 Relation Table CRUD 的持久化浏览器路径本轮未执行；该服务端元数据及写入计算链有专项单测，Admin 公式预览有浏览器证据。
- 本轮仅克隆带明确 synthetic 标签的模板，保持禁用邮件监听，完成前检查流程不含 service / send / script task。失败的第一次数值公式配 VARCHAR 尝试被既有校验拒绝，留下未部署 DRAFT；没有放宽该校验。

截图：

- [旧任务保存失败](../../frontend/user-portal/verification-screenshots/2026-11-04-computed-text-red.png)
- [旧任务修后保存刷新](../../frontend/user-portal/verification-screenshots/2026-11-04-computed-text-green.png)
- [新实例保存](../../frontend/user-portal/verification-screenshots/2026-11-04-computed-text-fresh.png)
- [流程正常完成](../../frontend/user-portal/verification-screenshots/2026-11-04-computed-text-complete.png)
- [DW 公式预览](../../frontend/developer-workstation/verification-screenshots/2026-11-04-computed-text-preview.png)
- [Admin 公式预览](../../frontend/admin-center/verification-screenshots/2026-11-04-computed-text-preview.png)
- [计算字段三语帮助中的类型说明](../../frontend/help/verification-screenshots/2026-11-04-computed-field-text-types.png)

复验入口：`frontend/scripts/verify-computed-text-runtime.mjs`，依赖已有的 synthetic 模板 FU50131；这是本地专项浏览器脚本，不会因新增文件自动加入 MI 门禁。在 frontend 目录执行：旧构建用 `prepare-red` → `red`；部署修复后同任务用 `green`。新构建可用 `prepare` 创建另一隔离副本，`fresh` 新建请求；`dw` / `admin` 验公式弹窗，`enable-complete` 只为该副本配置完成动作，随后 `fresh` → `complete`。本地状态文件均忽略，不提交会话或数据备份。

## 验证与基线

- Common 计算字段 126 项通过，含新类型测试及既有 golden vectors。
- Portal 后端相关计算字段 / Batch 测试 343 项通过，含两版本目录隔离、旧 DW、Relation Table、校验副本、普通字段错误拒绝及 omitted-store。
- 标准 Portal `mvn test` 被干净基线中的两个 Postgres 测试缺少 Testcontainers 依赖挡在 testCompile。本轮未修改依赖或排除测试；专项测试用真实 javac 编译新增/改动测试后运行 Surefire。不能把专项结果称为后端全量通过。
- Portal / Common 打包通过；Admin、DW、Engine 直接下游 Maven 打包通过。
- DW、Portal、Admin、Help build 通过；本地受影响前端和 Portal Docker 服务重建，读取服务日志及健康状态后测试。
- 最终 Portal 包中校验副本、FU 重算器、RT 重算器三个 class 的 SHA-256 与容器文件一致；四个前端 dist 入口与容器一致。最终重建后另建过程 `3e3592a2-d844-11f1-a1af-aeb2e07f8cc8`、任务 `3e4b16bd-d844-11f1-a1af-aeb2e07f8cc8`，Save / 刷新 / Complete 再次通过，浏览器无未捕获异常；日志 `.codex-computed-latest-fresh.log` / `.codex-computed-latest-complete.log`。
- 最终完成后再次只读查库：COMPLETED、两行 reference_copy 均为 string、逐字符等于源值、doubled_units 均为 number；仅输出类型/数量/一致性，不输出实际卡号。日志 `.codex-computed-latest-db.log`。
- 前端计算专项：Portal160 / DW12 通过。干净 HEAD 基线先暂存记录，再恢复改动全量复跑：DW1275 通过，保留同两个 locale 测试文件失败；Portal2769→2772 通过，保留同一 Lookup 显示列失败。
- 类型诊断：DW162、Portal380、Admin135，与干净基线对照无新增；Help 类型检查通过。类型接口补齐 `BindingFieldDefinition.dataType`，没有用 any 或屏蔽规则放行。
- Admin 全量 91 项通过，`.codex-computed-admin-full-tests.log`。
- 完整 MI：250 单测和六组浏览器脚本通过，`.codex-computed-mi.log`。不存在的 People 控件仍按门禁原输出标注由单测覆盖，未冒充实际 UI 操作。
- 完整 Batch 事件回归通过，`.codex-computed-batch.log`：同步/异步事件矩阵、角色/人员切换、设计保存、原 Markdown 脚本及字段错误版本，覆盖 DW / Portal 普通表单和 Batch。
- 差异卫生及增量敏感字面量扫描通过；临时 probe、会话、元数据备份、构建日志和截图不纳入源码提交，截图保留在各应用 verification-screenshots。

## Code Review

结论：CONDITIONAL。未发现未处理的新增 Blocker / Major；全量测试与类型检查有上述既有失败，且 Relation Table 实际 CRUD / UAT 未验证，不写全仓库 PASS。

Findings 及处置：

- Major，校验重算的浅层合并可能省略聚合依赖的子表：失败测试确认，复用既有合并后通过；只在校验副本补齐，不扩大实际提交。
- Major，逐行匹配计算结果可能在大表上形成平方复杂度：改为配置主键索引，保持身份歧义拒绝及业务值校验，不按位置配行。
- Major，扩展旧 DW / RT 元数据查询时，不能用 json 是否为空区分普通字段与计算字段，否则缺少公式的计算字段会静默跳过。两个新增用例先失败，改为读取显式 is_computed 后通过；缺失公式仍明确报错。日志 `.codex-computed-missing-definition-red.log` 与最终 343 项专项结果。
- Minor，IDE 编译器对通配 Map 流的推导与 javac 不同：显式声明 map 的泛型，Maven 构建及专项重跑通过，不依赖 IDE 留下的类作为部署产物。
- 测试脚本首次误认为模板自带 Approve：真实页面没有该动作；仅修改隔离副本的正式设计后新建请求取得完成证据，没有绕过权限或直接调用引擎完成。

通用性及生命周期：运行时不按 FU、表或卡号字段名分支；使用现有 dataType / 主键 / 发布版本，不增加包格式或 schema。Clone 双副本运行已验；Export / Import / Version / Rollback 没有修改持久化格式或消费者，本轮未全链路重跑这些操作。没有改主外键、MI scope、权限、分派契约或冻结定义摘要。

发布建议：重建所有打入新 Common 的后端（Portal / DW / Admin / Engine），同步发布 Portal / DW / Admin 前端及 Help；DW 仅部署到原本提供设计器的开发环境。本修复不新增数据库迁移，目标环境仍需满足当前应用版本的既有 schema 要求。数值运算使用文本列的既有公式应核对并显式使用 VALUE。现有错误计算文本值在正常保存时由服务端恢复，不能宣称未保存的所有历史数据已经迁移。UAT 需部署后按实际任务再验；本次未访问、改数据或部署 UAT。
