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
    private final com.workflow.assignment.AssignmentTaskStateRepository states = mock(com.workflow.assignment.AssignmentTaskStateRepository.class);
    private final com.workflow.assignment.AssignmentDefinitionPolicy policies = mock(com.workflow.assignment.AssignmentDefinitionPolicy.class);
    private final TaskWriteContextResolver resolver = new TaskWriteContextResolver(tasks, definitions, runtime, states, policies);
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
    void protectedRawParticipantUsesFrozenConfiguredCompositeIdentity() throws Exception {
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("rawItem");
        userTask.setLoopCharacteristics(loop);
        process.addFlowElement(userTask);
        item("rawItem", Map.of("region", "HK", "sequence_no", 7), "execution-one");
        when(policies.tracked("deployed:1:old-definition")).thenReturn(true);
        var state = mock(com.workflow.assignment.AssignmentTaskStateRepository.State.class);
        when(state.taskId()).thenReturn("task-one"); when(state.active()).thenReturn(true); when(state.definition()).thenReturn("deployed:1:old-definition");
        when(state.instance()).thenReturn("instance-one"); when(state.node()).thenReturn("review");
        when(states.find("task-one")).thenReturn(java.util.Optional.of(state));
        when(states.position("task-one")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"multiInstances\":[{\"scope\":\"review\",\"row\":{\"region\":\"HK\",\"sequence_no\":7}}]}"));
        assertThat(resolver.resolve("task-one").currentItem()).isEqualTo(Map.of("rowKey", Map.of("region", "HK", "sequence_no", 7)));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void protectedCompositeIdentityPreservesBooleanPrimaryKeyType(boolean flag) throws Exception {
        protectedRawParticipantUsesFrozenConfiguredCompositeIdentity();
        var key = Map.of("tenant", "HK", "flag", flag);
        when(states.position("task-one")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(
                Map.of("multiInstances", java.util.List.of(Map.of("scope", "review", "row", key)))));
        assertThat(resolver.resolve("task-one").currentItem()).isEqualTo(Map.of("rowKey", key));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"{}", "{\"multiInstances\":[]}",
            "{\"multiInstances\":[{\"scope\":\"other\",\"row\":{\"id\":1}}]}",
            "{\"multiInstances\":[{\"scope\":\"review\",\"row\":{} }]}",
            "{\"multiInstances\":[{\"scope\":\"review\"},{\"scope\":\"review\",\"row\":{\"id\":1}}]}",
            "{\"multiInstances\":[{\"scope\":\"review\",\"row\":{\"id\":1}},{\"scope\":\"review\",\"row\":{\"id\":2}}]}",
            "{\"multiInstances\":[{\"scope\":\"review\",\"row\":{\"id\":{\"nested\":1}}}]}"})
    void damagedProtectedPositionCannotFallBackToRuntimeWrapper(String position) throws Exception {
        protectedRawParticipantUsesFrozenConfiguredCompositeIdentity();
        item("rawItem", Map.of("rowKey", Map.of("forged", "fallback")), "execution-one");
        when(states.position("task-one")).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper().readTree(position));
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
    }

    @Test
    void missingProtectedStateCannotFallBackToRuntimeWrapper() throws Exception {
        protectedRawParticipantUsesFrozenConfiguredCompositeIdentity();
        when(states.find("task-one")).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> resolver.resolve("task-one")).hasMessageContaining("cannot be verified");
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
