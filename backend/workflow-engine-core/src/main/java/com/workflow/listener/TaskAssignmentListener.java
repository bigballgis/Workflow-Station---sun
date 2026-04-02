package com.workflow.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import com.workflow.service.TaskAssigneeResolver;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务分配监听器
 * 在任务创建时根据 BPMN 中定义的 assigneeType 自动分配处理人
 * 
 * 支持9种标准分配类型：
 * 
 * 直接分配类型（3种）：
 * 1. FUNCTION_MANAGER - 职能经理
 * 2. ENTITY_MANAGER - 实体经理
 * 3. INITIATOR - 流程发起人
 * 
 * 认领类型（6种）：
 * 4. CURRENT_BU_ROLE - 当前人业务单元角色
 * 5. CURRENT_PARENT_BU_ROLE - 当前人上级业务单元角色
 * 6. INITIATOR_BU_ROLE - 发起人业务单元角色
 * 7. INITIATOR_PARENT_BU_ROLE - 发起人上级业务单元角色
 * 8. FIXED_BU_ROLE - 指定业务单元角色
 * 9. BU_UNBOUNDED_ROLE - BU无关型角色
 * 
 * 多实例分配类型（1种）：
 * 10. ELEMENT_VARIABLE - 从多实例元素变量中读取处理人
 * 
 * BPMN 扩展属性：
 * - assigneeType: 分配类型代码
 * - roleId: 角色ID（6种角色类型需要）
 * - businessUnitId: 业务单元ID（FIXED_BU_ROLE需要）
 * - assigneeLabel: 显示标签
 * - subTableId: 子表ID（ELEMENT_VARIABLE需要）
 * - subTableName: 子表名称（ELEMENT_VARIABLE需要）
 * - assigneeField: 处理人字段名（ELEMENT_VARIABLE需要）
 * - rowIdVariable: 行ID变量名（ELEMENT_VARIABLE需要）
 */
@Slf4j
@Component
public class TaskAssignmentListener implements FlowableEventListener {

    @Autowired
    @Lazy
    private TaskAssigneeResolver taskAssigneeResolver;

    @Autowired
    @Lazy
    private TaskService taskService;

    @Autowired
    @Lazy
    private RuntimeService runtimeService;

    @Autowired
    @Lazy
    private RepositoryService repositoryService;

