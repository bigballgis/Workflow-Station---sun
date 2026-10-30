package com.portal.repository;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.common.assignment.AssignmentSourceSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** One SQL snapshot: source data and its coordination revisions share the same MVCC visibility. */
@Repository
public class AssignmentSourceSnapshotRepository {
    private final JdbcTemplate jdbc;
    public AssignmentSourceSnapshotRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Stored read(AssignmentSourceSnapshot.Request request) {
        // Engine authenticates the case/instance binding. Portal reads only shared revisions and its own mirror.
        return jdbc.query("SELECT c.revision,c.portal_revision,p.id,p.variables::text,s.process_instance_id "
                        + "FROM we_assignment_cases c LEFT JOIN up_process_instance p ON p.id=? "
                        + "LEFT JOIN up_assignment_source_presence s ON s.process_instance_id=? "
                        + "WHERE c.case_id=? AND (p.id IS NULL OR p.process_definition_id=?)",
                (rs,n) -> new Stored(rs.getLong(1),rs.getLong(2),rs.getString(3)!=null,rs.getString(4),rs.getString(5)!=null),
                request.instanceId(),request.instanceId(),request.caseId(),request.definitionId()).stream().findFirst()
                .orElseThrow(() -> new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_CONTEXT_UNAVAILABLE));
    }
    public record Stored(long caseRevision, long portalRevision, boolean present, String variables, boolean previouslyPresent) { }
}
