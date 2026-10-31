package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RuntimeService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentPresentationSourcesTest {
    final RuntimeService runtime=mock(RuntimeService.class);
    final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    final AssignmentSourceSnapshotClient portal=mock(AssignmentSourceSnapshotClient.class);
    final AssignmentSourceValues source=new AssignmentSourceValues(runtime,definitions,states,portal);
    final UUID caseId=UUID.randomUUID();
    @BeforeEach void epoch() {
        when(states.caseContext(caseId)).thenReturn(new AssignmentTaskStateRepository.CaseContext("origin","BU",3,0));
        when(states.portalRevision(caseId)).thenReturn(2L);
    }
    @Test void manualSourcesShareOneRpcAndExposeOnlyConfiguredFields() {
        var selected=new LinkedHashMap<Task,AssignmentTaskStateRepository.State>();
        selected.put(task("first"),state("first",AssignmentSourceSpec.manual(null,null,null)));
        selected.put(task("second"),state("second",AssignmentSourceSpec.manual(null,null,null)));
        ready(Map.of("manualAssignBu_work","CURRENT_BU","manualAssignRole_work","ROLE","private","hidden"));
        var result=source.presentationSources(selected);
        assertEquals(Set.of("first","second"),result.keySet());
        assertEquals(Map.of("manualAssignBu_work","CURRENT_BU","manualAssignRole_work","ROLE"),result.get("first"));
        verify(portal,times(1)).readMany(argThat(requests->requests.size()==2 && requests.stream().allMatch(request->
                request.row()==null && request.fields().equals(List.of("manualAssignee_work","manualAssignBu_work","manualAssignRole_work")))));
        verify(states,times(2)).caseContext(caseId); verify(states,times(2)).portalRevision(caseId);
        verifyNoInteractions(runtime);
    }
    @Test void rowSourcesUseTheSameFrozenSelectorAndKeepAllRoleValues() {
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("process"); model.addProcess(process);
        var work=new UserTask(); work.setId("work"); work.setLoopCharacteristics(new MultiInstanceLoopCharacteristics()); process.addFlowElement(work);
        var properties=new ExtensionElement(); properties.setName("properties");
        var property=new ExtensionElement(); property.setName("property");
        var name=new ExtensionAttribute(); name.setName("name"); name.setValue("subTableName"); property.addAttribute(name);
        var value=new ExtensionAttribute(); value.setName("value"); value.setValue("items"); property.addAttribute(value);
        properties.addChildElement(property); work.addExtensionElement(properties);
        when(definitions.model("frozen")).thenReturn(model);
        var key=Map.of("tenant","HK","flag",false);
        when(states.position("row")).thenReturn(JsonUtils.getObjectMapper().valueToTree(
                Map.of("multiInstances",List.of(Map.of("scope","work","row",key)))));
        ready(Map.of("bu","ROW_BU","roles",List.of("role-a","role-b")));
        var result=source.presentationSources(Map.of(task("row"),state("row",AssignmentSourceSpec.row("owner","roles","bu"))));
        assertEquals(List.of("role-a","role-b"),result.get("row").get("roles"));
        verify(portal).readMany(argThat(requests->requests.size()==1 && requests.get(0).row().storeKey().equals("dw:items")
                && requests.get(0).row().key().equals(JsonUtils.getObjectMapper().valueToTree(key))));
    }
    @Test void aCaseChangedDuringTheBatchRejectsTheEntirePresentation() {
        ready(Map.of("manualAssignBu_work","BU"));
        when(states.caseContext(caseId)).thenReturn(new AssignmentTaskStateRepository.CaseContext("origin","BU",3,0),
                new AssignmentTaskStateRepository.CaseContext("origin","BU",4,0));
        var failure=assertThrows(AssignmentFailure.class,()->source.presentationSources(
                Map.of(task("task"),state("task",AssignmentSourceSpec.manual(null,null,null)))));
        assertEquals("ASSIGNMENT_STATE_CHANGED",failure.getDetails().get("reason"));
    }
    @Test void initialSnapshotUsesRuntimeOnlyForTheRequestedSourceFields() {
        when(portal.readMany(anyList())).thenAnswer(call->{
            List<AssignmentSourceSnapshot.Request> requests=call.getArgument(0);
            return requests.stream().map(request->new AssignmentSourceSnapshot(1,caseId,"instance",3,2,
                    AssignmentSourceSnapshot.State.INITIAL,JsonUtils.getObjectMapper().createObjectNode())).toList();
        });
        when(runtime.getVariables("instance")).thenReturn(Map.of("manualAssignBu_work","INITIAL_BU","private","hidden"));
        var result=source.presentationSources(Map.of(task("task"),state("task",AssignmentSourceSpec.manual(null,null,null))));
        assertEquals(Map.of("manualAssignBu_work","INITIAL_BU"),result.get("task"));
    }
    @Test void aResponseFromAnotherPortalRevisionIsRejectedBeforeRuntimeFallback() {
        when(portal.readMany(anyList())).thenReturn(List.of(new AssignmentSourceSnapshot(1,caseId,"instance",3,9,
                AssignmentSourceSnapshot.State.INITIAL,JsonUtils.getObjectMapper().createObjectNode())));
        assertThrows(AssignmentFailure.class,()->source.presentationSources(
                Map.of(task("task"),state("task",AssignmentSourceSpec.manual(null,null,null)))));
        verifyNoInteractions(runtime);
    }
    private void ready(Map<String,Object> values) {
        when(portal.readMany(anyList())).thenAnswer(call->{
            List<AssignmentSourceSnapshot.Request> requests=call.getArgument(0);
            return requests.stream().map(request->new AssignmentSourceSnapshot(1,request.caseId(),request.instanceId(),3,2,
                    AssignmentSourceSnapshot.State.READY,JsonUtils.getObjectMapper().valueToTree(values))).toList();
        });
    }
    private Task task(String id) {
        var task=mock(Task.class); when(task.getId()).thenReturn(id); when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("frozen"); when(task.getTaskDefinitionKey()).thenReturn("work"); return task;
    }
    private AssignmentTaskStateRepository.State state(String id,AssignmentSourceSpec spec) {
        var state=mock(AssignmentTaskStateRepository.State.class); when(state.taskId()).thenReturn(id); when(state.active()).thenReturn(true);
        when(state.instance()).thenReturn("instance"); when(state.definition()).thenReturn("frozen"); when(state.node()).thenReturn("work");
        when(state.caseId()).thenReturn(caseId); when(state.sourceSpec()).thenReturn(spec); return state;
    }
}
