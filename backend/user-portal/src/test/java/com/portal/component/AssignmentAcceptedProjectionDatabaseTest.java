package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.portal.component.impl.AssignmentPortalSyncImpl;
import com.portal.repository.AssignmentAcceptedProjectionRepository;
import com.portal.repository.AssignmentPortalSyncRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentAcceptedProjectionDatabaseTest extends AssignmentPortalDatabaseFixture {
    final OwnerFieldComponent owners=mock(OwnerFieldComponent.class);
    final MineTaskListCache mine=new MineTaskListCache();
    final MiOverlayComponent overlays=mock(MiOverlayComponent.class);
    AssignmentAcceptedProjectionRepository repository;
    AssignmentPortalSyncImpl sync;
    UUID caseId;
    String root;
    final Instant ended=Instant.parse("2026-10-28T02:00:00Z");
    @BeforeAll void projectionColumns() {
        jdbc.execute("ALTER TABLE up_process_instance ADD COLUMN process_instance_id varchar(64),ADD COLUMN process_definition_key varchar(255),"
                +"ADD COLUMN process_definition_name varchar(255),ADD COLUMN function_unit_catalog_id varchar(64),ADD COLUMN function_unit_code varchar(255),"
                +"ADD COLUMN function_unit_version_label varchar(32),ADD COLUMN start_user_id varchar(64),ADD COLUMN initiator_id varchar(64),"
                +"ADD COLUMN parent_process_instance_id varchar(64),ADD COLUMN call_activity_id varchar(255),ADD COLUMN status varchar(32),"
                +"ADD COLUMN current_node varchar(255),ADD COLUMN current_assignee varchar(64),ADD COLUMN candidate_users varchar(500),"
                +"ADD COLUMN end_time timestamp,ADD COLUMN completed_at timestamp,ADD COLUMN business_key varchar(255),ADD COLUMN title varchar(200),"
                +"ADD COLUMN start_time timestamp,ADD COLUMN started_at timestamp");
    }
    @BeforeEach void seed() {
        reset(owners,overlays); mine.invalidate(); caseId=UUID.randomUUID(); root=caseId.toString();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version,accepted_operation_seq) VALUES(?,'original',1,1)",caseId);
        jdbc.update("INSERT INTO up_process_instance(id,process_definition_id,process_definition_key,function_unit_catalog_id,function_unit_code,"
                +"function_unit_version_label,start_user_id,variables,lock_version,status) VALUES(?,'frozenA','processA','catalogA','FUA','1.0.0','original',"
                +"'{\"a\":1,\"disjoint\":\"preserved\"}',0,'RUNNING')",root);
        repository=new AssignmentAcceptedProjectionRepository(jdbc,json);
        sync=component(repository);
        doAnswer(call->{
            Map<String,Object> values=call.getArgument(1);
            values.put("handler","RUNNING".equals(call.getArgument(4))?"user:"+call.getArgument(2):"");return null;
        }).when(owners).applyAssigneeSnapshot(anyString(),anyMap(),nullable(String.class),nullable(String.class),anyString(),any(MiOuterStepResolver.OuterLookup.class));
    }
    @Test void terminalStateAndOriginalSnapshotApplyAtomicallyWithoutCopyingStaleEngineBusinessValues() {
        var accepted=receipt(List.of(state(root,null,"frozenA","COMPLETED",Map.of("a",999,"approvalStatus","REJECTED"),List.of())));
        transaction.executeWithoutResult(status->sync.apply(accepted));
        var values=new AssignmentPortalSyncRepository(jdbc,json).variables(root).values();
        assertEquals(2,values.path("a").asInt()); assertEquals("preserved",values.path("disjoint").asText());
        assertEquals("original accepted",values.path("_snapshot_task").path("value").asText());
        assertEquals("REJECTED",jdbc.queryForObject("SELECT status FROM up_process_instance WHERE id=?",String.class,root));
        assertEquals("",values.path("handler").asText());
        assertEquals(java.time.LocalDateTime.ofInstant(ended,java.time.ZoneId.systemDefault()),jdbc.queryForObject("SELECT end_time FROM up_process_instance WHERE id=?",java.time.LocalDateTime.class,root));
        jdbc.update("UPDATE up_process_instance SET variables=jsonb_set(variables,'{disjoint}','\"later\"') WHERE id=?",root);
        transaction.executeWithoutResult(status->sync.apply(accepted));
        assertEquals("later",new AssignmentPortalSyncRepository(jdbc,json).variables(root).values().path("disjoint").asText());
        verify(owners,times(1)).applyAssigneeSnapshot(anyString(),anyMap(),nullable(String.class),nullable(String.class),anyString(),any(MiOuterStepResolver.OuterLookup.class));
    }
    @Test void committedRecoveryEvictsMineAndAffectedMiCachesOnlyAfterTheBusinessCommit() {
        mine.put("synthetic-cache",List.of(com.portal.dto.TaskInfo.builder().taskId("old-task").build()));
        var accepted=receipt(List.of(state(root,null,"frozenA","COMPLETED",Map.of(),List.of())));
        transaction.executeWithoutResult(status->{
            sync.apply(accepted);
            assertNotNull(mine.get("synthetic-cache")); verifyNoInteractions(overlays);
        });
        assertNull(mine.get("synthetic-cache")); verify(overlays).invalidate(root);
        transaction.executeWithoutResult(status->sync.apply(accepted));
        verify(overlays,times(1)).invalidate(root);
    }
    @Test void newlyCalledUnitsUseTheirOwnFrozenVersionsAndKeepTheOriginalInitiator() {
        String child=UUID.randomUUID().toString();
        var work=new AssignmentAcceptedProcessState.Task("next","work","Review","next-user",List.of(),child,null);
        var accepted=receipt(List.of(state(root,null,"frozenA","ACTIVE",Map.of(),List.of(work)),
                state(child,root,"frozenB","ACTIVE",Map.of("functionUnitId","catalogB","functionUnitCode","FUB","functionUnitVersionLabel","2.0.0","input","frozen input"),List.of(work))));
        transaction.executeWithoutResult(status->sync.apply(accepted));
        var childRow=repository.load(Set.of(child)).get(child).mirror();
        assertEquals("catalogB",childRow.getFunctionUnitCatalogId()); assertEquals("FUB",childRow.getFunctionUnitCode());
        assertEquals("original",childRow.getStartUserId()); assertEquals(root,childRow.getParentProcessInstanceId());
        assertEquals("frozen input",childRow.getVariables().get("input")); assertEquals("user:next-user",childRow.getVariables().get("handler"));
        assertEquals("Review",jdbc.queryForObject("SELECT current_node FROM up_process_instance WHERE id=?",String.class,root));
        assertEquals(1,jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
    }
    @Test void aProjectionFailureRollsBackStageWritesNewMirrorsAuditAndTheReceiptWatermark() {
        var failing=spy(repository);
        doThrow(new IllegalStateException("synthetic projection failure")).when(failing).save(anyList());
        sync=component(failing);
        String child=UUID.randomUUID().toString();
        var accepted=receipt(List.of(state(root,null,"frozenA","ACTIVE",Map.of(),List.of()),
                state(child,root,"frozenB","ACTIVE",Map.of("functionUnitId","catalogB","functionUnitCode","FUB","functionUnitVersionLabel","2.0.0"),List.of())));
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->sync.apply(accepted)));
        assertEquals(1,new AssignmentPortalSyncRepository(jdbc,json).variables(root).values().path("a").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_process_instance WHERE id=?",Integer.class,child));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_change_history WHERE process_instance_id=?",Integer.class,root));
        assertEquals(0,jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_assignment_operation_sync WHERE case_id=?",Integer.class,caseId));
    }
    @Test void malformedFrozenAssociationCannotCreateAChildOrAdvanceTheLedger() {
        var accepted=receipt(List.of(state(root,null,"another-definition","ACTIVE",Map.of(),List.of())));
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->sync.apply(accepted)));
        assertEquals(1,new AssignmentPortalSyncRepository(jdbc,json).variables(root).values().path("a").asInt());
        assertEquals(0,jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
    }
    @Test void declaredCallerOutputUsesItsOriginalExpectationAndPreservesOtherSavedValues() {
        jdbc.update("UPDATE up_process_instance SET variables=variables || '{\"destination\":\"old\"}'::jsonb WHERE id=?",root);
        var original=receipt(List.of(state(root,null,"frozenA","ACTIVE",Map.of(),List.of())));
        var accepted=withOutput(original);
        transaction.executeWithoutResult(status->sync.apply(accepted));
        var values=new AssignmentPortalSyncRepository(jdbc,json).variables(root).values();
        assertEquals("new",values.path("destination").asText()); assertEquals("preserved",values.path("disjoint").asText()); assertEquals(2,values.path("a").asInt());
    }
    @Test void aConflictingCallerFieldRollsBackTheCompletingTasksStageSnapshotAndAudit() {
        jdbc.update("UPDATE up_process_instance SET variables=variables || '{\"destination\":\"later edit\"}'::jsonb WHERE id=?",root);
        var accepted=withOutput(receipt(List.of(state(root,null,"frozenA","ACTIVE",Map.of(),List.of()))));
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->sync.apply(accepted)));
        var values=new AssignmentPortalSyncRepository(jdbc,json).variables(root).values();
        assertEquals("later edit",values.path("destination").asText()); assertEquals(1,values.path("a").asInt()); assertFalse(values.has("_snapshot_task"));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM up_change_history WHERE process_instance_id=?",Integer.class,root));
        assertEquals(0,jdbc.queryForObject("SELECT portal_applied_operation_seq FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
    }
    private AssignmentOperationReceipt withOutput(AssignmentOperationReceipt original) {
        var patch=new AssignmentRelatedVariablePatch(root,"frozenA",AssignmentVariablePatch.between(json.createObjectNode().put("destination","old"),json.createObjectNode().put("destination","new")));
        var payload=new LinkedHashMap<>(original.acceptedEffect());
        payload.put(AssignmentRelatedVariablePatch.EFFECT_KEY,JsonUtils.getObjectMapper().convertValue(List.of(patch),new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() { }));
        return new AssignmentOperationReceipt(original.operationId(),original.caseId(),original.taskId(),original.actorUserId(),original.representedUserId(),original.operation(),
                original.requestDigest(),original.operationSeq(),original.portalBaseRevision(),payload,original.state());
    }
    private AssignmentPortalSyncImpl component(AssignmentAcceptedProjectionRepository source) {
        var value=new AssignmentPortalSyncImpl(new AssignmentPortalSyncRepository(jdbc,json),jdbc,
                new AssignmentAcceptedProjection(source,owners,new ProcessTerminalStatusResolver()),new com.portal.repository.AssignmentPostActionRepository(jdbc,json),
                new AssignmentCompletionCacheInvalidation(mine,overlays));
        ReflectionTestUtils.setField(value,"entities",mock(jakarta.persistence.EntityManager.class));
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(value); return value;
    }
    private AssignmentAcceptedProcessState state(String id,String parent,String definition,String status,Map<String,Object> vars,List<AssignmentAcceptedProcessState.Task> tasks) {
        return new AssignmentAcceptedProcessState(id,definition,parent==null?"processA":"processB","Process",parent,parent==null?null:"call",
                status,null,null,ended.minusSeconds(100),"ACTIVE".equals(status)?null:ended,vars,tasks);
    }
    private AssignmentOperationReceipt receipt(List<AssignmentAcceptedProcessState> states) {
        var before=json.createObjectNode().put("a",1); var after=json.createObjectNode().put("a",2);
        after.putObject("_snapshot_task").put("value","original accepted");
        var audit=new AssignmentCompletionRequest.AuditChange("a","1","2",AssignmentCompletionRequest.AuditKind.FIELD_UPDATE,null,null,ended,null,null);
        var effect=new AssignmentCompletionRequest.Effect(root,AssignmentVariablePatch.between(before,after),"review","b".repeat(64),List.of(audit),Map.of());
        Map<String,Object> payload=JsonUtils.getObjectMapper().convertValue(effect,new com.fasterxml.jackson.core.type.TypeReference<>() { });
        payload.put(AssignmentAcceptedProcessState.EFFECT_KEY,JsonUtils.getObjectMapper().convertValue(states,new com.fasterxml.jackson.core.type.TypeReference<List<Map<String,Object>>>() { }));
        payload.put(AssignmentRelatedVariablePatch.EFFECT_KEY,List.of());
        return new AssignmentOperationReceipt(UUID.randomUUID(),caseId,"task","actor",null,"APPROVE","a".repeat(64),1,0,payload,"PENDING_SYNC");
    }
}
