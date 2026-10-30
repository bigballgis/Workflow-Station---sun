package com.portal.component;

import com.platform.common.assignment.AssignmentTaskReadSnapshot;
import com.platform.common.assignment.AssignmentTaskReadSnapshot.*;
import com.portal.client.*;
import com.portal.dto.*;
import com.portal.repository.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real list/permission/claim-batch components; remote engine and identity projection are synthetic. */
class AssignmentTodoClaimReadTest {
    private final WorkflowEngineClient engine=mock(WorkflowEngineClient.class);
    private final AssignmentTaskReadClient reads=mock(AssignmentTaskReadClient.class);
    private final MineTaskScanner scanner=mock(MineTaskScanner.class);
    private final WorkspaceTaskFilterComponent workspace=mock(WorkspaceTaskFilterComponent.class);
    private final TaskPermissionEvaluator permissions=new TaskPermissionEvaluator(mock(DelegationRuleRepository.class),engine,
            workspace,new AssignmentClaimReadAnnotator(reads));
    private final TodoListQueryComponent todo=new TodoListQueryComponent(engine,mock(ProcessInstanceRepository.class),workspace,
            mock(RequestIdEnricher.class),permissions,mock(ClaimForceUnclaimAnnotator.class),scanner);
    private void setup() {
        var raw=IntStream.range(0,105).mapToObj(i->Map.<String,Object>of("taskId","task-"+i,
                "processDefinitionId","definition","taskDefinitionKey","review","bpmnAssigneeType","BU_ROLE",
                "candidateUserIds",List.of("actor"),"allowBatch",true)).toList();
        when(engine.getUserClaimPoolTasks(eq("actor"),anyInt(),eq(500))).thenReturn(Optional.of(Map.of("tasks",raw)));
        when(scanner.scanMineTasks(eq("actor"),any(),isNull(),anyInt())).thenReturn(List.of());
        when(workspace.filterFixedBuRoleTasksForActiveWorkspace(anyList(),eq("actor"))).thenAnswer(call->call.getArgument(0));
        when(reads.read(anyList(),eq("actor"))).thenAnswer(call->{
            List<String> ids=call.getArgument(0);
            var entries=ids.stream().map(id->new Entry(id,true,State.CURRENT,
                    new Claim(!id.equals("task-0") && !id.equals("task-104"),"RESOLVER",null),
                    new Stamp(2L,7,Instant.now().plusSeconds(120)))).toList();
            return new AssignmentTaskReadSnapshot(1,"actor",7,Instant.now(),entries);
        });
    }
    @Test void batchGroupExclusionAffectsTotalBeforePagination() {
        setup();
        var request=new TodoTaskQueryRequest(0,1,List.of(),null,null,null,List.of(),List.of(),"pd:definition","review");
        var page=todo.queryTodoList("actor",request);
        assertThat(page.totalElements()).isEqualTo(103);
        assertThat(page.content()).extracting(TaskInfo::getTaskId).containsExactly("task-1");
    }
    @Test void ordinaryListKeepsReadOnlyRowsAndClaimAllUsesSameVetoBeforeItsSliceAndCount() {
        setup();
        var page=todo.queryTodoList("actor",new TodoTaskQueryRequest(0,1,List.of(),null,null,null,List.of(),List.of()));
        assertThat(page.totalElements()).isEqualTo(105);
        assertThat(page.content().get(0).getTaskId()).isEqualTo("task-0");
        assertThat(page.content().get(0).isClaimable()).isFalse();
        var query=mock(TaskQueryComponent.class);
        when(query.listClaimPoolTasks("actor")).thenAnswer(call->todo.listClaimPoolTasks("actor"));
        var process=mock(TaskProcessComponent.class);
        when(process.claimTask(anyString(),eq("actor"),isNull())).thenReturn(TaskInfo.builder().build());
        var result=new ClaimBatchComponent(query,process).claimNextBatch("actor",null,new ClaimBatchRequest(List.of()));
        assertThat(result.claimed()).isEqualTo(100);
        assertThat(result.remaining()).isEqualTo(3);
        assertThat(result.attemptedTaskIds()).doesNotContain("task-0","task-104");
    }
    @Test void cachedMineFlagsAreRecomputedBeforeGroupSelection() {
        setup();
        when(engine.getUserClaimPoolTasks(eq("actor"),anyInt(),eq(500))).thenReturn(Optional.of(Map.of("tasks",List.of())));
        var stale=TaskInfo.builder().taskId("task-0").processDefinitionId("definition").taskDefinitionKey("review")
                .bpmnAssigneeType("BU_ROLE").candidateUserIds(List.of("actor")).allowBatch(true).claimable(true).build();
        when(scanner.scanMineTasks(eq("actor"),any(),isNull(),anyInt())).thenReturn(List.of(stale));
        var page=todo.queryTodoList("actor",new TodoTaskQueryRequest(0,1,List.of(),null,null,null,List.of(),List.of(),"pd:definition","review"));
        assertThat(page.totalElements()).isZero();
        assertThat(stale.isClaimable()).isFalse();
    }
    @Test void changedHolderIsRemovedBeforeBatchGroupCountAndPagination() {
        setup();
        when(engine.getUserClaimPoolTasks(eq("actor"),anyInt(),eq(500))).thenReturn(Optional.of(Map.of("tasks",List.of())));
        var stale=TaskInfo.builder().taskId("changed").assignee("actor").processDefinitionId("definition")
                .taskDefinitionKey("review").allowBatch(true).build();
        var current=TaskInfo.builder().taskId("current").assignee("actor").processDefinitionId("definition")
                .taskDefinitionKey("review").allowBatch(true).build();
        when(scanner.scanMineTasks(eq("actor"),any(),isNull(),anyInt())).thenReturn(List.of(stale,current));
        when(reads.read(anyList(),eq("actor"))).thenAnswer(call->{
            List<String> ids=call.getArgument(0);
            var entries=ids.stream().map(id->new Entry(id,true,State.CURRENT,
                    new Claim(false,"RESOLVER","PRIMARY",new Holding(id.equals("changed")?"someone-else":"actor")),
                    new Stamp(2L,7,Instant.now().plusSeconds(120)))).toList();
            return new AssignmentTaskReadSnapshot(1,"actor",7,Instant.now(),entries);
        });
        var page=todo.queryTodoList("actor",new TodoTaskQueryRequest(0,1,List.of(),null,null,null,List.of(),List.of(),"pd:definition","review"));
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(TaskInfo::getTaskId).containsExactly("current");
    }
    @Test void claimAllIncludesVerifiedBackupTasksFromMineBeforeItsQuotaAndRemainingCount() {
        setup();
        var backup=TaskInfo.builder().taskId("backup").assignmentType("CANDIDATE_USERS")
                .bpmnAssigneeType("HIERARCHY_ROLE").candidateUserIds(List.of("actor")).build();
        when(scanner.scanMineTasks(eq("actor"),any(),isNull(),anyInt())).thenReturn(List.of(backup));
        when(reads.read(anyList(),eq("actor"))).thenAnswer(call->{
            List<String> ids=call.getArgument(0);
            var entries=ids.stream().map(id->new Entry(id,true,State.CURRENT,
                    new Claim(!id.equals("task-0") && !id.equals("task-104"),id.equals("backup")?"BACKUP":"RESOLVER",null),
                    new Stamp(2L,7,Instant.now().plusSeconds(120)))).toList();
            return new AssignmentTaskReadSnapshot(1,"actor",7,Instant.now(),entries);
        });
        var query=mock(TaskQueryComponent.class);
        when(query.listClaimPoolTasks("actor")).thenAnswer(call->todo.listClaimPoolTasks("actor"));
        var process=mock(TaskProcessComponent.class);
        when(process.claimTask(anyString(),eq("actor"),isNull())).thenReturn(TaskInfo.builder().build());
        var result=new ClaimBatchComponent(query,process).claimNextBatch("actor",null,new ClaimBatchRequest(List.of()));
        assertThat(result.claimed()).isEqualTo(100); assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.attemptedTaskIds()).contains("backup").doesNotContain("task-0","task-104");
    }
}
