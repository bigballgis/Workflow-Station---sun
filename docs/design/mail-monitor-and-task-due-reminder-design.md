# 邮箱监控入子表 & 任务即将过期通知 — 技术方案

> 状态：**方案评审中（未实现）**
> 决策基线：M365/Exchange（Microsoft Graph）· 写入 FU 流程内子表（`__subTables__`）· 首版轮询后续升级实时 · 优先复用 bpmn-js + Flowable
> 关联记忆：[[mi-people-subtable-id-churn]]、[[mi-overlay-status-cache-invalidation]]

## 1. 需求

| # | 需求 | 核心难点 |
|---|---|---|
| **A** | 监控某邮箱，收到邮件时把邮件内容提取到某个 Function Unit 的**子表**中（实时优先） | 邮箱实时监听 + 邮件→子表行字段映射 + 关联/触发流程 |
| **B** | 任务**即将过期**时通知用户 | 定时扫描 dueDate + 复用已有通知/邮件渠道 |

## 2. 现状盘点（与需求相关的既有能力）

| 能力 | 现状 | 接入点 |
|---|---|---|
| Flowable 引擎 | ✅ 7.0.0 已集成 | `workflow-engine-core` |
| 消息事件触发流程 | ✅ `ProcessEventManager.triggerEvent()`（封装 `messageEventReceived`/`signalEventReceived`） | `backend/workflow-engine-core/src/main/java/com/workflow/component/ProcessEventManager.java:47` |
| Relation Table 行写入 | ✅ `RelationTableDataService.addData(tableId, Map)`（写 `rt_table_data_rows`） | `backend/admin-center/src/main/java/com/admin/service/RelationTableDataService.java:30` |
| 流程内子表写入 | 走流程变量 `__subTables__`，需 `RuntimeService.setVariable()` | （JSON-row 规则，无物理表） |
| 表/流程定义元数据 | ✅ `TableDefinitionRepository` / `ProcessDefinitionRepository` | `developer-workstation/repository` |
| 即将到期任务查询 | ✅ **已存在** `findTasksDueSoon(now, alertTime)` | `backend/workflow-engine-core/src/main/java/com/workflow/repository/ExtendedTaskInfoRepository.java:152` |
| 逾期任务查询 | ✅ `findOverdueTasks(now)` | 同上 |
| 定时任务机制 | ✅ Spring `@Scheduled`（已有 8 处先例，如 RetryAndCompensationComponent） | `workflow-engine-core/component/*` |
| 任务到期字段 | ✅ `ExtendedTaskInfo.dueDate` + `isOverdue()` | `backend/workflow-engine-core/src/main/java/com/workflow/entity/ExtendedTaskInfo.java:152,268` |
| 通知（in-app + WebSocket + Kafka） | ✅ `NotificationDispatchHelper.publishToUserAfterCommit()` + `up_notification` + STOMP | `platform-messaging` / `user-portal` |
| 通知模板（多语言） | ✅ 已有 `TASK_OVERDUE` 等模板 | `backend/workflow-engine-core/.../NotificationTemplateManager.java` |
| 邮件发送 | ⚠️ **框架在、实现是模拟的（仅打日志）** | `backend/workflow-engine-core/.../NotificationChannelDispatcher.java:35-71` |
| BPMN 设计器 + 属性面板 | ✅ bpmn-js 17 + properties-panel + EventProperties | `frontend/developer-workstation/src/components/designer` |
| 事件配置面板（Message/Signal/Timer） | ✅ `EventProperties.vue` / `useEventDefinitions.ts` | 同上 |
| 邮件**接收**监听 | ❌ 无（全新能力） | 需新增 `mail-connector` |

> 结论：需求 B 约 70% 现成（查询方法已写好，只缺 Scheduler + 真实邮件）；需求 A 需新增「邮件接收适配器」，但下游（写子表、触发流程）全部复用。

## 3. 公共前置项：启用真实邮件发送

