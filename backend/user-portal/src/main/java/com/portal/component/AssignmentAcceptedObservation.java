package com.portal.component;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.util.JsonUtils;
import com.portal.client.WorkflowEngineClient;
import com.portal.dto.BatchAssignmentVerification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.List;
import java.util.UUID;

/** Optional post-commit MI verification and its history entry are stored once per accepted completion. */
@Component
public class AssignmentAcceptedObservation {
    private final JdbcTemplate jdbc;
    private final WorkflowEngineClient engine;
    private final TransactionTemplate transaction;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AssignmentAcceptedObservation.class);
    public AssignmentAcceptedObservation(JdbcTemplate jdbc,WorkflowEngineClient engine,PlatformTransactionManager manager) {
        this.jdbc=jdbc; this.engine=engine; transaction=new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public BatchAssignmentVerification observe(AssignmentCompletionOrchestrator.Result completion,String actor,String task) {
        if (completion.observation()==null || !"READY".equals(completion.state())) return null;
        BatchAssignmentVerification observed=null;
        try {
            var cached=read(completion.operationId(),false);
            if (cached!=null) return cached;
            observed=SubTableBatchCompletionObservation.observe(completion.observation(),engine);
            var stored=new BatchAssignmentVerification(observed.status(),observed.issues(),true);
            return transaction.execute(status->record(completion,actor,task,stored));
        } catch (RuntimeException unavailable) {
            // FALLBACK(external): Completion is committed. Preserve the optional history failure without re-completing it.
            LOG.warn("Accepted completion observation unavailable: operationId={}",completion.operationId());
            return observed==null?new BatchAssignmentVerification("UNCONFIRMED",List.of(
                    new BatchAssignmentVerification.Issue(null,"OBSERVATION_HISTORY_UNAVAILABLE")),false)
                    :new BatchAssignmentVerification(observed.status(),observed.issues(),false);
        }
    }
    private BatchAssignmentVerification record(AssignmentCompletionOrchestrator.Result completion,String actor,String task,BatchAssignmentVerification result) {
        var existing=read(completion.operationId(),true);
        if (existing!=null) return existing;
        String encoded=JsonUtils.toJson(result);
        jdbc.update("INSERT INTO up_process_history(process_instance_id,task_id,operator_id,operation_type,activity_type,comment,operation_time) "
                        +"VALUES(?,?,?,'BATCH_ASSIGNMENT_VERIFICATION','BATCH_ASSIGNMENT_VERIFICATION',?,clock_timestamp())",
                completion.observation().instanceId(),task,actor,encoded);
        if (jdbc.update("UPDATE up_assignment_operation_sync SET completion_observation=?::jsonb WHERE operation_id=? AND completion_observation IS NULL",
                encoded,completion.operationId())!=1) throw conflict();
        return result;
    }
    private BatchAssignmentVerification read(UUID operation,boolean lock) {
        var rows=jdbc.query("SELECT completion_observation::text FROM up_assignment_operation_sync WHERE operation_id=?"+(lock?" FOR UPDATE":""),
                (rs,n)->rs.getString(1),operation);
        if (rows.size()!=1) throw conflict();
        if (rows.get(0)==null) return null; // The accepted effect has not yet been observed.
        var result=JsonUtils.fromJson(rows.get(0),BatchAssignmentVerification.class);
        if (!List.of("VERIFIED","FAILED","UNCONFIRMED").contains(result.status()) || result.issues()==null || !Boolean.TRUE.equals(result.historyRecorded())) throw conflict();
        return result;
    }
    private AssignmentFailure conflict() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT); }
}
