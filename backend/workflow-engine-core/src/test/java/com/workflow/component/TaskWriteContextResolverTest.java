package com.workflow.component;

import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskWriteContextResolverTest {
    private final TaskService tasks = mock(TaskService.class);
    private final RepositoryService definitions = mock(RepositoryService.class);
    private final RuntimeService runtime = mock(RuntimeService.class);
    private final TaskWriteContextResolver resolver = new TaskWriteContextResolver(tasks, definitions, runtime);
    private final Task task = mock(Task.class);
    private final BpmnModel model = new BpmnModel();
    private final org.flowable.bpmn.model.Process process = new org.flowable.bpmn.model.Process();
    private final UserTask userTask = new UserTask();

    @BeforeEach
    void setUp() {
        TaskQuery query = mock(TaskQuery.class);
        when(tasks.createTaskQuery()).thenReturn(query);
        when(query.taskId("task-one")).thenReturn(query);
        when(query.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task-one");
        when(task.getExecutionId()).thenReturn("execution-one");
        when(task.getProcessInstanceId()).thenReturn("instance-one");
        when(task.getProcessDefinitionId()).thenReturn("deployed:1:old-definition");
        when(task.getTaskDefinitionKey()).thenReturn("review");
        userTask.setId("review");
        process.setId("process");
        model.addProcess(process);
        when(definitions.getBpmnModel("deployed:1:old-definition")).thenReturn(model);
    }

    @Test
    void normalTaskIsNotMiEvenWhenProcessVariablesHaveStaleCurrentItem() {
        process.addFlowElement(userTask);
        var result = resolver.resolve("task-one");
        assertThat(result.multiInstance()).isFalse();
        assertThat(result.currentItem()).isEmpty();
        assertThat(result.processDefinitionId()).isEqualTo("deployed:1:old-definition");
        verifyNoInteractions(runtime);
    }

    @Test
    void nestedTaskUsesConfiguredLoopVariableOnItsOwnExecutionAndOnlyReturnsIdentity() {
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("configuredParticipant");
        var parent = new SubProcess();
        parent.setId("parallel-step");
        parent.setLoopCharacteristics(loop);
        parent.addFlowElement(userTask);
        process.addFlowElement(parent);
        item("configuredParticipant", Map.of("rowKey", Map.of("renamed_business_key", "participant-one"),
                "privateOtherField", "not-returned"), "mi-body-execution");
        var result = resolver.resolve("task-one");
        assertThat(result.multiInstance()).isTrue();
        assertThat(result.currentItem()).isEqualTo(Map.of("rowKey", Map.of("renamed_business_key", "participant-one")));
        verify(runtime).getVariableInstance("execution-one", "configuredParticipant");
        verifyNoMoreInteractions(runtime);
    }

    @Test
    void directMultiInstanceTaskIsRecognizedWithoutAnOuterSubprocess() {
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("directItem");
        userTask.setLoopCharacteristics(loop);
        process.addFlowElement(userTask);
        item("directItem", Map.of("rowKey", Map.of("numeric_key", 3)), "execution-one");
        assertThat(resolver.resolve("task-one").currentItem()).isEqualTo(Map.of("rowKey", Map.of("numeric_key", 3)));
    }

    @Test
    void missingDefinitionCannotBeInterpretedAsOrdinaryTask() {
        when(definitions.getBpmnModel("deployed:1:old-definition")).thenReturn(null);
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
        verifyNoInteractions(runtime);
    }

    @Test
    void missingElementOrLoopVariableOrRowIdentityFailsClosed() {
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
        var loop = new MultiInstanceLoopCharacteristics();
        userTask.setLoopCharacteristics(loop);
        process.addFlowElement(userTask);
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
        loop.setElementVariable("declaredItem");
        for (Object value : java.util.List.of(Map.of(), Map.of("rowId", "not-a-configured-key"),
                Map.of("rowKey", Map.of("renamedKey", "")), Map.of("rowKey", Map.of("renamedKey", Map.of("id", "nested"))))) {
            item("declaredItem", value, "execution-one");
            assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
        }
    }

    @Test
    void executionReadFailurePropagatesInsteadOfGivingFullScope() {
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("declaredItem");
        userTask.setLoopCharacteristics(loop);
        process.addFlowElement(userTask);
        when(runtime.getVariableInstance("execution-one", "declaredItem")).thenThrow(new IllegalStateException("synthetic engine failure"));
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessage("synthetic engine failure");
    }

    @Test
    void processWideLoopVariableCannotImpersonateAnExecutionOwnedParticipant() {
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("declaredItem");
        userTask.setLoopCharacteristics(loop);
        process.addFlowElement(userTask);
        item("declaredItem", Map.of("rowKey", Map.of("key", "forged-process-value")), "instance-one");
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
    }

    private void item(String variable, Object value, String ownerExecution) {
        var instance = mock(org.flowable.variable.api.persistence.entity.VariableInstance.class);
        when(instance.getExecutionId()).thenReturn(ownerExecution);
        lenient().when(instance.getProcessInstanceId()).thenReturn("instance-one");
        lenient().when(instance.getValue()).thenReturn(value);
        when(runtime.getVariableInstance("execution-one", variable)).thenReturn(instance);
    }
}
