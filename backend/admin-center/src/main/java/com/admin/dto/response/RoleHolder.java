package com.admin.dto.response;

/**
 * Someone who holds a role, and through what (platform access model §4.1, §5.3; Role page "members").
 *
 * @param scopeType {@code GROUP} — a SYSTEM group bound to the role (SYS_ADMIN, AUDITOR);
 *                  {@code TEAM} — the role's list of an active team (TECH_LEAD, DEVELOPER);
 *                  {@code BUSINESS_UNIT} — a business unit membership (UBR) of a business role
 * @param scopeName the group, team or business unit name
 */
public record RoleHolder(String userId, String username, String fullName, String employeeId, String email,
                         String status, String scopeType, String scopeName) {
}
