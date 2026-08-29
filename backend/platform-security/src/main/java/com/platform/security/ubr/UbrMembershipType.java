package com.platform.security.ubr;

import java.util.Locale;

/**
 * Per-UBR (user + business unit + role) tier for Claim/Hold.
 * Leader is not a platform role and is not a BU Approver.
 */
public final class UbrMembershipType {

    public static final String MEMBER = "MEMBER";
    public static final String LEADER = "LEADER";

    private UbrMembershipType() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return MEMBER;
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (MEMBER.equals(value) || LEADER.equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("membershipType must be MEMBER or LEADER");
    }

    public static boolean isLeader(String raw) {
        return LEADER.equals(normalize(raw));
    }
}
