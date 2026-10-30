package com.portal.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.platform.common.assignment.*;
import com.platform.common.jdbc.SqlIdentifiers;
import com.portal.component.impl.AssignmentPortalSyncImpl;
import com.portal.repository.AssignmentPortalSyncRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL", matches=".+")
class AssignmentPortalSyncDatabaseTest extends AssignmentPortalDatabaseFixture {
    AssignmentPortalSyncRepository repository;
    AssignmentPortalSync sync;
    String instance;
    UUID caseId;

    @BeforeEach void request() {
        repository = new AssignmentPortalSyncRepository(jdbc, json);
        sync = component(repository);
        caseId = UUID.randomUUID(); instance = "instance-" + caseId;
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version,accepted_operation_seq) VALUES(?,'actor',1,1)", caseId);
        jdbc.update("INSERT INTO up_process_instance(id,variables,lock_version) VALUES(?,CAST(? AS jsonb),0)", instance, "{\"a\":1,\"b\":3}");
    }

    @Test void minimalEffectPreservesDisjointWritesAndCommitsLedgerAndWatermarkOnce() {
        var receipt = receipt(1);
        transaction.executeWithoutResult(status -> sync.apply(receipt));
        transaction.executeWithoutResult(status -> sync.apply(receipt));
        assertEquals("{\"a\":2,\"b\":3}", repository.variables(instance).values().toString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM up_assignment_operation_sync WHERE case_id=?", Integer.class, caseId));
        assertEquals(1, jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?", Long.class, caseId));
        assertEquals(1, repository.variables(instance).version());
    }

    @Test void sameFieldConflictNeverCreatesASuccessLedgerOrAdvancesWatermark() {
        jdbc.update("UPDATE up_process_instance SET variables='{\"a\":9,\"b\":3}'::jsonb WHERE id=?", instance);
        assertThrows(AssignmentFailure.class, () -> transaction.executeWithoutResult(status -> sync.apply(receipt(1))));
        assertEquals(9, repository.variables(instance).values().get("a").intValue());
        assertFalse(repository.applied(receipt(1).operationId()).isPresent());
        assertEquals(0, jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?", Long.class, caseId));
    }

    @Test void nativeWriteWithoutVersionIncrementIsStillDetectedByContentComparison() {
        var baseline = repository.variables(instance);
        jdbc.update("UPDATE up_process_instance SET variables='{\"a\":7}'::jsonb WHERE id=?", instance);
        assertThrows(AssignmentFailure.class, () -> transaction.executeWithoutResult(status -> repository.replace(baseline,
                baseline.values().deepCopy().put("a", 2))));
        assertEquals(7, repository.variables(instance).values().get("a").intValue());
        assertEquals(0, repository.variables(instance).version());
    }

    @Test void outOfOrderEffectRollsBackTheAlreadyAttemptedLocalVariableUpdate() {
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=2 WHERE case_id=?", caseId);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> sync.apply(receipt(2))));
        assertEquals(1, repository.variables(instance).values().get("a").intValue());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM up_assignment_operation_sync WHERE case_id=?", Integer.class, caseId));
    }

    @Test void ledgerFailureRollsBackVariablesAndCaseWatermark() {
        var failing = org.mockito.Mockito.spy(repository);
        org.mockito.Mockito.doThrow(new IllegalStateException("synthetic ledger failure")).when(failing).record(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
        var component = component(failing);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> component.apply(receipt(1))));
        assertEquals(1, repository.variables(instance).values().get("a").intValue());
        assertEquals(0, repository.variables(instance).version());
        assertEquals(0, jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?", Long.class, caseId));
    }

    @Test void acceptedSnapshotAndOriginalAuditApplyExactlyOnceWithTheVariablesAndLedger() {
        var accepted=auditedReceipt();
        transaction.executeWithoutResult(status->sync.apply(accepted));
        jdbc.update("UPDATE up_process_instance SET variables=jsonb_set(variables,'{b}','9') WHERE id=?",instance);
        transaction.executeWithoutResult(status->sync.apply(accepted));
        assertEquals(2,repository.variables(instance).values().path("a").asInt());
        assertEquals(9,repository.variables(instance).values().path("b").asInt());
        assertEquals("original accepted value",repository.variables(instance).values().path("_snapshot_task").path("value").asText());
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM up_change_history WHERE process_instance_id=?",Integer.class,instance));
        var audit=jdbc.queryForMap("SELECT user_id,stage_id,old_value,new_value,batch_id FROM up_change_history WHERE process_instance_id=?",instance);
        assertEquals("actor",audit.get("user_id")); assertEquals("review",audit.get("stage_id"));
        assertEquals("old display",audit.get("old_value")); assertEquals("original accepted display",audit.get("new_value"));
        assertEquals("accepted-batch",audit.get("batch_id"));
    }

    @Test void failureAfterAuditInsertRollsBackSnapshotHistoryLedgerAndWatermarkTogether() {
        var failing=org.mockito.Mockito.spy(repository);
        org.mockito.Mockito.doAnswer(call->{call.callRealMethod();throw new IllegalStateException("synthetic failure after audit persistence");})
                .when(failing).applyAudit(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
        var component=component(failing);
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->component.apply(auditedReceipt())));
        assertEquals(1,repository.variables(instance).values().path("a").asInt());
        assertFalse(repository.variables(instance).values().has("_snapshot_task"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_change_history WHERE process_instance_id=?",Integer.class,instance));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_assignment_operation_sync WHERE case_id=?",Integer.class,caseId));
        assertEquals(0,jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
    }

    private AssignmentOperationReceipt auditedReceipt() {
        var original=receipt(1); var before=json.createObjectNode().put("a",1); var after=json.createObjectNode().put("a",2);
        after.putObject("_snapshot_task").put("value","original accepted value");
        var at=java.time.Instant.parse("2026-10-27T10:00:00Z");
        var change=new AssignmentCompletionRequest.AuditChange("caption","old display","original accepted display",AssignmentCompletionRequest.AuditKind.FIELD_UPDATE,
                null,null,at,"accepted-batch",at);
        var effect=new AssignmentCompletionRequest.Effect(instance,AssignmentVariablePatch.between(before,after),"review","b".repeat(64),List.of(change),Map.of());
        Map<String,Object> payload=com.platform.common.util.JsonUtils.getObjectMapper().convertValue(effect,new com.fasterxml.jackson.core.type.TypeReference<>() { });
        return new AssignmentOperationReceipt(original.operationId(),caseId,"task","actor",null,"APPROVE",original.requestDigest(),1,0,payload,"PENDING_SYNC");
    }

    private AssignmentOperationReceipt receipt(long sequence) {
        ObjectNode before = json.createObjectNode().put("a", 1), after = json.createObjectNode().put("a", 2);
        var effect = new AssignmentCompletionRequest.Effect(instance, AssignmentVariablePatch.between(before, after));
        Map<String,Object> payload = json.convertValue(effect, new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() { });
        return new AssignmentOperationReceipt(UUID.nameUUIDFromBytes(instance.getBytes(java.nio.charset.StandardCharsets.UTF_8)), caseId,
                "task", "actor", null, "APPROVE", "a".repeat(64), sequence, 0, payload, "PENDING_SYNC");
    }

    private AssignmentPortalSyncImpl component(AssignmentPortalSyncRepository source) {
        var value=new AssignmentPortalSyncImpl(source,jdbc,org.mockito.Mockito.mock(AssignmentAcceptedProjection.class),
                new com.portal.repository.AssignmentPostActionRepository(jdbc,json),org.mockito.Mockito.mock(AssignmentCompletionCacheInvalidation.class));
        org.springframework.test.util.ReflectionTestUtils.setField(value,"entities",org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class));
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(value);
        return value;
    }


}
