package com.workflow.listener;

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
import org.flowable.common.engine.impl.event.FlowableEntityEventImpl;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.service.impl.persistence.entity.TaskEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * BPMN 扩展属性：
 * - assigneeType: 分配类型代码
 * - roleId: 角色ID（6种角色类型需要）
 * - businessUnitId: 业务单元ID（FIXED_BU_ROLE需要）
 * - assigneeLabel: 显示标签
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

    @Override
    public void onEvent(FlowableEvent event) {
        log.info("=== TaskAssignmentListener.onEvent called === Event type: {}", event.getType());
        if (event.getType() == FlowableEngineEventType.TASK_CREATED) {
            log.info("=== TASK_CREATED event detected, calling handleTaskCreated ===");
            handleTaskCreated(event);
        }
    }

    private void handleTaskCreated(FlowableEvent event) {
        log.info("=== handleTaskCreated called ===");
        
        if (!(event instanceof FlowableEntityEventImpl)) {
            log.warn("Event is not FlowableEntityEventImpl, skipping. Event class: {}", event.getClass().getName());
            return;
        }

        FlowableEntityEventImpl entityEvent = (FlowableEntityEventImpl) event;
        Object entity = entityEvent.getEntity();
        
        if (!(entity instanceof TaskEntity)) {
            log.warn("Entity is not TaskEntity, skipping. Entity class: {}", entity != null ? entity.getClass().getName() : "null");
            return;
        }

        TaskEntity task = (TaskEntity) entity;
        String taskId = task.getId();
        String processInstanceId = task.getProcessInstanceId();
        String taskDefinitionKey = task.getTaskDefinitionKey();
        String processDefinitionId = task.getProcessDefinitionId();

        log.info("=== Task created: taskId={}, taskName={}, taskDefKey={}, processInstanceId={} ===", 
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

            if (assigneeType == null || assigneeType.isEmpty()) {
                log.debug("No assigneeType defined for task {}", taskId);
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
                // 认领类型：根据分配类型决定使用 candidateGroup 还是 candidateUsers
                // 对于 Fixed BU Role 和基于业务单元的角色分配，必须使用 candidateUsers
                // 因为 Flowable 不知道业务单元的角色绑定关系
                if (result.getCandidateUsers() != null && !result.getCandidateUsers().isEmpty()) {
                    // 优先使用候选用户列表（适用于 Fixed BU Role 等需要具体用户列表的情况）
                    for (String candidateUser : result.getCandidateUsers()) {
                        taskService.addCandidateUser(taskId, candidateUser);
                    }
                    log.info("Task {} set candidate users ({}): {}", taskId, result.getCandidateUsers().size(), result.getCandidateUsers());
                } else if (roleId != null && !roleId.isEmpty() && 
                          (assigneeType == null || 
                           !assigneeType.equals("FIXED_BU_ROLE") && 
                           !assigneeType.equals("INITIATOR_BU_ROLE") && 
                           !assigneeType.equals("INITIATOR_PARENT_BU_ROLE") && 
                           !assigneeType.equals("CURRENT_BU_ROLE") && 
                           !assigneeType.equals("CURRENT_PARENT_BU_ROLE"))) {
                    // 对于 BU_UNBOUNDED_ROLE 等不依赖业务单元的角色，可以使用 candidateGroup
                    // 使用角色作为候选组，所有拥有该角色的用户都能看到任务
                    taskService.addCandidateGroup(taskId, roleId);
                    log.info("Task {} set candidate group (role): {}", taskId, roleId);
                } else {
                    log.warn("Task {} requires claim but no candidate users or groups set. assigneeType={}, roleId={}, businessUnitId={}", 
                            taskId, assigneeType, roleId, businessUnitId);
                }
            }
            
            // 创建扩展任务信息记录
            createExtendedTaskInfo(task, assigneeType, result, roleId, businessUnitId);

        } catch (Exception e) {
            log.error("Error handling task assignment for task {}: {}", taskId, e.getMessage(), e);
        }
    }
    
    /**
     * 创建扩展任务信息记录
     */
    private void createExtendedTaskInfo(TaskEntity task, String assigneeType, 
                                       TaskAssigneeResolver.ResolveResult result,
                                       String roleId, String businessUnitId) {
        try {
            String taskId = task.getId();
            
            // 确定分配目标
            String assignmentTarget = null;
            if (!result.isRequiresClaim() && result.getAssignee() != null) {
                // 直接分配：使用 assignee
                assignmentTarget = result.getAssignee();
            } else if (result.isRequiresClaim()) {
                // 认领类型：使用 roleId 或第一个候选用户
                if (roleId != null && !roleId.isEmpty()) {
                    assignmentTarget = roleId;
                } else if (result.getCandidateUsers() != null && !result.getCandidateUsers().isEmpty()) {
                    assignmentTarget = String.join(",", result.getCandidateUsers());
                }
            }
            
            // 转换 assigneeType 为 AssignmentType 枚举
            AssignmentType assignmentTypeEnum = convertToAssignmentType(assigneeType);
            
            // 创建扩展任务信息
            ExtendedTaskInfo extendedTaskInfo = ExtendedTaskInfo.builder()
                .taskId(taskId)
                .taskName(task.getName())
                .taskDescription(task.getDescription())
                .processInstanceId(task.getProcessInstanceId())
                .processDefinitionId(task.getProcessDefinitionId())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .assignmentType(assignmentTypeEnum)
                .assignmentTarget(assignmentTarget)
                .priority(task.getPriority())
                .dueDate(task.getDueDate() != null ? 
                    LocalDateTime.ofInstant(task.getDueDate().toInstant(), ZoneId.systemDefault()) : null)
                .status("ACTIVE")
                .createdTime(LocalDateTime.now())
                .build();
            
            extendedTaskInfoRepository.save(extendedTaskInfo);
            log.info("Created extended task info for task {}: assignmentType={}, assignmentTarget={}", 
                    taskId, assignmentTypeEnum, assignmentTarget);
                    
        } catch (Exception e) {
            log.error("Failed to create extended task info for task {}: {}", task.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * 转换字符串类型为 AssignmentType 枚举
     */
    private AssignmentType convertToAssignmentType(String assigneeType) {
        if (assigneeType == null || assigneeType.isEmpty()) {
            return AssignmentType.USER;
        }
        
        try {
            return AssignmentType.valueOf(assigneeType);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown assigneeType: {}, defaulting to USER", assigneeType);
            return AssignmentType.USER;
        }
    }

    /**
     * 从 UserTask 的扩展元素中获取 custom:property 或 custom:values 的值
     * 支持两种格式：
     * 1. <custom:property name="xxx" value="yyy"/>
     * 2. <custom:values name="xxx" value="yyy"/>
     */
    private String getExtensionProperty(UserTask userTask, String propertyName) {
        if (userTask.getExtensionElements() == null) {
            return null;
        }

        // 查找 custom:properties 元素
        List<ExtensionElement> propertiesElements = userTask.getExtensionElements().get("properties");
        if (propertiesElements == null || propertiesElements.isEmpty()) {
            return null;
        }

        for (ExtensionElement propertiesElement : propertiesElements) {
            // 尝试查找 custom:property 子元素
            List<ExtensionElement> propertyElements = propertiesElement.getChildElements().get("property");
            if (propertyElements != null) {
                for (ExtensionElement propertyElement : propertyElements) {
                    String name = propertyElement.getAttributeValue(null, "name");
                    if (propertyName.equals(name)) {
                        return propertyElement.getAttributeValue(null, "value");
                    }
                }
            }
            
            // 尝试查找 custom:values 子元素（兼容旧格式）
            List<ExtensionElement> valuesElements = propertiesElement.getChildElements().get("values");
            if (valuesElements != null) {
                for (ExtensionElement valuesElement : valuesElements) {
                    String name = valuesElement.getAttributeValue(null, "name");
                    if (propertyName.equals(name)) {
                        return valuesElement.getAttributeValue(null, "value");
                    }
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
