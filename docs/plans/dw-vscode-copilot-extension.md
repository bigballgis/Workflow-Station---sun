# 设计 Plan：DW VS Code 扩展（IT Developer 用 Copilot 开发 FU / Automation / Superset）

> 状态：设计已确认（2026-10-30），待进入 playbook 任务整理。
> 本文只定「做什么」；「怎么做」在每期开工前按 skill `implementation-autonomy` 另出技术方案。

## 【背景 / 问题】

- IT Developer 有 GitHub Copilot 额度，业务用户没有。
- 业务用户继续用 DW 内的 AI Studio / AI Generate（模型走 DW 自己的 DeepSeek / CO 通道，成本归平台）。
- IT Developer 目前只能在浏览器里用 DW 设计器，无法用 Copilot 驱动开发；DW 也没有面向工具的入口（无 PAT、无 MCP、无 dry-run 端点）。
- Copilot 额度是按人头的许可，**不能**经服务端转给业务用户使用——两个人群的 AI 入口天然分开。

## 【目标】

**两个入口、一条写入通道**：不管设计由谁写（AI Studio 的模型 / IT 的 Copilot），都经 DW 同一套「校验 → 写入 → 撤销 → 版本」落库；模型可换，治理不变。

```
业务用户 ── AI Studio (DeepSeek/CO) ───────────┐
                                               ├─► DW apply（校验 / 写入 / 撤销 / 版本）─► 导出 ─► Admin Center 导入 ─► 生产
IT 开发 ── VS Code 扩展 + Copilot agent 模式 ──┘
```

IT Developer 在 VS Code 里能用 Copilot：

1. 创建 / 修改 FU；
2. 创建 FU + Automation（service task 调 flow）；
3. 创建 Superset 数据集 / 图表 / 看板；
4. 产出三份发布物 + 按顺序排好的导入清单，交给现有生产导入通道。

## 【非目标】（本阶段不做）

- **不做 MCP 层**：公司 Copilot 策略禁用 MCP；其他 IDE（JetBrains / Cursor / Claude）以后有需要再补。
- **不处理并发修改**：一个 FU 只归一方（业务用户或 IT），v1 不做乐观锁 / 接手。
- **不做统一发布包**：不推翻 FR-B12（FU zip 刻意不带 flow，见 `FunctionUnitExporterAutomationFlowTest`）；Admin Center 不新增 Superset 资产导入。
- **扩展不连生产**：只连 dev（DW 本身只在 dev），生产仍走 Admin Center 导入。
- **不改业务用户侧**：AI Studio / AI Generate 行为不变。
- **不在扩展里放业务规则**：校验全部在服务端，扩展只是客户端。
- **不用 git 做设计真源**：DW dev 库是工作真源，zip / flow 文件 / Superset 资产 zip 是发布物。

## 【模块】

dw（后端）｜admin（Superset 鉴权代理，视 P3 选型）｜新增 `tools/vscode-extension`（位置 [待确认]）｜deploy（SSO 回调地址、Kong 路由视需要）

## 【方案】

IT 侧入口三选一已比选并拍板：

- **方案 A：VS Code 扩展（选定）** —— 扩展负责 SSO 登录、用 `vscode.lm.registerTool` 注册 DW 工具供 Copilot agent 模式调用、提供 `@dw` 对话入口和可视化（树 / diff 预览 / 环境状态栏）。核心逻辑在 DW API。
- 方案 B：DW MCP Server —— 被公司禁用 MCP 策略否决。
- 方案 C：仅指令文档 + 直调 REST —— 只作为 P0 可行性验证手段，不作为产品形态（token 手贴、脆弱）。
- 已否决：GitHub App 形式的 Copilot Extension —— GitHub 已于 2025-11 停用，官方迁往 MCP（落地前再核一次公告）。

**推荐 A 的理由**：解决工具鉴权（SSO + SecretStorage，开发者不碰凭证）、绕开 MCP 禁令、apply 前可视化 diff、写入复用 AI Studio 同一 apply 链路（零第二套规则）。

### 生产交付：三通道各自走（已拍板）

