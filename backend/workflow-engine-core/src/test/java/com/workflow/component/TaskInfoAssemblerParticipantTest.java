package com.workflow.component;

import com.workflow.client.AdminCenterClient;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.variable.api.persistence.entity.VariableInstance;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskInfoAssemblerParticipantTest {
    @Test
    void detailUsesDeployedParticipantRatherThanProcessWideLegacyRow() {
        verifyDetail(false);
    }

    @Test
    void unavailableConfiguredParticipantRejectsDetailInsteadOfReturningFullScope() {
        verifyDetail(true);
    }

    private void verifyDetail(boolean unavailable) {
        var assembler = new TaskInfoAssembler();
        var runtime = mock(RuntimeService.class);
        var definitions = mock(RepositoryService.class, RETURNS_DEEP_STUBS);
        ReflectionTestUtils.setField(assembler, "runtimeService", runtime);
        ReflectionTestUtils.setField(assembler, "repositoryService", definitions);
        ReflectionTestUtils.setField(assembler, "taskService", mock(TaskService.class));
        ReflectionTestUtils.setField(assembler, "bpmnActionParser", mock(BpmnActionParser.class));
        ReflectionTestUtils.setField(assembler, "adminCenterClient", mock(AdminCenterClient.class));
        ReflectionTestUtils.setField(assembler, "extendedTaskInfoRepository", mock(ExtendedTaskInfoRepository.class));
        var task = mock(Task.class);
        when(task.getId()).thenReturn("task");
        when(task.getExecutionId()).thenReturn("child-execution");
        when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("deployed:1:definition");
        when(task.getTaskDefinitionKey()).thenReturn("work");
        var model = new BpmnModel();
        var process = new org.flowable.bpmn.model.Process();
        process.setId("process");
        var node = new UserTask();
        node.setId("work");
        var loop = new MultiInstanceLoopCharacteristics();
        loop.setElementVariable("item");
        node.setLoopCharacteristics(loop);
        process.addFlowElement(node);
        model.addProcess(process);
        lenient().when(definitions.getBpmnModel("deployed:1:definition")).thenReturn(model);
        var persisted = new HashMap<String, Object>(Map.of("currentItem", Map.of("rowKey", Map.of("key", "foreign")),
                "_currentItem", Map.of("rowKey", Map.of("key", "foreign")), "ordinary", "retained"));
        when(runtime.getVariables("instance")).thenReturn(persisted);
        var variable = mock(VariableInstance.class);
        lenient().when(variable.getExecutionId()).thenReturn("child-execution");
        lenient().when(variable.getProcessInstanceId()).thenReturn("instance");
        var row = Map.of("rowKey", Map.of("key", "own"));
        lenient().when(variable.getValue()).thenReturn(row);
        lenient().when(runtime.getVariableInstance("child-execution", "item")).thenReturn(unavailable ? null : variable);

        if (unavailable) {
            assertThatThrownBy(() -> assembler.buildTaskInfoFromFlowableTask(task, true, Map.of("instance", "initiator")))
                    .hasMessageContaining("Configured MI participant variable is unavailable");
            assertThat(persisted).containsKey("currentItem");
            return;
        }

        var result = assembler.buildTaskInfoFromFlowableTask(task, true, Map.of("instance", "initiator"));

        assertThat(result.getVariables()).containsEntry("_currentItem", row).containsEntry("ordinary", "retained");
        assertThat(result.getVariables()).doesNotContainKey("currentItem");
        assertThat(persisted.get("_currentItem")).isNotEqualTo(row);
    }
}
