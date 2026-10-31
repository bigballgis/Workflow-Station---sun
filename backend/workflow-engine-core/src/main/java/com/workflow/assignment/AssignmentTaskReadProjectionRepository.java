package com.workflow.assignment;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.jdbc.SqlIdentifiers;
import com.platform.common.util.JsonUtils;
import org.flowable.engine.ManagementService;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;

/** One final READ COMMITTED statement after the actor snapshot; never holds a case/qualification lock over HTTP. */
@Repository
public class AssignmentTaskReadProjectionRepository {
    private final JdbcTemplate jdbc;
    private final ManagementService tables;
    public AssignmentTaskReadProjectionRepository(JdbcTemplate jdbc,@Lazy ManagementService tables) { this.jdbc=jdbc; this.tables=tables; }
    public Snapshot read(List<String> ids,String actor) {
        if (ids==null || ids.isEmpty() || ids.size()>1000 || actor==null || actor.isBlank()) throw new IllegalArgumentException("Invalid assignment projection batch");
        String table=SqlIdentifiers.requireQualifiedName(tables.getTableName(Task.class,true));
        var rows=new NamedParameterJdbcTemplate(jdbc).query(sql(table),Map.of("ids",ids,"actor",JsonUtils.toJson(List.of(actor))),(rs,n)->{
            String id=rs.getString(3);
            Row row=id==null?null:new Row(new Live(rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)),
                    new Binding(rs.getBoolean(8),rs.getBoolean(9),rs.getBoolean(10),rs.getBoolean(11),rs.getBoolean(12)),
                    new Versions(rs.getLong(13),rs.getLong(14),rs.getLong(15),rs.getTimestamp(16)==null?null:rs.getTimestamp(16).toInstant()),
                    new Pool(rs.getString(17),rs.getBoolean(18),rs.getString(19),rs.getString(20)),rs.getBoolean(21));
            return new Parsed(rs.getLong(1),rs.getTimestamp(2).toInstant(),id,row);
        });
        if (rows.isEmpty() || rows.get(0).authority()<0) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        Map<String,Row> result=new LinkedHashMap<>();
        for (var row:rows) if (row.id()!=null) result.put(row.id(),row.row());
        return new Snapshot(rows.get(0).authority(),rows.get(0).observedAt(),Map.copyOf(result));
    }
    private String sql(String table) {
        return """
                SELECT f.revision,statement_timestamp(),t.ID_,t.PROC_INST_ID_,t.PROC_DEF_ID_,t.TASK_DEF_KEY_,t.ASSIGNEE_,
                  s.task_id IS NOT NULL,i.process_instance_id IS NOT NULL,
                  s.case_id=i.case_id AND s.process_instance_id=t.PROC_INST_ID_ AND s.process_definition_id=t.PROC_DEF_ID_
                    AND s.node_key=t.TASK_DEF_KEY_ AND i.process_definition_id=t.PROC_DEF_ID_,s.active,s.source_spec IS NOT NULL,
                  c.revision,s.synced_revision,s.authority_revision,s.next_evaluation_at,s.state,
                  COALESCE(s.eligible_candidates @> CAST(:actor AS jsonb),false),s.pool_source,s.holder_source,
                  c.accepted_operation_seq<>c.confirmed_operation_seq OR c.pending_reopen_operation_id IS NOT NULL
                FROM ac_assignment_authority_fence f LEFT JOIN %s t ON t.ID_ IN (:ids)
                LEFT JOIN we_assignment_task_states s ON s.task_id=t.ID_
                LEFT JOIN we_assignment_instances i ON i.process_instance_id=t.PROC_INST_ID_
                LEFT JOIN we_assignment_cases c ON c.case_id=s.case_id WHERE f.scope='platform' ORDER BY t.ID_
                """.formatted(SqlIdentifiers.requireQualifiedName(table));
    }
    public record Snapshot(long authority,Instant observedAt,Map<String,Row> rows) { }
    public record Live(String instance,String definition,String node,String holder) { }
    public record Binding(boolean statePresent,boolean instanceRegistered,boolean bound,boolean active,boolean hasSource) { }
    public record Versions(long caseRevision,long syncedRevision,long authority,Instant validUntil) { }
    public record Pool(String state,boolean containsActor,String source,String holderSource) { }
    public record Row(Live live,Binding binding,Versions versions,Pool pool,boolean synchronizationPending) { }
    private record Parsed(long authority,Instant observedAt,String id,Row row) { }
}
