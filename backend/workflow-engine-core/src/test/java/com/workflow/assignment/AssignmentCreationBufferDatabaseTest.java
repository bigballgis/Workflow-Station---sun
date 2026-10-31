package com.workflow.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentCreationBufferDatabaseTest extends AssignmentPostgresFixture {
    @Test void excludedRecipientsAreNeverPassedToTheNotificationPublisher() {
        var allowed = new AtomicInteger();
        var denied = new AtomicInteger();
        transaction.executeWithoutResult(status -> {
            AssignmentCreationBuffer.begin("task");
            assertTrue(AssignmentCreationBuffer.defer("task", "allowed", allowed::incrementAndGet));
            assertTrue(AssignmentCreationBuffer.defer("task", "excluded", denied::incrementAndGet));
            assertEquals(0, allowed.get());
            AssignmentCreationBuffer.finish("task", Set.of("allowed"));
        });
        assertEquals(1, allowed.get());
        assertEquals(0, denied.get());
        assertFalse(AssignmentCreationBuffer.active());
    }

    @Test void failedCreationDoesNotLeakItsBufferIntoAnotherRequest() {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            AssignmentCreationBuffer.begin("failed");
            throw new IllegalStateException("synthetic listener failure");
        }));
        assertFalse(AssignmentCreationBuffer.active());
        assertFalse(AssignmentCreationBuffer.defer("failed", "actor", () -> fail("must not run")));
        transaction.executeWithoutResult(status -> {
            AssignmentCreationBuffer.begin("fresh");
            AssignmentCreationBuffer.finish("fresh", Set.of());
        });
    }

    @Test void nestedTasksKeepTheirOwnNotificationAndSourceScopes() {
        transaction.executeWithoutResult(status -> {
            AssignmentCreationBuffer.begin("parent");
            AssignmentCreationBuffer.record(AssignmentSourceSpec.row("configured_user", "configured_role", "configured_bu"));
            AssignmentCreationBuffer.record(AssignmentSourceSpec.resolver(new AssignmentSourceSpec.Resolver("BU_ROLE", List.of("r"), "b", "i", null, null)));
            AssignmentCreationBuffer.begin("child");
            AssignmentCreationBuffer.record(AssignmentSourceSpec.variable("selectedUsers"));
            assertEquals(AssignmentSourceSpec.Kind.VARIABLE, AssignmentCreationBuffer.source("child", List.of(), true).kind());
            AssignmentCreationBuffer.finish("child", Set.of());
            assertEquals(AssignmentSourceSpec.Kind.ROW, AssignmentCreationBuffer.source("parent", List.of(), false).kind());
            AssignmentCreationBuffer.finish("parent", Set.of());
        });
    }

    @Test void independentRecoveryTransactionCannotConsumeItsSuspendedParentsScope() {
        var independent = new org.springframework.transaction.support.TransactionTemplate(transaction.getTransactionManager());
        independent.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status -> {
            AssignmentCreationBuffer.begin("outer");
            independent.executeWithoutResult(inner -> {
                assertFalse(AssignmentCreationBuffer.active());
                AssignmentCreationBuffer.begin("independent");
                AssignmentCreationBuffer.finish("independent", Set.of());
            });
            assertTrue(AssignmentCreationBuffer.active());
            AssignmentCreationBuffer.finish("outer", Set.of());
        });
    }
}
