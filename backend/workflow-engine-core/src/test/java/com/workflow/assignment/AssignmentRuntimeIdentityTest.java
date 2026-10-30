package com.workflow.assignment;

import com.workflow.util.CallActivityDataMappingCompiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** G3 probes the actual compiler/engine scopes rather than inferring identity from task IDs. */
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentRuntimeIdentityTest extends AssignmentPostgresFixture {
    @BeforeAll
    void deployModels() {
        openEngine();
        var repository = engine.getRepositoryService();
        repository.createDeployment().addString("child.bpmn20.xml", child()).deploy();
        repository.createDeployment().addString("parent.bpmn20.xml",
                CallActivityDataMappingCompiler.compile(parent())).deploy();
        repository.createDeployment().addString("nested.bpmn20.xml", nested()).deploy();
    }

    @Test
    void rowCallsExposeStableRowsAndVerifiedParentBeforeAnyPortalMirror() {
        var runtime = engine.getRuntimeService();
        var root = runtime.startProcessInstanceByKey("assignment_p0_parent", Map.of(
                "assignmentCase", "root-a", "originalInitiator", "actor-a",
                "__subTables__", Map.of("dw:arbitrary_lines", List.of(
                        Map.of("platformRowUuid", "row-a", "custom_key", "A"),
                        Map.of("platformRowUuid", "row-b", "custom_key", "B")))));
        var children = runtime.createProcessInstanceQuery().superProcessInstanceId(root.getId()).list();
        assertEquals(2, children.size());
        var identities = new java.util.HashSet<String>();
        for (var child : children) {
            var task = engine.getTaskService().createTaskQuery().processInstanceId(child.getId()).singleResult();
            var row = (Map<?, ?>) runtime.getVariable(task.getExecutionId(), "__callRow");
            assertNotNull(row, "actual compiler must pass the call's row to its child");
            identities.add((String) row.get("platformRowUuid"));
            assertEquals("root-a", runtime.getVariable(task.getExecutionId(), "assignmentCase"));
            assertEquals("actor-a", runtime.getVariable(task.getExecutionId(), "originalInitiator"));
        }
        assertEquals(Set.of("row-a", "row-b"), identities);
    }

    @Test
    void nestedMiRetainsEachAncestorItemWithoutUsingArrayOffsets() {
        var runtime = engine.getRuntimeService();
        var root = runtime.startProcessInstanceByKey("assignment_p0_nested", Map.of("outerItems", List.of(
                Map.of("platformRowUuid", "outer-a", "entries", List.of(Map.of("part", "P", "sequence", 1))),
                Map.of("platformRowUuid", "outer-b", "entries", List.of(Map.of("part", "P", "sequence", 1))))));
        var tasks = engine.getTaskService().createTaskQuery().processInstanceId(root.getId()).list();
        assertEquals(2, tasks.size());
        var parents = new java.util.HashSet<String>();
        for (var task : tasks) {
            Map<?, ?> outer = (Map<?, ?>) runtime.getVariable(task.getExecutionId(), "outerItem");
            Map<?, ?> inner = (Map<?, ?>) runtime.getVariable(task.getExecutionId(), "innerItem");
            assertEquals(Map.of("part", "P", "sequence", 1), inner);
            parents.add((String) outer.get("platformRowUuid"));
            var scopes = new ArrayList<String>();
            String execution = task.getExecutionId();
            while (execution != null) {
                var current = runtime.createExecutionQuery().executionId(execution).singleResult();
                if (runtime.hasVariableLocal(execution, "outerItem")) scopes.add("outer");
                if (runtime.hasVariableLocal(execution, "innerItem")) scopes.add("inner");
                execution = current.getParentId();
            }
            assertTrue(scopes.containsAll(List.of("outer", "inner")));
        }
        assertEquals(Set.of("outer-a", "outer-b"), parents);
    }

    private static String child() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="assignment-p0">
                  <process id="assignment_p0_child" isExecutable="true">
                    <startEvent id="start"/><userTask id="work"/><endEvent id="end"/>
                    <sequenceFlow id="s1" sourceRef="start" targetRef="work"/>
                    <sequenceFlow id="s2" sourceRef="work" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }

    private static String parent() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" xmlns:custom="http://workflow/custom" targetNamespace="assignment-p0">
                  <process id="assignment_p0_parent" isExecutable="true">
                    <startEvent id="start"/>
                    <callActivity id="call" calledElement="assignment_p0_child">
                      <extensionElements><custom:properties><custom:property name="callRowsTable" value="arbitrary_lines"/>
                      </custom:properties><flowable:in source="assignmentCase" target="assignmentCase"/>
                      <flowable:in source="originalInitiator" target="originalInitiator"/></extensionElements>
                      <multiInstanceLoopCharacteristics isSequential="false"/>
                    </callActivity>
                    <endEvent id="end"/>
                    <sequenceFlow id="s1" sourceRef="start" targetRef="call"/>
                    <sequenceFlow id="s2" sourceRef="call" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }

    private static String nested() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:flowable="http://flowable.org/bpmn" targetNamespace="assignment-p0">
                  <process id="assignment_p0_nested" isExecutable="true">
                    <startEvent id="start"/>
                    <subProcess id="outer">
                      <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="outerItems" flowable:elementVariable="outerItem"/>
                      <startEvent id="inside"/>
                      <userTask id="inner">
                        <multiInstanceLoopCharacteristics isSequential="false" flowable:collection="${outerItem.entries}" flowable:elementVariable="innerItem"/>
                      </userTask>
                      <endEvent id="insideEnd"/>
                      <sequenceFlow id="i1" sourceRef="inside" targetRef="inner"/>
                      <sequenceFlow id="i2" sourceRef="inner" targetRef="insideEnd"/>
                    </subProcess>
                    <endEvent id="end"/>
                    <sequenceFlow id="s1" sourceRef="start" targetRef="outer"/>
                    <sequenceFlow id="s2" sourceRef="outer" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }
}