| 目标 | 发布物 | 进生产的通道（现有） |
|---|---|---|
| FU | FU zip | DW 导出 → Admin Center FU 导入 |
| Automation | flow 文件 | Admin Center `POST /automation/flows/import`（`AutomationFlowController`） |
| Superset | Superset 原生资产 zip | 导入生产 Superset → Admin Center `POST /bi/dashboards/sync` 登记（`BiDashboardRegistryController`，只登记已存在的看板，不导资产） |

导入顺序：**flow → FU → Superset 资产 → Admin Center sync**（BPMN 用 `ap:flowKey` 引用 flow，flow 须先存在）。

## 【扩展模块拆分】

| # | 模块 | 内容 |
|---|---|---|
| ① | 登录 | 打开平台统一登录页（client=`developer-workstation`，redirect_uri=扩展回调）→ 用户照常登录（含 DSP 免密）→ 扩展拿 code 调 DW `POST /api/v1/auth/sso/exchange` 换 JWT（默认 24h）→ `POST /auth/refresh` 续期（refresh 默认 7d）→ token 存 VS Code SecretStorage。审计记到本人，权限沿用 `@RequireDeveloperPermission` |
| ② | 工具（`registerTool`） | 见下表 |
| ③ | 知识 | `generatedData` 格式说明、各 scope 约束、已知坑，**运行时从 DW 拉取**（候选：复用 `GET /ai-generation/prompt-templates`），扩展不内置，DW 升级不需扩展发版 |
| ④ | 界面 | 侧边栏树（FU / flow / 看板）、apply 前 diff 视图（基于 dry-run）、状态栏环境、一键在 DW 网页打开 |
| ⑤ | `@dw` 对话入口 | 用 `vscode.lm` 调开发者本人的 Copilot 模型（消耗 IT 额度）；P0 若 `registerTool` 被禁，这是退路 |

**工具清单**

| 目标 | 工具 | 后端 |
|---|---|---|
| FU | 列工作区 / FU、读设计、**dry-run**、apply、undo、导出 zip | 现有 `POST /api/v1/ai-generation/studio-chat/apply`（`AiStudioApplyRequest{functionUnitId, scope, generatedData}`）、`/undo`、`GET /export-import/function-units/{id}/export`；dry-run **新增** |
| Automation | 列 piece / connection、建或改 flow、**写完回读核对**、绑定 service task、导出 flow 文件 | 个人 AP token：与 DW 内嵌 builder 同路径（admin-center `GET /internal/ap/token`，`ServiceTaskTokenController`；能否经 Kong 给扩展用 [待确认]）→ Kong `/api/ap/*`；绑定走 DW `SERVICE_TASK_BINDINGS` scope |
| Superset | 列数据库 / 数据集、建 SQL 数据集、建图表 / 看板、导出资产 zip | Superset 原生 REST API；鉴权 [待确认]（P3 首项） |
| 发布 | 生成三份发布物 + 导入清单 | 扩展本地 |

## 【影响面】

| 层级 | 变更 |
|------|------|
| API / Component（dw） | 新增 dry-run 端点：包装现有 `AiStudioProposalPreviewer.preview(functionUnitId, scope, AiGeneratedData)`（已 `@Transactional(readOnly = true)`），入参与 `AiStudioApplyRequest` 同款，经与 Apply 相同的 convert + normalize；权限 `FUNCTION_UNIT_VIEW` |
| API / Component（dw） | 格式说明下发：先评估 prompt-templates 是否够用；不够再新增只读端点 [待确认] |
| API（admin） | P3 视选型：Superset 代理端点（带用户身份）或不改 |
| Entity / SQL | 无（不新增表、不动 `deploy/init-scripts/`） |
| 前端 types / views | 三个 Vue 前端不改 |
| i18n | dry-run 返回的校验消息复用现有 key；如有新增 key 则三语同步 |
| 部署 / 配置 | admin-center `platform.sso.clients.developer-workstation.redirect-uri-prefixes` 增加扩展回调前缀（固定路径，如 `vscode://workflow-station.dw-copilot/sso/callback`），各环境 configmap 同步；Kong 是否需为 `/internal/ap/token` 开放给扩展 [待确认] |
| 新增工程 | VS Code 扩展（TypeScript），私有 VSIX 分发 |

## 【数据与契约】

