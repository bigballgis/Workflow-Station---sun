package com.portal.repository;

import com.platform.common.assignment.AssignmentReopenReceipt;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AssignmentReopenMirrorRepository {
    private final JdbcTemplate jdbc;
    public AssignmentReopenMirrorRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Optional<Applied> find(UUID operation) {
        return jdbc.query("SELECT case_id,source_instance_id,process_instance_id FROM up_assignment_reopen_sync WHERE operation_id=?",
                (rs,n) -> new Applied((UUID) rs.getObject(1), rs.getString(2), rs.getString(3)), operation).stream().findFirst();
    }
    public void record(AssignmentReopenReceipt receipt) {
        jdbc.update("INSERT INTO up_assignment_reopen_sync(operation_id,case_id,source_instance_id,process_instance_id) VALUES(?,?,?,?)",
                receipt.operationId(), receipt.caseId(), receipt.sourceInstanceId(), receipt.processInstanceId());
    }
    public record Applied(UUID caseId, String source, String instance) { }
}
