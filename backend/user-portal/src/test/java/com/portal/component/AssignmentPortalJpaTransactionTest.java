package com.portal.component;

import com.platform.common.assignment.*;
import com.portal.client.AssignmentEngineTransport;
import com.portal.component.impl.AssignmentPortalWriteGuardImpl;
import com.portal.entity.ProcessInstance;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Uses the production entity and transaction manager, in addition to the lower-level JDBC fence tests. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentPortalJpaTransactionTest extends AssignmentPortalDatabaseFixture {
    final AssignmentEngineTransport engine=mock(AssignmentEngineTransport.class);
    LocalContainerEntityManagerFactoryBean factory;
    jakarta.persistence.EntityManagerFactory emf;
    EntityManager entities;
    AssignmentPortalWriteGuard guard;
    AssignmentPortalWriteExecutor writes;
    AssignmentPortalWriteGuard.Request request;
    AssignmentWriteDecision decision;
    UUID caseId;
    String instance;

    @BeforeAll void hibernate() {
        factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(jdbc.getDataSource());
        factory.setManagedTypes(PersistenceManagedTypes.of(ProcessInstance.class.getName()));
        factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","update","hibernate.show_sql","false"));
        factory.afterPropertiesSet(); emf=Objects.requireNonNull(factory.getObject());
        entities=SharedEntityManagerCreator.createSharedEntityManager(emf);
        transaction=new TransactionTemplate(new JpaTransactionManager(emf));
        guard=new AssignmentPortalWriteGuardImpl(engine,jdbc);
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(guard);
        ReflectionTestUtils.setField(guard,"entities",entities);
        writes=new AssignmentPortalWriteExecutor(guard,transaction.getTransactionManager());
    }
    @AfterAll void closeHibernate() { if (factory!=null) factory.destroy(); }
    @BeforeEach void seed() {
        reset(engine); caseId=UUID.randomUUID(); instance=caseId.toString();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'actor',1)",caseId);
        transaction.executeWithoutResult(status->entities.persist(ProcessInstance.builder().id(instance).processDefinitionId("frozen-definition")
                .processDefinitionKey("synthetic").startUserId("actor").status("RUNNING").variables(new LinkedHashMap<>(Map.of("caption","original","other","preserved"))).build()));
        request=AssignmentPortalWriteGuard.Request.of("task","actor","SAVE",Map.of("caption","accepted"),instance);
        decision=new AssignmentWriteDecision(1,true,"task","actor",null,"SAVE",request.digest(),caseId,0,0,
                new AssignmentAuthorityFence(jdbc).revision(),Instant.now().plusSeconds(30),instance);
        when(engine.post(anyList(),any(),any(),eq(false))).thenAnswer(call->{
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive()); return decision;
        });
    }
    @Test void jdbcFenceAndEntityWriteUseTheSamePrimaryConnectionAndCommitTogether() {
        writes.execute(request,true,()->{
            assertEquals(((Number)entities.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()).intValue(),
                    jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));
            assertFalse(jdbc.queryForObject("SELECT pg_is_in_recovery()",Boolean.class));
            changeCaption(); return null;
        });
        assertEquals("accepted",caption()); assertEquals(1L,revision("revision")); assertEquals(1L,revision("portal_revision"));
        assertEquals(1L,jdbc.queryForObject("SELECT lock_version FROM up_process_instance WHERE id=?",Long.class,instance));
    }
    @Test void aFailureAfterBothWritesRollsBackEntityAndCoordinationRowsTogether() {
        var prepared=guard.prepare(request);
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{
            guard.enter(prepared,request); changeCaption(); guard.written(prepared,true);
            throw new IllegalStateException("synthetic failure after JPA flush and revision publication");
        }));
        assertEquals("original",caption()); assertEquals(0L,revision("revision")); assertEquals(0L,revision("portal_revision"));
        assertEquals(0L,jdbc.queryForObject("SELECT lock_version FROM up_process_instance WHERE id=?",Long.class,instance));
    }
    @Test void anOsivEntityReadBeforePreparationIsClearedBeforeTheProtectedWrite() {
        var osiv=emf.createEntityManager();
        TransactionSynchronizationManager.bindResource(emf,new EntityManagerHolder(osiv));
        try {
            assertEquals("original",osiv.find(ProcessInstance.class,instance).getVariables().get("caption"));
            jdbc.update("UPDATE up_process_instance SET variables='{\"caption\":\"concurrent\",\"other\":\"newer value\"}',lock_version=lock_version+1 WHERE id=?",instance);
            writes.execute(request,true,()->{
                var current=entities.find(ProcessInstance.class,instance);
                assertEquals("concurrent",current.getVariables().get("caption"));
                assertEquals("newer value",current.getVariables().get("other"));
                changeCaption(); return null;
            });
            assertEquals("accepted",caption());
            assertEquals("newer value",jdbc.queryForObject("SELECT variables->>'other' FROM up_process_instance WHERE id=?",String.class,instance));
        } finally { TransactionSynchronizationManager.unbindResourceIfPossible(emf); osiv.close(); }
    }
    private void changeCaption() {
        var current=entities.find(ProcessInstance.class,instance);
        var values=new LinkedHashMap<>(current.getVariables()); values.put("caption","accepted");
        current.setVariables(values); entities.flush();
    }
    @Test void aRealJpaTransactionManagerCommitsOnlyNeutralProbePhasesWhileTheBusinessChangeRollsBack() throws Exception {
        try (var connection=Objects.requireNonNull(jdbc.getDataSource()).getConnection()) {
            org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.FileSystemResource(
                    java.nio.file.Path.of("../../deploy/init-scripts/00-schema/105-assignment-coordination-probes.sql").toAbsolutePath().normalize()));
        }
        var probes=new AssignmentCoordinationProofRepository(jdbc,transaction.getTransactionManager());
        transaction.executeWithoutResult(status->{
            changeCaption();
            var challenge=probes.create(AssignmentCoordinationProof.Participant.USER_PORTAL); var acknowledgement=UUID.randomUUID();
            probes.acknowledge(challenge,acknowledgement);
            assertTrue(probes.confirm(new AssignmentCoordinationProof.Confirmation(challenge.probeId(),challenge.nonce(),challenge.participant(),acknowledgement)));
            status.setRollbackOnly();
        });
        assertEquals("original",caption()); assertEquals(0,revision("revision")); assertEquals(0,revision("portal_revision"));
    }
    private String caption() { return jdbc.queryForObject("SELECT variables->>'caption' FROM up_process_instance WHERE id=?",String.class,instance); }
    private long revision(String field) {
        if (!List.of("revision","portal_revision").contains(field)) throw new IllegalArgumentException("Invalid test revision");
        return jdbc.queryForObject("SELECT "+field+" FROM we_assignment_cases WHERE case_id=?",Long.class,caseId);
    }
}
