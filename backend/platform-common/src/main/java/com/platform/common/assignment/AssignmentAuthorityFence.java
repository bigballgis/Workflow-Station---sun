package com.platform.common.assignment;

import com.platform.common.jdbc.PostgresTransactionLock;
import org.springframework.jdbc.core.JdbcTemplate;

/** Qualification version only: Admin/Portal keep ownership of their own users, roles and delegation rules. */
public final class AssignmentAuthorityFence {
    private final JdbcTemplate jdbc;

    public AssignmentAuthorityFence(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Read-only local qualification snapshot only; never hold this lock across workflow execution or HTTP calls. */
    public long readLocked() {
        PostgresTransactionLock.shared(jdbc, "assignment", "authority");
        return revision();
    }

    /** Must run before a qualification mutation; its version and data changes commit atomically. */
    public long beginChange() {
        PostgresTransactionLock.exclusive(jdbc, "assignment", "authority");
        int changed = jdbc.update("UPDATE ac_assignment_authority_fence SET revision=revision+1 WHERE scope='platform'");
        if (changed != 1) throw new IllegalStateException("Assignment qualification fence is unavailable");
        return revision();
    }

    /** Final authorization revalidates this committed version after taking the case lock, at READ COMMITTED isolation. */
    public long revision() {
        Long value = jdbc.queryForObject("SELECT revision FROM ac_assignment_authority_fence WHERE scope='platform'", Long.class);
        if (value == null) throw new IllegalStateException("Assignment qualification fence is unavailable");
        return value;
    }

    /** Database wall clock, not a transaction-start timestamp. */
    public java.time.Instant currentTime() {
        var time = jdbc.queryForObject("SELECT clock_timestamp()", (rs,n) -> rs.getTimestamp(1).toInstant());
        if (time == null) throw new IllegalStateException("Assignment authority clock is unavailable");
        return time;
    }
}
