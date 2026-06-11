package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portal.client.WorkflowEngineClient;
import com.portal.util.SubTableNestingSanitizer;
import com.portal.dto.ChangeHistoryContext;
import com.portal.dto.SubTableChange;
import com.portal.exception.PortalException;
import com.portal.dto.ProcessDefinitionInfo;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import com.portal.entity.FavoriteProcess;
import com.portal.entity.ProcessDraft;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.entity.ActionDefinition;
import com.portal.repository.FavoriteProcessRepository;
import com.portal.repository.ProcessDraftRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.repository.ActionDefinitionRepository;
import com.portal.service.PortalWorkspaceAuthService;
import com.portal.service.ProcessAssigneeSnapshot;
import com.portal.service.UserDisplayNameResolver;
import com.platform.common.i18n.I18nService;
import com.platform.common.jdbc.PostgresPhysicalTablePrimaryKeys;
import com.platform.common.jdbc.SubTablePhysicalColumnResolver;
import com.platform.common.jdbc.SubTableRowKeySupport;
import com.platform.common.util.ApiResponseBodyUnwrap;
import com.platform.security.util.SecurityContextUtils;
import com.portal.util.PortalUserSecurityUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessComponent {

    private final FavoriteProcessRepository favoriteProcessRepository;
    private final ProcessDraftRepository processDraftRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessHistoryRepository processHistoryRepository;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    private final WorkflowEngineClient workflowEngineClient;
    private final ProcessDraftComponent processDraftComponent;
    private final ChangeHistoryComponent changeHistoryComponent;
    private final PortalWorkspaceAuthService portalWorkspaceAuthService;
    private final RestTemplate restTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final MeetingParticipantVariablesPersistence meetingParticipantVariablesPersistence;
    private final TaskFormComponent taskFormComponent;
    private final UserDisplayNameResolver userDisplayNameResolver;
    private final I18nService i18nService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * In-memory cache for function unit content payloads (forms + BPMN + data tables).
     * Key: resolved functionUnitId. TTL: 5 minutes (matching FunctionUnitAccessComponent).
     * Same process key → same content for all tasks, so caching eliminates repeated
     * admin-center HTTP round-trips when users navigate between To Do detail pages.
     */
    private final Map<String, CachedFuContent> fuContentCache = Collections.synchronizedMap(
            new LinkedHashMap<>(32, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedFuContent> eldest) {
                    return size() > 100;
                }
            });

    private static final long FU_CONTENT_CACHE_TTL_MS = java.util.concurrent.TimeUnit.MINUTES.toMillis(5);

    private record CachedFuContent(Map<String, Object> payload, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > FU_CONTENT_CACHE_TTL_MS;
        }
    }

    // ==================== Process definitions and start ====================

    /**
     * Returns startable process definitions
     * Loads deployed function units from admin center and filters by business role
     */
    public List<ProcessDefinitionInfo> getAvailableProcessDefinitions(String userId, String category, String keyword) {
        log.info("Getting available process definitions for user: {}", userId);
        List<ProcessDefinitionInfo> definitions = new ArrayList<>();
        
        try {
            // Fetch deployed function units from admin center
            String url = adminCenterUrl + "/api/v1/admin/function-units/deployed/latest";
            log.info("Fetching latest deployed function units from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                List<Map<String, Object>> units = ApiResponseBodyUnwrap.normalizeToListOfMaps(response);
                
                if (!units.isEmpty()) {
                    log.info("Got {} deployed function units", units.size());
                
                    // Filter function units by user business roles
                    List<Map<String, Object>> accessibleUnits = functionUnitAccessComponent.filterAccessibleFunctionUnits(userId, units);
                    log.info("After filtering, {} function units are accessible to user {}", accessibleUnits.size(), userId);
                
                    for (Map<String, Object> unit : accessibleUnits) {
                        ProcessDefinitionInfo info = ProcessDefinitionInfo.builder()
                                .id((String) unit.get("id"))
                                .key((String) unit.get("code"))
                                .name((String) unit.get("name"))
                                .description((String) unit.get("description"))
                                .category(i18nService.getMessage("portal.process.category.business"))
                                .version(unit.get("version") != null ? String.valueOf(unit.get("version")) : "1.0.0")
                                .icon((String) unit.get("iconSvg"))
                                .build();
                        definitions.add(info);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch deployed function units from admin center: {}", e.getMessage(), e);
        }
        
        // Return empty list when none accessible (no mock data)
        // Frontend shows no-available-processes message
        if (definitions.isEmpty()) {
            log.info("No accessible process definitions found for user: {}", userId);
        }

        // Filter
        if (category != null && !category.isEmpty()) {
            definitions.removeIf(d -> !d.getCategory().equals(category));
        }
        if (keyword != null && !keyword.isEmpty()) {
            final String kw = keyword;
            definitions.removeIf(d -> {
                String n = d.getName();
                String desc = d.getDescription();
                boolean nameMatch = n != null && n.contains(kw);
                boolean descMatch = desc != null && desc.contains(kw);
                return !nameMatch && !descMatch;
            });
        }

        // Mark favorites
        List<FavoriteProcess> favorites = favoriteProcessRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        Set<String> favoriteKeys = new HashSet<>();
        favorites.forEach(f -> favoriteKeys.add(f.getProcessDefinitionKey()));
        definitions.forEach(d -> d.setIsFavorite(favoriteKeys.contains(d.getKey())));

        return definitions;
    }

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

        // Load process definition name and BPMN XML
        String processName = processKey;
        String bpmnXml = null;
        
        // Prefer deployed process definition key from API response (most reliable).
        // admin-center API returns this in ProcessContentDTO.flowableProcessDefinitionKey.
        String flowableProcessKey = null;

        try {
            Map<String, Object> content = getFunctionUnitContent(pin.catalogId());
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

        // Check Flowable engine availability
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Workflow engine unavailable, please check if workflow-engine-core service is running");
        }

        log.info("Using Flowable engine to start process: {}", processKey);
        
        // If API lacks flowableProcessDefinitionKey, extract from BPMN XML
        if (flowableProcessKey == null || flowableProcessKey.isEmpty()) {
            flowableProcessKey = extractProcessIdFromBpmn(bpmnXml);
            if (flowableProcessKey == null || flowableProcessKey.isEmpty()) {
                throw new IllegalStateException(
                        i18nService.getMessage("portal.process.bpmn_process_id_missing", processKey));
            }
        }

        // Deploy process definition if not already deployed
        Optional<Map<String, Object>> deployResult = workflowEngineClient.deployProcess(processKey, bpmnXml, processName);
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

        // actualProcessKey is the key used to start (Flowable uses BPMN <process id>)
        final String actualProcessKey = flowableProcessKey;

        // Start instance: strip forged workspace keys; UBR users need valid JWT workspace context (matches hasContext)
        Map<String, Object> variables = request.getFormData() != null ? new HashMap<>(request.getFormData()) : new HashMap<>();
        variables.remove("activeBusinessUnitId");
        variables.remove("activeRoleId");
        variables.put("initiator", userId);
        // Demo FU fu-20260403-a1b2c5: assign-participants node uses INITIATOR; scripts still read participant_assigner_user_id
        if ("fu-20260403-a1b2c5".equals(pin.code())) {
            variables.put("participant_assigner_user_id", userId);
        }

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

        Map<String, Object> data = workflowEngineClient.startProcess(
                actualProcessKey, request.getBusinessKey(), userId, variables);
        if (data == null || data.get("processInstanceId") == null) {
            throw new IllegalStateException("Process start returned empty data: " + processKey);
        }

        String flowableProcessInstanceId = (String) data.get("processInstanceId");
        log.info("Process started via Flowable: {}", flowableProcessInstanceId);
        
        // Persist process instance as RUNNING so ProcessCompletionListener callback finds a row
        String startUserDisplayName = userDisplayNameResolver.resolve(userId);
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
        
        // Auto-complete first task (initiator task)
        // After start, first task is usually initiator form — auto-complete to reach next approval
        String currentNodeName = null;
        ProcessAssigneeSnapshot nextAssigneeSnapshot = ProcessAssigneeSnapshot.empty();
        String initiatorTaskIdForHistory = null;
        String initiatorTaskDefKeyForHistory = null;

        try {
            // Query tasks for process instance
            Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(flowableProcessInstanceId);
            if (tasksResult.isPresent()) {
                Map<String, Object> tasksData = tasksResult.get();
                if (tasksData != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                    if (tasks != null && !tasks.isEmpty()) {
                        // Take first task
                        Map<String, Object> firstTask = tasks.get(0);
                        String taskId = (String) firstTask.get("taskId");
                        initiatorTaskIdForHistory = taskId;
                        Object taskDefKey = firstTask.get("taskDefinitionKey");
                        initiatorTaskDefKeyForHistory = taskDefKey != null ? taskDefKey.toString() : null;
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
                            Optional<Map<String, Object>> nextTasksResult = workflowEngineClient.getProcessInstanceTasks(flowableProcessInstanceId);
                            if (nextTasksResult.isPresent()) {
                                Map<String, Object> nextTasksData = nextTasksResult.get();
                                if (nextTasksData != null) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> nextTasks = (List<Map<String, Object>>) nextTasksData.get("tasks");
                                    if (nextTasks != null && !nextTasks.isEmpty()) {
                                        Map<String, Object> currentTask = nextTasks.get(0);
                                        currentNodeName = (String) currentTask.get("taskName");
                                        nextAssigneeSnapshot = ProcessAssigneeSnapshot.fromEngineTask(currentTask);
                                        log.info("Current task after auto-complete: node={}, assignee={}, candidates={}",
                                                currentNodeName,
                                                nextAssigneeSnapshot.getAssigneeUserId(),
                                                nextAssigneeSnapshot.getCandidateUserIds());
                                    }
                                }
                            }
                        } else {
                            if (completeResult.isPresent() && Boolean.FALSE.equals(completeResult.get().get("success"))) {
                                Object em = completeResult.get().get("message");
                                log.warn("Failed to complete first task (engine): {} — {}", taskId, em);
                            } else {
                                log.warn("Failed to complete first task: {}", taskId);
                            }
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

        // First task completed via engine API (not TaskFormComponent); record field change history like todo submit
        recordInitialSubmitChangeHistory(
                flowableProcessInstanceId,
                initiatorTaskIdForHistory,
                initiatorTaskDefKeyForHistory,
                userId,
                variables);

        // Update process instance (current node and assignee)
        // Conditional UPDATE avoids overwriting COMPLETED set by ProcessCompletionListener (race)
        // JPA L1 cache stale findById — use @Modifying native update to bypass cache
        int updated = processInstanceRepository.updateCurrentNodeAndAssigneesIfNotCompleted(
                flowableProcessInstanceId,
                currentNodeName,
                nextAssigneeSnapshot.getAssigneeUserId(),
                nextAssigneeSnapshot.getCandidateUserIds());
        if (updated > 0) {
            log.info("Process instance updated in local database: {} with currentNode={}, assignee={}, candidates={}",
                    flowableProcessInstanceId, currentNodeName,
                    nextAssigneeSnapshot.getAssigneeUserId(), nextAssigneeSnapshot.getCandidateUserIds());
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
        
        Map<String, String> startAssigneeCache = userDisplayNameResolver.resolveBatch(
                userDisplayNameResolver.collectAssigneeUserKeys(
                        nextAssigneeSnapshot.getAssigneeUserId(),
                        nextAssigneeSnapshot.getCandidateUserIds()));
        String startAssigneeDisplay = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                nextAssigneeSnapshot.getAssigneeUserId(),
                nextAssigneeSnapshot.getCandidateUserIds(),
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
                .currentNode(currentNodeName)
                .currentAssignee(startAssigneeDisplay)
                .candidateUsers(nextAssigneeSnapshot.getCandidateUserIds())
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();
    }

    private record ActiveCatalogPin(String catalogId, String code, String versionLabel) {}

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
            return extractAttribute(tag, "id");
        } catch (Exception e) {
            log.warn("Failed to extract process id from BPMN: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parses BPMN XML for first approval user task (skips initiator task)
     */
    private Map<String, String> parseFirstUserTask(String bpmnXml, Map<String, Object> formData, String initiatorId) {
        Map<String, String> result = new HashMap<>();
        log.info("Parsing BPMN XML for first user task, initiatorId: {}", initiatorId);
        log.info("BPMN XML length: {}", bpmnXml != null ? bpmnXml.length() : 0);
        
        try {
            // Find all userTask tags
            int searchStart = 0;
            int taskCount = 0;
            
            while (true) {
                int userTaskStart = bpmnXml.indexOf("<userTask", searchStart);
                if (userTaskStart == -1) {
                    userTaskStart = bpmnXml.indexOf("<bpmn:userTask", searchStart);
                }
                
                if (userTaskStart == -1) {
                    break;
                }
                
                // Locate full userTask element including children
                int userTaskEnd = findClosingTag(bpmnXml, userTaskStart, "userTask");
                if (userTaskEnd == -1) {
                    userTaskEnd = findClosingTag(bpmnXml, userTaskStart, "bpmn:userTask");
                }
                if (userTaskEnd == -1) {
                    // Self-closing tag
                    userTaskEnd = bpmnXml.indexOf("/>", userTaskStart);
                    if (userTaskEnd == -1) {
                        break;
                    }
                    userTaskEnd += 2;
                }
                
                String userTaskElement = bpmnXml.substring(userTaskStart, userTaskEnd);
                taskCount++;
                
                // Extract task name
                String name = extractAttribute(userTaskElement, "name");
                
                // Parse custom:properties (aligned with DW designer and workflow-engine listeners)
                String taskDefKey = extractAttribute(userTaskElement, "id");
                String assigneeType = extractCustomProperty(userTaskElement, "assigneeType");
                String assigneeValue = extractCustomProperty(userTaskElement, "assigneeValue");
                String assigneeAnchor = extractCustomProperty(userTaskElement, "assigneeAnchor");
                String assigneeVariableExt = extractCustomProperty(userTaskElement, "assigneeVariable");
                String manualAssignVariable = extractCustomProperty(userTaskElement, "manualAssignVariable");
                String assignee = null;
                String candidateUsers = null;
                
                if (assigneeType != null) {
                    log.info("Found assigneeType: {} for task: {}", assigneeType, name);
                    
                    String normalizedType = assigneeType.toUpperCase(Locale.ROOT);
                    switch (normalizedType) {
                        case "INITIATOR":
                        case "PROCESS_INITIATOR":
                            assignee = initiatorId;
                            break;
                        case "ENTITY_MANAGER":
                            if (isLastTaskAssigneeAnchor(assigneeAnchor)) {
                                result.put("assigneeType", assigneeType);
                                result.put("requiresClaim", "true");
                            } else {
                                assignee = getEntityManager(initiatorId);
                            }
                            break;
                        case "FUNCTION_MANAGER":
                        case "FUNCTIONAL_MANAGER":
                            if (isLastTaskAssigneeAnchor(assigneeAnchor)) {
                                result.put("assigneeType", assigneeType);
                                result.put("requiresClaim", "true");
                            } else {
                                assignee = getFunctionManager(initiatorId);
                            }
                            break;
                        case "HIERARCHY_ROLE":
                        case "BU_ROLE":
                        case "FIXED_BU_ROLE":
                        case "CURRENT_BU_ROLE":
                        case "CURRENT_PARENT_BU_ROLE":
                        case "INITIATOR_BU_ROLE":
                        case "INITIATOR_PARENT_BU_ROLE":
                            result.put("assigneeType", assigneeType);
                            result.put("requiresClaim", "true");
                            break;
                        case "MANUAL_ASSIGN":
                            result.put("assigneeType", assigneeType);
                            String userVar = (manualAssignVariable != null && !manualAssignVariable.isBlank())
                                    ? manualAssignVariable.trim()
                                    : "manualAssignee_" + (taskDefKey != null ? taskDefKey : "");
                            if (formData != null && formData.containsKey(userVar)) {
                                Object v = formData.get(userVar);
                                if (v != null) {
                                    assignee = firstUserIdFromCommaList(String.valueOf(v).trim());
                                }
                            }
                            if (assignee == null) {
                                result.put("requiresClaim", "true");
                            }
                            break;
                        case "ASSIGNEE_FROM_VARIABLE":
                            result.put("assigneeType", assigneeType);
                            if (assigneeVariableExt != null && !assigneeVariableExt.isBlank()
                                    && formData != null && formData.containsKey(assigneeVariableExt.trim())) {
                                Object v = formData.get(assigneeVariableExt.trim());
                                if (v != null) {
                                    assignee = firstUserIdFromCommaList(String.valueOf(v).trim());
                                }
                            }
                            if (assignee == null) {
                                result.put("requiresClaim", "true");
                            }
                            break;
                        case "ELEMENT_VARIABLE":
                            result.put("assigneeType", assigneeType);
                            result.put("requiresClaim", "true");
                            break;
                        case "BU_UNBOUNDED_ROLE":
                            result.put("assigneeType", assigneeType);
                            result.put("requiresClaim", "true");
                            break;
                        case "DEPT_OTHERS":
                            result.put("assigneeType", "DEPT_OTHERS");
                            result.put("requiresClaim", "true");
                            break;
                        case "PARENT_DEPT":
                            result.put("assigneeType", "PARENT_DEPT");
                            result.put("requiresClaim", "true");
                            break;
                        case "FIXED_DEPT":
                            result.put("assigneeType", "FIXED_DEPT");
                            result.put("assigneeValue", assigneeValue);
                            result.put("requiresClaim", "true");
                            break;
                        case "VIRTUAL_GROUP":
                            result.put("assigneeType", "VIRTUAL_GROUP");
                            result.put("assigneeValue", assigneeValue);
                            result.put("candidateGroups", assigneeValue);
                            result.put("requiresClaim", "true");
                            break;
                        default:
                            log.debug("assigneeType {} not in converged switch; trying legacy resolver", assigneeType);
                            assignee = resolveLegacyAssigneeType(assigneeType, assigneeValue, initiatorId);
                    }
                } else {
                    // Fall back to standard attribute parsing
                    assignee = extractAttribute(userTaskElement, "camunda:assignee");
                    if (assignee == null) {
                        assignee = extractAttribute(userTaskElement, "flowable:assignee");
                    }
                    if (assignee == null) {
                        assignee = extractAttribute(userTaskElement, "assignee");
                    }
                }
                
                // Skip initiator task (first task is usually initiator form)
                boolean isInitiatorTask = "initiator".equalsIgnoreCase(assigneeType)
                    || "INITIATOR".equalsIgnoreCase(assigneeType)
                    || "PROCESS_INITIATOR".equalsIgnoreCase(assigneeType)
                    || (assignee != null && (assignee.equals("${initiator}") || assignee.equals(initiatorId)));
                
                if (!isInitiatorTask || taskCount > 1) {
                    // First task requiring approval
                    if (name != null) {
                        result.put("name", name);
                    }
                    
                    // Resolve assignee variable if not yet parsed
                    if (assignee != null) {
                        if (assignee.startsWith("${") && assignee.endsWith("}")) {
                            String varName = assignee.substring(2, assignee.length() - 1);
                            assignee = resolveProcessVariable(varName, formData, initiatorId);
                        }
                        result.put("assignee", assignee);
                    }
                    
                    // Set candidate users
                    if (candidateUsers != null) {
                        result.put("candidateUsers", candidateUsers);
                        if (result.get("assignee") == null) {
                            result.put("assignee", candidateUsers.split(",")[0]);
                        }
                    }
                    
                    // Check standard candidateUsers (multi-instance sign-off)
                    if (candidateUsers == null) {
                        candidateUsers = extractAttribute(userTaskElement, "flowable:candidateUsers");
                        if (candidateUsers == null) {
                            candidateUsers = extractAttribute(userTaskElement, "camunda:candidateUsers");
                        }
                        if (candidateUsers != null) {
                            List<String> resolvedCandidates = resolveCandidateUsers(candidateUsers, formData, initiatorId);
                            if (!resolvedCandidates.isEmpty()) {
                                result.put("candidateUsers", String.join(",", resolvedCandidates));
                                if (result.get("assignee") == null) {
                                    result.put("assignee", resolvedCandidates.get(0));
                                }
                            }
                        }
                    }
                    
                    // Check candidateGroups (group task)
                    String candidateGroups = extractAttribute(userTaskElement, "flowable:candidateGroups");
                    if (candidateGroups == null) {
                        candidateGroups = extractAttribute(userTaskElement, "camunda:candidateGroups");
                    }
                    if (candidateGroups != null && result.get("assignee") == null) {
                        result.put("candidateGroups", candidateGroups);
                    }
                    
                    break;
                }
                
                searchStart = userTaskEnd;
            }
        } catch (Exception e) {
            log.warn("Failed to parse BPMN for first user task: {}", e.getMessage(), e);
        }
        return result;
    }
    
    /**
     * Extracts property value from custom:properties
     */
    private String extractCustomProperty(String element, String propertyName) {
        try {
            // Find custom:property tags
            String searchPattern = "name=\"" + propertyName + "\"";
            int propIndex = element.indexOf(searchPattern);
            if (propIndex == -1) {
                return null;
            }
            
            // Read value attribute on property tag
            int lineStart = element.lastIndexOf("<", propIndex);
            int lineEnd = element.indexOf("/>", propIndex);
            if (lineEnd == -1) {
                lineEnd = element.indexOf(">", propIndex);
            }
            
            if (lineStart == -1 || lineEnd == -1) {
                return null;
            }
            
            String propertyTag = element.substring(lineStart, lineEnd);
            return extractAttribute(propertyTag, "value");
        } catch (Exception e) {
            log.warn("Failed to extract custom property {}: {}", propertyName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Finds closing tag position
     */
    private int findClosingTag(String xml, int startIndex, String tagName) {
        String closingTag = "</" + tagName + ">";
        int closingIndex = xml.indexOf(closingTag, startIndex);
        if (closingIndex != -1) {
            return closingIndex + closingTag.length();
        }
        return -1;
    }
    
    private static boolean isLastTaskAssigneeAnchor(String anchor) {
        if (anchor == null || anchor.isBlank()) {
            return false;
        }
        String u = anchor.trim().toUpperCase(Locale.ROOT);
        return "LAST_TASK_ASSIGNEE".equals(u) || "LAST".equals(u) || "CURRENT".equals(u);
    }

    private static String firstUserIdFromCommaList(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isEmpty()) {
            return null;
        }
        int idx = commaSeparated.indexOf(',');
        return idx < 0 ? commaSeparated : commaSeparated.substring(0, idx).trim();
    }

    /**
     * Parses legacy assignment type (backward compatible)
     */
    private String resolveLegacyAssigneeType(String assigneeType, String assigneeValue, String initiatorId) {
        return switch (assigneeType.toLowerCase()) {
            case "initiator" -> initiatorId;
            case "manager", "entitymanager" -> getEntityManager(initiatorId);
            case "functionmanager" -> getFunctionManager(initiatorId);
            case "user" -> assigneeValue;
            default -> null;
        };
    }
    
    /**
     * Resolves process variables
     */
    private String resolveProcessVariable(String varName, Map<String, Object> formData, String initiatorId) {
        // Check form data first
        if (formData != null && formData.containsKey(varName)) {
            return String.valueOf(formData.get(varName));
        }
        
        // Handle special variables (seven standard assignment types)
        return switch (varName) {
            case "initiator" -> initiatorId;
            case "entityManager" -> getEntityManager(initiatorId);
            case "functionManager" -> getFunctionManager(initiatorId);
            default -> null;
        };
    }
    
    /**
     * Resolves candidate user expressions (multiple vars, e.g. ${entityManager},${functionManager})
     */
    private List<String> resolveCandidateUsers(String candidateUsersExpr, Map<String, Object> formData, String initiatorId) {
        List<String> result = new ArrayList<>();
        
        if (candidateUsersExpr == null || candidateUsersExpr.isEmpty()) {
            return result;
        }
        
        // Split multiple candidate user expressions
        String[] expressions = candidateUsersExpr.split(",");
        for (String expr : expressions) {
            expr = expr.trim();
            if (expr.startsWith("${") && expr.endsWith("}")) {
                String varName = expr.substring(2, expr.length() - 1);
                String resolved = resolveProcessVariable(varName, formData, initiatorId);
                if (resolved != null && !resolved.isEmpty()) {
                    result.add(resolved);
                } else {
                    log.warn("Failed to resolve candidate user variable: {}", varName);
                }
            } else if (!expr.isEmpty()) {
                // Literal user ID
                result.add(expr);
            }
        }
        
        return result;
    }
    
    /**
     * Resolves initiator entity manager
     */
    private String getEntityManager(String initiatorId) {
        try {
            // Try user ID first
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + initiatorId;
            log.info("Fetching user info for entity manager from: {}", userUrl);
            
            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = ApiResponseBodyUnwrap.unwrapDataMap(response);
            } catch (Exception e) {
                log.warn("Failed to get user by ID {}, trying by username: {}", initiatorId, e.getMessage());
            }
            
            // If lookup by ID fails, try username
            if (userInfo == null || userInfo.get("entityManagerId") == null) {
                String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + initiatorId + "&size=1";
                log.info("Searching user by username from: {}", searchUrl);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
                    List<Map<String, Object>> users = searchResponse != null
                            ? ApiResponseBodyUnwrap.normalizeToListOfMaps(searchResponse)
                            : Collections.emptyList();
                    if (!users.isEmpty()) {
                        String foundUserId = (String) users.get(0).get("id");
                        String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + foundUserId;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                        userInfo = ApiResponseBodyUnwrap.unwrapDataMap(detailResponse);
                    }
                } catch (Exception e) {
                    log.warn("Failed to search user by username {}: {}", initiatorId, e.getMessage());
                }
            }
            
            if (userInfo == null || userInfo.get("entityManagerId") == null) {
                log.warn("User {} has no entity manager", initiatorId);
                return null;
            }
            
            String entityManagerId = (String) userInfo.get("entityManagerId");
            log.info("Found entity manager {} for user {}", entityManagerId, initiatorId);
            return entityManagerId;
            
        } catch (Exception e) {
            log.error("Failed to get entity manager for {}: {}", initiatorId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Resolves initiator function manager
     */
    private String getFunctionManager(String initiatorId) {
        try {
            // Try user ID first
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + initiatorId;
            log.info("Fetching user info for function manager from: {}", userUrl);
            
            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = ApiResponseBodyUnwrap.unwrapDataMap(response);
            } catch (Exception e) {
                log.warn("Failed to get user by ID {}, trying by username: {}", initiatorId, e.getMessage());
            }
            
            // If lookup by ID fails, try username
            if (userInfo == null || userInfo.get("functionManagerId") == null) {
                String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + initiatorId + "&size=1";
                log.info("Searching user by username from: {}", searchUrl);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
                    List<Map<String, Object>> users = searchResponse != null
                            ? ApiResponseBodyUnwrap.normalizeToListOfMaps(searchResponse)
                            : Collections.emptyList();
                    if (!users.isEmpty()) {
                        String foundUserId = (String) users.get(0).get("id");
                        String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + foundUserId;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                        userInfo = ApiResponseBodyUnwrap.unwrapDataMap(detailResponse);
                    }
                } catch (Exception e) {
                    log.warn("Failed to search user by username {}: {}", initiatorId, e.getMessage());
                }
            }
            
            if (userInfo == null || userInfo.get("functionManagerId") == null) {
                log.warn("User {} has no function manager", initiatorId);
                return null;
            }
            
            String functionManagerId = (String) userInfo.get("functionManagerId");
            log.info("Found function manager {} for user {}", functionManagerId, initiatorId);
            return functionManagerId;
            
        } catch (Exception e) {
            log.error("Failed to get function manager for {}: {}", initiatorId, e.getMessage());
            return null;
        }
    }
    
    /**
     * Cached display-name resolution to avoid duplicate DB hits in one batch
     */
    private String resolveUserDisplayNameCached(String userId, Map<String, String> cache) {
        return userDisplayNameResolver.resolveCached(userId, cache);
    }
    
    /**
     * Resolves user display name
     */
    private String resolveUserDisplayName(String userId) {
        return userDisplayNameResolver.resolve(userId);
    }
    
    /**
     * Extracts attribute value from XML tag
     */
    private String extractAttribute(String tag, String attrName) {
        String pattern1 = attrName + "=\"";
        int start = tag.indexOf(pattern1);
        if (start != -1) {
            start += pattern1.length();
            int end = tag.indexOf("\"", start);
            if (end != -1) {
                return tag.substring(start, end);
            }
        }
        // Try single quotes
        String pattern2 = attrName + "='";
        start = tag.indexOf(pattern2);
        if (start != -1) {
            start += pattern2.length();
            int end = tag.indexOf("'", start);
            if (end != -1) {
                return tag.substring(start, end);
            }
        }
        return null;
    }

    // ==================== Process queries ====================

    /**
     * For running processes with incomplete local assignee data, backfill user/candidate ids from engine and persist.
     */
    private void enrichRunningAssigneesFromEngine(List<ProcessInstance> instances) {
        if (instances == null || instances.isEmpty() || !workflowEngineClient.isAvailable()) {
            return;
        }
        for (ProcessInstance instance : instances) {
            if (instance == null || !"RUNNING".equals(instance.getStatus())) {
                continue;
            }
            if (!needsAssigneeEnrichment(instance)) {
                continue;
            }
            try {
                Optional<Map<String, Object>> tasksResult =
                        workflowEngineClient.getProcessInstanceTasks(instance.getId());
                if (tasksResult.isEmpty()) {
                    continue;
                }
                Map<String, Object> tasksData = tasksResult.get();
                if (tasksData == null) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                if (tasks == null || tasks.isEmpty()) {
                    continue;
                }
                ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(tasks.get(0));
                if (snapshot.getAssigneeUserId() == null && snapshot.getCandidateUserIds() == null) {
                    continue;
                }
                instance.setCurrentAssignee(snapshot.getAssigneeUserId());
                instance.setCandidateUsers(snapshot.getCandidateUserIds());
                processInstanceRepository.save(instance);
                log.debug("Enriched assignee snapshot for process {}: assignee={}, candidates={}",
                        instance.getId(), snapshot.getAssigneeUserId(), snapshot.getCandidateUserIds());
            } catch (Exception e) {
                log.warn("Failed to enrich assignee from engine for process {}: {}",
                        instance.getId(), e.getMessage());
            }
        }
    }

    private boolean needsAssigneeEnrichment(ProcessInstance instance) {
        String assignee = instance.getCurrentAssignee();
        String candidates = instance.getCandidateUsers();
        if (candidates != null && !candidates.isBlank()) {
            return true;
        }
        if (assignee == null || assignee.isBlank()) {
            return true;
        }
        Map<String, String> probe = userDisplayNameResolver.resolveBatch(
                userDisplayNameResolver.collectAssigneeUserKeys(assignee, candidates));
        String display = userDisplayNameResolver.resolveCurrentAssigneeDisplay(assignee, candidates, probe);
        return display != null && display.equals(assignee.trim());
    }

    /**
     * Returns my applications list
     */
    public Page<ProcessInstanceInfo> getMyApplications(String userId, String status, Pageable pageable) {
        log.info("Getting applications for user: {}, status: {}", userId, status);
        
        Page<ProcessInstance> instancePage;
        if (status != null && !status.isEmpty()) {
            instancePage = processInstanceRepository.findByStartUserIdAndStatusOrderByStartTimeDesc(userId, status, pageable);
        } else {
            instancePage = processInstanceRepository.findByStartUserIdOrderByStartTimeDesc(userId, pageable);
        }

        List<ProcessInstance> pageContent = instancePage.getContent();
        enrichRunningAssigneesFromEngine(pageContent);

        Set<String> assigneeKeys = pageContent.stream()
                .flatMap(inst -> userDisplayNameResolver.collectAssigneeUserKeys(
                        inst.getCurrentAssignee(), inst.getCandidateUsers()).stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, String> userNameCache = userDisplayNameResolver.resolveBatch(assigneeKeys);
        // List API omits variables: JSONB may contain Jackson-unfriendly nesting → HttpMessageNotWritableException → SYS_INTERNAL_ERROR
        List<ProcessInstanceInfo> instances = pageContent.stream()
                .map(inst -> toProcessInstanceInfoForList(inst, userNameCache))
                .peek(info -> info.setVariables(null))
                .toList();

        return new PageImpl<>(instances, pageable, instancePage.getTotalElements());
    }
    
    /**
     * Entity to DTO without cache (single process query)
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance) {
        return toProcessInstanceInfo(instance, new HashMap<>());
    }
    
    /**
     * My requests list: do not call workflow-engine per row for runtime tasks (one HTTP per row is too slow).
     * Assignee display name from {@link ProcessInstance#getCurrentAssignee()} / {@link ProcessInstance#getCandidateUsers()}
     * via {@link UserDisplayNameResolver} (single name; BU/Role OR pool {@code name1, name2, name3}).
     */
    private ProcessInstanceInfo toProcessInstanceInfoForList(ProcessInstance instance, Map<String, String> userNameCache) {
        String currentAssigneeName = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                instance.getCurrentAssignee(), instance.getCandidateUsers(), userNameCache);

        log.debug("toProcessInstanceInfoForList: processId={}, status={}, assignee={}, candidates={}, display={}",
                instance.getId(), instance.getStatus(),
                instance.getCurrentAssignee(), instance.getCandidateUsers(), currentAssigneeName);

        String currentNode = instance.getCurrentNode();
        if ("COMPLETED".equals(instance.getStatus())) {
            currentNode = null;
        }

        return ProcessInstanceInfo.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .processDefinitionName(instance.getProcessDefinitionName())
                .businessKey(instance.getBusinessKey())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .completedAt(instance.getCompletedAt())
                .title(instance.getTitle())
                .status(instance.getStatus())
                .startUserId(instance.getStartUserId())
                .startUserName(instance.getStartUserName())
                .currentNode(currentNode)
                .currentAssignee(currentAssigneeName)
                .candidateUsers(instance.getCandidateUsers())
                .variables(instance.getVariables())
                .functionUnitCatalogId(instance.getFunctionUnitCatalogId())
                .functionUnitCode(instance.getFunctionUnitCode())
                .functionUnitVersionLabel(instance.getFunctionUnitVersionLabel())
                .build();
    }

    /**
     * Entity to DTO with method-level display-name cache (avoids list N+1)
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance, Map<String, String> userNameCache) {
        String currentAssigneeName = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                instance.getCurrentAssignee(), instance.getCandidateUsers(), userNameCache);

        log.debug("toProcessInstanceInfo: processId={}, status={}, assignee={}, candidates={}, display={}",
                instance.getId(), instance.getStatus(),
                instance.getCurrentAssignee(), instance.getCandidateUsers(), currentAssigneeName);

        String currentNode = instance.getCurrentNode();
        if ("COMPLETED".equals(instance.getStatus())) {
            currentNode = null;
        }

        return ProcessInstanceInfo.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .processDefinitionName(instance.getProcessDefinitionName())
                .businessKey(instance.getBusinessKey())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .completedAt(instance.getCompletedAt())
                .title(instance.getTitle())
                .status(instance.getStatus())
                .startUserId(instance.getStartUserId())
                .startUserName(instance.getStartUserName())
                .currentNode(currentNode)
                .currentAssignee(currentAssigneeName)
                .candidateUsers(instance.getCandidateUsers())
                .variables(instance.getVariables())
                .functionUnitCatalogId(instance.getFunctionUnitCatalogId())
                .functionUnitCode(instance.getFunctionUnitCode())
                .functionUnitVersionLabel(instance.getFunctionUnitVersionLabel())
                .build();
    }

    /**
     * Returns process detail
     * When local DB lacks current node, fetch live from Flowable
     */
    public ProcessInstanceInfo getProcessDetail(String processId) {
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
        if (optInstance.isEmpty()) {
            return null;
        }
        
        ProcessInstance instance = optInstance.get();
        ProcessInstanceInfo info = toProcessInstanceInfo(instance);

        // If the process is still running but the local DB has no currentNode stored,
        // try to fetch the live state from Flowable — this self-heals processes where
        // the auto-complete path set currentNode=null (e.g. no next task after initiator task).
        if ("RUNNING".equals(instance.getStatus()) &&
            (info.getCurrentNode() == null || info.getCurrentNode().isEmpty())) {
            try {
                if (workflowEngineClient.isAvailable()) {
                    Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(processId);
                    if (tasksResult.isPresent()) {
                        Map<String, Object> tasksData = tasksResult.get();
                        if (tasksData != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                            if (tasks != null && !tasks.isEmpty()) {
                                Map<String, Object> currentTask = tasks.get(0);
                                String currentNodeName = (String) currentTask.get("taskName");
                                ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(currentTask);
                                Map<String, String> assigneeCache = userDisplayNameResolver.resolveBatch(
                                        userDisplayNameResolver.collectAssigneeUserKeys(
                                                snapshot.getAssigneeUserId(), snapshot.getCandidateUserIds()));
                                String currentAssigneeDisplay = userDisplayNameResolver.resolveCurrentAssigneeDisplay(
                                        snapshot.getAssigneeUserId(), snapshot.getCandidateUserIds(), assigneeCache);

                                if (currentNodeName != null && !currentNodeName.isEmpty()) {
                                    info.setCurrentNode(currentNodeName);
                                    info.setCurrentAssignee(currentAssigneeDisplay);
                                    info.setCandidateUsers(snapshot.getCandidateUserIds());
                                    instance.setCurrentNode(currentNodeName);
                                    instance.setCurrentAssignee(snapshot.getAssigneeUserId());
                                    instance.setCandidateUsers(snapshot.getCandidateUserIds());
                                    processInstanceRepository.save(instance);
                                    log.info("getProcessDetail: refreshed currentNode={}, assignee={}, candidates={} from Flowable for process {}",
                                            currentNodeName, snapshot.getAssigneeUserId(),
                                            snapshot.getCandidateUserIds(), processId);
                                }
                            } else {
                                log.debug("getProcessDetail: no active tasks in Flowable for process {}, keeping null currentNode", processId);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("getProcessDetail: failed to refresh currentNode from Flowable for process {}: {}", processId, e.getMessage());
            }
        }

        Map<String, Object> vars = info.getVariables();
        boolean hasSubTables =
                vars != null
                        && vars.get("__subTables__") instanceof Map<?, ?> subMap
                        && !subMap.isEmpty();
        Map<String, Map<String, MiRowProgress>> miProgress = Collections.emptyMap();
        if (hasSubTables || "RUNNING".equals(instance.getStatus())) {
            miProgress = resolveMiRowProgress(processId, instance.getStatus());
        }
        if ("RUNNING".equals(instance.getStatus())) {
            reconcileCurrentNodeWithMiOverlay(info, miProgress);
        }
        if (hasSubTables) {
            enrichSubTablesWithAssignmentData(info, miProgress);
        }

        return info;
    }

    /**
     * Merge persisted relation-table columns into {@code variables.__subTables__} rows (same as process detail).
     * Task detail previously merged PI variables without this step, so MI todo rows often stayed thin until reload elsewhere.
     */
    public void enrichSubTablesVariablesFromPhysicalTables(String processInstanceId, Map<String, Object> variables) {
        if (processInstanceId == null || processInstanceId.isBlank()
                || variables == null || variables.isEmpty()) {
            return;
        }
        // Collapse any geometrically bloated __subTables__ (rows that embed full nested copies of the whole
        // sub-table tree from prior task rounds) down to the canonical one-level nesting BEFORE enriching.
        // This both fixes already-persisted bloat on read and bounds the recursive overlay cost.
        int strippedNested = SubTableNestingSanitizer.stripDeepNestedSubTables(variables);
        if (strippedNested > 0) {
            log.info("[PERF] enrich(public) stripped {} deep nested __subTables__ for {}",
                    strippedNested, processInstanceId);
        }
        // Same process instance is enriched up to 3x per detail page load (detail endpoint, /form, /form-data).
        // The recursive __subTables__ overlay is expensive on large payloads, so reuse a freshly computed result
        // across those calls when the input __subTables__ is byte-identical (fingerprint) and recent. The short
        // TTL guards MI/runtime freshness (a participant advancing must re-run within seconds).
        Object baseSub = variables.get("__subTables__");
        String enrichFingerprint = fingerprintForEnrichCache(baseSub);
        if (enrichFingerprint != null) {
            EnrichedSubTablesCacheEntry hit = enrichedSubTablesCache.get(processInstanceId);
            if (hit != null
                    && enrichFingerprint.equals(hit.baseFingerprint())
                    && System.currentTimeMillis() - hit.timestampMs() < ENRICH_RESULT_TTL_MS) {
                Object restored = readEnrichCacheValue(hit.enrichedJson());
                if (restored != null) {
                    variables.put("__subTables__", restored);
                    log.info("[PERF] enrich(public) CACHE HIT for {}", processInstanceId);
                    return;
                }
            }
        }

        ProcessInstanceInfo synthetic = new ProcessInstanceInfo();
        synthetic.setId(processInstanceId);
        synthetic.setVariables(variables);
        processInstanceRepository.findById(processInstanceId).ifPresent(pi -> {
            synthetic.setFunctionUnitCatalogId(pi.getFunctionUnitCatalogId());
            synthetic.setFunctionUnitCode(pi.getFunctionUnitCode());
            synthetic.setProcessDefinitionKey(pi.getProcessDefinitionKey());
            synthetic.setStatus(pi.getStatus());
        });
        long __t = System.nanoTime();
        enrichSubTablesWithAssignmentData(synthetic);
        log.info("[PERF] enrichSubTablesWithAssignmentData(public) took {} ms", (System.nanoTime() - __t) / 1_000_000L);

        if (enrichFingerprint != null) {
            String enrichedJson = writeEnrichCacheValue(variables.get("__subTables__"));
            if (enrichedJson != null) {
                enrichedSubTablesCache.put(processInstanceId,
                        new EnrichedSubTablesCacheEntry(enrichFingerprint, enrichedJson, System.currentTimeMillis()));
            }
        }
    }

    private static final long ENRICH_RESULT_TTL_MS = 5000L;
    private final Map<String, EnrichedSubTablesCacheEntry> enrichedSubTablesCache = new ConcurrentHashMap<>();
    private final ObjectMapper enrichCacheMapper = new ObjectMapper();

    private record EnrichedSubTablesCacheEntry(String baseFingerprint, String enrichedJson, long timestampMs) {}

    private String fingerprintForEnrichCache(Object subTables) {
        if (subTables == null) {
            return null;
        }
        try {
            String json = enrichCacheMapper.writeValueAsString(subTables);
            return json.length() + ":" + Integer.toHexString(json.hashCode());
        } catch (Exception e) {
            return null;
        }
    }

    private String writeEnrichCacheValue(Object subTables) {
        if (subTables == null) {
            return null;
        }
        try {
            return enrichCacheMapper.writeValueAsString(subTables);
        } catch (Exception e) {
            return null;
        }
    }

    private Object readEnrichCacheValue(String json) {
        if (json == null) {
            return null;
        }
        try {
            return enrichCacheMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check whether a portal userId is a participant of the given process.
     * Quick local checks first; falls back to querying the workflow-engine
     * process history to see if the user was ever a task assignee.
     */
    public boolean isProcessParticipant(String userId, ProcessInstanceInfo detail) {
        if (detail == null || userId == null) return false;

        // 1. Process initiator
        if (userId.equals(detail.getStartUserId())) return true;

        // 2. Current assignee (may be display-name OR userId depending on data flow)
        if (userId.equals(detail.getCurrentAssignee())) return true;

        // 3. Candidate users (or-sign scenario, comma-separated)
        String candidates = detail.getCandidateUsers();
        if (candidates != null && !candidates.isBlank()) {
            for (String c : candidates.split(",")) {
                if (userId.equals(c.trim())) return true;
            }
        }

        // 4. Resolve userId to display name and compare against currentAssignee
        //    (currentAssignee is often stored as display name)
        try {
            String displayName = resolveUserDisplayName(userId);
            if (displayName != null && displayName.equals(detail.getCurrentAssignee())) return true;
        } catch (Exception e) {
            log.debug("Could not resolve display name for user {}: {}", userId, e.getMessage());
        }

        // 5. Fall back: check workflow-engine process history for any task
        //    where the user was ever an assignee (covers completed MI sub-tasks, etc.)
        try {
            if (workflowEngineClient.isAvailable()) {
                var historyOpt = workflowEngineClient.getProcessInstanceHistory(detail.getId());
                if (historyOpt.isPresent()) {
                    for (Map<String, Object> record : historyOpt.get()) {
                        Object operatorId = record.get("operatorId");
                        if (operatorId != null && userId.equals(String.valueOf(operatorId).trim())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to check process history participation for user {} in process {}: {}",
                    userId, detail.getId(), e.getMessage());
        }

        return false;
    }

    @SuppressWarnings("unchecked")
    private void enrichSubTablesWithAssignmentData(ProcessInstanceInfo info) {
        Map<String, Object> variables = info.getVariables();
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?> subMap) || subMap.isEmpty()) {
            return;
        }
        enrichSubTablesWithAssignmentData(info, resolveMiRowProgress(info.getId(), info.getStatus()));
    }

    /**
     * Uses pre-resolved MI overlay (same snapshot as {@link #enrichSubTablesMapPayload}) so initiator detail avoids a
     * duplicate workflow-engine MI HTTP round-trip.
     */
    @SuppressWarnings("unchecked")
    private void enrichSubTablesWithAssignmentData(
            ProcessInstanceInfo info, Map<String, Map<String, MiRowProgress>> miProgressByTable) {
        Map<String, Object> variables = info.getVariables();
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Object subTablesObj = variables.get("__subTables__");
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        long __t1 = System.nanoTime();
        Map<String, String> bindingTableNames = resolveSubTableBindingTableNames(info);
        log.info("[PERF] enrich.resolveSubTableBindingTableNames took {} ms", (System.nanoTime() - __t1) / 1_000_000L);
        long __t2 = System.nanoTime();
        ENRICH_SQL_STATS.set(new long[4]);
        enrichSubTablesMapPayload(info, subTables, bindingTableNames, miProgressByTable);
        long[] __s = ENRICH_SQL_STATS.get();
        log.info("[PERF] enrich.enrichSubTablesMapPayload took {} ms | perRowSelect={} (sum {} ms), subTableExists={}, resolvePk={}",
                (System.nanoTime() - __t2) / 1_000_000L, __s[0], __s[1], __s[2], __s[3]);
        // Numeric bindingIds (64/66) and legacy keys (90/subtable2) share the same MI rows; only slices with a
        // designer binding name were overlaid above — propagate engine state to every duplicate row in __subTables__.
        long __t3 = System.nanoTime();
        propagateMiOverlayAcrossAllSubTableSlices(subTables, miProgressByTable, info);
        log.info("[PERF] enrich.propagateMiOverlayAcrossAllSubTableSlices took {} ms", (System.nanoTime() - __t3) / 1_000_000L);
    }

    /**
     * Apply resolved MI progress to every sub-table row in variables, regardless of {@code __subTables__} slice key.
     * Fixes initiator My Request when the first merged row comes from an unmapped slice (still showing sub form1).
     */
    @SuppressWarnings("unchecked")
    private void propagateMiOverlayAcrossAllSubTableSlices(
            Map<String, Object> subTables,
            Map<String, Map<String, MiRowProgress>> miProgressByTable,
            ProcessInstanceInfo info) {
        if (subTables == null || subTables.isEmpty() || miProgressByTable == null || miProgressByTable.isEmpty()) {
            return;
        }
        Map<String, List<String>> pkColsByTable = new HashMap<>();
        for (String tableName : miProgressByTable.keySet()) {
            if (tableName == null || tableName.isBlank()) {
                continue;
            }
            try {
                String safe = requireSafeIdentifier(tableName);
                pkColsByTable.put(tableName, resolvePkColumnsCached(safe));
            } catch (Exception e) {
                log.debug("propagateMiOverlay: skip PK for {}: {}", tableName, e.getMessage());
            }
        }
        Map<Long, MiRowProgress> byNumericRowId = buildMiProgressIndexByNumericRowId(miProgressByTable);
        propagateMiOverlayWalkSubTables(subTables, miProgressByTable, pkColsByTable, byNumericRowId, info);
    }

    private Map<Long, MiRowProgress> buildMiProgressIndexByNumericRowId(
            Map<String, Map<String, MiRowProgress>> miProgressByTable) {
        Map<Long, MiRowProgress> out = new HashMap<>();
        if (miProgressByTable == null) {
            return out;
        }
        for (Map<String, MiRowProgress> tableMap : miProgressByTable.values()) {
            if (tableMap == null) {
                continue;
            }
            for (var e : tableMap.entrySet()) {
                Long id = parseCanonicalSinglePkSuffixLong(e.getKey());
                if (id == null) {
                    continue;
                }
                out.merge(id, e.getValue(), ProcessComponent::preferMiRowProgressOverlay);
            }
        }
        return out;
    }

    private static Long extractNumericSubTableRowId(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (String key : List.of("id", "id_idw", "rowId")) {
            Long n = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(row, key));
            if (n != null) {
                return n;
            }
        }
        Object rawRk = row.get("rowKey");
        if (rawRk instanceof Map<?, ?> m) {
            Map<String, Object> rk = SubTableRowKeySupport.normalizeStringKeyMap(m);
            for (String key : List.of("id", "id_idw", "rowId")) {
                Long n = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(rk, key));
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    private static void normalizeVariableRowPkEnvelope(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        Long idNum = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(row, "id"));
        Long idIdwNum = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(row, "id_idw"));
        if (idNum == null && idIdwNum != null) {
            row.put("id", idIdwNum);
        } else if (idNum != null && idIdwNum == null) {
            row.put("id_idw", idNum);
        }
    }

    private static Set<String> miDashboardColumnsToProtect(Map<String, MiRowProgress> miProgress) {
        Set<String> cols = new LinkedHashSet<>();
        cols.add("task_status");
        cols.add("task_current_node");
        if (miProgress == null) {
            return cols;
        }
        for (MiRowProgress p : miProgress.values()) {
            if (p == null) {
                continue;
            }
            if (p.statusColumn != null && !p.statusColumn.isBlank()) {
                cols.add(p.statusColumn.trim());
            }
            if (p.nodeColumn != null && !p.nodeColumn.isBlank()) {
                cols.add(p.nodeColumn.trim());
            }
        }
        return cols;
    }

    private static boolean isProtectedMiDashboardColumn(Set<String> protectedCols, String columnName) {
        if (columnName == null || protectedCols == null || protectedCols.isEmpty()) {
            return false;
        }
        for (String p : protectedCols) {
            if (p != null && p.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void propagateMiOverlayWalkSubTables(
            Map<String, Object> subTables,
            Map<String, Map<String, MiRowProgress>> miProgressByTable,
            Map<String, List<String>> pkColsByTable,
            Map<Long, MiRowProgress> byNumericRowId,
            ProcessInstanceInfo info) {
        for (Object sliceVal : subTables.values()) {
            if (!(sliceVal instanceof List<?> rows)) {
                continue;
            }
            for (Object rowObj : rows) {
                if (!(rowObj instanceof Map<?, ?> rawRow)) {
                    continue;
                }
                Map<String, Object> row = (Map<String, Object>) rawRow;
                normalizeVariableRowPkEnvelope(row);
                MiRowProgress best = resolveBestMiProgressForVariableRow(row, miProgressByTable, pkColsByTable);
                if (best == null) {
                    Long numericId = extractNumericSubTableRowId(row);
                    if (numericId != null) {
                        best = byNumericRowId.get(numericId);
                    }
                }
                if (best != null) {
                    applyMiOverlayToVariableRow(row, best);
                }
                if (isPortalProcessCompleted(info)) {
                    normalizeStuckMiParticipantRowForCompletedProcess(row);
                }
                Object nestedRaw = row.get("__subTables__");
                if (nestedRaw instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
                    propagateMiOverlayWalkSubTables(
                            (Map<String, Object>) nestedMap,
                            miProgressByTable,
                            pkColsByTable,
                            byNumericRowId,
                            info);
                }
            }
        }
    }

    private MiRowProgress resolveBestMiProgressForVariableRow(
            Map<String, Object> row,
            Map<String, Map<String, MiRowProgress>> miProgressByTable,
            Map<String, List<String>> pkColsByTable) {
        MiRowProgress best = null;
        for (var tableEntry : miProgressByTable.entrySet()) {
            List<String> pkCols = pkColsByTable.get(tableEntry.getKey());
            if (pkCols == null || pkCols.isEmpty()) {
                continue;
            }
            Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
            if (rowKey == null) {
                continue;
            }
            MiRowProgress p = lookupMiRowProgressForVariableRow(tableEntry.getValue(), pkCols, rowKey);
            if (p != null) {
                best = preferMiRowProgressOverlay(best, p);
            }
        }
        return best;
    }

    private static MiRowProgress preferMiRowProgressOverlay(MiRowProgress a, MiRowProgress b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean aTerminal = isTerminalMiOverlayProgress(a);
        boolean bTerminal = isTerminalMiOverlayProgress(b);
        if (aTerminal && !bTerminal) {
            return a;
        }
        if (bTerminal && !aTerminal) {
            return b;
        }
        int oa = miSubFormOrdinalHint(a.currentNode());
        int ob = miSubFormOrdinalHint(b.currentNode());
        if (oa != Integer.MIN_VALUE && ob != Integer.MIN_VALUE && oa != ob) {
            return ob > oa ? b : a;
        }
        return b;
    }

    private static boolean isTerminalMiOverlayProgress(MiRowProgress p) {
        if (p == null) {
            return false;
        }
        if (p.status() != null && "COMPLETED".equalsIgnoreCase(p.status().trim())) {
            return true;
        }
        String node = p.currentNode();
        return node != null && "end".equalsIgnoreCase(node.trim());
    }

    /**
     * Parallel MI rows may sit on different user tasks; Flowable / portal DB {@link ProcessInstance#getCurrentNode()}
     * reflects one arbitrary active task (often {@code tasks.get(0)}). Align headline {@link ProcessInstanceInfo#getCurrentNode()}
     * with the numerically greatest {@code sub form N} among in-flight MI rows (matches sub-table overlay semantics).
     */
    private void reconcileCurrentNodeWithMiOverlay(
            ProcessInstanceInfo info, Map<String, Map<String, MiRowProgress>> byTable) {
        if (info == null || byTable == null || byTable.isEmpty()) {
            return;
        }
        String bestNode = null;
        int bestOrd = Integer.MIN_VALUE;
        for (Map<String, MiRowProgress> rows : byTable.values()) {
            if (rows == null || rows.isEmpty()) {
                continue;
            }
            for (MiRowProgress p : rows.values()) {
                if (p == null || p.currentNode() == null || p.currentNode().isBlank()) {
                    continue;
                }
                String st = p.status();
                if (st == null || st.isBlank()) {
                    continue;
                }
                String u = st.trim().toUpperCase(Locale.ROOT);
                if (!("IN_PROGRESS".equals(u) || "ASSIGNED".equals(u) || "CREATED".equals(u))) {
                    continue;
                }
                int ord = miSubFormOrdinalHint(p.currentNode());
                if (ord == Integer.MIN_VALUE) {
                    continue;
                }
                if (ord > bestOrd) {
                    bestOrd = ord;
                    bestNode = p.currentNode();
                }
            }
        }
        if (bestNode == null || bestOrd == Integer.MIN_VALUE) {
            return;
        }
        String prev = Optional.ofNullable(info.getCurrentNode()).orElse("").trim();
        int existingOrd = miSubFormOrdinalHint(prev);
        boolean prevLooksMiSubForm =
                prev.toLowerCase(Locale.ROOT).replace(" ", "").contains("subform");

        boolean upgrade =
                (existingOrd != Integer.MIN_VALUE && bestOrd > existingOrd)
                        || (prevLooksMiSubForm && bestOrd > existingOrd)
                        || (prevLooksMiSubForm && bestOrd == existingOrd && !bestNode.equalsIgnoreCase(prev));
        if (!upgrade) {
            return;
        }
        info.setCurrentNode(bestNode);
    }

    /** Largest N from {@code sub form N} tokens; {@link Integer#MIN_VALUE} if none. */
    private static int miSubFormOrdinalHint(String name) {
        if (name == null || name.isBlank()) {
            return Integer.MIN_VALUE;
        }
        Matcher m = Pattern.compile("(?i)\\bsub\\s*form\\s*(\\d+)\\b").matcher(name.trim());
        int max = Integer.MIN_VALUE;
        while (m.find()) {
            try {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                /* ignore */
            }
        }
        return max;
    }

    /**
     * When canonical PK strings differ between variables and wf_extended_task_info (e.g. copied forms /
     * id vs id_idw), still resolve MI overlay if there is exactly one logical PK value match.
     */
    private MiRowProgress lookupMiRowProgressForVariableRow(
            Map<String, MiRowProgress> miProgress,
            List<String> pkCols,
            Map<String, Object> rowKey) {
        if (miProgress == null || miProgress.isEmpty() || rowKey == null) {
            return null;
        }
        String canon = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
        MiRowProgress hit = miProgress.get(canon);
        if (hit != null) {
            return hit;
        }
        if (pkCols.size() != 1) {
            return null;
        }
        Long want = normalizeRowKeyLong(pkCols.get(0), rowKey);
        if (want == null) {
            return null;
        }
        MiRowProgress onlyMatch = null;
        int matches = 0;
        for (Map.Entry<String, MiRowProgress> e : miProgress.entrySet()) {
            Long parsed = parseCanonicalSinglePkSuffixLong(e.getKey());
            if (parsed != null && parsed.equals(want)) {
                matches++;
                onlyMatch = e.getValue();
            }
        }
        if (matches == 1 && onlyMatch != null) {
            return onlyMatch;
        }
        return null;
    }

    private static Long normalizeRowKeyLong(String pkCol, Map<String, Object> rowKey) {
        Object v = SubTableRowKeySupport.getRowValueIgnoreCase(rowKey, pkCol);
        return coerceWholeNumber(v);
    }

    private static Long parseCanonicalSinglePkSuffixLong(String canonKey) {
        if (canonKey == null || canonKey.isEmpty()) {
            return null;
        }
        int eq = canonKey.lastIndexOf('=');
        if (eq < 0 || eq >= canonKey.length() - 1) {
            return null;
        }
        return coerceWholeNumber(canonKey.substring(eq + 1));
    }

    private static Long coerceWholeNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isPortalProcessCompleted(ProcessInstanceInfo info) {
        return info != null && info.getStatus() != null
                && "COMPLETED".equalsIgnoreCase(info.getStatus().trim());
    }

    /**
     * Mirrors portal {@code task_status}/{@code task_current_node} when writing MI extension columns,
     * SubTableField / My Request flow chart depends on these columns.
     */
    private void applyMiOverlayToVariableRow(Map<String, Object> row, MiRowProgress p) {
        if (row == null || p == null) {
            return;
        }
        if (p.statusColumn != null && !p.statusColumn.isBlank()) {
            row.put(p.statusColumn, p.status);
        }
        if (p.nodeColumn != null && !p.nodeColumn.isBlank()) {
            row.put(p.nodeColumn, p.currentNode);
        }
        row.put("task_status", mapWorkflowMiStatusToPortalTaskStatus(p.status));
        row.put("task_current_node",
                p.currentNode != null && !p.currentNode.isBlank() ? p.currentNode : "-");
    }

    private static String mapWorkflowMiStatusToPortalTaskStatus(String workflowStatus) {
        if (workflowStatus == null || workflowStatus.isBlank()) {
            return "PENDING";
        }
        String u = workflowStatus.trim().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(u)) {
            return "COMPLETED";
        }
        if ("CANCELLED".equals(u)) {
            return "CANCELLED";
        }
        if ("IN_PROGRESS".equals(u) || "ASSIGNED".equals(u) || "CREATED".equals(u)) {
            return "IN_PROGRESS";
        }
        return workflowStatus;
    }

    /**
     * Fallback when process is archived but variable snapshot/MI API still shows in-progress placeholders (often after soft-deleted extended tasks).
     */
    private static void normalizeStuckMiParticipantRowForCompletedProcess(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        Object ts = row.get("task_status");
        String s = ts != null ? String.valueOf(ts).trim() : "";
        if ("COMPLETED".equalsIgnoreCase(s) || "CANCELLED".equalsIgnoreCase(s)) {
            return;
        }
        boolean miLike = row.containsKey("assignee_user_id")
                || row.containsKey("assignee_display_name")
                || row.containsKey("task_current_node");
        if (!miLike) {
            return;
        }
        row.put("task_status", "COMPLETED");
        row.put("task_current_node", "end");
    }

    /**
     * Physical-row merge + MI overlay for one {@code __subTables__}-shaped map (top-level or nested under a row).
     * Recurses into each row's {@code __subTables__} so link-form child slices persisted only under parent rows still hydrate.
     */
    private static final ThreadLocal<long[]> ENRICH_SQL_STATS = ThreadLocal.withInitial(() -> new long[4]);

    @SuppressWarnings("unchecked")
    private void enrichSubTablesMapPayload(
            ProcessInstanceInfo info,
            Map<String, Object> subTables,
            Map<String, String> bindingTableNames,
            Map<String, Map<String, MiRowProgress>> miProgressByTable) {
        if (subTables == null || subTables.isEmpty()) {
            return;
        }
        try {
            for (Map.Entry<String, Object> subTableEntry : subTables.entrySet()) {
                String sliceKey = subTableEntry.getKey();
                String tableName = bindingTableNames.get(sliceKey);
                if (tableName == null || tableName.isBlank()) {
                    tableName = bindingTableNames.get(normalizeMiTableKey(sliceKey));
                }
                if (tableName == null || tableName.isBlank() || !(subTableEntry.getValue() instanceof List<?> rows)) {
                    continue;
                }

                Map<String, MiRowProgress> miProgress = lookupMiProgressForDesignerTable(miProgressByTable, tableName);
                Set<String> protectedMiCols = miDashboardColumnsToProtect(miProgress);

                String safeTableName = requireSafeIdentifier(tableName);
                List<String> pkCols;
                try {
                    ENRICH_SQL_STATS.get()[3]++;
                    pkCols = resolvePkColumnsCached(safeTableName);
                } catch (Exception e) {
                    log.debug("enrichSubTablesMapPayload: skip table {} (PK): {}", safeTableName, e.getMessage());
                    continue;
                }

                // Designer metadata can imply a PK for a logical name (e.g. MI token "subtable") while no physical
                // relation exists; SELECT against a missing relation aborts the whole PostgreSQL transaction and
                // the request later fails with UnexpectedRollbackException despite catch blocks here (see Docker
                // logs: ERROR relation "subtable" does not exist → current transaction is aborted).
                ENRICH_SQL_STATS.get()[2]++;
                final boolean physicalTablePresent = subTableExists(safeTableName);

                // Legacy: physical table merge first (assignee, persisted field values). DB may still hold stale
                // task_status / task_current_node after a participant advances — those columns must not win over
                // the engine; MI overlay applied below overwrites them.
                for (Object rowObj : rows) {
                    if (!(rowObj instanceof Map<?, ?> rawRow)) {
                        continue;
                    }
                    Map<String, Object> row = (Map<String, Object>) rawRow;
                    Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                    boolean canQueryDb = rowKey != null && physicalTablePresent;
                    if (canQueryDb) {
                        String where = SubTableRowKeySupport.buildPkWhereClause(pkCols);
                        Object[] args = SubTableRowKeySupport.orderedPkParams(pkCols, rowKey);
                        long __tsel = System.nanoTime();
                        ENRICH_SQL_STATS.get()[0]++;
                        List<Map<String, Object>> dbRows = jdbcTemplate.query(
                                "SELECT * FROM " + safeTableName + " WHERE " + where,
                                (rs, i) -> {
                                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                                    Map<String, Object> m = new HashMap<>();
                                    for (int c = 1; c <= meta.getColumnCount(); c++) {
                                        m.put(meta.getColumnName(c), rs.getObject(c));
                                    }
                                    return m;
                                }, args);
                        ENRICH_SQL_STATS.get()[1] += (System.nanoTime() - __tsel) / 1_000_000L;
                        if (!dbRows.isEmpty()) {
                            Map<String, Object> dbRow = dbRows.get(0);
                            String displayName = (String) dbRow.get("assignee_display_name");
                            String userId = (String) dbRow.get("assignee_user_id");
                            if (displayName == null && userId != null && !userId.isBlank()) {
                                displayName = resolveUsernameById(userId);
                                dbRow.put("assignee_display_name", displayName);
                            }
                            repairStaleTaskStatus(safeTableName, dbRow, rowKey, pkCols);
                            for (Map.Entry<String, Object> entry : dbRow.entrySet()) {
                                if (entry.getValue() == null) {
                                    continue;
                                }
                                // Physical PG may still hold sub form1 after participant advances; engine overlay wins.
                                if (!protectedMiCols.isEmpty()
                                        && isProtectedMiDashboardColumn(protectedMiCols, entry.getKey())) {
                                    continue;
                                }
                                row.put(entry.getKey(), entry.getValue());
                            }
                        }
                    }
                    Object nestedRaw = row.get("__subTables__");
                    if (nestedRaw instanceof Map<?, ?> nestedMap && !nestedMap.isEmpty()) {
                        enrichSubTablesMapPayload(
                                info, (Map<String, Object>) nestedMap, bindingTableNames, miProgressByTable);
                    }
                }

                // Engine-driven MI status last so initiator My Request matches runtime tasks (not stale DB columns).
                if (!miProgress.isEmpty()) {
                    for (Object rowObj : rows) {
                        if (!(rowObj instanceof Map<?, ?> rawRow)) {
                            continue;
                        }
                        Map<String, Object> row = (Map<String, Object>) rawRow;
                        Map<String, Object> rowKey = SubTableRowKeySupport.rowKeyFromVariableRow(row, pkCols);
                        if (rowKey == null) {
                            continue;
                        }
                        MiRowProgress p = lookupMiRowProgressForVariableRow(miProgress, pkCols, rowKey);
                        // Do not fabricate PENDING when no engine row matches — DB merge / variables may already
                        // hold COMPLETED for copied forms (subform_copy) or PK-canonical mismatch cases.
                        if (p != null) {
                            applyMiOverlayToVariableRow(row, p);
                        }
                        if (isPortalProcessCompleted(info)) {
                            normalizeStuckMiParticipantRowForCompletedProcess(row);
                        }
                    }
                } else if (isPortalProcessCompleted(info)) {
                    for (Object rowObj : rows) {
                        if (!(rowObj instanceof Map<?, ?> rawRow)) {
                            continue;
                        }
                        Map<String, Object> row = (Map<String, Object>) rawRow;
                        normalizeStuckMiParticipantRowForCompletedProcess(row);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("enrichSubTablesMapPayload skipped: {}", e.getMessage());
        }
    }

    /**
     * Sub-table physical-table metadata (PK columns, existence) is queried per row/slice while walking the
     * recursive {@code __subTables__} payload, which previously fired tens of thousands of identical
     * information_schema / to_regclass queries for the same handful of table names (see issue: portal task
     * detail 30s load). Schema is stable at runtime, so memoize per table name. Business tables are JSON-row
     * stored (no physical table) per json-row-storage rule, so most lookups are stable "absent" results.
     */
    private final Map<String, List<String>> pkColumnsCache = new ConcurrentHashMap<>();
    private final Map<String, Boolean> tableExistsCache = new ConcurrentHashMap<>();

    private List<String> resolvePkColumnsCached(String safeTableName) {
        List<String> cached = pkColumnsCache.get(safeTableName);
        if (cached != null) {
            return cached;
        }
        List<String> resolved = PostgresPhysicalTablePrimaryKeys.resolvePrimaryKeyColumns(jdbcTemplate, safeTableName);
        pkColumnsCache.put(safeTableName, resolved);
        return resolved;
    }

    private boolean subTableExists(String tableName) {
        Boolean cached = tableExistsCache.get(tableName);
        if (cached != null) {
            return cached;
        }
        boolean exists;
        try {
            String resolved = jdbcTemplate.queryForObject("SELECT to_regclass(?)::text", String.class, tableName);
            exists = resolved != null && !resolved.isBlank();
        } catch (Exception e) {
            exists = false;
        }
        tableExistsCache.put(tableName, exists);
        return exists;
    }

    private record MiRowProgress(String statusColumn, String nodeColumn, String status, String currentNode) {}

    private Map<String, MiRowProgress> lookupMiProgressForDesignerTable(
            Map<String, Map<String, MiRowProgress>> byTable,
            String designerTableName) {
        if (designerTableName == null || designerTableName.isBlank()) {
            return Collections.emptyMap();
        }
        Map<String, MiRowProgress> direct = byTable.get(designerTableName);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() != null && designerTableName.equalsIgnoreCase(e.getKey())) {
                return e.getValue();
            }
        }
        String dn = normalizeMiTableKey(designerTableName);
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() != null && normalizeMiTableKey(e.getKey()).equals(dn)) {
                return e.getValue();
            }
        }
        /*
         * Designer binding labels often suffix the BPMN MI scope token (subtable vs subtable2).
         * Prefer longest engine-table prefix so unrelated tables never inherit overlay (replaces blind singleton).
         */
        Map<String, MiRowProgress> bestPrefix = null;
        int bestPrefixLen = -1;
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            String ek = normalizeMiTableKey(e.getKey());
            if (ek.length() < 8) {
                continue;
            }
            if (dn.startsWith(ek) && dn.length() > ek.length() && ek.length() > bestPrefixLen) {
                bestPrefixLen = ek.length();
                bestPrefix = e.getValue();
            }
        }
        if (bestPrefix != null) {
            return bestPrefix;
        }
        /*
         * Prefixed logical names (dw_* / scope segments) embed the MI token — pick longest engine key contained in dn.
         */
        Map<String, MiRowProgress> bestContain = null;
        int bestContainLen = -1;
        for (Map.Entry<String, Map<String, MiRowProgress>> e : byTable.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            String ek = normalizeMiTableKey(e.getKey());
            if (ek.length() < 8) {
                continue;
            }
            if (dn.contains(ek) && ek.length() > bestContainLen) {
                bestContainLen = ek.length();
                bestContain = e.getValue();
            }
        }
        if (bestContain != null) {
            return bestContain;
        }
        return Collections.emptyMap();
    }

    private static String normalizeMiTableKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static final long MI_STATUS_CACHE_TTL_MS = 5000L;
    private final Map<String, MiStatusCacheEntry> miStatusCache = new ConcurrentHashMap<>();

    private record MiStatusCacheEntry(Map<String, Object> payload, long timestampMs) {}

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, MiRowProgress>> resolveMiRowProgress(String processInstanceId, String processInstanceStatus) {
        if (processInstanceId == null || processInstanceId.isBlank()) {
            return Collections.emptyMap();
        }
        if (!workflowEngineClient.isAvailable()) {
            return Collections.emptyMap();
        }
        try {
            long __t = System.nanoTime();
            Map<String, Object> data;
            MiStatusCacheEntry cached = miStatusCache.get(processInstanceId);
            if (cached != null
                    && System.currentTimeMillis() - cached.timestampMs() < MI_STATUS_CACHE_TTL_MS) {
                data = cached.payload();
                log.info("[PERF] resolveMiRowProgress.getMultiInstanceStatus CACHE HIT for {}", processInstanceId);
            } else {
                Optional<Map<String, Object>> opt = workflowEngineClient.getMultiInstanceStatus(processInstanceId);
                log.info("[PERF] resolveMiRowProgress.getMultiInstanceStatus(engine) took {} ms",
                        (System.nanoTime() - __t) / 1_000_000L);
                if (opt.isEmpty()) {
                    return Collections.emptyMap();
                }
                data = opt.get();
                miStatusCache.put(processInstanceId, new MiStatusCacheEntry(data, System.currentTimeMillis()));
            }
            Object tasksObj = data.get("tasks");
            if (!(tasksObj instanceof List<?> tasks)) {
                return Collections.emptyMap();
            }
            long __tpk = System.nanoTime();
            int[] __pkCalls = {0};
            Map<String, Map<String, List<Map<String, Object>>>> byTableRow = new HashMap<>();
            for (Object o : tasks) {
                if (!(o instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> t = (Map<String, Object>) raw;
                Object tableObj = t.get("subTableName");
                String tn = tableObj != null ? String.valueOf(tableObj).trim() : "";
                if (tn.isEmpty()) {
                    continue;
                }
                List<String> pkCols;
                try {
                    __pkCalls[0]++;
                    pkCols = resolvePkColumnsCached(tn);
                } catch (Exception e) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> rowKey = t.get("subTableRowKey") instanceof Map<?, ?>
                        ? SubTableRowKeySupport.normalizeStringKeyMap((Map<?, ?>) t.get("subTableRowKey"))
                        : null;
                if (rowKey == null || !SubTableRowKeySupport.isComplete(pkCols, rowKey)) {
                    rowKey = SubTableRowKeySupport.rowKeyFromExtendedProps(t, pkCols);
                }
                if (rowKey == null) {
                    continue;
                }
                String canon = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
                byTableRow.computeIfAbsent(tn, k -> new HashMap<>())
                        .computeIfAbsent(canon, k -> new ArrayList<>())
                        .add(t);
            }
            log.info("[PERF] resolveMiRowProgress: {} MI tasks, {} resolvePrimaryKeyColumns(JDBC) calls, loop took {} ms",
                    tasks.size(), __pkCalls[0], (System.nanoTime() - __tpk) / 1_000_000L);

            Map<String, Map<String, MiRowProgress>> out = new HashMap<>();
            boolean processEndedCompleted = processInstanceStatus != null
                    && "COMPLETED".equalsIgnoreCase(processInstanceStatus.trim());
            for (var e : byTableRow.entrySet()) {
                String tableName = e.getKey();
                Map<String, List<Map<String, Object>>> rows = e.getValue();
                Map<String, MiRowProgress> rowProgress = new HashMap<>();
                for (var re : rows.entrySet()) {
                    String rowCanon = re.getKey();
                    List<Map<String, Object>> rowTasks = re.getValue();
                    if (rowTasks == null || rowTasks.isEmpty()) {
                        continue;
                    }

                    String statusCol = firstNonBlank(stringVal(rowTasks.get(0).get("miTaskStatusField")), "task_status");
                    String nodeCol = firstNonBlank(stringVal(rowTasks.get(0).get("miTaskCurrentNodeField")), "task_current_node");

                    MiRowProgress computed;
                    if (processEndedCompleted) {
                        // Runtime is gone; wf_extended_task_info may leave stray non-terminal rows. Never show MI as in-flight.
                        computed = new MiRowProgress(statusCol, nodeCol, "COMPLETED", "end");
                    } else {
                        List<Map<String, Object>> saneRowTasks = dedupeMiTasksPreferCompletedPerStepKey(rowTasks);
                        Map<String, Object> active = pickLatestActiveTask(saneRowTasks);
                        if (active != null) {
                            String node = firstNonBlank(stringVal(active.get("taskName")), "-");
                            computed = new MiRowProgress(statusCol, nodeCol, "IN_PROGRESS", node);
                        } else {
                            computed = new MiRowProgress(statusCol, nodeCol, "COMPLETED", "end");
                        }
                    }
                    rowProgress.put(rowCanon, computed);
                }
                if (!rowProgress.isEmpty()) {
                    out.put(tableName, rowProgress);
                }
            }

            return out;
        } catch (Exception e) {
            log.debug("resolveMiRowProgress skipped: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Same sub-table row may retain multiple extended tasks per BPMN step (completed + orphan CREATED/ASSIGNED).
     * Without dedupe, portal may show an earlier step as current (e.g. still sub form1).
     */
    private static List<Map<String, Object>> dedupeMiTasksPreferCompletedPerStepKey(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.size() <= 1) {
            return tasks;
        }
        Map<String, List<Map<String, Object>>> byStep = new LinkedHashMap<>();
        for (Map<String, Object> t : tasks) {
            String key = miAggregateStepKey(t);
            byStep.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<Map<String, Object>> out = new ArrayList<>(tasks.size());
        for (List<Map<String, Object>> group : byStep.values()) {
            boolean hasCompleted = group.stream()
                    .anyMatch(x -> "COMPLETED".equalsIgnoreCase(stringVal(x.get("status"))));
            if (hasCompleted) {
                group.stream()
                        .filter(x -> "COMPLETED".equalsIgnoreCase(stringVal(x.get("status"))))
                        .max(Comparator.comparing(x -> {
                            LocalDateTime done = parseMiStatusLocalDateTime(x.get("completedTime"));
                            if (done != null) {
                                return done;
                            }
                            LocalDateTime created = parseMiStatusLocalDateTime(x.get("createdTime"));
                            return created != null ? created : LocalDateTime.MIN;
                        }))
                        .ifPresent(out::add);
            } else {
                // Same BPMN step, no terminal COMPLETED snapshot — overlapping extended rows explode candidate count;
                // a late orphaned CREATED must not outweigh a real ASSIGNED row on a later BPMN step in pickLatestActiveTask.
                pickRepresentativeOverlappingMiExtendedRow(group).ifPresent(out::add);
            }
        }
        return out.isEmpty() ? tasks : out;
    }

    private static String miAggregateStepKey(Map<String, Object> t) {
        String defKey = stringVal(t.get("taskDefinitionKey"));
        if (defKey != null && !defKey.isBlank()) {
            return defKey.trim();
        }
        String name = stringVal(t.get("taskName"));
        if (name != null && !name.isBlank()) {
            return name.trim().replaceAll("\\s+", " ");
        }
        String taskId = stringVal(t.get("taskId"));
        return taskId != null && !taskId.isBlank() ? taskId.trim() : ("anon:" + System.identityHashCode(t));
    }

    /**
     * When several {@code wf_extended_task_info} rows collide on the same step key and none are completed,
     * prefer the row that mirrors what Flowable still keeps as a real workload (assignee/status), not stray CREATED.
     */
    private static Optional<Map<String, Object>> pickRepresentativeOverlappingMiExtendedRow(List<Map<String, Object>> group) {
        if (group == null || group.isEmpty()) {
            return Optional.empty();
        }
        if (group.size() == 1) {
            return Optional.of(group.get(0));
        }
        Map<String, Object> best = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            Map<String, Object> cand = group.get(i);
            if (compareOverlappingMiExtendedRows(cand, best) > 0) {
                best = cand;
            }
        }
        return Optional.of(best);
    }

    /**
     * Higher score ⇒ more authoritative for overlapping extended MI rows sharing a step key or across sequential steps.
     */
    private static int miOverlappingExtendedTaskAuthority(Map<String, Object> t) {
        if (t == null) {
            return -10_000;
        }
        String st = stringVal(t.get("status"));
        if ("COMPLETED".equalsIgnoreCase(st) || "CANCELLED".equalsIgnoreCase(st)) {
            return -10_000;
        }
        String assignee = stringVal(t.get("assignee"));
        boolean hasAssignee = assignee != null && !assignee.isBlank();
        if ("ASSIGNED".equalsIgnoreCase(st) && hasAssignee) {
            return 500;
        }
        if ("IN_PROGRESS".equalsIgnoreCase(st)) {
            return 450;
        }
        if ("CREATED".equalsIgnoreCase(st) && hasAssignee) {
            return 300;
        }
        if ("CREATED".equalsIgnoreCase(st)) {
            return 100;
        }
        return 200;
    }

    /**
     * &gt;0 if {@code a} should win over {@code b} when both denote overlapping MI extension noise.
     */
    private static int compareOverlappingMiExtendedRows(Map<String, Object> a, Map<String, Object> b) {
        int ca = miOverlappingExtendedTaskAuthority(a);
        int cb = miOverlappingExtendedTaskAuthority(b);
        if (ca != cb) {
            return Integer.compare(ca, cb);
        }
        LocalDateTime ta = parseMiStatusLocalDateTime(a.get("createdTime"));
        LocalDateTime tb = parseMiStatusLocalDateTime(b.get("createdTime"));
        if (ta != null && tb != null && !ta.equals(tb)) {
            return ta.compareTo(tb);
        }
        if (ta != null && tb == null) {
            return 1;
        }
        if (ta == null && tb != null) {
            return -1;
        }
        String ida = Objects.toString(stringVal(a.get("taskId")), "");
        String idb = Objects.toString(stringVal(b.get("taskId")), "");
        return ida.compareTo(idb);
    }

    /** LocalDateTime in API Map may be an ISO string */
    private static LocalDateTime parseMiStatusLocalDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return LocalDateTime.parse(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Object> pickLatestActiveTask(List<Map<String, Object>> tasks) {
        Map<String, Object> best = null;
        for (Map<String, Object> t : tasks) {
            String st = stringVal(t.get("status"));
            if ("COMPLETED".equalsIgnoreCase(st) || "CANCELLED".equalsIgnoreCase(st)) {
                continue;
            }
            if (best == null) {
                best = t;
                continue;
            }
            int cmp = compareOverlappingMiExtendedRows(t, best);
            if (cmp > 0) {
                best = t;
                continue;
            }
            if (cmp < 0) {
                continue;
            }
            int ordT = miSubFormOrdinalHint(stringVal(t.get("taskName")));
            int ordB = miSubFormOrdinalHint(stringVal(best.get("taskName")));
            if (ordT != Integer.MIN_VALUE && ordB != Integer.MIN_VALUE && ordT > ordB) {
                best = t;
            }
        }
        return best;
    }

    private static String stringVal(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> resolveSubTableBindingTableNames(ProcessInstanceInfo info) {
        Map<String, String> result = new HashMap<>();
        String functionUnitRef = firstNonBlank(
                info.getFunctionUnitCatalogId(),
                info.getFunctionUnitCode(),
                info.getProcessDefinitionKey()
        );
        if (functionUnitRef == null || functionUnitRef.isBlank()) {
            return result;
        }
        try {
            Map<String, Object> content = getFunctionUnitContent(functionUnitRef);
            Object formsObj = content.get("forms");
            if (!(formsObj instanceof List<?> forms)) {
                return result;
            }
            for (Object formObj : forms) {
                if (!(formObj instanceof Map<?, ?> form)) {
                    continue;
                }
                Object bindingsObj = form.get("tableBindings");
                if (!(bindingsObj instanceof List<?> bindings)) {
                    continue;
                }
                for (Object bindingObj : bindings) {
                    if (!(bindingObj instanceof Map<?, ?> binding)) {
                        continue;
                    }
                    Object bindingType = binding.get("bindingType");
                    Object bindingId = binding.get("bindingId");
                    Object tableName = binding.get("tableName");
                    // SUB and RELATED both participate in __subTables__ (designer + MI write-back).
                    // RELATED-only bindings were previously skipped, so initiator My Request never merged
                    // physical row data into variables and sub-task filled columns appeared empty.
                    String bt = bindingType != null ? String.valueOf(bindingType) : "";
                    if (("SUB".equals(bt) || "RELATED".equals(bt)) && bindingId != null && tableName != null) {
                        String phys = String.valueOf(tableName);
                        String bid = String.valueOf(bindingId);
                        result.put(bid, phys);
                        Object displayName = binding.get("tableDisplayName");
                        if (displayName != null && !String.valueOf(displayName).isBlank()) {
                            String label = String.valueOf(displayName).trim();
                            result.putIfAbsent(label, phys);
                            result.putIfAbsent(normalizeMiTableKey(label), phys);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("resolveSubTableBindingTableNames skipped: {}", e.getMessage());
        }
        return result;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * If a sub-table row task_status is PENDING but the engine's wf_extended_task_info
     * already records the task as COMPLETED, update the DB row to match AND recover
     * the form field values from Flowable's historical execution variables.
     * This self-heals rows that were stuck before the writeBack fix.
     */
    private void repairStaleTaskStatus(String tableName, Map<String, Object> dbRow, Map<String, Object> rowKey,
                                       List<String> pkCols) {
        Object ts = dbRow.get("task_status");
        if (ts != null && !"PENDING".equals(String.valueOf(ts))) {
            return;
        }
        if (!columnExists(tableName, "task_status")) {
            return;
        }
        if (pkCols.size() != 1 || !(rowKey.get(pkCols.get(0)) instanceof Number)) {
            return;
        }
        long rowId = ((Number) rowKey.get(pkCols.get(0))).longValue();
        String pkColumn = pkCols.get(0);
        try {
            List<Map<String, Object>> taskEntries = jdbcTemplate.query(
                    "SELECT e.task_id, e.status FROM wf_extended_task_info e "
                            + "WHERE e.is_deleted = false "
                            + "AND (e.extended_properties LIKE '%\"subTableRowId\":' || ? || ',%' "
                            + "  OR e.extended_properties LIKE '%\"subTableRowId\":' || ? || '}%')",
                    (rs, i) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("task_id", rs.getString("task_id"));
                        m.put("status", rs.getString("status"));
                        return m;
                    }, rowId, rowId);

            String completedTaskId = taskEntries.stream()
                    .filter(e -> "COMPLETED".equals(e.get("status")))
                    .map(e -> (String) e.get("task_id"))
                    .findFirst().orElse(null);

            if (completedTaskId == null) {
                return;
            }

            // 1. Fix task_status
            StringBuilder statusSql = new StringBuilder("UPDATE ").append(tableName)
                    .append(" SET task_status = 'COMPLETED'");
            if (columnExists(tableName, "task_current_node")) {
                statusSql.append(", task_current_node = NULL");
                dbRow.put("task_current_node", null);
            }
            statusSql.append(" WHERE ").append(pkColumn).append(" = ? AND task_status = 'PENDING'");
            jdbcTemplate.update(statusSql.toString(), rowId);
            dbRow.put("task_status", "COMPLETED");

            // 2. Recover form field values from Flowable execution history.
            recoverFormFieldsFromHistory(tableName, dbRow, rowKey, pkCols, completedTaskId);

            log.info("repairStaleTaskStatus: fixed {} row {} -> COMPLETED (task {})", tableName, rowId, completedTaskId);
        } catch (Exception e) {
            log.debug("repairStaleTaskStatus skipped for {}#{}: {}", tableName, rowKey, e.getMessage());
        }
    }

    /**
     * Read the Flowable execution-scope variables that were saved when the subtask
     * was completed, and write matching columns back to the configured sub-table.
     */
    private void recoverFormFieldsFromHistory(String tableName, Map<String, Object> dbRow, Map<String, Object> rowKey,
                                              List<String> pkCols, String taskId) {
        try {
            List<String> execIds = jdbcTemplate.query(
                    "SELECT EXECUTION_ID_ FROM ACT_HI_TASKINST WHERE ID_ = ?",
                    (rs, i) -> rs.getString(1), taskId);
            if (execIds.isEmpty()) {
                return;
            }

            List<Map<String, Object>> histVars = jdbcTemplate.query(
                    "SELECT NAME_, TEXT_ FROM ACT_HI_VARINST WHERE EXECUTION_ID_ = ? AND TEXT_ IS NOT NULL",
                    (rs, i) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", rs.getString("NAME_"));
                        m.put("value", rs.getString("TEXT_"));
                        return m;
                    }, execIds.get(0));

            Set<String> validCols = dbRow.keySet();
            Set<String> skipCols = new HashSet<>(List.of(
                    "row_version", "task_status", "task_current_node", "meeting_id", "sort_order"));
            for (String pk : pkCols) {
                skipCols.add(pk);
            }
            Map<String, Object> updates = new HashMap<>();
            for (Map<String, Object> hv : histVars) {
                String name = (String) hv.get("name");
                Object value = hv.get("value");
                if (name == null || value == null) {
                    continue;
                }
                String col = SubTablePhysicalColumnResolver.resolvePhysicalColumnKey(
                        jdbcTemplate, tableName, name, validCols);
                if (col != null && !skipCols.contains(col)) {
                    updates.put(col, value);
                }
            }
            if (updates.isEmpty()) {
                return;
            }

            StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" SET ");
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                sql.append(entry.getKey()).append(" = ?, ");
                params.add(entry.getValue());
            }
            sql.setLength(sql.length() - 2);
            sql.append(" WHERE ").append(SubTableRowKeySupport.buildPkWhereClause(pkCols));
            params.addAll(Arrays.asList(SubTableRowKeySupport.orderedPkParams(pkCols, rowKey)));

            jdbcTemplate.update(sql.toString(), params.toArray());
            dbRow.putAll(updates);
            log.info("recoverFormFieldsFromHistory: recovered {} fields for {} rowKey {}", updates.size(), tableName, rowKey);
        } catch (Exception e) {
            log.debug("recoverFormFieldsFromHistory skipped for {} {}: {}", tableName, rowKey, e.getMessage());
        }
    }

    private String requireSafeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name");
        }
        return identifier;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                        "WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?",
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private String resolveUsernameById(String userId) {
        try {
            List<String> names = jdbcTemplate.query(
                    "SELECT COALESCE(username, display_name) FROM sys_users WHERE id = ? LIMIT 1",
                    (rs, i) -> rs.getString(1), userId);
            return names.isEmpty() ? userId : names.get(0);
        } catch (Exception e) {
            log.debug("resolveUsernameById failed for {}: {}", userId, e.getMessage());
            return userId;
        }
    }

    // ==================== Process actions (withdraw, urge, favorite) ====================

    /**
     * Withdraws process
     */
    public boolean withdrawProcess(String userId, String processId, String reason) {
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
        if (optInstance.isEmpty()) {
            return false;
        }
        
        ProcessInstance instance = optInstance.get();
        if (!instance.getStartUserId().equals(userId)) {
            return false;
        }
        if (!"RUNNING".equals(instance.getStatus())) {
            return false;
        }
        
        // Set status to withdrawn
        instance.setStatus("WITHDRAWN");
        instance.setEndTime(LocalDateTime.now());
        processInstanceRepository.save(instance);
        
        // Cancel process instance via Flowable engine
        try {
            if (workflowEngineClient.isAvailable()) {
                Optional<Map<String, Object>> cancelResult = workflowEngineClient
                        .cancelProcessInstance(processId,
                                reason != null ? reason : i18nService.getMessage("portal.process.withdraw.default_reason"));
                if (cancelResult.isPresent()) {
                    log.info("Flowable process instance cancelled: {}", processId);
                } else {
                    log.warn("Failed to cancel Flowable process instance: {}, local status already updated", processId);
                }
            } else {
                log.warn("Workflow engine not available, skipped Flowable cancellation for process: {}", processId);
            }
        } catch (Exception e) {
            log.warn("Failed to cancel Flowable process instance {}: {}", processId, e.getMessage());
        }
        
        return true;
    }

    /**
     * Return running process to the first completed user-task node (initiator revision).
     * Only the process starter may invoke this (My Requests → Draft).
     */
    public boolean returnProcessToFirstStep(String userId, String processId, String comment) {
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
        if (optInstance.isEmpty()) {
            return false;
        }
        ProcessInstance instance = optInstance.get();
        if (!instance.getStartUserId().equals(userId)) {
            return false;
        }
        if (!"RUNNING".equals(instance.getStatus())) {
            return false;
        }
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available, cannot return process {} to first step", processId);
            return false;
        }

        Optional<String> activeTaskId = findActiveEngineTaskId(processId);
        if (activeTaskId.isEmpty()) {
            log.warn("No active task for process {} — cannot return to first step", processId);
            return false;
        }
        Optional<String> firstActivityId = findFirstReturnableActivityId(activeTaskId.get());
        if (firstActivityId.isEmpty()) {
            log.warn("No returnable first activity for task {} (process {})", activeTaskId.get(), processId);
            return false;
        }

        String reason = comment != null && !comment.isBlank()
                ? comment
                : i18nService.getMessage("portal.process.return_to_first.default_reason");

        Optional<Map<String, Object>> returnResult = workflowEngineClient.returnTask(
                activeTaskId.get(), firstActivityId.get(), userId, reason, "DRAFT");
        if (returnResult.isEmpty()) {
            return false;
        }
        Map<String, Object> data = returnResult.get();
        if (Boolean.FALSE.equals(data.get("success"))) {
            log.warn("Engine return-to-first failed for process {}: {}", processId, data.get("message"));
            return false;
        }

        refreshRunningProcessCurrentNodeFromEngine(processId, instance);
        log.info("Process {} returned to first step ({}) by initiator {}", processId, firstActivityId.get(), userId);
        return true;
    }

    @SuppressWarnings("unchecked")
    private Optional<String> findActiveEngineTaskId(String processInstanceId) {
        Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(processInstanceId);
        if (tasksResult.isEmpty()) {
            return Optional.empty();
        }
        Object tasksObj = tasksResult.get().get("tasks");
        if (!(tasksObj instanceof List<?> tasks) || tasks.isEmpty()) {
            return Optional.empty();
        }
        Object first = tasks.get(0);
        if (!(first instanceof Map<?, ?> taskMap)) {
            return Optional.empty();
        }
        Object taskId = taskMap.get("taskId");
        if (taskId == null) {
            taskId = taskMap.get("id");
        }
        return taskId != null ? Optional.of(String.valueOf(taskId)) : Optional.empty();
    }

    private Optional<String> findFirstReturnableActivityId(String taskId) {
        Optional<List<Map<String, Object>>> activitiesOpt = workflowEngineClient.getReturnableActivities(taskId);
        if (activitiesOpt.isEmpty() || activitiesOpt.get().isEmpty()) {
            return Optional.empty();
        }
        List<Map<String, Object>> activities = activitiesOpt.get();
        Map<String, Object> firstStep = activities.get(activities.size() - 1);
        Object activityId = firstStep.get("taskId");
        if (activityId == null) {
            activityId = firstStep.get("activityId");
        }
        return activityId != null ? Optional.of(String.valueOf(activityId)) : Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private void refreshRunningProcessCurrentNodeFromEngine(String processId, ProcessInstance instance) {
        if (!"RUNNING".equals(instance.getStatus()) || !workflowEngineClient.isAvailable()) {
            return;
        }
        try {
            Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(processId);
            if (tasksResult.isEmpty()) {
                return;
            }
            Map<String, Object> tasksData = tasksResult.get();
            if (tasksData == null) {
                return;
            }
            List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
            if (tasks == null || tasks.isEmpty()) {
                return;
            }
            Map<String, Object> currentTask = tasks.get(0);
            String currentNodeName = (String) currentTask.get("taskName");
            ProcessAssigneeSnapshot snapshot = ProcessAssigneeSnapshot.fromEngineTask(currentTask);
            if (currentNodeName != null && !currentNodeName.isEmpty()) {
                instance.setCurrentNode(currentNodeName);
                instance.setCurrentAssignee(snapshot.getAssigneeUserId());
                instance.setCandidateUsers(snapshot.getCandidateUserIds());
                processInstanceRepository.save(instance);
            }
        } catch (Exception e) {
            log.warn("Failed to refresh currentNode after return-to-first for process {}: {}", processId, e.getMessage());
        }
    }

    /**
     * Urges process
     */
    public boolean urgeProcess(String userId, String processId) {
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
        if (optInstance.isEmpty()) {
            return false;
        }
        
        ProcessInstance instance = optInstance.get();
        if (!instance.getStartUserId().equals(userId)) {
            return false;
        }
        if (!"RUNNING".equals(instance.getStatus())) {
            return false;
        }
        // Send urge notification — messaging can be integrated here
        log.info("Urging process: {} by user: {}", processId, userId);
        return true;
    }

    /**
     * Toggles favorite status
     */
    public boolean toggleFavorite(String userId, String processKey) {
        Optional<FavoriteProcess> existing = favoriteProcessRepository.findByUserIdAndProcessDefinitionKey(userId, processKey);
        if (existing.isPresent()) {
            favoriteProcessRepository.delete(existing.get());
            return false;
        } else {
            FavoriteProcess favorite = new FavoriteProcess();
            favorite.setUserId(userId);
            favorite.setProcessDefinitionKey(processKey);
            favorite.setCreatedAt(LocalDateTime.now());
            favoriteProcessRepository.save(favorite);
            return true;
        }
    }

    // ==================== Draft management (delegated to ProcessDraftComponent) ====================

    /**
     * Saves draft
     * @see ProcessDraftComponent#saveDraft(String, String, Map)
     */
    public ProcessDraft saveDraft(String userId, String processKey, Map<String, Object> formData) {
        return processDraftComponent.saveDraft(userId, processKey, formData);
    }

    /**
     * Loads draft
     * @see ProcessDraftComponent#getDraft(String, String)
     */
    public Optional<ProcessDraft> getDraft(String userId, String processKey) {
        return processDraftComponent.getDraft(userId, processKey);
    }

    /**
     * Deletes draft
     * @see ProcessDraftComponent#deleteDraft(String, String)
     */
    public void deleteDraft(String userId, String processKey) {
        processDraftComponent.deleteDraft(userId, processKey);
    }
    
    /**
     * Lists drafts for user
     * @see ProcessDraftComponent#getDraftList(String)
     */
    public List<Map<String, Object>> getDraftList(String userId) {
        return processDraftComponent.getDraftList(userId);
    }
    
    /**
     * Deletes draft by ID
     * @see ProcessDraftComponent#deleteDraftById(String, Long)
     */
    public void deleteDraftById(String userId, Long draftId) {
        processDraftComponent.deleteDraftById(userId, draftId);
    }
    
    /**
     * Returns full function unit content (BPMN, forms, action bindings, etc.)
     * Checks function unit is enabled; throws when disabled.
     * Results are cached in-memory (5 min TTL) to avoid repeated admin-center HTTP round-trips.
     */
    public Map<String, Object> getFunctionUnitContent(String userId, String functionUnitIdOrCode) {
        log.info("Getting function unit content for: {}, user: {}", functionUnitIdOrCode, userId);

        // Must use raw processKey/code/id: resolve-then-check passes UUID and breaks processKey cache eviction (FunctionUnitAccessComponent)
        functionUnitAccessComponent.checkFunctionUnitAccess(userId, functionUnitIdOrCode);
        String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
        log.info("Resolved function unit ID: {}", functionUnitId);

        // Check cache before making HTTP call to admin-center
        CachedFuContent cached = fuContentCache.get(functionUnitId);
        if (cached != null && !cached.isExpired()) {
            return cached.payload();
        }
        
        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (!payload.isEmpty()) {
                log.info("Got function unit content: name={}", payload.get("name"));
                fuContentCache.put(functionUnitId, new CachedFuContent(payload, System.currentTimeMillis()));
                return payload;
            }
            
            return Collections.emptyMap();
            
        } catch (FunctionUnitAccessComponent.FunctionUnitDisabledException | 
                 FunctionUnitAccessComponent.FunctionUnitAccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get function unit content for {}: {}", functionUnitId, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
    
    /**
     * Returns full function unit content without permission check (internal).
     * Primary source: admin-center (function unit catalog with BPMN, forms, etc.).
     * If admin-center returns no BPMN, falls back to workflow-engine (deployed BPMN by functionUnitCode).
     * Results are cached in-memory (5 min TTL) to avoid repeated admin-center HTTP round-trips.
     */
    public Map<String, Object> getFunctionUnitContent(String functionUnitIdOrCode) {
        log.info("Getting function unit content for: {}", functionUnitIdOrCode);

        try {
            // Resolve function unit ID (code or ID)
            String functionUnitId = functionUnitIdOrCode;
            try {
                functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
                log.info("Resolved function unit ID: {}", functionUnitId);
            } catch (Exception e) {
                log.warn("Could not resolve functionUnitId for {}, using as-is: {}", functionUnitIdOrCode, e.getMessage());
            }

            // Check cache before making HTTP call
            CachedFuContent cached = fuContentCache.get(functionUnitId);
            if (cached != null && !cached.isExpired()) {
                return cached.payload();
            }

            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (!payload.isEmpty()) {
                log.info("Got function unit content from admin-center: name={}", payload.get("name"));
                // Cache successful result
                fuContentCache.put(functionUnitId, new CachedFuContent(payload, System.currentTimeMillis()));
                return payload;
            }

            log.warn("Admin-center returned empty content for functionUnitId={}; attempting fallback to workflow-engine BPMN", functionUnitId);

            // Fallback: fetch BPMN from workflow-engine by functionUnitCode, wrap in same shape
            Map<String, Object> fallbackResult = loadBpmnFallbackFromEngine(functionUnitIdOrCode);
            if (fallbackResult != null && !fallbackResult.isEmpty() && !fallbackResult.containsKey("error")) {
                fuContentCache.put(functionUnitId, new CachedFuContent(fallbackResult, System.currentTimeMillis()));
            }
            return fallbackResult;

        } catch (Exception e) {
            log.error("Failed to get function unit content for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            // Attempt fallback before giving up
            try {
                Map<String, Object> fallback = loadBpmnFallbackFromEngine(functionUnitIdOrCode);
                if (fallback != null && !fallback.isEmpty()) {
                    log.info("Function unit content loaded via workflow-engine fallback for {}", functionUnitIdOrCode);
                    return fallback;
                }
            } catch (Exception ignored) { /* fall through to error result */ }
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }

    /**
     * Fallback: load BPMN from workflow-engine using functionUnitCode (or functionUnitId if it's a code).
     * Returns a payload compatible with getFunctionUnitContent shape: { name, processes: [{ data: bpmnXml }] }
     */
    private Map<String, Object> loadBpmnFallbackFromEngine(String functionUnitIdOrCode) {
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available for BPMN fallback");
            return Collections.emptyMap();
        }
        try {
            Optional<String> bpmnOpt = workflowEngineClient.getBpmnXml(functionUnitIdOrCode);
            if (bpmnOpt.isPresent() && bpmnOpt.get() != null && !bpmnOpt.get().isBlank()) {
                log.info("BPMN loaded from workflow-engine for key: {}", functionUnitIdOrCode);
                Map<String, Object> result = new HashMap<>();
                result.put("name", functionUnitIdOrCode);
                result.put("code", functionUnitIdOrCode);
                List<Map<String, Object>> processes = new ArrayList<>();
                Map<String, Object> processEntry = new HashMap<>();
                processEntry.put("data", bpmnOpt.get());
                processes.add(processEntry);
                result.put("processes", processes);
                return result;
            } else {
                log.warn("Workflow engine returned no BPMN for key: {}", functionUnitIdOrCode);
            }
        } catch (Exception e) {
            log.warn("Failed to load BPMN from workflow-engine for {}: {}", functionUnitIdOrCode, e.getMessage());
        }
        return Collections.emptyMap();
    }
    
    /**
     * Returns function unit content of a given type (form dialogs, etc.)
     * 
     * Uses /function-units/{id}/content then filters client-side
     * Spring ResourceHttpRequestHandler intercepts some specific path patterns
     */
    public List<Map<String, Object>> getFunctionUnitContents(String functionUnitIdOrCode, String contentType) {
        log.info("Getting function unit contents for: {}, contentType: {}", functionUnitIdOrCode, contentType);
        
        try {
            // Resolve function unit ID (code or name)
            String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
            log.info("Resolved function unit ID: {}", functionUnitId);
            
            // Use generic /content endpoint for all content
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            
            if (!payload.isEmpty()) {
                // Extract array for requested content type
                String key = contentType.equalsIgnoreCase("FORM") ? "forms" :
                            contentType.equalsIgnoreCase("PROCESS") ? "processes" :
                            contentType.equalsIgnoreCase("DATA_TABLE") ? "dataTables" : null;
                
                if (key != null && payload.containsKey(key)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> contents = (List<Map<String, Object>>) payload.get(key);
                    log.info("Got {} contents of type {} from key '{}'", contents.size(), contentType, key);
                    return contents;
                } else {
                    log.warn("Payload does not contain key '{}' for contentType '{}'", key, contentType);
                }
            } else {
                log.warn("Got empty payload from admin center (unwrap)");
            }
            
            return Collections.emptyList();
            
        } catch (Exception e) {
            log.error("Failed to get function unit contents for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Returns process history
     * Calls workflow-engine process history with resolved user display names
     */
    public List<Map<String, Object>> getProcessHistory(String processId) {
        log.debug("ProcessComponent.getProcessHistory called for: {}", processId);
        
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available, returning empty history");
            return Collections.emptyList();
        }
        
        log.debug("Workflow engine is available, calling getProcessInstanceHistory");
        
        try {
            // Calls workflow-engine process instance history API by processInstanceId
            // Queries Flowable historic activities and resolves display names
            Optional<List<Map<String, Object>>> historyResult = workflowEngineClient.getProcessInstanceHistory(processId);
            
            if (historyResult.isPresent()) {
                List<Map<String, Object>> history = historyResult.get();
                log.debug("Got {} history records for process: {}", history.size(), processId);
                return history;
            } else {
                log.warn("Failed to get process history from workflow engine for process: {}", processId);
                return Collections.emptyList();
            }
            
        } catch (Exception e) {
            log.error("Failed to get process history for {}: {}", processId, e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Marks process as completed
     * Invoked by workflow-engine process completion listener
     */
    public void markProcessAsCompleted(String processId, String lastActivityName) {
        log.debug("ProcessComponent.markProcessAsCompleted called for: {} with lastActivity: {}", 
                processId, lastActivityName);
        
        try {
            Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
            if (optInstance.isEmpty()) {
                log.warn("Process instance not found in local database: {}", processId);
                return;
            }
            
            ProcessInstance instance = optInstance.get();
            
            // Update only processes still RUNNING
            if ("RUNNING".equals(instance.getStatus())) {
                instance.setStatus("COMPLETED");
                LocalDateTime finishedAt = LocalDateTime.now();
                instance.setEndTime(finishedAt);
                instance.setCompletedAt(finishedAt);
                // Completed processes have no current step; see process history for last node
                instance.setCurrentNode(null);
                // Clear current assignee
                instance.setCurrentAssignee(null);
                processInstanceRepository.save(instance);
                log.info("Process instance {} marked as COMPLETED (current step cleared)", processId);
            } else {
                log.info("Process instance {} already has status: {}, skipping update", 
                        processId, instance.getStatus());
            }
            
        } catch (Exception e) {
            log.error("Failed to mark process as completed for {}: {}", processId, e.getMessage(), e);
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
                        Object rowId = row.get("id");
                        changes.add(SubTableChange.builder()
                                .changeType("ROW_ADD")
                                .rowIdentifier(String.valueOf(rowId))
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

    /**
     * Get action definitions by IDs.
     * Delegates to ActionDefinitionRepository.
     */
    public List<ActionDefinition> getActionsByIds(List<String> ids) {
        List<ActionDefinition> actions = actionDefinitionRepository.findAllById(ids);
        if (actions.isEmpty()) {
            log.warn("No actions found for ids: {}", ids);
        }
        return actions;
    }
}