当前 `NotificationChannelDispatcher.java:35-71` 邮件为**模拟实现**（只打日志）。需求 A 的告警邮件、需求 B 的到期邮件都依赖它。

改动：
1. 引入 `JavaMailSender` Bean（`spring-boot-starter-mail` 已在 admin-center，需让 workflow-engine-core 可用，或把邮件能力下沉到 `platform-common`）。
2. 替换模拟代码为真实 `mailSender.send(MimeMessage)`，支持 HTML 模板。
3. 配置 `spring.mail.*`，从现有 `backend/platform-common/src/main/resources/application-external-config-example.yml:154` 的 `app.messaging.email-*` 桥接或统一。
4. 失败重试 + 降级：邮件失败不影响 in-app 通知。

## 4. 需求 A：M365 邮箱监控 → 邮件内容入流程内子表

### 4.1 总体架构

以 **BPMN Message Event 作为入口**，让"邮件到达"成为流程一等公民事件，融入现有 Flowable/FU 体系，开发者在 bpmn-js 里像配普通事件一样配置它。

```
┌──────────────┐  delta query(@Scheduled 30s) / 二期 webhook  ┌─────────────────────────┐
│ M365 邮箱     │ ────────────────────────────────────────────>│ GraphMailConnector       │
│ (Azure AD)   │  OAuth2 client-credentials, Mail.Read         │ (新增 mail-connector 模块)│
└──────────────┘                                               └───────────┬─────────────┘
                                                                           │ MIME→InboundMailEvent
                                                                           │ (Message-ID 去重)
                                                                           ▼
                                          ┌──────────────────────────────────────────────┐
                                          │ MailInboundHandler                             │
                                          │ 1. correlation: 由主题/会话ID 定位 processInst │
                                          │ 2. 字段映射 from/subject/body/附件 → 子表列    │
                                          │ 3. 读 __subTables__[key] → append 行           │
                                          │    (复用 MI rowId 规范化, 防 id churn)         │
                                          │ 4. RuntimeService.setVariable() 写回           │
                                          │ 5. (可选) ProcessEventManager.triggerEvent     │
                                          │           (MESSAGE 推进流程)                    │
                                          └──────────────────────────────────────────────┘
```

### 4.2 邮箱接入：Microsoft Graph（M365/Exchange）

| 阶段 | 实现 |
|---|---|
| **首版（轮询）** | `@Scheduled(30s)` → Graph `/messages/delta` 增量游标拉取（优于时间戳过滤，去重更可靠） |
| **二期（实时）** | Graph change notifications (subscription) → 公网 HTTPS webhook `/mail/graph/notify` → 收到通知后 GET 邮件 → 同一个 `MailInboundHandler` |

与 IMAP 方案的关键差异：

| 项 | Graph 方案影响 |
|---|---|
| **认证** | Azure AD 应用注册 + OAuth2 client credentials（应用权限 `Mail.Read`/`Mail.ReadWrite`），需 IT/管理员 admin consent。client secret/证书走平台加密存储。 |
| **实时性（二期）** | change notification 需公网可达 HTTPS 回调，订阅有有效期（邮件类约 ~4230 分钟），需 `@Scheduled` 定时续订。 |
| **首版轮询** | 用 delta query 而非时间戳过滤，原生增量游标。 |
| **依赖** | `microsoft-graph` SDK + `azure-identity`，封装在新 `mail-connector` 子模块，预留 connector 接口（以后可接 IMAP/Gmail）。 |
| **节流** | Graph throttling（429），轮询频率/批量大小可配，遇 429 退避。 |

### 4.3 写入目标：流程内子表（`__subTables__`）

⚠️ 选择「流程内子表」意味着**不能**用 `RelationTableDataService.addData()`（那是 Relation Table 的），而要走**流程变量更新**：

