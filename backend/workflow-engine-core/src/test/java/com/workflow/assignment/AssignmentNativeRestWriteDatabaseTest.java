package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.exception.GlobalExceptionHandler;
import org.flowable.rest.service.api.RestResponseFactory;
import org.flowable.rest.service.api.runtime.task.TaskIdentityLinkCollectionResource;
import org.flowable.rest.service.api.runtime.task.TaskVariableCollectionResource;
import org.flowable.rest.service.api.runtime.process.ProcessInstanceVariableCollectionResource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Actual Flowable 7.2 REST resources and actual PostgreSQL, without a mock variable/task service. */
@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentNativeRestWriteDatabaseTest extends AssignmentPostgresFixture {
    MockMvc mvc;
    String definition;
    AnnotationConfigWebApplicationContext parentContext;
    AnnotationConfigWebApplicationContext restContext;
    @BeforeAll void configure() {
        openEngine();
        var json=new ObjectMapper().findAndRegisterModules(); var factory=new RestResponseFactory(json);
        var links=new TaskIdentityLinkCollectionResource();
        ReflectionTestUtils.setField(links,"taskService",engine.getTaskService());
        ReflectionTestUtils.setField(links,"restResponseFactory",factory);
        var taskVariables=new TaskVariableCollectionResource();
        ReflectionTestUtils.setField(taskVariables,"taskService",engine.getTaskService());
        ReflectionTestUtils.setField(taskVariables,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(taskVariables,"restResponseFactory",factory);
        ReflectionTestUtils.setField(taskVariables,"objectMapper",json);
        var processVariables=new ProcessInstanceVariableCollectionResource();
        ReflectionTestUtils.setField(processVariables,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(processVariables,"restResponseFactory",factory);
        ReflectionTestUtils.setField(processVariables,"objectMapper",json);
        var bulkTasks=new org.flowable.rest.service.api.runtime.task.TaskCollectionResource();
        ReflectionTestUtils.setField(bulkTasks,"taskService",engine.getTaskService());
        ReflectionTestUtils.setField(bulkTasks,"restResponseFactory",factory);
        var bulkProcesses=new org.flowable.rest.service.api.runtime.process.ProcessInstanceCollectionResource();
        ReflectionTestUtils.setField(bulkProcesses,"runtimeService",engine.getRuntimeService());
        ReflectionTestUtils.setField(bulkProcesses,"repositoryService",engine.getRepositoryService());
        ReflectionTestUtils.setField(bulkProcesses,"historyService",engine.getHistoryService());
        ReflectionTestUtils.setField(bulkProcesses,"restResponseFactory",factory);
        mvc=restMvc(json,links,taskVariables,processVariables,bulkTasks,bulkProcesses);
        var deployed=engine.getRepositoryService().createDeployment().addString("native.bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="synthetic">
                <process id="native_write_probe" isExecutable="true"><startEvent id="start"/><userTask id="work"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/></process></definitions>
                """).deploy();
        definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployed.getId()).singleResult().getId();
    }
    private MockMvc restMvc(ObjectMapper json,Object links,Object tasks,Object processes,Object bulkTasks,Object bulkProcesses) {
        var servlet=new org.springframework.mock.web.MockServletContext();
        parentContext=new AnnotationConfigWebApplicationContext(); parentContext.setServletContext(servlet);
        var legacy=new AssignmentLegacyTaskCheck(engine.getTaskService(),engine.getRuntimeService(),new AssignmentCaseRepository(jdbc,json),
                new AssignmentDefinitionPolicy(engine.getRepositoryService()));
        parentContext.addBeanFactoryPostProcessor(factory->{
            factory.registerSingleton("legacyCheck",legacy);
            factory.registerSingleton("managementService",engine.getManagementService());
            factory.registerSingleton("historyCheck",new AssignmentLegacyHistoryCheck(engine.getHistoryService(),engine.getRuntimeService(),
                    new AssignmentCaseRepository(jdbc,json),new AssignmentDefinitionPolicy(engine.getRepositoryService()),legacy));
            factory.registerSingleton("objectMapper",json);
            factory.registerSingleton("platformErrors",new GlobalExceptionHandler());
        });
        parentContext.register(com.workflow.config.AssignmentNativeWriteConfiguration.class,AssignmentNativeWriteInterceptor.class,
                AssignmentNativeRestErrors.class,AssignmentNativeBodyWriteAdvice.class,AssignmentNativeJobWriteInterceptor.class,AssignmentNativeHistoryWriteInterceptor.class);
        parentContext.refresh();
        restContext=new AnnotationConfigWebApplicationContext(); restContext.setParent(parentContext); restContext.setServletContext(servlet);
        restContext.addBeanFactoryPostProcessor(factory->{
            factory.registerSingleton("nativeLinks",links); factory.registerSingleton("nativeTaskVariables",tasks);
            factory.registerSingleton("nativeProcessVariables",processes);
            factory.registerSingleton("nativeBulkTasks",bulkTasks); factory.registerSingleton("nativeBulkProcesses",bulkProcesses);
        });
        // Exactly the MVC support class imported by Flowable's separate process-api DispatcherServlet.
        restContext.register(org.flowable.spring.boot.DispatcherServletConfiguration.class);
        restContext.refresh();
        return MockMvcBuilders.webAppContextSetup(restContext).build();
    }
    @AfterAll void closeMvcContexts() {
        if (restContext!=null) restContext.close();
        if (parentContext!=null) parentContext.close();
    }
    @Test void nativeCandidateWriteCannotBypassTheProtectedCase() throws Exception {
        var target=start(true);
        var result=mvc.perform(post("/runtime/tasks/{taskId}/identitylinks",target.task()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"user\":\"excluded-native-user\",\"type\":\"candidate\"}")).andReturn();
        assertAll(()->assertRejected(result),
                ()->assertTrue(engine.getTaskService().getIdentityLinksForTask(target.task()).isEmpty()));
    }
    @Test void nativeTaskVariablesCannotChangeTheProtectedProcessSource() throws Exception {
        var target=start(true);
        var result=mvc.perform(post("/runtime/tasks/{taskId}/variables",target.task()).contentType(MediaType.APPLICATION_JSON)
                .content(variable())).andReturn();
        assertAll(()->assertRejected(result),
                ()->assertNull(engine.getRuntimeService().getVariable(target.process(),"renamed_source")));
    }
    @Test void nativeProcessVariablesCannotChangeTheProtectedSource() throws Exception {
        var target=start(true);
        var result=mvc.perform(post("/runtime/process-instances/{processInstanceId}/variables",target.process()).contentType(MediaType.APPLICATION_JSON)
                .content(variable().replace("\"global\"","\"local\""))).andReturn();
        assertAll(()->assertRejected(result),
                ()->assertNull(engine.getRuntimeService().getVariable(target.process(),"renamed_source")));
    }
    @Test void aBulkTaskUpdateIsRejectedBeforeChangingAnyProtectedOrLegacyTask() throws Exception {
        var legacy=start(false); var target=start(true);
        var result=mvc.perform(put("/runtime/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("taskIds",List.of(legacy.task(),target.task()),"name","synthetic-renamed")))).andReturn();
        assertAll(()->assertRejected(result),
                ()->assertNotEquals("synthetic-renamed",engine.getTaskService().createTaskQuery().taskId(legacy.task()).singleResult().getName()),
                ()->assertNotEquals("synthetic-renamed",engine.getTaskService().createTaskQuery().taskId(target.task()).singleResult().getName()));
    }
    @Test void bulkDeletionCannotPartiallyDeleteAMixedLegacyAndProtectedSelection() throws Exception {
        var legacy=start(false); var target=start(true);
        var result=mvc.perform(post("/runtime/process-instances/delete").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("instanceIds",List.of(legacy.process(),target.process()),"action","delete")))).andReturn();
        assertAll(()->assertRejected(result),
                ()->assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(legacy.process()).singleResult()),
                ()->assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.process()).singleResult()));
    }
    @Test void legacyBulkUpdatesAndDeletesRemainAvailable() throws Exception {
        var target=start(false);
        var update=mvc.perform(put("/runtime/tasks").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("taskIds",List.of(target.task()),"name","legacy-renamed")))).andReturn();
        assertEquals(200,update.getResponse().getStatus(),update.getResponse().getContentAsString());
        assertEquals("legacy-renamed",engine.getTaskService().createTaskQuery().taskId(target.task()).singleResult().getName());
        var deletion=mvc.perform(post("/runtime/process-instances/delete").contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(java.util.Map.of("instanceIds",List.of(target.process()),"action","delete")))).andReturn();
        assertEquals(204,deletion.getResponse().getStatus(),deletion.getResponse().getContentAsString());
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.process()).singleResult());
    }
    private void assertRejected(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        String response=result.getResponse().getContentAsString();
        assertEquals(503,result.getResponse().getStatus(),response);
        var body=new ObjectMapper().readTree(response);
        assertFalse(body.path("success").asBoolean(true));
        assertEquals("ASSIGNMENT_CONTEXT_UNAVAILABLE",body.path("error").path("details").path("reason").asText(),response);
    }
    @Test void nativeLegacyWritesAndProtectedReadsRemainAvailable() throws Exception {
        var legacy=start(false);
        var result=mvc.perform(post("/runtime/tasks/{taskId}/variables",legacy.task()).contentType(MediaType.APPLICATION_JSON).content(variable())).andReturn();
        assertEquals(201,result.getResponse().getStatus(),result.getResponse().getContentAsString());
        assertEquals("synthetic-user",engine.getRuntimeService().getVariable(legacy.process(),"renamed_source"));
        var legacyProcess=start(false);
        var processWrite=mvc.perform(post("/runtime/process-instances/{processInstanceId}/variables",legacyProcess.process())
                .contentType(MediaType.APPLICATION_JSON).content(variable().replace("\"global\"","\"local\""))).andReturn();
        assertEquals(201,processWrite.getResponse().getStatus(),processWrite.getResponse().getContentAsString());
        assertEquals("synthetic-user",engine.getRuntimeService().getVariable(legacyProcess.process(),"renamed_source"));
        var protectedTarget=start(true);
        assertEquals(200,mvc.perform(get("/runtime/tasks/{taskId}/identitylinks",protectedTarget.task())).andReturn().getResponse().getStatus());
    }
    private String variable() { return "[{\"name\":\"renamed_source\",\"type\":\"string\",\"value\":\"synthetic-user\",\"scope\":\"global\"}]"; }
    private Target start(boolean protectedCase) {
        var process=engine.getRuntimeService().startProcessInstanceById(definition);
        var task=engine.getTaskService().createTaskQuery().processInstanceId(process.getId()).singleResult();
        if (protectedCase) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var caseId=UUID.randomUUID();
            cases.createCase(caseId,process.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(process.getId(),caseId,UUID.randomUUID(),definition,List.of()),null,null,"native_write_probe");
        }
        return new Target(process.getId(),task.getId());
    }
    record Target(String process,String task) { }
}
