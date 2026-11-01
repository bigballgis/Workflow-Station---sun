package com.workflow.component;

import com.workflow.assignment.AssignmentWriteBoundary;
import com.workflow.repository.ExtendedTaskInfoRepository;
import java.util.HashMap;
import java.util.Map;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TaskCompletionOutcomeTest {
    @Test void outcomeIsRecordedBeforeCompletingEvenWithoutAFreeTextComment() {
        var service = new TaskCompletionService();
        var tasks = mock(TaskService.class);
        var query = mock(org.flowable.task.api.TaskQuery.class, RETURNS_SELF);
        when(tasks.createTaskQuery()).thenReturn(query);
        var runtime = mock(RuntimeService.class);
        var actionService = mock(TaskActionService.class);
        var task = mock(Task.class);
        when(task.getAssignee()).thenReturn("actor");
        when(task.getProcessInstanceId()).thenReturn("process");
        when(query.singleResult()).thenReturn(task);
        when(runtime.getVariable("process", "initiator")).thenReturn("actor");
        when(actionService.engineActorMatchesPortalUser("actor", "actor")).thenReturn(true);
        ReflectionTestUtils.setField(service, "taskService", tasks);
        ReflectionTestUtils.setField(service, "runtimeService", runtime);
        ReflectionTestUtils.setField(service, "taskActionService", actionService);
        ReflectionTestUtils.setField(service, "extendedTaskInfoRepository", mock(ExtendedTaskInfoRepository.class));
        ReflectionTestUtils.setField(service, "taskMultiInstanceService", mock(TaskMultiInstanceService.class));
        ReflectionTestUtils.setField(service, "assignmentBoundary", mock(AssignmentWriteBoundary.class));
        var variables = new HashMap<String,Object>(Map.of("action", "REJECT"));
        assertThat(service.completeTask("task", "actor", variables, false).isSuccess()).isTrue();
        var ordered = inOrder(tasks);
        ordered.verify(tasks).addComment("task", "process", "workflow-completion:REJECT", "");
        ordered.verify(tasks).complete("task", variables);
    }
}
