package com.admin.security;

import java.util.Collection;
import java.util.Set;

/**
 * Who may call an Admin Center endpoint (platform access model §8.1). Declared per endpoint with
 * {@link AdminAccess}; an endpoint without a declaration is {@link #SYS_ADMIN}.
 */
public enum AdminAudience {

    /** No sign-in needed: login, SSO, health; or the endpoint authenticates the caller itself (nonce, SSO token). */
    PUBLIC(Set.of()),

    /** First-party service calls only (valid X-Service-Token); no user role grants access. */
    SERVICE(Set.of()),

    /** Any signed-in platform user; the endpoint applies its own finer check. */
    SIGNED_IN(Set.of()),

    /** SYS_ADMIN, AUDITOR, TECH_LEAD, DEVELOPER: lookups behind Developer Workstation pages. */
    WORKSTATION(Set.of("SYS_ADMIN", "AUDITOR", "TECH_LEAD", "DEVELOPER")),

    /** SYS_ADMIN and AUDITOR: Admin Center reads no Technical Lead page needs. */
    ADMIN_READ(Set.of("SYS_ADMIN", "AUDITOR")),

    /** SYS_ADMIN only: every Admin Center write, and the default. */
    SYS_ADMIN(Set.of("SYS_ADMIN")),

    /** SYS_ADMIN, TECH_LEAD, DEVELOPER: Function Unit import / validate / deploy reached from a DW deployment. */
    DEPLOYER(Set.of("SYS_ADMIN", "TECH_LEAD", "DEVELOPER")),

    /** SYS_ADMIN, AUDITOR, TECH_LEAD: a shared resource every one of them reads in full (§8.2, D-21). */
    SHARED_READ(Set.of("SYS_ADMIN", "AUDITOR", "TECH_LEAD")),

    /** SYS_ADMIN, AUDITOR, TECH_LEAD: a team resource; a Technical Lead reads only the teams they lead (§8.2). */
    TEAM_READ(Set.of("SYS_ADMIN", "AUDITOR", "TECH_LEAD")),

    /** SYS_ADMIN, TECH_LEAD: a team resource; a Technical Lead changes only the teams they lead (§8.2). */
    TEAM_WRITE(Set.of("SYS_ADMIN", "TECH_LEAD")),

    /** SYS_ADMIN, TECH_LEAD: a write every Technical Lead may make, whatever team (BU, Custom role, D-12, D-15). */
    LEAD_WRITE(Set.of("SYS_ADMIN", "TECH_LEAD"));

    private final Set<String> roles;

    AdminAudience(Set<String> roles) {
        this.roles = roles;
    }

    /** Whether one of these platform roles is in the audience; PUBLIC / SIGNED_IN / SERVICE are decided elsewhere. */
    public boolean admitsAnyOf(Collection<String> roleCodes) {
        return roleCodes.stream().anyMatch(roles::contains);
    }

    /** Whether a Technical Lead in this audience is limited to the teams they lead. */
    public boolean teamScoped() {
        return this == TEAM_READ || this == TEAM_WRITE;
    }
}
