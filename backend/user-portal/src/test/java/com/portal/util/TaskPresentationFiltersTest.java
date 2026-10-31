package com.portal.util;

import com.platform.common.list.ListColumnFilter;
import com.portal.dto.TaskInfo;
import com.portal.dto.TaskQueryRequest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TaskPresentationFiltersTest {
    private TaskInfo task(String id,String raw,String painted) {
        return TaskInfo.builder().taskId(id).assignee("raw-"+id).assigneeName(raw)
                .assigneeDisplay(painted).candidateUserIds(List.of("candidate-"+id)).build();
    }
    @Test void paintedPoolMatchesColumnAndToolbarWhileRawNamesAndIdsRemainSearchable() {
        var task=task("a","Ada, Bob","Finance + Department Manager");
        for (String value:List.of("Finance","Department Manager","Ada","raw-a")) {
            assertThat(TaskInfoQueryFilters.apply(List.of(task),TaskQueryRequest.builder()
                    .filters(List.of(new ListColumnFilter("assigneeName","contains",value,null))).build())).containsExactly(task);
            assertThat(TaskInfoQueryFilters.apply(List.of(task),TaskQueryRequest.builder().keyword(value).build())).containsExactly(task);
        }
        assertThat(TaskInfoQueryFilters.apply(List.of(task),TaskQueryRequest.builder()
                .filters(List.of(new ListColumnFilter("assigneeName","notContains","Finance",null))).build())).isEmpty();
        assertThat(task.getAssignee()).isEqualTo("raw-a");
        assertThat(task.getAssigneeName()).isEqualTo("Ada, Bob");
        assertThat(task.getCandidateUserIds()).containsExactly("candidate-a");
    }
    @Test void sortUsesPaintedLabelAndTreatsEmptyDisplayAsAuthoritative() {
        var finance=task("finance","Ada","Z Finance + Manager");
        var legal=task("legal","Zoe","A Legal + Approver");
        var empty=task("empty","Old synthetic holder","");
        var request=TaskQueryRequest.builder().sortBy("assigneeName").sortDirection("asc").build();
        assertThat(TaskInfoListOps.applySorting(List.of(finance,legal,empty),request)).containsExactly(empty,legal,finance);
        assertThat(TaskQueryColumnFilters.assigneeNameMatches(empty,new ListColumnFilter("assigneeName","isNull",null,null))).isTrue();
        assertThat(TaskQueryColumnFilters.assigneeNameMatches(empty,new ListColumnFilter("assigneeName","isNotNull",null,null))).isFalse();
    }
    @Test void stageFilteringUsesCompletePresentationInsteadOfNativeTaskName() {
        var task=TaskInfo.builder().taskId("mi").taskName("Review").currentStepName("multi: Review").build();
        var request=TaskQueryRequest.builder().filters(List.of(new ListColumnFilter("currentStepName","eq","multi: Review",null))).build();
        assertThat(EngineTaskPushdown.canFullyPush(request)).isFalse();
        assertThat(EngineTaskPushdown.from(request).hasFilterFragments()).isFalse();
        assertThat(TaskInfoQueryFilters.apply(List.of(task),request)).containsExactly(task);
    }
}
