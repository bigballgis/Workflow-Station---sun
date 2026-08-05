package com.portal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Portal login entitlement: user must belong to at least one ACTIVE virtual group
 * of type SYSTEM, CUSTOM, or DEVELOPER (compatible with Hermes Default Users and
 * AD-backed custom teams).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PortalEntitlementService {

    public static final String LOGIN_ERROR_PORTAL_ENTITLEMENT_DENIED = "PORTAL_ENTITLEMENT_DENIED";

    private final JdbcTemplate jdbcTemplate;

    /**
     * @return true when the user has at least one qualifying virtual-group membership
     */
    public boolean hasEligibleVirtualGroupMembership(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    """
                            SELECT COUNT(*)
                            FROM sys_virtual_group_members m
                            INNER JOIN sys_virtual_groups g ON g.id = m.group_id
                            WHERE m.user_id = ?
                              AND UPPER(TRIM(COALESCE(g.status, ''))) = 'ACTIVE'
                              AND UPPER(TRIM(COALESCE(NULLIF(TRIM(g.type), ''), 'CUSTOM')))
                                  IN ('SYSTEM', 'CUSTOM', 'DEVELOPER')
                            """,
                    Integer.class,
                    userId);
            return count != null && count > 0;
        } catch (Exception e) {
            // Authoritative login gate: fail closed (no membership proven → deny).
            log.error("Portal entitlement check failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
