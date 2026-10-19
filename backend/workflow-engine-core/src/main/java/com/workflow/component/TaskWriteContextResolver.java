package com.workflow.component;

import com.workflow.dto.response.TaskWriteContext;
import com.workflow.exception.WorkflowBusinessException;
import com.workflow.util.MultiInstanceTaskItem;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** Strict opt-in write authority; legacy task detail assembly keeps its existing read semantics. */
@Component
public class TaskWriteContextResolver {
    private final TaskService taskService;
    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;

    public TaskWriteContextResolver(TaskService taskService, RepositoryService repositoryService, RuntimeService runtimeService) {
        this.taskService = taskService;
        this.repositoryService = repositoryService;
        this.runtimeService = runtimeService;
    }

    public TaskWriteContext resolve(String taskId) {
        var task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || blank(task.getProcessDefinitionId()) || blank(task.getExecutionId())
                || blank(task.getProcessInstanceId()) || blank(task.getTaskDefinitionKey())) throw invalid();
        var model = repositoryService.getBpmnModel(task.getProcessDefinitionId());
        if (model == null) throw invalid();
        FlowElement element = model.getFlowElement(task.getTaskDefinitionKey());
        if (!(element instanceof org.flowable.bpmn.model.UserTask)) throw invalid();
        MultiInstanceLoopCharacteristics loop = MultiInstanceTaskItem.loopFor(element);
        Map<String, Object> item = Map.of();
        if (loop != null) {
            String variable = loop.getElementVariable();
            if (blank(variable)) throw invalid();
            var instance = runtimeService.getVariableInstance(task.getExecutionId(), variable);
            if (instance == null || blank(instance.getExecutionId())
                    || task.getProcessInstanceId().equals(instance.getExecutionId())
                    || !task.getProcessInstanceId().equals(instance.getProcessInstanceId())) throw invalid();
            Object value = instance.getValue();
            if (!(value instanceof Map<?, ?> row) || !(row.get("rowKey") instanceof Map<?, ?> key) || key.isEmpty()) throw invalid();
            Map<String, Object> rowKey = new LinkedHashMap<>();
            key.forEach((name, identity) -> {
                if (!(name instanceof String field) || field.isBlank()
                        || !(identity instanceof String || identity instanceof Number) || String.valueOf(identity).isBlank()) throw invalid();
                rowKey.put(field, identity);
            });
            item = Map.of("rowKey", rowKey);
        }
        return new TaskWriteContext(task.getId(), task.getProcessInstanceId(), task.getProcessDefinitionId(),
                task.getTaskDefinitionKey(), loop != null, item);
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static WorkflowBusinessException invalid() {
        return new WorkflowBusinessException("TASK_WRITE_CONTEXT_UNAVAILABLE", "Active task write scope cannot be verified");
    }
}
