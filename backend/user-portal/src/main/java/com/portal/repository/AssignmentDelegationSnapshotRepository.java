package com.portal.repository;

import com.platform.common.assignment.AssignmentAuthorityFence;
import com.platform.common.assignment.AssignmentDelegationSnapshot;
import com.platform.common.assignment.AssignmentFailure;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

/** Portal reads only its own delegation rules and request mirror, plus the shared authority epoch. */
@Repository
public class AssignmentDelegationSnapshotRepository {
    private final JdbcTemplate jdbc;
    public AssignmentDelegationSnapshotRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Epoch lockSnapshot() {
        var authority=new AssignmentAuthorityFence(jdbc);
        long revision=authority.readLocked();
        return new Epoch(revision,authority.currentTime());
    }
    public Mirror mirror(AssignmentDelegationSnapshot.Request request) {
        var mirrors=jdbc.query("SELECT p.process_definition_key,p.priority FROM up_process_instance p "
                        + "JOIN we_assignment_cases c ON c.case_id=? WHERE p.id=? AND p.process_definition_id=?",
                (rs,n)->new Mirror(rs.getString(1),rs.getString(2)),request.caseId(),request.instanceId(),request.definitionId());
        if (mirrors.size()!=1 || mirrors.get(0).process()==null || mirrors.get(0).process().isBlank()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE);
        }
        return mirrors.get(0);
    }
    public List<Rule> active(AssignmentDelegationSnapshot.Request request,Instant now) {
        return jdbc.query("SELECT id,process_types::text,priority_filter::text,end_time AT TIME ZONE current_setting('TimeZone') "
                        + "FROM up_delegation_rule WHERE delegator_id=? AND delegate_id=? AND status='ACTIVE' "
                        + "AND (start_time IS NULL OR start_time<=?::timestamptz) AND (end_time IS NULL OR end_time>?::timestamptz) ORDER BY id",
                (rs,n)->new Rule(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getTimestamp(4)==null?null:rs.getTimestamp(4).toInstant()),
                request.nominalHolder(),request.actor(),now.toString(),now.toString());
    }
    public record Epoch(long revision,Instant now) { }
    public record Mirror(String process,String priority) { }
    public record Rule(long id,String processes,String priorities,Instant end) { }
}
