package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.functionunit.CatalogSnapshotReader;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentSubTableWriterDatabaseTest extends AssignmentPortalDatabaseFixture {
    final CatalogSnapshotReader catalogs=mock(CatalogSnapshotReader.class);
    AssignmentSubTableWriter writer;
    @BeforeAll void prepare() {
        jdbc.execute("ALTER TABLE up_process_instance ADD COLUMN function_unit_catalog_id varchar(64)");
        jdbc.execute("CREATE TABLE physical_review_rows(zone_code text,document_ref text,parent_ref text,reviewer_ref text,PRIMARY KEY(zone_code,document_ref))");
        writer=new AssignmentSubTableWriter(jdbc,json,catalogs);
    }
    @BeforeEach void resetRows() {
        reset(catalogs);
        jdbc.update("DELETE FROM up_process_instance"); jdbc.update("DELETE FROM physical_review_rows");
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=0 WHERE scope='platform'");
    }
    @Test void jsonOnlyUuidRowChangesOnlyTheSelectedProcessor() throws Exception {
        var key=Map.<String,Object>of("platformRowUuid",UUID.randomUUID().toString());
        setup("json_review_rows",List.of("platformRowUuid"),List.of(row(key,"old"),row(Map.of("platformRowUuid","other"),"other-user")));
        transaction.executeWithoutResult(status->writer.assign(plan("json_review_rows"),null,key));
        var rows=json.readTree(variables()).path("__subTables__").path("dw:json_review_rows");
        assertEquals("target",rows.get(0).path("reviewer_ref").asText());
        assertEquals("other-user",rows.get(1).path("reviewer_ref").asText());
        assertEquals("unchanged",rows.get(0).path("memo").asText());
        assertEquals(1,rows.get(0).path("_wsRowVersion").asInt());
        assertFalse(rows.get(1).has("_wsRowVersion"));
    }
    @Test void compositeBooleanKeyKeepsItsTypeAndSelectsOnlyTheMatchingRow() throws Exception {
        var key=Map.<String,Object>of("tenant_ref","tenant-a","approved_flag",false);
        setup("json_review_rows",List.of("tenant_ref","approved_flag"),List.of(row(key,"old"),
                row(Map.of("tenant_ref","tenant-a","approved_flag",true),"other-user")));
        var result=transaction.execute(status->writer.assign(plan("json_review_rows"),null,key));
        assertEquals(false,((Map<?,?>)result.get("rowKey")).get("approved_flag"));
        var rows=json.readTree(variables()).at("/__subTables__/dw:json_review_rows");
        assertEquals("target",rows.get(0).path("reviewer_ref").asText());
        assertEquals("other-user",rows.get(1).path("reviewer_ref").asText());
    }
    @Test void invalidVersionsCannotMutateTheAssignee() {
        for (Object version:List.of(-1,2147483647,"5",1.5)) {
            resetRows(); var identity=Map.<String,Object>of("document_ref","doc-a");
            var original=row(identity,"old"); original.put("_wsRowVersion",version);
            setup("json_review_rows",List.of("document_ref"),List.of(original));
            assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->writer.assign(plan("json_review_rows"),null,identity)));
            assertFalse(variables().contains("target"));
        }
    }
    @Test void assignmentInvalidatesTheOldRowVersionBeforeAnotherPageCanSave() throws Exception {
        var identity=Map.<String,Object>of("document_ref","renamed-key-a");
        var original=row(identity,"old"); original.put("_wsRowVersion",5);
        setup("json_review_rows",List.of("document_ref"),List.of(original));
        var receipt=transaction.execute(status->writer.assign(plan("json_review_rows"),null,identity));
        assertEquals(5,receipt.get("previousRowVersion")); assertEquals(6,receipt.get("rowVersion"));
        assertEquals("dw:json_review_rows",receipt.get("storeKey")); assertEquals("reviewer_ref",receipt.get("assigneeField"));
        var baseline=json.readValue(variables(),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() { });
        var tables=(Map<String,Object>)baseline.get("__subTables__");
        var submitted=new LinkedHashMap<String,Object>(); submitted.put("dw:json_review_rows",List.of(original));
        var scope=new com.portal.dto.SubTableBindingScope(); scope.setBindingId("7"); scope.setStoreKey("dw:json_review_rows");
        scope.setRowKeys(List.of(identity)); scope.setEmptied(false);
        var binding=new SubTableWriteDesign.Binding("7","dw:json_review_rows",null,null,null,List.of("document_ref"),List.of(),"structuralFk");
        var failure=assertThrows(com.portal.exception.PortalException.class,()->new SubTableBindingScopeGuard().assertAndApply(
                List.of(scope),"synthetic-fu",Map.of(),submitted,tables,Map.of("7",binding)));
        assertEquals("409",failure.getCode());
        assertEquals(6,json.readTree(variables()).at("/__subTables__/dw:json_review_rows/0/_wsRowVersion").asInt());
        assertEquals("target",json.readTree(variables()).at("/__subTables__/dw:json_review_rows/0/reviewer_ref").asText());
    }
    @Test void fullCompositeKeyUpdatesPhysicalAndJsonTogether() throws Exception {
        setupPhysical("parent-a");
        transaction.executeWithoutResult(status->writer.assign(plan("physical_review_rows"),null,key()));
        assertEquals("target",physical()); assertEquals("target",json.readTree(variables()).at("/__subTables__/dw:physical_review_rows/0/reviewer_ref").asText());
    }
    @Test void assignmentUpdatesOnlyMatchingLiveNestedCopiesWithoutChangingArchive() throws Exception {
        var key=Map.<String,Object>of("document_ref","child-a");
        var child=row(key,"old"); child.put("_wsRowVersion",5);
        setup("json_review_rows",List.of("document_ref"),List.of(child));
        var variables=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(variables());
        var tables=(com.fasterxml.jackson.databind.node.ObjectNode)variables.path("__subTables__");
        tables.set("dw:parents",json.valueToTree(List.of(Map.of("parent_id","parent-a","__subTables__",Map.of(
                "dw:json_review_rows",List.of(child,row(Map.of("document_ref","other"),"other-user")),
                "dw:unrelated",List.of(child))))));
        variables.set("_snapshot",json.valueToTree(Map.of("__subTables__",Map.of("dw:json_review_rows",List.of(child)))));
        jdbc.update("UPDATE up_process_instance SET variables=?::jsonb WHERE id='instance'",variables.toString());
        transaction.executeWithoutResult(status->writer.assign(plan("json_review_rows"),null,key));
        var saved=json.readTree(variables());
        var nested=saved.at("/__subTables__/dw:parents/0/__subTables__/dw:json_review_rows");
        assertEquals("target",nested.get(0).path("reviewer_ref").asText());
        assertEquals(6,nested.get(0).path("_wsRowVersion").asInt());
        assertEquals("unchanged",nested.get(0).path("memo").asText());
        assertEquals("other-user",nested.get(1).path("reviewer_ref").asText());
        assertEquals("old",saved.at("/__subTables__/dw:parents/0/__subTables__/dw:unrelated/0/reviewer_ref").asText());
        assertEquals("old",saved.at("/_snapshot/__subTables__/dw:json_review_rows/0/reviewer_ref").asText());
    }
    @Test void laterFailureRollsBackBothPhysicalAndJsonWrites() {
        setupPhysical("parent-a");
        assertThrows(IllegalStateException.class,()->transaction.executeWithoutResult(status->{
            writer.assign(plan("physical_review_rows"),null,key()); throw new IllegalStateException("synthetic rollback");
        }));
        assertEquals("old",physical()); assertTrue(variables().contains("old"));
    }
    @Test void movedPhysicalRowIsRejectedAndJsonRemainsUnchanged() {
        setupPhysical("different-parent");
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->writer.assign(plan("physical_review_rows"),null,key())));
        assertEquals("old",physical()); assertTrue(variables().contains("old"));
    }
    @Test void partialCompositeKeyCannotSelectAnotherRow() {
        setupPhysical("parent-a");
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->writer.assign(plan("physical_review_rows"),null,Map.of("zone_code","zone-a"))));
        assertEquals("old",physical());
    }
    @Test void duplicateJsonIdentityCannotPickAnArbitrarySeat() {
        setup("json_review_rows",List.of("document_ref"),List.of(row(Map.of("document_ref","duplicate"),"one"),row(Map.of("document_ref","duplicate"),"two")));
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->writer.assign(plan("json_review_rows"),null,Map.of("document_ref","duplicate"))));
        assertFalse(variables().contains("target"));
    }
    @Test void targetRevocationEpochCannotUseThePreparedPlan() {
        setupPhysical("parent-a"); jdbc.update("UPDATE ac_assignment_authority_fence SET revision=1 WHERE scope='platform'");
        assertThrows(AssignmentFailure.class,()->transaction.executeWithoutResult(status->writer.assign(plan("physical_review_rows"),null,key())));
        assertEquals("old",physical());
    }
    @Test void writerRequiresTheRealLocalTransaction() {
        assertThrows(AssignmentFailure.class,()->writer.assign(plan("json_review_rows"),null,key()));
    }
    @Test void numericPathIdMatchesTheSameJsonNumberWithoutGuessingItsColumnName() throws Exception {
        setup("json_numeric_rows",List.of("renamed_numeric_key"),List.of(row(Map.of("renamed_numeric_key",7),"old")));
        transaction.executeWithoutResult(status->writer.assign(plan("json_numeric_rows"),7L,null));
        assertEquals("target",json.readTree(variables()).at("/__subTables__/dw:json_numeric_rows/0/reviewer_ref").asText());
    }
    @Test void publicCollaboratorPreparesOutsideTheTransactionAndCommitsBothCaseWatermarks() {
        setupPhysical("parent-a");
        UUID caseId=UUID.randomUUID();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'actor',1)",caseId);
        var transport=mock(com.portal.client.AssignmentEngineTransport.class);
        when(transport.post(anyList(),any(),any(),eq(false))).thenAnswer(call->{
            assertFalse(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive());
            if (call.getArgument(0).equals(List.of("sub-table-plans"))) return plan("physical_review_rows");
            AssignmentWriteDecision.Request request=call.getArgument(1);
            return new AssignmentWriteDecision(1,true,request.taskId(),request.actor(),null,request.operation(),request.requestDigest(),
                    caseId,0,0,0,java.time.Instant.now().plusSeconds(30),"instance");
        });
        var guard=new com.portal.component.impl.AssignmentPortalWriteGuardImpl(transport,jdbc);
        com.portal.testsupport.AssignmentCoordinationTestSupport.stubVerified(guard);
        org.springframework.test.util.ReflectionTestUtils.setField(guard,"entities",mock(jakarta.persistence.EntityManager.class));
        var mine=mock(MineTaskListCache.class); var overlays=mock(MiOverlayComponent.class);
        doAnswer(call->{
            assertEquals("target",admin.queryForObject("SELECT reviewer_ref FROM "+com.platform.common.jdbc.SqlIdentifiers.requireIdentifier(schema)+".physical_review_rows",String.class));
            return null;
        }).when(mine).invalidate();
        var service=new AssignmentSubTableAssignment(transport,new AssignmentPortalWriteExecutor(guard,transaction.getTransactionManager()),writer,mine,overlays);
        var task=com.portal.dto.TaskInfo.builder().taskId("task").processInstanceId("instance").processDefinitionId("definition").build();
        assertTrue(service.assign(task,null,key(),"target","actor").orElseThrow().containsKey("success"));
        assertEquals("target",physical());
        assertEquals(1L,jdbc.queryForObject("SELECT revision FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
        assertEquals(1L,jdbc.queryForObject("SELECT portal_revision FROM we_assignment_cases WHERE case_id=?",Long.class,caseId));
        verify(mine).invalidate(); verify(overlays).invalidate("instance");
    }
    private void setupPhysical(String parent) {
        setup("physical_review_rows",List.of("zone_code","document_ref"),List.of(row(key(),"old")));
        jdbc.update("INSERT INTO physical_review_rows VALUES(?,?,?,?)","zone-a","doc-a",parent,"old");
    }
    private void setup(String table,List<String> keys,List<Map<String,Object>> rows) {
        when(catalogs.dataTable("catalog",table)).thenReturn(Optional.of(Map.of("fields",List.of(
                Map.of("fieldName","reviewer_ref"),Map.of("fieldName","parent_ref","isForeignKey",true)))));
        when(catalogs.primaryKeyFields("catalog",table)).thenReturn(keys);
        var vars=json.valueToTree(Map.of("__subTables__",Map.of("dw:"+table,rows),"unrelated","keep"));
        jdbc.update("INSERT INTO up_process_instance(id,process_definition_id,function_unit_catalog_id,variables,lock_version) VALUES('instance','definition','catalog',?::jsonb,0)",vars.toString());
    }
    private Map<String,Object> row(Map<String,?> key,String user) {
        var row=new LinkedHashMap<String,Object>(key); row.put("reviewer_ref",user); row.put("parent_ref","parent-a"); row.put("memo","unchanged"); return row;
    }
    private Map<String,Object> key() { return Map.of("zone_code","zone-a","document_ref","doc-a"); }
    private AssignmentSubTablePlan plan(String table) { return new AssignmentSubTablePlan(true,"task","instance","definition",table,"reviewer_ref","parent_ref","target",0); }
    private String physical() { return jdbc.queryForObject("SELECT reviewer_ref FROM physical_review_rows",String.class); }
    private String variables() { return jdbc.queryForObject("SELECT variables::text FROM up_process_instance WHERE id='instance'",String.class); }
}
