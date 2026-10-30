package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentWriteDecision;
import com.workflow.assignment.impl.AssignmentWriteDecisionComponentImpl;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentWriteDecisionDatabaseTest extends AssignmentPostgresFixture {
    final TaskService tasks=mock(TaskService.class);
    final AssignmentCaseRegistry registry=mock(AssignmentCaseRegistry.class);
    final AssignmentWriteGuard guard=mock(AssignmentWriteGuard.class);
    AssignmentWriteDecisionComponent component;
    @BeforeEach void configure() {
        reset(tasks,registry,guard);
        var query=mock(TaskQuery.class); var task=mock(Task.class);
        when(tasks.createTaskQuery()).thenReturn(query); when(query.taskId("task")).thenReturn(query); when(query.singleResult()).thenReturn(task);
        when(task.getProcessInstanceId()).thenReturn("actual-instance");
        when(registry.instance("actual-instance")).thenReturn(Optional.empty());
        component=new AssignmentWriteDecisionComponentImpl(tasks,registry,guard,mock(AssignmentLegacyTaskCheck.class),transaction.getTransactionManager());
    }
    @Test void taskOnAnotherInstanceCannotAuthorizeEvenALegacyLocalWrite() {
        assertThrows(AssignmentFailure.class,()->component.prepare(request("other-instance")));
        verifyNoInteractions(registry,guard);
    }
    @Test void knownLegacyIsAnExplicitBoundDecisionAndDoesNotInferUserPermission() {
        var packet=component.prepare(request("actual-instance"));
        assertFalse(packet.tracked()); assertEquals("actual-instance",packet.instanceId()); assertEquals("a".repeat(64),packet.requestDigest());
        verifyNoInteractions(guard);
    }
    @Test void unavailableTaskAndAnOuterTransactionNeverProduceALegacyPacket() {
        assertThrows(AssignmentFailure.class,()->transaction.execute(status->component.prepare(request("actual-instance"))));
        var query=tasks.createTaskQuery().taskId("task"); when(query.singleResult()).thenReturn(null);
        assertThrows(AssignmentFailure.class,()->component.prepare(request("actual-instance")));
        verifyNoInteractions(registry,guard);
    }
    private AssignmentWriteDecision.Request request(String instance) { return new AssignmentWriteDecision.Request("task","actor",null,"SAVE","a".repeat(64),instance); }
}
