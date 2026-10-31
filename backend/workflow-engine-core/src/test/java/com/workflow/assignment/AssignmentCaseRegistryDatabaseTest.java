package com.workflow.assignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.functionunit.CatalogSnapshotReader;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@EnabledIfEnvironmentVariable(named = "ASSIGNMENT_TEST_JDBC_URL", matches = ".+")
class AssignmentCaseRegistryDatabaseTest extends AssignmentPostgresFixture {
    AssignmentDefinitionPolicy definitions;
    AssignmentCaseRegistry registry;
    String childDefinition;
    String parentDefinition;

    @BeforeAll void configure() {
        openEngine();
        var json = new ObjectMapper();
        var repository = engine.getRepositoryService();
        var compiler = new com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl(repository, mock(AssignmentIdentityClient.class), json,
                mock(com.workflow.client.AdminCenterClient.class));
        String childXml = compiler.compile(model("protected_child", "<userTask id=\"work\">" + policy() + "</userTask>"));
        var child = repository.createDeployment().addString("child.bpmn20.xml", childXml).deploy();
        childDefinition = repository.createProcessDefinitionQuery().deploymentId(child.getId()).singleResult().getId();
        String parentXml = model("protected_parent", "<callActivity id=\"work\" calledElement=\"" + childDefinition
                + "\" flowable:calledElementType=\"id\"/>");
        var parent = repository.createDeployment().addString("parent.bpmn20.xml", compiler.compile(parentXml)).deploy();
        parentDefinition = repository.createProcessDefinitionQuery().deploymentId(parent.getId()).singleResult().getId();
        definitions = new AssignmentDefinitionPolicy(repository);
        registry = new com.workflow.assignment.impl.AssignmentCaseRegistryImpl(jdbc, new AssignmentCaseRepository(jdbc, json), engine.getRuntimeService(), definitions, mock(CatalogSnapshotReader.class));
        engine.getRuntimeService().addEventListener(new RegistryProbe(), FlowableEngineEventType.TASK_CREATED,
                FlowableEngineEventType.PROCESS_COMPLETED, FlowableEngineEventType.PROCESS_CANCELLED);
    }

