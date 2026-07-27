# Workflow Engine Core — Reverse-Engineering X-Ray

Module: `backend/workflow-engine-core` (Spring Boot 3.2.1, Java 17, **Flowable 7.0.0** embedded, port 8081, service name `workflow-engine`).
All paths below are relative to `/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun/` unless absolute.

---

## 1. Flowable 7 Embedding

| Aspect | Evidence |
|---|---|
| Dependencies | `flowable-spring-boot-starter` + `flowable-spring-boot-starter-rest` v7.0.0 — `backend/workflow-engine-core/pom.xml:127-134`. The starter-rest exposes Flowable's native REST (`/process-api/**`, `/dmn-api/**`, …). |
| Engine config | `config/FlowableConfig.java:30-47` — `EngineConfigurationConfigurer<SpringProcessEngineConfiguration>` registers two typed event listeners: `TASK_CREATED → TaskAssignmentListener`, `PROCESS_COMPLETED → ProcessCompletionListener`. |
| Schema | `application.yml:74` `flowable.database-schema-update: ${FLOWABLE_SCHEMA_UPDATE:false}`; JPA `ddl-auto: none` (line 31). Authoritative DDL lives in `deploy/init-scripts/00-schema/*.sql`. `config/FlowableActHiCommentSchemaRepair.java` re-applies the ACT_HI_COMMENT widen at startup. |
| History | `flowable.history-level: full`, `async-executor-activate: true` (`application.yml:75-76`). |
| DMN | `flowable.dmn.enabled: true` (`application.yml:78-80`); `component/impl/DecisionExecutionComponentImpl.java:27` injects `DmnDecisionService` and evaluates with a 30 s single-thread timeout wrapper. **But no code anywhere deploys DMN artifacts to the Flowable DMN engine** (zero `DmnRepositoryService` / `.dmn` deployment references in the repo) — see Gaps. |
| Auth | `config/SecurityConfig.java:42-64` — stateless JWT filter (shared `JWT_SECRET` with portal); `permitAll` for Flowable native REST (`/process-api/**` etc., comment: "Kong does not expose these paths") and for deploy/instances/purge endpoints ("internal service-to-service calls"). |
| Gateway | Kong routes `/api/workflow` → workflow-engine, `strip_path: false` (`deploy/kong/kong.yml.template:100-107`, `deploy/k8s/config_map/preprod/kong-declarative-config.yml:95-104`). All other `/api/v1/*` engine endpoints are reached only service-to-service (portal/DW/AC backends via `WORKFLOW_ENGINE_URL`). |

---

## 2. Full Lifecycle (design → deploy → run → complete → history)

