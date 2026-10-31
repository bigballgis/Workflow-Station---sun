package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real schema and production repository: replacement, rounds, delegate responsibility and rollback. */
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentResponsibilityDatabaseTest extends AssignmentPostgresFixture {
    UUID caseId;
    UUID roundId;
    AssignmentResponsibilityRepository records;

    @BeforeEach
    void createCase() {
        caseId = UUID.randomUUID();
        roundId = UUID.randomUUID();
        records = new AssignmentResponsibilityRepository(jdbc, new ObjectMapper());
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'initiator',1)", caseId);
        instance("root-" + caseId, roundId);
    }

    @Test void samePositionReplacesOnlyAfterTheNextSuccessfulCompletion() {
        task("first", "slot", "root-" + caseId);
        transaction.executeWithoutResult(status -> records.completed("first", "b", Set.of("b")));
        task("retry", "slot", "root-" + caseId);
        assertEquals(Set.of("b"), effectiveUsers());
        transaction.executeWithoutResult(status -> records.completed("retry", "c", Set.of("c")));
        assertEquals(Set.of("c"), effectiveUsers());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM we_assignment_completion_records WHERE case_id=?", Integer.class, caseId));
    }

    @Test void differentPositionsAndCancelledReopenRoundsAccumulate() {
        task("line-a", "slot-a", "root-" + caseId);
        task("line-b", "slot-b", "root-" + caseId);
        transaction.executeWithoutResult(status -> {
            records.completed("line-a", "b", Set.of("b"));
            records.completed("line-b", "c", Set.of("c"));
        });
        jdbc.update("UPDATE we_assignment_instances SET state='COMPLETED' WHERE case_id=?", caseId);
        String reopened = "reopened-" + caseId;
        instance(reopened, UUID.randomUUID());
        task("reopen-task", "slot-a", reopened);
        transaction.executeWithoutResult(status -> records.completed("reopen-task", "d", Set.of("d")));
        jdbc.update("UPDATE we_assignment_instances SET state='CANCELLED' WHERE process_instance_id=?", reopened);
        assertEquals(Set.of("b", "c", "d"), effectiveUsers());
    }

    @Test void delegateRecordsBothIdentitiesAndConflictingReplayIsRejected() {
        task("delegated", "slot", "root-" + caseId);
        transaction.executeWithoutResult(status -> records.completed("delegated", "c", Set.of("b", "c")));
        long revision = jdbc.queryForObject("SELECT revision FROM we_assignment_cases WHERE case_id=?", Long.class, caseId);
        transaction.executeWithoutResult(status -> records.completed("delegated", "c", Set.of("b", "c")));
        assertEquals(revision, jdbc.queryForObject("SELECT revision FROM we_assignment_cases WHERE case_id=?", Long.class, caseId));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> records.completed("delegated", "c", Set.of("c"))));
        assertEquals(Set.of("b", "c"), effectiveUsers());
    }

    @Test void failingCompletionDoesNotInvalidateThePreviousEffectiveResponsibility() {
        task("old", "slot", "root-" + caseId);
        task("new", "slot", "root-" + caseId);
        transaction.executeWithoutResult(status -> records.completed("old", "b", Set.of("b")));
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            records.completed("new", "c", Set.of("c"));
            throw new IllegalStateException("synthetic engine failure");
        }));
        assertEquals(Set.of("b"), effectiveUsers());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM we_assignment_completion_records WHERE task_id='new'", Integer.class));
    }

    private Set<String> effectiveUsers() {
        return records.effective(caseId).stream().flatMap(record -> record.responsibleUserIds().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    private void instance(String id, UUID round) {
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,process_definition_id,function_unit_code,call_path,state) "
                + "VALUES(?,?,?,'frozen-definition','synthetic-fu','[]','ACTIVE')", id, caseId, round);
    }

    private void task(String id, String slot, String instance) {
        jdbc.update("INSERT INTO we_assignment_task_states(task_id,case_id,process_instance_id,process_definition_id,node_key,slot_key,slot_identity,"
                + "policy_hash,primary_candidates,state) VALUES(?,?,?,'frozen-definition','work',?,'{}','hash','[]','ACTIVE')", id, caseId, instance, slot);
    }
}
