package com.platform.common.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.platform.common.util.JsonUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.function.Supplier;
import static com.platform.common.assignment.AssignmentCoordinationProof.*;

/** Each phase commits only a fresh probe row; never keeps a DB transaction/lock open across HTTP. */
@Repository
public class AssignmentCoordinationProofRepository {
    private static final List<String> RELATIONS=List.of("we_assignment_cases","we_assignment_instances","we_assignment_task_states",
            "we_assignment_completion_records","we_assignment_operations","up_assignment_operation_sync","up_assignment_reopen_sync",
            "up_assignment_source_presence","ac_assignment_authority_fence","we_assignment_coordination_probes");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    public AssignmentCoordinationProofRepository(JdbcTemplate jdbc,PlatformTransactionManager manager) {
        this.jdbc=jdbc; transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED); transaction.setTimeout(5);
    }
    public Challenge create(Participant participant) {
        return phase(()->{
            var id=UUID.randomUUID(); var nonce=UUID.randomUUID(); var relations=relations();
            jdbc.update("DELETE FROM we_assignment_coordination_probes WHERE expires_at<statement_timestamp()");
            var until=jdbc.queryForObject("""
                    INSERT INTO we_assignment_coordination_probes(probe_id,nonce,participant,relation_oids,expires_at)
                    VALUES (?,?,?,CAST(? AS jsonb),statement_timestamp()+interval '30 seconds') RETURNING expires_at
                    """,java.sql.Timestamp.class,id,nonce,participant.name(),JsonUtils.toJson(relations));
            return new Challenge(id,nonce,participant,relations,Objects.requireNonNull(until).toInstant());
        });
    }
    public void acknowledge(Challenge challenge,UUID acknowledgement) {
        phase(()->{
            if (!relations().equals(challenge.relations())) throw unavailable();
            int updated=jdbc.update("""
                    UPDATE we_assignment_coordination_probes SET acknowledgement=? WHERE probe_id=? AND nonce=? AND participant=?
                      AND acknowledgement IS NULL AND expires_at>statement_timestamp()
                    """,acknowledgement,challenge.probeId(),challenge.nonce(),challenge.participant().name());
            if (updated!=1) throw unavailable();
            return null;
        });
    }
    public boolean confirm(Confirmation request) {
        return phase(()->{
            var rows=jdbc.queryForList("""
                    SELECT relation_oids::text FROM we_assignment_coordination_probes WHERE probe_id=? AND nonce=? AND participant=?
                      AND acknowledgement=? AND expires_at>statement_timestamp() FOR UPDATE
                    """,String.class,request.probeId(),request.nonce(),request.participant().name(),request.acknowledgement());
            if (rows.size()!=1 || !relations().equals(readRelations(rows.get(0)))) throw unavailable();
            if (jdbc.update("DELETE FROM we_assignment_coordination_probes WHERE probe_id=?",request.probeId())!=1) throw unavailable();
            return true;
        });
    }
    private Map<String,Long> relations() {
        var result=new LinkedHashMap<String,Long>();
        new NamedParameterJdbcTemplate(jdbc).query("SELECT name,to_regclass(name)::oid::bigint FROM unnest(ARRAY[:names]::text[]) names(name)",
                Map.of("names",RELATIONS),row->{
                    Long oid=row.getObject(2,Long.class);
                    if (oid==null || oid<=0) throw unavailable();
                    result.put(row.getString(1),oid);
                });
        if (result.size()!=RELATIONS.size()) throw unavailable();
        return Map.copyOf(result);
    }
    private Map<String,Long> readRelations(String value) {
        try { return JsonUtils.getObjectMapper().readValue(value,new TypeReference<Map<String,Long>>() { }); }
        catch (java.io.IOException invalid) { throw unavailable(); }
    }
    private <T> T phase(Supplier<T> action) {
        try {
            return transaction.execute(status->{
                if (!Boolean.TRUE.equals(jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Boolean>)
                        connection->!connection.getAutoCommit()))) throw unavailable();
                if (!Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT NOT pg_is_in_recovery() AND current_setting('transaction_read_only')='off'",Boolean.class))) throw unavailable();
                return action.get();
            });
        } catch (org.springframework.dao.DataAccessException failure) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE,failure);
        }
    }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE); }
}
