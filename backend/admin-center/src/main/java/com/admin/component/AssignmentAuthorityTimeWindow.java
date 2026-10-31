package com.admin.component;

import com.platform.common.assignment.AssignmentIdentitySnapshot.TimeWindow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Role validity changes with wall time without a row mutation. Expose only their temporal fence, never role/member data. */
@Component
public class AssignmentAuthorityTimeWindow {
    private final JdbcTemplate jdbc;
    public AssignmentAuthorityTimeWindow(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public TimeWindow read() {
        return jdbc.queryForObject("""
                WITH moment AS MATERIALIZED (SELECT clock_timestamp() AS observed), bounds AS (
                  SELECT (SELECT max(valid_from) FROM sys_role_assignments WHERE valid_from<=observed) AS value FROM moment
                  UNION ALL SELECT (SELECT min(valid_from) FROM sys_role_assignments WHERE valid_from>observed) FROM moment
                  UNION ALL SELECT (SELECT max(valid_to) FROM sys_role_assignments WHERE valid_to<=observed) FROM moment
                  UNION ALL SELECT (SELECT min(valid_to) FROM sys_role_assignments WHERE valid_to>observed) FROM moment
                  UNION ALL SELECT (SELECT max(valid_from) FROM sys_user_roles WHERE valid_from<=observed) FROM moment
                  UNION ALL SELECT (SELECT min(valid_from) FROM sys_user_roles WHERE valid_from>observed) FROM moment
                  UNION ALL SELECT (SELECT max(valid_to) FROM sys_user_roles WHERE valid_to<=observed) FROM moment
                  UNION ALL SELECT (SELECT min(valid_to) FROM sys_user_roles WHERE valid_to>observed) FROM moment
                ) SELECT observed,max(value::timestamptz) FILTER(WHERE value<=observed),
                    min(value::timestamptz) FILTER(WHERE value>observed) FROM moment LEFT JOIN bounds ON true GROUP BY observed
                """,(row,n)->new TimeWindow(row.getTimestamp(2)==null?null:row.getTimestamp(2).toInstant(),
                row.getTimestamp(3)==null?null:row.getTimestamp(3).toInstant(),row.getTimestamp(1).toInstant()));
    }
}
