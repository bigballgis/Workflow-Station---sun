package com.workflow.assignment;

import com.platform.common.jdbc.SqlIdentifiers;
import org.flowable.engine.ManagementService;
import org.flowable.engine.runtime.Execution;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Engine-owned, bounded batch read of actual task/caller ancestry. No candidate, user or role HTTP calls. */
@Repository
public class AssignmentTaskReadContexts {
    private final JdbcTemplate jdbc;
    private final ManagementService tables;
    public AssignmentTaskReadContexts(JdbcTemplate jdbc,@Lazy ManagementService tables) { this.jdbc=jdbc; this.tables=tables; }

    public Map<String,Context> read(List<String> ids) {
        if (ids==null || ids.isEmpty() || ids.size()>1000 || ids.stream().anyMatch(id->id==null || id.isBlank())
                || new HashSet<>(ids).size()!=ids.size()) throw new IllegalArgumentException("Invalid assignment read batch");
        String taskTable=SqlIdentifiers.requireQualifiedName(tables.getTableName(Task.class,true));
        String executionTable=SqlIdentifiers.requireQualifiedName(tables.getTableName(Execution.class,true));
        var rows=new NamedParameterJdbcTemplate(jdbc).query(sql(taskTable,executionTable),Map.of("ids",ids),(rs,n)->{
            var subject=new Subject(rs.getString(1),new Position(rs.getString(2),rs.getString(3),rs.getString(4)),rs.getString(5),rs.getBoolean(6));
            UUID caseId=(UUID)rs.getObject(11);
            var registered=caseId==null?null:new Registered(caseId,rs.getString(12));
            var ancestor=rs.getString(7)==null?null:new Ancestor(rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),registered);
            return new Row(subject,ancestor);
        });
        Map<String,Subject> subjects=new LinkedHashMap<>(); Map<String,List<Ancestor>> ancestors=new LinkedHashMap<>();
        for (var row:rows) {
            subjects.putIfAbsent(row.subject().id(),row.subject());
            var chain=ancestors.computeIfAbsent(row.subject().id(),ignored->new ArrayList<>());
            if (row.ancestor()!=null) chain.add(row.ancestor());
        }
        Map<String,Context> result=new LinkedHashMap<>();
        subjects.forEach((id,subject)->result.put(id,new Context(subject,List.copyOf(ancestors.get(id)))));
        return Map.copyOf(result);
    }

    private String sql(String tasks,String executions) {
        return """
                WITH RECURSIVE subjects AS (
                  SELECT t.ID_ task_id,t.PROC_INST_ID_ instance_id,t.PROC_DEF_ID_ definition_id,t.TASK_DEF_KEY_ node_id,t.ASSIGNEE_ holder,
                         EXISTS(SELECT 1 FROM we_assignment_task_states s WHERE s.task_id=t.ID_) has_state
                  FROM %s t WHERE t.ID_ IN (:ids)
                ), chain AS (
                  SELECT s.task_id,e.ID_ instance_id,e.PROC_DEF_ID_ definition_id,e.SUPER_EXEC_ super_call,c.PROC_INST_ID_ parent_instance,
                         ARRAY[e.ID_::text] visited,0 depth
                  FROM subjects s JOIN %s e ON e.ID_=s.instance_id LEFT JOIN %s c ON c.ID_=e.SUPER_EXEC_
                  UNION ALL
                  SELECT chain.task_id,e.ID_,e.PROC_DEF_ID_,e.SUPER_EXEC_,c.PROC_INST_ID_,chain.visited || e.ID_::text,chain.depth+1
                  FROM chain JOIN %s e ON e.ID_=chain.parent_instance LEFT JOIN %s c ON c.ID_=e.SUPER_EXEC_
                  WHERE chain.depth<32 AND NOT e.ID_::text=ANY(chain.visited)
                )
                SELECT s.task_id,s.instance_id,s.definition_id,s.node_id,s.holder,s.has_state,
                       c.instance_id,c.definition_id,c.super_call,c.parent_instance,i.case_id,i.process_definition_id
                FROM subjects s LEFT JOIN chain c ON c.task_id=s.task_id
                LEFT JOIN we_assignment_instances i ON i.process_instance_id=c.instance_id ORDER BY s.task_id,c.depth
                """.formatted(SqlIdentifiers.requireQualifiedName(tasks),SqlIdentifiers.requireQualifiedName(executions),
                SqlIdentifiers.requireQualifiedName(executions),SqlIdentifiers.requireQualifiedName(executions),SqlIdentifiers.requireQualifiedName(executions));
    }
    public record Position(String instance,String definition,String node) { }
    public record Subject(String id,Position position,String holder,boolean hasState) { }
    public record Registered(UUID caseId,String definition) { }
    public record Ancestor(String instance,String definition,String superCall,String parent,Registered registered) { }
    public record Context(Subject subject,List<Ancestor> ancestors) { }
    private record Row(Subject subject,Ancestor ancestor) { }
}
