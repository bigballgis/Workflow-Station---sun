package com.platform.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Portal entry, the one definition shared by the Portal, the engine and Admin (platform access model §7.1):
 * an active, not deleted account that is a member of at least one ACTIVE CUSTOM virtual group (AD or manual source).
 *
 * <p>SYSTEM and DEVELOPER groups, platform roles and business roles (UBR) give no entry. An account whose status is
 * LOCKED has none; a sign-in lock that only sets {@code locked_until} on an ACTIVE account does not take entry away,
 * so a few minutes' lockout does not hand the user's tasks to colleagues (§7.3).</p>
 */
@Service
@RequiredArgsConstructor
public class PortalEntryService {

    private static final int BATCH = 500;

    private static final String WITH_ENTRY_SQL = "SELECT DISTINCT u.id FROM sys_users u "
            + "JOIN sys_virtual_group_members m ON m.user_id = u.id AND m.list_type = 'MEMBER' "
            + "JOIN sys_virtual_groups g ON g.id = m.group_id AND g.type = 'CUSTOM' AND g.status = 'ACTIVE' "
            + "WHERE u.status = 'ACTIVE' AND COALESCE(u.deleted, false) = false AND u.id IN (%s)";

    private final JdbcTemplate jdbcTemplate;

    public boolean hasPortalEntry(String userId) {
        return userId != null && !userId.isBlank() && withPortalEntry(List.of(userId)).contains(userId);
    }

    /** The users among {@code userIds} that have Portal entry. */
    public Set<String> withPortalEntry(Collection<String> userIds) {
        List<String> ids = userIds.stream().filter(id -> id != null && !id.isBlank()).distinct().toList();
        Set<String> entitled = new HashSet<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<String> slice = ids.subList(from, Math.min(from + BATCH, ids.size()));
            String placeholders = String.join(",", slice.stream().map(id -> "?").toList());
            entitled.addAll(jdbcTemplate.queryForList(String.format(WITH_ENTRY_SQL, placeholders), String.class,
                    slice.toArray()));
        }
        return entitled;
    }
}
