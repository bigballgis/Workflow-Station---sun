package com.portal.component;

import com.portal.dto.ProcessDefinitionInfo;
import com.portal.dto.ProcessInstanceInfo;
import com.portal.dto.ProcessStartRequest;
import com.portal.entity.FavoriteProcess;
import com.portal.entity.ProcessDraft;
import com.portal.entity.ProcessHistory;
import com.portal.entity.ProcessInstance;
import com.portal.repository.FavoriteProcessRepository;
import com.portal.repository.ProcessDraftRepository;
import com.portal.repository.ProcessHistoryRepository;
import com.portal.repository.ProcessInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

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
    private final FunctionUnitAccessComponent functionUnitAccessComponent;
    
    @Value("${admin-center.url:http://localhost:8090}")
    private String adminCenterUrl;

    /**
     * 获取可发起的流程定义列表
     * 从管理员中心获取已部署的功能单元，并根据用户的业务角色过滤
     */
    public List<ProcessDefinitionInfo> getAvailableProcessDefinitions(String userId, String category, String keyword) {
        log.info("Getting available process definitions for user: {}", userId);
        List<ProcessDefinitionInfo> definitions = new ArrayList<>();
        
        try {
            // 尝试从管理员中心获取已部署的功能单元
            RestTemplate restTemplate = new RestTemplate();
            String url = adminCenterUrl + "/api/v1/admin/function-units/deployed";
            log.info("Fetching deployed function units from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null && response.containsKey("content")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> units = (List<Map<String, Object>>) response.get("content");
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
                            .version(1)
                            .build();
                    definitions.add(info);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to fetch deployed function units from admin center: {}", e.getMessage(), e);
        }
        
        // 如果没有从管理员中心获取到数据，使用模拟数据
        if (definitions.isEmpty()) {
            definitions.add(ProcessDefinitionInfo.builder()
                    .id("def-1").key("leave-request").name("请假申请")
                    .description("员工请假申请流程").category("人事").version(1).build());
            definitions.add(ProcessDefinitionInfo.builder()
                    .id("def-2").key("expense-claim").name("费用报销")
                    .description("费用报销申请流程").category("财务").version(1).build());
            definitions.add(ProcessDefinitionInfo.builder()
                    .id("def-3").key("purchase-request").name("采购申请")
                    .description("物资采购申请流程").category("采购").version(1).build());
        }

        // 过滤
        if (category != null && !category.isEmpty()) {
            definitions.removeIf(d -> !d.getCategory().equals(category));
        }
        if (keyword != null && !keyword.isEmpty()) {
            definitions.removeIf(d -> !d.getName().contains(keyword) && !d.getDescription().contains(keyword));
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
     */
    public ProcessInstanceInfo startProcess(String userId, String processKey, ProcessStartRequest request) {
        if (processKey == null || processKey.isEmpty()) {
            throw new IllegalArgumentException("流程Key不能为空");
        }
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        // 获取流程定义名称和第一个任务信息
        String processName = processKey;
        String firstTaskName = "待审批";
        String firstTaskAssignee = null;
        String candidateUsers = null;
        
        try {
            Map<String, Object> content = getFunctionUnitContent(processKey);
            if (content != null) {
                if (content.get("name") != null) {
                    processName = (String) content.get("name");
                }
                // 解析 BPMN 获取第一个用户任务信息
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> processes = (List<Map<String, Object>>) content.get("processes");
                if (processes != null && !processes.isEmpty()) {
                    String bpmnXml = (String) processes.get(0).get("data");
                    if (bpmnXml != null) {
                        Map<String, String> taskInfo = parseFirstUserTask(bpmnXml, request.getFormData(), userId);
                        if (taskInfo.get("name") != null) {
                            firstTaskName = taskInfo.get("name");
                        }
                        if (taskInfo.get("assignee") != null) {
                            firstTaskAssignee = taskInfo.get("assignee");
                        }
                        if (taskInfo.get("candidateUsers") != null) {
                            candidateUsers = taskInfo.get("candidateUsers");
                            log.info("Resolved candidate users for process: {}", candidateUsers);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to get process info for {}: {}", processKey, e.getMessage());
        }

        // 创建流程实例
        ProcessInstance instance = ProcessInstance.builder()
                .id(UUID.randomUUID().toString())
                .processDefinitionId("def-" + processKey)
                .processDefinitionKey(processKey)
                .processDefinitionName(processName)
                .businessKey(request.getBusinessKey())
                .startUserId(userId)
                .startUserName(userId)
                .currentNode(firstTaskName)
                .currentAssignee(firstTaskAssignee)
                .candidateUsers(candidateUsers)
                .status("RUNNING")
                .variables(request.getFormData())
                .priority(request.getPriority())
                .startTime(LocalDateTime.now())
                .build();

        // 保存到数据库
        processInstanceRepository.save(instance);
        log.info("Created process instance: {} for user: {}, assignee: {}, candidateUsers: {}", 
                instance.getId(), userId, firstTaskAssignee, candidateUsers);

        // 记录流程启动历史
        ProcessHistory startHistory = ProcessHistory.builder()
                .processInstanceId(instance.getId())
                .activityId("startEvent")
                .activityName("提交申请")
                .activityType("startEvent")
                .operationType("SUBMIT")
                .operatorId(userId)
                .operatorName(userId)
                .comment("发起流程")
                .build();
        processHistoryRepository.save(startHistory);

        return ProcessInstanceInfo.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionName(instance.getProcessDefinitionName())
                .businessKey(instance.getBusinessKey())
                .startTime(instance.getStartTime())
                .status(instance.getStatus())
                .startUserId(instance.getStartUserId())
                .startUserName(instance.getStartUserName())
                .currentNode(instance.getCurrentNode())
                .currentAssignee(instance.getCurrentAssignee())
                .build();
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
                
                // 首先尝试从 custom:properties 中解析 assigneeType
                String assigneeType = extractCustomProperty(userTaskElement, "assigneeType");
                String assignee = null;
                String candidateUsers = null;
                
                if (assigneeType != null) {
                    // 根据 assigneeType 解析处理人
                    log.info("Found assigneeType: {} for task: {}", assigneeType, name);
                    
                    switch (assigneeType) {
                        case "initiator":
                            assignee = initiatorId;
                            break;
                        case "manager":
                            assignee = getInitiatorManager(initiatorId);
                            break;
                        case "entityManager":
                            assignee = getEntityManager(initiatorId);
                            break;
                        case "functionManager":
                            assignee = getFunctionManager(initiatorId);
                            break;
                        case "departmentManager":
                            assignee = getDepartmentManager(initiatorId);
                            break;
                        case "departmentSecondaryManager":
                            assignee = getDepartmentSecondaryManager(initiatorId);
                            break;
                        case "eitherManager":
                            // 实体或职能管理者（或签）
                            String entityMgr = getEntityManager(initiatorId);
                            String funcMgr = getFunctionManager(initiatorId);
                            List<String> managers = new ArrayList<>();
                            if (entityMgr != null) managers.add(entityMgr);
                            if (funcMgr != null && !funcMgr.equals(entityMgr)) managers.add(funcMgr);
                            if (!managers.isEmpty()) {
                                candidateUsers = String.join(",", managers);
                                assignee = managers.get(0);
                            }
                            break;
                        case "bothManagers":
                            // 实体+职能管理者（会签）
                            String entityMgr2 = getEntityManager(initiatorId);
                            String funcMgr2 = getFunctionManager(initiatorId);
                            List<String> bothManagers = new ArrayList<>();
                            if (entityMgr2 != null) bothManagers.add(entityMgr2);
                            if (funcMgr2 != null && !funcMgr2.equals(entityMgr2)) bothManagers.add(funcMgr2);
                            if (!bothManagers.isEmpty()) {
                                candidateUsers = String.join(",", bothManagers);
                            }
                            break;
                        case "user":
                            // 指定用户
                            assignee = extractCustomProperty(userTaskElement, "assigneeValue");
                            break;
                        case "group":
                            // 指定部门/组
                            String groupValue = extractCustomProperty(userTaskElement, "assigneeValue");
                            if (groupValue != null) {
                                result.put("candidateGroups", groupValue);
                            }
                            break;
                        case "expression":
                            // 表达式
                            String expr = extractCustomProperty(userTaskElement, "assigneeValue");
                            if (expr != null && expr.startsWith("${") && expr.endsWith("}")) {
                                String varName = expr.substring(2, expr.length() - 1);
                                assignee = resolveProcessVariable(varName, formData, initiatorId);
                            }
                            break;
                        default:
                            log.warn("Unknown assigneeType: {}", assigneeType);
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
                boolean isInitiatorTask = "initiator".equals(assigneeType) || 
                    (assignee != null && (assignee.equals("${initiator}") || assignee.equals(initiatorId)));
                
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
    
    /**
     * 解析流程变量
     */
    private String resolveProcessVariable(String varName, Map<String, Object> formData, String initiatorId) {
        // 首先检查表单数据
        if (formData != null && formData.containsKey(varName)) {
            return String.valueOf(formData.get(varName));
        }
        
        // 处理特殊变量
        if ("initiator".equals(varName)) {
            return initiatorId;
        }
        
        if ("initiatorManager".equals(varName) || "manager".equals(varName)) {
            // 获取发起人的直属上级（部门经理）
            return getInitiatorManager(initiatorId);
        }
        
        if ("entityManager".equals(varName)) {
            // 获取发起人的实体管理者
            return getEntityManager(initiatorId);
        }
        
        if ("functionManager".equals(varName)) {
            // 获取发起人的职能管理者
            return getFunctionManager(initiatorId);
        }
        
        if ("departmentManager".equals(varName)) {
            // 获取发起人的部门主经理
            return getDepartmentManager(initiatorId);
        }
        
        if ("departmentSecondaryManager".equals(varName)) {
            // 获取发起人的部门副经理
            return getDepartmentSecondaryManager(initiatorId);
        }
        
        return null;
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
            RestTemplate restTemplate = new RestTemplate();
            
            // 首先尝试通过用户ID查询
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + initiatorId;
            log.info("Fetching user info for entity manager from: {}", userUrl);
            
            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = response;
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
                    if (searchResponse != null && searchResponse.get("content") != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> users = (List<Map<String, Object>>) searchResponse.get("content");
                        if (!users.isEmpty()) {
                            // 找到用户后，获取详细信息
                            String foundUserId = (String) users.get(0).get("id");
                            String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + foundUserId;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                            userInfo = detailResponse;
                        }
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
            RestTemplate restTemplate = new RestTemplate();
            
            // 首先尝试通过用户ID查询
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + initiatorId;
            log.info("Fetching user info for function manager from: {}", userUrl);
            
            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = response;
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
                    if (searchResponse != null && searchResponse.get("content") != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> users = (List<Map<String, Object>>) searchResponse.get("content");
                        if (!users.isEmpty()) {
                            // 找到用户后，获取详细信息
                            String foundUserId = (String) users.get(0).get("id");
                            String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + foundUserId;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                            userInfo = detailResponse;
                        }
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
     * 获取发起人的部门主经理
     */
    private String getDepartmentManager(String initiatorId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // 1. 获取用户信息，包含部门ID
            // 首先尝试通过用户ID查询
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + initiatorId;
            log.info("Fetching user info for department manager from: {}", userUrl);
            
            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = response;
            } catch (Exception e) {
                log.warn("Failed to get user by ID {}, trying by username: {}", initiatorId, e.getMessage());
            }
            
            // 如果通过ID查询失败，尝试通过用户名查询
            if (userInfo == null || userInfo.get("departmentId") == null) {
                String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + initiatorId + "&size=1";
                log.info("Searching user by username from: {}", searchUrl);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
                    if (searchResponse != null && searchResponse.get("content") != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> users = (List<Map<String, Object>>) searchResponse.get("content");
                        if (!users.isEmpty()) {
                            // 找到用户后，获取详细信息
                            String foundUserId = (String) users.get(0).get("id");
                            String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + foundUserId;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                            userInfo = detailResponse;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to search user by username {}: {}", initiatorId, e.getMessage());
                }
            }
            
            if (userInfo == null || userInfo.get("departmentId") == null) {
                log.warn("User {} has no department", initiatorId);
                return null;
            }
            
            String departmentId = (String) userInfo.get("departmentId");
            
            // 2. 获取部门信息，包含主经理ID
            String deptUrl = adminCenterUrl + "/api/v1/admin/departments/" + departmentId;
            log.info("Fetching department info from: {}", deptUrl);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> deptInfo = restTemplate.getForObject(deptUrl, Map.class);
            
            if (deptInfo == null || deptInfo.get("managerId") == null) {
                log.warn("Department {} has no manager", departmentId);
                return null;
            }
            
            String managerId = (String) deptInfo.get("managerId");
            log.info("Found department manager {} for user {}", managerId, initiatorId);
            return managerId;
            
        } catch (Exception e) {
            log.error("Failed to get department manager for {}: {}", initiatorId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取发起人的部门副经理
     */
    private String getDepartmentSecondaryManager(String initiatorId) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            // 1. 获取用户信息，包含部门ID
            // 首先尝试通过用户ID查询
            String userUrl = adminCenterUrl + "/api/v1/admin/users/" + initiatorId;
            log.info("Fetching user info for department secondary manager from: {}", userUrl);
            
            Map<String, Object> userInfo = null;
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restTemplate.getForObject(userUrl, Map.class);
                userInfo = response;
            } catch (Exception e) {
                log.warn("Failed to get user by ID {}, trying by username: {}", initiatorId, e.getMessage());
            }
            
            // 如果通过ID查询失败，尝试通过用户名查询
            if (userInfo == null || userInfo.get("departmentId") == null) {
                String searchUrl = adminCenterUrl + "/api/v1/admin/users?keyword=" + initiatorId + "&size=1";
                log.info("Searching user by username from: {}", searchUrl);
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> searchResponse = restTemplate.getForObject(searchUrl, Map.class);
                    if (searchResponse != null && searchResponse.get("content") != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> users = (List<Map<String, Object>>) searchResponse.get("content");
                        if (!users.isEmpty()) {
                            // 找到用户后，获取详细信息
                            String foundUserId = (String) users.get(0).get("id");
                            String detailUrl = adminCenterUrl + "/api/v1/admin/users/" + foundUserId;
                            @SuppressWarnings("unchecked")
                            Map<String, Object> detailResponse = restTemplate.getForObject(detailUrl, Map.class);
                            userInfo = detailResponse;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to search user by username {}: {}", initiatorId, e.getMessage());
                }
            }
            
            if (userInfo == null || userInfo.get("departmentId") == null) {
                log.warn("User {} has no department", initiatorId);
                return null;
            }
            
            String departmentId = (String) userInfo.get("departmentId");
            
            // 2. 获取部门信息，包含副经理ID
            String deptUrl = adminCenterUrl + "/api/v1/admin/departments/" + departmentId;
            log.info("Fetching department info from: {}", deptUrl);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> deptInfo = restTemplate.getForObject(deptUrl, Map.class);
            
            if (deptInfo == null || deptInfo.get("secondaryManagerId") == null) {
                log.warn("Department {} has no secondary manager", departmentId);
                return null;
            }
            
            String secondaryManagerId = (String) deptInfo.get("secondaryManagerId");
            log.info("Found department secondary manager {} for user {}", secondaryManagerId, initiatorId);
            return secondaryManagerId;
            
        } catch (Exception e) {
            log.error("Failed to get department secondary manager for {}: {}", initiatorId, e.getMessage());
            return null;
        }
    }
    
    /**
     * 获取发起人的直属上级（实体管理者）
     * 注意：直属上级是 entityManagerId，不是部门经理
     */
    private String getInitiatorManager(String initiatorId) {
        return getEntityManager(initiatorId);
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

        List<ProcessInstanceInfo> instances = instancePage.getContent().stream()
                .map(this::toProcessInstanceInfo)
                .toList();

        return new PageImpl<>(instances, pageable, instancePage.getTotalElements());
    }
    
    /**
     * 转换实体到DTO
     */
    private ProcessInstanceInfo toProcessInstanceInfo(ProcessInstance instance) {
        return ProcessInstanceInfo.builder()
                .id(instance.getId())
                .processDefinitionId(instance.getProcessDefinitionId())
                .processDefinitionKey(instance.getProcessDefinitionKey())
                .processDefinitionName(instance.getProcessDefinitionName())
                .businessKey(instance.getBusinessKey())
                .startTime(instance.getStartTime())
                .endTime(instance.getEndTime())
                .status(instance.getStatus())
                .startUserId(instance.getStartUserId())
                .startUserName(instance.getStartUserName())
                .currentNode(instance.getCurrentNode())
                .currentAssignee(instance.getCurrentAssignee())
                .candidateUsers(instance.getCandidateUsers())
                .variables(instance.getVariables())
                .build();
    }

    /**
     * 获取流程详情
     */
    public ProcessInstanceInfo getProcessDetail(String processId) {
        return processInstanceRepository.findById(processId)
                .map(this::toProcessInstanceInfo)
                .orElse(null);
    }

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
        // 发送催办通知（TODO: 实现通知逻辑）
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

    /**
     * 保存草稿
     */
    public ProcessDraft saveDraft(String userId, String processKey, Map<String, Object> formData) {
        Optional<ProcessDraft> existing = processDraftRepository.findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey);
        ProcessDraft draft;
        if (existing.isPresent()) {
            draft = existing.get();
            draft.setFormData(formData);
            draft.setUpdatedAt(LocalDateTime.now());
        } else {
            draft = new ProcessDraft();
            draft.setUserId(userId);
            draft.setProcessDefinitionKey(processKey);
            draft.setFormData(formData);
            draft.setCreatedAt(LocalDateTime.now());
            draft.setUpdatedAt(LocalDateTime.now());
        }
        return processDraftRepository.save(draft);
    }

    /**
     * 获取草稿
     */
    public Optional<ProcessDraft> getDraft(String userId, String processKey) {
        return processDraftRepository.findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey);
    }

    /**
     * 删除草稿
     */
    public void deleteDraft(String userId, String processKey) {
        processDraftRepository.findFirstByUserIdAndProcessDefinitionKeyOrderByUpdatedAtDesc(userId, processKey)
                .ifPresent(processDraftRepository::delete);
    }
    
    /**
     * 获取用户的草稿列表
     */
    public List<Map<String, Object>> getDraftList(String userId) {
        log.info("Getting draft list for user: {}", userId);
        List<ProcessDraft> drafts = processDraftRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (ProcessDraft draft : drafts) {
            Map<String, Object> draftInfo = new HashMap<>();
            draftInfo.put("id", draft.getId());
            draftInfo.put("processDefinitionKey", draft.getProcessDefinitionKey());
            draftInfo.put("formData", draft.getFormData());
            draftInfo.put("createdAt", draft.getCreatedAt());
            draftInfo.put("updatedAt", draft.getUpdatedAt());
            
            // 尝试获取功能单元名称
            try {
                Map<String, Object> content = getFunctionUnitContent(draft.getProcessDefinitionKey());
                if (content != null && content.get("name") != null) {
                    draftInfo.put("processDefinitionName", content.get("name"));
                } else {
                    draftInfo.put("processDefinitionName", draft.getProcessDefinitionKey());
                }
            } catch (Exception e) {
                log.warn("Failed to get function unit name for {}: {}", draft.getProcessDefinitionKey(), e.getMessage());
                draftInfo.put("processDefinitionName", draft.getProcessDefinitionKey());
            }
            
            result.add(draftInfo);
        }
        
        return result;
    }
    
    /**
     * 根据ID删除草稿
     */
    public void deleteDraftById(String userId, Long draftId) {
        processDraftRepository.findById(draftId).ifPresent(draft -> {
            if (draft.getUserId().equals(userId)) {
                processDraftRepository.delete(draft);
            }
        });
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
            RestTemplate restTemplate = new RestTemplate();
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                log.info("Got function unit content: name={}", response.get("name"));
                return response;
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
            
            RestTemplate restTemplate = new RestTemplate();
            String url = adminCenterUrl + "/api/v1/admin/function-units/" + functionUnitId + "/content";
            log.info("Fetching function unit content from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response != null) {
                log.info("Got function unit content: name={}", response.get("name"));
                return response;
            }
            
            return Collections.emptyMap();
            
        } catch (Exception e) {
            log.error("Failed to get function unit content for {}: {}", functionUnitIdOrCode, e.getMessage(), e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", e.getMessage());
            return errorResult;
        }
    }
}
