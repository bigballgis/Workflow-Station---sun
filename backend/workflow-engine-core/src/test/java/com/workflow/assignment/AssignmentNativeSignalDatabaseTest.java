package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.flowable.rest.service.api.runtime.SignalResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Actual SDK broadcast, PostgreSQL subscriptions and a counted delegate; no external messages are sent. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentNativeSignalDatabaseTest extends AssignmentPostgresFixture {
    MockMvc mvc;
    AssignmentLegacyTaskCheck legacyCheck;
    @BeforeAll void configure() {
        openEngine();
        var json=new ObjectMapper().findAndRegisterModules();
        var resource=new SignalResource();
        ReflectionTestUtils.setField(resource,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(resource,"restResponseFactory",new org.flowable.rest.service.api.RestResponseFactory(json));
        var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        legacyCheck=spy(new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),new AssignmentCaseRepository(jdbc,json),definitions));
        var configuration=(org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl)engine.getProcessEngineConfiguration();
        new AssignmentNativeSignalBoundary(legacyCheck,definitions).afterInit(configuration.getEventSubscriptionServiceConfiguration());
        mvc=MockMvcBuilders.standaloneSetup(resource).addInterceptors(new AssignmentNativeWriteInterceptor(legacyCheck))
                .setControllerAdvice(new AssignmentNativeRestErrors(new com.platform.common.exception.GlobalExceptionHandler())).build();
    }
    @BeforeEach void resetCounter() { CountedDelegate.calls.set(0); }

    @Test void mixedBroadcastIsRejectedBeforeAnyDelegateAndLeavesEverySubscriptionIntact() throws Exception {
        String signal=signal(); var legacy=start(signal,false); var protectedInstance=start(signal,true);
        var result=send(signal,false,null);
        assertAll(()->assertDenied(result),()->assertEquals(0,CountedDelegate.calls.get()),
                ()->assertWaiting(legacy),()->assertWaiting(protectedInstance));
        assertFalse(AssignmentNativeRestScope.active(),"A rejected HTTP request must not mark subsequent internal engine work as native REST");
    }

    @Test void asyncMixedBroadcastCannotQueueJobsThatBypassTheBoundaryLater() throws Exception {
        String signal=signal(); var legacy=start(signal,false); var protectedInstance=start(signal,true);
        long before=engine.getManagementService().createJobQuery().count();
        var result=send(signal,true,null);
        assertAll(()->assertDenied(result),()->assertEquals(before,engine.getManagementService().createJobQuery().count()),
                ()->assertWaiting(legacy),()->assertWaiting(protectedInstance),()->assertEquals(0,CountedDelegate.calls.get()));
    }

    @Test void legacyOnlyBroadcastKeepsPayloadAndInvokesEveryExistingSubscriber() throws Exception {
        String signal=signal(); var first=start(signal,false); var second=start(signal,false);
        var result=send(signal,false,null);
        assertEquals(204,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        assertEquals(2,CountedDelegate.calls.get());
        assertEquals("synthetic",engine.getRuntimeService().getVariable(first,"payload"));
        assertEquals("synthetic",engine.getRuntimeService().getVariable(second,"payload"));
    }

    @Test void tenantScopedLegacyBroadcastDoesNotInspectOrDispatchAnotherTenantsProtectedSubscription() throws Exception {
        String signal=signal(); var legacy=start(signal,false,"legacy-tenant"); var other=start(signal,true,"other-tenant");
        var result=send(signal,false,"legacy-tenant");
        assertEquals(204,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        assertEquals(1,CountedDelegate.calls.get());
        assertEquals("synthetic",engine.getRuntimeService().getVariable(legacy,"payload"));
        assertWaiting(other);
    }

    @Test void legacyAsyncBroadcastStillQueuesExactlyItsExistingSubscribers() throws Exception {
        String signal=signal(); var first=start(signal,false); var second=start(signal,false);
        long before=engine.getManagementService().createJobQuery().count();
        var result=send(signal,true,null);
        assertEquals(202,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        assertEquals(before+2,engine.getManagementService().createJobQuery().count());
        assertEquals(0,CountedDelegate.calls.get());
        assertWaiting(first); assertWaiting(second);
    }

    @Test void aProtectedSubscriptionCommittedDuringValidationIsOutsideTheCapturedBroadcast() throws Exception {
        String signal=signal(); var first=start(signal,false);
        var late=new java.util.concurrent.atomic.AtomicReference<String>();
        // The checker still runs its real DB/definition checks; this hook controls when another transaction commits.
        doAnswer(call->{
            call.callRealMethod();
            if (late.get()==null) late.set(java.util.concurrent.CompletableFuture.supplyAsync(()->start(signal,true))
                    .get(10,java.util.concurrent.TimeUnit.SECONDS));
            return null;
        }).when(legacyCheck).requireLegacyInstance(anyString());
        try {
            var result=send(signal,false,null);
            assertEquals(204,result.getResponse().getStatus(),result.getResponse().getContentAsString());
            assertNotNull(late.get()); assertWaiting(late.get());
            assertEquals(1,CountedDelegate.calls.get());
            assertEquals("synthetic",engine.getRuntimeService().getVariable(first,"payload"));
        } finally {
            doCallRealMethod().when(legacyCheck).requireLegacyInstance(anyString());
        }
    }

    @Test void protectedSignalStartDefinitionIsRejectedBeforeStartingANewInstanceOrDispatchingLegacyWork() throws Exception {
        String signal=signal(); var legacy=start(signal,false);
        String key="start_"+UUID.randomUUID().toString().replace("-","");
        String protectedXml=xml(key,signal).replace("<startEvent id=\"start\"/>","""
                <extensionElements><flowable:properties><flowable:property name="assignmentRuntimeContract" value="1"/>
                <flowable:property name="assignmentRuntimeTracking" value="true"/>
                <flowable:property name="assignmentRuntimeUsers" value="{}"/></flowable:properties></extensionElements>
                <startEvent id="start"><signalEventDefinition signalRef="signal"/></startEvent>
                """).replace("<sequenceFlow id=\"a\" sourceRef=\"start\" targetRef=\"wait\"/>",
                "<sequenceFlow id=\"a\" sourceRef=\"start\" targetRef=\"count\"/>");
        // Fixture-owned engine intentionally permits direct compiled definitions; the production compiler gate is tested separately.
        var deployed=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",protectedXml).deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployed.getId()).singleResult().getId();
        var result=send(signal,false,null);
        assertAll(()->assertDenied(result),()->assertWaiting(legacy),()->assertEquals(0,CountedDelegate.calls.get()),
                ()->assertEquals(0,engine.getRuntimeService().createProcessInstanceQuery().processDefinitionId(definition).count()));
    }

    private org.springframework.test.web.servlet.MvcResult send(String signal,boolean async,String tenant) throws Exception {
        var body=new LinkedHashMap<String,Object>(); body.put("signalName",signal); body.put("async",async);
        if (!async) body.put("variables",List.of(Map.of("name","payload","type","string","value","synthetic")));
        if (tenant!=null) body.put("tenantId",tenant);
        return mvc.perform(post("/runtime/signals").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(body))).andReturn();
    }
    private void assertDenied(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        String body=result.getResponse().getContentAsString();
        assertEquals(503,result.getResponse().getStatus(),body);
        assertEquals("ASSIGNMENT_CONTEXT_UNAVAILABLE",new ObjectMapper().readTree(body).path("error").path("details").path("reason").asText(),body);
    }
    private void assertWaiting(String instance) {
        assertEquals(1,engine.getRuntimeService().createEventSubscriptionQuery().processInstanceId(instance).count());
        assertEquals(0,engine.getTaskService().createTaskQuery().processInstanceId(instance).count());
        assertNull(engine.getRuntimeService().getVariable(instance,"payload"));
    }
    private String start(String signal,boolean tracked) {
        return start(signal,tracked,null);
    }
    private String start(String signal,boolean tracked,String tenant) {
        String key="probe_"+UUID.randomUUID().toString().replace("-","");
        var builder=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml(key,signal));
        if (tenant!=null) builder.tenantId(tenant);
        var deployed=builder.deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployed.getId()).singleResult().getId();
        var instance=engine.getRuntimeService().startProcessInstanceById(definition);
        if (tracked) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var caseId=UUID.randomUUID();
            cases.createCase(caseId,instance.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(instance.getId(),caseId,UUID.randomUUID(),definition,List.of()),null,null,key);
        }
        return instance.getId();
    }
    private String signal() { return "signal_"+UUID.randomUUID().toString().replace("-",""); }
    private String xml(String key,String signal) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:synthetic">
                <signal id="signal" name="%s"/><process id="%s" isExecutable="true"><startEvent id="start"/>
                <intermediateCatchEvent id="wait"><signalEventDefinition signalRef="signal"/></intermediateCatchEvent>
                <serviceTask id="count" flowable:class="com.workflow.assignment.AssignmentNativeSignalDatabaseTest$CountedDelegate"/>
                <userTask id="after"/><sequenceFlow id="a" sourceRef="start" targetRef="wait"/>
                <sequenceFlow id="b" sourceRef="wait" targetRef="count"/><sequenceFlow id="c" sourceRef="count" targetRef="after"/>
                </process></definitions>
                """.formatted(signal,key);
    }
    public static class CountedDelegate implements JavaDelegate {
        static final AtomicInteger calls=new AtomicInteger();
        @Override public void execute(DelegateExecution execution) { calls.incrementAndGet(); }
    }
}
