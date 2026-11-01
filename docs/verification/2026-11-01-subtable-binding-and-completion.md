# 2026-11-01：双绑定外键、Investigation 推进与 Batch 配置验收

## 结论

按用户确认的推荐方案完成本地修复和浏览器复验。本次增量未发现剩余阻断问题；仓库完整门禁仍为 CONDITIONAL，既有失败见文末，不能表述为全量全绿。

1. 双绑定外键问题已复现并修复，草稿保存 / 重开 / 编辑 / 删除替换 / 再次提交通过。
2. Investigation 完成时的只读子表误写已修复。实际进入下一步后，任务页面上的 Investigation 为绿色；未复现需要另改着色代码的独立缺陷。
3. Batch Form 配置 assignee Lookup、字段 change 和逐行 email 回填，本轮已验证现有实现可用，没有新增 FU 专用配置或回填代码。

## 方案比较与确认

- 方案 A：仅在流程发起入口增加一次绑定过滤。改动较少，但补值与保存校验仍有两套判据，显式双外键重叠记录容易再次分叉。
- 方案 B（采用）：共用发布 binding 的过滤外键及显式额外填充字段规则；按已声明行身份限制补值，再完整执行原有 scope、父上下文、行版本及权限校验。
- 不采用关闭归属检查、给所有 binding 放行全部外键、或者通过业务表名 / 列名判断层级的做法。
- 用户明确回复“按推荐方案连续修复”。后续完成校验过程中发现的显式重叠记录、嵌套主键事件断链及单绑定父表边界均在该验收范围内处理。

## 一、双绑定外键

### 复现与根因

从原 MCY FU 48 克隆独立 FU 50125，新增一张两条结构外键的 Correspondence 验证表：
Case 绑定按 related_case 过滤，Transaction 绑定按 related_transaction 过滤；普通场景没有额外填充配置。
从页面新增 A/B 两笔交易及各自一条 Correspondence，再新增一条 Case Correspondence，保存草稿后提交。

修复前真实返回 403：`New sub-table row contains a foreign key not owned by this binding`。
`ProcessStartFormEnricherComponent` 按表级全部 FK 和祖先上下文补值，给交易级记录多填了 Case FK；之后 scope guard 按 binding 拒绝该值。

### 最终修复

- 发起入口将 transport 层的 scopes 单独传给补值组件，不写入业务变量。
- `SubTableFkOwnership` 读取同一发布版本中 `SubTableWriteDesign.Binding` 的过滤字段与显式额外字段；只补允许的空 FK，保留非法客户端值供后续守卫拒绝。
- 配置主键 / 复合主键 / 平台行身份复用现有 scope guard 的解析与比较，不猜列名，不按 UUID 形状推断业务身份。
- 是否存在 scope 按表判断，其他表有 scope 不会使一个明确的单绑定父表失去兼容路径；新行归属检查只覆盖本次 scope 涉及的表。
- 显式双外键会形成合法重叠：同一记录可见于两个过滤视图。每个 claim 仍校验父上下文与行版本，随后要求至少一个已验证 binding 的配置能完整授权该行全部非空 FK，最后只递增一次行版本。
- 不将多个 binding 的部分权限取并集：两个 binding 各自只允许一个 FK、却同时申报双 FK 新行，仍拒绝。

### 显式双外键验证发现的前端断链

另从 50125 克隆 FU 50126，只给交易 Correspondence 显式配置 Case FK 的 PRIMARY 填充。
页面复现：第一笔嵌套新增分配的 Case 主键没有传回主表，第二笔又分配新 Case 编号，第一笔成为旧引用。

修复 `SubTableAddDialog → SubTableField → FormRenderer` 的 `update:primaryFormData` 事件传递。
同一 Request 共用首次分配的 Case 主键；没有修改主键生成策略。事件类型移入现有 helper types，未扩大已有超长 SFC。

### 红→绿证据

- 普通双绑定：同一路径 403 → 200，进入 Transaction Assignment。
- 最终普通样本：流程 `900f83fa-d5a2-11f1-a759-6acd226d917b`，两笔交易、三条 Correspondence；交易行只有交易 FK，Case 行只有 Case FK。
- 页面重开草稿：A/B 各自只显示自己的子行；编辑 A，编辑后删除并替换 B，编辑 Case 行，保存草稿重开后再次提交成功。
- 最终截图样本：流程 `bd544233-d5a2-11f1-a759-6acd226d917b`，包含上述编辑 / 删除 / 重开操作。
- 显式双外键：流程 `6ffdad39-d5a2-11f1-a759-6acd226d917b` 提交成功；查库确认交易两条记录均有正确 Case + Transaction FK，Case 记录仅有 Case FK。
- 两份样本均查库断言：两笔交易 / 三条消息，消息主键互异；A/B 嵌套副本与对应顶层行主键、父交易身份一致，无丢行。
- 合法浏览器会话直接向真实接口提交三个反例：未授权额外 FK、组合两个 claim 试图扩大权限、伪造 binding ID，全部 403；提交前后实例数相同，无部分流程创建。
- 临时关闭补值 / 基线修复的新增测试出现 3 个断言失败；恢复后通过。
- 以旧版 guard 运行重叠回归，合法显式重叠失败；最终实现保留合法重叠与非法组合的正反例。
- 前端新增事件回归先失败（没有主键更新事件），修复后通过；真实页面两笔交易和三条消息的 Case 编号由不一致变为一致。

