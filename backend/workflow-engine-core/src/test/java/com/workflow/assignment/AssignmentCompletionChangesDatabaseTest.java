package com.workflow.assignment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ASSIGNMENT_TEST_JDBC_URL",matches=".+")
class AssignmentCompletionChangesDatabaseTest extends AssignmentPostgresFixture {
    @Test void independentTransactionsCannotLeakTheirAffectedInstancesIntoTheOuterCompletion() {
        var independent=new TransactionTemplate(transaction.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.executeWithoutResult(status->{
            try (var outer=AssignmentCompletionChanges.open("source")) {
                AssignmentCompletionChanges.changed("child");
                independent.executeWithoutResult(inner->{
                    assertThrows(IllegalStateException.class,()->AssignmentCompletionChanges.instances("source"));
                    try (var nested=AssignmentCompletionChanges.open("independent")) {
                        AssignmentCompletionChanges.changed("independent-child");
                        assertEquals(Set.of("independent","independent-child"),AssignmentCompletionChanges.instances("independent"));
                    }
                });
                assertEquals(Set.of("source","child"),AssignmentCompletionChanges.instances("source"));
            }
            // A later REQUIRES_NEW still suspends registered synchronizations after the scope has closed.
            independent.executeWithoutResult(inner->assertThrows(IllegalStateException.class,()->AssignmentCompletionChanges.instances("source")));
        });
        assertThrows(IllegalStateException.class,()->AssignmentCompletionChanges.instances("source"));
    }
    @Test void snapshotIncludesAffectedInstancesAndTheirAncestorsButExcludesUnrelatedSiblingsAndRounds() {
        var json=new com.fasterxml.jackson.databind.ObjectMapper();
        var cases=new AssignmentCaseRepository(jdbc,json); var states=new AssignmentTaskStateRepository(jdbc,json);
        var caseId=UUID.randomUUID(); var round=UUID.randomUUID();
        cases.createCase(caseId,"root","owner","BU");
        insert(cases,"root",null,caseId,round); insert(cases,"child","root",caseId,round);
        insert(cases,"grandchild","child",caseId,round); insert(cases,"sibling","root",caseId,round);
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,process_definition_id,function_unit_code,call_path,state) "
                +"VALUES('earlier',?,?,'frozen','FU','[]','COMPLETED')",caseId,UUID.randomUUID());
        var selected=states.affectedInstances("grandchild",Set.of("grandchild","earlier"));
        assertEquals(Set.of("root","child","grandchild"),selected.stream().map(AssignmentTaskStateRepository.RoundInstance::instance).collect(java.util.stream.Collectors.toSet()));
    }
    private void insert(AssignmentCaseRepository cases,String instance,String parent,UUID caseId,UUID round) {
        cases.insert(new AssignmentCaseRegistry.Registration(instance,caseId,round,"frozen",List.of()),parent,null,"FU");
    }
}