- 扩展 ↔ DW 的写入契约 = AI Studio 现有 `generatedData` + `scope`，**只增不改**；dry-run 的返回结构 = `AiStudioProposalPreview`（新增 / 更新 / 替换 + ERROR/WARN），与 AI Studio 提案卡同源。
- 扩展启动时读 DW 的格式说明版本号，不匹配则提示升级（P4）。
- 审计：所有写入带开发者本人 JWT，与网页操作同口径。

## 【分期】

原则：先验证可行性，再按「确定性从高到低」排序；每期验收都必须走到 UAT 导入成功。

- **P0 · 可行性验证（关卡）**
  1. 公司 Copilot 禁 MCP 后，扩展 `registerTool` 注册的工具能否被 agent 模式调用？不行 → 退回 `@dw` 入口自执行工具。
  2. IdP 能否登记扩展回调地址？不行 → 退回「DW 网页生成个人 token，粘贴进扩展」（需另做 PAT，届时补方案）。
  3. dev 里手动拿 JWT，让 Copilot 只靠格式说明写 `generatedData` 调 `/apply`，能否建出简单 FU？不行 → 先补格式说明。
- **P1 · FU（MVP）**：dry-run 端点、格式说明下发；扩展登录续期、FU 工具、知识拉取、树、diff 预览。
- **P2 · FU + Automation**：AP token 握手、flow 建改 + 回读核对、service task 绑定（强制 inputMapping / outputMapping）、flow 导出、导入清单。
- **P3 · Superset**：先定鉴权，再做数据集 / 图表 / 看板 / 资产导出，清单加 sync。鉴权调研可与 P1 并行。
- **P4 · 分发与收尾**：私有 VSIX / 内部市场、版本兼容检查、IT 使用手册。

## 【风险与回滚】

| 风险 | 影响 | 应对 |
|---|---|---|
| `registerTool` 也被公司策略禁用 | Copilot 无法自动调工具 | P0 先测；退路 `@dw` 入口 |
| IdP 不给登记回调地址 | 无法 SSO 登录 | 退路个人 token（需另起方案） |
| Copilot 写的 `generatedData` 质量不稳 | apply 频繁失败 | dry-run 先拦；格式说明从 DW 下发、可迭代 |
| 写错后撤不回 | `/undo` 只覆盖 7 个 scope（EMAIL_TEMPLATES / CONNECTIONS / EMAIL_MONITORS / VIEWS / SERVICE_TASK_BINDINGS / PROCESS / TABLE_RELATIONS），**TABLES / FORMS / ACTIONS / ALL 不可撤**；撤销记录在内存 `ConcurrentHashMap`，DW 重启即失 | 扩展的安全网改为 DW 版本快照：apply 前先存一版（P1 技术方案确认快照 API），出错走版本回滚；`/undo` 只作锦上添花 |
| AP 往已有 flow 中间插步骤被静默丢弃（三个调用全 200） | flow 看似成功实则缺步 | 每次写后回读 flow 链核对，不一致即报错 |
| service task 不配 mapping 时发空 body 且全链路全绿 | 运行时数据丢失 | 绑定工具强制要求 inputMapping / outputMapping |
| Superset 作者鉴权没有现成程序化通道 | P3 卡住 | P3 首项定方案，可与 P1 并行调研 |
| VS Code Chat / LM API 变动 | 扩展需跟版本维护 | 业务逻辑不进扩展，扩展只是薄客户端 |

回滚：服务端只新增只读 dry-run（及格式说明端点），不改现有写路径；扩展停用即恢复原状。写入出错走 DW 版本回滚（见上表「写错后撤不回」）。

## 【验收】

- **P0**：三个问题都有明确结论并记录；任何一项走退路时更新本 Plan。
- **P1**
  - 正例：IT 在 VS Code 根据一段需求从零建出含表、表单、流程、视图的 FU，DW 设计器可正常打开；导出 zip 在 UAT Admin Center 导入成功，portal 能发起一单；审计记录为开发者本人。
  - 反例：故意写错一处（如表单绑定不存在的字段）→ dry-run 返回 ERROR、扩展禁止 apply。
- **P2**
  - 正例：FU 的 service task 调 flow，dev 跑一单，`wf_ap_execution_record.input_data / output_data` 均非空；UAT 按清单先 flow 后 FU 导入，跑一单成功。
  - 反例：往已有 flow 中间插步骤 → 回读核对发现缺步并报错；service task 不配 mapping → 绑定工具拒绝。