```
邮件到达
  → 定位一个【正在运行的流程实例】(processInstanceId)
  → 读该实例流程变量中目标子表变量 (__subTables__ 下某 key)
  → 字段映射后 append 一行
  → RuntimeService.setVariable() 写回
  → (可选) 触发 message event 推进流程
```

字段映射示例：

```
from        → applicant_email
subject     → title
body(text)  → content
receivedAt  → received_time
attachments → attachments   (存附件引用，二进制落对象存储，不进 JSONB)
```

### 4.4 低代码配置体验（bpmn-js 设计器）

复用 `EventProperties.vue` / `useEventDefinitions.ts`，在 Message Event 上新增 **「Mail Trigger」属性分组**，序列化进 BPMN `extensionElements`（与现有 Hermes 事件/通知配置一致）：

```
Message Event «mailReceived»
  └─ Mail Trigger 配置
       ├─ 邮箱账户   : monitor@company.com  (引用加密的 Azure 应用凭证配置)
       ├─ 监听模式   : 轮询(30s) / 实时(webhook, 二期)
       ├─ 过滤条件   : 发件人含 / 主题含 / 仅未读
       ├─ 关联键     : <correlation 规则，见 Open Questions>
       ├─ 目标子表   : <选择本 FU 的某个 table>   ← 下拉来自 TableDefinitionRepository
       └─ 字段映射   : 邮件字段 → 子表列
```

FU 部署时由 `MailMonitorRegistry` 读取该配置并注册到 connector。

### 4.5 安全与健壮性

- **凭证**：Azure 应用 client secret/证书加密存储 + token 缓存/刷新；绝不明文进 BPMN。
- **幂等去重**：以邮件 `Message-ID`（或 Graph delta 游标）去重，防重连/重投重复入表。建轻量 `mail_processed_log(message_id, fu_code, processed_at)`（系统表，可建物理表）。
- **失败隔离**：解析/写表失败的邮件进死信（复用现有 Kafka DLT），不阻塞后续邮件。
- **分布式单消费者**：多 pod 下轮询必须用 Redis 锁（platform-cache）选主，避免重复拉取/重复触发；webhook 期天然单入口但要防重复通知。
- **MI id churn 风险**：见 [[mi-people-subtable-id-churn]]，若目标是 MI 子表，append 行必须复用现有 rowId 生成/规范化逻辑（疑点 `miLinkChildRows.ts:86`），否则与已知潜伏 bug 叠加导致子任务错乱。需专门回归 MI 子任务。

## 5. 需求 B：即将过期任务通知

### 5.1 架构

```
@Scheduled(每5分钟)
   │
   ▼
TaskDueAlertScheduler  (新增, workflow-engine-core)
   │  ExtendedTaskInfoRepository.findTasksDueSoon(now, now+window)  ← 已存在
   ▼
对每个即将到期任务:
   ├─ 幂等检查 (该任务+该阈值是否已提醒 → reminderSentLevels)
   ├─ NotificationDispatchHelper.publishToUserAfterCommit(
   │       assignee, "REMINDER", "任务即将到期", "...剩 N 小时", taskLink, "workflow-engine")
   │       → in-app + WebSocket 实时弹窗 (复用现有链路)
   └─ (可选) 邮件: NotificationManagerComponent.sendEmailNotification(...)
```

### 5.2 关键点

1. **复用现成查询** `findTasksDueSoon(currentTime, alertTime)`，直接用。
2. **多级提醒阈值**：支持「到期前 24h / 4h / 1h」多个提醒点，每阈值发一次。需幂等标记避免每 5 分钟重复轰炸 → `ExtendedTaskInfo.reminderSentLevels`（JSON 列）或 `task_reminder_log(task_id, threshold, sent_at)`。
3. **通知模板**：新增 `TASK_DUE_SOON` 模板（多语言，遵循 i18n），复用 `NotificationTemplateManager`。
4. **配置化**：
   ```yaml
   workflow:
     task-reminder:
       enabled: true
       scan-cron: "0 */5 * * * ?"
       thresholds: ["PT24H","PT4H","PT1H"]
       channels: [IN_APP, EMAIL]
   ```
