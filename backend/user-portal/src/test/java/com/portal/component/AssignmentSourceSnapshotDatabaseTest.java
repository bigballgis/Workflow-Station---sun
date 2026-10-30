package com.portal.component;

import com.platform.common.assignment.*;
import com.portal.repository.AssignmentSourceSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentSourceSnapshotDatabaseTest extends AssignmentPortalDatabaseFixture {
    UUID caseId;
    String instance;
    AssignmentSourceSnapshotComponent component;
    @BeforeEach void seed() {
        caseId=UUID.randomUUID(); instance=caseId.toString();
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version,revision,portal_revision) VALUES(?,'owner',1,3,2)",caseId);
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,process_definition_id,function_unit_code,call_path,state) VALUES(?,?,?,'frozen','fu','[]','ACTIVE')",
                instance,caseId,UUID.randomUUID());
        component=new AssignmentSourceSnapshotComponent(new AssignmentSourceSnapshotRepository(jdbc),json);
    }
    @Test void initialIsExplicitAndDeletedMirrorNeverBecomesInitial() {
        assertEquals(AssignmentSourceSnapshot.State.INITIAL,component.read(request(List.of("reviewer"),null)).state());
        mirror("{\"reviewer\":\"a\"}");
        assertEquals(AssignmentSourceSnapshot.State.READY,component.read(request(List.of("reviewer"),null)).state());
        jdbc.update("DELETE FROM up_process_instance WHERE id=?",instance);
        assertThrows(AssignmentFailure.class,()->component.read(request(List.of("reviewer"),null)));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM up_assignment_source_presence WHERE process_instance_id=?",Integer.class,instance));
    }
    @Test void savedVariableIsFreshAndOnlyRequestedFieldsLeavePortal() {
        mirror("{\"reviewer\":\"a\",\"private\":\"hidden\"}");
        jdbc.update("UPDATE up_process_instance SET variables='{\"reviewer\":\"b\",\"private\":\"hidden\"}' WHERE id=?",instance);
        var snapshot=component.read(request(List.of("reviewer","absent"),null));
        assertEquals("{\"reviewer\":\"b\"}",snapshot.values().toString());
        assertEquals(3,snapshot.caseRevision()); assertEquals(2,snapshot.portalRevision());
    }
    @Test void compositeKeysSelectExactRowRegardlessOfOrderOrOtherBindings() throws Exception {
        mirror("{\"__subTables__\":{\"dw:items\":[{\"region\":\"HK\",\"number\":2,\"reviewer\":\"other\"},{\"region\":\"HK\",\"number\":1,\"reviewer\":\"right\",\"private\":9}],\"dw:else\":[{\"region\":\"HK\",\"number\":1,\"reviewer\":\"wrong\"}]}}");
        var row=new AssignmentSourceSnapshot.Row("dw:items",json.readTree("{\"region\":\"HK\",\"number\":1}"));
        assertEquals("{\"reviewer\":\"right\"}",component.read(request(List.of("reviewer"),row)).values().toString());
    }
    @Test void uuidSelectionDoesNotDependOnBusinessKeyOrArrayIndex() throws Exception {
        mirror("{\"__subTables__\":{\"dw:people\":[{\"platformRowUuid\":\"row-b\",\"person\":\"b\"},{\"platformRowUuid\":\"row-a\",\"person\":\"a\"}]}}");
        var row=new AssignmentSourceSnapshot.Row("dw:people",json.readTree("{\"platformRowUuid\":\"row-a\"}"));
        assertEquals("a",component.read(request(List.of("person"),row)).values().path("person").asText());
    }
    @Test void ambiguousOrRemovedRowAndWrongFrozenContextFailClosed() throws Exception {
        mirror("{\"__subTables__\":{\"dw:items\":[{\"key\":1},{\"key\":1}]}}");
        var row=new AssignmentSourceSnapshot.Row("dw:items",json.readTree("{\"key\":1}"));
        assertThrows(AssignmentFailure.class,()->component.read(request(List.of("reviewer"),row)));
        jdbc.update("UPDATE up_process_instance SET variables='{\"__subTables__\":{\"dw:items\":[]}}' WHERE id=?",instance);
        assertThrows(AssignmentFailure.class,()->component.read(request(List.of("reviewer"),row)));
        assertThrows(AssignmentFailure.class,()->component.read(new AssignmentSourceSnapshot.Request(caseId,instance,"latest",List.of("reviewer"),null)));
        assertThrows(AssignmentFailure.class,()->component.read(new AssignmentSourceSnapshot.Request(UUID.randomUUID(),instance,"frozen",List.of("reviewer"),null)));
    }
    @Test void presenceAndSourceRollbackTogether() {
        transaction.executeWithoutResult(status->{ mirror("{\"reviewer\":\"a\"}"); status.setRollbackOnly(); });
        assertEquals(AssignmentSourceSnapshot.State.INITIAL,component.read(request(List.of("reviewer"),null)).state());
    }
    private AssignmentSourceSnapshot.Request request(List<String> fields,AssignmentSourceSnapshot.Row row) {
        return new AssignmentSourceSnapshot.Request(caseId,instance,"frozen",fields,row);
    }
    private void mirror(String variables) { jdbc.update("INSERT INTO up_process_instance(id,process_definition_id,variables,lock_version) VALUES(?,'frozen',CAST(? AS jsonb),0)",instance,variables); }
}
