package com.portal.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentFailure;
import com.portal.entity.ProcessInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Portal-owned projection writes. Published Engine variables never replace an existing business variable map. */
@Repository
public class AssignmentAcceptedProjectionRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AssignmentAcceptedProjectionRepository(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public Map<String,Stored> load(Set<String> ids) {
        return new NamedParameterJdbcTemplate(jdbc).query("SELECT id,process_definition_id,process_definition_key,process_definition_name,"
                        +"function_unit_catalog_id,function_unit_code,function_unit_version_label,variables::text,lock_version,start_user_id,"
                        +"parent_process_instance_id,call_activity_id FROM up_process_instance WHERE id IN (:ids)",Map.of("ids",ids),(rs,n)->{
                    String raw=rs.getString(8);
                    var mirror=ProcessInstance.builder().id(rs.getString(1)).processDefinitionId(rs.getString(2))
                            .processDefinitionKey(rs.getString(3)).processDefinitionName(rs.getString(4)).functionUnitCatalogId(rs.getString(5))
                            .functionUnitCode(rs.getString(6)).functionUnitVersionLabel(rs.getString(7)).variables(variables(raw))
                            .startUserId(rs.getString(10)).parentProcessInstanceId(rs.getString(11)).callActivityId(rs.getString(12)).build();
                    return new Stored(mirror,raw,rs.getObject(9,Long.class));
                }).stream().collect(Collectors.toMap(value->value.mirror().getId(),value->value));
    }
    public List<Stored> create(List<ProcessInstance> mirrors) {
        var rows=mirrors.stream().map(mirror->new Stored(mirror,encode(mirror.getVariables()),0L)).toList();
        jdbc.batchUpdate("INSERT INTO up_process_instance(id,process_instance_id,process_definition_id,process_definition_key,"
                +"process_definition_name,start_user_id,initiator_id,parent_process_instance_id,call_activity_id,function_unit_catalog_id,"
                +"function_unit_code,function_unit_version_label,variables,lock_version,status,business_key,title,start_time,started_at) "
                +"VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,0,'RUNNING',?,?,?,?)",rows.stream().map(this::insert).toList());
        return rows;
    }
    public void save(List<Stored> rows) {
        int[] counts=jdbc.batchUpdate("UPDATE up_process_instance SET variables=?::jsonb,status=?,current_node=?,current_assignee=?,candidate_users=?,"
                        +"end_time=?,completed_at=?,parent_process_instance_id=?,call_activity_id=?,lock_version=COALESCE(lock_version,0)+1,updated_at=clock_timestamp() "
                        +"WHERE id=? AND process_definition_id=? AND lock_version IS NOT DISTINCT FROM ? AND variables IS NOT DISTINCT FROM ?::jsonb",
                rows.stream().map(this::update).toList());
        for (int count:counts) if (count!=1) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_SYNC_CONFLICT);
    }
    private Object[] insert(Stored row) {
        var p=row.mirror();
        return new Object[]{p.getId(),p.getId(),p.getProcessDefinitionId(),p.getProcessDefinitionKey(),p.getProcessDefinitionName(),p.getStartUserId(),
                p.getStartUserId(),p.getParentProcessInstanceId(),p.getCallActivityId(),p.getFunctionUnitCatalogId(),p.getFunctionUnitCode(),
                p.getFunctionUnitVersionLabel(),row.raw(),p.getBusinessKey(),p.getTitle(),p.getStartTime(),p.getStartTime()};
    }
    private Object[] update(Stored row) {
        var p=row.mirror();
        return new Object[]{encode(p.getVariables()),p.getStatus(),p.getCurrentNode(),p.getCurrentAssignee(),p.getCandidateUsers(),p.getEndTime(),
                p.getCompletedAt(),p.getParentProcessInstanceId(),p.getCallActivityId(),p.getId(),p.getProcessDefinitionId(),row.version(),row.raw()};
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { throw new IllegalStateException("Invalid accepted Portal projection",malformed); }
    }
    private Map<String,Object> variables(String raw) {
        if (raw==null) return new java.util.LinkedHashMap<>(); // The Portal schema permits an empty initial variables column.
        try {
            var value=json.readTree(raw);
            if (!value.isObject()) throw new IllegalStateException("Stored Portal variables must be an object");
            return json.convertValue(value,new TypeReference<>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) { throw new IllegalStateException("Invalid stored Portal variables",malformed); }
    }
    public record Stored(ProcessInstance mirror,String raw,Long version) { }
}
