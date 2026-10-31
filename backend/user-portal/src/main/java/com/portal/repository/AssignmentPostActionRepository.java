package com.portal.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Portal-owned frozen condition row and mail variables; replay never captures a later business state. */
@Repository
public class AssignmentPostActionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AssignmentPostActionRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public void record(AssignmentOperationReceipt receipt,AssignmentCompletionRequest.Effect effect) {
        if (effect.postAction().isEmpty()) return; // Explicitly no enabled post-action mail.
        var plan=json.convertValue(effect.postAction(),AssignmentPostAction.Plan.class);
        String row=jdbc.queryForObject("SELECT to_jsonb(p)::text FROM up_process_instance p WHERE id=?",String.class,effect.instanceId());
        var context=json.createObjectNode().put("instanceId",effect.instanceId()).put("actor",receipt.actorUserId());
        context.set("plan",json.valueToTree(plan)); context.set("row",decode(row));
        if (jdbc.update("UPDATE up_assignment_operation_sync SET post_action_context=?::jsonb WHERE operation_id=? AND post_action_context IS NULL",
                context.toString(),receipt.operationId())!=1) throw conflict();
    }
    public List<Due> due(int limit) {
        if (limit<1 || limit>50) throw new IllegalArgumentException("Invalid post-action recovery batch");
        return jdbc.query("SELECT operation_id,post_action_context::text FROM up_assignment_operation_sync WHERE post_action_context IS NOT NULL "
                        +"AND NOT post_action_dispatched AND post_action_next_attempt_at<=clock_timestamp() ORDER BY post_action_next_attempt_at LIMIT ?",
                (rs,n)->new Due(rs.getObject(1,UUID.class),decode(rs.getString(2))),limit);
    }
    public void dispatched(UUID operation) {
        jdbc.update("UPDATE up_assignment_operation_sync SET post_action_dispatched=true WHERE operation_id=?",operation);
    }
    public void retry(UUID operation) {
        jdbc.update("UPDATE up_assignment_operation_sync SET post_action_next_attempt_at=clock_timestamp()+interval '5 seconds' WHERE operation_id=?",operation);
    }
    public Optional<JsonNode> context(UUID operation) {
        return jdbc.query("SELECT post_action_context::text FROM up_assignment_operation_sync WHERE operation_id=? AND post_action_context IS NOT NULL",
                (rs,n)->decode(rs.getString(1)),operation).stream().findFirst();
    }
    public Map<String,Object> variables(AssignmentPostAction.SnapshotRequest request) {
        var frozen=context(request.operationId()).orElseThrow(this::conflict);
        var plan=json.convertValue(frozen.get("plan"),AssignmentPostAction.Plan.class);
        if (request.ruleIndex()<0 || request.ruleIndex()>=plan.rules().size()
                || !AssignmentPostAction.eventId(request.operationId(),request.ruleIndex()).equals(request.eventId())
                || !frozen.path("instanceId").asText().equals(request.processInstanceId()) || !frozen.path("actor").asText().equals(request.actor())
                || !frozen.path("row").path("variables").isObject()) throw conflict();
        return json.convertValue(frozen.path("row").path("variables"),new com.fasterxml.jackson.core.type.TypeReference<>() { });
    }
    private JsonNode decode(String value) {
        try {
            var node=json.readTree(value);
            if (node==null || !node.isObject()) throw conflict();
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { throw conflict(); }
    }
    public record Due(UUID operationId,JsonNode context) { }
    private AssignmentFailure conflict() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT); }
}
