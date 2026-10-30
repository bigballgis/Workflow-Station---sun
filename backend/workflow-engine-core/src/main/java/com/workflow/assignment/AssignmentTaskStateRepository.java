package com.workflow.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Recoverable assignment projection and one-use handoff provenance, never an independent permission authority. */
@Repository
public class AssignmentTaskStateRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssignmentTaskStateRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Optional<State> find(String taskId) {
        return jdbc.query("SELECT task_id,case_id,process_instance_id,process_definition_id,node_key,source_spec::text,"
                        + "holder_source,pool_source,primary_candidates::text,eligible_candidates::text,state,synced_revision,"
                        + "authority_revision,released_holder_id,handoff_consumed,active,policy_hash FROM we_assignment_task_states WHERE task_id=?",
                (rs,n) -> new State(rs.getString(1), (UUID) rs.getObject(2), rs.getString(3), rs.getString(4), rs.getString(5),
                        source(rs.getString(6)), rs.getString(7), rs.getString(8), users(rs.getString(9)), users(rs.getString(10)),
                        rs.getString(11), rs.getLong(12), rs.getLong(13), rs.getString(14), rs.getBoolean(15), rs.getBoolean(16), rs.getString(17)),
                taskId).stream().findFirst();
    }

    public CaseContext caseContext(UUID caseId) {
        return jdbc.queryForObject("SELECT initiator_user_id,start_bu_code,revision,authority_revision FROM we_assignment_cases WHERE case_id=?",
                (rs,n) -> new CaseContext(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4)), caseId);
    }

    public String functionUnit(String instance) {
        return jdbc.queryForObject("SELECT function_unit_code FROM we_assignment_instances WHERE process_instance_id=?", String.class, instance);
    }

    public List<RoundInstance> affectedInstances(String instance,java.util.Set<String> changed) {
        return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc).query("WITH RECURSIVE affected AS ("
                        + "SELECT i.* FROM we_assignment_instances i JOIN we_assignment_instances source ON source.process_instance_id=:source "
                        + "AND i.case_id=source.case_id AND i.round_id=source.round_id WHERE i.process_instance_id IN (:changed) UNION "
                        + "SELECT parent.* FROM we_assignment_instances parent JOIN affected child ON parent.process_instance_id=child.parent_instance_id "
                        + "AND parent.case_id=child.case_id AND parent.round_id=child.round_id) "
                        + "SELECT process_instance_id,process_definition_id,parent_instance_id,call_path->-1->>'node',state,(call_path->-1->'row')::text FROM affected ORDER BY process_instance_id",
                java.util.Map.of("source",instance,"changed",changed),
                (rs,n)->new RoundInstance(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rowIdentity(rs.getString(6))));
    }
    public record RoundInstance(String instance,String definition,String parent,String callActivity,String state,com.fasterxml.jackson.databind.JsonNode row) {
        public RoundInstance(String instance,String definition,String parent,String callActivity,String state) { this(instance,definition,parent,callActivity,state,null); }
    }
    public List<RoundInstance> roundPositions(String source) {
        return jdbc.query("SELECT i.process_instance_id,i.process_definition_id,i.parent_instance_id,i.call_path->-1->>'node',i.state,(i.call_path->-1->'row')::text "
                        + "FROM we_assignment_instances i JOIN we_assignment_instances source ON source.process_instance_id=? "
                        + "AND i.case_id=source.case_id AND i.round_id=source.round_id",
                (rs,n)->new RoundInstance(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rowIdentity(rs.getString(6))),source);
    }
    private com.fasterxml.jackson.databind.JsonNode rowIdentity(String value) {
        if (value==null) return null; // Root instances and non-MI calls have no call-row selector.
        try { return json.readTree(value); }
        catch (java.io.IOException malformed) { throw new IllegalStateException("Invalid frozen call-row identity",malformed); }
    }

    public long portalRevision(UUID caseId) {
        Long revision = jdbc.queryForObject("SELECT portal_revision FROM we_assignment_cases WHERE case_id=?", Long.class, caseId);
        if (revision == null) throw new IllegalStateException("Assignment case Portal revision is unavailable");
        return revision;
    }

    public com.fasterxml.jackson.databind.JsonNode position(String taskId) {
        String value = jdbc.queryForObject("SELECT slot_identity::text FROM we_assignment_task_states WHERE task_id=? AND active",String.class,taskId);
        try { return json.readTree(value); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Invalid frozen task position",failure); }
    }

    public void source(String taskId, AssignmentSourceSpec source) {
        int updated = jdbc.update("UPDATE we_assignment_task_states SET source_spec=CAST(? AS jsonb) WHERE task_id=? AND active", encode(source), taskId);
        if (updated != 1) throw new IllegalStateException("Cannot register source for an inactive assignment task");
    }

    public void projection(Projection projection) {
        int updated = jdbc.update("UPDATE we_assignment_task_states SET primary_candidates=CAST(? AS jsonb),eligible_candidates=CAST(? AS jsonb),"
                        + "pool_source=?,state=?,synced_revision=?,authority_revision=?,next_evaluation_at=?,last_evaluated_at=clock_timestamp() WHERE task_id=? AND active",
                encode(projection.primary()), encode(projection.eligible()), projection.source().name(), projection.state(),
                projection.revision(), projection.authorityRevision(), projection.nextEvaluationAt() == null ? null
                        : java.sql.Timestamp.from(projection.nextEvaluationAt()), projection.taskId());
        if (updated != 1) throw new IllegalStateException("Cannot project an inactive assignment task");
    }

    public void released(String taskId, String previousHolder, long revision) {
        jdbc.update("UPDATE we_assignment_task_states SET released_holder_id=?,released_at=clock_timestamp(),holder_source=NULL,"
                        + "handoff_consumed=false,release_history=release_history || jsonb_build_array(jsonb_build_object("
                        + "'userId',CAST(? AS text),'revision',CAST(? AS bigint),'at',clock_timestamp(),'reason','QUALIFICATION_LOST')) WHERE task_id=? AND active",
                previousHolder, previousHolder, revision, taskId);
    }

    public void held(String taskId, AssignmentExclusionEvaluator.Source source) {
        jdbc.update("UPDATE we_assignment_task_states SET holder_source=?,handoff_consumed=true WHERE task_id=? AND active", source.name(), taskId);
    }

    public void handedOff(String taskId,String actor,String target,AssignmentExclusionEvaluator.Source source) {
        int changed=jdbc.update("UPDATE we_assignment_task_states t SET holder_source=?,handoff_consumed=true,release_history=t.release_history "
                        + "|| jsonb_build_array(jsonb_build_object('reason','HANDOFF','userId',CAST(? AS text),'targetId',CAST(? AS text),"
                        + "'revision',c.revision+1,'at',clock_timestamp())) FROM we_assignment_cases c "
                        + "WHERE t.task_id=? AND t.active AND NOT t.handoff_consumed AND t.released_holder_id=? AND c.case_id=t.case_id",
                source.name(),actor,target,taskId,actor);
        if (changed!=1) throw new com.platform.common.assignment.AssignmentFailure(com.platform.common.assignment.AssignmentFailure.Reason.ASSIGNMENT_STATE_CHANGED);
    }

    public void unheld(String taskId) {
        jdbc.update("UPDATE we_assignment_task_states SET holder_source=NULL,handoff_consumed=true WHERE task_id=? AND active", taskId);
    }

    public void changed(UUID caseId) {
        jdbc.update("UPDATE we_assignment_cases SET revision=revision+1 WHERE case_id=?", caseId);
    }

    public List<String> activeTasks(UUID caseId) {
        return jdbc.queryForList("SELECT task_id FROM we_assignment_task_states WHERE case_id=? AND active ORDER BY task_id", String.class, caseId);
    }

    public void reconciled(UUID caseId, long revision, long authorityRevision) {
        jdbc.update("UPDATE we_assignment_cases SET applied_revision=?,authority_revision=?,last_error=NULL,"
                        + "next_evaluation_at=(SELECT min(t.next_evaluation_at) FROM we_assignment_task_states t WHERE t.case_id=we_assignment_cases.case_id AND t.active) "
                        + "WHERE case_id=? AND revision=? AND NOT EXISTS(SELECT 1 FROM we_assignment_task_states t "
                        + "WHERE t.case_id=we_assignment_cases.case_id AND t.active AND (t.synced_revision<? OR t.authority_revision<? "
                        + "OR t.next_evaluation_at<=clock_timestamp()))",
                revision, authorityRevision, caseId, revision, revision, authorityRevision);
    }

    public List<String> dueTasks(long authorityRevision, int limit) {
        if (limit < 1 || limit > 1000) throw new IllegalArgumentException("Invalid assignment reconciliation lookahead");
        return jdbc.queryForList("SELECT t.task_id FROM we_assignment_task_states t JOIN we_assignment_cases c ON c.case_id=t.case_id "
                        + "WHERE t.active AND c.next_attempt_at<=clock_timestamp() AND (t.synced_revision<c.revision "
                        + "OR t.authority_revision<? OR t.next_evaluation_at<=clock_timestamp()) "
                        + "ORDER BY (t.synced_revision<c.revision OR t.authority_revision<?) DESC,"
                        + "t.last_evaluated_at NULLS FIRST,t.next_evaluation_at NULLS FIRST,c.next_attempt_at,t.task_id LIMIT ?",
                String.class, authorityRevision, authorityRevision, limit);
    }

    public void retryLater(UUID caseId, String reason) {
        jdbc.update("UPDATE we_assignment_cases SET next_attempt_at=clock_timestamp()+interval '5 seconds',last_error=? WHERE case_id=?", reason, caseId);
    }

    public void attempted(UUID caseId) {
        jdbc.update("UPDATE we_assignment_cases SET next_attempt_at=clock_timestamp(),last_error=NULL WHERE case_id=?", caseId);
    }

    public void cancelledTask(String taskId) {
        jdbc.update("UPDATE we_assignment_task_states SET active=false,state='CANCELLED' WHERE task_id=? AND active", taskId);
    }

    public List<UUID> dueCases(long authorityRevision, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid assignment reconciliation batch");
        return jdbc.queryForList("SELECT c.case_id FROM we_assignment_cases c WHERE EXISTS(SELECT 1 FROM we_assignment_task_states t "
                        + "WHERE t.case_id=c.case_id AND t.active) AND c.next_attempt_at<=clock_timestamp() AND "
                        + "(c.applied_revision<c.revision OR c.authority_revision<? OR c.next_evaluation_at<=clock_timestamp()) "
                        + "ORDER BY c.next_attempt_at LIMIT ?", UUID.class, authorityRevision, limit);
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("Invalid assignment projection", e); }
    }
    private List<String> users(String value) {
        try { return List.copyOf(json.readValue(value, new TypeReference<List<String>>() { })); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored assignment candidate identities", e); }
    }
    private AssignmentSourceSpec source(String value) {
        if (value == null) return null; // Explicit creation state, before the original source has been captured.
        try { return json.readValue(value, AssignmentSourceSpec.class); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored assignment source", e); }
    }

    public record CaseContext(String initiator, String startBu, long revision, long authorityRevision) { }
    public record State(String taskId, UUID caseId, String instance, String definition, String node,
            AssignmentSourceSpec sourceSpec, String holderSource, String poolSource, List<String> primary,
            List<String> eligible, String state, long revision, long authorityRevision, String releasedHolder,
            boolean handoffConsumed, boolean active, String policyHash) { }
    public record Projection(String taskId, List<String> primary, List<String> eligible, AssignmentExclusionEvaluator.Source source,
            String state, long revision, long authorityRevision, Instant nextEvaluationAt) { }
}