```mermaid
sequenceDiagram
    autonumber
    participant DWF as DW Frontend
    participant DW as developer-workstation
    participant AC as admin-center
    participant WE as workflow-engine-core (Flowable 7)
    participant AP as Activepieces
    participant UPF as Portal Frontend
    participant UP as user-portal
    participant K as Kafka

    DWF->>DW: Design BPMN (bpmn-js), save Function Unit
    DW->>DW: dw_process_definitions (BPMN XML stored, NOT deployed)
    DW->>AC: Publish FU (export → sys_function_units + contents)
    Note over AC: approval chain sys_function_unit_approvals<br/>(approval_order, migration 10)
    AC->>WE: POST /api/v1/processes/definitions/deploy<br/>(ProcessDeploymentComponent.java:72)
    WE->>WE: BpmnDeployEnhancer: email sendTask→serviceTask<br/>${sendEmailTaskDelegate}; XXE-hardened parse
    WE->>WE: repositoryService.createDeployment().deploy()
    WE-->>AC: deploymentId + processDefinitionId (key:ver:uuid)
    AC->>AC: save flowableProcessDefinitionId on FU content

    UPF->>UP: Start process (form submit / view action)
    UP->>WE: POST /api/v1/processes/instances<br/>(ProcessStartComponent.java:167 → WorkflowEngineProcessClient.java:92)
    WE->>WE: runtimeService.startProcessInstanceByKey<br/>(ProcessEngineComponent.java:133, Authentication=startUserId)
    WE->>K: NotificationEvent → platform.notification.events<br/>(NotificationDispatchHelper, after commit)
    WE-->>UP: processInstanceId; UP persists up_process_instance snapshot

    Note over WE: TASK_CREATED event
    WE->>WE: TaskAssignmentListener → TaskAssigneeResolver<br/>(assigneeType via admin-center; candidates/claim)
    WE->>WE: MultiInstanceTaskWriter: wf_extended_task_info +<br/>sub-table progress columns (MI per-row tasks)
    WE->>K: task-assigned notification (TaskAssignmentListener.java:778)
    K-->>UP: NotificationKafkaConsumer → up notification + WS

    alt Service task (Activepieces)
        WE->>AP: POST {base}/api/v1/webhooks/{flowId}/sync<br/>(ApTaskExecutor, retries+backoff, SSRF-checked)
        AP-->>WE: JSON result → output mapping → process variables<br/>(may include __subTables__); ap_execution_records row
    else Email send task
        WE->>WE: SendEmailTaskDelegate → AC credentials → SMTP<br/>BpmnError EMAIL_* on failure
    end

    UPF->>UP: Complete task (form data + __subTables__)
    UP->>WE: POST /api/v1/tasks/{taskId}/complete<br/>(WorkflowEngineTaskClient.java:218)
    WE->>WE: TaskCompletionService (@Transactional):<br/>permission check, MI sub-table writeback,<br/>setVariables on PI, taskService.complete
    WE->>K: task-completed notification (TaskCompletionService.java:640)
    WE-->>UP: result; UP updates up_process_instance variables

    Note over WE: PROCESS_COMPLETED (after COMMIT)
    WE->>UP: async POST /api/portal/processes/{id}/complete<br/>+X-Internal-Service-Token (ProcessCompletionListener.java:73)
    UP->>UP: up_process_instance → COMPLETED, lastActivityName

    UPF->>UP: History views
    UP->>WE: GET /api/v1/history/completed-tasks, /history/tasks,<br/>/tasks/{id}/history (Flowable act_hi_*)
```

Alternate trigger — **email inbound** (migrations 48/49/50): `email/inbound/EmailMonitorScheduler.java` polls IMAP every 30 s (`@Scheduled` + ShedLock `EmailMonitor_poll`, lockAtMostFor 5 m); `EmailMonitorProcessor.java:63-116` extracts fields per `EmailFieldExtractor` spec (incl. sub-table rows → `__subTables__`, line 106), enforces idempotency via `we_email_processed_messages UNIQUE(rule_uid, message_id)`, calls `ProcessEngineComponent.startProcess`, then `EmailMonitorPortalSyncComponent.hydratePortalProcessInstanceAsync` creates the portal-side `up_process_instance` row.

---

## 3. Module Structure (214 main / 66 test files, package `com.workflow`)

| Package | Contents / Role |
|---|---|
| `controller/` (8) | `ProcessController`, `TaskController`, `HistoryController`, `MonitoringController`, `MultiInstanceStatusController`, `ApExecutionController`, `DecisionExecutionController`, `HealthAliasController` (+`TaskHistoryAssembler` helper). |
| `component/` (~60) | The real service layer. Process: `ProcessEngineComponent` (start/query facade), `ProcessDeploymentManager` (deploy/suspend/activate/delete, BPMN validation), `ProcessInstanceQueryManager`, `ProcessEventManager`. Task: `TaskManagerComponent` facade → `TaskQueryService`, `TaskCompletionService`, `TaskActionService`, `TaskMultiInstanceService`, `TaskOrphanRepairService`, `TaskInfoAssembler`, `TaskStatsService`, `SubTableAssignmentHandler`, `SubTableDataInjector`, `MultiInstanceDataResolver`, `MultiInstanceCanceller`. Errors: `ExceptionHandlerComponent`, `RetryAndCompensationComponent`, `CompensationExecutor`. Notifications: `NotificationManagerComponent` + `NotificationEventCoordinator`/`NotificationKafkaDispatcher` (Redis-simulated, see Gaps). Plus large Security*/Performance*/HorizontalScaling*/ProcessMonitor* families (mostly self-contained, Redis-backed, "simulated" enterprise features). |
| `delegate/` | `SendEmailTaskDelegate` — the only `@Component("...")` bean-named JavaDelegate besides `ApTaskExecutor` (which lives in `component/`). |
| `listener/` (5) | `TaskAssignmentListener` (801 L), `MultiInstanceTaskWriter` (535 L), `ProcessCompletionListener`, `AssigneeUserIdNormalizer`, `UserTaskExtensionPropertyReader`. |
| `email/` | `inbound/` (IMAP poller, rules/connections synced into `sys_email_*`, processed ledger) and `extract/` (field/sub-table extraction spec). |
| `service/` | `TaskAssigneeResolver`, `LastUserTaskAssigneeQuery`, `EmailSenderService`, `UserPermissionService`. |
| `entity/` + `repository/` | `ExtendedTaskInfo` (`wf_extended_task_info`), `ApExecutionRecord` (`ap_execution_records`), `ExceptionRecord`, `AuditLog`, `ProcessVariable` (+ email inbound entities). |
| `messaging/` | `SubTableUpdatePublisher` — STOMP WebSocket (NOT Kafka) to `/topic/tasks/{taskId}/sub-table-updates`. |
| `config/` | `FlowableConfig`, `SecurityConfig`, `WebSocketConfig` (STOMP endpoint `/ws/sub-table-updates`), `SchedulerLockConfig` (ShedLock on Redis), `RestTemplateConfig`, `FlowableActHiCommentSchemaRepair`, CORS/Redis/Jsonb. |
| `util/` | `BpmnDeployEnhancer` (sendTask→serviceTask rewrite), `BpmnExtensionUtils`, `ApVariableMappingUtil`, `SubTableFieldResolver`/`SubTableHtmlFormatter` (`__subTables__` → email templates), `EmailTemplateResolver`, `WorkflowActorResolver`, assignment fallbacks. |
| `aspect/` | `WorkflowAuditAspect` — `@Around("@annotation(auditable)")` → `AuditManagerComponent` → `wf` audit tables. |

