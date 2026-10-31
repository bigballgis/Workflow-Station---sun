package com.workflow.assignment;

import com.platform.common.assignment.AssignmentAuthorityFence;
import com.sun.net.httpserver.HttpServer;
import org.flowable.engine.delegate.JavaDelegate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** P0 counterexample: synchronous downstream qualification writes re-enter a globally held read fence over HTTP. */
@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentAuthorityReentryTest extends AssignmentPostgresFixture {
    HttpServer server;
    ExecutorService httpExecutor;
    URI mutationUri;
    final AtomicReference<String> sqlState = new AtomicReference<>();

    @BeforeAll void configure() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpExecutor = Executors.newSingleThreadExecutor();
        server.setExecutor(httpExecutor);
        server.createContext("/synthetic-qualification-mutation", exchange -> {
            int status = 200;
            try {
                transaction.executeWithoutResult(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout='200ms'");
                    new AssignmentAuthorityFence(jdbc).beginChange();
                });
            } catch (DataAccessException error) {
                Throwable cause = error;
                while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
                if (cause instanceof java.sql.SQLException sql) sqlState.set(sql.getSQLState());
                status = 503;
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        mutationUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/synthetic-qualification-mutation");
        openEngine();
        engine.getRepositoryService().createDeployment().addString("reentry.bpmn20.xml", model()).deploy();
    }

    @Override Map<Object,Object> engineBeans() {
        JavaDelegate mutate = execution -> new RestTemplate().postForEntity(mutationUri, null, String.class);
        return Map.of("mutateQualification", mutate);
    }

    @BeforeEach void reset() {
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
        sqlState.set(null);
    }

    @Test void transactionWideQualificationReadLockBlocksASynchronousDownstreamQualificationMutation() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_authority_reentry");
        var first = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
            new AssignmentAuthorityFence(jdbc).readLocked();
            lockCase("synthetic-case");
            engine.getTaskService().complete(first.getId());
        }));
        assertEquals("55P03", sqlState.get(), "independent HTTP transaction must fail specifically on the held PostgreSQL lock");
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(first.getId()).singleResult());
        assertEquals(0, new AssignmentAuthorityFence(jdbc).revision());
    }

    @Test void authorizationAtTheFinalVersionReadAllowsTheAlreadyAcceptedOperationToFinish() {
        var instance = engine.getRuntimeService().startProcessInstanceByKey("assignment_authority_reentry");
        var first = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        long evaluatedRevision = new AssignmentAuthorityFence(jdbc).revision();
        transaction.executeWithoutResult(status -> {
            lockCase("synthetic-case");
            assertEquals(evaluatedRevision, new AssignmentAuthorityFence(jdbc).revision());
            engine.getTaskService().complete(first.getId());
        });
        assertNull(sqlState.get());
        assertNull(engine.getTaskService().createTaskQuery().taskId(first.getId()).singleResult());
        assertEquals(1, new AssignmentAuthorityFence(jdbc).revision());
    }

    @AfterAll void stopHttpServer() {
        if (server != null) server.stop(0);
        if (httpExecutor != null) httpExecutor.shutdownNow();
    }

    private String model() {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="assignment-p0">
                  <process id="assignment_authority_reentry" isExecutable="true">
                    <startEvent id="start"/><userTask id="first"/>
                    <serviceTask id="changeQualification" flowable:delegateExpression="${mutateQualification}"/><endEvent id="end"/>
                    <sequenceFlow id="s1" sourceRef="start" targetRef="first"/>
                    <sequenceFlow id="s2" sourceRef="first" targetRef="changeQualification"/>
                    <sequenceFlow id="s3" sourceRef="changeQualification" targetRef="end"/>
                  </process>
                </definitions>
                """;
    }
}
