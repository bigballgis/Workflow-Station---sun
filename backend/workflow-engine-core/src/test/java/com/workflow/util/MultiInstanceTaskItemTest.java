package com.workflow.util;

import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.flowable.bpmn.model.SubProcess;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.flowable.variable.api.persistence.entity.VariableInstance;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MultiInstanceTaskItemTest {
    private final RepositoryService definitions = mock(RepositoryService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final Task task = mock(Task.class);
    private final UserTask node = new UserTask();

    private void model(boolean nested) {
        when(task.getProcessDefinitionId()).thenReturn("deployed-definition");
        when(task.getTaskDefinitionKey()).thenReturn("work");
        lenient().when(task.getExecutionId()).thenReturn("child-execution");
        lenient().when(task.getProcessInstanceId()).thenReturn("instance");
        var model = new BpmnModel();
        var process = new org.flowable.bpmn.model.Process();
        process.setId("process");
        node.setId("work");
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("renamedParticipant");
        if (nested) {
            var parent = new SubProcess();
            parent.setId("parallel-body");
            parent.setLoopCharacteristics(loop);
            parent.addFlowElement(node);
            process.addFlowElement(parent);
        } else {
            node.setLoopCharacteristics(loop);
            process.addFlowElement(node);
        }
        model.addProcess(process);
        when(definitions.getBpmnModel("deployed-definition")).thenReturn(model);
    }

    private void item(String ownerExecution, String processId, Object value) {
        var variable = mock(VariableInstance.class);
        when(variable.getExecutionId()).thenReturn(ownerExecution);
        lenient().when(variable.getProcessInstanceId()).thenReturn(processId);
        lenient().when(variable.getValue()).thenReturn(value);
        when(runtime.getVariableInstance("child-execution", "renamedParticipant")).thenReturn(variable);
    }

    @Test
    void directAndNestedLoopsUseConfiguredExecutionScopedVariable() {
        for (boolean nested : new boolean[] { false, true }) {
            model(nested);
            var row = Map.of("rowKey", Map.of("business_key", "row-one"));
            item(nested ? "mi-body-execution" : "child-execution", "instance", row);
            assertThat(MultiInstanceTaskItem.resolve(definitions, runtime, task)).isSameAs(row);
        }
        verify(runtime, never()).getVariable(anyString(), anyString());
    }

    @Test
    void processWideOrForeignInstanceVariableCannotSupplyParticipant() {
        model(true);
        item("instance", "instance", Map.of("rowKey", Map.of("key", "wrong")));
        assertThatThrownBy(() -> MultiInstanceTaskItem.resolve(definitions, runtime, task))
                .hasMessageContaining("unavailable on the task execution");
        item("other-execution", "other-instance", Map.of("rowKey", Map.of("key", "wrong")));
        assertThatThrownBy(() -> MultiInstanceTaskItem.resolve(definitions, runtime, task))
                .hasMessageContaining("unavailable on the task execution");
        verify(runtime, never()).getVariable(anyString(), anyString());
    }

    @Test
    void missingOrMalformedVariableDoesNotFallBackToLegacyNames() {
        model(false);
        assertThatThrownBy(() -> MultiInstanceTaskItem.resolve(definitions, runtime, task)).isInstanceOf(IllegalStateException.class);
        item("child-execution", "instance", "not-a-row");
        assertThatThrownBy(() -> MultiInstanceTaskItem.resolve(definitions, runtime, task)).isInstanceOf(IllegalStateException.class);
        node.getLoopCharacteristics().setElementVariable(" ");
        assertThatThrownBy(() -> MultiInstanceTaskItem.resolve(definitions, runtime, task)).isInstanceOf(IllegalStateException.class);
        verify(runtime, never()).getVariable(anyString(), anyString());
    }

    @Test
    void missingDeployedModelDoesNotPermitLegacyLookup() {
        when(task.getProcessDefinitionId()).thenReturn("missing");
        when(task.getTaskDefinitionKey()).thenReturn("work");
        assertThatThrownBy(() -> MultiInstanceTaskItem.resolve(definitions, runtime, task)).hasMessageContaining("definition is unavailable");
        verifyNoInteractions(runtime);
    }
}
