package com.portal.component;

import com.portal.client.WorkflowEngineClient;
import com.portal.dto.PortalListPage;
import com.portal.dto.TaskInfo;
import com.portal.dto.TodoTaskQueryRequest;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each To Do row carries the batch group it can be processed in, so the list can flag exactly
 * the rows the Batch Actions tab offers. The rule is BatchTaskEligibility's, not a second copy.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TodoListBatchGroupKeyTest {

    private static final String ME = "u-1";

    @Mock private WorkflowEngineClient workflowEngineClient;
    @Mock private ProcessInstanceRepository processInstanceRepository;
    @Mock private WorkspaceTaskFilterComponent workspaceTaskFilter;
    @Mock private RequestIdEnricher requestIdEnricher;
    @Mock private TaskPermissionEvaluator taskPermissionEvaluator;
    @Mock private ClaimForceUnclaimAnnotator claimForceUnclaimAnnotator;
    @Mock private MineTaskScanner mineTaskScanner;

    @InjectMocks
    private TodoListQueryComponent component;

    @BeforeEach
    void emptyClaimPool() {
        when(workflowEngineClient.getUserClaimPoolTasks(anyString(), anyInt(), anyInt()))
                .thenReturn(Optional.of(Map.of("tasks", List.of())));
        when(workspaceTaskFilter.filterFixedBuRoleTasksForActiveWorkspace(any(), anyString()))
                .thenAnswer(a -> a.getArgument(0));
    }

    private static TaskInfo.TaskInfoBuilder task(String taskId) {
        return TaskInfo.builder()
                .taskId(taskId)
                .assignmentClaimAccess(com.portal.testsupport.AssignmentReadTestSupport.legacy(taskId))
                .processInstanceId("pi-" + taskId)
                .processDefinitionId("purchase:3:abc")
                .taskDefinitionKey("dept")
                .functionUnitCatalogId("cat-1023")
                .assignee(ME)
                .allowBatch(true);
    }

    private Map<String, String> batchGroupKeysOf(TaskInfo... mine) {
        when(mineTaskScanner.scanMineTasks(eq(ME), any(), any(), anyInt())).thenReturn(List.of(mine));
        PortalListPage<TaskInfo> page = component.queryTodoList(ME,
                new TodoTaskQueryRequest(0, 20, List.of(), null, null, null, List.of(), List.of()));
        return page.content().stream().collect(Collectors.toMap(TaskInfo::getTaskId,
                t -> String.valueOf(t.getBatchGroupKey()), (a, b) -> a, LinkedHashMap::new));
    }

    @Test
    void flagsOnlyTheRowsIMayProcessInBatch() {
        Map<String, String> keys = batchGroupKeysOf(
                task("eligible").build(),
                task("no-batch-on-node").allowBatch(false).build(),
                task("held-by-colleague").assignee("u-2").build(),
                task("pool-i-may-claim").assignee(null).claimPoolTask(true).claimable(true).build(),
                task("candidate-pool").assignee(null).candidateUserIds(List.of(ME, "u-2")).build(),
                task("no-candidates").assignee(null).build(),
                task("mi-subtask").multiInstanceSubTask(true).build());

        assertThat(keys).containsEntry("eligible", "fu:cat-1023")
                .containsEntry("no-batch-on-node", "null")
                .containsEntry("held-by-colleague", "null")
                .containsEntry("pool-i-may-claim", "fu:cat-1023")
                .containsEntry("candidate-pool", "fu:cat-1023")
                .containsEntry("no-candidates", "null")
                .containsEntry("mi-subtask", "fu:cat-1023");
        // The list trusts To Do membership: canProcessTask's group check is a remote call per row.
        verify(taskPermissionEvaluator, never()).canProcessTask(any(), any(), any());
    }

    /** A request with no pinned release groups by engine definition, as the Batch tab does. */
    @Test
    void usesTheSameGroupKeyAsTheBatchTab() {
        TaskInfo unpinned = task("unpinned").functionUnitCatalogId(null).build();

        assertThat(batchGroupKeysOf(unpinned)).containsEntry("unpinned", "pd:purchase:3:abc");
    }
}
