package com.workflow.util;

import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;

import java.util.Map;

/** Resolves participant data from the actual deployed loop, never a differently named row variable. */
public final class MultiInstanceTaskItem {
    private MultiInstanceTaskItem() { }

    public static MultiInstanceLoopCharacteristics loopFor(FlowElement element) {
        FlowElement current = element;
        while (current != null) {
            if (current instanceof Activity activity && activity.getLoopCharacteristics() != null) {
                return activity.getLoopCharacteristics();
            }
            current = current.getSubProcess();
        }
        return null; // The deployed model positively establishes that this task is not MI.
    }

    public static Object resolve(RepositoryService definitions, RuntimeService runtime, Task task) {
        var model = definitions.getBpmnModel(task.getProcessDefinitionId());
        var element = model == null ? null : model.getFlowElement(task.getTaskDefinitionKey());
        if (element == null) throw new IllegalStateException("Deployed assignment task definition is unavailable");
        var loop = loopFor(element);
        if (loop == null) {
            // FALLBACK(migration): D6 preserves legacy ELEMENT_VARIABLE tasks without an MI loop.
            // Remove when those deployed legacy definitions are no longer supported; explicit loops never use it.
            Object legacy = runtime.getVariable(task.getExecutionId(), "currentItem");
            return legacy != null ? legacy : runtime.getVariable(task.getExecutionId(), "_currentItem");
        }
        String variable = loop.getElementVariable();
        if (variable == null || variable.isBlank()) throw unavailable();
        var instance = runtime.getVariableInstance(task.getExecutionId(), variable);
        if (instance == null || instance.getExecutionId() == null || instance.getExecutionId().isBlank()
                || task.getProcessInstanceId().equals(instance.getExecutionId())
                || !task.getProcessInstanceId().equals(instance.getProcessInstanceId())
                || !(instance.getValue() instanceof Map<?, ?>)) throw unavailable();
        return instance.getValue();
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Configured MI participant variable is unavailable on the task execution");
    }
}
