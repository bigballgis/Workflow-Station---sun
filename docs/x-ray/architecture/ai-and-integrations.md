# AI / Agent Layer & External Automation Integrations — X-Ray

Repo: `/Users/qiweige/Desktop/PROJECTXXXSUN/Workflow-Station---sun` (branch `common_0701_timeline`, working-tree state incl. uncommitted AI-layer changes).
All paths below are repo-absolute. Status labels: **Confirmed** (code + tests + docs agree, tested per docs), **Partial** (implemented but incomplete/unverified in prod), **Mocked**, **Dead**, **Unknown**.

---

## 1. AI Generation in Developer Workstation — Status: Confirmed (dev-tested end-to-end)

### 1.1 What it is

A 3-phase conversational "generate a whole Function Unit with AI" feature inside the Developer Workstation. Phases: `REQUIREMENTS → DESIGN → GENERATION` (`enums/AiPhase.java`), modes `NEW`/`MODIFY` (auto-detected). The final GENERATION phase emits a full **AiGeneratedData** payload — table definitions, form definitions, action definitions, DMN decision definitions, table relations (FKs), BPMN process definition, and an icon — which, on user "Apply", is validated and written into the Function Unit's design entities. So yes: **tables + forms + actions + decisions + FK relations + BPMN, i.e. a complete function unit**, not just one artifact.

Evidence for what gets written: `AiWriteServiceImpl.applyGeneratedData()` clears then writes tables, FKs (`writeForeignKeys` L234), table relations (L285), forms (L320), actions (L610), decisions (L643), process/BPMN (L667) — `backend/developer-workstation/src/main/java/com/developer/service/impl/AiWriteServiceImpl.java:41-680`.

### 1.2 End-to-end request flow

```mermaid
sequenceDiagram
    participant U as Developer (AiPanel.vue)
    participant K as Kong (/api/v1/ai-generation, 300s timeouts)
    participant C as AiGenerationController
    participant Comp as AiGenerationComponentImpl
    participant Svc as AiGenerationServiceImpl
    participant AP as Activepieces sync webhook<br/>(flow: AI Function Unit Generation)
    participant DS as deepseek-v4-pro (custom provider, Bearer key)

    U->>K: POST /ai-generation/chat/stream (fetch + ReadableStream, JWT + X-User-Id)
    K->>C: route (read/write timeout 300s)
    C->>Comp: chatStream(request, userId)
    Comp->>Comp: assert workspace access + extend edit lock
    Comp->>Svc: createSession / restoreSession, saveMessage(USER)
    Comp->>Svc: serializeFunctionUnitContext(fuId) (100KB cap, 2-tier truncation)
    Comp->>Svc: getLatestDocuments(phase, mode)
    Comp-->>U: SseEmitter returned immediately (timeout = 2*300s + 60s)
    Note over Comp: async on taskExecutor
    Comp->>Svc: callAiWebhook(sessionId, msg, phase, mode, ctx, docs, scope)
    Svc->>Svc: buildPriorConversationHistory (full history minus current msg)
    Svc->>AP: POST http://activepieces:80/api/v1/webhooks/QnU0ytf5oBaxL9rbwOU2Z/sync<br/>{sessionId, message, phase, mode, functionUnitId, context(JSON str), existingDocuments, conversationHistory, schemaMetadata, includeExplanations, regenerateScope}
    AP->>AP: Code "Build Prompt" (phase-specific system prompt + markers)
    AP->>DS: piece-ai run_agent (provider=custom, model=deepseek-v4-pro)
    DS-->>AP: markdown reply with ---REQUIREMENTS_DOC/DESIGN_DOC/GENERATED_DATA/PHASE_COMPLETE--- markers
    AP->>AP: Code "Parse Response" → {reply, document, documentType, phaseComplete, generatedData}
    AP-->>Svc: Return Response JSON (sync HTTP response)
    Svc-->>Comp: response map (retry once on TIMEOUT/CALL_FAILED, +2s; session-not-found → rebuild w/ full history and re-call)
    Comp-->>U: SSE events: session → token → document → phase_complete → generated_data(+qualityScore) → done | error{errorCode, degradationOptions}
    U->>C: POST /ai-generation/{fuId}/apply {generatedData, sessionId, regenerateScope}
    C->>Comp: applyGeneratedData → normalize (MANY_TO_ONE→ONE_TO_MANY, crossFieldRules.targetField default) → validate → undo snapshot (30s TTL) → AiWriteService writes all entities
    Comp-->>U: event-SSE write_success / write_error; optional POST /{fuId}/undo within 30s
```