- **P3**
  - 正例：VS Code 建看板 → 导入 UAT Superset → Admin Center sync 可见 → 按 BI RBAC 分配后对应 portal 用户可见。
  - 反例：未登记 / 未分配的看板对 portal 用户不可见。
- **P4**：未参与开发的 IT 同事仅凭手册完成安装、登录与 P1 场景。
- taskId / applicationId / FU：[待确认]，P1 开工时指定 dev 夹具工作区。

## 【验证】（实现后最低命令）

- DW 后端：`mvn -pl backend/developer-workstation -am clean package`（JDK17）+ dry-run 端点单测（正例 / 反例各一）。
- 扩展：`npm run compile` + 扩展单测；在 VS Code Extension Host 手动跑 P1 验收场景。
- dev 端到端：重启 DW 容器后，用扩展跑验收场景；写操作后回读真实状态（DW 设计器 / flow 链 / `wf_ap_execution_record`）。
- UAT：按导入清单导入并发起一单。

## 【P0 结论】（2026-10-30，本机 dev）

| 问题 | 结论 | 证据 |
|---|---|---|
| ① `registerTool` 在公司禁 MCP 策略下能否被 agent 模式调用 | **机制可行，公司策略待实测** | 本机 VS Code 1.133 冒烟：扩展激活、4 个工具全部出现在 `vscode.lm.tools`、`invokeTool` 通路可达。公司 Copilot 账号的实测步骤见 `tools/dw-vscode-spike/README.md` |
| ② SSO 回调地址能否登记 | **能，且在我们自己手里** | 登录页是平台自己的统一登录（`frontend/login`），回调白名单是 admin-center 按 client 的前缀配置（`PlatformSsoService.validateRedirectUri`，`startsWith` 匹配）；登录页用 `window.location.href = redirectUri` 跳转（`useLogin.ts`），自定义 scheme 原理上可用。DSP 免密发生在登录页内部，不受影响。端到端实走放 P1 |
| ③ 工具能否在浏览器外完成「写设计」 | **能** | curl 经 Kong：`/auth/login` 拿 JWT → 建 FU 50150（须带 `virtualGroupIds`，否则 `WORKSPACE_FORBIDDEN`）→ `studio-chat/apply` 反例 `dataType=MONEY` 被拒（`INVALID_ENUM` + `fieldPath`，模型可据此自修）→ 正例写入 → `GET /function-units/50150/tables` 回读一致 |

P0 附带发现：

- **格式说明基本够用**：`GET /ai-generation/prompt-templates/GENERATION`（约 22k 字符）已含完整 `generatedData` 字段规范；但它引用的 `schemaMetadata`（枚举权威表）是 `AiGenerationServiceImpl.buildSchemaMetadata()` 在服务端注入的，**没有对外端点**。P1 的「格式说明下发」= 模板 + schemaMetadata 一并返回。
- **undo 覆盖不全**：见【风险与回滚】「写错后撤不回」，安全网改为版本快照。
- **建 FU 必须指定开发组**：扩展建 FU 工具要让用户选组（`GET /function-units/my-dev-groups`）。

## 【待确认】

1. 扩展工程放在本仓库哪个目录（P0 spike 暂放 `tools/dw-vscode-spike`），还是独立仓库？
2. ~~IdP 回调地址登记~~ → P0 已确认是平台自有配置；剩余：扩展回调用 `vscode://` 还是固定端口 loopback（P1 技术方案定）。
3. admin-center `/internal/ap/token` 能否经 Kong 给扩展调用，还是需要扩展专用的 token 端点？
4. ~~prompt-templates 够不够~~ → 基本够，需补 schemaMetadata 下发。
5. Superset 鉴权：admin-center 代理带用户身份，还是 Superset 自身 API token？
6. 扩展的发布者名（publisher）与分发渠道（私有 VSIX / 内部市场）。
7. 公司 Copilot 账号实测问题 ①（按 spike README 操作）。

---
请确认以上设计 Plan。确认后若要开始实现，请回复 **按 playbook 执行**（或补充 / 修正项）。
