package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
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
import org.springframework.transaction.annotation.Transactional;
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
import java.util.Set;

/**
 * Process start flow: active catalog pin resolution, BPMN deployment, engine start,
 * first-task auto-completion and start bookkeeping. Extracted from {@link ProcessComponent}
 * (the original 330-line {@code startProcess} body is decomposed into sequential steps
 * with unchanged order and logic).
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
    private final TaskFormComponent taskFormComponent;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final I18nService i18nService;

    /** Lazy: breaks cycle with {@link ProcessComponent}, which keeps the FU content cache. */
    @Lazy
    @Autowired
    private ProcessComponent processComponent;

    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * Starts process
     * Via WorkflowEngineClient calling Flowable engine
     */
    @Transactional
    public ProcessInstanceInfo startProcess(String userId, String processKey, ProcessStartRequest request) {
        if (processKey == null || processKey.isEmpty()) {
            throw new IllegalArgumentException("Process key cannot be empty");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("User ID cannot be empty");
        }

        ActiveCatalogPin pin = resolveActiveCatalogPin(userId, processKey);

        LoadedStartDefinition def = loadProcessDefinitionForStart(pin, processKey);

        // actualProcessKey is the key used to start (Flowable uses BPMN <process id>)
        final String actualProcessKey = deployAndResolveProcessKey(processKey, def);

        // Start instance: strip forged workspace keys; UBR users need valid JWT workspace context (matches hasContext)
        Map<String, Object> variables = request.getFormData() != null ? new HashMap<>(request.getFormData()) : new HashMap<>();
        variables.remove("activeBusinessUnitId");
        variables.remove("activeRoleId");
        applyCatalogContextToVariables(variables, userId, pin.catalogId(), pin.code());
        // Demo FU fu-20260403-a1b2c5: assign-participants node uses INITIATOR; scripts still read participant_assigner_user_id
        if ("fu-20260403-a1b2c5".equals(pin.code())) {
            variables.put("participant_assigner_user_id", userId);
        }

        applyWorkspaceContextVariables(userId, variables);

        Map<String, Object> data = workflowEngineClient.startProcess(
                actualProcessKey, request.getBusinessKey(), userId, variables);
        if (data == null || data.get("processInstanceId") == null) {
            throw new IllegalStateException("Process start returned empty data: " + processKey);
        }

        String flowableProcessInstanceId = (String) data.get("processInstanceId");
        log.info("Process started via Flowable: {}", flowableProcessInstanceId);

        // Persist process instance as RUNNING so ProcessCompletionListener callback finds a row
        String startUserDisplayName = userDisplayNameResolver.resolve(userId);
        persistRunningProcessInstance(
                flowableProcessInstanceId, data, processKey, def.processName(), request,
                userId, startUserDisplayName, variables, pin);

        FirstTaskOutcome outcome = autoCompleteFirstTask(flowableProcessInstanceId, userId, variables, pin);

        // First task completed via engine API (not TaskFormComponent); record field change history like todo submit
        recordInitialSubmitChangeHistory(
                flowableProcessInstanceId,
                outcome.initiatorTaskIdForHistory,
                outcome.initiatorTaskDefKeyForHistory,
                userId,
                variables);

        updateInstanceNodeAndRecordStartHistory(flowableProcessInstanceId, outcome, userId, startUserDisplayName);

        return buildStartResult(flowableProcessInstanceId, data, processKey, def.processName(), request,
                userId, startUserDisplayName, outcome, pin);
    }

    record ActiveCatalogPin(String catalogId, String code, String versionLabel) {}

    /** Process name / BPMN XML / deployed process key loaded from FU content for one start request. */
    private record LoadedStartDefinition(String processName, String bpmnXml, String flowableProcessKey) {}

    /** Mutable holder mirroring the original {@code startProcess} locals around first-task auto-completion. */
    private static final class FirstTaskOutcome {
        String currentNodeName;
        ProcessAssigneeSnapshot nextAssigneeSnapshot = ProcessAssigneeSnapshot.empty();
        String initiatorTaskIdForHistory;
        String initiatorTaskDefKeyForHistory;
    }

    /** Catalog context required by Send Email delegate and other FU-scoped service tasks. */
    static void applyCatalogContextToVariables(
            Map<String, Object> variables, String userId, String catalogId, String catalogCode) {
        variables.put("initiator", userId);
        variables.put("functionUnitId", catalogId);
        variables.put("functionUnitCode", catalogCode);
    }

    private ActiveCatalogPin resolveActiveCatalogPin(String userId, String processKey) {
        Optional<ActiveCatalogPin> activePinOpt = fetchActiveCatalogForStart(processKey);
        if (activePinOpt.isEmpty()) {
            throw new IllegalStateException(
                    "No deployed and enabled function unit version available for this process. Please deploy and activate in Admin Center: " + processKey);
        }
        ActiveCatalogPin pin = activePinOpt.get();

        String resolvedFunctionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(processKey);
        if (!pin.catalogId().equals(resolvedFunctionUnitId)) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException(
                    "Portal startable version mismatch. Please refresh the process list and try again");
        }

        functionUnitAccessComponent.checkFunctionUnitAccess(userId, pin.catalogId());
        return pin;
    }

    private LoadedStartDefinition loadProcessDefinitionForStart(ActiveCatalogPin pin, String processKey) {
        // Load process definition name and BPMN XML
        String processName = processKey;
        String bpmnXml = null;

        // Prefer deployed process definition key from API response (most reliable).
        // admin-center API returns this in ProcessContentDTO.flowableProcessDefinitionKey.
        String flowableProcessKey = null;

        try {
            Map<String, Object> content = processComponent.getFunctionUnitContent(pin.catalogId());
            if (content != null) {
                if (content.get("name") != null) {
                    processName = (String) content.get("name");
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> processes = (List<Map<String, Object>>) content.get("processes");
                if (processes != null && !processes.isEmpty()) {
                    // Read deployed process key from API response (most reliable)
                    Object flowableKeyObj = processes.get(0).get("flowableProcessDefinitionKey");
                    if (flowableKeyObj != null) {
                        flowableProcessKey = flowableKeyObj.toString();
                    }
                    Object dataObj = processes.get(0).get("data");
                    bpmnXml = (String) dataObj;
                }
            }
        } catch (FunctionUnitAccessComponent.FunctionUnitDisabledException |
                 FunctionUnitAccessComponent.FunctionUnitAccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get process info for {}: {}", processKey, e.getMessage(), e);
        }

        if (bpmnXml == null) {
            throw new IllegalStateException("Cannot obtain process definition BPMN: " + processKey);
        }
        return new LoadedStartDefinition(processName, bpmnXml, flowableProcessKey);
    }

    private String deployAndResolveProcessKey(String processKey, LoadedStartDefinition def) {
        // Check Flowable engine availability
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Workflow engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to start process: {}", processKey);

        String flowableProcessKey = def.flowableProcessKey();
        // If API lacks flowableProcessDefinitionKey, extract from BPMN XML
        if (flowableProcessKey == null || flowableProcessKey.isEmpty()) {
            flowableProcessKey = extractProcessIdFromBpmn(def.bpmnXml());
            if (flowableProcessKey == null || flowableProcessKey.isEmpty()) {
                throw new IllegalStateException(
                        i18nService.getMessage("portal.process.bpmn_process_id_missing", processKey));
            }
        }

        // Deploy process definition if not already deployed
        Optional<Map<String, Object>> deployResult = workflowEngineClient.deployProcess(processKey, def.bpmnXml(), def.processName());
        if (deployResult.isPresent()) {
            Map<String, Object> deployed = deployResult.get();
            log.info("Process definition deployed: {}", deployed);
            // deployResult processDefinitionKey from Flowable response, validated
            Object deployedKey = deployed.get("processDefinitionKey");
            if (deployedKey != null && !deployedKey.toString().isEmpty()) {
                flowableProcessKey = deployedKey.toString();
                log.info("Using actual process definition key from deployment response: [{}]", flowableProcessKey);
            }
        } else {
            throw new IllegalStateException("Process deployment returned empty data: " + processKey);
        }
        return flowableProcessKey;
    }

    private void applyWorkspaceContextVariables(String userId, Map<String, Object> variables) {
        List<PortalWorkspaceAuthService.WorkspaceContextRow> wctx = portalWorkspaceAuthService.listWorkspaceContexts(userId);
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
                .currentNode(null)
                .currentAssignee(null)
                .variables(variables)
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();
        processInstanceRepository.save(processInstance);
        log.info("Process instance pre-saved to local database: {}", flowableProcessInstanceId);
    }

    private FirstTaskOutcome autoCompleteFirstTask(
            String flowableProcessInstanceId, String userId, Map<String, Object> variables, ActiveCatalogPin pin) {
        // Auto-complete only when the first user task is a true initiator / start-form node.
        // Approval-first flows (BU_ROLE, INITIATOR_BU_ROLE, etc.) must stay open for assignees.
        FirstTaskOutcome outcome = new FirstTaskOutcome();

        try {
            Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(flowableProcessInstanceId);
            if (tasksResult.isPresent()) {
                Map<String, Object> tasksData = tasksResult.get();
                if (tasksData != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                    if (tasks != null && !tasks.isEmpty()) {
                        Map<String, Object> firstTask = tasks.get(0);
                        if (BpmnInitiatorTaskDetection.shouldAutoCompleteFirstTask(firstTask)) {
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
            // Do not throw; process already started successfully
        }
        return outcome;
    }

    private void captureFirstTaskWithoutAutoComplete(FirstTaskOutcome outcome, Map<String, Object> firstTask) {
        outcome.currentNodeName = (String) firstTask.get("taskName");
        outcome.nextAssigneeSnapshot = ProcessAssigneeSnapshot.fromEngineTask(firstTask);
        log.info("Skipping auto-complete for first approval task: node={}, assignee={}, candidates={}, bpmnAssigneeType={}",
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

        // Claim task first (set assignee)
        Optional<Map<String, Object>> claimResult = workflowEngineClient.claimTask(taskId, userId);
        if (claimResult.isPresent()) {
            log.info("First task claimed successfully: {} by user: {}", taskId, userId);
        } else {
            log.warn("Failed to claim first task: {}, trying to complete anyway", taskId);
        }

        Object firstTaskDefKeyObj = firstTask.get("taskDefinitionKey");
        String firstTaskDefKey = firstTaskDefKeyObj != null ? firstTaskDefKeyObj.toString() : "";
        if ("Task_CreateMeeting".equals(firstTaskDefKey)) {
            meetingParticipantVariablesPersistence.persistIfApplicable(
                    flowableProcessInstanceId, variables, pin.code());
            processInstanceRepository.findById(flowableProcessInstanceId).ifPresent(pi -> {
                pi.setVariables(new HashMap<>(variables));
                processInstanceRepository.save(pi);
            });
        }

        // User field may deserialize as { userId } without id; Map#toString can exceed Flowable varchar(255)
        coerceUserRefVariablesForEngine(variables);

        // Compute sub-table condition variables (e.g. requestItemsHasHighValue) and inject
        computeSubTableConditionVariables(variables);

        // Complete first task
        Optional<Map<String, Object>> completeResult = workflowEngineClient.completeTask(
                taskId, userId, "SUBMIT", variables);
        if (completeResult.isPresent()
                && !Boolean.FALSE.equals(completeResult.get().get("success"))) {
            log.info("First task completed successfully: {}", taskId);
            taskFormComponent.captureTaskFormSnapshot(
                    taskId, userId, firstTaskDefKey, flowableProcessInstanceId, variables);

            // After first task, query current task (next approval node)
            captureNextTaskAfterAutoComplete(outcome, flowableProcessInstanceId);
        } else {
            if (completeResult.isPresent() && Boolean.FALSE.equals(completeResult.get().get("success"))) {
                Object em = completeResult.get().get("message");
                log.warn("Failed to complete first task (engine): {} — {}", taskId, em);
            } else {
                log.warn("Failed to complete first task: {}", taskId);
            }
        }
    }

    private void captureNextTaskAfterAutoComplete(FirstTaskOutcome outcome, String flowableProcessInstanceId) {
        Optional<Map<String, Object>> nextTasksResult = workflowEngineClient.getProcessInstanceTasks(flowableProcessInstanceId);
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
                }
            }
        }
    }

    private void updateInstanceNodeAndRecordStartHistory(
            String flowableProcessInstanceId, FirstTaskOutcome outcome, String userId, String startUserDisplayName) {
        // Update process instance (current node and assignee)
        // Conditional UPDATE avoids overwriting COMPLETED set by ProcessCompletionListener (race)
        // JPA L1 cache stale findById — use @Modifying native update to bypass cache
        int updated = processInstanceRepository.updateCurrentNodeAndAssigneesIfNotCompleted(
                flowableProcessInstanceId,
                outcome.currentNodeName,
                outcome.nextAssigneeSnapshot.getAssigneeUserId(),
                outcome.nextAssigneeSnapshot.getCandidateUserIds());
        if (updated > 0) {
            log.info("Process instance updated in local database: {} with currentNode={}, assignee={}, candidates={}",
                    flowableProcessInstanceId, outcome.currentNodeName,
                    outcome.nextAssigneeSnapshot.getAssigneeUserId(), outcome.nextAssigneeSnapshot.getCandidateUserIds());
        } else {
            log.info("Process instance {} already COMPLETED, skipped currentNode update (race condition avoided)",
                    flowableProcessInstanceId);
        }

        // Record process start history
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
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();
    }

    /**
     * Fetches highest semantic-version deployed+enabled catalog row for code (same as /deployed/latest)
     */
    private Optional<ActiveCatalogPin> fetchActiveCatalogForStart(String code) {
        try {
            String enc = URLEncoder.encode(code, StandardCharsets.UTF_8);
            String url = adminCenterUrl + "/api/v1/admin/function-units/code/" + enc + "/active-for-start";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {});
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
        if (bpmnXml == null) return null;
        try {
            int processTagIdx = bpmnXml.indexOf("<process ");
            if (processTagIdx == -1) {
                processTagIdx = bpmnXml.indexOf("<bpmn:process ");
            }
            if (processTagIdx == -1) return null;
            int tagEnd = bpmnXml.indexOf('>', processTagIdx);
            if (tagEnd == -1) return null;
            String tag = bpmnXml.substring(processTagIdx, tagEnd + 1);
            return ProcessStartAssigneeResolver.extractAttribute(tag, "id");
        } catch (Exception e) {
            log.warn("Failed to extract process id from BPMN: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Writes start-form variables to up_change_history for application detail change-history display.
     * Records only user-visible form fields; excludes engine/snapshot internal keys to reduce noise.
     */
    private void recordInitialSubmitChangeHistory(String processInstanceId,
                                                String taskInstanceId,
                                                String taskDefinitionKey,
                                                String userId,
                                                Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
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
            // Resolve actual form field names from form definitions; only record those.
            Set<String> formFieldNames = resolveFormFieldNames(variables);

            Map<String, Object> filtered = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                String k = e.getKey();
                if ("__subTables__".equals(k)) {
                    continue; // handled separately below
                }
                if (!formFieldNames.isEmpty() && !formFieldNames.contains(k)) {
                    continue;
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

        // Record sub-table (subform) rows submitted during process initiation as ROW_ADD
        try {
            Object subTablesObj = variables.get("__subTables__");
            if (subTablesObj instanceof Map<?, ?>) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
                for (Map.Entry<String, Object> subTableEntry : subTables.entrySet()) {
                    String subTableKey = subTableEntry.getKey();
                    Object rowsObj = subTableEntry.getValue();
                    if (!(rowsObj instanceof List<?>)) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> rows = (List<Map<String, Object>>) rowsObj;
                    if (rows.isEmpty()) {
                        continue;
                    }
                    List<SubTableChange> changes = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        String rowId = ChangeHistoryComponent.resolveRowIdentifier(row);
                        changes.add(SubTableChange.builder()
                                .changeType("ROW_ADD")
                                .rowIdentifier(rowId)
                                .oldValues(null)
                                .newValues(new HashMap<>(row))
                                .build());
                    }
                    changeHistoryComponent.recordSubTableChanges(context, subTableKey, changes);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to record initial sub-table change history for process {}: {}",
                    processInstanceId, e.getMessage());
        }
    }

    /**
     * Extract user-visible form field names from the PROCESS form configJson for a given function unit code.
     * Returns an empty set on failure (caller falls back to ChangeHistoryComponent blacklist).
     */
    private Set<String> resolveFormFieldNames(Map<String, Object> variables) {
        Set<String> fields = new HashSet<>();
        ObjectMapper mapper = new ObjectMapper();
        try {
            String sql = """
                    SELECT fd.config_json::text AS config_json
                    FROM dw_form_definitions fd
                    WHERE fd.form_type = 'PROCESS'
                    """;
            List<String> configs = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("config_json"));
            for (String raw : configs) {
                if (raw == null || raw.isBlank()) continue;
                try {
                    Map<String, Object> config = mapper.readValue(raw, new TypeReference<>() {});
                    extractFieldNames(config, fields);
                } catch (Exception ignored) { /* skip malformed */ }
            }
        } catch (Exception e) {
            log.debug("resolveFormFieldNames failed: {}", e.getMessage());
        }
        return fields;
    }

    @SuppressWarnings("unchecked")
    private void extractFieldNames(Map<String, Object> config, Set<String> fields) {
        Object rule = config.get("rule");
        if (rule instanceof List<?> rules) {
            for (Object item : rules) {
                if (item instanceof Map<?, ?> ruleItem) {
                    Object field = ruleItem.get("field");
                    if (field instanceof String f && !f.isBlank()) {
                        fields.add(f);
                    }
                }
            }
        }
        Object subForms = config.get("subForms");
        if (subForms instanceof Map<?, ?> subMap) {
            for (Object subConfig : subMap.values()) {
                if (subConfig instanceof Map<?, ?> sc) {
                    extractFieldNames((Map<String, Object>) sc, fields);
                }
            }
        }
    }

    /**
     * Coerces portal form "user" objects (id / userId / user_id / value) to plain strings so workflow-engine
     * ASSIGNEE_FROM_VARIABLE parse failures or oversized identifiers.
     */
    private void coerceUserRefVariablesForEngine(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        String[] keys = {"participant_assigner_user_id"};
        for (String key : keys) {
            Object v = variables.get(key);
            if (!(v instanceof Map<?, ?> m)) {
                continue;
            }
            String uid = null;
            for (String k : new String[]{"id", "userId", "user_id", "value"}) {
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
     * Computes sub-table condition variables from form data and injects them for gateway conditions.
     * Supported variables:
     *   requestItemsHasHighValue — any row with total_price > 10000 (Boolean)
     *   totalPrice               — sum of total_price (Double, for BPMN comparisons)
     *   maxItemPrice             — max total_price across rows (Double)
     *   itemCount                — sub-table row count (Integer)
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
                if (!(tableData instanceof List)) continue;
                List<Object> rows = (List<Object>) tableData;
                for (Object rowObj : rows) {
                    if (!(rowObj instanceof Map)) continue;
                    Map<String, Object> row = (Map<String, Object>) rowObj;
                    itemCount++;

                    // Support both snake_case and camelCase field names for total_price
                    Object priceVal = row.get("total_price");
                    if (priceVal == null) priceVal = row.get("totalPrice");
                    if (priceVal == null) priceVal = row.get("total_Price");
                    // Also check unit_price as fallback
                    if (priceVal == null) priceVal = row.get("unit_price");
                    if (priceVal == null) priceVal = row.get("unitPrice");
                    if (priceVal == null) continue;

                    double price = 0;
                    if (priceVal instanceof Number) {
                        price = ((Number) priceVal).doubleValue();
                    } else {
                        try { price = Double.parseDouble(priceVal.toString()); } catch (NumberFormatException e) { log.debug("Failed to parse price value: {}", priceVal); }
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
