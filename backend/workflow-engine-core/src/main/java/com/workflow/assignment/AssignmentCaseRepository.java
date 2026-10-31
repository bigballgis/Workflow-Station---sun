package com.workflow.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence of server-owned context and immutable task-position snapshots. */
@Repository
public class AssignmentCaseRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssignmentCaseRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public void createCase(UUID caseId, String root, String actor, String startBu) {
        createCase(caseId, root, actor, startBu, null);
    }

    public void createCase(UUID caseId, String root, String actor, String startBu, String startBuId) {
        jdbc.update("INSERT INTO we_assignment_cases(case_id,root_instance_id,initiator_user_id,start_bu_code,start_bu_id,runtime_contract_version) VALUES(?,?,?,?,?,1)",
                caseId, root, actor, startBu, startBuId);
    }

    public Optional<AssignmentCaseRegistry.Registration> find(String process) {
        return jdbc.query("SELECT process_instance_id,case_id,round_id,process_definition_id,call_path::text FROM we_assignment_instances WHERE process_instance_id=?",
                (rs,n) -> new AssignmentCaseRegistry.Registration(rs.getString(1), (UUID) rs.getObject(2), (UUID) rs.getObject(3),
                        rs.getString(4), path(rs.getString(5))), process).stream().findFirst();
    }

    public void insert(AssignmentCaseRegistry.Registration registration, String parent, String source, String functionUnitCode) {
        jdbc.update("INSERT INTO we_assignment_instances(process_instance_id,case_id,round_id,parent_instance_id,reopen_source_instance_id,"
                        + "process_definition_id,function_unit_code,call_path,state) VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),'ACTIVE')",
                registration.instance(), registration.caseId(), registration.roundId(), parent, source, registration.definition(),
                functionUnitCode, encode(registration.calls()));
    }

    public SourceState sourceState(String process) {
        return jdbc.queryForObject("SELECT parent_instance_id,state FROM we_assignment_instances WHERE process_instance_id=?",
                (rs,n) -> new SourceState(rs.getString(1) == null, rs.getString(2),rs.getString(1)), process);
    }

    public Origin origin(UUID caseId) {
        return jdbc.queryForObject("SELECT initiator_user_id,start_bu_code,start_bu_id FROM we_assignment_cases WHERE case_id=?",
                (rs,n) -> new Origin(rs.getString(1), rs.getString(2), rs.getString(3)), caseId);
    }

    public com.platform.common.assignment.AssignmentCaseFence.State lockCase(UUID caseId) {
        return new com.platform.common.assignment.AssignmentCaseFence(jdbc).locked(caseId);
    }

    public boolean hasActiveRoot(UUID caseId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM we_assignment_instances WHERE case_id=? AND parent_instance_id IS NULL AND state='ACTIVE')", Boolean.class, caseId));
    }

    public Optional<com.platform.common.assignment.AssignmentReopenReceipt> reopened(UUID operation) {
        return jdbc.query("SELECT reopen_operation_id,reopen_source_instance_id,process_instance_id,process_definition_id,reopened_by,reopen_snapshot::text,state,reopen_mirrored,case_id "
                        + "FROM we_assignment_instances WHERE reopen_operation_id=?", (rs,n) -> new com.platform.common.assignment.AssignmentReopenReceipt(
                        (UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), object(rs.getString(6)), rs.getString(7), rs.getBoolean(8), (UUID) rs.getObject(9)), operation)
                .stream().findFirst();
    }

    public void reopened(AssignmentCaseRegistry.Registration registration, UUID operation, String actor, Map<String,Object> variables) {
        jdbc.update("UPDATE we_assignment_instances SET reopen_operation_id=?,reopened_by=?,reopen_snapshot=CAST(? AS jsonb) WHERE process_instance_id=?",
                operation, actor, encode(variables), registration.instance());
        jdbc.update("UPDATE we_assignment_cases SET pending_reopen_operation_id=? WHERE case_id=?", operation, registration.caseId());
    }

    public List<UUID> pendingReopens(int limit) {
        if (limit<1 || limit>100) throw new IllegalArgumentException("Invalid reopen recovery batch");
        return jdbc.queryForList("SELECT reopen_operation_id FROM we_assignment_instances WHERE reopen_operation_id IS NOT NULL AND NOT reopen_mirrored "
                + "AND reopen_next_attempt_at<=clock_timestamp() ORDER BY reopen_next_attempt_at,created_at LIMIT ?", UUID.class, limit);
    }

    public void reopenMirrored(UUID operation) {
        var receipt = reopened(operation).orElseThrow(() -> new IllegalStateException("Unknown reopen operation"));
        var state = lockCase(receipt.caseId());
        if (reopened(operation).orElseThrow().mirrored()) return;
        if (!operation.equals(state.pendingReopen()) || !operation.equals(state.portalAppliedReopen())) {
            throw new IllegalStateException("Portal has not committed the reopened round mirror");
        }
        jdbc.update("UPDATE we_assignment_instances SET reopen_mirrored=true WHERE reopen_operation_id=?", operation);
        jdbc.update("UPDATE we_assignment_cases SET pending_reopen_operation_id=NULL,next_attempt_at=clock_timestamp() WHERE case_id=? AND pending_reopen_operation_id=?", receipt.caseId(), operation);
    }

    public void retryReopen(UUID operation, boolean conflict) {
        jdbc.update("UPDATE we_assignment_instances SET reopen_next_attempt_at=clock_timestamp()+interval '5 seconds',reopen_last_error=? "
                        + "WHERE reopen_operation_id=? AND NOT reopen_mirrored",
                conflict ? "ASSIGNMENT_SYNC_CONFLICT" : "ASSIGNMENT_DEPENDENCY_UNAVAILABLE", operation);
    }

    public void registerTask(TaskSnapshot task) {
        var previous = jdbc.queryForList("SELECT slot_identity::text FROM we_assignment_task_states WHERE task_id=?", String.class, task.id());
        if (!previous.isEmpty()) {
            if (!object(previous.get(0)).equals(object(task.position().descriptor()))) throw new IllegalStateException("Conflicting task position replay");
            return;
        }
        var registration = task.registration();
        jdbc.update("INSERT INTO we_assignment_task_states(task_id,case_id,process_instance_id,process_definition_id,node_key,slot_key,slot_identity,"
                        + "policy_hash,primary_candidates,state) VALUES(?,?,?,?,?,?,CAST(? AS jsonb),?,'[]','RECONCILING')",
                task.id(), registration.caseId(), registration.instance(), registration.definition(), task.nodeKey(),
                task.position().key(), task.position().descriptor(), task.policyHash());
    }

    public void ended(AssignmentCaseRegistry.Registration registration, boolean cancelled) {
        String state = cancelled ? "CANCELLED" : "COMPLETED";
        jdbc.update("UPDATE we_assignment_instances SET state=? WHERE process_instance_id=?", state, registration.instance());
        jdbc.update("UPDATE we_assignment_task_states SET active=false,state=? WHERE process_instance_id=? AND active", state, registration.instance());
        jdbc.update("UPDATE we_assignment_cases SET revision=revision+1 WHERE case_id=?", registration.caseId());
    }

    public List<String> catalogsFor(String definition) {
        return jdbc.queryForList("SELECT DISTINCT function_unit_id FROM sys_function_unit_contents WHERE flowable_process_definition_id=?", String.class, definition);
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("Invalid assignment metadata", e); }
    }
    private Map<String,Object> object(String value) {
        try { return json.readValue(value, new TypeReference<Map<String,Object>>() { }); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored assignment identity", e); }
    }
    private List<Map<String,Object>> path(String value) {
        try { return json.readValue(value, new TypeReference<List<Map<String,Object>>>() { }); }
        catch (java.io.IOException e) { throw new IllegalStateException("Invalid stored assignment call path", e); }
    }

    public record SourceState(boolean root, String state,String parent) { }
    public record Origin(String initiator, String startBu, String startBuId) { }
    public record TaskSnapshot(String id, String nodeKey, AssignmentCaseRegistry.Registration registration,
            AssignmentSlotIdentity.Position position, String policyHash) { }
}