    @Test void firstChildTaskIsRegisteredUnderTheRootWithoutAnyPortalMirror() {
        var root = start(parentDefinition);
        var task = engine.getTaskService().createTaskQuery().processDefinitionKey("protected_child").list().stream()
                .filter(t -> registry.instance(t.getProcessInstanceId()).orElseThrow().caseId().equals(root.caseId()))
                .findFirst().orElseThrow();
        var child = registry.instance(task.getProcessInstanceId()).orElseThrow();
        assertEquals(root.caseId(), child.caseId());
        assertEquals(root.roundId(), child.roundId());
        assertEquals(1, child.calls().size());
        assertEquals("work", child.calls().get(0).get("node"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM we_assignment_task_states WHERE task_id=?", Integer.class, task.getId()));
    }

    @Test void reopenedRootKeepsTheOriginalDefinitionAndCaseButCreatesANewRound() {
        var original = start(childDefinition);
        var oldTask = engine.getTaskService().createTaskQuery().processInstanceId(original.instance()).singleResult();
        engine.getTaskService().complete(oldTask.getId());
        var reopened = transaction.execute(status -> {
            var next = registry.reopen(original.instance());
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(next.definition())
                    .predefineProcessInstanceId(next.instance()).start();
            return next;
        });
        assertNotNull(reopened);
        assertEquals(original.definition(), reopened.definition());
        assertEquals(original.caseId(), reopened.caseId());
        assertNotEquals(original.roundId(), reopened.roundId());
        assertNotEquals(original.instance(), reopened.instance());
        assertNotNull(engine.getTaskService().createTaskQuery().processInstanceId(reopened.instance()).singleResult());
    }

    @Test void protectedDirectStartsCannotInventLegacyContextWhenTheRegistryIsMissing() {
        assertThrows(RuntimeException.class, () -> engine.getRuntimeService().startProcessInstanceById(childDefinition));
    }

    @Test void compiledChildPolicyPropagatesTrackingToItsParent() {
        assertTrue(definitions.tracked(childDefinition));
        assertTrue(definitions.tracked(parentDefinition));
        assertTrue(definitions.node(childDefinition, "work").excludeInitiator());
    }

    @Test void protectedRootStarterOverridesClientInitiatorAndRegistersBeforeTheFirstChildTask() {
        var qualification = mock(AssignmentRootQualification.class);
        var snapshot = new AssignmentRootQualification.Prepared("canonical-initiator", "bu-id", "original-bu", 0, java.time.Instant.now().plusSeconds(5));
        when(qualification.prepare(any())).thenAnswer(call -> {
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            return snapshot;
        });
        var starter = new com.workflow.assignment.impl.AssignmentRootStarterImpl(engine.getRuntimeService(), registry,
                qualification, definitions, transaction.getTransactionManager());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"history",engine.getHistoryService());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"runtimeSupport",testRuntimeSupport());
        var request = new com.workflow.dto.request.StartProcessRequest();
        request.setProcessDefinitionKey("protected_parent");
        request.setStartUserId("canonical-initiator");
        request.setVariables(java.util.Map.of("initiator", "untrusted-variable", "activeBusinessUnitId", "untrusted-bu"));
        var root = starter.start(request, parentDefinition, process -> {
            var registration = registry.instance(process.getId()).orElseThrow();
            assertEquals("canonical-initiator", jdbc.queryForObject("SELECT initiator_user_id FROM we_assignment_cases WHERE case_id=?", String.class, registration.caseId()));
        });
        assertEquals("canonical-initiator", engine.getRuntimeService().getVariable(root.getId(), "initiator"));
        assertEquals("bu-id", engine.getRuntimeService().getVariable(root.getId(), "activeBusinessUnitId"));
        var registration = registry.instance(root.getId()).orElseThrow();
        assertEquals("original-bu", jdbc.queryForObject("SELECT start_bu_code FROM we_assignment_cases WHERE case_id=?", String.class, registration.caseId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM we_assignment_task_states WHERE case_id=?", Integer.class, registration.caseId()));
    }
    @Test void closedRuntimeCapabilityCannotBeBypassedByStartingAnAlreadyDeployedProtectedDefinition() {
        var qualification=mock(AssignmentRootQualification.class);
        when(qualification.prepare(any())).thenReturn(new AssignmentRootQualification.Prepared("canonical-initiator",null,null,0,java.time.Instant.now().plusSeconds(5)));
        var starter=new com.workflow.assignment.impl.AssignmentRootStarterImpl(engine.getRuntimeService(),registry,qualification,definitions,transaction.getTransactionManager());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"history",engine.getHistoryService());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"runtimeSupport",new AssignmentUnavailableRuntime());
        var request=new com.workflow.dto.request.StartProcessRequest(); request.setProcessDefinitionKey("protected_child");
        long before=jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases",Long.class);
        var failure=assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->starter.start(request,childDefinition,created->{}));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases",Long.class));
    }
    @Test void closedRuntimeCapabilityCannotBeBypassedByOpeningANewRound() {
        var original=endedRoot();
        var component=reopenComponent("canonical-initiator",false);
        org.springframework.test.util.ReflectionTestUtils.setField(component,"runtimeSupport",new AssignmentUnavailableRuntime());
        long before=jdbc.queryForObject("SELECT count(*) FROM we_assignment_instances WHERE case_id=?",Long.class,original.caseId());
        var failure=assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->component.reopen(original.instance(),
                new com.platform.common.assignment.AssignmentReopenReceipt.Request(java.util.UUID.randomUUID(),"canonical-initiator")));
        assertEquals("ASSIGNMENT_ENGINE_UNSUPPORTED",failure.getDetails().get("reason"));
        assertEquals(before,jdbc.queryForObject("SELECT count(*) FROM we_assignment_instances WHERE case_id=?",Long.class,original.caseId()));
    }

    @Test void failedRootFinalCheckDoesNotLeaveAPartialCaseOrStartATask() {
        var qualification = mock(AssignmentRootQualification.class);
        var snapshot = new AssignmentRootQualification.Prepared("canonical-initiator", null, null, 0, java.time.Instant.now());
        when(qualification.prepare(any())).thenReturn(snapshot);
        org.mockito.Mockito.doThrow(new com.platform.common.assignment.AssignmentFailure(
                com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED)).when(qualification).check(snapshot);
        var starter = new com.workflow.assignment.impl.AssignmentRootStarterImpl(engine.getRuntimeService(), registry,
                qualification, definitions, transaction.getTransactionManager());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"history",engine.getHistoryService());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"runtimeSupport",testRuntimeSupport());
        long before = jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases", Long.class);
        var request = new com.workflow.dto.request.StartProcessRequest(); request.setProcessDefinitionKey("protected_child");
        assertThrows(com.platform.common.assignment.AssignmentFailure.class, () -> starter.start(request, childDefinition, process -> fail("Invalid start must not notify")));
        assertEquals(before, jdbc.queryForObject("SELECT count(*) FROM we_assignment_cases", Long.class));
    }

    @Test void initialPortalValuesIncludeAutomaticExecutionBeforeTheFirstTask() {
        var compiler=new com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl(engine.getRepositoryService(),mock(AssignmentIdentityClient.class),
                new ObjectMapper(),mock(com.workflow.client.AdminCenterClient.class));
        String key="initial_values_"+java.util.UUID.randomUUID().toString().replace("-","");
        String properties=policy().replace("</extensionElements>",
                "<flowable:executionListener event=\"start\" expression=\"${execution.setVariable('renamed_approver', 'returned-person')}\"/></extensionElements>");
        var deployment=engine.getRepositoryService().createDeployment().addString(key+".bpmn20.xml",
                compiler.compile(model(key,"<userTask id=\"work\">"+properties+"</userTask>"))).deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        var qualification=mock(AssignmentRootQualification.class);
        when(qualification.prepare(any())).thenReturn(new AssignmentRootQualification.Prepared("canonical-initiator",null,null,0,java.time.Instant.now().plusSeconds(10)));
        var starter=new com.workflow.assignment.impl.AssignmentRootStarterImpl(engine.getRuntimeService(),registry,qualification,definitions,transaction.getTransactionManager());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"history",engine.getHistoryService());
        org.springframework.test.util.ReflectionTestUtils.setField(starter,"runtimeSupport",testRuntimeSupport());
        var request=new com.workflow.dto.request.StartProcessRequest(); request.setProcessDefinitionKey(key);
        request.setVariables(java.util.Map.of("renamed_approver","original-person","other","keep"));
        var instance=starter.start(request,definition,created->{});
        assertEquals("returned-person",engine.getRuntimeService().getVariable(instance.getId(),"renamed_approver"));
        assertEquals("returned-person",request.getVariables().get("renamed_approver"));
        assertEquals("keep",request.getVariables().get("other"));
        assertEquals("canonical-initiator",request.getVariables().get("initiator"));
    }

    @Test void originalInitiatorReopensOneRoundAndAnIdenticalRetryReturnsThatRound() {
        var original = endedRoot();
        var component = reopenComponent("canonical-initiator", false);
        var command = new com.platform.common.assignment.AssignmentReopenReceipt.Request(java.util.UUID.randomUUID(), "canonical-initiator");
        var reopened = component.reopen(original.instance(), command);
        org.springframework.test.util.ReflectionTestUtils.setField(component,"runtimeSupport",new AssignmentUnavailableRuntime());
        assertEquals(reopened, component.reopen(original.instance(), command));
        var wrongActor=new com.platform.common.assignment.AssignmentReopenReceipt.Request(command.operationId(),"another-actor");
        assertThrows(com.platform.common.assignment.AssignmentFailure.class,()->component.reopen(original.instance(),wrongActor));
        assertEquals(original.definition(), reopened.processDefinitionId());
        assertEquals(original.caseId(), registry.instance(reopened.processInstanceId()).orElseThrow().caseId());
        assertEquals("retained", engine.getRuntimeService().getVariable(reopened.processInstanceId(), "businessValue"));
        assertEquals("canonical-initiator", engine.getRuntimeService().getVariable(reopened.processInstanceId(), "initiator"));
        assertEquals(1, new AssignmentResponsibilityRepository(jdbc, new ObjectMapper()).effective(original.caseId()).size());
        assertTrue(component.pending(100).stream().anyMatch(receipt -> receipt.operationId().equals(command.operationId())));
        assertThrows(IllegalStateException.class, () -> component.mirrored(command.operationId()));
        transaction.executeWithoutResult(status -> new com.platform.common.assignment.AssignmentCaseFence(jdbc)
                .portalReopenApplied(original.caseId(), command.operationId()));
        jdbc.update("UPDATE we_assignment_cases SET next_attempt_at=clock_timestamp()+interval '1 hour' WHERE case_id=?",original.caseId());
        component.mirrored(command.operationId());
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at<=clock_timestamp() FROM we_assignment_cases WHERE case_id=?",Boolean.class,original.caseId()));
        assertTrue(component.pending(100).stream().noneMatch(receipt -> receipt.operationId().equals(command.operationId())));
    }

    @Test void systemAdministratorMayReopenButDoesNotBecomeTheOriginalInitiator() {
        var original = endedRoot();
        var reopened = reopenComponent("administrator", true).reopen(original.instance(),
                new com.platform.common.assignment.AssignmentReopenReceipt.Request(java.util.UUID.randomUUID(), "administrator"));
        assertEquals("administrator", reopened.actor());
        assertEquals("canonical-initiator", reopened.variables().get("initiator"));
        assertEquals("canonical-initiator", engine.getHistoryService().createHistoricProcessInstanceQuery()
                .processInstanceId(reopened.processInstanceId()).singleResult().getStartUserId());
        assertEquals("canonical-initiator", new AssignmentCaseRepository(jdbc, new ObjectMapper()).origin(original.caseId()).initiator());
    }

    @Test void unrelatedActiveUserCannotReopenSomeoneElseRequest() {
        var original = endedRoot();
        var error = assertThrows(com.platform.common.assignment.AssignmentFailure.class, () -> reopenComponent("unrelated", false)
                .reopen(original.instance(), new com.platform.common.assignment.AssignmentReopenReceipt.Request(java.util.UUID.randomUUID(), "unrelated")));
        assertEquals("ASSIGNMENT_EXCLUDED", error.getDetails().get("reason"));
        assertFalse(new AssignmentCaseRepository(jdbc, new ObjectMapper()).hasActiveRoot(original.caseId()));
    }

    @Test void anUnsynchronizedCompletionCannotBeBypassedByReopeningTheRequest() {
        var original = endedRoot();
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?", original.caseId());
        var error = assertThrows(com.platform.common.assignment.AssignmentFailure.class, () -> reopenComponent("canonical-initiator", false)
                .reopen(original.instance(), new com.platform.common.assignment.AssignmentReopenReceipt.Request(java.util.UUID.randomUUID(), "canonical-initiator")));
        assertEquals("ASSIGNMENT_SYNC_PENDING", error.getDetails().get("reason"));
        assertFalse(new AssignmentCaseRepository(jdbc, new ObjectMapper()).hasActiveRoot(original.caseId()));
    }

    private AssignmentCaseRegistry.Registration endedRoot() {
        var original = start(childDefinition);
        engine.getRuntimeService().setVariables(original.instance(), java.util.Map.of("businessValue", "retained", "action", "APPROVE"));
        var task = engine.getTaskService().createTaskQuery().processInstanceId(original.instance()).singleResult();
        transaction.executeWithoutResult(status -> {
            new AssignmentResponsibilityRepository(jdbc, new ObjectMapper()).completed(task.getId(), "handler", java.util.Set.of("handler"));
            engine.getTaskService().complete(task.getId());
        });
        return original;
    }

    @Test void reopenHintsRespectTheSameOwnerAdministratorAndPendingCaseRules() {
        var source=endedRoot();
        assertTrue(actionReads("canonical-initiator",false).reopen(source.instance(),"canonical-initiator").allowed());
        assertTrue(actionReads("administrator",true).reopen(source.instance(),"administrator").allowed());
        assertFalse(actionReads("unrelated",false).reopen(source.instance(),"unrelated").allowed());
        jdbc.update("UPDATE we_assignment_cases SET accepted_operation_seq=1 WHERE case_id=?",source.caseId());
        assertFalse(actionReads("canonical-initiator",false).reopen(source.instance(),"canonical-initiator").allowed());
    }
    @Test void subprocessMiUsesTheDeclaredChildTableAndEveryConfiguredPrimaryKeyWithoutAPlatformUuid() {
        String xml=model("pk_mi_"+UUID.randomUUID().toString().replace("-",""),"""
                <subProcess id="work"><multiInstanceLoopCharacteristics flowable:collection="rows" flowable:elementVariable="row"/>
                <startEvent id="inner_start"/><userTask id="review">%s</userTask><endEvent id="inner_end"/>
                <sequenceFlow id="inner_a" sourceRef="inner_start" targetRef="review"/>
                <sequenceFlow id="inner_b" sourceRef="review" targetRef="inner_end"/></subProcess>
                """.formatted(policy().replace("</custom:properties>","<custom:property name=\"subTableName\" value=\"renamed_rows\"/></custom:properties>")));
        var compiler=new com.workflow.assignment.impl.AssignmentDeploymentCompilerImpl(engine.getRepositoryService(),mock(AssignmentIdentityClient.class),new ObjectMapper(),mock(com.workflow.client.AdminCenterClient.class));
        var deployment=engine.getRepositoryService().createDeployment().addString("pk-mi.bpmn20.xml",compiler.compile(xml)).deploy();
        String definition=engine.getRepositoryService().createProcessDefinitionQuery().deploymentId(deployment.getId()).singleResult().getId();
        jdbc.execute("CREATE TABLE IF NOT EXISTS sys_function_unit_contents(function_unit_id text,flowable_process_definition_id text)");
        jdbc.update("INSERT INTO sys_function_unit_contents VALUES ('pk-catalog',?)",definition);
        var catalogs=(CatalogSnapshotReader)org.springframework.test.util.ReflectionTestUtils.getField(registry,"catalogs");
        when(catalogs.primaryKeyFields("pk-catalog","renamed_rows")).thenReturn(List.of("zone_code","document_ref"));
        var rows=List.of(java.util.Map.of("zone_code","zone-a","document_ref","doc-a"),java.util.Map.of("zone_code","zone-a","document_ref","doc-b"));
        var root=transaction.execute(status->{
            var registration=registry.newRoot(definition,"canonical-initiator","original-bu");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition).predefineProcessInstanceId(registration.instance()).variables(java.util.Map.of("rows",rows)).start();
            return registration;
        });
        var saved=jdbc.queryForList("SELECT slot_identity::text FROM we_assignment_task_states WHERE process_instance_id=?",String.class,root.instance());
        assertEquals(2,saved.size());
        var keys=saved.stream().map(value->{try{return new ObjectMapper().readTree(value).at("/multiInstances/0/row");}catch(java.io.IOException invalid){throw new IllegalStateException(invalid);}}).collect(java.util.stream.Collectors.toSet());
        assertEquals(rows.stream().map(row->new ObjectMapper().valueToTree(row)).collect(java.util.stream.Collectors.toSet()),keys);
    }
    private AssignmentActionAvailabilityRead actionReads(String actor,boolean administrator) {
        var component=reopenComponent(actor,administrator);
        var preparation=(AssignmentReopenPreparation)org.springframework.test.util.ReflectionTestUtils.getField(component,"preparation");
        return new AssignmentActionAvailabilityRead(mock(AssignmentWriteGuard.class),preparation,new AssignmentCaseRepository(jdbc,new ObjectMapper()),
                mock(AssignmentLegacyHistoryCheck.class),transaction.getTransactionManager());
    }
    private AssignmentReopenComponent reopenComponent(String actor, boolean administrator) {
        var qualification = mock(AssignmentRootQualification.class);
        when(qualification.prepare(any())).thenReturn(new AssignmentRootQualification.Prepared(actor, null, null, 0, java.time.Instant.now().plusSeconds(5)));
        var identities = mock(AssignmentIdentityClient.class);
        when(identities.management(actor)).thenReturn(new com.platform.common.assignment.AssignmentManagementSnapshot(0, actor, true, administrator));
        var cases = new AssignmentCaseRepository(jdbc, new ObjectMapper());
        var preparation = new AssignmentReopenPreparation(cases, qualification, identities, engine.getHistoryService(), engine.getRepositoryService());
        var component=new com.workflow.assignment.impl.AssignmentReopenComponentImpl(registry, cases, preparation, engine.getRuntimeService(), transaction.getTransactionManager());
        org.springframework.test.util.ReflectionTestUtils.setField(component,"runtimeSupport",testRuntimeSupport());
        return component;
    }

    private AssignmentRuntimeSupport testRuntimeSupport() {
        // Owned P0 fixture only; production capability remains contractVersion=0, ready=false.
        return new AssignmentRuntimeSupport();
    }

    private AssignmentCaseRegistry.Registration start(String definition) {
        return transaction.execute(status -> {
            var registration = registry.newRoot(definition, "canonical-initiator", "original-bu");
            engine.getRuntimeService().createProcessInstanceBuilder().processDefinitionId(definition)
                    .predefineProcessInstanceId(registration.instance()).start();
            return registration;
        });
    }

    private class RegistryProbe implements FlowableEventListener {
        @Override public void onEvent(FlowableEvent event) {
            if (event.getType() == FlowableEngineEventType.TASK_CREATED && event instanceof FlowableEntityEvent entity) {
                registry.registerTask((Task) entity.getEntity());
            } else if (event instanceof org.flowable.common.engine.api.delegate.event.FlowableEngineEvent engineEvent) {
                registry.ended(engineEvent.getProcessInstanceId(), event.getType() == FlowableEngineEventType.PROCESS_CANCELLED);
            }
        }
        @Override public boolean isFailOnException() { return true; }
        @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
        @Override public String getOnTransaction() { return null; }
    }

    private static String model(String key, String work) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn"
                    xmlns:custom="http://workflow/custom" targetNamespace="assignment-p0">
                  <process id="%s" isExecutable="true"><startEvent id="start"/>%s<endEvent id="end"/>
                    <sequenceFlow id="s1" sourceRef="start" targetRef="work"/><sequenceFlow id="s2" sourceRef="work" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(key, work);
    }

    private static String policy() {
        return """
                <extensionElements><custom:properties><custom:property name="assignmentExclusion" value="{&quot;schemaVersion&quot;:1,
                &quot;excludeInitiator&quot;:true,&quot;excludePreviousHandlers&quot;:false,&quot;excludeSelectedNodeHandlers&quot;:false,
                &quot;selectedNodeKeys&quot;:[],&quot;backup&quot;:{&quot;enabled&quot;:false}}"/></custom:properties></extensionElements>
                """;
    }
}
