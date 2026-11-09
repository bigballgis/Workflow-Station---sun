package com.developer.security;

import java.util.Collection;
import java.util.Set;

/**
 * Who may enter the Developer Workstation (platform access model §6.1): SYS_ADMIN, AUDITOR (read-only), or anyone
 * on the Technical Lead or Developer list of an active team. The unified role resolution grants TECH_LEAD and
 * DEVELOPER only from those lists, so the effective role codes decide it. Membership of a CUSTOM group alone does
 * not admit anyone.
 */
public final class DeveloperWorkstationEntitlement {

    private static final Set<String> ENTITLING_ROLES = Set.of("SYS_ADMIN", "AUDITOR", "TECH_LEAD", "DEVELOPER");

    private DeveloperWorkstationEntitlement() {
    }

    public static boolean isEntitled(Collection<String> effectiveRoleCodes) {
        return effectiveRoleCodes != null && effectiveRoleCodes.stream().anyMatch(ENTITLING_ROLES::contains);
    }
}