5. **水平扩展**：多 pod 必须 Redis 锁/ShedLock 选主执行，否则每 pod 各发一遍。

### 5.3 前端

基本无需改动（走现有 `up_notification` + WebSocket 弹窗 + 通知中心）。可选：通知中心给 REMINDER 类型加图标/颜色。注意 [[mi-overlay-status-cache-invalidation]] 的 5s TTL 缓存模式，新通知类型若涉及状态刷新需确认失效逻辑。

### 5.4 做成 bpmn-js 设计器自定义组件（可选增强）

需求 B 有两种实现哲学，可按低代码诉求选择：

| 维度 | 路线 1 全局扫描器（5.1-5.3） | 路线 2 设计器自定义组件 |
|---|---|---|
| 配置方 | 运维改 yaml，全局生效 | 开发者在设计器里按任务配，声明式随 FU 走 |
| 灵活度 | 所有任务同一套阈值/渠道 | 每个任务可不同阈值/渠道/对象 |
| 低代码契合度 | 低（藏在后端） | **高（符合平台定位）** |
| 实现成本 | 小 | 中 |

**现有基础（关键）**：项目已有路线 2 的雏形 —— `frontend/developer-workstation/src/components/designer/properties/UserTaskProperties.vue:591-630` 已有「超时配置(Timeout)」分组（`timeoutEnabled`/`timeoutDuration`/`timeoutAction`，动作含提醒/自动审批/拒绝），并通过 `bpmnExtensions.ts` 序列化进 `extensionElements`。Flowable async-executor 已启用（`backend/workflow-engine-core/src/main/resources/application.yml:75`），Timer job 可调度。

#### 路线 2A：BPMN 原生 Boundary Timer Event（纯 BPMN 语义）

在 UserTask 上挂非中断 boundary timer（`timeDuration`=到期前 N 小时）→ 连 Service Task(`JavaDelegate`) 发通知。
- ✅ 引擎原生调度，精准无轮询延迟；流程图可视化、审计清晰。
- ⚠️ **设计器缺口**：当前不支持配 boundary event 的 `attachedToRef`（挂到哪个任务），也无 ServiceTask 的 Listener/Delegate 可视化配置（`N8nTaskExecutor` 这个 JavaDelegate 在但无 UI）。补这两块前端，工作量中等。
- ⚠️ 多级阈值需挂多个 boundary timer，繁琐。

#### 路线 2B：扩展现有「超时配置」分组（推荐）✅

把 UserTaskProperties 的 Timeout 分组扩成「到期提醒(Due Reminder)」配置，写进 `extensionElements`：

```
UserTask 属性面板 › 到期提醒(新)
  ├─ 启用         : on/off
  ├─ 提前量(多级) : ["PT24H","PT4H","PT1H"]   ← 到期前 24h/4h/1h
  ├─ 提醒对象     : 受理人 / 受理人+主管 / 指定角色
  └─ 渠道         : in-app / email / both
```

后端 = **路线 1 的执行引擎 + 路线 2 的声明式配置** 的混合：仍是一个 Scheduler（复用 `findTasksDueSoon` + Redis 锁选主 + 多阈值幂等），但阈值/渠道/对象不再 hard-code，而是从流程定义里**读每个任务在设计器声明的 extensionElements 配置**。

**推荐 2B 的理由**：
1. 最大化复用现有 Timeout 分组 UI 与 `extensionElements` 序列化，避开 2A 的两个设计器缺口（boundary `attachedToRef`、Delegate 配置器）。
2. 多级阈值在 2B 是自然数组，在 2A 要挂多个 boundary timer。
3. 既满足"设计器可配、随 FU 走"的低代码诉求，又保留集中式 Scheduler 的运维可控（单一选主、统一幂等）。

