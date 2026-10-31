package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.Map;

/** Definition row locks serialize deletion with root registration and publication of frozen references. */
@Component
public class AssignmentDefinitionRetention {
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;
    public AssignmentDefinitionRetention(JdbcTemplate jdbc) { this.jdbc=jdbc; named=new NamedParameterJdbcTemplate(jdbc); }
    public void start(String definition) { lock(List.of(definition)); }
    public void publication(String deployment,List<String> references) {
        requireTransaction();
        if (references.isEmpty()) return;
        lock(references);
        var parameters=references.stream().map(id->Map.of("owner",deployment,"target",id)).toArray(Map[]::new);
        named.batchUpdate("INSERT INTO we_assignment_definition_dependencies VALUES (:owner,:target) ON CONFLICT DO NOTHING",parameters);
    }
    public void deletion(String deployment) {
        requireTransaction();
        // Ordered before all retention reads and before Flowable can cancel even the first runtime task.
        var ids=jdbc.queryForList("SELECT id_ FROM act_re_procdef WHERE deployment_id_=? ORDER BY id_ FOR NO KEY UPDATE",String.class,deployment);
        if (ids.isEmpty()) return; // The SDK retains its original absent-deployment handling.
        var parameters=Map.of("ids",ids,"deployment",deployment);
        Boolean retained=named.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM we_assignment_instances WHERE process_definition_id IN (:ids))
                  OR EXISTS(SELECT 1 FROM we_assignment_definition_dependencies
                    WHERE referenced_definition_id IN (:ids) AND owner_deployment_id<>:deployment)
                  OR EXISTS(SELECT 1 FROM sys_function_unit_contents WHERE content_type='PROCESS'
                    AND (flowable_deployment_id=:deployment OR flowable_process_definition_id IN (:ids)))
                """,parameters,Boolean.class);
        if (!Boolean.FALSE.equals(retained)) throw unavailable();
    }
    public void deleted(String deployment) {
        requireTransaction();
        jdbc.update("DELETE FROM we_assignment_definition_dependencies WHERE owner_deployment_id=?",deployment);
    }
    public void cascade(String definition) {
        requireTransaction();
        // Compatible with the FK KEY SHARE taken by Flowable's separate failed-job transaction.
        jdbc.queryForList("SELECT id_ FROM act_re_procdef WHERE id_=? FOR NO KEY UPDATE",String.class,definition);
        Boolean retained=jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM we_assignment_instances WHERE process_definition_id=?)
                  OR EXISTS(SELECT 1 FROM we_assignment_definition_dependencies WHERE referenced_definition_id=?)
                """,Boolean.class,definition,definition);
        if (!Boolean.FALSE.equals(retained)) throw unavailable();
    }
    private void lock(List<String> definitions) {
        requireTransaction();
        var distinct=definitions.stream().distinct().sorted().toList();
        var locked=named.queryForList("SELECT id_ FROM act_re_procdef WHERE id_ IN (:ids) ORDER BY id_ FOR SHARE",
                Map.of("ids",distinct),String.class);
        if (locked.size()!=distinct.size()) throw unavailable();
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw unavailable();
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
