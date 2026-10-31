package com.portal.component;

import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentFailure;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentDelegationSnapshotDatabaseTest extends AssignmentPortalDatabaseFixture {
    AssignmentDelegationSnapshotComponent component;
    AssignmentDelegationSnapshot.Request request;
    @BeforeAll void configureDelegation() throws Exception {
        String sql=Files.readString(Path.of("../../deploy/init-scripts/00-schema/03-user-portal-schema.sql"));
        int start=sql.indexOf("CREATE TABLE IF NOT EXISTS up_delegation_rule (");
        jdbc.execute(sql.substring(start,sql.indexOf(");",start)+2));
        jdbc.execute("ALTER TABLE up_process_instance ADD COLUMN process_definition_key varchar(255), ADD COLUMN priority varchar(32)");
        component=new AssignmentDelegationSnapshotComponent(new com.portal.repository.AssignmentDelegationSnapshotRepository(jdbc),json,transaction.getTransactionManager());
    }
    @BeforeEach void seed() {
        var caseId=UUID.randomUUID();
        jdbc.update("DELETE FROM up_delegation_rule");
        jdbc.update("UPDATE ac_assignment_authority_fence SET revision=7 WHERE scope='platform'");
        jdbc.update("INSERT INTO we_assignment_cases(case_id,initiator_user_id,runtime_contract_version) VALUES(?,'owner',1)",caseId);
        jdbc.update("INSERT INTO up_process_instance(id,process_definition_id,process_definition_key,priority,variables) VALUES(?,'frozen','expense','HIGH','{}')",caseId.toString());
        request=new AssignmentDelegationSnapshot.Request(caseId,"task",caseId.toString(),"frozen","delegate","holder");
    }
    @Test void bothConfiguredFiltersMustMatchTheCommittedRequest() {
        rule("[\"expense\"]","[\"HIGH\"]","clock_timestamp()+interval '1 hour'");
        var snapshot=component.read(request);
        assertTrue(snapshot.granted()); assertNotNull(snapshot.ruleId()); assertEquals(7,snapshot.authorityRevision());
        jdbc.update("UPDATE up_process_instance SET priority='NORMAL' WHERE id=?",request.instanceId());
        assertFalse(component.read(request).granted());
        jdbc.update("UPDATE up_process_instance SET priority='HIGH',process_definition_key='leave' WHERE id=?",request.instanceId());
        assertFalse(component.read(request).granted());
    }
    @Test void expiryCapsTheFinalDecisionAndExpiredOrFutureRulesDeny() {
        rule("[]",null,"clock_timestamp()+interval '5 seconds'");
        var end=jdbc.queryForObject("SELECT end_time AT TIME ZONE current_setting('TimeZone') FROM up_delegation_rule",java.sql.Timestamp.class).toInstant();
        assertEquals(end,component.read(request).validUntil());
        jdbc.update("UPDATE up_delegation_rule SET end_time=clock_timestamp()");
        assertFalse(component.read(request).granted());
        jdbc.update("UPDATE up_delegation_rule SET end_time=NULL,start_time=clock_timestamp()+interval '1 hour'");
        assertFalse(component.read(request).granted());
    }
    @Test void onlyExactActiveDirectPartiesGrantAndEmptyFiltersMeanUnrestricted() {
        rule(null,"[]","NULL"); assertTrue(component.read(request).granted());
        jdbc.update("UPDATE up_delegation_rule SET delegate_id='another'"); assertFalse(component.read(request).granted());
        jdbc.update("UPDATE up_delegation_rule SET delegate_id='delegate',delegator_id='another'"); assertFalse(component.read(request).granted());
        jdbc.update("UPDATE up_delegation_rule SET delegator_id='holder',status='CANCELLED'"); assertFalse(component.read(request).granted());
    }
    @Test void exactEndInstantIsExcludedAndExactStartInstantIsIncluded() {
        rule(null,null,"NULL");
        var now=jdbc.queryForObject("SELECT clock_timestamp()",java.sql.Timestamp.class).toInstant();
        jdbc.update("UPDATE up_delegation_rule SET end_time=?::timestamptz",now.toString());
        var repository=new com.portal.repository.AssignmentDelegationSnapshotRepository(jdbc);
        assertTrue(repository.active(request,now).isEmpty());
        jdbc.update("UPDATE up_delegation_rule SET end_time=NULL,start_time=?::timestamptz",now.toString());
        assertEquals(1,repository.active(request,now).size());
    }
    @Test void malformedFilterAndMissingOrDifferentFrozenMirrorFailClosed() {
        rule("{}",null,"NULL"); assertThrows(AssignmentFailure.class,()->component.read(request));
        jdbc.update("DELETE FROM up_delegation_rule");
        jdbc.update("UPDATE up_process_instance SET process_definition_id='latest' WHERE id=?",request.instanceId());
        assertThrows(AssignmentFailure.class,()->component.read(request));
        jdbc.update("DELETE FROM up_process_instance WHERE id=?",request.instanceId());
        assertThrows(AssignmentFailure.class,()->component.read(request));
    }
    private void rule(String processes,String priorities,String expiry) {
        // Expiry expressions are fixed test-owned literals; business inputs remain bound parameters.
        jdbc.update("INSERT INTO up_delegation_rule(delegator_id,delegate_id,delegation_type,process_types,priority_filter,end_time) "
                +"VALUES('holder','delegate','ALL',?::jsonb,?::jsonb,"+expiry+")",processes,priorities);
    }
}
