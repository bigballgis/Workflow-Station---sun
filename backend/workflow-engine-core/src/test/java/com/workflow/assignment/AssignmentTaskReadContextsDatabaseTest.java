package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentTaskReadContextsDatabaseTest extends AssignmentPostgresFixture {
    AssignmentTaskReadContexts contexts;
    AssignmentReadContextClassifier classifier;
    AssignmentCaseRepository cases;
    CountingJdbc reads;
    String legacy,tracked,parent;
    @BeforeAll void prepare() {
        openEngine(); reads=new CountingJdbc(source);
        contexts=new AssignmentTaskReadContexts(reads,engine.getManagementService());
        classifier=new AssignmentReadContextClassifier(new AssignmentDefinitionPolicy(engine.getRepositoryService()));
        cases=new AssignmentCaseRepository(jdbc,new ObjectMapper());
        legacy=deploy("batch_read_legacy",false,"<userTask id=\"work\"/>");
        tracked=deploy("batch_read_tracked",true,"<userTask id=\"work\"/>");
        parent=deploy("batch_read_parent",false,"<callActivity id=\"work\" calledElement=\""+legacy+"\" flowable:calledElementType=\"id\"/>");
    }
    @Test void aHundredUnrelatedLegacyRootsUseOneContextStatement() {
        var ids=transaction.execute(status->{
            var tasks=new ArrayList<String>();
            for (int i=0;i<100;i++) {
                var process=engine.getRuntimeService().startProcessInstanceById(legacy);
                tasks.add(engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult().getId());
            }
            return tasks;
        });
        reads.statements=0;
        var decisions=classifier.classify(contexts.read(ids));
        assertEquals(100,decisions.size()); assertTrue(decisions.values().stream().allMatch(value->value==AssignmentReadContextClassifier.Kind.LEGACY));
        assertEquals(1,reads.statements,"Caller ancestry must not query once per task");
    }
    @Test void aMissingTaskStateDoesNotEraseTrackedDefinitionContext() {
        var process=engine.getRuntimeService().startProcessInstanceById(tracked);
        String task=engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult().getId();
        assertEquals(AssignmentReadContextClassifier.Kind.TRACKED,classifier.classify(contexts.read(List.of(task))).get(task));
    }
    @Test void anUntrackedChildDefinitionInheritsItsRealTrackedCallerContext() {
        var root=engine.getRuntimeService().startProcessInstanceById(parent);
        var child=engine.getRuntimeService().createProcessInstanceQuery().superProcessInstanceId(root.getId()).singleResult();
        String task=engine.getTaskService().createTaskQuery().processInstanceId(child.getId()).singleResult().getId();
        var before=contexts.read(List.of(task));
        assertEquals(2,before.get(task).ancestors().size());
        assertEquals(AssignmentReadContextClassifier.Kind.LEGACY,classifier.classify(before).get(task));
        UUID caseId=UUID.randomUUID(); cases.createCase(caseId,root.getId(),"original","BU");
        cases.insert(new AssignmentCaseRegistry.Registration(root.getId(),caseId,UUID.randomUUID(),parent,List.of()),null,null,"synthetic");
        assertEquals(AssignmentReadContextClassifier.Kind.TRACKED,classifier.classify(contexts.read(List.of(task))).get(task));
    }
    @Test void absentTasksAreNotFabricatedAndIndependentTasksHaveExplicitLegacyProof() {
        var task=engine.getTaskService().newTask(); engine.getTaskService().saveTask(task);
        var found=contexts.read(List.of(task.getId(),"absent-task"));
        assertEquals(Set.of(task.getId()),found.keySet());
        assertEquals(AssignmentReadContextClassifier.Kind.LEGACY,classifier.classify(found).get(task.getId()));
    }
    @Test void duplicateEmptyAndOversizedBatchesFailBeforeAnyQuery() {
        reads.statements=0;
        assertThrows(IllegalArgumentException.class,()->contexts.read(List.of()));
        assertThrows(IllegalArgumentException.class,()->contexts.read(List.of("same","same")));
        assertThrows(IllegalArgumentException.class,()->contexts.read(java.util.stream.IntStream.range(0,1001).mapToObj(Integer::toString).toList()));
        assertEquals(0,reads.statements);
    }
    private String deploy(String key,boolean compiled,String work) {
        String metadata=compiled?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/><flowable:property name="assignmentRuntimeUsers" value="{}"/>
                </flowable:properties></extensionElements>
                """:"";
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="%s" isExecutable="true">%s<startEvent id="start"/>%s<sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """.formatted(key,metadata,work)).deploy();
        return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
    static class CountingJdbc extends JdbcTemplate {
        int statements;
        CountingJdbc(javax.sql.DataSource source) { super(source); }
        @Override public <T> List<T> query(PreparedStatementCreator query,RowMapper<T> mapper) {
            statements++; return super.query(query,mapper);
        }
    }
}
