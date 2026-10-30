package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.flowable.bpmn.model.*;
import org.flowable.engine.RepositoryService;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssignmentOutcomeWorkTest {
    @Test void identicalNodeIdsInTwoUnitsUseTheirOwnFrozenMiAndCallPositions() {
        var definitions=mock(RepositoryService.class);
        var call=new CallActivity(); call.setId("work"); call.setName("Parent rows"); call.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        when(definitions.getBpmnModel("frozen-A")).thenReturn(model("parent",call));
        var outer=new SubProcess(); outer.setId("mi"); outer.setName("Child rows"); outer.setLoopCharacteristics(new MultiInstanceLoopCharacteristics());
        var node=new UserTask(); node.setId("work"); node.setName("Review"); outer.addFlowElement(node); node.setParentContainer(outer);
        when(definitions.getBpmnModel("frozen-B")).thenReturn(model("child",outer));
        var parent=position("parent","frozen-A",null,null); var child=position("child","frozen-B","parent","work");
        var work=new AssignmentOutcomeWork(Map.of("parent",parent,"child",child),List.of(task("task","child","work")),definitions);
        assertEquals("Parent rows",work.project(parent).get(0).ownerOuterStep());
        assertEquals("Child rows",work.project(child).get(0).ownerOuterStep());
        assertEquals("child",work.project(parent).get(0).instanceId());
        work.project(parent); work.project(child);
        verify(definitions,times(1)).getBpmnModel("frozen-A"); verify(definitions,times(1)).getBpmnModel("frozen-B");
    }
    @Test void anOwnActiveTaskTakesPrecedenceOverCalledWork() {
        var definitions=mock(RepositoryService.class); var node=new UserTask(); node.setId("own");
        when(definitions.getBpmnModel("frozen-A")).thenReturn(model("parent",node));
        var parent=position("parent","frozen-A",null,null); var child=position("child","frozen-B","parent","call");
        var work=new AssignmentOutcomeWork(Map.of("parent",parent,"child",child),List.of(task("own-task","parent","own"),task("child-task","child","work")),definitions);
        var projected=work.project(parent);
        assertEquals(1,projected.size()); assertEquals("own-task",projected.get(0).id()); assertNull(projected.get(0).ownerOuterStep());
    }
    @Test void missingOrCyclicCallPositionsNeverBecomeAnEmptySuccessfulProjection() {
        var definitions=mock(RepositoryService.class); var parent=position("parent","frozen-A",null,null);
        var missing=new AssignmentOutcomeWork(Map.of("parent",parent),List.of(task("task","unknown","work")),definitions);
        assertThrows(AssignmentFailure.class,()->missing.project(parent));
        var cycle=position("child","frozen-B","child","call");
        var cyclic=new AssignmentOutcomeWork(Map.of("parent",parent,"child",cycle),List.of(task("task","child","work")),definitions);
        assertThrows(AssignmentFailure.class,()->cyclic.project(parent));
    }
    private AssignmentTaskStateRepository.RoundInstance position(String instance,String definition,String parent,String call) {
        return new AssignmentTaskStateRepository.RoundInstance(instance,definition,parent,call,"ACTIVE");
    }
    private Task task(String id,String process,String node) {
        var task=mock(Task.class); when(task.getId()).thenReturn(id); when(task.getProcessInstanceId()).thenReturn(process);
        when(task.getTaskDefinitionKey()).thenReturn(node); doReturn(List.of()).when(task).getIdentityLinks(); return task;
    }
    private BpmnModel model(String key,FlowElement node) {
        var model=new BpmnModel(); var process=new org.flowable.bpmn.model.Process(); process.setId(key);
        process.addFlowElement(node); node.setParentContainer(process); model.addProcess(process); return model;
    }
}