> 默认建议：**先做路线 1（最小可用），再平滑升级到 2B**（Scheduler 不变，只是阈值来源从 yaml 改为 extensionElements）。两者后端执行引擎同构，升级无返工。

## 6. 新增 / 改动清单

### 需求 A
- **新增**：`mail-connector` 子模块 — `GraphMailConnector`（delta 轮询 + 二期 webhook）、`MailInboundHandler`、`MailMonitorConfig` + `MailMonitorRegistry`、`mail_processed_log` 去重表
- **复用不改**：`ProcessEventManager.triggerEvent()`、`RuntimeService.setVariable()`、`TableDefinitionRepository`
- **前端扩展**：`EventProperties.vue` / `useEventDefinitions.ts` 增 Mail Trigger 配置分组（账户、模式、过滤、关联键、目标子表下拉、字段映射）+ i18n

### 需求 B
- **新增（路线 1，必做）**：`TaskDueAlertScheduler`（`@Scheduled` + Redis 锁选主 + 多阈值幂等）、`TASK_DUE_SOON` 模板 + i18n、`reminderSentLevels` 字段（或 `task_reminder_log`）
- **新增（路线 2B，可选增强）**：扩展 `UserTaskProperties.vue` 的 Timeout 分组为「到期提醒」配置 + i18n；Scheduler 增加从流程定义 `extensionElements` 读取各任务提醒声明的解析逻辑
- **复用不改**：`findTasksDueSoon()`、`NotificationDispatchHelper`、`NotificationManagerComponent`、`bpmnExtensions.ts`

### 公共前置
- 启用真实 `JavaMailSender` + `spring.mail.*` 配置

## 7. 实施优先级

| 阶段 | 内容 | 依赖 | 工作量 |
|---|---|---|---|
| **P0** | 启用真实邮件发送 | spring.mail 配置 | 小 |
| **P1** | 需求 B：TaskDueAlertScheduler（in-app 优先，邮件次之） | P0、Redis 锁 | 小-中 |
| **P2** | 需求 A 后端：GraphMailConnector(轮询) + Handler + 写子表 + 触发流程 | ProcessEventManager、RuntimeService | 中-大 |
| **P3** | 需求 A 前端：BPMN 设计器 Mail Trigger 配置面板 | P2 后端 API | 中 |
| **P4** | 需求 A 实时化：Graph webhook 订阅 + 续订；健壮性（去重/死信/单消费者锁） | platform-cache 锁 | 中 |

建议从 P0+P1 起步，P2 用轮询先打通端到端再升级实时。

## 8. Open Questions（需评审拍板）

1. **🔴 语义矛盾（最重要）**：「写流程内子表」要求邮件能关联到一个**已运行的流程实例**。若真实需求是"收到邮件就开新流程/新工单"，则应写 **Relation Table**（或在"新流程启动+初始化子表"里做），而非往 `__subTables__` append。请确认邮件到底是**开新流程**还是**补已有流程数据**。
2. **🟠 Correlation 机制**：邮件→流程实例的关联键是什么？业务单号在主题里？邮件回复链（`In-Reply-To`/会话 ID）？发件人匹配某字段？这是需求 A 能否落地的命门。
3. **🟠 MI 子表确认**：目标子表是否为 MI（驱动多实例子任务）子表？若是，必须设计 rowId 复用并回归，规避 [[mi-people-subtable-id-churn]]。
4. **🟡 Graph 运维**：Azure 应用注册、admin consent、二期 webhook 公网回调 + 订阅续订，是 IT 协作项，排期需计入。
5. **🟡 分布式单消费者**：A、B 都需 Redis 锁选主，确认 platform-cache 锁可用。
6. **🟡 需求 B 形态**：在 路线 1（全局 yaml 配置）/ 2B（设计器声明式，推荐）/ 2A（纯 BPMN boundary timer）之间拍板。默认建议先做路线 1，再平滑升级 2B（执行引擎同构，无返工）。
