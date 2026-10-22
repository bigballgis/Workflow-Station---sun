package com.platform.common.functionunit;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Visible in the existing Admin audit log; survives the rejected business transaction. */
@Component
@RequiredArgsConstructor
public class VersionExceptionAudit {
    private final JdbcTemplate jdbc;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(FunctionUnitVersionException error, String actor, String path, String traceId) {
        jdbc.update("INSERT INTO admin_audit_logs(id,action,resource_type,resource_id,user_id,"
                        + "change_details,success,failure_reason,request_path,timestamp) "
                        + "VALUES (?,'QUERY','FUNCTION_UNIT_VERSION',?,?,?,false,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID().toString(), error.getDetails().get("reference"), actor,
                "Version routing rejected; traceId=" + traceId, error.getDetails().get("reason"), path);
    }
}
