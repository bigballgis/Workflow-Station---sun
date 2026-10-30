package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentDefinitionRetentionDatabaseTest extends AssignmentPostgresFixture {
    org.springframework.test.web.servlet.MockMvc mvc;
    @Override void configureEngine(org.flowable.spring.SpringProcessEngineConfiguration configuration) {
        configuration.setRepositoryService(new AssignmentRepositoryService(new AssignmentDefinitionRetention(jdbc)));
        configuration.setCustomJobHandlers(AssignmentDefinitionActionJobs.handlers(new AssignmentDefinitionRetention(jdbc)));
        configuration.setPreBpmnParseHandlers(List.of(new AssignmentDeploymentParseGuard(new AssignmentDefinitionRetention(jdbc))));
    }
    @BeforeAll void configure() throws Exception {
        try(var connection=source.getConnection()) {
            ScriptUtils.executeSqlScript(connection,new FileSystemResource("../../deploy/init-scripts/00-schema/106-assignment-definition-retention.sql"));
        }
        jdbc.execute("CREATE TABLE sys_function_unit_contents(content_type text,flowable_deployment_id text,flowable_process_definition_id text)");
        openEngine();
        var resource=new org.flowable.rest.service.api.repository.ProcessDefinitionResource();
        var json=new ObjectMapper().findAndRegisterModules();
        org.springframework.test.util.ReflectionTestUtils.setField(resource,"repositoryService",engine.getRepositoryService());
        org.springframework.test.util.ReflectionTestUtils.setField(resource,"restResponseFactory",new org.flowable.rest.service.api.RestResponseFactory(json));
        org.springframework.test.util.ReflectionTestUtils.setField(resource,"processEngineConfiguration",engine.getProcessEngineConfiguration());
        mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(resource)
                .setControllerAdvice(new AssignmentNativeRestErrors(new com.platform.common.exception.GlobalExceptionHandler())).build();
    }
    @Test void aRegisteredRunningRoundCannotBeCascadeDeleted() {
        var target=target(true,false);
        assertAll(()->assertThrows(AssignmentFailure.class,()->engine.getRepositoryService().deleteDeployment(target.deployment(),true)),
                ()->assertNotNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.instance()).singleResult()),
                ()->assertEquals(1,engine.getTaskService().createTaskQuery().processInstanceId(target.instance()).count()),
                ()->assertNotNull(engine.getRepositoryService().getProcessDefinition(target.definition())));
    }
    @Test void anEndedRoundKeepsItsDefinitionAndHistoryForReopen() {
        var target=target(true,true);
        assertAll(()->assertThrows(AssignmentFailure.class,()->((org.flowable.engine.impl.RepositoryServiceImpl)engine.getRepositoryService()).deleteDeploymentCascade(target.deployment())),
                ()->assertNotNull(engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(target.definition()).singleResult()),
                ()->assertEquals(1,engine.getHistoryService().createHistoricProcessInstanceQuery().processInstanceId(target.instance()).count()));
    }
    @Test void frozenReferencesAreCheckedEvenBeforeTheCalledInstanceExists() {
        var child=target(false,true); var owner=target(false,true);
        transaction.executeWithoutResult(status->new AssignmentDefinitionRetention(jdbc).publication(owner.deployment(),List.of(child.definition())));
        assertAll(()->assertThrows(AssignmentFailure.class,()->engine.getRepositoryService().deleteDeployment(child.deployment(),true)),
                ()->assertNotNull(engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(child.definition()).singleResult()));
        engine.getRepositoryService().deleteDeployment(owner.deployment(),true);
        engine.getRepositoryService().deleteDeployment(child.deployment(),true);
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM we_assignment_definition_dependencies WHERE owner_deployment_id=?",Integer.class,owner.deployment()));
    }
    @Test void nativeRepositoryDeleteCannotBypassTheCatalogPin() {
        var target=target(false,true);
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('PROCESS',?,?)",target.deployment(),target.definition());
        assertThrows(AssignmentFailure.class,()->engine.getRepositoryService().deleteDeployment(target.deployment(),true));
        assertNotNull(engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(target.definition()).singleResult());
    }
    @Test void anUnreferencedLegacyDeploymentRetainsItsOriginalDeleteBehavior() {
        var target=target(false,false);
        engine.getRepositoryService().deleteDeployment(target.deployment(),true);
        assertNull(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.instance()).singleResult());
        assertNull(engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(target.definition()).singleResult());
    }
    @Test void nativeDefinitionCascadeCannotSuspendAPendingProtectedRound() throws Exception {
        var target=target(true,false);
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE root_instance_id=?",target.instance());
        assertAll(()->assertEquals(503,action(target.definition(),"suspend",true,null)),
                ()->assertFalse(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.instance()).singleResult().isSuspended()),
                ()->assertFalse(engine.getRepositoryService().getProcessDefinition(target.definition()).isSuspended()));
    }
    @Test void nativeDefinitionCascadeCannotActivateOrScheduleAProtectedRound() throws Exception {
        var target=target(true,false); engine.getRepositoryService().suspendProcessDefinitionById(target.definition());
        engine.getRuntimeService().suspendProcessInstanceById(target.instance());
        assertEquals(503,action(target.definition(),"activate",true,null));
        assertTrue(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.instance()).singleResult().isSuspended());
        long before=engine.getManagementService().createTimerJobQuery().count();
        assertEquals(503,action(target.definition(),"activate",true,java.time.Instant.now().plusSeconds(3600).toString()));
        assertEquals(before,engine.getManagementService().createTimerJobQuery().count());
    }
    @Test void legacyCascadeAndProtectedDefinitionOnlyChangesKeepTheirOriginalBehavior() throws Exception {
        var legacy=target(false,false);
        assertEquals(200,action(legacy.definition(),"suspend",true,null));
        assertTrue(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(legacy.instance()).singleResult().isSuspended());
        assertEquals(200,action(legacy.definition(),"activate",true,null));
        assertFalse(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(legacy.instance()).singleResult().isSuspended());
        var protectedTarget=target(true,false);
        assertEquals(200,action(protectedTarget.definition(),"suspend",false,null));
        assertFalse(engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(protectedTarget.instance()).singleResult().isSuspended());
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"suspend","activate"})
    void aPreviouslyScheduledLegacyCascadeRechecksProtectionAtItsActualExecution(String operation) {
        var target=target(false,false);
        if ("activate".equals(operation)) {
            engine.getRepositoryService().suspendProcessDefinitionById(target.definition());
            engine.getRuntimeService().suspendProcessInstanceById(target.instance());
            engine.getRepositoryService().activateProcessDefinitionById(target.definition(),true,java.util.Date.from(java.time.Instant.now().plusSeconds(3600)));
        } else engine.getRepositoryService().suspendProcessDefinitionById(target.definition(),true,java.util.Date.from(java.time.Instant.now().plusSeconds(3600)));
        var timer=engine.getManagementService().createTimerJobQuery().processDefinitionId(target.definition()).singleResult();
        assertNotNull(timer);
        publishCaller(target.definition(),true); // Protection is established after the legacy administrative request.
        var executable=engine.getManagementService().moveTimerToExecutableJob(timer.getId());
        assertAll(()->assertThrows(RuntimeException.class,()->engine.getManagementService().executeJob(executable.getId())),
                ()->assertEquals("activate".equals(operation),engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(target.instance()).singleResult().isSuspended()));
    }
    private int action(String definition,String action,boolean cascade,String date) throws Exception {
        var body=new LinkedHashMap<String,Object>(); body.put("action",action); body.put("includeProcessInstances",cascade);
        if(date!=null) body.put("date",date);
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/repository/process-definitions/{id}",definition)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(body)))
                .andReturn().getResponse().getStatus();
    }
    @Test void actualCompilerAndParseGuardRetainTheEntireTransitiveCalledGraph() {
        var leaf=target(false,true); String middle=publishCaller(leaf.definition(),false);
        String root=publishCaller(middle,true);
        String owner=engine.getRepositoryService().getProcessDefinition(root).getDeploymentId();
        assertEquals(Set.of(leaf.definition(),middle),new HashSet<>(jdbc.queryForList(
                "SELECT referenced_definition_id FROM we_assignment_definition_dependencies WHERE owner_deployment_id=?",String.class,owner)));
        assertThrows(AssignmentFailure.class,()->engine.getRepositoryService().deleteDeployment(leaf.deployment(),true));
        engine.getRepositoryService().deleteDeployment(owner,true);
        engine.getRepositoryService().deleteDeployment(leaf.deployment(),true);
    }
    @Test void invalidDeploymentRollsBackItsDerivedFrozenReferenceRows() {
        var leaf=target(false,true); long before=jdbc.queryForObject("SELECT count(*) FROM we_assignment_definition_dependencies",Long.class);
        var compiler=new com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl(engine.getRepositoryService(),
                org.mockito.Mockito.mock(AssignmentIdentityClient.class),new ObjectMapper(),org.mockito.Mockito.mock(com.workflow.client.AdminCenterClient.class));
        String xml=compiler.compile(callerXml(leaf.definition(),true)).replace("<userTask id=\"work\">","<serviceTask id=\"work\">")
                .replace("</userTask>","</serviceTask>");
        assertThrows(RuntimeException.class,()->AssignmentCompiledDeployment.deploy("invalid.bpmn20.xml",xml,
                ()->engine.getRepositoryService().createDeployment().addString("invalid.bpmn20.xml",xml).deploy()));
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM we_assignment_definition_dependencies",Long.class));
    }
    @Test void deletionWaitsForRootRegistrationAndThenObservesTheCommittedRound() throws Exception {
        var target=target(false,true); var locked=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1); var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var registration=pool.submit(()->transaction.executeWithoutResult(status->{
                new AssignmentDefinitionRetention(jdbc).start(target.definition());
                var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var id=UUID.randomUUID();
                cases.createCase(id,"reserved-root","original","BU");
                cases.insert(new AssignmentCaseRegistry.Registration("reserved-root",id,UUID.randomUUID(),target.definition(),List.of()),null,null,"synthetic");
                locked.countDown();
                try { assertTrue(release.await(5,java.util.concurrent.TimeUnit.SECONDS)); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertTrue(locked.await(5,java.util.concurrent.TimeUnit.SECONDS));
            var deletion=pool.submit(()->engine.getRepositoryService().deleteDeployment(target.deployment(),true));
            assertThrows(java.util.concurrent.TimeoutException.class,()->deletion.get(200,java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown(); registration.get(5,java.util.concurrent.TimeUnit.SECONDS);
            var failure=assertThrows(java.util.concurrent.ExecutionException.class,()->deletion.get(5,java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(AssignmentFailure.class,failure.getCause());
            assertNotNull(engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionId(target.definition()).singleResult());
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    private String publishCaller(String called,boolean tracked) {
        String xml=callerXml(called,tracked);
        if (tracked) xml=new com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl(engine.getRepositoryService(),
                org.mockito.Mockito.mock(AssignmentIdentityClient.class),new ObjectMapper(),org.mockito.Mockito.mock(com.workflow.client.AdminCenterClient.class)).compile(xml);
        String compiled=xml;
        var deployment=AssignmentCompiledDeployment.deploy("caller.bpmn20.xml",compiled,
                ()->engine.getRepositoryService().createDeployment().addString("caller.bpmn20.xml",compiled).deploy());
        return engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
    }
    private String callerXml(String called,boolean tracked) {
        String policy=tracked?"""
                <extensionElements><flowable:properties><flowable:property name="assignmentExclusion"
                value="{&quot;schemaVersion&quot;:1,&quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,
                &quot;excludeSelectedNodeHandlers&quot;:false,&quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/>
                </flowable:properties></extensionElements>
                """:"";
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="synthetic">
                <process id="caller_%s" isExecutable="true"><startEvent id="start"/><userTask id="work">%s</userTask>
                <callActivity id="called" calledElement="%s" flowable:calledElementType="id"/><endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/><sequenceFlow id="b" sourceRef="work" targetRef="called"/>
                <sequenceFlow id="c" sourceRef="called" targetRef="end"/></process></definitions>
                """.formatted(UUID.randomUUID().toString().replace("-",""),policy,called);
    }
    private Target target(boolean tracked,boolean finish) {
        String key="retention_"+UUID.randomUUID().toString().replace("-","");
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml","""
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="synthetic">
                <process id="%s" isExecutable="true"><startEvent id="start"/><userTask id="work"/><endEvent id="end"/>
                <sequenceFlow id="a" sourceRef="start" targetRef="work"/><sequenceFlow id="b" sourceRef="work" targetRef="end"/>
                </process></definitions>
                """.formatted(key)).deploy();
        var definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult();
        var instance=engine.getRuntimeService().startProcessInstanceById(definition.getId());
        if (tracked) {
            var cases=new AssignmentCaseRepository(jdbc,new ObjectMapper()); var id=UUID.randomUUID();
            cases.createCase(id,instance.getId(),"original","BU");
            cases.insert(new AssignmentCaseRegistry.Registration(instance.getId(),id,UUID.randomUUID(),definition.getId(),List.of()),null,null,key);
        }
        if (finish) engine.getTaskService().complete(engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult().getId());
        return new Target(deployment.getId(),definition.getId(),instance.getId());
    }
    record Target(String deployment,String definition,String instance) { }
}