## 二、Investigation 节点

### 复现与根因

独立 MCY 样本的 Investigation 点 Mark Complete 返回：
`Versioned sub-table writes require binding scopes; reload and retry`，任务仍在原节点。

对照持久化实例与 TaskInfo，业务行只在动态展示的 sub_task_status / sub_task_current_node 上不同。
完成链先从 TaskInfo 补齐省略表，把展示增强值带回了写入，之后触发行版本守卫。

### 修复与运行验证

`TaskApprovalPreparer` 在展示数据 hydration 之前，从持久化实例补齐省略的表；已有批量运行契约的实例才使用该行为。
显式提交的值不被替换，继续经过权限、scope 和版本检查。复用本次已经需要加载的实例，未增加逐行查询。

- 原失败任务完成返回 200，数据库确认生成 Mark Completed。
- 最终版本新流程 `593268a2-d5a2-11f1-a759-6acd226d917b` 完成 Assignment 后生成三个 Investigation。
- 完成其中任务 `8bb2926f-d5a2-11f1-a759-6acd226d917b`，数据库生成下一步 `c9da6f42-d5a2-11f1-a759-6acd226d917b`。
- 下一步页面实际 computed fill 为 `rgb(232, 245, 233)`、stroke 为绿色，Investigation 已点亮。浏览器 pageerror 为空。
- My Request 汇总页：当另两笔交易仍在 Investigation 时该节点为橙色；三笔全部走过后为绿色。这是汇总状态，本轮没有将仍有活动实例的节点强制改绿。
- 本地完成推进后没有复现独立着色缺陷，因此未修改 BPMN 解析器或颜色代码。

## 三、Batch 配置与 email

本轮真实打开 DW Batch Form，点击 Assignee，可编辑字段、Lookup 来源、显示列和事件。
已有 MCY 验证副本 50123 v1.0.3 中，Batch 与普通 Form 都已保存独立 change 配置。

本轮再次从页面执行 Edit / Details / Batch：选择用户，勾选两行后 Apply，两行分别写入 email，第三行业务数据不变；保存重读正常。
之后显式为第三行分派，Complete 成功，数据库确认三个 Investigation。未修改 Batch 产品代码或真实原始 FU。

配置位置：Form Design → 目标任务表单 → 目标 Sub Table → Batch Form。
选择 Lookup 控件，把 Field 绑定到设计器中实际的 assignee 字段，配置用户来源；email 使用实际目标字段，并配置该 Lookup 的 change。
普通 Form 与 Batch Form 的脚本分别配置，不自动继承；Batch 在 Apply 时对每个勾选行执行，统一输入区不会即时预演各行结果。

## 最终验证命令与限制

- `mvn -pl backend/user-portal -am package -Dnet.bytebuddy.experimental=true -Dtest=*SubTable*,*MiCollection*,*TaskApproval*,*ProcessStart*,*AssignmentCompletion*,!SubTableBatchPostgresTransactionIT -Dsurefire.failIfNoSpecifiedTests=false`
  → Portal 汇总 630 项，0 失败 / 0 错误 / 20 项条件跳过，BUILD SUCCESS；即 610 项实际通过。
  本机 Java 21 的旧 Byte Buddy 测试依赖需要 experimental 兼容参数；未修改仓库依赖、生产 JVM 参数或测试断言。
- Portal 新事件及相关组件专项 26 项通过；新增测试随后补齐 mode 的完整类型输入，全量 typecheck 无新增诊断。
- `pnpm exec vitest run` → 2602 通过 / 1 失败（345 文件）。剩余仍为既有 `viewDetailForm.test.ts` 的“未配显示列留空”用例。
- `pnpm exec vue-tsc --noEmit` → 381 条，与提交前记录 381 条按文件 / 错误码比较没有新增。全量 typecheck 仍失败。
- Portal `pnpm run build` 成功，Portal 前后端 Compose 服务已重建；已核对运行容器 guard class 与最终打包 class 的哈希一致。
- 最终 `pnpm run regression:mi` → 250 项单测及 6 个真实浏览器场景全部通过。使用合法缓存会话和固定的既有 MI 样本，未改产品鉴权或 API 响应。
- `pwsh -NoProfile -File scripts/verify-batch-postgres.ps1`：31 项中 29 通过 / 2 失败。
  两项均为 `unmarkedInvalidValueCannotBypassMetadataOrIncrementEntityVersion` 的两个主键样本：预期拒绝普通未标记 caption 数值 12，实际没有抛错。
  已用 `git archive efb1453b0` 的独立后端副本运行同一脚本，得到相同的 31 项 / 2 失败；这是提交前已存在的独立元数据校验问题，本次未弱化断言或改写该路径。
  两轮均使用独立临时 PostgreSQL，运行结束后验证没有该标签的遗留容器；未对平台库执行 IT schema 创建或删除。

