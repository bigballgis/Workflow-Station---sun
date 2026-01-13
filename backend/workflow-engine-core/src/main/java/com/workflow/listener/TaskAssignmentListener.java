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
 * 支持7种标准分配类型：
 * 1. FUNCTION_MANAGER - 职能经理（直接分配）
 * 2. ENTITY_MANAGER - 实体经理（直接分配）
 * 3. INITIATOR - 流程发起人（直接分配）
 * 4. DEPT_OTHERS - 本部门其他人（需要认领）
 * 5. PARENT_DEPT - 上级部门（需要认领）
 * 6. FIXED_DEPT - 指定部门（需要认领）
 * 7. VIRTUAL_GROUP - 虚拟组（需要认领）
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
            String assigneeValue = null;
            
            if (processDefinitionId != null && taskDefinitionKey != null) {
                BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
                if (bpmnModel != null) {
                    FlowElement flowElement = bpmnModel.getFlowElement(taskDefinitionKey);
                    if (flowElement instanceof UserTask) {
                        UserTask userTask = (UserTask) flowElement;
                        assigneeType = getExtensionProperty(userTask, "assigneeType");
                        assigneeValue = getExtensionProperty(userTask, "assigneeValue");
                        log.info("Found BPMN extension properties: assigneeType={}, assigneeValue={}", 
                                assigneeType, assigneeValue);
                    }
                }
            }

            // 如果 BPMN 中没有定义，尝试从流程变量中获取
            if (assigneeType == null || assigneeType.isEmpty()) {
                Map<String, Object> variables = runtimeService.getVariables(processInstanceId);
                assigneeType = getStringVariable(variables, "assigneeType");
                assigneeValue = getStringVariable(variables, "assigneeValue");
            }

            if (assigneeType == null || assigneeType.isEmpty()) {
                log.debug("No assigneeType defined for task {}", taskId);
                return;
            }

            // 获取流程发起人
            Map<String, Object> processVariables = runtimeService.getVariables(processInstanceId);
            String initiatorId = getStringVariable(processVariables, "initiator");
            
            if (initiatorId == null || initiatorId.isEmpty()) {
                log.warn("No initiator found for process instance {}", processInstanceId);
                return;
            }

            log.info("Resolving assignee for task {}: type={}, value={}, initiator={}", 
                    taskId, assigneeType, assigneeValue, initiatorId);

            // 使用 TaskAssigneeResolver 解析处理人
            TaskAssigneeResolver.ResolveResult result = taskAssigneeResolver.resolve(
                    assigneeType, assigneeValue, initiatorId);

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
                // 认领类型：设置候选人或候选组
                if (result.getCandidateUsers() != null && !result.getCandidateUsers().isEmpty()) {
                    for (String candidateUser : result.getCandidateUsers()) {
                        taskService.addCandidateUser(taskId, candidateUser);
                    }
                    log.info("Task {} set candidate users: {}", taskId, result.getCandidateUsers());
                }
                if (result.getCandidateGroup() != null) {
                    taskService.addCandidateGroup(taskId, result.getCandidateGroup());
                    log.info("Task {} set candidate group: {}", taskId, result.getCandidateGroup());
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
