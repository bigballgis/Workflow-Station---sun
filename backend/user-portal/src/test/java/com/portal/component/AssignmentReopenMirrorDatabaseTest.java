package com.portal.component;

import com.platform.common.assignment.*;
import com.portal.component.impl.AssignmentReopenMirrorImpl;
import com.portal.entity.ProcessInstance;
import com.portal.repository.AssignmentReopenMirrorRepository;
import com.portal.repository.ProcessInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL", matches=".+")
class AssignmentReopenMirrorDatabaseTest extends AssignmentPortalDatabaseFixture {
    ProcessInstanceRepository instances;
    AssignmentReopenMirrorRepository ledger;
    AssignmentReopenMirror mirror;
    AssignmentReopenReceipt receipt;
    ProcessInstance source;

    @BeforeEach void prepare() {
        UUID caseId = UUID.randomUUID(), operation = UUID.randomUUID();
        String sourceId = "source-" + operation, next = "next-" + operation;
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version,pending_reopen_operation_id) VALUES(?,'owner',1,?)", caseId, operation);
        source = ProcessInstance.builder().id(sourceId).startUserId("owner").processDefinitionId("frozen-definition")
                .processDefinitionKey("frozen-key").functionUnitCatalogId("frozen-catalog").functionUnitCode("FU")
                .functionUnitVersionLabel("1").status("COMPLETED").variables(Map.of("formValue", "source")).build();
        receipt = new AssignmentReopenReceipt(operation, sourceId, next, "frozen-definition", "administrator",
                Map.of("initiator", "owner", "formValue", "accepted"), "ACTIVE", false, caseId);
        instances = mock(ProcessInstanceRepository.class);
        when(instances.findById(sourceId)).thenReturn(Optional.of(source));
        when(instances.existsById(next)).thenAnswer(call -> jdbc.queryForObject("SELECT count(*) FROM up_process_instance WHERE id=?", Integer.class, next) > 0);
        when(instances.saveAndFlush(any())).thenAnswer(call -> {
            ProcessInstance value = call.getArgument(0);
            jdbc.update("INSERT INTO up_process_instance(id,variables,lock_version) VALUES(?,CAST(? AS jsonb),0)", value.getId(), json.writeValueAsString(value.getVariables()));
            return value;
        });
        ledger = new AssignmentReopenMirrorRepository(jdbc);
        mirror = new AssignmentReopenMirrorImpl(instances, ledger, jdbc, new ProcessTerminalStatusResolver());
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(mirror);
    }

    @Test void mirrorRetainsTheOriginalOwnerAndFrozenReleaseWithAnAtomicSynchronizationProof() {
        transaction.executeWithoutResult(status -> mirror.apply(receipt));
        var captured = org.mockito.ArgumentCaptor.forClass(ProcessInstance.class);
        verify(instances).saveAndFlush(captured.capture());
        assertEquals("owner", captured.getValue().getStartUserId());
        assertEquals("frozen-catalog", captured.getValue().getFunctionUnitCatalogId());
        assertEquals("RUNNING", captured.getValue().getStatus());
        assertEquals(receipt.operationId(), transaction.execute(status -> new AssignmentCaseFence(jdbc).locked(receipt.caseId()).portalAppliedReopen()));
        assertTrue(ledger.find(receipt.operationId()).isPresent());
    }

    @Test void repeatingAnAcceptedMirrorCannotRestoreAnOldFormSnapshot() {
        transaction.executeWithoutResult(status -> mirror.apply(receipt));
        jdbc.update("UPDATE up_process_instance SET variables='{\"formValue\":\"later-save\"}'::jsonb WHERE id=?", receipt.processInstanceId());
        transaction.executeWithoutResult(status -> mirror.apply(receipt));
        assertEquals("later-save", jsonValue().get("formValue"));
        verify(instances, times(1)).saveAndFlush(any());
    }

    @Test void failedLedgerWriteRollsBackTheMirrorAndItsSharedProof() {
        var failing = spy(ledger);
        doThrow(new IllegalStateException("synthetic ledger failure")).when(failing).record(any());
        var component = new AssignmentReopenMirrorImpl(instances, failing, jdbc, new ProcessTerminalStatusResolver());
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(component);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> component.apply(receipt)));
        assertFalse(instances.existsById(receipt.processInstanceId()));
        assertFalse(ledger.find(receipt.operationId()).isPresent());
        assertNull(transaction.execute(status -> new AssignmentCaseFence(jdbc).locked(receipt.caseId()).portalAppliedReopen()));
    }

    @Test void contradictorySourceOwnershipDoesNotCreateAMisownedRequest() {
        source.setStartUserId("someone-else");
        assertThrows(AssignmentFailure.class, () -> transaction.executeWithoutResult(status -> mirror.apply(receipt)));
        assertFalse(instances.existsById(receipt.processInstanceId()));
        verify(instances, never()).saveAndFlush(any());
    }

    private Map<?,?> jsonValue() {
        try { return json.readValue(jdbc.queryForObject("SELECT variables::text FROM up_process_instance WHERE id=?", String.class, receipt.processInstanceId()), Map.class); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