### Key runtime tables
- Flowable `act_ru_*` / `act_hi_*` (identitylink & comment columns widened to 4000/bytea — migrations 30/31, motivated by long virtual-group IDs and long comments failing task completion).
- `wf_extended_task_info` — engine's own task metadata: assignmentType, currentAssignee, MI flags, sub-table row binding (`subTableRowId`), soft-delete.
- `ap_execution_records` — every AP webhook call (PENDING/SUCCESS/FAILED, input/output JSON, retryCount).
- `we_email_processed_messages` — inbound-mail idempotency ledger (migration 50).
- `wf_multi_instance_execution` (migration 24) — **dead**: zero Java references anywhere (grep across backend); MI state actually derives from Flowable `nrOfInstances/nrOfCompletedInstances/nrOfActiveInstances` variables + `wf_extended_task_info` (`MultiInstanceStatusController.java:86-120`).

---

## 4. Service Tasks

### 4.1 Activepieces (`component/ApTaskExecutor.java`, bean `apTaskExecutor`)
- Implements `JavaDelegate`; BPMN Service Tasks carry `custom:properties` with `ap:` prefix: `ap:flowId`, `ap:webhookUrl` (override), `ap:inputMapping`, `ap:outputMapping`, `ap:timeoutSeconds` (default 120), `ap:retryCount` (default 3) — read at lines 77-82, parser at 304-333.
- **`ap:timeoutSeconds` is recorded, not enforced.** AP owns the wait via `AP_WEBHOOK_TIMEOUT_SECONDS` (300s); the shared `RestTemplate` read timeout (10 min) is deliberately longer so AP always answers first with an attributable 204. Enforcing 60-120s client-side would abort flows that are still running in AP — unknown side effects, and every automation slower than that breaks. Shorten on the AP side instead.
- **Synchronous** POST to `{activepieces.webhook-base-url}/api/v1/webhooks/{flowId}/sync` (lines 185-196); AP flow must end with "Return Response". No callback/async/Redis state (class javadoc lines 33-41). n8n is fully migrated off — only test files renamed `AiN8N* → AiWebhook*` remain in DW.
- **HTTP 2xx alone does not mean the automation succeeded.** AP publishes the sync response only from the "Return Response" step, so a run that fails earlier returns `204 No Content` + empty body once AP's webhook listener expires. `invokeWebhook` rejects 204 as `ApFlowNoResponseException`; treating it as success previously let a failed flow advance the process with empty output and no error anywhere.
- Retry: exponential backoff `1s·2^n` (`calculateRetryDelay:296`), record marked FAILED then `RuntimeException` rethrown for BPMN error boundaries (lines 265-273) — otherwise the Flowable async executor/incident behavior applies. `ApFlowNoResponseException` is **not** retried: it is deterministic and each attempt blocks for the full AP webhook timeout.
- SSRF: `SsrfProtection.validate` with the configured AP host allow-listed (lines 203-217).
- Output mapping written back via `execution.setVariables(outputData)` (line 112) — this is how an AP flow can set `__subTables__`.
- Relative `/api/...` file URLs converted to absolute using `file-service.base-url` so AP containers can fetch uploads (lines 377-396).
- Action mode (user-triggered, not process-driven): `executeSynchronous()` (128-177) exposed at `POST /api/v1/ap/execute`.

