package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flowable.rest.service.api.management.JobResource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Real management REST and real Flowable jobs. The delegate counts local effects only. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentNativeJobDatabaseTest extends AssignmentPostgresFixture {
    MockMvc mvc;
    @BeforeAll void configure() {
        openEngine(); var json=new ObjectMapper().findAndRegisterModules();
        var resource=new JobResource();
        ReflectionTestUtils.setField(resource,"managementService",engine.getManagementService());
        ReflectionTestUtils.setField(resource,"processEngineConfiguration",engine.getProcessEngineConfiguration());
        ReflectionTestUtils.setField(resource,"restResponseFactory",new org.flowable.rest.service.api.RestResponseFactory(json));
        var legacy=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),new AssignmentCaseRepository(jdbc,json),
                new AssignmentDefinitionPolicy(engine.getRepositoryService()));
        var collection=new org.flowable.rest.service.api.management.JobCollectionResource();
        ReflectionTestUtils.setField(collection,"managementService",engine.getManagementService());
        ReflectionTestUtils.setField(collection,"processEngineConfiguration",engine.getProcessEngineConfiguration());
        ReflectionTestUtils.setField(collection,"restResponseFactory",new org.flowable.rest.service.api.RestResponseFactory(json));
        var jobs=new AssignmentNativeJobWriteInterceptor(engine.getManagementService(),legacy);
        var history=new AssignmentLegacyHistoryCheck(engine.getHistoryService(),engine.getRuntimeService(),new AssignmentCaseRepository(jdbc,json),
                new AssignmentDefinitionPolicy(engine.getRepositoryService()),legacy);
        mvc=MockMvcBuilders.standaloneSetup(resource,collection).addInterceptors(new AssignmentNativeWriteInterceptor(legacy),jobs)
                .setControllerAdvice(new AssignmentNativeRestErrors(new com.platform.common.exception.GlobalExceptionHandler()),
                        new AssignmentNativeBodyWriteAdvice(legacy,jobs,history)).build();
    }
    @BeforeEach void resetCounter() { CountedDelegate.calls.set(0); }

    @Test void nativeExecutionCannotAdvanceAProtectedProcessBeforeItsNextTask() throws Exception {
        var target=start(true,false);
        var result=action("jobs",target.job(),"execute");
        assertAll(()->denied(result),()->assertEquals(0,CountedDelegate.calls.get()),
                ()->assertNotNull(engine.getManagementService().createJobQuery().jobId(target.job()).singleResult()),
                ()->assertEquals(0,engine.getTaskService().createTaskQuery().processInstanceId(target.process()).count()));
    }
    @Test void nativeTimerPromotionCannotPrepareAProtectedJobForLaterExecution() throws Exception {
        var target=start(true,true);
        var result=action("timer-jobs",target.job(),"move");
        assertAll(()->denied(result),()->assertEquals(0,CountedDelegate.calls.get()),
                ()->assertNotNull(engine.getManagementService().createTimerJobQuery().jobId(target.job()).singleResult()),
                ()->assertNull(engine.getManagementService().createJobQuery().jobId(target.job()).singleResult()));
    }
    @Test void legacyExecutionAndTimerPromotionKeepTheirActualEngineBehavior() throws Exception {
        var executable=start(false,false);
        var result=action("jobs",executable.job(),"execute");
        assertTrue(result.getResponse().getStatus()/100==2,result.getResponse().getContentAsString());
        assertEquals(1,CountedDelegate.calls.get());
        assertNull(engine.getManagementService().createJobQuery().jobId(executable.job()).singleResult());
        assertEquals(1,engine.getTaskService().createTaskQuery().processInstanceId(executable.process()).count());
        var timer=start(false,true);
        var moved=action("timer-jobs",timer.job(),"move");
        assertTrue(moved.getResponse().getStatus()/100==2,moved.getResponse().getContentAsString());
        assertNull(engine.getManagementService().createTimerJobQuery().jobId(timer.job()).singleResult());
        assertNotNull(engine.getManagementService().createJobQuery().jobId(timer.job()).singleResult());
        assertEquals(1,CountedDelegate.calls.get());
    }
    @Test void protectedSuspendedJobCannotBeDeletedAndItsReadRemainsAvailable() throws Exception {
        var target=start(true,false);
        engine.getRuntimeService().suspendProcessInstanceById(target.process());
        var result=mvc.perform(delete("/management/suspended-jobs/{jobId}",target.job())).andReturn();
        denied(result);
        assertNotNull(engine.getManagementService().createSuspendedJobQuery().jobId(target.job()).singleResult());
        var read=mvc.perform(get("/management/suspended-jobs/{jobId}",target.job())).andReturn();
        assertEquals(200,read.getResponse().getStatus(),read.getResponse().getContentAsString());
        assertEquals(target.job(),new ObjectMapper().readTree(read.getResponse().getContentAsString()).path("id").asText());
    }
    @Test void protectedDeadLetterJobCannotBePromoted() throws Exception {
        var target=start(true,false); engine.getManagementService().moveJobToDeadLetterJob(target.job());
        denied(action("deadletter-jobs",target.job(),"move"));
        assertNotNull(engine.getManagementService().createDeadLetterJobQuery().jobId(target.job()).singleResult());
        assertNull(engine.getManagementService().createJobQuery().jobId(target.job()).singleResult());
    }
    @Test void aMixedBulkPromotionIsRejectedBeforeMovingEitherJob() throws Exception {
        var legacy=start(false,false); var tracked=start(true,false);
        engine.getManagementService().moveJobToDeadLetterJob(legacy.job());
        engine.getManagementService().moveJobToDeadLetterJob(tracked.job());
        var result=bulk(List.of(legacy.job(),tracked.job()));
        assertAll(()->denied(result),
                ()->assertNotNull(engine.getManagementService().createDeadLetterJobQuery().jobId(legacy.job()).singleResult()),
                ()->assertNotNull(engine.getManagementService().createDeadLetterJobQuery().jobId(tracked.job()).singleResult()),
                ()->assertNull(engine.getManagementService().createJobQuery().jobId(legacy.job()).singleResult()),
                ()->assertNull(engine.getManagementService().createJobQuery().jobId(tracked.job()).singleResult()));
    }
    @Test void legacyBulkPromotionMovesTheWholeRequestedSelection() throws Exception {
        var first=start(false,false); var second=start(false,false);
        engine.getManagementService().moveJobToDeadLetterJob(first.job()); engine.getManagementService().moveJobToDeadLetterJob(second.job());
        var result=bulk(List.of(first.job(),second.job()));
        assertTrue(result.getResponse().getStatus()/100==2,result.getResponse().getContentAsString());
        assertNull(engine.getManagementService().createDeadLetterJobQuery().jobId(first.job()).singleResult());
        assertNull(engine.getManagementService().createDeadLetterJobQuery().jobId(second.job()).singleResult());
        assertNotNull(engine.getManagementService().createJobQuery().jobId(first.job()).singleResult());
        assertNotNull(engine.getManagementService().createJobQuery().jobId(second.job()).singleResult());
    }
    private org.springframework.test.web.servlet.MvcResult bulk(List<String> jobs) throws Exception {
        return mvc.perform(post("/management/deadletter-jobs").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("action","move","jobIds",jobs)))).andReturn();
    }
    private org.springframework.test.web.servlet.MvcResult action(String kind,String job,String action) throws Exception {
        return mvc.perform(post("/management/"+kind+"/{jobId}",job).contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(Map.of("action",action)))).andReturn();
    }
    private void denied(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        String body=result.getResponse().getContentAsString(); assertEquals(503,result.getResponse().getStatus(),body);
        assertEquals("ASSIGNMENT_CONTEXT_UNAVAILABLE",new ObjectMapper().readTree(body).path("error").path("details").path("reason").asText(),body);
    }
    private Target start(boolean tracked,boolean timer) {
        String key="job_"+UUID.randomUUID().toString().replace("-","");
        String work=timer?"<intermediateCatchEvent id=\"work\"><timerEventDefinition><timeDuration>PT1H</timeDuration></timerEventDefinition></intermediateCatchEvent>"
                :"<serviceTask id=\"work\" flowable:async=\"true\" flowable:class=\"com.workflow.assignment.AssignmentNativeJobDatabaseTest$CountedDelegate\"/>";
        String xml="""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="urn:synthetic">
                <process id="%s" isExecutable="true"><startEvent id="start"/>%s<userTask id="after"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/><sequenceFlow id="b" sourceRef="work" targetRef="after"/>
                </process></definitions>
                """.formatted(key,work);
        var deployed=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",xml).deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployed.getId()).singleResult().getId();
        var instance=engine.getRuntimeService().startProcessInstanceById(definition);
        if (tracked) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var caseId=UUID.randomUUID();
            cases.createCase(caseId,instance.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(instance.getId(),caseId,UUID.randomUUID(),definition,List.of()),null,null,key);
        }
        var job=timer?engine.getManagementService().createTimerJobQuery().processInstanceId(instance.getId()).singleResult()
                :engine.getManagementService().createJobQuery().processInstanceId(instance.getId()).singleResult();
        return new Target(instance.getId(),job.getId());
    }
    record Target(String process,String job) { }
    public static class CountedDelegate implements org.flowable.engine.delegate.JavaDelegate {
        static final AtomicInteger calls=new AtomicInteger();
        @Override public void execute(org.flowable.engine.delegate.DelegateExecution execution) { calls.incrementAndGet(); }
    }
}
