package com.workflow.listener;

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
     * 从 UserTask 的扩展元素中获取 custom:property 的值
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
            // 查找 custom:property 子元素
            List<ExtensionElement> propertyElements = propertiesElement.getChildElements().get("property");
            if (propertyElements == null) {
                continue;
            }

            for (ExtensionElement propertyElement : propertyElements) {
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
