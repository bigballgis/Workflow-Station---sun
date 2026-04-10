package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.ChangeHistoryContext;
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

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    // ==================== 流程定义与发起 ====================

    /**
     * 获取可发起的流程定义列表
     * 从管理员中心获取已部署的功能单元，并根据用户的业务角色过滤
     */
    public List<ProcessDefinitionInfo> getAvailableProcessDefinitions(String userId, String category, String keyword) {
        log.info("Getting available process definitions for user: {}", userId);
        List<ProcessDefinitionInfo> definitions = new ArrayList<>();
        
        try {
            // 尝试从管理员中心获取已部署的功能单元
            String url = adminCenterUrl + "/api/v1/admin/function-units/deployed/latest";
            log.info("Fetching latest deployed function units from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                List<Map<String, Object>> units = ApiResponseBodyUnwrap.normalizeToListOfMaps(response);
                
                if (!units.isEmpty()) {
                    log.info("Got {} deployed function units", units.size());
                
                    // 根据用户的业务角色过滤可访问的功能单元
                    List<Map<String, Object>> accessibleUnits = functionUnitAccessComponent.filterAccessibleFunctionUnits(userId, units);
                    log.info("After filtering, {} function units are accessible to user {}", accessibleUnits.size(), userId);
                
                    for (Map<String, Object> unit : accessibleUnits) {
                        ProcessDefinitionInfo info = ProcessDefinitionInfo.builder()
                                .id((String) unit.get("id"))
                                .key((String) unit.get("code"))
                                .name((String) unit.get("name"))
                                .description((String) unit.get("description"))
                                .category("业务流程")
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
        
        // 如果没有可访问的流程，返回空列表（不再使用模拟数据）
        // 前端会显示"暂无可发起的流程"提示
        if (definitions.isEmpty()) {
            log.info("No accessible process definitions found for user: {}", userId);
        }

        // 过滤
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

        // 标记收藏
        List<FavoriteProcess> favorites = favoriteProcessRepository.findByUserIdOrderByDisplayOrderAsc(userId);
        Set<String> favoriteKeys = new HashSet<>();
        favorites.forEach(f -> favoriteKeys.add(f.getProcessDefinitionKey()));
        definitions.forEach(d -> d.setIsFavorite(favoriteKeys.contains(d.getKey())));

        return definitions;
    }

    /**
     * 发起流程
     * 通过 WorkflowEngineClient 调用 Flowable 引擎
     */
    @Transactional
    public ProcessInstanceInfo startProcess(String userId, String processKey, ProcessStartRequest request) {
        if (processKey == null || processKey.isEmpty()) {
            throw new IllegalArgumentException("流程Key不能为空");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        Optional<ActiveCatalogPin> activePinOpt = fetchActiveCatalogForStart(processKey);
        if (activePinOpt.isEmpty()) {
            throw new IllegalStateException(
                    "当前没有可用于发起的已部署且已启用的功能单元版本，请在管理中心完成部署并激活: " + processKey);
        }
        ActiveCatalogPin pin = activePinOpt.get();

        String resolvedFunctionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(processKey);
        if (!pin.catalogId().equals(resolvedFunctionUnitId)) {
            throw new FunctionUnitAccessComponent.FunctionUnitAccessDeniedException(
                    "与当前门户可发起版本不一致，请刷新流程列表后重试");
        }

        functionUnitAccessComponent.checkFunctionUnitAccess(userId, pin.catalogId());

        // 获取流程定义名称和 BPMN XML
        String processName = processKey;
        String bpmnXml = null;
        
        try {
            Map<String, Object> content = getFunctionUnitContent(pin.catalogId());
            if (content != null) {
                if (content.get("name") != null) {
                    processName = (String) content.get("name");
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> processes = (List<Map<String, Object>>) content.get("processes");
                if (processes != null && !processes.isEmpty()) {
                    bpmnXml = (String) processes.get(0).get("data");
                }
            }
        } catch (FunctionUnitAccessComponent.FunctionUnitDisabledException | 
                 FunctionUnitAccessComponent.FunctionUnitAccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to get process info for {}: {}", processKey, e.getMessage());
        }

        if (bpmnXml == null) {
            throw new IllegalStateException("无法获取流程定义 BPMN: " + processKey);
        }

        // 检查 Flowable 引擎是否可用
        if (!workflowEngineClient.isAvailable()) {
            throw new IllegalStateException("Flowable 引擎不可用，请检查 workflow-engine-core 服务是否启动");
        }

        log.info("Using Flowable engine to start process: {}", processKey);
        
        // 先部署流程定义（如果尚未部署）
        String actualProcessKey = processKey; // 默认使用传入的 key
        Optional<Map<String, Object>> deployResult = workflowEngineClient.deployProcess(processKey, bpmnXml, processName);
        if (deployResult.isPresent()) {
            Map<String, Object> deployed = deployResult.get();
            log.info("Process definition deployed: {}", deployed);
            // WorkflowEngineClient 已 unwrap ApiResponse.data，此处为部署结果顶层 Map（含 processDefinitionKey）
            Object pdk = deployed.get("processDefinitionKey");
            if (pdk != null && !pdk.toString().isEmpty()) {
                actualProcessKey = pdk.toString();
                log.info("Using actual process definition key from deployment: {}", actualProcessKey);
            }
        } else {
            // 部署失败或引擎返回空，尝试从 BPMN XML 中提取 process id 作为 key
            String bpmnProcessId = extractProcessIdFromBpmn(bpmnXml);
            if (bpmnProcessId != null && !bpmnProcessId.isEmpty()) {
                actualProcessKey = bpmnProcessId;
                log.info("Deploy returned empty, using BPMN process id as key: {}", actualProcessKey);
            }
        }

        // 启动流程实例：剥离客户端伪造的工作台键；有 UBR 的用户必须携带有效 JWT 工作台上下文（与 hasContext 一致）
        Map<String, Object> variables = request.getFormData() != null ? new HashMap<>(request.getFormData()) : new HashMap<>();
        variables.remove("activeBusinessUnitId");
        variables.remove("activeRoleId");
        variables.put("initiator", userId);
        // 会议参与人演示功能单元 fu-20260403-a1b2c5：「分配参与人」节点使用 INITIATOR；脚本/后续逻辑仍读取 participant_assigner_user_id
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
                        "无法校验登录身份与工作台，请重新登录后再发起流程");
            }
            String jwtBu = PortalUserSecurityUtils.getCurrentActiveBusinessUnitId().orElse("").trim();
            String jwtRole = PortalUserSecurityUtils.getCurrentActiveRoleId().orElse("").trim();
            if (jwtBu.isEmpty() || jwtRole.isEmpty()) {
                throw new PortalException("400",
                        "您的账号关联业务单元角色，发起流程前请先登录，并在顶部选择工作台，或重新登录后再试");
            }
            if (!portalWorkspaceAuthService.hasContext(userId, jwtBu, jwtRole)) {
                throw new PortalException("400",
                        "当前工作台身份已失效（权限可能已调整），请切换工作台或重新登录后再发起");
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

        Optional<Map<String, Object>> startResult = workflowEngineClient.startProcess(
                actualProcessKey, request.getBusinessKey(), userId, variables);

        if (startResult.isEmpty()) {
            throw new IllegalStateException("启动流程失败: " + processKey);
        }
        
        // startResult 已为 unwrap 后的 ProcessInstanceResult 字段 Map，无嵌套 data
        Map<String, Object> data = startResult.get();
        if (data == null || data.get("processInstanceId") == null) {
            throw new IllegalStateException("启动流程返回数据为空: " + processKey);
        }

        String flowableProcessInstanceId = (String) data.get("processInstanceId");
        log.info("Process started via Flowable: {}", flowableProcessInstanceId);
        
        // 立即保存流程实例到本地数据库（状态为 RUNNING），防止 ProcessCompletionListener 回调时找不到记录
        String startUserDisplayName = resolveUserDisplayName(userId);
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
        
        // 自动完成第一个任务（发起人任务）
        // 流程启动后，第一个任务通常是发起人填写表单的任务，需要自动完成以流转到下一个审批节点
        String currentNodeName = null;
        String currentAssigneeId = null;
        String currentAssigneeName = null;
        String initiatorTaskIdForHistory = null;
        String initiatorTaskDefKeyForHistory = null;

        try {
            // 查询流程实例的任务
            Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(flowableProcessInstanceId);
            if (tasksResult.isPresent()) {
                Map<String, Object> tasksData = tasksResult.get();
                if (tasksData != null) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                    if (tasks != null && !tasks.isEmpty()) {
                        // 获取第一个任务
                        Map<String, Object> firstTask = tasks.get(0);
                        String taskId = (String) firstTask.get("taskId");
                        initiatorTaskIdForHistory = taskId;
                        Object taskDefKey = firstTask.get("taskDefinitionKey");
                        initiatorTaskDefKeyForHistory = taskDefKey != null ? taskDefKey.toString() : null;
                        log.info("Auto-completing first task: {} for process: {}", taskId, flowableProcessInstanceId);

                        // 先认领任务（设置 assignee）
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

                        // 用户字段可能反序列化为 { userId } 而无 id，引擎侧若误用 Map#toString 会撑爆 Flowable varchar(255)
                        coerceUserRefVariablesForEngine(variables);
                        
                        // 计算子表条件变量（如 requestItemsHasHighValue）并注入
                        computeSubTableConditionVariables(variables);

                        // 完成第一个任务
                        Optional<Map<String, Object>> completeResult = workflowEngineClient.completeTask(
                                taskId, userId, "SUBMIT", variables);
                        if (completeResult.isPresent()
                                && !Boolean.FALSE.equals(completeResult.get().get("success"))) {
                            log.info("First task completed successfully: {}", taskId);
                            
                            // 完成第一个任务后，查询当前任务（下一个审批节点）
                            Optional<Map<String, Object>> nextTasksResult = workflowEngineClient.getProcessInstanceTasks(flowableProcessInstanceId);
                            if (nextTasksResult.isPresent()) {
                                Map<String, Object> nextTasksData = nextTasksResult.get();
                                if (nextTasksData != null) {
                                    @SuppressWarnings("unchecked")
                                    List<Map<String, Object>> nextTasks = (List<Map<String, Object>>) nextTasksData.get("tasks");
                                    if (nextTasks != null && !nextTasks.isEmpty()) {
                                        Map<String, Object> currentTask = nextTasks.get(0);
                                        currentNodeName = (String) currentTask.get("taskName");
                                        // 使用 currentAssignee 或 assignmentTarget 字段
                                        currentAssigneeId = (String) currentTask.get("currentAssignee");
                                        if (currentAssigneeId == null) {
                                            currentAssigneeId = (String) currentTask.get("assignmentTarget");
                                        }
                                        // 获取当前处理人名称
                                        currentAssigneeName = (String) currentTask.get("currentAssigneeName");
                                        if (currentAssigneeName == null || currentAssigneeName.isEmpty()) {
                                            // 如果没有名称，解析用户ID为名称
                                            if (currentAssigneeId != null && !currentAssigneeId.isEmpty()) {
                                                currentAssigneeName = resolveUserDisplayName(currentAssigneeId);
                                            }
                                        }
                                        
                                        log.info("Current task after auto-complete: node={}, assignee={}, assigneeName={}", 
                                                currentNodeName, currentAssigneeId, currentAssigneeName);
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
            // 不抛出异常，流程已经启动成功
        }

        // 首任务由引擎 API 直接完成，不经过 TaskFormComponent，在此补写字段级变更历史（与待办提交路径一致）
        recordInitialSubmitChangeHistory(
                flowableProcessInstanceId,
                initiatorTaskIdForHistory,
                initiatorTaskDefKeyForHistory,
                userId,
                variables);

        // 更新流程实例（补充当前节点和处理人信息）
        // 使用条件 UPDATE 避免覆盖 ProcessCompletionListener 回调已设置的 COMPLETED 状态（竞态条件）
        // JPA 一级缓存会导致 findById 返回旧对象，所以必须用 @Modifying 原生更新绕过缓存
        String assignee = currentAssigneeName != null ? currentAssigneeName : currentAssigneeId;
        int updated = processInstanceRepository.updateCurrentNodeIfNotCompleted(
                flowableProcessInstanceId, currentNodeName, assignee);
        if (updated > 0) {
            log.info("Process instance updated in local database: {} with currentNode={}, currentAssignee={}",
                    flowableProcessInstanceId, currentNodeName, assignee);
        } else {
            log.info("Process instance {} already COMPLETED, skipped currentNode update (race condition avoided)",
                    flowableProcessInstanceId);
        }
        
        // 记录流程启动历史
        ProcessHistory startHistory = ProcessHistory.builder()
                .processInstanceId(flowableProcessInstanceId)
                .activityId("startEvent")
                .activityName("提交申请")
                .activityType("startEvent")
                .operationType("SUBMIT")
                .operatorId(userId)
                .operatorName(startUserDisplayName)
                .comment("发起流程")
                .build();
        processHistoryRepository.save(startHistory);
        
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
                .currentAssignee(currentAssigneeName != null ? currentAssigneeName : currentAssigneeId)
                .functionUnitCatalogId(pin.catalogId())
                .functionUnitCode(pin.code())
                .functionUnitVersionLabel(pin.versionLabel())
                .build();
    }

    private record ActiveCatalogPin(String catalogId, String code, String versionLabel) {}

    /**
     * 从 admin 获取当前 code 下「已部署+已启用」中语义版本最高的目录行（与 /deployed/latest 规则一致）
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
     * 从 BPMN XML 中提取 <process id="..."> 属性值
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
     * 解析 BPMN XML 获取第一个需要审批的用户任务信息（跳过发起人任务）
     */
    private Map<String, String> parseFirstUserTask(String bpmnXml, Map<String, Object> formData, String initiatorId) {
        Map<String, String> result = new HashMap<>();
        log.info("Parsing BPMN XML for first user task, initiatorId: {}", initiatorId);
        log.info("BPMN XML length: {}", bpmnXml != null ? bpmnXml.length() : 0);
        
        try {
            // 查找所有 userTask 标签
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
                
                // 找到完整的 userTask 元素（包括子元素）
                int userTaskEnd = findClosingTag(bpmnXml, userTaskStart, "userTask");
                if (userTaskEnd == -1) {
                    userTaskEnd = findClosingTag(bpmnXml, userTaskStart, "bpmn:userTask");
                }
                if (userTaskEnd == -1) {
                    // 自闭合标签
                    userTaskEnd = bpmnXml.indexOf("/>", userTaskStart);
                    if (userTaskEnd == -1) {
                        break;
                    }
                    userTaskEnd += 2;
                }
                
                String userTaskElement = bpmnXml.substring(userTaskStart, userTaskEnd);
                taskCount++;
                
                // 提取任务名称
                String name = extractAttribute(userTaskElement, "name");
                
                // 从 custom:properties 解析（与 developer-workstation 设计器、workflow-engine 监听器对齐）
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
                    // 回退到标准属性解析
                    assignee = extractAttribute(userTaskElement, "camunda:assignee");
                    if (assignee == null) {
                        assignee = extractAttribute(userTaskElement, "flowable:assignee");
                    }
                    if (assignee == null) {
                        assignee = extractAttribute(userTaskElement, "assignee");
                    }
                }
                
                // 跳过发起人任务（第一个任务通常是发起人填写表单）
                boolean isInitiatorTask = "initiator".equalsIgnoreCase(assigneeType)
                    || "INITIATOR".equalsIgnoreCase(assigneeType)
                    || "PROCESS_INITIATOR".equalsIgnoreCase(assigneeType)
                    || (assignee != null && (assignee.equals("${initiator}") || assignee.equals(initiatorId)));
                
                if (!isInitiatorTask || taskCount > 1) {
                    // 这是需要审批的任务
                    if (name != null) {
                        result.put("name", name);
                    }
                    
                    // 解析 assignee 变量（如果还没解析）
                    if (assignee != null) {
                        if (assignee.startsWith("${") && assignee.endsWith("}")) {
                            String varName = assignee.substring(2, assignee.length() - 1);
                            assignee = resolveProcessVariable(varName, formData, initiatorId);
                        }
                        result.put("assignee", assignee);
                    }
                    
                    // 设置候选用户
                    if (candidateUsers != null) {
                        result.put("candidateUsers", candidateUsers);
                        if (result.get("assignee") == null) {
                            result.put("assignee", candidateUsers.split(",")[0]);
                        }
                    }
                    
                    // 检查是否有标准的 candidateUsers（会签任务）
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
                    
                    // 检查是否有 candidateGroups（组任务）
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
     * 从 custom:properties 中提取属性值
     */
    private String extractCustomProperty(String element, String propertyName) {
        try {
            // 查找 custom:property 标签
            String searchPattern = "name=\"" + propertyName + "\"";
            int propIndex = element.indexOf(searchPattern);
            if (propIndex == -1) {
                return null;
            }
            
            // 找到这个 property 标签的 value 属性
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
     * 找到闭合标签的位置
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
     * 解析旧版分配类型（向后兼容）
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
     * 解析流程变量
     */
    private String resolveProcessVariable(String varName, Map<String, Object> formData, String initiatorId) {
        // 首先检查表单数据
        if (formData != null && formData.containsKey(varName)) {
            return String.valueOf(formData.get(varName));
        }
        
        // 处理特殊变量（使用新的7种标准类型）
        return switch (varName) {
            case "initiator" -> initiatorId;
            case "entityManager" -> getEntityManager(initiatorId);
            case "functionManager" -> getFunctionManager(initiatorId);
            default -> null;
        };
    }
    
    /**
     * 解析候选用户表达式（支持多个变量，如 ${entityManager},${functionManager}）
     */
    private List<String> resolveCandidateUsers(String candidateUsersExpr, Map<String, Object> formData, String initiatorId) {
        List<String> result = new ArrayList<>();
        
        if (candidateUsersExpr == null || candidateUsersExpr.isEmpty()) {
            return result;
        }
        
        // 分割多个候选用户表达式
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
                // 直接是用户ID
                result.add(expr);
            }
        }
        
        return result;
    }
    
    /**
     * 获取发起人的实体管理者
     */
    private String getEntityManager(String initiatorId) {
        try {
            // 首先尝试通过用户ID查询
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
            
            // 如果通过ID查询失败，尝试通过用户名查询
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
     * 获取发起人的职能管理者
     */
    private String getFunctionManager(String initiatorId) {
        try {
            // 首先尝试通过用户ID查询
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
            
            // 如果通过ID查询失败，尝试通过用户名查询
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
     * 带缓存的用户显示名称解析，避免同一批次内重复 HTTP 调用
     */
    private String resolveUserDisplayNameCached(String userId, Map<String, String> cache) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        return cache.computeIfAbsent(userId, this::resolveUserDisplayName);
    }
    
    /**
     * 解析用户显示名称
     * 优先级: fullName > displayName > username > userId
     */
    private String resolveUserDisplayName(String userId) {
        if (userId == null || userId.isEmpty()) {
            return null;
        }
        
        try {
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + userId;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> rawUser = restTemplate.getForObject(userUrl, Map.class);
            Map<String, Object> userInfo = ApiResponseBodyUnwrap.unwrapDataMap(rawUser);
            
            if (userInfo != null && !userInfo.isEmpty()) {
                // 优先使用 fullName
                String fullName = (String) userInfo.get("fullName");
                if (fullName != null && !fullName.isEmpty()) {
                    return fullName;
                }
                
                // 其次使用 displayName
                String displayName = (String) userInfo.get("displayName");
                if (displayName != null && !displayName.isEmpty()) {
                    return displayName;
                }
                
                // 再次使用 username
                String username = (String) userInfo.get("username");
                if (username != null && !username.isEmpty()) {
                    return username;
                }
            }
        } catch (Exception e) {
            log.warn("Failed to resolve user display name for {}: {}", userId, e.getMessage());
        }
        
        // 最后回退到使用 userId
        return userId;
    }
    
    /**
     * 从 XML 标签中提取属性值
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
        // 尝试单引号
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

    // ==================== 流程查询 ====================

    /**
     * 获取我的申请列表
     */
    public Page<ProcessInstanceInfo> getMyApplications(String userId, String status, Pageable pageable) {
        log.info("Getting applications for user: {}, status: {}", userId, status);
        
        Page<ProcessInstance> instancePage;
        if (status != null && !status.isEmpty()) {
            instancePage = processInstanceRepository.findByStartUserIdAndStatusOrderByStartTimeDesc(userId, status, pageable);
        } else {
            instancePage = processInstanceRepository.findByStartUserIdOrderByStartTimeDesc(userId, pageable);
        }

        Map<String, String> userNameCache = new HashMap<>();
        // 列表接口不返回 variables：库内 JSONB 可能含 Jackson 无法序列化的嵌套结构，会在写响应时触发 HttpMessageNotWritableException → SYS_INTERNAL_ERROR
        List<ProcessInstanceInfo> instances = instancePage.getContent().stream()
                .map(inst -> toProcessInstanceInfo(inst, userNameCache))
                .peek(info -> info.setVariables(null))
                .toList();

        return new PageImpl<>(instances, pageable, instancePage.getTotalElements());
    }
    
    /**
     * 转换实体到DTO（无缓存版本，用于单个流程查询）
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance) {
        return toProcessInstanceInfo(instance, new HashMap<>());
    }
    
    /**
     * 转换实体到DTO（带方法级用户名缓存，避免列表查询 N+1）
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance, Map<String, String> userNameCache) {
        String currentAssignee = instance.getCurrentAssignee();
        String currentAssigneeName = null;
        
        log.debug("toProcessInstanceInfo: processId={}, status={}, currentAssignee from DB={}", 
                instance.getId(), instance.getStatus(), currentAssignee);
        
        if (currentAssignee != null && !currentAssignee.isEmpty() && "RUNNING".equals(instance.getStatus())) {
            try {
                if (workflowEngineClient.isAvailable()) {
                    Optional<Map<String, Object>> tasksResult = workflowEngineClient.getProcessInstanceTasks(instance.getId());
                    if (tasksResult.isPresent()) {
                        Map<String, Object> tasksData = tasksResult.get();
                        if (tasksData != null) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> tasks = (List<Map<String, Object>>) tasksData.get("tasks");
                            if (tasks != null && !tasks.isEmpty()) {
                                Map<String, Object> currentTask = tasks.get(0);
                                currentAssigneeName = (String) currentTask.get("currentAssigneeName");
                                if (currentAssigneeName == null || currentAssigneeName.isEmpty()) {
                                    currentAssigneeName = resolveUserDisplayNameCached(currentAssignee, userNameCache);
                                }
                            } else {
                                log.debug("No active tasks found for process instance {}, clearing current assignee", instance.getId());
                                currentAssigneeName = null;
                                currentAssignee = null;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get current assignee name for process {}: {}", instance.getId(), e.getMessage());
                currentAssigneeName = resolveUserDisplayNameCached(currentAssignee, userNameCache);
            }
        }
        
        if (currentAssigneeName == null && currentAssignee != null) {
            currentAssigneeName = resolveUserDisplayNameCached(currentAssignee, userNameCache);
        }
        
        log.debug("toProcessInstanceInfo: final currentAssigneeName={}", currentAssigneeName);
        
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
                .currentNode(instance.getCurrentNode())
                .currentAssignee(currentAssigneeName)
                .candidateUsers(instance.getCandidateUsers())
                .variables(instance.getVariables())
                .functionUnitCatalogId(instance.getFunctionUnitCatalogId())
                .functionUnitCode(instance.getFunctionUnitCode())
                .functionUnitVersionLabel(instance.getFunctionUnitVersionLabel())
                .build();
    }

    /**
     * 获取流程详情
     * 如果本地数据库中没有当前节点信息，从 Flowable 实时获取
     */
    public ProcessInstanceInfo getProcessDetail(String processId) {
        Optional<ProcessInstance> optInstance = processInstanceRepository.findById(processId);
        if (optInstance.isEmpty()) {
            return null;
        }
        
        ProcessInstance instance = optInstance.get();
        ProcessInstanceInfo info = toProcessInstanceInfo(instance);
        
        // 如果流程正在运行且没有当前节点信息，从 Flowable 实时获取
        if ("RUNNING".equals(instance.getStatus()) && 
            (info.getCurrentNode() == null || info.getCurrentAssignee() == null)) {
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
                                String currentAssigneeId = (String) currentTask.get("currentAssignee");
                                if (currentAssigneeId == null) {
                                    currentAssigneeId = (String) currentTask.get("assignmentTarget");
                                }
                                
                                // 获取当前处理人名称
                                String currentAssigneeName = (String) currentTask.get("currentAssigneeName");
                                if (currentAssigneeName == null || currentAssigneeName.isEmpty()) {
                                    // 如果没有名称，尝试解析用户ID为名称
                                    if (currentAssigneeId != null && !currentAssigneeId.isEmpty()) {
                                        currentAssigneeName = resolveUserDisplayName(currentAssigneeId);
                                    }
                                }
                                
                                // 更新返回的信息
                                info.setCurrentNode(currentNodeName);
                                info.setCurrentAssignee(currentAssigneeName != null ? currentAssigneeName : currentAssigneeId);
                                
                                // 同时更新本地数据库（保存用户名称而不是ID）
                                instance.setCurrentNode(currentNodeName);
                                instance.setCurrentAssignee(currentAssigneeName != null ? currentAssigneeName : currentAssigneeId);
                                processInstanceRepository.save(instance);
                                
                                log.info("Updated process instance {} with currentNode={}, currentAssignee={}", 
                                        processId, currentNodeName, currentAssigneeName);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to get current task info from Flowable for process {}: {}", processId, e.getMessage());
            }
        }

        enrichSubTablesWithAssignmentData(info);
        
        return info;
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
        if (!(subTablesObj instanceof Map<?, ?>)) {
            return;
        }
        Map<String, Object> subTables = (Map<String, Object>) subTablesObj;
        List<Map<String, Object>> rowsWithId = new ArrayList<>();
        for (Object v : subTables.values()) {
            if (!(v instanceof List<?> list)) {
                continue;
            }
            for (Object rowObj : list) {
                if (rowObj instanceof Map<?, ?> row) {
                    Object rowId = row.get("id");
                    if (rowId == null) rowId = ((Map<?, ?>) row).get("rowId");
                    if (rowId != null) rowsWithId.add((Map<String, Object>) row);
                }
            }
        }
        if (rowsWithId.isEmpty()) {
            return;
        }
        try {
            for (Map<String, Object> row : rowsWithId) {
                Object rowId = row.get("id");
                if (rowId == null) rowId = row.get("rowId");
                Long id;
                if (rowId instanceof Number n) {
                    id = n.longValue();
                } else {
                    try { id = Long.parseLong(String.valueOf(rowId).trim()); } catch (Exception ignored) { continue; }
                }
                List<Map<String, Object>> dbRows = jdbcTemplate.query(
                        "SELECT * FROM participants WHERE id = ?",
                        (rs, i) -> {
                            java.sql.ResultSetMetaData meta = rs.getMetaData();
                            Map<String, Object> m = new HashMap<>();
                            for (int c = 1; c <= meta.getColumnCount(); c++) {
                                m.put(meta.getColumnName(c), rs.getObject(c));
                            }
                            return m;
                        }, id);
                if (!dbRows.isEmpty()) {
                    Map<String, Object> dbRow = dbRows.get(0);
                    String displayName = (String) dbRow.get("assignee_display_name");
                    String userId = (String) dbRow.get("assignee_user_id");
                    if (displayName == null && userId != null && !userId.isBlank()) {
                        displayName = resolveUsernameById(userId);
                        dbRow.put("assignee_display_name", displayName);
                    }
                    // Self-heal: if DB row is still PENDING but engine task is COMPLETED,
                    // repair the status so the initiator sees the correct state.
                    repairStaleTaskStatus(dbRow, id);
                    for (Map.Entry<String, Object> entry : dbRow.entrySet()) {
                        if (entry.getValue() != null) {
                            row.put(entry.getKey(), entry.getValue());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("enrichSubTablesWithAssignmentData skipped: {}", e.getMessage());
        }
    }

    /**
     * If participants.task_status is PENDING but the engine's wf_extended_task_info
     * already records the task as COMPLETED, update the DB row to match AND recover
     * the form field values from Flowable's historical execution variables.
     * This self-heals rows that were stuck before the writeBack fix.
     */
    private void repairStaleTaskStatus(Map<String, Object> dbRow, Long participantId) {
        Object ts = dbRow.get("task_status");
        if (ts != null && !"PENDING".equals(String.valueOf(ts))) {
            return;
        }
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
                    }, participantId, participantId);

            String completedTaskId = taskEntries.stream()
                    .filter(e -> "COMPLETED".equals(e.get("status")))
                    .map(e -> (String) e.get("task_id"))
                    .findFirst().orElse(null);

            if (completedTaskId == null) {
                return;
            }

            // 1. Fix task_status
            jdbcTemplate.update(
                    "UPDATE participants SET task_status = 'COMPLETED' WHERE id = ? AND task_status = 'PENDING'",
                    participantId);
            dbRow.put("task_status", "COMPLETED");

            // 2. Recover form field values from Flowable execution history.
            //    Cross-table access to ACT_HI_* is intentional — self-healing one-time
            //    repair for rows where writeBackSubTableRow was never called.
            recoverFormFieldsFromHistory(dbRow, participantId, completedTaskId);

            log.info("repairStaleTaskStatus: fixed participant {} → COMPLETED (task {})", participantId, completedTaskId);
        } catch (Exception e) {
            log.debug("repairStaleTaskStatus skipped for {}: {}", participantId, e.getMessage());
        }
    }

    /**
     * Read the Flowable execution-scope variables that were saved when the subtask
     * was completed, and write matching columns back to the participants table.
     */
    private void recoverFormFieldsFromHistory(Map<String, Object> dbRow, Long participantId, String taskId) {
        try {
            List<String> execIds = jdbcTemplate.query(
                    "SELECT EXECUTION_ID_ FROM ACT_HI_TASKINST WHERE ID_ = ?",
                    (rs, i) -> rs.getString(1), taskId);
            if (execIds.isEmpty()) return;

            List<Map<String, Object>> histVars = jdbcTemplate.query(
                    "SELECT NAME_, TEXT_ FROM ACT_HI_VARINST WHERE EXECUTION_ID_ = ? AND TEXT_ IS NOT NULL",
                    (rs, i) -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", rs.getString("NAME_"));
                        m.put("value", rs.getString("TEXT_"));
                        return m;
                    }, execIds.get(0));

            Set<String> validCols = dbRow.keySet();
            Set<String> skipCols = Set.of("id", "row_version", "task_status", "meeting_id", "sort_order");
            Map<String, Object> updates = new HashMap<>();
            for (Map<String, Object> hv : histVars) {
                String name = (String) hv.get("name");
                Object value = hv.get("value");
                if (name != null && value != null && validCols.contains(name) && !skipCols.contains(name)) {
                    updates.put(name, value);
                }
            }
            if (updates.isEmpty()) return;

            StringBuilder sql = new StringBuilder("UPDATE participants SET ");
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                sql.append(entry.getKey()).append(" = ?, ");
                params.add(entry.getValue());
            }
            sql.setLength(sql.length() - 2);
            sql.append(" WHERE id = ?");
            params.add(participantId);

            jdbcTemplate.update(sql.toString(), params.toArray());
            dbRow.putAll(updates);
            log.info("recoverFormFieldsFromHistory: recovered {} fields for participant {}", updates.size(), participantId);
        } catch (Exception e) {
            log.debug("recoverFormFieldsFromHistory skipped for participant {}: {}", participantId, e.getMessage());
        }
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

    // ==================== 流程操作（撤回、催办、收藏） ====================

    /**
     * 撤回流程
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
        
        // 更新状态为已撤回
        instance.setStatus("WITHDRAWN");
        instance.setEndTime(LocalDateTime.now());
        processInstanceRepository.save(instance);
        
        // 调用 Flowable 引擎取消流程实例
        try {
            if (workflowEngineClient.isAvailable()) {
                Optional<Map<String, Object>> cancelResult = workflowEngineClient
                        .cancelProcessInstance(processId, reason != null ? reason : "用户撤回");
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
     * 催办流程
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
        // 发送催办通知 - 通知逻辑可以在此处集成
        log.info("Urging process: {} by user: {}", processId, userId);
        return true;
    }

    /**
     * 切换收藏状态
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

    // ==================== 草稿管理（委托给 ProcessDraftComponent） ====================

    /**
     * 保存草稿
     * @see ProcessDraftComponent#saveDraft(String, String, Map)
     */
    public ProcessDraft saveDraft(String userId, String processKey, Map<String, Object> formData) {
        return processDraftComponent.saveDraft(userId, processKey, formData);
    }

    /**
     * 获取草稿
     * @see ProcessDraftComponent#getDraft(String, String)
     */
    public Optional<ProcessDraft> getDraft(String userId, String processKey) {
        return processDraftComponent.getDraft(userId, processKey);
    }

    /**
     * 删除草稿
     * @see ProcessDraftComponent#deleteDraft(String, String)
     */
    public void deleteDraft(String userId, String processKey) {
        processDraftComponent.deleteDraft(userId, processKey);
    }
    
    /**
     * 获取用户的草稿列表
     * @see ProcessDraftComponent#getDraftList(String)
     */
    public List<Map<String, Object>> getDraftList(String userId) {
        return processDraftComponent.getDraftList(userId);
    }
    
    /**
     * 根据ID删除草稿
     * @see ProcessDraftComponent#deleteDraftById(String, Long)
     */
    public void deleteDraftById(String userId, Long draftId) {
        processDraftComponent.deleteDraftById(userId, draftId);
    }
    
    /**
     * 获取功能单元完整内容（BPMN、表单、动作绑定等）
     * 会检查功能单元是否启用，禁用时抛出异常
     */
    public Map<String, Object> getFunctionUnitContent(String userId, String functionUnitIdOrCode) {
        log.info("Getting function unit content for: {}, user: {}", functionUnitIdOrCode, userId);
        
        // 解析功能单元 ID（支持 code 或 ID）
        String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
        log.info("Resolved function unit ID: {}", functionUnitId);
        
        // 检查功能单元访问权限（包含启用状态检查）
        functionUnitAccessComponent.checkFunctionUnitAccess(userId, functionUnitId);
        
        try {
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (!payload.isEmpty()) {
                log.info("Got function unit content: name={}", payload.get("name"));
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
     * 获取功能单元完整内容（不检查权限，用于内部调用）
     */
    public Map<String, Object> getFunctionUnitContent(String functionUnitIdOrCode) {
        log.info("Getting function unit content for: {}", functionUnitIdOrCode);
        
        try {
            // 先解析功能单元 ID（支持 code 或名称）
            String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
            log.info("Resolved function unit ID: {}", functionUnitId);
            
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            if (!payload.isEmpty()) {
                log.info("Got function unit content: name={}", payload.get("name"));
                return payload;
            }
            
            return Collections.emptyMap();
            
        } catch (Exception e) {
            log.error("Failed to get function unit content for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
    
    /**
     * 获取功能单元特定类型的内容（用于表单弹窗等场景）
     * 
     * 使用 /function-units/{id}/content 端点获取所有内容，然后在客户端过滤
     * 这是因为 Spring 的 ResourceHttpRequestHandler 会拦截某些特定路径模式
     */
    public List<Map<String, Object>> getFunctionUnitContents(String functionUnitIdOrCode, String contentType) {
        log.info("Getting function unit contents for: {}, contentType: {}", functionUnitIdOrCode, contentType);
        
        try {
            // 先解析功能单元 ID（支持 code 或名称）
            String functionUnitId = functionUnitAccessComponent.resolveFunctionUnitId(functionUnitIdOrCode);
            log.info("Resolved function unit ID: {}", functionUnitId);
            
            // 使用通用的 /content 端点获取所有内容
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            Map<String, Object> payload = ApiResponseBodyUnwrap.unwrapDataMap(response);
            
            if (!payload.isEmpty()) {
                // 根据内容类型提取对应的数组
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
     * 获取流程历史记录
     * 调用 workflow-engine 的流程历史接口，返回已解析用户名称的历史记录
     */
    public List<Map<String, Object>> getProcessHistory(String processId) {
        log.debug("ProcessComponent.getProcessHistory called for: {}", processId);
        
        if (!workflowEngineClient.isAvailable()) {
            log.warn("Workflow engine not available, returning empty history");
            return Collections.emptyList();
        }
        
        log.debug("Workflow engine is available, calling getProcessInstanceHistory");
        
        try {
            // 直接调用 workflow-engine 的流程实例历史接口（通过 processInstanceId）
            // 该接口会查询 Flowable 的历史活动记录并解析用户名称
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
     * 标记流程为已完成
     * 由 workflow-engine 的流程完成监听器调用
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
            
            // 只更新状态为 RUNNING 的流程
            if ("RUNNING".equals(instance.getStatus())) {
                instance.setStatus("COMPLETED");
                LocalDateTime finishedAt = LocalDateTime.now();
                instance.setEndTime(finishedAt);
                instance.setCompletedAt(finishedAt);
                // 保存最后一个节点名称，而不是清空
                if (lastActivityName != null && !lastActivityName.isEmpty()) {
                    instance.setCurrentNode(lastActivityName);
                } else {
                    instance.setCurrentNode("已完成");
                }
                // 清空当前处理人
                instance.setCurrentAssignee(null);
                processInstanceRepository.save(instance);
                log.info("Process instance {} marked as COMPLETED with lastNode: {}", 
                        processId, instance.getCurrentNode());
            } else {
                log.info("Process instance {} already has status: {}, skipping update", 
                        processId, instance.getStatus());
            }
            
        } catch (Exception e) {
            log.error("Failed to mark process as completed for {}: {}", processId, e.getMessage(), e);
        }
    }

    /**
     * 从表单变量中计算子表条件变量，注入到 variables 中供网关条件判断使用。
     * 支持以下变量：
     *   requestItemsHasHighValue — 任意一条记录的 total_price > 10000（Boolean）
     *   totalPrice               — 所有记录 total_price 之和（Double，供 BPMN 直接比较）
     *   maxItemPrice             — 所有记录中最大的 total_price（Double）
     *   itemCount                — 子表记录总数（Integer）
     */
    @SuppressWarnings("unchecked")
    /**
     * 发起流程时提交的表单变量写入 up_change_history，便于申请单详情「变更历史」展示。
     * 排除引擎/快照内部键，避免噪声。
     */
    private void recordInitialSubmitChangeHistory(String processInstanceId,
                                                String taskInstanceId,
                                                String taskDefinitionKey,
                                                String userId,
                                                Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return;
        }
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : variables.entrySet()) {
            String k = e.getKey();
            if ("initiator".equals(k) || k.startsWith("_snapshot_")) {
                continue;
            }
            filtered.put(k, e.getValue());
        }
        if (filtered.isEmpty()) {
            return;
        }
        try {
            ChangeHistoryContext context = ChangeHistoryContext.builder()
                    .processInstanceId(processInstanceId)
                    .taskInstanceId(taskInstanceId)
                    .stageId(taskDefinitionKey)
                    .userId(userId)
                    .build();
            changeHistoryComponent.recordFieldChanges(context, Collections.emptyMap(), filtered);
        } catch (Exception e) {
            log.warn("Failed to record initial submit change history for process {}: {}",
                    processInstanceId, e.getMessage());
        }
    }

    /**
     * 将门户表单中的「用户」对象（id / userId / user_id / value）压成纯字符串，避免 workflow-engine
     * ASSIGNEE_FROM_VARIABLE 解析失败或误写入超长标识。
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
