package com.workflow.assignment;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentWorkflowValuesDatabaseTest extends AssignmentPostgresFixture {
    @BeforeAll void deploy() {
        openEngine();
        engine.getRepositoryService().createDeployment().addString("archive-view.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="archive">
                <process id="archiveView" isExecutable="true"><startEvent id="start"/><userTask id="review"/><endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="review"/><sequenceFlow id="b" sourceRef="review" targetRef="end"/>
                </process></definitions>
                """).deploy();
    }
    @Test void runtimeAndEndedViewsOmitArchivesWithoutRemovingStoredSnapshotOrBusinessMaps() {
        var runtime=engine.getRuntimeService();var history=engine.getHistoryService();
        var business=Map.of("dw:renamed",java.util.List.of(Map.of("compoundA","A","compoundB",3)));
        var archive=Map.of("caption","old snapshot","rows",java.util.List.of(Map.of("id","original")));
        var process=runtime.startProcessInstanceByKey("archiveView",Map.of("__subTables__",business,"memo","current","_snapshot_old",archive));
        String id=process.getId();
        assertEquals(Map.of("__subTables__",business,"memo","current"),AssignmentWorkflowValues.runtime(runtime,Set.of(id)).get(id));
        assertEquals(archive,runtime.getVariable(id,"_snapshot_old"));
        var task=engine.getTaskService().createTaskQuery().processInstanceId(id).singleResult();
        engine.getTaskService().complete(task.getId());
        assertEquals(Map.of("__subTables__",business,"memo","current"),AssignmentWorkflowValues.historic(history,Set.of(id)).get(id));
        assertEquals(archive,history.createHistoricVariableInstanceQuery().processInstanceId(id).variableName("_snapshot_old").singleResult().getValue());
    }
}
