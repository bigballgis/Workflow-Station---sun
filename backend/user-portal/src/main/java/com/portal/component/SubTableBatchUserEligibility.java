package com.portal.component;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import com.portal.exception.PortalException;

/** Current directory state is checked at the write/Complete boundary, not frozen or cached. */
final class SubTableBatchUserEligibility {
    private SubTableBatchUserEligibility() { }
    static void requireActive(Collection<String> targets, JdbcTemplate jdbc) {
        Set<String> ids = new LinkedHashSet<>(targets);
        if (ids.isEmpty()) return;
        Set<String> eligible = new LinkedHashSet<>(new NamedParameterJdbcTemplate(jdbc).queryForList(
                "SELECT id FROM sys_users WHERE id IN (:ids) AND status = 'ACTIVE' AND deleted = false",
                Map.of("ids", ids), String.class));
        if (!eligible.equals(ids)) throw new PortalException("403", "Initial assignment person target is not currently active");
    }
}