## Code review 结果

### 结论

本次增量审查未发现剩余 Blocker / Major；完整仓库门禁保留 CONDITIONAL。

### Findings 与处置

- Major：显式双 FK 记录被多个 scope 合法申报时，逐 scope 外键判断误拒绝。
  已改为每条 claim 先独立校验，再要求单个完整授权 owner；非法权限组合与错误父上下文均有拒绝断言和真实请求证据。
- Major：全局 scopes 非空导致普通单绑定父表无法采用唯一发布 binding；把未申报的单绑定表也纳入新检查会改变兼容行为。
  已改为按表判断，scope 外的表保留原校验边界；增加混合父子表测试，最终显式双外键页面通过。
- Major：嵌套主键 patch 未传回宿主，重复分配主键。
  已补全事件链，组件红→绿与真实两交易 / 三消息持久化断言通过。
- 新增测试装配问题（mock 返回同一可变 Map、版本断言读取旧引用、Vue 测试漏 mode）均已纠正；没有通过削弱业务断言、跳过新增测试或修改产品权限来放行。
- 最终重审源码 / 测试 / 暂存内容：配置判据来自发布 metadata；没有 MCY / HMDC、FU ID 或业务列名分支；没有新增数据库 schema、API transport 字段、配置序列化字段或额外依赖。

### 影响链与通用性

- Portal 发起、scope guard 的保存 / 完成 / Process Form 共用写入边界，以及已有批量实例的 Complete hydration。
- Portal 嵌套 Add/Edit 对话框将主表身份 patch 传回现有宿主处理器。
- Save/Load、真实 Clone 后发布及两种 FU 配置已验证。
- Export / Import / Rollback、Admin 两条导入消费者没有新增字段或持久化格式变更，本次未修改；其完整生命周期未重新穷举。
- 所有权限、行版本、父上下文验证仍执行；没有逐行 DB 查询或网络请求。

### 残余风险 / 未验证项

- 上述全量前端测试、TypeScript 与 PostgreSQL IT 基线失败保留，不宣称仓库全绿。
- 未对远程公司 Dev、原 Credit Card 的实际发布版本、全部 FK 配置组合、独立第二账号完整办理及全部生命周期进行穷举验收。
- 新旧 / 重命名 / 复合键和非法跨父上下文有测试；不能代替任意生产配置的验收。

## 截图

截图保留在本地忽略目录，远程 PR 需另附图片。

- 复现报错：`frontend/user-portal/verification-screenshots/2026-11-01_dual-submit-result.png`
- 草稿重开及独立 Case 子表：`frontend/user-portal/verification-screenshots/2026-11-01_dual-normal-evidence-before-submit.png`
- 交易 A 自己的 Correspondence：`frontend/user-portal/verification-screenshots/2026-11-01_dual-normal-evidence-transaction-a.png`
- 显式额外 FK 提交：`frontend/user-portal/verification-screenshots/2026-11-01_dual-explicit-verified-submit-result.png`
- 下一步 Investigation 已绿：`frontend/user-portal/verification-screenshots/2026-11-01_final-investigation-green.png`
- 全部交易走过后的 My Request：`frontend/user-portal/verification-screenshots/2026-11-01_investigation-all-passed-request.png`
- Batch 已配置 Lookup：`frontend/developer-workstation/verification-screenshots/2026-11-01_batch-assignee-config.png`
- Batch 持久化回填：`frontend/user-portal/verification-screenshots/2026-11-01_final-batch-email.png`


## 提交前终审

- 最后针对重叠归属与 Complete 基线的 6 项后端测试通过，新增主键事件组件测试 1 项通过；测试辅助方法的多余参数与可消除的泛型警告已清理。
- 18 个暂存文件的新增行敏感信息扫描：0 个候选；运行时代码的 MCY / HMDC / FU ID / 具体业务列名判据扫描：0 个候选。
- 暂存内容不含合法会话、Cookie、日志、截图、独立基线目录或临时探针。diff 空白检查通过。
- 按用户此前“没有新增问题即可 commit”的明确授权提交本轮修复；完整门禁的 CONDITIONAL 与基线失败如上保留。仅本地提交，不推送远程。
