# AI Studio ↔ Content Organizer（CO）Agent 输出契约

> 状态：草案（2026-11-02）。AI Studio 尚未接入 CO；本文定义接入时 CO 的 Agent 必须产出什么，
> 让 DW 现有的解析、预览、Apply、撤销链路**原样复用**，不为 CO 另写一套。

## 0. 真源与适用范围

契约的真源是 DW 代码，本文只是导读和约束汇总。两边不一致时以代码为准：

| 内容 | 真源 |
|------|------|
| 每个切片的 JSON 字段、枚举、交叉约束 | `backend/developer-workstation/src/main/resources/ai-prompts/generation.txt`（第 29–262 行）+ `AiGenerationServiceImpl#getSchemaMetadata` |
| 当前环境实际生效的模板 + 元数据 | `GET /ai-generation/studio-chat/format-spec`（DB 里可覆盖模板，**配置 CO 时以它的返回为准**） |
| 阶段 → scope → 允许切片 | `AiStudioPhase`、`AiStudioChatServiceImpl#SCOPE_SLICES` / `ONE_CLICK_SLICES` |
| 输出信封解析 | `AiResponseParser`（`GENERATED_DATA_PATTERN`、`PHASE_COMPLETE_MARKER`） |
| 写入语义（替换 / upsert / 补丁） | `AiWriteServiceImpl#clearScopedData`、`AiEmailProposalWriter`、`AiViewProposalWriter` |
| Apply 前的预校验 | `AiStudioProposalPreviewer`、`AiStudioProposalReferenceValidator`、`AiStructureValidator` |

## 1. 三种调用形态

| 形态 | 触发 | CO 产出 | DW 落点 |
|------|------|---------|---------|
| ADVISORY | 阶段内普通对话 | 只有文本，**不带数据块** | 对话气泡 |
| PROPOSE | 某个阶段点 "Propose change" | 文本 + **该阶段 scope 的切片** | 提案卡 → 预览 → Apply |
| ONE_CLICK | Build with AI 一键生成 | 文本 + **6 个核心切片全量** | 作业完成后自动 Apply |

`VALIDATION` 阶段没有 scope，只支持 ADVISORY。

## 2. 输出信封（硬约束）

CO 主管的**最终输出**必须是下面的形状。DW 用 `AiResponseParser` 解析，不需要改代码：

```text
<给用户看的说明文字，用用户的语言>

---GENERATED_DATA_START---
{ ...严格 JSON，只含本次 scope 允许的切片... }
---GENERATED_DATA_END---
```

- 解析顺序：先找标记块，找不到再找 ```` ```json ```` 围栏，再退到首尾大括号。**请始终用标记块**，后两种只是兜底。
- 说明文字 = 整段输出去掉标记块之后剩下的部分，原样显示在提案卡上。主管现有的「需求摘要 / 调用了哪些 Agent / 结论 / 假设与人工步骤」写在这里。
- JSON 必须严格：不能有注释、尾逗号、省略号、占位符，XML 不能缩写或 Base64。
- ADVISORY 形态不输出标记块。PROPOSE 形态如果判断无法产出合法数据（例如缺少依赖），也**不输出**标记块，在说明文字里讲清缺什么。DW 对「既没数据也没文字」的结果会显式报错（`AI_STUDIO_PROPOSAL_EMPTY`）。
- `---PHASE_COMPLETE---` 可加可不加，AI Studio 不依赖它。

## 3. 阶段 → scope → 切片 → 写入语义

| AI Studio 阶段 | scope | 允许的切片 key | 写入语义 |
|----------------|-------|----------------|----------|
| PROCESS_DESIGN | `PROCESS` | `processDefinition` | **整体替换** |
| TABLE_DESIGN | `TABLES` | `tableDefinitions`、`tableRelations` | **整体替换**（清空整个表图谱后重写） |
| FORM_DESIGN | `FORMS` | `formDefinitions` | **整体替换** |
| VIEW_DESIGN | `VIEWS` | `mainTableViews` | upsert，键 = `mainTableName` + `viewName` |
| ACTION_DESIGN | `ACTIONS` | `actionDefinitions` | **整体替换** |
| AUTOMATION | `SERVICE_TASK_BINDINGS` | `serviceTaskBindings` | 对**现有** BPMN 定点打补丁 |
| CONNECTIONS | `CONNECTIONS` | `emailConnections` | upsert，键 = `name` |
| EMAIL_TEMPLATES | `EMAIL_TEMPLATES` | `emailTemplates` | upsert，键 = `name` |
| EMAIL_MONITORS | `EMAIL_MONITORS` | `emailMonitorRules` | upsert，键 = `name` |
| DECISION_DESIGN | `DECISIONS` | `decisionDefinitions` | **整体替换** |
| VALIDATION | — | 无 | 只出文字 |
| 一键生成 | `ALL` | `processDefinition`、`tableDefinitions`、`tableRelations`、`formDefinitions`、`actionDefinitions`、`decisionDefinitions` | 全量替换 |

由此得出四条 CO 必须遵守的规则：

1. **整体替换型 scope 必须输出完整切片**：包括没改动的条目，名称和 ID 原样保留。漏掉一条，就等于删掉这一条。
2. **upsert 型 scope 只列新增和修改的条目**：没列出的保持不变；这条路径删不掉任何东西。
3. **一次 PROPOSE 只能有一个 scope 的数据**：多出来的切片会被 DW 静默裁掉（提案返回前和 Apply 前各裁一次）。改动波及其他阶段时（例如改表之后表单要跟着改），只能在说明文字里列为「下一步去 X 阶段」，**不能**把别的切片塞进数据块。
4. **一键生成不含视图、邮件三阶段和 service task 绑定**：连接需要用户手工补凭证，监控依赖连接，绑定依赖已发布的 Automation flow，带上它们整份结果过不了引用校验。CO 主管里「完整 FU 按依赖顺序调用」的清单，在 ONE_CLICK 形态下要收窄到上面 6 个切片。

不论什么形态，数据块里都不要放 `name`、`description`（FU 级别）和 `icon`。

## 4. CO 需要拿到的输入

CO 的 12 个 Agent 目前都没有工具，所以 DW 调用 CO 时必须把以下内容一次带上。现在 `AiGenerationService#callAiModel` 走网关时，带的也是这一套：