### 4.2 Email send (`delegate/SendEmailTaskDelegate.java` via `util/BpmnDeployEnhancer.java`)
- At deploy time every email `sendTask` is rewritten to `serviceTask` with `flowable:delegateExpression="${sendEmailTaskDelegate}"` (`BpmnDeployEnhancer.java:106-119`), invoked from `ProcessDeploymentManager.deployProcess:70`.
- Delegate reads extension properties (`connectionId`, `emailTo/Cc/Bcc/ReplyTo/From/FromName`, subject/body templates, attachments JSON), resolves `${var}` templates incl. `__subTables__` slices (`SubTableFieldResolver`, `SubTableHtmlFormatter`), resolves user-IDs → emails via AdminCenter (fallback: keep raw value on AC outage, line 198-203), fetches SMTP credentials per `functionUnitId+connectionId` from AdminCenter, sends via `EmailSenderService`, sets `emailSendResult` variable, and throws typed `BpmnError`s (`EMAIL_CONFIG_INVALID`, `EMAIL_CONNECTION_NOT_FOUND`, `EMAIL_SEND_FAILED`) — lines 38-141.

### 4.3 `__subTables__` variable
- Produced by: portal form submissions (task complete variables), AP output mapping, email-inbound extraction (`EmailMonitorProcessor.java:106`).
- Consumed by: MI collection resolution (`TaskMultiInstanceService.java:135-152` strips `__subTables__` and MI bookkeeping keys from element scopes), email templates, portal task-detail grids.
- Engine→portal propagation (the historical bug, now fixed on both sides):
  - Engine: `ProcessController.java:139-157` — instance detail merges **live** `runtimeService.getVariables()` over the projected subset so service-task outputs are visible.
  - Portal: `user-portal/.../component/TaskQueryComponent.java:460-484` `hydrateEngineSubTablesIntoMerged` (called at :541) — fill-only merge of engine-only `__subTables__` slices into the portal store row, persisted onto `up_process_instance` so subsequent completion carries the rows; guarded by `SubTableNestingSanitizer` (portal) against nested-slice bloat.

### 4.4 Multi-instance
- BPMN MI user tasks iterate sub-table rows (`collection` variable derived from `__subTables__[bindingId]`); per-element `currentItem`/`_currentItem`.
- `listener/MultiInstanceTaskWriter.java` writes `wf_extended_task_info` rows keyed to sub-table row (`subTableRowId`, composite-PK aware via `SubTableRowKeySupport`) and updates task-progress columns directly in the physical sub-table (SQL identifiers whitelist-validated, line 46).
- Completion writeback: `TaskCompletionService.java:154-157` → `TaskMultiInstanceService.handleMultiInstanceSubTaskCompletion` (row status/result columns); `MultiInstanceDataResolver` has an `OptimisticLockException` (row_version, migration 25).
- Return/rollback across an MI block cascades cancellation: `TaskCompletionService.returnTask` → `MultiInstanceCanceller.cancelMultiInstanceTasks` (lines 281-286).
- Row-level reassignment: `POST /api/v1/tasks/{taskId}/sub-table-rows/{rowId}/assign` → `SubTableAssignmentHandler`, pushed live via `messaging/SubTableUpdatePublisher` STOMP topic.

---

## 5. Task Assignment

