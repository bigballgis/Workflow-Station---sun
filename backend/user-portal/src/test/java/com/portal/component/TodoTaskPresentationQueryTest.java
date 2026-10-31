package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.*;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TodoTaskPresentationQueryTest {
    @Test void unionIsLabeledBeforeFilteringCountingSortingAndPagination() {
        var engine=mock(WorkflowEngineClient.class);
        var workspace=mock(WorkspaceTaskFilterComponent.class);
        var scanner=mock(MineTaskScanner.class);
        var labels=mock(TaskPresentationLabels.class);
        var mine=TaskInfo.builder().taskId("mine").assignee("native-id").assigneeName("Zoe")
                .currentStepName("multi: Review").assigneeDisplay("A Legal + Approver").build();
        when(scanner.scanMineTasks(eq("actor"),any(),isNull(),anyInt())).thenReturn(List.of(mine));
        when(engine.getUserClaimPoolTasks(eq("actor"),eq(0),eq(500))).thenReturn(Optional.of(Map.of("tasks",List.of(
                Map.of("taskId","pool","taskName","Review","bpmnAssigneeType","BU_ROLE", "candidateUserIds",List.of("candidate-a"))))));
        when(workspace.filterFixedBuRoleTasksForActiveWorkspace(anyList(),eq("actor"))).thenAnswer(call->call.getArgument(0));
        doAnswer(call->{
            Collection<TaskInfo> tasks=call.getArgument(0);
            tasks.forEach(task->{ task.setAssigneeDisplay("Z Finance + Approver"); task.setCurrentStepName("multi: Review"); });
            return null;
        }).when(labels).annotate(anyCollection());
        var component=new TodoListQueryComponent(engine,mock(ProcessInstanceRepository.class),workspace,
                mock(RequestIdEnricher.class),mock(TaskPermissionEvaluator.class),mock(ClaimForceUnclaimAnnotator.class),
                scanner,new TaskQueryPresentation(labels));
        var ordered=component.queryTodoList("actor",new TodoTaskQueryRequest(0,1,List.of(),"assigneeName","asc",null,List.of(),List.of()));
        assertThat(ordered.totalElements()).isEqualTo(2);
        assertThat(ordered.content()).extracting(TaskInfo::getTaskId).containsExactly("mine");
        var filtered=component.queryTodoList("actor",new TodoTaskQueryRequest(0,1,List.of(
                new ListColumnFilter("assigneeName","contains","Finance",null),
                new ListColumnFilter("currentStepName","eq","multi: Review",null)),null,null,null,List.of(),List.of()));
        assertThat(filtered.totalElements()).isEqualTo(1);
        assertThat(filtered.content()).extracting(TaskInfo::getTaskId).containsExactly("pool");
        assertThat(filtered.content().get(0).getCandidateUserIds()).containsExactly("candidate-a");
        assertThat(mine.getAssignee()).isEqualTo("native-id");
        assertThat(mine.getAssigneeName()).isEqualTo("Zoe");
    }
}