### 1.3 Backend anatomy (current working tree)

| Concern | Where | Notes |
|---|---|---|
| Controller | `backend/developer-workstation/src/main/java/com/developer/controller/AiGenerationController.java` | `/ai-generation/chat/stream` (SSE POST, L49), `/events/{fuId}` (long-lived SSE, L59), lock acquire/release/force-unlock (L69-118), sessions/messages (L120-134), phase update (L136), document versions/save (L148-181), `/{fuId}/apply` (L183), `/{fuId}/undo` (L195). All gated by `@RequireDeveloperPermission("FUNCTION_UNIT_UPDATE|VIEW")`. |
| Orchestrator | `component/impl/AiGenerationComponentImpl.java` | `chatStream` L84-266: lock renew → session → save user msg → load context/docs **in main thread** (transaction-bound; graceful degrade to null/empty on failure L117-122) → async webhook call with **superseded-emitter guard** (`emitIfCurrent` L134-141, prevents a stopped/restarted turn's stale events leaking into the new stream). Apply flow L324-384 with undo snapshot cache (`undoSnapshots`, 30s TTL, L55-58, L342-351). LLM-output normalizers: `normalizeTableRelations` (MANY_TO_ONE→swap→ONE_TO_MANY, L490) and `normalizeCrossFieldRules` (default `targetField` = last of `fields[]`, L456). Quality score computed and attached to `generated_data` event (L196-214). |
| Service | `service/impl/AiGenerationServiceImpl.java` | Session CRUD w/ status machine (ACTIVE→COMPLETED/CANCELLED only, L731-743); mode detection (any existing component ⇒ MODIFY, L195-212); message persistence (`dw_ai_messages`); document versioning (auto-increment per fuId+type, L249-269); context serialization w/ 100KB cap and 2-tier truncation (bpmnXml → form configJson rule arrays → whole configJson, L289-376); webhook call w/ history rebuild (L452-482); SSE via `AiSseEmitterManager` collaborator. |
| Webhook target | `AiGenerationServiceImpl.java:64-72` | `activepieces.ai-generation.webhook-url` default `http://activepieces:80/api/v1/webhooks/QnU0ytf5oBaxL9rbwOU2Z/sync`; timeout `activepieces.ai-generation.timeout-seconds` default **300s** (comment: deepseek-v4-pro DESIGN docs measured ~230s; must be ≥ AP's `AP_WEBHOOK_TIMEOUT_SECONDS` or AP returns 204 empty first). |
| SSRF guard | `AiGenerationServiceImpl.java:110-124` | `@PostConstruct` builds a dedicated RestTemplate (connect+read timeout = 300s) and validates the webhook URL against `ssrf.allowed-hosts` (`localhost,activepieces` default) via `com.platform.common.security.SsrfProtection`. |
| Session memory rebuild | `AiGenerationServiceImpl.callAiWebhook` L452-482 | AP sync webhook is stateless per call, so **every** call carries full prior conversation history (`buildPriorConversationHistory` L396, dedupes the just-saved current user message). If the response *still* looks like "session not found" (`isSessionNotFoundError` L635-657: error/errorCode/message string sniffing), it reloads context+docs+full history from DB and re-sends once. Tests: `src/test/java/com/developer/service/AiSessionMemoryRebuildTest.java`. |
| Resilience | `doCallAiWebhookWithRetry` L581-611 | One retry after 2s sleep for `AI_WEBHOOK_TIMEOUT`/`AI_WEBHOOK_CALL_FAILED`; on second failure throws with `extraData = {lastSuccessTime, degradationOptions: [SAVE_DRAFT, MANUAL_CREATE]}` → surfaced to the UI via the SSE `error` event (ComponentImpl L240-251). `lastAiWebhookSuccessTime` tracked (L81, L584). **No circuit breaker** — just 1 retry + degradation messaging. Empty body ⇒ `AI_WEBHOOK_EMPTY_RESPONSE` (non-retryable, L622). |
| SSE mgmt | `service/impl/AiSseEmitterManager.java` | Chat emitter timeout computed in ServiceImpl L667: `timeoutSeconds*2*1000 + 60s` = 660s; event emitter fixed 300s (`EVENT_EMITTER_TIMEOUT` L28). Superseded-emitter check `isChatEmitterSuperseded`. Tests: `AiSseEmitterManagementTest.java`, `AiSseTimeoutProperties.java`. |
| Exceptions | `exception/AiExceptionHandler.java` | `@Order(HIGHEST_PRECEDENCE)`: lock conflict→409, validation failed→422 (with `errors` detail), `AiGenerationException`→status by code (L89-98): NOT_FOUND codes→404, `AI_CONTEXT_TOO_LARGE`→413, `AI_WEBHOOK_TIMEOUT`→504, `AI_WEBHOOK_CALL_FAILED`/`EMPTY_RESPONSE`→502, else 500. 8-char traceId per error. |
| Validation | `service/impl/AiValidationServiceImpl.java` + `AiStructureValidator`/`AiReferenceValidator`/`AiSecurityValidator` + `AiQualityScorer` | Runs before apply; failures → 422 with error list; warnings ride along in `write_success` event. |
| Locking | `service/impl/AiLockServiceImpl.java` | Per-FU edit lock, TTL 1800s, force-unlock request/response over the event SSE channel (config `ai-generation.lock.*`). |

### 1.4 Request-body contract (verified by property tests)

`buildAiWebhookRequestBody` (`AiGenerationServiceImpl.java:484-527`) always includes: `sessionId`, `message`, `phase`, `mode`, `functionUnitId`, `context` (pre-serialized JSON **string** to dodge AP `[object Object]` rendering, L504-505), `existingDocuments` (plain-text formatted, 50k-char/doc cap, L508-511 + L758-785), `conversationHistory`, `schemaMetadata` (enum lists FormType/TableType/ActionType + configJson extension specs + visibilityCondition operator list + newEntities incl. the "MANY_TO_ONE is NOT valid" instruction, L535-573), `includeExplanations: true`, `regenerateScope` (default `ALL`; enum ALL/TABLES/FORMS/ACTIONS/DECISIONS/PROCESS/TABLE_RELATIONS).

Renamed tests (n8n→webhook terminology migration, uncommitted):
- `src/test/java/com/developer/service/AiWebhookRequestBodyProperties.java` (R100 from `AiN8NRequestBodyProperties`) — jqwik properties: schemaMetadata completeness, `includeExplanations` always true, regenerateScope defaulting.
- `src/test/java/com/developer/service/AiWebhookResilienceProperties.java` (R100 from `AiN8NResilienceProperties`) — success updates `lastAiWebhookSuccessTime`; non-retryable failure leaves it untouched.
- **Note:** `AiN8NWorkflowConfigTest.java` is staged **deleted** with no `AiWebhookConfigTest.java` in the working tree (`git diff --cached --name-status`: `D` … AiN8NWorkflowConfigTest.java). The config-URL test coverage appears dropped, not renamed (the earlier git snapshot showed an RM to `AiWebhookConfigTest.java`, but that file no longer exists).

### 1.5 Config inventory (application.yml)

`backend/developer-workstation/src/main/resources/application.yml:191-208` (+ `application-docker.yml:108-114`):

| Key | Default | Env |
|---|---|---|
| `activepieces.ai-generation.webhook-url` | `http://activepieces:80/api/v1/webhooks/QnU0ytf5oBaxL9rbwOU2Z/sync` | `AI_GENERATION_WEBHOOK_URL` |
| `activepieces.ai-generation.timeout-seconds` | `300` | `AI_GENERATION_TIMEOUT` |
| `ssrf.allowed-hosts` | `localhost,activepieces` (docker: `activepieces` only) | `SSRF_ALLOWED_HOSTS` |
| `ai-generation.lock.ttl-seconds` | `1800` | `AI_GENERATION_LOCK_TTL` |
| `ai-generation.lock.force-unlock-timeout-seconds` | `60` | `AI_GENERATION_FORCE_UNLOCK_TIMEOUT` |
| `ai-generation.context.max-size-bytes` | `102400` | `AI_GENERATION_CONTEXT_MAX_SIZE` |

---

## 2. Frontend AI UI (frontend/developer-workstation) — Status: Confirmed

- **`src/components/ai/AiPanel.vue`** (723 L): slide-in panel (docked or detached/draggable/resizable), header w/ session-history dropdown (status/mode/phase tags), lock-conflict overlay with "request force unlock" button. Body = ChatDialog (left) + DocumentPanel (right). `handleApply` (L514) → `aiGenerationApi.applyGeneratedData` → success toast + `dataApplied` emit (design canvas refresh); `write_success` from the *event* SSE avoids double-toast for the actor (L433-437).
- **`src/components/ai/ChatDialog.vue`** (987 L): message list (`ChatMessage.vue` + `MarkdownRenderer.vue`), phase indicator (`PhaseIndicator.vue`), regenerate-scope selector (L316), GenerationPreview with Apply/Regenerate (L158-159), **undo button with 30s countdown** (L166-173), inline document viewer.
- **`src/components/ai/GenerationPreview.vue`** (568 L): structured preview of generatedData (tables/forms/actions/decisions/relations/process incl. XML tree view via `XmlTreeView.vue`) + quality score; Apply/Regenerate emit up.
- **`src/composables/useAiChat.ts`** (400 L): SSE over **fetch + ReadableStream** (POST SSE; EventSource is GET-only, L58-61). Parses events `session/token/document/phase_complete/generated_data/validation_warning/error/done`; a 6-step generation progress value; retryable error codes `AI_WEBHOOK_TIMEOUT`/`AI_WEBHOOK_CALL_FAILED` (L272); degradation info captured (L277-282); **drafts of generatedData auto-saved to localStorage with 24h expiry** (L14-55, saved on each `generated_data` event L246-254, cleared after apply). Sends `X-User-Id` header from the local user object (L84-93).
- Companion composables: `useAiLock`, `useAiSession`, `useAiEvents` (event SSE), `aiPanel/useAiPanelLayout|Sidebar`, `chatDialog/useChatDialogDraft|Preview`. API base: `src/api/aiGeneration.ts` (`AI_CHAT_STREAM_URL`, apply L31, undo L34).

User-visible flow: open AI panel on a function unit → lock acquired → chat through REQUIREMENTS (doc streamed into DocumentPanel, versioned, user-editable) → DESIGN → GENERATION → structured preview + quality score → Apply (validation errors 422 shown) → 30s undo window → design canvas refreshes.

---

## 3. Activepieces integration — Status: Confirmed (dev end-to-end; k8s manifests ready, cluster-side Partial/unverified)

Docs: `deploy/ACTIVEPIECES_INTEGRATION.md` (401 L, authoritative), `deploy/ACTIVEPIECES_USER_GUIDE.md`.

### 3.1 AP gateway ("shared account bridge", dev :8085)
Non-prod only: platform users enter the AP UI via a **shared AP account** through a cross-domain SSO handshake ("方案 B", 2026-06-30): admin-domain `GET /api/v1/admin/internal/ap/launch` (platform JWT cookie valid there) → server-side shared-account sign-in to AP → one-time Redis nonce (60s TTL) → browser jumps to AP domain `/__ap/bridge#nonce=…` → `GET /__ap/token?nonce=` exchanges for `{token, projectId}` → written to AP's localStorage → user is in as AP ADMIN. No platform cookie ever needed on the AP domain; AP token never in a URL. Backend lives in admin-center `com.admin.ap` (ApTokenController, ApBridgeNonceStore, ActivepiecesApiClient) per `ACTIVEPIECES_INTEGRATION.md` §2-3. K8s: `deploy/k8s/ap-gateway.yaml` (Istio Gateway+VirtualService rewriting `/__ap/bridge`,`/__ap/token` → admin-center-service, rest → activepieces-service; L23-79). Bootstrap: `deploy/k8s/ap-bootstrap-job.yaml` + `deploy/scripts/ap-bootstrap-shared-account.js` — idempotent sign-up **plus** `POST /api/v1/platforms` to finish onboarding (AP 0.84 CE sign-up alone leaves the account in ONBOARDING with `projectId=null`, §6). Prod = **runtime only**: `deploy/k8s/activepieces.yaml` exposes **only** `/api/v1/webhooks` through Istio (L225-246 comment "Webhook-only ingress"), no UI, no shared account.

### 3.2 AP deployment hardening (k8s/activepieces.yaml)
Image `…/activepieces:0.84.0` (L48); `AP_WEBHOOK_TIMEOUT_SECONDS=300` (L121-123, comment ties it to DW's 300s: keep AP ≤ DW so AP answers first — note the memory of "120" is outdated; current manifest says 300); offline policy `AP_PIECES_SYNC_MODE=NONE` + `AP_TELEMETRY_ENABLED=false` (0.84 has no `AP_PIECES_SOURCE` var — the once-configured `AP_PIECES_SOURCE=DB` was never read and has been removed) — piece catalog only from `piece_metadata` table. Approved-piece supply chain: `deploy/pieces/` (whitelist `pieces.json`, metadata seed SQL, tarballs, Dockerfile that pre-installs pieces into the worker's bun workspace — runtime zero-network; `NPM_CONFIG_REGISTRY` fail-closed `.invalid` default in uat/preprod because empty silently falls back to public npm).

### 3.3 Flows under git (`deploy/ap-flows/`, git-as-source publish channel)
- `ai-function-unit-gen.json` — **the** AI generation flow: `Catch Webhook → Code "Build Prompt" (phase prompts + English-output mandate + "data below is authoritative, no external tools" anti-injection preamble + document markers) → piece-ai run_agent (provider=custom, model=deepseek-v4-pro) → Code "Parse Response" (extracts ---REQUIREMENTS_DOC/DESIGN_DOC/GENERATED_DATA/PHASE_COMPLETE--- markers, 3-level JSON salvage: direct parse → \`\`\`json block → first{…last}) → Return Response`.
- `aptest.json` — demo flow for the service-task channel; `deploy/ap-demo/aptest-service-task.bpmn` is the matching demo BPMN.
- Publish pipeline: `deploy/scripts/ap-export.js` / `ap-import.js` (idempotent create→IMPORT_FLOW→LOCK_AND_PUBLISH→CHANGE_STATUS=ENABLED; AP CE has no Git Sync — EE-only, tested 404), Jenkins templates `deploy/ci/Jenkinsfile.ap-flows-export|publish`. Caveats: connections (credentials) never travel with flows — prod must pre-create same-named connections; flowId changes per environment (BPMN `ap:flowId` must be re-pointed). `deploy/pieces/AI Function Unit Generation.json` is a BOM-prefixed editor-export duplicate of the flow (reference copy).

### 3.4 Workflow service tasks → AP (Path B) — Status: Confirmed (dev e2e 2026-06-26)
BPMN service task with extension properties `serviceType=ap`, `ap:flowId`, optional `ap:webhookUrl|inputMapping|outputMapping|timeoutSeconds(120)|retryCount(3)`. At **deploy time** `ProcessDeploymentManager#bindActivepiecesServiceTasks` injects `flowable:delegateExpression=${apTaskExecutor}` (the missing link n8n never had — its designer wrote extension attrs but never a delegate, so the n8n service-task path never actually ran from the designer). Runtime: `com.workflow.component.ApTaskExecutor` (workflow-engine-core) builds `<activepieces.webhook-base-url>/api/v1/webhooks/<flowId>/sync` (SSRF-validated), POSTs synchronously with exponential-backoff retries, records to `wf_ap_execution_record`, maps the Return-Response JSON back to process variables via `ApVariableMappingUtil` (dot-path nesting). Designer UI: `ServiceTaskProperties.vue` type `ap` → `ApTaskPropertiesPanel.vue`. Action-mode REST (`POST /api/v1/ap/execute`) exists backend-side but **has no user-facing UI** (Partial, §11.6).

---

## 4. n8n — Status: Dead (legacy; infra remnants still deployed)

- Engine/service-task channel: **removed** — "n8n 已整体移除，仅 developer-workstation 的 AI 生成 webhook 例外保留" (`ACTIVEPIECES_INTEGRATION.md` §11 preamble), and that exception has itself since been migrated to AP (the `AiN8N*` → `AiWebhook*` test renames in this working tree are the tail end of the cleanup).
- Remaining code traces: enum constant `N8N_ACTION` in `backend/developer-workstation/src/main/java/com/developer/enums/ActionType.java:37` (no runtime handler anywhere — grep of user-portal + workflow-engine-core finds only a comment in `RestTemplateConfig.java:31` and a legacy unit test) → dead vocabulary.
- Remaining infra: `deploy/k8s/n8n.yaml` (image `n8n:1.89.2`) still listed in `deploy/k8s/kustomization.yaml:7`; dev compose still starts `platform-n8n-dev` (`deploy/environments/dev/docker-compose.dev.yml:104-132`). `deploy/n8n-workflows/` keeps two templates: `ai-function-unit-gen-workflow.json` (superseded by the AP flow) and `travel-expense-invoice-recognition.json` (Doubao vision LLM invoice OCR — **no live caller in code**; import/credential steps are manual per its README). Verdict: the n8n container is deployed-but-orphaned; candidates for removal.

---

## 5. Superset / BI — Status: Confirmed in dev, Partial in prod (covered in depth by another agent)

`deploy/superset/` (Dockerfile, `superset_config.py`, `superset_security_manager.py`, `author-proxy/`), `deploy/k8s/workflow-station-superset.yaml`, `deploy/k8s/SUPERSET_SSO_GATEWAY.md`, doc `deploy/SUPERSET_SSO_INTEGRATION.md`. Superset 6.0, unified SSO: dev converged to single FQDN + path `localhost:3000/bi` (Superset reads `SUPERSET_APP_ROOT`); only `/bi/login/` is gated via nginx `auth_request` → admin-center `/internal/bi/superset/authorize` (platform JWT + `bi_rbac_mapping` → `X-Remote-*`/REMOTE_USER injection); all other paths pass with forged-header stripping. Prod still runs the older dual-subdomain split (author gateway via nginx reverse proxy + DENY) and is slated to converge to `/bi`. Not part of the AI request path.

---

## 6. Kong — Status: Confirmed (dedicated SSE route for AI)

`deploy/kong/kong.yml.template:54-65`: a **separate Kong service** `developer-workstation-sse-service` routes `/api/v1/ai-generation` to `developer-workstation:8080` with `write_timeout`/`read_timeout` **300000 ms** (vs. the generic route), sized for the long SSE chat stream. Kong does not sit between DW and Activepieces — the webhook call is direct east-west (`http://activepieces:80`), and external AP webhook exposure in k8s is via Istio (`activepieces.yaml` webhook-only VirtualService), not Kong. Note the Kong read timeout (300s) < chat SSE emitter timeout (660s incl. retry headroom): a webhook call that exhausts timeout+retry (~600s) would have its SSE cut by Kong first — worst case the client sees a dropped stream instead of the structured `error` event.

---

## 7. Risks

1. **Unauthenticated webhook** — AP CE sync webhooks carry no auth ("AP CE webhook 免鉴权，无需 apiKey", `ACTIVEPIECES_INTEGRATION.md` §11.1). In-cluster this is private-network-trust only; anyone who can reach `activepieces:80` can invoke the AI flow (burning LLM tokens) or any service-task flow. In k8s the webhook path is *deliberately* exposed externally (`/api/v1/webhooks` Istio route) for third-party triggers — the AI-gen flow's ID is therefore publicly invokable if the flowId leaks (it is hardcoded in `application.yml:193`).
2. **Secrets** — LLM key (deepseek, Bearer-prefixed custom provider key) lives as an AP *connection* inside AP's encrypted store, not in this repo (good); AP's own `ACTIVEPIECES_{ENCRYPTION_KEY,JWT_SECRET,SHARED_PASSWORD,…}` live in k8s secrets (`secret/{uat,preprod}`). No AI credentials found hardcoded in the repo. Shared-account password immutability is an operational foot-gun (§6 "铁律").
3. **Prompt injection surface** — the request body concatenates *user message*, *stored conversation history*, *existing documents* (previously AI- or user-authored, user-editable via `POST /documents`), and the *function-unit context* (names/descriptions user-controlled) into one agent prompt. The Build-Prompt code adds a "data below is authoritative / you cannot call external tools" preamble, and defense-in-depth exists at the *output* boundary (AiStructureValidator/AiReferenceValidator/AiSecurityValidator + normalization before write, apply gated by `@RequireDeveloperPermission` + workspace access + validation), so injected instructions can at worst distort generated design data the same user then previews and applies. Residual risk is acceptable-by-design but real for markdown rendering of replies (MarkdownRenderer sanitization not audited here).
4. **AP down / slow** — DW: 300s connect/read timeout → `AI_WEBHOOK_TIMEOUT` → 1 retry after 2s → structured SSE `error` with `degradationOptions [SAVE_DRAFT, MANUAL_CREATE]` + `lastSuccessTime`; frontend shows retry button + degradation choices; generated-data drafts survive in localStorage 24h. No circuit breaker/bulkhead: concurrent chats each hold a taskExecutor thread up to ~10 min (timeout×2+2s); engine service tasks similarly block a Flowable thread (RestTemplate read timeout 10 min). A wedged AP degrades thread pools before failing fast.
5. **Timeout chain coherence** — three knobs must stay ordered: AP `AP_WEBHOOK_TIMEOUT_SECONDS` (300) ≤ DW `AI_GENERATION_TIMEOUT` (300) < Kong SSE read timeout (300)… the first two are equal-by-config and the Kong one does *not* cover the retry path (see §6). If AP times out first it returns **204 empty** → surfaces as non-retryable `AI_WEBHOOK_EMPTY_RESPONSE` (misleading error, documented in yml comment).
6. **`X-User-Id` header** — `useAiChat.getAuthHeaders()` sends a client-supplied `X-User-Id`; authorization relies on the JWT-backed `SecurityContextUtils`/permission annotations, but any server code trusting the raw header would be spoofable — worth an audit.
7. **Test coverage gap** — the webhook *config* test was deleted, not renamed (§1.4): no test now pins `activepieces.ai-generation.webhook-url`/timeout wiring.
8. **Session-not-found sniffing** — `isSessionNotFoundError` string-matches "session…not found" in AP responses (`AiGenerationServiceImpl.java:635-657`); an LLM reply echoing that phrase in an error field could trigger a spurious full-history re-call (cost, latency) — low probability, low impact.
