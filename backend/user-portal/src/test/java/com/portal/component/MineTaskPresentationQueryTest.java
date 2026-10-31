package com.portal.component;

import com.platform.common.list.ListColumnFilter;
import com.portal.dto.*;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MineTaskPresentationQueryTest {
    @Test void presentationIsReadyBeforeMineFiltersAndCachedResultsStayPartitionedByActor() {
        var fetcher=mock(EngineVisibleTaskFetcher.class);
        var workspace=mock(WorkspaceTaskFilterComponent.class);
        var labels=mock(TaskPresentationLabels.class);
        when(workspace.filterFixedBuRoleTasksForActiveWorkspace(anyList(),anyString())).thenAnswer(call->call.getArgument(0));
        when(fetcher.fetchAllWithRequestContext(anyString(),isNull(),any())).thenAnswer(call->List.of(
                TaskInfo.builder().taskId(call.<String>getArgument(0)+"-task").assigneeName("Raw candidate")
                        .candidateUserIds(List.of(call.<String>getArgument(0))).build()));
        doAnswer(call->{
            Collection<TaskInfo> tasks=call.getArgument(0);
            tasks.forEach(task->task.setAssigneeDisplay("Finance + Approver")); return null;
        }).when(labels).annotate(anyCollection());
        var scanner=new MineTaskScanner(new MineTaskListCache(),fetcher,mock(RequestIdEnricher.class),
                mock(TaskPermissionEvaluator.class),mock(ClaimForceUnclaimAnnotator.class),workspace,
                mock(ProcessInstanceRepository.class),new TaskQueryPresentation(labels));
        var request=TaskQueryRequest.builder().filters(List.of(new ListColumnFilter("assigneeName","contains","Finance",null))).build();
        var first=scanner.scanMineTasks("actor-a",request,null,20);
        var second=scanner.scanMineTasks("actor-b",request,null,20);
        assertThat(first).extracting(TaskInfo::getTaskId).containsExactly("actor-a-task");
        assertThat(second).extracting(TaskInfo::getTaskId).containsExactly("actor-b-task");
        assertThat(scanner.scanMineTasks("actor-a",request,null,20)).containsExactlyElementsOf(first);
        verify(fetcher,times(2)).fetchAllWithRequestContext(anyString(),isNull(),any());
        verify(labels,times(2)).annotate(anyCollection());
    }
}