    @Autowired
    @Lazy
    private ExtendedTaskInfoRepository extendedTaskInfoRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.TASK_CREATED) {
            handleTaskCreated(event);
        }
    }

    private void handleTaskCreated(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEventImpl)) {
            return;
        }

        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        Object entity = entityEvent.getEntity();
        
        if (!(entity instanceof TaskEntity)) {
            return;
        }

        TaskEntity task = (TaskEntity) entity;
        String taskId = task.getId();
        String processInstanceId = task.getProcessInstanceId();
        String taskDefinitionKey = task.getTaskDefinitionKey();
        String processDefinitionId = task.getProcessDefinitionId();

        log.info("Task created: taskId={}, taskName={}, taskDefKey={}, processInstanceId={}", 
                taskId, task.getName(), taskDefinitionKey, processInstanceId);

        // 如果任务已经有 assignee，不需要再分配
        if (task.getAssignee() != null && !task.getAssignee().isEmpty()) {
            log.info("Task {} already has assignee: {}", taskId, task.getAssignee());
            return;
        }

        try {
            // 从 BPMN 模型中获取任务的扩展属性
            String assigneeType = null;
            String roleId = null;
            String businessUnitId = null;
            String assigneeValue = null; // 兼容旧版本
            
            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask) {
                        UserTask userTask = (UserTask) flowElement;
                        assigneeType = getExtensionProperty(userTask, "assigneeType");
                        roleId = getExtensionProperty(userTask, "roleId");
                        businessUnitId = getExtensionProperty(userTask, "businessUnitId");
                        assigneeValue = getExtensionProperty(userTask, "assigneeValue"); // 兼容旧版本

                        // 标准 BPMN/Flowable 上配置了 assignee 表达式，但扩展里未写 assigneeType（常见于旧导出）
                        if ((assigneeType == null || assigneeType.isEmpty()) && userTask.getAssignee() != null
                                && !userTask.getAssignee().isBlank()) {
                            String ga = userTask.getAssignee().trim();
                            if (isInitiatorExpression(ga)) {
                                assigneeType = "INITIATOR";
                            }
                        }

                        log.info("Found BPMN extension properties: assigneeType={}, roleId={}, businessUnitId={}",
                                assigneeType, roleId, businessUnitId);
                    }
                }
            }

            // 如果 BPMN 中没有定义，尝试从流程变量中获取
            if (assigneeType == null || assigneeType.isEmpty()) {
                Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
                assigneeType = getStringVariable(variables, "assigneeType");
                roleId = getStringVariable(variables, "roleId");
                businessUnitId = getStringVariable(variables, "businessUnitId");
                assigneeValue = getStringVariable(variables, "assigneeValue");
            }

            // 开发者工作站旧版 TaskProperties：assigneeType=expression + assigneeValue=${initiator} → 无法被 AssigneeType 识别
            assigneeType = normalizeLegacyAssigneeType(assigneeType, assigneeValue);
            if (assigneeType != null && "INITIATOR".equalsIgnoreCase(assigneeType.trim())) {
                // 避免走 resolve(INITIATOR, "${initiator}", …) 导致三参解析失败
                assigneeValue = null;
            }

            if (assigneeType == null || assigneeType.isEmpty()) {
                log.debug("No assigneeType defined for task {}", taskId);
                return;
            }

            // 处理 ELEMENT_VARIABLE 分配类型（多实例子流程）
            if ("ELEMENT_VARIABLE".equals(assigneeType)) {
                handleElementVariableAssignment(task, taskId, processInstanceId, processDefinitionId, taskDefinitionKey);
                return;
            }

            // 获取流程变量
            Map<String, Object> processVariables = runtimeService.getVariables(processInstanceId);
            
            // 获取流程发起人
            String initiatorId = getStringVariable(processVariables, "initiator");
            if (initiatorId == null || initiatorId.isEmpty()) {
                log.warn("No initiator found for process instance {}", processInstanceId);
                return;
            }
            
            // 获取当前处理人（上一个任务的处理人）
            // 对于第一个任务，currentUserId 等于 initiatorId
            String currentUserId = getStringVariable(processVariables, "currentUserId");
            if (currentUserId == null || currentUserId.isEmpty()) {
                currentUserId = initiatorId;
            }

            log.info("Resolving assignee for task {}: type={}, roleId={}, businessUnitId={}, initiator={}, currentUser={}", 
                    taskId, assigneeType, roleId, businessUnitId, initiatorId, currentUserId);

            // 使用 TaskAssigneeResolver 解析处理人
            TaskAssigneeResolver.ResolveResult result;
            
            // 如果有新版本的参数（roleId），使用新版本方法
            if (roleId != null && !roleId.isEmpty()) {
                result = taskAssigneeResolver.resolve(assigneeType, roleId, businessUnitId, initiatorId, currentUserId);
            } else {
                // 兼容旧版本：使用 assigneeValue
                result = taskAssigneeResolver.resolve(assigneeType, assigneeValue, initiatorId);
            }

            if (result.getErrorMessage() != null) {
                log.warn("Failed to resolve assignee for task {}: {}", taskId, result.getErrorMessage());
                return;
            }

            // 根据解析结果设置任务分配
            if (!result.isRequiresClaim() && result.getAssignee() != null) {
                // 直接分配类型：设置 assignee
                taskService.setAssignee(taskId, result.getAssignee());
                log.info("Task {} assigned to user: {}", taskId, result.getAssignee());
            } else if (result.isRequiresClaim()) {
                // 认领类型：设置候选人
                if (result.getCandidateUsers() != null && !result.getCandidateUsers().isEmpty()) {
                    for (String candidateUser : result.getCandidateUsers()) {
                        taskService.addCandidateUser(taskId, candidateUser);
                    }
                    log.info("Task {} set candidate users: {}", taskId, result.getCandidateUsers());
                }
            }

        } catch (Exception e) {
            log.error("Error handling task assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 处理 ELEMENT_VARIABLE 分配类型（多实例子流程）
     * 从 execution 变量中获取 currentItem（Map 类型），读取 assigneeId 并分配任务
     * 创建 ExtendedTaskInfo 记录，包含多实例相关元数据
     * 
     * @param task 任务实体
     * @param taskId 任务ID
     * @param processInstanceId 流程实例ID
     * @param processDefinitionId 流程定义ID
     * @param taskDefinitionKey 任务定义键
     */
    private void handleElementVariableAssignment(TaskEntity task, String taskId, 
                                                  String processInstanceId, 
                                                  String processDefinitionId, 
                                                  String taskDefinitionKey) {
        try {
            log.info("Handling ELEMENT_VARIABLE assignment for task {}", taskId);
            
            // 从 execution 变量中获取 currentItem（Map 类型）
            String executionId = task.getExecutionId();
            Object currentItemObj = runtimeService.getVariable(executionId, "currentItem");
            
            if (currentItemObj == null) {
                log.warn("currentItem variable is null for task {}, task will remain CREATED", taskId);
                return;
            }
            
            if (!(currentItemObj instanceof Map)) {
                log.warn("currentItem variable is not a Map for task {}, task will remain CREATED", taskId);
                return;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> currentItem = (Map<String, Object>) currentItemObj;
            
            // 读取 assigneeId
            Object assigneeIdObj = currentItem.get("assigneeId");
            if (assigneeIdObj == null) {
                log.warn("assigneeId not found in currentItem for task {}, task will remain CREATED", taskId);
                return;
            }
            
            String assigneeId = String.valueOf(assigneeIdObj);
            
            // 读取 rowId 和 rowVersion
            Object rowIdObj = currentItem.get("rowId");
            Object rowVersionObj = currentItem.get("rowVersion");
            
            Long subTableRowId = null;
            Long subTableRowVersion = null;
            
            if (rowIdObj != null) {
                if (rowIdObj instanceof Number) {
                    subTableRowId = ((Number) rowIdObj).longValue();
                } else {
                    try {
                        subTableRowId = Long.parseLong(String.valueOf(rowIdObj));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid rowId format in currentItem for task {}: {}", taskId, rowIdObj);
                    }
                }
            }
            
            if (rowVersionObj != null) {
                if (rowVersionObj instanceof Number) {
                    subTableRowVersion = ((Number) rowVersionObj).longValue();
                } else {
                    try {
                        subTableRowVersion = Long.parseLong(String.valueOf(rowVersionObj));
                    } catch (NumberFormatException e) {
                        log.warn("Invalid rowVersion format in currentItem for task {}: {}", taskId, rowVersionObj);
                    }
                }
            }
            
            // 从 BPMN 扩展属性中获取子表配置
            String subTableId = null;
            String subTableName = null;
            
            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask) {
                        UserTask userTask = (UserTask) flowElement;
                        subTableId = getExtensionProperty(userTask, "subTableId");
                        subTableName = getExtensionProperty(userTask, "subTableName");
                    }
                }
            }
            
            // 设置任务处理人
            try {
                taskService.setAssignee(taskId, assigneeId);
                log.info("Task {} assigned to user {} via ELEMENT_VARIABLE", taskId, assigneeId);
            } catch (Exception e) {
                log.warn("Failed to set assignee {} for task {}: {}, task will remain CREATED", 
                        assigneeId, taskId, e.getMessage());
                // 处理人不存在/已禁用时记录 WARN 日志，任务状态保持 CREATED
                return;
            }
            
            // 构建 extendedProperties JSON
            Map<String, Object> extendedProps = new HashMap<>();
            extendedProps.put("multiInstance", true);
            if (subTableRowId != null) {
                extendedProps.put("subTableRowId", subTableRowId);
            }
            if (subTableRowVersion != null) {
                extendedProps.put("subTableRowVersion", subTableRowVersion);
            }
            if (subTableId != null) {
                extendedProps.put("subTableId", subTableId);
            }
            if (subTableName != null) {
                extendedProps.put("subTableName", subTableName);
            }
            
            String extendedPropertiesJson;
            try {
                extendedPropertiesJson = objectMapper.writeValueAsString(extendedProps);
            } catch (Exception e) {
                log.error("Failed to serialize extendedProperties for task {}: {}", taskId, e.getMessage());
                extendedPropertiesJson = "{}";
            }
            
            // 创建 ExtendedTaskInfo 记录
            try {
                ExtendedTaskInfo extInfo = ExtendedTaskInfo.builder()
                        .taskId(taskId)
                        .processInstanceId(processInstanceId)
                        .processDefinitionId(processDefinitionId)
                        .taskDefinitionKey(taskDefinitionKey)
                        .taskName(task.getName())
                        .assignmentType(AssignmentType.USER)
                        .assignmentTarget(assigneeId)
                        .status("ASSIGNED")
                        .createdTime(LocalDateTime.now())
                        .extendedProperties(extendedPropertiesJson)
                        .build();
                
                extendedTaskInfoRepository.save(extInfo);
                log.info("Created ExtendedTaskInfo for multi-instance task {}: assignee={}, rowId={}", 
                        taskId, assigneeId, subTableRowId);
            } catch (Exception e) {
                // ExtendedTaskInfo 保存失败时记录 ERROR 日志，不影响 Flowable 任务创建
                log.error("Failed to save ExtendedTaskInfo for task {}: {}", taskId, e.getMessage(), e);
            }
            
        } catch (Exception e) {
            log.error("Error handling ELEMENT_VARIABLE assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }

    /**
     * 旧版设计器：expression + ${initiator} / ${initiatorId} → 标准 INITIATOR
     */
    private static String normalizeLegacyAssigneeType(String assigneeType, String assigneeValue) {
        if (assigneeType == null) {
            return null;
        }
        String t = assigneeType.trim();
        if ("initiator".equalsIgnoreCase(t)) {
            return "INITIATOR";
        }
        if ("expression".equalsIgnoreCase(t) && assigneeValue != null) {
            if (isInitiatorExpression(assigneeValue.trim())) {
                return "INITIATOR";
            }
        }
        return assigneeType;
    }

    private static boolean isInitiatorExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        String e = expr.trim();
        if ("${initiator}".equals(e) || "${initiatorId}".equalsIgnoreCase(e)) {
            return true;
        }
        return e.matches("(?i)^\\$\\{\\s*initiator\\s*}$") || e.matches("(?i)^\\$\\{\\s*initiatorId\\s*}$");
    }

    /**
     * 从 UserTask 扩展中读取 custom:property；兼容 Flowable 解析后不同 namespace 下 key 不一致的情况
     */
    private String getExtensionProperty(UserTask userTask, String propertyName) {
        if (userTask.getExtensionElements() == null || userTask.getExtensionElements().isEmpty()) {
            return null;
        }
        for (List<ExtensionElement> group : userTask.getExtensionElements().values()) {
            if (group == null) {
                continue;
            }
            for (ExtensionElement container : group) {
                if (container == null || container.getName() == null) {
                    continue;
                }
                if (!"properties".equalsIgnoreCase(container.getName())) {
                    continue;
                }
                String v = findPropertyInPropertiesContainer(container, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private String findPropertyInPropertiesContainer(ExtensionElement propertiesElement, String propertyName) {
        if (propertiesElement.getChildElements() == null) {
            return null;
        }
        for (List<ExtensionElement> propertyElements : propertiesElement.getChildElements().values()) {
            if (propertyElements == null) {
                continue;
            }
            for (ExtensionElement propertyElement : propertyElements) {
                if (propertyElement.getName() == null || !"property".equalsIgnoreCase(propertyElement.getName())) {
                    continue;
                }
                String name = propertyElement.getAttributeValue(null, "name");
                if (propertyName.equals(name)) {
                    return propertyElement.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }

    private String getStringVariable(Map<String, Object> variables, String key) {
        if (variables == null) return null;
        Object value = variables.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public boolean isFailOnException() {
        // 不因为分配失败而导致流程失败
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
