package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import com.portal.dto.SubTableChange;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.exception.PortalException;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.PortalWorkspaceAuthService;
import com.portal.service.ProcessAssigneeSnapshot;
import com.portal.util.BpmnInitiatorTaskDetection;
import com.portal.service.UserDisplayNameResolver;
import com.platform.common.i18n.I18nService;
import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.security.util.SecurityContextUtils;
import com.portal.util.PortalUserSecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pinned process start, first-task submission, and start bookkeeping. Extracted from {@link ProcessComponent}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessStartComponent {

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final WorkflowEngineClient workflowEngineClient;
    private final ChangeHistoryComponent changeHistoryComponent;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MeetingParticipantVariablesPersistence meetingParticipantVariablesPersistence;
    private final ProcessStartFormEnricherComponent processStartFormEnricherComponent;
    private final OwnerFieldComponent ownerFieldComponent;
    private final TaskFormComponent taskFormComponent;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final I18nService i18nService;
    private final PlatformTransactionManager transactionManager;

    @Lazy
    @Autowired
    private ChangeHistorySubmissionFilter changeHistorySubmissionFilter;

    /** Lazy: first-task Case Handler MI lookup; null in {@code new}-constructed tests. */
    @Lazy
    @Autowired
    private MiOuterStepResolver miOuterStepResolver;

    /** Lazy: start writes share Save/Complete isolation; null in {@code new}-constructed tests. */
    @Lazy
    @Autowired
    private SubTableWriteIsolation subTableWriteIsolation;

    private ChangeHistorySubmissionFilter changeHistorySubmissionFilter() {
        ChangeHistorySubmissionFilter filter = changeHistorySubmissionFilter;
        if (filter == null) {
            filter = new ChangeHistorySubmissionFilter(jdbcTemplate, new ObjectMapper());
            changeHistorySubmissionFilter = filter;
        }
        return filter;
    }

    /**
     * Programmatic short transactions for the DB-write phases of a start.
     *
     * <p>
     * {@code startProcess} makes ~4-5 sequential blocking HTTP calls (admin-center
     * FU checks, engine
     * deploy/start/auto-complete). Previously the whole method was
     * {@code @Transactional}, so one JDBC
     * connection was pinned across all of that — capping start throughput at
     * {@code pool / holdTime}
     * (~30-60 TPS on pool=20; thread dumps showed most workers parked in
     * {@code Hikari.borrow} while a
     * few held a connection blocked on an engine/admin socket read). Now the HTTP
     * runs with no ambient
     * transaction and only the actual writes run inside these short
     * {@link TransactionTemplate} blocks,
     * so a connection is held for milliseconds, not the whole flow. Lazily built
     * (Lombok owns the ctor).
     * </p>
     */
    private volatile TransactionTemplate txTemplate;

    @Autowired
    private SubTableBatchAssignmentHistory batchAssignmentHistory;
    @Autowired @Lazy private AssignmentInitialTaskCompletion initialTaskCompletion;

    private TransactionTemplate tx() {
        TransactionTemplate t = txTemplate;
        if (t == null) {
            t = new TransactionTemplate(transactionManager);
            txTemplate = t;
        }
        return t;
    }

    /**
     * Binding-scoped start writes use the same isolation as Task Save/Complete.
     * Transport metadata stays off {@code formData}/engine variables.
     */
    static void stripSubTableTransportMetadata(Map<String, Object> variables) {
        SubTableWriteIsolation.stripTransportMetadata(variables);
    }

    void isolateStartSubTables(Map<String, Object> variables, ProcessStartRequest request, String functionUnitCode) {
        isolateStartSubTables(variables, request, functionUnitCode, null);
    }

    void isolateStartSubTables(Map<String, Object> variables, ProcessStartRequest request,
                              String functionUnitCode, String catalogId) {
        stripSubTableTransportMetadata(variables);
        SubTableWriteIsolation isolation = subTableWriteIsolation;
        if (isolation == null) {
            // Null in {@code new}-constructed tests keeps the previous unscoped start write.
            return;
        }
        if (variables == null || request == null) {
            return;
        }
        isolation.apply(new SubTableWriteIsolation.Request(
                variables,
                variables,
                Map.of(),
                request.getEmptiedSubTableKeys(),
                request.getSubTableBindingScopes(),
                functionUnitCode, catalogId, null));
    }

    /**
     * Lazy: breaks cycle with {@link ProcessComponent}, which keeps the FU content
     * cache.
     */
    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    /**
     * Builds MI collections for sub-processes reached from the auto-completed first task; null in
     * {@code new}-constructed tests, which skips it.
     */
    @Lazy
    @Autowired(required = false)
    private MiCollectionVariableBuilder miCollectionVariableBuilder;

    /** Records the Function Units a request called on submit; null in {@code new}-constructed tests. */
    @Lazy
    @Autowired(required = false)
    private CalledProcessSyncComponent calledProcessSync;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * Starts process
     * Via WorkflowEngineClient calling Flowable engine
     */
    // NOT @Transactional: the ~4-5 engine/admin HTTP calls below must not run while
    // holding a DB
    // connection (that pins the pool and throttles start throughput). DB writes are
    // confined to the
    // short tx() blocks; all HTTP runs connection-free. See txTemplate javadoc.
    public ProcessInstanceInfo startProcess(String userId, String processKey, ProcessStartRequest request) {
        return startProcess(userId, processKey, request, false, false);
    }

    /**
     * Service-to-service start (email monitor). Same enrich / first-task / history path as Portal
     * start, without JWT workspace or per-user Function Unit access checks.
     */
    public ProcessInstanceInfo startProcessFromInternal(
            String userId, String processKey, ProcessStartRequest request) {
        return startProcess(userId, processKey, request, true, true);
    }

    /** CSV is a server producer, not an internal caller: ordinary FU/workspace authorization still applies. */
    public ProcessInstanceInfo startProcessFromServer(String userId, String processKey, ProcessStartRequest request) {
        return startProcess(userId, processKey, request, false, true);
    }

    private ProcessInstanceInfo startProcess(
            String userId, String processKey, ProcessStartRequest request, boolean internal, boolean serverProduced) {
        if (processKey == null || processKey.isEmpty()) {
            throw new IllegalArgumentException("Process key cannot be empty");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }

        com.platform.common.functionunit.RuntimeVersionContext.rejectClientMetadata(request.getFormData());
        ActiveCatalogPin pin = resolveActiveCatalogPin(userId, processKey, internal);

        LoadedStartDefinition def = loadProcessDefinitionForStart(pin, processKey);
        var currentContext = SubTableBatchStartContext.capture(def.catalogContent(), pin, new ObjectMapper());
        SubTableBatchStartContext.validate(request.getBatchStartContext(), currentContext, serverProduced,
                i18nService.getMessage("portal.batch.start_context_changed"));

        // actualProcessKey is the key used to start (Flowable uses BPMN <process id>)
        final DeploymentPin deployment = new DeploymentPin(def.flowableProcessKey(), def.definitionId());

        // Start instance: strip forged workspace keys; UBR users need valid JWT
        // workspace context (matches hasContext)
        Map<String, Object> submittedSnapshot = changeHistorySubmissionFilter().copyPayload(request.getFormData());
        Map<String, Object> variables = changeHistorySubmissionFilter().copyPayload(request.getFormData());
        variables.remove("activeBusinessUnitId");
        variables.remove("activeRoleId");
        applyCatalogContextToVariables(variables, userId, pin.catalogId(), pin.code());
        variables.put("functionUnitVersionLabel", pin.versionLabel());
        // Demo FU fu-20260403-a1b2c5: assign-participants node uses INITIATOR; scripts
        // still read participant_assigner_user_id
        if ("fu-20260403-a1b2c5".equals(pin.code())) {
            variables.put("participant_assigner_user_id", userId);
        }
        if (internal) {
            applyWorkspaceContextForInternalStart(userId, variables);
        } else {
            applyWorkspaceContextVariables(userId, variables);
        }
        processStartFormEnricherComponent.enrichOnInsert(pin.code(), userId, variables);
        isolateStartSubTables(variables, request, pin.code(), pin.catalogId());
        // Build the capability contract before Flowable starts, from this exact
        // catalog response. It stays only in local ProcessInstance.variables.
        Map<String, Object> persistedVariables = withBatchRuntimeSnapshot(variables, def.catalogContent(), pin);
        String startUserDisplayName = userDisplayNameResolver.resolve(userId);
        Map<String, Object> userChanges = changeHistorySubmissionFilter().filterProcessSubmission(
                pin.code(), submittedSnapshot, variables);
        var initialAssignments = SubTableBatchInitialMiPreflight.prepare(
                def.bpmnXml(), deployment.key(), variables, persistedVariables, jdbcTemplate);
        Map<String, Object> data = startPinnedProcess(deployment, request.getBusinessKey(), userId,
                variables);
        if (data == null || data.get("processInstanceId") == null) {
            throw new IllegalStateException("Process start returned empty data: " + processKey);
        }
        String flowableProcessInstanceId = (String) data.get("processInstanceId");
        log.info("Process started via Flowable: {}", flowableProcessInstanceId);
        // TX1 (short): persist instance as RUNNING so ProcessCompletionListener
        // callback finds a row.
        // Committing here (before auto-complete) is stronger than the old single-tx
        // behaviour, where the
        // row was not visible until the whole method committed after all the engine
        // round-trips.
        try {
            tx().executeWithoutResult(status -> persistRunningProcessInstance(
                    flowableProcessInstanceId, data, processKey, def.processName(), request,
                    userId, startUserDisplayName, persistedVariables, pin));
        } catch (RuntimeException persistFailure) {
            boolean compensated = compensateUnpersistedEngineStart(flowableProcessInstanceId);
            log.error("Failed to persist local process instance {} after Flowable start; engine compensationSucceeded={}",
                    flowableProcessInstanceId, compensated, persistFailure);
            throw new IllegalStateException("Could not persist process start; the engine instance was "
                    + (compensated ? "cancelled" : "flagged for operator remediation"), persistFailure);
        }
        // Engine HTTP (claim/complete first task, query next) — runs with NO ambient tx
        // / no connection held.
        // Its own internal writes are individually transactional
        // (captureTaskFormSnapshot is @Transactional;
        // the meeting-path save auto-commits); autoCompleteFirstTask never throws (see
        // its catch).
        boolean leaveFirstTaskOpen = Boolean.TRUE.equals(request.getLeaveFirstTaskOpen());
        FirstTaskOutcome outcome = autoCompleteFirstTask(
                flowableProcessInstanceId, userId, variables, pin, leaveFirstTaskOpen);
        // TX2 (short): change history + current-node update grouped atomically (the
        // node update is a
        // @Modifying query that requires a transaction). First task completed via
        // engine API.
        tx().executeWithoutResult(status -> {
            recordInitialSubmitChangeHistory(
                    flowableProcessInstanceId,
                    outcome.initiatorTaskIdForHistory,
                    outcome.initiatorTaskDefKeyForHistory,
                    userId,
                    userChanges,
                    pin.catalogId());
            updateInstanceNodeAndRecordStartHistory(flowableProcessInstanceId, outcome, userId, startUserDisplayName);
        });
        if (outcome.calledWorkStatus != null) {
            // After the caller's row is final: the called instances' rows link back to it.
            calledProcessSync.recordCalledWork(flowableProcessInstanceId, outcome.calledWorkStatus);
        }

        ProcessInstanceInfo result = buildStartResult(flowableProcessInstanceId, data, processKey, def.processName(), request,
                userId, startUserDisplayName, outcome, pin);
        var assignmentPlan = initialAssignments.isEmpty() ? outcome.assignmentPlan
                : new SubTableBatchInitialMiOutcome.Plan(flowableProcessInstanceId, deployment.definitionId(), initialAssignments);
        var observation = SubTableBatchCompletionObservation.observe(assignmentPlan, workflowEngineClient);
        if (assignmentPlan != null) observation = batchAssignmentHistory.record(assignmentPlan, observation, userId, outcome.initiatorTaskIdForHistory);
        result.setBatchAssignmentVerification(observation);
        return result;
    }

    record ActiveCatalogPin(String catalogId, String code, String versionLabel) {
    }

    record DeploymentPin(String key, String definitionId) { }

    private Map<String, Object> startPinnedProcess(DeploymentPin deployment, String businessKey, String userId,
            Map<String, Object> engineVariables) {
        if (deployment.definitionId() == null || deployment.definitionId().isBlank()) {
            throw new IllegalStateException("Process start requires a verified deployment ID");
        }
        return workflowEngineClient.startProcessWithDeploymentPin(deployment.key(), businessKey, userId,
                engineVariables, deployment.definitionId());
    }

    /**
     * Process name / BPMN XML / deployed process key loaded from FU content for one
     * start request.
     */
    private record LoadedStartDefinition(
            String processName, String bpmnXml, String flowableProcessKey, String definitionId, Map<String, Object> catalogContent) {
    }

    /**
     * Mutable holder mirroring the original {@code startProcess} locals around
     * first-task auto-completion.
     */
    private static final class FirstTaskOutcome {
        String currentNodeName;
        ProcessAssigneeSnapshot nextAssigneeSnapshot = ProcessAssigneeSnapshot.empty();
        String initiatorTaskIdForHistory;
        String initiatorTaskDefKeyForHistory;
        /** Non-null when auto-completion of the first task failed; surfaced to the caller. */
        String firstStepError;
        AssignmentCompletionOrchestrator.Result initialCompletion;
        /** Engine status when the request went straight into a Function Unit call; else null. */
        Map<String, Object> calledWorkStatus;
        /** Server-only scope retained only after the first task actually completes. */
        SubTableBatchInitialMiOutcome.Plan assignmentPlan;
    }

    /**
     * Catalog context required by Send Email delegate and other FU-scoped service
     * tasks.
     */
    static void applyCatalogContextToVariables(
            Map<String, Object> variables, String userId, String catalogId, String catalogCode) {
        variables.put("initiator", userId);
        variables.put("functionUnitId", catalogId);
        variables.put("functionUnitCode", catalogCode);
    }

    /** Engine start payload {@code nextTaskName}; blank or absent → null (no invented step name). */
    static String currentNodeFromStartData(Map<String, Object> data) {
        if (data == null) {
            return null;
        }
        Object raw = data.get("nextTaskName");
        if (!(raw instanceof String name)) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static boolean shouldWriteOutcomeCurrentNode(String currentNodeName) {
        return currentNodeName != null && !currentNodeName.isBlank();
    }

    /**
     * Copies engine variables into the local persistence representation and adds
     * the catalog-pinned batch contract only there. Keeping this boundary explicit
     * prevents a future engine call from accidentally receiving the reserved key.
     */
    static Map<String, Object> withBatchRuntimeSnapshot(
            Map<String, Object> engineVariables, Map<String, Object> catalogContent, ActiveCatalogPin pin) {
        Map<String, Object> persistedVariables = new HashMap<>(engineVariables);
        ObjectMapper mapper = new ObjectMapper();
        SubTableBatchRuntimeSnapshot.capture(catalogContent, pin, mapper).ifPresent(snapshot -> {
            persistedVariables.put(SubTableWriteIsolation.BATCH_RUNTIME_FIELD, snapshot);
            SubTableBatchInitialRows.initialize(persistedVariables, snapshot, mapper);
        });
        return persistedVariables;
    }

    /** The release a new request of {@code processKey} starts on, after the same checks as the start itself. */
    String activeCatalogIdForStart(String userId, String processKey) {
        return resolveActiveCatalogPin(userId, processKey, false).catalogId();
    }

    private ActiveCatalogPin resolveActiveCatalogPin(String userId, String processKey, boolean internal) {
        Optional<ActiveCatalogPin> activePinOpt = fetchActiveCatalogForStart(processKey);
        if (activePinOpt.isEmpty()) {
            throw new IllegalStateException(
                    "No deployed and enabled function unit version available for this process. Please deploy and activate in Admin Center: "
                            + processKey);
        }
        ActiveCatalogPin pin = activePinOpt.get();

        String resolvedFunctionUnitId = functionUnitAccessComponent
                .resolveFunctionUnitIdAlignedWithActiveCatalog(processKey, pin.catalogId());
        if (!pin.catalogId().equals(resolvedFunctionUnitId)) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException(
                    "Portal startable version mismatch. Please refresh the process list and try again");
        }

        if (!internal) {
            functionUnitAccessComponent.checkFunctionUnitAccess(userId, pin.catalogId());
        }
        return pin;
    }

    private LoadedStartDefinition loadProcessDefinitionForStart(ActiveCatalogPin pin, String processKey) {
        Map<String, Object> content = processComponent.getPublishedStartContent(pin.catalogId());
        Object raw = content.get("processes");
        if (!(raw instanceof List<?> processes) || processes.size() != 1
                || !(processes.get(0) instanceof Map<?, ?> process)) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_SNAPSHOT_INCOMPLETE", pin.catalogId());
        }
        Object definitionId = process.get("flowableProcessDefinitionId");
        Object key = process.get("flowableProcessDefinitionKey");
        Object xml = process.get("data");
        if (!(definitionId instanceof String id) || id.isBlank()
                || !(key instanceof String processDefinitionKey) || processDefinitionKey.isBlank()
                || !(xml instanceof String bpmn) || bpmn.isBlank()) {
            throw new com.platform.common.functionunit.FunctionUnitVersionException(
                    "VERSION_DEPLOYMENT_NOT_READY", pin.catalogId());
        }
        return new LoadedStartDefinition(String.valueOf(content.get("name")), bpmn,
                processDefinitionKey, id, content);
    }

    /**
     * Email / internal starts have no JWT. Apply BU only when the initiator has exactly one
     * workspace; multiple memberships are left unset rather than guessed.
     */
    private void applyWorkspaceContextForInternalStart(String userId, Map<String, Object> variables) {
        List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService
                .listWorkspaceContexts(userId);
        if (wctx.size() == 1) {
            String bu = wctx.get(0).getBusinessUnitId();
            if (bu != null && !bu.isBlank()) {
                variables.put("activeBusinessUnitId", bu.trim());
            }
            return;
        }
        if (wctx.size() > 1) {
            log.warn("Internal process start: initiator has {} workspaces; leaving activeBusinessUnitId unset",
                    wctx.size());
        }
    }

    private void applyWorkspaceContextVariables(String userId, Map<String, Object> variables) {
        List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService
                .listWorkspaceContexts(userId);
        boolean jwtUserMatches = SecurityContextUtils.getCurrentUserId().map(uid -> uid.equals(userId)).orElse(false);
        if (!jwtUserMatches && userId != null && !userId.isEmpty()) {
            log.warn("Process start: path userId={} does not match JWT subject={} — active BU will not be applied",
                    userId, SecurityContextUtils.getCurrentUserId().orElse(null));
        }

        if (!wctx.isEmpty()) {
            if (!jwtUserMatches) {
                throw new PortalException("400",
                        "Cannot verify logged-in identity with workspace, please re-login to start process");
            }
            String jwtBu = PortalUserSecurityUtils.getCurrentActiveBusinessUnitId().orElse("").trim();
            String jwtRole = PortalUserSecurityUtils.getCurrentActiveRoleId().orElse("").trim();
            if (jwtBu.isEmpty() || jwtRole.isEmpty()) {
                throw new PortalException("400",
                        "Your account is associated with a business unit role. Please login first and select a workspace from the top, or re-login and try again");
            }
            if (!portalWorkspaceAuthService.hasContext(userId, jwtBu, jwtRole)) {
                throw new PortalException("400",
                        "Current workspace identity has expired (permissions may have been adjusted), please switch workspace or re-login and try again");
            }
            variables.put("activeBusinessUnitId", jwtBu);
        } else {
            if (jwtUserMatches) {
                PortalUserSecurityUtils.getCurrentActiveBusinessUnitId()
                        .map(String::trim)
                        .filter(bu -> !bu.isEmpty())
                        .ifPresent(bu -> variables.put("activeBusinessUnitId", bu));
            }
        }
    }

    private void persistRunningProcessInstance(
            String flowableProcessInstanceId, Map<String, Object> data, String processKey, String processName,
            ProcessStartRequest request, String userId, String startUserDisplayName,
            Map<String, Object> variables, ActiveCatalogPin pin) {
        ProcessInstance processInstance = ProcessInstance.builder()
                .id(flowableProcessInstanceId)
                .processInstanceId(flowableProcessInstanceId)
                .processDefinitionId((String) data.get("processDefinitionId"))
                .processDefinitionKey(processKey)
                .processDefinitionName(processName)
                .businessKey(request.getBusinessKey())
                .initiatorId(userId)
                .startUserId(userId)
                .startUserName(startUserDisplayName)
                .status("RUNNING")
                .currentNode(currentNodeFromStartData(data))
                .currentAssignee(null)
                .variables(variables)
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();
        processInstanceRepository.save(processInstance);
        log.info("Process instance pre-saved to local database: {}", flowableProcessInstanceId);
    }

    /**
     * A Flowable start precedes the local ProcessInstance insert. If that insert
     * fails, remove the engine instance synchronously so it cannot become an
     * orphan that later hydrates against a newer catalog definition.
     */
    private boolean compensateUnpersistedEngineStart(String processInstanceId) {
        try {
            if (workflowEngineClient.cancelProcessInstance(processInstanceId,
                    "Portal ProcessInstance persistence failed").isPresent()) {
                return true;
            }
        } catch (RuntimeException ex) {
            log.error("Engine cancellation failed for unpersisted process instance {}", processInstanceId, ex);
        }
        try {
            // Last-resort cleanup is intentionally scoped to the just-created ID.
            return workflowEngineClient.purgeProcessInstance(processInstanceId);
        } catch (RuntimeException ex) {
            log.error("Engine purge failed for unpersisted process instance {}", processInstanceId, ex);
            return false;
        }
    }

    private FirstTaskOutcome autoCompleteFirstTask(
            String flowableProcessInstanceId,
            String userId,
            Map<String, Object> variables,
            ActiveCatalogPin pin,
            boolean leaveFirstTaskOpen) {
        // Auto-complete only when the first user task is a true initiator / start-form
        // node.
        // Approval-first flows (BU_ROLE, INITIATOR_BU_ROLE, etc.) must stay open for
        // assignees.
        FirstTaskOutcome outcome = new FirstTaskOutcome();

        try {
            Optional<Map<String, Object>> tasksResult = workflowEngineClient
                    .getProcessInstanceTasks(flowableProcessInstanceId);
            if (tasksResult.isPresent()) {
                Map<String, Object> tasksData = tasksResult.get();
                if (tasksData != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                    if (tasks != null && !tasks.isEmpty()) {
                        Map<String, Object> firstTask = tasks.get(0);
                        if (leaveFirstTaskOpen) {
                            captureFirstTaskWithoutAutoComplete(outcome, firstTask);
                        } else if (BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(firstTask)) {
                            completeFirstTaskAndCaptureNext(
                                    outcome, firstTask, flowableProcessInstanceId, userId, variables, pin);
                        } else {
                            captureFirstTaskWithoutAutoComplete(outcome, firstTask);
                        }
                    } else {
                        log.warn("No tasks found for process instance: {}", flowableProcessInstanceId);
                    }
                }
            } else {
                log.warn("Failed to get tasks for process instance: {}", flowableProcessInstanceId);
            }
        } catch (Exception e) {
            log.warn("Failed to auto-complete first task: {}", e.getMessage());
            // Do not throw: the instance already exists. Preserve the original first-step
            // error when completion has not succeeded; do not turn it into a green submit.
            // A retained batch plan proves engine completion succeeded. Its post-commit
            // observation will warn FAILED/UNCONFIRMED; never invite a duplicate submit.
            if (outcome.initialCompletion == null && outcome.assignmentPlan == null && outcome.firstStepError == null) {
                outcome.firstStepError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            }
        }
        return outcome;
    }

    private void captureFirstTaskWithoutAutoComplete(FirstTaskOutcome outcome, Map<String, Object> firstTask) {
        outcome.currentNodeName = (String) firstTask.get("taskName");
        outcome.nextAssigneeSnapshot = ProcessAssigneeSnapshot.fromEngineTask(firstTask);
        log.info(
                "Skipping auto-complete for first approval task: node={}, assignee={}, candidates={}, bpmnAssigneeType={}",
                outcome.currentNodeName,
                outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                outcome.nextAssigneeSnapshot.getCandidateUserIds(),
                firstTask.get("bpmnAssigneeType"));
    }

    private void completeFirstTaskAndCaptureNext(
            FirstTaskOutcome outcome, Map<String, Object> firstTask, String flowableProcessInstanceId,
            String userId, Map<String, Object> variables, ActiveCatalogPin pin) {
        String taskId = (String) firstTask.get("taskId");
        outcome.initiatorTaskIdForHistory = taskId;
        Object taskDefKey = firstTask.get("taskDefinitionKey");
        outcome.initiatorTaskDefKeyForHistory = taskDefKey != null ? taskDefKey.toString() : null;
        log.info("Auto-completing first task: {} for process: {}", taskId, flowableProcessInstanceId);

        var accepted = initialTaskCompletion.complete(taskId, userId);
        if (accepted.isPresent()) {
            var result = accepted.get();
            outcome.initialCompletion = result;
            outcome.assignmentPlan = result.observation();
            captureNextTaskAfterAutoComplete(outcome, flowableProcessInstanceId);
            return;
        }
        initialTaskCompletion.claimIfUnassigned(firstTask, userId);

        Object firstTaskDefKeyObj = firstTask.get("taskDefinitionKey");
        String firstTaskDefKey = firstTaskDefKeyObj != null ? firstTaskDefKeyObj.toString() : "";
        if ("Task_CreateMeeting".equals(firstTaskDefKey)) {
            meetingParticipantVariablesPersistence.persistIfApplicable(
                    flowableProcessInstanceId, variables, pin.code());
            processInstanceRepository.findById(flowableProcessInstanceId).ifPresent(pi -> {
                Map<String, Object> mergedVariables = new HashMap<>();
                if (pi.getVariables() != null) {
                    mergedVariables.putAll(pi.getVariables());
                }
                mergedVariables.putAll(variables);
                pi.setVariables(mergedVariables);
                processInstanceRepository.save(pi);
            });
        }

        // User field may deserialize as { userId } without id; Map#toString can exceed
        // Flowable varchar(255)
        coerceUserRefVariablesForEngine(variables);

        // Compute sub-table condition variables (e.g. requestItemsHasHighValue) and
        // inject
        computeSubTableConditionVariables(variables);

        var assignmentPlan = injectMiCollectionsForFirstTask(firstTask, firstTaskDefKey, flowableProcessInstanceId, pin, variables);

        // Complete first task
        Optional<Map<String, Object>> completeResult = workflowEngineClient.completeTask(
                taskId, userId, "SUBMIT", variables);
        String failure = firstStepErrorOf(completeResult);
        if (failure == null) {
            outcome.assignmentPlan = assignmentPlan;
            log.info("First task completed successfully: {}", taskId);
            // §6.3: this node's Case Handler must hold the actual operator *before* the
            // snapshot freezes. The initiator's own first step is a Complete too, and the
            // next-task assignee write below only touches live data — without this the
            // completed node's snapshot keeps whatever Case Handler held at submit time.
            applyOwnerOnFirstTaskComplete(
                    flowableProcessInstanceId, (String) firstTask.get("taskName"), userId, variables);
            taskFormComponent.captureTaskFormSnapshot(
                    taskId, userId, firstTaskDefKey, flowableProcessInstanceId, variables);

            // After first task, query current task (next approval node)
            captureNextTaskAfterAutoComplete(outcome, flowableProcessInstanceId);
        } else {
            log.warn("Failed to complete first task: {} — {}", taskId, failure);
            outcome.firstStepError = failure;
        }
    }

    /**
     * The collection of an MI sub-process this submission leads into, as a To Do completion would
     * build it ({@link TaskApprovalCompletionComponent}).
     *
     * <p>Submitting completes the first task here, not through the To Do path, so an MI
     * sub-process reached from it — directly, or after Function Unit calls, which cannot set this
     * process' variables — used to get no collection, and the request failed with "Variable
     * '...collection' was not found" when the token reached it. Legacy prepared collections remain;
     * enhanced instances must instead consume the server-validated frozen assignment collection.
     */
    private SubTableBatchInitialMiOutcome.Plan injectMiCollectionsForFirstTask(Map<String, Object> firstTask, String firstTaskDefKey,
                                                 String processInstanceId, ActiveCatalogPin pin,
                                                 Map<String, Object> variables) {
        ProcessInstance instance = processInstanceRepository.findById(processInstanceId)
                .orElseThrow(() -> new IllegalStateException("Process instance is missing before first-task completion"));
        Map<String, Object> baseline = instance.getVariables();
        if (baseline == null) throw new IllegalStateException("Process variables are missing before first-task completion");
        boolean enhanced = baseline.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD);
        if (miCollectionVariableBuilder == null || firstTaskDefKey == null || firstTaskDefKey.isBlank()) {
            if (enhanced) throw new IllegalStateException("Frozen first-task assignment context is missing");
            return null;
        }
        Object keyObj = firstTask.get("processDefinitionKey");
        String processKey = keyObj != null && !keyObj.toString().isBlank() ? keyObj.toString() : pin.code();
        Map<String, Object> withCollections = new HashMap<>(variables);
        if (enhanced) {
            var expected = miCollectionVariableBuilder.injectMiCollectionForFirstTask(
                    processKey, firstTaskDefKey, processInstanceId, withCollections, baseline);
            variables.putAll(withCollections);
            return expected.isEmpty() ? null : new SubTableBatchInitialMiOutcome.Plan(
                    processInstanceId, instance.getProcessDefinitionId(), expected);
        } else {
            miCollectionVariableBuilder.injectMiCollectionFromBpmn(
                    processKey, firstTaskDefKey, processInstanceId, withCollections);
            withCollections.forEach(variables::putIfAbsent);
            return null;
        }
    }

    /**
     * Writes the actual operator into MAIN {@code CASE_HANDLER} for the auto-completed
     * initiator task (§3.3.3 / §6.3), so the node's {@code _snapshot_{taskId}} freezes
     * who submitted it rather than an empty handler.
     */
    private void applyOwnerOnFirstTaskComplete(String flowableProcessInstanceId, String firstTaskName,
                                               String userId, Map<String, Object> variables) {
        processInstanceRepository.findById(flowableProcessInstanceId).ifPresent(instance -> {
            MiOuterStepResolver.OuterLookup miLookup = miOuterStepResolver == null
                    ? MiOuterStepResolver.OuterLookup.unknown()
                    : miOuterStepResolver.lookup(instance, firstTaskName);
            ownerFieldComponent.applyOnComplete(
                    instance.getFunctionUnitCode(), variables, userId, miLookup);
        });
    }

    /** Opaque marker returned to the browser; the real reason stays in the server log. */
    static final String FIRST_STEP_NOT_COMPLETED = "FIRST_STEP_NOT_COMPLETED";

    /**
     * Whether the first task failed to auto-complete: {@code null} when it completed, otherwise the
     * engine's reason — <b>for logging only</b>, see {@link #FIRST_STEP_NOT_COMPLETED}.
     *
     * <p>The instance exists and the task is back in the initiator's To Do, so this is recoverable
     * and {@code /start} must not fail — but it is not a successful submission either. The caller
     * needs to know so it can say "created, first step did not complete" instead of "submitted
     * successfully"; a WARN in the log is invisible to whoever clicked Submit.
     */
    static String firstStepErrorOf(Optional<Map<String, Object>> completeResult) {
        if (completeResult.isEmpty() || completeResult.get() == null) {
            return "no response from workflow engine";
        }
        Map<String, Object> body = completeResult.get();
        if (!Boolean.FALSE.equals(body.get("success"))) {
            return null;
        }
        Object message = body.get("message");
        String text = message != null ? message.toString().trim() : "";
        return text.isEmpty() ? "workflow engine rejected task completion" : text;
    }

    private void captureNextTaskAfterAutoComplete(FirstTaskOutcome outcome, String flowableProcessInstanceId) {
        Optional<Map<String, Object>> nextTasksResult = workflowEngineClient
                .getProcessInstanceTasks(flowableProcessInstanceId);
        if (nextTasksResult.isPresent()) {
            Map<String, Object> nextTasksData = nextTasksResult.get();
            if (nextTasksData != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> nextTasks = (List<Map<String, Object>>) nextTasksData.get("tasks");
                if (nextTasks != null && !nextTasks.isEmpty()) {
                    Map<String, Object> currentTask = nextTasks.get(0);
                    outcome.currentNodeName = (String) currentTask.get("taskName");
                    outcome.nextAssigneeSnapshot = ProcessAssigneeSnapshot.fromEngineTask(currentTask);
                    log.info("Current task after auto-complete: node={}, assignee={}, candidates={}",
                            outcome.currentNodeName,
                            outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                            outcome.nextAssigneeSnapshot.getCandidateUserIds());
                    return;
                }
            }
        }
        captureCalledWorkAfterAutoComplete(outcome, flowableProcessInstanceId);
    }

    /**
     * The request has no task of its own after submit when it went straight into a Function Unit
     * call: its current step is then the called unit's task, which the engine's status reports.
     */
    private void captureCalledWorkAfterAutoComplete(FirstTaskOutcome outcome, String flowableProcessInstanceId) {
        if (calledProcessSync == null) {
            return;
        }
        Optional<Map<String, Object>> status = workflowEngineClient.getProcessInstanceStatus(flowableProcessInstanceId);
        if (status.isEmpty() || !(status.get().get("nextTaskName") instanceof String nextTaskName)) {
            return;
        }
        outcome.currentNodeName = nextTaskName;
        outcome.nextAssigneeSnapshot = new ProcessAssigneeSnapshot(
                (String) status.get().get("nextAssignee"),
                (String) status.get().get("nextCandidateUsers"));
        outcome.calledWorkStatus = status.get();
        log.info("Request {} waits on a called Function Unit: node={}, assignee={}",
                flowableProcessInstanceId, nextTaskName, outcome.nextAssigneeSnapshot.getAssigneeUserId());
    }

    private void updateInstanceNodeAndRecordStartHistory(
            String flowableProcessInstanceId, FirstTaskOutcome outcome, String userId, String startUserDisplayName) {
        if (outcome.initialCompletion == null) writeOutcomeCurrentNodeIfPresent(flowableProcessInstanceId, outcome);
        ProcessHistory startHistory = ProcessHistory.builder()
                .processInstanceId(flowableProcessInstanceId)
                .activityId("startEvent")
                .activityName(i18nService.getMessage("portal.process.history.submit_application"))
                .activityType("startEvent")
                .operationType("SUBMIT")
                .operatorId(userId)
                .operatorName(startUserDisplayName)
                .comment(i18nService.getMessage("portal.process.history.start_comment"))
                .build();
        processHistoryRepository.save(startHistory);
    }

    /**
     * Conditional UPDATE avoids overwriting COMPLETED set by ProcessCompletionListener.
     * Native update bypasses a stale JPA L1 cache. A null outcome node must not overwrite
     * the start-time current_node (email start cannot query tasks without a JWT).
     */
    private void writeOutcomeCurrentNodeIfPresent(String flowableProcessInstanceId, FirstTaskOutcome outcome) {
        if (!shouldWriteOutcomeCurrentNode(outcome.currentNodeName)) {
            return;
        }
        int updated = processInstanceRepository.updateCurrentNodeAndAssigneesIfNotCompleted(
                flowableProcessInstanceId,
                outcome.currentNodeName,
                outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                outcome.nextAssigneeSnapshot.getCandidateUserIds());
        if (updated <= 0) {
            log.info("Process instance {} already COMPLETED, skipped currentNode update (race condition avoided)",
                    flowableProcessInstanceId);
            return;
        }
        log.info("Process instance updated in local database: {} with currentNode={}, assignee={}, candidates={}",
                flowableProcessInstanceId, outcome.currentNodeName,
                outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                outcome.nextAssigneeSnapshot.getCandidateUserIds());
        processInstanceRepository.findById(flowableProcessInstanceId).ifPresent(instance -> {
            Map<String, Object> vars = instance.getVariables() == null
                    ? new HashMap<>()
                    : new HashMap<>(instance.getVariables());
            MiOuterStepResolver.OuterLookup miLookup = miOuterStepResolver == null
                    ? MiOuterStepResolver.OuterLookup.unknown()
                    : miOuterStepResolver.lookup(instance, outcome.currentNodeName);
            ownerFieldComponent.applyAssigneeSnapshot(
                    instance.getFunctionUnitCode(),
                    vars,
                    outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                    outcome.nextAssigneeSnapshot.getCandidateUserIds(),
                    instance.getStatus(),
                    miLookup);
            instance.setVariables(vars);
            processInstanceRepository.save(instance);
        });
    }

    private ProcessInstanceInfo buildStartResult(
            String flowableProcessInstanceId, Map<String, Object> data, String processKey, String processName,
            ProcessStartRequest request, String userId, String startUserDisplayName,
            FirstTaskOutcome outcome, ActiveCatalogPin pin) {
        Map<String, String> startAssigneeCache = userDisplayNameResolver.resolveBatch(
                userDisplayNameResolver.collectAssigneeUserKeys(
                        outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                        outcome.nextAssigneeSnapshot.getCandidateUserIds()));
        String startAssigneeDisplay = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                outcome.nextAssigneeSnapshot.getCandidateUserIds(),
                startAssigneeCache);

        return ProcessInstanceInfo.builder()
                .id(flowableProcessInstanceId)
                .processDefinitionId((String) data.get("processDefinitionId"))
                .processDefinitionKey(processKey)
                .processDefinitionName(processName)
                .businessKey(request.getBusinessKey())
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .startUserId(userId)
                .startUserName(startUserDisplayName)
                .currentNode(outcome.currentNodeName)
                .currentAssignee(startAssigneeDisplay)
                .candidateUsers(outcome.nextAssigneeSnapshot.getCandidateUserIds())
                // Opaque on purpose: the engine's text names the AP sync-webhook URL, and an AP CE
                // webhook URL *is* the credential (unauthenticated, reachable through Kong at
                // /api/ap/*). Handing it to every initiator would let them fire the automation
                // directly. The full reason is in the WARN log above.
                .firstStepError(outcome.firstStepError != null ? FIRST_STEP_NOT_COMPLETED : null)
                .initialSubmissionOperationId(outcome.initialCompletion == null ? null : outcome.initialCompletion.operationId())
                .initialSubmissionState(outcome.initialCompletion == null ? null : outcome.initialCompletion.state())
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();
    }

    /**
     * Fetches highest semantic-version deployed+enabled catalog row for code (same
     * as /deployed/latest)
     */
    private Optional<ActiveCatalogPin> fetchActiveCatalogForStart(String code) {
        try {
            String enc = URLEncoder.encode(code, StandardCharsets.UTF_8);
            String url = adminCenterUrl + "/api/v1/admin/function-units/code/" + enc + "/active-for-start";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response.getBody());
            String id = (String) payload.get("id");
            if (id == null || id.isEmpty()) {
                return Optional.empty();
            }
            Object ver = payload.get("version");
            String versionLabel = ver != null ? String.valueOf(ver) : "";
            String c = (String) payload.get("code");
            return Optional.of(new ActiveCatalogPin(id, c != null ? c : code, versionLabel));
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            log.warn("fetchActiveCatalogForStart HTTP error for {}: {}", code, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("fetchActiveCatalogForStart failed for {}: {}", code, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Extracts <process id="..."> from BPMN XML
     */
    private String extractProcessIdFromBpmn(String bpmnXml) {
        if (bpmnXml == null)
            return null;
        try {
            int processTagIdx = bpmnXml.indexOf("<process ");
            if (processTagIdx == -1) {
                processTagIdx = bpmnXml.indexOf("<bpmn:process ");
            }
            if (processTagIdx == -1)
                return null;
            int tagEnd = bpmnXml.indexOf('>', processTagIdx);
            if (tagEnd == -1)
                return null;
            String tag = bpmnXml.substring(processTagIdx, tagEnd + 1);
            return ProcessStartAssigneeResolver.extractAttribute(tag, "id");
        } catch (Exception e) {
            log.warn("Failed to extract process id from BPMN: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Writes start-form variables to up_change_history for application detail
     * change-history display.
     * Records only user-visible form fields; excludes engine/snapshot internal keys
     * to reduce noise.
     */
    private void recordInitialSubmitChangeHistory(String processInstanceId,
            String taskInstanceId,
            String taskDefinitionKey,
            String userId,
            Map<String, Object> userChanges,
            String catalogId) {
        if (userChanges == null || userChanges.isEmpty()) {
            return;
        }
        ChangeHistoryContext context = ChangeHistoryContext.builder()
                .processInstanceId(processInstanceId)
                .taskInstanceId(taskInstanceId)
                .stageId(taskDefinitionKey)
                .userId(userId)
                .build();
        // Record top-level field changes
        try {
            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : userChanges.entrySet()) {
                String k = e.getKey();
                if ("__subTables__".equals(k)) {
                    continue; // handled separately below
                }
                filtered.put(k, e.getValue());
            }
            if (!filtered.isEmpty()) {
                changeHistoryComponent.recordFieldChanges(context, Collections.emptyMap(), filtered);
            }
        } catch (Exception e) {
            log.warn("Failed to record initial submit change history for process {}: {}",
                    processInstanceId, e.getMessage());
        }
        // Record sub-table (subform) rows submitted during process initiation as
        // ROW_ADD.
        // Text-key aliases are preferred; when only numeric binding IDs are present,
        // merge
        // all rows into one comparison keyed by the first binding ID to avoid
        // duplication.
        try {
            Object subTablesObj = userChanges.get("__subTables__");
            if (subTablesObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
                // Separate numeric (binding ID) keys from text (table name) keys
                List<String> numericKeys = new ArrayList<>();
                List<String> textKeys = new ArrayList<>();
                for (String key : subTables.keySet()) {
                    if (key.matches("\\d+")) {
                        numericKeys.add(key);
                    } else {
                        textKeys.add(key);
                    }
                }
                if (!textKeys.isEmpty()) {
                    // Text keys exist — record only those (normalization merges aliases, dedup
                    // handles duplicates)
                    for (String subTableKey : textKeys) {
                        recordStartSubTableAdds(context, subTables, subTableKey, catalogId);
                    }
                } else if (!numericKeys.isEmpty()) {
                    // Only numeric keys — merge all rows and record under one virtual name
                    List<SubTableChange> allChanges = new ArrayList<>();
                    java.util.Set<Object> seen = new java.util.HashSet<>();
                    for (String subTableKey : numericKeys) {
                        Object rowsObj = subTables.get(subTableKey);
                        if (!(rowsObj instanceof List<?>))
                            continue;
                        for (Object row : (List<?>) rowsObj) {
                            if (!(row instanceof Map<?, ?>))
                                continue;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> rowMap = (Map<String, Object>) row;
                            // Identity comes from the key this slice's table declares, resolved per
                            // slice — a table keyed by `correspondence_id` is matched by no list of
                            // likely column names.
                            String rowId = ChangeHistoryComponent.resolveRowIdentifier(rowMap,
                                    changeHistoryComponent.pinnedPrimaryKeyFieldsForSliceKey(
                                            catalogId, subTableKey));
                            if (rowId != null && seen.add(rowId)) {
                                allChanges.add(SubTableChange.builder()
                                        .changeType("ROW_ADD")
                                        .rowIdentifier(rowId)
                                        .oldValues(null)
                                        .newValues(new HashMap<>(rowMap))
                                        .build());
                            }
                        }
                    }
                    if (!allChanges.isEmpty()) {
                        changeHistoryComponent.recordSubTableChangesWithName(
                                context, numericKeys.get(0), allChanges);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record initial sub-table change history for process {}: {}",
                    processInstanceId, e.getMessage());
        }
    }

    private void recordStartSubTableAdds(ChangeHistoryContext context,
            Map<String, Object> subTables, String subTableKey, String catalogId) {
        Object rowsObj = subTables.get(subTableKey);
        if (!(rowsObj instanceof List<?>))
            return;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;
        if (rows.isEmpty())
            return;
        List<SubTableChange> changes = new ArrayList<>();
        // Resolved once per slice: identity is the primary key this table declares, not a column
        // whose name happens to look like one.
        List<String> pkFields = changeHistoryComponent.pinnedPrimaryKeyFieldsForSliceKey(
                catalogId, subTableKey);
        for (Map<String, Object> row : rows) {
            String rowId = ChangeHistoryComponent.resolveRowIdentifier(row, pkFields);
            changes.add(SubTableChange.builder()
                    .changeType("ROW_ADD")
                    .rowIdentifier(rowId)
                    .oldValues(null)
                    .newValues(new HashMap<>(row))
                    .build());
        }
        changeHistoryComponent.recordSubTableChanges(context, subTableKey, changes);
    }

    /**
     * Coerces portal form "user" objects (id / userId / user_id / value) to plain
     * strings so workflow-engine
     * ASSIGNEE_FROM_VARIABLE parse failures or oversized identifiers.
     */
    private void coerceUserRefVariablesForEngine(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        String[] keys = { "participant_assigner_user_id" };
        for (String key : keys) {
            Object v = variables.get(key);
            if (!(v instanceof Map<?, ?> m)) {
                continue;
            }
            String uid = null;
            for (String k : new String[] { "id", "userId", "user_id", "value" }) {
                Object part = m.get(k);
                if (part != null) {
                    String s = String.valueOf(part).trim();
                    if (!s.isEmpty()) {
                        uid = s;
                        break;
                    }
                }
            }
            if (uid != null) {
                if (uid.length() > 255) {
                    log.warn("coerceUserRefVariablesForEngine: {} value longer than 255 chars, skip coerce", key);
                } else {
                    variables.put(key, uid);
                }
            }
        }
    }

    /**
     * Computes sub-table condition variables from form data and injects them for
     * gateway conditions.
     * Supported variables:
     * requestItemsHasHighValue — any row with total_price > 10000 (Boolean)
     * totalPrice — sum of total_price (Double, for BPMN comparisons)
     * maxItemPrice — max total_price across rows (Double)
     * itemCount — sub-table row count (Integer)
     */
    @SuppressWarnings("unchecked")
    private void computeSubTableConditionVariables(Map<String, Object> variables) {
        try {
            Object subTablesObj = variables.get("__subTables__");
            if (!(subTablesObj instanceof Map)) {
                variables.put("requestItemsHasHighValue", false);
                variables.put("totalPrice", 0.0);
                variables.put("maxItemPrice", 0.0);
                variables.put("itemCount", 0);
                log.info("[PriceCheck] No __subTables__ found, all price variables set to 0/false");
                return;
            }
            Map<String, Object> subTables = (Map<String, Object>) subTablesObj;

            boolean hasHighValue = false;
            double totalPrice = 0.0;
            double maxItemPrice = 0.0;
            int itemCount = 0;

            for (Object tableData : subTables.values()) {
                if (!(tableData instanceof List))
                    continue;
                List<Object> rows = (List<Object>) tableData;
                for (Object rowObj : rows) {
                    if (!(rowObj instanceof Map))
                        continue;
                    Map<String, Object> row = (Map<String, Object>) rowObj;
                    itemCount++;

                    // Support both snake_case and camelCase field names for total_price
                    Object priceVal = row.get("total_price");
                    if (priceVal == null)
                        priceVal = row.get("totalPrice");
                    if (priceVal == null)
                        priceVal = row.get("total_Price");
                    // Also check unit_price as fallback
                    if (priceVal == null)
                        priceVal = row.get("unit_price");
                    if (priceVal == null)
                        priceVal = row.get("unitPrice");
                    if (priceVal == null)
                        continue;

                    double price = 0;
                    if (priceVal instanceof Number) {
                        price = ((Number) priceVal).doubleValue();
                    } else {
                        try {
                            price = Double.parseDouble(priceVal.toString());
                        } catch (NumberFormatException e) {
                            log.debug("Failed to parse price value: {}", priceVal);
                        }
                    }

                    totalPrice += price;
                    if (price > maxItemPrice) {
                        maxItemPrice = price;
                    }
                    if (price > 10000) {
                        hasHighValue = true;
                    }
                }
            }

            variables.put("requestItemsHasHighValue", hasHighValue);
            variables.put("totalPrice", totalPrice);
            variables.put("maxItemPrice", maxItemPrice);
            variables.put("itemCount", itemCount);
            log.info("[PriceCheck] requestItemsHasHighValue={}, totalPrice={}, maxItemPrice={}, itemCount={}",
                    hasHighValue, totalPrice, maxItemPrice, itemCount);
        } catch (Exception e) {
            log.warn("[PriceCheck] Failed to compute price condition variables: {}", e.getMessage());
            variables.put("requestItemsHasHighValue", false);
            variables.put("totalPrice", 0.0);
            variables.put("maxItemPrice", 0.0);
            variables.put("itemCount", 0);
        }
    }
}
