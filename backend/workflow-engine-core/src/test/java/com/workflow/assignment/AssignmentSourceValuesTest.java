package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class AssignmentSourceValuesTest {
    final RuntimeService runtime=mock(RuntimeService.class);
    final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    final AssignmentSourceSnapshotClient portal=mock(AssignmentSourceSnapshotClient.class);
    final AssignmentSourceValues values=new AssignmentSourceValues(runtime,definitions,states,portal);
    final Task task=mock(Task.class);
    final UUID caseId=UUID.randomUUID();
    @BeforeEach void configure() {
        when(task.getId()).thenReturn("task"); when(task.getProcessInstanceId()).thenReturn("instance");
        var state=mock(AssignmentTaskStateRepository.State.class);
        when(state.caseId()).thenReturn(caseId); when(state.instance()).thenReturn("instance"); when(state.definition()).thenReturn("frozen");
        when(states.find("task")).thenReturn(java.util.Optional.of(state));
        when(states.caseContext(caseId)).thenReturn(new AssignmentTaskStateRepository.CaseContext("owner","BU",3,0));
        when(states.portalRevision(caseId)).thenReturn(2L);
        when(runtime.getVariables("instance")).thenReturn(Map.of("reviewer","stale"));
    }
    @Test void readySnapshotNeverInheritsAnOldRuntimeValue() {
        snapshot(AssignmentSourceSnapshot.State.READY,Map.of("reviewer","saved"),3);
        assertEquals("saved",values.variables(task,List.of("reviewer")).get("reviewer"));
        verifyNoInteractions(runtime);
        snapshot(AssignmentSourceSnapshot.State.READY,Map.of(),3);
        assertFalse(values.variables(task,List.of("reviewer")).containsKey("reviewer"));
    }
    @Test void onlyExplicitInitialStatePermitsRuntimeSource() {
        snapshot(AssignmentSourceSnapshot.State.INITIAL,Map.of(),3);
        assertEquals("stale",values.variables(task,List.of("reviewer")).get("reviewer"));
        when(portal.read(any())).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE));
        assertThrows(AssignmentFailure.class,()->values.variables(task,List.of("reviewer")));
    }
    @Test void firstTaskCreationUsesItsOwnInFlightValuesWithoutCallingPortal() {
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            AssignmentCreationBuffer.begin("task");
            assertEquals("stale",values.variables(task,List.of("reviewer")).get("reviewer"));
            verifyNoInteractions(portal,states);
            AssignmentCreationBuffer.finish("task",java.util.Set.of());
        } finally {
            for (var scope:org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()) scope.afterCompletion(1);
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
            org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }
    @Test void concurrentSavedSourceChangeInvalidatesPreparation() {
        snapshot(AssignmentSourceSnapshot.State.READY,Map.of("reviewer","saved"),4);
        assertThrows(AssignmentFailure.class,()->values.variables(task,List.of("reviewer")));
        snapshot(AssignmentSourceSnapshot.State.READY,Map.of("reviewer","saved"),3);
        when(states.portalRevision(caseId)).thenReturn(3L);
        assertThrows(AssignmentFailure.class,()->values.variables(task,List.of("reviewer")));
        verifyNoInteractions(runtime);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void nestedMiUsesNearestFrozenScopeAndItsFullKey(boolean declaredOnChild) throws Exception {
        when(task.getProcessDefinitionId()).thenReturn("frozen"); when(task.getTaskDefinitionKey()).thenReturn("review");
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("fu"); model.addProcess(process);
        var outer=new SubProcess(); outer.setId("outer"); outer.setLoopCharacteristics(new MultiInstanceLoopCharacteristics()); process.addFlowElement(outer);
        var inner=new SubProcess(); inner.setId("inner"); inner.setLoopCharacteristics(new MultiInstanceLoopCharacteristics()); outer.addFlowElement(inner);
        var review=new UserTask(); review.setId("review"); inner.addFlowElement(review);
        var container=new ExtensionElement(); container.setName("properties");
        var property=new ExtensionElement(); property.setName("property");
        var name=new ExtensionAttribute(); name.setName("name"); name.setValue("subTableName"); property.addAttribute(name);
        var value=new ExtensionAttribute(); value.setName("value"); value.setValue("Line_Items"); property.addAttribute(value);
        container.addChildElement(property); (declaredOnChild?review:inner).addExtensionElement(container);
        when(definitions.model("frozen")).thenReturn(model);
        when(states.position("task")).thenReturn(JsonUtils.getObjectMapper().readTree("{\"multiInstances\":[{\"scope\":\"outer\",\"row\":{\"parent\":\"A\"}},{\"scope\":\"inner\",\"row\":{\"region\":\"HK\",\"number\":7}}]}"));
        snapshot(AssignmentSourceSnapshot.State.READY,Map.of("reviewer","saved"),3);
        assertEquals("saved",values.row(task,List.of("reviewer")).get("reviewer"));
        var capture=org.mockito.ArgumentCaptor.forClass(AssignmentSourceSnapshot.Request.class);
        verify(portal).read(capture.capture());
        assertEquals("dw:line_items",capture.getValue().row().storeKey());
        assertEquals(JsonUtils.getObjectMapper().readTree("{\"region\":\"HK\",\"number\":7}"),capture.getValue().row().key());
        verifyNoInteractions(runtime);
    }
    private void snapshot(AssignmentSourceSnapshot.State state,Map<String,Object> fields,long revision) {
        when(portal.read(any())).thenReturn(new AssignmentSourceSnapshot(1,caseId,"instance",revision,2,state,JsonUtils.getObjectMapper().valueToTree(fields)));
    }
}
