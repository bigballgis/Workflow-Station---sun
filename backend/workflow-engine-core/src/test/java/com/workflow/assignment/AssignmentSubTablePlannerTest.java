package com.workflow.assignment;

import com.platform.common.assignment.*;
import org.flowable.engine.TaskService;
import org.flowable.bpmn.model.*;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentSubTablePlannerTest {
    final TaskService tasks=mock(TaskService.class);
    final AssignmentTaskStateRepository states=mock(AssignmentTaskStateRepository.class);
    final AssignmentLegacyTaskCheck legacy=mock(AssignmentLegacyTaskCheck.class);
    final AssignmentDefinitionPolicy definitions=mock(AssignmentDefinitionPolicy.class);
    final AssignmentIdentityClient identities=mock(AssignmentIdentityClient.class);
    final AssignmentSubTablePlanner planner=new AssignmentSubTablePlanner(tasks,states,legacy,definitions,identities);
    final Task task=mock(Task.class);
    final UserTask node=new UserTask();
    final ExtensionElement properties=new ExtensionElement();
    @BeforeEach void setup() {
        var query=mock(TaskQuery.class,RETURNS_SELF); when(tasks.createTaskQuery()).thenReturn(query); when(query.singleResult()).thenReturn(task);
        when(task.getId()).thenReturn("task"); when(task.getProcessInstanceId()).thenReturn("instance");
        when(task.getProcessDefinitionId()).thenReturn("frozen"); when(task.getTaskDefinitionKey()).thenReturn("node");
        var state=mock(AssignmentTaskStateRepository.State.class); when(state.active()).thenReturn(true);
        when(state.instance()).thenReturn("instance"); when(state.definition()).thenReturn("frozen"); when(states.find("task")).thenReturn(Optional.of(state));
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId("synthetic"); model.addProcess(process);
        node.setId("node"); process.addFlowElement(node); when(definitions.model("frozen")).thenReturn(model);
        properties.setName("properties"); node.addExtensionElement(properties);
        property("subTableName","renamed_rows"); property("assigneeField","renamed_reviewer");
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(8,Map.of("target",new AssignmentIdentitySnapshot.User("target","login",true)),Set.of()));
    }
    @Test void frozenConfigurationAndExactActiveTargetAreReturnedWithoutCurrentPreTaskPoolRestriction() {
        var plan=planner.prepare(request()); assertTrue(plan.tracked()); assertEquals("renamed_rows",plan.table());
        assertEquals("renamed_reviewer",plan.assigneeField()); assertEquals("target",plan.target()); assertEquals(8,plan.authorityRevision());
        verifyNoInteractions(legacy);
    }
    @Test void deletedProjectionCannotBeAssumedLegacy() {
        when(states.find("task")).thenReturn(Optional.empty()); doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE)).when(legacy).requireLegacy(task);
        assertThrows(AssignmentFailure.class,()->planner.prepare(request())); verifyNoInteractions(identities);
    }
    @Test void positivelyLegacyTaskKeepsItsExistingPath() {
        when(states.find("task")).thenReturn(Optional.empty()); assertFalse(planner.prepare(request()).tracked()); verify(legacy).requireLegacy(task); verifyNoInteractions(identities);
    }
    @Test void inactiveTargetIsRejected() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(8,Map.of("target",new AssignmentIdentitySnapshot.User("target","login",false)),Set.of()));
        assertThrows(AssignmentFailure.class,()->planner.prepare(request()));
    }
    @Test void aUsernameCannotBeSubstitutedForTheRequestedCanonicalId() {
        when(identities.resolve(anyList())).thenReturn(new AssignmentIdentitySnapshot(8,Map.of("target",new AssignmentIdentitySnapshot.User("different-id","target",true)),Set.of()));
        assertThrows(AssignmentFailure.class,()->planner.prepare(request()));
    }
    private AssignmentSubTablePlan.Request request() { return new AssignmentSubTablePlan.Request("task","target"); }
    private void property(String name,String value) {
        var property=new ExtensionElement(); property.setName("property");
        for (var pair:Map.of("name",name,"value",value).entrySet()) { var attribute=new ExtensionAttribute(); attribute.setName(pair.getKey()); attribute.setValue(pair.getValue()); property.addAttribute(attribute); }
        properties.addChildElement(property);
    }
}
