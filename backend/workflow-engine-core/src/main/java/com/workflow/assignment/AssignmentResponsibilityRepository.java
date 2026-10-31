package com.workflow.assignment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentCaseFence;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Engine-owned completion facts. Effective pointers move only after a real successful completion. */
@Repository
public class AssignmentResponsibilityRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssignmentResponsibilityRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public long completed(String taskId, String actor, Set<String> responsibleUsers) {
        if (actor == null || actor.isBlank() || responsibleUsers == null || !responsibleUsers.contains(actor)
                || responsibleUsers.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("Completion requires verified canonical responsibility identities");
        }
        var task = jdbc.queryForMap("SELECT t.*,i.round_id,i.function_unit_code FROM we_assignment_task_states t "
                + "JOIN we_assignment_instances i ON i.process_instance_id=t.process_instance_id WHERE t.task_id=?", taskId);
        UUID caseId = (UUID) task.get("case_id");
        new AssignmentCaseFence(jdbc).locked(caseId);
        var recorded = jdbc.queryForList("SELECT actor_user_id,responsible_user_ids::text,completion_seq "
                + "FROM we_assignment_completion_records WHERE task_id=?", taskId);
        if (!recorded.isEmpty()) {
            Map<String,Object> previous = recorded.get(0);
            if (!actor.equals(previous.get("actor_user_id"))
                    || !responsibleUsers.equals(users((String) previous.get("responsible_user_ids")))) {
                throw new IllegalStateException("Conflicting responsibility replay");
            }
            return ((Number) previous.get("completion_seq")).longValue();
        }
        jdbc.update("UPDATE we_assignment_completion_records SET is_effective=false "
                        + "WHERE case_id=? AND round_id=? AND slot_key=? AND is_effective",
                caseId, task.get("round_id"), task.get("slot_key"));
        Long sequence = jdbc.queryForObject("UPDATE we_assignment_cases SET revision=revision+1 WHERE case_id=? RETURNING revision",
                Long.class, caseId);
        jdbc.update("INSERT INTO we_assignment_completion_records(task_id,case_id,round_id,process_instance_id,"
                        + "process_definition_id,function_unit_code,node_key,slot_key,slot_identity,completion_seq,actor_user_id,responsible_user_ids) "
                        + "VALUES(?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,CAST(? AS jsonb))",
                taskId, caseId, task.get("round_id"), task.get("process_instance_id"), task.get("process_definition_id"),
                task.get("function_unit_code"), task.get("node_key"), task.get("slot_key"), task.get("slot_identity").toString(),
                sequence, actor, encode(responsibleUsers.stream().sorted().toList()));
        jdbc.update("UPDATE we_assignment_task_states SET active=false,state='COMPLETED' WHERE task_id=?", taskId);
        if (sequence == null) throw new IllegalStateException("Missing completion sequence");
        return sequence;
    }

    /** Effective records include cancelled rounds; cancellation never removes completed responsibility. */
    public List<AssignmentExclusionEvaluator.Responsibility> effective(UUID caseId) {
        return jdbc.query("SELECT function_unit_code,node_key,responsible_user_ids::text "
                        + "FROM we_assignment_completion_records WHERE case_id=? AND is_effective",
                (rs,n) -> new AssignmentExclusionEvaluator.Responsibility(rs.getString(1), rs.getString(2), users(rs.getString(3))), caseId);
    }

    private Set<String> users(String value) {
        try {
            return Set.copyOf(json.readValue(value, new TypeReference<List<String>>() { }));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Invalid stored responsibility identities", e);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot encode responsibility identities", e);
        }
    }
}