- `listener/TaskAssignmentListener.java` (TASK_CREATED): reads BPMN extension props (`assigneeType`, `assigneeValue`, `roleId(s)`, `businessUnitId`, manual-assign variable names…) via `UserTaskExtensionPropertyReader`; anchors: PROCESS_INITIATOR / LAST-task assignee (`LastUserTaskAssigneeQuery`); BU id→code mapping via AdminCenter (:186-197).
- `service/TaskAssigneeResolver.java` resolves BU_ROLE / HIERARCHY / etc. through `AdminCenterClient`; `ResolveResult` distinguishes `infraFailure` (AdminCenter down — auto-repairable later) from config errors (lines 29-42).
- Single resolved user → `task.setAssignee`; multiple → candidate users (`taskService.addCandidateUser`) / candidate groups requiring claim (`requiresClaim`); group IDs can be long "virtual group" ids — the reason for migration 30 widening `act_ru/hi_identitylink.group_id_` to VARCHAR(4000).
- Failure trace: task-local vars `assignmentFailure` / `assignmentFailureKind` (INFRA/CONFIG/ERROR) + `ExceptionRecord` (`TaskAssignmentListener.java:106-184`). Repair: `component/TaskOrphanRepairService` — **not scheduled**; invoked lazily from `TaskQueryService.java:65,173` on task-list queries (repairs orphan BU-role pool tasks, MI tasks, initiator tasks).
- Delegation/claim/transfer/return: `TaskController` endpoints backed by `TaskActionService`/`TaskCompletionService`; portal mirrors delegation records locally (`DelegatedTaskQueryComponent`). Approval comments → Flowable `taskService.addComment` (`TaskCompletionService.java:194-196`), hence migration 31 (ACT_HI_COMMENT widen + `FlowableActHiCommentSchemaRepair`).
- `approval_order` (migration 10) is **not an engine concept**: it orders the FU *publish approval* chain in admin-center (`sys_function_unit_approvals`; `admin-center/.../DeploymentManagerComponent.java:127`).

---

## 6. Error Handling & Transactions

- Controllers → `WorkflowExceptionControllerAdvice` + `ApiResponse` envelope; domain exceptions `WorkflowBusinessException` / `WorkflowValidationException` / `AdminCenterUnavailableException`.
- `ExceptionHandlerComponent.recordException` classifies type/severity by class-name heuristics (:119-180), persists `ExceptionRecord`, HIGH/CRITICAL alert via NotificationManager; used by assignment failures and task timeouts.
- `RetryAndCompensationComponent` (605 L): retry policies, in-memory dead-letter (`DeadLetterMessage`) and `CompensationTransaction` registry + `CompensationExecutor` — **self-contained, not wired to a persistent DLQ**.
- AP failures: in-loop retry then RuntimeException → Flowable rollback of the service-task transaction (sync continuation ⇒ the *task-complete* HTTP call that triggered the token move fails; portal surfaces the error).
- Transactions: `TaskCompletionService` and `ProcessDeploymentManager` are class-level `@Transactional`; Flowable shares the Spring tx. Deadlock avoidance between engine and portal: `ProcessCompletionListener` fires only `COMMITTED` (:191-194) and notifies portal **async with a 500 ms sleep** (:68-71) because portal `completeTask` holds the `up_process_instance` row lock — a sleep-based ordering, see Gaps.
- Email inbound: per-message ledger status STARTED/REVIEW/FAILED; failures never crash the poll loop; ShedLock serializes replicas.

---

## 7. Kafka (platform-messaging)

- Topics (`platform-messaging/.../config/KafkaTopics.java`): `platform.process.events`, `platform.task.events`, `platform.permission.events`, `platform.deployment.events`, `platform.notification.events` + `.dlt`/`.retry` variants. `DeadLetterHandler` listens on all `.dlt` topics (log-only).
- **Engine is a producer only** (no `@KafkaListener` in workflow-engine-core). All real publishing goes through `NotificationDispatchHelper.publishToUserAfterCommit` (after-commit, bounded best-effort pool, drop-on-saturation — `platform-messaging/.../support/NotificationDispatchHelper.java:30-60`) → `KafkaEventPublisher` → `platform.notification.events`. Publish points: process started (`ProcessEngineComponent.java:164`), task assigned (`TaskAssignmentListener.java:778,792`), task completed (`TaskCompletionService.java:640,661`), action executed (`TaskActionService.java:335,566,582`).
- Consumer: `user-portal/.../component/NotificationKafkaConsumer.java` (group `user-portal-notification-group`) → portal notification records → portal WS `/api/portal/ws/notifications`.
- The `PROCESS/TASK/PERMISSION/DEPLOYMENT` topics have **no producers or consumers** in the codebase — declared but unused.
- `component/NotificationKafkaDispatcher.java:36-42` is **not Kafka**: it pushes JSON into a Redis list (`notification:kafka:{topic}`) and triggers in-JVM consumers — an explicit simulation retained from the NotificationManagerComponent extraction.

