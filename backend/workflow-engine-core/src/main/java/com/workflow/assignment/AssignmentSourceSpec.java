package com.workflow.assignment;

import java.util.List;

/** Server-captured original source, reused to refresh qualification without inheriting filtered identity links. */
public record AssignmentSourceSpec(Kind kind, String type, List<String> roles, String buCode, String initiator,
        String anchor, String activeBuCode, String variable, String manualUserVariable,
        String manualBuVariable, String manualRoleVariable, String assigneeField, String roleField, String buField,
        List<String> fixedUsers, boolean claimPool, boolean canonicalFixedUsers) {
    public enum Kind { RESOLVER, VARIABLE, MANUAL, ROW, FIXED }

    public static AssignmentSourceSpec resolver(Resolver source) {
        return new AssignmentSourceSpec(Kind.RESOLVER, source.type(), List.copyOf(source.roles()), source.buCode(),
                source.initiator(), source.anchor(), source.activeBuCode(), null, null, null, null, null, null, null, List.of(), false, false);
    }
    public static AssignmentSourceSpec variable(String variable) {
        return new AssignmentSourceSpec(Kind.VARIABLE, null, List.of(), null, null, null, null, variable,
                null, null, null, null, null, null, List.of(), false, false);
    }
    public static AssignmentSourceSpec manual(String user, String bu, String role) {
        return new AssignmentSourceSpec(Kind.MANUAL, null, List.of(), null, null, null, null, null,
                user, bu, role, null, null, null, List.of(), false, false);
    }
    public static AssignmentSourceSpec row(String user, String role, String bu) {
        return new AssignmentSourceSpec(Kind.ROW, null, List.of(), null, null, null, null, null,
                null, null, null, user, role, bu, List.of(), false, false);
    }
    public static AssignmentSourceSpec fixed(List<String> users, boolean claimPool) {
        return new AssignmentSourceSpec(Kind.FIXED, null, List.of(), null, null, null, null, null,
                null, null, null, null, null, null, List.copyOf(users), claimPool, false);
    }
    public static AssignmentSourceSpec fixedCanonical(List<String> users, boolean claimPool) {
        return new AssignmentSourceSpec(Kind.FIXED, null, List.of(), null, null, null, null, null,
                null, null, null, null, null, null, List.copyOf(users), claimPool, true);
    }
    public record Resolver(String type, List<String> roles, String buCode, String initiator, String anchor, String activeBuCode) { }
}
