package com.workflow.component;

import com.workflow.repository.FlowableRuntimeTaskQueries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The engine's own moves of a task happen once, while the task still has the holder the pass read (real SQL). */
class TaskHandoverTest {

    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private TaskHandover handover;

    @BeforeEach
    void setUp() {
        database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("CREATE TABLE act_ru_task (id_ VARCHAR(64) PRIMARY KEY, assignee_ VARCHAR(255))");
        jdbc.update("INSERT INTO act_ru_task VALUES ('held', 'b')");
        jdbc.update("INSERT INTO act_ru_task VALUES ('pool', NULL)");
        handover = new TaskHandover(new FlowableRuntimeTaskQueries(jdbc), new DataSourceTransactionManager(database));
    }

    @AfterEach
    void tearDown() {
        database.shutdown();
    }

    @Test
    void theMoveRunsWhileTheTaskStillHasTheHolderThePassRead() {
        assertThat(handover.ifStillHeldBy("held", "b", () -> reassign("held", "m"))).isTrue();
        assertThat(assignee("held")).isEqualTo("m");
    }

    @Test
    void aSecondPassFromTheSameEarlierReadFindsTheHolderChangedAndDoesNothing() {
        AtomicInteger moves = new AtomicInteger();
        Runnable toManager = () -> {
            moves.incrementAndGet();
            reassign("held", "m");
        };

        assertThat(handover.ifStillHeldBy("held", "b", toManager)).isTrue();
        assertThat(handover.ifStillHeldBy("held", "b", toManager)).isFalse();

        assertThat(moves).hasValue(1);
        assertThat(assignee("held")).isEqualTo("m");
    }

    @Test
    void anUnclaimedTaskIsMovedOnlyWhileStillUnclaimed() {
        assertThat(handover.ifStillHeldBy("pool", null, () -> reassign("pool", "m"))).isTrue();
        assertThat(handover.ifStillHeldBy("pool", " ", () -> reassign("pool", "x"))).isFalse();
        assertThat(assignee("pool")).isEqualTo("m");
    }

    @Test
    void aCompletedTaskIsNotMoved() {
        assertThat(handover.ifStillHeldBy("gone", "b", () -> { throw new AssertionError("must not run"); })).isFalse();
    }

    @Test
    void aFailingMoveLeavesTheTaskAsItWas() {
        assertThatThrownBy(() -> handover.ifStillHeldBy("held", "b", () -> {
            reassign("held", "m");
            throw new IllegalStateException("flowable refused");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(assignee("held")).isEqualTo("b");
    }

    @Test
    void aMoveWhoseExtendedRowChangedMeanwhileIsUndoneAndReportedAsNotMoved() {
        assertThat(handover.ifStillHeldBy("held", "b", () -> {
            reassign("held", "m");
            throw new OptimisticLockingFailureException("wf_extended_task_info row changed");
        })).isFalse();
        assertThat(assignee("held")).isEqualTo("b");
    }

    private void reassign(String taskId, String assignee) {
        jdbc.update("UPDATE act_ru_task SET assignee_ = ? WHERE id_ = ?", assignee, taskId);
    }

    private String assignee(String taskId) {
        return jdbc.queryForObject("SELECT assignee_ FROM act_ru_task WHERE id_ = ?", String.class, taskId);
    }
}