| 输入 | 内容 | 为什么必须带 |
|------|------|--------------|
| `mode` / `phase` / `scope` | 见第 1、3 节 | 决定只产哪个切片、用哪种写入语义 |
| 设计上下文 | `FunctionUnitContextDTO` 全量：`GET /ai-generation/studio-chat/design-context?functionUnitId=` | 替换型 scope 需要现有完整数据才能「原样保留」。**只有这里有** `orgCatalog`（视图授权）、`serviceTasks` / `automationFlows`（绑定）、连接的 `hasCredentials`（监控） |
| `schemaMetadata` | `format-spec` 返回的元数据 | 枚举以它为准（例如 `actionTypes`） |
| 文档 | 该 FU 的 REQUIREMENTS / DESIGN 最新版 | 业务意图 |
| 对话转写 | 历史消息；助手消息附带上一轮提案 JSON（已截断） | 支持「把刚才那个再改一下」 |

另一种做法是给 CO 配**只读**工具，由 CO 自己取数和自检。可以直接复用 DW 已有的、给 VS Code 扩展用的接口：

- `GET /ai-generation/studio-chat/design-context`：取设计上下文
- `GET /ai-generation/studio-chat/format-spec`：取格式模板和元数据
- `POST /ai-generation/studio-chat/dry-run`：传入 `{functionUnitId, scope, generatedData}`，返回和 Apply 完全同一套的预览与问题（`issues[].severity`）

`/apply` 和 `/undo` **不给** CO，写入一律由用户在 AI Studio 里点 Apply。

## 5. DW 侧兜底校验（CO 替代不了）

- 结构校验 + 自动修复重试：`callAiModel` 里的 `AiStructureValidator` 检查字段名、枚举、类型、引用完整性。
- 提案卡预校验：`AiStudioProposalPreviewer` 给出新增 / 更新 / 替换清单，以及 `REFERENCE_NOT_FOUND` 等问题。有 ERROR 时禁用 Apply。

所以在 PROPOSE / ONE_CLICK 形态下，CO 的 `validation_agent` 只是自查，权威结论来自 DW 的 dry-run。两种取舍：给 CO 接上 dry-run 工具，让修正循环跑在真实校验上；或者在这两种形态下跳过 validation_agent，省掉一轮延迟。

## 6. CO 现有 Instruction 与契约的冲突（按 Agent 列）

基于 2026-11-02 读取的 CO dev 配置（`content-organizer-dev.hk.hsbc` → Use case management → `dw_function_unit_orchestrator`）。
按这些冲突改好的 12 段 Instruction，可直接粘贴的版本见 [developer-workstation-ai-studio-co-agent-instructions.md](./developer-workstation-ai-studio-co-agent-instructions.md)。

**dw_function_unit_orchestrator（主管）**
- 补上第 1 节的形态判断，以及第 2 节的输出信封；「Final response must include」那 5 节改为写进说明文字。
- 「targeted change 时调用受影响的 Agent 及其依赖」→ 依赖方只能在说明文字里写成后续步骤（第 3 节规则 3）。
- 完整 FU 的调用顺序在 ONE_CLICK 下收窄到 6 个切片；`decisions` 要排在 `actions` 之前（见下面 decision 条）。
- 语言规则不用改：DW 的标识符和展示名一律英文，说明文字跟随用户语言。