---

## 8. REST Endpoint Inventory (engine) with Callers

Callers verified by grep over `user-portal` (UP), `developer-workstation` (DW), `admin-center` (AC) backends and all three frontends (frontends never call the engine over HTTP; only WS, see below).

### ProcessController `/api/v1/processes` (`controller/ProcessController.java`)
| Endpoint | Purpose | Callers |
|---|---|---|
| POST `/definitions/deploy` (:49) | Deploy BPMN (enhance→validate→deploy) | AC `ProcessDeploymentComponent.java:72,144`; UP `WorkflowEngineProcessClient.java:49`; DW client method exists but **unused in DW** |
| GET `/definitions` (:70) | List latest defs | **Orphan** (no caller found) |
| POST `/instances` (:91) | Start process instance | UP `WorkflowEngineProcessClient.java:92` (← `ProcessStartComponent.java:167`); engine-internal email inbound |
| GET `/instances/{id}` (:113) | Instance detail + **live merged variables** (:139-157) | UP `WorkflowEngineProcessClient.java:255,284` |
| DELETE `/instances/{id}` (:167) | Terminate | UP (same client) |
| POST `/instances/{id}/purge` (:199) | Hard purge runtime+history | UP `WorkflowEngineProcessClient.java:138` |
| DELETE `/definitions/deployments/{id}` (:214) | Undeploy | AC `WorkflowEngineClient.java:127` |
| POST `/definitions/{id}/suspend` / `/activate` (:232/:248) | Definition lifecycle | AC `WorkflowEngineClient.java:156,180` |
| GET `/definitions/{key}/bpmn` (:264) | BPMN XML for viewer | UP `WorkflowEngineProcessClient.java:317` |
| GET `/{id}/status` (:283) | Instance status probe | UP `WorkflowEngineProcessClient.java:164` |

### TaskController `/api/v1/tasks` (`controller/TaskController.java`)
| Endpoint | Purpose | Callers |
|---|---|---|
| GET `` (:71) | Task list (user/groups/filters) | UP `WorkflowEngineTaskClient.java:48-125`; AC `DepartmentRoleTaskServiceImpl.java:214` (`?groupIds=`) |
| GET `/{taskId}` (:141) | Task detail (+actionIds) | UP `WorkflowEngineTaskClient.java:148` |
| GET `/{taskId}/history`, `/process/{piId}/history` (:155/:173) | Per-task / per-instance task history | UP `WorkflowEngineTaskHistoryClient.java:102,74` |
| POST `/{taskId}/assign` (:187) | Direct assign | **Orphan** (no external caller) |
| POST `/{taskId}/claim` (:213) | Claim | UP `WorkflowEngineTaskClient.java:258`; AC `DepartmentRoleTaskServiceImpl.java:262` |
| POST `/{taskId}/delegate` / `/unclaim` / `/transfer` (:246/:273/:298) | Delegation ops | UP `WorkflowEngineTaskClient.java:290,323,355` |
| POST `/{taskId}/complete` (:327) | Complete with variables | UP `WorkflowEngineTaskClient.java:218` |
| POST `/{taskId}/return` (:363), GET `/{taskId}/returnable-activities` (:394) | Rollback | UP `WorkflowEngineTaskClient.java:457,496` |
| POST `/batch/complete` (:408) | Batch complete | **Orphan** |
| GET `/count` (:463) | Todo counters | UP `WorkflowEngineTaskClient.java:192` |
| GET `/user-permissions` (:489), `/{taskId}/check-permission` (:521) | Permission probes | UP `WorkflowEngineTaskHistoryClient.java:128,152` |
| POST `/{taskId}/sub-table-rows/{rowId}/assign` (:561) | MI row-level assignee | UP `WorkflowEngineTaskClient.java:396` |

### HistoryController `/api/v1/history` (`controller/HistoryController.java`)
| Endpoint | Purpose | Callers |
|---|---|---|
| GET `/completed-tasks` (:49) | User's done list | UP `WorkflowEngineClient.java:467` |
| GET `/tasks` (:207) | Historic tasks by PI | UP `WorkflowEngineTaskHistoryClient.java:43` |
| GET `/activities` (:286) | Historic activities | **Orphan** |
| GET `/process-statistics` (:324) | Per-user stats | UP `WorkflowEngineClient.java:503` |

