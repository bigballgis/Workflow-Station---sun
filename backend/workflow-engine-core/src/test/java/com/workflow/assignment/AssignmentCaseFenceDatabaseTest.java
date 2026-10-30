package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAuthorityFence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** G2/G6: independent database connections, version visibility and bounded lock contention. */
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentCaseFenceDatabaseTest extends AssignmentPostgresFixture {
    @BeforeEach
    void clearCases() {
        jdbc.execute("TRUNCATE p0_case,p0_saved,p0_responsibility");
        jdbc.update("INSERT INTO p0_case VALUES('case-a',0)");
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
    }

    @Test
    void stalePortalDecisionDoesNotCommitBusinessData() {
        long decision = revision();
        transaction.executeWithoutResult(status -> {
            lockCase("case-a");
            jdbc.update("UPDATE p0_case SET revision=revision+1 WHERE id='case-a'");
        });
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            lockCase("case-a");
            if (decision != revision()) throw new IllegalStateException("ASSIGNMENT_STATE_CHANGED");
            jdbc.update("INSERT INTO p0_saved VALUES('form','stale')");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_saved", Integer.class));
    }

    @Test
    void concurrentSameActorCompletionsAreSerializedAcrossConnections() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        var succeeded = new AtomicInteger();
        try {
            var operation = (java.util.concurrent.Callable<Void>) () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                transaction.executeWithoutResult(status -> {
                    lockCase("case-a");
                    if (revision() == 0) {
                        jdbc.update("UPDATE p0_case SET revision=1 WHERE id='case-a'");
                        succeeded.incrementAndGet();
                    }
                });
                return null;
            };
            var a = pool.submit(operation);
            var b = pool.submit(operation);
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
            assertEquals(1, succeeded.get());
            assertEquals(1, revision());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void authorityRevocationInvalidatesDecisionEvenWithoutCaseChange() {
        var authority = new AssignmentAuthorityFence(jdbc);
        long decision = authority.revision();
        transaction.executeWithoutResult(status -> {
            authority.beginChange();
        });
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            lockCase("case-a");
            long latest = authority.revision();
            if (decision != latest) {
                throw new IllegalStateException("ASSIGNMENT_STATE_CHANGED");
            }
            jdbc.update("INSERT INTO p0_saved VALUES('form','revoked')");
        }));
        assertEquals(0, revision());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM p0_saved", Integer.class));
    }

    @Test
    void rollbackReleasesCaseLockAndDoesNotAdvanceRevision() throws Exception {
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            lockCase("case-a");
            jdbc.update("UPDATE p0_case SET revision=1 WHERE id='case-a'");
            throw new IllegalStateException("synthetic rollback");
        }));
        var pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.execute("SET LOCAL lock_timeout='1s'");
                lockCase("case-a");
                assertEquals(0, revision());
            })).get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
    }

    private long revision() {
        return jdbc.queryForObject("SELECT revision FROM p0_case WHERE id='case-a'", Long.class);
    }

    @Test
    void locksRejectAnUnrelatedOrMissingTransactionInsteadOfLockingAnAutocommitConnection() {
        assertThrows(IllegalStateException.class, () -> lockCase("case-a"));
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, () -> lockCase("case-a"));
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
    }
}
