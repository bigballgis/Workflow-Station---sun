package com.portal.util;

import com.platform.common.list.ListColumnMeta;
import com.platform.common.list.ListColumnMeta.Kind;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed column declaration for permission request / approval shared lists. Target is a
 * COALESCE of stored names (honest text predicate); identity columns are USER.
 */
public final class PermissionRequestColumnSpec {

    /** SQL expression for the composed target label shown in the grid. */
    public static final String TARGET_SQL = "COALESCE("
            + "NULLIF(TRIM(p.business_unit_name), ''),"
            + "NULLIF(TRIM(p.virtual_group_name), ''),"
            + "NULLIF(TRIM(p.organization_unit_name), ''),"
            + "'-')";

    private PermissionRequestColumnSpec() {
    }

    public static List<ListColumnMeta> columns() {
        return List.of(
                ListColumnMeta.withOptions("requestType", "permission.requestType",
                        Kind.ENUM, typeOptions()),
                ListColumnMeta.of("targetName", "permission.requestTarget", Kind.TEXT),
                ListColumnMeta.withOptions("membershipType", "permission.membershipType",
                        Kind.ENUM, membershipOptions()),
                ListColumnMeta.of("applicantId", "permission.beneficiaryColumn", Kind.USER),
                ListColumnMeta.of("submittedByUserId", "permission.submittedByColumn", Kind.USER),
                ListColumnMeta.of("reason", "permission.reason", Kind.TEXT),
                ListColumnMeta.withOptions("status", "permission.status", Kind.ENUM, statusOptions()),
                ListColumnMeta.of("approverComment", "permission.approverComment", Kind.TEXT),
                ListColumnMeta.of("createdAt", "permission.applyTime", Kind.DATETIME),
                ListColumnMeta.of("approvedAt", "permission.approvedAt", Kind.DATETIME)
        );
    }

    public static ListFilterSql sql() {
        Map<String, ListColumnMeta> byField = new LinkedHashMap<>();
        for (ListColumnMeta column : columns()) {
            byField.put(column.field(), column);
        }
        return new ListFilterSql(byField, PermissionRequestColumnSpec::sqlFor, "p.id", "p.created_at DESC");
    }

    private static String sqlFor(String field) {
        return switch (field) {
            case "requestType" -> "p.request_type";
            case "targetName" -> TARGET_SQL;
            case "membershipType" -> "p.membership_type";
            case "applicantId" -> "p.applicant_id";
            case "submittedByUserId" -> "p.submitted_by_user_id";
            case "reason" -> "p.reason";
            case "status" -> "p.status";
            case "approverComment" -> "p.approve_comment";
            case "createdAt" -> "p.created_at::text";
            case "approvedAt" -> "p.approve_time::text";
            default -> throw new IllegalArgumentException("Unknown permission-request column: " + field);
        };
    }

    private static List<ListColumnMeta.Option> typeOptions() {
        return List.of(
                new ListColumnMeta.Option("ROLE_ASSIGNMENT", "permission.roleAssignment"),
                new ListColumnMeta.Option("VIRTUAL_GROUP_JOIN", "permission.virtualGroupJoin"),
                new ListColumnMeta.Option("BUSINESS_UNIT_JOIN", "permission.businessUnitJoin"),
                new ListColumnMeta.Option("BUSINESS_UNIT_ROLE_REMOVAL", "permission.businessUnitRoleRemoval"),
                new ListColumnMeta.Option("BUSINESS_UNIT_EXIT", "permission.businessUnitExit")
        );
    }

    private static List<ListColumnMeta.Option> membershipOptions() {
        return List.of(
                new ListColumnMeta.Option("MEMBER", "permission.member"),
                new ListColumnMeta.Option("LEADER", "permission.leader")
        );
    }

    private static List<ListColumnMeta.Option> statusOptions() {
        return List.of(
                new ListColumnMeta.Option("PENDING", "permission.pending"),
                new ListColumnMeta.Option("APPROVED", "permission.approved"),
                new ListColumnMeta.Option("REJECTED", "permission.rejected"),
                new ListColumnMeta.Option("CANCELLED", "permission.cancelled")
        );
    }
}