### MultiInstanceStatusController `/api/v1/workflow/multi-instance`
| Endpoint | Purpose | Callers |
|---|---|---|
| GET `/{processInstanceId}/status` (:68) | MI aggregate (nrOf* + wf_extended_task_info, soft-deleted fallback for ended PIs) | UP `WorkflowEngineProcessClient.java:234` |
| GET `/tasks/{taskId}/sub-table-data/all` (:514) | Full sub-table rows for a task | UP `WorkflowEngineTaskClient.java:171` |

### MonitoringController `/api/v1/monitoring` (`controller/MonitoringController.java`)
| Endpoint | Purpose | Callers |
|---|---|---|
| GET `/processes/{id}/current-activity` (:72) | Current node for portal timeline | UP `WorkflowEngineProcessClient.java:187` |
| POST `/processes/query`, GET `/processes/statistics`, GET `/processes/{id}/diagram`, POST `/history/query`, POST `/history/export`, GET `/health` (:34-:112) | Monitoring suite | **Orphan** (no callers anywhere) |

### Others
| Endpoint | Purpose | Callers |
|---|---|---|
| GET `/api/workflow/ap/executions[/{id}]` (`ApExecutionController.java:42,68`) | AP execution record query | **Orphan** (Kong-reachable at `/api/workflow`, but no frontend/backend caller; documented in `deploy/ACTIVEPIECES_INTEGRATION.md:361`) |
| POST `/api/v1/ap/execute` (`ApExecutionController.java:79`) | Sync AP Action execution ("Internal API") | **Orphan** — portal executes AP actions via its own path; no caller found |
| POST `/api/v1/processes/decisions/{decisionKey}/evaluate` (`DecisionExecutionController.java:34`) | DMN evaluate | **Orphan** — and no DMN deployment exists, so it can only 404-at-decision-level |
| GET `/health/live`, `/health/ready`, `/.well-known/health` (`HealthAliasController.java`) | K8s probes | Infra |
| Flowable native REST `/process-api/**`, `/dmn-api/**`, … (starter-rest, permitAll) | Full engine admin API | Not proxied by Kong; reachable on the pod/container network |
| STOMP WS `/ws/sub-table-updates` (`WebSocketConfig.java:32`) | Sub-table live updates | Portal frontend `useSubTableWebSocket.ts:25` connects to `/api/workflow/ws/sub-table-updates` — **path mismatch**, see Gaps |

---

## 9. Risks / Gaps

