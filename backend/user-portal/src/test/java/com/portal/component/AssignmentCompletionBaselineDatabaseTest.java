package com.portal.component;

import com.platform.common.assignment.AssignmentFailure;
import com.portal.repository.AssignmentCompletionBaselineRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentCompletionBaselineDatabaseTest extends AssignmentPortalDatabaseFixture {
    AssignmentCompletionBaselineRepository repository;
    @BeforeAll void prepareSchema() {
        jdbc.execute("ALTER TABLE up_process_instance ADD COLUMN parent_process_instance_id varchar(64)");
        repository=new AssignmentCompletionBaselineRepository(jdbc,json);
    }
    @BeforeEach void resetRows() { jdbc.update("DELETE FROM up_process_instance"); }

    @Test void capturesOnlyTheOriginalCallerChainAndFrozenDefinitions() {
        insert("root",null,"{\"keep\":\"root\"}"); insert("parent","root","{\"keep\":\"parent\"}");
        insert("source","parent","{\"keep\":\"source\"}"); insert("unrelated","root","{\"secret\":\"not included\"}");
        var captured=repository.capture("source");
        assertEquals(java.util.List.of("source","parent","root"),captured.stream().map(value->value.instanceId()).toList());
        assertEquals("parent-def",captured.get(1).definitionId());
        assertEquals("parent",captured.get(1).values().path("keep").asText());
    }

    @Test void missingSourceOrAncestorCannotProduceAPartialBaseline() {
        assertThrows(AssignmentFailure.class,()->repository.capture("absent"));
        insert("source","absent","{}");
        assertThrows(AssignmentFailure.class,()->repository.capture("source"));
    }

    @Test void retainsCurrentDraftExpectationWithoutCopyingEarlierArchives() {
        insert("source",null,"{\"memo\":\"current\",\"_snapshot_old\":{\"memo\":\"history\"},\"_snapshot_task\":{\"memo\":\"draft\"}}");
        var captured=repository.capture("source","task").get(0).values();
        assertEquals("current",captured.path("memo").asText());
        assertFalse(captured.has("_snapshot_old"));
        assertEquals("draft",captured.path("_snapshot_task").path("memo").asText());
        assertTrue(jdbc.queryForObject("SELECT variables::jsonb ? '_snapshot_old' FROM up_process_instance WHERE id='source'",Boolean.class));
    }

    @Test void cyclesFailWithoutRecursiveQueryGrowth() {
        insert("one","two","{}"); insert("two","one","{}");
        assertThrows(AssignmentFailure.class,()->repository.capture("one"));
    }

    @Test void malformedVariablesAndMissingFrozenDefinitionFailClosed() {
        insert("array",null,"[]");
        assertThrows(AssignmentFailure.class,()->repository.capture("array"));
        insert("missing-definition",null,"{}");
        jdbc.update("UPDATE up_process_instance SET process_definition_id=NULL WHERE id='missing-definition'");
        assertThrows(AssignmentFailure.class,()->repository.capture("missing-definition"));
    }

    @Test void depthExceedingTheContractLimitCannotBeAccepted() {
        for (int index=0;index<33;index++) insert("level"+index,index==0?null:"level"+(index-1),"{}");
        assertThrows(AssignmentFailure.class,()->repository.capture("level32"));
        assertEquals(32,repository.capture("level31").size());
    }

    private void insert(String id,String parent,String variables) {
        jdbc.update("INSERT INTO up_process_instance(id,parent_process_instance_id,process_definition_id,variables,lock_version) VALUES(?,?,?,?::jsonb,0)",
                id,parent,id+"-def",variables);
    }
}
