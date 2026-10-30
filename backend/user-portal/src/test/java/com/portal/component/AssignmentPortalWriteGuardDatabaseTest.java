package com.portal.component;

import com.platform.common.assignment.*;
import com.portal.client.AssignmentEngineTransport;
import com.portal.component.impl.AssignmentPortalWriteGuardImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentPortalWriteGuardDatabaseTest extends AssignmentPortalDatabaseFixture {
    final AssignmentEngineTransport engine=mock(AssignmentEngineTransport.class);
    final EntityManager entities=mock(EntityManager.class);
    AssignmentPortalWriteGuard guard;
    AssignmentPortalWriteGuard.Request request;
    AssignmentWriteDecision decision;
    UUID caseId;
    @BeforeEach void seed() {
        reset(engine,entities);
        caseId=UUID.randomUUID();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'actor',1)",caseId);
        request=AssignmentPortalWriteGuard.Request.of("task","actor","SAVE",Map.of("reviewer","next"),caseId.toString());
        long epoch=new AssignmentAuthorityFence(jdbc).revision();
        decision=new AssignmentWriteDecision(1,true,"task","actor",null,"SAVE",request.digest(),caseId,0,0,epoch,Instant.now().plusSeconds(30),request.instanceId());
        guard=new AssignmentPortalWriteGuardImpl(engine,jdbc);
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(guard);
        ReflectionTestUtils.setField(guard,"entities",entities);
        when(engine.post(anyList(),any(),any(),eq(false))).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"Permission preparation must precede the business transaction");
            return decision;
        });
    }
    @Test void exactPacketPublishesDataAndBothRevisionsAtomically() {
        var prepared=guard.prepare(request);
        transaction.executeWithoutResult(status->{
            guard.enter(prepared,request);
            jdbc.update("INSERT INTO up_process_instance(id,variables) VALUES(?,'{\"reviewer\":\"next\"}')",caseId.toString());
            guard.written(prepared,true);
        });
        assertEquals(1,revision("revision")); assertEquals(1,revision("portal_revision"));
        verify(entities).clear();
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(prepared,request)));
        verify(engine,times(1)).post(anyList(),any(),any(),eq(false));
    }
    @Test void anActiveNamedActorWithoutAnAuthenticatedPrincipalCannotPrepareOrConsumeAProtectedPacket() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        assertThrows(AssignmentFailure.class,()->guard.prepare(request));
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(decision,request)));
        verifyNoInteractions(entities);
    }
    @Test void rollbackDoesNotPublishARevisionOrAnyBusinessRow() {
        var prepared=guard.prepare(request);
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{
            guard.enter(prepared,request);
            jdbc.update("INSERT INTO up_process_instance(id,variables) VALUES(?,'{}')",caseId.toString());
            guard.written(prepared,true);
            throw new IllegalStateException("synthetic business failure");
        }));
        assertEquals(0,revision("revision")); assertEquals(0,revision("portal_revision"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_process_instance WHERE id=?",Integer.class,caseId.toString()));
    }
    @Test void anUnverifiedCoordinationDomainCannotPrepareOrEnterABusinessWrite() {
        var coordination=(AssignmentCoordinationVerifier)ReflectionTestUtils.getField(guard,"coordination");
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(coordination).requireSamePrimary();
        assertThrows(AssignmentFailure.class,()->guard.prepare(request));
        doThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE)).when(coordination).requireVerified();
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(decision,request)));
        assertEquals(0,revision("revision")); assertEquals(0,revision("portal_revision"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_process_instance WHERE id=?",Integer.class,request.instanceId()));
        verifyNoInteractions(entities);
    }
    @Test void aCommittedRevocationBeforeFinalCheckRejectsTheWrite() {
        var prepared=guard.prepare(request);
        transaction.executeWithoutResult(status->new AssignmentAuthorityFence(jdbc).beginChange());
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(prepared,request)));
        verifyNoInteractions(entities);
    }
    @Test void pendingCompletionOrReopenBlocksLocalWrites() {
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?",caseId);
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(decision,request)));
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=0,pending_reopen_operation_id=? WHERE case_id=?",UUID.randomUUID(),caseId);
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(decision,request)));
        verifyNoInteractions(entities);
    }
    @Test void expiredOrAlteredPayloadCannotReuseAPermissionPacket() {
        var altered=AssignmentPortalWriteGuard.Request.of("task","actor","SAVE",Map.of("reviewer","other"),request.instanceId());
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(decision,altered)));
        decision=new AssignmentWriteDecision(1,true,"task","actor",null,"SAVE",request.digest(),caseId,0,0,decision.authorityRevision(),Instant.EPOCH,request.instanceId());
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->guard.enter(decision,request)));
        verifyNoInteractions(entities);
    }
    @Test void unavailableEngineIsNotTreatedAsLegacy() {
        when(engine.post(anyList(),any(),any(),eq(false))).thenThrow(new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_ENGINE_UNSUPPORTED));
        assertThrows(AssignmentFailure.class,()->guard.prepare(request));
    }
    @Test void anotherProcessCannotReuseAnOtherwiseValidTaskPacket() {
        var other=AssignmentPortalWriteGuard.Request.of("task","actor","SAVE",Map.of("reviewer","next"),"another-instance");
        assertThrows(AssignmentFailure.class,()->guard.prepare(other));
        verifyNoInteractions(entities);
    }
    @Test void executorTakesFinalFenceBeforeBusinessWritesAndCommitsRevision() {
        var executor=new AssignmentPortalWriteExecutor(guard,transaction.getTransactionManager());
        String result=executor.execute(request,false,()->{
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            verify(entities).clear();
            jdbc.update("INSERT INTO up_process_instance(id,variables) VALUES(?,'{}')",caseId.toString());
            return "saved";
        });
        assertEquals("saved",result); assertEquals(1,revision("portal_revision")); assertEquals(0,revision("revision"));
        assertThrows(AssignmentFailure.class,()->executor.execute(request,false,()->{fail("Stale packet must not reach business writes");return null;}));
    }
    private long revision(String field) {
        return switch(field) {
            case "revision"->jdbc.queryForObject("SELECT revision FROM we_assignment_cases WHERE case_id=?",Long.class,caseId);
            case "portal_revision"->jdbc.queryForObject("SELECT portal_revision FROM we_assignment_cases WHERE case_id=?",Long.class,caseId);
            default->throw new IllegalArgumentException("Unknown test field");
        };
    }
}
