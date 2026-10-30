package com.portal.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentCompletionRequest;
import com.platform.common.assignment.AssignmentFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;

/** One MVCC snapshot of the existing caller chain. Only selected output-field expectations survive Engine acceptance. */
@Repository
public class AssignmentCompletionBaselineRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AssignmentCompletionBaselineRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public List<AssignmentCompletionRequest.InstanceBaseline> capture(String source) {
        return capture(source,null);
    }
    public List<AssignmentCompletionRequest.InstanceBaseline> capture(String source,String taskId) {
        var rows=jdbc.query("WITH RECURSIVE chain AS (SELECT id,process_definition_id,parent_process_instance_id,variables,ARRAY[id]::text[] AS visited "
                        +"FROM up_process_instance WHERE id=? UNION ALL SELECT p.id,p.process_definition_id,p.parent_process_instance_id,p.variables,c.visited || p.id "
                        +"FROM up_process_instance p JOIN chain c ON p.id=c.parent_process_instance_id WHERE NOT p.id=ANY(c.visited) AND cardinality(c.visited)<33) "
                        +"SELECT id,process_definition_id,CASE WHEN variables IS NULL OR jsonb_typeof(variables::jsonb)='object' THEN "
                        +"(SELECT coalesce(jsonb_object_agg(key,value),'{}'::jsonb)::text FROM jsonb_each(coalesce(variables::jsonb,'{}'::jsonb)) "
                        +"WHERE left(key,10)<>'_snapshot_' OR key=?) ELSE variables::text END,parent_process_instance_id FROM chain",
                (rs,n)->new Stored(baseline(rs.getString(1),rs.getString(2),rs.getString(3)),rs.getString(4)),source,
                taskId==null?null:com.platform.common.assignment.AssignmentSnapshotMetadata.PREFIX+taskId);
        var ids=rows.stream().map(value->value.baseline().instanceId()).collect(java.util.stream.Collectors.toSet());
        if (rows.isEmpty() || rows.size()>32 || rows.stream().filter(value->value.parent()==null).count()!=1
                || rows.stream().anyMatch(value->value.parent()!=null && !ids.contains(value.parent()))) throw unavailable();
        return rows.stream().map(Stored::baseline).toList();
    }
    private AssignmentCompletionRequest.InstanceBaseline baseline(String instance,String definition,String value) {
        try {
            var variables=value==null?json.createObjectNode():json.readTree(value);
            if (!variables.isObject() || definition==null || definition.isBlank()) throw unavailable();
            return new AssignmentCompletionRequest.InstanceBaseline(instance,definition,variables);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { throw unavailable(); }
    }
    private record Stored(AssignmentCompletionRequest.InstanceBaseline baseline,String parent) { }
    private AssignmentFailure unavailable() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE); }
}
