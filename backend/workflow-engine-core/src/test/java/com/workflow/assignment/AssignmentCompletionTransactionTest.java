package com.workflow.assignment;

import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** G1: real Flowable agenda and Spring/JDBC transaction, without a mocked engine. */
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentCompletionTransactionTest extends AssignmentPostgresFixture {
    final List<String> events = new ArrayList<>();
    final List<Integer> connections = new ArrayList<>();
    boolean failNextCreation;
    String operationId;

    @BeforeAll
    void configureEngine() {
        openEngine();
        engine.getRuntimeService().addEventListener(new CompletionProbe(),
                FlowableEngineEventType.TASK_CREATED, FlowableEngineEventType.TASK_COMPLETED);
        engine.getRepositoryService().createDeployment().addString("serial.bpmn20.xml", model()).deploy();
    }

    @BeforeEach
    void clearProbe() {
        jdbc.execute("TRUNCATE p0_responsibility,p0_operation,p0_sync,p0_saved");
        events.clear();
        connections.clear();
        failNextCreation = false;
        operationId = null;
    }

    @Test
    void completionIsVisibleBeforeTheSynchronousSuccessorCreation() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial");
        var first = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        events.clear();
        complete(first.getId());
        assertEquals(List.of("completed:first", "created:second:1"), events);
        assertEquals(1, connections.stream().distinct().count(), "listener SQL must share the Flowable transaction");
        assertEquals("actor-a", jdbc.queryForObject("SELECT actor FROM p0_responsibility WHERE task_id=?",
                String.class, first.getId()));
        assertEquals("second", engine.getTaskService().createTaskQuery()
                .processInstanceId(instance.getId()).singleResult().getTaskDefinitionKey());
    }

    @Test
    void failedSuccessorCreationRollsBackTaskAndResponsibilityTogether() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial");
        var first = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        failNextCreation = true;
        assertThrows(RuntimeException.class, () -> complete(first.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_responsibility WHERE task_id=?",
                Integer.class, first.getId()));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(first.getId()).singleResult());
        assertEquals(0, engine.getTaskService().createTaskQuery().processInstanceId(instance.getId())
                .taskDefinitionKey("second").count());
    }

    @Test
    void firstTaskSeesRootContextWithoutAnyPortalMirror() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial",
                Map.of("assignmentCase", "root-case", "originalInitiator", "actor-a"));
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertEquals("root-case", engine.getTaskService().getVariable(task.getId(), "assignmentCase"));
        assertEquals("actor-a", engine.getTaskService().getVariable(task.getId(), "originalInitiator"));
    }

    @Test
    void acceptedOperationSurvivesLostResponseAndCanBeAppliedExactlyOnce() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial");
        var first = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        operationId = "operation-" + first.getId();
        complete(first.getId()); // Intentionally discard the completion response.
        assertEquals(first.getId(), jdbc.queryForObject("SELECT task_id FROM p0_operation WHERE id=?",
                String.class, operationId));
        applyAcceptedEffect(operationId);
        applyAcceptedEffect(operationId); // The confirmation response may also be lost.
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM p0_sync WHERE id=?", Integer.class, operationId));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM p0_saved WHERE id=?", Integer.class, operationId));
        assertNull(engine.getTaskService().createTaskQuery().taskId(first.getId()).singleResult());
    }

    @Test
    void failedEngineCompletionDoesNotLeaveASuccessfulOperationReceipt() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial");
        var first = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        operationId = "operation-" + first.getId();
        failNextCreation = true;
        assertThrows(RuntimeException.class, () -> complete(first.getId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_operation WHERE id=?", Integer.class, operationId));
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(first.getId()).singleResult());
    }

    @Test
    void portalEffectAndItsIdempotencyLedgerRollbackTogether() {
        String id = "rolled-back-effect";
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO p0_saved VALUES(?,?)", id, "accepted-effect");
            jdbc.update("INSERT INTO p0_sync VALUES(?,?)", id, "digest");
            throw new IllegalStateException("synthetic portal write failure");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_saved WHERE id=?", Integer.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_sync WHERE id=?", Integer.class, id));
    }

    private void applyAcceptedEffect(String id) {
        transaction.executeWithoutResult(status -> {
            lockCase("case-a");
            if (jdbc.queryForObject("SELECT count(*) FROM p0_sync WHERE id=?", Integer.class, id) == 0) {
                String digest = jdbc.queryForObject("SELECT digest FROM p0_operation WHERE id=?", String.class, id);
                jdbc.update("INSERT INTO p0_saved VALUES(?,?)", id, "accepted-effect");
                jdbc.update("INSERT INTO p0_sync VALUES(?,?)", id, digest);
            }
        });
    }

    @Test
    void releasedOwnershipPersistsWhenALaterForbiddenRequestRollsBack() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        engine.getTaskService().setAssignee(task.getId(), "actor-a");
        transaction.executeWithoutResult(status -> engine.getTaskService().unclaim(task.getId()));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO p0_saved VALUES('forbidden','payload')");
            throw new IllegalStateException("ASSIGNMENT_EXCLUDED");
        }));
        assertNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult().getAssignee());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_saved", Integer.class));
    }

    @Test
    void candidateProjectionCanRemoveAnExcludedUserWithoutDiscardingTheTask() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_p0_serial");
        var task = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        transaction.executeWithoutResult(status -> {
            engine.getTaskService().addCandidateUser(task.getId(), "actor-a");
            engine.getTaskService().addCandidateUser(task.getId(), "actor-b");
            engine.getTaskService().deleteCandidateUser(task.getId(), "actor-a");
        });
        assertEquals(0, engine.getTaskService().createTaskQuery().taskId(task.getId()).taskCandidateUser("actor-a").count());
        assertEquals(1, engine.getTaskService().createTaskQuery().taskId(task.getId()).taskCandidateUser("actor-b").count());
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(task.getId()).singleResult());
    }

    private void complete(String id) {
        String previous = Authentication.getAuthenticatedUserId();
        try {
            Authentication.setAuthenticatedUserId("actor-a");
            engine.getTaskService().complete(id);
        } finally {
            Authentication.setAuthenticatedUserId(previous);
        }
    }

    private class CompletionProbe implements FlowableEventListener {
        @Override
        public void onEvent(FlowableEvent event) {
            if (!(event instanceof FlowableEntityEvent entity) || !(entity.getEntity() instanceof Task task)) return;
            if (event.getType() == FlowableEngineEventType.TASK_COMPLETED) {
                connections.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                jdbc.update("INSERT INTO p0_responsibility VALUES(?,?)", task.getId(), Authentication.getAuthenticatedUserId());
                if (operationId != null) jdbc.update("INSERT INTO p0_operation VALUES(?,?,?)", operationId, task.getId(), "digest");
                events.add("completed:" + task.getTaskDefinitionKey());
            } else if ("second".equals(task.getTaskDefinitionKey())) {
                connections.add(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                int count = jdbc.queryForObject("SELECT count(*) FROM p0_responsibility", Integer.class);
                events.add("created:second:" + count);
                if (failNextCreation) throw new IllegalStateException("synthetic successor failure");
            }
        }
        @Override public boolean isFailOnException() { return true; }
        @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
        @Override public String getOnTransaction() { return null; }
    }

    private static String model() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="assignment-p0">
                  <process id="assignment_p0_serial" isExecutable="true">
                    <startEvent id="start"/><userTask id="first"/><userTask id="second"/><endEvent id="end"/>
                    <sequenceFlow id="s1" sourceRef="start" targetRef="first"/>
                    <sequenceFlow id="s2" sourceRef="first" targetRef="second"/>
                    <sequenceFlow id="s3" sourceRef="second" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }
}
