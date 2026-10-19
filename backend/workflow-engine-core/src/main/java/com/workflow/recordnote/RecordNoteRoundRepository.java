package com.workflow.recordnote;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.recordnote.RecordNoteTaskTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** Current task pointers and completed recipient snapshots, updated in the Flowable transaction. */
@Repository
@RequiredArgsConstructor
public class RecordNoteRoundRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public void created(String processId, String definitionId, String executionId, RecordNoteTaskTarget target) {
        activate(processId,target.activationContext());
        String identity = json(new java.util.TreeMap<>(target.rowKey()));
        jdbc.update("UPDATE we_record_note_task_rounds SET is_current=false, updated_at=now() "
                + "WHERE process_instance_id=? AND node_key=? AND row_identity=? AND task_id<>? AND is_current",
                processId, target.nodeKey(), identity, target.taskId());
        jdbc.update("""
                INSERT INTO we_record_note_task_rounds
                (task_id,process_instance_id,process_definition_id,execution_id,node_key,scope_key,table_name,
                 row_identity,row_key,activation_id,assignment_mode,person_field,recipient_id,state,activation_context)
                VALUES (?,?,?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,?,?,CAST(? AS jsonb)) ON CONFLICT(task_id) DO NOTHING
                """, target.taskId(), processId, definitionId, executionId, target.nodeKey(), target.scopeKey(),
                target.tableName(), identity, json(target.rowKey()), target.activationId(),
                target.assignmentMode(), target.personField(), target.recipientId(), target.state(),json(target.activationContext()));
    }

    public void changed(String taskId, String recipientId, String state) {
        jdbc.update("UPDATE we_record_note_task_rounds SET recipient_id=?,state=?,updated_at=now() "
                + "WHERE task_id=? AND is_current", recipientId, state, taskId);
    }

    public List<RecordNoteTaskTarget> current(String processId) {
        return jdbc.query("SELECT * FROM we_record_note_task_rounds WHERE process_instance_id=? AND is_current",
                (rs,n) -> readTarget(rs),processId);
    }

    public java.util.Optional<RecordNoteTaskTarget> task(String taskId) {
        return jdbc.query("SELECT * FROM we_record_note_task_rounds WHERE task_id=? AND is_current",
                (rs,n) -> readTarget(rs),taskId).stream().findFirst();
    }

    private RecordNoteTaskTarget readTarget(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String,String> activation;
        try { activation=mapper.readValue(rs.getString("activation_context"),new TypeReference<>() {}); }
        catch(Exception error) { throw new IllegalStateException("Invalid stored MI activation context",error); }
        return new RecordNoteTaskTarget(rs.getString("task_id"),rs.getString("node_key"),rs.getString("scope_key"),
                rs.getString("table_name"),readMap(rs.getString("row_key")),rs.getString("activation_id"),
                rs.getString("assignment_mode"),rs.getString("person_field"),rs.getString("recipient_id"),
                activation.isEmpty()?"UNRESOLVED":rs.getString("state"),activation);
    }

    public void cancelExecution(String processId,String nodeKey,String executionId) {
        jdbc.update("UPDATE we_record_note_task_rounds SET state='CANCELLED',is_current=false,updated_at=now() "
                + "WHERE process_instance_id=? AND node_key=? AND execution_id=? AND is_current",
                processId,nodeKey,executionId);
    }

    public void activate(String processId,Map<String,String> context) {
        if(context.isEmpty()) return;
        jdbc.update("""
                UPDATE we_record_note_task_rounds r SET state='SUPERSEDED',is_current=false,updated_at=now()
                WHERE process_instance_id=? AND is_current AND EXISTS (
                  SELECT 1 FROM jsonb_each_text(CAST(? AS jsonb)) fresh
                  WHERE jsonb_exists(r.activation_context,fresh.key) AND r.activation_context->>fresh.key <> fresh.value)
                """,processId,json(context));
    }

    public void cancelActivation(String processId,String activation) {
        jdbc.update("""
                UPDATE we_record_note_task_rounds r SET state='CANCELLED',is_current=false,updated_at=now()
                WHERE process_instance_id=? AND is_current AND EXISTS (
                  SELECT 1 FROM jsonb_each_text(r.activation_context) context WHERE context.value=?)
                """,processId,activation);
    }

    public void invalidate(String processId, List<String> nodes, Map<String,Object> rowKey) {
        invalidate(processId,nodes,rowKey,null);
    }

    public void invalidate(String processId,List<String> nodes,Map<String,Object> rowKey,String activation) {
        if(nodes.isEmpty()) return;
        var named=new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc);
        Map<String,Object> parameters=new java.util.HashMap<>();
        parameters.put("process",processId); parameters.put("nodes",nodes);
        String sql="UPDATE we_record_note_task_rounds SET is_current=false,state='SUPERSEDED',updated_at=now() "
                + "WHERE process_instance_id=:process AND node_key IN (:nodes) AND is_current";
        if(rowKey!=null) {
            parameters.put("identity",json(new java.util.TreeMap<>(rowKey)));
            sql+=" AND row_identity=:identity";
        }
        if(activation!=null) {
            parameters.put("activation",activation);
            sql+=" AND EXISTS (SELECT 1 FROM jsonb_each_text(activation_context) c WHERE c.value=:activation)";
        }
        named.update(sql,parameters);
    }

    private Map<String,Object> readMap(String raw) {
        try { return mapper.readValue(raw,new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Invalid stored note task row key",e); }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid note task context",e); }
    }
}
