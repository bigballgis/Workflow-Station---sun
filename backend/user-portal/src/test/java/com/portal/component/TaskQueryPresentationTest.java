package com.portal.component;

import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import com.platform.common.list.ListColumnFilter;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class TaskQueryPresentationTest {
    @Test void operationalMineScansDoNotDependOnPresentationAndPaintedQueriesResolveBeforeFiltering() {
        assertThat(TaskQueryPresentation.neededBeforeFiltering(TaskQueryRequest.builder().build())).isFalse();
        assertThat(TaskQueryPresentation.neededBeforeFiltering(TaskQueryRequest.builder().sortBy("assigneeName").build())).isTrue();
        assertThat(TaskQueryPresentation.neededBeforeFiltering(TaskQueryRequest.builder().keyword("Finance").build())).isTrue();
        assertThat(TaskQueryPresentation.neededBeforeFiltering(TaskQueryRequest.builder().filters(List.of(
                new ListColumnFilter("currentStepName","eq","multi: Review",null))).build())).isTrue();
    }
    @Test void batchesMissingLabelsAndDoesNotRequeryAuthoritativeEmptyOrEnrichedMineRows() {
        var labels=mock(TaskPresentationLabels.class);
        doAnswer(call->{
            java.util.Collection<TaskInfo> rows=call.getArgument(0);
            rows.forEach(task->task.setAssigneeDisplay("")); return null;
        }).when(labels).annotate(anyCollection());
        var query=new TaskQueryPresentation(labels);
        var tasks=IntStream.range(0,1001).mapToObj(i->TaskInfo.builder().taskId("t"+i)
                .assignee("raw-holder").candidateUserIds(List.of("candidate")).build()).toList();
        query.annotate(tasks);
        var sizes=mockingDetails(labels).getInvocations().stream()
                .map(inv->((java.util.Collection<?>)inv.getArgument(0)).size()).toList();
        assertThat(sizes).containsExactly(1000,1);
        query.annotate(tasks);
        verify(labels,times(2)).annotate(anyCollection());
        assertThat(tasks).allSatisfy(task->{
            assertThat(task.getAssignee()).isEqualTo("raw-holder");
            assertThat(task.getCandidateUserIds()).containsExactly("candidate");
        });
    }
}
