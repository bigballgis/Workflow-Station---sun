package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.ReflectionUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Actual SDK history resources with finished Flowable instances and variables in owned PostgreSQL. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentNativeHistoryDatabaseTest extends AssignmentPostgresFixture {
    MockMvc mvc;
    ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    String definition;
    @BeforeAll void configure() {
        openEngine();
        var processes=new org.flowable.rest.service.api.history.HistoricProcessInstanceResource();
        var tasks=new org.flowable.rest.service.api.history.HistoricTaskInstanceResource();
        var bulk=new org.flowable.rest.service.api.history.HistoricProcessInstanceCollectionResource();
        wire(processes); wire(tasks); wire(bulk);
        var cases=new AssignmentCaseRepository(jdbc,json); var definitions=new AssignmentDefinitionPolicy(engine.getRepositoryService());
        var legacy=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),cases,definitions);
        var jobs=new AssignmentNativeJobWriteInterceptor(engine.getManagementService(),legacy);
        var history=new AssignmentLegacyHistoryCheck(engine.getHistoryService(),engine.getRuntimeService(),cases,definitions,legacy);
        mvc=MockMvcBuilders.standaloneSetup(processes,tasks,bulk).addInterceptors(new AssignmentNativeWriteInterceptor(legacy),
                        new AssignmentNativeHistoryWriteInterceptor(history))
                .setControllerAdvice(new AssignmentNativeRestErrors(new com.platform.common.exception.GlobalExceptionHandler()),
                        new AssignmentNativeBodyWriteAdvice(legacy,jobs,history)).build();
        var deployed=engine.getRepositoryService().createDeployment().addString("history.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="urn:synthetic">
                <process id="history_probe" isExecutable="true"><startEvent id="start"/><userTask id="work"/><endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/><sequenceFlow id="b" sourceRef="work" targetRef="end"/>
                </process></definitions>
                """).deploy();
        definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployed.getId()).singleResult().getId();
    }
    private void wire(Object resource) {
        var factory=new org.flowable.rest.service.api.RestResponseFactory(json);
        // SDK subclasses shadow some base fields; wire every declared service field, including the actual base query path.
        ReflectionUtils.doWithFields(resource.getClass(),field->{
            Object value=field.getType()==org.flowable.engine.HistoryService.class?engine.getHistoryService()
                    :field.getType()==org.flowable.engine.TaskService.class?engine.getTaskService()
                    :field.getType()==org.flowable.engine.RepositoryService.class?engine.getRepositoryService()
                    :field.getType()==org.flowable.rest.service.api.RestResponseFactory.class?factory:null;
            if (value!=null) { ReflectionUtils.makeAccessible(field); field.set(resource,value); }
        });
    }
    @Test void protectedProcessHistoryCannotBeDeletedIndividually() throws Exception {
        var target=start(true);
        var result=mvc.perform(delete("/history/historic-process-instances/{processInstanceId}",target.process())).andReturn();
        assertAll(()->denied(result),()->retained(target));
    }
    @Test void protectedCompletedTaskHistoryCannotBeDeletedIndividually() throws Exception {
        var target=start(true);
        var result=mvc.perform(delete("/history/historic-task-instances/{taskId}",target.task())).andReturn();
        assertAll(()->denied(result),()->retained(target));
    }
    @Test void mixedBulkHistoryDeletionIsRejectedBeforeRemovingEitherInstance() throws Exception {
        var legacy=start(false); var tracked=start(true);
        var result=bulk(List.of(legacy.process(),tracked.process()));
        assertAll(()->denied(result),()->retained(legacy),()->retained(tracked));
    }
    @Test void ordinarySingleAndBulkHistoryDeletionKeepTheirExistingBehavior() throws Exception {
        var single=start(false); var first=start(false); var second=start(false);
        var one=mvc.perform(delete("/history/historic-process-instances/{processInstanceId}",single.process())).andReturn();
        assertEquals(204,one.getResponse().getStatus(),one.getResponse().getContentAsString());
        var many=bulk(List.of(first.process(),second.process()));
        assertEquals(204,many.getResponse().getStatus(),many.getResponse().getContentAsString());
        for (var target:List.of(single,first,second)) {
            assertEquals(0,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(target.process()).count());
            assertEquals(0,engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(target.process()).count());
        }
    }
    @Test void protectedProcessAndCompletedTaskRemainReadable() throws Exception {
        var target=start(true);
        var process=mvc.perform(get("/history/historic-process-instances/{processInstanceId}",target.process())).andReturn();
        var task=mvc.perform(get("/history/historic-task-instances/{taskId}",target.task())).andReturn();
        assertEquals(200,process.getResponse().getStatus(),process.getResponse().getContentAsString());
        assertEquals(target.process(),json.readTree(process.getResponse().getContentAsString()).path("id").asText());
        assertEquals(200,task.getResponse().getStatus(),task.getResponse().getContentAsString());
        assertEquals(target.task(),json.readTree(task.getResponse().getContentAsString()).path("id").asText());
        retained(target);
    }
    @Test void anOrdinaryCompletedTaskCanStillBeDeletedWithoutDeletingItsProcessOrVariables() throws Exception {
        var target=start(false);
        var result=mvc.perform(delete("/history/historic-task-instances/{taskId}",target.task())).andReturn();
        assertEquals(204,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        assertEquals(0,engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(target.task()).count());
        assertEquals(1,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(target.process()).count());
        assertEquals("synthetic",engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(target.process()).variableName("kept").singleResult().getValue());
    }
    private org.springframework.test.web.servlet.MvcResult bulk(List<String> ids) throws Exception {
        return mvc.perform(post("/history/historic-process-instances/delete").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("instanceIds",ids,"action","delete")))).andReturn();
    }
    private void denied(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        var body=result.getResponse().getContentAsString(); assertEquals(503,result.getResponse().getStatus(),body);
        assertEquals("ASSIGNMENT_CONTEXT_UNAVAILABLE",json.readTree(body).path("error").path("details").path("reason").asText(),body);
    }
    private void retained(Target target) {
        assertNotNull(engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(target.process()).singleResult());
        assertEquals(1,engine.getHistoryService().createHistoricTaskInstanceQuery().taskId(target.task()).count());
        var value=engine.getHistoryService().createHistoricVariableInstanceQuery().processInstanceId(target.process()).variableName("kept").singleResult();
        assertNotNull(value); assertEquals("synthetic",value.getValue());
    }
    private Target start(boolean tracked) {
        var instance=engine.getRuntimeService().startProcessInstanceById(definition,Map.of("kept","synthetic"));
        var task=engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        if (tracked) {
            var cases=new AssignmentCaseRepository(jdbc,json); var caseId=UUID.randomUUID(); cases.createCase(caseId,instance.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(instance.getId(),caseId,UUID.randomUUID(),definition,List.of()),null,null,"history_probe");
        }
        engine.getTaskService().complete(task.getId()); return new Target(instance.getId(),task.getId());
    }
    record Target(String process,String task) { }
}