| # | Status | Finding | Evidence |
|---|---|---|---|
| 1 | **Confirmed (missing endpoint)** | Portal calls `GET /api/v1/history/processes/{id}` but the engine has no such mapping (HistoryController only has completed-tasks/tasks/activities/process-statistics). Every call 404s and is silently swallowed into `Optional.empty()` — process history detail from this path is permanently empty. | `user-portal/.../client/WorkflowEngineProcessClient.java:211` vs `workflow-engine-core/.../controller/HistoryController.java:49-324` |
| 2 | **Confirmed (dead feature)** | DMN: DW designs/validates/exports decisions (`DecisionDesignComponentImpl`, exporter writes `decisions/*.dmn`) and the engine enables the DMN engine + evaluate endpoint, but **nothing ever deploys DMN to Flowable** (no `DmnRepositoryService`/DMN deployment usage repo-wide) and nothing calls `POST /api/v1/processes/decisions/{key}/evaluate`. End-to-end decision execution is disconnected. | `DecisionExecutionController.java:34`; `DecisionExecutionComponentImpl.java:27`; grep: zero deploy/eval callers |
| 3 | **Confirmed (likely broken)** | Sub-table WebSocket path mismatch: engine registers STOMP at `/ws/sub-table-updates`, Kong forwards `/api/workflow/*` with `strip_path:false`, portal frontend connects to `/api/workflow/ws/sub-table-updates` → engine receives a path it never registered. Live sub-table updates degrade to polling. (Dev vite proxy has no `/api/workflow` route at all.) | `WebSocketConfig.java:32`; `deploy/kong/kong.yml.template:105-107`; `frontend/user-portal/src/composables/useSubTableWebSocket.ts:25`; `frontend/user-portal/vite.config.ts:36-62` |
| 4 | **Confirmed (dead schema)** | `wf_multi_instance_execution` (migration 24) has zero Java references — MI status is computed from Flowable variables + `wf_extended_task_info` instead. | `deploy/init-scripts/00-schema/24-add-multi-instance-execution-table.sql`; repo-wide grep |
| 5 | **Confirmed (misleading naming)** | `NotificationKafkaDispatcher` "Kafka" is a Redis-list simulation with in-JVM consumers; the real Kafka path is `NotificationDispatchHelper`. Only `platform.notification.events` is actually used; the other 4 declared topics (+retry) have no producers/consumers. | `NotificationKafkaDispatcher.java:36-42`; `KafkaTopics.java`; grep |
| 6 | **Partial (fragile ordering)** | Process-completion sync to portal relies on `Thread.sleep(500)` after COMMITTED and fire-and-forget HTTP; if portal is down or the callback loses the race, `up_process_instance` stays RUNNING (mitigated only for user-task flows by portal's own completion path; pure-automation flows depend entirely on this callback). | `ProcessCompletionListener.java:64-90` |
| 7 | **Partial (fixed, with residual)** | Service-task `__subTables__` propagation: engine merges live vars into instance detail (fix in place) and portal hydrates+persists engine-only slices (fix in place), but hydration is best-effort, read-path-triggered (only when a task detail is opened) and skipped when the engine is down — a completion issued without a prior detail read can still miss AP-produced rows. | `ProcessController.java:139-157`; `TaskQueryComponent.java:460-484,537-541` |
| 8 | **Partial (security)** | Flowable native REST (`/process-api/**`, `/dmn-api/**`, `/idm-api/**`, …) is `permitAll`, relying solely on Kong not routing those paths; any actor on the cluster/compose network gets full unauthenticated engine admin. Deploy/instances/purge `/api/v1` endpoints are likewise `permitAll` for service-to-service convenience. | `SecurityConfig.java:48-62`; `pom.xml:132-134` |
| 9 | **Partial (resilience)** | AP service-task retry loop `Thread.sleep`s inside the engine thread (up to 1+2+4 s… per node) and the whole AP call is synchronous within the Flowable transaction — long AP flows push toward the portal's HTTP timeout during task completion; timeoutSeconds is recorded but not enforced client-side (RestTemplate global timeouts govern). | `ApTaskExecutor.java:244-274`; `RestTemplateConfig.java` |
| 10 | **Unknown** | `RetryAndCompensationComponent`/`CompensationExecutor` dead-letter and compensation registries are in-memory only; no caller wires BPMN failures into them (no persistence, lost on restart). Appears to be scaffolding awaiting integration. | `RetryAndCompensationComponent.java:67-145` |
| 11 | **Orphan (endpoints)** | No caller anywhere: `GET /api/v1/processes/definitions`, `POST /api/v1/tasks/{id}/assign`, `POST /api/v1/tasks/batch/complete`, `GET /api/v1/history/activities`, 6 of 7 MonitoringController endpoints, both `ApExecutionController` GETs, `POST /api/v1/ap/execute`, DMN evaluate. | table §8 |
| 12 | **Confirmed (ops)** | Engine has `ddl-auto:none`, no Flyway; correctness depends on operators applying `deploy/init-scripts/00-schema` (esp. 30/31 column widenings — without them long group IDs/comments make task completion fail at runtime). `FlowableActHiCommentSchemaRepair` self-heals only the comment table. | `application.yml:31,74`; migrations 30/31 headers |

---

## 10. up_process_instance Interaction Summary (current state)

- Portal owns `up_process_instance` (JSON `variables` column) as its materialized copy of engine state; engine never writes it directly.
- Writers: portal `ProcessStartComponent` (create on start), portal task completion (merge submitted variables), `ProcessCompletionListener` callback → portal `markProcessAsCompleted`, engine-side email-inbound `EmailMonitorPortalSyncComponent.hydratePortalProcessInstanceAsync` (calls a portal internal API to create the row for engine-initiated instances).
- The service-task-variable gap is closed by the two-sided fix described in Gap 7; `SubTableNestingSanitizer` (portal) prevents the nested-`__subTables__` growth that previously bloated the JSON column; business_key widened by migration 23.
