package com.admin.security;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What each Admin Center page lets a caller do, by platform role (platform access model §8.2, §9, D-43). Sent with the
 * signed-in user so the frontend shows menus, routes and write buttons from it and keeps no role table of its own;
 * {@code AdminCapabilitiesConsistencyTest} keeps it in line with the endpoints' {@link AdminAccess}.
 */
public final class AdminCapabilities {

    /** {@code TEAM}: writes limited to the teams the caller leads; rows say which ones ({@code canManage} and the like). */
    public enum Level { NONE, READ, TEAM, WRITE }

    /** Admin Center pages; the JSON key is the camel-case name ({@code functionUnits}). */
    public enum Feature {
        DASHBOARD, USERS, ORGANIZATION, ORGANIZATION_MEMBERS, VIRTUAL_GROUPS, ROLES, FUNCTION_UNITS, SLA_POLICIES,
        RELATION_TABLES, AUTOMATION_FLOWS, AUTOMATION_RUNS, AUTOMATION_PIECES, AUDIT_ADMIN_CENTER, AUDIT_USER_PORTAL,
        BI_DASHBOARD_REGISTRY, BI_AUDIENCE_ASSIGNMENT, BI_DATA_VIEW_ASSIGNMENT, BI_RBAC_MAPPING, ENVIRONMENT_VARIABLES,
        SYSTEM_CONFIG, LDAP_SYNC;

        public String key() {
            StringBuilder key = new StringBuilder();
            boolean upper = false;
            for (char c : name().toLowerCase().toCharArray()) {
                if (c == '_') {
                    upper = true;
                } else {
                    key.append(upper ? Character.toUpperCase(c) : c);
                    upper = false;
                }
            }
            return key.toString();
        }
    }

    /** §8.2: team resources at TEAM, BU / Custom role / BI Audience written by every Technical Lead, the rest read. */
    private static final Map<Feature, Level> TECH_LEAD = new EnumMap<>(Map.ofEntries(
            Map.entry(Feature.DASHBOARD, Level.READ),
            Map.entry(Feature.USERS, Level.READ),
            Map.entry(Feature.ORGANIZATION, Level.WRITE),
            Map.entry(Feature.ORGANIZATION_MEMBERS, Level.READ),
            Map.entry(Feature.VIRTUAL_GROUPS, Level.READ),
            Map.entry(Feature.ROLES, Level.WRITE),
            Map.entry(Feature.FUNCTION_UNITS, Level.TEAM),
            Map.entry(Feature.SLA_POLICIES, Level.TEAM),
            Map.entry(Feature.RELATION_TABLES, Level.TEAM),
            Map.entry(Feature.AUTOMATION_FLOWS, Level.READ),
            Map.entry(Feature.AUTOMATION_RUNS, Level.READ),
            Map.entry(Feature.AUTOMATION_PIECES, Level.READ),
            Map.entry(Feature.AUDIT_ADMIN_CENTER, Level.READ),
            Map.entry(Feature.AUDIT_USER_PORTAL, Level.READ),
            Map.entry(Feature.BI_DASHBOARD_REGISTRY, Level.READ),
            Map.entry(Feature.BI_AUDIENCE_ASSIGNMENT, Level.WRITE),
            Map.entry(Feature.BI_DATA_VIEW_ASSIGNMENT, Level.TEAM),
            Map.entry(Feature.BI_RBAC_MAPPING, Level.READ),
            Map.entry(Feature.ENVIRONMENT_VARIABLES, Level.READ),
            Map.entry(Feature.SYSTEM_CONFIG, Level.READ),
            Map.entry(Feature.LDAP_SYNC, Level.READ)));

    private AdminCapabilities() {
    }

    /** Each feature at the highest level any of the roles gives (§4.4: identities add up). */
    public static Map<String, Level> forRoles(Collection<String> roleCodes) {
        Map<String, Level> capabilities = new LinkedHashMap<>();
        for (Feature feature : Feature.values()) {
            capabilities.put(feature.key(), levelOf(feature, roleCodes));
        }
        return capabilities;
    }

    static Level levelOf(Feature feature, Collection<String> roleCodes) {
        Level level = Level.NONE;
        if (roleCodes.contains("SYS_ADMIN")) {
            return Level.WRITE;
        }
        if (roleCodes.contains("AUDITOR")) {
            level = Level.READ;
        }
        if (roleCodes.contains("TECH_LEAD") && TECH_LEAD.get(feature).compareTo(level) > 0) {
            level = TECH_LEAD.get(feature);
        }
        return level;
    }
}
