package com.workflow.component;

import com.platform.common.assignment.AssignmentExclusionPolicy;
import com.workflow.assignment.*;
import com.workflow.dto.response.TaskListResult.TaskInfo;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.repository.ExtendedTaskInfoRepository;
import org.flowable.engine.*;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskPresentationReaderTest {
    private final TaskService tasks=mock(TaskService.class);
    private final TaskQuery query=mock(TaskQuery.class,RETURNS_SELF);
    private final HistoryService history=mock(HistoryService.class);
    private final TaskInfoAssembler assembler=mock(TaskInfoAssembler.class);
    private final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    private final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    private final ExtendedTaskInfoRepository extended=mock(ExtendedTaskInfoRepository.class);
    private final AssignmentSourceValues sourceValues=mock(AssignmentSourceValues.class);
    private final TaskPresentationReader reader=new TaskPresentationReader(tasks,history,assembler,states,definitions);
    private final UUID caseId=UUID.randomUUID();

    @BeforeEach void configure() {
        when(tasks.createTaskQuery()).thenReturn(query);
        when(assembler.prewarmUserDisplayNames(anyList())).thenReturn(Map.of("process","initiator"));
        ReflectionTestUtils.setField(reader,"extended",extended);
        ReflectionTestUtils.setField(reader,"sourceValues",sourceValues);
        when(states.caseContext(caseId)).thenReturn(new AssignmentTaskStateRepository.CaseContext("original","CASE_BU",3,0));
    }

    @Test void actualNativeHolderTakesPrecedenceOverAllPoolAndDelegationLabels() {
        var info=info("held");
        info.setCurrentAssignee("stale-display-holder");
        info.setDelegatedTargetType("BU_ROLE"); info.setDelegatedBuCode("DELEGATED_BU"); info.setDelegatedRoleCode("delegate-role");
        var task=task("held","native-holder",info);
        var result=read(task).get(0);
        assertThat(result.task().getCurrentAssignee()).isEqualTo("native-holder");
        assertThat(result.scope()).isNull();
        verifyNoInteractions(states,definitions,sourceValues);
    }

    @Test void unclaimedDelegatedBuRoleUsesItsExplicitDelegationScope() {
        var info=info("delegated");
        info.setDelegatedTargetType("BU_ROLE"); info.setDelegatedBuCode("DELEGATED_BU"); info.setDelegatedRoleCode("delegate-role");
        var result=read(task("delegated",null,info)).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("DELEGATED_BU",List.of("delegate-role")));
    }

    @Test void miRolePoolRetainsEveryConfiguredRoleRatherThanOnlyTheLegacyFirstRole() {
        var info=info("mi");
        info.setMiAssigneeMode("role"); info.setMiBusinessUnitCode("MI_BU"); info.setMiRoleCode("role-a");
        when(extended.findByTaskIdAndIsDeletedFalse("mi")).thenReturn(Optional.of(ExtendedTaskInfo.builder()
                .extendedProperties("{\"assigneeMode\":\"role\",\"businessUnitCode\":\"MI_BU\",\"roleCodes\":[\"role-a\",\"role-b\"]}").build()));
        var result=read(task("mi",null,info)).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("MI_BU",List.of("role-a","role-b")));
    }

    @Test void fixedBackupScopeOverridesThePrimaryBuAndRoles() {
        var info=info("backup"); info.setBpmnAssigneeType("BU_ROLE");
        info.setBpmnBusinessUnitId("PRIMARY_BU"); info.setBpmnRoleIds(List.of("primary-role"));
        trackedBackup("backup",AssignmentExclusionPolicy.BuSource.FIXED_BU,AssignmentSourceSpec.fixed(List.of("original"),false));
        var result=read(task("backup",null,info)).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("BACKUP_BU",List.of("backup-a","backup-b")));
    }

    @Test void nodeBuBackupForARowUserReadsTheActualConfiguredRowBusinessUnit() {
        var info=info("row"); info.setBpmnAssigneeType("ELEMENT_VARIABLE");
        trackedBackup("row",AssignmentExclusionPolicy.BuSource.NODE_ASSIGNMENT_BU,AssignmentSourceSpec.row("reviewer",null,"rowBu"));
        var task=task("row",null,info);
        when(sourceValues.presentationSources(anyMap())).thenReturn(Map.of("row",Map.of("rowBu","ROW_BU")));
        var result=read(task).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("ROW_BU",List.of("backup-a","backup-b")));
        verify(sourceValues).presentationSources(argThat(selected->selected.size()==1 && selected.containsKey(task)));
    }

    @Test void unavailableNodeBuNeverInventsACaseOrPrimaryBusinessUnit() {
        var info=info("missing-bu"); info.setBpmnAssigneeType("ELEMENT_VARIABLE");
        trackedBackup("missing-bu",AssignmentExclusionPolicy.BuSource.NODE_ASSIGNMENT_BU,AssignmentSourceSpec.row("reviewer",null,"rowBu"));
        var task=task("missing-bu",null,info);
        when(sourceValues.presentationSources(anyMap())).thenReturn(Map.of("missing-bu",Map.of()));
        assertThatThrownBy(()->read(task)).isInstanceOf(IllegalStateException.class);
    }

    @Test void aDynamicPrimaryRolePoolUsesCurrentFactsInsteadOfCreationMetadata() {
        var info=info("changed"); info.setBpmnAssigneeType("ELEMENT_VARIABLE");
        info.setMiAssigneeMode("role"); info.setMiBusinessUnitCode("OLD_BU"); info.setMiRoleCode("old-role");
        var state=trackedBackup("changed",AssignmentExclusionPolicy.BuSource.NODE_ASSIGNMENT_BU,AssignmentSourceSpec.row("owner","roles","bu"));
        when(state.poolSource()).thenReturn("PRIMARY");
        when(sourceValues.presentationSources(anyMap())).thenReturn(Map.of("changed",Map.of("bu","NEW_BU","roles",List.of("new-a","new-b"))));
        var result=read(task("changed",null,info)).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("NEW_BU",List.of("new-a","new-b")));
        verifyNoInteractions(extended);
    }

    @Test void nodeBuBackupUsesCurrentRowBuEvenWhenLegacyMiMetadataNamesAnotherBu() {
        var info=info("changed-backup"); info.setBpmnAssigneeType("ELEMENT_VARIABLE");
        info.setMiAssigneeMode("role"); info.setMiBusinessUnitCode("OLD_BU"); info.setMiRoleCode("old-role");
        trackedBackup("changed-backup",AssignmentExclusionPolicy.BuSource.NODE_ASSIGNMENT_BU,AssignmentSourceSpec.row("owner","roles","bu"));
        when(sourceValues.presentationSources(anyMap())).thenReturn(Map.of("changed-backup",Map.of("bu","NEW_BU","roles",List.of("new-role"))));
        var result=read(task("changed-backup",null,info)).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("NEW_BU",List.of("backup-a","backup-b")));
        verifyNoInteractions(extended);
    }

    @Test void legacyInitiatorPresentationRestoresActualNativeCandidatesAfterAssemblerNormalization() {
        var info=info("legacy"); info.setBpmnAssigneeType("PROCESS_INITIATOR");
        info.setCurrentAssignee("synthetic-initiator"); info.setCandidateUserIds(List.of());
        var candidate=mock(org.flowable.identitylink.api.IdentityLink.class);
        when(candidate.getType()).thenReturn("candidate"); when(candidate.getUserId()).thenReturn("actual-candidate");
        when(tasks.getIdentityLinksForTask("legacy")).thenReturn(List.of(candidate));
        var result=read(task("legacy",null,info)).get(0);
        assertThat(result.task().getCurrentAssignee()).isNull();
        assertThat(result.task().getCandidateUserIds()).containsExactly("actual-candidate");
    }

    @Test void rowRoleNodeBuUsesTheSameCaseBuDefaultAsQualificationWhenNoBuFieldIsConfigured() {
        var info=info("default-bu"); info.setBpmnAssigneeType("ELEMENT_VARIABLE");
        trackedBackup("default-bu",AssignmentExclusionPolicy.BuSource.NODE_ASSIGNMENT_BU,AssignmentSourceSpec.row(null,"roles",null));
        when(sourceValues.presentationSources(anyMap())).thenReturn(Map.of("default-bu",Map.of("roles",List.of("primary-role"))));
        var result=read(task("default-bu",null,info)).get(0);
        assertThat(result.scope()).isEqualTo(new TaskPresentationReader.Scope("CASE_BU",List.of("backup-a","backup-b")));
    }

    @Test void aProcessReturnsAllMiTasksAndUsesTheExistingBatchPrewarm() {
        List<Task> selected=IntStream.range(0,125).mapToObj(i->task("mi-"+i,null,info("mi-"+i))).toList();
        when(query.list()).thenReturn(selected);
        var result=reader.read(new TaskPresentationReader.Request(List.of("process"),List.of()));
        assertThat(result).hasSize(125);
        assertThat(result).extracting(entry->entry.task().getTaskId())
                .containsExactlyElementsOf(IntStream.range(0,125).mapToObj(i->"mi-"+i).toList());
        verify(assembler).prewarmUserDisplayNames(selected);
        verify(query,never()).listPage(anyInt(),anyInt());
        verifyNoInteractions(history);
    }

    @Test void anEmptySelectionIsRejectedBeforeAnyEngineQuery() {
        assertThatThrownBy(()->reader.read(new TaskPresentationReader.Request(List.of(),List.of())))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(tasks,history,assembler,states,definitions);
    }

    @Test void explicitlyRequestedFinishedTasksKeepTheirHistoricHolder() {
        var pastQuery=mock(org.flowable.task.api.history.HistoricTaskInstanceQuery.class,RETURNS_SELF);
        var past=mock(org.flowable.task.api.history.HistoricTaskInstance.class);
        when(history.createHistoricTaskInstanceQuery()).thenReturn(pastQuery);
        when(query.list()).thenReturn(List.of()); when(pastQuery.list()).thenReturn(List.of(past));
        var info=info("done"); info.setCurrentAssignee("historic-holder"); info.setVariables(Map.of("private","must-not-leak"));
        when(assembler.buildTaskInfoFromHistoricTask(past)).thenReturn(info);
        var result=reader.read(new TaskPresentationReader.Request(List.of(),List.of("done")));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).task().getCurrentAssignee()).isEqualTo("historic-holder");
        assertThat(result.get(0).task().getVariables()).isNull();
        assertThat(result.get(0).scope()).isNull();
        verify(pastQuery).taskIds(List.of("done")); verify(pastQuery).finished();
    }

    private TaskInfo info(String id) {
        return TaskInfo.builder().taskId(id).processInstanceId("process").processDefinitionId("frozen")
                .taskDefinitionKey("work").taskName("Sub form").currentStepName("Multi: Sub form").build();
    }
    private Task task(String id,String holder,TaskInfo info) {
        var task=mock(Task.class);
        when(task.getId()).thenReturn(id); when(task.getAssignee()).thenReturn(holder);
        when(task.getProcessInstanceId()).thenReturn("process"); when(task.getProcessDefinitionId()).thenReturn("frozen");
        when(task.getTaskDefinitionKey()).thenReturn("work");
        when(assembler.convertFlowableTaskToTaskInfo(task)).thenReturn(info);
        when(assembler.convertFlowableTaskToTaskInfo(eq(task),anyMap())).thenReturn(info);
        return task;
    }
    private List<TaskPresentationReader.Entry> read(Task... selected) {
        when(query.list()).thenReturn(List.of(selected));
        return reader.read(new TaskPresentationReader.Request(List.of("process"),List.of()));
    }
    private AssignmentTaskStateRepository.State trackedBackup(String task,AssignmentExclusionPolicy.BuSource bu,AssignmentSourceSpec source) {
        when(definitions.tracked("frozen")).thenReturn(true);
        var state=mock(AssignmentTaskStateRepository.State.class);
        when(states.find(task)).thenReturn(Optional.of(state)); when(state.poolSource()).thenReturn("BACKUP");
        when(state.sourceSpec()).thenReturn(source);
        when(state.caseId()).thenReturn(caseId);
        when(definitions.node("frozen","work")).thenReturn(new AssignmentExclusionPolicy(1,true,false,false,List.of(),
                new AssignmentExclusionPolicy.Backup(true,AssignmentExclusionPolicy.Mode.BU_ROLE,bu,
                        bu==AssignmentExclusionPolicy.BuSource.FIXED_BU?"BACKUP_BU":null,List.of("backup-a","backup-b"),List.of())));
        return state;
    }
}
