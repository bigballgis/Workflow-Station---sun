package com.workflow.component;

import com.workflow.assignment.AssignmentTaskTrackingQuery;
import com.workflow.client.AdminCenterClient;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TaskInfoAssemblerAssignmentHolderTest {
    @ParameterizedTest @ValueSource(booleans={true,false})
    void protectedUnclaimedInitiatorTaskMustNotRegainTheExcludedInitiator(boolean tracked) {
        var assembler=new TaskInfoAssembler();
        var task=mock(Task.class); var parser=mock(BpmnActionParser.class);
        var tracking=mock(AssignmentTaskTrackingQuery.class);
        ReflectionTestUtils.setField(assembler,"taskService",mock(TaskService.class));
        ReflectionTestUtils.setField(assembler,"runtimeService",mock(RuntimeService.class));
        ReflectionTestUtils.setField(assembler,"repositoryService",mock(RepositoryService.class,RETURNS_DEEP_STUBS));
        ReflectionTestUtils.setField(assembler,"adminCenterClient",mock(AdminCenterClient.class));
        ReflectionTestUtils.setField(assembler,"bpmnActionParser",parser);
        ReflectionTestUtils.setField(assembler,"extendedTaskInfoRepository",mock(ExtendedTaskInfoRepository.class));
        when(task.getId()).thenReturn("task"); when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("process:1:frozen"); when(task.getTaskDefinitionKey()).thenReturn("work");
        when(parser.getUserTaskExtensionPropertyValue("process:1:frozen","work","assigneeType")).thenReturn("PROCESS_INITIATOR");
        ReflectionTestUtils.setField(assembler,"assignmentTracking",tracking);
        when(tracking.trackedInstance("instance","process:1:frozen")).thenReturn(tracked);
        var result=assembler.buildTaskInfoFromFlowableTask(task,false,Map.of("instance","initiator"));
        if (tracked) {
            assertThat(result.getCurrentAssignee()).isNull();
            assertThat(result.getAssignmentTarget()).isNull();
        } else assertThat(result.getCurrentAssignee()).isEqualTo("initiator");
    }
}