**process_design_agent**：只输出 `processDefinition.bpmnXml`。必须满足 generation.txt 第 239–262 行，尤其是：
- `assigneeType` 以 `custom:values` 的形式写在扩展属性里；
- 审批网关条件只能是 `${decision == 'yes'}` / `${decision == 'no'}`；
- 完整的 BPMN DI；
- 多实例（MI）子流程的固定写法。

**table_design_agent**
- `length`、`precision`、`scale`、`defaultValue` 必须是 JSON **字符串**；VARCHAR 的 length 要大于 0，DECIMAL 的 precision 和 scale 要大于 0。
- 输出 `tableDefinitions` + `tableRelations` 的**完整**切片。

**form_design_agent**
- `formType` 取值是 `PROCESS|TASK|ACTION`，现有 Instruction 没提 ACTION 表单，需要补上（`FORM_POPUP` 类动作依赖它）。
- 每个表单恰好一个 PRIMARY 绑定；SUB 绑定要写 `foreignKeyField`；多实例子表要以 SUB 绑定挂在对应的 TASK 表单上。
- 「每个 userTask 恰好一个 TASK 表单」与 DW 生成规则（generation.txt 第 199 行）一致，保留。

**view_design_agent**
- `accessRules` 要么为空（只有系统管理员可见），要么至少一个 BUSINESS_UNIT 加至少一个 ROLE；`targetId` 从 `orgCatalog` **原样复制**，ROLE 必须属于所选 BU。
- 系统字段只在 MAIN 表视图上可用；不能输出 `id`、`mainTableId`、`detailFormId`、`isDefault`、`status`。
- 「平台没有结构化格式就给文字规格」这句删掉：VIEWS 有格式。

**action_design_agent**
- 有 userTask 就必须恰好一个 `PROCESS_SUBMIT`，绑定在第一个 userTask 上；`stageIds` 不能为空。
- ROLLBACK 写 `configJson.targetStep`；`actionType` 以 `schemaMetadata.actionTypes` 为准。

**automation_agent**（冲突最大）
- DW 的 AUTOMATION 切片只有 `{serviceTaskId, flowKey}`：两个值都从设计上下文原样复制。
- 明确**禁止**输出 `inputMapping`、`outputMapping`、`timeoutSeconds`、`retryCount`、`flowId`、`webhookUrl`。
- 现有 Instruction 要求定义「input/output mapping、retry、timeout」，与此正面冲突。这些内容只能作为人工配置步骤写进说明文字。
- 没有合适的 service task 或 flow 时，不输出数据块。

**connection_agent**（冲突）
- DW 的连接只有**邮件连接**：`name` 就是发件邮箱，`connectionType` 取 `GMAIL|OUTLOOK|YAHOO|QQ|NETEASE_163|SMTP`，`direction` 只能是 `OUTBOUND|INBOUND`（没有 BOTH）。
- 不能出现 host、port、TLS、用户名、凭证。
- 现有 Instruction 按「通用外部连接」写（target system、auth method、endpoint、permitted consumers），需要改成邮件连接。

**email_template_agent**
- 切片只有 `{name, subject, bodyHtml, enabled}`；变量只能用 MAIN 表字段的 `${fieldName}`，禁止子表或 lookup 类 token。
- 收件人规则、多语言、附件、fallback 等不在格式里，只能写进说明文字。

**email_monitor_agent**（冲突）
- DW 的监控规则只是**模板**：禁止输出 `startEventId`、`filterFrom`、`filterSubject`、`processDefinitionKey`。启动事件的绑定在 Process Design 里配。
- 现有 Instruction 要求「exact process start-event binding」和「sender/recipient filters」，与此冲突。
- `connectionName` 必须是已存在、INBOUND、已启用、`hasCredentials=true` 的连接；不存在时不输出数据块。

**decision_design_agent**
- 切片只有 `{decisionKey, decisionName, dmnXml, hitPolicy, description}`，**没有**「谁调用这个决策」的字段：引用关系在 BPMN 的 DMN service task 和 DECISION_TABLE 动作的 `configJson` 里。
- 所以 Instruction 里「define the exact service task / DECISION_TABLE action that invokes each decision」只能写进说明文字。
- 这也是 decisions 要排在 actions 之前的原因：先有 `decisionKey`，动作才能引用它。

**validation_agent**：不产切片，见第 5 节。

## 7. 验收方式（不改 DW 代码就能做）

对每个阶段，把 CO 的输出切出数据块，调用：

```http
POST /ai-generation/studio-chat/dry-run
{ "functionUnitId": <测试 FU>, "scope": "<第 3 节的 scope>", "generatedData": { ...数据块... } }
```

通过标准：

- `issues` 里没有 ERROR；
- 替换型 scope 的 `replacements` 数量符合预期（没有误删存量条目）；
- upsert 型 scope 的 `items` 只包含本次新增或修改的条目。

dry-run 和 Apply 走同一套裁剪、归一化和校验，dry-run 通过就代表 Apply 会通过。
