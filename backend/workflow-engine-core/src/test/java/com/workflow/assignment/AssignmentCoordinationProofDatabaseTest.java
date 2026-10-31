package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.util.JsonUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.platform.common.assignment.AssignmentCoordinationProof.*;

/** Same JDBC URL can select different coordination relations. Only the origin's observation of a committed write is proof. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentCoordinationProofDatabaseTest extends AssignmentPostgresFixture {
    AssignmentCoordinationProofRepository origin;
    AssignmentCoordinationProofRepository peer;
    JdbcTemplate peerJdbc;
    String peerSchema;
    @BeforeAll void configure() throws Exception {
        script(source,"105-assignment-coordination-probes.sql"); script(source,"105-assignment-coordination-probes.sql");
        origin=new AssignmentCoordinationProofRepository(jdbc,transaction.getTransactionManager());
        peerSchema=SqlIdentifiers.requireIdentifier(schema+"_peer"); admin.execute("CREATE SCHEMA "+peerSchema);
        var peerSource=dataSource(peerSchema,false); peerJdbc=new JdbcTemplate(peerSource);
        script(peerSource,"100-assignment-exclusion.sql"); script(peerSource,"105-assignment-coordination-probes.sql");
        peer=new AssignmentCoordinationProofRepository(peerJdbc,new DataSourceTransactionManager(peerSource));
        assertEquals(source.getUrl(),peerSource.getUrl());
    }
    @AfterAll void closePeer() { if (peerSchema!=null) admin.execute("DROP SCHEMA "+SqlIdentifiers.requireIdentifier(peerSchema)+" CASCADE"); }

    @Test void committedAcknowledgementIsObservedAndConsumedOnlyOnceByTheOrigin() {
        var challenge=origin.create(Participant.USER_PORTAL); var nonce=UUID.randomUUID();
        origin.acknowledge(challenge,nonce);
        var confirm=new Confirmation(challenge.probeId(),challenge.nonce(),challenge.participant(),nonce);
        assertTrue(origin.confirm(confirm));
        unavailable(()->origin.confirm(confirm));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM we_assignment_coordination_probes WHERE probe_id=?",Integer.class,challenge.probeId()));
    }
    @Test void identicalJdbcUrlAndWritablePrimaryDoNotProveIdenticalCoordinationSchema() {
        var challenge=origin.create(Participant.ADMIN_CENTER); var nonce=UUID.randomUUID();
        assertFalse(Boolean.TRUE.equals(peerJdbc.queryForObject("SELECT pg_is_in_recovery()",Boolean.class)));
        unavailable(()->peer.acknowledge(challenge,nonce));
        assertNull(jdbc.queryForObject("SELECT acknowledgement FROM we_assignment_coordination_probes WHERE probe_id=?",UUID.class,challenge.probeId()));
        unavailable(()->origin.confirm(new Confirmation(challenge.probeId(),challenge.nonce(),challenge.participant(),nonce)));
    }
    @Test void evenSpoofedLocalMetadataAndAReadableCopiedChallengeCannotReplaceTheOriginWitness() {
        var challenge=origin.create(Participant.USER_PORTAL); var local=peer.create(Participant.USER_PORTAL); var nonce=UUID.randomUUID();
        // Adversarial protocol input models matching/spoofed relation metadata on an independently writable copy.
        peerJdbc.update("INSERT INTO we_assignment_coordination_probes(probe_id,nonce,participant,relation_oids,expires_at) VALUES (?,?,?,CAST(? AS jsonb),?)",
                challenge.probeId(),challenge.nonce(),challenge.participant().name(),JsonUtils.toJson(local.relations()),java.sql.Timestamp.from(challenge.expiresAt()));
        var spoofed=new Challenge(challenge.probeId(),challenge.nonce(),challenge.participant(),local.relations(),challenge.expiresAt());
        peer.acknowledge(spoofed,nonce);
        assertEquals(nonce,peerJdbc.queryForObject("SELECT acknowledgement FROM we_assignment_coordination_probes WHERE probe_id=?",UUID.class,challenge.probeId()));
        assertNull(jdbc.queryForObject("SELECT acknowledgement FROM we_assignment_coordination_probes WHERE probe_id=?",UUID.class,challenge.probeId()));
        unavailable(()->origin.confirm(new Confirmation(challenge.probeId(),challenge.nonce(),challenge.participant(),nonce)));
    }
    @Test void neutralProbeCommitsDoNotCommitTheCallersUnrelatedBusinessTransaction() {
        transaction.execute(status->{
            jdbc.update("INSERT INTO p0_saved(id,value) VALUES ('outer-business','must-roll-back')");
            var challenge=origin.create(Participant.ADMIN_CENTER); var nonce=UUID.randomUUID();
            origin.acknowledge(challenge,nonce);
            assertTrue(origin.confirm(new Confirmation(challenge.probeId(),challenge.nonce(),challenge.participant(),nonce)));
            status.setRollbackOnly(); return null;
        });
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM p0_saved WHERE id='outer-business'",Integer.class));
    }
    @Test void aReadOnlyConnectionCannotProduceAWriteAcknowledgementEvenOnAPrimary() {
        var challenge=origin.create(Participant.USER_PORTAL); var nonce=UUID.randomUUID();
        var readOnly=dataSource(schema,true);
        var reader=new AssignmentCoordinationProofRepository(new JdbcTemplate(readOnly),new DataSourceTransactionManager(readOnly));
        unavailable(()->reader.acknowledge(challenge,nonce));
        assertNull(jdbc.queryForObject("SELECT acknowledgement FROM we_assignment_coordination_probes WHERE probe_id=?",UUID.class,challenge.probeId()));
    }
    @Test void wrongParticipantAndExpiredOrReplayedChallengesCannotPass() {
        var challenge=origin.create(Participant.USER_PORTAL); var nonce=UUID.randomUUID(); origin.acknowledge(challenge,nonce);
        unavailable(()->origin.confirm(new Confirmation(challenge.probeId(),challenge.nonce(),Participant.ADMIN_CENTER,nonce)));
        jdbc.update("UPDATE we_assignment_coordination_probes SET expires_at=statement_timestamp()-interval '1 second' WHERE probe_id=?",challenge.probeId());
        unavailable(()->origin.confirm(new Confirmation(challenge.probeId(),challenge.nonce(),challenge.participant(),nonce)));
    }
    @Test void realHttpWitnessSeesTheCommittedAcknowledgementWithoutCommittingOuterBusinessData() throws Exception {
        try (var server=new HttpProbeServer()) {
            var verifier=new AssignmentCoordinationVerifier(origin,AssignmentPublicationCapability.newTransport(),server.origin(),HttpProbeServer.TOKEN,Participant.USER_PORTAL);
            transaction.execute(status->{
                jdbc.update("INSERT INTO p0_saved(id,value) VALUES ('http-outer','must-roll-back')");
                verifier.requireSamePrimary(); status.setRollbackOnly(); return null;
            });
            assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM p0_saved WHERE id='http-outer'",Integer.class));
            assertEquals(2,server.calls.get());
            verifier.requireSamePrimary(); assertEquals(2,server.calls.get(),"An application instance reuses its successful startup proof");
        }
    }
    @Test void realHttpCannotAcceptAnAcknowledgementWrittenToAnotherCoordinationSchema() throws Exception {
        try (var server=new HttpProbeServer()) {
            var verifier=new AssignmentCoordinationVerifier(peer,AssignmentPublicationCapability.newTransport(),server.origin(),HttpProbeServer.TOKEN,Participant.USER_PORTAL);
            unavailable(verifier::requireSamePrimary);
            assertEquals(1,server.calls.get(),"Local schema proof fails before a confirmation request is sent");
        }
    }
    @Test void actualControllerChecksServiceAuthenticationBeforeCreatingAProbe() throws Exception {
        int before=jdbc.queryForObject("SELECT count(*) FROM we_assignment_coordination_probes",Integer.class);
        try (var server=new HttpProbeServer()) {
            var verifier=new AssignmentCoordinationVerifier(origin,AssignmentPublicationCapability.newTransport(),server.origin(),"synthetic-wrong-token",Participant.USER_PORTAL);
            unavailable(verifier::requireSamePrimary); assertEquals(1,server.calls.get());
        }
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM we_assignment_coordination_probes",Integer.class));
    }

    /** Fixture HTTP adapter calls the actual production controller/repository; not a full Spring application startup. */
    private final class HttpProbeServer implements AutoCloseable {
        static final String TOKEN="synthetic-coordination-token";
        final java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        final com.sun.net.httpserver.HttpServer server;
        HttpProbeServer() throws Exception {
            var controller=new com.workflow.controller.AssignmentCoordinationController(origin);
            org.springframework.test.util.ReflectionTestUtils.setField(controller,"token",TOKEN);
            var json=new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
            server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v1/internal/assignment/coordination-proofs",exchange->{
                calls.incrementAndGet(); Object body; int status=200;
                try {
                    String token=exchange.getRequestHeaders().getFirst("X-Internal-Token");
                    if (exchange.getRequestURI().getPath().endsWith("/confirm")) {
                        body=controller.confirm(token,json.readValue(exchange.getRequestBody(),Confirmation.class));
                    } else body=controller.create(token,json.readValue(exchange.getRequestBody(),Request.class));
                } catch (RuntimeException failure) {
                    status=failure instanceof org.springframework.security.access.AccessDeniedException?403:503;
                    body=com.platform.common.dto.ApiResponse.error("PROBE_REJECTED","Synthetic probe rejected");
                }
                byte[] bytes=json.writeValueAsBytes(body); exchange.getResponseHeaders().set("Content-Type","application/json");
                exchange.sendResponseHeaders(status,bytes.length);
                try (var output=exchange.getResponseBody()) { output.write(bytes); }
            });
            server.start();
        }
        String origin() { return "http://127.0.0.1:"+server.getAddress().getPort(); }
        @Override public void close() { server.stop(0); }
    }
    private DriverManagerDataSource dataSource(String target,boolean readOnly) {
        var result=new DriverManagerDataSource(source.getUrl(),"postgres",""); var properties=new Properties();
        properties.setProperty("currentSchema",SqlIdentifiers.requireIdentifier(target));
        if (readOnly) properties.setProperty("options","-c default_transaction_read_only=on");
        result.setConnectionProperties(properties); return result;
    }
    private void script(DriverManagerDataSource target,String name) throws Exception {
        try (var connection=target.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource(java.nio.file.Path.of("../../deploy/init-scripts/00-schema",name).toAbsolutePath().normalize()));
        }
    }
    private void unavailable(org.junit.jupiter.api.function.Executable action) {
        assertEquals("ASSIGNMENT_DEPENDENCY_UNAVAILABLE",assertThrows(AssignmentFailure.class,action).getDetails().get("reason"));
    }
}
